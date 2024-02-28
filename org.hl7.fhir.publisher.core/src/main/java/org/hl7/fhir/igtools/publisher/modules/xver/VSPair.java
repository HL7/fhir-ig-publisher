package org.hl7.fhir.igtools.publisher.modules.xver;

import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.ValueSet;

public class VSPair {

  private ValueSet vs;
  private CodeSystem cs;
  private String version;

  public VSPair(String version, ValueSet vs, CodeSystem cs) {
    this.version = version;
    this.vs = vs;
    this.cs = cs;
  }

  public String getVersion() {
    return version;
  }

  public ValueSet getVs() {
    return vs;
  }

  public CodeSystem getCs() {
    return cs;
  }
}