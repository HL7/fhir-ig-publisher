package org.hl7.fhir.igtools.publisher.xig;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

import javax.xml.parsers.ParserConfigurationException;

import org.hl7.fhir.convertors.analytics.PackageVisitor;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.igtools.publisher.loaders.PublisherLoader;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.model.Parameters;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.ToolsVersion;
import org.xml.sax.SAXException;

public class XIGGenerator {

  private static final String SNOMED_EDITION = "900000000000207008"; // international

  private String target;
  private XIGInformation info = new XIGInformation();

  private FilesystemPackageCacheManager pcm;

  private String date;
    
  public static void main(String[] args) throws Exception {
    new XIGGenerator(args[0]).execute();
  }

  public XIGGenerator(String target) throws FHIRException, IOException, URISyntaxException {
    super();
    this.target = target;
    pcm = new FilesystemPackageCacheManager(true, ToolsVersion.TOOLS_VERSION);
    NpmPackage npm = pcm.loadPackage("hl7.fhir.r5.core#5.0.0-ballot");
    info.setCtxt(new SimpleWorkerContext.SimpleWorkerContextBuilder().fromPackage(npm, new PublisherLoader(npm, SpecMapManager.fromPackage(npm), npm.getWebLocation(), null).makeLoader()));
    info.getCtxt().setAllowLazyLoading(false);
    info.getCtxt().setAllowLoadingDuplicates(true);
//    this.ctxt.connectToTSServer(TerminologyClientFactory.makeClient("http://tx.fhir.org", "fhir/publisher", FhirPublication.R5), null);
    info.getCtxt().setExpansionProfile(buildExpansionProfile());
    String ds = new SimpleDateFormat("dd MMM yyyy", new Locale("en", "US")).format(Calendar.getInstance().getTime());
    String dl = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ", new Locale("en", "US")).format(Calendar.getInstance().getTime());
    date = "<span title=\""+dl+"\">"+ds+"</span>";
    
    info.getJson().add("date", date);
    JsonObject doco = new JsonObject();
    info.getJson().add("_doco", doco);
    doco.add("packages", "An object containing of all the packages found, with {id}#{version} : package.json contents");
    doco.add("canonicals", "An array of all the canonical resources found, with details");
    addDoco(doco); 
    
    info.getJson().add("packages", new JsonObject());
    info.getJson().add("canonicals", new JsonArray()); 
  }

  private void addDoco(JsonObject doco) {
    JsonObject docoC = new JsonObject();
    doco.add("canonicalDetails", docoC);
    docoC.add("pid", "package where this resource was found");
    docoC.add("fver", "version of FHIR for this resource");
    docoC.add("published", "true if this is from a published package (as oppoosed to the ci-build");
    docoC.add("filebase", "the name of the file (same directory as this json file). Both .json and .html exist");
    docoC.add("path", "Where to find the HTML publication of this resource");
    docoC.add("resourceType", "the type of this resource");
    docoC.add("id", "id of the resource in the source package"); 
    docoC.add("canonical", "canonical URL, if there is one"); 
    docoC.add("version", "business version, if there is one"); 
    docoC.add("status", "status, if there is one"); 
    docoC.add("publisher", "stated publisher, if there is one"); 
    docoC.add("name", "name, if there is one"); 
    docoC.add("title", "title, if there is one"); 
    docoC.add("date", "stated date, if there is one"); 
    docoC.add("experimental", "whether the resource is marked experimental"); 
    docoC.add("description", "description, if there is one");
    docoC.add("copyright", "stated copyright, if there is one"); 
    docoC.add("jurisdiction", "stated jurisdiction, if there is one"); 
        

    docoC.add("abstract", "abstract, if stated (StructureDefinition)"); 
    docoC.add("affectsState", "affectsState, if stated (OperationDefinition)"); 
    docoC.add("base", "base, if stated (OperationDefinition, StructureDefinition)"); 
    docoC.add("caseSensitive", "caseSensitive, if stated (CodeSystem)"); 
    docoC.add("code", "code, if stated (OperationDefinition, SearchParameter)"); 
    docoC.add("compositional", "compositional, if stated (CodeSystem)"); 
    docoC.add("content", "content, if stated (CodeSystem)"); 
    docoC.add("contextInvs", "array of contextInvs in the resource (StructureDefinition)"); 
    docoC.add("contexts", "array of contexts (type:expression in the resource (StructureDefinition)"); 
    docoC.add("derivation", "derivation, if stated (StructureDefinition)"); 
    docoC.add("fhirVersion", "Stated version in the resource (CapabilityStatement, StructureDefinition)"); 
    docoC.add("formats", "array of formats and patchFormats in the resource (CapabilityStatement)"); 
    docoC.add("hierarchyMeaning", "hierarchyMeaning, if stated (CodeSystem)"); 
    docoC.add("immutable", "immutable, if stated (ValueSet)"); 
    docoC.add("implementationGuides", "array of implementationGuides in the resource (CapabilityStatement)"); 
    docoC.add("imports", "array of imports  in the resource (CapabilityStatement)"); 
    docoC.add("instance", "instance, if stated (OperationDefinition)"); 
    docoC.add("instantiates", "array of instantiates claims in the resource (CapabilityStatement)"); 
    docoC.add("keywords", "array of keywords in the resource (StructureDefinition)"); 
    docoC.add("kind", "kind, if stated (CapabilityStatement, NamingSystem, StructureDefinition)"); 
    docoC.add("languages", "array of languages in the resource (CapabilityStatement)"); 
    docoC.add("resources", "array of base resource, in the resource (OperationDefinition)"); 
    docoC.add("resourcesSP", "array of base resource, in the resource (SearchParameter)"); 
    docoC.add("sourceScope", "sourceScope, if stated (ConceptMap"); 
    docoC.add("sources", "array of sources in the resource (ConceptMap"); 
    docoC.add("supplements", "supplements, if stated (CodeSystem)"); 
    docoC.add("system", "system, if stated (OperationDefinition)"); 
    docoC.add("targetScope", "targetScope, if stated (ConceptMap"); 
    docoC.add("targets", "array of targets in the resource (ConceptMap"); 
    docoC.add("type", "type, if stated (NamingSystem, OperationDefinition, SearchParameter, StructureDefinition)"); 
    docoC.add("valueSet", "valueSet, if stated (CodeSystem)"); 
    docoC.add("versionNeeded", "versionNeeded, if stated (CodeSystem)");
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
  
  public void execute() throws IOException, ParserConfigurationException, SAXException, FHIRException, EOperationOutcome {
    PackageVisitor pv = new PackageVisitor();
    pv.getResourceTypes().add("CapabilityStatement");
    pv.getResourceTypes().add("SearchParameter");
    pv.getResourceTypes().add("OperationDefinition");
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
    pv.setProcessor(new XIGLoader(info));
    pv.setCurrent(true);
    pv.visitPackages();
    
    info.buildUsageMap();

    new XIGRenderer(info, target, date).produce(pcm);
    printSummary(); 
  }

  private void printSummary() {
    System.out.println("");
    System.out.println("IGs: "+info.getPid().size());
    int i = 0;
    for (String s : Utilities.sorted(info.getCounts().keySet())) {
      i = i + info.getCounts().get(s).size();
      System.out.println(s+": "+info.getCounts().get(s).size());
    }
    System.out.println("Total: "+i);
  }

}
