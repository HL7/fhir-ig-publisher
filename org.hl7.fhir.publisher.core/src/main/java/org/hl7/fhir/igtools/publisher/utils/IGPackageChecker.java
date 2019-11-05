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


import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.model.Enumerations.FHIRVersion;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.SPDXLicense;
import org.hl7.fhir.r5.test.utils.ToolsHelper;
import org.hl7.fhir.r5.utils.NPMPackageGenerator;
import org.hl7.fhir.r5.utils.NPMPackageGenerator.Category;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.cache.NpmPackageIndexBuilder;
import org.hl7.fhir.utilities.cache.PackageGenerator.PackageType;
import org.hl7.fhir.utilities.json.JsonTrackingParser;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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

  public void check(String ver, String pckId, String fhirversion, String name, Date date, String url, String canonical) throws IOException, FHIRException {
    String pf = Utilities.path(folder, "package.tgz");
    File f = new File(pf);
    if (!f.exists()) {
      makePackage(pf, name, ver, fhirversion, date);
    } else {
      NpmPackage pck = NpmPackage.fromPackage(new FileInputStream(f));
      File af = new File(Utilities.changeFileExt(f.getAbsolutePath(), ".tgz.bak"));
      if (!af.exists()) // if it already exists, assume that's our backup, don't overrwrite it
        f.renameTo(af);
      JsonObject json = pck.getNpm();
      if (!json.has("version") || !json.get("version").getAsString().equals(ver)) {
        json.remove("version");
        json.addProperty("version", ver);
      }
      if (!json.has("name") || !json.get("name").getAsString().equals(pckId)) {
        json.remove("name");
        json.addProperty("name", pckId);
      }
      if (!json.has("url") || !json.get("url").getAsString().equals(url)) {
        json.remove("url");
        json.addProperty("url", url);
      }
      if (!json.has("canonical") || !json.get("canonical").getAsString().equals(canonical)) {
        json.remove("canonical");
        json.addProperty("canonical", canonical);
      }
      JsonArray vl = new JsonArray();
      json.add("fhirVersions", vl);
      vl.add(fhirversion);
      
      if (json.has("dependencies")) {
        JsonObject dep = json.getAsJsonObject("dependencies");
        if (dep.has("hl7.fhir.core")) {
          String v = VersionUtilities.getCurrentVersion(fhirversion);
          if (VersionUtilities.packageForVersion(v) != null) {
            dep.addProperty(VersionUtilities.packageForVersion(v), v);
          }
          dep.remove("hl7.fhir.core");
        }
      }
      pck.save(new FileOutputStream(f));
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
    fhirversions.add(fhirversion);
    NPMPackageGenerator npm = new NPMPackageGenerator(file, canonical, vpath, PackageType.IG, ig, date, fhirversions);
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
