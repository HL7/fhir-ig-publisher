package org.hl7.fhir.igtools.publisher;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;

/**
 * Symbolic links that lead out of the IG's folder (see PublisherUtils.linksOut()). Whatever they link to is read as IG
 * content and can be published, or emptied or written to when the publisher clears or writes a folder.
 *
 * On the ci-build, an IG that has any is refused. Run locally, the user can allow them, for all IGs: with
 * -allow-external-links, which records "allowExternalLinks": true in fhir-settings.json (the publisher's own entry:
 * core's settings class ignores properties it doesn't know). Then the build goes on, with a warning
 */
public class ExternalLinkPermission {

  public static final String PROPERTY = "allowExternalLinks";
  public static final String PARAMETER = "-allow-external-links";

  /**
   * @param links the links out of the IG's folder (relative to it)
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
    if (settingsPath != null && isAllowed(settingsPath)) {
      warn.accept(what+". These are allowed, as recorded in "+settingsPath+", but what they link to will be read (and can be published), and folders they link to can be emptied");
      return;
    }
    if (parameter) {
      if (settingsPath != null) {
        allow(settingsPath);
        warn.accept(what+". These are allowed, as you said ("+PARAMETER+"): recorded in "+settingsPath+", so you won't be asked again. What they link to will be read (and can be published), and folders they link to can be emptied");
      } else {
        warn.accept(what+". These are allowed for this run, as you said ("+PARAMETER+"). It couldn't be recorded, because the FHIR settings file isn't known");
      }
      return;
    }
    throw new FHIRException(what+". What a link leads to is read as part of the IG (and can be published), and a folder it leads to can be emptied by the publisher, "
        + "so an IG from someone else could use one to publish or delete your files. If you are sure these are fine, run the IG Publisher again with '"+PARAMETER+"' to allow links out of the IG's folder, for all IGs. "
        + (settingsPath != null ? "That will be recorded in "+settingsPath+", and you won't be asked again." : ""));
  }

  public static boolean isAllowed(String settingsPath) throws IOException {
    JsonObject settings = read(settingsPath);
    return settings != null && settings.has(PROPERTY) && settings.get(PROPERTY).isJsonBoolean() && settings.asBoolean(PROPERTY);
  }

  public static void allow(String settingsPath) throws IOException {
    JsonObject settings = read(settingsPath);
    if (settings == null) {
      settings = new JsonObject();
    }
    settings.remove(PROPERTY);
    settings.add(PROPERTY, true);
    File f = new File(settingsPath);
    if (f.getParentFile() != null) {
      FileUtilities.createDirectory(f.getParentFile().getAbsolutePath());
    }
    FileUtilities.stringToFile(JsonParser.compose(settings, true), settingsPath);
  }

  private static JsonObject read(String settingsPath) throws IOException {
    File f = new File(settingsPath);
    if (!f.exists()) {
      return null;
    }
    try {
      return JsonParser.parseObject(f);
    } catch (Exception e) {
      // never write over a settings file we can't read
      throw new FHIRException("Unable to read the FHIR settings file "+settingsPath+": "+e.getMessage(), e);
    }
  }
}
