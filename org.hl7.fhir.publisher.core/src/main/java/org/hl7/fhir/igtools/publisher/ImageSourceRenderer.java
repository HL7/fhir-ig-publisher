package org.hl7.fhir.igtools.publisher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.filesystem.ManagedFileAccess;

import net.sourceforge.plantuml.BlockUml;
import net.sourceforge.plantuml.ErrorUml;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.core.Diagram;
import net.sourceforge.plantuml.error.PSystemError;
import net.sourceforge.plantuml.preproc.Defines;

/**
 * Renders the diagram sources in the folders that path-image-source names (fhir2.base.template declares
 * input/images-source and template/images-source) to SVG, for pages to include as {% include [name].svg %}. This
 * replaces the template running a plantuml.jar of its own. Which converter is used is decided by the file's extension;
 * for now there is only PlantUML (.plantuml, .puml). Other files in the folders (e.g. .iuml) are only there to be
 * included.
 *
 * Output names are the ones the PlantUML command line (which the templates ran) gave them: the name on the @startuml
 * line if there is one, else the source file's name, with _001, _002... for the second and later images from the same
 * file. All images go into one folder, whichever sub-folder their source is in.
 *
 * Layout is always PlantUML's own java one (smetana), so the output doesn't depend on whether Graphviz happens to be
 * installed.
 *
 * Security: PlantUML runs in its SANDBOX profile, so it can't read any file, or anything from the web, itself (the
 * PlantUML standard library, !include &lt;...&gt;, is inside its jar, and still works). Diagrams can !include files in
 * the image source folders: this class does those includes, before PlantUML sees the diagram. (Not PlantUML's ALLOWLIST
 * profile: its check is a prefix match on the absolute path, so allowing folder a lets a/../b through.)
 */
public class ImageSourceRenderer {

  public static class RenderedImage {
    private final String name;
    private final byte[] content;
    private final File source;

    public RenderedImage(String name, byte[] content, File source) {
      this.name = name;
      this.content = content;
      this.source = source;
    }
    /** the file name to write in _includes, e.g. sequence.svg */
    public String getName() {
      return name;
    }
    public byte[] getContent() {
      return content;
    }
    public File getSource() {
      return source;
    }
  }

  public static class ImageIssue {
    private final File source;
    private final int line;
    private final String message;

    public ImageIssue(File source, int line, String message) {
      this.source = source;
      this.line = line;
      this.message = message;
    }
    /** the diagram source the problem is in (or that includes the file it's in) */
    public File getSource() {
      return source;
    }
    /** 1-based, in getSource(), or 0 if not known */
    public int getLine() {
      return line;
    }
    public String getMessage() {
      return message;
    }
  }

  /** the diagram source with what it includes in place, and where each line came from */
  private static class Expansion {
    private final List<String> lines = new ArrayList<>();
    private final List<File> files = new ArrayList<>();
    private final List<Integer> lineNumbers = new ArrayList<>();
    private final Set<String> included = new HashSet<>();

    void add(String line, File file, int lineNumber) {
      lines.add(line);
      files.add(file);
      lineNumbers.add(lineNumber);
    }
  }

  private static final List<String> CONFIG = Arrays.asList("!pragma layout smetana");
  private static final int MAX_INCLUDE_DEPTH = 20;

  /**
   * PlantUML fixes its security profile the first time anything uses it - which might be core rendering an
   * ExampleScenario, long before the image sources are rendered - so this must be called before the build does
   * anything else. It overrides any PLANTUML_SECURITY_PROFILE the environment sets
   */
  public static void initPlantUmlSecurity() {
    System.setProperty("PLANTUML_SECURITY_PROFILE", "SANDBOX");
  }

  /**
   * Render every diagram source in the folders (and their sub-folders). Folders that don't exist are skipped.
   * Problems are added to issues, and the diagrams they're in produce no image
   */
  public List<RenderedImage> render(List<String> folders, List<ImageIssue> issues) throws IOException {
    List<RenderedImage> images = new ArrayList<>();
    List<File> sources = new ArrayList<>();
    List<String> allowed = new ArrayList<>();
    for (String folder : folders) {
      File dir = ManagedFileAccess.file(folder);
      if (dir.isDirectory()) {
        allowed.add(dir.getCanonicalPath());
        listSources(dir, sources);
      }
    }
    if (sources.isEmpty()) {
      return images;
    }
    // PlantUML writes diagnostics of its own to the console (layout engine chatter, stack traces for diagrams it can't
    // handle); what matters about a diagram is reported as an issue, so the console output is dropped while it runs
    PrintStream out = System.out;
    PrintStream err = System.err;
    PrintStream sink = new PrintStream(OutputStream.nullOutputStream());
    System.setOut(sink);
    System.setErr(sink);
    try {
      for (File source : sources) {
        try {
          renderPlantUml(source, allowed, images, issues);
        } catch (Throwable e) {
          // PlantUML can fail in all sorts of ways on bad input; one diagram mustn't stop the build
          issues.add(new ImageIssue(source, 0, "PlantUML: unable to render the diagram: "+e.getMessage()));
        }
      }
    } finally {
      System.setOut(out);
      System.setErr(err);
    }
    return images;
  }

  private void listSources(File dir, List<File> sources) {
    File[] files = dir.listFiles();
    if (files == null) {
      return;
    }
    // sorted, so the order (and which of two sources with the same output name wins) doesn't depend on the file system
    Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
    for (File f : files) {
      if (f.isDirectory()) {
        listSources(f, sources);
      } else if (isPlantUml(f.getName())) {
        sources.add(f);
      }
    }
  }

  public static boolean isPlantUml(String name) {
    String n = name.toLowerCase();
    return n.endsWith(".plantuml") || n.endsWith(".puml");
  }

  private void renderPlantUml(File source, List<String> allowed, List<RenderedImage> images, List<ImageIssue> issues) throws IOException {
    Expansion exp = new Expansion();
    if (!expand(source, source, allowed, exp, 0, issues)) {
      return;
    }
    FileFormatOption format = new FileFormatOption(FileFormat.SVG, false); // false: no metadata (the source) in the svg
    SourceStringReader reader = new SourceStringReader(Defines.createWithFileName(source), String.join("\n", exp.lines), "UTF-8", CONFIG);
    String fileName = source.getName();
    fileName = fileName.substring(0, fileName.lastIndexOf('.'));
    int unnamed = 0;
    for (BlockUml block : reader.getBlocks()) {
      Diagram diagram = block.getDiagram();
      if (diagram instanceof PSystemError) {
        reportError(source, exp, (PSystemError) diagram, issues);
        continue;
      }
      String name = nameFromStart(block.getFileOrDirname());
      int index = 0;
      if (name == null) {
        name = fileName;
        index = unnamed;
        unnamed++;
      }
      for (int i = 0; i < diagram.getNbImages(); i++) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        diagram.exportDiagram(os, i, format);
        int n = index + i;
        images.add(new RenderedImage(n == 0 ? name+".svg" : name+"_"+String.format("%03d", n)+".svg", os.toByteArray(), source));
      }
    }
  }

  /**
   * Put file's lines into exp, with the files it includes (!include, !include_once, !include_many) in place. An
   * included file has to be in one of the image source folders - where it really is, so .. and links can't get out.
   * Includes this can't do (the path uses a variable, a sub-part with !, !includesub, !includeurl) are left for PlantUML,
   * which can't read files or URLs in its sandbox, and reports them as errors. !include &lt;...&gt; is PlantUML's
   * standard library, which works in the sandbox. As in PlantUML, a file is included once, unless it's !include_many,
   * and only what's between @startuml and @enduml is included, if the file has them
   */
  private boolean expand(File source, File file, List<String> allowed, Expansion exp, int depth, List<ImageIssue> issues) throws IOException {
    String[] lines = FileUtilities.fileToLines(file);
    int start = 0;
    int end = lines.length;
    if (depth > 0) {
      for (int i = 0; i < lines.length; i++) {
        String t = lines[i].trim();
        if (t.startsWith("@startuml") && start == 0) {
          start = i + 1;
        } else if (t.startsWith("@enduml") && start > 0) {
          end = i;
          break;
        }
      }
    }
    for (int i = start; i < end; i++) {
      String line = lines[i];
      String t = line.trim();
      String keyword = t.contains(" ") ? t.substring(0, t.indexOf(" ")).toLowerCase() : t.toLowerCase();
      if (keyword.equals("!include") || keyword.equals("!include_once") || keyword.equals("!include_many")) {
        String target = unquote(t.substring(keyword.length()).trim());
        if (!target.startsWith("<") && !target.contains("$") && !target.contains("%") && !target.contains("!") && !target.contains("://")) {
          File inc = new File(target).isAbsolute() ? new File(target) : new File(file.getParentFile(), target);
          String path = inc.getCanonicalPath();
          if (!isInside(path, allowed)) {
            issues.add(new ImageIssue(source, file == source ? i + 1 : 0, "PlantUML: "+(file == source ? "" : file.getName()+" line "+(i+1)+": ")
                + "the diagram includes "+target+", which isn't in an image source folder, so it hasn't been rendered"));
            return false;
          }
          if (inc.isFile()) {
            if (depth >= MAX_INCLUDE_DEPTH) {
              issues.add(new ImageIssue(source, 0, "PlantUML: includes are nested more than "+MAX_INCLUDE_DEPTH+" deep, so the diagram hasn't been rendered"));
              return false;
            }
            if (exp.included.add(path) || keyword.equals("!include_many")) {
              if (!expand(source, inc, allowed, exp, depth + 1, issues)) {
                return false;
              }
            }
            continue;
          }
          // a file that isn't there: PlantUML says it can't include it
        }
      }
      exp.add(line, file, i + 1);
    }
    return true;
  }

  private String unquote(String s) {
    if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
      return s.substring(1, s.length() - 1);
    }
    return s;
  }

  private boolean isInside(String path, List<String> allowed) {
    for (String a : allowed) {
      if (path.startsWith(a + File.separator)) {
        return true;
      }
    }
    return false;
  }

  /**
   * the name after @startuml, which PlantUML treats as a file name. It can't choose where the image goes (it's always
   * the includes folder), so only its last segment is used, and a .svg on the end is ignored. A name ending with / is
   * a folder for PlantUML: there, the source file's name is used
   */
  private String nameFromStart(String s) {
    if (s == null) {
      return null;
    }
    s = s.trim().replace('\\', '/');
    if (s.isEmpty() || s.endsWith("/")) {
      return null;
    }
    s = s.substring(s.lastIndexOf('/') + 1);
    if (s.toLowerCase().endsWith(".svg")) {
      s = s.substring(0, s.length() - 4);
    }
    return s.isEmpty() || s.equals(".") || s.equals("..") ? null : s;
  }

  private void reportError(File source, Expansion exp, PSystemError error, List<ImageIssue> issues) {
    List<String> messages = new ArrayList<>();
    for (ErrorUml e : error.getErrorsUml()) {
      if (!messages.contains(e.getError())) {
        messages.add(e.getError());
      }
    }
    String msg = messages.isEmpty() ? "error in the diagram" : String.join("; ", messages);
    int line = 0;
    if (error.getLineLocation() != null) {
      // a line in the expanded source - which could be in an included file
      int pos = error.getLineLocation().getPosition();
      if (pos >= 0 && pos < exp.lines.size()) {
        if (exp.files.get(pos) == source) {
          line = exp.lineNumbers.get(pos);
        } else {
          msg = msg + " (at line "+exp.lineNumbers.get(pos)+" of "+exp.files.get(pos).getName()+")";
        }
      }
    }
    issues.add(new ImageIssue(source, line, "PlantUML: "+msg));
  }
}
