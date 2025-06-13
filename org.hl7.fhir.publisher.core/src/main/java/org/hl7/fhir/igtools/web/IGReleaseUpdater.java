package org.hl7.fhir.igtools.web;

/*-
 * #%L
 * org.hl7.fhir.publisher.core
 * %%
 * Copyright (C) 2014 - 2019 Health Level 7
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_10_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.web.IGRegistryMaintainer.ImplementationGuideEntry;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.model.JsonString;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;


public class IGReleaseUpdater {

  public enum ServerType {
    APACHE, ASP1, ASP2, LITESPEED, CLOUD;

    public static ServerType fromCode(String st) {
      st = st.toLowerCase();
      if (st.equals("asp-old")) {
        return ServerType.ASP1;
      } else if (st.equals("asp-new")) {
        return ServerType.ASP2;
      } else if (st.equals("apache")) {
        return ServerType.APACHE;
      } else if (st.equals("nginx")) {
        return ServerType.APACHE;
      } else if (st.equals("litespeed")) {
        return ServerType.LITESPEED;
      } else if (st.equals("cloud")) {
        return ServerType.CLOUD;
      } else { 
        throw new Error("-server-type "+st+" not known - use ASP or Apache");
      }
    }
  }

  private String folder;
  private String url;
  private String rootFolder;
  private IGRegistryMaintainer reg;
  private ServerType serverType;
  private List<String> ignoreList = new ArrayList<>();
  private File sft;
  private boolean fullUpdate;
  private String historySource;

  public IGReleaseUpdater(String folder, String url, String rootFolder, IGRegistryMaintainer reg, ServerType serverType, List<String> otherSpecs, File sft, boolean full, String historySource) throws IOException {
    this.folder = folder;
    this.url = url;
    this.rootFolder = rootFolder;
    this.fullUpdate = full;
    this.sft = sft;
    this.historySource = historySource;
    if (!"".equals("http://hl7.org/fhir")) { // keep the main spec out of the registry
      this.reg = reg;
    }
    this.serverType = serverType;
    if (new File(Utilities.path(folder, "publish.ini")).exists()) {
      IniFile ini = new IniFile(Utilities.path(folder, "publish.ini"));
      if (ini.getPropertyNames("ig-dirs") != null) {
        for (String s : ini.getPropertyNames("ig-dirs")) {
          this.ignoreList.add(Utilities.path(folder, s));
        }
      }      
    }
    if (otherSpecs != null) {
      this.ignoreList.addAll(otherSpecs);
    }
  }

  public void check(Map<String, IndexMaintainer> indexes, boolean throwError, boolean updateStatements, String templateSrc) throws IOException  {
    List<String> errs = new ArrayList<>(); 
    try {
      String f = Utilities.path(folder, "package-list.json");
      if (!new File(f).exists())
        errs.add("unable to find package-list.json");
      else {
        JsonObject json = JsonParser.parseObjectFromFile(f);
        PackageList pl = new PackageList(json);
        pl.setSource(f);
        String canonical = json.asString("canonical");
        String packageId = json.asString("package-id");
        String realm = Utilities.charCount(packageId, '.') > 1 ? packageId.split("\\.")[2] : null;

        JsonArray list = json.getJsonArray("list");
        JsonObject root = null;
        System.out.println("Update "+folder+" for "+canonical);
        for (JsonElement n : list) {
          JsonObject o = (JsonObject) n;
          if (!o.has("version"))
           throw new Error(folder+" has Version without version");
          if (!o.asString("version").equals("current")) {
            if (o.has("current") && o.asBoolean("current") && o.has("path") && o.asString("path").startsWith(canonical+"/")) {
              root = o;
            }
          }
        }
        boolean save = false;
        ImplementationGuideEntry rc = reg == null ? null : reg.seeIg(json.asString("package-id"), canonical, json.asString("title"), json.asString("category"));
        if (!json.has("category")) {
          if (Utilities.noString(rc.getCategory())) {
            errs.add(f+" has no category value");            
          } else {
            json.add("category", rc.getCategory());
            save = true;
          }
        }
        boolean hasRelease = false;
        List<String> folders = new ArrayList<>();
        for (JsonElement n : list) {
          JsonObject o = (JsonObject) n;
          if (o.asString("version").equals("current")) {
            if (reg != null) {
              reg.seeCiBuild(rc, o.asString("path"), f);
            }
            System.out.println("-- ignoring current build entry");
          } else {
            String v = o.asString("version");
            if (!o.has("path")) {
              System.out.println("-- ignoring version "+v+" as it has no path");
              errs.add("version "+v+" has no path'"); 
            } else {
              String path = o.asString("path");
              String vf = Utilities.path(path.replace(url, rootFolder));
              if (indexes != null && indexes.containsKey(realm)) {
                indexes.get(realm).seeEntry(packageId, json, o);
              }

              if (!o.has("sequence")) {
                throw new Error("No Sequence value for version "+v+" in "+f);
              }
              if (!path.endsWith(".html")) {
                File file = new File(vf);
                if (!(file.exists())) {
                  if (Utilities.isAbsoluteUrl(vf)) {
                    System.out.println("-- ignoring version "+v+" as it appears to be a reference to an external target ('"+path+"')");
                  } else {
                    System.out.println("-- ignoring version "+v+" as path not found ('"+path+"')");
                    errs.add("version "+v+" path "+vf+" not found (canonical = "+canonical+", path = "+path+")");
                  }
                } else if (!file.isDirectory()) {
                  System.out.println("-- ignoring version "+v+" as path is not a folder ('"+path+"')");
                  errs.add("version "+v+" path "+vf+" is not a folder (canonical = "+canonical+", path = "+path+")");
                } else {
                  System.out.println("-- updating version "+v+" in '"+vf+"'");
                  folders.add(vf);
                  save = updateStatement(vf, null, ignoreList, json, o, errs, root, canonical, folder, canonical.equals("http://hl7.org/fhir"), false, list, updateStatements, pl.milestones()) | save;
                }
              } else {
                System.out.println("-- ignoring version "+v+" as it is an invalid path");
              }
              if (o.has("current") && o.asBoolean("current") && o.has("path") && o.asString("path").startsWith(canonical+"/")) {
                root = o;
              }
              if (reg != null) {
                if (o.asString("status").equals("release") || o.asString("status").equals("trial-use") || o.asString("status").equals("update")) {
                  reg.seeRelease(rc, o.asString("status").equals("update") ? "STU Update" : o.asString("sequence"), o.asString("version"), o.asString("fhirversion", "fhir-version"), o.asString("path"));
                  hasRelease = true;
                } else if (!hasRelease && VersionUtilities.packageForVersion(o.asString("fhirversion", "fhir-version")) != null)
                  reg.seeCandidate(rc, o.asString("sequence")+" "+Utilities.titleize(o.asString("status")), o.asString("version"), o.asString("fhirversion", "fhir-version"), o.asString("path"));
              }
            }
          }
        }
        if (root != null) {
          updateStatement(folder, folders, ignoreList, json, root, errs, root, canonical, folder, canonical.equals("http://hl7.org/fhir"), true, list, updateStatements, pl.milestones());
        }
        if (save)
          FileUtilities.stringToFile(JsonParser.compose(json, true), f);
        new HistoryPageUpdater().updateHistoryPage(historySource, folder, templateSrc, false);
      }
        
    } catch (Exception e) {
      e.printStackTrace();
      errs.add(e.getMessage());
    }
    if (errs.size() == 0)
      System.out.println(": ok");
    else {
      System.out.println("");
      for (String s : errs) {
        System.out.println("    "+s);
      }  
      if (throwError) {
        throw new IOException("Error Processing "+folder+", cannot continue. ("+String.join("|", errs)+")");
      }
    }
  }

  
  private String summariseDate(String d) {
    if (d == null || d.length() < 10) {
      return "??";
    }
    return d.substring(0,7);
  }


  private void checkCopyFolderFromRoot(String focus, String name) throws IOException {
    File src = new File(Utilities.path(rootFolder, name));
    if (!src.exists()) {
      System.out.println("History Note: "+src.getAbsolutePath()+" does not exist, so ignoring it");        
      return; // if the named folder doesn't exist in root, we don't try to copy it
    }
    
    File f = new File(Utilities.path(focus, name));
    if (!f.exists() || f.isDirectory()) {
      if (f.exists()) {
        FileUtilities.clearDirectory(f.getAbsolutePath());
      }

      if (!src.isDirectory()) {
        System.out.println("History Error: "+src.getAbsolutePath()+" is a file, not a directory");
      } else {
        FileUtils.copyDirectory(src, f);
      }
    } else  {
      System.out.println("History Error: "+f.getAbsolutePath()+" is a file, not a directory");
    }
  }

  private void scrubApostrophes(JsonObject json) {
    for (JsonProperty p : json.getProperties()) {
      if (p.getValue().isJsonPrimitive()) {
        scrubApostrophesInProperty(p);
      } else if (p.getValue().isJsonObject()) {
        scrubApostrophes((JsonObject) p.getValue());
      } else if (p.getValue().isJsonArray()) {
        int i = 0;
        for (JsonElement ai : ((JsonArray) p.getValue())) {
          if (ai.isJsonPrimitive()) {
            if (ai.asString().contains("'"))
              throw new Error("Don't know how to handle apostrophes in arrays");
          } else if (ai.isJsonObject()) {
            scrubApostrophes((JsonObject) ai);
          } // no arrays containing arrays in package-list.json
          i++;
        }
      }
    }
  }

//  private void checkJsonNoApostrophes(String path, JsonObject json) {
//    for (Entry<String, JsonElement> p : json.entrySet()) {
//      if (p.getValue().isJsonPrimitive()) {
//        checkJsonNoApostrophesInProperty(path+"."+p.getKey(), p.getValue());
//      } else if (p.getValue().isJsonObject()) {
//        checkJsonNoApostrophes(path+"."+p.getKey(), (JsonObject) p.getValue());
//      } else if (p.getValue().isJsonArray()) {
//        int i = 0;
//        for (JsonElement ai : ((JsonArray) p.getValue())) {
//          if (ai.isJsonPrimitive()) {
//            checkJsonNoApostrophesInProperty(path+"."+p.getKey()+"["+Integer.toString(i)+"]", ai);
//          } else if (ai.isJsonObject()) {
//            checkJsonNoApostrophes(path+"."+p.getKey()+"["+Integer.toString(i)+"]", (JsonObject) ai);
//          } // no arrays containing arrays in package-list.json
//          i++;
//        }
//      }
//    }
//  }
//
  private void scrubApostrophesInProperty(JsonProperty p) {
    String s = p.getValue().asString();
    if (s.contains("'")) {
      s = s.replace("'", "`");
      p.setValue(new JsonString(s));
    }
  }

//  private void checkJsonNoApostrophesInProperty(String path, JsonElement json) {
//    String s = json.getAsString();
//    if (s.contains("'"))
//      System.out.println("There is a problem in the package-list.json file: "+path+" contains an apostrophe (\"'\")");
//  }
//
  private boolean updateStatement(String vf, List<String> ignoreList, List<String> ignoreListOuter, JsonObject ig, JsonObject version, List<String> errs, JsonObject root, String canonical, String canonicalPath, boolean isCore, 
      boolean isCurrent, JsonArray list, boolean updateStatements, List<PackageListEntry> milestones) throws FileNotFoundException, IOException, FHIRException, ParseException {
    if (!fullUpdate) {
      return false;
    }
    boolean vc = false;
    IGReleaseVersionUpdater igvu = new IGReleaseVersionUpdater(vf, url, rootFolder, ignoreList, ignoreListOuter, version, folder);
    if (updateStatements) {
      PackageList pl = new PackageList(ig);
      PackageListEntry pv = pl.findByVersion(version.asString("version"));
      String fragment = PublishBoxStatementGenerator.genFragment(pl, pv, pl.current(), canonical, pv == pl.current(), false);
      System.out.println("  "+vf+": "+fragment);
      igvu.updateStatement(fragment, ignoreList != null ? 0 : 1, milestones);
      System.out.println("  .. "+igvu.getCountTotal()+" files checked, "+igvu.getCountUpdated()+" updated");
    }
    igvu.checkXmlJsonClones(vf);
    System.out.println("  .. "+igvu.getClonedTotal()+" clones checked, "+igvu.getClonedCount()+" updated");
    if (!isCore) {
      IGPackageChecker pc = new IGPackageChecker(vf, canonical, version.asString("path"), ig.asString("package-id"));
      String fv = version.asString("fhirversion", "fhirversion");
      pc.check(version.asString("version"), ig.asString("package-id"), fv, 
          ig.asString("title"), new SimpleDateFormat("yyyy-MM-dd", new Locale("en", "US")).parse(version.asString("date")), version.asString("path"), canonical, getJurisdiction(vf, fv, ig, version));
    }
    IGReleaseRedirectionBuilder rb = new IGReleaseRedirectionBuilder(vf, canonical, version.asString("path"), rootFolder);
    if (serverType == ServerType.APACHE) {
      rb.buildApacheRedirections();
    } else if (serverType == ServerType.CLOUD) {
      rb.buildCloudRedirections();
    } else if (serverType == ServerType.ASP2) {
      rb.buildNewAspRedirections(isCore, isCore && vf.equals(rootFolder));
    } else if (serverType == ServerType.ASP1) {
      rb.buildOldAspRedirections();
    } else if (serverType == ServerType.LITESPEED) {
      rb.buildLitespeedRedirections();
    } else if (!canonical.contains("hl7.org/fhir")) {
      rb.buildApacheRedirections();
    } else {
      rb.buildOldAspRedirections();
    }
    System.out.println("  .. "+rb.getCountTotal()+" redirections ("+rb.getCountUpdated()+" created/updated)");
    new DownloadBuilder(vf, canonical, isCurrent ?  canonical : version.asString("path"), ignoreList).execute();
    if (!isCurrent && serverType == ServerType.ASP2) {
      new VersionRedirectorGenerator(canonicalPath).execute(version.asString("version"), version.asString("path"));
    }
    if (!version.has("fhirversion", "fhirversion")) {
      if (rb.getFhirVersion() == null) {
        System.out.println("Unable to determine FHIR version for "+vf);
      } else {
        version.add("fhirversion", rb.getFhirVersion());
        vc = true;
      }
    }
    // check links:
    checkFileExists(vf, "package.tgz");
    checkFileExists(vf, "qa.html");
    checkFileExists(vf, isCore ? "fhir-spec.zip" : "full-ig.zip");
    
    if (sft != null) {
      String html = FileUtilities.fileToString(sft);
      html = fixParameter(html, "title", ig.asString("title"));
      html = fixParameter(html, "id", ig.asString("package-id"));
      html = fixParameter(html, "version", isCurrent ? "All Versions" : version.asString("version"));
      html = fixParameter(html, "path", isCurrent ? canonicalPath : version.asString("path"));
      html = fixParameter(html, "history", isCurrent ? "history.html" : "../history.html");
      html = fixParameter(html, "search-list", searchLinks(isCurrent, version, canonicalPath, list));
      html = fixParameter(html, "note", isCurrent ? "this search searches all versions of the "+ig.asString("title")+", including balloted versions. You can also search specific versions" :
        "this search searches version "+version.asString("version")+" of the "+ig.asString("title")+". You can also search other versions, or all versions at once");
      html = fixParameter(html, "prefix", "");            
      FileUtilities.stringToFile(html, Utilities.path(vf, "searchform.html"));          
    }

    return vc;
  }


  private String fixParameter(String html, String name, String value) {
    while (html.contains("[%"+name+"%]")) {
      html = html.replace("[%"+name+"%]", value == null ? "" : value);
    }
    return html;
  }


  
  private String getJurisdiction(String vf, String fv, JsonObject ig, JsonObject version) throws FHIRFormatError, FHIRException, FileNotFoundException, IOException {
    String inferred = readJurisdictionFromPackageIg(version.has("package-id") ? version.asString("package-id") : ig.asString("package-id"));
        
    // first, we're going to look in the directory to find a set of IG resources.
    List<String> igs = findCandidateIgs(vf);
    // then we choose the most likely one
    if (igs.size() != 1) {
      System.out.println("Candidate IG resources = "+igs.toString()+". Using "+inferred);
      return inferred;
    }
    String igf = igs.get(0);
    ImplementationGuide igr;
    // then we parse it using the specified version
    try {
      igr = loadIg(Utilities.path(vf, igf), fv);
    } catch (Exception e) {
      System.out.println("Failed to read IG resource "+Utilities.path(vf, igf)+" ("+fv+"): "+e.getMessage());
      e.printStackTrace();
      System.out.println("Using "+inferred);
      return inferred;
    }
    
    if (igr == null) {
      System.out.println("Can't load IG resource "+Utilities.path(vf, igf)+" ("+fv+"). Using "+inferred);
      return inferred;      
    }
    // then we see if it has a jurisdiction
    if (!igr.hasJurisdiction()) {
      System.out.println("IG resource "+igf+": no Jurisdiction. Using "+inferred);
      return inferred;
    }
    for (CodeableConcept cc : igr.getJurisdiction()) {
      for (Coding c : cc.getCoding()) {
        String res = c.getSystem()+"#"+c.getCode();
        if (inferred != null && !inferred.equals(res)) {
          System.out.println("IG resource "+igs.toString()+": Jurisdiction mismatch. Found "+res+" but package implies "+inferred);          
        }
        return res;
      }
    }
    System.out.println("IG resource "+igs.toString()+": no Jurisdiction found. Using "+inferred);
    return inferred;
  }

  private ImplementationGuide loadIg(String igf, String fv) throws FHIRFormatError, FHIRException, FileNotFoundException, IOException {
    FileInputStream fs = new FileInputStream(igf);
    try {
      if (VersionUtilities.isR2Ver(fv)) {
        return (ImplementationGuide) VersionConvertorFactory_10_50.convertResource(new org.hl7.fhir.dstu2.formats.XmlParser().parse(fs));
      }
      if (VersionUtilities.isR3Ver(fv)) {
        return (ImplementationGuide) VersionConvertorFactory_30_50.convertResource(new org.hl7.fhir.dstu3.formats.XmlParser().parse(fs), new BaseAdvisor_30_50(false));
      }
      if (VersionUtilities.isR4Ver(fv)) {
        return (ImplementationGuide) VersionConvertorFactory_40_50.convertResource(new org.hl7.fhir.r4.formats.XmlParser().parse(fs));
      }
      if (VersionUtilities.isR3Ver(fv)) {
        return (ImplementationGuide) new org.hl7.fhir.r5.formats.XmlParser().parse(fs);
      }
      return null;
    } finally {
      fs.close();
    }
  }

  private String readJurisdictionFromPackageIg(String id) {
    String[] p = id.split("\\.");
    if (p.length >= 3 && "hl7".equals(p[0]) && "fhir".equals(p[1])) {
     if ("uv".equals(p[2])) {
       return "http://unstats.un.org/unsd/methods/m49/m49.htm#001";
     }
     if ("us".equals(p[2])) {
       return "urn:iso:std:iso:3166#US";
     }
     return null;
    }
    return null;
  }

  private List<String> findCandidateIgs(String vf) {
    List<String> res = new ArrayList<>();
    for (String f : new File(vf).list()) {
      if (f.endsWith(".xml") && f.startsWith("ImplementationGuide-")) {
        res.add(f);
      }
    }
    return res;
  }

  private String searchLinks(boolean root, JsonObject focus, String canonical, JsonArray list) {
    StringBuilder b = new StringBuilder();
    if (!root) {
      b.append(" <li><a data-no-external=\"true\" href=\""+canonical+"/searchform.html\">All Versions</a></li>\r\n");
    }
    for (JsonElement n : list) {
      JsonObject o = (JsonObject) n;
      if (!o.asString("version").equals("current")) {
        String v = o.asString("version");
        String path = o.asString("path");
        String date = o.asString("date");
        if (o == focus && !root) {
          b.append(" <li>"+o.asString("sequence")+" "+Utilities.titleize(o.asString("status"))+" (v"+v+", "+summariseDate(date)+") (this version)</li>\r\n");
        } else {
          b.append(" <li><a data-no-external=\"true\" href=\""+path+"/searchform.html\">"+o.asString("sequence")+" "+Utilities.titleize(o.asString("status"))+" (v"+v+", "+summariseDate(date)+")</a></li>\r\n");
        }
      }
    }
    return b.toString();
  }

  private void checkFileExists(String vf, String name) throws IOException {
    if (!new File(Utilities.path(vf, name)).exists()) {
      System.out.println("File "+name+" does not exist in "+vf);
    }

  }


  public static void main(String[] args) throws Exception {
    new IGReleaseUpdater(args[0], args[1], args[2], null, ServerType.ASP2, null, null, true, args[3]).check(null, false, true, args[4]);
  }
  
}
