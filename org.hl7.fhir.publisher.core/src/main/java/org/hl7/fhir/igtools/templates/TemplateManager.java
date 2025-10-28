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
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.ILoggingService;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageGenerator.PackageType;

public class TemplateManager {

  private FilesystemPackageCacheManager pcm;
  private ILoggingService logger;
  private List<JsonObject> configs = new ArrayList<JsonObject>();
  boolean canExecute;
  String templateThatCantExecute;
  String templateReason;
  List<String> templateList = new ArrayList<>();
  Set<String> antScripts = new HashSet<>();
  private boolean autoMode;

  public TemplateManager(FilesystemPackageCacheManager pcm, ILoggingService logger) {
    this.pcm = pcm;
    this.logger = logger;
  }

  public Template loadTemplate(String template, String rootFolder, String packageId, boolean autoMode, boolean wantLog, boolean rapidoMode) throws FHIRException, IOException {
    this.autoMode = autoMode;
    String templateDir = Utilities.path(rootFolder, "template");
    boolean inPlace = template.equals("#template");
    if (!inPlace) {
      FileUtilities.createDirectory(templateDir);
      FileUtilities.clearDirectory(templateDir);
    };
    List<String> scriptTemplates = new ArrayList<String>();
    
    canExecute = true;
    NpmPackage npm;
    if (!inPlace) {
      installTemplate(template, rootFolder, templateDir, scriptTemplates, new ArrayList<String>(), 0);
    }
    
    if (!autoMode) {
      canExecute = true; // nah, we don't care. locally, we'll build whatever people give us
    }
    if (!canExecute) {
      logger.logMessage("IG template '"+templateThatCantExecute+"' is not trusted.  No scripts will be executed");
    }
    return new Template(rootFolder, canExecute, templateThatCantExecute, templateReason, wantLog, rapidoMode);
  }

  private void installTemplate(String template, String rootFolder, String templateDir, List<String> scriptIds, ArrayList<String> loadedIds, int level) throws FHIRException, IOException {
    logger.logMessage(Utilities.padLeft("", ' ', level) + "Load Template from "+template);
    NpmPackage npm = loadPackage(template, rootFolder);
    if (!npm.isType(PackageType.IG_TEMPLATE))
      throw new FHIRException("The referenced package '"+template+"' does not have the correct type - is "+npm.type()+" but should be a template");
    templateList.add(npm.name()+"#"+npm.version());
    loadedIds.add(npm.name());
    if (npm.getNpm().has("base")) {
      String baseTemplate = npm.getNpm().asString("base");
      if (loadedIds.contains(baseTemplate)) {
        loadedIds.add(baseTemplate);
        throw new FHIRException("Template parents recurse: " + String.join("->", loadedIds));
      }
      if (!npm.getNpm().has("dependencies") || !npm.getNpm().getJsonObject("dependencies").has(baseTemplate)) {
        throw new FHIRException("Unable to resolve "+baseTemplate+" because it is not listed in the dependencies");
      }
      String ver = npm.getNpm().getJsonObject("dependencies").asString(baseTemplate);
      installTemplate(baseTemplate+"#"+ver, rootFolder, templateDir, scriptIds, loadedIds, level + 1);
    }
    // npm.debugDump("template");
    
    List<String> files = new ArrayList<>();
    npm.unPackWithAppend(templateDir, files);
    Set<String> ext = new HashSet<>();
    String scriptReason = null;
    JsonObject config = null;
    
    // if scriptReason != null, then the template has active content, and scriptReason contains the reason why we decided that there's active content
    
    if (npm.hasFile(Utilities.path("package", "$root"), "config.json")) {
      // we found a config file
      try {
        config = JsonParser.parseObject(npm.load(Utilities.path("package", "$root"), "config.json"));
      } catch (Exception e) {
        FileUtilities.streamToFile(npm.load(Utilities.path("package", "$root"), "config.json"), Utilities.path("[tmp]", npm.name()+"#"+npm.version()+"$config.json"));
        throw new FHIRException("Error parsing "+npm.name()+"#"+npm.version()+"#"+Utilities.path("package", "$root", "config.json")+": "+e.getMessage(), e);
      }
      configs.add(config);
      
      // check to see if it defines an active script
      if (config.has("script") || config.has("targets")) {
        scriptReason = "Template nominates an ant script or targets";
      }
      if (config.has("script")) {
        antScripts.add(config.asString("script"));
        
        // the ant script might call other ant scripts. No way we can introspect the ant script to see what it calls, 
        // so the template has to declare what other any scripts it can call out to, so we can track to see if any template
        // later tries to overwrite them
        // if it doesn't declare the list - even if it's empty - we refuse to run at all- templates have to do this
        if (!config.has("otherScripts")) {
          if (autoMode) {
            throw new Error("Template "+npm.name()+"#"+npm.version()+" names a script, but is not explicit about all ant scripts - this is no longer allowed");  
          } else {
            System.out.println("Template "+npm.name()+"#"+npm.version()+" names a script, but is not explicit about all ant scripts. Note that this means that this template no longer works with the ci-build infrastructure");
          }
        } else {
          for (String s : config.getStrings("otherScripts")) {
            antScripts.add(s);
          }
        }
      }
    }  
    
    // if we have't already found that it's considered a script, we'll look through the content
    if (scriptReason == null) {
      for (String fn : files) {
        String n = FileUtilities.getRelativePath(templateDir, fn);
        String lower = n.toLowerCase();

        if (!Utilities.existsInList(lower, "license", "readme")) {
          // Normalize path separators and get basename
          String unix = n.replace('\\', '/');
          String base = unix.contains("/") ? unix.substring(unix.lastIndexOf('/') + 1) : unix;

          // Allow a few harmless dotfiles anywhere
          if (Utilities.existsInList(base, ".gitignore", ".gitattributes", ".gitkeep")) {
            continue;
          }

          // Explicit, narrow allowlist for .github/ housekeeping files
          if (unix.startsWith(".github/")) {
            boolean safeGithub =
                unix.equals(".github/CODEOWNERS") ||
                unix.equals(".github/FUNDING.yml") ||
                unix.equals(".github/SECURITY.md") ||
                unix.equals(".github/dependabot.yml") ||
                unix.equalsIgnoreCase(".github/pull_request_template.md") ||
                (unix.startsWith(".github/workflows/") && (unix.endsWith(".yml") || unix.endsWith(".yaml"))) ||
                (unix.startsWith(".github/ISSUE_TEMPLATE/") &&
                    (unix.endsWith(".md") || unix.endsWith(".yml") || unix.endsWith(".yaml"))) ||
                // Optionally allow docs in .github/ (Markdown only)
                unix.endsWith(".md");

            if (safeGithub) {
              continue;
            }
          }

          String s = extension(n);
          if (antScripts.contains(n)) {
            // nah, if you overwrite an xml ant script, you're doing active content
            scriptReason = "Template contains a registered ant script";
          } else if (!Utilities.existsInList(s,
              ".html", ".css", ".png", ".gif", ".oet", ".json", ".xml", ".ico", ".jpg", ".md", ".ini",
              ".eot", ".otf", ".svg", ".ttf", ".woff", ".woff2", ".txt", ".yml", ".yaml", ".liquid", ".gitignore")) {
            // we don't track what other scripts are potentially active, so we only allow a whitelist of files.
            // this is safe if the base templates don't call out to scripts with weird file extensions (the template authors know about this rule)
            ext.add(s);
            break;
          }
        }
      }
      if (!ext.isEmpty()) {
        scriptReason = "Template has file extensions: " + ext;
      }
    }

    // if it has active content, then we check to see if it's trusted. Locally: always trusted. auto-build: only some trusted
    if (scriptReason != null) {
      checkTemplateId(template, npm.name(), scriptReason);
    }
    if (level==0 && configs.size() > 1) {
      config = configs.get(0);
      for (int i=1;i<configs.size(); i++) {
        applyConfigChanges(config, configs.get(i));
      }
      String configString = JsonParser.compose(config, true);
      String configPath = Utilities.path(templateDir, "config.json");
      FileUtilities.stringToFile(configString, configPath);
    }
  }
  
  private void applyConfigChanges(JsonObject baseConfig, JsonObject deltaConfig) throws FHIRException {
    for (JsonProperty p : deltaConfig.getProperties()) {
      if (baseConfig.has(p.getName())) {
        JsonElement baseElement = baseConfig.get(p.getName());
        JsonElement newElement = deltaConfig.get(p.getName());
        if (baseElement.isJsonArray()!=newElement.isJsonArray() || baseElement.isJsonObject()!=newElement.isJsonObject() || baseElement.isJsonPrimitive()!=newElement.isJsonPrimitive())
          throw new FHIRException("When overriding template config file, element " + p.getName() + " has a different JSON type in the base config file (" + baseElement + ") than it does in the overriding config file (" + newElement + ").");
        if (newElement.isJsonObject()) {
          applyConfigChanges((JsonObject) baseElement, (JsonObject) p.getValue());
        } else if (newElement.isJsonArray()) {
          ((JsonArray)baseElement).getItems().addAll(((JsonArray)newElement).getItems());
        } else {
          baseConfig.remove(p.getName());
          baseConfig.add(p.getName(), deltaConfig.get(p.getName()));
        }
      } else {
        baseConfig.add(p.getName(), deltaConfig.get(p.getName()));
      }
    }
  }

  private String extension(String n) {
    if (!n.contains(".")) {
      return "";
    }
    return n.substring(n.lastIndexOf("."));
  }

  /**
   * If we get to here, we've found a template with active content. This code checks that it's authorised.
   * 
   * If the IG is being run locally, we don't care - we'll always run it (see code in loadTemplate)
   * But on the ci-build, we only allow scripts that are approved by the FHIR product director
   * (as enforced by this code here).
   * 
   * We only allow scripts from packages loaded by package id, for known packages 
   * (the only way to fiddle with packages loaded by id is through the code)
   * 
   * todo:
   * - cross-check that the package id matches the github id (needs change on the ci-build infrastructure)
   *   this change would check that someone hasn't run a different template through the ci-build with the same id
   *   
   * @param template
   * @param packageId
   * @return
   */
  private void checkTemplateId(String template, String packageId, String reason) {
    String t = template.contains("#") ? template.substring(0, template.indexOf("#")) : template;
    if (!t.equals(packageId)) {
      canExecute = false;
      templateThatCantExecute = template;
      templateReason = reason;
    } else if (!Utilities.existsInList(packageId, 
        // if you are proposing to change this list, discuss with FHIR Product Director first
        "fhir.test.template", 
        "fhir.base.template",
        "fhir2.base.template",
        "hl7.base.template",
        "hl7.fhir.template",
        "hl7.au.base.template",
        "hl7.au.fhir.template",
        "hl7.utg.template",
        "hl7.be.fhir.template",
        "hl7.cda.template",
        "hl7.davinci.template",
        "hl7.sdc.template",
        "openhie.fhir.template",
        "who.fhir.template",
        "who.template.root",
        "hl7.davinci.template",
        "hl7.extensions.template",
        "ihe.fhir.template",
        "cqf.fhir.template",
        "ans.fr.template",
        "openehr.fhir.template",
        "ch.fhir.ig.template")) {
      canExecute = false;
      templateThatCantExecute = template;
      templateReason = reason;
    }
  }

  private boolean isTemplate(String url, String id, String template) {
    if (url.equals(template))
      return true;
    if (template.equals(id))
      return true;
    if (template.matches(FilesystemPackageCacheManager.PACKAGE_VERSION_REGEX) && template.startsWith(id+"#"))
      return true;
    return false;
  }

  private NpmPackage loadPackage(String template, String rootFolder) throws FHIRException, IOException {
    try {
      if (template.startsWith("#")) {
        File f = new File(Utilities.path(rootFolder, template.substring(1)));
        if (f.exists() && f.isDirectory()) {
          NpmPackage npm = NpmPackage.fromFolder(f.getAbsolutePath(), PackageType.IG_TEMPLATE, "output", ".git");
          return npm;
        }
      }

      if (template.matches(FilesystemPackageCacheManager.PACKAGE_REGEX)) {
        return pcm.loadPackage(template, null);
      }
      if (template.matches(FilesystemPackageCacheManager.PACKAGE_VERSION_REGEX)) {
        String[] p = template.split("\\#");
        return pcm.loadPackage(p[0], p[1]);
      }
      File f = new File(template);
      if (!f.exists() && !Utilities.isURL(template)) {
        f = new File(Utilities.path(rootFolder, template));
      }
      if (f.exists()) {
        if (f.isDirectory()) {
          return NpmPackage.fromFolder(f.getAbsolutePath());
        } else {
          return NpmPackage.fromPackage(new FileInputStream(template));
        }
      }
      if (template.startsWith("https://github.") || template.startsWith("http://github.")) {
        if (template.startsWith("http://")) {
          template = template.replace("http://", "https://");
        }

        URL url = new URL(zipUrl(template));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        InputStream zip = connection.getInputStream();
        return NpmPackage.fromZip(zip, true, url.toString()); 
      }
      throw new FHIRException("Unable to load template source from "+template+". Use a github URL, a local directory, or #[folder] for a contained template");
    } catch (Exception e) {
      e.printStackTrace();
      throw new FHIRException("Error loading template "+template+": "+e.getMessage(), e);
    }
  }

  private String zipUrl(String template) {
    if (!template.startsWith("https://github.")) {
      throw new FHIRException("Cannot refer to a template by URL unless referring to a github repository: "+template);
    } else if (Utilities.charCount(template, '/') == 4) {
      return Utilities.pathURL(template, "archive", "master.zip");      
    } else if (Utilities.charCount(template, '/') == 6) {
      String[] p = template.split("\\/");
      return Utilities.pathURL("https://"+p[2], p[3], p[4], "archive", p[6]+".zip");      
    } else {
      throw new FHIRException("Template syntax in URL referring to a github repository was not understood: "+template);
    }
  }

  public List<String> listTemplates() {
    return templateList;
  }
  
}
