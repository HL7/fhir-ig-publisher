package org.hl7.fhir.igtools.publisher;

import org.apache.commons.codec.binary.Base64;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.utilities.*;
import org.hl7.fhir.utilities.filesystem.ManagedFileAccess;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;
import org.hl7.fhir.utilities.xhtml.XhtmlToMarkdownConverter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * The AI generation code does this:
 *
 * create a file llms.txt which is actually mardkown (I don't know why they set it up that way)
 * for every generated html file, figure out whether it is: authored page, base resource page, or other resource page
 * for authored pages and base resource pages, convert the xhtml to markdown, stripping stuff from the templates
 * link to them from llms.txt in an organised fashion
 * create a zip file ai.zip so people can download all that
 *
 * I already processed all the already published IGs like thisGrahame Grieve: so see, say, http://hl7.org/fhir/us/core/llms.txt
 */
public class AIProcessor {
  private final String rootDir;
  private final Cache cache;

  /**
   * Watch mode: what the last run worked out from the files that Jekyll hasn't written again since, which the output
   * files no longer show (the html checks change them after this runs: see HTMLInspector.recordChecks())
   */
  public static class Cache {
    private String packageStamp;
    private NpmPackage npm;
    private final Map<Boolean, List<LoadedResource>> resources = new HashMap<>();
    // the title and markdown of a page
    private final Map<String, String[]> pages = new HashMap<>();
    // the markdown of a resource (they're from the package)
    private final Map<String, String> resourceMarkdown = new HashMap<>();
    // the zips the runs wrote: what each holds (a digest), and the file's stamp when it was written (see AiZip)
    private final Map<String, String[]> zips = new HashMap<>();
    private Set<String> fresh;
    private HTMLInspector.OutputRewriter rewriter;
    private final Set<String> written = new HashSet<>();

    /**
     * Before a run: the files in the output written since the last run (null: all of them), and how to write the
     * others again as Jekyll wrote them, if the run needs to read one it didn't read last time
     */
    public void prepare(Set<String> fresh, HTMLInspector.OutputRewriter rewriter) {
      this.fresh = fresh;
      this.rewriter = rewriter;
      written.clear();
    }

    /**
     * The files the last run wrote, or wrote again as Jekyll wrote them (the files it makes are only written if what
     * they hold is different)
     */
    public Set<String> getWritten() {
      return written;
    }

    private boolean isFresh(File f) {
      return fresh == null || fresh.contains(f.getAbsolutePath());
    }

    private void refresh(List<File> files) throws IOException {
      if (files.isEmpty()) {
        return;
      }
      List<String> paths = new ArrayList<>();
      for (File f : files) {
        paths.add(f.getAbsolutePath());
      }
      Set<String> unknown = rewriter == null ? new HashSet<>(paths) : rewriter.rewrite(paths);
      if (!unknown.isEmpty()) {
        throw new IOException("Jekyll can't write " + unknown.iterator().next() + " again");
      }
      fresh.addAll(paths);
      written.addAll(paths);
    }
  }

  public AIProcessor(String rootDir) {
    this(rootDir, null);
  }

  public AIProcessor(String rootDir, Cache cache) {
    this.rootDir = rootDir;
    this.cache = cache;
  }

  private NpmPackage loadPackage() throws IOException {
    File pf = new File(Utilities.path(rootDir, "package.tgz"));
    if (cache == null) {
      return readPackage(pf);
    }
    String stamp = pf.length() + ":" + pf.lastModified();
    if (cache.npm == null || !stamp.equals(cache.packageStamp)) {
      cache.npm = readPackage(pf);
      cache.packageStamp = stamp;
      cache.resources.clear();
      cache.resourceMarkdown.clear();
    }
    return cache.npm;
  }

  private static NpmPackage readPackage(File f) throws IOException {
    try (FileInputStream in = new FileInputStream(f)) {
      return NpmPackage.fromPackage(in);
    }
  }

  private void written(String path) {
    if (cache != null) {
      cache.written.add(new File(path).getAbsolutePath());
    }
  }

  /**
   * Writes a file the run makes; in watch mode only if what it holds is different, so that the steps after this only
   * redo the files that changed
   */
  private void writeText(String content, String path) throws IOException {
    if (deferred != null) {
      deferred.put(path, content);
      return;
    }
    if (cache != null) {
      File f = new File(path);
      byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
      if (f.isFile() && f.length() == bytes.length && Arrays.equals(Files.readAllBytes(f.toPath()), bytes)) {
        return;
      }
    }
    FileUtilities.stringToFile(content, path);
    written(path);
  }

  // watch mode, old templates: the files to write at the end (an old template's resource page is taken for a page
  // too, so its .md is written twice, and only the second is what's left)
  private Map<String, String> deferred;

  private void writeDeferred() throws IOException {
    Map<String, String> files = deferred;
    deferred = null;
    for (Map.Entry<String, String> e : files.entrySet()) {
      writeText(e.getValue(), e.getKey());
    }
  }

  /**
   * The ai.zip of a folder. In watch mode what goes in it is kept, and it's only written if that's different from
   * what the last run wrote to it, or the file isn't as that run left it
   */
  private final class AiZip {
    private final String path;
    private final String target;
    private final ZipGenerator zip;
    private final List<Object[]> entries = new ArrayList<>();

    /** The zip is written to path, and then renamed to target */
    private AiZip(String path, String target) throws IOException {
      this.path = path;
      this.target = target;
      this.zip = cache == null ? new ZipGenerator(path) : null;
    }

    private void addFileSource(String name, String content, boolean omitIfExists) throws IOException {
      if (zip != null) {
        zip.addFileSource(name, content, omitIfExists);
      } else {
        entries.add(new Object[] {name, content, omitIfExists});
      }
    }

    private void close() throws IOException {
      if (zip != null) {
        zip.close();
        try {
          rename();
        } catch (IOException e) {
          // as before: the zip there is left as it is
          System.out.println("Unable to replace " + target + " with " + path + ": " + e.getMessage());
        }
        return;
      }
      String digest = digest();
      String[] last = cache.zips.remove(target);
      if (last != null && last[0].equals(digest) && last[1].equals(stamp(target))) {
        cache.zips.put(target, last);
        return;
      }
      ZipGenerator z = new ZipGenerator(path);
      for (Object[] e : entries) {
        z.addFileSource((String) e[0], (String) e[1], (Boolean) e[2]);
      }
      z.close();
      rename();
      written(target);
      String stamp = stamp(target);
      if (stamp != null) {
        cache.zips.put(target, new String[] {digest, stamp});
      }
    }

    /** Moves the zip to target (replacing what is there: File.renameTo doesn't on Windows) */
    private void rename() throws IOException {
      if (!path.equals(target)) {
        Files.move(Paths.get(path), Paths.get(target), StandardCopyOption.REPLACE_EXISTING);
      }
    }

    private String digest() throws IOException {
      try {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        for (Object[] e : entries) {
          for (Object o : e) {
            byte[] b = o.toString().getBytes(StandardCharsets.UTF_8);
            md.update(ByteBuffer.allocate(4).putInt(b.length).array());
            md.update(b);
          }
        }
        return Base64.encodeBase64String(md.digest());
      } catch (NoSuchAlgorithmException e) {
        throw new IOException(e);
      }
    }
  }

  /** A file's size, times and inode, or null if it isn't there */
  private static String stamp(String path) {
    File f = new File(path);
    if (!f.isFile()) {
      return null;
    }
    String res = f.length() + ":" + f.lastModified();
    try {
      Map<String, Object> a = Files.readAttributes(f.toPath(), "unix:ino,ctime");
      res = res + ":" + a.get("ino") + ":" + ((FileTime) a.get("ctime")).to(TimeUnit.NANOSECONDS);
    } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
      // the size and modification time
    }
    return res;
  }

  /**
   * Watch mode: makes sure that the pages the run reads that it can't take from the cache are as Jekyll wrote them
   */
  private void refreshPages(List<Page> pages, List<LoadedResource> resources) throws IOException {
    if (cache == null || cache.fresh == null) {
      return;
    }
    List<File> files = new ArrayList<>();
    for (Page p : pages) {
      if (!cache.isFresh(p.f) && (p.kind == PageKind.PAGE || p.f.getName().equals("index.html")) && !cache.pages.containsKey(p.f.getAbsolutePath())) {
        files.add(p.f);
      }
    }
    Map<String, Page> byName = byName(pages);
    for (LoadedResource r : resources) {
      Page p = byName.get(r.filename);
      if (p != null && !cache.isFresh(p.f) && !files.contains(p.f) && !cache.resourceMarkdown.containsKey(resourceKey(r, p))) {
        files.add(p.f);
      }
    }
    cache.refresh(files);
  }

  private String resourceKey(LoadedResource r, Page p) {
    return (p == null ? "" : p.f.getAbsolutePath()) + "|" + r.resource.asString("resourceType") + "|" + r.resource.asString("id");
  }

  /**
   * process this on a per language basis
   *
   * @param langs
   * @throws IOException
   */
  public void processNewTemplates(List<String> langs) throws IOException {
    // processing new templates is different to old template because of the layout with regard to
    // languages, and because there's a specific .ai file
    NpmPackage npm = loadPackage();

    for (String lang : langs) {
      processFolderNew(new File(Utilities.path(rootDir, lang)), npm, lang);
    }
    for (File f : new File(rootDir).listFiles()) {
      if (f.getName().endsWith(".ai.html")) {
        f.delete();
      }
    }
    if (!langs.isEmpty()) {
      String llms = FileUtilities.fileToString(Utilities.path(rootDir, langs.get(0), "llms.txt"));
      llms = llms.replace("](", "](" + langs.get(0) + "/");
      writeText(llms, Utilities.path(rootDir, "llms.txt"));
    }
  }

  /**
   * process this for an old template
   *
   * @throws IOException
   */
  public void processOldTemplates() throws IOException {
    NpmPackage npm = loadPackage();
    processFolderOld(new File(rootDir), npm);
  }

  public void processFolderNew(File dir, NpmPackage npm, String lang) throws IOException {
    List<LoadedResource> resources = new ArrayList<>();
    List<Page> pages = new ArrayList<>();

    // list all the resources in the IG, and note their type and whether they're an example
    loadResources(dir, resources, npm, true);

    // now list all the pages in the IG, and decide whether they are a page, a resource, or an alternate view of a resource
    long oldSize = loadPages(dir, pages, resources);
    long newSize = 0;
    refreshPages(pages, resources);

    AiZip zip = new AiZip(Utilities.path(dir, "ai.zip"), Utilities.path(dir, "ai.zip"));
    StringBuilder llms = new StringBuilder();
    llms.append("# " + npm.vid() + ": " + npm.title() + "("+lang+")\r\n\r\n");
    llms.append("## Pages\r\n\r\n");
    for (Page p : pages) {
      if (p.f.getName().equals("index.html")) {
        newSize += produceMDForPage(llms, p, zip, npm);
      }
    }
    for (Page p : pages) {
      if (!p.f.getName().equals("index.html") && p.kind == PageKind.PAGE) {
        newSize += produceMDForPage(llms, p, zip, npm);
      }
    }
    llms.append("\r\n## Resources\r\n");
    resources.sort(new LoadedResourceSorter());
    String t = null;
    Map<String, Page> byName = byName(pages);
    for (LoadedResource r : resources) {
      Page p = byName.get(r.filename);
      if (!r.type.equals(t)) {
        t = r.type;
        llms.append("\r\n### " + t.substring(1) + "\r\n\r\n");
      }
      String bn = r.filename;
      r.filename = r.filename.replace(".html", ".ai.html");
      // in watch mode it's written where it ends up, if it's different
      newSize += produceMDForResource(llms, r, p, dir, zip, true, cache == null ? null : file(dir, bn, ".md"));
      file(dir, r.filename).delete();
      if (cache == null) {
        file(dir, r.filename, ".md").renameTo(file(dir, bn, ".md"));
      } else {
        file(dir, r.filename, ".md").delete();
      }
    }
    zip.addFileSource("llms.txt", llms.toString(), false);
    zip.addFileSource("llms.md", llms.toString(), false);
    zip.close();
    writeText(llms.toString(), Utilities.path(dir, "llms.txt"));
    newSize += llms.toString().length();
    String pct = oldSize == 0 ? "n/a" : "" + (100 - ((newSize * 100) / oldSize));
    System.out.println("AI format for " + dir.getAbsolutePath() + " - % reduction = " + pct);
  }

  private File file(File dir, String s, String ext) throws IOException {
    return ManagedFileAccess.file(Utilities.path(dir, FileUtilities.changeFileExt(s, ext)));
  }

  private File file(File dir, String s) throws IOException {
    return ManagedFileAccess.file(Utilities.path(dir, s));
  }

  public void processFolderOld(File dir, NpmPackage npm) throws IOException {
    deferred = cache == null ? null : new LinkedHashMap<>();
    List<LoadedResource> resources = new ArrayList<>();
    List<Page> pages = new ArrayList<>();

    // list all the resources in the IG, and note their type and whether they're an example
    loadResources(dir, resources, npm, false);
    // now list all the pages in the IG, and decide whether they are a page, a resource, or an alternate view of a resource
    long oldSize = loadPages(dir, pages, resources);
    long newSize = 0;
    refreshPages(pages, resources);

    AiZip zip = new AiZip(Utilities.path(dir, "ai-prep.zip"), Utilities.path(dir, "ai.zip"));
    StringBuilder llms = new StringBuilder();
    llms.append("# " + npm.vid() + ": " + npm.title() + "\r\n\r\n");
    llms.append("## Pages\r\n\r\n");
    for (Page p : pages) {
      if (p.f.getName().equals("index.html")) {
        newSize += produceMDForPage(llms, p, zip, npm);
      }
    }
    for (Page p : pages) {
      if (!p.f.getName().equals("index.html") && p.kind == PageKind.PAGE) {
        newSize += produceMDForPage(llms, p, zip, npm);
      }
    }
    llms.append("\r\n## Resources\r\n");
    resources.sort(new LoadedResourceSorter());
    String t = null;
    Map<String, Page> byName = byName(pages);
    for (LoadedResource r : resources) {
      Page p = byName.get(r.filename);
      if (!r.type.equals(t)) {
        t = r.type;
        llms.append("\r\n### " + t.substring(1) + "\r\n\r\n");
      }
      newSize += produceMDForResource(llms, r, p, dir, zip, false, null);
    }
    zip.addFileSource("llms.txt", llms.toString(), false);
    zip.addFileSource("llms.md", llms.toString(), false);
    zip.close();
    writeText(llms.toString(), Utilities.path(dir, "llms.txt"));
    if (deferred != null) {
      writeDeferred();
    }
    newSize += llms.toString().length();
    String pct = oldSize == 0 ? "n/a" : "" + (100 - ((newSize * 100) / oldSize));
    System.out.println("Processed " + dir.getAbsolutePath() + " - % reduction = " + pct + " (" + oldSize + " ==> " + newSize + ")");
  }


  /** The pages by their file names (the first one, if two have the same name): the page of a resource */
  private Map<String, Page> byName(List<Page> pages) {
    Map<String, Page> res = new HashMap<>();
    for (Page p : pages) {
      res.putIfAbsent(p.f.getName(), p);
    }
    return res;
  }

  private NpmPackage loadResources(File dir, List<LoadedResource> resources, NpmPackage npm, boolean newML) throws IOException {
    if (cache != null) {
      List<LoadedResource> list = cache.resources.get(newML);
      if (list == null) {
        list = new ArrayList<>();
        readResources(list, npm, newML);
        cache.resources.put(newML, list);
      }
      for (LoadedResource lr : list) {
        resources.add(lr.copy());
      }
      return npm;
    }
    readResources(resources, npm, newML);
    return npm;
  }

  private void readResources(List<LoadedResource> resources, NpmPackage npm, boolean newML) throws IOException {
    SpecMapManager spm = new SpecMapManager(FileUtilities.streamToBytes(npm.load("other", "spec.internals")), npm.vid(), npm.fhirVersion());
    Set<String> ids = new HashSet<>();
    for (NpmPackage.PackagedResourceFile p : npm.listAllResources()) {
      JsonObject res = JsonParser.parseObject(npm.load(p.getFolder(), p.getFilename()));
      String rt = res.asString("resourceType");
      if (Utilities.isAbsoluteUrl(rt)) {
        rt = "Binary-"+tail(rt);
      }
      String id = res.asString("id");
      if (id != null && !ids.contains(rt + "/" + id)) {
        LoadedResource lr = new LoadedResource();
        resources.add(lr);
        if (res.has("text")) {
          res.remove("text");
        }
        ids.add(rt + "/" + id);
        if (newML) {
          lr.filename = rt+"-"+id+".html";
        } else {
          if (res.has("url")) {
            lr.filename = spm.getPath(res.asString("url"), null, rt, id);
          } else {
            lr.filename = spm.getPath(rt, id);
          }
          if (Utilities.isAbsoluteUrl(lr.filename)) {
            lr.filename = lr.filename.substring(lr.filename.lastIndexOf('/') + 1);
          }
        }
        lr.resource = res;
        lr.name = readName(res);
        lr.example = p.isExample();
        switch (rt) {
          case "StructureDefinition":
            res.remove("snapshot");
            if ("Extension".equals(res.asString("type"))) {
              lr.type = "4Extensions";
            } else if ("Logical".equals(res.asString("kind"))) {
              lr.type = "5Logical Models";
            } else if ("constraint".equals(res.asString("derivation"))) {
              lr.type = "3" + Utilities.capitalize(res.asString("kind")) + " Profiles";
            } else {
              lr.type = "2" + Utilities.capitalize(res.asString("kind") + "s");
            }
            break;
          case "ValueSet":
            if (res.has("compose") && res.has("expansion")) {
              res.remove("expansion");
            }
            lr.type = "1ValueSets";
            break;
          case "CodeSystem":
            lr.type = "0CodeSystems";
            break;
          default:
            if (p.isExample()) {
              lr.type = "9Examples";
              lr.name = lr.name + " (" + rt + ")";
            } else {
              lr.type = "8" + Utilities.pluralize(rt, 0);
            }
        }
      }
    }
  }

  private String tail(String rt) {
    return rt.contains("/") ? rt.substring(rt.lastIndexOf('/') + 1) : rt;
  }

  private int produceMDForPage(StringBuilder llms, Page p, AiZip zip, NpmPackage npm) throws IOException {
//    System.out.println("Processing "+p.f.getAbsolutePath());
    try {
      String[] tm = cache == null || cache.isFresh(p.f) ? null : cache.pages.get(p.f.getAbsolutePath());
      if (tm == null) {
        XhtmlNode xhtml = new XhtmlParser().setMustBeWellFormed(false).parse(p.f, "html");
        XhtmlNode x = xhtml.firstNamedDescendent("head");
        x = x == null ? x : x.firstNamedDescendent("title");
        XhtmlToMarkdownConverter conv = new XhtmlToMarkdownConverter(false);
        conv.configureForIGs(true);
        tm = new String[] {x == null ? "Untitled" : x.allText(), conv.convert(xhtml)};
        if (cache != null) {
          cache.pages.put(p.f.getAbsolutePath(), tm);
        }
      }
      String title = tm[0];
      String md = tm[1];
      String dest = FileUtilities.changeFileExt(p.f.getName(), ".md");

      if (npm.title() != null) {
        title = title.replace(npm.title(), "");
      }
      if (npm.id() != null) {
        title = title.replace(npm.id(), "");
      }
      if (npm.version() != null) {
        title = title.replace("v" + npm.version(), "").replace(npm.version(), "");
      }
      if (npm.name() != null) {
        title = title.replace(npm.name(), "");
      }
      title = title.trim();
      if (title.endsWith("-")) {
        title = title.substring(0, title.length() - 1).trim();
      }
      llms.append("* [" + title + "](" + dest + ")\r\n");
      if (cache == null) {
        FileUtilities.stringToFileIfDifferent(md, Utilities.path(FileUtilities.getDirectoryForFile(p.f), dest));
      } else {
        writeText(md, Utilities.path(FileUtilities.getDirectoryForFile(p.f), dest));
      }
      zip.addFileSource(dest, md, false);
      return md.length();
    } catch (Exception e) {
      throw new FHIRException("Unable to process " + p.f.getAbsolutePath(), e);
    }
  }

  /** target: where the markdown is written, if it isn't next to the resource's page */
  private int produceMDForResource(StringBuilder llms, LoadedResource r, Page p, File dir, AiZip zip, boolean newML, File target) throws IOException {
//    System.out.println("Processing "+p.f.getAbsolutePath());
    try {
      String key = cache == null ? null : resourceKey(r, p);
      String md = key == null || (p != null && cache.isFresh(p.f)) ? null : cache.resourceMarkdown.get(key);
      if (md == null) {
        md = resourceMarkdown(r, p);
        if (key != null) {
          cache.resourceMarkdown.put(key, md);
        }
      }
      if (r.filename == null) {
        r.filename = tail(r.resource.asString("resourceType")) + "-" + r.resource.asString("id") + ".html";
      }
      String dest = FileUtilities.changeFileExt(r.filename, ".md");
      if (newML) {
        llms.append("* [" + r.name + "](" + dest.replace(".ai", "")+ ")\r\n");
      } else {
        llms.append("* [" + r.name + "](" + dest + ")\r\n");
      }
      writeText(md, target == null ? Utilities.path(dir, dest) : target.getAbsolutePath());
      zip.addFileSource(dest, md, true);
      return md.length();
    } catch (Exception e) {
      if (p != null) {
        throw new FHIRException("Unable to process page " + p.f.getAbsolutePath(), e);
      } else {
        throw new FHIRException("Unable to process resource " + r.filename, e);
      }
    }
  }

  private String resourceMarkdown(LoadedResource r, Page p) throws IOException {
    String md;
    if (p != null) {
      XhtmlNode xhtml = new XhtmlParser().setMustBeWellFormed(false).parse(p.f, "html");
      switch (r.resource.asString("resourceType")) {
        case "CodeSystem":
          stripDiv(xhtml, "defines the following code");
          break;
        case "ValueSet":
          stripDiv(xhtml, "Include these");
          stripDiv(xhtml, "Expansion performed internally based on");
          stripDiv(xhtml, "Expansion based on");
          break;
        case "StructureDefinition":
          stripDiv(xhtml, "Differential Table");
        default:
          stripDiv(xhtml, "Generated Narrative: ");
          break;
      }
      XhtmlToMarkdownConverter conv = new XhtmlToMarkdownConverter(false);
      conv.configureForIGs(true);
      md = conv.convert(xhtml);
    } else {
      md = "# Resource " + r.name + "\r\n\r\n";
    }
    if ("Binary".equals(r.resource.asString("resourceType"))) {
      if (isText(r.resource.asString("contentType"))) {
        md = md + "\r\n\r\n## Resource Binary Content\r\n\r\n" +
                r.resource.asString("contentType") + ":\r\n\r\n```\r\n" +
                Base64.decodeBase64(r.resource.asString("data")) +
                "\r\n```\r\n";
      } else {
        md = md + "\r\n\r\n## Resource Binary Content\r\n\r\n" +
                r.resource.asString("contentType") + ":\r\n\r\n```\r\n" +
                "{snip}" +
                "\r\n```\r\n";
      }

    } else {
      md = md + "\r\n\r\n## Resource Content\r\n\r\n```json\r\n" +
              JsonParser.compose(r.resource, true) +
              "\r\n```\r\n";
    }
    return md;
  }

  private boolean isText(String contentType) {
    return (contentType.startsWith("text/"));
  }

  private void stripDiv(XhtmlNode x, String s) {
    List<XhtmlNode> toDelete = new ArrayList<>();
    for (XhtmlNode c : x.getChildNodes()) {
      if ("div".equals(c.getName()) && hasTextNotInDiv(c, s)) {
        toDelete.add(c);
      } else {
        stripDiv(c, s);
      }
    }
    x.getChildNodes().removeAll(toDelete);
  }

  private boolean hasTextNotInDiv(XhtmlNode c, String s) {
    for (XhtmlNode c1 : c.getChildNodes()) {
      if (c1.getNodeType() == NodeType.Text && c1.getContent().contains(s)) {
        return true;
      }
      if (!"div".equals(c1.getName())) {
        if (hasTextNotInDiv(c1, s)) {
          return true;
        }
      }
    }
    return false;
  }

  private long loadPages(File dir, List<Page> pages, List<LoadedResource> resources) {
    long res = 0;
    ResourceFileNames resourceFiles = new ResourceFileNames();
    for (LoadedResource lr : resources) {
      if (lr.filename != null) {
        resourceFiles.add(lr.filename);
      }
    }
    // in name order: the lists of pages and the zip don't depend on the order the file system lists them in
    File[] files = dir.listFiles();
    Arrays.sort(files);
    for (File f : files) {
      if ((f.getName().endsWith(".html") || f.getName().endsWith(".json") || f.getName().endsWith(".xml")) && !f.getName().startsWith("qa")) {
        res += f.length();
      }
      if (f.getName().endsWith(".html") && !f.getName().startsWith("qa") && !Utilities.existsInList(f.getName(),
              "toc.html", "searchform.html")) {
        boolean found = resourceFiles.isResourceFile(f.getName());
        boolean found2 = resourceFiles.startsWithResourceFile(f.getName());
        if (found) {
          pages.add(new Page(PageKind.RESOURCE, f));
        } else if (!found2) {
          pages.add(new Page(PageKind.PAGE, f));
        }
      }
    }
    return res;
  }

  private String readName(JsonObject res) {
    if (res.has("title")) {
      JsonElement title = res.get("title");
      if (title.isJsonPrimitive()) {
        return title.asString();
      }
    }
    if (res.has("name")) {
      JsonElement title = res.get("name");
      if (title.isJsonPrimitive()) {
        return title.asString();
      }
    }
    if (res.has("id")) {
      JsonElement title = res.get("id");
      if (title.isJsonPrimitive()) {
        return title.asString();
      }
    }
    return null; // can't get here, really
  }


  /**
   * The names of the files the resources were loaded from. The lower case names without extensions
   * are worked out once, rather than for every page.
   */
  static class ResourceFileNames {
    private final List<String> names = new ArrayList<>();
    private final List<String> stems = new ArrayList<>();

    void add(String filename) {
      names.add(filename);
      stems.add(FileUtilities.changeFileExt(filename.toLowerCase(), ""));
    }

    /** @return true if a resource was loaded from a file with this name, ignoring case */
    boolean isResourceFile(String name) {
      for (String n : names) {
        if (n.equalsIgnoreCase(name)) {
          return true;
        }
      }
      return false;
    }

    /**
     * @return true if the name, in lower case and without its extension, starts with the lower case
     *   name of a resource's file without its extension
     */
    boolean startsWithResourceFile(String name) {
      String n = FileUtilities.changeFileExt(name.toLowerCase(), "");
      for (String stem : stems) {
        if (n.startsWith(stem)) {
          return true;
        }
      }
      return false;
    }
  }

  private enum PageKind {PAGE, RESOURCE}

  ;

  private class LoadedResource {
    private JsonObject resource;
    private String name;
    private String type;
    private boolean example;
    private boolean listed;
    private String filename;

    private LoadedResource copy() {
      LoadedResource res = new LoadedResource();
      res.resource = resource;
      res.name = name;
      res.type = type;
      res.example = example;
      res.listed = listed;
      res.filename = filename;
      return res;
    }
  }

  private class Page {
    private PageKind kind;
    private File f;

    public Page(PageKind pageKind, File f) {
      this.kind = pageKind;
      this.f = f;
    }
  }

  private class LoadedResourceSorter implements Comparator<LoadedResource> {

    @Override
    public int compare(LoadedResource o1, LoadedResource o2) {
      return o1.type.compareTo(o2.type);
    }
  }

}
