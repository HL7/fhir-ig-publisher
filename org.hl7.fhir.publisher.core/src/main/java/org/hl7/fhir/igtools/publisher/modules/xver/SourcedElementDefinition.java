package org.hl7.fhir.igtools.publisher.modules.xver;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.VersionUtilities;

public class SourcedElementDefinition {
  public enum ElementValidState {
    FULL_VALID, CARDINALITY, NEW_TYPES, NEW_TARGETS, NOT_VALID, CODES, REMOVED_TYPES, PARENT
  }

  private StructureDefinition sd;
  private ElementDefinition ed;

  private ElementValidState validState;
  private CommaSeparatedStringBuilder statusReasons = new CommaSeparatedStringBuilder(",",  " and ");
  private String ver;
  private String startVer;
  private String stopVer;
  private Set<String> versions;
  private String verList;
  private SourcedElementDefinition repeater;
  private Set<String> names;
  private Set<Coding> codes;
  private StructureDefinition extension;

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

  public String getStatusReason() {
    if (statusReasons.length() == 0) {
      return validState == ElementValidState.FULL_VALID ? "Made up reason" : "No Change"; 
    } else {
      return statusReasons.toString();
    }
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

  public Set<String> getVersions() {
    return versions;
  }

  public void setVersions(Set<String> versions) {
    this.versions = versions;
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

  public ElementValidState getValidState() {
    return validState;
  }

  public void setValidState(ElementValidState validState) {
    this.validState = validState;
  }

  public boolean isValid() {
    return getValidState() != ElementValidState.NOT_VALID;
  }

  public void addToNames(Collection<String> names) {
    if (this.names == null) {
      this.names = new HashSet<>();
    }
    this.names.addAll(names);    
  }

  public Set<String> getNames() {
    return names;
  }
  
  public void addToCodes(Collection<Coding> codes) {
    if (this.codes == null) {
      this.codes = new HashSet<>();
    }
    this.codes.addAll(codes);    
  }

  public Set<Coding> getCodes() {
    return codes;
  }

  public StructureDefinition getExtension() {
    return extension;
  }

  public void setExtension(StructureDefinition extension) {
    this.extension = extension;
  }

  public boolean appliesToVersion(String tgtVer) {
    return versions != null && versions.contains(tgtVer);
  }

  public String extensionPath() {
    return "http://hl7.org/fhir/"+VersionUtilities.getMajMin(ver)+"/StructureDefinition/extension-"+ed.getPath();
  }
  
}