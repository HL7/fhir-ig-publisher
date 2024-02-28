package org.hl7.fhir.igtools.publisher.modules.xver;

import java.util.HashMap;
import java.util.Map;

import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.utilities.FhirPublication;

public class VersionDefinitions {
  private FhirPublication version;
  private Map<String, StructureDefinition> structures = new HashMap<>();
  private Map<String, ValueSet> valueSets = new HashMap<>();
  private Map<String, CodeSystem> codeSystems = new HashMap<>();
  
  public void add(CanonicalResource cr) {
    if (cr instanceof CodeSystem) {
      getCodeSystems().put(cr.getUrl(), (CodeSystem) cr);
      if (cr.hasVersion()) {
        getCodeSystems().put(cr.getUrl()+"|"+cr.getVersion(), (CodeSystem) cr);
      }
    } else if (cr instanceof ValueSet) {
      getValueSets().put(cr.getUrl(), (ValueSet) cr);
      if (cr.hasVersion()) {
        getValueSets().put(cr.getUrl()+"|"+cr.getVersion(), (ValueSet) cr);
      }
    } else if (cr instanceof StructureDefinition) {
      StructureDefinition sd = (StructureDefinition) cr;
      if (!sd.hasBaseDefinition() || sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
        getStructures().put(cr.getName(), sd);
      }
    }      
  }
  
  public String summary() {
    return ""+getStructures().size()+" Structures, "+getValueSets().size()+" Value Sets, "+getCodeSystems().size()+" CodeSystems";
  }

  public FhirPublication getVersion() {
    return version;
  }

  public void setVersion(FhirPublication version) {
    this.version = version;
  }

  public Map<String, StructureDefinition> getStructures() {
    return structures;
  }

  public Map<String, ValueSet> getValueSets() {
    return valueSets;
  }

  public Map<String, CodeSystem> getCodeSystems() {
    return codeSystems;
  }
}