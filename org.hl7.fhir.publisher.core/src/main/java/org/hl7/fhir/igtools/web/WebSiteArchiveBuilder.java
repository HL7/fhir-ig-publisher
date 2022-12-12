package org.hl7.fhir.igtools.web;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.ZipGenerator;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;

public class WebSiteArchiveBuilder {

  private static final String ARCHIVE_FILE_NAME = "_ig-pub-archive.zip";

  public static void main(String[] args) throws Exception {
    new WebSiteArchiveBuilder().start(args[0]);
  }
  
  
  public void start(String folder) throws IOException {
    File f = new File(Utilities.path(folder, "publish.ini"));
    if (f.exists()) {
      scanForAllIGs(folder);
    } else {
      f = new File(Utilities.path(folder, "package-list.json"));
      if (f.exists()) {
        String rootFolder = findRootFolder(folder);
        IniFile ini = new IniFile(Utilities.path(rootFolder, "publish.ini"));
        String url = ini.getStringProperty("website", "url");
        buildArchives(new File(folder), rootFolder, url);
      } else {
        f = new File(Utilities.path(folder, "package.tgz"));
        if (f.exists()) {
          buildArchive(folder, new ArrayList<>());
        } else {
          throw new IOException("Unable to pack "+folder);
        }
      }
    }    
  }


  private String findRootFolder(String folder) throws IOException {
    String dir = folder;
    while (!Utilities.noString(dir)) {
      File f = new File(Utilities.path(dir, "publish.ini"));
      if (f.exists()) {
        return dir;
      }
      dir = Utilities.getDirectoryForFile(folder);
    }
    throw new IOException("Unable to find publish.ini from "+folder);
  }


  public void scanForAllIGs(String folder) throws IOException {
    check(new File(Utilities.path(folder, "publish.ini")).exists(), "publish.ini not found at root");
    IniFile ini = new IniFile(Utilities.path(folder, "publish.ini"));
    String url = ini.getStringProperty("website", "url");
    scanForIGs(new File(folder), folder, url);
  }


  private void scanForIGs(File folder, String rootFolder, String rootUrl) throws FileNotFoundException, IOException {
    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        scanForIGs(f, rootFolder, rootUrl);
      } else if (f.getName().equals("package-list.json")) {
        JsonObject json = (JsonObject) JsonParser.parse(f);
        if (!"hl7.fhir.core".equals(json.asString("package-id"))) {
          buildArchives(folder, rootFolder, rootUrl);
        }
      }
    }    
  }

  public void buildArchives(File folder, String rootFolder, String rootUrl) throws FileNotFoundException, IOException {
    File plf = new File(Utilities.path(folder.getAbsolutePath(), "package-list.json")); 
    check(plf.exists(), "Package list not found at "+plf.getAbsolutePath());
    List<String> exemptions = new ArrayList<>();
    exemptions.add("package-list.json");
    exemptions.add("history.html");
    // todo: feeds
    
    PackageList pl = PackageList.fromFile(plf);
    System.out.println("Build archives for "+pl.pid());
    
    String canonical = pl.canonical();
    check(canonical != null, "canonical is needed");
    for (PackageListEntry v : pl.versions()) {
      String version = v.version();
      String path = v.path();
      check(path != null, "path is needed for "+version);
      String local = v.determineLocalPath(rootUrl, rootFolder);
      if (local == null) {
        System.out.println(" Version "+version+" at "+path+" did not have a resolveable path"); // we ignore it        
      } else {
        File lf = new File(local);
        check(lf.exists() && lf.isDirectory(), "Local Path '"+lf.getAbsolutePath()+"' for version "+version+" not found");
        if (path.startsWith(pl.canonical())) {
          exemptions.add(lf.getName());
        }
        buildArchive(lf.getAbsolutePath(), new ArrayList<>());          
      }
    }
    for (File f : folder.listFiles()) {
      if (isRedirectFolder(f)) {
        exemptions.add(f.getName());
      }
    }
    buildArchive(folder.getAbsolutePath(), exemptions);          
  }
    
  private boolean isRedirectFolder(File f) {
    if (f.isDirectory()) {
      for (File c : f.listFiles()) {
        if (!c.getName().equals("web.config")) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  private void check(boolean b, String msg) {
    if (!b) {
      throw new FHIRException(msg);
    }    
  }

  public void buildArchive(String folder, List<String> exemptions) throws FileNotFoundException, IOException {
    String war = Utilities.path(folder, ARCHIVE_FILE_NAME);
    if (new File(war).exists()) {
      System.out.println(" "+folder+": already archived");
      return;
    }
    ZipGenerator zip = new ZipGenerator(war);
    System.out.println("Produce Web Archive for "+folder+": "+addFolderToZip(zip, new File(folder), folder.length()+1, exemptions)+" files");
    zip.close();
  }

  private int addFolderToZip(ZipGenerator zip, File folder, int offset, List<String> exemptions) throws FileNotFoundException, IOException {
    int c = 0;
    for (File f : folder.listFiles()) {
      if (!exemptions.contains(f.getName())) {
        if (f.isDirectory()) {
          c = c + addFolderToZip(zip, f, offset, new ArrayList<>()); // no exemptions in sub-directories 
        } else if (!f.getName().equals(ARCHIVE_FILE_NAME)) {
          zip.addBytes(f.getAbsolutePath().substring(offset), TextFile.fileToBytes(f), false);
          c++;
        }
      }
    }
    return c;
  }

  private boolean isExemptFile(String name) {
    if (Utilities.existsInList(name, "package-list.json", "package-feed.xml", "publication-feed.xml", "history.html")) {
      return true;
    }
    return false;
  }
}
