package org.hl7.fhir.igtools.publisher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.hl7.fhir.model.core.Extension;
import org.hl7.fhir.model.core.ImplementationGuide;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;

/**
 * Redirects for content that used to be published in this IG and is now published in another one, as declared by
 * the new-location extensions on the ImplementationGuide.
 *
 * For each new-location in effect for this version of the IG, the build writes redirect pages where the content
 * would have been: the pages of a moved artifact (as named by the template) or the moved page, in the root and in
 * each language folder, and for an artifact, its canonical path (Type/id/). The website publication copies these
 * like any other page. Servers that route Type/* through a script (asp-new) get the redirects from the
 * new-locations in spec.internals.
 */
public class NewLocationRedirects {

  public static final String EXT_NEW_LOCATION = "http://hl7.org/fhir/tools/StructureDefinition/new-location";
  public static final String SPEC_INTERNALS_KEY = "new-locations";

  private static final String PATH = "ImplementationGuide.extension('"+EXT_NEW_LOCATION+"')";
  private static final String[] FORMATS = {"xml", "json", "ttl"};

  public static class NewLocation {
    private final String artifact;
    private final String page;
    private final String newSpec;
    private final String rename;
    private final String version;

    public NewLocation(String artifact, String page, String newSpec, String rename, String version) {
      this.artifact = artifact;
      this.page = page;
      this.newSpec = newSpec;
      this.rename = rename;
      this.version = version;
    }

    /**
     * @return what moved: Type/id for an artifact, or the page name
     */
    public String local() {
      return artifact != null ? artifact : page;
    }
  }

  private final List<ValidationMessage> errors;
  private final List<NewLocation> inEffect = new ArrayList<>();
  private final Map<String, String> redirects = new TreeMap<>();

  public NewLocationRedirects(List<ValidationMessage> errors) {
    this.errors = errors;
  }

  /**
   * Read the new-location extensions of the IG, and keep the ones in effect for this version of it: a new-location
   * applies from its version on, so a build of an earlier version (e.g. a technical correction) that still has the
   * content doesn't redirect it
   */
  public void load(ImplementationGuide ig, String igVersion) {
    for (Extension ext : ig.getExtensionsByUrl(EXT_NEW_LOCATION)) {
      NewLocation nl = new NewLocation(part(ext, "artifact"), part(ext, "page"), part(ext, "newSpec"), part(ext, "rename"), part(ext, "version"));
      if (check(nl, nl.artifact == null || nl.page == null, "has both an artifact and a page - it must have one of them")
          && check(nl, nl.artifact != null || nl.page != null, "has neither an artifact nor a page - it must have one of them")
          && check(nl, nl.artifact == null || nl.artifact.matches("[A-Z][A-Za-z]+/[A-Za-z0-9\\-\\.]{1,64}"), "artifact '"+nl.artifact+"' must be a local reference of the form Type/id")
          && check(nl, nl.page == null || !(nl.page.endsWith(".html") || nl.page.contains("/")), "page '"+nl.page+"' must be a page name, without a folder or .html")
          && check(nl, nl.newSpec != null && Utilities.isAbsoluteUrl(nl.newSpec), "newSpec '"+nl.newSpec+"' must be the absolute URL of the IG that the content is now in")
          && check(nl, nl.version != null, "has no version - it must say the version of this IG from which the content is found at its new location")) {
        int c;
        try {
          c = VersionUtilities.compareVersions(igVersion, nl.version);
        } catch (Exception e) {
          check(nl, false, "version '"+nl.version+"' is not a valid version ("+e.getMessage()+")");
          continue;
        }
        if (c >= 0) {
          inEffect.add(nl);
        } else {
          message(nl, IssueSeverity.INFORMATION, "isn't in effect until version "+nl.version+" (this is version "+igVersion+")");
        }
      }
    }
  }

  /**
   * Write the redirect pages into the IG output. Run after the IG pages have been generated, so that pages the IG
   * still generates aren't replaced
   *
   * @param defaults the template's default publishing configuration, which names the pages of each resource type
   * @param resources Type/id of the resources in this IG
   * @param langs the languages of a multi-language IG, each with its own folder (empty if not multi-language)
   */
  public void generate(String outputDir, JsonObject defaults, Set<String> resources, List<String> langs) throws IOException {
    for (NewLocation nl : inEffect) {
      if (nl.artifact != null) {
        if (!check(nl, !resources.contains(nl.artifact), "artifact "+nl.artifact+" is still in this IG, so it can't have a new location")) {
          continue;
        }
        String type = nl.artifact.substring(0, nl.artifact.indexOf("/"));
        String id = nl.artifact.substring(nl.artifact.indexOf("/")+1);
        String newId = nl.rename != null ? nl.rename : id;
        Map<String, String> pages = new LinkedHashMap<>();
        String mainPage = null;
        for (Map.Entry<String, Set<String>> pp : pagePatterns(defaults, type).entrySet()) {
          for (String pattern : pp.getValue()) {
            if (pattern.contains("{{[fmt]}}")) {
              for (String fmt : FORMATS) {
                pages.put(fill(pattern, type, id, fmt), fill(pattern, type, newId, fmt));
              }
            } else {
              pages.put(fill(pattern, type, id, null), fill(pattern, type, newId, null));
              if ("base".equals(pp.getKey())) {
                mainPage = fill(pattern, type, newId, null);
              }
            }
          }
        }
        if (mainPage == null) {
          mainPage = type+"-"+newId+".html";
          pages.put(type+"-"+id+".html", mainPage);
        }
        writePages(outputDir, langs, nl, pages);
        String target = Utilities.pathURL(nl.newSpec, mainPage);
        String canonicalPath = Utilities.path(outputDir, type, id, "index.html");
        if (check(nl, !new File(canonicalPath).exists(), "the IG still generates "+type+"/"+id+"/index.html")) {
          writeRedirect(canonicalPath, target);
        }
        redirects.put(nl.artifact, target);
        message(nl, IssueSeverity.INFORMATION, "redirects "+nl.artifact+" and its pages to "+target);
      } else {
        String page = (nl.rename != null ? nl.rename : nl.page)+".html";
        writePages(outputDir, langs, nl, Collections.singletonMap(nl.page+".html", page));
        redirects.put(nl.page, Utilities.pathURL(nl.newSpec, page));
        message(nl, IssueSeverity.INFORMATION, "redirects "+nl.page+".html to "+Utilities.pathURL(nl.newSpec, page));
      }
    }
  }

  /**
   * @return what is redirected (Type/id of an artifact, or a page name) and where to
   */
  public Map<String, String> getRedirects() {
    return redirects;
  }

  /**
   * The page name patterns that the template defines for a resource type, by property name. A type's own settings
   * replace those for Any, and the variants of a type (e.g. StructureDefinition:extension) add theirs, since it isn't
   * known which variant the moved resource was
   */
  public static Map<String, Set<String>> pagePatterns(JsonObject defaults, String type) {
    Map<String, Set<String>> res = new LinkedHashMap<>();
    if (defaults != null) {
      addPatterns(res, defaults.getJsonObject("Any"), false);
      addPatterns(res, defaults.getJsonObject(type), false);
      for (JsonProperty p : defaults.getProperties()) {
        if (p.getName().startsWith(type+":") && p.getValue().isJsonObject()) {
          addPatterns(res, p.getValue().asJsonObject(), true);
        }
      }
    }
    return res;
  }

  private static void addPatterns(Map<String, Set<String>> res, JsonObject cfg, boolean add) {
    if (cfg != null) {
      for (JsonProperty p : cfg.getProperties()) {
        if (!p.getName().startsWith("template") && !p.getName().startsWith("_") && p.getValue().isJsonString()) {
          String v = p.getValue().asString();
          if (v.endsWith(".html") && !v.replace("{{[type]}}", "").replace("{{[id]}}", "").replace("{{[fmt]}}", "").contains("{{")) {
            if (!add || !res.containsKey(p.getName())) {
              res.put(p.getName(), new LinkedHashSet<>());
            }
            res.get(p.getName()).add(v);
          }
        }
      }
    }
  }

  private static String fill(String pattern, String type, String id, String fmt) {
    String s = pattern.replace("{{[type]}}", type).replace("{{[id]}}", id);
    return fmt == null ? s : s.replace("{{[fmt]}}", fmt);
  }

  private void writePages(String outputDir, List<String> langs, NewLocation nl, Map<String, String> pages) throws IOException {
    for (Map.Entry<String, String> p : pages.entrySet()) {
      String fn = Utilities.path(outputDir, p.getKey());
      if (check(nl, !new File(fn).exists(), "the IG still generates the page "+p.getKey())) {
        writeRedirect(fn, Utilities.pathURL(nl.newSpec, p.getValue()));
      }
      for (String lang : langs) {
        if (new File(Utilities.path(outputDir, lang)).isDirectory()) {
          fn = Utilities.path(outputDir, lang, p.getKey());
          if (check(nl, !new File(fn).exists(), "the IG still generates the page "+lang+"/"+p.getKey())) {
            writeRedirect(fn, Utilities.pathURL(nl.newSpec, lang, p.getValue()));
          }
        }
      }
    }
  }

  private void writeRedirect(String filename, String target) throws IOException {
    String t = Utilities.escapeXml(target);
    FileUtilities.createDirectory(FileUtilities.getDirectoryForFile(filename));
    FileUtilities.stringToFile("<!DOCTYPE html>\r\n"+
        "<html lang=\"en\">\r\n"+
        "<head>\r\n"+
        "<meta charset=\"UTF-8\">\r\n"+
        "<meta http-equiv=\"refresh\" content=\"0; url="+t+"\">\r\n"+
        "<title>Moved</title>\r\n"+
        "<script>window.location.replace(\""+target.replace("\"", "%22")+"\");</script>\r\n"+
        "</head>\r\n"+
        "<body>\r\n"+
        "<p>This content is now found at <a href=\""+t+"\">"+t+"</a>.</p>\r\n"+
        "</body>\r\n"+
        "</html>\r\n", filename);
  }

  private static String part(Extension ext, String name) {
    Extension e = ext.getExtensionByUrl(name);
    return e == null || !e.hasValue() ? null : e.getValue().primitiveValue();
  }

  private boolean check(NewLocation nl, boolean test, String msg) {
    if (!test) {
      message(nl, IssueSeverity.ERROR, msg);
    }
    return test;
  }

  private void message(NewLocation nl, IssueSeverity level, String msg) {
    String what = nl.local() == null ? "A new-location" : "The new-location for "+nl.local();
    errors.add(new ValidationMessage(Source.Publisher, level == IssueSeverity.ERROR ? IssueType.BUSINESSRULE : IssueType.INFORMATIONAL, PATH, what+" "+msg, level));
  }
}
