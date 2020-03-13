package org.hl7.fhir.dstu3.utils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.hl7.fhir.dstu3.context.IWorkerContext;
import org.hl7.fhir.dstu3.formats.IParser;
import org.hl7.fhir.dstu3.formats.ParserType;
import org.hl7.fhir.dstu3.model.CodeSystem;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.ConceptMap;
import org.hl7.fhir.dstu3.model.ExpansionProfile;
import org.hl7.fhir.dstu3.model.MetadataResource;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.dstu3.model.StructureDefinition;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.hl7.fhir.dstu3.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.dstu3.model.ValueSet.ValueSetExpansionComponent;
import org.hl7.fhir.dstu3.terminologies.ValueSetExpander.ValueSetExpansionOutcome;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.TerminologyServiceException;

public class FakeWorker implements IWorkerContext {

  @Override
  public String getVersion() {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public IParser getParser(ParserType type) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public IParser getParser(String type) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public IParser newJsonParser() {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public IParser newXmlParser() {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public INarrativeGenerator getNarrativeGenerator(String prefix, String basePath) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public IResourceValidator newValidator() throws FHIRException {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public <T extends Resource> T fetchResource(Class<T> class_, String uri) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public <T extends Resource> T fetchResourceWithException(Class<T> class_, String uri) throws FHIRException {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public <T extends Resource> boolean hasResource(Class<T> class_, String uri) {
    throw new Error("Not available in the FakeWorker");  }

  @Override
  public List<String> getResourceNames() {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public Set<String> getResourceNamesAsSet() {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public List<String> getTypeNames() {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public List<StructureDefinition> allStructures() {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public List<MetadataResource> allConformanceResources() {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public ExpansionProfile getExpansionProfile() {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public void setExpansionProfile(ExpansionProfile expProfile) {


  }

  @Override
  public CodeSystem fetchCodeSystem(String system) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public boolean supportsSystem(String system) throws TerminologyServiceException {
    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public List<ConceptMap> findMapsForSource(String url) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public ValueSetExpansionOutcome expandVS(ValueSet source, boolean cacheOk, boolean heiarchical) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public ValueSetExpansionComponent expandVS(ConceptSetComponent inc, boolean heiarchical) throws TerminologyServiceException {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public ValidationResult validateCode(String system, String code, String display) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public ValidationResult validateCode(String system, String code, String display, ValueSet vs) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public ValidationResult validateCode(Coding code, ValueSet vs) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public ValidationResult validateCode(CodeableConcept code, ValueSet vs) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public ValidationResult validateCode(String system, String code, String display, ConceptSetComponent vsi) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public String getAbbreviation(String name) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public Set<String> typeTails() {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public String oid2Uri(String code) {
    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public boolean hasCache() {
    throw new Error("Not available in the FakeWorker");  }

  @Override
  public void setLogger(ILoggingService logger) {
    throw new Error("Not available in the FakeWorker");  }

  @Override
  public boolean isNoTerminologyServer() {
    throw new Error("Not available in the FakeWorker");  }

  @Override
  public StructureDefinition fetchTypeDefinition(String typeName) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public void setValidationMessageLanguage(Locale locale) {
    
  }

  @Override
  public Locale getLocale() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setLocale(Locale locale) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public String formatMessage(String theMessage, Object... theMessageArguments) {
    // TODO Auto-generated method stub
    return null;
  }


}
