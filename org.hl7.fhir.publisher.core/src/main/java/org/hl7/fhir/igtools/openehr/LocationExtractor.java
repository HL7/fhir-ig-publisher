package org.hl7.fhir.igtools.openehr;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Utility to extract web source locations from existing FHIR StructureDefinitions
 * and save them for use in BMM conversion
 */
public class LocationExtractor {

  private static final String WEB_SOURCE_EXTENSION_URL = "http://hl7.org/fhir/tools/StructureDefinition/web-source";
  private final Gson gson;

  public LocationExtractor() {
    this.gson = new GsonBuilder().setPrettyPrinting().create();
  }

  public static void main(String[] args) {
    if (args.length != 2) {
      System.err.println("Usage: java LocationExtractor <input-directory> <output-file>");
      System.err.println("  input-directory: Directory containing FHIR StructureDefinition JSON files");
      System.err.println("  output-file: Output file for locations (will determine format by extension: .json, .yaml, .properties)");
      System.exit(1);
    }

    String inputDir = args[0];
    String outputFile = args[1];

    LocationExtractor extractor = new LocationExtractor();
    try {
      Map<String, String> locations = extractor.extractLocations(inputDir);
      extractor.saveLocations(locations, outputFile);
      System.out.println("Extracted " + locations.size() + " locations from " + inputDir);
      System.out.println("Saved to " + outputFile);
    } catch (IOException e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  /**
   * Recursively scans directory for FHIR StructureDefinition JSON files and extracts web source locations
   */
  public Map<String, String> extractLocations(String directoryPath) throws IOException {
    Map<String, String> locations = new HashMap<>();
    Path rootPath = Paths.get(directoryPath);

    if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
      throw new IOException("Directory does not exist: " + directoryPath);
    }

    Files.walk(rootPath)
            .filter(path -> Files.isRegularFile(path))
            .filter(path -> path.toString().toLowerCase().endsWith(".json"))
            .forEach(path -> {
              try {
                extractLocationFromFile(path, locations);
              } catch (Exception e) {
                System.err.println("Warning: Failed to process file " + path + ": " + e.getMessage());
              }
            });

    return locations;
  }

  private void extractLocationFromFile(Path filePath, Map<String, String> locations) throws IOException {
    try (FileReader reader = new FileReader(filePath.toFile())) {
      JsonObject json = gson.fromJson(reader, JsonObject.class);

      // Check if this is a StructureDefinition
      if (!json.has("resourceType") ||
              !"StructureDefinition".equals(json.get("resourceType").getAsString())) {
        return;
      }

      // Extract name/id
      String name = null;
      if (json.has("name")) {
        name = json.get("name").getAsString();
      } else if (json.has("id")) {
        name = json.get("id").getAsString();
      }

      if (name == null) {
        System.err.println("Warning: No name or id found in " + filePath);
        return;
      }

      // Look for web-source extension
      if (json.has("extension")) {
        JsonArray extensions = json.getAsJsonArray("extension");
        for (JsonElement ext : extensions) {
          JsonObject extension = ext.getAsJsonObject();
          if (extension.has("url") &&
                  WEB_SOURCE_EXTENSION_URL.equals(extension.get("url").getAsString())) {

            String webSource = null;
            if (extension.has("valueUrl")) {
              webSource = extension.get("valueUrl").getAsString();
            } else if (extension.has("valueUri")) {
              webSource = extension.get("valueUri").getAsString();
            }

            if (webSource != null) {
              locations.put(name, webSource);
              System.out.println("Found location for " + name + ": " + webSource);
            }
            break;
          }
        }
      }
    }
  }

  /**
   * Saves locations map to file in format determined by file extension
   */
  public void saveLocations(Map<String, String> locations, String outputFile) throws IOException {
    String extension = getFileExtension(outputFile).toLowerCase();

    switch (extension) {
      case "json":
        saveAsJson(locations, outputFile);
        break;
      case "yaml":
      case "yml":
        saveAsYaml(locations, outputFile);
        break;
      case "properties":
        saveAsProperties(locations, outputFile);
        break;
      default:
        // Default to JSON
        saveAsJson(locations, outputFile);
        System.out.println("Unknown extension '" + extension + "', defaulting to JSON format");
    }
  }

  private void saveAsJson(Map<String, String> locations, String outputFile) throws IOException {
    try (FileWriter writer = new FileWriter(outputFile)) {
      gson.toJson(locations, writer);
    }
  }

  private void saveAsYaml(Map<String, String> locations, String outputFile) throws IOException {
    try (FileWriter writer = new FileWriter(outputFile)) {
      writer.write("# openEHR StructureDefinition web source locations\n");
      writer.write("# Generated by LocationExtractor\n\n");

      // Sort keys for consistent output
      TreeMap<String, String> sortedLocations = new TreeMap<>(locations);

      for (Map.Entry<String, String> entry : sortedLocations.entrySet()) {
        // Escape YAML special characters in values
        String value = entry.getValue().replace("\"", "\\\"");
        writer.write(String.format("%s: \"%s\"\n", entry.getKey(), value));
      }
    }
  }

  private void saveAsProperties(Map<String, String> locations, String outputFile) throws IOException {
    Properties props = new Properties();
    props.putAll(locations);

    try (FileWriter writer = new FileWriter(outputFile)) {
      props.store(writer, "openEHR StructureDefinition web source locations");
    }
  }

  private String getFileExtension(String filename) {
    int lastDot = filename.lastIndexOf('.');
    if (lastDot == -1 || lastDot == filename.length() - 1) {
      return "";
    }
    return filename.substring(lastDot + 1);
  }

  /**
   * Loads locations from a file (for use by BmmToFhirConverter)
   */
  public static Map<String, String> loadLocations(String locationsFile) throws IOException {
    String extension = new LocationExtractor().getFileExtension(locationsFile).toLowerCase();

    switch (extension) {
      case "json":
        return loadFromJson(locationsFile);
      case "yaml":
      case "yml":
        return loadFromYaml(locationsFile);
      case "properties":
        return loadFromProperties(locationsFile);
      default:
        throw new IOException("Unsupported file format: " + extension);
    }
  }

  private static Map<String, String> loadFromJson(String file) throws IOException {
    try (FileReader reader = new FileReader(file)) {
      Gson gson = new Gson();
      return gson.fromJson(reader, Map.class);
    }
  }

  private static Map<String, String> loadFromYaml(String file) throws IOException {
    Map<String, String> result = new HashMap<>();
    try (BufferedReader reader = Files.newBufferedReader(Paths.get(file))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }

        int colonIndex = line.indexOf(':');
        if (colonIndex > 0) {
          String key = line.substring(0, colonIndex).trim();
          String value = line.substring(colonIndex + 1).trim();

          // Remove quotes
          if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
          }

          result.put(key, value);
        }
      }
    }
    return result;
  }

  private static Map<String, String> loadFromProperties(String file) throws IOException {
    Properties props = new Properties();
    try (FileReader reader = new FileReader(file)) {
      props.load(reader);
    }

    Map<String, String> result = new HashMap<>();
    for (String key : props.stringPropertyNames()) {
      result.put(key, props.getProperty(key));
    }
    return result;
  }
}