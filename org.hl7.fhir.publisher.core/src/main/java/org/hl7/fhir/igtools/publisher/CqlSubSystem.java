package org.hl7.fhir.igtools.publisher;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

import org.cqframework.cql.cql2elm.*;
import org.cqframework.cql.cql2elm.model.TranslatedLibrary;
import org.cqframework.cql.elm.tracking.TrackBack;
import org.fhir.ucum.UcumEssenceService;
import org.fhir.ucum.UcumException;
import org.fhir.ucum.UcumService;
import org.hl7.elm.r1.*;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.CqlSubSystem.CqlSourceFileInformation;
import org.hl7.fhir.r5.context.IWorkerContext.ILoggingService;
import org.hl7.fhir.r5.model.Library;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.w3._1999.xhtml.P;

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
   * Provides a library source provider that can resolve CQL library source from an Npm package
   */
  public class NpmLibrarySourceProvider implements LibrarySourceProvider {

    @Override
    public InputStream getLibrarySource(VersionedIdentifier identifier) {
      // VersionedIdentifier.id: Name of the library
      // VersionedIdentifier.system: Namespace for the library, as a URL
      // VersionedIdentifier.version: Version of the library
      // TODO: Optimize to use the index to resolve by URL
      for (NpmPackage p : packages) {
        if (p.canonical().equals(identifier.getSystem())) {
          // Assume "package" folder organization
          try {
            for (String s : p.listResources("Library")) {
              Library l = reader.readLibrary(p.load("package", s));
              if (l.getName().equals(identifier.getId())) {
                if ((identifier.getVersion() == null) || identifier.getVersion().equals(l.getVersion())) {
                  for (org.hl7.fhir.r5.model.Attachment a : l.getContent()) {
                    if (a.getContentType() != null && a.getContentType().equals("text/cql")) {
                      return new ByteArrayInputStream(a.getData());
                    }
                  }
                }
              }
            }
          } catch (IOException e) {
            logger.logDebugMessage(ILoggingService.LogCategory.PROGRESS, String.format("Exceptions occurred attempting to load npm library source for %s", identifier.toString()));
          }
        }
      }

      return null;
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
      logger.logMessage("Translating CQL source");
      fileMap = new HashMap<>();

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
    logger.logMessage(String.format("Translating CQL source in folder %s", folder));

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
    libraryManager.getLibrarySourceLoader().registerProvider(new NpmLibrarySourceProvider());

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
      logger.logMessage(String.format("Translating CQL source in file %s", file.toString()));

      // translate toXML
      CqlTranslator translator = CqlTranslator.fromFile(file, modelManager, libraryManager,
              validateUnits ? ucumService : null, errorLevel, signatureLevel, options);

      CqlSourceFileInformation result = new CqlSourceFileInformation();
      // record errors and warnings
      for (CqlTranslatorException exception : translator.getExceptions()) {
        result.getErrors().add(exceptionToValidationMessage(file, exception));
      }

      // TODO: If the translation fails, output all the validation messages to the log?
      if (translator.getErrors().size() > 0) {
        logger.logMessage(String.format("Translation failed due with (%d) errors.", translator.getErrors().size()));
      }
      else {

        // convert to base64 bytes
        result.setElm(translator.toXml().getBytes());

        // Extract relatedArtifact data
        result.relatedArtifacts.addAll(extractRelatedArtifacts(translator.toELM()));

        // Extract dataRequirement data
        result.dataRequirements.addAll(extractDataRequirements(translator.toRetrieves(), translator.getTranslatedLibrary(), libraryManager));

        // TODO: Extract terminology data? (include code system and value set references as relatedArtifacts?

        // put to map
        fileMap.put(file.getAbsoluteFile().toString(), result);
      }
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

  private List<String> extractRelatedArtifacts(org.hl7.elm.r1.Library library) {
    List<String> result = new ArrayList();

    if (library.getIncludes() != null && !library.getIncludes().getDef().isEmpty()) {
      for (IncludeDef def : library.getIncludes().getDef()) {
        org.hl7.fhir.r5.model.RelatedArtifact ra = toRelatedArtifact(def);
        // TODO: Serialize to the result list
      }
    }

    return result;
  }

  private List<String> extractDataRequirements(List<org.hl7.elm.r1.Retrieve> retrieves, TranslatedLibrary library, LibraryManager libraryManager) {
    List<String> result = new ArrayList();

    for (Retrieve retrieve : retrieves) {
      org.hl7.fhir.r5.model.DataRequirement dr = toDataRequirement(retrieve, library, libraryManager);
      // TODO: Serialize to the result list
    }

    return result;
  }

  // TODO: Express in version independent Element Model?
  private org.hl7.fhir.r5.model.RelatedArtifact toRelatedArtifact(IncludeDef includeDef) {
    return new org.hl7.fhir.r5.model.RelatedArtifact()
            .setType(org.hl7.fhir.r5.model.RelatedArtifact.RelatedArtifactType.DEPENDSON)
            .setResource(getReferenceUrl(includeDef.getPath(), includeDef.getVersion()));
  }

  private String getReferenceUrl(String path, String version) {
    if (!Paths.get(path).isAbsolute()) {
      // TODO: How do I get to the canonical base for the IG?
    }

    return String.format("Library/%s%s", path, version != null ? ("|" + version) : "");
  }

  private org.hl7.fhir.r5.model.DataRequirement toDataRequirement(Retrieve retrieve, TranslatedLibrary library, LibraryManager libraryManager) {
    org.hl7.fhir.r5.model.DataRequirement dr = new org.hl7.fhir.r5.model.DataRequirement();

    dr.setType(org.hl7.fhir.r5.model.Enumerations.FHIRAllTypes.valueOf(retrieve.getDataType().getLocalPart()));

    // Set profile if specified
    if (retrieve.getTemplateId() != null) {
      dr.setProfile(Collections.singletonList(new org.hl7.fhir.r5.model.CanonicalType(retrieve.getTemplateId())));
    }

    // Set code path if specified
    if (retrieve.getCodeProperty() != null) {
      org.hl7.fhir.r5.model.DataRequirement.DataRequirementCodeFilterComponent cfc =
              new org.hl7.fhir.r5.model.DataRequirement.DataRequirementCodeFilterComponent();

      cfc.setPath(retrieve.getCodeProperty());

      if (retrieve.getCodes() instanceof ValueSetRef) {
        ValueSetRef vsr = (ValueSetRef)retrieve.getCodes();
        cfc.setValueSet(toReference(resolveValueSetRef(vsr, library, libraryManager)));
      }

      if (retrieve.getCodes() instanceof org.hl7.elm.r1.List) {
        org.hl7.elm.r1.List codeList = (org.hl7.elm.r1.List)retrieve.getCodes();
        for (Expression e : codeList.getElement()) {
          if (e instanceof org.hl7.elm.r1.CodeRef) {
            CodeRef cr = (CodeRef)e;
            cfc.addCode(toCoding(toCode(resolveCodeRef(cr, library, libraryManager)), library, libraryManager));
          }

          if (e instanceof org.hl7.elm.r1.Code) {
            cfc.addCode(toCoding((org.hl7.elm.r1.Code)e, library, libraryManager));
          }

          if (e instanceof org.hl7.elm.r1.ConceptRef) {
            ConceptRef cr = (ConceptRef)e;
            org.hl7.fhir.r5.model.CodeableConcept c = toCodeableConcept(toConcept(resolveConceptRef(cr, library, libraryManager), library, libraryManager), library, libraryManager);
            for (org.hl7.fhir.r5.model.Coding code : c.getCoding()) {
              cfc.addCode(code);
            }
          }

          if (e instanceof org.hl7.elm.r1.Concept) {
            org.hl7.fhir.r5.model.CodeableConcept c = toCodeableConcept((org.hl7.elm.r1.Concept)e, library, libraryManager);
            for (org.hl7.fhir.r5.model.Coding code : c.getCoding()) {
              cfc.addCode(code);
            }
          }
        }
      }

      dr.getCodeFilter().add(cfc);
    }

    // TODO: Set date range filters if literal

    return dr;
  }

  private org.hl7.fhir.r5.model.Coding toCoding(Code code, TranslatedLibrary library, LibraryManager libraryManager) {
    CodeSystemDef codeSystemDef = resolveCodeSystemRef(code.getSystem(), library, libraryManager);
    org.hl7.fhir.r5.model.Coding coding = new org.hl7.fhir.r5.model.Coding();
    coding.setCode(code.getCode());
    coding.setDisplay(code.getDisplay());
    coding.setSystem(codeSystemDef.getId());
    coding.setVersion(codeSystemDef.getVersion());
    return coding;
  }

  private org.hl7.fhir.r5.model.CodeableConcept toCodeableConcept(Concept concept, TranslatedLibrary library, LibraryManager libraryManager) {
    org.hl7.fhir.r5.model.CodeableConcept codeableConcept = new org.hl7.fhir.r5.model.CodeableConcept();
    codeableConcept.setText(concept.getDisplay());
    for (Code code : concept.getCode()) {
      codeableConcept.addCoding(toCoding(code, library, libraryManager));
    }
    return codeableConcept;
  }

  private String toReference(CodeSystemDef codeSystemDef) {
    return codeSystemDef.getId() + codeSystemDef.getVersion() != null ? ("|" + codeSystemDef.getVersion()) : "";
  }

  private String toReference(ValueSetDef valueSetDef) {
    return valueSetDef.getId() + valueSetDef.getVersion() != null ? ("|" + valueSetDef.getVersion()) : "";
  }

  // TODO: Move to the CQL-to-ELM translator

  private org.hl7.elm.r1.Concept toConcept(ConceptDef conceptDef, TranslatedLibrary library, LibraryManager libraryManager) {
    org.hl7.elm.r1.Concept concept = new org.hl7.elm.r1.Concept();
    concept.setDisplay(conceptDef.getDisplay());
    for (org.hl7.elm.r1.CodeRef codeRef : conceptDef.getCode()) {
      concept.getCode().add(toCode(resolveCodeRef(codeRef, library, libraryManager)));
    }
    return concept;
  }

  private org.hl7.elm.r1.Code toCode(CodeDef codeDef) {
    return new org.hl7.elm.r1.Code().withCode(codeDef.getId()).withSystem(codeDef.getCodeSystem()).withDisplay(codeDef.getDisplay());
  }

  private org.hl7.elm.r1.CodeDef resolveCodeRef(CodeRef codeRef, TranslatedLibrary library, LibraryManager libraryManager) {
    // If the reference is to another library, resolve to that library
    if (codeRef.getLibraryName() != null) {
      library = resolveLibrary(codeRef.getLibraryName(), library, libraryManager);
    }

    return library.resolveCodeRef(codeRef.getName());
  }

  private org.hl7.elm.r1.ConceptDef resolveConceptRef(ConceptRef conceptRef, TranslatedLibrary library, LibraryManager libraryManager) {
    // If the reference is to another library, resolve to that library
    if (conceptRef.getLibraryName() != null) {
      library = resolveLibrary(conceptRef.getLibraryName(), library, libraryManager);
    }

    return library.resolveConceptRef(conceptRef.getName());
  }

  private CodeSystemDef resolveCodeSystemRef(CodeSystemRef codeSystemRef, TranslatedLibrary library, LibraryManager libraryManager) {
    if (codeSystemRef.getLibraryName() != null) {
      library = resolveLibrary(codeSystemRef.getLibraryName(), library, libraryManager);
    }

    return library.resolveCodeSystemRef(codeSystemRef.getName());
  }

  private ValueSetDef resolveValueSetRef(ValueSetRef valueSetRef, TranslatedLibrary library, LibraryManager libraryManager) {
    // If the reference is to another library, resolve to that library
    if (valueSetRef.getLibraryName() != null) {
      library = resolveLibrary(valueSetRef.getLibraryName(), library, libraryManager);
    }

    return library.resolveValueSetRef(valueSetRef.getName());
  }

  private TranslatedLibrary resolveLibrary(String localLibraryName, TranslatedLibrary library, LibraryManager libraryManager) {
    IncludeDef includeDef = library.resolveIncludeRef(localLibraryName);
    return resolveLibrary(libraryManager, new VersionedIdentifier().withId(includeDef.getPath()).withVersion(includeDef.getVersion()));
  }

  private TranslatedLibrary resolveLibrary(LibraryManager libraryManager, VersionedIdentifier libraryIdentifier) {
    if (libraryManager.getTranslatedLibraries().containsKey(libraryIdentifier.getId())) {
      return libraryManager.getTranslatedLibraries().get(libraryIdentifier.getId());
    }

    throw new IllegalArgumentException(String.format("Could not resolve reference to translated library %s", libraryIdentifier.getId()));
  }
}
