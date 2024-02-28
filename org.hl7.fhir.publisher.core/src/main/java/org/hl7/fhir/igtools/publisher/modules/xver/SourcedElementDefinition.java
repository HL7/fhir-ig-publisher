package org.hl7.fhir.igtools.publisher.modules.xver;

import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.StructureDefinition;

public class SourcedElementDefinition {
  private StructureDefinition sd;
  private ElementDefinition ed;

  private boolean valid;
  private String statusReason;
  private String ver;
  private String startVer;
  private String stopVer;
  private String verList;
  private SourcedElementDefinition repeater;

  public SourcedElementDefinition(StructureDefinition sd, ElementDefinition ed) {
    this.sd = sd;
    this.ed = ed;    
    this.ver = sd.getFhirVersion().toCode();
  }

  @Override
  public String toString() {
    return ed.getPath()+" ("+sd.getFhirVersion().toCode()+")";
  }

  public StructureDefinition getSd() {
    return sd;
  }

  public ElementDefinition getEd() {
    return ed;
  }

  public boolean isValid() {
    return valid;
  }

  void setValid(boolean valid) {
    this.valid = valid;
  }

  public String getStatusReason() {
    return statusReason;
  }

  void setStatusReason(String statusReason) {
    this.statusReason = statusReason;
  }

  public String getVer() {
    return ver;
  }

  void setVer(String ver) {
    this.ver = ver;
  }

  String getStartVer() {
    return startVer;
  }

  void setStartVer(String startVer) {
    this.startVer = startVer;
  }

  String getStopVer() {
    return stopVer;
  }

  void setStopVer(String stopVer) {
    this.stopVer = stopVer;
  }

  public String getVerList() {
    return verList;
  }

  void setVerList(String verList) {
    this.verList = verList;
  }

  SourcedElementDefinition getRepeater() {
    return repeater;
  }

  void setRepeater(SourcedElementDefinition repeater) {
    this.repeater = repeater;
  }
}