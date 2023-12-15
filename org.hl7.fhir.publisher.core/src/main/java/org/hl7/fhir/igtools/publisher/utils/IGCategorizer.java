package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_10_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;


public class IGCategorizer {

  public static void main(String[] args) throws IOException {
    try {
    new IGCategorizer().process(args[0]);
    } catch (Throwable e) {
      System.out.println("Fatal: "+e.getMessage());
    }
  }
  
  private FilesystemPackageCacheManager pcm;
  
  public IGCategorizer() throws IOException {
    pcm = new FilesystemPackageCacheManager.Builder().build();
    pcm.setSuppressErrors(true);
  }
  
  private class IGInfo {
    private boolean content;
    private boolean rest;
    private boolean messaging;
    private boolean documents;
    
    private boolean clinicalCore;
    private boolean patientAdmin;
    private boolean carePlanning;
    private boolean financials;
    private boolean medsMgmt;
    private boolean medsReg;
    private boolean scheduling;
    private boolean diagnostics;
    private boolean measures;
    private boolean ebm;
    private boolean questionnaire;
    private boolean trials;
    
    private int profiles;
    private int extensions;
    private int logicals;
    private int operations;
    private int valuesets;
    private int codeSystems;
    private int tests;
    private int examples;

    public void update(JsonObject a) {
      if (content) {
        a.add("content", true);
      }
      if (rest) {
        a.add("rest", true);
      }
      if (messaging) {
        a.add("messaging", messaging);
      }
      if (documents) {
        a.add("documents", documents);
      }
      if (clinicalCore) {
        a.add("clinicalCore", clinicalCore);
      }
      if (patientAdmin) {
        a.add("patientAdmin", patientAdmin);
      }
      if (carePlanning) {
        a.add("carePlanning", carePlanning);
      }
      if (financials) {
        a.add("financials", financials);
      }
      if (medsMgmt) {
        a.add("medsMgmt", medsMgmt);
      }
      if (medsReg) {
        a.add("medsReg", medsReg);
      }
      if (scheduling) {
        a.add("scheduling", scheduling);
      }
      if (diagnostics) {
        a.add("diagnostics", diagnostics);
      }
      if (measures) {
        a.add("measures", measures);
      }
      if (ebm) {
        a.add("ebm", ebm);
      }
      if (questionnaire) {
        a.add("questionnaire", questionnaire);
      }
      if (trials) {
        a.add("trials", trials);
      }
      
      if (profiles > 0) {
        a.add("profiles", profiles);
      }
      if (extensions> 0) {
        a.add("extensions", extensions);
      }
      if (logicals> 0) {
        a.add("logicals", logicals);
      }
      if (operations > 0) {
        a.add("operations", operations);
      }
      if (valuesets > 0) {
        a.add("valuesets", valuesets);
      }
      if (codeSystems > 0) {
        a.add("codeSystems", codeSystems);
      }
      if (tests > 0) {
        a.add("tests", tests);
      }
      if (examples > 0) {
        a.add("examples", examples);
      }
    }

    public String dump() {
      String s = "";
      if (content) {
        s = s + " C";
      }
      if (rest) {
        s = s + " R";
      }
      if (messaging) {
        s = s + " M";
      }
      if (documents) {
        s = s + " D";
      }
      if (clinicalCore) {
        s = s + " cc";
      }
      if (patientAdmin) {
        s = s + "pa";
      }
      if (carePlanning) {
        s = s + "cp";
      }
      if (financials) {
        s = s + " fin";
      }
      if (medsMgmt) {
        s = s + " mm";
      }
      if (medsReg) {
        s = s + " mr";
      }
      if (scheduling) {
        s = s + " sc";
      }
      if (diagnostics) {
        s = s + " dg";
      }
      if (measures) {
        s = s + " ms";
      }
      if (ebm) {
        s = s + " eb";
      }
      if (questionnaire) {
        s = s + " qq";
      }
      if (trials) {
        s = s + " tr";
      }
      if (profiles > 0) {
        s = s + " p:"+profiles;
      }
      if (logicals > 0) {
        s = s + " l:"+logicals;
      }
      if (extensions> 0) {
        s = s + " x:"+extensions;
      }
      if (operations > 0) {
        s = s + " o:"+operations;
      }
      if (valuesets > 0) {
        s = s + " v:"+valuesets;
      }
      if (codeSystems > 0) {
        s = s + " c:"+codeSystems;
      }
      if (tests > 0) {
        s = s + " t:"+tests;
      }
      if (examples > 0) {
        s = s + " e:"+examples;
      }
      return s;
    }    
  }
  
  private void process(String path) throws IOException {
    JsonObject iglist = JsonParser.parseObject(new File(path));
    for (JsonObject ig : iglist.getJsonObjects("guides")) {
        processIG(ig);
    }  
    JsonParser.compose(iglist, new FileOutputStream(path));
  }

  private void processIG(JsonObject ig) {
    String name = ig.asString("npm-name");
    JsonObject analysis = ig.forceObject("analysis");
    analysis.getProperties().clear();
    for (JsonObject edition : ig.getJsonObjects("editions")) {
      if (edition.has("analysis")) {
        edition.remove("analysis");
      }
      String version = edition.asString("ig-version");
      try {
        IGInfo info = processIGEdition(ig, edition, analysis);

        info.update(analysis);
        System.out.println(name+"#"+version+": "+info.dump());
        break;
      } catch (Exception e) {
        System.out.println(name+"#"+version+": Error "+e.getMessage());
        e.printStackTrace();
        analysis.add("error", e.getMessage());
      }
    }  
  }

  private IGInfo processIGEdition(JsonObject ig, JsonObject edition, JsonObject analysis) throws FHIRException, IOException {
    IGInfo info = new IGInfo();
    NpmPackage npm = pcm.loadPackage(edition.asString("package"));

    for (String t : npm.listResources("CodeSystem", "ValueSet", "StructureDefinition", "OperationDefinition", 
        "SearchParameter", "ImplementationGuide", "TestScript", "Conformance", "CapabilityStatement", "MessageDefinition")) {
      Resource resource = loadResourceFromPackage(npm.loadResource(t), npm.fhirVersion());
      processResource(info, resource);
    }
    for (String t : npm.list("example")) {
      info.examples++;
    }
    return info;
  }
  
  private void processResource(IGInfo info, Resource resource) {
    if (resource.fhirType().equals("CodeSystem")) {
      info.codeSystems++;
    }
    if (resource.fhirType().equals("ValueSet")) {
      info.valuesets++;
    }
    if (resource.fhirType().equals("StructureDefinition")) {
      info.content = true;
      StructureDefinition sd = (StructureDefinition) resource;
      if (sd.getKind() == StructureDefinitionKind.LOGICAL) {
        info.logicals++;
      } else if (sd.getDerivation() == TypeDerivationRule.CONSTRAINT) {
        if ("Extension".equals(sd.getType())) {
          info.extensions++;          
        } else {
          info.profiles++;
          if (sd.getType().equals("Bundle")) {
            if (hasDocumentType(sd)) {
              info.documents = true;
            }
          }
          processProfileType(info, sd.getType());
        }
      }
    }

    if (resource.fhirType().equals("OperationDefinition")) {
      info.operations++;
    }
    if (resource.fhirType().equals("TestScript")) {
      info.tests++;
    }
    if (resource.fhirType().equals("MessageDefinition")) {
      info.messaging = true;
    }
    if (resource.fhirType().equals("CapabilityStatement") || resource.fhirType().equals("SearchParameter")) {
      info.rest = true;
    }
  }

  private void processProfileType(IGInfo info, String type) {
    switch (type) {
    
    case "AdverseEvent":
    case "AllergyIntolerance":
    case "ClinicalImpression":
    case "Condition":
    case "Consent":
    case "DetectedIssue":
    case "Device":
    case "DeviceRequest":
    case "DeviceUseRequest":
    case "DeviceUseStatement":
    case "Encounter":
    case "EpisodeOfCare":
    case "FamilyMemberHistory":
    case "Flag":
    case "Immunization":
    case "Media":
    case "Patient":
    case "Observation":
    case "Procedure":
    case "RelatedPerson":
    case "Organization":
    case "Practitioner":
    case "PractitionerRole":
    case "Person":
    case "RiskAssessment":
    case "Group":
    case "HealthcareService":
    case "Location":
    case "DiagnosticReport":
    case "OrganizationAffiliation":
    case "CarePlan":
    case "CareTeam":
    case "Goal":
    case "ServiceRequest":
    case "ProcedureRequest":
    case "ReferralRequest":
      info.clinicalCore = true;
      return;
      
    case "Account":
    case "ChargeItem":
    case "ChargeItemDefinition":
    case "Claim":
    case "ClaimResponse":
    case "Contract":
    case "Coverage":
    case "CoverageEligibilityRequest":
    case "CoverageEligibilityResponse":
    case "EligibilityRequest":
    case "EligibilityResponse":
    case "EnrollmentRequest":
    case "EnrollmentResponse":
    case "ExplanationOfBenefit":
    case "InsurancePlan":
    case "Invoice":
    case "PaymentNotice":
    case "PaymentReconciliation":
    case "ProcessRequest":
    case "ProcessResponse":
    case "VisionPrescription":
      info.financials = true;
      return;
      
    case "ActivityDefinition":
    case "Communication":
    case "CommunicationRequest":
    case "GuidanceResponse":
    case "NutritionOrder":
    case "Order":
    case "OrderResponse":
    case "PlanDefinition":
    case "RequestGroup":
    case "SupplyDelivery":
    case "SupplyRequest":
    case "Task":
      info.carePlanning = true;
      return;

    case "Appointment":
    case "AppointmentResponse":
    case "Schedule":
    case "Slot":
      info.scheduling = true;
      return;
      
    case "BiologicallyDerivedProduct":
    case "BodySite":
    case "BodyStructure":
    case "CatalogEntry":
    case "DiagnosticOrder":
    case "ImagingManifest":
    case "ImagingObjectSelection":
    case "ImagingStudy":
    case "Sequence":
    case "Specimen":
    case "SpecimenDefinition":
    case "ObservationDefinition":
      info.diagnostics = true;
      return;
      
    case "DocumentManifest":
    case "DocumentReference":
      info.documents = true;
      return;
      
    case "Measure":
    case "MeasureReport":
      info.measures = true;
      return;
      
    case "EffectEvidenceSynthesis":
    case "Evidence":
    case "EvidenceVariable":
    case "RiskEvidenceSynthesis":
      info.ebm = true;
      return;

    case "Substance":
    case "Medication":
    case "MedicationAdministration":
    case "MedicationDispense":
    case "MedicationOrder":
    case "MedicationRequest":
    case "MedicationStatement":
      info.clinicalCore = true;
      info.medsMgmt = true;
      return;

    case "MedicationKnowledge":
    case "ImmunizationEvaluation":
    case "ImmunizationRecommendation":
      info.medsMgmt = true;
      return;

    case "MedicinalProduct":
    case "MedicinalProductAuthorization":
    case "MedicinalProductContraindication":
    case "MedicinalProductIndication":
    case "MedicinalProductIngredient":
    case "MedicinalProductInteraction":
    case "MedicinalProductManufactured":
    case "MedicinalProductPackaged":
    case "MedicinalProductPharmaceutical":
    case "MedicinalProductUndesirableEffect":
    case "SubstanceNucleicAcid":
    case "SubstancePolymer":
    case "SubstanceProtein":
    case "SubstanceReferenceInformation":
    case "SubstanceSourceMaterial":
    case "SubstanceSpecification":
    case "MolecularSequence":
      info.medsReg = true;
      return;
      
    case "Questionnaire":
    case "QuestionnaireResponse":
      info.questionnaire = true;
      return;
      
    case "ResearchDefinition":
    case "ResearchElementDefinition":
    case "ResearchStudy":
    case "ResearchSubject":
      info.trials = true;
    }
  }

  private boolean hasDocumentType(StructureDefinition sd) {
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      if (ed.getPath().equals("Bundle.type") && ed.hasFixed() && "document".equals(ed.getFixed().primitiveValue())) {
        return true;
      }
    }
    for (ElementDefinition ed : sd.getDifferential().getElement()) {
      if (ed.getPath().equals("Bundle.type") && ed.hasFixed() && "document".equals(ed.getFixed().primitiveValue())) {
        return true;
      }
    }
    return false;
  }

  private Resource loadResourceFromPackage(InputStream s, String version) throws FHIRException, IOException {
    if (VersionUtilities.isR3Ver(version)) {
      return VersionConvertorFactory_30_50.convertResource(new org.hl7.fhir.dstu3.formats.JsonParser().parse(s), new BaseAdvisor_30_50(false));
    } else if (VersionUtilities.isR4Ver(version)) {
      return VersionConvertorFactory_40_50.convertResource(new org.hl7.fhir.r4.formats.JsonParser().parse(s));
    } else if (VersionUtilities.isR2Ver(version)) {
      return VersionConvertorFactory_10_50.convertResource(new org.hl7.fhir.dstu2.formats.JsonParser().parse(s));
    } else if (VersionUtilities.isR5Plus(version)) {
      return new org.hl7.fhir.r5.formats.JsonParser().parse(s);
    } else {
      throw new FHIRException("Unsupported version: "+version);
    }
  }
  
  
}