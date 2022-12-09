package org.hl7.fhir.igtools.publisher.utils;

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
    File pl = new File(Utilities.path(folder.getAbsolutePath(), "package-list.json")); 
    check(pl.exists(), "Package list not found at "+pl.getAbsolutePath());
    List<String> exemptions = new ArrayList<>();
    exemptions.add("package-list.json");
    exemptions.add("history.html");
    // todo: feeds
    
    JsonObject json = (JsonObject) JsonParser.parse(pl);
    System.out.println("Build archives for "+json.asString("package-id"));
    
    String canonical = json.asString("canonical");
    check(canonical != null, "canonical is needed");
    for (JsonObject v : json.getJsonObjects("list")) {
      String version = v.asString("version");
      if (!"current".equals(version)) {
        String path = v.asString("path");
        check(path != null, "path is needed for "+version);
        if (path.startsWith(canonical+"/")) {
          String local = path.substring(canonical.length()+1);
          check(local != null && !local.contains("/") && !local.contains("\\"), "Format is wrong for local '"+local+"' for version "+version);
          File lf = new File(Utilities.path(folder.getAbsolutePath(), local));
          check(lf.exists() && lf.isDirectory(), "Existence is wrong for local '"+lf.getAbsolutePath()+"' for version "+version);
          exemptions.add(local);
          buildArchive(lf.getAbsolutePath(), new ArrayList<>());          
        } else if (!Utilities.startsWithInList(path, "http://hl7.org/fhir/DSTU2", "http://hl7.org/fhir/2015Sep", "http://hl7.org/fhir/2015May")) {
          // it's somewhere else. So it's not going to be an exemption, but we are going to archive it 
          if (path.startsWith(rootUrl+"/")) {
            String tail = path.substring(rootUrl.length()+1);
            String lfolder = Utilities.path(rootFolder, tail);
            File lf = new File(lfolder);
            check(lf.exists() && lf.isDirectory(), "Existence is wrong for different local '"+lf.getAbsolutePath()+"' for version "+version);
            buildArchive(lf.getAbsolutePath(), new ArrayList<>());          
          } else {
            System.out.println(" Version "+version+" is on a different server at "+path); // we ignore it
          }
        }
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
