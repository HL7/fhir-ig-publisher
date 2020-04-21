package org.hl7.fhir.igtools.publisher.realm;

import java.io.IOException;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.StructureDefinition;

public abstract class RealmBusinessRules {

  public abstract void startChecks() throws IOException; 
  public abstract void checkSD(FetchedFile f, StructureDefinition sd) throws IOException ;
  public abstract void checkCR(FetchedFile f, CanonicalResource resource);
  public abstract void finishChecks();
  
  public abstract void addOtherFiles(Set<String> otherFilesRun) throws IOException;

}
