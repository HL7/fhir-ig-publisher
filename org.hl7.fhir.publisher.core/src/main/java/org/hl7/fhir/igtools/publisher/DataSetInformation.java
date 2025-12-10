package org.hl7.fhir.igtools.publisher;

import lombok.Getter;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.SearchParameter;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataSetInformation {
  @Getter private final JsonObject config;
  @Getter private final List<SearchParameter> allSearchParameters = new ArrayList<>();
  @Getter private final List<SearchParameter> searchParameters = new ArrayList<>();
  @Getter private final List<SearchParameter> columns = new ArrayList<>();


  public DataSetInformation(JsonObject config) {
    this.config = config;
  }

  public void load(String rootDir) throws IOException {
    Map<String, SearchParameter> map = new HashMap<>();
    for (String s : config.getStrings("searches")) {
      String file = Utilities.path(rootDir, s);
      SearchParameter sp = (SearchParameter) new JsonParser().parse(new FileInputStream(file));
      allSearchParameters.add(sp);
      map.put(sp.getCode(), sp);
    }
    for (String s : config.getStrings("parameters")) {
      SearchParameter sp = map.get(s);
      if (sp != null) {
        searchParameters.add(sp);
      }
    }
    for (String s : config.getStrings("columns")) {
      SearchParameter sp = map.get(s);
      if (sp != null) {
        columns.add(sp);
      }
    }
  }

  public String name() {
    return config.asString("name");
  }

  public String buildFragment(String param) {
    switch (param) {
      case "form":
        return buildForm();
      case "results":
        return buildResults();
      case "script" :
        return buildScript();
      case "view" :
        return buildView();
      default:
        throw new Error("Invalid dataset mode "+param);
    }
  }

  private String buildView() {
    return  "  <div id=\"content\">\n" +
            "    <div class=\"loading\">Loading resource...</div>\n" +
            "  </div>\n" +
            "\n" +
            "  <style>\n" +
            "  /* JSON toggle section */\n" +
            ".json-section {\n" +
            "  margin-top: 20px;\n" +
            "  border: 1px solid #ddd;\n" +
            "  border-radius: 4px;\n" +
            "}\n" +
            "\n" +
            ".json-header {\n" +
            "  display: flex;\n" +
            "  justify-content: space-between;\n" +
            "  align-items: center;\n" +
            "  padding: 10px 15px;\n" +
            "  background: #f5f5f5;\n" +
            "  cursor: pointer;\n" +
            "  user-select: none;\n" +
            "}\n" +
            "\n" +
            ".json-header:hover {\n" +
            "  background: #eee;\n" +
            "}\n" +
            "\n" +
            ".json-title {\n" +
            "  font-weight: bold;\n" +
            "}\n" +
            "\n" +
            ".json-toggle {\n" +
            "  color: #0366d6;\n" +
            "}\n" +
            "\n" +
            ".json-content {\n" +
            "  display: none;  /* Hidden by default */\n" +
            "  padding: 15px;\n" +
            "  overflow: auto;\n" +
            "  max-height: 500px;\n" +
            "}\n" +
            "\n" +
            ".json-content.open {\n" +
            "  display: block;  /* Shown when .open class is added */\n" +
            "}\n" +
            "\n" +
            ".raw-json {\n" +
            "  margin: 0;\n" +
            "  white-space: pre-wrap;\n" +
            "  word-wrap: break-word;\n" +
            "  font-size: 12px;\n" +
            "}" +
            "  </style>\n"+
            "  <script src=\""+name()+"-data.js\"></script>\n" +
            "  <script src=\"assets/js/datasets.js\"></script>\n" +
            "  <script>\n" +
            "    // Initialize\n" +
            "    const id = getIdParam();\n" +
            "    loadResource(id);\n" +
            "  </script>";
  }

  private String buildScript() {
    StringBuilder b = new StringBuilder();
    b.append(
            "  <script src=\""+name()+"-index.js\"></script>\n" +
            "  <script src=\"assets/js/datasets.js\"></script>\n" +
            "  <script>\n" +
            "  cfg = {\n" +
            "    // Search parameters: list of column names to search on\n" +
            "    searchParams: [");
    List<String> list = new ArrayList<>();
    for (SearchParameter sp : searchParameters) {
      list.add("'"+sp.getCode()+"'");
    }
    b.append(CommaSeparatedStringBuilder.join(", ", list));
    b.append("],\n" +
            "    \n" +
            "    // Columns to display in results table\n" +
            "    // Each entry: { key: 'columnName', label: 'Display Label' }\n" +
            "    displayColumns: [\n" +
            "      { key: 'id', label: 'Code', isLink: true }");
    for (SearchParameter sp : columns) {
      b.append(",\n      { key: '" + sp.getCode() + "', label: '" + sp.getTitle() + "' }");
    }
    b.append(
            "\n    ],\n" +
            "    \n" +
            "    // Detail page URL (id will be appended as query param)\n" +
            "    detailPage: '"+config.asString("detail")+"',\n" +
            "    \n" +
            "    // Maximum results to display\n" +
            "    maxResults: 100\n" +
            "  };\n" +
            "\n" +
            "    // Run on page load\n" +
            "    init(cfg);\n" +
            "  </script>\n");
    return b.toString();
  }

  private String buildForm() {
    StringBuilder b = new StringBuilder();
    b.append(" <form method=\"GET\" id=\"searchForm\">\n" +
            "   <table class=\"grid\"><tr>\r\n");
    for (SearchParameter sp : searchParameters) {
      b.append("<td><label for=\"" + sp.getCode() + "\">" + sp.getTitle() + "</label><input name=\"" + sp.getCode() + "\" id=\"" + sp.getCode() + "\" placeholder=\"" + sp.getDescription() + "\" type=\"text\"/></td>\r\n");
    }
    b.append("<td><button type=\"submit\">Search</button></td></tr></table>\n" +
            "  </form>");
    return b.toString();
  }

  private String buildResults() {
    return "<div class=\"results\">\n" +
            "    <div class=\"results-header\">\n" +
            "      <span id=\"totalCount\" class=\"total-count\"></span>\n" +
            "      <span id=\"searchTime\" class=\"search-time\"></span>\n" +
            "    </div>\n" +
            "    <div id=\"resultsBody\" class=\"results-body\">\n" +
            "      <div class=\"empty-state\">Enter search criteria above to find items</div>\n" +
            "    </div>\n" +
            "  </div>";
  }

}
