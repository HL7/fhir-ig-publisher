package org.hl7.fhir.igtools.renderers;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.persistence.jpa.jpql.parser.FunctionsReturningDatetimeBNF;
import org.hl7.fhir.convertors.conv14_50.VersionConvertor_14_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_14_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_43_50;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.DependencyAnalyser;
import org.hl7.fhir.igtools.publisher.DependencyAnalyser.ArtifactDependency;
import org.hl7.fhir.igtools.renderers.DependencyRenderer.GlobalProfile;
import org.hl7.fhir.igtools.renderers.DependencyRenderer.GlobalProfileSorter;
import org.hl7.fhir.igtools.renderers.DependencyRenderer.VersionState;
import org.hl7.fhir.igtools.templates.TemplateManager;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideGlobalComponent;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.BasePackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageHacker;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.Cell;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.Row;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.TableModel;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class DependencyRenderer {

  public class GlobalProfile {
    private ImplementationGuide guide; 
    private StructureDefinition profile;
    private String type;
    private String pUrl;
    private NpmPackage npm;
    
    public GlobalProfile(NpmPackage npm, ImplementationGuide guide, String type, String pUrl, StructureDefinition profile) {
      super();
      this.npm = npm;
      this.guide = guide;
      this.type = type;
      this.pUrl = pUrl;
      this.profile = profile;
    }
    public String getpUrl() {
      return pUrl;
    }
    public ImplementationGuide getGuide() {
      return guide;
    }
    public StructureDefinition getProfile() {
      return profile;
    }
    public String getType() {
      return type;
    }
    public NpmPackage getNpm() {
      return npm;
    }
  }

  public class GlobalProfileSorter implements Comparator<GlobalProfile> {

    @Override
    public int compare(GlobalProfile o1, GlobalProfile o2) {
      String p1 = o1.npm == null ? "" : o1.npm.name();
      String p2 = o2.npm == null ? "" : o2.npm.name();
      return o1.type.equals(o2.type) ? p1.compareTo(p2) : o1.type.compareTo(o2.type);
    }

  }

  public enum VersionState {
    VERSION_LATEST_INTERIM,
    VERSION_LATEST_MILESTONE,
    VERSION_OUTDATED,
    VERSION_NO_LIST,
    VERSION_UNKNOWN,
  }

  private BasePackageCacheManager pcm;
  private String dstFolder;
  private Set<String> ids = new HashSet<>();
  private String fver;
  private String npmName;
  private TemplateManager templateManager;
  private Map<String, JsonObject> packageListCache = new HashMap<>();
  private List<DependencyAnalyser.ArtifactDependency> dependencies;
  private List<GlobalProfile> globals = new ArrayList<>();
  private IWorkerContext context;
  
  public DependencyRenderer(BasePackageCacheManager pcm, String dstFolder, String npmName, TemplateManager templateManager, List<DependencyAnalyser.ArtifactDependency> dependencies, IWorkerContext context) {
    super();
    this.pcm = pcm;
    this.dstFolder = dstFolder;
    this.npmName = npmName;
    this.templateManager = templateManager;
    this.dependencies = dependencies;
    this.context = context;
  }

  public String render(ImplementationGuide ig, boolean QA, boolean details) throws FHIRException, IOException {
    boolean hasDesc = false;
    for (ImplementationGuideDependsOnComponent d : ig.getDependsOn()) {
      hasDesc = hasDesc || d.hasExtension(ToolingExtensions.EXT_IGDEP_COMMENT);
    }
    
    HierarchicalTableGenerator gen = new HierarchicalTableGenerator(dstFolder, true, true);
    TableModel model = createTable(gen, QA, hasDesc);
    
    String realm = determineRealmForIg(ig.getPackageId());

    StringBuilder b = new StringBuilder();
    
    Row row = addBaseRow(gen, model, ig, QA, hasDesc);
    for (ImplementationGuideDependsOnComponent d : ig.getDependsOn()) {
      try {
        NpmPackage p = resolve(d);
        addPackageRow(gen, row.getSubRows(), p, d.getVersion(), realm, QA, b, ToolingExtensions.readStringExtension(d, ToolingExtensions.EXT_IGDEP_COMMENT), hasDesc);
      } catch (Exception e) {
        addErrorRow(gen, row.getSubRows(), d.getPackageId(), d.getVersion(), d.getUri(), null, e.getMessage(), QA, hasDesc);
      }
    }
    if (!QA) {
      checkGlobals(ig, null);
    }
    // create the table
    // add the rows 
    // render it       
    XhtmlNode x = gen.generate(model, dstFolder, 0, null);
    XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
    div.add(x);
    if (QA) {
      div.add(makeTemplateTable());
    }
    return new XhtmlComposer(false).compose(div)+(details ? b.toString() : "");
  }

  private XhtmlNode makeTemplateTable() {
    XhtmlNode p = new XhtmlNode(NodeType.Element, "para");
    CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder(" -> ");
    if (templateManager != null) {
      for (String  t : templateManager.listTemplates()) {
        b.append(t);
      }
      p.tx("Templates: "+b.toString());
    } else {
      p.tx("No templates used");
    }
    
    return p;
  }

  private String determineRealmForIg(String packageId) {
    if (packageId.startsWith("hl7.")) {
      String[] p = packageId.split("\\.");
      if (p.length > 2 && "fhir".equals(p[1])) {
        if (Utilities.existsInList(p[2], "us", "au", "ch", "be", "nl", "fr", "de") ) {
          return p[2];
        }
        return "uv";
      }
    }
    return null;
  }

  private void addPackageRow(HierarchicalTableGenerator gen, List<Row> rows, NpmPackage npm, String originalVersion, String realm, boolean QA, StringBuilder b, String desc, boolean hasDesc) throws FHIRException, IOException {
    if (!npm.isCore()) {
      String idv = npm.name()+"#"+npm.version();
      boolean isNew = !ids.contains(idv);
      ids.add(idv);
      String comment = "";
      if (!isNew) {
        comment = "see above";
      } else if (!VersionUtilities.versionsCompatible(fver, npm.fhirVersion())) {
        comment = "FHIR Version Mismatch";
      } else if ("current".equals(npm.version())) {
        comment = "Cannot be published with a dependency on a current build version";
      } else if (!npm.version().equals(originalVersion)) {
        comment = "Matched to latest patch release";
      } else if (realm != null) {
        String drealm = determineRealmForIg(npm.name());
        if (drealm != null) {
          if ("uv".equals(realm)) {
            if (!"uv".equals(drealm)) {
              comment = "An international realm (uv) publication cannot depend on a realm specific guide ("+drealm+")";
            }
          } else if (!"uv".equals(drealm) && !realm.equals(drealm)) {
            comment = "An realm publication for "+realm+" should not depend on a realm specific guide from ("+drealm+")";
          }
        }
      }
      Row row = addRow(gen, rows, npm.name(), npm.title(), npm.version(), getVersionState(npm.name(), npm.version(), npm.canonical()), getLatestVersion(npm.name(), npm.canonical()), "current".equals(npm.version()), npm.fhirVersion(), 
          !VersionUtilities.versionsCompatible(fver, npm.fhirVersion()), npm.canonical(), PackageHacker.fixPackageUrl(npm.getWebLocation()), comment, desc, QA, hasDesc);
      if (isNew) {
        for (String d : npm.dependencies()) {
          String id = d.substring(0, d.indexOf("#"));
          String version = d.substring(d.indexOf("#")+1);
          try {
            NpmPackage p = resolve(id, version);
            addPackageRow(gen, row.getSubRows(), p, d.substring(d.indexOf("#")+1), realm, QA, b, null, hasDesc);
          } catch (Exception e) {
            addErrorRow(gen, row.getSubRows(), id, version, null, null, e.getMessage(), QA, hasDesc);
          }
        }
      }
    }
    if (!QA) {
      checkGlobals(npm);
      if (!npm.isCore() && !npm.isTx()) {
        String n = (npm.name()+"#"+npm.version());
        b.append("<p><b>Package ");
        b.append(n);
        b.append("</b></p>\r\n<p>");
        b.append(Utilities.escapeXml(npm.description()));
        b.append("</p>\r\n<p><b>Dependencies</b></p>\r\n");
        boolean first = true;
        for (ArtifactDependency ad : dependencies) {
          String t = ad.getTarget().getUserString("package");
          if (n.equals(t)) {
            if (first) {
              b.append("<ul>\r\n");
              first = false;
            }
            b.append("<li><a href=\"");
            b.append(ad.getSource().getUserString("path"));
            b.append("\">");
            if (ad.getSource() instanceof CanonicalResource) {
              b.append(Utilities.escapeXml(((CanonicalResource) ad.getSource()).present()));
            } else {
              b.append(ad.getSource().fhirType()+"/"+ad.getSource().getId());          
            }
            b.append("</a> ");
            b.append(ad.getKind());
            b.append(" <a href=\"");
            b.append(ad.getTarget().getUserString("path"));
            b.append("\">");
            if (ad.getTarget() instanceof CanonicalResource) {
              b.append(Utilities.escapeXml(((CanonicalResource) ad.getTarget()).present()));
            } else {
              b.append(ad.getTarget().fhirType()+"/"+ad.getTarget().getId());          
            }
            b.append("</a></li>\r\n");
          } 
        }
        if (first) {
          b.append("<p><i>No dependencies found</i></p>\r\n");
        } else {
          b.append("</ul>\r\n");
        }
      }
    }
  }


  private void checkGlobals(NpmPackage npm) throws IOException {
    for (String n : npm.listResources("ImplementationGuide")) {
      ImplementationGuide ig = loadImplementationGuide(npm.loadResource(n), npm.fhirVersion());
      if (ig != null && !ig.getUrl().equals("http://hl7.org/fhir/us/daf")) {
        checkGlobals(ig, npm);
      }
    }
  }

  private ImplementationGuide loadImplementationGuide(InputStream content, String v) throws FHIRFormatError, FHIRException, IOException {
    if (VersionUtilities.isR2BVer(v)) {
      return (ImplementationGuide) VersionConvertorFactory_14_50.convertResource(new org.hl7.fhir.dstu2016may.formats.JsonParser().parse(content));
    } else if (VersionUtilities.isR3Ver(v)) {
      return (ImplementationGuide) VersionConvertorFactory_30_50.convertResource(new org.hl7.fhir.dstu3.formats.JsonParser().parse(content));
    } else if (VersionUtilities.isR4Ver(v)) {
      return (ImplementationGuide) VersionConvertorFactory_40_50.convertResource(new org.hl7.fhir.r4.formats.JsonParser().parse(content));
    } else if (VersionUtilities.isR4BVer(v)) {
      return (ImplementationGuide) VersionConvertorFactory_43_50.convertResource(new org.hl7.fhir.r4b.formats.JsonParser().parse(content));
    } else if (VersionUtilities.isR4BVer(v)) {
      return (ImplementationGuide) new org.hl7.fhir.r5.formats.JsonParser().parse(content);
    } else {
      return null;
    }
  }

  private void checkGlobals(ImplementationGuide ig, NpmPackage npm) {
    for (ImplementationGuideGlobalComponent g : ig.getGlobal()) {
      StructureDefinition sd = context.fetchResource(StructureDefinition.class, g.getProfile());
      globals.add(new GlobalProfile(npm, ig, g.getType(), g.getProfile(), sd));
    }    
  }

  private String getLatestVersion(String name, String canonical) {
    JsonObject pl = fetchPackageList(name, canonical);
    if (pl == null) {
      return null;
    }
    for (JsonObject v : pl.getArr("list").asObjects()) {
      if (!"current".equals(v.getString("version"))) {
        if (v.getBoolean("current")) {// this is the current official release
          return v.getString("version");
        } 
      }
    }      
    return null;
  }

  private VersionState getVersionState(String name, String version, String canonical) {
    JsonObject pl = fetchPackageList(name, canonical);
    if (pl == null) {
      return VersionState.VERSION_NO_LIST;
    }
    boolean latestInterim = true;
    for (JsonObject v : pl.getArr("list").asObjects()) {
      if (!"current".equals(v.getString("version"))) {
        if (version.equals(v.getString("version"))) {
          if (v.getBoolean("current")) {// this is the current official release
            return VersionState.VERSION_LATEST_MILESTONE;
          } if (latestInterim) {
            return VersionState.VERSION_LATEST_INTERIM;
          } else {
            return VersionState.VERSION_OUTDATED;
          }
        } else {
          latestInterim = false;
        }
      }      
    }
    return VersionState.VERSION_UNKNOWN;
  }

  private JsonObject fetchPackageList(String name, String canonical) {
    if (packageListCache .containsKey(name)) {
      return packageListCache.get(name);
    }
    JsonObject pl;
    try {
      pl =  org.hl7.fhir.utilities.json.parser.JsonParser.parseObjectFromUrl(Utilities.pathURL(canonical, "package-list.json")); 
          
    } catch (Exception e) {
      pl = null;
    }
    packageListCache.put(name, pl);
    return pl;
  }

  private NpmPackage resolve(ImplementationGuideDependsOnComponent d) throws FHIRException, IOException {
    if (d.hasPackageId()) {
      return resolve(d.getPackageId(), d.getVersion());
    }
    String pid = pcm.getPackageId(d.getUri());
    if (pid == null) {
      throw new FHIRException("Unable to resolve canonical URL to package Id");
    }
    return resolve(pid, d.getVersion());
  }

  private NpmPackage resolve(String id, String version) throws FHIRException, IOException {
    if (VersionUtilities.isCorePackage(id)) {
      version = VersionUtilities.getCurrentVersion(version);
      return pcm.loadPackage(id, version);      
    } else {
      return pcm.loadPackage(id, version);
    }
  }

  private Row addBaseRow(HierarchicalTableGenerator gen, TableModel model, ImplementationGuide ig, boolean QA, boolean hasDesc) {
    String id = ig.getPackageId();
    String ver = ig.getVersion();
    String fver = ig.getFhirVersion().get(0).asStringValue();
    this.fver = fver;
    String canonical = ig.getUrl();
    String web = ig.getManifest().getRendering();
    if (canonical.contains("/ImplementationGuide/")) {
      canonical = canonical.substring(0, canonical.indexOf("/ImplementationGuide/"));
    }
    String comment = null;
    if (!id.equals(npmName)) {
      comment = "Expected Package Id is "+npmName;
    } else if (id.startsWith("hl7") && !id.startsWith("hl7.fhir.")) {
      comment = "HL7 Packages must have an id that starts with hl7.fhir.";
    }
    Row row = addRow(gen, model.getRows(), id,  ig.present(), ver, null, null, false, fver, false, canonical, web, comment, null, QA, hasDesc);
    if (QA && comment != null) {
      row.getCells().get(5).addStyle("background-color: #ffcccc");
    }
    
    return row;
  }

  private Row addRow(HierarchicalTableGenerator gen, List<Row> rows, String id, String title, String ver, VersionState verState, String latestVer, boolean verError, String fver, boolean fverError, String canonical, String web, String problems, String desc, boolean QA, boolean hasDesc) {
    Row row = gen.new Row();
    rows.add(row);
    row.setIcon("icon-fhir-16.png", "NPM Package");
    if (QA) {
      row.getCells().add(gen.new Cell(null, null, id, null, null));
      Cell c = gen.new Cell(null, null, ver, null, null);
      row.getCells().add(c);
      if (verState != null) {
        c.addText(" ");
        switch (verState) {
        case VERSION_LATEST_INTERIM:
          c.addStyledText("Latest Interim Release", "I", "white", "green", null, false);
          break;
        case VERSION_LATEST_MILESTONE:
          c.addStyledText("Latest Milestone Release", "M", "white", "green", null, false);
          break;
        case VERSION_OUTDATED:
          c.addStyledText("Outdated Release", "O", "white", "red", null, false);
          break;
        case VERSION_NO_LIST:
          c.addStyledText("Not yet released", "U", "white", "red", null, false);
          break;    
        case VERSION_UNKNOWN:
          c.addStyledText("Illegal Version", "V", "white", "red", null, false);
          break;    
        }      
      }
    } else {
      row.getCells().add(gen.new Cell(null, web, title, "Canonical: "+canonical, null));
      row.getCells().add(gen.new Cell(null, "https://simplifier.net/packages/"+id+"/"+ver, id+"#"+ver, null, null));
    }
    row.getCells().add(gen.new Cell(null, VersionUtilities.getSpecUrl(fver), VersionUtilities.getNameForVersion(fver), null, null));
    if (QA) {
      row.getCells().add(gen.new Cell(null, null, canonical, null, null));
      row.getCells().add(gen.new Cell(null, null, web, null, null));
      
      String s = Utilities.noString(problems) ? "" : problems;
      String v = verState == VersionState.VERSION_OUTDATED ? "Latest Release is "+latestVer+"" : "";
      if (Utilities.noString(s)) {
        if (Utilities.noString(v)) {
          s = "";
        } else {
          s = v;
        }
      } else {
        if (Utilities.noString(v)) {
          // s = s;
        } else {
          s = s +". "+ v;
        }      
      }
      row.getCells().add(gen.new Cell(null, null, s, null, null));
      if (verError) {
        row.getCells().get(1).addStyle("background-color: #ffcccc");
      }
      if (fverError) {
        row.getCells().get(2).addStyle("background-color: #ffcccc");
      }
    } else if (hasDesc) {   
      row.getCells().add(gen.new Cell(null, null, desc, null, null));
    }
    return row;
  }

  private void addErrorRow(HierarchicalTableGenerator gen, List<Row> rows, String id, String ver, String uri, String web, String message, boolean QA, boolean hasDesc) {
    Row row = gen.new Row();
    rows.add(row);
    row.setIcon("icon-fhir-16.png", "NPM Package");
    row.getCells().add(gen.new Cell(null, null, id, null, null));
    row.getCells().add(gen.new Cell(null, null, ver, null, null));
    row.getCells().add(gen.new Cell(null, null, null, null, null));
    row.getCells().add(gen.new Cell(null, null, uri, null, null));
    row.getCells().add(gen.new Cell(null, null, web, null, null));
    if (QA) {
      row.getCells().add(gen.new Cell(null, null, message, null, null));
    } else if (hasDesc) {
      row.getCells().add(gen.new Cell(null, null, message, null, null));
    }
    row.setColor("#ffcccc");
  }

  private TableModel createTable(HierarchicalTableGenerator gen, boolean QA, boolean hasDesc) {
    TableModel model = gen.new TableModel("dep", false);
    
    model.setAlternating(true);
    if (!QA) {
      model.getTitles().add(gen.new Title(null, null, "IG", "Implementation Guide Reference", null, 0));      
    }
    model.getTitles().add(gen.new Title(null, null, "Package", "The NPM Package Id", null, 0));
    if (QA) {
      model.getTitles().add(gen.new Title(null, null, "Version", "The version of the package", null, 0));
    }
    model.getTitles().add(gen.new Title(null, null, "FHIR", "The version of FHIR that the package is based on", null, 0));
    if (QA) {
      model.getTitles().add(gen.new Title(null, null, "Canonical", "Canonical URL", null, 0));
      model.getTitles().add(gen.new Title(null, null, "Web Base", "Web Reference Base", null, 0));
      model.getTitles().add(gen.new Title(null, null, "Comment", "Comments about this entry", null, 0));
    } else if (hasDesc) {
      model.getTitles().add(gen.new Title(null, null, "Comment", "Explains why this dependency exists", null, 0));
    }
    return model;
  }
  
  public String renderGlobals() {
    if (globals.isEmpty()) {
      return "<p><i>There are no Global profiles defined</i></p>\r\n";
    } else {
      StringBuilder b = new StringBuilder();
      b.append("<p>Global Profiles:</p>\r\n<table class=\"none\">\r\n<tr><td><b>Type</b></td><td><b>Source</b></td><td><b>Profile</b></td></tr>\r\n");
      Collections.sort(globals, new GlobalProfileSorter());
      for (GlobalProfile gp : globals) {
        b.append("<tr><td>");
        StructureDefinition sd = context.fetchTypeDefinition(gp.type);
        if (sd == null) {
          b.append("<code>");
          b.append(gp.type);          
          b.append("</code>");          
        } else {
          b.append("<a href=\"");
          b.append(sd.getUserString("path"));
          b.append("\">");
          b.append(Utilities.escapeXml(sd.present()));
          b.append("</a>");
        }
        b.append("</td><td>");
        if (gp.npm != null) {
          b.append("<a href=\"");
          b.append(gp.npm.getWebLocation());
          b.append("\">");
          b.append(Utilities.escapeXml(gp.npm.name()+"#"+gp.npm.version()));
          b.append("</a>");
        }
        b.append("</td><td>");
        if (gp.profile == null) {
          b.append("<code>");
          b.append(gp.pUrl);          
          b.append("</code>");          
        } else {
          b.append("<a href=\"");
          b.append(gp.profile.getUserString("path"));
          b.append("\">");
          b.append(Utilities.escapeXml(gp.profile.present()));
          b.append("</a>");
        }
        b.append("</td></tr>");
      }
      b.append("</table>\r\n");
      b.append("<p>All resources of these types must conform to these profiles.</p>\r\n");
      return b.toString();
    }
  }
}
