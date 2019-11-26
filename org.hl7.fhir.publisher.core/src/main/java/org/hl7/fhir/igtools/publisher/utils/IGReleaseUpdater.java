package org.hl7.fhir.igtools.publisher.utils;

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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.utils.IGRegistryMaintainer.ImplementationGuideEntry;
import org.hl7.fhir.igtools.publisher.utils.IGReleaseUpdater.ServerType;
import org.hl7.fhir.r5.model.Enumerations.FHIRVersion;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.JSONUtil;
import org.hl7.fhir.utilities.json.JsonTrackingParser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class IGReleaseUpdater {

  public enum ServerType {
    APACHE, ASP;

    public static ServerType fromCode(String st) {
      st = st.toLowerCase();
      if (st.equals("asp"))
        return ServerType.ASP;
      else if (st.equals("apache"))
        return ServerType.APACHE;
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

  public IGReleaseUpdater(String folder, String url, String rootFolder, IGRegistryMaintainer reg, ServerType serverType, List<String> otherSpecs) throws IOException {
    this.folder = folder;
    this.url = url;
    this.rootFolder = rootFolder;
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
            if (o.has("current"))
              root = o;
          }
        }
        boolean save = false;
        ImplementationGuideEntry rc = reg == null ? null : reg.seeIg(JSONUtil.str(json, "package-id"), canonical, JSONUtil.str(json, "title"));
        boolean hasRelease = false;
        List<String> folders = new ArrayList<>();
        for (JsonElement n : list) {
          JsonObject o = (JsonObject) n;
          if (JSONUtil.str(o, "version").equals("current")) {
            if (reg != null) {
              reg.seeCiBuild(rc, JSONUtil.str(o, "path"));
            }
          } else {
            String v = JSONUtil.str(o, "version");
            if (!o.has("path"))
              errs.add("version "+v+" has no path'"); 
            else {
              String path = JSONUtil.str(o, "path");
              String vf = Utilities.path(path.replace(url, rootFolder));
              if (!path.endsWith(".html")) {
                if (!(new File(vf).exists()))
                  errs.add("version "+v+" path "+vf+" not found (canonical = "+canonical+", path = "+path+")");
                else {
                  folders.add(vf);
                  save = updateStatement(vf, null, ignoreList, json, o, errs, root, canonical, canonical.equals("http://hl7.org/fhir")) | save;
                }
              }
              if (o.has("current"))
                root = o;
              if (reg != null) {
                if (JSONUtil.str(o, "status").equals("release") || JSONUtil.str(o, "status").equals("trial-use") || JSONUtil.str(o, "status").equals("update")) {
                  reg.seeRelease(rc, JSONUtil.str(o, "status").equals("update") ? "STU Update" : JSONUtil.str(o, "sequence"), JSONUtil.str(o, "version"), JSONUtil.str(o, "fhirversion", "fhir-version"), JSONUtil.str(o, "path"));
                  hasRelease = true;
                } else if (!hasRelease && FHIRVersion.isValidCode(JSONUtil.str(o, "fhirversion", "fhir-version")))
                  reg.seeCandidate(rc, JSONUtil.str(o, "sequence")+" "+Utilities.titleize(JSONUtil.str(o, "status")), JSONUtil.str(o, "version"), JSONUtil.str(o, "fhirversion", "fhir-version"), JSONUtil.str(o, "path"));
              }
            }
          }
        }
        if (root != null)
          updateStatement(folder, folders, ignoreList, json, root, errs, root, canonical, canonical.equals("http://hl7.org/fhir"));
        if (save)
          TextFile.stringToFile(new GsonBuilder().setPrettyPrinting().create().toJson(json), f, false);
        File ht = new File(Utilities.path(folder, "history.template"));
        if (ht.exists()) {
          scrubApostrophes(json);
          String jsonv = new GsonBuilder().create().toJson(json);
          String html = TextFile.fileToString(ht);
          while (html.contains("[%title%]"))
            html = html.replace("[%title%]", json.get("title").getAsString());
          while (html.contains("[%json%]"))
            html = html.replace("[%json%]", jsonv);
          TextFile.stringToFile(html, Utilities.path(folder, "history.html"), false);
        }
        ht = new File(Utilities.path(folder, "directory.template"));
        if (ht.exists()) {
          scrubApostrophes(json);
          String jsonv = new GsonBuilder().create().toJson(json);
          String html = TextFile.fileToString(ht);
          while (html.contains("[%title%]"))
            html = html.replace("[%title%]", json.get("title").getAsString());
          while (html.contains("[%json%]"))
            html = html.replace("[%json%]", jsonv);
          TextFile.stringToFile(html, Utilities.path(folder, "directory.html"), false);
        }
      }
        
    } catch (Exception e) {e.printStackTrace();
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
  private boolean updateStatement(String vf, List<String> ignoreList, List<String> ignoreListOuter, JsonObject ig, JsonObject version, List<String> errs, JsonObject root, String canonical, boolean isCore) throws FileNotFoundException, IOException, FHIRException, ParseException {

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
      pc.check(JSONUtil.str(version, "version"), JSONUtil.str(ig, "package-id"), JSONUtil.str(version, "fhirversion", "fhirversion"), 
          JSONUtil.str(ig, "title"), new SimpleDateFormat("yyyy-MM-dd").parse(JSONUtil.str(version, "date")), JSONUtil.str(version, "path"), canonical);
    }
    IGReleaseRedirectionBuilder rb = new IGReleaseRedirectionBuilder(vf, canonical, JSONUtil.str(version, "path"));
    if (serverType == ServerType.APACHE)
      rb.buildApacheRedirections();
    else if (serverType == ServerType.ASP)
      rb.buildAspRedirections();
    else if (!canonical.contains("hl7.org/fhir"))
      rb.buildApacheRedirections();
    else
      rb.buildAspRedirections();
    System.out.println("  .. "+rb.getCountTotal()+" redirections ("+rb.getCountUpdated()+" created/updated)");
    if (!JSONUtil.has(version, "fhirversion", "fhirversion")) {
      if (rb.getFhirVersion() == null) {
        System.out.println("Unable to determine FHIR version for "+vf);
      } else {
        version.addProperty("fhirversion", rb.getFhirVersion());
        vc = true;
      }
    }
    return vc;
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
    new IGReleaseUpdater(args[0], args[1], args[2], null, ServerType.ASP, null).check();
  }
  
}
