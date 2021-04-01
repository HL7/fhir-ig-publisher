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


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.JsonTrackingParser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class BallotChecker {

  private String folder;
  JsonObject pl = null;
  
  public BallotChecker(String folder) throws IOException {
    this.folder = folder;
    String plfn = Utilities.path(folder, "package-list.json");
    File f = new File(plfn);
    if (f.exists()) {
      try {
        pl = JsonTrackingParser.parseJson(f);
      } catch (Exception e) {
      }
    }
  }

  public String check(String canonical, String packageId, String version, String historyPage, String fhirVersion) throws IOException { 
    if (!canonical.contains("hl7.org") && !canonical.contains("fhir.org"))
      return "n/a - not an HL7.org or FHIR.org implementation guide\r\n";
    
    List<String> errors = new ArrayList<String>();
    if (!Utilities.existsInList(historyPage, Utilities.pathURL(canonical, "history.html"))) {
      errors.add("History Page '"+Utilities.escapeXml(historyPage)+"' is wrong (ig.json#paths/history) - must be '"+Utilities.escapeXml(Utilities.pathURL(canonical, "history.html"))+"'");
    }
    if (new File(Utilities.path(folder, "output", "package-list.json")).exists()) {
      errors.add("There is a package-list.json in the output folder - cannot publish while it is there");      
    }
      
    if (pl == null) {
      String plfn = Utilities.path(folder, "package-list.json");
      File f = new File(plfn);
      if (!f.exists()) {
        errors.add("package-list.json: file not found in "+folder);
      } else {
        try {
          JsonTrackingParser.parseJson(f);
        } catch (Exception e) {
          errors.add("package-list.json: " +Utilities.escapeXml(e.getMessage()));
        }
      }
    }
    
    JsonObject json = pl;
    if (json != null) {
      if (!json.has("package-id"))
        errors.add("package-list.json: No Package Id");
      else if (!json.get("package-id").getAsString().equals(packageId))
        errors.add("package-list.json: package-id is wrong - is '"+Utilities.escapeXml(json.get("package-id").getAsString())+"' should be '"+Utilities.escapeXml(packageId)+"'");
      
      if (!json.has("canonical"))
        errors.add("package-list.json: No Canonical URL");
      else if (!json.get("canonical").getAsString().equals(canonical))
        errors.add("package-list.json: canonical is wrong - is '"+Utilities.escapeXml(json.get("canonical").getAsString())+"' should be '"+Utilities.escapeXml(canonical)+"'");

      if (!json.has("category")) {
        errors.add("package-list.json: No category entry for the registry category (talk to FHIR product director on #IG Creation for assistance). Note: existing IGs already have a category in the <a href=\""+Utilities.pathURL(canonical, "package-list.json")+"\">existing package-list.json</a> - update your package-list.json from there");
      }

      JsonArray list = json.getAsJsonArray("list");
      boolean found = false;
      for (JsonElement n : list) {
        JsonObject o = (JsonObject) n;
        if (o.has("version") && o.get("version").getAsString().equals(version)) {
          found = true;
          if (!o.has("desc") && !o.has("descmd") && !o.has("changes")) {
            errors.add("package-list.json entry for v"+Utilities.escapeXml(version)+": must have a 'desc' / 'descmd' or 'changes' (or both)");
          }
          if (!o.has("date")) {
            errors.add("package-list.json entry for v"+Utilities.escapeXml(version)+": must have a 'date' (though the value doesn't matter)");
          }
          if (!o.has("status")) {
            errors.add("package-list.json entry for v"+Utilities.escapeXml(version)+": must have a 'status' that describes the ballot status");
          }
          if (!o.has("sequence")) {
            errors.add("package-list.json entry for v"+Utilities.escapeXml(version)+": must have a 'sequence' that describes the ballot goal");
          }
          if (!o.has("fhirversion")) {
            errors.add("package-list.json entry for v"+Utilities.escapeXml(version)+": must have a 'fhirversion' that specifies the FHIR version ("+Utilities.escapeXml(fhirVersion)+")");
          } else if (!o.get("fhirversion").getAsString().equals(fhirVersion)) {
            errors.add("package-list.json entry for v"+Utilities.escapeXml(version)+": must have a 'fhirversion' with the right value - is '"+Utilities.escapeXml(o.get("fhirversion").getAsString())+"', should be '"+Utilities.escapeXml(fhirVersion)+"'");
          }
          if (!o.has("path")) {
            errors.add("package-list.json entry for v"+Utilities.escapeXml(version)+": must have a 'path' where it will be published");
          } else if (!o.get("path").getAsString().startsWith(canonical)) {
            errors.add("package-list.json entry for v"+Utilities.escapeXml(version)+": must have a 'path' that starts with the canonical (is '"+Utilities.escapeXml(o.get("path").getAsString())+"', should start with '"+Utilities.escapeXml(canonical)+"'");
          }
          if (errors.size() == 0) {
            errors.add("All OK (path - "+Utilities.escapeXml(o.get("path").getAsString())+")");
          }            
        }
      }
      if (!found) {
        errors.add("package-list.json: No entry found for version "+Utilities.escapeXml(version));
      }
    }
    if (errors.size() == 1) {
      return Utilities.escapeXml(errors.get(0));
    } else {
      StringBuilder b = new StringBuilder();
      b.append("<ul>\r\n");
      for (String s : errors) {
        b.append("  <li>"+s+"</li>\r\n");
      }
      b.append("</ul>\r\n");
      return b.toString();
    }              
  }

  public JsonObject getPackageList() {
    return pl;
  }

}
