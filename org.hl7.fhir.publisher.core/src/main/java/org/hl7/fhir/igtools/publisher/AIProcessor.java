package org.hl7.fhir.igtools.publisher;

import org.apache.commons.codec.binary.Base64;
import org.eclipse.persistence.internal.sessions.DirectCollectionChangeRecord;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.utils.OldIGAIGenerator;
import org.hl7.fhir.r5.model.ImplementationGuide;
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
import java.util.*;

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

  public AIProcessor(String rootDir) {
    this.rootDir = rootDir;
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
    NpmPackage npm = NpmPackage.fromPackage(new FileInputStream(Utilities.path(rootDir, "package.tgz")));

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
      FileUtilities.stringToFile(llms, Utilities.path(rootDir, "llms.txt"));
    }
  }

  /**
   * process this for an old template
   *
   * @throws IOException
   */
  public void processOldTemplates() throws IOException {
    NpmPackage npm = NpmPackage.fromPackage(new FileInputStream(Utilities.path(rootDir, "package.tgz")));
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

    ZipGenerator zip = new ZipGenerator(Utilities.path(dir, "ai.zip"));
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
    for (LoadedResource r : resources) {
      Page p = getPageForResource(r, pages);
      if (!r.type.equals(t)) {
        t = r.type;
        llms.append("\r\n### " + t.substring(1) + "\r\n\r\n");
      }
      String bn = r.filename;
      r.filename = r.filename.replace(".html", ".ai.html");
      newSize += produceMDForResource(llms, r, p, dir, zip, true);
      file(dir, r.filename).delete();
      file(dir, r.filename, ".md").renameTo(file(dir, bn, ".md"));
    }
    zip.addFileSource("llms.txt", llms.toString(), false);
    zip.addFileSource("llms.md", llms.toString(), false);
    zip.close();
    FileUtilities.stringToFile(llms.toString(), Utilities.path(dir, "llms.txt"));
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
    List<LoadedResource> resources = new ArrayList<>();
    List<Page> pages = new ArrayList<>();

    // list all the resources in the IG, and note their type and whether they're an example
    loadResources(dir, resources, npm, false);
    // now list all the pages in the IG, and decide whether they are a page, a resource, or an alternate view of a resource
    long oldSize = loadPages(dir, pages, resources);
    long newSize = 0;

    ZipGenerator zip = new ZipGenerator(Utilities.path(dir, "ai-prep.zip"));
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
    for (LoadedResource r : resources) {
      Page p = getPageForResource(r, pages);
      if (!r.type.equals(t)) {
        t = r.type;
        llms.append("\r\n### " + t.substring(1) + "\r\n\r\n");
      }
      newSize += produceMDForResource(llms, r, p, dir, zip, false);
    }
    zip.addFileSource("llms.txt", llms.toString(), false);
    zip.addFileSource("llms.md", llms.toString(), false);
    zip.close();
    new File(Utilities.path(dir, "ai-prep.zip")).renameTo(new File(Utilities.path(dir, "ai.zip")));
    FileUtilities.stringToFile(llms.toString(), Utilities.path(dir, "llms.txt"));
    newSize += llms.toString().length();
    String pct = oldSize == 0 ? "n/a" : "" + (100 - ((newSize * 100) / oldSize));
    System.out.println("Processed " + dir.getAbsolutePath() + " - % reduction = " + pct + " (" + oldSize + " ==> " + newSize + ")");
  }


  private Page getPageForResource(LoadedResource r, List<Page> pages) {
    for (Page p : pages) {
      if (p.f.getName().equals(r.filename)) {
        return p;
      }
    }
    return null;
  }

  private NpmPackage loadResources(File dir, List<LoadedResource> resources, NpmPackage npm, boolean newML) throws IOException {
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
    return npm;
  }

  private String tail(String rt) {
    return rt.contains("/") ? rt.substring(rt.lastIndexOf('/') + 1) : rt;
  }

  private int produceMDForPage(StringBuilder llms, Page p, ZipGenerator zip, NpmPackage npm) throws IOException {
//    System.out.println("Processing "+p.f.getAbsolutePath());
    try {
      XhtmlNode xhtml = new XhtmlParser().setMustBeWellFormed(false).parse(new FileInputStream(p.f), "html");
      XhtmlNode x = xhtml.firstNamedDescendent("head");
      x = x == null ? x : x.firstNamedDescendent("title");
      String title = x == null ? "Untitled" : x.allText();
      XhtmlToMarkdownConverter conv = new XhtmlToMarkdownConverter(false);
      conv.configureForIGs(true);
      String md = conv.convert(xhtml);
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
      FileUtilities.stringToFileIfDifferent(md, Utilities.path(FileUtilities.getDirectoryForFile(p.f), dest));
      zip.addFileSource(dest, md, false);
      return md.length();
    } catch (Exception e) {
      throw new FHIRException("Unable to process " + p.f.getAbsolutePath(), e);
    }
  }

  private int produceMDForResource(StringBuilder llms, LoadedResource r, Page p, File dir, ZipGenerator zip, boolean newML) throws IOException {
//    System.out.println("Processing "+p.f.getAbsolutePath());
    try {
      String md;
      if (p != null) {
        XhtmlNode xhtml = new XhtmlParser().setMustBeWellFormed(false).parse(new FileInputStream(p.f), "html");
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
      if (r.filename == null) {
        r.filename = tail(r.resource.asString("resourceType")) + "-" + r.resource.asString("id") + ".html";
      }
      String dest = FileUtilities.changeFileExt(r.filename, ".md");
      if (newML) {
        llms.append("* [" + r.name + "](" + dest.replace(".ai", "")+ ")\r\n");
      } else {
        llms.append("* [" + r.name + "](" + dest + ")\r\n");
      }
      FileUtilities.stringToFile(md, Utilities.path(dir, dest));
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
    for (File f : dir.listFiles()) {
      if ((f.getName().endsWith(".html") || f.getName().endsWith(".json") || f.getName().endsWith(".xml")) && !f.getName().startsWith("qa")) {
        res += f.length();
      }
      if (f.getName().endsWith(".html") && !f.getName().startsWith("qa") && !Utilities.existsInList(f.getName(),
              "toc.html", "searchform.html")) {
        boolean found = false;
        boolean found2 = false;
        for (LoadedResource lr : resources) {
          if (lr.filename != null) {
            if (lr.filename.equalsIgnoreCase(f.getName())) {
              found = true;
            }
            String n = FileUtilities.changeFileExt(f.getName().toLowerCase(), "");
            if (n.startsWith(FileUtilities.changeFileExt(lr.filename.toLowerCase(), ""))) {
              found2 = true;
            }
          }
        }
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


  private enum PageKind {PAGE, RESOURCE}

  ;

  private class LoadedResource {
    private JsonObject resource;
    private String name;
    private String type;
    private boolean example;
    private boolean listed;
    private String filename;
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
