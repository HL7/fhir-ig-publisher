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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_40_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionResourceComponent;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r5.model.Reference;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.i18n.POObject;
import org.hl7.fhir.utilities.i18n.POSource;
import org.hl7.fhir.utilities.json.JsonException;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonPrimitive;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.model.JsonString;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.settings.FhirSettings;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.hl7.fhir.utilities.xml.XMLUtil;


public class Template {

  public static final int IG_NONE = 0;
  public static final int IG_ANY = 1;
  public static final int IG_NO_RESOURCE = 2;
  private static final boolean USE_R5_IG_FORMAT = false;
  
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
  private String templateThatCantExecute;
  private Project antProject;
  private JsonObject defaults;
  private JsonArray extraTemplates;
  private JsonArray preProcess;
  private String templateReason;
  private Set<String> summaryRows = new HashSet<>();
  private Set<String> templateParams = new HashSet<>();
  private boolean wantLog;
  private Map<String, String> scriptMappings = new HashMap<>();
  @Getter Map<String, TemplateFragmentTypeLoader.PrefixGroup> usedFragmentTypes;
  private boolean rapido;

  /** unpack the template into /template 
   * 
   * @param rootDir  the root directory for the IG
   * @param canExecute 
   * @param templateExtensions 
   * 
   * @throws IOException - only if the path is incorrect or the disk runs out of space
   */
  public Template(String rootDir, boolean canExecute, String templateThatCantExecute, String templateReason, boolean wantLog, boolean rapido) throws IOException {
    root = rootDir;
    this.canExecute = canExecute;
    this.templateThatCantExecute = templateThatCantExecute;
    this.templateReason = templateReason;
    this.wantLog = wantLog;
    this.rapido = rapido;

    templateDir = Utilities.path(rootDir, "template");

    // ok, now templateDir has the content of the template
    configuration = org.hl7.fhir.utilities.json.parser.JsonParser.parseObjectFromFile(Utilities.path(templateDir, "config.json"));
    if (configuration.has("script")) {
      script = configuration.asString("script");
      if (!configuration.has("targets"))
        throw new FHIRException("If a script is provided, then targets must be defined");
      JsonObject targets = configuration.getJsonObject("targets");
      if (targets.has("onLoad"))
        targetOnLoad = targets.asString("onLoad");
      if (targets.has("onGenerate"))
        targetOnGenerate = targets.asString("onGenerate");
      if (targets.has("onJekyll"))
        targetOnJekyll = targets.asString("onJekyll");
      if (targets.has("onCheck"))
        targetOnCheck = targets.asString("onCheck");
      File buildFile = new File(Utilities.path(templateDir, script));
      antProject = new Project();

      ProjectHelper.configureProject(antProject, buildFile);
      DefaultLogger consoleLogger = new DefaultLogger();
      consoleLogger.setErrorPrintStream(System.err);
      consoleLogger.setOutputPrintStream(System.out);
      if (wantLog) {        
        consoleLogger.setMessageOutputLevel(Project.MSG_INFO);
      } else {
        consoleLogger.setMessageOutputLevel(Project.MSG_ERR);        
      }
      antProject.addBuildListener(consoleLogger);
      antProject.setBasedir(root);
      antProject.setProperty("ig.root", root);
      antProject.setProperty("ig.template", templateDir);
      antProject.setProperty("ig.scripts", Utilities.path(templateDir, "scripts"));
      antProject.setProperty("ig.networkprohibited", Boolean.toString(FhirSettings.isProhibitNetworkAccess()));
      antProject.init();
    }
    for (JsonProperty p : configuration.getProperties()) {
      if (p.getName().startsWith("template-parameters")) {
        for (JsonElement j : p.getValue().asJsonArray()) {
          templateParams.add(j.asString());                  
        }
      }
    }
    for (JsonProperty p : configuration.forceObject("script-mappings").getProperties()) {
      scriptMappings.put(p.getName(), p.getValue().asString());                  
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
    if (configuration.has("summaryRows")) {
      for (String s : configuration.asString("summaryRows").split("\\ "))
      summaryRows.add(s);
    }
    loadFragmentTypes();
  }

  private void loadFragmentTypes() {
    usedFragmentTypes = new TemplateFragmentTypeLoader().process(templateDir);
  }

  public void processTemplateTranslations(String defLang, List<String> langCodes) throws IOException {
    List<String> allLangCodes = new ArrayList<>();
    allLangCodes.add(defLang);
    allLangCodes.addAll(langCodes);
    String tf = Utilities.path(templateDir, "translations");
    File tff = new File(tf);
    if (tff.exists()) {
      for (File f : tff.listFiles()) {
        if (f.getName().endsWith(".json")) {
          processTranslationFile(tf, f, allLangCodes);
        }
      }
    }
  }

  private void processTranslationFile(String tf, File fl, List<String> langCodes) throws JsonException, IOException {
    String base = FileUtilities.fileTitle(fl.getName());
    Map<String, POSource> translations = new HashMap<>();

    // load the base file
    JsonObject lf = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(fl);
    
    // load all the translations
    for (File f : new File(tf).listFiles()) {
      if (f.getName().startsWith(base+"-") && f.getName().endsWith(".po")) {
        String lang = f.getName().substring(base.length()+1).replace(".po", "");
        translations.put(lang, POSource.loadPOFile(f.getAbsolutePath()));
      }
    }
    // create language files for entries in the base
    for (JsonProperty langEntry : lf.getProperties()) {
      String lang = langEntry.getName();
      if (!"en".equals(lang)) {
        POSource source;
        if (!translations.containsKey(lang)) {
          source = new POSource();
          translations.put(base, source);
          for (JsonProperty p : langEntry.getValue().asJsonObject().getProperties()) {
            source.getPOObjects().add(new POObject(p.getName(), lf.getJsonObject("en").asString(p.getName()), p.getValue().asString()));
          }
        } else {
          source = translations.get(lang);
        }
        for (JsonProperty p : lf.getJsonObject("en").getProperties()) {
          POObject existing = findPO(source, p);
          if (existing == null) {
            source.getPOObjects().add(new POObject(p.getName(), p.getValue().asString(), null));
          } else {
            existing.setMsgid(p.getValue().asString());
          }
        }
        source.savePOFile(Utilities.path(tf, base+"-"+lang+".po"), 1, 0);
      }
    }
    // populate from the PO files 
    for (String lang : langCodes) {
      POSource units = translations.get(lang);
      if (!"en".equals(lang)) {
      if (units != null) {
        JsonObject l = lf.forceObject(lang);
        for (POObject po : units.getPOObjects()) {
          l.remove(po.getId());
          if (po.getMsgstr().isEmpty() || Utilities.noString(po.getMsgstr().get(0))) {
            l.add(po.getId(), lf.getJsonObject("en").asString(po.getId()));
          } else {
            l.add(po.getId(), po.getMsgstr().get(0));
          }
        }
      }
      }
    }
    // fill out missing entries
    for (JsonProperty langEntry : lf.getProperties()) {
      String lang = langEntry.getName();
      if (!"en".equals(lang)) {
        JsonObject l = lf.getJsonObject(lang);
        for (JsonProperty p : lf.getJsonObject("en").getProperties()) {
          if (!hasEntry(l, p.getName())) {
            l.remove(p.getName());
            l.add(p.getName(), p.getValue());            
          }
        }
      }
    }

    for (String lang : langCodes) {
      POSource source = translations.get(lang);
      if (source != null) {
        for (JsonProperty p : lf.getJsonObject("en").getProperties()) {
          POObject existing = findPO(source, p);
          if (existing == null) {
            POObject n = new POObject(p.getName(), p.getValue().asString(), null);
            source.getPOObjects().add(n);
          }
        }
        source.savePOFile(Utilities.path(tf, base+"-"+lang+".po"), 1, 0);
      }
    }
    
    org.hl7.fhir.utilities.json.parser.JsonParser.compose(lf, fl, true);
    String xml = translationsToXML(lf);
    String xmlFile = fl.getAbsolutePath();
    xmlFile = xmlFile.substring(0, xmlFile.length()-4)+"xml";
    try {
      Files.writeString(Paths.get(xmlFile), xml);
    } catch (IOException e) {
        System.err.println("An error occurred while writing to the file " + xmlFile + ": " + e.getMessage());
    }
  }

  private POObject findPO(POSource source, JsonProperty p) {
    POObject existing = null;
    for (POObject t : source.getPOObjects()) {
      if (t.getId().equals(p.getName())) {
        existing = t;
        break;
      }
    }
    return existing;
  }
  
  private String translationsToXML(JsonObject jt) {
  StringBuilder sb = new StringBuilder();
  sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<translations>\r\n");
  List<String> langs = jt.getNames();
  JsonObject baseLang = jt.getJsonObject(langs.get(0));
  for (int i = 0; i < baseLang.getNames().size(); i++) {
    String key = baseLang.getNames().get(i);
    sb.append("<translation name=\"" + key + "\"");
    sb.append(" " + langs.get(0) + "=\"" + XMLUtil.escapeXML(baseLang.getJsonString(key).asString(), "UTF-8", false) + "\"");
    for (int j = 1; j < langs.size(); j++) {
      JsonObject transLang = jt.getJsonObject(langs.get(j));
      JsonString trans = transLang.getJsonString(key);
      sb.append(" " + langs.get(j) + "=\"" + (trans==null ? "" : XMLUtil.escapeXML(trans.asString(), "UTF-8", false)) + "\"");
    }
    sb.append("/>\r\n");
  }
  sb.append("</translations>");
  return sb.toString();
  }

  private boolean hasEntry(JsonObject l, String name) {
    JsonElement v = l.get(name);
    return !(v == null || v.isJsonNull() || Utilities.noString(v.asString()));
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

  public Collection<String> getFormats() {
    Collection<String> formatList = new ArrayList<String>();
    if (configuration.has("formats")) {
      for (JsonElement format: configuration.getJsonArray("formats"))
        formatList.add(format.asString());
    } else {
      formatList.add("xml");
      formatList.add("json");
      formatList.add("ttl");
    }
    return formatList;
  }
  
  private ImplementationGuide runScriptTarget(String target, Map<String, List<ValidationMessage>> messages, ImplementationGuide ig, List<String> fileNames, int modifyIg) throws IOException, FHIRException {
    if (!canExecute) {
      throw new FHIRException("Unable to execute '"+target+"' in script '"+script+"' as the template '"+templateThatCantExecute+"' is not trusted (reason: "+templateReason+")");
    }
    File jsonOutcomes = new File(Utilities.path(templateDir, target + "-validation.json"));
    File xmlOutcomes = new File(Utilities.path(templateDir, target + "-validation.xml"));
    if (jsonOutcomes.exists())
      jsonOutcomes.delete();
    if (xmlOutcomes.exists())
      xmlOutcomes.delete();
    String sfn = Utilities.path(templateDir, target + "-ig-working.");
    String fn = Utilities.path(templateDir, target + "-ig-updated.");
    File jsonIg = new File(sfn +"json");
    File xmlIg = new File(sfn + "xml");
    if (ig != null) {
      antProject.setProperty(target + ".ig.source", sfn);
      antProject.setProperty(target + ".ig.dest", fn);
      if (jsonIg.exists())
        jsonIg.delete();
      if (xmlIg.exists())
        xmlIg.delete();
      if (USE_R5_IG_FORMAT) {
        new XmlParser().compose(new FileOutputStream(sfn+"xml"), ig);
        new JsonParser().compose(new FileOutputStream(sfn+"json"), ig);
      } else {
        org.hl7.fhir.r4.model.ImplementationGuide ig4 = (org.hl7.fhir.r4.model.ImplementationGuide) VersionConvertorFactory_40_50.convertResource(ig, new BaseAdvisor_40_50(true, true));
        new org.hl7.fhir.r4.formats.XmlParser().compose(new FileOutputStream(sfn+"xml"), ig4);
        new org.hl7.fhir.r4.formats.JsonParser().compose(new FileOutputStream(sfn+"json"), ig4);
      }
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
          if (new File(newXml).exists()) {
            if (USE_R5_IG_FORMAT) {
              return (ImplementationGuide) new XmlParser().parse(new FileInputStream(newXml));
            } else {
              return (ImplementationGuide) VersionConvertorFactory_40_50.convertResource(new org.hl7.fhir.r4.formats.XmlParser().parse(new FileInputStream(newXml)));                
            }
          } else if (new File(newJson).exists()) {
            if (USE_R5_IG_FORMAT) {
              return (ImplementationGuide) new JsonParser().parse(new FileInputStream(newJson));              
            } else {
              return (ImplementationGuide) VersionConvertorFactory_40_50.convertResource(new org.hl7.fhir.r4.formats.JsonParser().parse(new FileInputStream(newXml)));                
            }
          } else
            throw new FHIRException("onLoad script "+targetOnLoad+" failed - no output file produced");        
        case IG_NO_RESOURCE:
          if (new File(newXml).exists()) {
            if (USE_R5_IG_FORMAT) {
              loadModifiedIg((ImplementationGuide) new XmlParser().parse(new FileInputStream(newXml)), ig);
            } else {
              loadModifiedIg((ImplementationGuide) VersionConvertorFactory_40_50.convertResource(new org.hl7.fhir.r4.formats.XmlParser().parse(new FileInputStream(newXml))), ig);
            }
          }
          else if (new File(newJson).exists()) {
            if (USE_R5_IG_FORMAT) {
              loadModifiedIg((ImplementationGuide) new JsonParser().parse(new FileInputStream(newJson)), ig);
            } else {
              loadModifiedIg((ImplementationGuide) VersionConvertorFactory_40_50.convertResource(new org.hl7.fhir.r4.formats.JsonParser().parse(new FileInputStream(newXml))), ig);
            }
          }
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
      throw new FHIRException("Templates are not allowed to modify the resources ("+oc+"/"+nc+")");
    for (ImplementationGuideDefinitionResourceComponent or : ig.getDefinition().getResource()) {
      ImplementationGuideDefinitionResourceComponent nr = getMatchingResource(modIg, or.getReference()); 
      if (nr == null)
        throw new FHIRException("Templates are not allowed to modify the resources - didn't find '"+or.getReference()+"'");
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
      String source = ExtensionUtilities.readStringExtension(issue, ExtensionDefinitions.EXT_ISSUE_SOURCE);
      if (source == null)
        source = "";
      if (!res.containsKey(source))
        res.put(source, new ArrayList<>());
      ValidationMessage vm = ExtensionUtilities.readValidationMessage(issue, Source.Template);
      if (vm.getLevel() == IssueSeverity.FATAL)
        throw new FHIRException("Fatal Error from Template: "+vm.getMessage());
      if (vm.getLevel() != null) {
        res.get(source).add(vm);
      }
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
    return p.asString();
  }


  public JsonObject config() {
    return configuration;
  }

  public boolean getIncludeHeadings() {
    return !configuration.has("includeHeadings") || configuration.asBoolean("includeHeadings");
  }

  public String getIGArtifactsPage() {
    return configuration.has("igArtifactsPage") ? configuration.asString("igArtifactsPage") : null;
  }

  public boolean getDoTransforms() throws Exception {
    return "true".equals(ostr(configuration, "do-transforms"));
  }

  public void getExtraTemplates(Map<String, String> extraTemplates) throws Exception {
    JsonArray templates = configuration.getJsonArray("extraTemplates");
    if (templates!=null) {
      for (JsonElement template : templates) {
        if (template.isJsonPrimitive())
          extraTemplates.put(template.asString(), template.asString());
        else {
          if (!((JsonObject)template).has("name") || !((JsonObject)template).has("description"))
            throw new Exception("extraTemplates must be an array of objects with 'name' and 'description' properties");
          extraTemplates.put(((JsonObject)template).asString("name"), ((JsonObject)template).asString("description"));
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
  
  public Map<String, List<ValidationMessage>> beforeGenerateEvent(ImplementationGuide ig, String tempDir, Set<String> fileList, List<String> newFileList, List<String> translationLangs) throws IOException, FHIRException {
    File src = new File(Utilities.path(templateDir, "content"));
    if (src.exists()) {
      for (File f : src.listFiles()) {
        if (f.isDirectory()) {
          FileUtils.copyDirectory(f, new File(Utilities.path(tempDir, f.getName())));
          if (!translationLangs.isEmpty()) {
            for (String lang: translationLangs) {
              FileUtils.copyDirectory(f, new File(Utilities.path(tempDir, lang, f.getName())));
            }
          }
        } else {
          File nf = new File(Utilities.path(tempDir, f.getName()));
          fileList.add(nf.getAbsolutePath());
          FileUtils.copyFile(f, nf);
          if (!translationLangs.isEmpty()) {
            for (String lang: translationLangs) {
              File nfl = new File(Utilities.path(tempDir, lang, f.getName()));
              fileList.add(nfl.getAbsolutePath());
              FileUtils.copyFile(f, nfl);
            }
          }
        }
      }
    }
    if (targetOnGenerate != null) {
      Map<String, List<ValidationMessage>> messages = new HashMap<String, List<ValidationMessage>>();
      antProject.setProperty("ig.temp", tempDir);
      runScriptTarget(targetOnGenerate, messages, ig, newFileList, IG_NO_RESOURCE);
      return messages;
    } else
      return null;
  }

  public Map<String, List<ValidationMessage>> beforeJekyllEvent(ImplementationGuide ig, List<String> newFileList) throws IOException, FHIRException {
    if (targetOnJekyll != null) {
      Map<String, List<ValidationMessage>> messages = new HashMap<String, List<ValidationMessage>>();
      runScriptTarget(targetOnJekyll, messages, null, newFileList, IG_NONE);
      return messages;
    } else
      return null;
  }

  public Map<String, List<ValidationMessage>> onCheckEvent(ImplementationGuide ig) throws IOException, FHIRException {
    if (targetOnCheck != null) {
      Map<String, List<ValidationMessage>> messages = new HashMap<String, List<ValidationMessage>>();
      runScriptTarget(targetOnCheck, messages, null, null, IG_NONE);
      return messages;
    } else
      return null;
  }


  public void loadSummaryRows(Set<String> rows) {
    rows.addAll(summaryRows);   
  }

  public boolean isParameter(String pc) {
    return templateParams.contains(pc) || (templateParams.isEmpty() && Utilities.existsInList(pc, "releaselabel", "shownav", "excludettl", "jira-code", "fmm-definition", "excludelogbinaryformat"));
  }

  public Map<String, String> getScriptMappings() {
    return scriptMappings ;
  }

  private int fragments;
  private int noproduced;

  public boolean wantGenerateFragment(String s, String code) {
    if (usedFragmentTypes == null) {
      return true;
    }
    if (!rapido) {
      return true;
    }
    fragments++;
    TemplateFragmentTypeLoader.PrefixGroup pfx = usedFragmentTypes.get(s);

    if (pfx != null && pfx.suffixes.contains(code)) {
      return true;
    }
    pfx = usedFragmentTypes.get("{{[type]}}");
    if (pfx != null && pfx.suffixes.contains(code)) {
      return true;
    }
    if (Utilities.existsInList(code, "jekyll-data",
            "xml", "json", "ttl",
            "csv", "xlsx", "sch",
            "xml-html", "json-html", "ttl-html")) {
      return true;
    }

    noproduced++;
    return false;
  }

  public void rapidoSummary() {
    if (rapido) {
      System.out.println("Rapido Template Summary: don't produce "+noproduced+" of "+fragments+" fragments");
    }
  }
}
