package org.hl7.fhir.igtools.publisher;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import lombok.Getter;
import org.apache.tools.ant.filters.StringInputStream;
import org.hl7.fhir.r5.context.ContextUtilities;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.formats.IParser;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r5.renderers.RendererFactory;
import org.hl7.fhir.r5.renderers.ResourceRenderer;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;

/**
 * Builds a compact JSON search index for client-side searching.
 *
 * Features:
 * - Dictionary encoding for low-cardinality columns (saves space)
 * - Direct values for high-cardinality columns
 * - FHIRPath evaluation for extracting search values
 *
 * Output format:
 * {
 *   "columns": ["id", "code", "name", ...],
 *   "dictionaries": {
 *     "form": ["Tablet", "Capsule", ...],
 *     ...
 *   },
 *   "resources": [
 *     ["PH6040-1", "Abacavir", 0, ...],  // 0 = index into form dictionary
 *     ...
 *   ]
 * }
 */
public class ClientSideIndexBuilder {

  private static final double DICTIONARY_THRESHOLD = 0.1; // Use dictionary if unique values < 10% of total
  private static final int DICTIONARY_MAX_SIZE = 1000;    // Or if fewer than 1000 unique values

  private final FHIRPathEngine fhirPath;
  private final List<SearchParameter> searchParameters;
  private final String resourceFolder;
  private final String outputFolder;
  private final String name;
  private final RenderingContext rc;
  private final String resourceType;
  private final ContextUtilities cu;
  @Getter private final Set<String> urls = new HashSet<>();

  // Column metadata
  private List<String> columnNames = new ArrayList<>();
  private List<SearchParameter> columnParams = new ArrayList<>();

  // Collected data
  private List<Map<String, List<String>>> resourceData = new ArrayList<>();
  private Map<String, Set<String>> uniqueValues = new LinkedHashMap<>();
  private StringBuilder dataset = new StringBuilder();

  public ClientSideIndexBuilder(ContextUtilities cu, FHIRPathEngine fhirPath, RenderingContext rc,
                                String resourceType, List<SearchParameter> searchParameters,
                                String name, String resourceFolder, String outputFolder) {
    this.fhirPath = fhirPath;
    this.cu = cu;
    this.rc = rc;
    this.name = name;
    this.resourceFolder = resourceFolder;
    this.searchParameters = searchParameters;
    this.outputFolder = outputFolder;
    this.resourceType = resourceType;
  }

  /**
   * Build the index and write to output file
   */
  public IndexStats build() throws IOException {
    dataset.append("{");
    // Initialize columns from search parameters
    initializeColumns();

    // Process all resources
    processResources();

    // Analyze cardinality and build output
    JsonObject index = buildIndex();

    // Write output
    writeOutput(index);

    return new IndexStats(resourceData.size(), columnNames.size(),
            calculateOutputSize(index));
  }

  private void initializeColumns() {
    // Always include id as first column
    columnNames.add("id");
    columnParams.add(null);
    uniqueValues.put("id", new LinkedHashSet<>());

    // Add search parameter columns
    for (SearchParameter sp : searchParameters) {
      if (sp.getExpression() != null && !sp.getExpression().isEmpty()) {
        columnNames.add(sp.getCode());
        columnParams.add(sp);
        uniqueValues.put(sp.getCode(), new LinkedHashSet<>());
      }
    }
  }

  private void processResources() throws IOException {
    try (Stream<Path> paths = Files.walk(Path.of(resourceFolder))) {
      paths.filter(Files::isRegularFile)
              .filter(p -> p.toString().endsWith(".json"))
              .forEach(this::processResourceFile);
    }
  }

  private void processResourceFile(Path file) {
    try {
      String content = Files.readString(file);
      Element resource = parseResource(content);
      if (resource != null && resource.fhirType().equals(resourceType)) {
        generateNarrative(resource);
        processResource(resource);
        addResourceToDataset(resource);
      }

    } catch (Exception e) {
      // Skip files that can't be parsed
      System.err.println("Warning: Could not process " + file + ": " + e.getMessage());
    }
  }

  private void addResourceToDataset(Element resource) throws IOException {
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    Manager.compose(cu.getWorker(), resource, bs, Manager.FhirFormat.JSON, IParser.OutputStyle.NORMAL, null);
    String json = new String(bs.toByteArray());
    if (dataset.length() > 2) {
      dataset.append(",");
    }
    dataset.append("\""+resource.getIdBase()+"\": ");
    dataset.append(json);
  }

  private void generateNarrative(Element resource) throws EOperationOutcome, IOException {
    ResourceWrapper rw = ResourceWrapper.forResource(cu, resource);
    ResourceRenderer rr = RendererFactory.factory(rw, rc);
    rr.renderResource(rw);
  }

  private Element parseResource(String json) {
    try {
      return Manager.parseSingle(fhirPath.getWorker(), new StringInputStream(json), Manager.FhirFormat.JSON);
    } catch (Exception e) {
      return null;
    }
  }

  private void processResource(Element resource) {
    findUrls(resource);
    Map<String, List<String>> row = new LinkedHashMap<>();

    for (int i = 0; i < columnNames.size(); i++) {
      String colName = columnNames.get(i);
      SearchParameter sp = columnParams.get(i);

      List<String> values;
      if (sp == null) {
        // ID column
        values = Collections.singletonList(resource.getIdBase());
      } else {
        // Evaluate FHIRPath
        values = evaluateSearchParam(resource, sp);
      }

      row.put(colName, values);
      uniqueValues.get(colName).addAll(values);
    }

    resourceData.add(row);
  }

  private void findUrls(Element element) {
    switch (element.fhirType()) {
      case "url":
      case "uri":
      case "canonical":
      case "uuid":
      case "oid":
        if (element.hasValue()) {
          urls.add(element.getValue());
        }
        break;
      case "Reference" :
        String ref = element.getNamedChildValue("reference");
        if (ref != null) {
          urls.add(ref);
        }
    }
    for (Element child : element.getChildren()) {
      findUrls(child);
    }
  }

  private List<String> evaluateSearchParam(Element resource, SearchParameter sp) {
    List<String> result = new ArrayList<>();

    try {
      String expression = sp.getExpression();
      if (expression == null || expression.isEmpty()) {
        return result;
      }

      List<Base> values = fhirPath.evaluate(resource, expression);

      for (Base value : values) {
        String extracted = extractStringValue(value, sp.getType());
        if (extracted != null && !extracted.isEmpty()) {
          result.add(extracted);
        }
      }
    } catch (Exception e) {
      // FHIRPath evaluation failed - return empty
    }

    return result;
  }

  private List<String> splitUnionExpression(String expression) {
    List<String> parts = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int parenDepth = 0;

    for (int i = 0; i < expression.length(); i++) {
      char c = expression.charAt(i);
      if (c == '(') {
        parenDepth++;
        current.append(c);
      } else if (c == ')') {
        parenDepth--;
        current.append(c);
      } else if (c == '|' && parenDepth == 0) {
        parts.add(current.toString().trim());
        current = new StringBuilder();
      } else {
        current.append(c);
      }
    }

    if (current.length() > 0) {
      parts.add(current.toString().trim());
    }

    return parts;
  }

  /**
   * Extract a string value from a FHIRPath result based on search parameter type
   */
  private String extractStringValue(Base value, Enumerations.SearchParamType type) {
    if (value == null) return null;

    if (type == null) {
      type = Enumerations.SearchParamType.STRING;
    }

    switch (type) {
      case TOKEN:
        return extractTokenValue(value);
      case STRING:
        return extractSimpleValue(value);
      case REFERENCE:
        return extractReferenceValue(value);
      case DATE:
        return extractSimpleValue(value);
      case QUANTITY:
        return extractQuantityValue(value);
      case URI:
        return extractSimpleValue(value);
      default:
        return extractSimpleValue(value);
    }
  }

  private String extractTokenValue(Base value) {
    if (value instanceof Coding) {
      Coding c = (Coding) value;
      // Return code only for simpler client-side matching
      return c.hasCode() ? c.getCode() : null;
    } else if (value instanceof CodeableConcept) {
      CodeableConcept cc = (CodeableConcept) value;
      if (cc.hasCoding() && cc.getCodingFirstRep().hasCode()) {
        return cc.getCodingFirstRep().getCode();
      }
      return null;
    } else if (value instanceof Identifier) {
      Identifier id = (Identifier) value;
      return id.hasValue() ? id.getValue() : null;
    } else if (value instanceof PrimitiveType) {
      return ((PrimitiveType<?>) value).asStringValue();
    }
    return value.toString();
  }

  private String extractSimpleValue(Base value) {
    if (value.isPrimitive()) {
      return value.primitiveValue();
    } else {
      return value.toString();
    }
  }

  private String extractReferenceValue(Base value) {
    if (value instanceof Reference) {
      Reference ref = (Reference) value;
      return ref.hasReference() ? ref.getReference() : null;
    }
    return extractSimpleValue(value);
  }

  private String extractQuantityValue(Base value) {
    if (value instanceof Quantity) {
      Quantity q = (Quantity) value;
      return q.hasValue() ? q.getValue().toPlainString() : null;
    }
    return extractSimpleValue(value);
  }

  private JsonObject buildIndex() {
    JsonObject index = new JsonObject();

    // Column names
    JsonArray columns = new JsonArray();
    columnNames.forEach(columns::add);
    index.add("columns", columns);

    // Determine which columns use dictionary encoding
    Map<String, List<String>> dictionaries = new LinkedHashMap<>();
    Set<String> dictionaryColumns = new HashSet<>();

    int totalResources = resourceData.size();
    for (String colName : columnNames) {
      Set<String> unique = uniqueValues.get(colName);
      int uniqueCount = unique.size();

      // Use dictionary if cardinality is low
      if (uniqueCount < totalResources * DICTIONARY_THRESHOLD || uniqueCount < DICTIONARY_MAX_SIZE) {
        if (uniqueCount < totalResources) { // Only if there's actual deduplication benefit
          List<String> dict = new ArrayList<>(unique);
          dictionaries.put(colName, dict);
          dictionaryColumns.add(colName);
        }
      }
    }

    // Add dictionaries to output
    JsonObject dicts = new JsonObject();
    for (Map.Entry<String, List<String>> entry : dictionaries.entrySet()) {
      JsonArray arr = new JsonArray();
      entry.getValue().forEach(arr::add);
      dicts.add(entry.getKey(), arr);
    }
    index.add("dictionaries", dicts);

    // Build resource rows
    JsonArray resources = new JsonArray();
    for (Map<String, List<String>> row : resourceData) {
      JsonArray resourceRow = new JsonArray();

      for (String colName : columnNames) {
        List<String> values = row.get(colName);

        if (dictionaryColumns.contains(colName)) {
          // Use dictionary index
          if (values.isEmpty()) {
            resourceRow.add(-1); // No value
          } else if (values.size() == 1) {
            resourceRow.add(dictionaries.get(colName).indexOf(values.get(0)));
          } else {
            // Multiple values - store as array of indices
            JsonArray indices = new JsonArray();
            for (String v : values) {
              indices.add(dictionaries.get(colName).indexOf(v));
            }
            resourceRow.add(indices);
          }
        } else {
          // Store value directly
          if (values.isEmpty()) {
            resourceRow.add((String) null);
          } else if (values.size() == 1) {
            resourceRow.add(values.get(0));
          } else {
            // Multiple values - store as array
            JsonArray arr = new JsonArray();
            values.forEach(arr::add);
            resourceRow.add(arr);
          }
        }
      }

      resources.add(resourceRow);
    }
    index.add("resources", resources);

    return index;
  }

  private void writeOutput(JsonObject index) throws IOException {
    FileUtilities.createDirectory(outputFolder);
    String filename = Utilities.path(outputFolder, name);

    // first, the index
    String json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(index, false);
    FileUtilities.stringToFile(json, filename+"-index.json");
    String js = "const INDEX = "+json+";";
    FileUtilities.stringToFile(js, filename+"-index.js");

    // now the data
    dataset.append("}");
    json = dataset.toString();
    FileUtilities.stringToFile(json, filename+"-data.json");
    js = "const DATA_SET = "+json+";";
    FileUtilities.stringToFile(js, filename+"-data.js");
  }

  private long calculateOutputSize(JsonObject index) {
    String json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(index, false);
    return json.length();
  }

  /**
   * Statistics about the generated index
   */
  public static class IndexStats {
    public final int resourceCount;
    public final int columnCount;
    public final long outputSizeBytes;

    public IndexStats(int resourceCount, int columnCount, long outputSizeBytes) {
      this.resourceCount = resourceCount;
      this.columnCount = columnCount;
      this.outputSizeBytes = outputSizeBytes;
    }

    @Override
    public String toString() {
      return String.format("IndexStats: %d resources, %d columns, %.2f KB",
              resourceCount, columnCount, outputSizeBytes / 1024.0);
    }
  }
}