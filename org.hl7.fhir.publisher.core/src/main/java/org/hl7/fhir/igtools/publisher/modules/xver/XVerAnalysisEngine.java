package org.hl7.fhir.igtools.publisher.modules.xver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_10_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_10_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_43_50;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.modules.xver.XVerAnalysisEngine.LoadedFile;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.ConceptMap.ConceptMapGroupComponent;
import org.hl7.fhir.r5.model.ConceptMap.ConceptMapGroupUnmappedMode;
import org.hl7.fhir.r5.model.ConceptMap.SourceElementComponent;
import org.hl7.fhir.r5.model.ConceptMap.TargetElementComponent;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.Enumerations.BindingStrength;
import org.hl7.fhir.r5.model.Enumerations.CodeSystemContentMode;
import org.hl7.fhir.r5.model.Enumerations.ConceptMapRelationship;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.r5.model.StructureMap.StructureMapGroupComponent;
import org.hl7.fhir.r5.model.StructureMap.StructureMapGroupInputComponent;
import org.hl7.fhir.r5.model.StructureMap.StructureMapGroupRuleComponent;
import org.hl7.fhir.r5.model.StructureMap.StructureMapGroupRuleDependentComponent;
import org.hl7.fhir.r5.model.StructureMap.StructureMapGroupRuleSourceComponent;
import org.hl7.fhir.r5.model.StructureMap.StructureMapGroupRuleTargetComponent;
import org.hl7.fhir.r5.model.StructureMap.StructureMapGroupTypeMode;
import org.hl7.fhir.r5.model.StructureMap.StructureMapInputMode;
import org.hl7.fhir.r5.model.StructureMap.StructureMapStructureComponent;
import org.hl7.fhir.r5.model.StructureMap.StructureMapTransform;
import org.hl7.fhir.r5.model.UriType;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.renderers.ConceptMapRenderer.IMultiMapRendererAdvisor;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities;
import org.hl7.fhir.r5.terminologies.ConceptMapUtilities;
import org.hl7.fhir.r5.terminologies.ConceptMapUtilities.TranslatedCode;
import org.hl7.fhir.r5.terminologies.ValueSetUtilities;
import org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.DebugUtilities;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.validation.BaseValidator.BooleanHolder;

/**
 * This class runs as a pre-compile step for the xversion IG
 * 
 * It takes one parameter, the root directory of the cross version IG git repo.
 * It loads R2-R5 definitions, reads all the conceptmaps in the IG source, and then
 * generates the following:
 * 
 *  - page fragments in input/includes with HTML summaries of the content 
 *  - extension definitions in input/extensions 
 *  - 
 */
public class XVerAnalysisEngine implements IMultiMapRendererAdvisor {

  public class LoadedFile {

    private String filename;
    private String source;
    private CanonicalResource cr;
    private String id;
    private String nid;
    public boolean changed;

    public LoadedFile(File f, String source, CanonicalResource cr) {
      this.filename = f.getAbsolutePath();
      this.source = source;
      this.cr = cr;
    }
  }

  public enum MakeLinkMode {
    INWARD, OUTWARD, CHAIN, ORIGIN_CHAIN

  }

  public enum XVersions {
    VER_2_3, VER_3_4, VER_4_4B, VER_4B_5;
  }

  public static void main(String[] args) throws Exception {
    new XVerAnalysisEngine().execute(args[0]);
    System.out.println("Done");
  }

  public boolean process(String path) throws FHIRException, IOException {
    return execute(path);
  }

  private Map<String, VersionDefinitions> versions = new HashMap<>();
  private Map<String, ConceptMap> conceptMaps = new HashMap<>();
  private Map<String, ConceptMap> conceptMapsByUrl = new HashMap<>();
  private Map<String, ConceptMap> conceptMapsByScope = new HashMap<>();
  private Map<String, StructureMap> structureMaps = new HashMap<>();
  private VersionDefinitions vdr2;
  private VersionDefinitions vdr3;
  private VersionDefinitions vdr4;
  private VersionDefinitions vdr4b;
  private VersionDefinitions vdr5;
  private List<ElementDefinitionLink> allLinks = new ArrayList<>();
  private List<SourcedElementDefinition> terminatingElements = new ArrayList<>();
  private List<SourcedElementDefinition> origins = new ArrayList<>();
  private boolean failures;

  private boolean execute(String folder) throws FHIRException, IOException {
    // checkIds(folder);
    
    failures = false;
    loadVersions();
    loadConceptMaps(folder);
    loadStructureMaps(folder);

    logProgress("Checking Maps");
    // 1. sanity check on resource and element maps
    checkMaps();

    logProgress("Building Links");
    // 2. build all the links. At the end, chains are all the terminating elements
    buildLinks(XVersions.VER_2_3, vdr2, cm("resources-2to3"), cm("elements-2to3"), vdr3, false);
    buildLinks(XVersions.VER_3_4, vdr3, cm("resources-3to4"), cm("elements-3to4"), vdr4, false);
    buildLinks(XVersions.VER_4_4B, vdr4, cm("resources-4to4b"), cm("elements-4to4b"), vdr4b, false);
    buildLinks(XVersions.VER_4B_5, vdr4b, cm("resources-4bto5"), cm("elements-4bto5"), vdr5, true);    

    logProgress("Building & processing Chains");
    findTerminalElements();    
    for (SourcedElementDefinition te : terminatingElements) {
      if (te.getEd().getPath().startsWith("SampledData.interval")) {
        DebugUtilities.breakpoint();
      }
      identifyChain(te);
    }
    checkAllLinksInChains();

    for (SourcedElementDefinition te : terminatingElements) {
      if (te.getEd().getPath().startsWith("SampledData.interval")) {
        DebugUtilities.breakpoint();
      }
      scanChainElements(te);
    }

    Collections.sort(origins, new SourcedElementDefinitionSorter());

    checkStructureMaps();

    for (ConceptMap cm : conceptMaps.values()) {
      if (cm.hasUserData("cm.used") && "false".equals(cm.getUserString("cm.used"))) {
        if (!cm.getId().contains("4to5") && !cm.getId().contains("5to4")) {
          qaMsg("Unused conceptmap: "+cm.getId(), false);
        }        
      }
    }
    return !failures;
  }


  private void checkIds(String folder) throws IOException {
    initCtxt();
    List<LoadedFile> files = new ArrayList<>();
    loadAllFiles(files, new File(Utilities.path(folder, "input")));
    Set<String> ids = new HashSet<>();
    int ic = 0;
    int t = files.size()/50;
    for (LoadedFile file : files) {
      ic++;
      if (ic == t) {
        System.out.print(".");
        ic = 0;
      }
      if (file.cr != null) {
        String id = file.cr.getId();
        String nid = generateConciseId(id); 
        if (nid.length() > 60) {
          System.out.println("id too long: "+nid+" (from "+id+")");
        }
        String b = nid;
        int i = 0;
        while (ids.contains(nid)) {
          i++;
          nid = b+"-"+i;
          System.out.println("id try  "+nid+" (from "+id+" in "+file.filename+")");
        }
        file.id = id;
        file.nid = nid;
        ids.add(nid);            
      }
    }
    int tc = 0;
    System.out.println("!");
    ic = 0;
    for (LoadedFile file : files) {
      ic++;
      if (ic == t) {
        System.out.print(".");
        ic = 0;
      }
      if (file.id != null && !file.id.equals(file.nid)) {
        tc++;
        file.changed = true;
        for (LoadedFile src : files) {
          if (src.source.contains(file.id)) {
            src.source= src.source.replace(file.id, file.nid);
            src.changed = true;
          }
        }
      }
    }
    System.out.println("!");
    ic = 0;
    for (LoadedFile file : files) {
      ic++;
      if (ic == t) {
        System.out.print(".");
        ic = 0;
      }
      if (file.changed) {
        if (file.id != null && !file.id.equals(file.nid)) {
          new File(file.filename).delete();
          file.filename = file.filename.replace(file.id, file.nid);
          TextFile.stringToFile(file.source, file.filename);
        } else {
          TextFile.stringToFile(file.source, file.filename);
        }
      }
    }
    System.out.println("!");
    System.out.println("Found "+ids.size()+" IDs, changed ");
    throw new Error("here");
  }

  private String generateConciseId(String id) {
    String nid = id;
    if (id.contains(".")) {
      String[] parts = id.split("\\-"); 
      for (int i = 0; i < parts.length; i++) {
        String s = parts[i];
        if (Utilities.charCount(s, '.') > 1) {
          String[] sp = s.split("\\.");
          CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder(".");
          b.append(getTLA(sp[0]));
          for (int j = 1; j < sp.length-1; j++) {
            b.append(sp[j].substring(0, 2));
          }
          b.append(sp[sp.length-1]);
          parts[i] = b.toString();
        } else if (Utilities.charCount(s, '.') == 1) {
          parts[i] = getTLA(s.substring(0, s.indexOf(".")))+"."+s.substring(s.lastIndexOf(".")+1);
        }
      }
      nid = CommaSeparatedStringBuilder.join("-", parts);
    }
    return nid;
  }

  private void loadAllFiles(List<LoadedFile> files, File dir) throws FileNotFoundException, IOException {
    for (File f : dir.listFiles()) {
      if (!f.getName().startsWith(".")) {
        if (f.isDirectory()) {
          loadAllFiles(files,  f);
        } else {
          String source = TextFile.fileToString(f); 
          CanonicalResource cr = null;
          try {
            if (f.getName().endsWith(".fml")) {
              cr = new StructureMapUtilities(ctxt).parse(source, f.getName());
            } else {
              cr = (CanonicalResource) new JsonParser().parse(source);
            }
          } catch (Exception e) {
//            System.out.println("not a resource: "+f.getAbsolutePath()+": "+e.getMessage());
          }
          files.add(new LoadedFile(f, source, cr));
        }
      }
    }    
  }

  
  private String getTLA(String name) {
    switch (name) {
    case "Account" : return "act";
    case "ActivityDefinition" : return "adf";
    case "Address" : return "add";
    case "Age" : return "age";
    case "AdverseEvent" : return "aev";
    case "AllergyIntolerance" : return "ait";
    case "Annotation" : return "ann";
    case "Appointment" : return "app";
    case "AppointmentResponse" : return "apr";
    case "ArtifactAssessment" : return "ara";
    case "Attachment" : return "att";
    case "Availability" : return "av";
    case "Base" : return "base";
    case "Basic" : return "bas";
    case "BackboneElement" : return "bbe";
    case "BackboneType" : return "bbt";
    case "PrimitiveType" : return "pt";
    case "DataType" : return "dt";
    case "Dosage" : return "dos";
    case "Bundle" : return "bdl";
    case "Binary" : return "bin";
    case "BiologicallyDerivedProduct" : return "bdp";
    case "BiologicallyDerivedProductDispense" : return "bdpd";
    case "CanonicalResource" : return "cnl";
    case "MetadataResource" : return "mdr";
    case "ChargeItem" : return "cit";
    case "ChargeItemDefinition" : return "cid";
    case "Citation" : return "ctn";
    case "CodeableConcept" : return "ccc";
    case "CodeableReference" : return "ccr";
    case "ClinicalImpression" : return "cli";
    case "ClaimResponse" : return "clr";
    case "ConceptMap" : return "cmd";
    case "Composition" : return "cmp";
    case "CommunicationRequest" : return "cmr";
    case "CapabilityStatement" : return "cpb";
    case "Conformance" : return "cpb";
    case "Consent" : return "ppc";
    case "DetectedIssue" : return "dti";
    case "Coding" : return "cod";
    case "Communication" : return "com";
    case "Condition" : return "con";
    case "Count" : return "cnt";
    case "Coverage" : return "cov";
    case "CoverageEligibilityRequest" : return "cer";
    case "CoverageEligibilityResponse" : return "ces";
    case "CarePlan" : return "cpl";
    case "CareTeam" : return "ctm";
    case "ContactPoint" : return "cpt";
    case "ConditionDefinition" : return "cdf";
    case "DataElement" : return "dae";
    case "DataRequirement" : return "drq";
    case "DocumentManifest" : return "dcm";
    case "DocumentReference" : return "dcr";
    case "Device" : return "dev";
    case "DeviceDefinition" : return "dvd";
    case "DiagnosticReport" : return "dir";
    case "DeviceRequest" : return "dur";
    case "DeviceUsage" : return "dus";
    case "DeviceComponent" : return "dvc";
    case "DeviceMetric" : return "dvm";
    case "DeviceAssociation" : return "da";
    case "Distance" : return "dis";
    case "DomainResource" : return "dom";
    case "Duration" : return "drt";
    case "ElementDefinition" : return "eld";
    case "Element" : return "ele";
    case "EligibilityRequest" : return "elq";
    case "EligibilityResponse" : return "elr";
    case "Encounter" : return "enc";
    case "EncounterHistory" : return "enh";
    case "Endpoint" : return "enp";
    case "EnrollmentRequest" : return "enq";
    case "EnrollmentResponse" : return "enr";
    case "ExplanationOfBenefit" : return "eob";
    case "EpisodeOfCare" : return "eoc";
    case "EventDefinition" : return "evd";
    case "Evidence" : return "evi";
    case "EvidenceReport" : return "evr";
    case "EvidenceVariable" : return "evv";
    case "Expression" : return "exp";
    case "ExtensionDefinition" : return "exd";
    case "Extension" : return "ext";
    case "FamilyMemberHistory" : return "fhs";
    case "Flag" : return "alt";
    case "Goal" : return "gol";
    case "GraphDefinition" : return "gdf";
    case "Group" : return "grp";
    case "GuidanceResponse" : return "grs";
    case "HealthcareService" : return "hcs";
    case "Identifier" : return "idr";
    case "Immunization" : return "imm";
    case "ImmunizationEvaluation" : return "tla";
    case "ImmunizationRecommendation" : return "imr";
    case "ImplementationGuide" : return "ig";
    case "ImagingSelection" : return "imk";
    case "ImagingStudy" : return "ims";
    case "InventoryReport" : return "ivr";
    case "Invoice" : return "inv";
    case "ItemInstance" : return "iin";
    case "Location" : return "loc";
    case "Library" : return "lib";
    case "Linkage" : return "lnk";
    case "List" : return "lst";
    case "Measure" : return "mea";
    case "MeasureReport" : return "mrp";
    case "MedicationAdministration" : return "mad";
    case "MedicationDispense" : return "mdd";
    case "Medication" : return "med";
    case "MedicationRequest" : return "mps";
    case "MedicationOrder" : return "mps";
    case "MessageDefinition" : return "msd";
    case "MessageHeader" : return "msh";
    case "MedicationStatement" : return "mst";
    case "MedicationKnowledge" : return "mkn";
    case "MedicinalProductDefinition" : return "mpd";
    case "PackagedProductDefinition" : return "ppd";
    case "ManufacturedItemDefinition" : return "mid";
    case "AdministrableProductDefinition" : return "apd";
    case "RegulatedAuthorization" : return "rau";
    case "Ingredient" : return "ing";
    case "ClinicalUseDefinition" : return "cud";
    case "Money" : return "mny";
    case "HumanName" : return "nam";
    case "NutritionOrder" : return "nor";
    case "NamingSystem" : return "nsd";
    case "Observation" : return "obs";
    case "OperationDefinition" : return "opd";
    case "OperationOutcome" : return "opo";
    case "Organization" : return "org";
    case "Patient" : return "pat";
    case "Period" : return "per";
    case "PlanDefinition" : return "pdf";
    case "ParameterDefinition" : return "prd";
    case "PaymentNotice" : return "pmn";
    case "PaymentReconciliation" : return "pmr";
    case "Practitioner" : return "prc";
    case "PractitionerRole" : return "prl";
    case "Procedure" : return "pro";
    case "InsurancePlan" : return "ipn";
    case "InsuranceProduct" : return "ipr";
    case "OrganizationAffiliation" : return "oga";
    case "ServiceRequest" : return "srq";
    case "Provenance" : return "prv";
    case "Person" : return "psn";
    case "QuestionnaireResponse" : return "qrs";
    case "MoneyQuantity" : return "mtqy";
    case "SimpleQuantity" : return "sqty";
    case "Quantity" : return "qty";
    case "Questionnaire" : return "que";
    case "RiskAssessment" : return "ras";
    case "Ratio" : return "rat";
    case "RatioRange" : return "ratrng";
    case "Reference" : return "ref";
    case "RelativeTime" : return "rlt";
    case "ResearchStudy" : return "rst";
    case "ResearchSubject" : return "rsb";
    case "Resource" : return "res";
    case "Range" : return "rng";
    case "RelatedPerson" : return "rpp";
    case "RequestOrchestration" : return "rqo";
    case "Schedule" : return "sch";
    case "MolecularSequence" : return "msq";
    case "SpecimenDefinition" : return "spdf";
    case "Subscription" : return "scr";
    case "SubscriptionStatus" : return "scrs";
    case "SubscriptionTopic" : return "scrt";
    case "SampledData" : return "sdd";
    case "AuditEvent" : return "sev";
    case "Slot" : return "slt";
    case "SearchParameter" : return "spd";
    case "StructureDefinition" : return "sdf";
    case "StructureMap" : return "smp";
    case "SupportingDocumentation" : return "sdc";
    case "Specimen" : return "spm";
    case "Substance" : return "sub";
    case "SubstanceDefinition" : return "ssp";
    case "SubstancePolymer" : return "spl";
    case "SubstanceReferenceInformation" : return "sri";
    case "SubstanceNucleicAcid" : return "sna";
    case "SubstanceProtein" : return "spr";
    case "SubstanceSourceMaterial" : return "ssm";
    case "SupplyDelivery" : return "sud";
    case "SupplyRequest" : return "sur";
    case "TestScript" : return "tst";
    case "Timing" : return "tim";
    case "TriggerDefinition" : return "trd";
    case "Narrative" : return "txt";
    case "VisionPrescription" : return "vps";
    case "ValueSet" : return "vsd";
    case "CodeSystem" : return "csd";
    case "TerminologyCapabilities" : return "tcp";
    case "CompartmentDefinition" : return "cpd";
    case "Task" : return "tsk";
    case "Transport" : return "trn";
    case "GenomicStudy" : return "gns";
    case "ExampleScenario" : return "exs";
    case "ObservationDefinition" : return "obdf";
    case "VerificationResult" : return "vrs";
    case "NutritionProduct" : return "ntp";
    case "Permission" : return "perm";
    case "DeviceDispense" : return "dvdp";
    case "ActorDefinition" : return "actr";
    case "Requirements" : return "req";
    case "TestPlan" : return "tpl";
    case "InventoryItem" : return "invi";
    case "ProcessRequest" : return "prq";
    case "ProcessResponse" : return "prp";
    case "Claim" : return "clm";
    case "RequestGroup": return "rgp";
    case "Media" : return "mda";
    case "Contract": return "ctt";
    case "ProcedureRequest" :  return "pcrq";
    case "UsageContext" : return "usc";
    case "CatalogEntry" : return "cte";
    case "TestReport" : return "tsr";
    case "ReferralRequest": return "rfr";
    case "ResearchElementDefinition" : return "red";
    case "DiagnosticOrder" : return "dgo";
    case "DeviceUseStatement" : return "dus";
    case "ResearchDefinition" : return "rdf";
    case "RelatedArtifact" : return "rla";
    case "Contributor" : return "ctb";
    case "Sequence" : return "seq";
    case "DeviceUseRequest" : return "duq";
    default:
      throw new Error("NO TLA for "+name);
    }
  }


  private void checkStructureMaps() throws FileNotFoundException, IOException {
    for (StructureMap map : structureMaps.values()) {
      checkStructureMap(map);
    }
  }

  private void checkStructureMap(StructureMap map) throws FileNotFoundException, IOException {
    Map<String, SourcedStructureDefinition> srcList = new HashMap();
    if (determineSources(map, srcList)) {
      String srcVer = VersionUtilities.getNameForVersion(getInputUrl(map, "source")).substring(1).toLowerCase();
      String dstVer = VersionUtilities.getNameForVersion(getInputUrl(map, "target")).substring(1).toLowerCase();
      List<StructureMapGroupComponent> grpList = new ArrayList<>();
      for (StructureMapGroupComponent grp : map.getGroup()) {
        if (isStart(grp)) {
          Map<String, ElementWithType> vars = processInputs(map, grp, srcList);
          processGroup(map, grpList, srcVer, dstVer, srcList, vars, grp);          
        }
      }

      for (StructureMapGroupComponent grp : map.getGroup()) {
        if (!grpList.contains(grp)) {
          // we didn't process it for some reason, but we'll still try to chcek the concept map references... 
          // qaMsg("unvisited group "+grp.getName()+" in "+map.getUrl(), false);
          for (StructureMapGroupRuleComponent r : grp.getRule()) {
            for (StructureMapGroupRuleTargetComponent tgt : r.getTarget()) {
              if (tgt.getTransform() == StructureMapTransform.TRANSLATE) {
                String url = tgt.getParameter().get(1).getValue().primitiveValue();
                ConceptMap cm = conceptMapsByUrl.get(url);
                if (cm == null) {
                  qaMsg("bed ref '"+url+"' in "+map.getUrl(), true);
                } else {
                  cm.setUserData("cm.used", "true");
                }
              }
            }
          }
        }
      }
    }
  }

  private String getInputUrl(StructureMap map, String mode) {
    for (StructureMapStructureComponent uses : map.getStructure()) {
      if (mode.equals(uses.getMode().toCode())) {
        return uses.getUrl(); 
      }
    }
    throw new Error("No uses for mode '"+mode+"' found in "+map.getUrl());
  }

  private  Map<String, ElementWithType> processInputs(StructureMap map, StructureMapGroupComponent grp, Map<String, SourcedStructureDefinition> srcList) {
    Map<String, ElementWithType> vars = new HashMap<>();
    for (StructureMapGroupInputComponent input : grp.getInput()) {
      SourcedStructureDefinition sd = srcList.get(input.getMode().toCode()+":"+input.getType());
      if (sd == null) {
        qaMsg("Unable to locate type '"+input.getMode().toCode()+":"+input.getType()+"' in map '"+map.getUrl()+"'", true);
      } else {
        vars.put(input.getMode().toCode()+":"+input.getName(), new ElementWithType(sd.getDefinitions(), sd.getStructureDefinition(), sd.getStructureDefinition().getSnapshot().getElementFirstRep()));
      }
    }
    return vars;
  }

  private boolean isStart(StructureMapGroupComponent grp) {
    return grp.getTypeMode() == StructureMapGroupTypeMode.TYPEANDTYPES && grp.getInput().size() == 2;
  }

  private void processGroup(StructureMap map, List<StructureMapGroupComponent> grpList, String srcVer, String dstVer, Map<String, SourcedStructureDefinition> srcList, Map<String, ElementWithType> vars,  StructureMapGroupComponent grp) throws FileNotFoundException, IOException {
    grpList.add(grp);
    for (StructureMapGroupRuleComponent r : grp.getRule()) {
      processRuleSource(map, grpList, srcVer, dstVer, vars, grp, r, srcList);
    }    
  }

  private void processRuleSource(StructureMap map, List<StructureMapGroupComponent> grpList, String srcVer, String dstVer, Map<String, ElementWithType> vars, StructureMapGroupComponent grp, StructureMapGroupRuleComponent r, Map<String, SourcedStructureDefinition> srcList) throws FileNotFoundException, IOException {
    if (!isProcessible(r)) {
      qaMsg("Cannot process rule "+r.getName()+" in group "+grp.getName()+" in map "+map.getUrl(), true);
    } else {
      BooleanHolder bh = new BooleanHolder(true);
      StructureMapGroupRuleSourceComponent srcR = r.getSourceFirstRep();
      ElementWithType src = vars.get("source:"+srcR.getContext());
      Map<String, ElementWithType> tvars = clone(vars);
      if (src == null) {
        qaMsg("Cannot find src var '"+srcR.getContext()+"' in rule "+r.getName()+" in group "+grp.getName()+" in map "+map.getUrl()+" (vars = "+dump(vars)+")", true);
        bh.fail();
      } else {
        ElementWithType source = findChild(src, srcR.getElement(), srcR.getType());
        if (source == null) {
          //          qaMsg("Cannot find src element '"+srcR.getContext()+"."+srcR.getElement()+"'"+(srcR.hasType() ? " : "+srcR.getType() : "")+" on "+src.toString()+" in rule "+r.getName()+" in group "+grp.getName()+" in map "+map.getUrl(), true);
          bh.fail();
        } else {
          if (srcR.hasVariable()) {
            tvars.put("source:"+srcR.getVariable(), source);
          }
          for (StructureMapGroupRuleTargetComponent t : r.getTarget()) {
            tvars = processRuleTarget(map, srcVer, dstVer, grp, r, source, tvars, t, srcList, bh);
          }
        }
      }
      if (bh.ok()) {
        if (r.hasRule()) {
          for (StructureMapGroupRuleComponent dr : r.getRule()) {
            Map<String, ElementWithType> rvars = clone(tvars);
            processRuleSource(map, grpList, srcVer, dstVer, rvars, grp, dr, srcList);
          }
        }
        if (r.hasDependent()) {
          for (StructureMapGroupRuleDependentComponent dep : r.getDependent()) {
            StructureMapGroupComponent dgrp = getGroup(map, dep.getName());
            if (dgrp == null) {
              // we assume that it's in some other map we don't care about; we won't validate it anyway
              bh.fail();
            } else {
              if (dep.getParameter().size() != dgrp.getInput().size()) {              
                qaMsg("Calling '"+dgrp.getName()+"' with "+dep.getParameter().size()+" parameters, but it has "+dgrp.getInput().size()+" inputs in rule "+r.getName()+" in group "+grp.getName()+" in map "+map.getUrl(), true);
              } else if (!grpList.contains(dgrp)) { // recursive rules
                Map<String, ElementWithType> gvars = new HashMap<>();
                boolean ok = true;
                for (int i = 0; i < dep.getParameter().size(); i++) {
                  StructureMapGroupInputComponent input = dgrp.getInput().get(i);
                  if (dep.getParameter().get(i).getValue() instanceof IdType) {
                    String varName = input.getMode().toCode()+":"+dep.getParameter().get(i).getValue().primitiveValue();
                    String varName2 = input.getMode() == StructureMapInputMode.SOURCE ? "target:"+dep.getParameter().get(i).getValue().primitiveValue() : null;
                    if (tvars.containsKey(varName)) {
                      gvars.put(input.getMode().toCode()+":"+input.getName(), tvars.get(varName));
                    } else if (varName2 != null && tvars.containsKey(varName2)) {
                      gvars.put(input.getMode().toCode()+":"+input.getName(), tvars.get(varName2));
                    } else {
                      qaMsg("Parameter '"+varName+"' "+(varName2 != null ? "(or "+varName2+") " : "")+"is unknown in rule "+r.getName()+" in group "+grp.getName()+" in map "+map.getUrl()+"; vars = "+dump(tvars), true);
                    }                  
                  } else {
                    throw new Error("Not done yet");
                  }
                }
                processGroup(map, grpList, srcVer, dstVer, srcList, gvars, dgrp);
              }
            }
          }
        }
      }
    }
  }

  private StructureMapGroupComponent getGroup(StructureMap map, String name) {
    for (StructureMapGroupComponent grp : map.getGroup()) {
      if (name.equals(grp.getName())) {
        return grp;
      }
    }
    return null;
  }

  private Map<String, ElementWithType> processRuleTarget(StructureMap map, String srcVer, String dstVer, StructureMapGroupComponent grp, StructureMapGroupRuleComponent r, ElementWithType source, Map<String, ElementWithType> tvars, StructureMapGroupRuleTargetComponent t, Map<String, SourcedStructureDefinition> srcList, BooleanHolder bh) throws FileNotFoundException, IOException {
    ElementWithType tgt = tvars.get("target:"+t.getContext());
    if (tgt == null) {
      if (t.getContext() == null && t.getTransform() == StructureMapTransform.EVALUATE) {
        // complicated queries... we're not going to check any further, but it's not an error
        bh.fail();
      } else if (t.getContext() == null && t.getTransform() == StructureMapTransform.CREATE) {
        VersionDefinitions vd = getInputDefinitions(srcList, "target");
        StructureDefinition sd = vd.getStructures().get(t.getParameter().get(0).getValue().primitiveValue());
        if (sd == null) {
          qaMsg("Cannot find type '"+t.getParameter().get(0).getValue().primitiveValue()+"' in create '"+t.toString()+"' in rule "+r.getName()+" in group "+grp.getName()+" in map "+map.getUrl()+" (vars = "+dump(tvars)+")", true);
          bh.fail();
        } else {
          ElementWithType var = new ElementWithType(vd, sd, sd.getSnapshot().getElementFirstRep());
          tvars = clone(tvars);
          tvars.put("target:"+t.getVariable(), var);
        }
      } else {
        qaMsg("Cannot find tgt var '"+t.getContext()+"' in rule "+r.getName()+" in group "+grp.getName()+" in map "+map.getUrl()+" (vars = "+dump(tvars)+")", true);
        bh.fail();
      }
    } else {
      ElementWithType target = findChild(tgt, t.getElement(), null);
      if (target == null) {
        // qaMsg("Cannot find tgt element '"+t.getContext()+"."+t.getElement()+"' on "+tgt.toString()+" in rule "+r.getName()+" in group "+grp.getName()+" in map "+map.getUrl(), true);
        bh.fail();
      } else {
        if (t.getTransform() == StructureMapTransform.CREATE) {
          if (t.hasParameter()) {
            String type = t.getParameter().get(0).getValue().primitiveValue();
            target.setType(type);
          } else {
            bh.fail();
          }
        } else if (t.getTransform() == StructureMapTransform.TRANSLATE) {
          String url = t.getParameter().get(1).getValue().primitiveValue();
          VSPair vsl = isEnum(source.toSED());
          VSPair vsr = isEnum(target.toSED());
          if (vsl == null) {
//            qaMsg("cm but not coded in "+map.getUrl(), true);                
          } else if (vsr == null) {
//            qaMsg("cm but not coded in "+map.getUrl(), true);                
          } else {
            Set<String> lset = CodeSystemUtilities.codes(vsl.getCs());
            Set<String> rset = CodeSystemUtilities.codes(vsr.getCs());
            Set<String> lvset = ValueSetUtilities.codes(vsl.getVs(), vsl.getCs());
            Set<String> rvset = ValueSetUtilities.codes(vsr.getVs(), vsr.getCs());

            if (!vsl.getCs().getUrl().contains("resource-types") && !vsr.getCs().getUrl().contains("resource-types")) {
              ConceptMap cm = conceptMapsByUrl.get(url);
              if (cm == null) {
                String srcName = source.getEd().getPath();
                String tgtName = target.getEd().getPath();
                String id = srcName.equals(tgtName) ? srcName+"-"+srcVer+"to"+dstVer : srcName+"-"+tgtName+"-"+srcVer+"to"+dstVer;
                String correct = "http://hl7.org/fhir/uv/xver/ConceptMap/"+id;
                if (!url.equals(correct)) {
                  qaMsg("bad ref '"+url+"' should be '"+correct+"' in "+map.getUrl(), true);
                } else {
                  qaMsg("Missing ConceptMap '"+url+"' in "+map.getId(), true);
//                  makeCM(id, source.toSED(), target.toSED(), vsl, vsr, lset, rset, lvset, rvset);              
                }
              } else {
                String cmSrcVer = VersionUtilities.getNameForVersion(cm.getSourceScope().primitiveValue()).substring(1).toLowerCase();
                String cmDstVer = VersionUtilities.getNameForVersion(cm.getTargetScope().primitiveValue()).substring(1).toLowerCase();
                if (!cmSrcVer.equals(srcVer)) {
                  qaMsg("bad ref '"+url+"' srcVer is "+cmSrcVer+" should be "+srcVer + " in "+map.getUrl(), true);
                } else if (!cmDstVer.equals(dstVer)) {
                  qaMsg("bad ref '"+url+"' dstVer is "+cmDstVer+" should be "+dstVer + " in "+map.getUrl(), true);
                } else {
                  checkCM(cm, source.toSED(), target.toSED(), vsl, vsr, lset, rset, lvset, rvset);
                  cm.setUserData("cm.used", "true");
                }
              }
            }
          }
        }
        if (t.hasVariable()) {
          tvars = clone(tvars);
          tvars.put("target:"+t.getVariable(), target);
        }
      }
    }
    return tvars;
  }

  private VersionDefinitions getInputDefinitions(Map<String, SourcedStructureDefinition> srcList, String mode) {
    for (String n : srcList.keySet()) {
      if (n.startsWith(mode+":")) {
        return srcList.get(n).getDefinitions();      
      }
    }
    return null;
  }

  private String dump(Map<String, ElementWithType> vars) {
    CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
    for (String n : Utilities.sorted(vars.keySet())) {
      ElementWithType var = vars.get(n); 
      b.append(n+" : "+var.toString());
    }
    return b.toString();
  }

  private Map<String, ElementWithType> clone(Map<String, ElementWithType> source) {
    Map<String, ElementWithType> res = new HashMap<>();
    res.putAll(source);
    return res;
  }

  private ElementWithType findChild(ElementWithType src, String element, String type) {
    if (element == null) {
      return src;
    }

    StructureDefinition sd = null;
    String path = null;

    String wt = src.getWorkingType();
    if (wt.startsWith("#")) {
      sd = src.getSd();
      if (sd.getFhirVersion().toCode().equals("1.0.2")) {
        path = getR2PathForReference(sd, wt.substring(1))+"."+element;     
      } else {
        path = wt.substring(1)+"."+element;
      }
    } else {
      sd = src.getDef().getStructures().get(wt);
      if (sd != null && !sd.getAbstract() && !Utilities.existsInList(sd.getType(), "Element", "BackboneElement")) {
        path = sd.getType()+"."+element;
      } else {
        sd = src.getSd();
        path = src.getEd().getPath()+"."+element;
      }
    }
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      if (ed.getPath().equals(path) && hasType(ed, type)) {
        return new ElementWithType(src.getDef(), sd, ed, type);
      }
      if (ed.getPath().equals(path+"[x]") && hasType(ed, type)) {
        return new ElementWithType(src.getDef(), sd, ed, type);
      }
    }
    return null;
  }

  private String getR2PathForReference(StructureDefinition sd, String id) {
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      if (id.equals(ed.getId())) {
        return ed.getPath();
      }
    }
    return null;
  }

  private boolean hasType(ElementDefinition ed, String type) {
    if (type == null) {
      return true;
    }
    for (TypeRefComponent tr : ed.getType()) {
      if (type.equals(tr.getWorkingCode())) {
        return true;
      }
    }
    return false;

  }

  private boolean isProcessible(StructureMapGroupRuleComponent r) {
    return r.getSource().size() == 1 && r.getSourceFirstRep().hasContext();
  }

  private boolean determineSources(StructureMap map, Map<String, SourcedStructureDefinition> srcList) {
    boolean ok = true;
    for (StructureMapStructureComponent uses : map.getStructure()) {
      String type = uses.getUrl();
      String tn = tail(type);
      String verMM = VersionUtilities.getMajMin(type);
      String ver = VersionUtilities.getNameForVersion(verMM).toLowerCase();
      VersionDefinitions vd = versions.get(ver);
      StructureDefinition sd = vd.getStructures().get(tn);
      if (sd == null) {
        qaMsg("Unknown type "+type+" in map "+map.getUrl(), true);
        ok = false;
      } else {
        String name = uses.getMode().toCode()+":"+(uses.hasAlias() ? uses.getAlias() : sd.getType());
        srcList.put(name, new SourcedStructureDefinition(vd, sd, null));
      }
    }
    return ok;
  }

  private boolean logStarted = false;
  private SimpleWorkerContext ctxt;
  public void logProgress(String msg) {
    System.out.println((logStarted ? "" : "xve: ")+msg);
    logStarted = false;
  }

  public void logProgressStart(String msg) {
    System.out.print("xve: "+msg);
    logStarted = true;
  }

  public void qaMsg(String msg, boolean fail) {
    System.out.println((msg.startsWith(" ") ? "    " : "xve "+(fail ? "error" : "note")+": ")+msg);
    if (fail)  {
      failures = true;
    }
  }

  private void findTerminalElements() {
    // At this point, the only listed terminal elements are any elements that never had any links at all 
    // check that
    for (SourcedElementDefinition te : terminatingElements) {
      List<ElementDefinitionLink> links = makeEDLinks(te, MakeLinkMode.OUTWARD);
      if (links.size() > 0) {
        throw new Error("Logic error - "+te.toString()+" has outbound links");
      }
    }
    for (ElementDefinitionLink link : allLinks) {
      SourcedElementDefinition tgt = link.getNext();
      List<ElementDefinitionLink> links = makeEDLinks(tgt.getEd(), MakeLinkMode.OUTWARD);
      if (links.size() == 0 && !terminatingElements.contains(tgt)) {
        terminatingElements.add(tgt);
      }      
    }
  }


  private void checkAllLinksInChains() {
    for (ElementDefinitionLink link : allLinks) {
      if (link.getChainIds().isEmpty()) {
        qaMsg("Link not in chain: "+link.toString(), true);
      }
    }
  }

  private void identifyChain(SourcedElementDefinition te) {
    String id = VersionUtilities.getNameForVersion(te.getSd().getFhirVersion().toCode())+"."+te.getEd().getPath();

    Queue<ElementDefinitionLink> processList = new ConcurrentLinkedQueue<ElementDefinitionLink>();
    List<ElementDefinitionLink> processed = makeEDLinks(te.getEd(), MakeLinkMode.CHAIN);
    for (ElementDefinitionLink link : makeEDLinks(te.getEd(), MakeLinkMode.INWARD)) {
      processList.add(link);

      link.setLeftWidth(findLeftWidth(link.getPrev()));
    }
    while (!processList.isEmpty()) {
      ElementDefinitionLink link = processList.remove();
      processed.add(link);
      link.getChainIds().add(id);
      for (ElementDefinitionLink olink : makeEDLinks(link.getPrev().getEd(), MakeLinkMode.INWARD)) {
        if (!processed.contains(olink)) {
          processList.add(olink);
        }
      }
    }
  }


  private int findLeftWidth(SourcedElementDefinition node) {
    List<ElementDefinitionLink> links = makeEDLinks(node, MakeLinkMode.INWARD);
    if (links.size() == 0) {
      return 1; // just this entry
    } else {
      // we group incoming links by source. 
      Map<StructureDefinition, Integer> counts = new HashMap<>();
      for (ElementDefinitionLink link : links) {
        Integer c = counts.get(link.getPrev().getSd());
        if (c == null) {
          c = 0;
        }
        //if (link.leftWidth == 0) {
        link.setLeftWidth(findLeftWidth(link.getPrev()));            
        //}
        c = c + link.getLeftWidth();
        counts.put(link.getPrev().getSd(), c);
      }
      int res = 1;
      for (Integer c : counts.values()) {
        res = Integer.max(res, c);
      }
      return res;      
    }
  }


  private void checkMaps() throws FileNotFoundException, IOException {
    checkMapsReciprocal(cm("resources-2to3"), cm("resources-3to2"), "resources", false);
    checkMapsReciprocal(cm("resources-3to4"), cm("resources-4to3"), "resources", false);
    checkMapsReciprocal(cm("resources-4to4b"), cm("resources-4bto4"), "resources", false);
    checkMapsReciprocal(cm("resources-4bto5"), cm("resources-5to4b"), "resources", false);
    checkMapsReciprocal(cm("types-2to3"), cm("types-3to2"), "types", false);
    checkMapsReciprocal(cm("types-3to4"), cm("types-4to3"), "types", false);
    checkMapsReciprocal(cm("types-4to4b"), cm("types-4bto4"), "types", false);
    checkMapsReciprocal(cm("types-4bto5"), cm("types-5to4b"), "types", false);
    checkMapsReciprocal(cm("elements-2to3"), cm("elements-3to2"), "elements", false);
    checkMapsReciprocal(cm("elements-3to4"), cm("elements-4to3"), "elements", false);
    checkMapsReciprocal(cm("elements-4to4b"), cm("elements-4bto4"), "elements", false);
    checkMapsReciprocal(cm("elements-4bto5"), cm("elements-5to4b"), "elements", false);
  }


  private void checkMapsReciprocal(ConceptMap left, ConceptMap right, String folder, boolean save) throws FileNotFoundException, IOException {
    List<String> issues = new ArrayList<String>();
    if (ConceptMapUtilities.checkReciprocal(left, right, issues)) {
      if (save) {
        // wipes formatting in files
        // new org.hl7.fhir.r5.formats.JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream("/Users/grahamegrieve/work/fhir-cross-version/input/"+folder+"/ConceptMap-"+left.getId()+".json"), left);
        // new org.hl7.fhir.r5.formats.JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream("/Users/grahamegrieve/work/fhir-cross-version/input/"+folder+"/ConceptMap-"+right.getId()+".json"), right);
      } else {
        qaMsg("Unsaved corrections checking reciprocity of "+left.getId()+" and "+right.getId(), true);
      }
    }
    if (!issues.isEmpty()) {
      qaMsg("Found issues checking reciprocity of "+left.getId()+" and "+right.getId(), true);
      for (String s : issues) {
        qaMsg("  "+s, true);
      }
    }
  }


  private boolean isResourceTypeMap(ConceptMap cm) {
    if (cm.getGroup().size() != 1) {
      return false;
    }
    return cm.getGroupFirstRep().getSource().contains("resource-type") || cm.getGroupFirstRep().getTarget().contains("resource-type");
  }

  public ConceptMap cm(String name) {
    ConceptMap cm = conceptMaps.get(name);
    if (cm == null) {
      throw new Error("Concept Map "+name+" not found");
    }
    return cm;
  }

  private void loadConceptMaps(String dir) throws FHIRFormatError, FileNotFoundException, IOException {
    loadConceptMaps(new File(Utilities.path(dir, "input", "resources")), false);
    loadConceptMaps(new File(Utilities.path(dir, "input", "types")), false);
    loadConceptMaps(new File(Utilities.path(dir, "input", "elements")), false);
    loadConceptMaps(new File(Utilities.path(dir, "input", "codes")), true);
    loadConceptMaps(new File(Utilities.path(dir, "input", "search-params")), false);
  }

  private void loadConceptMaps(File file, boolean track) throws FHIRFormatError, FileNotFoundException, IOException {
    logProgressStart("Load ConceptMaps from "+file.getAbsolutePath()+": ");
    int i = 0;
    for (File f : file.listFiles()) {
      if (f.getName().startsWith("ConceptMap-") && !f.getName().contains(".old.json")) {
        ConceptMap cm = null;
        String id = null;
        try {
          cm = (ConceptMap) new org.hl7.fhir.r5.formats.JsonParser().parse(new FileInputStream(f));
          id = f.getName().replace("ConceptMap-", "").replace(".json", "");
          if (!cm.getId().equals(id)) {
            throw new Error("Error parsing "+f.getAbsolutePath()+": id mismatch - is "+cm.getId()+", should be "+id);
          }
          String url = "http://hl7.org/fhir/uv/xver/ConceptMap/"+id;
          if (!cm.getUrl().equals(url)) {
            throw new Error("Error parsing "+f.getAbsolutePath()+": url mismatch - is "+cm.getUrl()+", should be "+url);
          }
        } catch (Exception e) {
          throw new Error("Error parsing "+f.getAbsolutePath()+": "+e.getMessage(), e);
        }
        if (track) {
          cm.setUserData("cm.used", "false");
        }
        cm.setWebPath("ConceptMap-"+cm.getId()+".html");
        conceptMaps.put(id.toLowerCase(), cm);
        conceptMapsByUrl.put(cm.getUrl(), cm);
        if (!cm.hasSourceScope() || !cm.hasTargetScope()) {
          throw new Error("ConceptMap "+cm.getId()+" is missing scopes");
        }
        if ("http://hl7.org/fhir/1.0/StructureDefinition/AuditEvent#AuditEvent.participant.media".equals(cm.getSourceScope().primitiveValue())) {
          System.out.println(cm.getSourceScope().primitiveValue()+":"+cm.getTargetScope().primitiveValue());
        }
        conceptMapsByScope.put(cm.getSourceScope().primitiveValue()+":"+cm.getTargetScope().primitiveValue(), cm);
        i++;
      }
    }
    logProgress(" "+i+" loaded");
  }

  private void loadStructureMaps(String dir) throws FHIRFormatError, FileNotFoundException, IOException {
    initCtxt();
    loadStructureMaps(ctxt, new File(Utilities.path(dir, "input", "R2toR3")));
    loadStructureMaps(ctxt, new File(Utilities.path(dir, "input", "R3toR2")));
    loadStructureMaps(ctxt, new File(Utilities.path(dir, "input", "R3toR4")));
    loadStructureMaps(ctxt, new File(Utilities.path(dir, "input", "R4BtoR5")));
    loadStructureMaps(ctxt, new File(Utilities.path(dir, "input", "R4toR3")));
    loadStructureMaps(ctxt, new File(Utilities.path(dir, "input", "R4toR5")));
    loadStructureMaps(ctxt, new File(Utilities.path(dir, "input", "R5toR4")));
    loadStructureMaps(ctxt, new File(Utilities.path(dir, "input", "R5toR4B")));
  }

  private void initCtxt() throws IOException {
    if (ctxt == null) {
      FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager.Builder().build();
      NpmPackage npm = pcm.loadPackage("hl7.fhir.r5.core");    
      ctxt = new SimpleWorkerContext.SimpleWorkerContextBuilder().withAllowLoadingDuplicates(true).fromPackage(npm);
    }
  }

  private void loadStructureMaps(SimpleWorkerContext ctxt, File file) throws FHIRFormatError, FileNotFoundException, IOException {
    logProgressStart("Load StructureMaps from "+file.getAbsolutePath()+": ");
    int i = 0;
    for (File f : file.listFiles()) {
      if (f.getName().endsWith(".fml")) {
        StructureMap map = null;
        try {
          map = new StructureMapUtilities(ctxt).parse(TextFile.fileToString(f), f.getName());
        } catch (Exception e) {
          qaMsg("Error parsing "+f.getAbsolutePath()+": "+e.getMessage(), true);
        }
        if (map != null) {
          map.setWebPath("StructreMap-"+map.getId()+".html");
          String url = "http://hl7.org/fhir/uv/xver/StructureMap/"+map.getId();
          if (!map.getUrl().equals(url)) {
            qaMsg("Error parsing "+f.getAbsolutePath()+": url mismatch - is "+map.getUrl()+", should be "+url, true);
          }
          if (structureMaps.containsKey(map.getId())) {
            qaMsg("Error parsing "+f.getAbsolutePath()+": Duplicate id "+map.getId(), true);          
          }
          structureMaps.put(map.getId(), map);
          i++;
        }
      }
    }
    logProgress(" "+i+" loaded");
  }

  public boolean hasValid(List<ElementDefinitionLink> links) {
    for (ElementDefinitionLink link : links) {
      if (link.getNext().isValid()) {
        return true;
      }
    }
    return false;
  }


  private void scanChainElements(SourcedElementDefinition terminus) throws FileNotFoundException, IOException {
    List<ElementDefinitionLink> chain = makeEDLinks(terminus, MakeLinkMode.CHAIN);

    // this chain can include multiple logical subchains that all terminate at the same point. 
    // we're going to make a list of all origins, and then build a chain for each origin that only contains links in this chain (because links can be in more than one chain) 
    List<SourcedElementDefinition> origins = new ArrayList<>();
    if (chain.isEmpty()) {
      origins.add(terminus);
    } else {
      for (ElementDefinitionLink link : chain) {
        List<ElementDefinitionLink> links = makeEDLinks(link.getPrev(), MakeLinkMode.INWARD);
        if (links.size() == 0) {
          origins.add(link.getPrev());       
        }
      }
    }
    for (SourcedElementDefinition origin : origins) {
      if (this.origins.contains(origin)) {
        qaMsg("Ignoring duplicate report origin "+origin.toString(), false); 
      } else {
        this.origins.add(origin);
        List<ElementDefinitionLink> originChain = makeEDLinks(origin, MakeLinkMode.ORIGIN_CHAIN);
        buildSubChain(originChain, origin, chain);
        scanChainElements(origin, originChain);
      }
    }
  }

  private void buildSubChain(List<ElementDefinitionLink> subChain, SourcedElementDefinition node, List<ElementDefinitionLink> chain) {
    List<ElementDefinitionLink> links = makeEDLinks(node, MakeLinkMode.OUTWARD);
    for (ElementDefinitionLink link : links) {
      if (chain.contains(link)) {
        subChain.add(link);
        buildSubChain(subChain, link.getNext(), chain);
      }
    }
  }

  private void scanChainElements(SourcedElementDefinition origin, List<ElementDefinitionLink> links) throws FileNotFoundException, IOException {
    // now we have a nice single chain across a set of versions
    List<SourcedElementDefinition> all = new ArrayList<SourcedElementDefinition>();

    assert Utilities.noString(origin.getStatusReason());
    
    origin.setValid(true);
    origin.setStartVer(origin.getVer());
    origin.setStopVer(origin.getVer());
    all.add(origin);

    SourcedElementDefinition template = origin;    
    for (ElementDefinitionLink link : links) {
      SourcedElementDefinition element = link.getNext();
      all.add(element);
      if (link.getRel() != ConceptMapRelationship.EQUIVALENT) {
        element.addStatusReason("Not Equivalent");
        element.setValid(true);
        template = element;        
        template.setStartVer(element.getVer());
        template.setStopVer(element.getVer()); 
      } else if (!template.getEd().repeats() && element.getEd().repeats()) {
        element.addStatusReason("Element repeats");
        element.setValid(true);
        template.setRepeater(element);
        template = element;        
        template.setStartVer(element.getVer());
        template.setStopVer(element.getVer()); 
      } else {
        List<String> newTypes = findNewTypes(template.getEd(), element.getEd());
        if (!newTypes.isEmpty()) {
          element.addStatusReason("New Types "+CommaSeparatedStringBuilder.join("|", newTypes));
          element.setValid(true);
          template = element;        
          template.setStartVer(element.getVer());
          template.setStopVer(element.getVer()); 
        } else {
          List<String> newTargets = findNewTargets(template.getEd(), element.getEd());
          if (!newTargets.isEmpty()) {
            element.addStatusReason("New Targets "+CommaSeparatedStringBuilder.join("|", newTargets));
            element.setValid(true);
            template = element;        
            template.setStartVer(element.getVer());
            template.setStopVer(element.getVer()); 
          } else {
            element.clearStatusReason();
            element.setValid(false);
            template.setStopVer(element.getVer()); 
          }
        }
      }
    }

    for (SourcedElementDefinition element : all) {
      if (element.isValid()) {
        String bv = element.getStartVer();
        String ev = element.getRepeater() != null ? element.getRepeater().getStopVer() : element.getStopVer();
        CommaSeparatedStringBuilder vers = makeVerList(bv, ev);
        if (vers.count() == 0) {
          element.setValid(false);
          element.addStatusReason("??");
        } else {
          element.setVerList(vers.toString());
        }
      }
    }

    for (ElementDefinitionLink link : links) {
      VSPair l = isEnum(link.getPrev());
      VSPair r = isEnum(link.getNext());
      if (l != null && r != null) {
        if (l.getCs().getUrl().contains("resource-types") || r.getCs().getUrl().contains("resource-types")) {
          String idF = "resources-"+l.getVersion()+"to"+r.getVersion();
          String idR = "resources-"+r.getVersion()+"to"+l.getVersion();
          link.setNextCM(conceptMaps.get(idF));
          link.setPrevCM(conceptMaps.get(idR));
        } else {
          String leftScope = "http://hl7.org/fhir/"+VersionUtilities.getMajMin(link.getPrev().getVer())+"/StructureDefinition/" + link.getPrev().getSd().getType() + "#" + link.getPrev().getEd().getPath();
          String rightScope = "http://hl7.org/fhir/"+VersionUtilities.getMajMin(link.getNext().getVer())+"/StructureDefinition/" + link.getNext().getSd().getType() + "#" + link.getNext().getEd().getPath();
          
          String idF = link.getNext().getEd().getPath().equals(link.getPrev().getEd().getPath()) ? link.getNext().getEd().getPath()+"-"+l.getVersion()+"to"+r.getVersion() : link.getPrev().getEd().getPath()+"-"+link.getNext().getEd().getPath()+"-"+l.getVersion()+"to"+r.getVersion();
          String idR = link.getNext().getEd().getPath().equals(link.getPrev().getEd().getPath()) ? link.getNext().getEd().getPath()+"-"+r.getVersion()+"to"+l.getVersion() : link.getNext().getEd().getPath()+"-"+link.getPrev().getEd().getPath()+"-"+r.getVersion()+"to"+l.getVersion();
          ConceptMap cmF = conceptMapsByScope.get(leftScope + ":"+rightScope);
          ConceptMap cmR = conceptMapsByScope.get(rightScope + ":"+leftScope);
          Set<String> lset = CodeSystemUtilities.codes(l.getCs());
          Set<String> rset = CodeSystemUtilities.codes(r.getCs());
          Set<String> lvset = ValueSetUtilities.codes(l.getVs(), l.getCs());
          Set<String> rvset = ValueSetUtilities.codes(r.getVs(), r.getCs());

          if (cmF != null) {
            checkCM(cmF, link.getPrev(), link.getNext(), l, r, lset, rset, lvset, rvset);
          } else { // if (!rset.containsAll(lset)) {
            System.out.println("didn't find "+leftScope + ":"+rightScope);
            String nid = generateConciseId(idF);
            cmF = makeCM(nid, idF, link.getPrev(), link.getNext(), l, r, lset, rset, lvset, rvset, leftScope, rightScope);              
          }

          if (cmR != null) {
            checkCM(cmR, link.getNext(), link.getPrev(), r, l, rset, lset, rvset, lvset);
          } else { // if (!lset.containsAll(rset)) {
            System.out.println("didn't find "+rightScope + ":"+leftScope);
            String nid = generateConciseId(idR);
            cmR = makeCM(nid, idR, link.getNext(), link.getPrev(), r, l, rset, lset, rvset, lvset, rightScope, leftScope);            
          }
          if (cmF != null && cmR != null) {
            List<String> errs = new ArrayList<String>();
            boolean altered = ConceptMapUtilities.checkReciprocal(cmF, cmR, errs);
            for (String s : errs) {
              qaMsg("Error between "+cmF.getId()+" and "+cmR.getId()+" maps: "+s, true);
            }
            if (altered) {
              new org.hl7.fhir.r5.formats.JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream("/Users/grahamegrieve/work/fhir-cross-version/input/codes/ConceptMap-"+cmR.getId()+".json"), cmR);
              new org.hl7.fhir.r5.formats.JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream("/Users/grahamegrieve/work/fhir-cross-version/input/codes/ConceptMap-"+cmF.getId()+".json"), cmF);
            }
          }
          link.setNextCM(cmF);
          link.setPrevCM(cmR);
          
          if (!rvset.isEmpty() && cmR != null && !link.getNext().isValid()) {
            link.setNewCodes(ConceptMapUtilities.listCodesWithNoMappings(rvset, cmR));
            if (!link.getNewCodes().isEmpty()) {
              link.getNext().setValid(true);
              link.getNext().setStartVer(template.getVer());
              link.getNext().setStopVer(link.getPrev().getVer());
              CommaSeparatedStringBuilder vers = makeVerListPositive(template.getVer(), link.getPrev().getVer());
              link.getNext().setVerList(vers.toString());
              link.getNext().addStatusReason("Added "+Utilities.pluralize("code", link.getNewCodes().size())+" '"+CommaSeparatedStringBuilder.join(", ", link.getNewCodes())+"'");              
            }
          }
          if (!lvset.isEmpty() && cmF != null && !link.getPrev().isValid()) {
            link.setOldCodes(ConceptMapUtilities.listCodesWithNoMappings(lvset, cmF));
            if (!link.getOldCodes().isEmpty()) {
              link.getPrev().setValid(true);
              // no we need to know... which version was it defined in? and how far does this chain go?
              link.getPrev().setStartVer(link.getNext().getVer());
              link.getPrev().setStopVer("5.0.0");
              CommaSeparatedStringBuilder vers = makeVerListPositive(link.getNext().getVer(), "5.0.0");
              link.getPrev().setVerList(vers.toString());
              link.getPrev().addStatusReason("Removed "+Utilities.pluralize("code", link.getOldCodes().size())+" '"+CommaSeparatedStringBuilder.join(", ", link.getOldCodes())+"'");              
            }
          }
        }
      }        
    }
  }

  private CommaSeparatedStringBuilder makeVerList(String bv, String ev) {
    CommaSeparatedStringBuilder vers = new CommaSeparatedStringBuilder();

    if (!VersionUtilities.includedInRange(bv, ev, "1.0.2")) {
      vers.append("R2");
    }
    if (!VersionUtilities.includedInRange(bv, ev, "3.0.2")) {
      vers.append("R3");
    }
    if (!VersionUtilities.includedInRange(bv, ev, "4.0.1")) {
      vers.append("R4");
    }
    if (!VersionUtilities.includedInRange(bv, ev, "4.3.0")) {
      vers.append("R4B");
    }
    if (!VersionUtilities.includedInRange(bv, ev, "5.0.0")) {
      vers.append("R5");
    }
    return vers;
  }
  


  private CommaSeparatedStringBuilder makeVerListPositive(String bv, String ev) {
    CommaSeparatedStringBuilder vers = new CommaSeparatedStringBuilder();

    if (VersionUtilities.includedInRange(bv, ev, "1.0.2")) {
      vers.append("R2");
    }
    if (VersionUtilities.includedInRange(bv, ev, "3.0.2")) {
      vers.append("R3");
    }
    if (VersionUtilities.includedInRange(bv, ev, "4.0.1")) {
      vers.append("R4");
    }
    if (VersionUtilities.includedInRange(bv, ev, "4.3.0")) {
      vers.append("R4B");
    }
    if (VersionUtilities.includedInRange(bv, ev, "5.0.0")) {
      vers.append("R5");
    }
    return vers;
  }

  

  private ConceptMap makeCM(String id, String rawId, SourcedElementDefinition se, SourcedElementDefinition de, VSPair s, VSPair d, 
         Set<String> source, Set<String> dest, Set<String> lvset, Set<String> rvset, String scopeUri, String targetUri) throws FileNotFoundException, IOException {
    ConceptMap cm = new ConceptMap();
    cm.setId(id);
    cm.setUrl("http://hl7.org/fhir/uv/xver/ConceptMap/"+id);
    cm.setName(rawId.replace("-", ""));
    if (se.getEd().getPath().equals(de.getEd().getPath())) {
      cm.setTitle("Mapping for "+se.getEd().getPath()+" from "+se.getVer()+" to "+de.getVer());
    } else {
      cm.setTitle("Mapping for "+se.getEd().getPath()+"/"+de.getEd().getPath()+" from "+se.getVer()+" to "+de.getVer());
    }
    cm.setSourceScope(new UriType(scopeUri));
    cm.setTargetScope(new UriType(targetUri));
    ConceptMapGroupComponent g = cm.addGroup();
    scopeUri = injectVersionToUri(s.getCs().getUrl(), VersionUtilities.getMajMin(se.getVer()));
    targetUri = injectVersionToUri(d.getCs().getUrl(), VersionUtilities.getMajMin(de.getVer()));
    g.setSource(scopeUri);
    g.setTarget(targetUri);
    Set<String> unmapped = new HashSet<>();    
    for (String c : dest) {
      if (!source.contains(c)) {
        unmapped.add(c);
      }      
    }
    for (String c : source) {
      SourceElementComponent src = g.addElement();
      src.setCode(c);
      if (!dest.contains(c)) {
        src.setNoMap(true);
        src.setDisplay("CHECK! missed = "+CommaSeparatedStringBuilder.join(",", unmapped)+"; all = "+CommaSeparatedStringBuilder.join(",", dest));
        TargetElementComponent tgt = src.addTarget();
        tgt.setCode("CHECK!");
        tgt.setRelationship(ConceptMapRelationship.EQUIVALENT);     
      } else {
        TargetElementComponent tgt = src.addTarget();
        tgt.setCode(c);
        tgt.setRelationship(ConceptMapRelationship.EQUIVALENT);        
      }
    }
    new org.hl7.fhir.r5.formats.JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream("/Users/grahamegrieve/work/fhir-cross-version/input/codes/ConceptMap-"+cm.getId()+".json"), cm);
    return cm;
  }

  private void checkCM(ConceptMap cm, SourcedElementDefinition se, SourcedElementDefinition de, VSPair s, VSPair d, Set<String> source, Set<String> dest, Set<String> lvset, Set<String> rvset) throws FileNotFoundException, IOException {
    cm.setUserData("cm.used", "true");
    String scopeUri = "http://hl7.org/fhir/"+VersionUtilities.getMajMin(se.getVer())+"/StructureDefinition/"+se.getSd().getName()+"#"+se.getEd().getPath();
    String targetUri = "http://hl7.org/fhir/"+VersionUtilities.getMajMin(de.getVer())+"/StructureDefinition/"+de.getSd().getName()+"#"+de.getEd().getPath();
    if (!cm.hasSourceScopeUriType()) {
      qaMsg("Issue with "+cm.getId()+": Source Scope should be "+scopeUri, true);
    } else if (!scopeUri.equals(cm.getSourceScopeUriType().primitiveValue())) {
      qaMsg("Issue with "+cm.getId()+": Source Scope should be "+scopeUri+" not "+cm.getSourceScopeUriType().primitiveValue(), true);
    }
    if (!cm.hasTargetScopeUriType()) {
      qaMsg("Issue with "+cm.getId()+": Target Scope should be "+targetUri, true);
    } else if (!targetUri.equals(cm.getTargetScopeUriType().primitiveValue())) {
      qaMsg("Issue with "+cm.getId()+": Target Scope should be "+targetUri+" not "+cm.getTargetScopeUriType().primitiveValue(), true);
    }
    if (cm.getGroup().size() != 1) {
      qaMsg("Concept Map "+cm.getId()+" should have one and only one group", true);
    } else {
      ConceptMapGroupComponent g = cm.getGroupFirstRep();
      for (SourceElementComponent src : g.getElement()) {
        if (src.hasDisplay()) {
          qaMsg("Issue with "+cm.getId()+": "+src.getCode()+" has a display", true);
        }
        for (TargetElementComponent tgt : src.getTarget()) {
          if (tgt.hasDisplay()) {
            qaMsg("Issue with "+cm.getId()+": "+tgt.getCode()+" has a display", true);
          }
        }
        if (src.hasTarget() && src.getNoMap()) {
          qaMsg("Issue with "+cm.getId()+": "+src.getCode()+" has a both targets and noMap = true", true);
        }
      }

      scopeUri = injectVersionToUri(s.getCs().getUrl(), VersionUtilities.getMajMin(se.getVer()));
      targetUri = injectVersionToUri(d.getCs().getUrl(), VersionUtilities.getMajMin(de.getVer()));
      if (!scopeUri.equals(g.getSource())) {
        g.setSource(scopeUri);
        qaMsg("Issue with "+cm.getId()+": Group source should be "+scopeUri+" not "+g.getSource(), true);
      }
      if (!targetUri.equals(g.getTarget())) {
        qaMsg("Issue with "+cm.getId()+": Group target should be "+targetUri+" not "+g.getTarget(), true);
      }
      Set<String> missed = new HashSet<>();
      Set<String> invalid = new HashSet<>();
      Set<String> mapped = new HashSet<>(); 
      for (String c : source) {
        SourceElementComponent src = getSource(g, c);
        if (src != null) {
          for (TargetElementComponent tgt : src.getTarget()) {
            if (!dest.contains(tgt.getCode())) {
              invalid.add(tgt.getCode());
            } else {
              mapped.add(tgt.getCode());
            }
            if (tgt.hasComment() && tgt.getComment().contains("s:")) {
              qaMsg("Issue with "+cm.getId()+": comment contains 's:'", true);
            }
          }
        } else if (!dest.contains(c) || !hasUnMapped(g)) {
          missed.add(c);
        }        
      }
      if (!missed.isEmpty()) {
        Set<String> amissed = new HashSet<>();
        for (String c : missed) {
          if ((lvset.isEmpty() || lvset.contains(c)) && !c.startsWith("_") && !c.equals("null") && !CodeSystemUtilities.isNotSelectable(d.getCs(), c)) {
            g.addElement().setCode(c).setNoMap(true);
            qaMsg("Issue with "+cm.getId()+": no mappings for '"+c+"'", true);
            amissed.add(c);
          }
        }
        if (!amissed.isEmpty()) {
          qaMsg("Concept Map "+cm.getId()+" is missing mappings for "+CommaSeparatedStringBuilder.join(",", amissed), true);
        }
      }
      if (!invalid.isEmpty()) {
        qaMsg("Concept Map "+cm.getId()+" has invalid mappings to "+CommaSeparatedStringBuilder.join(",", invalid), true);
        Set<String> unmapped = new HashSet<>();
        for (String c : dest) {
          if (!mapped.contains(c)) {
            unmapped.add(c);
          }
        }
        for (String c : source) {
          SourceElementComponent src = getSource(g, c);
          if (src != null) {
            for (TargetElementComponent tgt : src.getTarget()) {
              if (!dest.contains(tgt.getCode())) {
                qaMsg("Issue with "+cm.getId()+": target "+tgt.getCode()+" is not valid (missed "+CommaSeparatedStringBuilder.join(",", unmapped)+")", true);
              }
            }
            if (src.getNoMap()) {
              qaMsg("Issue with "+cm.getId()+": missed: "+CommaSeparatedStringBuilder.join(",", unmapped), true);
            }
          }        
        }
      }
      invalid.clear();
      for (SourceElementComponent t : g.getElement()) {
        if (!source.contains(t.getCode())) {
          invalid.add(t.getCode());
          qaMsg("Issue with "+cm.getId()+": source "+t.getCode()+" is not valid" , true);
        }
      }
      if (!invalid.isEmpty()) {
        qaMsg("Concept Map "+cm.getId()+" has invalid mappings from "+CommaSeparatedStringBuilder.join(",", invalid), true);
      }
      if (!lvset.isEmpty()) {
//        qaMsg("Concept Map "+cm.getId()+" has mappings not in the value set: "+CommaSeparatedStringBuilder.join(",", lvset), true);
      }
      if (!rvset.isEmpty()) {
        for (SourceElementComponent src : g.getElement()) {
          for (TargetElementComponent tgt : src.getTarget()) {
            if (!"CHECK!".equals(tgt.getCode())) {
              if (!dest.contains(tgt.getCode())) {
                tgt.setComment("The code "+tgt.getCode()+" is not in the target code system - FIX");
                qaMsg("Issue with "+cm.getId()+": The code "+tgt.getCode()+" is not in the target code system", true);
              } else if (!rvset.contains(tgt.getCode())) {
                // qaMsg("Issue with "+cm.getId()+": The code "+tgt.getCode()+" not in the target value set, but is the correct translation", true);
              }
            }
          }
        }
      }
    }
  }


  private boolean hasUnMapped(ConceptMapGroupComponent g) {
    return g.hasUnmapped() && g.getUnmapped().getMode() == ConceptMapGroupUnmappedMode.USESOURCECODE;
  }

  private SourceElementComponent getSource(ConceptMapGroupComponent g, String c) {
    for (SourceElementComponent t : g.getElement()) {
      if (c.equals(t.getCode())) {
        return t;
      }
    }
    return null;
  }
  private String injectVersionToUri(String url, String ver) {
    return url.replace("http://hl7.org/fhir", "http://hl7.org/fhir/"+ver);
  }

  private VSPair isEnum(ElementWithType et) {
    VersionDefinitions vd = et.getDef();
    if (et.getEd().getBinding().getStrength() == BindingStrength.REQUIRED || et.getEd().getBinding().getStrength() == BindingStrength.EXTENSIBLE) {
      ValueSet vs = vd.getValueSets().get(et.getEd().getBinding().getValueSet());
      if (vs != null && vs.getCompose().getInclude().size() == 1) {
        CodeSystem cs = vd.getCodeSystems().get(vs.getCompose().getIncludeFirstRep().getSystem());
        if (cs != null && cs.getContent() == CodeSystemContentMode.COMPLETE) {
          return new VSPair(VersionUtilities.getNameForVersion(vd.getVersion().toCode()).substring(1), vs, cs);
        }
      }
    }
    return null;
    
  }
  
  private VSPair isEnum(SourcedElementDefinition pair) {
    String v = VersionUtilities.getNameForVersion(pair.getVer()).toLowerCase();
    VersionDefinitions vd = versions.get(v);
    if (pair.getEd().getBinding().getStrength() == BindingStrength.REQUIRED || pair.getEd().getBinding().getStrength() == BindingStrength.EXTENSIBLE) {
      ValueSet vs = vd.getValueSets().get(pair.getEd().getBinding().getValueSet());
      if (vs != null && vs.getCompose().getInclude().size() == 1) {
        CodeSystem cs = vd.getCodeSystems().get(vs.getCompose().getIncludeFirstRep().getSystem());
        if (cs != null && cs.getContent() == CodeSystemContentMode.COMPLETE) {
          return new VSPair(v.substring(1), vs, cs);
        }
      }
    }
    return null;
  }

  private List<String> findNewTypes(ElementDefinition template, ElementDefinition element) {
    Set<String> types = new HashSet<>();
    for (TypeRefComponent tr : template.getType()) {
      types.add(tr.getWorkingCode());
    }
    List<String> res = new ArrayList<>();
    for (TypeRefComponent tr : element.getType()) {
      if (!types.contains(tr.getWorkingCode())) {
        res.add(tr.getWorkingCode());
      }
    }
    return res;
  }


  private List<String> findNewTargets(ElementDefinition template, ElementDefinition element) {
    Set<String> targets = new HashSet<>();
    for (TypeRefComponent tr : template.getType()) {
      for (CanonicalType c : tr.getTargetProfile()) {
        targets.add(c.asStringValue());
      }
    }
    List<String> res = new ArrayList<>();
    for (TypeRefComponent tr : element.getType()) {
      for (CanonicalType c : tr.getTargetProfile()) {
        if (!targets.contains(c.asStringValue())) {
          res.add(tail(c.asStringValue()));
        }
      }
    }
    return res;
  }

  private void buildLinks(XVersions ver, VersionDefinitions defsPrev, ConceptMap resFwd, ConceptMap elementFwd, VersionDefinitions defsNext, boolean last) {
    logProgress("Build links between "+defsPrev.getVersion().toCode()+" and "+defsNext.getVersion().toCode());

    for (String name : Utilities.sorted(defsPrev.getStructures().keySet())) {
      StructureDefinition sd = defsPrev.getStructures().get(name);
      if (sd.getKind() == StructureDefinitionKind.COMPLEXTYPE && (!sd.getAbstract() || Utilities.existsInList(sd.getName(), "Quantity")) && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
        List<SourcedStructureDefinition> matches = new ArrayList<>();
        matches.add(new SourcedStructureDefinition(defsNext, defsNext.getStructures().get(name), ConceptMapRelationship.EQUIVALENT));        
        buildLinksForElements(ver, elementFwd, sd, matches);
      }
    }

    for (String name : Utilities.sorted(defsPrev.getStructures().keySet())) {
      StructureDefinition sd = defsPrev.getStructures().get(name);
      if (sd.getKind() == StructureDefinitionKind.RESOURCE && !sd.getAbstract() && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
        
        List<SourcedStructureDefinition> matches = new ArrayList<>();
        List<TranslatedCode> names = translateResourceName(resFwd, name);
        if (names.isEmpty()) {
          matches.add(new SourcedStructureDefinition(defsNext, null, null));
        } else {
          for (TranslatedCode n : names) {
            matches.add(new SourcedStructureDefinition(defsNext, defsNext.getStructures().get(n.getCode()), n.getRelationship()));
          }
        }
        buildLinksForElements(ver, elementFwd, sd, matches);
      }
    }
    
    if (last) {
      // now that we've done that, scan anything in defsNext that didn't get mapped to from destPrev

      for (String name : Utilities.sorted(defsNext.getStructures().keySet())) {
        StructureDefinition sd = defsNext.getStructures().get(name);
        if (sd.getKind() == StructureDefinitionKind.COMPLEXTYPE && (!sd.getAbstract() || Utilities.existsInList(sd.getName(), "Quantity")) && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
          for (ElementDefinition ed : sd.getDifferential().getElement()) {
            if (!ed.hasUserData("sed")) {
              List<ElementDefinitionLink> links = makeEDLinks(ed, MakeLinkMode.OUTWARD);
              terminatingElements.add(makeSED(sd, ed));
            }
          }     
        }
      }

      for (String name : Utilities.sorted(defsNext.getStructures().keySet())) {
        StructureDefinition sd = defsNext.getStructures().get(name);
        if (sd.getKind() == StructureDefinitionKind.RESOURCE && !sd.getAbstract() && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
          for (ElementDefinition ed : sd.getDifferential().getElement()) {   
            if (!ed.hasUserData("sed")) {
              List<ElementDefinitionLink> links = makeEDLinks(ed, MakeLinkMode.OUTWARD);
              terminatingElements.add(makeSED(sd, ed));
            }
          }     
        }
      }
    }
  }


  private void buildLinksForElements(XVersions ver, ConceptMap elementFwd, StructureDefinition sd, List<SourcedStructureDefinition> matches) {
    for (ElementDefinition ed : sd.getDifferential().getElement()) {        
      List<ElementDefinitionLink> links = makeEDLinks(ed, MakeLinkMode.OUTWARD);
      for (SourcedStructureDefinition ssd : matches) {
        if (ssd.getStructureDefinition() != null) {
          List<MatchedElementDefinition> edtl = findTranslatedElements(ed, ssd.getStructureDefinition(), elementFwd, ssd.getRelationship());
          if (edtl.isEmpty()) {
            // no links
          } else {
            for (MatchedElementDefinition edt : edtl) {
              ElementDefinitionLink link = new ElementDefinitionLink();
              link.setVersions(ver);
              link.setPrev(makeSED(sd, ed));
              link.setNext(makeSED(ssd.getStructureDefinition(), edt.getEd()));
              link.setRel(edt.getRel());
              allLinks.add(link);
              links.add(link);
              List<ElementDefinitionLink> linksOther = makeEDLinks(edt.getEd(), MakeLinkMode.INWARD);
              linksOther.add(link);
            }
          }
        }
      }
      if (links.size() == 0) {
        terminatingElements.add(makeSED(sd, ed));
      }
    }
  }

  public SourcedElementDefinition makeSED(StructureDefinition sd, ElementDefinition ed) {
    SourcedElementDefinition sed = (SourcedElementDefinition) ed.getUserData("sed");
    if (sed == null) {
      sed = new SourcedElementDefinition(sd, ed);
      ed.setUserData("sed", sed);
    }
    return sed;
  }


  public List<ElementDefinitionLink> makeEDLinks(SourcedElementDefinition sed, MakeLinkMode mode) {
    return makeEDLinks(sed.getEd(), mode);
  }

  public List<ElementDefinitionLink> makeEDLinks(ElementDefinition ed, MakeLinkMode mode) {
    String id = "links."+mode;
    List<ElementDefinitionLink> links = (List<ElementDefinitionLink>) ed.getUserData(id);
    if (links == null) {
      links = new ArrayList<>();
      ed.setUserData(id, links);
    }
    return links;
  }



  public StructureDefinitionColumn getColumn(List<StructureDefinitionColumn> columns, StructureDefinition sd) {
    for (StructureDefinitionColumn col : columns) {
      if (col.getSd() == sd) {
        return col;
      }
    }
    throw new Error("not found");
  }




  private List<ConceptMapUtilities.TranslatedCode> translateResourceName(ConceptMap map, String name) {
    List<ConceptMapUtilities.TranslatedCode> res = new ArrayList<>();
    for (ConceptMapGroupComponent g : map.getGroup()) {
      for (SourceElementComponent e : g.getElement()) {
        if (e.getCode().equals(name)) {
          for (TargetElementComponent t : e.getTarget()) {
            if (t.getRelationship() == ConceptMapRelationship.EQUIVALENT) {
              res.add(new ConceptMapUtilities.TranslatedCode(t.getCode(), t.getRelationship()));
            } else if (t.getRelationship() == ConceptMapRelationship.SOURCEISBROADERTHANTARGET) {
              res.add(new ConceptMapUtilities.TranslatedCode(t.getCode(), t.getRelationship()));
            } else if (t.getRelationship() == ConceptMapRelationship.SOURCEISNARROWERTHANTARGET) {
              res.add(new ConceptMapUtilities.TranslatedCode(t.getCode(), t.getRelationship()));
            }
          }
        }
      }
    }
    return res;
  }
  //
  private List<MatchedElementDefinition> findTranslatedElements(ElementDefinition eds, StructureDefinition structureDefinition, ConceptMap elementMap, ConceptMapRelationship resrel) {
    //  String ver = structureDefinition.getFhirVersion().toCode();
    List<MatchedElementDefinition> res = new ArrayList<MatchedElementDefinition>();
    String path = eds.getPath();
    String epath = path.contains(".") ? structureDefinition.getName()+path.substring(path.indexOf(".") ): structureDefinition.getName();
    List<TranslatedCode> names = translateElementName(path, elementMap, epath);
    for (TranslatedCode n : names) {
      ElementDefinition ed = structureDefinition.getDifferential().getElementByPath(n.getCode());
      if (ed != null) {
        res.add(new MatchedElementDefinition(ed, !ed.getPath().contains(".") ? resrel : n.getRelationship()));
      }
    }
    return res;
  }

  private List<TranslatedCode> translateElementName(String name, ConceptMap map, String def) {
    List<TranslatedCode> res = new ArrayList<>();
    for (ConceptMapGroupComponent g : map.getGroup()) {
      boolean found = false;
      for (SourceElementComponent e : g.getElement()) {
        if (e.getCode().equals(name) || e.getCode().equals(def)) {
          found = true;
          for (TargetElementComponent t : e.getTarget()) {
            if (t.getRelationship() == ConceptMapRelationship.EQUIVALENT || t.getRelationship() == ConceptMapRelationship.SOURCEISBROADERTHANTARGET || t.getRelationship() == ConceptMapRelationship.SOURCEISNARROWERTHANTARGET) {
              res.add(new TranslatedCode(t.getCode(), t.getRelationship()));
            }
          }
        }
      }
      if (!found) {
        res.add(new TranslatedCode(def, ConceptMapRelationship.EQUIVALENT));
      }
    }
    return res;
  }

  private String tail(String value) {
    return value.contains("/") ? value.substring(value.lastIndexOf("/")+1) : value;
  }


  private void loadVersions() throws IOException {
    FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager.Builder().build();
    vdr2 = loadR2(pcm);
    versions.put("r2", vdr2);
    vdr3 = loadR3(pcm);
    versions.put("r3", vdr3);
    vdr4 = loadR4(pcm);
    versions.put("r4", vdr4);
    vdr4b = loadR4B(pcm);
    versions.put("r4b", vdr4b);
    vdr5 = loadR5(pcm);
    versions.put("r5", vdr5);
  }


  private VersionDefinitions loadR2(FilesystemPackageCacheManager pcm) throws FHIRException, IOException {
    VersionDefinitions vd = new VersionDefinitions();
    vd.setVersion(FhirPublication.DSTU2);
    logProgressStart("Load "+vd.getVersion().toCode());
    NpmPackage npm = pcm.loadPackage("hl7.fhir.r2.core");
    for (String n : npm.listResources("StructureDefinition", "ValueSet", "CodeSystem")) {
      BaseAdvisor_10_50 advisor = new BaseAdvisor_10_50();
      CanonicalResource cr = (CanonicalResource) VersionConvertorFactory_10_50.convertResource(new org.hl7.fhir.dstu2.formats.JsonParser().parse(npm.load(n)), advisor);
      for (CodeSystem cs : advisor.getCslist()) {
        vd.add(cs);
      }
      vd.add(cr);
    }    
    logProgress(": "+vd.summary());
    return vd;
  }

  private VersionDefinitions loadR3(FilesystemPackageCacheManager pcm) throws FHIRException, IOException {
    VersionDefinitions vd = new VersionDefinitions();
    vd.setVersion(FhirPublication.STU3);
    logProgressStart("Load "+vd.getVersion().toCode());
    NpmPackage npm = pcm.loadPackage("hl7.fhir.r3.core");
    for (String n : npm.listResources("StructureDefinition", "ValueSet", "CodeSystem")) {
      CanonicalResource cr = (CanonicalResource) VersionConvertorFactory_30_50.convertResource(new org.hl7.fhir.dstu3.formats.JsonParser().parse(npm.load(n)));
      vd.add(cr);
    }    
    logProgress(": "+vd.summary());
    return vd;
  }

  private VersionDefinitions loadR4(FilesystemPackageCacheManager pcm) throws FHIRFormatError, FHIRException, IOException {
    VersionDefinitions vd = new VersionDefinitions();
    vd.setVersion(FhirPublication.R4);
    logProgressStart("Load "+vd.getVersion().toCode());
    NpmPackage npm = pcm.loadPackage("hl7.fhir.r4.core");
    for (String n : npm.listResources("StructureDefinition", "ValueSet", "CodeSystem")) {
      CanonicalResource cr = (CanonicalResource) VersionConvertorFactory_40_50.convertResource(new org.hl7.fhir.r4.formats.JsonParser().parse(npm.load(n)));
      vd.add(cr);
    }    
    logProgress(": "+vd.summary());
    return vd;
  }

  private VersionDefinitions loadR4B(FilesystemPackageCacheManager pcm) throws FHIRException, IOException {
    VersionDefinitions vd = new VersionDefinitions();
    vd.setVersion(FhirPublication.R4B);
    logProgressStart("Load "+vd.getVersion().toCode());
    NpmPackage npm = pcm.loadPackage("hl7.fhir.r4b.core");
    for (String n : npm.listResources("StructureDefinition", "ValueSet", "CodeSystem")) {
      CanonicalResource cr = (CanonicalResource) VersionConvertorFactory_43_50.convertResource(new org.hl7.fhir.r4b.formats.JsonParser().parse(npm.load(n)));
      vd.add(cr);
    }    
    logProgress(": "+vd.summary());
    return vd;
  }

  private VersionDefinitions loadR5(FilesystemPackageCacheManager pcm) throws FHIRException, IOException {
    VersionDefinitions vd = new VersionDefinitions();
    vd.setVersion(FhirPublication.R5);
    logProgressStart("Load "+vd.getVersion().toCode());
    NpmPackage npm = pcm.loadPackage("hl7.fhir.r5.core");
    for (String n : npm.listResources("StructureDefinition", "ValueSet", "CodeSystem")) {
      CanonicalResource cr = (CanonicalResource) new org.hl7.fhir.r5.formats.JsonParser().parse(npm.load(n));
      vd.add(cr);
    }    
    logProgress(": "+vd.summary());
    return vd;
  }

  @Override
  public String getLink(String system, String code) {
    if (system == null) {
      return null;
    }
    switch (system) {
    case "http://hl7.org/fhir/1.0/resource-types" : return determineResourceLink("1.0", code);
    case "http://hl7.org/fhir/3.0/resource-types" : return determineResourceLink("3.0", code);
    case "http://hl7.org/fhir/4.0/resource-types" : return determineResourceLink("4.0", code);
    case "http://hl7.org/fhir/4.3/resource-types" : return determineResourceLink("4.3", code);
    case "http://hl7.org/fhir/5.0/resource-types" : return determineResourceLink("5.0", code);
    case "http://hl7.org/fhir/1.0/data-types" : return determineDataTypeLink("1.0", code);
    case "http://hl7.org/fhir/3.0/data-types" : return determineDataTypeLink("3.0", code);
    case "http://hl7.org/fhir/4.0/data-types" : return determineDataTypeLink("4.0", code);
    case "http://hl7.org/fhir/4.3/data-types" : return determineDataTypeLink("4.3", code);
    case "http://hl7.org/fhir/5.0/data-types" : return determineDataTypeLink("5.0", code);
    default:
      return null;
    }
  }

  private String determineDataTypeLink(String string, String code) {
    if (Utilities.existsInList(code, "base64Binary", "boolean", "canonical", "code", "date", "dateTime", "decimal", "id", "instant", "integer", "integer64",
        "markdown", "oid", "positiveInt", "string", "time", "unsignedInt", "uri", "url", "uuid", "xhtml")) {
      return null;
    } else {
      return "cross-version-"+code+".html"; 
    }
  }

  private String determineResourceLink(String version, String code) {
    if ("5.0".equals(version)) {
      return "cross-version-"+code+".html";
    } else {
      if (vdr5.getStructures().containsKey(code)) {
        return "cross-version-"+code+".html";
      }
      List<String> codes = new ArrayList<>();
      codes.add(code);
      if ("1.0".equals(version)) {
        codes = translateResourceName(codes, cm("resources-2to3"));
        version = "3.0";        
      }
      if ("3.0".equals(version)) {
        codes = translateResourceName(codes, cm("resources-3to4"));
        version = "4.0";        
      }
      if ("4.0".equals(version)) {
        codes = translateResourceName(codes, cm("resources-4to4b"));
        version = "4.3";        
      }
      if ("4.3".equals(version)) {
        codes = translateResourceName(codes, cm("resources-4bto5"));
      }
      for (String c : codes) {
        if (vdr5.getStructures().containsKey(c)) {
          return "cross-version-"+c+".html";
        } 
      }
    }
    return null;
  }

  private List<String> translateResourceName(List<String> codes, ConceptMap cm) {
    List<String> res = new ArrayList<String>();
    for (ConceptMapGroupComponent grp : cm.getGroup()) {
      for (SourceElementComponent src : grp.getElement()) {
        if (codes.contains(src.getCode())) {
          for (TargetElementComponent tgt : src.getTarget()) {
            if (tgt.getRelationship() == ConceptMapRelationship.EQUIVALENT || tgt.getRelationship() == ConceptMapRelationship.RELATEDTO ||
                tgt.getRelationship() == ConceptMapRelationship.SOURCEISBROADERTHANTARGET || tgt.getRelationship() == ConceptMapRelationship.SOURCEISNARROWERTHANTARGET) {
              res.add(tgt.getCode());
            }
          }
        }
      }
    }
    return res;
  }


  @Override
  public List<Coding> getMembers(String uri) {
    String version = VersionUtilities.getNameForVersion(uri);
    VersionDefinitions vd = versions.get(version.toLowerCase());
    if (vd != null) {
      if (uri.contains("#")) {
        String ep = uri.substring(uri.indexOf("#")+1);
        String base = uri.substring(0, uri.indexOf("#"));
        String name = base.substring(44);
        StructureDefinition sd = vd.getStructures().get(name);
        if (sd != null) {
          ElementDefinition ed = sd.getDifferential().getElementByPath(ep);
          return processVS(vd, ed.getBinding().getValueSet());
        }
      } else if (uri.endsWith("/ValueSet/resource-types")) {
        return listResources(vd.getStructures(),  VersionUtilities.getMajMin(vd.getVersion().toCode()));
      } else if (uri.endsWith("/ValueSet/data-types")) {
        return listDatatypes(vd.getStructures(),  VersionUtilities.getMajMin(vd.getVersion().toCode()));
      } else {
        System.out.println(uri);
      }
    }
    return null;
  }

  private List<Coding> listResources(Map<String, StructureDefinition> structures, String ver) {
    List<Coding> list = new ArrayList<>();
    for (String n : Utilities.sorted(structures.keySet())) {
      StructureDefinition sd = structures.get(n);
      if (sd.getKind() == StructureDefinitionKind.RESOURCE && !sd.getAbstract() && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
        list.add(new Coding("http://hl7.org/fhir/"+ver+"/resource-types", sd.getType(), sd.getType()));
      }
    }
//        if (!types.contains(sd.getType()) && sd.getKind() == StructureDefinitionKind.COMPLEXTYPE && (!sd.getAbstract() || Utilities.existsInList(sd.getName(), "Quantity")) && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
    return list;
  }

  private List<Coding> listDatatypes(Map<String, StructureDefinition> structures, String ver) {
    List<Coding> list = new ArrayList<>();
    for (String n : Utilities.sorted(structures.keySet())) {
      StructureDefinition sd = structures.get(n);
      if ((sd.getKind() == StructureDefinitionKind.COMPLEXTYPE || sd.getKind() == StructureDefinitionKind.PRIMITIVETYPE) && !sd.getAbstract() && !Utilities.existsInList(sd.getType(), "BackboneElement") && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
        list.add(new Coding("http://hl7.org/fhir/"+ver+"/data-types", sd.getType(), sd.getType()));
      }
    }
//        if (!types.contains(sd.getType()) && sd.getKind() == StructureDefinitionKind.COMPLEXTYPE && (!sd.getAbstract() || Utilities.existsInList(sd.getName(), "Quantity")) && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
    return list;
  }

  private List<Coding> processVS(VersionDefinitions vd, String url) {
    ValueSet vs = vd.getValueSets().get(url);
    if (vs != null && vs.hasCompose() && !vs.getCompose().hasExclude()) {
      List<Coding> list = new ArrayList<>();
      for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
        if (inc.hasValueSet() || inc.hasFilter()) {
          return null;
        }
        String system = inc.getSystem().replace("http://hl7.org/fhir/", "http://hl7.org/fhir/"+VersionUtilities.getMajMin(vd.getVersion().toCode())+"/");
        String vn = VersionUtilities.getNameForVersion(vd.getVersion().toCode());
        if (inc.hasConcept()) {
          for (ConceptReferenceComponent cc : inc.getConcept()) {
            list.add(new Coding().setSystem(system).setCode(cc.getCode()).setDisplay(cc.getDisplay()+" ("+vn+")"));
          }
        } else {
          CodeSystem cs = vd.getCodeSystems().get(inc.getSystem());
          if (cs == null) {
            return null;
          } else {
            addCodings(system, vn, cs.getConcept(), list);
          }
        }
      }
      return list;
    } else {
      return null;
    }
  }

  private void addCodings(String system, String vn, List<ConceptDefinitionComponent> concepts, List<Coding> list) {
    for (ConceptDefinitionComponent cd : concepts) {
      list.add(new Coding().setSystem(system).setCode(cd.getCode()).setDisplay(cd.getDisplay()+" ("+vn+")"));
      addCodings(system, vn, cd.getConcept(), list);
    }

  }

  public Map<String, VersionDefinitions> getVersions() {
    return versions;
  }

  public Map<String, ConceptMap> getConceptMaps() {
    return conceptMaps;
  }

  public VersionDefinitions getVdr2() {
    return vdr2;
  }

  public VersionDefinitions getVdr3() {
    return vdr3;
  }

  public VersionDefinitions getVdr4() {
    return vdr4;
  }

  public VersionDefinitions getVdr4b() {
    return vdr4b;
  }

  public VersionDefinitions getVdr5() {
    return vdr5;
  }

  public List<ElementDefinitionLink> getAllLinks() {
    return allLinks;
  }

  public List<SourcedElementDefinition> getTerminatingElements() {
    return terminatingElements;
  }

  public List<SourcedElementDefinition> getOrigins() {
    return origins;
  }

  @Override
  public boolean describeMap(ConceptMap map, XhtmlNode x) {
    switch (map.getTargetScope().primitiveValue()) {
    case "http://hl7.org/fhir/1.0/ValueSet/data-types":
      x.b().ah("http://hl7.org/fhir/DSTU2/datatypes.html").tx("R2 DataTypes");
      break;
    case "http://hl7.org/fhir/1.0/ValueSet/resource-types":
      x.b().ah("http://hl7.org/fhir/DSTU2/resourcelist.html").tx("R2 Resources");
      break;
    case "http://hl7.org/fhir/3.0/ValueSet/data-types":
      x.b().ah("http://hl7.org/fhir/STU3/datatypes.html").tx("R3 DataTypes");
      break;
    case "http://hl7.org/fhir/3.0/ValueSet/resource-types":
      x.b().ah("http://hl7.org/fhir/STU3/resourcelist.html").tx("R3 Resources");
      break;
    case "http://hl7.org/fhir/4.0/ValueSet/data-types":
      x.b().ah("http://hl7.org/fhir/R4/datatypes.html").tx("R4 DataTypes");
      break;
    case "http://hl7.org/fhir/4.0/ValueSet/resource-types":
      x.b().ah("http://hl7.org/fhir/R4/resourcelist.html").tx("R4 Resources");
      break;
    case "http://hl7.org/fhir/4.3/ValueSet/data-types":
      x.b().ah("http://hl7.org/fhir/R4B/datatypes.html").tx("R4B DataTypes");
      break;
    case "http://hl7.org/fhir/4.3/ValueSet/resource-types":
      x.b().ah("http://hl7.org/fhir/R4B/resourcelist.html").tx("R4B Resources");
      break;
    case "http://hl7.org/fhir/5.0/ValueSet/data-types":
      x.b().ah("http://hl7.org/fhir/R5/datatypes.html").tx("R5 DataTypes");
      break;
    case "http://hl7.org/fhir/5.0/ValueSet/resource-types":
      x.b().ah("http://hl7.org/fhir/R5/resourcelist.html").tx("R5 Resources");
      break;
    default:
      return false;
    }
    x.tx(" (");
    x.ah(map.getWebPath()).tx("Map");
    x.tx(")");
    return true;
  }



}
