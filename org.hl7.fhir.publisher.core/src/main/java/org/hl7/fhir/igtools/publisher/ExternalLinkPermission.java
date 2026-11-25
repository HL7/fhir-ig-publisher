package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.utilities.json.model.JsonObject;

/**
 * Symbolic links that lead out of the IG's folder (see PublisherUtils.linksOut()). Whatever they link to is read as IG
 * content and can be published, or written to when the publisher writes through them.
 *
 * On the ci-build, an IG that has any is refused. Run locally, the user can allow them for an IG: with
 * -allow-external-links, which records the IG's folder (where it really is) in "allowExternalLinks" in
 * fhir-settings.json (the publisher's own entry: core's settings class ignores properties it doesn't know). Then
 * builds of that IG go on, with a warning. Another IG with links out has to be allowed separately.
 *
 * Earlier versions recorded "allowExternalLinks": true, which allowed links for every IG. That isn't honoured any more:
 * the user is asked again, and allowing the IG replaces it
 */
public class ExternalLinkPermission {

  public static final String PROPERTY = "allowExternalLinks";
  public static final String PARAMETER = "-allow-external-links";

  /**
   * @param links the links out of the IG's folder (relative to it)
   * @param root the IG's folder
   * @param ciBuild whether this is the ci-build
   * @param parameter whether the user ran with -allow-external-links
   * @param settingsPath fhir-settings.json (null if it isn't known)
   * @param warn where the warning goes, when the links are allowed
   */
  public static void check(List<String> links, String root, boolean ciBuild, boolean parameter, String settingsPath, Consumer<String> warn) throws IOException {
    if (links.isEmpty()) {
      return;
    }
    String list = String.join(", ", links.size() > 10 ? links.subList(0, 10) : links)+(links.size() > 10 ? ", ..." : "");
    String what = "The IG's folder ("+root+") has "+(links.size() == 1 ? "a symbolic link" : links.size()+" symbolic links")+" to somewhere outside it: "+list;
    if (ciBuild) {
      throw new FHIRException(what+". That isn't allowed on the ci-build");
    }
    if (settingsPath != null && isAllowed(settingsPath, root)) {
      warn.accept(what+". These are allowed for this IG, as recorded in "+settingsPath+", but what they link to will be read (and can be published), and can be written to");
      return;
    }
    if (parameter) {
      if (settingsPath != null) {
        allow(settingsPath, root);
        warn.accept(what+". These are allowed for this IG, as you said ("+PARAMETER+"): recorded in "+settingsPath+", so you won't be asked again for this IG. What they link to will be read (and can be published), and can be written to");
      } else {
        warn.accept(what+". These are allowed for this run, as you said ("+PARAMETER+"). It couldn't be recorded, because the FHIR settings file isn't known");
      }
      return;
    }
    String legacy = settingsPath != null && hasLegacyAllowAll(settingsPath)
        ? " ("+settingsPath+" says links are allowed for all IGs: that was recorded by an earlier version of the IG Publisher, and isn't honoured any more. Links are now allowed one IG at a time.)"
        : "";
    throw new FHIRException(what+". What a link leads to is read as part of the IG (and can be published), and can be written to by the publisher, "
        + "so an IG from someone else could use one to publish or overwrite your files."+legacy+" If you are sure these are fine, run the IG Publisher again with '"+PARAMETER+"' to allow links out of this IG's folder. "
        + (settingsPath != null ? "That will be recorded in "+settingsPath+", and you won't be asked again for this IG." : ""));
  }

  /** whether links out of the IG's folder have been allowed for this IG */
  public static boolean isAllowed(String settingsPath, String root) throws IOException {
    return IGPermissions.isAllowed(settingsPath, PROPERTY, root);
  }

  /** records that links out of the IG's folder are allowed for this IG (and drops the old allow-all, if it's there) */
  public static void allow(String settingsPath, String root) throws IOException {
    IGPermissions.allow(settingsPath, PROPERTY, root);
  }

  private static boolean hasLegacyAllowAll(String settingsPath) throws IOException {
    JsonObject settings = IGPermissions.read(settingsPath);
    return settings != null && settings.has(PROPERTY) && settings.get(PROPERTY).isJsonBoolean() && settings.asBoolean(PROPERTY);
  }
}
