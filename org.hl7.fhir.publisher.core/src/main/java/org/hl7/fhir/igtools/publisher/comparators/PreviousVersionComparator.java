package org.hl7.fhir.igtools.publisher.comparators;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.PastProcessHackerUtilities;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.igtools.publisher.loaders.PublisherLoader;
import org.hl7.fhir.igtools.publisher.modules.NullModule;
import org.hl7.fhir.r5.comparison.ComparisonRenderer;
import org.hl7.fhir.r5.comparison.ComparisonSession;
import org.hl7.fhir.r5.conformance.profile.ProfileKnowledgeProvider;
import org.hl7.fhir.r5.context.ILoggingService;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.DomainResource;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.i18n.RenderingI18nContext;
import org.hl7.fhir.utilities.npm.BasePackageCacheManager;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;


public class PreviousVersionComparator {

  public class ProfilePair {
    CanonicalResource left;
    CanonicalResource right;
    
    public ProfilePair(CanonicalResource left, CanonicalResource right) {
      super();
      this.left = left;
      this.right = right;
    }

    public CanonicalResource getLeft() {
      return left;
    }

    public CanonicalResource getRight() {
      return right;
    }

    public String getUrl() {
      return left != null ? left.getUrl() : right.getUrl();
    }
  }
  
  private class VersionInstance {
    private String version;
    private SimpleWorkerContext context;    
    private List<CanonicalResource> resources = new ArrayList<>();
    private String errMsg;
    private IniFile ini;
    private ProfileKnowledgeProvider pkp;
    private boolean annotate;
    
    public VersionInstance(String version, IniFile ini, boolean annotate) {
      super();
      this.version = version;
      this.ini = ini;
      this.annotate = annotate;
    }
  }

  private SimpleWorkerContext context;
  private String version;
  private String dstDir;
  private List<ProfilePair> comparisons = new ArrayList<>();
  private ProfileKnowledgeProvider newpkp;
  private String errMsg;
  private String pid;
  private List<VersionInstance> versionList = new ArrayList<>();
  private ILoggingService logger;
  private List<CanonicalResource> resources;
  private String lastName;
  private String lastUrl;
  private String businessVersion;
  private RenderingI18nContext i18n;
  private VersionInstance workingVersion;
  
  public PreviousVersionComparator(SimpleWorkerContext context, String version, String businessVersion, String rootDir, String dstDir, String canonical, ProfileKnowledgeProvider pkp, ILoggingService logger, List<String> versions, String versionToAnnotate, RenderingI18nContext i18n) {
    super();
        
    this.context = context;
    this.version = version;
    this.businessVersion = businessVersion;
    this.dstDir = dstDir;
    this.newpkp = pkp;
    this.logger = logger;
    this.i18n = i18n;
    try {
      if (businessVersion == null) {
         errMsg = "No Version Information Provided";
      } else {
        processVersions(canonical, versions, rootDir, versionToAnnotate);
      }
    } catch (Exception e) {
      errMsg = "Unable to find version history at "+canonical+" ("+e.getMessage()+")";
    }
  }

  private void processVersions(String canonical, List<String> versions, String rootDir, String versionToAnnotate) throws IOException {
    List<PackageListEntry> publishedVersions = null;
    for (String v : versions) {
      if (publishedVersions == null) {
        publishedVersions = fetchVersionHistory(canonical);
      }
      if (Utilities.existsInList(v, "{last}", "{current}")) {
        String last = null;
        String major = null;
        for (PackageListEntry o : publishedVersions) {
          if (last == null) {
            last = o.version();
            lastUrl = o.path();
            lastName = o.version();
          }
          if (o.current()) {
            major = o.version();
            lastUrl = o.path();
            lastName = o.sequence();                
          }
        }
        if ("{last}".equals(v)) {
          if(last == null) {
            throw new FHIRException("no {last} version found in package-list.json");
          } else if (!last.equals(businessVersion)) {
            VersionInstance vi = new VersionInstance(last, makeIni(rootDir, last), last.equals(versionToAnnotate) || "{last}".equals(versionToAnnotate));
            versionList.add(vi);
          }
        } 
        if ("{current}".equals(v)) {
          if(last == null) {
            throw new FHIRException("no {current} version found in package-list.json");
          } else if (!last.equals(businessVersion)) {
            versionList.add(new VersionInstance(major, makeIni(rootDir, major), major.equals(versionToAnnotate) || "{current}".equals(versionToAnnotate)));
          }
        } 
      } else {
        versionList.add(new VersionInstance(v, makeIni(rootDir, v), v.equals(versionToAnnotate)));
      }
    }
  }
    
  private IniFile makeIni(String rootDir, String v) throws IOException {
    File ini = new File(Utilities.path(rootDir, "url-map-v-"+v+".ini"));
    if (ini.exists()) {
      return new IniFile(new FileInputStream(ini));
    } else {
      return null;
    }
  }

  private List<PackageListEntry> fetchVersionHistory(String canonical) { 
    try {
      canonical = PastProcessHackerUtilities.actualUrl(canonical); // hack for old publishing process problems 
      String ppl = Utilities.pathURL(canonical, "package-list.json");
      logger.logMessage("Fetch "+ppl+" for version check");
      PackageList pl = PackageList.fromUrl(ppl);
      if (!canonical.equals(pl.canonical())) {
        throw new FHIRException("Mismatch canonical URL");
      } else if (!pl.hasPid()) {
        throw new FHIRException("Package ID not specified in package-list.json");        
      } else {
        pid = pl.pid();
        return pl.versions();
      }
    } catch (Exception e) {
      throw new FHIRException("Problem #1 with package-list.json at "+canonical+": "+e.getMessage(), e);
    }
  }


  public void startChecks(ImplementationGuide ig) {
    if (errMsg == null && pid != null && businessVersion != null) {
      resources = new ArrayList<>();
      for (VersionInstance vi : versionList) {
        String filename = "";
        try {
          vi.resources = new ArrayList<>();
          BasePackageCacheManager pcm = new FilesystemPackageCacheManager.Builder().build();
          NpmPackage current = pcm.loadPackage(pid, vi.version);
          for (String id : current.listResources("StructureDefinition", "ValueSet", "CodeSystem")) {
            filename = id;
            CanonicalResource curr = (CanonicalResource) loadResourceFromPackage(current, id, current.fhirVersion());
            if (curr != null) {
              curr.setWebPath(Utilities.pathURL(current.getWebLocation(), curr.fhirType()+"-"+curr.getId()+".html")); // to do - actually refactor to use the correct algorithm
              vi.resources.add(curr);
            }
          }
          NpmPackage core = pcm.loadPackage(VersionUtilities.packageForVersion(current.fhirVersion()), VersionUtilities.getCurrentVersion(current.fhirVersion()));
          vi.context = new SimpleWorkerContext.SimpleWorkerContextBuilder().withTerminologyCachePath(Utilities.path(context.getTxCache().getFolder(), vi.version)).fromPackage(core, new PublisherLoader(core, SpecMapManager.fromPackage(core), core.getWebLocation(), null).makeLoader(), true);
          //vi.context.initTS();
          vi.context.connectToTSServer(context.getTxClientManager().getFactory(), context.getTxClientManager().getMasterClient(), false);
          vi.context.setAllowLoadingDuplicates(true);
          vi.context.setExpansionParameters(context.getExpansionParameters());
          vi.context.setUcumService(context.getUcumService());
          vi.context.setLocale(context.getLocale());
          vi.context.setLogger(context.getLogger());
          vi.context.loadFromPackageAndDependencies(current, new PublisherLoader(current, SpecMapManager.fromPackage(current), current.getWebLocation(), null).makeLoader(), pcm);
          vi.pkp = new IGKnowledgeProvider(vi.context, current.getWebLocation(), current.canonical(), null, null, false, null, null, null, null, new NullModule());
        } catch (Exception e) {
          vi.errMsg = "Unable to find load package "+pid+"#"+vi.version+" ("+e.getMessage()+" on file "+filename+")";
          e.printStackTrace();
        }
      }
    }
  }

  private Resource loadResourceFromPackage(NpmPackage uscore, String filename, String version) throws FHIRException, IOException {
    InputStream s = uscore.loadResource(filename);
    if (VersionUtilities.isR3Ver(version)) {
      return VersionConvertorFactory_30_50.convertResource(new org.hl7.fhir.dstu3.formats.JsonParser().parse(s), new BaseAdvisor_30_50(false));
    } else if (VersionUtilities.isR4Ver(version)) {
      return VersionConvertorFactory_40_50.convertResource(new org.hl7.fhir.r4.formats.JsonParser().parse(s));
    } else if (VersionUtilities.isR5Plus(version)) {
      return new org.hl7.fhir.r5.formats.JsonParser().parse(s);
    } else {
      return null;
    }
  }

  public void finishChecks() throws IOException {
    if (errMsg == null && pid != null && businessVersion != null) {
      for (VersionInstance vi : versionList) {
        comparisons.clear();
        Set<String> set = new HashSet<>();
        for (CanonicalResource rl : vi.resources) {
          CanonicalResource t = findByUrl(rl.getUrl(), resources, vi.ini);
          comparisons.add(new ProfilePair(rl, t));
          set.add(rl.getUrl());      
        }
        for (CanonicalResource rr : resources) {
          String url = fixForIniMap(rr.getUrl(), vi.ini);
          if (!set.contains(url)) {
            CanonicalResource t = findByUrl(url, vi.resources, null);
            comparisons.add(new ProfilePair(t, rr));
          }
        }

        try {
          ComparisonSession session = new ComparisonSession(i18n, vi.context, context, "Comparison of v"+vi.version+" with this version", vi.pkp, newpkp);
          session.setAnnotate(vi.annotate);
          //    session.setDebug(true);
          for (ProfilePair c : comparisons) {
//            System.out.println("Version Comparison: compare "+vi.version+" to current for "+c.getUrl());
            session.compare(c.left, c.right);      
          }
          FileUtilities.createDirectory(Utilities.path(dstDir, "comparison-v"+vi.version));
          ComparisonRenderer cr = new ComparisonRenderer(vi.context, context, Utilities.path(dstDir, "comparison-v"+vi.version), session);
          cr.loadTemplates(context);
          cr.render("Version "+vi.version, "Current Build");
        } catch (Throwable e) {
          errMsg = "Current Version Comparison failed: "+e.getMessage();
          e.printStackTrace();
        }
      }
    }
  }

  private String fixForIniMap(String url, IniFile ini) {
    if (ini == null) {
      return url;
    }
    if (ini.hasProperty("urls", url)) {
      return ini.getStringProperty("urls", url);
    }
    return url;
  }

//
//  private void buildindexPage(String path) throws IOException {
//    StringBuilder b = new StringBuilder();
//    b.append("<table class=\"grid\">");
//    processResources("CodeSystem", b);
//    processResources("ValueSet", b);
//    processResources("StructureDefinition", b);
//    b.append("</table>\r\n");
//    TextFile.stringToFile(b.toString(), path);
//  }
//
//
//  private void processResources(String rt, StringBuilder b) {
//    List<String> urls = new ArrayList<>();
//    for (CanonicalResource cr : vi.resources) {
//      if (cr.fhirType().equals(rt)) {
//        urls.add(cr.getUrl());
//      }
//    }
//    for (CanonicalResource cr : resources) {
//      if (cr.fhirType().equals(rt)) {
//        if (!urls.contains(cr.getUrl())) {
//          urls.add(cr.getUrl());
//        }
//      }
//    }
//    Collections.sort(urls);
//    if (!urls.isEmpty()) {
//      b.append("<tr><td colspan=3><b>"+Utilities.pluralize(rt, urls.size())+"</b></td></tr>\r\n");
//      for (String url : urls) {
//        CanonicalResource crL = findByUrl(url, vi.resources);
//        CanonicalResource crR = findByUrl(url, resources);
//        b.append("<tr>");
//        b.append("<td>"+url+"</td>");
//        if (crL == null) {
//          b.append("<td>Added</td>");
//        } else if (crR == null) {
//          b.append("<td>Removed</td>");
//        } else {
//          b.append("<td>Changed</td>");
//        }
//        b.append("</tr>");        
//      }
//    }
//  }


  private CanonicalResource findByUrl(String url, List<CanonicalResource> list, IniFile ini) {
    for (CanonicalResource r : list) {
      if (fixForIniMap(r.getUrl(), ini).equals(url)) {
        return r;
      }
    }
    return null;
  }


  public void addOtherFiles(Set<String> otherFilesRun, String outputDir) throws IOException {
    for (VersionInstance vi : versionList) {
      otherFilesRun.add(Utilities.path(outputDir, "comparison-v"+vi.version));
    }
  }

  public String checkHtml() {
    if (errMsg != null) {
      return "Unable to compare with previous version: "+errMsg;
    } else {
      StringBuilder b = new StringBuilder();
      boolean first = true;
      for (VersionInstance vi : versionList) {
        if(first) first = false; else b.append("<br/>");
        b.append("<a href=\"comparison-v"+vi.version+"/index.html\">Comparison with version "+vi.version+"</a>");
      }
      return b.toString();
    }
  }

  public void check(CanonicalResource resource) {
    if (errMsg == null && pid != null) {
      resources.add(resource);
    }
  }

  public boolean hasLast() {
    return lastUrl != null;
  }

  public String getLastName() {
    return lastName;
  }

  public String getLastUrl() {
    return lastUrl;
  }


  public Set<String> deprecatedResourceIds() {
    if (lastVersion() == null) {
      return null;
    }
    Set<String> result = new HashSet<>();
    for (Resource r : lastVersion().resources) {
      if (r instanceof DomainResource) {
        DomainResource dr = (DomainResource) r;
        if (dr.hasExtension(ExtensionDefinitions.EXT_STANDARDS_STATUS)) {
          String st = dr.getExtensionString(ExtensionDefinitions.EXT_STANDARDS_STATUS);
          if ("deprecated".equals(st)) {
            result.add(r.fhirType()+"/"+r.getId());
          }
        }
      }
    }
    return result;
  }

  private VersionInstance lastVersion() {
    return versionList.isEmpty() ? null : versionList.get(0);
  }

  public Set<String> listAllResourceIds() {
    if (lastVersion() == null) {
      return null;
    }
    Set<String> result = new HashSet<>();
    for (Resource r : lastVersion().resources) {
      result.add(r.fhirType()+"/"+r.getId());
    }
    return result;
  }

  public List<CanonicalResource> listAllResources() {
    if (lastVersion() == null) {
      return null;
    } else {
      return lastVersion().resources;
    }
  }
}
