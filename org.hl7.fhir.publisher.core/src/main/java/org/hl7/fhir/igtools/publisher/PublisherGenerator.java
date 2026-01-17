package org.hl7.fhir.igtools.publisher;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_10_50;
import org.hl7.fhir.convertors.factory.*;
import org.hl7.fhir.convertors.misc.NpmPackageVersionConverter;
import org.hl7.fhir.convertors.misc.ProfileVersionAdaptor;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.renderers.*;
import org.hl7.fhir.igtools.renderers.CodeSystemRenderer;
import org.hl7.fhir.igtools.renderers.ExampleScenarioRenderer;
import org.hl7.fhir.igtools.renderers.OperationDefinitionRenderer;
import org.hl7.fhir.igtools.renderers.QuestionnaireRenderer;
import org.hl7.fhir.igtools.renderers.QuestionnaireResponseRenderer;
import org.hl7.fhir.igtools.renderers.StructureDefinitionRenderer;
import org.hl7.fhir.igtools.renderers.StructureMapRenderer;
import org.hl7.fhir.igtools.renderers.ValueSetRenderer;
import org.hl7.fhir.igtools.spreadsheets.ObservationSummarySpreadsheetGenerator;
import org.hl7.fhir.igtools.web.IGReleaseVersionUpdater;
import org.hl7.fhir.r5.conformance.ConstraintJavaGenerator;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.context.ContextUtilities;
import org.hl7.fhir.r5.context.ExpansionOptions;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.elementmodel.ObjectConverter;
import org.hl7.fhir.r5.elementmodel.ParserBase;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r5.formats.IParser;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.RdfParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.liquid.BaseJsonWrapper;
import org.hl7.fhir.r5.liquid.LiquidEngine;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.openapi.OpenApiGenerator;
import org.hl7.fhir.r5.openapi.Writer;
import org.hl7.fhir.r5.renderers.*;
import org.hl7.fhir.r5.renderers.spreadsheets.CodeSystemSpreadsheetGenerator;
import org.hl7.fhir.r5.renderers.spreadsheets.ConceptMapSpreadsheetGenerator;
import org.hl7.fhir.r5.renderers.spreadsheets.StructureDefinitionSpreadsheetGenerator;
import org.hl7.fhir.r5.renderers.spreadsheets.ValueSetSpreadsheetGenerator;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.Resolver;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.terminologies.TerminologyUtilities;
import org.hl7.fhir.r5.terminologies.ValueSetUtilities;
import org.hl7.fhir.r5.terminologies.expansion.ValueSetExpansionOutcome;
import org.hl7.fhir.r5.terminologies.utilities.ValidationResult;
import org.hl7.fhir.r5.utils.*;
import org.hl7.fhir.r5.utils.formats.CSVWriter;
import org.hl7.fhir.r5.utils.sql.Runner;
import org.hl7.fhir.r5.utils.sql.StorageJson;
import org.hl7.fhir.r5.utils.sql.StorageSqlite3;
import org.hl7.fhir.utilities.*;
import org.hl7.fhir.utilities.i18n.LanguageTag;
import org.hl7.fhir.utilities.i18n.RenderingI18nContext;
import org.hl7.fhir.utilities.i18n.subtag.LanguageSubtagRegistry;
import org.hl7.fhir.utilities.i18n.subtag.LanguageSubtagRegistryLoader;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.model.JsonString;
import org.hl7.fhir.utilities.npm.ToolsVersion;
import org.hl7.fhir.utilities.settings.FhirSettings;
import org.hl7.fhir.utilities.turtle.Turtle;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationOptions;
import org.hl7.fhir.utilities.xhtml.*;
import org.hl7.fhir.utilities.xml.XMLUtil;
import org.hl7.fhir.utilities.xml.XmlEscaper;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hl7.fhir.igtools.publisher.Publisher.*;

/**
 * this class is part of the Publisher Core cluster, and handles all the routines that generate content (other than QA). See @Publisher for discussion
 */

public class PublisherGenerator extends PublisherBase {

  public class Item {
    public Item(FetchedFile f, FetchedResource r, String sort) {
      this.f= f;
      this.r = r;
      this.sort = sort;
    }
    public Item() {
      // TODO Auto-generated constructor stub
    }
    private String sort;
    private FetchedFile f;
    private FetchedResource r;
  }
  public class ItemSorter implements Comparator<Item> {

    @Override
    public int compare(Item a0, Item a1) {
      return a0.sort.compareTo(a1.sort);
    }
  }


  public class ListItemEntry {

    private String id;
    private String link;
    private String name;
    private String desc;
    private String type;
    private String title;
    private Element element;

    public ListItemEntry(String type, String id, String link, String name, String title, String desc, Element element) {
      super();
      this.type = type;
      this.id = id;
      this.link = link;
      this.name = name;
      this.title = title;
      this.desc = desc;
      this.element = element;
    }

    public String getId() {
      return id;
    }

    public String getLink() {
      return link;
    }

    public String getName() {
      return name;
    }

    public String getTitle() {
      return title;
    }

    public String getDesc() {
      return desc;
    }

    public String getType() {
      return type;
    }

  }

  public class ListViewSorterById implements Comparator<ListItemEntry> {
    @Override
    public int compare(ListItemEntry arg0, ListItemEntry arg1) {
      return arg0.getId().compareTo(arg1.getId());
    }
  }

  public class ListViewSorterByName implements Comparator<ListItemEntry> {
    @Override
    public int compare(ListItemEntry arg0, ListItemEntry arg1) {
      return arg0.getName().toLowerCase().compareTo(arg1.getName().toLowerCase());
    }
  }

  private class ImplementationGuideDefinitionPageComponentComparator implements Comparator<ImplementationGuide.ImplementationGuideDefinitionPageComponent> {
    @Override
    public int compare(ImplementationGuide.ImplementationGuideDefinitionPageComponent x, ImplementationGuide.ImplementationGuideDefinitionPageComponent y) {
      try {
        return x.getName().compareTo(y.getName());
      } catch (FHIRException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
        return 0;
      }
    }
  }


  public PublisherGenerator(PublisherSettings settings) {
    super(settings);
  }

  public void generate() throws Exception {
    if (settings.isSimplifierMode()) {
      return;
    }

    for (String s : pf.context.getBinaryKeysAsSet()) {
      if (needFile(s)) {
        if (pf.makeQA) {
          checkMakeFile(pf.context.getBinaryForKey(s), Utilities.path(pf.qaDir, s), pf.otherFilesStartup);
        }
        checkMakeFile(pf.context.getBinaryForKey(s), Utilities.path(pf.tempDir, s), pf.otherFilesStartup);
        for (String l : allLangs()) {
          checkMakeFile(fillLangTemplate(pf.context.getBinaryForKey(s), l), Utilities.path(pf.tempDir, l, s), pf.otherFilesStartup);
        }
      }
    }

    Base.setCopyUserData(true); // just keep all the user data when copying while rendering
    pf.bdr = new BaseRenderer(pf.context, checkAppendSlash(pf.specPath), pf.igpkp, pf.specMaps, pageTargets(), pf.markdownEngine, pf.packge, pf.rc);

    forceDir(pf.tempDir);
    forceDir(Utilities.path(pf.tempDir, "_includes"));
    forceDir(Utilities.path(pf.tempDir, "_data"));
    if (pf.hasTranslations) {
      forceDir(pf.tempLangDir);
    }
    pf.rc.setNoHeader(true);
    pf.rcLangs.setNoHeader(true);

    pf.otherFilesRun.add(Utilities.path(pf.outputDir, "package.tgz"));
    pf.otherFilesRun.add(Utilities.path(pf.outputDir, "package.manifest.json"));
    pf.otherFilesRun.add(Utilities.path(pf.tempDir, "package.db"));
    DBBuilder db = pf.generatingDatabase ? new DBBuilder(Utilities.path(pf.tempDir, "package.db"), pf.context, pf.rc, pf.cu, pf.fileList) : null;
    copyData();
    for (String rg : pf.regenList) {
      regenerate(rg);
    }

    updateImplementationGuide();
    generateDataFile(db);

    logMessage("Generate Native Outputs");

    for (FetchedFile f : pf.changeList) {
      f.start("generate1");
      try {
        generateNativeOutputs(f, false, db);
      } finally {
        f.finish("generate1");
      }
    }
    if (db != null) {
      db.finishResources();
    }

    generateViewDefinitions(db);
    templateBeforeGenerate();
    if (pf.saveExpansionParams) {
      new JsonParser().setOutputStyle(IParser.OutputStyle.NORMAL).compose(new FileOutputStream(Utilities.path(pf.tempDir, "parameters-expansion-parameters.json")), pf.context.getExpansionParameters());
      new XmlParser().setOutputStyle(IParser.OutputStyle.NORMAL).compose(new FileOutputStream(Utilities.path(pf.tempDir, "parameters-expansion-parameters.xml")), pf.context.getExpansionParameters());
    }

    logMessage("Generate HTML Outputs");
    for (FetchedFile f : pf.changeList) {
      f.start("generate2");
      try {
        generateHtmlOutputs(f, false, db, null);
      } finally {
        f.finish("generate2");
      }
    }

    logMessage("Generate Spreadsheets");
    for (FetchedFile f : pf.changeList) {
      f.start("generate2");
      try {
        generateSpreadsheets(f, false, db);
      } finally {
        f.finish("generate2");
      }
    }
    if (pf.allProfilesCsv != null) {
      pf.allProfilesCsv.dump();
    }
    if (pf.allProfilesXlsx != null) {
      pf.allProfilesXlsx.configure();
      String path = Utilities.path(pf.tempDir, "all-profiles.xlsx");
      pf.allProfilesXlsx.finish(new FileOutputStream(path));
      pf.otherFilesRun.add(Utilities.path(pf.tempDir, "all-profiles.xlsx"));
      pf.allProfilesXlsx.dump();
    }
    logMessage("Generate Summaries");


    for (String s : pf.profileTestCases) {
      logMessage("Running Profile Tests Cases in "+s);
      new ProfileTestCaseExecutor(pf.rootDir, pf.context, pf.validator, pf.fileList).execute(s);
    }
    if (!pf.changeList.isEmpty()) {
      if (isNewML()) {
        for (String l : allLangs()) {
          generateSummaryOutputs(db, l, pf.rcLangs.get(l));
        }
      } else {
        generateSummaryOutputs(db, null, pf.rc);
      }
    }
    pf.template.rapidoSummary();
    genBasePages();
    if (db != null) {
      db.closeUp();
    }
    FileUtilities.bytesToFile(pf.extensionTracker.generate(), Utilities.path(pf.tempDir, "usage-stats.json"));
    try {
      log("Sending Usage Stats to Server");
      pf.extensionTracker.sendToServer("http://test.fhir.org/usage-stats");
    } catch (Exception e) {
      log("Submitting Usage Stats failed: "+e.getMessage());
    }

    pf.realmRules.addOtherFiles(pf.otherFilesRun, pf.outputDir);
    pf.previousVersionComparator.addOtherFiles(pf.otherFilesRun, pf.outputDir, pf.getExemptHtmlPatterns());
    if (pf.ipaComparator != null) {
      pf.ipaComparator.addOtherFiles(pf.otherFilesRun, pf.outputDir);
    }
    if (pf.ipsComparator != null) {
      pf.ipsComparator.addOtherFiles(pf.otherFilesRun, pf.outputDir);
    }
    pf.otherFilesRun.add(Utilities.path(pf.tempDir, "usage-stats.json"));

    Set<String> urls = new HashSet<>();
    for (DataSetInformation t : pf.dataSets) {
      processDataSet(urls, t);
    }
    saveResolvedUrls(urls);

    printMemUsage();
    log("Reclaiming memory...");
    cleanOutput(pf.tempDir);
    for (FetchedFile f : pf.fileList) {
      f.trim();
    }
    pf.context.unload();
    for (RelatedIG ig : pf.relatedIGs) {
      ig.dump();
    }
    System.gc();
    printMemUsage();

    if (pf.nestedIgConfig != null) {
      if (pf.nestedIgOutput == null || pf.igArtifactsPage == null) {
        throw new Exception("If nestedIgConfig is specified, then nestedIgOutput and igArtifactsPage must also be specified.");
      }
      pf.inspector.setAltRootFolder(pf.nestedIgOutput);
      log("");
      log("**************************");
      log("Processing nested IG: " + pf.nestedIgConfig);
      pf.childPublisher = new Publisher();
      pf.childPublisher.setConfigFile(Utilities.path(FileUtilities.getDirectoryForFile(this.getConfigFile()), pf.nestedIgConfig));
      pf.childPublisher.setJekyllCommand(this.getJekyllCommand());
      pf.childPublisher.settings.setTxServer(this.settings.getTxServer());
      pf.childPublisher.settings.setDebug(this.settings.isDebug());
      pf.childPublisher.settings.setCacheOption(this.settings.getCacheOption());
      pf.childPublisher.setIsChild(true);
      pf.childPublisher.settings.setMode(this.getMode());
      pf.childPublisher.settings.setTargetOutput(this.getTargetOutputNested());

      try {
        pf.childPublisher.execute();
        log("Done processing nested IG: " + pf.nestedIgConfig);
        log("**************************");
        pf.childPublisher.updateInspector(pf.inspector, pf.nestedIgOutput);
      } catch (Exception e) {
        log("Publishing Child IG Failed: " + pf.nestedIgConfig);
        throw e;
      }
      createToc(pf.childPublisher.getPublishedIg().getDefinition().getPage(), pf.igArtifactsPage, pf.nestedIgOutput);
    }
    fixSearchForm();
    if (!settings.isGenerationOff()) {
      templateBeforeJekyll();
    }

    if (runTool()) {
      if (!settings.isGenerationOff()) {
        templateOnCheck();
      }

      if (!pf.changeList.isEmpty()) {
        File df = makeSpecFile();
        addFileToNpm(NPMPackageGenerator.Category.OTHER, "spec.internals", FileUtilities.fileToBytes(df.getAbsolutePath()));
        addFileToNpm(NPMPackageGenerator.Category.OTHER, "validation-summary.json", validationSummaryJson());
        addFileToNpm(NPMPackageGenerator.Category.OTHER, "validation-oo.json", validationSummaryOO());
        for (String t : pf.testDirs) {
          addTestDir(new File(t), t);
        }
        for (DataSetInformation t : pf.dataSets) {
          processDataSetContent(t);
        }
        for (String n : pf.otherDirs) {
          File f = new File(n);
          if (f.exists()) {
            for (File ff : f.listFiles()) {
              if (!SimpleFetcher.isIgnoredFile(f.getName())) {
                addFileToNpm(NPMPackageGenerator.Category.OTHER, ff.getName(), FileUtilities.fileToBytes(ff.getAbsolutePath()));
              }
            }
          } else {
            logMessage("Other Directory not found: "+n);
          }
        }
        File pr = new File(Utilities.path(FileUtilities.getDirectoryForFile(settings.getConfigFile()), "publication-request.json"));
        if (settings.getMode() != PublisherUtils.IGBuildMode.PUBLICATION && pr.exists()) {
          addFileToNpm(NPMPackageGenerator.Category.OTHER, "publication-request.json", FileUtilities.fileToBytes(pr));
        }
        pf.npm.finish();
        for (NPMPackageGenerator vnpm : pf.vnpms.values()) {
          vnpm.finish();
        }
        for (NPMPackageGenerator vnpm : pf.lnpms.values()) {
          vnpm.finish();
        }
        if (pf.r4tor4b.canBeR4() && pf.r4tor4b.canBeR4B()) {
          try {
            pf.r4tor4b.clonePackage(pf.npmName, pf.npm.filename());
          } catch (Exception e) {
            pf.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.EXCEPTION, "package.tgz", "Error converting pacakge to R4B: "+e.getMessage(), ValidationMessage.IssueSeverity.ERROR));
          }
        }

        if (settings.getMode() == null || settings.getMode() == PublisherUtils.IGBuildMode.MANUAL) {
          if (settings.isCacheVersion()) {
            pf.pcm.addPackageToCache(pf.publishedIg.getPackageId(), pf.publishedIg.getVersion(), new FileInputStream(pf.npm.filename()), "[output]");
          } else {
            pf.pcm.addPackageToCache(pf.publishedIg.getPackageId(), "dev", new FileInputStream(pf.npm.filename()), "[output]");
            if (pf.branchName != null) {
              pf.pcm.addPackageToCache(pf.publishedIg.getPackageId(), "dev$"+ pf.branchName, new FileInputStream(pf.npm.filename()), "[output]");
            }
          }
        } else if (settings.getMode() == PublisherUtils.IGBuildMode.PUBLICATION) {
          pf.pcm.addPackageToCache(pf.publishedIg.getPackageId(), pf.publishedIg.getVersion(), new FileInputStream(pf.npm.filename()), "[output]");
        }
        JsonArray json = new JsonArray();
        for (String s : pf.generateVersions) {
          json.add(s);
          //generatePackageVersion(npm.filename(), s);
        }
        FileUtilities.bytesToFile(org.hl7.fhir.utilities.json.parser.JsonParser.composeBytes(json), Utilities.path(pf.outputDir, "sub-package-list.json"));
        generateZips(df);
      }
    }

    if (pf.childPublisher !=null) {
      // Combine list of files so that the validation report will include everything
      pf.fileList.addAll(pf.childPublisher.getFileList());
    }

    if (!isChild()) {
      log("Checking Output HTML");
      String statusMessage;
      Map<String, String> statusMessages = new HashMap<>();
      if (settings.getMode() == PublisherUtils.IGBuildMode.AUTOBUILD) {
        statusMessage = pf.rc.formatPhrase(RenderingI18nContext.STATUS_MSG_AUTOBUILD, Utilities.escapeXml(pf.sourceIg.present()), Utilities.escapeXml(pf.sourceIg.getPublisher()), Utilities.escapeXml(workingVersion()), gh(), pf.igpkp.getCanonical());
        for (String lang : allLangs()) {
          statusMessages.put(lang, pf.rcLangs.get(lang).formatPhrase(RenderingI18nContext.STATUS_MSG_AUTOBUILD, Utilities.escapeXml(pf.sourceIg.present()), Utilities.escapeXml(pf.sourceIg.getPublisher()), Utilities.escapeXml(workingVersion()), gh(), pf.igpkp.getCanonical()));
        }
      } else if (settings.getMode() == PublisherUtils.IGBuildMode.PUBLICATION) {
        statusMessage = pf.rc.formatPhrase(RenderingI18nContext.STATUS_MSG_PUBLICATION_HOLDER);
        for (String lang : allLangs()) {
          statusMessages.put(lang, pf.rcLangs.get(lang).formatPhrase(RenderingI18nContext.STATUS_MSG_PUBLICATION_HOLDER));
        }
      } else {
        statusMessage = pf.rc.formatPhrase(RenderingI18nContext.STATUS_MSG_LOCAL_BUILD, Utilities.escapeXml(pf.sourceIg.present()), Utilities.escapeXml(workingVersion()), pf.igpkp.getCanonical());
        for (String lang : allLangs()) {
          statusMessages.put(lang, pf.rcLangs.get(lang).formatPhrase(RenderingI18nContext.STATUS_MSG_LOCAL_BUILD, Utilities.escapeXml(pf.sourceIg.present()), Utilities.escapeXml(workingVersion()), pf.igpkp.getCanonical()));
        }
      }

      pf.realmRules.addOtherFiles(pf.inspector.getExceptions(), pf.outputDir);
      pf.previousVersionComparator.addOtherFiles(pf.inspector.getExceptions(), pf.outputDir, pf.getExemptHtmlPatterns());
      if (pf.ipaComparator != null) {
        pf.ipaComparator.addOtherFiles(pf.inspector.getExceptions(), pf.outputDir);
      }
      if (pf.ipsComparator != null) {
        pf.ipsComparator.addOtherFiles(pf.inspector.getExceptions(), pf.outputDir);
      }

      if (!settings.isGenerationOff()) {
        AIProcessor ai = new AIProcessor(pf.inspector.getRootFolder());
        if (isNewML()) {
          ai.processNewTemplates(allLangs());
        } else {
          ai.processOldTemplates();
        }
      }

      pf.inspector.setReqFolder(pf.repoRoot);
      pf.inspector.setDefaultLang(pf.defaultTranslationLang);
      pf.inspector.setTranslationLangs(pf.translationLangs);
      List<ValidationMessage> linkmsgs = settings.isGenerationOff() ? new ArrayList<ValidationMessage>() : pf.inspector.check(statusMessage, statusMessages);
      int bl = 0;
      int lf = 0;
      for (ValidationMessage m : ValidationPresenter.filterMessages(null, linkmsgs, true, pf.suppressedMessages)) {
        if (m.getLevel() == ValidationMessage.IssueSeverity.ERROR) {
          if (m.getType() == ValidationMessage.IssueType.NOTFOUND) {
            bl++;
          } else {
            lf++;
          }
        } else if (m.getLevel() == ValidationMessage.IssueSeverity.FATAL) {
          throw new Exception(m.getMessage());
        }
      }
      log("  ... "+Integer.toString(pf.inspector.total())+" html "+checkPlural("file", pf.inspector.total())+", "+Integer.toString(lf)+" "+checkPlural("page", lf)+" invalid xhtml ("+Integer.toString((lf*100)/(pf.inspector.total() == 0 ? 1 : pf.inspector.total()))+"%)");
      log("  ... "+Integer.toString(pf.inspector.links())+" "+checkPlural("link", pf.inspector.links())+", "+Integer.toString(bl)+" broken "+checkPlural("link", lf)+" ("+Integer.toString((bl*100)/(pf.inspector.links() == 0 ? 1 : pf.inspector.links()))+"%)");
      pf.errors.addAll(linkmsgs);
      if (pf.brokenLinksError && linkmsgs.size() > 0) {
        throw new Error("Halting build because broken links have been found, and these are disallowed in the IG control file");
      }
      if (settings.getMode() == PublisherUtils.IGBuildMode.AUTOBUILD && !pf.inspector.getPublishBoxOK()) {
        throw new FHIRException("The auto-build infrastructure does not publish IGs unless the publish-box is present ("+ pf.inspector.getPublishboxParsingSummary()+"). For further information, see note at http://wiki.hl7.org/index.php?title=FHIR_Implementation_Guide_Publishing_Requirements#HL7_HTML_Standards_considerations");
      }
      generateFragmentUsage();
      log("Build final .zip");
      if (settings.getMode() == PublisherUtils.IGBuildMode.PUBLICATION) {
        IGReleaseVersionUpdater igvu = new IGReleaseVersionUpdater(pf.outputDir, null, null, new ArrayList<>(), new ArrayList<>(), null, pf.outputDir);
        String fragment = pf.sourceIg.present()+" - Downloaded Version "+ pf.businessVersion +" See the <a href=\""+ pf.igpkp.getCanonical()+"/history.html\">Directory of published versions</a></p>";
        igvu.updateStatement(fragment, 0, null);
      }
      ZipGenerator zip = new ZipGenerator(Utilities.path(pf.tempDir, "full-ig.zip"));
      zip.addFolder(pf.outputDir, "site/", false);
      zip.addFileSource("index.html", REDIRECT_SOURCE, false);
      zip.close();
      FileUtilities.copyFile(Utilities.path(pf.tempDir, "full-ig.zip"), Utilities.path(pf.outputDir, "full-ig.zip"));
      log("Final .zip built");
    }
  }

  private byte[] fillLangTemplate(byte[] b, String l) {
    try {
      String s = new String(b, "UTF-8");
      if (s.startsWith("--")) {
        s = s.replace("{{lang}}", l);
        return s.getBytes("UTF-8");
      } else {
        return b;
      }
    } catch (Exception e) {
      return b;
      }
  }

  private void copyData() throws IOException {
    for (String d : pf.dataDirs) {
      File[] fl = new File(d).listFiles();
      if (fl == null) {
        logDebugMessage(LogCategory.PROGRESS, "No files found to copy at "+d);
      } else {
        for (File f : fl) {
          String df = Utilities.path(this.pf.tempDir, "_data", f.getName());
          this.pf.otherFilesRun.add(df);
          FileUtilities.copyFile(f, new File(df));
        }
      }
    }
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

  private void regenerate(String uri) throws Exception {
    Resource res ;
    if (uri.contains("/StructureDefinition/")) {
      res = pf.context.fetchResource(StructureDefinition.class, uri);
    } else {
      throw new Exception("Unable to process "+uri);
    }

    if (res == null) {
      throw new Exception("Unable to find regeneration source for "+uri);
    }

    CanonicalResource bc = (CanonicalResource) res;

    FetchedFile f = new FetchedFile(bc.getWebPath());
    FetchedResource r = f.addResource(f.getName()+" (regen)");
    r.setResource(res);
    r.setId(bc.getId());
    r.setTitle(bc.getName());
    r.setValidated(true);
    r.setElement(convertToElement(r, bc));
    this.pf.igpkp.findConfiguration(f, r);
    bc.setUserData(UserDataNames.pub_resource_config, r.getConfig());
    generateNativeOutputs(f, true, null);
    generateHtmlOutputs(f, true, null, null);
  }


  private void generateNativeOutputs(FetchedFile f, boolean regen, DBBuilder db) throws IOException, FHIRException, SQLException {
    for (FetchedResource r : f.getResources()) {
      logDebugMessage(LogCategory.PROGRESS, "Produce resources for "+r.fhirType()+"/"+r.getId());
      var json = saveNativeResourceOutputs(f, r);
      if (db != null) {
        db.saveResource(f, r, json);
      }
    }
  }

  private void generateHtmlOutputs(FetchedFile f, boolean regen, DBBuilder db, String lang) throws Exception {
    if (this.settings.isGenerationOff()) {
      return;
    }
    if (f.getProcessMode() == FetchedFile.PROCESS_NONE) {
      String dst = Utilities.path(this.pf.tempDir, f.getRelativePath());
      try {
        if (f.isFolder()) {
          f.getOutputNames().add(dst);
          FileUtilities.createDirectory(dst);
        } else {
          if (isNewML() && !f.getStatedPath().contains(File.separator+"template"+File.separator)) {
            for (String l : allLangs()) {
              if (f.getRelativePath().startsWith("_includes"+File.separator)) {
                dst = Utilities.path(this.pf.tempDir, addLangFolderToFilename(f.getRelativePath(), l));
              } else {
                dst = Utilities.path(this.pf.tempDir, l, f.getRelativePath());
              }
              byte[] src = loadTranslationSource(f, l);
              if (f.getPath().endsWith(".md")) {
                checkMakeFile(processCustomLiquid(db, stripFrontMatter(src), f, lang), dst, f.getOutputNames());
              } else {
                checkMakeFile(processCustomLiquid(db, src, f, lang), dst, f.getOutputNames());
              }
            }
            dst = Utilities.path(this.pf.tempDir, f.getRelativePath());
            if (f.getPath().endsWith(".md")) {
              checkMakeFile(processCustomLiquid(db, stripFrontMatter(f.getSource()), f, lang), dst, f.getOutputNames());
            } else {
              checkMakeFile(processCustomLiquid(db, f.getSource(), f, lang), dst, f.getOutputNames());
            }
          } else {
            if (f.getPath().endsWith(".md")) {
              checkMakeFile(processCustomLiquid(db, stripFrontMatter(f.getSource()), f, lang), dst, f.getOutputNames());
            } else {
              checkMakeFile(processCustomLiquid(db, f.getSource(), f, lang), dst, f.getOutputNames());
            }
          }
        }
      } catch (IOException e) {
        log("Exception generating page "+dst+" for "+f.getRelativePath()+" in "+ this.pf.tempDir +": "+e.getMessage());

      }
    } else if (f.getProcessMode() == FetchedFile.PROCESS_XSLT) {
      String dst = Utilities.path(this.pf.tempDir, f.getRelativePath());
      try {
        if (f.isFolder()) {
          f.getOutputNames().add(dst);
          FileUtilities.createDirectory(dst);
        } else {
          if (isNewML() && !f.getStatedPath().contains(File.separator+"template"+File.separator)) {
            for (String l : allLangs()) {
              if (f.getRelativePath().startsWith("_includes"+File.separator)) {
                dst = Utilities.path(this.pf.tempDir, addLangFolderToFilename(f.getRelativePath(), l));
              } else {
                dst = Utilities.path(this.pf.tempDir, l, f.getRelativePath());
              }
              byte[] src = loadTranslationSource(f, l);
              checkMakeFile(processCustomLiquid(db, new XSLTransformer(this.settings.isDebug()).transform(src, f.getXslt()), f, lang), dst, f.getOutputNames());
            }
          } else {
            checkMakeFile(processCustomLiquid(db, new XSLTransformer(this.settings.isDebug()).transform(f.getSource(), f.getXslt()), f, lang), dst, f.getOutputNames());
          }
        }
      } catch (Exception e) {
        log("Exception generating xslt page "+dst+" for "+f.getRelativePath()+" in "+ this.pf.tempDir +": "+e.getMessage());
      }
    } else {
      if (isNewML()) {
        generateHtmlOutputsInner(f, regen, db, null, this.pf.rc);
        for (String l : allLangs()) {
          generateHtmlOutputsInner(f, regen, db, l, this.pf.rcLangs.get(l));
        }
      } else {
        generateHtmlOutputsInner(f, regen, db, null, this.pf.rc);
      }
    }
  }

  private void generateHtmlOutputsInner(FetchedFile f, boolean regen, DBBuilder db, String lang, RenderingContext lrc)
          throws IOException, FileNotFoundException, Exception, Error {
    lrc = lrc.copy(true); // whatever happens in here shouldn't alter the settings on the base RenderingContext
    saveFileOutputs(f, lang);
    for (FetchedResource r : f.getResources()) {

      logDebugMessage(LogCategory.PROGRESS, "Produce outputs for "+r.fhirType()+"/"+r.getId());
      Map<String, String> vars = makeVars(r);
      makeTemplates(f, r, vars, lang, lrc);
      saveDirectResourceOutputs(f, r, r.getResource(), vars,lang, lrc);
///*        if (r.getResEntry() != null && r.getResEntry().hasExtension("http://hl7.org/fhir/tools/StructureDefinition/implementationguide-resource-fragment")) {
//          generateResourceFragments(f, r, System.currentTimeMillis());
//        }*/
      List<StringPair> clist = new ArrayList<>();
      if (r.getResource() == null && !r.isCustomResource()) {
        try {
          Resource container = convertFromElement(r.getElement());
          r.setResource(container);
        } catch (Exception e) {
          if (Utilities.existsInList(r.fhirType(), "CodeSystem", "ValueSet", "ConceptMap", "List", "CapabilityStatement", "StructureDefinition", "OperationDefinition", "StructureMap", "Questionnaire", "Library")) {
            logMessage("Unable to convert resource " + r.getTitle() + " for rendering: " + e.getMessage());
            logMessage("This resource should already have been converted, so it is likely invalid. It won't be rendered correctly, and Jekyll is quite likely to fail (depends on the template)");
          } else if (Utilities.existsInList(r.fhirType(), new ContextUtilities(this.pf.context, this.pf.suppressedMappings).getCanonicalResourceNames())) {
            logMessage("Unable to convert resource " + r.getTitle() + " for rendering: " + e.getMessage());
            logMessage("This resource is a canonical resource and might not be rendered correctly, and Jekyll may fail (depends on the template)");
          } else if (r.getElement().hasChildren("contained")) {
            logMessage("Unable to convert resource " + r.getTitle() + " for rendering: " + e.getMessage());
            logMessage("This resource contains other resources that won't be rendered correctly, and Jekyll may fail");
          } else {
            // we don't care
          }
        }

      }
      if (r.getResource() != null) {
        generateResourceHtml(f, regen, r, r.getResource(), vars, "", db, lang, lrc);
        if (r.getResource() instanceof DomainResource) {
          DomainResource container = (DomainResource) r.getResource();
          List<org.hl7.fhir.r5.elementmodel.Element> containedElements = r.getElement().getChildren("contained");
          List<Resource> containedResources = container.getContained();
          if (containedResources.size() > containedElements.size()) {
            throw new Error("Error: containedResources.size ("+containedResources.size()+") > containedElements.size ("+containedElements.size()+")");
          }
          // we have a list of the elements, and of the resources.
          // The resources might not be the same as the elements - they've been converted to R5. We'll use the resources
          // if that's ok, else we'll use the element (resources render better)
          for (int i = 0; i < containedResources.size(); i++ ) {
            Element containedElement = containedElements.get(i);
            Resource containedResource = containedResources.get(i);
            if (RendererFactory.hasSpecificRenderer(containedElement.fhirType())) {
              if (containedElement.fhirType().equals(containedResource.fhirType())) {
                String prefixForContained = r.getResource().getId()+"_";
                makeTemplatesContained(f, r, containedResource, vars, prefixForContained, lang);
                String fn = saveDirectResourceOutputsContained(f, r, containedResource, vars, prefixForContained, lang);
                if (containedResource instanceof CanonicalResource) {
                  CanonicalResource cr = ((CanonicalResource) containedResource).copy();
                  cr.copyUserData(container);
                  if (!(container instanceof CanonicalResource)) {
                    if (!cr.hasUrl() || !cr.hasVersion()) {
                      //                    throw new FHIRException("Unable to publish: contained canonical resource in a non-canonical resource does not have url+version");
                    }
                  } else {
                    cr.copyUserData(container);
                    if (!cr.hasUrl()) {
                      cr.setUrl(((CanonicalResource) container).getUrl()+"#"+containedResource.getId());
                    }
                    if (!cr.hasVersion()) {
                      cr.setVersion(((CanonicalResource) container).getVersion());
                    }
                  }
                  generateResourceHtml(f, regen, r, cr, vars, prefixForContained, db, lang, lrc);
                  clist.add(new StringPair(cr.present(), fn));
                } else {
                  generateResourceHtml(f, regen, r, containedResource, vars, prefixForContained, db, lang, lrc);
                  clist.add(new StringPair(containedResource.fhirType()+"/"+containedResource.getId(), fn));
                }
              }
            }
          }
        }
      } else {
        // element contained
        // TODO: figure this out
        //          for (Element c : r.getElement().getChildren("contained")) {
        //            if (hasSpecificRenderer(c.fhirType())) {
        //              String t = c.getChildValue("title");
        //              if (Utilities.noString(t)) {
        //                t = c.getChildValue("name");
        //              }
        //              String d = c.getChildValue("description");
        //              if (Utilities.noString(d)) {
        //                d = c.getChildValue("definition");
        //              }
        //              CanonicalResource canonical = null;
        //              if (Utilities.existsInList(c.fhirType(), VersionUtilities.getCanonicalResourceNames(context.getVersion()))) {
        //                try {
        //                  canonical = (CanonicalResource)convertFromElement(c);
        //                } catch (Exception ex) {
        //                  System.out.println("Error converting contained resource " + t + " - " + ex.getMessage());
        //                }
        //              }
        //              list.add(new ContainedResourceDetails(c.fhirType(), c.getIdBase(), t, d, canonical));
        //            }
        //          }
        if ("QuestionnaireResponse".equals(r.fhirType())) {
          String prefixForContained = "";
          generateOutputsQuestionnaireResponse(f, r, vars, prefixForContained, lang);
        }
      }
      if (wantGen(r, "contained-index")) {
        long start = System.currentTimeMillis();
        fragment(r.fhirType()+"-"+r.getId()+"-contained-index", genContainedIndex(r, clist, null), f.getOutputNames(), start, "contained-index", "Resource", lang);
      }
    }
  }

  private void generateSpreadsheets(FetchedFile f, boolean regen, DBBuilder db) throws Exception {
    if (this.settings.isGenerationOff()) {
      return;
    }
    //    System.out.println("gen2: "+f.getName());
    if (f.getProcessMode() == FetchedFile.PROCESS_NONE) {
      // nothing
    } else if (f.getProcessMode() == FetchedFile.PROCESS_XSLT) {
      // nothing
    } else {
      for (FetchedResource r : f.getResources()) {
        Map<String, String> vars = makeVars(r);
        if (r.getResource() != null) {
          generateResourceSpreadsheets(f, regen, r, r.getResource(), vars, "", db);
        }
      }
    }
  }
  private void saveNativeResourceOutputFormats(FetchedFile f, FetchedResource r, Element element, String lang) throws IOException, FileNotFoundException {
    String path;
    if (wantGen(r, "xml") || forHL7orFHIR()) {
      if (this.settings.isNewMultiLangTemplateFormat() && lang != null) {
        path = Utilities.path(this.pf.tempDir, lang, r.fhirType()+"-"+r.getId()+".xml");
      } else {
        path = Utilities.path(this.pf.tempDir, r.fhirType()+"-"+r.getId()+".xml");
      }
      f.getOutputNames().add(path);
      FileOutputStream stream = new FileOutputStream(path);
      org.hl7.fhir.r5.elementmodel.XmlParser xp = new org.hl7.fhir.r5.elementmodel.XmlParser(this.pf.context);
      if (suppressId(f, r)) {
        xp.setIdPolicy(ParserBase.IdRenderingPolicy.NotRoot);
      }
      xp.compose(element, stream, IParser.OutputStyle.PRETTY, this.pf.igpkp.getCanonical());
      stream.close();
    }
    if (wantGen(r, "json") || forHL7orFHIR()) {
      if (this.settings.isNewMultiLangTemplateFormat() && lang != null) {
        path = Utilities.path(this.pf.tempDir, lang, r.fhirType()+"-"+r.getId()+".json");
      } else {
        path = Utilities.path(this.pf.tempDir, r.fhirType()+"-"+r.getId()+".json");
      }
      f.getOutputNames().add(path);
      FileOutputStream stream = new FileOutputStream(path);
      org.hl7.fhir.r5.elementmodel.JsonParser jp = new org.hl7.fhir.r5.elementmodel.JsonParser(this.pf.context);
      if (suppressId(f, r)) {
        jp.setIdPolicy(ParserBase.IdRenderingPolicy.NotRoot);
      }
      jp.compose(element, stream, IParser.OutputStyle.PRETTY, this.pf.igpkp.getCanonical());
      stream.close();
    }
    if (wantGen(r, "ttl")) {
      if (this.settings.isNewMultiLangTemplateFormat() && lang != null) {
        path = Utilities.path(this.pf.tempDir, lang, r.fhirType()+"-"+r.getId()+".ttl");
      } else {
        path = Utilities.path(this.pf.tempDir, r.fhirType()+"-"+r.getId()+".ttl");
      }
      f.getOutputNames().add(path);
      FileOutputStream stream = new FileOutputStream(path);
      org.hl7.fhir.r5.elementmodel.TurtleParser tp = new org.hl7.fhir.r5.elementmodel.TurtleParser(this.pf.context);
      if (suppressId(f, r)) {
        tp.setIdPolicy(ParserBase.IdRenderingPolicy.NotRoot);
      }
      tp.compose(element, stream, IParser.OutputStyle.PRETTY, this.pf.igpkp.getCanonical());
      stream.close();
    }
  }

  private boolean isExample(FetchedFile f, FetchedResource r) {
    ImplementationGuide.ImplementationGuideDefinitionResourceComponent igr = findIGReference(r.fhirType(), r.getId());
    if (igr == null)
      return false;
    else
      return igr.getIsExample();
  }


  private void saveFileOutputs(FetchedFile f, String lang) throws IOException, FHIRException {
    long start = System.currentTimeMillis();
    if (f.getResources().size() == 1) {
      Map<String, String> vars = new HashMap<>();
      FetchedResource r = f.getResources().get(0);
      StringBuilder b = new StringBuilder();
      b.append("<table class=\"grid\" data-fhir=\"generated-heirarchy\">\r\n");
      b.append("<tr><td><b>Level</b></td><td><b>Type</b></td><td><b>Location</b></td><td><b>Message</b></td></tr>\r\n");
      genVMessage(b, f.getErrors(), ValidationMessage.IssueSeverity.FATAL);
      genVMessage(b, f.getErrors(), ValidationMessage.IssueSeverity.ERROR);
      genVMessage(b, f.getErrors(), ValidationMessage.IssueSeverity.WARNING);
      genVMessage(b, f.getErrors(), ValidationMessage.IssueSeverity.INFORMATION);
      b.append("</table>\r\n");
      fragment(r.fhirType()+"-"+r.getId()+"-validation", b.toString(), f.getOutputNames(), r, vars, null, start, "file", "File", lang);
    }
  }

  public void genVMessage(StringBuilder b, List<ValidationMessage> vms, ValidationMessage.IssueSeverity lvl) {
    for (ValidationMessage vm : vms) {
      if (vm.getLevel() == lvl) {
        b.append("<tr><td>");
        b.append(vm.getLevel().toCode());
        b.append("</td><td>");
        b.append(vm.getType().toCode());
        b.append("</td><td>");
        b.append(vm.getLocation());
        b.append("</td><td>");
        b.append(vm.getHtml());
        b.append("</td><td>");
        b.append("</td></tr>\r\n");
      }
    }
  }

  private void makeTemplatesContained(FetchedFile f, FetchedResource r, Resource res, Map<String, String> vars, String prefixForContained, String lang) throws FileNotFoundException, Exception {
    String baseName = this.pf.igpkp.getPropertyContained(r, "base", res);
    if (res != null && res instanceof StructureDefinition) {
      if (this.pf.igpkp.hasProperty(r, "template-base-"+((StructureDefinition) res).getKind().toCode().toLowerCase(), res))
        genWrapperContained(f, r, res, this.pf.igpkp.getPropertyContained(r, "template-base-"+((StructureDefinition) res).getKind().toCode().toLowerCase(), res), baseName, f.getOutputNames(), vars, null, "", prefixForContained, lang);
      else
        genWrapperContained(f, r, res, this.pf.igpkp.getPropertyContained(r, "template-base", res), baseName, f.getOutputNames(), vars, null, "", prefixForContained, lang);
    } else
      genWrapperContained(f, r, res, this.pf.igpkp.getPropertyContained(r, "template-base", res), baseName, f.getOutputNames(), vars, null, "", prefixForContained, lang);
    genWrapperContained(null, r, res, this.pf.igpkp.getPropertyContained(r, "template-defns", res), this.pf.igpkp.getPropertyContained(r, "defns", res), f.getOutputNames(), vars, null, "definitions", prefixForContained, lang);
    for (String templateName : this.pf.extraTemplates.keySet()) {
      if (!templateName.equals("format") && !templateName.equals("defns") && !templateName.equals("change-history")) {
        String output = this.pf.igpkp.getProperty(r, templateName);
        if (output == null)
          output = r.fhirType()+"-"+r.getId()+"_"+res.getId()+"-"+templateName+".html";
        genWrapperContained(null, r, res, this.pf.igpkp.getPropertyContained(r, "template-"+templateName, res), output, f.getOutputNames(), vars, null, templateName, prefixForContained, lang);
      }
    }
  }

  private void makeTemplates(FetchedFile f, FetchedResource r, Map<String, String> vars, String lang, RenderingContext lrc) throws FileNotFoundException, Exception {
    String baseName = this.pf.igpkp.getProperty(r, "base");
    if (r.getResource() != null && r.getResource() instanceof StructureDefinition) {
      if (this.pf.igpkp.hasProperty(r, "template-base-"+((StructureDefinition) r.getResource()).getKind().toCode().toLowerCase(), null))
        genWrapper(f, r, this.pf.igpkp.getProperty(r, "template-base-"+((StructureDefinition) r.getResource()).getKind().toCode().toLowerCase()), baseName, f.getOutputNames(), vars, null, "", true, lang, lrc);
      else
        genWrapper(f, r, this.pf.igpkp.getProperty(r, "template-base"), baseName, f.getOutputNames(), vars, null, "", true, lang, lrc);
    } else
      genWrapper(f, r, this.pf.igpkp.getProperty(r, "template-base"), baseName, f.getOutputNames(), vars, null, "", true, lang, lrc);
    genWrapper(null, r, this.pf.igpkp.getProperty(r, "template-defns"), this.pf.igpkp.getProperty(r, "defns"), f.getOutputNames(), vars, null, "definitions", false, lang, lrc);
    for (String templateName : this.pf.extraTemplates.keySet()) {
      if (!templateName.equals("format") && !templateName.equals("defns")) {
        String output = this.pf.igpkp.getProperty(r, templateName);
        if (output == null)
          output = r.fhirType()+"-"+r.getId()+"-"+templateName+".html";
        genWrapper(null, r, this.pf.igpkp.getProperty(r, "template-"+templateName), output, f.getOutputNames(), vars, null, templateName, false, lang, lrc);
      }
    }
  }

  private void saveDirectResourceOutputs(FetchedFile f, FetchedResource r, Resource res, Map<String, String> vars, String lang, RenderingContext lrc) throws FileNotFoundException, Exception {
// Lloyd debug: res!=null && res.getId().equals("SdcQuestionLibrary")
    Element langElement = r.getElement();
    if (lang != null) {
      langElement = this.pf.langUtils.copyToLanguage(langElement, lang, true, r.getElement().getChildValue("language"), pf.defaultTranslationLang, r.getErrors());
      res = this.pf.langUtils.copyToLanguage(res, lang, true, pf.defaultTranslationLang, r.getErrors());
    }
    boolean example = r.isExample();
    if (wantGen(r, "maturity") && res != null) {
      long start = System.currentTimeMillis();
      fragment(res.fhirType()+"-"+r.getId()+"-maturity",  genFmmBanner(r, null), f.getOutputNames(), start, "maturity", "Resource", lang);
    }
    if (wantGen(r, "ip-statements") && res != null) {
      long start = System.currentTimeMillis();
      fragment(res.fhirType()+"-"+r.getId()+"-ip-statements", new IPStatementsRenderer(this.pf.context, this.pf.markdownEngine, this.pf.sourceIg.getPackageId(), lrc).genIpStatements(r, example, lang), f.getOutputNames(), start, "ip-statements", "Resource", lang);
    }
    if (wantGen(r, "validate")) {
      long start = System.currentTimeMillis();
      fragment(r.fhirType()+"-"+r.getId()+"-validate",  genValidation(f, r), f.getOutputNames(), start, "validate", "Resource", lang);
    }

    if (wantGen(r, "status") && res instanceof DomainResource) {
      long start = System.currentTimeMillis();
      fragment(r.fhirType()+"-"+r.getId()+"-status",  genStatus(f, r, res, null), f.getOutputNames(), start, "status", "Resource", lang);
    }

    String template = this.pf.igpkp.getProperty(r, "template-format");
    if (wantGen(r, "xml")) {
      genWrapper(null, r, template, this.pf.igpkp.getProperty(r, "format"), f.getOutputNames(), vars, "xml", "", false, lang, lrc);
    }
    if (wantGen(r, "json")) {
      genWrapper(null, r, template, this.pf.igpkp.getProperty(r, "format"), f.getOutputNames(), vars, "json", "", false, lang, lrc);
    }
    if (wantGen(r, "jekyll-data") && this.pf.produceJekyllData) {
      org.hl7.fhir.r5.elementmodel.JsonParser jp = new org.hl7.fhir.r5.elementmodel.JsonParser(this.pf.context);
      FileOutputStream bs = new FileOutputStream(Utilities.path(this.pf.tempDir, "_data", r.fhirType()+"-"+r.getId()+".json"));
      jp.compose(langElement, bs, IParser.OutputStyle.NORMAL, null);
      bs.close();
    }
    if (wantGen(r, "ttl")) {
      genWrapper(null, r, template, this.pf.igpkp.getProperty(r, "format"), f.getOutputNames(), vars, "ttl", "", false, lang, lrc);
    }
    org.hl7.fhir.r5.elementmodel.XmlParser xp = new org.hl7.fhir.r5.elementmodel.XmlParser(this.pf.context);
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    xp.compose(langElement, bs, IParser.OutputStyle.NORMAL, null);
    int size = bs.size();

    Element e = langElement;
    if (SpecialTypeHandler.handlesType(r.fhirType(), this.pf.context.getVersion()) && !pf.customResourceNames.contains(r.fhirType())) {
      e = new ObjectConverter(this.pf.context).convert(r.getResource());
    } else if (this.pf.module.isNoNarrative() && e.hasChild("text")) {
      e = (Element) e.copy();
      e.removeChild("text");
    }

    if (wantGen(r, "xml-html")) {
      long start = System.currentTimeMillis();
      XmlXHtmlRenderer x = new XmlXHtmlRenderer();
      x.setPrism(size < PRISM_SIZE_LIMIT);
      xp.setLinkResolver(this.pf.igpkp);
      xp.setShowDecorations(false);
      if (suppressId(f, r)) {
        xp.setIdPolicy(ParserBase.IdRenderingPolicy.NotRoot);
      }
      xp.compose(e, x);
      fragment(r.fhirType()+"-"+r.getId()+"-xml-html", x.toString(), f.getOutputNames(), r, vars, "xml", start, "xml-html", "Resource", lang);
    }
    if (wantGen(r, "json-html")) {
      long start = System.currentTimeMillis();
      JsonXhtmlRenderer j = new JsonXhtmlRenderer();
      j.setPrism(size < PRISM_SIZE_LIMIT);
      org.hl7.fhir.r5.elementmodel.JsonParser jp = new org.hl7.fhir.r5.elementmodel.JsonParser(this.pf.context);
      jp.setLinkResolver(this.pf.igpkp);
      jp.setAllowComments(true);
      if (suppressId(f, r)) {
        jp.setIdPolicy(ParserBase.IdRenderingPolicy.NotRoot);
      }
      jp.compose(e, j);
      fragment(r.fhirType()+"-"+r.getId()+"-json-html", j.toString(), f.getOutputNames(), r, vars, "json", start, "json-html", "Resource", lang);
    }

    if (wantGen(r, "ttl-html")) {
      long start = System.currentTimeMillis();
      org.hl7.fhir.r5.elementmodel.TurtleParser ttl = new org.hl7.fhir.r5.elementmodel.TurtleParser(this.pf.context);
      ttl.setLinkResolver(this.pf.igpkp);
      Turtle rdf = new Turtle();
      if (suppressId(f, r)) {
        ttl.setIdPolicy(ParserBase.IdRenderingPolicy.NotRoot);
      }
      ttl.setStyle(IParser.OutputStyle.PRETTY);
      ttl.compose(e, rdf, "");
      fragment(r.fhirType()+"-"+r.getId()+"-ttl-html", rdf.asHtml(size < PRISM_SIZE_LIMIT), f.getOutputNames(), r, vars, "ttl", start, "ttl-html", "Resource", lang);
    }

    generateHtml(f, r, res, langElement, vars, lrc, lang);


    if (wantGen(r, "history")) {
      long start = System.currentTimeMillis();
      XhtmlComposer xc = new XhtmlComposer(XhtmlComposer.XML, this.pf.module.isNoNarrative());
      XhtmlNode xhtml = new HistoryGenerator(lrc).generate(r);
      String html = xhtml == null ? "" : xc.compose(xhtml);
      fragment(r.fhirType()+"-"+r.getId()+"-history", html, f.getOutputNames(), r, vars, null, start, "history", "Resource", lang);
    }

    //  NarrativeGenerator gen = new NarrativeGenerator(null, null, context);
    //  gen.generate(f.getElement(), false);
    //  xhtml = getXhtml(f);
    //  html = xhtml == null ? "" : new XhtmlComposer().compose(xhtml);
    //  fragment(f.getId()+"-gen-html", html);
  }

  private void generateHtml(FetchedFile f, FetchedResource rX, Resource lr, Element le, Map<String, String> vars, RenderingContext lrc,  String lang)
          throws Exception, UnsupportedEncodingException, IOException, EOperationOutcome {
    XhtmlComposer xc = new XhtmlComposer(XhtmlComposer.XML, this.pf.module.isNoNarrative());
    if (wantGen(rX, "html")) {
      long start = System.currentTimeMillis();
      // Lloyd debug: rX.getId().equals("SdcQuestionLibrary")
      XhtmlNode xhtml = (lang == null || lang.equals(le.getNamedChildValueSingle("language"))) ? getXhtml(f, rX, lr,le) : null;
      if (xhtml == null && HistoryGenerator.allEntriesAreHistoryProvenance(le)) {
        RenderingContext ctxt = lrc.copy(false).setParser(getTypeLoader(f, rX));
        List<ProvenanceDetails> entries = loadProvenanceForBundle(this.pf.igpkp.getLinkFor(rX, true), le, f);
        xhtml = new HistoryGenerator(ctxt).generateForBundle(entries);
        fragment(rX.fhirType()+"-"+rX.getId()+"-html", xc.compose(xhtml), f.getOutputNames(), rX, vars, null, start, "html", "Resource", lang);
      } else if (rX.fhirType().equals("Binary")) {
        String pfx = "";
        if (rX.getExampleUri() != null) {
          StructureDefinition sd = this.pf.context.fetchResource(StructureDefinition.class, rX.getExampleUri());
          if (sd != null && sd.getKind() == StructureDefinition.StructureDefinitionKind.LOGICAL) {
            pfx = "<p>This content is an example of the <a href=\""+Utilities.escapeXml(sd.getWebPath())+"\">"+Utilities.escapeXml(sd.present())+"</a> Logical Model and is not a FHIR Resource</p>\r\n";
          }
        }
        String html = null;
        if (rX.getLogicalElement() != null) {
          String rXContentType = rX.getElement().getNamedChildValueSingle("contentType");
          if (rXContentType.contains("xml")) {
            org.hl7.fhir.r5.elementmodel.XmlParser xmlParser = new org.hl7.fhir.r5.elementmodel.XmlParser(this.pf.context);
            XmlXHtmlRenderer xmlXHtmlRenderer = new XmlXHtmlRenderer();
            xmlXHtmlRenderer.setPrism(true);
            xmlParser.setElideElements(true);
            xmlXHtmlRenderer.setAutoNamespaces(true);
            xmlParser.setLinkResolver(this.pf.igpkp);
            xmlParser.setShowDecorations(false);
            if (suppressId(f, rX)) {
              xmlParser.setIdPolicy(ParserBase.IdRenderingPolicy.NotRoot);
            }
            xmlParser.compose(rX.getLogicalElement(), xmlXHtmlRenderer);
            html = xmlXHtmlRenderer.toString();
          } else if (rXContentType.contains("json")) {
            JsonXhtmlRenderer jsonXhtmlRenderer = new JsonXhtmlRenderer();
            jsonXhtmlRenderer.setPrism(true);
            org.hl7.fhir.r5.elementmodel.JsonParser jsonParser = new org.hl7.fhir.r5.elementmodel.JsonParser(this.pf.context);
            jsonParser.setLinkResolver(this.pf.igpkp);
            jsonParser.setAllowComments(true);
            jsonParser.setElideElements(true);
            if (suppressId(f, rX)) {
              jsonParser.setIdPolicy(ParserBase.IdRenderingPolicy.NotRoot);
            }
            jsonParser.compose(rX.getLogicalElement(), jsonXhtmlRenderer);
            html = jsonXhtmlRenderer.toString();
          }
        }
        if (html == null) {
          BinaryRenderer br = new BinaryRenderer(this.pf.tempDir, this.pf.template.getScriptMappings());
          if (lr instanceof Binary) {
            html = pfx + br.display((Binary) lr);
          } else if (le.fhirType().equals("Binary")) {
            html = pfx + br.display(le);
          } else if (f.getResources().size() == 1 && f.getSource() != null) {
            html = pfx + br.display(rX.getId(), rX.getContentType(), f.getSource());
          } else {
            html = pfx + br.display(le);
          }
          for (String fn : br.getFilenames()) {
            this.pf.otherFilesRun.add(Utilities.path(this.pf.tempDir, fn));
          }
        }
        fragment(rX.fhirType()+"-"+rX.getId()+"-html", html, f.getOutputNames(), rX, vars, null, start, "html", "Resource", lang);
      } else {
        if (xhtml == null || rX.isGeneratedNarrative()) {
          RenderingContext xlrc = lrc.copy(false);
          xlrc.setRules(RenderingContext.GenerationRules.IG_PUBLISHER);
          ResourceRenderer rr = RendererFactory.factory(rX.fhirType(), xlrc);
          if (lr != null && lr instanceof DomainResource) {
// Lloyd debug - getting here and dying
            xhtml = rr.buildNarrative(ResourceWrapper.forResource(xlrc, lr));
          } else {
            ResourceWrapper rw = ResourceWrapper.forResource(xlrc, le);
            try {
              xhtml = rr.buildNarrative(rw);
            } catch (Exception ex) {
              xhtml = new XhtmlNode(NodeType.Element, "div");
              xhtml.para("Error rendering resource: "+ex.getMessage());
            }
          }
        }
        String html = xhtml == null ? "" : xc.compose(xhtml);
        fragment(rX.fhirType()+"-"+rX.getId()+"-html", html, f.getOutputNames(), rX, vars, null, start, "html", "Resource", lang);
      }
    }
  }

  private List<ProvenanceDetails> loadProvenanceForBundle(String path, Element bnd, FetchedFile f) throws Exception {
    List<ProvenanceDetails> ret = new ArrayList<>();
    List<Element> entries = bnd.getChildrenByName("entry");
    for (int i = 0; i < entries.size(); i++) {
      Element entry = entries.get(i);
      Element res = entry.getNamedChild("resource");
      if (res != null && "Provenance".equals(res.fhirType())) {
        ret.add(processProvenanceForBundle(f, path, res));
      }
    }
    return ret;
  }

  private String saveDirectResourceOutputsContained(FetchedFile f, FetchedResource r, Resource res, Map<String, String> vars, String prefixForContained, String lang) throws FileNotFoundException, Exception {
    if (wantGen(r, "history")) {
      String html = "";
      long start = System.currentTimeMillis();
      fragment(res.fhirType()+"-"+prefixForContained+res.getId()+"-history", html, f.getOutputNames(), r, vars, null, start, "history", "Resource", lang);
    }
    if (wantGen(r, "html")) {
      long start = System.currentTimeMillis();
      XhtmlNode xhtml = getXhtml(f, r, res);
      if (xhtml == null && HistoryGenerator.allEntriesAreHistoryProvenance(r.getElement())) {
        RenderingContext ctxt = this.pf.rc.copy(false).setParser(getTypeLoader(f, r));
        List<ProvenanceDetails> entries = loadProvenanceForBundle(this.pf.igpkp.getLinkFor(r, true), r.getElement(), f);
        xhtml = new HistoryGenerator(ctxt).generateForBundle(entries);
        fragment(res.fhirType()+"-"+prefixForContained+res.getId()+"-html", new XhtmlComposer(XhtmlComposer.XML).compose(xhtml), f.getOutputNames(), r, vars, prefixForContained, start, "html", "Resource", lang);
      } else {
        String html = xhtml == null ? "" : new XhtmlComposer(XhtmlComposer.XML).compose(xhtml);
        fragment(res.fhirType()+"-"+prefixForContained+res.getId()+"-html", html, f.getOutputNames(), r, vars, prefixForContained, start, "html", "Resource", lang);
      }
    }
    return res.fhirType()+"-"+prefixForContained+res.getId()+".html"; // will be a broken link if wantGen(html) == false
  }

  private String genFmmBanner(FetchedResource r, String lang) throws FHIRException {
    String fmm = null;
    StandardsStatus ss = null;
    if (r.getResource() instanceof DomainResource) {
      fmm = ExtensionUtilities.readStringExtension((DomainResource) r.getResource(), ExtensionDefinitions.EXT_FMM_LEVEL);
      ss = ExtensionUtilities.getStandardsStatus((DomainResource) r.getResource());
    }
    if (ss == null)
      ss = StandardsStatus.TRIAL_USE;
    if (fmm != null) {
      return pf.rcLangs.get(lang).formatPhrase(RenderingContext.FMM_TABLE, fmm, checkAppendSlash(pf.specPath), ss.toDisplay());
    } else {
      return "";
    }
  }

  private String genStatus(FetchedFile f, FetchedResource r, Resource resource, String lang) throws FHIRException {
    org.hl7.fhir.igtools.renderers.StatusRenderer.ResourceStatusInformation info = StatusRenderer.analyse((DomainResource) resource);
    return StatusRenderer.render(this.pf.igpkp.specPath(), info, this.pf.rcLangs.get(lang));
  }

  private String genValidation(FetchedFile f, FetchedResource r) throws FHIRException {
    StringBuilder b = new StringBuilder();
    String version = this.settings.getMode() == PublisherUtils.IGBuildMode.AUTOBUILD ? "current" : this.settings.getMode() == PublisherUtils.IGBuildMode.PUBLICATION ? this.pf.publishedIg.getVersion() : "dev";
    if (isExample(f, r)) {
      // we're going to use javascript to determine the relative path of this for the user.
      b.append("<p data-fhir=\"generated\"><b>Validation Links:</b></p><ul><li><a href=\"https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator\">Validate using FHIR Validator</a> (Java): <code id=\"vl-"+r.fhirType()+"-"+r.getId()+"\">$cmd$</code></li></ul>\r\n");
      b.append("<script type=\"text/javascript\">\r\n");
      b.append("  var x = window.location.href;\r\n");
      b.append("  document.getElementById(\"vl-"+r.fhirType()+"-"+r.getId()+"\").innerHTML = \"java -jar [path]/org.hl7.fhir.validator.jar -ig "+ this.pf.publishedIg.getPackageId()+"#"+version+" \"+x.substr(0, x.lastIndexOf(\".\")).replace(\"file:///\", \"\") + \".json\";\r\n");
      b.append("</script>\r\n");
    } else if (r.getResource() instanceof StructureDefinition) {
      b.append("<p data-fhir=\"generated\">Validate this resource:</b></p><ul><li><a href=\"https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator\">Validate using FHIR Validator</a> (Java): <code>"+
              "java -jar [path]/org.hl7.fhir.validator.jar -ig "+ this.pf.publishedIg.getPackageId()+"#"+version+" -profile "+((StructureDefinition) r.getResource()).getUrl()+" [resource-to-validate]"+
              "</code></li></ul>\r\n");
    } else {
    }
    return b.toString();
  }

  private void genWrapper(FetchedFile ff, FetchedResource r, String template, String outputName, Set<String> outputTracker, Map<String, String> vars, String format, String extension, boolean recordPath, String lang, RenderingContext lrc) throws FileNotFoundException, IOException, FHIRException {
    if (template != null && !template.isEmpty()) {
      boolean existsAsPage = false;
      if (ff != null) {
        String fn = pf.igpkp.getLinkFor(r, true);
        for (String pagesDir: pf.pagesDirs) {
          if (pf.altMap.containsKey("page/"+Utilities.path(pagesDir, fn))) {
            existsAsPage = true;
            break;
          }
        }
        if (!existsAsPage && !pf.prePagesDirs.isEmpty()) {
          for (String prePagesDir : pf.prePagesDirs) {
            if (pf.altMap.containsKey("page/"+Utilities.path(prePagesDir, fn))) {
              existsAsPage = true;
              break;
            }
          }
        }
      }
      if (!existsAsPage) {
        if (isNewML() && lang == null) {
          genWrapperRedirect(ff, r, outputName, outputTracker, vars, format, extension, recordPath);
        } else {
          genWrapperInner(ff, r, template, outputName, outputTracker, vars, format, extension, recordPath, lang);
        }
      }
    }
  }

  private void genWrapperRedirect(FetchedFile ff, FetchedResource r, String outputName, Set<String> outputTracker, Map<String, String> vars, String format, String extension, boolean recordPath) throws FileNotFoundException, IOException, FHIRException {
    outputName = determineOutputName(outputName, r, vars, format, extension);
    if (!outputName.contains("#")) {
      String path = Utilities.path(pf.tempDir, outputName);
      if (recordPath)
        r.setPath(outputName);
      checkMakeFile(makeLangRedirect(outputName), path, outputTracker);
    }
  }

  private void genWrapperInner(FetchedFile ff, FetchedResource r, String template, String outputName, Set<String> outputTracker, Map<String, String> vars, String format, String extension, boolean recordPath, String lang) throws FileNotFoundException, IOException, FHIRException {

    template = pf.fetcher.openAsString(Utilities.path(pf.fetcher.pathForFile(settings.getConfigFile()), template));
    if (vars == null) {
      vars = new HashMap<String, String>();
    }
    if (lang != null) {
      vars.put("lang", lang);
    }
    vars.put("langsuffix", lang==null? "" : ("-" + lang));

    template = pf.igpkp.doReplacements(template, r, vars, format);

    outputName = determineOutputName(outputName, r, vars, format, extension);
    if (!outputName.contains("#")) {
      String path = lang == null ? Utilities.path(pf.tempDir, outputName) : Utilities.path(pf.tempDir, lang, outputName);
      if (recordPath)
        r.setPath(outputName);
      checkMakeFile(FileUtilities.stringToBytes(template), path, outputTracker);
    }
  }

  private void genWrapperContained(FetchedFile ff, FetchedResource r, Resource res, String template, String outputName, Set<String> outputTracker, Map<String, String> vars, String format, String extension, String prefixForContained, String lang) throws FileNotFoundException, IOException, FHIRException {
    if (template != null && !template.isEmpty()) {
      if (vars == null) {
        vars = new HashMap<String, String>();
      }
      if (lang != null) {
        vars.put("lang", lang);
      }
      vars.put("langsuffix", lang==null? "" : ("-" + lang));
      boolean existsAsPage = false;
      if (ff != null) {
        String fn = pf.igpkp.getLinkFor(r, true, res);
        for (String pagesDir: pf.pagesDirs) {
          if (pf.altMap.containsKey("page/"+Utilities.path(pagesDir, fn))) {
            existsAsPage = true;
            break;
          }
        }
        if (!existsAsPage && !pf.prePagesDirs.isEmpty()) {
          for (String prePagesDir : pf.prePagesDirs) {
            if (pf.altMap.containsKey("page/"+Utilities.path(prePagesDir, fn))) {
              existsAsPage = true;
              break;
            }
          }
        }
      }
      if (!existsAsPage) {
        outputName = determineOutputName(outputName, r, res, vars, format, extension, prefixForContained);
        if (isNewML() && lang == null) {
          genWrapperRedirect(ff, r, outputName, outputTracker, vars, format, extension, true);
        } else {
          template = pf.fetcher.openAsString(Utilities.path(pf.fetcher.pathForFile(settings.getConfigFile()), template));
          template = pf.igpkp.doReplacements(template, r, res, vars, format, prefixForContained);

          if (!outputName.contains("#")) {
            String path = lang == null ? Utilities.path(pf.tempDir, outputName) : Utilities.path(pf.tempDir, lang, outputName);
            checkMakeFile(FileUtilities.stringToBytes(template), path, outputTracker);
          }
        }
      }
    }
  }

  private String determineOutputName(String outputName, FetchedResource r, Map<String, String> vars, String format, String extension) throws FHIRException {
    if (outputName == null)
      outputName = "{{[type]}}-{{[id]}}"+(extension.equals("")? "":"-"+extension)+(format==null? "": ".{{[fmt]}}")+".html";
    if (outputName.contains("{{["))
      outputName = pf.igpkp.doReplacements(outputName, r, vars, format);
    return outputName;
  }

  private String determineOutputName(String outputName, FetchedResource r, Resource res, Map<String, String> vars, String format, String extension, String prefixForContained) throws FHIRException {
    if (outputName == null)
      outputName = "{{[type]}}-{{[id]}}"+(extension.equals("")? "":"-"+extension)+(format==null? "": ".{{[fmt]}}")+".html";
    if (outputName.contains("{{["))
      outputName = pf.igpkp.doReplacements(outputName, r, res, vars, format, prefixForContained);
    return outputName;
  }

  /**
   * Generate:
   *   summary
   *   content as html
   *   xref
   * @throws org.hl7.fhir.exceptions.FHIRException
   * @throws Exception
   */
  private void generateOutputsCodeSystem(FetchedFile f, FetchedResource fr, CodeSystem cs, Map<String, String> vars, String prefixForContainer, RenderingContext lrc, String lang) throws Exception {
    CodeSystemRenderer csr = new CodeSystemRenderer(this.pf.context, this.pf.specPath, cs, this.pf.igpkp, this.pf.specMaps, pageTargets(), this.pf.markdownEngine, this.pf.packge, lrc, this.pf.versionToAnnotate, this.pf.relatedIGs);
    csr.setFileList(this.pf.fileList);
    if (wantGen(fr, "summary")) {
      long start = System.currentTimeMillis();
      fragment("CodeSystem-"+prefixForContainer+cs.getId()+"-summary", csr.summaryTable(fr, wantGen(fr, "xml"), wantGen(fr, "json"), wantGen(fr, "ttl"), this.pf.igpkp.summaryRows()), f.getOutputNames(), fr, vars, null, start, "summary", "CodeSystem", lang);
    }
    if (wantGen(fr, "summary-table")) {
      long start = System.currentTimeMillis();
      fragment("CodeSystem-"+prefixForContainer+cs.getId()+"-summary-table", csr.summaryTable(fr, wantGen(fr, "xml"), wantGen(fr, "json"), wantGen(fr, "ttl"), this.pf.igpkp.summaryRows()), f.getOutputNames(), fr, vars, null, start, "summary-table", "CodeSystem", lang);
    }
    if (wantGen(fr, "content")) {
      long start = System.currentTimeMillis();
      fragment("CodeSystem-"+prefixForContainer+cs.getId()+"-content", csr.content(this.pf.otherFilesRun), f.getOutputNames(), fr, vars, null, start, "content", "CodeSystem", lang);
    }
    if (wantGen(fr, "xref")) {
      long start = System.currentTimeMillis();
      fragment("CodeSystem-"+prefixForContainer+cs.getId()+"-xref", csr.xref(), f.getOutputNames(), fr, vars, null, start, "xref", "CodeSystem", lang);
    }
    if (wantGen(fr, "nsinfo")) {
      long start = System.currentTimeMillis();
      fragment("CodeSystem-"+prefixForContainer+cs.getId()+"-nsinfo", csr.nsInfo(), f.getOutputNames(), fr, vars, null, start, "nsinfo", "CodeSystem", lang);
    }
    if (wantGen(fr, "changes")) {
      long start = System.currentTimeMillis();
      fragment("CodeSystem-"+prefixForContainer+cs.getId()+"-changes", csr.changeSummary(), f.getOutputNames(), fr, vars, null, start, "changes", "CodeSystem", lang);
    }

    if (wantGen(fr, "xlsx")) {
      CodeSystemSpreadsheetGenerator vsg = new CodeSystemSpreadsheetGenerator(this.pf.context);
      if (vsg.canGenerate(cs)) {
        String path = Utilities.path(this.pf.tempDir, "CodeSystem-"+prefixForContainer + cs.getId()+".xlsx");
        f.getOutputNames().add(path);
        vsg.renderCodeSystem(cs);
        vsg.finish(new FileOutputStream(path));
      }
    }
  }

  /**
   * Genrate:
   *   summary
   *   Content logical definition
   *   cross-reference
   *
   * and save the expansion as html. todo: should we save it as a resource too? at this time, no: it's not safe to do that; encourages abuse
   * @param vs
   * @throws org.hl7.fhir.exceptions.FHIRException
   * @throws Exception
   */
  private void generateOutputsValueSet(FetchedFile f, FetchedResource r, ValueSet vs, Map<String, String> vars, String prefixForContainer, DBBuilder db, RenderingContext lrc, String lang) throws Exception {
    ValueSetRenderer vsr = new ValueSetRenderer(this.pf.context, this.pf.specPath, vs, this.pf.igpkp, this.pf.specMaps, pageTargets(), this.pf.markdownEngine, this.pf.packge, lrc, this.pf.versionToAnnotate, this.pf.relatedIGs);
    vsr.setFileList(this.pf.fileList);
    if (wantGen(r, "summary")) {
      long start = System.currentTimeMillis();
      fragment("ValueSet-"+prefixForContainer+vs.getId()+"-summary", vsr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), this.pf.igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary", "ValueSet", lang);
    }
    if (wantGen(r, "summary-table")) {
      long start = System.currentTimeMillis();
      fragment("ValueSet-"+prefixForContainer+vs.getId()+"-summary-table", vsr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), this.pf.igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary-table", "ValueSet", lang);
    }
    if (wantGen(r, "cld")) {
      long start = System.currentTimeMillis();
      try {
        fragment("ValueSet-"+prefixForContainer+vs.getId()+"-cld", vsr.cld(this.pf.otherFilesRun), f.getOutputNames(), r, vars, null, start, "cld", "ValueSet", lang);
      } catch (Exception e) {
        fragmentError(vs.getId()+"-cld", e.getMessage(), null, f.getOutputNames(), start, "cld", "ValueSet", lang);
      }
    }
    if (wantGen(r, "xref")) {
      long start = System.currentTimeMillis();
      fragment("ValueSet-"+prefixForContainer+vs.getId()+"-xref", vsr.xref(), f.getOutputNames(), r, vars, null, start, "xref", "ValueSet", lang);
    }
    if (wantGen(r, "changes")) {
      long start = System.currentTimeMillis();
      fragment("ValueSet-"+prefixForContainer+vs.getId()+"-changes", vsr.changeSummary(), f.getOutputNames(), r, vars, null, start, "changes", "ValueSet", lang);
    }
    if (wantGen(r, "expansion")) {
      long start = System.currentTimeMillis();
      if (vs.getStatus() == Enumerations.PublicationStatus.RETIRED) {
        String html = "<p style=\"color: maroon\">Expansions are not generated for retired value sets</p>";

        fragment("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", html, f.getOutputNames(), r, vars, null, start, "expansion", "ValueSet", lang);
      } else {
        ValueSetExpansionOutcome exp = this.pf.context.expandVS(ExpansionOptions.cacheNoHeirarchy().withLanguage(lrc.getLocale().getLanguage()).withIncompleteOk(true), vs);

        if (db != null) {
          db.recordExpansion(vs, exp);
        }
        if (exp.getValueset() != null) {
          if (pf.savingExpansions) {
            this.pf.expansions.add(exp.getValueset());
          }
          RenderingContext elrc = lrc.withUniqueLocalPrefix("x").withMode(RenderingContext.ResourceRendererMode.END_USER);
          exp.getValueset().setCompose(null);
          exp.getValueset().setText(null);
          RendererFactory.factory(exp.getValueset(), elrc).renderResource(ResourceWrapper.forResource(elrc, exp.getValueset()));
          String html = new XhtmlComposer(XhtmlComposer.XML).compose(exp.getValueset().getText().getDiv());
          fragment("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", html, f.getOutputNames(), r, vars, null, start, "expansion", "ValueSet", lang);
          elrc = elrc.withOids(true);
          XhtmlNode node = RendererFactory.factory(exp.getValueset(), elrc).buildNarrative(ResourceWrapper.forResource(elrc, exp.getValueset()));
          html = new XhtmlComposer(XhtmlComposer.XML).compose(node);
          fragment("ValueSet-"+prefixForContainer+vs.getId()+"-expansion-oids", html, f.getOutputNames(), r, vars, null, start, "expansion", "ValueSet", lang);
          if (ValueSetUtilities.isIncompleteExpansion(exp.getValueset())) {
            f.getErrors().add(new ValidationMessage(ValidationMessage.Source.TerminologyEngine, ValidationMessage.IssueType.INFORMATIONAL, "ValueSet.where(id = '"+vs.getId()+"')", "The value set expansion is too large, and only a subset has been displayed", ValidationMessage.IssueSeverity.INFORMATION).setTxLink(exp.getTxLink()));
          }
        } else {
          if (exp.getError() != null) {
            if (exp.getError().contains("Unable to provide support")) {
              if (exp.getError().contains("known versions")) {
                fragmentErrorHtml("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", "No Expansion for this valueset (Unsupported Code System Version<!-- "+Utilities.escapeXml(exp.getAllErrors().toString())+" -->)", "Publication Tooling Error: "+Utilities.escapeXml(exp.getAllErrors().toString()), f.getOutputNames(), start, "expansion", "ValueSet", lang);
              } else {
                fragmentErrorHtml("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", "No Expansion for this valueset (Unknown Code System<!-- "+Utilities.escapeXml(exp.getAllErrors().toString())+" -->)", "Publication Tooling Error: "+Utilities.escapeXml(exp.getAllErrors().toString()), f.getOutputNames(), start, "expansion", "ValueSet", lang);
              }
              f.getErrors().add(new ValidationMessage(ValidationMessage.Source.TerminologyEngine, ValidationMessage.IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", exp.getError(), ValidationMessage.IssueSeverity.WARNING).setTxLink(exp.getTxLink()));
              r.getErrors().add(new ValidationMessage(ValidationMessage.Source.TerminologyEngine, ValidationMessage.IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", exp.getError(), ValidationMessage.IssueSeverity.WARNING).setTxLink(exp.getTxLink()));
            } else if (exp.getError().contains("grammar") || exp.getError().contains("enumerated") ) {
              fragmentErrorHtml("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", "This value set cannot be expanded because of the way it is defined - it has an infinite number of members<!-- "+Utilities.escapeXml(exp.getAllErrors().toString())+" -->", "Publication Tooling Error: "+Utilities.escapeXml(exp.getAllErrors().toString()), f.getOutputNames(), start, "expansion", "ValueSet", lang);
              f.getErrors().add(new ValidationMessage(ValidationMessage.Source.TerminologyEngine, ValidationMessage.IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", exp.getError(), ValidationMessage.IssueSeverity.ERROR).setTxLink(exp.getTxLink()));
            } else if (exp.getError().contains("too many") ) {
              fragmentErrorHtml("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", "This value set cannot be expanded because the terminology server(s) deemed it too costly to do so<!-- "+Utilities.escapeXml(exp.getAllErrors().toString())+" -->", "Publication Tooling Error: "+Utilities.escapeXml(exp.getAllErrors().toString()), f.getOutputNames(), start, "expansion", "ValueSet", lang);
              f.getErrors().add(new ValidationMessage(ValidationMessage.Source.TerminologyEngine, ValidationMessage.IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", exp.getError(), ValidationMessage.IssueSeverity.ERROR).setTxLink(exp.getTxLink()));
            } else {
              fragmentErrorHtml("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", "No Expansion for this valueset (not supported by Publication Tooling<!-- "+Utilities.escapeXml(exp.getAllErrors().toString())+" -->)", "Publication Tooling Error: "+Utilities.escapeXml(exp.getAllErrors().toString()), f.getOutputNames(), start, "expansion", "ValueSet", lang);
              f.getErrors().add(new ValidationMessage(ValidationMessage.Source.TerminologyEngine, ValidationMessage.IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", exp.getError(), ValidationMessage.IssueSeverity.ERROR).setTxLink(exp.getTxLink()));
              r.getErrors().add(new ValidationMessage(ValidationMessage.Source.TerminologyEngine, ValidationMessage.IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", exp.getError(), ValidationMessage.IssueSeverity.ERROR).setTxLink(exp.getTxLink()));
            }
          } else {
            fragmentError("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", "No Expansion for this valueset (not supported by Publication Tooling)", "Unknown Error", f.getOutputNames(), start, "expansion", "ValueSet", lang);
            f.getErrors().add(new ValidationMessage(ValidationMessage.Source.TerminologyEngine, ValidationMessage.IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", "Unknown Error expanding ValueSet", ValidationMessage.IssueSeverity.ERROR).setTxLink(exp.getTxLink()));
            r.getErrors().add(new ValidationMessage(ValidationMessage.Source.TerminologyEngine, ValidationMessage.IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", "Unknown Error expanding ValueSet", ValidationMessage.IssueSeverity.ERROR).setTxLink(exp.getTxLink()));
          }
        }
      }
    }
    if (wantGen(r, "xlsx")) {
      ValueSetSpreadsheetGenerator vsg = new ValueSetSpreadsheetGenerator(this.pf.context);
      if (vsg.canGenerate(vs)) {
        String path = Utilities.path(this.pf.tempDir, "ValueSet-"+prefixForContainer + r.getId()+".xlsx");
        f.getOutputNames().add(path);
        vsg.renderValueSet(vs);
        vsg.finish(new FileOutputStream(path));
      }
    }

  }

  private void fragmentError(String name, String error, String overlay, Set<String> outputTracker, long start, String code, String context, String lang) throws IOException, FHIRException {
    if (Utilities.noString(overlay)) {
      fragment(name, "<p><span style=\"color: maroon; font-weight: bold\">"+Utilities.escapeXml(error)+"</span></p>\r\n", outputTracker, start, code, context, lang);
    } else {
      fragment(name, "<p><span style=\"color: maroon; font-weight: bold\" title=\""+Utilities.escapeXml(overlay)+"\">"+Utilities.escapeXml(error)+"</span></p>\r\n", outputTracker, start, code, context, lang);
    }
  }

  private void fragmentErrorHtml(String name, String error, String overlay, Set<String> outputTracker, long start, String code, String context, String lang) throws IOException, FHIRException {
    if (Utilities.noString(overlay)) {
      fragment(name, "<p style=\"border: maroon 1px solid; background-color: #FFCCCC; font-weight: bold; padding: 8px\" title=\""+Utilities.escapeXml(error)+"\">"+error+"</p>\r\n", outputTracker, start, code, context, lang);
    } else {
      fragment(name, "<p style=\"border: maroon 1px solid; background-color: #FFCCCC; font-weight: bold; padding: 8px\" title=\""+Utilities.escapeXml(error)+"\">"+error+"</p>\r\n", outputTracker, start, code, context, lang);
    }
  }


  /**
   * Generate:
   *   summary
   *   content as html
   *   xref
   * @throws IOException
   */
  private void generateOutputsConceptMap(FetchedFile f, FetchedResource r, ConceptMap cm, Map<String, String> vars, String prefixForContainer, RenderingContext lrc, String lang) throws IOException, FHIRException {
    if (wantGen(r, "summary")) {
      long start = System.currentTimeMillis();
      fragmentError("ConceptMap-"+prefixForContainer+cm.getId()+"-summary", "yet to be done: concept map summary", null, f.getOutputNames(), start, "summary", "ConceptMap", lang);
    }
    if (wantGen(r, "summary-table")) {
      long start = System.currentTimeMillis();
      fragmentError("ConceptMap-"+prefixForContainer+cm.getId()+"-summary-table", "yet to be done: concept map summary", null, f.getOutputNames(), start, "summary-table", "ConceptMap", lang);
    }
    if (wantGen(r, "content")) {
      long start = System.currentTimeMillis();
      fragmentError("ConceptMap-"+prefixForContainer+cm.getId()+"-content", "yet to be done: table presentation of the concept map", null, f.getOutputNames(), start, "content", "ConceptMap", lang);
    }
    if (wantGen(r, "xref")) {
      long start = System.currentTimeMillis();
      fragmentError("ConceptMap-"+prefixForContainer+cm.getId()+"-xref", "yet to be done: list of all places where concept map is used", null, f.getOutputNames(), start, "xref", "ConceptMap", lang);
    }
    MappingSheetParser p = new MappingSheetParser();
    if (wantGen(r, "sheet") && p.isSheet(cm)) {
      long start = System.currentTimeMillis();
      fragment("ConceptMap-"+prefixForContainer+cm.getId()+"-sheet", p.genSheet(cm), f.getOutputNames(), r, vars, null, start, "sheet", "ConceptMap", lang);
    }
    ConceptMapSpreadsheetGenerator cmg = new ConceptMapSpreadsheetGenerator(this.pf.context);
    if (wantGen(r, "xlsx") && cmg.canGenerate(cm)) {
      String path = Utilities.path(this.pf.tempDir, prefixForContainer + r.getId()+".xlsx");
      f.getOutputNames().add(path);
      cmg.renderConceptMap(cm);
      cmg.finish(new FileOutputStream(path));
    }
  }

  private void generateOutputsImplementationGuide(FetchedFile f, FetchedResource r, ImplementationGuide ig, Map<String, String> vars, String prefixForContainer, RenderingContext lrc, String lang) throws IOException, FHIRException {
  }

  private String renderExpansionParameters() throws IOException {
    DataRenderer resourceRenderer = new DataRenderer(pf.rc);
    Parameters p = pf.context.getExpansionParameters();

    boolean hasInterestingParams = false;
    if (p != null) {
      for (Parameters.ParametersParameterComponent pp : p.getParameter()) {
        hasInterestingParams = hasInterestingParams || !Utilities.existsInList(pp.getName(), "x-system-cache-id", "defaultDisplayLanguage");
      }
    }
    if (!hasInterestingParams) {
      return "";
    } else {
      XhtmlNode x = new XhtmlNode(NodeType.Element, "div");
      XhtmlNode tbl = x.table("grid");
      XhtmlNode tr = tbl.tr();
      tr.th().tx("Parameter");
      tr.th().tx("Value");
      for (Parameters.ParametersParameterComponent pp : p.getParameter()) {
        if (!Utilities.existsInList(pp.getName(), "x-system-cache-id", "defaultDisplayLanguage")) {
          tr = tbl.tr();
          tr.td().tx(pp.getName());
          if (Utilities.existsInList(pp.getName(), "exclude-system", "system-version", "check-system-version", "force-system-version", "default-valueset-version", "check-valueset-version", "force-valueset-version")) {
            String canonical = pp.getValue().primitiveValue();
            if (canonical.contains("|")) {
              String system = canonical.substring(0, canonical.indexOf("|"));
              String version = canonical.substring(canonical.indexOf("|")+1);
              tr.td().tx(resourceRenderer.displayCodeSource(system, version));
            } else {
              resourceRenderer.renderDataType(new Renderer.RenderingStatus(), tr.td(), ResourceWrapper.forType(pf.rc.getContextUtilities(), pp.getValue()));
            }
          } else {
            resourceRenderer.renderDataType(new Renderer.RenderingStatus(), tr.td(), ResourceWrapper.forType(pf.rc.getContextUtilities(), pp.getValue()));
          }
        }
      }
      return new XhtmlComposer(true, true).compose(x.getChildNodes());
    }
  }

  private void generateOutputsCapabilityStatement(FetchedFile f, FetchedResource r, CapabilityStatement cpbs, Map<String, String> vars, String prefixForContainer, RenderingContext lrc, String lang) throws Exception {
    if (wantGen(r, "swagger") || wantGen(r, "openapi")) {
      String lp = isNewML() && lang != null && !lang.equals(this.pf.defaultTranslationLang) ? "-"+lang : "";
      org.hl7.fhir.r5.openapi.Writer oa = null;
      if (this.pf.openApiTemplate != null)
        oa = new org.hl7.fhir.r5.openapi.Writer(new FileOutputStream(Utilities.path(this.pf.tempDir, cpbs.getId()+ lp+".openapi.json")), new FileInputStream(Utilities.path(FileUtilities.getDirectoryForFile(this.settings.getConfigFile()), this.pf.openApiTemplate)));
      else
        oa = new Writer(new FileOutputStream(Utilities.path(this.pf.tempDir, cpbs.getId()+ lp+".openapi.json")));
      String lic = license();
      String displ = this.pf.context.validateCode(new ValidationOptions(FhirPublication.R5, "en-US"), new Coding("http://hl7.org/fhir/spdx-license",  lic, null), null).getDisplay();
      new OpenApiGenerator(this.pf.context, cpbs, oa).generate(displ, "http://spdx.org/licenses/"+lic+".html");
      oa.commit();
      if (lang == null) {
        this.pf.otherFilesRun.add(Utilities.path(this.pf.tempDir, cpbs.getId()+lp+ ".openapi.json"));
        addFileToNpm(NPMPackageGenerator.Category.OPENAPI, cpbs.getId()+ lp+".openapi.json", FileUtilities.fileToBytes(Utilities.path(this.pf.tempDir, cpbs.getId()+lp+".openapi.json")));
      }
    }
  }

  private void generateSpreadsheetsStructureDefinition(FetchedFile f, FetchedResource r, StructureDefinition sd, Map<String, String> vars, boolean regen, String prefixForContainer) throws Exception {
    String sdPrefix = this.pf.newIg ? "StructureDefinition-" : "";
    if (wantGen(r, "csv")) {
      String path = Utilities.path(this.pf.tempDir, sdPrefix + r.getId()+".csv");
      f.getOutputNames().add(path);
      ProfileUtilities pu = new ProfileUtilities(this.pf.context, this.pf.errors, this.pf.igpkp);
      pu.generateCsv(new FileOutputStream(path), sd, true);
      if (this.pf.allProfilesCsv == null) {
        this.pf.allProfilesCsv = new CSVWriter(new FileOutputStream(Utilities.path(this.pf.tempDir, "all-profiles.csv")), true);
        this.pf.otherFilesRun.add(Utilities.path(this.pf.tempDir, "all-profiles.csv"));

      }
      pu.addToCSV(this.pf.allProfilesCsv, sd);
    }
    if (wantGen(r, "xlsx")) {
      lapsed(null);
      String path = Utilities.path(this.pf.tempDir, sdPrefix + r.getId()+".xlsx");
      f.getOutputNames().add(path);
      StructureDefinitionSpreadsheetGenerator sdg = new StructureDefinitionSpreadsheetGenerator(this.pf.context, true, anyMustSupport(sd));
      sdg.renderStructureDefinition(sd, false);
      sdg.finish(new FileOutputStream(path));
      lapsed("xslx");
      if (this.pf.allProfilesXlsx == null) {
        this.pf.allProfilesXlsx = new StructureDefinitionSpreadsheetGenerator(this.pf.context, true, false);
      }
      this.pf.allProfilesXlsx.renderStructureDefinition(sd, true);
      lapsed("all-xslx");
    }

    if (!regen && sd.getDerivation() == StructureDefinition.TypeDerivationRule.CONSTRAINT && wantGen(r, "sch")) {
      String path = Utilities.path(this.pf.tempDir, sdPrefix + r.getId()+".sch");
      f.getOutputNames().add(path);
      new ProfileUtilities(this.pf.context, this.pf.errors, this.pf.igpkp).generateSchematrons(new FileOutputStream(path), sd);
      addFileToNpm(NPMPackageGenerator.Category.SCHEMATRON, sdPrefix + r.getId()+".sch", FileUtilities.fileToBytes(Utilities.path(this.pf.tempDir, sdPrefix + r.getId()+".sch")));
    }
//    if (wantGen(r, "sch"))
//      start = System.currentTimeMillis();
//    fragmentError("StructureDefinition-"+prefixForContainer+sd.getId()+"-sch", "yet to be done: schematron as html", null, f.getOutputNames(), start, "sch", "StructureDefinition");
  }

  private void generateOutputsStructureDefinition(FetchedFile f, FetchedResource r, StructureDefinition sd, Map<String, String> vars, boolean regen, String prefixForContainer, RenderingContext lrc, String lang) throws Exception {
    // todo : generate shex itself
    if (wantGen(r, "shex")) {
      long start = System.currentTimeMillis();
      fragmentError("StructureDefinition-"+prefixForContainer+sd.getId()+"-shex", "yet to be done: shex as html", null, f.getOutputNames(), start, "shex", "StructureDefinition", lang);
    }

// todo : generate json schema itself. JSON Schema generator
    //    if (wantGen(r, ".schema.json")) {
    //      String path = Utilities.path(tempDir, r.getId()+".sch");
    //      f.getOutputNames().add(path);
    //      new ProfileUtilities(context, errors, igpkp).generateSchematrons(new FileOutputStream(path), sd);
    //    }
    if (wantGen(r, "json-schema")) {
      long start = System.currentTimeMillis();
      fragmentError("StructureDefinition-"+prefixForContainer+sd.getId()+"-json-schema", "yet to be done: json schema as html", null, f.getOutputNames(), start, "json-schema", "StructureDefinition", lang);
    }

    StructureDefinitionRenderer sdr = new StructureDefinitionRenderer(this.pf.context, this.pf.sourceIg.getPackageId(), checkAppendSlash(this.pf.specPath), sd, Utilities.path(this.pf.tempDir), this.pf.igpkp, this.pf.specMaps, pageTargets(), this.pf.markdownEngine, this.pf.packge, this.pf.fileList, lrc, this.pf.allInvariants, this.pf.sdMapCache, this.pf.specPath, this.pf.versionToAnnotate, this.pf.relatedIGs);
    sdr.setNoXigLink(this.pf.noXigLink);

    if (wantGen(r, "summary")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-summary", sdr.summary(false), f.getOutputNames(), r, vars, null, start, "summary", "StructureDefinition", lang);
    }
    if (wantGen(r, "summary-all")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-summary-all", sdr.summary(true), f.getOutputNames(), r, vars, null, start, "summary", "StructureDefinition", lang);
    }
    if (wantGen(r, "summary-table")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-summary-table", sdr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), this.pf.igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary-table", "StructureDefinition", lang);
    }
    if (wantGen(r, "class-table")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-class-table", sdr.classTable(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.SUMMARY, false), f.getOutputNames(), r, vars, null, start, "class-table", "StructureDefinition", lang);
    }
    if (wantGen(r, "header")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-header", sdr.header(), f.getOutputNames(), r, vars, null, start, "header", "StructureDefinition", lang);
    }
    if (wantGen(r, "uses")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-uses", sdr.uses(), f.getOutputNames(), r, vars, null, start, "uses", "StructureDefinition", lang);
    }
    if (wantGen(r, "ctxts")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-ctxts", sdr.contexts(), f.getOutputNames(), r, vars, null, start, "ctxts", "StructureDefinition", lang);
    }
    if (wantGen(r, "experimental-warning")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-experimental-warning", sdr.experimentalWarning(), f.getOutputNames(), r, vars, null, start, "experimental-warning", "StructureDefinition", lang);
    }

    if (wantGen(r, "eview")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-eview", sdr.eview(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.SUMMARY, false), f.getOutputNames(), r, vars, null, start, "eview", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-eview-all", sdr.eview(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.SUMMARY, true), f.getOutputNames(), r, vars, null, start, "eview", "StructureDefinition", lang);
    }
    if (wantGen(r, "adl")) {
      long start = System.currentTimeMillis();
      String adl = sd.hasUserData(UserDataNames.archetypeSource) ? sdr.adl() : "";
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-adl", adl, f.getOutputNames(), r, vars, null, start, "adl", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-adl-all", adl, f.getOutputNames(), r, vars, null, start, "adl", "StructureDefinition", lang);
    }
    if (wantGen(r, "diff")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-diff", sdr.diff(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.SUMMARY, false), f.getOutputNames(), r, vars, null, start, "diff", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-diff-all", sdr.diff(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.SUMMARY, true), f.getOutputNames(), r, vars, null, start, "diff", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot", sdr.snapshot(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.SUMMARY, false), f.getOutputNames(), r, vars, null, start, "snapshot", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-all", sdr.snapshot(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.SUMMARY, true), f.getOutputNames(), r, vars, null, start, "snapshot", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot-by-key")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-key", sdr.byKey(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.SUMMARY, false), f.getOutputNames(), r, vars, null, start, "snapshot-by-key", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-key-all", sdr.byKey(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.SUMMARY, true), f.getOutputNames(), r, vars, null, start, "snapshot-by-key", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot-by-mustsupport")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-mustsupport", sdr.byMustSupport(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.SUMMARY, false), f.getOutputNames(), r, vars, null, start, "snapshot-by-mustsupport", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-mustsupport-all", sdr.byMustSupport(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.SUMMARY, true), f.getOutputNames(), r, vars, null, start, "snapshot-by-mustsupport", "StructureDefinition", lang);
    }
    if (wantGen(r, "diff-bindings")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-diff-bindings", sdr.diff(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.BINDINGS, false), f.getOutputNames(), r, vars, null, start, "diff-bindings", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-diff-bindings-all", sdr.diff(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.BINDINGS, true), f.getOutputNames(), r, vars, null, start, "diff-bindings", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot-bindings")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-bindings", sdr.snapshot(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.BINDINGS, false), f.getOutputNames(), r, vars, null, start, "snapshot-bindings", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-bindings-all", sdr.snapshot(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.BINDINGS, true), f.getOutputNames(), r, vars, null, start, "snapshot-bindings", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot-by-key-bindings")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-key-bindings", sdr.byKey(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.BINDINGS, false), f.getOutputNames(), r, vars, null, start, "snapshot-by-key-bindings", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-key-bindings-all", sdr.byKey(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.BINDINGS, true), f.getOutputNames(), r, vars, null, start, "snapshot-by-key-bindings", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot-by-mustsupport-bindings")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-mustsupport-bindings", sdr.byMustSupport(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.BINDINGS, false), f.getOutputNames(), r, vars, null, start, "snapshot-by-mustsupport-bindings", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-mustsupport-bindings-all", sdr.byMustSupport(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.BINDINGS, true), f.getOutputNames(), r, vars, null, start, "snapshot-by-mustsupport-bindings", "StructureDefinition", lang);
    }
    if (wantGen(r, "diff-obligations")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-diff-obligations", sdr.diff(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.OBLIGATIONS, false), f.getOutputNames(), r, vars, null, start, "diff-obligations", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-diff-obligations-all", sdr.diff(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.OBLIGATIONS, true), f.getOutputNames(), r, vars, null, start, "diff-obligations", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot-obligations")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-obligations", sdr.snapshot(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.OBLIGATIONS, false), f.getOutputNames(), r, vars, null, start, "snapshot-obligations", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-obligations-all", sdr.snapshot(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.OBLIGATIONS, true), f.getOutputNames(), r, vars, null, start, "snapshot-obligations", "StructureDefinition", lang);
    }
    if (wantGen(r, "obligations")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-obligations", sdr.obligations(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.OBLIGATIONS, false), f.getOutputNames(), r, vars, null, start, "diff-obligations", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-obligations-all", sdr.obligations(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.OBLIGATIONS, true), f.getOutputNames(), r, vars, null, start, "diff-obligations", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot-by-key-obligations")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-key-obligations", sdr.byKey(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.OBLIGATIONS, false), f.getOutputNames(), r, vars, null, start, "snapshot-by-key-obligations", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-key-obligations-all", sdr.byKey(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.OBLIGATIONS, true), f.getOutputNames(), r, vars, null, start, "snapshot-by-key-obligations", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot-by-mustsupport-obligations")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-mustsupport-obligations", sdr.byMustSupport(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.OBLIGATIONS, false), f.getOutputNames(), r, vars, null, start, "snapshot-by-mustsupport-obligations", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-mustsupport-obligations-all", sdr.byMustSupport(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, this.pf.tabbedSnapshots, RenderingContext.StructureDefinitionRendererMode.OBLIGATIONS, true), f.getOutputNames(), r, vars, null, start, "snapshot-by-mustsupport-obligations", "StructureDefinition", lang);
    }
    if (wantGen(r, "expansion")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-expansion", sdr.expansion(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun, "x"), f.getOutputNames(), r, vars, null, start, "expansion", "StructureDefinition", lang);
    }
    if (wantGen(r, "grid")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-grid", sdr.grid(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun), f.getOutputNames(), r, vars, null, start, "grid", "StructureDefinition", lang);
    }
    if (wantGen(r, "pseudo-xml")) {
      long start = System.currentTimeMillis();
      fragmentError("StructureDefinition-"+prefixForContainer+sd.getId()+"-pseudo-xml", "yet to be done: Xml template", null, f.getOutputNames(), start, "pseudo-xml", "StructureDefinition", lang);
    }
    if (wantGen(r, "pseudo-json")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-pseudo-json", sdr.pseudoJson(), f.getOutputNames(), r, vars, null, start, "pseudo-json", "StructureDefinition", lang);
    }
    if (wantGen(r, "pseudo-ttl")) {
      long start = System.currentTimeMillis();
      fragmentError("StructureDefinition-"+prefixForContainer+sd.getId()+"-pseudo-ttl", "yet to be done: Turtle template", null, f.getOutputNames(), start, "pseudo-ttl", "StructureDefinition", lang);
    }
    if (this.pf.generateUml != PublisherUtils.UMLGenerationMode.NONE) {
      long start = System.currentTimeMillis();
      try {
        ClassDiagramRenderer cdr = new ClassDiagramRenderer(Utilities.path(this.pf.rootDir, "input", "diagrams"), Utilities.path(this.pf.rootDir, "temp", "diagrams"), sd.getId(), "uml-", this.pf.rc, lang);
        String src = sd.getDerivation() == StructureDefinition.TypeDerivationRule.SPECIALIZATION ? cdr.buildClassDiagram(sd,this.pf.igpkp.getDefinitionsName(r) ) : cdr.buildConstraintDiagram(sd, this.pf.igpkp.getDefinitionsName(r));
        if (this.pf.generateUml == PublisherUtils.UMLGenerationMode.ALL || cdr.hasSource()) {
          r.setUmlGenerated(true);
        } else {
          src = "";
        }
        fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-uml", src, f.getOutputNames(), r, vars, null, start, "uml", "StructureDefinition", lang);
      } catch (Exception e) {
        e.printStackTrace();
        fragmentError("StructureDefinition-"+prefixForContainer+sd.getId()+"-uml", e.getMessage(), null, f.getOutputNames(), start, "uml", "StructureDefinition", lang);
      }
      try {
        ClassDiagramRenderer cdr = new ClassDiagramRenderer(Utilities.path(this.pf.rootDir, "input", "diagrams"), Utilities.path(this.pf.rootDir, "temp", "diagrams"), sd.getId(), "all-uml-", this.pf.rc, lang);
        String src = sd.getDerivation() == StructureDefinition.TypeDerivationRule.SPECIALIZATION ? cdr.buildClassDiagram(sd, this.pf.igpkp.getDefinitionsName(r)) : cdr.buildConstraintDiagram(sd, this.pf.igpkp.getDefinitionsName(r));
        if (this.pf.generateUml == PublisherUtils.UMLGenerationMode.ALL || cdr.hasSource()) {
          r.setUmlGenerated(true);
        } else {
          src = "";
        }
        fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-uml-all", src, f.getOutputNames(), r, vars, null, start, "uml-all", "StructureDefinition", lang);
      } catch (Exception e) {
        e.printStackTrace();
        fragmentError("StructureDefinition-"+prefixForContainer+sd.getId()+"-uml-all", e.getMessage(), null, f.getOutputNames(), start, "uml-all", "StructureDefinition", lang);
      }
    }
    if (wantGen(r, "tx")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-tx", sdr.tx(this.pf.includeHeadings, false, false), f.getOutputNames(), r, vars, null, start, "tx", "StructureDefinition", lang);
    }
    if (wantGen(r, "tx-must-support")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-tx-must-support", sdr.tx(this.pf.includeHeadings, true, false), f.getOutputNames(), r, vars, null, start, "tx-must-support", "StructureDefinition", lang);
    }
    if (wantGen(r, "tx-key")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-tx-key", sdr.tx(this.pf.includeHeadings, false, true), f.getOutputNames(), r, vars, null, start, "tx-key", "StructureDefinition", lang);
    }
    if (wantGen(r, "tx-diff")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-tx-diff", sdr.txDiff(this.pf.includeHeadings, false), f.getOutputNames(), r, vars, null, start, "tx-diff", "StructureDefinition", lang);
    }
    if (wantGen(r, "tx-diff-must-support")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-tx-diff-must-support", sdr.txDiff(this.pf.includeHeadings, true), f.getOutputNames(), r, vars, null, start, "tx-diff-must-support", "StructureDefinition", lang);
    }
    if (wantGen(r, "inv-diff")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-inv-diff", sdr.invOldMode(this.pf.includeHeadings, StructureDefinitionRenderer.GEN_MODE_DIFF), f.getOutputNames(), r, vars, null, start, "inv-diff", "StructureDefinition", lang);
    }
    if (wantGen(r, "inv-key")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-inv-key", sdr.invOldMode(this.pf.includeHeadings, StructureDefinitionRenderer.GEN_MODE_KEY), f.getOutputNames(), r, vars, null, start, "inv-key", "StructureDefinition", lang);
    }
    if (wantGen(r, "inv")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-inv", sdr.invOldMode(this.pf.includeHeadings, StructureDefinitionRenderer.GEN_MODE_SNAP), f.getOutputNames(), r, vars, null, start, "inv", "StructureDefinition", lang);
    }
    if (wantGen(r, "dict")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-dict", sdr.dict(true, StructureDefinitionRenderer.GEN_MODE_SNAP, StructureDefinitionRenderer.ANCHOR_PREFIX_SNAP), f.getOutputNames(), r, vars, null, start, "dict", "StructureDefinition", lang);
    }
    if (wantGen(r, "dict-diff")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-dict-diff", sdr.dict(true, StructureDefinitionRenderer.GEN_MODE_DIFF, StructureDefinitionRenderer.ANCHOR_PREFIX_DIFF), f.getOutputNames(), r, vars, null, start, "dict-diff", "StructureDefinition", lang);
    }
    if (wantGen(r, "dict-ms")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-dict-ms", sdr.dict(true, StructureDefinitionRenderer.GEN_MODE_MS, StructureDefinitionRenderer.ANCHOR_PREFIX_MS), f.getOutputNames(), r, vars, null, start, "dict-ms", "StructureDefinition", lang);
    }
    if (wantGen(r, "dict-key")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-dict-key", sdr.dict(true, StructureDefinitionRenderer.GEN_MODE_KEY, StructureDefinitionRenderer.ANCHOR_PREFIX_KEY), f.getOutputNames(), r, vars, null, start, "dict-key", "StructureDefinition", lang);
    }
    if (wantGen(r, "dict-active")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-dict-active", sdr.dict(false, StructureDefinitionRenderer.GEN_MODE_SNAP, StructureDefinitionRenderer.ANCHOR_PREFIX_SNAP), f.getOutputNames(), r, vars, null, start, "dict-active", "StructureDefinition", lang);
    }
    if (wantGen(r, "crumbs")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-crumbs", sdr.crumbTrail(), f.getOutputNames(), r, vars, null, start, "crumbs", "StructureDefinition", lang);
    }
    if (wantGen(r, "maps")) {
      long start = System.currentTimeMillis();
         fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-maps", sdr.mappings(this.pf.igpkp.getDefinitionsName(r), this.pf.otherFilesRun), f.getOutputNames(), r, vars, null, start, "maps", "StructureDefinition", lang);
    }
    if (wantGen(r, "xref")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-sd-xref", sdr.references(lang, lrc), f.getOutputNames(), r, vars, null, start, "xref", "StructureDefinition", lang);
    }
    if (wantGen(r, "sd-use-context")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-sd-use-context", sdr.useContext(), f.getOutputNames(), r, vars, null, start, "sd-use-context", "StructureDefinition", lang);
    }
    if (wantGen(r, "search-params")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-search-params", sdr.searchParameters(), f.getOutputNames(), r, vars, null, start, "search-params", "StructureDefinition", lang);
    }
    TemplateRenderer tr = new TemplateRenderer(pf.rc, sd, this.pf.igpkp.getDefinitionsName(r));
    if (wantGen(r, "template-xml") && sd.getKind() != StructureDefinition.StructureDefinitionKind.LOGICAL && sd.getDerivation() == StructureDefinition.TypeDerivationRule.SPECIALIZATION) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-template-xml", tr.generateXml(), f.getOutputNames(), r, vars, null, start, "template-xml", "StructureDefinition", lang);
    }
    if (wantGen(r, "template-json") && sd.getKind() != StructureDefinition.StructureDefinitionKind.LOGICAL && sd.getDerivation() == StructureDefinition.TypeDerivationRule.SPECIALIZATION) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-template-json", tr.generateJson(), f.getOutputNames(), r, vars, null, start, "template-json", "StructureDefinition", lang);
    }
    if (wantGen(r, "template-ttl") && sd.getKind() != StructureDefinition.StructureDefinitionKind.LOGICAL && sd.getDerivation() == StructureDefinition.TypeDerivationRule.SPECIALIZATION) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-template-ttl", tr.generateTtl(), f.getOutputNames(), r, vars, null, start, "template-ttl", "StructureDefinition", lang);
    }
    if (wantGen(r, "changes")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-sd-changes", sdr.changeSummary(), f.getOutputNames(), r, vars, null, start, "changes", "StructureDefinition", lang);
    }
    long start = System.currentTimeMillis();
    fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-typename", sdr.typeName(lang, lrc), f.getOutputNames(), r, vars, null, start, "-typename", "StructureDefinition", lang);
    if (sd.getDerivation() == StructureDefinition.TypeDerivationRule.CONSTRAINT && wantGen(r, "span")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-span", sdr.span(true, this.pf.igpkp.getCanonical(), this.pf.otherFilesRun, "sp"), f.getOutputNames(), r, vars, null, start, "span", "StructureDefinition", lang);
    }
    if (sd.getDerivation() == StructureDefinition.TypeDerivationRule.CONSTRAINT && wantGen(r, "spanall")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-spanall", sdr.span(true, this.pf.igpkp.getCanonical(), this.pf.otherFilesRun, "spall"), f.getOutputNames(), r, vars, null, start, "spanall", "StructureDefinition", lang);
    }

    if (wantGen(r, "example-list")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-example-list-"+prefixForContainer+sd.getId(), sdr.exampleList(this.pf.fileList, true), f.getOutputNames(), r, vars, null, start, "example-list", "StructureDefinition", lang);
    }
    if (wantGen(r, "example-table")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-example-table-"+prefixForContainer+sd.getId(), sdr.exampleTable(this.pf.fileList, true), f.getOutputNames(), r, vars, null, start, "example-table", "StructureDefinition", lang);
    }
    if (wantGen(r, "example-list-all")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-example-list-all-"+prefixForContainer+sd.getId(), sdr.exampleList(this.pf.fileList, false), f.getOutputNames(), r, vars, null, start, "example-list-all", "StructureDefinition", lang);
    }
    if (wantGen(r, "example-table-all")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-example-table-all-"+prefixForContainer+sd.getId(), sdr.exampleTable(this.pf.fileList, false), f.getOutputNames(), r, vars, null, start, "example-table-all", "StructureDefinition", lang);
    }

    if (wantGen(r, "testplan-list")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-testplan-list-"+prefixForContainer+sd.getId(), sdr.testplanList(this.pf.fileList), f.getOutputNames(), r, vars, null, start, "testplan-list", "StructureDefinition", lang);
    }
    if (wantGen(r, "testplan-table")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-testplan-table-"+prefixForContainer+sd.getId(), sdr.testplanTable(this.pf.fileList), f.getOutputNames(), r, vars, null, start, "testplan-table", "StructureDefinition", lang);
    }

    if (wantGen(r, "testscript-list")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-testscript-list-"+prefixForContainer+sd.getId(), sdr.testscriptList(this.pf.fileList), f.getOutputNames(), r, vars, null, start, "testscript-list", "StructureDefinition", lang);
    }
    if (wantGen(r, "testscript-table")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-testscript-table-"+prefixForContainer+sd.getId(), sdr.testscriptTable(this.pf.fileList), f.getOutputNames(), r, vars, null, start, "testscript-table", "StructureDefinition", lang);
    }

    if (wantGen(r, "other-versions")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-other-versions", sdr.otherVersions(f.getOutputNames(), r), f.getOutputNames(), r, vars, null, start, "other-versions", "StructureDefinition", lang);
    }

    for (Extension ext : sd.getExtensionsByUrl(ExtensionDefinitions.EXT_SD_IMPOSE_PROFILE)) {
      StructureDefinition sdi = this.pf.context.fetchResource(StructureDefinition.class, ext.getValue().primitiveValue());
      if (sdi != null) {
        start = System.currentTimeMillis();
        String cid = sdi.getUserString(UserDataNames.pub_imposes_compare_id);
        fragment("StructureDefinition-imposes-"+prefixForContainer+sd.getId()+"-"+cid, sdr.compareImposes(sdi), f.getOutputNames(), r, vars, null, start, "imposes", "StructureDefinition", lang);
      }
    }

    if (wantGen(r, "java")) {
      ConstraintJavaGenerator jg = new ConstraintJavaGenerator(this.pf.context, this.pf.version, this.pf.tempDir, this.pf.sourceIg.getUrl());
      try {
        f.getOutputNames().add(jg.generate(sd));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private boolean wantGen(FetchedResource r, String code) {
    if (pf.wantGenParams.containsKey(code)) {
      Boolean genParam = pf.wantGenParams.get(code);
      if (!genParam.booleanValue())
        return false;
    }
    return pf.igpkp.wantGen(r, code) && pf.template.wantGenerateFragment(r.fhirType(), code);
  }

  private void lapsed(String msg) {
    long now = System.currentTimeMillis();
    long d = now - pf.last;
    pf.last = now;
  }

  private boolean anyMustSupport(StructureDefinition sd) {
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      if (ed.getMustSupport()) {
        return true;
      }
    }
    return false;
  }

  private void generateOutputsStructureMap(FetchedFile f, FetchedResource r, StructureMap map, Map<String,String> vars, String prefixForContainer, RenderingContext lrc, String lang) throws Exception {
    StructureMapRenderer smr = new StructureMapRenderer(this.pf.context, checkAppendSlash(this.pf.specPath), map, Utilities.path(this.pf.tempDir), this.pf.igpkp, this.pf.specMaps, pageTargets(), this.pf.markdownEngine, this.pf.packge, lrc, this.pf.versionToAnnotate, this.pf.relatedIGs);
    if (wantGen(r, "summary")) {
      long start = System.currentTimeMillis();
      fragment("StructureMap-"+prefixForContainer+map.getId()+"-summary", smr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), this.pf.igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary", "StructureMap", lang);
    }
    if (wantGen(r, "summary-table")) {
      long start = System.currentTimeMillis();
      fragment("StructureMap-"+prefixForContainer+map.getId()+"-summary-table", smr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), this.pf.igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary-table", "StructureMap", lang);
    }
    if (wantGen(r, "content")) {
      long start = System.currentTimeMillis();
      fragment("StructureMap-"+prefixForContainer+map.getId()+"-content", smr.content(), f.getOutputNames(), r, vars, null, start, "content", "StructureMap", lang);
    }
    if (wantGen(r, "profiles")) {
      long start = System.currentTimeMillis();
      fragment("StructureMap-"+prefixForContainer+map.getId()+"-profiles", smr.profiles(), f.getOutputNames(), r, vars, null, start, "profiles", "StructureMap", lang);
    }
    if (wantGen(r, "script")) {
      long start = System.currentTimeMillis();
      fragment("StructureMap-"+prefixForContainer+map.getId()+"-script", smr.script(false), f.getOutputNames(), r, vars, null, start, "script", "StructureMap", lang);
    }
    if (wantGen(r, "script-plain")) {
      long start = System.currentTimeMillis();
      fragment("StructureMap-"+prefixForContainer+map.getId()+"-script-plain", smr.script(true), f.getOutputNames(), r, vars, null, start, "script-plain", "StructureMap", lang);
    }
    // to generate:
    // map file
    // summary table
    // profile index

  }

  private void generateOutputsCanonical(FetchedFile f, FetchedResource r, CanonicalResource cr, Map<String,String> vars, String prefixForContainer, RenderingContext lrc, String lang) throws Exception {
    CanonicalRenderer smr = new CanonicalRenderer(this.pf.context, checkAppendSlash(this.pf.specPath), cr, Utilities.path(this.pf.tempDir), this.pf.igpkp, this.pf.specMaps, pageTargets(), this.pf.markdownEngine, this.pf.packge, lrc, this.pf.versionToAnnotate, this.pf.relatedIGs);
    if (wantGen(r, "summary")) {
      long start = System.currentTimeMillis();
      fragment(cr.fhirType()+"-"+prefixForContainer+cr.getId()+"-summary", smr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), this.pf.igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary", "Canonical", lang);
    }
    if (wantGen(r, "summary-table")) {
      long start = System.currentTimeMillis();
      fragment(cr.fhirType()+"-"+prefixForContainer+cr.getId()+"-summary-table", smr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), this.pf.igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary-table", "Canonical", lang);
    }
  }

  private void generateOutputsLibrary(FetchedFile f, FetchedResource r, Library lib, Map<String,String> vars, String prefixForContainer, RenderingContext lrc, String lang) throws Exception {
    int counter = 0;
    for (Attachment att : lib.getContent()) {
      String extension = att.hasContentType() ? MimeType.getExtension(att.getContentType()) : null;
      if (extension != null && att.hasData()) {
        String filename = "Library-"+r.getId()+(counter == 0 ? "" : "-"+Integer.toString(counter))+"."+extension;
        FileUtilities.bytesToFile(att.getData(), Utilities.path(this.pf.tempDir, filename));
        this.pf.otherFilesRun.add(Utilities.path(this.pf.tempDir, filename));
      }
      counter++;
    }
  }

  private void generateOutputsExampleScenario(FetchedFile f, FetchedResource r, ExampleScenario scen, Map<String,String> vars, String prefixForContainer, RenderingContext lrc, String lang) throws Exception {
    ExampleScenarioRenderer er = new ExampleScenarioRenderer(this.pf.context, checkAppendSlash(this.pf.specPath), scen, Utilities.path(this.pf.tempDir), this.pf.igpkp, this.pf.specMaps, pageTargets(), this.pf.markdownEngine, this.pf.packge, lrc.copy(false).setDefinitionsTarget(this.pf.igpkp.getDefinitionsName(r)), this.pf.versionToAnnotate, this.pf.relatedIGs);
    if (wantGen(r, "actor-table")) {
      long start = System.currentTimeMillis();
      fragment("ExampleScenario-"+prefixForContainer+scen.getId()+"-actor-table", er.render(RenderingContext.ExampleScenarioRendererMode.ACTORS), f.getOutputNames(), r, vars, null, start, "actor-table", "ExampleScenario", lang);
    }
    if (wantGen(r, "instance-table")) {
      long start = System.currentTimeMillis();
      fragment("ExampleScenario-"+prefixForContainer+scen.getId()+"-instance-table", er.render(RenderingContext.ExampleScenarioRendererMode.INSTANCES), f.getOutputNames(), r, vars, null, start, "instance-table", "ExampleScenario", lang);
    }
    if (wantGen(r, "processes")) {
      long start = System.currentTimeMillis();
      fragment("ExampleScenario-"+prefixForContainer+scen.getId()+"-processes", er.render(RenderingContext.ExampleScenarioRendererMode.PROCESSES), f.getOutputNames(), r, vars, null, start, "processes", "ExampleScenario", lang);
    }
    if (wantGen(r, "process-diagram")) {
      long start = System.currentTimeMillis();
      fragment("ExampleScenario-"+prefixForContainer+scen.getId()+"-process-diagram", er.renderDiagram(), f.getOutputNames(), r, vars, null, start, "process-diagram", "ExampleScenario", lang);
    }
  }

  private void generateOutputsQuestionnaire(FetchedFile f, FetchedResource r, Questionnaire q, Map<String,String> vars, String prefixForContainer, RenderingContext lrc, String lang) throws Exception {
    QuestionnaireRenderer qr = new QuestionnaireRenderer(this.pf.context, checkAppendSlash(this.pf.specPath), q, Utilities.path(this.pf.tempDir), this.pf.igpkp, this.pf.specMaps, pageTargets(), this.pf.markdownEngine, this.pf.packge, lrc.copy(false).setDefinitionsTarget(this.pf.igpkp.getDefinitionsName(r)), this.pf.versionToAnnotate, this.pf.relatedIGs);
    if (wantGen(r, "summary")) {
      long start = System.currentTimeMillis();
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-summary", qr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), this.pf.igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary", "Questionnaire", lang);
    }
    if (wantGen(r, "summary-table")) {
      long start = System.currentTimeMillis();
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-summary-table", qr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), this.pf.igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary-table", "Questionnaire", lang);
    }
    if (wantGen(r, "tree")) {
      long start = System.currentTimeMillis();
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-tree", qr.render(RenderingContext.QuestionnaireRendererMode.TREE), f.getOutputNames(), r, vars, null, start, "tree", "Questionnaire", lang);
    }
    if (wantGen(r, "form")) {
      long start = System.currentTimeMillis();
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-form", qr.render(RenderingContext.QuestionnaireRendererMode.FORM), f.getOutputNames(), r, vars, null, start, "form", "Questionnaire", lang);
    }
    if (wantGen(r, "links")) {
      long start = System.currentTimeMillis();
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-links", qr.render(RenderingContext.QuestionnaireRendererMode.LINKS), f.getOutputNames(), r, vars, null, start, "links", "Questionnaire", lang);
    }
    if (wantGen(r, "logic")) {
      long start = System.currentTimeMillis();
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-logic", qr.render(RenderingContext.QuestionnaireRendererMode.LOGIC), f.getOutputNames(), r, vars, null, start, "logic", "Questionnaire", lang);
    }
    if (wantGen(r, "dict")) {
      long start = System.currentTimeMillis();
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-dict", qr.render(RenderingContext.QuestionnaireRendererMode.DEFNS), f.getOutputNames(), r, vars, null, start, "dict", "Questionnaire", lang);
    }
    if (wantGen(r, "responses")) {
      long start = System.currentTimeMillis();
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-responses", responsesForQuestionnaire(q), f.getOutputNames(), r, vars, null, start, "responses", "Questionnaire", lang);
    }
  }

  private String responsesForQuestionnaire(Questionnaire q) {
    StringBuilder b = new StringBuilder();
    for (FetchedFile f : pf.fileList) {
      f.start("responsesForQuestionnaire");
      try {

        for (FetchedResource r : f.getResources()) {
          if (r.fhirType().equals("QuestionnaireResponse")) {
            String qurl = r.getElement().getChildValue("questionnaire");
            if (!Utilities.noString(qurl)) {
              if (b.length() == 0) {
                b.append("<ul>\r\n");
              }
              b.append(" <li><a href=\""+ this.pf.igpkp.getLinkFor(r, true)+"\">"+getTitle(f,r )+"</a></li>\r\n");
            }
          }
        }
      } finally {
        f.finish("responsesForQuestionnaire");
      }
    }
    if (b.length() > 0) {
      b.append("</ul>\r\n");
    }
    return b.toString();
  }

  private void generateOutputsQuestionnaireResponse(FetchedFile f, FetchedResource r, Map<String,String> vars, String prefixForContainer, String lang) throws Exception {
    RenderingContext lrc = this.pf.rc.copy(false).setParser(getTypeLoader(f, r));
    String qu = getQuestionnaireURL(r);
    if (qu != null) {
      Questionnaire q = this.pf.context.fetchResource(Questionnaire.class, qu);
      if (q != null && q.hasWebPath()) {
        lrc.setDefinitionsTarget(q.getWebPath());
      }
    }

    QuestionnaireResponseRenderer qr = new QuestionnaireResponseRenderer(this.pf.context, checkAppendSlash(this.pf.specPath), r.getElement(), Utilities.path(this.pf.tempDir), this.pf.igpkp, this.pf.specMaps, pageTargets(), this.pf.markdownEngine, this.pf.packge, lrc);
    if (wantGen(r, "tree")) {
      long start = System.currentTimeMillis();
      fragment("QuestionnaireResponse-"+prefixForContainer+r.getId()+"-tree", qr.render(RenderingContext.QuestionnaireRendererMode.TREE), f.getOutputNames(), r, vars, null, start, "tree", "QuestionnaireResponse", lang);
    }
    if (wantGen(r, "form")) {
      long start = System.currentTimeMillis();
      fragment("QuestionnaireResponse-"+prefixForContainer+r.getId()+"-form", qr.render(RenderingContext.QuestionnaireRendererMode.FORM), f.getOutputNames(), r, vars, null, start, "form", "QuestionnaireResponse", lang);
    }
  }

  private String getQuestionnaireURL(FetchedResource r) {
    if (r.getResource() != null && r.getResource() instanceof QuestionnaireResponse) {
      return ((QuestionnaireResponse) r.getResource()).getQuestionnaire();
    }
    return r.getElement().getChildValue("questionnaire");
  }


  private String getTitle(FetchedFile f, FetchedResource r) {
    if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
      return ((CanonicalResource) r.getResource()).getTitle();
    }
    String t = r.getElement().getChildValue("title");
    if (t != null) {
      return t;
    }
    t = r.getElement().getChildValue("name");
    if (t != null) {
      return t;
    }
    for (ImplementationGuide.ImplementationGuideDefinitionResourceComponent res : this.pf.publishedIg.getDefinition().getResource()) {
      FetchedResource tr = (FetchedResource) res.getUserData(UserDataNames.pub_loaded_resource);
      if (tr == r) {
        return res.getDescription();
      }
    }
    return "(no description)";
  }

  private XhtmlNode getXhtml(FetchedFile f, FetchedResource r, Resource res, Element e) throws Exception {
    if (r.fhirType().equals("Bundle")) {
      // bundles are difficult and complicated.
      //      if (true) {
      //        RenderingContext lrc = rc.copy().setParser(getTypeLoader(f, r));
      //        return new BundleRenderer(lrc).render(ResourceElement.forResource(lrc, r.getElement()));
      //      }
      if (r.getResource() != null && r.getResource() instanceof Bundle) {
        RenderingContext lrc = this.pf.rc.copy(false).setParser(getTypeLoader(f, r));
        Bundle b = (Bundle) res;
        BundleRenderer br = new BundleRenderer(lrc);
        if (br.canRender(b)) {
          return br.buildNarrative(ResourceWrapper.forResource(this.pf.rc, b));
        }
      }
    }
    if (r.getResource() != null && res instanceof DomainResource) {
      DomainResource dr = (DomainResource) res;
      if (dr.getText().hasDiv())
        return removeResHeader(dr.getText().getDiv());
    }
    if (res != null && res instanceof Parameters) {
      Parameters p = (Parameters) r.getResource();
      return new ParametersRenderer(this.pf.rc).buildNarrative(ResourceWrapper.forResource(this.pf.rc, p));
    }
    if (r.fhirType().equals("Parameters")) {
      RenderingContext lrc = this.pf.rc.copy(false).setParser(getTypeLoader(f, r));
      return new ParametersRenderer(lrc).buildNarrative(ResourceWrapper.forResource(lrc, e));
    } else {
      return getHtmlForResource(e);
    }
  }

  private XhtmlNode getXhtml(FetchedFile f, FetchedResource r, Resource resource) throws Exception {
    if (resource instanceof DomainResource) {
      DomainResource dr = (DomainResource) resource;
      if (dr.getText().hasDiv())
        return dr.getText().getDiv();
    }
    if (resource instanceof Bundle) {
      Bundle b = (Bundle) resource;
      return new BundleRenderer(this.pf.rc).buildNarrative(ResourceWrapper.forResource(this.pf.rc, b));
    }
    if (resource instanceof Parameters) {
      Parameters p = (Parameters) resource;
      return new ParametersRenderer(this.pf.rc).buildNarrative(ResourceWrapper.forResource(this.pf.rc, p));
    }
    RenderingContext lrc = this.pf.rc.copy(false).setParser(getTypeLoader(f, r));
    return RendererFactory.factory(resource, lrc).buildNarrative(ResourceWrapper.forResource(this.pf.rc, resource));
  }


  private XhtmlNode getHtmlForResource(Element element) {
    Element text = element.getNamedChild("text");
    if (text == null)
      return null;
    Element div = text.getNamedChild("div");
    if (div == null)
      return null;
    else
      return removeResHeader(div.getXhtml());
  }

  private XhtmlNode removeResHeader(XhtmlNode xhtml) {
    XhtmlNode res = new XhtmlNode(xhtml.getNodeType(), xhtml.getName());
    res.getAttributes().putAll(xhtml.getAttributes());
    for (XhtmlNode x : xhtml.getChildNodes()) {
      if("div".equals(x.getName())) {
        res.getChildNodes().add(removeResHeader(x));
      } else if (!x.isClass("res-header-id"))  {
        res.getChildNodes().add(x);
      }
    }
    return res;
  }

  private void fragmentIfNN(String name, String content, Set<String> outputTracker, long start, String code, String context, String lang) throws IOException, FHIRException {
    if (!Utilities.noString(content)) {
      fragment(name, content, outputTracker, null, null, null, start, code, context, lang);
    }
  }

  private void trackedFragment(String id, String name, String content, Set<String> outputTracker, long start, String code, String context, String lang) throws IOException, FHIRException {
    if (!pf.trackedFragments.containsKey(id)) {
      pf.trackedFragments.put(id, new ArrayList<>());
    }
    pf.trackedFragments.get(id).add(name+".xhtml");
    fragment(name, content+HTMLInspector.TRACK_PREFIX+id+HTMLInspector.TRACK_SUFFIX, outputTracker, null, null, null, start, code, context, lang);
  }

  private void fragment(String name, String content, Set<String> outputTracker, long start, String code, String context, String lang) throws IOException, FHIRException {
    fragment(name, content, outputTracker, null, null, null, start, code, context, lang);
  }

  private void fragment(String name, String content, Set<String> outputTracker, FetchedResource r, Map<String, String> vars, String format, long start, String code, String context, String lang) throws IOException, FHIRException {
    String fixedContent = (r==null? content : pf.igpkp.doReplacements(content, r, vars, format))+(settings.isTrackFragments() ? "<!-- fragment:"+context+"."+code+" -->" : "");

    PublisherBase.FragmentUseRecord frag = pf.fragmentUses.get(context+"."+code);
    if (frag == null) {
      frag = new PublisherBase.FragmentUseRecord();
      pf.fragmentUses.put(context+"."+code, frag);
    }
    frag.record(System.currentTimeMillis() - start, fixedContent.length());

    if (checkMakeFile(FileUtilities.stringToBytes(wrapLiquid(fixedContent)), Utilities.path(pf.tempDir, "_includes", name+(lang == null ? "" : "-" + lang)+".xhtml"), outputTracker)) {
      if (settings.getMode() != PublisherUtils.IGBuildMode.AUTOBUILD && pf.makeQA) {
        FileUtilities.stringToFile(pageWrap(fixedContent, name), Utilities.path(pf.qaDir, name+".html"));
      }
    }
  }


  private void updateImplementationGuide() throws Exception {
    FetchedResource r = pf.altMap.get(IG_NAME).getResources().get(0);
    if (!pf.publishedIg.hasText() || !pf.publishedIg.getText().hasDiv()) {
      pf.publishedIg.setText(((ImplementationGuide)r.getResource()).getText());
    }
    r.setResource(pf.publishedIg);
    r.setElement(convertToElement(r, pf.publishedIg));

    for (ImplementationGuide.ImplementationGuideDefinitionResourceComponent res : pf.publishedIg.getDefinition().getResource()) {
      FetchedResource rt = null;
      for (FetchedFile tf : pf.fileList) {
        for (FetchedResource tr : tf.getResources()) {
          if (tr.getLocalRef().equals(res.getReference().getReference())) {
            rt = tr;
          }
        }
      }
      if (rt != null) {
        if (!rt.getProvenance()) {
          // Don't expose a page for a resource that is just provenance information
          String path = pf.igpkp.doReplacements(pf.igpkp.getLinkFor(rt, false), rt, null, null);
          res.addExtension().setUrl("http://hl7.org/fhir/StructureDefinition/implementationguide-page").setValue(new UriType(path));
          pf.inspector.addLinkToCheck(Utilities.path(pf.outputDir, path), path, "fake generated link for Implementation Guide");
        }
        for (PublisherUtils.ContainedResourceDetails c : getContained(rt.getElement())) {
          Extension ex = new Extension(ExtensionDefinitions.EXT_IGP_CONTAINED_RESOURCE_INFO);
          res.getExtension().add(ex);
          ex.addExtension("type", new CodeType(c.getType()));
          ex.addExtension("id", new IdType(c.getId()));
          ex.addExtension("title", new StringType(c.getType()));
          ex.addExtension("description", new StringType(c.getDescription()));
        }
      }
    }
  }


  private void generateDataFile(DBBuilder db) throws Exception {
    JsonObject data = new JsonObject();
    data.add("path", checkAppendSlash(pf.specPath));
    data.add("canonical", pf.igpkp.getCanonical());
    data.add("igId", pf.publishedIg.getId());
    data.add("igName", pf.publishedIg.getName());
    data.add("packageId", pf.npmName);
    data.add("igVer", workingVersion());
    data.add("errorCount", getErrorCount());
    data.add("version", pf.version);

    StringType rl = findReleaseLabel();
    if (rl == null) {
      data.add("releaseLabel", "n/a");
      for (String l : allLangs()) {
        data.add("releaseLabel"+l, pf.rcLangs.get(l).formatPhrase(RenderingI18nContext._NA));
      }
    } else {
      data.add("releaseLabel", rl.getValue());
      addTranslationsToJson(data, "releaseLabel", rl, false);
    }
    data.add("revision", pf.specMaps.get(0).getBuild());
    data.add("versionFull", pf.version +"-"+ pf.specMaps.get(0).getBuild());
    data.add("toolingVersion", Constants.VERSION);
    data.add("toolingRevision", ToolsVersion.TOOLS_VERSION_STR);
    data.add("toolingVersionFull", Constants.VERSION+" ("+ToolsVersion.TOOLS_VERSION_STR+")");

    data.add("genDate", genTime());
    data.add("genDay", genDate());
    if (db != null) {
      for (JsonProperty p : data.getProperties()) {
        if (p.getValue().isJsonPrimitive()) {
          db.metadata(p.getName(), p.getValue().asString());
        }
      }
      db.metadata("gitstatus", getGitStatus());
    }

    data.add("totalFiles", pf.fileList.size());
    data.add("processedFiles", pf.changeList.size());

    if (settings.getRepoSource() != null) {
      data.add("repoSource", gh());
    } else {
      String git= getGitSource();
      if (git != null) {
        data.add("repoSource", git);
      }
    }

    JsonArray rt = data.forceArray("resourceTypes");
    List<String> rtl = pf.context.getResourceNames();
    for (String s : rtl) {
      rt.add(s);
    }
    rt = data.forceArray("dataTypes");
    ContextUtilities cu = new ContextUtilities(pf.context, pf.suppressedMappings);
    for (String s : cu.getTypeNames()) {
      if (!rtl.contains(s)) {
        rt.add(s);
      }
    }

    JsonObject ig = new JsonObject();
    data.add("ig", ig);
    ig.add("id", pf.publishedIg.getId());
    ig.add("name", pf.publishedIg.getName());
    ig.add("title", pf.publishedIg.getTitle());
    addTranslationsToJson(ig, "title", pf.publishedIg.getTitleElement(), false);
    ig.add("url", pf.publishedIg.getUrl());
    ig.add("version", workingVersion());
    ig.add("status", pf.publishedIg.getStatusElement().asStringValue());
    ig.add("experimental", pf.publishedIg.getExperimental());
    ig.add("publisher", pf.publishedIg.getPublisher());
    addTranslationsToJson(ig, "publisher", pf.publishedIg.getPublisherElement(), false);

    if (pf.previousVersionComparator != null && pf.previousVersionComparator.hasLast() && !targetUrl().startsWith("file:")) {
      JsonObject diff = new JsonObject();
      data.add("diff", diff);
      diff.add("name", Utilities.encodeUri(pf.previousVersionComparator.getLastName()));
      diff.add("current", Utilities.encodeUri(targetUrl()));
      diff.add("previous", Utilities.encodeUri(pf.previousVersionComparator.getLastUrl()));
    }
    if (pf.ipaComparator != null && pf.ipaComparator.hasLast() && !targetUrl().startsWith("file:")) {
      JsonObject diff = new JsonObject();
      data.add("ipa-diff", diff);
      diff.add("name", Utilities.encodeUri(pf.ipaComparator.getLastName()));
      diff.add("current", Utilities.encodeUri(targetUrl()));
      diff.add("previous", Utilities.encodeUri(pf.ipaComparator.getLastUrl()));
    }
    if (pf.ipsComparator != null && pf.ipsComparator.hasLast() && !targetUrl().startsWith("file:")) {
      JsonObject diff = new JsonObject();
      data.add("ips-diff", diff);
      diff.add("name", Utilities.encodeUri(pf.ipsComparator.getLastName()));
      diff.add("current", Utilities.encodeUri(targetUrl()));
      diff.add("previous", Utilities.encodeUri(pf.ipsComparator.getLastUrl()));
    }

    if (pf.publishedIg.hasContact()) {
      JsonArray jc = new JsonArray();
      ig.add("contact", jc);
      for (ContactDetail c : pf.publishedIg.getContact()) {
        JsonObject jco = new JsonObject();
        jc.add(jco);
        jco.add("name", c.getName());
        if (c.hasTelecom()) {
          JsonArray jct = new JsonArray();
          jco.add("telecom", jct);
          for (ContactPoint cc : c.getTelecom()) {
            jct.add(new JsonString(cc.getValue()));
          }
        }
      }
      for (String l : allLangs()) {
        jc = new JsonArray();
        ig.add("contact"+l, jc);
        for (ContactDetail c : pf.publishedIg.getContact()) {
          JsonObject jco = new JsonObject();
          jc.add(jco);
          jco.add("name", pf.langUtils.getTranslationOrBase(c.getNameElement(), l));
          if (c.hasTelecom()) {
            JsonArray jct = new JsonArray();
            jco.add("telecom", jct);
            for (ContactPoint cc : c.getTelecom()) {
              jct.add(new JsonString(cc.getValue()));
            }
          }
        }

      }
    }
    ig.add("date", pf.publishedIg.getDateElement().asStringValue());
    ig.add("description", preProcessMarkdown(pf.publishedIg.getDescription()));
    addTranslationsToJson(ig, "description", pf.publishedIg.getDescriptionElement(), false);

    if (pf.context.getTxClientManager() != null && pf.context.getTxClientManager().getMaster() != null) {
      ig.add("tx-server", pf.context.getTxClientManager().getMaster().getAddress());
    }
    ig.add("copyright", pf.publishedIg.getCopyright());
    addTranslationsToJson(ig, "copyright", pf.publishedIg.getCopyrightElement(), false);

    for (Enumeration<Enumerations.FHIRVersion> v : pf.publishedIg.getFhirVersion()) {
      ig.add("fhirVersion", v.asStringValue());
      break;
    }

    for (SpecMapManager sm : pf.specMaps) {
      if (sm.getName() != null) {
        data.set(sm.getName(), pf.appendTrailingSlashInDataFile ? sm.getBase() : Utilities.appendForwardSlash(sm.getBase()));
        if (!data.has("ver")) {
          data.add("ver", new JsonObject());
        }
        data.getJsonObject("ver").set(sm.getName(), pf.appendTrailingSlashInDataFile ? sm.getBase2() : Utilities.appendForwardSlash(sm.getBase2()));
      }
    }
    String json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(data, true);
    FileUtilities.stringToFile(json, Utilities.path(pf.tempDir, "_data", "fhir.json"));
    JsonObject related = new JsonObject();
    for (RelatedIG rig : pf.relatedIGs) {
      JsonObject o = new JsonObject();
      related.add(rig.getCode(), o);
      o.add("id", rig.getId());
      o.add("link", rig.getWebLocation());
      o.add("homepage", rig.getWebLocation().startsWith("file:") ? Utilities.pathURL(rig.getWebLocation(), "index.html") : rig.getWebLocation());
      o.add("canonical", rig.getCanonical());
      o.add("title", rig.getTitle());
      o.add("version", rig.getVersion());
    }
    json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(related, true);
    FileUtilities.stringToFile(json, Utilities.path(pf.tempDir, "_data", "related.json"));
  }


  private void addTranslationsToJson(JsonObject item, String name, PrimitiveType<?> element, boolean preprocess) throws Exception {
    JsonObject ph = item.forceObject(name+"lang");
    for (String l : allLangs()) {
      String s;
      s = pf.langUtils.getTranslationOrBase(element, l);
      if (preprocess) {
        s = preProcessMarkdown(s);
      }
      ph.add(l, s);
    }
  }

  private void generateViewDefinitions(DBBuilder db) {
    for (String vdn : pf.viewDefinitions) {
      logMessage("Generate View "+vdn);
      Runner runner = new Runner();
      try {
        runner.setContext(pf.context);
        PublisherProvider pprov = new PublisherProvider(pf.context, pf.npmList, pf.fileList, pf.igpkp.getCanonical());
        runner.setProvider(pprov);
        runner.setStorage(new StorageSqlite3(db.getConnection()));
        JsonObject vd = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(new File(Utilities.path(FileUtilities.getDirectoryForFile(settings.getConfigFile()), vdn)));
        pprov.inspect(vd);
        runner.execute(vd);
        captureIssues(vdn, runner.getIssues());

        StorageJson jstore = new StorageJson();
        runner.setStorage(jstore);
        runner.execute(vd);
        String filename = Utilities.path(pf.tempDir, vd.asString("name")+".json");
        FileUtilities.stringToFile(org.hl7.fhir.utilities.json.parser.JsonParser.compose(jstore.getRows(), true), filename);
        pf.otherFilesRun.add(filename);
      } catch (Exception e) {
        e.printStackTrace();
        pf.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.REQUIRED, vdn, "Error Processing ViewDefinition: "+e.getMessage(), ValidationMessage.IssueSeverity.ERROR));
        captureIssues(vdn, runner.getIssues());
      }
    }
  }

  private void captureIssues(String vdn, List<ValidationMessage> issues) {
    if (issues != null) {
      for (ValidationMessage msg : issues) {
        ValidationMessage nmsg = new ValidationMessage(msg.getSource(), msg.getType(), msg.getLine(), msg.getCol(), vdn+msg.getLocation(), msg.getMessage(), msg.getLevel());
        pf.errors.add(nmsg);
      }
    }
  }


  private String getGitStatus() throws IOException {
    File gitDir = new File(FileUtilities.getDirectoryForFile(settings.getConfigFile()));
    return GitUtilities.getGitStatus(gitDir);
  }

  private String getGitSource() throws IOException {
    File gitDir = new File(FileUtilities.getDirectoryForFile(settings.getConfigFile()));
    return GitUtilities.getGitSource(gitDir);
  }

  private String gh() {
    return settings.getRepoSource() != null ? settings.getRepoSource() : settings.getTargetOutput() != null ? settings.getTargetOutput().replace("https://build.fhir.org/ig", "https://github.com") : null;
  }


  private String genTime() {
    return new SimpleDateFormat("EEE, MMM d, yyyy HH:mmZ", new Locale("en", "US")).format(pf.getExecTime().getTime());
  }

  private String genDate() {
    return new SimpleDateFormat("dd/MM/yyyy", new Locale("en", "US")).format(pf.getExecTime().getTime());
  }

  private void generateSummaryOutputs(DBBuilder db, String lang, RenderingContext rc) throws Exception {
    log("Generating Summary Outputs"+(lang == null?"": " ("+lang+")"));
    generateResourceReferences(lang);

    generateCanonicalSummary(lang);

    CrossViewRenderer cvr = new CrossViewRenderer(pf.igpkp.getCanonical(), pf.altCanonical, pf.context, pf.igpkp.specPath(), rc.copy(false));
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
          cvr.seeResource((CanonicalResource) r.getResource());
        }
      }
    }
    MappingSummaryRenderer msr = new MappingSummaryRenderer(pf.context, rc);
    msr.addCanonical(pf.igpkp.getCanonical());
    if (pf.altCanonical != null) {
      msr.addCanonical(pf.altCanonical);
    }
    msr.analyse();
    Set<String> types = new HashSet<>();
    for (StructureDefinition sd : pf.context.fetchResourcesByType(StructureDefinition.class)) {
      if (sd.getUrl().equals("http://hl7.org/fhir/StructureDefinition/Base") || (sd.getDerivation() == StructureDefinition.TypeDerivationRule.SPECIALIZATION && sd.getKind() != StructureDefinition.StructureDefinitionKind.LOGICAL && !types.contains(sd.getType()))) {
        types.add(sd.getType());
        long start = System.currentTimeMillis();
        String src = msr.render(sd);
        fragment("maps-"+sd.getTypeTail(), src, pf.otherFilesRun, start, "maps", "Cross", lang);
      }
    }
    long start = System.currentTimeMillis();
    fragment("summary-observations", cvr.getObservationSummary(), pf.otherFilesRun, start, "summary-observations", "Cross", lang);
    String path = Utilities.path(pf.tempDir, "observations-summary.xlsx");
    ObservationSummarySpreadsheetGenerator vsg = new ObservationSummarySpreadsheetGenerator(pf.context);
    pf.otherFilesRun.add(path);
    vsg.generate(cvr.getObservations());
    vsg.finish(new FileOutputStream(path));
    DeprecationRenderer dpr = new DeprecationRenderer(pf.context, checkAppendSlash(pf.specPath), pf.igpkp, pf.specMaps, pageTargets(), pf.markdownEngine, pf.packge, rc.copy(false));
    fragment("deprecated-list", dpr.deprecationSummary(pf.fileList, pf.previousVersionComparator), pf.otherFilesRun, start, "deprecated-list", "Cross", lang);
    fragment("new-extensions", dpr.listNewResources(pf.fileList, pf.previousVersionComparator, "StructureDefinition.extension"), pf.otherFilesRun, start, "new-extensions", "Cross", lang);
    fragment("deleted-extensions", dpr.listDeletedResources(pf.fileList, pf.previousVersionComparator, "StructureDefinition.extension"), pf.otherFilesRun, start, "deleted-extensions", "Cross", lang);

    JsonObject data = new JsonObject();
    JsonArray ecl = new JsonArray();
    //    data.add("extension-contexts-populated", ecl); causes a bug in jekyll see https://github.com/jekyll/jekyll/issues/9289

    start = System.currentTimeMillis();
    fragment("summary-extensions", cvr.getExtensionSummary(), pf.otherFilesRun, start, "summary-extensions", "Cross", lang);
    start = System.currentTimeMillis();
    fragment("extension-list", cvr.buildExtensionTable(), pf.otherFilesRun, start, "extension-list", "Cross", lang);
    Set<String> econtexts = pf.cu.getTypeNameSet();
    for (String s : cvr.getExtensionContexts()) {
      ecl.add(s);
      econtexts.add(s);
    }
    for (String s : econtexts) {
      start = System.currentTimeMillis();
      fragment("extension-list-"+s, cvr.buildExtensionTable(s), pf.otherFilesRun, start, "extension-list-", "Cross", lang);
    }
    for (String s : pf.context.getResourceNames()) {
      start = System.currentTimeMillis();
      fragment("extension-search-list-"+s, cvr.buildExtensionSearchTable(s), pf.otherFilesRun, start, "extension-search-list", "Cross", lang);
    }
    for (String s : cvr.getExtensionIds()) {
      start = System.currentTimeMillis();
      fragment("extension-search-"+s, cvr.buildSearchTableForExtension(s), pf.otherFilesRun, start, "extension-search", "Cross", lang);
    }

    start = System.currentTimeMillis();
    List<ValueSet> vslist = cvr.buildDefinedValueSetList(pf.fileList);
    fragment("valueset-list", cvr.renderVSList(pf.versionToAnnotate, vslist, cvr.needVersionReferences(vslist, pf.publishedIg.getVersion()), false), pf.otherFilesRun, start, "valueset-list",  "Cross", lang);
    saveVSList("valueset-list", vslist, db, 1);

    start = System.currentTimeMillis();
    vslist = cvr.buildUsedValueSetList(false, pf.fileList);
    fragment("valueset-ref-list", cvr.renderVSList(pf.versionToAnnotate, vslist, cvr.needVersionReferences(vslist, pf.publishedIg.getVersion()), true), pf.otherFilesRun, start, "valueset-ref-list", "Cross", lang);
    saveVSList("valueset-ref-list", vslist, db, 2);

    start = System.currentTimeMillis();
    vslist = cvr.buildUsedValueSetList(true, pf.fileList);
    fragment("valueset-ref-all-list", cvr.renderVSList(pf.versionToAnnotate, vslist, cvr.needVersionReferences(vslist, pf.publishedIg.getVersion()), true), pf.otherFilesRun, start, "valueset-ref-all-list", "Cross", lang);
    saveVSList("valueset-ref-all-list", vslist, db, 3);

    start = System.currentTimeMillis();
    List<CodeSystem> cslist = cvr.buildDefinedCodeSystemList(pf.fileList);
    fragment("codesystem-list", cvr.renderCSList(pf.versionToAnnotate, cslist, cvr.needVersionReferences(vslist, pf.publishedIg.getVersion()), false), pf.otherFilesRun, start, "codesystem-list", "Cross", lang);
    saveCSList("codesystem-list", cslist, db, 1);

    start = System.currentTimeMillis();
    cslist = cvr.buildUsedCodeSystemList(false, pf.fileList);
    fragment("codesystem-ref-list", cvr.renderCSList(pf.versionToAnnotate, cslist, cvr.needVersionReferences(vslist, pf.publishedIg.getVersion()), true), pf.otherFilesRun, start, "codesystem-ref-list", "Cross", lang);
    saveCSList("codesystem-ref-list", cslist, db, 2);

    start = System.currentTimeMillis();
    cslist = cvr.buildUsedCodeSystemList(true, pf.fileList);
    fragment("codesystem-ref-all-list", cvr.renderCSList(pf.versionToAnnotate, cslist, cvr.needVersionReferences(vslist, pf.publishedIg.getVersion()), true), pf.otherFilesRun, start, "codesystem-ref-all-list", "Cross", lang);
    saveCSList("codesystem-ref-all-list", cslist, db, 3);

    fragment("obligation-summary", cvr.renderObligationSummary(), pf.otherFilesRun, System.currentTimeMillis(), "obligation-summary", "Cross", lang);

    for (String v : pf.generateVersions) {
      for (String n : pf.context.getResourceNames()) {
        fragment("version-"+v+"-summary-"+n, generateVersionSummary(v, n), pf.otherFilesRun, start, "version-"+v+"-summary-"+n, "Cross", lang);
      }
    }
    start = System.currentTimeMillis();
    pf.ipStmt = new IPStatementsRenderer(pf.context, pf.markdownEngine, pf.sourceIg.getPackageId(), rc).genIpStatements(pf.fileList, lang);
    trackedFragment("1", "ip-statements", pf.ipStmt, pf.otherFilesRun, start, "ip-statements", "Cross", lang);
    if (VersionUtilities.isR4Ver(pf.version) || VersionUtilities.isR4BVer(pf.version)) {
      trackedFragment("2", "cross-version-analysis", pf.r4tor4b.generate(pf.npmName, false), pf.otherFilesRun, System.currentTimeMillis(), "cross-version-analysis", "Cross", lang);
      trackedFragment("2", "cross-version-analysis-inline", pf.r4tor4b.generate(pf.npmName, true), pf.otherFilesRun, System.currentTimeMillis(), "cross-version-analysis-inline", "Cross", lang);
    } else {
      fragment("cross-version-analysis", pf.r4tor4b.generate(pf.npmName, false), pf.otherFilesRun, System.currentTimeMillis(), "cross-version-analysis", "Cross", lang);
      fragment("cross-version-analysis-inline", pf.r4tor4b.generate(pf.npmName, true), pf.otherFilesRun, System.currentTimeMillis(), "cross-version-analysis-inline", "Cross", lang);
    }
    DependencyRenderer depr = new DependencyRenderer(pf.pcm, pf.tempDir, pf.npmName, pf.templateManager, makeDependencies(), pf.context, pf.markdownEngine, rc, pf.specMaps);
    trackedFragment("3", "dependency-table", depr.render(pf.publishedIg, false, true, true), pf.otherFilesRun, System.currentTimeMillis(), "dependency-table", "Cross", lang);
    trackedFragment("3", "dependency-table-short", depr.render(pf.publishedIg, false, false, false), pf.otherFilesRun, System.currentTimeMillis(), "dependency-table-short", "Cross", lang);
    trackedFragment("3", "dependency-table-nontech", depr.renderNonTech(pf.publishedIg), pf.otherFilesRun, System.currentTimeMillis(), "dependency-table-nontech", "Cross", lang);
    trackedFragment("4", "globals-table", depr.renderGlobals(), pf.otherFilesRun, System.currentTimeMillis(), "globals-table", "Cross", lang);
    String expr = renderExpansionParameters();
    if (Utilities.noString(expr)) {
      fragment("expansion-params", expr, pf.otherFilesRun, System.currentTimeMillis(), "expansion-params", "Cross", lang);
    } else {
      trackedFragment("5", "expansion-params", expr, pf.otherFilesRun, System.currentTimeMillis(), "expansion-params", "Cross", lang);
    }

    fragment("related-igs-list", relatedIgsList(), pf.otherFilesRun, System.currentTimeMillis(), "related-igs-list", "Cross", lang);
    fragment("related-igs-table", relatedIgsTable(), pf.otherFilesRun, System.currentTimeMillis(), "related-igs-table", "Cross", lang);

    // now, list the profiles - all the profiles
    int i = 0;
    JsonObject maturities = new JsonObject();
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof DomainResource) {
          String fmm = ExtensionUtilities.readStringExtension((DomainResource) r.getResource(), ExtensionDefinitions.EXT_FMM_LEVEL);
          if (fmm != null) {
            maturities.add(r.getResource().fhirType()+"-"+r.getId(), fmm);
          }
        }
        if (r.fhirType().equals("StructureDefinition")) {
          StructureDefinition sd = (StructureDefinition) r.getResource();

          JsonObject item = new JsonObject();
          data.add(sd.getId(), item);
          item.add("index", i);
          item.add("url", sd.getUrl());
          item.add("name", sd.getName());
          item.add("title", sd.present());
          item.add("uml", r.isUmlGenerated());
          addTranslationsToJson(item, "title", sd.getTitleElement(), false);
          item.add("path", sd.getWebPath());
          if (sd.hasKind()) {
            item.add("kind", sd.getKind().toCode());
          }
          item.add("type", sd.getType());
          item.add("base", sd.getBaseDefinition());
          StructureDefinition base = sd.hasBaseDefinition() ? this.pf.context.fetchResource(StructureDefinition.class, sd.getBaseDefinition()) : null;
          if (base != null) {
            item.add("basename", base.getName());
            item.add("basepath", Utilities.escapeXml(base.getWebPath()));
          } else if ("http://hl7.org/fhir/StructureDefinition/Base".equals(sd.getBaseDefinitionNoVersion())) {
            item.add("basename", "Base");
            item.add("basepath", "http://hl7.org/fhir/StructureDefinition/Element");
          }
          item.add("adl", sd.hasUserData(UserDataNames.archetypeSource));
          if (sd.hasStatus()) {
            item.add("status", sd.getStatus().toCode());
          }
          if (sd.hasDate()) {
            item.add("date", sd.getDate().toString());
          }
          item.add("abstract", sd.getAbstract());
          if (sd.hasDerivation()) {
            item.add("derivation", sd.getDerivation().toCode());
          }
          item.add("publisher", sd.getPublisher());
          addTranslationsToJson(item, "publisher", sd.getPublisherElement(), false);
          item.add("copyright", sd.getCopyright());
          addTranslationsToJson(item, "copyright", sd.getCopyrightElement(), false);
          item.add("description", preProcessMarkdown(sd.getDescription()));
          addTranslationsToJson(item, "description", this.pf.publishedIg.getDescriptionElement(), true);
          item.add("obligations", ProfileUtilities.hasObligations(sd));

          if (sd.hasContext()) {
            JsonArray contexts = new JsonArray();
            item.add("contexts", contexts);
            for (StructureDefinition.StructureDefinitionContextComponent ec : sd.getContext()) {
              JsonObject citem = new JsonObject();
              contexts.add(citem);
              citem.add("type", ec.hasType() ? ec.getType().getDisplay() : "??");
              citem.add("expression", ec.getExpression());
            }
          }
          if (ProfileUtilities.isExtensionDefinition(sd)) {
            List<String> ec = cvr.getExtensionContext(sd);
            JsonArray contexts = new JsonArray();
            item.add("extension-contexts", contexts);
            for (String s : ec) {
              contexts.add(s);
            }
          }
          i++;
        }
      }
    }
    if (maturities.getProperties().size() > 0) {
      data.add("maturities", maturities);
    }

    for (FetchedResource r: pf.examples) {
      FetchedResource baseRes = getResourceForUri(r.getExampleUri());
      if (baseRes == null) {
        // We only yell if the resource doesn't exist, not only if it doesn't exist in the current IG.
        if (pf.context.fetchResource(StructureDefinition.class, r.getExampleUri())==null) {
          FetchedFile f = findFileForResource(r);
          (f != null ? f.getErrors() : this.pf.errors).add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.NOTFOUND, r.fhirType()+"/"+r.getId(), "Unable to find profile " + r.getExampleUri() + " nominated as the profile for which resource " + r.getUrlTail()+" is an example", ValidationMessage.IssueSeverity.ERROR));
        }
      } else {
        baseRes.addStatedExample(r);
      }
    }

    for (FetchedResource r : pf.testplans) {
      if (r.hasTestArtifacts()) {
        FetchedResource baseRes = null;
        for (String tsArtifact : r.getTestArtifacts()) {
          baseRes = getResourceForUri(tsArtifact);
          if (baseRes == null) {
            // We only yell if the resource doesn't exist, not only if it doesn't exist in the current IG.
            pf.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.NOTFOUND, r.fhirType()+"/"+r.getId(), "Unable to find artifact " + tsArtifact + " nominated as the artifact for test resource " + r.getUrlTail(), ValidationMessage.IssueSeverity.WARNING));
          } else {
            baseRes.addFoundTestPlan(r);
          }
        }
      }
    }

    for (FetchedResource r : pf.testscripts) {
      if (r.hasTestArtifacts()) {
        FetchedResource baseRes = null;
        for (String tsArtifact : r.getTestArtifacts()) {
          baseRes = getResourceForUri(tsArtifact);
          if (baseRes == null) {
            // We only yell if the resource doesn't exist, not only if it doesn't exist in the current IG.
            pf.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.NOTFOUND, r.fhirType()+"/"+r.getId(), "Unable to find artifact " + tsArtifact + " nominated as the artifact for test resource " + r.getUrlTail(), ValidationMessage.IssueSeverity.WARNING));
          } else {
            baseRes.addFoundTestScript(r);
          }
        }
      }
    }

    String json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(data, true);
    FileUtilities.stringToFile(json, Utilities.path(pf.tempDir, "_data", "structuredefinitions.json"));

    // now, list the profiles - all the profiles
    data = new JsonObject();
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("Questionnaire")) {
          Questionnaire q = (Questionnaire) r.getResource();

          JsonObject item = new JsonObject();
          data.add(q.getId(), item);
          item.add("index", i);
          item.add("url", q.getUrl());
          item.add("name", q.getName());
          addTranslationsToJson(item, "name", q.getNameElement(), false);
          item.add("path", q.getWebPath());
          item.add("status", q.getStatus().toCode());
          item.add("date", q.getDate().toString());
          item.add("publisher", q.getPublisher());
          addTranslationsToJson(item, "publisher", q.getPublisherElement(), false);
          item.add("copyright", q.getCopyright());
          addTranslationsToJson(item, "copyright", q.getCopyrightElement(), false);
          item.add("description", preProcessMarkdown(q.getDescription()));
          addTranslationsToJson(item, "description", q.getDescriptionElement(), true);
          i++;
        }
      }
    }

    json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(data, true);
    FileUtilities.stringToFile(json, Utilities.path(pf.tempDir, "_data", "questionnaires.json"));

    // now, list the profiles - all the profiles
    data = new JsonObject();
    i = 0;
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        JsonObject item = new JsonObject();
        data.add(r.fhirType()+"/"+r.getId(), item);
        item.add("history", r.hasHistory());
        item.add("testplan", r.hasFoundTestPlans());
        item.add("testscript", r.hasFoundTestScripts());
        item.add("index", i);
        item.add("source", f.getStatedPath());
        item.add("sourceTail", tailPI(f.getStatedPath()));
        if (f.hasAdditionalPaths()) {
          JsonArray adp = item.forceArray("additional-paths");
          for (String s : f.getAdditionalPaths()) {
            JsonObject p = new JsonObject();
            adp.add(p);
            p.add("source", s);
            p.add("sourceTail", tailPI(s));
          }
        }
        if (r.fhirType().equals("CodeSystem")) {
          item.add("content", ((CodeSystem) r.getResource()).getContent().toCode());
        }
        path = null;
        if (r.getPath() != null) {
          path = r.getPath();
        } else if (r.getResource() != null) {
          path = r.getResource().getWebPath();
        } else {
          path = r.getElement().getWebPath();
        }
        if (path != null) {
          item.add("path", path);
        }
        if (r.getResource() != null) {
          populateResourceEntry(r, item, null);
        } else if (r.isCustomResource()) {
          populateCustomResourceEntry(r, item, null);
        }
        JsonArray contained = null;
        // contained resources get added twice - once as sub-entries under the resource that contains them, and once as an entry in their own right, for their own rendering
        for (PublisherUtils.ContainedResourceDetails crd : getContained(r.getElement())) {
          if (contained == null) {
            contained = new JsonArray();
            item.add("contained", contained);
          }
          JsonObject jo = new JsonObject();
          contained.add(jo);
          jo.add("type", crd.getType());
          jo.add("id", crd.getId());
          jo.add("title", crd.getTitle());
          jo.add("description", preProcessMarkdown(crd.getDescription()));

          JsonObject citem = new JsonObject();
          data.add(crd.getType()+"/"+r.getId()+"_"+crd.getId(), citem);
          citem.add("history", r.hasHistory());
          citem.add("index", i);
          citem.add("source", f.getStatedPath()+"#"+crd.getId());
          citem.add("sourceTail", tailPI(f.getStatedPath())+"#"+crd.getId());
          citem.add("path", crd.getType()+"-"+r.getId()+"_"+crd.getId()+".html");// todo: is this always correct?
          JsonObject container = new JsonObject();
          citem.add("container", container);
          container.add("id", r.fhirType()+"/"+r.getId());
          if (path != null) {
            container.add("path", path);
          }
          if (r.getResource() != null) {
            populateResourceEntry(r, citem, crd);
          }
        }
        i++;
      }
    }

    json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(data, true);
    FileUtilities.stringToFile(json, Utilities.path(pf.tempDir, "_data", "resources.json"));

    data = new JsonObject();
    if (pf.sourceIg.hasLanguage()) {
      data.add("ig", pf.sourceIg.getLanguage());
    }
    data.add("hasTranslations", pf.hasTranslations);
    data.add("defLang", pf.defaultTranslationLang);
    JsonArray langs = new JsonArray();
    data.add("langs", langs);
    Map<String, Map<String, String>> langDisplays = loadLanguagesCsv();
    ValueSet vs = pf.context.fetchResource(ValueSet.class, "http://hl7.org/fhir/ValueSet/languages");
    Pattern RtlLocalesRe = Pattern.compile("^(ar|dv|he|iw|fa|nqo|ps|sd|ug|ur|yi|.*[-_](Arab|Hebr|Thaa|Nkoo|Tfng))(?!.*[-_](Latn|Cyrl)($|-|_))($|-|_)");
    for (String code : allLangs()) {
      JsonObject lu = new JsonObject();
      langs.add(lu);
      lu.add("code", code);
      lu.add("rtl", RtlLocalesRe.matcher(code).find());
    }
    for (String code : allLangs()) {
      String disp = null;
      if (langDisplays.containsKey(code)) {
        disp = langDisplays.get(code).get("en");
      }
      if (disp == null && vs != null) {
        ValueSet.ConceptReferenceComponent cc = getConceptReference(vs, "urn:ietf:bcp:47", code);
        if (cc != null) {
          disp = cc.getDisplay();
        }
      }
      if (disp == null) {
        disp = getLangDesc(code, null);
      }

      for (String code2 : allLangs()) {
        String disp2 = null;
        if (langDisplays.containsKey(code)) {
          disp2 = langDisplays.get(code).get(code2);
        }
        if (disp2 == null && vs != null) {
          ValueSet.ConceptReferenceComponent cc = getConceptReference(vs, "urn:ietf:bcp:47", code);
          ValueSet.ConceptReferenceDesignationComponent dd = null;
          for (ValueSet.ConceptReferenceDesignationComponent t : cc.getDesignation()) {
            if (code2.equals(t.getLanguage())) {
              dd = t;
            }
          }
          if (dd != null) {
            disp2 = dd.getValue();
          }
        }
        if (code2.equals(code)) {
          JsonObject lu = null;
          for (JsonObject t : langs.asJsonObjects()) {
            if (code2.equals(t.asString("code"))) {
              lu = t;
            }
          }
          lu.add("display", disp2);
        }
        JsonObject lu = null;
        for (JsonObject t : langs.asJsonObjects()) {
          if (code2.equals(t.asString("code"))) {
            lu = t;
          }
        }
        if (disp2 != null) {
          lu.add("display-"+code, disp2);
        }
      }
    }

    json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(data, true);
    FileUtilities.stringToFile(json, Utilities.path(pf.tempDir, "_data", "languages.json"));
  }

  public ValueSet.ConceptReferenceComponent getConceptReference(ValueSet vs, String system, String code) {
    for (ValueSet.ConceptSetComponent inc : vs.getCompose().getInclude()) {
      if (system.equals(inc.getSystem())) {
        for (ValueSet.ConceptReferenceComponent cc : inc.getConcept()) {
          if (cc.getCode().equals(code)) {
            return cc;
          }
        }
      }
    }
    return null;
  }


  private String generateVersionSummary(String v, String n) throws IOException {

    String ver = VersionUtilities.versionFromCode(v);

    XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
    div.h4().tx("Summary: "+n+" resources in "+v.toUpperCase());
    XhtmlNode tbl = null;
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (n.equals(r.fhirType())) {
          if (r.getOtherVersions().get(ver+"-"+n) != null && !r.getOtherVersions().get(ver+"-"+n).log.isEmpty()) {
            if (tbl == null) {
              tbl = div.table("grid");
              XhtmlNode tr = tbl.tr();
              tr.th().tx("Resource");
              tr.th().tx("Action");
              tr.th().tx("Notes");
            }
            XhtmlNode tr = tbl.tr();

            tr.td().ah(n+"-"+r.getId()+".html").tx(r.getId());
            FetchedResource.AlternativeVersionResource vv = r.getOtherVersions().get(ver+"-"+n);
            if (vv == null) {
              tr.td().tx("Unchanged");
              tr.td().tx("");
            } else {
              if (vv.getResource() == null) {
                tr.td().tx("Removed");
              } else if (vv.getLog().isEmpty()) {
                tr.td().tx("Unchanged");
              } else {
                tr.td().tx("Changed");
              }
              XhtmlNode ul = tr.td().ul();
              for (ProfileVersionAdaptor.ConversionMessage msg : vv.getLog()) {
                switch (msg.getStatus()) {
                  case ERROR:
                    ul.li().span().style("padding: 4px; color: maroon").tx(msg.getMessage());
                    break;
                  case NOTE:
                    //                ul.li().span().style("padding: 4px; background-color: #fadbcf").tx(msg.getMessage());
                    break;
                  case WARNING:
                  default:
                    ul.li().tx(msg.getMessage());
                }
              }
            }
          }
        }
      }
    }
    Set<String> ids = pf.otherVersionAddedResources.get(ver+"-"+n);
    if (ids != null) {
      for (String id : ids) {
        if (tbl == null) {
          tbl = div.table("grid");
          XhtmlNode tr = tbl.tr();
          tr.th().tx("Resource");
          tr.th().tx("Action");
          tr.th().tx("Notes");
        }
        XhtmlNode tr = tbl.tr();
        tr.td().tx(id);
        tr.td().tx("Added");
        tr.td().tx("Not present in "+v);
      }
    }

    if (tbl == null) {
      div.tx("No resources found");
    }
    return new XhtmlComposer(false, true).compose(div.getChildNodes());
  }

  private void cleanOutput(String folder) throws IOException {
    for (File f : new File(folder).listFiles()) {
      cleanOutputFile(f);
    }
  }


  public void cleanOutputFile(File f) {
    // Lloyd: this was changed from getPath to getCanonicalPath, but
    // Grahame: changed it back because this was achange that broke everything, and with no reason provided
    if (!isValidFile(f.getPath())) {
      if (!f.isDirectory()) {
        f.delete();
      }
    }
  }

  private void templateBeforeGenerate() throws IOException, FHIRException {
    if (pf.template != null) {
      logMessage("Run Template");
      TimeTracker.Session tts = pf.tt.start("template");
      List<String> newFileList = new ArrayList<String>();
      checkOutcomes(pf.template.beforeGenerateEvent(pf.publishedIg, pf.tempDir, pf.otherFilesRun, newFileList, allLangs()));
      for (String newFile: newFileList) {
        if (!newFile.isEmpty()) {
          try {
            FetchedFile f = this.pf.fetcher.fetch(Utilities.path(this.pf.repoRoot, newFile));
            String dir = FileUtilities.getDirectoryForFile(f.getPath());
            if (this.pf.tempDir.startsWith("/var") && dir.startsWith("/private/var")) {
              dir = dir.substring(8);
            }
            String relative = this.pf.tempDir.length() > dir.length() ? "" : dir.substring(this.pf.tempDir.length());
            if (relative.length() > 0)
              relative = relative.substring(1);
            f.setRelativePath(f.getPath().substring( FileUtilities.getDirectoryForFile(f.getPath()).length()+1));
            PreProcessInfo ppinfo = new PreProcessInfo(null, relative);
            loadPrePage(f, ppinfo);
          } catch (Exception e) {
            throw new FHIRException(e.getMessage());
          }
        }
      }
      pf.igPages.clear();
      if (pf.publishedIg.getDefinition().hasPage()) {
        loadIgPages(pf.publishedIg.getDefinition().getPage(), pf.igPages);
      }
      tts.end();
      logDebugMessage(LogCategory.PROGRESS, "Template Done");
    }
    cleanUpExtensions(pf.publishedIg);
  }


  private void genBasePages() throws IOException, Exception {
    String json;
    if (pf.publishedIg.getDefinition().hasPage()) {
      JsonObject pages = new JsonObject();
      addPageData(pages, pf.publishedIg.getDefinition().getPage(), "0", "", new HashMap<>());
      JsonProperty priorEntry = null;
      for (JsonProperty entry: pages.getProperties()) {
        if (priorEntry!=null) {
          String priorPageUrl = priorEntry.getName();
          String currentPageUrl = entry.getName();
          JsonObject priorPageData = (JsonObject) priorEntry.getValue();
          JsonObject currentPageData = (JsonObject) entry.getValue();
          priorPageData.add("next", currentPageUrl);
          currentPageData.add("previous", priorPageUrl);
        }
        priorEntry = entry;
      }
      json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(pages, true);
      FileUtilities.stringToFile(json, Utilities.path(pf.tempDir, "_data", "pages.json"));

      createToc();
      if (pf.htmlTemplate != null || pf.mdTemplate != null) {
        applyPageTemplate(pf.htmlTemplate, pf.mdTemplate, pf.publishedIg.getDefinition().getPage());
      }
    }
  }


  private void addPageData(JsonObject pages, ImplementationGuide.ImplementationGuideDefinitionPageComponent page, String label, String breadcrumb, Map<String, String> breadcrumbs) throws FHIRException, IOException {
    if (!page.hasName()) {
      pf.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.REQUIRED, "Base IG resource", "The page \""+page.getTitle()+"\" is missing a name/source element", ValidationMessage.IssueSeverity.ERROR));
    } else {
      addPageData(pages, page, page.getName(), page.getTitle(), label, breadcrumb, breadcrumbs);
    }
  }


  private void applyPageTemplate(String htmlTemplate, String mdTemplate, ImplementationGuide.ImplementationGuideDefinitionPageComponent page) throws Exception {
    String p = page.getName();
    String sourceName = null;
    String template = null;
    if (htmlTemplate != null && page.getGeneration() == ImplementationGuide.GuidePageGeneration.HTML  && !pf.relativeNames.keySet().contains(p) && p != null && p.endsWith(".html")) {
      sourceName = p.substring(0, p.indexOf(".html")) + ".xml";
      template = htmlTemplate;
    } else if (mdTemplate != null && page.getGeneration() == ImplementationGuide.GuidePageGeneration.MARKDOWN  && !pf.relativeNames.keySet().contains(p) && p != null && p.endsWith(".html")) {
      sourceName = p.substring(0, p.indexOf(".html")) + ".md";
      template = mdTemplate;
    }
    if (sourceName!=null) {
      String sourcePath = Utilities.path("_includes", sourceName);
      if (!pf.relativeNames.keySet().contains(sourcePath) && !sourceName.equals("toc.xml")) {
        throw new Exception("Template based HTML file " + p + " is missing source file " + sourceName);
      }
      FetchedFile f = this.pf.relativeNames.get(sourcePath);
      if (isNewML()) {
        String targetPath = Utilities.path(this.pf.tempDir, p);
        if (f==null) { // toc.xml
          checkMakeFile(makeLangRedirect(p), targetPath, this.pf.otherFilesRun);
        } else {
          checkMakeFile(makeLangRedirect(p), targetPath, f.getOutputNames());
        }
        for (String l : allLangs()) {
          String s = "---\r\n---\r\n{% include " + template + " lang='" + l + "' %}";
          targetPath = Utilities.path(this.pf.tempDir, l, p);
          FileUtilities.stringToFile(s, targetPath);
          if (f==null) { // toc.xml
            checkMakeFile(s.getBytes(), targetPath, this.pf.otherFilesRun);
          } else {
            checkMakeFile(s.getBytes(), targetPath, f.getOutputNames());
          }
        }
      } else {
        String s = "---\r\n---\r\n{% include " + template + " %}";
        String targetPath = Utilities.path(this.pf.tempDir, p);
        FileUtilities.stringToFile(s, targetPath);
        if (f==null) { // toc.xml
          checkMakeFile(s.getBytes(), targetPath, this.pf.otherFilesRun);
        } else {
          checkMakeFile(s.getBytes(), targetPath, f.getOutputNames());
        }
      }
    }

    for (ImplementationGuide.ImplementationGuideDefinitionPageComponent childPage : page.getPage()) {
      applyPageTemplate(htmlTemplate, mdTemplate, childPage);
    }
  }


  private void createToc() throws IOException, FHIRException {
    createToc(null, null, null);
  }

  private void createToc(ImplementationGuide.ImplementationGuideDefinitionPageComponent insertPage, String insertAfterName, String insertOffset) throws IOException, FHIRException {
    String s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><div style=\"col-12\"><table style=\"border:0px;font-size:11px;font-family:verdana;vertical-align:top;\" cellpadding=\"0\" border=\"0\" cellspacing=\"0\"><tbody>";
    s = s + createTocPage(pf.publishedIg.getDefinition().getPage(), insertPage, insertAfterName, insertOffset, null, "", "0", false, "", 0, null);
    s = s + "</tbody></table></div>";
    FileUtilities.stringToFile(s, Utilities.path(pf.tempDir, "_includes", "toc.xml"));
    if (isNewML()) {
      for (String lang : allLangs()) {
        s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><div style=\"col-12\"><table style=\"border:0px;font-size:11px;font-family:verdana;vertical-align:top;\" cellpadding=\"0\" border=\"0\" cellspacing=\"0\"><tbody>";
        s = s + createTocPage(pf.publishedIg.getDefinition().getPage(), insertPage, insertAfterName, insertOffset, null, "", "0", false, "", 0, lang);
        s = s + "</tbody></table></div>";
        FileUtilities.stringToFile(s, Utilities.path(pf.tempDir, "_includes", lang, "toc.xml"));
      }
    }
  }

  private String createTocPage(ImplementationGuide.ImplementationGuideDefinitionPageComponent page, ImplementationGuide.ImplementationGuideDefinitionPageComponent insertPage, String insertAfterName, String insertOffset, String currentOffset, String indents, String label, boolean last, String idPrefix, int position, String lang) throws FHIRException {
    if (position > 222) {
      position = 222;
      if (!pf.tocSizeWarning) {
        System.out.println("Table of contents has a section with more than 222 entries.  Collapsing will not work reliably");
        pf.tocSizeWarning = true;
      }
    }
    String id = idPrefix + (char)(position+33);
    String s = "<tr style=\"border:0px;padding:0px;vertical-align:top;background-color:inherit;\" id=\"" + Utilities.escapeXml(id) + "\">";
    s = s + "<td style=\"vertical-align:top;text-align:var(--ig-left,left);background-color:inherit;padding:0px 4px 0px 4px;white-space:nowrap;background-image:url(tbl_bck0.png)\" class=\"hierarchy\">";
    s = s + "<img style=\"background-color:inherit\" alt=\".\" class=\"hierarchy\" src=\"tbl_spacer.png\"/>";
    s = s + indents;
    if (!label.equals("0") && !page.hasPage()) {
      if (last)
        s = s + "<img style=\"background-color:inherit\" alt=\".\" class=\"hierarchy\" src=\"tbl_vjoin_end.png\"/>";
      else
        s = s + "<img style=\"background-color:inherit\" alt=\".\" class=\"hierarchy\" src=\"tbl_vjoin.png\"/>";
    }
    // lloyd check
    if (page.hasPage() && !label.equals("0"))
      if (last)
        s = s + "<img onClick=\"tableRowAction(this)\" src=\"tbl_vjoin_end-open.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/>";
      else
        s = s + "<img onClick=\"tableRowAction(this)\" src=\"tbl_vjoin-open.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/>";
    if (page.hasPage())
      s = s + "<img style=\"background-color:inherit\" alt=\".\" class=\"hierarchy\" src=\"icon_page-child.gif\"/>";
    else
      s = s + "<img style=\"background-color:inherit\" alt=\".\" class=\"hierarchy\" src=\"icon_page.gif\"/>";
    if (page.hasName()) {
      s = s + "<a title=\"" + Utilities.escapeXml(page.getTitle()) + "\" href=\"" + (currentOffset!=null ? currentOffset + "/" : "") + page.getName() +"\"> " + label + " " + Utilities.escapeXml(page.getTitle()) + "</a></td></tr>";
    } else {
      s = s + "<a title=\"" + Utilities.escapeXml(page.getTitle()) + "\"> " + label + " " + Utilities.escapeXml(page.getTitle()) + "</a></td></tr>";
    }

    int total = page.getPage().size();
    int i = 1;
    for (ImplementationGuide.ImplementationGuideDefinitionPageComponent childPage : page.getPage()) {
      String newIndents = indents;
      if (!label.equals("0")) {
        if (last)
          newIndents = newIndents + "<img style=\"background-color:inherit\" alt=\".\" class=\"hierarchy\" src=\"tbl_blank.png\"/>";
        else
          newIndents = newIndents + "<img style=\"background-color:inherit\" alt=\".\" class=\"hierarchy\" src=\"tbl_vline.png\"/>";
      }
      if (insertAfterName!=null && childPage.getName().equals(insertAfterName)) {
        total++;
      }

      s = s + createTocPage(childPage, insertPage, insertAfterName, insertOffset, currentOffset, newIndents, (label.equals("0") ? "" : label+".") + Integer.toString(i), i==total, id, i, lang);
      i++;
      if (insertAfterName!=null && childPage.getName().equals(insertAfterName)) {
        s = s + createTocPage(insertPage, null, null, "", insertOffset, newIndents, (label.equals("0") ? "" : label+".") + Integer.toString(i), i==total, id, i, lang);
        i++;
      }
    }
    return s;
  }


  private byte[] makeLangRedirect(String p) {
    StringBuilder b  = new StringBuilder();
    b.append("<html><body>\r\n");
    b.append("<!--ReleaseHeader--><p id=\"publish-box\">Publish Box goes here</p><!--EndReleaseHeader-->\r\n");
    b.append("<script type=\"text/javascript\">\r\n");
    b.append("// "+ HierarchicalTableGenerator.uuid+"\r\n");
    b.append("langs=[");
    boolean first = true;
    for (String l : allLangs()) {
      if (!first)
        b.append(",");
      first = false;
      b.append("\""+l+"\"");
    }
    b.append("]\r\n</script>\r\n");
    b.append("<script type=\"text/javascript\" src=\"{{site.data.info.assets}}assets/js/lang-redirects.js\"></script>\r\n"
            + "</body></html>\r\n");
    return ("---\r\n---\r\n"+b.toString()).getBytes(StandardCharsets.UTF_8);
  }

  private String breadCrumbForPage(ImplementationGuide.ImplementationGuideDefinitionPageComponent page, boolean withLink) throws FHIRException {
    if (withLink) {
      return "<li><a href='" + page.getName() + "'><b>" + Utilities.escapeXml(page.getTitle()) + "</b></a></li>";
    } else {
      return "<li><b>" + Utilities.escapeXml(page.getTitle()) + "</b></li>";
    }
  }

  private Map<String, String> getLangTitles(StringType titleElement, String description) {
    Map<String, String> map = new HashMap<String, String>();
    for (String l : allLangs()) {
      String title = pf.langUtils.getTranslationOrBase(titleElement, l);
      if (!description.isEmpty()) {
        title += " - " + pf.langUtils.getTranslationOrBase(new StringType(description), l);
      }
      map.put(l, title);
    }
    return map;
  }

  private void addPageData(JsonObject pages, ImplementationGuide.ImplementationGuideDefinitionPageComponent page, String source, String title, String label, String breadcrumb, Map<String, String> breadcrumbs) throws FHIRException, IOException {
    FetchedResource r = pf.resources.get(source);
    if (r==null) {
      String fmm = ExtensionUtilities.readStringExtension(page, ExtensionDefinitions.EXT_FMM_LEVEL);
      String status = ExtensionUtilities.readStringExtension(page, ExtensionDefinitions.EXT_STANDARDS_STATUS);
      String normVersion = ExtensionUtilities.readStringExtension(page, ExtensionDefinitions.EXT_NORMATIVE_VERSION);
      addPageDataRow(pages, source, title, getLangTitles(page.getTitleElement(), ""), label + (page.hasPage() ? ".0" : ""), fmm, status, normVersion, breadcrumb + breadCrumbForPage(page, false), addToBreadcrumbs(breadcrumbs, page, false), null, null, null, page);
    } else {
      Map<String, String> vars = makeVars(r);
      String outputName = determineOutputName(pf.igpkp.getProperty(r, "base"), r, vars, null, "");
      addPageDataRow(pages, outputName, title, getLangTitles(page.getTitleElement(), ""), label, breadcrumb + breadCrumbForPage(page, false), breadcrumbs, r.getStatedExamples(), r.getFoundTestPlans(), r.getFoundTestScripts(), page);
      //      addPageDataRow(pages, source, title, label, breadcrumb + breadCrumbForPage(page, false), r.getStatedExamples());
      for (String templateName: pf.extraTemplateList) {
        if (r.getConfig() !=null && r.getConfig().get("template-"+templateName)!=null && !r.getConfig().get("template-"+templateName).asString().isEmpty()) {
          if (templateName.equals("format")) {
            String templateDesc = pf.extraTemplates.get(templateName);
            for (String format: pf.template.getFormats()) {
              String formatTemplateDesc = templateDesc.replace("FMT", format.toUpperCase());
              if (wantGen(r, format)) {
                outputName = determineOutputName(pf.igpkp.getProperty(r, "format"), r, vars, format, "");

                addPageDataRow(pages, outputName, page.getTitle() + " - " + formatTemplateDesc, getLangTitles(page.getTitleElement(), formatTemplateDesc), label, breadcrumb + breadCrumbForPage(page, false), addToBreadcrumbs(breadcrumbs, page, false), null, null, null, page);
              }
            }
          } else if (page.hasGeneration() && page.getGeneration().equals(ImplementationGuide.GuidePageGeneration.GENERATED) /*page.getKind().equals(ImplementationGuide.GuidePageKind.RESOURCE) */) {
            boolean showPage = true;
            if (pf.historyTemplates.contains(templateName) && r.getAudits().isEmpty())
              showPage = false;
            if (pf.exampleTemplates.contains(templateName) && r.getStatedExamples().isEmpty())
              showPage = false;
            if (showPage) {
              String templateDesc = pf.extraTemplates.get(templateName);
              outputName = pf.igpkp.getProperty(r, templateName);
              if (outputName==null)
                throw new FHIRException("Error in publisher template.  Unable to find file-path property " + templateName + " for resource type " + r.fhirType() + " when property template-" + templateName + " is defined.");
              outputName = pf.igpkp.doReplacements(outputName, r, vars, "");
              addPageDataRow(pages, outputName, page.getTitle() + " - " + templateDesc, getLangTitles(page.getTitleElement(), ""), label, breadcrumb + breadCrumbForPage(page, false), addToBreadcrumbs(breadcrumbs, page, false), null, null, null, page);
            }
          }
        }
      }
    }

    int i = 1;
    for (ImplementationGuide.ImplementationGuideDefinitionPageComponent childPage : page.getPage()) {
      addPageData(pages, childPage, (label.equals("0") ? "" : label+".") + Integer.toString(i), breadcrumb + breadCrumbForPage(page, true), addToBreadcrumbs(breadcrumbs, page, true));
      i++;
    }
  }


  private Map<String, String> addToBreadcrumbs(Map<String, String> breadcrumbs, ImplementationGuide.ImplementationGuideDefinitionPageComponent page, boolean withLink) {
    Map<String, String> map = new HashMap<>();
    for (String l : allLangs()) {
      String s = breadcrumbs.containsKey(l) ? breadcrumbs.get(l) : "";
      String t = Utilities.escapeXml(pf.langUtils.getTranslationOrBase(page.getTitleElement(), l)) ;
      if (withLink) {
        map.put(l, s + "<li><a href='" + page.getName() + "'><b>" + t+ "</b></a></li>");
      } else {
        map.put(l, s + "<li><b>" + t + "</b></li>");
      }
    }
    return map;

  }

  private void addPageDataRow(JsonObject pages, String url, String title, Map<String, String> titles, String label, String breadcrumb, Map<String, String> breadcrumbs, Set<FetchedResource> examples, Set<FetchedResource> testplans, Set<FetchedResource> testscripts, ImplementationGuide.ImplementationGuideDefinitionPageComponent page) throws FHIRException, IOException {
    addPageDataRow(pages, url, title, titles, label, null, null, null, breadcrumb, breadcrumbs, examples, testplans, testscripts, page);
  }

  private void addPageDataRow(JsonObject pages, String url, String title, Map<String, String> titles, String label, String fmm, String status, String normVersion, String breadcrumb, Map<String, String> breadcrumbs, Set<FetchedResource> examples, Set<FetchedResource> testplans, Set<FetchedResource> testscripts, ImplementationGuide.ImplementationGuideDefinitionPageComponent page) throws FHIRException, IOException {
    JsonObject jsonPage = new JsonObject();
    registerPageFile(pages, url, jsonPage);
    jsonPage.add("title", title);
    JsonObject jsonTitle = new JsonObject();
    jsonPage.add("titlelang", jsonTitle);
    for (String l : allLangs()) {
      jsonTitle.add(l, titles.get(l));
    }
    jsonPage.add("label", label);
    jsonPage.add("breadcrumb", breadcrumb);
    JsonObject jsonBreadcrumb = new JsonObject();
    jsonPage.add("breadcrumblang", jsonBreadcrumb);
    for (String l : allLangs()) {
      String tBreadcrumb = breadcrumbs.get(l);
      if (tBreadcrumb.endsWith("</a></li>"))
        tBreadcrumb += "<li><b>" + titles.get(l) + "</b></li>";
      jsonBreadcrumb.add(l, tBreadcrumb);
    }
    if (fmm != null)
      jsonPage.add("fmm", fmm);
    if (status != null) {
      jsonPage.add("status", status);
      if (normVersion != null)
        jsonPage.add("normativeVersion", normVersion);
    }
    if (fmm != null || status != null) {
      String statusClass = StatusRenderer.getColor("Active", status, fmm);
      jsonPage.add("statusclass", statusClass);
    }

    String baseUrl = url;

    if (baseUrl.indexOf(".html") > 0) {
      baseUrl = baseUrl.substring(0, baseUrl.indexOf(".html"));
    }

    for (String pagesDir: pf.pagesDirs) {
      String contentFile = pagesDir + File.separator + "_includes" + File.separator + baseUrl + "-intro.xml";
      if (new File(contentFile).exists()) {
        registerSubPageFile(jsonPage, url, "intro", baseUrl+"-intro.xml");
        registerSubPageFile(jsonPage, url, "intro-type", "xml");
      } else {
        contentFile = pagesDir + File.separator + "_includes" + File.separator + baseUrl + "-intro.md";
        if (new File(contentFile).exists()) {
          registerSubPageFile(jsonPage, url, "intro", baseUrl+"-intro.md");
          registerSubPageFile(jsonPage, url, "intro-type", "md");
        }
      }

      contentFile = pagesDir + File.separator + "_includes" + File.separator + baseUrl + "-notes.xml";
      if (new File(contentFile).exists()) {
        registerSubPageFile(jsonPage, url, "notes", baseUrl+"-notes.xml");
        registerSubPageFile(jsonPage, url, "notes-type", "xml");
      } else {
        contentFile = pagesDir + File.separator + "_includes" + File.separator + baseUrl + "-notes.md";
        if (new File(contentFile).exists()) {
          registerSubPageFile(jsonPage, url, "notes", baseUrl+"-notes.md");
          registerSubPageFile(jsonPage, url, "notes-type", "md");
        }
      }
    }

    for (String prePagesDir: pf.prePagesDirs) {
      PreProcessInfo ppinfo = pf.preProcessInfo.get(prePagesDir);
      String baseFile = prePagesDir + File.separator;
      if (ppinfo.getRelativePath().equals("")) {
        baseFile = baseFile + "_includes" + File.separator;
      } else if (!ppinfo.getRelativePath().equals("_includes")) {
        continue;
      }
      baseFile = baseFile + baseUrl;
      String contentFile = baseFile + "-intro.xml";
      if (new File(contentFile).exists()) {
        registerSubPageFile(jsonPage, url, "intro", baseUrl+"-intro.xml");
        registerSubPageFile(jsonPage, url, "intro-type", "xml");
      } else {
        contentFile = baseFile + "-intro.md";
        if (new File(contentFile).exists()) {
          registerSubPageFile(jsonPage, url, "intro", baseUrl+"-intro.md");
          registerSubPageFile(jsonPage, url, "intro-type", "md");
        }
      }

      contentFile = baseFile + "-notes.xml";
      if (new File(contentFile).exists()) {
        registerSubPageFile(jsonPage, url, "notes", baseUrl+"-notes.xml");
        registerSubPageFile(jsonPage, url, "notes-type", "xml");
      } else {
        contentFile = baseFile + "-notes.md";
        if (new File(contentFile).exists()) {
          registerSubPageFile(jsonPage, url, "notes", baseUrl+"-notes.md");
          registerSubPageFile(jsonPage, url, "notes-type", "md");
        }
      }
    }

    if (examples != null) {
      JsonArray exampleArray = new JsonArray();
      jsonPage.add("examples", exampleArray);

      TreeSet<ImplementationGuide.ImplementationGuideDefinitionPageComponent> examplePages = new TreeSet<ImplementationGuide.ImplementationGuideDefinitionPageComponent>(new ImplementationGuideDefinitionPageComponentComparator());
      for (FetchedResource exampleResource: examples) {
        ImplementationGuide.ImplementationGuideDefinitionPageComponent page2 = pageForFetchedResource(exampleResource);
        if (page2!=null)
          examplePages.add(page2);
        // else
        //   throw new Error("Unable to find page for resource "+ exampleResource.getId());
      }
      for (ImplementationGuide.ImplementationGuideDefinitionPageComponent examplePage : examplePages) {
        JsonObject exampleItem = new JsonObject();
        exampleArray.add(exampleItem);
        exampleItem.add("url", examplePage.getName());
        exampleItem.add("title", examplePage.getTitle());

        jsonTitle = new JsonObject();
        exampleItem.add("titlelang", jsonTitle);
        for (String l : allLangs()) {
          jsonTitle.add(l, pf.rcLangs.get(l).getTranslated(examplePage.getTitleElement()));
        }
      }
    }

    if (testplans != null) {
      JsonArray testplanArray = new JsonArray();
      jsonPage.add("testplans", testplanArray);

      TreeSet<ImplementationGuide.ImplementationGuideDefinitionPageComponent> testplanPages = new TreeSet<ImplementationGuide.ImplementationGuideDefinitionPageComponent>(new ImplementationGuideDefinitionPageComponentComparator());
      for (FetchedResource testplanResource: testplans) {
        ImplementationGuide.ImplementationGuideDefinitionPageComponent page2 = pageForFetchedResource(testplanResource);
        if (page2!=null)
          testplanPages.add(page2);
      }
      for (ImplementationGuide.ImplementationGuideDefinitionPageComponent testplanPage : testplanPages) {
        JsonObject testplanItem = new JsonObject();
        testplanArray.add(testplanItem);
        testplanItem.add("url", testplanPage.getName());
        testplanItem.add("title", testplanPage.getTitle());
      }
    }

    if (testscripts != null) {
      JsonArray testscriptArray = new JsonArray();
      jsonPage.add("testscripts", testscriptArray);

      TreeSet<ImplementationGuide.ImplementationGuideDefinitionPageComponent> testscriptPages = new TreeSet<ImplementationGuide.ImplementationGuideDefinitionPageComponent>(new ImplementationGuideDefinitionPageComponentComparator());
      for (FetchedResource testscriptResource: testscripts) {
        ImplementationGuide.ImplementationGuideDefinitionPageComponent page2 = pageForFetchedResource(testscriptResource);
        if (page2!=null)
          testscriptPages.add(page2);
      }
      for (ImplementationGuide.ImplementationGuideDefinitionPageComponent testscriptPage : testscriptPages) {
        JsonObject testscriptItem = new JsonObject();
        testscriptArray.add(testscriptItem);
        testscriptItem.add("url", testscriptPage.getName());
        testscriptItem.add("title", testscriptPage.getTitle());
      }
    }

    if (isNewML()) {
      String p = page.getName();
      String sourceName = null;
      if (pf.htmlTemplate != null && page.getGeneration() == ImplementationGuide.GuidePageGeneration.HTML  && !pf.relativeNames.keySet().contains(p) && p != null && p.endsWith(".html")) {
        sourceName = p.substring(0, p.indexOf(".html")) + ".xml";
      } else if (pf.mdTemplate != null && page.getGeneration() == ImplementationGuide.GuidePageGeneration.MARKDOWN  && !pf.relativeNames.keySet().contains(p) && p != null && p.endsWith(".html")) {
        sourceName = p.substring(0, p.indexOf(".html")) + ".md";
      }
      if (sourceName!=null) {
        String sourcePath = Utilities.path("_includes", sourceName);
        FetchedFile f = this.pf.relativeNames.get(sourcePath);
        if (f != null) {
          for (String l : allLangs()) {
            jsonPage.forceObject("translated").add(l, this.pf.defaultTranslationLang.equals(l) || f.getTranslated(l));
          }
        }
      }
    }
  }

  private void registerSubPageFile(JsonObject jsonPage, String url, String name, String value) {
    if (jsonPage.has(name) && !value.equals(jsonPage.asString("name"))) {
      pf.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.REQUIRED, "ToC", "Attempt to register a page file '"+name+"' more than once for the page "+url+". New Value '"+value+"', existing value '"+jsonPage.asString(name)+"'",
              ValidationMessage.IssueSeverity.ERROR).setRuleDate("2022-12-01"));
    }
    jsonPage.set(name, value);
  }

  private void registerPageFile(JsonObject pages, String url, JsonObject jsonPage) {
    if (pages.has(url)) {
      pf.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.REQUIRED, "ToC", "The ToC contains the page "+url+" more than once", ValidationMessage.IssueSeverity.ERROR).setRuleDate("2022-12-01"));
    }
    pages.set(url, jsonPage);
  }


  private void templateBeforeJekyll() throws IOException, FHIRException {
    if (pf.template != null) {
      TimeTracker.Session tts = pf.tt.start("template");
      List<String> newFileList = new ArrayList<String>();
      checkOutcomes(pf.template.beforeJekyllEvent(pf.publishedIg, newFileList));
      tts.end();
    }
  }


  private void fixSearchForm() throws IOException {

    String sfn = Utilities.path(pf.tempDir, "searchform.html");
    if (new File(sfn).exists() ) {
      String sf = FileUtilities.fileToString(sfn);
      sf = sf.replace("{{title}}", pf.publishedIg.present());
      sf = sf.replace("{{url}}", targetUrl());
      FileUtilities.stringToFile(sf, sfn);
    }
  }

  private boolean runTool() throws Exception {
    if (settings.isSimplifierMode()) {
      return true;
    }
    if (settings.isGenerationOff()) {
      FileUtils.copyDirectory(new File(pf.tempDir), new File(pf.outputDir));
      return true;
    }
    return runJekyll();
  }

  public class MyFilterHandler extends OutputStream {

    private byte[] buffer;
    private int length;
    private boolean observedToSucceed = false;

    public MyFilterHandler() {
      buffer = new byte[256];
    }

    public String getBufferString() {
      return new String(this.buffer, 0, length);
    }

    private boolean passJekyllFilter(String s) {
      if (Utilities.noString(s)) {
        return false;
      }
      if (s.contains("Source:")) {
        return true;
      }
      if (s.contains("Liquid Exception:")) {
        return true;
      }
      if (s.contains("Destination:")) {
        return false;
      }
      if (s.contains("Configuration")) {
        return false;
      }
      if (s.contains("Incremental build:")) {
        return false;
      }
      if (s.contains("Auto-regeneration:")) {
        return false;
      }
      if (s.contains("done in")) {
        observedToSucceed = true;
      }
      return true;
    }

    @Override
    public void write(int b) throws IOException {
      buffer[length] = (byte) b;
      length++;
      if (b == 10) { // eoln
        String s = new String(buffer, 0, length);
        if (passJekyllFilter(s)) {
          log("Jekyll: "+s.trim());
        }
        length = 0;
      }
    }
  }

  private boolean runJekyll() throws IOException, InterruptedException {
    TimeTracker.Session tts = pf.tt.start("jekyll");

    DefaultExecutor exec = new DefaultExecutor();
    exec.setExitValue(0);
    MyFilterHandler pumpHandler = new MyFilterHandler();
    PumpStreamHandler pump = new PumpStreamHandler(pumpHandler, pumpHandler);
    exec.setStreamHandler(pump);
    exec.setWorkingDirectory(new File(pf.tempDir));
    ExecuteWatchdog watchdog = new ExecuteWatchdog(pf.jekyllTimeout);
    exec.setWatchdog(watchdog);

    try {
      log("Run jekyll: "+ pf.jekyllCommand +" build --destination \""+ pf.outputDir +"\" (in folder "+ pf.tempDir +")");
      if (SystemUtils.IS_OS_WINDOWS) {
        log("Due to a known issue, Jekyll errors are lost between Java and Ruby in windows systems.");
        log("If the build process hangs at this point, you have to go to a");
        log("command prompt, and then run these two commands:");
        log("");
        log("cd "+ pf.tempDir);
        log(pf.jekyllCommand +" build --destination \""+ pf.outputDir +"\"");
        log("");
        log("and then investigate why Jekyll has failed");
      }
      log("Troubleshooting Note: usual cases for Jekyll to fail are:");
      log("* A failure to produce a fragment that is already logged in the output above");
      log("* A reference to a manually edited file that hasn't been provided");
      if (SystemUtils.IS_OS_WINDOWS) {
        final String enclosedOutputDir = "\"" + pf.outputDir + "\"";
        final CommandLine commandLine = new CommandLine("cmd")
                .addArgument( "/C")
                .addArgument(pf.jekyllCommand)
                .addArgument("build")
                .addArgument("--destination")
                .addArgument(enclosedOutputDir);
        exec.execute(commandLine);
      } else if (FhirSettings.hasRubyPath()) {
        ProcessBuilder processBuilder = new ProcessBuilder(new String("bash -c "+ pf.jekyllCommand));
        Map<String, String> env = processBuilder.environment();
        Map<String, String> vars = new HashMap<>();
        vars.putAll(env);
        String path = FhirSettings.getRubyPath()+":"+env.get("PATH");
        vars.put("PATH", path);
        if (FhirSettings.getGemPath() != null) {
          vars.put("GEM_PATH", FhirSettings.getGemPath());
        }
        CommandLine commandLine = new CommandLine("bash").addArgument("-c").addArgument(pf.jekyllCommand +" build --destination "+ pf.outputDir, false);
        exec.execute(commandLine, vars);
      } else {
        final String enclosedOutputDir = "\"" + pf.outputDir + "\"";
        final CommandLine commandLine = new CommandLine(pf.jekyllCommand)
                .addArgument("build")
                .addArgument("--destination")
                .addArgument(enclosedOutputDir);
        exec.execute(commandLine);
      }
      tts.end();
    } catch (IOException ioex) {
      tts.end();
      if (pumpHandler.observedToSucceed) {
        if (watchdog.killedProcess()) {
          log("Jekyll timeout exceeded: " + Long.toString(pf.jekyllTimeout /1000) + " seconds");
        }
        log("Jekyll claimed to succeed, but returned an error. Proceeding anyway");
      } else {
        log("Jekyll has failed. Complete output from running Jekyll: " + pumpHandler.getBufferString());
        if (watchdog.killedProcess()) {
          log("Jekyll timeout exceeded: " + Long.toString(pf.jekyllTimeout /1000) + " seconds");
        } else {
          log("Note: Check that Jekyll is installed correctly");
        }
        throw ioex;
      }
    }
    return true;
  }

  private void dumpVars() {
    log("---- Props -------------");
    Properties properties = System.getProperties();
    properties.forEach((k, v) -> log((String) k + ": "+ v));
    log("---- Vars -------------");
    System.getenv().forEach((k, v) -> {
      log(k + ":" + v);
    });
    log("-----------------------");
  }


  private void addFileToNpm(NPMPackageGenerator.Category other, String name, byte[] cnt) throws IOException {
    pf.npm.addFile(other, name, cnt);
    for (NPMPackageGenerator vnpm : pf.vnpms.values()) {
      vnpm.addFile(other, name, cnt);
    }
  }

  private void addFileToNpm(String other, String name, byte[] cnt) throws IOException {
    pf.npm.addFile(other, name, cnt);
    for (NPMPackageGenerator vnpm : pf.vnpms.values()) {
      vnpm.addFile(other, name, cnt);
    }
  }

  private File makeSpecFile() throws Exception {
    SpecMapManager map = new SpecMapManager(pf.npmName, pf.npmName +"#"+ pf.version, pf.version, Constants.VERSION, Integer.toString(ToolsVersion.TOOLS_VERSION), pf.getExecTime(), pf.igpkp.getCanonical());
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        String u = this.pf.igpkp.getCanonical()+r.getUrlTail();
        String u2 = this.pf.altCanonical == null ? "" : this.pf.altCanonical +r.getUrlTail();
        String u3 = this.pf.altCanonical == null ? "" : this.pf.altCanonical +"/"+r.getId();
        if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
          String uc = ((CanonicalResource) r.getResource()).getUrl();
          if (uc != null && !(u.equals(uc) || u2.equals(uc) || u3.equals(uc)) && !isListedURLExemption(uc) && !isExampleResource((CanonicalResource) r.getResource()) && this.pf.adHocTmpDir == null) {
            f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, f.getName(), "URL Mismatch "+u+" vs "+uc, ValidationMessage.IssueSeverity.ERROR));
            r.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, f.getName(), "URL Mismatch "+u+" vs "+uc, ValidationMessage.IssueSeverity.ERROR));
          }
          if (uc != null && !u.equals(uc)) {
            map.path(uc, this.pf.igpkp.getLinkFor(r, true));
          }
          String v = ((CanonicalResource) r.getResource()).getVersion();
          if (v != null) {
            map.path(uc + "|" + v, this.pf.igpkp.getLinkFor(r, true));
          }
        }
        map.path(u, this.pf.igpkp.getLinkFor(r, true));
      }
    }
    for (String s : new File(pf.outputDir).list()) {
      if (s.endsWith(".html")) {
        map.target(s);
      }
    }
    File df = File.createTempFile("fhir", "tmp");
    df.deleteOnExit();
    map.save(df.getCanonicalPath());
    return df;
  }

  private void generateZips(File df) throws Exception {
    if (generateExampleZip(Manager.FhirFormat.XML)) {
      generateDefinitions(Manager.FhirFormat.XML, df.getCanonicalPath());
    }
    if (generateExampleZip(Manager.FhirFormat.JSON)) {
      generateDefinitions(Manager.FhirFormat.JSON, df.getCanonicalPath());
    }
    if (supportsTurtle() && generateExampleZip(Manager.FhirFormat.TURTLE)) {
      generateDefinitions(Manager.FhirFormat.TURTLE, df.getCanonicalPath());
    }
    generateExpansions();
    generateValidationPack(df.getCanonicalPath());
    // Create an IG-specific named igpack to make is easy to grab the igpacks for multiple igs without the names colliding (Talk to Lloyd before removing this)
    FileUtils.copyFile(new File(Utilities.path(pf.outputDir, "validator.pack")),new File(Utilities.path(pf.outputDir, "validator-" + pf.sourceIg.getId() + ".pack")));
    generateCsvZip();
    generateExcelZip();
    generateSchematronsZip();
  }


  private String makeTempZip(String ext) throws IOException {
    File tmp = File.createTempFile("fhir", "zip");
    tmp.deleteOnExit();
    if (generateZipByExtension(tmp.getCanonicalPath(), ext)) {
      return tmp.getCanonicalPath();
    } else {
      return null;
    }
  }

  private boolean generateZipByExtension(String path, String ext) throws IOException {
    Set<String> files = new HashSet<String>();
    for (String s : new File(pf.outputDir).list()) {
      if (s.endsWith(ext)) {
        files.add(s);
      }
    }
    if (files.size() == 0) {
      return false;
    }
    ZipGenerator zip = new ZipGenerator(path);
    for (String fn : files) {
      zip.addFileName(fn, Utilities.path(pf.outputDir, fn), false);
    }
    zip.close();
    return true;
  }

  private boolean generateExampleZip(Manager.FhirFormat fmt) throws Exception {
    Set<String> files = new HashSet<String>();
    for (FetchedFile f : pf.fileList) {
      f.start("generateExampleZip");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.isExample()) {
            String fn = Utilities.path(this.pf.outputDir, r.fhirType()+"-"+r.getId()+"."+fmt.getExtension());
            if (new File(fn).exists()) {
              files.add(fn);
            }
          }
        }
      } finally {
        f.finish("generateExampleZip");
      }
    }
    if (!files.isEmpty()) {
      ZipGenerator zip = new ZipGenerator(Utilities.path(pf.outputDir, "examples."+fmt.getExtension()+".zip"));
      for (String fn : files) {
        zip.addFileName(fn.substring(fn.lastIndexOf(File.separator)+1), fn, false);
      }
      zip.close();
    }

    return !files.isEmpty();
  }


  private boolean supportsTurtle() {
    return !Utilities.existsInList(pf.version, "1.0.2", "1.4.0");
  }


  private void generateExpansions() throws FileNotFoundException, IOException {

    if (pf.savingExpansions) {
      Bundle exp = new Bundle();
      exp.setType(Bundle.BundleType.COLLECTION);
      exp.setId(UUID.randomUUID().toString());
      exp.getMeta().setLastUpdated(pf.getExecTime().getTime());
      for (ValueSet vs : pf.expansions) {
        exp.addEntry().setResource(vs).setFullUrl(vs.getUrl());
      }

      new JsonParser().setOutputStyle(IParser.OutputStyle.PRETTY).compose(new FileOutputStream(Utilities.path(pf.outputDir, "expansions.json")), exp);
      new XmlParser().setOutputStyle(IParser.OutputStyle.PRETTY).compose(new FileOutputStream(Utilities.path(pf.outputDir, "expansions.xml")), exp);
      ZipGenerator zip = new ZipGenerator(Utilities.path(pf.outputDir, "expansions.json.zip"));
      zip.addFileName("expansions.json", Utilities.path(pf.outputDir, "expansions.json"), false);
      zip.close();
      zip = new ZipGenerator(Utilities.path(pf.outputDir, "expansions.xml.zip"));
      zip.addFileName("expansions.xml", Utilities.path(pf.outputDir, "expansions.xml"), false);
      zip.close();
    }
  }


  private boolean isListedURLExemption(String uc) {
    return pf.listedURLExemptions.contains(uc);
  }

  private void generateDefinitions(Manager.FhirFormat fmt, String specFile)  throws Exception {
    // public definitions
    Set<FetchedResource> files = new HashSet<FetchedResource>();
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
          files.add(r);
        }
      }
    }
    if (!files.isEmpty()) {
      ZipGenerator zip = new ZipGenerator(Utilities.path(pf.outputDir, "definitions."+fmt.getExtension()+".zip"));
      for (FetchedResource r : files) {
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        if (VersionUtilities.isR3Ver(pf.version)) {
          org.hl7.fhir.dstu3.model.Resource r3 = VersionConvertorFactory_30_50.convertResource(r.getResource());
          if (fmt.equals(Manager.FhirFormat.JSON)) {
            new org.hl7.fhir.dstu3.formats.JsonParser().compose(bs, r3);
          } else if (fmt.equals(Manager.FhirFormat.XML)) {
            new org.hl7.fhir.dstu3.formats.XmlParser().compose(bs, r3);
          } else if (fmt.equals(Manager.FhirFormat.TURTLE)) {
            new org.hl7.fhir.dstu3.formats.RdfParser().compose(bs, r3);
          }
        } else if (VersionUtilities.isR4Ver(pf.version)) {
          org.hl7.fhir.r4.model.Resource r4 = VersionConvertorFactory_40_50.convertResource(r.getResource());
          if (fmt.equals(Manager.FhirFormat.JSON)) {
            new org.hl7.fhir.r4.formats.JsonParser().compose(bs, r4);
          } else if (fmt.equals(Manager.FhirFormat.XML)) {
            new org.hl7.fhir.r4.formats.XmlParser().compose(bs, r4);
          } else if (fmt.equals(Manager.FhirFormat.TURTLE)) {
            new org.hl7.fhir.r4.formats.RdfParser().compose(bs, r4);
          }
        } else if (VersionUtilities.isR4BVer(pf.version)) {
          org.hl7.fhir.r4b.model.Resource r4b = VersionConvertorFactory_43_50.convertResource(r.getResource());
          if (fmt.equals(Manager.FhirFormat.JSON)) {
            new org.hl7.fhir.r4b.formats.JsonParser().compose(bs, r4b);
          } else if (fmt.equals(Manager.FhirFormat.XML)) {
            new org.hl7.fhir.r4b.formats.XmlParser().compose(bs, r4b);
          } else if (fmt.equals(Manager.FhirFormat.TURTLE)) {
            new org.hl7.fhir.r4b.formats.RdfParser().compose(bs, r4b);
          }
        } else if (VersionUtilities.isR2BVer(pf.version)) {
          org.hl7.fhir.dstu2016may.model.Resource r14 = VersionConvertorFactory_14_50.convertResource(r.getResource());
          if (fmt.equals(Manager.FhirFormat.JSON)) {
            new org.hl7.fhir.dstu2016may.formats.JsonParser().compose(bs, r14);
          } else if (fmt.equals(Manager.FhirFormat.XML)) {
            new org.hl7.fhir.dstu2016may.formats.XmlParser().compose(bs, r14);
          } else if (fmt.equals(Manager.FhirFormat.TURTLE)) {
            new org.hl7.fhir.dstu2016may.formats.RdfParser().compose(bs, r14);
          }
        } else if (VersionUtilities.isR2Ver(pf.version)) {
          BaseAdvisor_10_50 advisor = new IGR2ConvertorAdvisor5();
          org.hl7.fhir.dstu2.model.Resource r14 = VersionConvertorFactory_10_50.convertResource(r.getResource(), advisor);
          if (fmt.equals(Manager.FhirFormat.JSON)) {
            new org.hl7.fhir.dstu2.formats.JsonParser().compose(bs, r14);
          } else if (fmt.equals(Manager.FhirFormat.XML)) {
            new org.hl7.fhir.dstu2.formats.XmlParser().compose(bs, r14);
          } else if (fmt.equals(Manager.FhirFormat.TURTLE)) {
            throw new Exception("Turtle is not supported for releases < 3");
          }
        } else {
          if (fmt.equals(Manager.FhirFormat.JSON)) {
            new JsonParser().compose(bs, r.getResource());
          } else if (fmt.equals(Manager.FhirFormat.XML)) {
            new XmlParser().compose(bs, r.getResource());
          } else if (fmt.equals(Manager.FhirFormat.TURTLE)) {
            new RdfParser().compose(bs, r.getResource());
          }
        }
        zip.addBytes(r.fhirType()+"-"+r.getId()+"."+fmt.getExtension(), bs.toByteArray(), false);
      }
      zip.addFileName("spec.internals", specFile, false);
      zip.close();
    }
  }

  private void generateExcelZip()  throws Exception {
    generateZipByExtension(Utilities.path(pf.outputDir, "excels.zip"), ".xlsx");
  }

  private void generateCsvZip()  throws Exception {
    generateZipByExtension(Utilities.path(pf.outputDir, "csvs.zip"), ".csv");
  }

  private void generateSchematronsZip()  throws Exception {
    generateZipByExtension(Utilities.path(pf.outputDir, "schematrons.zip"), ".sch");
  }

  private void generateRegistryUploadZip(String specFile)  throws Exception {
    ZipGenerator zip = new ZipGenerator(Utilities.path(pf.outputDir, "registry.fhir.org.zip"));
    zip.addFileName("spec.internals", specFile, false);
    StringBuilder ri = new StringBuilder();
    ri.append("[registry]\r\n");
    ri.append("toolversion="+getToolingVersion()+"\r\n");
    ri.append("fhirversion="+ pf.version +"\r\n");
    int i = 0;
    for (FetchedFile f : pf.fileList) {
      f.start("generateRegistryUploadZip");
      try {

        for (FetchedResource r : f.getResources()) {
          if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
            try {
              ByteArrayOutputStream bs = new ByteArrayOutputStream();
              org.hl7.fhir.dstu3.model.Resource r3 = VersionConvertorFactory_30_50.convertResource(r.getResource());
              new org.hl7.fhir.dstu3.formats.JsonParser().compose(bs, r3);
              zip.addBytes(r.fhirType()+"-"+r.getId()+".json", bs.toByteArray(), false);
            } catch (Exception e) {
              log("Can't store "+r.fhirType()+"-"+r.getId()+" in R3 format for registry.fhir.org");
              e.printStackTrace();
            }
            i++;
          }
        }
      } finally {
        f.finish("generateRegistryUploadZip");
      }
    }
    ri.append("resourcecount="+Integer.toString(i)+"\r\n");
    zip.addBytes("registry.info",FileUtilities.stringToBytes(ri.toString()), false);
    zip.close();
  }

  private void generateValidationPack(String specFile)  throws Exception {
    String sch = makeTempZip(".sch");
    String js = makeTempZip(".schema.json");
    String shex = makeTempZip(".shex");

    ZipGenerator zip = new ZipGenerator(Utilities.path(pf.outputDir, "validator.pack"));
    zip.addBytes("version.info", makeNewVersionInfo(pf.version), false);
    zip.addFileName("spec.internals", specFile, false);
    for (FetchedFile f : pf.fileList) {
      f.start("generateValidationPack");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            if (VersionUtilities.isR3Ver(this.pf.version)) {
              new org.hl7.fhir.dstu3.formats.JsonParser().compose(bs, VersionConvertorFactory_30_50.convertResource(r.getResource()));
            } else if (VersionUtilities.isR4Ver(this.pf.version)) {
              new org.hl7.fhir.r4.formats.JsonParser().compose(bs, VersionConvertorFactory_40_50.convertResource(r.getResource()));
            } else if (VersionUtilities.isR2BVer(this.pf.version)) {
              new org.hl7.fhir.dstu2016may.formats.JsonParser().compose(bs, VersionConvertorFactory_14_50.convertResource(r.getResource()));
            } else if (VersionUtilities.isR2Ver(this.pf.version)) {
              BaseAdvisor_10_50 advisor = new IGR2ConvertorAdvisor5();
              new org.hl7.fhir.dstu2.formats.JsonParser().compose(bs, VersionConvertorFactory_10_50.convertResource(r.getResource(), advisor));
            } else if (VersionUtilities.isR4BVer(this.pf.version)) {
              new org.hl7.fhir.r4b.formats.JsonParser().compose(bs, VersionConvertorFactory_43_50.convertResource(r.getResource()));
            } else if (VersionUtilities.isR5Plus(this.pf.version)) {
              new JsonParser().compose(bs, r.getResource());
            } else {
              throw new Exception("Unsupported version "+ this.pf.version);
            }
            zip.addBytes(r.fhirType()+"-"+r.getId()+".json", bs.toByteArray(), false);
          }
        }
      } finally {
        f.finish("generateValidationPack");
      }
    }
    if (sch != null) {
      zip.addFileName("schematron.zip", sch, false);
    }
    if (js != null) {
      zip.addFileName("json.schema.zip", sch, false);
    }
    if (shex != null) {
      zip.addFileName("shex.zip", sch, false);
    }
    zip.close();
  }

  private byte[] makeNewVersionInfo(String version) throws IOException {
    String is = "[FHIR]\r\nversion="+version+"\r\n";
    IniFile ini = new IniFile(new ByteArrayInputStream(FileUtilities.stringToBytes(is)));
    ini.setStringProperty("IG", "version", version, null);
    ini.setStringProperty("IG", "date",  new SimpleDateFormat("yyyyMMddhhmmssZ", new Locale("en", "US")).format(pf.getExecTime().getTime()), null);
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    ini.save(b);
    return b.toByteArray();
  }


  private ImplementationGuide.ImplementationGuideDefinitionPageComponent pageForFetchedResource(FetchedResource r) throws FHIRException {
    String key = pf.igpkp.doReplacements(pf.igpkp.getLinkFor(r, false), r, null, null);
    return pf.igPages.get(key);
  }


  private void templateOnCheck() throws IOException, FHIRException {
    if (pf.template != null) {
      TimeTracker.Session tts = pf.tt.start("template");
      checkOutcomes(pf.template.onCheckEvent(pf.publishedIg));
      tts.end();
    }
  }

  private byte[] validationSummaryOO() throws IOException {
    Bundle bnd = new Bundle();
    bnd.setType(Bundle.BundleType.COLLECTION);
    bnd.setTimestamp(new Date());
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        OperationOutcome oo = new OperationOutcome();
        oo.setId(r.fhirType()+"-"+r.getId());
        bnd.addEntry().setFullUrl(this.pf.igpkp.getCanonical()+"/OperationOutcome/"+oo.getId()).setResource(oo);
        for (ValidationMessage vm : r.getErrors()) {
          if (!vm.getLevel().isHint()) {
            oo.addIssue(OperationOutcomeUtilities.convertToIssue(vm, oo));
          }
        }
      }
    }
    return new JsonParser().composeBytes(bnd);
  }

  private byte[] validationSummaryJson() throws UnsupportedEncodingException {
    JsonObject json = new JsonObject();
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        JsonObject rd = new JsonObject();
        json.add(r.fhirType()+"/"+r.getId(), rd);
        createValidationSummary(rd, r);
      }
    }
    return org.hl7.fhir.utilities.json.parser.JsonParser.composeBytes(json);
  }

  private void createValidationSummary(JsonObject fd, FetchedResource r) {
    int e = 0;
    int w = 0;
    int i = 0;
    for (ValidationMessage vm : r.getErrors()) {
      if (vm.getLevel() == ValidationMessage.IssueSeverity.INFORMATION) {
        i++;
      } else if (vm.getLevel() == ValidationMessage.IssueSeverity.WARNING) {
        w++;
      } else {
        e++;
      }
    }
    fd.add("errors", e);
    fd.add("warnings", w);
    //    fd.add("hints", i);
  }

  private void addTestDir(File dir, String t) throws FileNotFoundException, IOException {
    for (File f : dir.listFiles()) {
      if (f.isDirectory()) {
        addTestDir(f, t);
      } else if (!f.getName().equals(".DS_Store")) {
        String s = FileUtilities.getRelativePath(t, dir.getAbsolutePath());
        addFileToNpm(Utilities.noString(s) ?  "tests" : Utilities.path("tests", s), f.getName(), FileUtilities.fileToBytes(f));
      }
    }
  }


  private void saveResolvedUrls(Set<String> urls) throws IOException {
    JsonObject map = new JsonObject();
    for (String url : urls) {
      Resolver.ResourceWithReference link = pf.resolver.resolve(pf.rc, url, null);
      if (link != null) {
        map.add(url, link.getWebPath());
      } else {
        System.out.println("Unresolved URL: "+url);
      }
    }
    String j = org.hl7.fhir.utilities.json.parser.JsonParser.compose(map, false);
    String p = Utilities.path(pf.tempDir, "url-literals.json");
    FileUtilities.stringToFile(j, p);
    pf.otherFilesRun.add(p);
  }

  private void processDataSet(Set<String> urls, DataSetInformation dsi) throws FileNotFoundException, IOException {
    log("Process DataSet '"+dsi.getConfig().asString("name")+"'");

    if (isNewML()) {
      for (String l : allLangs()) {
        ClientSideIndexBuilder ib = new ClientSideIndexBuilder(
                pf.cu, pf.validator.getFHIRPathEngine(), pf.rc.copy(true),
                dsi.getConfig().asString("resourceType"), dsi.getAllSearchParameters(),
                dsi.name(), Utilities.path(pf.rootDir, dsi.getConfig().asString("source")), Utilities.path(pf.tempDir, l));
        ib.build();
        urls.addAll(ib.getUrls());
        pf.otherFilesRun.add(Utilities.path(pf.tempDir, l, dsi.getConfig().asString("name")+"-index.json"));
        pf.otherFilesRun.add(Utilities.path(pf.tempDir, l, dsi.getConfig().asString("name")+"-index.js"));
        pf.otherFilesRun.add(Utilities.path(pf.tempDir, l, dsi.getConfig().asString("name")+"-data.json"));
        pf.otherFilesRun.add(Utilities.path(pf.tempDir, l, dsi.getConfig().asString("name")+"-data.js"));
      }
    } else {
      ClientSideIndexBuilder ib = new ClientSideIndexBuilder(
              pf.cu, pf.validator.getFHIRPathEngine(), pf.rc.copy(true),
              dsi.getConfig().asString("resourceType"), dsi.getSearchParameters(),
              dsi.name(), Utilities.path(pf.rootDir, dsi.getConfig().asString("source")), pf.tempDir);
      ib.build();
      urls.addAll(ib.getUrls());
      pf.otherFilesRun.add(Utilities.path(pf.tempDir, dsi.getConfig().asString("name")+"-index.json"));
      pf.otherFilesRun.add(Utilities.path(pf.tempDir, dsi.getConfig().asString("name")+"-index.js"));
      pf.otherFilesRun.add(Utilities.path(pf.tempDir, dsi.getConfig().asString("name")+"-data.json"));
      pf.otherFilesRun.add(Utilities.path(pf.tempDir, dsi.getConfig().asString("name")+"-data.js"));
    }
  }

  private void processDataSetContent(DataSetInformation t) throws FileNotFoundException, IOException {
    String src = Utilities.path(pf.rootDir, t.getConfig().asString("source"));
    processDataSetFolder(new File(src), src);
  }

  private void processDataSetFolder(File dir, String t) throws FileNotFoundException, IOException {
    for (File f : dir.listFiles()) {
      if (f.isDirectory()) {
        processDataSetFolder(f, t);
      } else if (!f.getName().equals(".DS_Store")) {
        String s = FileUtilities.getRelativePath(t, dir.getAbsolutePath());
        addFileToNpm(Utilities.noString(s) ?  "data" : Utilities.path("data", s), f.getName(), FileUtilities.fileToBytes(f));
      }
    }
  }


  private String checkPlural(String word, int c) {
    return c == 1 ? word : Utilities.pluralizeMe(word);
  }

  public void populateResourceEntry(FetchedResource r, JsonObject item, PublisherUtils.ContainedResourceDetails crd) throws Exception {
    if (r.getResource() instanceof CanonicalResource || (crd!= null && crd.getCanonical() != null)) {
//      item.add("layout-type", "canonical");
      boolean containedCr = crd != null && crd.getCanonical() != null;
      CanonicalResource cr = containedCr ? crd.getCanonical() : (CanonicalResource) r.getResource();
      CanonicalResource pcr = r.getResource() instanceof CanonicalResource ? (CanonicalResource) r.getResource() : null;
      if (crd != null) {
        if (r.getResource() instanceof CanonicalResource)
          item.add("url", ((CanonicalResource)r.getResource()).getUrl()+"#"+crd.getId());
      } else {
        item.add("url", cr.getUrl());
      }
      if (cr.hasIdentifier()) {
        List<String> ids = new ArrayList<String>();
        for (Identifier id : cr.getIdentifier()) {
          if (id.hasValue()) {
            ids.add(pf.dr.displayDataType(id));
          }
        }
        if (!ids.isEmpty())
          item.add("identifiers", String.join(", ", ids));
      }
      if (pcr != null && pcr.hasVersion()) {
        item.add("version", pcr.getVersion());
      }
      if (cr.hasName()) {
        item.add("name", cr.getName());
      }
      if (cr.hasTitle()) {
        item.add("title", cr.getTitle());
        addTranslationsToJson(item, "title", cr.getTitleElement(), false);
      }
      if (cr.hasExperimental()) {
        item.add("experimental", cr.getExperimental());
      }
      if (cr.hasDate()) {
        item.add("date", cr.getDateElement().primitiveValue());
      }
      // status gets overridden later, and it appears in there
      // publisher & description are exposed in domain resource as  'owner' & 'link'
      if (cr.hasDescription()) {
        item.add("description", preProcessMarkdown(cr.getDescription()));
        addTranslationsToJson(item, "description", cr.getDescriptionElement(), true);
      }
      if (cr.hasUseContext() && !containedCr) {
        List<String> contexts = new ArrayList<String>();
        for (UsageContext uc : cr.getUseContext()) {
          String label = pf.dr.displayDataType(uc.getCode());
          if (uc.hasValueCodeableConcept()) {
            String value = pf.dr.displayDataType(uc.getValueCodeableConcept());
            if (value!=null) {
              contexts.add(label + ":\u00A0" + value);
            }
          } else if (uc.hasValueQuantity()) {
            String value = pf.dr.displayDataType(uc.getValueQuantity());
            if (value!=null)
              contexts.add(label + ":\u00A0" + value);
          } else if (uc.hasValueRange()) {
            String value = pf.dr.displayDataType(uc.getValueRange());
            if (!value.isEmpty())
              contexts.add(label + ":\u00A0" + value);

          } else if (uc.hasValueReference()) {
            String value = null;
            String reference = null;
            if (uc.getValueReference().hasReference()) {
              Resolver.ResourceWithReference rr = pf.rc.getResolver().resolve(pf.rc, uc.getValueReference().getReference(), null);
              if (rr != null) {
                reference = rr.getWebPath();
              } else {
                reference = uc.getValueReference().getReference().contains(":") ? "" : pf.igpkp.getCanonical() + "/";
                reference += uc.getValueReference().getReference();
              }
            }
            if (uc.getValueReference().hasDisplay()) {
              if (reference != null)
                value = "[" + uc.getValueReference().getDisplay() + "](" + reference + ")";
              else
                value = uc.getValueReference().getDisplay();
            } else if (reference!=null)
              value = "[" + uc.getValueReference().getReference() + "](" + reference + ")";
            else if (uc.getValueReference().hasIdentifier()) {
              String idLabel = pf.dr.displayDataType(uc.getValueReference().getIdentifier().getType());
              value = idLabel!=null ? label + ":\u00A0" + uc.getValueReference().getIdentifier().getValue() : uc.getValueReference().getIdentifier().getValue();
            }
            if (value != null)
              contexts.add(value);
          } else if (uc.hasValue()) {
            throw new FHIRException("Unsupported type for UsageContext.value - " + uc.getValue().fhirType());
          }
        }
        if (!contexts.isEmpty())
          item.add("contexts", String.join(", ", contexts));
      }
      if (cr.hasJurisdiction() && !containedCr) {
        File flagDir = new File(pf.tempDir + "/assets/images");
        if (!flagDir.exists())
          flagDir.mkdirs();
        JsonArray jNodes = new JsonArray();
        item.add("jurisdictions", jNodes);
        ValueSet jvs = pf.context.fetchResource(ValueSet.class, "http://hl7.org/fhir/ValueSet/jurisdiction");
        for (CodeableConcept cc : cr.getJurisdiction()) {
          JsonObject jNode = new JsonObject();
          jNodes.add(jNode);
          ValidationResult vr = jvs==null ? null : pf.context.validateCode(new ValidationOptions(FhirPublication.R5, "en-US"),  cc, jvs);
          if (vr != null && vr.asCoding()!=null) {
            try {
              Coding cd = vr.asCoding();
              jNode.add("code", cd.getCode());
              if (cd.getSystem().equals("http://unstats.un.org/unsd/methods/m49/m49.htm") && cd.getCode().equals("001")) {
                jNode.add("name", "International");
                jNode.add("flag", "001");
              } else if (cd.getSystem().equals("urn:iso:std:iso:3166")) {
                String code = translateCountryCode(cd.getCode()).toLowerCase();
                jNode.add("name", displayForCountryCode(cd.getCode()));
                File flagFile = new File(pf.vsCache + "/" + code + ".svg");
                if (!flagFile.exists() && !pf.ignoreFlags.contains(code)) {
                  URL url2 = new URL("https://flagcdn.com/" + pf.shortCountryCode.get(code.toUpperCase()).toLowerCase() + ".svg");
                  try {
                    InputStream in = url2.openStream();
                    Files.copy(in, Paths.get(flagFile.getAbsolutePath()));
                  } catch (Exception e2) {
                    pf.ignoreFlags.add(code);
                    System.out.println("Unable to access " + url2 + " or " + url2 + " (" + e2.getMessage() + ")");
                  }
                }
                if (flagFile.exists()) {
                  FileUtils.copyFileToDirectory(flagFile, flagDir);
                  jNode.add("flag", code);
                }
              } else if (cd.getSystem().equals("urn:iso:std:iso:3166:-2")) {
                String code = cd.getCode();
                String[] codeParts = cd.getCode().split("-");
                jNode.add("name", displayForStateCode(cd.getCode()) + " (" + displayForCountryCode(codeParts[0]) + ")");
                File flagFile = new File(pf.vsCache + "/" + code + ".svg");
                if (!flagFile.exists()) {
                  URL url = new URL("http://flags.ox3.in/svg/" + codeParts[0].toLowerCase() + "/" + codeParts[1].toLowerCase() + ".svg");
                  try (InputStream in = url.openStream()) {
                    Files.copy(in, Paths.get(flagFile.getAbsolutePath()));
                  } catch (Exception e) {
                    // If we can't find the file, that's ok.
                  }
                }
                if (flagFile.exists()) {
                  FileUtils.copyFileToDirectory(flagFile, flagDir);
                  jNode.add("flag", code);
                }
              }
            } catch (Exception e) {
              System.out.println("ERROR: Unable to populate IG flag information: "+e.getMessage());
            }
          } else{
            jNode.add("name", pf.dr.displayDataType(cc));
          }
        }
      }
      if (pcr != null && pcr.hasStatus())
        item.add("status", pcr.getStatus().toCode());
      if (cr.hasPurpose())
        item.add("purpose", ProfileUtilities.processRelativeUrls(cr.getPurpose(), "", pf.igpkp.specPath(), pf.context.getResourceNames(), pf.specMaps.get(0).listTargets(), pageTargets(), false));

      if (cr.hasCopyright()) {
        item.add("copyright", cr.getCopyright());
        addTranslationsToJson(item, "copyright", cr.getCopyrightElement(), false);
      }
      if (pcr!=null && pcr.hasExtension(ExtensionDefinitions.EXT_FMM_LEVEL)) {
        IntegerType fmm = pcr.getExtensionByUrl(ExtensionDefinitions.EXT_FMM_LEVEL).getValueIntegerType();
        item.add("fmm", fmm.asStringValue());
        if (fmm.hasExtension(ExtensionDefinitions.EXT_FMM_DERIVED)) {
          String derivedFrom = "FMM derived from: ";
          for (Extension ext: fmm.getExtensionsByUrl(ExtensionDefinitions.EXT_FMM_DERIVED)) {
            derivedFrom += "\r\n" + ext.getValueCanonicalType().asStringValue();
          }
          item.add("fmmSource", derivedFrom);
        }
      }
      List<String> keywords = new ArrayList<String>();
      if (r.getResource() instanceof StructureDefinition) {
        StructureDefinition sd = (StructureDefinition)r.getResource();
        if (sd.hasKeyword()) {
          for (Coding coding : sd.getKeyword()) {
            String value = pf.dr.displayDataType(coding);
            if (value != null)
              keywords.add(value);
          }
        }
      } else if (r.getResource() instanceof CodeSystem) {
        CodeSystem cs = (CodeSystem)r.getResource();
        for (Extension e : cs.getExtensionsByUrl(ExtensionDefinitions.EXT_CS_KEYWORD)) {
          keywords.add(e.getValueStringType().asStringValue());
        }
      } else if (r.getResource() instanceof ValueSet) {
        ValueSet vs = (ValueSet)r.getResource();
        for (Extension e : vs.getExtensionsByUrl(ExtensionDefinitions.EXT_VS_KEYWORD)) {
          keywords.add(e.getValueStringType().asStringValue());
        }
      }
      if (!keywords.isEmpty())
        item.add("keywords", String.join(", ", keywords));
    }
    if (r.getResource() instanceof DomainResource) {
      org.hl7.fhir.igtools.renderers.StatusRenderer.ResourceStatusInformation info = StatusRenderer.analyse((DomainResource) r.getResource());
      JsonObject jo = new JsonObject();
      if (info.getColorClass() != null) {
        jo.add("class", info.getColorClass());
      }
      if (info.getOwner() != null) {
        jo.add("owner", info.getOwner());
      }
      if (info.getOwnerLink() != null) {
        jo.add("link", info.getOwnerLink());
      }
      if (info.getSstatus() != null) {
        jo.add("standards-status", info.getSstatus());
      } else if (pf.sourceIg.hasExtension(ExtensionDefinitions.EXT_STANDARDS_STATUS)) {
        jo.add("standards-status","informative");
      }
      if (info.getSstatusSupport() != null) {
        jo.add("standards-status-support", info.getSstatusSupport());
      }
      if (info.getNormVersion() != null) {
        item.add("normativeVersion", info.getNormVersion());
      }
      if (info.getFmm() != null) {
        jo.add("fmm", info.getFmm());
      }
      if (info.getSstatusSupport() != null) {
        jo.add("fmm-support", info.getFmmSupport());
      }
      if (info.getStatus() != null && !jo.has("status")) {
        jo.add("status", info.getStatus());
      }
      if (!jo.getProperties().isEmpty()) {
        item.set("status", jo);
      }
    }
  }



  private void generateCanonicalSummary(String lang) throws IOException {
    List<CanonicalResource> crlist = new ArrayList<>();
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
          crlist.add((CanonicalResource) r.getResource());
        }
      }
    }
    Collections.sort(crlist, new ResourceSorters.CanonicalResourceSortByUrl());
    JsonArray list = new JsonArray();
    for (CanonicalResource cr : crlist) {
      JsonObject obj = new JsonObject();
      obj.add("id", cr.getId());
      obj.add("type", cr.fhirType());
      if (cr.hasUrl()) {
        obj.add("url", cr.getUrl());
      }
      if (cr.hasVersion()) {
        obj.add("version", cr.getVersion());
      }
      if (cr.hasName()) {
        obj.add("name", cr.getName());
      }
      JsonArray oids = new JsonArray();
      JsonArray urls = new JsonArray();
      for (Identifier id : cr.getIdentifier()) {
        if (id != null) {
          if("urn:ietf:rfc:3986".equals(id.getSystem()) && id.hasValue()) {
            if (id.getValue().startsWith("urn:oid:")) {
              oids.add(id.getValue().substring(8));
            } else {
              urls.add(id.getValue());
            }
          }
        }
      }
      if (oids.size() > 0) {
        obj.add("oids", oids);
      }
      if (urls.size() > 0) {
        obj.add("alt-urls", urls);
      }
      list.add(obj);
    }
    String json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(list, true);
    FileUtilities.stringToFile(json, Utilities.path(pf.tempDir, "_data", "canonicals.json"));
    FileUtilities.stringToFile(json, Utilities.path(pf.tempDir, "canonicals.json"));
    pf.otherFilesRun.add(Utilities.path(pf.tempDir, "canonicals.json"));

    Collections.sort(crlist, new ResourceSorters.CanonicalResourceSortByTypeId());
    XhtmlNode tbl = new XhtmlNode(NodeType.Element, "table").setAttribute("class", "grid");
    XhtmlNode tr = tbl.tr();
    tr.td().b().tx("Canonical");
    tr.td().b().tx("Id");
    tr.td().b().tx("Version");
    tr.td().b().tx("Oids");
    tr.td().b().tx("Other URLS");
    String type = "";
    for (CanonicalResource cr : crlist) {
      CommaSeparatedStringBuilder bu = new CommaSeparatedStringBuilder();
      CommaSeparatedStringBuilder bo = new CommaSeparatedStringBuilder();
      for (Identifier id : cr.getIdentifier()) {
        if (id != null) {
          if ("urn:ietf:rfc:3986".equals(id.getSystem()) && id.hasValue()) {
            if (id.getValue().startsWith("urn:oid:")) {
              bo.append(id.getValue().substring(8));
            } else {
              bu.append(id.getValue());
            }
          }
        }
      }
      if (!type.equals(cr.fhirType())) {
        type = cr.fhirType();
        XhtmlNode h = tbl.tr().style("background-color: #eeeeee").td().colspan("5").h3();
        h.an(type);
        h.tx(type);
      }
      tr = tbl.tr();
      if (cr.getWebPath() != null) {
        tr.td().ah(cr.getWebPath()).tx(cr.getUrl());
      } else {
        tr.td().code().tx(cr.getUrl());
      }
      tr.td().tx(cr.getId());
      tr.td().tx(cr.getVersion());
      tr.td().tx(bo.toString());
      tr.td().tx(bu.toString());
    }
    String xhtml = new XhtmlComposer(true).compose(tbl);
    long start = System.currentTimeMillis();
    fragment("canonical-index", xhtml, pf.otherFilesRun, start, "canonical-index", "Cross", lang);
  }

  private String url(List<ContactPoint> telecom) {
    for (ContactPoint cp : telecom) {
      if (cp.getSystem() == ContactPoint.ContactPointSystem.URL) {
        return cp.getValue();
      }
    }
    return null;
  }


  private String email(List<ContactPoint> telecom) {
    for (ContactPoint cp : telecom) {
      if (cp.getSystem() == ContactPoint.ContactPointSystem.EMAIL) {
        return cp.getValue();
      }
    }
    return null;
  }

  private void generateProfiles(String lang) throws Exception {
    long start = System.currentTimeMillis();
    List<Item> items = new ArrayList<Item>();
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("StructureDefinition")) {
          StructureDefinition sd = (StructureDefinition) r.getResource();
          if (sd.getDerivation() == StructureDefinition.TypeDerivationRule.CONSTRAINT && sd.getKind() == StructureDefinition.StructureDefinitionKind.RESOURCE) {
            items.add(new Item(f, r, sd.hasTitle() ? sd.getTitle() : sd.hasName() ? sd.getName() : r.getTitle()));
          }
        }
      }
    }
    if (items.size() > 0) {
      Collections.sort(items, new ItemSorter());
      StringBuilder list = new StringBuilder();
      StringBuilder lists = new StringBuilder();
      StringBuilder table = new StringBuilder();
      StringBuilder listMM = new StringBuilder();
      StringBuilder listsMM = new StringBuilder();
      StringBuilder tableMM = new StringBuilder();
      for (Item i : items) {
        StructureDefinition sd = (StructureDefinition) i.r.getResource();
        genEntryItem(list, lists, table, listMM, listsMM, tableMM, i.f, i.r, i.sort, null);
      }
      fragment("list-profiles", list.toString(), pf.otherFilesRun, start, "list-profiles", "Cross", lang);
      fragment("list-simple-profiles", lists.toString(), pf.otherFilesRun, start, "list-simple-profiles", "Cross", lang);
      fragment("table-profiles", table.toString(), pf.otherFilesRun, start, "table-profiles", "Cross", lang);
      fragment("list-profiles-mm", listMM.toString(), pf.otherFilesRun, start, "list-profiles-mm", "Cross", lang);
      fragment("list-simple-profiles-mm", listsMM.toString(), pf.otherFilesRun, start, "list-simple-profiles-mm", "Cross", lang);
      fragment("table-profiles-mm", tableMM.toString(), pf.otherFilesRun, start, "table-profiles-mm", "Cross", lang);
    }
  }

  private void generateExtensions(String lang) throws Exception {
    long start = System.currentTimeMillis();
    List<Item> items = new ArrayList<Item>();
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("StructureDefinition")) {
          StructureDefinition sd = (StructureDefinition) r.getResource();
          if (ProfileUtilities.isExtensionDefinition(sd)) {
            items.add(new Item(f, r, sd.hasTitle() ? sd.getTitle() : sd.hasName() ? sd.getName() : r.getTitle()));
          }
        }
      }
    }

    StringBuilder list = new StringBuilder();
    StringBuilder lists = new StringBuilder();
    StringBuilder table = new StringBuilder();
    StringBuilder listMM = new StringBuilder();
    StringBuilder listsMM = new StringBuilder();
    StringBuilder tableMM = new StringBuilder();
    for (Item i : items) {
      StructureDefinition sd = (StructureDefinition) i.r.getResource();
      genEntryItem(list, lists, table, listMM, listsMM, tableMM, i.f, i.r, i.sort, null);
    }
    fragment("list-extensions", list.toString(), pf.otherFilesRun, start, "list-extensions", "Cross", lang);
    fragment("list-simple-extensions", lists.toString(), pf.otherFilesRun, start, "list-simple-extensions", "Cross", lang);
    fragment("table-extensions", table.toString(), pf.otherFilesRun, start, "table-extensions", "Cross", lang);
    fragment("list-extensions-mm", listMM.toString(), pf.otherFilesRun, start, "list-extensions-mm", "Cross", lang);
    fragment("list-simple-extensions-mm", listsMM.toString(), pf.otherFilesRun, start, "list-simple-extensions-mm", "Cross", lang);
    fragment("table-extensions-mm", tableMM.toString(), pf.otherFilesRun, start, "table-extensions-mm", "Cross", lang);
  }

  private void generateLogicals(String lang) throws Exception {
    long start = System.currentTimeMillis();
    List<Item> items = new ArrayList<Item>();
    for (FetchedFile f : pf.fileList) {
      f.start("generateLogicals");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.fhirType().equals("StructureDefinition")) {
            StructureDefinition sd = (StructureDefinition) r.getResource();
            if (sd.getKind() == StructureDefinition.StructureDefinitionKind.LOGICAL) {
              items.add(new Item(f, r, sd.hasTitle() ? sd.getTitle() : sd.hasName() ? sd.getName() : r.getTitle()));
            }
          }
        }
      } finally {
        f.finish("generateLogicals");
      }
    }

    StringBuilder list = new StringBuilder();
    StringBuilder lists = new StringBuilder();
    StringBuilder table = new StringBuilder();
    StringBuilder listMM = new StringBuilder();
    StringBuilder listsMM = new StringBuilder();
    StringBuilder tableMM = new StringBuilder();
    for (Item i : items) {
      StructureDefinition sd = (StructureDefinition) i.r.getResource();
      genEntryItem(list, lists, table, listMM, listsMM, tableMM, i.f, i.r, i.sort, null);
    }
    fragment("list-logicals", list.toString(), pf.otherFilesRun, start, "list-logicals", "Cross", lang);
    fragment("list-simple-logicals", lists.toString(), pf.otherFilesRun, start, "list-simple-logicals", "Cross", lang);
    fragment("table-logicals", table.toString(), pf.otherFilesRun, start, "table-logicals", "Cross", lang);
    fragment("list-logicals-mm", listMM.toString(), pf.otherFilesRun, start, "list-logicals-mm", "Cross", lang);
    fragment("list-simple-logicals-mm", listsMM.toString(), pf.otherFilesRun, start, "list-simple-logicals-mm", "Cross", lang);
    fragment("table-logicals-mm", tableMM.toString(), pf.otherFilesRun, start, "table-logicals-mm", "Cross", lang);
  }

  private void genEntryItem(StringBuilder list, StringBuilder lists, StringBuilder table, StringBuilder listMM, StringBuilder listsMM, StringBuilder tableMM, FetchedFile f, FetchedResource r, String name, String prefixType) throws Exception {
    String ref = this.pf.igpkp.doReplacements(this.pf.igpkp.getLinkFor(r, false), r, null, null);
    if (Utilities.noString(ref))
      throw new Exception("No reference found for "+r.getId());
    if (prefixType != null)
      if (ref.contains("."))
        ref = ref.substring(0, ref.lastIndexOf("."))+"."+prefixType+ref.substring(ref.lastIndexOf("."));
      else
        ref = ref+"."+prefixType;
    String desc = r.getTitle();
    String descSrc = "Resource Title";
    if (!r.hasTitle()) {
      desc = f.getTitle();
      descSrc = "File Title";
    }
    if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
      name = ((CanonicalResource) r.getResource()).present();
      String d = getDesc((CanonicalResource) r.getResource());
      if (d != null) {
        desc = this.pf.markdownEngine.process(d, descSrc);
        descSrc = "Canonical Resource";
      }
    } else if (r.getElement() != null && r.getElement().hasChild("description")) {
      String d = new StringType(r.getElement().getChildValue("description")).asStringValue();
      if (d != null) {
        desc = this.pf.markdownEngine.process(d, descSrc);
        // new BaseRenderer(context, null, igpkp, specMaps, markdownEngine, packge, rc).processMarkdown("description", desc )
        descSrc = "Canonical Resource Source";
      }
    }
    list.append(" <li><a href=\""+ref+"\">"+Utilities.escapeXml(name)+"</a> "+desc+"</li>\r\n");
    lists.append(" <li><a href=\""+ref+"\">"+Utilities.escapeXml(name)+"</a></li>\r\n");
    table.append(" <tr><td><a href=\""+ref+"\">"+Utilities.escapeXml(name)+"</a> </td><td>"+desc+"</td></tr>\r\n");

    if (listMM != null) {
      String mm = "";
      if (r.getResource() != null && r.getResource() instanceof DomainResource) {
        String fmm = ExtensionUtilities.readStringExtension((DomainResource) r.getResource(), ExtensionDefinitions.EXT_FMM_LEVEL);
        if (fmm != null) {
          // Use hard-coded spec link to point to current spec because DSTU2 had maturity listed on a different page
          mm = " <a class=\"fmm\" href=\"http://hl7.org/fhir/versions.html#maturity\" title=\"Maturity Level\">"+fmm+"</a>";
        }
      }
      listMM.append(" <li><a href=\""+ref+"\">"+Utilities.escapeXml(name)+"</a>"+mm+" "+desc+"</li>\r\n");
      listsMM.append(" <li><a href=\""+ref+"\">"+Utilities.escapeXml(name)+"</a>"+mm+"</li>\r\n");
      tableMM.append(" <tr><td><a href=\""+ref+"\">"+Utilities.escapeXml(name)+"</a> </td><td>"+desc+"</td><td>"+mm+"</td></tr>\r\n");
    }
  }

  private void generateResourceReferences(String rt, String lang) throws Exception {
    List<Item> items = new ArrayList<Item>();
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals(rt)) {
          if (r.getResource() instanceof CanonicalResource) {
            CanonicalResource md = (CanonicalResource) r.getResource();
            items.add(new Item(f, r, md.hasTitle() ? md.getTitle() : md.hasName() ? md.getName() : r.getTitle()));
          } else
            items.add(new Item(f, r, Utilities.noString(r.getTitle()) ? r.getId() : r.getTitle()));
        }
      }
    }

    genResourceReferencesList(rt, items, "", lang);
    Collections.sort(items, new ItemSorterById());
    if (pf.cu.getCanonicalResourceNames().contains(rt)) {
      genResourceReferencesGrid(rt, items, "grid-", lang);
    }
    genResourceReferencesList(rt, items, "byid-", lang);
    Collections.sort(items, new ItemSorterByName());
    genResourceReferencesList(rt, items, "name-", lang);
  }


  public class ItemSorterById implements Comparator<Item> {
    @Override
    public int compare(Item arg0, Item arg1) {
      String l = arg0.r == null ? null : arg0.r.getId();
      String r = arg1.r == null ? null : arg1.r.getId();
      return l == null ? 0 : l.compareTo(r);
    }
  }

  public class ItemSorterByName implements Comparator<Item> {
    @Override
    public int compare(Item arg0, Item arg1) {
      String l = arg0.r == null ? null : arg0.r.getTitle();
      String r = arg1.r == null ? null : arg1.r.getTitle();
      return l == null ? 0 : l.compareTo(r);
    }
  }

  public void genResourceReferencesGrid(String rt, List<Item> items, String ext, String lang) throws Exception, IOException {
    long start = System.currentTimeMillis();
    XhtmlNode x = new XhtmlNode(NodeType.Element, "div");
    XhtmlNode tbl = x.table("grid");
    boolean cs = gridHeader(tbl, rt);
    int t  = 0;
    for (Item i : items) {
      Element e = i.r.getElement();
      if (rt == null || rt.equals(e.fhirType())) {
        t++;
        XhtmlNode tr = tbl.tr();
        tr.clss("data-row");
        tr.td().ah(i.r.getLocalRef(), e.getIdBase()).tx(trunc(e.getIdBase()));
        tr.td().tx(e.getNamedChildValueSingle("title", "name"));
        tr.td().tx(e.getNamedChildValueSingle("version"));
        describeStatus(tr.td(), e);
        if (cs) {
          tr.td().tx(e.getNamedChildValueSingle("content"));
        }
        describeOwner(tr.td(), e);
        tr.td().tx(describeCopyRight(e.getNamedChildValueSingle("copyright"), e.getNamedChildValueSingle("copyrightLabel")));
        tr.td().tx(dateOnly(e.getNamedChildValueSingle("date")));
        describeDescription(tr.td(), e.getNamedChildValueSingle("description"));
      }
    }
    x.para().clss("table-filter-summary").tx("Showing "+t+" of "+t+" entries");
    x.jsSrc("assets/js/table.js");
    String html = new XhtmlComposer(false, true).compose(x.getChildNodes());
    String pm = Utilities.pluralizeMe(rt.toLowerCase());
    fragment("table-"+ext+pm, html, pf.otherFilesRun, start, "table-"+ext+pm, "Cross", lang);
  }

  private boolean gridHeader(XhtmlNode tbl, String type) {
    boolean cs = "CodeSystem".equals(type);

    tbl.clss("filtered-table");
    tbl.attribute("data-resource-type", type);

    XhtmlNode tr = tbl.tr();
    tr.th().tx("Identity");
    tr.th().tx("Name");
    tr.th().tx("Version");
    tr.th().tx("Status");
    if (cs) {
      tr.th().tx("Content");
    }
    tr.th().tx("Owner");
    tr.th().tx("Copyright");
    tr.th().tx("Date");
    tr.th().tx("Description");

    tr = tbl.tr();
    tr.td().input(null, "text", null, 10).attribute("id", "filter-identity").clss("filter-input");
    tr.td().input(null, "text", null, 30).attribute("id", "filter-name").clss("filter-input");
    tr.td().input(null, "text", null, 4).attribute("id", "filter-version").clss("filter-input");
    tr.td().input(null, "text", null, 4).attribute("id", "filter-status").clss("filter-input");
    if (cs) {
      tr.td().input(null, "text", null, 6).attribute("id", "filter-content").clss("filter-input");
    }
    tr.td().input(null, "text", null, 5).attribute("id", "filter-owner").clss("filter-input");
    tr.td().input(null, "text", null, 5).attribute("id", "filter-copyright").clss("filter-input");
    tr.td().input(null, "text", null, 6).attribute("id", "filter-date").clss("filter-input");
    tr.td().input(null, "text", null, 30).attribute("id", "filter-desc").clss("filter-input");
    return cs;
  }

  public void genResourceReferencesList(String rt, List<Item> items, String ext, String lang) throws Exception, IOException {
    long start = System.currentTimeMillis();
    StringBuilder list = new StringBuilder();
    StringBuilder lists = new StringBuilder();
    StringBuilder table = new StringBuilder();
    StringBuilder listMM = new StringBuilder();
    StringBuilder listsMM = new StringBuilder();
    StringBuilder tableMM = new StringBuilder();
    StringBuilder listJ = new StringBuilder();
    StringBuilder listsJ = new StringBuilder();
    StringBuilder tableJ = new StringBuilder();
    StringBuilder listX = new StringBuilder();
    StringBuilder listsX = new StringBuilder();
    StringBuilder tableX = new StringBuilder();
    if (items.size() > 0) {
      for (Item i : items) {
        String name = i.r.getTitle();
        if (Utilities.noString(name))
          name = rt;
        genEntryItem(list, lists, table, listMM, listsMM, tableMM, i.f, i.r, i.sort, null);
        genEntryItem(listJ, listsJ, tableJ, null, null, null, i.f, i.r, i.sort, "json");
        genEntryItem(listX, listsX, tableX, null, null, null, i.f, i.r, i.sort, "xml");
      }
    }
    String pm = Utilities.pluralizeMe(rt.toLowerCase());
    fragment("list-"+ext+pm, list.toString(), pf.otherFilesRun, start, "list-"+ext+pm, "Cross", lang);
    fragment("list-simple-"+ext+pm, lists.toString(), pf.otherFilesRun, start, "list-simple-"+ext+pm, "Cross", lang);
    fragment("table-"+ext+pm, table.toString(), pf.otherFilesRun, start, "table-"+ext+pm, "Cross", lang);
    fragment("list-"+ext+pm+"-json", listJ.toString(), pf.otherFilesRun, start, "list-"+ext+pm+"-json", "Cross", lang);
    fragment("list-simple-"+ext+pm+"-json", listsJ.toString(), pf.otherFilesRun, start, "list-simple-"+ext+pm+"-json", "Cross", lang);
    fragment("table-"+ext+pm+"-json", tableJ.toString(), pf.otherFilesRun, start, "table-"+ext+pm+"-json", "Cross", lang);
    fragment("list-"+ext+pm+"-xml", listX.toString(), pf.otherFilesRun, start, "list-"+ext+pm+"-xml", "Cross", lang);
    fragment("list-simple-"+ext+pm+"-xml", listsX.toString(), pf.otherFilesRun, start, "list-simple-"+ext+pm+"-xml", "Cross", lang);
    fragment("table-"+ext+pm+"-xml", tableX.toString(), pf.otherFilesRun, start, "table-"+ext+pm+"-xml", "Cross", lang);
  }

  @SuppressWarnings("rawtypes")
  private String getDesc(CanonicalResource r) {
    if (r.hasDescriptionElement()) {
      return r.getDescriptionElement().asStringValue();
    }
    return null;
  }

  private void generateResourceReferences(String lang) throws Exception {
    Set<String> resourceTypes = new HashSet<>();
    for (StructureDefinition sd : pf.context.fetchResourcesByType(StructureDefinition.class)) {
      if (sd.getDerivation() == StructureDefinition.TypeDerivationRule.SPECIALIZATION && sd.getKind() == StructureDefinition.StructureDefinitionKind.RESOURCE) {
        resourceTypes.add(sd.getType());
        resourceTypes.add(sd.getTypeTail());
      }
    }
    for (String rt : resourceTypes) {
      if (!rt.contains(":")) {
        generateResourceReferences(rt, lang);
      }
    }
    generateProfiles(lang);
    generateExtensions(lang);
    generateLogicals(lang);
  }



  public boolean generateResourceHtml(FetchedFile f, boolean regen, FetchedResource r, Resource res, Map<String, String> vars, String prefixForContainer, DBBuilder db, String lang, RenderingContext lrc) {
    return generateResourceHtmlInner(f, regen, r, res, vars, prefixForContainer, db, lrc, lang);
  }

  public boolean generateResourceHtmlInner(FetchedFile f, boolean regen, FetchedResource r, Resource res, Map<String, String> vars, String prefixForContainer, DBBuilder db, RenderingContext lrc, String lang) {
    boolean result = true;
    try {

      // now, start generating resource type specific stuff
      switch (res.getResourceType()) {
        case CodeSystem:
          generateOutputsCodeSystem(f, r, (CodeSystem) res, vars, prefixForContainer, lrc, lang);
          break;
        case ValueSet:
          generateOutputsValueSet(f, r, (ValueSet) res, vars, prefixForContainer, db, lrc, lang);
          break;
        case ConceptMap:
          generateOutputsConceptMap(f, r, (ConceptMap) res, vars, prefixForContainer, lrc, lang);
          break;
        case ImplementationGuide:
          generateOutputsImplementationGuide(f, r, (ImplementationGuide) res, vars, prefixForContainer, lrc, lang);
          break;
        case List:
          generateOutputsList(f, r, (ListResource) res, vars, prefixForContainer, lrc, lang);
          break;

        case CapabilityStatement:
          generateOutputsCapabilityStatement(f, r, (CapabilityStatement) res, vars, prefixForContainer, lrc, lang);
          break;
        case StructureDefinition:
          generateOutputsStructureDefinition(f, r, (StructureDefinition) res, vars, regen, prefixForContainer, lrc, lang);
          break;
        case OperationDefinition:
          generateOutputsOperationDefinition(f, r, (OperationDefinition) res, vars, regen, prefixForContainer, lrc, lang);
          break;
        case StructureMap:
          generateOutputsStructureMap(f, r, (StructureMap) res, vars, prefixForContainer, lrc, lang);
          break;
        case Questionnaire:
          generateOutputsQuestionnaire(f, r, (Questionnaire) res, vars, prefixForContainer, lrc, lang);
          break;
        case Library:
          generateOutputsLibrary(f, r, (Library) res, vars, prefixForContainer, lrc, lang);
          break;
        case ExampleScenario:
          generateOutputsExampleScenario(f, r, (ExampleScenario) res, vars, prefixForContainer, lrc, lang);
          break;
        default:
          if (res instanceof CanonicalResource) {
            generateOutputsCanonical(f, r, (CanonicalResource) res, vars, prefixForContainer, lrc, lang);
          }
          // nothing to do...
          result = false;
      }
    } catch (Exception e) {
      log("Exception generating resource "+f.getName()+"::"+r.fhirType()+"/"+r.getId()+(!Utilities.noString(prefixForContainer) ? "#"+res.getId() : "")+": "+e.getMessage());
      f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.EXCEPTION, r.fhirType(), "Error Rendering Resource: "+e.getMessage(), ValidationMessage.IssueSeverity.ERROR));
      e.printStackTrace();
      for (StackTraceElement m : e.getStackTrace()) {
        log("   "+m.toString());
      }
    }
    return result;
  }

  public boolean generateResourceSpreadsheets(FetchedFile f, boolean regen, FetchedResource r, Resource res, Map<String, String> vars, String prefixForContainer, DBBuilder db) {
    boolean result = true;
    try {

      // now, start generating resource type specific stuff
      switch (res.getResourceType()) {
//      case CodeSystem:
//        generateOutputsCodeSystem(f, r, (CodeSystem) res, vars, prefixForContainer, lrc, lang);
//        break;
//      case ValueSet:
//        generateOutputsValueSet(f, r, (ValueSet) res, vars, prefixForContainer, db, lrc, lang);
//        break;
//      case ConceptMap:
//        generateOutputsConceptMap(f, r, (ConceptMap) res, vars, prefixForContainer, lrc, lang);
//        break;
//
//      case List:
//        generateOutputsList(f, r, (ListResource) res, vars, prefixForContainer, lrc, lang);
//        break;
//
//      case CapabilityStatement:
//        generateOutputsCapabilityStatement(f, r, (CapabilityStatement) res, vars, prefixForContainer, lrc, lang);
//        break;
        case StructureDefinition:
          generateSpreadsheetsStructureDefinition(f, r, (StructureDefinition) res, vars, regen, prefixForContainer);
          break;
//      case OperationDefinition:
//        generateOutputsOperationDefinition(f, r, (OperationDefinition) res, vars, regen, prefixForContainer, lrc, lang);
//        break;
//      case StructureMap:
//        generateOutputsStructureMap(f, r, (StructureMap) res, vars, prefixForContainer, lrc, lang);
//        break;
//      case Questionnaire:
//        generateOutputsQuestionnaire(f, r, (Questionnaire) res, vars, prefixForContainer, lrc, lang);
//        break;
//      case Library:
//        generateOutputsLibrary(f, r, (Library) res, vars, prefixForContainer, lrc, lang);
//        break;
//      case ExampleScenario:
//        generateOutputsExampleScenario(f, r, (ExampleScenario) res, vars, prefixForContainer, lrc, lang);
//        break;
        default:
//        if (res instanceof CanonicalResource) {
//          generateOutputsCanonical(f, r, (CanonicalResource) res, vars, prefixForContainer, lrc, lang);
//        }
          // nothing to do...
          result = false;
      }
    } catch (Exception e) {
      log("Exception generating resource "+f.getName()+"::"+r.fhirType()+"/"+r.getId()+(!Utilities.noString(prefixForContainer) ? "#"+res.getId() : "")+": "+e.getMessage());
      f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.EXCEPTION, r.fhirType(), "Error Rendering Resource: "+e.getMessage(), ValidationMessage.IssueSeverity.ERROR));
      e.printStackTrace();
      for (StackTraceElement m : e.getStackTrace()) {
        log("   "+m.toString());
      }
    }
    return result;
  }


  private void generateOutputsOperationDefinition(FetchedFile f, FetchedResource r, OperationDefinition od, Map<String, String> vars, boolean regen, String prefixForContainer, RenderingContext lrc, String lang) throws FHIRException, IOException {
    OperationDefinitionRenderer odr = new OperationDefinitionRenderer(this.pf.context, checkAppendSlash(this.pf.specPath), od, Utilities.path(this.pf.tempDir), this.pf.igpkp, this.pf.specMaps, pageTargets(), this.pf.markdownEngine, this.pf.packge, this.pf.fileList, lrc, this.pf.versionToAnnotate, this.pf.relatedIGs);
    if (wantGen(r, "summary")) {
      long start = System.currentTimeMillis();
      fragment("OperationDefinition-"+prefixForContainer+od.getId()+"-summary", odr.summary(), f.getOutputNames(), r, vars, null, start, "summary", "OperationDefinition", lang);
    }
    if (wantGen(r, "summary-table")) {
      long start = System.currentTimeMillis();
      fragment("OperationDefinition-"+prefixForContainer+od.getId()+"-summary-table", odr.summary(), f.getOutputNames(), r, vars, null, start, "summary-table", "OperationDefinition", lang);
    }
    if (wantGen(r, "idempotence")) {
      long start = System.currentTimeMillis();
      fragment("OperationDefinition-"+prefixForContainer+od.getId()+"-idempotence", odr.idempotence(), f.getOutputNames(), r, vars, null, start, "idempotence", "OperationDefinition", lang);
    }
  }



  private void generateOutputsList(FetchedFile f, FetchedResource r, ListResource resource, Map<String, String> vars, String prefixForContainer, RenderingContext lrc, String lang) throws IOException, FHIRException {
    // we have 4 kinds of outputs:
    //  * list: a series of <li><a href="{{link}}">{{name}}</a> {{desc}}</li>
    //  * list-simple: a series of <li><a href="{{link}}">{{name}}]</a></li>
    //  * table: a series of <tr><td><a href="{{link}}">{{name}}]</a></td><td>{{desc}}</td></tr>
    //  * scripted: in format as provided by config, using liquid variables {{link}}, {{name}}, {{desc}} as desired
    // not all resources have a description. Name might be generated
    //
    // each list is produced 3 times:
    //  * in order provided by the list
    //  * in alphabetical order by link
    //  * in allhpbetical order by name
    // and if there is more than one resource type in the list,

    List<ListItemEntry> list = new ArrayList<>();

    for (ListResource.ListResourceEntryComponent li : resource.getEntry()) {
      if (!li.getDeleted() && li.hasItem() && li.getItem().hasReference()) {
        String ref = li.getItem().getReference();
        FetchedResource lr = getResourceForUri(f, ref);
        if (lr == null)
          lr = getResourceForRef(f, ref);
        if (lr != null) {
          list.add(new ListItemEntry(lr.fhirType(), getListId(lr), getListLink(lr), getListName(lr), getListTitle(lr), getListDesc(lr), lr.getElement()));
        } else {
          // ok, we'll see if we can resolve it from another spec
          Resource l = this.pf.context.fetchResource(null, ref);
          if (l== null && ref.matches(Constants.LOCAL_REF_REGEX)) {
            String[] p = ref.split("\\/");
            l = this.pf.context.fetchResourceById(p[0], p[1]);
          }
          if (l != null)
            list.add(new ListItemEntry(l.fhirType(), getListId(l), getListLink(l), getListName(l), getListTitle(lr), getListDesc(l), null));
        }
      }
    }

    String types = this.pf.igpkp.getProperty(r, "list-types");
    Collections.sort(list, new ListViewSorterById());
    if (types != null) {
      for (String type : types.split("\\|")) {
        if (this.pf.cu.getCanonicalResourceNames().contains(type)) {
          long start = System.currentTimeMillis();
          String html = genGridView(list, type);
          fragmentIfNN("List-" + resource.getId() + "-list-grid" + (type == null ? "" : "-" + type), html, f.getOutputNames(), start, "list-list-table", "List", lang);
        }
      }
    }

    String script = this.pf.igpkp.getProperty(r, "list-script");
    genListViews(f, r, resource, list, script, "no", null, lang);
    if (types != null) {
      for (String t : types.split("\\|")) {
        genListViews(f, r, resource, list, script, "no", t.trim(), lang);
      }
    }
    Collections.sort(list, new ListViewSorterById());
    genListViews(f, r, resource, list, script, "id", null, lang);
    if (types != null) {
      for (String t : types.split("\\|")) {
        genListViews(f, r, resource, list, script, "id", t.trim(), lang);
      }
    }
    Collections.sort(list, new ListViewSorterByName());
    genListViews(f, r, resource, list, script, "name", null, lang);
    if (types != null) {
      for (String t : types.split("\\|")) {
        genListViews(f, r, resource, list, script, "name", t.trim(), lang);
      }
    }


    // now, if the list has a package-id extension, generate the package for the list
    if (resource.hasExtension(ExtensionDefinitions.EXT_LIST_PACKAGE)) {
      Extension ext = resource.getExtensionByUrl(ExtensionDefinitions.EXT_LIST_PACKAGE);
      String id = ExtensionUtilities.readStringExtension(ext, "id");
      String name = ExtensionUtilities.readStringExtension(ext, "name");
      String dfn = Utilities.path(this.pf.tempDir, id+".tgz");
      NPMPackageGenerator gen = NPMPackageGenerator.subset(this.pf.npm, dfn, id, name, this.pf.getExecTime().getTime(), !this.settings.isPublishing());
      for (ListItemEntry i : list) {
        if (i.element != null) {
          ByteArrayOutputStream bs = new ByteArrayOutputStream();
          new org.hl7.fhir.r5.elementmodel.JsonParser(this.pf.context).compose(i.element, bs, IParser.OutputStyle.NORMAL, this.pf.igpkp.getCanonical());
          gen.addFile(NPMPackageGenerator.Category.RESOURCE, i.element.fhirType()+"-"+i.element.getIdBase()+".json", bs.toByteArray());
        }
      }
      gen.finish();
      this.pf.otherFilesRun.add(Utilities.path(this.pf.tempDir, id+".tgz"));
    }
  }


  public void genListViews(FetchedFile f, FetchedResource r, ListResource resource, List<ListItemEntry> list, String script, String id, String type, String lang) throws IOException, FHIRException {
    if (wantGen(r, "list-list")) {
      long start = System.currentTimeMillis();
      fragmentIfNN("List-"+resource.getId()+"-list-"+id+(type == null ? "" : "-"+type), genListView(list, "<li><a href=\"{{link}}\">{{title}}</a> {{desc}}</li>\r\n", type), f.getOutputNames(), start, "list-list", "List", lang);
    }
    if (wantGen(r, "list-list-simple")) {
      long start = System.currentTimeMillis();
      fragmentIfNN("List-"+resource.getId()+"-list-"+id+"-simple"+(type == null ? "" : "-"+type), genListView(list, "<li><a href=\"{{link}}\">{{title}}</a></li>\r\n", type), f.getOutputNames(), start, "list-list-simple", "List", lang);
    }
    if (wantGen(r, "list-list-table")) {
      long start = System.currentTimeMillis();
      fragmentIfNN("List-"+resource.getId()+"-list-"+id+"-table"+(type == null ? "" : "-"+type), genListView(list, "<tr><td><a href=\"{{link}}\">{{title}}</a></td><td>{{desc}}</td></tr>\r\n", type), f.getOutputNames(), start, "list-list-table", "List", lang);
    }
    if (script != null) {
      long start = System.currentTimeMillis();
      fragmentIfNN("List-"+resource.getId()+"-list-"+id+"-script"+(type == null ? "" : "-"+type), genListView(list, script, type), f.getOutputNames(), start, "script", "List", lang);
    }
  }

  private String genListView(List<ListItemEntry> list, String template, String type) {
    StringBuilder b = new StringBuilder();
    for (ListItemEntry i : list) {
      if (type == null || type.equals(i.getType())) {
        String s = template;
        if (s.contains("{{link}}")) {
          s = s.replace("{{link}}", i.getLink());
        }
        if (s.contains("{{name}}"))
          s = s.replace("{{name}}", i.getName());
        if (s.contains("{{id}}"))
          s = s.replace("{{id}}", i.getId());
        if (s.contains("{{title}}"))
          s = s.replace("{{title}}", i.getTitle());
        if (s.contains("{{desc}}"))
          s = s.replace("{{desc}}", i.getDesc() == null ? "" : trimPara(pf.markdownEngine.process(i.getDesc(), "List reference description")));
        b.append(s);
      }
    }
    return b.toString();
  }

  private String genGridView(List<ListItemEntry> list, String type) throws IOException {
    XhtmlNode x = new XhtmlNode(NodeType.Element, "div");
    XhtmlNode tbl = x.table("grid");
    boolean cs = gridHeader(tbl, type);
    int t = 0;
    for (ListItemEntry i : list) {
      if (type == null || type.equals(i.getType())) {
        t++;
        XhtmlNode tr = tbl.tr();
        tr.clss("data-row");
        tr.td().ah(i.link, i.getId()).tx(trunc(i.getId()));
        tr.td().tx(i.getTitle());
        tr.td().tx(i.element.getNamedChildValueSingle("version"));
        describeStatus(tr.td(), i.element);
        if (cs) {
          tr.td().tx(i.element.getNamedChildValueSingle("content"));
        }
        describeOwner(tr.td(), i.element);
        tr.td().tx(describeCopyRight(i.element.getNamedChildValueSingle("copyright"), i.element.getNamedChildValueSingle("copyrightLabel")));
        tr.td().tx(dateOnly(i.element.getNamedChildValueSingle("date")));
        describeDescription(tr.td(), i.getDesc());
      }
    }
    x.para().clss("table-filter-summary").tx("Showing "+t+" of "+t+" entries");
    x.jsSrc("assets/js/table.js");
    return new XhtmlComposer(false, true).compose(x.getChildNodes());
  }

  private void describeDescription(XhtmlNode x, String md) throws IOException {
    if (md != null) {
      String mds = md;
      if (md.length() > 100) {
        mds = md.substring(0, 99) + "\u2026";
      }
      x.attribute("title", md).markdownSimple(mds, "List reference description");
    }
  }

  private String dateOnly(String n) {
    if (n == null) {
      return "n/a";
    } else if (n.length() > 10) {
      return n.substring(0, 10);
    } else {
      return n;
    }
  }

  private String trunc(String n) {
    if (n.contains("/")) {
      n = n.substring(n.indexOf("/")+1);
    }
    if (n.length() > 20) {
      return n.substring(0, 19)+"\u2026";
    } else {
      return n;
    }
  }

  private void describeStatus(XhtmlNode x, Element element) {
    String ss = element.getExtensionString(ExtensionDefinitions.EXT_STANDARDS_STATUS);
    if (ss != null) {
      x.tx(ss);
    } else {
      x.tx(element.getNamedChildValueSingle("status"));
    }
  }

  private void describeOwner(XhtmlNode x, Element element) {
    if (element.hasExtension(ExtensionDefinitions.EXT_WORKGROUP)) {
      String wg = element.getExtensionString(ExtensionDefinitions.EXT_WORKGROUP);
      HL7WorkGroups.HL7WorkGroup wgo = HL7WorkGroups.find(wg);
      if (wgo == null) {
        x.tx(wg+"?");
      } else
        x.ah(wgo.getLink()).tx(wg);
    } else {
      String url = null;
      for (Element tc : element.getChildrenByName("contact")) {
        if (tc != null) {
          for (Element t : tc.getChildrenByName("telecom")) {
            if ("url".equals(t.getNamedChildValueSingle("system"))) {
              url = t.getNamedChildValueSingle("value");
            }
          }
        }
      }
      String pub = element.getNamedChildValueSingle("publisher");
      if (pub == null) {
        x.ahOrNot(url).tx("n/a");
      } else {
        pub = pub.toLowerCase();
        String n = "external";
        if (Utilities.containsInList(pub, "health level seven", "health level 7", "hl7")) {
          n = "HL7";
        } else if (Utilities.containsInList(pub, "international organization for standardization", "(iso)")) {
          n = "ISO";
        } else if (Utilities.containsInList(pub, "iana")) {
          n = "IANA";
        } else if (Utilities.containsInList(pub, "world health")) {
          n = "WHO";
        } else if (Utilities.containsInList(pub, "cdc")) {
          n = "CDC";
        } else if (Utilities.containsInList(pub, "national library of medicine")) {
          n = "NLM";
        } else if (Utilities.containsInList(pub, "x12")) {
          n = "X12";
        } else if (Utilities.containsInList(pub, "national council for prescription drug programs")) {
          n = "NCPDP";
        } else if (Utilities.containsInList(pub, "national cancer institute", "(nci)")) {
          n ="NCI";
        } else if (Utilities.containsInList(pub, "national institute of standards and technology")) {
          n = "NIST";
        } else if (Utilities.containsInList(pub, "medicaid")) {
          n ="CMS";
        }
        x.ahOrNot(url, pub).tx(n);
      }
    }
  }

  private String describeCopyRight(String copyright, String label) {
    Set<String> labels = new HashSet<>();
    if (label != null) {
      labels.add(label);
    } else if (copyright != null) {
      copyright = copyright.toLowerCase();
      if (copyright.contains("(tho)") || copyright.contains("hl7 terminology") || copyright.contains("terminology.hl7")) {
        labels.add("THO");
      }
      if (copyright.contains("dicom")) {
        labels.add("DICOM");
      }
      if (copyright.contains("loinc")) {
        labels.add("LOINC");
      }
      if (copyright.contains("snomed")) {
        labels.add("SCT");
      }
      if (copyright.contains("iso.org")) {
        labels.add("ISO");
      }
      if (copyright.contains("creative commons public domain")) {
        labels.add("CC0");
      }
      if (labels.isEmpty()) {
        labels.add("other");
      }
    } else {
      labels.add("n/a");
    }
    return CommaSeparatedStringBuilder.join(",", Utilities.sorted(labels));
  }

  private String trimPara(String output) {
    if (output.startsWith("<p>") && output.endsWith("</p>\n") && !output.substring(3).contains("<p>"))
      return output.substring(0, output.length()-5).substring(3);
    else
      return output;
  }


  private String getListId(FetchedResource lr) {
    return lr.fhirType()+"/"+lr.getId();
  }

  private String getListLink(FetchedResource lr) {
    String res;
    if (lr.getResource() != null && lr.getResource().hasWebPath())
      res = lr.getResource().getWebPath();
    else
      res = pf.igpkp.getLinkFor(lr, true);
    return res;
  }

  private String getListName(FetchedResource lr) {
    if (lr.getResource() != null) {
      if (lr.getResource() instanceof CanonicalResource)
        return ((CanonicalResource)lr.getResource()).getName();
      return lr.getResource().fhirType()+"/"+lr.getResource().getId();
    }
    else {
      // well, as a non-metadata resource, we don't really have a name. We'll use the link
      return getListLink(lr);
    }
  }

  private String getListTitle(FetchedResource lr) {
    if (lr.getResource() != null) {
      if (lr.getResource() instanceof CanonicalResource)
        return ((CanonicalResource)lr.getResource()).present();
      return lr.getResource().fhirType()+"/"+lr.getResource().getId();
    }
    else {
      // well, as a non-metadata resource, we don't really have a name. We'll use the link
      return getListLink(lr);
    }
  }

  private String getListDesc(FetchedResource lr) {
    if (lr.getResource() != null) {
      if (lr.getResource() instanceof CanonicalResource)
        return ((CanonicalResource)lr.getResource()).getDescription();
      return lr.getResource().fhirType()+"/"+lr.getResource().getId();
    }
    else
      return null;
  }

  private String getListId(Resource r) {
    return r.fhirType()+"/"+r.getId();
  }

  private String getListLink(Resource r) {
    return r.getWebPath();
  }

  private String getListName(Resource r) {
    if (r instanceof CanonicalResource)
      return ((CanonicalResource) r).getName();
    return r.fhirType()+"/"+r.getId();
  }

  private String getListDesc(Resource r) {
    if (r instanceof CanonicalResource)
      return ((CanonicalResource) r).getName();
    return r.fhirType()+"/"+r.getId();
  }

  private Map<String, String> makeVars(FetchedResource r) {
    Map<String, String> map = new HashMap<String, String>();
    if (r.getResource() != null) {
      switch (r.getResource().getResourceType()) {
        case StructureDefinition:
          StructureDefinition sd = (StructureDefinition) r.getResource();
          String url = sd.getBaseDefinition();
          StructureDefinition base = pf.context.fetchResource(StructureDefinition.class, url);
          if (base != null) {
            map.put("parent-name", base.getName());
            map.put("parent-link", base.getWebPath());
          } else {
            map.put("parent-name", "?? Unknown reference");
            map.put("parent-link", "??");
          }
          map.put("sd.Type", sd.getType());
          map.put("sd.Type-plural", Utilities.pluralize(sd.getType(), 2));
          map.put("sd.type", !sd.hasType() ? "" : sd.getType().toLowerCase());
          map.put("sd.type-plural", !sd.hasType() ? "" : Utilities.pluralize(sd.getType(), 2).toLowerCase());
          return map;
        default: return null;
      }
    } else
      return null;
  }

  /**
   * saves the resource as XML, JSON, Turtle,
   * then all 3 of those as html with embedded links to the definitions
   * then the narrative as html
   *
   * @param r
   * @throws IOException
   * @throws FHIRException
   * @throws FileNotFoundException
   * @throws Exception
   */
  private byte[] saveNativeResourceOutputs(FetchedFile f, FetchedResource r) throws FHIRException, IOException {
    ByteArrayOutputStream bsj = new ByteArrayOutputStream();
    org.hl7.fhir.r5.elementmodel.JsonParser jp = new org.hl7.fhir.r5.elementmodel.JsonParser(this.pf.context);
    Element element = r.getElement();
    Element eNN = element;
    jp.compose(element, bsj, IParser.OutputStyle.NORMAL, this.pf.igpkp.getCanonical());
    if (!r.isCustomResource()) {
      this.pf.npm.addFile(isExample(f,r ) ? NPMPackageGenerator.Category.EXAMPLE : NPMPackageGenerator.Category.RESOURCE, element.fhirTypeRoot()+"-"+r.getId()+".json", bsj.toByteArray());
      if (isNewML()) {
        for (String l : allLangs()) {
          Element le = this.pf.langUtils.copyToLanguage(element, l, true, r.getElement().getChildValue("language"), pf.defaultTranslationLang, r.getErrors()); // todo: should we keep this?
          ByteArrayOutputStream bsjl = new ByteArrayOutputStream();
          jp.compose(le, bsjl, IParser.OutputStyle.NORMAL, this.pf.igpkp.getCanonical());
          this.pf.lnpms.get(l).addFile(isExample(f,r ) ? NPMPackageGenerator.Category.EXAMPLE : NPMPackageGenerator.Category.RESOURCE, element.fhirTypeRoot()+"-"+r.getId()+".json", bsjl.toByteArray());
        }
      }
      for (String v : this.pf.generateVersions) {
        String ver = VersionUtilities.versionFromCode(v);
        Resource res = r.hasOtherVersions() && r.getOtherVersions().containsKey(ver+"-"+r.fhirType()) ? r.getOtherVersions().get(ver+"-"+r.fhirType()).getResource() : r.getResource();
        if (res != null) {
          byte[] resVer = null;
          try {
            resVer = convVersion(res.copy(), ver);
          } catch (Exception e) {
            System.out.println("Unable to convert "+res.fhirType()+"/"+res.getId()+" to "+ver+": "+e.getMessage());
            resVer = null;
          }
          if (resVer != null) {
            this.pf.vnpms.get(v).addFile(isExample(f,r ) ? NPMPackageGenerator.Category.EXAMPLE : NPMPackageGenerator.Category.RESOURCE, element.fhirTypeRoot()+"-"+r.getId()+".json", resVer);
          }
        }
      }
      if (r.getResource() != null && r.getResource().hasUserData(UserDataNames.archetypeSource)) {
        addFileToNpm(NPMPackageGenerator.Category.ADL, r.getResource().getUserString(UserDataNames.archetypeName), r.getResource().getUserString(UserDataNames.archetypeSource).getBytes(StandardCharsets.UTF_8));
      }

    } else  if ("StructureDefinition".equals(r.fhirType())) {
      // GG 20-Feb 2025 - how can you ever get to here?
      addFileToNpm(NPMPackageGenerator.Category.RESOURCE, element.fhirType()+"-"+r.getId()+".json", bsj.toByteArray());
      StructureDefinition sdt = (StructureDefinition) r.getResource().copy();
      sdt.setKind(StructureDefinition.StructureDefinitionKind.RESOURCE);
      bsj = new ByteArrayOutputStream();
      new JsonParser().setOutputStyle(IParser.OutputStyle.NORMAL).compose(bsj, sdt);
      addFileToNpm(NPMPackageGenerator.Category.CUSTOM, "StructureDefinition-"+r.getId()+".json", bsj.toByteArray());
    } else {
      addFileToNpm(NPMPackageGenerator.Category.CUSTOM, element.fhirType()+"-"+r.getId()+".json", bsj.toByteArray());
      Binary bin = new Binary("application/fhir+json");
      bin.setId(r.getId());
      bin.setContent(bsj.toByteArray());
      bsj = new ByteArrayOutputStream();
      new JsonParser().setOutputStyle(IParser.OutputStyle.NORMAL).compose(bsj, bin);
      addFileToNpm(isExample(f,r ) ? NPMPackageGenerator.Category.EXAMPLE : NPMPackageGenerator.Category.RESOURCE, "Binary-"+r.getId()+".json", bsj.toByteArray());
    }

    if (this.pf.module.isNoNarrative()) {
      // we don't use the narrative in these resources in _includes, so we strip it - it slows Jekyll down greatly
      eNN = (Element) element.copy();
      eNN.removeChild("text");
      bsj = new ByteArrayOutputStream();
      jp.compose(eNN, bsj, IParser.OutputStyle.PRETTY, this.pf.igpkp.getCanonical());
    }
    String path = Utilities.path(this.pf.tempDir, "_includes", r.fhirType()+"-"+r.getId()+".json");
    FileUtilities.bytesToFile(bsj.toByteArray(), path);
    String pathEsc = Utilities.path(this.pf.tempDir, "_includes", r.fhirType()+"-"+r.getId()+".escaped.json");
    XmlEscaper.convert(path, pathEsc);

    saveNativeResourceOutputFormats(f, r, element, "");
    for (String lang : allLangs()) {
      Element e = (Element) element.copy();
      if (this.pf.langUtils.switchLanguage(e, lang, true, r.getElement().getChildValue("language"), pf.defaultTranslationLang, r.getErrors())) {
        saveNativeResourceOutputFormats(f, r, e, lang);
      }
    }

    return bsj.toByteArray();
  }


  private String generateResourceFragment(FetchedFile f, FetchedResource r, String fragExpr, String syntax, List<PublisherUtils.ElideExceptDetails> excepts, List<String> elides) throws FHIRException {
    FHIRPathEngine fpe = new FHIRPathEngine(this.pf.context);
    Base root = r.getElement();
    if (r.getLogicalElement()!=null)
      root = r.getLogicalElement();

    List<Base> fragNodes = new ArrayList<Base>();
    if (fragExpr == null)
      fragNodes.add(root);
    else {
      try {
        fragNodes = fpe.evaluate(root, fragExpr);
      } catch (Exception e) {
        e.printStackTrace();
      }
      if (fragNodes.isEmpty()) {
        f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.EXCEPTION, fragExpr, "Unable to resolve expression to fragment within resource", ValidationMessage.IssueSeverity.ERROR));
        return "ERROR Expanding Fragment";

      } else if (fragNodes.size() > 1) {
        f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.EXCEPTION, fragExpr, "Found multiple occurrences of expression within resource, and only one is allowed when extracting a fragment", ValidationMessage.IssueSeverity.ERROR));
        return "ERROR Expanding Fragment";
      }
    }
    Element e = (Element)fragNodes.get(0);
    Element jsonElement = e;

    if (!elides.isEmpty() || !excepts.isEmpty()) {
//        e = e.copy();
      for (PublisherUtils.ElideExceptDetails elideExceptDetails : excepts) {
        List<Base> baseElements = new ArrayList<Base>();
        baseElements.add(e);
        String elideBaseExpr = null;
        if (elideExceptDetails.hasBase()) {
          elideBaseExpr = elideExceptDetails.getBase();
          baseElements = fpe.evaluate(e, elideBaseExpr);
          if (baseElements.isEmpty()) {
            f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.EXCEPTION, fragExpr, "Unable to find matching base elements for elideExcept expression " + elideBaseExpr + " within fragment path ", ValidationMessage.IssueSeverity.ERROR));
            return "ERROR Expanding Fragment";
          }
        }

        String elideExceptExpr = elideExceptDetails.getExcept();
        boolean foundExclude = false;
        for (Base elideElement: baseElements) {
          for (Element child: ((Element)elideElement).getChildren()) {
            child.setElided(true);
          }
          List<Base> elideExceptElements = fpe.evaluate(elideElement, elideExceptExpr);
          if (!elideExceptElements.isEmpty())
            foundExclude = true;
          for (Base exclude: elideExceptElements) {
            ((Element)exclude).setElided(false);
          }
        }
        if (!foundExclude) {
          f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.EXCEPTION, fragExpr, "Unable to find matching exclude elements for elideExcept expression " + elideExceptExpr + (elideBaseExpr == null ? "": (" within base" + elideBaseExpr)) + " within fragment path ", ValidationMessage.IssueSeverity.ERROR));
          return "ERROR Expanding Fragment";
        }
      }

      for (String elideExpr : elides) {
        List<Base> elideElements = fpe.evaluate(e, elideExpr);
        if (elideElements.isEmpty()) {
          f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.EXCEPTION, fragExpr, "Unable to find matching elements for elide expression " + elideExpr + " within fragment path ", ValidationMessage.IssueSeverity.ERROR));
          return "ERROR Expanding Fragment";
        }
        for (Base elideElment: elideElements) {
          ((Element)elideElment).setElided(true);
        }
      }

      jsonElement = trimElided(e, true);
      e = trimElided(e, false);
    }

    try {
      if (syntax.equals("xml")) {
        org.hl7.fhir.r5.elementmodel.XmlParser xp = new org.hl7.fhir.r5.elementmodel.XmlParser(this.pf.context);
        XmlXHtmlRenderer x = new XmlXHtmlRenderer();
        x.setPrism(true);
        xp.setElideElements(true);
        xp.setLinkResolver(this.pf.igpkp);
        xp.setShowDecorations(false);
        if (suppressId(f, r)) {
          xp.setIdPolicy(ParserBase.IdRenderingPolicy.NotRoot);
        }
        xp.compose(e, x);
        return x.toString();

      } else if (syntax.equals("json")) {
        JsonXhtmlRenderer j = new JsonXhtmlRenderer();
        j.setPrism(true);
        org.hl7.fhir.r5.elementmodel.JsonParser jp = new org.hl7.fhir.r5.elementmodel.JsonParser(this.pf.context);
        jp.setLinkResolver(this.pf.igpkp);
        jp.setAllowComments(true);
        jp.setElideElements(true);
/*        if (fragExpr != null || r.getLogicalElement() != null)
          jp.setSuppressResourceType(true);*/
        if (suppressId(f, r)) {
          jp.setIdPolicy(ParserBase.IdRenderingPolicy.NotRoot);
        }
        jp.compose(jsonElement, j);
        return j.toString();

      } else if (syntax.equals("ttl")) {
        org.hl7.fhir.r5.elementmodel.TurtleParser ttl = new org.hl7.fhir.r5.elementmodel.TurtleParser(this.pf.context);
        ttl.setLinkResolver(this.pf.igpkp);
        Turtle rdf = new Turtle();
        if (suppressId(f, r)) {
          ttl.setIdPolicy(ParserBase.IdRenderingPolicy.NotRoot);
        }
        ttl.setStyle(IParser.OutputStyle.PRETTY);
        ttl.compose(e, rdf, "");
        return rdf.toString();
      } else
        throw new FHIRException("Unrecognized syntax: " + syntax);
    } catch (Exception except) {
      throw new FHIRException(except);
    }
  }

  /*
   Recursively removes consecutive elided elements from children of the element
   */
  private Element trimElided(Element e, boolean asJson) {
    Element trimmed = (Element)e.copy();
    trimElide(trimmed, asJson);
    return trimmed;
  }

  private void trimElide(Element e, boolean asJson) {
    if (!e.hasChildren())
      return;

    boolean inElided = false;
    for (int i = 0; i < e.getChildren().size();) {
      Element child = e.getChildren().get(i);
      if (child.isElided()) {
        if (inElided) {
          // Check to see if this an elided collection item where the previous item isn't in the collection and the following item is in the collection and isn't elided
          if (asJson && i > 0 && i < e.getChildren().size()-1 && !e.getChildren().get(i-1).getName().equals(child.getName()) && !e.getChildren().get(i+1).isElided() && e.getChildren().get(i+1).getName().equals(child.getName())) {
            // Do nothing
          } else {
            e.getChildren().remove(child);
            continue;
          }
        } else
          inElided = true;
      } else {
        inElided = false;
        trimElide(child, asJson);
      }
      i++;
    }
  }

  private byte[] processCustomLiquid(DBBuilder db, byte[] content, FetchedFile f, String lang) throws FHIRException {
    if (!Utilities.existsInList(Utilities.getFileExtension(f.getPath()), "html", "md", "xml")) {
      return content;
    }

    String src = new String(content);
    try {
      scanForValidationFragments(f, src);
    } catch (Exception e) {
      System.out.println("Error scanning for validation fragments: "+e.getMessage());
    }
    try {
      boolean changed = false;
      String[] keywords = {"sql", "fragment", "json", "class-diagram", "uml", "multi-map", "lang-fragment", "dataset"};
      for (String keyword: Arrays.asList(keywords)) {

        while (db != null && src.contains("{% " + keyword)) {
          int i = src.indexOf("{% " + keyword);
          String pfx = src.substring(0, i);
          src = src.substring(i + 3 + keyword.length());
          i = src.indexOf("%}");
          if (i == -1)
            throw new FHIRException("No closing '%}' for '{% '" + keyword + " in " + f.getName());
          String sfx = src.substring(i + 2);
          String arguments = src.substring(0, i).trim();

          String substitute = "";
          try {
            switch (keyword) {
              case "sql":
                if (arguments.trim().startsWith("ToData ")) {
                  substitute = processSQLData(db, arguments.substring(arguments.indexOf("ToData ") + 7), f);
                } else {
                  substitute = processSQLCommand(db, arguments, f);
                }
                break;

              case "multi-map" :
                substitute = buildMultiMap(arguments, f);
                break;

              case "lang-fragment":
                if (isNewML()) {
                  substitute = "{% include " + arguments.trim().replace(".xhtml", "") + "-" + (lang == null ? "en" : lang) + ".xhtml %}";
                } else {
                  substitute = "{% include " + arguments +" %}";
                }
                break;

              case "fragment":
                substitute = processFragment(arguments, f);
                break;

              case "json":
                substitute = processJson(arguments, f);
                break;

              case "dataset":
                substitute = processDataset(arguments, f);
                break;

              case "class-diagram":
                substitute = processClassDiagram(arguments, f);
                break;

              default:
                throw new FHIRException("Internal Error - unknown keyword "+keyword);
            }
          } catch (Exception e) {
            if (this.settings.isDebug()) {
              e.printStackTrace();
            } else {
              System.out.println("Error processing custom liquid in "+f.getName()+": " + e.getMessage());
            }
            substitute = "<p>Error processing command: "+Utilities.escapeXml(e.getMessage());
          }

          src = pfx + substitute + sfx;
          changed = true;
        }
        while (db != null && src.contains("{%! " + keyword)) {
          int i = src.indexOf("{%! " + keyword);
          String pfx = src.substring(0, i);
          src = src.substring(i + 3);
          i = src.indexOf("%}");
          String sfx = src.substring(i+2);
          src = src.substring(0, i);
          src = pfx + "{% raw %}{%"+src+"%}{% endraw %}"+ sfx;
          changed = true;
        }
      }
      while (src.contains("[[[")) {
        int i = src.indexOf("[[[");
        String pfx = src.substring(0, i);
        src = src.substring(i+3);
        i = src.indexOf("]]]");
        String sfx = src.substring(i+3);
        src = src.substring(0, i);
        src = pfx+processRefTag(db, src, f)+sfx;
        changed = true;
      }
      if (changed) {
        return src.replace("[[~[", "[[[").getBytes(StandardCharsets.UTF_8);
      } else {
        return content;
      }
    } catch (Exception e) {
      if (this.settings.isDebug()) {
        e.printStackTrace();
      } else {
        System.out.println("Error processing custom liquid in "+f.getName()+": " + e.getMessage());
      }
      return content;
    }
  }

  private void scanForValidationFragments(FetchedFile f, String html) {
    int i = 0;
    Pattern pattern = Pattern.compile(
            "<pre[^>]*\\bvalidationRule=[\"']([^\"']*)[\"'][^>]*>(.*?)</pre>",
            Pattern.DOTALL
    );
    Matcher matcher = pattern.matcher(html);

    while (matcher.find()) {
      String validationRule = matcher.group(1);
      String content = matcher.group(2);

      String path = "pre["+i+"]";
      if (validationRule.contains(":")) {
        path = validationRule.substring(validationRule.indexOf(":") + 1);
        validationRule = validationRule.substring(0, validationRule.indexOf(":"));
      }
      // Process the content
      validateFragment(f, validationRule, content, path);
      i++;
    }
  }

  private void validateFragment(FetchedFile f, String type, String content, String path) {
    String src = StringEscapeUtils.unescapeHtml4(content).trim();
    try {
      if (src.startsWith("{")) {
        org.hl7.fhir.r5.elementmodel.JsonParser p = (org.hl7.fhir.r5.elementmodel.JsonParser) Manager.makeParser(pf.context, Manager.FhirFormat.JSON);
        p.setupValidation(ParserBase.ValidationPolicy.QUICK);
        p.parse(src, type, false);
      } else {
        src = "<"+type+" xmlns=\"http://hl7.org/fhir\">"+src+"</"+type+">";
        DocumentBuilderFactory factory = XMLUtil.newXXEProtectedDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(src));
        Document doc = builder.parse(is);
        org.w3c.dom.Element base = doc.getDocumentElement();
        org.hl7.fhir.r5.elementmodel.XmlParser p = (org.hl7.fhir.r5.elementmodel.XmlParser) Manager.makeParser(pf.context, Manager.FhirFormat.XML);
        p.setupValidation(ParserBase.ValidationPolicy.QUICK);
        p.parse(null, XMLUtil.getFirstChild(base), type);
      }
    } catch (Exception e) {
      f.getErrors().add(new ValidationMessage(ValidationMessage.Source.TerminologyEngine, ValidationMessage.IssueType.STRUCTURE, path, e.getMessage(), ValidationMessage.IssueSeverity.ERROR));
    }
  }

  private String processClassDiagram(String arguments, FetchedFile f) {
    try {
      JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(arguments);
      return new ClassDiagramRenderer(Utilities.path(this.pf.rootDir, "input", "diagrams"), Utilities.path(this.pf.rootDir, "temp", "diagrams"), json.asString("id"), json.asString("prefix"), this.pf.rc, null).buildClassDiagram(json);
    } catch (Exception e) {
      e.printStackTrace();
      return "<p style=\"color: maroon\"><b>"+Utilities.escapeXml(e.getMessage())+"</b></p>";
    }
  }

  private String buildMultiMap(String arguments, FetchedFile f) {
    try {
      JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(arguments);
      return new MultiMapBuilder(this.pf.rc).buildMap(json);
    } catch (Exception e) {
      e.printStackTrace();
      return "<p style=\"color: maroon\"><b>"+Utilities.escapeXml(e.getMessage())+"</b></p>";
    }
  }

  private String processRefTag(DBBuilder db, String src, FetchedFile f) {
    if (Utilities.existsInList(src, "$ver")) {
      switch (src) {
        case "$ver": return this.pf.businessVersion;
      }
    } else if (Utilities.isAbsoluteUrl(src)) {

      try {
        CanonicalResource cr = (CanonicalResource) this.pf.context.fetchResource(Resource.class, src);
        if (cr != null && cr.hasWebPath()) {
          return "<a href=\""+cr.getWebPath()+"\">"+Utilities.escapeXml(cr.present())+"</a>";
        }
      } catch (Exception e) {
      }
    } else {
      for (FetchedFile f1 : this.pf.fileList) {
        for (FetchedResource r : f1.getResources()) {
          if (r.getResource() instanceof CanonicalResource) {
            CanonicalResource cr = (CanonicalResource) r.getResource();
            if (src.equalsIgnoreCase(cr.getName()) && cr.hasWebPath()) {
              return "<a href=\""+cr.getWebPath()+"\">"+Utilities.escapeXml(cr.present())+"</a>";
            }
          }
        }
      }
      try {
        StructureDefinition sd = this.pf.context.fetchTypeDefinition(src);
        if (sd != null) {
          return "<a href=\""+sd.getWebPath()+"\">"+Utilities.escapeXml(sd.present())+"</a>";
        }
      } catch (Exception e) {
        // nothing
      }
    }
    for (RelatedIG rig : this.pf.relatedIGs) {
      if (rig.getId().equals(src) && rig.getWebLocation() != null) {
        return "<a href=\""+rig.getWebLocation()+"\">"+Utilities.escapeXml(rig.getTitle())+"</a>";
      }
    }
    // use [[~[ so we don't get stuck in a loop
    return "[[~["+src+"]]]";
  }


  private String processSQLCommand(DBBuilder db, String src, FetchedFile f) throws FHIRException, IOException {
    long start = System.currentTimeMillis();
    String output = db == null ? "<span style=\"color: maroon\" data-fhir=\"generated\">No SQL this build</span>" : db.processSQL(src);
    int i = this.pf.sqlIndex++;
    fragment("sql-"+i+"-fragment", output, f.getOutputNames(), start, "sql", "SQL", null);
    return "{% include sql-"+i+"-fragment.xhtml %}";
  }

  private String processDataset(String arguments, FetchedFile f) throws FHIRException, IOException {
    String[] params = arguments.split(" ");
    DataSetInformation dsi = null;
    for (DataSetInformation t : pf.dataSets) {
      if (t.name().equals(params[1])) {
        dsi = t;
      }
    }
    if (dsi == null) {
      throw new Error("Unable to find dataset "+params[1]);
    }
    return dsi.buildFragment(params[0]);
  }

  private String processJson(String arguments, FetchedFile f) throws FHIRException, IOException {
    long start = System.currentTimeMillis();
    String cnt = null;
    try {
      String args = arguments.trim();
      File src = new File(Utilities.path(FileUtilities.getDirectoryForFile(this.settings.getConfigFile()), args.substring(0, args.indexOf(" ")).trim()));
      File tsrc = new File(Utilities.path(FileUtilities.getDirectoryForFile(this.settings.getConfigFile()), args.substring(args.indexOf(" ")+1).trim()));

      JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(src);
      LiquidEngine liquid = new LiquidEngine(this.pf.context, this.pf.rc.getServices());
      LiquidEngine.LiquidDocument template = liquid.parse(FileUtilities.fileToString(tsrc), tsrc.getAbsolutePath());
      BaseJsonWrapper base = new BaseJsonWrapper(json);
      cnt = liquid.evaluate(template, base, this).trim();
    } catch (Exception e) {
      XhtmlNode p = new XhtmlNode(NodeType.Element, "p");
      p.tx(e.getMessage());
      cnt = new XhtmlComposer(false, true).compose(p);
    }
    int i = this.pf.sqlIndex++;
    fragment("json-"+i+"-fragment", "\r\n"+cnt, f.getOutputNames(), start, "json", "page", null);
    return "{% include json-"+i+"-fragment.xhtml %}";
  }

  private String processFragment(String arguments, FetchedFile f) throws FHIRException {
    int firstSpace = arguments.indexOf(" ");
    int secondSpace = arguments.indexOf(" ",firstSpace + 1);
    if (firstSpace == -1)
      throw new FHIRException("Fragment syntax error: syntax must be '[ResourceType]/[id] [syntax] [filters]'.  Found: " + arguments + "\r\n in file " + f.getName());
    String reference = arguments.substring(0, firstSpace);
    String format = (secondSpace == -1) ? arguments.substring(firstSpace) : arguments.substring(firstSpace, secondSpace);
    format = format.trim().toLowerCase();
    String filters = (secondSpace == -1) ? "" : arguments.substring(secondSpace).trim();
    Pattern refPattern = Pattern.compile("^([A-Za-z]+)\\/([A-Za-z0-9\\-\\.]{1,64})$");
    Matcher refMatcher = refPattern.matcher(reference);
    if (!refMatcher.find())
      throw new FHIRException("Fragment syntax error: Referenced instance must be expressed as [ResourceType]/[id].  Found " + reference + " in file " + f.getName());
    String type = refMatcher.group(1);
    String id = refMatcher.group(2);
    FetchedResource r = fetchByResource(type, id);
    if (r == null)
      throw new FHIRException(("Unable to find fragment resource " + reference + " pointed to in file " + f.getName()));
    if (!format.equals("xml") && !format.equals("json") && !format.equals("ttl"))
      throw new FHIRException("Unrecognized fragment format " + format + " - expecting 'xml', 'json', or 'ttl' in file " + f.getName());

    Pattern filterPattern = Pattern.compile("(BASE:|EXCEPT:|ELIDE:)");
    Matcher filterMatcher = filterPattern.matcher(filters);
    String remainingFilters = filters;
    String base = null;
    List<String> elides = new ArrayList<>();
    List<String> includes = new ArrayList<>();
    List<PublisherUtils.ElideExceptDetails> excepts = new ArrayList<>();
    PublisherUtils.ElideExceptDetails currentExcept = null;
    boolean matches = filterMatcher.find();
    if (!matches && !filters.isEmpty())
      throw new FHIRException("Unrecognized filters in fragment: " + filters + " in file " + f.getName());
    while (matches) {
      String filterType = filterMatcher.group(0);
      String filterText = "";
      int start = remainingFilters.indexOf(filterType) + filterType.length();
      matches = filterMatcher.find();
      if (matches) {
        String nextTag = filterMatcher.group(0);
        filterText = remainingFilters.substring(start, remainingFilters.indexOf(nextTag, start)).trim();
        remainingFilters = remainingFilters.substring(remainingFilters.indexOf(nextTag, start));
      } else {
        filterText = remainingFilters.substring(start).trim();
        remainingFilters = "";
      }
      switch (filterType) {
        case "BASE:":
          if (currentExcept==null) {
            if (base != null)
              throw new FHIRException("Cannot have more than one BASE: declaration in fragment definition - " + filters + " in file " + f.getName());
            base = filterText;
          } else {
            if (currentExcept.hasBase())
              throw new FHIRException("Cannot have more than one BASE: declaration for an Except - " + filters + " in file " + f.getName());
            currentExcept.setBase(filterText);
          }
          break;
        case "EXCEPT:":
          currentExcept = new PublisherUtils.ElideExceptDetails(filterText);
          excepts.add(currentExcept);
          break;
        default: // "ELIDE:"
          elides.add(filterText);
      }
    }

    return generateResourceFragment(f, r, base, format, excepts, elides);
  }

  private String processSQLData(DBBuilder db, String src, FetchedFile f) throws FHIRException, IOException {
    long start = System.currentTimeMillis();

    if (db == null) {
      return "<span style=\"color: maroon\">No SQL this build</span>";
    }

    String[] parts = src.trim().split("\\s+", 2);
    String fileName = parts[0];
    String sql = parts[1];

    try {
      String json = db.executeQueryToJson(sql);
      String outputPath = Utilities.path(this.pf.tempDir, "_data", fileName + ".json");
      FileUtilities.stringToFile(json, outputPath);
      return "{% assign " + fileName + " = site.data." + fileName + " %}";
    } catch (Exception e) {
      return "<span style=\"color: maroon\">Error processing SQL: " + Utilities.escapeXml(e.getMessage()) + "</span>";
    }
  }


  private void generateFragmentUsage() throws IOException {
    log("Generate fragment-usage-analysis.csv");
    StringBuilder b = new StringBuilder();
    b.append("Fragment");
    b.append(",");
    b.append("Count");
    b.append(",");
    b.append("Time (ms)");
    b.append(",");
    b.append("Size (bytes)");
    if (settings.isTrackFragments()) {
      b.append(",");
      b.append("Used?");
    }
    b.append("\r\n");
    for (String n : Utilities.sorted(pf.fragmentUses.keySet())) {
      if (pf.fragmentUses.get(n).used) {
        b.append(n);
        b.append(",");
        pf.fragmentUses.get(n).produce(b);
        b.append("\r\n");
      }
    }
    for (String n : Utilities.sorted(pf.fragmentUses.keySet())) {
      if (!pf.fragmentUses.get(n).used) {
        b.append(n);
        b.append(",");
        pf.fragmentUses.get(n).produce(b);
        b.append("\r\n");
      }
    }
    FileUtilities.stringToFile(b.toString(), Utilities.path(pf.outputDir, "fragment-usage-analysis.csv"));
  }

  private void generatePackageVersion(String filename, String ver) throws IOException {
    NpmPackageVersionConverter self = new NpmPackageVersionConverter(filename, Utilities.path(FileUtilities.getDirectoryForFile(filename), pf.publishedIg.getPackageId()+"."+ver+".tgz"), ver, pf.publishedIg.getPackageId()+"."+ver, pf.context);
    self.execute();
    for (String s : self.getErrors()) {
      pf.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.EXCEPTION, "ImplementationGuide", "Error creating "+ver+" package: "+s, ValidationMessage.IssueSeverity.ERROR));
    }
  }


  private String addLangFolderToFilename(String path, String lang) throws IOException {
    int index = path.lastIndexOf(File.separator);
    if (index < 1) {
      return path; // that's actually an error
    } else {
      return Utilities.path(path.substring(0, index),lang, path.substring(index+1));
    }
  }


  private byte[] loadTranslationSource(FetchedFile f, String l) throws IOException, FileNotFoundException {
    byte[] src;
    if (this.pf.defaultTranslationLang.equals(l)) {
      src = f.getSource();
    } else {
      File ff = null;
      for (String ts : this.pf.translationSources) {
        if (Utilities.endsWithInList(ts, "/"+l, "\\"+l, "-"+l)) {
          File t = new File(Utilities.path(this.pf.rootDir, ts, f.getLoadPath()));
          if (t.exists()) {
            ff = t;
            f.setTranslation(l, true);
            break;
          }
        }
      }
      if (ff != null) {
        src = FileUtilities.fileToBytes(ff);
      } else {
        src = f.getSource();
      }
    }
    return src;
  }

  private String genContainedIndex(FetchedResource r, List<StringPair> clist, String lang) {
    StringBuilder b = new StringBuilder();
    if (clist.size() > 0) {
      b.append("<ul data-fhir=\"generated\">\r\n");
      for (StringPair sp : clist) {
        b.append("<li><a href=\""+sp.getValue()+"\">"+Utilities.escapeXml(sp.getName())+"</a></li>\r\n");
      }
      b.append("</ul>\r\n");
    }
    return b.toString();
  }

  private byte[] stripFrontMatter(byte[] source) {
    String src = new String(source, StandardCharsets.UTF_8);
    if (src.startsWith("---")) {
      String t = src.substring(3);
      int i = t.indexOf("---");
      if (i >= 0) {
        src = t.substring(i+3);
      }
    }
    return src.getBytes(StandardCharsets.UTF_8);
  }


  private ProvenanceDetails processProvenanceForBundle(FetchedFile f, String path, Element r) throws Exception {
    Provenance pv = (Provenance) convertFromElement(r);
    ProvenanceDetails pd = processProvenance(path, pv);

    for (Reference entity : pv.getTarget()) {
      String ref = entity.getReference();
      FetchedResource target = getResourceForRef(f, ref);
      String p, d;
      if (target == null) {
        p = null;
        d = entity.hasDisplay() ? entity.getDisplay() : ref;
      } else {
        p = this.pf.igpkp.getLinkFor(target, true);
        d = target.getTitle() != null ? target.getTitle() : entity.hasDisplay() ? entity.getDisplay() : ref;
      }
      pd.getTargets().add(pd.new ProvenanceDetailsTarget(p, d));
    }
    return pd;
  }


  /**
   * None of the fragments presently generated include {{ }} liquid tags. So any
   * liquid tags found in the fragments are actually what should be displayed post-jekyll.
   * So we're going to globally escape them here. If any fragments want to include actual
   * Jekyll tags, we'll have to do something much harder.
   *
   * see https://stackoverflow.com/questions/24102498/escaping-double-curly-braces-inside-a-markdown-code-block-in-jekyll
   *
   * @return
   */
  private String wrapLiquid(String content) {
    return "{% raw %}"+content+"{% endraw %}";
  }



  private String pageWrap(String content, String title) {
    return "<html>\r\n"+
            "<head>\r\n"+
            "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/>\r\n"+
            "  <title>"+title+"</title>\r\n"+
            "  <link rel=\"stylesheet\" href=\"fhir.css\"/>\r\n"+
            "</head>\r\n"+
            "<body>\r\n"+
            content+
            "</body>\r\n"+
            "</html>\r\n";
  }

  private void saveCSList(String name, List<CodeSystem> cslist, DBBuilder db, int view) throws Exception {
    StringBuilder b = new StringBuilder();
    JsonObject json = new JsonObject();
    JsonArray items = new JsonArray();
    json.add("codeSystems", items);

    b.append("URL,Version,Status,OIDs,Name,Title,Description,Used\r\n");

    for (CodeSystem cs : cslist) {

      JsonObject item = new JsonObject();
      items.add(item);
      item.add("url", cs.getUrl());
      item.add("version", cs.getVersion());
      if (cs.hasStatus()) {
        item.add("status", cs.getStatus().toCode());
      }
      item.add("name", cs.getName());
      item.add("title", cs.getTitle());
      item.add("description", preProcessMarkdown(cs.getDescription()));

      Set<String> oids = TerminologyUtilities.listOids(cs);
      if (!oids.isEmpty()) {
        JsonArray oidArr = new JsonArray();
        item.add("oids", oidArr);
        for (String s : oids) {
          oidArr.add(s);
        }
      }
      Set<Resource> rl = (Set<Resource>) cs.getUserData(UserDataNames.pub_xref_used);
      Set<String> links = new HashSet<>();
      if (rl != null) {
        JsonObject uses = new JsonObject();
        item.add("uses", uses);
        for (Resource r : rl) {
          String title = (r instanceof CanonicalResource) ? ((CanonicalResource) r).present() : r.fhirType()+"/"+r.getIdBase();
          String link = r.getWebPath();
          links.add(r.fhirType()+"/"+r.getIdBase());
          if (link != null) {
            if (!item.has(link)) {
              item.add(link, title);
            } else if (!item.asString(link).equals(title)) {
              log("inconsistent link info for "+link+": already "+item.asString(link)+", now "+title);
            }
          }
        }
      }

      if (db != null) {
        db.addToCSList(view, cs, oids, rl);
      }

      b.append(cs.getUrl());
      b.append(",");
      b.append(cs.getVersion());
      b.append(",");
      if (cs.hasStatus()) {
        b.append(cs.getStatus().toCode());
      } else {
        b.append("");
      }
      b.append(",");
      b.append(oids.isEmpty() ? "" : "\""+CommaSeparatedStringBuilder.join(",", oids)+"\"");
      b.append(",");
      b.append(Utilities.escapeCSV(cs.getName()));
      b.append(",");
      b.append(Utilities.escapeCSV(cs.getTitle()));
      b.append(",");
      b.append("\""+Utilities.escapeCSV(cs.getDescription())+"\"");
      b.append(",");
      b.append(links.isEmpty() ? "" : "\""+CommaSeparatedStringBuilder.join(",", links)+"\"");
      b.append("\r\n");
    }
    FileUtilities.stringToFile(b.toString(), Utilities.path(pf.tempDir, name+".csv"));
    pf.otherFilesRun.add(Utilities.path(pf.tempDir, name+".csv"));
    org.hl7.fhir.utilities.json.parser.JsonParser.compose(json, new File(Utilities.path(pf.tempDir, name+".json")), true);
    pf.otherFilesRun.add(Utilities.path(pf.tempDir, name+".json"));
  }

  private void saveVSList(String name, List<ValueSet> vslist, DBBuilder db, int view) throws Exception {
    StringBuilder b = new StringBuilder();
    JsonObject json = new JsonObject();
    JsonArray items = new JsonArray();
    json.add("codeSystems", items);

    b.append("URL,Version,Status,OIDs,Name,Title,Description,Uses,Used,Sources\r\n");

    for (ValueSet vs : vslist) {

      JsonObject item = new JsonObject();
      items.add(item);
      item.add("url", vs.getUrl());
      item.add("version", vs.getVersion());
      if (vs.hasStatus()) {
        item.add("status", vs.getStatus().toCode());
      }
      item.add("name", vs.getName());
      item.add("title", vs.getTitle());
      item.add("description", preProcessMarkdown(vs.getDescription()));

      Set<String> used = ValueSetUtilities.listSystems(pf.context, vs);
      if (!used.isEmpty()) {
        JsonArray sysdArr = new JsonArray();
        item.add("systems", sysdArr);
        for (String s : used) {
          sysdArr.add(s);
        }
      }

      Set<String> oids = TerminologyUtilities.listOids(vs);
      if (!oids.isEmpty()) {
        JsonArray oidArr = new JsonArray();
        item.add("oids", oidArr);
        for (String s : oids) {
          oidArr.add(s);
        }
      }

      Set<String> sources = (Set<String>) vs.getUserData(UserDataNames.pub_xref_sources);
      if (!oids.isEmpty()) {
        JsonArray srcArr = new JsonArray();
        item.add("sources", srcArr);
        for (String s : oids) {
          srcArr.add(s);
        }
      }

      Set<Resource> rl = (Set<Resource>) vs.getUserData(UserDataNames.pub_xref_used);
      Set<String> links = new HashSet<>();
      if (rl != null) {
        JsonObject uses = new JsonObject();
        item.add("uses", uses);
        for (Resource r : rl) {
          String title = (r instanceof CanonicalResource) ? ((CanonicalResource) r).present() : r.fhirType()+"/"+r.getIdBase();
          String link = r.getWebPath();
          links.add(r.fhirType()+"/"+r.getIdBase());
          item.add(link,  title);
        }
      }

      if (db != null) {
        db.addToVSList(view, vs, oids, used, sources, rl);
      }

      b.append(vs.getUrl());
      b.append(",");
      b.append(vs.getVersion());
      b.append(",");
      if (vs.hasStatus()) {
        b.append(vs.getStatus().toCode());
      } else {
        b.append("");
      }
      b.append(",");
      b.append(oids.isEmpty() ? "" : "\""+CommaSeparatedStringBuilder.join(",", oids)+"\"");
      b.append(",");
      b.append(Utilities.escapeCSV(vs.getName()));
      b.append(",");
      b.append(Utilities.escapeCSV(vs.getTitle()));
      b.append(",");
      b.append("\""+Utilities.escapeCSV(vs.getDescription())+"\"");
      b.append(",");
      b.append(links.isEmpty() ? "" : "\""+CommaSeparatedStringBuilder.join(",", links)+"\"");
      b.append(",");
      b.append(sources.isEmpty() ? "" : "\""+CommaSeparatedStringBuilder.join(",", sources)+"\"");
      b.append("\r\n");
    }
    FileUtilities.stringToFile(b.toString(), Utilities.path(pf.tempDir, name+".csv"));
    pf.otherFilesRun.add(Utilities.path(pf.tempDir, name+".csv"));
    org.hl7.fhir.utilities.json.parser.JsonParser.compose(json, new File(Utilities.path(pf.tempDir, name+".json")), true);
    pf.otherFilesRun.add(Utilities.path(pf.tempDir, name+".json"));
  }


  private List<DependencyAnalyser.ArtifactDependency> makeDependencies() {
    DependencyAnalyser analyser = new DependencyAnalyser(pf.context);
    for (FetchedFile f : pf.fileList) {
      f.start("makeDependencies");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.getResource() != null && r.getResource() != null) {
            analyser.analyse(r.getResource());
          }
        }
      } finally {
        f.finish("makeDependencies");
      }
    }
    this.pf.dependencyList = analyser.getList();
    return analyser.getList();
  }


  private String relatedIgsList() throws IOException {
    Map<RelatedIG.RelatedIGRole, List<RelatedIG>> roles = new HashMap<>();
    for (RelatedIG ig : pf.relatedIGs) {
      if (!roles.containsKey(ig.getRole())) {
        roles.put(ig.getRole(), new ArrayList<>());
      }
      roles.get(ig.getRole()).add(ig);
    }
    XhtmlNode x = new XhtmlNode(NodeType.Element);
    XhtmlNode ul = x.ul();
    for (RelatedIG.RelatedIGRole r : roles.keySet()) {
      XhtmlNode li = ul.li();
      li.tx(r.toDisplay(roles.get(r).size() > 1));
      boolean first = true;
      for (RelatedIG ig : roles.get(r)) {
        if (first) {
          li.tx(": ");
          first = false;
        } else {
          li.tx(", ");
        }
        li.ahOrNot(ig.getWebLocation()).tx(ig.getId());
        if (ig.getTitle() != null) {
          li.tx(" (");
          li.tx(ig.getTitle());
          li.tx("}");
        }
      }
    }
    return new XhtmlComposer(false, true).compose(ul);
  }

  private String relatedIgsTable() throws IOException {
    if (pf.relatedIGs.isEmpty()) {
      return "";
    }
    XhtmlNode x = new XhtmlNode(NodeType.Element);
    XhtmlNode tbl = x.table("grid");
    XhtmlNode tr = tbl.tr();
    tr.th().b().tx("ID");
    tr.th().b().tx("Title");
    tr.th().b().tx("Role");
    tr.th().b().tx("Version");
    for (RelatedIG ig : pf.relatedIGs) {
      tr = tbl.tr();
      tr.td().ahOrNot(ig.getWebLocation()).tx(ig.getId());
      tr.td().tx(ig.getTitle());
      tr.td().tx(ig.getRoleCode());
      tr.td().tx(ig.getNpm() == null ? "??" : ig.getNpm().version());
    }
    return new XhtmlComposer(false, true).compose(tbl);
  }


  private void populateCustomResourceEntry(FetchedResource r, JsonObject item, Object object) throws Exception {
    Element e = r.getElement();
//      item.add("layout-type", "canonical");
    if (e.getChildren("url").size() == 1) {
      item.add("url", e.getNamedChildValueSingle("url"));
    }
    if (e.hasChildren("identifier")) {
      List<String> ids = new ArrayList<String>();
      for (Element id : e.getChildren("identifier")) {
        if (id.hasChild("value")) {
          ids.add(pf.dr.displayDataType(ResourceWrapper.forType(pf.cu, id)));
        }
      }
      if (!ids.isEmpty()) {
        item.add("identifiers", String.join(", ", ids));
      }
    }
    if (e.getChildren("version").size() == 1) {
      item.add("version", e.getNamedChildValueSingle("version"));
    }
    if (e.getChildren("name").size() == 1) {
      item.add("name", e.getNamedChildValueSingle("name"));
    }
    if (e.getChildren("title").size() == 1) {
      item.add("title", e.getNamedChildValueSingle("title"));
//        addTranslationsToJson(item, "title", e.getNamedChild("title"), false);
    }
    if (e.getChildren("experimental").size() == 1) {
      item.add("experimental", e.getNamedChildValueSingle("experimental"));
    }
    if (e.getChildren("date").size() == 1) {
      item.add("date", e.getNamedChildValueSingle("date"));
    }
    if (e.getChildren("description").size() == 1) {
      item.add("description", preProcessMarkdown(e.getNamedChildValueSingle("description")));
//        addTranslationsToJson(item, "description", e.getNamedChild("description"), false);
    }

//      if (cr.hasUseContext() && !containedCr) {
//        List<String> contexts = new ArrayList<String>();
//        for (UsageContext uc : cr.getUseContext()) {
//          String label = dr.displayDataType(uc.getCode());
//          if (uc.hasValueCodeableConcept()) {
//            String value = dr.displayDataType(uc.getValueCodeableConcept());
//            if (value!=null) {
//              contexts.add(label + ":\u00A0" + value);
//            }
//          } else if (uc.hasValueQuantity()) {
//            String value = dr.displayDataType(uc.getValueQuantity());
//            if (value!=null)
//              contexts.add(label + ":\u00A0" + value);
//          } else if (uc.hasValueRange()) {
//            String value = dr.displayDataType(uc.getValueRange());
//            if (!value.isEmpty())
//              contexts.add(label + ":\u00A0" + value);
//
//          } else if (uc.hasValueReference()) {
//            String value = null;
//            String reference = null;
//            if (uc.getValueReference().hasReference()) {
//              reference = uc.getValueReference().getReference().contains(":") ? "" : igpkp.getCanonical() + "/";
//              reference += uc.getValueReference().getReference();
//            }
//            if (uc.getValueReference().hasDisplay()) {
//              if (reference != null)
//                value = "[" + uc.getValueReference().getDisplay() + "](" + reference + ")";
//              else
//                value = uc.getValueReference().getDisplay();
//            } else if (reference!=null)
//              value = "[" + uc.getValueReference().getReference() + "](" + reference + ")";
//            else if (uc.getValueReference().hasIdentifier()) {
//              String idLabel = dr.displayDataType(uc.getValueReference().getIdentifier().getType());
//              value = idLabel!=null ? label + ":\u00A0" + uc.getValueReference().getIdentifier().getValue() : uc.getValueReference().getIdentifier().getValue();
//            }
//            if (value != null)
//              contexts.add(value);
//          } else if (uc.hasValue()) {
//            throw new FHIRException("Unsupported type for UsageContext.value - " + uc.getValue().fhirType());
//          }
//        }
//        if (!contexts.isEmpty())
//          item.add("contexts", String.join(", ", contexts));
//      }
//      if (cr.hasJurisdiction() && !containedCr) {
//        File flagDir = new File(tempDir + "/assets/images");
//        if (!flagDir.exists())
//          flagDir.mkdirs();
//        JsonArray jNodes = new JsonArray();
//        item.add("jurisdictions", jNodes);
//        ValueSet jvs = context.fetchResource(ValueSet.class, "http://hl7.org/fhir/ValueSet/jurisdiction");
//        for (CodeableConcept cc : cr.getJurisdiction()) {
//          JsonObject jNode = new JsonObject();
//          jNodes.add(jNode);
//          ValidationResult vr = jvs==null ? null : context.validateCode(new ValidationOptions(FhirPublication.R5, "en-US"),  cc, jvs);
//          if (vr != null && vr.asCoding()!=null) {
//            Coding cd = vr.asCoding();
//            jNode.add("code", cd.getCode());
//            if (cd.getSystem().equals("http://unstats.un.org/unsd/methods/m49/m49.htm") && cd.getCode().equals("001")) {
//              jNode.add("name", "International");
//              jNode.add("flag", "001");
//            } else if (cd.getSystem().equals("urn:iso:std:iso:3166")) {
//              String code = translateCountryCode(cd.getCode()).toLowerCase();
//              jNode.add("name", displayForCountryCode(cd.getCode()));
//              File flagFile = new File(vsCache + "/" + code + ".svg");
//              if (!flagFile.exists() && !ignoreFlags.contains(code)) {
//                URL url2 = new URL("https://flagcdn.com/" + shortCountryCode.get(code.toUpperCase()).toLowerCase() + ".svg");
//                try {
//                  InputStream in = url2.openStream();
//                  Files.copy(in, Paths.get(flagFile.getAbsolutePath()));
//                } catch (Exception e2) {
//                  ignoreFlags.add(code);
//                  System.out.println("Unable to access " + url2 + " or " + url2+" ("+e2.getMessage()+")");
//                }
//              }
//              if (flagFile.exists()) {
//                FileUtils.copyFileToDirectory(flagFile, flagDir);
//                jNode.add("flag", code);
//              }
//            } else if (cd.getSystem().equals("urn:iso:std:iso:3166:-2")) {
//              String code = cd.getCode();
//              String[] codeParts = cd.getCode().split("-");
//              jNode.add("name", displayForStateCode(cd.getCode()) + " (" + displayForCountryCode(codeParts[0]) + ")");
//              File flagFile = new File(vsCache + "/" + code + ".svg");
//              if (!flagFile.exists()) {
//                URL url = new URL("http://flags.ox3.in/svg/" + codeParts[0].toLowerCase() + "/" + codeParts[1].toLowerCase() + ".svg");
//                try (InputStream in = url.openStream()) {
//                  Files.copy(in, Paths.get(flagFile.getAbsolutePath()));
//                } catch (Exception e) {
//                  // If we can't find the file, that's ok.
//                }
//              }
//              if (flagFile.exists()) {
//                FileUtils.copyFileToDirectory(flagFile, flagDir);
//                jNode.add("flag", code);
//              }
//            }
//          } else {
//            jNode.add("name", dr.displayDataType(cc));
//          }
//        }
//      }

    if (e.getChildren("purpose").size() == 1) {
      item.add("purpose", ProfileUtilities.processRelativeUrls(e.getNamedChildValueSingle("purpose"), "", pf.igpkp.specPath(), pf.context.getResourceNames(), pf.specMaps.get(0).listTargets(), pageTargets(), false));
//        addTranslationsToJson(item, "purpose", e.getNamedChild("purpose"), false);
    }
    if (e.getChildren("status").size() == 1) {
      item.add("status", e.getNamedChildValueSingle("status"));
    }
    if (e.getChildren("copyright").size() == 1) {
      item.add("copyright", ProfileUtilities.processRelativeUrls(e.getNamedChildValueSingle("copyright"), "", pf.igpkp.specPath(), pf.context.getResourceNames(), pf.specMaps.get(0).listTargets(), pageTargets(), false));
//        addTranslationsToJson(item, "description", e.getNamedChild("description"), false);
    }

//      if (pcr!=null && pcr.hasExtension(ExtensionDefinitions.EXT_FMM_LEVEL)) {
//        IntegerType fmm = pcr.getExtensionByUrl(ExtensionDefinitions.EXT_FMM_LEVEL).getValueIntegerType();
//        item.add("fmm", fmm.asStringValue());
//        if (fmm.hasExtension(ExtensionDefinitions.EXT_FMM_DERIVED)) {
//          String derivedFrom = "FMM derived from: ";
//          for (Extension ext: fmm.getExtensionsByUrl(ExtensionDefinitions.EXT_FMM_DERIVED)) {
//            derivedFrom += "\r\n" + ext.getValueCanonicalType().asStringValue();
//          }
//          item.add("fmmSource", derivedFrom);
//        }
//      }
//      List<String> keywords = new ArrayList<String>();
//      if (r.getResource() instanceof StructureDefinition) {
//        StructureDefinition sd = (StructureDefinition)r.getResource();
//        if (sd.hasKeyword()) {
//          for (Coding coding : sd.getKeyword()) {
//            String value = dr.displayDataType(coding);
//            if (value != null)
//              keywords.add(value);
//          }
//        }
//      } else if (r.getResource() instanceof CodeSystem) {
//        CodeSystem cs = (CodeSystem)r.getResource();
//        for (Extension e : cs.getExtensionsByUrl(ExtensionDefinitions.EXT_CS_KEYWORD)) {
//          keywords.add(e.getValueStringType().asStringValue());
//        }
//      } else if (r.getResource() instanceof ValueSet) {
//        ValueSet vs = (ValueSet)r.getResource();
//        for (Extension e : vs.getExtensionsByUrl(ExtensionDefinitions.EXT_VS_KEYWORD)) {
//          keywords.add(e.getValueStringType().asStringValue());
//        }
//      }
//      if (!keywords.isEmpty())
//        item.add("keywords", String.join(", ", keywords));
//
//
    org.hl7.fhir.igtools.renderers.StatusRenderer.ResourceStatusInformation info = StatusRenderer.analyse(e);
    JsonObject jo = new JsonObject();
    if (info.getColorClass() != null) {
      jo.add("class", info.getColorClass());
    }
    if (info.getOwner() != null) {
      jo.add("owner", info.getOwner());
    }
    if (info.getOwnerLink() != null) {
      jo.add("link", info.getOwnerLink());
    }
    if (info.getSstatus() != null) {
      jo.add("standards-status", info.getSstatus());
    } else if (pf.sourceIg.hasExtension(ExtensionDefinitions.EXT_STANDARDS_STATUS)) {
      jo.add("standards-status","informative");
    }
    if (info.getSstatusSupport() != null) {
      jo.add("standards-status-support", info.getSstatusSupport());
    }
    if (info.getNormVersion() != null) {
      item.add("normativeVersion", info.getNormVersion());
    }
    if (info.getFmm() != null) {
      jo.add("fmm", info.getFmm());
    }
    if (info.getSstatusSupport() != null) {
      jo.add("fmm-support", info.getFmmSupport());
    }
    if (info.getStatus() != null && !jo.has("status")) {
      jo.add("status", info.getStatus());
    }
    if (!jo.getProperties().isEmpty()) {
      item.set("status", jo);
    }

  }

  private String getLangDesc(String s) throws IOException {
    if (pf.registry == null) {
      pf.registry = new LanguageSubtagRegistry();
      LanguageSubtagRegistryLoader loader = new LanguageSubtagRegistryLoader(pf.registry);
      loader.loadFromDefaultResource();
    }
    LanguageTag tag = new LanguageTag(pf.registry, s);
    return tag.present();
  }

  private String getLangDesc(String s, String l) throws IOException {
    if (pf.registry == null) {
      pf.registry = new LanguageSubtagRegistry();
      LanguageSubtagRegistryLoader loader = new LanguageSubtagRegistryLoader(pf.registry);
      loader.loadFromDefaultResource();
    }
    LanguageTag tag = new LanguageTag(pf.registry, s);
    return tag.present();
  }



  // Turn a country code into a 3-character country code;
  private String translateCountryCode(String code) throws Exception {
    setupCountries();
    String newCode = code;
    if (StringUtils.isNumeric(code)) {
      newCode = pf.countryCodeForNumeric.get(code);
      if (newCode == null)
        throw new Exception("Unable to find numeric ISO country code: " + code);
    } else if (code.length()==2) {
      newCode = pf.countryCodeFor2Letter.get(code);
      if (newCode == null)
        throw new Exception("Unable to find 2-char ISO country code: " + code);
    }
    return newCode.toUpperCase();
  }

  private String displayForCountryCode(String code) throws Exception {
    String newCode = translateCountryCode(code);
    return pf.countryNameForCode.get(newCode);
  }

  private String displayForStateCode(String code) throws Exception {
    return pf.stateNameForCode.get(code);
  }

  private void setupCountries() throws Exception {
    if (pf.countryCodeForName !=null)
      return;
    pf.countryCodeForName = new HashMap<String, String>();
    pf.countryNameForCode = new HashMap<String, String>();
    pf.countryCodeFor2Letter = new HashMap<String, String>();
    pf.countryCodeForNumeric = new HashMap<String, String>();
    pf.shortCountryCode = new HashMap<String, String>();
    pf.stateNameForCode = new HashMap<String, String>();
    pf.ignoreFlags = new ArrayList<String>();
    JsonParser p = new org.hl7.fhir.r5.formats.JsonParser(false);
    ValueSet char3 = (ValueSet)p.parse("{\"resourceType\":\"ValueSet\",\"url\":\"http://hl7.org/fhir/ValueSet/iso3166-1-3\",\"version\":\"4.0.1\",\"name\":\"Iso3166-1-3\",\"status\":\"active\",\"compose\":{\"include\":[{\"system\":\"urn:iso:std:iso:3166\",\"filter\":[{\"property\":\"code\",\"op\":\"regex\",\"value\":\"^[A-Z]{3}$\"}]}]}}");
    ValueSet char2 = (ValueSet)p.parse("{\"resourceType\":\"ValueSet\",\"url\":\"http://hl7.org/fhir/ValueSet/iso3166-1-2\",\"version\":\"4.0.1\",\"name\":\"Iso3166-1-2\",\"status\":\"active\",\"compose\":{\"include\":[{\"system\":\"urn:iso:std:iso:3166\",\"filter\":[{\"property\":\"code\",\"op\":\"regex\",\"value\":\"^[A-Z]{2}$\"}]}]}}");
    ValueSet num = (ValueSet)p.parse("{\"resourceType\":\"ValueSet\",\"url\":\"http://hl7.org/fhir/ValueSet/iso3166-1-N\",\"version\":\"4.0.1\",\"name\":\"Iso3166-1-N\",\"status\":\"active\",\"compose\":{\"include\":[{\"system\":\"urn:iso:std:iso:3166\",\"filter\":[{\"property\":\"code\",\"op\":\"regex\",\"value\":\"^[0-9]{3}$\"}]}]}}");
    ValueSet state = (ValueSet)p.parse("{\"resourceType\":\"ValueSet\",\"url\":\"http://hl7.org/fhir/ValueSet/jurisdiction\",\"version\":\"4.0.1\",\"name\":\"JurisdictionValueSet\",\"status\":\"active\",\"compose\":{\"include\":[{\"system\":\"urn:iso:std:iso:3166:-2\"}]}}");
    ValueSetExpansionOutcome char3Expand = pf.context.expandVS(char3,true,false);
    ValueSetExpansionOutcome char2Expand = pf.context.expandVS(char2,true,false);
    ValueSetExpansionOutcome numExpand = pf.context.expandVS(num,true,false);
    ValueSetExpansionOutcome stateExpand = pf.context.expandVS(state,true,false);
    if (!char3Expand.isOk() || !char2Expand.isOk() || !numExpand.isOk() || !stateExpand.isOk()) {
      if (!char3Expand.isOk())
        System.out.println("Error expanding 3-character country codes: " + char3Expand.getError());
      if (!char2Expand.isOk())
        System.out.println("Error expanding 2-character country codes: " + char2Expand.getError());
      if (!numExpand.isOk())
        System.out.println("Error expanding numeric country codes: " + numExpand.getError());
      if (!stateExpand.isOk())
        System.out.println("Error expanding state & province codes: " + stateExpand.getError());
      throw new Exception("Error expanding ISO country-code & state value sets");
    }
    for (ValueSet.ValueSetExpansionContainsComponent c: char3Expand.getValueset().getExpansion().getContains()) {
      if (!c.hasDisplay())
        System.out.println("No display value for 3-character country code " + c.getCode());
      else {
        pf.countryCodeForName.put(c.getDisplay(), c.getCode());
        pf.countryNameForCode.put(c.getCode(), c.getDisplay());
      }
    }
    for (ValueSet.ValueSetExpansionContainsComponent c: char2Expand.getValueset().getExpansion().getContains()) {
      if (!c.hasDisplay())
        System.out.println("No display value for 2-character country code " + c.getCode());
      else {
        String code = pf.countryCodeForName.get(c.getDisplay());
        if (code==null) {
          switch (c.getDisplay()) {
            case "Åland Islands":
              code = pf.countryCodeForName.get("Eland Islands");
              break;
            case "Côte d''Ivoire":
              code = pf.countryCodeForName.get("Ctte d'Ivoire");
              break;
            case "Curaçao":
              code = "Curagao";
              break;
            case "Korea, Democratic People''s Republic of":
              code = "Korea, Democratic People's Republic of";
              break;
            case "Lao People''s Democratic Republic":
              code = "Lao People's Democratic Republic";
              break;
            case "Réunion":
              code = "Riunion";
              break;
            case "Saint Barthélemy":
              code = pf.countryCodeForName.get("Saint Barthilemy");
              break;
            case "United Kingdom of Great Britain and Northern Ireland":
              code = pf.countryCodeForName.get("United Kingdom");
              break;
            case "Virgin Islands,":
              code = pf.countryCodeForName.get("Virgin Islands, U.S.");
              break;
            default:
              log("Unable to find 3-character code having same country code as ISO 2-char code " + c.getCode() + " - " + c.getDisplay());
          }
        }
        pf.countryCodeFor2Letter.put(c.getCode(), code);
        pf.shortCountryCode.put(code, c.getCode());
      }
    }
    for (ValueSet.ValueSetExpansionContainsComponent c: numExpand.getValueset().getExpansion().getContains()) {
      String code = pf.countryCodeForName.get(c.getDisplay());
//      if (code==null)
//        throw new Exception("Unable to find 3-character code having same country code as ISO numeric code " + c.getCode() + " - " + c.getDisplay());
      pf.countryCodeForNumeric.put(c.getCode(), code);
    }
    for (ValueSet.ValueSetExpansionContainsComponent c: stateExpand.getValueset().getExpansion().getContains()) {
      if (c.getSystem().equals("urn:iso:std:iso:3166:-2"))
        pf.stateNameForCode.put(c.getCode(), c.getDisplay());
    }
  }


  private Map<String, Map<String, String>> loadLanguagesCsv() throws FHIRException, IOException {
    Map<String, Map<String, String>> res = new HashMap<>();
    CSVReader csv = MagicResources.loadLanguagesCSV();
    boolean first = true;
    String[] headers = csv.readHeaders();
    for (String s : headers) {
      if (first) {
        first = false;
      } else {
        res.put(s, new HashMap<>());
      }
    }
    while (csv.line()) {
      String[] cells = csv.getCells();
      for (int i = 1; i < cells.length; i++) {
        res.get(headers[i]).put(cells[0], Utilities.noString(cells[i]) ? null : cells[i]);
      }
    }
    return res;
  }


  private void cleanUpExtensions(ImplementationGuide ig) {
    ExtensionUtilities.removeExtension(ig.getDefinition(), ExtensionDefinitions.EXT_IGP_SPREADSHEET);
    ExtensionUtilities.removeExtension(ig.getDefinition(), ExtensionDefinitions.EXT_IGP_BUNDLE);
    ExtensionUtilities.removeExtension(ig, ExtensionDefinitions.EXT_IGP_CONTAINED_RESOURCE_INFO); // - this is in contained resources somewhere, not the root of IG?
    for (ImplementationGuide.ImplementationGuideDefinitionResourceComponent r : ig.getDefinition().getResource())
      ExtensionUtilities.removeExtension(r, ExtensionDefinitions.EXT_IGP_RESOURCE_INFO);
  }

}
