package org.hl7.fhir.igtools.publisher;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.utils.BaseCSVWrapper;
import org.hl7.fhir.r5.utils.LiquidEngine;
import org.hl7.fhir.r5.utils.LiquidEngine.LiquidDocument;
import org.hl7.fhir.utilities.CSVReader;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.JsonException;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;

public class TestDataFactory {

  private String rootFolder;
  private LiquidEngine liquid;
  private IniFile ini;
  
  protected TestDataFactory(String rootFolder, String iniFile, LiquidEngine liquid) throws IOException {
    super();
    this.rootFolder = rootFolder;
    ini = new IniFile(Utilities.path(rootFolder, iniFile));
    this.liquid = liquid;
  }
  
  public String getName() {
    return ini.getStringProperty("factory", "name");
  }
  
  public void execute() throws FHIRException, IOException {
    try {
    LiquidDocument template = liquid.parse(TextFile.fileToString(Utilities.path(rootFolder, ini.getStringProperty("factory", "liquid"))), "liquid");
    List<String> columns = new ArrayList<String>();
    List<List<String>> rows = new ArrayList<List<String>>();
    loadData(columns, rows, Utilities.path(rootFolder, ini.getStringProperty("factory", "data")));
    String format = ini.getStringProperty("factory", "format");
    if ("bundle".equals(ini.getStringProperty("factory", "mode"))) {
      byte[] data = runBundle(template, columns, rows, format);
      TextFile.bytesToFile(data, Utilities.path(rootFolder, ini.getStringProperty("factory", "output"), ini.getStringProperty("factory", "filename")));
    } else {
      for (List<String> row : rows) { 
        byte[] data = runInstance(template, columns, row, format);
        TextFile.bytesToFile(data, Utilities.path(rootFolder, ini.getStringProperty("factory", "output"), 
            getFileName(ini.getStringProperty("factory", "filename"), columns, row)));
      }
    }
    } catch (Exception e) {
      System.out.println("Error running test case '"+getName()+"': "+e.getMessage());
      e.printStackTrace();
      throw e;
    }
  }

  private String getFileName(String name, List<String> columns, List<String> values) {
    for (int i = 0; i < columns.size(); i++) {
      name = name.replace("{{"+columns.get(i)+"}}", values.get(i));
    }
    return name;
  }

  private byte[] runInstance(LiquidDocument template, List<String> columns, List<String> row, String format) throws JsonException, IOException {
    BaseCSVWrapper base = BaseCSVWrapper.forRow(columns, row);
    String cnt = liquid.evaluate(template, base, this).trim();
    if ("json".equals(format)) {
      JsonObject j = JsonParser.parseObject(cnt, true);
      return JsonParser.composeBytes(j, true);
    } else {
      throw new Error("Format "+format+" not supported at this time.");
    }
  }

  private byte[] runBundle(LiquidDocument template, List<String> columns, List<List<String>> rows, String format) throws JsonException, IOException {
    BaseCSVWrapper base = BaseCSVWrapper.forRows(columns, rows);
    String cnt = liquid.evaluate(template, base, this).trim();
    TextFile.stringToFile(cnt, "/Users/grahamegrieve/temp/liquid.out.json");
    if ("json".equals(format)) {
      JsonObject j = JsonParser.parseObject(cnt, true);
      return JsonParser.composeBytes(j, true);
    } else {
      throw new Error("Format "+format+" not supported at this time.");
    }
  }

  private void loadData(List<String> columns, List<List<String>> rows, String path) throws FHIRException, IOException {
    CSVReader csv = new CSVReader(new FileInputStream(path));
    columns.add("counter");
    for (String n : csv.parseLine()) {
      columns.add(n);
    }
    int t = columns.size();
    int c = 0;
    while (csv.ready()) {
      c++;
      List<String> values = new ArrayList<String>();
      values.add(""+c);
      for (String b : csv.parseLine()) {
        values.add(b);
      }
      while (values.size() < t) {
        values.add("");
      }
      while (values.size() > t) {
        values.remove(values.size()-1);
      }
      rows.add(values);
    }
    
  }
}
