package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.JsonTrackingParser;
import org.hl7.fhir.utilities.npm.NpmPackage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class PackageChecker {


  public static void main(String[] args) throws FileNotFoundException, IOException {
   new PackageChecker().process(new File("C:\\web\\hl7.org\\fhir"));
  }

  private void process(File folder) throws FileNotFoundException, IOException {
    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        process(f);
      } else {
        if (f.getName().endsWith(".tgz")) {
          System.out.println("Package "+f.getAbsolutePath());
          NpmPackage pck = NpmPackage.fromPackage(new FileInputStream(f));
          boolean save = false;
          JsonObject json = JsonTrackingParser.parseJson(pck.load("package", "package.json"));
          JsonArray vl = json.getAsJsonArray("fhir-version-list");
          if (vl != null) {
            json.add("fhirVersions", vl);
            json.remove("fhir-version-list");
            save = true;
          }
          if (save) {
            f.renameTo(new File(Utilities.changeFileExt(f.getAbsolutePath(), ".tgz-old")));
            pck.save(new FileOutputStream(f));
          }
        }
      }
    }    
  }

  public void checkDep(String v, JsonObject deps, String id, String vc, String vf) {
    if (deps.has(id)) {
      deps.remove(id);
      if (v != null && v.startsWith(vc)) {
        deps.addProperty(id, vf);
      }
    } else if (v.startsWith(vc)) {
      deps.addProperty(id, vf);
    }
  }


  private String getSpecialVersion(File folder) {
    if ("C:\\web\\hl7.org\\fhir".equals(folder.getAbsolutePath()))
      return "4.0.1";
    if ("C:\\web\\hl7.org\\fhir\\us\\daf-research\\2017Jan".equals(folder.getAbsolutePath()))
      return "1.8.0";
    if ("C:\\web\\hl7.org\\fhir\\us\\daf-research".equals(folder.getAbsolutePath()))
      return "3.0.2";
    if ("C:\\web\\hl7.org\\fhir\\us\\daf-research\\STU2".equals(folder.getAbsolutePath()))
      return "3.0.2";
    if (folder.getAbsolutePath().endsWith("STU3"))
      return "3.0.2";
    if ("C:\\web\\hl7.org\\fhir\\us\\hai\\STU2".equals(folder.getAbsolutePath()))
      return "4.0.1";
    if ("C:\\web\\hl7.org\\fhir\\us\\sdc\\2016Sep".equals(folder.getAbsolutePath()))
      return "1.6.0";
    if ("C:\\web\\hl7.org\\fhir\\uv\\ecr\\2018Jan".equals(folder.getAbsolutePath()))
      return "3.0.2";    
    return null;
  }

  private String findVersion(File folder) throws IOException {
    File pl = new File(Utilities.path(folder.getAbsolutePath(), "package-list.json"));
    if (pl.exists()) {
      JsonObject json = JsonTrackingParser.parseJson(pl);
      for (JsonElement e : json.getAsJsonArray("list")) {
        JsonObject vo = (JsonObject) e;
        if ((vo.has("current") && vo.get("current").getAsBoolean()) && !"ci-build".equals(vo.get("status").getAsString())) {
          return vo.get("fhirversion").getAsString();
        }
      }
    } else {
      String parent = Utilities.getDirectoryForFile(folder.getAbsolutePath());
      String name = folder.getName();
      pl = new File(Utilities.path(parent, "package-list.json"));
      if (pl.exists()) {
        JsonObject json = JsonTrackingParser.parseJson(pl);
        String canonical = json.get("canonical").getAsString();
        for (JsonElement e : json.getAsJsonArray("list")) {
          JsonObject vo = (JsonObject) e;
          if (vo.get("path").getAsString().equals(Utilities.pathURL(canonical, name))) {
            return vo.get("fhirversion").getAsString();
          }
        }
      }
    }
    return null;
  }

  private String tail(String s) {
    return s.substring(s.lastIndexOf("/")+1);
  }

  
}
