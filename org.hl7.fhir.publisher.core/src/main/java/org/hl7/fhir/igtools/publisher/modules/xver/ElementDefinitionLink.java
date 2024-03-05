package org.hl7.fhir.igtools.publisher.modules.xver;

import java.util.HashSet;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.modules.xver.XVerAnalysisEngine.XVersions;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.Enumerations.ConceptMapRelationship;

public class ElementDefinitionLink {
  private XVersions versions;
  private ConceptMapRelationship rel;
  private SourcedElementDefinition next;
  private SourcedElementDefinition prev;
  private ConceptMap nextCM;
  private ConceptMap prevCM;
  private int leftWidth; 
  Set<String> chainIds = new HashSet<>();
  private Set<Coding> newCodes;
  private Set<Coding> oldCodes;

  @Override
  public String toString() {
    return getVersions()+": "+getPrev().toString()+" "+getRel().getSymbol()+" "+getNext().toString()+" ["+getChainIds().toString()+"]";
  }

  XVersions getVersions() {
    return versions;
  }

  void setVersions(XVersions versions) {
    this.versions = versions;
  }

  public ConceptMapRelationship getRel() {
    return rel;
  }

  void setRel(ConceptMapRelationship rel) {
    this.rel = rel;
  }

  public SourcedElementDefinition getNext() {
    return next;
  }

  void setNext(SourcedElementDefinition next) {
    this.next = next;
  }

  public SourcedElementDefinition getPrev() {
    return prev;
  }

  void setPrev(SourcedElementDefinition prev) {
    this.prev = prev;
  }

  public ConceptMap getNextCM() {
    return nextCM;
  }

  void setNextCM(ConceptMap nextCM) {
    this.nextCM = nextCM;
  }

  ConceptMap getPrevCM() {
    return prevCM;
  }

  void setPrevCM(ConceptMap prevCM) {
    this.prevCM = prevCM;
  }

  public int getLeftWidth() {
    return leftWidth;
  }

  void setLeftWidth(int leftWidth) {
    this.leftWidth = leftWidth;
  }

  Set<String> getChainIds() {
    return chainIds;
  }

  public Set<Coding> getNewCodes() {
    return newCodes;
  }

  public void setNewCodes(Set<Coding> newCodes) {
    this.newCodes = newCodes;
  }

  public Set<Coding> getOldCodes() {
    return oldCodes;
  }

  public void setOldCodes(Set<Coding> oldCodes) {
    this.oldCodes = oldCodes;
  }
  
  
}