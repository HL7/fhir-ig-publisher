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


import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonString;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;

public class IGRegistryMaintainer {

  public class PublicationEntry {
    private String name;
    private String version;
    private String fhirVersion;
    private String path;
    
    public PublicationEntry(String name, String version, String fhirVersion, String path) {
      super();
      this.name = name;
      this.version = version;
      this.fhirVersion = fhirVersion;
      this.path = removeTrailingSlash(path);
    }
    
    public String getName() {
      return name;
    }
    
    public String getVersion() {
      return version;
    }
    
    public String getFhirVersion() {
      return fhirVersion;
    }
    
    public String getPath() {
      return path;
    }
  }
  
  public class ImplementationGuideEntry {
    private String packageId;
    private String canonical;
    private String title;
    private String cibuild;
    private boolean withdrawn;
    private String withDrawalDate;
    private List<PublicationEntry> releases = new ArrayList<>();
    private List<PublicationEntry> candidates = new ArrayList<>();
    private List<String> products = new ArrayList<>();
    private JsonObject entry;
    
    public ImplementationGuideEntry(String packageId, String canonical, String title) {
      super();
      this.packageId = packageId;
      if (canonical == null) {
        throw new Error("No canonical");
      }
      if (canonical == null || canonical.endsWith("/")) {
        throw new Error("Canonical ends with /: "+canonical);
      }
      this.canonical = canonical;
      this.title = title;
    }

    public String getPackageId() {
      return packageId;
    }

    public String getCanonical() {
      return canonical;
    }

    public String getTitle() {
      return title;
    }

    public List<PublicationEntry> getReleases() {
      return releases;
    }

    public List<PublicationEntry> getCandidates() {
      return candidates;
    }

    public String getCategory() {
      return entry.asString("category");
    }

    public List<String> getProducts() {
      return products;
    }
  }

  private String path;
  private List<ImplementationGuideEntry> igs = new ArrayList<>();
  private JsonObject json;

  public IGRegistryMaintainer(String path) throws FileNotFoundException, IOException {
    this.path = path;
    if (path != null) {
      json = JsonParser.parseObject(FileUtilities.fileToString(path));
    }
  }

  public String removeTrailingSlash(String p) {
    return p.endsWith("/") ? p.substring(0,  p.length()-1) : p;
  }

  public ImplementationGuideEntry seeIg(String packageId, String canonical, String title, String category) {
    ImplementationGuideEntry ig = new ImplementationGuideEntry(packageId, canonical, title);
    igs.add(ig);
    ig.getProducts().add("fhir");
    ig.entry = json.getJsonArray("guides").findByStringProp("npm-name", ig.packageId);
    if (ig.entry == null) {
      ig.entry = new JsonObject();
      json.getJsonArray("guides").add(ig.entry);
      ig.entry.add("name", ig.title);
      ig.entry.add("category", Utilities.noString(category) ? "??" : category);
      ig.entry.add("npm-name", ig.packageId);
      ig.entry.add("description", "??");
      ig.entry.add("authority", getAuthority(ig.canonical));
      ig.entry.add("country", getCountry(ig.canonical));
      ig.entry.add("history", getHistoryPage(ig.canonical));
      ig.entry.forceArray("product").add("fhir");
      JsonArray a = new JsonArray();
      ig.entry.add("language", a);
      a.add(new JsonString("en"));
    } else {
      for (String s : ig.entry.forceArray("product").asStrings()) {
        if (!ig.products.contains(s)) {
          ig.products.add(s);
        }
      }
    }
    if (!ig.entry.has("category") && !Utilities.noString(category)) {
      ig.entry.add("category", category);      
    }
    return ig;
  }

  public void seeCiBuild(ImplementationGuideEntry ig, String path, String source) {
    if (path.startsWith("https://build.fhir.org/ig")) {
      System.out.println("Error in "+source+":path to build.fhir.org should not use https://");
      path = path.replace("https://build.fhir.org/ig", "http://build.fhir.org/ig");
    }
    ig.cibuild = removeTrailingSlash(path);
  }

  public void withdraw(ImplementationGuideEntry ig, String name, String version, String fhirVersion, String path) {
    ig.releases.clear();
    ig.withdrawn = true;
  }
  
  public void seeRelease(ImplementationGuideEntry ig, String name, String version, String fhirVersion, String path) {
    PublicationEntry p = new PublicationEntry(name, version, fhirVersion, path);
    ig.releases.add(p);
  }

  public void seeCandidate(ImplementationGuideEntry ig, String name, String version, String fhirVersion, String path) {
    PublicationEntry p = new PublicationEntry(name, version, fhirVersion, path);
    ig.candidates.add(p);
  }

  public void finish() throws FileNotFoundException, IOException {
    if (json != null) {
      for (ImplementationGuideEntry ig : igs) {
        if (!ig.entry.has("canonical") || !ig.entry.get("canonical").asString().equals(ig.canonical)) {
          ig.entry.remove("canonical");
          ig.entry.add("canonical", ig.canonical);
        }
        if (!ig.entry.has("ci-build") || !ig.entry.get("ci-build").asString().equals(ig.cibuild)) {
          ig.entry.remove("ci-build");
          ig.entry.add("ci-build", dropTrailingSlash(ig.cibuild));
        }      

        if (ig.entry.has("editions")) {
          ig.entry.remove("editions");
        }
        ig.entry.remove("product");
        for (String s : ig.products) {
          ig.entry.forceArray("product").add(s);
        }
        if (ig.withdrawn) {
          ig.entry.set("withdrawn", true);          
        } else if (ig.entry.has("withdrawn")) {
          ig.entry.remove("withdrawn");
        }
        if (ig.withDrawalDate != null) {
          ig.entry.set("withDrawalDate", ig.withDrawalDate);          
        } else if (ig.entry.has("withDrawalDate")) {
          ig.entry.remove("withDrawalDate");
        }
        
        JsonArray a = new JsonArray();
        ig.entry.add("editions", a);
        if (!ig.getCandidates().isEmpty()) {
          PublicationEntry p = ig.getCandidates().get(0);
          a.add(makeEdition(p, ig.packageId));
        }
        for (PublicationEntry p : ig.getReleases()) {
          a.add(makeEdition(p, ig.packageId));
        }
      }
      JsonParser.compose(json, new FileOutputStream(path), true);
    }
    for (ImplementationGuideEntry ig : igs) {
      System.out.println(ig.packageId+" ("+ig.canonical+"): "+ig.title+" @ "+ig.cibuild);
      for (PublicationEntry p : ig.getReleases()) {
        System.out.println("  release: "+p.name+" "+p.version+"/"+p.fhirVersion+" @ "+p.path);
      }
      if (!ig.getCandidates().isEmpty()) {
        PublicationEntry p = ig.getCandidates().get(0);
        System.out.println("  candidate: "+p.name+" "+p.version+"/"+p.fhirVersion+" @ "+p.path);
      }
    }    
  }
  
  private JsonObject makeEdition(PublicationEntry p, String packageId) {
    JsonObject e = new JsonObject();
    if (!e.has("name") || !e.get("name").asString().equals(p.getName())) {
      e.remove("name");
      e.add("name", p.getName());
    }
    if (!e.has("ig-version") || !e.get("ig-version").asString().equals(p.getName())) {
      e.remove("ig-version");
      e.add("ig-version", p.getVersion());
    }
    if (!e.has("package") || !e.get("package").asString().equals(packageId+"#"+p.getVersion())) {
      e.remove("package");
      e.add("package", packageId+"#"+p.getVersion());
    }
    if (p.getFhirVersion() != null) {
      if (!e.has("fhir-version") || e.getJsonArray("fhir-version").size() != 1 || !e.getJsonArray("fhir-version").get(0).asString().equals(p.getName())) {
        e.remove("fhir-version");
        JsonArray a = new JsonArray();
        e.add("fhir-version", a);
        a.add(new JsonString(p.getFhirVersion()));
      } 
    } else if(e.has("fhir-version")) {
      e.remove("fhir-version");
    }
    if (!e.has("url") || !e.get("url").asString().equals(p.getPath())) {
      e.remove("url");
      e.add("url", dropTrailingSlash(p.getPath()));
    }
    return e;
  }

  private String dropTrailingSlash(String p) {
    return p != null && p.endsWith("/") ? p.substring(0, p.length()-1) : p;
  }

  private String getHistoryPage(String canonical) {
    return Utilities.pathURL(canonical, "history.html");
  }

  private String getCountry(String canonical) {
    if (canonical.contains("hl7.org")) {
      if (canonical.contains("/uv/"))
        return "uv";
      if (canonical.contains("/us/"))
        return "us";
    }
    return "??";
  }

  private String getAuthority(String canonical) {
    if (canonical.contains("hl7.org"))
      return "HL7";
    return "??";
  }

  public String getPath() {
    return path;
  }

  public void withdraw(ImplementationGuideEntry rc, PackageListEntry plVer) {
    rc.withdrawn = true;
    rc.withDrawalDate = plVer.date();
  }

}
