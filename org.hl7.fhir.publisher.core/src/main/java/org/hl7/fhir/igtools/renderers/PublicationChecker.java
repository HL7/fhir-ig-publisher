package org.hl7.fhir.igtools.renderers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.JsonTrackingParser;
import org.hl7.fhir.utilities.json.JsonUtilities;
import org.hl7.fhir.utilities.npm.NpmPackage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class PublicationChecker {

  private String folder; // the root folder being built
  private String historyPage;

  public PublicationChecker(String folder, String historyPage) {
    super();
    this.folder = folder;
    this.historyPage = historyPage;
  }
  
  /**
   * returns html for the qa page 
   * @param string 
   * @param c 
   * @param errors2 
   * 
   * @return
   * @throws IOException 
   */
  public String check() throws IOException {
    List<String> errors = new ArrayList<>();
    checkFolder(errors);
    if (errors.size() == 0) {
      return "No Information found";
    } else if (errors.size() == 1) {
      return errors.get(0);
    } else {
      StringBuilder b = new StringBuilder();
      b.append("<ul>");
      for (String s : errors) {
        b.append("<li>");
        b.append(s);
        b.append("</li>\r\n");
      }
      b.append("</ul>\r\n");
      return b.toString();
    }
  }

  private void checkFolder(List<String> errors) throws IOException {
    check(errors, !exists("package-list.json"), "The file package-list.json should not exist in the root folder"+mkError());
    check(errors, !exists("output", "package-list.json"), "The file package-list.json should not exist in generated output"+mkError());
    if (check(errors, exists("output", "package.tgz"), "No output package found - can't check publication details"+mkWarning())) {
      NpmPackage npm = null;
      try {
        npm = NpmPackage.fromPackage(new FileInputStream(Utilities.path(folder, "output", "package.tgz")));
      } catch (Exception e) {
        check(errors, false, "Error reading package: "+e.getMessage()+mkError());
      }
      if (npm != null) {
        checkPackage(errors, npm);  
        String dst = determineDestination(npm);
        JsonObject pl = null;
        try {
          pl = readPackageList(dst);
        } catch (Exception e) {
          if (e.getMessage().contains("404")) {
            errors.add("<span title=\""+Utilities.escapeXml(e.getMessage())+"\">This IG has never been published</span>"+mkInfo());
          } else {            
            check(errors, false, "Error fetching package-list from "+dst+": "+e.getMessage()+mkError());
          }
        }
        checkExistingPublication(errors, npm, pl);
        if (check(errors, exists("publication-request.json"), "No publication request found"+mkInfo())) {
          checkPublicationRequest(errors, npm, pl);
        } 
      }
    }
  }

  private void checkPackage(List<String> errors, NpmPackage npm) {
    if (check(errors, !"current".equals(npm.version()), "The version of the IG is 'current' which is not valid. It should have a version X.Y.z-cibuild"+mkError())) {
      check(errors, VersionUtilities.isSemVer(npm.version()), "Version '"+npm.version()+"' does not conform to semver rules"+mkError());
    }
    check(errors, Utilities.pathURL(npm.canonical(), "history.html").equals(historyPage), "History Page '"+Utilities.escapeXml(historyPage)+"' is wrong (ig.json#paths/history) - must be '"+
       Utilities.escapeXml(Utilities.pathURL(npm.canonical(), "history.html"))+"'"+mkError());
  }

  private void checkExistingPublication(List<String> errors, NpmPackage npm, JsonObject pl) {
    if (pl != null) {
      check(errors, npm.name().equals(JsonUtilities.str(pl, "package-id")), "Package ID mismatch. This package is "+npm.name()+" but the website has "+JsonUtilities.str(pl, "package-id")+mkError());
      check(errors, npm.canonical().equals(JsonUtilities.str(pl, "canonical")), "Package canonical mismatch. This package canonical is "+npm.canonical()+" but the website has "+JsonUtilities.str(pl, "canonical")+mkError());
      check(errors, !hasVersion(pl, npm.version()), "Version "+npm.version()+" has already been published"+mkWarning());
    } else {
      check(errors, npm.version().startsWith("0.1"), "This IG has never been published, so the version should start with 0."+mkWarning());
    }  
  }

  private void checkPublicationRequest(List<String> errors, NpmPackage npm, JsonObject pl) {
    JsonObject pr = null;
    try {
      pr = JsonTrackingParser.parseJsonFile(Utilities.path(folder, "publication-request.json"));
    } catch (Exception e) {
      check(errors, false, "Error parsing publication-request.json: "+e.getMessage()+mkError());
      return;
    }
    if (pr.has("version") && pr.has("path") && pr.has("status") && pr.has("sequence")) {
      errors.add("A publication request exists to post this version (v"+JsonUtilities.str(pr, "version")+") to <code>"+JsonUtilities.str(pr, "path")+
      "</code> with status <code>"+JsonUtilities.str(pr, "status")+"</code> in sequence <code>"+JsonUtilities.str(pr, "sequence")+"</code>"+mkInfo());
    }
    
    if (check(errors, pr.has("package-id"), "No package id found in publication request (required for cross-check)"+mkError())) {
      check(errors, npm.name().equals(JsonUtilities.str(pr, "package-id")), "Publication Request is for '"+JsonUtilities.str(pr, "package-id")+"' but package is "+npm.name()+mkError());
    }
    if (check(errors, pr.has("version"), "No publication request version found"+mkError())) {
      String v = JsonUtilities.str(pr, "version");
      if (check(errors, npm.version().equals(v), "Publication Request is for v'"+JsonUtilities.str(pr, "version")+"' but package version is v"+npm.version()+mkError()));
      if (pl != null) {
        String cv = getLatestVersion(pl);
        check(errors, cv == null || VersionUtilities.isThisOrLater(cv, v), "Proposed version v"+v+" is older than already published version v"+cv+mkError());
      }
    }
    if (check(errors, pr.has("path"), "No publication request path found"+mkError())) {
      check(errors, JsonUtilities.str(pr, "path").startsWith(npm.canonical()), "Proposed path for this publication does not start with the canonical URL ("+JsonUtilities.str(pr, "path")+" vs "+npm.canonical() +")"+mkError());
    }
    if (check(errors, pr.has("status"), "No publication request status found"+mkError())) {
      check(errors, isValidStatus(JsonUtilities.str(pr, "status")), "Proposed status for this publication is not valid (valid values: release|trial-use|update|qa-preview|ballot|draft|normative+trial-use|normative|informative)"+mkError());
    }
    if (check(errors, pr.has("sequence"), "No publication request sequence found (sequence is e.g. R1, and groups all the pre-publications together. if you don't have a lifecycle like that, just use 'Releases' or 'Publications')"+mkError())) {
      if (pl != null) {
        String seq = getCurrentSequence(pl);
        check(errors, JsonUtilities.str(pr, "sequence").equals(seq), "This publication will finish the sequence '"+seq+"' and start a new sequence '"+JsonUtilities.str(pr, "sequence")+"'"+mkInfo());
      }
    }

    if (check(errors, pr.has("desc") || pr.has("descmd") , "No publication request description found"+mkError())) {
      check(errors, pr.has("desc"), "No publication request desc found (it is recommended to provide a shorter desc as well as descmd"+mkWarning());
    }
    if (pr.has("descmd")) {
      String md = JsonUtilities.str(pr, "descmd");
      check(errors, !md.contains("'"), "descmd cannot contain a '"+mkError());
      check(errors, !md.contains("\""), "descmd cannot contain a \""+mkError());
    }
    if (pr.has("changes")) {
      check(errors, !Utilities.isAbsoluteUrl(JsonUtilities.str(pr, "changes")), "Publication request changes must be a relative URL"+mkError());
    }
    if (pl == null) {
      check(errors, pr.has("category"), "No publication request category found (needed for first publication - consult FHIR product director for a value"+mkError());
      check(errors, pr.has("title"), "No publication request title found (needed for first publication)"+mkError());
      check(errors, pr.has("introduction"), "No publication request introduction found (needed for first publication)"+mkError());
      check(errors, pr.has("ci-build"), "No publication request ci-build found (needed for first publication)"+mkError());
    }
    check(errors, !pr.has("date"), "Cannot specify a date of publication in the request"+mkError());
    check(errors, !pr.has("canonical"), "Cannot specify a canonical in the request"+mkError());
    
    
  }

  private String mkError() {
    return " <img src=\"icon-error.gif\" height=\"16px\" width=\"16px\"/>";
  }

  private String mkWarning() {
    return " <img src=\"icon-warning.png\" height=\"16px\" width=\"16px\"/>";
  }

  private String mkInfo() {
    return " <img src=\"information.png\" height=\"16px\" width=\"16px\"/>";
  }

  private boolean isValidStatus(String str) {
    return Utilities.existsInList(str, "release", "trial-use", "update", "qa-preview", "ballot", "draft", "normative+trial-use", "normative", "informative");
  }

  private String getCurrentSequence(JsonObject pl) {
    String cv = null;
    String res = null;
    for (JsonObject j : JsonUtilities.objects(pl, "list")) {
      String v = JsonUtilities.str(j, "version");
      if (!Utilities.noString(v)) {
        if (cv == null || VersionUtilities.isThisOrLater(v, cv)) {
          cv = v;
          res = JsonUtilities.str(j, "sequence");
        }
      }
    }
    return res;
  }

  private String getLatestVersion(JsonObject pl) {
    String cv = null;
    for (JsonObject j : JsonUtilities.objects(pl, "list")) {
      String v = JsonUtilities.str(j, "version");
      if (!Utilities.noString(v)) {
        if (cv == null || VersionUtilities.isThisOrLater(v, cv)) {
          cv = v;
        }
      }
    }
    return cv;
  }

  private boolean hasVersion(JsonObject pl, String version) {
    JsonArray list = pl.getAsJsonArray("list");
    if (list != null) {
      for (JsonElement e : list) {
        JsonObject o = (JsonObject) e;
        if (o.has("version") && o.get("version").getAsString().equals(version)) {
          return true;
        }
      }
    }
    return false;
  }
  
  private JsonObject readPackageList(String dst) throws IOException {
    return JsonTrackingParser.fetchJson(Utilities.pathURL(dst, "package-list.json"));
  }

  private String determineDestination(NpmPackage npm) {
    return npm.canonical();
  }

  private boolean check(List<String> errors, boolean test, String error) {
    if (!test) {
      errors.add(error);
    }
    return test;
  }

  private boolean exists(String... parts) throws IOException {
    List<String> p = new ArrayList<>();
    p.add(folder);
    for (String s : parts) {
      p.add(s);
    }
    File f = new File(Utilities.path(p.toArray(new String[] {})));
    return f.exists();
  }
  
}
