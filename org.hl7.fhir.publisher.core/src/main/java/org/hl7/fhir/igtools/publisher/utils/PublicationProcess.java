package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.igtools.publisher.PastProcessHackerUtilities;
import org.hl7.fhir.igtools.publisher.Publisher;
import org.hl7.fhir.igtools.publisher.utils.WebSiteLayoutRulesProviders.WebSiteLayoutRulesProvider;
import org.hl7.fhir.utilities.FileNotifier;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.ZipGenerator;
import org.hl7.fhir.utilities.json.JsonUtilities;
import org.hl7.fhir.utilities.json.JsonTrackingParser;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ca.uhn.fhir.util.JsonUtil;

public class PublicationProcess {
 
  /*
   * checks that must be run prior to runing this:
   *  
   *  - FMG has approved the publication
   *  - FHIR Product Director has approved the version
   *  - realm matches what was proposed and authorised.
   *  - code hasn't been changed since last published and matches what was approved by FMG
   *  - there are no publication issues outstanding
   *  - remaining qa issues are ok
   *  
   * If this is a milestone:
   *   - TSC has approved the publication
   *   
   */

  /**
   * 
   * @param source - the directory that contains the IG source that will be published. This must be post normal build, and the build must have succeeded, and the output must have gone to \output. it will  be rebuilt
   * @param destination - the root folder of the local copy of files for the web site to which the IG is being published. 
   * @throws Exception 
   */
  public void publish(String source, String rootFolder, boolean first, String date, String registrySource, String history, String temp) throws Exception {
    PublisherConsoleLogger logger = new PublisherConsoleLogger();
    rootFolder = new File(rootFolder).getAbsolutePath();
    logger.start(Utilities.path("[tmp]", "publication-process.log"));
    try {
      List<ValidationMessage> res = publishInner(source, rootFolder, first, date, registrySource, history, temp, logger);
      if (res.size() == 0) {
        System.out.println("Success");
      } else {
        for (ValidationMessage vm : res) {
          System.out.println(vm.summary());
        }
      }
    } catch (Exception e) {
      System.out.println("Exception publishing: "+e.getMessage());
      e.printStackTrace();
    }
    System.out.println("Full log in "+logger.getFilename());
  }
  
  public List<ValidationMessage> publishInner(String source, String rootFolder, boolean first, String date, String registrySource, String history, String temp, PublisherConsoleLogger logger) throws Exception {
    List<ValidationMessage> res = new ArrayList<>();

    // check the wider context
    File fSource = checkDirectory(source, res, "Source");
    File fRoot = checkDirectory(rootFolder, res, "Destination");
    checkDirectory(Utilities.path(rootFolder, "ig-build-zips"), res, "Destination Zip Folder");
    File fRegistry = checkFile(registrySource, res, "Registry");
    File fHistory = checkDirectory(history, res, "History Template");
    File fPubIni = checkFile(Utilities.path(rootFolder, "publish.ini"), res, "Publish.ini");
    if (res.size() > 0) {
      return res;
    }
    IniFile ini = new IniFile(fPubIni.getAbsolutePath());
    String url = ini.getStringProperty("website", "url");
    
    // check the output
    File fOutput = checkDirectory(Utilities.path(source, "output"), res, "Publication Source");
    File fQA = checkFile(Utilities.path(source, "output", "qa.json"), res, "Publication QA info");
    if (res.size() > 0) {
      return res;
    }
    JsonObject qa = JsonTrackingParser.parseJson(loadFile("Source QA file", fQA.getAbsolutePath()));
    
    // now: is the IG itself ready for publication
    NpmPackage npm = NpmPackage.fromPackage(loadFile("Source package", Utilities.path(source, "output", "package.tgz")));
    String id = npm.name();
    String[] p = id.split("\\.");
    String canonical = PastProcessHackerUtilities.actualUrl(npm.canonical());
    if (!check(res, canonical != null, "canonical URL not found")) {
      return res;
    }
    String version = npm.version();
    if (!check(res, version != null, "Source Package has no version")) {
      return res;
    }

    // --- Rules for layout depend on publisher ------
    WebSiteLayoutRulesProvider rp = WebSiteLayoutRulesProviders.recogniseNpmId(id, p, ini.getStringProperty("website", "layout"));
    if (!rp.checkNpmId(res)) {
      return res;
    }
    if (!rp.checkCanonicalAndUrl(res, canonical, url)) {
      return res;
    }
    String destination = rp.getDestination(rootFolder); 
    // ----------------------------------------------

    if (!check(res, !(new File(Utilities.path(source, "package-list.json")).exists()), "Source '"+source+"' contains a package-list.json - must not exist")) {
      return res;
    }            
    if (!check(res, new File(Utilities.path(source, "publication-request.json")).exists(), "Source '"+source+"' does not contain a publication-request.json - consult documentation to see how to set it up")) {
      return res;
    }            
    
    JsonObject prSrc = JsonTrackingParser.parseJson(loadFile("Source Package List", Utilities.path(source, "publication-request.json")));

    boolean milestone = JsonUtilities.bool(prSrc, "milestone");
    if (first) {
      if (new File(Utilities.path(destination, "package-list.json")).exists()) {
        check(res, false, "Destination Package List already exists, but the parameter -first was provided");
      } else {
        boolean ok = true;
        ok = check(res, !Utilities.noString(JsonUtilities.str(prSrc, "category")), "No category in the publication request") && ok;
        ok = check(res, !Utilities.noString(JsonUtilities.str(prSrc, "title")), "No title in the publication request") && ok;
        ok = check(res, !Utilities.noString(JsonUtilities.str(prSrc, "introduction")), "No introduction in the publication request") && ok;
        ok = check(res, !Utilities.noString(JsonUtilities.str(prSrc, "ci-build")), "No ci-build in the publication request") && ok;
        if (!ok) {
          return res;
        }
        JsonObject tplDst = new JsonObject();
        tplDst.addProperty("package-id", npm.name());
        tplDst.addProperty("canonical", npm.canonical());
        tplDst.addProperty("title", JsonUtilities.str(prSrc, "title"));
        tplDst.addProperty("category", JsonUtilities.str(prSrc, "category"));
        tplDst.addProperty("introduction", JsonUtilities.str(prSrc, "introduction"));
        JsonArray vl = new JsonArray();
        tplDst.add("list", vl);
        JsonObject ci = new JsonObject();
        vl.add(ci);
        ci.addProperty("version", "current");
        ci.addProperty("desc", "Continuous Integration Build (latest in version control) - Content subject to frequent changes");
        ci.addProperty("path", JsonUtilities.str(prSrc, "ci-build"));
        ci.addProperty("status", "ci-build");
        JsonTrackingParser.write(tplDst, Utilities.path(destination, "package-list.json"));
      }      
    }
    if (!check(res, new File(Utilities.path(destination, "package-list.json")).exists(), "Destination '"+destination+"' does not contain a package-list.json - cannot proceed")) {
      return res;
    }
    JsonObject plPub = JsonTrackingParser.parseJson(loadFile("Published Package List", Utilities.path(destination, "package-list.json")));

    check(res, id.equals(JsonUtilities.str(prSrc, "package-id")), "Source Package List has the wrong package id: "+JsonUtilities.str(prSrc, "package-id")+" (should be "+id+")");
    check(res, id.equals(JsonUtilities.str(plPub, "package-id")), "Published Package List has the wrong package id: "+JsonUtilities.str(plPub, "package-id")+" (should be "+id+")");
    check(res, canonical.equals(JsonUtilities.str(plPub, "canonical")), "Source Package List has the wrong canonical: "+JsonUtilities.str(plPub, "canonical")+" (should be "+canonical+")");
    check(res, plPub.has("category"), "No Entry found in dest package-list 'category'");

    JsonObject vPub = JsonUtilities.findByStringProp(plPub.getAsJsonArray("list"), "version", version);
    
    check(res, plPub.getAsJsonArray("list").size() > 0, "Dest package-list has no existent version (should have ci-build entry)");
    check(res, vPub == null, "Found an entry in the publication package-list for v"+version+" - it looks like it has already been published");
    check(res, prSrc.has("desc") || prSrc.has("descmd"), "Source Package list has no description for v"+version);
    String pathVer = JsonUtilities.str(prSrc, "path");
    String vCode = pathVer.substring(pathVer.lastIndexOf("/")+1);
    
    check(res, pathVer.equals(Utilities.pathURL(canonical, vCode)), "Source publication request path is wrong - is '"+JsonUtilities.str(prSrc, "path")+"', doesn't match canonical)");
    // ok, the ids and canonicals are all lined up, and w're ready to publish     

    check(res, id.equals(JsonUtilities.str(qa, "package-id")), "Generated IG has wrong package "+JsonUtilities.str(qa, "package-id"));
    check(res, version.equals(JsonUtilities.str(qa, "ig-ver")), "Generated IG has wrong version "+JsonUtilities.str(qa, "ig-ver"));
    check(res, qa.has("errs"), "Generated IG has no error count");
    check(res, npm.fhirVersion().equals(JsonUtilities.str(qa, "version")), "Generated IG has wrong FHIR version "+JsonUtilities.str(qa, "version"));
    check(res, JsonUtilities.str(qa, "url").startsWith(canonical) || JsonUtilities.str(qa, "url").startsWith(npm.canonical()), "Generated IG has wrong Canonical "+JsonUtilities.str(qa, "url"));
    
    String destVer = Utilities.path(destination, vCode);
    if (!check(res, new File(destination).exists(), "Destination '"+destVer+"' not found - must be set up manually for first publication")) {
      return res;
    }    
    check(res, !(new File(destVer).exists()), "Nominated path '"+destVer+"' already exists");

    // check to see if there's history template files; if there isn't, copy it in
    check(res, new File(Utilities.path(rootFolder, "templates", "header.template")).exists(), Utilities.path(rootFolder, "templates", "header.template")+" not found - template not set up properly");
    check(res, new File(Utilities.path(rootFolder, "templates", "preamble.template")).exists(), Utilities.path(rootFolder, "templates", "preamble.template")+" not found - template not set up properly");
    check(res, new File(Utilities.path(rootFolder, "templates", "postamble.template")).exists(), Utilities.path(rootFolder, "templates", "postamble.template")+" not found - template not set up properly");
      
    // todo: check the license, header, footer?... 
    
    // well, we've run out of things to test... time to actually try...
    if (res.size() == 0) {
      doPublish(fSource, fOutput, qa, destination, destVer, pathVer, fRoot, ini, plPub, prSrc, fRegistry, npm, milestone, date, fHistory, temp, logger);
    }        
    return res;
    
  }

  private File checkDirectory(String filename, List<ValidationMessage> res, String name) throws Exception {
    if (filename == null) {
      throw new Exception("No "+name+" parameter provided");
    }
    File f = new File(filename);
    if (check(res, f.exists(), name+" '"+filename+"' not found")) {
      check(res, f.isDirectory(), name+" '"+filename+"' is not a directory");
    }    
    return f;
  }

  private File checkFile(String filename, List<ValidationMessage> res, String name) {
    File f = new File(filename);
    if (check(res, f.exists(), name+" '"+filename+"' not found")) {
      check(res, !f.isDirectory(), name+" '"+filename+"' is a directory");
    }    
    return f;
  }

  private boolean check(List<ValidationMessage> res, boolean b, String message) {
    if (!b)  {
      ValidationMessage msg = new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, "parameters", message, IssueSeverity.ERROR);
      res.add(msg);
    }
    return b;
    
  }

  private InputStream loadFile(String string, String path) throws FileNotFoundException {
    File f = new File(path);
    if (!f.exists()) {
      throw new Error("File "+string+" at "+path+" not found");
    }
    return new FileInputStream(f);
  }

  private void doPublish(File fSource, File fOutput, JsonObject qa, String destination, String destVer, String pathVer, File fRoot, IniFile ini, JsonObject plPub, JsonObject prSrc, File fRegistry, NpmPackage npm, boolean milestone, String date, File history, String tempDir, PublisherConsoleLogger logger) throws Exception {
    // ok. all our tests have passed.
    // 1. do the publication build(s)
    System.out.println("All checks passed. Do the publication build from "+fSource.getAbsolutePath()+" and publish to "+destination);        

    // create a temporary copy and build in that:
    File temp = cloneToTemp(tempDir, fSource, npm.name()+"#"+npm.version());
    File tempM = null; 
    System.out.println("Build IG at "+fSource.getAbsolutePath()+": final copy suitable for publication (in "+temp.getAbsolutePath()+")");        
    runBuild(qa, temp.getAbsolutePath(), new String[] {"-ig", temp.getAbsolutePath(), "-resetTx", "-publish", pathVer, "-no-exit"});

    if (milestone) {
      tempM = cloneToTemp(tempDir, temp, npm.name()+"#"+npm.version()+"-milestone");
      System.out.println("Build IG at "+fSource.getAbsolutePath()+": final copy suitable for publication (in "+tempM.getAbsolutePath()+") (milestone build)");        
      runBuild(qa, tempM.getAbsolutePath(), new String[] {"-ig", tempM.getAbsolutePath(), "-publish", pathVer, "-milestone", "-no-exit"});      
    }

    // 2. make a copy of what we built
    System.out.println("Keep a copy of the build directory at "+Utilities.path(fRoot.getAbsolutePath(), "ig-build-zips", npm.name()+"#"+npm.version()+".zip"));    
    zipFolder(temp, Utilities.path(fRoot.getAbsolutePath(), "ig-build-zips", npm.name()+"#"+npm.version()+".zip"));

    System.out.println("");        
    System.out.println("ok. All checks passed. Publish v"+npm.version()+" to "+destVer);        

    // 3. create the folder {root}/{realm}/{code}/{subdir}
    System.out.println("Copy the IG to "+destVer);    
    Utilities.createDirectory(destVer);
    FileUtils.copyDirectory(new File(Utilities.path(temp.getAbsolutePath(), "output")), new File(destVer));

    // now, update the package list 
    System.out.println("Update "+Utilities.path(destination, "package-list.json"));    
    updatePackageList(plPub, prSrc, pathVer,  Utilities.path(destination, "package-list.json"), milestone, date, npm.fhirVersion());
    
    if (milestone) {
      System.out.println("This is a milestone release - publish v"+npm.version()+" to "+destination);      
      
      System.out.println("Clear out existing content");        
       Publisher.main(new String[] { "-delete-current", destination, "-history", history.getAbsolutePath(), "-no-exit"});       

      System.out.println("Copy to directory");        
      FileUtils.copyDirectory(new File(Utilities.path(tempM.getAbsolutePath(), "output")), new File(destination));
    }
    
    // finally
    System.out.println("Rebuild everything for "+Utilities.path(destination, "package-list.json"));
    Publisher.main(new String[] { "-publish-update", "-folder", fRoot.getAbsolutePath(), "-registry", fRegistry.getAbsolutePath(), "-filter", destination, "-history", history.getAbsolutePath(), "-no-exit"});

    System.out.println("Finished Publishing");
    logger.stop();
    FileUtils.copyFile(new File(logger.getFilename()), new File(Utilities.path(fRoot.getAbsolutePath(), "ig-build-zips", npm.name()+"#"+npm.version()+".log")));    
  }

  private File cloneToTemp(String tempDir, File fSource, String name) throws IOException {
    if (tempDir == null) {
      tempDir = "[tmp]";
    }
    String dest = Utilities.path(tempDir, name);
    System.out.println("Prepare Build space in "+dest);        
    File fDest = new File(dest);
    if (!fDest.exists()) {
      Utilities.createDirectory(dest);
    } else {
      Utilities.clearDirectory(dest);
    }
    FileUtils.copyDirectory(fSource, fDest);
    return fDest;
  }
  
  private void updatePackageList(JsonObject plPub, JsonObject prSrc, String webpath, String filepath, boolean milestone, String date, String fhirVersion) throws IOException {
    if (date == null) {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      date = sdf.format(new Date());      
    }
    JsonObject newVer = new JsonObject();
    newVer.addProperty("version", JsonUtilities.str(prSrc,  "version"));
    newVer.addProperty("date", date);
    newVer.addProperty("path", webpath);
    newVer.addProperty("status", JsonUtilities.str(prSrc,  "status"));
    newVer.addProperty("sequence", JsonUtilities.str(prSrc,  "sequence"));
    newVer.addProperty("fhirversion", fhirVersion);
    if (prSrc.has("desc")) {
      newVer.addProperty("desc", JsonUtilities.str(prSrc,  "desc"));
    }
    if (prSrc.has("descmd")) {
      newVer.addProperty("descmd", JsonUtilities.str(prSrc,  "descmd"));
    }
    if (prSrc.has("changes")) {
      newVer.addProperty("changes", JsonUtilities.str(prSrc,  "changes"));
    }
    if (milestone) {
      newVer.addProperty("current", true);
    }
    JsonArray oldArr = plPub.getAsJsonArray("list");
    JsonArray newArr = new JsonArray();
    newArr.add(oldArr.get(0)); // the ci-build entry
    newArr.add(newVer);
    for (int i = 1; i < oldArr.size(); i++) {
      JsonObject v = (JsonObject) oldArr.get(i);
      if (milestone && v.has("current")) {
        v.remove("current");      
      }
      newArr.add(v);
    }
    plPub.remove("list");
    plPub.add("list", newArr);
    JsonTrackingParser.write(plPub, new File(filepath));
  }

  private void zipFolder(File fSource, String path) throws IOException {
    ZipGenerator zip = new ZipGenerator(path);
    zip.addFolder(fSource.getAbsolutePath(), "", false);
    zip.close();
  }

  private void runBuild(JsonObject qa, String path, String[] args) throws Exception {
    Publisher.main(args); // any exceptions, we just let them propagate
    // check it built ok:
    File f = new File(Utilities.path(path, "output", "qa.json"));
    if (!f.exists()) {
      throw new Error("File "+f.getAbsolutePath()+" not found - it appears the build run failed");
    }
    JsonObject nqa = JsonTrackingParser.parseJson(loadFile("Source QA file", f.getAbsolutePath()));
    compareJsonProp(qa, nqa, "errs", "Errors");
    compareJsonProp(qa, nqa, "warnings", "Warnings");
    compareJsonProp(qa, nqa, "hints", "Hints");
  }

  private void compareJsonProp(JsonObject qa, JsonObject nqa, String name, String label) throws Exception {
    if (!qa.has(name)) {
      throw new Exception("Unable to find "+name+" for past run");
    }
    int oldv = qa.get(name).getAsInt();
    if (!nqa.has(name)) {
      throw new Exception("Unable to find "+name+" for current run");
    }
    int newv = qa.get(name).getAsInt();
    if (newv > oldv) {
      throw new Exception("Increase in "+label+" on run - was "+oldv+", now "+newv);      
    }
  }


}
