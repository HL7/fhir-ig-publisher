package org.hl7.fhir.igtools.publisher;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cqframework.cql.cql2elm.*;
import org.cqframework.cql.cql2elm.model.CompiledLibrary;
import org.cqframework.cql.cql2elm.model.Model;
import org.cqframework.cql.cql2elm.model.Version;
import org.cqframework.cql.cql2elm.quick.FhirLibrarySourceProvider;
import org.cqframework.cql.elm.requirements.fhir.DataRequirementsProcessor;
import org.cqframework.cql.elm.requirements.fhir.utilities.SpecificationLevel;
import org.cqframework.cql.elm.tracking.TrackBack;
import org.fhir.ucum.UcumService;
import org.hl7.cql.model.*;
import org.hl7.elm.r1.AccessModifier;
import org.hl7.elm.r1.Code;
import org.hl7.elm.r1.CodeDef;
import org.hl7.elm.r1.CodeRef;
import org.hl7.elm.r1.CodeSystemDef;
import org.hl7.elm.r1.CodeSystemRef;
import org.hl7.elm.r1.Concept;
import org.hl7.elm.r1.ConceptDef;
import org.hl7.elm.r1.ConceptRef;
import org.hl7.elm.r1.Expression;
import org.hl7.elm.r1.ExpressionDef;
import org.hl7.elm.r1.FunctionDef;
import org.hl7.elm.r1.IncludeDef;
import org.hl7.elm.r1.ParameterDef;
import org.hl7.elm.r1.Retrieve;
import org.hl7.elm.r1.UsingDef;
import org.hl7.elm.r1.ValueSetDef;
import org.hl7.elm.r1.ValueSetRef;
import org.hl7.elm.r1.VersionedIdentifier;
import org.hl7.elm_modelinfo.r1.ModelInfo;
import org.hl7.elm_modelinfo.r1.serializing.ModelInfoReaderFactory;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.r5.context.ILoggingService;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;

/**
 * What this system does
 *
 * Library: Processes CQL source files to:
 *     1. Compile the CQL and report any compilation issues
 *     2. Add the base-64 encoded CQL to the Library resource
 *     3. Add the base-64 encoded XML and/or JSON ELM to the Library resource
 *     4. Add inferred data requirements, parameters, and dependencies to the Library resource
 *
 * Measure: Process effective data requirements for the measure if the measure is using CQL-based libraries
 *
 * PlanDefinition: Process effective data requirements for the plan definition if it is using CQL-based libraries
 *
 * ActivityDefinition: Process effective data requirements for the activity definition if it is using CQL-based libraries
 *
 * Questionnaire: Process effective data requirements for the questionnaire if it is using CQL-based libraries
 *
 */
public class CqlSubSystem {

  /**
   * information about a cql file
   */
  public class CqlSourceFileInformation {
    private final String path;
    private CqlTranslatorOptions options;
    private VersionedIdentifier identifier;
    private byte[] elm;
    private byte[] jsonElm;
    private List<ValidationMessage> errors = new ArrayList<>();
    private List<DataRequirement> dataRequirements = new ArrayList<>();
    private List<RelatedArtifact> relatedArtifacts = new ArrayList<>();
    private List<ParameterDefinition> parameters = new ArrayList<>();
    public CqlSourceFileInformation(String path) {
        this.path = path;
    }
    public String getPath() {
        return path;
    }
    public CqlTranslatorOptions getOptions() {
        return options;
    }
    public void setOptions(CqlTranslatorOptions options) {
        this.options = options;
    }
    public VersionedIdentifier getIdentifier() {
      return identifier;
    }
    public void setIdentifier(VersionedIdentifier identifier) {
      this.identifier = identifier;
    }
    public byte[] getElm() {
      return elm;
    }
    public void setElm(byte[] elm) {
      this.elm = elm;
    }
    public byte[] getJsonElm() {
      return jsonElm;
    }
    public void setJsonElm(byte[] jsonElm) {
      this.jsonElm = jsonElm;
    }
    public List<ValidationMessage> getErrors() {
      return errors;
    }
    public List<DataRequirement> getDataRequirements() {
      return dataRequirements;
    }
    public List<RelatedArtifact> getRelatedArtifacts() {
      return relatedArtifacts;
    }
    public List<ParameterDefinition> getParameters() {
      return parameters;
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
      for (NpmPackage p : packages) {
        try {
          InputStream s = p.loadByCanonicalVersion(identifier.getSystem()+"/Library/"+identifier.getId(), identifier.getVersion());
          if (s != null) {
            Library l = reader.readLibrary(s);
            for (org.hl7.fhir.r5.model.Attachment a : l.getContent()) {
              if (a.getContentType() != null && a.getContentType().equals("text/cql")) {
                return new ByteArrayInputStream(a.getData());
              }
            }
          }
        } catch (IOException e) {
          logger.logMessage(String.format("Exceptions occurred attempting to load npm library source for %s", identifier.toString()));
        }
      }

      return null;
    }
  }

  public class NpmModelInfoProvider implements ModelInfoProvider {

    public ModelInfo load(ModelIdentifier modelIdentifier) {
      // VersionedIdentifier.id: Name of the model
      // VersionedIdentifier.system: Namespace for the model, as a URL
      // VersionedIdentifier.version: Version of the model
      for (NpmPackage p : packages) {
        try {
          VersionedIdentifier identifier = new VersionedIdentifier()
                  .withId(modelIdentifier.getId())
                  .withVersion(modelIdentifier.getVersion())
                  .withSystem(modelIdentifier.getSystem());

          if (identifier.getSystem() == null) {
            identifier.setSystem(p.canonical());
          }

          InputStream s = p.loadByCanonicalVersion(identifier.getSystem()+"/Library/"+identifier.getId()+"-ModelInfo", identifier.getVersion());
          if (s != null) {
            Library l = reader.readLibrary(s);
            for (org.hl7.fhir.r5.model.Attachment a : l.getContent()) {
              if (a.getContentType() != null && a.getContentType().equals("application/xml")) {
                // Do not set the URL to the package canonical, the model info may be loading from another package
                //if (modelIdentifier.getSystem() == null) {
                //  modelIdentifier.setSystem(identifier.getSystem());
                //}
                InputStream is = new ByteArrayInputStream(a.getData());
                ModelInfo mi = ModelInfoReaderFactory.getReader("application/xml").read(is);
                // Set the URL to the model url
                if (mi != null && mi.getUrl() != null && modelIdentifier.getSystem() == null) {
                  modelIdentifier.setSystem(mi.getUrl());
                }
                // Save this loaded model and the library from which it was loaded so that we can correctly report
                // the dependency
                loadedModels.put(identifier, mi);
                return mi;
              }
            }
          }
        } catch (IOException e) {
          logger.logMessage(String.format("Exceptions occurred attempting to load npm library for model %s", modelIdentifier.toString()));
        }
      }

      return null;
    }
  }

  /**
   * The Implementation Guide build supports multiple versions. This code runs as R5 code.
   * The library reader loads the library etc from the NpmPackage and returns an R5 library etc,
   * irrespective of what version the IG is
   */
  public interface ICqlResourceReader {
    public Library readLibrary(InputStream stream) throws FHIRFormatError, IOException;
    public Measure readMeasure(InputStream stream) throws FHIRFormatError, IOException;
    public PlanDefinition readPlanDefinition(InputStream stream) throws FHIRFormatError, IOException;
    public ActivityDefinition readActivityDefinition(InputStream stream) throws FHIRFormatError, IOException;
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
  private ICqlResourceReader reader;

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

  /**
   * Saved map of translated files by fully qualified file name.
   * Populated as the fileMap is emptied by the adjunct file system processing.
   * Both the fileMap and this map are searched when artifact processing needs
   * to find the source file information for a given library.
   */
  private Map<String, CqlSourceFileInformation> savedFileMap;

  /**
   * Saved map of processed model info. These are model infos that are
   * defined in the CQL directory (i.e. locally in this IG)
   */
  private Map<VersionedIdentifier, ModelInfo> models;


  /**
   * Saved map of loaded model info. These are model infos that were
   * loaded from dependencies.
   */
  private Map<VersionedIdentifier, ModelInfo> loadedModels;

  /**
   * The packageId for the implementation guide, used to construct a NamespaceInfo for the CQL translator
   * Libraries that don't specify a namespace will be built in this namespace
   * Libraries can specify a namespace, but must use this name to do it
   */
  private String packageId;

  /**
   * The canonical base of the IG, used to construct a NamespaceInfo for the CQL translator
   * Libraries translated in this IG will have this namespaceUri as their system
   * Library resources published in this IG will then have URLs of [canonicalBase]/Library/[libraryName]
   */
  private String canonicalBase;

  private NamespaceInfo namespaceInfo;

  public CqlSubSystem(List<NpmPackage> packages, List<String> folders, ICqlResourceReader reader, ILoggingService logger, UcumService ucumService, String packageId, String canonicalBase) {
    super();
    this.packages = packages;
    this.folders = folders;
    this.reader = reader;
    this.logger = logger;
    this.ucumService = ucumService;
    this.packageId = packageId;
    this.canonicalBase = canonicalBase;
    if (packageId != null && !packageId.isEmpty() && canonicalBase != null && !canonicalBase.isEmpty()) {
      this.namespaceInfo = new NamespaceInfo(packageId, canonicalBase);
    }
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
      models = new HashMap<>();
      loadedModels = new HashMap<>();
      determineCqfCommonDependency();
      determineUsingCqlDependency();

      // foreach folder
      for (String folder : folders) {
        translateFolder(folder);
      }
    }
    catch (Exception E) {
      logger.logMessage(String.format("Errors occurred attempting to translate CQL content: %s", E.getMessage()));
    }
  }

  /**
   * Return CqlSourceFileInformation for the given filename
   * @param filename Fully qualified name of the source file
   * @return
   */
  public CqlSourceFileInformation getFileInformation(String filename) {
    if (fileMap == null) {
      throw new IllegalStateException("CQL File map is not available, execute has not been called");
    }

    if (savedFileMap == null) {
        savedFileMap = new HashMap<String, CqlSourceFileInformation>();
    }

    if (!fileMap.containsKey(filename)) {
      for (Map.Entry<String, CqlSourceFileInformation> entry: fileMap.entrySet()) {
        if (filename.equalsIgnoreCase(entry.getKey())) {
          logger.logMessage(String.format("File with a similar name but different casing was found. File found: '%s'", entry.getKey()));
        }
      }
      return null;
    }

    savedFileMap.put(filename, fileMap.get(filename));
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

  public class CqlPublisherOptions {
    /**
     * Determines whether target model mapping should be corrected in the compiled ELM
     * This option controls whether Using declarations in the resulting ELM retain their
     * original URI and version, or if they get set to the targetUrl and targetVersion of the
     * model if the modelinfo has targetUrl.
     *
     * For example, USCore, 7.0.0, http://hl7.org/fhir/us/core (unmapped value, correctModelUrls true)
     * versus USCore, null, http://hl7.org/fhir (mapped to targetUrl/targetVersion)
     *
     * Note that this is due to the behavior of the applyTargetModelMaps function in the translator,
     * which sets the uri of the ModelInfo to the targetUrl of if it is specified (when the ModelInfo
     * is either Profile-Informed or Profile-Aware).
     */
    private boolean correctModelUrls = true;
    public boolean getCorrectModelUrls() {
      return correctModelUrls;
    }
    public void setCorrectModelUrls(boolean correctModelUrls) {
      this.correctModelUrls = correctModelUrls;
    }
  }

  public class CqlPublisherOptionsMapper {
    private static ObjectMapper om = new ObjectMapper();

    public static CqlPublisherOptions fromFile(String fileName) {
      FileReader fr = null;

      try {
        fr = new FileReader(fileName);
        return fromReader(fr);
      } catch (IOException e) {
        throw new RuntimeException(String.format("Errors occurred reading options: %s", e.getMessage()));
      }
    }

    public static CqlPublisherOptions fromReader(Reader reader) {
      try {
        return (CqlPublisherOptions)om.readValue(reader, CqlPublisherOptions.class);
      } catch (IOException e) {
        throw new RuntimeException(String.format("Errors occurred reading options: %s", e.getMessage()));
      }
    }

    public static void toFile(String fileName, CqlPublisherOptions options) {
      FileWriter fw = null;

      try {
        fw = new FileWriter(fileName);
        toWriter(fw, options);
      } catch (IOException e) {
        throw new RuntimeException(String.format("Errors occurred writing options: %s", e.getMessage()));
      }
    }

    public static void toWriter(Writer writer, CqlPublisherOptions options) {
      ObjectMapper om = new ObjectMapper();

      try {
        om.writeValue(writer, options);
      } catch (IOException e) {
        throw new RuntimeException(String.format("Errors occurred writing options: %s", e.getMessage()));
      }
    }
  }

  /**
   * Reads publisher configuration file named cql-publisher-options.json from the given folder if present.
   * @param folder
   * @return
   */
  private CqlPublisherOptions getPublisherCqlOptions(String folder) {
    String optionsFileName = folder + File.separator + "cql-publisher-options.json";
    CqlPublisherOptions options = null;
    File file = new File(optionsFileName);
    if (file.exists()) {
      options = CqlPublisherOptionsMapper.fromFile(optionsFileName);
    }
    else {
      options = new CqlPublisherOptions();
    }

    return options;
  }

  /**
   * Reads configuration file named cql-options.json from the given folder if present. Otherwise returns default options.
   * @param folder
   * @return
   */
  private CqlTranslatorOptions getTranslatorOptions(String folder) {
    String optionsFileName = folder + File.separator + "cql-options.json";
    CqlTranslatorOptions options = null;
    File file = new File(optionsFileName);
    if (file.exists()) {
      options = CqlTranslatorOptionsMapper.fromFile(file.getAbsolutePath());
    }
    else {
      options = CqlTranslatorOptions.defaultOptions();
    }

    return options;
  }

  private void checkCachedManager() {
    if (cachedOptions == null) {
      if (hasMultipleBinaryPaths) {
        throw new RuntimeException("CqlProcessor has been used with multiple Cql paths, ambiguous options and manager");
      }
      else {
        throw new RuntimeException("CqlProcessor has not been executed, no cached options or manager");
      }
    }
  }

  private boolean hasMultipleBinaryPaths = false;
  private CqlTranslatorOptions cachedOptions;
  private CqlTranslatorOptions getCqlTranslatorOptions() {
    checkCachedManager();
    return cachedOptions;
  }

  private LibraryManager cachedLibraryManager;
  private LibraryManager getLibraryManager() {
    checkCachedManager();
    return cachedLibraryManager;
  }

  private CqlPublisherOptions cachedPublisherOptions;
  private CqlPublisherOptions getPublisherOptions() {
    checkCachedManager();
    return cachedPublisherOptions;
  }

  private boolean hasCqfCommonDependency = false;
  public boolean getHasCqfCommonDependency() {
    return hasCqfCommonDependency;
  }

  private void determineCqfCommonDependency() {
    hasCqfCommonDependency = false;
    for (NpmPackage p : packages) {
      if (p.id().equals("fhir.cqf.common") && p.version().equals("4.0.1")) {
        hasCqfCommonDependency = true;
        break;
      }
    }
  }

  private boolean hasUsingCqlDependency = false;
  public boolean getHasUsingCqlDependency() {
    return hasUsingCqlDependency;
  }

  private void determineUsingCqlDependency() {
    hasUsingCqlDependency = false;
    for (NpmPackage p : packages) {
      if (p.id().equals("hl7.fhir.uv.cql")) {
        Version v = new Version(p.version());
        if (v.getMajorVersion() >= 2) {
          hasUsingCqlDependency = true;
          break;
        }
      }
    }
  }

  private void translateFolder(String folder) {
    logger.logMessage(String.format("Translating CQL source in folder %s", folder));

    CqlTranslatorOptions options = getTranslatorOptions(folder);
    CqlPublisherOptions publisherOptions = getPublisherCqlOptions(folder);

    // Setup
    // Construct DefaultLibrarySourceProvider
    // Construct FhirLibrarySourceProvider
    ModelManager modelManager = new ModelManager();
    modelManager.getModelInfoLoader().registerModelInfoProvider(new NpmModelInfoProvider());
    modelManager.getModelInfoLoader().registerModelInfoProvider(new DefaultModelInfoProvider(Paths.get(folder)));

    LibraryManager libraryManager = new LibraryManager(modelManager, options.getCqlCompilerOptions());
    libraryManager.getLibrarySourceLoader().registerProvider(new NpmLibrarySourceProvider());
    libraryManager.getLibrarySourceLoader().registerProvider(new DefaultLibrarySourceProvider(Paths.get(folder)));
    libraryManager.getLibrarySourceLoader().registerProvider(new FhirLibrarySourceProvider());

    loadNamespaces(libraryManager);

    boolean hadCqlFiles = false;
    // foreach *-modelinfo* file (need to process all the models first so that they are available for dependency detection)
    for (File file : new File(folder).listFiles(getModelInfoFilenameFilter())) {
      hadCqlFiles = true;
      processModelInfoFile(modelManager, libraryManager, file, options);
    }

    // foreach *.cql file
    for (File file : new File(folder).listFiles(getCqlFilenameFilter())) {
      hadCqlFiles = true;
      translateFile(modelManager, libraryManager, file, options, publisherOptions);
    }

    if (hadCqlFiles) {
      if (cachedOptions == null) {
        if (!hasMultipleBinaryPaths) {
          cachedOptions = options;
          cachedLibraryManager = libraryManager;
          cachedPublisherOptions = publisherOptions;
        }
      }
      else {
        if (!hasMultipleBinaryPaths) {
          hasMultipleBinaryPaths = true;
          cachedOptions = null;
          cachedLibraryManager = null;
          cachedPublisherOptions = null;
        }
      }
    }
  }

  private void loadNamespaces(LibraryManager libraryManager) {
    if (namespaceInfo != null) {
      libraryManager.getNamespaceManager().ensureNamespaceRegistered(namespaceInfo);
    }

    for (NpmPackage p : packages) {
      if (p.name() != null && !p.name().isEmpty() && p.canonical() != null && !p.canonical().isEmpty()) {
        NamespaceInfo ni = new NamespaceInfo(p.name(), p.canonical());
        if (libraryManager.getNamespaceManager().resolveNamespaceUri(ni.getName()) != null) {
          logger.logMessage(String.format("Skipped loading namespace info for name %s because it is already registered", ni.getName()));
        }
        else if (libraryManager.getNamespaceManager().getNamespaceInfoFromUri(ni.getUri()) != null) {
          logger.logMessage(String.format("Skipped loading namespace infor for uri %s because it is already registered", ni.getUri()));
        }
        else {
          libraryManager.getNamespaceManager().ensureNamespaceRegistered(ni);
        }
      }
    }
  }

  private ValidationMessage.IssueType severityToIssueType(CqlCompilerException.ErrorSeverity severity) {
    switch (severity) {
      case Info: return ValidationMessage.IssueType.INFORMATIONAL;
      case Warning:
      case Error: return ValidationMessage.IssueType.PROCESSING;
      default: return ValidationMessage.IssueType.UNKNOWN;
    }
  }

  private ValidationMessage.IssueSeverity severityToIssueSeverity(CqlCompilerException.ErrorSeverity severity) {
    switch (severity) {
      case Info: return ValidationMessage.IssueSeverity.INFORMATION;
      case Warning: return ValidationMessage.IssueSeverity.WARNING;
      case Error: return ValidationMessage.IssueSeverity.ERROR;
      default: return ValidationMessage.IssueSeverity.NULL;
    }
  }

  private ValidationMessage exceptionToValidationMessage(File file, CqlCompilerException exception) {
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

  private CqlSourceFileInformation currentInfo = null;

  private void addError(ValidationMessage.IssueType issueType, String message, ValidationMessage.IssueSeverity severity) {
    currentInfo.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, issueType, currentInfo.getPath(), message, severity));
  }

  private void processModelInfoFile(ModelManager modelManager, LibraryManager libraryManager, File file, CqlTranslatorOptions options) {
    logger.logMessage(String.format("Processing CQL ModelInfo in file %s", file.toString()));
    CqlSourceFileInformation result = new CqlSourceFileInformation(file.getAbsolutePath());
    fileMap.put(file.getAbsoluteFile().toString(), result);
    try {
      ModelInfo mi = null;
      if (file.getName().toLowerCase().endsWith(".xml")) {
        mi = ModelInfoReaderFactory.getReader("application/xml").read(file);
      }
      else if (file.getName().toLowerCase().endsWith(".json")) {
        mi = ModelInfoReaderFactory.getReader("application/json").read(file);
      }
      else {
        throw new IllegalArgumentException("Could not read model info from file.");
      }

      result.setIdentifier(new VersionedIdentifier().withId(mi.getName()).withSystem(mi.getUrl()).withVersion(mi.getVersion()));
      models.put(result.getIdentifier(), mi);

      // TODO: Perform further validation of the model info...

    }
    catch (Exception e) {
      result.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, IssueType.EXCEPTION, file.getName(), "CQL ModelInfo Processing failed with exception: "+e.getMessage(), IssueSeverity.ERROR));
    }
  }

  private void translateFile(ModelManager modelManager, LibraryManager libraryManager, File file, CqlTranslatorOptions options, CqlPublisherOptions publisherOptions) {
    logger.logMessage(String.format("Translating CQL source in file %s", file.toString()));
    CqlSourceFileInformation result = new CqlSourceFileInformation(file.getAbsolutePath());
    fileMap.put(file.getAbsoluteFile().toString(), result);
    currentInfo = result;

    try {
      if (options.getCqlCompilerOptions().getValidateUnits()) {
        libraryManager.setUcumService(ucumService);
      }

      // translate toXML
      CqlTranslator translator = CqlTranslator.fromFile(namespaceInfo, file, libraryManager);

      // record errors and warnings
      for (CqlCompilerException exception : translator.getExceptions()) {
        result.getErrors().add(exceptionToValidationMessage(file, exception));
      }

      if (translator.getErrors().size() > 0) {
        result.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, IssueType.EXCEPTION, file.getName(),
                String.format("CQL Processing failed with (%d) errors.", translator.getErrors().size()), IssueSeverity.ERROR));
        logger.logMessage(String.format("Translation failed with (%d) errors; see the error log for more information.", translator.getErrors().size()));
      }
      else {
        try {
          result.setOptions(options);
          result.setIdentifier(translator.toELM().getIdentifier());

          // Correct target model mapping based on publisher options
          if (publisherOptions.getCorrectModelUrls()) {
            correctModelUrls(libraryManager, translator.toELM());
          }

          // convert to base64 bytes
          if (options.getFormats().contains(CqlTranslatorOptions.Format.XML)) {
            result.setElm(translator.toXml().getBytes());
          }
          if (options.getFormats().contains(CqlTranslatorOptions.Format.JSON)) {
            result.setJsonElm(translator.toJson().getBytes());
          }

          // Add the translated library to the library manager (NOTE: This should be a "cacheLibrary" call on the LibraryManager, available in 1.5.3+)
          // Without this, the data requirements processor will try to load the current library, resulting in a re-translation
          CompiledLibrary compiledLibrary = translator.getTranslatedLibrary();
          libraryManager.getCompiledLibraries().put(compiledLibrary.getIdentifier(), compiledLibrary);

          // TODO: Report context, requires 1.5 translator (ContextDef)
          // NOTE: In STU3, only Patient context is supported

          // TODO: Report direct-reference codes

          // Extract relatedArtifact data (models, libraries, code systems, and value sets)
          result.relatedArtifacts.addAll(extractRelatedArtifacts(translator.toELM()));

          // Extract parameter data and validate result types are supported types
          result.parameters.addAll(extractParameters(translator.toELM()));

          // Extract dataRequirement data
          result.dataRequirements.addAll(extractDataRequirements(translator.toRetrieves(), translator.getTranslatedLibrary(), libraryManager));

          logger.logMessage("CQL translation completed successfully.");
        }
        catch (Exception ex) {
          logger.logMessage(String.format("CQL Translation succeeded for file: '%s', but ELM generation failed with the following error: %s", file.getAbsolutePath(), ex.getMessage()));
        }
      }
    }
    catch (Exception e) {
      result.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, IssueType.EXCEPTION, file.getName(), "CQL Processing failed with exception: "+e.getMessage(), IssueSeverity.ERROR));
    }
    finally {
        currentInfo = null;
    }
  }

  private FilenameFilter getModelInfoFilenameFilter() {
    return new FilenameFilter() {
      @Override
      public boolean accept(File path, String name) {
        return name.toLowerCase().contains("-modelinfo");
      }
    };
  }

  private FilenameFilter getCqlFilenameFilter() {
    return new FilenameFilter() {
      @Override
      public boolean accept(File path, String name) {
        return name.endsWith(".cql");
      }
    };
  }

  private int getMajorVersion(String version) {
    if (version == null || version.equals("")) {
      return -1;
    }

    int indexOf = version.indexOf(".");
    if (indexOf == -1) {
      return -1;
    }

    for (int i = 0; i < indexOf; i++) {
      if (!Character.isDigit(version.charAt(i))) {
        return -1;
      }
    }

    return Integer.parseUnsignedInt(version.substring(0, indexOf));
  }

  private Model getProcessedModel(LibraryManager libraryManager, String modelName) {
    List<Model> models = new ArrayList<>();
    for (var entry : libraryManager.getModelManager().getGlobalCache().entrySet()) {
      if (modelName.equals(entry.getKey().getId())) {
        models.add(entry.getValue());
      }
    }

    if (models.size() == 0) {
      return null;
    }

    return models.get(0);
  }

  private Model getLoadedModel(LibraryManager libraryManager, String modelName) {
    List<Model> models = new ArrayList<>();
    for (var entry : libraryManager.getModelManager().getGlobalCache().entrySet()) {
      if (modelName.equals(entry.getKey().getId())) {
        models.add(entry.getValue());
      }
    }

    if (models.size() == 0) {
      return null;
    }

    return models.get(0);
  }

  private void correctModelUrls(LibraryManager libraryManager, org.hl7.elm.r1.Library library) {
    if (library.getUsings() != null && !library.getUsings().getDef().isEmpty()) {
      for (UsingDef def : library.getUsings().getDef()) {
        if (def.getLocalIdentifier() != null) {
          Model m = getLoadedModel(libraryManager, def.getLocalIdentifier());
          if (m != null) {
            def.setUri(m.getModelInfo().getUrl());
            def.setVersion(m.getModelInfo().getVersion());
          }
        }
      }
    }
  }

  private List<RelatedArtifact> extractRelatedArtifacts(org.hl7.elm.r1.Library library) {
    List<RelatedArtifact> result = new ArrayList<>();

    // Report model dependencies
    // URL for a model info is: [baseCanonical]/Library/[model-name]-ModelInfo
    if (library.getUsings() != null && !library.getUsings().getDef().isEmpty()) {
      for (UsingDef def : library.getUsings().getDef()) {
        // System model info is an implicit dependency, do not report
        if (!def.getLocalIdentifier().equals("System")) {
          // FHIR model info included from the translator is implicit, do not report
          if (def.getLocalIdentifier().equals("FHIR") && "http://hl7.org/fhir".equals(def.getUri())) {
            if (getMajorVersion(def.getVersion()) <= 4) {
              continue;
            }
          }

          // USCore model info 6.1.0 and prior included from the translator is implicit, do not report
          if (def.getLocalIdentifier().equals("USCore")) {
            if (getMajorVersion(def.getVersion()) <= 6) {
              continue;
            }
          }

          // QICore model info 6.0.0 and prior included from the translator is implicit, do not report
          if (def.getLocalIdentifier().equals("QICore")) {
            if (getMajorVersion(def.getVersion()) <= 6) {
              continue;
            }
          }

          result.add(toRelatedArtifact(def));
        }
      }
    }

    // Report library dependencies
    if (library.getIncludes() != null && !library.getIncludes().getDef().isEmpty()) {
      for (IncludeDef def : library.getIncludes().getDef()) {
        // System library is an implicit dependency, do not report
        if (!def.getLocalIdentifier().equals("System")) {
          // FHIR Helpers included from the translator is implicit, do not report
          if (!(def.getPath().equals("http://hl7.org/fhir/FHIRHelpers"))) {
            result.add(toRelatedArtifact(def));
          }
        }
      }
    }

    // Report CodeSystem dependencies
    if (library.getCodeSystems() != null && !library.getCodeSystems().getDef().isEmpty()) {
      for (CodeSystemDef def : library.getCodeSystems().getDef()) {
        result.add(toRelatedArtifact(def));
      }
    }

    // Report ValueSet dependencies
    if (library.getValueSets() != null && !library.getValueSets().getDef().isEmpty()) {
      for (ValueSetDef def : library.getValueSets().getDef()) {
        result.add(toRelatedArtifact(def));
      }
    }

    return result;
  }

  private List<ParameterDefinition> extractParameters(org.hl7.elm.r1.Library library) {
    List<ParameterDefinition> result = new ArrayList<>();

    if (library.getParameters() != null && !library.getParameters().getDef().isEmpty()) {
      for (ParameterDef def : library.getParameters().getDef()) {
        result.add(toParameterDefinition(def));
      }
    }

    if (library.getStatements() != null && !library.getStatements().getDef().isEmpty()) {
      for (ExpressionDef def : library.getStatements().getDef()) {
        if (!(def instanceof FunctionDef) && (def.getAccessLevel() == null || def.getAccessLevel() == AccessModifier.PUBLIC)) {
          result.add(toOutputParameterDefinition(def));
        }
      }
    }

    return result;
  }

  private List<DataRequirement> extractDataRequirements(List<org.hl7.elm.r1.Retrieve> retrieves, CompiledLibrary library, LibraryManager libraryManager) {
    List<DataRequirement> result = new ArrayList<>();

    for (Retrieve retrieve : retrieves) {
      result.add(toDataRequirement(retrieve, library, libraryManager));
    }

    return result;
  }

  private org.hl7.fhir.r5.model.RelatedArtifact toRelatedArtifact(UsingDef usingDef) {
    return new org.hl7.fhir.r5.model.RelatedArtifact()
            .setType(RelatedArtifact.RelatedArtifactType.DEPENDSON)
            .setDisplay("Model " + usingDef.getLocalIdentifier())
            .setResource(getModelInfoReferenceUrl(usingDef.getUri(), usingDef.getLocalIdentifier(), usingDef.getVersion()));
  }

  private String getModelInfoReferenceUrl(String uri, String name, String version) {
    // If the model is defined in this IG
    for (var m : models.values()) {
      if (name.equals(m.getName())) {
        return String.format("%s/Library/%s-ModelInfo%s", canonicalBase, name, version != null ? ("|" + version) : "");
      }
    }

    // If the model was loaded from a dependency, report it from that dependency
    for (var entry : loadedModels.entrySet()) {
      if (entry.getKey().getId().equals(name)) {
        return String.format("%s/Library/%s-ModelInfo%s",
            entry.getKey().getSystem(),
            entry.getKey().getId(),
            entry.getKey().getVersion() != null ? ("|" + entry.getKey().getVersion()) : "");
      }
    }

    if (uri != null) {
      return String.format("%s/Library/%s-ModelInfo%s", uri, name, version != null ? ("|" + version) : "");
    }

    return String.format("%s/Library/%s-ModelInfo%s", canonicalBase, name, version != null ? ("|" + version) : "");
  }

  private org.hl7.fhir.r5.model.RelatedArtifact toRelatedArtifact(IncludeDef includeDef) {
    return new org.hl7.fhir.r5.model.RelatedArtifact()
            .setType(org.hl7.fhir.r5.model.RelatedArtifact.RelatedArtifactType.DEPENDSON)
            .setDisplay(includeDef.getLocalIdentifier() != null ? "Library " + includeDef.getLocalIdentifier() : null)
            .setResource(getReferenceUrl(includeDef.getPath(), includeDef.getVersion()));
  }

  private String getReferenceUrl(String path, String version) {
    String uri = NamespaceManager.getUriPart(path);
    String name = NamespaceManager.getNamePart(path);

    if (uri != null) {
      return String.format("%s/Library/%s%s", uri, name, version != null ? ("|" + version) : "");
    }

    return String.format("Library/%s%s", path, version != null ? ("|" + version) : "");
  }

  private org.hl7.fhir.r5.model.RelatedArtifact toRelatedArtifact(CodeSystemDef codeSystemDef) {
    return new org.hl7.fhir.r5.model.RelatedArtifact()
            .setType(org.hl7.fhir.r5.model.RelatedArtifact.RelatedArtifactType.DEPENDSON)
            .setDisplay("Code System " + codeSystemDef.getName())
            .setResource(toReference(codeSystemDef));
  }

  private org.hl7.fhir.r5.model.RelatedArtifact toRelatedArtifact(ValueSetDef valueSetDef) {
    return new org.hl7.fhir.r5.model.RelatedArtifact()
            .setType(org.hl7.fhir.r5.model.RelatedArtifact.RelatedArtifactType.DEPENDSON)
            .setDisplay("Value Set " + valueSetDef.getName())
            .setResource(toReference(valueSetDef));
  }

  private ParameterDefinition toParameterDefinition(ParameterDef def) {
    org.hl7.cql.model.DataType parameterType = def.getResultType() instanceof ListType ? ((ListType)def.getResultType()).getElementType() : def.getResultType();

    AtomicBoolean isList = new AtomicBoolean(false);
    Enumerations.FHIRTypes typeCode = Enumerations.FHIRTypes.fromCode(toFHIRParameterTypeCode(parameterType, def.getName(), isList));

    return new ParameterDefinition()
            .setName(def.getName())
            .setUse(Enumerations.OperationParameterUse.IN)
            .setMin(0)
            .setMax(isList.get() ? "*" : "1")
            .setType(typeCode);
  }

  private ParameterDefinition toOutputParameterDefinition(ExpressionDef def) {
    AtomicBoolean isList = new AtomicBoolean(false);
    Enumerations.FHIRTypes typeCode = Enumerations.FHIRTypes.fromCode(toFHIRResultTypeCode(def.getResultType(), def.getName(), isList));

    return new ParameterDefinition()
            .setName(def.getName())
            .setUse(Enumerations.OperationParameterUse.OUT)
            .setMin(0)
            .setMax(isList.get() ? "*" : "1")
            .setType(typeCode);
  }

  private String toFHIRResultTypeCode(org.hl7.cql.model.DataType dataType, String defName, AtomicBoolean isList) {
    AtomicBoolean isValid = new AtomicBoolean(true);
    String resultCode = toFHIRTypeCode(dataType, isValid, isList);
    if (!isValid.get()) {
      // Issue a warning that the result type is not supported
      addError(IssueType.NOTSUPPORTED,
              String.format("Result type %s of definition %s is not supported; implementations may not be able to use the result of this expression",
                      dataType.toLabel(), defName),
              IssueSeverity.WARNING
      );
    }

    return resultCode;
  }

  private String toFHIRParameterTypeCode(org.hl7.cql.model.DataType dataType, String parameterName, AtomicBoolean isList) {
    AtomicBoolean isValid = new AtomicBoolean(true);
    String resultCode = toFHIRTypeCode(dataType, isValid, isList);
    if (!isValid.get()) {
      // Issue a warning that the parameter type is not supported
      addError(IssueType.NOTSUPPORTED,
              String.format("Parameter type %s of parameter %s is not supported; reported as FHIR.Any", dataType.toLabel(), parameterName),
              IssueSeverity.WARNING
      );
    }

    return resultCode;
  }

  private String toFHIRTypeCode(org.hl7.cql.model.DataType dataType, AtomicBoolean isValid, AtomicBoolean isList) {
    isList.set(false);
    if (dataType instanceof ListType) {
      isList.set(true);
      return toFHIRTypeCode(((ListType)dataType).getElementType(), isValid);
    }

    return toFHIRTypeCode(dataType, isValid);
  }

  private String toFHIRTypeCode(org.hl7.cql.model.DataType dataType, AtomicBoolean isValid) {
    isValid.set(true);
    if (dataType instanceof NamedType) {
      switch (((NamedType)dataType).getName()) {
        case "System.Boolean": return "boolean";
        case "System.Integer": return "integer";
        case "System.Decimal": return "decimal";
        case "System.Date": return "date";
        case "System.DateTime": return "dateTime";
        case "System.Time": return "time";
        case "System.String": return "string";
        case "System.Quantity": return "Quantity";
        case "System.Ratio": return "Ratio";
        case "System.Any": return "Any";
        case "System.Code": return "Coding";
        case "System.Concept": return "CodeableConcept";
      }

      if ("FHIR".equals(((NamedType)dataType).getNamespace())) {
        return ((NamedType)dataType).getSimpleName();
      }
    }

    if (dataType instanceof IntervalType) {
      if (((IntervalType)dataType).getPointType() instanceof NamedType) {
        switch (((NamedType)((IntervalType)dataType).getPointType()).getName()) {
          case "System.Date":
          case "System.DateTime": return "Period";
          case "System.Quantity": return "Range";
        }
      }
    }

    isValid.set(false);
    return "Any";
  }

  private org.hl7.fhir.r5.model.DataRequirement toDataRequirement(Retrieve retrieve, CompiledLibrary library, LibraryManager libraryManager) {
    org.hl7.fhir.r5.model.DataRequirement dr = new org.hl7.fhir.r5.model.DataRequirement();

    dr.setType(org.hl7.fhir.r5.model.Enumerations.FHIRTypes.fromCode(retrieve.getDataType().getLocalPart()));

    // Set profile if specified
    if (retrieve.getTemplateId() != null) {
      dr.setProfile(Collections.singletonList(new org.hl7.fhir.r5.model.CanonicalType(retrieve.getTemplateId())));
    }

    // Set code path if specified
    if (retrieve.getCodeProperty() != null) {
      org.hl7.fhir.r5.model.DataRequirement.DataRequirementCodeFilterComponent cfc =
              new org.hl7.fhir.r5.model.DataRequirement.DataRequirementCodeFilterComponent();

      cfc.setPath(retrieve.getCodeProperty());

      // TODO: Support retrieval when the target is a CodeSystemRef

      if (retrieve.getCodes() instanceof ValueSetRef) {
        ValueSetRef vsr = (ValueSetRef)retrieve.getCodes();
        cfc.setValueSet(toReference(resolveValueSetRef(vsr, new ResolutionContext(libraryManager, library))));
      }

      if (retrieve.getCodes() instanceof org.hl7.elm.r1.ToList) {
        org.hl7.elm.r1.ToList toList = (org.hl7.elm.r1.ToList)retrieve.getCodes();
        resolveCodeFilterCodes(cfc, toList.getOperand(), library, libraryManager);
      }

      if (retrieve.getCodes() instanceof org.hl7.elm.r1.List) {
        org.hl7.elm.r1.List codeList = (org.hl7.elm.r1.List)retrieve.getCodes();
        for (Expression e : codeList.getElement()) {
          resolveCodeFilterCodes(cfc, e, library, libraryManager);
        }
      }

      dr.getCodeFilter().add(cfc);
    }

    // TODO: Set date range filters if literal

    return dr;
  }

  private void resolveCodeFilterCodes(org.hl7.fhir.r5.model.DataRequirement.DataRequirementCodeFilterComponent cfc, Expression e,
                                      CompiledLibrary library, LibraryManager libraryManager) {
    if (e instanceof org.hl7.elm.r1.CodeRef) {
      CodeRef cr = (CodeRef)e;
      ResolutionContext context = new ResolutionContext(libraryManager, library);
      cfc.addCode(toCoding(toCode(resolveCodeRef(cr, context)), context));
    }

    if (e instanceof org.hl7.elm.r1.Code) {
      cfc.addCode(toCoding((org.hl7.elm.r1.Code)e, new ResolutionContext(libraryManager, library)));
    }

    if (e instanceof org.hl7.elm.r1.ConceptRef) {
      ConceptRef cr = (ConceptRef)e;
      ResolutionContext context = new ResolutionContext(libraryManager, library);
      org.hl7.fhir.r5.model.CodeableConcept c = toCodeableConcept(toConcept(resolveConceptRef(cr, context), context), context);
      for (org.hl7.fhir.r5.model.Coding code : c.getCoding()) {
        cfc.addCode(code);
      }
    }

    if (e instanceof org.hl7.elm.r1.Concept) {
      org.hl7.fhir.r5.model.CodeableConcept c = toCodeableConcept((org.hl7.elm.r1.Concept)e, new ResolutionContext(libraryManager, library));
      for (org.hl7.fhir.r5.model.Coding code : c.getCoding()) {
        cfc.addCode(code);
      }
    }
  }

  private org.hl7.fhir.r5.model.Coding toCoding(Code code, ResolutionContext context) {
    CodeSystemDef codeSystemDef = resolveCodeSystemRef(code.getSystem(), context);
    org.hl7.fhir.r5.model.Coding coding = new org.hl7.fhir.r5.model.Coding();
    coding.setCode(code.getCode());
    coding.setDisplay(code.getDisplay());
    if (codeSystemDef != null) {
        coding.setSystem(codeSystemDef.getId());
        coding.setVersion(codeSystemDef.getVersion());
    }
    else {
        // Message that the code system reference could not be resolved
        addError(IssueType.PROCESSING,
                String.format("Code system reference %s could not be resolved", code.getSystem()),
                IssueSeverity.ERROR
        );
        coding.setSystem(code.getSystem().toString());
    }
    return coding;
  }

  private org.hl7.fhir.r5.model.CodeableConcept toCodeableConcept(Concept concept, ResolutionContext context) {
    org.hl7.fhir.r5.model.CodeableConcept codeableConcept = new org.hl7.fhir.r5.model.CodeableConcept();
    codeableConcept.setText(concept.getDisplay());
    for (Code code : concept.getCode()) {
      codeableConcept.addCoding(toCoding(code, new ResolutionContext(context.getLibraryManager(), context.getLibrary())));
    }
    return codeableConcept;
  }

  private String toReference(CodeSystemDef codeSystemDef) {
    return codeSystemDef.getId() + (codeSystemDef.getVersion() != null ? ("|" + codeSystemDef.getVersion()) : "");
  }

  private String toReference(ValueSetDef valueSetDef) {
    return valueSetDef.getId() + (valueSetDef.getVersion() != null ? ("|" + valueSetDef.getVersion()) : "");
  }

  // TODO: Move to the CQL-to-ELM translator

  private org.hl7.elm.r1.Concept toConcept(ConceptDef conceptDef, ResolutionContext context) {
    org.hl7.elm.r1.Concept concept = new org.hl7.elm.r1.Concept();
    concept.setDisplay(conceptDef.getDisplay());
    for (org.hl7.elm.r1.CodeRef codeRef : conceptDef.getCode()) {
      concept.getCode().add(toCode(resolveCodeRef(codeRef, new ResolutionContext(context.getLibraryManager(), context.getLibrary()))));
    }
    return concept;
  }

  private org.hl7.elm.r1.Code toCode(CodeDef codeDef) {
    return new org.hl7.elm.r1.Code().withCode(codeDef.getId()).withSystem(codeDef.getCodeSystem()).withDisplay(codeDef.getDisplay());
  }

  private class ResolutionContext {
      private final LibraryManager libraryManager;
      private CompiledLibrary library;
      public ResolutionContext(LibraryManager libraryManager, CompiledLibrary library) {
          this.libraryManager = libraryManager;
          this.library = library;
      }
      public LibraryManager getLibraryManager() {
          return libraryManager;
      }
      public CompiledLibrary getLibrary() {
          return library;
      }
      public void setLibrary(CompiledLibrary library) {
          this.library = library;
      }
  }

  private org.hl7.elm.r1.CodeDef resolveCodeRef(CodeRef codeRef, ResolutionContext context) {
    // If the reference is to another library, resolve to that library
    if (codeRef.getLibraryName() != null) {
      context.setLibrary(resolveLibrary(codeRef.getLibraryName(), context));
    }

    return context.getLibrary().resolveCodeRef(codeRef.getName());
  }

  private org.hl7.elm.r1.ConceptDef resolveConceptRef(ConceptRef conceptRef, ResolutionContext context) {
    // If the reference is to another library, resolve to that library
    if (conceptRef.getLibraryName() != null) {
      context.setLibrary(resolveLibrary(conceptRef.getLibraryName(), context));
    }

    return context.getLibrary().resolveConceptRef(conceptRef.getName());
  }

  private CodeSystemDef resolveCodeSystemRef(CodeSystemRef codeSystemRef, ResolutionContext context) {
    if (codeSystemRef.getLibraryName() != null) {
      context.setLibrary(resolveLibrary(codeSystemRef.getLibraryName(), context));
    }

    return context.getLibrary().resolveCodeSystemRef(codeSystemRef.getName());
  }

  private ValueSetDef resolveValueSetRef(ValueSetRef valueSetRef, ResolutionContext context) {
    // If the reference is to another library, resolve to that library
    if (valueSetRef.getLibraryName() != null) {
      context.setLibrary(resolveLibrary(valueSetRef.getLibraryName(), context));
    }

    return context.getLibrary().resolveValueSetRef(valueSetRef.getName());
  }

  private CompiledLibrary resolveLibrary(String localLibraryName, ResolutionContext context) {
    IncludeDef includeDef = context.getLibrary().resolveIncludeRef(localLibraryName);
    return resolveLibrary(context.getLibraryManager(), new VersionedIdentifier()
            .withId(NamespaceManager.getNamePart(includeDef.getPath()))
            .withSystem(NamespaceManager.getUriPart(includeDef.getPath()))
            .withVersion(includeDef.getVersion()));
  }

  private CompiledLibrary resolveLibrary(LibraryManager libraryManager, VersionedIdentifier libraryIdentifier) {
    return libraryManager.resolveLibrary(libraryIdentifier);
  }

  /**
   * return true if the resource is modified
   * @param f for errors
   * @param resource
   * @return
   */
  public boolean processArtifact(FetchedFile f, Resource resource) {
    try {
      if (resource instanceof Measure
              || resource instanceof ActivityDefinition
              || resource instanceof PlanDefinition
              || resource instanceof Questionnaire) {
        processEffectiveDataRequirements(f, (DomainResource)resource);
        return true;
      }
    }
    catch (Exception e) {
      f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, IssueType.PROCESSING,
              f.getPath(), e.getMessage(), IssueSeverity.WARNING));
      f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, IssueType.PROCESSING,
              f.getPath(), "Exceptions occurred attempting to process effective data requirements",
              IssueSeverity.WARNING));
    }

    return false;
  }

  private CqlSourceFileInformation getCqlInfo(String libraryId) {
    if (fileMap != null) {
      for (var info : fileMap.values()) {
        if (info.identifier != null && info.identifier.getId() != null && info.identifier.getId().equals(libraryId)) {
          return info;
        }
      }
    }

    if (savedFileMap != null) {
      for (var info : savedFileMap.values()) {
        if (info.identifier != null && info.identifier.getId() != null && info.identifier.getId().equals(libraryId)) {
          return info;
        }
      }
    }

    return null;
  }

  private CqlSourceFileInformation getLibraryInfo(FetchedFile f, DomainResource r) {
    if (r instanceof Measure) {
      return getLibraryInfo(f, ((Measure)r).getLibrary());
    }
    else if (r instanceof ActivityDefinition) {
      return getLibraryInfo(f, ((ActivityDefinition)r).getLibrary());
    }
    else if (r instanceof PlanDefinition) {
      return getLibraryInfo(f, ((PlanDefinition)r).getLibrary());
    }
    else if (r instanceof Questionnaire) {
      return getLibraryInfo(f, r.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/cqf-library").stream().map(Extension::getValueCanonicalType).toList());
    }
    else {
      return null;
    }
  }

  private CqlSourceFileInformation getLibraryInfo(FetchedFile f, List<CanonicalType> libraries) {
    // Skip if there are no libraries
    if (libraries == null || libraries.isEmpty()) {
      return null;
    }

    // Skip and inform that effective data requirements processing will not be performed if there are multiple libraries
    if (libraries.size() > 1) {
      f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, IssueType.PROCESSING,
          f.getPath(), "Artifact has multiple libraries so no effective data requirements were inferred.",
          IssueSeverity.INFORMATION));
      return null;
    }

    // Get the compiled measure library
    CqlSourceFileInformation libraryInfo = null;
    for (var canonical : libraries) {
      libraryInfo = getCqlInfo(Utilities.tail(canonical.getCanonical()));
      if (libraryInfo != null) {
        break;
      }
    }

    // If it was not successfully compiled, log a warning that measure data requirements could not be inferred
    if (libraryInfo == null) {
      f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, IssueType.PROCESSING,
          f.getPath(), "Artifact library could not be found so no data requirements were inferred.",
          IssueSeverity.WARNING));
    }

    return libraryInfo;
  }

  private void processEffectiveDataRequirements(FetchedFile f, DomainResource r) {
    CqlSourceFileInformation libraryInfo = getLibraryInfo(f, r);

    if (libraryInfo != null) {
      // Build the module definition library
      DataRequirementsProcessor drp = new DataRequirementsProcessor();
      drp.setSpecificationLevel(SpecificationLevel.QM_STU_1);
      Library moduleDefinitionLibrary = getModuleDefinitionLibrary(f, r, drp, libraryInfo);
      attachModuleDefinitionLibrary(r, moduleDefinitionLibrary);

      // Report issues to the fetched file
      for (var message : drp.getValidationMessages()) {
        f.getErrors().add(message);
      }
    }
  }

  private void fixupFHIRReferences(FetchedFile f, Library library, CompiledLibrary compiledLibrary) {
    // Correct references to any reported models to the models and loadedModels tables (local ig and ig dependency models)
    for (var r : library.getRelatedArtifact()) {
      if (r.hasResource() && r.getResource().contains("/Library/") && r.getResource().contains("-ModelInfo")) {
        var tail = Utilities.urlTail(r.getResource());
        var uri = Utilities.extractBaseUrl(Utilities.extractBaseUrl(r.getResource()));
        var modelName = tail.substring(0, tail.indexOf("-"));
        var versionIndex = tail.indexOf("|");
        var version = versionIndex > 0 ? tail.substring(versionIndex + 1) : null;
        r.setResource(getModelInfoReferenceUrl(uri, modelName, version));
      }
    }

    // If the library still has a dependency on 'http://fhir.org/guides/cqf/common/Library/FHIR-ModelInfo'
    // and to 'http://hl7.org/fhir/Library/FHIRHelpers' then the guide is using CQFCommon, but
    // the dependency resolved to the translator-included FHIRHelpers, which doesn't resolve in the
    // IG. So correct the dependency to 'http://fhir.org/guides/cqf/common/Library/FHIRHelpers'
    RelatedArtifact cqfCommonModelInfo = null;
    for (var r : library.getRelatedArtifact()) {
      if (r.hasResource() && r.getResource().startsWith("http://fhir.org/guides/cqf/common/Library/FHIR-ModelInfo")) {
        cqfCommonModelInfo = r;
          break;
      }
    }

    if (cqfCommonModelInfo != null) {
      // Find the loaded model for FHIR
      // If there is one, use it, otherwise, remove the reported dependency (it is the implicit one loaded in the translator)
      VersionedIdentifier fhirModel = null;
      for (var entry : loadedModels.entrySet()) {
        if ("FHIR".equals(entry.getKey().getId())) {
          fhirModel = entry.getKey();
          break;
        }
      }

      if (fhirModel != null && fhirModel.getSystem() != null) {
        cqfCommonModelInfo.setResource(String.format("%s/Library/%s-ModelInfo%s",
            fhirModel.getSystem(),
            fhirModel.getId(),
            fhirModel.getVersion() != null ? "|" + fhirModel.getVersion() : ""
        ));

        for (var r : library.getRelatedArtifact()) {
          if (r.hasResource() && r.getResource().startsWith("http://hl7.org/fhir/Library/FHIRHelpers")) {
            if (r.getResource().contains("|")) {
              r.setResource(fhirModel.getSystem() + "/Library/FHIRHelpers|4.0.1");
            }
            else {
              r.setResource(fhirModel.getSystem() + "/Library/FHIRHelpers");
            }

            f.getErrors().add(
                new ValidationMessage(
                    ValidationMessage.Source.Publisher,
                    IssueType.PROCESSING,
                    f.getPath(),
                    "Artifact is using a package-loaded FHIR-ModelInfo, but the implicit FHIRHelpers. The dependency has been updated to use the FHIRHelpers from the same package.",
                    IssueSeverity.INFORMATION
                )
            );
          }
        }
      }
      else if (hasCqfCommonDependency) {
        // The IG declares a dependency on fhir.cqf.common#4.0.1
        // But order of providers loaded model info from the implicit translator
        // And then the data requirements processing assigned it to the CQF uri
        // So switch FHIRHelpers to the cqf common as well
        for (var r : library.getRelatedArtifact()) {
          if (r.hasResource() && r.getResource().startsWith("http://hl7.org/fhir/Library/FHIRHelpers")) {
            if (r.getResource().contains("|")) {
              r.setResource("http://fhir.org/guides/cqf/common/Library/FHIRHelpers|4.0.1");
            }
            else {
              r.setResource("http://fhir.org/guides/cqf/common/Library/FHIRHelpers");
            }

            f.getErrors().add(
                new ValidationMessage(
                    ValidationMessage.Source.Publisher,
                    IssueType.PROCESSING,
                    f.getPath(),
                    "Artifact is using CQF Common package, but the implicitly loaded FHIR-ModelInfo and FHIRHelpers. The dependencies have been updated to use the libraries from the CQF Common package.",
                    IssueSeverity.INFORMATION
                )
            );
          }
        }
      }
      else if (hasUsingCqlDependency) {
        // The IG declares a dependency on hl7.fhir.uv.cql#2.0.0+
        // But order of providers loaded model info from the implicit translator
        // And then the data requirements processing assigned it to the CQF uri
        // So switch the model back to the uv/cql dependency
        if (cqfCommonModelInfo.getResource().contains("|")) {
          cqfCommonModelInfo.setResource("http://hl7.org/fhir/uv/cql/Library/FHIR-ModelInfo|4.0.1");
        }
        else {
          cqfCommonModelInfo.setResource("http://hl7.org/fhir/uv/cql/Library/FHIR-ModelInfo");
        }

        f.getErrors().add(
            new ValidationMessage(
                ValidationMessage.Source.Publisher,
                IssueType.PROCESSING,
                f.getPath(),
                "Artifact is using Using CQL 2.0+ package, but the implicitly loaded FHIR-ModelInfo. The artifact dependencies have been updated to use the Using CQL 2.0+ dependency.",
                IssueSeverity.INFORMATION
            )
        );
      }
      else {
        // Remove the reported dependency (it is the implicit one loaded in the translator)
        library.getRelatedArtifact().remove(cqfCommonModelInfo);

        f.getErrors().add(
            new ValidationMessage(
                ValidationMessage.Source.Publisher,
                IssueType.PROCESSING,
                f.getPath(),
                "Artifact is using the implicitly loaded FHIR-ModelInfo and FHIRHelpers and is not referencing Using CQL version 2 or CQF Common version 4.0.1, so the model info dependency was removed. Consider using hl7.fhir.uv.cql#2.0.0 or fhir.cqf.common#4.0.1.",
                IssueSeverity.INFORMATION
            )
        );
      }
    }
  }

  private void attachModuleDefinitionLibrary(DomainResource r, Library moduleDefinitionLibrary) {
    // Remove extensions from QM IG STU2 or earlier
    r.getExtension().removeAll(r.getExtensionsByUrl(
        "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-directReferenceCode",
        "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-logicDefinition",
        "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-parameter",
        "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-dataRequirement"
    ));

    // Remove the existing effective data requirements library and extension if one is present
    r.getContained().removeIf(res -> res.getId().equals(getDataRequirementsLibraryId(r)));
    r.getExtension().removeAll(r.getExtensionsByUrl(
        "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-effectiveDataRequirements",
        "http://hl7.org/fhir/uv/crmi/StructureDefinition/crmi-effectiveDataRequirements"
    ));

    // Add the module effective data requirements library
    if (!moduleDefinitionLibrary.hasId()) {
      moduleDefinitionLibrary.setId("effective-data-requirements");
    }
    r.getContained().add(moduleDefinitionLibrary);
    r.getExtension().add(
        new Extension(
            "http://hl7.org/fhir/uv/crmi/StructureDefinition/crmi-effectiveDataRequirements",
            new CanonicalType("#" + moduleDefinitionLibrary.getId())
        )
    );
  }

  private String getDataRequirementsLibraryId(DomainResource r) {
    Extension edr = r.getExtensionByUrl("http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-effectiveDataRequirements");
    if (edr == null) {
      edr = r.getExtensionByUrl("http://hl7.org/fhir/uv/crmi/StructureDefinition/crmi-effectiveDataRequirements");
    }

    // Be as liberal as possible in how the effective data requirements reference is specified
    String reference = null;
    if (edr != null) {
      if (edr.hasValueStringType()) {
        reference = edr.getValueStringType().getValue();
      }
      else if (edr.hasValueCanonicalType()) {
        reference = edr.getValueCanonicalType().getValue();
      }
      else if (edr.hasValueUriType()) {
        reference =  edr.getValueUriType().getValue();
      }
      else if (edr.hasValueUrlType()) {
        reference = edr.getValueUrlType().getValue();
      }
      else if (edr.hasValueReference()) {
        reference = edr.getValueReference().getReference();
      }
    }

    if (reference != null && reference.startsWith("#")) {
      reference = reference.substring(1);
    }

    return reference;
  }

  private Library getModuleDefinitionLibrary(FetchedFile f, DomainResource r, DataRequirementsProcessor drp, CqlSourceFileInformation info) {
    Set<String> expressions = getExpressions(r);

    boolean annotationsEnabled = info.getOptions().getCqlCompilerOptions().getOptions().contains(CqlCompilerOptions.Options.EnableAnnotations);
    if (!annotationsEnabled) {
      drp.getValidationMessages().add(
          new ValidationMessage(ValidationMessage.Source.Publisher, IssueType.PROCESSING,
              info.getPath(), "Artifact library was not translated with annotations enabled, so the measure narrative will not include logic definitions.",
              IssueSeverity.INFORMATION)
      );
    }

    CompiledLibrary compiledLibrary = getLibraryManager().resolveLibrary(info.getIdentifier(), new ArrayList<>());
    Library moduleDefinitionLibrary = drp.gatherDataRequirements(
        getLibraryManager(),
        compiledLibrary,
        info.getOptions().getCqlCompilerOptions(),
        expressions,
        annotationsEnabled
    );

    fixupFHIRReferences(f, moduleDefinitionLibrary, compiledLibrary);

    return moduleDefinitionLibrary;
  }

  private boolean isExpressionIdentifier(org.hl7.fhir.r5.model.Expression expression) {
    return expression.hasLanguage() && expression.hasExpression()
        && (expression.getLanguage().equalsIgnoreCase("text/cql.identifier")
        || expression.getLanguage().equalsIgnoreCase("text/cql")
        || expression.getLanguage().equalsIgnoreCase("text/cql-identifier"));
  }

  private Set<String> getExpressions(DomainResource r) {
    if (r instanceof Measure) {
      return getMeasureExpressions((Measure) r);
    }
    else if (r instanceof PlanDefinition) {
      return getPlanDefinitionExpressions((PlanDefinition) r);
    }
    else if (r instanceof ActivityDefinition) {
      return getActivityDefinitionExpressions((ActivityDefinition) r);
    }
    else if (r instanceof Questionnaire) {
      return getQuestionnaireExpressions((Questionnaire) r);
    }
    else {
      return new HashSet<>();
    }
  }

  private void getPlanDefinitionActionExpressions(PlanDefinition.PlanDefinitionActionComponent action, Set<String> expressionSet) {
    for (var condition : action.getCondition()) {
      if (condition.hasExpression() && isExpressionIdentifier(condition.getExpression())) {
        expressionSet.add(condition.getExpression().getExpression());
      }
    }

    for (var dynamicValue : action.getDynamicValue()) {
      if (dynamicValue.hasExpression() && isExpressionIdentifier(dynamicValue.getExpression())) {
        expressionSet.add(dynamicValue.getExpression().getExpression());
      }
    }

    for (var childAction : action.getAction()) {
      getPlanDefinitionActionExpressions(childAction, expressionSet);
    }
  }

  private Set<String> getPlanDefinitionExpressions(PlanDefinition planDefinition) {
    Set<String> expressionSet = new HashSet<>();
    planDefinition.getAction().forEach(action -> {
      getPlanDefinitionActionExpressions(action, expressionSet);
    });
    return expressionSet;
  }

  private Set<String> getActivityDefinitionExpressions(ActivityDefinition activityDefinition) {
    Set<String> expressionSet = new HashSet<>();
    for (var dynamicValue : activityDefinition.getDynamicValue()) {
      if (dynamicValue.hasExpression() && isExpressionIdentifier(dynamicValue.getExpression())) {
        expressionSet.add(dynamicValue.getExpression().getExpression());
      }
    }
    return expressionSet;
  }

  private void getQuestionnaireExtensionExpressions(List<Extension> extensions, Set<String> expressions) {
    for (var extension : extensions) {
      if (Utilities.existsInList(extension.getUrl(),
          "http://hl7.org/fhir/StructureDefinition/cqf-expression",
          "http://hl7.org/fhir/StructureDefinition/cqf-calculatedExpression",
          "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-itemPopulationContext",
          "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-initialExpression",
          "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-candidaiteExpression",
          "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-contextExpression",
          "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-answerExpression",
          "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-enableWhenExpression"
      )) {
        if (isExpressionIdentifier(extension.getValueExpression())) {
          expressions.add(extension.getValueExpression().getExpression());
        }
      }
      else if ("http://hl7.org/fhir/StructureDefinition/cqf-calculatedValue".equals(extension.getUrl())) {
        expressions.add(extension.getValueStringType().getValue());
      }
    }
  }

  private Set<String> getQuestionnaireExpressions(Questionnaire questionnaire) {
    Set<String> expressionSet = new HashSet<>();
    getQuestionnaireExtensionExpressions(questionnaire.getExtension(), expressionSet);
    for (var item : questionnaire.getItem()) {
      getQuestionnaireExtensionExpressions(item.getExtension(), expressionSet);
    }
    return expressionSet;
  }

  private Set<String> getMeasureExpressions(Measure measure) {
    Set<String> expressionSet = new HashSet<>();
    measure.getSupplementalData().forEach(supData -> {
      if (supData.hasCriteria() && isExpressionIdentifier(supData.getCriteria())) {
        expressionSet.add(supData.getCriteria().getExpression());
      }
    });
    measure.getGroup().forEach(groupMember -> {
      groupMember.getPopulation().forEach(population -> {
        if (population.hasCriteria() && isExpressionIdentifier(population.getCriteria())) {
          expressionSet.add(population.getCriteria().getExpression());
        }
      });
      groupMember.getStratifier().forEach(stratifier -> {
        if (stratifier.hasCriteria() && isExpressionIdentifier(stratifier.getCriteria())) {
          expressionSet.add(stratifier.getCriteria().getExpression());
        }
      });
    });
    return expressionSet;
  }
}
