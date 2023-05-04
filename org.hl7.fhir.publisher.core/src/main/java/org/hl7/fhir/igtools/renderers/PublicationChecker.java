package org.hl7.fhir.igtools.renderers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.igtools.web.PublicationProcess.PublicationProcessMode;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.StringPair;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;


public class PublicationChecker {

  private String folder; // the root folder being built
  private String historyPage;
  private MarkDownProcessor mdEngine;

  public PublicationChecker(String folder, String historyPage, MarkDownProcessor markdownEngine) {
    super();
    this.folder = folder;
    this.historyPage = historyPage;
    this.mdEngine = markdownEngine;
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
    List<String> messages = new ArrayList<>();
    List<StringPair> summary = new ArrayList<>();
    checkFolder(messages, summary);
    StringBuilder bs = new StringBuilder();
    if (summary.size() > 0) {
      bs.append("<table class=\"grid\">\r\n");
      for (StringPair p : summary) {
        if ("descmd".equals(p.getName())) {
          bs.append(" <tr><td>"+Utilities.escapeXml(p.getName())+"</d><td>"+p.getValue()+"</td></tr>\r\n");
        } else {
          bs.append(" <tr><td>"+Utilities.escapeXml(p.getName())+"</d><td>"+Utilities.escapeXml(p.getValue())+"</td></tr>\r\n");
        }
      }      
      bs.append("</table>\r\n");
    }
    if (messages.size() == 0) {
      return bs.toString()+"No Messages found - all good";
    } else if (messages.size() == 1) {
      return bs.toString()+messages.get(0);
    } else {
      StringBuilder b = new StringBuilder();
      b.append("<ul>");
      for (String s : messages) {
        b.append("<li>");
        b.append(s);
        b.append("</li>\r\n");
      }
      b.append("</ul>\r\n");
      return bs.toString()+b.toString();
    }
  }

  private void checkFolder(List<String> messages, List<StringPair> summary) throws IOException {
    check(messages, !exists("package-list.json"), "The file package-list.json should not exist in the root folder"+mkError());
    check(messages, !exists("output", "package-list.json"), "The file package-list.json should not exist in generated output"+mkError());
    if (check(messages, exists("output", "package.tgz"), "No output package found - can't check publication details"+mkWarning())) {
      NpmPackage npm = null;
      try {
        npm = NpmPackage.fromPackage(new FileInputStream(Utilities.path(folder, "output", "package.tgz")));
      } catch (Exception e) {
        check(messages, false, "Error reading package: "+e.getMessage()+mkError());
      }
      if (npm != null) {
        checkPackage(messages, npm);  
        String dst = determineDestination(npm);
        PackageList pl = null;
        try {
          pl = readPackageList(dst);
        } catch (Exception e) {
          if (e.getMessage() != null && e.getMessage().contains("404")) {
            messages.add("<span title=\""+Utilities.escapeXml(e.getMessage())+"\">This IG has never been published</span>"+mkInfo());
          } else {            
            check(messages, false, "Error fetching package-list from "+dst+": "+e.getMessage()+mkError());
          }
        }
        checkExistingPublication(messages, npm, pl);
        if (check(messages, exists("publication-request.json"), "No publication request found"+mkInfo())) {
          checkPublicationRequest(messages, npm, pl, summary);
        } 
      }
    }
  }

  private void checkPackage(List<String> messages, NpmPackage npm) {
    if (check(messages, !"current".equals(npm.version()), "The version of the IG is 'current' which is not valid. It should have a version X.Y.z-cibuild"+mkError())) {
      check(messages, VersionUtilities.isSemVer(npm.version()), "Version '"+npm.version()+"' does not conform to semver rules"+mkError());
    }
    check(messages, Utilities.pathURL(npm.canonical(), "history.html").equals(historyPage), "History Page '"+Utilities.escapeXml(historyPage)+"' is wrong (ig.json#paths/history) - must be '"+
       Utilities.escapeXml(Utilities.pathURL(npm.canonical(), "history.html"))+"'"+mkError());
  }

  private void checkExistingPublication(List<String> messages, NpmPackage npm, PackageList pl) {
    if (pl != null) {
      check(messages, npm.name().equals(pl.pid()), "Package ID mismatch. This package is "+npm.name()+" but the website has "+pl.pid()+mkError());
      check(messages, npm.canonical().equals(pl.canonical()), "Package canonical mismatch. This package canonical is "+npm.canonical()+" but the website has "+pl.canonical()+mkError());
      check(messages, !hasVersion(pl, npm.version()), "Version "+npm.version()+" has already been published"+mkWarning());
    } else {
      check(messages, npm.version().startsWith("0.1") || npm.version().contains("-"), "This IG has never been published, so the version should start with '0.' or include a patch version e.g. '-ballot'"+mkWarning());
    }  
  }

  private void checkPublicationRequest(List<String> messages, NpmPackage npm, PackageList pl, List<StringPair> summary) throws IOException {
    JsonObject pr = null;
    try {
      pr = JsonParser.parseObjectFromFile(Utilities.path(folder, "publication-request.json"));
    } catch (Exception e) {
      check(messages, false, "Error parsing publication-request.json: "+e.getMessage()+mkError());
      return;
    }    
    if (check(messages, pr.has("package-id"), "No package id found in publication request (required for cross-check)"+mkError())) {
      if (check(messages, npm.name().equals(pr.asString("package-id")), "Publication Request is for '"+pr.asString("package-id")+"' but package is "+npm.name()+mkError())) {
        summary.add(new StringPair("package-id", pr.asString("package-id")));
      }
    }
    if (check(messages, pr.has("version"), "No publication request version found"+mkError())) {
      String v = pr.asString("version");
      if (check(messages, npm.version().equals(v), "Publication Request is for v'"+pr.asString("version")+"' but package version is v"+npm.version()+mkError())) {
        summary.add(new StringPair("version", pr.asString("version")));        
      }
      if (pl != null) {
        PackageListEntry plv = getVersionObject(v, pl);
        if (!check(messages, plv == null, "Publication Requet is for version v"+v+" which is already published"+mkError())) {
          summary.clear();
          return;          
        }
        String cv = getLatestVersion(pl);
        check(messages, cv == null || VersionUtilities.isThisOrLater(cv, v), "Proposed version v"+v+" is older than already published version v"+cv+mkError());
      }
    }
    if (check(messages, pr.has("path"), "No publication request path found"+mkError())) {
      if (check(messages, pr.asString("path").startsWith(npm.canonical()), "Proposed path for this publication does not start with the canonical URL ("+pr.asString("path")+" vs "+npm.canonical() +")"+mkError())) {
        summary.add(new StringPair("path", pr.asString("path")));                        
      }
    }
    PublicationProcessMode mode = PublicationProcessMode.fromCode(pr.asString("mode"));
    if (mode != PublicationProcessMode.WORKING) {
      if (check(messages, !npm.version().contains("-"), "This release is labelled as a "+mode.toCode()+", so should not have a patch version ("+npm.version() +")"+mkWarning())) {
        summary.add(new StringPair("mode", mode.toCode()));        
      }
    } else {
      if (check(messages, npm.version().contains("-"), "This release is labelled as a milestone or technical correction, so should have a patch version ("+npm.version() +")"+mkWarning())) {
        summary.add(new StringPair("milestone", pr.asString("milestone")));                
      }
    }
    if (check(messages, pr.has("status"), "No publication request status found"+mkError())) {
      if (check(messages, isValidStatus(pr.asString("status")), "Proposed status for this publication is not valid (valid values: release|trial-use|update|qa-preview|ballot|draft|normative+trial-use|normative|informative)"+mkError())) {
        summary.add(new StringPair("status", pr.asString("status")));                        
      }
    }
    if (mode == PublicationProcessMode.TECHNICAL_CORRECTION) {
      if (check(messages, pl != null, "Can't publish a technical correction when nothing is published yet."+mkWarning())) {
        PackageListEntry cv = getCurrentPublication(pl);
        check(messages, cv != null, "TCan't publish a technical correction when there's no current publication."+mkWarning());
      }
    }
    if (check(messages, pr.has("sequence"), "No publication request sequence found (sequence is e.g. R1, and groups all the pre-publications together. if you don't have a lifecycle like that, just use 'Releases' or 'Publications')"+mkError())) {
      if (pl != null) {
        String seq = getCurrentSequence(pl);
        if (pr.asString("sequence").equals(seq)) {
          PackageListEntry lv = getLastVersionForSequence(pl, seq); 
          check(messages, mode != PublicationProcessMode.WORKING || lv.current(), "This release is labelled as a working release in the sequence '"+seq+"'. This is an unexpected workflow - check that the sequence really is correct."+mkWarning());
        } else if (check(messages, mode != PublicationProcessMode.TECHNICAL_CORRECTION, "Technical Corrections must happen in the scope of the current sequence ('"+seq+"', not '"+pr.asString("sequence")+"'."+mkWarning())) {
          PackageListEntry ls = getLastVersionForSequence(pl, pr.asString("sequence"));
          check(messages, ls == null || !ls.current(), "The sequence '"+seq+"' has already been closed with a current publication, and a new sequence '"+seq+"' started - is going back to '"+pr.asString("sequence")+"' really what's intended?"+mkWarning());
        }        
      }
      summary.add(new StringPair("sequence", pr.asString("sequence")));                        
    }

    if (check(messages, pr.has("desc") || pr.has("descmd") , "No publication request description found"+mkError())) {
      check(messages, pr.has("desc"), "No publication request desc found (it is recommended to provide a shorter desc as well as descmd"+mkWarning());
      if (pr.has("desc")) {
        summary.add(new StringPair("desc", pr.asString("desc")));                        
      }
    }
    if (pr.has("descmd")) {
      String md = pr.asString("descmd");
      if (md.startsWith("@")) {
        File mdFile = new File(Utilities.path(folder, md.substring(1)));
        if (check(messages, mdFile.exists(), "descmd references the file "+md.substring(1)+" but it doesn't exist")) {
          md = TextFile.fileToString(mdFile);
        }
      }
      check(messages, !md.contains("'"), "descmd cannot contain a '"+mkError());
      check(messages, !md.contains("\""), "descmd cannot contain a \""+mkError());
      summary.add(new StringPair("descmd", mdEngine.process(md, "descmd")));                        
    }
    if (pr.has("changes")) {
      summary.add(new StringPair("changes", pr.asString("changes")));                        
      if (check(messages, !Utilities.isAbsoluteUrl(pr.asString("changes")), "Publication request changes must be a relative URL"+mkError())) {
      }
    }
    if (pl == null) {
      if (check(messages, "true".equals(pr.asString("first")), "This IG is not yet published at all, so there must be \"first\" : true in the publication request"+mkError())) {
        summary.add(new StringPair("first", pr.asString("first")));                                
      }

      if (check(messages, pr.has("category"), "No publication request category found (needed for first publication - consult FHIR product director for a value"+mkError())) {
        summary.add(new StringPair("category", pr.asString("category")));                                
      }
      if (check(messages, pr.has("title"), "No publication request title found (needed for first publication)"+mkError())) {
        summary.add(new StringPair("title", pr.asString("title")));                                
      }
      if (check(messages, pr.has("introduction"), "No publication request introduction found (needed for first publication)"+mkError())) {
        summary.add(new StringPair("introduction", pr.asString("introduction")));                                
      }
      if (check(messages, pr.has("ci-build"), "No publication request ci-build found (needed for first publication)"+mkError())) {
        summary.add(new StringPair("ci-build", pr.asString("ci-build")));                                
      }
    } else {
      check(messages, !pr.has("category"), "Publication request category found (not allowed after first publication"+mkError());
      check(messages, !pr.has("title"), "Publication request title found (not allowed after first publication)"+mkError());
      check(messages, !pr.has("introduction"), "Publication request introduction found (not allowed after first publication)"+mkError());
      check(messages, !pr.has("ci-build"), "Publication request ci-build found (not allowed after first publication)"+mkError());
    }
    check(messages, !pr.has("date"), "Cannot specify a date of publication in the request"+mkError());
    check(messages, !pr.has("canonical"), "Cannot specify a canonical in the request"+mkError());
    
  }

  private PackageListEntry getLastVersionForSequence(PackageList pl, String seq) {
    for (PackageListEntry v : pl.versions()) {
      if (seq.equals(v.sequence())) {
        return v;
      }
    }
    return null;
  }

  private PackageListEntry getCurrentPublication(PackageList pl) {
    for (PackageListEntry v : pl.versions()) {
      if (v.current()) {
        return v;
      }
    }
    return null;
  }

  private PackageListEntry getVersionObject(String v, PackageList pl) {
    for (PackageListEntry j : pl.list()) {
      String vl = j.version();
      if (v.equals(vl)) {
        return j;
      }
    }
    return null;
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
    return Utilities.existsInList(str, "release", "trial-use", "update", "qa-preview", "ballot", "draft", "normative+trial-use", "normative", "informative", "public-comment");
  }

  private String getCurrentSequence(PackageList pl) {
    String cv = null;
    String res = null;
    for (PackageListEntry j : pl.list()) {
      String v = j.version();
      if (!Utilities.noString(v) && !"current".equals(v)) {
        if (cv == null || VersionUtilities.isThisOrLater(cv, v)) {
          cv = v;
          res = j.sequence();
        }
      }
    }
    return res;
  }

  private String getLatestVersion(PackageList pl) {
    String cv = null;
    for (PackageListEntry j : pl.list()) {
      String v = j.version();
      if (!Utilities.noString(v) && !"current".equals(v)) {
        if (cv == null || VersionUtilities.isThisOrLater(v, cv)) {
          cv = v;
        }
      }
    }
    return cv;
  }

  private boolean hasVersion(PackageList pl, String version) {
    List<PackageListEntry> list = pl.list();
    for (PackageListEntry o : list) {
      if (version.equals(o.version())) {
        return true;
      }
    }
    return false;
  }
  
  private PackageList readPackageList(String dst) throws IOException {
    return PackageList.fromUrl(Utilities.pathURL(dst, "package-list.json"));
  }

  private String determineDestination(NpmPackage npm) {
    return npm.canonical();
  }

  private boolean check(List<String> messages, boolean test, String error) {
    if (!test) {
      messages.add(error);
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
