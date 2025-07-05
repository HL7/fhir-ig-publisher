package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.JsonException;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;

public class HL7OrgFhirVersionRedirectRemover {

  public static void main(String[] args) throws IOException {
    File folderRoot = new File("/Users/grahamegrieve/web/www.hl7.org.cda");
    new HL7OrgFhirVersionRedirectRemover().execute(folderRoot);
  }

  private int count = 0;
  
  private void execute(File folder) throws IOException {
    executeGo(folder, true);
    System.out.println("Directories removed: "+count);    
  }
  
  private void executeGo(File folder, boolean root) throws IOException {
    for (File f : folder.listFiles()) {
      if (f.getName().contains("|")) {
        FileUtilities.clearDirectory(f.getAbsolutePath());
        f.delete();
        count++;
      } else if (f.isDirectory()) {
        executeGo(f, false);
      } else if (!root && f.getName().equals("package-list.json")) {
        buildRedirect(f);
      }
    }
  }

  private void buildRedirect(File f) throws JsonException, IOException {
    Map<String, String> versions = new HashMap<>();
    
    PackageList pl = PackageList.fromFile(f);
    for (PackageListEntry v : pl.versions()) {
      if (!v.cibuild()) {
        if (v.current()) {
          versions.put(v.version(), pl.canonical());          
          versions.put(VersionUtilities.getMajMin(v.version()), pl.canonical());          
        } else if (v == pl.latest()) {
          versions.put(v.version(), v.path());
        } else if (!v.version().contains("-") || v == pl.latest()) {
          versions.put(v.version(), v.path());
          versions.put(VersionUtilities.getMajMin(v.version()), v.path());          
        }
      }
    }
    
    System.out.println(pl.canonical());
    
    StringBuilder b = new StringBuilder();
    b.append("RewriteEngine On\r\n");
    b.append("\r\n");
    for (String v : Utilities.sorted(versions.keySet())) {
      b.append("RewriteRule ^(.+)\\|"+v.replace(".", "\\.").replace("-", "\\-")+"$ "+versions.get(v)+"/$1 [R=302,L]\r\n");
    }
    b.append("\r\n");
    String fn = Utilities.path(FileUtilities.getDirectoryForFile(f.getAbsolutePath()), ".htaccess");
    FileUtilities.stringToFile(b.toString(), fn);
  }

 
}

