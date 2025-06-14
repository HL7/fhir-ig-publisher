package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageClient;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;
import org.hl7.fhir.utilities.npm.PackageServer;
import org.stringtemplate.v4.ST;


public class DependentIGFinder {

  public class Triple {
    private String url;
    private String text;
    private String link;
    public Triple(String url, String text, String link) {
      super();
      this.url = url;
      this.text = text;
      this.link = link;
    }

  }

  public class DeplistSorter implements Comparator<DepInfo> {

    @Override
    public int compare(DepInfo arg0, DepInfo arg1) {
      return arg0.pid.compareTo(arg1.pid);
    }
  }

  public class DepInfoDetails {
    public DepInfoDetails(String version, String path) {
      this.version = version;
      this.path = path;
    }
    private String version;
    private String path;
    private Map<String, List<Triple>> codesystemsVs = new HashMap<>();
    private Map<String, List<Triple>> codesystemsExamples = new HashMap<>();
    private Map<String, List<Triple>> valuesetsInc = new HashMap<>();
    private Map<String, List<Triple>> valuesetsBind = new HashMap<>();
    private Map<String, List<Triple>> profilesRef = new HashMap<>();
    private Map<String, List<Triple>> profilesDeriv = new HashMap<>();
    private Map<String, List<Triple>> extensionsRef = new HashMap<>();
    private Map<String, List<Triple>> extensionsDeriv = new HashMap<>();
    private Map<String, List<Triple>> operations = new HashMap<>();
    private Map<String, List<Triple>> searchParams = new HashMap<>();
    private Map<String, List<Triple>> capabilitiesInst = new HashMap<>();
    private Map<String, List<Triple>> capabilitiesImpl = new HashMap<>();
    
  }
  
  public class DepInfo {
    private String path;
    private String pid;
    private DepInfoDetails cibuild;
    private DepInfoDetails ballot;
    private DepInfoDetails published;
    public DepInfo(String pid, String path) {
      super();
      this.pid = pid;
      this.path = path;
    }    
  }

  private List<DepInfo> deplist = new ArrayList<>();
  private List<String> errors = new ArrayList<>();
  
  private String id; // the id of the IG in question
  private String outcome; // html rendering
  private boolean debug = false;
  private boolean working = true;
  private FilesystemPackageCacheManager pcm;
  private Map<String, Triple> triples = new HashMap<>();
  private List<String> codeSystems = new ArrayList<>();
  private List<String> valueSets = new ArrayList<>();
  private List<String> profiles = new ArrayList<>();
  private List<String> extensions = new ArrayList<>();
  private List<String> logicals = new ArrayList<>();
  private List<String> searchParams = new ArrayList<>();
  private List<String> capabilityStatements = new ArrayList<>();
  private List<String> examples = new ArrayList<>();

  private String countDesc;
  private String summary;
  private String details1;
  private String details2;
  
  public DependentIGFinder(String id) throws IOException {
    super();
    this.id = id;
    pcm = new FilesystemPackageCacheManager.Builder().build();
    outcome = "Finding Dependent IGs not done yet";
  }

  public void go() {
    startThread();
//    analyse();
  }

  private void startThread() {
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          analyse();
        } catch (Exception e) {
          if (debug) System.out.println("Processing DependentIGs failed: "+e.getMessage());
        }
      }

    }).start();
  }

  private void analyse() {
    try {
      Set<String> plist = getDependentPackages();
      JsonObject json = JsonParser.parseObjectFromUrl("https://fhir.github.io/ig-registry/fhir-ig-list.json");
      
      for (String pid : plist) {
        JsonObject guide = getGuide(json, pid);
        if (guide != null) {
          checkIGDependencies(guide);
        }
      }
      render();
      working = false;
    } catch (Exception e) {
      System.out.println("Error processing dependencies: "+e.getMessage());
      errors.add("Unable to process: " +e.getMessage());
      outcome = "<span style=\"color: maroon\">Error analysing dependencies: "+Utilities.escapeXml(e.getMessage())+"</span>";
      working = false;
    }
  }

  private Set<String> getDependentPackages() {
    Set<String> list = new HashSet<>();
//    getDependentPackages(list, PackageClient.PRIMARY_SERVER);
    getDependentPackages(list, PackageServer.secondaryServer());
    return list;
  }

  private void getDependentPackages(Set<String> list, PackageServer server) {
    PackageClient client = new PackageClient(server);
    try {
      client.findDependents(list, id);
    } catch (Exception e) {
      // nothing      
    }
  }

  private JsonObject getGuide(JsonObject json, String pid) {
    for (JsonObject o : json.getJsonObjects("guides")) {
      if (pid.equals(o.asString("npm-name"))) {
        return o;
      }
    }
    return null;
  }

  private void render() {
    Collections.sort(deplist, new DeplistSorter());
    StringBuilder bs = new StringBuilder();
    StringBuilder bd1 = new StringBuilder();
    StringBuilder bd2 = new StringBuilder();
    int c = 0;
    bs.append("<table class=\"grid\">");
    bs.append("<tr>");
    bs.append("<td>id</td>");
    bs.append("<td>published</td>");
    bs.append("<td>ballot</td>");
    bs.append("<td>current</td>");
    bs.append("</tr>");
    for (DepInfo dep : deplist) {
      if (isFound(dep.ballot) || isFound(dep.cibuild) || isFound(dep.published)) {
        c++;
        bs.append("<tr>");
        bs.append("<td><a href=\"#"+dep.pid+"\">"+dep.pid+"</a></td>");
        bs.append("<td>"+present(dep.pid, dep.published, true)+"</td>");
        bs.append("<td>"+present(dep.pid, dep.ballot, true)+"</td>");
        bs.append("<td>"+present(dep.pid, dep.cibuild, false)+"</td>");
        bs.append("</tr>");
        if (isFound(dep.cibuild)) {
          details(bd1, dep, dep.cibuild);
        } else if (isFound(dep.published)) {
          details(bd1, dep, dep.published);
        } else if (isFound(dep.ballot)) {
          details(bd1, dep, dep.ballot);
        }
      }
    }
    bs.append("</table>");

    generate(bd2, codeSystems, "CodeSystems");
    generate(bd2, valueSets, "ValueSets");
    generate(bd2, profiles, "Profiles");
    generate(bd2, extensions, "Extensions");

    if (c == 0) {
      countDesc = "no references";
      summary = "<p>no references</p>";
      details1 = "<p>(no details)</p>";
      details2 = "<p>(no details)</p>";
    } else {
      countDesc = ""+c+" "+Utilities.pluralize("guide", c);
      summary = bs.toString();
      details1 = bd1.toString();
      details2 = bd2.toString();
    }
  }

  private void generate(StringBuilder bd2, List<String> list, String title) {
    bd2.append("<h3>"+title+"</h3>\r\n");
    
    bd2.append("<table class=\"grid\">");
    for (String s : sorted(list)) {
      Triple t = triples.get(s);
      bd2.append("<tr>");
      bd2.append("  <td><a href=\""+t.link+"\" title=\""+t.url+"\">"+Utilities.escapeXml(t.text)+"</a></td>\r\n");      
      bd2.append("  <td>\r\n");
      int c = 0;
      for (DepInfo dep : deplist) {
        if (isFound(dep.cibuild)) {
          c = processDep(bd2, s, dep, dep.cibuild, c);          
        } else if (isFound(dep.ballot)) {
          c = processDep(bd2, s, dep, dep.ballot, c);          
        } else if (isFound(dep.published)) {
          c = processDep(bd2, s, dep, dep.published, c);
        }
      }
      if (c == 0) {
        bd2.append("(not used)");
      }
      bd2.append("  </td>\r\n");
      bd2.append(" </tr>\r\n");
    }
    bd2.append("</table>");
  }

  private int processDep(StringBuilder bd2, String url, DepInfo dep, DepInfoDetails di, int c) {
    c = processDepList(bd2, url, dep, di.codesystemsVs, dep.pid, c, "Used In");
    c = processDepList(bd2, url, dep, di.codesystemsExamples, dep.pid, c, "Used In");
    c = processDepList(bd2, url, dep, di.valuesetsBind, dep.pid, c, "Bound In");
    c = processDepList(bd2, url, dep, di.valuesetsInc, dep.pid, c, "Imported In");
    c = processDepList(bd2, url, dep, di.profilesDeriv, dep.pid, c, "Constrained In");
    c = processDepList(bd2, url, dep, di.profilesRef, dep.pid, c, "Used In");
    c = processDepList(bd2, url, dep, di.extensionsDeriv, dep.pid, c, "Constrained In");
    c = processDepList(bd2, url, dep, di.extensionsRef, dep.pid, c, "Used In");
    return c;
  }

  private int processDepList(StringBuilder bd2, String url, DepInfo dep, Map<String, List<Triple>> map, String pid, int c, String label) {
    List<Triple> list = map.get(url);
    if (list != null) {
      for (Triple t : list) {
        if (c > 0) {
          bd2.append("<br/>");
        }
        c++;
        bd2.append("<a href=\""+t.link+"\" title=\""+t.url+"\">"+label+" "+Utilities.escapeXml(t.text)+"</a> ("+pid+")\r\n");      
      }
    }
    return c;
  }

  private List<String> sorted(List<String> list) {
    List<String> res = new ArrayList<>();
    res.addAll(list);
    Collections.sort(res);
    return res;
  }

  private void details(StringBuilder bd1, DepInfo dep, DepInfoDetails di) {
    bd1.append("<a name=\""+dep.pid+"\"> </a><br/><h2><a href=\""+di.path+"\">"+dep.pid+"#"+di.version+"</a></h3>\r\n");
    generateDetails(bd1, "CodeSystems used in ValueSets", di.codesystemsVs);    
    generateDetails(bd1, "ValueSets used in profiles", di.valuesetsBind);    
    generateDetails(bd1, "Derived profiles", di.profilesDeriv);    
    generateDetails(bd1, "Used profiles", di.profilesRef);    
    generateDetails(bd1, "Derived extensions", di.extensionsDeriv);    
    generateDetails(bd1, "Used Extensions", di.extensionsRef);    
  }

  private void generateDetails(StringBuilder bd1, String head, Map<String, List<Triple>> list) {
    if (!list.isEmpty()) {
      bd1.append("<p><b>"+head+"</b></p>\r\n<table class=\"grid\">\r\n");
      for (String n : list.keySet()) {
        bd1.append(" <tr>\r\n");
        Triple t = triples.get(n);
        bd1.append("  <td><a href=\""+t.link+"\" title=\""+t.url+"\">"+Utilities.escapeXml(t.text)+"</a></td>\r\n");
        bd1.append("  <td>\r\n");
        boolean first = true;
        for (Triple p : list.get(n)) {
          if (first) { first = false; } else { bd1.append("<br/>"); }
          bd1.append("  <a href=\""+p.link+"\" title=\""+t.url+"\">"+Utilities.escapeXml(p.text)+"</a>\r\n");
        }
        bd1.append("  </td>\r\n");
        bd1.append(" </tr>\r\n");
      }
      bd1.append("</table>");
    }
  }

  private String present(String id, DepInfoDetails s, boolean currentWrong) {
    if (s == null) {
      return "";
    }
    if (s.version.equals("??")) {
      return "<span style=\"color: maroon\">??</span>";
    }
    if (s.version.equals("current") && currentWrong) {
      return "<a style=\"background-color: #ffd4d4\" href=\""+s.path+"_current\">current</span>";
    }      
    return "<a style=\"color: maroon\" href=\""+s.path+"\">"+s.version+"</span>";
  }

  private boolean isFound(DepInfoDetails s) {
    return s != null && !s.version.equals("??");
  }

  private void checkIGDependencies(JsonObject guide) { 
    String pid = guide.asString("npm-name");
//    System.out.println("check "+pid+" " +abc);
    
    // we only check the latest published version, and the CI build
    try {
      PackageList pl = PackageList.fromUrl(Utilities.pathURL(guide.asString("canonical"), "package-list.json"));
      String canonical = guide.asString("canonical");
      DepInfo dep = new DepInfo(pid, Utilities.path(canonical, "history.html"));
      deplist.add(dep);
      for (PackageListEntry e : pl.versions()) {
        boolean ballot = false;
        String version = e.version();
          String status = e.status();
          if ("ballot".equals(status) || "public-comment".equals(status) ) {
            if (!ballot) {
              ballot = true;
              dep.ballot = checkForDependency(pid, version, e.path());          
            }
          } else {
            dep.published = checkForDependency(pid, version, e.path());                    
            break;
          }
      }
      dep.cibuild = checkForDependency(pid, "current", guide.asString("ci-build"));
    } catch (Exception e) {
      errors.add("Unable to process "+guide.asString("name")+": " +e.getMessage());
      if (debug) System.out.println("Dependency Analysis - Unable to process "+guide.asString("name")+": " +e.getMessage());
    }    
  }

  private DepInfoDetails checkForDependency(String pid, String version, String path) throws IOException {
    try {
      NpmPackage npm = pcm.loadPackage(pid, version);
      for (String dep : npm.dependencies()) {
        if (dep.startsWith(id+"#")) {
          return buildDetails(npm, path, dep.substring(dep.indexOf("#")+1)); 
        }
      }
    } catch (Exception e) {
      if (!"current".equals(version)) {
        errors.add("Unable to process "+pid+"#"+version+": " +e.getMessage());
        if (debug) System.out.println("Dependency Analysis - Unable to process "+pid+": " +e.getMessage());
      }
      return new DepInfoDetails("??", null);
    }    
    return null;
  }

  private DepInfoDetails buildDetails(NpmPackage npm, String path, String version) throws FHIRFormatError, IOException {
    DepInfoDetails res = new DepInfoDetails(version, path);
    if (VersionUtilities.isR4Ver(npm.fhirVersion()) || VersionUtilities.isR4BVer(npm.fhirVersion())) {
      scanR4IG(npm, res);
    } else if (VersionUtilities.isR5Plus(npm.fhirVersion())) {
//      scanR5IG(npm, res);
    } else if (VersionUtilities.isR3Ver(npm.fhirVersion())) {
//      scanR3IG(npm, res);
    }    
    return res;
  }

  private void scanR4IG(NpmPackage npm, DepInfoDetails di) throws FHIRFormatError, IOException {
    try {
    for (String t : npm.listResources("CodeSystem", "ValueSet", "StructureDefinition")) {
      org.hl7.fhir.r4.model.Resource r = new org.hl7.fhir.r4.formats.JsonParser().parse(npm.loadResource(t));
      String link = "??";
      if (r instanceof org.hl7.fhir.r4.model.CodeSystem) {
        scanCodeSystemR4((org.hl7.fhir.r4.model.CodeSystem) r, di);
      } else if (r instanceof org.hl7.fhir.r4.model.ValueSet) {
        scanValueSetR4((org.hl7.fhir.r4.model.ValueSet) r, di, link);
      } else if (r instanceof org.hl7.fhir.r4.model.StructureDefinition) {
        scanStructureDefinitionR4((org.hl7.fhir.r4.model.StructureDefinition) r, di, link);
      }
    }
    // check the examples
    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    }
  }

  private void scanCodeSystemR4(org.hl7.fhir.r4.model.CodeSystem cs, DepInfoDetails di) {
    // todo at some stage: check properties)
    
  }

  private void scanValueSetR4(org.hl7.fhir.r4.model.ValueSet vs, DepInfoDetails di, String link) {
    for (ConceptSetComponent t : vs.getCompose().getInclude()) {
      addToMap(codeSystems, di.codesystemsVs, t.getSystem(), vs.getUrl(), vs.present(), link);
    }   
    for (ConceptSetComponent t : vs.getCompose().getExclude()) {
      addToMap(codeSystems, di.codesystemsVs, t.getSystem(), vs.getUrl(), vs.present(), link);
    }
  }

  private void addToMap(List<String> set, Map<String, List<Triple>> list, String url, String turl, String title, String link) {
    if (set.contains(url)) {
      if (!list.containsKey(url)) {
        list.put(url, new ArrayList<>());          
      }      
      List<Triple> v = list.get(url);
      for (Triple t : v) {
        if (t.url != null && t.url.equals(turl)) {
          return;
        }
      }
      v.add(new Triple(turl, title, link));
    }
  }

  private void scanStructureDefinitionR4(org.hl7.fhir.r4.model.StructureDefinition sd, DepInfoDetails di, String link) {    
    addToMap(profiles, di.profilesDeriv, sd.getBaseDefinition(), sd.getUrl(), sd.present(), link);
    addToMap(extensions, di.extensionsDeriv, sd.getBaseDefinition(), sd.getUrl(), sd.present(), link);
    
    // we only scan diffs - we're not interested in repeating everything 
    for (org.hl7.fhir.r4.model.ElementDefinition ed : sd.getDifferential().getElement()) {
      if (ed.getBinding().hasValueSet()) {
        addToMap(valueSets, di.valuesetsBind, ed.getBinding().getValueSet(), sd.getUrl(), sd.present(), link);
      }      
      for (org.hl7.fhir.r4.model.ElementDefinition.TypeRefComponent tr : ed.getType()) {
        for (CanonicalType c : tr.getProfile()) {
          addToMap(extensions, di.extensionsRef, c.getValue(), sd.getUrl(), sd.present(), link);
        }
        for (CanonicalType c : tr.getTargetProfile()) {
          addToMap(profiles, di.profilesRef, c.getValue(), sd.getUrl(), sd.present(), link);
        }
      }
      if (ed.hasFixedOrPattern()) {
        if (ed.getFixedOrPattern() instanceof org.hl7.fhir.r4.model.Coding) {
          org.hl7.fhir.r4.model.Coding c = (org.hl7.fhir.r4.model.Coding) ed.getFixedOrPattern();
          addToMap(codeSystems, di.codesystemsExamples, c.getSystem(), sd.getUrl(), sd.present(), link);
        }
        if (ed.getFixedOrPattern() instanceof org.hl7.fhir.r4.model.CodeableConcept) {
          org.hl7.fhir.r4.model.CodeableConcept cc = (org.hl7.fhir.r4.model.CodeableConcept) ed.getFixedOrPattern();
          for (org.hl7.fhir.r4.model.Coding c : cc.getCoding()) {
            addToMap(codeSystems, di.codesystemsExamples, c.getSystem(), sd.getUrl(), sd.present(), link);
          }
        }
        if (ed.getFixedOrPattern() instanceof org.hl7.fhir.r4.model.Quantity) {
          org.hl7.fhir.r4.model.Quantity c = (org.hl7.fhir.r4.model.Quantity) ed.getFixedOrPattern();
          addToMap(codeSystems, di.codesystemsExamples, c.getSystem(), sd.getUrl(), sd.present(), link);
        }
      }
    }
  }
  
  public String getId() {
    return id;
  }

  private ST template(String t) {
    return new ST(t, '$', '$');
  }

  public void finish(String path, String title) throws IOException {
    while (working) {
      try {
        Thread.sleep(1000);
        System.out.println("Waiting for dependency analysis to complete");
      } catch (InterruptedException e) {
      }
    }
    
    String page = 
        "<!DOCTYPE HTML>\r\n"+
        "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\r\n"+
        "<head>\r\n"+
        "  <title>$title$ : Dependent IGs Analysis</title>\r\n"+
        "  <link href=\"fhir.css\" rel=\"stylesheet\"/>\r\n"+
        "<body style=\"margin: 20px; background-color: #ffffff\">\r\n"+
        " <h1>Dependent IGs Analysis for $title$</h1>\r\n"+
        " <p>Generated $time$ for $packageId$</p>\r\n"+
        " <h2>Summary:</h2></tr>\r\n"+
        "$summary$\r\n"+
        " <h2>Details By IG</h2></tr>\r\n"+
        "$details1$\r\n"+
        " <h2>Details By Resource</h2></tr>\r\n"+
        "$details2$\r\n"+
        " <h2>Errors</h2></tr>\r\n"+
        "<pre>\r\n"+
        "$errors$\r\n"+
        "</pre>\r\n"+
        "</body>\r\n"+
        "</html>\r\n";

    ST t = template(page);
    t.add("title", title);
    t.add("time", new Date().toString());
    t.add("packageId", id);
    t.add("summary", summary);
    t.add("details1", details1);
    t.add("details2", details2);
    t.add("errors", String.join("\r\n", errors));
    
    FileUtilities.stringToFile(t.render(), Utilities.path(path, "qa-dep.html"));
  }

  public void addCodeSystem(String url, String title, String link) {
    codeSystems.add(url);
    triples.put(url, new Triple(url, title, link));
  }

  public void addValueSet(String url, String title, String link) {
    valueSets.add(url);
    triples.put(url, new Triple(url, title, link));
  }

  public void addProfile(String url, String title, String link) {
    profiles.add(url);
    triples.put(url, new Triple(url, title, link));
  }

  public void addExtension(String url, String title, String link) {
    extensions.add(url);
    triples.put(url, new Triple(url, title, link));
  }

  public void addLogical(String url, String title, String link) {
    logicals.add(url);
    triples.put(url, new Triple(url, title, link));
  }

  public void addSearchParam(String url, String title, String link) {
    searchParams.add(url);
    triples.put(url, new Triple(url, title, link));
  }

  public void addCapabilityStatement(String url, String title, String link) {
    capabilityStatements.add(url);
    triples.put(url, new Triple(url, title, link));
  }

  public void addExample(String url, String title, String link) {
    examples.add(url);
    triples.put(url, new Triple(url, title, link));
  }

  public String getCountDesc() {
    return countDesc;
  }


}
