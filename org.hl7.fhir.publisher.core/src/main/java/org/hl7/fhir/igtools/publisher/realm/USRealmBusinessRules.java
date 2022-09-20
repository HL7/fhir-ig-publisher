package org.hl7.fhir.igtools.publisher.realm;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.convertors.conv30_50.VersionConvertor_30_50;
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.exceptions.DefinitionException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.I18nConstants;
import org.hl7.fhir.r5.comparison.ComparisonRenderer;
import org.hl7.fhir.r5.comparison.ComparisonSession;
import org.hl7.fhir.r5.conformance.ProfileUtilities.ProfileKnowledgeProvider;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.context.IWorkerContext.PackageVersion;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.Parameters;
import org.hl7.fhir.r5.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.utilities.SimpleHTTPClient;
import org.hl7.fhir.utilities.SimpleHTTPClient.HTTPResult;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.JsonTrackingParser;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.ToolsVersion;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class USRealmBusinessRules extends RealmBusinessRules {

  public class ProfilePair {
    List<ValidationMessage> errors;
    StructureDefinition local;
    StructureDefinition uscore;
    public ProfilePair(List<ValidationMessage> errors, StructureDefinition local, StructureDefinition uscore) {
      super();
      this.errors = errors;
      this.local = local;
      this.uscore = uscore;
    }
    public List<ValidationMessage> getErrors() {
      return errors;
    }
    public StructureDefinition getLocal() {
      return local;
    }
    public StructureDefinition getUscore() {
      return uscore;
    }
  }

  private static final Object US_SNOMED = "http://snomed.info/sct/731000124108";

  List<StructureDefinition> usCoreProfiles;
  private IWorkerContext context;
  private String version;
  private String dstDir;
  private List<StructureDefinition> problems = new ArrayList<>();
  private List<ProfilePair> comparisons = new ArrayList<>();

  private String name;
  private ProfileKnowledgeProvider pkp;

  public USRealmBusinessRules(IWorkerContext context, String version, String dstDir, String canonical, ProfileKnowledgeProvider pkp) {
    super();
    this.context = context;
    this.version = version;
    this.dstDir = dstDir;
    this.pkp = pkp;
  }


  @Override
  public void startChecks(ImplementationGuide ig) throws IOException {
    this.name = ig.present();
  }

  @Override
  public void checkSD(FetchedFile f, StructureDefinition sd) throws IOException {
    if (usCoreProfiles == null) {
      usCoreProfiles = new ArrayList<>();
      NpmPackage uscore = fetchLatestUSCore();
      if (uscore != null) {
        for (String id : uscore.listResources("StructureDefinition", "ValueSet", "CodeSystem")) {
          CanonicalResource usd = (CanonicalResource) loadResourceFromPackage(uscore, id);
          usd.setUserData("path", Utilities.pathURL(uscore.getWebLocation(), usd.fhirType()+"-"+usd.getId()+".html"));
          if (usd instanceof StructureDefinition) {
            usCoreProfiles.add((StructureDefinition) usd);
          }
          if (!context.hasResource(Resource.class, usd.getUrl())) {
            context.cacheResourceFromPackage(usd, new PackageVersion(uscore.id(), uscore.version(), uscore.dateAsDate()));
          }
        }
      }
    }
    List<String> types = new ArrayList<>();
    for (StructureDefinition usd : usCoreProfiles) {
      if (!types.contains(usd.getType()) && usd.getKind() == StructureDefinitionKind.RESOURCE) {
        types.add(usd.getType());
      }
    }
    if (Utilities.existsInList(sd.getType(), types)) {
      StructureDefinition t = sd;
      while (t != null && !existsInSDList(usCoreProfiles, t.getUrl())) {
        t = context.fetchResource(StructureDefinition.class, t.getBaseDefinition());
      }
      if (t == null) {
        problems.add(sd);
        StringBuilder b = new StringBuilder();
        ValidationMessage vm = new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"').baseDefinition", "US FHIR Usage rules require that all profiles on "+sd.getType()+
            (matches(usCoreProfiles, sd.getType()) > 1 ? " derive from one of the base US profiles" : " derive from the core US profile"),
            IssueSeverity.WARNING).setMessageId(I18nConstants.US_CORE_DERIVATION); 
        b.append(vm.getMessage());
        f.getErrors().add(vm);
        // actually, that should be an error, but US realm doesn't have a proper base, so we're going to report the differences against the base
        for (StructureDefinition candidate : candidateProfiles(sd.getType())) {
          comparisons.add(new ProfilePair(f.getErrors(), sd, candidate));
          b.append(". <a href=\"us-core-comparisons/sd-"+candidate.getId()+"-"+sd.getId()+".html\">Comparison with "+candidate.present()+"</a>");
        }
        vm.setHtml(b.toString());
      }
    }
  }

  private List<StructureDefinition> candidateProfiles(String type) {
    List<StructureDefinition> res = new ArrayList<>();
    for (StructureDefinition usd : usCoreProfiles) {
      if (usd.getType().equals(type)) {
        res.add(usd);
      }
    }
    return res;
  }

  private int matches(List<StructureDefinition> sdl, String type) {
    int i = 0;
    for (StructureDefinition sd : sdl) {
      if (type.equals(sd.getType())) {
        i++;
      }
    }
    return i;
  }

  private boolean existsInSDList(List<StructureDefinition> sdl, String url) {
    for (StructureDefinition sd : sdl) {
      if (url.equals(sd.getUrl())) {
        return true;
      }
    }
    return false;
  }

  private NpmPackage fetchLatestUSCore() throws IOException {
    JsonObject pl = fetchJson("https://hl7.org/fhir/us/core/package-list.json");
    for (JsonElement e : pl.getAsJsonArray("list")) {
      JsonObject v = (JsonObject) e;
      if (v.has("fhirversion") && VersionUtilities.versionsCompatible(version, v.get("fhirversion").getAsString())) {
        return new FilesystemPackageCacheManager(true, ToolsVersion.TOOLS_VERSION).loadPackage("hl7.fhir.us.core", v.get("version").getAsString());
      }
    }
    return null;
  }

  private Resource loadResourceFromPackage(NpmPackage uscore, String filename) throws FHIRException, IOException {
    InputStream s = uscore.loadResource(filename);
    if (VersionUtilities.isR3Ver(version)) {
      return VersionConvertorFactory_30_50.convertResource(new org.hl7.fhir.dstu3.formats.JsonParser().parse(s), new BaseAdvisor_30_50(false));
    } else if (VersionUtilities.isR4Ver(version)) {
      return VersionConvertorFactory_40_50.convertResource(new org.hl7.fhir.r4.formats.JsonParser().parse(s));
    } else {
      return null;
    }
  }

  private JsonObject fetchJson(String source) throws IOException  {
    SimpleHTTPClient http = new SimpleHTTPClient();
    HTTPResult res = http.get(source+"?nocache=" + System.currentTimeMillis());
    res.checkThrowException();
    return JsonTrackingParser.parseJson(res.getContent());
  }

  @Override
  public void checkCR(FetchedFile f, CanonicalResource resource) {
    // no checks    
  }

  public void addOtherFiles(Set<String> otherFilesRun, String outputDir) throws IOException {
    otherFilesRun.add(Utilities.path(outputDir, "us-core-comparisons"));
  }

  @Override
  public void finishChecks() throws DefinitionException, FHIRFormatError, IOException {
    try {
      ComparisonSession session = new ComparisonSession(context, context, "Comparison of "+name+" with US-Core", pkp, pkp);
      //    session.setDebug(true);
      for (ProfilePair c : comparisons) {
        System.out.println("US Core Comparison: compare "+c.local+" to "+c.uscore);
        session.compare(c.uscore, c.local);      
      }
      Utilities.createDirectory(Utilities.path(dstDir, "us-core-comparisons"));
      ComparisonRenderer cr = new ComparisonRenderer(context, context, Utilities.path(dstDir, "us-core-comparisons"), session);
      cr.getTemplates().put("CodeSystem", new String(context.getBinaryForKey("template-comparison-CodeSystem.html")));
      cr.getTemplates().put("ValueSet", new String(context.getBinaryForKey("template-comparison-ValueSet.html")));
      cr.getTemplates().put("Profile", new String(context.getBinaryForKey("template-comparison-Profile.html")));
      cr.getTemplates().put("CapabilityStatement", new String(context.getBinaryForKey("template-comparison-CapabilityStatement.html")));
      cr.getTemplates().put("Index", new String(context.getBinaryForKey("template-comparison-index.html")));
      cr.setPreamble(renderProblems());
      cr.render("US Realm", "Current Build");
      System.out.println("US Core Comparisons Finished");
    } catch (Throwable e) {
      System.out.println("US Core Comparison failed: "+e.getMessage());
      e.printStackTrace();
    }
  }

  private String renderProblems() {
    if (problems == null || problems.isEmpty()) {
      return "";
    } else {
      StringBuilder b = new StringBuilder();
      b.append("<p>The following Profiles do not derive from US Core, and should be reviewed with the US Realm Committee:</p>\r\n");
      b.append("<ul>\r\n");
      for (StructureDefinition s : problems) {
        b.append("<li><a href=\"../"+s.getUserString("path")+"\">"+s.present()+"</a></li>\r\n");
      }
      b.append("</ul>\r\n");
      return b.toString();
    }
  }


  @Override
  public String checkHtml() {
    String sct;
    if (!context.getCodeSystemsUsed().contains("http://snomed.info/sct")) {
      sct = "<p>Snomed: The IG doesn't use SNOMED CT</p>";      
    } else {
      String sctVer = getSnomedVersion(context.getExpansionParameters());
      if (US_SNOMED.equals(sctVer)) {
        sct = "<p>Snomed: The IG specifies the US edition of SNOMED CT <b>&#10003;</b> </p>";
      } else {
        sct = "<p>Snomed: <span style=\"background-color: #ffcccc\">The IG does not specify the US edition of SNOMED CT version in the parameters (<code>"+US_SNOMED+"</code>)</span></p>";
      }
    }
    if (problems == null || problems.isEmpty()) {
      return sct+"<p>Profiles: All OK</p>";
    } else {
      return sct+"<p><a href=\"us-core-comparisons/index.html\">"+Integer.toString(problems.size())+" Profiles not based on US Core</a></p>";      
    }
  }
 

  private String getSnomedVersion(Parameters params) {
    for (ParametersParameterComponent p : params.getParameter()) {
      if ("system-version".equals(p.getName()) && p.hasValue() && p.getValue().hasPrimitiveValue() && p.getValue().primitiveValue().startsWith("http://snomed.info/sct|")) {
        return p.getValue().primitiveValue().substring("http://snomed.info/sct|".length());
      }
    }
    return null;
  }


  @Override
  public String checkText() {
    if (problems == null || problems.isEmpty()) {
      return "All OK";
    } else {
      return Integer.toString(problems.size())+" Profiles not based on US Core";
    }
  }


  @Override
  public String code() {
    return "US";
  }
}
