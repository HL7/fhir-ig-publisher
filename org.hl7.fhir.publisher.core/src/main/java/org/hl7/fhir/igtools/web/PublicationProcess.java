package org.hl7.fhir.igtools.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.utils.NPMPackageGenerator;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.MarkDownProcessor.Dialect;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.ZipGenerator;
import org.hl7.fhir.utilities.json.JsonException;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageGenerator.PackageType;
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
 
  private int exitCode = 1;
  
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
    CREATION,
    WORKING,
    MILESTONE,
    TECHNICAL_CORRECTION,
    WITHDRAWAL;

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
      if ("creation".equals(s)) {
        return CREATION;
      }
      if ("technical-correction".equals(s)) {
        return TECHNICAL_CORRECTION;
      }
      if ("withdrawal".equals(s)) {
        return WITHDRAWAL;
      }
      throw new Error("Unknown publication process mode '"+s+"'");
    }
    
    public String toCode() {
      switch (this) {
      case MILESTONE: return "milestone";
      case TECHNICAL_CORRECTION:return "technical-correction";
      case WORKING:return "working release";
      case WITHDRAWAL : return "withdrawal";
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
  public void publish(String source, String web, String date, String registrySource, String history, String templatesSrc, String temp, String igBuildZipParam, String[] args) throws Exception {
    PublisherConsoleLogger logger = new PublisherConsoleLogger();
    logger.start(Utilities.path("[tmp]", "publication-process.log"));
    try {
      Date dd = new Date(); 
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      if (Utilities.noString(date)) {
        date = sdf.format(dd);
      } else {
        dd = sdf.parse(date);
      }
      
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
      List<ValidationMessage> res = publishInner(source, web, date, dd, registrySource, history, templatesSrc, temp, igBuildZipParam, logger, args);
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
    System.exit(exitCode);
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

  public List<ValidationMessage> publishInner(String source, String web, String date, Date dd, String registrySource, String history, String templateSrc, String temp, String igBuildZipParam, PublisherConsoleLogger logger, String[] args) throws Exception {
    List<ValidationMessage> res = new ArrayList<>();

    if (temp == null) {
      temp = "[tmp]";
    }
    File sf = new File(source);
    if (sf.exists() && !sf.isDirectory() && sf.getName().endsWith(".json")) {
      JsonObject pr = JsonParser.parseObject(sf);
      if ("withdrawal".equals(pr.asString("mode"))) {
        return null; // publishWithdrawal(pr, web, );
      }
    }
    // check the wider context
    File fSource = checkDirectory(source, res, "Source");

    String workingRoot = Utilities.path(temp, "web-root", "run-"+new SimpleDateFormat("yyyyMMdd").format(new Date()));
    if (new File(workingRoot).exists()) {
      FileUtilities.clearDirectory(workingRoot);
    } else {
      FileUtilities.createDirectory(workingRoot);
    }
    File fRoot = checkDirectory(workingRoot, res, "Working Web Folder");
    WebSourceProvider src = new WebSourceProvider(workingRoot, web);
    if (getNamedParam(args, "-upload-server") != null) {
      throw new Error("Uploading files by FTP is no longer supported");
    }

    File igBuildZipDir = new File(igBuildZipParam == null ? Utilities.path(workingRoot, "ig-build-zips") : igBuildZipParam); 
    checkDirectory(igBuildZipDir.getAbsolutePath(), res, "Destination Zip Folder", true);
    File fRegistry = checkFile(registrySource, res, "Registry");
    File fHistory = checkDirectory(history, res, "History Template");
    src.needFile("publish-setup.json");
    File fPubIni = checkFile(Utilities.path(workingRoot, "publish-setup.json"), res, "publish-setup.json");
    if (res.size() > 0) {
      return res;
    }
    JsonObject pubSetup = JsonParser.parseObject(fPubIni);
    String url = pubSetup.getJsonObject("website").asString("url");
    if (!check(res, pubSetup.getJsonObject("website").has("clone-xml-json"), "publish-setup.json does not have a '$.website.clone-xml-json' property - consult the FHIR Product Director")) {
      return res;
    }
    boolean jsonXmlClones = pubSetup.getJsonObject("website").asBoolean("clone-xml-json");
    
    src.needOptionalFile("publish-counter.json");
    if (!check(res, !new File(Utilities.path(workingRoot, "publish-counter.json")).exists(), "Found a publish-counter.json file. This indicates that the source folder is not correctly set up")) {
      return res;
    }

    src.needOptionalFile("package-registry.json");
    if (!check(res, new File(Utilities.path(workingRoot, "package-registry.json")).exists(), "There is no package-registry.json file. Create one by running the publisher -generate-package-registry {folder} (where folder contains the entire website)")) {
      return res;
    }


    if (!check(res, !(new File(Utilities.path(source, "package-list.json")).exists()), "Source '"+source+"' contains a package-list.json - must not exist")) {
      return res;
    }            
    if (!check(res, new File(Utilities.path(source, "publication-request.json")).exists(), "Source '"+source+"' does not contain a publication-request.json - consult documentation to see how to set it up")) {
      return res;
    }            

    JsonObject prSrc = JsonParser.parseObject(loadFile("Source publication request", Utilities.path(source, "publication-request.json")));
    PublicationProcessMode mode = PublicationProcessMode.fromCode(prSrc.asString("mode"));

    if (!check(res,  VersionUtilities.isSemVer(prSrc.asString("version"), false), "Publication Request version '"+prSrc.asString("version")+"' is not a valid semver")) {
      return res;
    }

    String id = prSrc.asString("package-id");
    String[] p = id.split("\\.");
    
    boolean help = true;
    JsonObject rules = getPublishingRules(pubSetup, id, p);
    if (check(res, rules != null, "This website does not have an entry in the layout rules in "+fPubIni.getAbsolutePath()+" to publish the IG with Package Id '"+id+"'")) {
      String cURL = calcByRule(rules.str("url"), p);
      if (cURL == null) {
        cURL = url;
      }
      String destination = Utilities.path(workingRoot, calcByRule(rules.str("destination"), p));
      help = false;
      if (mode == PublicationProcessMode.WITHDRAWAL) {
        processWithdrawal(source, web, url, date, dd, registrySource, history, templateSrc, temp, logger, args,
            destination, workingRoot, res, src, id, pubSetup, fSource, fRoot, fRegistry, fHistory, 
            jsonXmlClones, igBuildZipDir, prSrc, mode);
      } else {
        // check the output
        File fOutput = checkDirectory(Utilities.path(source, "output"), res, "Publication Source");
        File fQA = checkFile(Utilities.path(source, "output", "qa.json"), res, "Publication QA info");
        if (res.size() > 0) {
          return res;
        }
        JsonObject qa = JsonParser.parseObject(loadFile("Source QA file", fQA.getAbsolutePath()));

        // now: is the IG itself ready for publication
        NpmPackage npm = NpmPackage.fromPackage(loadFile("Source package", Utilities.path(source, "output", "package.tgz")));
        String npmid = npm.name();

        check(res, npmid.equals(id), "Source publication has the wrong package id: "+npmid+" (should be "+id+")");
        String canonical = PastProcessHackerUtilities.actualUrl(npm.canonical());
        if (!check(res, canonical != null, "canonical URL not found")) {
          return res;
        }
        String version = npm.version();
        if (!check(res, version != null, "Source Package has no version")) {
          return res;
        }

        if (!canonical.startsWith(cURL)) {
          System.out.println("Publication URL of '"+canonical+"' is not consistent with the required base URL of '"+cURL+"'");
        }
        String cCanonical = calcByRule(rules.str("canonical"), p);
        if (!cCanonical.equals(canonical)) {
          System.out.println("Publication URL of '"+canonical+"' does not match the required web site URL of '"+cCanonical+"'");
        }

        publishInner2(source, web, date, dd, registrySource, history, templateSrc, temp, logger, args,
            destination, workingRoot, res, src, id, canonical, version, npm, pubSetup, qa,
            fSource, fOutput, fRoot, fRegistry, fHistory, jsonXmlClones, igBuildZipDir, prSrc, mode, pubSetup.asBoolean("canonical-mismatch"));
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


  public List<ValidationMessage> publishInner2(String source, String web, String date, Date dd, String registrySource, String history, String templateSrc, String temp, 
      PublisherConsoleLogger logger, String[] args, String destination, String workingRoot, List<ValidationMessage> res, WebSourceProvider src,
      String id, String canonical, String version, NpmPackage npm, JsonObject pubSetup, JsonObject qa, 
      File fSource, File fOutput, File fRoot, File fRegistry, File fHistory, boolean jsonXmlClones, File igBuildZipDir, JsonObject prSrc, PublicationProcessMode mode, boolean canonicalMisMatch) throws Exception {
    System.out.println("Relative directory for IG is '"+destination.substring(workingRoot.length())+"'");
    String relDest = FileUtilities.getRelativePath(workingRoot, destination);
    FileUtilities.createDirectory(destination);

    System.out.println("===== Web Publication Run Details ===============================");
    System.out.println(" Source IG: "+npm.name()+"#"+npm.version()+" : "+npm.canonical()+" ("+VersionUtilities.getNameForVersion(npm.fhirVersion())+") from "+source); 
    
    // ----------------------------------------------

    if (mode != PublicationProcessMode.CREATION) {
      JsonObject json = JsonParser.parseObject(npm.load("package", "package.json"));
      JsonObject dep = json.getJsonObject("dependencies");
      if (dep != null) {
        for (JsonProperty jp : dep.getProperties()) {
          String ver = jp.getValue().asJsonString().getValue();
          if (Utilities.existsInList(ver, "current", "cibuild", "dev")) {
            if (!prSrc.asBoolean("allow-current-dependencies")) {
              check(res, false, "Package "+json.asString("name")+"#"+json.asString("version")+" depends on "+jp.getName()+"#"+ver+" which is not allowed (current version check)");
              return res;
            }
          }
        }
      }
    }
    boolean first = prSrc.asBoolean("first"); 
    src.needOptionalFile(Utilities.noString(relDest) ? "package-list.json" : Utilities.path(relDest,"package-list.json"));
    if (first) {
      if (new File(Utilities.path(destination, "package-list.json")).exists()) {
        PackageList pl = PackageList.fromFile(Utilities.path(destination, "package-list.json"));
        if (pl.versions().size() > ((pl.ciBuild() == null) ? 0 : 1)) {
          check(res, false, "Package List already exists, but the publication request says this is the first publication");
          return res;
        }
      } 
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
      if ("current-only".equals(prSrc.asString("publish-pattern"))) {
        pl.setCurrentOnly(true);
      }
      FileUtilities.stringToFile(pl.toJson(), Utilities.path(destination, "package-list.json"));
           
    }
    if (!check(res, new File(Utilities.path(destination, "package-list.json")).exists(), "Destination '"+destination+"' does not contain a package-list.json - cannot proceed")) {
      return res;
    }
    PackageList pl = PackageList.fromFile(Utilities.path(destination, "package-list.json"));

    if (pl.isCurrentOnly()) {
      mode = PublicationProcessMode.MILESTONE;
    }
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


    check(res, canonicalMisMatch || pathVer.equals(Utilities.pathURL(canonical, vCode)), "Source publication request path is wrong - is '"+pathVer+"', doesn't match expected based on canonical of '"+Utilities.pathURL(canonical, vCode)+"')");
    // ok, the ids and canonicals are all lined up, and w're ready to publish     

    check(res, id.equals(qa.asString("package-id")), "Generated IG has wrong package "+qa.asString("package-id"));
    check(res, version.equals(qa.asString("ig-ver")), "Generated IG has wrong version "+qa.asString("ig-ver"));
    check(res, qa.has("errs"), "Generated IG has no error count");
    check(res, npm.fhirVersion().equals(qa.asString("version")), "Generated IG has wrong FHIR version "+qa.asString("version"));
    check(res, qa.asString("url").startsWith(canonical) || qa.asString("url").startsWith(npm.canonical()), "Generated IG has wrong Canonical "+qa.asString("url"));
    
    src.needOptionalFolder(vCode, false);
    
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
    String tcPath = null;
    PackageListEntry tcVer = null;
    if (mode == PublicationProcessMode.TECHNICAL_CORRECTION) {
      checkFile(Utilities.path(templateSrc, "tech-correction.template.html"), res, "Technical Correction Template");
      if (pl.current() == null) {
        throw new Error("Cannot perform a technical correction when the spec hasn't yet been published");        
      }
      tcVer = pl.current();
      if (tcVer.version().equals(prSrc.asString("version"))) {
        throw new Error("Technical correction must have a different version to the currently published version ("+pl.current().version()+")");        
      }
      tcName = pl.pid()+"-"+tcVer.version()+".zip";
      tcPath = tcVer.determineLocalPath(pubSetup.getJsonObject("website").asString("url"), workingRoot);
      String tcRelPath = FileUtilities.getRelativePath(workingRoot, tcPath); 
      src.needFolder(tcRelPath, false);
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
          pubSetup.getJsonObject("website").asString("url"), src, sft, relDest, templateSrc, first, indexes, dd, getComputerName(), 
          IGVersionUtil.getVersionString(), gitSrcId(source), tcName, tcPath, tcVer, workingRoot, jsonXmlClones, igBuildZipDir, args);
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
      FileUtilities.createDirectory(f.getAbsolutePath());
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
                         Date genDate, String username, String version, String gitSrcId, String tcName, String tcPath, PackageListEntry tcVer, String workingRoot, boolean jsonXmlClones, File igBuildZipDir, String[] args) throws Exception {
    // ok. all our tests have passed.
    // 1. do the publication build(s)
    List<String> existingFiles = new ArrayList<>();
    if (mode == PublicationProcessMode.CREATION) {
      System.out.println("All checks passed. Create an empty history at "+destination);              
    } else {
      System.out.println("All checks passed. Do the publication build from "+fSource.getAbsolutePath()+" and publish to "+destination);        

      // create a temporary copy and build in that:
      File temp = cloneToTemp(tempDir, fSource, npm.name()+"#"+npm.version());
      File tempM = null; 
      System.out.println("Build IG at "+fSource.getAbsolutePath()+": final copy suitable for publication (in "+temp.getAbsolutePath()+")");
      String[] baseParams = new String[] {"-publish", pathVer, "-no-exit" };
      String tx = getNamedParam(args, "-tx");
      if (tx != null) {
        baseParams = Utilities.concatStringArray(baseParams, new String[] {"-tx", tx});
      }
      runBuild(qa, temp.getAbsolutePath(), Utilities.concatStringArray(baseParams, new String[] {"-ig", temp.getAbsolutePath(), "-resetTx"}));

      if (mode != PublicationProcessMode.WORKING) {
        tempM = cloneToTemp(tempDir, temp, npm.name()+"#"+npm.version()+"-milestone");
        System.out.println("Build IG at "+fSource.getAbsolutePath()+": final copy suitable for publication (in "+tempM.getAbsolutePath()+") (milestone build)");        
        runBuild(qa, tempM.getAbsolutePath(), Utilities.concatStringArray(baseParams, new String[] {"-ig", tempM.getAbsolutePath(), "-milestone"}));
      }

      // 2. make a copy of what we built
      System.out.println("Keep a copy of the build directory at "+Utilities.path(igBuildZipDir, npm.name()+"#"+npm.version()+".zip"));    

      // 2.1. Delete the ".git" subfolder
      delTempFolder(temp, ".git");
      delTempFolder(temp, "temp");
      delTempFolder(temp, "template");

      zipFolder(temp, Utilities.path(igBuildZipDir, npm.name()+"#"+npm.version()+".zip"));

      System.out.println("");        
      System.out.println("ok. All checks passed. Publish v"+npm.version()+" to "+destVer);

      // 3. create the folder {root}/{realm}/{code}/{subdir}
      System.out.println("Copy the IG to "+destVer);    
      FileUtilities.createDirectory(destVer);
      if (pl.isCurrentOnly()) {
        FileUtilities.copyFile(new File(Utilities.path(temp.getAbsolutePath(), "output", "package.tgz")), new File(Utilities.path(destVer, "package.tgz")));
      } else {
        FileUtils.copyDirectory(new File(Utilities.path(temp.getAbsolutePath(), "output")), new File(destVer));
      }
      List<String> subPackages = loadSubPackageList(Utilities.path(temp.getAbsolutePath(), "output", "sub-package-list.json"));
      // now, update the package list 
      System.out.println("Update "+Utilities.path(destination, "package-list.json"));    
      PackageListEntry plVer = updatePackageList(pl, fSource.getAbsolutePath(), prSrc, pathVer,  Utilities.path(destination, "package-list.json"), mode, date, npm.fhirVersion(), Utilities.pathURL(pubSetup.asString("url"), tcName), subPackages);
      updatePublishBox(pl, plVer, destVer, pathVer, destination, fRoot.getAbsolutePath(), false, ServerType.fromCode(pubSetup.getJsonObject("website").asString("server")), sft, null, url, jsonXmlClones);

      if (mode != PublicationProcessMode.WORKING) {
        String igSrc = Utilities.path(tempM.getAbsolutePath(), "output");
        if (mode == PublicationProcessMode.TECHNICAL_CORRECTION) {
          
          System.out.println("This is a technical correction - publish v"+npm.version()+" to "+destination+" but archive to "+tcName+" first");
          produceArchive(tcPath, Utilities.path(igSrc,tcName));
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
              String relPath = FileUtilities.getRelativePath(fRoot.getAbsolutePath(), path);
              ignoreList.add(path);
              src.needFolder(relPath, false);
              if (!v.cibuild() && !v.current()) {
                String pv = v.path();
                String vCode = pv.substring(pv.lastIndexOf("/")+1);
                String dv = Utilities.path(fRoot, relPath);
                System.out.println("Update publish box for version "+v.version()+" @ "+v.path());
                updatePublishBox(pl, v, dv, pv, destination, fRoot.getAbsolutePath(), false, null, null, null, url, jsonXmlClones);
              }
            }
          }
        }
        if (mode == PublicationProcessMode.TECHNICAL_CORRECTION) {
          // replace every html file in the /version specific subfolder with a notice indicating that this version has been technically corrected, and a link to the same page in the new version
          String tcTemplate = FileUtilities.fileToString(Utilities.path(templateSrc, "tech-correction.template.html"));
          for (File f : new File(tcPath).listFiles()) {
            if (f.getName().endsWith(".html")) {
              boolean found = new File(Utilities.path(destVer, f.getName())).exists();
              String cnt = tcTemplate.replace("[%title%]", pl.title());
              if (!found) {
                cnt = cnt.replace("[%tc-link%]", "removed");              
              } else {
                cnt = cnt.replace("[%tc-link%]", "replaced by <a href=\""+Utilities.pathURL(pathVer, f.getName())+"\">this new page</a>");
              }
              FileUtilities.stringToFile(cnt, f);
            }
          }
          String vCode = tcPath.substring(tcPath.lastIndexOf("/")+1);
          String dv = Utilities.path(destination, vCode);
          updatePublishBox(pl, tcVer, dv, tcPath, destination, fRoot.getAbsolutePath(), false, null, null, null, url, jsonXmlClones);
        }
        // we do this first in the output so we can get a proper diff
        updatePublishBox(pl, plVer, igSrc, pathVer, igSrc, fRoot.getAbsolutePath(), true, ServerType.fromCode(pubSetup.getJsonObject("website").asString("server")), sft, null, url, jsonXmlClones);

        System.out.println("Check for Files to delete");        
        List<String> newFiles = FileUtilities.listAllFiles(igSrc, null);
        List<String> historyFiles = FileUtilities.listAllFiles(history.getAbsolutePath(), null);
        existingFiles = FileUtilities.listAllFiles(destination, ignoreList);
        existingFiles.removeAll(newFiles);
        existingFiles.removeAll(historyFiles);
        existingFiles.remove("package-list.json");
        existingFiles.remove("package-registry.json");
        existingFiles.remove("publish-setup.json");
        existingFiles.removeIf(s -> s.endsWith("web.config"));
        for (String s : existingFiles) {
          new File(Utilities.path(destination, s)).delete();
        }
        System.out.println("  ... "+existingFiles.size()+" files");        
        System.out.println("Copy to new IG "+destination);        
        FileUtils.copyDirectory(new File(Utilities.path(tempM.getAbsolutePath(), "output")), new File(destination));  

      } else {
        src.cleanFolder(relDest);
      }
      NpmPackage npmB = NpmPackage.fromPackage(new FileInputStream(Utilities.path(destVer, "package.tgz")));
      updateFeed(fRoot, destVer, pl, plVer, pubSetup.forceObject("feeds").asString("package"), false, src, pubSetup.forceObject("website").asString("org"), npmB, genDateS(genDate), username, version, gitSrcId, subPackages);
      updateFeed(fRoot, destVer, pl, plVer, pubSetup.forceObject("feeds").asString("publication"), true, src, pubSetup.forceObject("website").asString("org"), npmB, genDateS(genDate), username, version, gitSrcId, subPackages);
      new PackageRegistryBuilder(workingRoot).update(destination.substring(workingRoot.length()+1), pl);
      IndexMaintainer ndx = getIndexForIg(indexes, npmB.id());
      if (ndx != null) {
        src.needFile(FileUtilities.changeFileExt(ndx.path(), ".json"));      
        ndx.loadJson();
        ndx.updateForPublication(pl, plVer, mode != PublicationProcessMode.WORKING);
        ndx.buildJson();
        ndx.execute();
      }
      updateRegistry(fRegistry, pl, plVer, npmB);
      System.out.println("Build is complete. "+src.verb()+" from "+ fRoot.getAbsolutePath());
    }
    new HistoryPageUpdater().updateHistoryPage(history.getAbsolutePath(), destination, templateSrc, !first); 
    
    logger.stop();
    FileUtils.copyFile(new File(logger.getFilename()), new File(Utilities.path(igBuildZipDir, npm.name()+"#"+npm.version()+".log")));
    src.finish(relDest, existingFiles);
    String anonuncement = buildAnnouncement(pubSetup, prSrc, npm, destination, destVer, pathVer, mode, url, relDest, version, tcName);
    FileUtilities.stringToFile(anonuncement, Utilities.path(igBuildZipDir, npm.name()+"#"+npm.version()+"-announcement.txt"));
    System.out.println("Finished Publishing. "+src.instructions(existingFiles.size()));
    exitCode = 0;
  }

  private String buildAnnouncement(JsonObject pubSetup, JsonObject prSrc, NpmPackage npm, String destination, String destVer, String pathVer, PublicationProcessMode mode, String url, String relDest, String version, String tcName) {
    String st = null;
    switch (prSrc.asString("status")) {
    case "release":
      st = "Release";
      break;
    case "trial-use":
      st = "STU";
      break;
    case "update":
      st = "Update";
      break;
    case "preview":
      st = "Preview";
      break;
    case "ballot":
      st = "Ballot";
      break;
    case "draft":
      st = "Draft";
      break;
    case "normative+trial-use":
      st = "Mixed Normative+STU";
      break;
    case "normative":
      st = "Normative";
      break;
    case "informative":
      st = "Informative";
      break;
    default:
      st = "Unknown";
      break;
    }
    
    StringBuilder b = new StringBuilder();
    switch (mode) {
    case CREATION: 
      b.append("New Publication Action: ");
      break;
    case MILESTONE:
      b.append("New "+st+" Publication Available: ");
      break;
    case TECHNICAL_CORRECTION:
      b.append("New Technical Correction: ");
      break;
    case WORKING:
      b.append("New "+st+" Version Available: ");
      break;
    default:
      b.append("New "+st+" Publication: ");
      break;
    }
    b.append(npm.title());
    b.append(": "+npm.name());
    b.append("#");
    b.append(npm.version());
    b.append(" @ ");
    b.append(pathVer);
    b.append("\r\n\r\n");
    b.append(prSrc.has("descmd") ? prSrc.asString("descmd") : prSrc.asString("descmd"));
        
    System.out.println("Announcement");
    System.out.println(b.toString());
    return b.toString();        
  }

  public void delTempFolder(File temp, String s) throws IOException {
    File gitFolder = new File(temp, s);
    if (gitFolder.exists()) {
      FileUtils.deleteDirectory(gitFolder);
    }
  }

  private List<String> loadSubPackageList(String path) throws JsonException, IOException {
    List<String> list = new ArrayList<>();
    File f = new File(path);
    if (f.exists()) {
      JsonArray json = (JsonArray) JsonParser.parse(f);
      list.addAll(json.asStrings());
    }
    return list;
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
        zip.addBytes(f.getAbsolutePath().substring(offset), FileUtilities.fileToBytes(f), false);
        c++;
      }
    }
    return c;
  }

  private String genDateS(Date genDate) {
    return new SimpleDateFormat("dd/MM/yyyy", new Locale("en", "US")).format(genDate);
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
      if (plVer.status().equals("withdrawn")) {
        reg.withdraw(rc, plVer);
      } if (plVer.status().equals("release") || plVer.status().equals("trial-use") || plVer.status().equals("update")) {
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
    return realm == null || ("uv".equals(realm) && Utilities.existsInList(code, "smart-app-launch", "extensions", "tools")) ? null : indexes.get(realm);
  }

  private void updateFeed(File fRoot, String destVer, PackageList pl, PackageListEntry plVer, String file, boolean isPublication, WebSourceProvider src, String orgName, NpmPackage npm, String genDate, String username, String version, String gitSrcId, List<String> otherPackages) throws IOException {
    if (!Utilities.noString(file)) {
      src.needFile(file);
      if (!updateFeedAsXml(Utilities.path(fRoot.getAbsolutePath(), file), pl, plVer, isPublication, orgName, npm, genDate, username, version, gitSrcId, otherPackages)) {
        String newContent = makeEntry(pl, plVer, isPublication, orgName, npm, genDate, username, version, gitSrcId);
        String rss = FileUtilities.fileToString(Utilities.path(fRoot.getAbsolutePath(), file));
        int i = rss.indexOf("<item");
        while (rss.charAt(i-1) == ' ') {
          i--;
        }
        rss = rss.substring(0, i) + newContent+rss.substring(i);
        FileUtilities.stringToFile(rss, Utilities.path(fRoot.getAbsolutePath(), file));
      }
    }
  }

  private boolean updateFeedAsXml(String file, PackageList pl, PackageListEntry plVer, boolean isPublication, String orgName, NpmPackage npm, String genDate, String username, String version, String gitSrcId, List<String> otherPackages) {
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
      if (!isPublication && otherPackages != null) {
        for (String p : otherPackages) {
          String pid = npm.name()+"."+p;
          XMLUtil.addTextTag(xml, nitem, "title", pid + "#" + plVer.version(), 6);
          XMLUtil.addTextTag(xml, nitem, "description", plVer.desc(), 6);
          String l = link.replace("package.tgz", pid+".tgz");
          XMLUtil.addTextTag(xml, nitem, "link", l, 6);
          XMLUtil.addTextTag(xml, nitem, "guid", l, 6).setAttribute("isPermaLink", "true");
          XMLUtil.addTextTag(xml, nitem, "dc:creator", orgName, 6);
          XMLUtil.addTextTag(xml, nitem, "fhir:version", VersionUtilities.versionFromCode(p), 6);
          XMLUtil.addTextTag(xml, nitem, "fhir:kind", npm.getNpm().asString("type"), 6);
          XMLUtil.addTextTag(xml, nitem, "pubDate", presentDate(npm.dateAsDate()), 6);
          XMLUtil.addTextTag(xml, nitem, "fhir:details", "Publication run at " + genDate + " by " + username + " using " + version + (gitSrcId != null ? " source id " + gitSrcId : ""), 6);
          nitem.appendChild(xml.createTextNode("\n    "));
          nitem = xml.createElement("item");
          channel.insertBefore(nitem, item);
          channel.insertBefore(xml.createTextNode("\n    "), item);
        }
      }
      XMLUtil.addTextTag(xml, nitem, "title", isPublication ? pl.title()+" version "+plVer.version() : pl.pid()+"#"+plVer.version(), 6);
      XMLUtil.addTextTag(xml, nitem, "description", plVer.desc(), 6);
      XMLUtil.addTextTag(xml, nitem, "link", link, 6);
      XMLUtil.addTextTag(xml, nitem, "guid", link, 6).setAttribute("isPermaLink", "true");
      XMLUtil.addTextTag(xml, nitem, "dc:creator", orgName, 6);
      XMLUtil.addTextTag(xml, nitem, "fhir:version", plVer.fhirVersion(), 6);
      XMLUtil.addTextTag(xml, nitem, "fhir:kind", npm.getNpm().asString("type"), 6);
      XMLUtil.addTextTag(xml, nitem, "pubDate", presentDate(npm.dateAsDate()), 6);
      XMLUtil.addTextTag(xml, nitem, "fhir:details", "Publication run at "+genDate+" by "+username+" using "+version+(gitSrcId != null ? " source id "+gitSrcId : ""), 6);
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

  private String makeEntry(PackageList pl, PackageListEntry plVer, boolean isPublication, String orgName, NpmPackage npm, String genDate, String username, String version, String gitSrcId) {
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
    b.append("      <fhir:details>Publication run at "+genDate+" by "+username+" using "+version+" source id "+gitSrcId+"</fhir:details>\r\n");
    b.append("    </item>\r\n");
    
    return b.toString();
  }
  
  private static final String RSS_DATE = "EEE, dd MMM yyyy hh:mm:ss Z";
  public String presentDate(Date date) {
    SimpleDateFormat sdf = new SimpleDateFormat(RSS_DATE, new Locale("en", "US"));// Wed, 04 Sep 2019 08:58:14 GMT      
    return sdf.format(date);
  }

  private void updatePublishBox(PackageList pl, PackageListEntry plVer, String destVer, String pathVer, String destination, String rootFolder, boolean current, ServerType serverType, File sft, List<String> ignoreList, String url, boolean jsonXmlClones) throws FileNotFoundException, IOException {
    IGReleaseVersionUpdater igvu = new IGReleaseVersionUpdater(destVer, url, rootFolder, ignoreList, null, plVer.json(), destination);
    String fragment = PublishBoxStatementGenerator.genFragment(pl, plVer, pl.current(), pl.canonical(), current, false);
    System.out.println("Publish Box Statement: "+fragment);
    igvu.updateStatement(fragment, current ? 0 : 1, pl.milestones());
    System.out.println("  .. "+igvu.getCountTotal()+" files checked, "+igvu.getCountUpdated()+" updated");

    if (jsonXmlClones) {
      igvu.checkXmlJsonClones(destVer);
      System.out.println("  .. "+igvu.getClonedTotal()+" clones checked, "+igvu.getClonedCount()+" updated");
    }
    if (serverType != null) {
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
      if (!current && serverType == ServerType.ASP2) {
        new VersionRedirectorGenerator(destination).execute(plVer.version(), plVer.path());
      }
    }
    new DownloadBuilder(destVer, pl.canonical(), current ?  pl.canonical() : plVer.path(), ignoreList).execute();

    
    if (sft != null) {
      String html = FileUtilities.fileToString(sft);
      html = fixParameter(html, "title", pl.title());
      html = fixParameter(html, "id", pl.pid());
      html = fixParameter(html, "version", current ? "All Versions" : plVer.version());
      html = fixParameter(html, "path", current ? pl.canonical() : plVer.path());
      html = fixParameter(html, "history", current ? "history.html" : "../history.html");
      html = fixParameter(html, "search-list", searchLinks(current, plVer, pl.canonical(), pl.list()));
      html = fixParameter(html, "note", current ? "this search searches all versions of the "+pl.title()+", including balloted versions. You can also search specific versions" :
        "this search searches version "+plVer.version()+" of the "+pl.title()+". You can also search other versions, or all versions at once");
      html = fixParameter(html, "prefix", "");            
      FileUtilities.stringToFile(html, Utilities.path(destVer, "searchform.html"));          
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
    String dest = Utilities.path(tempDir, "ig-builds", name);
    System.out.println("Prepare Build space in "+dest);        
    File fDest = new File(dest);
    if (!fDest.exists()) {
      FileUtilities.createDirectory(dest);
    } else {
      FileUtilities.clearDirectory(dest);
    }
    FileUtils.copyDirectory(fSource, fDest);
    return fDest;
  }
  
  private PackageListEntry updatePackageList(PackageList pl, String folder, JsonObject prSrc, String webpath, String filepath, PublicationProcessMode mode, String date, String fhirVersion, String tcPath, List<String> subPackages) throws Exception {
    if (date == null) {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      date = sdf.format(new Date());      
    }
    PackageListEntry tnv = null;
    
    if (mode == PublicationProcessMode.TECHNICAL_CORRECTION) {
      tnv = pl.current();
      tnv.setStatus("corrected");
    }
    if (mode != PublicationProcessMode.WORKING) {
      for (PackageListEntry e : pl.versions()) {
        e.setCurrent(false);
      }
    }
    PackageListEntry nv = pl.newVersion(prSrc.asString("version"), webpath,  prSrc.asString("status"), prSrc.asString("sequence"), FhirPublication.fromCode(fhirVersion));

    nv.setDate(date);
    if (mode != PublicationProcessMode.WORKING) {
      nv.setCurrent(true);
    }

    if (mode == PublicationProcessMode.TECHNICAL_CORRECTION) {
      nv.takeCorrections(tnv);
      nv.addTC(tnv.version(), tcPath, date);
    }

    
    String md = null;
    if (prSrc.has("descmd")) {
      md = prSrc.asString("descmd");
      if (md.startsWith("@")) {
        File mdFile = new File(Utilities.path(folder, md.substring(1)));
        if (!mdFile.exists()) {
          throw new Exception("descmd references the file "+md.substring(1)+" but it doesn't exist");
        }
        md = FileUtilities.fileToString(mdFile);
      }
    }    
    nv.describe(prSrc.asString("desc"), md, prSrc.asString("changes"));
    nv.clearSubPackages();
    if (subPackages != null) {
    for (String s : subPackages) {
      nv.addSubPackage(s);
    }
    }

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


  private List<ValidationMessage> processWithdrawal(String source, String web, String url, String date, Date dd, String registrySource, String history,
      String templateSrc, String temp, PublisherConsoleLogger logger, String[] args, String destination,
      String workingRoot, List<ValidationMessage> res, WebSourceProvider src, String id, JsonObject pubSetup,
      File fSource, File fRoot, File fRegistry, File fHistory, boolean jsonXmlClones, File igBuildZipDir,
      JsonObject prSrc, PublicationProcessMode mode) throws Exception {
    System.out.println("Relative directory for IG is '"+destination.substring(workingRoot.length())+"'");
    String relDest = FileUtilities.getRelativePath(workingRoot, destination);
    FileUtilities.createDirectory(destination);

    System.out.println("===== Web Publication Run Details ===============================");
    System.out.println(" Withdrawal: "+id+" from "+source);
    
    src.needOptionalFile(Utilities.noString(relDest) ? "package-list.json" : Utilities.path(relDest,"package-list.json"));

    String plpath = Utilities.path(destination, "package-list.json");
    if (!check(res, new File(plpath).exists(), "Destination '"+destination+"' does not contain a package-list.json - cannot proceed")) {
      return res;
    }
    PackageList pl = PackageList.fromFile(plpath);
    check(res, id.equals(pl.pid()), "Published Package List has the wrong package id: "+pl.pid()+" (should be "+id+")");

    PackageListEntry curr = pl.current();
    PackageListEntry last = pl.latest();
    String currentVersion = curr == null ? last.version() : curr.version();
    String version = VersionUtilities.versionWithoutLabels(currentVersion)+"-withdrawal";
    String sequence = curr == null ? last.sequence() : curr.sequence();
    String destVer = Utilities.path(destination, version);
    File destVerF = new File(destVer);

    src.needOptionalFolder(Utilities.path(relDest, version), false);
    
    check(res, pl.findByVersion(version) == null, "Expected withdrawal version '"+version+"' already exists");
    check(res, !prSrc.has("version") || version.equals(prSrc.asString("version")), "Publication Request specifies version '"+prSrc.asString("version")+"' but withdrawal version is '"+version+"'");
    check(res, !Utilities.noString(prSrc.asString("desc")), "Publication Request has no desc value");
    check(res, !Utilities.noString(prSrc.asString("descmd")), "Publication Request has no descmd value");
    check(res, !prSrc.has("sequence") || sequence.equals(prSrc.asString("sequence")), "Publication Request specifies sequence '"+prSrc.asString("sequence")+"' but withdrawal sequence is '"+sequence+"'");
    check(res, "withdrawn".equals(prSrc.asString("status")), "Publication Request status must be 'withdrawn' not '"+prSrc.asString("status")+"'");
    check(res, !destVerF.exists(), "There is already a folder at "+destVer);
    checkFile(Utilities.path(templateSrc, "withdrawal.template.html"), res, "Withdrawal Template");
    checkDirectory(Utilities.path(templateSrc, "withdrawal.assets"), res, "Withdrawal assets");
    if (!res.isEmpty()) {
      return res;
    }

    List<String> ignoreList = new ArrayList<>();
    ignoreList.add(destVer);
    // get the current content from the source
    for (PackageListEntry v : pl.versions()) {
      if (v != curr) {
        String path = v.determineLocalPath(url, fRoot.getAbsolutePath());
        if (path != null) {
          String relPath = FileUtilities.getRelativePath(fRoot.getAbsolutePath(), path);
          ignoreList.add(path);
          src.needFolder(relPath, false);
        }
      }
    }
    
    prSrc.set("sequence", sequence);
    prSrc.set("version", version);
    
    String fhirVer = curr == null ? last.fhirVersion() : curr.fhirVersion();
    String vurl = Utilities.pathURL(url, relDest, version);
    byte[] npm = makeWithdrawalPackage(id, version, pl.canonical(), vurl, prSrc.asString("desc"), fhirVer, dd);

    FileUtilities.createDirectory(destVer);
    FileUtilities.bytesToFile(npm, Utilities.path(destVer, "package.tgz"));

    String withdrawalPage = makeWithdrawalPage(pl, prSrc, templateSrc, version);
    
    if (curr != null) {
      curr.setStatus("withdrawn");
      for (File f : new File(destination).listFiles()) {
        if (f.getName().endsWith(".html")) {
          FileUtilities.stringToFile(withdrawalPage, f);
        }
      }
      throw new Error("not supported yet");
    } else {
      FileUtilities.copyDirectory(Utilities.path(templateSrc, "withdrawal.assets"), Utilities.path(destination), null);
    }
    FileUtilities.stringToFile(withdrawalPage, Utilities.path(destination, "index.html"));
    FileUtilities.copyDirectory(Utilities.path(templateSrc, "withdrawal.assets"), Utilities.path(destVer), null);
    FileUtilities.stringToFile(withdrawalPage, Utilities.path(destVer, "index.html"));
    
    PackageListEntry plVer = updatePackageList(pl, source, prSrc, vurl, plpath, mode, date, fhirVer, null, null);
    for (PackageListEntry v : pl.versions()) {
      if (!v.cibuild()) {
        String pv = v.path();
        String vCode = pv.substring(pv.lastIndexOf("/")+1);
        String dv = Utilities.path(destination, vCode);
        System.out.println("Update publish box for version "+v.version()+" @ "+v.path());
        updatePublishBox(pl, v, dv, pv, destination, fRoot.getAbsolutePath(), false, null, null, null, url, false);
      }
    }
    updatePublishBox(pl, plVer, destination, url, destination, fRoot.getAbsolutePath(), true, null, null, null, url, false);
    
    String username = getComputerName();

    NpmPackage npmB = NpmPackage.fromPackage(new FileInputStream(Utilities.path(destVer, "package.tgz")));
    updateFeed(fRoot, destVer, pl, plVer, pubSetup.forceObject("feeds").asString("package"), false, src, pubSetup.forceObject("website").asString("org"), npmB, genDateS(dd), username, version, null, null);
    updateFeed(fRoot, destVer, pl, plVer, pubSetup.forceObject("feeds").asString("publication"), true, src, pubSetup.forceObject("website").asString("org"), npmB, genDateS(dd), username, version, null, null);
    new PackageRegistryBuilder(workingRoot).update(destination.substring(workingRoot.length()+1), pl);
    new HistoryPageUpdater().updateHistoryPage(history, destination, templateSrc, false);

    Map<String, IndexMaintainer> indexes = new HashMap<>();
    if (pubSetup.has("indexes")) {
      JsonObject ndxs = pubSetup.getJsonObject("indexes");
      for (String realm : ndxs.getNames()) {
        JsonObject ndx = ndxs.getJsonObject(realm);
        indexes.put(realm, new IndexMaintainer(realm, ndx.asString("title"), ndx.asString("source"), Utilities.path(fRoot.getAbsolutePath(), ndx.asString("source")), Utilities.path(templateSrc, pubSetup.getJsonObject("website").asString("index-template"))));        
      }
    }
    IndexMaintainer ndx = getIndexForIg(indexes, npmB.id());
    if (ndx != null) {
      src.needFile(FileUtilities.changeFileExt(ndx.path(), ".json"));      
      ndx.loadJson();
      ndx.updateForPublication(pl, plVer, mode != PublicationProcessMode.WORKING);
      ndx.buildJson();
      ndx.execute();
    }
    updateRegistry(fRegistry, pl, plVer, npmB);
    System.out.println("Build is complete. "+src.verb()+" from "+ fRoot.getAbsolutePath());
    
    logger.stop();
    FileUtils.copyFile(new File(logger.getFilename()), new File(Utilities.path(igBuildZipDir, npmB.name()+"#"+npmB.version()+".log")));
    src.finish(relDest, null);
    String anonuncement = buildAnnouncement(pubSetup, prSrc, npmB, destination, destVer, vurl, mode, url, relDest, version, null);
    FileUtilities.stringToFile(anonuncement, Utilities.path(igBuildZipDir, npmB.name()+"#"+npmB.version()+"-announcement.txt"));
    System.out.println("Finished Publishing. "+src.instructions(0));
    exitCode = 0;
    return res;
  }

  private String makeWithdrawalPage(PackageList pl, JsonObject prSrc, String templateSrc, String version) throws FileNotFoundException, IOException {
    String tcTemplate = FileUtilities.fileToString(Utilities.path(templateSrc, "withdrawal.template.html"));
    String cnt = tcTemplate.replace("[%title%]", pl.title());
    cnt = cnt.replace("[%ver%]", version);
    cnt = cnt.replace("[%id%]", pl.pid());
    if (prSrc.has("replacement")) {
      cnt = cnt.replace("[%repl-link%]", "<p>This specification is replaced by <a href=\""+prSrc.asString("replacement")+"\">a new specification</a></p>");
    } else {
      cnt = cnt.replace("[%repl-link%]", "");
    }
    if (prSrc.has("descmd")) {
      MarkDownProcessor md = new MarkDownProcessor(Dialect.COMMON_MARK);
      String d = md.process(prSrc.asString("descmd"), "publication-request.json#descmd");
      cnt = cnt.replace("[%desc%]", d);      
      
    } else {
      cnt = cnt.replace("[%desc%]", "<p>"+Utilities.escapeXml(prSrc.asString("desc"))+"</a>");      
    }
    return cnt;
  }


  private byte[] makeWithdrawalPackage(String id, String version, String canonical, String url, String desc, String fhirVer, Date date) throws IOException {
    String path = Utilities.path("[tmp]", "withdrawal.tgz");
    NPMPackageGenerator gen = new NPMPackageGenerator(id, path, canonical, url, PackageType.IG, new ImplementationGuide(), date, null, false, fhirVer);
    gen.getPackageJ().add("description", desc);
    gen.finish();
    return FileUtilities.fileToBytes(path);
 
  }
/**
 * ok updated list for withdrawal:

create a new entry in the package-list.json file saying that the previous publication has been withdrawn
replaces all the html files in the root copy of the specification with a redirect to withdrawal-notice.html
withdrawal-notice.html says why it's been withdrawn, and maybe directs users to an alternate specification
all the publish boxes get updated to note that the spec is withdrawn, with a reference to the withdrawl-notice.html file
publish an empty package.tgz file with an IG in it that has updated status and description / title etc saying withdrawn and no other contents
the version of the package is (patch+1)-withdrawn e.g. 1.0.2 will be replaced by 1.0.3-withdrawn
 */
}
