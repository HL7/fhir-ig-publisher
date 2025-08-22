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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.hl7.fhir.igtools.publisher.Publisher;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.JsonException;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xml.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class TemplateReleaser {
  
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

  private static final String INDEX_TEMPLATE = "---\nlayout: page\ntitle: FHIR IG Templates (HL7 FHIR Foundation)\n---\n<p>A list of currently maintained FHIR IG templates</p>\n{{index}}"+
    "<p>Note that not all templates must be released through this page, but most are in order to benefit from a comment version/lifecycle management system. Contact fhir-director at hl7.org to get listed.</p>\n";

  private Document rss;
  private Element channel;
  private String linkRoot;

  private File xml;


  // 3 parameters: source of package, package dest folder, and release note
  public static void main(String[] args) throws Exception {
    try {
      new TemplateReleaser().release(args[0], args[1]);
    } catch (Throwable e) {
      System.out.println("Error releasing templates from "+args[0]+" to "+args[1]+":");
      System.out.println(e.getMessage());
      System.out.println("");
      e.printStackTrace();
    }
  }

  private void release(String source, String dest) throws Exception {
    System.out.println("Source: "+source);
    System.out.println("Destination: "+dest);
    SimpleDateFormat df = new SimpleDateFormat(RSS_DATE, new Locale("en", "US"));
    checkDest(dest);
    
    Map<String, String> currentPublishedVersions = scanForCurrentVersions(dest);
    Map<String, String> currentVersions = scanForCurrentVersions(source);
    summary(source, dest, currentPublishedVersions, currentVersions);
    List<VersionDecision> versionsList = analyseVersions(source, currentVersions, currentPublishedVersions, dest);
    System.out.println("Actions to take");
    for (VersionDecision vd : versionsList) {
      System.out.println(" * "+vd.summary());
    }
    System.out.println("Do you want to continue [y/n]");
    int r = System.in.read();
    if (r == 'y') {
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
      System.out.println("Building IGs");
      for (VersionDecision vd : versionsList) {
        if (!vd.built) {
          build(source, vd, versionsList);
        }
      }
      System.out.println("Finished Building IGs");
    
      System.out.println("Releasing Packages");
      for (VersionDecision vd : versionsList) {
        if (vd.type != VersionChangeType.NONE) {
          release(dest, source, vd, df);
        }
      }

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

      System.out.println("Updating Indexes");
      tidyDirectory(source, new File(dest), new File(dest), false);
      buildIndexPage(currentVersions, dest);
      System.out.println("Published");
    }
  }

  
  private void tidyDirectory(String source, File dest, File folder, boolean ver) throws IOException {
    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        File npmf = new File(Utilities.path(f, "package.tgz"));
        File ndxf = new File(Utilities.path(f, "index.html"));
        if (npmf.exists() && ndxf.exists()) {
          NpmPackage npm = NpmPackage.fromPackage(new FileInputStream(npmf));
          String index = FileUtilities.fileToString(Utilities.path(source, "index.template"));
          index = index.replace("{pid}", npm.name());
          if (ver) {
            index = index.replace("{path}", "../");
            index = index.replace("{vid}", " version "+npm.version());
          } else {
            index = index.replace("{path}", "");
            index = index.replace("{vid}", "");
          }
          String dep = buildDep(dest, npm);
          index = index.replace("{dep}", dep);         
          FileUtilities.stringToFile(index, Utilities.path(f, "index.html"));
          tidyDirectory(source, dest, f, true);
        } else {
          tidyDirectory(source, dest, f, false);
        }
      } 
    }
  }

  private String buildDep(File dest, NpmPackage npm) throws IOException {
    String npmf2 = Utilities.path(dest, npm.name(), "package.tgz");
    
    String base = npm.getNpm().asString("base");
    if (base == null) {
      return npm.name()+"#"+npm.version();
    } else {
      String baseVer = npm.getNpm().getJsonObject("dependencies").asString(base);
      String npmf = Utilities.path(dest, base, baseVer, "package.tgz");
      File f = new File(npmf);
      if (!f.exists()) {
        return npm.name()+"#"+npm.version()+ " --> "+base+"#"+baseVer+" (broken)";
      } else {
        NpmPackage bnpm = NpmPackage.fromPackage(new FileInputStream(npmf));
        return npm.name()+"#"+npm.version()+ " --> "+buildDep(dest, bnpm);       
      }
    }
  }

  private void summary(String source, String target, Map<String, String> currentPublishedVersions, Map<String, String> currentVersions) throws JsonException, IOException {
    // TODO Auto-generated method stub

    Set<String> all = new HashSet<>();
    all.addAll(currentPublishedVersions.keySet());
    all.addAll(currentVersions.keySet());
    System.out.println("Package Summary");
    int l = 0;
    for (String s : all) {
      l = Integer.max(l, s.length());
    }
    for (String s: Utilities.sorted(all)) {
      String v = currentPublishedVersions.containsKey(s) ? currentPublishedVersions.get(s) : "--";
      String nv = currentVersions.containsKey(s) ? currentVersions.get(s) : "--";
      String base = findCurrentBase(source, target, s);
      System.out.println("  "+Utilities.padRight(s, ' ', l)+"  "+Utilities.padRight(v, ' ', 10)+"  "+Utilities.padRight(nv, ' ', 10)+"    base = "+base);      
    }
  }

  private String findCurrentBase(String source, String target, String pid) throws JsonException, IOException {
    File f = new File(Utilities.path(source, pid, "package", "package.json"));
    if (!f.exists()) {
      return "?";
    }
    JsonObject json = JsonParser.parseObject(f);
    String base = json.asString("base");
    String cver = null;
    if (base == null) {
      return "--";
    } else {
      cver = base+"#"+json.forceObject("dependencies").asString(base);
    }
    f = new File(Utilities.path(target, pid, "package.tgz"));
    if (!f.exists()) {
      return cver + " / ?";
    } else {
      NpmPackage npm = NpmPackage.fromPackage(new FileInputStream(f));
      return cver +" / "+npm.getNpm().forceObject("dependencies").asString(base);
    }
  }

  private void buildIndexPage(Map<String, String> currentVersions, String path) throws JsonException, IOException {
    XhtmlNode tbl = new XhtmlNode(NodeType.Element, "table");
    tbl.attribute("class", "grid");
    XhtmlNode tr = tbl.tr();
    tr.td().b().tx("ID");
    tr.td().b().tx("Title");
    tr.td().b().tx("Version");
    tr.td().b().tx("Release Date");
    for (String id : Utilities.sorted(currentVersions.keySet())) {
      tr = tbl.tr();
      PackageList pl = new PackageList(JsonParser.parseObject(new File(Utilities.path(path, id, "package-list.json"))));
      tr.td().ah(id).tx(pl.pid());
      tr.td().tx(pl.title());
      tr.td().ah(id+"/"+pl.current().version()+"/package.tgz").tx(pl.current().version());
      tr.td().tx(pl.current().date());
    }
    String s = INDEX_TEMPLATE.replace("{{index}}", new XhtmlComposer(false, false).compose(tbl));
    FileUtilities.stringToFile(s, Utilities.path(path, "index.html"));
  }

  private void build(String source, VersionDecision vd, List<VersionDecision> versions) throws Exception {
    List<String> dependendencies = listDependencies(source, vd.getId());
    for (String s : dependendencies) {
      VersionDecision v = findVersion(versions, s);
      if (!v.built) {
        build(source, v, versions);
      }
    }
    Publisher.publishDirect(Utilities.path(source, vd.getId()));
    vd.built = true;
  }

  private void updateVersions(String source, VersionDecision vd, List<VersionDecision> versionsList) throws FileNotFoundException, IOException {
    JsonObject npm = JsonParser.parseObject(new FileInputStream(Utilities.path(source, vd.getId(), "package", "package.json")));
    npm.remove("version");
    npm.add("version", vd.getNewVersion());
    if (npm.has("dependencies")) {
      JsonObject d = npm.getJsonObject("dependencies");
      List<String> deps = new ArrayList<>();
      for (JsonProperty e : d.getProperties()) {
        deps.add(e.getName());
      }
      for (String s : deps) {
        d.remove(s);
        d.add(s, findVersion(versionsList, s).newVersion);
      }
    }
    String jcnt = JsonParser.compose(npm, true);
    FileUtilities.stringToFile(jcnt, Utilities.path(source, vd.getId(), "package", "package.json"));
  }

  private void resetVersions(String source, VersionDecision vd, List<VersionDecision> versionsList) throws FileNotFoundException, IOException {
    JsonObject npm = JsonParser.parseObject(new FileInputStream(Utilities.path(source, vd.getId(), "package", "package.json")));
    if (npm.has("dependencies")) {
      JsonObject d = npm.getJsonObject("dependencies");
      List<String> deps = new ArrayList<>();
      for (JsonProperty e : d.getProperties()) {
        deps.add(e.getName());
      }
      for (String s : deps) {
        d.remove(s);
        d.add(s, "current");
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
    PackageList pl = PackageList.fromFile(new File(Utilities.path(source, vd.getId(), "package-list.json")));
    for (PackageListEntry e : pl.versions()) {
      e.setCurrent(false);
    }
    PackageListEntry e = pl.newVersion(vd.newVersion, Utilities.pathURL(pl.canonical(), vd.newVersion), "release", "Publications", FhirPublication.R4);
    e.describe("Upgrade for dependency on "+vd.implicitSource, null, null);
    e.setDate("XXXX-XX-XX");
    e.setCurrent(true);
    FileUtilities.stringToFile(pl.toJson(), Utilities.path(source, vd.getId(), "package-list.json"));
  }

  private List<VersionDecision> analyseVersions(String source, Map<String, String> newList, Map<String, String> oldList, String dest) throws Exception {
    List<VersionDecision> res = new ArrayList<TemplateReleaser.VersionDecision>();
    for (String s : oldList.keySet()) {
      if (!s.equals("fhir.ca.template")) {
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
    }
    for (String s : newList.keySet()) {
      if (!oldList.containsKey(s)) {
        throw new Exception("New template "+s+" not found in existing set - manual set up required");
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
      System.out.println("Nothing found to release - updating History pages");
      updateHistoryPages(dest, oldList);
      System.out.println("Done");
      System.exit(0);
    }
    
    return res;
  }

  private void updateHistoryPages(String dest, Map<String, String> oldList) throws IOException {
    for (String s : oldList.keySet()) {
      String folder = Utilities.path(dest, s);
      // create history page 
      JsonObject jh = JsonParser.parseObjectFromFile(Utilities.path(folder, "package-list.json"));
      String history = FileUtilities.fileToString(Utilities.path(dest, "history.template")).replace("\r\n", "\n");
      history = history.replace("{{id}}", s);
      history = history.replace("{{pl}}", JsonParser.compose(jh, false).replace('\'', '`'));
      FileUtilities.stringToFile(history, Utilities.path(folder, "history.html"));
    }
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
      if (v == null) {
        throw new Error("Unable to find version for "+s+" for "+vd.getId());
      }
      if (v.checked == null) {
        checkDependencies(source, v, versions);
      } else if (!v.checked) {
        throw new Exception("Circular dependency");
      }
      VersionChangeType t = v.type;
      if (vd.isExplicit()) {
        if (vd.type.lessThan(t)) {
          throw new Exception("invalid operation for "+vd.id+": change type from "+vd.type+" to "+t);
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
      for (JsonProperty s : npm.getJsonObject("dependencies").getProperties()) {
//        if (!"current".equals(s.getValue().getAsString())) {
//          throw new Exception("Dependency is not 'current'");
//        }
        res.add(s.getName());
      }
    }
    return res;
  }

  private VersionChangeType checkVersionChangeType(String v, String nv) {
    if (v == null) {
      return VersionChangeType.MAJOR;      
    } else if (v.equals(nv)) { 
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
      if (f.isDirectory()) {
        scanForCurrentVersions(res, f);
      } else if (f.getName().equals("package-list.json")) {
        PackageList pl = PackageList.fromFile(f);
        boolean ok = false;
        for (PackageListEntry v : pl.list()) {
          if ("release".equals(v.status()) && v.current()) {
            res.put(pl.pid(), v.version());
            ok = true;
          }
        }
        if (!ok) {
          res.put(pl.pid(), null);            
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
    check(pid.equals(folder), "Name mismatch between folder and package");
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
      check(!dst.exists() || FileUtilities.isEmptyDirectory(dst), "Implied destination "+dst.getAbsolutePath()+" already exists - check that a new version is being released.");  
      
      // copy files
      FileUtilities.createDirectory(dst.getAbsolutePath());
      FileUtilities.copyDirectory(Utilities.path(source, vd.getId(), "output"), Utilities.path(dest, npm.name(), npm.version()), null);
      FileUtilities.copyDirectory(Utilities.path(source, vd.getId(), "output"), Utilities.path(dest, npm.name()), null);
      FileUtilities.copyFile(Utilities.path(source, vd.getId(), "package-list.json"), Utilities.path(dest, npm.name(), "package-list.json"));

      // create history page 
      JsonObject jh = JsonParser.parseObjectFromFile(Utilities.path(source, vd.getId(), "package-list.json"));
      String history = FileUtilities.fileToString(Utilities.path(dest, "history.template"));
      history = history.replace("{{id}}", vd.getId());
      history = history.replace("{{pl}}", JsonParser.compose(jh, false));
      FileUtilities.stringToFile(history, Utilities.path(dest, vd.getId(), "history.html"));

      // create index page 
      String index = FileUtilities.fileToString(Utilities.path(source, "index.template"));
      index = index.replace("{{id}}", vd.getId());
      FileUtilities.stringToFile(index, Utilities.path(dest, vd.getId(), "index.html"));
      
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
