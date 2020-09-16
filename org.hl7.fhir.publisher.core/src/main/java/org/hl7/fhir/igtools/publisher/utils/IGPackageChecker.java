package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.model.Enumerations.FHIRVersion;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.SPDXLicense;
import org.hl7.fhir.r5.utils.NPMPackageGenerator;
import org.hl7.fhir.r5.utils.NPMPackageGenerator.Category;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.cache.PackageGenerator.PackageType;
import org.hl7.fhir.utilities.json.JSONUtil;
import org.hl7.fhir.utilities.json.JsonTrackingParser;

import com.google.gson.JsonObject;

public class IGPackageChecker {

  private String folder;
  private String canonical;
  private String vpath;
  private String packageId;

  public IGPackageChecker(String folder, String canonical, String vpath, String packageId) {
    this.folder = folder; 
    this.canonical = canonical;
    this.vpath = vpath;
    this.packageId = packageId;
  }

  public void check(String ver, String pckId, String fhirversion, String name, Date date, String url, String canonical, String jurisdiction) throws IOException, FHIRException {
    String pf = Utilities.path(folder, "package.tgz");
    File f = new File(pf);
    if (!f.exists()) {
      makePackage(pf, name, ver, fhirversion, date);
    } else {
      NpmPackage pck = NpmPackage.fromPackage(new FileInputStream(f));
      JsonObject json = pck.getNpm();
      checkJsonProp(pf, json, "version", ver);
      checkJsonProp(pf, json, "name", pckId);
      checkJsonProp(pf, json, "url", url);
      checkJsonProp(pf, json, "canonical", canonical);
      if (jurisdiction != null) {
        checkChangeJsonProp(pck, pf, json, "jurisdiction", jurisdiction);
      }
      if (pck.isNotForPublication()) {
        throw new Error("Error: the package at "+pf+" is not suitable for publication");
      }
      if (!json.has("fhirVersions")) {
        System.out.println("Problem #2 with "+pf+": missing fhirVersions");
      } else {
        if (json.getAsJsonArray("fhirVersions").size() == 0) {
          System.out.println("Problem #3 with "+pf+": fhirVersions size = "+json.getAsJsonArray("fhirVersions").size());          
        }
        if (!VersionUtilities.versionsCompatible(json.getAsJsonArray("fhirVersions").get(0).getAsString(), fhirversion)) {
          System.out.println("Problem #4 with "+pf+": fhirVersions value mismatch (expected "+(fhirversion.contains("|") ? "one of "+fhirversion : fhirversion)+", found "+json.getAsJsonArray("fhirVersions").get(0).getAsString()+")");
        }
      }
      if (json.has("dependencies")) {
        JsonObject dep = json.getAsJsonObject("dependencies");
        if (dep.has("hl7.fhir.core")) {
          System.out.println("Problem #5 with "+pf+": found hl7.fhir.core in dependencies");
        }  
        if (fhirversion.startsWith("1.0")) {
          if (!dep.has("hl7.fhir.r2.core")) {
            System.out.println("Problem #6 with "+pf+": R2 guide doesn't list R2 in it's dependencies");
          } else if (!VersionUtilities.versionsCompatible(fhirversion, JSONUtil.str(dep, "hl7.fhir.r2.core"))) {
            System.out.println("Problem #7 with "+pf+": fhirVersions value mismatch on hl7.fhir.r2.core (expected "+fhirversion+", found "+JSONUtil.str(dep, "hl7.fhir.r2.core"));
          }
        } else if (fhirversion.startsWith("1.4")) {
          if (!dep.has("hl7.fhir.r2b.core")) {
            System.out.println("Problem #8 with "+pf+": R2B guide doesn't list R2B in it's dependencies");
          } else if (!VersionUtilities.versionsCompatible(fhirversion, JSONUtil.str(dep, "hl7.fhir.r2b.core"))) {
            System.out.println("Problem #9 with "+pf+": fhirVersions value mismatch on hl7.fhir.r2b.core (expected "+fhirversion+", found "+JSONUtil.str(dep, "hl7.fhir.r2b.core"));
          }          
        } else if (fhirversion.startsWith("3.0")) {
          if (!dep.has("hl7.fhir.r3.core")) {
            System.out.println("Problem #10 with "+pf+": R3 guide doesn't list R3 in it's dependencies");
          } else if (!VersionUtilities.versionsCompatible(fhirversion, JSONUtil.str(dep, "hl7.fhir.r3.core"))) {
            System.out.println("Problem #11 with "+pf+": fhirVersions value mismatch on hl7.fhir.r3.core (expected "+fhirversion+", found "+JSONUtil.str(dep, "hl7.fhir.r3.core"));
          }
        } else if (fhirversion.startsWith("4.0")) {
          if (!dep.has("hl7.fhir.r4.core")) {
            System.out.println("Problem #12 with "+pf+": R4 guide doesn't list R4 in it's dependencies");
          } else if (!VersionUtilities.versionsCompatible(fhirversion, JSONUtil.str(dep, "hl7.fhir.r4.core"))) {
            System.out.println("Problem #13 with "+pf+": fhirVersions value mismatch on hl7.fhir.r4.core (expected "+fhirversion+", found "+JSONUtil.str(dep, "hl7.fhir.r4.core"));
          }
        }
      }
      if (pck.isChangedByLoader()) {
        pck.save(new FileOutputStream(f));
      }
    }
  }

  public void checkJsonProp(String pf, JsonObject json, String propName, String value) {
    if (!json.has(propName)) {
      System.out.println("Problem #14 with "+pf+": missing "+propName);
    } else if (!json.get(propName).getAsString().equals(value)) {
      System.out.println("Problem #15 with "+pf+": expected "+propName+" "+value+" but found "+json.get(propName).getAsString());
    }
  }

  public void checkChangeJsonProp(NpmPackage pck, String pf, JsonObject json, String propName, String value) throws FileNotFoundException, IOException {
    if (!json.has(propName) || !json.get(propName).getAsString().equals(value)) {
      if (json.has(propName)) {
        json.remove(propName);
      }
      json.addProperty(propName, value);
      pck.save(new FileOutputStream(pf));      
    }
  }

  private String tail(String s) {
    return s.substring(s.lastIndexOf("/")+1);
  }

  private void makePackage(String file, String name, String ver, String fhirversion, Date date) throws FHIRException, IOException {
    ImplementationGuide ig = new ImplementationGuide();
    ig.setUrl(Utilities.pathURL(canonical, "ImplementationGuide", "ig"));
    ig.setName(name);
    ig.setTitle(Utilities.titleize(name));
    ig.setVersion(ver);
    ig.getDateElement().setValue(date);
    ig.setPackageId(packageId);
    ig.setLicense(SPDXLicense.CC01_0);
    ig.getManifest().setRendering(vpath);
    if (FHIRVersion.isValidCode(fhirversion))
      ig.addFhirVersion(FHIRVersion.fromCode(fhirversion));
    List<String> fhirversions = new ArrayList<>();
    if (fhirversion.contains("|")) {
      for (String v : fhirversion.split("\\|")) {
        fhirversions.add(v);
      }
    } else {
      fhirversions.add(fhirversion);
    }
    NPMPackageGenerator npm = new NPMPackageGenerator(file, canonical, vpath, PackageType.IG, ig, date, fhirversions, true);
    for (File f : new File(folder).listFiles()) {
      if (f.getName().endsWith(".openapi.json")) {
        byte[] src = TextFile.fileToBytes(f.getAbsolutePath());
        npm.addFile(Category.OPENAPI, f.getName(), src);
      } else if (f.getName().endsWith(".json")) {
        byte[] src = TextFile.fileToBytes(f.getAbsolutePath());
        String s = TextFile.bytesToString(src);
        if (s.contains("\"resourceType\"")) {
          JsonObject json = JsonTrackingParser.parseJson(s);
          if (json.has("resourceType") && json.has("id") && json.get("id").isJsonPrimitive()) {
            String rt = json.get("resourceType").getAsString();
            String id = json.get("id").getAsString();
            npm.addFile(Category.RESOURCE, rt+"-"+id+".json", src);
          }
        }
      }
      if (f.getName().endsWith(".sch")) {
        byte[] src = TextFile.fileToBytes(f.getAbsolutePath());
        npm.addFile(Category.SCHEMATRON, f.getName(), src);
      }
      if (f.getName().equals("spec.internals")) {
        byte[] src = TextFile.fileToBytes(f.getAbsolutePath());
        npm.addFile(Category.OTHER, f.getName(), src);
      }
    }
    npm.finish();    
  }

}
