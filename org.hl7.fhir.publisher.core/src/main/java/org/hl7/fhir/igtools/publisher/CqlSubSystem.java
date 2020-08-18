package org.hl7.fhir.igtools.publisher;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.CqlTranslatorException;
import org.cqframework.cql.cql2elm.CqlTranslatorOptions;
import org.cqframework.cql.cql2elm.CqlTranslatorOptionsMapper;
import org.cqframework.cql.cql2elm.DefaultLibrarySourceProvider;
import org.cqframework.cql.cql2elm.FhirLibrarySourceProvider;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.LibrarySourceProvider;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.cql2elm.NamespaceInfo;
import org.cqframework.cql.cql2elm.NamespaceManager;
import org.cqframework.cql.cql2elm.model.TranslatedLibrary;
import org.cqframework.cql.elm.tracking.TrackBack;
import org.fhir.ucum.UcumService;
import org.hl7.cql.model.IntervalType;
import org.hl7.cql.model.ListType;
import org.hl7.cql.model.NamedType;
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
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.r5.context.IWorkerContext.ILoggingService;
import org.hl7.fhir.r5.model.DataRequirement;
import org.hl7.fhir.r5.model.Enumerations;
import org.hl7.fhir.r5.model.Library;
import org.hl7.fhir.r5.model.ParameterDefinition;
import org.hl7.fhir.r5.model.RelatedArtifact;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;

public class CqlSubSystem {

  /** 
   * information about a cql file
   */
  public class CqlSourceFileInformation {
    private byte[] elm;
    private byte[] jsonElm;
    private List<ValidationMessage> errors = new ArrayList<>();
    private List<DataRequirement> dataRequirements = new ArrayList<>();
    private List<RelatedArtifact> relatedArtifacts = new ArrayList<>();
    private List<ParameterDefinition> parameters = new ArrayList<>();
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
          logger.logDebugMessage(ILoggingService.LogCategory.PROGRESS, String.format("Exceptions occurred attempting to load npm library source for %s", identifier.toString()));
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

  public CqlSubSystem(List<NpmPackage> packages, List<String> folders, ILibraryReader reader, ILoggingService logger, UcumService ucumService, String packageId, String canonicalBase) {
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
      throw new IllegalStateException("CQL File map is not available, execute has not been called");
    }

    if (!fileMap.containsKey(filename)) {
      return null;
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
      if (!options.getFormats().contains(CqlTranslator.Format.XML)) {
        options.getFormats().add(CqlTranslator.Format.XML);
      }
    }

    return options;
  }

  private void translateFolder(String folder) {
    logger.logMessage(String.format("Translating CQL source in folder %s", folder));

    CqlTranslatorOptions options = getTranslatorOptions(folder);

    // Setup
    // Construct DefaultLibrarySourceProvider
    // Construct FhirLibrarySourceProvider
    ModelManager modelManager = new ModelManager();
    LibraryManager libraryManager = new LibraryManager(modelManager);
    libraryManager.getLibrarySourceLoader().registerProvider(new NpmLibrarySourceProvider());
    libraryManager.getLibrarySourceLoader().registerProvider(new FhirLibrarySourceProvider());
    libraryManager.getLibrarySourceLoader().registerProvider(new DefaultLibrarySourceProvider(Paths.get(folder)));

    loadNamespaces(libraryManager);

    // foreach *.cql file
    for (File file : new File(folder).listFiles(getCqlFilenameFilter())) {
      translateFile(modelManager, libraryManager, file, options);
    }
  }

  private void loadNamespaces(LibraryManager libraryManager) {
    if (namespaceInfo != null) {
      libraryManager.getNamespaceManager().addNamespace(namespaceInfo);
    }

    for (NpmPackage p : packages) {
      if (p.name() != null && !p.name().isEmpty() && p.canonical() != null && !p.canonical().isEmpty()) {
        NamespaceInfo ni = new NamespaceInfo(p.name(), p.canonical());
        libraryManager.getNamespaceManager().addNamespace(ni);
      }
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

  private void translateFile(ModelManager modelManager, LibraryManager libraryManager, File file, CqlTranslatorOptions options) {
    logger.logMessage(String.format("Translating CQL source in file %s", file.toString()));
    CqlSourceFileInformation result = new CqlSourceFileInformation();
    fileMap.put(file.getAbsoluteFile().toString(), result);

    try {

      // translate toXML
      CqlTranslator translator = CqlTranslator.fromFile(namespaceInfo, file, modelManager, libraryManager,
              options.getValidateUnits() ? ucumService : null, options);

      // record errors and warnings
      for (CqlTranslatorException exception : translator.getExceptions()) {
        result.getErrors().add(exceptionToValidationMessage(file, exception));
      }

      if (translator.getErrors().size() > 0) {
        result.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, IssueType.EXCEPTION, file.getName(),
                String.format("CQL Processing failed with (%d) errors.", translator.getErrors().size()), IssueSeverity.ERROR));
        logger.logMessage(String.format("Translation failed with (%d) errors; see the error log for more information.", translator.getErrors().size()));
      }
      else {
        // convert to base64 bytes
        // NOTE: Publication tooling requires XML content
        result.setElm(translator.toXml().getBytes());
        if (options.getFormats().contains(CqlTranslator.Format.JSON)) {
          result.setJsonElm(translator.toJson().getBytes());
        }
        if (options.getFormats().contains(CqlTranslator.Format.JXSON)) {
          result.setJsonElm(translator.toJxson().getBytes());
        }

        // TODO: Report context, requires 1.5 translator (ContextDef)
        // NOTE: In STU3, only Patient context is supported

        // Extract relatedArtifact data (models, libraries, code systems, and value sets)
        result.relatedArtifacts.addAll(extractRelatedArtifacts(translator.toELM()));

        // Extract parameter data and validate result types are supported types
        List<ValidationMessage> paramMessages = new ArrayList<>();
        result.parameters.addAll(extractParameters(translator.toELM(), paramMessages));
        for (ValidationMessage paramMessage : paramMessages) {
          result.getErrors().add(new ValidationMessage(paramMessage.getSource(), paramMessage.getType(), file.getName(),
                  paramMessage.getMessage(), paramMessage.getLevel()));
        }

        // Extract dataRequirement data
        result.dataRequirements.addAll(extractDataRequirements(translator.toRetrieves(), translator.getTranslatedLibrary(), libraryManager));

        logger.logMessage("CQL translation completed successfully.");
      }
    }
    catch (Exception e) {
      result.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, IssueType.EXCEPTION, file.getName(), "CQL Processing failed with exception: "+e.getMessage(), IssueSeverity.ERROR));
    }
  }

  private FilenameFilter getCqlFilenameFilter() {
    return new FilenameFilter() {
      @Override
      public boolean accept(File path, String name) {
        return name.endsWith(".cql");
      }
    };
  }

  private List<RelatedArtifact> extractRelatedArtifacts(org.hl7.elm.r1.Library library) {
    List<RelatedArtifact> result = new ArrayList<>();

    // Report model dependencies
    // URL for a model info is: [baseCanonical]/Library/[model-name]-ModelInfo
    if (library.getUsings() != null && !library.getUsings().getDef().isEmpty()) {
      for (UsingDef def : library.getUsings().getDef()) {
        // System model info is an implicit dependency, do not report
        if (!def.getLocalIdentifier().equals("System")) {
          result.add(toRelatedArtifact(def));
        }
      }
    }

    // Report library dependencies
    if (library.getIncludes() != null && !library.getIncludes().getDef().isEmpty()) {
      for (IncludeDef def : library.getIncludes().getDef()) {
        result.add(toRelatedArtifact(def));
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

  private List<ParameterDefinition> extractParameters(org.hl7.elm.r1.Library library, List<ValidationMessage> messages) {
    List<ParameterDefinition> result = new ArrayList<>();

    if (library.getParameters() != null && !library.getParameters().getDef().isEmpty()) {
      for (ParameterDef def : library.getParameters().getDef()) {
        result.add(toParameterDefinition(def, messages));
      }
    }

    if (library.getStatements() != null && !library.getStatements().getDef().isEmpty()) {
      for (ExpressionDef def : library.getStatements().getDef()) {
        if (!(def instanceof FunctionDef) && (def.getAccessLevel() == null || def.getAccessLevel() == AccessModifier.PUBLIC)) {
          result.add(toOutputParameterDefinition(def, messages));
        }
      }
    }

    return result;
  }

  private List<DataRequirement> extractDataRequirements(List<org.hl7.elm.r1.Retrieve> retrieves, TranslatedLibrary library, LibraryManager libraryManager) {
    List<DataRequirement> result = new ArrayList<>();

    for (Retrieve retrieve : retrieves) {
      result.add(toDataRequirement(retrieve, library, libraryManager));
    }

    return result;
  }

  private org.hl7.fhir.r5.model.RelatedArtifact toRelatedArtifact(UsingDef usingDef) {
    return new org.hl7.fhir.r5.model.RelatedArtifact()
            .setType(RelatedArtifact.RelatedArtifactType.DEPENDSON)
            .setResource(getModelInfoReferenceUrl(usingDef.getUri(), usingDef.getLocalIdentifier(), usingDef.getVersion()));
  }

  private String getModelInfoReferenceUrl(String uri, String name, String version) {
    if (uri != null) {
      return String.format("%s/Library/%s-ModelInfo%s", uri, name, version != null ? ("|" + version) : "");
    }

    return String.format("Library/%-ModelInfo%s", name, version != null ? ("|" + version) : "");
  }

  private org.hl7.fhir.r5.model.RelatedArtifact toRelatedArtifact(IncludeDef includeDef) {
    return new org.hl7.fhir.r5.model.RelatedArtifact()
            .setType(org.hl7.fhir.r5.model.RelatedArtifact.RelatedArtifactType.DEPENDSON)
            .setResource(getReferenceUrl(includeDef.getPath(), includeDef.getVersion()));
  }

  private String getReferenceUrl(String path, String version) {
    String uri = NamespaceManager.getUriPart(path);
    String name = NamespaceManager.getNamePart(path);

    if (uri != null) {
      // The translator has no way to correctly infer the namespace of the FHIRHelpers library, since it will happily provide that source to any namespace that wants it
      // So override the declaration here so that it points back to the FHIRHelpers library in the base specification
      if (name.equals("FHIRHelpers") && !uri.equals("http://hl7.org/fhir")) {
        uri = "http://hl7.org/fhir";
      }
      return String.format("%s/Library/%s%s", uri, name, version != null ? ("|" + version) : "");
    }

    return String.format("Library/%s%s", path, version != null ? ("|" + version) : "");
  }

  private org.hl7.fhir.r5.model.RelatedArtifact toRelatedArtifact(CodeSystemDef codeSystemDef) {
    return new org.hl7.fhir.r5.model.RelatedArtifact()
            .setType(org.hl7.fhir.r5.model.RelatedArtifact.RelatedArtifactType.DEPENDSON)
            .setResource(toReference(codeSystemDef));
  }

  private org.hl7.fhir.r5.model.RelatedArtifact toRelatedArtifact(ValueSetDef valueSetDef) {
    return new org.hl7.fhir.r5.model.RelatedArtifact()
            .setType(org.hl7.fhir.r5.model.RelatedArtifact.RelatedArtifactType.DEPENDSON)
            .setResource(toReference(valueSetDef));
  }

  private ParameterDefinition toParameterDefinition(ParameterDef def, List<ValidationMessage> messages) {
    org.hl7.cql.model.DataType parameterType = def.getResultType() instanceof ListType ? ((ListType)def.getResultType()).getElementType() : def.getResultType();

    AtomicBoolean isList = new AtomicBoolean(false);
    Enumerations.FHIRAllTypes typeCode = Enumerations.FHIRAllTypes.fromCode(toFHIRParameterTypeCode(parameterType, def.getName(), isList, messages));

    return new ParameterDefinition()
            .setName(def.getName())
            .setUse(Enumerations.OperationParameterUse.IN)
            .setMin(0)
            .setMax(isList.get() ? "*" : "1")
            .setType(typeCode);
  }

  private ParameterDefinition toOutputParameterDefinition(ExpressionDef def, List<ValidationMessage> messages) {
    AtomicBoolean isList = new AtomicBoolean(false);
    Enumerations.FHIRAllTypes typeCode = Enumerations.FHIRAllTypes.fromCode(toFHIRResultTypeCode(def.getResultType(), def.getName(), isList, messages));

    return new ParameterDefinition()
            .setName(def.getName())
            .setUse(Enumerations.OperationParameterUse.OUT)
            .setMin(0)
            .setMax(isList.get() ? "*" : "1")
            .setType(typeCode);
  }

  private String toFHIRResultTypeCode(org.hl7.cql.model.DataType dataType, String defName, AtomicBoolean isList, List<ValidationMessage> messages) {
    AtomicBoolean isValid = new AtomicBoolean(true);
    String resultCode = toFHIRTypeCode(dataType, isValid, isList);
    if (!isValid.get()) {
      // Issue a warning that the result type is not supported
      messages.add(new ValidationMessage(ValidationMessage.Source.Publisher, IssueType.NOTSUPPORTED, "CQL Library Packaging",
              String.format("Result type %s of definition %s is not supported; implementations may not be able to use the result of this expression",
                      dataType.toLabel(), defName), IssueSeverity.WARNING));
    }

    return resultCode;
  }

  private String toFHIRParameterTypeCode(org.hl7.cql.model.DataType dataType, String parameterName, AtomicBoolean isList, List<ValidationMessage> messages) {
    AtomicBoolean isValid = new AtomicBoolean(true);
    String resultCode = toFHIRTypeCode(dataType, isValid, isList);
    if (!isValid.get()) {
      // Issue a warning that the parameter type is not supported
      messages.add(new ValidationMessage(ValidationMessage.Source.Publisher, IssueType.NOTSUPPORTED, "CQL Library Packaging",
              String.format("Parameter type %s of parameter %s is not supported; reported as FHIR.Any", dataType.toLabel(), parameterName), IssueSeverity.WARNING));
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

  private org.hl7.fhir.r5.model.DataRequirement toDataRequirement(Retrieve retrieve, TranslatedLibrary library, LibraryManager libraryManager) {
    org.hl7.fhir.r5.model.DataRequirement dr = new org.hl7.fhir.r5.model.DataRequirement();

    dr.setType(org.hl7.fhir.r5.model.Enumerations.FHIRAllTypes.fromCode(retrieve.getDataType().getLocalPart()));

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
        cfc.setValueSet(toReference(resolveValueSetRef(vsr, library, libraryManager)));
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
                                      TranslatedLibrary library, LibraryManager libraryManager) {
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
    return codeSystemDef.getId() + (codeSystemDef.getVersion() != null ? ("|" + codeSystemDef.getVersion()) : "");
  }

  private String toReference(ValueSetDef valueSetDef) {
    return valueSetDef.getId() + (valueSetDef.getVersion() != null ? ("|" + valueSetDef.getVersion()) : "");
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
