package org.hl7.fhir.igtools.publisher.realm;

import java.io.IOException;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.StructureDefinition;

public class NullRealmBusinessRules extends RealmBusinessRules {

  @Override
  public void checkSD(FetchedFile f, StructureDefinition sd) throws IOException {
    // nothing    
  }

  @Override
  public void checkCR(FetchedFile f, CanonicalResource resource) {
    // nothing        
  }

  public void addOtherFiles(Set<String> otherFilesRun) {
    // nothing            
  }

  @Override
  public void startChecks() {
    // nothing            
  }

  @Override
  public void finishChecks() {
    // nothing            
  }

}
