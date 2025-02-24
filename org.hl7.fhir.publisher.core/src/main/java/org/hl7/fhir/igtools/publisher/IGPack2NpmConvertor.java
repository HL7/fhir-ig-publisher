package org.hl7.fhir.igtools.publisher;

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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.hl7.fhir.convertors.factory.VersionConvertorFactory_10_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_14_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.Constants;
import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.model.Enumerations.FHIRVersion;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionResourceComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideManifestComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.ManifestResourceComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.SPDXLicense;
import org.hl7.fhir.r5.model.Reference;
import org.hl7.fhir.r5.utils.NPMPackageGenerator;
import org.hl7.fhir.r5.utils.NPMPackageGenerator.Category;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.PackageGenerator.PackageType;

public class IGPack2NpmConvertor {

  private FilesystemPackageCacheManager pcm;
  private Scanner scanner;
  private List<String> paths;

  private IniFile tini;
  private String packageId;
  private String versionIg;
  private String source;
  private String dest;
  private String license;
  private String website;
  
  public static void main(String[] args) throws FileNotFoundException, IOException, FHIRException {
    IGPack2NpmConvertor self = new IGPack2NpmConvertor();
    self.init();
    self.tini = new IniFile(Utilities.path("[tmp]", "v.ini"));
//    self.execute(new File("C:\\work\\org.hl7.fhir.us"));
//    self.execute(new File("C:\\work\\org.hl7.fhir.au"));
//    self.execute(new File("C:\\work\\org.hl7.fhir.intl"));
    self.execute(new File("F:\\fhir\\web"));
    self.execute(new File("F:\\fhir.org\\web\\guides"));
    System.out.println("Finished");
    System.out.println("Paths:");
    for (String s : self.paths)
      System.out.println(s);
  }

  
  public String getPackageId() {
    return packageId;
  }


  public void setPackageId(String packageId) {
    this.packageId = packageId;
  }


  public String getSource() {
    return source;
  }


  public void setSource(String source) {
    this.source = source;
  }


  public String getDest() {
    return dest;
  }


  public void setDest(String dest) {
    this.dest = dest;
  }

  public String getVersionIg() {
    return versionIg;
  }


  public void setVersionIg(String version) {
    this.versionIg = version;
  }


  public String getLicense() {
    return license;
  }


  public void setLicense(String license) {
    this.license = license;
  }


  public String getWebsite() {
    return website;
  }


  public void setWebsite(String website) {
    this.website = website;
  }


  public void execute() throws IOException {
    if (source == null)
      throw new IOException("A -source parameter is required");
    if (dest == null)
      throw new IOException("A -dest parameter is required");
    if (packageId == null)
      throw new IOException("A -packageId parameter is required");
    init();
    processValidatorPack(new File(source));
  }

  private void init() throws IOException {
    pcm = new FilesystemPackageCacheManager.Builder().build();
    scanner = new Scanner(System. in);
    paths = new ArrayList<String>();
  }

  private void execute(File folder) throws IOException {
    for (File f : folder.listFiles()) {
      if (f.isDirectory())
        execute(f);
      else if (f.getName().equals("validator.pack")) {
        processValidatorPack(f);
      }
    }
  }

  private void processValidatorPack(File f) throws IOException {
    System.out.println("Processing "+f.getAbsolutePath());
    try {
      Map<String, byte[]> files = loadZip(new FileInputStream(f));
      String version = determineVersion(files);
      if (Utilities.existsInList(version, "n/a", "3.1.0", "1.8.0")) {
        System.out.println("  version not supported");
      } else {
        ImplementationGuide ig = loadIg(files, version);
        String canonical = ig.getUrl(); /// ig.getUrl().substring(0, ig.getUrl().indexOf("/ImplementationGuide/"));
        determinePackageId(ig, canonical, f.getAbsolutePath());
        checkVersions(ig, version, f.getAbsolutePath());
        checkLicense(ig);

        System.out.println("  url = "+canonical+", version = "+ig.getVersion()+", fhirversion = "+ig.getFhirVersion()+", id = "+ig.getPackageId()+", license = "+ig.getLicense());

        for (String k : files.keySet()) {
          if (k.endsWith(".json"))
            ig.getManifest().addResource().setReference(convertToReference(k));
        }
        for (ImplementationGuideDefinitionResourceComponent rd : ig.getDefinition().getResource()) {
          ManifestResourceComponent ra = getMatchingResource(rd.getReference().getReference(), ig);
          if (ra != null) {
            ra.setIsExample(rd.getIsExample());
            throw new Error("What is this code doing?");
//            if (rd.hasExtension(" http://hl7.org/fhir/StructureDefinition/implementationguide-page")) {
//              ra.setRelativePath(rd.getExtensionString("http://hl7.org/fhir/StructureDefinition/implementationguide-page"));
//              rd.removeExtension(" http://hl7.org/fhir/StructureDefinition/implementationguide-page");
//            }
          }
        }

        if (files.containsKey("spec.internals"))
          loadSpecInternals(ig,  files.get("spec.internals"), "??", version, canonical, files);
        String destFile = dest != null ? dest : Utilities.path(FileUtilities.getDirectoryForFile(f.getAbsolutePath()), "package.tgz");
        String url = Utilities.noString(website) ? canonical : website;
        NPMPackageGenerator npm = new NPMPackageGenerator(ig.getPackageId(), destFile, canonical, url, PackageType.IG, ig, new Date(), null, false);
        

        npm.addFile(Category.RESOURCE, "ImplementationGuide-"+ig.getId()+".json", compose(ig, version));

        for (String k : files.keySet()) {
          if (k.endsWith(".json"))
            npm.addFile(Category.RESOURCE, k, files.get(k));
          else if (k.equals("schematron.zip")) {
            Map<String, byte[]> xfiles = loadZip(new ByteArrayInputStream(files.get(k)));
            for (String xk : xfiles.keySet())
              npm.addFile(Category.SCHEMATRON, xk, xfiles.get(xk));
          } else if (k.equals("spec.internals")) {  // hedging against changes in IG format
            npm.addFile(Category.OTHER, k, files.get(k));
          }
        }
        npm.finish();
        System.out.println("  saved to "+npm.filename());
        paths.add(npm.filename());
      }
    } catch (Throwable e) {
      System.out.println("  error: "+e.getMessage());
      e.printStackTrace();
    }
  }  
  


  private Reference convertToReference(String k) {
    k = k.substring(0, k.length()-5);
    return new Reference(k.substring(0, k.indexOf("-"))+'/'+k.substring(k.indexOf("-")+1));
  }

  private byte[] compose(ImplementationGuide ig, String version) throws IOException, FHIRException {
    if (version.startsWith("1.0")) {
      return new org.hl7.fhir.dstu2.formats.JsonParser().composeBytes(VersionConvertorFactory_10_50.convertResource(ig));
    } else if (version.startsWith("1.4")) {
      return new org.hl7.fhir.dstu2016may.formats.JsonParser().composeBytes(VersionConvertorFactory_14_50.convertResource(ig));
    } else if (version.startsWith("3.0") ) {
      return new org.hl7.fhir.dstu3.formats.JsonParser().composeBytes(VersionConvertorFactory_30_50.convertResource(ig));
    } else if (version.startsWith("4.0") ) {
      return new org.hl7.fhir.r4.formats.JsonParser().composeBytes(VersionConvertorFactory_40_50.convertResource(ig));
    } else if (version.equals(Constants.VERSION)) {
      return new org.hl7.fhir.r5.formats.JsonParser().composeBytes(ig);
    } else
      throw new FHIRException("Unsupported version "+version);
  }

  private void checkLicense(ImplementationGuide ig) throws IOException, FHIRException {
    if (!ig.hasLicense()) {
      if (source != null) {
        if (license == null)
          throw new IOException("A -license parameter is required, with a valid SPDX license code, or not-open-source"); 
        ig.setLicense(SPDXLicense.fromCode(license));
      }
    } else
      ig.setLicense(SPDXLicense.CC0_1_0);
    
  }

  private void loadSpecInternals(ImplementationGuide ig, byte[] bs, String version, String id, String canonical, Map<String, byte[]> files) throws Exception {
    SpecMapManager spm = new SpecMapManager(bs, id, version);
    ImplementationGuideManifestComponent man = ig.getManifest();
    man.setRendering(spm.getWebUrl(""));
    for (String s : spm.getPathUrls()) {
      if (s.startsWith(canonical)) {
        String r = s.equals(canonical) ? "" : s.substring(canonical.length() + 1);
        ManifestResourceComponent ra = getMatchingResource(r, ig);
        if (ra != null && !ra.hasRelativePath())
          ra.setRelativePath(spm.getPath(s, null, null, null));
      }
    }
    for (String s : spm.getImages()) {
      ig.getManifest().addImage(s);
    }
    for (String s : spm.getTargets()) {
      if (s.contains("#"))
        throw new Error("contains # in spec.internal");
      ig.getManifest().addPage().setName(s);
    }
    if (spm.getPages().size() > 0) 
      throw new Error("contains pages in spec.internal");

  }

  private ManifestResourceComponent getMatchingResource(String r, ImplementationGuide ig) {
    for (ManifestResourceComponent t : ig.getManifest().getResource()) 
      if (r.equals(t.getReference().getReference()))
        return t;
    return null;
  }

  private void checkVersions(ImplementationGuide ig, String version, String filename) throws FHIRException, IOException {
    if ("STU3".equals(ig.getFhirVersion()))
      ig.addFhirVersion(FHIRVersion._3_0_0);
    
    if (!ig.hasFhirVersion())
      ig.addFhirVersion(FHIRVersion.fromCode(version));
    else if (ig.getFhirVersion().size()>1) {
      throw new FHIRException("Can't create an IGPack for a multi-version IG");
    }
    else {
      boolean ok = false;
      for (Enumeration<FHIRVersion> v : ig.getFhirVersion()) {
        if (version.equals(v.primitiveValue()))
          ok = true;
      }
      if (!ok)
        throw new FHIRException("FHIR version mismatch: "+version +" vs "+ig.getFhirVersion().get(0));
    }
    
    if (!ig.hasVersion()) {
      if (packageId != null) {
        if (versionIg == null)
          throw new IOException("A -version parameter is required");
        ig.setVersion(versionIg);
      } else {
        String s = tini.getStringProperty("versions", ig.getUrl());
        while (Utilities.noString(s)) {
          System.out.print("Enter version for "+ig.getUrl()+": ");
          s = scanner.nextLine();
          s = s.trim();
        }
        tini.setStringProperty("versions", ig.getUrl(), s, null);
        ig.setVersion(s);
      }
    }
    
    for (ImplementationGuideDependsOnComponent d : ig.getDependsOn()) {
      if (!d.hasVersion()) {
        if (d.getUri().equals("http://hl7.org/fhir/us/core")) {
          d.setVersion("1.0.1");
        }
      }
      if (!d.hasPackageId()) {
        String pid = pcm.getPackageId(d.getUri());
        boolean post = pid == null;
        while (Utilities.noString(pid)) {
          System.out.println("Enter package-id for "+d.getUri()+" from "+filename+":");
          pid = scanner.nextLine().trim();
        }
        d.setPackageId(pid);
      }
    }
  }

  private void determinePackageId(ImplementationGuide ig, String canonical, String filename) throws IOException, FHIRException {
    if (packageId != null) {
      ig.setPackageId(packageId);
      return;
    }
    
    String pid = pcm.getPackageId(canonical);
    if (ig.hasPackageId()) {
      if (!ig.getPackageId().equals(pid))
        throw new FHIRException("package mismatch "+canonical+"="+ig.getPackageId()+" but cache has "+pid);
    } else {
      while (Utilities.noString(pid)) {
        System.out.println("Enter package-id for "+canonical+" from "+filename+":");
        pid = scanner.nextLine().trim();
      }
      ig.setPackageId(pid);      
    }
  }

  private ImplementationGuide loadIg(Map<String, byte[]> files, String version) throws FHIRException, IOException {
    String n = null;
    for (String k : files.keySet()) {
      if (k.startsWith("ImplementationGuide-"))
        if (n == null)
          n = k;
        else
          throw new FHIRException("Multiple Implementation Guides found");
    }
    if (n == null)
      throw new FHIRException("Multiple Implementation Guides found");
    byte[] b = files.get(n);
    if (version.startsWith("1.0")) {
      org.hl7.fhir.dstu2.model.Resource r = new org.hl7.fhir.dstu2.formats.JsonParser().parse(b);
      return (ImplementationGuide) VersionConvertorFactory_10_50.convertResource(r);
    } else if (version.startsWith("1.4")) {
      org.hl7.fhir.dstu2016may.model.Resource r = new org.hl7.fhir.dstu2016may.formats.JsonParser().parse(b);
      return (ImplementationGuide) VersionConvertorFactory_14_50.convertResource(r);
    } else if (version.startsWith("3.0") ) {
      org.hl7.fhir.dstu3.model.Resource r = new org.hl7.fhir.dstu3.formats.JsonParser().parse(b);
      return (ImplementationGuide) VersionConvertorFactory_30_50.convertResource(r);
    } else if (version.startsWith("4.0") ) {
      org.hl7.fhir.r4.model.Resource r = new org.hl7.fhir.r4.formats.JsonParser().parse(b);
      return (ImplementationGuide) VersionConvertorFactory_40_50.convertResource(r);
    } else if (version.equals(Constants.VERSION)) {
      org.hl7.fhir.r5.model.Resource r = new org.hl7.fhir.r5.formats.JsonParser().parse(b);
      return (ImplementationGuide) r;
    } else
      throw new FHIRException("Unsupported version "+version);
  }

  private String determineVersion(Map<String, byte[]> files) {
    byte[] b = files.get("version.info");
    if (b == null)
      return "n/a";
    String s = new String(b);
    s = Utilities.stripBOM(s).trim();
    while (s.charAt(0) != '[')
      s = s.substring(1);
    byte[] bytes = {};
    try {
      bytes = s.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {

    }
    ByteArrayInputStream bs = new ByteArrayInputStream(bytes);
    IniFile ini = new IniFile(bs);
    String v = ini.getStringProperty("FHIR", "version");
    if (v == null)
      throw new Error("unable to determine version from "+new String(bytes));
    if (v.startsWith("3.0"))
      v = "3.0.2";
    if (v.startsWith("4.0"))
      v = "4.0.1";
    return v;
  }

  protected Map<String, byte[]> loadZip(InputStream stream) throws IOException {
    Map<String, byte[]> res = new HashMap<String, byte[]>();
    ZipInputStream zip = new ZipInputStream(stream);
    ZipEntry ze;
    while ((ze = zip.getNextEntry()) != null) {
      final String entryName = ze.getName();

      if (entryName.contains("..")) {
        throw new IOException("Entry with an illegal name: " + entryName);
      }


      int size;
      byte[] buffer = new byte[2048];

      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      BufferedOutputStream bos = new BufferedOutputStream(bytes, buffer.length);

      while ((size = zip.read(buffer, 0, buffer.length)) != -1) {
        bos.write(buffer, 0, size);
      }
      bos.flush();
      bos.close();
      res.put(entryName, bytes.toByteArray());

      zip.closeEntry();
    }
    zip.close();
    return res;
  }

}
