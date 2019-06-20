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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionResourceComponent;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r5.model.Reference;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.utilities.JsonMerger;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.json.JsonTrackingParser;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;

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
    
    if (configuration.has("scripts") && canExecute) {
      JsonObject scripts = configuration.getAsJsonObject("scripts");
      if (scripts.has("onLoad"))
        scriptOnLoad = scripts.get("onLoad").getAsString();
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
    String sfn = Utilities.path(templateDir, "ig-working.");
    String fn = Utilities.path(templateDir, "ig-updated.");
    Map<String, String> props = new HashMap<>(); 
    props.put("ig.source", sfn); 
    props.put("ig.dest", fn); 
    
    new XmlParser().compose(new FileOutputStream(sfn+"xml"), ig);
    new JsonParser().compose(new FileOutputStream(sfn+"json"), ig);
    runScript(scriptOnLoad, Utilities.path(root, "temp"), props, null);
    if (new File(fn+"xml").exists())
      return (ImplementationGuide) new XmlParser().parse(new FileInputStream(fn+"xml"));
    else if (new File(fn+"json").exists())
      return (ImplementationGuide) new JsonParser().parse(new FileInputStream(fn+"json"));
    else
      throw new FHIRException("onLoad script "+scriptOnLoad+" failed - no output file produced");
    
    
  }
  
  private Map<String, List<ValidationMessage>> runScript(String script, String tempDir, Map<String, String> props, ImplementationGuide ig) throws IOException, FHIRException {
    String filename = script;
    String target = null;
    if (filename.contains("#")) {
      target = filename.substring(filename.indexOf("#")+1);
      filename = filename.substring(0, filename.indexOf("#"));
    }
    
    File jsonOutcomes = new File(Utilities.path(templateDir, "validation.json"));
    File xmlOutcomes = new File(Utilities.path(templateDir, "validation.xml"));
    File jsonIg = new File(Utilities.path(templateDir, "ig-updated.json"));
    File xmlIg = new File(Utilities.path(templateDir, "ig-updated.xml"));
    if (jsonOutcomes.exists()) jsonOutcomes.delete();
    if (xmlOutcomes.exists())  xmlOutcomes.delete();
    if (jsonIg.exists())  jsonIg.delete();
    if (xmlIg.exists())  xmlIg.delete();
    
    File buildFile = new File(Utilities.path(templateDir, filename));
    Project project = new Project();
    ProjectHelper.configureProject(project, buildFile);
    DefaultLogger consoleLogger = new DefaultLogger();
    consoleLogger.setErrorPrintStream(System.err);
    consoleLogger.setOutputPrintStream(System.out);
    consoleLogger.setMessageOutputLevel(Project.MSG_INFO);
    project.addBuildListener(consoleLogger);
    project.setBasedir(root);
    project.setProperty("ig.root", root);
    project.setProperty("ig.temp", tempDir);
    project.setProperty("ig.template", templateDir);
    project.setProperty("ig.scripts", Utilities.path(templateDir, "scripts"));
    if (props != null) {
      for (String s : props.keySet()) {
        project.setProperty(s, props.get(s));
      }
    }
    project.init();
    project.executeTarget(target == null ?  project.getDefaultTarget() : target);
    Map<String, List<ValidationMessage>> res = new HashMap<>();
    if (jsonOutcomes.exists()) {
      loadValidationMessages((OperationOutcome) new JsonParser().parse(new FileInputStream(jsonOutcomes)), res);
    } else if (xmlOutcomes.exists()) {
      loadValidationMessages((OperationOutcome) new XmlParser().parse(new FileInputStream(xmlOutcomes)), res);
    }
    if (ig != null) {
      if (jsonIg.exists())
        loadModifiedIg((ImplementationGuide) new JsonParser().parse(new FileInputStream(jsonIg)), ig);
      else if (xmlIg.exists())
        loadModifiedIg((ImplementationGuide) new XmlParser().parse(new FileInputStream(jsonIg)), ig);
    }
    
    if (jsonOutcomes.exists()) jsonOutcomes.delete();
    if (xmlOutcomes.exists())  xmlOutcomes.delete();
    if (jsonIg.exists())  jsonIg.delete();
    if (xmlIg.exists())  xmlIg.delete();
    
    return res;
  }

  private void loadModifiedIg(ImplementationGuide modIg, ImplementationGuide ig) throws FHIRException {
    int oc = ig.getDefinition().getResource().size();
    int nc = modIg.getDefinition().getResource().size();
    if (oc != nc)
      throw new FHIRException("Ths template is not allowed to modify the resources ("+oc+"/"+nc+")");
    for (ImplementationGuideDefinitionResourceComponent or : ig.getDefinition().getResource()) {
      ImplementationGuideDefinitionResourceComponent nr = getMatchingResource(modIg, or.getReference()); 
      if (nr == null)
        throw new FHIRException("Ths template is not allowed to modify the resources - didn't find '"+or.getReference()+"'");
    }
    ig.setDefinition(modIg.getDefinition());
    ig.getManifest().setPage(modIg.getManifest().getPage());
    ig.getManifest().setImage(modIg.getManifest().getImage());
    ig.getManifest().setOther(modIg.getManifest().getOther());
  }

  private ImplementationGuideDefinitionResourceComponent getMatchingResource(ImplementationGuide modIg, Reference reference) {
    for (ImplementationGuideDefinitionResourceComponent nr : modIg.getDefinition().getResource()) {
      if (nr.getReference().getReference().equals(reference.getReference()))
        return nr;
    }
    return null;
  }

  private void loadValidationMessages(OperationOutcome op, Map<String, List<ValidationMessage>> res) throws FHIRException {
    for (OperationOutcomeIssueComponent issue : op.getIssue()) {
      String source = ToolingExtensions.readStringExtension(issue, ToolingExtensions.EXT_ISSUE_SOURCE);
      if (source == null)
        source = "";
      if (res.containsKey(source))
        res.put(source, new ArrayList<>());
      ValidationMessage vm = ToolingExtensions.readValidationMessage(issue, Source.Template);
      if (vm.getLevel() == IssueSeverity.FATAL)
        throw new FHIRException("Fatal Error from Template: "+vm.getMessage());
      res.get(source).add(vm);
    }    
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

  public Map<String, List<ValidationMessage>> beforeGenerateEvent(String tempDir, ImplementationGuide ig, Set<String> fileList) throws IOException, FHIRException {
    File src = new File(Utilities.path(templateDir, "content"));
    if (src.exists()) {
      for (File f : src.listFiles()) {
        if (f.isDirectory())
          FileUtils.copyDirectory(f, new File(Utilities.path(tempDir, f.getName())));
        else {
          File nf = new File(Utilities.path(tempDir, f.getName()));
          fileList.add(nf.getAbsolutePath());
          FileUtils.copyFile(f, nf);
        }
      }
    }
    if (canExecute && scriptOnGenerate != null) {
      String sfn = Utilities.path(templateDir, "ig-working.");
      Map<String, String> props = new HashMap<>(); 
      props.put("ig.source", sfn);       
      new XmlParser().compose(new FileOutputStream(sfn+"xml"), ig);
      new JsonParser().compose(new FileOutputStream(sfn+"json"), ig);
      return runScript(scriptOnGenerate, Utilities.path(root, "temp"), props, ig);      
    } else
      return null;
  }

  public Map<String, List<ValidationMessage>> beforeJekyllEvent(String tempDir, ImplementationGuide ig) throws IOException, FHIRException {
    if (canExecute && scriptOnJekyll != null) {
      String sfn = Utilities.path(templateDir, "ig-working.");
      Map<String, String> props = new HashMap<>(); 
      props.put("ig.source", sfn);       
      new XmlParser().compose(new FileOutputStream(sfn+"xml"), ig);
      new JsonParser().compose(new FileOutputStream(sfn+"json"), ig);
      return runScript(scriptOnJekyll, Utilities.path(root, "temp"), props, null);      
    } else
      return null;
  }

  public Map<String, List<ValidationMessage>> onCheckEvent(String tempDir, ImplementationGuide ig) throws IOException, FHIRException {
    if (canExecute && scriptOnCheck != null) {
      String sfn = Utilities.path(templateDir, "ig-working.");
      Map<String, String> props = new HashMap<>(); 
      props.put("ig.source", sfn);       
      new XmlParser().compose(new FileOutputStream(sfn+"xml"), ig);
      new JsonParser().compose(new FileOutputStream(sfn+"json"), ig);
      return runScript(scriptOnCheck, Utilities.path(root, "temp"), props, null);      
    } else
      return null;
  }

  
}
