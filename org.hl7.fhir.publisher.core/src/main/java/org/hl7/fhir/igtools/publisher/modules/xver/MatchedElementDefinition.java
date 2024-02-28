package org.hl7.fhir.igtools.publisher.modules.xver;

import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.Enumerations.ConceptMapRelationship;

public class MatchedElementDefinition {

  private ElementDefinition ed;
  private ConceptMapRelationship rel;

  public MatchedElementDefinition(ElementDefinition ed, ConceptMapRelationship rel) {
    this.ed = ed;
    this.rel = rel;
  }

  ElementDefinition getEd() {
    return ed;
  }

  ConceptMapRelationship getRel() {
    return rel;
  }

}