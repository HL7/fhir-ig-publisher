package org.hl7.fhir.igtools.publisher;

import java.io.File;
import java.io.IOException;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;

/**
 * Things the user has allowed for one IG, recorded in fhir-settings.json as a list of the IGs' folders (where they
 * really are) under a property of the publisher's own (core's settings class ignores properties it doesn't know).
 * Used by ExternalLinkPermission and CommittedScriptPermission
 */
class IGPermissions {

  /** whether the IG's folder is in the list under property */
  static boolean isAllowed(String settingsPath, String property, String root) throws IOException {
    JsonObject settings = read(settingsPath);
    if (settings == null || !settings.has(property) || !settings.get(property).isJsonArray()) {
      return false;
    }
    return settings.getJsonArray(property).asStrings().contains(realPath(root));
  }

  /** adds the IG's folder to the list under property (replacing whatever is there if it isn't a list) */
  static void allow(String settingsPath, String property, String root) throws IOException {
    JsonObject settings = read(settingsPath);
    if (settings == null) {
      settings = new JsonObject();
    }
    String ig = realPath(root);
    JsonArray igs = new JsonArray();
    if (settings.has(property) && settings.get(property).isJsonArray()) {
      igs = settings.getJsonArray(property);
    } else {
      settings.remove(property);
      settings.add(property, igs);
    }
    if (!igs.asStrings().contains(ig)) {
      igs.add(ig);
    }
    File f = new File(settingsPath);
    if (f.getParentFile() != null) {
      FileUtilities.createDirectory(f.getParentFile().getAbsolutePath());
    }
    FileUtilities.stringToFile(JsonParser.compose(settings, true), settingsPath);
  }

  /** where the IG's folder really is, so a link to it, or another way of writing it, is the same IG */
  static String realPath(String root) {
    File f = new File(root).getAbsoluteFile();
    try {
      return f.toPath().normalize().toRealPath().toString();
    } catch (IOException e) {
      return f.toPath().normalize().toString();
    }
  }

  /** the settings, or null if there's no settings file */
  static JsonObject read(String settingsPath) throws IOException {
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
