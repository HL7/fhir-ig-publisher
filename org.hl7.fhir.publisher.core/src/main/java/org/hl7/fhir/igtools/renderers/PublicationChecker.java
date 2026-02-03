package org.hl7.fhir.igtools.renderers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.RelatedIG;
import org.hl7.fhir.igtools.web.PublicationProcess.PublicationProcessMode;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.StringPair;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;


public class PublicationChecker {

  private String folder; // the root folder being built
  private String historyPage;
  private MarkDownProcessor mdEngine;
  private String releaseLabel;
  private ImplementationGuide ig;
  private List<RelatedIG> relatedIgs;
  private FilesystemPackageCacheManager pcm;

  public PublicationChecker(String folder, String historyPage, MarkDownProcessor markdownEngine, String releaseLabel, ImplementationGuide ig, List<RelatedIG> relatedIgs) {
    super();
    this.folder = folder;
    this.historyPage = historyPage;
    this.mdEngine = markdownEngine;
    this.releaseLabel = releaseLabel;
    this.ig = ig;
    this.relatedIgs = relatedIgs;
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
    JsonObject pr = checkFolder(messages, summary);
    checkIg(messages, summary);
    if (pr != null) {
      checkRelatedIgs(pr, messages, summary);
    }
    
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

  private void checkRelatedIgs(JsonObject pr, List<String> messages, List<StringPair> summary) throws IOException {
    if (relatedIgs.isEmpty()) {
      summary.add(new StringPair("RelatedIgs", "(None Found)"));
    } else {
      CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
      for (RelatedIG rig : relatedIgs) {
        String rigV = pr.forceObject("related").asString(rig.getCode());
        if (rigV == null) {
          messages.add("No specified Publication version for relatedIG "+rig.getCode()+mkError());
          b.append(rig.getCode()+"=ERROR");
        } else {
          if (pcm == null) {
            pcm = new FilesystemPackageCacheManager.Builder().build();
          }
          NpmPackage npm;
          try {
            npm = pcm.loadPackage(rig.getId(), rigV);
          } catch (Exception e) {
            if (!e.getMessage().toLowerCase().contains("not found")) {
              messages.add("Error looking for "+rig.getId()+"#"+rigV+" for relatedIG  "+rig.getCode()+": "+e.getMessage()+mkError());              
            }
            npm = null;
          }
          if (npm == null) {
            // now, the tricky bit: determining where it will be located.
            try {
              npm = pcm.loadPackage(rig.getId(), "current");
              JsonObject json = JsonParser.parseObject(npm.load("other", "publication-request.json"));
              String location = json.asString("path");
              String version = json.asString("version");
              if (rigV.equals(version)) {
                b.append(rig.getCode()+"="+rigV+" (Yet to be published at '"+location+"')");
              } else {
                b.append(rig.getCode()+"="+rigV+" (Not published, and the proposed publication is a different version: "+version+" instead of "+rigV+")");
                messages.add("The proposed publication for relatedIG  "+rig.getCode()+" is a different version: "+version+" instead of "+rigV+mkError());              
              }
            } catch (Exception e) {
              messages.add("Error looking for "+rig.getId()+"#current for relatedIG  "+rig.getCode()+": "+e.getMessage()+mkError());              
              b.append(rig.getCode()+"="+rigV+" (Yet to be published, and cannot determine location)");                          
            }
          } else {
            b.append(rig.getCode()+"="+rigV+" (Already published at "+npm.getWebLocation()+")");
          }
        }
      }
      summary.add(new StringPair("RelatedIgs", b.toString()));
    }    
  }

  private void checkIg(List<String> messages, List<StringPair> summary) {
    for (ImplementationGuideDependsOnComponent dep : ig.getDependsOn()) {
      if (dep.getVersion() == null) {
        messages.add("Dependency on "+dep.getPackageId()+" has no version"+mkError());
      } else if ("current".equals(dep.getVersion())) {
        messages.add("Dependency on "+dep.getPackageId()+" is to the <code>current</code> version - not allowed"+mkError());        
      }
    }
  }

  private JsonObject checkFolder(List<String> messages, List<StringPair> summary) throws IOException {
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
          if (exceptionCausedByNotFound(e)) {
            messages.add("<span title=\""+Utilities.escapeXml(e.getMessage())+"\">This IG has never been published</span>"+mkInfo());
          } else {            
            check(messages, false, "Error fetching package-list from "+dst+": "+e.getMessage()+mkError());
          }
        }
        checkExistingPublication(messages, npm, pl);
        if (check(messages, exists("publication-request.json"), "No publication request found"+mkInfo())) {
          return checkPublicationRequest(messages, npm, pl, summary);
        } 
      }
    }
    return null;
  }

  protected static boolean exceptionCausedByNotFound(Exception e) {
    if (e.getMessage() != null) {
      if (e.getMessage().contains("404") || e.getMessage().contains("Not Found")) {
        return true;
      }
    }
    if (e.getCause() != null) {
      Throwable cause = e.getCause();
      if (cause.getMessage() != null) {
        if (cause.getMessage().contains("404") || cause.getMessage().contains("Not Found")) {
          return true;
        }
      }
    }
    return false;
  }

  private void checkPackage(List<String> messages, NpmPackage npm) {
    if (check(messages, !"current".equals(npm.version()), "The version of the IG is 'current' which is not valid. It should have a version X.Y.z-cibuild"+mkError())) {
      check(messages, VersionUtilities.isSemVer(npm.version(), false), "Version '"+npm.version()+"' does not conform to semver rules"+mkError());
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

  private JsonObject checkPublicationRequest(List<String> messages, NpmPackage npm, PackageList pl, List<StringPair> summary) throws IOException {
    JsonObject pr = null;
    try {
      pr = JsonParser.parseObjectFromFile(Utilities.path(folder, "publication-request.json"));
    } catch (Exception e) {
      check(messages, false, "Error parsing publication-request.json: "+e.getMessage()+mkError());
      return null;
    }    
    if (pl == null) {
      check(messages, "true".equals(pr.asString("first")), "This appears to be the first publication, so first must be true"+mkError());
    } else {
      check(messages, !"true".equals(pr.asString("first")), "This is not the first publication, so first must not be true"+mkError());
    }

    if (check(messages, pr.has("package-id"), "No package id found in publication request (required for cross-check)"+mkError())) {
      if (check(messages, npm.name().equals(pr.asString("package-id")), "Publication Request is for '"+pr.asString("package-id")+"' but package is "+npm.name()+mkError())) {
        summary.add(new StringPair("package-id", pr.asString("package-id")));
      }
    }
    if (check(messages, pr.has("version"), "No publication request version found"+mkError())) {
      String v = pr.asString("version");
      check(messages, VersionUtilities.isSemVer(v, false), "Publication Request is for v'"+pr.asString("version")+"' but this is not a valid SemVer version"+mkError());
      if (check(messages, npm.version().equals(v), "Publication Request is for v'"+pr.asString("version")+"' but package version is v"+npm.version()+mkError())) {
        summary.add(new StringPair("version", pr.asString("version")));        
      }
      if (pl != null) {
        PackageListEntry plv = getVersionObject(v, pl);
        if (!check(messages, plv == null, "Publication Request is for version v"+v+" which is already published"+mkError())) {
          summary.clear();
          return pr;          
        }
        String cv = getLatestVersion(pl);
        check(messages, cv == null || VersionUtilities.isThisOrLater(cv, v, VersionUtilities.VersionPrecision.MINOR), "Proposed version v"+v+" is older than already published version v"+cv+mkError());
      }
    }
    if (check(messages, pr.has("path"), "No publication request path found"+mkError())) {
      if (check(messages, pr.asString("path").startsWith(npm.canonical()) && !pr.asString("path").equals(npm.canonical()), "Proposed path for this publication does not start with the canonical URL ("+pr.asString("path")+" vs "+npm.canonical() +")"+mkError())) {
        summary.add(new StringPair("path", pr.asString("path")));                        
      }
      boolean exists = false;
      if (pl != null) {
        for (PackageListEntry v : pl.versions()) {
          if (v.path().equals(pr.asString("path"))) {
            exists = true;
          }
        }
        check(messages,  !exists, "A publication already exists at "+pr.asString("path")+mkError());
      }

      if ("milestone".equals(pr.asString("mode"))) {
        check(messages,  pr.asString("path").equals(Utilities.pathURL(npm.canonical(), pr.asString("sequence").replace(" ", ""))) || pr.asString("path").equals(Utilities.pathURL(npm.canonical(), pr.asString("version"))), 
            "Proposed path for this milestone publication should usually be canonical with either sequence or version appended"+mkWarning());        
        if (pl != null) {
          for (PackageListEntry e : pl.list()) {
            String pp = e.path();
            if (pp!= null && pp.equals(pr.asString("path"))) {
              check(messages,  
                  false,
                  "A version of this IG has already been published at "+pp+mkError());
            }
          }
        }
      } else if (pr.has("version")) {
        check(messages,  
            pr.asString("path").equals(Utilities.pathURL(npm.canonical(), pr.asString("version"))) ||
            pr.asString("path").startsWith(Utilities.pathURL(npm.canonical(), pr.asString("version"))+"-") ||
            pr.asString("path").startsWith(Utilities.pathURL(npm.canonical(), pr.asString("sequence"))+"-"),
            "Proposed path for this publication should usually be the canonical with the version or sequence appended and then some kind of label (typically '-snapshot')"+mkWarning());
      }
    }
    PublicationProcessMode mode = null;
    if (check(messages, Utilities.existsInList(pr.asString("mode"), "working", "milestone", "technical-correction"), "The release must have a mode of working, milestone or technical-correction"+mkError())) {
      mode = PublicationProcessMode.fromCode(pr.asString("mode"));
      summary.add(new StringPair("Pub-Mode", mode.toCode()));        
      if (mode != PublicationProcessMode.WORKING) {
        check(messages, !npm.version().contains("-"), "This release is labelled as a "+mode.toCode()+", so should not have a patch version ("+npm.version() +")"+mkWarning()); 
      } else {
        check(messages, npm.version().contains("-"), "This release is not labelled as a milestone or technical correction, so should have a patch version ("+npm.version() +")"+(isHL7(npm) ? mkError() : mkWarning()));
      }
    }
    
    if (check(messages, pr.has("status"), "No publication request status found"+mkError())) {
      if (check(messages, isValidStatus(pr.asString("status")), "Proposed status for this publication is not valid (valid values: release|trial-use|update|preview|ballot|draft|normative+trial-use|normative|informative)"+mkError())) {
        summary.add(new StringPair("status", pr.asString("status")));                        
      }
    }
    summary.add(new StringPair("Release-Label", releaseLabel));
    if (mode == PublicationProcessMode.TECHNICAL_CORRECTION) {
      if (check(messages, pl != null, "Can't publish a technical correction when nothing is published yet."+mkWarning())) {
        PackageListEntry cv = getCurrentPublication(pl);
        check(messages, cv != null, "Can't publish a technical correction when there's no current publication."+mkWarning());
      }
    }
    if (check(messages, pr.has("sequence"), "No publication request sequence found (sequence is e.g. R1, and groups all the pre-publications together. if you don't have a lifecycle like that, just use 'Releases' or 'Publications')"+mkError())) {
      if (pl != null) {
        String seq = getCurrentSequence(pl);
        if (pr.asString("sequence").equals(seq)) {
          PackageListEntry lv = getLastVersionForSequence(pl, seq); 
          check(messages, mode != PublicationProcessMode.WORKING || lv.current() || Utilities.existsInList(lv.status(), "ballot", "draft"),
              "This release is labelled as a working release in the sequence '"+seq+"'. This is an unexpected workflow - check that the sequence really is correct."+mkWarning());
        } else if (check(messages, mode != PublicationProcessMode.TECHNICAL_CORRECTION, "Technical Corrections must happen in the scope of the current sequence ('"+seq+"', not '"+pr.asString("sequence")+"'."+mkWarning())) {
          PackageListEntry ls = getLastVersionForSequence(pl, pr.asString("sequence"));
          check(messages, ls == null || !ls.current(), "The sequence '"+seq+"' has already been closed with a current publication, and a new sequence '"+seq+"' started - is going back to '"+pr.asString("sequence")+"' really what's intended?"+mkWarning());
        }        
        String cs = pl.current() == null ? null : pl.current().sequence();
        Set<String> sl = new HashSet<>();
        for (PackageListEntry ple : pl.list()) {
          if (ple.sequence() != null && !Utilities.existsInList(ple.sequence(), cs, "ci-build")) {
            sl.add("'"+ple.sequence()+"'");
          }
        }  
        summary.add(new StringPair("Sequence (Group)", pr.asString("sequence")+" (current: "+(cs == null ? "n/a" : "'"+cs+"'")+
            ", others = "+(sl.isEmpty() ? "n/a" : CommaSeparatedStringBuilder.join(",", Utilities.sorted(sl)))+")"));        
      } else {
        summary.add(new StringPair("Sequence (Group)", pr.asString("sequence")));
      }
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
          md = FileUtilities.fileToString(mdFile);
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
    return pr;
  }

  private boolean isHL7(NpmPackage npm) {
    return npm.id().startsWith("hl7.");
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
    return Utilities.existsInList(str, "release", "trial-use", "update", "preview", "ballot", "draft", "normative+trial-use", "normative", "informative", "public-comment");
  }

  private String getCurrentSequence(PackageList pl) {
    String cv = null;
    String res = null;
    for (PackageListEntry j : pl.list()) {
      String v = j.version();
      if (!Utilities.noString(v) && !"current".equals(v)) {
        if (cv == null || VersionUtilities.isThisOrLater(cv, v, VersionUtilities.VersionPrecision.MINOR)) {
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
        if (cv == null || VersionUtilities.isThisOrLater(v, cv, VersionUtilities.VersionPrecision.MINOR)) {
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
