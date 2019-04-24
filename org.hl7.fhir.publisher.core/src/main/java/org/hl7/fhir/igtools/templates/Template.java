package org.hl7.fhir.igtools.templates;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.utilities.JsonMerger;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.json.JsonTrackingParser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class Template {

  private NpmPackage pack;
  private JsonObject configuration;
  
  private boolean noInit;
  private String templateDir;
  private String root;
  
  /** unpack the template into /template 
   * 
   * @param npm - the package containing the template
   * @param noInit - a flag to prevent the template being copied into {rootDir}/template (only when it's already there as an inline template)
   * @param rootDir  the root directory for the IG
   * 
   * @throws IOException - only if the path is incorrect or the disk runs out of space
   */
  public Template(NpmPackage npm, boolean noInit, String rootDir) throws IOException {
    pack = npm;
    root = rootDir;
    this.noInit = noInit;
    templateDir = Utilities.path(rootDir, "template");
    if (!noInit) {  // special case  - no init when template is already in the right place
      Utilities.createDirectory(templateDir);
      Utilities.clearDirectory(templateDir);
      pack.unPack(templateDir);
    }
    // ok, now templateDir has the content of the template
    configuration = JsonTrackingParser.parseJsonFile(Utilities.path(templateDir, "config.json"));
  }
  

  /**
   * this is the first event of the template life cycle. At this point, the template can modify the IG as it sees fit. 
   * This typically includes scanning the content in the IG and filling out resource/page entries and details
   * 
   * Note that the param
   * 
   * @param ig
   * @return
   */
  public ImplementationGuide modifyIG(ImplementationGuide ig) {
    return ig;
  }

  private String ostr(JsonObject obj, String name) throws Exception {
    if (obj == null)
      return null;
    if (!obj.has(name))
      return null;
    if (!(obj.get(name) instanceof JsonPrimitive))
      return null;
    JsonPrimitive p = (JsonPrimitive) obj.get(name);
    if (!p.isString())
      return null;
    return p.getAsString();
  }


  public JsonObject config() {
    return configuration;
  }

  public boolean getIncludeHeadings() {
    return !configuration.has("includeHeadings") || configuration.get("includeHeadings").getAsBoolean();
  }

  public String getIGArtifactsPage() {
    return configuration.has("igArtifactsPage") ? configuration.get("igArtifactsPage").getAsString() : null;
  }

  public boolean getDoTransforms() throws Exception {
    return "true".equals(ostr(configuration, "do-transforms"));
  }

  public void getExtraTemplates(Map<String, String> extraTemplates) throws Exception {
    JsonArray templates = configuration.getAsJsonArray("extraTemplates");
    if (templates!=null) {
      for (JsonElement template : templates) {
        if (template.isJsonPrimitive())
          extraTemplates.put(template.getAsString(), template.getAsString());
        else {
          if (!((JsonObject)template).has("name") || !((JsonObject)template).has("description"))
            throw new Exception("extraTemplates must be an array of objects with 'name' and 'description' properties");
          extraTemplates.put(((JsonObject)template).get("name").getAsString(), ((JsonObject)template).get("description").getAsString());
        }
      }
    }
  }

  public JsonObject getConfig(String type, String id) {
    return null;
  }

  public void beforeGenerate(String tempDir) throws IOException {
    File src = new File(Utilities.path(templateDir, "jekyll"));
    if (src.exists()) {
      FileUtils.copyDirectory(src, new File(tempDir));
    }
    // load it into temp
    // if it as an initial any file, let it run 
    // load template configuration for templates / defaults    
  }


  
}
