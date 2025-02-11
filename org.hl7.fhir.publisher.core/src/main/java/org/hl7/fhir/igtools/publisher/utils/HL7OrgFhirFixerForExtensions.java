package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;

public class HL7OrgFhirFixerForExtensions {

  public static void main(String[] args) throws IOException {
    File folderRoot = new File("/Users/grahamegrieve/web/www.hl7.org.fhir");
    File folderExt = new File("/Users/grahamegrieve/web/www.hl7.org.fhir/extensions");
    new HL7OrgFhirFixerForExtensions().execute(folderRoot, folderExt);
  }

  void execute(File folderRoot, File folderExt) throws IOException {

    scanForEmptyFolders(folderRoot);
  }

  private void scanForEmptyFolders(File ff) throws IOException {
    boolean indexed = false;
    for (File f : ff.listFiles()) {
      if (f.isDirectory()) {
        if (!Utilities.existsInList(f.getName(), "assets", "assets-hist", "dist", "dist-hist", "less", "images", "less-glyphicons", "less-joyo", "html", "css",
            "quick", "qicore", "external", "examples", "sid", ".git", "ehrsrle")) {
          scanForEmptyFolders(f);
        }
      } else {
        if (Utilities.existsInList(f.getName(), "index.html", "index.php", "web.config", ".no.index")) {
          indexed = true;
        }
        if (f.getName().equals(".no.index")) {
          f.delete();
        }
        if (f.getName().equals("web.config")) {
          String s = FileUtilities.fileToString(f);
          String url = s.substring(s.indexOf("destination=")+13);
          url = url.substring(0, url.indexOf("\""));
          String rf = genRedirect(url.replace("http://hl7.org/fhir", ""));
          String dst = Utilities.path(ff, "index.php");
          FileUtilities.stringToFile(rf, dst);
          f.delete();          
        }
      }
    }
  }

  private boolean lowerCaseHtmlExists(File ff) throws IOException {
    String path = Utilities.path(ff.getParentFile(), ff.getName().toLowerCase()+".html");
    return new File(path).exists();
  }

  private String genRedirect(String url) {
    return "<?php\r\n"+
"function Redirect($url)\r\n"+
"{\r\n"+
"  header('Location: ' . $url, true, 302);\r\n"+
"  exit();\r\n"+
"}\r\n"+
"\r\n"+
"$accept = $_SERVER['HTTP_ACCEPT'];\r\n"+
"if (strpos($accept, 'html') !== false)\r\n"+
"  Redirect('/fhir"+url+"');\r\n"+
"else \r\n"+
"  header($_SERVER[\"SERVER_PROTOCOL\"].\" 404 Not Found\");\r\n"+
"?>\r\n"+
"\r\n"+
"{ \"message\" : \"not-found\" }\r\n"+
"\r\n";
}
  
  private boolean isResourceName(String name) {
    return Utilities.existsInList(name,
        "Account", "ActivityDefinition", "ActorDefinition", "AdministrableProductDefinition", "AdverseEvent", "AllergyIntolerance", "Appointment", "AppointmentResponse", "ArtifactAssessment", "AuditEvent",
        "Basic", "Binary", "BiologicallyDerivedProduct", "BiologicallyDerivedProductDispense", "BodySite", "BodyStructure", "Bundle", "CanonicalResource", "CapabilityStatement", "CarePlan", "CareTeam",
        "CatalogEntry", "ChargeItem", "ChargeItemDefinition", "Citation", "Claim", "ClaimResponse", "ClinicalImpression", "ClinicalUseDefinition", "CodeSystem", "Communication", "CommunicationRequest", 
        "CompartmentDefinition", "Composition", "ConceptMap", "Condition", "ConditionDefinition", "Conformance", "Consent", "Contract", "Coverage", "CoverageEligibilityRequest", "CoverageEligibilityResponse", 
        "DataElement", "DetectedIssue", "Device", "DeviceAssociation", "DeviceComponent", "DeviceDefinition", "DeviceDispense", "DeviceMetric", "DeviceRequest", "DeviceUsage", "DeviceUseRequest",
        "DeviceUseStatement", "DiagnosticOrder", "DiagnosticReport", "DocumentManifest", "DocumentReference", "DomainResource", "EffectEvidenceSynthesis", "EligibilityRequest", "EligibilityResponse",
        "Encounter", "EncounterHistory", "Endpoint", "EnrollmentRequest", "EnrollmentResponse", "EpisodeOfCare", "EventDefinition", "Evidence", "EvidenceReport", "EvidenceVariable", "ExampleScenario",
        "ExpansionProfile", "ExplanationOfBenefit", "FamilyMemberHistory", "Flag", "FormularyItem", "GenomicStudy", "Goal", "GraphDefinition", "Group", "GuidanceResponse", "HealthcareService", "ImagingManifest",
        "ImagingObjectSelection", "ImagingSelection", "ImagingStudy", "Immunization", "ImmunizationEvaluation", "ImmunizationRecommendation", "ImplementationGuide", "Ingredient", "InsurancePlan", "InventoryItem",
        "InventoryReport", "Invoice", "Library", "Linkage", "List", "Location", "ManufacturedItemDefinition", "Measure", "MeasureReport", "Media", "Medication", "MedicationAdministration", "MedicationDispense", 
        "MedicationKnowledge", "MedicationOrder", "MedicationPrescription", "MedicationRequest", "MedicationStatement", "MedicationUsage", "MedicinalProduct", "MedicinalProductAuthorization", "MedicinalProductContraindication", 
        "MedicinalProductDefinition", "MedicinalProductIndication", "MedicinalProductIngredient", "MedicinalProductInteraction", "MedicinalProductManufactured", "MedicinalProductPackaged", 
        "MedicinalProductPharmaceutical", "MedicinalProductUndesirableEffect", "MessageDefinition", "MessageHeader", "MetadataResource", "MolecularSequence", "NamingSystem", "NutritionIntake", "NutritionOrder",
        "NutritionProduct", "Observation", "ObservationDefinition", "OperationDefinition", "OperationOutcome", "Order", "OrderResponse", "Organization", "OrganizationAffiliation", "PackagedProductDefinition",
        "Parameters", "Patient", "PaymentNotice", "PaymentReconciliation", "Permission", "Person", "PlanDefinition", "Practitioner", "PractitionerRole", "Procedure", "ProcedureRequest", "ProcessRequest",
        "ProcessResponse", "Provenance", "Questionnaire", "QuestionnaireResponse", "ReferralRequest", "RegulatedAuthorization", "RelatedPerson", "RequestGroup", "RequestOrchestration", "Requirements", 
        "ResearchDefinition", "ResearchElementDefinition", "ResearchStudy", "ResearchSubject", "Resource", "RiskAssessment", "RiskEvidenceSynthesis", "Schedule", "SearchParameter", "Sequence", "ServiceDefinition",
        "ServiceRequest", "Slot", "Specimen", "SpecimenDefinition", "StructureDefinition", "StructureMap", "Subscription", "SubscriptionStatus", "SubscriptionTopic", "Substance", "SubstanceDefinition",
        "SubstanceNucleicAcid", "SubstancePolymer", "SubstanceProtein", "SubstanceReferenceInformation", "SubstanceSourceMaterial", "SubstanceSpecification", "SupplyDelivery", "SupplyRequest", "Task",
        "TerminologyCapabilities", "TestPlan", "TestReport", "TestScript", "Transport", "ValueSet", "VerificationResult", "VisionPrescription",
        
        "Supply", "Contraindication", "CapabilityStatement2", "ClinicalUseIssue", "DiagnosticRequest", "NutritionRequest", 
        "DecisionSupportServiceModule", "ModuleDefinition", "GuidanceRequest", "DecisionSupportRule", "ModuleMetadata", "OrderSet", "ModuleDefinition", "GuidanceRequest", "EvidenceFocus","ItemInstance"
        );
  }


}