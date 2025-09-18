package org.hl7.fhir.igtools.openehr;

import org.hl7.fhir.r5.formats.IParser;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Utility class for converting BMM (Basic Meta-Model) class definitions to FHIR R5 StructureDefinitions
 */
public class BmmToFhirConverter {

  public static void main(String[] args) {
    if (args.length < 3 || args.length > 4) {
      System.err.println("Usage: java BmmToFhirConverter <input-file> <locations-file> <output-directory> [invariants-file]");
      System.err.println("  input-file: BMM source file");
      System.err.println("  locations-file: File containing web-source locations");
      System.err.println("  output-directory: Output folder for generated structure definitions");
      System.err.println("  invariants-file: Optional YAML file with invariant mappings");
      System.exit(1);
    }

    String inputFile = args[0];
    String locationsFile = args[1];
    String outputDir = args[2];
    String invariantsFile = args.length > 3 ? args[3] : null;

    BmmToFhirConverter converter = new BmmToFhirConverter();
    try {
      converter.convertBmmToFhir(inputFile, outputDir, new Date(), locationsFile, invariantsFile);
      System.out.println("Done - generated in " + outputDir);
      if (invariantsFile != null) {
        System.out.println("Used invariant mappings from " + invariantsFile);
      }
    } catch (IOException e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static final String OPENEHR_BASE_URL = "http://openehr.org/fhir/StructureDefinition/";
  private static final String FHIR_BASE_URL = "http://hl7.org/fhir/StructureDefinition/";
  private static final String TYPE_PARAMETER_EXTENSION_URL = "http://hl7.org/fhir/tools/StructureDefinition/type-parameter";
  private static final String WEB_SOURCE_EXTENSION_URL = "http://hl7.org/fhir/tools/StructureDefinition/web-source";

  public BmmToFhirConverter() {
  }

  /**
   * Converts BMM file to FHIR StructureDefinitions
   *
   * @param bmmFilePath Path to the BMM JSON file
   * @param outputFolder Folder to write StructureDefinitions to (will be cleared first)
   * @param conversionDate Date to use for the StructureDefinitions
   * @param locationsFile Name of file with web source data
   * @throws IOException if file operations fail
   */
  public void convertBmmToFhir(String bmmFilePath, String outputFolder, Date conversionDate, String locationsFile) throws IOException {
    convertBmmToFhir(bmmFilePath, outputFolder, conversionDate, locationsFile, null);
  }

  /**
   * Converts BMM file to FHIR StructureDefinitions
   *
   * @param bmmFilePath Path to the BMM JSON file
   * @param outputFolder Folder to write StructureDefinitions to (will be cleared first)
   * @param conversionDate Date to use for the StructureDefinitions
   * @param locationsFile Name of file with web source data
   * @param invariantsFile Optional YAML file with invariant mappings
   * @throws IOException if file operations fail
   */
  public void convertBmmToFhir(String bmmFilePath, String outputFolder, Date conversionDate, String locationsFile, String invariantsFile) throws IOException {
    // Clear output directory
    FileUtilities.clearDirectory(outputFolder);

    Map<String, String> locations = LocationExtractor.loadLocations(locationsFile);
    Map<String, Map<String, InvariantMapping>> invariants = invariantsFile != null ?
            loadInvariantMappings(invariantsFile) : new HashMap<>();

    // Parse BMM file
    JsonObject bmmRoot = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(new File(bmmFilePath));

    // Extract metadata
    String rmRelease = bmmRoot.asString("rm_release");
    JsonObject classDefinitions = bmmRoot.getJsonObject("class_definitions");

    // Convert each class definition
    for (JsonProperty entry : classDefinitions.getProperties()) {
      String className = entry.getName();
      JsonObject classData = entry.getValue().asJsonObject();

      StructureDefinition sd = createStructureDefinition(className, classData, rmRelease, conversionDate, locations, invariants);

      // Write to file - replace underscores with hyphens in filename
      String fileName = className.replace("_", "-") + ".json";
      Path outputPath = Paths.get(outputFolder, fileName);

      try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
        new JsonParser().setOutputStyle(IParser.OutputStyle.PRETTY).compose(fos, sd);
      }
    }
  }

  private StructureDefinition createStructureDefinition(String className, JsonObject classData,
                                                        String rmRelease, Date conversionDate, Map<String, String> locations,
                                                        Map<String, Map<String, InvariantMapping>> invariants) {
    StructureDefinition sd = new StructureDefinition();

    // Basic fields - replace underscores with hyphens in ID and URLs
    String fhirId = className.replace("_", "-");
    sd.setId(fhirId);
    sd.setUrl(OPENEHR_BASE_URL + fhirId);
    sd.setVersion(rmRelease);
    sd.setName(className);
    sd.setTitle(className);
    sd.setStatus(Enumerations.PublicationStatus.ACTIVE);
    sd.setExperimental(false);
    sd.setDate(conversionDate);
    sd.setPublisher("openEHR");
    sd.setFhirVersion(Enumerations.FHIRVersion._5_0_0);
    sd.setKind(StructureDefinition.StructureDefinitionKind.LOGICAL);
    sd.setType(OPENEHR_BASE_URL + className);
    sd.setDerivation(StructureDefinition.TypeDerivationRule.SPECIALIZATION);

    // Contact info
    ContactDetail contact = new ContactDetail();
    contact.setName("openEHR");
    ContactPoint telecom = new ContactPoint();
    telecom.setSystem(ContactPoint.ContactPointSystem.URL);
    telecom.setValue("https://specifications.openehr.org");
    contact.addTelecom(telecom);
    sd.addContact(contact);

    // Description
    if (classData.has("documentation")) {
      sd.setDescription(classData.asString("documentation"));
    }

    // Abstract flag
    if (classData.has("is_abstract")) {
      sd.setAbstract(classData.asBoolean("is_abstract"));
    } else {
      sd.setAbstract(false);
    }

    // Extensions
    addExtensions(sd, classData, locations, className);

    // Base definition
    setBaseDefinition(sd, classData);

    // Differential - populate elements
    StructureDefinition.StructureDefinitionDifferentialComponent diff = new StructureDefinition.StructureDefinitionDifferentialComponent();
    sd.setDifferential(diff);

    addElements(diff, className, classData, invariants);

    // Add functions as contained OperationDefinitions and extensions
    addFunctions(sd, classData);

    return sd;
  }

  private void addElements(StructureDefinition.StructureDefinitionDifferentialComponent diff, String className, JsonObject classData,
                           Map<String, Map<String, InvariantMapping>> invariants) {
    // Add base element for the class itself
    ElementDefinition baseElement = new ElementDefinition();
    baseElement.setPath(className);

    String documentation = classData.has("documentation") ? classData.asString("documentation") : "";
    baseElement.setShort(getFirstSentence(documentation));
    baseElement.setDefinition(documentation);
    baseElement.setMin(0);
    baseElement.setMax("*");
    baseElement.setIsModifier(false);

    // Add invariants as constraints on the root element
    addInvariantsToElement(baseElement, className, classData, invariants);

    diff.addElement(baseElement);

    // Add elements for properties
    if (classData.has("properties")) {
      JsonObject properties = classData.getJsonObject("properties");
      for (JsonProperty propEntry : properties.getProperties()) {
        String propName = propEntry.getName();
        JsonObject propData = propEntry.getValue().asJsonObject();

        ElementDefinition propElement = createPropertyElement(className, propName, propData);
        diff.addElement(propElement);
      }
    }
  }

  private void addInvariantsToElement(ElementDefinition element, String className, JsonObject classData,
                                      Map<String, Map<String, InvariantMapping>> invariants) {
    if (!classData.has("invariants")) {
      return;
    }

    JsonObject classInvariants = classData.getJsonObject("invariants");
    Map<String, InvariantMapping> mappings = invariants.get(className);

    for (JsonProperty invEntry : classInvariants.getProperties()) {
      String invName = invEntry.getName();
      String originalExpression = invEntry.getValue().asString();

      // Check if this is a terminology binding invariant
      String codeSetId = extractCodeSetId(originalExpression);
      if (codeSetId != null) {
        // This is a terminology binding - add as binding instead of constraint
        ElementDefinition.ElementDefinitionBindingComponent binding =
                new ElementDefinition.ElementDefinitionBindingComponent();
        binding.setStrength(Enumerations.BindingStrength.REQUIRED);
        binding.setValueSet("https://specifications.openehr.org/fhir/valueset-" + codeSetId);
        element.setBinding(binding);
        // Skip adding this as a regular constraint
        continue;
      }

      // Regular invariant - add as constraint
      ElementDefinition.ElementDefinitionConstraintComponent constraint =
              new ElementDefinition.ElementDefinitionConstraintComponent();

      constraint.setKey(invName);
      constraint.setSeverity(ElementDefinition.ConstraintSeverity.ERROR);

      // Use mapping if available, otherwise fall back to original
      if (mappings != null && mappings.containsKey(invName)) {
        InvariantMapping mapping = mappings.get(invName);
        constraint.setHuman(mapping.humanDescription);
        if (mapping.fhirPathExpression != null && !mapping.fhirPathExpression.trim().isEmpty()) {
          constraint.setExpression(mapping.fhirPathExpression);
        }
      } else {
        // Fallback to original expression
        constraint.setHuman(generateFallbackHumanDescription(invName, originalExpression));
        // Don't set expression if we can't translate it
      }

      element.addConstraint(constraint);
    }
  }

  /**
   * Extracts the code set ID from a terminology binding invariant expression.
   *
   * @param expression The invariant expression to check
   * @return The code set ID if this is a terminology binding, null otherwise
   */
  private String extractCodeSetId(String expression) {
    if (expression == null) {
      return null;
    }

    // Pattern: language \/= Void implies code_set (Code_set_id_xxxx).has_code (language)
    // Also handle variations with/without spaces around parentheses

    // Look for the pattern "code_set (Code_set_id_" or "code_set(Code_set_id_"
    String pattern1 = "code_set\\s*\\(\\s*Code_set_id_([^)]+)\\)";
    Pattern regex1 = Pattern.compile(pattern1);
    Matcher matcher1 = regex1.matcher(expression);

    if (matcher1.find()) {
      return matcher1.group(1);
    }

    // Alternative pattern with different spacing or capitalization
    String pattern2 = "code_set\\s*\\(\\s*CODE_SET_ID_([^)]+)\\)";
    Pattern regex2 = Pattern.compile(pattern2, Pattern.CASE_INSENSITIVE);
    Matcher matcher2 = regex2.matcher(expression);

    if (matcher2.find()) {
      return matcher2.group(1);
    }

    return null;
  }

  private String generateFallbackHumanDescription(String invName, String expression) {
    String desc = invName.replace("_", " ");

    // Basic pattern matching for common invariant types
    if (expression.toLowerCase().contains("not") && expression.contains("is_empty")) {
      return desc + " must not be empty";
    } else if (expression.contains("/= Void") || expression.contains("!= null")) {
      return desc + " must be present";
    } else if (expression.contains(">=") && expression.contains("<=")) {
      return desc + " must be within valid range";
    } else if (expression.contains("xor")) {
      return desc + " must satisfy exclusive constraint";
    } else if (expression.contains("implies")) {
      return desc + " must satisfy conditional constraint";
    } else {
      return desc + " must be valid";
    }
  }

  private Map<String, Map<String, InvariantMapping>> loadInvariantMappings(String yamlFile) throws IOException {
    Map<String, Map<String, InvariantMapping>> result = new HashMap<>();

    try (BufferedReader reader = Files.newBufferedReader(Paths.get(yamlFile))) {
      String line;
      String currentClass = null;
      String currentInvariant = null;
      InvariantMapping currentMapping = null;

      while ((line = reader.readLine()) != null) {
        String tline = line.trim();
        if (tline.isEmpty() || tline.startsWith("#")) {
          continue;
        }

        if (!line.startsWith(" ") && line.endsWith(":")) {
          // New class
          currentClass = line.substring(0, line.length() - 1);
          result.put(currentClass, new HashMap<>());
        } else if (line.startsWith("  ") && !line.startsWith("    ") && line.endsWith(":")) {
          // New invariant
          currentInvariant = line.substring(2, line.length() - 1);
          currentMapping = new InvariantMapping();
          if (currentClass != null) {
            result.get(currentClass).put(currentInvariant, currentMapping);
          }
        } else if (line.startsWith("    ") && currentMapping != null) {
          // Invariant property
          String[] parts = line.substring(4).split(":", 2);
          if (parts.length == 2) {
            String key = parts[0].trim();
            String value = parts[1].trim();

            // Remove quotes
            if (value.startsWith("\"") && value.endsWith("\"")) {
              value = value.substring(1, value.length() - 1);
            }

            switch (key) {
              case "original":
                currentMapping.originalExpression = value;
                break;
              case "human":
                currentMapping.humanDescription = value;
                break;
              case "fhirpath":
                if (!"null".equals(value)) {
                  currentMapping.fhirPathExpression = value;
                }
                break;
            }
          }
        }
      }
    }

    return result;
  }

  private ElementDefinition createPropertyElement(String className, String propName, JsonObject propData) {
    ElementDefinition element = new ElementDefinition();
    element.setPath(className + "." + propName);

    String documentation = propData.has("documentation") ? propData.asString("documentation") : "";
    element.setShort(getFirstSentence(documentation));
    element.setDefinition(documentation);

    // Set min based on is_mandatory
    if (propData.has("is_mandatory") && propData.asBoolean("is_mandatory")) {
      element.setMin(1);
    } else {
      element.setMin(0);
    }

    // Set max and type based on property structure
    setMaxAndType(element, propData);

    element.setIsModifier(false);

    return element;
  }

  private void setMaxAndType(ElementDefinition element, JsonObject propData) {
    String max = "1"; // default
    ElementDefinition.TypeRefComponent typeRef = null;

    // Determine property type structure
    if (propData.has("_type")) {
      String bmm_type = propData.asString("_type");

      switch (bmm_type) {
        case "P_BMM_CONTAINER_PROPERTY":
          // Container property with cardinality
          if (propData.has("cardinality")) {
            JsonObject cardinality = propData.getJsonObject("cardinality");
            if (cardinality.has("upper_unbounded") && cardinality.asBoolean("upper_unbounded")) {
              max = "*";
            } else if (cardinality.has("upper")) {
              max = cardinality.asString("upper");
            }
          }

          // Get contained type
          if (propData.has("type_def")) {
            JsonObject typeDef = propData.getJsonObject("type_def");
            typeRef = createTypeRef(typeDef);
          } else {
            throw new RuntimeException("P_BMM_CONTAINER_PROPERTY missing type_def for property: " + element.getPath());
          }
          break;

        case "P_BMM_GENERIC_PROPERTY":
          // Generic property
          if (propData.has("type_def")) {
            JsonObject typeDef = propData.getJsonObject("type_def");
            typeRef = createTypeRef(typeDef);
          } else {
            throw new RuntimeException("P_BMM_GENERIC_PROPERTY missing type_def for property: " + element.getPath());
          }
          break;

        case "P_BMM_SINGLE_PROPERTY_OPEN":
          // Open single property using generic parameter
          if (propData.has("type")) {
            String typeName = propData.asString("type");
            typeRef = new ElementDefinition.TypeRefComponent();
            typeRef.setCode(getTypeCode(typeName));
          } else {
            throw new RuntimeException("P_BMM_SINGLE_PROPERTY_OPEN missing type for property: " + element.getPath());
          }
          break;

        default:
          throw new RuntimeException("Unexpected property _type: " + bmm_type + " for property: " + element.getPath());
      }
    } else {
      // Simple property with direct type
      if (propData.has("type")) {
        String typeName = propData.asString("type");
        typeRef = new ElementDefinition.TypeRefComponent();
        typeRef.setCode(getTypeCode(typeName));
      } else {
        throw new RuntimeException("Property missing type information: " + element.getPath());
      }
    }

    element.setMax(max);

    if (typeRef != null) {
      element.addType(typeRef);
    }
  }

  private ElementDefinition.TypeRefComponent createTypeRef(JsonObject typeDef) {
    ElementDefinition.TypeRefComponent typeRef = new ElementDefinition.TypeRefComponent();

    if (typeDef.has("_type")) {
      String typeDefType = typeDef.asString("_type");

      switch (typeDefType) {
        case "P_BMM_GENERIC_TYPE":
          // Generic type with parameters
          if (!typeDef.has("root_type")) {
            throw new RuntimeException("P_BMM_GENERIC_TYPE missing root_type");
          }

          String rootType = typeDef.asString("root_type");
          typeRef.setCode(getTypeCode(rootType));

          // Add generic parameters as extensions
          if (typeDef.has("generic_parameters")) {
            JsonElement genericParams = typeDef.get("generic_parameters");
            if (genericParams.isJsonArray()) {
              int paramIndex = 0;
              for (JsonElement param : genericParams.asJsonArray()) {
                String paramType = param.asString();
                // Use standard generic parameter names: T, U, V, etc.
                String paramName = getGenericParameterName(paramIndex);
                Extension paramExt = createTypeParameterExtension(paramName, paramType);
                typeRef.addExtension(paramExt);
                paramIndex++;
              }
            }
          }
          break;

        default:
          throw new RuntimeException("Unexpected type_def _type: " + typeDefType);
      }
    } else if (typeDef.has("root_type")) {
      // Direct generic type definition (without _type wrapper)
      String rootType = typeDef.asString("root_type");
      typeRef.setCode(getTypeCode(rootType));

      // Add generic parameters as extensions
      if (typeDef.has("generic_parameters")) {
        JsonElement genericParams = typeDef.get("generic_parameters");
        if (genericParams.isJsonArray()) {
          int paramIndex = 0;
          for (JsonElement param : genericParams.asJsonArray()) {
            String paramType = param.asString();
            // Use standard generic parameter names: T, U, V, etc.
            String paramName = getGenericParameterName(paramIndex);
            Extension paramExt = createTypeParameterExtension(paramName, paramType);
            typeRef.addExtension(paramExt);
            paramIndex++;
          }
        }
      }
    } else if (typeDef.has("container_type")) {
      // Container type like List, Array, Hash
      String containerType = typeDef.asString("container_type");
      // For now, we ignore the container type and just use the contained type
      if (typeDef.has("type")) {
        String containedType = typeDef.asString("type");
        typeRef.setCode(getTypeCode(containedType));
      } else if (typeDef.has("type_def")) {
        // Nested type definition
        return createTypeRef(typeDef.getJsonObject("type_def"));
      } else {
        throw new RuntimeException("Container type missing contained type information");
      }
    } else {
      throw new RuntimeException("Unknown type_def structure: " + typeDef.toString());
    }

    return typeRef;
  }

  private String getGenericParameterName(int index) {
    // Standard generic parameter naming: T, U, V, W, X, Y, Z
    char paramChar = (char) ('T' + index);
    if (paramChar <= 'Z') {
      return String.valueOf(paramChar);
    } else {
      // If we need more than 7 parameters, use T1, T2, etc.
      return "T" + (index - 6);
    }
  }

  private Extension createTypeParameterExtension(String paramName, String paramType) {
    Extension typeParamExt = new Extension();
    typeParamExt.setUrl(TYPE_PARAMETER_EXTENSION_URL);

    // Name sub-extension
    Extension nameExt = new Extension();
    nameExt.setUrl("name");
    nameExt.setValue(new CodeType(paramName));
    typeParamExt.addExtension(nameExt);

    // Type sub-extension
    Extension typeExt = new Extension();
    typeExt.setUrl("type");
    typeExt.setValue(new UriType(getTypeCode(paramType)));
    typeParamExt.addExtension(typeExt);

    return typeParamExt;
  }

  private String getTypeCode(String typeName) {
    // Handle primitive types
    switch (typeName) {
      case "String":
        return "string";
      case "Integer":
      case "Integer64":
        return "integer";
      case "Boolean":
        return "boolean";
      case "Real":
      case "Double":
        return "decimal";
      case "Character":
        return "string"; // FHIR doesn't have a char type
      case "Octet":
        return "base64Binary";
      case "Any":
        return "Element"; // FHIR base type for Any
      default:
        // For BMM-defined types, use openEHR namespace with hyphens
        return OPENEHR_BASE_URL + typeName.replace("_", "-");
    }
  }

  private void addFunctions(StructureDefinition sd, JsonObject classData) {
    if (!classData.has("functions")) {
      return;
    }

    JsonObject functions = classData.getJsonObject("functions");
    for (JsonProperty funcEntry : functions.getProperties()) {
      String funcName = funcEntry.getName();
      JsonObject funcData = funcEntry.getValue().asJsonObject();

      // Add extension pointing to the contained operation
      Extension funcExt = new Extension();
      funcExt.setUrl("http://hl7.org/fhir/tools/StructureDefinition/type-operation");
      funcExt.setValue(new CanonicalType("#" + funcName));
      sd.addExtension(funcExt);

      // Create contained OperationDefinition
      OperationDefinition opDef = createOperationDefinition(funcName, funcData);
      sd.addContained(opDef);
    }
  }

  private OperationDefinition createOperationDefinition(String funcName, JsonObject funcData) {
    OperationDefinition opDef = new OperationDefinition();

    opDef.setId(funcName);
    opDef.setName(funcName);
    opDef.setTitle(funcName);
    opDef.setStatus(Enumerations.PublicationStatus.ACTIVE);
    opDef.setKind(OperationDefinition.OperationKind.OPERATION);
    opDef.setCode(funcName);
    opDef.setSystem(false);
    opDef.setInstance(true);

    // Set description from documentation
    if (funcData.has("documentation")) {
      opDef.setDescription(funcData.asString("documentation"));
    }

    // Add input parameters
    if (funcData.has("parameters")) {
      JsonObject parameters = funcData.getJsonObject("parameters");
      for (JsonProperty paramEntry : parameters.getProperties()) {
        String paramName = paramEntry.getName();
        JsonObject paramData = paramEntry.getValue().asJsonObject();

        OperationDefinition.OperationDefinitionParameterComponent param = createInputParameter(paramName, paramData);
        opDef.addParameter(param);
      }
    }

    // Add return parameter if function has a result
    if (funcData.has("result")) {
      JsonObject resultData = funcData.getJsonObject("result");
      OperationDefinition.OperationDefinitionParameterComponent returnParam = createReturnParameter(resultData);
      if (returnParam != null) {
        opDef.addParameter(returnParam);
      }
    }

    return opDef;
  }

  private OperationDefinition.OperationDefinitionParameterComponent createInputParameter(String paramName, JsonObject paramData) {
    OperationDefinition.OperationDefinitionParameterComponent param = new OperationDefinition.OperationDefinitionParameterComponent();

    param.setName(paramName);
    param.setUse(Enumerations.OperationParameterUse.IN);
    param.setMin(1); // Assume required unless specified otherwise
    param.setMax("1"); // Default single value

    // Handle different parameter type structures
    if (paramData.has("_type")) {
      String bmm_type = paramData.asString("_type");
      switch (bmm_type) {
        case "P_BMM_CONTAINER_FUNCTION_PARAMETER":
          param.setMax("*"); // Container allows multiple values
          if (paramData.has("type_def")) {
            JsonObject typeDef = paramData.getJsonObject("type_def");
            String fhirType = getParameterTypeFromTypeDef(typeDef);
            if (fhirType != null) {
              param.setType(Enumerations.FHIRTypes.fromCode(fhirType));
            }
          } else {
            throw new RuntimeException("P_BMM_CONTAINER_FUNCTION_PARAMETER missing type_def for parameter: " + paramName);
          }
          // Handle cardinality if present
          if (paramData.has("cardinality")) {
            JsonObject cardinality = paramData.getJsonObject("cardinality");
            if (cardinality.has("lower")) {
              param.setMin(cardinality.asInteger("lower"));
            }
          }
          break;
        default:
          throw new RuntimeException("Unexpected parameter _type: " + bmm_type + " for parameter: " + paramName);
      }
    } else if (paramData.has("type")) {
      String typeName = paramData.asString("type");
      String fhirType = getFhirParameterType(typeName);
      if (fhirType != null) {
        param.setType(Enumerations.FHIRTypes.fromCode(fhirType));
      } else {
        // For complex types, use Parameters resource to allow structured data
        param.setType(Enumerations.FHIRTypes.PARAMETERS);
      }
    } else {
      throw new RuntimeException("Function parameter missing type: " + paramName);
    }

    return param;
  }

  private OperationDefinition.OperationDefinitionParameterComponent createReturnParameter(JsonObject resultData) {
    OperationDefinition.OperationDefinitionParameterComponent param = new OperationDefinition.OperationDefinitionParameterComponent();

    param.setName("return");
    param.setUse(Enumerations.OperationParameterUse.OUT);
    param.setMin(1);
    param.setMax("1");

    // Check for nullable return
    if (resultData.has("is_nullable") && resultData.asBoolean("is_nullable")) {
      param.setMin(0);
    }

    // Handle different result type structures
    if (resultData.has("type")) {
      String typeName = resultData.asString("type");
      String fhirType = getFhirParameterType(typeName);
      if (fhirType != null) {
        param.setType(Enumerations.FHIRTypes.fromCode(fhirType));
      } else {
        // For complex types, use Parameters to allow structured data
        param.setType(Enumerations.FHIRTypes.PARAMETERS);
      }
    } else if (resultData.has("_type")) {
      // Handle complex return types
      String bmm_type = resultData.asString("_type");
      switch (bmm_type) {
        case "P_BMM_CONTAINER_TYPE":
          param.setMax("*"); // Multiple values
          String fhirType = getParameterTypeFromTypeDef(resultData);
          if (fhirType != null) {
            param.setType(Enumerations.FHIRTypes.fromCode(fhirType));
          }
          break;
        case "P_BMM_GENERIC_TYPE":
          String genericFhirType = getParameterTypeFromTypeDef(resultData);
          if (genericFhirType != null) {
            param.setType(Enumerations.FHIRTypes.fromCode(genericFhirType));
          }
          break;
        default:
          throw new RuntimeException("Unexpected return type structure: " + bmm_type);
      }
    } else {
      throw new RuntimeException("Function result missing type information");
    }

    return param;
  }

  private String getParameterTypeFromTypeDef(JsonObject typeDef) {
    if (typeDef.has("container_type")) {
      // Container type - get the contained type
      if (typeDef.has("type")) {
        String containedType = typeDef.asString("type");
        return getFhirParameterType(containedType);
      } else if (typeDef.has("type_def")) {
        // Nested type definition
        return getParameterTypeFromTypeDef(typeDef.getJsonObject("type_def"));
      }
    } else if (typeDef.has("root_type")) {
      // Generic type - use root type
      String rootType = typeDef.asString("root_type");
      return getFhirParameterType(rootType);
    } else if (typeDef.has("type")) {
      // Direct type reference
      String typeName = typeDef.asString("type");
      return getFhirParameterType(typeName);
    }

    // Fallback for complex types
    return "Parameters";
  }

  private String getFhirParameterType(String bmmTypeName) {
    if (bmmTypeName == null) {
      return "Parameters";
    }

    // Handle primitive types that map directly to FHIR primitives
    switch (bmmTypeName) {
      case "String":
        return "string";
      case "Integer":
      case "Integer64":
        return "integer";
      case "Boolean":
        return "boolean";
      case "Real":
      case "Double":
        return "decimal";
      case "Character":
        return "string";
      case "Date":
        return "date";
      case "DateTime":
        return "dateTime";
      case "Time":
        return "time";
      case "Duration":
        return "Duration";
      case "Instant":
        return "instant";
      case "PositiveInt":
        return "positiveInt";
      case "UnsignedInt":
        return "unsignedInt";
      case "Decimal":
        return "decimal";
      case "Base64Binary":
      case "Octet":
        return "base64Binary";
      case "Uri":
      case "Url":
        return "uri";
      case "Uuid":
        return "uuid";
      case "Oid":
        return "oid";
      case "Code":
        return "code";
      case "Id":
        return "id";
      case "Markdown":
        return "markdown";
      case "void":
        return null; // No return parameter for void functions

      // Base types
      case "Any":
      case "Element":
        return "Element";
      case "Resource":
        return "Resource";
      case "DomainResource":
        return "DomainResource";
      case "BackboneElement":
        return "BackboneElement";

      // Special openEHR primitive-like types that can map to FHIR primitives
      case "DV_TEXT":
      case "DV_CODED_TEXT":
        return "string";
      case "DV_BOOLEAN":
        return "boolean";
      case "DV_COUNT":
      case "DV_ORDINAL":
        return "integer";
      case "DV_QUANTITY":
      case "DV_PROPORTION":
        return "decimal";
      case "DV_DATE":
        return "date";
      case "DV_TIME":
        return "time";
      case "DV_DATE_TIME":
        return "dateTime";
      case "DV_DURATION":
        return "Duration";
      case "DV_URI":
      case "DV_EHR_URI":
        return "uri";
      case "DV_IDENTIFIER":
        return "Identifier";
      case "CODE_PHRASE":
        return "Coding";

      // Collections and complex structures
      case "List":
      case "Array":
      case "Set":
      case "Hash":
      case "Map":
        return "Parameters"; // Use Parameters for complex collections

      default:
        // For all other openEHR types, use Parameters to allow structured data
        // This allows the operation to pass complex openEHR structures as nested parameters
        return "Parameters";
    }
  }

  private String getFirstSentence(String text) {
    if (text == null || text.trim().isEmpty()) {
      return "";
    }

    text = text.trim();

    // Find first sentence ending with period, exclamation, or question mark
    int firstPeriod = text.indexOf('.');
    int firstExclamation = text.indexOf('!');
    int firstQuestion = text.indexOf('?');

    int firstSentenceEnd = -1;

    if (firstPeriod != -1) {
      firstSentenceEnd = firstPeriod;
    }
    if (firstExclamation != -1 && (firstSentenceEnd == -1 || firstExclamation < firstSentenceEnd)) {
      firstSentenceEnd = firstExclamation;
    }
    if (firstQuestion != -1 && (firstSentenceEnd == -1 || firstQuestion < firstSentenceEnd)) {
      firstSentenceEnd = firstQuestion;
    }

    if (firstSentenceEnd != -1) {
      return text.substring(0, firstSentenceEnd + 1).trim();
    } else {
      // No sentence ending found, return first 100 chars or whole text if shorter
      return text.length() > 100 ? text.substring(0, 100) + "..." : text;
    }
  }

  private void addExtensions(StructureDefinition sd, JsonObject classData, Map<String, String> locations, String className) {
    // Web source extension
    if (locations != null && locations.containsKey(className)) {
      Extension webSourceExt = new Extension();
      webSourceExt.setUrl(WEB_SOURCE_EXTENSION_URL);
      webSourceExt.setValue(new UrlType(locations.get(className)));
      sd.addExtension(webSourceExt);
    }

    // Type parameter extensions
    if (classData.has("generic_parameter_defs")) {
      JsonObject genericParams = classData.getJsonObject("generic_parameter_defs");

      for (JsonProperty paramEntry : genericParams.getProperties()) {
        String paramName = paramEntry.getName();
        JsonObject paramData = paramEntry.getValue().asJsonObject();

        Extension typeParamExt = new Extension();
        typeParamExt.setUrl(TYPE_PARAMETER_EXTENSION_URL);

        // Name sub-extension
        Extension nameExt = new Extension();
        nameExt.setUrl("name");
        nameExt.setValue(new CodeType(paramName));
        typeParamExt.addExtension(nameExt);

        // Type sub-extension
        Extension typeExt = new Extension();
        typeExt.setUrl("type");

        String conformsToType = "Any"; // default
        if (paramData.has("conforms_to_type")) {
          conformsToType = paramData.asString("conforms_to_type");
        }

        typeExt.setValue(new UriType(OPENEHR_BASE_URL + conformsToType.replace("_", "-")));
        typeParamExt.addExtension(typeExt);

        sd.addExtension(typeParamExt);
      }
    }
  }

  private void setBaseDefinition(StructureDefinition sd, JsonObject classData) {
    String baseDefinition = FHIR_BASE_URL + "Base"; // default

    if (classData.has("ancestors")) {
      JsonElement ancestorsElement = classData.get("ancestors");
      if (ancestorsElement.isJsonArray() && ancestorsElement.asJsonArray().size() > 0) {
        String firstAncestor = ancestorsElement.asJsonArray().get(0).asString();

        // Skip "Any" as it's not a real FHIR type
        if (!"Any".equals(firstAncestor)) {
          baseDefinition = OPENEHR_BASE_URL + firstAncestor.replace("_", "-");
        }
      }
    }

    sd.setBaseDefinition(baseDefinition);
  }

  private static class InvariantMapping {
    String originalExpression;
    String humanDescription;
    String fhirPathExpression;
  }
}