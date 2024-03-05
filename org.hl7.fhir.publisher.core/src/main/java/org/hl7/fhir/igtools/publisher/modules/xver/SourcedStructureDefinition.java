package org.hl7.fhir.igtools.publisher.modules.xver;

import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.Enumerations.ConceptMapRelationship;
import org.hl7.fhir.r5.model.StructureDefinition;

public class SourcedStructureDefinition {

  private IWorkerContext definitions;
  private StructureDefinition structureDefinition;
  private ConceptMapRelationship relationship;
  
  protected SourcedStructureDefinition(IWorkerContext definitions, StructureDefinition structureDefinition, ConceptMapRelationship relationship) {
    super();
    this.definitions = definitions;
    this.structureDefinition = structureDefinition;
    this.relationship = relationship;
  }
  public IWorkerContext getDefinitions() {
    return definitions;
  }
  public StructureDefinition getStructureDefinition() {
    return structureDefinition;
  }
  public ConceptMapRelationship getRelationship() {
    return relationship;
  }

}