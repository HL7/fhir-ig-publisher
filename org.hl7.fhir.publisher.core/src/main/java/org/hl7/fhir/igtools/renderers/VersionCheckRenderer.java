package org.hl7.fhir.igtools.renderers;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.hl7.fhir.igtools.publisher.PastProcessHackerUtilities;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.JSONUtil;
import org.hl7.fhir.utilities.json.JsonTrackingParser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class VersionCheckRenderer {

  private String packageVersion;
  private String igVersion;
  private JsonObject packageList;
  private String canonical;
  
  public VersionCheckRenderer(String packageVersion, String igVersion, JsonObject packageList, String canonical) {
    super();
    this.packageVersion = packageVersion;
    this.igVersion = igVersion;
    this.packageList = packageList;
    this.canonical = canonical;
  }
  
  public String generate() {
    if (packageVersion == null) {
      return error("No version specified");
    } else if ("current".equals(packageVersion)) {
      return packageVersion+": "+error("Cannot publish while version is 'current'");
    } else if (!VersionUtilities.isSemVer(packageVersion)) {
      return packageVersion+": "+error("Version does not conform to semver rules");
    } else if (!packageVersion.equals(igVersion)) {
      return packageVersion+": "+error("Mismatch between package version and IG version ("+igVersion+")");
    } else if (packageList == null) {
      return packageVersion+": "+error("no package-list.json - the guide is not ready for publishing");
    } else {
      JsonObject ver = getVersion(packageList);
      if (ver == null) {
        return packageVersion+": "+error("No entry in the package-list.json file for this version");
      } else if (!ver.has("path")) {
        return packageVersion+": "+error("package-list.json has no path for this version");
      } else {
        CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder(". ");
        b.append(packageVersion+" = ok. Step <code>"+ver.get("status").getAsString()+"</code> in sequence <code>"+ver.get("sequence").getAsString()+"</code>, to be published at "+ver.get("path").getAsString()+" (subdir = "+subdir(PastProcessHackerUtilities.actualUrl(canonical), JSONUtil.str(ver, "path"))+")");  
        JsonObject pubPl = getPublishedPackageList();
        if (pubPl == null) {
          if (packageVersion.startsWith("0.")) {
            b.append("This IG does not appear to have been published yet");        
          } else {
            b.append(error("This IG does not appear to have been published yet, so should not have a version >=1.0.0"));        
          }
        } else {
          if (isPublished(pubPl, ver.get("path").getAsString())) {
            b.append(error("The version "+packageVersion+" has already been published"));        
          } 
          if (!JSONUtil.str(packageList, "package-id").equals(JSONUtil.str(pubPl, "package-id"))) {
            b.append(error("Package-id mismatch between provided and published package-list.json files: "+JSONUtil.str(packageList, "package-id")+" vs "+JSONUtil.str(pubPl, "package-id")));        
          }
          if (!JSONUtil.str(packageList, "canonical").equals(JSONUtil.str(pubPl, "canonical"))) {
            b.append(error("canonical mismatch between provided and published package-list.json files: "+JSONUtil.str(packageList, "canonical")+" vs "+JSONUtil.str(pubPl, "canonical")));        
          }
        }
        if (!ver.get("path").getAsString().startsWith(canonical) && !ver.get("path").getAsString().startsWith(PastProcessHackerUtilities.actualUrl(canonical))) {
          b.append(error("package-list.json path for this version does not start with the canonical URL ("+ver.get("path").getAsString()+" vs "+canonical+")"));
        } 
        String mostRecent = packageVersion;
        for (JsonObject o : JSONUtil.objects(packageList, "list")) {
          if (o.has("version") && !"current".equals(o.get("version").getAsString()) && VersionUtilities.isThisOrLater(mostRecent, o.get("version").getAsString())) {
            mostRecent = o.get("version").getAsString();
          }
        }
        if (!mostRecent.equals(packageVersion)) {
          b.append(error("Version is older than "+mostRecent));
        }
        if (!ver.has("status")) {
          b.append(error("package-list.json has no status for this version"));
        }
        if (!ver.has("sequence")) {
          b.append(error("package-list.json has no sequence for this version"));
        }
        for (JsonObject o : JSONUtil.objects(packageList, "list")) {
          if (!o.has("desc") && !o.has("descmd")) {
            b.append(error("package-list.json has no description version "+JSONUtil.str(o, "ver")));            
          }
          if (o.has("descmd") && JSONUtil.str(o, "descmd").contains("'")) {
            b.append(error("package-list.json has a descmd that contains ' for version "+JSONUtil.str(o, "ver")));            
          }
        }
        return b.toString();
      }
    }
  }

  private String subdir(String canonical, String path) {
    return path.startsWith(canonical) && path.length() > canonical.length() + 1 ? path.substring(canonical.length()+1) : "??";
  }

  private JsonObject getPublishedPackageList() {
    try {
      JsonObject json = JsonTrackingParser.fetchJson(Utilities.pathURL(PastProcessHackerUtilities.actualUrl(canonical), "package-list.json"));
      return json;
    } catch (Exception e) {
      System.out.println("Outcome of trying to fetch existing package-list,json: "+e.getMessage());
      return null;
    }
  }

  private boolean isPublished(JsonObject json, String path) {
      JsonArray list = json.getAsJsonArray("list");
      if (list != null) {
        for (JsonElement e : list) {
          JsonObject o = (JsonObject) e;
          if (o.has("version") && o.get("version").getAsString().equals(packageVersion)) {
            return true;
          }
          if (o.has("path") && o.get("path").getAsString().equals(path)) {
            return true;
          }
        }
      }
      return false;
  }

  private JsonObject getVersion(JsonObject obj) {
    JsonArray list = obj.getAsJsonArray("list");
    if (list != null) {
      for (JsonElement e : list) {
        JsonObject o = (JsonObject) e;
        if (o.has("version") && o.get("version").getAsString().equals(packageVersion)) {
          return o;
        }
      }
    }
    return null;
  }

  private String error(String msg) {
    return "<span stlye=\"color: marron; font-weight: bold\">"+Utilities.escapeXml(msg)+"</span>";
  }
}
