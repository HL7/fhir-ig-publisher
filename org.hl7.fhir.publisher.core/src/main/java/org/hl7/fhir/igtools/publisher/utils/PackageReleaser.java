package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.hl7.fhir.convertors.factory.VersionConvertorFactory_10_30;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_10_40;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_14_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_40;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.dstu2.model.StructureDefinition;
import org.hl7.fhir.dstu3.formats.IParser.OutputStyle;
import org.hl7.fhir.dstu3.model.ImplementationGuide;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.NpmPackage.ITransformingLoader;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;
import org.hl7.fhir.utilities.xml.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class PackageReleaser {
  
  public class PackageReleaserScrubber implements ITransformingLoader {

    private String ver;

    public PackageReleaserScrubber(String ver) {
      this.ver = ver; 
    }

    @Override
    public byte[] load(File f) {
      try {
        byte[] cnt = FileUtilities.fileToBytes(f);
        if (!f.getName().endsWith(".json")) {
          return cnt;
        }
        JsonObject json = JsonParser.parseObject(f);
        byte[] cntp = JsonParser.composeBytes(json, true);
        if (cntp != cnt) {
          FileUtilities.bytesToFile(cntp, f);
        }
        cnt = JsonParser.composeBytes(json, false);
        if (json.has("resourceType")) {
          //  check that it's valid
          if (VersionUtilities.isR2Ver(ver)) {
            new org.hl7.fhir.dstu2.formats.JsonParser().parse(cnt);
          } else if (VersionUtilities.isR2BVer(ver)) {
            new org.hl7.fhir.dstu2016may.formats.JsonParser().parse(cnt);
          } else if (VersionUtilities.isR3Ver(ver)) {
            new org.hl7.fhir.dstu3.formats.JsonParser().parse(cnt);
          } else if (VersionUtilities.isR4Ver(ver)) {
            new org.hl7.fhir.r4.formats.JsonParser().parse(cnt);
          } else if (VersionUtilities.isR5Plus(ver)) {
            new org.hl7.fhir.r5.formats.JsonParser().parse(cnt);
          } 
        }
        return cnt;
      } catch (Exception e) {
        throw new Error("Exception processing "+f.getAbsolutePath()+": "+e.getMessage(), e);
      }
      
    }

  }

  public enum VersionChangeType {
    NONE, PATCH, MINOR, MAJOR;

    public boolean lessThan(VersionChangeType t) {
      switch (this) {
      case MAJOR: return false;
      case MINOR: return (t == MAJOR);
      case NONE: return t != NONE; 
      case PATCH: return (t == MAJOR || t == MINOR);
      }
      return false;
    }
  }

  public class VersionDecision {
    private String id;
    private String currentVersion;
    private String newVersion;
    private VersionChangeType type;
    private boolean explicit;
    private String implicitSource;
    private String releaseNote;
    private Boolean checked = null;
    private boolean built;
    public String getId() {
      return id;
    }
    public void setId(String id) {
      this.id = id;
    }


    public String getCurrentVersion() {
      return currentVersion;
    }
    public void setCurrentVersion(String currentVersion) {
      this.currentVersion = currentVersion;
    }
    public String getNewVersion() {
      return newVersion;
    }
    public void setNewVersion(String newVersion) {
      this.newVersion = newVersion;
    }
    public VersionChangeType getType() {
      return type;
    }
    public void setType(VersionChangeType type) {
      this.type = type;
    }
    public boolean isExplicit() {
      return explicit;
    }
    public void setExplicit(boolean explicit) {
      this.explicit = explicit;
    }
    public String summary() {
      if (type == VersionChangeType.NONE) {
        return id+"#"+currentVersion+" (no change)";        
      } else {
        return id+"#"+currentVersion+" ->: "+newVersion+" "+(explicit ? "" : "(implied by "+implicitSource+") ");
      }
    }
  }

  private static final String RSS_DATE = "EEE, dd MMM yyyy hh:mm:ss Z";

  private Document rss;
  private Element channel;
  private String linkRoot;
  private org.hl7.fhir.r4.context.SimpleWorkerContext r4;
  private org.hl7.fhir.dstu3.support.context.SimpleWorkerContext r3;


  private File xml;
  private IniFile config;

  private FilesystemPackageCacheManager pcm;


  // 2 parameters: source of package, package dest folder
  public static void main(String[] args) throws Exception {
    try {
      if (args.length == 4) {        
        new PackageReleaser().pack(args[2], args[3]);
        new PackageReleaser().release(args[0], args[1]);
      } else {
        new PackageReleaser().release(args[0], args[1]);
      }
    } catch (Throwable e) {
      System.out.println("Error releasing packages from "+args[0]+" to "+args[1]+":");
      System.out.println(e.getMessage());
      System.out.println("");
      e.printStackTrace();
    }
  }

  private void pack(String src, String dst) throws IOException {
    JsonObject j = JsonParser.parseObject(new File(Utilities.path(src, "packages.json")));
    for (String s : j.getStrings("packages")) {
      packPackage(Utilities.path(src, s), dst);
    }
    System.out.println("Finished Building Packages");    
  }

  private void packPackage(String path, String dst) throws IOException {
    String name = new File(path).getName();
    System.out.println("Build "+name);
    NpmPackage npm = NpmPackage.fromFolder(path);
    npm.loadAllFiles();
    npm.save(new FileOutputStream(Utilities.path(dst, name+".tgz")));
  }

  private void release(String source, String dest) throws Exception {
    pcm = new FilesystemPackageCacheManager.Builder().build();
    System.out.println("Load hl7.fhir.r4.core");
    r4 = org.hl7.fhir.r4.context.SimpleWorkerContext.fromPackage(pcm.loadPackage("hl7.fhir.r4.core", "4.0.1"));
    System.out.println("Load hl7.fhir.r3.core");
    r3 = org.hl7.fhir.dstu3.support.context.SimpleWorkerContext.fromPackage(pcm.loadPackage("hl7.fhir.r3.core", "3.0.2"));
    System.out.println("Scanning "+source+" and comaparing to "+dest);

    SimpleDateFormat df = new SimpleDateFormat(RSS_DATE, new Locale("en", "US"));
    checkDest(dest);
    Map<String, String> currentPublishedVersions = scanForCurrentVersions(dest);
    System.out.println("Current Published Versions");
    for (String s : currentPublishedVersions.keySet()) {
      System.out.println(" * "+s+"#"+currentPublishedVersions.get(s));
    }
    List<VersionDecision> versionsList = analyseVersions(source, scanForCurrentVersions(source), currentPublishedVersions);
    System.out.println("Actions to take");
    for (VersionDecision vd : versionsList) {
      System.out.println(" * "+vd.summary());
    }
    System.out.println("Do you want to continue [y/n]");
    int r = System.in.read();
    if (r == 'y') {
      config = new IniFile(Utilities.path(source, "config.ini"));
      
      // now: for any implicit upgrades, set up the package-list.json
      System.out.println("Updating Package Lists");
      for (VersionDecision vd : versionsList) {
        if (!vd.explicit && vd.type != VersionChangeType.NONE) {
          updatePackageList(source, vd);
        }
      }
      SimpleDateFormat dfd = new SimpleDateFormat("yyyy-MM-dd", new Locale("en", "US"));
      String dateFmt = dfd.format(new Date());
      for (VersionDecision vd : versionsList) {
        if (vd.type != VersionChangeType.NONE) {
          updateDate(source, vd, dateFmt);
        }
      }
      System.out.println("Updating Packages");
      for (VersionDecision vd : versionsList) {
        updateVersions(source, vd, versionsList);
      }
      System.out.println("Building Packages");
      for (VersionDecision vd : versionsList) {
        if (!vd.built && vd.type != VersionChangeType.NONE) {
          build(source, vd, versionsList);
        }
      }
      System.out.println("Finished Building IGs");
//       if (true) { throw new Error("bang!"); }
      
        System.out.println("Releasing Packages");
        for (VersionDecision vd : versionsList) {
          if (vd.type != VersionChangeType.NONE) {
            release(dest, source, vd, df);
          }
        }
        updatePackagesList(dest, versionsList);

        System.out.println("Reset Packages");
        for (VersionDecision vd : versionsList) {
          resetVersions(source, vd, versionsList);
        }
        Element lbd = XMLUtil.getNamedChild(channel, "lastBuildDate");
        lbd.setTextContent(df.format(new Date()));
        File bak = new File(FileUtilities.changeFileExt(xml.getAbsolutePath(),  ".bak"));
        if (bak.exists())
          bak.delete();
        xml.renameTo(bak);
        saveXml(new FileOutputStream(xml));  
        System.out.println("Published");
    }
  }

  private void updateIndex(File file, PackageList pl) throws FileNotFoundException, IOException {
    StringBuilder b = new StringBuilder();
    
    for (PackageListEntry v : pl.list()) { 
      String ver = v.version();
      String desc = v.desc();
      String date = v.date();
      b.append("<li><a href=\""+ver+"/package.tgz\">"+ver+"</a>: "+Utilities.escapeJson(desc)+" ("+date+")</li>\r\n");
    }    
    String source = FileUtilities.fileToString(file);
    int i = source.indexOf("id=\"releases-list\">");
    if (i < 0) {
      throw new Error("Version insertion point Not found in "+file.getAbsolutePath());      
    }
    String pfx = source.substring(0, i+21);
    source = source.substring(i+21);
    i = source.indexOf("</ul>");
    source = source.substring(i);
    source = pfx + b.toString()+source;
    FileUtilities.stringToFile(source, file);
  }
  
  
  private void updatePackagesList(String dest, List<VersionDecision> versionsList) throws IOException {
    boolean save = false;
    JsonObject pl = JsonParser.parseObject(new File(Utilities.path(dest, "package-list.json")));
    for (VersionDecision vd : versionsList) {
      if (!pl.getJsonObject("packages").has(vd.getId())) {
        pl.getJsonObject("packages").add(vd.getId(), Utilities.pathURL("http://fhir.org/packages/", vd.getId()));
        save = true;
      }
    }    
    if (save) {
      JsonParser.compose(pl, new FileOutputStream(Utilities.path(dest, "package-list.json")));
    }
  }

  private void build(String source, VersionDecision vd, List<VersionDecision> versions) throws Exception {
    List<String> dependendencies = listDependencies(source, vd.getId());
    for (String s : dependendencies) {
      VersionDecision v = findVersion(versions, s);
      if (v != null && !v.built) {
        build(source, v, versions);
      }
    }
    makePackage(Utilities.path(source, vd.getId()), vd.getId(), vd.getNewVersion());
  }

  private void makePackage(String path, String id, String version) throws IOException {
    if (id.contains("dicom")) {
      String src = Utilities.path(path, version, "package.tgz");
      String dst = Utilities.path(path, "output", "package.tgz");
      FileUtilities.createDirectory(Utilities.path(path, "output"));
      FileUtilities.copyFile(src,  dst);
      return;
    }
    try {
      if (config.hasSection(id)) {
        String v = config.getStringProperty(id, "version");
        makeResources(config.getStringProperty(id, "r2-source"), "1.0", v, path);
        makeResources(config.getStringProperty(id, "r2b-source"), "1.4", v, path);
        makeResources(config.getStringProperty(id, "r3-source"), "3.0", v, path);
        makeResources(config.getStringProperty(id, "r4-source"), "4.0", v, path);
        makeR2Structures(path, v);
        makeR3Structures(path, v);
        makeR4Structures(path, v);
      }
      // now, actully make the tgz file
      FileUtilities.clearDirectory(Utilities.path(path, "output"));
      new File(Utilities.path(path, "output")).delete();

      NpmPackage npm = NpmPackage.fromFolder(path);
      npm.loadAllFiles(makeLoader(npm.fhirVersion()));
      FileUtilities.createDirectory(Utilities.path(path, "output"));
      npm.save(new FileOutputStream(Utilities.path(path, "output", "package.tgz")));
    } catch (Throwable e) {
      throw new IOException("Error processing "+path+": "+e.getMessage(), e);
    }
  }

  private ITransformingLoader makeLoader(String ver) {
    return new PackageReleaserScrubber(ver);
  }

  private void makeR2Structures(String path, String outVer) throws FHIRException, IOException {
    NpmPackage npm = pcm.loadPackage("hl7.fhir.r2.core", null);
    for (String id : npm.listResources("StructureDefinition")) {
      StructureDefinition sd = (StructureDefinition) new org.hl7.fhir.dstu2.formats.JsonParser().parse(npm.loadResource(id));
      sd.setId("r2-"+sd.getId());
      sd.setUrl(sd.getUrl().substring(0, 20)+"2.0/"+sd.getUrl().substring(20));
      String filename = Utilities.path(path, "package", sd.fhirType()+"-"+sd.getId()+".json");
      if ("1.0".equals(outVer)) {
        new org.hl7.fhir.dstu2.formats.JsonParser().compose(new FileOutputStream(filename), sd);
      } else if ("3.0".equals(outVer)) {
        org.hl7.fhir.dstu3.model.Resource r3 = VersionConvertorFactory_10_30.convertResource(sd);
        new org.hl7.fhir.dstu3.formats.JsonParser().compose(new FileOutputStream(filename), r3);
      } else if ("4.0".equals(outVer)) {
        org.hl7.fhir.r4.model.Resource r4 = VersionConvertorFactory_10_40.convertResource(sd);
        new org.hl7.fhir.r4.formats.JsonParser().compose(new FileOutputStream(filename), r4);
      } else {
        throw new Error("Unknown version "+outVer);
      }      
    }
  }

  private void makeR3Structures(String path, String outVer) throws FHIRException, IOException {
    NpmPackage npm = pcm.loadPackage("hl7.fhir.r3.core", null);
    for (String id : npm.listResources("StructureDefinition")) {
      org.hl7.fhir.dstu3.model.StructureDefinition sd = (org.hl7.fhir.dstu3.model.StructureDefinition) new org.hl7.fhir.dstu3.formats.JsonParser().parse(npm.loadResource(id));
      sd.setId("r3-"+sd.getId());
      sd.setUrl(sd.getUrl().substring(0, 20)+"3.0/"+sd.getUrl().substring(20));
      String filename = Utilities.path(path, "package", sd.fhirType()+"-"+sd.getId()+".json");
      if ("1.0".equals(outVer)) {
        org.hl7.fhir.dstu2.model.Resource r2 = VersionConvertorFactory_10_30.convertResource(sd);
        new org.hl7.fhir.dstu2.formats.JsonParser().compose(new FileOutputStream(filename), r2);
      } else if ("3.0".equals(outVer)) {
        new org.hl7.fhir.dstu3.formats.JsonParser().compose(new FileOutputStream(filename), sd);
      } else if ("4.0".equals(outVer)) {
        org.hl7.fhir.r4.model.Resource r4 = VersionConvertorFactory_30_40.convertResource(sd);
        new org.hl7.fhir.r4.formats.JsonParser().compose(new FileOutputStream(filename), r4);
      } else {
        throw new Error("Unknown version "+outVer);
      }      
    }
  }

  private void makeR4Structures(String path, String outVer) throws FHIRException, IOException {
    NpmPackage npm = pcm.loadPackage("hl7.fhir.r4.core", null);
    for (String id : npm.listResources("StructureDefinition")) {
      org.hl7.fhir.r4.model.StructureDefinition sd = (org.hl7.fhir.r4.model.StructureDefinition) new org.hl7.fhir.r4.formats.JsonParser().parse(npm.loadResource(id));
      sd.setId("r4-"+sd.getId());
      sd.setUrl(sd.getUrl().substring(0, 20)+"4.0/"+sd.getUrl().substring(20));
      String filename = Utilities.path(path, "package", sd.fhirType()+"-"+sd.getId()+".json");
      if ("1.0".equals(outVer)) {
        org.hl7.fhir.dstu2.model.Resource r2 = VersionConvertorFactory_10_40.convertResource(sd);
        new org.hl7.fhir.dstu2.formats.JsonParser().compose(new FileOutputStream(filename), r2);
      } else if ("3.0".equals(outVer)) {
        org.hl7.fhir.dstu3.model.Resource r3 = VersionConvertorFactory_30_40.convertResource(sd);
        new org.hl7.fhir.dstu3.formats.JsonParser().compose(new FileOutputStream(filename), r3);
      } else if ("4.0".equals(outVer)) {
        new org.hl7.fhir.r4.formats.JsonParser().compose(new FileOutputStream(filename), sd);
      } else {
        throw new Error("Unknown version "+outVer);
      }      
    }
  }

  private void makeResources(String src, String inVer, String outVer, String dest) throws IOException, Error {
    if (src != null) {
      String[] srcs = src.split("\\,");
      for (String s : srcs) {
        File srcF = new File(Utilities.path(dest, s));
        makeResources(srcF, inVer, outVer, dest);
      }
    }
  }

  private void makeResources(File srcF, String inVer, String outVer, String dest) throws FHIRFormatError, FileNotFoundException, IOException {
    for (File f : srcF.listFiles()) {
      if (f.isDirectory()) {
        makeResources(f,  inVer, outVer, dest);
      } else if (Utilities.existsInList(Utilities.getFileExtension(f.getName()), "map", "xml", "json")) {
        try {
          Resource r = parseResource(f, inVer);
          String filename = Utilities.path(dest, "package", r.fhirType()+"-"+r.getId()+".json");
          if ("1.4".equals(outVer)) {
            org.hl7.fhir.dstu2016may.model.Resource r2b = VersionConvertorFactory_14_50.convertResource(r);
            new org.hl7.fhir.dstu2016may.formats.JsonParser().compose(new FileOutputStream(filename), r2b);
          } else if ("3.0".equals(outVer)) {
            org.hl7.fhir.dstu3.model.Resource r3 = VersionConvertorFactory_30_50.convertResource(r);
            new org.hl7.fhir.dstu3.formats.JsonParser().compose(new FileOutputStream(filename), r3);
          } else if ("4.0".equals(outVer)) {
            org.hl7.fhir.r4.model.Resource r4 = VersionConvertorFactory_40_50.convertResource(r);
            new org.hl7.fhir.r4.formats.JsonParser().compose(new FileOutputStream(filename), r4);
          } else {
            throw new Error("Unknown version "+outVer);
          }
        } catch (Exception e) {
          throw new FHIRException("Exception processing "+f.getName()+": "+e.getMessage(), e);
        }
      }
    }
  }

  private Resource parseResource(File f, String ver) throws FHIRFormatError, FileNotFoundException, IOException {
    if ("1.4".equals(ver)) {
      org.hl7.fhir.dstu2016may.model.Resource r;
      if (f.getName().endsWith(".xml")) {
        r = new org.hl7.fhir.dstu2016may.formats.XmlParser().parse(new FileInputStream(f));
      } else { // if (f.getName().endsWith(".json")) {
        r = new org.hl7.fhir.dstu2016may.formats.JsonParser().parse(new FileInputStream(f));
      }
      return VersionConvertorFactory_14_50.convertResource(r);
    } else if ("3.0".equals(ver)) {
      org.hl7.fhir.dstu3.model.Resource r;
      if (f.getName().endsWith(".xml")) {
        r = new org.hl7.fhir.dstu3.formats.XmlParser().parse(new FileInputStream(f));
      } else { // if (f.getName().endsWith(".json")) {
        r = new org.hl7.fhir.dstu3.formats.JsonParser().parse(new FileInputStream(f));
      }
      return VersionConvertorFactory_30_50.convertResource(r);
    } else if ("4.0".equals(ver)) {
      org.hl7.fhir.r4.model.Resource r;
      if (f.getName().endsWith(".map")) {
        r = new org.hl7.fhir.r4.utils.StructureMapUtilities(r4).parse(FileUtilities.fileToString(f), f.getName());
      } else if (f.getName().endsWith(".xml")) {
        r = new org.hl7.fhir.r4.formats.XmlParser().parse(new FileInputStream(f));
      } else { // if (f.getName().endsWith(".json")) {
        r = new org.hl7.fhir.r4.formats.JsonParser().parse(new FileInputStream(f));
      }
      return VersionConvertorFactory_40_50.convertResource(r);
    } else {
      throw new Error("Unknown version "+ver);
    }
  }

  private void updateVersions(String source, VersionDecision vd, List<VersionDecision> versionsList) throws FileNotFoundException, IOException {
    JsonObject npm = JsonParser.parseObject(new FileInputStream(Utilities.path(source, vd.getId(), "package", "package.json")));
    npm.remove("version");
    npm.add("version", vd.getNewVersion());
    if (npm.has("dependencies")) {
      JsonObject d = npm.getJsonObject("dependencies");
      List<String> deps = new ArrayList<>();
      for (String e : d.getNames()) {
        deps.add(e);
      }
      for (String s : deps) {
        VersionDecision nver = findVersion(versionsList, s);
        if (nver != null) {
          d.remove(s);
          d.add(s, nver.newVersion);
        }
      }
    }
    String version = npm.getJsonArray("fhirVersions").get(0).asString();
    String jcnt = JsonParser.compose(npm, true);
    FileUtilities.stringToFile(jcnt, Utilities.path(source, vd.getId(), "package", "package.json"));
    switch (VersionUtilities.getMajMin(version)) {
      case "1.0":
        throw new Error("R2 is no longer supported processing "+Utilities.path(source, vd.getId()));
      case "3.0" : 
        org.hl7.fhir.dstu3.model.ImplementationGuide ig3 = (ImplementationGuide) new org.hl7.fhir.dstu3.formats.JsonParser().parse(new FileInputStream(Utilities.path(source, vd.getId(), "package", "ImplementationGuide-"+vd.getId()+".json")));
        ig3.setVersion(vd.getNewVersion());
        new org.hl7.fhir.dstu3.formats.JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream(Utilities.path(source, vd.getId(), "package", "ImplementationGuide-"+vd.getId()+".json")), ig3);
        break;
      case "4.0" : 
      case "4.3" : 
        org.hl7.fhir.r4.model.ImplementationGuide ig4 = (org.hl7.fhir.r4.model.ImplementationGuide) new org.hl7.fhir.r4.formats.JsonParser().parse(new FileInputStream(Utilities.path(source, vd.getId(), "package", "ImplementationGuide-"+vd.getId()+".json")));
        ig4.setVersion(vd.getNewVersion());
        new org.hl7.fhir.r4.formats.JsonParser().setOutputStyle(org.hl7.fhir.r4.formats.IParser.OutputStyle.PRETTY).compose(new FileOutputStream(Utilities.path(source, vd.getId(), "package", "ImplementationGuide-"+vd.getId()+".json")), ig4);
        break;
      default: 
        org.hl7.fhir.r5.model.ImplementationGuide ig5 = (org.hl7.fhir.r5.model.ImplementationGuide) new org.hl7.fhir.r5.formats.JsonParser().parse(new FileInputStream(Utilities.path(source, vd.getId(), "package", "ImplementationGuide-"+vd.getId()+".json")));
        ig5.setVersion(vd.getNewVersion());
        new org.hl7.fhir.r5.formats.JsonParser().setOutputStyle(org.hl7.fhir.r5.formats.IParser.OutputStyle.PRETTY).compose(new FileOutputStream(Utilities.path(source, vd.getId(), "package", "ImplementationGuide-"+vd.getId()+".json")), ig5);
        break;        
    }
  }

  private void resetVersions(String source, VersionDecision vd, List<VersionDecision> versionsList) throws FileNotFoundException, IOException {
    JsonObject npm = JsonParser.parseObject(new FileInputStream(Utilities.path(source, vd.getId(), "package", "package.json")));
    if (npm.has("dependencies")) {
      JsonObject d = npm.getJsonObject("dependencies");
      List<String> deps = new ArrayList<>();
      for (String e : d.getNames()) {
        deps.add(e);
      }
      for (String s : deps) {
        d.remove(s);
        d.add(s, VersionUtilities.getCurrentVersion(npm.getStrings("fhirVersions").get(0)));
      }
      String jcnt = JsonParser.compose(npm, true);
      FileUtilities.stringToFile(jcnt, Utilities.path(source, vd.getId(), "package", "package.json"));
    }
  }

  private void updateDate(String source, VersionDecision vd, String dateFmt) throws FileNotFoundException, IOException {
    PackageList pl = PackageList.fromFile(new File(Utilities.path(source, vd.getId(), "package-list.json")));
    boolean ok = false;
    for (PackageListEntry v : pl.list()) {
      if (v.version().equals(vd.getNewVersion())) {
        v.setDate(dateFmt);
        ok = true;
        vd.releaseNote = v.desc();
      }
    }
    if (!ok) {
      throw new Error("unable to find version "+vd.getNewVersion()+" in pacjage list");
    }
    FileUtilities.stringToFile(pl.toJson(), Utilities.path(source, vd.getId(), "package-list.json"));
  }

  private void updatePackageList(String source, VersionDecision vd) throws FileNotFoundException, IOException {
    PackageList pl =PackageList.fromFile(new File(Utilities.path(source, vd.getId(), "package-list.json")));
    for (PackageListEntry e : pl.versions()) {
      e.setCurrent(false);
    }
    PackageListEntry e = pl.newVersion(vd.newVersion, Utilities.pathURL(pl.canonical(), vd.newVersion), "release", "Publications", FhirPublication.R4);
    e.describe("Upgrade for dependency on "+vd.implicitSource, null, null);
    e.setCurrent(true);
    e.setDate("XXXX-XX-XX");
    FileUtilities.stringToFile(pl.toJson(), Utilities.path(source, vd.getId(), "package-list.json"));
  }

  private List<VersionDecision> analyseVersions(String source, Map<String, String> newList, Map<String, String> oldList) throws Exception {
    List<VersionDecision> res = new ArrayList<PackageReleaser.VersionDecision>();
    for (String s : oldList.keySet()) {
      if (!newList.containsKey(s)) {
        throw new Exception("Existing template "+s+" not found in release set ("+newList.keySet()+")");
      }
      VersionDecision vd = new VersionDecision();
      res.add(vd);
      vd.setId(s);
      String v = oldList.get(s);
      String nv = newList.get(s);
      vd.setCurrentVersion(v);
      vd.setType(checkVersionChangeType(v, nv));
      vd.setExplicit(vd.getType() != VersionChangeType.NONE);
      if (vd.isExplicit()) {
        vd.setNewVersion(nv);
      }
    }
    for (String s : newList.keySet()) {
      if (!oldList.containsKey(s)) {
        VersionDecision vd = new VersionDecision();
        res.add(vd);
        vd.setId(s);
        String nv = newList.get(s);
        vd.setCurrentVersion(null);
        vd.setType(VersionChangeType.MAJOR);
        vd.setExplicit(true);
        if (vd.isExplicit()) {
          vd.setNewVersion(nv);
        }
      }
    }
    // ok, now check for implied upgrades
    for (VersionDecision vd : res) {
      if (vd.checked == null) {
        checkDependencies(source, vd, res);
      }
    }
    // now execute implied upgrades
    for (VersionDecision vd : res) {
      if (!vd.isExplicit()) {
        vd.setNewVersion(getNewVersion(vd.getType(), vd.getCurrentVersion()));
      }
    }
    boolean any = false;
    for (VersionDecision vd : res) {
      any = any || vd.type != VersionChangeType.NONE;
    }
    if (!any) {
      throw new Exception("Nothing found to release - Cannot Proceed");
    }
    
    return res;
  }

  private String getNewVersion(VersionChangeType t, String v) {
    switch (t) {
    case MAJOR: return VersionUtilities.incMajorVersion(v);
    case MINOR: return VersionUtilities.incMinorVersion(v);
    case PATCH: return VersionUtilities.incPatchVersion(v);
    }
    return v;
  }
  
  private void checkDependencies(String source, VersionDecision vd, List<VersionDecision> versions) throws Exception {
    vd.checked = false;
    List<String> dependendencies = listDependencies(source, vd.getId());
    for (String s : dependendencies) {
      VersionDecision v = findVersion(versions, s);
      if (v != null) {
        if (v.checked == null) {
          checkDependencies(source, v, versions);
        } else if (!v.checked) {
          throw new Exception("Circular dependency");
        }
        VersionChangeType t = v.type;
        if (vd.isExplicit()) {
          if (vd.type.lessThan(t)) {
            throw new Exception("invalid operation");
          }
        } else {
          if (vd.type.lessThan(t)) {
            vd.type = t;
            if (v.explicit) {
              vd.implicitSource = v.id;
            } else {
              vd.implicitSource = v.implicitSource;
            }
          }
        }
      }
    }
    vd.checked = true;
  }

  private VersionDecision findVersion(List<VersionDecision> versions, String s) {
    for (VersionDecision v : versions) {
      if (v.id.equals(s)) {
        return v;
      }
    }
    return null;
  }

  private List<String> listDependencies(String source, String id) throws Exception {
    JsonObject npm = JsonParser.parseObject(FileUtilities.fileToString(Utilities.path(source, id, "package", "package.json")));
    List<String> res = new ArrayList<String>();
    if (npm.has("dependencies")) {
      for (String s : npm.getJsonObject("dependencies").getNames()) {
//        if (!"current".equals(s.getValue().getAsString())) {
//          throw new Exception("Dependency is not 'current'");
//        }
        res.add(s);
      }
    }
    return res;
  }

  private VersionChangeType checkVersionChangeType(String v, String nv) {
    if (v.equals(nv)) { 
      return VersionChangeType.NONE;
    } else if (VersionUtilities.versionMatches(v, nv)) {
      return VersionChangeType.PATCH;
    } else if (v.charAt(0) == nv.charAt(0)) {
      return VersionChangeType.MINOR;
    } else {
      return VersionChangeType.MAJOR;
    }
  }

  private Map<String, String> scanForCurrentVersions(String folder) throws IOException {
    Map<String, String> res = new HashMap<String, String>();
    scanForCurrentVersions(res, new File(folder));
    return res;
  }

  private void scanForCurrentVersions(Map<String, String> res, File folder) throws IOException {
    for (File f : folder.listFiles()) {
      if (f.isDirectory() && !f.getName().contains(".r2")) {
        scanForCurrentVersions(res, f);
      } else if (f.getName().equals("package-list.json")) {
        PackageList pl = PackageList.fromFile(f);
        for (PackageListEntry v : pl.list()) {
          if ("release".equals(v.status()) && v.current()) {
            res.put(pl.pid(), v.version());
          }
        }
      }
    }
  }

  private void checkDest(String dest) throws Exception {
    File f = new File(dest);
    check(f.exists(), "Destination "+dest+" not found");
    check(f.isDirectory(), "Source "+dest+" is not a directoyy");
    xml = new File(Utilities.path(dest, "package-feed.xml"));
    check(xml.exists(), "Destination rss "+xml.getAbsolutePath()+" not found");
    rss = loadXml(xml);
    channel = XMLUtil.getNamedChild(rss.getDocumentElement(), "channel");
    check(channel != null, "Destination rss "+xml.getAbsolutePath()+" channel not found");
    check("HL7 FHIR Publication tooling".equals(XMLUtil.getNamedChildText(channel, "generator")), "Destination rss "+xml.getAbsolutePath()+" channel.generator not correct");
    String link = XMLUtil.getNamedChildText(channel, "link");
    check(link != null, "Destination rss "+xml.getAbsolutePath()+" channel.link not found");
    linkRoot = link.substring(0, link.lastIndexOf("/"));
  }

  private NpmPackage checkPackage(String source, String folder) throws IOException {
    File f = new File(Utilities.path(source, folder));
    check(f.exists(), "Source "+source+" not found");
    check(f.isDirectory(), "Source "+source+"\\"+folder+" is not a directory");
    File p = new File(Utilities.path(source, folder, "output", "package.tgz"));
    check(p.exists(), "Source Package "+p.getAbsolutePath()+" not found");
    check(!p.isDirectory(), "Source Package "+p.getAbsolutePath()+" is a directory");
    NpmPackage npm = NpmPackage.fromPackage(new FileInputStream(p));
    String pid = npm.name();
    String version = npm.version();
    check(pid != null, "Source Package "+p.getAbsolutePath()+" Package id not found");
    check(NpmPackage.isValidName(pid), "Source Package "+p.getAbsolutePath()+" Package id "+pid+" is not valid");
    check(pid.equals(folder), "Name mismatch between folder and package ("+pid+"/"+folder+")");
    check(version != null, "Source Package "+p.getAbsolutePath()+" Package version not found");
    check(NpmPackage.isValidVersion(version), "Source Package "+p.getAbsolutePath()+" Package version "+version+" is not valid");
    String fhirKind = npm.type(); 
    check(fhirKind != null, "Source Package "+p.getAbsolutePath()+" Package type not found");
    return npm;
  }
    

  private void release(String dest, String source, VersionDecision vd, SimpleDateFormat df) throws Exception {

    NpmPackage npm = checkPackage(source, vd.id);

    boolean isNew = true;
    for (Element item : XMLUtil.getNamedChildren(channel, "item")) {
      isNew = isNew && !(npm.name()+"#"+npm.version()).equals(XMLUtil.getNamedChildText(item, "title"));
    }
    if (isNew) {
      System.out.println("Publish Package at "+source+"\\"+vd.id+" to "+dest+". release note = "+vd.releaseNote);
      checkNote(vd.releaseNote);

      File dst = new File(Utilities.path(dest, npm.name(), npm.version()));
      check(!dst.exists(), "Implied destination "+dst.getAbsolutePath()+" already exists - check that a new version is being released.");  
    
      // if jekyll.html exists, delete index.html and rename jekyll.html to index.html
      File src = new File(Utilities.path(source, vd.getId(), "output"));
      File jf = new File(Utilities.path(source, vd.getId(), "output", "jekyll.html"));
      File xf = new File(Utilities.path(source, vd.getId(), "output", "index.html"));
      if (jf.exists()) {
        xf.delete();
        jf.renameTo(xf);
      }
      // copy files
      FileUtilities.createDirectory(dst.getAbsolutePath());
      FileUtilities.copyDirectory(Utilities.path(source, vd.getId(), "output"), Utilities.path(dest, npm.name(), npm.version()), null);
      FileUtilities.copyDirectory(Utilities.path(source, vd.getId(), "output"), Utilities.path(dest, npm.name()), null);
      FileUtilities.copyFile(Utilities.path(source, vd.getId(), "package-list.json"), Utilities.path(dest, npm.name(), "package-list.json"));
      
      // update the index.html
      File file = new File(Utilities.path(dest, npm.name(), "index.html"));
      if (!file.exists()) {
        throw new Error("not found: "+file.getAbsolutePath());
      }
      updateIndex(file, PackageList.fromFile(new File(Utilities.path(dest, npm.name(), "package-list.json"))));            

      // update rss feed      
      Element item = rss.createElement("item");
      List<Element> list = XMLUtil.getNamedChildren(channel, "item");
      Node txt = rss.createTextNode("\n    ");
      if (list.isEmpty()) {
        channel.appendChild(txt);
      } else {
        channel.insertBefore(txt, list.get(0));
      }
      channel.insertBefore(item, txt);
      addTextChild(item, "title", npm.name()+"#"+npm.version());
      addTextChild(item, "description", vd.releaseNote);
      addTextChild(item, "link", Utilities.pathURL(linkRoot, npm.name(), npm.version(), "package.tgz"));
      addTextChild(item, "guid", Utilities.pathURL(linkRoot, npm.name(), npm.version(), "package.tgz")).setAttribute("isPermaLink", "true");
      addTextChild(item, "dc:creator", "FHIR Project");
      addTextChild(item, "fhir:kind", npm.type());
      addTextChild(item, "fhir:version", npm.fhirVersion());
      addTextChild(item, "pubDate", df.format(new Date()));
      txt = rss.createTextNode("\n    ");
      item.appendChild(txt);
    }
  }

  private void saveXml(FileOutputStream stream) throws TransformerException, IOException {
    TransformerFactory factory = org.hl7.fhir.utilities.xml.XMLUtil.newXXEProtectedTransformerFactory();
    Transformer transformer = factory.newTransformer();
    Result result = new StreamResult(stream);
    Source source = new DOMSource(rss);
    transformer.transform(source, result);    
    stream.flush();
  }
  private Element addTextChild(Element focus, String name, String text) {
    Node txt = rss.createTextNode("\n      ");
    focus.appendChild(txt);
    Element child = rss.createElement(name);
    focus.appendChild(child);
    child.setTextContent(text);
    return child;
  }

  private void check(boolean condition, String message) {
    if (!condition) {
      throw new Error(message);
    }
  }

  private Document loadXml(File file) throws Exception {
    InputStream src = new FileInputStream(file);
    DocumentBuilderFactory dbf = XMLUtil.newXXEProtectedDocumentBuilderFactory();
    DocumentBuilder db = dbf.newDocumentBuilder();
    return db.parse(src);
  }

  private void checkNote(String note) {
    check(note != null, "A release note must be provided");
  }

}
