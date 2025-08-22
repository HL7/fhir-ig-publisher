package org.hl7.fhir.igtools.publisher.comparators;

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
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CapabilityStatement;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.i18n.RenderingI18nContext;
import org.hl7.fhir.utilities.npm.BasePackageCacheManager;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;


public class IpsComparator {

  public class ComparisonPair {
    CanonicalResource left;
    CanonicalResource right;
    
    public ComparisonPair(CanonicalResource left, CanonicalResource right) {
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
    private ProfileKnowledgeProvider pkp;
    
    public VersionInstance(String version) {
      super();
      this.version = version;
    }
  }

  private SimpleWorkerContext context;
  private String dstDir;
  private List<ComparisonPair> comparisons = new ArrayList<>();
  private ProfileKnowledgeProvider newpkp;
  private String errMsg;
  private String pid;
  private List<VersionInstance> versionList = new ArrayList<>();
  private ILoggingService logger;
  private List<CanonicalResource> resources;
  private String lastName;
  private String lastUrl;
  private RenderingI18nContext i18n;
  
  public IpsComparator(SimpleWorkerContext context, String rootDir, String dstDir, ProfileKnowledgeProvider pkp, ILoggingService logger, List<String> versions, RenderingI18nContext i18n) {
    super();
        
    this.context = context;
    this.dstDir = dstDir;
    this.newpkp = pkp;
    this.logger = logger;
    this.i18n = i18n;
    try {
      processVersions(versions, rootDir);
    } catch (Exception e) {
      errMsg = "Unable to find version history at http://hl7.org/fhir/uv/ips/ ("+e.getMessage()+")";
    }
  }

  private void processVersions(List<String> versions, String rootDir) throws IOException {
    String canonical = "http://hl7.org/fhir/uv/ips";
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
          if (last == null) {
            throw new FHIRException("no {last} version found in package-list.json");
          } else {
            versionList.add(new VersionInstance(last));
          }
        } 
        if ("{current}".equals(v)) {
          if(last == null) {
            throw new FHIRException("no {current} version found in package-list.json");
          } else {
            versionList.add(new VersionInstance(major));
          }
        } 
      } else {
        versionList.add(new VersionInstance(v));
      }
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
    if (errMsg == null && pid != null) {
      resources = new ArrayList<>();
      for (VersionInstance vi : versionList) {
        String filename = "";
        try {
          vi.resources = new ArrayList<>();
          BasePackageCacheManager pcm = new FilesystemPackageCacheManager.Builder().build();
          NpmPackage current = pcm.loadPackage(pid, vi.version);
          for (String id : current.listResources("StructureDefinition", "ValueSet", "CodeSystem", "CapabilityStatement")) {
            filename = id;
            CanonicalResource curr = (CanonicalResource) loadResourceFromPackage(current, id, current.fhirVersion());
            curr.setWebPath(Utilities.pathURL(current.getWebLocation(), curr.fhirType()+"-"+curr.getId()+".html")); // to do - actually refactor to use the correct algorithm
            if (curr != null) {
              vi.resources.add(curr);
            }
          }
          NpmPackage core = pcm.loadPackage(VersionUtilities.packageForVersion(current.fhirVersion()), VersionUtilities.getCurrentVersion(current.fhirVersion()));
          vi.context = new SimpleWorkerContext.SimpleWorkerContextBuilder().withTerminologyCachePath(Utilities.path(context.getTxCache().getFolder(), vi.version)).fromPackage(core,
                  new PublisherLoader(core, SpecMapManager.fromPackage(core), core.getWebLocation(), null, false).makeLoader(), true);
          //vi.context.initTS();
          vi.context.connectToTSServer(context.getTxClientManager().getFactory(), context.getTxClientManager().getMasterClient(), false);
          vi.context.setExpansionParameters(context.getExpansionParameters());
          vi.context.setUcumService(context.getUcumService());
          vi.context.setLocale(context.getLocale());
          vi.context.setLogger(context.getLogger());
          vi.context.loadFromPackageAndDependencies(current, new PublisherLoader(current, SpecMapManager.fromPackage(current), current.getWebLocation(), null, false).makeLoader(), pcm);
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
    if (errMsg == null && pid != null) {
      for (VersionInstance vi : versionList) {
        Set<String> set = new HashSet<>();
        for (CanonicalResource rr : resources) {
          CanonicalResource rl = findByType(rr, vi.resources);
          if (rl != null) {
            comparisons.add(new ComparisonPair(rl, rr));
          }
        }

        try {
          ComparisonSession session = new ComparisonSession(i18n, vi.context, context, "Comparison of v"+vi.version+" with this version", vi.pkp, newpkp);
          //    session.setDebug(true);
          for (ComparisonPair c : comparisons) {
//            System.out.println("Version Comparison: compare "+vi.version+" to current for "+c.getUrl());
            session.compare(c.left, c.right);      
          }
          FileUtilities.createDirectory(Utilities.path(dstDir, "ips-comparison-v"+vi.version));
          ComparisonRenderer cr = new ComparisonRenderer(vi.context, context, Utilities.path(dstDir, "ips-comparison-v"+vi.version), session);
          cr.loadTemplates(context);
          cr.render("Version "+vi.version, "Current Build");
        } catch (Throwable e) {
          errMsg = "Current Version Comparison failed: "+e.getMessage();
          e.printStackTrace();
        }
      }
    }
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


  private CanonicalResource findByType(CanonicalResource rr, List<CanonicalResource> list) {
    if (rr instanceof StructureDefinition) {
      String t = ((StructureDefinition) rr).getType();
      for (CanonicalResource r : list) {
        if (r instanceof StructureDefinition) {
          String tr = ((StructureDefinition) r).getType();
          if (tr.equals(t)) {
            return r;
          }
        }
      }      
    }
    if (rr instanceof CapabilityStatement) {
      String t = ((CapabilityStatement) rr).getRestFirstRep().getModeElement().asStringValue();
      for (CanonicalResource r : list) {
        if (r instanceof CapabilityStatement) {
          String tr = ((CapabilityStatement) r).getRestFirstRep().getModeElement().asStringValue();
          if (tr.equals(t)) {
            return r;
          }
        }
      }      
    }
    return null;
  }


  public void addOtherFiles(Set<String> otherFilesRun, String outputDir) throws IOException {
    for (VersionInstance vi : versionList) {
      otherFilesRun.add(Utilities.path(outputDir, "ips-comparison-v"+vi.version));
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
        b.append("<a href=\"ips-comparison-v"+vi.version+"/index.html\">Comparison with version "+vi.version+"</a>");
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

}
