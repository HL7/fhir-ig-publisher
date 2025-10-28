package org.hl7.fhir.igtools.publisher;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_30_50;
import org.hl7.fhir.convertors.factory.*;
import org.hl7.fhir.exceptions.DefinitionException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.parsers.*;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.context.ILoggingService;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.formats.IParser;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.renderers.RendererFactory;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.utils.DataTypeVisitor;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.r5.utils.client.FHIRToolingClient;
import org.hl7.fhir.utilities.*;
import org.hl7.fhir.utilities.filesystem.CSFile;
import org.hl7.fhir.utilities.i18n.RegionToLocaleMapper;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonPrimitive;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.PackageHacker;
import org.hl7.fhir.utilities.validation.ValidationMessage;

import javax.annotation.Nonnull;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * this class is part of the Publisher Core cluster. See @Publisher for discussion
 */
public class PublisherBase implements ILoggingService {

  @Getter @Setter public PublisherFields pf;
  @Getter final public PublisherSettings settings;


  public PublisherBase() {
    settings = new PublisherSettings();
  }

  public PublisherBase(PublisherSettings settings) {
    super();
    this.settings = settings;
  }

  protected static String getCurentDirectory() {
    String currentDirectory;
    File file = new File(".");
    currentDirectory = file.getAbsolutePath();
    return currentDirectory;
  }

  protected static String nowAsString(Calendar cal) {
    DateFormat df = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL);
    return df.format(cal.getTime());
  }

  protected static String nowAsDate(Calendar cal) {
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", new Locale("en", "US"));
    return df.format(cal.getTime());
  }

  @Nonnull
  protected FilesystemPackageCacheManager getFilesystemPackageCacheManager() throws IOException {
    if (settings.getPackageCacheFolder() != null) {
      return new FilesystemPackageCacheManager.Builder().withCacheFolder(settings.getPackageCacheFolder()).build();
    }
    return settings.getMode() == null || settings.getMode() == PublisherUtils.IGBuildMode.MANUAL || settings.getMode() == PublisherUtils.IGBuildMode.PUBLICATION ?
            new FilesystemPackageCacheManager.Builder().build()
            : new FilesystemPackageCacheManager.Builder().withSystemCacheFolder().build();

  }

  protected void log(String s) {
    pf.logger.logMessage(s);
  }

  protected String getTargetOutput() {
    return settings.getTargetOutput();
  }

  protected String focusDir() {
    String dir = settings.getConfigFile().endsWith("ig.ini") ? settings.getConfigFile().substring(0, settings.getConfigFile().length()-6) : settings.getConfigFile();
    if (dir.endsWith(File.separatorChar+".")) {
      dir = dir.substring(0, dir.length()-2);
    }
    return Utilities.noString(dir) ? PublisherBase.getCurentDirectory() : dir;
  }

  @Override
  public void logMessage(String msg) {
//    String s = lastMsg;
//    lastMsg = msg;
//    if (s == null) {
//      s = "";
//    }
    Runtime runtime = Runtime.getRuntime();
    long totalMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();
    long usedMemory = totalMemory - freeMemory;
    if (pf != null && usedMemory > pf.maxMemory) {
      pf.maxMemory = usedMemory;
    }

    String s = msg;
    if (pf == null || pf.tt == null) {
      System.out.println(Utilities.padRight(s, ' ', 100));
    } else { // if (tt.longerThan(4)) {
      System.out.println(Utilities.padRight(s, ' ', 100)+" ("+ pf.tt.milestone()+" / "+ pf.tt.clock()+", "+Utilities.describeSize(usedMemory)+")");
    }
    if (pf != null && pf.killFile != null && pf.killFile.exists()) {
      pf.killFile.delete();
      System.out.println("Terminating Process now");
      System.exit(1);
    }
  }

  @Override
  public void logDebugMessage(LogCategory category, String msg) {
    if (pf.logOptions.contains(category.toString().toLowerCase())) {
      logMessage(msg);
    }
  }

  @Override
  public boolean isDebugLogging() {
    return settings.isDebug();
  }

  protected boolean isTemplate() throws IOException {
    File pack = new File(Utilities.path(settings.getConfigFile(), "package", "package.json"));
    if (pack.exists()) {
      JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(pack);
      if (json.has("type") && "fhir.template".equals(json.asString("type"))) {
        pf.isBuildingTemplate = true;
        pf.templateInfo = json;
        pf.npmName = json.asString("name");
        //        System.out.println("targetOutput: "+targetOutput);
        return true;
      }
    }

    return false;
  }

  protected boolean checkDir(String dir) throws Exception {
    return checkDir(dir, false);
  }

  protected boolean checkDir(String dir, boolean emptyOk) throws Exception {
    IFetchFile.FetchState state = pf.fetcher.check(dir);
    if (state == IFetchFile.FetchState.NOT_FOUND) {
      if (emptyOk)
        return false;
      throw new Exception(String.format("Error: folder %s not found", dir));
    } else if (state == IFetchFile.FetchState.FILE)
      throw new Exception(String.format("Error: Output must be a folder (%s)", dir));
    return true;
  }

  protected void checkFile(String fn) throws Exception {
    IFetchFile.FetchState state = pf.fetcher.check(fn);
    if (state == IFetchFile.FetchState.NOT_FOUND)
      throw new Exception(String.format("Error: file %s not found", fn));
    else if (state == IFetchFile.FetchState.DIR)
      throw new Exception(String.format("Error: Output must be a file (%s)", fn));
  }

  protected void forceDir(String dir) throws Exception {
    File f = new File(dir);
    if (!f.exists())
      FileUtilities.createDirectory(dir);
    else if (!f.isDirectory())
      throw new Exception(String.format("Error: Output must be a folder (%s)", dir));
  }

  protected FetchedFile getFileForFile(String path) {
    for (FetchedFile f : pf.fileList) {
      if (f.getPath().equals(path))
        return f;
    }
    return null;
  }

  protected void checkOutcomes(Map<String, List<ValidationMessage>> outcomes) {
    if (outcomes == null)
      return;

    for (String s : outcomes.keySet()) {
      FetchedFile f = getFileForFile(s);
      if (f == null)
        this.pf.errors.addAll(outcomes.get(s));
      else
        f.getErrors().addAll(outcomes.get(s));
    }
  }

  protected String processVersion(String v) {
    return v.equals("$build") ? Constants.VERSION : v;
  }

  protected String pathForVersion() {
    String v = pf.version;
    while (v.indexOf(".") != v.lastIndexOf(".")) {
      v = v.substring(0, v.lastIndexOf("."));
    }
    if (v.equals("1.0")) {
      return PackageHacker.fixPackageUrl("http://hl7.org/fhir/DSTU2");
    }
    if (v.equals("1.4")) {
      return PackageHacker.fixPackageUrl("http://hl7.org/fhir/2016May");
    }
    if (v.equals("3.0")) {
      return PackageHacker.fixPackageUrl("http://hl7.org/fhir/STU3");
    }
    if (v.equals("4.0")) {
      return PackageHacker.fixPackageUrl("http://hl7.org/fhir/R4");
    }
    if (v.equals("4.3")) {
      return PackageHacker.fixPackageUrl("http://hl7.org/fhir/R4B");
    }
    return PackageHacker.fixPackageUrl("http://hl7.org/fhir/R5");
  }

  protected String oidIniLocation() throws IOException {
    String f = Utilities.path(FileUtilities.getDirectoryForFile(this.pf.igName), "oids.ini");
    if (new File(f).exists()) {
      if (this.pf.isSushi) {
        String nf = Utilities.path(this.pf.rootDir, "oids.ini");
        FileUtilities.copyFile(f, nf);
        new File(f).delete();
        return nf;
      }
      return f;
    }
    if (this.pf.isSushi) {
      f = Utilities.path(this.pf.rootDir, "oids.ini");
    }
    return f;
  }

  protected boolean hasOid(List<Identifier> identifiers) {
    for (Identifier id : identifiers) {
      if ("urn:ietf:rfc:3986".equals(id.getSystem()) && id.hasValue() && id.getValue().startsWith("urn:oid:")) {
        return true;
      }
    }
    return false;
  }

  protected List<String> allLangs() {
    List<String> all = new ArrayList<String>();
    if (isNewML()) {
      all.add(pf.defaultTranslationLang);
      all.addAll(pf.translationLangs);
    }
    return all;
  }

  protected boolean isNewML() {
    return settings.isNewMultiLangTemplateFormat();
  }

  protected String checkAppendSlash(String s) {
    return s.endsWith("/") ? s : s+"/";
  }

  protected String determineCanonical(String url, String path) throws FHIRException {
    if (url == null)
      return url;
    if (url.contains("/ImplementationGuide/"))
      return url.substring(0, url.indexOf("/ImplementationGuide/"));
    if (path != null) {
      pf.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.INVALID, path, "The canonical URL for an Implementation Guide must point directly to the implementation guide resource, not to the Implementation Guide as a whole", ValidationMessage.IssueSeverity.WARNING));
    }
    return url;
  }

  protected boolean checkMakeFile(byte[] bs, String path, Set<String> outputTracker) throws IOException {
    // logDebugMessage(LogCategory.GENERATE, "Check Generate "+path);
    String s = path.toLowerCase();
    if (pf.allOutputs.contains(s))
      throw new Error("Error generating build: the file "+path+" is being generated more than once (may differ by case)");
    pf.allOutputs.add(s);
    outputTracker.add(path);
    File f = new CSFile(path);
    File folder = new File(FileUtilities.getDirectoryForFile(f));
    if (!folder.exists()) {
      FileUtilities.createDirectory(folder.getAbsolutePath());
    }
    byte[] existing = null;
    if (f.exists())
      existing = FileUtilities.fileToBytes(path);
    if (!Arrays.equals(bs, existing)) {
      FileUtilities.bytesToFile(bs, path);
      return true;
    }
    return false;
  }

  protected String str(JsonObject obj, String name) throws Exception {
    if (!obj.has(name))
      throw new Exception("Property '"+name+"' not found");
    if (!(obj.get(name) instanceof JsonPrimitive))
      throw new Exception("Property '"+name+"' not a primitive");
    JsonPrimitive p = obj.get(name).asJsonPrimitive();
    return p.asString();
  }

  protected String ostr(JsonObject obj, String name) throws Exception {
    if (obj == null)
      return null;
    if (!obj.has(name))
      return null;
    if (!(obj.get(name).isJsonPrimitive()))
      return null;
    JsonPrimitive p = obj.get(name).asJsonPrimitive();
    return p.asString();
  }

  protected String str(JsonObject obj, String name, String defValue) throws Exception {
    if (obj == null || !obj.has(name))
      return defValue;
    if (!(obj.get(name) instanceof JsonPrimitive))
      throw new Exception("Property "+name+" not a primitive");
    JsonPrimitive p = obj.get(name).asJsonPrimitive();
    return p.asString();
  }

  public boolean isChild() {
    return this.pf.isChild;
  }

  protected String targetUrl() {
    if (settings.getMode() == null)
      return "file://"+ pf.outputDir;
    switch (settings.getMode()) {
    case AUTOBUILD: return settings.getTargetOutput() == null ? "https://build.fhir.org/ig/[org]/[repo]" : settings.getTargetOutput();
    case MANUAL: return "file://"+ pf.outputDir;
    case WEBSERVER: return "http://unknown";
    case PUBLICATION: return settings.getTargetOutput();
    default: return pf.igpkp.getCanonical();
    }
  }

  protected Map<String, String> relatedIgMap() {
    if (pf.relatedIGs.isEmpty()) {
      return null;
    }
    Map<String, String> map = new HashMap<>();
    for (RelatedIG ig : pf.relatedIGs) {
      if (ig.getVersion() != null) {
        map.put(ig.getId(), ig.getVersion());
      }
    }
    return map;
  }

  protected RenderingContext.ITypeParser getTypeLoader(FetchedFile f, FetchedResource r) throws Exception {
    String ver = r.getConfig() == null ? null : ostr(r.getConfig(), "version");
    return getTypeLoader(ver);
  }

  protected RenderingContext.ITypeParser getTypeLoader(String ver) throws Exception {
    if (ver == null)
      ver = pf.version; // fall back to global version
    if (VersionUtilities.isR3Ver(ver)) {
      return new TypeParserR3();
    } else if (VersionUtilities.isR4Ver(ver)) {
      return new TypeParserR4(this.pf);
    } else if (VersionUtilities.isR2BVer(ver)) {
      return new TypeParserR14();
    } else if (VersionUtilities.isR2Ver(ver)) {
      return new TypeParserR2();
    } else if (VersionUtilities.isR4BVer(ver)) {
      return new TypeParserR4B();
    } else if (VersionUtilities.isR5Plus(ver)) {
      return new TypeParserR5();
    } else
      throw new FHIRException("Unsupported version "+ver);
  }

  protected Element convertToElement(FetchedResource r, Resource res) throws Exception {
    String parseVersion = pf.version;
    if (r != null) {
      if (Utilities.existsInList(r.fhirType(), SpecialTypeHandler.specialTypes(pf.context.getVersion()))) {
        parseVersion = SpecialTypeHandler.VERSION;
      } else if (r.getConfig() != null) {
        parseVersion = str(r.getConfig(), "version", pf.version);
      }
    }
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    if (VersionUtilities.isR3Ver(parseVersion)) {
      org.hl7.fhir.dstu3.formats.JsonParser jp = new org.hl7.fhir.dstu3.formats.JsonParser();
      jp.compose(bs, VersionConvertorFactory_30_50.convertResource(res));
    } else if (VersionUtilities.isR4Ver(parseVersion)) {
      org.hl7.fhir.r4.formats.JsonParser jp = new org.hl7.fhir.r4.formats.JsonParser();
      jp.compose(bs, VersionConvertorFactory_40_50.convertResource(res));
    } else if (VersionUtilities.isR4BVer(parseVersion)) {
      org.hl7.fhir.r4b.formats.JsonParser jp = new org.hl7.fhir.r4b.formats.JsonParser();
      jp.compose(bs, VersionConvertorFactory_43_50.convertResource(res));
    } else if (VersionUtilities.isR2BVer(parseVersion)) {
      org.hl7.fhir.dstu2016may.formats.JsonParser jp = new org.hl7.fhir.dstu2016may.formats.JsonParser();
      jp.compose(bs, VersionConvertorFactory_14_50.convertResource(res));
    } else if (VersionUtilities.isR2Ver(parseVersion)) {
      org.hl7.fhir.dstu2.formats.JsonParser jp = new org.hl7.fhir.dstu2.formats.JsonParser();
      jp.compose(bs, VersionConvertorFactory_10_50.convertResource(res, new IGR2ConvertorAdvisor5()));
    } else {
      org.hl7.fhir.r5.formats.JsonParser jp = new org.hl7.fhir.r5.formats.JsonParser();
      jp.compose(bs, res);
    }
    byte[] cnt = bs.toByteArray();
    ByteArrayInputStream bi = new ByteArrayInputStream(cnt);
    Element e = new org.hl7.fhir.r5.elementmodel.JsonParser(pf.context).parseSingle(bi, null);
    return e;
  }

  protected Resource convertFromElement(Element res) throws IOException, FHIRException, FHIRFormatError, DefinitionException {
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    new org.hl7.fhir.r5.elementmodel.JsonParser(pf.context).compose(res, bs, IParser.OutputStyle.NORMAL, null);
    ByteArrayInputStream bi = new ByteArrayInputStream(bs.toByteArray());
    if (VersionUtilities.isR3Ver(pf.version)) {
      org.hl7.fhir.dstu3.formats.JsonParser jp = new org.hl7.fhir.dstu3.formats.JsonParser();
      return  VersionConvertorFactory_30_50.convertResource(jp.parse(bi));
    } else if (VersionUtilities.isR4Ver(pf.version)) {
      org.hl7.fhir.r4.formats.JsonParser jp = new org.hl7.fhir.r4.formats.JsonParser();
      return  VersionConvertorFactory_40_50.convertResource(jp.parse(bi));
    } else if (VersionUtilities.isR4BVer(pf.version)) {
      org.hl7.fhir.r4b.formats.JsonParser jp = new org.hl7.fhir.r4b.formats.JsonParser();
      return  VersionConvertorFactory_43_50.convertResource(jp.parse(bi));
    } else if (VersionUtilities.isR2BVer(pf.version)) {
      org.hl7.fhir.dstu2016may.formats.JsonParser jp = new org.hl7.fhir.dstu2016may.formats.JsonParser();
      return  VersionConvertorFactory_14_50.convertResource(jp.parse(bi));
    } else if (VersionUtilities.isR2Ver(pf.version)) {
      org.hl7.fhir.dstu2.formats.JsonParser jp = new org.hl7.fhir.dstu2.formats.JsonParser();
      return VersionConvertorFactory_10_50.convertResource(jp.parse(bi));
    } else { // if (version.equals(Constants.VERSION)) {
      org.hl7.fhir.r5.formats.JsonParser jp = new org.hl7.fhir.r5.formats.JsonParser();
      return jp.parse(bi);
    }
  }

  public String license() throws Exception {
    if (Utilities.noString(pf.license)) {
      throw new Exception("A license is required in the configuration file, and it must be a SPDX license identifier (see https://spdx.org/licenses/), or \"not-open-source\"");
    }
    return pf.license;
  }

  protected FetchedResource fetchByResource(String type, String id) {
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals(type) && r.getId().equals(id))
          return r;
      }
    }
    return null;
  }

  protected String tail(String url) {
    return url.substring(url.lastIndexOf("/")+1);
  }

  protected String fileNameTail(String url) {
    return url.substring(url.lastIndexOf(File.separator)+1);
  }

  protected String tailPI(String url) {
    int i = url.contains("\\") ? url.lastIndexOf("\\") : url.lastIndexOf("/");
    return url.substring(i+1);
  }

  protected Set<String> pageTargets() {
    Set<String> set = new HashSet<>();
    if (pf.sourceIg.getDefinition().getPage().hasName()) {
      set.add(pf.sourceIg.getDefinition().getPage().getName());
    }
    listPageTargets(set, pf.sourceIg.getDefinition().getPage().getPage());
    return set;
  }

  protected void listPageTargets(Set<String> set, List<ImplementationGuide.ImplementationGuideDefinitionPageComponent> list) {
    for (ImplementationGuide.ImplementationGuideDefinitionPageComponent p : list) {
      if (p.hasName()) {
        set.add(p.getName());
      }
      listPageTargets(set, p.getPage());
    }
  }

  protected List<String> metadataResourceNames() {
    List<String> res = new ArrayList<>();
    // order matters here
    res.add("NamingSystem");
    res.add("CodeSystem");
    res.add("ValueSet");
    res.add("ConceptMap");
    res.add("DataElement");
    res.add("StructureDefinition");
    res.add("OperationDefinition");
    res.add("SearchParameter");
    res.add("CapabilityStatement");
    res.add("Conformance");
    res.add("StructureMap");
    res.add("ActivityDefinition");
    res.add("Citation");
    res.add("ChargeItemDefinition");
    res.add("CompartmentDefinition");
    res.add("ConceptMap");
    res.add("ConditionDefinition");
    res.add("EffectEvidenceSynthesis");
    res.add("EventDefinition");
    res.add("Evidence");
    res.add("EvidenceVariable");
    res.add("ExampleScenario");
    res.add("GraphDefinition");
    res.add("ImplementationGuide");
    res.add("Library");
    res.add("Measure");
    res.add("MessageDefinition");
    res.add("PlanDefinition");
    res.add("Questionnaire");
    res.add("ResearchDefinition");
    res.add("ResearchElementDefinition");
    res.add("RiskEvidenceSynthesis");
    res.add("SearchParameter");
    res.add("Statistic");
    res.add("TerminologyCapabilities");
    res.add("TestPlan");
    res.add("TestScript");
    res.add("ActorDefinition");
    res.add("SubscriptionTopic");
    res.add("Requirements");
    for (String s : pf.customResourceNames) {
      res.remove(s);
    }
    return res;
  }

  protected Locale inferDefaultNarrativeLang(final boolean logDecision) {
    if (logDecision) {
      logDebugMessage(LogCategory.INIT, "-force-language="+ pf.forcedLanguage
              + " defaultTranslationLang="+ pf.defaultTranslationLang
              + (pf.sourceIg == null ? "" : " sourceIg.language="+ pf.sourceIg.getLanguage()
              + " sourceIg.jurisdiction="+ pf.sourceIg.getJurisdictionFirstRep().getCodingFirstRep().getCode())
      );
    }
    if (pf.forcedLanguage != null) {
      if (logDecision) {
        logMessage("Using " + pf.forcedLanguage + " as the default narrative language. (-force-language has been set)");
      }
      return pf.forcedLanguage;
    }
    if (pf.defaultTranslationLang != null) {
      if (logDecision) {
        logMessage("Using " + pf.defaultTranslationLang + " as the default narrative language. (Implementation Guide param i18n-default-lang)");
      }
      return Locale.forLanguageTag(pf.defaultTranslationLang);
    }
    if (pf.sourceIg != null) {
      if (pf.sourceIg.hasLanguage()) {
        if (logDecision) {
          logMessage("Using " + pf.sourceIg.getLanguage() + " as the default narrative language. (ImplementationGuide.language has been set)");
        }
        return Locale.forLanguageTag(pf.sourceIg.getLanguage());
      }
      if (pf.sourceIg.hasJurisdiction()) {
        final String jurisdiction = pf.sourceIg.getJurisdictionFirstRep().getCodingFirstRep().getCode();
        Locale localeFromRegion = RegionToLocaleMapper.getLocaleFromRegion(jurisdiction);
        if (localeFromRegion != null) {
          if (logDecision) {
            logMessage("Using " + localeFromRegion + " as the default narrative language. (inferred from ImplementationGuide.jurisdiction=" + jurisdiction + ")");
          }
          return localeFromRegion;
        }
      }
    }
    if (logDecision) {
      logMessage("Using en-US as the default narrative language. (no language information in Implementation Guide)");
    }
    return new Locale("en", "US");
  }

  protected Locale inferDefaultNarrativeLang() {
    return inferDefaultNarrativeLang(false);
  }

  protected void addFile(FetchedFile file, boolean add) {
    //  	if (fileNames.contains(file.getPath())) {
    //  		dlog("Found multiple definitions for file: " + file.getName()+ ".  Using first definition only.");
    //  	} else {
    pf.fileNames.add(file.getPath());
    if (file.getRelativePath()!=null)
      pf.relativeNames.put(file.getRelativePath(), file);
    if (file.getName().contains("menu")) {
      System.out.println("");
    }
    if (add) {
      pf.fileList.add(file);
    }
    if (!settings.isRapidoMode() || pf.hasCheckedDependencies) {
      pf.changeList.add(file);
    }
    //  	}
  }

  protected boolean loadPrePage(FetchedFile file, PreProcessInfo ppinfo) {
    FetchedFile existing = pf.altMap.get("pre-page/"+file.getPath());
    if (existing == null || existing.getTime() != file.getTime() || existing.getHash() != file.getHash()) {
      file.setProcessMode(ppinfo.hasXslt() && !file.getPath().endsWith(".md") ? FetchedFile.PROCESS_XSLT : FetchedFile.PROCESS_NONE);
      file.setXslt(ppinfo.getXslt());
      if (ppinfo.hasRelativePath())
        file.setRelativePath(ppinfo.getRelativePath() + File.separator + file.getRelativePath());
      addFile(file, true);
      pf.altMap.put("pre-page/"+file.getPath(), file);
      return true;
    } else {
      return false;
    }
  }

  protected void loadIgPages(ImplementationGuide.ImplementationGuideDefinitionPageComponent page, HashMap<String, ImplementationGuide.ImplementationGuideDefinitionPageComponent> map) throws FHIRException {
    if (page.hasName() && page.hasName())
      map.put(page.getName(), page);
    for (ImplementationGuide.ImplementationGuideDefinitionPageComponent childPage: page.getPage()) {
      loadIgPages(childPage, map);
    }
  }

  protected ImplementationGuide.ImplementationGuideDefinitionResourceComponent findIGReference(String type, String id) {
    for (ImplementationGuide.ImplementationGuideDefinitionResourceComponent r : pf.publishedIg.getDefinition().getResource()) {
      if (r.hasReference() && r.getReference().getReference().equals(type+"/"+id)) {
        return r;
      }
    }
    return null;
  }

  public String getConfigFile() {
    return settings.getConfigFile();
  }

  protected StructureDefinition fetchSnapshotted(String url) throws Exception {

    ProfileUtilities utils = null;
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() instanceof StructureDefinition) {
          StructureDefinition sd = (StructureDefinition) r.getResource();
          if (sd.getUrl().equals(url)) {
            if (!r.isSnapshotted()) {
              if (utils == null) {
                utils = new ProfileUtilities(this.pf.context, null, this.pf.igpkp);
                utils.setXver(this.pf.context.getXVer());
                utils.setForPublication(true);
                utils.setMasterSourceFileNames(this.pf.specMaps.get(0).getTargets());
                utils.setLocalFileNames(pageTargets());
                if (VersionUtilities.isR4Plus(this.pf.version)) {
                  utils.setNewSlicingProcessing(true);
                }
              }
              generateSnapshot(f, r, sd, false, utils);
            }
            return sd;
          }
        }
      }
    }
    return pf.context.fetchResource(StructureDefinition.class, url);
  }

  protected void generateSnapshot(FetchedFile f, FetchedResource r, StructureDefinition sd, boolean close, ProfileUtilities utils) throws Exception {
    boolean changed = false;

    logDebugMessage(LogCategory.PROGRESS, "Check Snapshot for "+sd.getUrl());
    sd.setFhirVersion(Enumerations.FHIRVersion.fromCode(this.pf.version));
    List<ValidationMessage> messages = new ArrayList<>();
    utils.setMessages(messages);
    utils.setSuppressedMappings(this.pf.suppressedMappings);
    StructureDefinition base = sd.hasBaseDefinition() ? fetchSnapshotted(sd.getBaseDefinition()) : null;
    if (base == null) {
      throw new Exception("Cannot find or generate snapshot for base definition ("+sd.getBaseDefinition()+" from "+sd.getUrl()+")");
    }
    if (sd.isGeneratedSnapshot()) {
      changed = true;
      // we already tried to generate the snapshot, and maybe there were messages? if there are,
      // put them in the right place
      List<ValidationMessage> vmsgs = (List<ValidationMessage>) sd.getUserData(UserDataNames.SNAPSHOT_GENERATED_MESSAGES);
      if (vmsgs != null && !vmsgs.isEmpty()) {
        f.getErrors().addAll(vmsgs);
      }
    } else {
      sd.setSnapshot(null); // make sure its cleared out if it came from elsewhere so that we do actually regenerate it at this point
    }
    if (sd.getKind() != StructureDefinition.StructureDefinitionKind.LOGICAL || sd.getDerivation()== StructureDefinition.TypeDerivationRule.CONSTRAINT) {
      if (!sd.hasSnapshot()) {
        logDebugMessage(LogCategory.PROGRESS, "Generate Snapshot for "+sd.getUrl());
        List<String> errors = new ArrayList<String>();
        if (close) {
          utils.closeDifferential(base, sd);
        } else {
          try {
            utils.sortDifferential(base, sd, "profile " + sd.getUrl(), errors, true);
          } catch (Exception e) {
            messages.add(new ValidationMessage(ValidationMessage.Source.ProfileValidator, ValidationMessage.IssueType.EXCEPTION, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "Exception generating snapshot: "+e.getMessage(), ValidationMessage.IssueSeverity.ERROR));
          }
        }
        for (String s : errors) {
          messages.add(new ValidationMessage(ValidationMessage.Source.ProfileValidator, ValidationMessage.IssueType.INVALID, "StructureDefinition.where(url = '"+sd.getUrl()+"')", s, ValidationMessage.IssueSeverity.ERROR));
        }
        utils.setIds(sd, true);

        String p = sd.getDifferential().hasElement() ? sd.getDifferential().getElement().get(0).getPath() : null;
        if (p == null || p.contains(".")) {
          changed = true;
          sd.getDifferential().getElement().add(0, new ElementDefinition().setPath(p == null ? sd.getType() : p.substring(0, p.indexOf("."))));
        }
        utils.setDefWebRoot(this.pf.igpkp.getCanonical());
        try {
          if (base.getUserString(UserDataNames.render_webroot) != null) {
            utils.generateSnapshot(base, sd, sd.getUrl(), base.getUserString(UserDataNames.render_webroot), sd.getName());
          } else {
            utils.generateSnapshot(base, sd, sd.getUrl(), null, sd.getName());
          }
        } catch (Exception e) {
          if (this.settings.isDebug()) {
            e.printStackTrace();
          }
          throw new FHIRException("Unable to generate snapshot for "+sd.getUrl()+" in "+f.getName()+" because "+e.getMessage(), e);
        }
        changed = true;
      }
    } else { //sd.getKind() == StructureDefinitionKind.LOGICAL
      logDebugMessage(LogCategory.PROGRESS, "Generate Snapshot for Logical Model or specialization"+sd.getUrl());
      if (!sd.hasSnapshot()) {
        utils.setDefWebRoot(this.pf.igpkp.getCanonical());
        utils.generateSnapshot(base, sd, sd.getUrl(), Utilities.extractBaseUrl(base.getWebPath()), sd.getName());
        changed = true;
      }
    }

    if (changed || (!r.getElement().hasChild("snapshot") && sd.hasSnapshot())) {
      r.setElement(convertToElement(r, sd));
    }
    r.getElement().setUserData(UserDataNames.SNAPSHOT_ERRORS, messages);
    r.getElement().setUserData(UserDataNames.SNAPSHOT_DETAILS, sd.getSnapshot());
    f.getErrors().addAll(messages);
    r.setSnapshotted(true);
    logDebugMessage(LogCategory.CONTEXT, "Context.See "+sd.getUrl());
    this.pf.context.cacheResourceFromPackage(sd, this.pf.packageInfo);
  }

  protected boolean checkCanonicalsForVersions(FetchedFile f, CanonicalResource bc, boolean snapshotMode) {
    if (this.pf.pinningPolicy == PublisherUtils.PinningPolicy.NO_ACTION) {
      return false;
//    } else if ("ImplementationGuide".equals(bc.fhirType())) {
//      return false;
    } else {
      DataTypeVisitor dv = new DataTypeVisitor();
      dv.visit(bc, new PublisherBase.CanonicalVisitor<CanonicalType>(f, snapshotMode));
      return dv.isAnyTrue();
    }
  }

  protected void generateSnapshots() throws Exception {
    pf.context.setAllowLoadingDuplicates(true);

    ProfileUtilities utils = new ProfileUtilities(pf.context, null, pf.igpkp);
    utils.setXver(pf.context.getXVer());
    utils.setForPublication(true);
    utils.setMasterSourceFileNames(pf.specMaps.get(0).getTargets());
    utils.setLocalFileNames(pageTargets());
    if (VersionUtilities.isR4Plus(pf.version)) {
      utils.setNewSlicingProcessing(true);
    }

    boolean first = true;
    for (FetchedFile f : pf.fileList) {
      if (!f.isLoaded()) {
        f.start("generateSnapshots");
        try {
          for (FetchedResource r : f.getResources()) {
            if (r.getResource() instanceof StructureDefinition) {
              if (first) {
                logDebugMessage(LogCategory.PROGRESS, "Generate Snapshots");
                first = false;
              }
              if (r.getResEntry() != null) {
                ExtensionUtilities.setStringExtension(r.getResEntry(), ExtensionDefinitions.EXT_IGP_RESOURCE_INFO, r.fhirType()+":"+IGKnowledgeProvider.getSDType(r));
              }

              StructureDefinition sd = (StructureDefinition) r.getResource();
              if (!r.isSnapshotted()) {
                try {
                  generateSnapshot(f, r, sd, false, utils);
                } catch (Exception e) {
                  throw new Exception("Error generating snapshot for "+f.getTitle()+(f.getResources().size() > 0 ? "("+r.getId()+")" : "")+": "+e.getMessage(), e);
                }
              }
              checkCanonicalsForVersions(f, sd, false);
              if ("Extension".equals(sd.getType()) && sd.getSnapshot().getElementFirstRep().getIsModifier()) {
                this.pf.modifierExtensions.add(sd);
              }
            }
          }
        } finally {
          f.finish("generateSnapshots");
        }
      }
    }
  }

  protected boolean isExampleResource(CanonicalResource mr) {
    for (ImplementationGuide.ImplementationGuideDefinitionResourceComponent ir : pf.publishedIg.getDefinition().getResource()) {
      if (isSameResource(ir, mr)) {
        return ir.getIsExample() || ir.hasProfile();
      }
    }
    return false;
  }

  protected boolean isSameResource(ImplementationGuide.ImplementationGuideDefinitionResourceComponent ir, CanonicalResource mr) {
    return ir.getReference().getReference().equals(mr.fhirType()+"/"+mr.getId());
  }

  protected String preProcessMarkdown(String description) throws Exception {
    if (pf.bdr == null) {
      return "description";
    }
    return pf.bdr.preProcessMarkdown("json", description);
  }

  public String workingVersion() {
    return pf.businessVersion == null ? pf.publishedIg.getVersion() : pf.businessVersion;
  }

  protected void printMemUsage() {
    int mb = 1024*1024;
    Runtime runtime = Runtime.getRuntime();
    String s = "## Memory (MB): " +
               "Use = " + (runtime.totalMemory() - runtime.freeMemory()) / mb+
               ", Free = " + runtime.freeMemory() / mb+
               ", Total = " + runtime.totalMemory() / mb+
               ", Max = " + runtime.maxMemory() / mb;
    log(s);
  }

  protected boolean isValidFile(String p) {
    if (p.contains("tbl_bck")) {
      return true; // these are not always tracked
    }
    if (pf.otherFilesStartup.contains(p)) {
      return true;
    }
    if (pf.otherFilesRun.contains(p)) {
      return true;
    }
    for (FetchedFile f : pf.fileList) {
      if (f.getOutputNames().contains(p)) {
        return true;
      }
    }
    for (FetchedFile f : pf.altMap.values()) {
      if (f.getOutputNames().contains(p)) {
        return true;
      }
    }
    return false;
  }

  public void setJekyllCommand(String theJekyllCommand) {
    if (!Utilities.noString(theJekyllCommand)) {
      this.pf.jekyllCommand = theJekyllCommand;
    }
  }

  public String getJekyllCommand() {
    return this.pf.jekyllCommand;
  }

  protected boolean forHL7orFHIR() {
    return pf.igpkp.getCanonical().contains("hl7.org") || pf.igpkp.getCanonical().contains("fhir.org") ;
  }

  protected boolean suppressId(FetchedFile f, FetchedResource r) {
    if (this.pf.suppressedIds.size() == 0) {
      return false;
    } else if (this.pf.suppressedIds.contains(r.getId()) || this.pf.suppressedIds.contains(r.fhirType()+"/"+r.getId())) {
      return true;
    } else if (this.pf.suppressedIds.get(0).equals("$examples") && r.isExample()) {
      return true;
    } else {
      return false;
    }
  }

  protected int getErrorCount() {
    int result = countErrs(pf.errors);
    for (FetchedFile f : pf.fileList) {
      result = result + countErrs(f.getErrors());
    }
    return result;
  }

  protected int countErrs(List<ValidationMessage> list) {
    int i = 0;
    for (ValidationMessage vm : list) {
      if (vm.getLevel() == ValidationMessage.IssueSeverity.ERROR || vm.getLevel() == ValidationMessage.IssueSeverity.FATAL)
        i++;
    }
    return i;
  }






  protected void setIgPack(String s) {
    if (!Utilities.noString(s))
      pf.igPack = s;
  }


  public PublisherUtils.IGBuildMode getMode() {
    return settings.getMode();
  }

  public void setFetcher(SimpleFetcher theFetcher) {
    pf.fetcher = theFetcher;
  }

  public void setContext(SimpleWorkerContext theContext) {
    pf.context = theContext;
  }

  public void setSpecPath(String theSpecPath) {
    pf.specPath = theSpecPath;
  }

  public void setTempDir(String theTempDir) {
    pf.tempDir = theTempDir;
  }

  public void setOutputDir(String theDir) {
    pf.outputDir = theDir;
  }

  public void setIgName(String theIgName) {
    pf.igName = theIgName;
  }

  public void setConfigFileRootPath(String theConfigFileRootPath) {
    settings.setConfigFile(theConfigFileRootPath);
  }

  public FHIRToolingClient getWebTxServer() {
    return pf.webTxServer;
  }

  public void setWebTxServer(FHIRToolingClient webTxServer) {
    this.pf.webTxServer = webTxServer;
  }

  public void setIsChild(boolean newIsChild) {
    this.pf.isChild = newIsChild;
  }

  public IGKnowledgeProvider getIgpkp() {
    return pf.igpkp;
  }

  public List<FetchedFile> getFileList() {
    return pf.fileList;
  }

  public ImplementationGuide getSourceIg() {
    return pf.sourceIg;
  }

  public ImplementationGuide getPublishedIg() {
    return pf.publishedIg;
  }

  public String getTargetOutputNested() {
    return settings.getTargetOutputNested();
  }

  protected FetchedResource getResourceForRef(FetchedFile f, String ref) {
    for (FetchedResource r : f.getResources()) {
      if ((r.fhirType()+"/"+r.getId()).equals(ref))
        return r;
    }
    for (FetchedFile f1 : this.pf.fileList) {
      for (FetchedResource r : f1.getResources()) {
        if ((r.fhirType()+"/"+r.getId()).equals(ref))
          return r;
        if (r.getResource() != null && r.getResource() instanceof CanonicalResource && ((CanonicalResource) r.getResource()).getUrl().equals(ref))
          return r;
      }
    }

    return null;
  }

  protected boolean processProvenance(String path, Element resource, Resource r) {
    boolean containsHistory = false;
    Provenance pv = null;
    try {
      pv = (Provenance) (r == null ? convertFromElement(resource) : r);
      RendererFactory.factory(pv, pf.rc.setParser(getTypeLoader(null))).renderResource(ResourceWrapper.forResource(pf.rc, pv));
    } catch (Exception e) {
      // nothing, if there's a problem, we'll take it up elsewhere
    }
    if (pv != null) {
      for (Reference entity : pv.getTarget()) {
        if (entity.hasReference()) {
          String[] ref = entity.getReference().split("\\/");
          int i = chooseType(ref);
          if (i >= 0) {
            FetchedResource res = fetchByResource(ref[i], ref[i+1]);
            if (res != null) {
              res.getAudits().add(processProvenance(path, pv));
              containsHistory = true;
            }
          }
        }
      }
    }
    return containsHistory;
  }

  protected ProvenanceDetails processProvenance(String path, Provenance pv) {
    ProvenanceDetails res = new ProvenanceDetails();
    res.setPath(path);
    res.setAction(pv.getActivity().getCodingFirstRep());
    res.setDate(pv.hasOccurredPeriod() ? pv.getOccurredPeriod().getEndElement() : pv.hasOccurredDateTimeType() ? pv.getOccurredDateTimeType() : pv.getRecordedElement());
    if (pv.getAuthorizationFirstRep().getConcept().hasText()) {
      res.setComment(pv.getAuthorizationFirstRep().getConcept().getText());
    } else if (pv.getActivity().hasText()) {
      res.setComment(pv.getActivity().getText());
    }
    for (Provenance.ProvenanceAgentComponent agent : pv.getAgent()) {
      for (Coding c : agent.getType().getCoding()) {
        res.getActors().put(c, agent.getWho());
      }
    }
    return res;
  }

  private int chooseType(String[] ref) {
    int res = -1;
    for (int i = 0; i < ref.length-1; i++) { // note -1 - don't bother checking the last value, which might also be a resource name (e.g StructureDefinition/XXX)
      if (pf.context.getResourceNames().contains(ref[i])) {
        res = i;
      }
    }
    return res;
  }

  protected List<PublisherUtils.ContainedResourceDetails> getContained(Element e) {
    // this list is for the index. Only some kind of resources are pulled out and presented indepedently
    List<PublisherUtils.ContainedResourceDetails> list = new ArrayList<>();
    for (Element c : e.getChildren("contained")) {
      if (RendererFactory.hasSpecificRenderer(c.fhirType())) {
        // the intent of listing a resource type is that it has multiple renderings, so gets a page of it's own
        // other wise it's rendered inline
        String t = c.getChildValue("title");
        if (Utilities.noString(t)) {
          t = c.getChildValue("name");
        }
        String d = c.getChildValue("description");
        if (Utilities.noString(d)) {
          d = c.getChildValue("definition");
        }
        CanonicalResource canonical = null;
        if (VersionUtilities.getCanonicalResourceNames(pf.context.getVersion()).contains(c.fhirType())) {
          try {
            canonical = (CanonicalResource)convertFromElement(c);
          } catch (Exception ex) {
            System.out.println("Error converting contained resource " + t + " - " + ex.getMessage());
          }
        }
        list.add(new PublisherUtils.ContainedResourceDetails(c.fhirType(), c.getIdBase(), t, d, canonical));
      }
    }
    return list;
  }

  protected StringType findReleaseLabel() {
    for (ImplementationGuide.ImplementationGuideDefinitionParameterComponent p : pf.publishedIg.getDefinition().getParameter()) {
      if ("releaselabel".equals(p.getCode().getCode())) {
        return p.getValueElement();
      }
    }
    return null;
  }

  protected String findReleaseLabelString() {
    StringType s = findReleaseLabel();
    return s == null ? "n/a" : s.asStringValue();
  }

  protected FetchedFile getFileForUri(String uri) {
    for (FetchedFile f : pf.fileList) {
      if (getResourceForUri(f, uri) != null)
        return f;
    }
    return null;
  }

  protected FetchedResource getResourceForUri(FetchedFile f, String uri) {
    for (FetchedResource r : f.getResources()) {
      if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
        CanonicalResource bc = (CanonicalResource) r.getResource();
        if (bc.getUrl() != null && bc.getUrl().equals(uri))
          return r;
      }
    }
    return null;
  }

  protected FetchedResource getResourceForUri(String uri) {
    for (FetchedFile f : pf.fileList) {
      FetchedResource r = getResourceForUri(f, uri);
      if (r != null)
        return r;
    }
    return null;
  }

  protected FetchedFile findFileForResource(FetchedResource r) {
    for (FetchedFile f : pf.fileList) {
      if (f.getResources().contains(r)) {
        return f;
      }
    }
    return null;
  }

  protected String getToolingVersion() {
    InputStream vis = Publisher.class.getResourceAsStream("/version.info");
    if (vis != null) {
      IniFile vi = new IniFile(vis);
      if (vi.getStringProperty("FHIR", "buildId") != null) {
        return vi.getStringProperty("FHIR", "version")+"-"+vi.getStringProperty("FHIR", "buildId");
      } else {
        return vi.getStringProperty("FHIR", "version")+"-"+vi.getStringProperty("FHIR", "revision");
      }
    }
    return "?? (not a build IGPublisher?)";
  }

  protected byte[] convVersion(Resource res, String v) throws FHIRException, IOException {
    if (res.hasWebPath() && (res instanceof DomainResource)) {
      ExtensionUtilities.setUrlExtension((DomainResource) res, ExtensionDefinitions.EXT_WEB_SOURCE_NEW, res.getWebPath());
    }
    String version = v.startsWith("r") ? VersionUtilities.versionFromCode(v) : v;
//    checkForCoreDependencies(res);
    convertResourceR5(res, v);
    if (VersionUtilities.isR2Ver(version)) {
      return new org.hl7.fhir.dstu2.formats.JsonParser().composeBytes(VersionConvertorFactory_10_50.convertResource(res));
    } else if (VersionUtilities.isR2BVer(version)) {
      return new org.hl7.fhir.dstu2016may.formats.JsonParser().composeBytes(VersionConvertorFactory_14_50.convertResource(res));
    } else if (VersionUtilities.isR3Ver(version)) {
      return new org.hl7.fhir.dstu3.formats.JsonParser().composeBytes(VersionConvertorFactory_30_50.convertResource(res, new BaseAdvisor_30_50(false)));
    } else if (VersionUtilities.isR4Ver(version) || VersionUtilities.isR4BVer(version)) {
      return new org.hl7.fhir.r4.formats.JsonParser().composeBytes(VersionConvertorFactory_40_50.convertResource(res));
    } else if (VersionUtilities.isR5Plus(version)) {
      return new org.hl7.fhir.r5.formats.JsonParser().composeBytes(res);
    } else {
      throw new Error("Unknown version "+version);
    }
  }

  private void convertResourceR5(Resource res, String v) {
    if (res instanceof ImplementationGuide) {
      ImplementationGuide ig = (ImplementationGuide) res;
      ig.getFhirVersion().clear();
      ig.getFhirVersion().add(new Enumeration<>(new Enumerations.FHIRVersionEnumFactory(), pf.version));
      ig.setPackageId(pf.publishedIg.getPackageId()+"."+v);
    }
    if (res instanceof StructureDefinition) {
      StructureDefinition sd = (StructureDefinition) res;
      sd.setFhirVersion(Enumerations.FHIRVersion.fromCode(v));
    }
  }

  public class FragmentUseRecord {

    public int count;
    public long time;
    public long size;
    public boolean used;

    public void record(long time, long size) {
      count++;
      this.time = this.time + time;
      this.size = this.size + size;
    }

    public void setUsed() {
      used = true;
    }

    public void produce(StringBuilder b) {
      b.append(count);
      b.append(",");
      b.append(time);
      b.append(",");
      b.append(size);
      if (settings.isTrackFragments()) {
        b.append(",");
        b.append(used);
      }
    }

  }

  protected void installPackage(String id, String ver) {
    try {
      if (!pf.pcm.packageInstalled(id, ver)) {
        log("Found dependency on "+id+"#"+ver+" in Sushi config. Pre-installing");
        pf.pcm.loadPackage(id, ver);
      }
    } catch (FHIRException | IOException e) {
      log("Unable to install "+id+"#"+ver+": "+e.getMessage());
      log("Trying to go on");
    }

  }

  public class CanonicalVisitor<T> implements DataTypeVisitor.IDatatypeVisitor {
    private FetchedFile f;
    private boolean snapshotMode;

    public CanonicalVisitor(FetchedFile f, boolean snapshotMode) {
      super();
      this.f = f;
      this.snapshotMode = snapshotMode;
    }

    @Override
    public Class classT() {
      return CanonicalType.class;
    }

    @Override
    public boolean visit(String path, DataType node) {
      CanonicalType ct = (CanonicalType) node;
      String url = ct.asStringValue();

      if (url == null) {
        return false;
      }
      if (url.contains("|")) {
        return false;
      }
      CanonicalResource tgt = (CanonicalResource) PublisherBase.this.pf.context.fetchResourceRaw(Resource.class, url);
      if (tgt instanceof CodeSystem) {
        CodeSystem cs = (CodeSystem) tgt;
        if (cs.getContent() == Enumerations.CodeSystemContentMode.NOTPRESENT && cs.hasSourcePackage() && cs.getSourcePackage().isTHO()) {
          // we ignore these definitions - their version is completely wrong for a start
          return false;
        }
      }
      if (tgt == null) {
        return false;
      }
      if (!tgt.hasVersion()) {
        return false;
      }
      if (Utilities.startsWithInList(path, "ImplementationGuide.dependsOn")) {
        return false;
      }
      if (PublisherBase.this.pf.pinningPolicy == PublisherUtils.PinningPolicy.FIX) {
        if (!snapshotMode) {
          PublisherBase.this.pf.pinCount++;
          f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.PROCESSING, path, "Pinned the version of "+url+" to "+tgt.getVersion(),
                  ValidationMessage.IssueSeverity.INFORMATION).setMessageId(PublisherMessageIds.PIN_VERSION));
        }
        if (PublisherBase.this.pf.pinDest != null) {
          pinInManifest(tgt.fhirType(), url, tgt.getVersion());
        } else {
          ct.setValue(url+"|"+tgt.getVersion());
        }
        return true;
      } else {
        Map<String, String> lst = PublisherBase.this.pf.validationFetcher.fetchCanonicalResourceVersionMap(null, null, url);
        if (lst.size() < 2) {
          return false;
        } else {
          if (!snapshotMode) {
            PublisherBase.this.pf.pinCount++;
            f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.PROCESSING, path, "Pinned the version of "+url+" to "+tgt.getVersion()+" from choices of "+stringify(",", lst),
                    ValidationMessage.IssueSeverity.INFORMATION).setMessageId(PublisherMessageIds.PIN_VERSION));
          }
          if (PublisherBase.this.pf.pinDest != null) {
            pinInManifest(tgt.fhirType(), url, tgt.getVersion());
          } else {
            ct.setValue(url+"|"+tgt.getVersion());
          }
          return true;
        }
      }
    }

    private void pinInManifest(String type, String url, String version) {
      FetchedResource r = fetchByResource("Parameters", PublisherBase.this.pf.pinDest);
      if (r == null) {
        throw new Error("Unable to find nominated pin-manifest "+ PublisherBase.this.pf.pinDest);
      }
      Element p = r.getElement();
      if (!p.hasUserData(UserDataNames.EXP_REVIEWED)) {
        new ExpansionParameterUtilities(PublisherBase.this.pf.context).reviewVersions(p);
        p.setUserData(UserDataNames.EXP_REVIEWED, true);
      }
      String pn = null;
      switch (type) {
        case "CodeSystem":
          pn = "system-version";
          break;
        case "ValueSet":
          pn = "default-valueset-version";
          break;
        default:
          pn = "default-canonical-version";
      }
      String v = url+"|"+version;
      boolean add = true;
      for (Element t : p.getChildren("parameter")) {
        String name = t.getNamedChildValue("name");
        String value = t.getNamedChildValue("value");
        if (name.equals(pn) && value.startsWith(url+"|")) {
          if (!v.equals(value)) {
            if (t.hasUserData(UserDataNames.auto_added_parameter)) {
              throw new FHIRException("An error occurred building the version manifest: the IGPublisher wanted to add version "+version+" but had already added version "+value.substring(version.indexOf("|")+1));
            } else {
              throw new FHIRException("An error occurred building the version manifest: the IGPublisher wanted to add version "+version+" but found version "+value.substring(version.indexOf("|")+1)+" already specified");
            }
          }
          add = false;
        }
      }
      if (add) {
        Element pp = p.addElement("parameter");
        pp.setChildValue("name", pn);
        pp.setChildValue("valueUri", v);
        pp.setUserData(UserDataNames.auto_added_parameter, true);
      }
    }

    private String stringify(String string, Map<String, String> lst) {
      CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
      for (String s : Utilities.sorted(lst.keySet())) {
        b.append(s+" ("+lst.get(s)+")");
      }
      return b.toString();
    }
  }


}
