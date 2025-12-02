package org.hl7.fhir.igtools.openehr;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.http.*;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;

/**
 * Client for downloading resources from openEHR Clinical Knowledge Manager (CKM).
 *
 * This client uses the CKM REST API to download all archetypes and templates
 * from a specified project.
 */
public class CKMClient {

  private static final int PAGE_SIZE = 100;
  private static final String SERVER_TYPE = "ckm";

  private String server;
  private String project;
  private String username;
  private String password;
  private List<String> errors;

  /**
   * Downloads all resources from a CKM project specified in an INI file.
   *
   * The INI file should contain a [ckm] section with:
   * - project: the citeable identifier of the project (e.g., 1013.30.44)
   * - server: the CKM server URL (e.g., https://ckm.openehr.org/ckm)
   * - username: (optional) username for authentication
   * - password: (optional) password for authentication
   *
   * @param ini the INI file containing CKM configuration
   * @param dest the destination folder for downloaded files
   * @return true if all resources were downloaded successfully, false if any errors occurred
   * @throws IOException if there are configuration or I/O errors
   */
  public boolean process(IniFile ini, File dest) throws IOException {
    errors = new ArrayList<>();

    // Validate and read configuration
    if (!validateConfiguration(ini, dest)) {
      return false;
    }

    readConfiguration(ini);

    // Create destination directory if it doesn't exist
    if (!dest.exists()) {
      dest.mkdirs();
    }

    System.out.println("Downloading resources from CKM project: " + project);
    System.out.println("Server: " + server);
    System.out.println("Destination: " + dest.getAbsolutePath());

    // Download archetypes
    downloadArchetypes(dest);

    // Download templates
    downloadTemplates(dest);

    // Report results
    if (errors.isEmpty()) {
      System.out.println("\nAll resources downloaded successfully.");
      return true;
    } else {
      System.err.println("\nErrors occurred during download:");
      for (String error : errors) {
        System.err.println("  - " + error);
      }
      return false;
    }
  }

  private boolean validateConfiguration(IniFile ini, File dest) {
    if (ini == null) {
      System.err.println("Error: IniFile cannot be null");
      return false;
    }

    if (!ini.hasSection("ckm")) {
      System.err.println("Error: INI file must contain a [ckm] section");
      return false;
    }

    if (!ini.hasProperty("ckm", "project")) {
      System.err.println("Error: [ckm] section must contain 'project' property");
      return false;
    }

    if (!ini.hasProperty("ckm", "server")) {
      System.err.println("Error: [ckm] section must contain 'server' property");
      return false;
    }

    if (dest == null) {
      System.err.println("Error: Destination folder cannot be null");
      return false;
    }

    if (dest.exists() && !dest.isDirectory()) {
      System.err.println("Error: Destination path exists but is not a directory");
      return false;
    }

    return true;
  }

  private void readConfiguration(IniFile ini) {
    project = ini.getStringProperty("ckm", "project");
    server = ini.getStringProperty("ckm", "server");
    username = ini.getStringProperty("ckm", "username");
    password = ini.getStringProperty("ckm", "password");

    // Remove trailing slash from server if present
    if (server.endsWith("/")) {
      server = server.substring(0, server.length() - 1);
    }
  }

  private void downloadArchetypes(File dest) {
    System.out.println("\nDownloading archetypes...");

    int offset = 0;
    int totalCount = 0;
    int downloadedCount = 0;
    boolean hasMore = true;

    while (hasMore) {
      try {
        String url = server + "/rest/v1/archetypes?cid-project=" + project +
                "&owned-only=true&size=" + PAGE_SIZE + "&offset=" + offset;

        HTTPResult result = makeRequest(url, "application/json");

        if (result.getCode() != 200) {
          errors.add("Failed to list archetypes (offset " + offset + "): HTTP " + result.getCode());
          break;
        }

        // Parse total count from header
        if (offset == 0 && HTTPHeaderUtil.hasHeader(result.getHeaders(), "x-total-count")) {
          try {
            totalCount = Integer.parseInt(HTTPHeaderUtil.getSingleHeader(result.getHeaders(), "x-total-count"));
            System.out.println("Total archetypes: " + totalCount);
          } catch (NumberFormatException e) {
            // Ignore if can't parse
          }
        }

        JsonArray resources = (JsonArray) JsonParser.parse(result.getContent());

        if (resources.size() == 0) {
          hasMore = false;
          break;
        }

        for (JsonObject resource : resources.asJsonObjects()) {
          String cid = resource.asString("cid");
          String mainId = resource.asString("resourceMainId");

          if (downloadArchetype(dest, cid, mainId)) {
            downloadedCount++;
          }
        }

        offset += resources.size();
        hasMore = resources.size() == PAGE_SIZE;

      } catch (Exception e) {
        errors.add("Error listing archetypes at offset " + offset + ": " + e.getMessage());
        break;
      }
    }

    System.out.println("Downloaded " + downloadedCount + " archetypes");
  }

  private boolean downloadArchetype(File dest, String cid, String mainId) {
    try {
      String url = server + "/rest/v1/archetypes/" + cid + "/adl";
      HTTPResult result = makeRequest(url, "text/plain");

      if (result.getCode() != 200) {
        errors.add("Failed to download archetype " + mainId + " (CID: " + cid + "): HTTP " + result.getCode());
        return false;
      }

      String filename = sanitizeFilename(mainId) + ".adl";
      File file = new File(dest, filename);

      try (FileOutputStream fos = new FileOutputStream(file)) {
        fos.write(result.getContent());
      }

      // post processing
      String src = FileUtilities.fileToString(file);
//      src = src.replace("adl_version=1.4;", "adl_version=1.4; rm_release=1.1.0;");
      FileUtilities.stringToFile(src, file);

      System.out.println("  Downloaded: " + filename);
      return true;

    } catch (Exception e) {
      errors.add("Error downloading archetype " + mainId + " (CID: " + cid + "): " + e.getMessage());
      return false;
    }
  }

  private void downloadTemplates(File dest) {
    System.out.println("\nDownloading templates...");

    int offset = 0;
    int totalCount = 0;
    int downloadedCount = 0;
    boolean hasMore = true;

    while (hasMore) {
      try {
        String url = server + "/rest/v1/templates?cid-project=" + project +
                "&owned-only=true&size=" + PAGE_SIZE + "&offset=" + offset;

        HTTPResult result = makeRequest(url, "application/json");

        if (result.getCode() != 200) {
          errors.add("Failed to list templates (offset " + offset + "): HTTP " + result.getCode());
          break;
        }

        // Parse total count from header
        if (offset == 0 && HTTPHeaderUtil.hasHeader(result.getHeaders(), "x-total-count")) {
          try {
            totalCount = Integer.parseInt(HTTPHeaderUtil.getSingleHeader(result.getHeaders(), "x-total-count"));
            System.out.println("Total templates: " + totalCount);
          } catch (NumberFormatException e) {
            // Ignore if can't parse
          }
        }

        JsonArray resources = (JsonArray) JsonParser.parse(result.getContent());

        if (resources.size() == 0) {
          hasMore = false;
          break;
        }

        for (JsonObject resource : resources.asJsonObjects()) {
          String cid = resource.asString("cid");
          String mainId = resource.asString("resourceMainId");

          if (downloadTemplate(dest, cid, mainId)) {
            downloadedCount++;
          }
        }

        offset += resources.size();
        hasMore = resources.size() == PAGE_SIZE;

      } catch (Exception e) {
        errors.add("Error listing templates at offset " + offset + ": " + e.getMessage());
        break;
      }
    }

    System.out.println("Downloaded " + downloadedCount + " templates");
  }

  private boolean downloadTemplate(File dest, String cid, String mainId) {
    try {
      String url = server + "/rest/v1/templates/" + cid + "/oet";
      HTTPResult result = makeRequest(url, "application/xml");

      if (result.getCode() != 200) {
        errors.add("Failed to download template " + mainId + " (CID: " + cid + "): HTTP " + result.getCode());
        return false;
      }

      String filename = sanitizeFilename(mainId) + ".oet";
      File file = new File(dest, filename);

      try (FileOutputStream fos = new FileOutputStream(file)) {
        fos.write(result.getContent());
      }

      System.out.println("  Downloaded: " + filename);
      return true;

    } catch (Exception e) {
      errors.add("Error downloading template " + mainId + " (CID: " + cid + "): " + e.getMessage());
      return false;
    }
  }

  private HTTPResult makeRequest(String url, String accept) throws IOException {
    ManagedWebAccessor accessor = ManagedWebAccess.accessor(Arrays.asList(SERVER_TYPE));

    // Set up authentication if credentials are provided
    if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
//      accessor.withAuthenticationMode(HTTPAuthenticationMode.BASIC);
//      accessor.withUsername(username);
//      accessor.withPassword(password);
    }

    return accessor.get(url, accept);
  }

  private String sanitizeFilename(String filename) {
    // Replace characters that are invalid in filenames
    return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
  }
}