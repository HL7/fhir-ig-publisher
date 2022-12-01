package org.hl7.fhir.igtools.publisher.realm;

import java.io.IOException;
import java.util.Set;

import org.hl7.fhir.exceptions.DefinitionException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.StructureDefinition;

public abstract class RealmBusinessRules {

  public abstract void startChecks(ImplementationGuide ig) throws IOException; 
  public abstract void checkSD(FetchedFile f, StructureDefinition sd) throws IOException ;
  public abstract void checkCR(FetchedFile f, CanonicalResource resource);
  public abstract void finishChecks() throws DefinitionException, FHIRFormatError, IOException;
  
  public abstract void addOtherFiles(Set<String> otherFilesRun, String outputDir) throws IOException;
  public abstract String checkHtml();
  public abstract String checkText();
  public abstract String code();
  public abstract boolean isExempt(String packageId);

}
