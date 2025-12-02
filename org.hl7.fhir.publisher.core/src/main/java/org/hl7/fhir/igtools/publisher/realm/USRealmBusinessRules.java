package org.hl7.fhir.igtools.publisher.realm;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.exceptions.DefinitionException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.PublisherMessageIds;
import org.hl7.fhir.r5.comparison.ComparisonRenderer;
import org.hl7.fhir.r5.comparison.ComparisonSession;
import org.hl7.fhir.r5.conformance.profile.ProfileKnowledgeProvider;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.PackageInformation;
import org.hl7.fhir.r5.model.Parameters;
import org.hl7.fhir.r5.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.http.HTTPResult;
import org.hl7.fhir.utilities.http.ManagedWebAccess;
import org.hl7.fhir.utilities.i18n.RenderingI18nContext;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;

/*
// todo:
// any us realm owned by public health
// jursdiction = US
// publisher = HL7 International / Public Health
//


 */
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
  List<StructureDefinition> usPHProfiles;
  private IWorkerContext context;
  private String version;
  private String dstDir;
  private List<StructureDefinition> problems = new ArrayList<>();
  private List<ProfilePair> comparisonsCore = new ArrayList<>();
  private List<ProfilePair> comparisonsPH = new ArrayList<>();

  private String name;
  private ProfileKnowledgeProvider pkp;

  private RenderingI18nContext i18n;

  private boolean isPH;

  public USRealmBusinessRules(IWorkerContext context, String version, String dstDir, String canonical,
                              ProfileKnowledgeProvider pkp, RenderingI18nContext i18n, ImplementationGuide ig) {
    super();
    this.context = context;
    this.version = version;
    this.dstDir = dstDir;
    this.pkp = pkp;
    this.i18n = i18n;
    isPH = false; // ig.getPublisher() != null && ig.getPublisher().contains("Public Health");
  }


  @Override
  public void startChecks(ImplementationGuide ig) throws IOException {
    this.name = ig.present();
  }

  @Override
  public void checkSD(FetchedFile f, StructureDefinition sd) throws IOException {
    checkUSCoreLoaded();
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
            (matches(usCoreProfiles, sd.getType()) > 1 ? " derive from one of the base US profiles" : " derive from the core US profile")+". See https://confluence.hl7.org/display/CGP/US+Core+Variance+Request+Process",
            IssueSeverity.WARNING).setMessageId(PublisherMessageIds.US_CORE_DERIVATION); 
        b.append(vm.getMessage());
        f.getErrors().add(vm);
        // actually, that should be an error, but US realm doesn't have a proper base, so we're going to report the differences against the base
        for (StructureDefinition candidate : candidateProfiles(sd.getType())) {
          comparisonsCore.add(new ProfilePair(f.getErrors(), sd, candidate));
          b.append(". <a href=\"us-core-comparisons/sd-"+candidate.getId()+"-"+sd.getId()+".html\">Comparison with "+candidate.present()+"</a>");
        }
        vm.setHtml(b.toString());
      }
    }

    if (isPH) {
      checkUSPHLoaded();
      for (StructureDefinition usd : usPHProfiles) {
        if (!types.contains(usd.getType()) && usd.getKind() == StructureDefinitionKind.RESOURCE) {
          types.add(usd.getType());
        }
      }
      if (Utilities.existsInList(sd.getType(), types)) {
        StructureDefinition t = sd;
        while (t != null && !existsInSDList(usPHProfiles, t.getUrl())) {
          t = context.fetchResource(StructureDefinition.class, t.getBaseDefinition());
        }
        if (t == null) {
          problems.add(sd);
          StringBuilder b = new StringBuilder();
          ValidationMessage vm = new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '" + sd.getUrl() + "').baseDefinition", "US Public Health FHIR Usage rules require that all profiles on " + sd.getType() +
                  (matches(usPHProfiles, sd.getType()) > 1 ? " derive from one of the base US Public Health profiles" : " derive from the core US Public Health profile") + ". See https://confluence.hl7.org/display/CGP/US+Core+Variance+Request+Process",
                  IssueSeverity.WARNING).setMessageId(PublisherMessageIds.US_CORE_DERIVATION);
          b.append(vm.getMessage());
          f.getErrors().add(vm);
          // actually, that should be an error, but US realm doesn't have a proper base, so we're going to report the differences against the base
          for (StructureDefinition candidate : candidateProfiles(sd.getType())) {
            comparisonsPH.add(new ProfilePair(f.getErrors(), sd, candidate));
            b.append(". <a href=\"us-ph-comparisons/sd-" + candidate.getId() + "-" + sd.getId() + ".html\">Comparison with " + candidate.present() + "</a>");
          }
          vm.setHtml(b.toString());
        }
      }
    }
  }

  private void checkUSCoreLoaded() throws IOException {
    if (usCoreProfiles == null) {
      usCoreProfiles = new ArrayList<>();
      NpmPackage uscore = fetchLatestUSCore();
      PackageInformation pi = new PackageInformation(uscore);
      if (uscore != null) {
        for (String id : uscore.listResources("StructureDefinition", "ValueSet", "CodeSystem")) {
          CanonicalResource usd = (CanonicalResource) loadResourceFromPackage(uscore, id);
          usd.setWebPath(Utilities.pathURL(uscore.getWebLocation(), usd.fhirType()+"-"+usd.getId()+".html"));
          if (usd instanceof StructureDefinition) {
            usCoreProfiles.add((StructureDefinition) usd);
          }
          if (!context.hasResource(Resource.class, usd.getUrl())) {
            context.getManager().cacheResourceFromPackage(usd, pi);
          }
        }
      }
    }
  }

  private void checkUSPHLoaded() throws IOException {
    if (usPHProfiles == null) {
      usPHProfiles = new ArrayList<>();
      NpmPackage usph = fetchLatestUSPH();
      PackageInformation pi = new PackageInformation(usph);
      if (usph != null) {
        for (String id : usph.listResources("StructureDefinition", "ValueSet", "CodeSystem")) {
          CanonicalResource usd = (CanonicalResource) loadResourceFromPackage(usph, id);
          usd.setWebPath(Utilities.pathURL(usph.getWebLocation(), usd.fhirType()+"-"+usd.getId()+".html"));
          if (usd instanceof StructureDefinition) {
            usPHProfiles.add((StructureDefinition) usd);
          }
          if (!context.hasResource(Resource.class, usd.getUrl())) {
            context.getManager().cacheResourceFromPackage(usd, pi);
          }
        }
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
    try {
      PackageList pl = PackageList.fromUrl("https://hl7.org/fhir/us/core/package-list.json");
      for (PackageListEntry v : pl.versions()) {
        if (VersionUtilities.versionMatches(version, v.fhirVersion())) {
          return new FilesystemPackageCacheManager.Builder().build().loadPackage("hl7.fhir.us.core", v.version());
        }
      }
      // we didn't find a compatible version, we'll just take the last version
      for (PackageListEntry v : pl.versions()) {
        return new FilesystemPackageCacheManager.Builder().build().loadPackage("hl7.fhir.us.core", v.version());
      }
      return null;
    } catch (Exception e) {
      System.out.println("Error checking US Core: "+e.getMessage());
    }
    FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager.Builder().build();
    return pcm.loadPackage("hl7.fhir.us.core");
  }

  private NpmPackage fetchLatestUSPH() throws IOException {
    try {
      PackageList pl = PackageList.fromUrl("https://hl7.org/fhir/us/ph-library/package-list.json");
      for (PackageListEntry v : pl.versions()) {
        if (VersionUtilities.versionMatches(version, v.fhirVersion())) {
          return new FilesystemPackageCacheManager.Builder().build().loadPackage("hl7.fhir.us.ph-library", v.version());
        }
      }
      // we didn't find a compatible version, we'll just take the last version
      for (PackageListEntry v : pl.versions()) {
        return new FilesystemPackageCacheManager.Builder().build().loadPackage("hl7.fhir.us.ph-library", v.version());
      }
      return null;
    } catch (Exception e) {
      System.out.println("Error checking US Core: "+e.getMessage());
    }
    FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager.Builder().build();
    return pcm.loadPackage("hl7.fhir.us.ph-library");
  }

  private Resource loadResourceFromPackage(NpmPackage uscore, String filename) throws FHIRException, IOException {
    InputStream s = uscore.loadResource(filename);
    if (VersionUtilities.isR3Ver(uscore.fhirVersion())) {
      return VersionConvertorFactory_30_50.convertResource(new org.hl7.fhir.dstu3.formats.JsonParser().parse(s), new BaseAdvisor_30_50(false));
    } else if (VersionUtilities.isR4Ver(uscore.fhirVersion())) {
      return VersionConvertorFactory_40_50.convertResource(new org.hl7.fhir.r4.formats.JsonParser().parse(s));
    } else if (VersionUtilities.isR5Plus(uscore.fhirVersion())) {
      return new org.hl7.fhir.r5.formats.JsonParser().parse(s);
    } else {
      return null;
    }
  }

  private JsonObject fetchJson(String source) throws IOException {
    try {
      HTTPResult res = ManagedWebAccess.get(Arrays.asList("web"), source+"?nocache=" + System.currentTimeMillis());
      res.checkThrowException();
      return JsonParser.parseObject(res.getContent());
    } catch (IOException e) {
      throw new IOException("Error reading "+source+": "+e.getMessage(), e);
    }
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
      ComparisonSession session = new ComparisonSession(i18n, context, context, "Comparison of "+name+" with US-Core", pkp, pkp);
      //    session.setDebug(true);
      for (ProfilePair c : comparisonsCore) {
//        System.out.println("US Core Comparison: compare "+c.local+" to "+c.uscore);
        session.compare(c.uscore, c.local);      
      }
      FileUtilities.createDirectory(Utilities.path(dstDir, "us-core-comparisons"));
      ComparisonRenderer cr = new ComparisonRenderer(context, context, Utilities.path(dstDir, "us-core-comparisons"), session);
      cr.loadTemplates(context);
      cr.setPreamble(renderProblems());
      cr.render("US Realm", "Current Build");
      System.out.println("US Core Comparisons Finished");
    } catch (Throwable e) {
      System.out.println("US Core Comparison failed: "+e.getMessage());
      e.printStackTrace();
    }
    if (isPH) {
      try {
        ComparisonSession session = new ComparisonSession(i18n, context, context, "Comparison of "+name+" with US-Public Health", pkp, pkp);
        //    session.setDebug(true);
        for (ProfilePair c : comparisonsPH) {
//        System.out.println("US Core Comparison: compare "+c.local+" to "+c.uscore);
          session.compare(c.uscore, c.local);
        }
        FileUtilities.createDirectory(Utilities.path(dstDir, "us-ph-comparisons"));
        ComparisonRenderer cr = new ComparisonRenderer(context, context, Utilities.path(dstDir, "us-ph-comparisons"), session);
        cr.loadTemplates(context);
        cr.setPreamble(renderProblems());
        cr.render("US Realm", "Current Build");
        System.out.println("US Public Health Comparisons Finished");
      } catch (Throwable e) {
        System.out.println("US Public Health Comparison failed: "+e.getMessage());
        e.printStackTrace();
      }

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
        b.append("<li><a href=\"../"+s.getWebPath()+"\">"+s.present()+"</a></li>\r\n");
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
      } else if (Utilities.noString(sctVer) ){
        sct = "<p>Snomed: <span style=\"background-color: #ffcccc\">The IG does not specify the US edition of SNOMED CT version in the parameters (<code>"+US_SNOMED+"</code>)</span></p>";
      } else {
        sct = "<p>Snomed: <span style=\"background-color: #ffcccc\">The IG specifies a different version ("+sctVer+") to the US edition of SNOMED CT version in the parameters (<code>"+US_SNOMED+"</code>)</span></p>";
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
  
  public boolean isExempt(String packageId) {
    return "hl7.fhir.us.core".equals(packageId);
  }

}
