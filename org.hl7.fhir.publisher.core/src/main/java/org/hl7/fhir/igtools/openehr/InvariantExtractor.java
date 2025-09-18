package org.hl7.fhir.igtools.openehr;

import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;

import java.io.*;
import java.util.*;

/**
 * Utility to extract invariants from BMM files and create a YAML file for FHIRPath mapping
 */
public class InvariantExtractor {

  public static void main(String[] args) {
    if (args.length != 2) {
      System.err.println("Usage: java InvariantExtractor <bmm-file> <output-yaml>");
      System.err.println("  bmm-file: BMM source file");
      System.err.println("  output-yaml: Output YAML file for invariant mappings");
      System.exit(1);
    }

    String bmmFile = args[0];
    String outputFile = args[1];

    InvariantExtractor extractor = new InvariantExtractor();
    try {
      extractor.extractInvariants(bmmFile, outputFile);
      System.out.println("Extracted invariants to " + outputFile);
    } catch (IOException e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  public void extractInvariants(String bmmFilePath, String outputPath) throws IOException {
    // Initialize FHIRPath validation
    FHIRPathEngine fhirPathEngine = initializeFHIRPathEngine();

    // Parse BMM file
    JsonObject bmmRoot = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(new File(bmmFilePath));
    JsonObject classDefinitions = bmmRoot.getJsonObject("class_definitions");

    // Build class registry for reference lookup
    Map<String, ClassInfo> classRegistry = buildClassRegistry(classDefinitions);

    Map<String, Map<String, InvariantInfo>> invariants = new LinkedHashMap<>();

    // Extract invariants from each class
    for (JsonProperty entry : classDefinitions.getProperties()) {
      String className = entry.getName();
      JsonObject classData = entry.getValue().asJsonObject();

      if (classData.has("invariants")) {
        JsonObject classInvariants = classData.getJsonObject("invariants");
        Map<String, InvariantInfo> classInvariantMap = new LinkedHashMap<>();

        for (JsonProperty invEntry : classInvariants.getProperties()) {
          String invName = invEntry.getName();
          String expression = invEntry.getValue().asString();

          InvariantInfo info = new InvariantInfo();
          info.originalExpression = expression;
          info.humanDescription = generateHumanDescription(invName, expression);
          info.fhirPathExpression = attemptFhirPathTranslation(expression, className, classRegistry);
          if (info.fhirPathExpression != null) {
            info.fhirPathExpression = info.fhirPathExpression.replace("()()", "()");
          }

          // Validate FHIRPath expression if we generated one
          if (info.fhirPathExpression != null && fhirPathEngine != null) {
            info.fhirPathError = validateFHIRPath(info.fhirPathExpression, fhirPathEngine);
          }

          classInvariantMap.put(invName, info);
        }

        if (!classInvariantMap.isEmpty()) {
          invariants.put(className, classInvariantMap);
        }
      }
    }

    // Write to YAML
    writeInvariantsYaml(invariants, outputPath);
  }

  private FHIRPathEngine initializeFHIRPathEngine() {
    try {
      FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager.Builder().build();
      NpmPackage npm = pcm.loadPackage("hl7.fhir.r5.core");
      IWorkerContext context = new SimpleWorkerContext.SimpleWorkerContextBuilder().withAllowLoadingDuplicates(true).fromPackage(npm);
      return new FHIRPathEngine(context);
    } catch (Exception e) {
      System.err.println("Warning: Could not initialize FHIRPath engine: " + e.getMessage());
      System.err.println("FHIRPath validation will be skipped");
      return null;
    }
  }

  private String validateFHIRPath(String fhirPathExpression, FHIRPathEngine engine) {
    try {
      engine.parse(fhirPathExpression);
      return null; // No error
    } catch (Exception e) {
      return e.getMessage();
    }
  }

  private Map<String, ClassInfo> buildClassRegistry(JsonObject classDefinitions) {
    Map<String, ClassInfo> registry = new HashMap<>();

    for (JsonProperty entry : classDefinitions.getProperties()) {
      String className = entry.getName();
      JsonObject classData = entry.getValue().asJsonObject();

      ClassInfo info = new ClassInfo();

      // Collect properties
      if (classData.has("properties")) {
        JsonObject properties = classData.getJsonObject("properties");
        for (JsonProperty prop : properties.getProperties()) {
          info.properties.add(prop.getName());
        }
      }

      // Collect functions
      if (classData.has("functions")) {
        JsonObject functions = classData.getJsonObject("functions");
        for (JsonProperty func : functions.getProperties()) {
          info.functions.add(func.getName());
        }
      }

      // Collect ancestors
      if (classData.has("ancestors")) {
        JsonElement ancestors = classData.get("ancestors");
        if (ancestors.isJsonArray()) {
          for (JsonElement ancestor : ancestors.asJsonArray()) {
            info.ancestors.add(ancestor.asString());
          }
        }
      }

      registry.put(className, info);
    }

    return registry;
  }

  private String generateHumanDescription(String invName, String expression) {
    // Try to generate a human-readable description from the invariant name and expression
    String desc = invName.replace("_", " ");

    // Add common patterns
    if (expression.contains("/= Void") || expression.contains("!= null")) {
      desc += " must be present";
    } else if (expression.contains("is_empty")) {
      desc += " must not be empty";
    } else if (expression.contains(">=") || expression.contains("<=")) {
      desc += " must satisfy range constraints";
    } else if (expression.contains("xor")) {
      desc += " must satisfy exclusive conditions";
    } else if (expression.contains("implies")) {
      desc += " must satisfy conditional constraints";
    }

    return desc;
  }

  private String attemptFhirPathTranslation(String bmmExpression, String currentClass, Map<String, ClassInfo> classRegistry) {
    String fhirPath = bmmExpression;

    // Simple translations first
    fhirPath = fhirPath.replace("/= Void", ".exists()");
    fhirPath = fhirPath.replace("= Void", ".empty()");
    fhirPath = fhirPath.replace(".is_empty", ".empty()");
    fhirPath = fhirPath.replace("not ", "not ");
    fhirPath = fhirPath.replace(" and ", " and ");
    fhirPath = fhirPath.replace(" or ", " or ");
    fhirPath = fhirPath.replace(" implies ", " implies ");
    fhirPath = fhirPath.replace(" xor ", " xor ");

    // Intelligent token analysis
    fhirPath = analyzeAndTransformTokens(fhirPath, currentClass, classRegistry);

    // Fix negation patterns - this must come after token analysis
    fhirPath = fixNegationPatterns(fhirPath);

    // If we couldn't translate it meaningfully, return null to indicate manual work needed
    if (fhirPath.equals(bmmExpression) &&
            (bmmExpression.contains("for_all") ||
                    bmmExpression.contains("repository") ||
                    bmmExpression.contains("terminology") ||
                    bmmExpression.contains("code_set"))) {
      return null; // Too complex for automatic translation
    }

    return fhirPath.equals(bmmExpression) ? null : fhirPath;
  }

  private String fixNegationPatterns(String fhirPath) {
    // Pattern 1: "not x.method()" -> "x.method().not()"
    // Pattern 2: "not x" (simple identifier) -> "x.not()"
    // We need to be careful about compound expressions

    // Use regex to find and replace negation patterns
    // Pattern: "not " followed by an identifier and optional method call
    String result = fhirPath;

    // Handle "not identifier.method()" -> "identifier.method().not()"
    result = result.replaceAll("\\bnot\\s+([a-zA-Z_][a-zA-Z0-9_]*\\.[a-zA-Z_][a-zA-Z0-9_]*\\(\\))", "$1.not()");

    // Handle "not identifier()" -> "identifier().not()"
    result = result.replaceAll("\\bnot\\s+([a-zA-Z_][a-zA-Z0-9_]*\\(\\))", "$1.not()");

    // Handle "not identifier" -> "identifier.not()" (but be careful about keywords)
    // Only do this if the identifier is not followed by another identifier (to avoid breaking "not x and y")
    result = result.replaceAll("\\bnot\\s+([a-zA-Z_][a-zA-Z0-9_]*)(?!\\s+[a-zA-Z_])", "$1.not()");

    return result;
  }

  private String analyzeAndTransformTokens(String expression, String currentClass, Map<String, ClassInfo> classRegistry) {
    // Use regex to split while preserving delimiters
    String[] parts = expression.split("(\\b)", -1);
    StringBuilder result = new StringBuilder();

    for (String part : parts) {
      if (isIdentifier(part.trim())) {
        String token = part.trim();

        // Check if this token is a property or function in the current class or its ancestors
        if (isFunction(token, currentClass, classRegistry)) {
          result.append(token).append("()");
        } else if (isProperty(token, currentClass, classRegistry)) {
          result.append(token);
        } else {
          // Handle special method names that should have parentheses
          if (isCommonMethod(token)) {
            result.append(token).append("()");
          } else {
            result.append(token);
          }
        }
      } else {
        result.append(part);
      }
    }

    return result.toString();
  }

  private boolean isIdentifier(String token) {
    // Check if token looks like an identifier (letters, digits, underscores)
    return token.matches("[a-zA-Z_][a-zA-Z0-9_]*");
  }

  private boolean isFunction(String token, String className, Map<String, ClassInfo> classRegistry) {
    return isMemberInClassHierarchy(token, className, classRegistry, true);
  }

  private boolean isProperty(String token, String className, Map<String, ClassInfo> classRegistry) {
    return isMemberInClassHierarchy(token, className, classRegistry, false);
  }

  private boolean isMemberInClassHierarchy(String memberName, String className, Map<String, ClassInfo> classRegistry, boolean isFunction) {
    Set<String> visited = new HashSet<>();
    return checkMemberInClass(memberName, className, classRegistry, isFunction, visited);
  }

  private boolean checkMemberInClass(String memberName, String className, Map<String, ClassInfo> classRegistry,
                                     boolean isFunction, Set<String> visited) {
    if (visited.contains(className)) {
      return false; // Prevent infinite recursion
    }
    visited.add(className);

    ClassInfo classInfo = classRegistry.get(className);
    if (classInfo == null) {
      return false;
    }

    // Check in current class
    Set<String> members = isFunction ? classInfo.functions : classInfo.properties;
    if (members.contains(memberName)) {
      return true;
    }

    // Check in ancestors
    for (String ancestor : classInfo.ancestors) {
      if (checkMemberInClass(memberName, ancestor, classRegistry, isFunction, visited)) {
        return true;
      }
    }

    return false;
  }

  private boolean isCommonMethod(String token) {
    // Common method names that should have parentheses
    Set<String> commonMethods = Set.of(
            "count", "size", "length", "isEmpty", "empty", "exists", "first", "last",
            "toString", "asString", "value", "type", "name", "id", "uid",
            "is_valid", "is_empty", "is_null", "has_value"
    );
    return commonMethods.contains(token);
  }

  private void writeInvariantsYaml(Map<String, Map<String, InvariantInfo>> invariants, String outputPath) throws IOException {
    try (FileWriter writer = new FileWriter(outputPath)) {
      writer.write("# openEHR BMM Invariants -> FHIR Constraints Mapping\n");
      writer.write("# Generated automatically - manual refinement needed\n");
      writer.write("# Format:\n");
      writer.write("#   className:\n");
      writer.write("#     invariantName:\n");
      writer.write("#       original: \"original BMM expression\"\n");
      writer.write("#       human: \"human readable description\"\n");
      writer.write("#       fhirpath: \"FHIRPath expression\" # null if needs manual work\n");
      writer.write("#       error: \"FHIRPath validation error\" # present if fhirpath is invalid\n\n");

      for (Map.Entry<String, Map<String, InvariantInfo>> classEntry : invariants.entrySet()) {
        String className = classEntry.getKey();
        writer.write(className + ":\n");

        for (Map.Entry<String, InvariantInfo> invEntry : classEntry.getValue().entrySet()) {
          String invName = invEntry.getKey();
          InvariantInfo info = invEntry.getValue();

          writer.write("  " + invName + ":\n");
          writer.write("    original: \"" + escapeYaml(info.originalExpression) + "\"\n");
          writer.write("    human: \"" + escapeYaml(info.humanDescription) + "\"\n");

          if (info.fhirPathExpression != null) {
            writer.write("    fhirpath: \"" + escapeYaml(info.fhirPathExpression) + "\"\n");
            if (info.fhirPathError != null) {
              writer.write("    error: \"" + escapeYaml(info.fhirPathError) + "\"\n");
            }
          } else {
            writer.write("    fhirpath: null # TODO: manual translation needed\n");
          }
          writer.write("\n");
        }
      }
    }
  }

  private String escapeYaml(String value) {
    if (value == null) return "";
    return value.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }

  private static class InvariantInfo {
    String originalExpression;
    String humanDescription;
    String fhirPathExpression;
    String fhirPathError;
  }

  private static class ClassInfo {
    Set<String> properties = new HashSet<>();
    Set<String> functions = new HashSet<>();
    Set<String> ancestors = new HashSet<>();
  }
}