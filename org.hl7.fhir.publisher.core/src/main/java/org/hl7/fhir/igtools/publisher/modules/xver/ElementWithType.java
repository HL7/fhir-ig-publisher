package org.hl7.fhir.igtools.publisher.modules.xver;

import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.StructureDefinition;

public class ElementWithType {

  private IWorkerContext def;
  private StructureDefinition sd;
  private ElementDefinition ed;
  private String type;

  public ElementWithType(IWorkerContext def, StructureDefinition sd, ElementDefinition ed) {
    this.def = def;
    this.sd = sd;
    this.ed = ed;
   }

  public ElementWithType(IWorkerContext def, StructureDefinition sd, ElementDefinition ed, String type) {
    this.def = def;
    this.sd = sd;
    this.ed = ed;
    this.type = type;
   }

  public StructureDefinition getSd() {
    return sd;
  }

  public ElementDefinition getEd() {
    return ed;
  }

  public String getType() {
    return type;
  }

  public IWorkerContext getDef() {
    return def;
  }

  public String getWorkingType() {
    if (type != null) {
      return type;
    } else if (!ed.getPath().contains(".")) {
      return ed.getPath();
    } else if (ed.hasContentReference()) {
      return ed.getContentReference();
    } else if (ed.getType().size() == 0) {
      throw new Error("No type information available for "+ed.getPath());
    } else if (ed.getType().size() > 1) {
      throw new Error("Multipe types for "+ed.getPath());
    } else {
      return ed.getTypeFirstRep().getWorkingCode();
    }
  }

  @Override
  public String toString() {
    return ed.getPath()+(type == null ? "" : ":"+type)+"/"+sd.getFhirVersion().toCode();
  }

  public void setType(String type) {
    this.type = type;
    
  }

  public SourcedElementDefinition toSED() {
    return new SourcedElementDefinition(sd, ed);
  }

}
