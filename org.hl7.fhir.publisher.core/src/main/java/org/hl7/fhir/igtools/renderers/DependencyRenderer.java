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

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_14_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_43_50;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.DependencyAnalyser;
import org.hl7.fhir.igtools.publisher.DependencyAnalyser.ArtifactDependency;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.igtools.templates.TemplateManager;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideGlobalComponent;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.tools.ExtensionConstants;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.npm.BasePackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageHacker;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.Cell;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.Row;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.TableModel;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class DependencyRenderer {

  public class PackageUsageInfo {
    private String comment;

    public String getComment() {
      return comment;
    }

    public void setComment(String comment) {
      this.comment = comment;
    }
    
  }

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
  private Map<String, PackageUsageInfo> ids = new HashMap<>();
  private String fhirVersion;
  private String npmName;
  private TemplateManager templateManager;
  private Map<String, PackageList> packageListCache = new HashMap<>();
  private List<DependencyAnalyser.ArtifactDependency> dependencies;
  private List<GlobalProfile> globals = new ArrayList<>();
  private Set<String> globalPackages = new HashSet<>();
  private IWorkerContext context;
  private MarkDownProcessor mdEngine;
  private RenderingContext rc;
  private List<SpecMapManager> specMaps;
  private Map<String, PackageInfo> packagesByName;
  
  public DependencyRenderer(BasePackageCacheManager pcm, String dstFolder, String npmName, TemplateManager templateManager,
      List<DependencyAnalyser.ArtifactDependency> dependencies, IWorkerContext context, MarkDownProcessor mdEngine, RenderingContext rc, List<SpecMapManager> specMaps) {
    super();
    this.pcm = pcm;
    this.dstFolder = dstFolder;
    this.npmName = npmName;
    this.templateManager = templateManager;
    this.dependencies = dependencies;
    this.context = context;
    this.mdEngine = mdEngine;
    this.rc = rc;
    this.specMaps = specMaps;
  }

  private class PackageVersionInfo {
    private NpmPackage p;
    @Getter
    @Setter
    private boolean direct;
    @Setter
    private String reason;
    private String parent;
    public PackageVersionInfo(NpmPackage p, boolean direct, String reason, String parent) {
      this.p = p;
      this.direct = direct;
      this.reason = reason;
      this.parent = parent;
    }

    public boolean hasReason() {
      return getReason()!=null;
    }

    public String getReason() {
      if (reason!=null)
        return mdEngine.process(reason, "Dependency Reason for " + p.title());
      else if (parent != null)
        return "Imported by " + Utilities.escapeXml(parent) + " (and potentially others)";
      else
        return null;
    }
  }

  private class PackageInfo {
    private NpmPackage p;
    private boolean direct;
    private Map<String, PackageVersionInfo> versions = new HashMap<String, PackageVersionInfo>();
    public PackageInfo(NpmPackage p, String reason, boolean direct, String parent) {
      this.p = p;
      this.direct = direct;
      versions = new HashMap<String, PackageVersionInfo>();
      PackageVersionInfo v = new PackageVersionInfo(p, direct, reason, parent);
      versions.put(p.version(), v);
    }

    // Returns true if the version wasn't already present
    protected boolean addVersion (NpmPackage p, String reason, boolean direct, String parent) {
      if (versions.containsKey(p.version())) {
        if (direct) {
          this.direct = direct;
          PackageVersionInfo v = versions.get(p.version());
          v.setDirect(direct);
          v.setReason(reason);
        }
        return false;
      }
      this.direct = this.direct || direct;
      PackageVersionInfo v = new PackageVersionInfo(p, direct, reason, parent);
      versions.put(p.version(), v);
      return true;
    }
  }

  public String renderNonTech(ImplementationGuide ig) throws FHIRException, IOException {
    packagesByName = new HashMap<String, PackageInfo>();
    for (ImplementationGuideDependsOnComponent d : ig.getDependsOn()) {
      NpmPackage p = resolve(d);
      boolean dloaded = isLoaded(p);
      if (dloaded) {
        addPackage(p, d.hasReason() ? d.getReason() : ExtensionUtilities.readStringExtension(d, ExtensionDefinitions.EXT_IGDEP_COMMENT), true);
      }
    }

    if (packagesByName.isEmpty())
      return "";
    else {
      StringBuilder b = new StringBuilder();
      b.append("<table style=\"border: 1px #F0F0F0 solid;\"><thead><tr style=\"border: 1px #F0F0F0 solid; font-size: 11px; font-family: verdana; vertical-align: top;\"><th><b>Implementation Guide</b></th><th><b>Version(s)</b></th><th><b>Reason</b></th></tr></thead><tbody>");
      List<String> names = new ArrayList<String>(packagesByName.keySet());
      Collections.sort(names);
      int lineCount = 1;
      for (String name: names) {
        PackageInfo info = packagesByName.get(name);
        String newRow = "<tr style=\"font-size: 11px; font-family: verdana; vertical-align: top; background-color: " + ((lineCount % 2 == 0) ? "#F7F7F7" : "white") + "\"><td";
        b.append(newRow);
        if (info.versions.size()!=1)
          b.append(" rowspan=\"" + info.versions.size() + "\"");
        b.append("><span class=\"copy-text\" title=\"canonical: " + info.p.canonical() + "\"><a style=\"font-size: 11px; font-family: verdana; font-weight:" + (info.direct ? "bold" : "normal") + "\"");
        b.append(" href=\"" + info.p.url() + "\">" + Utilities.escapeXml(name) + "</a><button class=\"btn-copy\" title=\"Click to copy URL\" data-clipboard-text=\"" + info.p.canonical() + "\"/></span></td><td>");
        boolean first = true;
        List<String> versions = new ArrayList<String>(info.versions.keySet());
        Collections.sort(versions, Collections.reverseOrder());
        for (String version: versions) {
          PackageVersionInfo verInfo = info.versions.get(version);
          if (!first)
            b.append(newRow + ">");
          b.append("<span class=\"copy-text\" title=\"package: " + verInfo.p.id() + "#" + version + "\"><a style=\"font-size: 11px; font-family: verdana; font-weight: " + (verInfo.direct ? "bold" : "normal") + "\"");
          b.append(" href=\"https://simplifier.net/packages/" + info.p.name() + "/" + version + "\">" + version + "</a><button class=\"btn-copy\" title=\"Click to copy package\" data-clipboard-text=\"" + verInfo.p.id() + "#" + version + "\"/></span>");
          b.append("</td><td" + (verInfo.direct ? "" : " style=\"font-style: italic;\"") + ">" + (verInfo.hasReason() ? verInfo.getReason() : "") + "</td></tr>");

          first = false;
        }
        lineCount++;
        first = true;
      }
      b.append("</tbody></table>");

      return b.toString();
    }
  }

  private void addPackage(NpmPackage p, String reason, boolean direct) {
    addPackage(p, reason, direct, null);
  }
  private void addPackage(NpmPackage p, String reason, boolean direct, String parent) {
    PackageInfo packageInfo;
    String title;
    if (p.getNpm().has("title"))
      title = p.title();
    else if (p.name().endsWith(".vsac"))
      title = "Value Set Authority Center (VSAC)";
    else if (p.name().endsWith(".phinvads"))
      title = "Public Health Information Network Vocabulary Access and Distribution System (PHIN VADS)";
    else
      title = p.name();
    if (title.contains("Wrapper)"))
      title = title.substring(0, title.indexOf("(")).trim();
    if (title.endsWith("Implementation Guide"))
      title = title.substring(0, title.length()-20).trim();
    if (packagesByName.containsKey(title)) {
      packageInfo = packagesByName.get(title);
      if (!packageInfo.addVersion(p, reason, direct, parent))
        return;
    } else {
      packageInfo = new PackageInfo(p, reason, direct, parent);
      packagesByName.put(title, packageInfo);
    }

    for (String d: p.dependencies()) {
      if (isLoaded(d)) {
        String id = d.substring(0, d.indexOf("#"));
        String version = d.substring(d.indexOf("#") + 1);
        try {
          NpmPackage dp = resolve(id, version);
          addPackage(dp, null, false, title);
        } catch (Exception e) {
          // Do nothing - this'll be dealt with elsewhere
        }
      }
    }
  }

  public String render(ImplementationGuide ig, boolean QA, boolean details, boolean first) throws FHIRException, IOException {
    boolean hasDesc = false;
    for (ImplementationGuideDependsOnComponent d : ig.getDependsOn()) {
      hasDesc = hasDesc || d.hasExtension(ExtensionDefinitions.EXT_IGDEP_COMMENT) || d.hasReason();
    }
    
    HierarchicalTableGenerator gen = new HierarchicalTableGenerator(rc, dstFolder, true, true, "dep");
    TableModel model = createTable(gen, QA, hasDesc);
  
    
    String realm = determineRealmForIg(ig.getPackageId());
    Set<String> processed = new HashSet<>();
    Set<String> listed = new HashSet<>();

    StringBuilder b = new StringBuilder();
    
    Row row = addBaseRow(gen, model, ig, QA, hasDesc);
    for (ImplementationGuideDependsOnComponent d : ig.getDependsOn()) {
      try {
        NpmPackage p = resolve(d);
        boolean dloaded = isLoaded(p);
        if (QA || dloaded) {
          addPackageRow(gen, row.getSubRows(), p, d.getVersion(), realm, QA, b, d.hasReason() ? d.getReason() : ExtensionUtilities.readStringExtension(d, ExtensionDefinitions.EXT_IGDEP_COMMENT), hasDesc, processed, listed, dloaded, false);
        }
      } catch (Exception e) {
        e.printStackTrace();
        addErrorRow(gen, row.getSubRows(), d.getPackageId(), d.getVersion(), d.getUri(), null, e.getMessage(), QA, hasDesc);
      }
    }
    for (Extension ext : ig.getDefinition().getExtensionsByUrl(ExtensionConstants.EXT_IGINTERNAL_DEPENDENCY)) {
      String pidv = ext.getValue().primitiveValue();
      if (pidv != null && pidv.contains("#")) {
        String pid = pidv.substring(0, pidv.indexOf("#"));
        String ver = pidv.substring(pidv.indexOf("#") + 1);
        try {
          NpmPackage p = resolve(pid, ver);
          boolean dloaded = isLoaded(p);
          if (QA || dloaded) {
            addPackageRow(gen, row.getSubRows(), p, ver, realm, QA, b, "for example references", hasDesc, processed, listed, dloaded, true);
          }
        } catch (Exception e) {
          e.printStackTrace();
          addErrorRow(gen, row.getSubRows(), pid, ver, null, null, e.getMessage(), QA, hasDesc);
        }
      }
    }
    if (QA) {
      
    } else if (first) {
      checkGlobals(ig, null);
    }
    // create the table
    // add the rows 
    // render it       
    XhtmlNode x = gen.generate(model, dstFolder, 0, null);
    XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
    div.add(x);
    if (QA) {
      div.add(makeTemplateTable(ig));
    }
    return new XhtmlComposer(false).compose(div)+(details ? b.toString() : "");
  }

  private XhtmlNode makeTemplateTable(ImplementationGuide ig) {
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

  private void addPackageRow(HierarchicalTableGenerator gen, List<Row> rows, NpmPackage npm, String originalVersion, String realm, boolean QA, StringBuilder b, String desc, boolean hasDesc, Set<String> processed, Set<String> listed, boolean loaded, boolean internal) throws FHIRException, IOException {
    if (!npm.isCore() && (QA || !listed.contains(npm.name()+"#"+npm.version()))) {
      listed.add(npm.name()+"#"+npm.version());
      String idv = npm.name()+"#"+npm.version();
      PackageUsageInfo pui = ids.get(idv);
      boolean isNew = pui == null;
      if (isNew) {
        pui = new PackageUsageInfo();
        ids.put(idv, pui);
      }
      String comment = "";
      if (!isNew) {
        comment = pui.getComment() != null ? pui.getComment()+" (as above)" : "";
      } else if (!VersionUtilities.versionMatches(npm.fhirVersion(), fhirVersion)) {
        comment = "FHIR Version Mismatch";
      } else if ("current".equals(npm.version())) {
        comment = "Cannot be published with a dependency on a current build version";
      } else if (!npm.version().equals(originalVersion)) {
        comment = "Matched to latest patch release ("+originalVersion+"->"+npm.version()+")";
      } else if (realm != null) {
        String drealm = determineRealmForIg(npm.name());
        if (drealm != null) {
          if ("uv".equals(realm)) {
            if (!"uv".equals(drealm)) {
              if (!internal) {
                comment = "An international realm (uv) publication cannot depend on a realm specific guide (" + drealm + ")";
              }
            }
          } else if (!"uv".equals(drealm) && !realm.equals(drealm)) {
            comment = "An realm publication for "+realm+" should not depend on a realm specific guide from ("+drealm+")";
          }
        }
      }
      if (internal) {
        if (!Utilities.noString(comment)) {
          comment = "Internal Dependency. "+comment;
        } else {
          comment = "Internal Dependency";
        }
      }
      if (isNew) {
        pui.setComment(comment);
      }
      Row row = addRow(gen, rows, npm.name(), npm.title(), npm.version(), getVersionState(npm.name(), npm.version(), npm.canonical()), getLatestVersion(npm.name(), npm.canonical()), "current".equals(npm.version()), npm.fhirVersion(), 
          !VersionUtilities.versionMatches(npm.fhirVersion(), fhirVersion), npm.canonical(), PackageHacker.fixPackageUrl(npm.getWebLocation()), comment, desc, QA, hasDesc, loaded, internal);
      if (isNew && !internal) {
        for (String d : npm.dependencies()) {
          boolean dloaded = isLoaded(d);
          if (QA || dloaded) {
            String id = d.substring(0, d.indexOf("#"));
            String version = d.substring(d.indexOf("#")+1);
            try {
              NpmPackage p = resolve(id, version);
              addPackageRow(gen, row.getSubRows(), p, d.substring(d.indexOf("#")+1), realm, QA, b, null, hasDesc, processed, listed, dloaded, internal);
            } catch (Exception e) {
              addErrorRow(gen, row.getSubRows(), id, version, null, null, e.getMessage(), QA, hasDesc);
            }
          }
        }
      }
    }
    if (!QA) {
      checkGlobals(npm);
      boolean firstP = true;
      if (!npm.isCore() && !npm.isTx() && npm.description() != null && !processed.contains(npm.name()+"#"+npm.version())) {
        processed.add(npm.name()+"#"+npm.version());
        if (firstP) {
          firstP = false;
          b.append("<table class=\"grid\"><tr><td>");      
        } else {
          b.append("</td></tr><tr><td>");                
        }
        String n = (npm.name()+"#"+npm.version());
        b.append("<p><b>Package ");
        b.append(n);
        b.append("</b></p>\r\n");
        if (npm.description().contains("<br") || npm.description().contains("<li") || npm.description().contains("<p") || npm.description().contains("<td") || npm.description().contains("<span")) {
          if (npm.description().contains("<p ") || npm.description().contains("<p>")) {
            b.append(npm.description());          
          } else {
            b.append("<p>"+npm.description()+"</p>");                      
          }
        } else {
          b.append(mdEngine.process(npm.description(), "npm.description"));
        }

        StringBuilder b2 = new StringBuilder();
        b2.append("\r\n<p><b>Dependencies</b></p>\r\n");
        boolean first = true;
        for (ArtifactDependency ad : dependencies) {
          String t = ad.getTarget().getUserString("package");
          if (n.equals(t)) {
            if (first) {
              b2.append("<ul>\r\n");
              first = false;
            }
            b2.append("<li><a href=\"");
            b2.append(ad.getSource().getWebPath());
            b2.append("\">");
            if (ad.getSource() instanceof CanonicalResource) {
              b2.append(Utilities.escapeXml(((CanonicalResource) ad.getSource()).present()));
            } else {
              b2.append(ad.getSource().fhirType()+"/"+ad.getSource().getId());          
            }
            b2.append("</a> ");
            b2.append(ad.getKind());
            b2.append(" <a href=\"");
            b2.append(ad.getTarget().getWebPath());
            b2.append("\">");
            if (ad.getTarget() instanceof CanonicalResource) {
              b2.append(Utilities.escapeXml(((CanonicalResource) ad.getTarget()).present()));
            } else {
              b2.append(ad.getTarget().fhirType()+"/"+ad.getTarget().getId());          
            }
            b2.append("</a></li>\r\n");
          } 
        }
        if (!first) {
          b2.append("</ul>\r\n");
          b.append(b2.toString());
        }
      }
      if (!firstP) {
        b.append("</td></tr></table>");      
      }
    }
  }


  private boolean isLoaded(NpmPackage p) {
    return isLoaded(p.vid());
  }
  
  private boolean isLoaded(String d) {
    for (SpecMapManager smm : specMaps) {
      if (smm.getNpmVId().equals(d)) {
        return true;
      }
    }
    return false;
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
    String key = ig.getVersionedUrl();
    if (!globalPackages.contains(key)) {
      globalPackages.add(key);
      for (ImplementationGuideGlobalComponent g : ig.getGlobal()) {
        StructureDefinition sd = context.fetchResource(StructureDefinition.class, g.getProfile());
        globals.add(new GlobalProfile(npm, ig, g.getType(), g.getProfile(), sd));
      }    
    }
  }

  private String getLatestVersion(String name, String canonical) {
    PackageList pl = fetchPackageList(name, canonical);
    if (pl == null) {
      return null;
    }
    for (PackageListEntry v : pl.versions()) {
      if (v.current()) {// this is the current official release
          return v.version();
      }
    }      
    return null;
  }

  private VersionState getVersionState(String name, String version, String canonical) {
    PackageList pl = fetchPackageList(name, canonical);
    if (pl == null) {
      return VersionState.VERSION_NO_LIST;
    }
    boolean latestInterim = true;
    for (PackageListEntry v : pl.versions()) {
      if (version.equals(v.version())) {
        if (v.current()) {// this is the current official release
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
    return VersionState.VERSION_UNKNOWN;
  }

  private PackageList fetchPackageList(String name, String canonical) {
    if (packageListCache .containsKey(name)) {
      return packageListCache.get(name);
    }
    PackageList pl;
    try {
      pl = PackageList.fromUrl(Utilities.pathURL(canonical, "package-list.json")); 
          
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
    this.fhirVersion = fver;
    String canonical = ig.getUrl();
    String web = ig.getManifest().getRendering();
    if (canonical.contains("/ImplementationGuide/")) {
      canonical = canonical.substring(0, canonical.indexOf("/ImplementationGuide/"));
    }
    String comment = null;
    if (!id.equals(npmName)) {
      comment = "Expected Package Id is "+npmName;
    } else if (id.startsWith("hl7") && !id.startsWith("hl7.cda.") && !id.startsWith("hl7.fhir.") && !id.startsWith("hl7.v2.") && !id.startsWith("hl7.ehrs.")) {
      comment = "HL7 Packages must have an id that starts with hl7.cda., hl7.fhir., hl7.v2., or hl7.ehrs.";
    }
    Row row = addRow(gen, model.getRows(), id,  ig.present(), ver, null, null, false, fver, false, canonical, web, comment, null, QA, hasDesc, true, false);
    if (QA && comment != null) {
      row.getCells().get(5).addStyle("background-color: #ffcccc");
    }
    
    return row;
  }

  private Row addRow(HierarchicalTableGenerator gen, List<Row> rows, String id, String title, String ver, VersionState verState, String latestVer, boolean verError, String fver, boolean fverError, String canonical, String web, String problems, String desc, boolean QA, boolean hasDesc, boolean loaded, boolean internal) {
    Row row = gen.new Row();
    rows.add(row);
    if (!loaded) {
      row.setIcon("icon-fhir-16-grey.png", "NPM Package (not loaded)");
    } else if (internal) {
      row.setIcon("icon-fhir-16-grey.png", "NPM Package IG Dependency");
      row.setColor("#fcf7c0");
    } else {
      row.setIcon("icon-fhir-16.png", "NPM Package");
    }
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
      row.getCells().add(gen.new Cell(null, web, makeTitle(title, id), "Canonical: "+canonical, null));
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

  private String makeTitle(String title, String id) {
    if (title != null) {
      return title;
    }
    switch (id) {
    case "us.nlm.vsac" : return "VSAC";
    case "us.cdc.phinvads" : return "PHINVads";
    default: return id;
    }
  }

  private void addErrorRow(HierarchicalTableGenerator gen, List<Row> rows, String id, String ver, String uri, String web, String message, boolean QA, boolean hasDesc) {
    Row row = gen.new Row();
    rows.add(row);
    row.setIcon("icon-fhir-16.png", "NPM Package");
    if (!QA) {
      row.getCells().add(gen.new Cell(null, null, null, null, null));
    }
    row.getCells().add(gen.new Cell(null, null, id, null, null));
    if (QA) {
      row.getCells().add(gen.new Cell(null, null, ver, null, null));
    }
    row.getCells().add(gen.new Cell(null, null, null, null, null));
    if (QA) {
      row.getCells().add(gen.new Cell(null, null, uri, null, null));
      row.getCells().add(gen.new Cell(null, null, web, null, null));
      row.getCells().add(gen.new Cell(null, null, message, null, null));
    } else if (hasDesc) {
      row.getCells().add(gen.new Cell(null, null, message, null, null));
    }
    row.setColor("#ffcccc");
  }

  private TableModel createTable(HierarchicalTableGenerator gen, boolean QA, boolean hasDesc) {
    TableModel model = gen.new TableModel("dep", true);
    
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
          b.append(sd.getWebPath());
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
          b.append(gp.profile.getWebPath());
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
