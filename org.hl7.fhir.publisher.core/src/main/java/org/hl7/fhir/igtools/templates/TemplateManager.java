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
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.IWorkerContext.ILoggingService;
import org.hl7.fhir.utilities.JsonMerger;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.cache.NpmPackage.NpmPackageFolder;
import org.hl7.fhir.utilities.cache.PackageCacheManager;
import org.hl7.fhir.utilities.cache.PackageGenerator.PackageType;
import org.hl7.fhir.utilities.json.JsonTrackingParser;

import com.google.gson.JsonObject;

public class TemplateManager {

  private PackageCacheManager pcm;
  private ILoggingService logger;
  boolean canExecute;

  public TemplateManager(PackageCacheManager pcm, ILoggingService logger) {
    this.pcm = pcm;
    this.logger = logger;
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
      logger.logMessage("IG template is not trusted.  No scripts will be executed");
    }
    return new Template(rootFolder, canExecute);
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
      installTemplate(baseTemplate, rootFolder, templateDir, scriptIds, loadedIds, level + 1);
    }
    npm.debugDump("template");
    npm.unPackWithAppend(templateDir);
    boolean noScripts = true;
    for (NpmPackageFolder f : npm.getFolders().values()) {
      for (String n : f.listFiles()) {
        noScripts = noScripts && Utilities.existsInList(extension(n), ".html", ".css", ".png", ".gif", ".oet", ".json", ".xml", ".ico");
      }
    }
    if (!noScripts) {
      checkTemplateId(template, npm.name());
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
  private void checkTemplateId(String template, String packageId) {
    if (!template.equals(packageId)) {
      canExecute = false;
    } else if (!Utilities.existsInList(packageId, 
        // if you are proposing to change this list, discuss with FHIR Product Director first
        "fhir.test.template", 
        "fhir.base.template",
        "hl7.fhir.template",
        "fhir.davinci.template",
        "ihe.fhir.template")) {
      canExecute = false;
    }
  }

  private boolean isTemplate(String url, String id, String template) {
    if (url.equals(template))
      return true;
    if (template.equals(id))
      return true;
    if (template.matches(PackageCacheManager.PACKAGE_VERSION_REGEX) && template.startsWith(id+"#"))
      return true;
    return false;
  }

  private NpmPackage loadPackage(String template, String rootFolder) throws FHIRException, IOException {
    if (template.startsWith("#")) {
      File f = new File(Utilities.path(rootFolder, template.substring(1)));
      if (f.exists() && f.isDirectory()) {
        NpmPackage npm = NpmPackage.fromFolder(f.getAbsolutePath(), PackageType.TEMPLATE, "output", ".git");
        return npm;
      }
    }
      
    if (template.matches(PackageCacheManager.PACKAGE_REGEX)) {
      return pcm.loadPackage(template, "current");
    }
    if (template.matches(PackageCacheManager.PACKAGE_VERSION_REGEX)) {
      String[] p = template.split("\\#");
      return pcm.loadPackage(p[0], p[1]);
    }
    File f = new File(template);
    if (f.exists())
      if (f.isDirectory())
        return NpmPackage.fromFolder(template);
      else
        return NpmPackage.fromPackage(new FileInputStream(template));
    if (template.startsWith("https://github.com") || template.startsWith("http://github.com")) {
      if (template.startsWith("http://github.com"))
        template = template.replace("http://github.com", "https://github.com");
      
      URL url = new URL(Utilities.pathURL(template, "archive", "master.zip"));
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      InputStream zip = connection.getInputStream();
      return NpmPackage.fromZip(zip, true, url.toString()); 
    }
    throw new FHIRException("Unable to load template from "+template);
  }
  
}
