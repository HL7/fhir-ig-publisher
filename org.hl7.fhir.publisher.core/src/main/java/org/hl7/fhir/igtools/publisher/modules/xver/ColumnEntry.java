package org.hl7.fhir.igtools.publisher.modules.xver;

import org.hl7.fhir.model.core.ElementDefinition;

public class ColumnEntry {


  private ElementDefinitionLink link;
  private ElementDefinition ed;

  public ColumnEntry(ElementDefinitionLink link, ElementDefinition ed) {
    this.link = link;
    this.ed = ed;
  }

  public ElementDefinitionLink getLink() {
    return link;
  }

  public ElementDefinition getEd() {
    return ed;
  }


}