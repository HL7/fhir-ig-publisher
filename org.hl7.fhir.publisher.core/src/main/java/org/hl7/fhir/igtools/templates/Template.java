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
import org.hl7.fhir.utilities.cache.NpmPackageIndexBuilder;
import org.hl7.fhir.utilities.json.JsonTrackingParser;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;


public class Template {

  public static final int IG_NONE = 0;
  public static final int IG_ANY = 1;
  public static final int IG_NO_RESOURCE = 2;
  
  private NpmPackage pack;
  private JsonObject configuration;
  
  private String templateDir;
  private String root;
  private boolean canExecute;
  private String script;
  private String targetOnLoad;
  private String targetOnGenerate;
  private String targetOnJekyll;
  private String targetOnCheck;
  private Project antProject;
  private JsonObject defaults;
  private JsonArray extraTemplates;
  private JsonArray preProcess;
  
  /** unpack the template into /template 
   * 
   * @param npm - the package containing the template
   * @param noInit - a flag to prevent the template being copied into {rootDir}/template (only when it's already there as an inline template)
   * @param rootDir  the root directory for the IG
   * @param canExecute 
   * 
   * @throws IOException - only if the path is incorrect or the disk runs out of space
   */
  public Template(NpmPackage npm, boolean noInit, String rootDir, boolean canExecute, boolean noClear, boolean noLoad) throws IOException {
    pack = npm;
    root = rootDir;
    this.canExecute = canExecute;
    
    templateDir = Utilities.path(rootDir, "template");
    if (!noInit) {  // special case  - no init when template is already in the right place
      Utilities.createDirectory(templateDir);
      if (!noClear)
        Utilities.clearDirectory(templateDir);
      pack.unPackWithAppend(templateDir);
    }
    // ok, now templateDir has the content of the template
    configuration = JsonTrackingParser.parseJsonFile(Utilities.path(templateDir, "config.json"));
    if (!noLoad) {
      if (configuration.has("script") && canExecute) {
        script = configuration.get("script").getAsString();
        if (!configuration.has("targets"))
          throw new FHIRException("If a script is provided, then targets must be defined");
        JsonObject targets = configuration.getAsJsonObject("targets");
        if (targets.has("onLoad"))
          targetOnLoad = targets.get("onLoad").getAsString();
        if (targets.has("onGenerate"))
          targetOnGenerate = targets.get("onGenerate").getAsString();
        if (targets.has("onJekyll"))
          targetOnJekyll = targets.get("onJekyll").getAsString();
        if (targets.has("onCheck"))
          targetOnCheck = targets.get("onCheck").getAsString();
        File buildFile = new File(Utilities.path(templateDir, script));
        antProject = new Project();
        
        ProjectHelper.configureProject(antProject, buildFile);
        DefaultLogger consoleLogger = new DefaultLogger();
        consoleLogger.setErrorPrintStream(System.err);
        consoleLogger.setOutputPrintStream(System.out);
        consoleLogger.setMessageOutputLevel(Project.MSG_INFO);
        antProject.addBuildListener(consoleLogger);
        antProject.setBasedir(root);
        antProject.setProperty("ig.root", root);
        antProject.setProperty("ig.template", templateDir);
        antProject.setProperty("ig.scripts", Utilities.path(templateDir, "scripts"));
        antProject.init();
      }
      
      if (configuration.has("defaults")) {
        defaults = (JsonObject)configuration.get("defaults");
      }
  
      if (configuration.has("extraTemplates")) {
        extraTemplates = (JsonArray)configuration.get("extraTemplates");
      }
  
      if (configuration.has("pre-process")) {
        preProcess = (JsonArray)configuration.get("pre-process");
      }
    }
}
  
  public boolean hasPreProcess() {
    return preProcess != null;
  }
    
  public JsonArray getPreProcess() {
    return preProcess;
  }
    
  public boolean hasExtraTemplates() {
    return extraTemplates != null;
  }
    
  public JsonArray getExtraTemplates() {
    return extraTemplates;
  }
    
  private ImplementationGuide runScriptTarget(String target, Map<String, List<ValidationMessage>> messages, ImplementationGuide ig, List<String> fileNames, int modifyIg) throws IOException, FHIRException {
    File jsonOutcomes = new File(Utilities.path(templateDir, target + "-validation.json"));
    File xmlOutcomes = new File(Utilities.path(templateDir, target + "-validation.xml"));
    if (jsonOutcomes.exists())
      jsonOutcomes.delete();
    if (xmlOutcomes.exists())
      xmlOutcomes.delete();
    String sfn = Utilities.path(templateDir, target + "-ig-working.");
    String fn = Utilities.path(templateDir, target + "-ig-updated.");
    File jsonIg = new File(Utilities.path(templateDir, sfn +"json"));
    File xmlIg = new File(Utilities.path(templateDir, sfn + "xml"));
    if (ig != null) {
      antProject.setProperty(target + ".ig.source", sfn);
      antProject.setProperty(target + ".ig.dest", fn);
      if (jsonIg.exists())
        jsonIg.delete();
      if (xmlIg.exists())
        xmlIg.delete();
      new XmlParser().compose(new FileOutputStream(sfn+"xml"), ig);
      new JsonParser().compose(new FileOutputStream(sfn+"json"), ig);    
    }
    antProject.executeTarget(target);
    if (fileNames!=null) {
      String files = antProject.getProperty(target + ".files");
      if (!files.isEmpty()) {
        String [] fileArray = files.split(";");
        for (int i=0;i<fileArray.length;i++) {
          fileNames.add(fileArray[i]);
        }
      }
    }
    if (jsonOutcomes.exists()) {
      loadValidationMessages((OperationOutcome) new JsonParser().parse(new FileInputStream(jsonOutcomes)), messages);
    } else if (xmlOutcomes.exists()) {
      loadValidationMessages((OperationOutcome) new XmlParser().parse(new FileInputStream(xmlOutcomes)), messages);
    }
    if (ig != null) {
      String newXml = fn+"xml";
      String newJson = fn+"json";
      switch (modifyIg) {
        case IG_ANY:
          if (new File(newXml).exists())
            return (ImplementationGuide) new XmlParser().parse(new FileInputStream(newXml));
          else if (new File(newJson).exists())
            return (ImplementationGuide) new JsonParser().parse(new FileInputStream(newJson));
          else
            throw new FHIRException("onLoad script "+targetOnLoad+" failed - no output file produced");        
        case IG_NO_RESOURCE:
          if (new File(newXml).exists())
            loadModifiedIg((ImplementationGuide) new XmlParser().parse(new FileInputStream(newXml)), ig);
          else if (new File(newJson).exists())
            loadModifiedIg((ImplementationGuide) new JsonParser().parse(new FileInputStream(newJson)), ig);
          return null;
        case IG_NONE:
          return null;
        default:
          throw new FHIRException("Unexpected modifyIg value: " + modifyIg);
      }
    }
    return null;
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
      if (!res.containsKey(source))
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
    if (defaults!=null) {
      if (defaults.has(type))
        return (JsonObject)defaults.get(type);
      else if (defaults.has("Any"))
        return (JsonObject)defaults.get("Any");
    }
    return null;
  }

  public ImplementationGuide onLoadEvent(ImplementationGuide ig, Map<String, List<ValidationMessage>> messages) throws IOException, FHIRException {
    if (targetOnLoad == null)
      return ig;
    else
      return runScriptTarget(targetOnLoad, messages, ig, null, IG_ANY);
  }
  
  public Map<String, List<ValidationMessage>> beforeGenerateEvent(ImplementationGuide ig, String tempDir, Set<String> fileList, List<String> newFileList) throws IOException, FHIRException {
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
    if (canExecute && targetOnGenerate != null) {
      Map<String, List<ValidationMessage>> messages = new HashMap<String, List<ValidationMessage>>();
      antProject.setProperty("ig.temp", tempDir);
      runScriptTarget(targetOnGenerate, messages, ig, newFileList, IG_NO_RESOURCE);
      return messages;
    } else
      return null;
  }

  public Map<String, List<ValidationMessage>> beforeJekyllEvent(ImplementationGuide ig, List<String> newFileList) throws IOException, FHIRException {
    if (canExecute && targetOnJekyll != null) {
      Map<String, List<ValidationMessage>> messages = new HashMap<String, List<ValidationMessage>>();
      runScriptTarget(targetOnJekyll, messages, null, newFileList, IG_NONE);
      return messages;
    } else
      return null;
  }

  public Map<String, List<ValidationMessage>> onCheckEvent(ImplementationGuide ig) throws IOException, FHIRException {
    if (canExecute && targetOnCheck != null) {
      Map<String, List<ValidationMessage>> messages = new HashMap<String, List<ValidationMessage>>();
      runScriptTarget(targetOnCheck, messages, null, null, IG_NONE);
      return messages;
    } else
      return null;
  }

  
}
