package org.hl7.fhir.igtools.publisher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;

/**
 * Redirects for content that used to be published in this IG and is now published in another one, as declared by
 * the new-location extensions on the ImplementationGuide.
 *
 * For each new-location in effect for this version of the IG, the build writes a redirect page in place of the
 * main page of a moved artifact, or of a moved page, and records where the content is now found in spec.internals.
 * From there, the website publication redirects the canonical URL of a moved artifact the same way as for the
 * artifacts still in the IG: to the new page, or to the new JSON or XML.
 */
public class NewLocationRedirects {

  public static final String EXT_NEW_LOCATION = "http://hl7.org/fhir/tools/StructureDefinition/new-location";
  public static final String SPEC_INTERNALS_KEY = "new-locations";

  private static final String PATH = "ImplementationGuide.extension('"+EXT_NEW_LOCATION+"')";

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

  /**
   * Where moved content is now found: its page, and for an artifact, its source files ({source}.json, {source}.xml)
   */
  public static class Target {
    private final String page;
    private final String source;

    public Target(String page, String source) {
      this.page = page;
      this.source = source;
    }

    public String getPage() {
      return page;
    }

    public String getSource() {
      return source;
    }
  }

  private final List<ValidationMessage> errors;
  private final List<NewLocation> inEffect = new ArrayList<>();
  private final Map<String, Target> redirects = new TreeMap<>();

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
   * @param defaults the template's default publishing configuration, which names the main page of each resource type
   * @param resources Type/id of the resources in this IG
   */
  public void generate(String outputDir, JsonObject defaults, Set<String> resources) throws IOException {
    for (NewLocation nl : inEffect) {
      if (nl.artifact != null) {
        if (!check(nl, !resources.contains(nl.artifact), "artifact "+nl.artifact+" is still in this IG, so it can't have a new location")) {
          continue;
        }
        String type = nl.artifact.substring(0, nl.artifact.indexOf("/"));
        String id = nl.artifact.substring(nl.artifact.indexOf("/")+1);
        String newId = nl.rename != null ? nl.rename : id;
        String pattern = basePattern(defaults, type);
        Target target = new Target(Utilities.pathURL(nl.newSpec, fill(pattern, type, newId)), Utilities.pathURL(nl.newSpec, type+"-"+newId));
        writePage(outputDir, nl, fill(pattern, type, id), target.page);
        redirects.put(nl.artifact, target);
        message(nl, IssueSeverity.INFORMATION, "redirects "+fill(pattern, type, id)+", and the canonical URL when the IG is published, to "+target.page);
      } else {
        Target target = new Target(Utilities.pathURL(nl.newSpec, (nl.rename != null ? nl.rename : nl.page)+".html"), null);
        writePage(outputDir, nl, nl.page+".html", target.page);
        redirects.put(nl.page, target);
        message(nl, IssueSeverity.INFORMATION, "redirects "+nl.page+".html to "+target.page);
      }
    }
  }

  /**
   * @return what is redirected (Type/id of an artifact, or a page name) and where to
   */
  public Map<String, Target> getRedirects() {
    return redirects;
  }

  /**
   * @return the template's name for the main page of a resource type
   */
  public static String basePattern(JsonObject defaults, String type) {
    for (String section : new String[] {type, "Any"}) {
      JsonObject cfg = defaults == null ? null : defaults.getJsonObject(section);
      if (cfg != null && cfg.hasString("base")) {
        String v = cfg.asString("base");
        if (v.endsWith(".html") && !v.replace("{{[type]}}", "").replace("{{[id]}}", "").contains("{{")) {
          return v;
        }
      }
    }
    return "{{[type]}}-{{[id]}}.html";
  }

  private static String fill(String pattern, String type, String id) {
    return pattern.replace("{{[type]}}", type).replace("{{[id]}}", id);
  }

  private void writePage(String outputDir, NewLocation nl, String name, String target) throws IOException {
    String fn = Utilities.path(outputDir, name);
    if (check(nl, !new File(fn).exists(), "the IG still generates the page "+name)) {
      String t = Utilities.escapeXml(target);
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
          "</html>\r\n", fn);
    }
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
