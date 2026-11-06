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
import java.nio.file.attribute.BasicFileAttributes;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.regex.Pattern;

import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.AntTypeDefinition;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.ComponentHelper;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.ExtensionPoint;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.PropertyHelper;
import org.apache.tools.ant.RuntimeConfigurable;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.UnknownElement;
import org.apache.tools.ant.property.LocalProperties;
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_40_50;
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_40_N;
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_50_N;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_N;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_50_N;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.model.IModelContext;
import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.ImplementationGuide;
import org.hl7.fhir.model.core.ImplementationGuide.*;
import org.hl7.fhir.model.core.OperationOutcome;
import org.hl7.fhir.model.core.OperationOutcome.*;
import org.hl7.fhir.model.core.Reference;
import org.hl7.fhir.model.core.formats.JsonParser;
import org.hl7.fhir.model.utilities.formats.IParser;
import org.hl7.fhir.model.core.formats.XmlParser;
import org.hl7.fhir.model.extensions.ExtensionDefinitions;
import org.hl7.fhir.model.extensions.ExtensionUtilities;
import org.hl7.fhir.services.context.IWorkerContext;
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
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
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
  // the only listener the script's project is given (see whyOnCheckMaySeeOutput())
  private BuildListener antLogger;
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
  private boolean devMode;
  private OutputListener outputListener;

  /** unpack the template into /template 
   * 
   * @param rootDir  the root directory for the IG
   * @param canExecute 
   * @param templateExtensions 
   * 
   * @throws IOException - only if the path is incorrect or the disk runs out of space
   */
  public Template(String rootDir, boolean canExecute, String templateThatCantExecute, String templateReason, boolean wantLog, boolean rapido, boolean devMode) throws IOException {
    root = rootDir;
    this.canExecute = canExecute;
    this.templateThatCantExecute = templateThatCantExecute;
    this.templateReason = templateReason;
    this.wantLog = wantLog;
    this.rapido = rapido;
    this.devMode = devMode;

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
      antLogger = consoleLogger;
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
    // it writes the translation files again
    writing("translations", translationOutputs(), () -> {
      processTemplateTranslationFiles(defLang, langCodes);
      return null;
    });
  }

  private void processTemplateTranslationFiles(String defLang, List<String> langCodes) throws IOException {
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

  // what ends a folder name in a path (not a wildcard, or a property that names a folder: those may stand for any folder)
  private static final String FOLDER_END = "(?![^/\"'\\s<>;,)&])";
  // a reference to the input folder (or fsh-generated, which pages come from too), and the folder in it, if one is
  // named: ${folder.input}, ${ig.root}/input...
  private static final String INPUT_REF = "(\\$\\{(?:folder\\.input|dir\\.input%s)\\}|\\$\\{(?:ig\\.root|basedir%s)\\}/(?:input|fsh-generated)(?![\\w.-]))(/([^/\"'*&;,()\\s<>$?\\[{]+)" + FOLDER_END + ")?";
  private static final java.util.regex.Pattern PROPERTY = java.util.regex.Pattern.compile(
      "<property\\s[^>]*?name=\"([^\"]+)\"[^>]*>");
  // a file set or the like that starts from the IG's root folder
  private static final String ROOT_DIR =
      "<(?!project[\\s>])([\\w.:-]+)\\s[^>]*?(?<=\\s)(?:base)?dir=\"(?:\\$\\{(?:ig\\.root|basedir%s)\\})?(?:/?\\.)?/?\"[^>]*>";
  // a property set to the folder that holds a file or folder
  private static final java.util.regex.Pattern DIRNAME = java.util.regex.Pattern.compile(
      "<dirname\\s[^>]*?(?<=\\s)property=\"([^\"]+)\"");
  // a property defined as the IG's folder (${ig.root}, ${basedir} or .), by group 1 the property it's defined from; or
  // as a folder above (..), which is the IG's folder outside a target, where paths start from the ant file's folder
  private static final java.util.regex.Pattern ROOT_VALUE = java.util.regex.Pattern.compile(
      "\\s(?:value|location)=\"(?:\\$\\{([^}]+)\\}|\\.\\.?(?:/\\.\\.?)*)/?\"");
  // a path that goes up out of a folder (by group 1, the property it starts from, if it does): all but the IG's
  // folder are in the IG's folder
  private static final java.util.regex.Pattern PARENT = java.util.regex.Pattern.compile(
      "(?:\\$\\{([^}]+)\\}|(?<![\\w.$}/-])(?!\\.\\.(?![^/\"'\\s<>]))[\\w.-]+)((?:/[^/\"'\\s<>]*)*?)/\\.\\.(?![^/\"'\\s<>])");
  // a folder in the input folder named by its path from the IG's folder (input/pagecontent)
  private static final java.util.regex.Pattern INPUT_PATH = java.util.regex.Pattern.compile(
      "(?<![\\w.-])(?:input|fsh-generated)/([^/\"'*&;,()\\s<>$?\\[{]+)" + FOLDER_END);
  // the input folder by its path from the IG's folder, followed by something that isn't a folder name ("input",
  // input/** or input/${x})
  private static final java.util.regex.Pattern INPUT_WHOLE = java.util.regex.Pattern.compile(
      "(?<![\\w.-])(?:input|fsh-generated)(?:/?[\"']|/)");
  // a property defined as the input folder by its path from the IG's folder
  private static final java.util.regex.Pattern INPUT_VALUE = java.util.regex.Pattern.compile(
      "\\s(?:value|location)=\"(?:\\./)?(?:input|fsh-generated)/?\"");
  // the folders that only pages come from: a script that names one may read them
  private static final String[] PAGE_FOLDER_NAMES = {"pagecontent", "intro-notes"};

  /** Whether a folder name in a path is . or .., which stand for the input folder, or the folder it is in */
  private static boolean isDots(String folder) {
    return folder.equals(".") || folder.equals("..");
  }

  /** Whether the rest of the path from i in src goes through . or .. (input/includes/../images) */
  private static boolean dotsAfter(String src, int i) {
    int end = i;
    while (end < src.length() && "\"'<> \t\r\n;,".indexOf(src.charAt(end)) < 0) {
      end++;
    }
    String rest = src.substring(i, end) + "/";
    return rest.contains("/./") || rest.contains("/../");
  }

  /**
   * Why a watch mode rebuild can't skip running this template's scripts again after only the content of some pages
   * changed (see PageRebuild), or null if it can: it can if the template runs nothing just before Jekyll, and its
   * onLoad and onGenerate scripts don't read the whole input folder. (The folders in it that they read are in
   * pageFolderBlockers(). The check script runs again after Jekyll: see PageRebuild.checkOnCheckMessages())
   */
  public String pageOnlyRebuildBlocker() throws IOException {
    if (targetOnJekyll != null) {
      return "the template runs a script before Jekyll";
    }
    boolean targets = targetOnGenerate != null || targetOnLoad != null;
    if (!targets && script == null) {
      return null;
    }
    Map<java.nio.file.Path, String> files = scriptFiles(!targets);
    if (files == null) {
      return targets ? "the template's scripts can't be read" : null;
    }
    Map<String, String> scripts = scripts(files);
    String unread = unreadAntFile(files);
    if (unread != null) {
      return unread;
    }
    String all = String.join("\n", scripts.values());
    for (Map.Entry<String, String> e : scripts.entrySet()) {
      String blocker = scriptPageUse(e.getKey(), e.getValue(), all);
      if (blocker != null) {
        return blocker;
      }
    }
    return null;
  }

  /**
   * The folders in the input folder that the template's onLoad and onGenerate scripts read (by name, e.g. "includes"), each with
   * how ("the template's script ant.xml reads (${dir.input}/includes)"): a page-only rebuild can't take a change to a
   * file in one of them, because the scripts aren't run again
   */
  public Map<String, String> pageFolderBlockers() throws IOException {
    Map<String, String> res = new HashMap<>();
    boolean targets = targetOnGenerate != null || targetOnLoad != null;
    Map<java.nio.file.Path, String> files = (!targets && script == null) || targetOnJekyll != null ? null : scriptFiles(!targets);
    Map<String, String> scripts = files == null ? null : scripts(files);
    if (scripts != null) {
      String all = String.join("\n", scripts.values());
      for (Map.Entry<String, String> e : scripts.entrySet()) {
        for (Map.Entry<String, String> u : scriptFolderUse(e.getKey(), e.getValue(), all).entrySet()) {
          res.putIfAbsent(u.getKey(), u.getValue());
        }
      }
    }
    return res;
  }

  /**
   * The template's scripts (not the jars in the scripts folder) by their path from the template's folder
   * (scripts/ant.xml), or null if they can't be read
   */
  private Map<String, String> scripts(Map<java.nio.file.Path, String> files) throws IOException {
    Map<String, String> res = new java.util.TreeMap<>();
    for (Map.Entry<java.nio.file.Path, String> e : files.entrySet()) {
      // ant takes \ as a separator too
      res.put(scriptName(e.getKey()), e.getValue().replace('\\', '/'));
    }
    return res;
  }

  /** A template script's path from the template's folder */
  private String scriptName(java.nio.file.Path f) throws IOException {
    java.nio.file.Path tdir = new File(templateDir).getCanonicalFile().toPath();
    return (f.startsWith(tdir) ? tdir.relativize(f) : f).toString().replace(File.separatorChar, '/');
  }

  /**
   * The template's scripts (its folder of scripts, and the ant file it runs, wherever it is), by where they are, or null
   * if there are none
   */
  private Map<java.nio.file.Path, String> scriptFiles(boolean outsideTargets) throws IOException {
    Set<java.nio.file.Path> propertyFiles = new HashSet<>();
    Map<java.nio.file.Path, String> res = scriptFiles(propertyFiles);
    if (res == null || !outsideTargets) {
      return res;
    }
    // the tasks outside the targets, which ant runs when it loads the ant files (with the template), in the ant files
    java.nio.file.Path main = new File(Utilities.path(templateDir, script)).getCanonicalFile().toPath();
    Map<java.nio.file.Path, String> top = new java.util.TreeMap<>();
    for (Map.Entry<java.nio.file.Path, String> e : res.entrySet()) {
      if (e.getKey().getFileName().toString().endsWith(".xml") || e.getKey().equals(main)) {
        top.put(e.getKey(), TARGET.matcher(COMMENT.matcher(e.getValue()).replaceAll(" ")).replaceAll(" "));
      } else if (propertyFiles.contains(e.getKey())) {
        // (the properties that the tasks may use)
        top.put(e.getKey(), e.getValue());
      }
    }
    return top.isEmpty() ? null : top;
  }

  private static final java.util.regex.Pattern COMMENT = java.util.regex.Pattern.compile("<!--.*?-->", java.util.regex.Pattern.DOTALL);
  private static final java.util.regex.Pattern TARGET = java.util.regex.Pattern.compile(
      "<target\\b[^>]*?/>|<target\\b.*?</target>", java.util.regex.Pattern.DOTALL);

  private Map<java.nio.file.Path, String> scriptFiles(Set<java.nio.file.Path> propertyFiles) throws IOException {
    java.nio.file.Path dir = new File(Utilities.path(templateDir, "scripts")).getCanonicalFile().toPath();
    Map<java.nio.file.Path, String> res = new java.util.TreeMap<>();
    if (java.nio.file.Files.isDirectory(dir)) {
      try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(dir)) {
        for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files::iterator) {
          if (java.nio.file.Files.isRegularFile(f) && !f.getFileName().toString().endsWith(".jar")) {
            res.put(f.toFile().getCanonicalFile().toPath(), FileUtilities.fileToString(f.toFile()));
          }
        }
      } catch (java.io.UncheckedIOException e) {
        throw e.getCause();
      }
    }
    if (script != null) {
      File f = new File(Utilities.path(templateDir, script)).getCanonicalFile();
      if (f.isFile()) {
        res.putIfAbsent(f.toPath(), FileUtilities.fileToString(f));
      }
    }
    // and the property files they load that are in the template (with each property as an ant file defines it, so that
    // the properties are seen); those that aren't there yet are written by the scripts while the build runs
    String all = String.join("\n", res.values()).replace('\\', '/');
    java.nio.file.Path tdir = new File(templateDir).getCanonicalFile().toPath();
    java.util.regex.Matcher m = PROPERTY_FILE.matcher(all);
    Set<java.nio.file.Path> loaded = new HashSet<>();
    while (m.find()) {
      java.nio.file.Path f = m.group(1).equalsIgnoreCase("file") || m.group(1).equalsIgnoreCase("srcfile") ? propertyFile(m.group(2), all) : null;
      if (f != null && f.startsWith(tdir) && java.nio.file.Files.isRegularFile(f) && loaded.add(f)) {
        // (one in the scripts folder is read already, as text)
        String src = FileUtilities.fileToString(f.toFile());
        StringBuilder b = new StringBuilder(res.getOrDefault(f, src));
        for (String line : src.split("\\R")) {
          java.util.regex.Matcher kv = PROPERTY_LINE.matcher(line);
          if (kv.matches()) {
            b.append("\n<property name=\"").append(kv.group(1)).append("\" value=\"").append(kv.group(2).trim()).append("\"/>");
          }
        }
        res.put(f, b.toString());
        propertyFiles.add(f);
      }
    }
    return res.isEmpty() ? null : res;
  }

  // a property file that a script loads (by group 1, how it names it, and group 2, the name)
  private static final java.util.regex.Pattern PROPERTY_FILE = java.util.regex.Pattern.compile(
      "<(?:property|loadproperties|xmlproperty)\\s[^>]*?(?<=\\s)(file|srcfile|url|resource)=\"([^\"]*)\"",
      java.util.regex.Pattern.CASE_INSENSITIVE);
  // a property in a property file
  private static final java.util.regex.Pattern PROPERTY_LINE = java.util.regex.Pattern.compile("\\s*([^#!\\s=:][^=:\\s]*)\\s*[=:](.*)");

  /**
   * Where a property file that a script loads is (with the properties the scripts define, all, and those the publisher
   * sets for the template's folders), or null if that can't be told
   */
  private java.nio.file.Path propertyFile(String f, String all) throws IOException {
    for (int i = 0; i < 10 && f.contains("${"); i++) {
      java.util.regex.Matcher r = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}").matcher(f);
      if (!r.find()) {
        break;
      }
      String v = r.group(1).equals("ig.template") ? templateDir.replace('\\', '/')
          : r.group(1).equals("ig.scripts") ? Utilities.path(templateDir, "scripts").replace('\\', '/')
          : r.group(1).equals("ig.root") || r.group(1).equals("basedir") ? root.replace('\\', '/') : null;
      if (v == null) {
        java.util.regex.Matcher p = java.util.regex.Pattern.compile("<property\\s[^>]*?name=\"" + java.util.regex.Pattern.quote(r.group(1))
            + "\"[^>]*?\\s(?:value|location)=\"([^\"]*)\"").matcher(all);
        if (!p.find()) {
          return null;
        }
        v = p.group(1);
      }
      f = f.substring(0, r.start()) + v + f.substring(r.end());
    }
    if (f.contains("${")) {
      return null;
    }
    File res = new File(f);
    return (res.isAbsolute() ? res : new File(root, f)).getCanonicalFile().toPath();
  }

  // an ant file that a script imports
  private static final java.util.regex.Pattern ANT_FILE = java.util.regex.Pattern.compile(
      "<(?:import|include)\\s[^>]*?(?<=\\s)file=\"([^\"]*)\"");
  // another build or a program that a script runs (ant, subant, exec, apply, or a task it defines), or an ant file it
  // imports from the class path: they aren't read. (Not java: the base templates run plantuml with it, on the folder
  // their scripts name, so it would stop every page rebuild. One that is given the IG's folder itself is caught in
  // scriptPageUse)
  private static final java.util.regex.Pattern SUB_BUILD = java.util.regex.Pattern.compile(
      "<(?:ant|subant|exec|apply|taskdef|typedef)[\\s/>]|<(?:import|include)\\s[^>]*?(?<=\\s)resource=\"");
  // a style sheet or script that an ant file uses: it must be one of the template's scripts, which are read
  private static final java.util.regex.Pattern USED_FILE = java.util.regex.Pattern.compile(
      "<(?:xslt|style)\\s[^>]*?(?<=\\s)style=\"([^\"]*)\"|<script(?:def)?\\s[^>]*?(?<=\\s)src=\"([^\"]*)\"");

  // code in an ant file (by group 1 the task, group 2 its attributes, group 3 the code)
  private static final java.util.regex.Pattern INLINE_CODE = java.util.regex.Pattern.compile(
      "<(script|scriptdef|groovy|groovyc|jython|beanshell)\\b([^>]*?)(?<!/)>(.*?)</\\1>", java.util.regex.Pattern.DOTALL);
  private static final java.util.regex.Pattern LANGUAGE = java.util.regex.Pattern.compile("(?<=\\s)language=\"([^\"]*)\"");
  // a string in javascript
  // (written so that a long string doesn't take a long chain of calls to match)
  private static final java.util.regex.Pattern JS_STRING = java.util.regex.Pattern.compile(
      "\"([^\"\\\\\\n]*+(?:\\\\.[^\"\\\\\\n]*+)*+)\"|'([^'\\\\\\n]*+(?:\\\\.[^'\\\\\\n]*+)*+)'");
  // what javascript needs to read files, or run what isn't read: without them it can only work with properties (as
  // the base templates do) and run the targets in the scripts
  private static final java.util.regex.Pattern JS_FILE_USE = java.util.regex.Pattern.compile(
      "\\b(?:new|java|javax|Packages|importClass|importPackage|JavaImporter|load|loadWithNewGlobal|require|eval|Function|"
      + "Java|getBaseDir|createTask|createDataType|getReference|getReferences|getClass|forName|Runtime|readFully|(?i:\\w*file\\w*|\\w*path\\w*|\\w*dir\\w*))\\b");

  /**
   * Why the template's code (in javascript, the language that ant runs it in, or another, for tasks other than
   * script) can't be told not to read pages, if it can't: only javascript that uses no files, and doesn't name the
   * input folder or the IG's, can be
   */
  static String scriptCode(String name, String language, String code, String all) {
    String lang = language.toLowerCase();
    if (!(lang.equals("javascript") || lang.equals("ecmascript") || lang.equals("js"))) {
      return "the template's script " + name + " runs code (" + (language.isEmpty() ? "in a language it doesn't name" : language) + "), which isn't checked";
    }
    Set<String> roots = rootAliases(all);
    java.util.regex.Pattern input = inputRef(all);
    // the code as ant sees it (a scriptdef's attributes and elements aren't code)
    String js = code.replace("<![CDATA[", " ").replace("]]>", " ").replaceAll("<(?:attribute|element)\\s[^>]*>|</element>", " ");
    if (!code.contains("<![CDATA[")) {
      js = js.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }
    StringBuilder rest = new StringBuilder();
    java.util.regex.Matcher m = JS_STRING.matcher(js);
    int last = 0;
    while (m.find()) {
      String str = m.group(1) != null ? m.group(1) : m.group(2);
      java.util.regex.Matcher i = input.matcher("${" + str + "}");
      if (str.equals("ig.root") || str.equals("basedir") || roots.contains(str) || (i.matches() && i.group(3) == null)) {
        return "the template's script " + name + " runs code that uses the property " + str + ", which isn't checked";
      }
      rest.append(js, last, m.start()).append("\"\"");
      last = m.end();
    }
    rest.append(js.substring(last));
    java.util.regex.Matcher f = JS_FILE_USE.matcher(rest);
    if (f.find()) {
      return "the template's script " + name + " runs code that may read files (" + f.group() + "), which isn't checked";
    }
    return null;
  }

  /**
   * An ant file that a script runs or imports, or a style sheet or script it uses, that isn't one of the template's
   * scripts, if there is one
   */
  private String unreadAntFile(Map<java.nio.file.Path, String> files) throws IOException {
    String all = String.join("\n", files.values()).replace('\\', '/');
    java.nio.file.Path main = script == null ? null : new File(Utilities.path(templateDir, script)).getCanonicalFile().toPath();
    for (Map.Entry<java.nio.file.Path, String> e : files.entrySet()) {
      java.util.regex.Matcher m = ANT_FILE.matcher(e.getValue());
      while (m.find()) {
        String f = m.group(1);
        if (f.contains("$") || !files.containsKey(e.getKey().getParent().resolve(f).toFile().getCanonicalFile().toPath())) {
          return "the template's script " + scriptName(e.getKey()) + " runs " + f + ", which isn't read";
        }
      }
      java.util.regex.Matcher pf = PROPERTY_FILE.matcher(e.getValue());
      java.nio.file.Path tdir = new File(templateDir).getCanonicalFile().toPath();
      while (pf.find()) {
        java.nio.file.Path f = pf.group(1).equalsIgnoreCase("file") || pf.group(1).equalsIgnoreCase("srcfile")
            ? propertyFile(pf.group(2), all) : null;
        // one in the template is read, or the scripts write it
        if (f == null || !f.startsWith(tdir) || (java.nio.file.Files.exists(f) && !files.containsKey(f))) {
          return "the template's script " + scriptName(e.getKey()) + " loads properties from " + pf.group(2) + ", which isn't read";
        }
      }
      java.util.regex.Matcher b = SUB_BUILD.matcher(e.getValue());
      if (b.find()) {
        return "the template's script " + scriptName(e.getKey()) + " runs another build or a program (" + b.group().trim() + "), which isn't read";
      }
      String code = e.getKey().getFileName().toString().endsWith(".js") ? scriptCode(scriptName(e.getKey()), "javascript", e.getValue(), all) : null;
      if (code != null) {
        return code;
      }
      // an ant file (not a style sheet, which may write html with scripts in it)
      if (e.getKey().getFileName().toString().endsWith(".xml") || e.getKey().equals(main)) {
        java.util.regex.Matcher c = INLINE_CODE.matcher(e.getValue());
        while (c.find()) {
          java.util.regex.Matcher l = LANGUAGE.matcher(c.group(2));
          code = scriptCode(scriptName(e.getKey()), c.group(1).startsWith("script") ? (l.find() ? l.group(1) : "") : c.group(1), c.group(3), all);
          if (code != null) {
            return code;
          }
        }
        java.util.regex.Matcher u = USED_FILE.matcher(e.getValue());
        while (u.find()) {
          String f = u.group(1) != null ? u.group(1) : u.group(2);
          if (!f.contains("${") && !new File(f).isAbsolute() && !new File(Utilities.path(root, f)).exists()) {
            // a path without properties is in the IG's folder (the ant project's basedir): if there's no such file,
            // the task reads nothing (fhir2.base.template has style="{$ig.scripts}/..." with failonerror="false")
            continue;
          }
          String rest = f.startsWith("${ig.scripts}/") ? f.substring("${ig.scripts}/".length()) : null;
          if (rest == null || rest.contains("$")
              || !files.containsKey(new File(Utilities.path(templateDir, "scripts", rest)).getCanonicalFile().toPath())) {
            return "the template's script " + scriptName(e.getKey()) + " uses " + f + ", which isn't read";
          }
        }
      }
    }
    return null;
  }

  /**
   * The folders in the input folder that a template script (an ant file) reads, each with how: those it names after
   * the input folder (directly, or through a property defined as it), and the page folders it names anywhere
   */
  static Map<String, String> scriptFolderUse(String name, String src) {
    return scriptFolderUse(name, src, src);
  }

  /** scriptFolderUse, with the properties defined in all the template's scripts (all): ant properties are global */
  static Map<String, String> scriptFolderUse(String name, String src, String all) {
    Map<String, String> res = new java.util.LinkedHashMap<>();
    java.util.regex.Matcher m = inputRef(all).matcher(src);
    while (m.find()) {
      if (m.group(3) != null && !isDots(m.group(3))) {
        res.putIfAbsent(m.group(3), "the template's script " + name + " reads (" + m.group() + ")");
      }
    }
    java.util.regex.Matcher r = INPUT_PATH.matcher(src);
    while (r.find()) {
      if (!isDots(r.group(1))) {
        res.putIfAbsent(r.group(1), "the template's script " + name + " reads (" + r.group() + ")");
      }
    }
    for (String f : PAGE_FOLDER_NAMES) {
      if (src.contains(f)) {
        res.putIfAbsent(f, "the template's script " + name + " names");
      }
    }
    return res;
  }

  /**
   * How a template script (an ant file) can read any file in the input folder, or null if it can't: it uses the input
   * folder (directly, or through a property defined as it) other than to define a property, or selects everything
   * under the IG's folder
   */
  static String scriptPageUse(String name, String src) {
    return scriptPageUse(name, src, src);
  }

  /** scriptPageUse, with the properties defined in all the template's scripts (all): ant properties are global */
  static String scriptPageUse(String name, String src, String all) {
    java.util.regex.Pattern inputRef = inputRef(all);
    java.util.regex.Matcher m = inputRef.matcher(src);
    while (m.find()) {
      if (m.group(3) != null && (isDots(m.group(3)) || dotsAfter(src, m.end()))) {
        return "the template's script " + name + " reads " + m.group();
      }
      if (m.group(3) == null && !inPropertyDefinition(src, m.start())) {
        return "the template's script " + name + " reads " + m.group();
      }
    }
    // a property defined as a folder in the input folder, and a path through it that goes up out of the folder
    java.util.regex.Matcher d = PROPERTY.matcher(all);
    while (d.find()) {
      if (inputRef.matcher(d.group()).find() || INPUT_PATH.matcher(d.group()).find()) {
        java.util.regex.Matcher u = java.util.regex.Pattern.compile("\\$\\{" + java.util.regex.Pattern.quote(d.group(1)) + "\\}").matcher(src);
        while (u.find()) {
          if (dotsAfter(src, u.end())) {
            return "the template's script " + name + " reads " + u.group() + " (" + d.group() + ") and goes out of the folder";
          }
        }
      }
    }
    java.util.regex.Matcher w = INPUT_WHOLE.matcher(src);
    while (w.find()) {
      java.util.regex.Matcher p = INPUT_PATH.matcher(src).region(w.start(), src.length());
      boolean named = w.group().endsWith("/") && p.lookingAt();
      // input/.. is the IG's folder, even as a property
      boolean dots = named && (isDots(p.group(1)) || dotsAfter(src, p.end()));
      if (dots || (!named && !inPropertyDefinition(src, w.start()))) {
        int end = w.end();
        while (end < src.length() && "\"'<> \t\r\n".indexOf(src.charAt(end)) < 0) {
          end++;
        }
        return "the template's script " + name + " reads " + src.substring(w.start(), end);
      }
    }
    Set<String> roots = rootAliases(all);
    java.util.regex.Matcher o = PARENT.matcher(src);
    while (o.find()) {
      // straight up out of the IG's folder (${ig.root}/../schemas) is outside it
      boolean outside = o.group(1) != null && o.group(2).isEmpty()
          && (o.group(1).equals("ig.root") || o.group(1).equals("basedir") || roots.contains(o.group(1)));
      if (!outside) {
        return "the template's script " + name + " reads " + o.group() + ", which may be the IG's folder";
      }
    }
    java.util.regex.Matcher r = java.util.regex.Pattern.compile(String.format(ROOT_DIR, alternatives(roots))).matcher(src);
    while (r.find()) {
      // what it selects: the includes attribute, or what's in the element
      String selection = r.group();
      if (!r.group().endsWith("/>")) {
        int end = src.indexOf("</" + r.group(1) + ">", r.end());
        selection = end < 0 ? src.substring(r.start()) : src.substring(r.start(), end);
      }
      if (!INCLUDES.matcher(selection).find() || INCLUDES_FILE.matcher(selection).find() || selection.contains("**")
          || WILD_FIRST.matcher(selection).find()) {
        return "the template's script " + name + " reads everything under the IG's folder";
      }
    }
    java.util.regex.Matcher j = JAVA.matcher(src);
    while (j.find()) {
      int end = j.group().endsWith("/>") ? j.end() : src.indexOf("</java>", j.end());
      String task = end < 0 ? src.substring(j.start()) : src.substring(j.start(), end);
      if (java.util.regex.Pattern.compile(String.format(ROOT_ARG, alternatives(roots))).matcher(task).find()) {
        return "the template's script " + name + " gives the IG's folder to a program (<java>), which isn't read";
      }
    }
    return null;
  }

  // what a selection under the IG's folder includes: an includes attribute or include element (a list in another file,
  // includesfile, may name anything)
  private static final java.util.regex.Pattern INCLUDES = java.util.regex.Pattern.compile(
      "(?<=\\s)includes=\"|<include[\\s/>]");
  private static final java.util.regex.Pattern INCLUDES_FILE = java.util.regex.Pattern.compile(
      "(?<=\\s)includesfile=\"|<includesfile[\\s/>]");
  // a program that a script runs with java, and the IG's folder itself (not a folder in it) given to it
  private static final java.util.regex.Pattern JAVA = java.util.regex.Pattern.compile("<java[\\s/>][^>]*>");
  private static final String ROOT_ARG =
      "\\$\\{(?:ig\\.root|basedir%s)\\}/?(?![^\"'\\s&;<>])|(?<=\\s)\\w+=\"\\./?\"";

  // a pattern in an include whose first folder is a wildcard (*/includes/menu.xml), so it may be the input folder
  private static final java.util.regex.Pattern WILD_FIRST = java.util.regex.Pattern.compile(
      "(?<=\\s)(?:name|includes)=\"(?:[^\"]*[,\\s])?[^\"/,\\s]*[*?]");

  private static boolean inPropertyDefinition(String src, int i) {
    int tag = src.lastIndexOf('<', i);
    return tag >= 0 && src.startsWith("<property", tag) && src.indexOf('>', tag) > i;
  }

  /**
   * The pattern for a reference to the input folder in src, including through the properties src defines as the input
   * folder (e.g. &lt;property name="in" value="${folder.input}"/&gt; makes ${in} one)
   */
  private static java.util.regex.Pattern inputRef(String src) {
    Set<String> roots = rootAliases(src);
    Set<String> aliases = new HashSet<>();
    boolean more = true;
    while (more) {
      more = false;
      java.util.regex.Pattern ref = inputRef(aliases, roots);
      java.util.regex.Matcher p = PROPERTY.matcher(src);
      while (p.find()) {
        java.util.regex.Matcher m = ref.matcher(p.group());
        if ((m.find() && m.group(3) == null || INPUT_VALUE.matcher(p.group()).find()) && !aliases.contains(p.group(1))) {
          aliases.add(p.group(1));
          more = true;
        }
      }
    }
    return inputRef(aliases, roots);
  }

  private static java.util.regex.Pattern inputRef(Set<String> aliases, Set<String> roots) {
    return java.util.regex.Pattern.compile(String.format(INPUT_REF, alternatives(aliases), alternatives(roots)));
  }

  /** The properties src defines as the IG's folder (e.g. &lt;property name="root" value="${ig.root}"/&gt;) */
  private static Set<String> rootAliases(String src) {
    Set<String> aliases = new HashSet<>();
    // a folder that holds another may be the IG's folder
    java.util.regex.Matcher d = DIRNAME.matcher(src);
    while (d.find()) {
      aliases.add(d.group(1));
    }
    boolean more = true;
    while (more) {
      more = false;
      java.util.regex.Matcher p = PROPERTY.matcher(src);
      while (p.find()) {
        java.util.regex.Matcher v = ROOT_VALUE.matcher(p.group());
        if (v.find() && (v.group(1) == null || v.group(1).equals("ig.root") || v.group(1).equals("basedir") || aliases.contains(v.group(1)))
            && aliases.add(p.group(1))) {
          more = true;
        }
      }
    }
    return aliases;
  }

  private static String alternatives(Set<String> names) {
    StringBuilder alt = new StringBuilder();
    for (String a : names) {
      alt.append('|').append(java.util.regex.Pattern.quote(a));
    }
    return alt.toString();
  }

  /** Whether the template runs a script while the IG is generated, before Jekyll */
  public boolean hasPreProcess() {
    return preProcess != null;
  }

  /** Whether the template has a script that runs on the output after Jekyll */
  public boolean hasOnCheck() {
    return targetOnCheck != null;
  }

  /**
   * What the template's check may run and still be known not to look at the output: these Ant tasks, as Ant defines
   * them (by class: a template can define its own under the same name), and these elements in them, each with the
   * attributes it may have. Of those, the ones that name a file or folder (PATH_ATTRIBUTES) must be in the template
   * folder, and the ones that are patterns (PATTERN_ATTRIBUTES) must stay in the folder they're in
   */
  private static final Map<String, Class<?>> BLIND_TASKS = Map.of(
      "echo", org.apache.tools.ant.taskdefs.Echo.class,
      "concat", org.apache.tools.ant.taskdefs.Concat.class,
      "replaceregexp", org.apache.tools.ant.taskdefs.optional.ReplaceRegExp.class);
  private static final Map<String, Set<String>> BLIND_ELEMENTS = Map.of(
      "echo", Set.of("file", "message", "append", "encoding", "level"),
      "concat", Set.of("destfile", "encoding", "outputencoding", "append", "fixlastline", "eol"),
      "replaceregexp", Set.of("file", "match", "replace", "flags", "byline", "encoding"),
      "fileset", Set.of("dir", "includes", "excludes", "defaultexcludes", "casesensitive"),
      "filterchain", Set.of(),
      "tailfilter", Set.of("lines", "skip"),
      "headfilter", Set.of("lines", "skip"));
  private static final Set<String> PATH_ATTRIBUTES = Set.of("file", "destfile", "dir");
  private static final Set<String> PATTERN_ATTRIBUTES = Set.of("includes", "excludes");

  // a property expansion that evaluates something (${toString:id}, ${ant.refid:id}...) rather than reading a property
  private static final Pattern EVALUATED_PROPERTY = Pattern.compile("\\$\\{[^}]*:");

  /**
   * Why the template's check (onCheck) may look at the output, in outputDir; null if it can't. It can't if all it
   * runs - its target, and the targets that target depends on, including those that extend an extension point it
   * depends on (extensionOf), whatever file they're in - is tasks that only read and write files in the template
   * folder (see BLIND_TASKS), the template folder has no links (a link could lead to the output), and nothing the
   * script ran before is left to run with it (another listener on the script's project, another way to expand
   * properties, a target, task or condition of the script's own kind). Anything else, or anything that can't be told
   * for sure, is a reason it may. (What isn't looked at: what code the script ran before hides on purpose, like a
   * thread left running; and, where the file system doesn't tell how many names a file has, hard links.)
   */
  public String whyOnCheckMaySeeOutput(String outputDir) {
    if (targetOnCheck == null) {
      return null;
    }
    if (antProject == null) {
      return "it has no script";
    }
    try {
      return whyOnCheckMaySee(Paths.get(outputDir));
    } catch (IOException | ReflectiveOperationException | RuntimeException e) {
      return "it can't be told (" + e + ")";
    }
  }

  private String whyOnCheckMaySee(Path output) throws IOException, ReflectiveOperationException {
    Path tmpl = Paths.get(templateDir).toAbsolutePath().normalize();
    if (output.toAbsolutePath().normalize().startsWith(tmpl)
        || (Files.exists(output) && output.toRealPath().startsWith(tmpl.toRealPath()))) {
      return "the output is in the template folder";
    }
    String why = whyFolderMayLeadOut(tmpl);
    if (why != null) {
      return why;
    }
    for (BuildListener l : antProject.getBuildListeners()) {
      // Ant's own class loaders listen, for when the build is over
      if (l != antLogger && l.getClass() != AntClassLoader.class) {
        return "its script's project has other listeners (" + l.getClass().getName() + ")";
      }
    }
    if (!onlyAntsPropertyEvaluation()) {
      return "its script expands properties its own way";
    }
    // a property may hold what it refers to (value="${ant.refid:id}"), and what that is is evaluated when it's used
    PropertyHelper ph = PropertyHelper.getPropertyHelper(antProject);
    for (Map<String, Object> props : List.of(ph.getProperties(), ph.getUserProperties())) {
      for (Map.Entry<String, Object> p : props.entrySet()) {
        if (p.getValue() != null && p.getValue().getClass() != String.class) {
          return "property '" + p.getKey() + "' holds a " + p.getValue().getClass().getName();
        }
      }
    }
    List<Target> run;
    try {
      run = antProject.topoSort(targetOnCheck, antProject.getTargets(), false);
    } catch (BuildException e) {
      return "its targets can't be ordered (" + e.getMessage() + ")";
    }
    ComponentHelper components = ComponentHelper.getComponentHelper(antProject);
    if (components.getClass() != ComponentHelper.class || components.getProject() != antProject) {
      return "its script makes tasks its own way";
    }
    for (Target t : run) {
      if (t.getClass() != Target.class && t.getClass() != ExtensionPoint.class) {
        return "target '" + t.getName() + "' is a " + t.getClass().getName();
      }
      why = t.getProject() != antProject ? "belongs to another project" : whyTargetMaySee(t);
      if (why != null) {
        return "target '" + t.getName() + "' " + why;
      }
      for (Task task : t.getTasks()) {
        String tag = task.getClass() == UnknownElement.class ? ((UnknownElement) task).getTag() : task.getTaskName();
        Class<?> cls = BLIND_TASKS.get(tag);
        AntTypeDefinition def = cls == null ? null : components.getDefinition(tag);
        // Ant's own definition: a macro or preset of the template's own is a subclass, and a taskdef may load the
        // class from elsewhere
        if (task.getClass() != UnknownElement.class || def == null || def.getClass() != AntTypeDefinition.class
            || def.getExposedClass(antProject) != cls || def.getTypeClass(antProject) != cls) {
          return "target '" + t.getName() + "' runs " + tag;
        }
        why = whyElementMaySeeOutput((UnknownElement) task, tmpl);
        if (why != null) {
          return "target '" + t.getName() + "': " + why;
        }
      }
    }
    return null;
  }

  /**
   * Why a target may run more than its tasks (see whyElementMaySeeOutput()): a condition (if, unless) that may
   * evaluate something, or one that isn't a property's, or something to run that isn't one of its tasks
   */
  private static String whyTargetMaySee(Target t) throws ReflectiveOperationException {
    Target plain = new Target();
    plain.setIf("x");
    plain.setUnless("x");
    for (String c : new String[] {"if", "unless"}) {
      Object cond = field(Target.class, c + "Condition", t);
      String prop = "if".equals(c) ? t.getIf() : t.getUnless();
      if (cond != null && (cond.getClass() != field(Target.class, c + "Condition", plain).getClass() || capturedBy(cond) != t)) {
        return "has a condition of its own (" + cond.getClass().getName() + ")";
      }
      // the condition is a property's name, and a name with ':' may be evaluated (toString:id), what it expands to too
      if (prop != null && (prop.contains(":") || prop.contains("$"))) {
        return c + "=\"" + prop + "\"";
      }
    }
    List<?> children = (List<?>) field(Target.class, "children", t);
    for (Object c : children) {
      if (!(c instanceof Task)) {
        return "runs " + c.getClass().getName();
      }
    }
    return null;
  }

  /** The target a condition made by Target.setIf(String) reads its property's name from */
  private static Object capturedBy(Object cond) throws ReflectiveOperationException {
    Object res = null;
    int n = 0;
    for (Field f : cond.getClass().getDeclaredFields()) {
      if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
        f.setAccessible(true);
        res = f.get(cond);
        n++;
      }
    }
    return n == 1 ? res : null;
  }

  private static Object field(Class<?> cls, String name, Object o) throws ReflectiveOperationException {
    Field f = cls.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(o);
  }

  /** Why what is in this folder may lead out of it: a symbolic link (or junction), or a file with another name (a hard link) */
  private static String whyFolderMayLeadOut(Path folder) throws IOException {
    boolean unix = folder.getFileSystem().supportedFileAttributeViews().contains("unix");
    Path real = folder.toRealPath();
    try (Stream<Path> files = Files.walk(folder)) {
      for (Path p : (Iterable<Path>) files::iterator) {
        BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        // (on Windows, what isn't a file or a folder may be a junction, or only a file in the cloud)
        if (a.isSymbolicLink() || (unix && a.isOther()) || ((a.isDirectory() || a.isOther()) && !p.toRealPath().startsWith(real))) {
          return "the template folder has a link, " + folder.relativize(p);
        }
        if (unix && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
            && ((Number) Files.getAttribute(p, "unix:nlink", LinkOption.NOFOLLOW_LINKS)).intValue() > 1) {
          return "the template folder has a file with another name too, " + folder.relativize(p);
        }
      }
    }
    return null;
  }

  /** Whether the script's properties are expanded as Ant expands them by default (and nothing else runs when they are) */
  private boolean onlyAntsPropertyEvaluation() throws ReflectiveOperationException {
    PropertyHelper ph = PropertyHelper.getPropertyHelper(antProject);
    if (ph.getClass() != PropertyHelper.class) {
      return false;
    }
    Project plain = new Project();
    plain.init();
    Set<Object> ants = new HashSet<>();
    for (List<?> l : delegates(PropertyHelper.getPropertyHelper(plain)).values()) {
      ants.addAll(l);
    }
    for (List<?> l : delegates(ph).values()) {
      for (Object d : l) {
        // Ant's own <local> properties: running a target sets them up
        if (!ants.contains(d) && d.getClass() != LocalProperties.class) {
          return false;
        }
      }
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  private static Map<?, List<?>> delegates(PropertyHelper ph) throws ReflectiveOperationException {
    return (Map<?, List<?>>) field(PropertyHelper.class, "delegates", ph);
  }

  private String whyElementMaySeeOutput(UnknownElement e, Path tmpl) {
    Set<String> allowed = BLIND_ELEMENTS.get(e.getTag());
    RuntimeConfigurable w = e.getWrapper();
    if (e.getClass() != UnknownElement.class || w == null || w.getClass() != RuntimeConfigurable.class || e.getRealThing() != null
        || e.getProject() != antProject) {
      return "<" + e.getQName() + "> of its own kind";
    }
    if (allowed == null || !e.getNamespace().isEmpty() || w.getPolyType() != null) {
      return "<" + e.getQName() + (w.getPolyType() != null ? " ant-type=\"" + w.getPolyType() + "\"" : "") + ">";
    }
    if (w.getText() != null && EVALUATED_PROPERTY.matcher(w.getText()).find()) {
      return "<" + e.getTag() + ">" + w.getText();
    }
    for (Map.Entry<String, Object> a : w.getAttributeMap().entrySet()) {
      // what else is there is evaluated when the task is set up
      if (a.getValue() == null || a.getValue().getClass() != String.class) {
        return "<" + e.getTag() + " " + a.getKey() + "> of its own kind";
      }
      String n = a.getKey().toLowerCase();
      String raw = String.valueOf(a.getValue());
      if (!allowed.contains(n) || EVALUATED_PROPERTY.matcher(raw).find()) {
        return "<" + e.getTag() + " " + a.getKey() + "=\"" + raw + "\">";
      }
      String v = antProject.replaceProperties(raw);
      if (PATH_ATTRIBUTES.contains(n) || PATTERN_ATTRIBUTES.contains(n)) {
        // a property that isn't set yet may be by the time it runs
        boolean inside = !v.contains("${");
        if (inside && PATH_ATTRIBUTES.contains(n)) {
          inside = antProject.resolveFile(v).toPath().toAbsolutePath().normalize().startsWith(tmpl);
        } else if (inside) {
          for (String pt : v.split("[,\\s]+")) {
            inside = inside && !pt.startsWith("/") && !pt.startsWith("\\") && !pt.contains(":") && !pt.contains("..");
          }
        }
        if (!inside) {
          return "<" + e.getTag() + " " + a.getKey() + "=\"" + v + "\">";
        }
      }
    }
    // Ant sets up what is nested from the wrappers' own children
    List<UnknownElement> children = e.getChildren() == null ? new ArrayList<>() : e.getChildren();
    List<RuntimeConfigurable> wrappers = Collections.list(w.getChildren());
    if (wrappers.size() != children.size()) {
      return "<" + e.getTag() + "> of its own kind";
    }
    for (int i = 0; i < children.size(); i++) {
      UnknownElement c = children.get(i);
      if (c.getWrapper() != wrappers.get(i)) {
        return "<" + c.getTag() + "> of its own kind";
      }
      String why = whyElementMaySeeOutput(c, tmpl);
      if (why != null) {
        return why;
      }
    }
    return null;
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
  
  /**
   * Told when the template writes to its own folder (templateDir): a script runs, or the translations are processed.
   * Watch mode uses it for a template that is used in place (template = #template), whose folder is watched: what
   * the build writes there is the build's own output, not a change that needs another build
   */
  public interface OutputListener {
    /**
     * @param folder  the folder written to (templateDir)
     * @param window  what writes: the script target, or "translations"
     * @param managed the paths (relative to folder, with '/') that are written there as the build's output; other
     *                changes made meanwhile may be the user's
     */
    void writing(String folder, String window, Predicate<String> managed) throws IOException;

    /** the writing has finished (or failed) */
    void written() throws IOException;
  }

  private interface Writing<T> {
    T run() throws IOException, FHIRException;
  }

  public void setOutputListener(OutputListener outputListener) {
    this.outputListener = outputListener;
  }

  /**
   * What a script target writes to the template folder:
   * <ul>
   *   <li>the IG it is given and gives back, under the names it is given (&lt;target&gt;-ig-working.json/xml and
   *   &lt;target&gt;-ig-updated.xml/json), and what the base template's scripts derive from them
   *   (&lt;target&gt;-ig-working.1.xml...)</li>
   *   <li>its outcomes, &lt;target&gt;-validation.json/xml, and the files the base template's scripts gather them
   *   from (ongenerate-validation-igqa.json...)</li>
   *   <li>versions.txt (the base template's onGenerate.checkVersions)</li>
   * </ul>
   * (see runScriptTargetAnt() and scripts/ant.xml in fhir.base.template). Files directly in the folder only
   */
  static Predicate<String> scriptOutputs(String target) {
    Pattern p = Pattern.compile("(" + Pattern.quote(target) + "-ig-(working|updated)\\.[^/]+"
        + "|" + Pattern.quote(target) + "-validation[^/]*\\.(json|xml)"
        + "|versions\\.txt)", Pattern.CASE_INSENSITIVE);
    return path -> p.matcher(path).matches();
  }

  /** What processTemplateTranslations() writes: the translation files, directly in translations/ */
  static Predicate<String> translationOutputs() {
    Pattern p = Pattern.compile("translations/[^/]+\\.(po|json|xml)");
    return path -> p.matcher(path).matches();
  }

  private <T> T writing(String window, Predicate<String> managed, Writing<T> work) throws IOException, FHIRException {
    if (outputListener == null) {
      return work.run();
    }
    outputListener.writing(templateDir, window, managed);
    T res;
    try {
      res = work.run();
    } catch (Throwable e) {
      try {
        outputListener.written();
      } catch (IOException e2) {
        e.addSuppressed(e2); // the failure itself is the one to report
      }
      throw e;
    }
    outputListener.written();
    return res;
  }

  private ImplementationGuide runScriptTarget(IWorkerContext context, String target, Map<String, List<ValidationMessage>> messages, ImplementationGuide ig, List<String> fileNames, int modifyIg) throws IOException, FHIRException {
    return writing(target, scriptOutputs(target), () -> runScriptTargetAnt(context, target, messages, ig, fileNames, modifyIg));
  }

  private ImplementationGuide runScriptTargetAnt(IWorkerContext context, String target, Map<String, List<ValidationMessage>> messages, ImplementationGuide ig, List<String> fileNames, int modifyIg) throws IOException, FHIRException {
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
    File jsonUpdated = new File(fn + "json");
    File xmlUpdated = new File(fn + "xml");
    if (ig != null) {
      antProject.setProperty(target + ".ig.source", sfn);
      antProject.setProperty(target + ".ig.dest", fn);
      if (jsonIg.exists())
        jsonIg.delete();
      if (xmlIg.exists())
        xmlIg.delete();
      // a file left over from an earlier run would otherwise be read back as this run's output
      Files.deleteIfExists(jsonUpdated.toPath());
      Files.deleteIfExists(xmlUpdated.toPath());
      if (USE_R5_IG_FORMAT) {
        org.hl7.fhir.r5.model.ImplementationGuide ig5 = (org.hl7.fhir.r5.model.ImplementationGuide) VersionConvertorFactory_50_N.convertResource(ig, new BaseAdvisor_50_N(true, true));
        try (FileOutputStream f = new FileOutputStream(sfn+"xml")) {
          new org.hl7.fhir.r5.formats.XmlParser().compose(f, ig5);
        }
        try (FileOutputStream f = new FileOutputStream(sfn+"json")) {
          new org.hl7.fhir.r5.formats.JsonParser().compose(f, ig5);
        }
      } else {
        org.hl7.fhir.r4.model.ImplementationGuide ig4 = (org.hl7.fhir.r4.model.ImplementationGuide) VersionConvertorFactory_40_N.convertResource(ig, new BaseAdvisor_40_N(true, true));
        try (FileOutputStream f = new FileOutputStream(sfn+"xml")) {
          new org.hl7.fhir.r4.formats.XmlParser().compose(f, ig4);
        }
        try (FileOutputStream f = new FileOutputStream(sfn+"json")) {
          new org.hl7.fhir.r4.formats.JsonParser().compose(f, ig4);
        }
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
    IModelContext modelContext = context == null ? ModelContext.fullCoreContext() : context.getModelContext();
    // the outcomes file is written by the template's script, not by us, so a malformed one is a
    // problem with the template - report it against the template rather than killing the build
    if (jsonOutcomes.exists()) {
      OperationOutcome oo = readOutcomes(target, jsonOutcomes, new JsonParser(modelContext), messages);
      if (oo != null) {
        loadValidationMessages(oo, messages);
      }
    } else if (xmlOutcomes.exists()) {
      OperationOutcome oo = readOutcomes(target, xmlOutcomes, new XmlParser(modelContext), messages);
      if (oo != null) {
        loadValidationMessages(oo, messages);
      }
    }
    if (ig != null) {
      String newXml = fn+"xml";
      String newJson = fn+"json";
      switch (modifyIg) {
        case IG_ANY:
          if (new File(newXml).exists()) {
            try (FileInputStream f = new FileInputStream(newXml)) {
              if (USE_R5_IG_FORMAT) {
                return (ImplementationGuide) VersionConvertorFactory_50_N.convertResource(new org.hl7.fhir.r5.formats.XmlParser().parse(f));
              } else {
                return (ImplementationGuide) VersionConvertorFactory_40_N.convertResource(new org.hl7.fhir.r4.formats.XmlParser().parse(f));
              }
            }
          } else if (new File(newJson).exists()) {
            try (FileInputStream f = new FileInputStream(newJson)) {
              if (USE_R5_IG_FORMAT) {
                return (ImplementationGuide) VersionConvertorFactory_50_N.convertResource(new org.hl7.fhir.r5.formats.JsonParser().parse(f));
              } else {
                return (ImplementationGuide) VersionConvertorFactory_40_N.convertResource(new org.hl7.fhir.r4.formats.JsonParser().parse(f));
              }
            }
          } else
            throw new FHIRException("onLoad script "+targetOnLoad+" failed - no output file produced");        
        case IG_NO_RESOURCE:
          if (new File(newXml).exists()) {
            try (FileInputStream f = new FileInputStream(newXml)) {
              if (USE_R5_IG_FORMAT) {
                loadModifiedIg((ImplementationGuide) VersionConvertorFactory_50_N.convertResource(new org.hl7.fhir.r5.formats.XmlParser().parse(f)), ig);
              } else {
                loadModifiedIg((ImplementationGuide) VersionConvertorFactory_40_N.convertResource(new org.hl7.fhir.r4.formats.XmlParser().parse(f)), ig);
              }
            }
          }
          else if (new File(newJson).exists()) {
            try (FileInputStream f = new FileInputStream(newJson)) {
              if (USE_R5_IG_FORMAT) {
                loadModifiedIg((ImplementationGuide) VersionConvertorFactory_50_N.convertResource(new org.hl7.fhir.r5.formats.JsonParser().parse(f)), ig);
              } else {
                loadModifiedIg((ImplementationGuide) VersionConvertorFactory_40_N.convertResource(new org.hl7.fhir.r4.formats.JsonParser().parse(f)), ig);
              }
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
    int oc = ig.getDefinition().getResourceList().size();
    int nc = modIg.getDefinition().getResourceList().size();
    if (oc != nc)
      throw new FHIRException("Templates are not allowed to modify the resources ("+oc+"/"+nc+")");
    for (ImplementationGuideDefinitionResourceComponent or : ig.getDefinition().getResourceList()) {
      ImplementationGuideDefinitionResourceComponent nr = getMatchingResource(modIg, or.getReference());
      if (nr == null)
        throw new FHIRException("Templates are not allowed to modify the resources - didn't find '"+or.getReference()+"'");
    }
    ig.setDefinition(modIg.getDefinition());
    ig.getManifest().setPageList(modIg.getManifest().getPageList());
    ig.getManifest().setImageList(modIg.getManifest().getImageList());
    ig.getManifest().setOtherList(modIg.getManifest().getOtherList());
  }

  private ImplementationGuideDefinitionResourceComponent getMatchingResource(ImplementationGuide modIg, Reference reference) {
    for (ImplementationGuideDefinitionResourceComponent nr : modIg.getDefinition().getResourceList()) {
      if (nr.getReference().getReference().equals(reference.getReference()))
        return nr;
    }
    return null;
  }

  /**
   * The template script writes the outcomes file itself, so it can be malformed in ways we have no
   * control over (a stray trailing comma in the issue array, a truncated write, the wrong resource
   * type). That should not stop the build: report it as an error attributed to the template, and
   * carry on without the messages that file was carrying.
   * <p>
   * Note that only the read is guarded. Once the outcome is in hand, loadValidationMessages() is
   * called outside this method, so a FATAL issue from the template still aborts the build as it
   * always has.
   *
   * @return the parsed OperationOutcome, or null if the file could not be read
   */
  private OperationOutcome readOutcomes(String target, File outcomes, IParser parser, Map<String, List<ValidationMessage>> res) {
    try (FileInputStream f = new FileInputStream(outcomes)) {
      return (OperationOutcome) parser.parse(f);
    } catch (IOException | FHIRException | ClassCastException e) {
      if (!res.containsKey("")) {
        res.put("", new ArrayList<>());
      }
      res.get("").add(new ValidationMessage(Source.Template, IssueType.STRUCTURE, outcomes.getName(),
          "The template did not produce a readable outcomes file for '" + target + "' (" + outcomes.getAbsolutePath() + "): " + e.getMessage()
          + ". Any messages the template reported for this step have been lost", IssueSeverity.ERROR));
      return null;
    }
  }

  private void loadValidationMessages(OperationOutcome op, Map<String, List<ValidationMessage>> res) throws FHIRException {
    for (OperationOutcomeIssueComponent issue : op.getIssueList()) {
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

  public JsonObject getConfig(String type, String id, boolean allowAny) {
    if (defaults!=null) {
      if (defaults.has(type))
        return (JsonObject)defaults.get(type);
      else if (allowAny && defaults.has("Any"))
        return (JsonObject)defaults.get("Any");
    }
    return null;
  }

  public ImplementationGuide onLoadEvent(ImplementationGuide ig, Map<String, List<ValidationMessage>> messages) throws IOException, FHIRException {
    if (targetOnLoad == null)
      return ig;
    else
      return runScriptTarget(null, targetOnLoad, messages, ig, null, IG_ANY);
  }
  
  public Map<String, List<ValidationMessage>> beforeGenerateEvent(IWorkerContext context, ImplementationGuide ig, String tempDir, Set<String> fileList, List<String> newFileList, List<String> translationLangs) throws IOException, FHIRException {
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
      runScriptTarget(context, targetOnGenerate, messages, ig, newFileList, IG_NO_RESOURCE);
      return messages;
    } else
      return null;
  }

  public Map<String, List<ValidationMessage>> beforeJekyllEvent(IWorkerContext context, ImplementationGuide ig, List<String> newFileList) throws IOException, FHIRException {
    if (targetOnJekyll != null) {
      Map<String, List<ValidationMessage>> messages = new HashMap<String, List<ValidationMessage>>();
      runScriptTarget(context, targetOnJekyll, messages, null, newFileList, IG_NONE);
      return messages;
    } else
      return null;
  }

  public Map<String, List<ValidationMessage>> onCheckEvent(IWorkerContext context, ImplementationGuide ig) throws IOException, FHIRException {
    if (targetOnCheck != null) {
      Map<String, List<ValidationMessage>> messages = new HashMap<String, List<ValidationMessage>>();
      runScriptTarget(context, targetOnCheck, messages, null, null, IG_NONE);
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

  public boolean isDevMode() {
    return devMode;
  }
}
