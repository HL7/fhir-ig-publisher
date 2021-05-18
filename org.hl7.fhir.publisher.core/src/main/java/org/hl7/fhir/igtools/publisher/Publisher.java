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

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.swing.UIManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.SystemUtils;
import org.hl7.fhir.convertors.VersionConvertor_10_50;
import org.hl7.fhir.convertors.VersionConvertor_14_30;
import org.hl7.fhir.convertors.VersionConvertor_14_50;
import org.hl7.fhir.convertors.VersionConvertor_30_50;
import org.hl7.fhir.convertors.VersionConvertor_40_50;
import org.hl7.fhir.convertors.advisors.VersionConvertorAdvisor50;
import org.hl7.fhir.convertors.misc.NpmPackageVersionConverter;
import org.hl7.fhir.convertors.txClient.TerminologyClientFactory;
import org.hl7.fhir.exceptions.DefinitionException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.exceptions.PathEngineException;
import org.hl7.fhir.igtools.publisher.FetchedFile.FetchedBundleType;
import org.hl7.fhir.igtools.publisher.IFetchFile.FetchState;
import org.hl7.fhir.igtools.publisher.Publisher.CopyRightUsageStatement;
import org.hl7.fhir.igtools.publisher.realm.NullRealmBusinessRules;
import org.hl7.fhir.igtools.publisher.realm.RealmBusinessRules;
import org.hl7.fhir.igtools.publisher.realm.USRealmBusinessRules;
import org.hl7.fhir.igtools.publisher.utils.IGRegistryMaintainer;
import org.hl7.fhir.igtools.publisher.utils.IGReleaseVersionDeleter;
import org.hl7.fhir.igtools.publisher.utils.IGWebSiteMaintainer;
import org.hl7.fhir.igtools.publisher.utils.PublicationProcess;
import org.hl7.fhir.igtools.publisher.utils.PublisherConsoleLogger;
import org.hl7.fhir.igtools.renderers.CanonicalRenderer;
import org.hl7.fhir.igtools.renderers.CodeSystemRenderer;
import org.hl7.fhir.igtools.renderers.CrossViewRenderer;
import org.hl7.fhir.igtools.renderers.DependencyRenderer;
import org.hl7.fhir.igtools.renderers.HTAAnalysisRenderer;
import org.hl7.fhir.igtools.renderers.HistoryGenerator;
import org.hl7.fhir.igtools.renderers.JsonXhtmlRenderer;
import org.hl7.fhir.igtools.renderers.OperationDefinitionRenderer;
import org.hl7.fhir.igtools.renderers.QuestionnaireRenderer;
import org.hl7.fhir.igtools.renderers.QuestionnaireResponseRenderer;
import org.hl7.fhir.igtools.renderers.StatusRenderer;
import org.hl7.fhir.igtools.renderers.StructureDefinitionRenderer;
import org.hl7.fhir.igtools.renderers.StructureMapRenderer;
import org.hl7.fhir.igtools.renderers.ValidationPresenter;
import org.hl7.fhir.igtools.renderers.ValueSetRenderer;
import org.hl7.fhir.igtools.renderers.VersionCheckRenderer;
import org.hl7.fhir.igtools.renderers.XmlXHtmlRenderer;
import org.hl7.fhir.igtools.spreadsheets.IgSpreadsheetParser;
import org.hl7.fhir.igtools.spreadsheets.MappingSpace;
import org.hl7.fhir.igtools.templates.Template;
import org.hl7.fhir.igtools.templates.TemplateManager;
import org.hl7.fhir.igtools.ui.GraphicalPublisher;
import org.hl7.fhir.r4.formats.FormatUtilities;
import org.hl7.fhir.r5.conformance.ConstraintJavaGenerator;
import org.hl7.fhir.r5.conformance.ProfileUtilities;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.context.IWorkerContext.IContextResourceLoader;
import org.hl7.fhir.r5.context.IWorkerContext.ILoggingService;
import org.hl7.fhir.r5.context.IWorkerContext.PackageVersion;
import org.hl7.fhir.r5.context.IWorkerContext.ValidationResult;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.elementmodel.ObjectConverter;
import org.hl7.fhir.r5.elementmodel.ParserBase.ValidationPolicy;
import org.hl7.fhir.r5.elementmodel.TurtleParser;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.RdfParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.Attachment;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r5.model.Bundle.BundleType;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.CapabilityStatement;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.Constants;
import org.hl7.fhir.r5.model.ContactDetail;
import org.hl7.fhir.r5.model.ContactPoint;
import org.hl7.fhir.r5.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r5.model.DateTimeType;
import org.hl7.fhir.r5.model.DomainResource;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionConstraintComponent;
import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.model.Enumerations.FHIRVersion;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r5.model.ExpressionNode;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.FhirPublication;
import org.hl7.fhir.r5.model.Identifier;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.GuidePageGeneration;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionGroupingComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionPageComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionParameterComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionResourceComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.SPDXLicense;
import org.hl7.fhir.r5.model.Library;
import org.hl7.fhir.r5.model.ListResource;
import org.hl7.fhir.r5.model.ListResource.ListResourceEntryComponent;
import org.hl7.fhir.r5.model.OperationDefinition;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.Parameters;
import org.hl7.fhir.r5.model.Provenance;
import org.hl7.fhir.r5.model.Provenance.ProvenanceAgentComponent;
import org.hl7.fhir.r5.model.Questionnaire;
import org.hl7.fhir.r5.model.Reference;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.ResourceFactory;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionContextComponent;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.r5.model.StructureMap.StructureMapModelMode;
import org.hl7.fhir.r5.model.StructureMap.StructureMapStructureComponent;
import org.hl7.fhir.r5.model.TypeDetails;
import org.hl7.fhir.r5.model.UriType;
import org.hl7.fhir.r5.model.UsageContext;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.openapi.OpenApiGenerator;
import org.hl7.fhir.r5.openapi.Writer;
import org.hl7.fhir.r5.renderers.BundleRenderer;
import org.hl7.fhir.r5.renderers.ParametersRenderer;
import org.hl7.fhir.r5.renderers.RendererFactory;
import org.hl7.fhir.r5.renderers.utils.BaseWrappers.ResourceWrapper;
import org.hl7.fhir.r5.renderers.utils.DirectWrappers;
import org.hl7.fhir.r5.renderers.utils.ElementWrappers;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.ITypeParser;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.QuestionnaireRendererMode;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.ResourceRendererMode;
import org.hl7.fhir.r5.renderers.utils.Resolver.IReferenceResolver;
import org.hl7.fhir.r5.renderers.utils.Resolver.ResourceContext;
import org.hl7.fhir.r5.renderers.utils.Resolver.ResourceContextType;
import org.hl7.fhir.r5.renderers.utils.Resolver.ResourceWithReference;
import org.hl7.fhir.r5.terminologies.ValueSetExpander.ValueSetExpansionOutcome;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.r5.utils.FHIRPathEngine;
import org.hl7.fhir.r5.utils.FHIRPathEngine.IEvaluationContext;
import org.hl7.fhir.r5.utils.IGHelper;
import org.hl7.fhir.r5.utils.IResourceValidator;
import org.hl7.fhir.r5.utils.IResourceValidator.IValidationProfileUsageTracker;
import org.hl7.fhir.r5.utils.LiquidEngine;
import org.hl7.fhir.r5.utils.MappingSheetParser;
import org.hl7.fhir.r5.utils.NPMPackageGenerator;
import org.hl7.fhir.r5.utils.NPMPackageGenerator.Category;
import org.hl7.fhir.r5.utils.OperationOutcomeUtilities;
import org.hl7.fhir.r5.utils.ResourceSorters;
import org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities;
import org.hl7.fhir.r5.utils.structuremap.StructureMapAnalysis;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.r5.utils.XVerExtensionManager;
import org.hl7.fhir.r5.utils.client.FHIRToolingClient;
import org.hl7.fhir.utilities.CSFile;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.MarkDownProcessor.Dialect;
import org.hl7.fhir.utilities.MimeType;
import org.hl7.fhir.utilities.TimeTracker.Session;
import org.hl7.fhir.utilities.StandardsStatus;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.TimeTracker;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.ZipGenerator;
import org.hl7.fhir.utilities.json.JSONUtil;
import org.hl7.fhir.utilities.json.JsonTrackingParser;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageHacker;
import org.hl7.fhir.utilities.npm.ToolsVersion;
import org.hl7.fhir.utilities.npm.PackageGenerator.PackageType;
import org.hl7.fhir.utilities.turtle.Turtle;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.hl7.fhir.utilities.validation.ValidationOptions;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;
import org.hl7.fhir.utilities.xml.XMLUtil;
import org.hl7.fhir.validation.codesystem.CodeSystemValidator;
import org.hl7.fhir.validation.instance.InstanceValidator;
import org.hl7.fhir.validation.instance.utils.ValidatorHostContext;
import org.hl7.fhir.validation.profile.ProfileValidator;
import org.w3c.dom.Document;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

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
 *     value set
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
 * @author Grahame Grieve
 */

public class Publisher implements IWorkerContext.ILoggingService, IReferenceResolver, IValidationProfileUsageTracker {

  public class CopyRightUsageStatement {

    private String name;
    private String html;

    public CopyRightUsageStatement(String name, String html) {
      super();
      this.name = name;
      this.html = html;
    }

    public String getSystemName() {
      return name;
    }

    public String getHtml() {
      return html;
    }

  }

  public class JsonDependency {
    private String name;
    private String canonical;
    private String npmId;
    private String version;
    public JsonDependency(String name, String canonical, String npmId, String version) {
      super();
      this.name = name;
      this.canonical = canonical;
      this.npmId = npmId;
      this.version = version;
    }
    public String getName() {
      return name;
    }
    public String getCanonical() {
      return canonical;
    }
    public String getNpmId() {
      return npmId;
    }
    public String getVersion() {
      return version;
    }
    

  }

  public class IGPublisherHostServices implements IEvaluationContext {

    @Override
    public Base resolveConstant(Object appContext, String name, boolean beforeContext) throws PathEngineException {
//      if ("id".equals(name))
        return null;
//      throw new NotImplementedException("Not done yet @ IGPublisherHostServices.resolveConstant("+appContext.toString()+", \""+name+"\")");
    }

    @Override
    public TypeDetails resolveConstantType(Object appContext, String name) throws PathEngineException {
      throw new NotImplementedException("Not done yet (IGPublisherHostServices.resolveConstantType)");
    }

    @Override
    public boolean log(String argument, List<Base> focus) {
      return false;
    }

    @Override
    public FunctionDetails resolveFunction(String functionName) {
      throw new NotImplementedException("Not done yet (IGPublisherHostServices.resolveFunction)");
    }

    @Override
    public TypeDetails checkFunction(Object appContext, String functionName, List<TypeDetails> parameters) throws PathEngineException {
      throw new NotImplementedException("Not done yet (IGPublisherHostServices.checkFunction)");
    }

    @Override
    public List<Base> executeFunction(Object appContext, List<Base> focus, String functionName, List<List<Base>> parameters) {
      throw new NotImplementedException("Not done yet (IGPublisherHostServices.executeFunction)");
    }

    @Override
    public Base resolveReference(Object appContext, String url, Base refContext) {
      if (Utilities.isAbsoluteUrl(url)) {
        if (url.startsWith(igpkp.getCanonical())) {
          url = url.substring(igpkp.getCanonical().length());
          if (url.startsWith("/")) {
            url = url.substring(1);
          }
        } else
          return null;;
      }
      for (FetchedFile f : fileList) {
        for (FetchedResource r : f.getResources()) {
          if (r.getElement() != null && url.equals(r.fhirType()+"/"+r.getId())) {
            return r.getElement();
          }
        }
      }
      return null;
    }

    @Override
    public boolean conformsToProfile(Object appContext, Base item, String url) throws FHIRException {
      IResourceValidator val = context.newValidator();
      List<ValidationMessage> valerrors = new ArrayList<ValidationMessage>();
      if (item instanceof Resource) {
        val.validate(appContext, valerrors, (Resource) item, url);
        boolean ok = true;
        for (ValidationMessage v : valerrors) {
          ok = ok && v.getLevel().isError();
        }
        return ok;
      }
      throw new NotImplementedException("Not done yet (IGPublisherHostServices.conformsToProfile), when item is element");
    }

    @Override
    public ValueSet resolveValueSet(Object appContext, String url) {
      throw new NotImplementedException("Not done yet (IGPublisherHostServices.resolveValueSet)"); // cause I don't know when we 'd need to do this
    }
  }

  public class TypeParserR2 implements ITypeParser {

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
      org.hl7.fhir.dstu2.model.Type t = new org.hl7.fhir.dstu2.formats.XmlParser().parseType(xml, type); 
      return VersionConvertor_10_50.convertType(t);
    }
  }

  public class TypeParserR14 implements ITypeParser {

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
      org.hl7.fhir.dstu2016may.model.Type t = new org.hl7.fhir.dstu2016may.formats.XmlParser().parseType(xml, type); 
      return VersionConvertor_14_50.convertType(t);
    }
  }

  public class TypeParserR3 implements ITypeParser {

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
      org.hl7.fhir.dstu3.model.Type t = new org.hl7.fhir.dstu3.formats.XmlParser().parseType(xml, type); 
      return VersionConvertor_30_50.convertType(t);
    }
  }

  public class TypeParserR4 implements ITypeParser {

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
      org.hl7.fhir.r4.model.Type t = new org.hl7.fhir.r4.formats.XmlParser().parseType(xml, type); 
      return VersionConvertor_40_50.convertType(t);
    }
  }

  public class TypeParserR5 implements ITypeParser {

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
      return new org.hl7.fhir.r5.formats.XmlParser().parseType(xml, type); 
    }
  }



  public enum IGBuildMode { MANUAL, AUTOBUILD, WEBSERVER, PUBLICATION }


  public enum LinkTargetType {

  }


  public enum CacheOption {
    LEAVE, CLEAR_ERRORS, CLEAR_ALL;
  }

  public static final boolean USE_COMMONS_EXEC = true;

  public enum GenerationTool {
    Jekyll
  }

  private static final String IG_NAME = "!ig!";

  private static final String REDIRECT_SOURCE = "<html>\r\n<head>\r\n<meta http-equiv=\"Refresh\" content=\"0; url=site/index.html\"/>\r\n</head>\r\n"+
       "<body>\r\n<p>See here: <a href=\"site/index.html\">this link</a>.</p>\r\n</body>\r\n</html>\r\n";

  private static final long JEKYLL_TIMEOUT = 60000 * 5; // 5 minutes.... 
  private static final long FSH_TIMEOUT = 60000 * 5; // 5 minutes.... 
  public static String txServerProd = "http://tx.fhir.org";
  public static String txServerDev = "http://local.fhir.org:960";
  private static final int PRISM_SIZE_LIMIT = 16384;

  private String consoleLog;
  private String configFile;
  private String sourceDir;
  private String destDir;
  private FHIRToolingClient webTxServer;
  private String txServer;
  private String igPack = "";
  private boolean watch;
  private boolean debug;
  private boolean isChild;
  private boolean cacheVersion;
  private boolean appendTrailingSlashInDataFile;
  private boolean newIg = false;

  private Publisher childPublisher = null;
  private GenerationTool tool;
  private boolean genExampleNarratives = true;

  private List<String> resourceDirs = new ArrayList<String>();
  private List<String> pagesDirs = new ArrayList<String>();
  private String tempDir;
  private String outputDir;
  private String specPath;
  private String qaDir;
  private String version;
  private FhirPublication pubVersion;
  private long fshTimeout = FSH_TIMEOUT;
  private Map<String, String> suppressedMessages = new HashMap<>();

  private String igName;
  private IGBuildMode mode; // for the IG publication infrastructure

  private IFetchFile fetcher = new SimpleFetcher(this);
  private SimpleWorkerContext context; // 
  private InstanceValidator validator;
  private ProfileValidator pvalidator;
  private CodeSystemValidator csvalidator;
  private XVerExtensionManager xverManager;
  private IGKnowledgeProvider igpkp;
  private List<SpecMapManager> specMaps = new ArrayList<SpecMapManager>();
  private boolean firstExecution;

  private Map<String, MappingSpace> mappingSpaces = new HashMap<String, MappingSpace>();
  private Map<ImplementationGuideDefinitionResourceComponent, FetchedFile> fileMap = new HashMap<ImplementationGuideDefinitionResourceComponent, FetchedFile>();
  private Map<String, FetchedFile> altMap = new HashMap<String, FetchedFile>();
  private List<FetchedFile> fileList = new ArrayList<FetchedFile>();
  private List<FetchedFile> changeList = new ArrayList<FetchedFile>();
  private List<String> fileNames = new ArrayList<String>();
  private Map<String, FetchedFile> relativeNames = new HashMap<String, FetchedFile>();
  private Set<String> bndIds = new HashSet<String>();
  private List<Resource> loaded = new ArrayList<Resource>();
  private ImplementationGuide sourceIg;
  private ImplementationGuide publishedIg;
  private List<ValidationMessage> errors = new ArrayList<ValidationMessage>();
  private Calendar execTime = Calendar.getInstance();
  private Set<String> otherFilesStartup = new HashSet<String>();
  private Set<String> otherFilesRun = new HashSet<String>();
  private Set<String> regenList = new HashSet<String>();
  private StringBuilder filelog;
  private Set<String> allOutputs = new HashSet<String>();
  private Set<FetchedResource> examples = new HashSet<FetchedResource>();
  private HashMap<String, FetchedResource> resources = new HashMap<String, FetchedResource>();
  private HashMap<String, ImplementationGuideDefinitionPageComponent> igPages = new HashMap<String, ImplementationGuideDefinitionPageComponent>();
  private List<String> logOptions = new ArrayList<String>();
  private List<String> listedURLExemptions = new ArrayList<String>();
  private String jekyllCommand = "jekyll";
  private boolean makeQA = true;
  private boolean bundleReferencesResolve = true;
  private CqlSubSystem cql;
  private IniFile apiKeyFile;

  private ILoggingService logger = this;

  private HTLMLInspector inspector;

  private List<String> prePagesDirs = new ArrayList<String>();
  private HashMap<String, PreProcessInfo> preProcessInfo = new HashMap<String, PreProcessInfo>();

  private String historyPage;

  private String vsCache;

  private String adHocTmpDir;

  private RenderingContext rc;

  private List<ContactDetail> contacts;
  private List<UsageContext> contexts;
  private List<String> binaryPaths = new ArrayList<>();
  private String copyright;
  private List<CodeableConcept> jurisdictions;
  private SPDXLicense licenseInfo;
  private String publisher;
  private String businessVersion;

  private CacheOption cacheOption;

  private String configFileRootPath;

  private MarkDownProcessor markdownEngine;
  private List<ValueSet> expansions = new ArrayList<>();

  private String npmName;

  private NPMPackageGenerator npm;

  private FilesystemPackageCacheManager pcm;

  private TemplateManager templateManager;

  private String rootDir;
  private String templatePck;
  
  private boolean templateLoaded ;

  private String packagesFolder;
  private String targetOutput;
  private String targetOutputNested;

  private String folderToDelete;

  private String specifiedVersion;

  private NpmPackage packge;

  private String txLog;

  private boolean includeHeadings;
  private String openApiTemplate;
  private Collection<String> extraTemplateList = new ArrayList<String>(); // List of templates in order they should appear when navigating next/prev
  private Map<String, String> extraTemplates = new HashMap<String, String>();
  private Collection<String> historyTemplates = new ArrayList<String>(); // What templates should only be turned on if there's history
  private Collection<String> exampleTemplates = new ArrayList<String>(); // What templates should only be turned on if there are examples
  private String license;
  private String htmlTemplate;
  private String mdTemplate;
  private boolean brokenLinksError;
  private String nestedIgConfig;
  private String igArtifactsPage;
  private String nestedIgOutput;
  private boolean genExamples;
  private boolean doTransforms;
  private boolean allInvariants = true;
  private List<String> spreadsheets = new ArrayList<>();
  private List<String> bundles = new ArrayList<>();
  private List<String> mappings = new ArrayList<>();
  private List<String> generateVersions = new ArrayList<>();
  private RealmBusinessRules realmRules;
  private PreviousVersionComparator previousVersionComparator;

  private IGPublisherLiquidTemplateServices templateProvider;

  private List<NpmPackage> npmList = new ArrayList<>();

  private String repoRoot;

  private ValidationServices validationFetcher;
  
  private Template template;

  private boolean igMode;

  private boolean isBuildingTemplate;
  private JsonObject templateInfo;
  private ExtensionTracker extensionTracker;

  private String currVer;

  private List<String> codeSystemProps = new ArrayList<>();

  private List<JsonDependency> jsonDependencies = new ArrayList<>();

  private Coding expectedJurisdiction;

  private boolean noFSH;

  private Set<String> loadedIds;

  private boolean duplicateInputResourcesDetected;

  private List<String> comparisonVersions;

  private TimeTracker tt;

  private boolean publishing = false;

  private String igrealm;

  private String copyrightYear;

  
  private class PreProcessInfo {
    private String xsltName;
    private byte[] xslt;
    private String relativePath;
    public PreProcessInfo(String xsltName, String relativePath) throws IOException {
      this.xsltName = xsltName;
      if (xsltName!=null) {
        this.xslt = TextFile.fileToBytes(xsltName);
      }
      this.relativePath = relativePath;
    }
    public boolean hasXslt() {
      return xslt!=null;
    }
    public byte[] getXslt() {
      return xslt;
    }
    public boolean hasRelativePath() {
      return relativePath != null && !relativePath.isEmpty();
    }
    public String getRelativePath() {
      return relativePath;
    }
  }

  public void execute() throws Exception {
    tt = new TimeTracker();
    initialize();
    if (isBuildingTemplate) {
      packageTemplate();
    } else {
      log("Load Content");
      try {
        createIg();
      } catch (Exception e) {
        recordOutcome(e, null);
        throw e;
      }

      if (watch) {
        firstExecution = false;
        log("Watching for changes on a 5sec cycle");
        while (watch) { // terminated externally
          Thread.sleep(5000);
          if (load()) {
            log("Processing changes to "+Integer.toString(changeList.size())+(changeList.size() == 1 ? " file" : " files")+" @ "+genTime());
            long startTime = System.nanoTime();
            loadConformance();
            generateNarratives();
            checkDependencies();
            validate();
            generate();
            clean();
            long endTime = System.nanoTime();
            processTxLog(Utilities.path(destDir != null ? destDir : outputDir, "qa-tx.html"));
            BallotChecker bc = new BallotChecker(repoRoot);
            ValidationPresenter val = new ValidationPresenter(version, workingVersion(), igpkp, childPublisher == null? null : childPublisher.getIgpkp(), outputDir, npmName, childPublisher == null? null : childPublisher.npmName, 
                bc.check(igpkp.getCanonical(), npmName, workingVersion(), historyPage, version), IGVersionUtil.getVersion(), fetchCurrentIGPubVersion(), realmRules, previousVersionComparator,
                new DependencyRenderer(pcm, outputDir, npmName, templateManager).render(publishedIg), new HTAAnalysisRenderer(context, outputDir, markdownEngine).render(publishedIg.getPackageId(), fileList, publishedIg.present()), 
                new VersionCheckRenderer(npm.version(), publishedIg.getVersion(), bc.getPackageList(), igpkp.getCanonical()).generate(), copyrightYear);
            log("Finished. "+Utilities.presentDuration(endTime - startTime)+". Validation output in "+val.generate(sourceIg.getName(), errors, fileList, Utilities.path(destDir != null ? destDir : outputDir, "qa.html"), suppressedMessages));
            recordOutcome(null, val);
          }
        }
      } else {
        log("Done"+(!publishing && mode != IGBuildMode.AUTOBUILD ? ". This IG has been built using the 'normal' process for local use. If building to host on an an external website, use the process documented [yet to be documented]])" : ""));
      }
    }
    if (templateLoaded && new File(rootDir).exists()) {
      Utilities.clearDirectory(Utilities.path(rootDir, "template"));
    }
    if (folderToDelete != null) {
      try {
        Utilities.clearDirectory(folderToDelete);
        new File(folderToDelete).delete();
      } catch (Throwable e) {
        // nothing
      }
    }
  }

  private void packageTemplate() throws IOException {
    Utilities.createDirectory(outputDir);
    long startTime = System.nanoTime();
    JsonObject qaJson = new JsonObject();
    qaJson.addProperty("url", templateInfo.get("canonical").getAsString());
    qaJson.addProperty("package-id", templateInfo.get("name").getAsString());
    qaJson.addProperty("ig-ver", templateInfo.get("version").getAsString());
    qaJson.addProperty("date", new SimpleDateFormat("EEE, dd MMM, yyyy HH:mm:ss Z", new Locale("en", "US")).format(execTime.getTime()));
    qaJson.addProperty("version", Constants.VERSION);
    qaJson.addProperty("tool", Constants.VERSION+" ("+ToolsVersion.TOOLS_VERSION+")");
    try {
      File od = new File(outputDir);
      FileUtils.cleanDirectory(od);
      npm = new NPMPackageGenerator(Utilities.path(outputDir, "package.tgz"), templateInfo, execTime.getTime(), !publishing);
      npm.loadFiles(rootDir, new File(rootDir), ".git", "output", "package");
      npm.finish();

      TextFile.stringToFile(makeTemplateIndexPage(), Utilities.path(outputDir, "index.html"), false);
      TextFile.stringToFile(makeTemplateJekyllIndexPage(), Utilities.path(outputDir, "jekyll.html"), false);
      TextFile.stringToFile(makeTemplateQAPage(), Utilities.path(outputDir, "qa.html"), false);
      
      if (mode != IGBuildMode.AUTOBUILD) {
        pcm.addPackageToCache(templateInfo.get("name").getAsString(), templateInfo.get("version").getAsString(), new FileInputStream(npm.filename()), Utilities.path(outputDir, "package.tgz"));
        pcm.addPackageToCache(templateInfo.get("name").getAsString(), "dev", new FileInputStream(npm.filename()), Utilities.path(outputDir, "package.tgz"));
      }
    } catch (Exception e) {
       e.printStackTrace();
       qaJson.addProperty("exception", e.getMessage());
    }
    long endTime = System.nanoTime();
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    String json = gson.toJson(qaJson);
    TextFile.stringToFile(json, Utilities.path(outputDir, "qa.json"), false);

    ZipGenerator zip = new ZipGenerator(Utilities.path(tempDir, "full-ig.zip"));
    zip.addFolder(outputDir, "site/", false);
    zip.addFileSource("index.html", REDIRECT_SOURCE, false);
    zip.close();
    Utilities.copyFile(Utilities.path(tempDir, "full-ig.zip"), Utilities.path(outputDir, "full-ig.zip"));

    // registering the package locally
    log("Finished. "+Utilities.presentDuration(endTime - startTime)+". Output in "+outputDir);
  }


  private String makeTemplateIndexPage() {
    String page = "<!DOCTYPE HTML>\r\n"+
       "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\r\n"+
       "<head>\r\n"+
       "  <title>Template Page</title>\r\n"+
       "</head>\r\n"+
       "<body>\r\n"+
       "  <p><b>Template {{npm}}</b></p>\r\n"+
       "  <p>You can <a href=\"package.tgz\">download the template</a>, though you should not need to; just refer to the template as {{npm}} in your IG configuration.</p>\r\n"+
       "  <p>A <a href=\"{{canonical}}/history.html\">full version history is published</a></p>\r\n"+
       "</body>\r\n"+
       "</html>\r\n";
    return page.replace("{{npm}}", templateInfo.get("name").getAsString()).replace("{{canonical}}", templateInfo.get("canonical").getAsString());
  }

  private String makeTemplateJekyllIndexPage() {
    String page = "---"+
       "layout: page"+
       "title: {{npm}}"+
       "---"+
       "  <p><b>Template {{npm}}</b></p>\r\n"+
       "  <p>You can <a href=\"package.tgz\">download the template</a>, though you should not need to; just refer to the template as {{npm}} in your IG configuration.</p>\r\n"+
       "  <p>A <a href=\"{{canonical}}/history.html\">full version history is published</a></p>\r\n";
    return page.replace("{{npm}}", templateInfo.get("name").getAsString()).replace("{{canonical}}", templateInfo.get("canonical").getAsString());
  }

  private String makeTemplateQAPage() {
    String page = "<!DOCTYPE HTML><html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\"><head><title>Template QA Page</title></head><body><p><b>Template {{npm}}</b></p><p>You  can <a href=\"package.tgz\">download the template</a>, though you should not need to; just refer to the template as {{npm}} in your IG configuration.</p></body></html>";
    return page.replace("{{npm}}", templateInfo.get("name").getAsString());
  }

  private void processTxLog(String path) throws FileNotFoundException, IOException {
    if (txLog != null) {
      String tx = TextFile.fileToString(txLog);
      PrintStream f = new PrintStream(new FileOutputStream(path));
      String title = "Terminology Server Log";
      f.println("<html><head><title>"+title+"</title></head><body><h2>"+title+"</h2>");
      f.print(tx);
      f.println("</head></html>");
      f.close();
    } 
  }


  public void createIg() throws Exception, IOException, EOperationOutcome, FHIRException {
    try {
      TimeTracker.Session tts = tt.start("loading");
      load();
      tts.end();

      tts = tt.start("generate");
      log("Processing Conformance Resources");
      loadConformance();
      log("Validating Resources");
      try {
        validate();
      } catch (Exception ex){
        log("Unhandled Exception: " +ex.toString());
        throw(ex);
      }
      log("Processing Provenance Records");
      processProvenanceDetails();
      log("Generating Outputs in "+outputDir);
      generate();
      clean();
      BallotChecker bc = new BallotChecker(repoRoot);
      ValidationPresenter val = new ValidationPresenter(version, workingVersion(), igpkp, childPublisher == null? null : childPublisher.getIgpkp(), outputDir, npmName, childPublisher == null? null : childPublisher.npmName, 
          bc.check(igpkp.getCanonical(), npmName, workingVersion(), historyPage, version), IGVersionUtil.getVersion(), fetchCurrentIGPubVersion(), realmRules, previousVersionComparator,
          new DependencyRenderer(pcm, outputDir, npmName, templateManager).render(publishedIg), new HTAAnalysisRenderer(context, outputDir, markdownEngine).render(publishedIg.getPackageId(), fileList, publishedIg.present()), 
          new VersionCheckRenderer(npm.version(), publishedIg.getVersion(), bc.getPackageList(), igpkp.getCanonical()).generate(), copyrightYear);
      tts.end();
      if (isChild()) {
        log("Finished. "+tt.report());      
      } else {
        processTxLog(Utilities.path(destDir != null ? destDir : outputDir, "qa-tx.html"));
        log("Finished. "+tt.report());      
        log("Validation output in "+val.generate(sourceIg.getName(), errors, fileList, Utilities.path(destDir != null ? destDir : outputDir, "qa.html"), suppressedMessages));
      }
      recordOutcome(null, val);
    } catch (Exception e) {
      try {
        recordOutcome(e, null);        
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      throw e;
    }
  }

  private void processProvenanceDetails() throws Exception {
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (!r.isExample()) {
          if (r.fhirType().equals("Provenance")) { 
            logDebugMessage(LogCategory.PROGRESS, "Process Provenance "+f.getName()+" : "+r.getId());
            if (processProvenance(igpkp.getLinkFor(r, true), r.getElement(), r.getResource()))
              r.setProvenance(true);
          } else if (r.fhirType().equals("Bundle")) {
            if (processProvenanceEntries(f, r))
              r.setProvenance(true);              
          }
        }
      }
    }    
  }

  public boolean processProvenanceEntries(FetchedFile f, FetchedResource r) throws Exception {
    boolean isHistory = true;
    Bundle b = (Bundle) r.getResource();
    List<Element> entries = r.getElement().getChildrenByName("entry");
    for (int i = 0; i < entries.size(); i++) {
      Element entry = entries.get(i);
      Element res = entry.getNamedChild("resource");
      if (res != null && "Provenance".equals(res.fhirType())) {
        logDebugMessage(LogCategory.PROGRESS, "Process Provenance "+f.getName()+" : "+r.getId()+".entry["+i+"]");
        if (processProvenance(igpkp.getLinkFor(r, true), res, b == null ? null : b.getEntry().get(i).getResource()))
          isHistory = true;
      }
    }
    return isHistory;
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
        p = igpkp.getLinkFor(target, true);
        d = target.getTitle() != null ? target.getTitle() : entity.hasDisplay() ? entity.getDisplay() : ref;
      }
      pd.getTargets().add(pd.new ProvenanceDetailsTarget(p, d));
    }    
    return pd;    
  }
  
  private boolean processProvenance(String path, Element resource, Resource r) {
    boolean containsHistory = false;
    Provenance pv = null;
    try {
      pv = (Provenance) (r == null ? convertFromElement(resource) : r);
      RendererFactory.factory(pv, rc.setParser(getTypeLoader(null))).render(pv);
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

  private ProvenanceDetails processProvenance(String path, Provenance pv) {
    ProvenanceDetails res = new ProvenanceDetails();
    res.setPath(path);
    res.setAction(pv.getActivity().getCodingFirstRep());
    res.setDate(pv.hasOccurredPeriod() ? pv.getOccurredPeriod().getEndElement() : pv.hasOccurredDateTimeType() ? pv.getOccurredDateTimeType() : pv.getRecordedElement());
    if (pv.hasReason()) {
      res.setComment(pv.getReasonFirstRep().getText());
    }
    for (ProvenanceAgentComponent agent : pv.getAgent()) {
      for (Coding c : agent.getType().getCoding()) {
        res.getActors().put(c, agent.getWho());
      }
    }
    return res;
  }

  private int chooseType(String[] ref) {
    int res = -1;
    for (int i = 0; i < ref.length-1; i++) { // note -1 - don't bother checking the last value, which might also be a resource name (e.g StructureDefinition/XXX)
      if (context.getResourceNames().contains(ref[i])) {
        res = i;
      }
    }
    return res;
  }

  private void recordOutcome(Exception ex, ValidationPresenter val) {
    try {
      JsonObject j = new JsonObject();
      if (sourceIg != null) {
        j.addProperty("url", sourceIg.getUrl());
        j.addProperty("name", sourceIg.getName());
      }
      if (publishedIg != null && publishedIg.hasPackageId()) {
        j.addProperty("package-id", publishedIg.getPackageId());
        j.addProperty("ig-ver", publishedIg.getVersion());
      }
      j.addProperty("date", new SimpleDateFormat("EEE, dd MMM, yyyy HH:mm:ss Z", new Locale("en", "US")).format(execTime.getTime()));
      if (val != null) {
        j.addProperty("errs", val.getErr());
        j.addProperty("warnings", val.getWarn());
        j.addProperty("hints", val.getInfo());
      }
      if (ex != null) {
        j.addProperty("exception", ex.getMessage());
      }
      j.addProperty("version", version);
      if (templatePck != null) {
        j.addProperty("template", templatePck);
      }
      j.addProperty("tool", Constants.VERSION+" ("+ToolsVersion.TOOLS_VERSION+")");
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      String json = gson.toJson(j);
      TextFile.stringToFile(json, Utilities.path(destDir != null ? destDir : outputDir, "qa.json"), false);
    } catch (Exception e) {
      // nothing at all
    }
  }
  
  public CacheOption getCacheOption() {
    return cacheOption;
  }


  public void setCacheOption(CacheOption cacheOption) {
    this.cacheOption = cacheOption;
  }


  @Override
  public ResourceWithReference resolve(RenderingContext context, String url) {
    if (url == null) {
      return null;
    }
    
    String[] parts = url.split("\\/");
    if (parts.length >= 2 && !Utilities.startsWithInList(url, "urn:uuid:", "urn:oid:", "cid:")) {
      for (FetchedFile f : fileList) {
        for (FetchedResource r : f.getResources()) {
          if (r.getElement() != null && r.fhirType().equals(parts[0]) && r.getId().equals(parts[1])) {
            String path = igpkp.getLinkFor(r, true);
            return new ResourceWithReference(path, new ElementWrappers.ResourceWrapperMetaElement(context, r.getElement()));
          }
          if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
            if (url.equals(((CanonicalResource) r.getResource()).getUrl())) {
              String path = igpkp.getLinkFor(r, true);
              return new ResourceWithReference(path, new DirectWrappers.ResourceWrapperDirect(context, r.getResource()));            
            }
          }
        }
      }
      for (FetchedFile f : fileList) {
        for (FetchedResource r : f.getResources()) {
          if (r.fhirType().equals("Bundle")) {
            List<Element> entries = r.getElement().getChildrenByName("entry");
            for (Element entry : entries) {
              Element res = entry.getNamedChild("resource");
              if (res != null && res.fhirType().equals(parts[0]) && res.hasChild("id") && res.getNamedChildValue("id").equals(parts[1])) {
                String path = igpkp.getLinkFor(r, true)+"#"+parts[0]+"_"+parts[1];
                return new ResourceWithReference(path, new ElementWrappers.ResourceWrapperMetaElement(context, r.getElement()));
              }
            }
          }
        }
      }
    }
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("Bundle")) {
          List<Element> entries = r.getElement().getChildrenByName("entry");
          for (Element entry : entries) {
            Element res = entry.getNamedChild("resource");
            String fu = entry.getNamedChildValue("fullUrl");
            if (res != null && fu != null && fu.equals(url)) {
              String path = igpkp.getLinkFor(r, true)+"#"+fu.replace(":", "-");
              return new ResourceWithReference(path, new ElementWrappers.ResourceWrapperMetaElement(context, r.getElement()));
            }
          }
        }
      }
    }
    
    for (SpecMapManager sp : specMaps) {
      String fp = sp.getBase()+"/"+url;
      String path;
      try {
        path = sp.getPath(fp, null);
      } catch (Exception e) {
        path = null;
      }
      if (path != null)
        return new ResourceWithReference(path, null);
    }
    return null;

  }

  private void generateNarratives() throws Exception {
    Session tts = tt.start("narrative generation");
    logDebugMessage(LogCategory.PROGRESS, "gen narratives");
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {        
        if (r.getExampleUri()==null || genExampleNarratives) {
          logDebugMessage(LogCategory.PROGRESS, "narrative for "+f.getName()+" : "+r.getId());
          if (r.getResource() != null && isConvertableResource(r.getResource().fhirType())) {
            boolean regen = false;
            rc.setDefinitionsTarget(igpkp.getDefinitionsName(r));
            if (r.getResource() instanceof DomainResource && !(((DomainResource) r.getResource()).hasText() && ((DomainResource) r.getResource()).getText().hasDiv())) {
              regen = true;
              RendererFactory.factory(r.getResource(), rc).render((DomainResource) r.getResource());
            } else if (r.getResource() instanceof Bundle) {
              regen = true;
              new BundleRenderer(rc).render((Bundle) r.getResource());
            } else if (r.getResource() instanceof Parameters) {
              regen = true;
              Parameters p = (Parameters) r.getResource();
              new ParametersRenderer(rc, new ResourceContext(ResourceContextType.PARAMETERS , p, null)).render(p);
            } else if (r.getResource() instanceof DomainResource) {
              checkExistingNarrative(f, r, ((DomainResource) r.getResource()).getText().getDiv());
            }
            if (regen)
              r.setElement(convertToElement(r.getResource()));
          } else {
            RenderingContext lrc = rc.copy().setParser(getTypeLoader(f,r));
            if ("http://hl7.org/fhir/StructureDefinition/DomainResource".equals(r.getElement().getProperty().getStructure().getBaseDefinition()) && !hasNarrative(r.getElement())) {
              ResourceWrapper rw = new ElementWrappers.ResourceWrapperMetaElement(lrc, r.getElement());
              RendererFactory.factory(rw, lrc).render(rw);
            } else if (r.fhirType().equals("Bundle")) {
              for (Element e : r.getElement().getChildrenByName("entry")) {
                Element res = e.getNamedChild("resource");
                if (res!=null && "http://hl7.org/fhir/StructureDefinition/DomainResource".equals(res.getProperty().getStructure().getBaseDefinition()) && !hasNarrative(res)) {
                  ResourceWrapper rw = new ElementWrappers.ResourceWrapperMetaElement(lrc, res);
                  RendererFactory.factory(rw, lrc, new ResourceContext(ResourceContextType.BUNDLE, r.getElement(), res)).render(rw);
                }
              }
            } else if ("http://hl7.org/fhir/StructureDefinition/DomainResource".equals(r.getElement().getProperty().getStructure().getBaseDefinition()) && hasNarrative(r.getElement())) {
              checkExistingNarrative(f, r, r.getElement().getNamedChild("text").getNamedChild("div").getXhtml());
            }
          }
        } else {
          logDebugMessage(LogCategory.PROGRESS, "skipped narrative for "+f.getName()+" : "+r.getId());
        }
      }
    }
    tts.end();
  }

  private void checkExistingNarrative(FetchedFile f, FetchedResource r, XhtmlNode xhtml) {
    if (xhtml != null) {
      boolean hasGenNarrative = scanForGeneratedNarrative(xhtml);
      if (hasGenNarrative) {
        f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, r.fhirType()+".text.div", "Resource has provided narrative, but the narrative indicates that it is generated - remove the narrative or fix it up", IssueSeverity.ERROR));
        r.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, r.fhirType()+".text.div", "Resource has provided narrative, but the narrative indicates that it is generated - remove the narrative or fix it up", IssueSeverity.ERROR));
      }
    }
  }

  private boolean scanForGeneratedNarrative(XhtmlNode x) {
    if (x.getContent() != null && x.getContent().contains("Generated Narrative")) {
      return true;
    }
    for (XhtmlNode c : x.getChildNodes()) {
      if (scanForGeneratedNarrative(c)) {
        return true;
      }
    }
    return false;
  }

  private boolean isConvertableResource(String t) {
    return Utilities.existsInList(t, "StructureDefinition", "ValueSet", "CodeSystem", "Conformance", "CapabilityStatement", "Questionnaire", "NamingSystem", 
        "ConceptMap", "OperationOutcome", "CompartmentDefinition", "OperationDefinition", "ImplementationGuide");
  }


  private ITypeParser getTypeLoader(FetchedFile f, FetchedResource r) throws Exception {
    String ver = r.getConfig() == null ? null : ostr(r.getConfig(), "version");
    return getTypeLoader(ver);
  }
  
  private ITypeParser getTypeLoader(String ver) throws Exception {
    if (ver == null)
      ver = version; // fall back to global version
    if (VersionUtilities.isR3Ver(ver)) {
      return new TypeParserR3();
    } else if (VersionUtilities.isR4Ver(ver)) {
      return new TypeParserR4();
    } else if (VersionUtilities.isR2BVer(ver)) {
      return new TypeParserR14();
    } else if (VersionUtilities.isR2Ver(ver)) {
      return new TypeParserR2();
    } else if (ver.equals(Constants.VERSION)) {
      return new TypeParserR5();
    } else
      throw new FHIRException("Unsupported version "+ver);
  }

  private boolean hasNarrative(Element element) {
    return element.hasChild("text") && element.getNamedChild("text").hasChild("div");
  }

  private void clean() throws Exception {
    for (Resource r : loaded) {
      context.dropResource(r);
    }
    if (destDir != null) {
      if (!(new File(destDir).exists()))
        Utilities.createDirectory(destDir);
      Utilities.copyDirectory(outputDir, destDir, null);
    }
  }

  private String genTime() {
    return new SimpleDateFormat("EEE, MMM d, yyyy HH:mmZ", new Locale("en", "US")).format(execTime.getTime());
  }

  private String genDate() {
    return new SimpleDateFormat("dd/MM/yyyy", new Locale("en", "US")).format(execTime.getTime());
  }

  private void checkDependencies() {
    // first, we load all the direct dependency lists
    for (FetchedFile f : fileList) {
      if (f.getDependencies() == null) {
        loadDependencyList(f);
      }
    }

    // now, we keep adding to the change list till there's no change
    boolean changed;
    do {
      changed = false;
      for (FetchedFile f : fileList) {
        if (!changeList.contains(f)) {
          boolean dep = false;
          for (FetchedFile d : f.getDependencies())
            if (changeList.contains(d))
              dep = true;
          if (dep) {
            changeList.add(f);
            changed = true;
          }
        }
      }
    } while (changed);
  }

  private void loadDependencyList(FetchedFile f) {
    f.setDependencies(new ArrayList<FetchedFile>());
    for (FetchedResource r : f.getResources()) {
      if (r.fhirType().equals("ValueSet"))
        loadValueSetDependencies(f, r);
      else if (r.fhirType().equals("StructureDefinition"))
        loadProfileDependencies(f, r);
      else
        ; // all other resource types don't have dependencies that we care about for rendering purposes
    }
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

  private FetchedFile getFileForFile(String path) {
    for (FetchedFile f : fileList) {
      if (f.getPath().equals(path))
        return f;
    }
    return null;
  }

  
  private FetchedFile getFileForUri(String uri) {
    for (FetchedFile f : fileList) {
      if (getResourceForUri(f, uri) != null)
        return f;
    }
    return null;
  }

  private FetchedResource getResourceForUri(FetchedFile f, String uri) {
    for (FetchedResource r : f.getResources()) {
      if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
        CanonicalResource bc = (CanonicalResource) r.getResource();
        if (bc.getUrl() != null && bc.getUrl().equals(uri))
          return r;
      }
    }
    return null;
  }

  private FetchedResource getResourceForRef(FetchedFile f, String ref) {
    for (FetchedResource r : f.getResources()) {
      if ((r.fhirType()+"/"+r.getId()).equals(ref))
        return r;
    }
    for (FetchedFile f1 : fileList) {
      for (FetchedResource r : f1.getResources()) {
        if ((r.fhirType()+"/"+r.getId()).equals(ref))
          return r;
        if (r.getResource() != null && r.getResource() instanceof CanonicalResource && ((CanonicalResource) r.getResource()).getUrl().equals(ref))
          return r;
      }
    }
    
    return null;
  }

  private FetchedResource getResourceForUri(String uri) {
    for (FetchedFile f : fileList) {
      FetchedResource r = getResourceForUri(f, uri);
      if (r != null)
        return r;
    }
    return null;
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
    JsonPrimitive p = (JsonPrimitive) obj.get(name);
    if (p.isBoolean())
      return p.getAsBoolean();
    return false;
  }
  
  private String str(JsonObject obj, String name) throws Exception {
    if (!obj.has(name))
      throw new Exception("Property '"+name+"' not found");
    if (!(obj.get(name) instanceof JsonPrimitive))
      throw new Exception("Property '"+name+"' not a primitive");
    JsonPrimitive p = (JsonPrimitive) obj.get(name);
    if (!p.isString())
      throw new Exception("Property '"+name+"' not a string");
    return p.getAsString();
  }

  private String ostr(JsonObject obj, String name) throws Exception {
    if (obj == null)
      return null;
    if (!obj.has(name))
      return null;
    if (!(obj.get(name) instanceof JsonPrimitive))
      return null;
    JsonPrimitive p = (JsonPrimitive) obj.get(name);
    if (p.isBoolean())
      return p.getAsString();
    if (!p.isString())
      return null;
    return p.getAsString();
  }

  private String str(JsonObject obj, String name, String defValue) throws Exception {
    if (obj == null || !obj.has(name))
      return defValue;
    if (!(obj.get(name) instanceof JsonPrimitive))
      throw new Exception("Property "+name+" not a primitive");
    JsonPrimitive p = (JsonPrimitive) obj.get(name);
    if (!p.isString())
      throw new Exception("Property "+name+" not a string");
    return p.getAsString();
  }

  public void initialize() throws Exception {
    firstExecution = true;
    pcm = new FilesystemPackageCacheManager(mode == null || mode == IGBuildMode.MANUAL || mode == IGBuildMode.PUBLICATION, ToolsVersion.TOOLS_VERSION);
    if (mode == IGBuildMode.PUBLICATION)
      log("Build Formal Publication package, intended for "+getTargetOutput());
    
    if (apiKeyFile == null) {
      apiKeyFile = new IniFile(Utilities.path(System.getProperty("user.home"), "apikeys.ini"));
    }
    log("API keys loaded from "+apiKeyFile.getFileName());
    templateManager = new TemplateManager(pcm, logger, gh());
    templateProvider = new IGPublisherLiquidTemplateServices();
    extensionTracker = new ExtensionTracker();
    log("Package Cache: "+pcm.getFolder());
    if (packagesFolder != null) {
      log("Also loading Packages from "+packagesFolder);
      pcm.loadFromFolder(packagesFolder);
    }
    fetcher.setRootDir(rootDir);
    fetcher.setResourceDirs(resourceDirs);
    if (configFile != null && focusDir().contains(" ")) {
      throw new Error("There is a space in the folder path: \""+focusDir()+"\". Please fix your directory arrangement to remove the space and try again");
    }
    if (configFile != null) {
      File fsh = new File(Utilities.path(focusDir(), "fsh"));
      if (fsh.exists() && fsh.isDirectory() && !noFSH) {
        runFsh(new File(Utilities.getDirectoryForFile(fsh.getAbsolutePath())));
      } else {
        File fsh2 = new File(Utilities.path(focusDir(), "input", "fsh"));
        if (fsh2.exists() && fsh2.isDirectory() && !noFSH) {
          runFsh(new File(Utilities.getDirectoryForFile(fsh.getAbsolutePath())));   
        }
      }
    }
    IniFile ini = checkNewIg();
    if (ini != null) {
      newIg = true;
      initializeFromIg(ini);   
    } else if (isTemplate())
      initializeTemplate();
    else
      initializeFromJson();
    expectedJurisdiction = checkForJurisdiction();
    realmRules = makeRealmBusinessRules();
    previousVersionComparator = makePreviousVersionComparator();
  }
  
  private Coding checkForJurisdiction() {
    String id = npmName;
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
    if (Utilities.existsInList(parts[1], "fhir") && !Utilities.existsInList(parts[1], "nothing-yet")) {
      if (parts[2].equals("uv")) {
        igrealm = "uv";
        return new Coding("http://unstats.un.org/unsd/methods/m49/m49.htm", "001", "World");
      } else {
        igrealm = parts[2];
        return new Coding("urn:iso:std:iso:3166", parts[2].toUpperCase(), null);
      }
    } else {
      return null;
    }
  }

  private String focusDir() {
    String dir = configFile.endsWith("ig.ini") ? configFile.substring(0, configFile.length()-6) : configFile;
    if (dir.endsWith(File.separatorChar+".")) {
      dir = dir.substring(0, dir.length()-2);
    }
    return Utilities.noString(dir) ? getCurentDirectory() : dir;
  }

  public class MySushiHandler extends OutputStream {

    private byte[] buffer;
    private int length;
    private int errorCount = -1;

    public MySushiHandler() {
      buffer = new byte[256];
    }

    public String getBufferString() {
      return new String(this.buffer, 0, length);
    }

    private boolean passSushiFilter(String s) {
      if (Utilities.noString(s))
        return false;
      return true;
    }

    @Override
    public void write(int b) throws IOException {
      buffer[length] = (byte) b;
      length++;
      if (b == 10) { // eoln
        String s = new String(buffer, 0, length);
        if (passSushiFilter(s)) {
          log("Sushi: "+s.trim());
          if (s.trim().startsWith("Errors:")) {
            errorCount = Integer.parseInt(s.substring(10).trim());
          }
        }
        length = 0;
      }
    }
  }

  private void runFsh(File file) throws IOException { 
    File inif = new File(Utilities.path(file.getAbsolutePath(), "fsh.ini"));
    if (!inif.exists()) {
      inif = new File(Utilities.path(Utilities.getDirectoryForFile(file.getAbsolutePath()), "fsh.ini"));
    }
    String fshVersion = null;
    if (inif.exists()) {
      IniFile ini = new IniFile(new FileInputStream(inif));
      if (ini.hasProperty("FSH", "timeout")) {
        fshTimeout = ini.getLongProperty("FSH", "timeout") * 1000;
      }
      if (ini.hasProperty("FSH", "sushi-version")) {
        fshVersion = ini.getStringProperty("FSH", "sushi-version");
      }
    }
    log("Run Sushi on "+file.getAbsolutePath());
    DefaultExecutor exec = new DefaultExecutor();
    exec.setExitValue(0);
    MySushiHandler pumpHandler = new MySushiHandler();
    PumpStreamHandler pump = new PumpStreamHandler(pumpHandler);
    exec.setStreamHandler(pump);
    exec.setWorkingDirectory(file);
    ExecuteWatchdog watchdog = new ExecuteWatchdog(fshTimeout);
    exec.setWatchdog(watchdog);
    String cmd = fshVersion == null ? "sushi" : "npx fsh-sushi@"+fshVersion;
    try {
      if (SystemUtils.IS_OS_WINDOWS) {
        exec.execute(org.apache.commons.exec.CommandLine.parse("cmd /C "+cmd+" . -o ."));
      } else {
        exec.execute(org.apache.commons.exec.CommandLine.parse(cmd+" . -o ."));
      }
    } catch (IOException ioex) {
      log("Sushi couldn't be run. Complete output from running Sushi : " + pumpHandler.getBufferString());
      log("Note: Check that Sushi is installed correctly (\"npm install -g fsh-sushi\". On windows, get npm from https://www.npmjs.com/get-npm)");
      log("Exception: "+ioex.getMessage());
      throw ioex;
    }    
    if (pumpHandler.errorCount > 0) {
      throw new IOException("Sushi failed with errors. Complete output from running Sushi : " + pumpHandler.getBufferString());
    }
  }


  private void initializeTemplate() throws IOException {
    rootDir = configFile;
    outputDir = Utilities.path(rootDir, "output");
  }


  private boolean isTemplate() throws IOException {
    File pack = new File(Utilities.path(configFile, "package", "package.json"));
    if (pack.exists()) {
      JsonObject json = JsonTrackingParser.parseJson(pack);
      if (json.has("type") && "fhir.template".equals(json.get("type").getAsString())) {
        isBuildingTemplate = true;
        templateInfo = json;
        npmName = json.get("name").getAsString();
        System.out.println("targetOutput: "+targetOutput);
        return true;
      }
    }
    
    return false;
  }
  
  private IniFile checkNewIg() throws IOException {
    if (configFile == null)
      return null;
    if (configFile.endsWith(File.separatorChar+".")) {
      configFile = configFile.substring(0, configFile.length()-2);
    }
    File cf = mode == IGBuildMode.AUTOBUILD ? new File(configFile) : new CSFile(configFile);
    if (!cf.exists())
      return null;
    if (cf.isDirectory())
      cf = mode == IGBuildMode.AUTOBUILD ? new File(Utilities.path(configFile, "ig.ini")) : new CSFile(Utilities.path(configFile, "ig.ini"));
    if (!cf.exists())
      return null;
    String s = TextFile.fileToString(cf);
    if (s.startsWith("[IG]"))
        return new IniFile(cf.getAbsolutePath());
    else
      return null;
  }


  private void initializeFromIg(IniFile ini) throws Exception {
    configFile = ini.getFileName();
    igMode = true;
    repoRoot = Utilities.getDirectoryForFile(ini.getFileName());
    rootDir = repoRoot;
    // ok, first we load the template
    String templateName = ini.getStringProperty("IG", "template");
    if (templateName == null)
      throw new Exception("You must nominate a template - consult the IG Publisher documentation");
    igName = Utilities.path(repoRoot, ini.getStringProperty("IG", "ig"));
    try {
      try {
        sourceIg = (ImplementationGuide) org.hl7.fhir.r5.formats.FormatUtilities.loadFile(igName);
      } catch (Exception e) {
        sourceIg = (ImplementationGuide) VersionConvertor_40_50.convertResource(FormatUtilities.loadFile(igName));
      }
    } catch (Exception e) {
      throw new Exception("Error Parsing File "+igName+": "+e.getMessage(), e);
    }
    template = templateManager.loadTemplate(templateName, rootDir, sourceIg.getPackageId(), mode == IGBuildMode.AUTOBUILD);

    if (template.hasExtraTemplates()) {
      processExtraTemplates(template.getExtraTemplates());
    }
    
    if (template.hasPreProcess()) {
      for (JsonElement e : template.getPreProcess()) {
        handlePreProcess((JsonObject)e, rootDir);
      }
    }
    
    Map<String, List<ValidationMessage>> messages = new HashMap<String, List<ValidationMessage>>();
    sourceIg = template.onLoadEvent(sourceIg, messages);
    checkOutcomes(messages);
    // ok, loaded. Now we start loading settings out of the IG
    tool = GenerationTool.Jekyll;
    version = processVersion(sourceIg.getFhirVersion().get(0).asStringValue()); // todo: support multiple versions

    if (!VersionUtilities.isSupportedVersion(version)) {
      throw new Exception("Error: the IG declares that is based on version "+version+" but this IG publisher only supports publishing the following versions: "+VersionUtilities.listSupportedVersions());
    }
    pubVersion = FhirPublication.fromCode(version);

    specPath = pathForVersion();
    qaDir = null;
    vsCache = Utilities.path(repoRoot, "txCache");
    templateProvider.clear();
    
    String expParams = null;
    List<String> exemptHtmlPatterns = new ArrayList<>();
    boolean hintAboutNonMustSupport = false;
    boolean anyExtensionsAllowed = false;
    boolean checkAggregation = false;
    boolean autoLoad = false;
    boolean showReferenceMessages = false;
    copyrightYear = null;
    Boolean useStatsOptOut = null;
    List<String> extensionDomains = new ArrayList<>();
    tempDir = Utilities.path(rootDir, "temp");
    outputDir = Utilities.path(rootDir, "output");
    Map<String, String> expParamMap = new HashMap<>();
    
    int count = 0;
    for (ImplementationGuideDefinitionParameterComponent p : sourceIg.getDefinition().getParameter()) {
      // documentation for this list: https://confluence.hl7.org/display/FHIR/Implementation+Guide+Parameters
      if (p.getCode().equals("logging")) { // added
        logOptions.add(p.getValue());        
      } else if (p.getCode().equals("generate")) { // added
        if ("example-narratives".equals(p.getValue()))
          genExampleNarratives = true;
        if ("examples".equals(p.getValue()))
          genExamples = true;
      } else if (p.getCode().equals("path-resource")) {
        resourceDirs.add(Utilities.path(rootDir, p.getValue()));
      } else if (p.getCode().equals("autoload-resources")) {     
        autoLoad = "true".equals(p.getValue());
      } else if (p.getCode().equals("codesystem-property")) {     
        codeSystemProps.add(p.getValue());
      } else if (p.getCode().equals("path-pages")) {     
        pagesDirs.add(Utilities.path(rootDir, p.getValue()));
      } else if (p.getCode().equals("copyrightyear")) {     
        copyrightYear = p.getValue();
      } else if (p.getCode().equals("path-qa")) {     
        qaDir = Utilities.path(rootDir, p.getValue());
      } else if (p.getCode().equals("path-tx-cache")) {     
        vsCache =  Paths.get(p.getValue()).isAbsolute() ? p.getValue() : Utilities.path(rootDir, p.getValue());
      } else if (p.getCode().equals("path-liquid")) {
        templateProvider.load(Utilities.path(rootDir, p.getValue()));
      } else if (p.getCode().equals("path-temp")) {
        tempDir = Utilities.path(rootDir, p.getValue());
        if (!tempDir.startsWith(rootDir))
          throw new Exception("Temp directory must be a sub-folder of the base directory");
      } else if (p.getCode().equals("path-output") && mode != IGBuildMode.WEBSERVER) {
        // Can't override outputDir if building using webserver
        outputDir = Utilities.path(rootDir, p.getValue());
        if (!outputDir.startsWith(rootDir))
          throw new Exception("Output directory must be a sub-folder of the base directory");
      } else if (p.getCode().equals("path-history")) {     
        historyPage = p.getValue();
      } else if (p.getCode().equals("path-expansion-params")) {     
        expParams = p.getValue();
      } else if (p.getCode().equals("path-suppressed-warnings")) {     
        loadSuppressedMessages(Utilities.path(rootDir, p.getValue()), "ImplementationGuide.definition.parameter["+count+"].value");
      } else if (p.getCode().equals("html-exempt")) {     
        exemptHtmlPatterns.add(p.getValue());
      } else if (p.getCode().equals("usage-stats-opt-out")) {     
        useStatsOptOut = "true".equals(p.getValue());
      } else if (p.getCode().equals("extension-domain")) {
        extensionDomains.add(p.getValue());
      } else if (p.getCode().equals("bundle-references-resolve")) {
        bundleReferencesResolve = "true".equals(p.getValue());        
      } else if (p.getCode().equals("active-tables")) {
        HierarchicalTableGenerator.ACTIVE_TABLES = "true".equals(p.getValue());
      } else if (p.getCode().equals("ig-expansion-parameters")) {     
        expParamMap.put(p.getCode(), p.getValue());
      } else if (p.getCode().equals("special-url")) {     
        listedURLExemptions.add(p.getValue());
      } else if (p.getCode().equals("template-openapi")) {     
        openApiTemplate = p.getValue();
      } else if (p.getCode().equals("template-html")) {     
        htmlTemplate = p.getValue();
      } else if (p.getCode().equals("template-md")) {     
        mdTemplate = p.getValue();
      } else if (p.getCode().equals("path-binary")) {     
        binaryPaths.add(Utilities.path(rootDir, p.getValue()));
      } else if (p.getCode().equals("show-inherited-invariants")) {     
        allInvariants = "true".equals(p.getValue());
      } else if (p.getCode().equals("apply-contact") && p.getValue().equals("true")) {
        contacts = sourceIg.getContact();
      } else if (p.getCode().equals("apply-context") && p.getValue().equals("true")) {
        contexts = sourceIg.getUseContext();
      } else if (p.getCode().equals("apply-copyright") && p.getValue().equals("true")) {
        copyright = sourceIg.getCopyright();
      } else if (p.getCode().equals("apply-jurisdiction") && p.getValue().equals("true")) {
        jurisdictions = sourceIg.getJurisdiction();
      } else if (p.getCode().equals("apply-license") && p.getValue().equals("true")) {
        licenseInfo = sourceIg.getLicense();
      } else if (p.getCode().equals("apply-publisher") && p.getValue().equals("true")) {
        publisher = sourceIg.getPublisher();
      } else if (p.getCode().equals("apply-version") && p.getValue().equals("true")) {
        businessVersion = sourceIg.getVersion();
      } else if (p.getCode().equals("generate-version")) {     
        generateVersions.add(p.getValue());
      } else if (p.getCode().equals("version-comparison")) {     
        if (comparisonVersions == null) {
          comparisonVersions = new ArrayList<>();
        }
        if ("n/a".equals(p.getValue())) {
          comparisonVersions.add(p.getValue());
        }        
      } else if (p.getCode().equals("validation")) {
        if (p.getValue().equals("check-must-support"))
          hintAboutNonMustSupport = true;
        else if (p.getValue().equals("allow-any-extensions"))
          anyExtensionsAllowed = true;
        else if (p.getValue().equals("check-aggregation"))
          checkAggregation = true;
        else if (p.getValue().equals("no-broken-links"))
          brokenLinksError = true;
        else if (p.getValue().equals("show-reference-messages"))
          showReferenceMessages = true;
      }
      count++;
    }
    
    // ok process the paths
    log("Root directory: "+rootDir);
    if (resourceDirs.isEmpty())
      resourceDirs.add(Utilities.path(rootDir, "resources"));
    if (pagesDirs.isEmpty())
      pagesDirs.add(Utilities.path(rootDir, "pages"));
    if (mode == IGBuildMode.WEBSERVER) 
      vsCache = Utilities.path(System.getProperty("java.io.tmpdir"), "fhircache");
    else if (vsCache == null) {
     if (mode == IGBuildMode.AUTOBUILD)
       vsCache = Utilities.path(System.getProperty("java.io.tmpdir"), "fhircache");
     else
       vsCache = Utilities.path(System.getProperty("user.home"), "fhircache");
    }
    
    logDebugMessage(LogCategory.INIT, "Check folders");
    List<String> missingDirs = new ArrayList<String>();
    for (String s : resourceDirs) {
      logDebugMessage(LogCategory.INIT, "Source: "+s);
      if (!checkDir(s, true))
        missingDirs.add(s);
    }
    resourceDirs.removeAll(missingDirs);
    
    missingDirs.clear();
    for (String s : pagesDirs) {
      logDebugMessage(LogCategory.INIT, "Pages: "+s);
      if (!checkDir(s, true))
        missingDirs.add(s);
    }
    pagesDirs.removeAll(missingDirs);

    logDebugMessage(LogCategory.INIT, "Temp: "+tempDir);
    Utilities.clearDirectory(tempDir);
    forceDir(tempDir);
    forceDir(Utilities.path(tempDir, "_includes"));
    forceDir(Utilities.path(tempDir, "_data"));
    logDebugMessage(LogCategory.INIT, "Output: "+outputDir);
    forceDir(outputDir);
    Utilities.clearDirectory(outputDir);
    if (qaDir != null) {
      logDebugMessage(LogCategory.INIT, "QA Dir: "+qaDir);
      forceDir(qaDir);
    }
    makeQA = mode == IGBuildMode.WEBSERVER ? false : qaDir != null;

    if (Utilities.existsInList(version.substring(0,  3), "1.0", "1.4", "1.6", "3.0"))
      markdownEngine = new MarkDownProcessor(Dialect.DARING_FIREBALL);
    else
      markdownEngine = new MarkDownProcessor(Dialect.COMMON_MARK);
    
    // loading the specifications
    context = loadCorePackage();
    context.setProgress(true);
    context.setIgnoreProfileErrors(true);
    context.setLogger(logger);
    context.setAllowLoadingDuplicates(true);
    context.setExpandCodesLimit(1000);
    context.setExpansionProfile(makeExpProfile());

    // initializing the tx sub-system
    Utilities.createDirectory(vsCache);
    if (cacheOption == CacheOption.CLEAR_ALL) {
      log("Terminology Cache is at "+vsCache+". Clearing now");
      Utilities.clearDirectory(vsCache);
    } else if (mode == IGBuildMode.AUTOBUILD) {
      log("Terminology Cache is at "+vsCache+". Trimming now");
      Utilities.clearDirectory(vsCache, "snomed.cache", "loinc.cache", "ucum.cache");
    } else if (cacheOption == CacheOption.CLEAR_ERRORS) {
      log("Terminology Cache is at "+vsCache+". Clearing Errors now");
      logDebugMessage(LogCategory.INIT, "Deleted "+Integer.toString(clearErrors(vsCache))+" files");
    } else
      log("Terminology Cache is at "+vsCache+". "+Integer.toString(Utilities.countFilesInDirectory(vsCache))+" files in cache");
    if (!new File(vsCache).exists())
      throw new Exception("Unable to access or create the cache directory at "+vsCache);
    logDebugMessage(LogCategory.INIT, "Load Terminology Cache from "+vsCache);
    context.initTS(vsCache);
    if (expParams != null) {
      context.setExpansionProfile((Parameters) VersionConvertor_40_50.convertResource(FormatUtilities.loadFile(Utilities.path(Utilities.getDirectoryForFile(igName), expParams))));
    } else if (!expParamMap.isEmpty()) {
      context.setExpansionProfile(new Parameters());      
    }
    for (String n : expParamMap.values())
      context.getExpansionParameters().addParameter(n, expParamMap.get(n));
    
    txLog = Utilities.createTempFile("fhir-ig-", ".log").getAbsolutePath();
    if (mode != IGBuildMode.WEBSERVER) {
      if (txServer == null || !txServer.contains(":")) {
        log("WARNING: Running without terminology server - terminology content will likely not publish correctly");
        context.setCanRunWithoutTerminology(true);
        txLog = null;
      } else {
        log("Connect to Terminology Server at "+txServer);
        checkTSVersion(vsCache, context.connectToTSServer(TerminologyClientFactory.makeClient(txServer, FhirPublication.fromCode(version)), txLog));
      }
    } else 
      checkTSVersion(vsCache, context.connectToTSServer(TerminologyClientFactory.makeClient(webTxServer.getAddress(), FhirPublication.fromCode(version)), txLog));
    
    loadPubPack();
    igpkp = new IGKnowledgeProvider(context, checkAppendSlash(specPath), determineCanonical(sourceIg.getUrl(), "ImplementationGuide.url"), template.config(), errors, VersionUtilities.isR2Ver(version), template, listedURLExemptions);
    if (autoLoad) {
      igpkp.setAutoPath(true);
    }
    fetcher.setPkp(igpkp);
    template.loadSummaryRows(igpkp.summaryRows());
    if (!dependsOnUTG(sourceIg.getDependsOn()) && !sourceIg.getPackageId().contains("hl7.terminology")) {
      loadUTG();
    }
    
    inspector = new HTLMLInspector(outputDir, specMaps, this, igpkp.getCanonical(), sourceIg.getPackageId());
    inspector.getManual().add("full-ig.zip");
    if (historyPage != null) {
      inspector.getManual().add(historyPage);
      inspector.getManual().add(Utilities.pathURL(igpkp.getCanonical(), historyPage));
    }
    inspector.getManual().add("qa.html");
    inspector.getManual().add("qa-tx.html");
    inspector.getExemptHtmlPatterns().addAll(exemptHtmlPatterns);
    inspector.setPcm(pcm);
    
    int i = 0;
    for (ImplementationGuideDependsOnComponent dep : sourceIg.getDependsOn()) {
      loadIg(dep, i);
      i++;
    }
    generateLoadedSnapshots();
    
    // set up validator;
    validator = new InstanceValidator(context, new IGPublisherHostServices(), context.getXVer()); // todo: host services for reference resolution....
    validator.setAllowXsiLocation(true);
    validator.setNoBindingMsgSuppressed(true);
    validator.setNoExtensibleWarnings(true);
    validator.setHintAboutNonMustSupport(hintAboutNonMustSupport);
    validator.setAnyExtensionsAllowed(anyExtensionsAllowed);
    validator.setAllowExamples(true);
    validator.setCrumbTrails(true);
    validator.setWantCheckSnapshotUnchanged(true);
    
    pvalidator = new ProfileValidator(context, context.getXVer());
    csvalidator = new CodeSystemValidator(context, context.getXVer());
    pvalidator.setCheckAggregation(checkAggregation);
    pvalidator.setCheckMustSupport(hintAboutNonMustSupport);
    validator.setShowMessagesFromReferences(showReferenceMessages);
    validator.getExtensionDomains().addAll(extensionDomains);
    validator.getExtensionDomains().add(IGHelper.EXT_PRIVATE_BASE);
    validationFetcher = new ValidationServices(context, igpkp, fileList, npmList, bundleReferencesResolve);
    validator.setFetcher(validationFetcher);
    validator.setTracker(this);
    for (String s : context.getBinaries().keySet()) {
      if (needFile(s)) {
        if (makeQA)
          checkMakeFile(context.getBinaries().get(s), Utilities.path(qaDir, s), otherFilesStartup);
        checkMakeFile(context.getBinaries().get(s), Utilities.path(tempDir, s), otherFilesStartup);
      }
    }
    otherFilesStartup.add(Utilities.path(tempDir, "_data"));
    otherFilesStartup.add(Utilities.path(tempDir, "_data", "fhir.json"));
    otherFilesStartup.add(Utilities.path(tempDir, "_data", "structuredefinitions.json"));
    otherFilesStartup.add(Utilities.path(tempDir, "_data", "questionnaires.json"));
    otherFilesStartup.add(Utilities.path(tempDir, "_data", "pages.json"));
    otherFilesStartup.add(Utilities.path(tempDir, "_includes"));

    if (sourceIg.hasLicense())
      license = sourceIg.getLicense().toCode();
    npmName = sourceIg.getPackageId();
    if (Utilities.noString(npmName)) {
      throw new Error("No packageId provided in the implementation guide resource - cannot build this IG");
    }
    appendTrailingSlashInDataFile = true;
    includeHeadings = template.getIncludeHeadings();
    igArtifactsPage = template.getIGArtifactsPage();
    doTransforms = template.getDoTransforms();
    template.getExtraTemplates(extraTemplates);
    
    for (Extension e : sourceIg.getExtensionsByUrl(IGHelper.EXT_SPREADSHEET)) {
      spreadsheets.add(e.getValue().primitiveValue());
    }
    ToolingExtensions.removeExtension(sourceIg, IGHelper.EXT_SPREADSHEET);

    for (Extension e : sourceIg.getExtensionsByUrl(IGHelper.EXT_MAPPING_CSV)) {
      mappings.add(e.getValue().primitiveValue());
    }
    for (Extension e : sourceIg.getExtensionsByUrl(IGHelper.EXT_BUNDLE)) {
      bundles.add(e.getValue().primitiveValue());
    }
    if (mode == IGBuildMode.AUTOBUILD)
      extensionTracker.setoptIn(true);
    else if (npmName.contains("hl7.") || npmName.contains("argonaut.") || npmName.contains("ihe."))
      extensionTracker.setoptIn(true);
    else if (useStatsOptOut != null) 
      extensionTracker.setoptIn(!useStatsOptOut);
    else
      extensionTracker.setoptIn(!ini.getBooleanProperty("IG", "usage-stats-opt-out"));
    
    log("Initialization complete");
  }

  private void generateLoadedSnapshots() {
    for (StructureDefinition sd : context.allStructures()) {
      if (!sd.hasSnapshot() && sd.hasBaseDefinition()) {
        generateSnapshot(sd);
      }
    }
  }

  private void generateSnapshot(StructureDefinition sd) {
    List<ValidationMessage> messages = new ArrayList<>();
    ProfileUtilities utils = new ProfileUtilities(context, messages, igpkp);
    StructureDefinition base = context.fetchResource(StructureDefinition.class, sd.getBaseDefinition());
    if (base == null) {
      System.out.println("Cannot find or generate snapshot for base definition "+sd.getBaseDefinition()+" from "+sd.getUrl());
    } else {
      if (!base.hasSnapshot()) {
        generateSnapshot(base);
      }
      utils.setIds(sd, true);
      try {
        utils.generateSnapshot(base, sd, sd.getUrl(), Utilities.extractBaseUrl(base.getUserString("path")), sd.getName());
        if (!sd.hasSnapshot()) {
          System.out.println("Unable to generate snapshot for "+sd.getUrl()+": "+messages.toString());        
        }
      } catch (Exception e) {
        System.out.println("Exception generating snapshot for "+sd.getUrl()+": "+e.getMessage());        
      }      
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


  private boolean dependsOnUTG(List<ImplementationGuideDependsOnComponent> dependsOn) {
    for (ImplementationGuideDependsOnComponent d : dependsOn) {
      if (d.hasPackageId() && d.getPackageId().contains("hl7.terminology")) {
        return true;
      }
      if (d.hasUri() && d.getUri().contains("terminology.hl7")) {
        return true;
      }
    }
    return false;
  }

  private String determineCanonical(String url, String path) throws FHIRException {
    if (url == null)
      return url;
    if (url.contains("/ImplementationGuide/"))
      return url.substring(0, url.indexOf("/ImplementationGuide/"));
    if (path != null) {
      errors.add(new ValidationMessage(Source.Publisher, IssueType.INVALID, path, "The canonical URL for an Implementation Guide must point directly to the implementation guide resource, not to the Implementation Guide as a whole", IssueSeverity.WARNING));
    }
    return url;
  }


  private String pathForVersion() {
    String v = version;
    while (v.indexOf(".") != v.lastIndexOf(".")) {
      v = v.substring(0, v.lastIndexOf("."));
    }
    if (v.equals("1.0"))
      return "http://hl7.org/fhir/DSTU2";
    if (v.equals("1.4"))
      return "http://hl7.org/fhir/2016May";
    if (v.equals("3.0"))
      return "http://hl7.org/fhir/STU3";
    if (v.equals("4.0"))
      return "http://hl7.org/fhir/R4";
    return "http://build.fhir.org";
  }


  private String processVersion(String v) {
    return v.equals("$build") ? Constants.VERSION : v;
  }


  private void initializeFromJson() throws Exception {
    JsonObject configuration = null;
    if (configFile == null) {
      adHocTmpDir = Utilities.path(System.getProperty("java.io.tmpdir"), "fhir-ig-scratch");
      log("Using inbuilt IG template in "+sourceDir);
      copyDefaultTemplate();
      buildConfigFile();
    } else
      log("Load Configuration from "+configFile);
    if (!new File(configFile).exists())
      throw new Exception("Unable to find file " + configFile + " and no ig.ini file specified - nothing to build.");
    try {
      configuration = JsonTrackingParser.parseJsonFile(configFile);
    } catch (Exception e) {
      throw new Exception("Error Reading JSON Config file at "+configFile+": "+e.getMessage(), e);
    }
    repoRoot = Utilities.getDirectoryForFile(configFile);
    if (configuration.has("redirect")) { // redirect to support auto-build for complex projects with IG folder in subdirectory
      String redirectFile = Utilities.path(Utilities.getDirectoryForFile(configFile), configuration.get("redirect").getAsString());
      log("Redirecting to Configuration from " + redirectFile);
      configFile = redirectFile;
      configuration = JsonTrackingParser.parseJsonFile(redirectFile);
    }
    if (configuration.has("logging")) {
      for (JsonElement n : configuration.getAsJsonArray("logging")) {
        String level = ((JsonPrimitive) n).getAsString();
        logOptions.add(level);
      }
    }
    if (configuration.has("exampleNarratives")) {
      genExampleNarratives = configuration.get("exampleNarratives").getAsBoolean();
    }

    if (configuration.has("tool") && !"jekyll".equals(str(configuration, "tool")))
      throw new Exception("Error: At present, configuration file must include a \"tool\" property with a value of 'jekyll'");
    tool = GenerationTool.Jekyll;
    version = ostr(configuration, "version");
    if (Utilities.noString(version))
      version = Constants.VERSION;
    
    if (!VersionUtilities.isSupportedVersion(version)) {
      throw new Exception("Error: the IG declares that is based on version "+version+" but this IG publisher only supports publishing the following versions: "+VersionUtilities.listSupportedVersions());
    }
    pubVersion = FhirPublication.fromCode(version);
        
    if (configuration.has("paths") && !(configuration.get("paths") instanceof JsonObject))
      throw new Exception("Error: if configuration file has a \"paths\", it must be an object");
    JsonObject paths = configuration.getAsJsonObject("paths");
    if (fetcher instanceof ZipFetcher) {
      rootDir = configFileRootPath;
    } else {
      rootDir = Utilities.getDirectoryForFile(configFile);
      if (Utilities.noString(rootDir))
        rootDir = getCurentDirectory();
      // We need the root to be expressed as a full path.  getDirectoryForFile will do that in general, but not in Eclipse
      rootDir = new File(rootDir).getCanonicalPath();
    }

    if (configuration.has("template")) {
      template = templateManager.loadTemplate(str(configuration, "template"), rootDir, configuration.has("npm-name") ? configuration.get("npm-name").getAsString() : null, mode == IGBuildMode.AUTOBUILD);
      if (!configuration.has("defaults"))
        configuration.add("defaults", template.config().get("defaults"));
      else
        JSONUtil.merge(template.config().getAsJsonObject("defaults"), configuration.getAsJsonObject("defaults"));
    }
    
    if (Utilities.existsInList(version.substring(0,  3), "1.0", "1.4", "1.6", "3.0"))
      markdownEngine = new MarkDownProcessor(Dialect.DARING_FIREBALL);
    else
      markdownEngine = new MarkDownProcessor(Dialect.COMMON_MARK);
    
    log("Root directory: "+rootDir);
    if (paths != null && paths.get("resources") instanceof JsonArray) {
      for (JsonElement e : (JsonArray) paths.get("resources"))
        resourceDirs.add(Utilities.path(rootDir, ((JsonPrimitive) e).getAsString()));
    } else
      resourceDirs.add(Utilities.path(rootDir, str(paths, "resources", "resources")));
    if (paths != null && paths.get("binaries") instanceof JsonArray) {
      for (JsonElement e : (JsonArray) paths.get("binaries")) {
        binaryPaths.add(Utilities.path(rootDir, ((JsonPrimitive) e).getAsString()));
      }
    } 
    if (paths != null && paths.get("pages") instanceof JsonArray) {
      for (JsonElement e : (JsonArray) paths.get("pages"))
        pagesDirs.add(Utilities.path(rootDir, ((JsonPrimitive) e).getAsString()));
    } else
      pagesDirs.add(Utilities.path(rootDir, str(paths, "pages", "pages")));
    
    if (mode != IGBuildMode.WEBSERVER){
      tempDir = Utilities.path(rootDir, str(paths, "temp", "temp"));
      String p = str(paths, "output", "output");
      outputDir = Paths.get(p).isAbsolute() ? p : Utilities.path(rootDir, p);
    }
   qaDir = Utilities.path(rootDir, str(paths, "qa", "qa"));
   vsCache = ostr(paths, "txCache");
   templateProvider.clear();
   if (paths.has("liquid")) {
     templateProvider.load(Utilities.path(rootDir, str(paths, "liquid", "liquid")));
   } 
    
   if (mode == IGBuildMode.WEBSERVER) 
      vsCache = Utilities.path(System.getProperty("java.io.tmpdir"), "fhircache");
    else if (vsCache != null)
      vsCache = Utilities.path(rootDir, vsCache);
    else if (mode == IGBuildMode.AUTOBUILD)
      vsCache = Utilities.path(System.getProperty("java.io.tmpdir"), "fhircache");
    else
      vsCache = Utilities.path(System.getProperty("user.home"), "fhircache");

    specPath = str(paths, "specification", "http://hl7.org/fhir/");
    if (configuration.has("pre-process")) {
      if (configuration.get("pre-process") instanceof JsonArray) {
        for (JsonElement e : (JsonArray) configuration.get("pre-process")) {
          handlePreProcess((JsonObject)e, rootDir);
        }
      } else
        handlePreProcess(configuration.getAsJsonObject("pre-process"), rootDir);
    }

    igName = Utilities.path(resourceDirs.get(0), str(configuration, "source", "ig.xml"));

    logDebugMessage(LogCategory.INIT, "Check folders");
    for (String s : resourceDirs) {
      logDebugMessage(LogCategory.INIT, "Source: "+s);
      checkDir(s);
    }
    for (String s : pagesDirs) {
      logDebugMessage(LogCategory.INIT, "Pages: "+s);
      checkDir(s);
    }
    logDebugMessage(LogCategory.INIT, "Temp: "+tempDir);
    Utilities.clearDirectory(tempDir);
    forceDir(tempDir);
    forceDir(Utilities.path(tempDir, "_includes"));
    forceDir(Utilities.path(tempDir, "_data"));
    logDebugMessage(LogCategory.INIT, "Output: "+outputDir);
    forceDir(outputDir);
    Utilities.clearDirectory(outputDir);
    logDebugMessage(LogCategory.INIT, "Temp: "+qaDir);
    forceDir(qaDir);

    Utilities.createDirectory(vsCache);
    if (cacheOption == CacheOption.CLEAR_ALL) {
      log("Terminology Cache is at "+vsCache+". Clearing now");
      Utilities.clearDirectory(vsCache);
    } else if (mode == IGBuildMode.AUTOBUILD) {
      log("Terminology Cache is at "+vsCache+". Trimming now");
      Utilities.clearDirectory(vsCache, "snomed.cache", "loinc.cache", "ucum.cache");
    } else if (cacheOption == CacheOption.CLEAR_ERRORS) {
        log("Terminology Cache is at "+vsCache+". Clearing Errors now");
        logDebugMessage(LogCategory.INIT, "Deleted "+Integer.toString(clearErrors(vsCache))+" files");
    } else
      log("Terminology Cache is at "+vsCache+". "+Integer.toString(Utilities.countFilesInDirectory(vsCache))+" files in cache");
    if (!new File(vsCache).exists())
      throw new Exception("Unable to access or create the cache directory at "+vsCache);

    context = loadCorePackage();
    context.setIgnoreProfileErrors(true);
    context.setProgress(true);
    context.setLogger(logger);
    context.setAllowLoadingDuplicates(true);
    context.setExpandCodesLimit(1000);
    context.setExpansionProfile(makeExpProfile());
    try {
      new ConfigFileConverter().convert(configFile, context, pcm);
    } catch (Exception e) {
      log("exception generating new IG");
      e.printStackTrace();
    }
    log("Load Terminology Cache from "+vsCache);
    context.initTS(vsCache);
    String sct = str(configuration, "sct-edition", "http://snomed.info/sct/900000000000207008");
    context.getExpansionParameters().addParameter("system-version", "http://snomed.info/sct|"+sct);
    txLog = Utilities.createTempFile("fhir-ig-", ".log").getAbsolutePath();
    context.getExpansionParameters().addParameter("activeOnly", "true".equals(ostr(configuration, "activeOnly")));
    if (mode != IGBuildMode.WEBSERVER) {
      if (txServer == null || !txServer.contains(":")) {
        log("WARNING: Running without terminology server - terminology content will likely not publish correctly");
        context.setCanRunWithoutTerminology(true);
        txLog = null;
      } else {
        log("Connect to Terminology Server at "+txServer);
        try {
          checkTSVersion(vsCache, context.connectToTSServer(TerminologyClientFactory.makeClient(txServer, FhirPublication.fromCode(version)), txLog));
        } catch (Exception e) {
          log("WARNING: Could not connect to terminology server - terminology content will likely not publish correctly ("+e.getMessage()+")");          
        }
      }
    } else 
      try {
        checkTSVersion(vsCache, context.connectToTSServer(TerminologyClientFactory.makeClient(webTxServer.getAddress(), FhirPublication.fromCode(version)), txLog));
      } catch (Exception e) {
        log("WARNING: Could not connect to terminology server - terminology content will likely not publish correctly ("+e.getMessage()+")");          
      }
    
    
    loadSpecDetails(context.getBinaries().get("spec.internals"));
    JsonElement cb = configuration.get("canonicalBase");
    if (cb == null)
      throw new Exception("You must define a canonicalBase in the json file");

    loadPubPack();

    igpkp = new IGKnowledgeProvider(context, checkAppendSlash(specPath), cb.getAsString(), configuration, errors, VersionUtilities.isR2Ver(version), null, listedURLExemptions);
    igpkp.loadSpecPaths(specMaps.get(0));
    fetcher.setPkp(igpkp);
    
    if (!dependsOnUTG(configuration.getAsJsonArray("dependencyList")) && (npmName == null || !npmName.contains("hl7.terminology"))) {
      loadUTG();
    }

    if (configuration.has("fixed-business-version")) {
      businessVersion = configuration.getAsJsonPrimitive("fixed-business-version").getAsString();
    }
    
    inspector = new HTLMLInspector(outputDir, specMaps, this, igpkp.getCanonical(), configuration.has("npm-name") ? configuration.get("npm-name").getAsString() : null);
    inspector.getManual().add("full-ig.zip");
    historyPage = ostr(paths, "history");
    if (historyPage != null) {
      inspector.getManual().add(historyPage);
      inspector.getManual().add(Utilities.pathURL(igpkp.getCanonical(), historyPage));
    }
    inspector.getManual().add("qa.html");
    inspector.getManual().add("qa-tx.html");
    if (configuration.has("exemptHtmlPatterns")) {
      for (JsonElement e : configuration.getAsJsonArray("exemptHtmlPatterns"))
        inspector.getExemptHtmlPatterns().add(e.getAsString());
    }
    inspector.setStrict("true".equals(ostr(configuration, "allow-malformed-html")));
    inspector.setPcm(pcm);
    makeQA = mode == IGBuildMode.WEBSERVER ? false : !"true".equals(ostr(configuration, "suppress-qa"));
    
    JsonArray deps = configuration.getAsJsonArray("dependencyList");
    if (deps != null) {
      for (JsonElement dep : deps) {
        loadIg((JsonObject) dep);
      }
    }

    generateLoadedSnapshots();
    
    JsonArray cspl = configuration.getAsJsonArray("code.system.property.list");
    if (cspl != null) {
      for (JsonElement csp : cspl) {
        codeSystemProps.add(((JsonPrimitive) csp).getAsString());
      }
    }

    
    // ;
    validator = new InstanceValidator(context, new IGPublisherHostServices(), context.getXVer()); // todo: host services for reference resolution....
    validator.setAllowXsiLocation(true);
    validator.setNoBindingMsgSuppressed(true);
    validator.setNoExtensibleWarnings(true);
    validator.setHintAboutNonMustSupport(bool(configuration, "hintAboutNonMustSupport"));
    validator.setAnyExtensionsAllowed(bool(configuration, "anyExtensionsAllowed"));
    validator.setAllowExamples(true);
    validator.setCrumbTrails(true);
    validator.setWantCheckSnapshotUnchanged(true);
    
    pvalidator = new ProfileValidator(context, context.getXVer());
    csvalidator = new CodeSystemValidator(context, context.getXVer());
    if (configuration.has("check-aggregation") && configuration.get("check-aggregation").getAsBoolean())
      pvalidator.setCheckAggregation(true);
    if (configuration.has("check-mustSupport") && configuration.get("check-mustSupport").getAsBoolean())
      pvalidator.setCheckMustSupport(true);
    if (configuration.has("show-reference-messages") && configuration.get("show-reference-messages").getAsBoolean())
      validator.setShowMessagesFromReferences(true);

    if (paths.get("extension-domains") instanceof JsonArray) {
      for (JsonElement e : (JsonArray) paths.get("extension-domains"))
        validator.getExtensionDomains().add(((JsonPrimitive) e).getAsString());
    }
    validator.getExtensionDomains().add(IGHelper.EXT_PRIVATE_BASE);
    if (configuration.has("jurisdiction")) {
      jurisdictions = new ArrayList<CodeableConcept>();
      for (String s : configuration.getAsJsonPrimitive("jurisdiction").getAsString().trim().split("\\,")) {
        CodeableConcept cc = new CodeableConcept();
        jurisdictions.add(cc);
        Coding c = cc.addCoding();
        String sc = s.trim();
        if (Utilities.isInteger(sc)) 
          c.setSystem("http://unstats.un.org/unsd/methods/m49/m49.htm").setCode(sc);
        else
          c.setSystem("urn:iso:std:iso:3166").setCode(sc);
        ValidationResult vr = context.validateCode(new ValidationOptions("en-US"), c, null);
        if (vr.getDisplay() != null)
          c.setDisplay(vr.getDisplay());
      }
    }
    if (configuration.has("suppressedWarningFile")) {
      String suppressPath = configuration.getAsJsonPrimitive("suppressedWarningFile").getAsString();
      if (!suppressPath.isEmpty())
        loadSuppressedMessages(Utilities.path(rootDir, suppressPath), "ConfigFile.suppressedWarningFile");
    }
    validationFetcher = new ValidationServices(context, igpkp, fileList, npmList, bool(configuration, "bundleReferencesResolve"));
    validator.setFetcher(validationFetcher);
    validator.setTracker(this);
    for (String s : context.getBinaries().keySet())
      if (needFile(s)) {
        if (makeQA)
          checkMakeFile(context.getBinaries().get(s), Utilities.path(qaDir, s), otherFilesStartup);
        checkMakeFile(context.getBinaries().get(s), Utilities.path(tempDir, s), otherFilesStartup);
      }
    otherFilesStartup.add(Utilities.path(tempDir, "_data"));
    otherFilesStartup.add(Utilities.path(tempDir, "_data", "fhir.json"));
    otherFilesStartup.add(Utilities.path(tempDir, "_data", "structuredefinitions.json"));
    otherFilesStartup.add(Utilities.path(tempDir, "_data", "questionnaires.json"));
    otherFilesStartup.add(Utilities.path(tempDir, "_data", "pages.json"));
    otherFilesStartup.add(Utilities.path(tempDir, "_includes"));

    JsonArray urls = configuration.getAsJsonArray("special-urls");
    if (urls != null) {
      for (JsonElement url : urls) {
        listedURLExemptions.add(url.getAsString());
      }
    }
    includeHeadings = !configuration.has("includeHeadings") || configuration.get("includeHeadings").getAsBoolean();
    openApiTemplate = configuration.has("openapi-template") ? configuration.get("openapi-template").getAsString() : null;
    license = ostr(configuration, "license");
    htmlTemplate = configuration.has("html-template") ? str(configuration, "html-template") : null;
    mdTemplate = configuration.has("md-template") ? str(configuration, "md-template") : null;
    npmName = configuration.has("npm-name") ? configuration.get("npm-name").getAsString(): null;
    brokenLinksError = "error".equals(ostr(configuration, "broken-links"));
    nestedIgConfig = configuration.has("nestedIgConfig") ? configuration.get("nestedIgConfig").getAsString() : null;
    nestedIgOutput = configuration.has("nestedIgOutput") ? configuration.get("nestedIgOutput").getAsString() : null;
    igArtifactsPage = configuration.has("igArtifactsPage") ? configuration.get("igArtifactsPage").getAsString() : null;
    genExamples = "true".equals(ostr(configuration, "gen-examples"));
    doTransforms = "true".equals(ostr(configuration, "do-transforms"));
    appendTrailingSlashInDataFile = "true".equals(ostr(configuration, "append-slash-to-dependency-urls"));
    allInvariants = configuration.has("show-inherited-invariants") ? "true".equals(ostr(configuration, "show-inherited-invariants")) : true;
    HierarchicalTableGenerator.ACTIVE_TABLES = configuration.has("activeTables") && configuration.get("activeTables").getAsBoolean();
    
    JsonArray array = configuration.getAsJsonArray("spreadsheets");
    if (array != null) {
      for (JsonElement be : array) 
        spreadsheets.add(be.getAsString());
    }
    if (configuration.has("mappings") && configuration.get("mappings").isJsonArray()) {
      array = configuration.getAsJsonArray("mappings");
      if (array != null) {
        for (JsonElement be : array) 
          mappings.add(be.getAsString());
      }
    }
    array = configuration.getAsJsonArray("bundles");
    if (array != null) {
      for (JsonElement be : array) 
        bundles.add(be.getAsString());
    }
    processExtraTemplates(configuration.getAsJsonArray("extraTemplates"));
    if (mode == IGBuildMode.AUTOBUILD)
      extensionTracker.setoptIn(true);
    else if (npmName.contains("hl7") || npmName.contains("argonaut") || npmName.contains("ihe"))
      extensionTracker.setoptIn(true);
    else 
      extensionTracker.setoptIn(!configuration.has("usage-stats-opt-out"));

    logDebugMessage(LogCategory.INIT, "Initialization complete");
    // now, do regeneration
    JsonArray regenlist = configuration.getAsJsonArray("regenerate");
    if (regenlist != null) {
      for (JsonElement regen : regenlist) {
        regenList.add(((JsonPrimitive) regen).getAsString());
      }
    }
  }

  private void loadPubPack() throws FHIRException, IOException {
    NpmPackage npm = pcm.loadPackage("hl7.fhir.pubpack", "0.0.7");
    context.loadFromPackage(npm, null);
    npm = pcm.loadPackage("hl7.fhir.xver-extensions", "0.0.5");
    context.loadFromPackage(npm, null);
  }

  private void loadUTG() throws FHIRException, IOException {
    String vs = null;
    if (VersionUtilities.isR3Ver(version)) {
      vs = "hl7.terminology.r3";
    } else if (VersionUtilities.isR4Ver(version)) {
      vs = "hl7.terminology.r4";
    } else if (VersionUtilities.isR5Ver(version)) {
      vs = "hl7.terminology.r5";
    }
    if (vs != null) {
      NpmPackage npm = pcm.loadPackage(vs, null);
      SpecMapManager spm = new SpecMapManager(TextFile.streamToBytes(npm.load("other", "spec.internals")), npm.fhirVersion());
      IContextResourceLoader loader = new PublisherLoader(npm, spm, npm.getWebLocation(), igpkp).makeLoader();
      context.loadFromPackage(npm, loader);
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
          name = template.getAsString();
        else {
          if (!((JsonObject)template).has("name") || !((JsonObject)template).has("description"))
            throw new Exception("extraTemplates must be an array of objects with 'name' and 'description' properties");
          name = ((JsonObject)template).get("name").getAsString();
          if (((JsonObject)template).has("isHistory") || ((JsonObject)template).has("isExamples"))
            setExtras = true;
        }
        if (name.equals("defns"))
          hasDefns = true;
        else if (name.equals("format"))
          hasFormat = true;
        
      }
      if (!hasDefns) {
        extraTemplateList.add("defns");
        extraTemplates.put("defns", "Definitions");
      }
      if (!hasFormat) {
        extraTemplateList.add("format");
        extraTemplates.put("format", "FMT Representation");
      }
      for (JsonElement template : templates) {
        if (template.isJsonPrimitive()) {
          extraTemplateList.add(template.getAsString());
          extraTemplates.put(template.getAsString(), template.getAsString());
          if (template.equals("examples"))
            exampleTemplates.add(template.getAsString());
          if (template.getAsString().endsWith("-history"))
            historyTemplates.add(template.getAsString());
        } else {
          String templateName = ((JsonObject)template).get("name").getAsString();
          extraTemplateList.add(templateName);
          extraTemplates.put(templateName, ((JsonObject)template).get("description").getAsString());
          if (!setExtras) {
            if (templateName.equals("examples"))
              exampleTemplates.add(templateName);
            if (templateName.endsWith("-history"))
              historyTemplates.add(templateName);
          } else if (((JsonObject)template).has("isExamples") && ((JsonObject)template).get("isExamples").getAsBoolean()) {
            exampleTemplates.add(templateName);
          } else if (((JsonObject)template).has("isHistory") && ((JsonObject)template).get("isHistory").getAsBoolean()) {
            historyTemplates.add(templateName);
          }            
        }
      }
    }
  }

  void handlePreProcess(JsonObject pp, String root) throws Exception {
    String path = Utilities.path(root, str(pp, "folder"));
    if (checkDir(path, true)) {
      prePagesDirs.add(path);
      String prePagesXslt = null;
      if (pp.has("transform")) {
        prePagesXslt = Utilities.path(root, str(pp, "transform"));
        checkFile(prePagesXslt);
      }
      String relativePath = null;
      if (pp.has("relativePath")) {
        relativePath = str(pp, "relativePath");
      }
      System.out.println("Pre-Process: "+path+" = "+relativePath+" | "+prePagesXslt);
      PreProcessInfo ppinfo = new PreProcessInfo(prePagesXslt, relativePath);
      preProcessInfo.put(path, ppinfo);
    }
  }

  private void loadSuppressedMessages(String messageFile, String path) throws Exception {
    File f = new File(messageFile);
    if (!f.exists()) {
      errors.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, path, "Supressed messages file not found", IssueSeverity.ERROR));
    } else {
      String s = TextFile.fileToString(messageFile);
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
                errors.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, path, "Supressed messages file has errors with no reason ("+l+")", IssueSeverity.ERROR));
                suppressedMessages.put(l, "?pub-msg-1?");
              } else {
                suppressedMessages.put(l, reason);
              }
            }
          }
        }
      } else {
        errors.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, path, "Supressed messages file is not using the new format (see https://confluence.hl7.org/display/FHIR/Implementation+Guide+Parameters)", IssueSeverity.ERROR));
        InputStreamReader r = new InputStreamReader(new FileInputStream(messageFile));
        StringBuilder b = new StringBuilder();
        while (r.ready()) {
          char c = (char) r.read();
          if (c == '\r' || c == '\n') {
            if (b.length() > 0)
              suppressedMessages.put(b.toString(), "?pub-msg-2?");
            b = new StringBuilder();
          } else
            b.append(c);
        }
        if (b.length() > 0)
          suppressedMessages.put(b.toString(), "?pub-msg-3?");
        r.close();
      }
    }
  }
  
  private void checkTSVersion(String dir, String version) throws FileNotFoundException, IOException {
    if (Utilities.noString(version))
      return;

    // we wipe the terminology cache if the terminology server cersion has changed
    File verFile = new File(Utilities.path(dir, "version.ctl"));
    if (verFile.exists()) {
      String ver = TextFile.fileToString(verFile);
      if (!ver.equals(version)) {
        logDebugMessage(LogCategory.INIT, "Terminology Server Version has changed from "+ver+" to "+version+", so clearing txCache");
        Utilities.clearDirectory(dir);
      }
    }
    TextFile.stringToFile(version, verFile, false);
  }


  private int clearErrors(String dirName) throws FileNotFoundException, IOException {
    File dir = new File(dirName);
    int i = 0;
    for (File f : dir.listFiles()) {
      String s = TextFile.fileToString(f);
      if (s.contains("OperationOutcome")) {
        f.delete();
        i++;
      }
    }
    return i;
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
    if (!new File(adHocTmpDir).exists())
      Utilities.createDirectory(adHocTmpDir);
    Utilities.clearDirectory(adHocTmpDir);

    FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager(true, ToolsVersion.TOOLS_VERSION);
    
    NpmPackage npm = null; 
    if (specifiedVersion == null) {
      npm = pcm.loadPackage("hl7.fhir.r5.core", Constants.VERSION);
    } else {
      String vid = VersionUtilities.getCurrentVersion(specifiedVersion);
      String pid = VersionUtilities.packageForVersion(vid);
      npm = pcm.loadPackage(pid, vid);
    }
    
    ZipInputStream zip = new ZipInputStream(npm.load("other", "ig-template.zip"));
    byte[] buffer = new byte[2048];
    ZipEntry entry;
    while((entry = zip.getNextEntry())!=null) {
      String filename = Utilities.path(adHocTmpDir, entry.getName());
      String dir = Utilities.getDirectoryForFile(filename);
      Utilities.createDirectory(dir);
      FileOutputStream output = new FileOutputStream(filename);
      int len = 0;
      while ((len = zip.read(buffer)) > 0)
        output.write(buffer, 0, len);
      output.close();
    }
    zip.close();
  }

  private void buildConfigFile() throws IOException, org.hl7.fhir.exceptions.FHIRException, FHIRFormatError {
    configFile = Utilities.path(adHocTmpDir, "ig.json");
    // temporary config, until full ig template is in place
    String v = specifiedVersion != null ? VersionUtilities.getCurrentVersion(specifiedVersion) : Constants.VERSION; 
    String igs = VersionUtilities.isR3Ver(v) ? "ig3.xml" : "ig4.xml";
    TextFile.stringToFile(
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
            "}\r\n", configFile, false);
    Utilities.createDirectory(Utilities.path(adHocTmpDir, "resources"));
    Utilities.createDirectory(Utilities.path(adHocTmpDir, "pages"));
  }

  private SimpleWorkerContext loadCorePackage() throws Exception {
    NpmPackage pi = null;
    
    String v = version.equals(Constants.VERSION) ? "current" : version;

    if (Utilities.noString(igPack)) {
      System.out.println("Core Package "+VersionUtilities.packageForVersion(v)+"#"+v);
      pi = pcm.loadPackage(VersionUtilities.packageForVersion(v), v);
    } else {
      System.out.println("Load from provided file "+igPack);
      pi = NpmPackage.fromPackage(new FileInputStream(igPack));
    }
    if (pi == null) {
      // gg: I don't think this works anymore
      throw new Error("Unsupported code - contact Grahame Grieve");
//      String url = getMasterSource();
//      InputStream src = fetchFromSource("hl7.fhir.core-"+v, url);
//      pi = pcm.addPackageToCache("hl7.fhir.core", v, src, url);
    }
    if (v.equals("current")) {
      // currency of the current core package is a problem, since its not really version controlled.
      // we'll check for a specified version...
      logDebugMessage(LogCategory.INIT, "Checking hl7.fhir.core-"+v+" currency");
      int cacheVersion = getBuildVersionForCorePackage(pi);
      int lastAcceptableVersion = ToolsVersion.TOOLS_VERSION;
      if (cacheVersion < lastAcceptableVersion) {
        logDebugMessage(LogCategory.INIT, "Updating hl7.fhir.core-"+version+" package from source (too old - is "+cacheVersion+", must be "+lastAcceptableVersion);
        pi = pcm.addPackageToCache("hl7.fhir.core", "current", fetchFromSource("hl7.fhir.core-"+v, getMasterSource()), getMasterSource());
      } else {
        logDebugMessage(LogCategory.INIT, "   ...  ok: is "+cacheVersion+", must be "+lastAcceptableVersion);
      }
    }
    logDebugMessage(LogCategory.INIT, "Load hl7.fhir.core-"+v+" package from "+pi.summary());
    npmList.add(pi);
    
    SpecMapManager spm = loadSpecDetails(TextFile.streamToBytes(pi.load("other", "spec.internals")));
    SimpleWorkerContext sp;
    IContextResourceLoader loader = new PublisherLoader(pi, spm, specPath, igpkp).makeLoader();
    sp = SimpleWorkerContext.fromPackage(pi, loader);
    sp.loadBinariesFromFolder(pi);
    sp.setCacheId(UUID.randomUUID().toString());
    if (!version.equals(Constants.VERSION)) {
      // If it wasn't a 4.0 source, we need to set the ids because they might not have been set in the source
      ProfileUtilities utils = new ProfileUtilities(context, new ArrayList<ValidationMessage>(), igpkp);
      for (StructureDefinition sd : sp.allStructures()) {
        utils.setIds(sd, true);
      }
    }
    return sp;    
  }
  
  private int getBuildVersionForCorePackage(NpmPackage pi) throws IOException {
    if (!pi.getNpm().has("tools-version"))
      return 0;
    return pi.getNpm().get("tools-version").getAsInt();
  }


  private String getMasterSource() {
    if (VersionUtilities.isR2Ver(version)) return "http://hl7.org/fhir/DSTU2/hl7.fhir.r2.core.tgz";
    if (VersionUtilities.isR2BVer(version)) return "http://hl7.org/fhir/2016May/hl7.fhir.r2b.core.tgz";
    if (VersionUtilities.isR3Ver(version)) return "http://hl7.org/fhir/STU3/hl7.fhir.r3.core.tgz";
    if (VersionUtilities.isR4Ver(version)) return "http://hl7.org/fhir/STU3/hl7.fhir.r4.core.tgz";
    if (Constants.VERSION.equals(version)) return "http://build.fhir.org/package.tgz";
    throw new Error("unknown version "+version);
  }

  private InputStream fetchFromSource(String id, String source) throws IOException {
    logDebugMessage(LogCategory.INIT, "Fetch "+id+" package from "+source);
    URL url = new URL(source+"?nocache=" + System.currentTimeMillis());
    URLConnection c = url.openConnection();
    return c.getInputStream();
  }

  private Parameters makeExpProfile() {
    Parameters ep  = new Parameters();
    ep.addParameter("profile-url", "dc8fd4bc-091a-424a-8a3b-6198ef146891"); // change this to blow the cache
    // all defaults....
    return ep;
  }

  private void loadIg(ImplementationGuideDependsOnComponent dep, int index) throws Exception {
    String name = dep.getId();
    if (!dep.hasId()) {
      logMessage("Dependency '"+idForDep(dep)+"' has no id, so can't be referred to in markdown in the IG");
      name = "u"+Utilities.makeUuidLC().replace("-", "");
    }
    if (!isValidIGToken(name))
      throw new Exception("IG Name must be a valid token ("+name+")");
    String canonical = determineCanonical(dep.getUri(), "ImplementationGuide.dependency["+index+"].url");
    String packageId = dep.getPackageId();
    if (Utilities.noString(packageId))
      packageId = pcm.getPackageId(canonical);
    if (Utilities.noString(canonical) && !Utilities.noString(packageId))
      canonical = pcm.getPackageUrl(packageId);
    if (Utilities.noString(canonical))
      throw new Exception("You must specify a canonical URL for the IG "+name);
    String igver = dep.getVersion();
    if (Utilities.noString(igver))
      throw new Exception("You must specify a version for the IG "+packageId+" ("+canonical+")");
    
    NpmPackage pi = packageId == null ? null : pcm.loadPackageFromCacheOnly(packageId, igver);
    if (pi != null)
      npmList.add(pi);
    if (pi == null) {
      pi = resolveDependency(canonical, packageId, igver);
      if (pi == null) {
        if (Utilities.noString(packageId))
          throw new Exception("Package Id for guide at "+canonical+" is unknown (contact FHIR Product Director");
        else
          throw new Exception("Unknown Package "+packageId+"#"+igver);
      }
    }
    logDebugMessage(LogCategory.INIT, "Load "+name+" ("+canonical+") from "+packageId+"#"+igver);

    if (dep.hasUri() && !dep.getUri().contains("/ImplementationGuide/")) {
      String cu = getIgUri(pi);
      if (cu != null) {
        errors.add(new ValidationMessage(Source.Publisher, IssueType.INFORMATIONAL, "ImplementationGuide.dependency["+index+"].url", 
            "The correct canonical URL for this dependency is "+cu, IssueSeverity.INFORMATION));
      }
    }
    String webref = pi.getWebLocation();
    webref = PackageHacker.fixPackageUrl(webref);

    SpecMapManager igm = pi.hasFile("other", "spec.internals") ?  new SpecMapManager( TextFile.streamToBytes(pi.load("other", "spec.internals")), pi.fhirVersion()) : SpecMapManager.createSpecialPackage(pi);
    igm.setName(name);
    igm.setBase(canonical);
    igm.setBase2(PackageHacker.fixPackageUrl(pi.url()));
    specMaps.add(igm);
    if (!VersionUtilities.versionsCompatible(version, igm.getVersion())) {
      log("Version mismatch. This IG is version "+version+", while the IG '"+name+"' is from version "+igm.getVersion()+" (will try to run anyway)");
    }
    
    loadFromPackage(name, canonical, pi, webref, igm);
    
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

  private String idForDep(ImplementationGuideDependsOnComponent dep) {
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
      JsonObject json = JsonTrackingParser.parseJson(pi.loadResource(rs));
      if (json.has("packageId") && json.get("packageId").getAsString().equals(pi.name()) && json.has("url")) {
        return json.get("url").getAsString();
      }
    }
    return null;
  }



  public void loadFromPackage(String name, String canonical, NpmPackage pi, String webref, SpecMapManager igm) throws IOException {
    for (String dep : pi.dependencies()) {
      if (!context.hasPackage(dep)) {        
        String coreVersion = VersionUtilities.getVersionForPackage(dep);
        if (coreVersion != null) {
          log("Ignore Core Dependency on FHIR version "+coreVersion+", from package '"+pi.name()+"#"+pi.version()+"'");
        } else {
          NpmPackage dpi = pcm.loadPackage(dep);
          if (dpi == null) {
            logDebugMessage(LogCategory.CONTEXT, "Unable to find package dependency "+dep+". Will proceed, but likely to be be errors in qz.html etc");
          } else {
            if (!VersionUtilities.versionsCompatible(version, pi.fhirVersion())) {
              log("Version mismatch. This IG is for FHIR version "+version+", while the package '"+pi.name()+"#"+pi.version()+"' is for FHIR version "+pi.fhirVersion()+" (will ignore that and try to run anyway)");
            }
            logDebugMessage(LogCategory.PROGRESS, "Load package dependency "+dep);
            SpecMapManager smm = dpi.hasFile("other", "spec.internals") ?  new SpecMapManager(TextFile.streamToBytes(dpi.load("other", "spec.internals")), dpi.fhirVersion()) : SpecMapManager.createSpecialPackage(dpi);
            smm.setName(dpi.name());
            smm.setBase(dpi.canonical());
            smm.setBase2(PackageHacker.fixPackageUrl(dpi.url()));
            specMaps.add(smm);
            loadFromPackage(dpi.title(), dpi.canonical(), dpi, dpi.url(), smm);          
          }
        }
      }
    }
    IContextResourceLoader loader = new PublisherLoader(pi, igm, webref, igpkp).makeLoader();
    context.loadFromPackage(pi, loader);
  }

  private void loadIg(JsonObject dep) throws Exception {
    String name = str(dep, "name");
    if (!isValidIGToken(name))
      throw new Exception("IG Name must be a valid token ("+name+")");
    String canonical = ostr(dep, "location");
    String igver = ostr(dep, "version");
    if (Utilities.noString(igver))
      throw new Exception("You must specify a version for the IG "+name+" ("+canonical+")");
    String packageId = ostr(dep, "package");
    if (Utilities.noString(packageId))
      packageId = pcm.getPackageId(canonical);
    if (Utilities.noString(canonical) && !Utilities.noString(packageId))
      canonical = pcm.getPackageUrl(packageId);
    
    NpmPackage pi = packageId == null ? null : pcm.loadPackageFromCacheOnly(packageId, igver);
    if (pi != null)
      npmList.add(pi);
    if (pi == null) {
      if (Utilities.noString(canonical))
        throw new Exception("You must specify a canonical URL for the IG "+name);
      pi = resolveDependency(canonical, packageId, igver);
      if (pi == null) {
        if (Utilities.noString(packageId))
          throw new Exception("Package Id for guide at "+canonical+" is unknown (contact FHIR Product Director");
        else
          throw new Exception("Unknown Package "+packageId+"#"+igver);
      }
    }
    if (packageId == null) {
      packageId = pi.name();
    }
    if (Utilities.noString(canonical)) {
      canonical = pi.canonical();
    }
  
    log("Load "+name+" ("+canonical+") from "+packageId+"#"+igver);
    if (ostr(dep, "package") == null && packageId != null)
      dep.addProperty("package", packageId);

    String webref = pi.getWebLocation();
    String location = dep.has("location") ? dep.get("location").getAsString() : ""; 
    if (location.startsWith(".."))
      webref = location;
    webref = PackageHacker.fixPackageUrl(webref);
    
    String ver = pi.fhirVersion();
    SpecMapManager igm = new SpecMapManager(TextFile.streamToBytes(pi.load("other", "spec.internals")), ver);
    igm.setName(name);
    igm.setBase2(PackageHacker.fixPackageUrl(webref));
    igm.setBase(canonical);
    specMaps.add(igm);
    if (!VersionUtilities.versionsCompatible(version, igm.getVersion())) {
      log("Version mismatch. This IG is for FHIR version "+version+", while the IG '"+pi.name()+"#"+pi.version()+"' is for FHIR version "+igm.getVersion()+" (will try to run anyway)");
    }
    
    loadFromPackage(name, canonical, pi, webref, igm);
    jsonDependencies .add(new JsonDependency(name, canonical, pi.name(), pi.version()));
  }


  private NpmPackage resolveDependency(String canonical, String packageId, String igver) throws Exception {
    if (packageId != null) 
      return pcm.loadPackage(packageId, igver);
    
    JsonObject pl;
    logDebugMessage(LogCategory.INIT, "Fetch Package history from "+Utilities.pathURL(canonical, "package-list.json"));
    try {
      pl = JsonTrackingParser.fetchJson(Utilities.pathURL(canonical, "package-list.json"));
    } catch (Exception e) {
      return null;
    }
    if (!canonical.equals(pl.get("canonical").getAsString()))
      throw new Exception("Canonical mismatch fetching package list for "+canonical+"#"+igver+", package-list.json says "+pl.get("canonical"));
    for (JsonElement e : pl.getAsJsonArray("list")) {
      JsonObject o = (JsonObject) e;
      if (igver.equals(o.get("version").getAsString())) {
        InputStream src = fetchFromSource(pl.get("package-id").getAsString()+"-"+igver, Utilities.pathURL(o.get("path").getAsString(), "package.tgz"));
        return pcm.addPackageToCache(pl.get("package-id").getAsString(), igver, src, Utilities.pathURL(o.get("path").getAsString(), "package.tgz"));
      }
    }
    return null;
  }

 
  private String fetchFromURL(String source, String name) throws Exception {
    String filename = Utilities.path(vsCache, name+".cache");
    if (new File(filename).exists())
      return filename;

    if (!source.endsWith("validator.pack"))
      source = Utilities.pathURL(source, "validator.pack");
    try {
      URL url = new URL(source+"?nocache=" + System.currentTimeMillis());
      URLConnection c = url.openConnection();
      byte[] cnt = IOUtils.toByteArray(c.getInputStream());
      TextFile.bytesToFile(cnt, filename);
      return filename;
    } catch (Exception e) {
      throw new Exception("Unable to load definitions from URL '"+source+"': "+e.getMessage(), e);
    }
  }

  private static String getCurentDirectory() {
    String currentDirectory;
    File file = new File(".");
    currentDirectory = file.getAbsolutePath();
    return currentDirectory;
  }

  private boolean checkDir(String dir) throws Exception {
    return checkDir(dir, false);
  }
  
  private boolean checkDir(String dir, boolean emptyOk) throws Exception {
    FetchState state = fetcher.check(dir);
    if (state == FetchState.NOT_FOUND) {
      if (emptyOk)
        return false;
      throw new Exception(String.format("Error: folder %s not found", dir));
    } else if (state == FetchState.FILE)
      throw new Exception(String.format("Error: Output must be a folder (%s)", dir));
    return true;
  }

  private void checkFile(String fn) throws Exception {
    FetchState state = fetcher.check(fn);
    if (state == FetchState.NOT_FOUND)
      throw new Exception(String.format("Error: file %s not found", fn));
    else if (state == FetchState.DIR)
      throw new Exception(String.format("Error: Output must be a file (%s)", fn));
  }

  private void forceDir(String dir) throws Exception {
    File f = new File(dir);
    if (!f.exists())
      Utilities.createDirectory(dir);
    else if (!f.isDirectory())
      throw new Exception(String.format("Error: Output must be a folder (%s)", dir));
  }

  private boolean checkMakeFile(byte[] bs, String path, Set<String> outputTracker) throws IOException {
    logDebugMessage(LogCategory.GENERATE, "Check Generate "+path);
    if (firstExecution) {
      String s = path.toLowerCase();
      if (allOutputs.contains(s))
        throw new Error("Error generating build: the file "+path+" is being generated more than once (may differ by case)");
      allOutputs.add(s);
    }
    outputTracker.add(path);
    File f = new CSFile(path);
    byte[] existing = null;
    if (f.exists())
      existing = TextFile.fileToBytes(path);
    if (!Arrays.equals(bs, existing)) {
      TextFile.bytesToFile(bs, path);
      return true;
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
    if (Utilities.existsInList(s, "modifier.png", "mustsupport.png", "summary.png", "lock.png", "external.png", "cc0.png", "target.png", "link.svg"))
      return true;
    
    return false;
  }

  public SpecMapManager loadSpecDetails(byte[] bs) throws IOException {
    SpecMapManager map = new SpecMapManager(bs, version);
    map.setBase(PackageHacker.fixPackageUrl(specPath));
    specMaps.add(map);
    return map;
  }


  private boolean load() throws Exception {
    validationFetcher.initOtherUrls();
    fileList.clear();
    changeList.clear();
    bndIds.clear();
    boolean needToBuild = false;
    FetchedFile igf = fetcher.fetch(igName);
    needToBuild = noteFile(IG_NAME, igf) || needToBuild;
    if (needToBuild) {
      if (sourceIg == null) // old JSON approach
        sourceIg = (ImplementationGuide) parse(igf);
      publishedIg = sourceIg.copy();
      FetchedResource igr = igf.addResource();
//      loadAsElementModel(igf, igr, null);
      igr.setResource(publishedIg);
      igr.setElement(convertToElement(publishedIg));
      igr.setId(sourceIg.getId()).setTitle(publishedIg.getName());
    } else {
      // special case; the source is updated during the build, so we track it differently
      publishedIg = sourceIg.copy();
      altMap.get(IG_NAME).getResources().get(0).setResource(publishedIg);
    }
    
    loadMappingSpaces(context.getBinaries().get("mappingSpaces.details"));
    validationFetcher.getMappingUrls().addAll(mappingSpaces.keySet());
    validationFetcher.getOtherUrls().add(publishedIg.getUrl());
    for (SpecMapManager s :  specMaps) {
      validationFetcher.getOtherUrls().add(s.getBase());
      if (s.getBase2() != null) {
        validationFetcher.getOtherUrls().add(s.getBase2());
      }
    }

    if (npmName == null)
      throw new Exception("A package name (npm-name) is required to publish implementation guides. For further information, see http://wiki.hl7.org/index.php?title=FHIR_NPM_Package_Spec#Package_name");
    if (!publishedIg.hasLicense())
      publishedIg.setLicense(licenseAsEnum());
    if (!publishedIg.hasPackageId())
      publishedIg.setPackageId(npmName);
    if (!publishedIg.hasFhirVersion())
      publishedIg.addFhirVersion(FHIRVersion.fromCode(version));
    if (!publishedIg.hasVersion() && businessVersion != null)
      publishedIg.setVersion(businessVersion);
    
    String id = npmName;
    if (npmName.startsWith("hl7.")) {
      if (!id.matches("[A-Za-z0-9\\-\\.]{1,64}"))
        throw new FHIRException("The generated ID is '"+id+"' which is not valid");
      FetchedResource r = fetchByResource("ImplementationGuide", publishedIg.getId());
      publishedIg.setId(id);
      publishedIg.setUrl(igpkp.getCanonical()+"/ImplementationGuide/"+id);
      if (r != null) { // it better be....
        r.setId(id);
        r.getElement().getNamedChild("id").setValue(id);
        r.getElement().getNamedChild("url").setValue(publishedIg.getUrl());
      }        
    } else if (!id.equals(publishedIg.getId()))
      errors.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "ImplementationGuide.id", "The Implementation Guide Resource id should be "+id, IssueSeverity.WARNING));
      
    // Cql Compile
    cql = new CqlSubSystem(npmList, binaryPaths, new LibraryLoader(version), this, context.getUcumService(), publishedIg.getPackageId(), igpkp.getCanonical());
    if (binaryPaths.size() > 0) {
      cql.execute();
    }
    fetcher.setRootDir(rootDir);
    loadedIds = new HashSet<>();
    duplicateInputResourcesDetected = false;
    // load any bundles
    if (sourceDir != null || igpkp.isAutoPath())
      needToBuild = loadResources(needToBuild, igf);
    needToBuild = loadSpreadsheets(needToBuild, igf);
    needToBuild = loadMappings(needToBuild, igf);
    needToBuild = loadBundles(needToBuild, igf);
    int i = 0;
    for (ImplementationGuideDefinitionResourceComponent res : publishedIg.getDefinition().getResource()) {
      if (!res.hasReference())
        throw new Exception("Missing source reference on a resource in the IG with the name '"+res.getName()+"' (index = "+i+")");
      i++;
      FetchedFile f = null;
      if (!bndIds.contains(res.getReference().getReference()) && !res.hasUserData("loaded.resource")) { // todo: this doesn't work for differential builds
        logDebugMessage(LogCategory.INIT, "Load "+res.getReference());
        f = fetcher.fetch(res.getReference(), igf);
        if (!f.hasTitle() && res.getName() != null)
          f.setTitle(res.getName());
        boolean rchanged = noteFile(res, f);        
        needToBuild = rchanged || needToBuild;
        if (rchanged) 
          loadAsElementModel(f, f.addResource(), res);
      }
      if (res.hasExampleCanonicalType()) {
        if (f != null && f.getResources().size()!=1)
          throw new Exception("Can't have an exampleFor unless the file has exactly one resource");
        FetchedResource r = res.hasUserData("loaded.resource") ? (FetchedResource) res.getUserData("loaded.resource") : f.getResources().get(0);
        if (r == null)
            throw new Exception("Unable to resolve example canonical " + res.getExampleCanonicalType().asStringValue());
        examples.add(r);
        String ref = res.getExampleCanonicalType().getValueAsString();
        if (Utilities.isAbsoluteUrl(ref)) {
          r.setExampleUri(ref);
        } else {
          r.setExampleUri(Utilities.pathURL(igpkp.getCanonical(), ref));
        }
        // Redo this because we now have example information
        if (f!=null)
          igpkp.findConfiguration(f, r);
      }
    }
    if (duplicateInputResourcesDetected) {
      throw new Error("Unable to continue because duplicate input resources were identified");
    }

    // load static pages
    needToBuild = loadPrePages() || needToBuild;
    needToBuild = loadPages() || needToBuild;

    if (publishedIg.getDefinition().hasPage())
      loadIgPages(publishedIg.getDefinition().getPage(), igPages);

    for (FetchedFile f: fileList) {
      for (FetchedResource r: f.getResources()) {
        resources.put(igpkp.doReplacements(igpkp.getLinkFor(r, false), r, null, null), r);
      }
    }
    
    for (JsonDependency dep : jsonDependencies) {
      ImplementationGuideDependsOnComponent d = null;
      for (ImplementationGuideDependsOnComponent t : publishedIg.getDependsOn()) {
        if (dep.getCanonical().equals(t.getUri()) || dep.getNpmId().equals(t.getPackageId())) {
          d = t;
          break;
        }
      }
      if (d == null) {
        d = publishedIg.addDependsOn();
        d.setUri(dep.getCanonical());
        d.setVersion(dep.getVersion());
        d.setPackageId(dep.getNpmId());
      } else {
        d.setVersion(dep.getVersion());
      }
    }

    for (ImplementationGuideDependsOnComponent dep : publishedIg.getDependsOn()) {
      if (!dep.hasPackageId()) {
        dep.setPackageId(pcm.getPackageId(determineCanonical(dep.getUri(), null)));
      }
      if (!dep.hasPackageId()) 
        throw new FHIRException("Unknown package id for "+dep.getUri());
    }
    npm = new NPMPackageGenerator(Utilities.path(outputDir, "package.tgz"), igpkp.getCanonical(), targetUrl(), PackageType.IG,  publishedIg, execTime.getTime(), !publishing);
    execTime = Calendar.getInstance();

    rc = new RenderingContext(context, markdownEngine, ValidationOptions.defaults(), checkAppendSlash(specPath), "", null, ResourceRendererMode.IG);
    rc.setTemplateProvider(templateProvider);
    rc.setResolver(this);    
    rc.setServices(validator.getExternalHostServices());
    rc.setDestDir(Utilities.path(tempDir));
    rc.setProfileUtilities(new ProfileUtilities(context, new ArrayList<ValidationMessage>(), igpkp));
    rc.setQuestionnaireMode(QuestionnaireRendererMode.TREE);
    rc.getCodeSystemPropList().addAll(codeSystemProps );
//    rc.setTargetVersion(pubVersion);

    if (igMode) {
      boolean failed = false;
      CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
      // sanity check: every specified resource must be loaded, every loaded resource must be specified
      for (ImplementationGuideDefinitionResourceComponent r : publishedIg.getDefinition().getResource()) {
        b.append(r.getReference().getReference());
        if (!r.hasUserData("loaded.resource")) {
          log("Resource "+r.getReference().getReference()+" not loaded");
          failed = true;
        }
      }  
      for (FetchedFile f : fileList) {
        for (FetchedResource r : f.getResources()) {
          ImplementationGuideDefinitionResourceComponent rg = findIGReference(r.fhirType(), r.getId());
          if (!"ImplementationGuide".equals(r.fhirType()) && rg == null) {
            log("Resource "+r.fhirType()+"/"+r.getId()+" not defined");
            failed = true;
          }
          if (rg != null) {
            if (!rg.hasName()) {
              if (r.getElement().hasChild("title")) {
                rg.setName(r.getElement().getChildValue("title"));
              } else if (r.getElement().hasExtension("http://hl7.org/fhir/tools/StructureDefinition/extension-title")) {
                rg.setName(r.getElement().getExtensionValue("http://hl7.org/fhir/tools/StructureDefinition/extension-title").primitiveValue());                
              }
            }
            if (!rg.hasDescription()) {
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
            if (!rg.hasExample()) {
              // If the instance declares a profile that's got the same canonical base as this IG, then the resource is an example of that profile
              Map<String, String> profiles = new HashMap<String, String>();
              if (r.getElement().hasChild("meta")) {
                for (Element p : r.getElement().getChildren("meta").get(0).getChildren("profile")) {
                  if (!profiles.containsKey(p.getValue()))
                    profiles.put(p.getValue(), p.getValue());
                }
              }
              if (r.getElement().getName().equals("Bundle")) {
                for (Element entry : r.getElement().getChildren("entry")) {
                  for (Element entres : entry.getChildren("resource")) {
                    if (entres.hasChild("meta")) {
                      for (Element p : entres.getChildren("meta").get(0).getChildren("profile")) {
                        if (!profiles.containsKey(p.getValue()))
                          profiles.put(p.getValue(), p.getValue());
                      }
                    }              
                  }
                }
              }
              for (String p : profiles.keySet()) {
                // Ideally we'd want to have *all* of the profiles listed as examples, but right now we can only have one, so we just overwrite and take the last.
                if (p.startsWith(igpkp.getCanonical()+"/StructureDefinition")) {
                  rg.setExample(new CanonicalType(p));
                  if (rg.getName()==null) {
                    String name = String.join(" - ", rg.getReference().getReference().split("/"));
                    rg.setName("Example " + name);
                  }
                  examples.add(r);
                  r.setExampleUri(p);
                  igpkp.findConfiguration(f, r);
                }
              }
            }            
          }
        }
      }
      if (failed) {
        log("Resources: "+b.toString());
        throw new Exception("Invalid - see reasons"); // if this ever happens, it's a programming issue....
      }
    }
    logDebugMessage(LogCategory.INIT, "Loaded Files: "+fileList.size());
    for (FetchedFile f : fileList) {
      logDebugMessage(LogCategory.INIT, "  "+f.getTitle()+" - "+f.getResources().size()+" Resources");
      for (FetchedResource r : f.getResources())
        logDebugMessage(LogCategory.INIT, "    "+r.fhirType()+"/"+r.getId());      
    }
    extensionTracker.scan(publishedIg);
    return needToBuild;
  }

  private FetchedResource fetchByResource(String type, String id) {
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals(type) && r.getId().equals(id))
          return r;
      }
    }
    return null;
  }


  private String targetUrl() {
    if (mode == null)
      return "file://"+outputDir;
    switch (mode) {
    case AUTOBUILD: return targetOutput == null ? "https://build.fhir.org/ig/[org]/[repo]" : targetOutput;
    case MANUAL: return "file://"+outputDir;
    case WEBSERVER: return "http://unknown";
    case PUBLICATION: return targetOutput;
    default: return igpkp.getCanonical();
    }
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


  private void templateBeforeGenerate() throws IOException, FHIRException {
    if (template != null) {
      if (debug) {
        waitForInput("before OnGenerate");
      }
      Session tts = tt.start("template");
      List<String> newFileList = new ArrayList<String>();
      checkOutcomes(template.beforeGenerateEvent(publishedIg, tempDir, otherFilesRun, newFileList));
      for (String newFile: newFileList) {
        if (!newFile.isEmpty()) {
          try {
            FetchedFile f = fetcher.fetch(Utilities.path(repoRoot, newFile));
            String dir = Utilities.getDirectoryForFile(f.getPath());
            String relative = tempDir.length() > dir.length() ? "" : dir.substring(tempDir.length());
            if (relative.length() > 0)
              relative = relative.substring(1);
            f.setRelativePath(f.getPath().substring(dir.length()+1));
            PreProcessInfo ppinfo = new PreProcessInfo(null, relative);
            loadPrePage(f, ppinfo);
          } catch (Exception e) {
            throw new FHIRException(e.getMessage());
          }
        }
      }
      igPages.clear();
      if (publishedIg.getDefinition().hasPage()) {
        loadIgPages(publishedIg.getDefinition().getPage(), igPages);
      }
      tts.end();
      if (debug) {
        waitForInput("after OnGenerate");
      }
    }
    cleanUpExtensions(publishedIg);
  }

  private void cleanUpExtensions(ImplementationGuide ig) {
   ToolingExtensions.removeExtension(ig, IGHelper.EXT_SPREADSHEET);
   ToolingExtensions.removeExtension(ig, IGHelper.EXT_BUNDLE);
   for (ImplementationGuideDefinitionResourceComponent r : ig.getDefinition().getResource())
     ToolingExtensions.removeExtension(r, IGHelper.EXT_RESOURCE_INFO);
  }


  private void checkOutcomes(Map<String, List<ValidationMessage>> outcomes) {
    if (outcomes == null)
      return;
    
    for (String s : outcomes.keySet()) {
      FetchedFile f = getFileForFile(s);
      if (f == null)
        errors.addAll(outcomes.get(s));
      else
        f.getErrors().addAll(outcomes.get(s));        
    }    
  }


  private void templateBeforeJekyll() throws IOException, FHIRException {
    if (template != null) {
      if (debug)
        waitForInput("before OnJekyll");
      Session tts = tt.start("template");
      List<String> newFileList = new ArrayList<String>();
      checkOutcomes(template.beforeJekyllEvent(publishedIg, newFileList));
      tts.end();
      if (debug)
        waitForInput("after OnJekyll");
    }
  }
  
  private void templateOnCheck() throws IOException, FHIRException {
    if (template != null) {
      if (debug)
        waitForInput("before OnJekyll");
      Session tts = tt.start("template");
      checkOutcomes(template.onCheckEvent(publishedIg));
      tts.end();
      if (debug)
        waitForInput("after OnJekyll");
    }
  }
  
  private void waitForInput(String string) throws IOException {
    System.out.print("At point '"+string+"' - press enter to continue: ");
    while (System.in.read() != 10) {};
  }


  private void loadIgPages(ImplementationGuideDefinitionPageComponent page, HashMap<String, ImplementationGuideDefinitionPageComponent> map) throws FHIRException {
    if (page.hasName() && page.hasNameUrlType())
      map.put(page.getNameUrlType().getValue(), page);
    for (ImplementationGuideDefinitionPageComponent childPage: page.getPage()) {
      loadIgPages(childPage, map);
    }
  }

  private boolean loadPrePages() throws Exception {
    boolean changed = false;
    if (prePagesDirs.isEmpty())
      return false;

    for (String prePagesDir : prePagesDirs) {
      FetchedFile dir = fetcher.fetch(prePagesDir);
      if (dir != null) {
        dir.setRelativePath("");
        if (!dir.isFolder())
          throw new Exception("pre-processed page reference is not a folder");
        if (loadPrePages(dir, dir.getStatedPath()))
          changed = true;
      }
    }
    return changed;
  }

  private boolean loadPrePages(FetchedFile dir, String basePath) throws Exception {
    System.out.println("loadPrePages from " + dir+ " as "+basePath);
    boolean changed = false;
    PreProcessInfo ppinfo = preProcessInfo.get(basePath);
    if (ppinfo==null) {
      System.out.println("PreProcessInfo hash:" + preProcessInfo.toString());
      throw new Exception("Unable to find preProcessInfo for basePath: " + basePath);
    }
    if (!altMap.containsKey("pre-page/"+dir.getPath())) {
      changed = true;
      altMap.put("pre-page/"+dir.getPath(), dir);
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
      FetchedFile f = fetcher.fetch(link);
      System.out.println("find pre-page "+f.getPath()+" at "+link+" from "+basePath);
      f.setRelativePath(f.getPath().substring(basePath.length()+1));
      if (f.isFolder())
        changed = loadPrePages(f, basePath) || changed;
      else
        changed = loadPrePage(f, ppinfo) || changed;
    }
    return changed;
  }

  private boolean loadPrePage(FetchedFile file, PreProcessInfo ppinfo) {
    FetchedFile existing = altMap.get("pre-page/"+file.getPath());
    if (existing == null || existing.getTime() != file.getTime() || existing.getHash() != file.getHash()) {
      file.setProcessMode(ppinfo.hasXslt() && !file.getPath().endsWith(".md") ? FetchedFile.PROCESS_XSLT : FetchedFile.PROCESS_NONE);
      file.setXslt(ppinfo.getXslt());
      if (ppinfo.hasRelativePath())
        file.setRelativePath(ppinfo.getRelativePath() + File.separator + file.getRelativePath());
      addFile(file);
      altMap.put("pre-page/"+file.getPath(), file);
      return true;
    } else {
      return false;
    }
  }

  private boolean loadPages() throws Exception {
    boolean changed = false;
    for (String pagesDir: pagesDirs) {
      FetchedFile dir = fetcher.fetch(pagesDir);
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
    if (!altMap.containsKey("page/"+dir.getPath())) {
      changed = true;
      altMap.put("page/"+dir.getPath(), dir);
      dir.setProcessMode(FetchedFile.PROCESS_NONE);
      addFile(dir);
    }
    for (String link : dir.getFiles()) {
      FetchedFile f = fetcher.fetch(link);
      f.setRelativePath(f.getPath().substring(basePath.length()+1));
      if (f.isFolder())
        changed = loadPages(f, basePath) || changed;
      else
        changed = loadPage(f) || changed;
    }
    return changed;
  }

  private boolean loadPage(FetchedFile file) {
    FetchedFile existing = altMap.get("page/"+file.getPath());
    if (existing == null || existing.getTime() != file.getTime() || existing.getHash() != file.getHash()) {
      file.setProcessMode(FetchedFile.PROCESS_NONE);
      addFile(file);
      altMap.put("page/"+file.getPath(), file);
      return true;
    } else {
      return false;
    }
  }

  private boolean loadBundles(boolean needToBuild, FetchedFile igf) throws Exception {
    for (String be : bundles) {
      needToBuild = loadBundle(be, needToBuild, igf);
    }
    return needToBuild;
  }

  private boolean loadBundle(String name, boolean needToBuild, FetchedFile igf) throws Exception {
    FetchedFile f = fetcher.fetch(new Reference().setReference("Bundle/"+name), igf);
    boolean changed = noteFile("Bundle/"+name, f);
    if (changed) {
      f.setBundle(new FetchedResource());
      f.setBundleType(FetchedBundleType.NATIVE);
      loadAsElementModel(f, f.getBundle(), null);
      List<Element> entries = new ArrayList<Element>();
      f.getBundle().getElement().getNamedChildren("entry", entries);
      int i = -1;
      for (Element bnde : entries) {
        i++;
        Element res = bnde.getNamedChild("resource"); 
        if (res == null) {
          f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, "Bundle.element["+i+"]", "All entries must have resources when loading a bundle", IssueSeverity.ERROR));
        } else {
          checkResourceUnique(res.fhirType()+"/"+res.getIdBase());
          FetchedResource r = f.addResource();
          r.setElement(res);
          r.setId(res.getIdBase());
          List<Element> profiles = new ArrayList<Element>();
          Element meta = res.getNamedChild("meta");
          if (meta != null)
            meta.getNamedChildren("profile", profiles);
          for (Element p : profiles)
            r.getStatedProfiles().add(p.primitiveValue());
          r.setTitle(r.getElement().getChildValue("name"));
          igpkp.findConfiguration(f, r);
        }
      }
    } else
      f = altMap.get("Bundle/"+name);
    ImplementationGuideDefinitionGroupingComponent pck = null;
    for (FetchedResource r : f.getResources()) {
      bndIds.add(r.fhirType()+"/"+r.getId());
      ImplementationGuideDefinitionResourceComponent res = findIGReference(r.fhirType(), r.getId());
      if (res == null) {
        if (pck == null) {
          pck = publishedIg.getDefinition().addGrouping().setName(f.getTitle());
          pck.setId(name);
        }
        res = publishedIg.getDefinition().addResource();
        res.setGroupingId(pck.getId());
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
      res.setUserData("loaded.resource", r);
      r.setResEntry(res);
    }
    return changed || needToBuild;
  }

  private boolean loadResources(boolean needToBuild, FetchedFile igf) throws Exception { // igf is not currently used, but it was about relative references? 
    List<FetchedFile> resources = fetcher.scan(sourceDir, context, igpkp.isAutoPath());
    for (FetchedFile ff : resources) {
      if (!ff.matches(igf))
        needToBuild = loadResource(needToBuild, ff);
    }
    return needToBuild;
  }

  private boolean loadResource(boolean needToBuild, FetchedFile f) throws Exception {
    logDebugMessage(LogCategory.INIT, "load "+f.getPath());
    boolean changed = noteFile(f.getPath(), f);
    if (changed) {
      loadAsElementModel(f, f.addResource(), null);
    }
    for (FetchedResource r : f.getResources()) {
      ImplementationGuideDefinitionResourceComponent res = findIGReference(r.fhirType(), r.getId());
      if (res == null) {
        res = publishedIg.getDefinition().addResource();
        if (!res.hasName())
          res.setName(r.getTitle());
        if (!res.hasDescription())
          res.setDescription(((CanonicalResource)r.getResource()).getDescription().trim());
        res.setReference(new Reference().setReference(r.fhirType()+"/"+r.getId()));
      }
      res.setUserData("loaded.resource", r);
      r.setResEntry(res);
    }
    return changed || needToBuild;
  }


  private boolean loadMappings(boolean needToBuild, FetchedFile igf) throws Exception {
    for (String s : mappings) {
      needToBuild = loadMapping(s, needToBuild, igf);
    }
    return needToBuild;
  }

  private boolean loadMapping(String name, boolean needToBuild, FetchedFile igf) throws Exception {
    if (name.startsWith("!"))
      return false;
    FetchedFile f = fetcher.fetchResourceFile(name);
    boolean changed = noteFile("Mapping/"+name, f);
    if (changed) {
      logDebugMessage(LogCategory.INIT, "load "+f.getPath());
      MappingSheetParser p = new MappingSheetParser();
      p.parse(new ByteArrayInputStream(f.getSource()), f.getRelativePath());
      ConceptMap cm = p.getConceptMap();
      FetchedResource r = f.addResource();
      r.setResource(cm);
      r.setId(cm.getId());
      r.setElement(convertToElement(cm));
      r.setTitle(r.getElement().getChildValue("name"));
      igpkp.findConfiguration(f, r);
    } else {
      f = altMap.get("Mapping/"+name);
    }
    return changed || needToBuild;
  }

  private boolean loadSpreadsheets(boolean needToBuild, FetchedFile igf) throws Exception {
    Set<String> knownValueSetIds = new HashSet<>();
    for (String s : spreadsheets) {
      needToBuild = loadSpreadsheet(s, needToBuild, igf, knownValueSetIds);
    }
    return needToBuild;
  }

  private boolean loadSpreadsheet(String name, boolean needToBuild, FetchedFile igf, Set<String> knownValueSetIds) throws Exception {
    if (name.startsWith("!"))
      return false;

    FetchedFile f = fetcher.fetchResourceFile(name);
    boolean changed = noteFile("Spreadsheet/"+name, f);
    if (changed) {
      f.getValuesetsToLoad().clear();
      logDebugMessage(LogCategory.INIT, "load "+f.getPath());
      Bundle bnd = new IgSpreadsheetParser(context, execTime, igpkp.getCanonical(), f.getValuesetsToLoad(), firstExecution, mappingSpaces, knownValueSetIds).parse(f);
      f.setBundle(new FetchedResource());
      f.setBundleType(FetchedBundleType.SPREADSHEET);
      f.getBundle().setResource(bnd);
      for (BundleEntryComponent b : bnd.getEntry()) {
        checkResourceUnique(b.getResource().fhirType()+"/"+b.getResource().getIdBase());
        FetchedResource r = f.addResource();
        r.setResource(b.getResource());
        r.setId(b.getResource().getId());
        r.setElement(convertToElement(r.getResource()));
        r.setTitle(r.getElement().getChildValue("name"));
        igpkp.findConfiguration(f, r);
      }
    } else {
      f = altMap.get("Spreadsheet/"+name);
    }

    for (String id : f.getValuesetsToLoad().keySet()) {
      if (!knownValueSetIds.contains(id)) {
        String vr = f.getValuesetsToLoad().get(id);
        checkResourceUnique("ValueSet/"+id);

        FetchedFile fv = fetcher.fetchFlexible(vr);
        boolean vrchanged = noteFile("sp-ValueSet/"+vr, fv);
        if (vrchanged) {
          loadAsElementModel(fv, fv.addResource(), null);
          checkImplicitResourceIdentity(id, fv);
        }
        knownValueSetIds.add(id);
        // ok, now look for an implicit code system with the same name
        boolean crchanged = false;
        String cr = vr.replace("valueset-", "codesystem-");
        if (!cr.equals(vr)) {
          if (fetcher.canFetchFlexible(cr)) {
            fv = fetcher.fetchFlexible(cr);
            crchanged = noteFile("sp-CodeSystem/"+vr, fv);
            if (crchanged) {
              loadAsElementModel(fv, fv.addResource(), null);
              checkImplicitResourceIdentity(id, fv);
            }
          }
        }
        changed = changed || vrchanged || crchanged;
      }
    }
    ImplementationGuideDefinitionGroupingComponent pck = null;
    for (FetchedResource r : f.getResources()) {
      bndIds.add(r.fhirType()+"/"+r.getId());
      ImplementationGuideDefinitionResourceComponent res = findIGReference(r.fhirType(), r.getId()); 
      if (res == null) {
        if (pck == null) {
          pck = publishedIg.getDefinition().addGrouping().setName(f.getTitle());
          pck.setId(name);
        }
        res = publishedIg.getDefinition().addResource();
        res.setGroupingId(pck.getId());
        if (!res.hasName())
          res.setName(r.getTitle());
        if (!res.hasDescription())
          res.setDescription(((CanonicalResource)r.getResource()).getDescription().trim());
        res.setReference(new Reference().setReference(r.fhirType()+"/"+r.getId()));
      }
      res.setUserData("loaded.resource", r);
      r.setResEntry(res);
    }
    return changed || needToBuild;
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
    if (!rurl.startsWith(igpkp.getCanonical()))
      throw new Exception("base/ resource url mismatch: "+igpkp.getCanonical()+" vs "+rurl);
  }


  private String tail(String url) {
    return url.substring(url.lastIndexOf("/")+1);
  }

  private List<String> metadataResourceNames() {
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
    res.add("CapabilityStatement2");
    res.add("StructureMap");
    res.add("ActivityDefinition");
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
    res.add("TestScript");
    return res;
  }
  
  private void loadConformance() throws Exception {
    for (String s : metadataResourceNames()) 
      scan(s);
    loadInfo();
    for (String s : metadataResourceNames()) 
      load(s);
    log("Generating Snapshots");
    generateSnapshots();
    log("Generating Narratives");
    generateNarratives();
    log("Validating Conformance Resources");
    for (String s : metadataResourceNames()) 
      validate(s);
    
    loadLists();
    checkConformanceResources();
    generateLogicalMaps();
//    load("StructureMap"); // todo: this is a problem...
    generateAdditionalExamples();
    executeTransforms();
    validateExpressions();
    errors.addAll(cql.getGeneralErrors());
    scanForUsageStats();
  }

  private void validate(String type) throws Exception {
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals(type)) {
          logDebugMessage(LogCategory.PROGRESS, "validate res: "+r.fhirType()+"/"+r.getId());
          if (!r.isValidated()) {
            validate(f, r);
          }
        }
      }
    }
  }

  private void loadInfo() {
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResEntry() != null) {
          ToolingExtensions.setStringExtension(r.getResEntry(), IGHelper.EXT_RESOURCE_INFO, r.fhirType());
        }
      }
    }
  }

  private void scanForUsageStats() {
    logDebugMessage(LogCategory.PROGRESS, "scanForUsageStats");
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("StructureDefinition")) 
          extensionTracker.scan((StructureDefinition) r.getResource());
        extensionTracker.scan(r.getElement(), f.getName());
      }
    }
  }


  private void checkConformanceResources() throws IOException {
    logDebugMessage(LogCategory.PROGRESS, "check profiles & code systems");
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("StructureDefinition")) {
          logDebugMessage(LogCategory.PROGRESS, "process profile: "+r.getId());
          StructureDefinition sd = (StructureDefinition) r.getResource();
          f.getErrors().addAll(pvalidator.validate(sd, false));
          checkJurisdiction(f, (CanonicalResource) r.getResource(), IssueSeverity.ERROR, "must");
        } else if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
          checkJurisdiction(f, (CanonicalResource) r.getResource(), IssueSeverity.WARNING, "should");
        }
        if (r.fhirType().equals("CodeSystem")) {
          logDebugMessage(LogCategory.PROGRESS, "process CodeSystem: "+r.getId());
          CodeSystem cs = (CodeSystem) r.getResource();
          f.getErrors().addAll(csvalidator.validate(cs, false));
        }
      }
    }
    logDebugMessage(LogCategory.PROGRESS, "check profiles & code systems");
    Session tts = tt.start("realm-rules");
    realmRules.startChecks(publishedIg);
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("StructureDefinition")) {
          StructureDefinition sd = (StructureDefinition) r.getResource();
          realmRules.checkSD(f, sd);
        } else if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
          realmRules.checkCR(f, (CanonicalResource) r.getResource());
        }
        if (r.fhirType().equals("CodeSystem")) {
          logDebugMessage(LogCategory.PROGRESS, "process CodeSystem: "+r.getId());
          CodeSystem cs = (CodeSystem) r.getResource();
          f.getErrors().addAll(csvalidator.validate(cs, false));
        }
      }
    }
    realmRules.finishChecks();
    tts.end();
    logDebugMessage(LogCategory.PROGRESS, "check profiles & code systems");
    tts = tt.start("previous-version");
    previousVersionComparator.startChecks(publishedIg);
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
          previousVersionComparator.check((CanonicalResource) r.getResource());
        }
      }
    }
    previousVersionComparator.finishChecks();
    tts.end();
  }
  
  private RealmBusinessRules makeRealmBusinessRules() {
    if (expectedJurisdiction != null && expectedJurisdiction.getCode().equals("US")) {
      return new USRealmBusinessRules(context, version, tempDir, igpkp.getCanonical(), igpkp);
    } else {
      return new NullRealmBusinessRules(igrealm);
    }
  }

  private PreviousVersionComparator makePreviousVersionComparator() throws IOException {
    if (isTemplate()) {
      return null;
    }
    if (comparisonVersions == null) {
      comparisonVersions = new ArrayList<>();
      comparisonVersions.add("{last}");
    }
    return new PreviousVersionComparator(context, version, rootDir, tempDir, igpkp.getCanonical(), igpkp, logger, comparisonVersions);
  }

  private void checkJurisdiction(FetchedFile f, CanonicalResource resource, IssueSeverity error, String verb) {
    if (expectedJurisdiction != null) {
      boolean ok = false;
      for (CodeableConcept cc : resource.getJurisdiction()) {
        ok = ok || cc.hasCoding(expectedJurisdiction);
      }
      if (!ok) {
        f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, resource.fhirType()+".jurisdiction", "The resource "+verb+" declare its jurisdiction to match the package id ("+npmName+", jurisdiction = "+expectedJurisdiction.toString()+") (for FSH: 'jurisdiction: "+toFSH(expectedJurisdiction)+"')",
            error).setMessageId(I18nConstants.RESOURCE_JURISDICTION_MISMATCH));
      }
    }
  }

  private String toFSH(Coding c) {
    StringBuilder b = new StringBuilder();
    b.append(c.getSystem());
    b.append("#");
    b.append(c.getCode());
    b.append(" \"");
    b.append(c.getDisplay());
    b.append("\"");
    return b.toString();
  }

  private void executeTransforms() throws FHIRException, Exception {
    if (doTransforms) {
      MappingServices services = new MappingServices(context, igpkp.getCanonical());
      StructureMapUtilities utils = new StructureMapUtilities(context, services, igpkp);

      // ok, our first task is to generate the profiles
      for (FetchedFile f : changeList) {
        List<StructureMap> worklist = new ArrayList<StructureMap>();
        for (FetchedResource r : f.getResources()) {
          if (r.getResource() != null && r.getResource() instanceof StructureDefinition) {
            List<StructureMap> transforms = context.findTransformsforSource(((StructureDefinition) r.getResource()).getUrl());
            worklist.addAll(transforms);
          }
        }

        for (StructureMap map : worklist) {
          StructureMapAnalysis analysis = utils.analyse(null, map);
          map.setUserData("analysis", analysis);
          for (StructureDefinition sd : analysis.getProfiles()) {
            FetchedResource nr = new FetchedResource();
            nr.setElement(convertToElement(sd));
            nr.setId(sd.getId());
            nr.setResource(sd);
            nr.setTitle("Generated Profile (by Transform)");
            f.getResources().add(nr);
            igpkp.findConfiguration(f, nr);
            sd.setUserData("path", igpkp.getLinkFor(nr, true));
            generateSnapshot(f, nr, sd, true);
          }
        }
      }

      for (FetchedFile f : changeList) {
        Map<FetchedResource, List<StructureMap>> worklist = new HashMap<FetchedResource, List<StructureMap>>();
        for (FetchedResource r : f.getResources()) {
          List<StructureMap> transforms = context.findTransformsforSource(r.getElement().getProperty().getStructure().getUrl());
          if (transforms.size() > 0) {
            worklist.put(r, transforms);
          }
        }
        for (Entry<FetchedResource, List<StructureMap>> t : worklist.entrySet()) {
          int i = 0;
          for (StructureMap map : t.getValue()) {
            boolean ok = true;
            String tgturl = null;
            for (StructureMapStructureComponent st : map.getStructure()) {
              if (st.getMode() == StructureMapModelMode.TARGET) {
                if (tgturl == null)
                  tgturl = st.getUrl();
                else
                  ok = false;
              }
            }
            if (ok) {
              Resource target = new Bundle().setType(BundleType.COLLECTION);
              if (tgturl != null) {
                StructureDefinition tsd = context.fetchResource(StructureDefinition.class, tgturl);
                if (tsd == null)
                  throw new Exception("Unable to find definition "+tgturl);
                target = ResourceFactory.createResource(tsd.getType());
              }
              if (t.getValue().size() > 1)
                target.setId(t.getKey().getId()+"-map-"+Integer.toString(i));
              else
                target.setId(t.getKey().getId()+"-map");
              i++;
              services.reset();
              utils.transform(target, t.getKey().getElement(), map, target);
              FetchedResource nr = new FetchedResource();
              nr.setElement(convertToElement(target));
              nr.setId(target.getId());
              nr.setResource(target);
              nr.setTitle("Generated Example (by Transform)");
              nr.setValidateAsResource(true);
              f.getResources().add(nr);
              igpkp.findConfiguration(f, nr);
            }
          }
        }
      }
    }
  }

  private boolean noteFile(ImplementationGuideDefinitionResourceComponent key, FetchedFile file) {
    FetchedFile existing = fileMap.get(key);
    if (existing == null || existing.getTime() != file.getTime() || existing.getHash() != file.getHash()) {
      fileList.add(file);
      fileMap.put(key, file);
      addFile(file);
      return true;
    } else {
      fileList.add(existing); // this one is already parsed
      return false;
    }
  }

  private boolean noteFile(String key, FetchedFile file) {
    FetchedFile existing = altMap.get(key);
    if (existing == null || existing.getTime() != file.getTime() || existing.getHash() != file.getHash()) {
      fileList.add(file);
      altMap.put(key, file);
      addFile(file);
      return true;
    } else {
      fileList.add(existing); // this one is already parsed
      return false;
    }
  }

  private void addFile(FetchedFile file) {
//  	if (fileNames.contains(file.getPath())) {
//  		dlog("Found multiple definitions for file: " + file.getName()+ ".  Using first definition only.");
//  	} else {
  	  fileNames.add(file.getPath());
  	  if (file.getRelativePath()!=null)
  	    relativeNames.put(file.getRelativePath(), file);
  	  changeList.add(file);
//  	}
  }

  private void loadAsElementModel(FetchedFile file, FetchedResource r, ImplementationGuideDefinitionResourceComponent srcForLoad) throws Exception {
    file.getErrors().clear();
    Element e = null;

    try {        
      if (file.getContentType().contains("json")) {
        e = loadFromJson(file);
      } else if (file.getContentType().contains("xml")) {
        e = loadFromXml(file);
      } else
        throw new Exception("Unable to determine file type for "+file.getName());
    } catch (Exception ex) {
      throw new Exception("Unable to parse "+file.getName()+": " +ex.getMessage(), ex);
    }
    if (e == null)
      throw new Exception("Unable to parse "+file.getName()+": " +file.getErrors().get(0).summary());
    
    if (e != null) {
      try {
        if (!Utilities.noString(e.getIdBase())) {
          checkResourceUnique(e.fhirType()+"/"+e.getIdBase());
        }
        boolean altered = false;

        String id = e.getChildValue("id");
        if (Utilities.noString(id)) {
          if (e.hasChild("url")) {
            String url = e.getChildValue("url");
            String prefix = Utilities.pathURL(igpkp.getCanonical(), e.fhirType())+"/";
            if (url.startsWith(prefix)) {
              id = e.getChildValue("url").substring(prefix.length());
              e.setChildValue("id", id);
              altered = true;
            } 
            if (Utilities.noString(id)) {
              throw new Exception("Resource has no id in "+file.getPath()+" and canonical URL ("+url+") does not start with the IG canonical URL ("+prefix+")");
            }
          }
        }
        r.setElement(e).setId(id);
        igpkp.findConfiguration(file, r);
        if (srcForLoad == null)
          srcForLoad = findIGReference(r.fhirType(), r.getId());
        if (srcForLoad == null && !"ImplementationGuide".equals(r.fhirType())) {
          srcForLoad = publishedIg.getDefinition().addResource();
          srcForLoad.getReference().setReference(r.fhirType()+"/"+r.getId());
        }

        String ver = ToolingExtensions.readStringExtension(srcForLoad, ToolingExtensions.EXT_IGP_LOADVERSION); 
        if (ver == null)
          ver = r.getConfig() == null ? null : ostr(r.getConfig(), "version");
        if (ver == null)
          ver = version; // fall back to global version

        // version check: for some conformance resources, they may be saved in a different vrsion from that stated for the IG. 
        // so we might need to convert them prior to loading. Note that this is different to the conversion below - we need to 
        // convert to the current version. Here, we need to convert to the stated version. Note that we need to do this after
        // the first load above because above, we didn't have enough data to get the configuration, but we do now. 
        if (!ver.equals(version)) {
          if (file.getContentType().contains("json"))
            e = loadFromJsonWithVersionChange(file, ver, version);
          else if (file.getContentType().contains("xml"))
            e = loadFromXmlWithVersionChange(file, ver, version);
          else
            throw new Exception("Unable to determine file type for "+file.getName());
          r.setElement(e);
        }
        if (srcForLoad != null) {
          srcForLoad.setUserData("loaded.resource", r);
          r.setResEntry(srcForLoad);
          if (srcForLoad.hasExampleCanonicalType()) {
            r.getElement().setUserData("profile", srcForLoad.getExampleCanonicalType().getValue());
            r.getStatedProfiles().add(srcForLoad.getExampleCanonicalType().getValue());
          }
        }
        
        r.setTitle(e.getChildValue("name"));
        Element m = e.getNamedChild("meta");
        if (m != null) {
          List<Element> profiles = m.getChildrenByName("profile");
          for (Element p : profiles)
            r.getStatedProfiles().add(p.getValue());
        }
        if ("1.0.1".equals(ver)) {
          file.getErrors().clear();
          org.hl7.fhir.dstu2.model.Resource res2 = null;
          if (file.getContentType().contains("json"))
            res2 = new org.hl7.fhir.dstu2.formats.JsonParser().parse(file.getSource());
          else if (file.getContentType().contains("xml"))
            res2 = new org.hl7.fhir.dstu2.formats.XmlParser().parse(file.getSource());
          org.hl7.fhir.r5.model.Resource res = VersionConvertor_10_50.convertResource(res2);
          e = convertToElement(res);
          r.setElement(e).setId(id).setTitle(e.getChildValue("name"));
          r.setResource(res);
        }
        if (new AdjunctFileLoader(binaryPaths, cql).replaceAttachments1(file, r, metadataResourceNames())) {
          altered = true;
        }
        if ((altered && r.getResource() != null) || (ver.equals(Constants.VERSION) && r.getResource() == null))
          r.setResource(new ObjectConverter(context).convert(r.getElement()));
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

  public void checkResourceUnique(String tid) throws Error {
    if (loadedIds.contains(tid)) {
      System.out.println("Duplicate Resource in IG: "+tid);
      duplicateInputResourcesDetected = true;
    }
    loadedIds.add(tid);
  }

  private ImplementationGuideDefinitionResourceComponent findIGReference(String type, String id) {
    for (ImplementationGuideDefinitionResourceComponent r : publishedIg.getDefinition().getResource()) {
      if (r.hasReference() && r.getReference().getReference().equals(type+"/"+id)) {
        return r;
      }
    }
    return null;
  }

  private Element loadFromXml(FetchedFile file) throws Exception {
    org.hl7.fhir.r5.elementmodel.XmlParser xp = new org.hl7.fhir.r5.elementmodel.XmlParser(context);
    xp.setAllowXsiLocation(true);
    xp.setupValidation(ValidationPolicy.EVERYTHING, file.getErrors());
    Element res = xp.parse(new ByteArrayInputStream(file.getSource()));
    if (res == null) {
      throw new Exception("Unable to parse XML for "+file.getName());
    }
    return res;
  }

  private Element loadFromJson(FetchedFile file) throws Exception {
    org.hl7.fhir.r5.elementmodel.JsonParser jp = new org.hl7.fhir.r5.elementmodel.JsonParser(context);
    jp.setupValidation(ValidationPolicy.EVERYTHING, file.getErrors());
    jp.setAllowComments(true);
    return jp.parse(new ByteArrayInputStream(file.getSource()));
  }

  private void saveToXml(FetchedFile file, Element e) throws Exception {
    org.hl7.fhir.r5.elementmodel.XmlParser xp = new org.hl7.fhir.r5.elementmodel.XmlParser(context);
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    xp.compose(e, bs, OutputStyle.PRETTY, null);
    file.setSource(bs.toByteArray());
  }

  private void saveToJson(FetchedFile file, Element e) throws Exception {
    org.hl7.fhir.r5.elementmodel.JsonParser jp = new org.hl7.fhir.r5.elementmodel.JsonParser(context);
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    jp.compose(e, bs, OutputStyle.PRETTY, null);
    file.setSource(bs.toByteArray());
  }

  private Element loadFromXmlWithVersionChange(FetchedFile file, String srcV, String dstV) throws Exception {
    InputStream src = new ByteArrayInputStream(file.getSource());
    ByteArrayOutputStream dst = new ByteArrayOutputStream();
    if (VersionUtilities.isR3Ver(srcV) && VersionUtilities.isR2BVer(dstV)) {
      org.hl7.fhir.dstu3.model.Resource r3 = new org.hl7.fhir.dstu3.formats.XmlParser().parse(src);
      org.hl7.fhir.dstu2016may.model.Resource r14 = VersionConvertor_14_30.convertResource(r3);
      new org.hl7.fhir.dstu2016may.formats.XmlParser().compose(dst, r14);
    } else if (VersionUtilities.isR3Ver(srcV) && Constants.VERSION.equals(dstV)) {
      org.hl7.fhir.dstu3.model.Resource r3 = new org.hl7.fhir.dstu3.formats.XmlParser().parse(src);
      org.hl7.fhir.r5.model.Resource r5 = VersionConvertor_30_50.convertResource(r3, false);
      new org.hl7.fhir.r5.formats.XmlParser().compose(dst, r5);
    } else if (VersionUtilities.isR4Ver(srcV) && Constants.VERSION.equals(dstV)) {
      org.hl7.fhir.r4.model.Resource r4 = new org.hl7.fhir.r4.formats.XmlParser().parse(src);
      org.hl7.fhir.r5.model.Resource r5 = VersionConvertor_40_50.convertResource(r4);
      new org.hl7.fhir.r5.formats.XmlParser().compose(dst, r5);
    } else {
      throw new Exception("Conversion from "+srcV+" to "+dstV+" is not supported yet"); // because the only know reason to do this is 3.0.1 --> 1.40
    }
    org.hl7.fhir.r5.elementmodel.XmlParser xp = new org.hl7.fhir.r5.elementmodel.XmlParser(context);
    xp.setAllowXsiLocation(true);
    xp.setupValidation(ValidationPolicy.EVERYTHING, file.getErrors());
    file.getErrors().clear();
    Element res = xp.parse(new ByteArrayInputStream(dst.toByteArray()));
    if (res == null) {
      throw new Exception("Unable to parse XML for "+file.getName());
    }
    return res;
  }

  private Element loadFromJsonWithVersionChange(FetchedFile file, String srcV, String dstV) throws Exception {
    throw new Exception("Version converting JSON resources is not supported yet"); // because the only know reason to do this is Forge, and it only works with XML
  }

  private void scan(String type) throws Exception {
    logDebugMessage(LogCategory.PROGRESS, "process type: "+type);
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals(type)) {
          String url = r.getElement().getChildValue("url");
          if (url != null) {
            validationFetcher.getOtherUrls().add(url);
          }
        }
      }
    }
  }
        
  private void loadLists() throws Exception {
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("List")) {
          ListResource l = (ListResource) convertFromElement(r.getElement());
          r.setResource(l);          
        }
      }
    }
  }
  
  private void load(String type) throws Exception {
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals(type)) {
          logDebugMessage(LogCategory.PROGRESS, "process res: "+r.fhirType()+"/"+r.getId());
          if (r.getResource() == null) {
            try {
              if (f.getBundleType() == FetchedBundleType.NATIVE) {
                r.setResource(parseInternal(f, r));
              } else {
                r.setResource(parse(f));
              }
              r.getResource().setUserData("element", r.getElement());
            } catch (Exception e) {
              throw new Exception("Error parsing "+f.getName()+": "+e.getMessage(), e);
            }
          }
          if (r.getResource() instanceof CanonicalResource) {
            CanonicalResource bc = (CanonicalResource) r.getResource();
            if (bc == null) {
              throw new Exception("Error: conformance resource "+f.getPath()+" could not be loaded");
            }
            boolean altered = false;
            if (bc.hasUrl()) {
              if (adHocTmpDir == null && !listedURLExemptions.contains(bc.getUrl()) && !isExampleResource(bc) && !bc.getUrl().equals(Utilities.pathURL(igpkp.getCanonical(), bc.fhirType(), bc.getId()))) {
                if (!bc.fhirType().equals("CapabilityStatement") || !bc.getUrl().contains("/Conformance/")) {
                  f.getErrors().add(new ValidationMessage(Source.ProfileValidator, IssueType.INVALID, bc.fhirType()+".where(url = '"+bc.getUrl()+"')", "Conformance resource "+f.getPath()+" - the canonical URL ("+Utilities.pathURL(igpkp.getCanonical(), bc.fhirType(), 
                      bc.getId())+") does not match the URL ("+bc.getUrl()+")", IssueSeverity.ERROR).setMessageId(I18nConstants.RESOURCE_CANONICAL_MISMATCH));
                  // throw new Exception("Error: conformance resource "+f.getPath()+" canonical URL ("+Utilities.pathURL(igpkp.getCanonical(), bc.fhirType(), bc.getId())+") does not match the URL ("+bc.getUrl()+")");
                }
              }
            } else if (bc.hasId()) {
              bc.setUrl(Utilities.pathURL(igpkp.getCanonical(), bc.fhirType(), bc.getId()));
            } else {
              throw new Exception("Error: conformance resource "+f.getPath()+" has neither id nor url");
            }
            if (replaceLiquidTags(bc)) {
              altered = true;
            }
            if (bc.fhirType().equals("CodeSystem")) {
              context.clearTSCache(bc.getUrl());
            }
            if (businessVersion != null) {
              if (!bc.hasVersion()) {
                altered = true;
                bc.setVersion(businessVersion);
              } else if (!bc.getVersion().equals(businessVersion)) {
                altered = true;
                bc.setVersion(businessVersion);
              }
            }
            if (contacts != null && !contacts.isEmpty()) {
              altered = true;
              bc.getContact().clear();
              bc.getContact().addAll(contacts);
            }
            if (contexts != null && !contexts.isEmpty()) {
              altered = true;
              bc.getUseContext().clear();
              bc.getUseContext().addAll(contexts);
            }
            // Todo: Enable these
            if (copyright != null && !bc.hasCopyright() && bc.supportsCopyright()) {
             altered = true;
             bc.setCopyright(copyright);
            }
            if (bc.hasCopyright() && bc.getCopyright().contains("{{{year}}}")) {
              bc.setCopyright(bc.getCopyright().replace("{{{year}}}", Integer.toString(Calendar.getInstance().get(Calendar.YEAR))));
              altered = true;
            }
            if (jurisdictions != null && !jurisdictions.isEmpty()) {
              altered = true;
              bc.getJurisdiction().clear();
              bc.getJurisdiction().addAll(jurisdictions);
            }
            if (publisher != null) {
              altered = true;
              bc.setPublisher(publisher);
            }


            if (!bc.hasDate()) {
              altered = true;
              bc.setDateElement(new DateTimeType(execTime));
            }
            if (!bc.hasStatus()) {
              altered = true;
              bc.setStatus(PublicationStatus.DRAFT);
            }
            if (new AdjunctFileLoader(binaryPaths, cql).replaceAttachments2(f, r)) {
              altered = true;
            }
            if (altered) {
              r.setElement(convertToElement(bc));
            }
            igpkp.checkForPath(f, r, bc, false);
            try {
              context.cacheResourceFromPackage(bc, new PackageVersion(publishedIg.getPackageId(), publishedIg.getVersion()));
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
            for (BundleEntryComponent be : b.getEntry()) {
              if (be.hasResource() && be.getResource().fhirType().equals(type)) {
                CanonicalResource mr = (CanonicalResource) be.getResource();
                if (mr.hasUrl()) {
                  if (!mr.hasUserData("path")) {
                    igpkp.checkForPath(f,  r,  mr, true);
                  }
                  context.cacheResourceFromPackage(mr, new PackageVersion(publishedIg.getPackageId(), publishedIg.getVersion()));
                } else
                  logDebugMessage(LogCategory.PROGRESS, "Ignoring resource "+type+"/"+mr.getId()+" in Bundle "+f.getName()+" because it has no canonical URL");

              }
            }
          }
        }
      }
    }
  }

  private boolean replaceLiquidTags(DomainResource resource) {
    if (!resource.hasText() || !resource.getText().hasDiv()) {
      return false;
    }
    Map<String, String> vars = new HashMap<>();
    vars.put("{{site.data.fhir.path}}", igpkp.specPath()+"/");
    return new LiquidEngine(context, validator.getExternalHostServices()).replaceInHtml(resource.getText().getDiv(), vars);
  }



  private boolean isExampleResource(CanonicalResource mr) {
    for (ImplementationGuideDefinitionResourceComponent ir : publishedIg.getDefinition().getResource()) {
      if (isSameResource(ir, mr)) {
        return ir.hasExample();
      }
    }
    return false;
  }


  private boolean isSameResource(ImplementationGuideDefinitionResourceComponent ir, CanonicalResource mr) {
    return ir.getReference().getReference().equals(mr.fhirType()+"/"+mr.getId());
  }


  private void generateAdditionalExamples() throws Exception {
    if (genExamples) {
      ProfileUtilities utils = new ProfileUtilities(context, null, null);
      for (FetchedFile f : changeList) {
        List<StructureDefinition> list = new ArrayList<StructureDefinition>();
        for (FetchedResource r : f.getResources()) {
          if (r.getResource() instanceof StructureDefinition) {
            list.add((StructureDefinition) r.getResource());
          }
        }
        for (StructureDefinition sd : list) {
          for (Element e : utils.generateExamples(sd, false)) {
            FetchedResource nr = new FetchedResource();
            nr.setElement(e);
            nr.setId(e.getChildValue("id"));
            nr.setTitle("Generated Example");
            nr.getStatedProfiles().add(sd.getUrl());
            f.getResources().add(nr);
            igpkp.findConfiguration(f, nr);
          }
        }
      }
    }
  }

  private void generateSnapshots() throws Exception {
    logDebugMessage(LogCategory.PROGRESS, "Generate Snapshots");
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() instanceof StructureDefinition) {
          if (r.getResEntry() != null) {
            ToolingExtensions.setStringExtension(r.getResEntry(), IGHelper.EXT_RESOURCE_INFO, r.fhirType()+":"+IGKnowledgeProvider.getSDType(r));
          }

          if (!r.isSnapshotted()) {
            StructureDefinition sd = (StructureDefinition) r.getResource();
            sd.setSnapshot(null); // make sure its clrared out so we do actually regenerate it at this point
            try {
              generateSnapshot(f, r, sd, false);
            } catch (Exception e) {
              throw new Exception("Error generating snapshot for "+f.getTitle()+(f.getResources().size() > 0 ? "("+r.getId()+")" : "")+": "+e.getMessage(), e);
            }
          }
        }
      }
    }
  }

  private void generateSnapshot(FetchedFile f, FetchedResource r, StructureDefinition sd, boolean close) throws Exception {
    boolean changed = false;
    logDebugMessage(LogCategory.PROGRESS, "Check Snapshot for "+sd.getUrl());
    sd.setFhirVersion(FHIRVersion.fromCode(version));
    ProfileUtilities utils = new ProfileUtilities(context, f.getErrors(), igpkp);
    StructureDefinition base = sd.hasBaseDefinition() ? fetchSnapshotted(sd.getBaseDefinition()) : null;
    utils.setIds(sd, true);
    utils.setXver(context.getXVer());
    if (base == null) {
      throw new Exception("Cannot find or generate snapshot for base definition ("+sd.getBaseDefinition()+" from "+sd.getUrl()+")");
    }

    if (sd.getKind() != StructureDefinitionKind.LOGICAL || sd.getDerivation()==TypeDerivationRule.CONSTRAINT) {
      if (!sd.hasSnapshot()) {
        logDebugMessage(LogCategory.PROGRESS, "Generate Snapshot for "+sd.getUrl());
        List<String> errors = new ArrayList<String>();
        if (close) {
          utils.closeDifferential(base, sd);
        } else {
    	    try {
            utils.sortDifferential(base, sd, "profile " + sd.getUrl(), errors, true);
          } catch (Exception e) {
            f.getErrors().add(new ValidationMessage(Source.ProfileValidator, IssueType.EXCEPTION, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "Exception generating snapshot: "+e.getMessage(), IssueSeverity.ERROR));
            r.getErrors().add(new ValidationMessage(Source.ProfileValidator, IssueType.EXCEPTION, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "Exception generating snapshot: "+e.getMessage(), IssueSeverity.ERROR));
          }
        }
        for (String s : errors) {
          f.getErrors().add(new ValidationMessage(Source.ProfileValidator, IssueType.INVALID, "StructureDefinition.where(url = '"+sd.getUrl()+"')", s, IssueSeverity.ERROR));
          r.getErrors().add(new ValidationMessage(Source.ProfileValidator, IssueType.INVALID, "StructureDefinition.where(url = '"+sd.getUrl()+"')", s, IssueSeverity.ERROR));
        }
        utils.setIds(sd, true);

        String p = sd.getDifferential().hasElement() ? sd.getDifferential().getElement().get(0).getPath() : null;
        if (p == null || p.contains(".")) {
          changed = true;
          sd.getDifferential().getElement().add(0, new ElementDefinition().setPath(p == null ? sd.getType() : p.substring(0, p.indexOf("."))));
        }
        utils.setDefWebRoot(igpkp.getCanonical());
        try {
          utils.generateSnapshot(base, sd, sd.getUrl(), Utilities.extractBaseUrl(base.getUserString("path")), sd.getName());
        } catch (Exception e) { 
          throw new FHIRException("Unable to generate snapshot for "+sd.getUrl()+" in "+f.getName(), e);
        }
        changed = true;
      }
    } else { //sd.getKind() == StructureDefinitionKind.LOGICAL
      logDebugMessage(LogCategory.PROGRESS, "Generate Snapshot for Logical Model or specialization"+sd.getUrl());
      if (!sd.hasSnapshot()) {
        utils.setDefWebRoot(igpkp.getCanonical());
        utils.generateSnapshot(base, sd, sd.getUrl(), Utilities.extractBaseUrl(base.getUserString("path")), sd.getName());
        changed = true;
      }
    }
    if (changed || (!r.getElement().hasChild("snapshot") && sd.hasSnapshot())) {
      r.setElement(convertToElement(sd));
    }
    r.setSnapshotted(true);
    logDebugMessage(LogCategory.CONTEXT, "Context.See "+sd.getUrl());
    context.cacheResourceFromPackage(sd, new PackageVersion(publishedIg.getPackageId(), publishedIg.getVersion()));
  }

  private void validateExpressions() {
    logDebugMessage(LogCategory.PROGRESS, "validate Expressions");
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() instanceof StructureDefinition && !r.isSnapshotted()) {
          StructureDefinition sd = (StructureDefinition) r.getResource();
          validateExpressions(f, sd, r);
        }
      }
    }
  }

  private void validateExpressions(FetchedFile f, StructureDefinition sd, FetchedResource r) {
    FHIRPathEngine fpe = new FHIRPathEngine(context);
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      for (ElementDefinitionConstraintComponent inv : ed.getConstraint()) {
        validateExpression(f, sd, fpe, ed, inv, r);
      }
    }
  }

  private void validateExpression(FetchedFile f, StructureDefinition sd, FHIRPathEngine fpe, ElementDefinition ed, ElementDefinitionConstraintComponent inv, FetchedResource r) {
    if (inv.hasExpression()) {
      try {
        ExpressionNode n = (ExpressionNode) inv.getUserData("validator.expression.cache");
        if (n == null) {
          n = fpe.parse(inv.getExpression(), sd.getUrl()+"#"+ed.getId()+" / "+inv.getKey());
          inv.setUserData("validator.expression.cache", n);
        }
        fpe.check(null, sd, ed.getPath(), n);
      } catch (Exception e) {
        f.getErrors().add(new ValidationMessage(Source.ProfileValidator, IssueType.INVALID, "StructureDefinition.where(url = '"+sd.getUrl()+"').snapshot.element.where('path = '"+ed.getPath()+"').constraint.where(key = '"+inv.getKey()+"')", e.getMessage(), IssueSeverity.ERROR));
        r.getErrors().add(new ValidationMessage(Source.ProfileValidator, IssueType.INVALID, "StructureDefinition.where(url = '"+sd.getUrl()+"').snapshot.element.where('path = '"+ed.getPath()+"').constraint.where(key = '"+inv.getKey()+"')", e.getMessage(), IssueSeverity.ERROR));
      }
    }
  }

  private StructureDefinition fetchSnapshotted(String url) throws Exception {
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() instanceof StructureDefinition) {
          StructureDefinition sd = (StructureDefinition) r.getResource();
          if (sd.getUrl().equals(url)) {
            if (!r.isSnapshotted()) {
              generateSnapshot(f, r, sd, false);
            }
            return sd;
          }
        }
      }
    }
    // Special case for logical models:
    if ("http://hl7.org/fhir/StructureDefinition/Base".equals(url)) {
      return ProfileUtilities.makeBaseDefinition(FHIRVersion.fromCode(version));
    }
    return context.fetchResource(StructureDefinition.class, url);
  }

  private void generateLogicalMaps() throws Exception {
    StructureMapUtilities mu = new StructureMapUtilities(context, null, null);
    for (FetchedFile f : fileList) {
      List<StructureMap> maps = new ArrayList<StructureMap>();
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() instanceof StructureDefinition) {
          StructureMap map = mu.generateMapFromMappings((StructureDefinition) r.getResource());
          if (map != null) {
            maps.add(map);
          }
        }
      }
      for (StructureMap map : maps) {
        FetchedResource nr = f.addResource();
        nr.setResource(map);
        nr.setElement(convertToElement(map));
        nr.setId(map.getId());
        nr.setTitle(map.getName());
        igpkp.findConfiguration(f, nr);
      }
    }
  }

  private Resource parseContent(String name, String contentType, String parseVersion, byte[] source) throws Exception {
    if (VersionUtilities.isR3Ver(parseVersion)) {
      org.hl7.fhir.dstu3.model.Resource res;
      if (contentType.contains("json")) {
        res = new org.hl7.fhir.dstu3.formats.JsonParser(true).parse(source);
      } else if (contentType.contains("xml")) {
        res = new org.hl7.fhir.dstu3.formats.XmlParser(true).parse(source);
      } else {
        throw new Exception("Unable to determine file type for "+name);
      }
      return VersionConvertor_30_50.convertResource(res, false);
    } else if (VersionUtilities.isR4Ver(parseVersion)) {
      org.hl7.fhir.r4.model.Resource res;
      if (contentType.contains("json")) {
        res = new org.hl7.fhir.r4.formats.JsonParser(true, true).parse(source);
      } else if (contentType.contains("xml")) {
        res = new org.hl7.fhir.r4.formats.XmlParser(true).parse(source);
      } else {
        throw new Exception("Unable to determine file type for "+name);
      }
      return VersionConvertor_40_50.convertResource(res);
    } else if (VersionUtilities.isR2BVer(parseVersion)) {
      org.hl7.fhir.dstu2016may.model.Resource res;
      if (contentType.contains("json")) {
        res = new org.hl7.fhir.dstu2016may.formats.JsonParser(true).parse(source);
      } else if (contentType.contains("xml")) {
        res = new org.hl7.fhir.dstu2016may.formats.XmlParser(true).parse(source);
      } else {
        throw new Exception("Unable to determine file type for "+name);
      }
      return VersionConvertor_14_50.convertResource(res);
    } else if (VersionUtilities.isR2Ver(parseVersion)) {
      org.hl7.fhir.dstu2.model.Resource res;
      if (contentType.contains("json")) {
        res = new org.hl7.fhir.dstu2.formats.JsonParser(true).parse(source);
      } else if (contentType.contains("xml")) {
        res = new org.hl7.fhir.dstu2.formats.XmlParser(true).parse(source);
      } else {
        throw new Exception("Unable to determine file type for "+name);
      }

      VersionConvertorAdvisor50 advisor = new IGR2ConvertorAdvisor5();
      return VersionConvertor_10_50.convertResource(res, advisor);
    } else if (parseVersion.equals(Constants.VERSION)) {
      if (contentType.contains("json")) {
        return new JsonParser(true, true).parse(source);
      } else if (contentType.contains("xml")) {
        return new XmlParser(true).parse(source);
      } else {
        throw new Exception("Unable to determine file type for "+name);
      }
    } else {
      throw new Exception("Unsupported version "+parseVersion);
    }    
  }

  private Resource parseInternal(FetchedFile file, FetchedResource res) throws Exception {
    String parseVersion = version;
    if (!file.getResources().isEmpty()) {
      parseVersion = str(file.getResources().get(0).getConfig(), "version", version);
    }
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    new org.hl7.fhir.r5.elementmodel.XmlParser(context).compose(res.getElement(), bs, OutputStyle.NORMAL, null);
    return parseContent("Entry "+res.getId()+" in "+file.getName(), "xml", parseVersion, bs.toByteArray());
  }
  
  private Resource parse(FetchedFile file) throws Exception {
    String parseVersion = version;
    if (!file.getResources().isEmpty()) {
      parseVersion = str(file.getResources().get(0).getConfig(), "version", version);
    }
    return parseContent(file.getName(), file.getContentType(), parseVersion, file.getSource());
  }

  private void validate() throws Exception {
    for (FetchedFile f : fileList) {
      logDebugMessage(LogCategory.PROGRESS, " .. validate "+f.getName());
      if (firstExecution) {
        logDebugMessage(LogCategory.PROGRESS, " .. "+f.getName());
      }
      for (FetchedResource r : f.getResources()) {
        if (!r.isValidated()) {
          logDebugMessage(LogCategory.PROGRESS, "     validating "+r.getTitle());
          validate(f, r);
        }
      }
    }
    logDebugMessage(LogCategory.PROGRESS, " .. check Profile Examples");
    logDebugMessage(LogCategory.PROGRESS, "gen narratives");
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("StructureDefinition")) {
          StructureDefinition sd = (StructureDefinition) r.getResource();
          if (!sd.getAbstract()) {
            if (sd.getKind() == StructureDefinitionKind.RESOURCE) {
              int cE = countStatedExamples(sd.getUrl());
              int cI = countFoundExamples(sd.getUrl());
              if (cE + cI == 0) {
                f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this profile", IssueSeverity.WARNING));
                r.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this profile", IssueSeverity.WARNING));
              } else if (cE == 0) {
                f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no explicitly linked examples for this profile", IssueSeverity.INFORMATION));
                r.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no explicitly linked examples for this profile", IssueSeverity.INFORMATION));
              }
            } else if (sd.getKind() == StructureDefinitionKind.COMPLEXTYPE) {
              if (sd.getType().equals("Extension")) {
                int c = countUsages(getFixedUrl(sd));
                if (c == 0) {
                  f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this extension", IssueSeverity.WARNING));
                  r.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this extension", IssueSeverity.WARNING));
                }
              } else {
                int cI = countFoundExamples(sd.getUrl());
                if (cI == 0) {
                  f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this data type profile", IssueSeverity.WARNING));
                  r.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this data type profile", IssueSeverity.WARNING));
                }
              }
            }
          }
        }
      }        
    }
  }

  private int countUsages(String fixedUrl) {
    int res = 0;
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        res = res + countExtensionUsage(r.getElement(), fixedUrl);
      }
    }
    return res;
  }



  private int countExtensionUsage(Element element, String url) {
    int res = 0;
    if (element.fhirType().equals("Extension") && url.equals(element.getChildValue("url"))) {
      res = res + 1;
    }
    for (Element child : element.getChildren()) {
      res = res + countExtensionUsage(child, url);
    }
    return res;
  }



  private String getFixedUrl(StructureDefinition sd) {
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      if (ed.getPath().equals("Extension.url") && ed.hasFixed()) {
        return ed.getFixed().primitiveValue();
      }
    }
    return sd.getUrl();
  }



  private int countStatedExamples(String url) {
    int res = 0;
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        for (String p : r.getStatedProfiles()) {
          if (url.equals(p)) {
            res++;
          }
        }
      }
    }
    return res;
  }

  private int countFoundExamples(String url) {
    int res = 0;
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        for (String p : r.getFoundProfiles()) {
          if (url.equals(p)) {
            res++;
          }
        }
      }
    }
    return res;
  }

  private void validate(FetchedFile file, FetchedResource r) throws Exception {
    Session tts = tt.start("validation");
    List<ValidationMessage> errs = new ArrayList<ValidationMessage>();
    r.getElement().setUserData("igpub.context.file", file);
    r.getElement().setUserData("igpub.context.resource", r);
    if (r.isValidateAsResource()) { 
      Resource res = r.getResource();
      if (res instanceof Bundle) {
        validator.validate(r.getElement(), errs, r.getElement());

        for (BundleEntryComponent be : ((Bundle) res).getEntry()) {
          Resource ber = be.getResource();
          if (ber.hasUserData("profile")) {
            validator.validate(r.getElement(), errs, ber, ber.getUserString("profile"));
          }
        }

      } else if (res.hasUserData("profile")) {
        validator.validate(r.getElement(), errs, res, res.getUserString("profile"));
      }
    } else {
      validator.setNoCheckAggregation(r.isExample() && ToolingExtensions.readBoolExtension(r.getResEntry(), "http://hl7.org/fhir/tools/StructureDefinition/igpublisher-no-check-aggregation"));
      if (r.getElement().hasUserData("profile")) {
        String ref = r.getElement().getUserString("profile");
        if (!Utilities.isAbsoluteUrl(ref)) {
          ref = Utilities.pathURL(igpkp.getCanonical(), ref);
        }
        validator.validate(r.getElement(), errs, r.getElement(), ref);
      } else {
        validator.validate(r.getElement(), errs, r.getElement());
      }
    }
    for (ValidationMessage vm : errs) {
      String loc = r.fhirType()+"/"+r.getId();
      if (!vm.getLocation().startsWith(loc)) {
        vm.setLocation(loc+": "+vm.getLocation());
      }
      file.getErrors().add(vm);
      r.getErrors().add(vm);
    }
    r.setValidated(true);
    if (r.getConfig() == null) {
      igpkp.findConfiguration(file, r);
    }
    tts.end();
  }

  private void generate() throws Exception {
    forceDir(tempDir);
    forceDir(Utilities.path(tempDir, "_includes"));
    forceDir(Utilities.path(tempDir, "data"));

    otherFilesRun.clear();
    otherFilesRun.add(Utilities.path(outputDir, "package.tgz"));
    otherFilesRun.add(Utilities.path(outputDir, "package.manifest.json"));
    for (String rg : regenList) {
      regenerate(rg);
    }

    updateImplementationGuide();
    
    for (FetchedFile f : changeList) {
      generateNativeOutputs(f, false);
    }
    
    templateBeforeGenerate();

    for (FetchedFile f : changeList) {
      generateHtmlOutputs(f, false);
    }

    if (!changeList.isEmpty()) {
      generateSummaryOutputs();
    }
    TextFile.bytesToFile(extensionTracker.generate(), Utilities.path(tempDir, "usage-stats.json"));
    try {
      log("Sending Usage Stats to Server");
      extensionTracker.sendToServer("http://test.fhir.org/usage-stats");
    } catch (Exception e) {
      log("Submitting Usage Stats failed: "+e.getMessage());
    }
    
    realmRules.addOtherFiles(otherFilesRun, outputDir);
    previousVersionComparator.addOtherFiles(otherFilesRun, outputDir);
    otherFilesRun.add(Utilities.path(tempDir, "usage-stats.json"));
    
    printMemUsage();
    System.out.println("Reclaiming memory...");
    cleanOutput(tempDir);
    for (FetchedFile f : fileList) {
      f.trim();
    }
    System.gc();
    printMemUsage();
        
    if (nestedIgConfig != null) {
      if (watch) {
        throw new Exception("Cannot run in watch mode when IG has a nested IG.");
      }
      if (nestedIgOutput == null || igArtifactsPage == null) {
        throw new Exception("If nestedIgConfig is specified, then nestedIgOutput and igArtifactsPage must also be specified.");
      }
      inspector.setAltRootFolder(nestedIgOutput);
      log("");
      log("**************************");
      log("Processing nested IG: " + nestedIgConfig);
      childPublisher = new Publisher();
      childPublisher.setConfigFile(Utilities.path(Utilities.getDirectoryForFile(this.getConfigFile()), nestedIgConfig));
      childPublisher.setJekyllCommand(this.getJekyllCommand());
      childPublisher.setTxServer(this.getTxServer());
      childPublisher.setDebug(this.debug);
      childPublisher.setCacheOption(this.getCacheOption());
      childPublisher.setIsChild(true);
      childPublisher.setMode(this.getMode());
      childPublisher.setTargetOutput(this.getTargetOutputNested());
      
      try {
        childPublisher.execute();
        log("Done processing nested IG: " + nestedIgConfig);
        log("**************************");
        childPublisher.updateInspector(inspector, nestedIgOutput);
      } catch (Exception e) {
        log("Publishing Child IG Failed: " + nestedIgConfig);
        throw e;
      }
      createToc(childPublisher.getPublishedIg().getDefinition().getPage(), igArtifactsPage, nestedIgOutput);
    }
    fixSearchForm();
    templateBeforeJekyll();

    if (runTool()) {
      templateOnCheck();

      if (!changeList.isEmpty()) {
        File df = makeSpecFile();
        npm.addFile(Category.OTHER, "spec.internals", TextFile.fileToBytes(df.getAbsolutePath()));
        npm.addFile(Category.OTHER, "validation-summary.json", validationSummaryJson());
        npm.addFile(Category.OTHER, "validation-oo.json", validationSummaryOO());
        npm.finish();
        
        if (mode == null || mode == IGBuildMode.MANUAL) {
          if (cacheVersion) {
            pcm.addPackageToCache(publishedIg.getPackageId(), publishedIg.getVersion(), new FileInputStream(npm.filename()), "[output]");
          } else {
            pcm.addPackageToCache(publishedIg.getPackageId(), "dev", new FileInputStream(npm.filename()), "[output]");
          }
        } else if (mode == IGBuildMode.PUBLICATION) {
          pcm.addPackageToCache(publishedIg.getPackageId(), publishedIg.getVersion(), new FileInputStream(npm.filename()), "[output]");
        }
        for (String s : generateVersions) {
          generatePackageVersion(npm.filename(), s);
        }
        generateZips(df);
      }
    }
    
    if (childPublisher!=null) {
      // Combine list of files so that the validation report will include everything
      fileList.addAll(childPublisher.getFileList());
    }
    
    if (!isChild()) {
      log("Checking Output HTML");
      String statusMessage;
      if (mode == IGBuildMode.AUTOBUILD) { 
        statusMessage = Utilities.escapeXml(sourceIg.present())+", published by "+Utilities.escapeXml(sourceIg.getPublisher())+". This is not an authorized publication; it is the continuous build for version "+workingVersion()+"). This version is based on the current content of <a href=\""+gh()+"\">"+gh()+"</a> and changes regularly. See the <a href=\""+igpkp.getCanonical()+"/history.html\">Directory of published versions</a>"; 
      } else if (mode == IGBuildMode.PUBLICATION) { 
        statusMessage = "Publication Build: This will be filled in by the publication tooling"; 
      } else { 
        statusMessage = Utilities.escapeXml(sourceIg.present())+" - Local Development build (v"+workingVersion()+"). See the <a href=\""+igpkp.getCanonical()+"/history.html\">Directory of published versions</a>";
      }
              
      realmRules.addOtherFiles(inspector.getExceptions(), outputDir);
      previousVersionComparator.addOtherFiles(inspector.getExceptions(), outputDir);
      List<ValidationMessage> linkmsgs = inspector.check(statusMessage);
      int bl = 0;
      int lf = 0;
      for (ValidationMessage m : ValidationPresenter.filterMessages(linkmsgs, true, suppressedMessages.keySet())) {
        if (m.getLevel() == IssueSeverity.ERROR) {
          if (m.getType() == IssueType.NOTFOUND) {
            bl++;
          } else {
            lf++;
          }
        } else if (m.getLevel() == IssueSeverity.FATAL) {
          throw new Exception(m.getMessage());
        }
      }
      log("  ... "+Integer.toString(inspector.total())+" html "+checkPlural("file", inspector.total())+", "+Integer.toString(lf)+" "+checkPlural("page", lf)+" invalid xhtml ("+Integer.toString((lf*100)/(inspector.total() == 0 ? 1 : inspector.total()))+"%)");
      log("  ... "+Integer.toString(inspector.links())+" "+checkPlural("link", inspector.links())+", "+Integer.toString(bl)+" broken "+checkPlural("link", lf)+" ("+Integer.toString((bl*100)/(inspector.links() == 0 ? 1 : inspector.links()))+"%)");
      errors.addAll(linkmsgs);    
      if (brokenLinksError && linkmsgs.size() > 0) {
        throw new Error("Halting build because broken links have been found, and these are disallowed in the IG control file");
      }
      if (mode == IGBuildMode.AUTOBUILD && inspector.isMissingPublishBox()) {
        throw new FHIRException("The auto-build infrastructure does not publish IGs that contain HTML pages without the publish-box present ("+inspector.getMissingPublishboxSummary()+"). For further information, see note at http://wiki.hl7.org/index.php?title=FHIR_Implementation_Guide_Publishing_Requirements#HL7_HTML_Standards_considerations");
      }

      log("Build final .zip");
      ZipGenerator zip = new ZipGenerator(Utilities.path(tempDir, "full-ig.zip"));
      zip.addFolder(outputDir, "site/", false);
      zip.addFileSource("index.html", REDIRECT_SOURCE, false);
      zip.close();
      Utilities.copyFile(Utilities.path(tempDir, "full-ig.zip"), Utilities.path(outputDir, "full-ig.zip"));
      log("Final .zip built");
    }
  }

  private byte[] validationSummaryOO() throws IOException {
    Bundle bnd = new Bundle();
    bnd.setType(BundleType.COLLECTION);
    bnd.setTimestamp(new Date());
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        OperationOutcome oo = new OperationOutcome();
        oo.setId(r.fhirType()+"-"+r.getId());
        bnd.addEntry().setFullUrl(igpkp.getCanonical()+"/OperationOutcome/"+oo.getId()).setResource(oo);
        for (ValidationMessage vm : r.getErrors()) {
          if (!vm.getLevel().isHint())
          oo.addIssue(OperationOutcomeUtilities.convertToIssue(vm, oo));
        }
      }
    }
    return new JsonParser().composeBytes(bnd);
  }

  private byte[] validationSummaryJson() throws UnsupportedEncodingException {
    JsonObject json = new JsonObject();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        JsonObject rd = new JsonObject();
        json.add(r.fhirType()+"/"+r.getId(), rd);
        createValidationSummary(rd, r);
      }
    }
    return JsonTrackingParser.write(json).getBytes("UTF-8");
  }

  private void createValidationSummary(JsonObject fd, FetchedResource r) {
    int e = 0;
    int w = 0;
    int i = 0;
    for (ValidationMessage vm : r.getErrors()) {
      if (vm.getLevel() == IssueSeverity.INFORMATION) {
        i++;
      } else if (vm.getLevel() == IssueSeverity.WARNING) {
        w++;
      } else {
        e++;
      }
    }
    fd.addProperty("errors", e);
    fd.addProperty("warnings", w);
//    fd.addProperty("hints", i);
  }

  private void fixSearchForm() throws IOException {
    
    String sfn = Utilities.path(tempDir, "searchform.html");
    if (new File(sfn).exists() ) {
      String sf = TextFile.fileToString(sfn);
      sf = sf.replace("{{title}}", publishedIg.present());
      sf = sf.replace("{{url}}", targetUrl());
      TextFile.stringToFile(sf, sfn);      
    }    
  }

  private void printMemUsage() {
    int mb = 1024*1024;
    Runtime runtime = Runtime.getRuntime();
    System.out.print("## Memory (MB): ");
    System.out.print("Use = " + (runtime.totalMemory() - runtime.freeMemory()) / mb);
    System.out.print(", Free = " + runtime.freeMemory() / mb);
    System.out.print(", Total = " + runtime.totalMemory() / mb);
    System.out.println(", Max =" + runtime.maxMemory() / mb);
  }

  private void generatePackageVersion(String filename, String ver) throws IOException {
    NpmPackageVersionConverter self = new NpmPackageVersionConverter(filename, Utilities.path(Utilities.getDirectoryForFile(filename), publishedIg.getPackageId()+"."+ver+".tgz"), ver);
    self.execute();
    for (String s : self.getErrors()) {
      errors.add(new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, "ImplementationGuide", "Error creating "+ver+" package: "+s, IssueSeverity.ERROR));
    }
  }

  private String gh() {
    return targetOutput == null ? null : targetOutput.replace("https://build.fhir.org/ig", "https://github.com");
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

  private void updateImplementationGuide() throws Exception {
    FetchedResource r = altMap.get(IG_NAME).getResources().get(0);
    if (!publishedIg.hasText() || !publishedIg.getText().hasDiv()) {
      publishedIg.setText(((ImplementationGuide)r.getResource()).getText());
    }
    r.setResource(publishedIg);
    r.setElement(convertToElement(publishedIg));
    
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    new JsonParser().setOutputStyle(OutputStyle.NORMAL).compose(bs, publishedIg);
    npm.addFile(Category.RESOURCE, "ig-r4.json", bs.toByteArray());
    
    for (ImplementationGuideDefinitionResourceComponent res : publishedIg.getDefinition().getResource()) {
      FetchedResource rt = null;
      for (FetchedFile tf : fileList) {
        for (FetchedResource tr : tf.getResources()) {
          if (tr.getLocalRef().equals(res.getReference().getReference())) {
            rt = tr;
          }
        }
      }
      if (rt != null) {
        if (!rt.getProvenance()) {
          // Don't expose a page for a resource that is just provenance information
          String path = igpkp.doReplacements(igpkp.getLinkFor(rt, false), rt, null, null);
          res.addExtension().setUrl("http://hl7.org/fhir/StructureDefinition/implementationguide-page").setValue(new UriType(path));
          inspector.addLinkToCheck("Implementation Guide", path, "fake generated link");
        }
      }
    }
  }

  private String checkPlural(String word, int c) {
    return c == 1 ? word : Utilities.pluralizeMe(word);
  }

  private void regenerate(String uri) throws Exception {
    Resource res ;
    if (uri.contains("/StructureDefinition/")) {
      res = context.fetchResource(StructureDefinition.class, uri);
    } else {
      throw new Exception("Unable to process "+uri);
    }

    if (res == null) {
      throw new Exception("Unable to find regeneration source for "+uri);
    }

    CanonicalResource bc = (CanonicalResource) res;

    FetchedFile f = new FetchedFile(bc.getUserString("path"));
    FetchedResource r = f.addResource();
    r.setResource(res);
    r.setId(bc.getId());
    r.setTitle(bc.getName());
    r.setValidated(true);
    r.setElement(convertToElement(bc));
    igpkp.findConfiguration(f, r);
    bc.setUserData("config", r.getConfig());
    generateNativeOutputs(f, true);
    generateHtmlOutputs(f, true);
  }

  private Element convertToElement(Resource res) throws IOException, org.hl7.fhir.exceptions.FHIRException, FHIRFormatError, DefinitionException {
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    if (VersionUtilities.isR3Ver(version)) {
      org.hl7.fhir.dstu3.formats.JsonParser jp = new org.hl7.fhir.dstu3.formats.JsonParser();
      jp.compose(bs, VersionConvertor_30_50.convertResource(res, false));
    } else if (VersionUtilities.isR4Ver(version)) {
      org.hl7.fhir.r4.formats.JsonParser jp = new org.hl7.fhir.r4.formats.JsonParser();
      jp.compose(bs, VersionConvertor_40_50.convertResource(res));
    } else if (VersionUtilities.isR2BVer(version)) {
      org.hl7.fhir.dstu2016may.formats.JsonParser jp = new org.hl7.fhir.dstu2016may.formats.JsonParser();
      jp.compose(bs, VersionConvertor_14_50.convertResource(res));
    } else if (VersionUtilities.isR2Ver(version)) {
      org.hl7.fhir.dstu2.formats.JsonParser jp = new org.hl7.fhir.dstu2.formats.JsonParser();
      jp.compose(bs, VersionConvertor_10_50.convertResource(res, new IGR2ConvertorAdvisor5()));
    } else { // if (version.equals(Constants.VERSION)) {
      org.hl7.fhir.r5.formats.JsonParser jp = new org.hl7.fhir.r5.formats.JsonParser();
      jp.compose(bs, res);
    }
    ByteArrayInputStream bi = new ByteArrayInputStream(bs.toByteArray());
    return new org.hl7.fhir.r5.elementmodel.JsonParser(context).parse(bi);
  }

  private Resource convertFromElement(Element res) throws IOException, org.hl7.fhir.exceptions.FHIRException, FHIRFormatError, DefinitionException {
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    new org.hl7.fhir.r5.elementmodel.JsonParser(context).compose(res, bs, OutputStyle.NORMAL, null);
    ByteArrayInputStream bi = new ByteArrayInputStream(bs.toByteArray());
    if (VersionUtilities.isR3Ver(version)) {
      org.hl7.fhir.dstu3.formats.JsonParser jp = new org.hl7.fhir.dstu3.formats.JsonParser();
      return  VersionConvertor_30_50.convertResource(jp.parse(bi), false);
    } else if (VersionUtilities.isR4Ver(version)) {
      org.hl7.fhir.r4.formats.JsonParser jp = new org.hl7.fhir.r4.formats.JsonParser();
      return  VersionConvertor_40_50.convertResource(jp.parse(bi));
    } else if (VersionUtilities.isR2BVer(version)) {
      org.hl7.fhir.dstu2016may.formats.JsonParser jp = new org.hl7.fhir.dstu2016may.formats.JsonParser();
      return  VersionConvertor_14_50.convertResource(jp.parse(bi));
    } else if (VersionUtilities.isR2Ver(version)) {
        org.hl7.fhir.dstu2.formats.JsonParser jp = new org.hl7.fhir.dstu2.formats.JsonParser();
        return VersionConvertor_10_50.convertResource(jp.parse(bi));
    } else { // if (version.equals(Constants.VERSION)) {
      org.hl7.fhir.r5.formats.JsonParser jp = new org.hl7.fhir.r5.formats.JsonParser();
      return jp.parse(bi);
    } 
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

  private boolean isValidFile(String p) {
    if (otherFilesStartup.contains(p)) {
      return true;
    }
    if (otherFilesRun.contains(p)) {
      return true;
    }
    for (FetchedFile f : fileList) {
      if (f.getOutputNames().contains(p)) {
        return true;
      }
    }
    for (FetchedFile f : altMap.values()) {
      if (f.getOutputNames().contains(p)) {
        return true;
      }
    }
    return false;
  }

  private File makeSpecFile() throws Exception {
    SpecMapManager map = new SpecMapManager(npmName, version, Constants.VERSION, Integer.toString(ToolsVersion.TOOLS_VERSION), execTime, igpkp.getCanonical());
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        String u = igpkp.getCanonical()+r.getUrlTail();
        if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
          String uc = ((CanonicalResource) r.getResource()).getUrl();
          if (uc != null && !u.equals(uc) && !isListedURLExemption(uc) && !isExampleResource((CanonicalResource) r.getResource()) && adHocTmpDir == null) {
            f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, f.getName(), "URL Mismatch "+u+" vs "+uc, IssueSeverity.ERROR));
            r.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, f.getName(), "URL Mismatch "+u+" vs "+uc, IssueSeverity.ERROR));
          }
          if (uc != null && !u.equals(uc)) {
            map.path(uc, igpkp.getLinkFor(r, true));
          }
          String v = ((CanonicalResource) r.getResource()).getVersion();
          if (v != null) {
            map.path(uc + "|" + v, v + "/" + igpkp.getLinkFor(r, true));
          }
        }
        map.path(u, igpkp.getLinkFor(r, true));
      }
    }
    for (String s : new File(outputDir).list()) {
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
    if (generateExampleZip(FhirFormat.XML)) {
      generateDefinitions(FhirFormat.XML, df.getCanonicalPath());
    }
    if (generateExampleZip(FhirFormat.JSON)) {
      generateDefinitions(FhirFormat.JSON, df.getCanonicalPath());
    }
    if (supportsTurtle() && generateExampleZip(FhirFormat.TURTLE)) {
      generateDefinitions(FhirFormat.TURTLE, df.getCanonicalPath());
    }
    generateExpansions();
    generateValidationPack(df.getCanonicalPath());
    // Create an IG-specific named igpack to make is easy to grab the igpacks for multiple igs without the names colliding (Talk to Lloyd before removing this)
    FileUtils.copyFile(new File(Utilities.path(outputDir, "validator.pack")),new File(Utilities.path(outputDir, "validator-" + sourceIg.getId() + ".pack")));
    generateCsvZip();
    generateExcelZip();
//    generateRegistryUploadZip(df.getCanonicalPath());
  }

  private boolean supportsTurtle() {
    return !Utilities.existsInList(version, "1.0.2", "1.4.0");
  }


  private void generateExpansions() throws FileNotFoundException, IOException {
    Bundle exp = new Bundle();
    exp.setType(BundleType.COLLECTION);
    exp.setId(UUID.randomUUID().toString());
    exp.getMeta().setLastUpdated(execTime.getTime());
    for (ValueSet vs : expansions) {
      exp.addEntry().setResource(vs).setFullUrl(vs.getUrl());
    }
    
    new JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream(Utilities.path(outputDir, "expansions.json")), exp);
    new XmlParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream(Utilities.path(outputDir, "expansions.xml")), exp);
    ZipGenerator zip = new ZipGenerator(Utilities.path(outputDir, "expansions.json.zip"));
    zip.addFileName("expansions.json", Utilities.path(outputDir, "expansions.json"), false);
    zip.close();
    zip = new ZipGenerator(Utilities.path(outputDir, "expansions.xml.zip"));
    zip.addFileName("expansions.xml", Utilities.path(outputDir, "expansions.xml"), false);
    zip.close();
  }


  private boolean isListedURLExemption(String uc) {
    return listedURLExemptions.contains(uc);
  }

  private void generateDefinitions(FhirFormat fmt, String specFile)  throws Exception {
    // public definitions
    Set<FetchedResource> files = new HashSet<FetchedResource>();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
          files.add(r);
        }
      }
    }
    if (!files.isEmpty()) {
      ZipGenerator zip = new ZipGenerator(Utilities.path(outputDir, "definitions."+fmt.getExtension()+".zip"));
      for (FetchedResource r : files) {
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        if (VersionUtilities.isR3Ver(version)) {
          org.hl7.fhir.dstu3.model.Resource r3 = VersionConvertor_30_50.convertResource(r.getResource(), false);
          if (fmt.equals(FhirFormat.JSON)) {
            new org.hl7.fhir.dstu3.formats.JsonParser().compose(bs, r3);
          } else if (fmt.equals(FhirFormat.XML)) {
            new org.hl7.fhir.dstu3.formats.XmlParser().compose(bs, r3);
          } else if (fmt.equals(FhirFormat.TURTLE)) {
            new org.hl7.fhir.dstu3.formats.RdfParser().compose(bs, r3);
          }
        } else if (VersionUtilities.isR4Ver(version)) {
          org.hl7.fhir.r4.model.Resource r4 = VersionConvertor_40_50.convertResource(r.getResource());
          if (fmt.equals(FhirFormat.JSON)) {
            new org.hl7.fhir.r4.formats.JsonParser().compose(bs, r4);
          } else if (fmt.equals(FhirFormat.XML)) {
            new org.hl7.fhir.r4.formats.XmlParser().compose(bs, r4);
          } else if (fmt.equals(FhirFormat.TURTLE)) {
            new org.hl7.fhir.r4.formats.RdfParser().compose(bs, r4);
          }
        } else if (VersionUtilities.isR2BVer(version)) {
          org.hl7.fhir.dstu2016may.model.Resource r14 = VersionConvertor_14_50.convertResource(r.getResource());
          if (fmt.equals(FhirFormat.JSON)) {
            new org.hl7.fhir.dstu2016may.formats.JsonParser().compose(bs, r14);
          } else if (fmt.equals(FhirFormat.XML)) {
            new org.hl7.fhir.dstu2016may.formats.XmlParser().compose(bs, r14);
          } else if (fmt.equals(FhirFormat.TURTLE)) {
            new org.hl7.fhir.dstu2016may.formats.RdfParser().compose(bs, r14);
          }
        } else if (VersionUtilities.isR2Ver(version)) {
          VersionConvertorAdvisor50 advisor = new IGR2ConvertorAdvisor5();
          org.hl7.fhir.dstu2.model.Resource r14 = VersionConvertor_10_50.convertResource(r.getResource(), advisor);
          if (fmt.equals(FhirFormat.JSON)) {
            new org.hl7.fhir.dstu2.formats.JsonParser().compose(bs, r14);
          } else if (fmt.equals(FhirFormat.XML)) {
            new org.hl7.fhir.dstu2.formats.XmlParser().compose(bs, r14);
          } else if (fmt.equals(FhirFormat.TURTLE)) {
            throw new Exception("Turtle is not supported for releases < 3");
          }
        } else {
          if (fmt.equals(FhirFormat.JSON)) {
            new JsonParser().compose(bs, r.getResource());
          } else if (fmt.equals(FhirFormat.XML)) {
            new XmlParser().compose(bs, r.getResource());
          } else if (fmt.equals(FhirFormat.TURTLE)) {
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
    generateZipByExtension(Utilities.path(outputDir, "excels.zip"), ".xlsx");
  }

  private void generateCsvZip()  throws Exception {
    generateZipByExtension(Utilities.path(outputDir, "csvs.zip"), ".csv");
  }

  private void generateRegistryUploadZip(String specFile)  throws Exception {
    ZipGenerator zip = new ZipGenerator(Utilities.path(outputDir, "registry.fhir.org.zip"));
    zip.addFileName("spec.internals", specFile, false);
    StringBuilder ri = new StringBuilder();
    ri.append("[registry]\r\n");
    ri.append("toolversion="+getToolingVersion()+"\r\n");
    ri.append("fhirversion="+version+"\r\n");
    int i = 0;
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
          try {
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            org.hl7.fhir.dstu3.model.Resource r3 = VersionConvertor_30_50.convertResource(r.getResource(), false);
            new org.hl7.fhir.dstu3.formats.JsonParser().compose(bs, r3);
            zip.addBytes(r.fhirType()+"-"+r.getId()+".json", bs.toByteArray(), false);
          } catch (Exception e) {
            log("Can't store "+r.fhirType()+"-"+r.getId()+" in R3 format for registry.fhir.org");
            e.printStackTrace();
          }
          i++;
        }
      }
    }
    ri.append("resourcecount="+Integer.toString(i)+"\r\n");
    zip.addBytes("registry.info",TextFile.stringToBytes(ri.toString(), false), false);
    zip.close();
  }

  private void generateValidationPack(String specFile)  throws Exception {
    String sch = makeTempZip(".sch");
    String js = makeTempZip(".schema.json");
    String shex = makeTempZip(".shex");

    ZipGenerator zip = new ZipGenerator(Utilities.path(outputDir, "validator.pack"));
    zip.addBytes("version.info", makeNewVersionInfo(version), false);
    zip.addFileName("spec.internals", specFile, false);
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
          ByteArrayOutputStream bs = new ByteArrayOutputStream();
          if (VersionUtilities.isR3Ver(version)) {
            new org.hl7.fhir.dstu3.formats.JsonParser().compose(bs, VersionConvertor_30_50.convertResource(r.getResource(), false));
          } else if (VersionUtilities.isR4Ver(version)) {
            new org.hl7.fhir.r4.formats.JsonParser().compose(bs, VersionConvertor_40_50.convertResource(r.getResource()));
          } else if (VersionUtilities.isR2BVer(version)) {
            new org.hl7.fhir.dstu2016may.formats.JsonParser().compose(bs, VersionConvertor_14_50.convertResource(r.getResource()));
          } else if (VersionUtilities.isR2Ver(version)) {
            VersionConvertorAdvisor50 advisor = new IGR2ConvertorAdvisor5();
            new org.hl7.fhir.dstu2.formats.JsonParser().compose(bs, VersionConvertor_10_50.convertResource(r.getResource(), advisor));
          } else if (version.equals(Constants.VERSION)) {
            new JsonParser().compose(bs, r.getResource());
          } else {
            throw new Exception("Unsupported version "+version);
          }
          zip.addBytes(r.fhirType()+"-"+r.getId()+".json", bs.toByteArray(), false);
        }
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
    IniFile ini = new IniFile(new ByteArrayInputStream(TextFile.stringToBytes(is, false)));
    ini.setStringProperty("IG", "version", version, null);
    ini.setStringProperty("IG", "date",  new SimpleDateFormat("yyyyMMddhhmmssZ", new Locale("en", "US")).format(execTime.getTime()), null);
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    ini.save(b);
    return b.toByteArray();
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
    for (String s : new File(outputDir).list()) {
      if (s.endsWith(ext)) {
        files.add(s);
      }
    }
    if (files.size() == 0) {
      return false;
    }
    ZipGenerator zip = new ZipGenerator(path);
    for (String fn : files) {
      zip.addFileName(fn, Utilities.path(outputDir, fn), false);
    }
    zip.close();
    return true;
  }
  
  private boolean generateExampleZip(FhirFormat fmt) throws Exception {
    Set<String> files = new HashSet<String>();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        String fn = Utilities.path(outputDir, r.fhirType()+"-"+r.getId()+"."+fmt.getExtension());
        if (new File(fn).exists()) {
          files.add(fn);
        }
      }
    }
    if (!files.isEmpty()) {
      ZipGenerator zip = new ZipGenerator(Utilities.path(outputDir, "examples."+fmt.getExtension()+".zip"));
      for (String fn : files) {
        zip.addFileName(fn.substring(fn.lastIndexOf(File.separator)+1), fn, false);
      }
      zip.close();
    }
       
    return !files.isEmpty();
  }

  private boolean runTool() throws Exception {
    switch (tool) {
    case Jekyll: return runJekyll();
    default:
      throw new Exception("unimplemented tool");
    }
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

/*  private boolean passJekyllFilter(String s) {
    if (Utilities.noString(s))
      return false;
    if (s.contains("Source:"))
      return false;
    if (s.contains("Destination:"))
      return false;
    if (s.contains("Configuration"))
      return false;
    if (s.contains("Incremental build:"))
      return false;
    if (s.contains("Auto-regeneration:"))
      return false;
    return true;
  }*/

  private boolean runJekyll() throws IOException, InterruptedException {
    Session tts = tt.start("jekyll");

    DefaultExecutor exec = new DefaultExecutor();
    exec.setExitValue(0);
    MyFilterHandler pumpHandler = new MyFilterHandler();
    PumpStreamHandler pump = new PumpStreamHandler(pumpHandler, pumpHandler);
    exec.setStreamHandler(pump);
    exec.setWorkingDirectory(new File(tempDir));
    ExecuteWatchdog watchdog = new ExecuteWatchdog(JEKYLL_TIMEOUT);
    exec.setWatchdog(watchdog);
    
//    dumpVars();

    try {
	    if (SystemUtils.IS_OS_WINDOWS) {
	      exec.execute(org.apache.commons.exec.CommandLine.parse("cmd /C "+jekyllCommand+" build --destination \""+outputDir+"\""));
	    } else {
	      exec.execute(org.apache.commons.exec.CommandLine.parse(jekyllCommand+" build --destination \""+outputDir+"\""));
	    }
	    tts.end();
    } catch (IOException ioex) {
      tts.end();
      if (pumpHandler.observedToSucceed) {
        log("Jekyll claimed to succeed, but returned an error. Proceeding anyway");
      } else {
        log("Jekyll has failed. Complete output from running Jekyll: " + pumpHandler.getBufferString());
        log("Note: Check that Jekyll is installed correctly");
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


  private void generateSummaryOutputs() throws Exception {
    log("Generating Summary Outputs");
    generateResourceReferences();

    generateDataFile();
    generateCanonicalSummary();
    
    CrossViewRenderer cvr = new CrossViewRenderer(igpkp.getCanonical(), context);
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
          cvr.seeResource((CanonicalResource) r.getResource());
        }
      }
    }
    fragment("summary-observations", cvr.getObservationSummary(), otherFilesRun);
    fragment("summary-extensions", cvr.getExtensionSummary(), otherFilesRun);
    fragment("ip-statements",  genIpStatements(fileList), otherFilesRun);

    // now, list the profiles - all the profiles
    JsonObject data = new JsonObject();
    int i = 0;
    JsonObject maturities = new JsonObject();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof DomainResource) {
          String fmm = ToolingExtensions.readStringExtension((DomainResource) r.getResource(), ToolingExtensions.EXT_FMM_LEVEL);
          if (fmm != null) {
            maturities.addProperty(r.getResource().fhirType()+"-"+r.getId(), fmm);
          }
        }
        if (r.fhirType().equals("StructureDefinition")) {
          StructureDefinition sd = (StructureDefinition) r.getResource();

          JsonObject item = new JsonObject();
          data.add(sd.getId(), item);
          item.addProperty("index", i);
          item.addProperty("url", sd.getUrl());
          item.addProperty("name", sd.getName());
          item.addProperty("path", sd.getUserString("path"));
          item.addProperty("kind", sd.getKind().toCode());
          item.addProperty("type", sd.getType());
          item.addProperty("base", sd.getBaseDefinition());
          StructureDefinition base = sd.hasBaseDefinition() ? context.fetchResource(StructureDefinition.class, sd.getBaseDefinition()) : null;
          if (base != null) {
            item.addProperty("basename", base.getName());
            item.addProperty("basepath", Utilities.escapeXml(base.getUserString("path")));
          } else if ("http://hl7.org/fhir/StructureDefinition/Base".equals(sd.getBaseDefinition())) {
            item.addProperty("basename", "Base");
            item.addProperty("basepath", "http://hl7.org/fhir/StructureDefinition/Element");            
          }
          item.addProperty("status", sd.getStatus().toCode());
          item.addProperty("date", sd.getDate().toString());
          item.addProperty("abstract", sd.getAbstract());
          if (sd.hasDerivation()) {
            item.addProperty("derivation", sd.getDerivation().toCode());
          }
          item.addProperty("publisher", sd.getPublisher());
          item.addProperty("copyright", sd.getCopyright());
          item.addProperty("description", sd.getDescription());
          if (sd.hasContext()) {
            JsonArray contexts = new JsonArray();
            item.add("contexts", contexts);
            for (StructureDefinitionContextComponent ec : sd.getContext()) {
              JsonObject citem = new JsonObject();
              contexts.add(citem);
              citem.addProperty("type", ec.getType().getDisplay());
              citem.addProperty("type", ec.getExpression());
            }
          }
          i++;
        }
      }
    }
    if (maturities.keySet().size() > 0) {
      data.add("maturities", maturities);
    }

    for (FetchedResource r: examples) {
      FetchedResource baseRes = getResourceForUri(r.getExampleUri());
      if (baseRes == null) {
        errors.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, r.fhirType()+"/"+r.getId(), "Unable to find profile " + r.getExampleUri() + " nominated as the profile for which resource " + r.getUrlTail()+" is an example", IssueSeverity.ERROR));
      } else {
        baseRes.addStatedExample(r);
      }
    }

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    String json = gson.toJson(data);
    TextFile.stringToFile(json, Utilities.path(tempDir, "_data", "structuredefinitions.json"), false);

    // now, list the profiles - all the profiles
    data = new JsonObject();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("Questionnaire")) {
          Questionnaire q = (Questionnaire) r.getResource();

          JsonObject item = new JsonObject();
          data.add(q.getId(), item);
          item.addProperty("index", i);
          item.addProperty("url", q.getUrl());
          item.addProperty("name", q.getName());
          item.addProperty("path", q.getUserString("path"));
          item.addProperty("status", q.getStatus().toCode());
          item.addProperty("date", q.getDate().toString());
          item.addProperty("publisher", q.getPublisher());
          item.addProperty("copyright", q.getCopyright());
          item.addProperty("description", q.getDescription());
          i++;
        }
      }
    }
    
    gson = new GsonBuilder().setPrettyPrinting().create();
    json = gson.toJson(data);
    TextFile.stringToFile(json, Utilities.path(tempDir, "_data", "questionnaires.json"), false);

    // now, list the profiles - all the profiles
    data = new JsonObject();
    i = 0;
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        JsonObject item = new JsonObject();
        data.add(r.fhirType()+"/"+r.getId(), item);
        item.addProperty("history", r.hasHistory());
        item.addProperty("index", i);
        item.addProperty("source", f.getStatedPath());
        item.addProperty("path", r.getElement().getUserString("path"));
        if (r.getResource() != null) {
          if (r.getResource() instanceof CanonicalResource) {
            CanonicalResource cr = (CanonicalResource) r.getResource();
            item.addProperty("url", cr.getUrl());
            if (cr.hasName()) {
              item.addProperty("name", cr.getName());
            }
            if (cr.hasTitle()) {
              item.addProperty("title", cr.getTitle());
            }
            if (cr.hasVersion()) {
              item.addProperty("version", cr.getVersion());
            }
            if (cr.hasDate()) {
              item.addProperty("date", cr.getDateElement().primitiveValue());
            }
            if (cr.hasStatus()) {
              item.addProperty("status", cr.getStatus().toCode());
            }
          }
          if (r.getResource() instanceof DomainResource) {
            org.hl7.fhir.igtools.renderers.StatusRenderer.ResourceStatusInformation info = StatusRenderer.analyse((DomainResource) r.getResource());
            JsonObject jo = new JsonObject();
            if (info.getColorClass() != null) {
              jo.addProperty("class", info.getColorClass());
            }
            if (info.getFmm() != null) {
              jo.addProperty("fmm", info.getFmm());
            }
            if (info.getOwner() != null) {
              jo.addProperty("owner", info.getOwner());
            }
            if (info.getOwnerLink() != null) {
              jo.addProperty("link", info.getOwnerLink());
            }
            if (info.getSstatus() != null) {
              jo.addProperty("standards-status", info.getSstatus());
            }
            if (info.getStatus() != null) {
              jo.addProperty("status", info.getStatus());
            }
            if (!jo.entrySet().isEmpty()) {
              item.add("status", jo);
            }
          }
        }
        i++;
      }
    }

    json = gson.toJson(data);
    TextFile.stringToFile(json, Utilities.path(tempDir, "_data", "resources.json"), false);

    if (publishedIg.getDefinition().hasPage()) {
      JsonObject pages = new JsonObject();
      addPageData(pages, publishedIg.getDefinition().getPage(), "0", "");
      //   gson = new GsonBuilder().setPrettyPrinting().create();
      //   json = gson.toJson(pages);
      Entry<String, JsonElement> priorEntry = null;
      for (Entry<String, JsonElement> entry: pages.entrySet()) {
        if (priorEntry!=null) {
          String priorPageUrl = priorEntry.getKey();
          String currentPageUrl = entry.getKey();
          JsonObject priorPageData = priorEntry.getValue().getAsJsonObject();
          JsonObject currentPageData = entry.getValue().getAsJsonObject();
          priorPageData.addProperty("next", currentPageUrl);
          currentPageData.addProperty("previous", priorPageUrl);
        }
        priorEntry = entry;
      }
      json = pages.toString();
      TextFile.stringToFile(json, Utilities.path(tempDir, "_data", "pages.json"), false);

      createToc();
      if (htmlTemplate != null || mdTemplate != null) {
        applyPageTemplate(htmlTemplate, mdTemplate, publishedIg.getDefinition().getPage());
      }
    }
  }


  private void generateCanonicalSummary() throws IOException {
    List<CanonicalResource> crlist = new ArrayList<>();
    for (FetchedFile f : fileList) {
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
      obj.addProperty("id", cr.getId());
      obj.addProperty("type", cr.fhirType());
      if (cr.hasUrl()) {
        obj.addProperty("url", cr.getUrl());
      }
      if (cr.hasVersion()) {
        obj.addProperty("version", cr.getVersion());
      }
      if (cr.hasName()) {
        obj.addProperty("name", cr.getName());
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
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    String json = gson.toJson(list);
    TextFile.stringToFile(json, Utilities.path(tempDir, "_data", "canonicals.json"), false);
    TextFile.stringToFile(json, Utilities.path(tempDir, "canonicals.json"), false);
    otherFilesRun.add(Utilities.path(tempDir, "canonicals.json"));
    
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
      tr.td().ah(cr.getUserString("path")).tx(cr.getUrl());
      tr.td().tx(cr.getId());
      tr.td().tx(cr.getVersion());
      tr.td().tx(bo.toString());
      tr.td().tx(bu.toString());      
    }
    String xhtml = new XhtmlComposer(true).compose(tbl);
    fragment("canonical-index", xhtml, otherFilesRun);
  }

  public String license() throws Exception {
    if (Utilities.noString(license)) {
      throw new Exception("A license is required in the configuration file, and it must be a SPDX license identifier (see https://spdx.org/licenses/), or \"not-open-source\"");
    }
    return license;
  }

  public SPDXLicense licenseAsEnum() throws Exception {
    return SPDXLicense.fromCode(license());
  }

  private String url(List<ContactPoint> telecom) {
    for (ContactPoint cp : telecom) {
      if (cp.getSystem() == ContactPointSystem.URL) {
        return cp.getValue();
      }
    }
    return null;
  }


  private String email(List<ContactPoint> telecom) {
    for (ContactPoint cp : telecom) {
      if (cp.getSystem() == ContactPointSystem.EMAIL) {
        return cp.getValue();
      }
    }
    return null;
  }

  private void applyPageTemplate(String htmlTemplate, String mdTemplate, ImplementationGuideDefinitionPageComponent page) throws Exception {
    String p = page.getNameUrlType().getValue();
    String sourceName = null;
    String template = null;
    if (htmlTemplate != null && page.getGeneration() == GuidePageGeneration.HTML  && !relativeNames.keySet().contains(p) && p.endsWith(".html")) {
      sourceName = p.substring(0, p.indexOf(".html")) + ".xml";
      template = htmlTemplate;
    } else if (mdTemplate != null && page.getGeneration() == GuidePageGeneration.MARKDOWN  && !relativeNames.keySet().contains(p) && p.endsWith(".html")) {
      sourceName = p.substring(0, p.indexOf(".html")) + ".md";
      template = mdTemplate;
    }
    if (sourceName!=null) {
      String sourcePath = Utilities.path("_includes", sourceName);
      if (!relativeNames.keySet().contains(sourcePath) && !sourceName.equals("toc.xml")) {
        throw new Exception("Template based HTML file " + p + " is missing source file " + sourceName);
      }
      FetchedFile f = relativeNames.get(sourcePath);
      String s = "---\r\n---\r\n{% include " + template + "%}";
      String targetPath = Utilities.path(tempDir, p);
      TextFile.stringToFile(s, targetPath, false);
      if (f==null) { // toc.xml
        checkMakeFile(s.getBytes(), targetPath, otherFilesRun);
      } else {
        checkMakeFile(s.getBytes(), targetPath, f.getOutputNames());
      }
    }

    for (ImplementationGuideDefinitionPageComponent childPage : page.getPage()) {
      applyPageTemplate(htmlTemplate, mdTemplate, childPage);
    }
  }

  private String breadCrumbForPage(ImplementationGuideDefinitionPageComponent page, boolean withLink) throws FHIRException {
    if (withLink) {
      return "<li><a href='" + page.getNameUrlType().getValue() + "'><b>" + Utilities.escapeXml(page.getTitle()) + "</b></a></li>";
    } else {
      return "<li><b>" + Utilities.escapeXml(page.getTitle()) + "</b></li>";
    }
  }

  private void addPageData(JsonObject pages, ImplementationGuideDefinitionPageComponent page, String label, String breadcrumb) throws FHIRException {
    if (!page.hasNameUrlType()) {
      errors.add(new ValidationMessage(Source.Publisher, IssueType.REQUIRED, "Base IG resource", "The page \""+page.getTitle()+"\" is missing a name/source element", IssueSeverity.ERROR));
    } else {
      addPageData(pages, page, page.getNameUrlType().getValue(), page.getTitle(), label, breadcrumb);
    }
  }

  private void addPageData(JsonObject pages, ImplementationGuideDefinitionPageComponent page, String source, String title, String label, String breadcrumb) throws FHIRException {
    FetchedResource r = resources.get(source);
    if (r==null) {
      addPageDataRow(pages, source, title, label + (page.hasPage() ? ".0" : ""), breadcrumb + breadCrumbForPage(page, false), null);
//      addPageDataRow(pages, source, title, label + (page.hasPage() ? ".0" : ""), breadcrumb + breadCrumbForPage(page, false), null);
    } else {
      Map<String, String> vars = makeVars(r);
      String outputName = determineOutputName(igpkp.getProperty(r, "base"), r, vars, null, "");
      addPageDataRow(pages, outputName, title, label, breadcrumb + breadCrumbForPage(page, false), r.getStatedExamples());
//      addPageDataRow(pages, source, title, label, breadcrumb + breadCrumbForPage(page, false), r.getStatedExamples());
      for (String templateName: extraTemplateList) {
        if (r.getConfig().get("template-"+templateName)!=null && !r.getConfig().get("template-"+templateName).getAsString().isEmpty()) {
          if (templateName.equals("format")) {
            String templateDesc = extraTemplates.get(templateName);
            for (String format: template.getFormats()) {
              String formatTemplateDesc = templateDesc.replace("FMT", format.toUpperCase());
              if (igpkp.wantGen(r, format)) {
                outputName = determineOutputName(igpkp.getProperty(r, "format"), r, vars, format, "");
                addPageDataRow(pages, outputName, page.getTitle() + " - " + formatTemplateDesc, label, breadcrumb + breadCrumbForPage(page, false), null);
              }
            }
          } else if (page.hasGeneration() && page.getGeneration().equals(GuidePageGeneration.GENERATED) /*page.getKind().equals(ImplementationGuide.GuidePageKind.RESOURCE) */) {
            boolean showPage = true;
            if (historyTemplates.contains(templateName) && r.getAudits().isEmpty())
              showPage = false;
            if (exampleTemplates.contains(templateName) && r.getStatedExamples().isEmpty())
              showPage = false;
            if (showPage) {
              String templateDesc = extraTemplates.get(templateName);
              outputName = igpkp.getProperty(r, templateName);
              if (outputName==null)
                throw new FHIRException("Error in publisher template.  Unable to find file-path property " + templateName + " for resource type " + r.fhirType() + " when property template-" + templateName + " is defined.");
              outputName = igpkp.doReplacements(outputName, r, vars, "");
              addPageDataRow(pages, outputName, page.getTitle() + " - " + templateDesc, label, breadcrumb + breadCrumbForPage(page, false), null);
            }
          }          
        }
      }
    }

    int i = 1;
    for (ImplementationGuideDefinitionPageComponent childPage : page.getPage()) {
      addPageData(pages, childPage, (label.equals("0") ? "" : label+".") + Integer.toString(i), breadcrumb + breadCrumbForPage(page, true));
      i++;
    }
  }


  private void addPageDataRow(JsonObject pages, String url, String title, String label, String breadcrumb, Set<FetchedResource> examples) throws FHIRException {
    JsonObject jsonPage = new JsonObject();
    pages.add(url, jsonPage);
    jsonPage.addProperty("title", title);
    jsonPage.addProperty("label", label);
    jsonPage.addProperty("breadcrumb", breadcrumb);

    String baseUrl = url;

    if (baseUrl.indexOf(".html") > 0) {
    	baseUrl = baseUrl.substring(0, baseUrl.indexOf(".html"));
    }

    for (String pagesDir: pagesDirs) {
      String contentFile = pagesDir + File.separator + "_includes" + File.separator + baseUrl + "-intro.xml";
      if (new File(contentFile).exists()) {
        jsonPage.addProperty("intro", baseUrl+"-intro.xml");
        jsonPage.addProperty("intro-type", "xml");
      } else {
        contentFile = pagesDir + File.separator + "_includes" + File.separator + baseUrl + "-intro.md";
        if (new File(contentFile).exists()) {
          jsonPage.addProperty("intro", baseUrl+"-intro.md");
          jsonPage.addProperty("intro-type", "md");
        }
      }

      contentFile = pagesDir + File.separator + "_includes" + File.separator + baseUrl + "-notes.xml";
      if (new File(contentFile).exists()) {
        jsonPage.addProperty("notes", baseUrl+"-notes.xml");
        jsonPage.addProperty("notes-type", "xml");
      } else {
        contentFile = pagesDir + File.separator + "_includes" + File.separator + baseUrl + "-notes.md";
        if (new File(contentFile).exists()) {
          jsonPage.addProperty("notes", baseUrl+"-notes.md");
          jsonPage.addProperty("notes-type", "md");
        }
      }
    }

    for (String prePagesDir: prePagesDirs) {
      PreProcessInfo ppinfo = preProcessInfo.get(prePagesDir);
      String baseFile = prePagesDir + File.separator;
      if (ppinfo.relativePath.equals("")) {
        baseFile = baseFile + "_includes" + File.separator;
      } else if (!ppinfo.relativePath.equals("_includes")) {
        continue;
      }
      baseFile = baseFile + baseUrl;
      String contentFile = baseFile + "-intro.xml";
      if (new File(contentFile).exists()) {
        jsonPage.addProperty("intro", baseUrl+"-intro.xml");
        jsonPage.addProperty("intro-type", "xml");
      } else {
        contentFile = baseFile + "-intro.md";
        if (new File(contentFile).exists()) {
          jsonPage.addProperty("intro", baseUrl+"-intro.md");
          jsonPage.addProperty("intro-type", "md");
        }
      }
  
      contentFile = baseFile + "-notes.xml";
      if (new File(contentFile).exists()) {
        jsonPage.addProperty("notes", baseUrl+"-notes.xml");
        jsonPage.addProperty("notes-type", "xml");
      } else {
        contentFile = baseFile + "-notes.md";
        if (new File(contentFile).exists()) {
          jsonPage.addProperty("notes", baseUrl+"-notes.md");
          jsonPage.addProperty("notes-type", "md");
        }        
      }
    }
    
    if (examples != null) {
      JsonArray exampleArray = new JsonArray();
      jsonPage.add("examples", exampleArray);

      TreeSet<ImplementationGuideDefinitionPageComponent> examplePages = new TreeSet<ImplementationGuideDefinitionPageComponent>(new ImplementationGuideDefinitionPageComponentComparator());
      for (FetchedResource exampleResource: examples) {
        ImplementationGuideDefinitionPageComponent page = pageForFetchedResource(exampleResource);
        if (page!=null)
          examplePages.add(page);
        // else
        //   throw new Error("Unable to find page for resource "+ exampleResource.getId());
      }
      for (ImplementationGuideDefinitionPageComponent examplePage : examplePages) {
        JsonObject exampleItem = new JsonObject();
        exampleArray.add(exampleItem);
        exampleItem.addProperty("url", examplePage.getNameUrlType().getValue());
        exampleItem.addProperty("title", examplePage.getTitle());
      }
    }
  }

    
  private void createToc() throws IOException, FHIRException {
    createToc(null, null, null);
  }
  
  private void createToc(ImplementationGuideDefinitionPageComponent insertPage, String insertAfterName, String insertOffset) throws IOException, FHIRException {
    String s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><div style=\"col-12\"><table style=\"border:0px;font-size:11px;font-family:verdana;vertical-align:top;\" cellpadding=\"0\" border=\"0\" cellspacing=\"0\"><tbody>";
    s = s + createTocPage(publishedIg.getDefinition().getPage(), insertPage, insertAfterName, insertOffset, null, "", "0", false);
    s = s + "</tbody></table></div>";
    TextFile.stringToFile(s, Utilities.path(tempDir, "_includes", "toc.xml"), false);
  }

  private String createTocPage(ImplementationGuideDefinitionPageComponent page, ImplementationGuideDefinitionPageComponent insertPage, String insertAfterName, String insertOffset, String currentOffset, String indents, String label, boolean last) throws FHIRException {
    String s = "<tr style=\"border:0px;padding:0px;vertical-align:top;background-color:white;\">";
    s = s + "<td style=\"vertical-align:top;text-align:left;background-color:white;padding:0px 4px 0px 4px;white-space:nowrap;background-image:url(tbl_bck0.png)\" class=\"hierarchy\">";
    s = s + "<img style=\"background-color:inherit\" alt=\".\" class=\"hierarchy\" src=\"tbl_spacer.png\"/>";
    s = s + indents;
    if (!label.equals("0")) {
      if (last)
        s = s + "<img style=\"background-color:inherit\" alt=\".\" class=\"hierarchy\" src=\"tbl_vjoin_end.png\"/>";
      else
        s = s + "<img style=\"background-color:inherit\" alt=\".\" class=\"hierarchy\" src=\"tbl_vjoin.png\"/>";
    }
    s = s + "<img style=\"background-color:white;background-color:inherit\" alt=\".\" class=\"hierarchy\" src=\"icon_page.gif\"/>";
    s = s + "<a title=\"" + Utilities.escapeXml(page.getTitle()) + "\" href=\"" + (currentOffset!=null ? currentOffset + "/" : "") + (page.hasNameUrlType() ? page.getNameUrlType().getValue() : "?name?") +"\">" + label + " " + Utilities.escapeXml(page.getTitle()) + "</a></td></tr>";

    int total = page.getPage().size();
    int i = 1;
    for (ImplementationGuideDefinitionPageComponent childPage : page.getPage()) {
      String newIndents = indents;
      if (!label.equals("0")) {
        if (last)
          newIndents = newIndents + "<img style=\"background-color:inherit\" alt=\".\" class=\"hierarchy\" src=\"tbl_blank.png\"/>";
        else
          newIndents = newIndents + "<img style=\"background-color:inherit\" alt=\".\" class=\"hierarchy\" src=\"tbl_vline.png\"/>";
      }
      if (insertAfterName!=null && childPage.getNameUrlType().getValue().equals(insertAfterName)) {
        total++;
      }
      
      s = s + createTocPage(childPage, insertPage, insertAfterName, insertOffset, currentOffset, newIndents, (label.equals("0") ? "" : label+".") + Integer.toString(i), i==total);
      i++;
      if (insertAfterName!=null && childPage.getNameUrlType().getValue().equals(insertAfterName)) {
        s = s + createTocPage(insertPage, null, null, "", insertOffset, newIndents, (label.equals("0") ? "" : label+".") + Integer.toString(i), i==total);
        i++;
      }
    }
    return s;
  }

  private ImplementationGuideDefinitionPageComponent pageForFetchedResource(FetchedResource r) throws FHIRException {
    String key = igpkp.doReplacements(igpkp.getLinkFor(r, false), r, null, null);
    return igPages.get(key);
  }

  private class ImplementationGuideDefinitionPageComponentComparator implements Comparator<ImplementationGuideDefinitionPageComponent> {
    @Override
    public int compare(ImplementationGuideDefinitionPageComponent x, ImplementationGuideDefinitionPageComponent y) {
      try {
        return x.getNameUrlType().getValue().compareTo(y.getNameUrlType().getValue());
      } catch (FHIRException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
        return 0;
      }
    }
  }

  private void generateDataFile() throws IOException, FHIRException {
    JsonObject data = new JsonObject();
    data.addProperty("path", checkAppendSlash(specPath));
    data.addProperty("canonical", igpkp.getCanonical());
    data.addProperty("igId", publishedIg.getId());
    data.addProperty("igName", publishedIg.getName());
    data.addProperty("packageId", npmName);
    data.addProperty("igVer", workingVersion());
    data.addProperty("errorCount", getErrorCount());
    data.addProperty("version", version);
    data.addProperty("revision", specMaps.get(0).getBuild());
    data.addProperty("versionFull", version+"-"+specMaps.get(0).getBuild());
    data.addProperty("toolingVersion", Constants.VERSION);
    data.addProperty("toolingRevision", ToolsVersion.TOOLS_VERSION_STR);
    data.addProperty("toolingVersionFull", Constants.VERSION+" ("+ToolsVersion.TOOLS_VERSION_STR+")");
    data.addProperty("totalFiles", fileList.size());
    data.addProperty("processedFiles", changeList.size());
    data.addProperty("genDate", genTime());
    data.addProperty("genDay", genDate());
    JsonObject ig = new JsonObject();
    data.add("ig", ig);
    ig.addProperty("id", publishedIg.getId());
    ig.addProperty("name", publishedIg.getName());
    ig.addProperty("title", publishedIg.getTitle());
    ig.addProperty("url", publishedIg.getUrl());
    ig.addProperty("version", workingVersion());
    ig.addProperty("status", publishedIg.getStatusElement().asStringValue());
    ig.addProperty("experimental", publishedIg.getExperimental());
    ig.addProperty("publisher", publishedIg.getPublisher());
    ig.addProperty("gitstatus", getGitStatus());
    if (previousVersionComparator != null && previousVersionComparator.hasLast() && !targetUrl().startsWith("file:")) {
      JsonObject diff = new JsonObject();
      data.add("diff", diff);
      diff.addProperty("name", Utilities.encodeUri(previousVersionComparator.getLastName()));
      diff.addProperty("current", Utilities.encodeUri(targetUrl()));
      diff.addProperty("previous", Utilities.encodeUri(previousVersionComparator.getLastUrl()));
    }
    
    if (publishedIg.hasContact()) {
      JsonArray jc = new JsonArray();
      ig.add("contact", jc);
      for (ContactDetail c : publishedIg.getContact()) {
        JsonObject jco = new JsonObject();
        jc.add(jco);
        jco.addProperty("name", c.getName());
        if (c.hasTelecom()) {
          JsonArray jct = new JsonArray();
          jco.add("telecom", jct);
          for (ContactPoint cc : c.getTelecom()) {
            jct.add(new JsonPrimitive(cc.getValue()));
          }
        }
      }
    }
    ig.addProperty("date", publishedIg.getDateElement().asStringValue());
    ig.addProperty("description", publishedIg.getDescription());
    ig.addProperty("copyright", publishedIg.getCopyright());
    for (Enumeration<FHIRVersion> v : publishedIg.getFhirVersion()) {
      ig.addProperty("fhirVersion", v.asStringValue());
      break;
    }

    for (SpecMapManager sm : specMaps) {
      if (sm.getName() != null) {
        data.addProperty(sm.getName(), appendTrailingSlashInDataFile ? sm.getBase() : Utilities.appendForwardSlash(sm.getBase()));
        if (!data.has("ver")) {
          data.add("ver", new JsonObject());
        }
        data.getAsJsonObject("ver").addProperty(sm.getName(), appendTrailingSlashInDataFile ? sm.getBase2() : Utilities.appendForwardSlash(sm.getBase2()));
      }
    }
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    String json = gson.toJson(data);
    TextFile.stringToFile(json, Utilities.path(tempDir, "_data", "fhir.json"), false);
  }

  public String workingVersion() {
    return businessVersion == null ? publishedIg.getVersion() : businessVersion;
  }

  private String getGitStatus() throws IOException {
    File gitDir = new File(Utilities.path(Utilities.getDirectoryForFile(configFile), ".git"));
    if (!gitDir.exists()) {
      return "";      
    }
    String head = TextFile.fileToString(Utilities.path(gitDir.getAbsolutePath(), "HEAD")).trim();
    return head.substring(head.lastIndexOf("/")+1);
  }

  private void generateResourceReferences() throws Exception {
    Set<String> resourceTypes = new HashSet<>();
    for (StructureDefinition sd : context.allStructures()) {
      if (sd.getDerivation() == TypeDerivationRule.SPECIALIZATION && sd.getKind() == StructureDefinitionKind.RESOURCE) {
        resourceTypes.add(sd.getType());
      }
    }
    for (String rt : resourceTypes) {
      generateResourceReferences(rt);
    }
    generateProfiles();
    generateExtensions();
    generateLogicals();
  }

  private class Item {
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
  private class ItemSorter implements Comparator<Item> {

    @Override
    public int compare(Item a0, Item a1) {
      return a0.sort.compareTo(a1.sort);
    }
  }

  private void generateProfiles() throws Exception {
    List<Item> items = new ArrayList<Item>();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("StructureDefinition")) {
          StructureDefinition sd = (StructureDefinition) r.getResource();
          if (sd.getDerivation() == TypeDerivationRule.CONSTRAINT && sd.getKind() == StructureDefinitionKind.RESOURCE) {
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
      fragment("list-profiles", list.toString(), otherFilesRun);
      fragment("list-simple-profiles", lists.toString(), otherFilesRun);
      fragment("table-profiles", table.toString(), otherFilesRun);
      fragment("list-profiles-mm", listMM.toString(), otherFilesRun);
      fragment("list-simple-profiles-mm", listsMM.toString(), otherFilesRun);
      fragment("table-profiles-mm", tableMM.toString(), otherFilesRun);
    }
  }

  private void generateExtensions() throws Exception {
    List<Item> items = new ArrayList<Item>();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("StructureDefinition")) {
          StructureDefinition sd = (StructureDefinition) r.getResource();
          if (sd.getDerivation() == TypeDerivationRule.CONSTRAINT && sd.getType().equals("Extension")) {
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
    fragment("list-extensions", list.toString(), otherFilesRun);
    fragment("list-simple-extensions", lists.toString(), otherFilesRun);
    fragment("table-extensions", table.toString(), otherFilesRun);
    fragment("list-extensions-mm", listMM.toString(), otherFilesRun);
    fragment("list-simple-extensions-mm", listsMM.toString(), otherFilesRun);
    fragment("table-extensions-mm", tableMM.toString(), otherFilesRun);
  }

  private void generateLogicals() throws Exception {
    List<Item> items = new ArrayList<Item>();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("StructureDefinition")) {
          StructureDefinition sd = (StructureDefinition) r.getResource();
          if (sd.getKind() == StructureDefinitionKind.LOGICAL) {
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
    fragment("list-logicals", list.toString(), otherFilesRun);
    fragment("list-simple-logicals", lists.toString(), otherFilesRun);
    fragment("table-logicals", table.toString(), otherFilesRun);
    fragment("list-logicals-mm", listMM.toString(), otherFilesRun);
    fragment("list-simple-logicals-mm", listsMM.toString(), otherFilesRun);
    fragment("table-logicals-mm", tableMM.toString(), otherFilesRun);
  }

  private void genEntryItem(StringBuilder list, StringBuilder lists, StringBuilder table, StringBuilder listMM, StringBuilder listsMM, StringBuilder tableMM, FetchedFile f, FetchedResource r, String name, String prefixType) throws Exception {
    String ref = igpkp.doReplacements(igpkp.getLinkFor(r, false), r, null, null);
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
        desc = markdownEngine.process(d, descSrc);
        descSrc = "Canonical Resource";
      }
    } else if (r.getElement() != null && r.getElement().hasChild("description")) {
      String d = new StringType(r.getElement().getChildValue("description")).asStringValue();
      if (d != null) {
        desc = markdownEngine.process(d, descSrc);
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
        String fmm = ToolingExtensions.readStringExtension((DomainResource) r.getResource(), ToolingExtensions.EXT_FMM_LEVEL);
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

  private void generateResourceReferences(String rt) throws Exception {
    List<Item> items = new ArrayList<Item>();
    for (FetchedFile f : fileList) {
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

    genResourceReferencesList(rt, items, "");
    Collections.sort(items, new ItemSorterById());
    genResourceReferencesList(rt, items, "byid-");
    Collections.sort(items, new ItemSorterByName());
    genResourceReferencesList(rt, items, "name-");
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

  public void genResourceReferencesList(String rt, List<Item> items, String ext) throws Exception, IOException {
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
    fragment("list-"+ext+pm, list.toString(), otherFilesRun);
    fragment("list-simple-"+ext+pm, lists.toString(), otherFilesRun);
    fragment("table-"+ext+pm, table.toString(), otherFilesRun);
    fragment("list-"+ext+pm+"-json", listJ.toString(), otherFilesRun);
    fragment("list-simple-"+ext+pm+"-json", listsJ.toString(), otherFilesRun);
    fragment("table-"+ext+pm+"-json", tableJ.toString(), otherFilesRun);
    fragment("list-"+ext+pm+"-xml", listX.toString(), otherFilesRun);
    fragment("list-simple-"+ext+pm+"-xml", listsX.toString(), otherFilesRun);
    fragment("table-"+ext+pm+"-xml", tableX.toString(), otherFilesRun);
  }

  @SuppressWarnings("rawtypes")
  private String getDesc(CanonicalResource r) {
    if (r.hasDescriptionElement()) {
      return r.getDescriptionElement().asStringValue();
    }
    return null;
  }

  private Number getErrorCount() {
    int result = countErrs(errors);
    for (FetchedFile f : fileList) {
      result = result + countErrs(f.getErrors());
    }
    return result;
  }

  private int countErrs(List<ValidationMessage> list) {
    int i = 0;
    for (ValidationMessage vm : list) {
      if (vm.getLevel() == IssueSeverity.ERROR || vm.getLevel() == IssueSeverity.FATAL)
        i++;
    }
    return i;
  }

  private void log(String s) {
    logger.logMessage(s);
  }

  private void generateNativeOutputs(FetchedFile f, boolean regen) throws IOException, FHIRException {
    for (FetchedResource r : f.getResources()) {
        logDebugMessage(LogCategory.PROGRESS, "Produce resources for "+r.fhirType()+"/"+r.getId());
        saveNativeResourceOutputs(f, r);
    }    
  }
  private void generateHtmlOutputs(FetchedFile f, boolean regen) throws Exception {
    if (f.getProcessMode() == FetchedFile.PROCESS_NONE) {
      String dst = tempDir;
      if (f.getRelativePath().startsWith(File.separator))
        dst = dst + f.getRelativePath();
      else
        dst = dst + File.separator + f.getRelativePath();
      try {
        if (f.isFolder()) {
          f.getOutputNames().add(dst);
          Utilities.createDirectory(dst);
        } else
          checkMakeFile(f.getSource(), dst, f.getOutputNames());
      } catch (IOException e) {
        log("Exception generating page "+dst+" for "+f.getRelativePath()+" in "+tempDir+": "+e.getMessage());
         
      }
    } else if (f.getProcessMode() == FetchedFile.PROCESS_XSLT) {
//      String dst = tempDir + f.getPath().substring(prePagesDir.length());
      String dst = tempDir;
      if (f.getRelativePath().startsWith(File.separator))
        dst = dst + f.getRelativePath();
      else
        dst = dst + File.separator + f.getRelativePath();
      try {
        if (f.isFolder()) {
          f.getOutputNames().add(dst);
          Utilities.createDirectory(dst);
        } else
          checkMakeFile(transform(f.getSource(), f.getXslt()), dst, f.getOutputNames());
      } catch (Exception e) {
        log("Exception generating xslt page "+dst+" for "+f.getRelativePath()+" in "+tempDir+": "+e.getMessage());
      }
    } else {
      saveFileOutputs(f);
      for (FetchedResource r : f.getResources()) {
        logDebugMessage(LogCategory.PROGRESS, "Produce outputs for "+r.fhirType()+"/"+r.getId());
        Map<String, String> vars = makeVars(r);
        makeTemplates(f, r, vars);
        saveDirectResourceOutputs(f, r, r.getResource(), vars);
        if (r.getResource() != null) {
          generateResourceHtml(f, regen, r, r.getResource(), vars, "");
          if (r.getResource() instanceof CanonicalResource) {
            CanonicalResource container = (CanonicalResource) r.getResource();
            for (Resource contained : container.getContained()) {              
              String prefixForContained = r.getResource().getId()+"_";
              makeTemplatesContained(f, r, contained, vars, prefixForContained);
              saveDirectResourceOutputsContained(f, r, contained, vars, prefixForContained);
              if (contained instanceof CanonicalResource) {
                CanonicalResource cr = ((CanonicalResource) contained).copy();
                cr.copyUserData(container);
                if (!cr.hasUrl()) {
                  cr.setUrl(container.getUrl()+"#"+contained.getId());
                }
                if (!cr.hasVersion()) {
                  cr.setVersion(container.getVersion());
                }
                generateResourceHtml(f, regen, r, cr, vars, prefixForContained);                  
              } else {
                generateResourceHtml(f, regen, r, contained, vars, prefixForContained);                
              }
            }
          }
        } else {
          if ("QuestionnaireResponse".equals(r.fhirType())) {
            String prefixForContained = "";
            generateOutputsQuestionnaireResponse(f, r, vars, prefixForContained);
          }
        }
      }
    }
  }

  public boolean generateResourceHtml(FetchedFile f, boolean regen, FetchedResource r, Resource res, Map<String, String> vars, String prefixForContainer) {
    boolean result = true;
    try {

      // now, start generating resource type specific stuff
      switch (res.getResourceType()) {
      case CodeSystem:
        generateOutputsCodeSystem(f, r, (CodeSystem) res, vars, prefixForContainer);
        break;
      case ValueSet:
        generateOutputsValueSet(f, r, (ValueSet) res, vars, prefixForContainer);
        break;
      case ConceptMap:
        generateOutputsConceptMap(f, r, (ConceptMap) res, vars, prefixForContainer);
        break;

      case List:
        generateOutputsList(f, r, (ListResource) res, vars, prefixForContainer);      
        break;
        
      case CapabilityStatement:
        generateOutputsCapabilityStatement(f, r, (CapabilityStatement) res, vars, prefixForContainer);
        break;
      case StructureDefinition:
        generateOutputsStructureDefinition(f, r, (StructureDefinition) res, vars, regen, prefixForContainer);
        break;
      case OperationDefinition:
        generateOutputsOperationDefinition(f, r, (OperationDefinition) res, vars, regen, prefixForContainer);
        break;
      case StructureMap:
        generateOutputsStructureMap(f, r, (StructureMap) res, vars, prefixForContainer);
        break;
      case Questionnaire:
        generateOutputsQuestionnaire(f, r, (Questionnaire) res, vars, prefixForContainer);
        break;
      case Library:
        generateOutputsLibrary(f, r, (Library) res, vars, prefixForContainer);
        break;
      default:
        if (res instanceof CanonicalResource) {
          generateOutputsCanonical(f, r, (CanonicalResource) res, vars, prefixForContainer);          
        }
        // nothing to do...
        result = false;
      }
    } catch (Exception e) {
      log("Exception generating resource "+f.getName()+"::"+r.fhirType()+"/"+r.getId()+(!Utilities.noString(prefixForContainer) ? "#"+res.getId() : "")+": "+e.getMessage());
      e.printStackTrace();
      for (StackTraceElement m : e.getStackTrace()) {
          log("   "+m.toString());
      }
    }
    return result;
  }


  private void generateOutputsOperationDefinition(FetchedFile f, FetchedResource r, OperationDefinition od, Map<String, String> vars, boolean regen, String prefixForContainer) throws FHIRException, IOException {
    OperationDefinitionRenderer odr = new OperationDefinitionRenderer(context, checkAppendSlash(specPath), od, Utilities.path(tempDir), igpkp, specMaps, markdownEngine, packge, fileList, rc);
    if (igpkp.wantGen(r, "summary")) {
      fragment("OperationDefinition-"+prefixForContainer+od.getId()+"-summary", odr.summary(), f.getOutputNames(), r, vars, null);
    }
    if (igpkp.wantGen(r, "summary-table")) {
      fragment("OperationDefinition-"+prefixForContainer+od.getId()+"-summary-table", odr.summary(), f.getOutputNames(), r, vars, null);
    }
    if (igpkp.wantGen(r, "idempotence")) {
      fragment("OperationDefinition-"+prefixForContainer+od.getId()+"-idempotence", odr.idempotence(), f.getOutputNames(), r, vars, null);
    }
  }

  public class ListItemEntry {

    private String id;
    private String link;
    private String name;
    private String desc;
    private String type;
    private Element element;

    public ListItemEntry(String type, String id, String link, String name, String desc, Element element) {
      super();
      this.type = type;
      this.id = id;
      this.link = link;
      this.name = name;
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


  private void generateOutputsList(FetchedFile f, FetchedResource r, ListResource resource, Map<String, String> vars, String prefixForContainer) throws IOException, FHIRException {
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
    
    for (ListResourceEntryComponent li : resource.getEntry()) {
      if (!li.getDeleted() && li.hasItem() && li.getItem().hasReference()) {
        String ref = li.getItem().getReference();
        FetchedResource lr = getResourceForUri(f, ref);
        if (lr == null)
          lr = getResourceForRef(f, ref);
        if (lr != null) {
          list.add(new ListItemEntry(lr.fhirType(), getListId(lr), getListLink(lr), getListName(lr), getListDesc(lr), lr.getElement()));          
        } else {
          // ok, we'll see if we can resolve it from another spec 
          Resource l = context.fetchResource(null, ref);
          if (l== null && ref.matches(Constants.LOCAL_REF_REGEX)) {
            String[] p = ref.split("\\/");
            l = context.fetchResourceById(p[0], p[1]);
          }
          if (l != null)
            list.add(new ListItemEntry(l.fhirType(), getListId(l), getListLink(l), getListName(l), getListDesc(l), null));          
        }
      }
    }
    String script = igpkp.getProperty(r, "list-script");
    String types = igpkp.getProperty(r, "list-types"); 
    genListViews(f, r, resource, list, script, "no", null);
    if (types != null) {
      for (String t : types.split("\\|")) {
        genListViews(f, r, resource, list, script, "no", t.trim());
      }
    }
    Collections.sort(list, new ListViewSorterById());
    genListViews(f, r, resource, list, script, "id", null);
    if (types != null) {
      for (String t : types.split("\\|")) {
        genListViews(f, r, resource, list, script, "id", t.trim());
      }
    }
    Collections.sort(list, new ListViewSorterByName());
    genListViews(f, r, resource, list, script, "name", null);
    if (types != null) {
      for (String t : types.split("\\|")) {
        genListViews(f, r, resource, list, script, "name", t.trim());
      }
    }
    
    // now, if the list has a package-id extension, generate the package for the list
    if (resource.hasExtension(ToolingExtensions.EXT_LIST_PACKAGE)) {
      Extension ext = resource.getExtensionByUrl(ToolingExtensions.EXT_LIST_PACKAGE);
      String id = ToolingExtensions.readStringExtension(ext, "id");
      String name = ToolingExtensions.readStringExtension(ext, "name");
      String dfn = Utilities.path(tempDir, id+".tgz");
      NPMPackageGenerator gen = NPMPackageGenerator.subset(npm, dfn, id, name, execTime.getTime(), !publishing);
      for (ListItemEntry i : list) {
        if (i.element != null) {
          ByteArrayOutputStream bs = new ByteArrayOutputStream();
          new org.hl7.fhir.r5.elementmodel.JsonParser(context).compose(i.element, bs, OutputStyle.NORMAL, igpkp.getCanonical());
          gen.addFile(Category.RESOURCE, i.element.fhirType()+"-"+i.element.getIdBase()+".json", bs.toByteArray());
        }
      }
      gen.finish();
      otherFilesRun.add(Utilities.path(tempDir, id+".tgz"));
    }
    
  }


  public void genListViews(FetchedFile f, FetchedResource r, ListResource resource, List<ListItemEntry> list, String script, String id, String type) throws IOException, FHIRException {
    if (igpkp.wantGen(r, "list-list"))
      fragmentIfNN("List-"+resource.getId()+"-list-"+id+(type == null ? "" : "-"+type), genListView(list, "<li><a href=\"{{link}}\">{{name}}</a> {{desc}}</li>\r\n", type), f.getOutputNames());
    if (igpkp.wantGen(r, "list-list-simple"))
      fragmentIfNN("List-"+resource.getId()+"-list-"+id+"-simple"+(type == null ? "" : "-"+type), genListView(list, "<li><a href=\"{{link}}\">{{name}}</a></li>\r\n", type), f.getOutputNames());
    if (igpkp.wantGen(r, "list-list-table"))
      fragmentIfNN("List-"+resource.getId()+"-list-"+id+"-table"+(type == null ? "" : "-"+type), genListView(list, "<tr><td><a href=\"{{link}}\">{{name}}</a></td><td>{{desc}}</td></tr>\r\n", type), f.getOutputNames());
    if (script != null)
      fragmentIfNN("List-"+resource.getId()+"-list-"+id+"-script"+(type == null ? "" : "-"+type), genListView(list, script, type), f.getOutputNames());
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
        if (s.contains("{{desc}}"))
          s = s.replace("{{desc}}", i.getDesc() == null ? "" : trimPara(markdownEngine.process(i.getDesc(), "List reference description")));
        b.append(s);
      }
    }
    return b.toString();
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
    if (lr.getResource() != null && lr.getResource().hasUserData("path"))
      res = lr.getResource().getUserString("path");
    else
      res = igpkp.getLinkFor(lr, true);
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
    return r.getUserString("path");
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

  private byte[] transform(byte[] source, byte[] xslt) throws TransformerException {
    TransformerFactory f = TransformerFactory.newInstance();
    StreamSource xsrc = new StreamSource(new ByteArrayInputStream(xslt));
    Transformer t = f.newTransformer(xsrc);

    StreamSource src = new StreamSource(new ByteArrayInputStream(source));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    StreamResult res = new StreamResult(out);
    t.transform(src, res);
    return out.toByteArray();
  }

  private Map<String, String> makeVars(FetchedResource r) {
    Map<String, String> map = new HashMap<String, String>();
    if (r.getResource() != null) {
      switch (r.getResource().getResourceType()) {
      case StructureDefinition:
        StructureDefinition sd = (StructureDefinition) r.getResource();
        String url = sd.getBaseDefinition();
        StructureDefinition base = context.fetchResource(StructureDefinition.class, url);
        if (base != null) {
          map.put("parent-name", base.getName());
          map.put("parent-link", base.getUserString("path"));
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
  private void saveNativeResourceOutputs(FetchedFile f, FetchedResource r) throws FHIRException, IOException {
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    new org.hl7.fhir.r5.elementmodel.JsonParser(context).compose(r.getElement(), bs, OutputStyle.NORMAL, igpkp.getCanonical());
    npm.addFile(isExample(f,r ) ? Category.EXAMPLE : Category.RESOURCE, r.fhirType()+"-"+r.getId()+".json", bs.toByteArray());
    String path = Utilities.path(tempDir, "_includes", r.fhirType()+"-"+r.getId()+".json");
    String json = new String(bs.toByteArray());
    TextFile.stringToFile(json, path);
    path = Utilities.path(tempDir, "_includes", r.fhirType()+"-"+r.getId()+".escaped.json");
    json = Utilities.escapeXml(json);
    TextFile.stringToFile(json, path);
    
    if (igpkp.wantGen(r, "xml") || forHL7orFHIR()) {
      path = Utilities.path(tempDir, r.fhirType()+"-"+r.getId()+".xml");
      f.getOutputNames().add(path);
      FileOutputStream stream = new FileOutputStream(path);
      new org.hl7.fhir.r5.elementmodel.XmlParser(context).compose(r.getElement(), stream, OutputStyle.PRETTY, igpkp.getCanonical());
      stream.close();
    }
    if (igpkp.wantGen(r, "json") || forHL7orFHIR()) {
      path = Utilities.path(tempDir, r.fhirType()+"-"+r.getId()+".json");
      f.getOutputNames().add(path);
      FileOutputStream stream = new FileOutputStream(path);
      new org.hl7.fhir.r5.elementmodel.JsonParser(context).compose(r.getElement(), stream, OutputStyle.PRETTY, igpkp.getCanonical());
      stream.close();
    } 
    if (igpkp.wantGen(r, "ttl")) {
      path = Utilities.path(tempDir, r.fhirType()+"-"+r.getId()+".ttl");
      f.getOutputNames().add(path);
      FileOutputStream stream = new FileOutputStream(path);
      new org.hl7.fhir.r5.elementmodel.TurtleParser(context).compose(r.getElement(), stream, OutputStyle.PRETTY, igpkp.getCanonical());
      stream.close();
    }    
  }

  private boolean isExample(FetchedFile f, FetchedResource r) {
    ImplementationGuideDefinitionResourceComponent igr = findIGReference(r.fhirType(), r.getId());
    if (igr == null)
      return false;
    else if (!igr.hasExample())
      return false;
    else if (igr.hasExampleCanonicalType())
      return true;
    else
      return igr.getExampleBooleanType().booleanValue();
  }


  private boolean forHL7orFHIR() {
    return igpkp.getCanonical().contains("hl7.org") || igpkp.getCanonical().contains("fhir.org") ;
  }


  private void saveFileOutputs(FetchedFile f) throws IOException, FHIRException {
    if (f.getResources().size() == 1) {
      Map<String, String> vars = new HashMap<>();
      FetchedResource r = f.getResources().get(0);
      StringBuilder b = new StringBuilder();
      b.append("<table class=\"grid\">\r\n");
      b.append("<tr><td><b>Level</b></td><td><b>Type</b></td><td><b>Location</b></td><td><b>Message</b></td></tr>\r\n");
      genVMessage(b, f.getErrors(), IssueSeverity.FATAL);
      genVMessage(b, f.getErrors(), IssueSeverity.ERROR);
      genVMessage(b, f.getErrors(), IssueSeverity.WARNING);
      genVMessage(b, f.getErrors(), IssueSeverity.INFORMATION);
      b.append("</table>\r\n");
      fragment(r.fhirType()+"-"+r.getId()+"-validation", b.toString(), f.getOutputNames(), r, vars, null);
    }
  }

  public void genVMessage(StringBuilder b, List<ValidationMessage> vms, IssueSeverity lvl) {
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

  private void makeTemplatesContained(FetchedFile f, FetchedResource r, Resource res, Map<String, String> vars, String prefixForContained) throws FileNotFoundException, Exception {
    String baseName = igpkp.getPropertyContained(r, "base", res);
    if (res != null && res instanceof StructureDefinition) {
      if (igpkp.hasProperty(r, "template-base-"+((StructureDefinition) res).getKind().toCode().toLowerCase(), res))
        genWrapperContained(f, r, res, igpkp.getPropertyContained(r, "template-base-"+((StructureDefinition) res).getKind().toCode().toLowerCase(), res), baseName, f.getOutputNames(), vars, null, "", prefixForContained);
      else
        genWrapperContained(f, r, res, igpkp.getPropertyContained(r, "template-base", res), baseName, f.getOutputNames(), vars, null, "", prefixForContained);
    } else
      genWrapperContained(f, r, res, igpkp.getPropertyContained(r, "template-base", res), baseName, f.getOutputNames(), vars, null, "", prefixForContained);
    genWrapperContained(null, r, res, igpkp.getPropertyContained(r, "template-defns", res), igpkp.getPropertyContained(r, "defns", res), f.getOutputNames(), vars, null, "definitions", prefixForContained);
    for (String templateName : extraTemplates.keySet()) {
      if (!templateName.equals("format") && !templateName.equals("defns")) {
        String output = igpkp.getProperty(r, templateName);
         if (output == null)
          output = r.fhirType()+"-"+r.getId()+"_"+res.getId()+"-"+templateName+".html";
        genWrapperContained(null, r, res, igpkp.getPropertyContained(r, "template-"+templateName, res), output, f.getOutputNames(), vars, null, templateName, prefixForContained);
      }
    }
  }
  
  private void makeTemplates(FetchedFile f, FetchedResource r, Map<String, String> vars) throws FileNotFoundException, Exception {
    String baseName = igpkp.getProperty(r, "base");
    if (r.getResource() != null && r.getResource() instanceof StructureDefinition) {
      if (igpkp.hasProperty(r, "template-base-"+((StructureDefinition) r.getResource()).getKind().toCode().toLowerCase(), null))
        genWrapper(f, r, igpkp.getProperty(r, "template-base-"+((StructureDefinition) r.getResource()).getKind().toCode().toLowerCase()), baseName, f.getOutputNames(), vars, null, "");
      else
        genWrapper(f, r, igpkp.getProperty(r, "template-base"), baseName, f.getOutputNames(), vars, null, "");
    } else
      genWrapper(f, r, igpkp.getProperty(r, "template-base"), baseName, f.getOutputNames(), vars, null, "");
    genWrapper(null, r, igpkp.getProperty(r, "template-defns"), igpkp.getProperty(r, "defns"), f.getOutputNames(), vars, null, "definitions");
    for (String templateName : extraTemplates.keySet()) {
      if (!templateName.equals("format") && !templateName.equals("defns")) {
        String output = igpkp.getProperty(r, templateName);
         if (output == null)
          output = r.fhirType()+"-"+r.getId()+"-"+templateName+".html";
        genWrapper(null, r, igpkp.getProperty(r, "template-"+templateName), output, f.getOutputNames(), vars, null, templateName);
      }
    }
  }
  
  private void saveDirectResourceOutputs(FetchedFile f, FetchedResource r, Resource res, Map<String, String> vars) throws FileNotFoundException, Exception {
    boolean example = r.isExample();
    if (igpkp.wantGen(r, "maturity") && res != null) {
      fragment(res.fhirType()+"-"+r.getId()+"-maturity",  genFmmBanner(r), f.getOutputNames());
    }
    if (igpkp.wantGen(r, "ip-statements") && res != null) {
      fragment(res.fhirType()+"-"+r.getId()+"-ip-statements",  genIpStatements(r, example), f.getOutputNames());
    }
    if (igpkp.wantGen(r, "validate")) {
      fragment(r.fhirType()+"-"+r.getId()+"-validate",  genValidation(f, r), f.getOutputNames());
    }

    if (igpkp.wantGen(r, "status") && res instanceof DomainResource) {
      fragment(r.fhirType()+"-"+r.getId()+"-status",  genStatus(f, r, res), f.getOutputNames());
    }

    String template = igpkp.getProperty(r, "template-format");
    if (igpkp.wantGen(r, "xml")) {
      if (tool == GenerationTool.Jekyll)
        genWrapper(null, r, template, igpkp.getProperty(r, "format"), f.getOutputNames(), vars, "xml", "");
    }
    if (igpkp.wantGen(r, "json")) {
      if (tool == GenerationTool.Jekyll)
        genWrapper(null, r, template, igpkp.getProperty(r, "format"), f.getOutputNames(), vars, "json", "");
    } 

    if (igpkp.wantGen(r, "ttl")) {
      if (tool == GenerationTool.Jekyll)
        genWrapper(null, r, template, igpkp.getProperty(r, "format"), f.getOutputNames(), vars, "ttl", "");
    }

    org.hl7.fhir.r5.elementmodel.XmlParser xp = new org.hl7.fhir.r5.elementmodel.XmlParser(context);
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    xp.compose(r.getElement(), bs, OutputStyle.NORMAL, null);
    int size = bs.size();
    
    if (igpkp.wantGen(r, "xml-html")) {
      XmlXHtmlRenderer x = new XmlXHtmlRenderer();
      x.setPrism(size < PRISM_SIZE_LIMIT);
      xp.setLinkResolver(igpkp);
      xp.setShowDecorations(false);
      xp.compose(r.getElement(), x);
      fragment(r.fhirType()+"-"+r.getId()+"-xml-html", x.toString(), f.getOutputNames(), r, vars, "xml");
    }
    if (igpkp.wantGen(r, "json-html")) {
      JsonXhtmlRenderer j = new JsonXhtmlRenderer();
      j.setPrism(size < PRISM_SIZE_LIMIT);
      org.hl7.fhir.r5.elementmodel.JsonParser jp = new org.hl7.fhir.r5.elementmodel.JsonParser(context);
      jp.setLinkResolver(igpkp);
      jp.compose(r.getElement(), j);
      fragment(r.fhirType()+"-"+r.getId()+"-json-html", j.toString(), f.getOutputNames(), r, vars, "json");
    }

    if (igpkp.wantGen(r, "ttl-html")) {
      org.hl7.fhir.r5.elementmodel.TurtleParser ttl = new org.hl7.fhir.r5.elementmodel.TurtleParser(context);
      ttl.setLinkResolver(igpkp);
      Turtle rdf = new Turtle();
      ttl.compose(r.getElement(), rdf, "");
      fragment(r.fhirType()+"-"+r.getId()+"-ttl-html", rdf.asHtml(), f.getOutputNames(), r, vars, "ttl");
    }

    if (igpkp.wantGen(r, "html")) {
      XhtmlNode xhtml = getXhtml(f, r);
      if (xhtml == null && HistoryGenerator.allEntriesAreHistoryProvenance(r.getElement())) {
        RenderingContext ctxt = rc.copy().setParser(getTypeLoader(f, r));
        List<ProvenanceDetails> entries = loadProvenanceForBundle(igpkp.getLinkFor(r, true), r.getElement(), f);
        xhtml = new HistoryGenerator(ctxt).generateForBundle(entries); 
        fragment(r.fhirType()+"-"+r.getId()+"-html", new XhtmlComposer(XhtmlComposer.XML).compose(xhtml), f.getOutputNames(), r, vars, null);
      } else {
        String html = xhtml == null ? "" : new XhtmlComposer(XhtmlComposer.XML).compose(xhtml);
        fragment(r.fhirType()+"-"+r.getId()+"-html", html, f.getOutputNames(), r, vars, null);
      }
    }

    if (igpkp.wantGen(r, "history")) {
      XhtmlNode xhtml = new HistoryGenerator(rc).generate(r);
      String html = xhtml == null ? "" : new XhtmlComposer(XhtmlComposer.XML).compose(xhtml);
      fragment(r.fhirType()+"-"+r.getId()+"-history", html, f.getOutputNames(), r, vars, null);
    }
   
    //  NarrativeGenerator gen = new NarrativeGenerator(null, null, context);
    //  gen.generate(f.getElement(), false);
    //  xhtml = getXhtml(f);
    //  html = xhtml == null ? "" : new XhtmlComposer().compose(xhtml);
    //  fragment(f.getId()+"-gen-html", html);
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

  private void saveDirectResourceOutputsContained(FetchedFile f, FetchedResource r, Resource res, Map<String, String> vars, String prefixForContained) throws FileNotFoundException, Exception {
    if (igpkp.wantGen(r, "history")) {
      String html = "";
      fragment(res.fhirType()+"-"+prefixForContained+res.getId()+"-history", html, f.getOutputNames(), r, vars, null);
    }
    if (igpkp.wantGen(r, "html")) {
      XhtmlNode xhtml = getXhtml(f, r, res);
      if (xhtml == null && HistoryGenerator.allEntriesAreHistoryProvenance(r.getElement())) {
        RenderingContext ctxt = rc.copy().setParser(getTypeLoader(f, r));
        List<ProvenanceDetails> entries = loadProvenanceForBundle(igpkp.getLinkFor(r, true), r.getElement(), f);
        xhtml = new HistoryGenerator(ctxt).generateForBundle(entries); 
        fragment(res.fhirType()+"-"+prefixForContained+res.getId()+"-html", new XhtmlComposer(XhtmlComposer.XML).compose(xhtml), f.getOutputNames(), r, vars, prefixForContained);
      } else {
        String html = xhtml == null ? "" : new XhtmlComposer(XhtmlComposer.XML).compose(xhtml);
        fragment(res.fhirType()+"-"+prefixForContained+res.getId()+"-html", html, f.getOutputNames(), r, vars, prefixForContained);
      }
    }
  }

  private String genIpStatements(List<FetchedFile> files) throws FHIRException {
    Set<String> systems = new HashSet<String>();
    for (FetchedFile f : files) {
      for (FetchedResource r : f.getResources()) {
        listAllCodeSystems(systems, r.getElement());        
      }
    }
    
    StringBuilder b = new StringBuilder();
    boolean first = true;
    for (String system : systems) {
      CopyRightUsageStatement stmt = getCopyRightStatement(system);
      if (stmt != null) {
        if (first) {
          b.append("<ul>\r\n");
        }
        first = false;
        b.append("<li>This publication includes IP from "+stmt.getSystemName()+". "+stmt.getHtml()+"</li>\r\n");
      }
    }
    if (!first) {
      b.append("</ul>\r\n");
    }
    return b.toString();     
  }
  
  private String genIpStatements(FetchedResource r, boolean example) throws FHIRException {
    Set<String> systems = listAllCodeSystems(r.getElement());
    StringBuilder b = new StringBuilder();
    boolean first = true;
    for (String system : systems) {
      CopyRightUsageStatement stmt = getCopyRightStatement(system);
      if (stmt != null) {
        if (first) {
          b.append("<ul>\r\n");
        }
        first = false;
        b.append("<li>This "+(example ? "example" : describeResource(r.getElement()))+" includes IP from "+stmt.getSystemName()+". "+stmt.getHtml()+"</li>\r\n");
      }
    }
    if (!first) {
      b.append("</ul>\r\n");
    }
    return b.toString(); 
  }

  private String describeResource(Element element) {
    String t = element.fhirType();
    if ("StructureDefinition".equals(t)) {
      String s = element.getChildValue("type");
      if ("Extension".equals(s)) {
        return "extension";
      } else {
        s = element.getChildValue("kind");
        if ("logical".equals(s)) {
          return "logical model";
        } else {
          return "profile";
        }       
      }
    } else {
      return t;
    }
  }

  // todo - hook this up to the
  private CopyRightUsageStatement getCopyRightStatement(String system) {
    if ("http://snomed.info/sct".equals(system)) {
      return new CopyRightUsageStatement("SNOMED Clinical Terms® (SNOMED CT®)", "This content from  which is copyright of SNOMED International. Implementers of these specifications must have the appropriate SNOMED CT Affiliate license - "+
       "for more information contact <a href=\"http://www.snomed.org/snomed-ct/get-snomed-ct\">http://www.snomed.org/snomed-ct/get-snomed-ct</a> or <a href=\"mailto:info@snomed.org\">info@snomed.org</a>.");
    }
    if ("http://loinc.org".equals(system)) {
      return new CopyRightUsageStatement("LOINC", "This material contains content from <a href=\"http://loinc.org\">LOINC</a. LOINC is copyright © 1995-2020, Regenstrief Institute, Inc. and the Logical Observation Identifiers Names and Codes (LOINC) "+
         "Committee and is available at no cost under the <a href=\"http://loinc.org/license\">license</a>. LOINC® is a registered United States trademark of Regenstrief Institute, Inc.");
    }
    if (system != null) {
      CodeSystem cs = context.fetchCodeSystem(system);
      if (cs != null && cs.hasCopyright()) {
        return new CopyRightUsageStatement(cs.present(), stripPara(markdownEngine.process(cs.getCopyright(), "Copyright")));        
      }
    }
    return null;
  }

  private String stripPara(String p) {
    if (Utilities.noString(p)) {
      return "";
    }
    if (p.startsWith("<p>")) {
      p = p.substring(3);
    }
    if (p.endsWith("</p>")) {
      p = p.substring(0, p.length()-4);
    }
    return p;
  }

  private Set<String> listAllCodeSystems(Element element) {
    Set<String> set = new HashSet<String>();
    listAllCodeSystems(set, element);
    return set;
  }

  private void listAllCodeSystems(Set<String> set, Element element) {
    if ("Coding".equals(element.fhirType())) {
      set.add(element.getChildValue("system"));
    }
    for (Element child : element.getChildren()) {
      listAllCodeSystems(set, child);
    }    
  }

  private String genFmmBanner(FetchedResource r) throws FHIRException {
    String fmm = null;
    StandardsStatus ss = null;
    if (r.getResource() instanceof DomainResource) {
      fmm = ToolingExtensions.readStringExtension((DomainResource) r.getResource(), ToolingExtensions.EXT_FMM_LEVEL);
      ss = ToolingExtensions.getStandardsStatus((DomainResource) r.getResource());
    }
    if (ss == null)
      ss = StandardsStatus.TRIAL_USE;
    if (fmm != null)
      return "<table class=\"cols\"><tbody><tr>"+
        "<td><a href=\"http://hl7.org/fhir/versions.html#maturity\">Maturity Level</a>: "+fmm+"</td>"+
        "<td>&nbsp;<a href=\""+checkAppendSlash(specPath)+"versions.html#std-process\" title=\"Standard Status\">"+ss.toDisplay()+"</a></td>"+
        "</tr></tbody></table>";
    else
      return "";
  }

  private String genStatus(FetchedFile f, FetchedResource r, Resource resource) throws FHIRException {
    org.hl7.fhir.igtools.renderers.StatusRenderer.ResourceStatusInformation info = StatusRenderer.analyse((DomainResource) resource);
    return StatusRenderer.render(igpkp.specPath(), info);
  }
  
  private String genValidation(FetchedFile f, FetchedResource r) throws FHIRException {
    StringBuilder b = new StringBuilder();
    String version = mode == IGBuildMode.AUTOBUILD ? "current" : mode == IGBuildMode.PUBLICATION ? publishedIg.getVersion() : "dev";
    if (isExample(f, r)) {
      // we're going to use javascript to determine the relative path of this for the user.
      b.append("<p><b>Validation Links:</b></p><ul><li><a href=\"https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator\">Validate using FHIR Validator</a> (Java): <code id=\"vl-"+r.fhirType()+"-"+r.getId()+"\">$cmd$</code></li></ul>\r\n");  
      b.append("<script type=\"text/javascript\">\r\n");
      b.append("  var x = window.location.href;\r\n");
      b.append("  document.getElementById(\"vl-"+r.fhirType()+"-"+r.getId()+"\").innerHTML = \"java -jar [path]/org.hl7.fhir.validator.jar -ig "+publishedIg.getPackageId()+"#"+version+" \"+x.substr(0, x.lastIndexOf(\".\")).replace(\"file:///\", \"\") + \".json\";\r\n");
      b.append("</script>\r\n");
    } else if (r.getResource() instanceof StructureDefinition) {
      b.append("<p>Validate this resource:</b></p><ul><li><a href=\"https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator\">Validate using FHIR Validator</a> (Java): <code>"+
          "java -jar [path]/org.hl7.fhir.validator.jar -ig "+publishedIg.getPackageId()+"#"+version+" -profile "+((StructureDefinition) r.getResource()).getUrl()+" [resource-to-validate]"+
          "</code></li></ul>\r\n");        
    } else {
    } 
    return b.toString();
  }

  private void genWrapper(FetchedFile ff, FetchedResource r, String template, String outputName, Set<String> outputTracker, Map<String, String> vars, String format, String extension) throws FileNotFoundException, IOException, FHIRException {
    if (template != null && !template.isEmpty()) {
      boolean existsAsPage = false;
      if (ff != null) {
        String fn = igpkp.getLinkFor(r, true);
        for (String pagesDir: pagesDirs) {
          if (altMap.containsKey("page/"+Utilities.path(pagesDir, fn))) {
            existsAsPage = true;
            break;
          }
        }
        if (!existsAsPage && !prePagesDirs.isEmpty()) {
          for (String prePagesDir : prePagesDirs) {
            if (altMap.containsKey("page/"+Utilities.path(prePagesDir, fn))) {
              existsAsPage = true;
              break;
            }
          }
        }
      }
      if (!existsAsPage) {
         template = fetcher.openAsString(Utilities.path(fetcher.pathForFile(configFile), template));
        template = igpkp.doReplacements(template, r, vars, format);

        outputName = determineOutputName(outputName, r, vars, format, extension);
        if (!outputName.contains("#")) {
          String path = Utilities.path(tempDir, outputName);
          checkMakeFile(TextFile.stringToBytes(template, false), path, outputTracker);
        }
      }
    }
  }

  private void genWrapperContained(FetchedFile ff, FetchedResource r, Resource res, String template, String outputName, Set<String> outputTracker, Map<String, String> vars, String format, String extension, String prefixForContained) throws FileNotFoundException, IOException, FHIRException {
    if (template != null && !template.isEmpty()) {
      boolean existsAsPage = false;
      if (ff != null) {
        String fn = igpkp.getLinkFor(r, true, res);
        for (String pagesDir: pagesDirs) {
          if (altMap.containsKey("page/"+Utilities.path(pagesDir, fn))) {
            existsAsPage = true;
            break;
          }
        }
        if (!existsAsPage && !prePagesDirs.isEmpty()) {
          for (String prePagesDir : prePagesDirs) {
            if (altMap.containsKey("page/"+Utilities.path(prePagesDir, fn))) {
              existsAsPage = true;
              break;
            }
          }
        }
      }
      if (!existsAsPage) {
        template = fetcher.openAsString(Utilities.path(fetcher.pathForFile(configFile), template));
        template = igpkp.doReplacements(template, r, res, vars, format, prefixForContained);

        outputName = determineOutputName(outputName, r, res, vars, format, extension, prefixForContained);
        if (!outputName.contains("#")) {
          String path = Utilities.path(tempDir, outputName);
          checkMakeFile(TextFile.stringToBytes(template, false), path, outputTracker);
        }
      }
    }
  }

  private String determineOutputName(String outputName, FetchedResource r, Map<String, String> vars, String format, String extension) throws FHIRException {
    if (outputName == null)
      outputName = "{{[type]}}-{{[id]}}"+(extension.equals("")? "":"-"+extension)+(format==null? "": ".{{[fmt]}}")+".html";
    if (outputName.contains("{{["))
      outputName = igpkp.doReplacements(outputName, r, vars, format);
    return outputName;
  }

  private String determineOutputName(String outputName, FetchedResource r, Resource res, Map<String, String> vars, String format, String extension, String prefixForContained) throws FHIRException {
    if (outputName == null)
      outputName = "{{[type]}}-{{[id]}}"+(extension.equals("")? "":"-"+extension)+(format==null? "": ".{{[fmt]}}")+".html";
    if (outputName.contains("{{["))
      outputName = igpkp.doReplacements(outputName, r, res, vars, format, prefixForContained);
    return outputName;
  }

  /**
   * Generate:
   *   summary
   *   content as html
   *   xref
   * @param resource
   * @throws org.hl7.fhir.exceptions.FHIRException
   * @throws Exception
   */
  private void generateOutputsCodeSystem(FetchedFile f, FetchedResource fr, CodeSystem cs, Map<String, String> vars, String prefixForContainer) throws Exception {
    CodeSystemRenderer csr = new CodeSystemRenderer(context, specPath, cs, igpkp, specMaps, markdownEngine, packge, rc);
    if (igpkp.wantGen(fr, "summary")) {
      fragment("CodeSystem-"+prefixForContainer+cs.getId()+"-summary", csr.summaryTable(fr, igpkp.wantGen(fr, "xml"), igpkp.wantGen(fr, "json"), igpkp.wantGen(fr, "ttl"), igpkp.summaryRows()), f.getOutputNames(), fr, vars, null);
    }
    if (igpkp.wantGen(fr, "summary-table")) {
      fragment("CodeSystem-"+prefixForContainer+cs.getId()+"-summary-table", csr.summaryTable(fr, igpkp.wantGen(fr, "xml"), igpkp.wantGen(fr, "json"), igpkp.wantGen(fr, "ttl"), igpkp.summaryRows()), f.getOutputNames(), fr, vars, null);
    }
    if (igpkp.wantGen(fr, "content")) {
      fragment("CodeSystem-"+prefixForContainer+cs.getId()+"-content", csr.content(otherFilesRun), f.getOutputNames(), fr, vars, null);
    }
    if (igpkp.wantGen(fr, "xref")) {
      fragment("CodeSystem-"+prefixForContainer+cs.getId()+"-xref", csr.xref(), f.getOutputNames(), fr, vars, null);
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
  private void generateOutputsValueSet(FetchedFile f, FetchedResource r, ValueSet vs, Map<String, String> vars, String prefixForContainer) throws Exception {
    ValueSetRenderer vsr = new ValueSetRenderer(context, specPath, vs, igpkp, specMaps, markdownEngine, packge, rc);
    if (igpkp.wantGen(r, "summary")) {
      fragment("ValueSet-"+prefixForContainer+vs.getId()+"-summary", vsr.summaryTable(r, igpkp.wantGen(r, "xml"), igpkp.wantGen(r, "json"), igpkp.wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null);
    }
    if (igpkp.wantGen(r, "summary-table")) {
      fragment("ValueSet-"+prefixForContainer+vs.getId()+"-summary-table", vsr.summaryTable(r, igpkp.wantGen(r, "xml"), igpkp.wantGen(r, "json"), igpkp.wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null);
    }
    if (igpkp.wantGen(r, "cld")) {
      try {
        fragment("ValueSet-"+prefixForContainer+vs.getId()+"-cld", vsr.cld(otherFilesRun), f.getOutputNames(), r, vars, null);
      } catch (Exception e) {
        fragmentError(vs.getId()+"-cld", e.getMessage(), null, f.getOutputNames());
      }
    }
    if (igpkp.wantGen(r, "xref")) {
      fragment("ValueSet-"+prefixForContainer+vs.getId()+"-xref", vsr.xref(), f.getOutputNames(), r, vars, null);
    }
    if (igpkp.wantGen(r, "expansion")) {
      if (vs.getStatus() == PublicationStatus.RETIRED) {
        String html = "<p style=\"color: maroon\">Expansions are not generated for retired value sets</p>";
        fragment("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", html, f.getOutputNames(), r, vars, null);
      } else {
        ValueSetExpansionOutcome exp = context.expandVS(vs, true, true);
        if (exp.getValueset() != null) {
          expansions.add(exp.getValueset());

          RenderingContext lrc = rc.copy();
          lrc.setTooCostlyNoteNotEmpty("This value set has >1000 codes in it. In order to keep the publication size manageable, only a selection (1000 codes) of the whole set of codes is shown");
          lrc.setTooCostlyNoteEmpty("This value set cannot be expanded because of the way it is defined - it has an infinite number of members");
          lrc.setTooCostlyNoteNotEmptyDependent("One of this value set's dependencies has >1000 codes in it. In order to keep the publication size manageable, only a selection of the whole set of codes is shown");
          lrc.setTooCostlyNoteEmptyDependent("This value set cannot be expanded because of the way it is defined - one of it's dependents has an infinite number of members");
          exp.getValueset().setCompose(null);
          exp.getValueset().setText(null);  
          RendererFactory.factory(exp.getValueset(), lrc).render(exp.getValueset());
          String html = new XhtmlComposer(XhtmlComposer.XML).compose(exp.getValueset().getText().getDiv());
          fragment("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", html, f.getOutputNames(), r, vars, null);
        } else {
          if (exp.getError() != null) { 
            if (exp.getError().contains("Unable to provide support")) {
              fragmentErrorHtml("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", "No Expansion for this valueset (Unknown Code System<!-- "+Utilities.escapeXml(exp.getAllErrors().toString())+" -->)", "Publication Tooling Error: "+Utilities.escapeXml(exp.getAllErrors().toString()), f.getOutputNames());
              f.getErrors().add(new ValidationMessage(Source.TerminologyEngine, IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", exp.getError(), IssueSeverity.WARNING).setTxLink(exp.getTxLink()));
              r.getErrors().add(new ValidationMessage(Source.TerminologyEngine, IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", exp.getError(), IssueSeverity.WARNING).setTxLink(exp.getTxLink()));
            } else {
              fragmentErrorHtml("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", "No Expansion for this valueset (not supported by Publication Tooling<!-- "+Utilities.escapeXml(exp.getAllErrors().toString())+" -->)", "Publication Tooling Error: "+Utilities.escapeXml(exp.getAllErrors().toString()), f.getOutputNames());
              f.getErrors().add(new ValidationMessage(Source.TerminologyEngine, IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", exp.getError(), IssueSeverity.ERROR).setTxLink(exp.getTxLink()));
              r.getErrors().add(new ValidationMessage(Source.TerminologyEngine, IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", exp.getError(), IssueSeverity.ERROR).setTxLink(exp.getTxLink()));
            }
          } else {
            fragmentError("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", "No Expansion for this valueset (not supported by Publication Tooling)", "Unknown Error", f.getOutputNames());
            f.getErrors().add(new ValidationMessage(Source.TerminologyEngine, IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", "Unknown Error expanding ValueSet", IssueSeverity.ERROR).setTxLink(exp.getTxLink()));
            r.getErrors().add(new ValidationMessage(Source.TerminologyEngine, IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", "Unknown Error expanding ValueSet", IssueSeverity.ERROR).setTxLink(exp.getTxLink()));
          }
        }
      }
    }
  }

  private void fragmentError(String name, String error, String overlay, Set<String> outputTracker) throws IOException, FHIRException {
    if (Utilities.noString(overlay))
      fragment(name, "<p><span style=\"color: maroon; font-weight: bold\">"+Utilities.escapeXml(error)+"</span></p>\r\n", outputTracker);
    else
      fragment(name, "<p><span style=\"color: maroon; font-weight: bold\" title=\""+Utilities.escapeXml(overlay)+"\">"+Utilities.escapeXml(error)+"</span></p>\r\n", outputTracker);
  }

  private void fragmentErrorHtml(String name, String error, String overlay, Set<String> outputTracker) throws IOException, FHIRException {
    if (Utilities.noString(overlay))
      fragment(name, "<p><span style=\"color: maroon; font-weight: bold\">"+error+"</span></p>\r\n", outputTracker);
    else
      fragment(name, "<p><span style=\"color: maroon; font-weight: bold\" title=\""+overlay+"\">"+error+"</span></p>\r\n", outputTracker);
  }

  /**
   * Generate:
   *   summary
   *   content as html
   *   xref
   * @param resource
   * @throws IOException
   */
  private void generateOutputsConceptMap(FetchedFile f, FetchedResource r, ConceptMap cm, Map<String, String> vars, String prefixForContainer) throws IOException, FHIRException {
    if (igpkp.wantGen(r, "summary")) {
      fragmentError("ConceptMap-"+prefixForContainer+cm.getId()+"-summary", "yet to be done: concept map summary", null, f.getOutputNames());
    }
    if (igpkp.wantGen(r, "summary-table")) {
      fragmentError("ConceptMap-"+prefixForContainer+cm.getId()+"-summary-table", "yet to be done: concept map summary", null, f.getOutputNames());
    }
    if (igpkp.wantGen(r, "content")) {
      fragmentError("ConceptMap-"+prefixForContainer+cm.getId()+"-content", "yet to be done: table presentation of the concept map", null, f.getOutputNames());
    }
    if (igpkp.wantGen(r, "xref")) {
      fragmentError("ConceptMap-"+prefixForContainer+cm.getId()+"-xref", "yet to be done: list of all places where concept map is used", null, f.getOutputNames());
    }
    MappingSheetParser p = new MappingSheetParser();
    if (igpkp.wantGen(r, "sheet") && p.isSheet(cm)) {
      fragment("ConceptMap-"+prefixForContainer+cm.getId()+"-sheet", p.genSheet(cm), f.getOutputNames(), r, vars, null);
    }
  }

  private void generateOutputsCapabilityStatement(FetchedFile f, FetchedResource r, CapabilityStatement cpbs, Map<String, String> vars, String prefixForContainer) throws Exception {
    if (igpkp.wantGen(r, "swagger") || igpkp.wantGen(r, "openapi")) {
      Writer oa = null;
      if (openApiTemplate != null) 
        oa = new Writer(new FileOutputStream(Utilities.path(tempDir, cpbs.getId()+ ".openapi.json")), new FileInputStream(Utilities.path(Utilities.getDirectoryForFile(configFile), openApiTemplate)));
      else
        oa = new Writer(new FileOutputStream(Utilities.path(tempDir, cpbs.getId()+ ".openapi.json")));
      String lic = license();
      String displ = context.validateCode(new ValidationOptions("en-US"), new Coding("http://hl7.org/fhir/spdx-license",  lic, null), null).getDisplay();
      new OpenApiGenerator(context, cpbs, oa).generate(displ, "http://spdx.org/licenses/"+lic+".html");
      oa.commit();
      otherFilesRun.add(Utilities.path(tempDir, cpbs.getId()+ ".openapi.json"));
      npm.addFile(Category.OPENAPI, cpbs.getId()+ ".openapi.json", TextFile.fileToBytes(Utilities.path(tempDir, cpbs.getId()+ ".openapi.json")));
    }
  }

  private void generateOutputsStructureDefinition(FetchedFile f, FetchedResource r, StructureDefinition sd, Map<String, String> vars, boolean regen, String prefixForContainer) throws Exception {
    // todo : generate shex itself
    if (igpkp.wantGen(r, "shex"))
      fragmentError("StructureDefinition-"+prefixForContainer+sd.getId()+"-shex", "yet to be done: shex as html", null, f.getOutputNames());

    // todo : generate json schema itself. JSON Schema generator
//    if (igpkp.wantGen(r, ".schema.json")) {
//      String path = Utilities.path(tempDir, r.getId()+".sch");
//      f.getOutputNames().add(path);
//      new ProfileUtilities(context, errors, igpkp).generateSchematrons(new FileOutputStream(path), sd);
//    }
    if (igpkp.wantGen(r, "json-schema"))
      fragmentError("StructureDefinition-"+prefixForContainer+sd.getId()+"-json-schema", "yet to be done: json schema as html", null, f.getOutputNames());

    StructureDefinitionRenderer sdr = new StructureDefinitionRenderer(context, checkAppendSlash(specPath), sd, Utilities.path(tempDir), igpkp, specMaps, markdownEngine, packge, fileList, rc, allInvariants);
    if (igpkp.wantGen(r, "summary")) {
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-summary", sdr.summary(), f.getOutputNames(), r, vars, null);
    }
    if (igpkp.wantGen(r, "summary-table")) {
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-summary-table", sdr.summaryTable(r, igpkp.wantGen(r, "xml"), igpkp.wantGen(r, "json"), igpkp.wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null);
    }
    if (igpkp.wantGen(r, "header"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-header", sdr.header(), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "diff"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-diff", sdr.diff(igpkp.getDefinitionsName(r), otherFilesRun), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "snapshot"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot", sdr.snapshot(igpkp.getDefinitionsName(r), otherFilesRun), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "snapshot-by-mustsupport"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-mustsupport", sdr.byMustSupport(igpkp.getDefinitionsName(r), otherFilesRun), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "grid"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-grid", sdr.grid(igpkp.getDefinitionsName(r), otherFilesRun), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "pseudo-xml"))
      fragmentError("StructureDefinition-"+prefixForContainer+sd.getId()+"-pseudo-xml", "yet to be done: Xml template", null, f.getOutputNames());
    if (igpkp.wantGen(r, "pseudo-json"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-pseudo-json", sdr.pseudoJson(), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "pseudo-ttl"))
      fragmentError("StructureDefinition-"+prefixForContainer+sd.getId()+"-pseudo-ttl", "yet to be done: Turtle template", null, f.getOutputNames());
    if (igpkp.wantGen(r, "uml"))
      fragmentError("StructureDefinition-"+prefixForContainer+sd.getId()+"-uml", "yet to be done: UML as SVG", null, f.getOutputNames());
    if (igpkp.wantGen(r, "tx"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-tx", sdr.tx(includeHeadings, false), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "tx-must-support"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-tx-must-support", sdr.tx(includeHeadings, true), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "tx-diff"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-tx-diff", sdr.txDiff(includeHeadings, false), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "tx-diff-must-support"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-tx-diff-must-support", sdr.txDiff(includeHeadings, true), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "inv"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-inv", sdr.inv(includeHeadings), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "dict"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-dict", sdr.dict(true), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "dict-active"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-dict-active", sdr.dict(false), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "maps"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-maps", sdr.mappings(false, false), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "maps"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-maps-all", sdr.mappings(true, false), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "maps"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-maps-diff", sdr.mappings(false, true), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "maps"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-maps-diff-all", sdr.mappings(true, true), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "xref"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-sd-xref", sdr.references(), f.getOutputNames(), r, vars, null);
    if (sd.getDerivation() == TypeDerivationRule.CONSTRAINT && igpkp.wantGen(r, "span"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-span", sdr.span(true, igpkp.getCanonical(), otherFilesRun), f.getOutputNames(), r, vars, null);
    if (sd.getDerivation() == TypeDerivationRule.CONSTRAINT && igpkp.wantGen(r, "spanall"))
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-spanall", sdr.span(true, igpkp.getCanonical(), otherFilesRun), f.getOutputNames(), r, vars, null);

    if (igpkp.wantGen(r, "example-list"))
      fragment("StructureDefinition-example-list-"+prefixForContainer+sd.getId(), sdr.exampleList(fileList, true), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "example-table"))
      fragment("StructureDefinition-example-table-"+prefixForContainer+sd.getId(), sdr.exampleTable(fileList, true), f.getOutputNames(), r, vars, null);

    if (igpkp.wantGen(r, "example-list-all"))
      fragment("StructureDefinition-example-list-all-"+prefixForContainer+sd.getId(), sdr.exampleList(fileList, false), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "example-table-all"))
      fragment("StructureDefinition-example-table-all-"+prefixForContainer+sd.getId(), sdr.exampleTable(fileList, false), f.getOutputNames(), r, vars, null);

    String sdPrefix = newIg ? "StructureDefinition-" : "";
    if (igpkp.wantGen(r, "csv")) {
      String path = Utilities.path(tempDir, sdPrefix + r.getId()+".csv");
      f.getOutputNames().add(path);
      new ProfileUtilities(context, errors, igpkp).generateCsvs(new FileOutputStream(path), sd, true);
    }

    if (igpkp.wantGen(r, "java")) {
      ConstraintJavaGenerator jg = new ConstraintJavaGenerator(context, version, tempDir, sourceIg.getUrl());
      try {
        f.getOutputNames().add(jg.generate(sd));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    if (igpkp.wantGen(r, "xlsx")) {
      String path = Utilities.path(tempDir, sdPrefix + r.getId()+".xlsx");
      f.getOutputNames().add(path);
      new ProfileUtilities(context, errors, igpkp).generateXlsx(new FileOutputStream(path), sd, true, true);
    }

    if (!regen && sd.getKind() != StructureDefinitionKind.LOGICAL &&  igpkp.wantGen(r, "sch")) {
      String path = Utilities.path(tempDir, sdPrefix + r.getId()+".sch");
      f.getOutputNames().add(path);
      new ProfileUtilities(context, errors, igpkp).generateSchematrons(new FileOutputStream(path), sd);
      npm.addFile(Category.SCHEMATRON, sdPrefix + r.getId()+".sch", IOUtils.toByteArray(Utilities.path(tempDir, sdPrefix + r.getId()+".sch")));
    }
    if (igpkp.wantGen(r, "sch"))
      fragmentError("StructureDefinition-"+prefixForContainer+sd.getId()+"-sch", "yet to be done: schematron as html", null, f.getOutputNames());
  }

  private String checkAppendSlash(String s) {
    return s.endsWith("/") ? s : s+"/";
  }

  private void generateOutputsStructureMap(FetchedFile f, FetchedResource r, StructureMap map, Map<String,String> vars, String prefixForContainer) throws Exception {
    StructureMapRenderer smr = new StructureMapRenderer(context, checkAppendSlash(specPath), map, Utilities.path(tempDir), igpkp, specMaps, markdownEngine, packge, rc);
    if (igpkp.wantGen(r, "summary"))
      fragment("StructureMap-"+prefixForContainer+map.getId()+"-summary", smr.summaryTable(r, igpkp.wantGen(r, "xml"), igpkp.wantGen(r, "json"), igpkp.wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "summary-table"))
      fragment("StructureMap-"+prefixForContainer+map.getId()+"-summary-table", smr.summaryTable(r, igpkp.wantGen(r, "xml"), igpkp.wantGen(r, "json"), igpkp.wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "content"))
      fragment("StructureMap-"+prefixForContainer+map.getId()+"-content", smr.content(), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "profiles"))
      fragment("StructureMap-"+prefixForContainer+map.getId()+"-profiles", smr.profiles(), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "script"))
      fragment("StructureMap-"+prefixForContainer+map.getId()+"-script", smr.script(), f.getOutputNames(), r, vars, null);
// to generate:
    // map file
    // summary table
    // profile index

  }

  private void generateOutputsCanonical(FetchedFile f, FetchedResource r, CanonicalResource cr, Map<String,String> vars, String prefixForContainer) throws Exception {
    CanonicalRenderer smr = new CanonicalRenderer(context, checkAppendSlash(specPath), cr, Utilities.path(tempDir), igpkp, specMaps, markdownEngine, packge, rc);
    if (igpkp.wantGen(r, "summary"))
      fragment(cr.fhirType()+"-"+prefixForContainer+cr.getId()+"-summary", smr.summaryTable(r, igpkp.wantGen(r, "xml"), igpkp.wantGen(r, "json"), igpkp.wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "summary-table"))
      fragment(cr.fhirType()+"-"+prefixForContainer+cr.getId()+"-summary-table", smr.summaryTable(r, igpkp.wantGen(r, "xml"), igpkp.wantGen(r, "json"), igpkp.wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null);
  }

  private void generateOutputsLibrary(FetchedFile f, FetchedResource r, Library lib, Map<String,String> vars, String prefixForContainer) throws Exception {
    int counter = 0;
    for (Attachment att : lib.getContent()) {
      String extension = att.hasContentType() ? MimeType.getExtension(att.getContentType()) : null;
      if (extension != null && att.hasData()) {
        String filename = "Library-"+r.getId()+(counter == 0 ? "" : "-"+Integer.toString(counter))+"."+extension;
        TextFile.bytesToFile(att.getData(), Utilities.path(tempDir, filename));
        otherFilesRun.add(Utilities.path(tempDir, filename));
      }
      counter++;
    }
  }
  
  private void generateOutputsQuestionnaire(FetchedFile f, FetchedResource r, Questionnaire q, Map<String,String> vars, String prefixForContainer) throws Exception {
    QuestionnaireRenderer qr = new QuestionnaireRenderer(context, checkAppendSlash(specPath), q, Utilities.path(tempDir), igpkp, specMaps, markdownEngine, packge, rc.copy().setDefinitionsTarget(igpkp.getDefinitionsName(r)));
    if (igpkp.wantGen(r, "summary"))
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-summary", qr.summaryTable(r, igpkp.wantGen(r, "xml"), igpkp.wantGen(r, "json"), igpkp.wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "summary-table"))
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-summary-table", qr.summaryTable(r, igpkp.wantGen(r, "xml"), igpkp.wantGen(r, "json"), igpkp.wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "tree"))
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-tree", qr.render(QuestionnaireRendererMode.TREE), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "form"))
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-form", qr.render(QuestionnaireRendererMode.FORM), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "links"))
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-links", qr.render(QuestionnaireRendererMode.LINKS), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "logic"))
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-logic", qr.render(QuestionnaireRendererMode.LOGIC), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "dict"))
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-dict", qr.render(QuestionnaireRendererMode.DEFNS), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "responses"))
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-responses", responsesForQuestionnaire(q), f.getOutputNames(), r, vars, null);
  }

  private String responsesForQuestionnaire(Questionnaire q) {
    StringBuilder b = new StringBuilder();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("QuestionnaireResponse")) {
          String qurl = r.getElement().getChildValue("questionnaire");
          if (!Utilities.noString(qurl)) {
            if (b.length() == 0) {
              b.append("<ul>\r\n");
            }
            b.append(" <li><a href=\""+igpkp.getLinkFor(r, true)+"\">"+getTitle(f,r )+"</a></li>\r\n");
          }
        }
      }
    }
    if (b.length() > 0) {
      b.append("</ul>\r\n");
    }
    return b.toString();
  }

  private void generateOutputsQuestionnaireResponse(FetchedFile f, FetchedResource r, Map<String,String> vars, String prefixForContainer) throws Exception {
    QuestionnaireResponseRenderer qr = new QuestionnaireResponseRenderer(context, checkAppendSlash(specPath), r.getElement(), Utilities.path(tempDir), igpkp, specMaps, markdownEngine, packge, 
        rc.copy().setParser(getTypeLoader(f, r)).setDefinitionsTarget(igpkp.getDefinitionsName(r)));
    if (igpkp.wantGen(r, "tree"))
      fragment("QuestionnaireResponse-"+prefixForContainer+r.getId()+"-tree", qr.render(QuestionnaireRendererMode.TREE), f.getOutputNames(), r, vars, null);
    if (igpkp.wantGen(r, "form"))
      fragment("QuestionnaireResponse-"+prefixForContainer+r.getId()+"-form", qr.render(QuestionnaireRendererMode.FORM), f.getOutputNames(), r, vars, null);
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
    for (ImplementationGuideDefinitionResourceComponent res : publishedIg.getDefinition().getResource()) {
      FetchedResource tr = (FetchedResource) res.getUserData("loaded.resource");
      if (tr == r) {
        return res.getDescription();
      }
    }
    return "(no description)";
  }

  private XhtmlNode getXhtml(FetchedFile f, FetchedResource r) throws Exception {
    if (r.getResource() != null && r.getResource() instanceof DomainResource) {
      DomainResource dr = (DomainResource) r.getResource();
      if (dr.getText().hasDiv())
        return dr.getText().getDiv();
    }
    if (r.getResource() != null && r.getResource() instanceof Bundle) {
      RenderingContext lrc = rc.copy().setParser(getTypeLoader(f, r));
      Bundle b = (Bundle) r.getResource();
      BundleRenderer br = new BundleRenderer(lrc);
      if (br.canRender(b)) {
        return br.render(b);
      }
    }
    if (r.getResource() != null && r.getResource() instanceof Parameters) {
      Parameters p = (Parameters) r.getResource();
      return new ParametersRenderer(rc, new ResourceContext(ResourceContextType.PARAMETERS, p, null)).render(p);
    }
    if (r.fhirType().equals("Bundle")) {
      RenderingContext lrc = rc.copy().setParser(getTypeLoader(f, r));
      return new BundleRenderer(lrc).render(new ElementWrappers.ResourceWrapperMetaElement(lrc, r.getElement()));
    } else if (r.fhirType().equals("Parameters")) {
      RenderingContext lrc = rc.copy().setParser(getTypeLoader(f, r));
      return new ParametersRenderer(lrc, new ResourceContext(ResourceContextType.PARAMETERS, r.getElement(), r.getElement())).render(new ElementWrappers.ResourceWrapperMetaElement(lrc, r.getElement()));
    } else {
      return getHtmlForResource(r.getElement());
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
      return new BundleRenderer(rc).render(b);
    }
    if (resource instanceof Parameters) {
      Parameters p = (Parameters) resource;
      return new ParametersRenderer(rc, new ResourceContext(ResourceContextType.PARAMETERS, p, null)).render(p);
    }
    RenderingContext lrc = rc.copy().setParser(getTypeLoader(f, r));
    return RendererFactory.factory(resource, lrc).build(resource);
  }


  private XhtmlNode getHtmlForResource(Element element) {
    Element text = element.getNamedChild("text");
    if (text == null)
      return null;
    Element div = text.getNamedChild("div");
    if (div == null)
      return null;
    else
      return div.getXhtml();
  }

  private void fragmentIfNN(String name, String content, Set<String> outputTracker) throws IOException, FHIRException {
    if (!Utilities.noString(content))
      fragment(name, content, outputTracker, null, null, null);
  }
  
  private void fragment(String name, String content, Set<String> outputTracker) throws IOException, FHIRException {
    fragment(name, content, outputTracker, null, null, null);
  }
  
  private void fragment(String name, String content, Set<String> outputTracker, FetchedResource r, Map<String, String> vars, String format) throws IOException, FHIRException {
    String fixedContent = (r==null? content : igpkp.doReplacements(content, r, vars, format));
    if (checkMakeFile(TextFile.stringToBytes(wrapLiquid(fixedContent), false), Utilities.path(tempDir, "_includes", name+".xhtml"), outputTracker)) {
      if (mode != IGBuildMode.AUTOBUILD && makeQA)
        TextFile.stringToFile(pageWrap(fixedContent, name), Utilities.path(qaDir, name+".html"), true);
    }
  }

  /**
   * None of the fragments presently generated inclde {{ }} liquid tags. So any 
   * liquid tags found in the fragments are actually what should be displayed post-jekyll.
   * So we're going to globally escape them here. If any fragments want to include actual
   * Jekyll tags, we'll have to do something much harder.
   * 
   * see https://stackoverflow.com/questions/24102498/escaping-double-curly-braces-inside-a-markdown-code-block-in-jekyll
   * 
   * @param fixedContent
   * @return
   */
  private String wrapLiquid(String content) {
    return "{% raw %}"+content+"{% endraw %}";
  }



  private String pageWrap(String content, String title) {
    return "<html>\r\n"+
        "<head>\r\n"+
        "  <title>"+title+"</title>\r\n"+
        "  <link rel=\"stylesheet\" href=\"fhir.css\"/>\r\n"+
        "</head>\r\n"+
        "<body>\r\n"+
        content+
        "</body>\r\n"+
        "</html>\r\n";
  }

  public void setConfigFile(String configFile) {
    this.configFile = configFile;
  }

  public String getSourceDir() {
    return sourceDir;
  }

  public void setSourceDir(String sourceDir) {
    this.sourceDir = sourceDir;
  }

  public String getDestDir() {
    return destDir;
  }

  public void setDestDir(String destDir) {
    this.destDir = destDir;
  }

  public String getConfigFile() {
    return configFile;
  }

  private static void runGUI() throws InterruptedException, InvocationTargetException {
    EventQueue.invokeLater(new Runnable() {
      public void run() {
        try {
          UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
          GraphicalPublisher window = new GraphicalPublisher();
          window.frame.setVisible(true);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
  }

  public void setTxServer(String s) {
    if (!Utilities.noString(s))
      txServer = s;
  }

  String getTxServer() {
    return txServer;
  }

  private void setIgPack(String s) {
    if (!Utilities.noString(s))
      igPack = s;
  }

  private static String getNamedParam(String[] args, String param) {
    boolean found = false;
    for (String a : args) {
      if (found)
        return a;
      if (a.equals(param)) {
        found = true;
      }
    }
    return null;
  }

  private static boolean hasNamedParam(String[] args, String param) {
    for (String a : args) {
      if (a.equals(param)) {
        return true;
      }
    }
    return false;
  }

  public void setLogger(ILoggingService logger) {
    this.logger = logger;
    fetcher.setLogger(logger);
  }

  public String getQAFile() throws IOException {
    return Utilities.path(outputDir, "qa.html");
  }

  @Override
  public void logMessage(String msg) {
    if (firstExecution)
      System.out.println(Utilities.padRight(msg, ' ', 80)+" ("+tt.clock()+")");
    else
      System.out.println(msg);    
  }

  @Override
  public void logDebugMessage(LogCategory category, String msg) {
    if (logOptions.contains(category.toString().toLowerCase())) {
      logMessage(msg);
  }
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
      b.append(TextFile.fileToString(qafile));

    b.append("\r\n");
    b.append("\r\n");

    if (ig != null) {
      b.append("= IG =\r\n");
      b.append(TextFile.fileToString(determineActualIG(ig, null)));
    }

    b.append("\r\n");
    b.append("\r\n");
    return b.toString();
  }
  
  public static void main(String[] args) throws Exception {
    int exitCode = 0;
    if (hasNamedParam(args, "-gui") || args.length == 0) {
      runGUI();
      // Returning here ends the main thread but leaves the GUI running
      return; 
    } else if (hasNamedParam(args, "-package")) {
      System.out.println("FHIR IG Publisher "+IGVersionUtil.getVersionString());
      System.out.println("Detected Java version: " + System.getProperty("java.version")+" from "+System.getProperty("java.home")+" on "+System.getProperty("os.arch")+" ("+System.getProperty("sun.arch.data.model")+"bit). "+toMB(Runtime.getRuntime().maxMemory())+"MB available");
      System.out.println("dir = "+System.getProperty("user.dir")+", path = "+System.getenv("PATH"));
      String s = "Parameters:";
      for (int i = 0; i < args.length; i++) {
          s = s + " "+args[i];
      }      
      System.out.println(s);
      FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager(!hasNamedParam(args, "system"), ToolsVersion.TOOLS_VERSION);
      System.out.println("Cache = "+pcm.getFolder());
      for (String p : getNamedParam(args, "-package").split("\\;")) {
        NpmPackage npm = pcm.loadPackage(p);
        System.out.println("OK: "+npm.name()+"#"+npm.version()+" for FHIR version(s) "+npm.fhirVersionList()+" with canonical "+npm.canonical());
      }
    } else if (hasNamedParam(args, "-help") || hasNamedParam(args, "-?") || hasNamedParam(args, "/?") || hasNamedParam(args, "?")) {
      System.out.println("");
      System.out.println("To use this publisher to publish a FHIR Implementation Guide, run ");
      System.out.println("with the commands");
      System.out.println("");
      System.out.println("-spec [igpack.zip] -ig [source] -tx [url] -packages [path] -watch");
      System.out.println("");
      System.out.println("-spec: a path or a url where the igpack for the version of the core FHIR");
      System.out.println("  specification used by the ig being published is located.  If not specified");
      System.out.println("  the tool will retrieve the file from the web based on the specified FHIR version");
      System.out.println("-ig: a path or a url where the implementation guide control file is found");
      System.out.println("  see Wiki for Documentation");
      System.out.println("-tx: (optional) Address to use for terminology server ");
      System.out.println("  (default is http://tx.fhir.org)");
      System.out.println("  use 'n/a' to run without a terminology server");
      System.out.println("-watch (optional): if this is present, the publisher will not terminate;");
      System.out.println("  instead, it will stay running, an watch for changes to the IG or its ");
      System.out.println("  contents and re-run when it sees changes ");
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
      System.out.println("For additional information, see http://wiki.hl7.org/index.php?title=Proposed_new_FHIR_IG_build_Process");
    } else if (hasNamedParam(args, "-convert")) {
      // convert a igpack.zip to a package.tgz
      IGPack2NpmConvertor conv = new IGPack2NpmConvertor();
      conv.setSource(getNamedParam(args, "-source"));
      conv.setDest(getNamedParam(args, "-dest"));
      conv.setPackageId(getNamedParam(args, "-npm-name"));
      conv.setVersionIg(getNamedParam(args, "-version"));
      conv.setLicense(getNamedParam(args, "-license"));
      conv.setWebsite(getNamedParam(args, "-website"));
      conv.execute();
    } else if (hasNamedParam(args, "-delete-current")) {
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
      String history = getNamedParam(args, "-history");
      if (Utilities.noString(history)) {
        throw new Error("-delete-current must have the format -delete-current {root}/{realm}/{code} -history {history} (no history found)");
      }
      File fh = new File(history);
      if (!fh.exists()) {
        throw new Error("-delete-current must have the format -delete-current {root}/{realm}/{code} -history {history} ({history} not found ("+history+"))");
      }
      if (!fh.isDirectory()) {
        throw new Error("-delete-current must have the format -delete-current {root}/{realm}/{code} -history {history} ({history} not a directory ("+history+"))");
      }
      IGReleaseVersionDeleter deleter = new IGReleaseVersionDeleter();
      deleter.clear(f.getAbsolutePath(), fh.getAbsolutePath());
    } else if (hasNamedParam(args, "-go-publish")) {
      new PublicationProcess().publish(getNamedParam(args, "-source"), getNamedParam(args, "-destination"), hasNamedParam(args, "-milestone"), getNamedParam(args, "-registry"), getNamedParam(args, "-history"), getNamedParam(args, "-temp"));
    } else if (hasNamedParam(args, "-publish-update")) {
      if (!args[0].equals("-publish-update")) {
        throw new Error("-publish-update must have the format -publish-update -folder {folder} -registry {registry}/fhir-ig-list.json (first argument is not -publish-update)");
      }
      if (args.length < 3) {
        throw new Error("-publish-update must have the format -publish-update -folder {folder} -registry {registry}/fhir-ig-list.json (not enough args)");
      }
      File f = new File(getNamedParam(args, "-folder"));
      if (!f.exists() || !f.isDirectory()) {
        throw new Error("-publish-update must have the format -publish-update -folder {folder} -registry {registry}/fhir-ig-list.json ({folder} not found)");
      }
      
      String registry = getNamedParam(args, "-registry");
      if (Utilities.noString(registry)) {
        throw new Error("-publish-update must have the format -publish-update -url {url} -root {root} -registry {registry}/fhir-ig-list.json (-registry parameter not found)");
      }
      String filter = getNamedParam(args, "-filter");

      if (!"n/a".equals(registry)) {
        File fr = new File(registry);
        if (!fr.exists() || fr.isDirectory()) {
          throw new Error("-publish-update must have the format -publish-update -url {url} -root {root} -registry {registry}/fhir-ig-list.json ({registry} not found)");
        }
      }
      boolean doCore = "true".equals(getNamedParam(args, "-core"));
      
      IGRegistryMaintainer reg = "n/a".equals(registry) ? null : new IGRegistryMaintainer(registry);
      IGWebSiteMaintainer.execute(f.getAbsolutePath(), reg, doCore, filter);
      reg.finish();      
    } else if (hasNamedParam(args, "-multi")) {
      int i = 1;
      for (String ig : TextFile.fileToString(getNamedParam(args, "-multi")).split("\\r?\\n")) {
        if (!ig.startsWith(";")) {
          System.out.println("=======================================================================================");
          System.out.println("Publish IG "+ig);
          Publisher self = new Publisher();
          self.setConfigFile(determineActualIG(ig, null));
          setTxServerValue(args, self);
          if (hasNamedParam(args, "-resetTx")) {
            self.setCacheOption(CacheOption.CLEAR_ALL);
          } else if (hasNamedParam(args, "-resetTxErrors")) {
            self.setCacheOption(CacheOption.CLEAR_ERRORS);
          } else {
            self.setCacheOption(CacheOption.LEAVE);
          }
          try {
            self.execute();
          } catch (Exception e) {
            exitCode = 1;
            System.out.println("Publishing Implementation Guide Failed: "+e.getMessage());
            System.out.println("");
            System.out.println("Stack Dump (for debugging):");
            e.printStackTrace();
            break;
          }
          TextFile.stringToFile(buildReport(ig, null, self.filelog.toString(), Utilities.path(self.qaDir, "validation.txt"), self.txServer), Utilities.path(System.getProperty("java.io.tmpdir"), "fhir-ig-publisher-"+Integer.toString(i)+".log"), false);
          System.out.println("=======================================================================================");
          System.out.println("");
          System.out.println("");
          i++;
        }
      }
    } else {
      Publisher self = new Publisher();
      String consoleLog = getNamedParam(args, "log");
      if (consoleLog == null) {     
        consoleLog =  Utilities.path("[tmp]", "fhir-ig-publisher-tmp.log");
      }
      PublisherConsoleLogger logger = new PublisherConsoleLogger();
      if (!hasNamedParam(args, "-auto-ig-build") && !hasNamedParam(args, "-publish-process")) {
        logger.start(consoleLog);
      }
        
      self.logMessage("FHIR IG Publisher "+IGVersionUtil.getVersionString());
      self.logMessage("Detected Java version: " + System.getProperty("java.version")+" from "+System.getProperty("java.home")+" on "+System.getProperty("os.name")+"/"+System.getProperty("os.arch")+" ("+System.getProperty("sun.arch.data.model")+"bit). "+toMB(Runtime.getRuntime().maxMemory())+"MB available");
      if (!"64".equals(System.getProperty("sun.arch.data.model"))) {
        self.logMessage("Attention: you should upgrade your Java to a 64bit version in order to be able to run this program without running out of memory");        
      }
      self.logMessage("dir = "+System.getProperty("user.dir")+", path = "+System.getenv("PATH"));
      String s = "Parameters:";
      for (int i = 0; i < args.length; i++) {
          s = s + " "+args[i];
      }      
      self.logMessage(s);
      self.logMessage("Start Clock @ "+nowAsString(self.execTime)+" ("+nowAsDate(self.execTime)+")");
      self.logMessage("");
      if (hasNamedParam(args, "-auto-ig-build")) {
        self.setMode(IGBuildMode.AUTOBUILD);
        self.targetOutput = getNamedParam(args, "-target");
      }
      if (hasNamedParam(args, "-api-key-file")) {
        self.apiKeyFile = new IniFile(new File(getNamedParam(args, "-api-key-file")).getAbsolutePath());
      }

      setTxServerValue(args, self);
      if (hasNamedParam(args, "-source")) {
        // run with standard template. this is publishing lite
        self.setSourceDir(getNamedParam(args, "-source"));
        self.setDestDir(getNamedParam(args, "-destination"));
        self.specifiedVersion = getNamedParam(args, "-version");
      } else if(!hasNamedParam(args, "-ig") && args.length == 1 && new File(args[0]).exists()) {
        self.setConfigFile(determineActualIG(args[0], IGBuildMode.MANUAL));
      } else if (hasNamedParam(args, "-prompt")) {
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
              System.out.println("Can't find "+last);
            }
          } 
        } else {
          while (!ok) {
            System.out.print("Enter path of IG ["+last+"]: ");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String nlast = reader.readLine();
            if (Utilities.noString(nlast))
              nlast = last;
            if (new File(nlast).exists()) {
              ok = true;
              last = nlast;
            } else {
              System.out.println("Can't find "+nlast);
            }
          }
        }
        ini.setStringProperty("execute", "path", last, null);
        ini.save();
        if (new File(last).isDirectory()) {
          self.setConfigFile(determineActualIG(Utilities.path(last, "ig.json"), IGBuildMode.MANUAL));
        } else {
          self.setConfigFile(determineActualIG(last, IGBuildMode.MANUAL));
        }
      } else if (hasNamedParam(args, "-simplifier")) {
        if (!hasNamedParam(args, "-destination")) {
          throw new Exception("A destination folder (-destination) must be provided for the output from processing the simplifier IG");
        }
        if (!hasNamedParam(args, "-canonical")) {
          throw new Exception("A canonical URL (-canonical) must be provided in order to process a simplifier IG");
        }
        if (!hasNamedParam(args, "-npm-name")) {
          throw new Exception("A package name (-npm-name) must be provided in order to process a simplifier IG");
        }
        if (!hasNamedParam(args, "-license")) {
          throw new Exception("A license code (-license) must be provided in order to process a simplifier IG");
        }
        List<String> packages = new ArrayList<String>();
        for (int i = 0; i < args.length; i++) {
          if (args[i].equals("-dependsOn")) { 
            packages.add(args[i+1]);
          }
        }
        // create an appropriate ig.json in the specified folder
        self.setConfigFile(generateIGFromSimplifier(getNamedParam(args, "-simplifier"), getNamedParam(args, "-destination"), getNamedParam(args, "-canonical"), getNamedParam(args, "-npm-name"), getNamedParam(args, "-license"), packages));
        self.folderToDelete = Utilities.getDirectoryForFile(self.getConfigFile());
      } else {
        self.setConfigFile(determineActualIG(getNamedParam(args, "-ig"), self.mode));
        if (Utilities.noString(self.getConfigFile())) {
          throw new Exception("No Implementation Guide Specified (-ig parameter)");
        }
        if (!(new File(self.getConfigFile()).isAbsolute())) {
          self.setConfigFile(Utilities.path(System.getProperty("user.dir"), self.getConfigFile()));
        }
      }
      self.setJekyllCommand(getNamedParam(args, "-jekyll"));
      self.setIgPack(getNamedParam(args, "-spec"));
      String proxy = getNamedParam(args, "-proxy");
      if (!Utilities.noString(proxy)) {
        String[] p = proxy.split("\\:");
        System.setProperty("http.proxyHost", p[0]);
        System.setProperty("http.proxyPort", p[1]);
        System.setProperty("https.proxyHost", p[0]);
        System.setProperty("https.proxyPort", p[1]);
      }
      self.setTxServer(getNamedParam(args, "-tx"));
      self.setPackagesFolder(getNamedParam(args, "-packages"));
      self.watch = hasNamedParam(args, "-watch");
      self.debug = hasNamedParam(args, "-debug");
      self.cacheVersion = hasNamedParam(args, "-cacheVersion");
      if (hasNamedParam(args, "-publish")) {
        self.setMode(IGBuildMode.PUBLICATION);
        self.targetOutput = getNamedParam(args, "-publish");   
        self.publishing  = true;
        self.targetOutputNested = getNamedParam(args, "-nested");        
      }
      if (hasNamedParam(args, "-resetTx")) {
        self.setCacheOption(CacheOption.CLEAR_ALL);
      } else if (hasNamedParam(args, "-resetTxErrors")) {
        self.setCacheOption(CacheOption.CLEAR_ERRORS);
      } else {
        self.setCacheOption(CacheOption.LEAVE);
      }
      if (hasNamedParam(args, "-no-sushi")) {
        self.noFSH = true;
      }
      try {
        self.execute();
        if (hasNamedParam(args, "-no-errors")) {
          exitCode = self.countErrs(self.errors) > 0 ? 1 : 0;
        }
      } catch (Exception e) {
        exitCode = 1;
        self.log("Publishing Content Failed: "+e.getMessage());
        self.log("");
        if (e.getMessage() != null &&  e.getMessage().contains("xsl:message")) {
          self.log("This error was created by the template");   
        } else {
          self.log("Use -? to get command line help");
          self.log("");
          self.log("Stack Dump (for debugging):");
          e.printStackTrace();
          for (StackTraceElement st : e.getStackTrace()) {
            if (st != null && self.filelog != null) {
              self.filelog.append(st.toString());
            }
          }
        }
        exitCode = 1;
      } finally {
        if (self.mode == IGBuildMode.MANUAL) {
          TextFile.stringToFile(buildReport(getNamedParam(args, "-ig"), getNamedParam(args, "-source"), self.filelog.toString(), Utilities.path(self.qaDir, "validation.txt"), self.txServer), Utilities.path(System.getProperty("java.io.tmpdir"), "fhir-ig-publisher.log"), false);
        }
      }
      logger.stop();
    }
    if (!hasNamedParam(args, "-no-exit")) {
      System.exit(exitCode);
    }
  }

  public static void setTxServerValue(String[] args, Publisher self) {
    if (hasNamedParam(args, "-tx")) {
      self.setTxServer(getNamedParam(args, "-tx"));
    } else if (hasNamedParam(args, "-devtx")) {
      self.setTxServer(txServerDev);
    } else {
      self.setTxServer(txServerProd);
    }
  }

  private static String generateIGFromSimplifier(String folder, String output, String canonical, String npmName, String license, List<String> packages) throws Exception {
    String ig = Utilities.path(System.getProperty("java.io.tmpdir"), "simplifier", UUID.randomUUID().toString().toLowerCase());
    Utilities.createDirectory(ig);
    String config = Utilities.path(ig, "ig.json");
    String pages =  Utilities.path(ig, "pages");
    String resources =  Utilities.path(ig, "resources");
    Utilities.createDirectory(pages);
    Utilities.createDirectory(resources);
    Utilities.createDirectory(Utilities.path(ig, "temp"));
    Utilities.createDirectory(Utilities.path(ig, "txCache"));
    // now, copy the entire simplifer folder to pages
    Utilities.copyDirectory(folder, pages, null);
    // now, copy the resources to resources;
    Utilities.copyDirectory(Utilities.path(folder, "artifacts"), resources, null);
    JsonObject json = new JsonObject();
    JsonObject paths = new JsonObject();
    json.add("paths", paths);
    JsonArray reslist = new JsonArray();
    paths.add("resources", reslist);
    reslist.add(new JsonPrimitive("resources"));
    paths.addProperty("pages", "pages");
    paths.addProperty("temp", "temp");
    paths.addProperty("output", output);
    paths.addProperty("qa", "qa");
    paths.addProperty("specification", "http://build.fhir.org");
    json.addProperty("version", "3.0.2");
    json.addProperty("license", license);
    json.addProperty("npm-name", npmName);
    JsonObject defaults = new JsonObject();
    json.add("defaults", defaults);
    JsonObject any = new JsonObject();
    defaults.add("Any", any);
    any.addProperty("java", false);
    any.addProperty("xml", false);
    any.addProperty("json", false);
    any.addProperty("ttl", false);
    json.addProperty("canonicalBase", canonical);
    json.addProperty("sct-edition", "http://snomed.info/sct/731000124108");
    json.addProperty("source", determineSource(resources, Utilities.path(folder, "artifacts")));
    json.addProperty("path-pattern", "[type]-[id].html");
    JsonObject resn = new JsonObject();
    json.add("resources", resn);
    resn.addProperty("*", "*");
    JsonArray deplist = new JsonArray();
    json.add("dependencyList", deplist);
    for (String d : packages) {
      String[] p = d.split("\\#");
      JsonObject dep = new JsonObject();
      deplist.add(dep);
      dep.addProperty("package", p[0]);
      dep.addProperty("version", p[1]);
      dep.addProperty("name", "n"+deplist.size());
    }
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    TextFile.stringToFile(gson.toJson(json), config);
    return config;
  }


  private static String determineSource(String folder, String srcF) throws Exception {
    for (File f : new File(folder).listFiles()) {
      String src = TextFile.fileToString(f);
      if (src.contains("<ImplementationGuide ")) {
        return f.getName();
      }
    }
    throw new Exception("Unable to find Implementation Guide in "+srcF); 
  }


  private void setPackagesFolder(String value) {
    packagesFolder = value;    
  }


  public void setJekyllCommand(String theJekyllCommand) {
    if (!Utilities.noString(theJekyllCommand)) {
      this.jekyllCommand = theJekyllCommand;
    }
  }

  public String getJekyllCommand() {
    return this.jekyllCommand;
  }

  public static String determineActualIG(String ig, IGBuildMode mode) throws Exception {
    if (ig.startsWith("http://") || ig.startsWith("https://")) {
      ig = convertUrlToLocalIg(ig);
    }
    File f = new File(ig);
    if (!f.exists() && mode == IGBuildMode.AUTOBUILD) {
      String s = Utilities.getDirectoryForFile(ig);
      f = new File(s == null ? System.getProperty("user.dir") : s);
    }
    if (!f.exists()) {
      throw new Exception("Unable to find the nominated IG at "+f.getAbsolutePath());
    }
    if (f.isDirectory() && new File(Utilities.path(ig, "ig.json")).exists()) {
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
      Utilities.createDirectory(folder);
    }
    Utilities.clearDirectory(f.getAbsolutePath());
    
    String ghUrl = "https://github.com/"+org+"/"+repo+"/archive/refs/heads/"+branch+".zip";
    InputStream zip = fetchGithubUrl(ghUrl);
    Utilities.unzip(zip, Paths.get(f.getAbsolutePath()));
    return Utilities.path(folder, repo+"-"+branch);
  }

  private static InputStream fetchGithubUrl(String ghUrl) throws IOException {
    URL url = new URL(ghUrl+"?nocache=" + System.currentTimeMillis());
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    c.setInstanceFollowRedirects(true);
    return c.getInputStream();
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

  private String getToolingVersion() {
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


  private static String toMB(long maxMemory) {
    return Long.toString(maxMemory / (1024*1024));
  }


  public IGBuildMode getMode() {
    return mode;
  }


  public void setMode(IGBuildMode mode) {
    this.mode = mode;
  }


  private static String nowAsString(Calendar cal) {
    DateFormat df = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL);
    return df.format(cal.getTime());
  }

  private static String nowAsDate(Calendar cal) {
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", new Locale("en", "US"));
    return df.format(cal.getTime());
  }


  public void setFetcher(ZipFetcher theFetcher) {
    fetcher = theFetcher;
  }


  public void setContext(SimpleWorkerContext theContext) {
    context = theContext;
  }


  public void setSpecPath(String theSpecPath) {
    specPath = theSpecPath;
  }


  public void setTempDir(String theTempDir) {
    tempDir = theTempDir;
  }

  public void setOutputDir(String theDir) {
    outputDir = theDir;
  }


  public void setIgName(String theIgName) {
    igName = theIgName;
  }

  public void setConfigFileRootPath(String theConfigFileRootPath) {
    configFileRootPath = theConfigFileRootPath;
  }


  public FHIRToolingClient getWebTxServer() {
    return webTxServer;
  }


  public void setWebTxServer(FHIRToolingClient webTxServer) {
    this.webTxServer = webTxServer;
  }

  public void setDebug(boolean theDebug) {
    this.debug = theDebug;
  }

  public void setIsChild(boolean newIsChild) {
    this.isChild = newIsChild;
  }
  
  public boolean isChild() {
    return this.isChild;
  }
  
  public IGKnowledgeProvider getIgpkp() {
    return igpkp;
  }

  public List<FetchedFile> getFileList() {
    return fileList;
  }

  public ImplementationGuide getSourceIg() {
    return sourceIg;
  }

  public ImplementationGuide getPublishedIg() {
    return publishedIg;
  }


  public String getTargetOutput() {
    return targetOutput;
  }


  public void setTargetOutput(String targetOutput) {
    this.targetOutput = targetOutput;
  }


  public String getTargetOutputNested() {
    return targetOutputNested;
  }


  public void setTargetOutputNested(String targetOutputNested) {
    this.targetOutputNested = targetOutputNested;
  }
  
  private void updateInspector(HTLMLInspector parentInspector, String path) {
    parentInspector.getManual().add(path+"/full-ig.zip");
    parentInspector.getManual().add("../"+historyPage);
    parentInspector.getSpecMaps().addAll(specMaps);
  }

  private String fetchCurrentIGPubVersion() {
    if (currVer == null) {
      try {
        // This calls the GitHub api, to fetch the info on the latest release. As part of our release process, we name
        // all tagged releases as the version number. ex: "1.1.2".
        // This value can be grabbed from the "tag_name" field, or the "name" field of the returned JSON.
        JsonObject json = JsonTrackingParser.fetchJson("https://api.github.com/repos/HL7/fhir-ig-publisher/releases/latest");
        currVer = json.getAsJsonObject().get("name").getAsString();
      } catch (IOException e) {
        currVer = "?pub-ver-1?";
      }
    }
    return currVer;
  }

  private void loadMappingSpaces(byte[] source) throws Exception {
    ByteArrayInputStream is = null;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      is = new ByteArrayInputStream(source);
      Document doc = builder.parse(is);
      org.w3c.dom.Element e = XMLUtil.getFirstChild(doc.getDocumentElement());
      while (e != null) {
        MappingSpace m = new MappingSpace(XMLUtil.getNamedChild(e, "columnName").getTextContent(), XMLUtil.getNamedChild(e, "title").getTextContent(),
            XMLUtil.getNamedChild(e, "id").getTextContent(), Integer.parseInt(XMLUtil.getNamedChild(e, "sort").getTextContent()), true, false, false, XMLUtil.getNamedChild(e, "link") != null ? XMLUtil.getNamedChild(e, "link").getTextContent(): XMLUtil.getNamedChild(e, "url").getTextContent());
        mappingSpaces.put(XMLUtil.getNamedChild(e, "url").getTextContent(), m);
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


  @Override
  public void recordProfileUsage(StructureDefinition profile, Object appContext, Element element) {
    if (profile.getUrl().startsWith(igpkp.getCanonical())) { // ignore anything we didn't define
      FetchedResource example;
      if (appContext instanceof ValidatorHostContext) {
        example = (FetchedResource) ((ValidatorHostContext) appContext).getResource().getUserData("igpub.context.resource");
      } else {
        example= (FetchedResource) ((Element) appContext).getUserData("igpub.context.resource");
      }
      if (example != null) {
        FetchedResource source = null;
        for (FetchedFile f : fileList) {
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
    self.setConfigFile(Publisher.determineActualIG(path, IGBuildMode.PUBLICATION));
    self.execute();
    self.setTxServer(txServerProd);
    if (self.countErrs(self.errors) > 0) {
      throw new Exception("Building IG '"+path+"' caused an error");
    }
    
  }


}
