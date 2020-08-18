package org.hl7.fhir.igtools.publisher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.convertors.VersionConvertor_30_50;
import org.hl7.fhir.convertors.VersionConvertor_40_50;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.realm.USRealmBusinessRules.ProfilePair;
import org.hl7.fhir.r5.comparison.ComparisonRenderer;
import org.hl7.fhir.r5.comparison.ComparisonSession;
import org.hl7.fhir.r5.conformance.ProfileUtilities.ProfileKnowledgeProvider;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.context.IWorkerContext.ILoggingService;
import org.hl7.fhir.r5.context.IWorkerContext.PackageVersion;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.UsageContext;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.Logger;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.Logger.LogMessageType;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.cache.BasePackageCacheManager;
import org.hl7.fhir.utilities.cache.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.cache.ToolsVersion;
import org.hl7.fhir.utilities.json.JSONUtil;
import org.hl7.fhir.utilities.validation.ValidationMessage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
    
    public VersionInstance(String version, IniFile ini) {
      super();
      this.version = version;
      this.ini = ini;
    }
  }

  private SimpleWorkerContext context;
  private String version;
  private String dstDir;
  private List<ProfilePair> comparisons = new ArrayList<>();
  private ProfileKnowledgeProvider pkp;
  private String errMsg;
  private String pid;
  private List<VersionInstance> versionList = new ArrayList<>();
  private ILoggingService logger;
  private List<CanonicalResource> resources;
  
  public PreviousVersionComparator(SimpleWorkerContext context, String version, String rootDir, String dstDir, String canonical, ProfileKnowledgeProvider pkp, ILoggingService logger, List<String> versions) {
    super();
        
    this.context = context;
    this.version = version;
    this.dstDir = dstDir;
    this.pkp = pkp;
    this.logger = logger;
    try {
      processVersions(canonical, versions, rootDir);
    } catch (Exception e) {
      errMsg = "Unable to find version history at "+canonical+" ("+e.getMessage()+")";
    }
  }

  private void processVersions(String canonical, List<String> versions, String rootDir) throws IOException {
    JsonArray publishedVersions = null;
    for (String v : versions) {
      if (Utilities.existsInList(v, "{last}", "{current}")) {
        if (publishedVersions == null) {
          publishedVersions = fetchVersionHistory(canonical);
        }
        String last = null;
        String major = null;
        for (JsonElement e : publishedVersions) {
          if (e instanceof JsonObject) {
            JsonObject o = e.getAsJsonObject();
            if (!"ci-build".equals(JSONUtil.str(o, "status"))) {
              if (last == null) {
                last = JSONUtil.str(o, "version");
              }
              if (o.has("current") && o.get("current").getAsBoolean()) {
                major = JSONUtil.str(o, "version");
              }
            }
          }
        }
        if ("{last}".equals(v)) {
          if(last == null) {
            throw new FHIRException("no {last} version found in package-list.json");
          } else {
            versionList.add(new VersionInstance(last, makeIni(rootDir, last)));
          }
        } 
        if ("{current}".equals(v)) {
          if(last == null) {
            throw new FHIRException("no {current} version found in package-list.json");
          } else {
            versionList.add(new VersionInstance(major, makeIni(rootDir, major)));
          }
        } 
      } else {
        versionList.add(new VersionInstance(v, makeIni(rootDir, v)));
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

  private JsonArray fetchVersionHistory(String canonical) { 
    try {
      String ppl = Utilities.pathURL(canonical, "package-list.json");
      logger.logMessage("Fetch "+ppl+" for version check");
      JsonObject pl = JSONUtil.fetchJson(ppl);
      if (!canonical.equals(JSONUtil.str(pl, "canonical"))) {
        throw new FHIRException("Mismatch canonical URL");
      } else if (!pl.has("package-id")) {
        throw new FHIRException("Package ID not specified in package-list.json");        
      } else {
        pid = JSONUtil.str(pl, "package-id");
        JsonArray arr = pl.getAsJsonArray("list");
        if (arr == null) {
          throw new FHIRException("Package-list has no history");
        } else {
          return arr;
        }
      }
    } catch (Exception e) {
      throw new FHIRException("Problem #1 with package-list.json at "+canonical+": "+e.getMessage(), e);
    }
  }


  public void startChecks(ImplementationGuide ig) {
    if (errMsg == null) {
      resources = new ArrayList<>();
      for (VersionInstance vi : versionList) {
        String filename = "";
        try {
          vi.resources = new ArrayList<>();
          BasePackageCacheManager pcm = new FilesystemPackageCacheManager(true, ToolsVersion.TOOLS_VERSION);
          NpmPackage current = pcm.loadPackage(pid, vi.version);
          for (String id : current.listResources("StructureDefinition", "ValueSet", "CodeSystem")) {
            filename = id;
            CanonicalResource curr = (CanonicalResource) loadResourceFromPackage(current, id, current.fhirVersion());
            curr.setUserData("path", Utilities.pathURL(current.getWebLocation(), curr.fhirType()+"-"+curr.getId()+".html")); // to do - actually refactor to use the correct algorithm
            if (curr != null) {
              vi.resources.add(curr);
            }
          }
          NpmPackage core = pcm.loadPackage(VersionUtilities.packageForVersion(current.fhirVersion()), VersionUtilities.getCurrentVersion(current.fhirVersion()));
          vi.context = SimpleWorkerContext.fromPackage(core, new PublisherLoader(core, SpecMapManager.fromPackage(core), core.getWebLocation(), null).makeLoader());
          vi.context.initTS(Utilities.path(context.getTxCache(), vi.version));
          vi.context.connectToTSServer(context.getTxClient(), null);
          vi.context.setExpansionProfile(context.getExpansionParameters());
          vi.context.setUcumService(context.getUcumService());
          vi.context.setLocale(context.getLocale());
          vi.context.setLogger(context.getLogger());
          vi.context.loadFromPackageAndDependencies(current, new PublisherLoader(current, SpecMapManager.fromPackage(current), current.getWebLocation(), null).makeLoader(), pcm);
        } catch (Exception e) {
          vi.errMsg = "Unable to find load package "+pid+"#"+vi.version+" ("+e.getMessage()+" on file "+filename+")";
          e.printStackTrace();
        }
      }
    }
  }

  private Resource loadResourceFromPackage(NpmPackage uscore, String filename, String version) throws FHIRFormatError, FHIRException, IOException {
    InputStream s = uscore.loadResource(filename);
    if (VersionUtilities.isR3Ver(version)) {
      return VersionConvertor_30_50.convertResource(new org.hl7.fhir.dstu3.formats.JsonParser().parse(s), true);
    } else if (VersionUtilities.isR4Ver(version)) {
      return VersionConvertor_40_50.convertResource(new org.hl7.fhir.r4.formats.JsonParser().parse(s));
    } else if (VersionUtilities.isR5Ver(version)) {
      return new org.hl7.fhir.r5.formats.JsonParser().parse(s);
    } else {
      return null;
    }
  }

  public void finishChecks() throws IOException {
    if (errMsg == null) {
      for (VersionInstance vi : versionList) {
        Set<String> set = new HashSet<>();
        for (CanonicalResource rl : vi.resources) {
          comparisons.add(new ProfilePair(rl, findByUrl(rl.getUrl(), resources, vi.ini)));
          set.add(rl.getUrl());      
        }
        for (CanonicalResource rr : resources) {
          String url = fixForIniMap(rr.getUrl(), vi.ini);
          if (!set.contains(url)) {
            comparisons.add(new ProfilePair(findByUrl(url, vi.resources, null), rr));
          }
        }

        try {
          ComparisonSession session = new ComparisonSession(vi.context, context, "Comparison of v"+vi.version+" with this version", pkp);
          //    session.setDebug(true);
          for (ProfilePair c : comparisons) {
            System.out.println("Version Comparison: compare "+vi.version+" to current for "+c.getUrl());
            session.compare(c.left, c.right);      
          }
          Utilities.createDirectory(Utilities.path(dstDir, "comparison-v"+vi.version));
          ComparisonRenderer cr = new ComparisonRenderer(vi.context, context, Utilities.path(dstDir, "comparison-v"+vi.version), session);
          cr.getTemplates().put("CodeSystem", new String(context.getBinaries().get("template-comparison-CodeSystem.html")));
          cr.getTemplates().put("ValueSet", new String(context.getBinaries().get("template-comparison-ValueSet.html")));
          cr.getTemplates().put("Profile", new String(context.getBinaries().get("template-comparison-Profile.html")));
          cr.getTemplates().put("Index", new String(context.getBinaries().get("template-comparison-index.html")));
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
    if (errMsg == null) {
      resources.add(resource);
    }
  }

}
