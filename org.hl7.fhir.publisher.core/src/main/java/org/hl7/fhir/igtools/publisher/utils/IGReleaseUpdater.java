package org.hl7.fhir.igtools.publisher.utils;

import java.io.BufferedReader;

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
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.hl7.fhir.convertors.VersionConvertor_10_50;
import org.hl7.fhir.convertors.VersionConvertor_30_50;
import org.hl7.fhir.convertors.VersionConvertor_40_50;
import org.hl7.fhir.dstu2.formats.JsonParser;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.utils.IGRegistryMaintainer.ImplementationGuideEntry;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.JSONUtil;
import org.hl7.fhir.utilities.json.JsonTrackingParser;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class IGReleaseUpdater {

  public enum ServerType {
    APACHE, ASP1, ASP2, LITESPEED;

    public static ServerType fromCode(String st) {
      st = st.toLowerCase();
      if (st.equals("asp-old"))
        return ServerType.ASP1;
      else if (st.equals("asp-new"))
        return ServerType.ASP2;
      else if (st.equals("apache"))
        return ServerType.APACHE;
      else if (st.equals("litespeed"))
        return ServerType.LITESPEED;
      else 
        throw new Error("-server-type "+st+" not known - use ASP or Apache");
    }
  }

  private String folder;
  private String url;
  private String rootFolder;
  private IGRegistryMaintainer reg;
  private ServerType serverType;
  private List<String> ignoreList = new ArrayList<>();
  private File sft;

  public IGReleaseUpdater(String folder, String url, String rootFolder, IGRegistryMaintainer reg, ServerType serverType, List<String> otherSpecs, File sft) throws IOException {
    this.folder = folder;
    this.url = url;
    this.rootFolder = rootFolder;
    this.sft = sft;
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
    this.ignoreList.addAll(otherSpecs);
  }

  public void check()  {
    List<String> errs = new ArrayList<>(); 
    try {
      String f = Utilities.path(folder, "package-list.json");
      if (!new File(f).exists())
        errs.add("unable to find package-list.json");
      else {
        JsonObject json = JsonTrackingParser.parseJsonFile(f);
        String canonical = JSONUtil.str(json, "canonical");

        JsonArray list = json.getAsJsonArray("list");
        JsonObject root = null;
        System.out.println("Update "+folder+" for "+canonical);
        for (JsonElement n : list) {
          JsonObject o = (JsonObject) n;
          if (!o.has("version"))
           throw new Error(folder+" has Version without version");
          if (!JSONUtil.str(o, "version").equals("current")) {
            if (o.has("current") && o.get("current").getAsBoolean() && o.has("path") && o.get("path").getAsString().startsWith(canonical+"/")) {
              root = o;
            }
          }
        }
        boolean save = false;
        ImplementationGuideEntry rc = reg == null ? null : reg.seeIg(JSONUtil.str(json, "package-id"), canonical, JSONUtil.str(json, "title"), JSONUtil.str(json, "category"));
        if (!json.has("category")) {
          if (Utilities.noString(rc.getCategory())) {
            errs.add(f+" has no category value");            
          } else {
            json.addProperty("category", rc.getCategory());
            save = true;
          }
        }
        boolean hasRelease = false;
        List<String> folders = new ArrayList<>();
        for (JsonElement n : list) {
          JsonObject o = (JsonObject) n;
          if (JSONUtil.str(o, "version").equals("current")) {
            if (reg != null) {
              reg.seeCiBuild(rc, JSONUtil.str(o, "path"), f);
            }
          } else {
            String v = JSONUtil.str(o, "version");
            if (!o.has("path"))
              errs.add("version "+v+" has no path'"); 
            else {
              String path = JSONUtil.str(o, "path");
              String vf = Utilities.path(path.replace(url, rootFolder));
              if (!o.has("sequence")) {
                throw new Error("No Sequence value for version "+v+" in "+f);
              }
              if (!path.endsWith(".html")) {
                if (!(new File(vf).exists()))
                  errs.add("version "+v+" path "+vf+" not found (canonical = "+canonical+", path = "+path+")");
                else {
                  folders.add(vf);
                  save = updateStatement(vf, null, ignoreList, json, o, errs, root, canonical, folder, canonical.equals("http://hl7.org/fhir"), false, list) | save;
                }
              }
              if (o.has("current") && o.get("current").getAsBoolean() && o.has("path") && o.get("path").getAsString().startsWith(canonical+"/")) {
                root = o;
              }
              if (reg != null) {
                if (JSONUtil.str(o, "status").equals("release") || JSONUtil.str(o, "status").equals("trial-use") || JSONUtil.str(o, "status").equals("update")) {
                  reg.seeRelease(rc, JSONUtil.str(o, "status").equals("update") ? "STU Update" : JSONUtil.str(o, "sequence"), JSONUtil.str(o, "version"), JSONUtil.str(o, "fhirversion", "fhir-version"), JSONUtil.str(o, "path"));
                  hasRelease = true;
                } else if (!hasRelease && VersionUtilities.packageForVersion(JSONUtil.str(o, "fhirversion", "fhir-version")) != null)
                  reg.seeCandidate(rc, JSONUtil.str(o, "sequence")+" "+Utilities.titleize(JSONUtil.str(o, "status")), JSONUtil.str(o, "version"), JSONUtil.str(o, "fhirversion", "fhir-version"), JSONUtil.str(o, "path"));
              }
            }
          }
        }
        if (root != null) {
          updateStatement(folder, folders, ignoreList, json, root, errs, root, canonical, folder, canonical.equals("http://hl7.org/fhir"), true, list);
        }
        if (save)
          TextFile.stringToFile(new GsonBuilder().setPrettyPrinting().create().toJson(json), f, false);
        File ht = new File(Utilities.path(folder, "history.template"));
        if (ht.exists()) {
          scrubApostrophes(json);
          String jsonv = new GsonBuilder().create().toJson(json);
          String html = TextFile.fileToString(ht);
          html = fixParameter(html, "title", json.get("title").getAsString());
          html = fixParameter(html, "json", jsonv);
          html = html.replace("assets/", "assets-hist/");
          html = html.replace("dist/", "dist-hist/");
          TextFile.stringToFile(html, Utilities.path(folder, "history.html"), false);
        }
        ht = new File(Utilities.path(folder, "directory.template"));
        if (ht.exists()) {
          scrubApostrophes(json);
          String jsonv = new GsonBuilder().create().toJson(json);
          String html = TextFile.fileToString(ht);
          html = fixParameter(html, "title", json.get("title").getAsString());
          html = fixParameter(html, "json", jsonv);
          TextFile.stringToFile(html, Utilities.path(folder, "directory.html"), false);
        }
        checkCopyFolderFromRoot(folder, "dist-hist");
        checkCopyFolderFromRoot(folder, "assets-hist");
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
    }
  }

  private String summariseDate(String d) {
    if (d == null || d.length() < 10) {
      return "??";
    }
    return d.substring(0,7);
  }

  private String fixParameter(String html, String name, String value) {
    while (html.contains("[%"+name+"%]")) {
      html = html.replace("[%"+name+"%]", value);
    }
    return html;
  }

  private void checkCopyFolderFromRoot(String focus, String name) throws IOException {
    File f = new File(Utilities.path(focus, name));
    if (!f.exists() || f.isDirectory()) {
      if (f.exists()) {
        Utilities.clearDirectory(f.getAbsolutePath());
      }

      File src = new File(Utilities.path(rootFolder, name));
      if (!src.exists()) {
        System.out.println("History Error: "+src.getAbsolutePath()+" doe not exist");        
      } else if (!src.isDirectory()) {
        System.out.println("History Error: "+src.getAbsolutePath()+" is a file, not a directory");
      } else {
        FileUtils.copyDirectory(src, f);
      }
    } else  {
      System.out.println("History Error: "+f.getAbsolutePath()+" is a file, not a directory");
    }
    
  }

  private void scrubApostrophes(JsonObject json) {
    for (Entry<String, JsonElement> p : json.entrySet()) {
      if (p.getValue().isJsonPrimitive()) {
        scrubApostrophesInProperty(p);
      } else if (p.getValue().isJsonObject()) {
        scrubApostrophes((JsonObject) p.getValue());
      } else if (p.getValue().isJsonArray()) {
        int i = 0;
        for (JsonElement ai : ((JsonArray) p.getValue())) {
          if (ai.isJsonPrimitive()) {
            if (ai.getAsString().contains("'"))
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
  private void scrubApostrophesInProperty(Entry<String, JsonElement> p) {
    String s = p.getValue().getAsString();
    if (s.contains("'")) {
      s = s.replace("'", "`");
      p.setValue(new JsonPrimitive(s));
    }
  }

//  private void checkJsonNoApostrophesInProperty(String path, JsonElement json) {
//    String s = json.getAsString();
//    if (s.contains("'"))
//      System.out.println("There is a problem in the package-list.json file: "+path+" contains an apostrophe (\"'\")");
//  }
//
  private boolean updateStatement(String vf, List<String> ignoreList, List<String> ignoreListOuter, JsonObject ig, JsonObject version, List<String> errs, JsonObject root, String canonical, String canonicalPath, boolean isCore, 
      boolean isCurrent, JsonArray list) throws FileNotFoundException, IOException, FHIRException, ParseException {

    boolean vc = false;
    String fragment = genFragment(ig, version, root, canonical, ignoreList != null, isCore);
    System.out.println("  "+vf+": "+fragment);
    IGReleaseVersionUpdater igvu = new IGReleaseVersionUpdater(vf, ignoreList, ignoreListOuter, version, folder);
    igvu.updateStatement(fragment, ignoreList != null ? 0 : 1);
    System.out.println("  .. "+igvu.getCountTotal()+" files checked, "+igvu.getCountUpdated()+" updated");
    igvu.checkXmlJsonClones(vf);
    System.out.println("  .. "+igvu.getClonedTotal()+" clones checked, "+igvu.getClonedCount()+" updated");
    if (!isCore) {
      IGPackageChecker pc = new IGPackageChecker(vf, canonical, JSONUtil.str(version, "path"), JSONUtil.str(ig, "package-id"));
      String fv = JSONUtil.str(version, "fhirversion", "fhirversion");
      pc.check(JSONUtil.str(version, "version"), JSONUtil.str(ig, "package-id"), fv, 
          JSONUtil.str(ig, "title"), new SimpleDateFormat("yyyy-MM-dd", new Locale("en", "US")).parse(JSONUtil.str(version, "date")), JSONUtil.str(version, "path"), canonical, getJurisdiction(vf, fv, ig, version));
    }
    IGReleaseRedirectionBuilder rb = new IGReleaseRedirectionBuilder(vf, canonical, JSONUtil.str(version, "path"), rootFolder);
    if (serverType == ServerType.APACHE) {
      rb.buildApacheRedirections();
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
    new DownloadBuilder(vf, canonical, isCurrent ?  canonical: JSONUtil.str(version, "path")).execute();
    if (!isCurrent && serverType == ServerType.ASP2) {
      new VersionRedirectorGenerator(canonicalPath).execute(JSONUtil.str(version, "version"), JSONUtil.str(version, "path"));
    }
    if (!JSONUtil.has(version, "fhirversion", "fhirversion")) {
      if (rb.getFhirVersion() == null) {
        System.out.println("Unable to determine FHIR version for "+vf);
      } else {
        version.addProperty("fhirversion", rb.getFhirVersion());
        vc = true;
      }
    }
    // check links:
    checkFileExists(vf, "package.tgz");
    checkFileExists(vf, "qa.html");
    checkFileExists(vf, isCore ? "fhir-spec.zip" : "full-ig.zip");
    
    if (sft != null) {
      String html = TextFile.fileToString(sft);
      html = fixParameter(html, "title", JSONUtil.str(ig, "title"));
      html = fixParameter(html, "id", JSONUtil.str(ig, "package-id"));
      html = fixParameter(html, "version", isCurrent ? "All Versions" : JSONUtil.str(version, "version"));
      html = fixParameter(html, "path", isCurrent ? canonicalPath : JSONUtil.str(version, "path"));
      html = fixParameter(html, "history", isCurrent ? "history.html" : "../history.html");
      html = fixParameter(html, "search-list", searchLinks(isCurrent, version, canonicalPath, list));
      html = fixParameter(html, "note", isCurrent ? "this search searches all versions of the "+JSONUtil.str(ig, "title")+", including balloted versions. You can also search specific versions" :
        "this search searches version "+JSONUtil.str(version, "version")+" of the "+JSONUtil.str(ig, "title")+". You can also search other versions, or all versions at once");
      html = fixParameter(html, "prefix", "");            
      TextFile.stringToFile(html, Utilities.path(vf, "searchform.html"), false);          
    }

    return vc;
  }

  private String getJurisdiction(String vf, String fv, JsonObject ig, JsonObject version) throws FHIRFormatError, FHIRException, FileNotFoundException, IOException {
    String inferred = readJurisdictionFromPackageIg(version.has("package-id") ? version.get("package-id").getAsString() : ig.get("package-id").getAsString());
        
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
        return (ImplementationGuide) VersionConvertor_10_50.convertResource(new org.hl7.fhir.dstu2.formats.XmlParser().parse(fs));
      }
      if (VersionUtilities.isR3Ver(fv)) {
        return (ImplementationGuide) VersionConvertor_30_50.convertResource(new org.hl7.fhir.dstu3.formats.XmlParser().parse(fs), true);
      }
      if (VersionUtilities.isR4Ver(fv)) {
        return (ImplementationGuide) VersionConvertor_40_50.convertResource(new org.hl7.fhir.r4.formats.XmlParser().parse(fs));
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
      b.append(" <li><a href=\""+canonical+"/searchform.html\">All Versions</a></li>\r\n");
    }
    for (JsonElement n : list) {
      JsonObject o = (JsonObject) n;
      if (!JSONUtil.str(o, "version").equals("current")) {
        String v = JSONUtil.str(o, "version");
        String path = JSONUtil.str(o, "path");
        String date = JSONUtil.str(o, "date");
        if (o == focus && !root) {
          b.append(" <li>"+JSONUtil.str(o, "sequence")+" "+Utilities.titleize(JSONUtil.str(o, "status"))+" (v"+v+", "+summariseDate(date)+") (this version)</li>\r\n");
        } else {
          b.append(" <li><a href=\""+path+"/searchform.html\">"+JSONUtil.str(o, "sequence")+" "+Utilities.titleize(JSONUtil.str(o, "status"))+" (v"+v+", "+summariseDate(date)+")</a></li>\r\n");
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

  /**
   * The fragment of HTML this generates has 3 parts 
   * 
   * 1. statement of what this is 
   * 2. reference to current version
   * 3. referenceto list of published versions
   * 
   * @param version
   * @param root
   * @param canonical
   * @return
   */
  private String genFragment(JsonObject ig, JsonObject version, JsonObject root, String canonical, boolean currentPublication, boolean isCore) {
    String p1 = JSONUtil.str(ig, "title")+" (v"+JSONUtil.str(version, "version")+": "+state(ig, version)+")";
    if (!isCore) {
      p1 = p1 + (version.has("fhirversion") ? " based on <a href=\"http://hl7.org/fhir/"+getPath(JSONUtil.str(version, "fhirversion", "fhir-version"))+"\">FHIR "+fhirRef(JSONUtil.str(version, "fhirversion"))+"</a>" : "")+". ";
    } else {
      p1 = p1 + ". ";      
    }
    String p2 = root == null ? "" : version == root ? "This is the current published version"+(currentPublication ? "" : " in it's permanent home (it will always be available at this URL)") : "The current version which supercedes this version is <a href=\""+(JSONUtil.str(root, "path").startsWith(canonical) ? canonical : JSONUtil.str(root, "path"))+"{{fn}}\">"+JSONUtil.str(root, "version")+"</a>";
    String p3;
    if (canonical.equals("http://hl7.org/fhir"))
      p3 = " For a full list of available versions, see the <a href=\""+canonical+"/directory.html\">Directory of published versions <img src=\"external.png\" style=\"text-align: baseline\"></a>";
    else
      p3 = " For a full list of available versions, see the <a href=\""+canonical+"/history.html\">Directory of published versions <img src=\"external.png\" style=\"text-align: baseline\"></a>";
    return "This page is part of the "+p1+p2+". "+p3;
  }

  private String getPath(String v) {
    if ("4.0.1".equals(v))
      return "R4";
    if ("4.0.0".equals(v))
      return "R4";
    if ("3.5a.0".equals(v))
      return "2018Dec";
    if ("3.5.0".equals(v))
      return "2018Sep";
    if ("3.3.0".equals(v))
      return "2018May";
    if ("3.2.0".equals(v))
      return "2018Jan";
    if ("3.0.0".equals(v))
      return "STU3";
    if ("3.0.1".equals(v))
      return "STU3";
    if ("3.0.2".equals(v))
      return "STU3";
    if ("1.8.0".equals(v))
      return "2017Jan";
    if ("1.6.0".equals(v))
      return "2016Sep";
    if ("1.4.0".equals(v))
      return "2016May";
    if ("1.1.0".equals(v))
      return "2015Dec";
    if ("1.0.2".equals(v))
      return "DSTU2";
    if ("1.0.0".equals(v))
      return "2015Sep";
    if ("0.5.0".equals(v))
      return "2015May";
    if ("0.4.0".equals(v))
      return "2015Jan";
    if ("0.0.82".equals(v))
      return "DSTU1";
    if ("0.11".equals(v))
      return "2013Sep";
    if ("0.06".equals(v))
      return "2013Jan";
    if ("0.05".equals(v))
      return "2012Sep";
    if ("0.01".equals(v))
      return "2012May";
    if ("current".equals(v))
      return "2011Aug";
    return v;
  }

  private String fhirRef(String v) {
    if (VersionUtilities.isR2Ver(v))
      return "R2";
    if (VersionUtilities.isR3Ver(v))
      return "R3";
    if (VersionUtilities.isR4Ver(v))
      return "R4";    
    return "v"+v;
  }

  private String state(JsonObject ig, JsonObject version) {
    String status = JSONUtil.str(version, "status");
    String sequence = JSONUtil.str(version, "sequence");
    if ("trial-use".equals(status))
      return decorate(sequence);
    else if ("release".equals(status))
      return "Release";
    else if ("qa-preview".equals(status))
      return "QA Preview";
    else if ("ballot".equals(status))
      return decorate(sequence)+" Ballot "+ballotCount(ig, sequence, version);
    else if ("draft".equals(status))
      return decorate(sequence)+" Draft";
    else if ("update".equals(status))
      return decorate(sequence)+" Update";
    else if ("normative+trial-use".equals(status))
      return decorate(sequence+" - Mixed Normative and STU");
    else 
      throw new Error("unknown status "+status);
  }

  private String decorate(String sequence) {
    sequence = sequence.replace("Normative", "<a href=\"https://confluence.hl7.org/display/HL7/HL7+Balloting\" title=\"Normative Standard\">Normative</a>");
    if (sequence.contains("DSTU"))
      return sequence.replace("DSTU", "<a href=\"https://confluence.hl7.org/display/HL7/HL7+Balloting\" title=\"Draft Standard for Trial-Use\">DSTU</a>");
    else
      return sequence.replace("STU", "<a href=\"https://confluence.hl7.org/display/HL7/HL7+Balloting\" title=\"Standard for Trial-Use\">STU</a>");
  }

  private String ballotCount(JsonObject ig, String sequence, JsonObject version) {
    int c = 1;
    JsonArray list = ig.getAsJsonArray("list");
    for (int i = list.size() - 1; i >= 0; i--) {
      JsonObject o = (JsonObject) list.get(i);
      if (o == version)
        return Integer.toString(c);
      if (sequence.equals(JSONUtil.str(o, "sequence")) && "ballot".equals(JSONUtil.str(version, "status")))
        c++;
    }
    return "1";
  }

  public static void main(String[] args) throws Exception {
    new IGReleaseUpdater(args[0], args[1], args[2], null, ServerType.ASP2, null, null).check();
  }
  
}
