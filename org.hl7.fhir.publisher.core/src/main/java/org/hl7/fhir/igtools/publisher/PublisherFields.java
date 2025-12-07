package org.hl7.fhir.igtools.publisher;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.igtools.publisher.comparators.IpaComparator;
import org.hl7.fhir.igtools.publisher.comparators.IpsComparator;
import org.hl7.fhir.igtools.publisher.comparators.PreviousVersionComparator;
import org.hl7.fhir.igtools.publisher.modules.IPublisherModule;
import org.hl7.fhir.igtools.publisher.realm.RealmBusinessRules;
import org.hl7.fhir.igtools.renderers.BaseRenderer;
import org.hl7.fhir.igtools.renderers.ValidationPresenter;
import org.hl7.fhir.igtools.spreadsheets.MappingSpace;
import org.hl7.fhir.igtools.templates.Template;
import org.hl7.fhir.igtools.templates.TemplateManager;
import org.hl7.fhir.igtools.web.PublisherConsoleLogger;
import org.hl7.fhir.r5.context.ContextUtilities;
import org.hl7.fhir.r5.context.ILoggingService;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.elementmodel.LanguageUtils;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.r5.renderers.spreadsheets.StructureDefinitionSpreadsheetGenerator;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.Resolver;
import org.hl7.fhir.r5.utils.NPMPackageGenerator;
import org.hl7.fhir.r5.utils.client.FHIRToolingClient;
import org.hl7.fhir.r5.utils.formats.CSVWriter;
import org.hl7.fhir.r5.utils.validation.ValidatorSession;
import org.hl7.fhir.utilities.*;
import org.hl7.fhir.utilities.i18n.subtag.LanguageSubtagRegistry;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.validation.instance.InstanceValidator;
import org.hl7.fhir.validation.profile.ProfileValidator;

import java.io.File;
import java.util.*;

public class PublisherFields {

    FHIRToolingClient webTxServer;
    @Getter private Calendar execTime = Calendar.getInstance();


    Locale forcedLanguage;
    String igPack = "";
    boolean isChild;
    boolean appendTrailingSlashInDataFile;
    boolean newIg = false;
    Map<String, String> countryCodeForName = null;
    Map<String, String> countryNameForCode = null;
    Map<String, String> countryCodeForNumeric = null;
    Map<String, String> countryCodeFor2Letter = null;
    Map<String, String> shortCountryCode = null;
    Map<String, String> stateNameForCode = null;
    Map<String, Map<String, ElementDefinition>> sdMapCache = new HashMap<String, Map<String, ElementDefinition>>();
    List<String> ignoreFlags = null;
    Map<String, Boolean> wantGenParams = new HashMap<String, Boolean>();
    Publisher childPublisher = null;
    boolean genExampleNarratives = true;
    List<FetchedResource> noNarrativeResources = new ArrayList<FetchedResource>();
    final List<String> customResourceFiles = new ArrayList<String>();
    final List<String> additionalResourceFiles = new ArrayList<String>();
    List<FetchedResource> noValidateResources = new ArrayList<FetchedResource>();
    List<String> resourceDirs = new ArrayList<String>();
    List<String> resourceFactoryDirs = new ArrayList<String>();
    List<String> pagesDirs = new ArrayList<String>();
    List<String> testDirs = new ArrayList<String>();
    List<DataSetInformation> dataSets = new ArrayList<DataSetInformation>();
    List<String> dataDirs = new ArrayList<String>();
    List<String> otherDirs = new ArrayList<String>();
    String tempDir;
    String tempLangDir;
    String outputDir;
    String specPath;
    String qaDir;
    String version;
    FhirPublication pubVersion;
    long jekyllTimeout = Publisher.JEKYLL_TIMEOUT;
    long fshTimeout = Publisher.FSH_TIMEOUT;
    SuppressedMessageInformation suppressedMessages = new SuppressedMessageInformation();
    boolean tabbedSnapshots = false;
    String igName;
    SimpleFetcher fetcher = new SimpleFetcher(null);

    @Getter
    SimpleWorkerContext context; //
    DataRenderer dr;
    InstanceValidator validator;
    ProfileValidator pvalidator;
    CodeSystemValidator csvalidator;
    IGKnowledgeProvider igpkp;
    List<SpecMapManager> specMaps = new ArrayList<SpecMapManager>();
    List<PublisherUtils.LinkedSpecification> linkSpecMaps = new ArrayList<PublisherUtils.LinkedSpecification>();
    List<String> suppressedIds = new ArrayList<String>();
    Map<String, MappingSpace> mappingSpaces = new HashMap<String, MappingSpace>();
    Map<ImplementationGuide.ImplementationGuideDefinitionResourceComponent, FetchedFile> fileMap = new HashMap<ImplementationGuide.ImplementationGuideDefinitionResourceComponent, FetchedFile>();
    Map<String, FetchedFile> altMap = new HashMap<String, FetchedFile>();
    Map<String, FetchedResource> canonicalResources = new HashMap<String, FetchedResource>();
    List<FetchedFile> fileList = new ArrayList<FetchedFile>();
    List<FetchedFile> changeList = new ArrayList<FetchedFile>();
    List<String> fileNames = new ArrayList<String>();
    Map<String, FetchedFile> relativeNames = new HashMap<String, FetchedFile>();
    Set<String> bndIds = new HashSet<String>();
    List<Resource> loaded = new ArrayList<Resource>();
    ImplementationGuide sourceIg;
    ImplementationGuide publishedIg;
    List<ValidationMessage> errors = new ArrayList<ValidationMessage>();
    Set<String> otherFilesStartup = new HashSet<String>();
    Set<String> otherFilesRun = new HashSet<String>();
    Set<String> regenList = new HashSet<String>();
    StringBuilder filelog;
    Set<String> allOutputs = new HashSet<String>();
    Set<FetchedResource> examples = new HashSet<FetchedResource>();
    Set<FetchedResource> testplans = new HashSet<FetchedResource>();
    Set<FetchedResource> testscripts = new HashSet<FetchedResource>();
    Set<String> profileTestCases = new HashSet<String>();
    HashMap<String, FetchedResource> resources = new HashMap<String, FetchedResource>();
    HashMap<String, ImplementationGuide.ImplementationGuideDefinitionPageComponent> igPages = new HashMap<String, ImplementationGuide.ImplementationGuideDefinitionPageComponent>();
    List<String> logOptions = new ArrayList<String>();
    List<String> listedURLExemptions = new ArrayList<String>();
    String altCanonical;
    String jekyllCommand = "jekyll";
    boolean makeQA = true;
    boolean bundleReferencesResolve = true;
    CqlSubSystem cql;
    File killFile;
    List<PageFactory> pageFactories = new ArrayList<PageFactory>();
    ILoggingService logger = null;
    HTMLInspector inspector;
    List<String> prePagesDirs = new ArrayList<String>();
    HashMap<String, PreProcessInfo> preProcessInfo = new HashMap<String, PreProcessInfo>();
    String historyPage;
    String vsCache;
    String adHocTmpDir;
    RenderingContext rc;
    RenderingContext.RenderingContextLangs rcLangs; // prepared lang alternatives
    List<ContactDetail> contacts;
    List<UsageContext> contexts;
    List<String> binaryPaths = new ArrayList<String>();
    MarkdownType copyright;
    List<CodeableConcept> jurisdictions;
    Enumeration<ImplementationGuide.SPDXLicense> licenseInfo;
    StringType publisher;
    String businessVersion;
    String wgm;
    List<ContactDetail> defaultContacts;
    List<UsageContext> defaultContexts;
    MarkdownType defaultCopyright;
    String defaultWgm;
    List<CodeableConcept> defaultJurisdictions;
    Enumeration<ImplementationGuide.SPDXLicense> defaultLicenseInfo;
    StringType defaultPublisher;
    String defaultBusinessVersion;
    String configFileRootPath;
    MarkDownProcessor markdownEngine;
    boolean savingExpansions = true;
    List<ValueSet> expansions = new ArrayList<ValueSet>();
    boolean generatingDatabase = true;
    String npmName;
    NPMPackageGenerator npm;
    Map<String, NPMPackageGenerator> vnpms = new HashMap<String, NPMPackageGenerator>();
    Map<String, NPMPackageGenerator> lnpms = new HashMap<String, NPMPackageGenerator>();
    FilesystemPackageCacheManager pcm;
    TemplateManager templateManager;
    String rootDir;
    String templatePck;
    boolean templateLoaded;
    String folderToDelete;
    NpmPackage packge;
    String txLog;
    boolean includeHeadings;
    String openApiTemplate;
    boolean isPropagateStatus;
    Collection<String> extraTemplateList = new ArrayList<String>(); // List of templates in order they should appear when navigating next/prev
    Map<String, String> extraTemplates = new HashMap<String, String>();
    Collection<String> historyTemplates = new ArrayList<String>(); // What templates should only be turned on if there's history
    Collection<String> exampleTemplates = new ArrayList<String>(); // What templates should only be turned on if there are examples
    String license;
    String htmlTemplate;
    String mdTemplate;
    boolean brokenLinksError;
    String nestedIgConfig;
    String igArtifactsPage;
    String nestedIgOutput;
    boolean genExamples;
    boolean doTransforms;
    boolean allInvariants = true;
    List<String> spreadsheets = new ArrayList<String>();
    List<String> bundles = new ArrayList<String>();
    List<String> mappings = new ArrayList<String>();
    List<String> generateVersions = new ArrayList<String>();
    RealmBusinessRules realmRules;
    PreviousVersionComparator previousVersionComparator;
    IpaComparator ipaComparator;
    IpsComparator ipsComparator;
    IGPublisherLiquidTemplateServices templateProvider;
    List<NpmPackage> npmList = new ArrayList<NpmPackage>();
    String repoRoot;
    ValidationServices validationFetcher;
    Template template;
    boolean igMode;
    boolean isBuildingTemplate;
    JsonObject templateInfo;
    ExtensionTracker extensionTracker;
    String currVer;
    List<String> codeSystemProps = new ArrayList<String>();
    List<PublisherUtils.JsonDependency> jsonDependencies = new ArrayList<PublisherUtils.JsonDependency>();
    Coding expectedJurisdiction;
    Map<String, String> loadedIds;
    boolean duplicateInputResourcesDetected;
    List<String> comparisonVersions;
    List<String> ipaComparisons;
    List<String> ipsComparisons;
    String versionToAnnotate;
    TimeTracker tt;
    String igrealm;
    String copyrightYear;
    String fmtDateTime = "yyyy-MM-dd HH:mm:ssZZZ";
    String fmtDate = "yyyy-MM-dd";
    DependentIGFinder dependentIgFinder;
    List<StructureDefinition> modifierExtensions = new ArrayList<StructureDefinition>();
    Object branchName;
    R4ToR4BAnalyser r4tor4b;
    List<DependencyAnalyser.ArtifactDependency> dependencyList;
    Map<String, List<String>> trackedFragments = new HashMap<String, List<String>>();
    PackageInformation packageInfo;
    boolean tocSizeWarning = false;
    CSVWriter allProfilesCsv;
    StructureDefinitionSpreadsheetGenerator allProfilesXlsx;
    boolean produceJekyllData;
    boolean noUsageCheck;
    boolean hasTranslations;
    String defaultTranslationLang;
    List<String> translationLangs = new ArrayList<String>();
    List<String> translationSources = new ArrayList<String>();
    List<String> usedLangFiles = new ArrayList<String>();
    List<String> viewDefinitions = new ArrayList<String>();
    int validationLogTime = 0;
    long maxMemory = 0;
    String oidRoot;
    IniFile oidIni;
    boolean hintAboutNonMustSupport = false;
    boolean anyExtensionsAllowed = false;
    boolean checkAggregation = false;
    boolean autoLoad = false;
    boolean showReferenceMessages = false;
    boolean noExperimentalContent = false;
    boolean displayWarnings = false;
    List<RelatedIG> relatedIGs = new ArrayList<RelatedIG>();
    long last = System.currentTimeMillis();
    List<String> unknownParams = new ArrayList<String>();
    RenderingContext.FixedValueFormat fixedFormat = RenderingContext.FixedValueFormat.JSON;
    static PublisherConsoleLogger consoleLogger;
    IPublisherModule module;
    BaseRenderer bdr;
    boolean noXigLink;

    int sqlIndex = 0;

    boolean isSushi;

    LanguageSubtagRegistry registry;

    public Map<String, PublisherBase.FragmentUseRecord> fragmentUses = new HashMap<>();

    LanguageUtils langUtils;


    ContextUtilities cu;

    boolean logLoading;

    JsonObject approvedIgsForCustomResources;
    Set<String> customResourceNames = new HashSet<>();
    List<StructureDefinition> customResources = new ArrayList<>();

    boolean needsRegen = false;

    ValidatorSession validatorSession;
    ValidationPresenter.LanguagePopulationPolicy langPolicy = ValidationPresenter.LanguagePopulationPolicy.NONE;

    List<String> testDataFactories;

    Map<String, String> factoryProfileMap = new HashMap<>();

    Map<String, Set<String>> otherVersionAddedResources = new HashMap<>();

    String ipStmt;

    PublisherUtils.PinningPolicy pinningPolicy = PublisherUtils.PinningPolicy.NO_ACTION;
    String pinDest = null;

    int pinCount;

    PublisherUtils.UMLGenerationMode generateUml = PublisherUtils.UMLGenerationMode.NONE;

    List<String> suppressedMappings = new ArrayList<>();

    PublisherSigner signer;

    public boolean hasCheckedDependencies;
    public boolean saveExpansionParams;
    @Getter private List<String> exemptHtmlPatterns = new ArrayList<>();
    Resolver.IReferenceResolver resolver;
}