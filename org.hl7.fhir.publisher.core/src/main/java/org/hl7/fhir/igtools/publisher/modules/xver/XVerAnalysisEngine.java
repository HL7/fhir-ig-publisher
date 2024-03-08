package org.hl7.fhir.igtools.publisher.modules.xver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.hl7.fhir.convertors.loaders.loaderR5.R2ToR5Loader;
import org.hl7.fhir.convertors.loaders.loaderR5.R3ToR5Loader;
import org.hl7.fhir.convertors.loaders.loaderR5.R4BToR5Loader;
import org.hl7.fhir.convertors.loaders.loaderR5.R4ToR5Loader;
import org.hl7.fhir.convertors.loaders.loaderR5.R5ToR5Loader;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.modules.xver.SourcedElementDefinition.ElementValidState;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r5.model.CodeType;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.ConceptMap.ConceptMapGroupComponent;
import org.hl7.fhir.r5.model.ConceptMap.ConceptMapGroupUnmappedMode;
import org.hl7.fhir.r5.model.ConceptMap.SourceElementComponent;
import org.hl7.fhir.r5.model.ConceptMap.TargetElementComponent;
import org.hl7.fhir.r5.model.Element;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionBindingComponent;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.Enumerations.BindingStrength;
import org.hl7.fhir.r5.model.Enumerations.ConceptMapRelationship;
import org.hl7.fhir.r5.model.Enumerations.FHIRVersion;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.Parameters;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.ExtensionContextType;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionContextComponent;
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
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionComponent;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionContainsComponent;
import org.hl7.fhir.r5.renderers.ConceptMapRenderer.IMultiMapRendererAdvisor;
import org.hl7.fhir.r5.terminologies.ConceptMapUtilities;
import org.hl7.fhir.r5.terminologies.ConceptMapUtilities.TranslatedCode;
import org.hl7.fhir.r5.terminologies.expansion.ValueSetExpansionOutcome;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.DebugUtilities;
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

  public class TypeDefinitionSorter implements Comparator<StructureDefinition> {

    @Override
    public int compare(StructureDefinition o1, StructureDefinition o2) {
      return o1.getType() == null || o2.getType() == null ? 0 : o1.getType().compareTo(o2.getType());
    }

  }

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

  private Map<String, IWorkerContext> versions = new HashMap<>();
  private Map<String, ConceptMap> conceptMaps = new HashMap<>();
  private Map<String, ConceptMap> conceptMapsByUrl = new HashMap<>();
  private Map<String, ConceptMap> conceptMapsByScope = new HashMap<>();
  private Map<String, StructureMap> structureMaps = new HashMap<>();
  private IWorkerContext vdr2;
  private IWorkerContext vdr3;
  private IWorkerContext vdr4;
  private IWorkerContext vdr4b;
  private IWorkerContext vdr5;
  private List<ElementDefinitionLink> allLinks = new ArrayList<>();
  private List<SourcedElementDefinition> terminatingElements = new ArrayList<>();
  private List<SourcedElementDefinition> origins = new ArrayList<>();
  private boolean failures;
  private List<StructureDefinition> extensions = new ArrayList<>();
  private Map<String, ValueSet> newValueSets = new HashMap<>();
  private Map<String, CodeSystem> newCodeSystems = new HashMap<>();

  private boolean execute(String folder) throws FHIRException, IOException {
    // checkIds(folder);
    
    failures = false;
    loadVersions(folder);
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
      identifyChain(te);
    }
    checkAllLinksInChains();

    for (SourcedElementDefinition te : terminatingElements) {
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

    for (SourcedElementDefinition origin : origins) {
      generateExtensions(origin);
    }
    checkAllLinksInChains();

    return !failures;
  }


  private void generateExtensions(SourcedElementDefinition origin) {
    List<ElementDefinitionLink> links = makeEDLinks(origin, MakeLinkMode.ORIGIN_CHAIN);
    checkCreateExtension(null, origin);
    for (ElementDefinitionLink link : links) {
      checkCreateExtension(link, link.getNext());
    }
  }

  private List<String> allVersions() {
    List<String> res = new ArrayList<String>();
    res.add("5.0");
    res.add("4.3");
    res.add("4.0");
    res.add("3.0");
    res.add("1.0");
    return res;
  }
  
  private void checkCreateExtension(ElementDefinitionLink link, SourcedElementDefinition element) {
    if (element.isValid() && element.getEd().getPath().contains(".")) {
      String ver = VersionUtilities.getMajMin(element.getSd().getFhirVersion().toCode());
      // we need to create the extension definition
      StructureDefinition sd = new StructureDefinition();
      element.setExtension(sd);
      extensions.add(sd);
      sd.setUrl(element.extensionPath()); 
      sd.setId("extension-"+VersionUtilities.getNameForVersion(element.getVer()).toLowerCase()+"-"+element.getEd().getPath());
      // sd.setVersion(null); // let the IG set this
      sd.setName("XVerExtension"+element.getEd().getPath()+VersionUtilities.getNameForVersion(element.getVer()).toUpperCase());
      sd.setTitle("Cross-Version Extension for "+element.getEd().getPath()+" ("+VersionUtilities.getNameForVersion(element.getVer()).toUpperCase()+")");
      sd.setStatus(PublicationStatus.ACTIVE);
      sd.setExperimental(false);
      // sd.setDateElement(null); // let the IG set this
      sd.setPublisher(element.getSd().getPublisher());
      sd.getContact().addAll(element.getSd().getContact());
      sd.addJurisdiction().addCoding("http://unstats.un.org/unsd/methods/m49/m49.htm", "001", null);
      sd.setDescription("Cross-Version Extension for "+element.getEd().getPath()+". Valid in versions "+element.getVerList());
      sd.setFhirVersion(FHIRVersion._5_0_0);
      sd.setKind(StructureDefinitionKind.COMPLEXTYPE);
      sd.setType("Extension");
      sd.setBaseDefinition("http://hl7.org/fhir/StructureDefinition/Extension");
      sd.setDerivation(TypeDerivationRule.CONSTRAINT);
      sd.setAbstract(false);
      
      String thisVersionParent = element.getEd().getPath().substring(0, element.getEd().getPath().lastIndexOf("."));
      ElementDefinition ed = element.getSd().getDifferential().getElementByPath(thisVersionParent);
      for (String tgtVer : allVersions()) {
        if (element.appliesToVersion(tgtVer)) {
          List<StructureDefinitionContextComponent> contexts = getParentContextsForVersion((SourcedElementDefinition) ed.getUserData("sed"), ver, tgtVer);
          for (StructureDefinitionContextComponent ctxt : contexts) {
            tagForVersion(tgtVer, ctxt); 
            sd.addContext(ctxt);
          }
        }
      }
      

      ElementDefinition src = element.getEd();
      ElementDefinition edr = new ElementDefinition();
      edr.setPath("Extension");      
      sd.getDifferential().addElement(edr);
      ElementDefinition edv = new ElementDefinition();
      edv.setPath("Extension.value[x]");      
      sd.getDifferential().addElement(edv);

      edr.setLabel(src.getLabel());
      edr.setCode(src.getCode());
      edr.setShort(src.getShort());
      edr.setDefinition(src.getDefinition());
      edr.setComment(src.getComment());
      edr.setRequirements(src.getRequirements());
      edr.getAlias().addAll(src.getAlias());
      edr.setComment(src.getComment());
      edr.setMin(src.getMin());
      edr.setMax(src.getMax());
      edr.setIsModifier(src.getIsModifier());
      edr.setIsModifierReason(src.getIsModifierReason());
//      edr.setIsSummary(src.getIsSummary());
      edr.setMapping(src.getMapping());
      
      edv.setMinValue(src.getMinValue());
      edv.setMaxValue(src.getMaxValue());
      edv.setMaxLengthElement(src.getMaxLengthElement());
      edv.setMustHaveValueElement(src.getMustHaveValueElement());
      edv.setValueAlternatives(src.getValueAlternatives());

      IWorkerContext vd = versions.get(element.getVer());

      switch (element.getValidState()) {
      case CARDINALITY:
        edv.getType().addAll(src.getType());
        copyBinding(vd, edv, src.getBinding());
        addToDescription(sd, "This is a valid cross-version extension because the cardinality changed");
        break;
      case FULL_VALID:
        edv.getType().addAll(src.getType());
        copyBinding(vd, edv, src.getBinding());
        addToDescription(sd, "This is a valid cross-version extension because it's counted as a new element");
        break;
      case NEW_TYPES:
        boolean coded = false;
        for (TypeRefComponent tr : src.getType()) {
          if (element.getNames().contains(tr.getCode())) {
            edv.getType().add(tr);
            if (isCoded(tr.getWorkingCode())) {
              coded = true;
            }
          }
        }
        if (coded) {
          copyBinding(vd, edv, src.getBinding());
        }
        addToDescription(sd, "This is a valid extension because it has the types "+CommaSeparatedStringBuilder.join(", ", element.getNames()));
        break;
      case NEW_TARGETS:
        coded = false;
        for (TypeRefComponent tr : src.getType()) {
          if (isReferenceDataType(tr.getCode())) {
            if (isCoded(tr.getWorkingCode())) {
              coded = true;
            }
            TypeRefComponent n = edv.addType();
            n.setCode(tr.getCode());
            for (CanonicalType tgt : tr.getTargetProfile()) {
              if (element.getNames().contains(tgt.asStringValue())) {
                n.getTargetProfile().add(tgt);
              }   
            }
          }
        }
        if (coded) {
          copyBinding(vd, edv, src.getBinding());
        }
        addToDescription(sd, "This is a valid extension because it has the target resources "+CommaSeparatedStringBuilder.join(", ", element.getNames()));
        break;
      case CODES:
        for (TypeRefComponent tr : src.getType()) {
          if (isCoded(tr.getCode())) {
            edv.getType().add(tr);
          }
        }
        copyBinding(vd, edv, src.getBinding(), element.getNames());
        addToDescription(sd, "This is a valid extension because it has the following codes that are not in other versions "+toString(element.getCodes()));
        break;
      default:      
      }
      // todo: "contentReference" : "<uri>", // I Reference to definition of content for the element
      // todo: type limitations
//      edv.getType().addAll(src.getType());

      // todo: constraints
      // todo: binding      
    }
  }
  

  private void tagForVersion(String tgtVer, Element element) {
    Extension ext = element.addExtension();
    ext.setUrl(ToolingExtensions.EXT_APPLICABLE_VERSION);
    ext.addExtension("startFhirVersion", new CodeType(tgtVer));    
    ext.addExtension("endFhirVersion", new CodeType(tgtVer));    
  }

  private List<StructureDefinitionContextComponent> getParentContextsForVersion(SourcedElementDefinition ed, String ver, String tgtVer) {
    // ok we have a handle to the parent. it's in multiple chains, and we're going to walk forward or backwards to the tgtVer looking for a match
    List<SourcedElementDefinition> list = new ArrayList<>();
    list.add(ed);
    boolean forwards = VersionUtilities.isThisOrLater(ver, tgtVer);
    MakeLinkMode mode = forwards ? MakeLinkMode.OUTWARD : MakeLinkMode.INWARD;
    while (true) {
      List<SourcedElementDefinition> nlist = new ArrayList<>();
      for (SourcedElementDefinition t : list) {
        List<ElementDefinitionLink> links = makeEDLinks(t, mode);
        for (ElementDefinitionLink link : links) {
          SourcedElementDefinition sed = forwards ? link.getNext() : link.getPrev();
          nlist.add(sed);
        }        
      }
      if (nlist.isEmpty()) {
        List<StructureDefinitionContextComponent> res = new ArrayList<>();
        // if it's a contained element, and it's lost its home, it goes onto it's parent extension
        if (ed.getEd().getPath().contains(".")) {
          res.add(new StructureDefinitionContextComponent().setType(ExtensionContextType.EXTENSION).setExpression(ed.extensionPath()));
          
        } else { // otherwise it goes on Basic
          res.add(new StructureDefinitionContextComponent().setType(ExtensionContextType.ELEMENT).setExpression("Basic"));
        }
        return res;
      }
      String fver = VersionUtilities.getMajMin(nlist.get(0).getSd().getFhirVersion().toCode());
      if (fver.equals(tgtVer)) {
        List<StructureDefinitionContextComponent> res = new ArrayList<>();
        for (SourcedElementDefinition t : nlist) {
          res.add(new StructureDefinitionContextComponent().setType(ExtensionContextType.ELEMENT).setExpression(t.getEd().getPath()));        
        }
        return res;
      }
      list = nlist;
    }    
  }

  private boolean isReferenceDataType(String code) {
    return Utilities.existsInList(code, "Reference", "canonical", "CodeableReference");
  }

  private boolean isCoded(String code) {
    return Utilities.existsInList(code, "code", "Coding", "CodeableConcept", "CodeableReference");
  }

  private void addToDescription(StructureDefinition sd, String text) {
    String s = sd.getDescription();
    s = s + "\r\n\r\n"+text;
    sd.setDescription(s);
  }

  private void copyBinding(IWorkerContext defns, ElementDefinition edv, ElementDefinitionBindingComponent binding) {
    if (!binding.isEmpty() && (binding.getStrength() == BindingStrength.EXTENSIBLE || binding.getStrength() == BindingStrength.REQUIRED)) {
      ValueSet vs = defns.fetchResource(ValueSet.class, binding.getValueSet());
      if (vs == null) {
        edv.getBinding().setStrength(binding.getStrength());
        edv.getBinding().setDescription(binding.getDescription());
        edv.getBinding().setValueSet(binding.getValueSet());
      } else {
        edv.getBinding().setStrength(binding.getStrength());
        edv.getBinding().setDescription(binding.getDescription());
        edv.getBinding().setValueSet(importValueSet(defns, vs));
      }
    }
  }

  private String importValueSet(IWorkerContext defns, ValueSet vs) {
    vs = vs.copy();
    assert vs.hasVersion();
    String vurl = vs.getVersionedUrl();
    if (newValueSets.containsKey(vurl)) {
      return vurl;
    }
    for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
      for (CanonicalType ct : inc.getValueSet()) {
        ValueSet ivs = defns.fetchResource(ValueSet.class, ct.asStringValue());
        if (ivs != null) {
          ct.setValue(importValueSet(defns, ivs));
        }
      }
      CodeSystem cs = defns.fetchResource(CodeSystem.class, inc.hasVersion() ? inc.getSystem()+"|"+inc.getVersion() : inc.getSystem());
      if (cs != null) {
        assert cs.hasVersion();
        if (!inc.hasVersion()) {
          inc.setVersion(cs.getVersion());
        }
        newCodeSystems.put(cs.getVersionedUrl(), cs);
      }
    }    
    newValueSets.put(vurl, vs);
    return vurl;
  }

  private void copyBinding(IWorkerContext defns, ElementDefinition edv, ElementDefinitionBindingComponent binding, Set<String> codes) {
    
  }

  
  private void checkIds(String folder) throws IOException {
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
              cr = new StructureMapUtilities(vdr5).parse(source, f.getName());
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
    case "Meta" : return "meta";
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
                      qaMsg("Parameter '"+varName+"' "+(varName2 != null ? "(or "+varName2+") " : "")+"is unknown in rule "+r.getName()+" in group "+grp.getName()+" in map "+map.getUrl()+"; vars = "+dump(tvars), false);
                      //todo  ok = false
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
        IWorkerContext vd = getInputDefinitions(srcList, "target");
        StructureDefinition sd = vd.fetchTypeDefinition(t.getParameter().get(0).getValue().primitiveValue());
        if (sd == null) {
          qaMsg("Cannot find type '"+t.getParameter().get(0).getValue().primitiveValue()+"' in create '"+t.toString()+"' in rule "+r.getName()+" in group "+grp.getName()+" in map "+map.getUrl()+" (vars = "+dump(tvars)+")", true);
          bh.fail();
        } else {
          ElementWithType var = new ElementWithType(vd, sd, sd.getSnapshot().getElementFirstRep());
          tvars = clone(tvars);
          tvars.put("target:"+t.getVariable(), var);
        }
      } else {
        qaMsg("Cannot find tgt var '"+t.getContext()+"' in rule "+r.getName()+" in group "+grp.getName()+" in map "+map.getUrl()+" (vars = "+dump(tvars)+")", false);
        // todo bh.fail();
      }
    } else {
      ElementWithType target = findChild(tgt, t.getElement(), null);
      if (target == null) {
        qaMsg("Cannot find tgt element '"+t.getContext()+"."+t.getElement()+"' on "+tgt.toString()+" in rule "+r.getName()+" in group "+grp.getName()+" in map "+map.getUrl(), false);
//       todo  bh.fail();
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
          VSPair vsl = isCoded(source.toSED());
          VSPair vsr = isCoded(target.toSED());
          if (vsl == null) {
            qaMsg("Rule "+r.getName()+" in group "+grp.getName()+" has a translate operation, but the source element "+source.toSED().toString()+" is not coded in "+map.getUrl(), true);                
          } else if (vsr == null) {
            qaMsg("Rule "+r.getName()+" in group "+grp.getName()+" has a translate operation, but the target element "+target.toSED().toString()+" is not coded in "+map.getUrl(), true);                
          } else {
            if (!(isResourceType(vsl.getCodes()) || isResourceType(vsr.getCodes()))) {
              ConceptMap cm = conceptMapsByUrl.get(url);
              if (cm == null) {
                String srcName = source.getEd().getPath();
                String tgtName = target.getEd().getPath();
                String id = srcName.equals(tgtName) ? srcName+"-"+srcVer+"to"+dstVer : srcName+"-"+tgtName+"-"+srcVer+"to"+dstVer;
                String nid = generateConciseId(id);

                String correct = "http://hl7.org/fhir/uv/xver/ConceptMap/"+nid;
                if (!url.equals(correct)) {
                  qaMsg("bad ref '"+url+"' should be '"+correct+"' in "+map.getUrl(), true);
                } else {
                  qaMsg("Missing ConceptMap '"+url+"' in "+map.getId(), true);
                  var se = source.toSED();
                  var de = target.toSED();
                  String scopeUri = "http://hl7.org/fhir/"+VersionUtilities.getMajMin(se.getVer())+"/StructureDefinition/"+se.getSd().getName()+"#"+se.getEd().getPath();
                  String targetUri = "http://hl7.org/fhir/"+VersionUtilities.getMajMin(de.getVer())+"/StructureDefinition/"+de.getSd().getName()+"#"+de.getEd().getPath();

                  makeCM(nid, id, se, de, vsl, vsr, scopeUri, targetUri);              
                }
              } else {
                String cmSrcVer = VersionUtilities.getNameForVersion(cm.getSourceScope().primitiveValue()).substring(1).toLowerCase();
                String cmDstVer = VersionUtilities.getNameForVersion(cm.getTargetScope().primitiveValue()).substring(1).toLowerCase();
                if (!cmSrcVer.equals(srcVer)) {
                  qaMsg("bad ref '"+url+"' srcVer is "+cmSrcVer+" should be "+srcVer + " in "+map.getUrl(), true);
                } else if (!cmDstVer.equals(dstVer)) {
                  qaMsg("bad ref '"+url+"' dstVer is "+cmDstVer+" should be "+dstVer + " in "+map.getUrl(), true);
                } else {
                  checkCM(cm, source.toSED(), target.toSED(), vsl, vsr);
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

  private IWorkerContext getInputDefinitions(Map<String, SourcedStructureDefinition> srcList, String mode) {
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
      sd = src.getDef().fetchTypeDefinition(wt);
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
      IWorkerContext vd = versions.get(ver);
      StructureDefinition sd = vd.fetchTypeDefinition(tn);
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
    if (ConceptMapUtilities.checkReciprocal(left, right, issues, save)) {
      // wipes formatting in files
      new org.hl7.fhir.r5.formats.JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream("/Users/grahamegrieve/work/fhir-cross-version/input/"+folder+"/ConceptMap-"+left.getId()+".json"), left);
      new org.hl7.fhir.r5.formats.JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream("/Users/grahamegrieve/work/fhir-cross-version/input/"+folder+"/ConceptMap-"+right.getId()+".json"), right);
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
    loadStructureMaps(vdr5, new File(Utilities.path(dir, "input", "R2toR3")));
    loadStructureMaps(vdr5, new File(Utilities.path(dir, "input", "R3toR2")));
    loadStructureMaps(vdr5, new File(Utilities.path(dir, "input", "R3toR4")));
    loadStructureMaps(vdr5, new File(Utilities.path(dir, "input", "R4BtoR5")));
    loadStructureMaps(vdr5, new File(Utilities.path(dir, "input", "R4toR3")));
    loadStructureMaps(vdr5, new File(Utilities.path(dir, "input", "R4toR5")));
    loadStructureMaps(vdr5, new File(Utilities.path(dir, "input", "R5toR4")));
    loadStructureMaps(vdr5, new File(Utilities.path(dir, "input", "R5toR4B")));
  }

  private void loadStructureMaps(IWorkerContext ctxt, File file) throws FHIRFormatError, FileNotFoundException, IOException {
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
    
    origin.setValidState(ElementValidState.FULL_VALID);
    origin.setStartVer(origin.getVer());
    origin.setStopVer(origin.getVer());
    all.add(origin);

    SourcedElementDefinition template = origin;    
    for (ElementDefinitionLink link : links) {
      SourcedElementDefinition element = link.getNext();
      all.add(element);
      if (link.getRel() != ConceptMapRelationship.EQUIVALENT) {
        element.addStatusReason("Not Equivalent");
        element.setValidState(ElementValidState.FULL_VALID);
        template = element;        
        template.setStartVer(element.getVer());
        template.setStopVer(element.getVer()); 
      } else if (!template.getEd().repeats() && element.getEd().repeats()) {
        element.addStatusReason("Element repeats");
        element.setValidState(ElementValidState.CARDINALITY);
        template.setRepeater(element);
        template = element;        
        template.setStartVer(element.getVer());
        template.setStopVer(element.getVer()); 
      } else {
        List<String> newTypes = findNewTypes(template.getEd(), element.getEd());
        if (!newTypes.isEmpty()) {
          element.addStatusReason("New Types "+CommaSeparatedStringBuilder.join("|", newTypes));
          element.setValidState(ElementValidState.NEW_TYPES);
          element.addToNames(newTypes);
          template = element;        
          template.setStartVer(element.getVer());
          template.setStopVer(element.getVer()); 
        } else {
          List<String> newTargets = findNewTargets(template.getEd(), element.getEd());
          if (!newTargets.isEmpty()) {
            element.addStatusReason("New Targets "+CommaSeparatedStringBuilder.join("|", newTargets));
            element.setValidState(ElementValidState.NEW_TARGETS);
            element.addToNames(newTargets);
            template = element;        
            template.setStartVer(element.getVer());
            template.setStopVer(element.getVer()); 
          } else {
            element.clearStatusReason();
            element.setValidState(ElementValidState.NOT_VALID);
            template.setStopVer(element.getVer()); 
          }
        }
      }
    }

    for (SourcedElementDefinition element : all) {
      if (element.getValidState() != ElementValidState.NOT_VALID) {
        String bv = element.getStartVer();
        String ev = element.getRepeater() != null ? element.getRepeater().getStopVer() : element.getStopVer();
        Set<String> versions = new HashSet<>();
        CommaSeparatedStringBuilder vers = makeVerList(bv, ev, versions);
        if (vers.count() == 0) {
          element.setValidState(ElementValidState.NOT_VALID);
          element.addStatusReason("??");
        } else {
          element.setVersions(versions);
          element.setVerList(vers.toString());
        }
      }
    }

    for (ElementDefinitionLink link : links) {
      VSPair l = isCoded(link.getPrev());
      VSPair r = isCoded(link.getNext());
      if (l != null && r != null) {
        if (isResourceType(l.getCodes()) || isResourceType(r.getCodes())) {
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

          if (cmF != null) {
            checkCM(cmF, link.getPrev(), link.getNext(), l, r);
          } else { // if (!rset.containsAll(lset)) {
            System.out.println("didn't find "+leftScope + ":"+rightScope);
            String nid = generateConciseId(idF);
            cmF = makeCM(nid, idF, link.getPrev(), link.getNext(), l, r, leftScope, rightScope);              
          }

          if (cmR != null) {
            checkCM(cmR, link.getNext(), link.getPrev(), r, l);
          } else { // if (!lset.containsAll(rset)) {
            System.out.println("didn't find "+rightScope + ":"+leftScope);
            String nid = generateConciseId(idR);
            cmR = makeCM(nid, idR, link.getNext(), link.getPrev(), r, l, rightScope, leftScope);            
          }
          if (cmF != null && cmR != null) {
            List<String> errs = new ArrayList<String>();
            boolean altered = ConceptMapUtilities.checkReciprocal(cmF, cmR, errs, true);
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
          
          if (!r.getCodes().isEmpty() && cmR != null && !link.getNext().isValid()) {
            link.setNewCodes(ConceptMapUtilities.listCodesWithNoMappings(r.getAllCodes(), cmR));
            if (!link.getNewCodes().isEmpty()) {
              link.getNext().setValidState(ElementValidState.CODES);
              link.getNext().addToCodes(link.getNewCodes());
              link.getNext().setStartVer(template.getVer());
              link.getNext().setStopVer(link.getPrev().getVer());
              CommaSeparatedStringBuilder vers = makeVerListPositive(template.getVer(), link.getPrev().getVer());
              link.getNext().setVerList(vers.toString());
              link.getNext().addStatusReason("Added "+Utilities.pluralize("code", link.getNewCodes().size())+" '"+toString(link.getNewCodes())+"'");              
            }
          }
          if (!l.getCodes().isEmpty() && cmF != null && !link.getPrev().isValid()) {
            link.setOldCodes(ConceptMapUtilities.listCodesWithNoMappings(l.getAllCodes(), cmF));
            if (!link.getOldCodes().isEmpty()) {
              link.getPrev().setValidState(ElementValidState.CODES);
              // no we need to know... which version was it defined in? and how far does this chain go?
              link.getPrev().setStartVer(link.getNext().getVer());
              link.getPrev().setStopVer("5.0.0");
              CommaSeparatedStringBuilder vers = makeVerListPositive(link.getNext().getVer(), "5.0.0");
              link.getPrev().setVerList(vers.toString());
              link.getPrev().addToCodes(link.getOldCodes());
              link.getPrev().addStatusReason("Removed "+Utilities.pluralize("code", link.getOldCodes().size())+" '"+toString(link.getOldCodes())+"'");              
            }
          }
        }
      }        
    }
  }

  private boolean isResourceType(Map<String, Set<Coding>> codes) {
    for (String s : codes.keySet()) {
      if (s.contains("resource-type")) {
        return true;
      }
    }
    return false;
  }

  private CommaSeparatedStringBuilder makeVerList(String bv, String ev, Set<String> versions) {
    CommaSeparatedStringBuilder vers = new CommaSeparatedStringBuilder();

    if (!VersionUtilities.includedInRange(bv, ev, "1.0.2")) {
      vers.append("R2");
      versions.add("1.0");
    }
    if (!VersionUtilities.includedInRange(bv, ev, "3.0.2")) {
      vers.append("R3");
      versions.add("3.0");
    }
    if (!VersionUtilities.includedInRange(bv, ev, "4.0.1")) {
      vers.append("R4");
      versions.add("4.0");
    }
    if (!VersionUtilities.includedInRange(bv, ev, "4.3.0")) {
      vers.append("R4B");
      versions.add("4.3");
    }
    if (!VersionUtilities.includedInRange(bv, ev, "5.0.0")) {
      vers.append("R5");
      versions.add("5.0");
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

  

  private ConceptMap makeCM(String id, String rawId, SourcedElementDefinition se, SourcedElementDefinition de, VSPair s, VSPair d, String scopeUri, String targetUri) throws FileNotFoundException, IOException {
//    if (true) {
//      throw new Error("why?");
//    }
    
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

    Set<Coding> unmapped = new HashSet<>();    
    for (String vu : d.getCodes().keySet()) {
      Set<Coding> dst = d.getCodes().get(vu);
      Set<Coding> src = s.getCodes().get(vu);
      if (src == null) {
        unmapped.addAll(dst);
      } else for (Coding c : dst) {
        if (!src.contains(c)) {
          unmapped.add(c);
        }
      }
    }

    for (String vu : s.getCodes().keySet()) {
      Set<Coding> src = s.getCodes().get(vu);
      String dvu = vu;
      Set<Coding> dst = d.getCodes().get(dvu);
      if (dst == null && s.getCodes().size() == 1 && d.getCodes().size() == 1) {
        dvu = d.getCodes().keySet().iterator().next();
        dst = d.getCodes().get(dvu);      
      }
      ConceptMapGroupComponent g = cm.forceGroup(injectVersionToUri(vu, VersionUtilities.getMajMin(se.getVer())),
          injectVersionToUri(dvu, VersionUtilities.getMajMin(de.getVer())));

      for (Coding c : src) {
        if (c.hasCode() && !c.getCode().startsWith("_") && !isNotSelectable(c) && !Utilities.existsInList(c.getCode(), "null")) {
          if (dst == null) {
            Coding pc = findCode(unmapped, null, c.getCode());
            if (pc == null) {
              SourceElementComponent e = g.addElement();
              e.setCode(c.getCode());
              e.setNoMap(true);         
            } else {
              ConceptMapGroupComponent pg = cm.forceGroup(injectVersionToUri(vu, VersionUtilities.getMajMin(se.getVer())), injectVersionToUri(pc.getSystem(), VersionUtilities.getMajMin(se.getVer())));
              if (pg.getElement().isEmpty()) {
                SourceElementComponent e = pg.addElement();
                e.setCode("CHECK!");              
              }
              SourceElementComponent e = pg.addElement();
              e.setCode(c.getCode());
              TargetElementComponent tgt = e.addTarget();
              tgt.setCode(pc.getCode());
              tgt.setRelationship(ConceptMapRelationship.EQUIVALENT);
            }
          } else if (!hasCode(dst, null, c.getCode())) {
            Coding pc = findCode(unmapped, null, c.getCode());
            if (pc == null) {
              SourceElementComponent e = g.addElement();
              e.setCode(c.getCode());
              e.setNoMap(true);
              if (dst.size() <= 10) {
                if (unmapped.size() <= 10) {
                  e.setDisplay("CHECK! missed = "+toString(unmapped)+"; all = "+toString(dst));
                } else {
                  e.setDisplay("CHECK! missed = ##; all = "+toString(dst));
                }
                TargetElementComponent tgt = e.addTarget();
                tgt.setCode("CHECK!");
                tgt.setRelationship(ConceptMapRelationship.EQUIVALENT);
              } else {
                // e.setDisplay("CHECK!");            
              }
            } else {
              ConceptMapGroupComponent pg = cm.forceGroup(injectVersionToUri(vu, VersionUtilities.getMajMin(se.getVer())), injectVersionToUri(pc.getSystem(), VersionUtilities.getMajMin(se.getVer())));
              if (pg.getElement().isEmpty()) {
                SourceElementComponent e = pg.addElement();
                e.setCode("CHECK!");              
              }
              SourceElementComponent e = pg.addElement();
              e.setCode(c.getCode());
              TargetElementComponent tgt = e.addTarget();
              tgt.setCode(pc.getCode());
              tgt.setRelationship(ConceptMapRelationship.EQUIVALENT);
            }
          } else {
            SourceElementComponent e = g.addElement();
            e.setCode(c.getCode());
            TargetElementComponent tgt = e.addTarget();
            tgt.setCode(c.getCode());
            tgt.setRelationship(ConceptMapRelationship.EQUIVALENT);        
          }
        }
      }
      Collections.sort(g.getElement(), new ConceptMapUtilities.ConceptMapElementSorter());
    }
    cm.getGroup().removeIf(g -> g.getElement().isEmpty());
    new org.hl7.fhir.r5.formats.JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream("/Users/grahamegrieve/work/fhir-cross-version/input/codes/ConceptMap-"+cm.getId()+".json"), cm);
    return cm;
  }

  private Coding findCode(Set<Coding> codes, String system, String code) {
    if (codes == null) {
      return null;
    }
    for (Coding c : codes) {
      if ((system == null || system.equals(c.getSystem())) && code.equals(c.getCode())) {
        return c;
      }
    }
    return null;

  }

  private String toString(Set<Coding> codes) {
    if (codes == null) {
      return "--";
    }
    String system = null;
    boolean ok = true;
    for (Coding c : codes) {
      if (system == null) {
        if (ok) {
          system = c.getSystem();
        }        
      } else if (!system.equals(c.getSystem())) {
        system = null;
        ok = false;
      }
    }
    if (ok && system != null) {
      CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
      for (Coding c : codes) {
        b.append(c.getCode());
      }
      return b.toString()+" ("+system+")";
    } else {
      CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
      for (Coding c : codes) {
        b.append(c.getSystem()+"#"+c.getCode());
      }
      return b.toString();
    }
  }

  private void checkCM(ConceptMap cm, SourcedElementDefinition se, SourcedElementDefinition de, VSPair s, VSPair d) throws FileNotFoundException, IOException {
    cm.setUserData("cm.used", "true");
    boolean mod = false;
    
    String scopeUri = "http://hl7.org/fhir/"+VersionUtilities.getMajMin(se.getVer())+"/StructureDefinition/"+se.getSd().getName()+"#"+se.getEd().getPath();
    String targetUri = "http://hl7.org/fhir/"+VersionUtilities.getMajMin(de.getVer())+"/StructureDefinition/"+de.getSd().getName()+"#"+de.getEd().getPath();
    if (!cm.hasSourceScopeUriType()) {
      qaMsg("Issue with "+cm.getId()+": Source Scope should be "+scopeUri, true);
      cm.setSourceScope(new UriType(scopeUri));
      mod = true;
    } else if (!scopeUri.equals(cm.getSourceScopeUriType().primitiveValue())) {
      qaMsg("Issue with "+cm.getId()+": Source Scope should be "+scopeUri+" not "+cm.getSourceScopeUriType().primitiveValue(), true);
      cm.setSourceScope(new UriType(scopeUri));
      mod = true;
    }
    if (!cm.hasTargetScopeUriType()) {
      qaMsg("Issue with "+cm.getId()+": Target Scope should be "+targetUri, true);
      cm.setTargetScope(new UriType(targetUri));
      mod = true;
    } else if (!targetUri.equals(cm.getTargetScopeUriType().primitiveValue())) {
      qaMsg("Issue with "+cm.getId()+": Target Scope should be "+targetUri+" not "+cm.getTargetScopeUriType().primitiveValue(), true);
      cm.setTargetScope(new UriType(targetUri));
      mod = true;
    }
    
    Set<Coding> unmapped = new HashSet<>();    
    for (String vu : d.getCodes().keySet()) {
      Set<Coding> dst = d.getCodes().get(vu);
      Set<Coding> src = s.getCodes().get(vu);
      if (src == null) {
        unmapped.addAll(dst);
      } else for (Coding c : dst) {
        if (!hasCode(src, c.getSystem(), c.getCode())) {
          unmapped.add(c);
        }
      }
    }

    Set<ConceptMapGroupComponent> ug = new HashSet<>();
    for (String vu : s.getCodes().keySet()) {
      Set<Coding> src = s.getCodes().get(vu);
      Set<Coding> dst = d.getCodes().get(vu);
      String su = injectVersionToUri(vu, VersionUtilities.getMajMin(se.getVer()));
      String tu = injectVersionToUri(vu, VersionUtilities.getMajMin(de.getVer()));
      String suVL = removeVersion(su);
      String tuVL = removeVersion(tu);
      ConceptMapGroupComponent g = cm.getGroup(su, tu);
      if (g == null) {
        if (cm.getGroup().size() == 1 && su.equals(cm.getGroupFirstRep().getSource())) {
          g = cm.getGroupFirstRep();
          tu = g.getTarget();
          tuVL = removeVersion(tu); 
          dst = d.getCodes().get(tuVL);
        }
      }
      if (g == null) {
        g = cm.addGroup();
        ug.add(g);
        mod = true;        
        g.setSource(su);
        g.setTarget(tu);
        for (Coding c : src) {
          if (c.hasCode() && !c.getCode().startsWith("_") && !isNotSelectable(c) && !Utilities.existsInList(c.getCode(), "null")) {
            if (dst == null) {
              Coding pc = findCode(unmapped, null, c.getCode());
              if (pc == null) {
                SourceElementComponent e = g.addElement();
                e.setCode(c.getCode());
                e.setNoMap(true);         
              } else {
                ConceptMapGroupComponent pg = cm.forceGroup(injectVersionToUri(vu, VersionUtilities.getMajMin(se.getVer())), injectVersionToUri(pc.getSystem(), VersionUtilities.getMajMin(se.getVer())));
                ug.add(pg);
                if (pg.getElement().isEmpty()) {
                  SourceElementComponent e = pg.addElement();
                  e.setCode("CHECK!");              
                }
                SourceElementComponent e = pg.getOrAddElement(c.getCode());
                if (!e.hasTargetCode(pc.getCode())) {
                  TargetElementComponent tgt = e.addTarget();
                  tgt.setCode(pc.getCode());
                  tgt.setRelationship(ConceptMapRelationship.EQUIVALENT);
                }
              }
            } else if (!hasCode(dst, null, c.getCode())) {
              Coding pc = findCode(unmapped, null, c.getCode());
              if (pc == null) {
                SourceElementComponent e = g.addElement();
                e.setCode(c.getCode());
                e.setNoMap(true);
                if (dst.size() <= 10) {
                  if (unmapped.size() <= 10) {
                    e.setDisplay("CHECK! missed = "+toString(unmapped)+"; all = "+toString(dst));
                  } else {
                    e.setDisplay("CHECK! missed = ##; all = "+toString(dst));
                  }
                  if (!e.hasTarget()) {
                    TargetElementComponent tgt = e.addTarget();
                    tgt.setCode("CHECK!");
                    tgt.setRelationship(ConceptMapRelationship.EQUIVALENT);
                  }
                } else {
                  // e.setDisplay("CHECK!");            
                }
              } else {
                ConceptMapGroupComponent pg = cm.forceGroup(injectVersionToUri(vu, VersionUtilities.getMajMin(se.getVer())), injectVersionToUri(pc.getSystem(), VersionUtilities.getMajMin(se.getVer())));
                ug.add(pg);
                if (pg.getElement().isEmpty()) {
                  SourceElementComponent e = pg.addElement();
                  e.setCode("CHECK!");              
                }
                SourceElementComponent e = pg.getOrAddElement(c.getCode());
                if (!e.hasTargetCode(pc.getCode())) {
                  TargetElementComponent tgt = e.addTarget();
                  tgt.setCode(pc.getCode());
                  tgt.setRelationship(ConceptMapRelationship.EQUIVALENT);
                }
              }
            } else {
              SourceElementComponent e = g.addElement();
              e.setCode(c.getCode());
              if (!e.hasTargetCode(c.getCode())) {
                TargetElementComponent tgt = e.addTarget();
                tgt.setCode(c.getCode());
                tgt.setRelationship(ConceptMapRelationship.EQUIVALENT);
              }
            }        
          }
        }
      } else {
        ug.add(g);
        for (SourceElementComponent e : g.getElement()) {
          if (e.hasDisplay()) {
            qaMsg("Issue with "+cm.getId()+": "+e.getCode()+" has a display", true);
            e.setDisplay(null);
            mod = true;
          }
          for (TargetElementComponent tgt : e.getTarget()) {
            if (!tgt.hasCode()) {
              tgt.setCode("CHECK!");              
              mod = true;
              qaMsg("Issue with "+cm.getId()+": "+e.getCode()+" has a target without a code", true);
            }
            if (tgt.hasDisplay()) {
              tgt.setDisplay(null);
              mod = true;
              qaMsg("Issue with "+cm.getId()+": "+tgt.getCode()+" has a display", true);
            }
          }
          if (e.hasTarget() && e.getNoMap()) {
            qaMsg("Issue with "+cm.getId()+": "+e.getCode()+" has both targets and noMap = true", true);
            e.setDisplay("CHECK!");
            mod = true;
          }
        }

        //      scopeUri = injectVersionToUri(s.getCs().getUrl(), VersionUtilities.getMajMin(se.getVer()));
        //      targetUri = injectVersionToUri(d.getCs().getUrl(), VersionUtilities.getMajMin(de.getVer()));
        //      if (!scopeUri.equals(g.getSource())) {
        //        g.setSource(scopeUri);
        //        qaMsg("Issue with "+cm.getId()+": Group source should be "+scopeUri+" not "+g.getSource(), true);
        //      }
        //      if (!targetUri.equals(g.getTarget())) {
        //        qaMsg("Issue with "+cm.getId()+": Group target should be "+targetUri+" not "+g.getTarget(), true);
        //      }
        Set<Coding> missed = new HashSet<>();
        Set<Coding> invalid = new HashSet<>();
        Set<Coding> mapped = new HashSet<>(); 
        Set<SourceElementComponent> matched = new HashSet<>();
        for (Coding c : src) {
          if (c.hasCode() && !c.getCode().startsWith("_") && !isNotSelectable(c) && !Utilities.existsInList(c.getCode(), "null")) {
            SourceElementComponent e = getSource(g, c.getCode());
            if (e != null) {
              matched.add(e);
              for (TargetElementComponent tgt : e.getTarget()) {
                if (tgt.hasCode()) {
                  if (!hasCode(dst, tuVL, tgt.getCode())) {
                    invalid.add(c);
                    tgt.setUserData("delete", true);
                  } else {
                    mapped.add(c);
                  }
                }
              }
            } else if ((dst == null || !dst.contains(c)) || !hasUnMapped(g)) {
              missed.add(c);
            }        
          }
        }
        if (src != null && g.getElement().removeIf(e -> !matched.contains(e))) {
          mod = true;
        }
        if (!missed.isEmpty()) {
          Set<Coding> amissed = new HashSet<>();
          for (Coding c : missed) {          
            if (!c.getCode().startsWith("_") && !c.getCode().equals("null") && !isNotSelectable(c)) {
              mod = true;
              Coding dc = findCode(dst, c.getSystem(), c.getCode());
              if (dc == null) {
                dc = findCode(unmapped, c.getSystem(), c.getCode());
              }
              if (dc != null) {
                SourceElementComponent e = g.addElement();
                e.setCode(c.getCode());
                TargetElementComponent tgt = e.addTarget();
                tgt.setCode(dc.getCode());
                tgt.setRelationship(ConceptMapRelationship.EQUIVALENT);
              } else {
                dc = findCode(unmapped, null, c.getCode());
                if (dc != null) {
                  ConceptMapGroupComponent pg = cm.forceGroup(injectVersionToUri(vu, VersionUtilities.getMajMin(se.getVer())), injectVersionToUri(dc.getSystem(), VersionUtilities.getMajMin(se.getVer())));
                  ug.add(pg);
                  if (pg.getElement().isEmpty()) {
                    SourceElementComponent e = pg.addElement();
                    e.setCode("CHECK!");              
                  }
                  SourceElementComponent e = pg.getOrAddElement(c.getCode());
                  if (!e.hasTargetCode(dc.getCode())) {
                    TargetElementComponent tgt = e.addTarget();
                    tgt.setCode(dc.getCode());
                    tgt.setRelationship(ConceptMapRelationship.EQUIVALENT);
                  }
                } else {
                  SourceElementComponent e = g.addElement();
                  e.setCode(c.getCode());
                  e.setNoMap(true);
                }
              }
//              g.addElement().setCode(c.getCode()).setNoMap(true).addTarget().setCode("CHECK!!").setRelationship(ConceptMapRelationship.EQUIVALENT).setDisplay("unmapped: "+toString(unmapped));
//              mod = false; // true;        
//              amissed.add(c);
            }
          }
//          if (!amissed.isEmpty()) {
//            qaMsg("Concept Map "+cm.getId()+" is missing mappings for "+toString(amissed), true);
//          }
        }
        if (!invalid.isEmpty()) {
          qaMsg("Concept Map "+cm.getId()+" has invalid mappings to "+toString(invalid), true);
          if (dst != null) {
            for (SourceElementComponent e : g.getElement()) {
              if (e.getTarget().removeIf(t -> t.hasUserData("delete"))) {
                mod = true;
                if (e.getTarget().isEmpty()) {
                  e.setNoMap(true);
                }
              }
            }
          }
          
          for (Coding c : src) {
            SourceElementComponent e = getSource(g, c.getCode());
            if (e != null) {
              for (TargetElementComponent tgt : e.getTarget()) {
                if (!hasCode(dst, tuVL, tgt.getCode())) {
//                  tgt.setComment("CHECK!: target "+tgt.getCode()+" is not valid (missed "+toString(unmapped)+")");
//                  mod = false; // true;        
                  qaMsg("Issue with "+cm.getId()+": target "+tgt.getCode()+" is not valid (missed "+toString(unmapped)+")", true);
                }
              }
              if (e.getNoMap()) {
                qaMsg("Issue with "+cm.getId()+": missed: "+toString(unmapped), true);
              }
            }        
          }
        }
        invalid.clear();
        for (SourceElementComponent t : g.getElement()) {
          if (!hasCode(src, suVL, t.getCode())) {
            invalid.add(new Coding(tuVL, t.getCode(), null));
//            t.setDisplay("Source "+t.getCode()+" is not valid");
//            mod = false; // true;        
            qaMsg("CHECK!: Issue with "+cm.getId()+": source "+t.getCode()+" is not valid" , true);
          }
        }
        if (!invalid.isEmpty()) {
          qaMsg("Concept Map "+cm.getId()+" has invalid mappings from "+toString(invalid), true);
        }
      }
    }
    if (cm.getGroup().removeIf(g -> !ug.contains(g))) {
      mod = true;
    }
    if (cm.getGroup().removeIf(g -> g.getElement().isEmpty())) {
      mod = true;
    }
//    if (mod) {
//      FileOutputStream f = new FileOutputStream("/Users/grahamegrieve/work/fhir-cross-version/input/codes/ConceptMap-"+cm.getId()+".json");
//      new JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(f, cm);
//      f.close();
//      System.out.println("Gen "+("/Users/grahamegrieve/work/fhir-cross-version/input/codes/ConceptMap-"+cm.getId()+".json"));
//    }
  }


  private boolean isNotSelectable(Coding c) {
    return c.hasUserData("abstract");
  }

  private String removeVersion(String url) {
    return url.replace("/1.0", "").replace("/3.0", "").replace("/4.0", "").replace("/4.3", "").replace("/5.0", "");
  }

  private boolean hasCode(Set<Coding> codes, String system, String code) {
    if (codes == null) {
      return false;
    }
    for (Coding c : codes) {
      if ((system == null || system.equals(c.getSystem())) && code.equals(c.getCode())) {
        return true;
      }
    }
    return false;
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

  private VSPair isCoded(ElementWithType et) {
    IWorkerContext vd = et.getDef();
    if (et.getEd().getBinding().getStrength() == BindingStrength.REQUIRED || et.getEd().getBinding().getStrength() == BindingStrength.EXTENSIBLE) {
      ValueSet vs = vd.fetchResource(ValueSet.class, et.getEd().getBinding().getValueSet());
      if (vs != null && vs.getCompose().getInclude().size() == 1) {
        ValueSetExpansionOutcome vse = vd.expandVS(vs, logStarted, false);
        if (vse.getValueset() != null) {
          Set<Coding> codes = processExpansion(vse.getValueset().getExpansion());
          if (codes.size() > 0) {
            return new VSPair(et.getDef().getVersion(), vs, codes);
          }
        }

      }
    }
    return null;
    
  }
  
  private VSPair isCoded(SourcedElementDefinition pair) {
    String v = VersionUtilities.getNameForVersion(pair.getVer()).toLowerCase();
    IWorkerContext vd = versions.get(v);
    if (pair.getEd().getBinding().getStrength() == BindingStrength.REQUIRED || pair.getEd().getBinding().getStrength() == BindingStrength.EXTENSIBLE) {
      ValueSet vs = vd.fetchResource(ValueSet.class, pair.getEd().getBinding().getValueSet());
      if (vs != null) {
        ValueSetExpansionOutcome vse = vd.expandVS(vs, logStarted, false);
        if (vse.getValueset() != null) {
          Set<Coding> codes = processExpansion(vse.getValueset().getExpansion());
          if (codes.size() > 0) {
            return new VSPair(v, vs, codes);
          }
        }
      }
    }
    return null;
  }

  private Set<Coding> processExpansion(ValueSetExpansionComponent expansion) {
    Set<Coding> codes = new HashSet<>();
    for (ValueSetExpansionContainsComponent cc : expansion.getContains()) {
      Coding c = new Coding(cc.getSystem(), cc.getVersion(), cc.getCode(), cc.getDisplay());
      if (cc.hasAbstract() && cc.getAbstract()) {
        c.setUserData("abstract", true);
      }
      codes.add(c);
    }
    return codes;
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

  private void buildLinks(XVersions ver, IWorkerContext defsPrev, ConceptMap resFwd, ConceptMap elementFwd, IWorkerContext defsNext, boolean last) {
    logProgress("Build links between "+defsPrev.getVersion()+" and "+defsNext.getVersion());

    for (StructureDefinition sd : sortedSDs(defsPrev.fetchResourcesByType(StructureDefinition.class))) {
      if (sd.getKind() == StructureDefinitionKind.COMPLEXTYPE && (!sd.getAbstract() || Utilities.existsInList(sd.getName(), "Quantity")) && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
        List<SourcedStructureDefinition> matches = new ArrayList<>();
        matches.add(new SourcedStructureDefinition(defsNext, defsNext.fetchTypeDefinition(sd.getType()), ConceptMapRelationship.EQUIVALENT));        
        buildLinksForElements(ver, elementFwd, sd, matches);
      }
    }

    for (StructureDefinition sd : sortedSDs(defsPrev.fetchResourcesByType(StructureDefinition.class))) {
      if (sd.getKind() == StructureDefinitionKind.RESOURCE && !sd.getAbstract() && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
        
        List<SourcedStructureDefinition> matches = new ArrayList<>();
        List<TranslatedCode> names = translateResourceName(resFwd, sd.getType());
        if (names.isEmpty()) {
          matches.add(new SourcedStructureDefinition(defsNext, null, null));
        } else {
          for (TranslatedCode n : names) {
            matches.add(new SourcedStructureDefinition(defsNext, defsNext.fetchTypeDefinition(n.getCode()), n.getRelationship()));
          }
        }
        buildLinksForElements(ver, elementFwd, sd, matches);
      }
    }
    
    if (last) {
      // now that we've done that, scan anything in defsNext that didn't get mapped to from destPrev

      for (StructureDefinition sd : sortedSDs(defsNext.fetchResourcesByType(StructureDefinition.class))) {
        if (sd.getKind() == StructureDefinitionKind.COMPLEXTYPE && (!sd.getAbstract() || Utilities.existsInList(sd.getName(), "Quantity")) && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
          for (ElementDefinition ed : sd.getDifferential().getElement()) {
            if (!ed.hasUserData("sed")) {
              List<ElementDefinitionLink> links = makeEDLinks(ed, MakeLinkMode.OUTWARD);
              terminatingElements.add(makeSED(sd, ed));
            }
          }     
        }
      }

      for (StructureDefinition sd : sortedSDs(defsNext.fetchResourcesByType(StructureDefinition.class))) {
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


  public List<StructureDefinition> sortedSDs(List<StructureDefinition> list) {
    List<StructureDefinition> res = new ArrayList<StructureDefinition>();
    for (StructureDefinition sd : list) {
      if (sd.getType() != null) {
        res.add(sd);
      }
    }
    Collections.sort(res, new TypeDefinitionSorter());
    return res;
  
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


  private void loadVersions(String path) throws IOException {
    FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager.Builder().build();
    vdr2 = loadR2(path, pcm);
    versions.put("r2", vdr2);
    versions.put(vdr2.getVersion(), vdr2);
    vdr3 = loadR3(path, pcm);
    versions.put("r3", vdr3);
    versions.put(vdr3.getVersion(), vdr3);
    vdr4 = loadR4(path, pcm);
    versions.put("r4", vdr4);
    versions.put(vdr4.getVersion(), vdr4);
    vdr4b = loadR4B(path, pcm);
    versions.put("r4b", vdr4b);
    versions.put(vdr4b.getVersion(), vdr4b);
    vdr5 = loadR5(path, pcm);
    versions.put("r5", vdr5);
    versions.put(vdr5.getVersion(), vdr5);
  }


  private IWorkerContext loadR2(String path, FilesystemPackageCacheManager pcm) throws FHIRException, IOException {
    logProgress("Load R2");
    NpmPackage npm = pcm.loadPackage("hl7.fhir.r2.core");
    R2ToR5Loader ldr = new R2ToR5Loader(loadTypes(), new XVerAnalysisLoader("http://hl7.org/fhir/DSTU2"));
    SimpleWorkerContext ctxt = new SimpleWorkerContext.SimpleWorkerContextBuilder().withTerminologyCachePath(Utilities.path(path, "input-cache", "xv-tx", "r2")).fromPackage(npm, ldr, true);
    ctxt.connectToTSServer(ldr.txFactory(), "http://tx.fhir.org", "Java Client", null);
    ctxt.setExpansionParameters(new Parameters());
    return ctxt;
  }

  private IWorkerContext loadR3(String path, FilesystemPackageCacheManager pcm) throws FHIRException, IOException {
    logProgress("Load R3");
    NpmPackage npm = pcm.loadPackage("hl7.fhir.r3.core");
    R3ToR5Loader ldr = new R3ToR5Loader(loadTypes(), new XVerAnalysisLoader("http://hl7.org/fhir/STU3"));
    SimpleWorkerContext ctxt = new SimpleWorkerContext.SimpleWorkerContextBuilder().withTerminologyCachePath(Utilities.path(path, "input-cache", "xv-tx", "r3")).fromPackage(npm, ldr, true);
    ctxt.connectToTSServer(ldr.txFactory(), "http://tx.fhir.org", "Java Client", null);
    ctxt.setExpansionParameters(new Parameters());
    return ctxt;
  }

  private IWorkerContext loadR4(String path, FilesystemPackageCacheManager pcm) throws FHIRFormatError, FHIRException, IOException {
    logProgress("Load R4");
    NpmPackage npm = pcm.loadPackage("hl7.fhir.r4.core");
    R4ToR5Loader ldr = new R4ToR5Loader(loadTypes(), new XVerAnalysisLoader("http://hl7.org/fhir/R4"), "4.0.0");
    SimpleWorkerContext ctxt = new SimpleWorkerContext.SimpleWorkerContextBuilder().withTerminologyCachePath(Utilities.path(path, "input-cache", "xv-tx", "r4")).fromPackage(npm, ldr, true);
    ctxt.connectToTSServer(ldr.txFactory(), "http://tx.fhir.org", "Java Client", null);
    ctxt.setExpansionParameters(new Parameters());
    return ctxt;
  }

  private IWorkerContext loadR4B(String path, FilesystemPackageCacheManager pcm) throws FHIRException, IOException {
    logProgress("Load R4B");
    NpmPackage npm = pcm.loadPackage("hl7.fhir.r4b.core");
    R4BToR5Loader ldr = new R4BToR5Loader(loadTypes(), new XVerAnalysisLoader("http://hl7.org/fhir/R4B"), "4.3.0");
    SimpleWorkerContext ctxt = new SimpleWorkerContext.SimpleWorkerContextBuilder().withTerminologyCachePath(Utilities.path(path, "input-cache", "xv-tx", "r4b")).fromPackage(npm, ldr, true);
    ctxt.connectToTSServer(ldr.txFactory(), "http://tx.fhir.org", "Java Client", null);
    ctxt.setExpansionParameters(new Parameters());
    return ctxt;
  }

  private IWorkerContext loadR5(String path, FilesystemPackageCacheManager pcm) throws FHIRException, IOException {
    logProgress("Load R5");
    NpmPackage npm = pcm.loadPackage("hl7.fhir.r5.core");
    R5ToR5Loader ldr = new R5ToR5Loader(loadTypes(), new XVerAnalysisLoader("http://hl7.org/fhir/R5"));
    SimpleWorkerContext ctxt = new SimpleWorkerContext.SimpleWorkerContextBuilder().withTerminologyCachePath(Utilities.path(path, "input-cache", "xv-tx", "r5")).fromPackage(npm, ldr, true);
    ctxt.connectToTSServer(ldr.txFactory(), "http://tx.fhir.org", "Java Client", null);
    ctxt.setExpansionParameters(new Parameters());
    return ctxt;
  }

  private List<String> loadTypes() {
    List<String> types = new ArrayList<String>();
    types.add("StructureDefinition");
    types.add("ValueSet");
    types.add("CodeSystem");
    return types;
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
      if (vdr5.fetchTypeDefinition(code) != null) {
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
        if (vdr5.fetchTypeDefinition(c) != null) {
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
    IWorkerContext vd = versions.get(version.toLowerCase());
    if (vd != null) {
      if (uri.contains("#")) {
        String ep = uri.substring(uri.indexOf("#")+1);
        String base = uri.substring(0, uri.indexOf("#"));
        String name = base.substring(44);
        StructureDefinition sd = vd.fetchTypeDefinition(name);
        if (sd != null) {
          ElementDefinition ed = sd.getDifferential().getElementByPath(ep);
          return processVS(vd, ed.getBinding().getValueSet());
        }
      } else if (uri.endsWith("/ValueSet/resource-types")) {
        return listResources(vd.fetchResourcesByType(StructureDefinition.class), VersionUtilities.getMajMin(vd.getVersion()));
      } else if (uri.endsWith("/ValueSet/data-types")) {
        return listDatatypes(vd.fetchResourcesByType(StructureDefinition.class), VersionUtilities.getMajMin(vd.getVersion()));
      } else {
        System.out.println(uri);
      }
    }
    return null;
  }

  private List<Coding> listResources(List<StructureDefinition> structures, String ver) {
    List<Coding> list = new ArrayList<>();
    for (StructureDefinition sd : sortedSDs(structures)) {
      if (sd.getKind() == StructureDefinitionKind.RESOURCE && !sd.getAbstract() && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
        list.add(new Coding("http://hl7.org/fhir/"+ver+"/resource-types", sd.getType(), sd.getType()));
      }
    }
//        if (!types.contains(sd.getType()) && sd.getKind() == StructureDefinitionKind.COMPLEXTYPE && (!sd.getAbstract() || Utilities.existsInList(sd.getName(), "Quantity")) && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
    return list;
  }

  private List<Coding> listDatatypes(List<StructureDefinition> structures, String ver) {
    List<Coding> list = new ArrayList<>();
    for (StructureDefinition sd : sortedSDs(structures)) {
      if ((sd.getKind() == StructureDefinitionKind.COMPLEXTYPE || sd.getKind() == StructureDefinitionKind.PRIMITIVETYPE) && !sd.getAbstract() && !Utilities.existsInList(sd.getType(), "BackboneElement") && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
        list.add(new Coding("http://hl7.org/fhir/"+ver+"/data-types", sd.getType(), sd.getType()));
      }
    }
//        if (!types.contains(sd.getType()) && sd.getKind() == StructureDefinitionKind.COMPLEXTYPE && (!sd.getAbstract() || Utilities.existsInList(sd.getName(), "Quantity")) && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
    return list;
  }

  private List<Coding> processVS(IWorkerContext vd, String url) {
    ValueSet vs = vd.fetchResource(ValueSet.class, url);
    if (vs != null && vs.hasCompose() && !vs.getCompose().hasExclude()) {
      List<Coding> list = new ArrayList<>();
      for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
        if (inc.hasValueSet() || inc.hasFilter()) {
          return null;
        }
        String system = inc.getSystem().replace("http://hl7.org/fhir/", "http://hl7.org/fhir/"+VersionUtilities.getMajMin(vd.getVersion())+"/");
        String vn = VersionUtilities.getNameForVersion(vd.getVersion());
        if (inc.hasConcept()) {
          for (ConceptReferenceComponent cc : inc.getConcept()) {
            list.add(new Coding().setSystem(system).setCode(cc.getCode()).setDisplay(cc.getDisplay()+" ("+vn+")"));
          }
        } else {
          CodeSystem cs = vd.fetchResource(CodeSystem.class, inc.getSystem());
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

  public Map<String, IWorkerContext> getVersions() {
    return versions;
  }

  public Map<String, ConceptMap> getConceptMaps() {
    return conceptMaps;
  }

  public IWorkerContext getVdr2() {
    return vdr2;
  }

  public IWorkerContext getVdr3() {
    return vdr3;
  }

  public IWorkerContext getVdr4() {
    return vdr4;
  }

  public IWorkerContext getVdr4b() {
    return vdr4b;
  }

  public IWorkerContext getVdr5() {
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

  public List<StructureDefinition> getExtensions() {
    return extensions;
  }

  public Map<String, ValueSet> getNewValueSets() {
    return newValueSets;
  }

  public Map<String, CodeSystem> getNewCodeSystems() {
    return newCodeSystems;
  }



}
