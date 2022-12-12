package org.hl7.fhir.igtools.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.hl7.fhir.igtools.publisher.PastProcessHackerUtilities;
import org.hl7.fhir.igtools.publisher.Publisher;
import org.hl7.fhir.igtools.web.WebSiteLayoutRulesProviders.WebSiteLayoutRulesProvider;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.ZipGenerator;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;

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
  public void publish(String source, String rootFolder, String date, String registrySource, String history, String temp) throws Exception {
    PublisherConsoleLogger logger = new PublisherConsoleLogger();
    rootFolder = new File(rootFolder).getAbsolutePath();
    logger.start(Utilities.path("[tmp]", "publication-process.log"));
    try {
      List<ValidationMessage> res = publishInner(source, rootFolder, date, registrySource, history, temp, logger);
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
  
  public List<ValidationMessage> publishInner(String source, String rootFolder, String date, String registrySource, String history, String temp, PublisherConsoleLogger logger) throws Exception {
    List<ValidationMessage> res = new ArrayList<>();

    // check the wider context
    File fSource = checkDirectory(source, res, "Source");
    checkDirectory(rootFolder, res, "Destination");
    
    String workingRoot = Utilities.path(temp, "web-root-"+new SimpleDateFormat("yyyyMMddhhmmss"));
    Utilities.createDirectory(workingRoot);    
    File fRoot = checkDirectory(workingRoot, res, "Working Web Folder");
    WebSourceProvider src = new WebSourceProvider(workingRoot, source);
    
    checkDirectory(Utilities.path(workingRoot, "ig-build-zips"), res, "Destination Zip Folder", true);
    File fRegistry = checkFile(registrySource, res, "Registry");
    File fHistory = checkDirectory(history, res, "History Template");
    src.needFile("publish.ini");
    File fPubIni = checkFile(Utilities.path(workingRoot, "publish.ini"), res, "Publish.ini");
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
    JsonObject qa = JsonParser.parseObject(loadFile("Source QA file", fQA.getAbsolutePath()));
    
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
    String destination = rp.getDestination(workingRoot); 
    // ----------------------------------------------

    if (!check(res, !(new File(Utilities.path(source, "package-list.json")).exists()), "Source '"+source+"' contains a package-list.json - must not exist")) {
      return res;
    }            
    if (!check(res, new File(Utilities.path(source, "publication-request.json")).exists(), "Source '"+source+"' does not contain a publication-request.json - consult documentation to see how to set it up")) {
      return res;
    }            
    
    JsonObject prSrc = JsonParser.parseObject(loadFile("Source publication request", Utilities.path(source, "publication-request.json")));

    boolean milestone = prSrc.asBoolean("milestone");
    boolean first = prSrc.asBoolean("first"); 
    src.needOptionalFile(Utilities.path(destination,"package-list.json"));
    if (first) {
      if (new File(Utilities.path(destination, "package-list.json")).exists()) {
        check(res, false, "Package List already exists, but the publication request says this is the first publication");
      } else {
        boolean ok = true;
        ok = check(res, !Utilities.noString(prSrc.asString("category")), "No category in the publication request") && ok;
        ok = check(res, !Utilities.noString(prSrc.asString("title")), "No title in the publication request") && ok;
        ok = check(res, !Utilities.noString(prSrc.asString("introduction")), "No introduction in the publication request") && ok;
        ok = check(res, !Utilities.noString(prSrc.asString("ci-build")), "No ci-build in the publication request") && ok;
        if (!ok) {
          return res;
        }
        PackageList pl = new PackageList();
        pl.init(npm.name(), npm.canonical(), prSrc.asString("title"), prSrc.asString("category"), prSrc.asString("introduction"));
        pl.addCIBuild("current", prSrc.asString("ci-build"), "Continuous Integration Build (latest in version control) - Content subject to frequent changes",  "ci-build");
        TextFile.stringToFile(pl.toJson(), Utilities.path(destination, "package-list.json"));
      }      
    }
    if (!check(res, new File(Utilities.path(destination, "package-list.json")).exists(), "Destination '"+destination+"' does not contain a package-list.json - cannot proceed")) {
      return res;
    }
    PackageList pl = PackageList.fromFile(Utilities.path(destination, "package-list.json"));

    check(res, id.equals(prSrc.asString("package-id")), "Source publication request has the wrong package id: "+prSrc.asString("package-id")+" (should be "+id+")");
    check(res, id.equals(pl.pid()), "Published Package List has the wrong package id: "+pl.pid()+" (should be "+id+")");
    check(res, canonical.equals(pl.canonical()), "Package List has the wrong canonical: "+pl.canonical()+" (should be "+canonical+")");
    check(res, pl.category() != null, "No Entry found in dest package-list 'category'");
    check(res, pl.ciBuild() != null, "Package List does not have an entry for the current version in the list of versions");

    PackageListEntry vPub = pl.findByVersion(version);
    
    check(res, pl.list().size() > 0, "Destination package-list has no existent version (should have ci-build entry)");
    check(res, vPub == null, "Found an entry in the publication package-list for v"+version+" - it looks like it has already been published");
    check(res, prSrc.has("desc") || prSrc.has("descmd"), "Source publication request has no description for v"+version);
    String pathVer = prSrc.asString("path");
    String vCode = pathVer.substring(pathVer.lastIndexOf("/")+1);
    
    check(res, pathVer.equals(Utilities.pathURL(canonical, vCode)), "Source publication request path is wrong - is '"+prSrc.asString("path")+"', doesn't match canonical)");
    // ok, the ids and canonicals are all lined up, and w're ready to publish     

    check(res, id.equals(qa.asString("package-id")), "Generated IG has wrong package "+qa.asString("package-id"));
    check(res, version.equals(qa.asString("ig-ver")), "Generated IG has wrong version "+qa.asString("ig-ver"));
    check(res, qa.has("errs"), "Generated IG has no error count");
    check(res, npm.fhirVersion().equals(qa.asString("version")), "Generated IG has wrong FHIR version "+qa.asString("version"));
    check(res, qa.asString("url").startsWith(canonical) || qa.asString("url").startsWith(npm.canonical()), "Generated IG has wrong Canonical "+qa.asString("url"));
    
    String destVer = Utilities.path(destination, vCode);
    if (!check(res, new File(destination).exists(), "Destination '"+destVer+"' not found - must be set up manually for first publication")) {
      return res;
    }    
    check(res, !(new File(destVer).exists()), "Nominated path '"+destVer+"' already exists");

    src.needFile(Utilities.path("templates", "header.template"));
    src.needFile(Utilities.path("templates", "preamble.template"));
    src.needFile(Utilities.path("templates", "postamble.template"));
    
    // check to see if there's history template files; if there isn't, copy it in
    check(res, new File(Utilities.path(workingRoot, "templates", "header.template")).exists(), Utilities.path(workingRoot, "templates", "header.template")+" not found - template not set up properly");
    check(res, new File(Utilities.path(workingRoot, "templates", "preamble.template")).exists(), Utilities.path(workingRoot, "templates", "preamble.template")+" not found - template not set up properly");
    check(res, new File(Utilities.path(workingRoot, "templates", "postamble.template")).exists(), Utilities.path(workingRoot, "templates", "postamble.template")+" not found - template not set up properly");
      
    // todo: check the license, header, footer?... 
    
    // well, we've run out of things to test... time to actually try...
    if (res.size() == 0) {
      doPublish(fSource, fOutput, qa, destination, destVer, pathVer, fRoot, ini, pl, prSrc, fRegistry, npm, milestone, date, fHistory, temp, logger, ini.getStringProperty("website", "url"), src);
    }        
    return res;
    
  }

  private File checkDirectory(String filename, List<ValidationMessage> res, String name) throws Exception {
    return checkDirectory(filename, res, name, false);
  }
  
  private File checkDirectory(String filename, List<ValidationMessage> res, String name, boolean autoCreate) throws Exception {
    if (filename == null) {
      throw new Exception("No "+name+" parameter provided");
    }
    File f = new File(filename);
    if (autoCreate && !f.exists()) {
      Utilities.createDirectory(f.getAbsolutePath());
    }
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

  private void doPublish(File fSource, File fOutput, JsonObject qa, String destination, String destVer, String pathVer, File fRoot, IniFile ini, PackageList pl, JsonObject prSrc, File fRegistry, NpmPackage npm, boolean milestone, String date, File history, String tempDir, PublisherConsoleLogger logger, String url, WebSourceProvider src) throws Exception {
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
    updatePackageList(pl, fSource.getAbsolutePath(), prSrc, pathVer,  Utilities.path(destination, "package-list.json"), milestone, date, npm.fhirVersion());
    
    if (milestone) {
      System.out.println("This is a milestone release - publish v"+npm.version()+" to "+destination);
       
      // get the current content from the source
      for (PackageListEntry v : pl.versions()) {
        String path = v.determineLocalPath(url, fRoot.getAbsolutePath());
        src.needFolder(path);
      }
      String path = pl.determineLocalPath(url, fRoot.getAbsolutePath());
      src.needFolder(path);
      
      System.out.println("Clear out existing content");        
      Publisher.main(new String[] { "-delete-current", destination, "-history", history.getAbsolutePath(), "-no-exit"});       

      System.out.println("Copy to directory");        
      FileUtils.copyDirectory(new File(Utilities.path(tempM.getAbsolutePath(), "output")), new File(destination));      
    }
    
    // update indexes and collateral 
    // if it's a milestone
    //  - generate redirects
    //  - update publish box
    // every time
    //  - uppdate history page
    //  - update feed(s)
    //  - update indexes 
    
    // finally
    System.out.println("Rebuild everything for "+Utilities.path(destination, "package-list.json"));
    Publisher.main(new String[] { "-publish-update", "-folder", fRoot.getAbsolutePath(), "-registry", fRegistry.getAbsolutePath(), "-filter", destination, "-history", history.getAbsolutePath(), "-no-exit"});

    if (milestone) {
      new WebSiteArchiveBuilder().buildArchives(new File(destination), fRoot.getAbsolutePath(), url);   
  } else {
     new WebSiteArchiveBuilder().buildArchive(destVer, new ArrayList<>());      
  }
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
  
  private void updatePackageList(PackageList pl, String folder, JsonObject prSrc, String webpath, String filepath, boolean milestone, String date, String fhirVersion) throws Exception {
    if (date == null) {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      date = sdf.format(new Date());      
    }
    if (milestone) {
      for (PackageListEntry e : pl.versions()) {
        e.setCurrent(false);
      }
    }
    PackageListEntry nv = pl.newVersion(prSrc.asString("version"), webpath,  prSrc.asString("status"), prSrc.asString("sequence"), FhirPublication.fromCode(fhirVersion));
    
    nv.setDate(date);
    if (milestone) {
      nv.setCurrent(true);
    }
    String md = null;
    if (prSrc.has("descmd")) {
      md = prSrc.asString("descmd");
      if (md.startsWith("@")) {
        File mdFile = new File(Utilities.path(folder, md.substring(1)));
        if (!mdFile.exists()) {
          throw new Exception("descmd references the file "+md.substring(1)+" but it doesn't exist");
        }
        md = TextFile.fileToString(mdFile);
      }
    }    
    nv.describe(prSrc.asString("desc"), md, prSrc.asString("changes"));

    pl.save(filepath);
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
    JsonObject nqa = JsonParser.parseObject(loadFile("Source QA file", f.getAbsolutePath()));
    compareJsonProp(qa, nqa, "errs", "Errors");
    compareJsonProp(qa, nqa, "warnings", "Warnings");
    compareJsonProp(qa, nqa, "hints", "Hints");
  }

  private void compareJsonProp(JsonObject qa, JsonObject nqa, String name, String label) throws Exception {
    if (!qa.has(name)) {
      throw new Exception("Unable to find "+name+" for past run");
    }
    int oldv = qa.asInteger(name);
    if (!nqa.has(name)) {
      throw new Exception("Unable to find "+name+" for current run");
    }
    int newv = qa.asInteger(name);
    if (newv > oldv) {
      throw new Exception("Increase in "+label+" on run - was "+oldv+", now "+newv);      
    }
  }


}
