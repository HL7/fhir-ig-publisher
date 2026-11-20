package org.hl7.fhir.igtools.templates;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The IG templates that are trusted to run code on the ci-build (ant scripts, pre-process transforms), and the GitHub
 * repository each one is built from.
 *
 * The repository matters because the ci-build publishes a #current build of whatever it builds under the package id
 * that the build claims: a template claiming a trusted id must be built from that template's own repository, or anyone
 * could publish a "trusted" template from a repository of their own (see PublisherIGLoader.checkTrustedTemplateSource)
 *
 * If you are proposing to change this list, discuss with the FHIR Product Director first
 */
public class TrustedTemplates {

  // package id -> GitHub repository (org/repo) it is built from. null: the repository hasn't been recorded, so the
  // template can be used as a trusted template, but the ci-build won't build it until it is
  private static final Map<String, String> TEMPLATES = new LinkedHashMap<>();

  static {
    // TEMPLATES.put("fhir.test.template", null); not currently in use
    TEMPLATES.put("fhir.base.template", "HL7/ig-template-base"); // no longer trusted: GG decision, 19-11-2026
    // TEMPLATES.put("fhir.base.template.a", null); // this was a transient template that is no longer supported
    TEMPLATES.put("fhir2.base.template", "HL7/ig-template-base2"); // Lloyd
    TEMPLATES.put("hl7.base.template", "HL7/ig-template-hl7"); // Lloyd
    TEMPLATES.put("hl7.fhir.template", "HL7/ig-template-fhir"); // Lloyd
    TEMPLATES.put("hl7.au.base.template", "hl7au/hl7.au.base.template"); // Brett
    TEMPLATES.put("hl7.au.fhir.template", "hl7au/hl7.au.fhir.template"); // Brett
    TEMPLATES.put("hl7.au.sparked.template", "hl7au/hl7.au.sparked.template"); // Brett
    TEMPLATES.put("hl7.utg.template", "HL7/ig-template-utg"); // Marc Duteau
    TEMPLATES.put("hl7.be.fhir.template", "hl7-be/hl7.be.fhir.template");
    TEMPLATES.put("hl7.cda.template", "HL7/ig-template-cda"); // Lloyd
    TEMPLATES.put("hl7.davinci.template", "HL7/ig-template-davinci"); // Lloyd
    TEMPLATES.put("hl7.sdc.template", "HL7/ig-template-sdc"); // Lloyd
    TEMPLATES.put("openhie.fhir.template", "openhie/openhie-ig-template");
    TEMPLATES.put("who.fhir.template", "WorldHealthOrganization/smart-ig-template-who"); // Carl
    TEMPLATES.put("who.template.root", "WorldHealthOrganization/smart-ig-template-who"); // Carl
    TEMPLATES.put("hl7.extensions.template", "HL7/ig-template-extensions"); // Lloyd
    TEMPLATES.put("ihe.fhir.template", "IHE/ihe-ig-template");
    TEMPLATES.put("cqf.fhir.template", "cqframework/cqf-ig-template"); // Bryn Rhodes
    TEMPLATES.put("ans.fr.template", "ansforge/IG-template");
    TEMPLATES.put("ans.cda.fr.template", null);
//    TEMPLATES.put("hl7.v2plus.template", null);
    TEMPLATES.put("openehr.fhir.template", "FHIR/openehr.fhir.template");
    TEMPLATES.put("ch.fhir.ig.template", null);
  }

  public static boolean isTrusted(String packageId) {
    return packageId != null && TEMPLATES.containsKey(packageId);
  }

  /**
   * @return the GitHub repository (org/repo) the trusted template is built from, or null if it isn't trusted or the
   * repository hasn't been recorded
   */
  public static String getRepository(String packageId) {
    return packageId == null ? null : TEMPLATES.get(packageId);
  }

  public static Set<String> getPackageIds() {
    return Collections.unmodifiableSet(TEMPLATES.keySet());
  }

  /**
   * Whether the source (a URL naming a repository: see githubRepository()) is the repository the trusted template is
   * built from. False if the template isn't trusted, its repository hasn't been recorded, or the source names no
   * GitHub repository. GitHub names aren't case sensitive, so neither is this
   */
  public static boolean isBuiltFrom(String packageId, String source) {
    String expected = getRepository(packageId);
    String actual = githubRepository(source);
    return expected != null && actual != null && expected.equalsIgnoreCase(actual);
  }

  /**
   * The GitHub repository (org/repo) a URL names: a github.com URL (https, http or ssh form, with or without .git, and
   * whatever path follows the repository, such as /tree/branch), or a ci-build URL (https://build.fhir.org/ig/org/repo/...)
   *
   * @return null if the url names neither
   */
  public static String githubRepository(String url) {
    if (url == null) {
      return null;
    }
    String s = url.trim();
    String rest;
    if (s.startsWith("git@github.com:")) {
      rest = s.substring("git@github.com:".length());
    } else {
      s = s.replaceFirst("^(?i)(https?|ssh)://(git@)?", "");
      if (s.toLowerCase().startsWith("www.")) {
        s = s.substring(4);
      }
      if (s.toLowerCase().startsWith("github.com/")) {
        rest = s.substring("github.com/".length());
      } else if (s.toLowerCase().startsWith("build.fhir.org/ig/")) {
        rest = s.substring("build.fhir.org/ig/".length());
      } else {
        return null;
      }
    }
    rest = rest.split("[?#]")[0];
    String[] parts = rest.split("/");
    if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
      return null;
    }
    String repo = parts[1].endsWith(".git") ? parts[1].substring(0, parts[1].length() - 4) : parts[1];
    return repo.isEmpty() ? null : parts[0] + "/" + repo;
  }
}
