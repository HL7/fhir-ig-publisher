package org.hl7.fhir.igtools.templates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.utilities.JsonMerger;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.json.JsonTrackingParser;

import com.google.gson.JsonObject;

public class Template {

  NpmPackage pack;
  public Template(NpmPackage npm) {
    pack = npm;
  }

  public void init(String rootDir) throws IOException {
    String templateDir = Utilities.path(rootDir, "template");
    Utilities.createDirectory(templateDir);
    Utilities.clearDirectory(templateDir);
//    template.copyTo("template", templateDir);
//    List<String> files = new ArrayList<String>();
//    copyFiles(Utilities.path(templateDir, "jekyll"), Utilities.path(templateDir, "jekyll"), tempDir, files); 
//    for (String s : files)
//      otherFilesStartup.add(Utilities.path(tempDir, s)); 
//    JsonObject tc = JsonTrackingParser.parseJson(template.load("template", "config.json"));
//    new JsonMerger().merge(configuration, tc);
//    templateLoaded = true;
//    templatePck = template.name();
  }
//  public NpmPackage fetch(String src) throws IOException {
//    if (src.startsWith("https://github.com")) {
//      URL url = new URL(Utilities.pathURL(src, "archive", "master.zip"));
//      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//      connection.setRequestMethod("GET");
//      InputStream zip = connection.getInputStream();
//      return NpmPackage.fromZip(zip, true); 
//    } else {
//      File f = new File(src);
//      if (f.exists() && f.isDirectory())    
//        return loadLiteralPath(src);
//      else
//        throw new IOException("Unable ti interpret template source '"+src+"'");
//    }
//  }
//
//  private InputStream fetchUrl(String pathURL) {
//    // TODO Auto-generated method stub
//    return null;
//  }
//
//  private NpmPackage loadLiteralPath(String path) throws IOException {
//    NpmPackage pi = new NpmPackage(path);
//    return pi;
//  }

  public ImplementationGuide modifyIG(ImplementationGuide ig, String repoRoot) {
    return ig;
  }

  public JsonObject config() {
    return new JsonObject();
  }


  
}
