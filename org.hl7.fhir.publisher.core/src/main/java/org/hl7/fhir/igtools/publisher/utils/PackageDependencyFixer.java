package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;

public class PackageDependencyFixer {


  public static void main(String[] args) throws FileNotFoundException, IOException {
   new PackageDependencyFixer().process(new File("/Users/grahamegrieve/web/hl7.org/fhir"));
  }

  private void process(File folder) throws FileNotFoundException, IOException {
    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        process(f);
      } else {
        if (f.getName().endsWith(".tgz")) {
          try {
//            System.out.println("Package "+f.getAbsolutePath());
            NpmPackage pck = NpmPackage.fromPackage(new FileInputStream(f));
            boolean save = false;
            JsonObject json = JsonParser.parseObject(pck.load("package", "package.json"));
            JsonObject dep = json.getJsonObject("dependencies");
            List<String> deps = new ArrayList<>();
            if (dep != null) {
              for (JsonProperty p : dep.getProperties()) {
                String ver = p.getValue().asJsonString().getValue();
                deps.add(p.getName()+"#"+ver);
                if ("current".equals(ver) || ver.contains("cibuild")) {
                  System.out.println("Package "+json.asString("name")+"#"+json.asString("version")+" depends on "+p.getName()+"#"+ver);
                }
              }
            }
//            System.out.println("Package "+json.asString("name")+" depends on "+CommaSeparatedStringBuilder.join(",", deps));            
            if (save) {
              f.renameTo(new File(FileUtilities.changeFileExt(f.getAbsolutePath(), ".tgz-old")));
              pck.save(new FileOutputStream(f));
            }
          } catch (Exception e) {
//            System.out.println("Error processing "+f.getAbsolutePath()+": "+e.getMessage());
          }
        }
      }
    }     
  }

  
}
