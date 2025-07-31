package org.hl7.fhir.igtools.publisher.xig;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;

import org.hl7.fhir.convertors.analytics.PackageVisitor;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.xml.sax.SAXException;

public class XIGGenerator {

  private static final String SNOMED_EDITION = "900000000000207008"; // international

  private String target;
  private String cache;
  private FilesystemPackageCacheManager pcm;

  private String date;
    
  public static void main(String[] args) throws Exception {
    new XIGGenerator(args[0], args[1]).execute(Integer.parseInt(args[3]));
  }

  public XIGGenerator(String target, String cache) throws FHIRException, IOException, URISyntaxException {
    super();
    this.target = target;
    this.cache = cache;
    FileUtilities.createDirectory(cache);
    pcm = new FilesystemPackageCacheManager.Builder().build();
    String ds = new SimpleDateFormat("dd MMM yyyy", new Locale("en", "US")).format(Calendar.getInstance().getTime());
    String dl = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ", new Locale("en", "US")).format(Calendar.getInstance().getTime());
    date = "<span title=\""+dl+"\">"+ds+"</span>";    
  }
  
  public void execute(int step) throws IOException, ParserConfigurationException, SAXException, FHIRException, EOperationOutcome, ParseException {
    ProfileUtilities.setSuppressIgnorableExceptions(true);

    long ms = System.currentTimeMillis();

    System.out.println("Start Step "+step);

    File tgt = new File(target);
    if (step == 1 || step == 0) {
      tgt.delete();
    }

    PackageVisitor pv = new PackageVisitor();

    pv.getResourceTypes().add("Account");
    pv.getResourceTypes().add("ActivityDefinition");
    pv.getResourceTypes().add("ActorDefinition");
    pv.getResourceTypes().add("AdministrableProductDefinition");
    pv.getResourceTypes().add("AdverseEvent");
    pv.getResourceTypes().add("AllergyIntolerance");
    pv.getResourceTypes().add("Appointment");
    pv.getResourceTypes().add("AppointmentResponse");
    pv.getResourceTypes().add("ArtifactAssessment");
    pv.getResourceTypes().add("AuditEvent");
    pv.getResourceTypes().add("Basic");
    pv.getResourceTypes().add("Binary");
    pv.getResourceTypes().add("BiologicallyDerivedProduct");
    pv.getResourceTypes().add("BiologicallyDerivedProductDispense");
    pv.getResourceTypes().add("BodySite");
    pv.getResourceTypes().add("BodyStructure");
    pv.getResourceTypes().add("Bundle");
    pv.getResourceTypes().add("CapabilityStatement");
    pv.getResourceTypes().add("CarePlan");
    pv.getResourceTypes().add("CareTeam");
    pv.getResourceTypes().add("CatalogEntry");
    pv.getResourceTypes().add("ChargeItem");
    pv.getResourceTypes().add("ChargeItemDefinition");
    pv.getResourceTypes().add("Citation");
    pv.getResourceTypes().add("Claim");
    pv.getResourceTypes().add("ClaimResponse");
    pv.getResourceTypes().add("ClinicalAssessment");
    pv.getResourceTypes().add("ClinicalImpression");
    pv.getResourceTypes().add("ClinicalUseDefinition");
    pv.getResourceTypes().add("CodeSystem");
    pv.getResourceTypes().add("Communication");
    pv.getResourceTypes().add("CommunicationRequest");
    pv.getResourceTypes().add("CompartmentDefinition");
    pv.getResourceTypes().add("Composition");
    pv.getResourceTypes().add("ConceptMap");
    pv.getResourceTypes().add("ConditionDefinition");
    pv.getResourceTypes().add("ConditionProblem)");
    pv.getResourceTypes().add("Consent");
    pv.getResourceTypes().add("Contract");
    pv.getResourceTypes().add("Coverage");
    pv.getResourceTypes().add("CoverageEligibilityRequest");
    pv.getResourceTypes().add("CoverageEligibilityResponse");
    pv.getResourceTypes().add("DataElement");
    pv.getResourceTypes().add("DetectedIssue");
    pv.getResourceTypes().add("Device");
    pv.getResourceTypes().add("DeviceAlert");
    pv.getResourceTypes().add("DeviceAssociation");
    pv.getResourceTypes().add("DeviceComponent");
    pv.getResourceTypes().add("DeviceDefinition");
    pv.getResourceTypes().add("DeviceDispense");
    pv.getResourceTypes().add("DeviceMetric");
    pv.getResourceTypes().add("DeviceRequest");
    pv.getResourceTypes().add("DeviceUsage");
    pv.getResourceTypes().add("DeviceUseStatement");
    pv.getResourceTypes().add("DiagnosticReport");
    pv.getResourceTypes().add("DocumentManifest");
    pv.getResourceTypes().add("DocumentReference");
    pv.getResourceTypes().add("EffectEvidenceSynthesis");
    pv.getResourceTypes().add("EligibilityRequest");
    pv.getResourceTypes().add("EligibilityResponse");
    pv.getResourceTypes().add("Encounter");
    pv.getResourceTypes().add("EncounterHistory");
    pv.getResourceTypes().add("Endpoint");
    pv.getResourceTypes().add("EnrollmentRequest");
    pv.getResourceTypes().add("EnrollmentResponse");
    pv.getResourceTypes().add("EpisodeOfCare");
    pv.getResourceTypes().add("EventDefinition");
    pv.getResourceTypes().add("Evidence");
    pv.getResourceTypes().add("EvidenceReport");
    pv.getResourceTypes().add("EvidenceVariable");
    pv.getResourceTypes().add("ExampleScenario");
    pv.getResourceTypes().add("ExpansionProfile");
    pv.getResourceTypes().add("ExplanationOfBenefit");
    pv.getResourceTypes().add("FamilyMemberHistory");
    pv.getResourceTypes().add("Flag");
    pv.getResourceTypes().add("FormularyItem");
    pv.getResourceTypes().add("GenomicStudy");
    pv.getResourceTypes().add("Goal");
    pv.getResourceTypes().add("GraphDefinition");
    pv.getResourceTypes().add("Group");
    pv.getResourceTypes().add("GuidanceResponse");
    pv.getResourceTypes().add("HealthcareService");
    pv.getResourceTypes().add("ImagingManifest");
    pv.getResourceTypes().add("ImagingSelection");
    pv.getResourceTypes().add("ImagingStudy");
    pv.getResourceTypes().add("Immunization");
    pv.getResourceTypes().add("ImmunizationEvaluation");
    pv.getResourceTypes().add("ImmunizationRecommendation");
    pv.getResourceTypes().add("ImplementationGuide");
    pv.getResourceTypes().add("Ingredient");
    pv.getResourceTypes().add("InsurancePlan");
    pv.getResourceTypes().add("InsuranceProduct");
    pv.getResourceTypes().add("InventoryItem");
    pv.getResourceTypes().add("InventoryReport");
    pv.getResourceTypes().add("Invoice");
    pv.getResourceTypes().add("Library");
    pv.getResourceTypes().add("Linkage");
    pv.getResourceTypes().add("List");
    pv.getResourceTypes().add("Location");
    pv.getResourceTypes().add("ManufacturedItemDefinition");
    pv.getResourceTypes().add("Measure");
    pv.getResourceTypes().add("MeasureReport");
    pv.getResourceTypes().add("Media");
    pv.getResourceTypes().add("Medication");
    pv.getResourceTypes().add("MedicationAdministration");
    pv.getResourceTypes().add("MedicationDispense");
    pv.getResourceTypes().add("MedicationKnowledge");
    pv.getResourceTypes().add("MedicationRequest");
    pv.getResourceTypes().add("MedicationStatement");
    pv.getResourceTypes().add("MedicinalProduct");
    pv.getResourceTypes().add("MedicinalProductAuthorization");
    pv.getResourceTypes().add("MedicinalProductContraindication");
    pv.getResourceTypes().add("MedicinalProductDefinition");
    pv.getResourceTypes().add("MedicinalProductIndication");
    pv.getResourceTypes().add("MedicinalProductIngredient");
    pv.getResourceTypes().add("MedicinalProductInteraction");
    pv.getResourceTypes().add("MedicinalProductManufactured");
    pv.getResourceTypes().add("MedicinalProductPackaged");
    pv.getResourceTypes().add("MedicinalProductPharmaceutical");
    pv.getResourceTypes().add("MedicinalProductUndesirableEffect");
    pv.getResourceTypes().add("MessageDefinition");
    pv.getResourceTypes().add("MessageHeader");
    pv.getResourceTypes().add("MolecularDefinition");
    pv.getResourceTypes().add("MolecularSequence");
    pv.getResourceTypes().add("NamingSystem");
    pv.getResourceTypes().add("NutritionIntake");
    pv.getResourceTypes().add("NutritionOrder");
    pv.getResourceTypes().add("NutritionProduct");
    pv.getResourceTypes().add("Observation");
    pv.getResourceTypes().add("ObservationDefinition");
    pv.getResourceTypes().add("OperationDefinition");
    pv.getResourceTypes().add("OperationOutcome");
    pv.getResourceTypes().add("Organization");
    pv.getResourceTypes().add("OrganizationAffiliation");
    pv.getResourceTypes().add("PackagedProductDefinition");
    pv.getResourceTypes().add("Parameters");
    pv.getResourceTypes().add("Patient");
    pv.getResourceTypes().add("PaymentNotice");
    pv.getResourceTypes().add("PaymentReconciliation");
    pv.getResourceTypes().add("Permission");
    pv.getResourceTypes().add("Person");
    pv.getResourceTypes().add("PersonalRelationship");
    pv.getResourceTypes().add("PlanDefinition");
    pv.getResourceTypes().add("Practitioner");
    pv.getResourceTypes().add("PractitionerRole");
    pv.getResourceTypes().add("Procedure");
    pv.getResourceTypes().add("ProcedureRequest");
    pv.getResourceTypes().add("ProcessRequest");
    pv.getResourceTypes().add("ProcessResponse");
    pv.getResourceTypes().add("Provenance");
    pv.getResourceTypes().add("Questionnaire");
    pv.getResourceTypes().add("QuestionnaireResponse");
    pv.getResourceTypes().add("ReferralRequest");
    pv.getResourceTypes().add("RegulatedAuthorization");
    pv.getResourceTypes().add("RelatedPerson");
    pv.getResourceTypes().add("RequestGroup");
    pv.getResourceTypes().add("RequestOrchestration");
    pv.getResourceTypes().add("Requirements");
    pv.getResourceTypes().add("ResearchDefinition");
    pv.getResourceTypes().add("ResearchElementDefinition");
    pv.getResourceTypes().add("ResearchStudy");
    pv.getResourceTypes().add("ResearchSubject");
    pv.getResourceTypes().add("RiskAssessment");
    pv.getResourceTypes().add("RiskEvidenceSynthesis");
    pv.getResourceTypes().add("Schedule");
    pv.getResourceTypes().add("SearchParameter");
    pv.getResourceTypes().add("Sequence");
    pv.getResourceTypes().add("ServiceDefinition");
    pv.getResourceTypes().add("ServiceRequest");
    pv.getResourceTypes().add("Slot");
    pv.getResourceTypes().add("Specialized");
    pv.getResourceTypes().add("Specimen");
    pv.getResourceTypes().add("SpecimenDefinition");
    pv.getResourceTypes().add("StructureDefinition");
    pv.getResourceTypes().add("StructureMap");
    pv.getResourceTypes().add("Subscription");
    pv.getResourceTypes().add("SubscriptionStatus");
    pv.getResourceTypes().add("SubscriptionTopic");
    pv.getResourceTypes().add("Substance");
    pv.getResourceTypes().add("SubstanceDefinition");
    pv.getResourceTypes().add("SubstanceNucleicAcid");
    pv.getResourceTypes().add("SubstancePolymer");
    pv.getResourceTypes().add("SubstanceProtein");
    pv.getResourceTypes().add("SubstanceReferenceInformation");
    pv.getResourceTypes().add("SubstanceSourceMaterial");
    pv.getResourceTypes().add("SubstanceSpecification");
    pv.getResourceTypes().add("SupplyDelivery");
    pv.getResourceTypes().add("SupplyRequest");
    pv.getResourceTypes().add("Support	Billing	Payment	General	");
    pv.getResourceTypes().add("Task");
    pv.getResourceTypes().add("TerminologyCapabilities");
    pv.getResourceTypes().add("TestPlan");
    pv.getResourceTypes().add("TestReport");
    pv.getResourceTypes().add("TestScript");
    pv.getResourceTypes().add("Transport");
    pv.getResourceTypes().add("ValueSet");
    pv.getResourceTypes().add("VerificationResult");
    pv.getResourceTypes().add("VisionPrescription");

    pv.setCache(cache);
    pv.setOldVersions(false);
    pv.setCorePackages(true);
    XIGDatabaseBuilder gather = new XIGDatabaseBuilder(target, step == 1 || step == 0, new SimpleDateFormat("dd MMM yyyy", new Locale("en", "US")).format(Calendar.getInstance().getTime()));
    pv.setProcessor(gather);

    gather.getResourceTypes().add("ActivityDefinition");
    gather.getResourceTypes().add("ActorDefinition");
    gather.getResourceTypes().add("CapabilityStatement");
    gather.getResourceTypes().add("CodeSystem");
    gather.getResourceTypes().add("ConceptMap");
    gather.getResourceTypes().add("ConditionDefinition");
    gather.getResourceTypes().add("DeviceDefinition");
    gather.getResourceTypes().add("EventDefinition");
    gather.getResourceTypes().add("ExampleScenario");
    gather.getResourceTypes().add("GraphDefinition");
    gather.getResourceTypes().add("Group");
    gather.getResourceTypes().add("ImplementationGuide");
    gather.getResourceTypes().add("Measure");
    gather.getResourceTypes().add("MeasureReport");
    gather.getResourceTypes().add("Medication");
    gather.getResourceTypes().add("MessageDefinition");
    gather.getResourceTypes().add("NamingSystem");
    gather.getResourceTypes().add("ObservationDefinition");
    gather.getResourceTypes().add("OperationDefinition");
    gather.getResourceTypes().add("PlanDefinition");
    gather.getResourceTypes().add("Questionnaire");
    gather.getResourceTypes().add("Requirements");
    gather.getResourceTypes().add("SearchParameter");
    gather.getResourceTypes().add("SpecimenDefinition");
    gather.getResourceTypes().add("StructureDefinition");
    gather.getResourceTypes().add("StructureMap");
    gather.getResourceTypes().add("TerminologyCapabilities");
    gather.getResourceTypes().add("TestPlan");
    gather.getResourceTypes().add("TestReport");
    gather.getResourceTypes().add("TestScript");
    gather.getResourceTypes().add("ValueSet");


    pv.setCurrent(true);
    pv.setStep(step);
    pv.visitPackages();
    gather.finish(step == 0 || step == 3);

    System.out.println("Finished Step "+step+": "+Utilities.describeDuration(System.currentTimeMillis() - ms));
    System.out.println("File "+target+", size is "+Utilities.describeSize(tgt.length()));
  }
//
//  private void test(File tgt) {
//    try {
//      Connection con = DriverManager.getConnection("jdbc:sqlite:"+tgt.getAbsolutePath());
//      Statement q = con.createStatement();
//      q.execute("select JsonR5 from Contents where ResourceKey = 228");
//      byte[] cnt = q.getResultSet().getBytes(1);
//      cnt = XIGDatabaseBuilder.unGzip(cnt);
//      String s = new String(cnt);
//      System.out.println(s);
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
//    throw new Error("Done");
//    
//  }

}
