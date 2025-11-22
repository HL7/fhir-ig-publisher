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


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.IContextResourceLoader;
import org.hl7.fhir.r5.model.Constants;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonPrimitive;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.BasePackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.ToolsVersion;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;

/**
 * TODO: should we call versionless references ok? not sure....
 * (Grahame, 31-Oct 2019)
 * 
 * @author graha
 *
 */
public class SpecMapManager {


  public enum SpecialPackageType {
    Simplifier, PhinVads, Vsac, Examples, DICOM, FACADE
  }

  private JsonObject spec;
  private JsonObject paths;
  private JsonObject pages;
  private JsonArray targets;
  private JsonArray images;
  private String base; // canonical, versionless
  private String base2; // versioned  
  private String name;
  private Set<String> targetSet = new HashSet<String>();
  private Set<String> imageSet = new HashSet<String>();
  private String version;
  private NpmPackage pi;
  private SpecialPackageType special;
  private SpecMapManager wrapped;
  private int key;

  private String auth;
  private String realm;
  private String npmVId;
  private IContextResourceLoader loader;
  private JsonObject r5ExampleMap;
  
  private SpecMapManager() {
    
  }
  
  public SpecMapManager(String npmName, String vid, String igVersion, String toolVersion, String buildId, Calendar genDate, String webUrl) {
    spec = new JsonObject();
    if (npmName != null)
      spec.add("npm-name", npmName);
    this.npmVId = vid;
    spec.add("ig-version", igVersion);
    spec.add("tool-version", toolVersion);
    spec.add("tool-build", buildId);
    spec.add("webUrl", webUrl);
    if (genDate != null) {
      spec.add("date", new SimpleDateFormat("yyyy-MM-dd", new Locale("en", "US")).format(genDate.getTime()));
      spec.add("date-time", new SimpleDateFormat("yyyyMMddhhmmssZ", new Locale("en", "US")).format(genDate.getTime()));
    }
    paths = new JsonObject();
    spec.add("paths", paths);
    pages = new JsonObject();
    spec.add("pages", pages);
    targets = new JsonArray();
    spec.add("targets", targets);
    images = new JsonArray();
    spec.add("images", images);
  }

  public SpecMapManager(byte[] bytes, String vid, String version) throws IOException {
    this.version = version;
    this.npmVId = vid;
    try {
      spec = JsonParser.parseObject(bytes);
    } catch (Exception e) {
      spec = new JsonObject();
    }
    base = spec.asString("webUrl");
    paths = spec.forceObject("paths");
    pages = spec.forceObject("pages");
    targets = spec.forceArray("targets");
    images = spec.forceArray("images");
    if (targets != null)
      for (String e : targets.asStrings()) {
        if (e != null){
          targetSet.add(e);
        }
    }
    if (paths != null) {
      for (JsonProperty p : paths.getProperties()) {
        targetSet.add(p.getValue().asString());
      }
    }
    if (images != null)
      for (String e : images.asStrings()) {
        imageSet.add(e);
    }
  }

  public static SpecMapManager fromPackage(NpmPackage pi) throws IOException {
    if (pi.hasFile("other", "spec.internals")) {
      return new SpecMapManager(FileUtilities.streamToBytes(pi.load("other", "spec.internals")), pi.vid(), pi.fhirVersion());      
    } else {
      return new SpecMapManager();
    }
  }


  public void path(String url, String path) {
    paths.set(url, path);
  }

  public void save(String filename) throws IOException {
    String json = JsonParser.compose(spec, true);
    FileUtilities.stringToFile(json, filename);    
  }

  public String getVersion() throws FHIRException {
    if (spec.has("tool-version")) 
      return str(spec, "ig-version");
    else
      return version;
  }

  public String getNpmName() throws FHIRException {
    return strOpt(spec, "npm-name", "fhir");
  }
  
  public String getBuild() throws FHIRException {
    if (spec.has("tool-build")) 
      return str(spec, "tool-build");
    else if (spec.has("build"))
      return str(spec, "build");
    else
      return null;
  }

  /**
   * The official location of the IG, when built (= canonical URL in the build file)
   * 
   * @param url
   * @return
   * @throws Exception
   */
  public String getWebUrl(String url) throws Exception {
    return str(spec, "webUrl");
  }

  public String getPath(String url, String def, String rt, String id) throws FHIRException {
    if (url == null) {
      return null;
    }
    if (paths.has(url) && !("http://hl7.org/fhir/R5".equals(base) && url.startsWith("http://terminology.hl7.org"))) {
      String p = strOpt(paths, url);
      if (!Utilities.isAbsoluteUrl(p) ) {
        p = Utilities.pathURL(base2 == null ? base : base2, p);
      }
      return p;      
    }
    String path = getSpecialPath(url);
    if (path != null) {
      return path;
    }
    if (def != null) {
      return def;
    }
    if (special != null) {
      switch (special) {
      case FACADE:
        if (wrapped != null && url.startsWith(base)) {
          return wrapped.getPath(url, def, rt, id); 
        } else {
          return null;
        }
      case Simplifier: return "https://simplifier.net/resolve?scope="+pi.name()+"@"+pi.version()+"&canonical="+url;
      case PhinVads:  
        try {
          if (url.startsWith(base)) {
            final String fileName = Utilities.urlTail(url) + "json";
  
            if ((isValidFilename(fileName) && pi.hasFile("package", fileName))
                    || pi.hasCanonical(url)) {
              return "phinvads_broken_link.html"; 
            } else {
              return null;
            }
              
          } else 
            break;
        } catch (IOException e) {
          return null;
        }
      case Vsac: if (url.contains("cts.nlm.nih.gov")) {
        return url.replace("http://cts.nlm.nih.gov/fhir/ValueSet/", "https://vsac.nlm.nih.gov/valueset/")+"/expansion";
      } else {
        return null;
      }
      case Examples:
        if ("hl7.fhir.r5.examples".equals(pi.name())) {
          if (r5ExampleMap == null) {
            ClassLoader classLoader = HierarchicalTableGenerator.class.getClassLoader();
            InputStream map = classLoader.getResourceAsStream("r5-examples.json");
            try {
              r5ExampleMap = JsonParser.parseObject(map);
            } catch (IOException e) {
              throw new FHIRException(e);
            }
          }
          JsonObject n = r5ExampleMap.getJsonObject(rt+"/"+id);
          if (n != null) {
            return Utilities.pathURL(str(spec, "webUrl"), n.str("path")+".html");
          }
        } else if (rt != null && url.startsWith(base)) {
          return Utilities.pathURL(str(spec, "webUrl"), rt.toLowerCase() + "-" + id.toLowerCase() + ".html");
        }
      case DICOM:
        try {
          final String fileName = Utilities.urlTail(url) + "json";

          if ((isValidFilename(fileName) && pi.hasFile("package", fileName))
                  || pi.hasCanonical(url)) {
            return url;
          } else {
            return null;
          }
        } catch (IOException e) {
          return null;
        }
      }
    }
    if (url.matches(Constants.URI_REGEX)) {
      int cc = 0;
      int cursor = url.length()-1;
      while (cursor > 0 && cc < 2) {
        if (url.charAt(cursor) == '/') {
          cc++;
        }
        cursor--;
      }
      String u = url.substring(cursor+2);
      return strOpt(paths, u);
      
    }
    
    return null;
  }

  public String getPath(String type, String id) {
    String url = Utilities.pathURL(base, type, id);
    return paths.has(url) ? Utilities.pathURL(spec.asString("webUrl"), paths.asString(url)) : null;
  }

  public boolean isValidFilename(String filename) {
    try {
      Paths.get(filename);
    } catch (InvalidPathException e) {
      return false;
    }
    return true;
  }

  // hack around things missing in spec.internals 
  private String getSpecialPath(String url) {
    if ("http://hl7.org/fhir/ValueSet/iso3166-1-3".equals(url)) {
      return Utilities.pathURL(base, "valueset-iso3166-1-3.html");
    }
    if ("http://hl7.org/fhir/ValueSet/iso3166-1-2".equals(url)) {
      return Utilities.pathURL(base, "valueset-iso3166-1-2.html");
    }
    return null;
  }

  public List<String> getPathUrls() {
    List<String> res = new ArrayList<String>();
    for (JsonProperty e : paths.getProperties()) 
      res.add(e.getName());
    return res;
  }

  public String getPage(String title) throws Exception {
    return strOpt(pages, title);
  }

  private String str(JsonObject obj, String name) throws FHIRException {
    if (!obj.has(name))
      throw new FHIRException("Property "+name+" not found");
    if (!(obj.get(name) instanceof JsonPrimitive))
      throw new FHIRException("Property "+name+" not a primitive");
    return obj.asString(name);
  }

  private String strOpt(JsonObject obj, String name) throws FHIRException {
    if (!obj.has(name))
      return null;
    if (!(obj.get(name) instanceof JsonPrimitive))
      throw new FHIRException("Property "+name+" not a primitive");
    return obj.asString(name);
  }

  private String strOpt(JsonObject obj, String name, String def) throws FHIRException {
    if (!obj.has(name))
      return def;
    if (!(obj.get(name) instanceof JsonPrimitive))
      throw new FHIRException("Property "+name+" not a primitive");
    return obj.asString(name);
  }

  public void page(String title, String url) {
    pages.add(title, url);    
  }

  public String getBase() {
    return base;
  }

  public void setBase(String base) {
    this.base = base;
  }

  public String getBase2() {
    return base2;
  }

  public void setBase2(String base2) {
    this.base2 = base2;
  }

  public void target(String tgt) {
    if (!targetSet.contains(tgt)) {
      targetSet.add(tgt);
      targets.add(tgt);
    }
  }
  
  public void image(String tgt) {
    if (!imageSet.contains(tgt)) {
      imageSet.add(tgt);
      images.add(tgt);
    }
  }
  
  public boolean hasTarget(String tgt) {
    if (wrapped != null) {
      return wrapped.hasTarget(tgt);
    }
    if (hasTarget1(tgt))
      return true;
    else 
      return hasTarget2(tgt);
  }
  
  public boolean hasTarget1(String tgt) {
    if (base != null && tgt.startsWith(base+"/"))
      tgt = tgt.substring(base.length()+1);
    else if (base != null && tgt.startsWith(base))
      tgt = tgt.substring(base.length());
    else
      return false;
    if (tgt.contains("#"))
      tgt = tgt.substring(0, tgt.indexOf("#"));
    if (targetSet.contains(tgt))
      return true;
    if (Utilities.existsInList(tgt, "qa.html", "toc.html"))
      return true;
    if (targetSet.contains(tgt))
      return true;
    if (base != null && paths.has(base+"/"+tgt))
      return true;
    return false;  
  }

  public boolean hasTarget2(String tgt) {
    if (base2 == null)
      return false;
    
    if (tgt.startsWith(base2+"/"))
      tgt = tgt.substring(base2.length()+1);
    else if (tgt.startsWith(base2))
      tgt = tgt.substring(base2.length());
    else
      return false;
    if (tgt.contains("#"))
      tgt = tgt.substring(0, tgt.indexOf("#"));
    if (targetSet.contains(tgt))
      return true;
    if (Utilities.existsInList(tgt, "qa.html", "toc.html"))
      return true;
    if (targetSet.contains(tgt))
      return true;
    if (paths.has(base2+"/"+tgt))
      return true;
    return false;  
  }

  public boolean hasImage(String tgt) {
    if (tgt == null) {
      return false;
    }
    if (tgt.startsWith(base+"/"))
      tgt = tgt.substring(base.length()+1);
    else if (tgt.startsWith(base))
      tgt = tgt.substring(base.length());
    else
      return false;
    if (imageSet.contains(tgt))
      return true;
    return false;  
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Set<String> getTargets() {
    return targetSet;
  }

  public Set<String> getImages() {
    return imageSet;
  }

  public List<String> getPages() {
    List<String> res = new ArrayList<String>();
    for (JsonProperty e : pages.getProperties()) 
      res.add(e.getName());
    return res;
  }

  public static SpecMapManager createSpecialPackage(NpmPackage pi, BasePackageCacheManager pcm) throws FHIRException, IOException {
    SpecMapManager res = new SpecMapManager(pi.name(), pi.vid(), pi.version(), ToolsVersion.TOOLS_VERSION_STR, null, null, pi.url());
    if (pi.name().equals("us.cdc.phinvads")) {
      res.special = SpecialPackageType.PhinVads;
    } else if (pi.name().equals("us.nlm.vsac")) {
      res.special = SpecialPackageType.Vsac;
//    } else if (pi.name().equals("hl7.fhir.us.core.3.1.1")) {
//      res.special = SpecialPackageType.FACADE;
//      if (pcm != null) {
//        NpmPackage npm = pcm.loadPackage("hl7.fhir.us.core#3.1.1"); 
//        res.wrapped = new SpecMapManager(TextFile.streamToBytes(npm.load("other", "spec.internals")), npm.fhirVersion());
//        res.wrapped.setName(npm.name());
//        res.wrapped.setBase2(PackageHacker.fixPackageUrl(npm.getWebLocation()));
//        res.wrapped.setBase(npm.canonical());
//      }
    } else if (pi.name().equals("fhir.dicom")) {
      res.special = SpecialPackageType.DICOM;
    } else if (pi.name().startsWith("hl7.fhir.") && pi.name().endsWith(".examples") ) {
      res.special = SpecialPackageType.Examples;
    } else if (!pi.name().startsWith("hl7.fhir.us.core.v")) {
      res.special = SpecialPackageType.Simplifier;  
    }
    res.pi = pi;
    return res;
  }

  public Set<String> listTargets() {
    return targetSet;
  }

  public SpecialPackageType getSpecial() {
    return special;
  }

  @Override
  public String toString() {
    return "SpecMapManager " + name+" for "+npmVId+"#"+version + ", "+base+" & "+base2;
  }

  public int getKey() {
    return key;
  }

  public void setKey(int key) {
    this.key = key;
  }

  public String getAuth() {
    return auth;
  }

  public void setAuth(String auth) {
    this.auth = auth;
  }

  public String getRealm() {
    return realm;
  }

  public void setRealm(String realm) {
    this.realm = realm;
  }

  public String getNpmVId() {
    return npmVId;
  }

  public boolean isCore() {
    return "hl7.fhir.core".equals(getNpmName());
  }

  public NpmPackage getNpm() {
    return pi;
  }

  public void setNpm(NpmPackage pi) {
    this.pi = pi;
  }

  public IContextResourceLoader getLoader() {
    return loader;
  }

  public void setLoader(IContextResourceLoader loader) {
    this.loader = loader;
  }

  
}
