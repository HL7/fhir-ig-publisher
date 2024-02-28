package org.hl7.fhir.igtools.publisher.modules.xver;

import org.hl7.fhir.r5.model.ElementDefinition;

public class ElementDefinitionPair {
  private ElementDefinition focus;
  private ElementDefinition anchor;
  
  public ElementDefinitionPair(ElementDefinition focus, ElementDefinition anchor) {
    super();
    this.focus = focus;
    this.anchor = anchor;
  }
  public ElementDefinition getFocus() {
    return focus;
  }
  public ElementDefinition getAnchor() {
    return anchor;
  }
  
}