package org.hl7.fhir.igtools.openehr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import com.nedap.archie.adlparser.modelconstraints.BMMConstraintImposer;
import com.nedap.archie.rminfo.MetaModels;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.model.Bundle.BundleType;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r5.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r5.model.ElementDefinition.DiscriminatorType;
import org.hl7.fhir.r5.model.ElementDefinition.SlicingRules;
import org.hl7.fhir.r5.model.Enumerations.BindingStrength;
import org.hl7.fhir.r5.model.Enumerations.CodeSystemContentMode;
import org.hl7.fhir.r5.model.Enumerations.FHIRVersion;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.xml.XMLUtil;
import org.openehr.referencemodels.BuiltinReferenceModels;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.nedap.archie.adl14.ADL14ConversionConfiguration;
import com.nedap.archie.adl14.ADL14Parser;
import com.nedap.archie.adlparser.ADLParseException;
import com.nedap.archie.adlparser.ADLParser;
import com.nedap.archie.aom.Archetype;
import com.nedap.archie.aom.ArchetypeSlot;
import com.nedap.archie.aom.CAttribute;
import com.nedap.archie.aom.CComplexObject;
import com.nedap.archie.aom.CComplexObjectProxy;
import com.nedap.archie.aom.CObject;
import com.nedap.archie.aom.ResourceDescriptionItem;
import com.nedap.archie.aom.primitives.CDuration;
import com.nedap.archie.aom.primitives.CInteger;
import com.nedap.archie.aom.primitives.CReal;
import com.nedap.archie.aom.primitives.CString;
import com.nedap.archie.aom.primitives.CTerminologyCode;
import com.nedap.archie.aom.terminology.ArchetypeTerm;
import com.nedap.archie.base.Interval;

public class ArchetypeImporter {

  private IWorkerContext context;
  private String canonicalBase;
  private Element terminology;
  private Map<String, String> atMap = new HashMap<>();

  private Archetype archetype;
  private StructureDefinition sd;
  private Bundle bnd; 
  
  public static class ProcessedArchetype {
    private Archetype archetype;
    private StructureDefinition sd;
    private Bundle bnd;
    private String source;
    private String sourceName;
    
    protected ProcessedArchetype(String source,String sourceName, Archetype archetype, Bundle bnd, StructureDefinition sd) {
      super();
      this.source = source;
      this.sourceName = sourceName;
      this.archetype = archetype;
      this.sd = sd;
      this.bnd = bnd;
    }
    public String getSource() {
      return source;
    }
    public String getSourceName() {
      return sourceName;
    }
    public Archetype getArchetype() {
      return archetype;
    }
    public StructureDefinition getSd() {
      return sd;
    }
    public Bundle getBnd() {
      return bnd;
    } 
  }
  
  public ArchetypeImporter(IWorkerContext context, String canonicalBase) throws ParserConfigurationException, SAXException, IOException {
    super();
    this.context = context;
    this.canonicalBase = canonicalBase;
    // todo: check openehr-base is loaded, and if it's not, load it 
    // todo: load this from the context.binaries
    byte[] tf = context.getBinaryForKey("openehr_terminology.xml");
    if (tf == null) {
      // hack temp workaround
      tf = FileUtilities.fileToBytes("/Users/grahamegrieve/Downloads/openehr_terminology.xml");
    }
    terminology = XMLUtil.parseToDom(tf).getDocumentElement();
  }


  public void checkArchetype(byte[] content, String name) throws ParserConfigurationException, SAXException, IOException, ADLParseException {
    checkArchetype(new ByteArrayInputStream(content), name);
  }

  public void checkArchetype(InputStream stream, String name) throws ParserConfigurationException, SAXException, IOException, ADLParseException {
    byte[] cnt = FileUtilities.streamToBytes(stream);
    try {
      archetype = load20(new ByteArrayInputStream(cnt));
    } catch (Exception e20) {
      try {
        archetype = load14(new ByteArrayInputStream(cnt));
      } catch (Exception e14) {
        if (e20.getMessage().equals(e14.getMessage())) {
          throw new FHIRException("Error reading "+name+": "+e20.getMessage(), e20);
        } else {
          throw new FHIRException("Error reading "+name+". ADL 1.4: "+e14.getMessage()+"; ADL 2: "+e20.getMessage(), e20);          
        }
      }
    }
  }
  
  public ProcessedArchetype importArchetype(byte[] content, String name) throws ParserConfigurationException, SAXException, IOException, ADLParseException {
    return importArchetype(new ByteArrayInputStream(content), name);
  }

  public ProcessedArchetype importArchetype(InputStream stream, String name) throws ParserConfigurationException, SAXException, IOException, ADLParseException {
    atMap.clear();

    byte[] cnt = FileUtilities.streamToBytes(stream);
    try {
      archetype = load20(new ByteArrayInputStream(cnt));
    } catch (Exception e20) {
      try {
        archetype = load14(new ByteArrayInputStream(cnt));
      } catch (Exception e14) {
        if (e20.getMessage().equals(e14.getMessage())) {
          throw new FHIRException("Error reading "+name+": "+e20.getMessage(), e20);
        } else {
          throw new FHIRException("Error reading "+name+". ADL 1.4: "+e14.getMessage()+"; ADL 2: "+e20.getMessage(), e20);          
        }
      }
    }

    bnd = new Bundle();
    bnd.setType(BundleType.COLLECTION);
    bnd.setId(archetype.getArchetypeId().toString().replace("_", "-"));
    
    
    sd = new StructureDefinition();
    sd.setId(archetype.getArchetypeId().toString().replace("_", "-"));
    sd.setUrl(canonicalBase +"/StructureDefinition/"+sd.getId());
    sd.setVersion(archetype.getDescription().getOtherDetails().get("revision"));
    sd.setTitle(archetype.getArchetypeId().toString());
    sd.setName(Utilities.javaTokenize(sd.getId().substring(sd.getId().indexOf(".")+1), true));
    if ("published".equals(archetype.getDescription().getLifecycleState().getCodeString())) {
      sd.setStatus(PublicationStatus.ACTIVE);
    } else {
      System.out.println(archetype.getDescription().getLifecycleState().getCodeString());
      sd.setStatus(PublicationStatus.DRAFT);      
    }
    sd.setExperimental(false);
    sd.setDate(new Date());
    sd.setPublisher(archetype.getDescription().getOtherDetails().get("custodian_organisation"));
    if (archetype.getDescription().getOtherDetails().get("current_contact") != null) {
      String contact = archetype.getDescription().getOtherDetails().get("current_contact");
      if (contact.contains("<")) {
        ContactDetail c = sd.addContact();
        c.setName(contact.substring(0, contact.indexOf("<")));
        c.addTelecom().setSystem(ContactPointSystem.EMAIL).setValue(contact.substring(contact.indexOf("<")+1).replace(">", ""));
        
      } else {
        sd.addContact().setName(contact);
      }
    }
    ResourceDescriptionItem lang = archetype.getDescription().getDetails().get("en");
    if (lang != null) { // todo: handle translations
      sd.setDescription(lang.getUse());
      if (lang.getMisuse() != null) {
        sd.setPurpose(lang.getPurpose()+" Misuse: "+lang.getMisuse());        
      } else {
        sd.setPurpose(lang.getPurpose());
      }
      sd.setCopyright(getCopyright(lang));
      for (String s : lang.getKeywords()) {
        sd.addKeyword().setDisplay(s);
      }
    }
    addResource(sd);
    
    CComplexObject defn = archetype.getDefinition();
    String baseType = defn.getRmTypeName();
    sd.setFhirVersion(FHIRVersion._5_0_0);
    sd.setKind(StructureDefinitionKind.LOGICAL);
    sd.setDerivation(TypeDerivationRule.CONSTRAINT);
    sd.setAbstract(false);
    sd.setType("http://openehr.org/fhir/StructureDefinition/"+baseType);
    sd.setBaseDefinition("http://openehr.org/fhir/StructureDefinition/"+baseType);
    
    List<ElementDefinition> defns = sd.getDifferential().getElement();
    processDefinition(defns, null, defn, baseType, null, baseType, defn.getNodeId());
    
    return new ProcessedArchetype(new String(cnt), name, archetype, bnd, sd);
  }

  private Archetype load20(InputStream stream) throws ADLParseException, IOException {
    ADLParser parser = new ADLParser();
    Archetype archetype = parser.parse(stream);
    return archetype;
  }

  private Archetype load14(InputStream stream) throws ParserConfigurationException, SAXException, IOException, ADLParseException {
    ADL14ConversionConfiguration conversionConfiguration = new ADL14ConversionConfiguration();
//    ADL14Parser parser = new ADL14Parser(BuiltinReferenceModels.getMetaModels());
    MetaModels mms = BuiltinReferenceModels.getMetaModels();
    ADL14Parser adl14Parser = new ADL14Parser(mms);
    Archetype archetype = adl14Parser.parse(stream, conversionConfiguration);
    if (archetype.getRmRelease() == null) { // It is always null in a real-life adl1.4 archetype.
      archetype.setRmRelease("1.1.0"); // Without it the bmm cannot be found - 1.1.0 is required for DV_SCALE support.
    }
    mms.selectModel(archetype);
    BMMConstraintImposer constraintImposer = new BMMConstraintImposer(mms.getSelectedBmmModel());
    constraintImposer.imposeConstraints(archetype.getDefinition()); // After this the archetype has the right existence constraints.
//    Archetype archetype = parser.parse(stream, conversionConfiguration);
    return archetype;
  }
  

  private void addResource(CanonicalResource cr) {
    bnd.addEntry().setResource(cr).setFullUrl(cr.getUrl());
  }

  private String getCopyright(ResourceDescriptionItem lang) {
    String copyright = lang.getCopyright();
    String licence = archetype.getDescription().getOtherDetails().get("licence");
    String ip = archetype.getDescription().getOtherDetails().get("ip_acknowledgements");
    if (ip != null) {
      return copyright+". "+licence+" "+ip;
    } else {
      return copyright+". "+licence;
    }
  }
  
  private void processDefinition(List<ElementDefinition> defns, ElementDefinition parent, CComplexObject source, String path, String sliceName, String id, String label) {
    if (source.getNodeId() != null) {
      atMap.put(source.getNodeId(), id);
    }
    ElementDefinition defn = new ElementDefinition(path);
    defn.setId(id);
    defns.add(defn);
    defn.setSliceName(sliceName);
    buildDefinition(source, defn);

    for (CAttribute a : source.getAttributes()) {
      String name = a.getRmAttributeName();
      if (a.getChildren().size() == 1) {
        CObject o = a.getChildren().get(0);
        if (o instanceof CComplexObject) {
          processDefinition(defns, defn, (CComplexObject) o, path+"."+name, null, id+"."+name, defn.hasLabel() ? defn.getLabel() : label);
        } else if (o instanceof CTerminologyCode) {
          CTerminologyCode c = (CTerminologyCode) o;
          if ("property".equals(name)) {
            if (c.getConstraint().size() == 1 && c.getConstraint().get(0).startsWith("[openehr::")) {
              Coding cc = new Coding("https://specifications.openehr.org/fhir/codesystem-property", c.getConstraint().get(0).substring(10).replace("]", ""), null); 
              defn.addExtension("http://openehr.org/fhir/StructureDefinition/property", cc);
            }
          } else if (bindingGoesOnParent(source.getRmTypeName(), name)) {
            defn.getBinding().setStrength(BindingStrength.REQUIRED);
            defn.getBinding().setValueSet(makeValueSet(label, parent.getShort(), parent.getDefinition(), c));
          } else {
            ElementDefinition ed = new ElementDefinition(path+"."+name);
            ed.setId(id+"."+name);
            defns.add(ed);
            ed.getBinding().setStrength(BindingStrength.REQUIRED);
            ed.getBinding().setValueSet(makeValueSet(label, defn.getShort(), defn.getDefinition(), c));
          }
        } else if (o instanceof CReal) {
          CReal c = (CReal) o;
          if (c.getConstraint().size() == 1) {
            Interval<Double> dbl = c.getConstraint().get(0);
            ElementDefinition ed = new ElementDefinition(path+"."+name);
            ed.setId(id+"."+name);
            defns.add(ed);
            if (dbl.getLower() != null) {
              ed.setMinValue(new DecimalType(dbl.getLower()));
            }
            if (dbl.getUpper() != null) {
              ed.setMaxValue(new DecimalType(dbl.getUpper()));
            }
          } else {
            System.out.println("not done yet: "+path+"."+name+": "+o.getClass().getName());           
          }
        } else if (o instanceof CString) {
          CString c = (CString) o;
          if (c.getConstraint().size() == 1) {
            String v = c.getConstraint().get(0);
            ElementDefinition ed = new ElementDefinition(path+"."+name);
            ed.setId(id+"."+name);
            defns.add(ed);
            ed.setFixed(new StringType(v));
          } else {
            System.out.println("not done yet: "+path+"."+name+": "+o.getClass().getName());           
          }
        } else if (o instanceof CDuration) {
          CDuration c = (CDuration) o;
          if (c.getConstraint().size() == 1) {
            Interval<TemporalAmount> v = c.getConstraint().get(0);
            ElementDefinition ed = new ElementDefinition(path+"."+name);
            ed.setId(id+"."+name);
            defns.add(ed);
            if (v.getLower() != null && v.getUpper() != null && v.getLower().equals(v.getUpper())) {
              ed.setFixed(quantityFromPeriod(v.getLower().toString()));
            } else {
              if (v.getLower() != null) {
                ed.setMinValue(quantityFromPeriod(v.getLower().toString()));
              }
              if (v.getUpper() != null) {
                ed.setMaxValue(quantityFromPeriod(v.getUpper().toString()));
              }
            }
          } else {
            System.out.println("not done yet: "+path+"."+name+": "+o.getClass().getName());           
          }
        } else if (o instanceof CInteger) {
          CInteger c = (CInteger) o;
          if (c.getConstraint().size() == 1) {
            Interval<Long> v = c.getConstraint().get(0);
            ElementDefinition ed = new ElementDefinition(path+"."+name);
            ed.setId(id+"."+name);
            defns.add(ed);
            if (v.getLower() != null && v.getUpper() != null && v.getLower() == v.getUpper()) {
              ed.setFixed(new IntegerType(v.getLower()));              
            } else {
              if (v.getLower() != null) {
                ed.setMinValue(new IntegerType(v.getLower()));
              }
              if (v.getUpper() != null) {
                ed.setMaxValue(new IntegerType(v.getUpper()));
              }
            }
          } else {
            System.out.println("not done yet: "+path+"."+name+": "+o.getClass().getName());           
          }
        } else if (o instanceof CComplexObjectProxy) {
          CComplexObjectProxy c = (CComplexObjectProxy) o;
          ElementDefinition ed = new ElementDefinition(path+"."+name);
          ed.setId(id+"."+name);
          defns.add(ed);
          String[] tp = c.getTargetPath().split("\\/");
          String at = tp[tp.length-1];
          at = at.substring(at.indexOf("[")+1).replace("]", "");
          var ct = ed.addType().setCode(c.getRmTypeName()).addProfileElement();
          ct.setValue(sd.getUrl());
          ct.addExtension(ExtensionDefinitions.EXT_PROFILE_ELEMENT, new StringType(atMap.get(at)));
        } else {
          System.out.println("not done yet: "+path+"."+name+": "+o.getClass().getName());
        }
      } else {
        ElementDefinition slicer = new ElementDefinition(path+"."+name);
        slicer.getSlicing().setRules(SlicingRules.CLOSED);
        boolean typeSlicing = false;
        if (isSingleton(source.getRmTypeName(), name)) {
          slicer.getSlicing().addDiscriminator().setType(DiscriminatorType.TYPE).setPath("$this");
          typeSlicing = true;
        } else {
          slicer.getSlicing().addDiscriminator().setType(DiscriminatorType.PROFILE).setPath("$this");
        }
        defns.add(slicer);
        for (CObject o : a.getChildren()) {
          String sn  = typeSlicing ? o.getRmTypeName(): o.getNodeId();
          if (o instanceof CComplexObject) {
            processDefinition(defns, defn, (CComplexObject) o, path+"."+name, sn, id+"."+name+":"+sn, defn.hasLabel() ? defn.getLabel() : label);
          } else if (o instanceof ArchetypeSlot) {
            ArchetypeSlot c = (ArchetypeSlot) o;
            ElementDefinition ed = new ElementDefinition(path+"."+name);
            ed.setId(id+"."+name+":"+sn);
            ed.setSliceName(sn);
            defns.add(ed);
            buildDefinition(c, ed);
            // ed.getTypeFirstRep().addProfile(canonical+"/StructureDefinition/"+c);
          } else {
            System.out.println("not done yet: "+path+"."+name+": "+o.getClass().getName());
          }
        }
      }
    }
  }

  private DataType quantityFromPeriod(String iso8601Duration) {
    if (iso8601Duration == null || iso8601Duration.isEmpty()) {
      throw new IllegalArgumentException("Duration string cannot be null or empty");
    }

    // Pattern to parse ISO 8601 duration
    Pattern pattern = Pattern.compile(
            "P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?"
    );

    Matcher matcher = pattern.matcher(iso8601Duration);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid ISO 8601 duration format: " + iso8601Duration);
    }

    // Extract components
    String years = matcher.group(1);
    String months = matcher.group(2);
    String weeks = matcher.group(3);
    String days = matcher.group(4);
    String hours = matcher.group(5);
    String minutes = matcher.group(6);
    String seconds = matcher.group(7);

    // Find the most significant (first non-zero) component and return as Quantity
    if (years != null) {
      return new Quantity().setValue(Long.parseLong(years)).setUnit("a").setSystem("http://unitsofmeasure.org").setCode("a");
    }
    if (months != null) {
      return new Quantity().setValue(Long.parseLong(months)).setUnit("mo").setSystem("http://unitsofmeasure.org").setCode("mo");
    }
    if (weeks != null) {
      return new Quantity().setValue(Long.parseLong(weeks)).setUnit("wk").setSystem("http://unitsofmeasure.org").setCode("wk");
    }
    if (days != null) {
      return new Quantity().setValue(Long.parseLong(days)).setUnit("d").setSystem("http://unitsofmeasure.org").setCode("d");
    }
    if (hours != null) {
      return new Quantity().setValue(Long.parseLong(hours)).setUnit("h").setSystem("http://unitsofmeasure.org").setCode("h");
    }
    if (minutes != null) {
      return new Quantity().setValue(Long.parseLong(minutes)).setUnit("min").setSystem("http://unitsofmeasure.org").setCode("min");
    }
    if (seconds != null) {
      return new Quantity().setValue(Double.parseDouble(seconds)).setUnit("s").setSystem("http://unitsofmeasure.org").setCode("s");
    }

    // Default to 0 seconds
    return new Quantity().setValue(0).setUnit("s").setSystem("http://unitsofmeasure.org").setCode("s");
  }

  private boolean bindingGoesOnParent(String rmTypeName, String name) {
    switch (rmTypeName+"."+name) {
    case "DV_CODED_TEXT.defining_code": return true;
    default:
      throw new Error("unknown element "+rmTypeName+"."+name);
    }
  }

  private boolean isSingleton(String rmTypeName, String name) {
    switch (rmTypeName+"."+name) {
    case "HISTORY.events": return false;
    case "ITEM_TREE.items": return false;
    case "CLUSTER.items": return false;
    case "ELEMENT.value": return true;
    case "DV_QUANTITY.magnitude" : return true;
      case "DV_QUANTITY.units" : return true;
      case "DV_QUANTITY.precision" : return true;
    default:
      throw new Error("unknown element "+rmTypeName+"."+name);
    }
  }

  public void buildDefinition(CObject source, ElementDefinition defn) {
    defn.setLabel(source.getNodeId());
    defn.setShort(lookup(source.getNodeId()));
    defn.setDefinition(lookupDesc(source.getNodeId()));
    if (source.getRmTypeName().contains("<")) {
      String base = source.getRmTypeName().substring(0, source.getRmTypeName().indexOf("<"));
      String param = source.getRmTypeName().substring(source.getRmTypeName().indexOf("<") + 1, source.getRmTypeName().length()-1);
      ElementDefinition.TypeRefComponent tr = defn.addType();
      tr.setCode("http://openehr.org/fhir/StructureDefinition/" + base);
      tr.addExtension("http://hl7.org/fhir/tools/StructureDefinition/type-parameter", new CodeType(param));
    } else {
      defn.addType().setCode("http://openehr.org/fhir/StructureDefinition/" + source.getRmTypeName());
    }
    if (source.getOccurrences() != null) {
      if (source.getOccurrences().isLowerIncluded()) {
        defn.setMin(source.getOccurrences().getLower());
      }
      if (source.getOccurrences().isUpperIncluded()) {
        if (source.getOccurrences().isUpperUnbounded()) {
          defn.setMax("*");
        } else {
          defn.setMax(source.getOccurrences().getUpper().toString());
        }
      }
    }
  }

  private String makeValueSet(String label, String text, String desc, CTerminologyCode c) {
    String id = sd.getId()+"."+label;
    String url = canonicalBase+"/ValueSet/"+id;
    
    ValueSet vs = new ValueSet();
    vs.setIdBase(id);
    vs.setUrl(url);
    vs.setName(Utilities.javaTokenize(text, true)+"VS");
    vs.setTitle(text+" ValueSet");
    vs.setStatus(sd.getStatus());
    vs.setDate(sd.getDate());
    vs.setVersion(sd.getVersion());
    vs.setDescription("ValueSet for "+desc);
    addResource(vs);
    
    CodeSystem cs = null;
    
    Map<String, ConceptSetComponent> incs = new HashMap<String, ValueSet.ConceptSetComponent>();
    for (String s : c.getConstraint()) {
      String code = s;
      ConceptSetComponent inc;
      if (s.contains("::")) {
        String sys = s.substring(0, s.indexOf("::")).replace("[", "");
        code = s.substring(s.indexOf("::")+2).replace("]", "");
        if ("openehr".equals(sys)) {
          String system = getSystemForConcept(code);
          inc = incs.get(system);
          if (inc == null) {
            inc = vs.getCompose().addInclude();
            inc.setSystem(system);
            incs.put(system,  inc);
          }
        } else {
          throw new Error("Not handled yet");
        }
      } else {
        if (cs == null) {
          cs = new CodeSystem();
          cs.setIdBase(id);
          cs.setUrl(canonicalBase+"/CodeSystem/"+id);
          cs.setStatus(sd.getStatus());
          cs.setName(Utilities.javaTokenize(text, true)+"CS");
          cs.setTitle(text);

          cs.setDate(sd.getDate());
          cs.setVersion(sd.getVersion());
          cs.setDescription("CodeSystem for "+desc);
          cs.setContent(CodeSystemContentMode.COMPLETE);
          cs.setCaseSensitive(true);
          
          addResource(cs);
        }
        inc = incs.get("");
        if (inc == null) {
          inc = vs.getCompose().addInclude();
          inc.setSystem(cs.getUrl());
          incs.put("",  inc);
        }
        ConceptDefinitionComponent cd = cs.addConcept();
        cd.setCode(s);
        cd.setDisplay(lookup(s));
        cd.setDefinition(lookupDesc(s));
      }
      inc.addConcept().setCode(code);
    }
    return vs.getUrl();
  }

  private String getSystemForConcept(String code) {
    for (Element group : XMLUtil.getNamedChildren(terminology, "group")) {
      for (Element concept : XMLUtil.getNamedChildren(group, "concept")) {
        if (code.equals(concept.getAttribute("id"))) {
          return "https://specifications.openehr.org/fhir/codesystem-"+group.getAttribute("openehr_id");
        }
      }
    }
    throw new Error("unknown openEHR code "+code);
  }


  private String lookup(String nodeId) {
    ArchetypeTerm td = archetype.getTerminology().getTermDefinition("en", nodeId);
    return td == null ? null : td.getText();
  }

  private String lookupDesc(String nodeId) {
    ArchetypeTerm td = archetype.getTerminology().getTermDefinition("en", nodeId);
    return td == null ? null : td.getDescription();
  }

}
