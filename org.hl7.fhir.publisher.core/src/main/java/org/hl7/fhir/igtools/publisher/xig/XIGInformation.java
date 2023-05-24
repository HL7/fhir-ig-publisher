package org.hl7.fhir.igtools.publisher.xig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CapabilityStatement;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.NamingSystem;
import org.hl7.fhir.r5.model.OperationDefinition;
import org.hl7.fhir.r5.model.SearchParameter;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;

public class XIGInformation {

  public class CanonicalResourceUsage {
    private CanonicalResource resource;
    private UsageType usage;
    public CanonicalResourceUsage(CanonicalResource resource, UsageType usage) {
      super();
      this.resource = resource;
      this.usage = usage;
    }
    public CanonicalResource getResource() {
      return resource;
    }
    public UsageType getUsage() {
      return usage;
    }
    
  }
  public enum UsageType {
    CS_IMPORTS, VS_SYSTEM, VS_VALUESET, VS_EXPANSION, DERIVATION, SP_PROFILE, OP_PROFILE, TARGET, BINDING, CM_SCOPE, CM_MAP, CM_UNMAP, CS_VALUESET, CS_SUPPLEMENTS, CS_PROFILE, SD_PROFILE;

    public String getDisplay() {
      switch (this) {
      case BINDING: return " (binding)";
      case CM_MAP: return " (used in map)";
      case CM_SCOPE: return " (map scope)";
      case CM_UNMAP: return " (map when unmapped)";
      case CS_IMPORTS: return " (depends on)";
      case CS_PROFILE: return " (profile)";
      case CS_SUPPLEMENTS: return " (supplements)";
      case CS_VALUESET: return " (all codes valueset)";
      case DERIVATION: return " (derives from)";
      case OP_PROFILE: return " (profile)";
      case SD_PROFILE: return " (profile)";
      case SP_PROFILE: return " (profile)";
      case TARGET: return " (target profile)";
      case VS_EXPANSION: return " (expansion)";
      case VS_SYSTEM: return " (system)";
      case VS_VALUESET: return " (valueset)";
      }
      return "";
    }

  }
  Map<String, String> pid = new HashMap<>();
  Map<String, Map<String, CanonicalResource>> counts = new HashMap<>();
  Map<String, CanonicalResource> resources = new HashMap<>();
  private Set<String> opr = new HashSet<>();
  private Set<String> spr = new HashSet<>();
  private Set<String> nspr = new HashSet<>();
  private JsonObject json = new JsonObject();
  private Set<String> jurisdictions = new HashSet<>();
  private SimpleWorkerContext ctxt;
  Map<String, List<CanonicalResourceUsage>> usages = new HashMap<>();
  private XIGExtensionHandler extensionHandler = new XIGExtensionHandler();
  private List<String> sdErrors = new ArrayList<>();
  
  public Map<String, String> getPid() {
    return pid;
  }
  public Map<String, Map<String, CanonicalResource>> getCounts() {
    return counts;
  }
  public Map<String, CanonicalResource> getResources() {
    return resources;
  }

  public Set<String> getOpr() {
    return opr;
  }
  public Set<String> getSpr() {
    return spr;
  }
  public JsonObject getJson() {
    return json;
  }
  public void setJson(JsonObject json) {
    this.json = json;
  }

  public Map<String, List<CanonicalResourceUsage>> getUsages() {
    return usages;
  }
  public Set<String> getNspr() {
    return nspr;
  }
  public SimpleWorkerContext getCtxt() {
    return ctxt;
  }
  public void setCtxt(SimpleWorkerContext ctxt) {
    this.ctxt = ctxt;
  }
  public void fillOutJson(String pid, CanonicalResource cr, JsonObject j) {
    j.add("resourceType", cr.fhirType());
    if (cr.hasId()) {           j.add("id", cr.getId()); }
    if (cr.hasUrl()) {          j.add("canonical", cr.getUrl()); }
    if (cr.hasVersion()) {      j.add("version", cr.getVersion()); }
    if (cr.hasStatus()) {       j.add("status", cr.getStatus().toCode()); }
    if (cr.hasPublisher()) {    j.add("publisher", cr.getPublisher()); }
    if (cr.hasName()) {         j.add("name", cr.getName()); }
    if (cr.hasTitle()) {        j.add("title", cr.getTitle()); }
    if (cr.hasDate()) {         j.add("date", cr.getDateElement().asStringValue()); }
    if (cr.hasExperimental()) { j.add("experimental", cr.getExperimental()); }
    if (cr.hasDescription()) {  j.add("description", cr.getDescription()); }
    if (cr.hasCopyright()) {    j.add("copyright", cr.getCopyright()); }
    for (CodeableConcept cc : cr.getJurisdiction()) {
      for (Coding c : cc.getCoding()) {
        if (c.is("http://unstats.un.org/unsd/methods/m49/m49.htm", "001"))  {
          jurisdictions.add("uv");

          if (!j.has("jurisdictions")) {
            j.add("jurisdictions", new JsonArray());
          }
          j.getJsonArray("jurisdictions").add("uv");
        } else if ("urn:iso:std:iso:3166".equals(c.getSystem())) {
          if (c.getCode().length() == 2) {
            jurisdictions.add(c.getCode().toLowerCase());
            if (!j.has("jurisdictions")) {
              j.add("jurisdictions", new JsonArray());
            }
            j.getJsonArray("jurisdictions").add(c.getCode().toLowerCase());
          }
        }
      }
    }
        
    if (cr instanceof CodeSystem) {
      new XIGCodeSystemHandler(this).fillOutJson((CodeSystem) cr, j);
    }
    if (cr instanceof ValueSet) {
      new XIGValueSetHandler(this).fillOutJson((ValueSet) cr, j);
    }
    if (cr instanceof ConceptMap) {
      new XIGConceptMapHandler(this).fillOutJson((ConceptMap) cr, j);
    }
    if (cr instanceof NamingSystem) {
      new XIGNamingSystemHandler(this).fillOutJson((NamingSystem) cr, j);
    }
    if (cr instanceof StructureDefinition) {
      new XIGStructureDefinitionHandler(this).fillOutJson(pid, (StructureDefinition) cr, j);
    }
    if (cr instanceof OperationDefinition) {
      new XIGOperationDefinitionHandler(this).fillOutJson((OperationDefinition) cr, j);
    }
    if (cr instanceof SearchParameter) {
      new XIGSearchParameterHandler(this).fillOutJson((SearchParameter) cr, j);
    }
    if (cr instanceof CapabilityStatement) {
      new XIGCapabilityStatementHandler(this).fillOutJson((CapabilityStatement) cr, j);
    }
  }
  public Set<String> getJurisdictions() {
    return jurisdictions;
  }

  public void buildUsageMap() {
    for (CanonicalResource cr : resources.values()) {
      switch (cr.fhirType()) {
      case "CapabilityStatement" :
        XIGCapabilityStatementHandler.buildUsages(this, (CapabilityStatement) cr);
        break;
      case "SearchParameter" :
        XIGSearchParameterHandler.buildUsages(this, (SearchParameter) cr);
        break;
      case "OperationDefinition" :
        XIGOperationDefinitionHandler.buildUsages(this, (OperationDefinition) cr);
        break;
      case "StructureDefinition" :
        XIGStructureDefinitionHandler.buildUsages(this, (StructureDefinition) cr);
        break;
      case "ValueSet" :
        XIGValueSetHandler.buildUsages(this, (ValueSet) cr);
        break;
      case "CodeSystem" :
        XIGCodeSystemHandler.buildUsages(this, (CodeSystem) cr);
        break;
      case "ConceptMap" :
        XIGConceptMapHandler.buildUsages(this, (ConceptMap) cr);
        break;
//      case "StructureMap" :
//        XIGStructureMapHandler.buildUsages(this, (StructureMap) cr);
//        break;
//      case "Questionnaire" :
//        XIGQuestionnaireHandler.buildUsages(this, (Questionnaire) cr);
//        break;
      } 
    }
  }
  public void recordUsage(CanonicalResource cr, String value, UsageType usage) {
    if (value != null) {
      List<CanonicalResourceUsage> list = usages.get(value);
      if (list == null) {
        list = new ArrayList<>();
        usages.put(value, list);
      }
      for (CanonicalResourceUsage cu : list) {
        if (cu.resource == cr && cu.usage == usage) {
          return;
        }
      }
      list.add(new CanonicalResourceUsage(cr, usage));
    }
  }
  public XIGExtensionHandler getExtensionHandler() {
    return extensionHandler;
  }
  public void addSDError(String msg) {
    sdErrors.add(msg);
  
  }
  public List<String> getSDErrors() {
    return sdErrors;
  }
  
  
}
