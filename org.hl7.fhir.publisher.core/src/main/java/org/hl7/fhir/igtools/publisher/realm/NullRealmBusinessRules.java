package org.hl7.fhir.igtools.publisher.realm;

import java.io.IOException;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.StructureDefinition;

public class NullRealmBusinessRules extends RealmBusinessRules {

  private String realm;

  public NullRealmBusinessRules(String realm) {
    this.realm = realm;
  }

  @Override
  public void checkSD(FetchedFile f, StructureDefinition sd) throws IOException {
    // nothing    
  }

  @Override
  public void checkCR(FetchedFile f, CanonicalResource resource) {
    // nothing        
  }

  public void addOtherFiles(Set<String> otherFilesRun, String outputDir) {
    // nothing            
  }

  @Override
  public void startChecks(ImplementationGuide ig) {
    // nothing            
  }

  @Override
  public void finishChecks() {
    // nothing            
  }

  @Override
  public String checkHtml() {
    return "<ul><li>n/a</li></ul>";
  }

  @Override
  public String checkText() {
    return "n/a";
  }

  @Override
  public String code() {
    return realm == null ? "n/a" : realm;
  }

  @Override
  public boolean isExempt(String packageId) {
    return false;
  }

}
