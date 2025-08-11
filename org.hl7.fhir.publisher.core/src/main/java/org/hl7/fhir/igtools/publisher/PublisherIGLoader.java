package org.hl7.fhir.igtools.publisher;

import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_10_50;
import org.hl7.fhir.convertors.factory.*;
import org.hl7.fhir.convertors.txClient.TerminologyClientFactory;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.openehr.ArchetypeImporter;
import org.hl7.fhir.igtools.publisher.comparators.IpaComparator;
import org.hl7.fhir.igtools.publisher.comparators.IpsComparator;
import org.hl7.fhir.igtools.publisher.comparators.PreviousVersionComparator;
import org.hl7.fhir.igtools.publisher.loaders.AdjunctFileLoader;
import org.hl7.fhir.igtools.publisher.loaders.CqlResourceLoader;
import org.hl7.fhir.igtools.publisher.loaders.PatchLoaderKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.loaders.PublisherLoader;
import org.hl7.fhir.igtools.publisher.modules.CrossVersionModule;
import org.hl7.fhir.igtools.publisher.modules.IPublisherModule;
import org.hl7.fhir.igtools.publisher.modules.NullModule;
import org.hl7.fhir.igtools.publisher.realm.NullRealmBusinessRules;
import org.hl7.fhir.igtools.publisher.realm.RealmBusinessRules;
import org.hl7.fhir.igtools.publisher.realm.USRealmBusinessRules;
import org.hl7.fhir.igtools.renderers.ValidationPresenter;
import org.hl7.fhir.igtools.spreadsheets.IgSpreadsheetParser;
import org.hl7.fhir.igtools.spreadsheets.MappingSpace;
import org.hl7.fhir.igtools.templates.TemplateManager;
import org.hl7.fhir.r4.formats.FormatUtilities;
import org.hl7.fhir.r5.conformance.R5ExtensionsLoader;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.context.ContextUtilities;
import org.hl7.fhir.r5.context.IContextResourceLoader;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.elementmodel.*;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.formats.IParser;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.liquid.BaseTableWrapper;
import org.hl7.fhir.r5.liquid.GlobalObject;
import org.hl7.fhir.r5.liquid.LiquidEngine;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.terminologies.TerminologyFunctions;
import org.hl7.fhir.r5.testfactory.TestDataFactory;
import org.hl7.fhir.r5.utils.MappingSheetParser;
import org.hl7.fhir.r5.utils.NPMPackageGenerator;
import org.hl7.fhir.r5.utils.ResourceUtilities;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities;
import org.hl7.fhir.r5.utils.validation.ValidatorSession;
import org.hl7.fhir.utilities.*;
import org.hl7.fhir.utilities.filesystem.CSFile;
import org.hl7.fhir.utilities.i18n.*;
import org.hl7.fhir.utilities.json.JsonException;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.*;
import org.hl7.fhir.utilities.settings.FhirSettings;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationOptions;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;
import org.hl7.fhir.utilities.xml.XMLUtil;
import org.hl7.fhir.validation.ValidatorSettings;
import org.hl7.fhir.validation.ValidatorUtils;
import org.hl7.fhir.validation.instance.InstanceValidator;
import org.hl7.fhir.validation.profile.ProfileValidator;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.hl7.fhir.igtools.publisher.Publisher.IG_NAME;
import static org.hl7.fhir.igtools.publisher.Publisher.TOOLING_IG_CURRENT_RELEASE;

/**
 * this class is part of the Publisher Core cluster, and handles loading all the content relevant to the IG. See @Publisher for discussion
 */

public class PublisherIGLoader extends PublisherBase {
  public PublisherIGLoader(PublisherFields publisherFields) {
    super(publisherFields);
  }


  public void initialize() throws Exception {
    f.pcm = getFilesystemPackageCacheManager();
    log("Build FHIR IG from "+ f.configFile);
    if (f.mode == PublisherUtils.IGBuildMode.PUBLICATION)
      log("Build Formal Publication package, intended for "+getTargetOutput());

    log("API keys loaded from "+ FhirSettings.getFilePath());

    f.templateManager = new TemplateManager(f.pcm, f.logger);
    f.templateProvider = new IGPublisherLiquidTemplateServices();
    f.extensionTracker = new ExtensionTracker();
    log("Package Cache: "+ f.pcm.getFolder());
    if (f.packagesFolder != null) {
      log("Also loading Packages from "+ f.packagesFolder);
      f.pcm.loadFromFolder(f.packagesFolder);
    }
    f.fetcher.setRootDir(f.rootDir);
    f.fetcher.setResourceDirs(f.resourceDirs);
    if (f.configFile != null && focusDir().contains(" ")) {
      throw new Error("There is a space in the folder path: \""+focusDir()+"\". Please fix your directory arrangement to remove the space and try again");
    }
    if (f.configFile != null) {
      File fsh = new File(Utilities.path(focusDir(), "fsh"));
      if (fsh.exists() && fsh.isDirectory() && !f.noSushi) {
        prescanSushiConfig(focusDir());
        new FSHRunner(this).runFsh(new File(FileUtilities.getDirectoryForFile(fsh.getAbsolutePath())), f.mode);
        f.isSushi = true;
      } else {
        File fsh2 = new File(Utilities.path(focusDir(), "input", "fsh"));
        if (fsh2.exists() && fsh2.isDirectory() && !f.noSushi) {
          prescanSushiConfig(focusDir());
          new FSHRunner(this).runFsh(new File(FileUtilities.getDirectoryForFile(fsh.getAbsolutePath())), f.mode);
          f.isSushi = true;
        }
      }
    }
    IniFile ini = checkNewIg();
    if (ini != null) {
      f.newIg = true;
      initializeFromIg(ini);
    } else if (isTemplate())
      initializeTemplate();
    else {
      // initializeFromJson();
      throw new Error("Old style JSON configuration is no longer supported. If you see this, then ig.ini wasn't found in '"+ f.rootDir +"'");
    }
    f.expectedJurisdiction = checkForJurisdiction();

  }

  private void prescanSushiConfig(String dir) throws IOException {
    // resolve packages for Sushi in advance
    File sc = new File(Utilities.path(dir, "sushi-config.yaml"));
    if (sc.exists()) {
      List<String> lines = Files.readAllLines(sc.toPath());
      boolean indeps = false;
      String pid = null;
      for (String line : lines) {
        if (!line.startsWith(" ") && "dependencies:".equals(line.trim())) {
          indeps = true;
        } else if (indeps && !line.trim().startsWith("#")) {
          int indent = Utilities.startCharCount(line, ' ');
          switch (indent) {
            case 2:
              String t = line.trim();
              if (t.contains(":")) {
                String name = t.substring(0, t.indexOf(":")).trim();
                String value = t.substring(t.indexOf(":")+1).trim();
                if (Utilities.noString(value)) {
                  pid = name;
                } else {
                  installPackage(name, value);
                }
              }
              break;
            case 4:
              t = line.trim();
              if (t.contains(":")) {
                String name = t.substring(0, t.indexOf(":")).trim();
                String value = t.substring(t.indexOf(":")+1).trim();
                if ("version".equals(name)) {
                  if (pid != null) {
                    installPackage(pid, value);
                  }
                  pid = null;
                }
              }
              break;
            case 0:
              indeps = false;
            default:
              // ignore this line
          }
        }
      }
    }
  }

  private IniFile checkNewIg() throws IOException {
    if (f.configFile == null)
      return null;
    if (f.configFile.endsWith(File.separatorChar+".")) {
      f.configFile = f.configFile.substring(0, f.configFile.length() - 2);
    }
    File cf = f.mode == PublisherUtils.IGBuildMode.AUTOBUILD ? new File(f.configFile) : new CSFile(f.configFile);
    if (!cf.exists())
      return null;
    if (cf.isDirectory())
      cf = f.mode == PublisherUtils.IGBuildMode.AUTOBUILD ? new File(Utilities.path(f.configFile, "ig.ini")) : new CSFile(Utilities.path(f.configFile, "ig.ini"));
    if (!cf.exists())
      return null;
    String s = FileUtilities.fileToString(cf);
    if (s.startsWith("[IG]"))
      return new IniFile(cf.getAbsolutePath());
    else
      return null;
  }


  private void initializeFromIg(IniFile ini) throws Exception {
    f.configFile = ini.getFileName();
    f.igMode = true;
    f.repoRoot = FileUtilities.getDirectoryForFile(ini.getFileName());
    f.rootDir = f.repoRoot;
    if (!f.rootDir.equals(f.configFile)) {
      log("Root directory: " + f.rootDir);
    }
    f.fetcher.setRootDir(f.rootDir);
    f.killFile = new File(Utilities.path(f.rootDir, "ig-publisher.kill"));
    // ok, first we load the template
    String templateName = ini.getStringProperty("IG", "template");
    if (templateName == null)
      throw new Exception("You must nominate a template - consult the IG Publisher documentation");
    f.module = loadModule(ini.getStringProperty("IG", "module"));
    if (f.module.useRoutine("preProcess")) {
      log("== Ask "+ f.module.name()+" to pre-process the IG ============================");
      if (!f.module.preProcess(f.rootDir)) {
        throw new Exception("Process terminating due to Module failure");
      } else {
        log("== Done ====================================================================");
      }
    }
    f.igName = Utilities.path(f.repoRoot, ini.getStringProperty("IG", "ig"));
    try {
      try {
        f.sourceIg = (ImplementationGuide) org.hl7.fhir.r5.formats.FormatUtilities.loadFileTight(f.igName);
        boolean isR5 = false;
        for (Enumeration<Enumerations.FHIRVersion> v : f.sourceIg.getFhirVersion()) {
          isR5 = isR5 || VersionUtilities.isR5VerOrLater(v.getCode());
        }
        if (!isR5) {
          f.sourceIg = (ImplementationGuide) VersionConvertorFactory_40_50.convertResource(FormatUtilities.loadFile(f.igName));
        }
      } catch (Exception e) {
        log("Unable to load IG as an r5 IG - try R4 ("+e.getMessage()+")");
        f.sourceIg = (ImplementationGuide) VersionConvertorFactory_40_50.convertResource(FormatUtilities.loadFile(f.igName));
      }
    } catch (Exception e) {
      throw new Exception("Error Parsing File "+ f.igName +": "+e.getMessage(), e);
    }
    f.template = f.templateManager.loadTemplate(templateName, f.rootDir, f.sourceIg.getPackageId(), f.mode == PublisherUtils.IGBuildMode.AUTOBUILD, f.logOptions.contains("template"));
    if (f.template.hasExtraTemplates()) {
      processExtraTemplates(f.template.getExtraTemplates());
    }

    if (f.template.hasPreProcess()) {
      for (JsonElement e : f.template.getPreProcess()) {
        handlePreProcess((JsonObject)e, f.rootDir);
      }
    }
    f.branchName = ini.getStringProperty("dev", "branch");

    Map<String, List<ValidationMessage>> messages = new HashMap<String, List<ValidationMessage>>();
    f.sourceIg = f.template.onLoadEvent(f.sourceIg, messages);
    checkOutcomes(messages);
    // ok, loaded. Now we start loading settings out of the IG
    f.version = processVersion(f.sourceIg.getFhirVersion().get(0).asStringValue()); // todo: support multiple versions
    if (VersionUtilities.isR2Ver(f.version) || VersionUtilities.isR2Ver(f.version)) {
      throw new Error("As of the end of 2024, the FHIR  R2 (version "+ f.version +") is no longer supported by the IG Publisher");
    }
    if (!Utilities.existsInList(f.version, "5.0.0", "4.3.0", "4.0.1", "3.0.2", "6.0.0-ballot3")) {
      throw new Error("Unable to support version '"+ f.version +"' - must be one of 5.0.0, 4.3.0, 4.0.1, 3.0.2 or 6.0.0-ballot3");
    }

    if (!VersionUtilities.isSupportedVersion(f.version)) {
      throw new Exception("Error: the IG declares that is based on version "+ f.version +" but this IG publisher only supports publishing the following versions: "+VersionUtilities.listSupportedVersions());
    }
    f.pubVersion = FhirPublication.fromCode(f.version);

    f.specPath = pathForVersion();
    f.qaDir = null;
    f.vsCache = Utilities.path(f.repoRoot, "txCache");
    f.templateProvider.clear();

    String expParams = null;
    List<String> exemptHtmlPatterns = new ArrayList<>();

    f.copyrightYear = null;
    Boolean useStatsOptOut = null;
    List<String> extensionDomains = new ArrayList<>();
    f.testDataFactories = new ArrayList<>();
    f.tempDir = Utilities.path(f.rootDir, "temp");
    f.tempLangDir = Utilities.path(f.rootDir, "translations");
    f.outputDir = Utilities.path(f.rootDir, "output");
    List<String> relatedIGParams = new ArrayList<>();
    ValidationOptions.R5BundleRelativeReferencePolicy r5BundleRelativeReferencePolicy = ValidationOptions.R5BundleRelativeReferencePolicy.DEFAULT;

    Map<String, String> expParamMap = new HashMap<>();
    boolean allowExtensibleWarnings = false;
    boolean noCIBuildIssues = false;
    List<String> conversionVersions = new ArrayList<>();
    List<String> liquid0 = new ArrayList<>();
    List<String> liquid1 = new ArrayList<>();
    List<String> liquid2 = new ArrayList<>();
    int count = 0;
    for (ImplementationGuide.ImplementationGuideDefinitionParameterComponent p : f.sourceIg.getDefinition().getParameter()) {
      // documentation for this list: https://confluence.hl7.org/display/FHIR/Implementation+Guide+Parameters
      String pc = p.getCode().getCode();
      if (pc == null) {
        throw new Error("The IG Parameter has no code");
      } else switch (pc) {
        case "logging":
          f.logOptions.add(p.getValue());
          break;
        case "generate":
          if ("example-narratives".equals(p.getValue()))
            f.genExampleNarratives = true;
          if ("examples".equals(p.getValue()))
            f.genExamples = true;
          break;
        case "no-narrative":
          String s = p.getValue();
          if (!s.contains("/")) {
            throw new Exception("Illegal value "+s+" for no-narrative: should be resource/id (see documentation at https://build.fhir.org/ig/FHIR/fhir-tools-ig/CodeSystem-ig-parameters.html)");
          }
          f.noNarratives.add(s);
          break;
        case "no-validate":
          f.noValidate.add(p.getValue());
          break;
        case "path-resource":
          String dir = getPathResourceDirectory(p);
          if (!f.resourceDirs.contains(dir)) {
            f.resourceDirs.add(dir);
          }
          break;
        case "path-factory":
          dir = getPathResourceDirectory(p);
          if (!f.resourceFactoryDirs.contains(dir)) {
            f.resourceFactoryDirs.add(dir);
          }
          break;
        case "autoload-resources":
          f.autoLoad = "true".equals(p.getValue());
          break;
        case "codesystem-property":
          f.codeSystemProps.add(p.getValue());
          break;
        case "path-pages":
          f.pagesDirs.add(Utilities.path(f.rootDir, p.getValue()));
          break;
        case "path-test":
          f.testDirs.add(Utilities.path(f.rootDir, p.getValue()));
          break;
        case "path-data":
          f.dataDirs.add(Utilities.path(f.rootDir, p.getValue()));
          break;
        case "path-other":
          f.otherDirs.add(Utilities.path(f.rootDir, p.getValue()));
          break;
        case "copyrightyear":
          f.copyrightYear = p.getValue();
          break;
        case "path-qa":
          f.qaDir = Utilities.path(f.rootDir, p.getValue());
          break;
        case "path-tx-cache":
          f.vsCache = Paths.get(p.getValue()).isAbsolute() ? p.getValue() : Utilities.path(f.rootDir, p.getValue());
          break;
        case "path-liquid":
          liquid1.add(p.getValue());
          break;
        case "path-liquid-template":
          liquid0.add(p.getValue());
          break;
        case "path-liquid-ig":
          liquid2.add(p.getValue());
          break;
        case "path-temp":
          f.tempDir = Utilities.path(f.rootDir, p.getValue());
          if (!f.tempDir.startsWith(f.rootDir))
            throw new Exception("Temp directory must be a sub-folder of the base directory");
          break;
        case "path-output":
          if (f.mode != PublisherUtils.IGBuildMode.WEBSERVER) {
            // Can't override outputDir if building using webserver
            f.outputDir = Utilities.path(f.rootDir, p.getValue());
            if (!f.outputDir.startsWith(f.rootDir))
              throw new Exception("Output directory must be a sub-folder of the base directory");
          }
          break;
        case "path-history":
          f.historyPage = p.getValue();
          break;
        case "path-expansion-params":
          expParams = p.getValue();
          break;
        case "path-suppressed-warnings":
          loadSuppressedMessages(Utilities.path(f.rootDir, p.getValue()), "ImplementationGuide.definition.parameter["+count+"].value");
          break;
        case "html-exempt":
          exemptHtmlPatterns.add(p.getValue());
          break;
        case "usage-stats-opt-out":
          useStatsOptOut = "true".equals(p.getValue());
          break;
        case "extension-domain":
          extensionDomains.add(p.getValue());
          break;
        case "bundle-references-resolve":
          f.bundleReferencesResolve = "true".equals(p.getValue());
          break;
        case "active-tables":
          HierarchicalTableGenerator.ACTIVE_TABLES = "true".equals(p.getValue());
          break;
        case "propagate-status":
          f.isPropagateStatus = p.getValue().equals("true");
          break;
        case "ig-expansion-parameters":
          expParamMap.put(pc, p.getValue());
          break;
        case "special-url":
          f.listedURLExemptions.add(p.getValue());
          break;
        case "special-url-base":
          f.altCanonical = p.getValue();
          break;
        case "no-usage-check":
          f.noUsageCheck = "true".equals(p.getValue());
          break;
        case "template-openapi":
          f.openApiTemplate = p.getValue();
          break;
        case "template-html":
          f.htmlTemplate = p.getValue();
          break;
        case "format-date":
          f.fmtDate = p.getValue();
          break;
        case "format-datetime":
          f.fmtDateTime = p.getValue();
          break;
        case "template-md":
          f.mdTemplate = p.getValue();
          break;
        case "path-binary":
          f.binaryPaths.add(Utilities.path(f.rootDir, p.getValue()));
          break;
        case "show-inherited-invariants":
          f.allInvariants = "true".equals(p.getValue());
          break;
        case "apply-contact":
          if (p.getValue().equals("true")) {
            f.contacts = f.sourceIg.getContact();
          }
          break;
        case "apply-context":
          if (p.getValue().equals("true")) {
            f.contexts = f.sourceIg.getUseContext();
          }
          break;
        case "apply-copyright":
          if (p.getValue().equals("true")) {
            f.copyright = f.sourceIg.getCopyrightElement();
          }
          break;
        case "apply-jurisdiction":
          if (p.getValue().equals("true")) {
            f.jurisdictions = f.sourceIg.getJurisdiction();
          }
          break;
        case "apply-license":
          if (p.getValue().equals("true")) {
            f.licenseInfo = f.sourceIg.getLicenseElement();
          }
          break;
        case "apply-publisher":
          if (p.getValue().equals("true")) {
            f.publisher = f.sourceIg.getPublisherElement();
          }
          break;
        case "apply-version":
          if (p.getValue().equals("true")) {
            f.businessVersion = f.sourceIg.getVersion();
          }
          break;
        case "apply-wg":
          if (p.getValue().equals("true")) {
            f.wgm = ExtensionUtilities.readStringExtension(f.sourceIg, ExtensionDefinitions.EXT_WORKGROUP);
          }
          break;
        case "default-contact":
          if (p.getValue().equals("true")) {
            f.defaultContacts = f.sourceIg.getContact();
          }
          break;
        case "default-context":
          if (p.getValue().equals("true")) {
            f.defaultContexts = f.sourceIg.getUseContext();
          }
          break;
        case "default-copyright":
          if (p.getValue().equals("true")) {
            f.defaultCopyright = f.sourceIg.getCopyrightElement();
          }
          break;
        case "default-jurisdiction":
          if (p.getValue().equals("true")) {
            f.defaultJurisdictions = f.sourceIg.getJurisdiction();
          }
          break;
        case "default-license":
          if (p.getValue().equals("true")) {
            f.defaultLicenseInfo = f.sourceIg.getLicenseElement();
          }
          break;
        case "default-publisher":
          if (p.getValue().equals("true")) {
            f.defaultPublisher = f.sourceIg.getPublisherElement();
          }
          break;
        case "default-version":
          if (p.getValue().equals("true")) {
            f.defaultBusinessVersion = f.sourceIg.getVersion();
          }
          break;
        case "default-wg":
          if (p.getValue().equals("true")) {
            f.defaultWgm = ExtensionUtilities.readStringExtension(f.sourceIg, ExtensionDefinitions.EXT_WORKGROUP);
          }
          break;
        case "log-loaded-resources":
          if (p.getValue().equals("true")) {
            f.logLoading = true;
          }
        case "generate-version":
          f.generateVersions.add(p.getValue());
          break;
        case "conversion-version":
          conversionVersions.add(p.getValue());
          break;
        case "custom-resource":
          f.customResourceFiles.add(p.getValue());
          break;
        case "related-ig":
          relatedIGParams.add(p.getValue());
          break;
        case "suppressed-ids":
          for (String s1 : p.getValue().split("\\,"))
            f.suppressedIds.add(s1);
          break;
        case "allow-extensible-warnings":
          allowExtensibleWarnings = p.getValue().equals("true");
          break;
        case "version-comparison":
          if (f.comparisonVersions == null) {
            f.comparisonVersions = new ArrayList<>();
          }
          if (!"n/a".equals(p.getValue()) && !f.comparisonVersions.contains(p.getValue())) {
            f.comparisonVersions.add(p.getValue());
          }
          break;
        case "version-comparison-master":
          f.versionToAnnotate = p.getValue();
          if (f.comparisonVersions == null) {
            f.comparisonVersions = new ArrayList<>();
          }
          if (!"n/a".equals(p.getValue()) && !f.comparisonVersions.contains(p.getValue())) {
            f.comparisonVersions.add(p.getValue());
          }
          break;
        case "ipa-comparison":
          if (f.ipaComparisons == null) {
            f.ipaComparisons = new ArrayList<>();
          }
          if (!"n/a".equals(p.getValue())) {
            f.ipaComparisons.add(p.getValue());
          }
          break;
        case "ips-comparison":
          if (f.ipsComparisons == null) {
            f.ipsComparisons = new ArrayList<>();
          }
          if (!"n/a".equals(p.getValue())) {
            f.ipsComparisons.add(p.getValue());
          }
          break;
        case "validation":
          if (p.getValue().equals("check-must-support"))
            f.hintAboutNonMustSupport = true;
          else if (p.getValue().equals("allow-any-extensions"))
            f.anyExtensionsAllowed = true;
          else if (p.getValue().equals("check-aggregation"))
            f.checkAggregation = true;
          else if (p.getValue().equals("no-broken-links"))
            f.brokenLinksError = true;
          else if (p.getValue().equals("show-reference-messages"))
            f.showReferenceMessages = true;
          else if (p.getValue().equals("no-experimental-content"))
            f.noExperimentalContent = true;
          break;
        case "tabbed-snapshots":
          f.tabbedSnapshots = p.getValue().equals("true");
          break;
        case "r4-exclusion":
          f.r4tor4b.markExempt(p.getValue(), true);
          break;
        case "r4b-exclusion":
          f.r4tor4b.markExempt(p.getValue(), false);
          break;
        case "display-warnings":
          f.displayWarnings = "true".equals(p.getValue());
          break;
        case "produce-jekyll-data":
          f.produceJekyllData = "true".equals(p.getValue());
          break;
        case "page-factory":
          dir = Utilities.path(f.rootDir, "temp", "factory-pages", "factory"+ f.pageFactories.size());
          FileUtilities.createDirectory(dir);
          f.pageFactories.add(new PageFactory(Utilities.path(f.rootDir, p.getValue()), dir));
          f.pagesDirs.add(dir);
          break;
        case "i18n-default-lang":
          f.hasTranslations = true;
          f.defaultTranslationLang = p.getValue();
          break;
        case "i18n-lang":
          f.hasTranslations = true;
          f.translationLangs.add(p.getValue());
          break;
        case "translation-supplements":
          f.hasTranslations = true;
          f.translationSources.add(p.getValue());
          break;
        case "translation-sources":
          f.hasTranslations = true;
          f.translationSources.add(p.getValue());
          break;
        case "validation-duration-report-cutoff":
          f.validationLogTime = Utilities.parseInt(p.getValue(), 0) * 1000;
          break;
        case "viewDefinition":
          f.viewDefinitions.add(p.getValue());
          break;
        case "test-data-factories":
          f.testDataFactories.add(p.getValue());
          break;
        case "fixed-value-format":
          f.fixedFormat = RenderingContext.FixedValueFormat.fromCode(p.getValue());
          break;
        case "no-cibuild-issues":
          noCIBuildIssues = "true".equals(p.getValue());
          break;
        case "logged-when-scanning":
          if ("false".equals(p.getValue())) {
            f.fetcher.setReport(false);
          } else if ("stack".equals(p.getValue())) {
            f.fetcher.setReport(true);
            f.fetcher.setDebug(true);
          }  else {
            f.fetcher.setReport(true);
          }
          break;
        case "auto-oid-root":
          f.oidRoot = p.getValue();
          if (!OIDUtilities.isValidOID(f.oidRoot)) {
            throw new Error("Invalid oid found in assign-missing-oids-root: "+ f.oidRoot);
          }
          f.oidIni = new IniFile(oidIniLocation());
          if (!f.oidIni.hasSection("Documentation")) {
            f.oidIni.setStringProperty("Documentation", "information1", "This file stores the OID assignments for resources defined in this IG.", null);
            f.oidIni.setStringProperty("Documentation", "information2", "It must be added to git and committed when resources are added or their id is changed", null);
            f.oidIni.setStringProperty("Documentation", "information3", "You should not generally need to edit this file, but if you do:", null);
            f.oidIni.setStringProperty("Documentation", "information4", " (a) you can change the id of a resource (left side) if you change it's actual id in your source, to maintain OID consistency", null);
            f.oidIni.setStringProperty("Documentation", "information5", " (b) you can change the oid of the resource to an OID you assign manually. If you really know what you're doing with OIDs", null);
            f.oidIni.setStringProperty("Documentation", "information6", "There is never a reason to edit anything else", null);
            f.oidIni.save();
          }
          if (!hasOid(f.sourceIg.getIdentifier())) {
            f.sourceIg.getIdentifier().add(new Identifier().setSystem("urn:ietf:rfc:3986").setValue("urn:oid:"+ f.oidRoot));
          }
          break;
        case "resource-language-policy":
          f.langPolicy = ValidationPresenter.LanguagePopulationPolicy.fromCode(p.getValue());
          if (f.langPolicy == null) {
            throw new Error("resource-language-policy value of '"+p.getValue()+"' not understood");
          }
          break;
        case "profile-test-cases":
          f.profileTestCases.add(p.getValue());
          break;
        case "pin-canonicals":
          switch (p.getValue()) {
            case "pin-none":
              f.pinningPolicy = PublisherUtils.PinningPolicy.NO_ACTION;
              break;
            case "pin-all":
              f.pinningPolicy = PublisherUtils.PinningPolicy.FIX;
              break;
            case "pin-multiples":
              f.pinningPolicy = PublisherUtils.PinningPolicy.WHEN_MULTIPLE_CHOICES;
              break;
            default:
              throw new FHIRException("Unknown value for 'pin-canonicals' of '"+p.getValue()+"'");
          }
          break;
        case "pin-manifest":
          f.pinDest = p.getValue();
          break;
        case "generate-uml":
          f.generateUml = PublisherUtils.UMLGenerationMode.fromCode(p.getValue());
          break;
        case "no-xig-link":
          f.noXigLink = "true".equals(p.getValue());
          break;
        case "r5-bundle-relative-reference-policy" :
          r5BundleRelativeReferencePolicy = ValidationOptions.R5BundleRelativeReferencePolicy.fromCode(p.getValue());
        case "suppress-mappings":
          if ("*".equals(p.getValue())) {
            f.suppressedMappings.addAll(Utilities.strings("http://hl7.org/fhir/fivews", "http://hl7.org/fhir/workflow", "http://hl7.org/fhir/interface", "http://hl7.org/v2",
                    // "http://loinc.org",  "http://snomed.org/attributebinding", "http://snomed.info/conceptdomain",
                    "http://hl7.org/v3/cda", "http://hl7.org/v3", "http://ncpdp.org/SCRIPT10_6",
                    "https://dicomstandard.org/current", "http://w3.org/vcard", "https://profiles.ihe.net/ITI/TF/Volume3", "http://www.w3.org/ns/prov",
                    "http://ietf.org/rfc/2445", "http://www.omg.org/spec/ServD/1.0/", "http://metadata-standards.org/11179/", "http://ihe.net/data-element-exchange",
                    "http://openehr.org", "http://siframework.org/ihe-sdc-profile", "http://siframework.org/cqf", "http://www.cdisc.org/define-xml",
                    "http://www.cda-adc.ca/en/services/cdanet/", "http://www.pharmacists.ca/", "http://www.healthit.gov/quality-data-model",
                    "http://hl7.org/orim", "http://hl7.org/fhir/w5", "http://hl7.org/fhir/logical", "http://hl7.org/qidam", "http://hl7.org/fhir/object-implementation",
                    "http://github.com/MDMI/ReferentIndexContent", "http://hl7.org/fhir/rr", "http://www.hl7.org/v3/PORX_RM020070UV",
                    "https://bridgmodel.nci.nih.gov", "https://www.iso.org/obp/ui/#iso:std:iso:11615", "https://www.isbt128.org/uri/","http://nema.org/dicom",
                    "https://www.iso.org/obp/ui/#iso:std:iso:11238", "urn:iso:std:iso:11073:10201", "urn:iso:std:iso:11073:10207", "urn:iso:std:iso:11073:20701"));
          } else {
            f.suppressedMappings.add(p.getValue());
          }
        default:
          if (pc.startsWith("wantGen-")) {
            String code = pc.substring(8);
            f.wantGenParams.put(code, Boolean.valueOf(p.getValue().equals("true")));
          } else if (!f.template.isParameter(pc)) {
            f.unknownParams.add(pc+"="+p.getValue());
          }
      }
      count++;
    }

    if (f.langPolicy == ValidationPresenter.LanguagePopulationPolicy.IG || f.langPolicy == ValidationPresenter.LanguagePopulationPolicy.ALL) {
      if (f.sourceIg.hasJurisdiction()) {
        Locale localeFromRegion = ResourceUtilities.getLocale(f.sourceIg);
        if (localeFromRegion != null) {
          f.sourceIg.setLanguage(localeFromRegion.toLanguageTag());
        } else {
          throw new Error("Unable to determine locale from jurisdiction (as requested by policy)");
        }
      } else {
        f.sourceIg.setLanguage("en");
      }
    }
    if (ini.hasProperty("IG", "jekyll-timeout")) { //todo: consider adding this to ImplementationGuideDefinitionParameterComponent
      f.jekyllTimeout = ini.getLongProperty("IG", "jekyll-timeout") * 1000;
    }

    for (String s : liquid0) {
      f.templateProvider.load(Utilities.path(f.rootDir, s));
    }
    for (String s : liquid1) {
      f.templateProvider.load(Utilities.path(f.rootDir, s));
    }
    for (String s : liquid2) {
      f.templateProvider.load(Utilities.path(f.rootDir, s));
    }

    // ok process the paths
    if (f.resourceDirs.isEmpty())
      f.resourceDirs.add(Utilities.path(f.rootDir, "resources"));
    if (f.pagesDirs.isEmpty())
      f.pagesDirs.add(Utilities.path(f.rootDir, "pages"));
    if (f.mode == PublisherUtils.IGBuildMode.WEBSERVER)
      f.vsCache = Utilities.path(System.getProperty("java.io.tmpdir"), "fhircache");
    else if (f.vsCache == null) {
      if (f.mode == PublisherUtils.IGBuildMode.AUTOBUILD)
        f.vsCache = Utilities.path(System.getProperty("java.io.tmpdir"), "fhircache");
      else
        f.vsCache = Utilities.path(System.getProperty("user.home"), "fhircache");
    }

    logDebugMessage(LogCategory.INIT, "Check folders");
    List<String> extraDirs = new ArrayList<String>();
    for (String s : f.resourceDirs) {
      if (s.endsWith(File.separator+"*")) {
        logDebugMessage(LogCategory.INIT, "Scan Source: "+s);
        scanDirectories(FileUtilities.getDirectoryForFile(s), extraDirs);

      }
    }
    f.resourceDirs.addAll(extraDirs);

    List<String> missingDirs = new ArrayList<String>();
    for (String s : f.resourceDirs) {
      logDebugMessage(LogCategory.INIT, "Source: "+s);
      if (s.endsWith(File.separator+"*")) {
        missingDirs.add(s);

      }
      if (!checkDir(s, true))
        missingDirs.add(s);
    }
    f.resourceDirs.removeAll(missingDirs);

    missingDirs.clear();
    for (String s : f.pagesDirs) {
      logDebugMessage(LogCategory.INIT, "Pages: "+s);
      if (!checkDir(s, true))
        missingDirs.add(s);
    }
    f.pagesDirs.removeAll(missingDirs);

    logDebugMessage(LogCategory.INIT, "Temp: "+ f.tempDir);
    FileUtilities.clearDirectory(f.tempDir);
    forceDir(f.tempDir);
    forceDir(Utilities.path(f.tempDir, "_includes"));
    forceDir(Utilities.path(f.tempDir, "_data"));
    for (String s : allLangs()) {
      forceDir(Utilities.path(f.tempDir, s));
    }
    logDebugMessage(LogCategory.INIT, "Output: "+ f.outputDir);
    forceDir(f.outputDir);
    FileUtilities.clearDirectory(f.outputDir);
    if (f.qaDir != null) {
      logDebugMessage(LogCategory.INIT, "QA Dir: "+ f.qaDir);
      forceDir(f.qaDir);
    }
    f.makeQA = f.mode == PublisherUtils.IGBuildMode.WEBSERVER ? false : f.qaDir != null;

    if (Utilities.existsInList(f.version.substring(0,  3), "1.0", "1.4", "1.6", "3.0"))
      f.markdownEngine = new MarkDownProcessor(MarkDownProcessor.Dialect.DARING_FIREBALL);
    else
      f.markdownEngine = new MarkDownProcessor(MarkDownProcessor.Dialect.COMMON_MARK);


    // initializing the tx sub-system
    FileUtilities.createDirectory(f.vsCache);
    if (f.cacheOption == PublisherUtils.CacheOption.CLEAR_ALL) {
      log("Terminology Cache is at "+ f.vsCache +". Clearing now");
      FileUtilities.clearDirectory(f.vsCache);
    } else if (f.mode == PublisherUtils.IGBuildMode.AUTOBUILD) {
      log("Terminology Cache is at "+ f.vsCache +". Trimming now");
      FileUtilities.clearDirectory(f.vsCache, "snomed.cache", "loinc.cache", "ucum.cache");
    } else if (f.cacheOption == PublisherUtils.CacheOption.CLEAR_ERRORS) {
      log("Terminology Cache is at "+ f.vsCache +". Clearing Errors now");
      logDebugMessage(LogCategory.INIT, "Deleted "+Integer.toString(clearErrors(f.vsCache))+" files");
    } else {
      log("Terminology Cache is at "+ f.vsCache +". "+Integer.toString(FileUtilities.countFilesInDirectory(f.vsCache))+" files in cache");
    }
    if (!new File(f.vsCache).exists())
      throw new Exception("Unable to access or create the cache directory at "+ f.vsCache);
    logDebugMessage(LogCategory.INIT, "Load Terminology Cache from "+ f.vsCache);


    // loading the specifications
    f.context = loadCorePackage();
    f.context.setProgress(true);
    f.context.setLogger(f.logger);
    f.context.setAllowLoadingDuplicates(true);
    f.context.setExpandCodesLimit(1000);
    f.context.setExpansionParameters(makeExpProfile());
    f.context.getTxClientManager().setUsage("publication");
    for (PageFactory pf : f.pageFactories) {
      pf.setContext(f.context);
    }
    f.dr = new DataRenderer(f.context);
    for (String s : conversionVersions) {
      loadConversionVersion(s);
    }
    f.langUtils = new LanguageUtils(f.context);
    f.txLog = FileUtilities.createTempFile("fhir-ig-", ".html").getAbsolutePath();
    System.out.println("Running Terminology Log: "+ f.txLog);
    if (f.mode != PublisherUtils.IGBuildMode.WEBSERVER) {
      if (f.txServer == null || !f.txServer.contains(":")) {
        log("WARNING: Running without terminology server - terminology content will likely not publish correctly");
        f.context.setCanRunWithoutTerminology(true);
        f.txLog = null;
      } else {
        log("Connect to Terminology Server at "+ f.txServer);
        f.context.connectToTSServer(new TerminologyClientFactory(f.version), f.txServer, "fhir/publisher", f.txLog, true);
      }
    } else {
      f.context.connectToTSServer(new TerminologyClientFactory(f.version), f.webTxServer.getAddress(), "fhir/publisher", f.txLog, true);
    }
    if (expParams != null) {
      /* This call to uncheckedPath is allowed here because the path is used to
         load an existing resource, and is not persisted in the loadFile method.
       */
      f.context.setExpansionParameters(new ExpansionParameterUtilities(f.context).reviewVersions((Parameters) VersionConvertorFactory_40_50.convertResource(FormatUtilities.loadFile(Utilities.uncheckedPath(FileUtilities.getDirectoryForFile(f.igName), expParams)))));
    } else if (!expParamMap.isEmpty()) {
      f.context.setExpansionParameters(new Parameters());
    }
    for (String n : expParamMap.values()) {
      f.context.getExpansionParameters().addParameter(n, expParamMap.get(n));
    }

    f.newMultiLangTemplateFormat = f.template.config().asBoolean("multilanguage-format");
    loadPubPack();
    f.igpkp = new IGKnowledgeProvider(f.context, checkAppendSlash(f.specPath), determineCanonical(f.sourceIg.getUrl(), "ImplementationGuide.url"), f.template.config(), f.errors, VersionUtilities.isR2Ver(f.version), f.template, f.listedURLExemptions, f.altCanonical, f.fileList, f.module);
    if (f.autoLoad) {
      f.igpkp.setAutoPath(true);
    }
    f.fetcher.setPkp(f.igpkp);
    f.fetcher.setContext(f.context);
    f.template.loadSummaryRows(f.igpkp.summaryRows());

    if (VersionUtilities.isR4Plus(f.version) && !dependsOnExtensions(f.sourceIg.getDependsOn()) && !f.sourceIg.getPackageId().contains("hl7.fhir.uv.extensions")) {
      ImplementationGuide.ImplementationGuideDependsOnComponent dep = new ImplementationGuide.ImplementationGuideDependsOnComponent();
      dep.setUserData(UserDataNames.pub_no_load_deps, "true");
      dep.setId("hl7ext");
      dep.setPackageId(getExtensionsPackageName());
      dep.setUri("http://hl7.org/fhir/extensions/ImplementationGuide/hl7.fhir.uv.extensions");
      dep.setVersion(f.pcm.getLatestVersion(dep.getPackageId(), true));
      dep.addExtension(ExtensionDefinitions.EXT_IGDEP_COMMENT, new MarkdownType("Automatically added as a dependency - all IGs depend on the HL7 Extension Pack"));
      f.sourceIg.getDependsOn().add(0, dep);
    }
    if (!dependsOnUTG(f.sourceIg.getDependsOn()) && !f.sourceIg.getPackageId().contains("hl7.terminology")) {
      ImplementationGuide.ImplementationGuideDependsOnComponent dep = new ImplementationGuide.ImplementationGuideDependsOnComponent();
      dep.setUserData(UserDataNames.pub_no_load_deps, "true");
      dep.setId("hl7tx");
      dep.setPackageId(getUTGPackageName());
      dep.setUri("http://terminology.hl7.org/ImplementationGuide/hl7.terminology");
      dep.setVersion(f.pcm.getLatestVersion(dep.getPackageId(), true));
      dep.addExtension(ExtensionDefinitions.EXT_IGDEP_COMMENT, new MarkdownType("Automatically added as a dependency - all IGs depend on HL7 Terminology"));
      f.sourceIg.getDependsOn().add(0, dep);
    }
    if (!"hl7.fhir.uv.tools".equals(f.sourceIg.getPackageId()) && !dependsOnTooling(f.sourceIg.getDependsOn())) {
      String toolingPackageId = getToolingPackageName()+"#"+TOOLING_IG_CURRENT_RELEASE;
      if (f.sourceIg.getDefinition().hasExtension("http://hl7.org/fhir/tools/StructureDefinition/ig-internal-dependency")) {
        f.sourceIg.getDefinition().getExtensionByUrl("http://hl7.org/fhir/tools/StructureDefinition/ig-internal-dependency").setValue(new CodeType(toolingPackageId));
      } else {
        f.sourceIg.getDefinition().addExtension("http://hl7.org/fhir/tools/StructureDefinition/ig-internal-dependency", new CodeType(toolingPackageId));
      }
    }

    f.inspector = new HTMLInspector(f.outputDir, f.specMaps, f.linkSpecMaps, this, f.igpkp.getCanonical(), f.sourceIg.getPackageId(), f.sourceIg.getVersion(), f.trackedFragments, f.fileList, f.module, f.mode == PublisherUtils.IGBuildMode.AUTOBUILD || f.mode == PublisherUtils.IGBuildMode.WEBSERVER, f.trackFragments ? f.fragmentUses : null, f.relatedIGs, noCIBuildIssues, allLangs());
    f.inspector.getManual().add("full-ig.zip");
    if (f.historyPage != null) {
      f.inspector.getManual().add(f.historyPage);
      f.inspector.getManual().add(Utilities.pathURL(f.igpkp.getCanonical(), f.historyPage));
    }
    f.inspector.getManual().add("qa.html");
    f.inspector.getManual().add("qa-tx.html");
    f.inspector.getManual().add("qa-ipreview.html");
    f.inspector.getExemptHtmlPatterns().addAll(exemptHtmlPatterns);
    f.inspector.setPcm(f.pcm);

    int i = 0;
    for (ImplementationGuide.ImplementationGuideDependsOnComponent dep : f.sourceIg.getDependsOn()) {
      loadIg(dep, i, !dep.hasUserData(UserDataNames.pub_no_load_deps));
      i++;
    }
    if (!"hl7.fhir.uv.tools".equals(f.sourceIg.getPackageId()) && !dependsOnTooling(f.sourceIg.getDependsOn())) {
      loadIg("igtools", getToolingPackageName(), TOOLING_IG_CURRENT_RELEASE, "http://hl7.org/fhir/tools/ImplementationGuide/hl7.fhir.uv.tools", i, false);
    }

    // we're also going to look for packages that can be referred to but aren't dependencies
    for (Extension ext : f.sourceIg.getDefinition().getExtensionsByUrl("http://hl7.org/fhir/tools/StructureDefinition/ig-link-dependency")) {
      loadLinkIg(ext.getValue().primitiveValue());
    }

    for (String s : relatedIGParams) {
      loadRelatedIg(s);
    }

    if (!VersionUtilities.isR5Plus(f.context.getVersion())) {
      System.out.println("Load R5 Specials");
      R5ExtensionsLoader r5e = new R5ExtensionsLoader(f.pcm, f.context);
      r5e.load();
      r5e.loadR5SpecialTypes(SpecialTypeHandler.specialTypes(f.context.getVersion()));
    }
    //    SpecMapManager smm = new SpecMapManager(r5e.getMap(), r5e.getPckCore().fhirVersion());
    //    smm.setName(r5e.getPckCore().name());
    //    smm.setBase("http://build.fhir.org");
    //    smm.setBase2("http://build.fhir.org/");
    //    specMaps.add(smm);
    //    smm = new SpecMapManager(r5e.getMap(), r5e.getPckExt().fhirVersion());
    //    smm.setName(r5e.getPckExt().name());
    //    smm.setBase("http://build.fhir.org/ig/HL7/fhir-extensions");
    //    smm.setBase2("http://build.fhir.org/ig/HL7/fhir-extensions");
    //    specMaps.add(smm);
    //    System.out.println(" - " + r5e.getCount() + " resources (" + tt.milestone() + ")");
    generateLoadedSnapshots();

    // set up validator;
    f.validatorSession = new ValidatorSession();
    IGPublisherHostServices hs = new IGPublisherHostServices(f.igpkp, f.fileList, f.context, new DateTimeType(f.execTime), new StringType(f.igpkp.specPath()));
    hs.registerFunction(new GlobalObject.GlobalObjectRandomFunction());
    hs.registerFunction(new BaseTableWrapper.TableColumnFunction());
    hs.registerFunction(new BaseTableWrapper.TableDateColumnFunction());
    hs.registerFunction(new TestDataFactory.CellLookupFunction());
    hs.registerFunction(new TestDataFactory.TableLookupFunction());
    hs.registerFunction(new TerminologyFunctions.ExpandFunction());
    hs.registerFunction(new TerminologyFunctions.ValidateVSFunction());
    hs.registerFunction(new TerminologyFunctions.TranslateFunction());

    f.validator = new InstanceValidator(f.context, hs, f.context.getXVer(), f.validatorSession, new ValidatorSettings()); // todo: host services for reference resolution....
    f.validator.setAllowXsiLocation(true);
    f.validator.setNoBindingMsgSuppressed(true);
    f.validator.setNoExtensibleWarnings(!allowExtensibleWarnings);
    f.validator.setHintAboutNonMustSupport(f.hintAboutNonMustSupport);
    f.validator.setAnyExtensionsAllowed(f.anyExtensionsAllowed);
    f.validator.setAllowExamples(true);
    f.validator.setCrumbTrails(true);
    f.validator.setWantCheckSnapshotUnchanged(true);
    f.validator.setForPublication(true);
    f.validator.getSettings().setDisplayWarningMode(f.displayWarnings);
    f.cu = new ContextUtilities(f.context, f.suppressedMappings);

    f.pvalidator = new ProfileValidator(f.context, f.validator.getSettings(), f.context.getXVer(), f.validatorSession);
    f.csvalidator = new CodeSystemValidator(f.context, f.validator.getSettings(), f.context.getXVer(), f.validatorSession);
    f.pvalidator.setCheckAggregation(f.checkAggregation);
    f.pvalidator.setCheckMustSupport(f.hintAboutNonMustSupport);
    f.validator.setShowMessagesFromReferences(f.showReferenceMessages);
    f.validator.getExtensionDomains().addAll(extensionDomains);
    f.validator.setNoExperimentalContent(f.noExperimentalContent);
    f.validator.getExtensionDomains().add(ExtensionDefinitions.EXT_PRIVATE_BASE);
    f.validationFetcher = new ValidationServices(f.context, f.igpkp, f.sourceIg, f.fileList, f.npmList, f.bundleReferencesResolve, f.specMaps, f.module);
    f.validator.setFetcher(f.validationFetcher);
    f.validator.setPolicyAdvisor(f.validationFetcher);
    f.validator.getSettings().setR5BundleRelativeReferencePolicy(r5BundleRelativeReferencePolicy);

    if (!f.generateVersions.isEmpty()) {
      Collections.sort(f.generateVersions);
      f.validator.getSettings().setMinVersion(VersionUtilities.getMajMin(f.generateVersions.get(0)));
      f.validator.getSettings().setMaxVersion(VersionUtilities.getMajMin(f.generateVersions.get(f.generateVersions.size()-1)));
    }

    for (String s : f.context.getBinaryKeysAsSet()) {
      if (needFile(s)) {
        if (f.makeQA)
          checkMakeFile(f.context.getBinaryForKey(s), Utilities.path(f.qaDir, s), f.otherFilesStartup);
        checkMakeFile(f.context.getBinaryForKey(s), Utilities.path(f.tempDir, s), f.otherFilesStartup);
        for (String l : allLangs()) {
          checkMakeFile(f.context.getBinaryForKey(s), Utilities.path(f.tempDir, l, s), f.otherFilesStartup);
        }
      }
    }
    f.otherFilesStartup.add(Utilities.path(f.tempDir, "_data"));
    f.otherFilesStartup.add(Utilities.path(f.tempDir, "_data", "fhir.json"));
    f.otherFilesStartup.add(Utilities.path(f.tempDir, "_data", "structuredefinitions.json"));
    f.otherFilesStartup.add(Utilities.path(f.tempDir, "_data", "questionnaires.json"));
    f.otherFilesStartup.add(Utilities.path(f.tempDir, "_data", "pages.json"));
    f.otherFilesStartup.add(Utilities.path(f.tempDir, "_includes"));

    if (f.sourceIg.hasLicense())
      f.license = f.sourceIg.getLicense().toCode();
    f.npmName = f.sourceIg.getPackageId();
    if (Utilities.noString(f.npmName)) {
      throw new Error("No packageId provided in the implementation guide resource - cannot build this IG");
    }
    f.appendTrailingSlashInDataFile = true;
    f.includeHeadings = f.template.getIncludeHeadings();
    f.igArtifactsPage = f.template.getIGArtifactsPage();
    f.doTransforms = f.template.getDoTransforms();
    f.template.getExtraTemplates(f.extraTemplates);

    for (Extension e : f.sourceIg.getExtensionsByUrl(ExtensionDefinitions.EXT_IGP_SPREADSHEET)) {
      f.spreadsheets.add(e.getValue().primitiveValue());
    }
    ExtensionUtilities.removeExtension(f.sourceIg, ExtensionDefinitions.EXT_IGP_SPREADSHEET);

    for (Extension e : f.sourceIg.getExtensionsByUrl(ExtensionDefinitions.EXT_IGP_MAPPING_CSV)) {
      f.mappings.add(e.getValue().primitiveValue());
    }
    for (Extension e : f.sourceIg.getDefinition().getExtensionsByUrl(ExtensionDefinitions.EXT_IGP_BUNDLE)) {
      f.bundles.add(e.getValue().primitiveValue());
    }
    if (f.mode == PublisherUtils.IGBuildMode.AUTOBUILD)
      f.extensionTracker.setoptIn(true);
    else if (f.npmName.contains("hl7.") || f.npmName.contains("argonaut.") || f.npmName.contains("ihe."))
      f.extensionTracker.setoptIn(true);
    else if (useStatsOptOut != null)
      f.extensionTracker.setoptIn(!useStatsOptOut);
    else
      f.extensionTracker.setoptIn(!ini.getBooleanProperty("IG", "usage-stats-opt-out"));

    log("Initialization complete");
  }

  private IPublisherModule loadModule(String name) throws Exception {
    if (Utilities.noString(name)) {
      return new NullModule();
    } else switch (name) {
      case "x-version": return new CrossVersionModule();
      default: throw new Exception("Unknown module name \""+name+"\" in ig.ini");
    }
  }


  private void initializeTemplate() throws IOException {
    f.rootDir = f.configFile;
    f.outputDir = Utilities.path(f.rootDir, "output");
    f.tempDir = Utilities.path(f.rootDir, "temp");
  }


  private Coding checkForJurisdiction() {
    String id = f.npmName;
    if (!id.startsWith("hl7.") || !id.contains(".")) {
      return null;
    }
    String[] parts = id.split("\\.");
    if (Utilities.existsInList(parts[1], "terminology")) {
      return null;
    }
    if (Utilities.existsInList(parts[1], "fhir") && Utilities.existsInList(parts[2], "cda")) {
      return null;
    }
    if (Utilities.existsInList(parts[1], "fhir") && Utilities.existsInList(parts[2], "test")) {
      return null;
    }
    if (Utilities.existsInList(parts[1], "fhir") && !Utilities.existsInList(parts[1], "nothing-yet")) {
      if (parts[2].equals("uv")) {
        f.igrealm = "uv";
        return new Coding("http://unstats.un.org/unsd/methods/m49/m49.htm", "001", "World");
      } else if (parts[2].equals("eu")) {
        f.igrealm = "eu";
        return new Coding("http://unstats.un.org/unsd/methods/m49/m49.htm", "150", "Europe");
      } else {
        f.igrealm = parts[2];
        return new Coding("urn:iso:std:iso:3166", parts[2].toUpperCase(), null);
      }
    } else {
      return null;
    }
  }



  private void processExtraTemplates(JsonArray templates) throws Exception {
    if (templates!=null) {
      boolean hasDefns = false;  // is definitions page in list of templates?
      boolean hasFormat = false; // are format pages in list of templates?
      boolean setExtras = false; // See if templates explicitly declare which are examples/history or whether we need to infer by name
      String name = null;
      for (JsonElement template : templates) {
        if (template.isJsonPrimitive())
          name = template.asString();
        else {
          if (!((JsonObject)template).has("name") || !((JsonObject)template).has("description"))
            throw new Exception("extraTemplates must be an array of objects with 'name' and 'description' properties");
          name = ((JsonObject)template).asString("name");
          if (((JsonObject)template).has("isHistory") || ((JsonObject)template).has("isExamples"))
            setExtras = true;
        }
        if (name.equals("defns"))
          hasDefns = true;
        else if (name.equals("format"))
          hasFormat = true;

      }
      if (!hasDefns) {
        f.extraTemplateList.add("defns");
        f.extraTemplates.put("defns", "Definitions");
      }
      if (!hasFormat) {
        f.extraTemplateList.add("format");
        f.extraTemplates.put("format", "FMT Representation");
      }
      for (JsonElement template : templates) {
        if (template.isJsonPrimitive()) {
          f.extraTemplateList.add(template.asString());
          f.extraTemplates.put(template.toString(), template.toString());
          if ("examples".equals(template.asString()))
            f.exampleTemplates.add(template.toString());
          if (template.asString().endsWith("-history"))
            f.historyTemplates.add(template.asString());
        } else {
          String templateName = ((JsonObject)template).asString("name");
          f.extraTemplateList.add(templateName);
          f.extraTemplates.put(templateName, ((JsonObject)template).asString("description"));
          if (!setExtras) {
            if (templateName.equals("examples"))
              f.exampleTemplates.add(templateName);
            if (templateName.endsWith("-history"))
              f.historyTemplates.add(templateName);
          } else if (((JsonObject)template).has("isExamples") && ((JsonObject)template).asBoolean("isExamples")) {
            f.exampleTemplates.add(templateName);
          } else if (((JsonObject)template).has("isHistory") && ((JsonObject)template).asBoolean("isHistory")) {
            f.historyTemplates.add(templateName);
          }
        }
      }
    }
  }

  void handlePreProcess(JsonObject pp, String root) throws Exception {
    String path = Utilities.path(root, str(pp, "folder"));
    if (checkDir(path, true)) {
      f.prePagesDirs.add(path);
      String prePagesXslt = null;
      if (pp.has("transform")) {
        prePagesXslt = Utilities.path(root, str(pp, "transform"));
        checkFile(prePagesXslt);
      }
      String relativePath = null;
      if (pp.has("relativePath")) {
        relativePath = str(pp, "relativePath");
      }
      //      System.out.println("Pre-Process: "+path+" = "+relativePath+" | "+prePagesXslt);
      PublisherFields.PreProcessInfo ppinfo = new PublisherFields.PreProcessInfo(prePagesXslt, relativePath);
      f.preProcessInfo.put(path, ppinfo);
    }
  }

  private String getPathResourceDirectory(ImplementationGuide.ImplementationGuideDefinitionParameterComponent p) throws IOException {
    if ( p.getValue().endsWith("*")) {
      return Utilities.path(f.rootDir, p.getValue().substring(0, p.getValue().length() - 1)) + "*";
    }
    return Utilities.path(f.rootDir, p.getValue());
  }

  private void scanDirectories(String dir, List<String> extraDirs) {
    f.fetcher.scanFolders(dir, extraDirs);

  }


  private void loadSuppressedMessages(String messageFile, String path) throws Exception {
    File f = new File(messageFile);
    if (!f.exists()) {
      this.f.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.NOTFOUND, path, "Supressed messages file not found", ValidationMessage.IssueSeverity.ERROR));
    } else {
      String s = FileUtilities.fileToString(messageFile);
      if (s.toLowerCase().startsWith("== suppressed messages ==")) {
        String[] lines = s.split("\\r?\\n");
        String reason = null;
        for (int i = 1; i < lines.length; i++) {
          String l = lines[i].trim();
          if (!Utilities.noString(l)) {
            if (l.startsWith("# ")) {
              reason = l.substring(2);
            } else {
              if (reason == null) {
                this.f.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.NOTFOUND, path, "Supressed messages file has errors with no reason ("+l+")", ValidationMessage.IssueSeverity.ERROR));
                this.f.suppressedMessages.add(l, "?pub-msg-1?");
              } else {
                this.f.suppressedMessages.add(l, reason);
              }
            }
          }
        }
      } else {
        this.f.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.NOTFOUND, path, "Supressed messages file is not using the new format (see https://confluence.hl7.org/display/FHIR/Implementation+Guide+Parameters)", ValidationMessage.IssueSeverity.ERROR));
        InputStreamReader r = new InputStreamReader(new FileInputStream(messageFile));
        StringBuilder b = new StringBuilder();
        while (r.ready()) {
          char c = (char) r.read();
          if (c == '\r' || c == '\n') {
            if (b.length() > 0)
              this.f.suppressedMessages.add(b.toString(), "?pub-msg-2?");
            b = new StringBuilder();
          } else
            b.append(c);
        }
        if (b.length() > 0)
          this.f.suppressedMessages.add(b.toString(), "?pub-msg-3?");
        r.close();
      }
    }
  }


  private int clearErrors(String dirName) throws FileNotFoundException, IOException {
    File dir = new File(dirName);
    int i = 0;
    for (File f : dir.listFiles()) {
      String s = FileUtilities.fileToString(f);
      if (s.contains("OperationOutcome")) {
        f.delete();
        i++;
      }
    }
    return i;
  }


  private SimpleWorkerContext loadCorePackage() throws Exception {
    NpmPackage pi = null;

    String v = f.version;

    if (Utilities.noString(f.igPack)) {
      log("Core Package "+VersionUtilities.packageForVersion(v)+"#"+v);
      pi = f.pcm.loadPackage(VersionUtilities.packageForVersion(v), v);
    } else {
      log("Load Core from provided file "+ f.igPack);
      pi = NpmPackage.fromPackage(new FileInputStream(f.igPack));
    }
    if (pi == null) {
      throw new Error("Unable to load core package!");
    }
    if (v.equals("current")) {
      // currency of the current core package is a problem, since its not really version controlled.
      // we'll check for a specified version...
      logDebugMessage(LogCategory.INIT, "Checking hl7.fhir.core-"+v+" currency");
      int cacheVersion = getBuildVersionForCorePackage(pi);
      int lastAcceptableVersion = ToolsVersion.TOOLS_VERSION;
      if (cacheVersion < lastAcceptableVersion) {
        logDebugMessage(LogCategory.INIT, "Updating hl7.fhir.core-"+ f.version +" package from source (too old - is "+cacheVersion+", must be "+lastAcceptableVersion);
        pi = f.pcm.addPackageToCache("hl7.fhir.core", "current", fetchFromSource("hl7.fhir.core-"+v, getMasterSource()), getMasterSource());
      } else {
        logDebugMessage(LogCategory.INIT, "   ...  ok: is "+cacheVersion+", must be "+lastAcceptableVersion);
      }
    }
    logDebugMessage(LogCategory.INIT, "Load hl7.fhir.core-"+v+" package from "+pi.summary());
    f.npmList.add(pi);

    SpecMapManager spm = loadSpecDetails(FileUtilities.streamToBytes(pi.load("other", "spec.internals")), "basespec", pi, f.specPath);
    SimpleWorkerContext sp;
    IContextResourceLoader loader = new PublisherLoader(pi, spm, f.specPath, f.igpkp).makeLoader();
    sp = new SimpleWorkerContext.SimpleWorkerContextBuilder().withTerminologyCachePath(f.vsCache).fromPackage(pi, loader, false);
    sp.loadBinariesFromFolder(pi);
    sp.setForPublication(true);
    sp.setSuppressedMappings(f.suppressedMappings);
    if (!f.version.equals(Constants.VERSION)) {
      // If it wasn't a 4.0 source, we need to set the ids because they might not have been set in the source
      ProfileUtilities utils = new ProfileUtilities(f.context, new ArrayList<ValidationMessage>(), f.igpkp);
      for (StructureDefinition sd : new ContextUtilities(sp, f.suppressedMappings).allStructures()) {
        utils.setIds(sd, true);
      }
    }
    return sp;
  }

  private int getBuildVersionForCorePackage(NpmPackage pi) throws IOException {
    if (!pi.getNpm().has("tools-version"))
      return 0;
    return pi.getNpm().asInteger("tools-version");
  }


  private Parameters makeExpProfile() {
    Parameters ep  = new Parameters();
    ep.addParameter("x-system-cache-id", "dc8fd4bc-091a-424a-8a3b-6198ef146891"); // change this to blow the cache
    // all defaults....
    return ep;
  }


  private void loadConversionVersion(String version) throws FHIRException, IOException {
    String v = VersionUtilities.getMajMin(version);
    if (VersionUtilities.versionsMatch(v, f.context.getVersion())) {
      throw new FHIRException("Unable to load conversion version "+version+" when base version is already "+ f.context.getVersion());
    }
    String pid = VersionUtilities.packageForVersion(v);
    log("Load "+pid);
    NpmPackage npm = f.pcm.loadPackage(pid);
    SpecMapManager spm = loadSpecDetails(FileUtilities.streamToBytes(npm.load("other", "spec.internals")), "convSpec"+v, npm, npm.getWebLocation());
    IContextResourceLoader loader = ValidatorUtils.loaderForVersion(npm.fhirVersion(), new PatchLoaderKnowledgeProvider(npm, spm));
    if (loader.getTypes().contains("StructureMap")) {
      loader.getTypes().remove("StructureMap");
    }
    loader.setPatchUrls(true);
    loader.setLoadProfiles(false);
    f.context.loadFromPackage(npm, loader);
  }


  private void loadPubPack() throws FHIRException, IOException {
    NpmPackage npm = f.pcm.loadPackage(CommonPackages.ID_PUBPACK, CommonPackages.VER_PUBPACK);
    f.context.loadFromPackage(npm, null);
    npm = f.pcm.loadPackage(CommonPackages.ID_XVER, CommonPackages.VER_XVER);
    f.context.loadFromPackage(npm, null);
  }

  private void loadUTG() throws FHIRException, IOException {
    String vs = getUTGPackageName();
    if (vs != null) {
      NpmPackage npm = f.pcm.loadPackage(vs, null);
      SpecMapManager spm = new SpecMapManager(FileUtilities.streamToBytes(npm.load("other", "spec.internals")), npm.vid(), npm.fhirVersion());
      IContextResourceLoader loader = new PublisherLoader(npm, spm, npm.getWebLocation(), f.igpkp).makeLoader();
      f.context.loadFromPackage(npm, loader);
    }
  }

  private String getUTGPackageName() throws FHIRException, IOException {
    String vs = null;
    if (VersionUtilities.isR3Ver(f.version)) {
      vs = "hl7.terminology.r3";
    } else if (VersionUtilities.isR4Ver(f.version) || VersionUtilities.isR4BVer(f.version)) {
      vs = "hl7.terminology.r4";
    } else if (VersionUtilities.isR5Ver(f.version)) {
      vs = "hl7.terminology.r5";
    } else if (VersionUtilities.isR6Ver(f.version)) {
      vs = "hl7.terminology.r5";
    }
    return vs;
  }

  private String getToolingPackageName() throws FHIRException, IOException {
    String pn = null;
    if (VersionUtilities.isR3Ver(f.version)) {
      pn = "hl7.fhir.uv.tools.r3";
    } else if (VersionUtilities.isR4Ver(f.version) || VersionUtilities.isR4BVer(f.version)) {
      pn = "hl7.fhir.uv.tools.r4";
    } else if (VersionUtilities.isR5Ver(f.version)) {
      pn = "hl7.fhir.uv.tools.r5";
    } else if (VersionUtilities.isR6Ver(f.version)) {
      pn = "hl7.fhir.uv.tools.r5";
    }
    return pn;
  }

  private String getExtensionsPackageName() throws FHIRException, IOException {
    String vs = null;
    if (VersionUtilities.isR3Ver(f.version)) {
      vs = "hl7.fhir.uv.extensions.r3";
    } else if (VersionUtilities.isR4Ver(f.version) || VersionUtilities.isR4BVer(f.version)) {
      vs = "hl7.fhir.uv.extensions.r4";
    } else if (VersionUtilities.isR5Ver(f.version)) {
      vs = "hl7.fhir.uv.extensions.r5";
    } else if (VersionUtilities.isR6Ver(f.version)) {
      vs = "hl7.fhir.uv.extensions.r6";
    }
    return vs;
  }


  private boolean dependsOnUTG(List<ImplementationGuide.ImplementationGuideDependsOnComponent> dependsOn) {
    for (ImplementationGuide.ImplementationGuideDependsOnComponent d : dependsOn) {
      if (d.hasPackageId() && d.getPackageId().contains("hl7.terminology")) {
        return true;
      }
      if (d.hasUri() && d.getUri().contains("terminology.hl7")) {
        return true;
      }
    }
    return false;
  }


  private boolean dependsOnTooling(List<ImplementationGuide.ImplementationGuideDependsOnComponent> dependsOn) {
    for (ImplementationGuide.ImplementationGuideDependsOnComponent d : dependsOn) {
      if (d.hasPackageId() && d.getPackageId().contains("hl7.fhir.uv.tools")) {
        return true;
      }
      if (d.hasUri() && d.getUri().contains("hl7.org/fhir/tools")) {
        return true;
      }
    }
    return false;
  }


  private boolean dependsOnExtensions(List<ImplementationGuide.ImplementationGuideDependsOnComponent> dependsOn) {
    for (ImplementationGuide.ImplementationGuideDependsOnComponent d : dependsOn) {
      if (d.hasPackageId() && Utilities.existsInList(d.getPackageId(), "hl7.fhir.uv.extensions", "hl7.fhir.uv.extensions.r3", "hl7.fhir.uv.extensions.r4", "hl7.fhir.uv.extensions.r5", "hl7.fhir.uv.extensions.r6")) {
        return true;
      }
      if (d.hasUri() && d.getUri().contains("hl7.org/fhir/extensions")) {
        return true;
      }
    }
    return false;
  }


  private void loadIg(ImplementationGuide.ImplementationGuideDependsOnComponent dep, int index, boolean loadDeps) throws Exception {
    String name = dep.getId();
    if (!dep.hasId()) {
      logMessage("Dependency '"+idForDep(dep)+"' has no id, so can't be referred to in markdown in the IG");
      name = "u"+UUIDUtilities.makeUuidLC().replace("-", "");
    }
    if (!isValidIGToken(name))
      throw new Exception("IG Name must be a valid token ("+name+")");
    String canonical = determineCanonical(dep.getUri(), "ImplementationGuide.dependency["+index+"].url");
    String packageId = dep.getPackageId();
    if (Utilities.noString(packageId))
      packageId = f.pcm.getPackageId(canonical);
    if (Utilities.noString(canonical) && !Utilities.noString(packageId))
      canonical = f.pcm.getPackageUrl(packageId);
    if (Utilities.noString(canonical))
      throw new Exception("You must specify a canonical URL for the IG "+name);
    String igver = dep.getVersion();
    if (Utilities.noString(igver)) {
      igver = f.pcm.getLatestVersion(packageId, true);
      if (Utilities.noString(igver)) {
        throw new Exception("The latest version could not be determined, so you must specify a version for the IG "+packageId+" ("+canonical+")");
      }
    }

    NpmPackage pi = packageId == null ? null : f.pcm.loadPackage(packageId, igver);
    if (pi == null) {
      pi = resolveDependency(canonical, packageId, igver);
      if (pi == null) {
        if (Utilities.noString(packageId))
          throw new Exception("Package Id for guide at "+canonical+" is unknown (contact FHIR Product Director");
        else
          throw new Exception("Unknown Package "+packageId+"#"+igver);
      }
    }
    if (dep.hasUri() && !dep.getUri().contains("/ImplementationGuide/")) {
      String cu = getIgUri(pi);
      if (cu != null) {
        f.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.INFORMATIONAL, "ImplementationGuide.dependency["+index+"].url",
                "The correct canonical URL for this dependency is "+cu, ValidationMessage.IssueSeverity.INFORMATION));
      }
    }

    loadIGPackage(name, canonical, packageId, igver, pi, loadDeps);

  }

  private void loadIg(String name, String packageId, String igver, String uri, int index, boolean loadDeps) throws Exception {
    String canonical = determineCanonical(uri, "ImplementationGuide.dependency["+index+"].url");
    if (Utilities.noString(canonical) && !Utilities.noString(packageId))
      canonical = f.pcm.getPackageUrl(packageId);
    if (Utilities.noString(canonical))
      throw new Exception("You must specify a canonical URL for the IG "+name);


    NpmPackage pi = packageId == null ? null : f.pcm.loadPackage(packageId, igver);
    if (pi == null) {
      pi = resolveDependency(canonical, packageId, igver);
      if (pi == null) {
        if (Utilities.noString(packageId))
          throw new Exception("Package Id for guide at "+canonical+" is unknown (contact FHIR Product Director");
        else
          throw new Exception("Unknown Package "+packageId+"#"+igver);
      }
    }
    loadIGPackage(name, canonical, packageId, igver, pi, loadDeps);
  }

  private void loadIGPackage(String name, String canonical, String packageId, String igver, NpmPackage pi, boolean loadDeps)
          throws IOException {
    if (pi != null)
      f.npmList.add(pi);
    logDebugMessage(LogCategory.INIT, "Load "+name+" ("+canonical+") from "+packageId+"#"+igver);


    String webref = pi.getWebLocation();
    webref = PackageHacker.fixPackageUrl(webref);

    SpecMapManager igm = pi.hasFile("other", "spec.internals") ?  new SpecMapManager( FileUtilities.streamToBytes(pi.load("other", "spec.internals")), pi.vid(), pi.fhirVersion()) : SpecMapManager.createSpecialPackage(pi, f.pcm);
    igm.setName(name);
    igm.setBase(canonical);
    igm.setBase2(PackageHacker.fixPackageUrl(pi.url()));
    igm.setNpm(pi);
    f.specMaps.add(igm);
    if (!VersionUtilities.versionsCompatible(f.version, pi.fhirVersion())) {
      if (!pi.isWarned()) {
        f.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, f.sourceIg.fhirType()+"/"+ f.sourceIg.getId(), "This IG is version "+ f.version +", while the IG '"+pi.name()+"' is from version "+pi.fhirVersion(), ValidationMessage.IssueSeverity.ERROR));
        log("Version mismatch. This IG is version "+ f.version +", while the IG '"+pi.name()+"' is from version "+pi.fhirVersion()+" (will try to run anyway)");
        pi.setWarned(true);
      }
    }

    igm.setLoader(loadFromPackage(name, canonical, pi, webref, igm, loadDeps));
  }

  private boolean isValidIGToken(String tail) {
    if (tail == null || tail.length() == 0)
      return false;
    boolean result = Utilities.isAlphabetic(tail.charAt(0));
    for (int i = 1; i < tail.length(); i++) {
      result = result && (Utilities.isAlphabetic(tail.charAt(i)) || Utilities.isDigit(tail.charAt(i)) || (tail.charAt(i) == '_'));
    }
    return result;
  }

  private String idForDep(ImplementationGuide.ImplementationGuideDependsOnComponent dep) {
    if (dep.hasPackageId()) {
      return dep.getPackageId();
    }
    if (dep.hasUri()) {
      return dep.getUri();
    }
    return "{no id}";
  }



  private String getIgUri(NpmPackage pi) throws IOException {
    for (String rs : pi.listResources("ImplementationGuide")) {
      JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(pi.loadResource(rs));
      if (json.has("packageId") && json.asString("packageId").equals(pi.name()) && json.has("url")) {
        return json.asString("url");
      }
    }
    return null;
  }



  public IContextResourceLoader loadFromPackage(String name, String canonical, NpmPackage pi, String webref, SpecMapManager igm, boolean loadDeps) throws IOException {
    if (loadDeps) { // we do not load dependencies for packages the tooling loads on it's own initiative
      for (String dep : pi.dependencies()) {
        if (!f.context.hasPackage(dep)) {
          String fdep = fixPackageReference(dep);
          String coreVersion = VersionUtilities.getVersionForPackage(fdep);
          if (coreVersion != null) {
            log("Ignore Dependency on Core FHIR "+fdep+", from package '"+pi.name()+"#"+pi.version()+"'");
          } else {
            NpmPackage dpi = f.pcm.loadPackage(fdep);
            if (dpi == null) {
              logDebugMessage(LogCategory.CONTEXT, "Unable to find package dependency "+fdep+". Will proceed, but likely to be be errors in qa.html etc");
            } else {
              f.npmList.add(dpi);
              if (!VersionUtilities.versionsCompatible(f.version, pi.fhirVersion())) {
                if (!pi.isWarned()) {
                  f.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, f.sourceIg.fhirType()+"/"+ f.sourceIg.getId(), "This IG is for FHIR version "+ f.version +", while the package '"+pi.name()+"#"+pi.version()+"' is for FHIR version "+pi.fhirVersion(), ValidationMessage.IssueSeverity.ERROR));
                  log("Version mismatch. This IG is for FHIR version "+ f.version +", while the package '"+pi.name()+"#"+pi.version()+"' is for FHIR version "+pi.fhirVersion()+" (will ignore that and try to run anyway)");
                  pi.setWarned(true);
                }
              }
              SpecMapManager smm = null;
              logDebugMessage(LogCategory.PROGRESS, "Load package dependency "+fdep);
              try {
                smm = dpi.hasFile("other", "spec.internals") ?  new SpecMapManager(FileUtilities.streamToBytes(dpi.load("other", "spec.internals")), dpi.vid(), dpi.fhirVersion()) : SpecMapManager.createSpecialPackage(dpi, f.pcm);
                smm.setName(dpi.name()+"_"+dpi.version());
                smm.setBase(dpi.canonical());
                smm.setBase2(PackageHacker.fixPackageUrl(dpi.url()));
                smm.setNpm(pi);
                f.specMaps.add(smm);
              } catch (Exception e) {
                if (!"hl7.fhir.core".equals(dpi.name())) {
                  System.out.println("Error reading SMM for "+dpi.name()+"#"+dpi.version()+": "+e.getMessage());
                }
              }

              try {
                smm.setLoader(loadFromPackage(dpi.title(), dpi.canonical(), dpi, PackageHacker.fixPackageUrl(dpi.getWebLocation()), smm, true));
              } catch (Exception e) {
                throw new IOException("Error loading "+dpi.name()+"#"+dpi.version()+": "+e.getMessage(), e);
              }
            }
          }
        }
      }
    }
    IContextResourceLoader loader = new PublisherLoader(pi, igm, webref, f.igpkp).makeLoader();
    f.context.loadFromPackage(pi, loader);
    return loader;
  }

  private String fixPackageReference(String dep) {
    String id = dep.substring(0, dep.indexOf("#"));
    String ver = dep.substring(dep.indexOf("#")+1);
    if ("hl7.fhir.uv.extensions".equals(id)) {
      if (VersionUtilities.isR3Ver(f.version)) {
        id = "hl7.fhir.uv.extensions.r3";
      } else if (VersionUtilities.isR4Ver(f.version) || VersionUtilities.isR4BVer(f.version)) {
        id = "hl7.fhir.uv.extensions.r4";
      } else if (VersionUtilities.isR5Ver(f.version)) {
        id = "hl7.fhir.uv.extensions.r5";
      }
      if (ver.endsWith("-cibuild")) {
        return id+"#"+ver.substring(0, ver.lastIndexOf("-"));
      } else {
        return id+"#"+ver;
      }
    }
    return dep;
  }

  private NpmPackage resolveDependency(String canonical, String packageId, String igver) throws Exception {
    PackageList pl;
    logDebugMessage(LogCategory.INIT, "Fetch Package history from "+Utilities.pathURL(canonical, "package-list.json"));
    try {
      pl = PackageList.fromUrl(Utilities.pathURL(canonical, "package-list.json"));
    } catch (Exception e) {
      return null;
    }
    if (!canonical.equals(pl.canonical()))
      throw new Exception("Canonical mismatch fetching package list for "+canonical+"#"+igver+", package-list.json says "+pl.canonical());
    for (PackageList.PackageListEntry e : pl.versions()) {
      if (igver.equals(e.version())) {
        InputStream src = fetchFromSource(pl.pid()+"-"+igver, Utilities.pathURL(e.path(), "package.tgz"));
        return f.pcm.addPackageToCache(pl.pid(), igver, src, Utilities.pathURL(e.path(), "package.tgz"));
      }
    }
    return null;
  }


  private void loadLinkIg(String packageId) throws Exception {
    if (!Utilities.noString(packageId)) {
      String[] p = packageId.split("\\#");
      NpmPackage pi = p.length == 1 ? f.pcm.loadPackage(p[0]) : f.pcm.loadPackage(p[0], p[1]);
      if (pi == null) {
        throw new Exception("Package Id "+packageId+" is unknown");
      }
      logDebugMessage(LogCategory.PROGRESS, "Load Link package "+packageId);
      String webref = pi.getWebLocation();
      webref = PackageHacker.fixPackageUrl(webref);

      SpecMapManager igm = pi.hasFile("other", "spec.internals") ?  new SpecMapManager( FileUtilities.streamToBytes(pi.load("other", "spec.internals")), pi.vid(), pi.fhirVersion()) : SpecMapManager.createSpecialPackage(pi, f.pcm);
      igm.setName(pi.title());
      igm.setBase(pi.canonical());
      igm.setBase2(PackageHacker.fixPackageUrl(pi.url()));
      f.linkSpecMaps.add(new PublisherUtils.LinkedSpecification(igm, pi));
    }
  }

  private void loadRelatedIg(String s) throws FHIRException, IOException {
    String role = s.substring(0, s.indexOf(":"));
    s = s.substring(s.indexOf(":")+1);

    String code = s.substring(0, s.indexOf("="));
    String id =  s.substring(s.indexOf("=")+1);

    NpmPackage npm;
    try {
      npm = f.pcm.loadPackage(id+"#dev");
    } catch (Exception e) {
      String msg = e.getMessage();
      if (msg.contains("(")) {
        msg = msg.substring(0, msg.indexOf("("));
      }
      f.relatedIGs.add(new RelatedIG(code, id, RelatedIG.RelatedIGRole.fromCode(role), msg));
      return;
    }

    if (f.mode == PublisherUtils.IGBuildMode.PUBLICATION) {
      f.relatedIGs.add(new RelatedIG(code, id, RelatedIG.RelatedIGLoadingMode.WEB, RelatedIG.RelatedIGRole.fromCode(role), npm, determineLocation(code, id)));
    } else if (Utilities.startsWithInList(npm.getWebLocation(), "http://", "https://")) {
      f.relatedIGs.add(new RelatedIG(code, id, RelatedIG.RelatedIGLoadingMode.CIBUILD, RelatedIG.RelatedIGRole.fromCode(role), npm));
    } else {
      f.relatedIGs.add(new RelatedIG(code, id, RelatedIG.RelatedIGLoadingMode.LOCAL, RelatedIG.RelatedIGRole.fromCode(role), npm));
    }
  }


  private void generateLoadedSnapshots() {
    for (StructureDefinition sd : new ContextUtilities(f.context, f.suppressedMappings).allStructures()) {
      if (!sd.hasSnapshot() && sd.hasBaseDefinition()) {
        generateSnapshot(sd);
      }
    }
  }


  private void generateSnapshot(StructureDefinition sd) {
    List<ValidationMessage> messages = new ArrayList<>();
    ProfileUtilities utils = new ProfileUtilities(f.context, messages, f.igpkp);
    StructureDefinition base = f.context.fetchResource(StructureDefinition.class, sd.getBaseDefinition());
    if (base == null) {
      System.out.println("Cannot find or generate snapshot for base definition "+sd.getBaseDefinition()+" from "+sd.getUrl());
    } else {
      if (!base.hasSnapshot()) {
        generateSnapshot(base);
      }
      utils.setIds(sd, true);
      utils.setSuppressedMappings(f.suppressedMappings);
      try {
        utils.generateSnapshot(base, sd, sd.getUrl(), Utilities.extractBaseUrl(base.getWebPath()), sd.getName());
        if (!sd.hasSnapshot()) {
          System.out.println("Unable to generate snapshot for "+sd.getUrl()+": "+messages.toString());
        }
      } catch (Exception e) {
        System.out.println("Exception generating snapshot for "+sd.getUrl()+": "+e.getMessage());
      }
    }
    org.hl7.fhir.r5.elementmodel.Element element = (Element) sd.getUserData(UserDataNames.pub_element);
    if (element != null) {
      element.setUserData(UserDataNames.SNAPSHOT_messages, messages);
    }
  }

  private boolean dependsOnUTG(JsonArray arr) throws Exception {
    if (arr == null) {
      return false;
    }
    for (JsonElement d : arr) {
      JsonObject dep = (JsonObject) d;
      String canonical = ostr(dep, "location");
      if (canonical != null && canonical.contains("terminology.hl7")) {
        return true;
      }
      String packageId = ostr(dep, "package");
      if (packageId != null && packageId.contains("hl7.terminology")) {
        return true;
      }
    }
    return false;
  }



  private boolean needFile(String s) {
    if (s.endsWith(".css") && !isChild())
      return true;
    if (s.startsWith("tbl"))
      return true;
    if (s.endsWith(".js"))
      return true;
    if (s.startsWith("icon"))
      return true;
    if (Utilities.existsInList(s, "modifier.png", "alert.jpg", "tree-filter.png", "mustsupport.png", "information.png", "summary.png", "new.png", "lock.png", "external.png", "cc0.png", "target.png", "link.svg"))
      return true;

    return false;
  }

  public SpecMapManager loadSpecDetails(byte[] bs, String name, NpmPackage npm, String path) throws IOException {
    SpecMapManager map = new SpecMapManager(bs, npm.vid(), f.version);
    map.setBase(PackageHacker.fixPackageUrl(path));
    map.setName(name);
    f.specMaps.add(map);
    return map;
  }

  private String getMasterSource() {
    if (VersionUtilities.isR2Ver(f.version)) return "http://hl7.org/fhir/DSTU2/hl7.fhir.r2.core.tgz";
    if (VersionUtilities.isR2BVer(f.version)) return "http://hl7.org/fhir/2016May/hl7.fhir.r2b.core.tgz";
    if (VersionUtilities.isR3Ver(f.version)) return "http://hl7.org/fhir/STU3/hl7.fhir.r3.core.tgz";
    if (VersionUtilities.isR4Ver(f.version)) return "http://hl7.org/fhir/R4/hl7.fhir.r4.core.tgz";
    if (Constants.VERSION.equals(f.version)) return "http://hl7.org/fhir/R5/hl7.fhir.r5.core.tgz";
    throw new Error("unknown version "+ f.version);
  }

  private InputStream fetchFromSource(String id, String source) throws IOException {
    logDebugMessage(LogCategory.INIT, "Fetch "+id+" package from "+source);
    URL url = new URL(source+"?nocache=" + System.currentTimeMillis());
    URLConnection c = url.openConnection();
    return c.getInputStream();
  }


  private String determineLocation(String code, String id) throws JsonException, IOException {
    // to determine the location of this IG in publication mode, we have to figure out what version we are publishing against
    // this is in the publication request
    // then, we need to look the package
    // if it's already published, we use that location
    // if it's to be published, we find #current, extract that publication request, and use that path (check version)
    // otherwise, bang
    JsonObject pr = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(new File(Utilities.path(FileUtilities.getDirectoryForFile(f.configFile), "publication-request.json")));
    String rigV = pr.forceObject("related").asString(code);
    if (rigV == null) {
      throw new FHIRException("No specified Publication version for relatedIG "+code);
    }
    NpmPackage npm;
    try {
      npm = f.pcm.loadPackage(id, rigV);
    } catch (Exception e) {
      if (!e.getMessage().toLowerCase().contains("not found")) {
        throw new FHIRException("Error looking for "+id+"#"+rigV+" for relatedIG  "+code+": "+e.getMessage());
      }
      npm = null;
    }
    if (npm != null) {
      if (isMilestoneBuild()) {
        return npm.canonical();
      } else {
        return npm.getWebLocation();
      }
    }
    JsonObject json = null;
    try {
      npm = f.pcm.loadPackage(id, "current");
      json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(npm.load("other", "publication-request.json"));
    } catch (Exception e) {
      throw new FHIRException("Error looking for publication request in  "+id+"#current for relatedIG  "+code+": "+e.getMessage());
    }
    String location = json.asString("path");
    String canonical = npm.canonical();
    String version = json.asString("version");
    String mode = json.asString("mode");
    if (!rigV.equals(version)) {
      throw new FHIRException("The proposed publication for relatedIG  "+code+" is a different version: "+version+" instead of "+rigV);
    }
    if ("milestone".equals(mode) && isMilestoneBuild()) {
      return canonical;
    } else {
      return location;
    }
  }

  public void load() throws Exception {
    f.validationFetcher.initOtherUrls();
    f.fileList.clear();
    f.changeList.clear();
    f.bndIds.clear();

    FetchedFile igf = f.fetcher.fetch(f.igName);
    noteFile(IG_NAME, igf);
    if (f.sourceIg == null) // old JSON approach
      f.sourceIg = (ImplementationGuide) parse(igf);
    if (isNewML()) {
      log("Load Translations");
      f.sourceIg.setLanguage(f.defaultTranslationLang);
      // but we won't load the translations yet - it' yet to be fully populated. we'll wait till everything else is loaded
    }
    log("Load Content");
    f.publishedIg = f.sourceIg.copy();
    FetchedResource igr = igf.addResource("$IG");
    //      loadAsElementModel(igf, igr, null);
    igr.setResource(f.publishedIg);
    igr.setElement(convertToElement(null, f.publishedIg));
    igr.setId(f.sourceIg.getId()).setTitle(f.publishedIg.getName());
    Locale locale = inferDefaultNarrativeLang(true);
    f.context.setLocale(locale);
    f.dependentIgFinder = new DependentIGFinder(f.sourceIg.getPackageId());

    for (ImplementationGuide.ImplementationGuideDependsOnComponent dep : f.publishedIg.getDependsOn()) {
      if (dep.hasPackageId() && dep.getPackageId().contains("@npm:")) {
        if (!dep.hasId()) {
          dep.setId(dep.getPackageId().substring(0, dep.getPackageId().indexOf("@npm:")));
        }
        dep.setPackageId(dep.getPackageId().substring(dep.getPackageId().indexOf("@npm:")+5));
        dep.getPackageIdElement().setUserData(UserDataNames.IG_DEP_ALIASED, true);
      }
    }

    loadMappingSpaces(f.context.getBinaryForKey("mappingSpaces.details"));
    f.validationFetcher.getMappingUrls().addAll(f.mappingSpaces.keySet());
    f.validationFetcher.getOtherUrls().add(f.publishedIg.getUrl());
    for (SpecMapManager s : f.specMaps) {
      f.validationFetcher.getOtherUrls().add(s.getBase());
      if (s.getBase2() != null) {
        f.validationFetcher.getOtherUrls().add(s.getBase2());
      }
    }

    if (f.npmName == null) {
      throw new Exception("A package name (npm-name) is required to publish implementation guides. For further information, see http://wiki.hl7.org/index.php?title=FHIR_NPM_Package_Spec#Package_name");
    }
    if (!f.publishedIg.hasLicense())
      f.publishedIg.setLicense(licenseAsEnum());
    if (!f.publishedIg.hasPackageId())
      f.publishedIg.setPackageId(f.npmName);
    if (!f.publishedIg.hasFhirVersion())
      f.publishedIg.addFhirVersion(Enumerations.FHIRVersion.fromCode(f.version));
    if (!f.publishedIg.hasVersion() && f.businessVersion != null)
      f.publishedIg.setVersion(f.businessVersion);
    if (!f.publishedIg.hasExtension(ExtensionDefinitions.EXT_WORKGROUP) && f.wgm != null) {
      f.publishedIg.addExtension(ExtensionDefinitions.EXT_WORKGROUP, new CodeType(f.wgm));
    }

    if (!VersionUtilities.isSemVer(f.publishedIg.getVersion())) {
      if (f.mode == PublisherUtils.IGBuildMode.AUTOBUILD) {
        throw new Error("The version "+ f.publishedIg.getVersion()+" is not a valid semantic version so cannot be published in the ci-build");
      } else {
        log("The version "+ f.publishedIg.getVersion()+" is not a valid semantic version so cannot be published in the ci-build");
        igf.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.EXCEPTION, "ImplementationGuide.version", "The version "+ f.publishedIg.getVersion()+" is not a valid semantic version and will not be acceptible to the ci-build, nor will it be a valid vesion in the NPM package system", ValidationMessage.IssueSeverity.WARNING));
      }
    }
    String id = f.npmName;
    if (f.npmName.startsWith("hl7.")) {
      if (!id.matches("[A-Za-z0-9\\-\\.]{1,64}"))
        throw new FHIRException("The generated ID is '"+id+"' which is not valid");
      FetchedResource r = fetchByResource("ImplementationGuide", f.publishedIg.getId());
      f.publishedIg.setId(id);
      f.publishedIg.setUrl(f.igpkp.getCanonical()+"/ImplementationGuide/"+id);
      if (r != null) { // it better be....
        r.setId(id);
        r.getElement().getNamedChild("id").setValue(id);
        r.getElement().getNamedChild("url").setValue(f.publishedIg.getUrl());
      }
    } else if (!id.equals(f.publishedIg.getId()))
      f.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "ImplementationGuide.id", "The Implementation Guide Resource id should be "+id, ValidationMessage.IssueSeverity.WARNING));

    f.packageInfo = new PackageInformation(f.publishedIg.getPackageId(), f.publishedIg.getVersion(), f.context.getVersion(), new Date(), f.publishedIg.getName(), f.igpkp.getCanonical(), f.targetOutput);

    // Cql Compile
    f.cql = new CqlSubSystem(f.npmList, f.binaryPaths, new CqlResourceLoader(f.version), this, f.context.getUcumService(), f.publishedIg.getPackageId(), f.igpkp.getCanonical());
    if (f.binaryPaths.size() > 0) {
      f.cql.execute();
    }
    f.fetcher.setRootDir(f.rootDir);
    f.loadedIds = new HashMap<>();
    f.duplicateInputResourcesDetected = false;
    loadCustomResources();

    if (f.sourceDir != null || f.igpkp.isAutoPath()) {
      loadResources(igf);
    }
    loadSpreadsheets(igf);
    loadMappings(igf);
    loadBundles(igf);
    loadTranslationSupplements(igf);

    f.context.getCutils().setMasterSourceNames(f.specMaps.get(0).getTargets());
    f.context.getCutils().setLocalFileNames(pageTargets());

    loadConformance1(true);
    for (String s : f.resourceFactoryDirs) {
      FileUtilities.clearDirectory(s);
    }
    if (!f.testDataFactories.isEmpty()) {
      processFactories(f.testDataFactories);
    }
    loadResources2(igf);

    loadConformance1(false);

    int i = 0;
    Set<String> resLinks = new HashSet<>();
    for (ImplementationGuide.ImplementationGuideDefinitionResourceComponent res : f.publishedIg.getDefinition().getResource()) {
      if (!res.hasReference()) {
        throw new Exception("Missing source reference on a resource in the IG with the name '"+res.getName()+"' (index = "+i+")");
      } else if (!res.getReference().hasReference()) {
        throw new Exception("Missing source reference.reference on a resource in the IG with the name '"+res.getName()+"' (index = "+i+")");
      } else if (resLinks.contains(res.getReference().getReference())) {
        throw new Exception("Duplicate source reference '"+res.getReference().getReference()+"' on a resource in the IG with the name '"+res.getName()+"' (index = "+i+")");
      } else {
        resLinks.add(res.getReference().getReference());
      }
      i++;
      FetchedFile f = null;
      if (!this.f.bndIds.contains(res.getReference().getReference()) && !res.hasUserData(UserDataNames.pub_loaded_resource)) {
        logDebugMessage(LogCategory.INIT, "Load "+res.getReference());
        f = this.f.fetcher.fetch(res.getReference(), igf);
        if (!f.hasTitle() && res.getName() != null)
          f.setTitle(res.getName());
        boolean rchanged = noteFile(res, f);
        if (rchanged) {
          if (res.hasExtension(ExtensionDefinitions.EXT_BINARY_FORMAT_NEW)) {
            loadAsBinaryResource(f, f.addResource(f.getName()), res, res.getExtensionString(ExtensionDefinitions.EXT_BINARY_FORMAT_NEW), "listed in IG");
          } else if (res.hasExtension(ExtensionDefinitions.EXT_BINARY_FORMAT_OLD)) {
            loadAsBinaryResource(f, f.addResource(f.getName()), res, res.getExtensionString(ExtensionDefinitions.EXT_BINARY_FORMAT_OLD), "listed in IG");
          } else {
            loadAsElementModel(f, f.addResource(f.getContentType()), res, false, "listed in IG");
          }
          if (res.hasExtension(ExtensionDefinitions.EXT_BINARY_LOGICAL)) {
            f.setLogical(res.getExtensionString(ExtensionDefinitions.EXT_BINARY_LOGICAL));
          }
        }
      }
      if (res.hasProfile()) {
        if (f != null && f.getResources().size()!=1)
          throw new Exception("Can't have an exampleFor unless the file has exactly one resource");
        FetchedResource r = res.hasUserData(UserDataNames.pub_loaded_resource) ? (FetchedResource) res.getUserData(UserDataNames.pub_loaded_resource) : f.getResources().get(0);
        if (r == null)
          throw new Exception("Unable to resolve example canonical " + res.getProfile().get(0).asStringValue());
        this.f.examples.add(r);
        String ref = res.getProfile().get(0).getValueAsString();
        if (Utilities.isAbsoluteUrl(ref)) {
          r.setExampleUri(stripVersion(ref));
        } else {
          r.setExampleUri(Utilities.pathURL(this.f.igpkp.getCanonical(), ref));
        }
        // Redo this because we now have example information
        if (f!=null)
          this.f.igpkp.findConfiguration(f, r);
      }
      // TestPlan Check
      if (res.hasReference() && res.getReference().hasReference() && res.getReference().getReference().contains("TestPlan/")) {
        if (f == null) {
          f = this.f.fetcher.fetch(res.getReference(), igf);
        }
        if (f != null) {
          FetchedResource r = res.hasUserData(UserDataNames.pub_loaded_resource) ? (FetchedResource) res.getUserData(UserDataNames.pub_loaded_resource) : f.getResources().get(0);
          if (r != null) {
            this.f.testplans.add(r);
            try {
              Element t = r.getElement();
              if (t != null) {
                // Set title of TestPlan FetchedResource
                String tsTitle = t.getChildValue("title");
                if (tsTitle != null) {
                  r.setTitle(tsTitle);
                }
                // Add TestPlan scope references
                List<Element> profiles = t.getChildrenByName("scope");
                if (profiles != null) {
                  for (Element profile : profiles) {
                    String tp = profile.getChildValue("reference");
                    if (tp != null && !tp.isEmpty()) {
                      r.addTestArtifact(tp);
                    }
                  }
                }
              }
            }
            catch(Exception e) {
              this.f.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.NOTFOUND, r.fhirType()+"/"+r.getId(), "Unable to load TestPlan resource " + r.getUrlTail(), ValidationMessage.IssueSeverity.ERROR));
            }
          }
        }
      }
      // TestScript Check
      if (res.hasReference() && res.getReference().hasReference() && res.getReference().getReference().contains("TestScript/")) {
        if (f == null) {
          f = this.f.fetcher.fetch(res.getReference(), igf);
        }
        if (f != null) {
          FetchedResource r = res.hasUserData(UserDataNames.pub_loaded_resource) ? (FetchedResource) res.getUserData(UserDataNames.pub_loaded_resource) : f.getResources().get(0);
          if (r != null) {
            this.f.testscripts.add(r);
            try {
              Element t = r.getElement();
              if (t != null) {
                // Set title of TestScript FetchedResource
                String tsTitle = t.getChildValue("title");
                if (tsTitle != null) {
                  r.setTitle(tsTitle);
                }
                // Add TestScript.profile references
                List<Element> profiles = t.getChildrenByName("profile");
                if (profiles != null) {
                  for (Element profile : profiles) {
                    String tp = profile.getChildValue("reference");
                    if (tp != null && !tp.isEmpty()) {
                      // R4 profile reference check
                      r.addTestArtifact(tp);
                    }
                    else {
                      // R5+ profile canonical check
                      tp = profile.getValue();
                      if (tp != null && !tp.isEmpty()) {
                        r.addTestArtifact(tp);
                      }
                    }
                  }
                }
                // Add TestScript.scope.artifact references
                List<Element> scopes = t.getChildrenByName("scope");
                if (scopes != null) {
                  for (Element scope : scopes) {
                    String tsa = scope.getChildValue("artifact");
                    if (tsa != null && !tsa.isEmpty()) {
                      r.addTestArtifact(tsa);
                    }
                  }
                }
                // Add TestScript extension for scope references
                List<Element> extensions = t.getChildrenByName("extension");
                if (extensions != null) {
                  for (Element extension : extensions) {
                    String url = extension.getChildValue("url");
                    if (url != null && url.equals("http://hl7.org/fhir/StructureDefinition/scope")) {
                      r.addTestArtifact(extension.getChildValue("valueCanonical"));
                    }
                  }
                }
              }
            }
            catch(Exception e) {
              this.f.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.NOTFOUND, r.fhirType()+"/"+r.getId(), "Unable to load test resource " + r.getUrlTail(), ValidationMessage.IssueSeverity.ERROR));
            }
          }
        }
      }
    }
    if (f.duplicateInputResourcesDetected) {
      throw new Error("Unable to continue because duplicate input resources were identified");
    }

    loadConformance1(false);
    for (PageFactory pf : f.pageFactories) {
      pf.execute(f.rootDir, f.publishedIg);
    }

    // load static pages
    loadPrePages();
    loadPages();

    if (f.publishedIg.getDefinition().hasPage())
      loadIgPages(f.publishedIg.getDefinition().getPage(), f.igPages);

    for (FetchedFile f: f.fileList) {
      for (FetchedResource r: f.getResources()) {
        this.f.resources.put(this.f.igpkp.doReplacements(this.f.igpkp.getLinkFor(r, false), r, null, null), r);
      }
    }

    for (PublisherUtils.JsonDependency dep : f.jsonDependencies) {
      ImplementationGuide.ImplementationGuideDependsOnComponent d = null;
      for (ImplementationGuide.ImplementationGuideDependsOnComponent t : f.publishedIg.getDependsOn()) {
        if (dep.getCanonical().equals(t.getUri()) || dep.getNpmId().equals(t.getPackageId())) {
          d = t;
          break;
        }
      }
      if (d == null) {
        d = f.publishedIg.addDependsOn();
        d.setUri(dep.getCanonical());
        d.setVersion(dep.getVersion());
        d.setPackageId(dep.getNpmId());
      } else {
        d.setVersion(dep.getVersion());
      }
    }

    for (ImplementationGuide.ImplementationGuideDependsOnComponent dep : f.publishedIg.getDependsOn()) {
      if (!dep.hasPackageId()) {
        dep.setPackageId(f.pcm.getPackageId(determineCanonical(dep.getUri(), null)));
      }
      if (!dep.hasPackageId())
        throw new FHIRException("Unknown package id for "+dep.getUri());
    }
    f.npm = new NPMPackageGenerator(f.publishedIg.getPackageId(), Utilities.path(f.outputDir, "package.tgz"), f.igpkp.getCanonical(), targetUrl(), PackageGenerator.PackageType.IG, f.publishedIg, f.execTime.getTime(), relatedIgMap(), !f.publishing);
    for (String v : f.generateVersions) {
      ImplementationGuide vig = f.publishedIg.copy();
      checkIgDeps(vig, v);
      f.vnpms.put(v, new NPMPackageGenerator(f.publishedIg.getPackageId()+"."+v, Utilities.path(f.outputDir, f.publishedIg.getPackageId()+"."+v+".tgz"),
              f.igpkp.getCanonical(), targetUrl(), PackageGenerator.PackageType.IG,  vig, f.execTime.getTime(), relatedIgMap(), !f.publishing, VersionUtilities.versionFromCode(v)));
    }
    if (isNewML()) {
      for (String l : allLangs()) {
        ImplementationGuide vig = (ImplementationGuide) f.langUtils.copyToLanguage(f.publishedIg, l, true);
        f.lnpms.put(l, new NPMPackageGenerator(f.publishedIg.getPackageId()+"."+l, Utilities.path(f.outputDir, f.publishedIg.getPackageId()+"."+l+".tgz"),
                f.igpkp.getCanonical(), targetUrl(), PackageGenerator.PackageType.IG, vig, f.execTime.getTime(), relatedIgMap(), !f.publishing, f.context.getVersion()));
      }
    }
    f.execTime = Calendar.getInstance();

    f.rc = new RenderingContext(f.context, f.markdownEngine, ValidationOptions.defaults(), checkAppendSlash(f.specPath), "", locale, RenderingContext.ResourceRendererMode.TECHNICAL, RenderingContext.GenerationRules.IG_PUBLISHER);
    f.rc.setTemplateProvider(f.templateProvider);
    f.rc.setServices(f.validator.getExternalHostServices());
    f.rc.setDestDir(Utilities.path(f.tempDir));
    f.rc.setProfileUtilities(new ProfileUtilities(f.context, new ArrayList<ValidationMessage>(), f.igpkp));
    f.rc.setQuestionnaireMode(RenderingContext.QuestionnaireRendererMode.TREE);
    f.rc.getCodeSystemPropList().addAll(f.codeSystemProps);
    f.rc.setParser(getTypeLoader(f.version));
    f.rc.addLink(RenderingContext.KnownLinkType.SELF, f.targetOutput);
    f.rc.setFixedFormat(f.fixedFormat);
    f.rc.setDebug(f.debug);
    f.module.defineTypeMap(f.rc.getTypeMap());
    f.rc.setDateFormatString(f.fmtDate);
    f.rc.setDateTimeFormatString(f.fmtDateTime);
    f.rc.setChangeVersion(f.versionToAnnotate);
    f.rc.setShowSummaryTable(false);
    for (FetchedFile f : f.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() instanceof CanonicalResource) {
          CanonicalResource cr = (CanonicalResource) r.getResource();
          this.f.rc.getNamedLinks().put(cr.getName(), new StringPair(cr.getWebPath(), cr.present()));
          this.f.rc.getNamedLinks().put(cr.getUrl(), new StringPair(cr.getWebPath(), cr.present()));
          this.f.rc.getNamedLinks().put(cr.getVersionedUrl(), new StringPair(cr.getWebPath(), cr.present()));
        }
      }
    }
    f.signer = new PublisherSigner(f.context, f.rootDir, f.rc.getTerminologyServiceOptions());
    f.rcLangs = new RenderingContext.RenderingContextLangs(f.rc);
    for (String l : allLangs()) {
      RenderingContext lrc = f.rc.copy(false);
      lrc.setLocale(Locale.forLanguageTag(l));
      f.rcLangs.seeLang(l, lrc);
    }
    f.r4tor4b = new R4ToR4BAnalyser(f.rc, isNewML());
    if (f.context != null) {
      f.r4tor4b.setContext(f.context);
    }
    f.realmRules = makeRealmBusinessRules();
    f.previousVersionComparator = makePreviousVersionComparator();
    f.ipaComparator = makeIpaComparator();
    f.ipsComparator = makeIpsComparator();
    //    rc.setTargetVersion(pubVersion);

    if (f.igMode) {
      boolean failed = false;
      CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
      // sanity check: every specified resource must be loaded, every loaded resource must be specified
      for (ImplementationGuide.ImplementationGuideDefinitionResourceComponent r : f.publishedIg.getDefinition().getResource()) {
        b.append(r.getReference().getReference());
        if (!r.hasUserData(UserDataNames.pub_loaded_resource)) {
          log("Resource "+r.getReference().getReference()+" not loaded");
          failed = true;
        }
      }
      for (FetchedFile f : f.fileList) {
        f.start("load-configure");
        try {
          for (FetchedResource r : f.getResources()) {
            ImplementationGuide.ImplementationGuideDefinitionResourceComponent rg = findIGReference(r.fhirType(), r.getId());
            if (!"ImplementationGuide".equals(r.fhirType()) && rg == null) {
              log("Resource "+r.fhirType()+"/"+r.getId()+" not defined");
              failed = true;
            }
            if (rg != null) {
              if (r.getElement().hasExtension(ExtensionDefinitions.EXT_RESOURCE_NAME)) {
                rg.setName(r.getElement().getExtensionValue(ExtensionDefinitions.EXT_RESOURCE_NAME).primitiveValue());
                r.getElement().removeExtension(ExtensionDefinitions.EXT_RESOURCE_NAME);
              } else if (r.getElement().hasExtension(ExtensionDefinitions.EXT_ARTIFACT_NAME)) {
                rg.setName(r.getElement().getExtensionValue(ExtensionDefinitions.EXT_ARTIFACT_NAME).primitiveValue());
              } else if (!rg.hasName()) {
                if (r.getElement().hasChild("title")) {
                  rg.setName(r.getElement().getChildValue("title"));
                } else if (r.getElement().hasChild("name") && r.getElement().getNamedChild("name").isPrimitive()) {
                  rg.setName(r.getElement().getChildValue("name"));
                } else if ("Bundle".equals(r.getElement().getName())) {
                  // If the resource is a document Bundle, get the title from the Composition
                  List<Element> entryList = r.getElement().getChildren("entry");
                  if (entryList != null && !entryList.isEmpty()) {
                    Element resource = entryList.get(0).getNamedChild("resource");
                    if (resource != null) {
                      rg.setName(resource.getChildValue("title") + " (Bundle)");
                    }
                  }
                }
              }
              if (r.getElement().hasExtension(ExtensionDefinitions.EXT_RESOURCE_DESC)) {
                rg.setDescription(r.getElement().getExtensionValue(ExtensionDefinitions.EXT_RESOURCE_DESC).primitiveValue());
                r.getElement().removeExtension(ExtensionDefinitions.EXT_RESOURCE_DESC);
              } else if (r.getElement().hasExtension(ExtensionDefinitions.EXT_ARTIFACT_DESC)) {
                rg.setDescription(r.getElement().getExtensionValue(ExtensionDefinitions.EXT_ARTIFACT_DESC).primitiveValue());
              } else if (!rg.hasDescription()) {
                if (r.getElement().hasChild("description")) {
                  Element descriptionElement = r.getElement().getNamedChild("description");
                  if (descriptionElement.hasValue()) {
                    rg.setDescription(r.getElement().getChildValue("description").trim());
                  }
                  else {
                    if (descriptionElement.hasChild("text")) {
                      Element textElement = descriptionElement.getNamedChild("text");
                      if (textElement.hasValue()) {
                        rg.setDescription(textElement.getValue().trim());
                      }
                    }
                  }
                }
              }
              if (rg.hasDescription()) {
                String desc = rg.getDescription();
                String descNew = ProfileUtilities.processRelativeUrls(desc, "", this.f.igpkp.specPath(), this.f.context.getResourceNames(), this.f.specMaps.get(0).getTargets(), pageTargets(), false);
                if (!desc.equals(descNew)) {
                  rg.setDescription(descNew);
                  //                System.out.println("change\r\n"+desc+"\r\nto\r\n"+descNew);
                }
              }
              // for the database layer later
              r.setResourceName(rg.getName());
              r.setResourceDescription(rg.getDescription());

              if (!rg.getIsExample()) {
                // If the instance declares a profile that's got the same canonical base as this IG, then the resource is an example of that profile
                Set<String> profiles = new HashSet<String>();
                if (r.getElement().hasChild("meta")) {
                  for (Element p : r.getElement().getChildren("meta").get(0).getChildren("profile")) {
                    if (!profiles.contains(p.getValue()))
                      profiles.add(p.getValue());
                  }
                }
                if (r.getElement().getName().equals("Bundle")) {
                  for (Element entry : r.getElement().getChildren("entry")) {
                    for (Element entres : entry.getChildren("resource")) {
                      if (entres.hasChild("meta")) {
                        for (Element p : entres.getChildren("meta").get(0).getChildren("profile")) {
                          if (!profiles.contains(p.getValue()))
                            profiles.add(p.getValue());
                        }
                      }
                    }
                  }
                }
                if (profiles.isEmpty()) {
                  profiles.addAll(r.getStatedProfiles());
                }
                for (String p : profiles) {
                  // Ideally we'd want to have *all* of the profiles listed as examples, but right now we can only have one, so we just overwrite and take the last.
                  if (p.startsWith(this.f.igpkp.getCanonical()+"/StructureDefinition")) {
                    rg.getProfile().add(new CanonicalType(p));
                    if (rg.getName()==null) {
                      String name = String.join(" - ", rg.getReference().getReference().split("/"));
                      rg.setName("Example " + name);
                    }
                    this.f.examples.add(r);
                    r.setExampleUri(p);
                    this.f.igpkp.findConfiguration(f, r);
                  }
                }
              }
            }
          }
        } finally {
          f.finish("load-configure");
        }
      }
      if (failed) {
        log("Resources: "+b.toString());
        throw new Exception("Invalid - see reasons"); // if this ever happens, it's a programming issue....
      }
    }
    logDebugMessage(LogCategory.INIT, "Loaded Files: "+ f.fileList.size());
    for (FetchedFile f : f.fileList) {
      logDebugMessage(LogCategory.INIT, "  "+f.getTitle()+" - "+f.getResources().size()+" Resources");
      for (FetchedResource r : f.getResources()) {
        logDebugMessage(LogCategory.INIT, "    "+r.fhirType()+"/"+r.getId());
      }

    }

    if (isNewML()) {
      List<LanguageFileProducer.TranslationUnit> translations = findTranslations(f.publishedIg.fhirType(), f.publishedIg.getId(), igf.getErrors());
      if (translations != null) {
        f.langUtils.importFromTranslations(f.publishedIg, translations, igf.getErrors());
      }
    }
    Map<String, String> ids = new HashMap<>();
    for (FetchedFile f : f.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (isBasicResource(r)) {
          if (ids.containsKey(r.getId())) {
            f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.DUPLICATE, r.fhirType(), "Because this resource is converted to a Basic resource in the package, its id clashes with "+ids.get(r.getId())+". One of them will need a different id.", ValidationMessage.IssueSeverity.ERROR));
          }
          ids.put(r.getId(), r.fhirType()+"/"+r.getId()+" from "+f.getPath());
        }
      }
    }
    f.extensionTracker.scan(f.publishedIg);
    finishLoadingCustomResources();

  }

  private boolean noteFile(String key, FetchedFile file) {
    FetchedFile existing = f.altMap.get(key);
    if (existing == null || existing.getTime() != file.getTime() || existing.getHash() != file.getHash()) {
      f.fileList.add(file);
      f.altMap.put(key, file);
      addFile(file);
      return true;
    } else {
      for (FetchedFile f : f.fileList) {
        if (file.getPath().equals(f.getPath())) {
          throw new Error("Attempt to process the same source resource twice: "+file.getPath());
        }
      }
      f.fileList.add(existing); // this one is already parsed
      return false;
    }
  }


  private void finishLoadingCustomResources() {
    for (StructureDefinition sd : f.customResources) {
      FetchedResource r = findLoadedStructure(sd);
      if (r == null) {
        System.out.println("Custom Resource "+sd.getId()+" not loaded normally");
        System.exit(1);
      } else {
        sd.setWebPath(f.igpkp.getDefinitionsName(r));
        // also mark this as a custom resource
        r.getResource().setUserData(UserDataNames.loader_custom_resource, "true");
      }
    }
  }

  private FetchedResource findLoadedStructure(StructureDefinition sd) {
    for (var f : f.fileList) {
      for (var r : f.getResources()) {
        if (r.fhirType().equals("StructureDefinition") && r.getId().equals(sd.getId())) {
          return r;
        }
      }
    }
    return null;
  }


  private boolean isBasicResource(FetchedResource r) {
    return "Basic".equals(r.fhirType())|| Utilities.existsInList(r.fhirType(), VersionUtilities.isR4BVer(f.context.getVersion()) ? SpecialTypeHandler.SPECIAL_TYPES_4B : SpecialTypeHandler.SPECIAL_TYPES_OTHER);
  }


  private List<LanguageFileProducer.TranslationUnit> findTranslations(String fhirType, String id, List<ValidationMessage> messages) throws IOException {
    List<LanguageFileProducer.TranslationUnit> res = null;

    String base = fhirType+"-"+id;
    String tbase = fhirType+"-$all";
    for (String dir : f.translationSources) {
      File df = new File(Utilities.path(f.rootDir, dir));
      if (df.exists()) {
        for (String fn : df.list()) {
          if ((fn.startsWith(base+".") || fn.startsWith(base+"-") || fn.startsWith(base+"_")) ||
                  (fn.startsWith(tbase+".") || fn.startsWith(tbase+"-") || fn.startsWith(tbase+"_"))) {
            LanguageFileProducer lp = null;
            String lang = findLang(fn, dir);
            switch (Utilities.getFileExtension(fn)) {
              case "po":
                if (lang == null) {
                  throw new Error("Unable to determine language from filename for "+Utilities.path(f.rootDir, dir, fn));
                }
                lp = new PoGetTextProducer(lang);
                break;
              case "xliff":
                lp = new XLIFFProducer();
                break;
              case "json":
                lp = new JsonLangFileProducer();
                break;
            }
            if (lp != null) {
              if (res == null) {
                res = new ArrayList<>();
              }
              File f = new File(Utilities.path(this.f.rootDir, dir, fn));
              this.f.usedLangFiles.add(f.getAbsolutePath());
              if (!Utilities.noString(FileUtilities.fileToString(f).trim())) {
                try {
                  FileInputStream s = new FileInputStream(f);
                  try {
                    res.addAll(lp.loadSource(s));
                  } finally {
                    s.close();
                  }
                } catch (Exception e) {
                  messages.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.EXCEPTION, fhirType, "Error loading "+f.getAbsolutePath()+": "+e.getMessage(), ValidationMessage.IssueSeverity.ERROR));
                }
              }
            }
          }
        }
      }
    }
    return res;
  }


  private String findLang(String fn, String dir) {
    Set<String> codes = new HashSet<>();
    for (String l : allLangs()) {
      codes.add(l);
    }
    for (Path part : Paths.get(dir)) {
      if (codes.contains(part.toString())) {
        return part.toString();
      }
    }
    for (String s : fn.split("\\-")) {
      if (codes.contains(s)) {
        return s;
      }
    }
    return null;
  }


  private RealmBusinessRules makeRealmBusinessRules() {
    if (f.expectedJurisdiction != null && f.expectedJurisdiction.getCode().equals("US")) {
      return new USRealmBusinessRules(f.context, f.version, f.tempDir, f.igpkp.getCanonical(), f.igpkp, f.rc);
    } else {
      return new NullRealmBusinessRules(f.igrealm);
    }
  }


  private PreviousVersionComparator makePreviousVersionComparator() throws IOException {
    if (isTemplate()) {
      return null;
    }
    if (f.comparisonVersions == null) {
      f.comparisonVersions = new ArrayList<>();
      f.comparisonVersions.add("{last}");
    }
    return new PreviousVersionComparator(f.context, f.version, f.businessVersion != null ? f.businessVersion : f.sourceIg == null ? null : f.sourceIg.getVersion(), f.rootDir, f.tempDir, f.igpkp.getCanonical(), f.igpkp, f.logger, f.comparisonVersions, f.versionToAnnotate, f.rc);
  }


  private IpaComparator makeIpaComparator() throws IOException {
    if (isTemplate()) {
      return null;
    }
    if (f.ipaComparisons == null) {
      return null;
    }
    return new IpaComparator(f.context, f.rootDir, f.tempDir, f.igpkp, f.logger, f.ipaComparisons, f.rc);
  }

  private IpsComparator makeIpsComparator() throws IOException {
    if (isTemplate()) {
      return null;
    }
    if (f.ipsComparisons == null) {
      return null;
    }
    return new IpsComparator(f.context, f.rootDir, f.tempDir, f.igpkp, f.logger, f.ipsComparisons, f.rc);
  }


  private void checkIgDeps(ImplementationGuide vig, String ver) {
    if ("r4b".equals(ver)) {
      ver = "r4";
    }
    String ov = VersionUtilities.getNameForVersion(f.context.getVersion()).toLowerCase();
    for (ImplementationGuide.ImplementationGuideDependsOnComponent dep : vig.getDependsOn()) {
      if (dep.getPackageId().endsWith("."+ov) ) {
        dep.setPackageId(dep.getPackageId().replace("."+ov, "."+ver));
      }
    }
  }
  private Resource parse(FetchedFile file) throws Exception {
    String parseVersion = f.version;
    if (!file.getResources().isEmpty()) {
      if (Utilities.existsInList(file.getResources().get(0).fhirType(), SpecialTypeHandler.specialTypes(f.context.getVersion()))) {
        parseVersion = SpecialTypeHandler.VERSION;
      } else {
        parseVersion = str(file.getResources().get(0).getConfig(), "version", f.version);
      }
    }
    return parseContent(file.getName(), file.getContentType(), parseVersion, file.getSource());
  }
  private Resource parseContent(String name, String contentType, String parseVersion, byte[] source) throws Exception {
    if (VersionUtilities.isR3Ver(parseVersion)) {
      org.hl7.fhir.dstu3.model.Resource res;
      if (contentType.contains("json")) {
        res = new org.hl7.fhir.dstu3.formats.JsonParser(true).parse(source);
      } else if (contentType.contains("xml")) {
        res = new org.hl7.fhir.dstu3.formats.XmlParser(true).parse(source);
      } else if (contentType.contains("fml")) {
        StructureMapUtilities mu = new StructureMapUtilities(f.context, null, null);
        return mu.parse(new String(source), "");
      } else {
        throw new Exception("Unable to determine file type for "+name);
      }
      return VersionConvertorFactory_30_50.convertResource(res);
    } else if (VersionUtilities.isR4Ver(parseVersion)) {
      org.hl7.fhir.r4.model.Resource res;
      if (contentType.contains("json")) {
        res = new org.hl7.fhir.r4.formats.JsonParser(true, true).parse(source);
      } else if (contentType.contains("xml")) {
        res = new org.hl7.fhir.r4.formats.XmlParser(true).parse(source);
      } else if (contentType.contains("fml")) {
        StructureMapUtilities mu = new StructureMapUtilities(f.context, null, null);
        return mu.parse(new String(source), "");
      } else {
        throw new Exception("Unable to determine file type for "+name);
      }
      return VersionConvertorFactory_40_50.convertResource(res);
    } else if (VersionUtilities.isR2BVer(parseVersion)) {
      org.hl7.fhir.dstu2016may.model.Resource res;
      if (contentType.contains("json")) {
        res = new org.hl7.fhir.dstu2016may.formats.JsonParser(true).parse(source);
      } else if (contentType.contains("xml")) {
        res = new org.hl7.fhir.dstu2016may.formats.XmlParser(true).parse(source);
      } else if (contentType.contains("fml")) {
        StructureMapUtilities mu = new StructureMapUtilities(f.context, null, null);
        return mu.parse(new String(source), "");
      } else {
        throw new Exception("Unable to determine file type for "+name);
      }
      return VersionConvertorFactory_14_50.convertResource(res);
    } else if (VersionUtilities.isR2Ver(parseVersion)) {
      org.hl7.fhir.dstu2.model.Resource res;
      if (contentType.contains("json")) {
        res = new org.hl7.fhir.dstu2.formats.JsonParser(true).parse(source);
      } else if (contentType.contains("xml")) {
        res = new org.hl7.fhir.dstu2.formats.XmlParser(true).parse(source);
      } else if (contentType.contains("fml")) {
        StructureMapUtilities mu = new StructureMapUtilities(f.context, null, null);
        return mu.parse(new String(source), "");
      } else {
        throw new Exception("Unable to determine file type for "+name);
      }

      BaseAdvisor_10_50 advisor = new IGR2ConvertorAdvisor5();
      return VersionConvertorFactory_10_50.convertResource(res, advisor);
    } else if (VersionUtilities.isR4BVer(parseVersion)) {
      org.hl7.fhir.r4b.model.Resource res;
      if (contentType.contains("json")) {
        res = new org.hl7.fhir.r4b.formats.JsonParser(true).parse(source);
      } else if (contentType.contains("xml")) {
        res = new org.hl7.fhir.r4b.formats.XmlParser(true).parse(source);
      } else if (contentType.contains("fml")) {
        StructureMapUtilities mu = new StructureMapUtilities(f.context, null, null);
        return mu.parse(new String(source), "");
      } else {
        throw new Exception("Unable to determine file type for "+name);
      }
      return VersionConvertorFactory_43_50.convertResource(res);
    } else if (VersionUtilities.isR5Plus(parseVersion)) {
      if (contentType.contains("json")) {
        return new JsonParser(true, true).parse(source);
      } else if (contentType.contains("xml")) {
        return new XmlParser(true).parse(source);
      } else if (contentType.contains("fml")) {
        StructureMapUtilities mu = new StructureMapUtilities(f.context, null, null);
        mu.setExceptionsForChecks(false);
        return mu.parse(new String(source), "");
      } else {
        throw new Exception("Unable to determine file type for "+name);
      }
    } else {
      throw new Exception("Unsupported version "+parseVersion);
    }
  }


  private void loadMappingSpaces(byte[] source) throws Exception {
    ByteArrayInputStream is = null;
    try {
      DocumentBuilderFactory factory = XMLUtil.newXXEProtectedDocumentBuilderFactory();
      factory.setNamespaceAware(true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      is = new ByteArrayInputStream(source);
      Document doc = builder.parse(is);
      org.w3c.dom.Element e = XMLUtil.getFirstChild(doc.getDocumentElement());
      while (e != null) {
        MappingSpace m = new MappingSpace(XMLUtil.getNamedChild(e, "columnName").getTextContent(), XMLUtil.getNamedChild(e, "title").getTextContent(),
                XMLUtil.getNamedChild(e, "id").getTextContent(), Integer.parseInt(XMLUtil.getNamedChild(e, "sort").getTextContent()), true, false, false, XMLUtil.getNamedChild(e, "link") != null ? XMLUtil.getNamedChild(e, "link").getTextContent(): XMLUtil.getNamedChild(e, "url").getTextContent());
        f.mappingSpaces.put(XMLUtil.getNamedChild(e, "url").getTextContent(), m);
        org.w3c.dom.Element p = XMLUtil.getNamedChild(e, "preamble");
        if (p != null) {
          m.setPreamble(new XhtmlParser().parseHtmlNode(p).setName("div"));
        }
        e = XMLUtil.getNextSibling(e);
      }
    } catch (Exception e) {
      throw new Exception("Error processing mappingSpaces.details: "+e.getMessage(), e);
    }
  }

  public ImplementationGuide.SPDXLicense licenseAsEnum() throws Exception {
    return ImplementationGuide.SPDXLicense.fromCode(license());
  }


  /**
   * this has to be called before load, and then load will reload the resource and override;
   * @throws IOException
   * @throws FHIRException
   * @throws FileNotFoundException
   *
   */
  private void loadCustomResources() throws FileNotFoundException, FHIRException, IOException {
    // scan existing load for custom resources
    for (StructureDefinition sd : f.context.fetchResourcesByType(StructureDefinition.class)) {
      if (sd.getKind() == StructureDefinition.StructureDefinitionKind.RESOURCE && sd.getDerivation() == StructureDefinition.TypeDerivationRule.SPECIALIZATION) {
        String scope = sd.getUrl().substring(0, sd.getUrl().lastIndexOf("/"));
        if (!"http://hl7.org/fhir/StructureDefinition".equals(scope)) {
          f.customResourceNames.add(sd.getTypeTail());
        }
      }
    }
    // look for new custom resources in this IG
    for (String s : f.customResourceFiles) {
      System.out.print("Load Custom Resource from "+s+":");
      System.out.println(loadCustomResource(s));
    }
  }


  /**
   * The point of this routine is to load the source file, and get the definition of the resource into the context
   * before anything else is loaded. The resource must be loaded normally for processing etc - we'll check that it has been later
   * @param filename
   * @throws IOException
   * @throws FHIRException
   * @throws FileNotFoundException
   */
  private String loadCustomResource(String filename) throws FileNotFoundException, FHIRException, IOException {
    // we load it as an R5 resource.
    StructureDefinition def = null;
    try {
      def = (StructureDefinition) org.hl7.fhir.r5.formats.FormatUtilities.loadFile(Utilities.uncheckedPath(FileUtilities.getDirectoryForFile(f.configFile), filename));
    } catch (Exception e) {
      return "Exception loading: "+e.getMessage();
    }

    if (f.approvedIgsForCustomResources == null) {
      try {
        f.approvedIgsForCustomResources = org.hl7.fhir.utilities.json.parser.JsonParser.parseObjectFromUrl("https://fhir.github.io/ig-registry/igs-approved-for-custom-resource.json");
      } catch (Exception e) {
        f.approvedIgsForCustomResources = new JsonObject();
        return "Exception checking IG status: "+e.getMessage();
      }
    }
    // checks
    // we'll validate it properly later. For now, we want to know:
    // 1. is this IG authorized to define custom resources?
    if (!f.approvedIgsForCustomResources.asBoolean(f.npmName)) {
      return "This IG is not authorised to define custom resources";
    }
    // 2. is this in the namespace of the IG (no flex there)
    if (!def.getUrl().startsWith(f.igpkp.getCanonical())) {
      return "The URL of this definition is not in the proper canonical URL space of the IG ("+ f.igpkp.getCanonical()+")";
    }
    // 3. is this based on Resource or DomainResource
    if (!Utilities.existsInList(def.getBaseDefinition(),
            "http://hl7.org/fhir/StructureDefinition/Resource",
            "http://hl7.org/fhir/StructureDefinition/DomainResource",
            "http://hl7.org/fhir/StructureDefinition/CanonicalResource",
            "http://hl7.org/fhir/StructureDefinition/MetadataResource")) {
      return "The definition must be based on Resource, DomainResource, CanonicalResource, or MetadataResource";
    }
//    // 4. is this active? (this is an easy way to turn this off if it stops the IG from building
//    if (def.getStatus() != PublicationStatus.ACTIVE) {
//      return "The definition is not active, so ignored";
//    }
    // 5. is this a specialization
    if (def.getDerivation() == StructureDefinition.TypeDerivationRule.CONSTRAINT) {
      return "This definition is not a specialization, so ignored";
    }

    if (def.getKind() == StructureDefinition.StructureDefinitionKind.LOGICAL) {
      def.setKind(StructureDefinition.StructureDefinitionKind.RESOURCE);
    }
    if (def.getKind() != StructureDefinition.StructureDefinitionKind.RESOURCE) {
      return "This definition does not describe a resource";
    }
    String ot = def.getType();
    if (def.getType().contains(":/")) {
      def.setType(tail(def.getType()));
    }
    // right, passed all the tests
    f.customResourceNames.add(def.getType());
    f.customResources.add(def);
    def.setUserData(UserDataNames.loader_custom_resource, "true");
    def.setWebPath("placeholder.html"); // we'll figure it out later
    f.context.cacheResource(def);

    // work around for a sushi limitation
    for (ImplementationGuide.ImplementationGuideDefinitionResourceComponent res : f.publishedIg.getDefinition().getResource()) {
      if (res.getReference().getReference().startsWith("Binary/")) {
        String id = res.getReference().getReference().substring(res.getReference().getReference().indexOf("/")+1);
        File of = new File(Utilities.path(FileUtilities.getDirectoryForFile(this.getConfigFile()), "fsh-generated", "resources", "Binary-"+id+".json"));
        File nf = new File(Utilities.path(FileUtilities.getDirectoryForFile(this.getConfigFile()), "fsh-generated", "resources", def.getType()+"-"+id+".json"));

        boolean read = false;
        boolean matches = res.getProfile().size() == 1 && (def.getUrl().equals(res.getProfile().get(0).primitiveValue()));
        if (!matches) {
          try {
            JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(of);
            String rt = json.asString("resourceType");
            read = true;
            matches = ot.equals(rt);
          } catch (Exception e) {
            // nothing here
          }
        }
        if (!matches && !read) {
          // try xml?
        }
        if (matches) {
          if (of.exists()) {
            of.renameTo(nf);
            JsonObject j = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(nf);
            j.set("resourceType", def.getType());
            org.hl7.fhir.utilities.json.parser.JsonParser.compose(j, nf, true);
          }
          res.getReference().setReference(def.getType()+res.getReference().getReference().substring(res.getReference().getReference().indexOf("/")));
        }
      }
    }

    return "loaded";
  }


  private void loadResources(FetchedFile igf) throws Exception { // igf is not currently used, but it was about relative references?
    List<FetchedFile> resources = f.fetcher.scan(f.sourceDir, f.context, f.igpkp.isAutoPath());
    for (FetchedFile ff : resources) {
      ff.start("loadResources");
      if (ff.getContentType().equals("adl")) {
        loadArchetype(ff, "scan folder "+FileUtilities.getDirectoryForFile(ff.getStatedPath()));
      } else {
        try {
          if (!ff.matches(igf) && !isBundle(ff)) {
            loadResource(ff, "scan folder "+FileUtilities.getDirectoryForFile(ff.getStatedPath()));
          }
        } finally {
          ff.finish("loadResources");
        }
      }
    }
  }

  private boolean loadArchetype(FetchedFile f, String cause) throws Exception {
    ArchetypeImporter.ProcessedArchetype pa = new ArchetypeImporter(this.f.context, this.f.igpkp.getCanonical()).importArchetype(f.getSource(), new File(f.getStatedPath()).getName());
    Bundle bnd = pa.getBnd();
    pa.getSd().setUserData(UserDataNames.archetypeSource, pa.getSource());
    pa.getSd().setUserData(UserDataNames.archetypeName, pa.getSourceName());

    f.setBundle(new FetchedResource(f.getName()+" (bundle)"));
    f.setBundleType(FetchedFile.FetchedBundleType.NATIVE);

    boolean changed = noteFile("Bundle/"+bnd.getIdBase(), f);
    int i = -1;
    for (Bundle.BundleEntryComponent be : bnd.getEntry()) {
      i++;
      Resource res = be.getResource();
      Element e = new ObjectConverter(this.f.context).convert(res);
      checkResourceUnique(res.fhirType()+"/"+res.getIdBase(), f.getName(), cause);
      FetchedResource r = f.addResource(f.getName()+"["+i+"]");
      r.setElement(e);
      r.setResource(res);
      r.setId(res.getIdBase());

      r.setTitle(r.getElement().getChildValue("name"));
      this.f.igpkp.findConfiguration(f, r);
    }
    for (FetchedResource r : f.getResources()) {
      this.f.bndIds.add(r.fhirType()+"/"+r.getId());
      ImplementationGuide.ImplementationGuideDefinitionResourceComponent res = findIGReference(r.fhirType(), r.getId());
      if (res == null) {
        res = this.f.publishedIg.getDefinition().addResource();
        if (!res.hasName())
          if (r.hasTitle())
            res.setName(r.getTitle());
          else
            res.setName(r.getId());
        if (!res.hasDescription() && r.getElement().hasChild("description")) {
          res.setDescription(r.getElement().getChildValue("description").trim());
        }
        res.setReference(new Reference().setReference(r.fhirType()+"/"+r.getId()));
      }
      res.setUserData(UserDataNames.pub_loaded_resource, r);
      r.setResEntry(res);
      if (r.getResource() instanceof CanonicalResource) {
        CanonicalResource cr = (CanonicalResource)r.getResource();
        if (!this.f.canonicalResources.containsKey(cr.getUrl())) {
          this.f.canonicalResources.put(cr.getUrl(), r);
          if (cr.hasVersion())
            this.f.canonicalResources.put(cr.getUrl()+"#"+cr.getVersion(), r);
        }
      }
    }
    return changed;
  }

  public void checkResourceUnique(String tid, String source, String cause) throws Error {
    if (f.logLoading) {
      System.out.println("id: "+tid+", file: "+source+", from "+cause);
    }
    if (f.loadedIds.containsKey(tid)) {
      System.out.println("Duplicate Resource in IG: "+tid+". first found in "+ f.loadedIds.get(tid)+", now in "+source+" ("+cause+")");
      f.duplicateInputResourcesDetected = true;
    }
    f.loadedIds.put(tid, source+" ("+cause+")");
  }


  private void loadSpreadsheets(FetchedFile igf) throws Exception {
    Set<String> knownValueSetIds = new HashSet<>();
    for (String s : f.spreadsheets) {
      loadSpreadsheet(s, igf, knownValueSetIds, "listed as a spreadsheet");
    }
  }

  private boolean loadSpreadsheet(String name, FetchedFile igf, Set<String> knownValueSetIds, String cause) throws Exception {
    if (name.startsWith("!"))
      return false;

    FetchedFile f = this.f.fetcher.fetchResourceFile(name);
    boolean changed = noteFile("Spreadsheet/"+name, f);
    if (changed) {
      f.getValuesetsToLoad().clear();
      logDebugMessage(LogCategory.INIT, "load "+f.getPath());
      Bundle bnd = new IgSpreadsheetParser(this.f.context, this.f.execTime, this.f.igpkp.getCanonical(), f.getValuesetsToLoad(), this.f.mappingSpaces, knownValueSetIds).parse(f);
      f.setBundle(new FetchedResource(f.getName()+" (ex spreadsheet)"));
      f.setBundleType(FetchedFile.FetchedBundleType.SPREADSHEET);
      f.getBundle().setResource(bnd);
      for (Bundle.BundleEntryComponent b : bnd.getEntry()) {
        checkResourceUnique(b.getResource().fhirType()+"/"+b.getResource().getIdBase(), name, cause);
        FetchedResource r = f.addResource(f.getName());
        r.setResource(b.getResource());
        r.setId(b.getResource().getId());
        r.setElement(convertToElement(r, r.getResource()));
        r.setTitle(r.getElement().getChildValue("name"));
        this.f.igpkp.findConfiguration(f, r);
      }
    } else {
      f = this.f.altMap.get("Spreadsheet/"+name);
    }

    for (String id : f.getValuesetsToLoad().keySet()) {
      if (!knownValueSetIds.contains(id)) {
        String vr = f.getValuesetsToLoad().get(id);
        checkResourceUnique("ValueSet/"+id, name, cause);

        FetchedFile fv = this.f.fetcher.fetchFlexible(vr);
        boolean vrchanged = noteFile("sp-ValueSet/"+vr, fv);
        if (vrchanged) {
          loadAsElementModel(fv, fv.addResource(f.getName()+" (VS)"), null, false, cause);
          checkImplicitResourceIdentity(id, fv);
        }
        knownValueSetIds.add(id);
        // ok, now look for an implicit code system with the same name
        boolean crchanged = false;
        String cr = vr.replace("valueset-", "codesystem-");
        if (!cr.equals(vr)) {
          if (this.f.fetcher.canFetchFlexible(cr)) {
            fv = this.f.fetcher.fetchFlexible(cr);
            crchanged = noteFile("sp-CodeSystem/"+vr, fv);
            if (crchanged) {
              loadAsElementModel(fv, fv.addResource(f.getName()+" (CS)"), null, false, cause);
              checkImplicitResourceIdentity(id, fv);
            }
          }
        }
        changed = changed || vrchanged || crchanged;
      }
    }
    ImplementationGuide.ImplementationGuideDefinitionGroupingComponent pck = null;
    for (FetchedResource r : f.getResources()) {
      this.f.bndIds.add(r.fhirType()+"/"+r.getId());
      ImplementationGuide.ImplementationGuideDefinitionResourceComponent res = findIGReference(r.fhirType(), r.getId());
      if (res == null) {
        if (pck == null) {
          pck = this.f.publishedIg.getDefinition().addGrouping().setName(f.getTitle());
          pck.setId(name);
        }
        res = this.f.publishedIg.getDefinition().addResource();
        res.setGroupingId(pck.getId());
        if (!res.hasName())
          res.setName(r.getTitle());
        if (!res.hasDescription() && ((CanonicalResource)r.getResource()).hasDescription()) {
          res.setDescription(((CanonicalResource)r.getResource()).getDescription().trim());
        }
        res.setReference(new Reference().setReference(r.fhirType()+"/"+r.getId()));
      }
      res.setUserData(UserDataNames.pub_loaded_resource, r);
      r.setResEntry(res);
    }
    return changed;
  }


  private void checkImplicitResourceIdentity(String id, FetchedFile fv) throws Exception {
    // check the resource ids:
    String rid = fv.getResources().get(0).getId();
    String rurl = fv.getResources().get(0).getElement().getChildValue("url");
    if (Utilities.noString(rurl))
      throw new Exception("ValueSet has no canonical URL "+fv.getName());
    if (!id.equals(rid))
      throw new Exception("ValueSet has wrong id ("+rid+", expecting "+id+") in "+fv.getName());
    if (!tail(rurl).equals(rid))
      throw new Exception("resource id/url mismatch: "+id+" vs "+rurl+" for "+fv.getResources().get(0).getTitle()+" in "+fv.getName());
    if (!rurl.startsWith(f.igpkp.getCanonical()))
      throw new Exception("base/ resource url mismatch: "+ f.igpkp.getCanonical()+" vs "+rurl);
  }

  private void loadMappings(FetchedFile igf) throws Exception {
    for (String s : f.mappings) {
      loadMapping(s, igf);
    }
  }

  private boolean loadMapping(String name, FetchedFile igf) throws Exception {
    if (name.startsWith("!"))
      return false;
    FetchedFile f = this.f.fetcher.fetchResourceFile(name);
    boolean changed = noteFile("Mapping/"+name, f);
    if (changed) {
      logDebugMessage(LogCategory.INIT, "load "+f.getPath());
      MappingSheetParser p = new MappingSheetParser();
      p.parse(new ByteArrayInputStream(f.getSource()), f.getRelativePath());
      ConceptMap cm = p.getConceptMap();
      FetchedResource r = f.addResource(f.getName()+" (mapping)");
      r.setResource(cm);
      r.setId(cm.getId());
      r.setElement(convertToElement(r, cm));
      r.setTitle(r.getElement().getChildValue("name"));
      this.f.igpkp.findConfiguration(f, r);
    } else {
      f = this.f.altMap.get("Mapping/"+name);
    }
    return changed;
  }


  private void loadBundles(FetchedFile igf) throws Exception {
    for (String be : f.bundles) {
      loadBundle(be, igf, "listed as a bundle");
    }
  }

  private boolean loadBundle(String name, FetchedFile igf, String cause) throws Exception {
    FetchedFile f = this.f.fetcher.fetch(new Reference().setReference("Bundle/"+name), igf);
    boolean changed = noteFile("Bundle/"+name, f);
    if (changed) {
      f.setBundle(new FetchedResource(f.getName()+" (bundle)"));
      f.setBundleType(FetchedFile.FetchedBundleType.NATIVE);
      loadAsElementModel(f, f.getBundle(), null, true, cause);
      List<Element> entries = new ArrayList<Element>();
      f.getBundle().getElement().getNamedChildren("entry", entries);
      int i = -1;
      for (Element bnde : entries) {
        i++;
        Element res = bnde.getNamedChild("resource");
        if (res == null) {
          f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.EXCEPTION, "Bundle.element["+i+"]", "All entries must have resources when loading a bundle", ValidationMessage.IssueSeverity.ERROR));
        } else {
          checkResourceUnique(res.fhirType()+"/"+res.getIdBase(), name, cause);
          FetchedResource r = f.addResource(f.getName()+"["+i+"]");
          r.setElement(res);
          r.setId(res.getIdBase());
          List<Element> profiles = new ArrayList<Element>();
          Element meta = res.getNamedChild("meta");
          if (meta != null)
            meta.getNamedChildren("profile", profiles);
          for (Element p : profiles)
            r.getStatedProfiles().add(p.primitiveValue());
          r.setTitle(r.getElement().getChildValue("name"));
          this.f.igpkp.findConfiguration(f, r);
        }
      }
    } else
      f = this.f.altMap.get("Bundle/"+name);
    for (FetchedResource r : f.getResources()) {
      this.f.bndIds.add(r.fhirType()+"/"+r.getId());
      ImplementationGuide.ImplementationGuideDefinitionResourceComponent res = findIGReference(r.fhirType(), r.getId());
      if (res == null) {
        res = this.f.publishedIg.getDefinition().addResource();
        if (!res.hasName())
          if (r.hasTitle())
            res.setName(r.getTitle());
          else
            res.setName(r.getId());
        if (!res.hasDescription() && r.getElement().hasChild("description")) {
          res.setDescription(r.getElement().getChildValue("description").trim());
        }
        res.setReference(new Reference().setReference(r.fhirType()+"/"+r.getId()));
      }
      res.setUserData(UserDataNames.pub_loaded_resource, r);
      r.setResEntry(res);
      if (r.getResource() instanceof CanonicalResource) {
        CanonicalResource cr = (CanonicalResource)r.getResource();
        if (!this.f.canonicalResources.containsKey(cr.getUrl())) {
          this.f.canonicalResources.put(cr.getUrl(), r);
          if (cr.hasVersion())
            this.f.canonicalResources.put(cr.getUrl()+"#"+cr.getVersion(), r);
        }
      }
    }
    return changed;
  }

  private void loadTranslationSupplements(FetchedFile igf) throws Exception {
    for (String p : f.translationSources) {
      File dir = new File(Utilities.path(f.rootDir, p));
      FileUtilities.createDirectory(dir.getAbsolutePath());
      for (File f : dir.listFiles()) {
        if (!this.f.usedLangFiles.contains(f.getAbsolutePath())) {
          loadTranslationSupplement(f);
        }
      }
    }
  }

  private void loadTranslationSupplement(File f) throws Exception {
    if (f.isDirectory()) {
      return;
    }
    String name = f.getName();
    if (!name.contains("-")) {
      if (!name.equals(".DS_Store")) {
        System.out.println("Ignoring file "+f.getAbsolutePath()+" - name is not {type}-{id}.xxx");
      }
    } else {
      String rtype = name.substring(0, name.indexOf("-"));
      String id = name.substring(name.indexOf("-")+1);
      String ext = name.substring(name.lastIndexOf(".")+1).toLowerCase();
      id = id.substring(0, id.lastIndexOf("."));
      if (!Utilities.isValidId(id)) {
        System.out.println("Ignoring file "+f.getAbsolutePath()+" - name is not {type}-{id}.xxx");
      } else if (!Utilities.existsInList(rtype, LanguageUtils.TRANSLATION_SUPPLEMENT_RESOURCE_TYPES)) {
        System.out.println("Ignoring file "+f.getAbsolutePath()+" - resource type '"+rtype+"' is not supported for translation supplements");
      } else if (Utilities.existsInList(rtype, "po", "xliff", "json")) {
        System.out.println("Ignoring file "+f.getAbsolutePath()+" - unknown format '"+ext+"'. Allowed = po, xliff, json");
      } else {
        CanonicalResource cr = (CanonicalResource) this.f.context.fetchResourceById(rtype, id);
        if (cr == null) {
          System.out.println("Ignoring file "+f.getAbsolutePath()+" - the resource "+rtype+"/"+id+" is not known");
        } else {
          FetchedFile ff = new FetchedFile(f.getAbsolutePath().substring(this.f.rootDir.length()+1));
          ff.setPath(f.getCanonicalPath());
          ff.setName(SimpleFetcher.fileTitle(f.getCanonicalPath()));
          ff.setTime(f.lastModified());
          ff.setFolder(false);
          ff.setContentType(ext);
          //          InputStream ss = new FileInputStream(f);
          //          byte[] b = new byte[ss.available()];
          //          ss.read(b, 0, ss.available());
          //          ff.setSource(b);
          //          ss.close();

          boolean changed = noteFile(f.getPath(), ff);
          // ok good to go
          CodeSystem csSrc = makeSupplement(cr, true); // what could be translated
          CodeSystem csDst = makeSupplement(cr, false); // what has been translated
          csDst.setUserData(UserDataNames.pub_source_filename, f.getName().substring(0, f.getName().indexOf(".")));
          List<LanguageFileProducer.TranslationUnit> list = loadTranslations(f, ext);
          this.f.langUtils.fillSupplement(csSrc, csDst, list);
          FetchedResource rr = ff.addResource("CodeSystemSupplement");
          rr.setElement(convertToElement(rr, csDst));
          rr.setResource(csDst);
          rr.setId(csDst.getId());
          rr.setTitle(csDst.getName());
          this.f.igpkp.findConfiguration(ff, rr);
          for (FetchedResource r : ff.getResources()) {
            ImplementationGuide.ImplementationGuideDefinitionResourceComponent res = findIGReference(r.fhirType(), r.getId());
            if (res == null) {
              res = this.f.publishedIg.getDefinition().addResource();
              if (!res.hasName())
                res.setName(r.getTitle());
              if (!res.hasDescription() && csDst.hasDescription()) {
                res.setDescription(csDst.getDescription().trim());
              }
              res.setReference(new Reference().setReference(r.fhirType()+"/"+r.getId()));
            }
            res.setUserData(UserDataNames.pub_loaded_resource, r);
            r.setResEntry(res);
          }
          return;
        }
      }
    }
  }

  private CodeSystem makeSupplement(CanonicalResource res, boolean content) {
    String id = "cs-"+ f.defaultTranslationLang +"-"+res.getId();
    CodeSystem supplement = new CodeSystem();
    supplement.setLanguage(content ? "en" : f.defaultTranslationLang); // base is EN?
    supplement.setId(id);
    supplement.setUrl(Utilities.pathURL(f.igpkp.getCanonical(), "CodeSystem", id));
    supplement.setVersion(res.getVersion());
    supplement.setStatus(res.getStatus());
    supplement.setContent(Enumerations.CodeSystemContentMode.SUPPLEMENT);
    supplement.setSupplements(res.getUrl());
    supplement.setCaseSensitive(false);
    supplement.setPublisher(f.sourceIg.getPublisher());
    supplement.setContact(f.sourceIg.getContact());
    supplement.setCopyright(f.sourceIg.getCopyright());

    supplement.setName(res.getName());
    supplement.setTitle(res.getTitle());
    supplement.setPublisher(res.getPublisher());
    supplement.setPurpose(res.getPurpose());
    supplement.setDescription(res.getDescription());
    supplement.setCopyright(res.getCopyright());

    if (content) {
      if (res instanceof CodeSystem) {
        CodeSystem cs = (CodeSystem) res;
        for (CodeSystem.ConceptDefinitionComponent cd : cs.getConcept()) {
          cloneConcept(supplement.getConcept(), cd);
        }
      } else if (res instanceof StructureDefinition) {
        StructureDefinition sd = (StructureDefinition) res;
        for (ElementDefinition ed : sd.getSnapshot().getElement()) {
          addConcept(supplement, ed.getId(), ed.getDefinition());
          addConcept(supplement, ed.getId()+"@requirements", ed.getRequirements(), ed.getDefinitionElement());
          addConcept(supplement, ed.getId()+"@comment", ed.getComment(), ed.getDefinitionElement());
          addConcept(supplement, ed.getId()+"@meaningWhenMissing", ed.getMeaningWhenMissing(), ed.getDefinitionElement());
          addConcept(supplement, ed.getId()+"@orderMeaning", ed.getOrderMeaning(), ed.getDefinitionElement());
          addConcept(supplement, ed.getId()+"@isModifierMeaning", ed.getIsModifierReason(), ed.getDefinitionElement());
          addConcept(supplement, ed.getId()+"@binding", ed.getBinding().getDescription(), ed.getDefinitionElement());
        }
      } else if (res instanceof Questionnaire) {
        Questionnaire q = (Questionnaire) res;
        for (Questionnaire.QuestionnaireItemComponent item : q.getItem()) {
          addItem(supplement, item, null);
        }
      }
    }
    return supplement;
  }

  private void cloneConcept(List<CodeSystem.ConceptDefinitionComponent> dest, CodeSystem.ConceptDefinitionComponent source) {
    // we clone everything translatable but the child concepts (need to flatten the hierarchy if there is one so we
    // can filter it later

    CodeSystem.ConceptDefinitionComponent clone = new CodeSystem.ConceptDefinitionComponent();
    clone.setCode(source.getCode());
    dest.add(clone);
    clone.setDisplay(source.getDisplay());
    clone.setDefinition(source.getDefinition());
    for (CodeSystem.ConceptDefinitionDesignationComponent d : source.getDesignation()) {
      if (wantToTranslate(d)) {
        clone.addDesignation(d.copy());
      }
    }
    for (Extension ext : source.getExtension()) {
      if (ext.hasValue() && Utilities.existsInList(ext.getValue().fhirType(), "string", "markdown")) {
        clone.addExtension(ext.copy());
      }
    }

    for (CodeSystem.ConceptDefinitionComponent cd : source.getConcept()) {
      cloneConcept(dest, cd);
    }
  }


  private boolean wantToTranslate(CodeSystem.ConceptDefinitionDesignationComponent d) {
    return !d.hasLanguage() && d.hasUse(); // todo: only if the source language is the right language?
  }

  private void addItem(CodeSystem supplement, Questionnaire.QuestionnaireItemComponent item, Questionnaire.QuestionnaireItemComponent parent) {
    addConcept(supplement, item.getLinkId(), item.getText(), parent == null ? null : parent.getTextElement());
    addConcept(supplement, item.getLinkId()+"@prefix", item.getPrefix(), item.getTextElement());
    for (Questionnaire.QuestionnaireItemAnswerOptionComponent ao : item.getAnswerOption()) {
      if (ao.hasValueCoding()) {
        if (ao.getValueCoding().hasDisplay()) {
          addConcept(supplement, item.getLinkId()+"@option="+ao.getValueCoding().getCode(), ao.getValueCoding().getDisplay(), item.getTextElement());
        }
      } else if (ao.hasValueStringType()) {
        addConcept(supplement, item.getLinkId()+"@option", ao.getValueStringType().primitiveValue(), item.getTextElement());
      } else if (ao.hasValueReference()) {
        if (ao.getValueReference().hasDisplay()) {
          addConcept(supplement, item.getLinkId()+"@option="+ao.getValueReference().getReference(), ao.getValueReference().getDisplay(), item.getText()+": "+ao.getValueReference().getReference());
        }
      }
    }
    for (Questionnaire.QuestionnaireItemInitialComponent ao : item.getInitial()) {
      if (ao.hasValueCoding()) {
        if (ao.getValueCoding().hasDisplay()) {
          addConcept(supplement, item.getLinkId()+"@initial="+ao.getValueCoding().getCode(), ao.getValueCoding().getDisplay(), item.getTextElement());
        }
      } else if (ao.hasValueStringType()) {
        addConcept(supplement, item.getLinkId()+"@initial", ao.getValueStringType().primitiveValue(), item.getText());
      } else if (ao.hasValueQuantity()) {
        addConcept(supplement, item.getLinkId()+"@initial", ao.getValueQuantity().getDisplay(), item.getText()+": "+ao.getValueQuantity().toString());
      } else if (ao.hasValueReference()) {
        if (ao.getValueReference().hasDisplay()) {
          addConcept(supplement, item.getLinkId()+"@initial="+ao.getValueReference().getReference(), ao.getValueReference().getDisplay(), item.getText()+": "+ao.getValueReference().getReference());
        }
      }
    }
    for (Questionnaire.QuestionnaireItemComponent child : item.getItem()) {
      addItem(supplement, child, item);
    }
  }

  private void copyConcepts(CodeSystem.ConceptDefinitionComponent tgt, CodeSystem.ConceptDefinitionComponent src, CodeSystem supplement) {
    for (CodeSystem.ConceptDefinitionComponent cd : src.getConcept()) {
      CodeSystem.ConceptDefinitionComponent clone = tgt.addConcept().setCode(cd.getCode()).setDisplay(cd.getDisplay());
      // don't create this - it's just admin overhead
      // CodeSystemUtilities.setProperty(supplement, clone, "translation-context", cd.getDefinitionElement());
      copyConcepts(clone, cd, supplement);
    }
  }

  private void addConcept(CodeSystem supplement, String code, String display, DataType context) {
    if (display != null) {
      CodeSystem.ConceptDefinitionComponent cs = supplement.addConcept().setCode(code).setDisplay(display.replace("\r", "\\r").replace("\n", "\\n"));
      if (context != null) {
        // don't create this - it's just admin overhead
        //  CodeSystemUtilities.setProperty(supplement, cs, "translation-context", context);
      }
    }
  }

  private void addConcept(CodeSystem supplement, String code, String display) {
    if (display != null) {
      CodeSystem.ConceptDefinitionComponent cs = supplement.addConcept().setCode(code).setDisplay(display.replace("\r", "\\r").replace("\n", "\\n"));
    }
  }

  private void addConcept(CodeSystem supplement, String code, String display, String context) {
    if (display != null) {
      CodeSystem.ConceptDefinitionComponent cs = supplement.addConcept().setCode(code).setDisplay(display.replace("\r", "\\r").replace("\n", "\\n"));
      if (context != null) {
        // don't create this - it's just admin overhead
        // CodeSystemUtilities.setProperty(supplement, cs, "translation-context", new StringType(context));
      }
    }
  }

  private List<LanguageFileProducer.TranslationUnit> loadTranslations(File f, String ext) throws FileNotFoundException, IOException, ParserConfigurationException, SAXException {
    try {
      switch (ext) {
        case "po": return new PoGetTextProducer().loadSource(new FileInputStream(f));
        case "xliff": return new XLIFFProducer().loadSource(new FileInputStream(f));
        case "json": return new JsonLangFileProducer().loadSource(new FileInputStream(f));
      }
    } catch (Exception e) {
      throw new FHIRException("Error parsing "+f.getAbsolutePath()+": "+e.getMessage(), e);
    }
    throw new IOException("Unknown extension "+ext); // though we won't get to here
  }


  private void loadPrePages() throws Exception {
    if (f.prePagesDirs.isEmpty())
      return;

    for (String prePagesDir : f.prePagesDirs) {
      FetchedFile dir = f.fetcher.fetch(prePagesDir);
      if (dir != null) {
        dir.setRelativePath("");
        if (!dir.isFolder())
          throw new Exception("pre-processed page reference is not a folder");
        loadPrePages(dir, dir.getStatedPath());
      }
    }
  }

  private void loadPrePages(FetchedFile dir, String basePath) throws Exception {
    System.out.println("loadPrePages from " + dir+ " as "+basePath);

    PublisherFields.PreProcessInfo ppinfo = f.preProcessInfo.get(basePath);
    if (ppinfo==null) {
      throw new Exception("Unable to find preProcessInfo for basePath: " + basePath);
    }
    if (!f.altMap.containsKey("pre-page/"+dir.getPath())) {
      f.altMap.put("pre-page/"+dir.getPath(), dir);
      dir.setProcessMode(ppinfo.hasXslt() ? FetchedFile.PROCESS_XSLT : FetchedFile.PROCESS_NONE);
      dir.setXslt(ppinfo.getXslt());
      if (ppinfo.hasRelativePath()) {
        if (dir.getRelativePath().isEmpty())
          dir.setRelativePath(ppinfo.getRelativePath());
        else
          dir.setRelativePath(ppinfo.getRelativePath() + File.separator + dir.getRelativePath());

      }
      addFile(dir);
    }
    for (String link : dir.getFiles()) {
      FetchedFile f = this.f.fetcher.fetch(link);
      if (basePath.startsWith("/var") && f.getPath().startsWith("/private/var")) {
        f.setPath(f.getPath().substring(8));
      }
      f.setRelativePath(f.getPath().substring(basePath.length()+1));
      if (f.isFolder())
        loadPrePages(f, basePath);
      else
        loadPrePage(f, ppinfo);
    }
  }

  private boolean loadPages() throws Exception {
    boolean changed = false;
    for (String pagesDir: f.pagesDirs) {
      FetchedFile dir = f.fetcher.fetch(pagesDir);
      dir.setRelativePath("");
      if (!dir.isFolder())
        throw new Exception("page reference is not a folder");
      if (loadPages(dir, dir.getPath()))
        changed = true;
    }
    return changed;
  }

  private boolean loadPages(FetchedFile dir, String basePath) throws Exception {
    boolean changed = false;
    if (!f.altMap.containsKey("page/"+dir.getPath())) {
      changed = true;
      f.altMap.put("page/"+dir.getPath(), dir);
      dir.setProcessMode(FetchedFile.PROCESS_NONE);
      addFile(dir);
    }
    for (String link : dir.getFiles()) {
      FetchedFile f = this.f.fetcher.fetch(link);
      f.setRelativePath(f.getPath().substring(basePath.length()+1));
      if (f.isFolder())
        changed = loadPages(f, basePath) || changed;
      else
        changed = loadPage(f) || changed;
    }
    return changed;
  }

  private boolean loadPage(FetchedFile file) {
    FetchedFile existing = f.altMap.get("page/"+file.getPath());
    if (existing == null || existing.getTime() != file.getTime() || existing.getHash() != file.getHash()) {
      file.setProcessMode(FetchedFile.PROCESS_NONE);
      addFile(file);
      f.altMap.put("page/"+file.getPath(), file);
      return true;
    } else {
      return false;
    }
  }

  private void loadResources2(FetchedFile igf) throws Exception {
    if (!f.resourceFactoryDirs.isEmpty()) {
      f.fetcher.setResourceDirs(f.resourceFactoryDirs);
      List<FetchedFile> resources = f.fetcher.scan(null, f.context, true);
      for (FetchedFile ff : resources) {
        ff.start("loadResources");
        try {
          if (!ff.matches(igf) && !isBundle(ff)) {
            loadResource(ff, "scan folder "+FileUtilities.getDirectoryForFile(ff.getStatedPath()));
          }
        } finally {
          ff.finish("loadResources");
        }
      }
    }
  }

  private boolean isBundle(FetchedFile ff) {
    File f = new File(ff.getName());
    String n = f.getName();
    if (n.endsWith(".json") || n.endsWith(".xml")) {
      n = n.substring(0, n.lastIndexOf("."));
    }
    for (String s : this.f.bundles) {
      if (n.equals("bundle-"+s) || n.equals("Bundle-"+s) ) {
        return true;
      }
    }
    return false;
  }

  private boolean loadResource(FetchedFile f, String cause) throws Exception {
    logDebugMessage(LogCategory.INIT, "load "+f.getPath());
    boolean changed = noteFile(f.getPath(), f);
    if (changed) {
      loadAsElementModel(f, f.addResource(f.getName()), null, false, cause);
    }
    for (FetchedResource r : f.getResources()) {
      ImplementationGuide.ImplementationGuideDefinitionResourceComponent res = findIGReference(r.fhirType(), r.getId());
      if (res == null) {
        res = this.f.publishedIg.getDefinition().addResource();
        if (!res.hasName()) {
          res.setName(r.getTitle());
        }
        if (!res.hasDescription()) {
          res.setDescription(((CanonicalResource) r.getResource()).getDescription().trim());
        }
        res.setReference(new Reference().setReference(r.fhirType()+"/"+r.getId()));
      }
      res.setUserData(UserDataNames.pub_loaded_resource, r);
      r.setResEntry(res);
    }
    return changed;
  }


  private void loadAsElementModel(FetchedFile file, FetchedResource r, ImplementationGuide.ImplementationGuideDefinitionResourceComponent srcForLoad, boolean suppressLoading, String cause) throws Exception {
    file.getErrors().clear();
    Element e = null;

    try {
      if (file.getContentType().contains("json")) {
        e = loadFromJson(file);
      } else if (file.getContentType().contains("xml")) {
        e = loadFromXml(file);
      } else if (file.getContentType().contains("fml")) {
        e = loadFromMap(file);
      } else {
        throw new Exception("Unable to determine file type for "+file.getName());
      }
    } catch (Exception ex) {
      throw new Exception("Unable to parse "+file.getName()+": " +ex.getMessage(), ex);
    }
    if (e == null)
      throw new Exception("Unable to parse "+file.getName()+": " +file.getErrors().get(0).summary());

    if (e != null) {
      try {
        String id;
        boolean altered = false;
        boolean binary = false;
        if (!f.context.getResourceNamesAsSet().contains(e.fhirType())) {
          if (ExtensionUtilities.readBoolExtension(e.getProperty().getStructure(), ExtensionDefinitions.EXT_LOAD_AS_RESOURCE)) {
            String type = e.getProperty().getStructure().getTypeName();
            id = e.getIdBase();
            if (id == null) {
              id = Utilities.makeId(e.getStatedResourceId());
            }
            if (id == null) {
              id = new File(file.getPath()).getName();
              id = Utilities.makeId(id.substring(0, id.lastIndexOf(".")));
            }
            checkResourceUnique(type+"/"+id, file.getPath(), cause);
            r.setElement(e).setId(id).setType(type);
            f.igpkp.findConfiguration(file, r);
            binary = false;
          } else {
            id = new File(file.getPath()).getName();
            id = Utilities.makeId(id.substring(0, id.lastIndexOf(".")));
            // are we going to treat it as binary, or something else?
            checkResourceUnique("Binary/"+id, file.getPath(), cause);
            r.setElement(e).setId(id).setType("Binary");
            f.igpkp.findConfiguration(file, r);
            binary = true;
          }
        } else {
          id = e.getChildValue("id");

          if (Utilities.noString(id)) {
            if (e.hasChild("url")) {
              String url = e.getChildValue("url");
              String prefix = Utilities.pathURL(f.igpkp.getCanonical(), e.fhirType())+"/";
              if (url.startsWith(prefix)) {
                id = e.getChildValue("url").substring(prefix.length());
                e.setChildValue("id", id);
                altered = true;
              }
              prefix = Utilities.pathURL(f.altCanonical, e.fhirType())+"/";
              if (url.startsWith(prefix)) {
                id = e.getChildValue("url").substring(prefix.length());
                e.setChildValue("id", id);
                altered = true;
              }
              if (Utilities.noString(id)) {
                if (f.simplifierMode) {
                  id = file.getName();
                  System.out.println("Resource has no id in "+file.getPath()+" and canonical URL ("+url+") does not start with the IG canonical URL ("+prefix+")");
                } else {
                  throw new Exception("Resource has no id in "+file.getPath()+" and canonical URL ("+url+") does not start with the IG canonical URL ("+prefix+")");
                }
              }
            } else {
              id = fileNameTail(file.getName());
            }
            e.setChildValue("id", id);
            altered = true;
          }
          if (!Utilities.noString(e.getIdBase())) {
            checkResourceUnique(e.fhirType()+"/"+e.getIdBase(), file.getPath(), cause);
          }
          r.setId(id);
          r.setElement(e);
          f.igpkp.findConfiguration(file, r);
        }
        if (!suppressLoading) {
          if (srcForLoad == null)
            srcForLoad = findIGReference(r.fhirType(), r.getId());
          if (srcForLoad == null && !"ImplementationGuide".equals(r.fhirType())) {
            srcForLoad = f.publishedIg.getDefinition().addResource();
            srcForLoad.getReference().setReference(r.fhirType()+"/"+r.getId());
          }
        }

        String ver = ExtensionUtilities.readStringExtension(srcForLoad, ExtensionDefinitions.EXT_IGP_LOADVERSION);
        if (ver == null)
          ver = r.getConfig() == null ? null : ostr(r.getConfig(), "version");
        if (ver == null)
          ver = f.version; // fall back to global version

        // version check: for some conformance resources, they may be saved in a different version from that stated for the IG.
        // so we might need to convert them prior to loading. Note that this is different to the conversion below - we need to
        // convert to the current version. Here, we need to convert to the stated version. Note that we need to do this after
        // the first load above because above, we didn't have enough data to get the configuration, but we do now.
        if (!ver.equals(f.version)) {
          if (file.getContentType().contains("json"))
            e = loadFromJsonWithVersionChange(file, ver, f.version);
          else if (file.getContentType().contains("xml"))
            e = loadFromXmlWithVersionChange(file, ver, f.version);
          else
            throw new Exception("Unable to determine file type for "+file.getName());
          r.setElement(e);
        }
        if (srcForLoad != null) {
          srcForLoad.setUserData(UserDataNames.pub_loaded_resource, r);
          r.setResEntry(srcForLoad);
          if (srcForLoad.hasProfile()) {
            r.getElement().setUserData(UserDataNames.map_profile, srcForLoad.getProfile().get(0).getValue());
            r.getStatedProfiles().add(stripVersion(srcForLoad.getProfile().get(0).getValue()));
          } else {
            String profile = f.factoryProfileMap.get(file.getName());
            if (profile != null) {
              r.getStatedProfiles().add(stripVersion(profile));
            }
          }
        }

        r.setTitle(e.getChildValue("name"));
        Element m = e.getNamedChild("meta");
        if (m != null) {
          List<Element> profiles = m.getChildrenByName("profile");
          for (Element p : profiles)
            r.getStatedProfiles().add(stripVersion(p.getValue()));
        }
        if ("1.0.1".equals(ver)) {
          file.getErrors().clear();
          org.hl7.fhir.dstu2.model.Resource res2 = null;
          if (file.getContentType().contains("json"))
            res2 = new org.hl7.fhir.dstu2.formats.JsonParser().parse(file.getSource());
          else if (file.getContentType().contains("xml"))
            res2 = new org.hl7.fhir.dstu2.formats.XmlParser().parse(file.getSource());
          org.hl7.fhir.r5.model.Resource res = VersionConvertorFactory_10_50.convertResource(res2);
          e = convertToElement(r, res);
          r.setElement(e).setId(id).setTitle(e.getChildValue("name"));
          r.setResource(res);
        }
        if (new AdjunctFileLoader(f.binaryPaths, f.cql).replaceAttachments1(file, r, metadataResourceNames())) {
          altered = true;
        }
        if (isNewML()) {
          if (e.canHaveChild("language") && !e.hasChild("language")) {
            e.setChildValue("language", f.defaultTranslationLang);
          }
          List<LanguageFileProducer.TranslationUnit> translations = findTranslations(r.fhirType(), r.getId(), r.getErrors());
          if (translations != null) {
            r.setHasTranslations(true);
            if (f.langUtils.importFromTranslations(e, translations, r.getErrors()) > 0) {
              altered = true;
            }
          }
        }
        if (!binary && !f.customResourceNames.contains(r.fhirType()) && ((altered && r.getResource() != null) || (ver.equals(Constants.VERSION) && r.getResource() == null && f.context.getResourceNamesAsSet().contains(r.fhirType())))) {
          r.setResource(new ObjectConverter(f.context).convert(r.getElement()));
          if (!r.getResource().hasId() && r.getId() != null) {
            r.getResource().setId(r.getId());
          }
        }
        if ((altered && r.getResource() == null)) {
          if (file.getContentType().contains("json")) {
            saveToJson(file, e);
          } else if (file.getContentType().contains("xml")) {
            saveToXml(file, e);
          }
        }
      } catch ( Exception ex ) {
        throw new Exception("Unable to determine type for  "+file.getName()+": " +ex.getMessage(), ex);
      }
    }
  }

  private Element loadFromMap(FetchedFile file) throws Exception {
    if (!VersionUtilities.isR4Plus(f.context.getVersion())) {
      throw new Error("Loading Map Files is not supported for version "+VersionUtilities.getNameForVersion(f.context.getVersion()));
    }
    FmlParser fp = new FmlParser(f.context, f.validator.getFHIRPathEngine());
    fp.setupValidation(ParserBase.ValidationPolicy.EVERYTHING);
    Element res = fp.parse(file.getErrors(), FileUtilities.bytesToString(file.getSource()));
    if (res == null) {
      throw new Exception("Unable to parse Map Source for "+file.getName());
    }
    return res;
  }

  private Element loadFromXml(FetchedFile file) throws Exception {
    org.hl7.fhir.r5.elementmodel.XmlParser xp = new org.hl7.fhir.r5.elementmodel.XmlParser(f.context);
    xp.setAllowXsiLocation(true);
    xp.setupValidation(ParserBase.ValidationPolicy.EVERYTHING);
    Element res = xp.parseSingle(new ByteArrayInputStream(file.getSource()), file.getErrors());
    if (res == null) {
      throw new Exception("Unable to parse XML for "+file.getName());
    }
    return res;
  }

  private Element loadFromJson(FetchedFile file) throws Exception {
    org.hl7.fhir.r5.elementmodel.JsonParser jp = new org.hl7.fhir.r5.elementmodel.JsonParser(f.context);
    jp.setupValidation(ParserBase.ValidationPolicy.EVERYTHING);
    jp.setAllowComments(true);
    jp.setLogicalModelResolver(f.fetcher);
    return jp.parseSingle(new ByteArrayInputStream(file.getSource()), file.getErrors());
  }

  private void saveToXml(FetchedFile file, Element e) throws Exception {
    org.hl7.fhir.r5.elementmodel.XmlParser xp = new org.hl7.fhir.r5.elementmodel.XmlParser(f.context);
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    xp.compose(e, bs, IParser.OutputStyle.PRETTY, null);
    file.setSource(bs.toByteArray());
  }

  private void saveToJson(FetchedFile file, Element e) throws Exception {
    org.hl7.fhir.r5.elementmodel.JsonParser jp = new org.hl7.fhir.r5.elementmodel.JsonParser(f.context);
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    jp.compose(e, bs, IParser.OutputStyle.PRETTY, null);
    file.setSource(bs.toByteArray());
  }

  private Element loadFromXmlWithVersionChange(FetchedFile file, String srcV, String dstV) throws Exception {
    InputStream src = new ByteArrayInputStream(file.getSource());
    ByteArrayOutputStream dst = new ByteArrayOutputStream();
    if (VersionUtilities.isR3Ver(srcV) && VersionUtilities.isR2BVer(dstV)) {
      org.hl7.fhir.dstu3.model.Resource r3 = new org.hl7.fhir.dstu3.formats.XmlParser().parse(src);
      org.hl7.fhir.dstu2016may.model.Resource r14 = VersionConvertorFactory_14_30.convertResource(r3);
      new org.hl7.fhir.dstu2016may.formats.XmlParser().compose(dst, r14);
    } else if (VersionUtilities.isR3Ver(srcV) && Constants.VERSION.equals(dstV)) {
      org.hl7.fhir.dstu3.model.Resource r3 = new org.hl7.fhir.dstu3.formats.XmlParser().parse(src);
      org.hl7.fhir.r5.model.Resource r5 = VersionConvertorFactory_30_50.convertResource(r3);
      new org.hl7.fhir.r5.formats.XmlParser().compose(dst, r5);
    } else if (VersionUtilities.isR4Ver(srcV) && Constants.VERSION.equals(dstV)) {
      org.hl7.fhir.r4.model.Resource r4 = new org.hl7.fhir.r4.formats.XmlParser().parse(src);
      org.hl7.fhir.r5.model.Resource r5 = VersionConvertorFactory_40_50.convertResource(r4);
      new org.hl7.fhir.r5.formats.XmlParser().compose(dst, r5);
    } else {
      throw new Exception("Conversion from "+srcV+" to "+dstV+" is not supported yet"); // because the only know reason to do this is 3.0.1 --> 1.40
    }
    org.hl7.fhir.r5.elementmodel.XmlParser xp = new org.hl7.fhir.r5.elementmodel.XmlParser(f.context);
    xp.setAllowXsiLocation(true);
    xp.setupValidation(ParserBase.ValidationPolicy.EVERYTHING);
    file.getErrors().clear();
    Element res = xp.parseSingle(new ByteArrayInputStream(dst.toByteArray()), file.getErrors());
    if (res == null) {
      throw new Exception("Unable to parse XML for "+file.getName());
    }
    return res;
  }

  private Element loadFromJsonWithVersionChange(FetchedFile file, String srcV, String dstV) throws Exception {
    throw new Exception("Version converting JSON resources is not supported yet"); // because the only know reason to do this is Forge, and it only works with XML
  }


  private boolean noteFile(ImplementationGuide.ImplementationGuideDefinitionResourceComponent key, FetchedFile file) {
    FetchedFile existing = f.fileMap.get(key);
    if (existing == null || existing.getTime() != file.getTime() || existing.getHash() != file.getHash()) {
      f.fileList.add(file);
      f.fileMap.put(key, file);
      addFile(file);
      return true;
    } else {
      for (FetchedFile f : f.fileList) {
        if (file.getPath().equals(f.getPath())) {
          throw new Error("Attempt to process the same source resource twice: "+file.getPath());
        }
      }
      f.fileList.add(existing); // this one is already parsed
      return false;
    }
  }


  private void loadAsBinaryResource(FetchedFile file, FetchedResource r, ImplementationGuide.ImplementationGuideDefinitionResourceComponent srcForLoad, String format, String cause) throws Exception {
    file.getErrors().clear();
    Binary bin = new Binary();
    String id = srcForLoad.getReference().getReference();
    if (id.startsWith("Binary/")) {
      bin.setId(id.substring(7));
    } else {
      throw new Exception("Unable to determine Resource id from reference: "+id);
    }
    bin.setContent(file.getSource());
    bin.setContentType(format);
    Element e = new ObjectConverter(f.context).convert(bin);
    checkResourceUnique(e.fhirType()+"/"+e.getIdBase(), file.getPath(), cause);
    r.setElement(e).setId(bin.getId());
    r.setResource(bin);
    r.setResEntry(srcForLoad);
    srcForLoad.setUserData(UserDataNames.pub_loaded_resource, r);
    r.setResEntry(srcForLoad);
    if (srcForLoad.hasProfile()) {
      r.getElement().setUserData(UserDataNames.pub_logical, srcForLoad.getProfile().get(0).getValue());
      r.setExampleUri(srcForLoad.getProfile().get(0).getValue());
    }
    f.igpkp.findConfiguration(file, r);
    srcForLoad.setUserData(UserDataNames.pub_loaded_resource, r);
  }

  private String stripVersion(String url) {
    return url.endsWith("|"+ f.businessVersion) ? url.substring(0, url.lastIndexOf("|")) : url;
  }


  private void loadConformance1(boolean first) throws Exception {
    boolean any = false;
    for (FetchedFile f : f.fileList) {
      if (!f.isLoaded()) {
        any = true;
      }
    }
    if (any) {
      log("Process "+(first ? "": "Additional ")+"Loaded Resources");
      for (String s : metadataResourceNames()) {
        load(s, !Utilities.existsInList(s, "Evidence", "EvidenceVariable")); // things that have changed in R6 that aren't internally critical
      }
      log("Generating Snapshots");
      generateSnapshots();
      for (FetchedFile f : f.fileList) {
        f.setLoaded(true);
      }
    }
  }


  private void load(String type, boolean isMandatory) throws Exception {
    for (FetchedFile f : f.fileList) {
      if (!f.isLoaded()) {
        f.start("load");
        try {
          for (FetchedResource r : f.getResources()) {
            loadResourceContent(type, isMandatory, f, r);
          }
        } finally {
          f.finish("load");
        }
      }
    }
  }

  public void loadResourceContent(String type, boolean isMandatory, FetchedFile f, FetchedResource r) throws Exception {
    if (r.fhirType().equals(type)) {
      logDebugMessage(LogCategory.PROGRESS, "process res: "+r.fhirType()+"/"+r.getId());
      if (r.getResource() == null) {
        try {
          if (f.getBundleType() == FetchedFile.FetchedBundleType.NATIVE) {
            r.setResource(parseInternal(f, r));
          } else {
            r.setResource(parse(f));
          }
          r.getResource().setUserData(UserDataNames.pub_element, r.getElement());
        } catch (Exception e) {
          if (isMandatory) {
            throw new FHIRException("Error parsing "+f.getName()+": "+e.getMessage(), e);

          } else {
            System.out.println("Error parsing "+f.getName()+": "+e.getMessage()); // , e);
          }
        }
      }
      if (r.getResource() instanceof CanonicalResource) {
        CanonicalResource bc = (CanonicalResource) r.getResource();
        if (bc == null) {
          throw new Exception("Error: conformance resource "+f.getPath()+" could not be loaded");
        }
        boolean altered = false;
        if (bc.hasUrl()) {
          if (this.f.adHocTmpDir == null && !this.f.listedURLExemptions.contains(bc.getUrl()) && !isExampleResource(bc) && !canonicalUrlIsOk(bc)) {
            if (!bc.fhirType().equals("CapabilityStatement") || !bc.getUrl().contains("/Conformance/")) {
              f.getErrors().add(new ValidationMessage(ValidationMessage.Source.ProfileValidator, ValidationMessage.IssueType.INVALID, bc.fhirType()+".where(url = '"+bc.getUrl()+"')", "Conformance resource "+f.getPath()+" - the canonical URL ("+Utilities.pathURL(this.f.igpkp.getCanonical(), bc.fhirType(),
                      bc.getId())+") does not match the URL ("+bc.getUrl()+")", ValidationMessage.IssueSeverity.ERROR).setMessageId(PublisherMessageIds.RESOURCE_CANONICAL_MISMATCH));
              // throw new Exception("Error: conformance resource "+f.getPath()+" canonical URL ("+Utilities.pathURL(igpkp.getCanonical(), bc.fhirType(), bc.getId())+") does not match the URL ("+bc.getUrl()+")");
            }
          }
        } else if (bc.hasId()) {
          bc.setUrl(Utilities.pathURL(this.f.igpkp.getCanonical(), bc.fhirType(), bc.getId()));
        } else {
          throw new Exception("Error: conformance resource "+f.getPath()+" has neither id nor url");
        }
        if (replaceLiquidTags(bc)) {
          altered = true;
        }
        if (bc.fhirType().equals("CodeSystem")) {
          this.f.context.clearTSCache(bc.getUrl());
        }
        CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
        if (this.f.businessVersion != null) {
          altered = true;
          b.append("version="+ this.f.businessVersion);
          bc.setVersion(this.f.businessVersion);
        } else if (this.f.defaultBusinessVersion != null && !bc.hasVersion()) {
          altered = true;
          b.append("version="+ this.f.defaultBusinessVersion);
          bc.setVersion(this.f.defaultBusinessVersion);
        }
        if (!(bc instanceof StructureDefinition)) {
          // can't do structure definitions yet, because snapshots aren't generated, and not all are registered.
          // do it later when generating snapshots
          altered = checkCanonicalsForVersions(f, bc, false) || altered;
        }
        if (!r.isExample()) {
          if (this.f.wgm != null) {
            if (!bc.hasExtension(ExtensionDefinitions.EXT_WORKGROUP)) {
              altered = true;
              b.append("wg="+ this.f.wgm);
              bc.addExtension(ExtensionDefinitions.EXT_WORKGROUP, new CodeType(this.f.wgm));
            } else if (!this.f.wgm.equals(ExtensionUtilities.readStringExtension(bc, ExtensionDefinitions.EXT_WORKGROUP))) {
              altered = true;
              b.append("wg="+ this.f.wgm);
              bc.getExtensionByUrl(ExtensionDefinitions.EXT_WORKGROUP).setValue(new CodeType(this.f.wgm));
            }
          } else if (this.f.defaultWgm != null && !bc.hasExtension(ExtensionDefinitions.EXT_WORKGROUP)) {
            altered = true;
            b.append("wg="+ this.f.defaultWgm);
            bc.addExtension(ExtensionDefinitions.EXT_WORKGROUP, new CodeType(this.f.defaultWgm));
          }
        }

        if (this.f.contacts != null && !this.f.contacts.isEmpty()) {
          altered = true;
          b.append("contact");
          bc.getContact().clear();
          bc.getContact().addAll(this.f.contacts);
        } else if (!bc.hasContact() && this.f.defaultContacts != null && !this.f.defaultContacts.isEmpty()) {
          altered = true;
          b.append("contact");
          bc.getContact().addAll(this.f.defaultContacts);
        }
        if (this.f.contexts != null && !this.f.contexts.isEmpty()) {
          altered = true;
          b.append("useContext");
          bc.getUseContext().clear();
          bc.getUseContext().addAll(this.f.contexts);
        } else if (!bc.hasUseContext() && this.f.defaultContexts != null && !this.f.defaultContexts.isEmpty()) {
          altered = true;
          b.append("useContext");
          bc.getUseContext().addAll(this.f.defaultContexts);
        }
        // Todo: Enable these
        if (this.f.copyright != null && !bc.hasCopyright() && bc.supportsCopyright()) {
          altered = true;
          b.append("copyright="+ this.f.copyright);
          bc.setCopyrightElement(this.f.copyright);
        } else if (!bc.hasCopyright() && this.f.defaultCopyright != null) {
          altered = true;
          b.append("copyright="+ this.f.defaultCopyright);
          bc.setCopyrightElement(this.f.defaultCopyright);
        }
        if (bc.hasCopyright() && bc.getCopyright().contains("{{{year}}}")) {
          bc.setCopyright(bc.getCopyright().replace("{{{year}}}", Integer.toString(Calendar.getInstance().get(Calendar.YEAR))));
          altered = true;
          b.append("copyright="+bc.getCopyright());
        }
        if (this.f.jurisdictions != null && !this.f.jurisdictions.isEmpty()) {
          altered = true;
          b.append("jurisdiction");
          bc.getJurisdiction().clear();
          bc.getJurisdiction().addAll(this.f.jurisdictions);
        } else if (!bc.hasJurisdiction() && this.f.defaultJurisdictions != null && !this.f.defaultJurisdictions.isEmpty()) {
          altered = true;
          b.append("jurisdiction");
          bc.getJurisdiction().addAll(this.f.defaultJurisdictions);
        }
        if (this.f.publisher != null) {
          altered = true;
          b.append("publisher="+ this.f.publisher);
          bc.setPublisherElement(this.f.publisher);
        } else if (!bc.hasPublisher() && this.f.defaultPublisher != null) {
          altered = true;
          b.append("publisher="+ this.f.defaultPublisher);
          bc.setPublisherElement(this.f.defaultPublisher);
        }


        if (!bc.hasDate()) {
          altered = true;
          b.append("date");
          bc.setDateElement(new DateTimeType(this.f.execTime));
        }
        if (!bc.hasStatus()) {
          altered = true;
          b.append("status=draft");
          bc.setStatus(Enumerations.PublicationStatus.DRAFT);
        }
        if (new AdjunctFileLoader(this.f.binaryPaths, this.f.cql).replaceAttachments2(f, r)) {
          altered = true;
        }
        if (this.f.oidRoot != null && !hasOid(bc.getIdentifier())) {
          String oid = getOid(r.fhirType(), bc.getIdBase());
          bc.getIdentifier().add(new Identifier().setSystem("urn:ietf:rfc:3986").setValue("urn:oid:"+oid));
          altered = true;
        }
        if (altered) {
          if ((this.f.langPolicy == ValidationPresenter.LanguagePopulationPolicy.ALL || this.f.langPolicy == ValidationPresenter.LanguagePopulationPolicy.OTHERS)) {
            if (!this.f.sourceIg.hasLanguage()) {
              if (r.getElement().hasChild("language")) {
                bc.setLanguage(null);
              }
            } else {
              bc.setLanguage(this.f.sourceIg.getLanguage());
            }
          }

          if (Utilities.existsInList(r.fhirType(), "GraphDefinition")) {
            f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.PROCESSING, bc.fhirType()+".where(url = '"+bc.getUrl()+"')",
                    "The resource needed to modified during loading to apply common headers "+b.toString()+" but this isn't possible for the type "+r.fhirType()+" because version conversion isn't working completely",
                    ValidationMessage.IssueSeverity.WARNING).setMessageId(PublisherMessageIds.RESOURCE_CONVERSION_NOT_POSSIBLE));
          } else {
            r.setElement(convertToElement(r, bc));
          }
        }
        this.f.igpkp.checkForPath(f, r, bc, false);
        try {
          this.f.context.cacheResourceFromPackage(bc, this.f.packageInfo);
        } catch (Exception e) {
          throw new Exception("Exception loading "+bc.getUrl()+": "+e.getMessage(), e);
        }
      }
    } else if (r.fhirType().equals("Bundle")) {
      Bundle b = (Bundle) r.getResource();
      if (b == null) {
        try {
          b = (Bundle) convertFromElement(r.getElement());
          r.setResource(b);
        } catch (Exception e) {
          logDebugMessage(LogCategory.PROGRESS, "Ignoring conformance resources in Bundle "+f.getName()+" because :"+e.getMessage());
        }
      }
      if (b != null) {
        for (Bundle.BundleEntryComponent be : b.getEntry()) {
          if (be.hasResource() && be.getResource().fhirType().equals(type)) {
            CanonicalResource mr = (CanonicalResource) be.getResource();
            if (mr.hasUrl()) {
              if (!mr.hasWebPath()) {
                this.f.igpkp.checkForPath(f,  r,  mr, true);
              }
              this.f.context.cacheResourceFromPackage(mr, this.f.packageInfo);
            } else
              logDebugMessage(LogCategory.PROGRESS, "Ignoring resource "+type+"/"+mr.getId()+" in Bundle "+f.getName()+" because it has no canonical URL");

          }
        }
      }
    }
  }

  private Resource parseInternal(FetchedFile file, FetchedResource res) throws Exception {
    String parseVersion = f.version;
    if (!file.getResources().isEmpty()) {
      parseVersion = str(file.getResources().get(0).getConfig(), "version", f.version);
    }
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    new org.hl7.fhir.r5.elementmodel.XmlParser(f.context).compose(res.getElement(), bs, IParser.OutputStyle.NORMAL, null);
    return parseContent("Entry "+res.getId()+" in "+file.getName(), "xml", parseVersion, bs.toByteArray());
  }


  private void processFactories(List<String> factories) throws IOException {
    LiquidEngine liquid = new LiquidEngine(f.context, f.validator.getExternalHostServices());
    for (String f : factories) {
      String rootFolder = FileUtilities.getDirectoryForFile(this.f.configFile);
      File path = new File(Utilities.path(rootFolder, f));
      if (!path.exists()) {
        throw new FHIRException("factory source '"+f+"' not found");
      }
      File log = new File(Utilities.path(FileUtilities.getDirectoryForFile(path.getAbsolutePath()), "log"));
      if (!log.exists()) {
        FileUtilities.createDirectory(log.getAbsolutePath());
      }

      JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(path);
      for (JsonObject fact : json.forceArray("factories").asJsonObjects()) {
        TestDataFactory tdf = new TestDataFactory(this.f.context, fact, liquid, this.f.validator.getFHIRPathEngine(), this.f.igpkp.getCanonical(), rootFolder, log.getAbsolutePath(), this.f.factoryProfileMap, this.f.context.getLocale());
        log("Execute Test Data Factory '"+tdf.getName()+"'. Log in "+tdf.statedLog());
        tdf.execute();
      }
    }
  }

  private boolean canonicalUrlIsOk(CanonicalResource bc) {
    if (bc.getUrl().equals(Utilities.pathURL(f.igpkp.getCanonical(), bc.fhirType(), bc.getId()))) {
      return true;
    }
    if (f.altCanonical != null) {
      if (bc.getUrl().equals(Utilities.pathURL(f.altCanonical, bc.fhirType(), bc.getId()))) {
        return true;
      }
      if (f.altCanonical.equals("http://hl7.org/fhir") && "CodeSystem".equals(bc.fhirType()) && bc.getUrl().equals(Utilities.pathURL(f.altCanonical, bc.getId()))) {
        return true;
      }
    }
    return false;
  }


  private boolean replaceLiquidTags(DomainResource resource) {
    if (!resource.hasText() || !resource.getText().hasDiv()) {
      return false;
    }
    Map<String, String> vars = new HashMap<>();
    vars.put("{{site.data.fhir.path}}", f.igpkp.specPath()+"/");
    return new LiquidEngine(f.context, f.validator.getExternalHostServices()).replaceInHtml(resource.getText().getDiv(), vars);
  }


  private String getOid(String type, String id) {
    String ot = oidNodeForType(type);
    String oid = f.oidIni.getStringProperty(type, id);
    if (oid != null) {
      return oid;
    }
    Integer keyR = f.oidIni.getIntegerProperty("Key", type);
    int key = keyR == null ? 0 : keyR.intValue();
    key++;
    oid = f.oidRoot +"."+ot+"."+key;
    f.oidIni.setIntegerProperty("Key", type, key, null);
    f.oidIni.setStringProperty(type, id, oid, null);
    f.oidIni.save();
    return oid;
  }

  private String oidNodeForType(String type) {
    switch (type) {
      case "ActivityDefinition" : return "11";
      case "ActorDefinition" : return "12";
      case "CapabilityStatement" : return "13";
      case "ChargeItemDefinition" : return "14";
      case "Citation" : return "15";
      case "CodeSystem" : return "16";
      case "CompartmentDefinition" : return "17";
      case "ConceptMap" : return "18";
      case "ConditionDefinition" : return "19";
      case "EffectEvidenceSynthesis" : return "20";
      case "EventDefinition" : return "21";
      case "Evidence" : return "22";
      case "EvidenceReport" : return "23";
      case "EvidenceVariable" : return "24";
      case "ExampleScenario" : return "25";
      case "GraphDefinition" : return "26";
      case "ImplementationGuide" : return "27";
      case "Library" : return "28";
      case "Measure" : return "29";
      case "MessageDefinition" : return "30";
      case "NamingSystem" : return "31";
      case "ObservationDefinition" : return "32";
      case "OperationDefinition" : return "33";
      case "PlanDefinition" : return "34";
      case "Questionnaire" : return "35";
      case "Requirements" : return "36";
      case "ResearchDefinition" : return "37";
      case "ResearchElementDefinition" : return "38";
      case "RiskEvidenceSynthesis" : return "39";
      case "SearchParameter" : return "40";
      case "SpecimenDefinition" : return "41";
      case "StructureDefinition" : return "42";
      case "StructureMap" : return "43";
      case "SubscriptionTopic" : return "44";
      case "TerminologyCapabilities" : return "45";
      case "TestPlan" : return "46";
      case "TestScript" : return "47";
      case "ValueSet" : return "48";
      default: return "10";
    }
  }


}
