package org.hl7.fhir.igtools.publisher.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.Attributes.Name;

import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;

public class IGRegistryScanner {

  private String filename;

  public IGRegistryScanner(String filename) {
    this.filename = filename;
  }

  public static void main(String[] args) throws FileNotFoundException, IOException {
    new IGRegistryScanner(args[0]).execute();

  }

  private void execute() throws FileNotFoundException, IOException {
    JsonObject reg = JsonParser.parseObject(new FileInputStream(filename));
    if (checkReg(reg)) {
      JsonParser.compose(reg, new FileOutputStream(filename), true);
    }
  }

  private boolean checkReg(JsonObject reg) {
    boolean changed = false;
    for (JsonObject ig : reg.forceArray("guides").asJsonObjects()) {
      if (!ig.has("language")) {
        String name = ig.asString("npm-name");
        if (true || name.contains("ihe.formatcode.fhir")) {
          changed = true;
          ig.forceArray("language").add("en");
        } else {
          System.out.println("No history: "+name);
        }
      }
    }
    return changed;
  }

}
