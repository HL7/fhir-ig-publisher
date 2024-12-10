package org.hl7.fhir.igtools.publisher;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.fhirpath.ExpressionNode.CollectionStatus;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine.IEvaluationContext.FunctionDefinition;
import org.hl7.fhir.r5.fhirpath.FHIRPathUtilityClasses.FunctionDetails;
import org.hl7.fhir.r5.fhirpath.TypeDetails;
import org.hl7.fhir.r5.liquid.BaseCSVWrapper;
import org.hl7.fhir.r5.liquid.LiquidEngine;
import org.hl7.fhir.r5.liquid.LiquidEngine.LiquidDocument;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.utilities.CSVReader;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.JsonException;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;

public class TestDataFactory {

  public static class DataTable extends Base {
    List<String> columns = new ArrayList<String>();
    List<List<String>> rows = new ArrayList<List<String>>();
    
    @Override
    public String fhirType() {
      return "DataTable";
    }
    @Override
    public String getIdBase() {
      return null;
    }
    @Override
    public void setIdBase(String value) {
      throw new Error("Readonly");
    }
    @Override
    public Base copy() {
      return this;
    }
    
    @Override
    public FhirPublication getFHIRPublicationVersion() {
      return FhirPublication.R5;
    }

    public String cell(int row, String col) {
      if (row >= 0 && row < rows.size()) {
        List<String> r = rows.get(row);
        int c = -1;
        if (Utilities.isInteger(col)) {
          c = Utilities.parseInt(col, -1);
        } else {
          c = columns.indexOf(col);
        }
        if (c > -1 && c  < r.size()) {
          return r.get(c);
        }
      }
      return null;
    }
  }
    
  public static class DataLookupFunction extends FunctionDefinition {

    @Override
    public String name() {
      return "cell";
    }

    @Override
    public FunctionDetails details() {
      return new FunctionDetails("Lookup a data element", 2, 2);
    }

    @Override
    public TypeDetails check(FHIRPathEngine engine, Object appContext, TypeDetails focus, List<TypeDetails> parameters) {
      return new TypeDetails(CollectionStatus.SINGLETON, "string");
    }

    @Override
    public List<Base> execute(FHIRPathEngine engine, Object appContext, List<Base> focus, List<List<Base>> parameters) {
      int row = Utilities.parseInt(parameters.get(0).get(0).primitiveValue(), 0);
      String col = parameters.get(1).get(0).primitiveValue();
      DataTable dt = (DataTable) focus.get(0);
      
      List<Base> res = new ArrayList<Base>();
      String s = dt.cell(row, col);
      if (!Utilities.noString(s)) {
        res.add(new StringType(s));
      }
      return res;
    }
    
  }
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
    DataTable dt = loadData(Utilities.path(rootFolder, ini.getStringProperty("factory", "data")));
    liquid.getVars().clear();
    if (ini.hasSection("tables")) {
      for (String n : ini.getPropertyNames("tables")) {
        liquid.getVars().put(n, loadData(Utilities.path(rootFolder, ini.getStringProperty("tables", n))));
      }
    }
    String format = ini.getStringProperty("factory", "format");
    if ("bundle".equals(ini.getStringProperty("factory", "mode"))) {
      byte[] data = runBundle(template, dt.columns, dt.rows, format);
      TextFile.bytesToFile(data, Utilities.path(rootFolder, ini.getStringProperty("factory", "output"), ini.getStringProperty("factory", "filename")));
    } else {
      for (List<String> row : dt.rows) { 
        byte[] data = runInstance(template, dt.columns, row, format);
        TextFile.bytesToFile(data, Utilities.path(rootFolder, ini.getStringProperty("factory", "output"), 
            getFileName(ini.getStringProperty("factory", "filename"), dt.columns, row)));
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
    if ("json".equals(format)) {
      JsonObject j = JsonParser.parseObject(cnt, true);
      return JsonParser.composeBytes(j, true);
    } else {
      throw new Error("Format "+format+" not supported at this time.");
    }
  }

  private DataTable loadData(String path) throws FHIRException, IOException {
    DataTable dt = new DataTable();
    CSVReader csv = new CSVReader(new FileInputStream(path));
    dt.columns.add("counter");
    for (String n : csv.parseLine()) {
      dt.columns.add(n);
    }
    int t = dt.columns.size();
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
      dt.rows.add(values);
    }
    return dt;
  }
}
