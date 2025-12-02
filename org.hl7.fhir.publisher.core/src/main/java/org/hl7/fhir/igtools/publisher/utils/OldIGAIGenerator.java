package org.hl7.fhir.igtools.publisher.utils;

import org.apache.commons.codec.binary.Base64;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.ZipGenerator;
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

public class OldIGAIGenerator {

  public static void main(String[] args) throws IOException {
    new OldIGAIGenerator().execute(new File("/Users/grahamegrieve/web/www.hl7.org.fhir"));
    System.out.println("done");
  }

  private void execute(File dir) throws IOException {
    for (File f : dir.listFiles()) {
      if (f.isDirectory()) {
        execute(f);
      } else if (f.getName().equals("package.tgz")) {
        processIg(dir);
      }
    }
  }

  private void processIg(File dir) throws IOException {
    if (new File(Utilities.path(dir, "ai.zip")).exists()) {
      return;
    }
    List<LoadedResource> resources = new ArrayList<>();
    List<Page> pages = new ArrayList<>();

    // list all the resources in the IG, and note their type and whether they're an example
    NpmPackage npm = loadResources(dir, resources);
    if (npm != null) {
      // now list all the pages in the IG, and decide whether they are a page, a resource, or an alternate view of a resource
      long oldSize = loadPages(dir, pages, resources);
      long newSize = 0;

      ZipGenerator zip = new ZipGenerator(Utilities.path(dir, "ai-prep.zip"));
      StringBuilder llms = new StringBuilder();
      llms.append("# " + npm.vid() + ": " + npm.title() + "\r\n\r\n");
      llms.append("## Pages\r\n\r\n");
      for (Page p : pages) {
        if (p.f.getName().equals("index.html")) {
          newSize += produceMDForPage(llms, p, zip);
        }
      }
      for (Page p : pages) {
        if (!p.f.getName().equals("index.html") && p.kind == PageKind.PAGE) {
          newSize += produceMDForPage(llms, p, zip);
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
        newSize += produceMDForResource(llms, r, p, dir, zip);
      }
      zip.addFileSource("llms.txt", llms.toString(), false);
      zip.addFileSource("llms.md", llms.toString(), false);
      zip.close();
      new File(Utilities.path(dir, "ai-prep.zip")).renameTo(new File(Utilities.path(dir, "ai.zip")));
      FileUtilities.stringToFileIfDifferent(llms.toString(), Utilities.path(dir, "llms.txt"));
      newSize += llms.toString().length();
      String pct = oldSize == 0 ? "n/a" : "" + (100 - ((newSize * 100) / oldSize));
      System.out.println("Processed " + dir.getAbsolutePath() + " - % reduction = " + pct+" ("+oldSize+" ==> "+newSize+")");
    }
  }


  private Page getPageForResource(LoadedResource r, List<Page> pages) {
    for (Page p : pages) {
      if (p.f.getName().equals(r.filename)) {
        return p;
      }
    }
    return null;
  }

  private NpmPackage loadResources(File dir, List<LoadedResource> resources) throws IOException {
    NpmPackage npm = NpmPackage.fromPackage(new FileInputStream(Utilities.path(dir, "package.tgz")));
    if (npm.isCore() || !npm.hasFile("other", "spec.internals")) {
      return null;
    }
    SpecMapManager spm = new SpecMapManager(FileUtilities.streamToBytes(npm.load("other", "spec.internals")), npm.vid(), npm.fhirVersion());
    Set<String> ids = new HashSet<>();
    for (NpmPackage.PackagedResourceFile p : npm.listAllResources()) {
      JsonObject res = JsonParser.parseObject(npm.load(p.getFolder(), p.getFilename()));
      String rt = res.asString("resourceType");
      String id = res.asString("id");
      if (id != null && !ids.contains(rt + "/" + id)) {
        LoadedResource lr = new LoadedResource();
        resources.add(lr);
        if (res.has("text")) {
          res.remove("text");
        }
        ids.add(rt + "/" + id);
        if (res.has("url")) {
          lr.filename = spm.getPath(res.asString("url"), null, rt, id);
        } else {
          lr.filename = spm.getPath(rt, id);
        }
        if (Utilities.isAbsoluteUrl(lr.filename)) {
          lr.filename = lr.filename.substring(lr.filename.lastIndexOf('/') + 1);
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

  private int produceMDForPage(StringBuilder llms, Page p, ZipGenerator zip) throws IOException {
//    System.out.println("Processing "+p.f.getAbsolutePath());
    try {
      XhtmlNode xhtml = new XhtmlParser().setMustBeWellFormed(false).parse(new FileInputStream(p.f), "html");
      XhtmlNode x =  xhtml.firstNamedDescendent("head");
      x = x == null ? x : x.firstNamedDescendent("title");
      String title = x == null ? "Untitled" : x.allText();
      XhtmlToMarkdownConverter conv = new XhtmlToMarkdownConverter(false);
      conv.configureForIGs(true);
      String md = conv.convert(xhtml);
      String dest = FileUtilities.changeFileExt(p.f.getName(), ".md");
      llms.append("* [" + title + "](" + dest + ")\r\n");
      FileUtilities.stringToFileIfDifferent(md, Utilities.path(FileUtilities.getDirectoryForFile(p.f), dest));
      zip.addFileSource(dest, md, false);
      return md.length();
    } catch (Exception e) {
      throw new FHIRException("Unable to process " + p.f.getAbsolutePath(), e);
    }
  }

  private int produceMDForResource(StringBuilder llms, LoadedResource r, Page p, File dir, ZipGenerator zip) throws IOException {
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
                  r.resource.asString("contentType")+":\r\n\r\n```\r\n" +
                  Base64.decodeBase64(r.resource.asString("data")) +
                  "\r\n```\r\n";
        } else {
          md = md + "\r\n\r\n## Resource Binary Content\r\n\r\n" +
                  r.resource.asString("contentType")+":\r\n\r\n```\r\n" +
                  "{snip}" +
                  "\r\n```\r\n";
        }

      } else {
        md = md + "\r\n\r\n## Resource Content\r\n\r\n```json\r\n" +
                JsonParser.compose(r.resource, true) +
                "\r\n```\r\n";
      }
      if (r.filename == null) {
        r.filename = r.resource.asString("resourceType") + "-" + r.resource.asString("id") + ".html";
      }
      String dest = FileUtilities.changeFileExt(r.filename, ".md");
      llms.append("* [" + r.name + "](" + dest + ")\r\n");
      FileUtilities.stringToFileIfDifferent(md, Utilities.path(dir, dest));
      zip.addFileSource(dest, md, true);
      return md.length();
    } catch (Exception e) {
      throw new FHIRException("Unable to process " + p.f.getAbsolutePath(), e);
    }
  }

  private boolean isText(String contentType) {
    return contentType.startsWith("text/");
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
