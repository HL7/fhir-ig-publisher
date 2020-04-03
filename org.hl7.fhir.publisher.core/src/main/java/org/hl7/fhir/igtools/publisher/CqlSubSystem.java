package org.hl7.fhir.igtools.publisher;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.CqlSubSystem.CqlSourceFileInformation;
import org.hl7.fhir.r5.context.IWorkerContext.ILoggingService;
import org.hl7.fhir.r5.model.Library;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.validation.ValidationMessage;

public class CqlSubSystem {

  /** 
   * information about a cql file
   */
  public class CqlSourceFileInformation {
    private byte[] elm;
    private List<ValidationMessage> errors = new ArrayList<>();
    private List<String> dataRequirements = new ArrayList<>();
    private List<String> relatedArtifacts = new ArrayList<>();
    public byte[] getElm() {
      return elm;
    }
    public void setElm(byte[] elm) {
      this.elm = elm;
    }
    public List<ValidationMessage> getErrors() {
      return errors;
    }
    public List<String> getDataRequirements() {
      return dataRequirements;
    }
    public List<String> getRelatedArtifacts() {
      return relatedArtifacts;
    }
    
  }

  /**
   * The Implementation Guide build supports multiple versions. This code runs as R5 code.
   * The library reader loads the library from the NpmPackage and returns an R5 library,
   * irrespective of waht version the IG is 
   */
  public interface ILibraryReader {
    public Library readLibrary(InputStream stream); 
  }
  
  /**
   * all the NPM packages this IG depends on (including base)
   */
  private List<NpmPackage> packages;
  
  /**
   * All the file paths cql files might be found in (absolute local file paths)
   */
  private List<String> folders; 
  
  /**
   * Version indepedent reader
   */
  private ILibraryReader reader;

  /**
   * use this to write to the standard IG log
   */
  private ILoggingService logger;
  
  public CqlSubSystem(List<NpmPackage> packages, List<String> folders, ILibraryReader reader, ILoggingService logger) {
    super();
    this.packages = packages;
    this.folders = folders;
    this.reader = reader;
    this.logger = logger;
  }
  
  /**
   * Do the compile. Do not return any exceptions related to content; only thros exceptions for infrastructural issues 
   * 
   * Any exception will stop the build cold.
   */
  public void execute() throws FHIRException {
    
  }

  public CqlSourceFileInformation getFileInformation(String filename) {
    return null;
  }
  
}
