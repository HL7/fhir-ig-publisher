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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.JsonUtilities;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

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
    private List<PublicationEntry> releases = new ArrayList<>();
    private List<PublicationEntry> candidates = new ArrayList<>();
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
      return JsonUtilities.str(entry, "category");
    }
  }

  private String path;
  private List<ImplementationGuideEntry> igs = new ArrayList<>();
  private JsonObject json;

  public IGRegistryMaintainer(String path) throws JsonSyntaxException, FileNotFoundException, IOException {
    this.path = path;
    if (path != null) {
      json = (JsonObject) new JsonParser().parse(TextFile.fileToString(path));
    }
  }

  public String removeTrailingSlash(String p) {
    return p.endsWith("/") ? p.substring(0,  p.length()-1) : p;
  }

  public ImplementationGuideEntry seeIg(String packageId, String canonical, String title, String category) {
    ImplementationGuideEntry ig = new ImplementationGuideEntry(packageId, canonical, title);
    igs.add(ig);
    ig.entry = JsonUtilities.findByStringProp(json.getAsJsonArray("guides"), "npm-name", ig.packageId);
    if (ig.entry == null) {
      ig.entry = new JsonObject();
      json.getAsJsonArray("guides").add(ig.entry);
      ig.entry.addProperty("name", ig.title);
      ig.entry.addProperty("category", Utilities.noString(category) ? "??" : category);
      ig.entry.addProperty("npm-name", ig.packageId);
      ig.entry.addProperty("description", "??");
      ig.entry.addProperty("authority", getAuthority(ig.canonical));
      ig.entry.addProperty("country", getCountry(ig.canonical));
      ig.entry.addProperty("history", getHistoryPage(ig.canonical));
      JsonArray a = new JsonArray();
      ig.entry.add("language", a);
      a.add(new JsonPrimitive("en"));
    } 
    if (!ig.entry.has("category") && !Utilities.noString(category)) {
      ig.entry.addProperty("category", category);      
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

  public void seeRelease(ImplementationGuideEntry ig, String name, String version, String fhirVersion, String path) {
    PublicationEntry p = new PublicationEntry(name, version, fhirVersion, path);
    ig.releases.add(p);
  }

  public void seeCandidate(ImplementationGuideEntry ig, String name, String version, String fhirVersion, String path) {
    PublicationEntry p = new PublicationEntry(name, version, fhirVersion, path);
    ig.candidates.add(p);
  }

  public void finish() throws JsonSyntaxException, FileNotFoundException, IOException {
    if (json != null) {
      for (ImplementationGuideEntry ig : igs) {
        if (!ig.entry.has("canonical") || !ig.entry.get("canonical").getAsString().equals(ig.canonical)) {
          ig.entry.remove("canonical");
          ig.entry.addProperty("canonical", ig.canonical);
        }
        if (!ig.entry.has("ci-build") || !ig.entry.get("ci-build").getAsString().equals(ig.cibuild)) {
          ig.entry.remove("ci-build");
          ig.entry.addProperty("ci-build", dropTrailingSlash(ig.cibuild));
        }      

        if (ig.entry.has("editions")) {
          ig.entry.remove("editions");
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
      TextFile.stringToFile(new GsonBuilder().setPrettyPrinting().create().toJson(json), path);
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
    if (!e.has("name") || !e.get("name").getAsString().equals(p.getName())) {
      e.remove("name");
      e.addProperty("name", p.getName());
    }
    if (!e.has("ig-version") || !e.get("ig-version").getAsString().equals(p.getName())) {
      e.remove("ig-version");
      e.addProperty("ig-version", p.getVersion());
    }
    if (!e.has("package") || !e.get("package").getAsString().equals(packageId+"#"+p.getVersion())) {
      e.remove("package");
      e.addProperty("package", packageId+"#"+p.getVersion());
    }
    if (p.getFhirVersion() != null) {
      if (!e.has("fhir-version") || e.getAsJsonArray("fhir-version").size() != 1 || !e.getAsJsonArray("fhir-version").get(0).getAsString().equals(p.getName())) {
        e.remove("fhir-version");
        JsonArray a = new JsonArray();
        e.add("fhir-version", a);
        a.add(new JsonPrimitive(p.getFhirVersion()));
      } 
    } else if(e.has("fhir-version")) {
      e.remove("fhir-version");
    }
    if (!e.has("url") || !e.get("url").getAsString().equals(p.getPath())) {
      e.remove("url");
      e.addProperty("url", dropTrailingSlash(p.getPath()));
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

}
