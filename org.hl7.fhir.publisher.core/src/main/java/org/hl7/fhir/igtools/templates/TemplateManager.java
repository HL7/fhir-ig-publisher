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
import org.hl7.fhir.r5.context.IWorkerContext.ILoggingService;
import org.hl7.fhir.utilities.JsonMerger;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.cache.NpmPackage.NpmPackageFolder;
import org.hl7.fhir.utilities.cache.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.cache.PackageGenerator.PackageType;
import org.hl7.fhir.utilities.json.JsonTrackingParser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class TemplateManager {

  private FilesystemPackageCacheManager pcm;
  private ILoggingService logger;
  private List<JsonObject> configs = new ArrayList<JsonObject>();
  boolean canExecute;
  String templateThatCantExecute;
  String templateReason;
  String ghUrl;

  public TemplateManager(FilesystemPackageCacheManager pcm, ILoggingService logger, String ghUrl) {
    this.pcm = pcm;
    this.logger = logger;
    this.ghUrl = ghUrl;
  }

  public Template loadTemplate(String template, String rootFolder, String packageId, boolean autoMode) throws FHIRException, IOException {
    String templateDir = Utilities.path(rootFolder, "template");
    boolean inPlace = template.equals("#template");
    if (!inPlace) {
      Utilities.createDirectory(templateDir);
      Utilities.clearDirectory(templateDir);
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
    return new Template(rootFolder, canExecute, templateThatCantExecute, templateReason);
  }

  private void installTemplate(String template, String rootFolder, String templateDir, List<String> scriptIds, ArrayList<String> loadedIds, int level) throws FHIRException, IOException {
    logger.logMessage(Utilities.padLeft("", ' ', level) + "Load Template from "+template);
    NpmPackage npm = loadPackage(template, rootFolder);
    if (!npm.isType(PackageType.TEMPLATE))
      throw new FHIRException("The referenced package '"+template+"' does not have the correct type - is "+npm.type()+" but should be a template");
    loadedIds.add(npm.name());
    if (npm.getNpm().has("base")) {
      String baseTemplate = npm.getNpm().get("base").getAsString();
      if (loadedIds.contains(baseTemplate)) {
        loadedIds.add(baseTemplate);
        throw new FHIRException("Template parents recurse: " + String.join("->", loadedIds));
      }
      if (!npm.getNpm().has("dependencies") || !npm.getNpm().getAsJsonObject("dependencies").has(baseTemplate)) {
        throw new FHIRException("Unable to resolve "+baseTemplate+" because it is not listed in the dependencies");
      }
      String ver = npm.getNpm().getAsJsonObject("dependencies").get(baseTemplate).getAsString();
      installTemplate(baseTemplate+"#"+ver, rootFolder, templateDir, scriptIds, loadedIds, level + 1);
    }
    // npm.debugDump("template");
    
    npm.unPackWithAppend(templateDir);
    Set<String> ext = new HashSet<>();
    boolean noScripts = true;
    JsonObject config = null;
    if (npm.hasFile(Utilities.path("package", "$root"), "config.json")) {
      config = JsonTrackingParser.parseJson(npm.load(Utilities.path("package", "$root"), "config.json"));
      configs.add(config);
      noScripts = !config.has("script") && !config.has("targets");
    }  
    if (noScripts) {
      for (NpmPackageFolder f : npm.getFolders().values()) {
        for (String n : f.listFiles()) {
          String s = extension(n);
          if (!Utilities.existsInList(s, ".html", ".css", ".png", ".gif", ".oet", ".json", ".xml", ".ico", ".jpg", ".md", ".ini", ".eot", ".otf", ".svg", ".ttf", ".woff", ".txt", ".yml")) {
            noScripts = false;
            ext.add(s);
            break;
          }
        }
      }
    }
    if (!noScripts) {
      checkTemplateId(template, npm.name(), config == null ? "has file extensions: "+ ext : config.has("script") ? "template nominates a script" : 
        config.has("targets") ? "template nominates ant targets" : "has file extensions: "+ ext);
    }
    if (level==0 && configs.size() > 1) {
      config = configs.get(0);
      for (int i=1;i<configs.size(); i++) {
        applyConfigChanges(config, configs.get(i));
      }
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      String configString = gson.toJson(config);
      String configPath = Utilities.path(templateDir, "config.json");
      TextFile.stringToFile(configString, configPath, false);
    }
  }
  
  private void applyConfigChanges(JsonObject baseConfig, JsonObject deltaConfig) throws FHIRException {
    for (String key : deltaConfig.keySet()) {
      if (baseConfig.has(key)) {
        JsonElement baseElement = baseConfig.get(key);
        JsonElement newElement = deltaConfig.get(key);
        if (baseElement.isJsonArray()!=newElement.isJsonArray() || baseElement.isJsonObject()!=newElement.isJsonObject() || baseElement.isJsonPrimitive()!=newElement.isJsonPrimitive())
          throw new FHIRException("When overriding template config file, element " + key + " has a different JSON type in the base config file (" + baseElement + ") than it does in the overriding config file (" + newElement + ").");
        if (newElement.isJsonObject()) {
          applyConfigChanges(baseElement.getAsJsonObject(), deltaConfig.get(key).getAsJsonObject());
        } else if (newElement.isJsonArray()) {
          baseElement.getAsJsonArray().addAll(newElement.getAsJsonArray());
        } else {
          baseConfig.remove(key);
          baseConfig.add(key, deltaConfig.get(key));
        }
      } else {
        baseConfig.add(key, deltaConfig.get(key));
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
        "hl7.base.template",
        "hl7.fhir.template",
        "hl7.utg.template",
        "hl7.be.fhir.template",
        "hl7.cda.template",
        "hl7.davinci.template",
        "ihe.fhir.template")) {
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
          NpmPackage npm = NpmPackage.fromFolder(f.getAbsolutePath(), PackageType.TEMPLATE, "output", ".git");
          return npm;
        }
      }

      if (template.matches(FilesystemPackageCacheManager.PACKAGE_REGEX)) {
        return pcm.loadPackage(template, "current");
      }
      if (template.matches(FilesystemPackageCacheManager.PACKAGE_VERSION_REGEX)) {
        String[] p = template.split("\\#");
        return pcm.loadPackage(p[0], p[1]);
      }
      File f = new File(template);
      if (f.exists()) {
        if (f.isDirectory()) {
          return NpmPackage.fromFolder(template);
        } else {
          return NpmPackage.fromPackage(new FileInputStream(template));
        }
      }
      if (template.startsWith("https://github.com") || template.startsWith("http://github.com")) {
        if (template.startsWith("http://github.com")) {
          template = template.replace("http://github.com", "https://github.com");
        }

        URL url = new URL(zipUrl(template));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        InputStream zip = connection.getInputStream();
        return NpmPackage.fromZip(zip, true, url.toString()); 
      }
      throw new FHIRException("Unable to load template from "+template+" cannot find template. Use a github URL, a local directory, or #[folder] for a contained template");
    } catch (Exception e) {
      throw new FHIRException("Error loading template "+template+": "+e.getMessage(), e);
    }
  }

  private String zipUrl(String template) {
    if (!template.startsWith("https://github.com")) {
      throw new FHIRException("Cannot refer to a template by URL unless referring to a github repository: "+template);
    } else if (Utilities.charCount(template, '/') == 4) {
      return Utilities.pathURL(template, "archive", "master.zip");      
    } else if (Utilities.charCount(template, '/') == 6) {
      String[] p = template.split("\\/");
      return Utilities.pathURL("https://github.com", p[3], p[4], "archive", p[6]+".zip");      
    } else {
      throw new FHIRException("Template syntax in URL referring to a github repository was not understood: "+template);
    }
  }
  
}
