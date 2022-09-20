package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.formula.functions.T;
import org.fhir.ucum.Canonical;
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_10_50;
import org.hl7.fhir.convertors.analytics.PackageVisitor;
import org.hl7.fhir.convertors.analytics.PackageVisitor.IPackageVisitorProcessor;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_10_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_14_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_43_50;
import org.hl7.fhir.convertors.txClient.TerminologyClientFactory;
import org.hl7.fhir.convertors.analytics.SearchParameterAnalysis;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.IGR2ConvertorAdvisor5;
import org.hl7.fhir.igtools.publisher.PublisherLoader;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.igtools.publisher.utils.XIGGenerator.CanonicalResourceSorter;
import org.hl7.fhir.igtools.publisher.utils.XIGGenerator.PageContent;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.r5.conformance.ProfileUtilities.ProfileKnowledgeProvider;
import org.hl7.fhir.r5.conformance.ProfileUtilities.ProfileKnowledgeProvider.BindingResolution;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.CodeSystemContentMode;
import org.hl7.fhir.r5.model.CodeType;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.ConceptMap.ConceptMapGroupComponent;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.Parameters;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionBindingComponent;
import org.hl7.fhir.r5.model.OperationDefinition;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.SearchParameter;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionContextComponent;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.renderers.RendererFactory;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.ResourceRendererMode;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.MarkDownProcessor.Dialect;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.JsonTrackingParser;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.ToolsVersion;
import org.hl7.fhir.utilities.validation.ValidationOptions;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.xml.sax.SAXException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

public class XIGGenerator implements IPackageVisitorProcessor, ProfileKnowledgeProvider {

  public class PageContent {
    String title;
    String content;
    
    public PageContent(String title, String content) {
      super();
      this.title = title;
      this.content = content;
    }
    public String getTitle() {
      return title;
    }
    public String getContent() {
      return content;
    }
  }

  public class CanonicalResourceSorter implements Comparator<CanonicalResource> {

    @Override
    public int compare(CanonicalResource arg0, CanonicalResource arg1) {
      return StringUtils.compareIgnoreCase(arg0.present(), arg1.present());
    }

  }
  private static final String SNOMED_EDITION = "900000000000207008"; // international

  Set<String> pid = new HashSet<>();
  Map<String, Map<String, CanonicalResource>> counts = new HashMap<>();
  Map<String, CanonicalResource> resources = new HashMap<>();
  private String source;
  private String target;
  private SimpleWorkerContext ctxt;
  private RenderingContext rc;
  private JsonObject json;
  private Map<String, JsonObject> pjlist = new HashMap<>();
  private Set<String> opr = new HashSet<>();
  private Set<String> spr = new HashSet<>();
  
  private final static String HEADER=
      "<html>\r\n"+
          "<head>\r\n"+
          "  <title>$title$</title>\r\n"+
          "  <link href=\"fhir.css\" rel=\"stylesheet\"/>\r\n"+
          "</head>\r\n"+
          "<body style=\"margin: 10\">\r\n";

  private final static String FOOTER =
      "</body>\r\n</html>\r\n";
  
  public XIGGenerator(String source, String target) throws JsonSyntaxException, FHIRException, IOException, URISyntaxException {
    super();
    this.source = source;
    this.target = target;
    NpmPackage npm = new FilesystemPackageCacheManager(true, ToolsVersion.TOOLS_VERSION).loadPackage("hl7.fhir.r5.core#5.0.0-ballot");
    this.ctxt =  new SimpleWorkerContext.SimpleWorkerContextBuilder().fromPackage(npm, new PublisherLoader(npm, SpecMapManager.fromPackage(npm), npm.getWebLocation(), null).makeLoader());
    this.ctxt.setAllowLazyLoading(false);
    this.ctxt.setAllowLoadingDuplicates(true);
//    this.ctxt.connectToTSServer(TerminologyClientFactory.makeClient("http://tx.fhir.org", "fhir/publisher", FhirPublication.R5), null);
    this.rc = new RenderingContext(ctxt, new MarkDownProcessor(Dialect.COMMON_MARK), 
        new ValidationOptions("en"), "http://hl7.org/fhir", "", "en", ResourceRendererMode.TECHNICAL);
    this.rc.setDestDir(target);
    this.rc.setPkp(this);
    this.rc.setNoSlowLookup(true);
    this.ctxt.setExpansionProfile(buildExpansionProfile());
    this.ctxt.setSuppressDebugMessages(true);
    this.json = new JsonObject();
    json.addProperty("date", new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ", new Locale("en", "US")).format(Calendar.getInstance().getTime()));
  }

  private Parameters buildExpansionProfile() {
    Parameters res = new Parameters();
    res.addParameter("profile-url", "urn:uuid:"+UUID.randomUUID().toString().toLowerCase());
    res.addParameter("excludeNested", false);
    res.addParameter("includeDesignations", true);
    // res.addParameter("activeOnly", true);
    res.addParameter("system-version", "http://snomed.info/sct|http://snomed.info/sct/"+SNOMED_EDITION); // value sets are allowed to override this. for now
    return res;
  }
  
  public static void main(String[] args) throws Exception {
    new XIGGenerator(args[0], args[1]).execute();
  }

  private void execute() throws IOException, ParserConfigurationException, SAXException, FHIRException, EOperationOutcome {
    PackageVisitor pv = new PackageVisitor();
    pv.getResourceTypes().add("CapabilityStatement");
    pv.getResourceTypes().add("SearchParameter");
    pv.getResourceTypes().add("OperationDefinition");
    pv.getResourceTypes().add("SearchParameter");
    pv.getResourceTypes().add("StructureDefinition");
    pv.getResourceTypes().add("ValueSet");
    pv.getResourceTypes().add("CodeSystem");
    pv.getResourceTypes().add("ConceptMap");
    pv.getResourceTypes().add("StructureMap");
    pv.getResourceTypes().add("NamingSystem");
    pv.getResourceTypes().add("GraphDefinition");
    pv.getResourceTypes().add("ActivityDefinition");
    pv.getResourceTypes().add("ConditionDefinition");
    pv.getResourceTypes().add("DeviceDefinition");
    pv.getResourceTypes().add("EventDefinition");
    pv.getResourceTypes().add("ObservationDefinition");
    pv.getResourceTypes().add("PlanDefinition");
    pv.getResourceTypes().add("Questionnaire");
    pv.getResourceTypes().add("SpecimenDefinition");
    pv.getResourceTypes().add("ExampleScenario");
    pv.getResourceTypes().add("ActorDefinition");
    pv.getResourceTypes().add("Requirements");
    
    pv.setOldVersions(false);
    pv.setCorePackages(false);
    pv.setProcessor(this);
    pv.setCurrent(false);
    pv.visitPackages();

    produce(source, target);
    printSummary(); 
  }

  private void produce(String source, String target) throws IOException, FHIRException, EOperationOutcome {
    JsonTrackingParser.write(json, Utilities.path(target, "registry.json"));
    json = null;
    
    System.out.println("Generate...");
    int i = 0;
    for (CanonicalResource cr : resources.values()) {
      renderResource(cr);
      i++;
      if (i > resources.size() / 100) {
        System.out.print(".");
        i = 0;
      }
    }
    System.out.println("Indexes...");
    Utilities.createDirectory(target);
    Utilities.copyDirectory(source, target, null);
    StringBuilder b = new StringBuilder();
    b.append("<p><b>Structures</b></p>\r\n");
    b.append("<ul style=\"column-count: 4\">\r\n");
    addPage(b, "profiles-resources.html", makeProfilesPage(StructureDefinitionKind.RESOURCE, "profile-res", "Resource Profiles"));   
    addPage(b, "profiles-datatypes.html", makeProfilesPage(null, "profile-dt", "DataType Profiles"));   
    addPage(b, "extensions.html", makeExtensionsPage("Extensions"));   
    addPage(b, "logicals.html", makeLogicalsPage());   
    b.append("</ul>\r\n");
    b.append("<p><b>CodeSytems</b></p>\r\n");
    b.append("<ul style=\"column-count: 4\">\r\n");
    addPage(b, "codeSystems-complete.html", makeCodeSystemPage(CodeSystemContentMode.COMPLETE, "Complete Code Systems"));   
    addPage(b, "codeSystems-example.html", makeCodeSystemPage(CodeSystemContentMode.EXAMPLE, "Example Code Systems"));   
    addPage(b, "codeSystems-fragment.html", makeCodeSystemPage(CodeSystemContentMode.FRAGMENT, "Fragement Code Systems"));   
    addPage(b, "codeSystems-notpresent.html", makeCodeSystemPage(CodeSystemContentMode.NOTPRESENT, "Not Present Code Systems"));   
    addPage(b, "codeSystems-supplement.html", makeCodeSystemPage(CodeSystemContentMode.SUPPLEMENT, "Code System Supplements"));   
    b.append("</ul>\r\n");
    b.append("<p><b>ValueSets</b></p>\r\n");
    b.append("<ul style=\"column-count: 4\">\r\n");
    addPage(b, "valuesets-all.html", makeValueSetsPage(null, "All ValueSets"));   
    addPage(b, "valuesets-cpt.html", makeValueSetsPage(counts.get("ValueSet/cpt"), "CPT ValueSets"));   
    addPage(b, "valuesets-dcm.html", makeValueSetsPage(counts.get("ValueSet/dcm"), "Dicom ValueSets"));   
    addPage(b, "valuesets-example.html",  makeValueSetsPage(counts.get("ValueSet/example"), "Example ValueSets"));   
    addPage(b, "valuesets-fhir.html", makeValueSetsPage(counts.get("ValueSet/fhir"), "FHIR ValueSets"));   
    addPage(b, "valuesets-cvx.html", makeValueSetsPage(counts.get("ValueSet/cvx"), "CVX ValueSets"));   
    addPage(b, "valuesets-icd.html", makeValueSetsPage(counts.get("ValueSet/icd"), "ICD ValueSets"));   
    addPage(b, "valuesets-icpc.html", makeValueSetsPage(counts.get("ValueSet/icpc"), "ICPC ValueSets"));   
    addPage(b, "valuesets-ietf.html",  makeValueSetsPage(counts.get("ValueSet/ietf"), "IETF ValueSets"));   
    addPage(b, "valuesets-ihe.html", makeValueSetsPage(counts.get("ValueSet/ihe"), "IHE ValueSets"));   
    addPage(b, "valuesets-internal.html", makeValueSetsPage(counts.get("ValueSet/internal"), "Internal ValueSets"));   
    addPage(b, "valuesets-iso.html", makeValueSetsPage(counts.get("ValueSet/iso"), "ISO ValueSets"));   
    addPage(b, "valuesets-loinc.html", makeValueSetsPage(counts.get("ValueSet/loinc"), "LOINC ValueSets"));   
    addPage(b, "valuesets-mixed.html", makeValueSetsPage(counts.get("ValueSet/mixed"), "Mixed ValueSets"));   
    addPage(b, "valuesets-n-a.html", makeValueSetsPage(counts.get("ValueSet/n/a"), "Expansion only ValueSets"));   
    addPage(b, "valuesets-ncpdp.html", makeValueSetsPage(counts.get("ValueSet/ncpdp"), "NCPDP ValueSets"));   
    addPage(b, "valuesets-ndc.html", makeValueSetsPage(counts.get("ValueSet/ndc"), "NDC ValueSets"));   
    addPage(b, "valuesets-nucc.html",  makeValueSetsPage(counts.get("ValueSet/nucc"), "NUCC ValueSets"));   
    addPage(b, "valuesets-oid.html",  makeValueSetsPage(counts.get("ValueSet/oid"), "OID ValueSets"));   
    addPage(b, "valuesets-tho.html",  makeValueSetsPage(counts.get("ValueSet/tho"), "THO ValueSets"));   
    addPage(b, "valuesets-rx.html", makeValueSetsPage(counts.get("ValueSet/rx"), "RxNorm ValueSets"));   
    addPage(b, "valuesets-sct.html", makeValueSetsPage(counts.get("ValueSet/sct"), "SNOMED CT ValueSets"));   
    addPage(b, "valuesets-fhir.html",  makeValueSetsPage(counts.get("ValueSet/tho"), "FHIR ValueSets"));   
    addPage(b, "valuesets-ucum.html", makeValueSetsPage(counts.get("ValueSet/ucum"), "UCUM ValueSets"));   
    addPage(b, "valuesets-vs.html", makeValueSetsPage(counts.get("ValueSet/vs"), "Composite ValueSets"));   
    b.append("</ul>\r\n");
    b.append("<p><b>ConceptMaps</b></p>\r\n");
    b.append("<ul style=\"column-count: 4\">\r\n");
    addPage(b, "conceptmaps-all.html", makeConceptMapsPage(null, "All ConceptMaps"));   
    addPage(b, "conceptmaps-cpt.html", makeConceptMapsPage("cpt", "CPT ConceptMaps"));   
    addPage(b, "conceptmaps-dcm.html", makeConceptMapsPage("dcm", "Dicom ConceptMaps"));   
    addPage(b, "conceptmaps-example.html",  makeConceptMapsPage("example", "Example ConceptMaps"));   
    addPage(b, "conceptmaps-fhir.html", makeConceptMapsPage("fhir", "FHIR ConceptMaps"));   
    addPage(b, "conceptmaps-cvx.html", makeConceptMapsPage("cvx", "CVX ConceptMaps"));   
    addPage(b, "conceptmaps-icd.html", makeConceptMapsPage("icd", "ICD ConceptMaps"));   
    addPage(b, "conceptmaps-ietf.html",  makeConceptMapsPage("ietf", "IETF ConceptMaps"));   
    addPage(b, "conceptmaps-ihe.html", makeConceptMapsPage("ihe", "IHE ConceptMaps"));   
    addPage(b, "conceptmaps-icpc.html", makeConceptMapsPage("icpc", "ICPC ConceptMaps"));   
    addPage(b, "conceptmaps-internal.html", makeConceptMapsPage("internal", "Internal ConceptMaps"));   
    addPage(b, "conceptmaps-iso.html", makeConceptMapsPage("iso", "ISO ConceptMaps"));   
    addPage(b, "conceptmaps-loinc.html", makeConceptMapsPage("loinc", "LOINC ConceptMaps"));   
    addPage(b, "conceptmaps-ncpdp.html", makeConceptMapsPage("ncpdp", "NCPDP ConceptMaps"));   
    addPage(b, "conceptmaps-ndc.html", makeConceptMapsPage("ndc", "NDC ConceptMaps"));   
    addPage(b, "conceptmaps-nucc.html",  makeConceptMapsPage("nucc", "NUCC ConceptMaps"));   
    addPage(b, "conceptmaps-oid.html",  makeConceptMapsPage("oid", "OID ConceptMaps"));   
    addPage(b, "conceptmaps-tho.html",  makeConceptMapsPage("tho", "THO ConceptMaps"));   
    addPage(b, "conceptmaps-rx.html", makeConceptMapsPage("rx", "RxNorm ConceptMaps"));   
    addPage(b, "conceptmaps-sct.html", makeConceptMapsPage("sct", "SNOMED CT ConceptMaps"));   
    addPage(b, "conceptmaps-tho.html",  makeConceptMapsPage("tho", "FHIR ConceptMaps"));   
    addPage(b, "conceptmaps-ucum.html", makeConceptMapsPage("ucum", "UCUM ConceptMaps"));   
    b.append("</ul>\r\n");
    b.append("<p><b>Operation Definitions</b></p>\r\n");
    b.append("<ul style=\"column-count: 4\">\r\n");
    for (String r : Utilities.sorted(opr)) {
      addPage(b, "operations-"+r.toLowerCase()+".html", makeOperationsPage(r, "Operations for "+r));         
    }
    b.append("</ul>\r\n");
    b.append("<p><b>Search Definitions</b></p>\r\n");
    b.append("<ul style=\"column-count: 4\">\r\n");
    for (String r : Utilities.sorted(spr)) {
      addPage(b, "searchparams-"+r.toLowerCase()+".html", makeSearchParamsPage(r, "Search Params for "+r));         
    }
    b.append("</ul>\r\n");

//    addPage(b, "logicals.html", "Logical Models", makeProfilesPage(StructureDefinitionKind.LOGICAL, "logical"));   
    genPage("XIG index", b.toString(), Utilities.path(target, "index.html"));
    System.out.println("Done");
  }
  
  private PageContent makeConceptMapsPage(String mode, String title) {
    List<ConceptMap> list = new ArrayList<>();
    for (CanonicalResource cr : resources.values()) {
      if (cr instanceof ConceptMap) {
        ConceptMap cm = (ConceptMap) cr;
        Set<String> systems = new HashSet<>();
        for (ConceptMapGroupComponent g : cm.getGroup()) {
          if (g.hasSource()) {
            systems.add(g.getSource());
          }
          if (g.hasTarget()) {
            systems.add(g.getTarget());
          }
        }
        if (inMode(mode, systems)) {
          list.add(cm);
        }
      }
    }
    if (list.size() == 0) {
      return null;
    }
    Collections.sort(list, new CanonicalResourceSorter());
    StringBuilder b = new StringBuilder();

    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, false);
    for (ConceptMap cm : list) {
      crTr(b, cm, 0);
      
    }
    b.append("</table>\r\n");

    return new PageContent(title+" ("+list.size()+")", b.toString());
  }

  private boolean inMode(String mode, Set<String> systems) {
    if (mode == null) {
      return true;
    }
    switch (mode) {
    case "cpt": return systems.contains("http://www.ama-assn.org/go/cpt");    
    case "dcm": return systems.contains("http://dicom.nema.org/resources/ontology/DCM");   
    case "example": 
      for (String s : systems) {
        if (s.contains("example.org")) {
          return true;
        }
      }
      return false;
    case "fhir":    
      for (String s : systems) {
        if (s.startsWith("http://hl7.org/fhir")) {
          return true;
        }
      }
      return false;
    case "cvx": return systems.contains("http://hl7.org/fhir/sid/cvx");   
    case "icd": 
      for (String s : systems) {
        if (Utilities.existsInList(s, "http://hl7.org/fhir/sid/icd-9-cm", "http://hl7.org/fhir/sid/icd-10", "http://fhir.de/CodeSystem/dimdi/icd-10-gm", "http://hl7.org/fhir/sid/icd-10-nl 2.16.840.1.113883.6.3.2", "http://hl7.org/fhir/sid/icd-10-cm")) {
          return true;
        }
      }
      return false;     
    case "ietf": 
      for (String s : systems) {
        if (s.contains(":ieft:")) {
          return true;
        }
      }
      return false;    
    case "ihe":  
      for (String s : systems) {
        if (s.contains("ihe.net")) {
          return true;
        }
      }
      return false;    
    case "icpc":  
      for (String s : systems) {
        if (s.contains("icpc")) {
          return true;
        }
      }
      return false;    
    case "internal": return systems.contains("");    
    case "iso": 
      for (String s : systems) {
        if (s.contains(":iso:")) {
          return true;
        }
      }
      return false;
    case "loinc": return systems.contains("http://loinc.org");    
    case "ncpdp": return systems.contains("");    
    case "ndc": return systems.contains("http://hl7.org/fhir/sid/ndc");    
    case "nucc":  
      for (String s : systems) {
        if (s.contains("nucc")) {
          return true;
        }
      }
      return false;  
    case "oid":  
      for (String s : systems) {
        if (s.contains("urn:oid:")) {
          return true;
        }
      }
      return false;     
    case "tho":  
      for (String s : systems) {
        if (s.contains("urn:oid:")) {
          return true;
        }
      }
      return false;    
    case "rx": return systems.contains("http://www.nlm.nih.gov/research/umls/rxnorm");    
    case "sct": return systems.contains("http://snomed.info/sct");    
    case "ucum": return systems.contains("http://unitsofmeasure.org");    
    }
    return false; 
  }

  private PageContent makeLogicalsPage() {
    List<StructureDefinition> list = new ArrayList<>();
    for (CanonicalResource cr : resources.values()) {
      if (cr instanceof StructureDefinition) {
        StructureDefinition sd = (StructureDefinition) cr;
        if ((sd.getKind() == StructureDefinitionKind.LOGICAL)) {
          list.add(sd);
        }
      }
    }
    Collections.sort(list, new CanonicalResourceSorter());
    StringBuilder b = new StringBuilder();

    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, false);
    for (StructureDefinition sd : list) {
      crTr(b, sd, 0);
      
    }
    b.append("</table>\r\n");

    return new PageContent("Logical Models ("+list.size()+")", b.toString());
  }

  private void crTrHeaders(StringBuilder b, boolean c) {
    b.append("<tr>"+(c ? "<td>#<td>" : "")+"<td>Name</td><td>Source</td><td>Ver</td><td>Description</td></li>\r\n");
  }

  private void crTr(StringBuilder b, CanonicalResource cr, int count) {
    b.append("<tr>"+(count > 0 ? "<td>"+count+"<td>" : "")+"<td title=\""+cr.getUrl()+"#"+cr.getVersion()+"\">"+crlink(cr)+"</td><td><a href=\""+cr.getUserString("purl")+"\">"+prepPackage(cr.getUserString("pid"))+"</a></td><td>"+
       ver(cr.getUserString("fver"))+"</td><td>"+
    Utilities.escapeXml(cr.getDescription())+"</td></li>\r\n");
  }

  private String ver(String ver) {
    if (VersionUtilities.isR2Ver(ver)) {
      return "R2";
    }
    if (VersionUtilities.isR2BVer(ver)) {
      return "R2B";
    }
    if (VersionUtilities.isR3Ver(ver)) {
      return "R3";
    }
    if (VersionUtilities.isR4Ver(ver)) {
      return "R4";
    }
    if (VersionUtilities.isR4BVer(ver)) {
      return "R4B";
    }
    if (VersionUtilities.isR5Ver(ver)) {
      return "R5";
    }
    return "R?";
  }

  private PageContent makeExtensionsPage(String string) throws IOException {
    Map<String, List<StructureDefinition>> profiles = new HashMap<>();
    for (CanonicalResource cr : resources.values()) {
      boolean ok = false;
      if (cr instanceof StructureDefinition) {
        StructureDefinition sd = (StructureDefinition) cr;
        for (StructureDefinitionContextComponent t : sd.getContext()) {
          String m = descContext(t);
          if (m != null) {
            if (!profiles.containsKey(m)) {
              profiles.put(m, new ArrayList<>());
            }
            profiles.get(m).add(sd);
            ok = true;
          }
        }
        if (!ok) {
          if (!profiles.containsKey("s")) {
            profiles.put("No Context", new ArrayList<>());
          }
          profiles.get("No Context").add(sd);        
        }
      }
    }
    StringBuilder b = new StringBuilder();
    int t = 0;
    b.append("<ul style=\"column-count: 3\">\r\n");
    for (String s : Utilities.sorted(profiles.keySet())) {
      t = t + profiles.get(s).size();
      addPage(b, "extension-"+s.toLowerCase()+".html",new PageContent(s+" ("+profiles.get(s).size()+")", makeProfilesTypePage(profiles.get(s), s)));
    }
    b.append("</ul>\r\n");

    return new PageContent("Extensions ("+t+")", b.toString());
  }

  private String descContext(StructureDefinitionContextComponent t) {
    switch (t.getType()) {
    case ELEMENT:
      if (t.getExpression().contains(".")) {
        return t.getExpression().substring(0, t.getExpression().indexOf("."));        
      } else {
        return t.getExpression();
      }
    case EXTENSION:
      return "Extension";
    case FHIRPATH:
      return "FHIRPath";
    default:
      return "Unknown";
    }
  }

  private PageContent makeCodeSystemPage(CodeSystemContentMode mode, String title) {
    List<CodeSystem> list = new ArrayList<>();
    for (CanonicalResource cr : resources.values()) {
      if (cr instanceof CodeSystem) {
        CodeSystem cs = (CodeSystem) cr;
        boolean ok = cs.getContent() == mode;
        if (ok) {
          list.add(cs);
        }
      }
    }
    StringBuilder b = new StringBuilder();

    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, false);
    for (CodeSystem cs : list) {
      crTr(b, cs, 0);      
    }
    b.append("</table>\r\n");

    return new PageContent(title+" ("+list.size()+")", b.toString());
  }

  private PageContent makeOperationsPage(String r, String title) {
    List<OperationDefinition> list = new ArrayList<>();
    for (CanonicalResource cr : resources.values()) {
      if (cr instanceof OperationDefinition) {
        OperationDefinition od = (OperationDefinition) cr;
        boolean ok = false;
        for (CodeType c : od.getResource()) {
          if (r.equals(c.asStringValue())) {
            ok = true;
          }
        }
        if (ok) {
          list.add(od);
        }
      }
    }
    StringBuilder b = new StringBuilder();

    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, false);
    for (OperationDefinition od : list) {
      crTr(b, od, 0);       
    }
    b.append("</table>\r\n");

    return new PageContent(title+" ("+list.size()+")", b.toString());
  }

  private PageContent makeSearchParamsPage(String r, String title) {
    List<SearchParameter> list = new ArrayList<>();
    for (CanonicalResource cr : resources.values()) {
      if (cr instanceof SearchParameter) {
        SearchParameter sp = (SearchParameter) cr;
        boolean ok = false;
        for (CodeType c : sp.getBase()) {
          if (r.equals(c.asStringValue())) {
            ok = true;
          }
        }
        if (ok) {
          list.add(sp);
        }
      }
    }
    StringBuilder b = new StringBuilder();

    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, false);
    for (SearchParameter sp : list) {
      crTr(b, sp, 0);       
    }
    b.append("</table>\r\n");

    return new PageContent(title+" ("+list.size()+")", b.toString());
  }

  private void genPage(String title, String content, String fn) throws IOException {
    String cnt = HEADER.replace("$title$", title)+content+FOOTER;
    TextFile.stringToFile(cnt, fn);
  }

  private PageContent makeValueSetsPage(Map<String, CanonicalResource> map, String title) {
    List<ValueSet> vslist = new ArrayList<>();
    for (CanonicalResource cr : resources.values()) {
      if (cr instanceof ValueSet) {
        ValueSet vs = (ValueSet) cr;
        String pid = vs.getUserString("pid");
        if (isOKVsPid(pid) && (map == null || map.containsValue(vs))) {
          vslist.add(vs);
        }
      }
    }
    Collections.sort(vslist, new CanonicalResourceSorter());
    StringBuilder b = new StringBuilder();
    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, false);
    for (ValueSet vs : vslist) {
      crTr(b, vs, 0);            
    }
    b.append("</table>\r\n");
    return new PageContent(title+" ("+vslist.size()+")", b.toString());
  }

  private boolean isOKVsPid(String pid) {
    if (pid.contains("#")) {
      pid = pid.substring(0, pid.indexOf("#"));
    }
    return !Utilities.existsInList(pid, "us.nlm.vsac", "fhir.dicom", "us.cdc.phinvads");
  }

  private PageContent makeProfilesPage(StructureDefinitionKind kind, String prefix, String title) throws IOException {
    Map<String, List<StructureDefinition>> profiles = new HashMap<>();
    for (CanonicalResource cr : resources.values()) {
      if (cr instanceof StructureDefinition) {
        StructureDefinition sd = (StructureDefinition) cr;
        if ((sd.getKind() == kind) || (kind == null && (sd.getKind() == StructureDefinitionKind.COMPLEXTYPE ||
            sd.getKind() == StructureDefinitionKind.PRIMITIVETYPE))) {
          String t = sd.getDifferential().getElementFirstRep().getPath();
          if (t == null) {
            t = sd.getType();
          }
          if (t == null) {
            break;
          }
          if (t.contains(".")) {
            t = t.substring(0, t.indexOf("."));
          }
          if (!"Extension".equals(t)) {
            if (!profiles.containsKey(t)) {
              profiles.put(t, new ArrayList<>());
            }
            profiles.get(t).add(sd);
          }
        }
      }
    }
    StringBuilder b = new StringBuilder();
    int t = 0;
    b.append("<ul style=\"column-count: 3\">\r\n");
    for (String s : Utilities.sorted(profiles.keySet())) {
      t = t + profiles.get(s).size();
      addPage(b, prefix+"-"+s.toLowerCase()+".html",new PageContent(s+" ("+profiles.get(s).size()+")", makeProfilesTypePage(profiles.get(s), s)));
    }
    b.append("</ul>\r\n");

    return new PageContent(title+" ("+t+")", b.toString());
  }

  private void addPage(StringBuilder b, String name, PageContent page) throws IOException {
    if (page != null) {
      genPage(page.getTitle(), page.getContent(), Utilities.path(target, name));
      b.append("<li><a href=\""+name+"\">"+page.getTitle()+"</a></li>\r\n");
    }
  }

  private String makeProfilesTypePage(List<StructureDefinition> list, String type) {
    Collections.sort(list, new CanonicalResourceSorter());
    StringBuilder b = new StringBuilder();
    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, true);
    int i = 0;
    for (StructureDefinition sd : list) {
      i++;
      sd.setUserData("#", i);
      crTr(b, sd, i);
    }
    b.append("</table>\r\n");
    StructureDefinition sdt = ctxt.fetchTypeDefinition(type);
    if (!type.equals("Extension") && sdt != null) {
      List<String> paths = new ArrayList<>();
      for (ElementDefinition ed : sdt.getSnapshot().getElement()) {
        if (!ed.getPath().endsWith(".id")) {
          paths.add(ed.getPath());
        }
      }
      for (StructureDefinition sd : list) {
        List<String> tpaths = new ArrayList<>();
        
        for (ElementDefinition ed : sd.getDifferential().getElement()) {
          if (ed.getPath() != null) {
            tpaths.add(ed.getPath());
          }
        }
        fillOutSparseElements(tpaths);
        for (String t : tpaths) {
          if (!paths.contains(t)) {
            int ndx = paths.indexOf(head(t));
            if (ndx == -1) {
              paths.add(t);
            } else {              
              paths.add(ndx+1, t);
            }
          }
        }
      }
      b.append("<table class=\"grid\">\r\n");
      b.append("<tr>\r\n");
      b.append("<td></td>\r\n");
      for (StructureDefinition sd : list) {
        b.append("<td>"+sd.getUserData("#")+"</td>\r\n");
      }
      b.append("</tr>\r\n");
      for (String p : paths) {
        b.append("<tr>\r\n");
        b.append("<td>"+p+"</td>\r\n");
        for (StructureDefinition sd : list) {
          b.append(analyse(p, sd)+"\r\n");
        }
        b.append("</tr>\r\n");
      }
      b.append("</table>\r\n");
    }
    
    return b.toString();
  }

  private void fillOutSparseElements(List<String> tpaths) {
    int i = 1;
    while (i < tpaths.size()) {
      if (Utilities.charCount(tpaths.get(i-1), '.') < Utilities.charCount(tpaths.get(i), '.')-1) {
        tpaths.add(i, head(tpaths.get(i)));
      } else {
        i++;
      }
    }
  }

  private String head(String path) {
    if (path.contains(".")) {
      return path.substring(0, path.lastIndexOf("."));
    } else {
      return path;
    }
  }

  private String analyse(String p, StructureDefinition sd) {
    List<ElementDefinition> list = new ArrayList<>();
    for (ElementDefinition ed : sd.getDifferential().getElement()) {
      if (p.equals(ed.getPath())) {
        list.add(ed);
      }
    }
    if (list.size() == 0) {
      return "<td style=\"background-color: #ddddd\"></td>";
    } else {
      boolean binding = false;
      boolean cardinality = false;
      boolean invariants = false;
      boolean fixed = false;
      boolean doco = false;
      boolean slicing = false;
      for (ElementDefinition ed : list) {
        doco = doco || (ed.hasDefinition() || ed.hasComment());
        binding = binding || ed.hasBinding();
        cardinality = cardinality || ed.hasMin() || ed.hasMax();
        invariants = invariants || ed.hasConstraint();
        fixed = fixed || ed.hasPattern() || ed.hasFixed();
        slicing = slicing || ed.hasSlicing() || ed.hasSliceName();
      }
      String s = "";
      if (slicing) {
        s = s +" <span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #cafcd3\" title=\"Slicing\">S</span>";
      }
      if (cardinality) {
        s = s +" <span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #b7e8f7\" title=\"Cardinality\">C</span>";
      }
      if (invariants) {
        s = s +" <span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #fdf4f4\" title=\"invariants\">I</span>";
      }
      if (fixed) {
        s = s +" <span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #fcfccc\" title=\"Fixed\">F</span>";
      }
      if (doco) {
        s = s +" <span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #fcd9f1\" title=\"Documentation\">D</span>";
      }
      if (binding) {
        s = s +" <span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #fce0d9\" title=\"Binding\">B</span>";
      }
      if (list.size() > 1) {
        return "<td style=\"background-color: #ffffff\">"+s+" ("+list.size()+")</td>";
      } else {
        return "<td style=\"background-color: #ffffff\">"+s+"</td>";
      }
    }
  }

  private String prepPackage(String pck) {
    if (pck == null) {
      return null;
    }
    if (pck.contains("#")) {
      pck = pck.substring(0, pck.indexOf("#"));
    }
    return pck.replace(".", ".<wbr/>");
  }

  private String crlink(CanonicalResource sd) {
    return "<a href=\""+sd.getUserString("filebase")+".html\">"+sd.present()+"</a>";
  }

  private void printSummary() {
    System.out.println("");
    System.out.println("IGs: "+pid.size());
    int i = 0;
    for (String s : Utilities.sorted(counts.keySet())) {
      i = i + counts.get(s).size();
      System.out.println(s+": "+counts.get(s).size());
    }
    System.out.println("Total: "+i);
  }

  @Override
  public void processResource(String pid, NpmPackage npm, String version, String type, String id, byte[] content) throws FHIRException, IOException, EOperationOutcome {
    this.pid.add(pid);
    Resource r = loadResource(pid, version, type, id, content);
    if (r != null && r instanceof CanonicalResource) {
      CanonicalResource cr = (CanonicalResource) r;
      if (cr.getUrl() != null && !isCoreDefinition(cr, pid)) {
        cr.setText(null);
        JsonObject pj = pjlist.get(pid);
        if (pj == null) {
          pj  = new JsonObject();
          pjlist.put(pid, pj);
          json.add(pid, pj);
          pj.addProperty("id", pid);
          pj.add("package", npm.getNpm());
          pj.add("canonicals", new JsonArray());
        } 
        JsonObject j = new JsonObject();
        pj.getAsJsonArray("canonicals").add(j);
        cr.setUserData("pid", pid);
        cr.setUserData("purl", npm.getWebLocation());
        cr.setUserData("path", determinePath(npm, cr));
        cr.setUserData("pname", npm.title());
        cr.setUserData("fver", npm.fhirVersion());
        cr.setUserData("json", j);
        cr.setUserData("filebase", (cr.fhirType()+"-"+pid.substring(0, pid.indexOf("#"))+"-"+cr.getId()).toLowerCase());
        j.addProperty("fver", npm.fhirVersion());
        j.addProperty("published", pid.contains("#current"));
        j.addProperty("filebase", cr.getUserString("filebase"));
        
        fillOutJson(cr, j);
        if (resources.containsKey(cr.getUrl())) {
          CanonicalResource crt = resources.get(cr.getUrl());
          if (VersionUtilities.isThisOrLater(crt.getVersion(), cr.getVersion())) {
            resources.put(cr.getUrl(), cr);
          }
        } else {
          resources.put(cr.getUrl(), cr);
        }
        ctxt.cacheResource(cr);
        String t = type;
        if (cr instanceof StructureDefinition) {
          StructureDefinition sd = (StructureDefinition) cr;
          if (sd.getKind() == StructureDefinitionKind.LOGICAL) {
            t = t + "/logical";
          } else if (sd.getType().equals("Extension")) {
            t = t + "/extension";
          } else if (sd.getKind() == StructureDefinitionKind.RESOURCE) {
            t = t + "/resource";
          } else {
            t = t + "/other";
          }
        } else if (cr instanceof ValueSet) {
          ValueSet vs = (ValueSet) cr;
          String sys = null;
          for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
            String s = null;
            String system = inc.getSystem();
            if (!Utilities.noString(system)) {
              if ("http://snomed.info/sct".equals(system)) {
                s = "sct";
              } else if ("http://loinc.org".equals(system)) {
                s = "loinc";
              } else if ("http://unitsofmeasure.org".equals(system)) {
                s = "ucum";
              } else if ("http://hl7.org/fhir/sid/ndc".equals(system)) {
                s = "ndc";
              } else if ("http://hl7.org/fhir/sid/cvx".equals(system)) {
                s = "cvx";
              } else if (system.contains(":iso:")) {
                s = "iso";
              } else if (system.contains(":ietf:")) {
                s = "ietf";
              } else if (system.contains("ihe.net")) {
                s = "ihe";
              } else if (system.contains("icpc")) {
                s = "icpc";
              } else if (system.contains("ncpdp")) {
                s = "ncpdp";
              } else if (system.contains("nucc")) {
                s = "nucc";
              } else if (Utilities.existsInList(system, "http://hl7.org/fhir/sid/icd-9-cm", "http://hl7.org/fhir/sid/icd-10", "http://fhir.de/CodeSystem/dimdi/icd-10-gm", "http://hl7.org/fhir/sid/icd-10-nl 2.16.840.1.113883.6.3.2", "http://hl7.org/fhir/sid/icd-10-cm")) {
                s = "icd";
              } else if (system.contains("urn:oid:")) {
                s = "oid";
              } else if ("http://unitsofmeasure.org".equals(system)) {
                s = "ucum";
              } else if ("http://dicom.nema.org/resources/ontology/DCM".equals(system)) {
                s = "dcm";
              } else if ("http://unitsofmeasure.org".equals(system)) {
                s = "ucum";
              } else if ("http://www.ama-assn.org/go/cpt".equals(system)) {
                s = "cpt";
              } else if ("http://www.nlm.nih.gov/research/umls/rxnorm".equals(system)) {
                s = "rx";
              } else if (system.startsWith("http://terminology.hl7.org")) {
                s = "tho";
              } else if (system.startsWith("http://hl7.org/fhir")) {
                s = "fhir";
              } else if (npm.canonical() != null && system.startsWith(npm.canonical())) {
                s = "internal";
              } else if (system.contains("example.org")) {
                s = "example";
              } else {
                s = "?";
              }
            } else if (inc.hasValueSet()) {
              s = "vs";
            }
            if (sys == null) {
              sys = s;
            } else if (!sys.equals(s)) {
              sys = "mixed";
            }
          }
          t = t + "/"+(sys == null ? "n/a" : sys);
        }
        if (!counts.containsKey(t)) {
          counts.put(t, new HashMap<>());
        }
        Map<String, CanonicalResource> list = counts.get(t);
        String url = cr.getUrl();
        if (url == null) {
          url = cr.getId();
        }
        list.put(url, cr);
      }
    }
  }

  private void fillOutJson(CanonicalResource cr, JsonObject j) {
    j.addProperty("type", cr.fhirType());
    if (cr.hasId()) {           j.addProperty("id", cr.getId()); }
    if (cr.hasUrl()) {          j.addProperty("canonical", cr.getUrl()); }
    if (cr.hasVersion()) {      j.addProperty("version", cr.getVersion()); }
    if (cr.hasStatus()) {       j.addProperty("status", cr.getStatus().toCode()); }
    if (cr.hasPublisher()) {    j.addProperty("publisher", cr.getPublisher()); }
    if (cr.hasName()) {         j.addProperty("name", cr.getName()); }
    if (cr.hasTitle()) {        j.addProperty("title", cr.getTitle()); }
    if (cr.hasDate()) {         j.addProperty("date", cr.getDateElement().asStringValue()); }
    if (cr.hasExperimental()) { j.addProperty("experimental", cr.getExperimental()); }
    if (cr.hasDescription()) {  j.addProperty("description", cr.getDescription()); }
    if (cr.hasCopyright()) {    j.addProperty("copyright", cr.getCopyright()); }
    if (cr.hasJurisdiction()) { j.addProperty("jurisdiction", DataRenderer.display(ctxt, cr.getJurisdictionFirstRep())); }
        
    if (cr instanceof CodeSystem) {
      CodeSystem cs = (CodeSystem) cr;
      if (cs.hasCaseSensitive()) {    j.addProperty("caseSensitive", cs.getCaseSensitive()); }
      if (cs.hasValueSet()) {         j.addProperty("valueSet", cs.getValueSet()); }
      if (cs.hasHierarchyMeaning()) { j.addProperty("hierarchyMeaning", cs.getHierarchyMeaning().toCode());}
      if (cs.hasCompositional()) {    j.addProperty("compositional", cs.getCompositional()); }
      if (cs.hasVersionNeeded()) {    j.addProperty("versionNeeded", cs.getVersionNeeded()); }
      if (cs.hasContent()) {          j.addProperty("content", cs.getContent().toCode()); }
    }
    if (cr instanceof ValueSet) {
      ValueSet vs = (ValueSet) cr;
      if (vs.hasImmutable()) {        j.addProperty("immutable", vs.getImmutable()); }
    }
    if (cr instanceof ConceptMap) {
      ConceptMap cm = (ConceptMap) cr;
      if (cm.hasSourceScope()) {        j.addProperty("sourceScope", cm.getSourceScope().primitiveValue()); }
      if (cm.hasTargetScope()) {        j.addProperty("TargetScope", cm.getTargetScope().primitiveValue()); }
      for (ConceptMapGroupComponent g : cm.getGroup()) {
        if (g.hasSource()) {    
          if (!j.has("source")) {
            j.add("source", new JsonArray());
          }
          j.getAsJsonArray("source").add(g.getSource()); 
        }
        if (g.hasTarget()) {        
          if (!j.has("target")) {
            j.add("target", new JsonArray());
          }
          j.getAsJsonArray("target").add(g.getTarget()); 
        }
      }
    }
    if (cr instanceof StructureDefinition) {
      StructureDefinition sd = (StructureDefinition) cr;
      if (sd.hasFhirVersion()) {      j.addProperty("fhirVersion", sd.getFhirVersion().toCode()); }
      if (sd.hasKind()) {             j.addProperty("kind", sd.getKind().toCode()); }
      if (sd.hasAbstract()) {         j.addProperty("abstract", sd.getAbstract()); }
      if (sd.hasType()) {             j.addProperty("type", sd.getType()); }
      if (sd.hasDerivation()) {       j.addProperty("derivation", sd.getDerivation().toCode()); }
      if (sd.hasBaseDefinition()) {   j.addProperty("base", sd.getBaseDefinition()); }
      if (sd.hasType()) {             j.addProperty("type", sd.getType()); }

      for (StringType t : sd.getContextInvariant()) {
        if (!j.has("contextInv")) {
          j.add("contextInv", new JsonArray());
        }
        j.getAsJsonArray("contextInv").add(t.asStringValue()); 
      }
      
      for (StructureDefinitionContextComponent t : sd.getContext()) {
        if (!j.has("context")) {
          j.add("context", new JsonArray());
        }
        j.getAsJsonArray("context").add(t.getType().toCode()+":"+t.getExpression()); 
      }
      
      for (Coding t : sd.getKeyword()) {
        if (!j.has("keyword")) {
          j.add("keyword", new JsonArray());
        }
        j.getAsJsonArray("keyword").add(t.toString()); 
      }
    }
    if (cr instanceof OperationDefinition) {
      OperationDefinition od = (OperationDefinition) cr;
      if (od.hasAffectsState()) {    j.addProperty("affectsState", od.getAffectsState()); }
      if (od.hasCode()) {            j.addProperty("code", od.getCode()); }
      if (od.hasBase()) {            j.addProperty("base", od.getBase()); }
      if (od.hasSystem()) {          j.addProperty("system", od.getSystem()); }
      if (od.hasType())     {        j.addProperty("type", od.getType()); }
      if (od.hasInstance()) {        j.addProperty("instance", od.getInstance()); }
      for (CodeType t : od.getResource()) {
        if (!j.has("resource")) {
          j.add("resource", new JsonArray());
        }
        j.getAsJsonArray("resource").add(t.toString()); 
        opr.add(t.toString());
      }
    }
    if (cr instanceof SearchParameter) {
      SearchParameter sp = (SearchParameter) cr;
      if (sp.hasCode()) {            j.addProperty("code", sp.getCode()); }
      if (sp.hasType()) {            j.addProperty("code", sp.getType().toCode()); }
      for (CodeType t : sp.getBase()) {
        if (!j.has("resource")) {
          j.add("resource", new JsonArray());
        }
        j.getAsJsonArray("resource").add(t.toString()); 
        spr.add(t.toString());
      }
    }
  }

  private void renderResource(CanonicalResource cr) throws FHIRException, IOException, EOperationOutcome {
    RendererFactory.factory(cr, rc).render(cr);
    String s = new XhtmlComposer(false, true).compose(cr.getText().getDiv());
    new JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream(Utilities.path(target, cr.getUserString("filebase")+".json")), cr);
//    new XmlParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream(Utilities.path(target, cr.getUserString("filebase")+".xml")), cr);
    genPage(cr.fhirType()+"-"+cr.getIdBase(), summaryForResource(cr) + s, Utilities.path(target, cr.getUserString("filebase")+".html"));  
    cr.setText(null);
  }


  private String summaryForResource(CanonicalResource cr) {
    StringBuilder b = new StringBuilder();
    b.append("<table class=\"grid\">\r\n");
    b.append("<tr><td>URL</td><td>"+cr.getUrl()+"</td></tr>\r\n");
    b.append("<tr><td>Version</td><td>"+cr.getVersion()+"</td></tr>\r\n");
    b.append("<tr><td>Status</td><td>"+(cr.hasStatus() ? cr.getStatus().toCode() : "n/a") +"</td></tr>\r\n");
    b.append("<tr><td>Name</td><td>"+cr.getName()+"</td></tr>\r\n");
    b.append("<tr><td>Title</td><td>"+cr.getTitle()+"</td></tr>\r\n");
    b.append("<tr><td>Source</td><td><a href=\""+cr.getUserString("purl")+"\">"+cr.getUserString("pid")+"</a>:"+cr.getUserString("pname")+"</td></tr>\r\n");
    b.append("</table>");
    b.append("<br>");
    return b.toString();
  }

  private String determinePath(NpmPackage npm, CanonicalResource cr) {
    return npm.getWebLocation();
  }

  private boolean isCoreDefinition(CanonicalResource cr, String pid) {
    return cr.getUrl().startsWith("http://hl7.org/fhir/"+cr.fhirType()) ||
        cr.getUrl().startsWith("http://hl7.org/fhir/1.0/"+cr.fhirType()) ||
        cr.getUrl().startsWith("http://hl7.org/fhir/3.0/"+cr.fhirType()) ||
        cr.getUrl().startsWith("http://hl7.org/fhir/4.0/"+cr.fhirType()) ||
        cr.getUrl().startsWith("http://hl7.org/fhir/5.0/"+cr.fhirType()) ||
        cr.getUrl().startsWith("http://hl7.org/fhir/4.3/"+cr.fhirType()) ||
        pid.startsWith("hl7.fhir.xver");
  }

  private Resource loadResource(String pid, String parseVersion, String type, String id, byte[] source) {
    try {
      if (parseVersion.equals("current")) {
        return null;
      }
      if (VersionUtilities.isR3Ver(parseVersion)) {
        org.hl7.fhir.dstu3.model.Resource res;
        res = new org.hl7.fhir.dstu3.formats.JsonParser(true).parse(source);
        return VersionConvertorFactory_30_50.convertResource(res);
      } else if (VersionUtilities.isR4Ver(parseVersion)) {
        org.hl7.fhir.r4.model.Resource res;
        res = new org.hl7.fhir.r4.formats.JsonParser(true, true).parse(source);
        return VersionConvertorFactory_40_50.convertResource(res);
      } else if (VersionUtilities.isR2BVer(parseVersion)) {
        org.hl7.fhir.dstu2016may.model.Resource res;
        res = new org.hl7.fhir.dstu2016may.formats.JsonParser(true).parse(source);
        return VersionConvertorFactory_14_50.convertResource(res);
      } else if (VersionUtilities.isR2Ver(parseVersion)) {
        org.hl7.fhir.dstu2.model.Resource res;
        res = new org.hl7.fhir.dstu2.formats.JsonParser(true).parse(source);

        BaseAdvisor_10_50 advisor = new IGR2ConvertorAdvisor5();
        return VersionConvertorFactory_10_50.convertResource(res, advisor);
      } else if (VersionUtilities.isR4BVer(parseVersion)) {
        org.hl7.fhir.r4b.model.Resource res;
        res = new org.hl7.fhir.r4b.formats.JsonParser(true).parse(source);
        return VersionConvertorFactory_43_50.convertResource(res);
      } else if (VersionUtilities.isR5Ver(parseVersion)) {
        return new JsonParser(true, true).parse(source);
      } else if (Utilities.existsInList(parseVersion, "4.6.0", "3.5.0", "1.8.0")) {
        return null;
      } else {
        throw new Exception("Unsupported version "+parseVersion);
      }    

    } catch (Exception e) {
      System.out.println("Error loading "+type+"/"+id+" from "+pid+"("+parseVersion+"):" +e.getMessage());
      e.printStackTrace();
      return null;
    }
  }


  @Override
  public boolean isDatatype(String typeSimple) {
    return ctxt.isDatatype(typeSimple);
  }

  @Override
  public boolean isResource(String typeSimple) {
    return ctxt.isResource(typeSimple);
  }

  @Override
  public boolean hasLinkFor(String typeSimple) {
    return false;
  }


  @Override
  public String getLinkFor(String corePath, String typeSimple) {
    return null;
  }


  @Override
  public BindingResolution resolveBinding(StructureDefinition def, ElementDefinitionBindingComponent binding, String path) throws FHIRException {
    return resolveBinding(def, binding.getValueSet(), path);
  }


  @Override
  public BindingResolution resolveBinding(StructureDefinition def, String url, String path) throws FHIRException {
    ValueSet vs = ctxt.fetchResource(ValueSet.class, url);
    if (vs != null) {
      return new BindingResolution(vs.present(), vs.getUserData("filebase")+".html");
    } else { 
      return new BindingResolution("todo", "http://none.none/none");
    }
  }


  @Override
  public String getLinkForProfile(StructureDefinition profile, String url) {
    return null;
  }


  @Override
  public boolean prependLinks() {
    return false;
  }


  @Override
  public String getLinkForUrl(String corePath, String s) {
    // TODO Auto-generated method stub
    return null;
  }
}
