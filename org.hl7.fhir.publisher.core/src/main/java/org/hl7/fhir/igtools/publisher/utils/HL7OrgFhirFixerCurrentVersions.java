package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.igtools.web.DownloadBuilder;
import org.hl7.fhir.igtools.web.IGReleaseRedirectionBuilder;
import org.hl7.fhir.igtools.web.IGReleaseVersionUpdater;
import org.hl7.fhir.igtools.web.PublishBoxStatementGenerator;
import org.hl7.fhir.igtools.web.VersionRedirectorGenerator;
import org.hl7.fhir.igtools.web.IGReleaseUpdater.ServerType;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;


public class HL7OrgFhirFixerCurrentVersions {

  public static void main(String[] args) throws IOException {
    File folder = new File("/Users/grahamegrieve/web/www.hl7.org.fhir");
    new HL7OrgFhirFixerCurrentVersions().execute(folder.getAbsolutePath().length(), folder, true);
  }

  int count = 0;
  
  private void execute(int rootLen, File folder, boolean root) throws IOException {
    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        execute(rootLen, f, false);
      } else if ("package-list.json".equals(f.getName())) { 
        checkVersionStatements(f);   
      }
    }
  }

  private void checkVersionStatements(File f) throws IOException {
//    System.out.println("Check "+f.getAbsolutePath());
    PackageList pl = PackageList.fromFile(f);
    String cv = pl.current() != null ? pl.current().version() : null;
    
    for (PackageListEntry pv : pl.list()) {
      if (!pv.cibuild()) {
        String fp = pv.path();
        if (fp.startsWith("http://hl7.org/fhir/")) {
          String rp = fp.substring("http://hl7.org/fhir/".length());
          String path = Utilities.path("/Users/grahamegrieve/web/www.hl7.org.fhir", rp);
          File ff = new File(Utilities.path(path, "index.html"));
          if (!ff.exists()) {
            // System.out.println("Can't find index for "+pv.version()+" @ "+ff.getAbsolutePath());
          } else {
            String index = FileUtilities.fileToString(ff);
            boolean broken = index.contains(". .  For a full list of available versions");
            if (broken) {
              System.out.println(pl.pid()+"#"+pv.version()+" version notice is broken. Fixing  @ "+path);
              count = count + updatePublishBox(pl, pv, path, fp, FileUtilities.getDirectoryForFile(f.getAbsolutePath()), "/Users/grahamegrieve/web/www.hl7.org.fhir", false, new ArrayList<String>(), "http://hl7.org/fhir");              
            } else {
              if (cv != null) {
                if (index.contains("The current version which supersedes this version is")) {
                  String src = index.substring(index.indexOf("The current version which supersedes this version is")+"The current version which supersedes this version is".length()+4);
                  int j = src.indexOf(">");
                  int k = src.indexOf("<");
                  if (j < k) {
                    src = src.substring(j+1, k);
                    if (!cv.equals(src)) {
                      System.out.println(pl.pid()+"#"+pv.version()+" version notice claims the wrong current version ("+src+", should be "+cv+"). Fixing  @ "+path);
                      count = count + updatePublishBox(pl, pv, path, fp, FileUtilities.getDirectoryForFile(f.getAbsolutePath()), "/Users/grahamegrieve/web/www.hl7.org.fhir", false, new ArrayList<String>(), "http://hl7.org/fhir");              

                    }
                  }
                }
              }
              boolean current = index.contains("This is the current published version");
              if (current) {
                if (!pv.current()) {
                  System.out.println(pl.pid()+"#"+pv.version()+" wrongly claims to be current. Fixing  @ "+path);
                  count = count + updatePublishBox(pl, pv, path, fp, FileUtilities.getDirectoryForFile(f.getAbsolutePath()), "/Users/grahamegrieve/web/www.hl7.org.fhir", false, new ArrayList<String>(), "http://hl7.org/fhir");
                }
              } else {
                if (pv.current()) {
                  //                System.out.println("Wrongly claims not to be current  "+pv.version()+": "+ff.getAbsolutePath());
                }
              }
            }
          }
        }
      }
    }
    if (count > 20000) {
      throw new Error("Terminating: "+count+" files changed");
    }
  }
  
  private int updatePublishBox(PackageList pl, PackageListEntry plVer, String destVer, String pathVer, String destination, String rootFolder, boolean current, List<String> ignoreList, String url) throws FileNotFoundException, IOException {
    IGReleaseVersionUpdater igvu = new IGReleaseVersionUpdater(destVer, url, rootFolder, ignoreList, null, plVer.json(), destination);
    String fragment = PublishBoxStatementGenerator.genFragment(pl, plVer, pl.current(), pl.canonical(), current, false);
    System.out.println("  Publish Box Statement: "+fragment);
    igvu.updateStatement(fragment, current ? 0 : 1, pl.milestones());
    System.out.println("  .. "+igvu.getCountTotal()+" files checked, "+igvu.getCountUpdated()+" updated");
    return igvu.getCountUpdated();
  }

  
}