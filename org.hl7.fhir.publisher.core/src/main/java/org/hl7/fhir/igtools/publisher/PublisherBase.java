package org.hl7.fhir.igtools.publisher;

import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_30_50;
import org.hl7.fhir.convertors.factory.*;
import org.hl7.fhir.exceptions.DefinitionException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
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
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * this class is part of the Publisher Core cluster. See @Publisher for discussion
 */
public class PublisherBase implements ILoggingService {
  final PublisherFields publisherFields;

  public PublisherBase() {
    publisherFields = new PublisherFields();
  }

  public PublisherBase(PublisherFields publisherFields) {
    super();
    this.publisherFields = publisherFields;
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
    if (publisherFields.getPackageCacheFolder() != null) {
      return new FilesystemPackageCacheManager.Builder().withCacheFolder(publisherFields.getPackageCacheFolder()).build();
    }
    return publisherFields.mode == null || publisherFields.mode == PublisherUtils.IGBuildMode.MANUAL || publisherFields.mode == PublisherUtils.IGBuildMode.PUBLICATION ?
            new FilesystemPackageCacheManager.Builder().build()
            : new FilesystemPackageCacheManager.Builder().withSystemCacheFolder().build();

  }

  protected void log(String s) {
    publisherFields.logger.logMessage(s);
  }

  protected String getTargetOutput() {
    return publisherFields.targetOutput;
  }

  protected String focusDir() {
    String dir = publisherFields.configFile.endsWith("ig.ini") ? publisherFields.configFile.substring(0, publisherFields.configFile.length()-6) : publisherFields.configFile;
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
    if (usedMemory > publisherFields.maxMemory) {
      publisherFields.maxMemory = usedMemory;
    }

    String s = msg;
    if (publisherFields.tt == null) {
      System.out.println(Utilities.padRight(s, ' ', 100));
    } else { // if (tt.longerThan(4)) {
      System.out.println(Utilities.padRight(s, ' ', 100)+" ("+ publisherFields.tt.milestone()+" / "+ publisherFields.tt.clock()+", "+Utilities.describeSize(usedMemory)+")");
    }
    if (publisherFields.killFile != null && publisherFields.killFile.exists()) {
      publisherFields.killFile.delete();
      System.out.println("Terminating Process now");
      System.exit(1);
    }
  }

  @Override
  public void logDebugMessage(LogCategory category, String msg) {
    if (publisherFields.logOptions.contains(category.toString().toLowerCase())) {
      logMessage(msg);
    }
  }

  @Override
  public boolean isDebugLogging() {
    return publisherFields.debug;
  }

  protected boolean isTemplate() throws IOException {
    File pack = new File(Utilities.path(publisherFields.configFile, "package", "package.json"));
    if (pack.exists()) {
      JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(pack);
      if (json.has("type") && "fhir.template".equals(json.asString("type"))) {
        publisherFields.isBuildingTemplate = true;
        publisherFields.templateInfo = json;
        publisherFields.npmName = json.asString("name");
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
    IFetchFile.FetchState state = publisherFields.fetcher.check(dir);
    if (state == IFetchFile.FetchState.NOT_FOUND) {
      if (emptyOk)
        return false;
      throw new Exception(String.format("Error: folder %s not found", dir));
    } else if (state == IFetchFile.FetchState.FILE)
      throw new Exception(String.format("Error: Output must be a folder (%s)", dir));
    return true;
  }

  protected void checkFile(String fn) throws Exception {
    IFetchFile.FetchState state = publisherFields.fetcher.check(fn);
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
    for (FetchedFile f : publisherFields.fileList) {
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
        this.publisherFields.errors.addAll(outcomes.get(s));
      else
        f.getErrors().addAll(outcomes.get(s));
    }
  }

  protected String processVersion(String v) {
    return v.equals("$build") ? Constants.VERSION : v;
  }

  protected String pathForVersion() {
    String v = publisherFields.version;
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
    String f = Utilities.path(FileUtilities.getDirectoryForFile(this.publisherFields.igName), "oids.ini");
    if (new File(f).exists()) {
      if (this.publisherFields.isSushi) {
        String nf = Utilities.path(this.publisherFields.rootDir, "oids.ini");
        FileUtilities.copyFile(f, nf);
        new File(f).delete();
        return nf;
      }
      return f;
    }
    if (this.publisherFields.isSushi) {
      f = Utilities.path(this.publisherFields.rootDir, "oids.ini");
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
      all.add(publisherFields.defaultTranslationLang);
      all.addAll(publisherFields.translationLangs);
    }
    return all;
  }

  protected boolean isNewML() {
    return publisherFields.newMultiLangTemplateFormat;
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
      publisherFields.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.INVALID, path, "The canonical URL for an Implementation Guide must point directly to the implementation guide resource, not to the Implementation Guide as a whole", ValidationMessage.IssueSeverity.WARNING));
    }
    return url;
  }

  protected boolean checkMakeFile(byte[] bs, String path, Set<String> outputTracker) throws IOException {
    // logDebugMessage(LogCategory.GENERATE, "Check Generate "+path);
    String s = path.toLowerCase();
    if (publisherFields.allOutputs.contains(s))
      throw new Error("Error generating build: the file "+path+" is being generated more than once (may differ by case)");
    publisherFields.allOutputs.add(s);
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
    return this.publisherFields.isChild;
  }

  public boolean isMilestoneBuild() {
    return publisherFields.milestoneBuild;
  }

  public void setMilestoneBuild(boolean milestoneBuild) {
    this.publisherFields.milestoneBuild = milestoneBuild;
  }

  protected String targetUrl() {
    if (publisherFields.mode == null)
      return "file://"+ publisherFields.outputDir;
    switch (publisherFields.mode) {
    case AUTOBUILD: return publisherFields.targetOutput == null ? "https://build.fhir.org/ig/[org]/[repo]" : publisherFields.targetOutput;
    case MANUAL: return "file://"+ publisherFields.outputDir;
    case WEBSERVER: return "http://unknown";
    case PUBLICATION: return publisherFields.targetOutput;
    default: return publisherFields.igpkp.getCanonical();
    }
  }

  protected Map<String, String> relatedIgMap() {
    if (publisherFields.relatedIGs.isEmpty()) {
      return null;
    }
    Map<String, String> map = new HashMap<>();
    for (RelatedIG ig : publisherFields.relatedIGs) {
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
      ver = publisherFields.version; // fall back to global version
    if (VersionUtilities.isR3Ver(ver)) {
      return new TypeParserR3();
    } else if (VersionUtilities.isR4Ver(ver)) {
      return new TypeParserR4();
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
    String parseVersion = publisherFields.version;
    if (r != null) {
      if (Utilities.existsInList(r.fhirType(), SpecialTypeHandler.specialTypes(publisherFields.context.getVersion()))) {
        parseVersion = SpecialTypeHandler.VERSION;
      } else if (r.getConfig() != null) {
        parseVersion = str(r.getConfig(), "version", publisherFields.version);
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
    Element e = new org.hl7.fhir.r5.elementmodel.JsonParser(publisherFields.context).parseSingle(bi, null);
    return e;
  }

  protected Resource convertFromElement(Element res) throws IOException, FHIRException, FHIRFormatError, DefinitionException {
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    new org.hl7.fhir.r5.elementmodel.JsonParser(publisherFields.context).compose(res, bs, IParser.OutputStyle.NORMAL, null);
    ByteArrayInputStream bi = new ByteArrayInputStream(bs.toByteArray());
    if (VersionUtilities.isR3Ver(publisherFields.version)) {
      org.hl7.fhir.dstu3.formats.JsonParser jp = new org.hl7.fhir.dstu3.formats.JsonParser();
      return  VersionConvertorFactory_30_50.convertResource(jp.parse(bi));
    } else if (VersionUtilities.isR4Ver(publisherFields.version)) {
      org.hl7.fhir.r4.formats.JsonParser jp = new org.hl7.fhir.r4.formats.JsonParser();
      return  VersionConvertorFactory_40_50.convertResource(jp.parse(bi));
    } else if (VersionUtilities.isR4BVer(publisherFields.version)) {
      org.hl7.fhir.r4b.formats.JsonParser jp = new org.hl7.fhir.r4b.formats.JsonParser();
      return  VersionConvertorFactory_43_50.convertResource(jp.parse(bi));
    } else if (VersionUtilities.isR2BVer(publisherFields.version)) {
      org.hl7.fhir.dstu2016may.formats.JsonParser jp = new org.hl7.fhir.dstu2016may.formats.JsonParser();
      return  VersionConvertorFactory_14_50.convertResource(jp.parse(bi));
    } else if (VersionUtilities.isR2Ver(publisherFields.version)) {
      org.hl7.fhir.dstu2.formats.JsonParser jp = new org.hl7.fhir.dstu2.formats.JsonParser();
      return VersionConvertorFactory_10_50.convertResource(jp.parse(bi));
    } else { // if (version.equals(Constants.VERSION)) {
      org.hl7.fhir.r5.formats.JsonParser jp = new org.hl7.fhir.r5.formats.JsonParser();
      return jp.parse(bi);
    }
  }

  public String license() throws Exception {
    if (Utilities.noString(publisherFields.license)) {
      throw new Exception("A license is required in the configuration file, and it must be a SPDX license identifier (see https://spdx.org/licenses/), or \"not-open-source\"");
    }
    return publisherFields.license;
  }

  protected FetchedResource fetchByResource(String type, String id) {
    for (FetchedFile f : publisherFields.fileList) {
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
    if (publisherFields.sourceIg.getDefinition().getPage().hasName()) {
      set.add(publisherFields.sourceIg.getDefinition().getPage().getName());
    }
    listPageTargets(set, publisherFields.sourceIg.getDefinition().getPage().getPage());
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
    return res;
  }

  protected Locale inferDefaultNarrativeLang(final boolean logDecision) {
    if (logDecision) {
      logDebugMessage(LogCategory.INIT, "-force-language="+ publisherFields.forcedLanguage
              + " defaultTranslationLang="+ publisherFields.defaultTranslationLang
              + (publisherFields.sourceIg == null ? "" : " sourceIg.language="+ publisherFields.sourceIg.getLanguage()
              + " sourceIg.jurisdiction="+ publisherFields.sourceIg.getJurisdictionFirstRep().getCodingFirstRep().getCode())
      );
    }
    if (publisherFields.forcedLanguage != null) {
      if (logDecision) {
        logMessage("Using " + publisherFields.forcedLanguage + " as the default narrative language. (-force-language has been set)");
      }
      return publisherFields.forcedLanguage;
    }
    if (publisherFields.defaultTranslationLang != null) {
      if (logDecision) {
        logMessage("Using " + publisherFields.defaultTranslationLang + " as the default narrative language. (Implementation Guide param i18n-default-lang)");
      }
      return Locale.forLanguageTag(publisherFields.defaultTranslationLang);
    }
    if (publisherFields.sourceIg != null) {
      if (publisherFields.sourceIg.hasLanguage()) {
        if (logDecision) {
          logMessage("Using " + publisherFields.sourceIg.getLanguage() + " as the default narrative language. (ImplementationGuide.language has been set)");
        }
        return Locale.forLanguageTag(publisherFields.sourceIg.getLanguage());
      }
      if (publisherFields.sourceIg.hasJurisdiction()) {
        final String jurisdiction = publisherFields.sourceIg.getJurisdictionFirstRep().getCodingFirstRep().getCode();
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

  protected void addFile(FetchedFile file) {
    //  	if (fileNames.contains(file.getPath())) {
    //  		dlog("Found multiple definitions for file: " + file.getName()+ ".  Using first definition only.");
    //  	} else {
    publisherFields.fileNames.add(file.getPath());
    if (file.getRelativePath()!=null)
      publisherFields.relativeNames.put(file.getRelativePath(), file);
    publisherFields.changeList.add(file);
    //  	}
  }

  protected boolean loadPrePage(FetchedFile file, PreProcessInfo ppinfo) {
    FetchedFile existing = publisherFields.altMap.get("pre-page/"+file.getPath());
    if (existing == null || existing.getTime() != file.getTime() || existing.getHash() != file.getHash()) {
      file.setProcessMode(ppinfo.hasXslt() && !file.getPath().endsWith(".md") ? FetchedFile.PROCESS_XSLT : FetchedFile.PROCESS_NONE);
      file.setXslt(ppinfo.getXslt());
      if (ppinfo.hasRelativePath())
        file.setRelativePath(ppinfo.getRelativePath() + File.separator + file.getRelativePath());
      addFile(file);
      publisherFields.altMap.put("pre-page/"+file.getPath(), file);
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
    for (ImplementationGuide.ImplementationGuideDefinitionResourceComponent r : publisherFields.publishedIg.getDefinition().getResource()) {
      if (r.hasReference() && r.getReference().getReference().equals(type+"/"+id)) {
        return r;
      }
    }
    return null;
  }

  public String getConfigFile() {
    return publisherFields.configFile;
  }

  protected StructureDefinition fetchSnapshotted(String url) throws Exception {

    ProfileUtilities utils = null;
    for (FetchedFile f : publisherFields.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() instanceof StructureDefinition) {
          StructureDefinition sd = (StructureDefinition) r.getResource();
          if (sd.getUrl().equals(url)) {
            if (!r.isSnapshotted()) {
              if (utils == null) {
                utils = new ProfileUtilities(this.publisherFields.context, null, this.publisherFields.igpkp);
                utils.setXver(this.publisherFields.context.getXVer());
                utils.setForPublication(true);
                utils.setMasterSourceFileNames(this.publisherFields.specMaps.get(0).getTargets());
                utils.setLocalFileNames(pageTargets());
                if (VersionUtilities.isR4Plus(this.publisherFields.version)) {
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
    return publisherFields.context.fetchResource(StructureDefinition.class, url);
  }

  protected void generateSnapshot(FetchedFile f, FetchedResource r, StructureDefinition sd, boolean close, ProfileUtilities utils) throws Exception {
    boolean changed = false;

    logDebugMessage(LogCategory.PROGRESS, "Check Snapshot for "+sd.getUrl());
    sd.setFhirVersion(Enumerations.FHIRVersion.fromCode(this.publisherFields.version));
    List<ValidationMessage> messages = new ArrayList<>();
    utils.setMessages(messages);
    utils.setSuppressedMappings(this.publisherFields.suppressedMappings);
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
        utils.setDefWebRoot(this.publisherFields.igpkp.getCanonical());
        try {
          if (base.getUserString(UserDataNames.render_webroot) != null) {
            utils.generateSnapshot(base, sd, sd.getUrl(), base.getUserString(UserDataNames.render_webroot), sd.getName());
          } else {
            utils.generateSnapshot(base, sd, sd.getUrl(), null, sd.getName());
          }
        } catch (Exception e) {
          if (this.publisherFields.debug) {
            e.printStackTrace();
          }
          throw new FHIRException("Unable to generate snapshot for "+sd.getUrl()+" in "+f.getName()+" because "+e.getMessage(), e);
        }
        changed = true;
      }
    } else { //sd.getKind() == StructureDefinitionKind.LOGICAL
      logDebugMessage(LogCategory.PROGRESS, "Generate Snapshot for Logical Model or specialization"+sd.getUrl());
      if (!sd.hasSnapshot()) {
        utils.setDefWebRoot(this.publisherFields.igpkp.getCanonical());
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
    this.publisherFields.context.cacheResourceFromPackage(sd, this.publisherFields.packageInfo);
  }

  protected boolean checkCanonicalsForVersions(FetchedFile f, CanonicalResource bc, boolean snapshotMode) {
    if (this.publisherFields.pinningPolicy == PublisherUtils.PinningPolicy.NO_ACTION) {
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
    publisherFields.context.setAllowLoadingDuplicates(true);

    ProfileUtilities utils = new ProfileUtilities(publisherFields.context, null, publisherFields.igpkp);
    utils.setXver(publisherFields.context.getXVer());
    utils.setForPublication(true);
    utils.setMasterSourceFileNames(publisherFields.specMaps.get(0).getTargets());
    utils.setLocalFileNames(pageTargets());
    if (VersionUtilities.isR4Plus(publisherFields.version)) {
      utils.setNewSlicingProcessing(true);
    }

    boolean first = true;
    for (FetchedFile f : publisherFields.fileList) {
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
                this.publisherFields.modifierExtensions.add(sd);
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
    for (ImplementationGuide.ImplementationGuideDefinitionResourceComponent ir : publisherFields.publishedIg.getDefinition().getResource()) {
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
    if (publisherFields.bdr == null) {
      return "description";
    }
    return publisherFields.bdr.preProcessMarkdown("json", description);
  }

  public String workingVersion() {
    return publisherFields.businessVersion == null ? publisherFields.publishedIg.getVersion() : publisherFields.businessVersion;
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
    if (publisherFields.otherFilesStartup.contains(p)) {
      return true;
    }
    if (publisherFields.otherFilesRun.contains(p)) {
      return true;
    }
    for (FetchedFile f : publisherFields.fileList) {
      if (f.getOutputNames().contains(p)) {
        return true;
      }
    }
    for (FetchedFile f : publisherFields.altMap.values()) {
      if (f.getOutputNames().contains(p)) {
        return true;
      }
    }
    return false;
  }

  public void setJekyllCommand(String theJekyllCommand) {
    if (!Utilities.noString(theJekyllCommand)) {
      this.publisherFields.jekyllCommand = theJekyllCommand;
    }
  }

  public String getJekyllCommand() {
    return this.publisherFields.jekyllCommand;
  }

  protected boolean forHL7orFHIR() {
    return publisherFields.igpkp.getCanonical().contains("hl7.org") || publisherFields.igpkp.getCanonical().contains("fhir.org") ;
  }

  protected boolean suppressId(FetchedFile f, FetchedResource r) {
    if (this.publisherFields.suppressedIds.size() == 0) {
      return false;
    } else if (this.publisherFields.suppressedIds.contains(r.getId()) || this.publisherFields.suppressedIds.contains(r.fhirType()+"/"+r.getId())) {
      return true;
    } else if (this.publisherFields.suppressedIds.get(0).equals("$examples") && r.isExample()) {
      return true;
    } else {
      return false;
    }
  }

  protected int getErrorCount() {
    int result = countErrs(publisherFields.errors);
    for (FetchedFile f : publisherFields.fileList) {
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


  public void setTxServer(String s) {
    if (!Utilities.noString(s))
      publisherFields.txServer = s;
  }

  String getTxServer() {
    return publisherFields.txServer;
  }

  protected void setIgPack(String s) {
    if (!Utilities.noString(s))
      publisherFields.igPack = s;
  }

  public PublisherUtils.CacheOption getCacheOption() {
    return publisherFields.cacheOption;
  }

  public void setCacheOption(PublisherUtils.CacheOption cacheOption) {
    this.publisherFields.cacheOption = cacheOption;
  }

  public PublisherUtils.IGBuildMode getMode() {
    return publisherFields.mode;
  }

  public void setMode(PublisherUtils.IGBuildMode mode) {
    this.publisherFields.mode = mode;
  }

  public void setFetcher(SimpleFetcher theFetcher) {
    publisherFields.fetcher = theFetcher;
  }

  public void setContext(SimpleWorkerContext theContext) {
    publisherFields.context = theContext;
  }

  public void setSpecPath(String theSpecPath) {
    publisherFields.specPath = theSpecPath;
  }

  public void setTempDir(String theTempDir) {
    publisherFields.tempDir = theTempDir;
  }

  public void setOutputDir(String theDir) {
    publisherFields.outputDir = theDir;
  }

  public void setIgName(String theIgName) {
    publisherFields.igName = theIgName;
  }

  public void setConfigFileRootPath(String theConfigFileRootPath) {
    publisherFields.configFileRootPath = theConfigFileRootPath;
  }

  public FHIRToolingClient getWebTxServer() {
    return publisherFields.webTxServer;
  }

  public void setWebTxServer(FHIRToolingClient webTxServer) {
    this.publisherFields.webTxServer = webTxServer;
  }

  public void setDebug(boolean theDebug) {
    this.publisherFields.debug = theDebug;
  }

  public void setIsChild(boolean newIsChild) {
    this.publisherFields.isChild = newIsChild;
  }

  public IGKnowledgeProvider getIgpkp() {
    return publisherFields.igpkp;
  }

  public List<FetchedFile> getFileList() {
    return publisherFields.fileList;
  }

  public ImplementationGuide getSourceIg() {
    return publisherFields.sourceIg;
  }

  public ImplementationGuide getPublishedIg() {
    return publisherFields.publishedIg;
  }

  public void setTargetOutput(String targetOutput) {
    this.publisherFields.targetOutput = targetOutput;
  }

  public String getTargetOutputNested() {
    return publisherFields.targetOutputNested;
  }

  public void setTargetOutputNested(String targetOutputNested) {
    this.publisherFields.targetOutputNested = targetOutputNested;
  }

  protected FetchedResource getResourceForRef(FetchedFile f, String ref) {
    for (FetchedResource r : f.getResources()) {
      if ((r.fhirType()+"/"+r.getId()).equals(ref))
        return r;
    }
    for (FetchedFile f1 : this.publisherFields.fileList) {
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
      RendererFactory.factory(pv, publisherFields.rc.setParser(getTypeLoader(null))).renderResource(ResourceWrapper.forResource(publisherFields.rc, pv));
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
      if (publisherFields.context.getResourceNames().contains(ref[i])) {
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
        if (VersionUtilities.getCanonicalResourceNames(publisherFields.context.getVersion()).contains(c.fhirType())) {
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
    for (ImplementationGuide.ImplementationGuideDefinitionParameterComponent p : publisherFields.publishedIg.getDefinition().getParameter()) {
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
    for (FetchedFile f : publisherFields.fileList) {
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
    for (FetchedFile f : publisherFields.fileList) {
      FetchedResource r = getResourceForUri(f, uri);
      if (r != null)
        return r;
    }
    return null;
  }

  protected FetchedFile findFileForResource(FetchedResource r) {
    for (FetchedFile f : publisherFields.fileList) {
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
      ig.getFhirVersion().add(new Enumeration<>(new Enumerations.FHIRVersionEnumFactory(), publisherFields.version));
      ig.setPackageId(publisherFields.publishedIg.getPackageId()+"."+v);
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
      if (publisherFields.trackFragments) {
        b.append(",");
        b.append(used);
      }
    }

  }

  protected void installPackage(String id, String ver) {
    try {
      if (!publisherFields.pcm.packageInstalled(id, ver)) {
        log("Found dependency on "+id+"#"+ver+" in Sushi config. Pre-installing");
        publisherFields.pcm.loadPackage(id, ver);
      }
    } catch (FHIRException | IOException e) {
      log("Unable to install "+id+"#"+ver+": "+e.getMessage());
      log("Trying to go on");
    }

  }

  public class TypeParserR2 implements RenderingContext.ITypeParser {

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
      org.hl7.fhir.dstu2.model.Type t = new org.hl7.fhir.dstu2.formats.XmlParser().parseType(xml, type);
      return VersionConvertorFactory_10_50.convertType(t);
    }

    @Override
    public Base parseType(Element base) throws FHIRFormatError, IOException, FHIRException {
      throw new NotImplementedException();
    }
  }

  public class TypeParserR14 implements RenderingContext.ITypeParser {

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
      org.hl7.fhir.dstu2016may.model.Type t = new org.hl7.fhir.dstu2016may.formats.XmlParser().parseType(xml, type);
      return VersionConvertorFactory_14_50.convertType(t);
    }
    @Override
    public Base parseType(Element base) throws FHIRFormatError, IOException, FHIRException {
      throw new NotImplementedException();
    }
  }

  public class TypeParserR3 implements RenderingContext.ITypeParser {

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
      org.hl7.fhir.dstu3.model.Type t = new org.hl7.fhir.dstu3.formats.XmlParser().parseType(xml, type);
      return VersionConvertorFactory_30_50.convertType(t);
    }
    @Override
    public Base parseType(Element base) throws FHIRFormatError, IOException, FHIRException {
      throw new NotImplementedException();
    }
  }

  public class TypeParserR4 implements RenderingContext.ITypeParser {

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
      org.hl7.fhir.r4.model.Type t = new org.hl7.fhir.r4.formats.XmlParser().parseType(xml, type);
      return VersionConvertorFactory_40_50.convertType(t);
    }
    @Override
    public Base parseType(Element base) throws FHIRFormatError, IOException, FHIRException {
      ByteArrayOutputStream bs = new ByteArrayOutputStream();
      new org.hl7.fhir.r5.elementmodel.XmlParser(publisherFields.context).compose(base, bs, IParser.OutputStyle.NORMAL, null);
      String xml = new String(bs.toByteArray(), StandardCharsets.UTF_8);
      return parseType(xml, base.fhirType());
    }
  }

  public class TypeParserR4B implements RenderingContext.ITypeParser {

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
      org.hl7.fhir.r4b.model.DataType t = new org.hl7.fhir.r4b.formats.XmlParser().parseType(xml, type);
      return VersionConvertorFactory_43_50.convertType(t);
    }
    @Override
    public Base parseType(Element base) throws FHIRFormatError, IOException, FHIRException {
      throw new NotImplementedException();
    }
  }

  public class TypeParserR5 implements RenderingContext.ITypeParser {

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
      return new org.hl7.fhir.r5.formats.XmlParser().parseType(xml, type);
    }
    @Override
    public Base parseType(Element base) throws FHIRFormatError, IOException, FHIRException {
      throw new NotImplementedException();
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
      if (url.contains("|")) {
        return false;
      }
      CanonicalResource tgt = (CanonicalResource) PublisherBase.this.publisherFields.context.fetchResourceRaw(Resource.class, url);
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
      if (PublisherBase.this.publisherFields.pinningPolicy == PublisherUtils.PinningPolicy.FIX) {
        if (!snapshotMode) {
          PublisherBase.this.publisherFields.pinCount++;
          f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.PROCESSING, path, "Pinned the version of "+url+" to "+tgt.getVersion(),
                  ValidationMessage.IssueSeverity.INFORMATION).setMessageId(PublisherMessageIds.PIN_VERSION));
        }
        if (PublisherBase.this.publisherFields.pinDest != null) {
          pinInManifest(tgt.fhirType(), url, tgt.getVersion());
        } else {
          ct.setValue(url+"|"+tgt.getVersion());
        }
        return true;
      } else {
        Map<String, String> lst = PublisherBase.this.publisherFields.validationFetcher.fetchCanonicalResourceVersionMap(null, null, url);
        if (lst.size() < 2) {
          return false;
        } else {
          if (!snapshotMode) {
            PublisherBase.this.publisherFields.pinCount++;
            f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.PROCESSING, path, "Pinned the version of "+url+" to "+tgt.getVersion()+" from choices of "+stringify(",", lst),
                    ValidationMessage.IssueSeverity.INFORMATION).setMessageId(PublisherMessageIds.PIN_VERSION));
          }
          if (PublisherBase.this.publisherFields.pinDest != null) {
            pinInManifest(tgt.fhirType(), url, tgt.getVersion());
          } else {
            ct.setValue(url+"|"+tgt.getVersion());
          }
          return true;
        }
      }
    }

    private void pinInManifest(String type, String url, String version) {
      FetchedResource r = fetchByResource("Parameters", PublisherBase.this.publisherFields.pinDest);
      if (r == null) {
        throw new Error("Unable to find nominated pin-manifest "+ PublisherBase.this.publisherFields.pinDest);
      }
      Element p = r.getElement();
      if (!p.hasUserData(UserDataNames.EXP_REVIEWED)) {
        new ExpansionParameterUtilities(PublisherBase.this.publisherFields.context).reviewVersions(p);
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
          return;
        }
      }
      Element pp = p.addElement("parameter");
      pp.setChildValue("name",pn);
      pp.setChildValue("valueUri", v);
      pp.setUserData(UserDataNames.auto_added_parameter, true);
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
