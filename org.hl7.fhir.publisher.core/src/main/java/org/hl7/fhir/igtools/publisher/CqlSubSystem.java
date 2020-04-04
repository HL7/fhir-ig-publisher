package org.hl7.fhir.igtools.publisher;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cqframework.cql.cql2elm.*;
import org.cqframework.cql.elm.tracking.TrackBack;
import org.fhir.ucum.UcumEssenceService;
import org.fhir.ucum.UcumException;
import org.fhir.ucum.UcumService;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
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
    public Library readLibrary(InputStream stream) throws FHIRFormatError, IOException; 
  }
  
  /**
   * all the NPM packages this IG depends on (including base). 
   * This list is in a maintained order such that you can just 
   * do for (NpmPackage p : packages) and that will resolve the 
   * library in the right order
   *   
   */
  private List<NpmPackage> packages;
  
  /**
   * All the file paths cql files might be found in (absolute local file paths)
   * 
   * will be at least one error
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

  /**
   * UcumService used by the translator to validate UCUM units
   */
  private UcumService ucumService;

  /**
   * Map of translated files by fully qualified file name.
   * Populated during execute
   */
  private Map<String, CqlSourceFileInformation> fileMap;

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
   * note that it's not an error if there's no .cql files - this is called without checking for their existence
   *  
   * Any exception will stop the build cold.
   */
  public void execute() throws FHIRException {
    try {
      fileMap = new HashMap<>();

      // TODO: Construct NPMLibrarySourceProvider

      // Set up UcumService
      initializeUcumService();

      // foreach folder
      for (String folder : folders) {
        translateFolder(folder);
      }
    }
    catch (Exception E) {
      logger.logDebugMessage(ILoggingService.LogCategory.PROGRESS, String.format("Errors occurred attempting to translate CQL content: %s", E.getMessage()));
    }
  }

  /**
   * Return CqlSourceFileInformation for the given filename
   * @param filename Fully qualified name of the source file
   * @return
   */
  public CqlSourceFileInformation getFileInformation(String filename) {
    if (fileMap == null) {
      throw new IllegalStateException("File map is not available, execute has not been called");
    }

    if (!fileMap.containsKey(filename)) {
      throw new IllegalArgumentException(String.format("File %s not found in file map", filename));
    }

    return this.fileMap.remove(filename);
  }

  /**
   * Called at the end after all getFileInformation have been called
   * return any errors that didn't have any particular home, and also
   * errors for any files that were linked but haven't been accessed using
   * getFileInformation - these have been omitted from the IG, and that's
   * an error
   *
   * @return
   */
  public List<ValidationMessage> getGeneralErrors() {
    List<ValidationMessage> result = new ArrayList<>();

    if (fileMap != null) {
      for (Map.Entry<String, CqlSourceFileInformation> entry : fileMap.entrySet()) {
        result.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.PROCESSING, entry.getKey(), "CQL source was not associated with a library resource in the IG.", ValidationMessage.IssueSeverity.ERROR));
      }
    }

    return result;
  }

  private void translateFolder(String folder) {
    // TODO: Readconfig - CqlTranslator.Options, outputFormat: { XML, JSON, BOTH }, validateUnits, errorLevel, signatureLevel
    CqlTranslator.Options[] options = getDefaultOptions();
    String format = "XML";
    CqlTranslatorException.ErrorSeverity errorLevel = CqlTranslatorException.ErrorSeverity.Info;
    LibraryBuilder.SignatureLevel signatureLevel = LibraryBuilder.SignatureLevel.None;
    boolean validateUnits = true;

    // Setup
    // Construct DefaultLibrarySourceProvider
    // Construct FhirLibrarySourceProvider
    ModelManager modelManager = new ModelManager();
    LibraryManager libraryManager = new LibraryManager(modelManager);
    libraryManager.getLibrarySourceLoader().registerProvider(new DefaultLibrarySourceProvider(Paths.get(folder)));
    libraryManager.getLibrarySourceLoader().registerProvider(new FhirLibrarySourceProvider());
    // TODO: register NpmLibrarySourceProvider

    // foreach *.cql file
    for (File file : new File(folder).listFiles(getCqlFilenameFilter())) {
      translateFile(modelManager, libraryManager, file, format, validateUnits, errorLevel, signatureLevel, options);
    }
  }

  private ValidationMessage.IssueType severityToIssueType(CqlTranslatorException.ErrorSeverity severity) {
    switch (severity) {
      case Info: return ValidationMessage.IssueType.INFORMATIONAL;
      case Warning:
      case Error: return ValidationMessage.IssueType.PROCESSING;
      default: return ValidationMessage.IssueType.UNKNOWN;
    }
  }

  private ValidationMessage.IssueSeverity severityToIssueSeverity(CqlTranslatorException.ErrorSeverity severity) {
    switch (severity) {
      case Info: return ValidationMessage.IssueSeverity.INFORMATION;
      case Warning: return ValidationMessage.IssueSeverity.WARNING;
      case Error: return ValidationMessage.IssueSeverity.ERROR;
      default: return ValidationMessage.IssueSeverity.NULL;
    }
  }

  private ValidationMessage exceptionToValidationMessage(File file, CqlTranslatorException exception) {
    TrackBack tb = exception.getLocator();
    if (tb != null) {
      return new ValidationMessage(ValidationMessage.Source.Publisher, severityToIssueType(exception.getSeverity()),
              tb.getStartLine(), tb.getStartChar(), tb.getLibrary().getId(), exception.getMessage(),
              severityToIssueSeverity(exception.getSeverity()));
    }
    else {
      return new ValidationMessage(ValidationMessage.Source.Publisher, severityToIssueType(exception.getSeverity()),
              file.toString(), exception.getMessage(), severityToIssueSeverity(exception.getSeverity()));
    }
  }

  private void translateFile(ModelManager modelManager, LibraryManager libraryManager, File file, String format,
                             boolean validateUnits, CqlTranslatorException.ErrorSeverity errorLevel,
                             LibraryBuilder.SignatureLevel signatureLevel, CqlTranslator.Options[] options) {
    try {
      // translate toXML
      CqlTranslator translator = CqlTranslator.fromFile(file, modelManager, libraryManager,
              validateUnits ? ucumService : null, errorLevel, signatureLevel, options);

      CqlSourceFileInformation result = new CqlSourceFileInformation();
      // record errors and warnings
      for (CqlTranslatorException exception : translator.getExceptions()) {
        result.getErrors().add(exceptionToValidationMessage(file, exception));
      }

      // TODO: Extract relatedArtifact data
      // TODO: Extract dataRequirement data
      // TODO: Extract terminology data

      // convert to base64 bytes
      result.setElm(translator.toXml().getBytes());

      // put to map
      fileMap.put(file.getAbsoluteFile().toString(), result);
    }
    catch (IOException e) {
      logger.logDebugMessage(ILoggingService.LogCategory.PROGRESS,
              String.format("Errors occurred attempting to translate file %s: %s", file.toString(), e.getMessage()));
    }
  }

  private CqlTranslator.Options[] getDefaultOptions() {
    // Default options based on recommended settings: http://build.fhir.org/ig/HL7/cqf-measures/using-cql.html#translation-to-elm
    ArrayList<CqlTranslator.Options> options = new ArrayList<>();
    options.add(CqlTranslator.Options.EnableAnnotations);
    options.add(CqlTranslator.Options.EnableLocators);
    options.add(CqlTranslator.Options.DisableListDemotion);
    options.add(CqlTranslator.Options.DisableListPromotion);
    return options.toArray(new CqlTranslator.Options[options.size()]);
  }

  private FilenameFilter getCqlFilenameFilter() {
    return new FilenameFilter() {
      @Override
      public boolean accept(File path, String name) {
        return name.endsWith(".cql");
      }
    };
  }

  private void initializeUcumService() {
    try {
      ucumService = new UcumEssenceService(UcumEssenceService.class.getResourceAsStream("/ucum-essence.xml"));
    }
    catch (UcumException e) {
      logger.logDebugMessage(ILoggingService.LogCategory.PROGRESS, String.format("Could not create UCUM validation service: %s", e.getMessage()));
    }
  }
}
