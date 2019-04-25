package org.hl7.fhir.igtools.templates;

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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.r5.formats.XmlParser;
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
  
  private String templateDir;
  private String root;
  private boolean canExecute;
  private String scriptOnLoad;
  private String scriptOnGenerate;
  private String scriptOnJekyll;
  private String scriptOnCheck;
  
  /** unpack the template into /template 
   * 
   * @param npm - the package containing the template
   * @param noInit - a flag to prevent the template being copied into {rootDir}/template (only when it's already there as an inline template)
   * @param rootDir  the root directory for the IG
   * @param canExecute 
   * 
   * @throws IOException - only if the path is incorrect or the disk runs out of space
   */
  public Template(NpmPackage npm, boolean noInit, String rootDir, boolean canExecute) throws IOException {
    pack = npm;
    root = rootDir;
    this.canExecute = canExecute;
    
    templateDir = Utilities.path(rootDir, "template");
    if (!noInit) {  // special case  - no init when template is already in the right place
      Utilities.createDirectory(templateDir);
      Utilities.clearDirectory(templateDir);
      pack.unPack(templateDir);
    }
    // ok, now templateDir has the content of the template
    configuration = JsonTrackingParser.parseJsonFile(Utilities.path(templateDir, "config.json"));
    
    if (configuration.has("scripts")) {
      JsonObject scripts = configuration.getAsJsonObject("scripts");
      if (scripts.has("onLoad"))
        scriptOnLoad = scripts.get("onload").getAsString();
      if (scripts.has("onGenerate"))
        scriptOnGenerate = scripts.get("onGenerate").getAsString();
      if (scripts.has("onJekyll"))
        scriptOnJekyll = scripts.get("onJekyll").getAsString();
      if (scripts.has("onCheck"))
        scriptOnCheck = scripts.get("onCheck").getAsString();
    }
  }
  
  /**
   * this is the first event of the template life cycle. At this point, the template can modify the IG as it sees fit. 
   * This typically includes scanning the content in the IG and filling out resource/page entries and details
   * 
   * Note that the param
   * 
   * @param ig
   * @return
   * @throws IOException 
   * @throws FileNotFoundException 
   * @throws FHIRException 
   */
  public ImplementationGuide modifyIGEvent(ImplementationGuide ig) throws FileNotFoundException, IOException, FHIRException {
    if (!canExecute || scriptOnLoad == null)
      return ig;
    new XmlParser().compose(new FileOutputStream(Utilities.path(templateDir, "ig-working.xml")), ig);
    runScript(scriptOnLoad);
    String fn = Utilities.path(templateDir, "ig-updated.xml");
    if (new File(fn).exists())
      throw new FHIRException("onLoad script "+scriptOnLoad+" failed - no output file produced");
    return (ImplementationGuide) new XmlParser().parse(new FileInputStream(fn));  
  }
  
  private void runScript(String script) throws IOException {
    File buildFile = new File(Utilities.path(templateDir, script));
    Project project = new Project();
    ProjectHelper.configureProject(project, buildFile);
    DefaultLogger consoleLogger = new DefaultLogger();
    consoleLogger.setErrorPrintStream(System.err);
    consoleLogger.setOutputPrintStream(System.out);
    consoleLogger.setMessageOutputLevel(Project.MSG_INFO);
    project.addBuildListener(consoleLogger);
    project.init();
    project.executeTarget(project.getDefaultTarget());
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
    File src = new File(Utilities.path(templateDir, "content"));
    if (src.exists()) {
      FileUtils.copyDirectory(src, new File(tempDir));
    }
    // load it into temp
    // if it as an initial any file, let it run 
    // load template configuration for templates / defaults    
  }


  
}
