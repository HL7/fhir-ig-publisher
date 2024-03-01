package org.hl7.fhir.igtools.publisher.modules.xver;

import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;

public class SourcedElementDefinition {
  private StructureDefinition sd;
  private ElementDefinition ed;

  private boolean valid;
  private CommaSeparatedStringBuilder statusReasons = new CommaSeparatedStringBuilder(",",  " and ");
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
    return statusReasons.length() == 0 ? "No Change" : statusReasons.toString();
  }

  void addStatusReason(String statusReason) {
    statusReasons.append(statusReason);
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

  public void clearStatusReason() {
    statusReasons = new CommaSeparatedStringBuilder(",",  "and ");    
  }
}