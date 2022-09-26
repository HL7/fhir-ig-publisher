package org.hl7.fhir.igtools.publisher.utils.xig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CapabilityStatement;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.NamingSystem;
import org.hl7.fhir.r5.model.OperationDefinition;
import org.hl7.fhir.r5.model.SearchParameter;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.renderers.DataRenderer;

import com.google.gson.JsonObject;

public class XIGInformation {

  Set<String> pid = new HashSet<>();
  Map<String, Map<String, CanonicalResource>> counts = new HashMap<>();
  Map<String, CanonicalResource> resources = new HashMap<>();
  private Set<String> opr = new HashSet<>();
  private Set<String> spr = new HashSet<>();
  private Set<String> nspr = new HashSet<>();
  private JsonObject json = new JsonObject();
  private Set<String> realms = new HashSet<>();
  private SimpleWorkerContext ctxt;
  
  public Set<String> getPid() {
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

  public Set<String> getNspr() {
    return nspr;
  }
  public SimpleWorkerContext getCtxt() {
    return ctxt;
  }
  public void setCtxt(SimpleWorkerContext ctxt) {
    this.ctxt = ctxt;
  }
  public void fillOutJson(CanonicalResource cr, JsonObject j) {
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
    for (CodeableConcept cc : cr.getJurisdiction()) {
      if (cr.hasJurisdiction()) { j.addProperty("jurisdiction", DataRenderer.display(ctxt, cr.getJurisdictionFirstRep())); 
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
      new XIGStructureDefinitionHandler(this).fillOutJson((StructureDefinition) cr, j);
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
}
