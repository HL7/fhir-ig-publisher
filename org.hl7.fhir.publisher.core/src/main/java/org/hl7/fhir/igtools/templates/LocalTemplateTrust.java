package org.hl7.fhir.igtools.templates;

import java.io.File;
import java.io.IOException;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;

/**
 * The templates the user has said they trust to run locally, though they aren't trusted templates: the array
 * "trustedTemplates" in fhir-settings.json, which holds template ids (see TemplateManager.trustId()) or "all".
 *
 * This is the publisher's own entry in fhir-settings.json: the settings class in core ignores properties it doesn't
 * know, so this needs no change there. Everything else in the file is left as it is
 */
public class LocalTemplateTrust {

  public static final String PROPERTY = "trustedTemplates";
  public static final String ALL = "all";

  /**
   * @return true if the settings file says the template (or all templates) can be trusted
   */
  public static boolean isTrusted(String settingsPath, String id) throws IOException {
    JsonObject settings = read(settingsPath);
    if (settings == null || !settings.has(PROPERTY) || !settings.get(PROPERTY).isJsonArray()) {
      return false;
    }
    for (JsonElement e : settings.getJsonArray(PROPERTY)) {
      if (e.isJsonPrimitive() && (ALL.equals(e.asString()) || e.asString().equals(id))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Adds the template id (or "all") to the settings file, creating the file if there isn't one
   */
  public static void addTrust(String settingsPath, String id) throws IOException {
    JsonObject settings = read(settingsPath);
    if (settings == null) {
      settings = new JsonObject();
    }
    if (!settings.has(PROPERTY) || !settings.get(PROPERTY).isJsonArray()) {
      settings.remove(PROPERTY);
      settings.add(PROPERTY, new JsonArray());
    }
    JsonArray list = settings.getJsonArray(PROPERTY);
    for (JsonElement e : list) {
      if (e.isJsonPrimitive() && id.equals(e.asString())) {
        return;
      }
    }
    list.add(id);
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
