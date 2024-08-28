package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.model.JsonString;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageClient;
import org.hl7.fhir.utilities.npm.PackageInfo;
import org.hl7.fhir.utilities.npm.PackageServer;

import com.google.gson.JsonPrimitive;

public class PastPackageFixerUpperer {

  private PackageClient client;
  private List<PackageInfo> evlist;

  public static void main(String[] args) throws FileNotFoundException, IOException {
    PastPackageFixerUpperer pp = new PastPackageFixerUpperer();
    pp.client = new PackageClient(new PackageServer("http://packages2.fhir.org/packages"));
    pp.execute(new File("/Users/grahamegrieve/web/hl7.org/fhir/"));
  }

  private void execute(File dir) throws FileNotFoundException, IOException {
    for (File f : dir.listFiles()) {
      if (f.isDirectory()) {
        execute(f);
      } else if (f.getName().endsWith(".tgz")) {
        try {
          NpmPackage npm = NpmPackage.fromPackage(new FileInputStream(f));
          if (checkPackage(f.getAbsolutePath(), npm)) {
            try {
              npm.save(new FileOutputStream(f));           
            } catch (Exception e) {
              System.out.println("Error writing "+f.getAbsolutePath()+": "+e.getMessage());
              e.printStackTrace();
            }
          }          
        } catch (Exception e) {
          System.out.println("Error reading "+f.getAbsolutePath()+": "+e.getMessage());
          e.printStackTrace();
        }
      }
    }    
  }

  private boolean checkPackage(String path, NpmPackage npm) throws IOException {
    if (!npm.getNpm().has("dependencies")) {
      return false;
    }
    for (JsonProperty dep : npm.getNpm().getJsonObject("dependencies").getProperties()) {

      String id = dep.getName();
      String ver = dep.getValue().asString();
      if (("current".equals(ver) || ver.contains("-cibuild")) && "hl7.fhir.uv.extensions".equals(id)) {
        String nver = evForDate(toInstant(npm.dateAsDate()));
        System.out.println(path+" depends on "+ver+" for "+id+" published on "+npm.date()+" so extensions version is "+nver+" - fixed");
        dep.setValue(new JsonString(nver));
        return true;
      } 
      if ("current".equals(ver) && "hl7.fhir.uv.tools".equals(id)) {
        String nver = "0.2.0";
        System.out.println(path+" depends on current for "+id+". fixed");
        dep.setValue(new JsonString(nver));
        return true;
      } 
      if ("current".equals(ver) || ver.contains("-cibuild")) {
        System.out.println(npm.vid()+" depends on "+id+"#"+ver);
//        dep.setValue(new JsonString(nver));
//        return true;
      } 
//      if ("current".equals(ver) && "hl7.fhir.uv.tools".equals(id)) {
//        System.out.println(path+" depends on current for "+id);
//      } 
    }
    return false;
  }

  private Instant toInstant(Date d) {
    return d.toInstant();
  }

  private String evForDate(Instant d) throws IOException {
    if (evlist == null) {
      evlist = client.getVersions("hl7.fhir.uv.extensions");
    }
    for (PackageInfo p : evlist) {
      if (p.getDate().isAfter(d)) {
        return p.getVersion();
      }
    }
    return null;
  }

}
