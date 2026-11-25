package org.hl7.fhir.igtools.publisher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scripts and programs committed to the IG's repository. IG editors are used to running the scripts in an IG's root
 * folder (_genonce, _updatePublisher...), and to running the jars and programs that sit beside them, and they arrive with
 * every pull, so anyone who can get a change into the repository can run code on the computer of everyone who builds
 * the IG. As an editorial practice, IGs don't contain scripts or programs: people who want them get them from their
 * source, and keep them out of the IG's repository.
 *
 * A script is a file with a script extension (see SCRIPT_EXTENSIONS) or whose first line starts with '#!'. A program is
 * a file with a program extension (see PROGRAM_EXTENSIONS), or whose content is one, whatever it is called: a native
 * executable or library (ELF, Mach-O, Windows PE), a Java class, or a jar (a zip with classes or a manifest in it - other
 * zips are fine). Only files git tracks count, so ones the user put in the IG's folder and git ignores are fine (when the
 * folder isn't a git repository, or git can't be run, every file in it counts). The executable bit doesn't count: plenty
 * of IGs have it on content files.
 *
 * For now, none of this stops the build: it's a warning in qa.html, saying what will happen in a future version. Then,
 * scripts and programs in the root folder will be refused on the ci-build, and locally the build will stop unless the
 * user has accepted them for the IG: with -allow-committed-scripts, which records the IG's folder (where it really is)
 * in "allowCommittedScripts" in fhir-settings.json (that already works, so people can accept them now). What they do is
 * not checked: accepting them is the user's decision. Ones in sub-folders are a warning, everywhere, now and then
 */
public class CommittedScriptPermission {

  public static final String PROPERTY = "allowCommittedScripts";
  public static final String PARAMETER = "-allow-committed-scripts";

  /** file extensions that make a file a script */
  public static final Set<String> SCRIPT_EXTENSIONS = Set.of("sh", "bash", "zsh", "bat", "cmd", "ps1", "command");

  /** file extensions that make a file a program */
  public static final Set<String> PROGRAM_EXTENSIONS = Set.of("jar", "war", "ear", "class", "exe", "dll", "msi", "so", "dylib");

  /** how far into a file to look for a Windows PE header */
  private static final int MAX_PE_OFFSET = 64 * 1024;

  /**
   * @param files the scripts and programs in the IG's repository (relative to its folder, with '/', see findScripts)
   * @param root the IG's folder
   * @param ciBuild whether this is the ci-build
   * @param parameter whether the user ran with -allow-committed-scripts
   * @param settingsPath fhir-settings.json (null if it isn't known)
   * @param warn where the warnings go
   */
  public static void check(List<String> files, String root, boolean ciBuild, boolean parameter, String settingsPath, Consumer<String> warn) throws IOException {
    List<String> inRoot = new ArrayList<>();
    List<String> inSubFolders = new ArrayList<>();
    for (String s : files) {
      (s.contains("/") ? inSubFolders : inRoot).add(s);
    }
    if (!inSubFolders.isEmpty()) {
      warn.accept("The IG's repository has "+count(inSubFolders)+" in sub-folders: "+list(inSubFolders)
          + ". The IG Publisher doesn't run them, but they arrive with every pull: don't run them unless you know what they do. Scripts and programs don't belong in an IG's repository");
    }
    if (inRoot.isEmpty()) {
      return;
    }
    String what = "The IG's repository has "+count(inRoot)+" in its root folder ("+root+"): "+list(inRoot);
    String why = "Scripts and programs don't belong in an IG's repository: they arrive with every pull, and people run them out of habit, "
        + "so anyone who can change the repository can run code on the computer of everyone who builds the IG. Remove them from the repository "
        + "(people who want them can get them from their source, and keep them out of the IG's repository)";
    if (ciBuild) {
      warn.accept(what+". "+why+". IMPORTANT: in a future version of the IG Publisher, the ci-build will refuse to build an IG that has these");
      return;
    }
    String ciBuildToo = "Note that in a future version of the IG Publisher, the ci-build will refuse to build an IG that has these, whatever you accept here";
    if (settingsPath != null && IGPermissions.isAllowed(settingsPath, PROPERTY, root)) {
      warn.accept(what+". These are accepted for this IG, as recorded in "+settingsPath+". Their content hasn't been checked. "+ciBuildToo);
      return;
    }
    if (parameter) {
      if (settingsPath != null) {
        IGPermissions.allow(settingsPath, PROPERTY, root);
        warn.accept(what+". These are accepted for this IG, as you said ("+PARAMETER+"): recorded in "+settingsPath+", so you won't be asked again for this IG. Their content hasn't been checked. "+ciBuildToo);
      } else {
        warn.accept(what+". These are accepted for this run, as you said ("+PARAMETER+"). It couldn't be recorded, because the FHIR settings file isn't known. Their content hasn't been checked. "+ciBuildToo);
      }
      return;
    }
    warn.accept(what+". "+why+". IMPORTANT: in a future version of the IG Publisher, the build will stop here, and the ci-build will refuse to build the IG. "
        + "If you accept these as they are, you can say so now by running the IG Publisher with '"+PARAMETER+"'"
        + (settingsPath != null ? " (recorded in "+settingsPath+", for this IG)" : "")
        + ", and local builds will go on then (but not the ci-build). The IG Publisher doesn't check what they do: accepting them is your decision");
  }

  /**
   * The scripts and programs in the IG's folder that git tracks, relative to the folder, with '/', sorted. When the
   * folder isn't in a git repository, or git can't be run, all the files in it (but not in .git)
   */
  public static List<String> findScripts(String root) throws IOException {
    Path r = Path.of(root).toAbsolutePath().normalize();
    List<String> files = gitTrackedFiles(r);
    if (files == null) {
      files = allFiles(r);
    }
    List<String> res = new ArrayList<>();
    for (String f : files) {
      if (isScriptOrProgram(r.resolve(f))) {
        res.add(f.replace('\\', '/'));
      }
    }
    Collections.sort(res);
    return res;
  }

  /**
   * whether the file is a script or a program: it has a script or program extension, or its content is one (a '#!'
   * line, a native executable or library, a Java class, a jar). One that isn't there isn't
   */
  static boolean isScriptOrProgram(Path file) {
    if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS) || Files.isDirectory(file)) {
      return false; // deleted but still tracked, or a submodule
    }
    String name = file.getFileName().toString();
    int i = name.lastIndexOf('.');
    if (i >= 0) {
      String ext = name.substring(i + 1).toLowerCase(Locale.ROOT);
      if (SCRIPT_EXTENSIONS.contains(ext) || PROGRAM_EXTENSIONS.contains(ext)) {
        return true;
      }
    }
    if (!Files.isRegularFile(file)) {
      return false;
    }
    try {
      byte[] head = readHead(file, 64);
      if (head.length < 2) {
        return false;
      }
      if (head[0] == '#' && head[1] == '!') {
        return true;
      }
      if (head.length >= 4) {
        int magic = ((head[0] & 0xFF) << 24) | ((head[1] & 0xFF) << 16) | ((head[2] & 0xFF) << 8) | (head[3] & 0xFF);
        switch (magic) {
        case 0x7F454C46: // ELF (Linux etc)
        case 0xFEEDFACE: // Mach-O 32 (macOS)
        case 0xFEEDFACF: // Mach-O 64
        case 0xCEFAEDFE: // Mach-O 32, little endian
        case 0xCFFAEDFE: // Mach-O 64, little endian
        case 0xCAFEBABE: // a Java class, or a Mach-O universal binary
          return true;
        case 0x504B0304: // a zip: a jar if it has classes or a manifest
          return isJar(file);
        default:
          break;
        }
      }
      return head[0] == 'M' && head[1] == 'Z' && isWindowsExecutable(file);
    } catch (IOException e) {
      return false;
    }
  }

  /** the first (up to) count bytes of the file */
  private static byte[] readHead(Path file, int count) throws IOException {
    try (InputStream in = Files.newInputStream(file)) {
      return in.readNBytes(count);
    }
  }

  /** 'MZ' is just the DOS stub: a Windows executable (or dll) has a PE header where the stub says (at 0x3C) */
  private static boolean isWindowsExecutable(Path file) throws IOException {
    byte[] head = readHead(file, 0x40);
    if (head.length < 0x40) {
      return false;
    }
    int offset = (head[0x3C] & 0xFF) | ((head[0x3D] & 0xFF) << 8) | ((head[0x3E] & 0xFF) << 16) | ((head[0x3F] & 0xFF) << 24);
    if (offset < 0x40 || offset > MAX_PE_OFFSET) {
      return false;
    }
    byte[] pe = readHead(file, offset + 4);
    return pe.length == offset + 4 && pe[offset] == 'P' && pe[offset + 1] == 'E' && pe[offset + 2] == 0 && pe[offset + 3] == 0;
  }

  /** a zip is a jar if it has a class or a manifest in it (only the names are read, not the content) */
  private static boolean isJar(Path file) {
    try (ZipFile zip = new ZipFile(file.toFile())) {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        String n = entries.nextElement().getName();
        if (n.endsWith(".class") || n.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
          return true;
        }
      }
      return false;
    } catch (IOException | RuntimeException e) {
      return false; // not a zip that can be read, so nothing can run it as a jar either
    }
  }

  /** the files git tracks in the folder (relative to it), or null if it isn't in a git repository, or git can't be run */
  static List<String> gitTrackedFiles(Path root) {
    try {
      Process p = new ProcessBuilder("git", "ls-files", "-z")
          .directory(root.toFile())
          .redirectError(ProcessBuilder.Redirect.DISCARD)
          .redirectInput(ProcessBuilder.Redirect.from(nullFile()))
          .start();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (InputStream in = p.getInputStream()) {
        in.transferTo(out);
      }
      if (!p.waitFor(60, TimeUnit.SECONDS)) {
        p.destroyForcibly();
        return null;
      }
      if (p.exitValue() != 0) {
        return null; // not a git repository (or one git won't work in)
      }
      List<String> res = new ArrayList<>();
      for (String s : out.toString(StandardCharsets.UTF_8).split("\0")) {
        if (!s.isEmpty()) {
          res.add(s);
        }
      }
      return res;
    } catch (IOException e) {
      return null; // no git
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }

  private static java.io.File nullFile() {
    return new java.io.File(System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows") ? "NUL" : "/dev/null");
  }

  /** every file in the folder (relative to it), not following links to folders, and not in .git */
  static List<String> allFiles(Path root) throws IOException {
    List<String> res = new ArrayList<>();
    if (!Files.isDirectory(root)) {
      return res;
    }
    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        return dir.getFileName() != null && ".git".equals(dir.getFileName().toString()) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (!attrs.isDirectory()) {
          res.add(root.relativize(file).toString());
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFileFailed(Path file, IOException exc) {
        return FileVisitResult.CONTINUE;
      }
    });
    return res;
  }

  private static String count(List<String> items) {
    return items.size() == 1 ? "a script or program" : items.size()+" scripts or programs";
  }

  private static String list(List<String> items) {
    return String.join(", ", items.size() > 10 ? items.subList(0, 10) : items)+(items.size() > 10 ? ", ..." : "");
  }
}
