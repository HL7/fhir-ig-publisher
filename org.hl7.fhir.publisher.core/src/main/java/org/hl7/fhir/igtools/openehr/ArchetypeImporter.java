package org.hl7.fhir.igtools.openehr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import com.nedap.archie.adlparser.modelconstraints.BMMConstraintImposer;
import com.nedap.archie.rminfo.MetaModelProvider;
import com.nedap.archie.rminfo.ModelNotFoundException;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.model.extensions.ExtensionDefinitions;
import org.hl7.fhir.model.core.BooleanType;
import org.hl7.fhir.model.core.Bundle;
import org.hl7.fhir.model.core.Bundle.BundleEntryComponent;
import org.hl7.fhir.model.core.Bundle.BundleType;
import org.hl7.fhir.model.core.CanonicalResource;
import org.hl7.fhir.model.core.CodeSystem;
import org.hl7.fhir.model.core.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.model.core.CodeType;
import org.hl7.fhir.model.core.Coding;
import org.hl7.fhir.model.core.ContactDetail;
import org.hl7.fhir.model.core.ContactPoint.ContactPointSystem;
import org.hl7.fhir.model.core.DataType;
import org.hl7.fhir.model.core.DateTimeType;
import org.hl7.fhir.model.core.DateType;
import org.hl7.fhir.model.core.DecimalType;
import org.hl7.fhir.model.core.Duration;
import org.hl7.fhir.model.core.ElementDefinition;
import org.hl7.fhir.model.core.ElementDefinition.ConstraintSeverity;
import org.hl7.fhir.model.core.ElementDefinition.DiscriminatorType;
import org.hl7.fhir.model.core.ElementDefinition.ElementDefinitionConstraintComponent;
import org.hl7.fhir.model.core.ElementDefinition.SlicingRules;
import org.hl7.fhir.model.core.Enumerations.BindingStrength;
import org.hl7.fhir.model.core.Enumerations.CodeSystemContentMode;
import org.hl7.fhir.model.core.Enumerations.FHIRVersion;
import org.hl7.fhir.model.core.Enumerations.PublicationStatus;
import org.hl7.fhir.model.core.IntegerType;
import org.hl7.fhir.model.core.StringType;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.model.core.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.model.core.ValueSet;
import org.hl7.fhir.model.core.ValueSet.ConceptSetComponent;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.xml.XMLUtil;
import org.openehr.bmm.core.BmmModel;
import org.openehr.referencemodels.BuiltinReferenceModels;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.nedap.archie.adl14.ADL14ConversionConfiguration;
import com.nedap.archie.adl14.ADL14Parser;
import com.nedap.archie.adl14.aom14.CTerminologyCodeADL14;
import com.nedap.archie.adlparser.ADLParseException;
import com.nedap.archie.adlparser.ADLParser;
import com.nedap.archie.aom.Archetype;
import com.nedap.archie.aom.ArchetypeSlot;
import com.nedap.archie.aom.CAttribute;
import com.nedap.archie.aom.CComplexObject;
import com.nedap.archie.aom.CComplexObjectProxy;
import com.nedap.archie.aom.CObject;
import com.nedap.archie.aom.ResourceDescriptionItem;
import com.nedap.archie.aom.primitives.CBoolean;
import com.nedap.archie.aom.primitives.CDate;
import com.nedap.archie.aom.primitives.CDateTime;
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
  
  private MetaModelProvider metaModelProvider;
  
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
    metaModelProvider = BuiltinReferenceModels.getMetaModelProvider();
  }


  public void checkArchetype(byte[] content, String name, String contentType) throws ParserConfigurationException, SAXException, IOException, ADLParseException {
    checkArchetype(new ByteArrayInputStream(content), name, contentType);
  }

  public void checkArchetype(InputStream stream, String name, String contentType) throws ParserConfigurationException, SAXException, IOException, ADLParseException {
    byte[] cnt = FileUtilities.streamToBytes(stream);
    if("adl".equals(contentType)) {
      try {
        archetype = load14(new ByteArrayInputStream(cnt));
      } catch (Exception e14) {
        throw new FHIRException("Error reading "+name+". ADL 1.4: "+e14.getMessage());
      }
    } else if ("adls".equals(contentType)) {
      try {
        archetype = load20(new ByteArrayInputStream(cnt));
      } catch (Exception e20) {
        throw new FHIRException("Error reading "+name+": "+e20.getMessage(), e20);
      }
    } else {
      throw new FHIRException("Unknown file type for "+name);
    }
  }
  
  public ProcessedArchetype importArchetype(byte[] content, String name, String contentType) throws ParserConfigurationException, SAXException, IOException, ADLParseException {
    return importArchetype(new ByteArrayInputStream(content), name, contentType);
  }

  public ProcessedArchetype importArchetype(InputStream stream, String adlName, String contentType) throws ParserConfigurationException, SAXException, IOException, ADLParseException {
    atMap.clear();
    byte[] cnt = FileUtilities.streamToBytes(stream);
    if("adl".equals(contentType)) {
      try {
        archetype = load14(new ByteArrayInputStream(cnt));
      } catch (Exception e14) {
        throw new FHIRException("Error reading "+adlName+". ADL 1.4: "+e14.getMessage());
      }
    } else if ("adls".equals(contentType)) {
      try {
        archetype = load20(new ByteArrayInputStream(cnt));
      } catch (Exception e20) {
        throw new FHIRException("Error reading "+adlName+" ADL 2.0: "+e20.getMessage(), e20);
      }
    } else {
      throw new FHIRException("Unknown file type for "+adlName);
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
    if ("published".equals(archetype.getDescription().getLifecycleState())) {
      sd.setStatus(PublicationStatus.ACTIVE);
    } else {
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
      if(lang.getKeywords()!=null) {
        for (String s : lang.getKeywords()) {
          sd.addKeyword().setDisplay(s);
        }
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
    
    List<ElementDefinition> defns = sd.getDifferential().getElementList();
    processDefinition(defns, null, defn, baseType, null, baseType, defn.getNodeId(), adlName);
    
    return new ProcessedArchetype(new String(cnt), adlName, archetype, bnd, sd);
  }

  protected Archetype load20(InputStream stream) throws ADLParseException, IOException {
    ADLParser parser = new ADLParser();
    Archetype archetype = parser.parse(stream);
    return archetype;
  }

  protected Archetype load14(InputStream stream) throws ParserConfigurationException, SAXException, IOException, ADLParseException {
    Archetype archetype = null;
    try {
      ADL14ConversionConfiguration conversionConfiguration = new ADL14ConversionConfiguration();
      ADL14Parser adl14Parser = new ADL14Parser(metaModelProvider);
      archetype = adl14Parser.parse(stream, conversionConfiguration);
      // If the archetype doesn't have an rm_release, we need to use 1.1.0 to get the BMM model
      BmmModel bmmm = metaModelProvider.getMetaModel(archetype).getBmmModel();
      if(bmmm == null) {
        bmmm = metaModelProvider.getMetaModel(archetype, "1.1.0").getBmmModel();
      }
      BMMConstraintImposer constraintImposer = new BMMConstraintImposer(bmmm);
      constraintImposer.imposeConstraints(archetype.getDefinition());
    } catch (Exception e) {
      if (e instanceof ModelNotFoundException && //
        ((ModelNotFoundException) e).getMessage().contains("openEHR.EHR version null not found")) {
        System.out.println("Error parsing ADL 1.4 archetype: missing attribute rm_release");
      }
      throw e;
    }
    return archetype;
  }
  
  private CanonicalResource checkResource(String url) {
    Optional<BundleEntryComponent> found = bnd.getEntryList().stream().filter(e -> e.getFullUrl().equals(url)).findAny();
    if(found.isPresent()) {
      return (CanonicalResource) found.get().getResource();
    } else {
      return null;
    }
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
  
  private void processDefinition(List<ElementDefinition> defns, ElementDefinition parent, CComplexObject source, String path, String sliceName, String id, String label, String adlName) {
    if (source.getNodeId() != null) {
      atMap.put(source.getNodeId(), id);
    }
    ElementDefinition defn = new ElementDefinition(context.getModelContext(), path);
    defn.setId(id);
    defns.add(defn);
    defn.setSliceName(sliceName);
    buildDefinition(source, defn);

    for (CAttribute a : source.getAttributes()) {
      String name = a.getRmAttributeName();
      if (a.getChildren().size() == 1) {
        CObject o = a.getChildren().get(0);
        if (o instanceof CComplexObject) {
          processDefinition(defns, defn, (CComplexObject) o, path+"."+name, null, id+"."+name, defn.hasLabel() ? defn.getLabel() : label, adlName);
        } else if (o instanceof CTerminologyCode) {
          mapCTerminologyCode(defns, path, name, id, null, (CTerminologyCode) o, defn, parent, label, source);
        } else if (o instanceof CTerminologyCodeADL14) {
          mapCTerminologyCodeADL14(defns, path, name, id, null, (CTerminologyCodeADL14) o, defn, parent, label, source, adlName);
        } else if (o instanceof CReal) {
          mapCReal(defns, path, name, id, null, (CReal) o, adlName);
        } else if (o instanceof CString) {
          mapCString(defns, path, name, id, null, (CString) o, adlName);
        } else if (o instanceof CDate) {
          mapCDate(defns, path, name, id, null, (CDate) o, adlName);
        } else if (o instanceof CDateTime) {
          mapCDateTime(defns, path, name, id, null, (CDateTime) o, adlName);
        } else if (o instanceof CDuration) {
          mapCDuration(defns, path, name, id, null, (CDuration) o, adlName);
        } else if (o instanceof CInteger) {
          mapCInteger(defns, path, name, id, null, (CInteger) o, adlName);
        } else if (o instanceof CBoolean) {
          mapCBoolean(defns, path, name, id, null, (CBoolean) o, adlName);
        }else if (o instanceof CComplexObjectProxy) {
          CComplexObjectProxy c = (CComplexObjectProxy) o;
          ElementDefinition ed = new ElementDefinition(context.getModelContext(), path+"."+name);
          ed.setId(id+"."+name);
          defns.add(ed);
          String[] tp = c.getTargetPath().split("\\/");
          String at = tp[tp.length-1];
          at = at.substring(at.indexOf("[")+1).replace("]", "");
          var ct = ed.addType().setCode(c.getRmTypeName()).addProfileElement();
          ct.setValue(sd.getUrl());
          ct.addExtension(ExtensionDefinitions.EXT_PROFILE_ELEMENT, new StringType(atMap.get(at)));
        } else if (o instanceof ArchetypeSlot) { 
//          mapArchetypeSlot(defns, path, name, id, null, (ArchetypeSlot) o);
        } else {
          System.out.println("not done yet "+o.getClass().getSimpleName()+" ("+o.getRmTypeName()+"): "+path+"."+name+" in file: "+adlName);
        }
      } else {
        ElementDefinition slicer = new ElementDefinition(context.getModelContext(), path+"."+name);
        slicer.getSlicing().setRules(SlicingRules.CLOSED);
        if (isSingleton(source.getRmTypeName(), name)) {
          slicer.getSlicing().addDiscriminator().setType(DiscriminatorType.TYPE).setPath("$this");
        } else {
          slicer.getSlicing().addDiscriminator().setType(DiscriminatorType.PROFILE).setPath("$this");
        }
        defns.add(slicer);
        for (CObject o : a.getChildren()) {
          String sn  = o.getNodeId();
          if (o instanceof CComplexObject) {
            processDefinition(defns, defn, (CComplexObject) o, path+"."+name, sn, id+"."+name+":"+sn, defn.hasLabel() ? defn.getLabel() : label, adlName);
          } else if (o instanceof CString) { 
            mapCString(defns, path, name, id, sn, (CString) o, adlName);
          } else if (o instanceof CReal) {
            mapCReal(defns, path, name, id, sn, (CReal) o, adlName);
          }  else if (o instanceof CInteger) {
            mapCInteger(defns, path, name, id, sn, (CInteger) o, adlName);
          } else if (o instanceof CBoolean) {
            mapCBoolean(defns, path, name, id, sn, (CBoolean) o, adlName);
          } else if (o instanceof CDate) {
            mapCDate(defns, path, name, id, null, (CDate) o, adlName);
          } else if (o instanceof CDateTime) {
            mapCDateTime(defns, path, name, id, sn, (CDateTime) o, adlName);
          } else if (o instanceof CDuration) {
            mapCDuration(defns, path, name, id, sn, (CDuration) o, adlName);
          } else if (o instanceof CTerminologyCode) {
            mapCTerminologyCode(defns, path, name, id, sn, (CTerminologyCode) o, defn, parent, label, source);
          } else if (o instanceof CTerminologyCodeADL14) {
            mapCTerminologyCodeADL14(defns, path, name, id, sn, (CTerminologyCodeADL14) o, defn, parent, label, source, adlName);
          } else if (o instanceof ArchetypeSlot) {
            mapArchetypeSlot(defns, path, name, id, sn, (ArchetypeSlot) o);
          } else {
            System.out.println("not done yet "+o.getClass().getSimpleName()+" ("+o.getRmTypeName()+"): "+path+"."+name+" in file: "+adlName);
          }
        }
      }
    }
  }

  private void mapArchetypeSlot(List<ElementDefinition> defns, String path, String name, String id, String sn, ArchetypeSlot o) {
    ArchetypeSlot c = (ArchetypeSlot) o;
    ElementDefinition ed = new ElementDefinition(context.getModelContext(), path+"."+name);
    if(StringUtils.isNotEmpty(sn)) {
      ed.setId(id+"."+name+":"+sn);
      ed.setSliceName(sn);
    }else {
      ed.setId(id+"."+name);
    }
    defns.add(ed);
    buildDefinition(c, ed);
    // ed.getTypeFirstRep().addProfile(canonical+"/StructureDefinition/"+c);
  }

  private void mapCTerminologyCodeADL14(List<ElementDefinition> defns, String path, String name, String id, String sn, CTerminologyCodeADL14 c,
      ElementDefinition defn, ElementDefinition parent, String label, CComplexObject source, String adlName) {
    if ("property".equals(name)) {
      if (c.getConstraint() != null && c.getConstraint().size() == 1 && c.getConstraint().get(0).startsWith("[openehr::")) {
        Coding cc = new Coding("https://specifications.openehr.org/fhir/codesystem-property", c.getConstraint().get(0).substring(10).replace("]", ""), null); 
        defn.addExtension("http://openehr.org/fhir/StructureDefinition/property", cc);
      }else {
        System.out.println("not done yet "+c.getClass().getSimpleName()+": "+path+"."+name+": Constraints "+c.getConstraint());
      }
    }
    if (c.getConstraint()!= null) {
      if (bindingGoesOnParent(source.getRmTypeName(), name)) {
        defn.getBinding().setStrength(BindingStrength.REQUIRED);
        defn.getBinding().setValueSet(makeValueSet(label, parent.getShort(), parent.getDefinition(), c));
      } else {
        ElementDefinition ed = new ElementDefinition(context.getModelContext(), path+"."+name);
        if(StringUtils.isNotEmpty(sn)) {
          ed.setId(id+"."+name+":"+sn);
          ed.setSliceName(sn);
        }else {
          ed.setId(id+"."+name);
        }
        defns.add(ed);
        ed.getBinding().setStrength(BindingStrength.REQUIRED);
        ed.getBinding().setValueSet(makeValueSet(label, defn.getShort(), defn.getDefinition(), c));
      }
    } else {
      System.out.println("not done yet "+c.getClass().getSimpleName()+" ("+c.getRmTypeName()+"): "+path+"."+name+",  Constraints "+c.getConstraint()+" in file: "+adlName);
    }
  }

  private void mapCTerminologyCode(List<ElementDefinition> defns, String path, String name, String id, String sn, CTerminologyCode c,
      ElementDefinition defn, ElementDefinition parent, String label, CComplexObject source) {
    if ("property".equals(name)) {
      if (c.getConstraint() != null && c.getConstraint().startsWith("[openehr::")) {
        Coding cc = new Coding("https://specifications.openehr.org/fhir/codesystem-property", c.getConstraint().substring(10).replace("]", ""), null); 
        defn.addExtension("http://openehr.org/fhir/StructureDefinition/property", cc);
      }
    } else if (bindingGoesOnParent(source.getRmTypeName(), name)) {
      defn.getBinding().setStrength(BindingStrength.REQUIRED);
      defn.getBinding().setValueSet(makeValueSet(label, parent.getShort(), parent.getDefinition(), c));
    } else {
      ElementDefinition ed = new ElementDefinition(context.getModelContext(), path+"."+name);
      if(StringUtils.isNotEmpty(sn)) {
        ed.setId(id+"."+name+":"+sn);
        ed.setSliceName(sn);
      }else {
        ed.setId(id+"."+name);
      }
      defns.add(ed);
      ed.getBinding().setStrength(BindingStrength.REQUIRED);
      ed.getBinding().setValueSet(makeValueSet(label, defn.getShort(), defn.getDefinition(), c));
    }
  }

  private void mapCDuration(List<ElementDefinition> defns, String path, String name, String id, String sn, CDuration c, String adlName) {
    if (c.getConstraint().size() == 1) {
      Interval<TemporalAmount> v = c.getConstraint().get(0);
      ElementDefinition ed = new ElementDefinition(context.getModelContext(), path+"."+name);
      if(StringUtils.isNotEmpty(sn)) {
        ed.setId(id+"."+name+":"+sn);
        ed.setSliceName(sn);
      }else {
        ed.setId(id+"."+name);
      }
      defns.add(ed);
      if (v.getLower() != null && v.getUpper() != null && v.getLower().equals(v.getUpper())) {
        ed.setFixed(ArchetypeImportHelper.durationFromPeriod(v.getLower().toString()));
      } else {
        if (v.getLower() != null) {
          ed.setMinValue(ArchetypeImportHelper.durationFromPeriod(v.getLower().toString()));
        }
        if (v.getUpper() != null) {
          ed.setMaxValue(ArchetypeImportHelper.durationFromPeriod(v.getUpper().toString()));
        }
      }
    } else {
      System.out.println("not done yet "+c.getClass().getSimpleName()+" ("+c.getRmTypeName()+"): "+path+"."+name+",  Constraints "+c.getConstraint() +", Pattern Constraints "+c.getPatternConstraint()+" in file: "+adlName);
    }
  }

  private void mapCBoolean(List<ElementDefinition> defns, String path, String name, String id, String sn, CBoolean c, String adlName) {
    if(c.getConstraint().size() == 1) {
      Boolean v = c.getConstraint().get(0);
      ElementDefinition ed = new ElementDefinition(context.getModelContext(), path+"."+name);
      if(StringUtils.isNotEmpty(sn)) {
        ed.setId(id+"."+name+":"+sn);
        ed.setSliceName(sn);
      }else {
        ed.setId(id+"."+name);
      }
      defns.add(ed);
      ed.setFixed(new BooleanType(v));
    } else {
      System.out.println("not done yet "+c.getClass().getSimpleName()+" ("+c.getRmTypeName()+"): "+path+"."+name+",  Constraints "+c.getConstraint()+" in file: "+adlName);
    }
  }

  private void mapCDateTime(List<ElementDefinition> defns, String path, String name, String id, String sn, CDateTime c, String adlName) {
    ElementDefinition ed = new ElementDefinition(context.getModelContext(), path+"."+name);
    if(StringUtils.isNotEmpty(sn)) {
      ed.setId(id+"."+name+":"+sn);
      ed.setSliceName(sn);
    }else {
      ed.setId(id+"."+name);
    }
    defns.add(ed);
    
    if (c.getConstraint().size() == 1) {
      Interval<TemporalAccessor> v = c.getConstraint().get(0);
      
      if (v.getLower() != null && v.getUpper() != null && v.getLower().equals(v.getUpper())) {
        ed.setFixed(ArchetypeImportHelper.dateTimeFromString(v.getLower().toString()));
      } else {
        if (v.getLower() != null) {
          ed.setMinValue(ArchetypeImportHelper.dateTimeFromString(v.getLower().toString()));
        }
        if (v.getUpper() != null) {
          ed.setMaxValue(ArchetypeImportHelper.dateTimeFromString(v.getUpper().toString()));
        }
      }
      
    } else {
      System.out.println("not done yet "+c.getClass().getSimpleName()+" ("+c.getRmTypeName()+"): "+path+"."+name+",  Constraints "+c.getConstraint() +", Pattern Constraints "+c.getPatternConstraint()+" in file: "+adlName);
    }
    if(c.getPatternConstraint() != null) {
      ed.addConstraint(ArchetypeImportHelper.createDateTimeConstraint(name, id, sn, c.getPatternConstraint()));
    }
    
    buildDefinition(c, ed);
  }




  private void mapCDate(List<ElementDefinition> defns, String path, String name, String id, String sn, CDate c, String adlName) {
    ElementDefinition ed = new ElementDefinition(context.getModelContext(), path+"."+name);
    if(StringUtils.isNotEmpty(sn)) {
      ed.setId(id+"."+name+":"+sn);
      ed.setSliceName(sn);
    }else {
      ed.setId(id+"."+name);
    }
    defns.add(ed);
    
    if (c.getConstraint().size() == 1) {
       Interval<Temporal> v = c.getConstraint().get(0);

       if (v.getLower() != null && v.getUpper() != null && v.getLower().equals(v.getUpper())) {
         ed.setFixed(ArchetypeImportHelper.dateFromString(v.getLower().toString()));
       } else {
         if (v.getLower() != null) {
           ed.setMinValue(ArchetypeImportHelper.dateFromString(v.getLower().toString()));
         }
         if (v.getUpper() != null) {
           ed.setMaxValue(ArchetypeImportHelper.dateFromString(v.getUpper().toString()));
         }
       }
    } else {
      System.out.println("not done yet "+c.getClass().getSimpleName()+" ("+c.getRmTypeName()+"): "+path+"."+name+",  Constraints "+c.getConstraint() +", Pattern Constraints "+c.getPatternConstraint()+" in file: "+adlName);
    }
    if(c.getPatternConstraint() != null) {
      ed.addConstraint(ArchetypeImportHelper.createDateConstraint(name, id, sn, c.getPatternConstraint()));
    }
    
    buildDefinition(c, ed);
  }

  

  private void mapCInteger(List<ElementDefinition> defns, String path, String name, String id, String sn, CInteger c, String adlName) {
    if (c.getConstraint().size() == 1) {
      Interval<Long> v = c.getConstraint().get(0);
      ElementDefinition ed = new ElementDefinition(context.getModelContext(), path+"."+name);
      if(StringUtils.isNotEmpty(sn)) {
        ed.setId(id+"."+name+":"+sn);
        ed.setSliceName(sn);
      }else {
        ed.setId(id+"."+name);
      }
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
      System.out.println("not done yet "+c.getClass().getSimpleName()+" ("+c.getRmTypeName()+"): "+path+"."+name+",  Constraints "+c.getConstraint()+" in file: "+adlName);
    }
  }

  private void mapCString(List<ElementDefinition> defns, String path, String name, String id, String sn, CString c, String adlName) {
    if (c.getConstraint().size() == 1) {
      String v = c.getConstraint().get(0);
      ElementDefinition ed = new ElementDefinition(context.getModelContext(), path+"."+name);
      if(StringUtils.isNotEmpty(sn)) {
        ed.setId(id+"."+name+":"+sn);
        ed.setSliceName(sn);
      }else {
        ed.setId(id+"."+name);
      }
      defns.add(ed);
      ed.setFixed(new StringType(v));
    } else {
      System.out.println("not done yet "+c.getClass().getSimpleName()+" ("+c.getRmTypeName()+"): "+path+"."+name+",  Constraints "+c.getConstraint()+" in file: "+adlName);
    }
  }

  private void mapCReal(List<ElementDefinition> defns, String path, String name, String id, String sn, CReal c, String adlName) {
    if (c.getConstraint().size() == 1) {
      Interval<Double> dbl = c.getConstraint().get(0);
      ElementDefinition ed = new ElementDefinition(context.getModelContext(), path+"."+name);
      if(StringUtils.isNotEmpty(sn)) {
        ed.setId(id+"."+name+":"+sn);
        ed.setSliceName(sn);
      }else {
        ed.setId(id+"."+name);
      }
      defns.add(ed);
      if (dbl.getLower() != null) {
        ed.setMinValue(new DecimalType(dbl.getLower()));
      }
      if (dbl.getUpper() != null) {
        ed.setMaxValue(new DecimalType(dbl.getUpper()));
      }
    } else {
      System.out.println("not done yet "+c.getClass().getSimpleName()+" ("+c.getRmTypeName()+"): "+path+"."+name+",  Constraints "+c.getConstraint()+" in file: "+adlName);
    }
  }
  
  

  private boolean bindingGoesOnParent(String rmTypeName, String name) {
    switch (rmTypeName+"."+name) {
    case "DV_CODED_TEXT.defining_code": return true;
    case "DV_MULTIMEDIA.media_type": return true;
    case "DV_ORDINAL.symbol": return true;
    case "DV_QUANTITY.property": return true;
    case "CLUSTER.defining_code": return false;
    default:
      throw new Error("unknown element "+rmTypeName+"."+name);
    }
  }

  private boolean isSingleton(String rmTypeName, String name) {
    StructureDefinition type = context.fetchTypeDefinition("http://openehr.org/fhir/StructureDefinition/" + rmTypeName);
    if (type == null) throw new Error("unknown type " + rmTypeName);

    Optional<ElementDefinition> child = type.getSnapshot().getElementList().stream().filter(e -> e.getId().equals(rmTypeName+"."+name.replace("_","-"))).findAny();
    if(child.isEmpty()) throw new Error("unknown element " + rmTypeName + "." + name);
    
    return child.get().getMaxAsInt()==1;
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

  private String makeValueSet(String label, String text, String desc, CTerminologyCodeADL14 c) {
    String id = sd.getId()+"."+label;
    String url = canonicalBase+"/ValueSet/"+id;
    
    CanonicalResource resource = checkResource(url);
    ValueSet vs = null;
    if(resource != null) {
      vs = (ValueSet) resource;
    }else {
      vs = new ValueSet();
      vs.setIdBase(id);
      vs.setUrl(url);
      vs.setName(Utilities.javaTokenize(text, true)+"VS");
      vs.setTitle(text+" ValueSet");
      vs.setStatus(sd.getStatus());
      vs.setDate(sd.getDate());
      vs.setVersion(sd.getVersion());
      vs.setDescription("ValueSet for "+desc);
      addResource(vs);
    }
    
    CodeSystem cs = null;
    
    Map<String, ConceptSetComponent> incs = new HashMap<String, ValueSet.ConceptSetComponent>();
    boolean hasOpenEHR = false;
    for (String s : c.getConstraint()) {
      if(s.equals(label)) {
        continue;
      }
      if("openehr".equals(s)) {
        hasOpenEHR = true;
        continue;
      }
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
      } else if (hasOpenEHR) {
        String system = getSystemForConcept(code);
        inc = incs.get(system);
        if (inc == null) {
          inc = vs.getCompose().addInclude();
          inc.setSystem(system);
          incs.put(system,  inc);
        }
      } else {
        
        CanonicalResource csresource = checkResource(canonicalBase+"/CodeSystem/"+id);
        if(csresource!=null) {
          cs = (CodeSystem) csresource;
        } else{
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


  private String makeValueSet(String label, String text, String desc, CTerminologyCode c) {
    String id = sd.getId()+"."+label;
    String url = canonicalBase+"/ValueSet/"+id;
    
    CanonicalResource resource = checkResource(url);
    ValueSet vs = null;
    if(resource != null) {
      vs = (ValueSet) resource;
    }else {
      vs = new ValueSet();
      vs.setIdBase(id);
      vs.setUrl(url);
      vs.setName(Utilities.javaTokenize(text, true)+"VS");
      vs.setTitle(text+" ValueSet");
      vs.setStatus(sd.getStatus());
      vs.setDate(sd.getDate());
      vs.setVersion(sd.getVersion());
      vs.setDescription("ValueSet for "+desc);
      addResource(vs);
    }
    
    CodeSystem cs = null;
    
    Map<String, ConceptSetComponent> incs = new HashMap<String, ValueSet.ConceptSetComponent>();
    //for (String s : c.getConstraint()) {
      String s = c.getConstraint();
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
        CanonicalResource csresource = checkResource(canonicalBase+"/CodeSystem/"+id);
        if(csresource!=null) {
          cs = (CodeSystem) csresource;
        } else{
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
    //}
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
