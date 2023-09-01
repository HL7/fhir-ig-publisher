package org.hl7.fhir.igtools.web;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.JsonException;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;

public class PackageRegistryBuilder {

  private String rootFolder;
  
  public PackageRegistryBuilder(String rootFolder) {
    super();
    this.rootFolder = rootFolder;
  }

  protected File prFile() throws IOException {
    return new File(Utilities.path(rootFolder, "package-registry.json"));
  }

  public void update(String path, PackageList list) throws JsonException, IOException {
    JsonObject j = JsonParser.parseObject(prFile());
    update(j, path+"/package-list.json", list);
    JsonParser.compose(j, new FileOutputStream(prFile()), true);
  }
  
  private void update(JsonObject json, String path, PackageList pl) {
    JsonObject e = null;
    for (JsonObject t : json.forceArray("packages").asJsonObjects()) {
      if (!t.has("path")) {
        throw new IllegalArgumentException("package entry in package-registry.json is missing path entry: " + t.toString());
      }
      if (t.asString("path").equals(path)) {
        e = t;
        break;
      }
    }
    if (e == null) {   
      e = new JsonObject();
      json.forceArray("packages").add(e);
    } else {
      e.clear();
    }
    e.add("path", path);
    e.add("package-id", pl.pid());
    e.add("title", pl.title());
    e.add("canonical", pl.canonical());
    e.add("category", pl.category());
    int o = 0;
    if (pl.ciBuild() != null) {
      e.add("ci-build", pl.ciBuild().path());  
      o = 1;
    }
    e.add("version-count", pl.list().size()-o);
    PackageListEntry ple = pl.latest();
    if (ple != null) {
      JsonObject jv = new JsonObject();
      jv.add("version", ple.version());
      jv.add("date", ple.date());
      jv.add("path", ple.path());
      e.add("latest", jv);
    }
    ple = pl.current();
    if (ple != null) {
      JsonObject jv = new JsonObject();
      jv.add("version", ple.version());
      jv.add("date", ple.date());
      jv.add("path", ple.path());
      e.add("milestone", jv);
    }
  }
  
  public void build() throws IOException {
    System.out.println("Scanning for packages @ "+rootFolder);
    Map<String, PackageList> packages = new HashMap<>();
    scanForPackages(packages, "", new File(rootFolder));
    System.out.println("Found "+packages.size()+" packages");
    
    File p = prFile();
    JsonObject j = p.exists() ? JsonParser.parseObject(prFile()) : new JsonObject();
    
    if (j.has("doco") ) {
      j.add("doco", "This json object lists all the package-list.json files found on this site, "+
           "and for the convenience of pages that use this file to present a website index, some of the key "+
          "information found in the package-lists is also contained in here.");
    }
    for (String path : Utilities.sorted(packages.keySet())) {
      update(j, path,  packages.get(path));
    }
    JsonParser.compose(j, new FileOutputStream(prFile()), true);
    System.out.println("Done. Built "+prFile().getAbsolutePath());
  }

  private void scanForPackages(Map<String, PackageList> packages, String statedPath, File file) throws IOException {
    File p = new File(Utilities.path(file.getAbsolutePath(), "package-list.json"));
    if (p.exists()) {
      JsonObject pl = JsonParser.parseObject(p);
      if (!"hl7.fhir.core".equals(pl.asString("package-id"))) {
        packages.put(Utilities.pathURL(statedPath, "package-list.json"), new PackageList(pl));
      }
      if (!Utilities.noString(statedPath)) {
        return;
      }
    } 
    for (File f : file.listFiles()) {
      if (f.isDirectory()) {
        scanForPackages(packages, Utilities.noString(statedPath) ? f.getName() : statedPath+"/"+f.getName(), f);
      }
    }

  }

}
