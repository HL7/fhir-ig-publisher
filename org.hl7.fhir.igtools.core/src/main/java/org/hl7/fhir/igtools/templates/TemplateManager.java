package org.hl7.fhir.igtools.templates;

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

  public Template loadTemplate(String template, String rootFolder) throws FHIRException, IOException {
    logger.logMessage("Load Template from "+template);
    NpmPackage npm = loadPackage(template, rootFolder);
    if (!npm.isType(PackageType.TEMPLATE))
      throw new FHIRException("The referenced package '"+template+"' does not have the correct type - is "+npm.type()+" but should be a template");
    return new Template(npm, template.equals("#template"), rootFolder);
  }

  private NpmPackage loadPackage(String template, String rootFolder) throws FHIRException, IOException {
    if (template.startsWith("#")) {
      File f = new File(Utilities.path(rootFolder, template.substring(1)));
      if (f.exists() && f.isDirectory()) {
        NpmPackage npm = NpmPackage.fromFolder(f.getAbsolutePath(), PackageType.TEMPLATE);
        return npm;
      }
    }
      
    if (template.matches(PackageCacheManager.PACKAGE_REGEX))
      return pcm.loadPackage(template);
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
    if (template.startsWith("https://github.com")) {
      URL url = new URL(Utilities.pathURL(template, "archive", "master.zip"));
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      InputStream zip = connection.getInputStream();
      return NpmPackage.fromZip(zip, true); 
    }
    throw new FHIRException("Unable to load template from "+template);
  }
  
}
