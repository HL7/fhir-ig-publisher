package org.hl7.fhir.dstu2016may.utils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.hl7.fhir.dstu2016may.formats.IParser;
import org.hl7.fhir.dstu2016may.formats.ParserType;
import org.hl7.fhir.dstu2016may.model.CodeSystem;
import org.hl7.fhir.dstu2016may.model.CodeableConcept;
import org.hl7.fhir.dstu2016may.model.Coding;
import org.hl7.fhir.dstu2016may.model.ConceptMap;
import org.hl7.fhir.dstu2016may.model.Resource;
import org.hl7.fhir.dstu2016may.model.StructureDefinition;
import org.hl7.fhir.dstu2016may.model.ValueSet;
import org.hl7.fhir.dstu2016may.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.dstu2016may.model.ValueSet.ValueSetExpansionComponent;
import org.hl7.fhir.dstu2016may.terminologies.ValueSetExpander.ValueSetExpansionOutcome;

public class FakeWorker implements IWorkerContext {

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
  public <T extends Resource> T fetchResource(Class<T> class_, String uri) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public <T extends Resource> boolean hasResource(Class<T> class_, String uri) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public List<String> getResourceNames() {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public List<StructureDefinition> allStructures() {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public CodeSystem fetchCodeSystem(String system) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public boolean supportsSystem(String system) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public List<ConceptMap> findMapsForSource(String url) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public ValueSetExpansionOutcome expandVS(ValueSet source, boolean cacheOk) {

    throw new Error("Not available in the FakeWorker");
  }

  @Override
  public ValueSetExpansionComponent expandVS(ConceptSetComponent inc) {

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
  public StructureDefinition fetchTypeDefinition(String typeName) {

    throw new Error("Not available in the FakeWorker");
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

  @Override
  public void setValidationMessageLanguage(Locale locale) {
    
  }

}
