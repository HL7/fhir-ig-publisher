package org.hl7.fhir.igtools.publisher;

/*-
 * #%L
 * org.hl7.fhir.publisher.core
 * %%
 * Copyright (C) 2014 - 2019 Health Level 7
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.logging.Level;
import org.hl7.fhir.igtools.logging.LogbackUtilities;
import org.hl7.fhir.igtools.publisher.loaders.PublisherLoader;
import org.hl7.fhir.igtools.publisher.xig.XIGGenerator;
import org.hl7.fhir.igtools.renderers.*;
import org.hl7.fhir.igtools.renderers.ValidationPresenter.IGLanguageInformation;
import org.hl7.fhir.igtools.ui.IGPublisherUI;
import org.hl7.fhir.igtools.web.*;
import org.hl7.fhir.r5.context.*;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.ElementVisitor;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.extensions.*;
import org.hl7.fhir.r5.model.ActorDefinition;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.Constants;
import org.hl7.fhir.r5.model.DateTimeType;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.Enumerations.CodeSystemContentMode;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionResourceComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideGlobalComponent;
import org.hl7.fhir.r5.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.UriType;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.IResourceLinkResolver;
import org.hl7.fhir.r5.renderers.utils.Resolver.IReferenceResolver;
import org.hl7.fhir.r5.renderers.utils.Resolver.ResourceReferenceKind;
import org.hl7.fhir.r5.renderers.utils.Resolver.ResourceWithReference;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.terminologies.client.TerminologyClientContext;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.r5.utils.NPMPackageGenerator;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.r5.utils.xver.XVerExtensionManager;
import org.hl7.fhir.r5.utils.validation.IValidationProfileUsageTracker;
import org.hl7.fhir.r5.utils.xver.XVerExtensionManagerFactory;
import org.hl7.fhir.utilities.*;
import org.hl7.fhir.utilities.filesystem.ManagedFileAccess;
import org.hl7.fhir.utilities.http.HTTPResult;
import org.hl7.fhir.utilities.http.ManagedWebAccess;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonBoolean;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonPrimitive;
import org.hl7.fhir.utilities.json.model.JsonString;
import org.hl7.fhir.utilities.npm.*;
import org.hl7.fhir.utilities.npm.NpmPackage.PackageResourceInformation;
import org.hl7.fhir.utilities.settings.FhirSettings;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.validation.SQLiteINpmPackageIndexBuilderDBImpl;
import org.hl7.fhir.validation.instance.utils.ValidationContext;

/**
 * Implementation Guide Publisher
 *
 * If you want to use this inside a FHIR server, and not to access content
 * on a local folder, provide your own implementation of the file fetcher
 *
 * rough sequence of activities:
 *
 *   load the context using the internal validation pack
 *   connect to the terminology service
 *
 *   parse the implementation guide
 *   find all the source files and determine the resource type
 *   load resources in this order:
 *     naming system
 *     code system
 *     value setx
 *     data element?
 *     structure definition
 *     concept map
 *     structure map
 *
 *   validate all source files (including the IG itself)
 *
 *   for each source file:
 *     generate all outputs
 *
 *   generate summary file
 *
 * Documentation: see http://wiki.hl7.org/index.php?title=IG_Publisher_Documentation
 *
 * The Publisher has 4 main related classes (and was broken up into these classes in August 2025)
 *  - @PublisherFields: a class that solely exists to carry all the data shared between the next 4 classes (when split, none of them had any other fields of their own, but they are allowed to)
 *  - @PublisherBase : shared routines (and maybe fields) common the next 3 classes
 *  - @PublisherIGLoader: routines concerned with loading all the content that's related to the IG (technically: initialise() and load())
 *  - @PublisherProcessor: routines related to checking and changing content of the loaded resources
 *  - @PublisherGenerator: routines concerned with generating all the content that's produced as part of the IG (technically:generate())
 *
 *  The division of labor between the resources is not proscriptive; it ijust a device to split up what had become a very large
 *  class that was otherwise hard to split because the content really is very internally related. Further refactoring is anticipated
 *
 * @author Grahame Grieve
 */

public class Publisher extends PublisherBase implements IReferenceResolver, IValidationProfileUsageTracker, IResourceLinkResolver {
  public static final String FHIR_SETTINGS_PARAM = "-fhir-settings";
  public static final int FMM_DERIVATION_MAX = 5;
  public static final String IG_NAME = "!ig!";
  public static final String REDIRECT_SOURCE = "<html>\r\n<head>\r\n<meta http-equiv=\"Refresh\" content=\"0; url=site/index.html\"/>\r\n</head>\r\n"+
          "<body>\r\n<p>See here: <a href=\"site/index.html\">this link</a>.</p>\r\n</body>\r\n</html>\r\n";
  public static final long JEKYLL_TIMEOUT = 60000 * 5; // 5 minutes....
  public static final long FSH_TIMEOUT = 60000 * 5; // 5 minutes....
  public static final int PRISM_SIZE_LIMIT = 16384;
  public static final String TOOLING_IG_CURRENT_RELEASE = "0.9.0";
  public static final String PACKAGE_CACHE_FOLDER_PARAM = "-package-cache-folder";

  private PublisherIGLoader loader;
  private PublisherProcessor processor;
  private PublisherGenerator generator;

  public Publisher() {
    super();
    loader = new PublisherIGLoader(settings);
    processor = new PublisherProcessor(settings);
    generator = new PublisherGenerator(settings);

    NpmPackageIndexBuilder.setExtensionFactory(new SQLiteINpmPackageIndexBuilderDBImpl.SQLiteINpmPackageIndexBuilderDBImplFactory());
  }


  private void setRepoSource(final String repoSource) {
    if (repoSource == null) {
      return;
    }
    this.settings.setRepoSource(GitUtilities.getURLWithNoUserInfo(repoSource, "-repo CLI parameter"));
  }

  public void execute() throws Exception {
    XhtmlNode.setCheckParaGeneral(true);

    String rootDir = settings.getConfigFile();
    FileChangeMonitor monitor = null;
    if (settings.isWatchMode()) {
      monitor = new FileChangeMonitor(Arrays.asList(
              FileChangeMonitor.MonitoredPath.file(Paths.get(Utilities.path(rootDir, "ig.ini"))),
              FileChangeMonitor.MonitoredPath.file(Paths.get(Utilities.path(rootDir, "sushi-config.yaml"))),
              FileChangeMonitor.MonitoredPath.file(Paths.get(Utilities.path(rootDir, "publication-request.json"))),
              FileChangeMonitor.MonitoredPath.file(Paths.get(Utilities.path(rootDir, "sushi-ignoreWarnings.txt"))),
              FileChangeMonitor.MonitoredPath.recursive(Paths.get(Utilities.path(rootDir, "input"))),
              FileChangeMonitor.MonitoredPath.recursive(Paths.get(Utilities.path(rootDir, "fsh-generated")))
      ));
    }

    do {
      pf = new PublisherFields();
      loader.setPf(pf);
      processor.setPf(pf);
      generator.setPf(pf);
      pf.resolver = this;
      setLogger(this);

      pf.tt = new TimeTracker();
      loader.initialize();
      if (pf.validator != null) {
        pf.validator.setTracker(this);
      }

      if (pf.isBuildingTemplate) {
        packageTemplate();
      } else {
        log("Load IG");
        try {
          createIg();
        } catch (Exception e) {
          recordOutcome(e, null);
          throw e;
        }
      }
      if (pf.templateLoaded && new File(rootDir).exists()) {
        FileUtilities.clearDirectory(Utilities.path(rootDir, "template"));
      }
      if (pf.folderToDelete != null) {
        try {
          FileUtilities.clearDirectory(pf.folderToDelete);
          new File(pf.folderToDelete).delete();
        } catch (Throwable e) {
          // nothing
        }
      }
    } while (monitor != null && thereIsAFileChange(monitor));
  }

  private boolean thereIsAFileChange(FileChangeMonitor monitor) throws InterruptedException {
    monitor.startMonitoring();
    System.out.println("Watching for changes (Cascais:Rapido mode)");
    while (true) {
      if (monitor.hasChanges()) {
        monitor.printChanges();
        monitor.clearChanges();
        monitor.stopMonitoring();
        return true;
      } else {
        Thread.sleep(100);
      }
    }
  }

  private String renderGlobals() {
    if (pf.sourceIg.hasGlobal()) {
      StringBuilder b = new StringBuilder();
      boolean list = pf.sourceIg.getGlobal().size() > 1;
      if (list) {
        b.append("<ul>\r\n");
      }
      for (ImplementationGuideGlobalComponent g : pf.sourceIg.getGlobal()) {
        b.append(list ? "<li>" : "");
        b.append(""+g.getType()+": "+g.getProfile());
        b.append(list ? "</li>" : "");
      }
      if (list) {
        b.append("</ul>\r\n");
      }
      return b.toString();
    } else {
      return "(none declared)";
    }
  }

  private void packageTemplate() throws IOException {
    FileUtilities.createDirectory(pf.outputDir);
    long startTime = System.nanoTime();
    JsonObject qaJson = new JsonObject();
    StringBuilder txt = new StringBuilder();
    StringBuilder txtGen = new StringBuilder();
    qaJson.add("url", pf.templateInfo.asString("canonical"));
    txt.append("url = "+ pf.templateInfo.asString("canonical")+"\r\n");
    txtGen.append("url = "+ pf.templateInfo.asString("canonical")+"\r\n");
    qaJson.add("package-id", pf.templateInfo.asString("name"));
    txt.append("package-id = "+ pf.templateInfo.asString("name")+"\r\n");
    txtGen.append("package-id = "+ pf.templateInfo.asString("name")+"\r\n");
    qaJson.add("ig-ver", pf.templateInfo.asString("version"));
    txt.append("ig-ver = "+ pf.templateInfo.asString("version")+"\r\n");
    txtGen.append("ig-ver = "+ pf.templateInfo.asString("version")+"\r\n");
    qaJson.add("date", new SimpleDateFormat("EEE, dd MMM, yyyy HH:mm:ss Z", new Locale("en", "US")).format(pf.getExecTime().getTime()));
    qaJson.add("dateISO8601", new DateTimeType(pf.getExecTime()).asStringValue());
    qaJson.add("version", Constants.VERSION);
    qaJson.add("tool", Constants.VERSION+" ("+ToolsVersion.TOOLS_VERSION+")");
    try {
      File od = new File(pf.outputDir);
      FileUtils.cleanDirectory(od);
      pf.npm = new NPMPackageGenerator(Utilities.path(pf.outputDir, "package.tgz"), pf.templateInfo, pf.getExecTime().getTime(), !settings.isPublishing());
      pf.npm.loadFiles(pf.rootDir, null, new File(pf.rootDir), ".git", "output", "package", "temp");
      pf.npm.finish();

      FileUtilities.stringToFile(makeTemplateIndexPage(), Utilities.path(pf.outputDir, "index.html"));
      FileUtilities.stringToFile(makeTemplateJekyllIndexPage(), Utilities.path(pf.outputDir, "jekyll.html"));
      FileUtilities.stringToFile(makeTemplateQAPage(), Utilities.path(pf.outputDir, "qa.html"));

      if (settings.getMode() != PublisherUtils.IGBuildMode.AUTOBUILD) {
        pf.pcm.addPackageToCache(pf.templateInfo.asString("name"), pf.templateInfo.asString("version"), new FileInputStream(pf.npm.filename()), Utilities.path(pf.outputDir, "package.tgz"));
        pf.pcm.addPackageToCache(pf.templateInfo.asString("name"), "dev", new FileInputStream(pf.npm.filename()), Utilities.path(pf.outputDir, "package.tgz"));
      }
    } catch (Exception e) {
      e.printStackTrace();
      qaJson.add("exception", e.getMessage());
      txt.append("exception = "+e.getMessage()+"\r\n");
      txtGen.append("exception = "+e.getMessage()+"\r\n");
    }
    long endTime = System.nanoTime();
    String json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(qaJson, true);
    FileUtilities.stringToFile(json, Utilities.path(pf.outputDir, "qa.json"));
    FileUtilities.stringToFile(txt.toString(), Utilities.path(pf.outputDir, "qa.txt"));
    FileUtilities.stringToFile(txtGen.toString(), Utilities.path(pf.outputDir, "qa.compare.txt"));

    FileUtilities.createDirectory(pf.tempDir);
    ZipGenerator zip = new ZipGenerator(Utilities.path(pf.tempDir, "full-ig.zip"));
    zip.addFolder(pf.outputDir, "site/", false);
    zip.addFileSource("index.html", REDIRECT_SOURCE, false);
    zip.close();
    FileUtilities.copyFile(Utilities.path(pf.tempDir, "full-ig.zip"), Utilities.path(pf.outputDir, "full-ig.zip"));
    new File(Utilities.path(pf.tempDir, "full-ig.zip")).delete();

    // registering the package locally
    log("Finished @ "+nowString()+". "+DurationUtil.presentDuration(endTime - startTime)+". Output in "+ pf.outputDir);
  }


  private String nowString() {
    LocalDateTime dateTime = LocalDateTime.now();
    DateTimeFormatter shortFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(pf.rc == null || pf.rc.getLocale() == null ?  Locale.getDefault() : pf.rc.getLocale());
    return dateTime.format(shortFormat);
  }

  private String makeTemplateIndexPage() {
    String page = "---\n"
        + "layout: page\n"
        + "title: {pid}\n"
        + "---\n"
        + "  <p><b>Template {pid}{vid}</b></p>\n"
        + "  <p>You can <a href=\"package.tgz\">download the template</a>, though you should not need to; just refer to the template as {pid} in your IG configuration.</p>\n"
        + "  <p>Dependencies: {dep}</p>\n"
        + "  <p>A <a href=\"{path}history.html\">full version history is published</a></p>\n"
        + "";
    return page.replace("{{npm}}", pf.templateInfo.asString("name")).replace("{{canonical}}", pf.templateInfo.asString("canonical"));
  }

  private String makeTemplateJekyllIndexPage() {
    String page = "---\r\n"+
        "layout: page\r\n"+
        "title: {{npm}}\r\n"+
        "---\r\n"+
        "  <p><b>Template {{npm}}</b></p>\r\n"+
        "  <p>You can <a href=\"package.tgz\">download the template</a>, though you should not need to; just refer to the template as {{npm}} in your IG configuration.</p>\r\n"+
        "  <p>A <a href=\"{{canonical}}/history.html\">full version history is published</a></p>\r\n";
    return page.replace("{{npm}}", pf.templateInfo.asString("name")).replace("{{canonical}}", pf.templateInfo.asString("canonical"));
  }

  private String makeTemplateQAPage() {
    String page = "<!DOCTYPE HTML><html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\"><head><title>Template QA Page</title></head><body><p><b>Template {{npm}} QA</b></p><p>No useful QA on templates - if you see this page, the template built ok.</p></body></html>";
    return page.replace("{{npm}}", pf.templateInfo.asString("name"));
  }

  public void createIg() throws Exception, IOException, EOperationOutcome, FHIRException {
    try {
      IniFile buildTracker = new IniFile(Utilities.path(pf.vsCache, ".build-tracker.ini"));
      TimeTracker.Session tts = pf.tt.start("loading");
      FetchedFile igFile = loader.load();

      pf.rc.setResolver(this);
      pf.rc.setResolveLinkResolver(this);
      for (RenderingContext rc : pf.rcLangs.langValues()) {
        rc.setResolver(this);
        rc.setResolveLinkResolver(this);

      }

      checkDependencies(igFile, buildTracker);
      tts.end();

      tts = pf.tt.start("generate");

      log("Checking Language");
      processor.checkLanguage();
      processor.loadConformance2();
      processor.checkSignBundles();

      if (!settings.isValidationOff()) {
        log("Validating Resources");
        try {
          processor.validate();
        } catch (Exception ex){
          log("Unhandled Exception: " +ex.toString());
          throw(ex);
        }
        pf.validatorSession.close();
      }
      if (pf.needsRegen) {
        log("Regenerating Narratives");
        processor.generateNarratives(true);
      }
      log("Processing Provenance Records");
      processor.processProvenanceDetails();
      if (pf.hasTranslations) {
        log("Generating Translation artifacts");
        processor.processTranslationOutputs();
      }
      log("Generating Outputs in "+ pf.outputDir);
      Map<String, String> uncsList = scanForUnattributedCodeSystems();
      generator.generate();
      clean();
      pf.dependentIgFinder.finish(pf.outputDir, pf.sourceIg.present());
      List<FetchedResource> fragments = new ArrayList<>();
      listFragments(fragments);
      checkForSnomedVersion();
      if (pf.txLog != null) {
        if (ManagedFileAccess.file(pf.txLog).exists()) {
           FileUtilities.copyFile(pf.txLog, Utilities.path(pf.rootDir, "output", "qa-tx.html"));
        }
      }
      deleteDuplicateMessages();
      ValidationPresenter val = new ValidationPresenter(pf.version, workingVersion(), pf.igpkp, pf.childPublisher == null? null : pf.childPublisher.getIgpkp(), pf.rootDir, pf.npmName, pf.childPublisher == null? null : pf.childPublisher.pf.npmName,
          IGVersionUtil.getVersion(), fetchCurrentIGPubVersion(), pf.realmRules, pf.previousVersionComparator, pf.ipaComparator, pf.ipsComparator,
          new DependencyRenderer(pf.pcm, pf.outputDir, pf.npmName, pf.templateManager, pf.dependencyList, pf.context, pf.markdownEngine, pf.rc, pf.specMaps).render(pf.publishedIg, true, false, false), new HTAAnalysisRenderer(pf.context, pf.outputDir, pf.markdownEngine).render(pf.publishedIg.getPackageId(), pf.fileList, pf.publishedIg.present()),
          new PublicationChecker(pf.repoRoot, pf.historyPage, pf.markdownEngine, findReleaseLabelString(), pf.publishedIg, pf.relatedIGs).check(), renderGlobals(), pf.copyrightYear, pf.context, scanForR5Extensions(), pf.modifierExtensions,
          generateDraftDependencies(), pf.noNarrativeResources, pf.noValidateResources, settings.isValidationOff(), settings.isGenerationOff(), pf.dependentIgFinder, pf.context.getTxClientManager(),
          fragments, makeLangInfo(), pf.relatedIGs);
      val.setValidationFlags(pf.hintAboutNonMustSupport, pf.anyExtensionsAllowed, pf.checkAggregation, pf.autoLoad, pf.showReferenceMessages, pf.noExperimentalContent, pf.displayWarnings);
      FileUtilities.stringToFile(new IPViewRenderer(uncsList, pf.inspector.getExternalReferences(), pf.inspector.getImageRefs(), pf.inspector.getCopyrights(),
              pf.ipStmt, pf.inspector.getVisibleFragments().get("1"), pf.context).execute(), Utilities.path(pf.outputDir, "qa-ipreview.html"));
      tts.end();
      if (isChild()) {
        log("Built. "+ pf.tt.report());
      } else {
        log("Built. "+ pf.tt.report());
        log("Generating QA");
        log("Validation output in "+val.generate(pf.sourceIg.getName(), pf.errors, pf.fileList, Utilities.path(settings.getDestDir() != null ? settings.getDestDir() : pf.outputDir, "qa.html"), pf.suppressedMessages, pinSummary()));
      }
      recordOutcome(null, val);
      buildTracker.setBooleanProperty("status", "complete", true, null);
      buildTracker.save();
      log("Finished @ "+nowString()+". Max Memory Used = "+Utilities.describeSize(pf.maxMemory)+logSummary());
    } catch (Exception e) {
      try {
        recordOutcome(e, null);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      throw e;
    }
  }

  private void listFragments(List<FetchedResource> fragments) {
    for (var f : pf.fileList) {
      for (var r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof CodeSystem && ((CodeSystem) r.getResource()).getContent() == CodeSystemContentMode.FRAGMENT) {
          fragments.add(r);
        }
      }
    }
  }

  private void deleteDuplicateMessages() {
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        for (ValidationMessage vm : r.getErrors()) {
          boolean inBase = false;
          for (ValidationMessage t :f.getErrors()) {
            if (vm.equals(t)) {
              inBase = true;
            }
          }
          if (!inBase) {
            f.getErrors().add(vm);
          }
        }
      }
    }
  }

  private String pinSummary() {
    String sfx = "";
    if (pf.pinDest != null) {
      sfx = " (in manifest Parameters/"+ pf.pinDest+")";
    }
    switch (pf.pinningPolicy) {
    case FIX: return ""+ pf.pinCount+" (all)"+sfx;
    case NO_ACTION: return "n/a";
    case WHEN_MULTIPLE_CHOICES: return ""+ pf.pinCount+" (when multiples)"+sfx;
    default: return "??";    
    }
  }

  private Map<String, String> scanForUnattributedCodeSystems() {
    Map<String, String> list = new HashMap<>();
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        String link = this.pf.igpkp.getLinkFor(r, true);
        r.setPath(link);
        scanForUnattributedCodeSystems(list, link, r.getElement());
      }
    }
    return list;
  }

  private void scanForUnattributedCodeSystems(Map<String, String> list, String link, Element e) {
    if ("Coding.system".equals(e.getProperty().getDefinition().getBase().getPath())) {
      String url = e.primitiveValue();
      if (url != null && !url.contains("example.org/") && !url.contains("acme.") && !url.contains("hl7.org")) {
        CodeSystem cs = pf.context.fetchCodeSystem(url);
        if (cs == null || !cs.hasCopyright()) {
          list.put(url, link);
        }
      }
    }
    if (e.hasChildren()) {
      for (Element c : e.getChildren()) {
        scanForUnattributedCodeSystems(list, link, c);
      }
    }    
  }
  
  private void checkForSnomedVersion() {
    if (!(pf.igrealm == null || "uv".equals(pf.igrealm)) && pf.context.getCodeSystemsUsed().contains("http://snomed.info/sct")) {
      boolean ok = false;
      for (ParametersParameterComponent p : pf.context.getExpansionParameters().getParameter()) {
        if ("system-version".equals(p.getName()) && p.hasValuePrimitive() && p.getValue().primitiveValue().startsWith("http://snomed.info/sct")) {
          ok = true;
        }
      }
      if (!ok) {
        pf.errors.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "IG", "The IG is not for the international realm, and it uses SNOMED CT, so it should fix the SCT edition in the expansion parameters", IssueSeverity.WARNING));
      }
    }

  }
  
  
  private IGLanguageInformation makeLangInfo() {
    IGLanguageInformation info = new IGLanguageInformation();
    info.setIgResourceLanguage(pf.publishedIg.getLanguage());
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        info.seeResource(r.getElement().hasChild("language")) ;
      }
    }
    info.setPolicy(pf.langPolicy );
    info.setIgLangs(new ArrayList<String>());
    return info;
  }

  private String logSummary() {
    if (PublisherFields.consoleLogger != null && PublisherFields.consoleLogger.started()) {
      return ". Log file saved in "+ PublisherFields.consoleLogger.getFilename();
    } else {
      return "";
    }
  }

  private String generateDraftDependencies() throws IOException {
    DraftDependenciesRenderer dr = new DraftDependenciesRenderer(pf.context, pf.packageInfo.getVID());
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        dr.checkResource(r);
      }
    }
    return dr.render();
  }

  private Set<String> scanForR5Extensions() {
    XVerExtensionManager xver = XVerExtensionManagerFactory.createExtensionManager(pf.context);
    Set<String> set = new HashSet<>();
    scanProfilesForR5(xver, set);
    scanExamplesForR5(xver, set);
    return set;
  }

  private void scanExamplesForR5(XVerExtensionManager xver, Set<String> set) {
    for (FetchedFile f : pf.fileList) {
      f.start("scanExamplesForR5");
      try {
        for (FetchedResource r : f.getResources()) {
          scanElementForR5(xver, set, r.getElement());
        }
      } finally {
        f.finish("scanExamplesForR5");
      }
    }
  }

  private void scanElementForR5(XVerExtensionManager xver, Set<String> set, Element element) {
    if (element.fhirType().equals("Extension")) {
      String url = element.getChildValue("url");
      scanRefForR5(xver, set, url);
    }
    for (Element c : element.getChildren()) {
      scanElementForR5(xver, set, c);
    }
  }

  private void scanProfilesForR5(XVerExtensionManager xver, Set<String> set) {
    for (FetchedFile f : pf.fileList) {
      f.start("scanProfilesForR5");
      try {

        for (FetchedResource r : f.getResources()) {
          if (r.fhirType().equals("StructureDefinition")) {
            StructureDefinition sd = (StructureDefinition) r.getResource();
            scanProfileForR5(xver, set, sd);
          }
        }
      } finally {
        f.finish("scanProfilesForR5");
      }
    }
  }



  private void scanProfileForR5(XVerExtensionManager xver, Set<String> set, StructureDefinition sd) {
    scanRefForR5(xver, set, sd.getBaseDefinition());
    StructureDefinition base = pf.context.fetchResource(StructureDefinition.class, sd.getBaseDefinition());
    if (base != null) {
      scanProfileForR5(xver, set, base);
    }
    for (ElementDefinition ed : sd.getDifferential().getElement()) {
      for (TypeRefComponent t : ed.getType()) {
        for (CanonicalType u : t.getProfile()) {
          scanRefForR5(xver, set, u.getValue());
        }
      }
    }
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      for (TypeRefComponent t : ed.getType()) {
        for (CanonicalType u : t.getProfile()) {
          scanRefForR5(xver, set, u.getValue());
        }
      }
    }
  }

  private void scanRefForR5(XVerExtensionManager xver, Set<String> set, String url) {
    if (xver.matchingUrl(url) && xver.isR5(url)) {
      set.add(url);
    }
  }

  private void recordOutcome(Exception ex, ValidationPresenter val) {
    try {
      JsonObject j = new JsonObject();
      if (pf.sourceIg != null) {
        j.add("url", pf.sourceIg.getUrl());
        j.add("name", pf.sourceIg.getName());
        j.add("title", pf.sourceIg.getTitle());
        j.add("description", preProcessMarkdown(pf.sourceIg.getDescription()));
        if (pf.sourceIg.hasDate()) {
          j.add("ig-date", pf.sourceIg.getDateElement().primitiveValue());
        }
        if (pf.sourceIg.hasStatus()) {
          j.add("status", pf.sourceIg.getStatusElement().primitiveValue());
        }
      }
      if (pf.publishedIg != null && pf.publishedIg.hasPackageId()) {
        j.add("package-id", pf.publishedIg.getPackageId());
        j.add("ig-ver", pf.publishedIg.getVersion());
      }
      j.add("date", new SimpleDateFormat("EEE, dd MMM, yyyy HH:mm:ss Z", new Locale("en", "US")).format(pf.getExecTime().getTime()));
      j.add("dateISO8601", new DateTimeType(pf.getExecTime()).asStringValue());
      if (val != null) {
        j.add("errs", val.getErr());
        j.add("warnings", val.getWarn());
        j.add("hints", val.getInfo());
        j.add("suppressed-hints", val.getSuppressedInfo());
        j.add("suppressed-warnings", val.getSuppressedWarnings());
      }
      if (ex != null) {
        j.add("exception", ex.getMessage());
      }
      j.add("version", pf.version);
      if (pf.templatePck != null) {
        j.add("template", pf.templatePck);
      }
      j.add("tool", Constants.VERSION+" ("+ToolsVersion.TOOLS_VERSION+")");
      j.add("maxMemory", pf.maxMemory);
      String json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(j, true);
      FileUtilities.stringToFile(json, Utilities.path(settings.getDestDir() != null ? settings.getDestDir() : pf.outputDir, "qa.json"));

      j = new JsonObject();
      j.add("date", new SimpleDateFormat("EEE, dd MMM, yyyy HH:mm:ss Z", new Locale("en", "US")).format(pf.getExecTime().getTime()));
      j.add("doco", "For each file: start is seconds after start activity occurred. Length = milliseconds activity took");

      for (FetchedFile f : pf.fileList) {
        JsonObject fj = new JsonObject();
        j.forceArray("files").add(fj);
        f.processReport(f, fj);
      }
      json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(j, true);
      FileUtilities.stringToFile(json, Utilities.path(settings.getDestDir() != null ? settings.getDestDir() : pf.outputDir, "qa-time-report.json"));

      StringBuilder b = new StringBuilder();
      b.append("Source File");
      b.append("\t");
      b.append("Size");
      for (String s : FetchedFile.getColumns()) {
        b.append("\t");
        b.append(s);
      }
      b.append("\r\n");
      for (FetchedFile f : pf.fileList) {
        f.appendReport(b);
        b.append("\r\n");
      }
      FileUtilities.stringToFile(b.toString(), Utilities.path(settings.getDestDir() != null ? settings.getDestDir() : pf.outputDir, "qa-time-report.tsv"));


    } catch (Exception e) {
      // nothing at all
      e.printStackTrace();
    }
  }


  @Override
  public ResourceWithReference resolve(RenderingContext context, String url, String version) throws IOException {
    if (url == null) {
      return null;
    }

    String[] parts = url.split("\\/");
    if (parts.length >= 2 && !Utilities.startsWithInList(url, "urn:uuid:", "urn:oid:", "cid:")) {
      for (FetchedFile f : pf.fileList) {
        for (FetchedResource r : f.getResources()) {
          if (r.getElement() != null && r.fhirType().equals(parts[0]) && r.getId().equals(parts[1])) {
            String path = this.pf.igpkp.getLinkFor(r, true);
            return new ResourceWithReference(ResourceReferenceKind.EXTERNAL, url, path, ResourceWrapper.forResource(context, r.getElement()));
          }
          if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
            if (url.equals(((CanonicalResource) r.getResource()).getUrl())) {
              String path = this.pf.igpkp.getLinkFor(r, true);
              return new ResourceWithReference(ResourceReferenceKind.EXTERNAL, url, path, ResourceWrapper.forResource(context, r.getResource()));
            }
          }
        }
      }
      for (FetchedFile f : pf.fileList) {
        for (FetchedResource r : f.getResources()) {
          if (r.fhirType().equals("Bundle")) {
            List<Element> entries = r.getElement().getChildrenByName("entry");
            for (Element entry : entries) {
              Element res = entry.getNamedChild("resource");
              if (res != null && res.fhirType().equals(parts[0]) && res.hasChild("id") && res.getNamedChildValue("id").equals(parts[1])) {
                String path = this.pf.igpkp.getLinkFor(r, true)+"#"+parts[0]+"_"+parts[1];
                return new ResourceWithReference(ResourceReferenceKind.EXTERNAL, url, path, ResourceWrapper.forResource(context, res));
              }
            }
          }
        }
      }
    }
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("Bundle")) {
          List<Element> entries = r.getElement().getChildrenByName("entry");
          for (Element entry : entries) {
            Element res = entry.getNamedChild("resource");
            String fu = entry.getNamedChildValue("fullUrl");
            if (res != null && fu != null && fu.equals(url)) {
              String path = this.pf.igpkp.getLinkFor(r, true)+"#"+fu.replace(":", "-");
              return new ResourceWithReference(ResourceReferenceKind.EXTERNAL, url, path, ResourceWrapper.forResource(context, res));
            }
          }
        }
      }
    }

    for (SpecMapManager sp : pf.specMaps) {
      ResourceWithReference res = getResourceFromMap(context, url, sp);
      if (res != null) return res;
    }

    for (PublisherUtils.LinkedSpecification sp : pf.linkSpecMaps) {
      ResourceWithReference res = getResourceFromMap(context, url, sp.getSpm());
      if (res != null) return res;
    }

    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if ("ActorDefinition".equals(r.fhirType())) {
          ActorDefinition act = ((ActorDefinition) r.getResource());
          String aurl = ExtensionUtilities.readStringExtension(act, "http://hl7.org/fhir/tools/StructureDefinition/ig-actor-example-url");
          if (aurl != null && url.startsWith(aurl)) {
            String tail = url.substring(aurl.length()+1);
            for (ImplementationGuideDefinitionResourceComponent igr : this.pf.sourceIg.getDefinition().getResource()) {
              if (tail.equals(igr.getReference().getReference())) {
                String actor = ExtensionUtilities.readStringExtension(igr, "http://hl7.org/fhir/tools/StructureDefinition/ig-example-actor");
                if (actor.equals(act.getUrl())) {
                  for (FetchedFile f2 : this.pf.fileList) {
                    for (FetchedResource r2 : f2.getResources()) {
                      String id = r2.fhirType()+"/"+r2.getId();
                      if (tail.equals(id)) {
                        String path = this.pf.igpkp.getLinkFor(r2, true);
                        return new ResourceWithReference(ResourceReferenceKind.EXTERNAL, url, path, ResourceWrapper.forResource(context, r2.getElement()));
                      }
                    }
                  }
                }
              } 
            }
          }
        }
      }
    }
    return null;

  }

  private static ResourceWithReference getResourceFromMap(RenderingContext context, String url, SpecMapManager sp) throws IOException {

    String[] pl = url.split("\\/");
    String rt = pl.length >= 2 ? pl[pl.length-2] : null;
    String id = pl.length >= 2 ? pl[pl.length-1] : null;
    String fp = Utilities.isAbsoluteUrl(url) ? url : sp.getBase()+"/"+ url;

    if (rt != null && !context.getContext().getResourceNamesAsSet().contains(rt)) {
      rt = null;
      id = null;
    }
    String path;
    try {
      path = sp.getPath(fp, null, rt, id);
    } catch (Exception e) {
      path = null;
    }

    // hack around an error in the R5 specmap file
    if (path != null && sp.isCore() && path.startsWith("http://terminology.hl7.org")) {
      path = null;
    }

    if (path != null) {
      InputStream s = null;
      if (rt != null && sp.getNpm() != null && fp.contains("/") && sp.getLoader() != null) {
        s = sp.getNpm().loadExampleResource(rt, id);
      }
      if (s == null) {
        return new ResourceWithReference(ResourceReferenceKind.EXTERNAL, url, path, null);
      } else {
        IContextResourceLoader loader = sp.getLoader();
        Resource res = loader.loadResource(s, true);
        res.setWebPath(path);
        return new ResourceWithReference(ResourceReferenceKind.EXTERNAL, url, path, ResourceWrapper.forResource(context, res));

      }
    }
    return null;
  }

  private void clean() throws Exception {
    for (Resource r : pf.loaded) {
      pf.context.dropResource(r);
    }
    if (settings.getDestDir() != null) {
      if (!(new File(settings.getDestDir()).exists()))
        FileUtilities.createDirectory(settings.getDestDir());
      FileUtilities.copyDirectory(pf.outputDir, settings.getDestDir(), null);
    }
  }

  public void checkDependencies(FetchedFile igf, IniFile buildTracker) throws Exception {
    if (!settings.isRapidoMode()) {
      buildTracker.setStringProperty("status", "uuid", HierarchicalTableGenerator.uuid, null);
      buildTracker.save();
      return;
    }
    pf.hasCheckedDependencies = true;

    if (buildTracker.getBooleanProperty("status", "started") && !buildTracker.getBooleanProperty("status", "complete")) {
      log("Rapido Mode: Complete Build after failure");
      pf.changeList.addAll(pf.fileList);
      loader.clearTempFolder();
      return;
    }
    buildTracker.setBooleanProperty("status", "complete", false, null);
    buildTracker.setBooleanProperty("status", "started", true, null);

    // first, we load all the direct dependency lists
    for (FetchedFile f : pf.fileList) {
      if (igf != f) {
        if (f.getDependencies() == null) {
          loadDependencyList(f, igf);
        }
      }
    }

    for (FetchedFile f : pf.fileList) {
      f.calculateHash();
    }

    Set<String> changed = new HashSet<>();
    for (FetchedFile f : pf.fileList) {
      String hNow = Long.toString(f.getHash());
      String hThen = buildTracker.getStringProperty("source", f.getLoadPath());
      if (!hNow.equals(hThen)) {
       changed.add(f.getLoadPath());
       buildTracker.setStringProperty("source", f.getLoadPath(), hNow, null);
      }
    }
    for (FetchedFile f : pf.fileList) {
      String hNow = Long.toString(f.getCalcHash());
      String hThen = buildTracker.getStringProperty("tracked", f.getLoadPath());
      if (!hNow.equals(hThen)) {
        pf.changeList.add(f);
        buildTracker.setStringProperty("tracked", f.getLoadPath(), hNow, null);
      }
    }
    buildTracker.save();


    if (pf.changeList.isEmpty()) {
      log("Rapido Mode: Complete Build (no changes)");
      pf.changeList.addAll(pf.fileList);
      loader.clearTempFolder();
      buildTracker.setStringProperty("status", "uuid", HierarchicalTableGenerator.uuid, null);
    } else if (pf.changeList.size() == pf.fileList.size()) {
      log("Rapido Mode: Complete Build (all files)");
      loader.clearTempFolder();
      buildTracker.setStringProperty("status", "uuid", HierarchicalTableGenerator.uuid, null);
    } else {
      log("Rapido Mode: Differential Build ("+pf.changeList.size()+" files)");
      for (String s : Utilities.sorted(changed)) {
        System.out.println("  "+s);
      }
      HierarchicalTableGenerator.uuid = buildTracker.getStringProperty("status", "uuid");
    }
  }

  private void loadDependencyList(FetchedFile f, FetchedFile igf) {
    f.setDependencies(new ArrayList<FetchedFile>());
    f.getDependencies().add(igf); // everything is dependent on the IG

    for (FetchedResource r : f.getResources()) {
      loadDepedencies(f, r, f.getDependencies());
    }
  }

  private void loadDepedencies(FetchedFile f, FetchedResource r, List<FetchedFile> dependencies) {
    ElementVisitor.IElementVisitor depVisitor = new DependencyElementVisitor(pf.fileList, dependencies, f);
    new ElementVisitor(depVisitor).visit(r, r.getElement());
  }

  private void loadValueSetDependencies(FetchedFile f, FetchedResource r) {
    ValueSet vs = (ValueSet) r.getResource();
    for (ConceptSetComponent cc : vs.getCompose().getInclude()) {
      for (UriType vsi : cc.getValueSet()) {
        FetchedFile fi = getFileForUri(vsi.getValue());
        if (fi != null)
          f.getDependencies().add(fi);
      }
    }
    for (ConceptSetComponent cc : vs.getCompose().getExclude()) {
      for (UriType vsi : cc.getValueSet()) {
        FetchedFile fi = getFileForUri(vsi.getValue());
        if (fi != null)
          f.getDependencies().add(fi);
      }
    }
    for (ConceptSetComponent vsc : vs.getCompose().getInclude()) {
      FetchedFile fi = getFileForUri(vsc.getSystem());
      if (fi != null)
        f.getDependencies().add(fi);
    }
    for (ConceptSetComponent vsc : vs.getCompose().getExclude()) {
      FetchedFile fi = getFileForUri(vsc.getSystem());
      if (fi != null)
        f.getDependencies().add(fi);
    }
  }


  private void loadProfileDependencies(FetchedFile f, FetchedResource r) {
    StructureDefinition sd = (StructureDefinition) r.getResource();
    FetchedFile fi = getFileForUri(sd.getBaseDefinition());
    if (fi != null)
      f.getDependencies().add(fi);
  }

  private boolean bool(JsonObject obj, String name) throws Exception {
    if (!obj.has(name))
      return false;
    if (!(obj.get(name) instanceof JsonPrimitive))
      return false;
    JsonPrimitive p = obj.get(name).asJsonPrimitive();
    if (p instanceof JsonBoolean)
      return ((JsonBoolean) p).isValue();
    return false;
  }


  public class FoundResource {
    private String path;
    private FhirFormat format;
    private String type;
    private String id;
    private String url;
    public FoundResource(String path, FhirFormat format, String type, String id, String url) {
      super();
      this.path = path;
      this.format = format;
      this.type = type;
      this.id = id;
    }
    public String getPath() {
      return path;
    }
    public FhirFormat getFormat() {
      return format;
    }
    public String getType() {
      return type;
    }
    public String getId() {
      return id;
    }
    public String getUrl() {
      return url;
    }
  }

  private void copyDefaultTemplate() throws IOException, FHIRException {
    if (!new File(pf.adHocTmpDir).exists())
      FileUtilities.createDirectory(pf.adHocTmpDir);
    FileUtilities.clearDirectory(pf.adHocTmpDir);

    FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager.Builder().build();

    NpmPackage npm = null; 
    if (settings.getSpecifiedVersion() == null) {
      npm = pcm.loadPackage("hl7.fhir.r5.core", Constants.VERSION);
    } else {
      String vid = VersionUtilities.getCurrentVersion(settings.getSpecifiedVersion());
      String pid = VersionUtilities.packageForVersion(vid);
      npm = pcm.loadPackage(pid, vid);
    }

    InputStream igTemplateInputStream = npm.load("other", "ig-template.zip");
    String zipTargetDirectory = pf.adHocTmpDir;
    unzipToDirectory(igTemplateInputStream, zipTargetDirectory);
  }

  protected static void unzipToDirectory(InputStream inputStream, String zipTargetDirectory) throws IOException {
    ZipInputStream zip = new ZipInputStream(inputStream);
    byte[] buffer = new byte[2048];
    ZipEntry entry;
    while((entry = zip.getNextEntry())!=null) {

      if (entry.isDirectory()) {
        continue;
      }
      String n = CompressionUtilities.makeOSSafe(entry.getName());
      String filename = Utilities.path(zipTargetDirectory, n);
      String dir = FileUtilities.getDirectoryForFile(filename);

      CompressionUtilities.zipSlipProtect(n, Path.of(dir));
      FileUtilities.createDirectory(dir);

      FileOutputStream output = new FileOutputStream(filename);
      int len = 0;
      while ((len = zip.read(buffer)) > 0)
        output.write(buffer, 0, len);
      output.close();
    }
    zip.close();
  }

  private void buildConfigFile() throws IOException, org.hl7.fhir.exceptions.FHIRException, FHIRFormatError {
    settings.setConfigFile(Utilities.path(pf.adHocTmpDir, "ig.json"));
    // temporary config, until full ig template is in place
    String v = settings.getSpecifiedVersion() != null ? VersionUtilities.getCurrentVersion(settings.getSpecifiedVersion()) : Constants.VERSION;
    String igs = VersionUtilities.isR3Ver(v) ? "ig3.xml" : "ig4.xml";
    FileUtilities.stringToFile(
        "{\r\n"+
            "  \"tool\" : \"jekyll\",\r\n"+
            "  \"canonicalBase\" : \"http://hl7.org/fhir/ig\",\r\n"+
            "  \"npm-name\" : \"hl7.fhir.test.ig\",\r\n"+
            "  \"license\" : \"not-open-source\",\r\n"+
            "  \"version\" : \""+v+"\",\r\n"+
            "  \"resources\" : {},\r\n"+
            "  \"paths\" : {\r\n"+
            "    \"resources\" : \"resources\",\r\n"+
            "    \"pages\" : \"pages\",\r\n"+
            "    \"temp\" : \"temp\",\r\n"+
            "    \"qa\" : \"qa\",\r\n"+
            "    \"output\" : \"output\",\r\n"+
            "    \"specification\" : \"http://build.fhir.org/\"\r\n"+
            "  },\r\n"+
            "  \"sct-edition\": \"http://snomed.info/sct/900000000000207008\",\r\n"+
            "  \"source\": \""+igs+"\"\r\n"+
            "}\r\n", settings.getConfigFile());
    FileUtilities.createDirectory(Utilities.path(pf.adHocTmpDir, "resources"));
    FileUtilities.createDirectory(Utilities.path(pf.adHocTmpDir, "pages"));
  }



  private void copyFiles(String base, String folder, String dest, List<String> files) throws IOException {
    for (File f : new File(folder).listFiles()) {
      if (f.isDirectory()) {
        copyFiles(base, f.getAbsolutePath(),  Utilities.path(dest, f.getName()), files);
      } else {
        String dst = Utilities.path(dest, f.getName());
        FileUtils.copyFile(f, new File(dst), true);
        files.add(f.getAbsolutePath().substring(base.length()+1));
      }
    } 
  }


  private void download(String address, String filename) throws IOException {
    URL url = new URL(address);
    URLConnection c = url.openConnection();
    InputStream s = c.getInputStream();
    FileOutputStream f = new FileOutputStream(filename);
    transfer(s, f, 1024);
    f.close();   
  }

  public static void transfer(InputStream in, OutputStream out, int buffer) throws IOException {
    byte[] read = new byte[buffer]; // Your buffer size.
    while (0 < (buffer = in.read(read))) {
      out.write(read, 0, buffer);
    }
  }

  public void setConfigFile(String configFile) {
    this.settings.setConfigFile(configFile);
  }

  public void setLogger(ILoggingService logger) {
    this.pf.logger = logger;
    pf.fetcher.setLogger(logger);
  }

  public String getQAFile() throws IOException {
    return Utilities.path(pf.outputDir, "qa.html");
  }


  public static void prop(StringBuilder b, String name, String value) {
    b.append(name+": ");
    b.append(value);
    b.append("\r\n");
  }

  public static String buildReport(String ig, String source, String log, String qafile, String tx) throws Exception {
    StringBuilder b = new StringBuilder();
    b.append("= Log =\r\n");
    b.append(log);
    b.append("\r\n\r\n");
    b.append("= System =\r\n");

    prop(b, "ig", ig);
    prop(b, "current.dir:", getCurentDirectory());
    prop(b, "source", source);
    prop(b, "user.dir", System.getProperty("user.home"));
    prop(b, "tx.server", tx);
    prop(b, "tx.cache", Utilities.path(System.getProperty("user.home"), "fhircache"));
    prop(b, "system.type", System.getProperty("sun.arch.data.model"));
    prop(b, "system.cores", Integer.toString(Runtime.getRuntime().availableProcessors()));   
    prop(b, "system.mem.free", Long.toString(Runtime.getRuntime().freeMemory()));   
    long maxMemory = Runtime.getRuntime().maxMemory();
    prop(b, "system.mem.max", maxMemory == Long.MAX_VALUE ? "no limit" : Long.toString(maxMemory));   
    prop(b, "system.mem.total", Long.toString(Runtime.getRuntime().totalMemory()));   

    b.append("= Validation =\r\n");
    if (qafile != null && new File(qafile).exists())
      b.append(FileUtilities.fileToString(qafile));

    b.append("\r\n");
    b.append("\r\n");

    if (ig != null) {
      b.append("= IG =\r\n");
      b.append(FileUtilities.fileToString(determineActualIG(ig, null)));
    }

    b.append("\r\n");
    b.append("\r\n");
    return b.toString();
  }

  public static void main(String[] args) throws Exception {
    int exitCode = 0;

    // Prevents SLF4J(I) from printing unnecessary info to the console.
    System.setProperty("slf4j.internal.verbosity", "WARN");

    setLogbackConfiguration(args);

    org.hl7.fhir.utilities.FileFormat.checkCharsetAndWarnIfNotUTF8(System.out);

    NpmPackage.setLoadCustomResources(true);
    if (CliParams.hasNamedParam(args, FHIR_SETTINGS_PARAM)) {
      FhirSettings.setExplicitFilePath(CliParams.getNamedParam(args, FHIR_SETTINGS_PARAM));
    }
    ManagedWebAccess.loadFromFHIRSettings();


    if (CliParams.hasNamedParam(args, "-gui")) {
      IGPublisherUI.main(args);
      return;
    } else if (CliParams.hasNamedParam(args, "-v")) {
      System.out.println(IGVersionUtil.getVersion());
    } else if (CliParams.hasNamedParam(args, "-package")) {
      System.out.println("FHIR IG Publisher " + IGVersionUtil.getVersionString());
      System.out.println("Detected Java version: " + System.getProperty("java.version") + " from " + System.getProperty("java.home") + " on " + System.getProperty("os.arch") + " (" + System.getProperty("sun.arch.data.model") + "bit). " + toMB(Runtime.getRuntime().maxMemory()) + "MB available");
      System.out.println("dir = " + System.getProperty("user.dir") + ", path = " + System.getenv("PATH"));
      String s = "Parameters:";
      for (int i = 0; i < args.length; i++) {
        s = s + " " + removePassword(args, i);
      }
      System.out.println(s);
      System.out.println("character encoding = " + java.nio.charset.Charset.defaultCharset() + " / " + System.getProperty("file.encoding"));
      FilesystemPackageCacheManager pcm = CliParams.hasNamedParam(args, "system")
              ? new FilesystemPackageCacheManager.Builder().withSystemCacheFolder().build()
              : new FilesystemPackageCacheManager.Builder().build();
      System.out.println("Cache = " + pcm.getFolder());
      for (String p : CliParams.getNamedParam(args, "-package").split("\\;")) {
        NpmPackage npm = pcm.loadPackage(p);
        System.out.println("OK: " + npm.name() + "#" + npm.version() + " for FHIR version(s) " + npm.fhirVersionList() + " with canonical " + npm.canonical());
      }
//    } else if (hasNamedParam(args, "-dicom-gen")) {
//      DicomPackageBuilder pgen = new DicomPackageBuilder();
//      pgen.setSource(getNamedParam(args, "src"));
//      pgen.setDest(getNamedParam(args, "dst"));
//      if (hasNamedParam(args, "-pattern")) {
//        pgen.setPattern(getNamedParam(args, "-pattern"));
//      }
//      pgen.execute();
    } else if (CliParams.hasNamedParam(args, "-help") || CliParams.hasNamedParam(args, "-?") || CliParams.hasNamedParam(args, "/?") || CliParams.hasNamedParam(args, "?") || args.length == 0) {
      System.out.println("");
      System.out.println("To use this publisher to publish a FHIR Implementation Guide, run ");
      System.out.println("with the commands");
      System.out.println("");
      System.out.println("-spec [igpack.zip] -ig [source] -tx [url] -packages [path] -watch");
      System.out.println("");
      System.out.println("-spec: a path or a url where the igpack for the version of the core FHIR");
      System.out.println("  specification used by the ig being published is located.  If not specified");
      System.out.println("  the tool will retrieve the file from the web based on the specified FHIR version");
      System.out.println("");
      System.out.println("-ig: a path or a url where the implementation guide control file is found");
      System.out.println("  see Wiki for Documentation");
      System.out.println("");
      System.out.println("-tx: (optional) Address to use for terminology server ");
      System.out.println("  (default is http://tx.fhir.org)");
      System.out.println("  use 'n/a' to run without a terminology server");
      System.out.println("");
      System.out.println("-no-network: (optional) Stop the IG publisher accessing the network");
      System.out.println("  Beware: the ig -pubisher will not function properly if the network is prohibited");
      System.out.println("  unless the package and terminology cache are correctly populated (not documented here)");
      System.out.println("");
      //      System.out.println("-watch (optional): if this is present, the publisher will not terminate;");
      //      System.out.println("  instead, it will stay running, an watch for changes to the IG or its ");
      //      System.out.println("  contents and re-run when it sees changes ");
      System.out.println("");
      System.out.println("-packages: a directory to load packages (*.tgz) from before resolving dependencies");
      System.out.println("           this parameter can be present multiple times");
      System.out.println("");
      System.out.println("The most important output from the publisher is qa.html");
      System.out.println("");
      System.out.println("Alternatively, you can run the Publisher directly against a folder containing");
      System.out.println("a set of resources, to validate and represent them");
      System.out.println("");
      System.out.println("-source [source] -destination [dest] -tx [url]");
      System.out.println("");
      System.out.println("-source: a local to scan for resources (e.g. logical models)");
      System.out.println("-destination: where to put the output (including qa.html)");
      System.out.println("");
      System.out.println("the publisher also supports the param -proxy=[address]:[port] for if you use a proxy (stupid java won't pick up the system settings)");
      System.out.println("or you can configure the proxy using -Dhttp.proxyHost=<ip> -Dhttp.proxyPort=<port> -Dhttps.proxyHost=<ip> -Dhttps.proxyPort=<port>");
      System.out.println("");
      System.out.println("For additional information, see https://confluence.hl7.org/display/FHIR/IG+Publisher+Documentation");
//    } else if (hasNamedParam(args, "-convert")) {
//      // convert a igpack.zip to a package.tgz
//      IGPack2NpmConvertor conv = new IGPack2NpmConvertor();
//      conv.setSource(getNamedParam(args, "-source"));
//      conv.setDest(getNamedParam(args, "-dest"));
//      conv.setPackageId(getNamedParam(args, "-npm-name"));
//      conv.setVersionIg(getNamedParam(args, "-version"));
//      conv.setLicense(getNamedParam(args, "-license"));
//      conv.setWebsite(getNamedParam(args, "-website"));
//      conv.execute();
    } else if (CliParams.hasNamedParam(args, "-delete-current")) {
      if (!args[0].equals("-delete-current")) {
        throw new Error("-delete-current must have the format -delete-current {root}/{realm}/{code} -history {history} (first argument is not -delete-current)");
      }
      if (args.length < 4) {
        throw new Error("-delete-current must have the format -delete-current {root}/{realm}/{code} -history {history} (not enough arguements)");
      }
      File f = new File(args[1]);
      if (!f.exists() || !f.isDirectory()) {
        throw new Error("-delete-current must have the format -delete-current {root}/{realm}/{code} -history {history} ({root}/{realm}/{code} not found)");
      }
      String history = CliParams.getNamedParam(args, "-history");
      if (Utilities.noString(history)) {
        throw new Error("-delete-current must have the format -delete-current {root}/{realm}/{code} -history {history} (no history found)");
      }
      File fh = new File(history);
      if (!fh.exists()) {
        throw new Error("-delete-current must have the format -delete-current {root}/{realm}/{code} -history {history} ({history} not found (" + history + "))");
      }
      if (!fh.isDirectory()) {
        throw new Error("-delete-current must have the format -delete-current {root}/{realm}/{code} -history {history} ({history} not a directory (" + history + "))");
      }
      IGReleaseVersionDeleter deleter = new IGReleaseVersionDeleter();
      deleter.clear(f.getAbsolutePath(), fh.getAbsolutePath());
    } else if (CliParams.hasNamedParam(args, "-go-publish")) {
      new PublicationProcess().publish(CliParams.getNamedParam(args, "-source"), CliParams.getNamedParam(args, "-web"), CliParams.getNamedParam(args, "-date"),
              CliParams.getNamedParam(args, "-registry"), CliParams.getNamedParam(args, "-history"), CliParams.getNamedParam(args, "-templates"),
              CliParams.getNamedParam(args, "-temp"), CliParams.getNamedParam(args, "-zips"), args);
    } else if (CliParams.hasNamedParam(args, "-generate-package-registry")) {
      new PackageRegistryBuilder(CliParams.getNamedParam(args, "-generate-package-registry")).build();
    } else if (CliParams.hasNamedParam(args, "-xig")) {
      new XIGGenerator(CliParams.getNamedParam(args, "-xig"), CliParams.getNamedParam(args, "-xig-cache")).execute(Integer.parseInt(CliParams.getNamedParam(args, "-xig-step")));
    } else if (CliParams.hasNamedParam(args, "-update-history")) {
      new HistoryPageUpdater().updateHistoryPages(CliParams.getNamedParam(args, "-history"), CliParams.getNamedParam(args, "-website"), CliParams.getNamedParam(args, "-website"));
    } else if (CliParams.hasNamedParam(args, "-publish-update")) {
      if (!args[0].equals("-publish-update")) {
        throw new Error("-publish-update must have the format -publish-update -folder {folder} -registry {registry}/fhir-ig-list.json -history {folder} (first argument is not -publish-update)");
      }
      if (args.length < 3) {
        throw new Error("-publish-update must have the format -publish-update -folder {folder} -registry {registry}/fhir-ig-list.json -history {folder} (not enough args)");
      }
      File f = new File(CliParams.getNamedParam(args, "-folder"));
      if (!f.exists() || !f.isDirectory()) {
        throw new Error("-publish-update must have the format -publish-update -folder {folder} -registry {registry}/fhir-ig-list.json -history {folder} ({folder} not found)");
      }

      String registry = CliParams.getNamedParam(args, "-registry");
      if (Utilities.noString(registry)) {
        throw new Error("-publish-update must have the format -publish-update -url {url} -root {root} -registry {registry}/fhir-ig-list.json -history {folder} (-registry parameter not found)");
      }
      String history = CliParams.getNamedParam(args, "-history");
      if (Utilities.noString(history)) {
        throw new Error("-publish-update must have the format -publish-update -url {url} -root {root} -registry {registry}/fhir-ig-list.json -history {folder} (-history parameter not found)");
      }
      String filter = CliParams.getNamedParam(args, "-filter");
      boolean skipPrompt = CliParams.hasNamedParam(args, "-noconfirm");

      if (!"n/a".equals(registry)) {
        File fr = new File(registry);
        if (!fr.exists() || fr.isDirectory()) {
          throw new Error("-publish-update must have the format -publish-update -url {url} -root {root} -registry {registry}/fhir-ig-list.json -history {folder} ({registry} not found)");
        }
      }
      boolean doCore = "true".equals(CliParams.getNamedParam(args, "-core"));
      boolean updateStatements = !"false".equals(CliParams.getNamedParam(args, "-statements"));

      IGRegistryMaintainer reg = "n/a".equals(registry) ? null : new IGRegistryMaintainer(registry);
      IGWebSiteMaintainer.execute(f.getAbsolutePath(), reg, doCore, filter, skipPrompt, history, updateStatements, CliParams.getNamedParam(args, "-templates"));
      reg.finish();
    } else if (CliParams.hasNamedParam(args, "-multi")) {
      int i = 1;
      for (String ig : FileUtilities.fileToString(CliParams.getNamedParam(args, "-multi")).split("\\r?\\n")) {
        if (!ig.startsWith(";")) {
          System.out.println("=======================================================================================");
          System.out.println("Publish IG " + ig);
          Publisher self = new Publisher();
          self.setConfigFile(determineActualIG(ig, null));
          setTxServerValue(args, self);
          if (CliParams.hasNamedParam(args, "-resetTx")) {
            self.settings.setCacheOption(PublisherUtils.CacheOption.CLEAR_ALL);
          } else if (CliParams.hasNamedParam(args, "-resetTxErrors")) {
            self.settings.setCacheOption(PublisherUtils.CacheOption.CLEAR_ERRORS);
          } else {
            self.settings.setCacheOption(PublisherUtils.CacheOption.LEAVE);
          }
          try {
            self.execute();
          } catch (Exception e) {
            exitCode = 1;
            System.out.println("Publishing Implementation Guide Failed: " + e.getMessage());
            System.out.println("");
            System.out.println("Stack Dump (for debugging):");
            e.printStackTrace();
            break;
          }
          FileUtilities.stringToFile(buildReport(ig, null, self.pf.filelog.toString(), Utilities.path(self.pf.qaDir, "validation.txt"), self.settings.getTxServer()), Utilities.path(System.getProperty("java.io.tmpdir"), "fhir-ig-publisher-" + Integer.toString(i) + ".log"));
          System.out.println("=======================================================================================");
          System.out.println("");
          System.out.println("");
          i++;
        }
      }
    } else {
      Publisher self = new Publisher();
      String consoleLog = CliParams.getNamedParam(args, "log");
      if (consoleLog == null) {
        consoleLog = Utilities.path("[tmp]", "fhir-ig-publisher-tmp.log");
      }
      PublisherFields.consoleLogger = new PublisherConsoleLogger();
      if (!CliParams.hasNamedParam(args, "-auto-ig-build") && !CliParams.hasNamedParam(args, "-publish-process")) {
        PublisherFields.consoleLogger.start(consoleLog);
      }
      self.logMessage("FHIR IG Publisher " + IGVersionUtil.getVersionString());
      self.logMessage("Detected Java version: " + System.getProperty("java.version") + " from " + System.getProperty("java.home") + " on " + System.getProperty("os.name") + "/" + System.getProperty("os.arch") + " (" + System.getProperty("sun.arch.data.model") + "bit). " + toMB(Runtime.getRuntime().maxMemory()) + "MB available");
      if (!"64".equals(System.getProperty("sun.arch.data.model"))) {
        self.logMessage("Attention: you should upgrade your Java to a 64bit version in order to be able to run this program without running out of memory");
      }
      self.logMessage("dir = " + System.getProperty("user.dir") + ", path = " + System.getenv("PATH"));
      String s = "Parameters:";
      for (int i = 0; i < args.length; i++) {
        s = s + " " + removePassword(args, i);
      }
      self.logMessage(s);
      self.logMessage("Character encoding = " + java.nio.charset.Charset.defaultCharset() + " / " + System.getProperty("file.encoding"));

      //      self.logMessage("=== Environment variables =====");
      //      for (String e : System.getenv().keySet()) {
      //        self.logMessage("  "+e+": "+System.getenv().get(e));
      //      }
      self.logMessage("Start Clock @ " + nowAsString(self.settings.getStartTime()) + " (" + nowAsDate(self.settings.getStartTime()) + ")");
      self.logMessage("");
      if (CliParams.hasNamedParam(args, "-auto-ig-build")) {
        self.settings.setMode(PublisherUtils.IGBuildMode.AUTOBUILD);
        self.settings.setTargetOutput(CliParams.getNamedParam(args, "-target"));
        self.settings.setRepoSource(CliParams.getNamedParam(args, "-repo"));
      } else if (CliParams.hasNamedParam(args, "-rapido") || CliParams.hasNamedParam(args, "-cascais")) {
        self.settings.setRapidoMode(true);
        if (CliParams.hasNamedParam(args, "-watch")) {
          self.settings.setWatchMode(true);
          System.out.println("Running in Cascais:Rapido mode. Watching for Changes. Report issues to Grahame on Zulip");
        } else {
          System.out.println("Running in Cascais:Rapido mode. Report issues to Grahame on Zulip");
        }
      }

      if (CliParams.hasNamedParam(args, "-no-narrative")) {
        String param = CliParams.getNamedParam(args, "-no-narrative");
        parseAndAddNoNarrativeParam(self, param);
      }
      if (CliParams.hasNamedParam(args, "-no-validate")) {
        String param = CliParams.getNamedParam(args, "-no-validate");
        parseAndAddNoValidateParam(self, param);
      }
      if (CliParams.hasNamedParam(args, "-no-network")) {
        ManagedWebAccess.setAccessPolicy(ManagedWebAccess.WebAccessPolicy.PROHIBITED);
      }
      if (CliParams.hasNamedParam(args, "-trackFragments")) {
        self.settings.setTrackFragments(true);
      }
      if (CliParams.hasNamedParam(args, "-milestone")) {
        self.settings.setMilestoneBuild(true);
        self.settings.setRapidoMode(false);
      }
      if (FhirSettings.isProhibitNetworkAccess()) {
        System.out.println("Running without network access - output may not be correct unless cache contents are correct");
      }

      if (CliParams.hasNamedParam(args, "-validation-off")) {
        self.settings.setValidationOff(true);
        System.out.println("Running without validation to shorten the run time (editor process only)");
      }
      if (CliParams.hasNamedParam(args, "-generation-off")) {
        self.settings.setGenerationOff(true);
        System.out.println("Running without generation to shorten the run time (editor process only)");
      }

      // deprecated
      if (CliParams.hasNamedParam(args, "-new-template-format")) {
        self.settings.setNewMultiLangTemplateFormat(true);
        System.out.println("Using new style template format for multi-lang IGs");
      }

      setTxServerValue(args, self);
      if (CliParams.hasNamedParam(args, "-source")) {
        // run with standard template. this is publishing lite
        self.settings.setSourceDir(CliParams.getNamedParam(args, "-source"));
        self.settings.setDestDir(CliParams.getNamedParam(args, "-destination"));
        self.settings.setSpecifiedVersion(CliParams.getNamedParam(args, "-version"));
      } else if (!CliParams.hasNamedParam(args, "-ig") && args.length == 1 && new File(args[0]).exists()) {
        self.setConfigFile(determineActualIG(args[0], PublisherUtils.IGBuildMode.MANUAL));
      } else if (CliParams.hasNamedParam(args, "-prompt")) {
        IniFile ini = new IniFile("publisher.ini");
        String last = ini.getStringProperty("execute", "path");
        boolean ok = false;
        if (Utilities.noString(last)) {
          while (!ok) {
            System.out.print("Enter path of IG: ");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            last = reader.readLine();
            if (new File(last).exists()) {
              ok = true;
            } else {
              System.out.println("Can't find " + last);
            }
          }
        } else {
          while (!ok) {
            System.out.print("Enter path of IG [" + last + "]: ");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String nlast = reader.readLine();
            if (Utilities.noString(nlast))
              nlast = last;
            if (new File(nlast).exists()) {
              ok = true;
              last = nlast;
            } else {
              System.out.println("Can't find " + nlast);
            }
          }
        }
        ini.setStringProperty("execute", "path", last, null);
        ini.save();
        if (new File(last).isDirectory()) {
          self.setConfigFile(determineActualIG(Utilities.path(last, "ig.json"), PublisherUtils.IGBuildMode.MANUAL));
        } else {
          self.setConfigFile(determineActualIG(last, PublisherUtils.IGBuildMode.MANUAL));
        }
      } else {
        self.setConfigFile(determineActualIG(CliParams.getNamedParam(args, "-ig"), self.settings.getMode()));
        if (Utilities.noString(self.getConfigFile())) {
          throw new Exception("No Implementation Guide Specified (-ig parameter)");
        }
        self.setConfigFile(getAbsoluteConfigFilePath(self.getConfigFile()));
      }
      self.setJekyllCommand(CliParams.getNamedParam(args, "-jekyll"));
      self.setIgPack(CliParams.getNamedParam(args, "-spec"));
      String proxy = CliParams.getNamedParam(args, "-proxy");
      if (!Utilities.noString(proxy)) {
        String[] p = proxy.split("\\:");
        System.setProperty("http.proxyHost", p[0]);
        System.setProperty("http.proxyPort", p[1]);
        System.setProperty("https.proxyHost", p[0]);
        System.setProperty("https.proxyPort", p[1]);
        System.out.println("Web Proxy = " + p[0] + ":" + p[1]);
      }
      if (CliParams.getNamedParam(args, "-tx") != null) {
        self.settings.setTxServer(CliParams.getNamedParam(args, "-tx"));
      }
      self.settings.setPackagesFolder(CliParams.getNamedParam(args, "-packages"));

      if (CliParams.hasNamedParam(args, "-force-language")) {
        self.setForcedLanguage(CliParams.getNamedParam(args, "-force-language"));
      }

      if (CliParams.hasNamedParam(args, "-simplifier")) {
        self.settings.setSimplifierMode(true);
        self.settings.setGenerationOff(true);
      }
      self.settings.setDebug(CliParams.hasNamedParam(args, "-debug"));
      self.settings.setCacheVersion(CliParams.hasNamedParam(args, "-cacheVersion"));
      if (CliParams.hasNamedParam(args, "-publish")) {
        self.settings.setMode(PublisherUtils.IGBuildMode.PUBLICATION);
        self.settings.setTargetOutput(CliParams.getNamedParam(args, "-publish"));
        self.settings.setPublishing(true);
        self.settings.setTargetOutputNested(CliParams.getNamedParam(args, "-nested"));
      }
      if (CliParams.hasNamedParam(args, "-resetTx")) {
        self.settings.setCacheOption(PublisherUtils.CacheOption.CLEAR_ALL);
      } else if (CliParams.hasNamedParam(args, "-resetTxErrors")) {
        self.settings.setCacheOption(PublisherUtils.CacheOption.CLEAR_ERRORS);
      } else {
        self.settings.setCacheOption(PublisherUtils.CacheOption.LEAVE);
      }
      if (CliParams.hasNamedParam(args, "-no-sushi")) {
        self.settings.setNoSushi(true);
      }
      if (CliParams.hasNamedParam(args, PACKAGE_CACHE_FOLDER_PARAM)) {
        self.settings.setPackageCacheFolder(CliParams.getNamedParam(args, PACKAGE_CACHE_FOLDER_PARAM));
      }
      if (CliParams.hasNamedParam(args, "-authorise-non-conformant-tx-servers")) {
        TerminologyClientContext.setAllowNonConformantServers(true);
      }
      TerminologyClientContext.setCanAllowNonConformantServers(true);
      try {
        self.execute();
        if (CliParams.hasNamedParam(args, "-no-errors")) {
          exitCode = self.countErrs(self.pf.errors) > 0 ? 1 : 0;
        }
      } catch (Exception e) {
        self.log("Publishing Content Failed: " + e.getMessage());
        self.log("");
        if (e.getMessage() != null && e.getMessage().contains("xsl:message")) {
          self.log("This error was created by the template");
        } else {
          self.log("Use -? to get command line help");
          self.log("");
          self.log("Stack Dump (for debugging):");
          e.printStackTrace();
          for (StackTraceElement st : e.getStackTrace()) {
            if (st != null && self.pf.filelog != null) {
              self.pf.filelog.append(st.toString());
            }
          }
        }
        exitCode = 1;
      } finally {
        if (self.settings.getMode() == PublisherUtils.IGBuildMode.MANUAL) {
          FileUtilities.stringToFile(buildReport(CliParams.getNamedParam(args, "-ig"), CliParams.getNamedParam(args, "-source"), self.pf.filelog.toString(), Utilities.path(self.pf.qaDir, "validation.txt"), self.settings.getTxServer()), Utilities.path(System.getProperty("java.io.tmpdir"), "fhir-ig-publisher.log"));
        }
      }
      PublisherFields.consoleLogger.stop();
    }
    if (!CliParams.hasNamedParam(args, "-no-exit")) {
      System.exit(exitCode);
    }
  }

  private static void setLogbackConfiguration(String[] args) {
    setLogbackConfiguration(args, CliParams.DEBUG_LOG, Level.DEBUG);
    setLogbackConfiguration(args, CliParams.TRACE_LOG, Level.TRACE);
    //log.debug("Test debug log");
    //log.trace("Test trace log");
    //log.info(MarkerFactory.getMarker("marker"), "Test marker interface");
  }

  private static void setLogbackConfiguration(String[] args, String logParam, Level logLevel) {
    if (CliParams.hasNamedParam(args, logParam)) {
      String logFile = CliParams.getNamedParam(args, logParam);
      if (logFile != null) {
        LogbackUtilities.setLogToFileAndConsole(logLevel, logFile);
      }
    }
  }

  private void setForcedLanguage(String language) {
    this.pf.forcedLanguage = Locale.forLanguageTag(language);
  }

  public static String getAbsoluteConfigFilePath(String configFilePath) throws IOException {
    if (new File(configFilePath).isAbsolute()) {
      return configFilePath;
    }
    return Utilities.path(System.getProperty("user.dir"), configFilePath);
  }

  public static void parseAndAddNoNarrativeParam(Publisher self, String param) {
    for (String p : param.split("\\,")) {
      self.settings.getNoNarratives().add(p);
    }
  }

  public static void parseAndAddNoValidateParam(Publisher publisher, String param) {
    for (String p : param.split("\\,")) {
      publisher.settings.getNoValidate().add(p);
    }
  }

  private static String removePassword(String[] args, int i) {
    if (i == 0 || !args[i-1].toLowerCase().contains("password")) {
      return args[i];
    } else {
      return "XXXXXX";
    }
  }

  private static String removePassword(String string) {
    // TODO Auto-generated method stub
    return null;
  }

  public static void setTxServerValue(String[] args, Publisher self) {
    if (CliParams.hasNamedParam(args, "-tx")) {
      self.settings.setTxServer(CliParams.getNamedParam(args, "-tx"));
    } else if (CliParams.hasNamedParam(args, "-devtx")) {
      self.settings.setTxServer(FhirSettings.getTxFhirDevelopment());
    } else {
      self.settings.setTxServer(FhirSettings.getTxFhirProduction());
    }
  }

  private static String generateIGFromSimplifier(String folder, String output, String canonical, String npmName, String license, List<String> packages) throws Exception {
    String ig = Utilities.path(System.getProperty("java.io.tmpdir"), "simplifier", UUID.randomUUID().toString().toLowerCase());
    FileUtilities.createDirectory(ig);
    String config = Utilities.path(ig, "ig.json");
    String pages =  Utilities.path(ig, "pages");
    String resources =  Utilities.path(ig, "resources");
    FileUtilities.createDirectory(pages);
    FileUtilities.createDirectory(resources);
    FileUtilities.createDirectory(Utilities.path(ig, "temp"));
    FileUtilities.createDirectory(Utilities.path(ig, "txCache"));
    // now, copy the entire simplifer folder to pages
    FileUtilities.copyDirectory(folder, pages, null);
    // now, copy the resources to resources;
    FileUtilities.copyDirectory(Utilities.path(folder, "artifacts"), resources, null);
    JsonObject json = new JsonObject();
    JsonObject paths = new JsonObject();
    json.add("paths", paths);
    JsonArray reslist = new JsonArray();
    paths.add("resources", reslist);
    reslist.add(new JsonString("resources"));
    paths.add("pages", "pages");
    paths.add("temp", "temp");
    paths.add("output", output);
    paths.add("qa", "qa");
    paths.add("specification", "http://build.fhir.org");
    json.add("version", "3.0.2");
    json.add("license", license);
    json.add("npm-name", npmName);
    JsonObject defaults = new JsonObject();
    json.add("defaults", defaults);
    JsonObject any = new JsonObject();
    defaults.add("Any", any);
    any.add("java", false);
    any.add("xml", false);
    any.add("json", false);
    any.add("ttl", false);
    json.add("canonicalBase", canonical);
    json.add("sct-edition", "http://snomed.info/sct/731000124108");
    json.add("source", determineSource(resources, Utilities.path(folder, "artifacts")));
    json.add("path-pattern", "[type]-[id].html");
    JsonObject resn = new JsonObject();
    json.add("resources", resn);
    resn.add("*", "*");
    JsonArray deplist = new JsonArray();
    json.add("dependencyList", deplist);
    for (String d : packages) {
      String[] p = d.split("\\#");
      JsonObject dep = new JsonObject();
      deplist.add(dep);
      dep.add("package", p[0]);
      dep.add("version", p[1]);
      dep.add("name", "n"+deplist.size());
    }
    FileUtilities.stringToFile(org.hl7.fhir.utilities.json.parser.JsonParser.compose(json, true), config);
    return config;
  }


  private static String determineSource(String folder, String srcF) throws Exception {
    for (File f : new File(folder).listFiles()) {
      String src = FileUtilities.fileToString(f);
      if (src.contains("<ImplementationGuide ")) {
        return f.getName();
      }
    }
    throw new Exception("Unable to find Implementation Guide in "+srcF); 
  }


  public static String determineActualIG(String ig, PublisherUtils.IGBuildMode mode) throws Exception {
    if (ig.startsWith("http://") || ig.startsWith("https://")) {
      ig = convertUrlToLocalIg(ig);
    }
    File f = new File(ig);
    if (!f.exists() && mode == PublisherUtils.IGBuildMode.AUTOBUILD) {
      String s = FileUtilities.getDirectoryForFile(ig);
      f = new File(s == null ? System.getProperty("user.dir") : s);
    }
    if (!f.exists()) {
      throw new Exception("Unable to find the nominated IG at "+f.getAbsolutePath());
    }
    /* This call to uncheckedPath is allowed here because the results of this
       method are used to read a configuration file, NOT to write potentially
       malicious data to disk. */
    if (f.isDirectory() && new File(Utilities.uncheckedPath(ig, "ig.json")).exists()) {
      return Utilities.path(ig, "ig.json");
    } else {
      return f.getAbsolutePath();
    }
  }


  private static String convertUrlToLocalIg(String ig) throws IOException {
    String org = null;
    String repo = null;
    String branch = "master"; // todo: main?
    String[] p = ig.split("\\/");
    if (p.length > 5 && (ig.startsWith("https://build.fhir.org/ig") || ig.startsWith("http://build.fhir.org/ig"))) {
      org = p[4];
      repo = p[5]; 
      if (p.length >= 8) {
        if (!"branches".equals(p[6])) {
          throw new Error("Unable to understand IG location "+ig);
        } else {
          branch = p[7];
        }
      }  
    } if (p.length > 4 && (ig.startsWith("https://github.com/") || ig.startsWith("http://github.com/"))) {
      org = p[3];
      repo = p[4];
      if (p.length > 6) {
        if (!"tree".equals(p[5])) {
          throw new Error("Unable to understand IG location "+ig);
        } else {
          branch = p[6];
        }
      }
    } 
    if (org == null || repo == null) {
      throw new Error("Unable to understand IG location "+ig);
    }
    String folder = Utilities.path("[tmp]", "fhir-igs", makeFileName(org), makeFileName(repo), branch == null ? "master" : makeFileName(branch));
    File f = new File(folder);
    if (f.exists() && !f.isDirectory()) {
      f.delete();
    }
    if (!f.exists()) {
      FileUtilities.createDirectory(folder);
    }
    FileUtilities.clearDirectory(f.getAbsolutePath());

    String ghUrl = "https://github.com/"+org+"/"+repo+"/archive/refs/heads/"+branch+".zip";
    InputStream zip = fetchGithubUrl(ghUrl);
    CompressionUtilities.unzip(zip, Paths.get(f.getAbsolutePath()));
    for (File fd : f.listFiles()) {
      if (fd.isDirectory()) {
        return fd.getAbsolutePath();        
      }
    }
    throw new Error("Extracting GitHub source failed.");
  }

  private static InputStream fetchGithubUrl(String ghUrl) throws IOException {  
    HTTPResult res = ManagedWebAccess.get(Arrays.asList("web"), ghUrl+"?nocache=" + System.currentTimeMillis());
    res.checkThrowException();
    return new ByteArrayInputStream(res.getContent());
  }

  //  private static void gitClone(String org, String repo, String branch, String folder) throws InvalidRemoteException, TransportException, GitAPIException {
  //    System.out.println("Git clone : https://github.com/"+org+"/"+repo+(branch == null ? "" : "/tree/"+branch)+" to "+folder);    
  //    CloneCommand git = Git.cloneRepository().setURI("https://github.com/"+org+"/"+repo).setDirectory(new File(folder));
  //    if (branch != null) {
  //      git = git.setBranch(branch);
  //    }
  //    git.call();
  //  }

  private static String makeFileName(String org) {
    StringBuilder b = new StringBuilder();
    for (char ch : org.toCharArray()) {
      if (isValidFileNameChar(ch)) {
        b.append(ch);
      }
    }
    return b.toString();
  }

  private static boolean isValidFileNameChar(char ch) {
    return Character.isDigit(ch) || Character.isAlphabetic(ch) || ch == '.' || ch == '-' || ch == '_' || ch == '#'  || ch == '$';
  }


  private static String toMB(long maxMemory) {
    return Long.toString(maxMemory / (1024*1024));
  }


  protected void updateInspector(HTMLInspector parentInspector, String path) {
    parentInspector.getManual().add(path+"/full-ig.zip");
    parentInspector.getManual().add("../"+ pf.historyPage);
    parentInspector.getSpecMaps().addAll(pf.specMaps);
  }

  private String fetchCurrentIGPubVersion() {
    if (pf.currVer == null) {
      try {
        // This calls the GitHub api, to fetch the info on the latest release. As part of our release process, we name
        // all tagged releases as the version number. ex: "1.1.2".
        // This value can be grabbed from the "tag_name" field, or the "name" field of the returned JSON.
        JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObjectFromUrl("https://api.github.com/repos/HL7/fhir-ig-publisher/releases/latest");
        pf.currVer = json.asString("name").toString();
      } catch (IOException e) {
        pf.currVer = null;
      } catch (FHIRException e) {
        pf.currVer = null;
      }
    }
    return pf.currVer;
  }

  @Override
  public void recordProfileUsage(StructureDefinition profile, Object appContext, Element element) {
    if (profile != null && profile.getUrl().startsWith(pf.igpkp.getCanonical())) { // ignore anything we didn't define
      FetchedResource example = null;
      if (appContext != null) {
        if (appContext instanceof ValidationContext) {
          example = (FetchedResource) ((ValidationContext) appContext).getResource().getUserData(UserDataNames.pub_context_resource);
        } else {
          example= (FetchedResource) ((Element) appContext).getUserData(UserDataNames.pub_context_resource);
        }
      }
      if (example != null) {
        FetchedResource source = null;
        for (FetchedFile f : pf.fileList) {
          for (FetchedResource r : f.getResources()) {
            if (r.getResource() == profile) {
              source = r;
            }
          }
        }
        if (source != null) {
          source.addFoundExample(example);
          example.getFoundProfiles().add(profile.getUrl());
        }
      }
    }

  }

  public static void publishDirect(String path) throws Exception {
    Publisher self = new Publisher();
    self.setConfigFile(Publisher.determineActualIG(path, PublisherUtils.IGBuildMode.PUBLICATION));
    self.execute();
    self.settings.setTxServer(FhirSettings.getTxFhirProduction());
    if (self.countErrs(self.pf.errors) > 0) {
      throw new Exception("Building IG '"+path+"' caused an error");
    }
  }

  public long getMaxMemory() {
    return pf.maxMemory;
  }

  @Override
  public String resolveUri(RenderingContext context, String uri) {
    for (Extension ext : pf.sourceIg.getExtensionsByUrl(ExtensionDefinitions.EXT_IG_URL)) {
      String value = ext.getExtensionString("uri");
      if (value != null && value.equals(uri)) {
        return ext.getExtensionString("target");
      }
    }
    
    return null;
  }

  @Override
  public <T extends Resource> T findLinkableResource(Class<T> class_, String uri) throws IOException {
    for (PublisherUtils.LinkedSpecification spec : pf.linkSpecMaps) {
      String name = class_.getSimpleName();
      Set<String> names = "Resource".equals(name) ? Utilities.stringSet("StructureDefinition", "ValueSet", "CodeSystem", "OperationDefinition") : Utilities.stringSet(name);
      for (PackageResourceInformation pri : spec.getNpm().listIndexedResources(names)) {
        boolean match = false;
        if (uri.contains("|")) {
          match = uri.equals(pri.getUrl()+"|"+pri.getVersion());
        } else {
          match = uri.equals(pri.getUrl());          
        }
        if (match) {
          InputStream s = spec.getNpm().load(pri);
          IContextResourceLoader pl = new PublisherLoader(spec.getNpm(), spec.getSpm(), PackageHacker.fixPackageUrl(spec.getNpm().getWebLocation()), pf.igpkp, false).makeLoader();
          Resource res = pl.loadResource(s, true);
          return (T) res;
        }
      }
    }
    return null;
  }

  public static void runDirectly(String folderPath, String txServer, boolean noGeneration, boolean noValidation, boolean noNetwork, boolean trackFragmentUsage, boolean clearTermCache, boolean noSushi2, boolean wantDebug, boolean nonConformantTxServers) throws Exception {
    Publisher self = new Publisher();
    self.logMessage("FHIR IG Publisher "+IGVersionUtil.getVersionString());
    self.logMessage("Detected Java version: " + System.getProperty("java.version")+" from "+System.getProperty("java.home")+" on "+System.getProperty("os.name")+"/"+System.getProperty("os.arch")+" ("+System.getProperty("sun.arch.data.model")+"bit). "+toMB(Runtime.getRuntime().maxMemory())+"MB available");
    self.logMessage("Start Clock @ "+nowAsString(self.settings.getStartTime())+" ("+nowAsDate(self.settings.getStartTime())+")");
    self.logMessage("");
    if (noValidation) {
      self.settings.setValidationOff(noValidation);
      System.out.println("Running without generation to shorten the run time (editor process only)");
    }
    if (noGeneration) {
      self.settings.setGenerationOff(noGeneration);
      System.out.println("Running without generation to shorten the run time (editor process only)");
    }
    self.settings.setTxServer(txServer);
    self.setConfigFile(determineActualIG(folderPath, PublisherUtils.IGBuildMode.MANUAL));

    self.settings.setDebug(wantDebug);

    if (clearTermCache) {
      self.settings.setCacheOption(PublisherUtils.CacheOption.CLEAR_ALL);
    } else {
      self.settings.setCacheOption(PublisherUtils.CacheOption.LEAVE);
    }
    if (noSushi2) {
      self.settings.setNoSushi(true);
    }
    if (nonConformantTxServers) {
      TerminologyClientContext.setAllowNonConformantServers(true);
      TerminologyClientContext.setCanAllowNonConformantServers(true);
    }      
    self.execute();
  }


}
