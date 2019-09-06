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
import org.hl7.fhir.utilities.cache.PackageCacheManager;
import org.hl7.fhir.utilities.cache.PackageGenerator.PackageType;
import org.hl7.fhir.utilities.json.JsonTrackingParser;

import com.google.gson.JsonObject;

public class TemplateManager {

  private PackageCacheManager pcm;
  private ILoggingService logger;

  public TemplateManager(PackageCacheManager pcm, ILoggingService logger) {
    this.pcm = pcm;
    this.logger = logger;
  }

  public Template loadTemplate(String template, String rootFolder, String packageId, boolean autoMode) throws FHIRException, IOException {
    logger.logMessage("Load Template from "+template);
    boolean canExecute = !autoMode || checkTemplateId(template, packageId);
    NpmPackage npm = loadPackage(template, rootFolder);
    if (!npm.isType(PackageType.TEMPLATE))
      throw new FHIRException("The referenced package '"+template+"' does not have the correct type - is "+npm.type()+" but should be a template");
    return new Template(npm, template.equals("#template"), rootFolder, canExecute);
  }

  private boolean checkTemplateId(String template, String packageId) {
    // template control on the autobuilder 
    // - templates are only allowed if approved by the FHIR product director
    // - templates can run code, so only trusted templates are allowed
    
    // first, the following templates authored by HL7 are allowed 
    if (isTemplate("http://github.com/FHIR/test-template", "fhir.test.template", template))
      return true;
    if (isTemplate("http://github.com/HL7/fhir-template", "hl7.fhir.template", template))
      return true;
    if (isTemplate("http://github.com/HL7/fhir-template", "fhir.base.template", template))
      return true;
    if (isTemplate("http://github.com/HL7/fhir-template", "fhir.divinci.template", template))
      return true;    
    
    // we might choose to allow some IGs here...
    return false;
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
      return NpmPackage.fromZip(zip, true); 
    }
    throw new FHIRException("Unable to load template from "+template);
  }
  
}
