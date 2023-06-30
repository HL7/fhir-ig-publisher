package org.hl7.fhir.igtools.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.hl7.fhir.igtools.publisher.IGVersionUtil;
import org.hl7.fhir.igtools.publisher.PastProcessHackerUtilities;
import org.hl7.fhir.igtools.publisher.Publisher;
import org.hl7.fhir.igtools.web.IGRegistryMaintainer.ImplementationGuideEntry;
import org.hl7.fhir.igtools.web.IGReleaseUpdater.ServerType;
import org.hl7.fhir.igtools.web.PublicationProcess.PublicationProcessMode;
import org.hl7.fhir.igtools.web.WebSiteLayoutRulesProviders.WebSiteLayoutRulesProvider;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.ZipGenerator;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.hl7.fhir.utilities.xml.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.gson.JsonSyntaxException;

//throw new Exception("-go-publish is not supported in this version (work in progress)");


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

  public enum PublicationProcessMode {
    WORKING,
    MILESTONE,
    TECHNICAL_CORRECTION;

    public static PublicationProcessMode fromCode(String s) {
      if (Utilities.noString(s)) {
        return WORKING;
      }
      s = s.toLowerCase();
      if ("working".equals(s)) {
        return WORKING;
      }
      if ("milestone".equals(s)) {
        return MILESTONE;
      }
      if ("technical-correction".equals(s)) {
        return TECHNICAL_CORRECTION;
      }
      throw new Error("Unknown publication process mode '"+s+"'");
    }
    
    public String toCode() {
      switch (this) {
      case MILESTONE: return "milestone";
      case TECHNICAL_CORRECTION:return "technical-correction";
      case WORKING:return "working release";
      default:return "??";
      }
    }
    
  }

  /**
   * 
   * @param source - the directory that contains the IG source that will be published. This must be post normal build, and the build must have succeeded, and the output must have gone to \output. it will  be rebuilt
   * @param args 
   * @param destination - the root folder of the local copy of files for the web site to which the IG is being published. 
   * @throws Exception 
   */
  public void publish(String source, String web, String date, String registrySource, String history, String templatesSrc, String temp, String[] args) throws Exception {
    PublisherConsoleLogger logger = new PublisherConsoleLogger();
    logger.start(Utilities.path("[tmp]", "publication-process.log"));
    try {
      System.out.println("FHIR IG Publisher "+IGVersionUtil.getVersionString());
      System.out.println("Detected Java version: " + System.getProperty("java.version")+" from "+System.getProperty("java.home")+" on "+System.getProperty("os.arch")+" ("+System.getProperty("sun.arch.data.model")+"bit). "+toMB(Runtime.getRuntime().maxMemory())+"MB available");
      System.out.println("dir = "+System.getProperty("user.dir")+", path = "+System.getenv("PATH"));
      String s = "Parameters:";
      for (int i = 0; i < args.length; i++) {
        s = s + " "+removePassword(args, i);
      }      
      System.out.println(s);
      System.out.println("---------------");
      System.out.println("Publication Run: publish "+source+" to "+web);
      boolean manualCheck = "true".equals(getNamedParam(args, "-manual-check"));
      List<ValidationMessage> res = publishInner(source, web, date, registrySource, history, templatesSrc, temp, logger, args, manualCheck);
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

  private static String removePassword(String[] args, int i) {
    if (i == 0 || !args[i-1].toLowerCase().contains("password")) {
      return args[i];
    } else {
      return "XXXXXX";
    }
  }


  private static String toMB(long maxMemory) {
    return Long.toString(maxMemory / (1024*1024));
  }

  public List<ValidationMessage> publishInner(String source, String web, String date, String registrySource, String history, String templateSrc, String temp, PublisherConsoleLogger logger, String[] args, boolean manualCheck) throws Exception {
    List<ValidationMessage> res = new ArrayList<>();

    if (temp == null) {
      temp = "[tmp]";
    }
    // check the wider context
    File fSource = checkDirectory(source, res, "Source");

    String workingRoot = Utilities.path(temp, "web-root", "run-"+new SimpleDateFormat("yyyyMMdd").format(new Date()));
    if (new File(workingRoot).exists()) {
      Utilities.clearDirectory(workingRoot);
    } else {
      Utilities.createDirectory(workingRoot);
    }
    File fRoot = checkDirectory(workingRoot, res, "Working Web Folder");
    WebSourceProvider src = new WebSourceProvider(workingRoot, web);
    if (getNamedParam(args, "-upload-server") != null) {
      src.configureUpload(getNamedParam(args, "-upload-server"), getNamedParam(args, "-upload-path"), getNamedParam(args, "-upload-user"), getNamedParam(args, "-upload-password"));
    }

    checkDirectory(Utilities.path(workingRoot, "ig-build-zips"), res, "Destination Zip Folder", true);
    File fRegistry = checkFile(registrySource, res, "Registry");
    File fHistory = checkDirectory(history, res, "History Template");
    src.needFile("publish-setup.json");
    File fPubIni = checkFile(Utilities.path(workingRoot, "publish-setup.json"), res, "publish-setup.json");
    if (res.size() > 0) {
      return res;
    }
    JsonObject pubSetup = JsonParser.parseObject(fPubIni);
    String url = pubSetup.getJsonObject("website").asString("url");

    src.needOptionalFile("package-registry.json");
    if (!check(res, new File(Utilities.path(workingRoot, "package-registry.json")).exists(), "There is no package-registry.json file. Create one by running the publisher -generate-package-registry {folder} (where folder contains the entire website)")) {
      return res;
    }

    src.needOptionalFile("publish-counter.json");
    File fPubCounter = new File(Utilities.path(workingRoot, "publish-counter.json"));
    JsonObject countJson = fPubCounter.exists() ? JsonParser.parseObject(fPubCounter) : new JsonObject();
    int runNumber = (countJson.has("run-number") ? countJson.asInteger("run-number") : 0) + 1; 
    System.out.println("Run Number: "+runNumber);
    countJson.set("run-number", runNumber);
    JsonParser.compose(countJson, new FileOutputStream(fPubCounter), true);

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
    boolean help = true;
    JsonObject rules = getPublishingRules(pubSetup, id, p);
    if (check(res, rules != null, "This website is not set up to publish the IG with Package Id '"+id+"'")) {
      String cURL = calcByRule(rules.str("url"), p);
      if (cURL == null) {
        cURL = url;
      }
      if (check(res, canonical.startsWith(cURL), "Publication URL of '"+canonical+"' is not consistent with the required base URL of '"+cURL+"'")) {
        String cCanonical = calcByRule(rules.str("canonical"), p);
        if (check(res, cCanonical.equals(canonical), "Publication URL of '"+canonical+"' does not match the required web site URL of '"+cCanonical+"'")) {
          String destination = Utilities.path(workingRoot, calcByRule(rules.str("destination"), p));
          help = false;
          publishInner2(source, web, date, registrySource, history, templateSrc, temp, logger, args,
              manualCheck, destination, workingRoot, res, src, id, canonical, version, npm, pubSetup, qa,
              fSource, fOutput, fRoot, fRegistry, fHistory, runNumber);
        }        
      }
    }
    check(res, !help, "For help, consult https://chat.fhir.org/#narrow/stream/179252-IG-creation");
    return res;
  }
      

  private JsonObject getPublishingRules(JsonObject pubSetup, String id, String[] p) {
    for (JsonObject lr : pubSetup.getJsonObjects("layout-rules")) {
      String[] pr = lr.asString("npm").split("\\.");
      if (partsMatch(p, pr)) {
        return lr;
      }
    }
    return null;
  }

  private boolean partsMatch(String[] p, String[] pr) {
    if (p.length != pr.length) {
      return false;
    }
    for (int i = 0; i < p.length; i++) {
      if (!"*".equals(pr[i]) && !pr[i].equals(p[i])) {
        return false;
      }
    }
    return true;
  }

  private String calcByRule(String str, String[] p) {
    if (str == null) {
      return null;
    }
    String ret = str;
    for (int i = 0; i < p.length; i++) {
      ret = ret.replace("{"+(i+1)+"}", p[i]);
    }  
    return ret;
  }


  public List<ValidationMessage> publishInner2(String source, String web, String date, String registrySource, String history, String templateSrc, String temp, 
      PublisherConsoleLogger logger, String[] args, boolean manualCheck, String destination, String workingRoot, List<ValidationMessage> res, WebSourceProvider src,
      String id, String canonical, String version, NpmPackage npm, JsonObject pubSetup, JsonObject qa, 
      File fSource, File fOutput, File fRoot, File fRegistry, File fHistory, int runNumber) throws Exception {
    System.out.println("Relative directory for IG is '"+destination.substring(workingRoot.length())+"'");
    String relDest = Utilities.getRelativePath(workingRoot, destination);
    Utilities.createDirectory(destination);
    
    // ----------------------------------------------

    if (!check(res, !(new File(Utilities.path(source, "package-list.json")).exists()), "Source '"+source+"' contains a package-list.json - must not exist")) {
      return res;
    }            
    if (!check(res, new File(Utilities.path(source, "publication-request.json")).exists(), "Source '"+source+"' does not contain a publication-request.json - consult documentation to see how to set it up")) {
      return res;
    }            
    
    JsonObject prSrc = JsonParser.parseObject(loadFile("Source publication request", Utilities.path(source, "publication-request.json")));

    PublicationProcessMode mode = PublicationProcessMode.fromCode(prSrc.asString("mode"));
    boolean first = prSrc.asBoolean("first"); 
    src.needOptionalFile(relDest == null ? "package-list.json" : Utilities.path(relDest,"package-list.json"));
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
    
    check(res, pathVer.equals(Utilities.pathURL(canonical, vCode)), "Source publication request path is wrong - is '"+prSrc.asString("path")+"', doesn't match canonical '"+canonical+"')");
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

    check(res, (new File(Utilities.path(templateSrc, "header.template")).exists()), "Template header.template not found in templates source ("+templateSrc+")");
    check(res, (new File(Utilities.path(templateSrc, "preamble.template")).exists()), "Template preamble.template not found in templates source ("+templateSrc+")");
    check(res, (new File(Utilities.path(templateSrc, "postamble.template")).exists()), "Template postamble.template not found in templates source ("+templateSrc+")");
      

    File sft = null;
    if (pubSetup.getJsonObject("website").has("search-template")) {
      sft = new File(Utilities.path(templateSrc, pubSetup.getJsonObject("website").asString("search-template")));
      if (!sft.exists()) {
        throw new Error("Search form "+sft.getAbsolutePath()+" not found");
      }
    }
    Map<String, IndexMaintainer> indexes = new HashMap<>();
    if (pubSetup.has("indexes")) {
      JsonObject ndxs = pubSetup.getJsonObject("indexes");
      for (String realm : ndxs.getNames()) {
        JsonObject ndx = ndxs.getJsonObject(realm);
        indexes.put(realm, new IndexMaintainer(realm, ndx.asString("title"), ndx.asString("source"), Utilities.path(fRoot.getAbsolutePath(), ndx.asString("source")), Utilities.path(templateSrc, pubSetup.getJsonObject("website").asString("index-template"))));        
      }
    }
    String tcName = null;
    if (mode == PublicationProcessMode.TECHNICAL_CORRECTION) {
      if (pl.current() == null) {
        throw new Error("Cannot perform a technical correction when the spec hasn't yet been published");        
      }
      if (pl.current().version().equals(prSrc.asString("version"))) {
        throw new Error("Technical correction must have a different version to the currently published version ("+pl.current().version()+")");        
      }
      tcName = pl.pid()+"#"+pl.current().version()+".zip";
    }
    
    // we always need the current folder, if there is one
    // nah, that just wastes time uploading it 
//    if (pl.current() != null) {
//      src.needFolder(relDest, false);
//    }
    // todo: check the license, header, footer?... 
    
    // well, we've run out of things to test... time to actually try...
    if (res.size() == 0) {
      doPublish(fSource, fOutput, qa, destination, destVer, pathVer, fRoot, pubSetup, pl, prSrc, fRegistry, npm, mode, date, fHistory, temp, logger, 
          pubSetup.getJsonObject("website").asString("url"), src, sft, relDest, templateSrc, first, indexes, Calendar.getInstance(), getComputerName(), IGVersionUtil.getVersionString(), gitSrcId(source), Integer.toString(runNumber), tcName, manualCheck,
          workingRoot);
    }        
    return res;    
  }

  private String gitSrcId(String source) {
    return "Unknown (todo)";
  }

  private String getComputerName() {
    Map<String, String> env = System.getenv();
    if (env.containsKey("COMPUTERNAME"))
        return env.get("COMPUTERNAME");
    else if (env.containsKey("HOSTNAME"))
      return env.get("HOSTNAME");
    else if (env.containsKey("USERNAME"))
      return env.get("USERNAME");
    else {
      String res = System.getProperty("user.name");
      if (res == null) {
        return "Unknown Computer";
      } else {
        return res;
      }
    }
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

  private void doPublish(File fSource, File fOutput, JsonObject qa, String destination, String destVer, String pathVer, File fRoot, JsonObject pubSetup, PackageList pl, JsonObject prSrc, File fRegistry, NpmPackage npm, 
      PublicationProcessMode mode, String date, File history, String tempDir, PublisherConsoleLogger logger, String url, WebSourceProvider src, File sft, String relDest, String templateSrc, boolean first, Map<String, IndexMaintainer> indexes,
      Calendar genDate, String username, String version, String gitSrcId, String runNumber, String tcName, boolean manualCheck, String workingRoot) throws Exception {
    // ok. all our tests have passed.
    // 1. do the publication build(s)
    System.out.println("All checks passed. Do the publication build from "+fSource.getAbsolutePath()+" and publish to "+destination);        

    // create a temporary copy and build in that:
    File temp = cloneToTemp(tempDir, fSource, npm.name()+"#"+npm.version());
    File tempM = null; 
    System.out.println("Build IG at "+fSource.getAbsolutePath()+": final copy suitable for publication (in "+temp.getAbsolutePath()+")");        
    runBuild(qa, temp.getAbsolutePath(), new String[] {"-ig", temp.getAbsolutePath(), "-resetTx", "-publish", pathVer, "-no-exit"});

    if (mode != PublicationProcessMode.WORKING) {
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
    PackageListEntry plVer = updatePackageList(pl, fSource.getAbsolutePath(), prSrc, pathVer,  Utilities.path(destination, "package-list.json"), mode, date, npm.fhirVersion(), Utilities.pathURL(pubSetup.asString("url"), tcName));
    updatePublishBox(pl, plVer, destVer, pathVer, destination, fRoot.getAbsolutePath(), false, ServerType.fromCode(pubSetup.getJsonObject("website").asString("server")), sft, null);
    
    List<String> existingFiles = new ArrayList<>();
    if (mode != PublicationProcessMode.WORKING) {
      String igSrc = Utilities.path(tempM.getAbsolutePath(), "output");
      if (mode == PublicationProcessMode.TECHNICAL_CORRECTION) {
        System.out.println("This is a technical correction - publish v"+npm.version()+" to "+destination+" but archive first");
        produceArchive(destVer, Utilities.path(igSrc,tcName));
      } else {
        System.out.println("This is a milestone release - publish v"+npm.version()+" to "+destination);
      }
      
      List<String> ignoreList = new ArrayList<>();
      ignoreList.add(destVer);
      // get the current content from the source
      for (PackageListEntry v : pl.versions()) {
        if (v != plVer) {
          String path = v.determineLocalPath(url, fRoot.getAbsolutePath());
          if (path != null) {
            String relPath = Utilities.getRelativePath(fRoot.getAbsolutePath(), path);
            ignoreList.add(path);
            src.needFolder(relPath, false);
          }
        }
      }
      // we do this first in the output so we can get a proper diff
      updatePublishBox(pl, plVer, igSrc, pathVer, igSrc, fRoot.getAbsolutePath(), true, ServerType.fromCode(pubSetup.getJsonObject("website").asString("server")), sft, null);
      
      System.out.println("Check for Files to delete");        
      List<String> newFiles = Utilities.listAllFiles(igSrc, null);
      List<String> historyFiles = Utilities.listAllFiles(history.getAbsolutePath(), null);
      existingFiles = Utilities.listAllFiles(destination, ignoreList);
      existingFiles.removeAll(newFiles);
      existingFiles.removeAll(historyFiles);
      existingFiles.remove("package-list.json");
      existingFiles.removeIf(s -> s.endsWith("web.config"));
      for (String s : existingFiles) {
        new File(Utilities.path(destination, s)).delete();
      }
      System.out.println("  ... "+existingFiles.size()+" files");        
      System.out.println("Copy to new IG to "+destination);        
      FileUtils.copyDirectory(new File(Utilities.path(tempM.getAbsolutePath(), "output")), new File(destination));
      if (src.isWeb()) {
        new WebSiteArchiveBuilder().buildArchives(new File(destination), fRoot.getAbsolutePath(), url);
      }
    } else {
      if (src.isWeb()) {
        new WebSiteArchiveBuilder().buildArchive(destVer, new ArrayList<>());
      }
      src.cleanFolder(relDest);
    }
    NpmPackage npmB = NpmPackage.fromPackage(new FileInputStream(Utilities.path(destVer, "package.tgz")));
    updateFeed(fRoot, destVer, pl, plVer, pubSetup.forceObject("feeds").asString("package"), false, src, pubSetup.forceObject("website").asString("org"), npmB, genDateS(genDate), username, version, gitSrcId, runNumber);
    updateFeed(fRoot, destVer, pl, plVer, pubSetup.forceObject("feeds").asString("publication"), true, src, pubSetup.forceObject("website").asString("org"), npmB, genDateS(genDate), username, version, gitSrcId, runNumber);
    new PackageRegistryBuilder(workingRoot).update(destination.substring(workingRoot.length()+1), pl);

    new HistoryPageUpdater().updateHistoryPage(history.getAbsolutePath(), destination, templateSrc, !first); 

    IndexMaintainer ndx = getIndexForIg(indexes, npmB.id());
    if (ndx != null) {
      src.needFile(Utilities.changeFileExt(ndx.path(), ".json"));      
      ndx.loadJson();
      ndx.updateForPublication(pl, plVer, mode != PublicationProcessMode.WORKING);
      ndx.buildJson();
      ndx.execute();
    }
    
    boolean go;
    if (manualCheck) {
      System.out.println("The build is complete, and ready to be applied. Check the output at "+fRoot.getAbsolutePath());
      System.out.println("Do you wish to continue? (Y/N)");
      int r = System.in.read();
      go = r == 'y' || r == 'Y';
    } else {
      System.out.println("Build is complete. "+src.verb()+" from "+ fRoot.getAbsolutePath());
      go = true;
    }
    if (go) {
      updateRegistry(fRegistry, pl, plVer, npmB);
      logger.stop();
      FileUtils.copyFile(new File(logger.getFilename()), new File(Utilities.path(fRoot.getAbsolutePath(), "ig-build-zips", npm.name()+"#"+npm.version()+".log")));
      src.finish(relDest, existingFiles);
      System.out.println("Finished Publishing. "+src.instructions(existingFiles.size()));
    } else {
      System.out.println("No!");
      System.out.print("Changes not applied. Finished");
    }
  }

  private void produceArchive(String source, String dest) throws IOException {
    System.out.println("Zipping "+source+" to "+dest);
    if (new File(dest).exists()) {
      System.out.println(" "+dest+": already exists");
      return;
    }
    ZipGenerator zip = new ZipGenerator(dest);
    addFolderToZip(zip, new File(source), source.length()+1);
    zip.close();    
  }

  private int addFolderToZip(ZipGenerator zip, File folder, int offset) throws FileNotFoundException, IOException {
    int c = 0;
    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        c = c + addFolderToZip(zip, f, offset);
      } else {
        zip.addBytes(f.getAbsolutePath().substring(offset), TextFile.fileToBytes(f), false);
        c++;
      }
    }
    return c;
  }

  private String genDateS(Calendar genDate) {
    return new SimpleDateFormat("dd/MM/yyyy", new Locale("en", "US")).format(genDate.getTime());
  }

  private String tail(String path) {
    return path.substring(path.lastIndexOf("/")+1);
  }

  private void updateRegistry(File fRegistry, PackageList pl, PackageListEntry plVer, NpmPackage npmB) throws JsonSyntaxException, FileNotFoundException, IOException {
    IGRegistryMaintainer reg = new IGRegistryMaintainer(fRegistry.getAbsolutePath());
    ImplementationGuideEntry rc = reg.seeIg(pl.pid(), pl.canonical(), pl.title(), pl.category());
    
    if (reg != null) {
      reg.seeCiBuild(rc, pl.ciBuild().path(), "package-list.json");
    }
    
    boolean hasRelease = false;
    if (reg != null) {
      if (plVer.status().equals("release") || plVer.status().equals("trial-use") || plVer.status().equals("update")) {
        reg.seeRelease(rc, plVer.status().equals("update") ? "STU Update" : plVer.sequence(), plVer.version(), plVer.fhirVersion(), plVer.path());
        hasRelease = true;
      } else if (!hasRelease && VersionUtilities.packageForVersion(plVer.fhirVersion()) != null) {
        reg.seeCandidate(rc, plVer.sequence()+" "+Utilities.titleize(plVer.status()), plVer.version(), plVer.fhirVersion(), plVer.path());
      }
    }
    reg.finish();
  }

  private IndexMaintainer getIndexForIg(Map<String, IndexMaintainer> indexes, String packageId) {
    String realm = Utilities.charCount(packageId, '.') > 1 ? packageId.split("\\.")[2] : null;
    String code = Utilities.charCount(packageId, '.') > 2 ? packageId.split("\\.")[3] : null;
    return realm == null || ("uv".equals(realm) && Utilities.existsInList(code, "smart-app-launch", "extensions")) ? null : indexes.get(realm);
  }

  private void updateFeed(File fRoot, String destVer, PackageList pl, PackageListEntry plVer, String file, boolean isPublication, WebSourceProvider src, String orgName, NpmPackage npm, String genDate, String username, String version, String gitSrcId, String runNumber) throws IOException {
    if (!Utilities.noString(file)) {
      src.needFile(file);
      if (!updateFeedAsXml(Utilities.path(fRoot.getAbsolutePath(), file), pl, plVer, isPublication, orgName, npm, genDate, username, version, gitSrcId, runNumber)) {
        String newContent = makeEntry(pl, plVer, isPublication, orgName, npm, genDate, username, version, gitSrcId, runNumber);
        String rss = TextFile.fileToString(Utilities.path(fRoot.getAbsolutePath(), file));
        int i = rss.indexOf("<item");
        while (rss.charAt(i-1) == ' ') {
          i--;
        }
        rss = rss.substring(0, i) + newContent+rss.substring(i);
        TextFile.stringToFile(rss, Utilities.path(fRoot.getAbsolutePath(), file));
      }
    }
  }

  private boolean updateFeedAsXml(String file, PackageList pl, PackageListEntry plVer, boolean isPublication, String orgName, NpmPackage npm, String genDate, String username, String version, String gitSrcId, String runNumber) {
    String link = Utilities.pathURL(plVer.path(), isPublication ? "index.html" : "package.tgz");
    try {
      Document xml = XMLUtil.parseFileToDom(file);
      Element rss = xml.getDocumentElement();
      Element channel = XMLUtil.getNamedChild(rss, "channel");
      Element item = XMLUtil.getNamedChild(channel, "item");
      Element nitem = null;
      if (item == null) {
        channel.appendChild(xml.createTextNode("\n    "));
        nitem = xml.createElement("item");
        channel.appendChild(nitem);        
      } else {
        nitem = xml.createElement("item");
        channel.insertBefore(nitem, item);        
        channel.insertBefore(xml.createTextNode("\n    "), item);        
      }
      XMLUtil.addTextTag(xml, nitem, "title", isPublication ? pl.title()+" version "+plVer.version() : pl.pid()+"#"+plVer.version(), 6);
      XMLUtil.addTextTag(xml, nitem, "description", plVer.desc(), 6);
      XMLUtil.addTextTag(xml, nitem, "link", link, 6);
      XMLUtil.addTextTag(xml, nitem, "guid", link, 6).setAttribute("isPermaLink", "true");
      XMLUtil.addTextTag(xml, nitem, "dc:creator", orgName, 6);
      XMLUtil.addTextTag(xml, nitem, "fhir:version", plVer.fhirVersion(), 6);
      XMLUtil.addTextTag(xml, nitem, "fhir:kind", npm.getNpm().asString("type"), 6);
      XMLUtil.addTextTag(xml, nitem, "pubDate", presentDate(npm.dateAsDate()), 6);
      XMLUtil.addTextTag(xml, nitem, "fhir:details", "Publication run at "+genDate+" by "+username+" using v"+version+" source id "+gitSrcId+" Run #"+runNumber, 6);
      nitem.appendChild(xml.createTextNode("\n    "));
      
      Element dt = XMLUtil.getNamedChild(channel, "lastBuildDate");
      dt.getChildNodes().item(0).setTextContent(presentDate(npm.dateAsDate()));
      dt = XMLUtil.getNamedChild(channel, "pubDate");
      dt.getChildNodes().item(0).setTextContent(presentDate(npm.dateAsDate()));
      XMLUtil.writeDomToFile(xml, file);
      return true;
    } catch (Exception e) {
      System.out.println("Unable to process RSS feed "+file+" as XML - processing as text instead ("+e.getMessage()+")");
      return false;
    }
  }

  private String makeEntry(PackageList pl, PackageListEntry plVer, boolean isPublication, String orgName, NpmPackage npm, String genDate, String username, String version, String gitSrcId, String runNumber) {
    String link = Utilities.pathURL(plVer.path(), isPublication ? "index.html" : "package.tgz");

    StringBuilder b = new StringBuilder();
    b.append("    <item>\r\n");
    b.append("      <title>"+Utilities.escapeXml(isPublication ? pl.title()+" version "+plVer.version() : pl.pid()+"#"+plVer.version())+"</title>\r\n");
    b.append("      <description>"+Utilities.escapeXml(plVer.desc())+"</description>\r\n");
    b.append("      <link>"+Utilities.escapeXml(link)+"</link>\r\n");
    b.append("      <guid isPermaLink=\"true\">"+Utilities.escapeXml(link)+"</guid>\r\n");
    b.append("      <dc:creator>"+Utilities.escapeXml(orgName)+"</dc:creator>\r\n");
    b.append("      <fhir:version>"+plVer.fhirVersion()+"</fhir:version>\r\n");
    b.append("      <fhir:kind>"+npm.getNpm().asString("type")+"</fhir:kind>\r\n");
    b.append("      <pubDate>"+presentDate(npm.dateAsDate())+"</pubDate>\r\n");
    b.append("      <fhir:details>Publication run at "+genDate+" by "+username+" using v"+version+" source id "+gitSrcId+" Run #"+runNumber+"</fhir:details>\r\n");
    b.append("    </item>\r\n");
    
    return b.toString();
  }
  
  private static final String RSS_DATE = "EEE, dd MMM yyyy hh:mm:ss Z";
  public String presentDate(Date date) {
    SimpleDateFormat sdf = new SimpleDateFormat(RSS_DATE, new Locale("en", "US"));// Wed, 04 Sep 2019 08:58:14 GMT      
    return sdf.format(date);
  }

  private void updatePublishBox(PackageList pl, PackageListEntry plVer, String destVer, String pathVer, String destination, String rootFolder, boolean current, ServerType serverType, File sft, List<String> ignoreList) throws FileNotFoundException, IOException {
    IGReleaseVersionUpdater igvu = new IGReleaseVersionUpdater(destVer, ignoreList, null, plVer.json(), destination);
    String fragment = PublishBoxStatementGenerator.genFragment(pl, plVer, pl.current(), pl.canonical(), current, false);
    System.out.println("Publish Box Statement: "+fragment);
    igvu.updateStatement(fragment, current ? 0 : 1);
    System.out.println("  .. "+igvu.getCountTotal()+" files checked, "+igvu.getCountUpdated()+" updated");
    
    igvu.checkXmlJsonClones(destVer);
    System.out.println("  .. "+igvu.getClonedTotal()+" clones checked, "+igvu.getClonedCount()+" updated");
    IGReleaseRedirectionBuilder rb = new IGReleaseRedirectionBuilder(destVer, pl.canonical(), plVer.path(), rootFolder);
    if (serverType == ServerType.APACHE) {
      rb.buildApacheRedirections();
    } else if (serverType == ServerType.ASP2) {
      rb.buildNewAspRedirections(false, false);
    } else if (serverType == ServerType.ASP1) {
      rb.buildOldAspRedirections();
    } else if (serverType == ServerType.LITESPEED) {
      rb.buildLitespeedRedirections();
    } else if (!pl.canonical().contains("hl7.org/fhir")) {
      rb.buildApacheRedirections();
    } else {
      rb.buildOldAspRedirections();
    }
    System.out.println("  .. "+rb.getCountTotal()+" redirections ("+rb.getCountUpdated()+" created/updated)");
    new DownloadBuilder(destVer, pl.canonical(), current ?  pl.canonical() : plVer.path(), ignoreList).execute();
    if (!current && serverType == ServerType.ASP2) {
      new VersionRedirectorGenerator(destination).execute(plVer.version(), plVer.path());
    }

    
    if (sft != null) {
      String html = TextFile.fileToString(sft);
      html = fixParameter(html, "title", pl.title());
      html = fixParameter(html, "id", pl.pid());
      html = fixParameter(html, "version", current ? "All Versions" : plVer.version());
      html = fixParameter(html, "path", current ? pl.canonical() : plVer.path());
      html = fixParameter(html, "history", current ? "history.html" : "../history.html");
      html = fixParameter(html, "search-list", searchLinks(current, plVer, pl.canonical(), pl.list()));
      html = fixParameter(html, "note", current ? "this search searches all versions of the "+pl.title()+", including balloted versions. You can also search specific versions" :
        "this search searches version "+plVer.version()+" of the "+pl.title()+". You can also search other versions, or all versions at once");
      html = fixParameter(html, "prefix", "");            
      TextFile.stringToFile(html, Utilities.path(destVer, "searchform.html"), false);          
    }
  }


  private String searchLinks(boolean root, PackageListEntry focus, String canonical, List<PackageListEntry> list) {
    StringBuilder b = new StringBuilder();
    if (!root) {
      b.append(" <li><a href=\""+canonical+"/searchform.html\">All Versions</a></li>\r\n");
    }
    for (PackageListEntry n : list) {
      if (!n.cibuild()) {
        String v = n.version();
        String path = n.path();
        String date = n.date();
        if (n == focus && !root) {
          b.append(" <li>"+n.sequence()+" "+Utilities.titleize(n.status())+" (v"+v+", "+summariseDate(date)+") (this version)</li>\r\n");
        } else {
          b.append(" <li><a href=\""+path+"/searchform.html\">"+n.sequence()+" "+Utilities.titleize(n.status())+" (v"+v+", "+summariseDate(date)+")</a></li>\r\n");
        }
      }
    }
    return b.toString();
  }


  private String summariseDate(String d) {
    if (d == null || d.length() < 10) {
      return "??";
    }
    return d.substring(0,7);
  }

  private String fixParameter(String html, String name, String value) {
    while (html.contains("[%"+name+"%]")) {
      html = html.replace("[%"+name+"%]", value == null ? "" : value);
    }
    return html;
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
  
  private PackageListEntry updatePackageList(PackageList pl, String folder, JsonObject prSrc, String webpath, String filepath, PublicationProcessMode mode, String date, String fhirVersion, String tcPath) throws Exception {
    if (date == null) {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      date = sdf.format(new Date());      
    }
    PackageListEntry nv = null;
    
    if (mode == PublicationProcessMode.TECHNICAL_CORRECTION) {
      nv = pl.current();
      nv.update(prSrc.asString("version"), webpath,  prSrc.asString("status"), prSrc.asString("sequence"), FhirPublication.fromCode(fhirVersion), tcPath, date);
    } else {
      if (mode != PublicationProcessMode.WORKING) {
        for (PackageListEntry e : pl.versions()) {
          e.setCurrent(false);
        }
      }
      nv = pl.newVersion(prSrc.asString("version"), webpath,  prSrc.asString("status"), prSrc.asString("sequence"), FhirPublication.fromCode(fhirVersion));

      nv.setDate(date);
      if (mode != PublicationProcessMode.WORKING) {
        nv.setCurrent(true);
      }
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
    return nv;
  }

  private void zipFolder(File fSource, String path) throws IOException {
    ZipGenerator zip = new ZipGenerator(path);
    zip.addFolder(fSource.getAbsolutePath(), "", false);
    zip.close();
  }

  private void runBuild(JsonObject qa, String path, String[] args) throws Exception {
    File f = new File(Utilities.path(path, "output", "qa.json"));
    if (!f.exists()) {
      f.delete();
    }
    
    Publisher.main(args); // any exceptions, we just let them propagate

    // check it built ok:
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

  private static String getNamedParam(String[] args, String param) {
    boolean found = false;
    for (String a : args) {
      if (found)
        return a;
      if (a.equals(param)) {
        found = true;
      }
    }
    return null;
  }


}
