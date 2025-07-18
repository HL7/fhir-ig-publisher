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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nonnull;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_10_50;
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_30_50;
import org.hl7.fhir.convertors.context.ContextResourceLoaderFactory;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_10_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_14_30;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_14_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_43_50;
import org.hl7.fhir.convertors.loaders.loaderR5.NullLoaderKnowledgeProviderR5;
import org.hl7.fhir.convertors.misc.NpmPackageVersionConverter;
import org.hl7.fhir.convertors.misc.ProfileVersionAdaptor;
import org.hl7.fhir.convertors.misc.ProfileVersionAdaptor.ConversionMessage;
import org.hl7.fhir.convertors.misc.ProfileVersionAdaptor.ConversionMessageStatus;
import org.hl7.fhir.convertors.txClient.TerminologyClientFactory;
import org.hl7.fhir.exceptions.DefinitionException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.logging.Level;
import org.hl7.fhir.igtools.logging.LogbackUtilities;
import org.hl7.fhir.igtools.openehr.ArchetypeImporter;
import org.hl7.fhir.igtools.openehr.ArchetypeImporter.ProcessedArchetype;
import org.hl7.fhir.igtools.publisher.FetchedFile.FetchedBundleType;
import org.hl7.fhir.igtools.publisher.FetchedResource.AlternativeVersionResource;
import org.hl7.fhir.igtools.publisher.IFetchFile.FetchState;
import org.hl7.fhir.igtools.publisher.PublisherSigner.SignatureType;
import org.hl7.fhir.igtools.publisher.RelatedIG.RelatedIGLoadingMode;
import org.hl7.fhir.igtools.publisher.RelatedIG.RelatedIGRole;
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
import org.hl7.fhir.igtools.publisher.xig.XIGGenerator;
import org.hl7.fhir.igtools.renderers.*;
import org.hl7.fhir.igtools.renderers.ValidationPresenter.IGLanguageInformation;
import org.hl7.fhir.igtools.renderers.ValidationPresenter.LanguagePopulationPolicy;
import org.hl7.fhir.igtools.spreadsheets.IgSpreadsheetParser;
import org.hl7.fhir.igtools.spreadsheets.MappingSpace;
import org.hl7.fhir.igtools.spreadsheets.ObservationSummarySpreadsheetGenerator;
import org.hl7.fhir.igtools.templates.Template;
import org.hl7.fhir.igtools.templates.TemplateManager;
import org.hl7.fhir.igtools.ui.IGPublisherUI;
import org.hl7.fhir.igtools.web.HistoryPageUpdater;
import org.hl7.fhir.igtools.web.IGRegistryMaintainer;
import org.hl7.fhir.igtools.web.IGReleaseVersionDeleter;
import org.hl7.fhir.igtools.web.IGWebSiteMaintainer;
import org.hl7.fhir.igtools.web.PackageRegistryBuilder;
import org.hl7.fhir.igtools.web.PublicationProcess;
import org.hl7.fhir.igtools.web.PublisherConsoleLogger;
import org.hl7.fhir.r4.formats.FormatUtilities;
import org.hl7.fhir.r5.conformance.ConstraintJavaGenerator;
import org.hl7.fhir.r5.conformance.R5ExtensionsLoader;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.context.*;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.FmlParser;
import org.hl7.fhir.r5.elementmodel.LanguageUtils;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.elementmodel.ObjectConverter;
import org.hl7.fhir.r5.elementmodel.ParserBase.IdRenderingPolicy;
import org.hl7.fhir.r5.elementmodel.ParserBase.ValidationPolicy;
import org.hl7.fhir.r5.fhirpath.ExpressionNode;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.RdfParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.liquid.BaseJsonWrapper;
import org.hl7.fhir.r5.liquid.BaseTableWrapper;
import org.hl7.fhir.r5.liquid.GlobalObject.GlobalObjectRandomFunction;
import org.hl7.fhir.r5.liquid.LiquidEngine;
import org.hl7.fhir.r5.liquid.LiquidEngine.LiquidDocument;
import org.hl7.fhir.r5.model.ActivityDefinition;
import org.hl7.fhir.r5.model.ActorDefinition;
import org.hl7.fhir.r5.model.Attachment;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r5.model.Binary;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r5.model.Bundle.BundleType;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.CapabilityStatement;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementDocumentComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementMessagingComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementMessagingSupportedMessageComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceOperationComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionDesignationComponent;
import org.hl7.fhir.r5.model.CodeType;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.ConceptMap.ConceptMapGroupComponent;
import org.hl7.fhir.r5.model.Constants;
import org.hl7.fhir.r5.model.ContactDetail;
import org.hl7.fhir.r5.model.ContactPoint;
import org.hl7.fhir.r5.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r5.model.DataType;
import org.hl7.fhir.r5.model.DateTimeType;
import org.hl7.fhir.r5.model.DomainResource;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionConstraintComponent;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.model.Enumerations.CodeSystemContentMode;
import org.hl7.fhir.r5.model.Enumerations.FHIRVersion;
import org.hl7.fhir.r5.model.Enumerations.FHIRVersionEnumFactory;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r5.model.ExampleScenario;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.GraphDefinition;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.Identifier;
import org.hl7.fhir.r5.model.Identifier.IdentifierUse;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.GuidePageGeneration;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionGroupingComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionPageComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionParameterComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionResourceComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideGlobalComponent;
import org.hl7.fhir.r5.model.ImplementationGuide.SPDXLicense;
import org.hl7.fhir.r5.model.IntegerType;
import org.hl7.fhir.r5.model.Library;
import org.hl7.fhir.r5.model.ListResource;
import org.hl7.fhir.r5.model.ListResource.ListResourceEntryComponent;
import org.hl7.fhir.r5.model.MarkdownType;
import org.hl7.fhir.r5.model.Measure;
import org.hl7.fhir.r5.model.MessageDefinition;
import org.hl7.fhir.r5.model.MessageDefinition.MessageDefinitionAllowedResponseComponent;
import org.hl7.fhir.r5.model.MessageDefinition.MessageDefinitionFocusComponent;
import org.hl7.fhir.r5.model.OperationDefinition;
import org.hl7.fhir.r5.model.OperationDefinition.OperationDefinitionParameterComponent;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.PackageInformation;
import org.hl7.fhir.r5.model.Parameters;
import org.hl7.fhir.r5.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r5.model.PlanDefinition;
import org.hl7.fhir.r5.model.PlanDefinition.PlanDefinitionActionComponent;
import org.hl7.fhir.r5.model.PrimitiveType;
import org.hl7.fhir.r5.model.Property;
import org.hl7.fhir.r5.model.Provenance;
import org.hl7.fhir.r5.model.Provenance.ProvenanceAgentComponent;
import org.hl7.fhir.r5.model.Questionnaire;
import org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemAnswerOptionComponent;
import org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemInitialComponent;
import org.hl7.fhir.r5.model.QuestionnaireResponse;
import org.hl7.fhir.r5.model.Reference;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.ResourceFactory;
import org.hl7.fhir.r5.model.SearchParameter;
import org.hl7.fhir.r5.model.SearchParameter.SearchParameterComponentComponent;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionContextComponent;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.r5.model.StructureMap.StructureMapModelMode;
import org.hl7.fhir.r5.model.StructureMap.StructureMapStructureComponent;
import org.hl7.fhir.r5.model.UriType;
import org.hl7.fhir.r5.model.UsageContext;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r5.model.ValueSet.ConceptReferenceDesignationComponent;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionContainsComponent;
import org.hl7.fhir.r5.openapi.OpenApiGenerator;
import org.hl7.fhir.r5.openapi.Writer;
import org.hl7.fhir.r5.renderers.BinaryRenderer;
import org.hl7.fhir.r5.renderers.BundleRenderer;
import org.hl7.fhir.r5.renderers.ClassDiagramRenderer;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.r5.renderers.ParametersRenderer;
import org.hl7.fhir.r5.renderers.RendererFactory;
import org.hl7.fhir.r5.renderers.ResourceRenderer;
import org.hl7.fhir.r5.renderers.spreadsheets.CodeSystemSpreadsheetGenerator;
import org.hl7.fhir.r5.renderers.spreadsheets.ConceptMapSpreadsheetGenerator;
import org.hl7.fhir.r5.renderers.spreadsheets.StructureDefinitionSpreadsheetGenerator;
import org.hl7.fhir.r5.renderers.spreadsheets.ValueSetSpreadsheetGenerator;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.ExampleScenarioRendererMode;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.FixedValueFormat;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.GenerationRules;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.IResourceLinkResolver;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.ITypeParser;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.KnownLinkType;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.QuestionnaireRendererMode;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.RenderingContextLangs;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.ResourceRendererMode;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.StructureDefinitionRendererMode;
import org.hl7.fhir.r5.renderers.utils.Resolver.IReferenceResolver;
import org.hl7.fhir.r5.renderers.utils.Resolver.ResourceReferenceKind;
import org.hl7.fhir.r5.renderers.utils.Resolver.ResourceWithReference;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.terminologies.TerminologyFunctions;
import org.hl7.fhir.r5.terminologies.TerminologyUtilities;
import org.hl7.fhir.r5.terminologies.ValueSetUtilities;
import org.hl7.fhir.r5.terminologies.client.TerminologyClientContext;
import org.hl7.fhir.r5.terminologies.expansion.ValueSetExpansionOutcome;
import org.hl7.fhir.r5.terminologies.utilities.ValidationResult;
import org.hl7.fhir.r5.testfactory.TestDataFactory;
import org.hl7.fhir.r5.utils.DataTypeVisitor;
import org.hl7.fhir.r5.utils.DataTypeVisitor.IDatatypeVisitor;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.r5.utils.MappingSheetParser;
import org.hl7.fhir.r5.utils.NPMPackageGenerator;
import org.hl7.fhir.r5.utils.NPMPackageGenerator.Category;
import org.hl7.fhir.r5.utils.OperationOutcomeUtilities;
import org.hl7.fhir.r5.utils.ResourceSorters;
import org.hl7.fhir.r5.utils.ResourceUtilities;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.r5.utils.XVerExtensionManager;
import org.hl7.fhir.r5.utils.client.FHIRToolingClient;
import org.hl7.fhir.r5.utils.formats.CSVWriter;
import org.hl7.fhir.r5.utils.sql.Runner;
import org.hl7.fhir.r5.utils.sql.StorageJson;
import org.hl7.fhir.r5.utils.sql.StorageSqlite3;
import org.hl7.fhir.r5.utils.structuremap.StructureMapAnalysis;
import org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities;
import org.hl7.fhir.r5.utils.validation.IValidationProfileUsageTracker;
import org.hl7.fhir.r5.utils.validation.ValidatorSession;
import org.hl7.fhir.utilities.CSVReader;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.CompressionUtilities;
import org.hl7.fhir.utilities.DurationUtil;
import org.hl7.fhir.utilities.ENoDump;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.MagicResources;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.MarkDownProcessor.Dialect;
import org.hl7.fhir.utilities.MimeType;
import org.hl7.fhir.utilities.OIDUtilities;
import org.hl7.fhir.utilities.StandardsStatus;
import org.hl7.fhir.utilities.StringPair;
import org.hl7.fhir.utilities.TimeTracker;
import org.hl7.fhir.utilities.TimeTracker.Session;
import org.hl7.fhir.utilities.UUIDUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.ZipGenerator;
import org.hl7.fhir.utilities.filesystem.CSFile;
import org.hl7.fhir.utilities.http.HTTPResult;
import org.hl7.fhir.utilities.http.ManagedWebAccess;
import org.hl7.fhir.utilities.i18n.I18nConstants;
import org.hl7.fhir.utilities.i18n.JsonLangFileProducer;
import org.hl7.fhir.utilities.i18n.LanguageFileProducer;
import org.hl7.fhir.utilities.i18n.LanguageFileProducer.TranslationUnit;
import org.hl7.fhir.utilities.i18n.LanguageTag;
import org.hl7.fhir.utilities.i18n.PoGetTextProducer;
import org.hl7.fhir.utilities.i18n.RegionToLocaleMapper;
import org.hl7.fhir.utilities.i18n.RenderingI18nContext;
import org.hl7.fhir.utilities.i18n.XLIFFProducer;
import org.hl7.fhir.utilities.i18n.subtag.LanguageSubtagRegistry;
import org.hl7.fhir.utilities.i18n.subtag.LanguageSubtagRegistryLoader;
import org.hl7.fhir.utilities.json.JsonException;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonBoolean;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonPrimitive;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.model.JsonString;
import org.hl7.fhir.utilities.npm.*;
import org.hl7.fhir.utilities.npm.NpmPackage.PackageResourceInformation;
import org.hl7.fhir.utilities.npm.PackageGenerator.PackageType;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;
import org.hl7.fhir.utilities.settings.FhirSettings;
import org.hl7.fhir.utilities.turtle.Turtle;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.hl7.fhir.utilities.validation.ValidationOptions;
import org.hl7.fhir.utilities.validation.ValidationOptions.R5BundleRelativeReferencePolicy;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;
import org.hl7.fhir.utilities.xml.XMLUtil;
import org.hl7.fhir.utilities.xml.XmlEscaper;
import org.hl7.fhir.validation.SQLiteINpmPackageIndexBuilderDBImpl;
import org.hl7.fhir.validation.ValidatorSettings;
import org.hl7.fhir.validation.ValidatorUtils;
import org.hl7.fhir.validation.instance.InstanceValidator;
import org.hl7.fhir.validation.instance.utils.ValidationContext;
import org.hl7.fhir.validation.profile.ProfileValidator;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import lombok.Getter;
import lombok.Setter;


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
 * @author Grahame Grieve
 */

public class Publisher implements ILoggingService, IReferenceResolver, IValidationProfileUsageTracker, IResourceLinkResolver {

   public enum UMLGenerationMode {
    NONE, SOURCED, ALL;

    public static UMLGenerationMode fromCode(String value) {
      if (Utilities.noString(value)) {
        return NONE;
      } else switch (value.toLowerCase()) {
      case "none" :
        return NONE;
      case "source":
      case "sourced":
        return SOURCED;
      case "all" : 
      case "always" : 
        return ALL;
      default:
        throw new FHIRException("Unknown UML generation mode `"+value+"`");
      }
    }
  }


  public enum PinningPolicy {NO_ACTION, FIX, WHEN_MULTIPLE_CHOICES}

  private static final String TOOLING_IG_CURRENT_RELEASE = "0.5.0";

  public class FragmentUseRecord {

    private int count;
    private long time;
    private long size;
    private boolean used;
    
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
      if (trackFragments) {
        b.append(",");
        b.append(used);
      }
    }

  }

  private static final String PACKAGE_CACHE_FOLDER_PARAM = "-package-cache-folder";

  public class ContainedResourceDetails {

    private String type;
    private String id;
    private String title;
    private String description;
    private CanonicalResource canonical;

    public ContainedResourceDetails(String type, String id, String title, String description, CanonicalResource canonical) {
      this.type = type;
      this.id = id;
      this.title = title;
      this.description = description;
      this.canonical = canonical;
    }

    public String getType() {
      return type;
    }

    public String getId() {
      return id;
    }

    public String getTitle() {
      return title;
    }

    public String getDescription() {
      return description;
    }

    public CanonicalResource getCanonical() {
      return canonical;
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

  public class TypeParserR2 implements ITypeParser {

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

  public class TypeParserR14 implements ITypeParser {

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

  public class TypeParserR3 implements ITypeParser {

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

  public class TypeParserR4 implements ITypeParser {

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
      org.hl7.fhir.r4.model.Type t = new org.hl7.fhir.r4.formats.XmlParser().parseType(xml, type);
      return VersionConvertorFactory_40_50.convertType(t);
    }
    @Override
    public Base parseType(Element base) throws FHIRFormatError, IOException, FHIRException {
      ByteArrayOutputStream bs = new ByteArrayOutputStream();
      new org.hl7.fhir.r5.elementmodel.XmlParser(context).compose(base, bs, OutputStyle.NORMAL, null);
      String xml = new String(bs.toByteArray(), StandardCharsets.UTF_8);
      return parseType(xml, base.fhirType());
    }
  }

  public class TypeParserR4B implements ITypeParser {

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

  public class TypeParserR5 implements ITypeParser {

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
      return new org.hl7.fhir.r5.formats.XmlParser().parseType(xml, type);
    }
    @Override
    public Base parseType(Element base) throws FHIRFormatError, IOException, FHIRException {
      throw new NotImplementedException();
    }
  }

  public class ElideExceptDetails {
    private String base = null;
    private String except = null;

    public ElideExceptDetails(String except) {
      this.except = except;
    }

    public String getBase() {
      return base;
    }

    public boolean hasBase() { return base != null; }

    public void setBase(String base) { this.base = base; }

    public String getExcept() { return except; }
  }

  public enum IGBuildMode { MANUAL, AUTOBUILD, WEBSERVER, PUBLICATION }


  public enum LinkTargetType {

  }

  public static class LinkedSpecification {
    private SpecMapManager spm;
    private NpmPackage npm;
    public LinkedSpecification(SpecMapManager spm, NpmPackage npm) {
      super();
      this.spm = spm;
      this.npm = npm;
    }
    public SpecMapManager getSpm() {
      return spm;
    }
    public NpmPackage getNpm() {
      return npm;
    }  
  }

  public enum CacheOption {
    LEAVE, CLEAR_ERRORS, CLEAR_ALL;
  }

  public static final String FHIR_SETTINGS_PARAM = "-fhir-settings";

  public static final int FMM_DERIVATION_MAX = 5;

  private static final String IG_NAME = "!ig!";

  private static final String REDIRECT_SOURCE = "<html>\r\n<head>\r\n<meta http-equiv=\"Refresh\" content=\"0; url=site/index.html\"/>\r\n</head>\r\n"+
      "<body>\r\n<p>See here: <a href=\"site/index.html\">this link</a>.</p>\r\n</body>\r\n</html>\r\n";

  private static final long JEKYLL_TIMEOUT = 60000 * 5; // 5 minutes....
  private static final long FSH_TIMEOUT = 60000 * 5; // 5 minutes....
  private static final int PRISM_SIZE_LIMIT = 16384;

  private String consoleLog;
  private String configFile;
  private String sourceDir;
  private String destDir;
  private FHIRToolingClient webTxServer;
  private String txServer;
  private Locale forcedLanguage;
  private String igPack = "";
  private boolean debug;
  private boolean isChild;
  private boolean cacheVersion;
  private boolean appendTrailingSlashInDataFile;
  private boolean newIg = false;
  private Map<String,String> countryCodeForName = null;
  private Map<String,String> countryNameForCode = null;
  private Map<String,String> countryCodeForNumeric = null;
  private Map<String,String> countryCodeFor2Letter = null;
  private Map<String,String> shortCountryCode = null;
  private Map<String,String> stateNameForCode = null;
  private Map<String, Map<String, ElementDefinition>> sdMapCache = new HashMap<>();
  private List<String> ignoreFlags = null;
  private Map<String, Boolean> wantGenParams = new HashMap<>();

  private Publisher childPublisher = null;
  private boolean genExampleNarratives = true;
  private final List<String> noNarratives = new ArrayList<>();
  private List<FetchedResource> noNarrativeResources = new ArrayList<>();
  private final List<String> noValidate = new ArrayList<>();
  private final List<String> customResourceFiles = new ArrayList<>();
  private List<FetchedResource> noValidateResources = new ArrayList<>();

  private List<String> resourceDirs = new ArrayList<String>();
  private List<String> resourceFactoryDirs = new ArrayList<String>();
  private List<String> pagesDirs = new ArrayList<String>();
  private List<String> testDirs = new ArrayList<String>();
  private List<String> dataDirs = new ArrayList<String>();
  private List<String> otherDirs = new ArrayList<String>();
  private String tempDir;
  private String tempLangDir;
  private String outputDir;
  private String specPath;
  private String qaDir;
  private String version;
  private FhirPublication pubVersion;
  private long jekyllTimeout = JEKYLL_TIMEOUT;
  private long fshTimeout = FSH_TIMEOUT;
  private SuppressedMessageInformation suppressedMessages = new SuppressedMessageInformation();
  private boolean tabbedSnapshots = false;

  private String igName;
  private IGBuildMode mode; // for the IG publication infrastructure

  private SimpleFetcher fetcher = new SimpleFetcher(this);
  private SimpleWorkerContext context; //
  private DataRenderer dr;
  private InstanceValidator validator;
  private ProfileValidator pvalidator;
  private CodeSystemValidator csvalidator;
  private XVerExtensionManager xverManager;
  private IGKnowledgeProvider igpkp;
  private List<SpecMapManager> specMaps = new ArrayList<SpecMapManager>();
  private List<NpmPackage> npms = new ArrayList<NpmPackage>();
  private List<LinkedSpecification> linkSpecMaps = new ArrayList<>();
  private List<String> suppressedIds = new ArrayList<>();

  private Map<String, MappingSpace> mappingSpaces = new HashMap<String, MappingSpace>();
  private Map<ImplementationGuideDefinitionResourceComponent, FetchedFile> fileMap = new HashMap<ImplementationGuideDefinitionResourceComponent, FetchedFile>();
  private Map<String, FetchedFile> altMap = new HashMap<String, FetchedFile>();
  private Map<String, FetchedResource> canonicalResources = new HashMap<String, FetchedResource>();
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
  private Set<FetchedResource> testplans = new HashSet<FetchedResource>();
  private Set<FetchedResource> testscripts = new HashSet<FetchedResource>();
  private Set<String> profileTestCases = new HashSet<>();
  private HashMap<String, FetchedResource> resources = new HashMap<String, FetchedResource>();
  private HashMap<String, ImplementationGuideDefinitionPageComponent> igPages = new HashMap<String, ImplementationGuideDefinitionPageComponent>();
  private List<String> logOptions = new ArrayList<String>();
  private List<String> listedURLExemptions = new ArrayList<String>();
  private String altCanonical;
  private String jekyllCommand = "jekyll";
  private boolean makeQA = true;
  private boolean bundleReferencesResolve = true;
  private CqlSubSystem cql;
  private File killFile;
  private List<PageFactory> pageFactories = new ArrayList<>();

  private ILoggingService logger = this;

  private HTMLInspector inspector;

  private List<String> prePagesDirs = new ArrayList<String>();
  private HashMap<String, PreProcessInfo> preProcessInfo = new HashMap<String, PreProcessInfo>();

  private String historyPage;

  private String vsCache;

  private String adHocTmpDir;

  private RenderingContext rc;
  private RenderingContextLangs rcLangs; // prepared lang alternatives 

  private List<ContactDetail> contacts;
  private List<UsageContext> contexts;
  private List<String> binaryPaths = new ArrayList<>();
  private MarkdownType copyright;
  private List<CodeableConcept> jurisdictions;
  private Enumeration<SPDXLicense> licenseInfo;
  private StringType publisher;
  private String businessVersion;
  private String wgm;
  private List<ContactDetail> defaultContacts;
  private List<UsageContext> defaultContexts;
  private MarkdownType defaultCopyright;
  private String defaultWgm;
  private List<CodeableConcept> defaultJurisdictions;
  private Enumeration<SPDXLicense> defaultLicenseInfo;
  private StringType defaultPublisher;
  private String defaultBusinessVersion;

  private CacheOption cacheOption;

  private String configFileRootPath;

  private MarkDownProcessor markdownEngine;
  private List<ValueSet> expansions = new ArrayList<>();

  private String npmName;

  private NPMPackageGenerator npm;
  private Map<String, NPMPackageGenerator> vnpms = new HashMap<String, NPMPackageGenerator>();
  private Map<String, NPMPackageGenerator> lnpms = new HashMap<String, NPMPackageGenerator>();
  
  private FilesystemPackageCacheManager pcm;

  private TemplateManager templateManager;

  private String rootDir;
  private String templatePck;

  private boolean templateLoaded ;

  private String packagesFolder;
  private String targetOutput;
  private String repoSource;

  private void setRepoSource(final String repoSource) {
    if (repoSource == null) {
      return;
    }
    this.repoSource = GitUtilities.getURLWithNoUserInfo(repoSource, "-repo CLI parameter");
  }

  private String targetOutputNested;

  private String folderToDelete;

  private String specifiedVersion;

  private NpmPackage packge;

  private String txLog;

  private boolean includeHeadings;
  private String openApiTemplate;
  private boolean isPropagateStatus;
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
  private IpaComparator ipaComparator;
  private IpsComparator ipsComparator;

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

  public boolean isNoSushi() {
    return noSushi;
  }

  public void setNoSushi(boolean noSushi) {
    this.noSushi = noSushi;
  }

  private boolean noSushi;

  private Map<String, String> loadedIds;

  private boolean duplicateInputResourcesDetected;

  private List<String> comparisonVersions;
  private List<String> ipaComparisons;
  private List<String> ipsComparisons;
  private String versionToAnnotate;

  private TimeTracker tt;

  private boolean publishing = false;

  private String igrealm;

  private String copyrightYear;

  @Getter
  @Setter
  private boolean validationOff;

  @Getter
  @Setter
  private boolean generationOff;

  @Getter
  @Setter
  private String packageCacheFolder = null;
  private String fmtDateTime = "yyyy-MM-dd HH:mm:ssZZZ";
  private String fmtDate = "yyyy-MM-dd";
  private DependentIGFinder dependentIgFinder;
  private List<StructureDefinition> modifierExtensions = new ArrayList<>();
  private Object branchName;
  private R4ToR4BAnalyser r4tor4b;
  private List<DependencyAnalyser.ArtifactDependency> dependencyList;
  private Map<String, List<String>> trackedFragments = new HashMap<>();
  private PackageInformation packageInfo;
  private boolean tocSizeWarning = false;
  private CSVWriter allProfilesCsv;
  private StructureDefinitionSpreadsheetGenerator allProfilesXlsx;
  private boolean produceJekyllData;
  private boolean noUsageCheck;
  private boolean hasTranslations;
  private String defaultTranslationLang;
  private List<String> translationLangs = new ArrayList<>();
  private List<String> translationSources = new ArrayList<>();
  private List<String> usedLangFiles = new ArrayList<>();
  private List<String> viewDefinitions = new ArrayList<>();
  private int validationLogTime = 0;
  private long maxMemory = 0;
  private String oidRoot;
  private IniFile oidIni;

  private boolean hintAboutNonMustSupport = false;
  private boolean anyExtensionsAllowed = false;
  private boolean checkAggregation = false;
  private boolean autoLoad = false;
  private boolean showReferenceMessages = false;
  private boolean noExperimentalContent = false;
  private boolean displayWarnings = false;
  private boolean newMultiLangTemplateFormat = false;
  private List<RelatedIG> relatedIGs = new ArrayList<>();

  long last = System.currentTimeMillis();
  private List<String> unknownParams = new ArrayList<>();

  private FixedValueFormat fixedFormat = FixedValueFormat.JSON;

  private static PublisherConsoleLogger consoleLogger;
  private IPublisherModule module;
  private boolean milestoneBuild;
  private BaseRenderer bdr;
  
  private class PreProcessInfo {
    private String xsltName;
    private byte[] xslt;
    private String relativePath;
    public PreProcessInfo(String xsltName, String relativePath) throws IOException {
      this.xsltName = xsltName;
      if (xsltName!=null) {
        this.xslt = FileUtilities.fileToBytes(xsltName);
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

  public Publisher() {
    NpmPackageIndexBuilder.setExtensionFactory(new SQLiteINpmPackageIndexBuilderDBImpl.SQLiteINpmPackageIndexBuilderDBImplFactory());
  }

  public void execute() throws Exception {
    XhtmlNode.setCheckParaGeneral(true);
    
    tt = new TimeTracker();
    initialize();
    if (isBuildingTemplate) {
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
    if (templateLoaded && new File(rootDir).exists()) {
      FileUtilities.clearDirectory(Utilities.path(rootDir, "template"));
    }
    if (folderToDelete != null) {
      try {
        FileUtilities.clearDirectory(folderToDelete);
        new File(folderToDelete).delete();
      } catch (Throwable e) {
        // nothing
      }
    }
  }

  private String renderGlobals() {
    if (sourceIg.hasGlobal()) {
      StringBuilder b = new StringBuilder();
      boolean list = sourceIg.getGlobal().size() > 1;
      if (list) {
        b.append("<ul>\r\n");
      }
      for (ImplementationGuideGlobalComponent g : sourceIg.getGlobal()) {
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
    FileUtilities.createDirectory(outputDir);
    long startTime = System.nanoTime();
    JsonObject qaJson = new JsonObject();
    StringBuilder txt = new StringBuilder();
    StringBuilder txtGen = new StringBuilder();
    qaJson.add("url", templateInfo.asString("canonical"));
    txt.append("url = "+templateInfo.asString("canonical")+"\r\n");
    txtGen.append("url = "+templateInfo.asString("canonical")+"\r\n");
    qaJson.add("package-id", templateInfo.asString("name"));
    txt.append("package-id = "+templateInfo.asString("name")+"\r\n");
    txtGen.append("package-id = "+templateInfo.asString("name")+"\r\n");
    qaJson.add("ig-ver", templateInfo.asString("version"));
    txt.append("ig-ver = "+templateInfo.asString("version")+"\r\n");
    txtGen.append("ig-ver = "+templateInfo.asString("version")+"\r\n");
    qaJson.add("date", new SimpleDateFormat("EEE, dd MMM, yyyy HH:mm:ss Z", new Locale("en", "US")).format(execTime.getTime()));
    qaJson.add("dateISO8601", new DateTimeType(execTime).asStringValue());
    qaJson.add("version", Constants.VERSION);
    qaJson.add("tool", Constants.VERSION+" ("+ToolsVersion.TOOLS_VERSION+")");
    try {
      File od = new File(outputDir);
      FileUtils.cleanDirectory(od);
      npm = new NPMPackageGenerator(Utilities.path(outputDir, "package.tgz"), templateInfo, execTime.getTime(), !publishing);
      npm.loadFiles(rootDir, new File(rootDir), ".git", "output", "package", "temp");
      npm.finish();

      FileUtilities.stringToFile(makeTemplateIndexPage(), Utilities.path(outputDir, "index.html"));
      FileUtilities.stringToFile(makeTemplateJekyllIndexPage(), Utilities.path(outputDir, "jekyll.html"));
      FileUtilities.stringToFile(makeTemplateQAPage(), Utilities.path(outputDir, "qa.html"));

      if (mode != IGBuildMode.AUTOBUILD) {
        pcm.addPackageToCache(templateInfo.asString("name"), templateInfo.asString("version"), new FileInputStream(npm.filename()), Utilities.path(outputDir, "package.tgz"));
        pcm.addPackageToCache(templateInfo.asString("name"), "dev", new FileInputStream(npm.filename()), Utilities.path(outputDir, "package.tgz"));
      }
    } catch (Exception e) {
      e.printStackTrace();
      qaJson.add("exception", e.getMessage());
      txt.append("exception = "+e.getMessage()+"\r\n");
      txtGen.append("exception = "+e.getMessage()+"\r\n");
    }
    long endTime = System.nanoTime();
    String json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(qaJson, true);
    FileUtilities.stringToFile(json, Utilities.path(outputDir, "qa.json"));
    FileUtilities.stringToFile(txt.toString(), Utilities.path(outputDir, "qa.txt"));
    FileUtilities.stringToFile(txtGen.toString(), Utilities.path(outputDir, "qa.compare.txt"));

    FileUtilities.createDirectory(tempDir);
    ZipGenerator zip = new ZipGenerator(Utilities.path(tempDir, "full-ig.zip"));
    zip.addFolder(outputDir, "site/", false);
    zip.addFileSource("index.html", REDIRECT_SOURCE, false);
    zip.close();
    FileUtilities.copyFile(Utilities.path(tempDir, "full-ig.zip"), Utilities.path(outputDir, "full-ig.zip"));
    new File(Utilities.path(tempDir, "full-ig.zip")).delete();

    // registering the package locally
    log("Finished @ "+nowString()+". "+DurationUtil.presentDuration(endTime - startTime)+". Output in "+outputDir);
  }


  private String nowString() {
    LocalDateTime dateTime = LocalDateTime.now();
    DateTimeFormatter shortFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(rc == null || rc.getLocale() == null ?  Locale.getDefault() : rc.getLocale());
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
    return page.replace("{{npm}}", templateInfo.asString("name")).replace("{{canonical}}", templateInfo.asString("canonical"));
  }

  private String makeTemplateJekyllIndexPage() {
    String page = "---\r\n"+
        "layout: page\r\n"+
        "title: {{npm}}\r\n"+
        "---\r\n"+
        "  <p><b>Template {{npm}}</b></p>\r\n"+
        "  <p>You can <a href=\"package.tgz\">download the template</a>, though you should not need to; just refer to the template as {{npm}} in your IG configuration.</p>\r\n"+
        "  <p>A <a href=\"{{canonical}}/history.html\">full version history is published</a></p>\r\n";
    return page.replace("{{npm}}", templateInfo.asString("name")).replace("{{canonical}}", templateInfo.asString("canonical"));
  }

  private String makeTemplateQAPage() {
    String page = "<!DOCTYPE HTML><html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\"><head><title>Template QA Page</title></head><body><p><b>Template {{npm}} QA</b></p><p>No useful QA on templates - if you see this page, the template built ok.</p></body></html>";
    return page.replace("{{npm}}", templateInfo.asString("name"));
  }

  public void createIg() throws Exception, IOException, EOperationOutcome, FHIRException {
    try {
      TimeTracker.Session tts = tt.start("loading");
      load();
      tts.end();

      tts = tt.start("generate");
      log("Processing Conformance Resources");

      log("Checking Language");
      checkLanguage();
      loadConformance2();
      checkSignBundles();

      if (!validationOff) {
        log("Validating Resources");
        try {
          validate();
        } catch (Exception ex){
          log("Unhandled Exception: " +ex.toString());
          throw(ex);
        }
        validatorSession.close();
      }
      if (needsRegen) {
        log("Regenerating Narratives");
        generateNarratives(true);
      }
      log("Processing Provenance Records");
      processProvenanceDetails();
      if (hasTranslations) {
        log("Generating Translation artifacts");
        processTranslationOutputs();
      }
      log("Generating Outputs in "+outputDir);
      Map<String, String> uncsList = scanForUnattributedCodeSystems();
      generate();
      clean();
      dependentIgFinder.finish(outputDir, sourceIg.present());
      List<FetchedResource> fragments = new ArrayList<>(); 
      for (var f : fileList) {
        for (var r : f.getResources()) {
          if (r.getResource() != null && r.getResource() instanceof CodeSystem && ((CodeSystem) r.getResource()).getContent() == CodeSystemContentMode.FRAGMENT) {
            fragments.add(r);
          }
        }
      }
      checkForSnomedVersion();
      if (txLog != null) {
        FileUtilities.copyFile(txLog, Utilities.path(rootDir, "output", "qa-tx.html"));
      }
      ValidationPresenter val = new ValidationPresenter(version, workingVersion(), igpkp, childPublisher == null? null : childPublisher.getIgpkp(), rootDir, npmName, childPublisher == null? null : childPublisher.npmName,
          IGVersionUtil.getVersion(), fetchCurrentIGPubVersion(), realmRules, previousVersionComparator, ipaComparator, ipsComparator,
          new DependencyRenderer(pcm, outputDir, npmName, templateManager, dependencyList, context, markdownEngine, rc, specMaps).render(publishedIg, true, false, false), new HTAAnalysisRenderer(context, outputDir, markdownEngine).render(publishedIg.getPackageId(), fileList, publishedIg.present()),
          new PublicationChecker(repoRoot, historyPage, markdownEngine, findReleaseLabelString(), publishedIg, relatedIGs).check(), renderGlobals(), copyrightYear, context, scanForR5Extensions(), modifierExtensions,
          generateDraftDependencies(), noNarrativeResources, noValidateResources, validationOff, generationOff, dependentIgFinder, context.getTxClientManager(), 
          fragments, makeLangInfo(), relatedIGs);
      val.setValidationFlags(hintAboutNonMustSupport, anyExtensionsAllowed, checkAggregation, autoLoad, showReferenceMessages, noExperimentalContent, displayWarnings);
      FileUtilities.stringToFile(new IPViewRenderer(uncsList, inspector.getExternalReferences(), inspector.getImageRefs(), inspector.getCopyrights(),
          ipStmt, inspector.getVisibleFragments().get("1"), context).execute(), Utilities.path(outputDir, "qa-ipreview.html"));
      tts.end();
      if (isChild()) {
        log("Built. "+tt.report());
      } else {
        log("Built. "+tt.report());
        log("Generating QA");
        log("Validation output in "+val.generate(sourceIg.getName(), errors, fileList, Utilities.path(destDir != null ? destDir : outputDir, "qa.html"), suppressedMessages, pinSummary()));
      }
      recordOutcome(null, val);
      log("Finished @ "+nowString()+". Max Memory Used = "+Utilities.describeSize(maxMemory)+logSummary());
    } catch (Exception e) {
      try {
        recordOutcome(e, null);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      throw e;
    }
  }

  private void checkSignBundles() throws Exception {
    log("Checking for Bundles to sign");
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if ("Bundle".equals(r.fhirType())) {
          Element sig = r.getElement().getNamedChild("signature");
          if (sig != null && !sig.hasChild("data") && "application/jose".equals(sig.getNamedChildValue("sigFormat"))) {
            signer.signBundle(r.getElement(), sig, SignatureType.JOSE);
          }
          if (sig != null && !sig.hasChild("data") && "application/pkcs7-signature".equals(sig.getNamedChildValue("sigFormat"))) {
            signer.signBundle(r.getElement(), sig, SignatureType.DIGSIG);
          }
        }
      }
    }
  }

  private String pinSummary() {
    String sfx = "";
    if (pinDest != null) {
      sfx = " (in manifest Parameters/"+pinDest+")";
    }
    switch (pinningPolicy) {
    case FIX: return ""+pinCount+" (all)"+sfx;
    case NO_ACTION: return "n/a";
    case WHEN_MULTIPLE_CHOICES: return ""+pinCount+" (when multiples)"+sfx;
    default: return "??";    
    }
  }

  private Map<String, String> scanForUnattributedCodeSystems() {
    Map<String, String> list = new HashMap<>();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        String link = igpkp.getLinkFor(r, true);
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
        CodeSystem cs = context.fetchCodeSystem(url);
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
    if (!(igrealm == null || "uv".equals(igrealm)) && context.getCodeSystemsUsed().contains("http://snomed.info/sct")) {
      boolean ok = false;
      for (ParametersParameterComponent p : context.getExpansionParameters().getParameter()) {
        if ("system-version".equals(p.getName()) && p.hasValuePrimitive() && p.getValue().primitiveValue().startsWith("http://snomed.info/sct")) {
          ok = true;
        }
      }
      if (!ok) {        
        errors.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "IG", "The IG is not for the international realm, and it uses SNOMED CT, so it should fix the SCT edition in the expansion parameters", IssueSeverity.WARNING));
      }
    }

  }
  
  
  private IGLanguageInformation makeLangInfo() {
    IGLanguageInformation info = new IGLanguageInformation();
    info.setIgResourceLanguage(publishedIg.getLanguage());
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        info.seeResource(r.getElement().hasChild("language")) ;
      }
    }
    info.setPolicy(langPolicy );
    info.setIgLangs(new ArrayList<String>());
    return info;
  }

  private StringType findReleaseLabel() {
    for (ImplementationGuideDefinitionParameterComponent p : publishedIg.getDefinition().getParameter()) {
      if ("releaselabel".equals(p.getCode().getCode())) {
        return p.getValueElement();
      }
    }
    return null;
  }

  private String findReleaseLabelString() {
    StringType s = findReleaseLabel();
    return s == null ? "n/a" : s.asStringValue();
  }

  private String logSummary() {
    if (consoleLogger != null && consoleLogger.started()) {
      return ". Log file saved in "+consoleLogger.getFilename();
    } else {
      return "";
    }
  }

  private String generateDraftDependencies() throws IOException {
    DraftDependenciesRenderer dr = new DraftDependenciesRenderer(context, packageInfo.getVID());
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        dr.checkResource(r);
      }
    }
    return dr.render();
  }

  private void processTranslationOutputs() throws IOException {

    PublisherTranslator pt = new PublisherTranslator(context, sourceIg.hasLanguage() ? sourceIg.getLanguage() : "en", defaultTranslationLang, translationLangs);
    pt.start(tempLangDir);
    for (FetchedFile f : fileList) {
      f.start("translate");
      try {
        for (FetchedResource r : f.getResources()) {
          pt.translate(f, r);
        }
      } finally {
        f.finish("translate");
      }
    }
    pt.finish();
  }

  private Set<String> scanForR5Extensions() {
    XVerExtensionManager xver = new XVerExtensionManager(context);
    Set<String> set = new HashSet<>();
    scanProfilesForR5(xver, set);
    scanExamplesForR5(xver, set);
    return set;
  }

  private void scanExamplesForR5(XVerExtensionManager xver, Set<String> set) {
    for (FetchedFile f : fileList) {
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
    for (FetchedFile f : fileList) {
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
    StructureDefinition base = context.fetchResource(StructureDefinition.class, sd.getBaseDefinition());
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

  private void processProvenanceDetails() throws Exception {
    for (FetchedFile f : fileList) {
      f.start("processProvenanceDetails");
      try {

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
      } finally {
        f.finish("processProvenanceDetails");
      }
    }
  }

  public boolean processProvenanceEntries(FetchedFile f, FetchedResource r) throws Exception {
    boolean isHistory = false;
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
      RendererFactory.factory(pv, rc.setParser(getTypeLoader(null))).renderResource(ResourceWrapper.forResource(rc, pv));
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
    if (pv.getAuthorizationFirstRep().getConcept().hasText()) {
      res.setComment(pv.getAuthorizationFirstRep().getConcept().getText());
    } else if (pv.getActivity().hasText()) {
      res.setComment(pv.getActivity().getText());
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
        j.add("url", sourceIg.getUrl());
        j.add("name", sourceIg.getName());
        j.add("title", sourceIg.getTitle());
        j.add("description", preProcessMarkdown(sourceIg.getDescription()));
        if (sourceIg.hasDate()) {
          j.add("ig-date", sourceIg.getDateElement().primitiveValue());
        }
        if (sourceIg.hasStatus()) {
          j.add("status", sourceIg.getStatusElement().primitiveValue());
        }
      }
      if (publishedIg != null && publishedIg.hasPackageId()) {
        j.add("package-id", publishedIg.getPackageId());
        j.add("ig-ver", publishedIg.getVersion());
      }
      j.add("date", new SimpleDateFormat("EEE, dd MMM, yyyy HH:mm:ss Z", new Locale("en", "US")).format(execTime.getTime()));
      j.add("dateISO8601", new DateTimeType(execTime).asStringValue());
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
      j.add("version", version);
      if (templatePck != null) {
        j.add("template", templatePck);
      }
      j.add("tool", Constants.VERSION+" ("+ToolsVersion.TOOLS_VERSION+")");
      j.add("maxMemory", maxMemory);
      String json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(j, true);
      FileUtilities.stringToFile(json, Utilities.path(destDir != null ? destDir : outputDir, "qa.json"));

      j = new JsonObject();
      j.add("date", new SimpleDateFormat("EEE, dd MMM, yyyy HH:mm:ss Z", new Locale("en", "US")).format(execTime.getTime()));
      j.add("doco", "For each file: start is seconds after start activity occurred. Length = milliseconds activity took");

      for (FetchedFile f : fileList) {
        JsonObject fj = new JsonObject();
        j.forceArray("files").add(fj);
        f.processReport(f, fj);
      }
      json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(j, true);
      FileUtilities.stringToFile(json, Utilities.path(destDir != null ? destDir : outputDir, "qa-time-report.json"));

      StringBuilder b = new StringBuilder();
      b.append("Source File");
      b.append("\t");
      b.append("Size");
      for (String s : FetchedFile.getColumns()) {
        b.append("\t");
        b.append(s);
      }
      b.append("\r\n");
      for (FetchedFile f : fileList) {
        f.appendReport(b);
        b.append("\r\n");
      }
      FileUtilities.stringToFile(b.toString(), Utilities.path(destDir != null ? destDir : outputDir, "qa-time-report.tsv"));


    } catch (Exception e) {
      // nothing at all
      e.printStackTrace();
    }
  }

  private String preProcessMarkdown(String description) throws Exception {
    if (bdr == null) {
      return "description";
    }
    return bdr.preProcessMarkdown("json", description);
  }

  public CacheOption getCacheOption() {
    return cacheOption;
  }


  public void setCacheOption(CacheOption cacheOption) {
    this.cacheOption = cacheOption;
  }


  @Override
  public ResourceWithReference resolve(RenderingContext context, String url, String version) throws IOException {
    if (url == null) {
      return null;
    }

    String[] parts = url.split("\\/");
    if (parts.length >= 2 && !Utilities.startsWithInList(url, "urn:uuid:", "urn:oid:", "cid:")) {
      for (FetchedFile f : fileList) {
        for (FetchedResource r : f.getResources()) {
          if (r.getElement() != null && r.fhirType().equals(parts[0]) && r.getId().equals(parts[1])) {
            String path = igpkp.getLinkFor(r, true);
            return new ResourceWithReference(ResourceReferenceKind.EXTERNAL, url, path, ResourceWrapper.forResource(context, r.getElement()));
          }
          if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
            if (url.equals(((CanonicalResource) r.getResource()).getUrl())) {
              String path = igpkp.getLinkFor(r, true);
              return new ResourceWithReference(ResourceReferenceKind.EXTERNAL, url, path, ResourceWrapper.forResource(context, r.getResource()));
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
                return new ResourceWithReference(ResourceReferenceKind.EXTERNAL, url, path, ResourceWrapper.forResource(context, r.getElement()));
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
              return new ResourceWithReference(ResourceReferenceKind.EXTERNAL, url, path, ResourceWrapper.forResource(context, r.getElement()));
            }
          }
        }
      }
    }

    for (SpecMapManager sp : specMaps) {
      String fp = Utilities.isAbsoluteUrl(url) ? url : sp.getBase()+"/"+url;
      
      String path;
      try {
        path = sp.getPath(fp, null, null, null);
      } catch (Exception e) {
        path = null;
      }
      
      
      // hack around an error in the R5 specmap file 
      if (path != null && sp.isCore() && path.startsWith("http://terminology.hl7.org")) {
        path = null;
      }
      
      if (path != null) {
        InputStream s = null;
        if (sp.getNpm() != null && fp.contains("/") && sp.getLoader() != null) {
          String[] pl = fp.split("\\/");
          String rt = pl[pl.length-2];
          String id = pl[pl.length-1]; 
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
    }

    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if ("ActorDefinition".equals(r.fhirType())) {
          ActorDefinition act = ((ActorDefinition) r.getResource());
          String aurl = ToolingExtensions.readStringExtension(act, "http://hl7.org/fhir/tools/StructureDefinition/ig-actor-example-url");
          if (aurl != null && url.startsWith(aurl)) {
            String tail = url.substring(aurl.length()+1);
            for (ImplementationGuideDefinitionResourceComponent igr : sourceIg.getDefinition().getResource()) {
              if (tail.equals(igr.getReference().getReference())) {
                String actor = ToolingExtensions.readStringExtension(igr, "http://hl7.org/fhir/tools/StructureDefinition/ig-example-actor");
                if (actor.equals(act.getUrl())) {
                  for (FetchedFile f2 : fileList) {
                    for (FetchedResource r2 : f2.getResources()) {
                      String id = r2.fhirType()+"/"+r2.getId();
                      if (tail.equals(id)) {
                        String path = igpkp.getLinkFor(r2, true);
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

  /*
   * Propagate status goes through all resources in the IG and propagates the FMM and Standards Status declarations from top-level artifacts
   * to any dependencies that don't declare their own values.  If different statuses or maturity level would propagate to a dependency from
   * different artifacts, the 'highest' FMM or most mature standards status will apply (Normative or Informative, then STU, then Draft).
   * Information does not propagate to artifacts marked as examples or as experimental.
   * Propagation is based on references.  E.g. the IG references everything (so its status will propagate everywhere by default).  If there
   * are higher statuses on certain CapabilityStatements, Operations or Profiles, those status will propagate to the artifacts they reference,
   * such as other profiles, ValueSets or CodeSystems.
   * Propagation only happens within the context of an IG.  There is no propagation across artifacts present in other IG packages listed as
   * dependencies.
   */
  private void propagateStatus() throws Exception {
    Session tts = tt.start("propagating status");
    logDebugMessage(LogCategory.PROGRESS, "propagating status");
    IntegerType igFMM = sourceIg.hasExtension(ToolingExtensions.EXT_FMM_LEVEL) ? sourceIg.getExtensionByUrl(ToolingExtensions.EXT_FMM_LEVEL).getValueIntegerType() : null;
    CodeType igStandardsStatus = sourceIg.hasExtension(ToolingExtensions.EXT_STANDARDS_STATUS) ? sourceIg.getExtensionByUrl(ToolingExtensions.EXT_STANDARDS_STATUS).getValueCodeType() : null;
    String igNormVersion = sourceIg.hasExtension(ToolingExtensions.EXT_NORMATIVE_VERSION) ? sourceIg.getExtensionByUrl(ToolingExtensions.EXT_NORMATIVE_VERSION).getValueStringType().asStringValue() : null;

    // If IG doesn't declare FMM or standards status, nothing to do
    if (igFMM == null && igStandardsStatus == null)
      return;

    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (!r.isExample())
          updateResourceStatus(r, igFMM, igStandardsStatus, igNormVersion, sourceIg.getUrl());
      }
    }

    updatePageStatus(publishedIg.getDefinition().getPage(), null, new CodeType("informative"), null);
    tts.end();
  }

  private void updatePageStatus(ImplementationGuideDefinitionPageComponent page, IntegerType parentFmm, CodeType parentStatus, String parentNormVersion) {
    IntegerType fmm = null;
    CodeType standardsStatus = page.hasExtension(ToolingExtensions.EXT_STANDARDS_STATUS) ? page.getExtensionByUrl(ToolingExtensions.EXT_STANDARDS_STATUS).getValueCodeType() : null;
    String normVersion = sourceIg.hasExtension(ToolingExtensions.EXT_NORMATIVE_VERSION) ? sourceIg.getExtensionByUrl(ToolingExtensions.EXT_NORMATIVE_VERSION).getValueStringType().asStringValue() : null;

    Extension fmmExt = page.getExtensionByUrl(ToolingExtensions.EXT_FMM_LEVEL);

    if (parentStatus != null && standardsStatus == null) {
      standardsStatus = parentStatus.copy();
      page.addExtension(new Extension(ToolingExtensions.EXT_STANDARDS_STATUS, standardsStatus));
      if (parentNormVersion != null && normVersion == null) {
        normVersion = parentNormVersion;
        page.addExtension(new Extension(ToolingExtensions.EXT_NORMATIVE_VERSION, new StringType(normVersion)));
      }
    } else {
      parentNormVersion = null;
    }

    if (standardsStatus.getValue().equals("informative")) {
      // We strip FMMs for informative artifacts
      if (fmmExt != null)
        page.getExtension().remove(fmmExt);
    } else {
      if (parentFmm != null && fmmExt == null) {
        fmm = parentFmm.copy();
        page.addExtension(new Extension(ToolingExtensions.EXT_FMM_LEVEL, fmm));
      } else if (fmmExt != null)
        fmm = fmmExt.getValueIntegerType();
    }
    for (ImplementationGuideDefinitionPageComponent childPage: page.getPage()) {
      FetchedResource res = resources.get(page.getName());
      if (res == null)
        updatePageStatus(childPage, fmm, standardsStatus, normVersion);
    }
  }

  private void updateResourceStatus(Reference ref, IntegerType parentFmm, CodeType parentStatus, String parentNormVersion, String parentCanonical) {
    String canonical = ref.getReference();
    if (canonical == null)
      return;
    if (!canonical.contains("://"))
      canonical = igpkp.getCanonical() + "/" + canonical;
    FetchedResource r = canonicalResources.get(canonical);
    if (r != null) {
      updateResourceStatus(r, parentFmm, parentStatus, parentNormVersion, parentCanonical);
    }
  }

  private void updateResourceStatus(String ref, IntegerType parentFmm, CodeType parentStatus, String parentNormVersion, String parentCanonical) {
    String canonical = ref;
    if (canonical == null)
      return;
    if (!canonical.contains("://"))
      canonical = igpkp.getCanonical() + "/" + canonical;
    FetchedResource r = canonicalResources.get(canonical);
    if (r != null) {
      updateResourceStatus(r, parentFmm, parentStatus, parentNormVersion, parentCanonical);
    }
  }

  private void updateResourceStatus(CanonicalType canonical, IntegerType parentFmm, CodeType parentStatus, String parentNormVersion, String parentCanonical) {
    FetchedResource r = canonicalResources.get(canonical.getValue());
    if (r != null) {
      updateResourceStatus(r, parentFmm, parentStatus, parentNormVersion, parentCanonical);
    }
  }

  private void updateResourceStatus(FetchedResource r, IntegerType parentFmm, CodeType parentStatus, String parentNormVersion, String parentCanonical) {
    // We only propagate status for resources that:
    // - are canonical resources
    // - aren't examples
    // - aren't experimental
    // - aren't one of these types:
    //     ChargeItemDefinition, Citation, ConditionDefinition, Evidence, EvidenceReport, EvidenceVariable, ExampleScenario, ObservationDefinition, TestScript
    boolean isInformative = false;
    if (!(r.getResource() instanceof CanonicalResource))
      return;

    CanonicalResource res = (CanonicalResource)r.getResource();

    if (r.isExample())
      isInformative = true;
    else {
      if (res.hasExperimental() && res.getExperimental())
        isInformative = true;

      switch (res.getResourceType()) {
      case ChargeItemDefinition :
      case Citation:
      case ConditionDefinition:
      case EvidenceReport:
      case EvidenceVariable:
      case ExampleScenario:
      case ObservationDefinition:
        isInformative = true;
      default:
        // We're in a resource we need to process, so continue on
      }
    }

    Extension statusExt = res.getExtensionByUrl(ToolingExtensions.EXT_STANDARDS_STATUS);
    CodeType status = statusExt!=null ? statusExt.getValueCodeType() : null;
    String statusNormVersion = res.hasExtension(ToolingExtensions.EXT_NORMATIVE_VERSION) ? res.getExtensionByUrl(ToolingExtensions.EXT_NORMATIVE_VERSION).getValueStringType().asStringValue() : null;
    if (isInformative) {
      if (status == null) {
        CodeType code = new CodeType("informative");
        code.addExtension(ToolingExtensions.EXT_FMM_DERIVED, new CanonicalType(parentCanonical));
        res.addExtension(ToolingExtensions.EXT_STANDARDS_STATUS, code);
      } else if (!Utilities.existsInList(status.getValue(), "informative", "draft", "deprecated")) {
        errors.add(new ValidationMessage(Source.Publisher, IssueType.INVALID, res.getResourceType() + " " + r.getId(), "If a resource is not implementable, is marked as experimental or example, the standards status can only be 'informative', 'draft' or 'deprecated', not '"+status.getValue()+"'.", IssueSeverity.ERROR));
      }

    } else {
      Extension fmmExt = res.getExtensionByUrl(ToolingExtensions.EXT_FMM_LEVEL);
      IntegerType fmm = fmmExt!=null ? fmmExt.getValueIntegerType() : null;

      boolean fmmChanged = false;
      if (parentFmm !=null) {
        boolean addExtension = false;
        if (fmm == null) {
          addExtension = true;

        } else if (fmm.hasExtension(ToolingExtensions.EXT_FMM_DERIVED)) {
          if (fmm.getValue() < parentFmm.getValue()) {
            res.getExtension().remove(fmmExt);
            addExtension = true;

          } else if (fmm.getValue() == parentFmm.getValue()) {
            if (fmm.getExtensionsByUrl(ToolingExtensions.EXT_FMM_DERIVED).size() < FMM_DERIVATION_MAX)
              fmm.addExtension(ToolingExtensions.EXT_FMM_DERIVED, new CanonicalType(parentCanonical));
          }
        }
        if (addExtension) {
          fmmChanged = true;
          IntegerType newFmm = parentFmm.copy();
          Extension e = new Extension(ToolingExtensions.EXT_FMM_LEVEL, newFmm);
          newFmm.addExtension(ToolingExtensions.EXT_FMM_DERIVED, new CanonicalType(parentCanonical));
          res.addExtension(e);
        }
      }

      boolean statusChanged = false;
      if (parentStatus != null) {
        boolean addExtension = false;
        if (status == null) {
          addExtension = true;

        } else if (status.hasExtension(ToolingExtensions.EXT_FMM_DERIVED)) {
          if (StandardsStatus.fromCode(parentStatus.getValue()).canDependOn(StandardsStatus.fromCode(status.getValue()))) {
            res.getExtension().remove(statusExt);
            addExtension = true;

          } else if (status.getValue() == parentStatus.getValue()) {
            if (fmm.getExtensionsByUrl(ToolingExtensions.EXT_FMM_DERIVED).size() < FMM_DERIVATION_MAX)
              fmm.addExtension(ToolingExtensions.EXT_FMM_DERIVED, new CanonicalType(parentCanonical));

          }
        }
        if (addExtension) {
          statusChanged = true;
          CodeType code = parentStatus.copy();
          Extension e = new Extension(ToolingExtensions.EXT_STANDARDS_STATUS, code);
          code.addExtension(ToolingExtensions.EXT_FMM_DERIVED, new CanonicalType(parentCanonical));
          res.addExtension(e);
          if (code.getCode().equals("normative") && !Utilities.noString(parentNormVersion)) {
            res.addExtension(new Extension(ToolingExtensions.EXT_NORMATIVE_VERSION, new CodeType(parentNormVersion)));
            statusNormVersion = parentNormVersion;
          }
        } else {
          parentNormVersion = null;
        }
      }

      // If we've changed things, need to propagate to children
      if (fmmChanged || statusChanged) {
        for (Extension e : getDescendantExtensions(res, "http://hl7.org/fhir/StructureDefinition/cqf-library")) {
          updateResourceStatus((CanonicalType)e.getValue(), fmm, status, statusNormVersion, res.getUrl());
        }
        switch (res.getResourceType()) {
        case ActivityDefinition:
          ActivityDefinition ad = (ActivityDefinition)res;
          for (CanonicalType canonical : ad.getLibrary()) {
            updateResourceStatus(canonical, fmm, status, statusNormVersion, res.getUrl());
          }
          if (ad.hasProfile())
            updateResourceStatus(ad.getProfileElement(), fmm, status, statusNormVersion, res.getUrl());
          for (CanonicalType ref : ad.getObservationRequirement()) {
            updateResourceStatus(ref, fmm, status, statusNormVersion, res.getUrl());
          }
          for (CanonicalType ref : ad.getObservationResultRequirement()) {
            updateResourceStatus(ref, fmm, status, statusNormVersion, res.getUrl());
          }
          if (ad.hasTransform())
            updateResourceStatus(ad.getTransformElement(), fmm, status, statusNormVersion, res.getUrl());
          break;

        case CapabilityStatement:
          CapabilityStatement cs = (CapabilityStatement)res;
          for (CapabilityStatementRestComponent rest: cs.getRest()) {
            for (CapabilityStatementRestResourceComponent resource: rest.getResource()) {
              if (resource.hasProfile())
                updateResourceStatus(resource.getProfileElement(), fmm, status, statusNormVersion, res.getUrl());
              for (CapabilityStatementRestResourceSearchParamComponent sp: resource.getSearchParam()) {
                if (sp.hasDefinition())
                  updateResourceStatus(sp.getDefinitionElement(), fmm, status, statusNormVersion, res.getUrl());
              }
              for (CapabilityStatementRestResourceOperationComponent op: resource.getOperation()) {
                if (op.hasDefinition())
                  updateResourceStatus(op.getDefinitionElement(), fmm, status, statusNormVersion, res.getUrl());
              }
            }
            for (CapabilityStatementRestResourceSearchParamComponent sp: rest.getSearchParam()) {
              if (sp.hasDefinition())
                updateResourceStatus(sp.getDefinitionElement(), fmm, status, statusNormVersion, res.getUrl());
            }
            for (CapabilityStatementRestResourceOperationComponent op: rest.getOperation()) {
              if (op.hasDefinition())
                updateResourceStatus(op.getDefinitionElement(), fmm, status, statusNormVersion, res.getUrl());
            }
            for (CapabilityStatementMessagingComponent messaging: cs.getMessaging()) {
              for (CapabilityStatementMessagingSupportedMessageComponent msg: messaging.getSupportedMessage()) {
                if (msg.hasDefinition())
                  updateResourceStatus(msg.getDefinitionElement(), fmm, status, statusNormVersion, res.getUrl());
              }
            }
            for (CapabilityStatementDocumentComponent doc: cs.getDocument()) {
              updateResourceStatus(doc.getProfileElement(), fmm, status, statusNormVersion, res.getUrl());
            }
          }
          break;

        case ConceptMap:
          ConceptMap cm = (ConceptMap)res;
          for (ConceptMapGroupComponent group: cm.getGroup()) {
            if (group.hasUnmapped() && group.getUnmapped().hasValueSet()) {
              updateResourceStatus(group.getUnmapped().getValueSetElement(), fmm, status, statusNormVersion, res.getUrl());
            }
          }
          break;

        case GraphDefinition:
          GraphDefinition gd = (GraphDefinition)res;
          //            if (gd.hasProfile())
          //              updateResourceStatus(gd.getProfileElement(), fmm, status, statusNormVersion, res.getUrl());
          //            for (GraphDefinitionLinkComponent link: gd.getLink()) {
          //              for (GraphDefinitionLinkTargetComponent target: link.getTarget()) {
          //                if (gd.hasProfile())
          //                  updateResourceStatus(target.getProfileElement(), fmm, status, statusNormVersion, res.getUrl());
          //              }
          //            }
          break;

        case ImplementationGuide:
          ImplementationGuide ig = (ImplementationGuide)res;
          for (ImplementationGuide.ImplementationGuideGlobalComponent global: ig.getGlobal()) {
            updateResourceStatus((CanonicalType)global.getProfileElement(), fmm, status, statusNormVersion, res.getUrl());
          }
          if (ig.hasDefinition()) {
            for (ImplementationGuide.ImplementationGuideDefinitionResourceComponent resource: ig.getDefinition().getResource()) {
              updateResourceStatus(resource.getReference(), fmm, status, statusNormVersion, res.getUrl());
            }
          }
          break;

        case Measure:
          Measure m = (Measure)res;
          for (CanonicalType library: m.getLibrary()) {
            updateResourceStatus(library, fmm, status, statusNormVersion, res.getUrl());
          }
          break;

        case MessageDefinition:
          MessageDefinition md = (MessageDefinition)res;
          if (md.hasBase())
            updateResourceStatus(md.getBaseElement(), fmm, status, statusNormVersion, res.getUrl());
          for (CanonicalType parent: md.getParent()) {
            updateResourceStatus(parent, fmm, status, statusNormVersion, res.getUrl());
          }
          for (MessageDefinitionFocusComponent focus : md.getFocus()) {
            if (focus.hasProfile())
              updateResourceStatus(focus.getProfileElement(), fmm, status, statusNormVersion, res.getUrl());
          }
          for (MessageDefinitionAllowedResponseComponent response : md.getAllowedResponse()) {
            if (response.hasMessage())
              updateResourceStatus(response.getMessageElement(), fmm, status, statusNormVersion, res.getUrl());
          }
          updateResourceStatus(md.getGraph(), fmm, status, statusNormVersion, res.getUrl());
          break;

        case OperationDefinition:
          OperationDefinition od = (OperationDefinition)res;
          if (od.hasBase())
            updateResourceStatus(od.getBaseElement(), fmm, status, statusNormVersion, res.getUrl());
          if (od.hasInputProfile())
            updateResourceStatus(od.getInputProfileElement(), fmm, status, statusNormVersion, res.getUrl());
          if (od.hasOutputProfile())
            updateResourceStatus(od.getOutputProfileElement(), fmm, status, statusNormVersion, res.getUrl());
          for (OperationDefinitionParameterComponent param : od.getParameter()) {
            for (CanonicalType profile: param.getTargetProfile()) {
              updateResourceStatus(profile, fmm, status, statusNormVersion, res.getUrl());
            }
            if (param.hasBinding() && param.getBinding().hasValueSet()) {
              updateResourceStatus(param.getBinding().getValueSetElement(), fmm, status, statusNormVersion, res.getUrl());
            }
          }
          break;

        case PlanDefinition:
          PlanDefinition pd = (PlanDefinition)res;
          for (CanonicalType library: pd.getLibrary()) {
            updateResourceStatus(library, fmm, status, statusNormVersion, res.getUrl());
          }
          for (PlanDefinitionActionComponent action: pd.getAction()) {
            if (action.hasDefinitionCanonicalType())
              updateResourceStatus(action.getDefinitionCanonicalType(), fmm, status, statusNormVersion, res.getUrl());
          }
          break;

        case Questionnaire:
          Questionnaire q = (Questionnaire)res;
          for (CanonicalType derived: q.getDerivedFrom()) {
            updateResourceStatus(derived, fmm, status, statusNormVersion, res.getUrl());
          }
          for (QuestionnaireItemComponent item: q.getItem()) {
            if (item.hasAnswerValueSet())
              updateResourceStatus(item.getAnswerValueSetElement(), fmm, status, statusNormVersion, res.getUrl());
            for (Extension ext: item.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/questionnaire-referenceProfile")) {
              updateResourceStatus(ext.getValueCanonicalType(), fmm, status, statusNormVersion, res.getUrl());
            }
            for (Extension ext: item.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/questionnaire-unitValueSet")) {
              updateResourceStatus(ext.getValueCanonicalType(), fmm, status, statusNormVersion, res.getUrl());
            }
          }
          break;

        case SearchParameter:
          SearchParameter sp = (SearchParameter)res;
          if (sp.hasDerivedFrom())
            updateResourceStatus(sp.getDerivedFromElement(), fmm, status, statusNormVersion, res.getUrl());
          for (SearchParameterComponentComponent comp: sp.getComponent()) {
            if (comp.hasDefinition())
              updateResourceStatus(comp.getDefinitionElement(), fmm, status, statusNormVersion, res.getUrl());
          }
          break;

        case StructureDefinition:
          StructureDefinition sd = (StructureDefinition)res;
          if (sd.hasBaseDefinition())
            updateResourceStatus(sd.getBaseDefinitionElement(), fmm, status, statusNormVersion, res.getUrl());
          for (ElementDefinition e: sd.getDifferential().getElement()) {
            if (e.hasBinding() && e.getBinding().hasValueSet())
              updateResourceStatus(e.getBinding().getValueSetElement(), fmm, status, statusNormVersion, res.getUrl());
          }
          break;

        case StructureMap:
          StructureMap sm = (StructureMap)res;
          for (CanonicalType imp: sm.getImport()) {
            updateResourceStatus(imp, fmm, status, statusNormVersion, res.getUrl());
          }
          break;

          /* TODO: Add this once SubscriptionTopic is a canonical
          case SubscriptionTopic:
            SubscriptionTopic st = (SubscriptionTopic)res;
            for (CanonicalType canonical: st.getDerivedFrom()) {
              updateResourceStatus(canonical, fmm, status, statusNormVersion, res.getUrl());
            }
            for (CanonicalType canonical: st.getDerivedFrom()) {
              for (SubscriptionTopicResourceTriggerComponent trigger: st.getResourceTrigger()) {
                for (SubscriptionTopicResourceTriggerCanFilterByComponent filter: trigger.getCanFilterBy()) {
                  updateResourceStatus(filter.getSearchParamName(), fmm, status, statusNormVersion, res.getUrl());
                }
              }
            }
            break;*/

        case ValueSet:
          ValueSet vs = (ValueSet)res;
          for (Extension ext: vs.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/valueset-map")) {
            updateResourceStatus(ext.getValueCanonicalType(), fmm, status, statusNormVersion, res.getUrl());
          }
          for (Extension ext: vs.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/valueset-supplement")) {
            updateResourceStatus(ext.getValueCanonicalType(), fmm, status, statusNormVersion, res.getUrl());
          }
          if (vs.hasCompose()) {
            for (ConceptSetComponent compose: vs.getCompose().getInclude()) {
              if (compose.hasSystem())
                updateResourceStatus(new CanonicalType(compose.getSystem()), fmm, status, statusNormVersion, res.getUrl());
              for (CanonicalType valueSet: compose.getValueSet()) {
                updateResourceStatus(valueSet, fmm, status, statusNormVersion, res.getUrl());
              }
            }
            for (ConceptSetComponent compose: vs.getCompose().getExclude()) {
              if (compose.hasSystem())
                updateResourceStatus(new CanonicalType(compose.getSystem()), fmm, status, statusNormVersion, res.getUrl());
              for (CanonicalType valueSet: compose.getValueSet()) {
                updateResourceStatus(valueSet, fmm, status, statusNormVersion, res.getUrl());
              }
            }
          }
          break;

          // The following types don't actually have anything to cascade to - at least not yet
        case CodeSystem:
        case EventDefinition:
        case Library:
        case NamingSystem:
        case TerminologyCapabilities:
        default:

        }
      }
    }
  }

  public List<Extension> getDescendantExtensions(Base e, String url) {
    List<Extension> extensions = new ArrayList<Extension>();
    for (Property childName: e.children()) {
      String name = childName.getName().endsWith("[x]") ? childName.getName().substring(0, childName.getName().length()-3) : childName.getName();
      for (Base b: e.listChildrenByName(name)) {
        if (b instanceof org.hl7.fhir.r5.model.Element) {
          org.hl7.fhir.r5.model.Element ce = (org.hl7.fhir.r5.model.Element)b;
          extensions.addAll(ce.getExtensionsByUrl(url));
          getDescendantExtensions(ce, url);
        }
      }
    }
    return extensions;
  }

  private void generateNarratives(boolean isRegen) throws Exception {
    Session tts = tt.start("narrative generation");
    logDebugMessage(LogCategory.PROGRESS, isRegen ? "regen narratives" : "gen narratives");
    for (FetchedFile f : fileList) {
      f.start("generateNarratives");
      try {
        for (FetchedResource r : f.getResources()) {
          if (!isRegen || r.isRegenAfterValidation()) {
            if (r.getExampleUri()==null || genExampleNarratives) {
              if (!passesNarrativeFilter(r)) {
                noNarrativeResources.add(r);
                logDebugMessage(LogCategory.PROGRESS, "narrative for "+f.getName()+" : "+r.getId()+" suppressed");
                if (r.getResource() != null && r.getResource() instanceof DomainResource) {
                  ((DomainResource) r.getResource()).setText(null);
                }
                r.getElement().removeChild("text");
              } else {
                List<Locale> langs = translationLocales();
                logDebugMessage(LogCategory.PROGRESS, "narrative for "+f.getName()+" : "+r.getId());
                if (r.getResource() != null && isConvertableResource(r.getResource().fhirType())) {
                  boolean regen = false;
                  boolean first = true;
                  for (Locale lang : langs) {
                    RenderingContext lrc = rc.copy(false).setDefinitionsTarget(igpkp.getDefinitionsName(r));
                    lrc.setLocale(lang);
                    lrc.setRules(GenerationRules.VALID_RESOURCE);
                    lrc.setDefinitionsTarget(igpkp.getDefinitionsName(r));
                    lrc.setSecondaryLang(!first);
                    if (!first) {
                      lrc.setUniqueLocalPrefix(lang.toLanguageTag());
                    }
                    first = false;
                    if (r.getResource() instanceof DomainResource && (langs.size() > 1 || !(((DomainResource) r.getResource()).hasText() && ((DomainResource) r.getResource()).getText().hasDiv()))) {
                      regen = true;
                      ResourceRenderer rr = RendererFactory.factory(r.getResource(), lrc);
                      if (rr.renderingUsesValidation()) {
                        r.setRegenAfterValidation(true);
                        needsRegen = true;
                      }
                      rr.setMultiLangMode(langs.size() > 1).renderResource(ResourceWrapper.forResource(lrc, r.getResource()));
                    } else if (r.getResource() instanceof Bundle) {
                      regen = true;
                      new BundleRenderer(lrc).setMultiLangMode(langs.size() > 1).renderResource(ResourceWrapper.forResource(lrc, r.getResource()));
                    } else if (r.getResource() instanceof Parameters) {
                      regen = true;
                      Parameters p = (Parameters) r.getResource();
                      new ParametersRenderer(lrc).setMultiLangMode(langs.size() > 1).renderResource(ResourceWrapper.forResource(lrc, p));
                    } else if (r.getResource() instanceof DomainResource) {
                      checkExistingNarrative(f, r, ((DomainResource) r.getResource()).getText().getDiv());
                    }
                  }
                  if (regen) {
                    Element e = convertToElement(r, r.getResource());
                    e.copyUserData(r.getElement());
                    r.setElement(e);
                  }
                } else {
                  boolean first = true;
                  for (Locale lang : langs) {
                    RenderingContext lrc = rc.copy(false).setParser(getTypeLoader(f,r));
                    lrc.clearAnchors();
                    lrc.setLocale(lang);
                    lrc.setRules(GenerationRules.VALID_RESOURCE);
                    lrc.setSecondaryLang(!first);
                    if (!first) {
                      lrc.setUniqueLocalPrefix(lang.toLanguageTag());
                    }
                    first = false;
                    if (isDomainResource(r) && (isRegen || langs.size() > 1 || !hasNarrative(r.getElement()))) {
                      ResourceWrapper rw = ResourceWrapper.forResource(lrc, r.getElement());
                      ResourceRenderer rr = RendererFactory.factory(rw, lrc);
                      if (rr.renderingUsesValidation()) {
                        r.setRegenAfterValidation(true);
                        needsRegen = true;
                      }
                      rr.setMultiLangMode(langs.size() > 1).renderResource(rw);
                      otherFilesRun.addAll(lrc.getFiles());
                    } else if (r.fhirType().equals("Bundle")) {
                      lrc.setAddName(true);
                      for (Element e : r.getElement().getChildrenByName("entry")) {
                        Element res = e.getNamedChild("resource");
                        if (res!=null && "http://hl7.org/fhir/StructureDefinition/DomainResource".equals(res.getProperty().getStructure().getBaseDefinition())) {
                          ResourceWrapper rw = ResourceWrapper.forResource(lrc, res);
                          ResourceRenderer rr = RendererFactory.factory(rw, lrc);
                          if (rr.renderingUsesValidation()) {
                            r.setRegenAfterValidation(true);
                            needsRegen = true;
                          }
                          if (hasNarrative(res)) {
                            rr.checkNarrative(rw);                        
                          } else {
                            rr.setMultiLangMode(langs.size() > 1).renderResource(rw);
                          }
                        }
                      }
                    } else if (isDomainResource(r) && hasNarrative(r.getElement())) {
                      checkExistingNarrative(f, r, r.getElement().getNamedChild("text").getNamedChild("div").getXhtml());
                    }
                  }
                }
              }
            } else {
              logDebugMessage(LogCategory.PROGRESS, "skipped narrative for "+f.getName()+" : "+r.getId());
            }
          }
        }
      } finally {
        f.finish("generateNarratives");
      }
    }
    tts.end();
  }

  private Locale inferDefaultNarrativeLang() {
    return inferDefaultNarrativeLang(false);
  }

  private Locale inferDefaultNarrativeLang(final boolean logDecision) {
    if (logDecision) {
      logDebugMessage(LogCategory.INIT, "-force-language="+forcedLanguage
              + " defaultTranslationLang="+defaultTranslationLang
            + (sourceIg == null ? "" : " sourceIg.language="+sourceIg.getLanguage()
              + " sourceIg.jurisdiction="+sourceIg.getJurisdictionFirstRep().getCodingFirstRep().getCode())
      );
    }
    if (forcedLanguage != null) {
      if (logDecision) {
        logMessage("Using " + forcedLanguage + " as the default narrative language. (-force-language has been set)");
      }
      return forcedLanguage;
    }
    if (defaultTranslationLang != null) {
      if (logDecision) {
        logMessage("Using " + defaultTranslationLang + " as the default narrative language. (Implementation Guide param i18n-default-lang)");
      }
      return Locale.forLanguageTag(defaultTranslationLang);
    }
    if (sourceIg != null) {
      if (sourceIg.hasLanguage()) {
        if (logDecision) {
          logMessage("Using " + sourceIg.getLanguage() + " as the default narrative language. (ImplementationGuide.language has been set)");
        }
        return Locale.forLanguageTag(sourceIg.getLanguage());
      }
      if (sourceIg.hasJurisdiction()) {
        final String jurisdiction = sourceIg.getJurisdictionFirstRep().getCodingFirstRep().getCode();
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

  /**
   * Return a list of locales containing the translation languages for the IG, as well as the inferred default language
   * of the IG.
   *
   * @return translation locales
   */
  private List<Locale> translationLocales() {
    List<Locale> res = new ArrayList<>();
    res.add(inferDefaultNarrativeLang());

    for (String translationLang : translationLangs) {
      Locale locale = Locale.forLanguageTag(translationLang);
      if (!res.contains(locale)) {
        res.add(locale);
      }
    }
    return res;
  }

  private boolean isDomainResource(FetchedResource r) {
    StructureDefinition sd = r.getElement().getProperty().getStructure();
    while (sd != null) {
      if ("DomainResource".equals(sd.getType())) {
        return true;
      }
      sd = context.fetchResource(StructureDefinition.class, sd.getBaseDefinition());
    }
    return false;
  }

  private boolean passesNarrativeFilter(FetchedResource r) {
    for (String s : noNarratives) {
      String[] p = s.split("\\/");
      if (p.length == 2) {
        if (("*".equals(p[0]) || r.fhirType().equals(p[0])) &&
            ("*".equals(p[1]) || r.getId().equals(p[1]))) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean passesValidationFilter(FetchedResource r) {
    for (String s : noValidate) {
      String[] p = s.split("\\/");
      if (p.length == 2) {
        if (("*".equals(p[0]) || r.fhirType().equals(p[0])) &&
            ("*".equals(p[1]) || r.getId().equals(p[1]))) {
          return false;
        }
      }
    }
    return true;
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
    return Utilities.existsInList(t, "StructureDefinition", "ValueSet", "CodeSystem", "Conformance", "CapabilityStatement", "Questionnaire", "NamingSystem", "SearchParameter",
        "ConceptMap", "OperationOutcome", "CompartmentDefinition", "OperationDefinition", "ImplementationGuide", "ActorDefinition", "Requirements", "StructureMap", "SubscriptionTopic");
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
    } else if (VersionUtilities.isR4BVer(ver)) {
      return new TypeParserR4B();
    } else if (VersionUtilities.isR5Plus(ver)) {
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
        FileUtilities.createDirectory(destDir);
      FileUtilities.copyDirectory(outputDir, destDir, null);
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
    JsonPrimitive p = obj.get(name).asJsonPrimitive();
    if (p instanceof JsonBoolean)
      return ((JsonBoolean) p).isValue();
    return false;
  }

  private String str(JsonObject obj, String name) throws Exception {
    if (!obj.has(name))
      throw new Exception("Property '"+name+"' not found");
    if (!(obj.get(name) instanceof JsonPrimitive))
      throw new Exception("Property '"+name+"' not a primitive");
    JsonPrimitive p = obj.get(name).asJsonPrimitive();
    return p.asString();
  }

  private String ostr(JsonObject obj, String name) throws Exception {
    if (obj == null)
      return null;
    if (!obj.has(name))
      return null;
    if (!(obj.get(name).isJsonPrimitive()))
      return null;
    JsonPrimitive p = obj.get(name).asJsonPrimitive();
    return p.asString();
  }

  private String str(JsonObject obj, String name, String defValue) throws Exception {
    if (obj == null || !obj.has(name))
      return defValue;
    if (!(obj.get(name) instanceof JsonPrimitive))
      throw new Exception("Property "+name+" not a primitive");
    JsonPrimitive p = obj.get(name).asJsonPrimitive();
    return p.asString();
  }

  public void initialize() throws Exception {
    pcm = getFilesystemPackageCacheManager();
    log("Build FHIR IG from "+configFile);
    if (mode == IGBuildMode.PUBLICATION)
      log("Build Formal Publication package, intended for "+getTargetOutput());

    log("API keys loaded from "+ FhirSettings.getFilePath());

    templateManager = new TemplateManager(pcm, logger);
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
      if (fsh.exists() && fsh.isDirectory() && !noSushi) {
        prescanSushiConfig(focusDir());
        new FSHRunner(this).runFsh(new File(FileUtilities.getDirectoryForFile(fsh.getAbsolutePath())), mode);
        isSushi = true;
      } else {
        File fsh2 = new File(Utilities.path(focusDir(), "input", "fsh"));
        if (fsh2.exists() && fsh2.isDirectory() && !noSushi) {
          prescanSushiConfig(focusDir());
          new FSHRunner(this).runFsh(new File(FileUtilities.getDirectoryForFile(fsh.getAbsolutePath())), mode);
          isSushi = true;
        }
      }
    }
    IniFile ini = checkNewIg();
    if (ini != null) {
      newIg = true;
      initializeFromIg(ini);
    } else if (isTemplate())
      initializeTemplate();
    else {
      // initializeFromJson();
      throw new Error("Old style JSON configuration is no longer supported. If you see this, then ig.ini wasn't found in '"+rootDir+"'");
    }
    expectedJurisdiction = checkForJurisdiction();
    
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

  private void installPackage(String id, String ver) {
    try {
      if (!pcm.packageInstalled(id, ver)) {
        log("Found dependency on "+id+"#"+ver+" in Sushi config. Pre-installing");
        pcm.loadPackage(id, ver);
      }
    } catch (FHIRException | IOException e) {
      log("Unable to install "+id+"#"+ver+": "+e.getMessage());
      log("Trying to go on");
    }
    
  }

  @Nonnull
  private FilesystemPackageCacheManager getFilesystemPackageCacheManager() throws IOException {
    if (getPackageCacheFolder() != null) {
      return new FilesystemPackageCacheManager.Builder().withCacheFolder(getPackageCacheFolder()).build();
    }
    return mode == null || mode == IGBuildMode.MANUAL || mode == IGBuildMode.PUBLICATION ?
            new FilesystemPackageCacheManager.Builder().build()
            : new FilesystemPackageCacheManager.Builder().withSystemCacheFolder().build();

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
    if (Utilities.existsInList(parts[1], "fhir") && Utilities.existsInList(parts[2], "test")) {
      return null;
    }
    if (Utilities.existsInList(parts[1], "fhir") && !Utilities.existsInList(parts[1], "nothing-yet")) {
      if (parts[2].equals("uv")) {
        igrealm = "uv";
        return new Coding("http://unstats.un.org/unsd/methods/m49/m49.htm", "001", "World");
      } else if (parts[2].equals("eu")) {
        igrealm = "eu";
        return new Coding("http://unstats.un.org/unsd/methods/m49/m49.htm", "150", "Europe");
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


  private void initializeTemplate() throws IOException {
    rootDir = configFile;
    outputDir = Utilities.path(rootDir, "output");
    tempDir = Utilities.path(rootDir, "temp");
  }


  private boolean isTemplate() throws IOException {
    File pack = new File(Utilities.path(configFile, "package", "package.json"));
    if (pack.exists()) {
      JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(pack);
      if (json.has("type") && "fhir.template".equals(json.asString("type"))) {
        isBuildingTemplate = true;
        templateInfo = json;
        npmName = json.asString("name");
        //        System.out.println("targetOutput: "+targetOutput);
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
    String s = FileUtilities.fileToString(cf);
    if (s.startsWith("[IG]"))
      return new IniFile(cf.getAbsolutePath());
    else
      return null;
  }

  private void initializeFromIg(IniFile ini) throws Exception {
    configFile = ini.getFileName();
    igMode = true;
    repoRoot = FileUtilities.getDirectoryForFile(ini.getFileName());
    rootDir = repoRoot;
    fetcher.setRootDir(rootDir);
    killFile = new File(Utilities.path(rootDir, "ig-publisher.kill"));    
    // ok, first we load the template
    String templateName = ini.getStringProperty("IG", "template");
    if (templateName == null)
      throw new Exception("You must nominate a template - consult the IG Publisher documentation");
    module = loadModule(ini.getStringProperty("IG", "module"));
    if (module.useRoutine("preProcess")) {
      log("== Ask "+module.name()+" to pre-process the IG ============================");
      if (!module.preProcess(rootDir)) {
        throw new Exception("Process terminating due to Module failure");
      } else {
        log("== Done ====================================================================");
      }
    }
    igName = Utilities.path(repoRoot, ini.getStringProperty("IG", "ig"));
    try {
      try {
        sourceIg = (ImplementationGuide) org.hl7.fhir.r5.formats.FormatUtilities.loadFileTight(igName);
        boolean isR5 = false;
        for (Enumeration<FHIRVersion> v : sourceIg.getFhirVersion()) {
          isR5 = isR5 || VersionUtilities.isR5VerOrLater(v.getCode());          
        }
        if (!isR5) {
          sourceIg = (ImplementationGuide) VersionConvertorFactory_40_50.convertResource(FormatUtilities.loadFile(igName));
        }
      } catch (Exception e) {
        log("Unable to load IG as an r5 IG - try R4 ("+e.getMessage()+")");
        sourceIg = (ImplementationGuide) VersionConvertorFactory_40_50.convertResource(FormatUtilities.loadFile(igName));
      }
    } catch (Exception e) {
      throw new Exception("Error Parsing File "+igName+": "+e.getMessage(), e);
    }
    template = templateManager.loadTemplate(templateName, rootDir, sourceIg.getPackageId(), mode == IGBuildMode.AUTOBUILD, logOptions.contains("template"));
    if (template.hasExtraTemplates()) {
      processExtraTemplates(template.getExtraTemplates());
    }

    if (template.hasPreProcess()) {
      for (JsonElement e : template.getPreProcess()) {
        handlePreProcess((JsonObject)e, rootDir);
      }
    }
    branchName = ini.getStringProperty("dev", "branch");

    Map<String, List<ValidationMessage>> messages = new HashMap<String, List<ValidationMessage>>();
    sourceIg = template.onLoadEvent(sourceIg, messages);
    checkOutcomes(messages);
    // ok, loaded. Now we start loading settings out of the IG
    version = processVersion(sourceIg.getFhirVersion().get(0).asStringValue()); // todo: support multiple versions
    if (VersionUtilities.isR2Ver(version) || VersionUtilities.isR2Ver(version)) {
      throw new Error("As of the end of 2024, the FHIR  R2 (version "+version+") is no longer supported by the IG Publisher");
    }
    if (!Utilities.existsInList(version, "5.0.0", "4.3.0", "4.0.1", "3.0.2", "6.0.0-ballot3")) {
      throw new Error("Unable to support version '"+version+"' - must be one of 5.0.0, 4.3.0, 4.0.1, 3.0.2 or 6.0.0-ballot3");
    }

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

    copyrightYear = null;
    Boolean useStatsOptOut = null;
    List<String> extensionDomains = new ArrayList<>();
    testDataFactories = new ArrayList<>();
    tempDir = Utilities.path(rootDir, "temp");
    tempLangDir = Utilities.path(rootDir, "translations");
    outputDir = Utilities.path(rootDir, "output");
    List<String> relatedIGParams = new ArrayList<>();
    R5BundleRelativeReferencePolicy r5BundleRelativeReferencePolicy = R5BundleRelativeReferencePolicy.DEFAULT;
    
    Map<String, String> expParamMap = new HashMap<>();
    boolean allowExtensibleWarnings = false;
    boolean noCIBuildIssues = false;
    List<String> conversionVersions = new ArrayList<>();
    List<String> liquid0 = new ArrayList<>();
    List<String> liquid1 = new ArrayList<>();
    List<String> liquid2 = new ArrayList<>();
    int count = 0;
    for (ImplementationGuideDefinitionParameterComponent p : sourceIg.getDefinition().getParameter()) {
      // documentation for this list: https://confluence.hl7.org/display/FHIR/Implementation+Guide+Parameters
      String pc = p.getCode().getCode();
      if (pc == null) {
        throw new Error("The IG Parameter has no code");
      } else switch (pc) {
      case "logging":
        logOptions.add(p.getValue());
        break;
      case "generate":
        if ("example-narratives".equals(p.getValue()))
          genExampleNarratives = true;
        if ("examples".equals(p.getValue()))
          genExamples = true;
        break;
      case "no-narrative":
        String s = p.getValue();
        if (!s.contains("/")) {
          throw new Exception("Illegal value "+s+" for no-narrative: should be resource/id (see documentation at https://build.fhir.org/ig/FHIR/fhir-tools-ig/CodeSystem-ig-parameters.html)");
        }
        noNarratives.add(s);
        break;
      case "no-validate":
        noValidate.add(p.getValue());
        break;
      case "path-resource":
        String dir = getPathResourceDirectory(p);
        if (!resourceDirs.contains(dir)) {
          resourceDirs.add(dir);
        }
        break;
      case "path-factory":
        dir = getPathResourceDirectory(p);
        if (!resourceFactoryDirs.contains(dir)) {
          resourceFactoryDirs.add(dir);
        }
        break;
      case "autoload-resources":     
        autoLoad = "true".equals(p.getValue());
        break;
      case "codesystem-property":     
        codeSystemProps.add(p.getValue());
        break;
      case "path-pages":     
        pagesDirs.add(Utilities.path(rootDir, p.getValue()));
        break;
      case "path-test":     
        testDirs.add(Utilities.path(rootDir, p.getValue()));
        break;
      case "path-data":     
        dataDirs.add(Utilities.path(rootDir, p.getValue()));
        break;
      case "path-other":     
        otherDirs.add(Utilities.path(rootDir, p.getValue()));
        break;
      case "copyrightyear":     
        copyrightYear = p.getValue();
        break;
      case "path-qa":     
        qaDir = Utilities.path(rootDir, p.getValue());
        break;
      case "path-tx-cache":     
        vsCache =  Paths.get(p.getValue()).isAbsolute() ? p.getValue() : Utilities.path(rootDir, p.getValue());
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
        tempDir = Utilities.path(rootDir, p.getValue());
        if (!tempDir.startsWith(rootDir))
          throw new Exception("Temp directory must be a sub-folder of the base directory");
        break;
      case "path-output":
        if (mode != IGBuildMode.WEBSERVER) {
          // Can't override outputDir if building using webserver
          outputDir = Utilities.path(rootDir, p.getValue());
          if (!outputDir.startsWith(rootDir))
            throw new Exception("Output directory must be a sub-folder of the base directory");
        }
        break;
      case "path-history":     
        historyPage = p.getValue();
        break;
      case "path-expansion-params":   
        expParams = p.getValue();
        break;
      case "path-suppressed-warnings":  
        loadSuppressedMessages(Utilities.path(rootDir, p.getValue()), "ImplementationGuide.definition.parameter["+count+"].value");
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
        bundleReferencesResolve = "true".equals(p.getValue());        
        break;
      case "active-tables":
        HierarchicalTableGenerator.ACTIVE_TABLES = "true".equals(p.getValue());
        break;
      case "propagate-status":     
        isPropagateStatus = p.getValue().equals("true");
        break;
      case "ig-expansion-parameters":     
        expParamMap.put(pc, p.getValue());
        break;
      case "special-url":    
        listedURLExemptions.add(p.getValue());
        break;
      case "special-url-base":
        altCanonical = p.getValue();
        break;
      case "no-usage-check":
        noUsageCheck = "true".equals(p.getValue());
        break;
      case "template-openapi": 
        openApiTemplate = p.getValue();
        break;
      case "template-html":
        htmlTemplate = p.getValue();
        break;
      case "format-date": 
        fmtDate = p.getValue();
        break;
      case "format-datetime": 
        fmtDateTime = p.getValue();
        break;
      case "template-md":
        mdTemplate = p.getValue();
        break;
      case "path-binary":     
        binaryPaths.add(Utilities.path(rootDir, p.getValue()));
        break;
      case "show-inherited-invariants":
        allInvariants = "true".equals(p.getValue());
        break;
      case "apply-contact":
        if (p.getValue().equals("true")) {
          contacts = sourceIg.getContact();
        }
        break;
      case "apply-context":
        if (p.getValue().equals("true")) {
          contexts = sourceIg.getUseContext();
        }
        break;
      case "apply-copyright":
        if (p.getValue().equals("true")) {
          copyright = sourceIg.getCopyrightElement();
        }
        break;
      case "apply-jurisdiction":
        if (p.getValue().equals("true")) {
          jurisdictions = sourceIg.getJurisdiction();
        }
        break;
      case "apply-license":
        if (p.getValue().equals("true")) {
          licenseInfo = sourceIg.getLicenseElement();
        }
        break;
      case "apply-publisher":
        if (p.getValue().equals("true")) {
          publisher = sourceIg.getPublisherElement();
        }
        break;
      case "apply-version":
        if (p.getValue().equals("true")) {
          businessVersion = sourceIg.getVersion();
        }
        break;
      case "apply-wg":
        if (p.getValue().equals("true")) {
          wgm = ToolingExtensions.readStringExtension(sourceIg, ToolingExtensions.EXT_WORKGROUP);
        }
        break;
      case "default-contact":
        if (p.getValue().equals("true")) {
          defaultContacts = sourceIg.getContact();
        }
        break;
      case "default-context":
        if (p.getValue().equals("true")) {
          defaultContexts = sourceIg.getUseContext();
        }
        break;
      case "default-copyright":
        if (p.getValue().equals("true")) {
          defaultCopyright = sourceIg.getCopyrightElement();
        }
        break;
      case "default-jurisdiction":
        if (p.getValue().equals("true")) {
          defaultJurisdictions = sourceIg.getJurisdiction();
        }
        break;
      case "default-license":
        if (p.getValue().equals("true")) {
          defaultLicenseInfo = sourceIg.getLicenseElement();
        }
        break;
      case "default-publisher":
        if (p.getValue().equals("true")) {
          defaultPublisher = sourceIg.getPublisherElement();
        }
        break;
      case "default-version":
        if (p.getValue().equals("true")) {
          defaultBusinessVersion = sourceIg.getVersion();
        }
        break;
      case "default-wg":
        if (p.getValue().equals("true")) {
          defaultWgm = ToolingExtensions.readStringExtension(sourceIg, ToolingExtensions.EXT_WORKGROUP);
        }
        break;
      case "log-loaded-resources":
        if (p.getValue().equals("true")) {
          logLoading = true;
        }
      case "generate-version":   
        generateVersions.add(p.getValue());
        break;
      case "conversion-version": 
        conversionVersions.add(p.getValue());
        break;
      case "custom-resource": 
        customResourceFiles.add(p.getValue());
        break;
      case "related-ig":   
        relatedIGParams.add(p.getValue());
        break;
      case "suppressed-ids":
        for (String s1 : p.getValue().split("\\,"))
          suppressedIds.add(s1);
        break;
      case "allow-extensible-warnings":
        allowExtensibleWarnings = p.getValue().equals("true");
        break;
      case "version-comparison":    
        if (comparisonVersions == null) {
          comparisonVersions = new ArrayList<>();
        }
        if (!"n/a".equals(p.getValue()) && !comparisonVersions.contains(p.getValue())) {
          comparisonVersions.add(p.getValue());
        }        
        break;
      case "version-comparison-master":
        versionToAnnotate = p.getValue();
        if (comparisonVersions == null) {
          comparisonVersions = new ArrayList<>();
        }
        if (!"n/a".equals(p.getValue()) && !comparisonVersions.contains(p.getValue())) {
          comparisonVersions.add(p.getValue());
        }        
        break;
      case "ipa-comparison":   
        if (ipaComparisons == null) {
          ipaComparisons = new ArrayList<>();
        }
        if (!"n/a".equals(p.getValue())) {
          ipaComparisons.add(p.getValue());
        }        
        break;
      case "ips-comparison":   
        if (ipsComparisons == null) {
          ipsComparisons = new ArrayList<>();
        }
        if (!"n/a".equals(p.getValue())) {
          ipsComparisons.add(p.getValue());
        }        
        break;
      case "validation":
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
        else if (p.getValue().equals("no-experimental-content"))
          noExperimentalContent = true;
        break;
      case "tabbed-snapshots":
        tabbedSnapshots = p.getValue().equals("true");
        break;
      case "r4-exclusion":
        r4tor4b.markExempt(p.getValue(), true);
        break;
      case "r4b-exclusion":
        r4tor4b.markExempt(p.getValue(), false);
        break;
      case "display-warnings":
        displayWarnings = "true".equals(p.getValue());
        break;
      case "produce-jekyll-data":    
        produceJekyllData = "true".equals(p.getValue());
        break;
      case "page-factory":
        dir = Utilities.path(rootDir, "temp", "factory-pages", "factory"+pageFactories.size());
        FileUtilities.createDirectory(dir);
        pageFactories.add(new PageFactory(Utilities.path(rootDir, p.getValue()), dir));
        pagesDirs.add(dir);
        break;
      case "i18n-default-lang":
        hasTranslations = true;
        defaultTranslationLang = p.getValue();
        break;
      case "i18n-lang":
        hasTranslations = true;
        translationLangs.add(p.getValue());
        break;
      case "translation-supplements":
        hasTranslations = true;
        translationSources.add(p.getValue());
        break;
      case "translation-sources":
        hasTranslations = true;
        translationSources.add(p.getValue());
        break;
      case "validation-duration-report-cutoff":
        validationLogTime = Utilities.parseInt(p.getValue(), 0) * 1000;
        break;
      case "viewDefinition":
        viewDefinitions.add(p.getValue());
        break;
      case "test-data-factories":
        testDataFactories.add(p.getValue());
        break;
      case "fixed-value-format":
        fixedFormat = FixedValueFormat.fromCode(p.getValue());
        break;
      case "no-cibuild-issues":
        noCIBuildIssues = "true".equals(p.getValue());
        break;
      case "logged-when-scanning":
        if ("false".equals(p.getValue())) {
          fetcher.setReport(false);
        } else if ("stack".equals(p.getValue())) {
          fetcher.setReport(true);
          fetcher.setDebug(true);
        }  else {
          fetcher.setReport(true);
        }
        break;
      case "auto-oid-root":
        oidRoot = p.getValue(); 
        if (!OIDUtilities.isValidOID(oidRoot)) {
          throw new Error("Invalid oid found in assign-missing-oids-root: "+oidRoot);
        }
        oidIni = new IniFile(oidIniLocation());
        if (!oidIni.hasSection("Documentation")) {
          oidIni.setStringProperty("Documentation", "information1", "This file stores the OID assignments for resources defined in this IG.", null);
          oidIni.setStringProperty("Documentation", "information2", "It must be added to git and committed when resources are added or their id is changed", null);
          oidIni.setStringProperty("Documentation", "information3", "You should not generally need to edit this file, but if you do:", null);
          oidIni.setStringProperty("Documentation", "information4", " (a) you can change the id of a resource (left side) if you change it's actual id in your source, to maintain OID consistency", null);
          oidIni.setStringProperty("Documentation", "information5", " (b) you can change the oid of the resource to an OID you assign manually. If you really know what you're doing with OIDs", null);
          oidIni.setStringProperty("Documentation", "information6", "There is never a reason to edit anything else", null);
          oidIni.save();
        }
        if (!hasOid(sourceIg.getIdentifier())) {
          sourceIg.getIdentifier().add(new Identifier().setSystem("urn:ietf:rfc:3986").setValue("urn:oid:"+oidRoot));
        }
        break;
      case "resource-language-policy":
        langPolicy = LanguagePopulationPolicy.fromCode(p.getValue());       
        if (langPolicy == null) {
          throw new Error("resource-language-policy value of '"+p.getValue()+"' not understood");
        }
        break;
      case "profile-test-cases": 
        profileTestCases.add(p.getValue());
      case "pin-canonicals":
        switch (p.getValue()) {
        case "pin-none":
          pinningPolicy = PinningPolicy.NO_ACTION;
          break;
        case "pin-all": 
          pinningPolicy = PinningPolicy.FIX;
          break;
        case "pin-multiples":
          pinningPolicy = PinningPolicy.WHEN_MULTIPLE_CHOICES;
          break;
        default:
          throw new FHIRException("Unknown value for 'pin-canonicals' of '"+p.getValue()+"'");
        }
        break;
      case "pin-manifest":
        pinDest = p.getValue();
        break;
      case "generate-uml":   
        generateUml = UMLGenerationMode.fromCode(p.getValue());
        break;
      case "r5-bundle-relative-reference-policy" : 
        r5BundleRelativeReferencePolicy = R5BundleRelativeReferencePolicy.fromCode(p.getValue());
      case "suppress-mappings":
        if ("*".equals(p.getValue())) {
          suppressedMappings.addAll(Utilities.strings("http://hl7.org/fhir/fivews", "http://hl7.org/fhir/workflow", "http://hl7.org/fhir/interface", "http://hl7.org/v2",
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
          suppressedMappings.add(p.getValue());          
        }
      default:
        if (pc.startsWith("wantGen-")) {
          String code = pc.substring(8);
          wantGenParams.put(code, Boolean.valueOf(p.getValue().equals("true")));
        } else if (!template.isParameter(pc)) {
          unknownParams.add(pc+"="+p.getValue());
        }
      }
      count++;
    }

    if (langPolicy == LanguagePopulationPolicy.IG || langPolicy == LanguagePopulationPolicy.ALL) {
      if (sourceIg.hasJurisdiction()) {
        Locale localeFromRegion = ResourceUtilities.getLocale(sourceIg);
        if (localeFromRegion != null) {
          sourceIg.setLanguage(localeFromRegion.toLanguageTag());
        } else {
          throw new Error("Unable to determine locale from jurisdiction (as requested by policy)");
        }
      } else { 
        sourceIg.setLanguage("en");
      }
    }
    if (ini.hasProperty("IG", "jekyll-timeout")) { //todo: consider adding this to ImplementationGuideDefinitionParameterComponent
      jekyllTimeout = ini.getLongProperty("IG", "jekyll-timeout") * 1000;
    }

    for (String s : liquid0) {
      templateProvider.load(Utilities.path(rootDir, s));
    }
    for (String s : liquid1) {
      templateProvider.load(Utilities.path(rootDir, s));
    }
    for (String s : liquid2) {
      templateProvider.load(Utilities.path(rootDir, s));
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
    List<String> extraDirs = new ArrayList<String>();
    for (String s : resourceDirs) {
      if (s.endsWith(File.separator+"*")) {
        logDebugMessage(LogCategory.INIT, "Scan Source: "+s);
        scanDirectories(FileUtilities.getDirectoryForFile(s), extraDirs);

      }
    }
    resourceDirs.addAll(extraDirs);

    List<String> missingDirs = new ArrayList<String>();
    for (String s : resourceDirs) {
      logDebugMessage(LogCategory.INIT, "Source: "+s);
      if (s.endsWith(File.separator+"*")) {
        missingDirs.add(s);

      }
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
    FileUtilities.clearDirectory(tempDir);
    forceDir(tempDir);
    forceDir(Utilities.path(tempDir, "_includes"));
    forceDir(Utilities.path(tempDir, "_data"));
    for (String s : allLangs()) {
      forceDir(Utilities.path(tempDir, s));
    }
    logDebugMessage(LogCategory.INIT, "Output: "+outputDir);
    forceDir(outputDir);
    FileUtilities.clearDirectory(outputDir);
    if (qaDir != null) {
      logDebugMessage(LogCategory.INIT, "QA Dir: "+qaDir);
      forceDir(qaDir);
    }
    makeQA = mode == IGBuildMode.WEBSERVER ? false : qaDir != null;

    if (Utilities.existsInList(version.substring(0,  3), "1.0", "1.4", "1.6", "3.0"))
      markdownEngine = new MarkDownProcessor(Dialect.DARING_FIREBALL);
    else
      markdownEngine = new MarkDownProcessor(Dialect.COMMON_MARK);


    // initializing the tx sub-system
    FileUtilities.createDirectory(vsCache);
    if (cacheOption == CacheOption.CLEAR_ALL) {
      log("Terminology Cache is at "+vsCache+". Clearing now");
      FileUtilities.clearDirectory(vsCache);
    } else if (mode == IGBuildMode.AUTOBUILD) {
      log("Terminology Cache is at "+vsCache+". Trimming now");
      FileUtilities.clearDirectory(vsCache, "snomed.cache", "loinc.cache", "ucum.cache");
    } else if (cacheOption == CacheOption.CLEAR_ERRORS) {
      log("Terminology Cache is at "+vsCache+". Clearing Errors now");
      logDebugMessage(LogCategory.INIT, "Deleted "+Integer.toString(clearErrors(vsCache))+" files");
    } else {
      log("Terminology Cache is at "+vsCache+". "+Integer.toString(FileUtilities.countFilesInDirectory(vsCache))+" files in cache");
    }
    if (!new File(vsCache).exists())
      throw new Exception("Unable to access or create the cache directory at "+vsCache);
    logDebugMessage(LogCategory.INIT, "Load Terminology Cache from "+vsCache);

    
    // loading the specifications
    context = loadCorePackage();
    context.setProgress(true);
    context.setLogger(logger);
    context.setAllowLoadingDuplicates(true);
    context.setExpandCodesLimit(1000);
    context.setExpansionParameters(makeExpProfile());
    context.getTxClientManager().setUsage("publication");
    for (PageFactory pf : pageFactories) {
      pf.setContext(context);
    }
    dr = new DataRenderer(context);
    for (String s : conversionVersions) {
      loadConversionVersion(s);
    }
    langUtils = new LanguageUtils(context);
    txLog = FileUtilities.createTempFile("fhir-ig-", ".html").getAbsolutePath();
    System.out.println("Running Terminology Log: "+txLog);
    if (mode != IGBuildMode.WEBSERVER) {
      if (txServer == null || !txServer.contains(":")) {
        log("WARNING: Running without terminology server - terminology content will likely not publish correctly");
        context.setCanRunWithoutTerminology(true);
        txLog = null;
      } else {
        log("Connect to Terminology Server at "+txServer);
        context.connectToTSServer(new TerminologyClientFactory(version), txServer, "fhir/publisher", txLog, true);
      }
    } else { 
      context.connectToTSServer(new TerminologyClientFactory(version), webTxServer.getAddress(), "fhir/publisher", txLog, true);
    }
    if (expParams != null) {
      /* This call to uncheckedPath is allowed here because the path is used to
         load an existing resource, and is not persisted in the loadFile method.
       */
      context.setExpansionParameters(new ExpansionParameterUtilities(context).reviewVersions((Parameters) VersionConvertorFactory_40_50.convertResource(FormatUtilities.loadFile(Utilities.uncheckedPath(FileUtilities.getDirectoryForFile(igName), expParams)))));
    } else if (!expParamMap.isEmpty()) {
      context.setExpansionParameters(new Parameters());      
    }
    for (String n : expParamMap.values()) {
      context.getExpansionParameters().addParameter(n, expParamMap.get(n));
    }

    newMultiLangTemplateFormat = template.config().asBoolean("multilanguage-format");  
    loadPubPack();
    igpkp = new IGKnowledgeProvider(context, checkAppendSlash(specPath), determineCanonical(sourceIg.getUrl(), "ImplementationGuide.url"), template.config(), errors, VersionUtilities.isR2Ver(version), template, listedURLExemptions, altCanonical, fileList, module);
    if (autoLoad) {
      igpkp.setAutoPath(true);
    }
    fetcher.setPkp(igpkp);
    fetcher.setContext(context);
    template.loadSummaryRows(igpkp.summaryRows());

    if (VersionUtilities.isR4Plus(version) && !dependsOnExtensions(sourceIg.getDependsOn()) && !sourceIg.getPackageId().contains("hl7.fhir.uv.extensions")) {
      ImplementationGuideDependsOnComponent dep = new ImplementationGuideDependsOnComponent();
      dep.setUserData(UserDataNames.pub_no_load_deps, "true");
      dep.setId("hl7ext");
      dep.setPackageId(getExtensionsPackageName());
      dep.setUri("http://hl7.org/fhir/extensions/ImplementationGuide/hl7.fhir.uv.extensions");
      dep.setVersion(pcm.getLatestVersion(dep.getPackageId()));
      dep.addExtension(ToolingExtensions.EXT_IGDEP_COMMENT, new MarkdownType("Automatically added as a dependency - all IGs depend on the HL7 Extension Pack"));
      sourceIg.getDependsOn().add(0, dep);
    } 
    if (!dependsOnUTG(sourceIg.getDependsOn()) && !sourceIg.getPackageId().contains("hl7.terminology")) {
      ImplementationGuideDependsOnComponent dep = new ImplementationGuideDependsOnComponent();
      dep.setUserData(UserDataNames.pub_no_load_deps, "true");
      dep.setId("hl7tx");
      dep.setPackageId(getUTGPackageName());
      dep.setUri("http://terminology.hl7.org/ImplementationGuide/hl7.terminology");
      dep.setVersion(pcm.getLatestVersion(dep.getPackageId()));
      dep.addExtension(ToolingExtensions.EXT_IGDEP_COMMENT, new MarkdownType("Automatically added as a dependency - all IGs depend on HL7 Terminology"));
      sourceIg.getDependsOn().add(0, dep);
    }    
    if (!"hl7.fhir.uv.tools".equals(sourceIg.getPackageId()) && !dependsOnTooling(sourceIg.getDependsOn())) {
      String toolingPackageId = getToolingPackageName()+"#"+TOOLING_IG_CURRENT_RELEASE;
      if (sourceIg.getDefinition().hasExtension("http://hl7.org/fhir/tools/StructureDefinition/ig-internal-dependency")) {
        sourceIg.getDefinition().getExtensionByUrl("http://hl7.org/fhir/tools/StructureDefinition/ig-internal-dependency").setValue(new CodeType(toolingPackageId));      
      } else {
        sourceIg.getDefinition().addExtension("http://hl7.org/fhir/tools/StructureDefinition/ig-internal-dependency", new CodeType(toolingPackageId));
      }
    }
    
    inspector = new HTMLInspector(outputDir, specMaps, linkSpecMaps, this, igpkp.getCanonical(), sourceIg.getPackageId(), sourceIg.getVersion(), trackedFragments, fileList, module, mode == IGBuildMode.AUTOBUILD || mode == IGBuildMode.WEBSERVER, trackFragments ? fragmentUses : null, relatedIGs, noCIBuildIssues, allLangs());
    inspector.getManual().add("full-ig.zip");
    if (historyPage != null) {
      inspector.getManual().add(historyPage);
      inspector.getManual().add(Utilities.pathURL(igpkp.getCanonical(), historyPage));
    }
    inspector.getManual().add("qa.html");
    inspector.getManual().add("qa-tx.html");
    inspector.getManual().add("qa-ipreview.html");
    inspector.getExemptHtmlPatterns().addAll(exemptHtmlPatterns);
    inspector.setPcm(pcm);

    int i = 0;
    for (ImplementationGuideDependsOnComponent dep : sourceIg.getDependsOn()) {
      loadIg(dep, i, !dep.hasUserData(UserDataNames.pub_no_load_deps));
      i++;
    }
    if (!"hl7.fhir.uv.tools".equals(sourceIg.getPackageId()) && !dependsOnTooling(sourceIg.getDependsOn())) {
      loadIg("igtools", getToolingPackageName(), TOOLING_IG_CURRENT_RELEASE, "http://hl7.org/fhir/tools/ImplementationGuide/hl7.fhir.uv.tools", i, false);   
    }

    // we're also going to look for packages that can be referred to but aren't dependencies
    for (Extension ext : sourceIg.getDefinition().getExtensionsByUrl("http://hl7.org/fhir/tools/StructureDefinition/ig-link-dependency")) {
      loadLinkIg(ext.getValue().primitiveValue());
    }

    for (String s : relatedIGParams) {
      loadRelatedIg(s);
    }

    if (!VersionUtilities.isR5Plus(context.getVersion())) {
      System.out.println("Load R5 Specials");
      R5ExtensionsLoader r5e = new R5ExtensionsLoader(pcm, context);
      r5e.load();
      r5e.loadR5SpecialTypes(SpecialTypeHandler.specialTypes(context.getVersion()));
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
    validatorSession = new ValidatorSession();
    IGPublisherHostServices hs = new IGPublisherHostServices(igpkp, fileList, context, new DateTimeType(execTime), new StringType(igpkp.specPath()));
    hs.registerFunction(new GlobalObjectRandomFunction());
    hs.registerFunction(new BaseTableWrapper.TableColumnFunction());
    hs.registerFunction(new BaseTableWrapper.TableDateColumnFunction());
    hs.registerFunction(new TestDataFactory.CellLookupFunction());
    hs.registerFunction(new TestDataFactory.TableLookupFunction());
    hs.registerFunction(new TerminologyFunctions.ExpandFunction());
    hs.registerFunction(new TerminologyFunctions.ValidateVSFunction());
    hs.registerFunction(new TerminologyFunctions.TranslateFunction());

    validator = new InstanceValidator(context, hs, context.getXVer(), validatorSession, new ValidatorSettings()); // todo: host services for reference resolution....
    validator.setAllowXsiLocation(true);
    validator.setNoBindingMsgSuppressed(true);
    validator.setNoExtensibleWarnings(!allowExtensibleWarnings);
    validator.setHintAboutNonMustSupport(hintAboutNonMustSupport);
    validator.setAnyExtensionsAllowed(anyExtensionsAllowed);
    validator.setAllowExamples(true);
    validator.setCrumbTrails(true);
    validator.setWantCheckSnapshotUnchanged(true);
    validator.setForPublication(true);
    validator.getSettings().setDisplayWarningMode(displayWarnings);
    cu = new ContextUtilities(context, suppressedMappings);

    pvalidator = new ProfileValidator(context, validator.getSettings(), context.getXVer(), validatorSession);
    csvalidator = new CodeSystemValidator(context, validator.getSettings(), context.getXVer(), validatorSession);
    pvalidator.setCheckAggregation(checkAggregation);
    pvalidator.setCheckMustSupport(hintAboutNonMustSupport);
    validator.setShowMessagesFromReferences(showReferenceMessages);
    validator.getExtensionDomains().addAll(extensionDomains);
    validator.setNoExperimentalContent(noExperimentalContent);
    validator.getExtensionDomains().add(ToolingExtensions.EXT_PRIVATE_BASE);
    validationFetcher = new ValidationServices(context, igpkp, sourceIg, fileList, npmList, bundleReferencesResolve, specMaps, module);
    validator.setFetcher(validationFetcher);
    validator.setPolicyAdvisor(validationFetcher);
    validator.setTracker(this);
    validator.getSettings().setR5BundleRelativeReferencePolicy(r5BundleRelativeReferencePolicy);

    if (!generateVersions.isEmpty()) {
      Collections.sort(generateVersions);
      validator.getSettings().setMinVersion(VersionUtilities.getMajMin(generateVersions.get(0)));
      validator.getSettings().setMaxVersion(VersionUtilities.getMajMin(generateVersions.get(generateVersions.size()-1)));
    }

    for (String s : context.getBinaryKeysAsSet()) {
      if (needFile(s)) {
        if (makeQA)
          checkMakeFile(context.getBinaryForKey(s), Utilities.path(qaDir, s), otherFilesStartup);
        checkMakeFile(context.getBinaryForKey(s), Utilities.path(tempDir, s), otherFilesStartup);
        for (String l : allLangs()) {
          checkMakeFile(context.getBinaryForKey(s), Utilities.path(tempDir, l, s), otherFilesStartup);            
        }
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

    for (Extension e : sourceIg.getExtensionsByUrl(ToolingExtensions.EXT_IGP_SPREADSHEET)) {
      spreadsheets.add(e.getValue().primitiveValue());
    }
    ToolingExtensions.removeExtension(sourceIg, ToolingExtensions.EXT_IGP_SPREADSHEET);

    for (Extension e : sourceIg.getExtensionsByUrl(ToolingExtensions.EXT_IGP_MAPPING_CSV)) {
      mappings.add(e.getValue().primitiveValue());
    }
    for (Extension e : sourceIg.getDefinition().getExtensionsByUrl(ToolingExtensions.EXT_IGP_BUNDLE)) {
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

  private void loadRelatedIg(String s) throws FHIRException, IOException {
    String role = s.substring(0, s.indexOf(":"));
    s = s.substring(s.indexOf(":")+1);
    
    String code = s.substring(0, s.indexOf("="));
    String id =  s.substring(s.indexOf("=")+1);
    
    NpmPackage npm;
    try {
      npm = pcm.loadPackage(id+"#dev");
    } catch (Exception e) {
      String msg = e.getMessage();
      if (msg.contains("(")) {
        msg = msg.substring(0, msg.indexOf("("));
      }
      relatedIGs.add(new RelatedIG(code, id, RelatedIGRole.fromCode(role), msg));
      return;
    } 

    if (mode == IGBuildMode.PUBLICATION) {
      relatedIGs.add(new RelatedIG(code, id, RelatedIGLoadingMode.WEB, RelatedIGRole.fromCode(role), npm, determineLocation(code, id)));      
    } else if (Utilities.startsWithInList(npm.getWebLocation(), "http://", "https://")) {
      relatedIGs.add(new RelatedIG(code, id, RelatedIGLoadingMode.CIBUILD, RelatedIGRole.fromCode(role), npm));
    } else {
      relatedIGs.add(new RelatedIG(code, id, RelatedIGLoadingMode.LOCAL, RelatedIGRole.fromCode(role), npm));
    }
  }

  private String determineLocation(String code, String id) throws JsonException, IOException {
    // to determine the location of this IG in publication mode, we have to figure out what version we are publishing against 
    // this is in the publication request 
    // then, we need to look the package 
    // if it's already published, we use that location 
    // if it's to be published, we find #current, extract that publication request, and use that path (check version)
    // otherwise, bang
    JsonObject pr = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(new File(Utilities.path(FileUtilities.getDirectoryForFile(configFile), "publication-request.json")));
    String rigV = pr.forceObject("related").asString(code);
    if (rigV == null) {
      throw new FHIRException("No specified Publication version for relatedIG "+code);
    }
    NpmPackage npm;
    try {
      npm = pcm.loadPackage(id, rigV);
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
      npm = pcm.loadPackage(id, "current");
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

  private void processFactories(List<String> factories) throws IOException {    
    LiquidEngine liquid = new LiquidEngine(context, validator.getExternalHostServices());
    for (String f : factories) {
      String rootFolder = FileUtilities.getDirectoryForFile(configFile);
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
        TestDataFactory tdf = new TestDataFactory(context, fact, liquid, validator.getFHIRPathEngine(), igpkp.getCanonical(), rootFolder, log.getAbsolutePath(), factoryProfileMap, context.getLocale());
        log("Execute Test Data Factory '"+tdf.getName()+"'. Log in "+tdf.statedLog());
        tdf.execute();
      }
    }
  }

  private List<String> allLangs() {
    List<String> all = new ArrayList<String>();
    if (isNewML()) {
      all.add(defaultTranslationLang);
      all.addAll(translationLangs);
    }
    return all;
  }

  private boolean isNewML() {
    return newMultiLangTemplateFormat;
  }

  private String oidIniLocation() throws IOException {
    String f = Utilities.path(FileUtilities.getDirectoryForFile(igName), "oids.ini");
    if (new File(f).exists()) {
      if (isSushi) {
        String nf = Utilities.path(rootDir, "oids.ini");
        FileUtilities.copyFile(f, nf);
        new File(f).delete();
        return nf;
      }
      return f;
    }
    if (isSushi) {
      f = Utilities.path(rootDir, "oids.ini");      
    }
    return f;
  }

  private IPublisherModule loadModule(String name) throws Exception {
    if (Utilities.noString(name)) {
      return new NullModule();
    } else switch (name) {
    case "x-version": return new CrossVersionModule();
    default: throw new Exception("Unknown module name \""+name+"\" in ig.ini");
    }
  }

  private void loadConversionVersion(String version) throws FHIRException, IOException {
    String v = VersionUtilities.getMajMin(version);
    if (VersionUtilities.versionsMatch(v, context.getVersion())) {
      throw new FHIRException("Unable to load conversion version "+version+" when base version is already "+context.getVersion());
    }
    String pid = VersionUtilities.packageForVersion(v);
    log("Load "+pid);
    NpmPackage npm = pcm.loadPackage(pid);
    SpecMapManager spm = loadSpecDetails(FileUtilities.streamToBytes(npm.load("other", "spec.internals")), "convSpec"+v, npm, npm.getWebLocation());
    IContextResourceLoader loader = ValidatorUtils.loaderForVersion(npm.fhirVersion(), new PatchLoaderKnowledgeProvider(npm, spm));
    if (loader.getTypes().contains("StructureMap")) {
      loader.getTypes().remove("StructureMap");
    }
    loader.setPatchUrls(true);
    loader.setLoadProfiles(false);
    context.loadFromPackage(npm, loader);
  }

  @NonNull
  private String getPathResourceDirectory(ImplementationGuideDefinitionParameterComponent p) throws IOException {
    if ( p.getValue().endsWith("*")) {
      return Utilities.path(rootDir, p.getValue().substring(0, p.getValue().length() - 1)) + "*";
    }
    return Utilities.path(rootDir, p.getValue());
  }

  private void scanDirectories(String dir, List<String> extraDirs) {
    fetcher.scanFolders(dir, extraDirs);

  }

  private void generateLoadedSnapshots() {
    for (StructureDefinition sd : new ContextUtilities(context, suppressedMappings).allStructures()) {
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
      utils.setSuppressedMappings(suppressedMappings);
      try {
        utils.generateSnapshot(base, sd, sd.getUrl(), Utilities.extractBaseUrl(base.getWebPath()), sd.getName());
        if (!sd.hasSnapshot()) {
          System.out.println("Unable to generate snapshot for "+sd.getUrl()+": "+messages.toString());        
        }
      } catch (Exception e) {
        System.out.println("Exception generating snapshot for "+sd.getUrl()+": "+e.getMessage());        
      }      
    }
    Element element = (Element) sd.getUserData(UserDataNames.pub_element);
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


  private boolean dependsOnTooling(List<ImplementationGuideDependsOnComponent> dependsOn) {
    for (ImplementationGuideDependsOnComponent d : dependsOn) {
      if (d.hasPackageId() && d.getPackageId().contains("hl7.fhir.uv.tools")) {
        return true;
      }
      if (d.hasUri() && d.getUri().contains("hl7.org/fhir/tools")) {
        return true;
      }
    }
    return false;
  }


  private boolean dependsOnExtensions(List<ImplementationGuideDependsOnComponent> dependsOn) {
    for (ImplementationGuideDependsOnComponent d : dependsOn) {
      if (d.hasPackageId() && d.getPackageId().equals("hl7.fhir.uv.extensions")) {
        return true;
      }
      if (d.hasUri() && d.getUri().contains("hl7.org/fhir/extensions")) {
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


  private String processVersion(String v) {
    return v.equals("$build") ? Constants.VERSION : v;
  }

  private void loadPubPack() throws FHIRException, IOException {
    NpmPackage npm = pcm.loadPackage(CommonPackages.ID_PUBPACK, CommonPackages.VER_PUBPACK);
    context.loadFromPackage(npm, null);
    npm = pcm.loadPackage(CommonPackages.ID_XVER, CommonPackages.VER_XVER);
    context.loadFromPackage(npm, null);
  }

  private void loadUTG() throws FHIRException, IOException {
    String vs = getUTGPackageName();
    if (vs != null) {
      NpmPackage npm = pcm.loadPackage(vs, null);
      SpecMapManager spm = new SpecMapManager(FileUtilities.streamToBytes(npm.load("other", "spec.internals")), npm.vid(), npm.fhirVersion());
      IContextResourceLoader loader = new PublisherLoader(npm, spm, npm.getWebLocation(), igpkp).makeLoader();
      context.loadFromPackage(npm, loader);
    }
  }

  private String getUTGPackageName() throws FHIRException, IOException {
    String vs = null;
    if (VersionUtilities.isR3Ver(version)) {
      vs = "hl7.terminology.r3";
    } else if (VersionUtilities.isR4Ver(version) || VersionUtilities.isR4BVer(version)) {
      vs = "hl7.terminology.r4";
    } else if (VersionUtilities.isR5Ver(version)) {
      vs = "hl7.terminology.r5";
    } else if (VersionUtilities.isR6Ver(version)) {
      vs = "hl7.terminology.r5";
    }
    return vs;
  }
  
  private String getToolingPackageName() throws FHIRException, IOException {
    String pn = null;
    if (VersionUtilities.isR3Ver(version)) {
      pn = "hl7.fhir.uv.tools.r3";
    } else if (VersionUtilities.isR4Ver(version) || VersionUtilities.isR4BVer(version)) {
      pn = "hl7.fhir.uv.tools.r4";
    } else if (VersionUtilities.isR5Ver(version)) {
      pn = "hl7.fhir.uv.tools.r5";
    } else if (VersionUtilities.isR6Ver(version)) {
      pn = "hl7.fhir.uv.tools.r5";
    }
    return pn;
  }

  private String getExtensionsPackageName() throws FHIRException, IOException {
    String vs = null;
    if (VersionUtilities.isR3Ver(version)) {
      vs = "hl7.fhir.uv.extensions.r3";
    } else if (VersionUtilities.isR4Ver(version) || VersionUtilities.isR4BVer(version)) {
      vs = "hl7.fhir.uv.extensions.r4";
    } else if (VersionUtilities.isR5Ver(version)) {
      vs = "hl7.fhir.uv.extensions.r5";
    } else if (VersionUtilities.isR6Ver(version)) {
      vs = "hl7.fhir.uv.extensions.r6";
    }
    return vs;
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
        extraTemplateList.add("defns");
        extraTemplates.put("defns", "Definitions");
      }
      if (!hasFormat) {
        extraTemplateList.add("format");
        extraTemplates.put("format", "FMT Representation");
      }
      for (JsonElement template : templates) {
        if (template.isJsonPrimitive()) {
          extraTemplateList.add(template.asString());
          extraTemplates.put(template.toString(), template.toString());
          if ("examples".equals(template.asString()))
            exampleTemplates.add(template.toString());
          if (template.asString().endsWith("-history"))
            historyTemplates.add(template.asString());
        } else {
          String templateName = ((JsonObject)template).asString("name");
          extraTemplateList.add(templateName);
          extraTemplates.put(templateName, ((JsonObject)template).asString("description"));
          if (!setExtras) {
            if (templateName.equals("examples"))
              exampleTemplates.add(templateName);
            if (templateName.endsWith("-history"))
              historyTemplates.add(templateName);
          } else if (((JsonObject)template).has("isExamples") && ((JsonObject)template).asBoolean("isExamples")) {
            exampleTemplates.add(templateName);
          } else if (((JsonObject)template).has("isHistory") && ((JsonObject)template).asBoolean("isHistory")) {
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
      //      System.out.println("Pre-Process: "+path+" = "+relativePath+" | "+prePagesXslt);
      PreProcessInfo ppinfo = new PreProcessInfo(prePagesXslt, relativePath);
      preProcessInfo.put(path, ppinfo);
    }
  }

  private void loadSuppressedMessages(String messageFile, String path) throws Exception {
    File f = new File(messageFile);
    if (!f.exists()) {
      errors.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, path, "Supressed messages file not found", IssueSeverity.ERROR));
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
                errors.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, path, "Supressed messages file has errors with no reason ("+l+")", IssueSeverity.ERROR));
                suppressedMessages.add(l, "?pub-msg-1?");
              } else {
                suppressedMessages.add(l, reason);
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
              suppressedMessages.add(b.toString(), "?pub-msg-2?");
            b = new StringBuilder();
          } else
            b.append(c);
        }
        if (b.length() > 0)
          suppressedMessages.add(b.toString(), "?pub-msg-3?");
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
      FileUtilities.createDirectory(adHocTmpDir);
    FileUtilities.clearDirectory(adHocTmpDir);

    FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager.Builder().build();

    NpmPackage npm = null; 
    if (specifiedVersion == null) {
      npm = pcm.loadPackage("hl7.fhir.r5.core", Constants.VERSION);
    } else {
      String vid = VersionUtilities.getCurrentVersion(specifiedVersion);
      String pid = VersionUtilities.packageForVersion(vid);
      npm = pcm.loadPackage(pid, vid);
    }

    InputStream igTemplateInputStream = npm.load("other", "ig-template.zip");
    String zipTargetDirectory = adHocTmpDir;
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
    configFile = Utilities.path(adHocTmpDir, "ig.json");
    // temporary config, until full ig template is in place
    String v = specifiedVersion != null ? VersionUtilities.getCurrentVersion(specifiedVersion) : Constants.VERSION; 
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
            "}\r\n", configFile);
    FileUtilities.createDirectory(Utilities.path(adHocTmpDir, "resources"));
    FileUtilities.createDirectory(Utilities.path(adHocTmpDir, "pages"));
  }

  private SimpleWorkerContext loadCorePackage() throws Exception {
    NpmPackage pi = null;

    String v = version;
    
    if (Utilities.noString(igPack)) {
      System.out.println("Core Package "+VersionUtilities.packageForVersion(v)+"#"+v);
      pi = pcm.loadPackage(VersionUtilities.packageForVersion(v), v);
    } else {
      System.out.println("Load from provided file "+igPack);
      pi = NpmPackage.fromPackage(new FileInputStream(igPack));
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
        logDebugMessage(LogCategory.INIT, "Updating hl7.fhir.core-"+version+" package from source (too old - is "+cacheVersion+", must be "+lastAcceptableVersion);
        pi = pcm.addPackageToCache("hl7.fhir.core", "current", fetchFromSource("hl7.fhir.core-"+v, getMasterSource()), getMasterSource());
      } else {
        logDebugMessage(LogCategory.INIT, "   ...  ok: is "+cacheVersion+", must be "+lastAcceptableVersion);
      }
    }
    logDebugMessage(LogCategory.INIT, "Load hl7.fhir.core-"+v+" package from "+pi.summary());
    npmList.add(pi);

    SpecMapManager spm = loadSpecDetails(FileUtilities.streamToBytes(pi.load("other", "spec.internals")), "basespec", pi, specPath);
    SimpleWorkerContext sp;
    IContextResourceLoader loader = new PublisherLoader(pi, spm, specPath, igpkp).makeLoader();
    sp = new SimpleWorkerContext.SimpleWorkerContextBuilder().withTerminologyCachePath(vsCache).fromPackage(pi, loader, false);
    sp.loadBinariesFromFolder(pi);
    sp.setForPublication(true);
    sp.setSuppressedMappings(suppressedMappings);
    if (!version.equals(Constants.VERSION)) {
      // If it wasn't a 4.0 source, we need to set the ids because they might not have been set in the source
      ProfileUtilities utils = new ProfileUtilities(context, new ArrayList<ValidationMessage>(), igpkp);
      for (StructureDefinition sd : new ContextUtilities(sp, suppressedMappings).allStructures()) {
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


  private String getMasterSource() {
    if (VersionUtilities.isR2Ver(version)) return "http://hl7.org/fhir/DSTU2/hl7.fhir.r2.core.tgz";
    if (VersionUtilities.isR2BVer(version)) return "http://hl7.org/fhir/2016May/hl7.fhir.r2b.core.tgz";
    if (VersionUtilities.isR3Ver(version)) return "http://hl7.org/fhir/STU3/hl7.fhir.r3.core.tgz";
    if (VersionUtilities.isR4Ver(version)) return "http://hl7.org/fhir/R4/hl7.fhir.r4.core.tgz";
    if (Constants.VERSION.equals(version)) return "http://hl7.org/fhir/R5/hl7.fhir.r5.core.tgz";
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
    ep.addParameter("x-system-cache-id", "dc8fd4bc-091a-424a-8a3b-6198ef146891"); // change this to blow the cache
    // all defaults....
    return ep;
  }

  private void loadLinkIg(String packageId) throws Exception {
    if (!Utilities.noString(packageId)) {
      String[] p = packageId.split("\\#");
      NpmPackage pi = p.length == 1 ? pcm.loadPackage(p[0]) : pcm.loadPackage(p[0], p[1]);
      if (pi == null) {
        throw new Exception("Package Id "+packageId+" is unknown");
      }
      logDebugMessage(LogCategory.PROGRESS, "Load Link package "+packageId);
      String webref = pi.getWebLocation();
      webref = PackageHacker.fixPackageUrl(webref);

      SpecMapManager igm = pi.hasFile("other", "spec.internals") ?  new SpecMapManager( FileUtilities.streamToBytes(pi.load("other", "spec.internals")), pi.vid(), pi.fhirVersion()) : SpecMapManager.createSpecialPackage(pi, pcm);
      igm.setName(pi.title());
      igm.setBase(pi.canonical());
      igm.setBase2(PackageHacker.fixPackageUrl(pi.url()));
      linkSpecMaps.add(new LinkedSpecification(igm, pi));
    }
  }


  private void loadIg(ImplementationGuideDependsOnComponent dep, int index, boolean loadDeps) throws Exception {
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
      packageId = pcm.getPackageId(canonical);
    if (Utilities.noString(canonical) && !Utilities.noString(packageId))
      canonical = pcm.getPackageUrl(packageId);
    if (Utilities.noString(canonical))
      throw new Exception("You must specify a canonical URL for the IG "+name);
    String igver = dep.getVersion();
    if (Utilities.noString(igver)) {
      igver = pcm.getLatestVersion(packageId);
      if (Utilities.noString(igver)) {
        throw new Exception("The latest version could not be determined, so you must specify a version for the IG "+packageId+" ("+canonical+")");
      }
    }

    NpmPackage pi = packageId == null ? null : pcm.loadPackage(packageId, igver);
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
        errors.add(new ValidationMessage(Source.Publisher, IssueType.INFORMATIONAL, "ImplementationGuide.dependency["+index+"].url", 
            "The correct canonical URL for this dependency is "+cu, IssueSeverity.INFORMATION));
      }
    }

    loadIGPackage(name, canonical, packageId, igver, pi, loadDeps);

  }

  private void loadIg(String name, String packageId, String igver, String uri, int index, boolean loadDeps) throws Exception {
    String canonical = determineCanonical(uri, "ImplementationGuide.dependency["+index+"].url");
    if (Utilities.noString(canonical) && !Utilities.noString(packageId))
      canonical = pcm.getPackageUrl(packageId);
    if (Utilities.noString(canonical))
      throw new Exception("You must specify a canonical URL for the IG "+name);


    NpmPackage pi = packageId == null ? null : pcm.loadPackage(packageId, igver);
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
      npmList.add(pi);
    logDebugMessage(LogCategory.INIT, "Load "+name+" ("+canonical+") from "+packageId+"#"+igver);


    String webref = pi.getWebLocation();
    webref = PackageHacker.fixPackageUrl(webref);

    SpecMapManager igm = pi.hasFile("other", "spec.internals") ?  new SpecMapManager( FileUtilities.streamToBytes(pi.load("other", "spec.internals")), pi.vid(), pi.fhirVersion()) : SpecMapManager.createSpecialPackage(pi, pcm);
    igm.setName(name);
    igm.setBase(canonical);
    igm.setBase2(PackageHacker.fixPackageUrl(pi.url()));
    igm.setNpm(pi);
    specMaps.add(igm);
    if (!VersionUtilities.versionsCompatible(version, pi.fhirVersion())) {
      if (!pi.isWarned()) {
        errors.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, sourceIg.fhirType()+"/"+sourceIg.getId(), "This IG is version "+version+", while the IG '"+pi.name()+"' is from version "+pi.fhirVersion(), IssueSeverity.ERROR));
        log("Version mismatch. This IG is version "+version+", while the IG '"+pi.name()+"' is from version "+pi.fhirVersion()+" (will try to run anyway)");
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
        if (!context.hasPackage(dep)) {
          String fdep = fixPackageReference(dep);
          String coreVersion = VersionUtilities.getVersionForPackage(fdep);
          if (coreVersion != null) {
            log("Ignore Dependency on Core FHIR "+fdep+", from package '"+pi.name()+"#"+pi.version()+"'");
          } else {
            NpmPackage dpi = pcm.loadPackage(fdep);
            if (dpi == null) {
              logDebugMessage(LogCategory.CONTEXT, "Unable to find package dependency "+fdep+". Will proceed, but likely to be be errors in qa.html etc");
            } else {
              npmList.add(dpi);
              if (!VersionUtilities.versionsCompatible(version, pi.fhirVersion())) {
                if (!pi.isWarned()) {
                  errors.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, sourceIg.fhirType()+"/"+sourceIg.getId(), "This IG is for FHIR version "+version+", while the package '"+pi.name()+"#"+pi.version()+"' is for FHIR version "+pi.fhirVersion(), IssueSeverity.ERROR));
                  log("Version mismatch. This IG is for FHIR version "+version+", while the package '"+pi.name()+"#"+pi.version()+"' is for FHIR version "+pi.fhirVersion()+" (will ignore that and try to run anyway)");
                  pi.setWarned(true);
                }
              }
              SpecMapManager smm = null;
              logDebugMessage(LogCategory.PROGRESS, "Load package dependency "+fdep);
              try {
                smm = dpi.hasFile("other", "spec.internals") ?  new SpecMapManager(FileUtilities.streamToBytes(dpi.load("other", "spec.internals")), dpi.vid(), dpi.fhirVersion()) : SpecMapManager.createSpecialPackage(dpi, pcm);
                smm.setName(dpi.name()+"_"+dpi.version());
                smm.setBase(dpi.canonical());
                smm.setBase2(PackageHacker.fixPackageUrl(dpi.url()));
                smm.setNpm(pi);
                specMaps.add(smm);
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
    IContextResourceLoader loader = new PublisherLoader(pi, igm, webref, igpkp).makeLoader();
    context.loadFromPackage(pi, loader);
    return loader;
  }

private String fixPackageReference(String dep) {
    String id = dep.substring(0, dep.indexOf("#"));
    String ver = dep.substring(dep.indexOf("#")+1);
    if ("hl7.fhir.uv.extensions".equals(id)) {
       if (VersionUtilities.isR3Ver(version)) {
        id = "hl7.fhir.uv.extensions.r3";
      } else if (VersionUtilities.isR4Ver(version) || VersionUtilities.isR4BVer(version)) {
        id = "hl7.fhir.uv.extensions.r4";
      } else if (VersionUtilities.isR5Ver(version)) {
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

//  private void loadIg(JsonObject dep, boolean loadDeps) throws Exception {
//    String name = str(dep, "name");
//    if (!isValidIGToken(name))
//      throw new Exception("IG Name must be a valid token ("+name+")");
//    String canonical = ostr(dep, "location");
//    String igver = ostr(dep, "version");
//    if (Utilities.noString(igver))
//      throw new Exception("You must specify a version for the IG "+name+" ("+canonical+")");
//    String packageId = ostr(dep, "package");
//    if (Utilities.noString(packageId))
//      packageId = pcm.getPackageId(canonical);
//    if (Utilities.noString(canonical) && !Utilities.noString(packageId))
//      canonical = pcm.getPackageUrl(packageId);
//
//    NpmPackage pi = packageId == null ? null : pcm.loadPackageFromCacheOnly(packageId, igver);
//    if (pi != null)
//      npmList.add(pi);
//    if (pi == null) {
//      if (Utilities.noString(canonical))
//        throw new Exception("You must specify a canonical URL for the IG "+name);
//      pi = resolveDependency(canonical, packageId, igver);
//      if (pi == null) {
//        if (Utilities.noString(packageId))
//          throw new Exception("Package Id for guide at "+canonical+" is unknown (contact FHIR Product Director");
//        else
//          throw new Exception("Unknown Package "+packageId+"#"+igver);
//      }
//    }
//    if (packageId == null) {
//      packageId = pi.name();
//    }
//    if (Utilities.noString(canonical)) {
//      canonical = pi.canonical();
//    }
//
//    log("Load "+name+" ("+canonical+") from "+packageId+"#"+igver);
//    if (ostr(dep, "package") == null && packageId != null)
//      dep.add("package", packageId);
//
//    String webref = pi.getWebLocation();
//    String location = dep.has("location") ? dep.asString("location") : ""; 
//    if (location.startsWith(".."))
//      webref = location;
//    webref = PackageHacker.fixPackageUrl(webref);
//
//    String ver = pi.fhirVersion();
//    SpecMapManager igm = new SpecMapManager(TextFile.streamToBytes(pi.load("other", "spec.internals")), pi.vid(), ver);
//    igm.setName(name);
//    igm.setBase2(PackageHacker.fixPackageUrl(webref));
//    igm.setBase(canonical);
//    specMaps.add(igm);
//    if (!VersionUtilities.versionsCompatible(version, igm.getVersion())) {
//      if (pi.isWarned()) {
//        log("Version mismatch. This IG is for FHIR version "+version+", while the IG '"+pi.name()+"#"+pi.version()+"' is for FHIR version "+igm.getVersion()+" (will try to run anyway)");
//        pi.setWarned(true);
//      }
//    }
//
//    loadFromPackage(name, canonical, pi, webref, igm, loadDeps);
//    jsonDependencies .add(new JsonDependency(name, canonical, pi.name(), pi.version()));
//  }


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
    for (PackageListEntry e : pl.versions()) {
      if (igver.equals(e.version())) {
        InputStream src = fetchFromSource(pl.pid()+"-"+igver, Utilities.pathURL(e.path(), "package.tgz"));
        return pcm.addPackageToCache(pl.pid(), igver, src, Utilities.pathURL(e.path(), "package.tgz"));
      }
    }
    return null;
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
      FileUtilities.createDirectory(dir);
    else if (!f.isDirectory())
      throw new Exception(String.format("Error: Output must be a folder (%s)", dir));
  }

  private boolean checkMakeFile(byte[] bs, String path, Set<String> outputTracker) throws IOException {
    // logDebugMessage(LogCategory.GENERATE, "Check Generate "+path);
    String s = path.toLowerCase();
    if (allOutputs.contains(s))
      throw new Error("Error generating build: the file "+path+" is being generated more than once (may differ by case)");
    allOutputs.add(s);
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
    SpecMapManager map = new SpecMapManager(bs, npm.vid(), version);
    map.setBase(PackageHacker.fixPackageUrl(path));
    map.setName(name);
    specMaps.add(map);
    return map;
  }
  
  
  private void load() throws Exception {
    validationFetcher.initOtherUrls();
    fileList.clear();
    changeList.clear();
    bndIds.clear();

    FetchedFile igf = fetcher.fetch(igName);
    noteFile(IG_NAME, igf);
    if (sourceIg == null) // old JSON approach
      sourceIg = (ImplementationGuide) parse(igf);
    if (isNewML()) {
      log("Load Translations");
      sourceIg.setLanguage(defaultTranslationLang);
      // but we won't load the translations yet - it' yet to be fully populated. we'll wait till everything else is loaded
    }
    log("Load Content");
    publishedIg = sourceIg.copy();
    FetchedResource igr = igf.addResource("$IG");
    //      loadAsElementModel(igf, igr, null);
    igr.setResource(publishedIg);
    igr.setElement(convertToElement(null, publishedIg));
    igr.setId(sourceIg.getId()).setTitle(publishedIg.getName());
    Locale locale = inferDefaultNarrativeLang(true);
    context.setLocale(locale);
    dependentIgFinder = new DependentIGFinder(sourceIg.getPackageId());

    for (ImplementationGuideDependsOnComponent dep : publishedIg.getDependsOn()) {
      if (dep.hasPackageId() && dep.getPackageId().contains("@npm:")) {
        if (!dep.hasId()) {
          dep.setId(dep.getPackageId().substring(0, dep.getPackageId().indexOf("@npm:")));
        }
        dep.setPackageId(dep.getPackageId().substring(dep.getPackageId().indexOf("@npm:")+5));
        dep.getPackageIdElement().setUserData(UserDataNames.IG_DEP_ALIASED, true); 
      }
    }

    loadMappingSpaces(context.getBinaryForKey("mappingSpaces.details"));
    validationFetcher.getMappingUrls().addAll(mappingSpaces.keySet());
    validationFetcher.getOtherUrls().add(publishedIg.getUrl());
    for (SpecMapManager s :  specMaps) {
      validationFetcher.getOtherUrls().add(s.getBase());
      if (s.getBase2() != null) {
        validationFetcher.getOtherUrls().add(s.getBase2());
      }
    }

    if (npmName == null) {
      throw new Exception("A package name (npm-name) is required to publish implementation guides. For further information, see http://wiki.hl7.org/index.php?title=FHIR_NPM_Package_Spec#Package_name");
    }
    if (!publishedIg.hasLicense())
      publishedIg.setLicense(licenseAsEnum());
    if (!publishedIg.hasPackageId())
      publishedIg.setPackageId(npmName);
    if (!publishedIg.hasFhirVersion())
      publishedIg.addFhirVersion(FHIRVersion.fromCode(version));
    if (!publishedIg.hasVersion() && businessVersion != null)
      publishedIg.setVersion(businessVersion);
    if (!publishedIg.hasExtension(ToolingExtensions.EXT_WORKGROUP) && wgm != null) {
      publishedIg.addExtension(ToolingExtensions.EXT_WORKGROUP, new CodeType(wgm));
    }

    if (!VersionUtilities.isSemVer(publishedIg.getVersion())) {
      if (mode == IGBuildMode.AUTOBUILD) {
        throw new Error("The version "+publishedIg.getVersion()+" is not a valid semantic version so cannot be published in the ci-build");
      } else {
        log("The version "+publishedIg.getVersion()+" is not a valid semantic version so cannot be published in the ci-build");
        igf.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, "ImplementationGuide.version", "The version "+publishedIg.getVersion()+" is not a valid semantic version and will not be acceptible to the ci-build, nor will it be a valid vesion in the NPM package system", IssueSeverity.WARNING));
      }
    }
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

    packageInfo = new PackageInformation(publishedIg.getPackageId(), publishedIg.getVersion(), context.getVersion(), new Date(), publishedIg.getName(), igpkp.getCanonical(), targetOutput); 

    // Cql Compile
    cql = new CqlSubSystem(npmList, binaryPaths, new CqlResourceLoader(version), this, context.getUcumService(), publishedIg.getPackageId(), igpkp.getCanonical());
    if (binaryPaths.size() > 0) {
      cql.execute();
    }
    fetcher.setRootDir(rootDir);
    loadedIds = new HashMap<>();
    duplicateInputResourcesDetected = false;
    loadCustomResources();
    
    if (sourceDir != null || igpkp.isAutoPath()) {
     loadResources(igf);
    }
    loadSpreadsheets(igf);
    loadMappings(igf);
    loadBundles(igf);
    loadTranslationSupplements(igf);

    context.getCutils().setMasterSourceNames(specMaps.get(0).getTargets());
    context.getCutils().setLocalFileNames(pageTargets());
    
    loadConformance1(true);
    for (String s : resourceFactoryDirs) {
      FileUtilities.clearDirectory(s);
    }
    if (!testDataFactories.isEmpty()) {
      processFactories(testDataFactories);
    }
    loadResources2(igf);
    
    loadConformance1(false);
    
    int i = 0;
    Set<String> resLinks = new HashSet<>();
    for (ImplementationGuideDefinitionResourceComponent res : publishedIg.getDefinition().getResource()) {
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
      if (!bndIds.contains(res.getReference().getReference()) && !res.hasUserData(UserDataNames.pub_loaded_resource)) { 
        logDebugMessage(LogCategory.INIT, "Load "+res.getReference());
        f = fetcher.fetch(res.getReference(), igf);
        if (!f.hasTitle() && res.getName() != null)
          f.setTitle(res.getName());
        boolean rchanged = noteFile(res, f);        
        if (rchanged) {
          if (res.hasExtension(ToolingExtensions.EXT_BINARY_FORMAT_NEW)) {
            loadAsBinaryResource(f, f.addResource(f.getName()), res, res.getExtensionString(ToolingExtensions.EXT_BINARY_FORMAT_NEW), "listed in IG");
          } else if (res.hasExtension(ToolingExtensions.EXT_BINARY_FORMAT_OLD)) {
            loadAsBinaryResource(f, f.addResource(f.getName()), res, res.getExtensionString(ToolingExtensions.EXT_BINARY_FORMAT_OLD), "listed in IG");
          } else {
            loadAsElementModel(f, f.addResource(f.getContentType()), res, false, "listed in IG");
          }
          if (res.hasExtension(ToolingExtensions.EXT_BINARY_LOGICAL)) {
            f.setLogical(res.getExtensionString(ToolingExtensions.EXT_BINARY_LOGICAL));
          }
        }
      }
      if (res.hasProfile()) {
        if (f != null && f.getResources().size()!=1)
          throw new Exception("Can't have an exampleFor unless the file has exactly one resource");
        FetchedResource r = res.hasUserData(UserDataNames.pub_loaded_resource) ? (FetchedResource) res.getUserData(UserDataNames.pub_loaded_resource) : f.getResources().get(0);
        if (r == null)
          throw new Exception("Unable to resolve example canonical " + res.getProfile().get(0).asStringValue());
        examples.add(r);
        String ref = res.getProfile().get(0).getValueAsString();
        if (Utilities.isAbsoluteUrl(ref)) {
          r.setExampleUri(stripVersion(ref));
        } else {
          r.setExampleUri(Utilities.pathURL(igpkp.getCanonical(), ref));
        }
        // Redo this because we now have example information
        if (f!=null)
          igpkp.findConfiguration(f, r);
      }
      // TestPlan Check
      if (res.hasReference() && res.getReference().hasReference() && res.getReference().getReference().contains("TestPlan/")) {
        if (f == null) {
          f = fetcher.fetch(res.getReference(), igf);
        }
        if (f != null) {
          FetchedResource r = res.hasUserData(UserDataNames.pub_loaded_resource) ? (FetchedResource) res.getUserData(UserDataNames.pub_loaded_resource) : f.getResources().get(0);
          if (r != null) {
            testplans.add(r);
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
              errors.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, r.fhirType()+"/"+r.getId(), "Unable to load TestPlan resource " + r.getUrlTail(), IssueSeverity.ERROR));
            }
          }
        }
      }
      // TestScript Check
      if (res.hasReference() && res.getReference().hasReference() && res.getReference().getReference().contains("TestScript/")) {
        if (f == null) {
          f = fetcher.fetch(res.getReference(), igf);
        }
        if (f != null) {
          FetchedResource r = res.hasUserData(UserDataNames.pub_loaded_resource) ? (FetchedResource) res.getUserData(UserDataNames.pub_loaded_resource) : f.getResources().get(0);
          if (r != null) {
            testscripts.add(r);
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
              errors.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, r.fhirType()+"/"+r.getId(), "Unable to load test resource " + r.getUrlTail(), IssueSeverity.ERROR));
            }
          }
        }
      }
    }
    if (duplicateInputResourcesDetected) {
      throw new Error("Unable to continue because duplicate input resources were identified");
    }

    loadConformance1(false);
    for (PageFactory pf : pageFactories) {
      pf.execute(rootDir, publishedIg);
    }

    // load static pages
    loadPrePages();
    loadPages();

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
    npm = new NPMPackageGenerator(publishedIg.getPackageId(), Utilities.path(outputDir, "package.tgz"), igpkp.getCanonical(), targetUrl(), PackageType.IG,  publishedIg, execTime.getTime(), relatedIgMap(), !publishing);
    for (String v : generateVersions) {
      ImplementationGuide vig = publishedIg.copy();
      checkIgDeps(vig, v);
      vnpms.put(v, new NPMPackageGenerator(publishedIg.getPackageId()+"."+v, Utilities.path(outputDir, publishedIg.getPackageId()+"."+v+".tgz"), 
          igpkp.getCanonical(), targetUrl(), PackageType.IG,  vig, execTime.getTime(), relatedIgMap(), !publishing, VersionUtilities.versionFromCode(v)));
    }
    if (isNewML()) {
      for (String l : allLangs()) {
        ImplementationGuide vig = (ImplementationGuide) langUtils.copyToLanguage(publishedIg, l, true);
        lnpms.put(l, new NPMPackageGenerator(publishedIg.getPackageId()+"."+l, Utilities.path(outputDir, publishedIg.getPackageId()+"."+l+".tgz"), 
            igpkp.getCanonical(), targetUrl(), PackageType.IG, vig, execTime.getTime(), relatedIgMap(), !publishing, context.getVersion()));
      }
    }
    execTime = Calendar.getInstance();

    rc = new RenderingContext(context, markdownEngine, ValidationOptions.defaults(), checkAppendSlash(specPath), "", locale, ResourceRendererMode.TECHNICAL, GenerationRules.IG_PUBLISHER);
    rc.setTemplateProvider(templateProvider);
    rc.setResolver(this);    
    rc.setServices(validator.getExternalHostServices());
    rc.setDestDir(Utilities.path(tempDir));
    rc.setProfileUtilities(new ProfileUtilities(context, new ArrayList<ValidationMessage>(), igpkp));
    rc.setQuestionnaireMode(QuestionnaireRendererMode.TREE);
    rc.getCodeSystemPropList().addAll(codeSystemProps);
    rc.setParser(getTypeLoader(version));
    rc.addLink(KnownLinkType.SELF, targetOutput);
    rc.setFixedFormat(fixedFormat);
    rc.setResolveLinkResolver(this);
    rc.setDebug(debug);
    module.defineTypeMap(rc.getTypeMap());
    rc.setDateFormatString(fmtDate);
    rc.setDateTimeFormatString(fmtDateTime);
    rc.setChangeVersion(versionToAnnotate);
    rc.setShowSummaryTable(false);
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() instanceof CanonicalResource) {
          CanonicalResource cr = (CanonicalResource) r.getResource();
          rc.getNamedLinks().put(cr.getName(), new StringPair(cr.getWebPath(), cr.present()));
          rc.getNamedLinks().put(cr.getUrl(), new StringPair(cr.getWebPath(), cr.present()));
          rc.getNamedLinks().put(cr.getVersionedUrl(), new StringPair(cr.getWebPath(), cr.present()));
        }
      }
    }
    signer = new PublisherSigner(context, rootDir, rc.getTerminologyServiceOptions());
    rcLangs = new RenderingContextLangs(rc);
    for (String l : allLangs()) {
      RenderingContext lrc = rc.copy(false);
      lrc.setLocale(Locale.forLanguageTag(l));
      rcLangs.seeLang(l, lrc);
    }
    r4tor4b = new R4ToR4BAnalyser(rc, isNewML());
    if (context != null) {
      r4tor4b.setContext(context);
    }
    realmRules = makeRealmBusinessRules();
    previousVersionComparator = makePreviousVersionComparator();
    ipaComparator = makeIpaComparator();
    ipsComparator = makeIpsComparator();
    //    rc.setTargetVersion(pubVersion);

    if (igMode) {
      boolean failed = false;
      CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
      // sanity check: every specified resource must be loaded, every loaded resource must be specified
      for (ImplementationGuideDefinitionResourceComponent r : publishedIg.getDefinition().getResource()) {
        b.append(r.getReference().getReference());
        if (!r.hasUserData(UserDataNames.pub_loaded_resource)) {
          log("Resource "+r.getReference().getReference()+" not loaded");
          failed = true;
        }
      }  
      for (FetchedFile f : fileList) {
        f.start("load-configure");
        try {
          for (FetchedResource r : f.getResources()) {
            ImplementationGuideDefinitionResourceComponent rg = findIGReference(r.fhirType(), r.getId());
            if (!"ImplementationGuide".equals(r.fhirType()) && rg == null) {
              log("Resource "+r.fhirType()+"/"+r.getId()+" not defined");
              failed = true;
            }
            if (rg != null) {
              if (r.getElement().hasExtension(ToolingExtensions.EXT_RESOURCE_NAME)) {
                rg.setName(r.getElement().getExtensionValue(ToolingExtensions.EXT_RESOURCE_NAME).primitiveValue()); 
                r.getElement().removeExtension(ToolingExtensions.EXT_RESOURCE_NAME);
              } else if (r.getElement().hasExtension(ToolingExtensions.EXT_ARTIFACT_NAME)) {
                rg.setName(r.getElement().getExtensionValue(ToolingExtensions.EXT_ARTIFACT_NAME).primitiveValue());                 
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
              if (r.getElement().hasExtension(ToolingExtensions.EXT_RESOURCE_DESC)) {
                rg.setDescription(r.getElement().getExtensionValue(ToolingExtensions.EXT_RESOURCE_DESC).primitiveValue()); 
                r.getElement().removeExtension(ToolingExtensions.EXT_RESOURCE_DESC);
              } else if (r.getElement().hasExtension(ToolingExtensions.EXT_ARTIFACT_DESC)) {
                rg.setDescription(r.getElement().getExtensionValue(ToolingExtensions.EXT_ARTIFACT_DESC).primitiveValue());                 
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
                String descNew = ProfileUtilities.processRelativeUrls(desc, "", igpkp.specPath(), context.getResourceNames(), specMaps.get(0).getTargets(), pageTargets(), false);
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
                  if (p.startsWith(igpkp.getCanonical()+"/StructureDefinition")) {
                    rg.getProfile().add(new CanonicalType(p));
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
        } finally {
          f.finish("load-configure");      
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
      for (FetchedResource r : f.getResources()) {
        logDebugMessage(LogCategory.INIT, "    "+r.fhirType()+"/"+r.getId());
      }
      
    }

    if (isNewML()) {
      List<TranslationUnit> translations = findTranslations(publishedIg.fhirType(), publishedIg.getId(), igf.getErrors());
      if (translations != null) {
        langUtils.importFromTranslations(publishedIg, translations, igf.getErrors());
      }
    }
    Map<String, String> ids = new HashMap<>();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (isBasicResource(r)) {
          if (ids.containsKey(r.getId())) {
            f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.DUPLICATE, r.fhirType(), "Because this resource is converted to a Basic resource in the package, its id clashes with "+ids.get(r.getId())+". One of them will need a different id.", IssueSeverity.ERROR));   
          }
          ids.put(r.getId(), r.fhirType()+"/"+r.getId()+" from "+f.getPath());
        }
      }
    }
    extensionTracker.scan(publishedIg);
    finishLoadingCustomResources();

  }

  private boolean isBasicResource(FetchedResource r) {
    return "Basic".equals(r.fhirType())|| Utilities.existsInList(r.fhirType(), VersionUtilities.isR4BVer(context.getVersion()) ? SpecialTypeHandler.SPECIAL_TYPES_4B : SpecialTypeHandler.SPECIAL_TYPES_OTHER);
  }

  private void checkIgDeps(ImplementationGuide vig, String ver) {
    if ("r4b".equals(ver)) {
      ver = "r4";
    }
    String ov = VersionUtilities.getNameForVersion(context.getVersion()).toLowerCase();
    for (ImplementationGuideDependsOnComponent dep : vig.getDependsOn()) {
      if (dep.getPackageId().endsWith("."+ov) ) {
        dep.setPackageId(dep.getPackageId().replace("."+ov, "."+ver));
      }
    }
  }

  private Map<String, String> relatedIgMap() {
    if (relatedIGs.isEmpty()) {
      return null;
    }
    Map<String, String> map = new HashMap<>();
    for (RelatedIG ig : relatedIGs) {
      if (ig.getVersion() != null) {
        map.put(ig.getId(), ig.getVersion());
      }
    }
    return map;
  }
  

  private void finishLoadingCustomResources() {
    for (StructureDefinition sd : customResources) {
      FetchedResource r = findLoadedStructure(sd);
      if (r == null) {
        System.out.println("Custom Resource "+sd.getId()+" not loaded normally");
        System.exit(1); 
      } else {
        sd.setWebPath(igpkp.getDefinitionsName(r));
        // also mark this as a custom resource
        r.getResource().setUserData(UserDataNames.loader_custom_resource, "true");
      }
    }
  }

  private FetchedResource findLoadedStructure(StructureDefinition sd) {
    for (var f : fileList) {
      for (var r : f.getResources()) {
        if (r.fhirType().equals("StructureDefinition") && r.getId().equals(sd.getId())) {
          return r;
        }
      }
    }
    return null;
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
    for (StructureDefinition sd : context.fetchResourcesByType(StructureDefinition.class)) {
      if (sd.getKind() == StructureDefinitionKind.RESOURCE && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
        String scope = sd.getUrl().substring(0, sd.getUrl().lastIndexOf("/"));
        if (!"http://hl7.org/fhir/StructureDefinition".equals(scope)) {
          customResourceNames.add(sd.getTypeTail());
        }
      }
    }
    // look for new custom resources in this IG
    for (String s : customResourceFiles) {
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
      def = (StructureDefinition) org.hl7.fhir.r5.formats.FormatUtilities.loadFile(Utilities.uncheckedPath(FileUtilities.getDirectoryForFile(configFile), filename));
    } catch (Exception e) {
      return "Exception loading: "+e.getMessage();
    }
    
    if (approvedIgsForCustomResources == null) {
      try {
        approvedIgsForCustomResources = org.hl7.fhir.utilities.json.parser.JsonParser.parseObjectFromUrl("https://fhir.github.io/ig-registry/igs-approved-for-custom-resource.json");
      } catch (Exception e) {
        approvedIgsForCustomResources = new JsonObject();
        return "Exception checking IG status: "+e.getMessage();
      }
    }
    // checks
    // we'll validate it properly later. For now, we want to know:
    // 1. is this IG authorized to define custom resources?
    if (!approvedIgsForCustomResources.asBoolean(npmName)) {
      return "This IG is not authorised to define custom resources";
    }
    // 2. is this in the namespace of the IG (no flex there)
    if (!def.getUrl().startsWith(igpkp.getCanonical())) {
      return "The URL of this definition is not in the proper canonical URL space of the IG ("+igpkp.getCanonical()+")";
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
    if (def.getDerivation() == TypeDerivationRule.CONSTRAINT) {
      return "This definition is not a specialization, so ignored";
    }
    
    if (def.getKind() == StructureDefinitionKind.LOGICAL) {
      def.setKind(StructureDefinitionKind.RESOURCE);
    }
    if (def.getKind() != StructureDefinitionKind.RESOURCE) {
      return "This definition does not describe a resource";
    }
    String ot = def.getType();
    if (def.getType().contains(":/")) {
      def.setType(tail(def.getType()));
    }
    // right, passed all the tests
    customResourceNames.add(def.getType());
    customResources.add(def);
    def.setUserData(UserDataNames.loader_custom_resource, "true");
    def.setWebPath("placeholder.html"); // we'll figure it out later
    context.cacheResource(def); 

    // work around for a sushi limitation 
    for (ImplementationGuideDefinitionResourceComponent res : publishedIg.getDefinition().getResource()) {
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

  private void loadTranslationSupplements(FetchedFile igf) throws Exception {
    for (String p : translationSources) {
      File dir = new File(Utilities.path(rootDir, p));
      FileUtilities.createDirectory(dir.getAbsolutePath());
      for (File f : dir.listFiles()) {
        if (!usedLangFiles.contains(f.getAbsolutePath())) {
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
        CanonicalResource cr = (CanonicalResource) context.fetchResourceById(rtype, id);
        if (cr == null) {
          System.out.println("Ignoring file "+f.getAbsolutePath()+" - the resource "+rtype+"/"+id+" is not known");
        } else {
          FetchedFile ff = new FetchedFile(f.getAbsolutePath().substring(rootDir.length()+1));
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
          List<TranslationUnit> list = loadTranslations(f, ext);
          langUtils.fillSupplement(csSrc, csDst, list);
          FetchedResource rr = ff.addResource("CodeSystemSupplement");
          rr.setElement(convertToElement(rr, csDst));
          rr.setResource(csDst);          
          rr.setId(csDst.getId());
          rr.setTitle(csDst.getName());
          igpkp.findConfiguration(ff, rr);
          for (FetchedResource r : ff.getResources()) {
            ImplementationGuideDefinitionResourceComponent res = findIGReference(r.fhirType(), r.getId());
            if (res == null) {
              res = publishedIg.getDefinition().addResource();
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
    String id = "cs-"+defaultTranslationLang+"-"+res.getId();
    CodeSystem supplement = new CodeSystem();
    supplement.setLanguage(content ? "en" : defaultTranslationLang); // base is EN? 
    supplement.setId(id);
    supplement.setUrl(Utilities.pathURL(igpkp.getCanonical(), "CodeSystem", id));
    supplement.setVersion(res.getVersion());
    supplement.setStatus(res.getStatus());
    supplement.setContent(CodeSystemContentMode.SUPPLEMENT);
    supplement.setSupplements(res.getUrl());
    supplement.setCaseSensitive(false);
    supplement.setPublisher(sourceIg.getPublisher());
    supplement.setContact(sourceIg.getContact());
    supplement.setCopyright(sourceIg.getCopyright());

    supplement.setName(res.getName());
    supplement.setTitle(res.getTitle());
    supplement.setPublisher(res.getPublisher());
    supplement.setPurpose(res.getPurpose());
    supplement.setDescription(res.getDescription());
    supplement.setCopyright(res.getCopyright());
    
    if (content) {
      if (res instanceof CodeSystem) {
        CodeSystem cs = (CodeSystem) res;
        for (ConceptDefinitionComponent cd : cs.getConcept()) {
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
        for (QuestionnaireItemComponent item : q.getItem()) {
          addItem(supplement, item, null);
        }
      }
    }
    return supplement;
  }

  private void cloneConcept(List<ConceptDefinitionComponent> dest, ConceptDefinitionComponent source) {
    // we clone everything translatable but the child concepts (need to flatten the hierarchy if there is one so we 
    // can filter it later 

    ConceptDefinitionComponent clone = new ConceptDefinitionComponent();
    clone.setCode(source.getCode()); 
    dest.add(clone);
    clone.setDisplay(source.getDisplay());
    clone.setDefinition(source.getDefinition());
    for (ConceptDefinitionDesignationComponent d : source.getDesignation()) {
      if (wantToTranslate(d)) {
        clone.addDesignation(d.copy());
      }
    }
    for (Extension ext : source.getExtension()) {
      if (ext.hasValue() && Utilities.existsInList(ext.getValue().fhirType(), "string", "markdown")) {
        clone.addExtension(ext.copy());
      }
    }

    for (ConceptDefinitionComponent cd : source.getConcept()) {
      cloneConcept(dest, cd);
    }
  }

  private boolean wantToTranslate(ConceptDefinitionDesignationComponent d) {
    return !d.hasLanguage() && d.hasUse(); // todo: only if the source language is the right language?
  }

  private void addItem(CodeSystem supplement, QuestionnaireItemComponent item, QuestionnaireItemComponent parent) {
    addConcept(supplement, item.getLinkId(), item.getText(), parent == null ? null : parent.getTextElement());   
    addConcept(supplement, item.getLinkId()+"@prefix", item.getPrefix(), item.getTextElement());   
    for (QuestionnaireItemAnswerOptionComponent ao : item.getAnswerOption()) {
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
    for (QuestionnaireItemInitialComponent ao : item.getInitial()) {
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
    for (QuestionnaireItemComponent child : item.getItem()) {
      addItem(supplement, child, item);
    }
  }

  private void copyConcepts(ConceptDefinitionComponent tgt, ConceptDefinitionComponent src, CodeSystem supplement) {
    for (ConceptDefinitionComponent cd : src.getConcept()) {
      ConceptDefinitionComponent clone = tgt.addConcept().setCode(cd.getCode()).setDisplay(cd.getDisplay());
      // don't create this - it's just admin overhead
      // CodeSystemUtilities.setProperty(supplement, clone, "translation-context", cd.getDefinitionElement());
      copyConcepts(clone, cd, supplement);
    }
  }

  private void addConcept(CodeSystem supplement, String code, String display, DataType context) {
    if (display != null) {
      ConceptDefinitionComponent cs = supplement.addConcept().setCode(code).setDisplay(display.replace("\r", "\\r").replace("\n", "\\n"));
      if (context != null) {
        // don't create this - it's just admin overhead
        //  CodeSystemUtilities.setProperty(supplement, cs, "translation-context", context);
      }
    }    
  }

  private void addConcept(CodeSystem supplement, String code, String display) {
    if (display != null) {
      ConceptDefinitionComponent cs = supplement.addConcept().setCode(code).setDisplay(display.replace("\r", "\\r").replace("\n", "\\n"));
    }    
  }

  private void addConcept(CodeSystem supplement, String code, String display, String context) {
    if (display != null) {
      ConceptDefinitionComponent cs = supplement.addConcept().setCode(code).setDisplay(display.replace("\r", "\\r").replace("\n", "\\n"));
      if (context != null) {
         // don't create this - it's just admin overhead
        // CodeSystemUtilities.setProperty(supplement, cs, "translation-context", new StringType(context));
      }
    }    
  }

  private List<TranslationUnit> loadTranslations(File f, String ext) throws FileNotFoundException, IOException, ParserConfigurationException, SAXException {
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
      logMessage("Run Template");
      Session tts = tt.start("template");
      List<String> newFileList = new ArrayList<String>();
      checkOutcomes(template.beforeGenerateEvent(publishedIg, tempDir, otherFilesRun, newFileList, allLangs()));
      for (String newFile: newFileList) {
        if (!newFile.isEmpty()) {
          try {
            FetchedFile f = fetcher.fetch(Utilities.path(repoRoot, newFile));
            String dir = FileUtilities.getDirectoryForFile(f.getPath());
            if (tempDir.startsWith("/var") && dir.startsWith("/private/var")) {
              dir = dir.substring(8);
            }
            String relative = tempDir.length() > dir.length() ? "" : dir.substring(tempDir.length());
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
      igPages.clear();
      if (publishedIg.getDefinition().hasPage()) {
        loadIgPages(publishedIg.getDefinition().getPage(), igPages);
      }
      tts.end();
      logDebugMessage(LogCategory.PROGRESS, "Template Done");
      if (debug) {
        waitForInput("after OnGenerate");
      }
    }
    cleanUpExtensions(publishedIg);
  }

  private void cleanUpExtensions(ImplementationGuide ig) {
    ToolingExtensions.removeExtension(ig.getDefinition(), ToolingExtensions.EXT_IGP_SPREADSHEET);
    ToolingExtensions.removeExtension(ig.getDefinition(), ToolingExtensions.EXT_IGP_BUNDLE);
    ToolingExtensions.removeExtension(ig, ToolingExtensions.EXT_IGP_CONTAINED_RESOURCE_INFO); // - this is in contained resources somewhere, not the root of IG?  
    for (ImplementationGuideDefinitionResourceComponent r : ig.getDefinition().getResource())
      ToolingExtensions.removeExtension(r, ToolingExtensions.EXT_IGP_RESOURCE_INFO);
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
    if (page.hasName() && page.hasName())
      map.put(page.getName(), page);
    for (ImplementationGuideDefinitionPageComponent childPage: page.getPage()) {
      loadIgPages(childPage, map);
    }
  }

  private void loadPrePages() throws Exception {
    if (prePagesDirs.isEmpty())
      return;

    for (String prePagesDir : prePagesDirs) {
      FetchedFile dir = fetcher.fetch(prePagesDir);
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

    PreProcessInfo ppinfo = preProcessInfo.get(basePath);
    if (ppinfo==null) {
      throw new Exception("Unable to find preProcessInfo for basePath: " + basePath);
    }
    if (!altMap.containsKey("pre-page/"+dir.getPath())) {
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

  private void loadBundles(FetchedFile igf) throws Exception {
    for (String be : bundles) {
       loadBundle(be, igf, "listed as a bundle");
    }
  }

  private boolean loadBundle(String name, FetchedFile igf, String cause) throws Exception {
    FetchedFile f = fetcher.fetch(new Reference().setReference("Bundle/"+name), igf);
    boolean changed = noteFile("Bundle/"+name, f);
    if (changed) {
      f.setBundle(new FetchedResource(f.getName()+" (bundle)"));
      f.setBundleType(FetchedBundleType.NATIVE);
      loadAsElementModel(f, f.getBundle(), null, true, cause);
      List<Element> entries = new ArrayList<Element>();
      f.getBundle().getElement().getNamedChildren("entry", entries);
      int i = -1;
      for (Element bnde : entries) {
        i++;
        Element res = bnde.getNamedChild("resource"); 
        if (res == null) {
          f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, "Bundle.element["+i+"]", "All entries must have resources when loading a bundle", IssueSeverity.ERROR));
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
          igpkp.findConfiguration(f, r);
        }
      }
    } else
      f = altMap.get("Bundle/"+name);
    for (FetchedResource r : f.getResources()) {
      bndIds.add(r.fhirType()+"/"+r.getId());
      ImplementationGuideDefinitionResourceComponent res = findIGReference(r.fhirType(), r.getId());
      if (res == null) {
        res = publishedIg.getDefinition().addResource();
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
        if (!canonicalResources.containsKey(cr.getUrl())) {
          canonicalResources.put(cr.getUrl(), r);
          if (cr.hasVersion())
            canonicalResources.put(cr.getUrl()+"#"+cr.getVersion(), r);
        }
      }
    }
    return changed;
  }

  private boolean loadArchetype(FetchedFile f, String cause) throws Exception {
    ProcessedArchetype pa = new ArchetypeImporter(context, igpkp.getCanonical()).importArchetype(f.getSource(), new File(f.getStatedPath()).getName());
    Bundle bnd = pa.getBnd();
    pa.getSd().setUserData(UserDataNames.archetypeSource, pa.getSource());
    pa.getSd().setUserData(UserDataNames.archetypeName, pa.getSourceName());
    
    f.setBundle(new FetchedResource(f.getName()+" (bundle)"));
    f.setBundleType(FetchedBundleType.NATIVE);

    boolean changed = noteFile("Bundle/"+bnd.getIdBase(), f);
    int i = -1;
    for (BundleEntryComponent be : bnd.getEntry()) { 
      i++;
      Resource res = be.getResource();
      Element e = new ObjectConverter(context).convert(res);
      checkResourceUnique(res.fhirType()+"/"+res.getIdBase(), f.getName(), cause);
      FetchedResource r = f.addResource(f.getName()+"["+i+"]");
      r.setElement(e);
      r.setResource(res);
      r.setId(res.getIdBase());

      r.setTitle(r.getElement().getChildValue("name"));
      igpkp.findConfiguration(f, r);
    }
    for (FetchedResource r : f.getResources()) {
      bndIds.add(r.fhirType()+"/"+r.getId());
      ImplementationGuideDefinitionResourceComponent res = findIGReference(r.fhirType(), r.getId());
      if (res == null) {
        res = publishedIg.getDefinition().addResource();
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
        if (!canonicalResources.containsKey(cr.getUrl())) {
          canonicalResources.put(cr.getUrl(), r);
          if (cr.hasVersion())
            canonicalResources.put(cr.getUrl()+"#"+cr.getVersion(), r);
        }
      }
    }
    return changed;
  }

  private void loadResources(FetchedFile igf) throws Exception { // igf is not currently used, but it was about relative references? 
    List<FetchedFile> resources = fetcher.scan(sourceDir, context, igpkp.isAutoPath());
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
  
  private void loadResources2(FetchedFile igf) throws Exception {
    if (!resourceFactoryDirs.isEmpty()) {
      fetcher.setResourceDirs(resourceFactoryDirs);
      List<FetchedFile> resources = fetcher.scan(null, context, true);
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
    for (String s : bundles) {
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
      ImplementationGuideDefinitionResourceComponent res = findIGReference(r.fhirType(), r.getId());
      if (res == null) {
        res = publishedIg.getDefinition().addResource();
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


  private void loadMappings(FetchedFile igf) throws Exception {
    for (String s : mappings) {
      loadMapping(s, igf);
    }
  }

  private boolean loadMapping(String name, FetchedFile igf) throws Exception {
    if (name.startsWith("!"))
      return false;
    FetchedFile f = fetcher.fetchResourceFile(name);
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
      igpkp.findConfiguration(f, r);
    } else {
      f = altMap.get("Mapping/"+name);
    }
    return changed;
  }

  private void loadSpreadsheets(FetchedFile igf) throws Exception {
    Set<String> knownValueSetIds = new HashSet<>();
    for (String s : spreadsheets) {
      loadSpreadsheet(s, igf, knownValueSetIds, "listed as a spreadsheet");
    }
  }

  private boolean loadSpreadsheet(String name, FetchedFile igf, Set<String> knownValueSetIds, String cause) throws Exception {
    if (name.startsWith("!"))
      return false;

    FetchedFile f = fetcher.fetchResourceFile(name);
    boolean changed = noteFile("Spreadsheet/"+name, f);
    if (changed) {
      f.getValuesetsToLoad().clear();
      logDebugMessage(LogCategory.INIT, "load "+f.getPath());
      Bundle bnd = new IgSpreadsheetParser(context, execTime, igpkp.getCanonical(), f.getValuesetsToLoad(), mappingSpaces, knownValueSetIds).parse(f);
      f.setBundle(new FetchedResource(f.getName()+" (ex spreadsheet)"));
      f.setBundleType(FetchedBundleType.SPREADSHEET);
      f.getBundle().setResource(bnd);
      for (BundleEntryComponent b : bnd.getEntry()) {
        checkResourceUnique(b.getResource().fhirType()+"/"+b.getResource().getIdBase(), name, cause);
        FetchedResource r = f.addResource(f.getName());
        r.setResource(b.getResource());
        r.setId(b.getResource().getId());
        r.setElement(convertToElement(r, r.getResource()));
        r.setTitle(r.getElement().getChildValue("name"));
        igpkp.findConfiguration(f, r);
      }
    } else {
      f = altMap.get("Spreadsheet/"+name);
    }

    for (String id : f.getValuesetsToLoad().keySet()) {
      if (!knownValueSetIds.contains(id)) {
        String vr = f.getValuesetsToLoad().get(id);
        checkResourceUnique("ValueSet/"+id, name, cause);

        FetchedFile fv = fetcher.fetchFlexible(vr);
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
          if (fetcher.canFetchFlexible(cr)) {
            fv = fetcher.fetchFlexible(cr);
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
    if (!rurl.startsWith(igpkp.getCanonical()))
      throw new Exception("base/ resource url mismatch: "+igpkp.getCanonical()+" vs "+rurl);
  }


  private String tail(String url) {
    return url.substring(url.lastIndexOf("/")+1);
  }

  private String tailPI(String url) {
    int i = url.contains("\\") ? url.lastIndexOf("\\") : url.lastIndexOf("/");
    return url.substring(i+1);
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

  private void loadConformance1(boolean first) throws Exception {
    boolean any = false;
    for (FetchedFile f : fileList) {
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
      for (FetchedFile f : fileList) {
        f.setLoaded(true);
      }
    }
  }

  private void loadConformance2() throws Exception {
    for (String s : metadataResourceNames()) 
      scanUrls(s);
    log("Load Dependency Info");
    loadDepInfo();
    log("Load Info");
    loadInfo();
    log("Load Paths");
    loadPaths();

    log("Check R4 / R4B");
    checkR4R4B();
    generateOtherVersions();
    
    log("Assign Comparison Ids");
    assignComparisonIds();
    if (isPropagateStatus) {
      log("Propagating status");
      propagateStatus();
    }
    log("Generating Narratives");
    doActorScan();
    generateNarratives(false);
    if (!validationOff) {
      log("Validating Conformance Resources");
      for (String s : metadataResourceNames()) {
        validate(s);
      }
    }

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

  private void doActorScan() {
    for (FetchedFile f : fileList) {
      for (FetchedResource r: f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof StructureDefinition) {
          for (ElementDefinition ed : ((StructureDefinition) r.getResource()).getDifferential().getElement()) {
            for (Extension obd : ToolingExtensions.getExtensions(ed, ToolingExtensions.EXT_OBLIGATION_CORE)) {
              for (Extension act : ToolingExtensions.getExtensions(obd, "actor")) {
                ActorDefinition ad = context.fetchResource(ActorDefinition.class, act.getValue().primitiveValue());
                if (ad != null) {
                  rc.getActorWhiteList().add(ad);
                }
              }
            }
          }
        }

        if (r.getResource() != null && r.getResource() instanceof ActorDefinition) {
          rc.getActorWhiteList().add((ActorDefinition) r.getResource());
        }
      }
    }
  }

  private void assignComparisonIds() {
    int i = 0;
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() instanceof StructureDefinition) {            
          StructureDefinition sd = (StructureDefinition) r.getResource();
          for (Extension ext : sd.getExtensionsByUrl(ToolingExtensions.EXT_SD_IMPOSE_PROFILE)) {
            StructureDefinition sdi = context.fetchResource(StructureDefinition.class, ext.getValue().primitiveValue());
            if (sdi != null && !sdi.hasUserData(UserDataNames.pub_imposes_compare_id)) {
              String cid = "c"+Integer.toString(i);
              sdi.setUserData(UserDataNames.pub_imposes_compare_id, cid);
              i++;
            }
          }
        }
      }
    }
  }


  private void loadPaths() {
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (!r.getElement().hasWebPath()) {
          igpkp.checkForPath(f, r, r.getElement());
        }
      }
    }
  }

  private void validate(String type) throws Exception {
    for (FetchedFile f : fileList) {
      f.start("validate");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.fhirType().equals(type)) {
            logDebugMessage(LogCategory.PROGRESS, "validate res: "+r.fhirType()+"/"+r.getId());
            if (!r.isValidated()) {
              validate(f, r);
            }
            if (SpecialTypeHandler.handlesType(r.fhirType(), context.getVersion()) && !VersionUtilities.isR5Plus(version)) {
              // we validated the resource as it was supplied, but now we need to 
              // switch it for the correct representation in the underlying version
              byte[] cnt = null;
              if (VersionUtilities.isR3Ver(version)) {
                org.hl7.fhir.dstu3.model.Resource res = VersionConvertorFactory_30_50.convertResource(r.getResource());
                cnt = new org.hl7.fhir.dstu3.formats.JsonParser().setOutputStyle(org.hl7.fhir.dstu3.formats.IParser.OutputStyle.PRETTY).composeBytes(res);
              } else if (VersionUtilities.isR4Ver(version)) {
                org.hl7.fhir.r4.model.Resource res = VersionConvertorFactory_40_50.convertResource(r.getResource());
                cnt = new org.hl7.fhir.r4.formats.JsonParser().setOutputStyle(org.hl7.fhir.r4.formats.IParser.OutputStyle.PRETTY).composeBytes(res);
              } else if (VersionUtilities.isR4BVer(version)) {
                org.hl7.fhir.r4b.model.Resource res = VersionConvertorFactory_43_50.convertResource(r.getResource());
                cnt = new org.hl7.fhir.r4b.formats.JsonParser().setOutputStyle(org.hl7.fhir.r4b.formats.IParser.OutputStyle.PRETTY).composeBytes(res);
              } else {
                throw new Error("Cannot use resources of type "+r.fhirType()+" in a IG with version "+version);
              }
              Element e = new org.hl7.fhir.r5.elementmodel.JsonParser(context).parseSingle(new ByteArrayInputStream(cnt), null);
              e.copyUserData(r.getElement());
              r.setElement(e);
            } 
          }
        }
      } finally {
        f.finish("validate");      
      }
    }
  }

  private void loadInfo() {
    for (FetchedFile f : fileList) {
      f.start("loadInfo");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.getResEntry() != null) {
            if (r.getResource() instanceof StructureDefinition) {
              ToolingExtensions.setStringExtension(r.getResEntry(), ToolingExtensions.EXT_IGP_RESOURCE_INFO, r.fhirType()+":"+IGKnowledgeProvider.getSDType(r));
            } else {
              ToolingExtensions.setStringExtension(r.getResEntry(), ToolingExtensions.EXT_IGP_RESOURCE_INFO, r.fhirType()); 
            }
          }
        }
      } finally {
        f.finish("loadInfo");      
      }
    }
  }

  private void scanForUsageStats() {
    logDebugMessage(LogCategory.PROGRESS, "scanForUsageStats");
    for (FetchedFile f : fileList) {
      f.start("scanForUsageStats");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.fhirType().equals("StructureDefinition")) 
            extensionTracker.scan((StructureDefinition) r.getResource());
          extensionTracker.scan(r.getElement(), f.getName());
        }
      } finally {
        f.finish("scanForUsageStats");      
      }
    }
  }


  private void checkConformanceResources() throws IOException {
    log("Check profiles & code systems");
    for (FetchedFile f : fileList) {
      f.start("checkConformanceResources");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.fhirType().equals("StructureDefinition")) {
            logDebugMessage(LogCategory.PROGRESS, "process profile: "+r.getId());
            StructureDefinition sd = (StructureDefinition) r.getResource();
            if (sd == null) {
              f.getErrors().add(new ValidationMessage(Source.ProfileValidator,IssueType.INVALID, "StructureDefinition", "Unable to validate - Profile not loaded", IssueSeverity.ERROR));
            } else {
              f.getErrors().addAll(pvalidator.validate(sd, false));
              checkJurisdiction(f, (CanonicalResource) r.getResource(), IssueSeverity.ERROR, "must");
            }
          } else if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
            checkJurisdiction(f, (CanonicalResource) r.getResource(), IssueSeverity.WARNING, "should");
          }
          if (r.fhirType().equals("CodeSystem")) {
            logDebugMessage(LogCategory.PROGRESS, "process CodeSystem: "+r.getId());
            CodeSystem cs = (CodeSystem) r.getResource();
            if (cs != null) {
              f.getErrors().addAll(csvalidator.validate(cs, false));
            }
          }
        }
      } finally {
        f.finish("checkConformanceResources");      
      }
    }
    Session tts = tt.start("realm-rules");
    if (!realmRules.isExempt(publishedIg.getPackageId())) {
      log("Check realm rules");
      realmRules.startChecks(publishedIg);
      for (FetchedFile f : fileList) {
        f.start("checkConformanceResources2");
        try {
          for (FetchedResource r : f.getResources()) {
            if (r.fhirType().equals("StructureDefinition")) {
              StructureDefinition sd = (StructureDefinition) r.getResource();
              realmRules.checkSD(f, sd);
            } else if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
              realmRules.checkCR(f, (CanonicalResource) r.getResource());
            }
          }
        } finally {
          f.finish("checkConformanceResources2");      
        }
      }
      realmRules.finishChecks();
    }
    tts.end();
    log("Previous Version Comparison");
    tts = tt.start("previous-version");
    previousVersionComparator.startChecks(publishedIg);
    if (ipaComparator != null) {
      ipaComparator.startChecks(publishedIg);      
    }
    if (ipsComparator != null) {
      ipsComparator.startChecks(publishedIg);      
    }
    for (FetchedFile f : fileList) {
      f.start("checkConformanceResources3");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
            previousVersionComparator.check((CanonicalResource) r.getResource());
            if (ipaComparator != null) {
              ipaComparator.check((CanonicalResource) r.getResource());      
            }
            if (ipsComparator != null) {
              ipsComparator.check((CanonicalResource) r.getResource());      
            }
          }

        }
      } finally {
        f.finish("checkConformanceResources3");      
      }
    }
    previousVersionComparator.finishChecks();
    if (ipaComparator != null) {
      ipaComparator.finishChecks();      
    }
    if (ipsComparator != null) {
      ipsComparator.finishChecks();      
    }
    tts.end();
  }

  private RealmBusinessRules makeRealmBusinessRules() {
    if (expectedJurisdiction != null && expectedJurisdiction.getCode().equals("US")) {
      return new USRealmBusinessRules(context, version, tempDir, igpkp.getCanonical(), igpkp, rc);
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
    return new PreviousVersionComparator(context, version, businessVersion != null ? businessVersion : sourceIg == null ? null : sourceIg.getVersion(), rootDir, tempDir, igpkp.getCanonical(), igpkp, logger, comparisonVersions, versionToAnnotate, rc);
  }


  private IpaComparator makeIpaComparator() throws IOException {
    if (isTemplate()) {
      return null;
    }
    if (ipaComparisons == null) {
      return null;
    }
    return new IpaComparator(context, rootDir, tempDir, igpkp, logger, ipaComparisons, rc);
  }

  private IpsComparator makeIpsComparator() throws IOException {
    if (isTemplate()) {
      return null;
    }
    if (ipsComparisons == null) {
      return null;
    }
    return new IpsComparator(context, rootDir, tempDir, igpkp, logger, ipsComparisons, rc);
  }

  private void checkJurisdiction(FetchedFile f, CanonicalResource resource, IssueSeverity error, String verb) {
    if (expectedJurisdiction != null) {
      boolean ok = false;
      CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
      for (CodeableConcept cc : resource.getJurisdiction()) {
        ok = ok || cc.hasCoding(expectedJurisdiction);
        b.append(cc.toString());
      }
      if (!ok) {
        f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, resource.fhirType()+".jurisdiction", 
            "The resource "+verb+" declare its jurisdiction to match the package id ("+npmName+", jurisdiction = "+expectedJurisdiction.toString()+
            (Utilities.noString(b.toString()) ? "" : " instead of or as well as "+b.toString())+
            ") (for Sushi users: in sushi-config.yaml, 'jurisdiction: "+toFSH(expectedJurisdiction)+"')",
            error).setMessageId(PublisherMessageIds.RESOURCE_JURISDICTION_MISMATCH));
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
        f.start("executeTransforms");
        try {
          List<StructureMap> worklist = new ArrayList<StructureMap>();
          for (FetchedResource r : f.getResources()) {
            if (r.getResource() != null && r.getResource() instanceof StructureDefinition) {
              List<StructureMap> transforms = context.findTransformsforSource(((StructureDefinition) r.getResource()).getUrl());
              worklist.addAll(transforms);
            }
          }
          

          ProfileUtilities putils = new ProfileUtilities(context, null, igpkp);
          putils.setXver(context.getXVer());
          putils.setForPublication(true);
          putils.setMasterSourceFileNames(specMaps.get(0).getTargets());
          putils.setLocalFileNames(pageTargets());
          if (VersionUtilities.isR4Plus(version)) {
            putils.setNewSlicingProcessing(true);
          }
           

          for (StructureMap map : worklist) {
            StructureMapAnalysis analysis = utils.analyse(null, map);
            map.setUserData(UserDataNames.pub_analysis, analysis);
            for (StructureDefinition sd : analysis.getProfiles()) {
              FetchedResource nr = new FetchedResource(f.getName()+" (ex transform)");
              nr.setElement(convertToElement(nr, sd));
              nr.setId(sd.getId());
              nr.setResource(sd);
              nr.setTitle("Generated Profile (by Transform)");
              f.getResources().add(nr);
              igpkp.findConfiguration(f, nr);
              sd.setWebPath(igpkp.getLinkFor(nr, true));
              generateSnapshot(f, nr, sd, true, putils);
            }
          }
        } finally {
          f.finish("executeTransforms");      
        }
      }

      for (FetchedFile f : changeList) {
        f.start("executeTransforms2");
        try {
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
                FetchedResource nr = new FetchedResource(f.getName()+" (ex transform 2)");
                nr.setElement(convertToElement(nr, target));
                nr.setId(target.getId());
                nr.setResource(target);
                nr.setTitle("Generated Example (by Transform)");
                nr.setValidateAsResource(true);
                f.getResources().add(nr);
                igpkp.findConfiguration(f, nr);
              }
            }
          }
        } finally {
          f.finish("executeTransforms2");      
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
      for (FetchedFile f : fileList) {
        if (file.getPath().equals(f.getPath())) {
          throw new Error("Attempt to process the same source resource twice: "+file.getPath());
        }
      }
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
      for (FetchedFile f : fileList) {
        if (file.getPath().equals(f.getPath())) {
          throw new Error("Attempt to process the same source resource twice: "+file.getPath());
        }
      }
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

  private void loadAsBinaryResource(FetchedFile file, FetchedResource r, ImplementationGuideDefinitionResourceComponent srcForLoad, String format, String cause) throws Exception {
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
    Element e = new ObjectConverter(context).convert(bin);
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
    igpkp.findConfiguration(file, r);
    srcForLoad.setUserData(UserDataNames.pub_loaded_resource, r);
  }

  private void loadAsElementModel(FetchedFile file, FetchedResource r, ImplementationGuideDefinitionResourceComponent srcForLoad, boolean suppressLoading, String cause) throws Exception {
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
        if (!context.getResourceNamesAsSet().contains(e.fhirType())) {
          if (ToolingExtensions.readBoolExtension(e.getProperty().getStructure(), ToolingExtensions.EXT_LOAD_AS_RESOURCE)) {
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
            igpkp.findConfiguration(file, r);
            binary = false;
          } else {
            id = new File(file.getPath()).getName();
            id = Utilities.makeId(id.substring(0, id.lastIndexOf(".")));
            // are we going to treat it as binary, or something else? 
            checkResourceUnique("Binary/"+id, file.getPath(), cause);
            r.setElement(e).setId(id).setType("Binary");
            igpkp.findConfiguration(file, r);
            binary = true;
          }
        } else {
          id = e.getChildValue("id");

          if (Utilities.noString(id)) {
            if (e.hasChild("url")) {
              String url = e.getChildValue("url");
              String prefix = Utilities.pathURL(igpkp.getCanonical(), e.fhirType())+"/";
              if (url.startsWith(prefix)) {
                id = e.getChildValue("url").substring(prefix.length());
                e.setChildValue("id", id);
                altered = true;
              } 
              prefix = Utilities.pathURL(altCanonical, e.fhirType())+"/";
              if (url.startsWith(prefix)) {
                id = e.getChildValue("url").substring(prefix.length());
                e.setChildValue("id", id);
                altered = true;
              } 
              if (Utilities.noString(id)) {
                if (simplifierMode) {
                  id = file.getName();
                  System.out.println("Resource has no id in "+file.getPath()+" and canonical URL ("+url+") does not start with the IG canonical URL ("+prefix+")");
                } else {
                  throw new Exception("Resource has no id in "+file.getPath()+" and canonical URL ("+url+") does not start with the IG canonical URL ("+prefix+")");
                }
              }
            } else {
              id = tail(file.getName());
            }
            e.setChildValue("id", id);
            altered = true;
          }
          if (!Utilities.noString(e.getIdBase())) {
            checkResourceUnique(e.fhirType()+"/"+e.getIdBase(), file.getPath(), cause);
          }
          r.setId(id);
          r.setElement(e);
          igpkp.findConfiguration(file, r);
        }
        if (!suppressLoading) {
          if (srcForLoad == null)
            srcForLoad = findIGReference(r.fhirType(), r.getId());
          if (srcForLoad == null && !"ImplementationGuide".equals(r.fhirType())) {
            srcForLoad = publishedIg.getDefinition().addResource();
            srcForLoad.getReference().setReference(r.fhirType()+"/"+r.getId());
          } 
        }

        String ver = ToolingExtensions.readStringExtension(srcForLoad, ToolingExtensions.EXT_IGP_LOADVERSION); 
        if (ver == null)
          ver = r.getConfig() == null ? null : ostr(r.getConfig(), "version");
        if (ver == null)
          ver = version; // fall back to global version

        // version check: for some conformance resources, they may be saved in a different version from that stated for the IG. 
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
          srcForLoad.setUserData(UserDataNames.pub_loaded_resource, r);
          r.setResEntry(srcForLoad);
          if (srcForLoad.hasProfile()) {
            r.getElement().setUserData(UserDataNames.map_profile, srcForLoad.getProfile().get(0).getValue());
            r.getStatedProfiles().add(stripVersion(srcForLoad.getProfile().get(0).getValue()));
          } else {
            String profile = factoryProfileMap.get(file.getName());
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
        if (new AdjunctFileLoader(binaryPaths, cql).replaceAttachments1(file, r, metadataResourceNames())) {
          altered = true;
        }
        if (isNewML()) {
          if (e.canHaveChild("language")) {
            e.setChildValue("language", defaultTranslationLang);
          }
          List<TranslationUnit> translations = findTranslations(r.fhirType(), r.getId(), r.getErrors());
          if (translations != null) {
            r.setHasTranslations(true);
            if (langUtils.importFromTranslations(e, translations, r.getErrors()) > 0) {
              altered = true;
            }
          }
        }
        if (!binary && !customResourceNames.contains(r.fhirType()) && ((altered && r.getResource() != null) || (ver.equals(Constants.VERSION) && r.getResource() == null && context.getResourceNamesAsSet().contains(r.fhirType())))) {
          r.setResource(new ObjectConverter(context).convert(r.getElement()));
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

  private String stripVersion(String url) {
    return url.endsWith("|"+businessVersion) ? url.substring(0, url.lastIndexOf("|")) : url;
  }

  private List<TranslationUnit> findTranslations(String fhirType, String id, List<ValidationMessage> messages) throws IOException {
    List<TranslationUnit> res = null;

    String base = fhirType+"-"+id;
    String tbase = fhirType+"-$all";
    for (String dir : translationSources) {      
      File df = new File(Utilities.path(rootDir, dir));
      if (df.exists()) {
        for (String fn : df.list()) {
          if ((fn.startsWith(base+".") || fn.startsWith(base+"-") || fn.startsWith(base+"_")) ||
              (fn.startsWith(tbase+".") || fn.startsWith(tbase+"-") || fn.startsWith(tbase+"_"))) {
            LanguageFileProducer lp = null;
            String lang = findLang(fn, dir);
            switch (Utilities.getFileExtension(fn)) {
            case "po":
              if (lang == null) {
                throw new Error("Unable to determine language from filename for "+Utilities.path(rootDir, dir, fn));
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
              File f = new File(Utilities.path(rootDir, dir, fn));
              usedLangFiles.add(f.getAbsolutePath());
              if (!Utilities.noString(FileUtilities.fileToString(f).trim())) {
                try {
                  FileInputStream s = new FileInputStream(f);
                  try {
                    res.addAll(lp.loadSource(s));
                  } finally {
                    s.close();
                  }
                } catch (Exception e) {
                  messages.add(new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, fhirType, "Error loading "+f.getAbsolutePath()+": "+e.getMessage(), IssueSeverity.ERROR));                    
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

  public void checkResourceUnique(String tid, String source, String cause) throws Error {
    if (logLoading) {
      System.out.println("id: "+tid+", file: "+source+", from "+cause);
    }
    if (loadedIds.containsKey(tid)) {
      System.out.println("Duplicate Resource in IG: "+tid+". first found in "+loadedIds.get(tid)+", now in "+source+" ("+cause+")");
      duplicateInputResourcesDetected = true;
    }
    loadedIds.put(tid, source+" ("+cause+")");
  }

  private ImplementationGuideDefinitionResourceComponent findIGReference(String type, String id) {
    for (ImplementationGuideDefinitionResourceComponent r : publishedIg.getDefinition().getResource()) {
      if (r.hasReference() && r.getReference().getReference().equals(type+"/"+id)) {
        return r;
      }
    }
    return null;
  }
  
  private Element loadFromMap(FetchedFile file) throws Exception {
    if (!VersionUtilities.isR4Plus(context.getVersion())) {
      throw new Error("Loading Map Files is not supported for version "+VersionUtilities.getNameForVersion(context.getVersion()));
    }
    FmlParser fp = new FmlParser(context, validator.getFHIRPathEngine());
    fp.setupValidation(ValidationPolicy.EVERYTHING);     
    Element res = fp.parse(file.getErrors(), FileUtilities.bytesToString(file.getSource()));
    if (res == null) {
      throw new Exception("Unable to parse Map Source for "+file.getName());
    }
    return res;      
  }

  private Element loadFromXml(FetchedFile file) throws Exception {
    org.hl7.fhir.r5.elementmodel.XmlParser xp = new org.hl7.fhir.r5.elementmodel.XmlParser(context);
    xp.setAllowXsiLocation(true);
    xp.setupValidation(ValidationPolicy.EVERYTHING);
    Element res = xp.parseSingle(new ByteArrayInputStream(file.getSource()), file.getErrors());
    if (res == null) {
      throw new Exception("Unable to parse XML for "+file.getName());
    }
    return res;
  }

  private Element loadFromJson(FetchedFile file) throws Exception {
    org.hl7.fhir.r5.elementmodel.JsonParser jp = new org.hl7.fhir.r5.elementmodel.JsonParser(context);
    jp.setupValidation(ValidationPolicy.EVERYTHING);
    jp.setAllowComments(true);
    jp.setLogicalModelResolver(fetcher);
    return jp.parseSingle(new ByteArrayInputStream(file.getSource()), file.getErrors());
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
    org.hl7.fhir.r5.elementmodel.XmlParser xp = new org.hl7.fhir.r5.elementmodel.XmlParser(context);
    xp.setAllowXsiLocation(true);
    xp.setupValidation(ValidationPolicy.EVERYTHING);
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

  private void scanUrls(String type) throws Exception {
    logDebugMessage(LogCategory.PROGRESS, "process type: "+type);
    for (FetchedFile f : fileList) {
      f.start("scan");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.fhirType().equals(type) ) {
            String url = r.getElement().getChildValue("url");
            if (url != null) {
              String title = r.getElement().getChildValue("title");
              if (title == null) {
                title = r.getElement().getChildValue("name");
              }
              String link = igpkp.getLinkFor(r, true);
              r.getElement().setWebPath(link);
              validationFetcher.getOtherUrls().add(url);
            }
          }
        }
      } finally {
        f.finish("scan");      
      }
    }
  }

  private void loadDepInfo() {
    for (FetchedFile f : fileList) {
      f.start("loadDepInfo");
      try {
        for (FetchedResource r : f.getResources()) {
          String url = r.getElement().getChildValue("url");
          if (url != null) {
            String title = r.getElement().getChildValue("title");
            if (title == null) {
              title = r.getElement().getChildValue("name");
            }
            String link = igpkp.getLinkFor(r, true);
            switch (r.fhirType() ) {
            case "CodeSystem":
              dependentIgFinder.addCodeSystem(url, title, link);
              break;
            case "ValueSet":
              dependentIgFinder.addValueSet(url, title, link);
              break;
            case "StructureDefinition":
              String kind = r.getElement().getChildValue("url");
              if ("logical".equals(kind)) {
                dependentIgFinder.addLogical(url, title, link);
              } else if ("Extension".equals(r.getElement().getChildValue("type"))) {
                dependentIgFinder.addExtension(url, title, link);
              } else {
                dependentIgFinder.addProfile(url, title, link);              
              }
              break;
            case "SearchParameter":
              dependentIgFinder.addSearchParam(url, title, link);
              break;
            case "CapabilityStatement":
              dependentIgFinder.addCapabilityStatement(url, title, link);
              break;
            default:
              // do nothing
            }
          }
        }
      } finally {
        f.finish("loadDepInfo");      
      }
    }
    dependentIgFinder.go();
  }

  private void loadLists() throws Exception {
    for (FetchedFile f : fileList) {
      f.start("loadLists");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.fhirType().equals("List")) {
            ListResource l = (ListResource) convertFromElement(r.getElement());
            r.setResource(l);          
          }
        }
      } finally {
        f.finish("loadLists");      
      }
    }
  }

  private void checkLanguage() {
    if ((langPolicy == LanguagePopulationPolicy.ALL || langPolicy == LanguagePopulationPolicy.OTHERS)) {
      for (FetchedFile f : fileList) {
        for (FetchedResource r : f.getResources()) {
          logDebugMessage(LogCategory.PROGRESS, "process language in res: "+r.fhirType()+"/"+r.getId());
          if (!sourceIg.hasLanguage()) {
            if (r.getElement().hasChild("language")) {
              r.getElement().removeChild("language");
            }
          } else {
            r.getElement().setChildValue("language", sourceIg.getLanguage());
          }
        }
      }
    }
  }
  
  private void load(String type, boolean isMandatory) throws Exception {
    for (FetchedFile f : fileList) {
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
          if (f.getBundleType() == FetchedBundleType.NATIVE) {
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
          if (adHocTmpDir == null && !listedURLExemptions.contains(bc.getUrl()) && !isExampleResource(bc) && !canonicalUrlIsOk(bc)) {
            if (!bc.fhirType().equals("CapabilityStatement") || !bc.getUrl().contains("/Conformance/")) {
              f.getErrors().add(new ValidationMessage(Source.ProfileValidator, IssueType.INVALID, bc.fhirType()+".where(url = '"+bc.getUrl()+"')", "Conformance resource "+f.getPath()+" - the canonical URL ("+Utilities.pathURL(igpkp.getCanonical(), bc.fhirType(), 
                  bc.getId())+") does not match the URL ("+bc.getUrl()+")", IssueSeverity.ERROR).setMessageId(PublisherMessageIds.RESOURCE_CANONICAL_MISMATCH));
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
        CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
        if (businessVersion != null) {
          altered = true;
          b.append("version="+businessVersion);
          bc.setVersion(businessVersion);
        } else if (defaultBusinessVersion != null && !bc.hasVersion()) {
          altered = true;
          b.append("version="+defaultBusinessVersion);
          bc.setVersion(defaultBusinessVersion);
        }
        if (!(bc instanceof StructureDefinition)) { 
          // can't do structure definitions yet, because snapshots aren't generated, and not all are registered.
          // do it later when generating snapshots
          altered = checkCanonicalsForVersions(f, bc, false) || altered;
        }
        if (!r.isExample()) {
          if (wgm != null) {
            if (!bc.hasExtension(ToolingExtensions.EXT_WORKGROUP)) {
              altered = true;
              b.append("wg="+wgm);
              bc.addExtension(ToolingExtensions.EXT_WORKGROUP, new CodeType(wgm));
            } else if (!wgm.equals(ToolingExtensions.readStringExtension(bc, ToolingExtensions.EXT_WORKGROUP))) {
              altered = true;
              b.append("wg="+wgm);
              bc.getExtensionByUrl(ToolingExtensions.EXT_WORKGROUP).setValue(new CodeType(wgm));
            }
          } else if (defaultWgm != null && !bc.hasExtension(ToolingExtensions.EXT_WORKGROUP)) {
            altered = true;
            b.append("wg="+defaultWgm);
            bc.addExtension(ToolingExtensions.EXT_WORKGROUP, new CodeType(defaultWgm));
          }
        }

        if (contacts != null && !contacts.isEmpty()) {
          altered = true;
          b.append("contact");
          bc.getContact().clear();
          bc.getContact().addAll(contacts);
        } else if (!bc.hasContact() && defaultContacts != null && !defaultContacts.isEmpty()) {
          altered = true;
          b.append("contact");
          bc.getContact().addAll(defaultContacts);
        }
        if (contexts != null && !contexts.isEmpty()) {
          altered = true;
          b.append("useContext");
          bc.getUseContext().clear();
          bc.getUseContext().addAll(contexts);
        } else if (!bc.hasUseContext() && defaultContexts != null && !defaultContexts.isEmpty()) {
          altered = true;
          b.append("useContext");
          bc.getUseContext().addAll(defaultContexts);
        }
        // Todo: Enable these
        if (copyright != null && !bc.hasCopyright() && bc.supportsCopyright()) {
          altered = true;
          b.append("copyright="+copyright);
          bc.setCopyrightElement(copyright);
        } else if (!bc.hasCopyright() && defaultCopyright != null) {
          altered = true;
          b.append("copyright="+defaultCopyright);
          bc.setCopyrightElement(defaultCopyright);
        }
        if (bc.hasCopyright() && bc.getCopyright().contains("{{{year}}}")) {
          bc.setCopyright(bc.getCopyright().replace("{{{year}}}", Integer.toString(Calendar.getInstance().get(Calendar.YEAR))));
          altered = true;
          b.append("copyright="+bc.getCopyright());
        }
        if (jurisdictions != null && !jurisdictions.isEmpty()) {
          altered = true;
          b.append("jurisdiction");
          bc.getJurisdiction().clear();
          bc.getJurisdiction().addAll(jurisdictions);
        } else if (!bc.hasJurisdiction() && defaultJurisdictions != null && !defaultJurisdictions.isEmpty()) {
          altered = true;
          b.append("jurisdiction");
          bc.getJurisdiction().addAll(defaultJurisdictions);
        }
        if (publisher != null) {
          altered = true;
          b.append("publisher="+publisher);
          bc.setPublisherElement(publisher);
        } else if (!bc.hasPublisher() && defaultPublisher != null) {
          altered = true;
          b.append("publisher="+defaultPublisher);
          bc.setPublisherElement(defaultPublisher);
        }


        if (!bc.hasDate()) {
          altered = true;
          b.append("date");
          bc.setDateElement(new DateTimeType(execTime));
        }
        if (!bc.hasStatus()) {
          altered = true;
          b.append("status=draft");
          bc.setStatus(PublicationStatus.DRAFT);
        }
        if (new AdjunctFileLoader(binaryPaths, cql).replaceAttachments2(f, r)) {
          altered = true;
        }
        if (oidRoot != null && !hasOid(bc.getIdentifier())) {
          String oid = getOid(r.fhirType(), bc.getIdBase());
          bc.getIdentifier().add(new Identifier().setSystem("urn:ietf:rfc:3986").setValue("urn:oid:"+oid));
          altered = true;
        }
        if (altered) {
          if ((langPolicy == LanguagePopulationPolicy.ALL || langPolicy == LanguagePopulationPolicy.OTHERS)) {
            if (!sourceIg.hasLanguage()) {
              if (r.getElement().hasChild("language")) {
                bc.setLanguage(null);
              }
            } else {
              bc.setLanguage(sourceIg.getLanguage());
            }
          }
          
          if (Utilities.existsInList(r.fhirType(), "GraphDefinition")) {
            f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.PROCESSING, bc.fhirType()+".where(url = '"+bc.getUrl()+"')", 
                "The resource needed to modified during loading to apply common headers "+b.toString()+" but this isn't possible for the type "+r.fhirType()+" because version conversion isn't working completely",
                IssueSeverity.WARNING).setMessageId(PublisherMessageIds.RESOURCE_CONVERSION_NOT_POSSIBLE));
          } else {
            r.setElement(convertToElement(r, bc));
          }
        }
        igpkp.checkForPath(f, r, bc, false);
        try {
          context.cacheResourceFromPackage(bc, packageInfo);
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
              if (!mr.hasWebPath()) {
                igpkp.checkForPath(f,  r,  mr, true);
              }
              context.cacheResourceFromPackage(mr, packageInfo);
            } else
              logDebugMessage(LogCategory.PROGRESS, "Ignoring resource "+type+"/"+mr.getId()+" in Bundle "+f.getName()+" because it has no canonical URL");

          }
        }
      }
    }
  }

  public class CanonicalVisitor<T> implements IDatatypeVisitor {
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
      CanonicalResource tgt = (CanonicalResource) context.fetchResourceRaw(Resource.class, url);
      if (tgt instanceof CodeSystem) {
        CodeSystem cs = (CodeSystem) tgt;
        if (cs.getContent() == CodeSystemContentMode.NOTPRESENT && cs.hasSourcePackage() && cs.getSourcePackage().isTHO()) {
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
      if (pinningPolicy == PinningPolicy.FIX) {
        if (!snapshotMode) {
          pinCount++;
          f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.PROCESSING, path, "Pinned the version of "+url+" to "+tgt.getVersion(),
              IssueSeverity.INFORMATION).setMessageId(PublisherMessageIds.PIN_VERSION));
        }
        if (pinDest != null) {
          pinInManifest(tgt.fhirType(), url, tgt.getVersion());
        } else {
          ct.setValue(url+"|"+tgt.getVersion());
        }
        return true;
      } else {
        Map<String, String> lst = validationFetcher.fetchCanonicalResourceVersionMap(null, null, url);
        if (lst.size() < 2) {
          return false;
        } else {
          if (!snapshotMode) {
            pinCount++;
            f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.PROCESSING, path, "Pinned the version of "+url+" to "+tgt.getVersion()+" from choices of "+stringify(",", lst), 
              IssueSeverity.INFORMATION).setMessageId(PublisherMessageIds.PIN_VERSION));
          }
          if (pinDest != null) {
            pinInManifest(tgt.fhirType(), url, tgt.getVersion());
          } else {
            ct.setValue(url+"|"+tgt.getVersion());
          }
          return true;
        }
      }
    }

    private void pinInManifest(String type, String url, String version) {
      FetchedResource r = fetchByResource("Parameters", pinDest);
      if (r == null) {
        throw new Error("Unable to find nominated pin-manifest "+pinDest);
      }
      Element p = r.getElement();
      if (!p.hasUserData(UserDataNames.EXP_REVIEWED)) {
        new ExpansionParameterUtilities(context).reviewVersions(p);
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
    
  private boolean checkCanonicalsForVersions(FetchedFile f, CanonicalResource bc, boolean snapshotMode) {
    if (pinningPolicy == PinningPolicy.NO_ACTION) {
      return false;     
//    } else if ("ImplementationGuide".equals(bc.fhirType())) {
//      return false;
    } else {
      DataTypeVisitor dv = new DataTypeVisitor();
      dv.visit(bc, new CanonicalVisitor<CanonicalType>(f, snapshotMode));
      return dv.isAnyTrue();
    } 
  }

  private String getOid(String type, String id) {
    String ot = oidNodeForType(type);
    String oid = oidIni.getStringProperty(type, id);
    if (oid != null) {
      return oid;
    }
    Integer keyR = oidIni.getIntegerProperty("Key", type);
    int key = keyR == null ? 0 : keyR.intValue();
    key++;
    oid = oidRoot+"."+ot+"."+key;
    oidIni.setIntegerProperty("Key", type, key, null);
    oidIni.setStringProperty(type, id, oid, null);
    oidIni.save();
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

  private boolean hasOid(List<Identifier> identifiers) {
    for (Identifier id : identifiers) {
      if ("urn:ietf:rfc:3986".equals(id.getSystem()) && id.hasValue() && id.getValue().startsWith("urn:oid:")) {
        return true;
      }
    }
    return false;
  }

  private boolean canonicalUrlIsOk(CanonicalResource bc) {
    if (bc.getUrl().equals(Utilities.pathURL(igpkp.getCanonical(), bc.fhirType(), bc.getId()))) {
      return true;
    }
    if (altCanonical != null) { 
      if (bc.getUrl().equals(Utilities.pathURL(altCanonical, bc.fhirType(), bc.getId()))) {
        return true;
      }
      if (altCanonical.equals("http://hl7.org/fhir") && "CodeSystem".equals(bc.fhirType()) && bc.getUrl().equals(Utilities.pathURL(altCanonical, bc.getId()))) {
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
    vars.put("{{site.data.fhir.path}}", igpkp.specPath()+"/");
    return new LiquidEngine(context, validator.getExternalHostServices()).replaceInHtml(resource.getText().getDiv(), vars);
  }



  private boolean isExampleResource(CanonicalResource mr) {
    for (ImplementationGuideDefinitionResourceComponent ir : publishedIg.getDefinition().getResource()) {
      if (isSameResource(ir, mr)) {
        return ir.getIsExample() || ir.hasProfile();
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
        f.start("generateAdditionalExamples");
        try {
          List<StructureDefinition> list = new ArrayList<StructureDefinition>();
          for (FetchedResource r : f.getResources()) {
            if (r.getResource() instanceof StructureDefinition) {
              list.add((StructureDefinition) r.getResource());
            }
          }
          for (StructureDefinition sd : list) {
            for (Element e : utils.generateExamples(sd, false)) {
              FetchedResource nr = new FetchedResource(f.getName()+" (additional example)");
              nr.setElement(e);
              nr.setId(e.getChildValue("id"));
              nr.setTitle("Generated Example");
              nr.getStatedProfiles().add(sd.getUrl());
              f.getResources().add(nr);
              igpkp.findConfiguration(f, nr);
            }
          }
        } finally {
          f.finish("generateAdditionalExamples");      
        }
      }
    }
  }

  private void generateSnapshots() throws Exception {
    context.setAllowLoadingDuplicates(true);

    ProfileUtilities utils = new ProfileUtilities(context, null, igpkp);
    utils.setXver(context.getXVer());
    utils.setForPublication(true);
    utils.setMasterSourceFileNames(specMaps.get(0).getTargets());
    utils.setLocalFileNames(pageTargets());
    if (VersionUtilities.isR4Plus(version)) {
      utils.setNewSlicingProcessing(true);
    }

    boolean first = true;
    for (FetchedFile f : fileList) {
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
                ToolingExtensions.setStringExtension(r.getResEntry(), ToolingExtensions.EXT_IGP_RESOURCE_INFO, r.fhirType()+":"+IGKnowledgeProvider.getSDType(r));
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
                modifierExtensions.add(sd);
              }
            }
          }
        } finally {
          f.finish("generateSnapshots");      
        }
      }
    }
  }

  private void checkR4R4B() throws Exception {
    logDebugMessage(LogCategory.PROGRESS, "R4/R4B Check");
    for (FetchedFile f : fileList) {
      f.start("checkR4R4B");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.getResource() instanceof StructureDefinition) {
            r4tor4b.checkProfile((StructureDefinition) r.getResource());
          } else {
            r4tor4b.checkExample(r.getElement());
          }
        }
      } finally {
        f.finish("checkR4R4B");      
      }
    }
  }

  private void generateOtherVersions() throws Exception {
    for (String v : generateVersions) {
      String version = VersionUtilities.versionFromCode(v);
      if (!VersionUtilities.versionsMatch(version, context.getVersion())) {
        logDebugMessage(LogCategory.PROGRESS, "Generate Other Version: "+version);

        NpmPackage targetNpm = pcm.loadPackage(VersionUtilities.packageForVersion(version));
        IContextResourceLoader loader = ContextResourceLoaderFactory.makeLoader(targetNpm.fhirVersion(), new NullLoaderKnowledgeProviderR5());
        SimpleWorkerContext tctxt = new SimpleWorkerContext.SimpleWorkerContextBuilder().fromPackage(targetNpm, loader, true);
        ProfileVersionAdaptor pva = new ProfileVersionAdaptor(context, tctxt);

        for (FetchedFile f : fileList) {
          f.start("generateOtherVersions");
          try {
            for (FetchedResource r : f.getResources()) {
              if (r.getResource() instanceof StructureDefinition) {
                generateOtherVersion(r, pva, version, (StructureDefinition) r.getResource());
              } 
              if (r.getResource() instanceof SearchParameter) {
                generateOtherVersion(r, pva, version, (SearchParameter) r.getResource());
              } 
            }
          } finally {
            f.finish("generateOtherVersions");      
          }
        }
        
        for (FetchedFile f: fileList) {
          f.start("generateOtherVersions");
          try {
            for (FetchedResource r : f.getResources()) {
              if (r.getResource() != null) {
                checkForCoreDependencies(vnpms.get(v), tctxt, r.getResource(), targetNpm);
              }  
            }
          } finally {
            f.finish("generateOtherVersions");      
          }
        }
      }
    }
  }

  private void checkForCoreDependencies(NPMPackageGenerator npm, SimpleWorkerContext tctxt, Resource res, NpmPackage tnpm) throws IOException {
    if (res instanceof StructureDefinition) {
      checkForCoreDependenciesSD(npm, tctxt, (StructureDefinition) res, tnpm);
    }
    if (res instanceof ValueSet) {
      checkForCoreDependenciesVS(npm, tctxt, (ValueSet) res, tnpm);
    }   
  }


  private void checkForCoreDependenciesSD(NPMPackageGenerator npm, SimpleWorkerContext tctxt, StructureDefinition sd, NpmPackage tnpm) throws IOException {
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      if (ed.hasBinding() && ed.getBinding().hasValueSet()) {
        ValueSet vs = context.fetchResource(ValueSet.class, ed.getBinding().getValueSet());
        if (vs != null) {
          checkForCoreDependenciesVS(npm, tctxt, vs, tnpm);
        }
      }
    }
  }

  private void checkForCoreDependenciesVS(NPMPackageGenerator npm, SimpleWorkerContext tctxt, ValueSet valueSet, NpmPackage tnpm) throws IOException {

    if (isCoreResource(valueSet)) {
      if (!inTargetCore(tnpm, valueSet)) {
        if (!npm.hasFile(Category.RESOURCE, valueSet.fhirType()+"-"+valueSet.getIdBase()+".json")) {
          noteOtherVersionAddedFile(tctxt.getVersion(), "ValueSet", valueSet.getIdBase());
          npm.addFile(Category.RESOURCE, valueSet.fhirType()+"-"+valueSet.getIdBase()+".json", convVersion(valueSet, tctxt.getVersion()));
        }
      }
    }
    for (ConceptSetComponent inc : valueSet.getCompose().getInclude()) {
      for (CanonicalType c : inc.getValueSet()) {
        ValueSet vs = context.fetchResource(ValueSet.class, c.getValue());
        if (vs != null) {
          checkForCoreDependenciesVS(npm, tctxt, vs, tnpm);
        }
      }
      if (inc.hasSystem()) {
        CodeSystem cs = context.fetchResource(CodeSystem.class, inc.getSystem(), inc.getVersion());
        if (cs != null) {
          checkForCoreDependenciesCS(npm, tctxt, cs, tnpm);
        }
      }
    }    
  }

  private void checkForCoreDependenciesCS(NPMPackageGenerator npm, SimpleWorkerContext tctxt, CodeSystem cs, NpmPackage tnpm) throws IOException {
    if (isCoreResource(cs)) {
      if (!inTargetCore(tnpm, cs)) {
        if (!npm.hasFile(Category.RESOURCE, cs.fhirType()+"-"+cs.getIdBase()+".json")) {
          noteOtherVersionAddedFile(tctxt.getVersion(), "CodeSystem", cs.getIdBase());
          npm.addFile(Category.RESOURCE, cs.fhirType()+"-"+cs.getIdBase()+".json", convVersion(cs, tctxt.getVersion()));
        }
      }
    }
  }

  private void noteOtherVersionAddedFile(String ver, String type, String id) {
    Set<String> ids = otherVersionAddedResources.get(ver+"-"+type);
    if (ids == null) {
      ids = new HashSet<String>();
      otherVersionAddedResources .put(ver+"-"+type, ids);
    }
    ids.add(id);  
  }

  private boolean inTargetCore(NpmPackage tnpm, CanonicalResource cr) throws IOException {
    boolean res = tnpm.hasCanonical(cr.getUrl());
    return res;
  }

  private boolean isCoreResource(CanonicalResource cr) {
    return cr.hasSourcePackage() && Utilities.existsInList(cr.getSourcePackage().getId(), "hl7.fhir.r5.core", "hl7.fhir.r4.core");
  }


  private void generateOtherVersion(FetchedResource r, ProfileVersionAdaptor pva, String v, StructureDefinition resource) throws FileNotFoundException, IOException {
    List<ConversionMessage> log = new ArrayList<>();
    try {
      StructureDefinition sd = pva.convert(resource, log);
      r.getOtherVersions().put(v+"-StructureDefinition", new AlternativeVersionResource(log, sd));
    } catch (Exception e) {
      System.out.println("Error converting "+r.getId()+" to "+v+": "+e.getMessage());
      log.add(new ConversionMessage(e.getMessage(), ConversionMessageStatus.ERROR));
      r.getOtherVersions().put(v+"-StructureDefinition", new AlternativeVersionResource(log, null));      
    }
  }
  
  private void generateOtherVersion(FetchedResource r, ProfileVersionAdaptor pva, String v, SearchParameter resource) throws FileNotFoundException, IOException {
    List<ConversionMessage> log = new ArrayList<>();
    try {
      SearchParameter sp = pva.convert(resource, log);
      r.getOtherVersions().put(v+"-SearchParameter", new AlternativeVersionResource(log, sp));
    } catch (Exception e) {
      System.out.println("Error converting "+r.getId()+" to "+v+": "+e.getMessage());
      log.add(new ConversionMessage(e.getMessage(), ConversionMessageStatus.ERROR));
      r.getOtherVersions().put(v+"-SearchParameter", new AlternativeVersionResource(log, null));      
    }
  }

  private void generateSnapshot(FetchedFile f, FetchedResource r, StructureDefinition sd, boolean close, ProfileUtilities utils) throws Exception {
    boolean changed = false;

    logDebugMessage(LogCategory.PROGRESS, "Check Snapshot for "+sd.getUrl());
    sd.setFhirVersion(FHIRVersion.fromCode(version));
    List<ValidationMessage> messages = new ArrayList<>();
    utils.setMessages(messages);
    utils.setSuppressedMappings(suppressedMappings);
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
            messages.add(new ValidationMessage(Source.ProfileValidator, IssueType.EXCEPTION, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "Exception generating snapshot: "+e.getMessage(), IssueSeverity.ERROR));
          }
        }
        for (String s : errors) {
          messages.add(new ValidationMessage(Source.ProfileValidator, IssueType.INVALID, "StructureDefinition.where(url = '"+sd.getUrl()+"')", s, IssueSeverity.ERROR));
        }
        utils.setIds(sd, true);

        String p = sd.getDifferential().hasElement() ? sd.getDifferential().getElement().get(0).getPath() : null;
        if (p == null || p.contains(".")) {
          changed = true;
          sd.getDifferential().getElement().add(0, new ElementDefinition().setPath(p == null ? sd.getType() : p.substring(0, p.indexOf("."))));
        }
        utils.setDefWebRoot(igpkp.getCanonical());
        try {
          if (base.getUserString(UserDataNames.render_webroot) != null) {            
            utils.generateSnapshot(base, sd, sd.getUrl(), base.getUserString(UserDataNames.render_webroot), sd.getName());
          } else {
            utils.generateSnapshot(base, sd, sd.getUrl(), null, sd.getName());
          }
        } catch (Exception e) { 
          if (debug) {
            e.printStackTrace();
          }
          throw new FHIRException("Unable to generate snapshot for "+sd.getUrl()+" in "+f.getName()+" because "+e.getMessage(), e);
        }
        changed = true;
      }
    } else { //sd.getKind() == StructureDefinitionKind.LOGICAL
      logDebugMessage(LogCategory.PROGRESS, "Generate Snapshot for Logical Model or specialization"+sd.getUrl());
      if (!sd.hasSnapshot()) {
        utils.setDefWebRoot(igpkp.getCanonical());
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
    context.cacheResourceFromPackage(sd, packageInfo);
  }

  private void validateExpressions() {
    logDebugMessage(LogCategory.PROGRESS, "Validate Expressions");
    for (FetchedFile f : fileList) {
      f.start("validateExpressions");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.getResource() instanceof StructureDefinition && !r.isSnapshotted()) {
            StructureDefinition sd = (StructureDefinition) r.getResource();
            validateExpressions(f, sd, r);
          }
        }
      } finally {
        f.finish("validateExpressions");      
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
        ExpressionNode n = (ExpressionNode) inv.getUserData(UserDataNames.validator_expression_cache);
        if (n == null) {
          n = fpe.parse(inv.getExpression(), sd.getUrl()+"#"+ed.getId()+" / "+inv.getKey());
          inv.setUserData(UserDataNames.validator_expression_cache, n);
        }
        fpe.check(null, "Resource", sd, ed.getPath(), n);
      } catch (Exception e) {
        f.getErrors().add(new ValidationMessage(Source.ProfileValidator, IssueType.INVALID, "StructureDefinition.where(url = '"+sd.getUrl()+"').snapshot.element.where('path = '"+ed.getPath()+"').constraint.where(key = '"+inv.getKey()+"')", e.getMessage(), IssueSeverity.ERROR));
        r.getErrors().add(new ValidationMessage(Source.ProfileValidator, IssueType.INVALID, "StructureDefinition.where(url = '"+sd.getUrl()+"').snapshot.element.where('path = '"+ed.getPath()+"').constraint.where(key = '"+inv.getKey()+"')", e.getMessage(), IssueSeverity.ERROR));
      }
    }
  }

  private StructureDefinition fetchSnapshotted(String url) throws Exception {

    ProfileUtilities utils = null;
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() instanceof StructureDefinition) {
          StructureDefinition sd = (StructureDefinition) r.getResource();
          if (sd.getUrl().equals(url)) {
            if (!r.isSnapshotted()) {
              if (utils == null) {
                utils = new ProfileUtilities(context, null, igpkp);
                utils.setXver(context.getXVer());
                utils.setForPublication(true);
                utils.setMasterSourceFileNames(specMaps.get(0).getTargets());
                utils.setLocalFileNames(pageTargets());
                if (VersionUtilities.isR4Plus(version)) {
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
    return context.fetchResource(StructureDefinition.class, url);
  }

  private void generateLogicalMaps() throws Exception {
    StructureMapUtilities mu = new StructureMapUtilities(context, null, null);
    for (FetchedFile f : fileList) {
      f.start("generateLogicalMaps");
      try {
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
          FetchedResource nr = f.addResource(f.getName()+" (LM)");
          nr.setResource(map);
          nr.setElement(convertToElement(nr, map));
          nr.setId(map.getId());
          nr.setTitle(map.getName());
          igpkp.findConfiguration(f, nr);
        }
      } finally {
        f.finish("generateLogicalMaps");      
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
      } else if (contentType.contains("fml")) {
        StructureMapUtilities mu = new StructureMapUtilities(context, null, null);
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
        StructureMapUtilities mu = new StructureMapUtilities(context, null, null);
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
        StructureMapUtilities mu = new StructureMapUtilities(context, null, null);
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
        StructureMapUtilities mu = new StructureMapUtilities(context, null, null);
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
        StructureMapUtilities mu = new StructureMapUtilities(context, null, null);
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
        StructureMapUtilities mu = new StructureMapUtilities(context, null, null);
        mu.setExceptionsForChecks(false);
        return mu.parse(new String(source), "");
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
      if (Utilities.existsInList(file.getResources().get(0).fhirType(), SpecialTypeHandler.specialTypes(context.getVersion()))) {
        parseVersion = SpecialTypeHandler.VERSION;
      } else {
        parseVersion = str(file.getResources().get(0).getConfig(), "version", version);
      }
    }
    return parseContent(file.getName(), file.getContentType(), parseVersion, file.getSource());
  }

  private void validate() throws Exception {
    if (validationOff) {
      return;
    }

    checkURLsUnique();
    checkOIDsUnique();

    for (FetchedFile f : fileList) {
      f.start("validate");
      try {
        logDebugMessage(LogCategory.PROGRESS, " .. validate "+f.getName());
        logDebugMessage(LogCategory.PROGRESS, " .. "+f.getName());
        FetchedResource r0 = f.getResources().get(0);
        if (f.getLogical() != null && f.getResources().size() == 1 && !r0.fhirType().equals("Binary")) {
          throw new Error("Not done yet");
        } else {
          for (FetchedResource r : f.getResources()) {
            if (!r.isValidated()) {
              logDebugMessage(LogCategory.PROGRESS, "     validating "+r.getTitle());
//              log("     validating "+r.getTitle());
              validate(f, r);
            }
          }
          if (f.getLogical() != null && f.getResources().size() == 1 && r0.fhirType().equals("Binary")) {
            Binary bin = (Binary) r0.getResource();
            StructureDefinition profile = context.fetchResource(StructureDefinition.class, f.getLogical());
            List<ValidationMessage> errs = new ArrayList<ValidationMessage>();
            if (profile == null) {
              errs.add(new ValidationMessage(Source.InstanceValidator, IssueType.NOTFOUND, "file", context.formatMessage(I18nConstants.Bundle_BUNDLE_Entry_NO_LOGICAL_EXPL, r0.getId(), f.getLogical()), IssueSeverity.ERROR));
            } else {
              FhirFormat fmt = FhirFormat.readFromMimeType(bin.getContentType() == null ? f.getContentType() : bin.getContentType());       
              Session tts = tt.start("validation");
              List<StructureDefinition> profiles = new ArrayList<>();
              profiles.add(profile);
              validate(f, r0, bin, errs, fmt, profiles);    
              tts.end();
            }
            processValidationOutcomes(f, r0, errs);
          }
        }
      } finally {
        f.finish("validate");      
      }
    }
    logDebugMessage(LogCategory.PROGRESS, " .. check Profile Examples");
    logDebugMessage(LogCategory.PROGRESS, "gen narratives");
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("StructureDefinition")) {
          validateSD(f, r);
        }
      }        
    }
  }

  private void validate(FetchedFile f, FetchedResource r, Binary bin, List<ValidationMessage> errs, FhirFormat fmt, List<StructureDefinition> profiles) {
    long ts = System.currentTimeMillis();
    r.setLogicalElement(validator.validate(r.getElement(), errs, new ByteArrayInputStream(bin.getContent()), fmt, profiles));
    long tf = System.currentTimeMillis();
    if (tf-ts > validationLogTime && validationLogTime > 0) {
      reportLongValidation(f, r, tf-ts);
    }
  }

  private void validate(FetchedFile f, FetchedResource r, List<ValidationMessage> errs, List<StructureDefinition> profiles) {
    long ts = System.currentTimeMillis();
    validator.validate(r.getElement(), errs, null, r.getElement(), profiles);
    long tf = System.currentTimeMillis();
    if (tf-ts > validationLogTime && validationLogTime > 0) {
      reportLongValidation(f, r, tf-ts);
    }
  }

  private void validate(FetchedFile f, FetchedResource r, List<ValidationMessage> errs, Binary bin) {
    long ts = System.currentTimeMillis();
    validator.validate(r.getElement(), errs, new ByteArrayInputStream(bin.getContent()), FhirFormat.readFromMimeType(bin.getContentType() == null ? f.getContentType() : bin.getContentType()));
    long tf = System.currentTimeMillis();
    if (tf-ts > validationLogTime && validationLogTime > 0) {
      reportLongValidation(f, r, tf-ts);
    }
  }

  private void validate(FetchedFile f, FetchedResource r, List<ValidationMessage> errs, Binary bin, StructureDefinition sd) {
    long ts = System.currentTimeMillis();
    List<StructureDefinition> profiles = new ArrayList<StructureDefinition>();
    profiles.add(sd);
    validator.validate(r.getElement(), errs, new ByteArrayInputStream(bin.getContent()), FhirFormat.readFromMimeType(bin.getContentType() == null ? f.getContentType(): bin.getContentType()), profiles);
    long tf = System.currentTimeMillis();
    if (tf-ts > validationLogTime && validationLogTime > 0) {
      reportLongValidation(f, r, tf-ts);
    }
  }

  private void validate(FetchedFile f, FetchedResource r, List<ValidationMessage> errs, Resource ber) {
    long ts = System.currentTimeMillis();
    validator.validate(r.getElement(), errs, ber, ber.getUserString(UserDataNames.map_profile));
    long tf = System.currentTimeMillis();
    if (tf-ts > validationLogTime && validationLogTime > 0) {
      reportLongValidation(f, r, tf-ts);
    }
  }

  private void validate(FetchedFile f, FetchedResource r, List<ValidationMessage> errs) {
    long ts = System.currentTimeMillis();
    validator.validate(r.getElement(), errs, null, r.getElement());
    long tf = System.currentTimeMillis();
    if (tf-ts > validationLogTime && validationLogTime > 0) {
      reportLongValidation(f, r, tf-ts);
    }
  }

  private void reportLongValidation(FetchedFile f, FetchedResource r, long l) {
    String bps = Long.toString(f.getSize()/l);
    System.out.println("Long Validation for "+f.getTitle()+" resource "+r.fhirType()+"/"+r.getId()+": "+Long.toString(l)+"ms ("+bps+" kb/sec)");
    System.out.println("  * "+validator.reportTimes());
  }

  private void checkURLsUnique() {
    Map<String, FetchedResource> urls = new HashMap<>();
    for (FetchedFile f : fileList) {
      f.start("checkURLsUnique");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
            CanonicalResource cr = (CanonicalResource) r.getResource();
            String url = cr.getUrl(); 
            if (url != null) {
              if (urls.containsKey(url)) {
                FetchedResource rs = urls.get(url);
                CanonicalResource crs = (CanonicalResource) rs.getResource();
                if (!(crs.getStatus() == PublicationStatus.RETIRED || cr.getStatus() == PublicationStatus.RETIRED)) {  
                  FetchedFile fs = findFileForResource(rs);
                  boolean local = url.startsWith(igpkp.getCanonical());
                  f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "Resource", "The URL '"+url+"' has already been used by "+rs.getId()+" in "+fs.getName(), local ? IssueSeverity.ERROR : IssueSeverity.WARNING));
                  fs.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "Resource", "The URL '"+url+"' is also used by "+r.getId()+" in "+f.getName(), local ? IssueSeverity.ERROR : IssueSeverity.WARNING));
                }
              } else {
                urls.put(url, r);
              }
            }
          }
        }
      } finally {
        f.finish("checkURLsUnique");      
      }
    }
  }

  private void checkOIDsUnique() {
    if (oidRoot != null) {
      try {
        JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObjectFromUrl("https://fhir.github.io/ig-registry/oid-assignments.json");
        JsonObject assignments = json.getJsonObject("assignments");
        String ig = null;
        String oid = null;
        if (assignments.has(oidRoot)) {
          ig = assignments.getJsonObject(oidRoot).asString("id");
        }
        for (JsonProperty p : assignments.getProperties()) {
          if (p.getValue().isJsonObject() && sourceIg.getPackageId().equals(p.getValue().asJsonObject().asString("id"))) {
            oid = p.getName();
          }
        }
        if (oid == null && ig == null) {
          errors.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "ImplementationGuide", "The assigned auto-oid-root value '"+oidRoot+"' is not registered in https://github.com/FHIR/ig-registry/blob/master/oid-assignments.json so isn't known to be valid", IssueSeverity.WARNING));          
        } else if (oid != null && !oid.equals(oidRoot)) {
          throw new FHIRException("The assigned auto-oid-root value '"+oidRoot+"' does not match the value of '"+oidRoot+"' registered in https://github.com/FHIR/ig-registry/blob/master/oid-assignments.json so cannot proceed");                    
        } else if (ig != null && !ig.equals(sourceIg.getPackageId())) {
          throw new FHIRException("The assigned auto-oid-root value '"+oidRoot+"' is already registered to the IG '"+ig+"' in https://github.com/FHIR/ig-registry/blob/master/oid-assignments.json so cannot proceed");                    
        }
      } catch (Exception e) {
        errors.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "ImplementationGuide", "Unable to check auto-oid-root because "+e.getMessage(), IssueSeverity.INFORMATION));
      }
    }
    String oidHint = " (OIDs are easy to assign - see https://build.fhir.org/ig/FHIR/fhir-tools-ig/CodeSystem-ig-parameters.html#ig-parameters-auto-oid-root)";
    Map<String, FetchedResource> oidMap = new HashMap<>();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
          CanonicalResource cr = (CanonicalResource) r.getResource();
          if (r.isExample()) {
            List<String> oids = loadOids(cr); 
            if (oids.isEmpty()) {
              if (Utilities.existsInList(r.getResource().fhirType(), "CodeSystem", "ValueSet")) {
                if (forHL7orFHIR()) {
                  f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "Resource", "The resource "+r.fhirType()+"/"+r.getId()+" must have an OID assigned to cater for possible use with OID based terminology systems e.g. CDA usage"+oidHint, IssueSeverity.ERROR));
                } else {
                  f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "Resource", "The resource "+r.fhirType()+"/"+r.getId()+" should have an OID assigned to cater for possible use with OID based terminology systems e.g. CDA usage"+oidHint, IssueSeverity.WARNING));                
                }
              } else {
                f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "Resource", "The resource "+r.fhirType()+"/"+r.getId()+" could usefully have an OID assigned"+oidHint, IssueSeverity.INFORMATION));
              }
            } else {
              for (String oid : oids) {
                if (oidMap.containsKey(oid)) {
                  FetchedResource rs = oidMap.get(oid);
                  FetchedFile fs = findFileForResource(rs);
                  f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "Resource", "The OID '"+oid+"' has already been used by "+rs.getId()+" in "+fs.getName(), IssueSeverity.ERROR));
                  fs.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "Resource", "The OID '"+oid+"' is also used by "+r.getId()+" in "+f.getName(), IssueSeverity.ERROR));
                } else {
                  oidMap.put(oid, r);
                }
              }
            }
          }
        }
      }
    }
  }

  private List<String> loadOids(CanonicalResource cr) {
    List<String> res = new ArrayList<>();
    for (Identifier id : cr.getIdentifier()) {
      if (id.hasValue() && id.getValue().startsWith("urn:oid:") && id.getUse() != IdentifierUse.OLD) {
        res.add(id.getValue().substring(8));
      }
    }
    return res;
  }

  private FetchedFile findFileForResource(FetchedResource r) {
    for (FetchedFile f : fileList) {
      if (f.getResources().contains(r)) {
        return f;
      }
    }
    return null;
  }

  public void validateSD(FetchedFile f, FetchedResource r) {
    StructureDefinition sd = (StructureDefinition) r.getResource();
    if (!sd.getAbstract() && !isClosing(sd)) {
      if (sd.getKind() == StructureDefinitionKind.RESOURCE) {
        int cE = countStatedExamples(sd.getUrl(), sd.getVersionedUrl());
        int cI = countFoundExamples(sd.getUrl(), sd.getVersionedUrl());
        if (cE + cI == 0) {
          f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this profile", IssueSeverity.WARNING));
          r.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this profile", IssueSeverity.WARNING));
        } else if (cE == 0) {
          f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no explicitly linked examples for this profile", IssueSeverity.INFORMATION));
          r.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no explicitly linked examples for this profile", IssueSeverity.INFORMATION));
        }
      } else if (sd.getKind() == StructureDefinitionKind.COMPLEXTYPE) {
        if (!noUsageCheck) {
          if (sd.getType().equals("Extension")) {
            int c = countUsages(getFixedUrl(sd));
            if (c == 0) {
              f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this extension", IssueSeverity.WARNING));
              r.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this extension", IssueSeverity.WARNING));
            }
          } else {
            int cI = countFoundExamples(sd.getUrl(), sd.getVersionedUrl());
            if (cI == 0) {
              f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this data type profile", IssueSeverity.WARNING));
              r.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this data type profile", IssueSeverity.WARNING));
            }
          }
        }
      }
    }
  }

  private boolean isClosing(StructureDefinition sd) {
    StandardsStatus ss = ToolingExtensions.getStandardsStatus(sd);
    if (ss == StandardsStatus.DEPRECATED || ss == StandardsStatus.WITHDRAWN) {
      return true;
    }
    if (sd.getStatus() == PublicationStatus.RETIRED) {
      return true; 
    }
    return false;
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



  private int countStatedExamples(String url, String vurl) {
    int res = 0;
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        for (String p : r.getStatedProfiles()) {
          if (url.equals(p) || vurl.equals(p)) {
            res++;
          }
        }
      }
    }
    return res;
  }

  private int countFoundExamples(String url, String vurl) {
    int res = 0;
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        for (String p : r.getFoundProfiles()) {
          if (url.equals(p) || vurl.equals(p)) {
            res++;
          }
        }
      }
    }
    return res;
  }

  private void validate(FetchedFile file, FetchedResource r) throws Exception {
    if (!passesValidationFilter(r)) {
      noValidateResources.add(r);
      return;
    }
    if ("ImplementationGuide".equals(r.fhirType()) && !unknownParams.isEmpty()) {
      file.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.INVALID, file.getName(), "Unknown Parameters: "+unknownParams.toString(), IssueSeverity.WARNING));
    }

    Session tts = tt.start("validation");
    List<ValidationMessage> errs = new ArrayList<ValidationMessage>();
    r.getElement().setUserData(UserDataNames.pub_context_file, file);
    r.getElement().setUserData(UserDataNames.pub_context_resource, r);
    validator.setExample(r.isExample());
    if (r.isValidateAsResource()) { 
      Resource res = r.getResource();
      if (res instanceof Bundle) {
        validate(file, r, errs);

        for (BundleEntryComponent be : ((Bundle) res).getEntry()) {
          Resource ber = be.getResource();
          if (ber.hasUserData(UserDataNames.map_profile)) {
            validate(file, r, errs, ber);
          }
        }
      } else if (res.hasUserData(UserDataNames.map_profile)) {
        validate(file, r, errs, res);
      }
    } else if (r.getResource() != null && r.getResource() instanceof Binary && file.getLogical() != null && context.hasResource(StructureDefinition.class, file.getLogical())) {
      StructureDefinition sd = context.fetchResource(StructureDefinition.class, file.getLogical());
      Binary bin = (Binary) r.getResource();
      validate(file, r, errs, bin, sd);    
    } else if (r.getResource() != null && r.getResource() instanceof Binary && r.getExampleUri() != null) {
      Binary bin = (Binary) r.getResource();
      validate(file, r, errs, bin);    
    } else {
      validator.setNoCheckAggregation(r.isExample() && ToolingExtensions.readBoolExtension(r.getResEntry(), "http://hl7.org/fhir/tools/StructureDefinition/igpublisher-no-check-aggregation"));
      List<StructureDefinition> profiles = new ArrayList<>();

      if (r.getElement().hasUserData(UserDataNames.map_profile)) {
        addProfile(profiles, r.getElement().getUserString(UserDataNames.map_profile), null);
      }
      for (String s : r.getProfiles(false)) {
        addProfile(profiles, s, r.fhirType());
      }
      validate(file, r, errs, profiles);
    }
    processValidationOutcomes(file, r, errs);
    r.setValidated(true);
    if (r.getConfig() == null) {
      igpkp.findConfiguration(file, r);
    }
    tts.end();
  }

  private void processValidationOutcomes(FetchedFile file, FetchedResource r, List<ValidationMessage> errs) {
    for (ValidationMessage vm : errs) {
      String loc = r.fhirType()+"/"+r.getId();
      if (!vm.getLocation().startsWith(loc)) {
        vm.setLocation(loc+": "+vm.getLocation());
      }
      if (!alreadyExists(file.getErrors(), vm)) {
        file.getErrors().add(vm);
      }
      r.getErrors().add(vm);
    }
  }

  private boolean alreadyExists(List<ValidationMessage> list, ValidationMessage vm) {
    for (ValidationMessage t : list) {
      if (t.matches(vm)) {
        return true;
      }
    }
    return false;
  }

  private void addProfile(List<StructureDefinition> profiles, String ref, String rt) {
    if (!Utilities.isAbsoluteUrl(ref)) {
      ref = Utilities.pathURL(igpkp.getCanonical(), ref);
    }
    for (StructureDefinition sd : profiles) {
      if (ref.equals(sd.getUrl())) {
        return;
      }
    }
    StructureDefinition sd = context.fetchResource(StructureDefinition.class, ref);
    if (sd != null && (rt == null || sd.getType().equals(rt))) {
      profiles.add(sd);
    }    
  }

  private void generate() throws Exception {
    if (simplifierMode) {
      return;
    }
    Base.setCopyUserData(true); // just keep all the user data when copying while rendering
    bdr = new BaseRenderer(context, checkAppendSlash(specPath), igpkp, specMaps, pageTargets(), markdownEngine, packge, rc);

    forceDir(tempDir);
    forceDir(Utilities.path(tempDir, "_includes"));
    forceDir(Utilities.path(tempDir, "_data"));
    if (hasTranslations) {
      forceDir(tempLangDir);
    }
    rc.setNoHeader(true);
    rcLangs.setNoHeader(true);
    
    otherFilesRun.add(Utilities.path(outputDir, "package.tgz"));
    otherFilesRun.add(Utilities.path(outputDir, "package.manifest.json"));
    otherFilesRun.add(Utilities.path(tempDir, "package.db"));
    DBBuilder db = new DBBuilder(Utilities.path(tempDir, "package.db"), context, rc, cu, fileList);
    copyData();
    for (String rg : regenList) {
      regenerate(rg);
    }

    updateImplementationGuide();
    generateDataFile(db);
    
    logMessage("Generate Native Outputs");

    for (FetchedFile f : changeList) {
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

    logMessage("Generate HTML Outputs");
    for (FetchedFile f : changeList) {
      f.start("generate2");
      try {
        generateHtmlOutputs(f, false, db);
      } finally {
        f.finish("generate2");      
      }
    }
    
    logMessage("Generate Spreadsheets");
    for (FetchedFile f : changeList) {
      f.start("generate2");
      try {
        generateSpreadsheets(f, false, db);
      } finally {
        f.finish("generate2");      
      }
    }
    if (allProfilesCsv != null) {
      allProfilesCsv.dump();
    }
    if (allProfilesXlsx != null) {
      allProfilesXlsx.configure();
      String path = Utilities.path(tempDir, "all-profiles.xlsx");
      allProfilesXlsx.finish(new FileOutputStream(path));
      otherFilesRun.add(Utilities.path(tempDir, "all-profiles.xlsx"));
      allProfilesXlsx.dump();
    }
    logMessage("Generate Summaries");


    for (String s : profileTestCases) {
      logMessage("Running Profile Tests Cases in "+s);
      new ProfileTestCaseExecutor(rootDir, context, validator, fileList).execute(s);
    }
    if (!changeList.isEmpty()) {
      if (isNewML()) {
        for (String l : allLangs()) {
          generateSummaryOutputs(db, l, rcLangs.get(l));          
        }
      } else {
        generateSummaryOutputs(db, null, rc);
      }
    }
    genBasePages();
    db.closeUp();
    FileUtilities.bytesToFile(extensionTracker.generate(), Utilities.path(tempDir, "usage-stats.json"));
    try {
      log("Sending Usage Stats to Server");
      extensionTracker.sendToServer("http://test.fhir.org/usage-stats");
    } catch (Exception e) {
      log("Submitting Usage Stats failed: "+e.getMessage());
    }

    realmRules.addOtherFiles(otherFilesRun, outputDir);
    previousVersionComparator.addOtherFiles(otherFilesRun, outputDir);
    if (ipaComparator != null) {
      ipaComparator.addOtherFiles(otherFilesRun, outputDir);
    }
    if (ipsComparator != null) {
      ipsComparator.addOtherFiles(otherFilesRun, outputDir);
    }
    otherFilesRun.add(Utilities.path(tempDir, "usage-stats.json"));

    printMemUsage();
    log("Reclaiming memory...");
    cleanOutput(tempDir);
    for (FetchedFile f : fileList) {
      f.trim();
    }
    context.unload();
    for (RelatedIG ig : relatedIGs) {
      ig.dump();
    }
    System.gc();
    printMemUsage();

    if (nestedIgConfig != null) {
      if (nestedIgOutput == null || igArtifactsPage == null) {
        throw new Exception("If nestedIgConfig is specified, then nestedIgOutput and igArtifactsPage must also be specified.");
      }
      inspector.setAltRootFolder(nestedIgOutput);
      log("");
      log("**************************");
      log("Processing nested IG: " + nestedIgConfig);
      childPublisher = new Publisher();
      childPublisher.setConfigFile(Utilities.path(FileUtilities.getDirectoryForFile(this.getConfigFile()), nestedIgConfig));
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
    if (!generationOff) {
      templateBeforeJekyll();
    }

    if (runTool()) {
      if (!generationOff) {
        templateOnCheck();
      }

      if (!changeList.isEmpty()) {
        File df = makeSpecFile();
        addFileToNpm(Category.OTHER, "spec.internals", FileUtilities.fileToBytes(df.getAbsolutePath()));
        addFileToNpm(Category.OTHER, "validation-summary.json", validationSummaryJson());
        addFileToNpm(Category.OTHER, "validation-oo.json", validationSummaryOO());
        for (String t : testDirs) {
          addTestDir(new File(t), t);
        }
        for (String n : otherDirs) {
          File f = new File(n);
          if (f.exists()) {
            for (File ff : f.listFiles()) {
              if (!SimpleFetcher.isIgnoredFile(f.getName())) {
                addFileToNpm(Category.OTHER, ff.getName(), FileUtilities.fileToBytes(ff.getAbsolutePath()));
              }              
            }
          } else {
            logMessage("Other Directory not found: "+n);                
          }
        }
        File pr = new File(Utilities.path(FileUtilities.getDirectoryForFile(configFile), "publication-request.json")); 
        if (mode != IGBuildMode.PUBLICATION && pr.exists()) {
          addFileToNpm(Category.OTHER, "publication-request.json", FileUtilities.fileToBytes(pr));          
        }
        npm.finish();
        for (NPMPackageGenerator vnpm : vnpms.values()) {
          vnpm.finish();
        }
        for (NPMPackageGenerator vnpm : lnpms.values()) {
          vnpm.finish();
        }
        if (r4tor4b.canBeR4() && r4tor4b.canBeR4B()) {
          try {
            r4tor4b.clonePackage(npmName, npm.filename());
          } catch (Exception e) {
            errors.add(new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, "package.tgz", "Error converting pacakge to R4B: "+e.getMessage(), IssueSeverity.ERROR));        
          }
        }

        if (mode == null || mode == IGBuildMode.MANUAL) {
          if (cacheVersion) {
            pcm.addPackageToCache(publishedIg.getPackageId(), publishedIg.getVersion(), new FileInputStream(npm.filename()), "[output]");
          } else {
            pcm.addPackageToCache(publishedIg.getPackageId(), "dev", new FileInputStream(npm.filename()), "[output]");
            if (branchName != null) {
              pcm.addPackageToCache(publishedIg.getPackageId(), "dev$"+branchName, new FileInputStream(npm.filename()), "[output]");
            }
          }
        } else if (mode == IGBuildMode.PUBLICATION) {
          pcm.addPackageToCache(publishedIg.getPackageId(), publishedIg.getVersion(), new FileInputStream(npm.filename()), "[output]");
        }
        JsonArray json = new JsonArray();
        for (String s : generateVersions) {
          json.add(s);
          //generatePackageVersion(npm.filename(), s);
        }
        FileUtilities.bytesToFile(org.hl7.fhir.utilities.json.parser.JsonParser.composeBytes(json), Utilities.path(outputDir, "sub-package-list.json"));
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
      Map<String, String> statusMessages = new HashMap<>();
      if (mode == IGBuildMode.AUTOBUILD) { 
        statusMessage = rc.formatPhrase(RenderingI18nContext.STATUS_MSG_AUTOBUILD, Utilities.escapeXml(sourceIg.present()), Utilities.escapeXml(sourceIg.getPublisher()), Utilities.escapeXml(workingVersion()), gh(), igpkp.getCanonical());
        for (String lang : allLangs()) {
          statusMessages.put(lang, rcLangs.get(lang).formatPhrase(RenderingI18nContext.STATUS_MSG_AUTOBUILD, Utilities.escapeXml(sourceIg.present()), Utilities.escapeXml(sourceIg.getPublisher()), Utilities.escapeXml(workingVersion()), gh(), igpkp.getCanonical()));
        }
      } else if (mode == IGBuildMode.PUBLICATION) { 
        statusMessage = rc.formatPhrase(RenderingI18nContext.STATUS_MSG_PUBLICATION_HOLDER);
        for (String lang : allLangs()) {
          statusMessages.put(lang, rcLangs.get(lang).formatPhrase(RenderingI18nContext.STATUS_MSG_PUBLICATION_HOLDER));
        }
      } else { 
        statusMessage = rc.formatPhrase(RenderingI18nContext.STATUS_MSG_LOCAL_BUILD, Utilities.escapeXml(sourceIg.present()), Utilities.escapeXml(workingVersion()), igpkp.getCanonical());
        for (String lang : allLangs()) {
          statusMessages.put(lang, rcLangs.get(lang).formatPhrase(RenderingI18nContext.STATUS_MSG_LOCAL_BUILD, Utilities.escapeXml(sourceIg.present()), Utilities.escapeXml(workingVersion()), igpkp.getCanonical()));
        }
      }

      realmRules.addOtherFiles(inspector.getExceptions(), outputDir);
      previousVersionComparator.addOtherFiles(inspector.getExceptions(), outputDir);
      if (ipaComparator != null) {
        ipaComparator.addOtherFiles(inspector.getExceptions(), outputDir);
      }
      if (ipsComparator != null) {
        ipsComparator.addOtherFiles(inspector.getExceptions(), outputDir);
      }
      
      List<ValidationMessage> linkmsgs = generationOff ? new ArrayList<ValidationMessage>() : inspector.check(statusMessage, statusMessages);
      int bl = 0;
      int lf = 0;
      for (ValidationMessage m : ValidationPresenter.filterMessages(null, linkmsgs, true, suppressedMessages)) {
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
      if (mode == IGBuildMode.AUTOBUILD && !inspector.getPublishBoxOK()) {
        throw new FHIRException("The auto-build infrastructure does not publish IGs unless the publish-box is present ("+inspector.getPublishboxParsingSummary()+"). For further information, see note at http://wiki.hl7.org/index.php?title=FHIR_Implementation_Guide_Publishing_Requirements#HL7_HTML_Standards_considerations");
      }
      generateFragmentUsage();
      log("Build final .zip");
      ZipGenerator zip = new ZipGenerator(Utilities.path(tempDir, "full-ig.zip"));
      zip.addFolder(outputDir, "site/", false);
      zip.addFileSource("index.html", REDIRECT_SOURCE, false);
      zip.close();
      FileUtilities.copyFile(Utilities.path(tempDir, "full-ig.zip"), Utilities.path(outputDir, "full-ig.zip"));
      log("Final .zip built");
    }
  }

  private void addFileToNpm(Category other, String name, byte[] cnt) throws IOException {
    npm.addFile(other, name, cnt);
    for (NPMPackageGenerator vnpm : vnpms.values()) {
      vnpm.addFile(other, name, cnt);      
    }
  }

  private void addFileToNpm(String other, String name, byte[] cnt) throws IOException {
    npm.addFile(other, name, cnt);
    for (NPMPackageGenerator vnpm : vnpms.values()) {
      vnpm.addFile(other, name, cnt);      
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
    if (trackFragments) {
      b.append(",");
      b.append("Used?");
    }
    b.append("\r\n");
    for (String n : Utilities.sorted(fragmentUses.keySet())) {
      if (fragmentUses.get(n).used) {
        b.append(n);
        b.append(",");
        fragmentUses.get(n).produce(b);
        b.append("\r\n");
      }
    }
    for (String n : Utilities.sorted(fragmentUses.keySet())) {
      if (!fragmentUses.get(n).used) {
        b.append(n);
        b.append(",");
        fragmentUses.get(n).produce(b);
        b.append("\r\n");
      }
    }
    FileUtilities.stringToFile(b.toString(), Utilities.path(outputDir, "fragment-usage-analysis.csv"));
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

  private void copyData() throws IOException {
    for (String d : dataDirs) {
      File[] fl = new File(d).listFiles();
      if (fl == null) {
        logDebugMessage(LogCategory.PROGRESS, "No files found to copy at "+d);
      } else {
        for (File f : fl) {
          String df = Utilities.path(tempDir, "_data", f.getName());
          otherFilesRun.add(df);
          FileUtilities.copyFile(f, new File(df));
        }
      }
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
    for (FetchedFile f : fileList) {
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
      if (vm.getLevel() == IssueSeverity.INFORMATION) {
        i++;
      } else if (vm.getLevel() == IssueSeverity.WARNING) {
        w++;
      } else {
        e++;
      }
    }
    fd.add("errors", e);
    fd.add("warnings", w);
    //    fd.add("hints", i);
  }

  private void fixSearchForm() throws IOException {

    String sfn = Utilities.path(tempDir, "searchform.html");
    if (new File(sfn).exists() ) {
      String sf = FileUtilities.fileToString(sfn);
      sf = sf.replace("{{title}}", publishedIg.present());
      sf = sf.replace("{{url}}", targetUrl());
      FileUtilities.stringToFile(sf, sfn);      
    }    
  }

  private void printMemUsage() {
    int mb = 1024*1024;
    Runtime runtime = Runtime.getRuntime();
    String s = "## Memory (MB): " +
               "Use = " + (runtime.totalMemory() - runtime.freeMemory()) / mb+
               ", Free = " + runtime.freeMemory() / mb+
               ", Total = " + runtime.totalMemory() / mb+
               ", Max = " + runtime.maxMemory() / mb;
    log(s);
  }

  private void generatePackageVersion(String filename, String ver) throws IOException {
    NpmPackageVersionConverter self = new NpmPackageVersionConverter(filename, Utilities.path(FileUtilities.getDirectoryForFile(filename), publishedIg.getPackageId()+"."+ver+".tgz"), ver, publishedIg.getPackageId()+"."+ver, context);
    self.execute();
    for (String s : self.getErrors()) {
      errors.add(new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, "ImplementationGuide", "Error creating "+ver+" package: "+s, IssueSeverity.ERROR));
    }
  }

  private String gh() {
    return repoSource != null ? repoSource : targetOutput != null ? targetOutput.replace("https://build.fhir.org/ig", "https://github.com") : null;
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
    r.setElement(convertToElement(r, publishedIg));
    
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
          inspector.addLinkToCheck(Utilities.path(outputDir, path), path, "fake generated link for Implementation Guide");
        }
        for (ContainedResourceDetails c : getContained(rt.getElement())) {
          Extension ex = new Extension(ToolingExtensions.EXT_IGP_CONTAINED_RESOURCE_INFO);
          res.getExtension().add(ex);
          ex.addExtension("type", new CodeType(c.getType()));
          ex.addExtension("id", new IdType(c.getId()));
          ex.addExtension("title", new StringType(c.getType()));
          ex.addExtension("description", new StringType(c.getDescription()));
        }
      }
    }
  }


  private List<ContainedResourceDetails> getContained(Element e) {
    // this list is for the index. Only some kind of resources are pulled out and presented indepedently
    List<ContainedResourceDetails> list = new ArrayList<>();
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
        if (VersionUtilities.getCanonicalResourceNames(context.getVersion()).contains(c.fhirType())) {
          try {
            canonical = (CanonicalResource)convertFromElement(c);
          } catch (Exception ex) {
            System.out.println("Error converting contained resource " + t + " - " + ex.getMessage());
          }
        }
        list.add(new ContainedResourceDetails(c.fhirType(), c.getIdBase(), t, d, canonical));
      }
    }
    return list;
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

    FetchedFile f = new FetchedFile(bc.getWebPath());
    FetchedResource r = f.addResource(f.getName()+" (regen)");
    r.setResource(res);
    r.setId(bc.getId());
    r.setTitle(bc.getName());
    r.setValidated(true);
    r.setElement(convertToElement(r, bc));
    igpkp.findConfiguration(f, r);
    bc.setUserData(UserDataNames.pub_resource_config, r.getConfig());
    generateNativeOutputs(f, true, null);
    generateHtmlOutputs(f, true, null);
  }

  private Element convertToElement(FetchedResource r, Resource res) throws Exception {
    String parseVersion = version;
    if (r != null) {
      if (Utilities.existsInList(r.fhirType(), SpecialTypeHandler.specialTypes(context.getVersion()))) {
        parseVersion = SpecialTypeHandler.VERSION;
      } else if (r.getConfig() != null) {
        parseVersion = str(r.getConfig(), "version", version);
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
    Element e = new org.hl7.fhir.r5.elementmodel.JsonParser(context).parseSingle(bi, null);
    return e;
  }

  private Resource convertFromElement(Element res) throws IOException, org.hl7.fhir.exceptions.FHIRException, FHIRFormatError, DefinitionException {
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    new org.hl7.fhir.r5.elementmodel.JsonParser(context).compose(res, bs, OutputStyle.NORMAL, null);
    ByteArrayInputStream bi = new ByteArrayInputStream(bs.toByteArray());
    if (VersionUtilities.isR3Ver(version)) {
      org.hl7.fhir.dstu3.formats.JsonParser jp = new org.hl7.fhir.dstu3.formats.JsonParser();
      return  VersionConvertorFactory_30_50.convertResource(jp.parse(bi));
    } else if (VersionUtilities.isR4Ver(version)) {
      org.hl7.fhir.r4.formats.JsonParser jp = new org.hl7.fhir.r4.formats.JsonParser();
      return  VersionConvertorFactory_40_50.convertResource(jp.parse(bi));
    } else if (VersionUtilities.isR4BVer(version)) {
      org.hl7.fhir.r4b.formats.JsonParser jp = new org.hl7.fhir.r4b.formats.JsonParser();
      return  VersionConvertorFactory_43_50.convertResource(jp.parse(bi));
    } else if (VersionUtilities.isR2BVer(version)) {
      org.hl7.fhir.dstu2016may.formats.JsonParser jp = new org.hl7.fhir.dstu2016may.formats.JsonParser();
      return  VersionConvertorFactory_14_50.convertResource(jp.parse(bi));
    } else if (VersionUtilities.isR2Ver(version)) {
      org.hl7.fhir.dstu2.formats.JsonParser jp = new org.hl7.fhir.dstu2.formats.JsonParser();
      return VersionConvertorFactory_10_50.convertResource(jp.parse(bi));
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
    if (p.contains("tbl_bck")) {
      return true; // these are not always tracked
    }
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
    SpecMapManager map = new SpecMapManager(npmName, npmName+"#"+version, version, Constants.VERSION, Integer.toString(ToolsVersion.TOOLS_VERSION), execTime, igpkp.getCanonical());
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        String u = igpkp.getCanonical()+r.getUrlTail();
        String u2 = altCanonical == null ? "" : altCanonical+r.getUrlTail();
        String u3 = altCanonical == null ? "" : altCanonical+"/"+r.getId();
        if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
          String uc = ((CanonicalResource) r.getResource()).getUrl();
          if (uc != null && !(u.equals(uc) || u2.equals(uc) || u3.equals(uc)) && !isListedURLExemption(uc) && !isExampleResource((CanonicalResource) r.getResource()) && adHocTmpDir == null) {
            f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, f.getName(), "URL Mismatch "+u+" vs "+uc, IssueSeverity.ERROR));
            r.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, f.getName(), "URL Mismatch "+u+" vs "+uc, IssueSeverity.ERROR));
          }
          if (uc != null && !u.equals(uc)) {
            map.path(uc, igpkp.getLinkFor(r, true));
          }
          String v = ((CanonicalResource) r.getResource()).getVersion();
          if (v != null) {
            map.path(uc + "|" + v, igpkp.getLinkFor(r, true));
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
    generateSchematronsZip();
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
          org.hl7.fhir.dstu3.model.Resource r3 = VersionConvertorFactory_30_50.convertResource(r.getResource());
          if (fmt.equals(FhirFormat.JSON)) {
            new org.hl7.fhir.dstu3.formats.JsonParser().compose(bs, r3);
          } else if (fmt.equals(FhirFormat.XML)) {
            new org.hl7.fhir.dstu3.formats.XmlParser().compose(bs, r3);
          } else if (fmt.equals(FhirFormat.TURTLE)) {
            new org.hl7.fhir.dstu3.formats.RdfParser().compose(bs, r3);
          }
        } else if (VersionUtilities.isR4Ver(version)) {
          org.hl7.fhir.r4.model.Resource r4 = VersionConvertorFactory_40_50.convertResource(r.getResource());
          if (fmt.equals(FhirFormat.JSON)) {
            new org.hl7.fhir.r4.formats.JsonParser().compose(bs, r4);
          } else if (fmt.equals(FhirFormat.XML)) {
            new org.hl7.fhir.r4.formats.XmlParser().compose(bs, r4);
          } else if (fmt.equals(FhirFormat.TURTLE)) {
            new org.hl7.fhir.r4.formats.RdfParser().compose(bs, r4);
          }
        } else if (VersionUtilities.isR4BVer(version)) {
          org.hl7.fhir.r4b.model.Resource r4b = VersionConvertorFactory_43_50.convertResource(r.getResource());
          if (fmt.equals(FhirFormat.JSON)) {
            new org.hl7.fhir.r4b.formats.JsonParser().compose(bs, r4b);
          } else if (fmt.equals(FhirFormat.XML)) {
            new org.hl7.fhir.r4b.formats.XmlParser().compose(bs, r4b);
          } else if (fmt.equals(FhirFormat.TURTLE)) {
            new org.hl7.fhir.r4b.formats.RdfParser().compose(bs, r4b);
          }
        } else if (VersionUtilities.isR2BVer(version)) {
          org.hl7.fhir.dstu2016may.model.Resource r14 = VersionConvertorFactory_14_50.convertResource(r.getResource());
          if (fmt.equals(FhirFormat.JSON)) {
            new org.hl7.fhir.dstu2016may.formats.JsonParser().compose(bs, r14);
          } else if (fmt.equals(FhirFormat.XML)) {
            new org.hl7.fhir.dstu2016may.formats.XmlParser().compose(bs, r14);
          } else if (fmt.equals(FhirFormat.TURTLE)) {
            new org.hl7.fhir.dstu2016may.formats.RdfParser().compose(bs, r14);
          }
        } else if (VersionUtilities.isR2Ver(version)) {
          BaseAdvisor_10_50 advisor = new IGR2ConvertorAdvisor5();
          org.hl7.fhir.dstu2.model.Resource r14 = VersionConvertorFactory_10_50.convertResource(r.getResource(), advisor);
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

  private void generateSchematronsZip()  throws Exception {
    generateZipByExtension(Utilities.path(outputDir, "schematrons.zip"), ".sch");
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

    ZipGenerator zip = new ZipGenerator(Utilities.path(outputDir, "validator.pack"));
    zip.addBytes("version.info", makeNewVersionInfo(version), false);
    zip.addFileName("spec.internals", specFile, false);
    for (FetchedFile f : fileList) {
      f.start("generateValidationPack");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            if (VersionUtilities.isR3Ver(version)) {
              new org.hl7.fhir.dstu3.formats.JsonParser().compose(bs, VersionConvertorFactory_30_50.convertResource(r.getResource()));
            } else if (VersionUtilities.isR4Ver(version)) {
              new org.hl7.fhir.r4.formats.JsonParser().compose(bs, VersionConvertorFactory_40_50.convertResource(r.getResource()));
            } else if (VersionUtilities.isR2BVer(version)) {
              new org.hl7.fhir.dstu2016may.formats.JsonParser().compose(bs, VersionConvertorFactory_14_50.convertResource(r.getResource()));
            } else if (VersionUtilities.isR2Ver(version)) {
              BaseAdvisor_10_50 advisor = new IGR2ConvertorAdvisor5();
              new org.hl7.fhir.dstu2.formats.JsonParser().compose(bs, VersionConvertorFactory_10_50.convertResource(r.getResource(), advisor));
            } else if (VersionUtilities.isR4BVer(version)) {
              new org.hl7.fhir.r4b.formats.JsonParser().compose(bs, VersionConvertorFactory_43_50.convertResource(r.getResource()));
            } else if (VersionUtilities.isR5Plus(version)) {
              new JsonParser().compose(bs, r.getResource());
            } else {
              throw new Exception("Unsupported version "+version);
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
      f.start("generateExampleZip");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.isExample()) {
            String fn = Utilities.path(outputDir, r.fhirType()+"-"+r.getId()+"."+fmt.getExtension());
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
      ZipGenerator zip = new ZipGenerator(Utilities.path(outputDir, "examples."+fmt.getExtension()+".zip"));
      for (String fn : files) {
        zip.addFileName(fn.substring(fn.lastIndexOf(File.separator)+1), fn, false);
      }
      zip.close();
    }

    return !files.isEmpty();
  }

  private boolean runTool() throws Exception {
    if (simplifierMode) {
      return true;
    }
    if (generationOff) {
      FileUtils.copyDirectory(new File(tempDir), new File(outputDir));
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
    Session tts = tt.start("jekyll");

    DefaultExecutor exec = new DefaultExecutor();
    exec.setExitValue(0);
    MyFilterHandler pumpHandler = new MyFilterHandler();
    PumpStreamHandler pump = new PumpStreamHandler(pumpHandler, pumpHandler);
    exec.setStreamHandler(pump);
    exec.setWorkingDirectory(new File(tempDir));
    ExecuteWatchdog watchdog = new ExecuteWatchdog(jekyllTimeout);
    exec.setWatchdog(watchdog);

    try {
      log("Run jekyll: "+jekyllCommand+" build --destination \""+outputDir+"\" (in folder "+tempDir+")");
      if (SystemUtils.IS_OS_WINDOWS) {
        log("Due to a known issue, Jekyll errors are lost between Java and Ruby in windows systems.");
        log("If the build process hangs at this point, you have to go to a");
        log("command prompt, and then run these two commands:");
        log("");
        log("cd "+tempDir);
        log(jekyllCommand+" build --destination \""+outputDir+"\"");
        log("");
        log("and then investigate why Jekyll has failed");
      }
      log("Troubleshooting Note: usual cases for Jekyll to fail are:");
      log("* A failure to produce a fragment that is already logged in the output above");
      log("* A reference to a manually edited file that hasn't been provided");
      if (SystemUtils.IS_OS_WINDOWS) {
        final String enclosedOutputDir = "\"" + outputDir + "\"";
        final CommandLine commandLine = new CommandLine("cmd")
                .addArgument( "/C")
                .addArgument(jekyllCommand)
                .addArgument("build")
                .addArgument("--destination")
                .addArgument(enclosedOutputDir);
        exec.execute(commandLine);
      } else if (FhirSettings.hasRubyPath()) {
        ProcessBuilder processBuilder = new ProcessBuilder(new String("bash -c "+jekyllCommand));
        Map<String, String> env = processBuilder.environment();
        Map<String, String> vars = new HashMap<>();
        vars.putAll(env);
        String path = FhirSettings.getRubyPath()+":"+env.get("PATH");
        vars.put("PATH", path);
        if (FhirSettings.getGemPath() != null) {
          vars.put("GEM_PATH", FhirSettings.getGemPath());
        }
        CommandLine commandLine = new CommandLine("bash").addArgument("-c").addArgument(jekyllCommand+" build --destination "+outputDir, false);
        exec.execute(commandLine, vars);
      } else {
        final String enclosedOutputDir = "\"" + outputDir + "\"";
        final CommandLine commandLine = new CommandLine(jekyllCommand)
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
          log("Jekyll timeout exceeded: " + Long.toString(jekyllTimeout/1000) + " seconds");
        }
        log("Jekyll claimed to succeed, but returned an error. Proceeding anyway");
      } else {
        log("Jekyll has failed. Complete output from running Jekyll: " + pumpHandler.getBufferString());
        if (watchdog.killedProcess()) {
          log("Jekyll timeout exceeded: " + Long.toString(jekyllTimeout/1000) + " seconds");
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

  private void generateSummaryOutputs(DBBuilder db, String lang, RenderingContext rc) throws Exception {
    log("Generating Summary Outputs"+(lang == null?"": " ("+lang+")"));
    generateResourceReferences(lang);

    generateCanonicalSummary(lang);

    CrossViewRenderer cvr = new CrossViewRenderer(igpkp.getCanonical(), altCanonical, context, igpkp.specPath(), rc.copy(false));
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
          cvr.seeResource((CanonicalResource) r.getResource());
        }
      }
    }
    MappingSummaryRenderer msr = new MappingSummaryRenderer(context, rc);
    msr.addCanonical(igpkp.getCanonical());
    if (altCanonical != null) {
      msr.addCanonical(altCanonical);
    }
    msr.analyse();
    Set<String> types = new HashSet<>();
    for (StructureDefinition sd : context.fetchResourcesByType(StructureDefinition.class)) {
      if (sd.getUrl().equals("http://hl7.org/fhir/StructureDefinition/Base") || (sd.getDerivation() == TypeDerivationRule.SPECIALIZATION && sd.getKind() != StructureDefinitionKind.LOGICAL && !types.contains(sd.getType()))) {
        types.add(sd.getType());
        long start = System.currentTimeMillis();
        String src = msr.render(sd);
        fragment("maps-"+sd.getTypeTail(), src, otherFilesRun, start, "maps", "Cross", lang);
      }
    }
    long start = System.currentTimeMillis();
    fragment("summary-observations", cvr.getObservationSummary(), otherFilesRun, start, "summary-observations", "Cross", lang);
    String path = Utilities.path(tempDir, "observations-summary.xlsx");
    ObservationSummarySpreadsheetGenerator vsg = new ObservationSummarySpreadsheetGenerator(context);
    otherFilesRun.add(path);
    vsg.generate(cvr.getObservations());
    vsg.finish(new FileOutputStream(path));
    DeprecationRenderer dpr = new DeprecationRenderer(context, checkAppendSlash(specPath), igpkp, specMaps, pageTargets(), markdownEngine, packge, rc.copy(false));
    fragment("deprecated-list", dpr.deprecationSummary(fileList, previousVersionComparator), otherFilesRun, start, "deprecated-list", "Cross", lang);
    fragment("new-extensions", dpr.listNewResources(fileList, previousVersionComparator, "StructureDefinition.extension"), otherFilesRun, start, "new-extensions", "Cross", lang);
    fragment("deleted-extensions", dpr.listDeletedResources(fileList, previousVersionComparator, "StructureDefinition.extension"), otherFilesRun, start, "deleted-extensions", "Cross", lang);

    JsonObject data = new JsonObject();
    JsonArray ecl = new JsonArray();
    //    data.add("extension-contexts-populated", ecl); causes a bug in jekyll see https://github.com/jekyll/jekyll/issues/9289

    start = System.currentTimeMillis();
    fragment("summary-extensions", cvr.getExtensionSummary(), otherFilesRun, start, "summary-extensions", "Cross", lang);
    start = System.currentTimeMillis();
    fragment("extension-list", cvr.buildExtensionTable(), otherFilesRun, start, "extension-list", "Cross", lang);    
    Set<String> econtexts = cu.getTypeNameSet();
    for (String s : cvr.getExtensionContexts()) {
      ecl.add(s);
      econtexts.add(s);
    }
    for (String s : econtexts) {
      start = System.currentTimeMillis();
      fragment("extension-list-"+s, cvr.buildExtensionTable(s), otherFilesRun, start, "extension-list-", "Cross", lang);      
    }
    for (String s : context.getResourceNames()) {
      start = System.currentTimeMillis();
      fragment("extension-search-list-"+s, cvr.buildExtensionSearchTable(s), otherFilesRun, start, "extension-search-list", "Cross", lang);      
    }
    for (String s : cvr.getExtensionIds()) {
      start = System.currentTimeMillis();
      fragment("extension-search-"+s, cvr.buildSearchTableForExtension(s), otherFilesRun, start, "extension-search", "Cross", lang);      
    }
    
    start = System.currentTimeMillis();
    List<ValueSet> vslist = cvr.buildDefinedValueSetList(fileList); 
    fragment("valueset-list", cvr.renderVSList(versionToAnnotate, vslist, cvr.needVersionReferences(vslist, publishedIg.getVersion()), false), otherFilesRun, start, "valueset-list",  "Cross", lang);
    saveVSList("valueset-list", vslist, db, 1);
    
    start = System.currentTimeMillis();
    vslist = cvr.buildUsedValueSetList(false, fileList);
    fragment("valueset-ref-list", cvr.renderVSList(versionToAnnotate, vslist, cvr.needVersionReferences(vslist, publishedIg.getVersion()), true), otherFilesRun, start, "valueset-ref-list", "Cross", lang);
    saveVSList("valueset-ref-list", vslist, db, 2);
    
    start = System.currentTimeMillis();
    vslist = cvr.buildUsedValueSetList(true, fileList);
    fragment("valueset-ref-all-list", cvr.renderVSList(versionToAnnotate, vslist, cvr.needVersionReferences(vslist, publishedIg.getVersion()), true), otherFilesRun, start, "valueset-ref-all-list", "Cross", lang);
    saveVSList("valueset-ref-all-list", vslist, db, 3);
    
    start = System.currentTimeMillis();
    List<CodeSystem> cslist = cvr.buildDefinedCodeSystemList(fileList);
    fragment("codesystem-list", cvr.renderCSList(versionToAnnotate, cslist, cvr.needVersionReferences(vslist, publishedIg.getVersion()), false), otherFilesRun, start, "codesystem-list", "Cross", lang);
    saveCSList("codesystem-list", cslist, db, 1);
    
    start = System.currentTimeMillis();
    cslist = cvr.buildUsedCodeSystemList(false, fileList);
    fragment("codesystem-ref-list", cvr.renderCSList(versionToAnnotate, cslist, cvr.needVersionReferences(vslist, publishedIg.getVersion()), true), otherFilesRun, start, "codesystem-ref-list", "Cross", lang);      
    saveCSList("codesystem-ref-list", cslist, db, 2);

    start = System.currentTimeMillis();
    cslist = cvr.buildUsedCodeSystemList(true, fileList);
    fragment("codesystem-ref-all-list", cvr.renderCSList(versionToAnnotate, cslist, cvr.needVersionReferences(vslist, publishedIg.getVersion()), true), otherFilesRun, start, "codesystem-ref-all-list", "Cross", lang);      
    saveCSList("codesystem-ref-all-list", cslist, db, 3);

    fragment("obligation-summary", cvr.renderObligationSummary(), otherFilesRun, System.currentTimeMillis(), "obligation-summary", "Cross", lang);      

    for (String v : generateVersions) {
      for (String n : context.getResourceNames()) {
        fragment("version-"+v+"-summary-"+n, generateVersionSummary(v, n), otherFilesRun, start, "version-"+v+"-summary-"+n, "Cross", lang);
      }      
    }
    start = System.currentTimeMillis();
    ipStmt = new IPStatementsRenderer(context, markdownEngine, sourceIg.getPackageId(), rc).genIpStatements(fileList, lang);
    trackedFragment("1", "ip-statements", ipStmt, otherFilesRun, start, "ip-statements", "Cross", lang);
    if (VersionUtilities.isR4Ver(version) || VersionUtilities.isR4BVer(version)) {
      trackedFragment("2", "cross-version-analysis", r4tor4b.generate(npmName, false), otherFilesRun, System.currentTimeMillis(), "cross-version-analysis", "Cross", lang);
      trackedFragment("2", "cross-version-analysis-inline", r4tor4b.generate(npmName, true), otherFilesRun, System.currentTimeMillis(), "cross-version-analysis-inline", "Cross", lang);
    } else {
      fragment("cross-version-analysis", r4tor4b.generate(npmName, false), otherFilesRun, System.currentTimeMillis(), "cross-version-analysis", "Cross", lang);      
      fragment("cross-version-analysis-inline", r4tor4b.generate(npmName, true), otherFilesRun, System.currentTimeMillis(), "cross-version-analysis-inline", "Cross", lang);      
    }
    DependencyRenderer depr = new DependencyRenderer(pcm, tempDir, npmName, templateManager, makeDependencies(), context, markdownEngine, rc, specMaps);
    trackedFragment("3", "dependency-table", depr.render(publishedIg, false, true, true), otherFilesRun, System.currentTimeMillis(), "dependency-table", "Cross", lang);
    trackedFragment("3", "dependency-table-short", depr.render(publishedIg, false, false, false), otherFilesRun, System.currentTimeMillis(), "dependency-table-short", "Cross", lang);
    trackedFragment("3", "dependency-table-nontech", depr.renderNonTech(publishedIg), otherFilesRun, System.currentTimeMillis(), "dependency-table-nontech", "Cross", lang);
    trackedFragment("4", "globals-table", depr.renderGlobals(), otherFilesRun, System.currentTimeMillis(), "globals-table", "Cross", lang);
    
    fragment("related-igs-list", relatedIgsList(), otherFilesRun, System.currentTimeMillis(), "related-igs-list", "Cross", lang);
    fragment("related-igs-table", relatedIgsTable(), otherFilesRun, System.currentTimeMillis(), "related-igs-table", "Cross", lang);

    // now, list the profiles - all the profiles
    int i = 0;
    JsonObject maturities = new JsonObject();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof DomainResource) {
          String fmm = ToolingExtensions.readStringExtension((DomainResource) r.getResource(), ToolingExtensions.EXT_FMM_LEVEL);
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
          StructureDefinition base = sd.hasBaseDefinition() ? context.fetchResource(StructureDefinition.class, sd.getBaseDefinition()) : null;
          if (base != null) {
            item.add("basename", base.getName());
            item.add("basepath", Utilities.escapeXml(base.getWebPath()));
          } else if ("http://hl7.org/fhir/StructureDefinition/Base".equals(sd.getBaseDefinition())) {
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
          addTranslationsToJson(item, "description", publishedIg.getDescriptionElement(), true);
          item.add("obligations", ProfileUtilities.hasObligations(sd));
          
          if (sd.hasContext()) {
            JsonArray contexts = new JsonArray();
            item.add("contexts", contexts);
            for (StructureDefinitionContextComponent ec : sd.getContext()) {
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

    for (FetchedResource r: examples) {
      FetchedResource baseRes = getResourceForUri(r.getExampleUri());
      if (baseRes == null) {
        // We only yell if the resource doesn't exist, not only if it doesn't exist in the current IG.
        if (context.fetchResource(StructureDefinition.class, r.getExampleUri())==null) {
          FetchedFile f = findFileForResource(r);
          (f != null ? f.getErrors() : errors).add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, r.fhirType()+"/"+r.getId(), "Unable to find profile " + r.getExampleUri() + " nominated as the profile for which resource " + r.getUrlTail()+" is an example", IssueSeverity.ERROR));
        }
      } else {
        baseRes.addStatedExample(r);
      }
    }

    for (FetchedResource r : testplans) {
      if (r.hasTestArtifacts()) {
        FetchedResource baseRes = null;
        for (String tsArtifact : r.getTestArtifacts()) {
          baseRes = getResourceForUri(tsArtifact);
          if (baseRes == null) {
            // We only yell if the resource doesn't exist, not only if it doesn't exist in the current IG.
            errors.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, r.fhirType()+"/"+r.getId(), "Unable to find artifact " + tsArtifact + " nominated as the artifact for test resource " + r.getUrlTail(), IssueSeverity.WARNING));
          } else {
            baseRes.addFoundTestPlan(r);
          }
        }
      }
    }

    for (FetchedResource r : testscripts) {
      if (r.hasTestArtifacts()) {
        FetchedResource baseRes = null;
        for (String tsArtifact : r.getTestArtifacts()) {
          baseRes = getResourceForUri(tsArtifact);
          if (baseRes == null) {
            // We only yell if the resource doesn't exist, not only if it doesn't exist in the current IG.
            errors.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, r.fhirType()+"/"+r.getId(), "Unable to find artifact " + tsArtifact + " nominated as the artifact for test resource " + r.getUrlTail(), IssueSeverity.WARNING));
          } else {
            baseRes.addFoundTestScript(r);
          }
        }
      }
    }

    String json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(data, true);
    FileUtilities.stringToFile(json, Utilities.path(tempDir, "_data", "structuredefinitions.json"));

    // now, list the profiles - all the profiles
    data = new JsonObject();
    for (FetchedFile f : fileList) {
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
    FileUtilities.stringToFile(json, Utilities.path(tempDir, "_data", "questionnaires.json"));

    // now, list the profiles - all the profiles
    data = new JsonObject();
    i = 0;
    for (FetchedFile f : fileList) {
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
        for (ContainedResourceDetails crd : getContained(r.getElement())) {
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
    FileUtilities.stringToFile(json, Utilities.path(tempDir, "_data", "resources.json"));
    
    data = new JsonObject();
    if (sourceIg.hasLanguage()) {
      data.add("ig", sourceIg.getLanguage());
    }
    data.add("hasTranslations", hasTranslations);
    data.add("defLang", defaultTranslationLang);
    JsonArray langs = new JsonArray();
    data.add("langs", langs); 
    Map<String, Map<String, String>> langDisplays = loadLanguagesCsv();
    ValueSet vs = context.fetchResource(ValueSet.class, "http://hl7.org/fhir/ValueSet/languages");
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
        ConceptReferenceComponent cc = getConceptReference(vs, "urn:ietf:bcp:47", code);
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
          ConceptReferenceComponent cc = getConceptReference(vs, "urn:ietf:bcp:47", code);
          ConceptReferenceDesignationComponent dd = null;
          for (ConceptReferenceDesignationComponent t : cc.getDesignation()) {
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
    FileUtilities.stringToFile(json, Utilities.path(tempDir, "_data", "languages.json"));
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

  public ConceptReferenceComponent getConceptReference(ValueSet vs, String system, String code) {
    for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
      if (system.equals(inc.getSystem())) {
        for (ConceptReferenceComponent cc : inc.getConcept()) {
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
    div.h3().tx("Summary: "+n+" resources in "+v.toUpperCase());
    XhtmlNode tbl = null;
    for (FetchedFile f : fileList) {
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

            tr.td().tx(r.getId());
            AlternativeVersionResource vv = r.getOtherVersions().get(ver+"-"+n);
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
              for (ConversionMessage msg : vv.getLog()) {
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
    Set<String> ids = otherVersionAddedResources.get(ver+"-"+n);
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

  private String relatedIgsList() throws IOException {
    Map<RelatedIGRole, List<RelatedIG>> roles = new HashMap<>();
    for (RelatedIG ig : relatedIGs) {
      if (!roles.containsKey(ig.getRole())) {
        roles.put(ig.getRole(), new ArrayList<>());
      }
      roles.get(ig.getRole()).add(ig);
    }
    XhtmlNode x = new XhtmlNode(NodeType.Element);
    XhtmlNode ul = x.ul();
    for (RelatedIGRole r : roles.keySet()) {
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
    if (relatedIGs.isEmpty()) {
      return "";
    }
    XhtmlNode x = new XhtmlNode(NodeType.Element);
    XhtmlNode tbl = x.table("grid");
    XhtmlNode tr = tbl.tr();
    tr.th().b().tx("ID");
    tr.th().b().tx("Title");
    tr.th().b().tx("Role");
    tr.th().b().tx("Version");
    for (RelatedIG ig : relatedIGs) {
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
      item.add("url", e.getNamedChildValue("url"));
    }
      if (e.hasChildren("identifier")) {
        List<String> ids = new ArrayList<String>();
        for (Element id : e.getChildren("identifier")) {
          if (id.hasChild("value")) {
            ids.add(dr.displayDataType(ResourceWrapper.forType(cu, id)));
          }
        }
        if (!ids.isEmpty()) {
          item.add("identifiers", String.join(", ", ids));
        }
      }
      if (e.getChildren("version").size() == 1) {
        item.add("version", e.getNamedChildValue("version"));
      }
      if (e.getChildren("name").size() == 1) {
        item.add("name", e.getNamedChildValue("name"));
      }
      if (e.getChildren("title").size() == 1) {
        item.add("title", e.getNamedChildValue("title"));
//        addTranslationsToJson(item, "title", e.getNamedChild("title"), false);
      }
      if (e.getChildren("experimental").size() == 1) {
        item.add("experimental", e.getNamedChildValue("experimental"));
      }
      if (e.getChildren("date").size() == 1) {
        item.add("date", e.getNamedChildValue("date"));
      }
      if (e.getChildren("description").size() == 1) {
        item.add("description", preProcessMarkdown(e.getNamedChildValue("description")));
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
        item.add("purpose", ProfileUtilities.processRelativeUrls(e.getNamedChildValue("purpose"), "", igpkp.specPath(), context.getResourceNames(), specMaps.get(0).listTargets(), pageTargets(), false));
//        addTranslationsToJson(item, "purpose", e.getNamedChild("purpose"), false);
      }
      if (e.getChildren("status").size() == 1) {
        item.add("status", e.getNamedChildValue("status"));
      }
      if (e.getChildren("copyright").size() == 1) {
        item.add("copyright", ProfileUtilities.processRelativeUrls(e.getNamedChildValue("copyright"), "", igpkp.specPath(), context.getResourceNames(), specMaps.get(0).listTargets(), pageTargets(), false));
//        addTranslationsToJson(item, "description", e.getNamedChild("description"), false);
      }

//      if (pcr!=null && pcr.hasExtension(ToolingExtensions.EXT_FMM_LEVEL)) {
//        IntegerType fmm = pcr.getExtensionByUrl(ToolingExtensions.EXT_FMM_LEVEL).getValueIntegerType();
//        item.add("fmm", fmm.asStringValue());
//        if (fmm.hasExtension(ToolingExtensions.EXT_FMM_DERIVED)) {
//          String derivedFrom = "FMM derived from: ";
//          for (Extension ext: fmm.getExtensionsByUrl(ToolingExtensions.EXT_FMM_DERIVED)) {
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
//        for (Extension e : cs.getExtensionsByUrl(ToolingExtensions.EXT_CS_KEYWORD)) {
//          keywords.add(e.getValueStringType().asStringValue());
//        }
//      } else if (r.getResource() instanceof ValueSet) {
//        ValueSet vs = (ValueSet)r.getResource();
//        for (Extension e : vs.getExtensionsByUrl(ToolingExtensions.EXT_VS_KEYWORD)) {
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
      } else if (sourceIg.hasExtension(ToolingExtensions.EXT_STANDARDS_STATUS)) {
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

  private void genBasePages() throws IOException, Exception {
    String json;
    if (publishedIg.getDefinition().hasPage()) {
      JsonObject pages = new JsonObject();
      addPageData(pages, publishedIg.getDefinition().getPage(), "0", "", new HashMap<>());
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
      FileUtilities.stringToFile(json, Utilities.path(tempDir, "_data", "pages.json"));

      createToc();
      if (htmlTemplate != null || mdTemplate != null) {
        applyPageTemplate(htmlTemplate, mdTemplate, publishedIg.getDefinition().getPage());
      }
    }
  }
  
  private String getLangDesc(String s) throws IOException {
    if (registry == null) {
      registry = new LanguageSubtagRegistry();
      LanguageSubtagRegistryLoader loader = new LanguageSubtagRegistryLoader(registry);
      loader.loadFromDefaultResource();
    }
    LanguageTag tag = new LanguageTag(registry, s);
    return tag.present();
  }

  private String getLangDesc(String s, String l) throws IOException {
    if (registry == null) {
      registry = new LanguageSubtagRegistry();
      LanguageSubtagRegistryLoader loader = new LanguageSubtagRegistryLoader(registry);
      loader.loadFromDefaultResource();
    }
    LanguageTag tag = new LanguageTag(registry, s);
    return tag.present();
  }

  private void addTranslationsToJson(JsonObject item, String name, PrimitiveType<?> element, boolean preprocess) throws Exception {
    JsonObject ph = item.forceObject(name+"lang");
    for (String l : allLangs()) {
      String s;
      s = langUtils.getTranslationOrBase(element, l);
      if (preprocess) {
        s = preProcessMarkdown(s);
      }
      ph.add(l, s);
    }    
  }

  private void generateViewDefinitions(DBBuilder db) {
    for (String vdn : viewDefinitions) {
      logMessage("Generate View "+vdn);
      Runner runner = new Runner();
      try {
        runner.setContext(context);
        PublisherProvider pprov = new PublisherProvider(context, npmList, fileList, igpkp.getCanonical());
        runner.setProvider(pprov);
        runner.setStorage(new StorageSqlite3(db.getConnection()));
        JsonObject vd = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(new File(Utilities.path(FileUtilities.getDirectoryForFile(configFile), vdn)));
        pprov.inspect(vd);
        runner.execute(vd);
        captureIssues(vdn, runner.getIssues());
        
        StorageJson jstore = new StorageJson();
        runner.setStorage(jstore);
        runner.execute(vd);
        String filename = Utilities.path(tempDir, vd.asString("name")+".json");
        FileUtilities.stringToFile(org.hl7.fhir.utilities.json.parser.JsonParser.compose(jstore.getRows(), true), filename);
        otherFilesRun.add(filename);
        } catch (Exception e) {
        e.printStackTrace();
        errors.add(new ValidationMessage(Source.Publisher, IssueType.REQUIRED, vdn, "Error Processing ViewDefinition: "+e.getMessage(), IssueSeverity.ERROR));
        captureIssues(vdn, runner.getIssues());
      }      
    }    
  }

  private void captureIssues(String vdn, List<ValidationMessage> issues) {
    if (issues != null) {
      for (ValidationMessage msg : issues) {
        ValidationMessage nmsg = new ValidationMessage(msg.getSource(), msg.getType(), msg.getLine(), msg.getCol(), vdn+msg.getLocation(), msg.getMessage(), msg.getLevel());
        errors.add(nmsg);
      }    
    }
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
      
      db.addToCSList(view, cs, oids, rl);
      
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
    FileUtilities.stringToFile(b.toString(), Utilities.path(tempDir, name+".csv"));
    otherFilesRun.add(Utilities.path(tempDir, name+".csv"));    
    org.hl7.fhir.utilities.json.parser.JsonParser.compose(json, new File(Utilities.path(tempDir, name+".json")), true);
    otherFilesRun.add(Utilities.path(tempDir, name+".json"));    
  }

  private void saveVSList(String name, List<ValueSet> vslist, DBBuilder db, int view) throws Exception {
    StringBuilder b = new StringBuilder();
    JsonObject json = new JsonObject();
    JsonArray items = new JsonArray();
    json.add("codeSystems", items);
    
    b.append("URL,Version,Status,OIDs,Name,Title,Descriptino,Uses,Used,Sources\r\n");
    
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

      Set<String> used = ValueSetUtilities.listSystems(context, vs);
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
      
      db.addToVSList(view, vs, oids, used, sources, rl);
      
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
    FileUtilities.stringToFile(b.toString(), Utilities.path(tempDir, name+".csv"));
    otherFilesRun.add(Utilities.path(tempDir, name+".csv"));    
    org.hl7.fhir.utilities.json.parser.JsonParser.compose(json, new File(Utilities.path(tempDir, name+".json")), true);
    otherFilesRun.add(Utilities.path(tempDir, name+".json"));   
  }

  private List<DependencyAnalyser.ArtifactDependency> makeDependencies() {
    DependencyAnalyser analyser = new DependencyAnalyser(context);
    for (FetchedFile f : fileList) {
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
    this.dependencyList = analyser.getList();
    return analyser.getList();
  }

  public void populateResourceEntry(FetchedResource r, JsonObject item, ContainedResourceDetails crd) throws Exception {
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
            ids.add(dr.displayDataType(id));
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
          String label = dr.displayDataType(uc.getCode());
          if (uc.hasValueCodeableConcept()) {
            String value = dr.displayDataType(uc.getValueCodeableConcept());
            if (value!=null) {
              contexts.add(label + ":\u00A0" + value);
            }
          } else if (uc.hasValueQuantity()) {
            String value = dr.displayDataType(uc.getValueQuantity());
            if (value!=null)
              contexts.add(label + ":\u00A0" + value);
          } else if (uc.hasValueRange()) {
            String value = dr.displayDataType(uc.getValueRange());
            if (!value.isEmpty())
              contexts.add(label + ":\u00A0" + value);

          } else if (uc.hasValueReference()) {
            String value = null;
            String reference = null;
            if (uc.getValueReference().hasReference()) {
              reference = uc.getValueReference().getReference().contains(":") ? "" : igpkp.getCanonical() + "/";
              reference += uc.getValueReference().getReference();
            }
            if (uc.getValueReference().hasDisplay()) {
              if (reference != null)
                value = "[" + uc.getValueReference().getDisplay() + "](" + reference + ")";
              else
                value = uc.getValueReference().getDisplay();
            } else if (reference!=null)
              value = "[" + uc.getValueReference().getReference() + "](" + reference + ")";
            else if (uc.getValueReference().hasIdentifier()) {
              String idLabel = dr.displayDataType(uc.getValueReference().getIdentifier().getType());
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
        File flagDir = new File(tempDir + "/assets/images");
        if (!flagDir.exists())
          flagDir.mkdirs();
        JsonArray jNodes = new JsonArray();
        item.add("jurisdictions", jNodes);
        ValueSet jvs = context.fetchResource(ValueSet.class, "http://hl7.org/fhir/ValueSet/jurisdiction");
        for (CodeableConcept cc : cr.getJurisdiction()) {
          JsonObject jNode = new JsonObject();
          jNodes.add(jNode);
          ValidationResult vr = jvs==null ? null : context.validateCode(new ValidationOptions(FhirPublication.R5, "en-US"),  cc, jvs);
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
                File flagFile = new File(vsCache + "/" + code + ".svg");
                if (!flagFile.exists() && !ignoreFlags.contains(code)) {
                  URL url2 = new URL("https://flagcdn.com/" + shortCountryCode.get(code.toUpperCase()).toLowerCase() + ".svg");
                  try {
                    InputStream in = url2.openStream();
                    Files.copy(in, Paths.get(flagFile.getAbsolutePath()));
                  } catch (Exception e2) {
                    ignoreFlags.add(code);
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
                File flagFile = new File(vsCache + "/" + code + ".svg");
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
              System.out.println("ERROR: Unable to populate flag information");
            }
          } else{
            jNode.add("name", dr.displayDataType(cc));
          }
        }
      }
      if (pcr != null && pcr.hasStatus())
        item.add("status", pcr.getStatus().toCode());
      if (cr.hasPurpose())
        item.add("purpose", ProfileUtilities.processRelativeUrls(cr.getPurpose(), "", igpkp.specPath(), context.getResourceNames(), specMaps.get(0).listTargets(), pageTargets(), false));

      if (cr.hasCopyright()) {
        item.add("copyright", cr.getCopyright());
        addTranslationsToJson(item, "copyright", cr.getCopyrightElement(), false);
      }
      if (pcr!=null && pcr.hasExtension(ToolingExtensions.EXT_FMM_LEVEL)) {
        IntegerType fmm = pcr.getExtensionByUrl(ToolingExtensions.EXT_FMM_LEVEL).getValueIntegerType();
        item.add("fmm", fmm.asStringValue());
        if (fmm.hasExtension(ToolingExtensions.EXT_FMM_DERIVED)) {
          String derivedFrom = "FMM derived from: ";
          for (Extension ext: fmm.getExtensionsByUrl(ToolingExtensions.EXT_FMM_DERIVED)) {
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
            String value = dr.displayDataType(coding);
            if (value != null)
              keywords.add(value);
          }
        }
      } else if (r.getResource() instanceof CodeSystem) {
        CodeSystem cs = (CodeSystem)r.getResource();
        for (Extension e : cs.getExtensionsByUrl(ToolingExtensions.EXT_CS_KEYWORD)) {
          keywords.add(e.getValueStringType().asStringValue());
        }
      } else if (r.getResource() instanceof ValueSet) {
        ValueSet vs = (ValueSet)r.getResource();
        for (Extension e : vs.getExtensionsByUrl(ToolingExtensions.EXT_VS_KEYWORD)) {
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
      } else if (sourceIg.hasExtension(ToolingExtensions.EXT_STANDARDS_STATUS)) {
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

  // Turn a country code into a 3-character country code;
  private String translateCountryCode(String code) throws Exception {
    setupCountries();
    String newCode = code;
    if (StringUtils.isNumeric(code)) {
      newCode = countryCodeForNumeric.get(code);
      if (newCode == null)
        throw new Exception("Unable to find numeric ISO country code: " + code);
    } else if (code.length()==2) {
      newCode = countryCodeFor2Letter.get(code);
      if (newCode == null)
        throw new Exception("Unable to find 2-char ISO country code: " + code);
    }
    return newCode.toUpperCase();
  }

  private String displayForCountryCode(String code) throws Exception {
    String newCode = translateCountryCode(code);
    return countryNameForCode.get(newCode);
  }

  private String displayForStateCode(String code) throws Exception {
    return stateNameForCode.get(code);
  }

  private void setupCountries() throws Exception {
    if (countryCodeForName!=null)
      return;
    countryCodeForName = new HashMap<String,String>();
    countryNameForCode = new HashMap<String,String>();
    countryCodeFor2Letter = new HashMap<String,String>();
    countryCodeForNumeric = new HashMap<String,String>();
    shortCountryCode = new HashMap<String,String>();
    stateNameForCode = new HashMap<String,String>();
    ignoreFlags = new ArrayList<String>();
    JsonParser p = new org.hl7.fhir.r5.formats.JsonParser(false);
    ValueSet char3 = (ValueSet)p.parse("{\"resourceType\":\"ValueSet\",\"url\":\"http://hl7.org/fhir/ValueSet/iso3166-1-3\",\"version\":\"4.0.1\",\"name\":\"Iso3166-1-3\",\"status\":\"active\",\"compose\":{\"include\":[{\"system\":\"urn:iso:std:iso:3166\",\"filter\":[{\"property\":\"code\",\"op\":\"regex\",\"value\":\"^[A-Z]{3}$\"}]}]}}");
    ValueSet char2 = (ValueSet)p.parse("{\"resourceType\":\"ValueSet\",\"url\":\"http://hl7.org/fhir/ValueSet/iso3166-1-2\",\"version\":\"4.0.1\",\"name\":\"Iso3166-1-2\",\"status\":\"active\",\"compose\":{\"include\":[{\"system\":\"urn:iso:std:iso:3166\",\"filter\":[{\"property\":\"code\",\"op\":\"regex\",\"value\":\"^[A-Z]{2}$\"}]}]}}");
    ValueSet num = (ValueSet)p.parse("{\"resourceType\":\"ValueSet\",\"url\":\"http://hl7.org/fhir/ValueSet/iso3166-1-N\",\"version\":\"4.0.1\",\"name\":\"Iso3166-1-N\",\"status\":\"active\",\"compose\":{\"include\":[{\"system\":\"urn:iso:std:iso:3166\",\"filter\":[{\"property\":\"code\",\"op\":\"regex\",\"value\":\"^[0-9]{3}$\"}]}]}}");
    ValueSet state = (ValueSet)p.parse("{\"resourceType\":\"ValueSet\",\"url\":\"http://hl7.org/fhir/ValueSet/jurisdiction\",\"version\":\"4.0.1\",\"name\":\"JurisdictionValueSet\",\"status\":\"active\",\"compose\":{\"include\":[{\"system\":\"urn:iso:std:iso:3166:-2\"}]}}");
    ValueSetExpansionOutcome char3Expand = context.expandVS(char3,true,false);
    ValueSetExpansionOutcome char2Expand = context.expandVS(char2,true,false);
    ValueSetExpansionOutcome numExpand = context.expandVS(num,true,false);
    ValueSetExpansionOutcome stateExpand = context.expandVS(state,true,false);
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
    for (ValueSetExpansionContainsComponent c: char3Expand.getValueset().getExpansion().getContains()) {
      if (!c.hasDisplay())
        System.out.println("No display value for 3-character country code " + c.getCode());
      else {
        countryCodeForName.put(c.getDisplay(), c.getCode());
        countryNameForCode.put(c.getCode(), c.getDisplay());
      }
    }
    for (ValueSetExpansionContainsComponent c: char2Expand.getValueset().getExpansion().getContains()) {
      if (!c.hasDisplay())
        System.out.println("No display value for 2-character country code " + c.getCode());
      else {
        String code = countryCodeForName.get(c.getDisplay());
        if (code==null) {
          switch (c.getDisplay()) {
            case "Åland Islands":
              code = countryCodeForName.get("Eland Islands");
              break;
            case "Côte d''Ivoire":
              code = countryCodeForName.get("Ctte d'Ivoire");
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
              code = countryCodeForName.get("Saint Barthilemy");
              break;
            case "United Kingdom of Great Britain and Northern Ireland":
              code = countryCodeForName.get("United Kingdom");
              break;
            case "Virgin Islands,":
              code = countryCodeForName.get("Virgin Islands, U.S.");
              break;
            default:
              throw new Exception("Unable to find 3-character code having same country code as ISO 2-char code " + c.getCode() + " - " + c.getDisplay());
          }
        }
        countryCodeFor2Letter.put(c.getCode(), code);
        shortCountryCode.put(code, c.getCode());
      }
    }
    for (ValueSetExpansionContainsComponent c: numExpand.getValueset().getExpansion().getContains()) {
      String code = countryCodeForName.get(c.getDisplay());
//      if (code==null)
//        throw new Exception("Unable to find 3-character code having same country code as ISO numeric code " + c.getCode() + " - " + c.getDisplay());
      countryCodeForNumeric.put(c.getCode(), code);
    }
    for (ValueSetExpansionContainsComponent c: stateExpand.getValueset().getExpansion().getContains()) {
      if (c.getSystem().equals("urn:iso:std:iso:3166:-2"))
        stateNameForCode.put(c.getCode(), c.getDisplay());
    }
  }


  private void generateCanonicalSummary(String lang) throws IOException {
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
    FileUtilities.stringToFile(json, Utilities.path(tempDir, "_data", "canonicals.json"));
    FileUtilities.stringToFile(json, Utilities.path(tempDir, "canonicals.json"));
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
    fragment("canonical-index", xhtml, otherFilesRun, start, "canonical-index", "Cross", lang);
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
    String p = page.getName();
    String sourceName = null;
    String template = null;
    if (htmlTemplate != null && page.getGeneration() == GuidePageGeneration.HTML  && !relativeNames.keySet().contains(p) && p != null && p.endsWith(".html")) {
      sourceName = p.substring(0, p.indexOf(".html")) + ".xml";
      template = htmlTemplate;
    } else if (mdTemplate != null && page.getGeneration() == GuidePageGeneration.MARKDOWN  && !relativeNames.keySet().contains(p) && p != null && p.endsWith(".html")) {
      sourceName = p.substring(0, p.indexOf(".html")) + ".md";
      template = mdTemplate;
    }
    if (sourceName!=null) {
      String sourcePath = Utilities.path("_includes", sourceName);
      if (!relativeNames.keySet().contains(sourcePath) && !sourceName.equals("toc.xml")) {
        throw new Exception("Template based HTML file " + p + " is missing source file " + sourceName);
      }
      FetchedFile f = relativeNames.get(sourcePath);
      if (isNewML()) {
        String targetPath = Utilities.path(tempDir, p);
        if (f==null) { // toc.xml
          checkMakeFile(makeLangRedirect(p), targetPath, otherFilesRun);
        } else {
          checkMakeFile(makeLangRedirect(p), targetPath, f.getOutputNames());
        }
        for (String l : allLangs()) {
          String s = "---\r\n---\r\n{% include " + template + " lang='" + l + "' %}";
          targetPath = Utilities.path(tempDir, l, p);
          FileUtilities.stringToFile(s, targetPath);
          if (f==null) { // toc.xml
            checkMakeFile(s.getBytes(), targetPath, otherFilesRun);
          } else {
            checkMakeFile(s.getBytes(), targetPath, f.getOutputNames());
          }
        }        
      } else {
        String s = "---\r\n---\r\n{% include " + template + " %}";
        String targetPath = Utilities.path(tempDir, p);
        FileUtilities.stringToFile(s, targetPath);
        if (f==null) { // toc.xml
          checkMakeFile(s.getBytes(), targetPath, otherFilesRun);
        } else {
          checkMakeFile(s.getBytes(), targetPath, f.getOutputNames());
        }
      }
    }

    for (ImplementationGuideDefinitionPageComponent childPage : page.getPage()) {
      applyPageTemplate(htmlTemplate, mdTemplate, childPage);
    }
  }

  private byte[] makeLangRedirect(String p) {
    StringBuilder b  = new StringBuilder();
    b.append("<html><body>\r\n");
    b.append("<!--ReleaseHeader--><p id=\"publish-box\">Publish Box goes here</p><!--EndReleaseHeader-->\r\n");
    b.append("<script type=\"text/javascript\">\r\n");
    b.append("// "+HierarchicalTableGenerator.uuid+"\r\n");
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

  private String breadCrumbForPage(ImplementationGuideDefinitionPageComponent page, boolean withLink) throws FHIRException {
    if (withLink) {
      return "<li><a href='" + page.getName() + "'><b>" + Utilities.escapeXml(page.getTitle()) + "</b></a></li>";
    } else {
      return "<li><b>" + Utilities.escapeXml(page.getTitle()) + "</b></li>";
    }
  }

  private void addPageData(JsonObject pages, ImplementationGuideDefinitionPageComponent page, String label, String breadcrumb, Map<String, String> breadcrumbs) throws FHIRException, IOException {
    if (!page.hasName()) {
      errors.add(new ValidationMessage(Source.Publisher, IssueType.REQUIRED, "Base IG resource", "The page \""+page.getTitle()+"\" is missing a name/source element", IssueSeverity.ERROR));
    } else {
      addPageData(pages, page, page.getName(), page.getTitle(), label, breadcrumb, breadcrumbs);
    }
  }

  private Map<String, String> getLangTitles(StringType titleElement, String description) {
    Map<String, String> map = new HashMap<String, String>();
    for (String l : allLangs()) {
      String title = langUtils.getTranslationOrBase(titleElement, l);
      if (!description.isEmpty()) {
        title += " - " + langUtils.getTranslationOrBase(new StringType(description), l);
      }
      map.put(l, title);
    }
    return map;
  }

  private void addPageData(JsonObject pages, ImplementationGuideDefinitionPageComponent page, String source, String title, String label, String breadcrumb, Map<String, String> breadcrumbs) throws FHIRException, IOException {
    FetchedResource r = resources.get(source);
    if (r==null) {
      String fmm = ToolingExtensions.readStringExtension(page, ToolingExtensions.EXT_FMM_LEVEL);
      String status = ToolingExtensions.readStringExtension(page, ToolingExtensions.EXT_STANDARDS_STATUS);
      String normVersion = ToolingExtensions.readStringExtension(page, ToolingExtensions.EXT_NORMATIVE_VERSION);
      addPageDataRow(pages, source, title, getLangTitles(page.getTitleElement(), ""), label + (page.hasPage() ? ".0" : ""), fmm, status, normVersion, breadcrumb + breadCrumbForPage(page, false), addToBreadcrumbs(breadcrumbs, page, false), null, null, null, page);
    } else {
      Map<String, String> vars = makeVars(r);
      String outputName = determineOutputName(igpkp.getProperty(r, "base"), r, vars, null, "");
      addPageDataRow(pages, outputName, title, getLangTitles(page.getTitleElement(), ""), label, breadcrumb + breadCrumbForPage(page, false), breadcrumbs, r.getStatedExamples(), r.getFoundTestPlans(), r.getFoundTestScripts(), page);
      //      addPageDataRow(pages, source, title, label, breadcrumb + breadCrumbForPage(page, false), r.getStatedExamples());
      for (String templateName: extraTemplateList) {
        if (r.getConfig() !=null && r.getConfig().get("template-"+templateName)!=null && !r.getConfig().get("template-"+templateName).asString().isEmpty()) {
          if (templateName.equals("format")) {
            String templateDesc = extraTemplates.get(templateName);
            for (String format: template.getFormats()) {
              String formatTemplateDesc = templateDesc.replace("FMT", format.toUpperCase());
              if (wantGen(r, format)) {
                outputName = determineOutputName(igpkp.getProperty(r, "format"), r, vars, format, "");

                addPageDataRow(pages, outputName, page.getTitle() + " - " + formatTemplateDesc, getLangTitles(page.getTitleElement(), formatTemplateDesc), label, breadcrumb + breadCrumbForPage(page, false), addToBreadcrumbs(breadcrumbs, page, false), null, null, null, page);
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
              addPageDataRow(pages, outputName, page.getTitle() + " - " + templateDesc, getLangTitles(page.getTitleElement(), ""), label, breadcrumb + breadCrumbForPage(page, false), addToBreadcrumbs(breadcrumbs, page, false), null, null, null, page);
            }
          }          
        }
      }
    }

    int i = 1;
    for (ImplementationGuideDefinitionPageComponent childPage : page.getPage()) {
      addPageData(pages, childPage, (label.equals("0") ? "" : label+".") + Integer.toString(i), breadcrumb + breadCrumbForPage(page, true), addToBreadcrumbs(breadcrumbs, page, true));
      i++;
    }
  }


  private Map<String, String> addToBreadcrumbs(Map<String, String> breadcrumbs, ImplementationGuideDefinitionPageComponent page, boolean withLink) {
    Map<String, String> map = new HashMap<>();
    for (String l : allLangs()) {
      String s = breadcrumbs.containsKey(l) ? breadcrumbs.get(l) : "";
      String t = Utilities.escapeXml(langUtils.getTranslationOrBase(page.getTitleElement(), l)) ;
      if (withLink) {
        map.put(l, s + "<li><a href='" + page.getName() + "'><b>" + t+ "</b></a></li>");
      } else {
        map.put(l, s + "<li><b>" + t + "</b></li>");
      }
    }
    return map;

  }

  private void addPageDataRow(JsonObject pages, String url, String title, Map<String, String> titles, String label, String breadcrumb, Map<String, String> breadcrumbs, Set<FetchedResource> examples, Set<FetchedResource> testplans, Set<FetchedResource> testscripts, ImplementationGuideDefinitionPageComponent page) throws FHIRException, IOException {
    addPageDataRow(pages, url, title, titles, label, null, null, null, breadcrumb, breadcrumbs, examples, testplans, testscripts, page);
  }

  private void addPageDataRow(JsonObject pages, String url, String title, Map<String, String> titles, String label, String fmm, String status, String normVersion, String breadcrumb, Map<String, String> breadcrumbs, Set<FetchedResource> examples, Set<FetchedResource> testplans, Set<FetchedResource> testscripts, ImplementationGuideDefinitionPageComponent page) throws FHIRException, IOException {
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

    for (String pagesDir: pagesDirs) {
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

      TreeSet<ImplementationGuideDefinitionPageComponent> examplePages = new TreeSet<ImplementationGuideDefinitionPageComponent>(new ImplementationGuideDefinitionPageComponentComparator());
      for (FetchedResource exampleResource: examples) {
        ImplementationGuideDefinitionPageComponent page2 = pageForFetchedResource(exampleResource);
        if (page2!=null)
          examplePages.add(page2);
        // else
        //   throw new Error("Unable to find page for resource "+ exampleResource.getId());
      }
      for (ImplementationGuideDefinitionPageComponent examplePage : examplePages) {
        JsonObject exampleItem = new JsonObject();
        exampleArray.add(exampleItem);
        exampleItem.add("url", examplePage.getName());
        exampleItem.add("title", examplePage.getTitle());
      }
    }

    if (testplans != null) {
      JsonArray testplanArray = new JsonArray();
      jsonPage.add("testplans", testplanArray);

      TreeSet<ImplementationGuideDefinitionPageComponent> testplanPages = new TreeSet<ImplementationGuideDefinitionPageComponent>(new ImplementationGuideDefinitionPageComponentComparator());
      for (FetchedResource testplanResource: testplans) {
        ImplementationGuideDefinitionPageComponent page2 = pageForFetchedResource(testplanResource);
        if (page2!=null)
          testplanPages.add(page2);
      }
      for (ImplementationGuideDefinitionPageComponent testplanPage : testplanPages) {
        JsonObject testplanItem = new JsonObject();
        testplanArray.add(testplanItem);
        testplanItem.add("url", testplanPage.getName());
        testplanItem.add("title", testplanPage.getTitle());
      }
    }

    if (testscripts != null) {
      JsonArray testscriptArray = new JsonArray();
      jsonPage.add("testscripts", testscriptArray);

      TreeSet<ImplementationGuideDefinitionPageComponent> testscriptPages = new TreeSet<ImplementationGuideDefinitionPageComponent>(new ImplementationGuideDefinitionPageComponentComparator());
      for (FetchedResource testscriptResource: testscripts) {
        ImplementationGuideDefinitionPageComponent page2 = pageForFetchedResource(testscriptResource);
        if (page2!=null)
          testscriptPages.add(page2);
      }
      for (ImplementationGuideDefinitionPageComponent testscriptPage : testscriptPages) {
        JsonObject testscriptItem = new JsonObject();
        testscriptArray.add(testscriptItem);
        testscriptItem.add("url", testscriptPage.getName());
        testscriptItem.add("title", testscriptPage.getTitle());
      }
    }
    
    if (isNewML()) {
      String p = page.getName();
      String sourceName = null;
      if (htmlTemplate != null && page.getGeneration() == GuidePageGeneration.HTML  && !relativeNames.keySet().contains(p) && p != null && p.endsWith(".html")) {
        sourceName = p.substring(0, p.indexOf(".html")) + ".xml";
      } else if (mdTemplate != null && page.getGeneration() == GuidePageGeneration.MARKDOWN  && !relativeNames.keySet().contains(p) && p != null && p.endsWith(".html")) {
        sourceName = p.substring(0, p.indexOf(".html")) + ".md";
      }
      if (sourceName!=null) {
        String sourcePath = Utilities.path("_includes", sourceName);
        FetchedFile f = relativeNames.get(sourcePath);
        if (f != null) {
          for (String l : allLangs()) {
            jsonPage.forceObject("translated").add(l, defaultTranslationLang.equals(l) || f.getTranslated(l));
          }
        }
      }
    }
  }

  private void registerSubPageFile(JsonObject jsonPage, String url, String name, String value) {
    if (jsonPage.has(name) && !value.equals(jsonPage.asString("name"))) {
      errors.add(new ValidationMessage(Source.Publisher, IssueType.REQUIRED, "ToC", "Attempt to register a page file '"+name+"' more than once for the page "+url+". New Value '"+value+"', existing value '"+jsonPage.asString(name)+"'", 
          IssueSeverity.ERROR).setRuleDate("2022-12-01"));
    }
    jsonPage.set(name, value);    
  }

  private void registerPageFile(JsonObject pages, String url, JsonObject jsonPage) {
    if (pages.has(url)) {
      errors.add(new ValidationMessage(Source.Publisher, IssueType.REQUIRED, "ToC", "The ToC contains the page "+url+" more than once", IssueSeverity.ERROR).setRuleDate("2022-12-01"));
    }
    pages.set(url, jsonPage);
  }


  private void createToc() throws IOException, FHIRException {
    createToc(null, null, null);
  }

  private void createToc(ImplementationGuideDefinitionPageComponent insertPage, String insertAfterName, String insertOffset) throws IOException, FHIRException {
    String s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><div style=\"col-12\"><table style=\"border:0px;font-size:11px;font-family:verdana;vertical-align:top;\" cellpadding=\"0\" border=\"0\" cellspacing=\"0\"><tbody>";
    s = s + createTocPage(publishedIg.getDefinition().getPage(), insertPage, insertAfterName, insertOffset, null, "", "0", false, "", 0, null);
    s = s + "</tbody></table></div>";
    FileUtilities.stringToFile(s, Utilities.path(tempDir, "_includes", "toc.xml"));
    if (isNewML()) {
      for (String lang : allLangs()) {
        s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><div style=\"col-12\"><table style=\"border:0px;font-size:11px;font-family:verdana;vertical-align:top;\" cellpadding=\"0\" border=\"0\" cellspacing=\"0\"><tbody>";
        s = s + createTocPage(publishedIg.getDefinition().getPage(), insertPage, insertAfterName, insertOffset, null, "", "0", false, "", 0, lang);
        s = s + "</tbody></table></div>";
        FileUtilities.stringToFile(s, Utilities.path(tempDir, "_includes", lang, "toc.xml"));        
      }
    }
  }

  private String createTocPage(ImplementationGuideDefinitionPageComponent page, ImplementationGuideDefinitionPageComponent insertPage, String insertAfterName, String insertOffset, String currentOffset, String indents, String label, boolean last, String idPrefix, int position, String lang) throws FHIRException {
    if (position > 222) {
      position = 222;
      if (!tocSizeWarning) {
        System.out.println("Table of contents has a section with more than 222 entries.  Collapsing will not work reliably");
        tocSizeWarning  = true;
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
    for (ImplementationGuideDefinitionPageComponent childPage : page.getPage()) {
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

  private ImplementationGuideDefinitionPageComponent pageForFetchedResource(FetchedResource r) throws FHIRException {
    String key = igpkp.doReplacements(igpkp.getLinkFor(r, false), r, null, null);
    return igPages.get(key);
  }

  private class ImplementationGuideDefinitionPageComponentComparator implements Comparator<ImplementationGuideDefinitionPageComponent> {
    @Override
    public int compare(ImplementationGuideDefinitionPageComponent x, ImplementationGuideDefinitionPageComponent y) {
      try {
        return x.getName().compareTo(y.getName());
      } catch (FHIRException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
        return 0;
      }
    }
  }

  private void generateDataFile(DBBuilder db) throws Exception {
    JsonObject data = new JsonObject();
    data.add("path", checkAppendSlash(specPath));
    data.add("canonical", igpkp.getCanonical());
    data.add("igId", publishedIg.getId());
    data.add("igName", publishedIg.getName());
    data.add("packageId", npmName);
    data.add("igVer", workingVersion());
    data.add("errorCount", getErrorCount());
    data.add("version", version);
    
    StringType rl = findReleaseLabel();  
    if (rl == null) {
      data.add("releaseLabel", "n/a");
      for (String l : allLangs()) {
        data.add("releaseLabel"+l, rcLangs.get(l).formatPhrase(RenderingI18nContext._NA));
      }
    } else { 
      data.add("releaseLabel", rl.getValue());
      addTranslationsToJson(data, "releaseLabel", rl, false);
    }
    data.add("revision", specMaps.get(0).getBuild());
    data.add("versionFull", version+"-"+specMaps.get(0).getBuild());
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

    data.add("totalFiles", fileList.size());
    data.add("processedFiles", changeList.size());

    if (repoSource != null) {
      data.add("repoSource", gh());
    } else {
      String git= getGitSource();
      if (git != null) {
        data.add("repoSource", git);
      }
    }

    JsonArray rt = data.forceArray("resourceTypes");
    List<String> rtl = context.getResourceNames();
    for (String s : rtl) {
      rt.add(s);
    }
    rt = data.forceArray("dataTypes");
    ContextUtilities cu = new ContextUtilities(context, suppressedMappings);
    for (String s : cu.getTypeNames()) {
      if (!rtl.contains(s)) {
        rt.add(s);
      }
    }

    JsonObject ig = new JsonObject();
    data.add("ig", ig);
    ig.add("id", publishedIg.getId());
    ig.add("name", publishedIg.getName());
    ig.add("title", publishedIg.getTitle());
    addTranslationsToJson(ig, "title", publishedIg.getTitleElement(), false);
    ig.add("url", publishedIg.getUrl());
    ig.add("version", workingVersion());
    ig.add("status", publishedIg.getStatusElement().asStringValue());
    ig.add("experimental", publishedIg.getExperimental());
    ig.add("publisher", publishedIg.getPublisher());    
    addTranslationsToJson(ig, "publisher", publishedIg.getPublisherElement(), false);

    if (previousVersionComparator != null && previousVersionComparator.hasLast() && !targetUrl().startsWith("file:")) {
      JsonObject diff = new JsonObject();
      data.add("diff", diff);
      diff.add("name", Utilities.encodeUri(previousVersionComparator.getLastName()));
      diff.add("current", Utilities.encodeUri(targetUrl()));
      diff.add("previous", Utilities.encodeUri(previousVersionComparator.getLastUrl()));
    }    
    if (ipaComparator != null && ipaComparator.hasLast() && !targetUrl().startsWith("file:")) {
      JsonObject diff = new JsonObject();
      data.add("iga-diff", diff);
      diff.add("name", Utilities.encodeUri(ipaComparator.getLastName()));
      diff.add("current", Utilities.encodeUri(targetUrl()));
      diff.add("previous", Utilities.encodeUri(ipaComparator.getLastUrl()));
    }
    if (ipsComparator != null && ipsComparator.hasLast() && !targetUrl().startsWith("file:")) {
      JsonObject diff = new JsonObject();
      data.add("iga-diff", diff);
      diff.add("name", Utilities.encodeUri(ipsComparator.getLastName()));
      diff.add("current", Utilities.encodeUri(targetUrl()));
      diff.add("previous", Utilities.encodeUri(ipsComparator.getLastUrl()));
    }

    if (publishedIg.hasContact()) {
      JsonArray jc = new JsonArray();
      ig.add("contact", jc);
      for (ContactDetail c : publishedIg.getContact()) {
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
        for (ContactDetail c : publishedIg.getContact()) {
          JsonObject jco = new JsonObject();
          jc.add(jco);
          jco.add("name", langUtils.getTranslationOrBase(c.getNameElement(), l));
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
    ig.add("date", publishedIg.getDateElement().asStringValue());
    ig.add("description", preProcessMarkdown(publishedIg.getDescription()));
    addTranslationsToJson(ig, "description", publishedIg.getDescriptionElement(), false);

    if (context.getTxClientManager() != null && context.getTxClientManager().getMaster() != null) {
      ig.add("tx-server", context.getTxClientManager().getMaster().getAddress());
    }
    ig.add("copyright", publishedIg.getCopyright());
    addTranslationsToJson(ig, "copyright", publishedIg.getCopyrightElement(), false);

    for (Enumeration<FHIRVersion> v : publishedIg.getFhirVersion()) {
      ig.add("fhirVersion", v.asStringValue());
      break;
    }

    for (SpecMapManager sm : specMaps) {
      if (sm.getName() != null) {
        data.set(sm.getName(), appendTrailingSlashInDataFile ? sm.getBase() : Utilities.appendForwardSlash(sm.getBase()));
        if (!data.has("ver")) {
          data.add("ver", new JsonObject());
        }
        data.getJsonObject("ver").set(sm.getName(), appendTrailingSlashInDataFile ? sm.getBase2() : Utilities.appendForwardSlash(sm.getBase2()));
      }
    }
    String json = org.hl7.fhir.utilities.json.parser.JsonParser.compose(data, true);
    FileUtilities.stringToFile(json, Utilities.path(tempDir, "_data", "fhir.json"));
    JsonObject related = new JsonObject();
    for (RelatedIG rig : relatedIGs) {
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
    FileUtilities.stringToFile(json, Utilities.path(tempDir, "_data", "related.json"));
  }

  public String workingVersion() {
    return businessVersion == null ? publishedIg.getVersion() : businessVersion;
  }



  private String getGitStatus() throws IOException {
    File gitDir = new File(FileUtilities.getDirectoryForFile(configFile));
    return GitUtilities.getGitStatus(gitDir);
  }
  
  private String getGitSource() throws IOException {
    File gitDir = new File(FileUtilities.getDirectoryForFile(configFile));
    return GitUtilities.getGitSource(gitDir);
  }

  private void generateResourceReferences(String lang) throws Exception {
    Set<String> resourceTypes = new HashSet<>();
    for (StructureDefinition sd : context.fetchResourcesByType(StructureDefinition.class)) {
      if (sd.getDerivation() == TypeDerivationRule.SPECIALIZATION && sd.getKind() == StructureDefinitionKind.RESOURCE) {
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

  private void generateProfiles(String lang) throws Exception {
    long start = System.currentTimeMillis();
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
      fragment("list-profiles", list.toString(), otherFilesRun, start, "list-profiles", "Cross", lang);
      fragment("list-simple-profiles", lists.toString(), otherFilesRun, start, "list-simple-profiles", "Cross", lang);
      fragment("table-profiles", table.toString(), otherFilesRun, start, "table-profiles", "Cross", lang);
      fragment("list-profiles-mm", listMM.toString(), otherFilesRun, start, "list-profiles-mm", "Cross", lang);
      fragment("list-simple-profiles-mm", listsMM.toString(), otherFilesRun, start, "list-simple-profiles-mm", "Cross", lang);
      fragment("table-profiles-mm", tableMM.toString(), otherFilesRun, start, "table-profiles-mm", "Cross", lang);
    }
  }

  private void generateExtensions(String lang) throws Exception {
    long start = System.currentTimeMillis();
    List<Item> items = new ArrayList<Item>();
    for (FetchedFile f : fileList) {
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
    fragment("list-extensions", list.toString(), otherFilesRun, start, "list-extensions", "Cross", lang);
    fragment("list-simple-extensions", lists.toString(), otherFilesRun, start, "list-simple-extensions", "Cross", lang);
    fragment("table-extensions", table.toString(), otherFilesRun, start, "table-extensions", "Cross", lang);
    fragment("list-extensions-mm", listMM.toString(), otherFilesRun, start, "list-extensions-mm", "Cross", lang);
    fragment("list-simple-extensions-mm", listsMM.toString(), otherFilesRun, start, "list-simple-extensions-mm", "Cross", lang);
    fragment("table-extensions-mm", tableMM.toString(), otherFilesRun, start, "table-extensions-mm", "Cross", lang);
  }

  private void generateLogicals(String lang) throws Exception {
    long start = System.currentTimeMillis();
    List<Item> items = new ArrayList<Item>();
    for (FetchedFile f : fileList) {
      f.start("generateLogicals");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.fhirType().equals("StructureDefinition")) {
            StructureDefinition sd = (StructureDefinition) r.getResource();
            if (sd.getKind() == StructureDefinitionKind.LOGICAL) {
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
    fragment("list-logicals", list.toString(), otherFilesRun, start, "list-logicals", "Cross", lang);
    fragment("list-simple-logicals", lists.toString(), otherFilesRun, start, "list-simple-logicals", "Cross", lang);
    fragment("table-logicals", table.toString(), otherFilesRun, start, "table-logicals", "Cross", lang);
    fragment("list-logicals-mm", listMM.toString(), otherFilesRun, start, "list-logicals-mm", "Cross", lang);
    fragment("list-simple-logicals-mm", listsMM.toString(), otherFilesRun, start, "list-simple-logicals-mm", "Cross", lang);
    fragment("table-logicals-mm", tableMM.toString(), otherFilesRun, start, "table-logicals-mm", "Cross", lang);
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

  private void generateResourceReferences(String rt, String lang) throws Exception {
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

    genResourceReferencesList(rt, items, "", lang);
    Collections.sort(items, new ItemSorterById());
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
    fragment("list-"+ext+pm, list.toString(), otherFilesRun, start, "list-"+ext+pm, "Cross", lang);
    fragment("list-simple-"+ext+pm, lists.toString(), otherFilesRun, start, "list-simple-"+ext+pm, "Cross", lang);
    fragment("table-"+ext+pm, table.toString(), otherFilesRun, start, "table-"+ext+pm, "Cross", lang);
    fragment("list-"+ext+pm+"-json", listJ.toString(), otherFilesRun, start, "list-"+ext+pm+"-json", "Cross", lang);
    fragment("list-simple-"+ext+pm+"-json", listsJ.toString(), otherFilesRun, start, "list-simple-"+ext+pm+"-json", "Cross", lang);
    fragment("table-"+ext+pm+"-json", tableJ.toString(), otherFilesRun, start, "table-"+ext+pm+"-json", "Cross", lang);
    fragment("list-"+ext+pm+"-xml", listX.toString(), otherFilesRun, start, "list-"+ext+pm+"-xml", "Cross", lang);
    fragment("list-simple-"+ext+pm+"-xml", listsX.toString(), otherFilesRun, start, "list-simple-"+ext+pm+"-xml", "Cross", lang);
    fragment("table-"+ext+pm+"-xml", tableX.toString(), otherFilesRun, start, "table-"+ext+pm+"-xml", "Cross", lang);
  }

  @SuppressWarnings("rawtypes")
  private String getDesc(CanonicalResource r) {
    if (r.hasDescriptionElement()) {
      return r.getDescriptionElement().asStringValue();
    }
    return null;
  }

  private int getErrorCount() {
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

  private void generateNativeOutputs(FetchedFile f, boolean regen, DBBuilder db) throws IOException, FHIRException, SQLException {
    for (FetchedResource r : f.getResources()) {
      logDebugMessage(LogCategory.PROGRESS, "Produce resources for "+r.fhirType()+"/"+r.getId());
      var json = saveNativeResourceOutputs(f, r);
      if (db != null) {
        db.saveResource(f, r, json);
      }
    }    
  }
  
  private void generateHtmlOutputs(FetchedFile f, boolean regen, DBBuilder db) throws Exception {
    if (generationOff) {
      return;
    }
    if (f.getProcessMode() == FetchedFile.PROCESS_NONE) {
      String dst = Utilities.path(tempDir, f.getRelativePath());
      try {
        if (f.isFolder()) {
          f.getOutputNames().add(dst);
          FileUtilities.createDirectory(dst);
        } else {
          if (isNewML() && !f.getStatedPath().contains(File.separator+"template"+File.separator)) {
            for (String l : allLangs()) {
              if (f.getRelativePath().startsWith("_includes"+File.separator)) {
                dst = Utilities.path(tempDir, addLangFolderToFilename(f.getRelativePath(), l));                
              } else {
                dst = Utilities.path(tempDir, l, f.getRelativePath());                
              }
              byte[] src = loadTranslationSource(f, l);
              if (f.getPath().endsWith(".md")) {
                checkMakeFile(processCustomLiquid(db, stripFrontMatter(src), f), dst, f.getOutputNames());
              } else {
                checkMakeFile(processCustomLiquid(db, src, f), dst, f.getOutputNames());
              }
            }
            dst = Utilities.path(tempDir, f.getRelativePath());
            if (f.getPath().endsWith(".md")) {
              checkMakeFile(processCustomLiquid(db, stripFrontMatter(f.getSource()), f), dst, f.getOutputNames());
            } else {
              checkMakeFile(processCustomLiquid(db, f.getSource(), f), dst, f.getOutputNames());
            }
          } else {
            if (f.getPath().endsWith(".md")) {
              checkMakeFile(processCustomLiquid(db, stripFrontMatter(f.getSource()), f), dst, f.getOutputNames());
            } else {
              checkMakeFile(processCustomLiquid(db, f.getSource(), f), dst, f.getOutputNames());
            }
          }
        }
      } catch (IOException e) {
        log("Exception generating page "+dst+" for "+f.getRelativePath()+" in "+tempDir+": "+e.getMessage());

      }
    } else if (f.getProcessMode() == FetchedFile.PROCESS_XSLT) {
      String dst = Utilities.path(tempDir, f.getRelativePath());
      try {
        if (f.isFolder()) {
          f.getOutputNames().add(dst);
          FileUtilities.createDirectory(dst);
        } else {
          if (isNewML() && !f.getStatedPath().contains(File.separator+"template"+File.separator)) {
            for (String l : allLangs()) {
              if (f.getRelativePath().startsWith("_includes"+File.separator)) {
                dst = Utilities.path(tempDir, addLangFolderToFilename(f.getRelativePath(), l));                
              } else {
                dst = Utilities.path(tempDir, l, f.getRelativePath());                
              }
              byte[] src = loadTranslationSource(f, l);
              checkMakeFile(processCustomLiquid(db, new XSLTransformer(debug).transform(src, f.getXslt()), f), dst, f.getOutputNames());
            }
          } else {
            checkMakeFile(processCustomLiquid(db, new XSLTransformer(debug).transform(f.getSource(), f.getXslt()), f), dst, f.getOutputNames());
          }
        }
      } catch (Exception e) {
        log("Exception generating xslt page "+dst+" for "+f.getRelativePath()+" in "+tempDir+": "+e.getMessage());
      }
    } else {
      if (isNewML()) {
        generateHtmlOutputsInner(f, regen, db, null, rc);       
        for (String l : allLangs()) {
          generateHtmlOutputsInner(f, regen, db, l, rcLangs.get(l));        
        }        
      } else {
        generateHtmlOutputsInner(f, regen, db, null, rc);
      }
    }
  }

  private void generateHtmlOutputsInner(FetchedFile f, boolean regen, DBBuilder db, String lang, RenderingContext lrc)
      throws IOException, FileNotFoundException, Exception, Error {
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
          } else if (Utilities.existsInList(r.fhirType(), new ContextUtilities(context, suppressedMappings).getCanonicalResourceNames())) {
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
          List<Element> containedElements = r.getElement().getChildren("contained");
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
    if (generationOff) {
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

  private byte[] loadTranslationSource(FetchedFile f, String l) throws IOException, FileNotFoundException {
    byte[] src;
    if (defaultTranslationLang.equals(l)) {
      src = f.getSource();
    } else {
      File ff = null;
      for (String ts : translationSources) {
        if (Utilities.endsWithInList(ts, "/"+l, "\\"+l, "-"+l)) {
          File t = new File(Utilities.path(rootDir, ts, f.getLoadPath()));
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

  private String addLangFolderToFilename(String path, String lang) throws IOException {
    int index = path.lastIndexOf(File.separator);
    if (index < 1) {
      return path; // that's actually an error
    } else {
      return Utilities.path(path.substring(0, index),lang, path.substring(index+1));
    }
  }

  private String generateResourceFragment(FetchedFile f, FetchedResource r, String fragExpr, String syntax, List<ElideExceptDetails> excepts, List<String> elides) throws FHIRException {
    FHIRPathEngine fpe = new FHIRPathEngine(context);
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
        f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, fragExpr, "Unable to resolve expression to fragment within resource", IssueSeverity.ERROR));
        return "ERROR Expanding Fragment";

      } else if (fragNodes.size() > 1) {
        f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, fragExpr, "Found multiple occurrences of expression within resource, and only one is allowed when extracting a fragment", IssueSeverity.ERROR));
        return "ERROR Expanding Fragment";
      }
    }
    Element e = (Element)fragNodes.get(0);
    Element jsonElement = e;

    if (!elides.isEmpty() || !excepts.isEmpty()) {
//        e = e.copy();
      for (ElideExceptDetails elideExceptDetails : excepts) {
        List<Base> baseElements = new ArrayList<Base>();
        baseElements.add(e);
        String elideBaseExpr = null;
        if (elideExceptDetails.hasBase()) {
          elideBaseExpr = elideExceptDetails.getBase();
          baseElements = fpe.evaluate(e, elideBaseExpr);
          if (baseElements.isEmpty()) {
            f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, fragExpr, "Unable to find matching base elements for elideExcept expression " + elideBaseExpr + " within fragment path ", IssueSeverity.ERROR));
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
          f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, fragExpr, "Unable to find matching exclude elements for elideExcept expression " + elideExceptExpr + (elideBaseExpr == null ? "": (" within base" + elideBaseExpr)) + " within fragment path ", IssueSeverity.ERROR));
          return "ERROR Expanding Fragment";
        }
      }

      for (String elideExpr : elides) {
        List<Base> elideElements = fpe.evaluate(e, elideExpr);
        if (elideElements.isEmpty()) {
          f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, fragExpr, "Unable to find matching elements for elide expression " + elideExpr + " within fragment path ", IssueSeverity.ERROR));
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
        org.hl7.fhir.r5.elementmodel.XmlParser xp = new org.hl7.fhir.r5.elementmodel.XmlParser(context);
        XmlXHtmlRenderer x = new XmlXHtmlRenderer();
        x.setPrism(true);
        xp.setElideElements(true);
        xp.setLinkResolver(igpkp);
        xp.setShowDecorations(false);
        if (suppressId(f, r)) {
          xp.setIdPolicy(IdRenderingPolicy.NotRoot);
        }
        xp.compose(e, x);
        return x.toString();

      } else if (syntax.equals("json")) {
        JsonXhtmlRenderer j = new JsonXhtmlRenderer();
        j.setPrism(true);
        org.hl7.fhir.r5.elementmodel.JsonParser jp = new org.hl7.fhir.r5.elementmodel.JsonParser(context);
        jp.setLinkResolver(igpkp);
        jp.setAllowComments(true);
        jp.setElideElements(true);
/*        if (fragExpr != null || r.getLogicalElement() != null)
          jp.setSuppressResourceType(true);*/
        if (suppressId(f, r)) {
          jp.setIdPolicy(IdRenderingPolicy.NotRoot);
        }
        jp.compose(jsonElement, j);
        return j.toString();

      } else if (syntax.equals("ttl")) {
        org.hl7.fhir.r5.elementmodel.TurtleParser ttl = new org.hl7.fhir.r5.elementmodel.TurtleParser(context);
        ttl.setLinkResolver(igpkp);
        Turtle rdf = new Turtle();
        if (suppressId(f, r)) {
          ttl.setIdPolicy(IdRenderingPolicy.NotRoot);
        }
        ttl.setStyle(OutputStyle.PRETTY);
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

  private byte[] processCustomLiquid(DBBuilder db, byte[] content, FetchedFile f) throws FHIRException {
    if (!Utilities.existsInList(Utilities.getFileExtension(f.getPath()), "html", "md", "xml")) {
      return content;
    }
    String src = new String(content);
    try {
      boolean changed = false;
      String[] keywords = {"sql", "fragment", "json", "class-diagram", "uml", "multi-map"};
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

            case "fragment":
              substitute = processFragment(arguments, f);
              break;

            case "json":
              substitute = processJson(arguments, f);
              break;

            case "class-diagram":
              substitute = processClassDiagram(arguments, f);
              break;

            default:
              throw new FHIRException("Internal Error - unkonwn keyword "+keyword);
            }
          } catch (Exception e) {
            if (debug) {
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
      if (debug) {
        e.printStackTrace();
      } else {
        System.out.println("Error processing custom liquid in "+f.getName()+": " + e.getMessage());
      }
      return content;
    }
  }

  private String processClassDiagram(String arguments, FetchedFile f) {
    try {
      JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(arguments);
      return new ClassDiagramRenderer(Utilities.path(rootDir, "input", "diagrams"), Utilities.path(rootDir, "temp", "diagrams"), json.asString("id"), json.asString("prefix"), rc, null).buildClassDiagram(json);
    } catch (Exception e) {
      e.printStackTrace();
      return "<p style=\"color: maroon\"><b>"+Utilities.escapeXml(e.getMessage())+"</b></p>";      
    }
  }
  
  private String buildMultiMap(String arguments, FetchedFile f) {
    try {
      JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(arguments);
      return new MultiMapBuilder(rc).buildMap(json);
    } catch (Exception e) {
      e.printStackTrace();
      return "<p style=\"color: maroon\"><b>"+Utilities.escapeXml(e.getMessage())+"</b></p>";      
    }
  }

  private String processRefTag(DBBuilder db, String src, FetchedFile f) {
    if (Utilities.existsInList(src, "$ver")) {
      switch (src) {
      case "$ver": return businessVersion;
      }
    } else if (Utilities.isAbsoluteUrl(src)) {
    
      try {
        CanonicalResource cr = (CanonicalResource) context.fetchResource(Resource.class, src);
        if (cr != null && cr.hasWebPath()) {
          return "<a href=\""+cr.getWebPath()+"\">"+Utilities.escapeXml(cr.present())+"</a>";
        }
      } catch (Exception e) {
      }
    } else {
      for (FetchedFile f1 : fileList) {
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
        StructureDefinition sd = context.fetchTypeDefinition(src);
        if (sd != null) {
          return "<a href=\""+sd.getWebPath()+"\">"+Utilities.escapeXml(sd.present())+"</a>";
        }
      } catch (Exception e) {
        // nothing
      }
    }
    for (RelatedIG rig : relatedIGs) {
      if (rig.getId().equals(src) && rig.getWebLocation() != null) { 
        return "<a href=\""+rig.getWebLocation()+"\">"+Utilities.escapeXml(rig.getTitle())+"</a>";
      }
    }
    // use [[~[ so we don't get stuck in a loop
    return "[[~["+src+"]]]";
  }

  private int sqlIndex = 0;

  private boolean isSushi;

  private LanguageSubtagRegistry registry;

  private Map<String, FragmentUseRecord> fragmentUses = new HashMap<>();
  private boolean trackFragments = false;

  private LanguageUtils langUtils;

  private boolean simplifierMode;

  private ContextUtilities cu;

  private boolean logLoading;
  
  private JsonObject approvedIgsForCustomResources;
  private Set<String> customResourceNames = new HashSet<>();
  private List<StructureDefinition> customResources = new ArrayList<>();

  private boolean needsRegen = false;

  private ValidatorSession validatorSession;
  private LanguagePopulationPolicy langPolicy = LanguagePopulationPolicy.NONE;

  private List<String> testDataFactories;

  private Map<String, String> factoryProfileMap = new HashMap<>();

  private Map<String, Set<String>> otherVersionAddedResources= new HashMap<>();

  private String ipStmt;

  private PinningPolicy pinningPolicy = PinningPolicy.NO_ACTION;
  private String pinDest = null;

  private int pinCount;

  private UMLGenerationMode generateUml = UMLGenerationMode.NONE;

  private List<String> suppressedMappings= new ArrayList<>();

  private PublisherSigner signer;
  
  private String processSQLCommand(DBBuilder db, String src, FetchedFile f) throws FHIRException, IOException {
    long start = System.currentTimeMillis();
    String output = db == null ? "<span style=\"color: maroon\">No SQL this build</span>" : db.processSQL(src);
    int i = sqlIndex++;
    fragment("sql-"+i+"-fragment", output, f.getOutputNames(), start, "sql", "SQL", null);
    return "{% include sql-"+i+"-fragment.xhtml %}";
  }

  private String processJson(String arguments, FetchedFile f) throws FHIRException, IOException {
    long start = System.currentTimeMillis();
    String cnt = null;
    try {    
      String args = arguments.trim();
      File src = new File(Utilities.path(FileUtilities.getDirectoryForFile(configFile), args.substring(0, args.indexOf(" ")).trim()));
      File tsrc = new File(Utilities.path(FileUtilities.getDirectoryForFile(configFile), args.substring(args.indexOf(" ")+1).trim()));

      JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(src);
      LiquidEngine liquid = new LiquidEngine(context, rc.getServices());
      LiquidDocument template = liquid.parse(FileUtilities.fileToString(tsrc), tsrc.getAbsolutePath());
      BaseJsonWrapper base = new BaseJsonWrapper(json);
      cnt = liquid.evaluate(template, base, this).trim();
    } catch (Exception e) {
      XhtmlNode p = new XhtmlNode(NodeType.Element, "p");
      p.tx(e.getMessage());
      cnt = new XhtmlComposer(false, true).compose(p);
    }
    int i = sqlIndex++;
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
    List<ElideExceptDetails> excepts = new ArrayList<>();
    ElideExceptDetails currentExcept = null;
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
          currentExcept = new ElideExceptDetails(filterText);
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
        String outputPath = Utilities.path(tempDir, "_data", fileName + ".json");
        FileUtilities.stringToFile(json, outputPath);
        return "{% assign " + fileName + " = site.data." + fileName + " %}";
    } catch (Exception e) {
        return "<span style=\"color: maroon\">Error processing SQL: " + Utilities.escapeXml(e.getMessage()) + "</span>";
    }
  }

  private String genContainedIndex(FetchedResource r, List<StringPair> clist, String lang) {
    StringBuilder b = new StringBuilder();
    if (clist.size() > 0) {
      b.append("<ul>\r\n");
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
      f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, r.fhirType(), "Error Rendering Resource: "+e.getMessage(), IssueSeverity.ERROR));
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
      f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, r.fhirType(), "Error Rendering Resource: "+e.getMessage(), IssueSeverity.ERROR));
      e.printStackTrace();
      for (StackTraceElement m : e.getStackTrace()) {
        log("   "+m.toString());
      }
    }
    return result;
  }


  private void generateOutputsOperationDefinition(FetchedFile f, FetchedResource r, OperationDefinition od, Map<String, String> vars, boolean regen, String prefixForContainer, RenderingContext lrc, String lang) throws FHIRException, IOException {
    OperationDefinitionRenderer odr = new OperationDefinitionRenderer(context, checkAppendSlash(specPath), od, Utilities.path(tempDir), igpkp, specMaps, pageTargets(), markdownEngine, packge, fileList, lrc, versionToAnnotate, relatedIGs);
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

  private Set<String> pageTargets() {
    Set<String> set = new HashSet<>();
    if (sourceIg.getDefinition().getPage().hasName()) {
      set.add(sourceIg.getDefinition().getPage().getName());
    }
    listPageTargets(set, sourceIg.getDefinition().getPage().getPage());
    return set;
  }

  private void listPageTargets(Set<String> set, List<ImplementationGuideDefinitionPageComponent> list) {
    for (ImplementationGuideDefinitionPageComponent p : list) {
      if (p.hasName()) {
        set.add(p.getName());
      }
      listPageTargets(set, p.getPage());
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

    for (ListResourceEntryComponent li : resource.getEntry()) {
      if (!li.getDeleted() && li.hasItem() && li.getItem().hasReference()) {
        String ref = li.getItem().getReference();
        FetchedResource lr = getResourceForUri(f, ref);
        if (lr == null)
          lr = getResourceForRef(f, ref);
        if (lr != null) {
          list.add(new ListItemEntry(lr.fhirType(), getListId(lr), getListLink(lr), getListName(lr), getListTitle(lr), getListDesc(lr), lr.getElement()));          
        } else {
          // ok, we'll see if we can resolve it from another spec 
          Resource l = context.fetchResource(null, ref);
          if (l== null && ref.matches(Constants.LOCAL_REF_REGEX)) {
            String[] p = ref.split("\\/");
            l = context.fetchResourceById(p[0], p[1]);
          }
          if (l != null)
            list.add(new ListItemEntry(l.fhirType(), getListId(l), getListLink(l), getListName(l), getListTitle(lr), getListDesc(l), null));          
        }
      }
    }
    String script = igpkp.getProperty(r, "list-script");
    String types = igpkp.getProperty(r, "list-types"); 
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
    if (lr.getResource() != null && lr.getResource().hasWebPath())
      res = lr.getResource().getWebPath();
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
        StructureDefinition base = context.fetchResource(StructureDefinition.class, url);
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
    org.hl7.fhir.r5.elementmodel.JsonParser jp = new org.hl7.fhir.r5.elementmodel.JsonParser(context);
    Element element = r.getElement();
    Element eNN = element;
    jp.compose(element, bsj, OutputStyle.NORMAL, igpkp.getCanonical());
    if (!r.isCustomResource()) {
      npm.addFile(isExample(f,r ) ? Category.EXAMPLE : Category.RESOURCE, element.fhirTypeRoot()+"-"+r.getId()+".json", bsj.toByteArray());
      if (isNewML()) {
        for (String l : allLangs()) {
          Element le = langUtils.copyToLanguage(element, l, true); // todo: should we keep this?
          ByteArrayOutputStream bsjl = new ByteArrayOutputStream();
          jp.compose(le, bsjl, OutputStyle.NORMAL, igpkp.getCanonical());
          lnpms.get(l).addFile(isExample(f,r ) ? Category.EXAMPLE : Category.RESOURCE, element.fhirTypeRoot()+"-"+r.getId()+".json", bsjl.toByteArray());
        }
      }
      for (String v : generateVersions) {
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
            vnpms.get(v).addFile(isExample(f,r ) ? Category.EXAMPLE : Category.RESOURCE, element.fhirTypeRoot()+"-"+r.getId()+".json", resVer);
          }
        }
      }
      if (r.getResource() != null && r.getResource().hasUserData(UserDataNames.archetypeSource)) {
        addFileToNpm(Category.ADL, r.getResource().getUserString(UserDataNames.archetypeName), r.getResource().getUserString(UserDataNames.archetypeSource).getBytes(StandardCharsets.UTF_8));
      }
      
    } else  if ("StructureDefinition".equals(r.fhirType())) {
      // GG 20-Feb 2025 - how can you ever get to here? 
      addFileToNpm(Category.RESOURCE, element.fhirType()+"-"+r.getId()+".json", bsj.toByteArray());
      StructureDefinition sdt = (StructureDefinition) r.getResource().copy();
      sdt.setKind(StructureDefinitionKind.RESOURCE);
      bsj = new ByteArrayOutputStream();
      new JsonParser().setOutputStyle(OutputStyle.NORMAL).compose(bsj, sdt);
      addFileToNpm(Category.CUSTOM, "StructureDefinition-"+r.getId()+".json", bsj.toByteArray());      
    } else {
      addFileToNpm(Category.CUSTOM, element.fhirType()+"-"+r.getId()+".json", bsj.toByteArray());
      Binary bin = new Binary("application/fhir+json");
      bin.setId(r.getId());
      bin.setContent(bsj.toByteArray());
      bsj = new ByteArrayOutputStream();
      new JsonParser().setOutputStyle(OutputStyle.NORMAL).compose(bsj, bin);
      addFileToNpm(isExample(f,r ) ? Category.EXAMPLE : Category.RESOURCE, "Binary-"+r.getId()+".json", bsj.toByteArray());
    }
    
    if (module.isNoNarrative()) {
      // we don't use the narrative in these resources in _includes, so we strip it - it slows Jekyll down greatly 
      eNN = (Element) element.copy();
      eNN.removeChild("text");
      bsj = new ByteArrayOutputStream();
      jp.compose(eNN, bsj, OutputStyle.PRETTY, igpkp.getCanonical());
    }
    String path = Utilities.path(tempDir, "_includes", r.fhirType()+"-"+r.getId()+".json");
    FileUtilities.bytesToFile(bsj.toByteArray(), path);
    String pathEsc = Utilities.path(tempDir, "_includes", r.fhirType()+"-"+r.getId()+".escaped.json");
    XmlEscaper.convert(path, pathEsc);

    saveNativeResourceOutputFormats(f, r, element, ""); 
    for (String lang : allLangs()) {
      Element e = (Element) element.copy();
      if (langUtils.switchLanguage(e, lang, true)) {
        saveNativeResourceOutputFormats(f, r, e, lang);         
      }
    }
    
    return bsj.toByteArray();
  }

  private byte[] convVersion(Resource res, String v) throws FHIRException, IOException {
    if (res.hasWebPath() && (res instanceof DomainResource)) {
      ToolingExtensions.setUrlExtension((DomainResource) res, ToolingExtensions.EXT_WEB_SOURCE_NEW, res.getWebPath());
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
      ig.getFhirVersion().add(new Enumeration<>(new FHIRVersionEnumFactory(), version));
      ig.setPackageId(publishedIg.getPackageId()+"."+v);
    }
    if (res instanceof StructureDefinition) {
      StructureDefinition sd = (StructureDefinition) res;
      sd.setFhirVersion(FHIRVersion.fromCode(v));
    }
  }
  
  private void saveNativeResourceOutputFormats(FetchedFile f, FetchedResource r, Element element, String lang) throws IOException, FileNotFoundException {
    String path;
    if (wantGen(r, "xml") || forHL7orFHIR()) {
      if (newMultiLangTemplateFormat && lang != null) {
        path = Utilities.path(tempDir, lang, r.fhirType()+"-"+r.getId()+".xml");        
      } else {
        path = Utilities.path(tempDir, r.fhirType()+"-"+r.getId()+".xml");
      }
      f.getOutputNames().add(path);
      FileOutputStream stream = new FileOutputStream(path);
      org.hl7.fhir.r5.elementmodel.XmlParser xp = new org.hl7.fhir.r5.elementmodel.XmlParser(context);
      if (suppressId(f, r)) {
        xp.setIdPolicy(IdRenderingPolicy.NotRoot);
      }
      xp.compose(element, stream, OutputStyle.PRETTY, igpkp.getCanonical());
      stream.close();
    }
    if (wantGen(r, "json") || forHL7orFHIR()) {
      if (newMultiLangTemplateFormat && lang != null) {
        path = Utilities.path(tempDir, lang, r.fhirType()+"-"+r.getId()+".json");        
      } else {
        path = Utilities.path(tempDir, r.fhirType()+"-"+r.getId()+".json");
      }
      f.getOutputNames().add(path);
      FileOutputStream stream = new FileOutputStream(path);
      org.hl7.fhir.r5.elementmodel.JsonParser jp = new org.hl7.fhir.r5.elementmodel.JsonParser(context); 
      if (suppressId(f, r)) {
        jp.setIdPolicy(IdRenderingPolicy.NotRoot);
      }
      jp.compose(element, stream, OutputStyle.PRETTY, igpkp.getCanonical());
      stream.close();
    } 
    if (wantGen(r, "ttl")) {
      if (newMultiLangTemplateFormat && lang != null) {
        path = Utilities.path(tempDir, lang, r.fhirType()+"-"+r.getId()+".ttl");        
      } else {
        path = Utilities.path(tempDir, r.fhirType()+"-"+r.getId()+".ttl");
      }
      f.getOutputNames().add(path);
      FileOutputStream stream = new FileOutputStream(path);
      org.hl7.fhir.r5.elementmodel.TurtleParser tp = new org.hl7.fhir.r5.elementmodel.TurtleParser(context);
      if (suppressId(f, r)) {
        tp.setIdPolicy(IdRenderingPolicy.NotRoot);
      }
      tp.compose(element, stream, OutputStyle.PRETTY, igpkp.getCanonical());
      stream.close();
    }
  }

  private boolean isExample(FetchedFile f, FetchedResource r) {
    ImplementationGuideDefinitionResourceComponent igr = findIGReference(r.fhirType(), r.getId());
    if (igr == null)
      return false;
    else 
      return igr.getIsExample();
  }


  private boolean forHL7orFHIR() {
    return igpkp.getCanonical().contains("hl7.org") || igpkp.getCanonical().contains("fhir.org") ;
  }


  private void saveFileOutputs(FetchedFile f, String lang) throws IOException, FHIRException {
    long start = System.currentTimeMillis();
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
      fragment(r.fhirType()+"-"+r.getId()+"-validation", b.toString(), f.getOutputNames(), r, vars, null, start, "file", "File", lang);
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

  private void makeTemplatesContained(FetchedFile f, FetchedResource r, Resource res, Map<String, String> vars, String prefixForContained, String lang) throws FileNotFoundException, Exception {
    String baseName = igpkp.getPropertyContained(r, "base", res);
    if (res != null && res instanceof StructureDefinition) {
      if (igpkp.hasProperty(r, "template-base-"+((StructureDefinition) res).getKind().toCode().toLowerCase(), res))
        genWrapperContained(f, r, res, igpkp.getPropertyContained(r, "template-base-"+((StructureDefinition) res).getKind().toCode().toLowerCase(), res), baseName, f.getOutputNames(), vars, null, "", prefixForContained, lang);
      else
        genWrapperContained(f, r, res, igpkp.getPropertyContained(r, "template-base", res), baseName, f.getOutputNames(), vars, null, "", prefixForContained, lang);
    } else
      genWrapperContained(f, r, res, igpkp.getPropertyContained(r, "template-base", res), baseName, f.getOutputNames(), vars, null, "", prefixForContained, lang);
    genWrapperContained(null, r, res, igpkp.getPropertyContained(r, "template-defns", res), igpkp.getPropertyContained(r, "defns", res), f.getOutputNames(), vars, null, "definitions", prefixForContained, lang);
    for (String templateName : extraTemplates.keySet()) {
      if (!templateName.equals("format") && !templateName.equals("defns") && !templateName.equals("change-history")) {
        String output = igpkp.getProperty(r, templateName);
        if (output == null)
          output = r.fhirType()+"-"+r.getId()+"_"+res.getId()+"-"+templateName+".html";
        genWrapperContained(null, r, res, igpkp.getPropertyContained(r, "template-"+templateName, res), output, f.getOutputNames(), vars, null, templateName, prefixForContained, lang);
      }
    }
  }

  private void makeTemplates(FetchedFile f, FetchedResource r, Map<String, String> vars, String lang, RenderingContext lrc) throws FileNotFoundException, Exception {
    String baseName = igpkp.getProperty(r, "base");
    if (r.getResource() != null && r.getResource() instanceof StructureDefinition) {
      if (igpkp.hasProperty(r, "template-base-"+((StructureDefinition) r.getResource()).getKind().toCode().toLowerCase(), null))
        genWrapper(f, r, igpkp.getProperty(r, "template-base-"+((StructureDefinition) r.getResource()).getKind().toCode().toLowerCase()), baseName, f.getOutputNames(), vars, null, "", true, lang, lrc);
      else
        genWrapper(f, r, igpkp.getProperty(r, "template-base"), baseName, f.getOutputNames(), vars, null, "", true, lang, lrc);
    } else
      genWrapper(f, r, igpkp.getProperty(r, "template-base"), baseName, f.getOutputNames(), vars, null, "", true, lang, lrc);
    genWrapper(null, r, igpkp.getProperty(r, "template-defns"), igpkp.getProperty(r, "defns"), f.getOutputNames(), vars, null, "definitions", false, lang, lrc);
    for (String templateName : extraTemplates.keySet()) {
      if (!templateName.equals("format") && !templateName.equals("defns")) {
        String output = igpkp.getProperty(r, templateName);
        if (output == null)
          output = r.fhirType()+"-"+r.getId()+"-"+templateName+".html";
        genWrapper(null, r, igpkp.getProperty(r, "template-"+templateName), output, f.getOutputNames(), vars, null, templateName, false, lang, lrc);
      }
    }
  }

  private void saveDirectResourceOutputs(FetchedFile f, FetchedResource r, Resource res, Map<String, String> vars, String lang, RenderingContext lrc) throws FileNotFoundException, Exception {
    Element langElement = r.getElement();
    if (lang != null) {
      langElement = langUtils.copyToLanguage(langElement, lang, true);
      res = langUtils.copyToLanguage(res, lang, true);      
    }
    boolean example = r.isExample();
    if (wantGen(r, "maturity") && res != null) {
      long start = System.currentTimeMillis();
      fragment(res.fhirType()+"-"+r.getId()+"-maturity",  genFmmBanner(r, null), f.getOutputNames(), start, "maturity", "Resource", lang);
    }
    if (wantGen(r, "ip-statements") && res != null) {
      long start = System.currentTimeMillis();
      fragment(res.fhirType()+"-"+r.getId()+"-ip-statements", new IPStatementsRenderer(context, markdownEngine, sourceIg.getPackageId(), lrc).genIpStatements(r, example, lang), f.getOutputNames(), start, "ip-statements", "Resource", lang);
    }
    if (wantGen(r, "validate")) {
      long start = System.currentTimeMillis();
      fragment(r.fhirType()+"-"+r.getId()+"-validate",  genValidation(f, r), f.getOutputNames(), start, "validate", "Resource", lang);
    }

    if (wantGen(r, "status") && res instanceof DomainResource) {
      long start = System.currentTimeMillis();
      fragment(r.fhirType()+"-"+r.getId()+"-status",  genStatus(f, r, res, null), f.getOutputNames(), start, "status", "Resource", lang);
    }

    String template = igpkp.getProperty(r, "template-format");
    if (wantGen(r, "xml")) {
      genWrapper(null, r, template, igpkp.getProperty(r, "format"), f.getOutputNames(), vars, "xml", "", false, lang, lrc);
    }
    if (wantGen(r, "json")) {
        genWrapper(null, r, template, igpkp.getProperty(r, "format"), f.getOutputNames(), vars, "json", "", false, lang, lrc);
    } 
    if (wantGen(r, "jekyll-data") && produceJekyllData) {
      org.hl7.fhir.r5.elementmodel.JsonParser jp = new org.hl7.fhir.r5.elementmodel.JsonParser(context);
      FileOutputStream bs = new FileOutputStream(Utilities.path(tempDir, "_data", r.fhirType()+"-"+r.getId()+".json"));
      jp.compose(langElement, bs, OutputStyle.NORMAL, null);
      bs.close();      
    }
    if (wantGen(r, "ttl")) {
        genWrapper(null, r, template, igpkp.getProperty(r, "format"), f.getOutputNames(), vars, "ttl", "", false, lang, lrc);
    }
    org.hl7.fhir.r5.elementmodel.XmlParser xp = new org.hl7.fhir.r5.elementmodel.XmlParser(context);
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    xp.compose(langElement, bs, OutputStyle.NORMAL, null);
    int size = bs.size();

    Element e = langElement;
    if (SpecialTypeHandler.handlesType(r.fhirType(), context.getVersion())) {
      e = new ObjectConverter(context).convert(r.getResource());
    } else if (module.isNoNarrative() && e.hasChild("text")) {
      e = (Element) e.copy();
      e.removeChild("text");
    }  

    if (wantGen(r, "xml-html")) {
      long start = System.currentTimeMillis();
      XmlXHtmlRenderer x = new XmlXHtmlRenderer();
      x.setPrism(size < PRISM_SIZE_LIMIT);
      xp.setLinkResolver(igpkp);
      xp.setShowDecorations(false);
      if (suppressId(f, r)) {
        xp.setIdPolicy(IdRenderingPolicy.NotRoot);
      }
      xp.compose(e, x);
      fragment(r.fhirType()+"-"+r.getId()+"-xml-html", x.toString(), f.getOutputNames(), r, vars, "xml", start, "xml-html", "Resource", lang);
    }
    if (wantGen(r, "json-html")) {
      long start = System.currentTimeMillis();
      JsonXhtmlRenderer j = new JsonXhtmlRenderer();
      j.setPrism(size < PRISM_SIZE_LIMIT);
      org.hl7.fhir.r5.elementmodel.JsonParser jp = new org.hl7.fhir.r5.elementmodel.JsonParser(context);
      jp.setLinkResolver(igpkp);
      jp.setAllowComments(true);
      if (suppressId(f, r)) {
        jp.setIdPolicy(IdRenderingPolicy.NotRoot);
      }
      jp.compose(e, j);
      fragment(r.fhirType()+"-"+r.getId()+"-json-html", j.toString(), f.getOutputNames(), r, vars, "json", start, "json-html", "Resource", lang);
    }

    if (wantGen(r, "ttl-html")) {
      long start = System.currentTimeMillis();
      org.hl7.fhir.r5.elementmodel.TurtleParser ttl = new org.hl7.fhir.r5.elementmodel.TurtleParser(context);
      ttl.setLinkResolver(igpkp);
      Turtle rdf = new Turtle();
      if (suppressId(f, r)) {
        ttl.setIdPolicy(IdRenderingPolicy.NotRoot);
      }
      ttl.setStyle(OutputStyle.PRETTY);
      ttl.compose(e, rdf, "");
      fragment(r.fhirType()+"-"+r.getId()+"-ttl-html", rdf.asHtml(size < PRISM_SIZE_LIMIT), f.getOutputNames(), r, vars, "ttl", start, "ttl-html", "Resource", lang);
    }

    generateHtml(f, r, res, langElement, vars, lrc, lang);
    

    if (wantGen(r, "history")) {
      long start = System.currentTimeMillis();
      XhtmlComposer xc = new XhtmlComposer(XhtmlComposer.XML, module.isNoNarrative());
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
    XhtmlComposer xc = new XhtmlComposer(XhtmlComposer.XML, module.isNoNarrative());
    if (wantGen(rX, "html")) {
      long start = System.currentTimeMillis();
      XhtmlNode xhtml = (lang == null || lang.equals(le.getNamedChildValue("language"))) ? getXhtml(f, rX, lr,le) : null;
      if (xhtml == null && HistoryGenerator.allEntriesAreHistoryProvenance(le)) {
        RenderingContext ctxt = lrc.copy(false).setParser(getTypeLoader(f, rX));
        List<ProvenanceDetails> entries = loadProvenanceForBundle(igpkp.getLinkFor(rX, true), le, f);
        xhtml = new HistoryGenerator(ctxt).generateForBundle(entries); 
        fragment(rX.fhirType()+"-"+rX.getId()+"-html", xc.compose(xhtml), f.getOutputNames(), rX, vars, null, start, "html", "Resource", lang);
      } else if (rX.fhirType().equals("Binary")) {
        String pfx = "";
        if (rX.getExampleUri() != null) {
          StructureDefinition sd = context.fetchResource(StructureDefinition.class, rX.getExampleUri());
          if (sd != null && sd.getKind() == StructureDefinitionKind.LOGICAL) {
            pfx = "<p>This content is an example of the <a href=\""+Utilities.escapeXml(sd.getWebPath())+"\">"+Utilities.escapeXml(sd.present())+"</a> Logical Model and is not a FHIR Resource</p>\r\n";
          }          
        }
        String html = null;
        BinaryRenderer br = new BinaryRenderer(tempDir, template.getScriptMappings());
        if (lr instanceof Binary) {
          html = pfx+br.display((Binary) lr);
        } else if (le.fhirType().equals("Binary")) {
          html = pfx+br.display(le);
        } else if (f.getResources().size() == 1 && f.getSource() != null) {
          html = pfx+br.display(rX.getId(), rX.getContentType(), f.getSource());        
        } else {
          html = pfx+br.display(le);
        }
        for (String fn : br.getFilenames()) {
          otherFilesRun.add(Utilities.path(tempDir, fn));
        }
        fragment(rX.fhirType()+"-"+rX.getId()+"-html", html, f.getOutputNames(), rX, vars, null, start, "html", "Resource", lang);
      } else {
        if (xhtml == null) {
          RenderingContext xlrc = lrc.copy(false);
          ResourceRenderer rr = RendererFactory.factory(rX.fhirType(), xlrc);
          if (lr != null && lr instanceof DomainResource) {
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

  private boolean suppressId(FetchedFile f, FetchedResource r) {
    if (suppressedIds.size() == 0) {
      return false;
    } else if (suppressedIds.contains(r.getId()) || suppressedIds.contains(r.fhirType()+"/"+r.getId())) {
      return true;
    } else if (suppressedIds.get(0).equals("$examples") && r.isExample()) {
      return true;
    } else {
      return false;
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
        RenderingContext ctxt = rc.copy(false).setParser(getTypeLoader(f, r));
        List<ProvenanceDetails> entries = loadProvenanceForBundle(igpkp.getLinkFor(r, true), r.getElement(), f);
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
      fmm = ToolingExtensions.readStringExtension((DomainResource) r.getResource(), ToolingExtensions.EXT_FMM_LEVEL);
      ss = ToolingExtensions.getStandardsStatus((DomainResource) r.getResource());
    }
    if (ss == null)
      ss = StandardsStatus.TRIAL_USE;
    if (fmm != null) {
      return rcLangs.get(lang).formatPhrase(RenderingContext.FMM_TABLE, fmm, checkAppendSlash(specPath), ss.toDisplay());
    } else {
      return "";
    }
  }

  private String genStatus(FetchedFile f, FetchedResource r, Resource resource, String lang) throws FHIRException {
    org.hl7.fhir.igtools.renderers.StatusRenderer.ResourceStatusInformation info = StatusRenderer.analyse((DomainResource) resource);
    return StatusRenderer.render(igpkp.specPath(), info, rcLangs.get(lang));
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

  private void genWrapper(FetchedFile ff, FetchedResource r, String template, String outputName, Set<String> outputTracker, Map<String, String> vars, String format, String extension, boolean recordPath, String lang, RenderingContext lrc) throws FileNotFoundException, IOException, FHIRException {
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
      String path = Utilities.path(tempDir, outputName);
      if (recordPath)
        r.setPath(outputName);
      checkMakeFile(makeLangRedirect(outputName), path, outputTracker);
    }
  }

  private void genWrapperInner(FetchedFile ff, FetchedResource r, String template, String outputName, Set<String> outputTracker, Map<String, String> vars, String format, String extension, boolean recordPath, String lang) throws FileNotFoundException, IOException, FHIRException {

    template = fetcher.openAsString(Utilities.path(fetcher.pathForFile(configFile), template));
    if (vars == null) {
      vars = new HashMap<String, String>();
    }
    if (lang != null) {
      vars.put("lang", lang);
    }
    vars.put("langsuffix", lang==null? "" : ("-" + lang));

    template = igpkp.doReplacements(template, r, vars, format);

    outputName = determineOutputName(outputName, r, vars, format, extension);
    if (!outputName.contains("#")) {
      String path = lang == null ? Utilities.path(tempDir, outputName) : Utilities.path(tempDir, lang, outputName);
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
        outputName = determineOutputName(outputName, r, res, vars, format, extension, prefixForContained);
        if (isNewML() && lang == null) {
          genWrapperRedirect(ff, r, outputName, outputTracker, vars, format, extension, true);
        } else {
          template = fetcher.openAsString(Utilities.path(fetcher.pathForFile(configFile), template));
          template = igpkp.doReplacements(template, r, res, vars, format, prefixForContained);
  
          if (!outputName.contains("#")) {
            String path = lang == null ? Utilities.path(tempDir, outputName) : Utilities.path(tempDir, lang, outputName);
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
   * @throws org.hl7.fhir.exceptions.FHIRException
   * @throws Exception
   */
  private void generateOutputsCodeSystem(FetchedFile f, FetchedResource fr, CodeSystem cs, Map<String, String> vars, String prefixForContainer, RenderingContext lrc, String lang) throws Exception {
    CodeSystemRenderer csr = new CodeSystemRenderer(context, specPath, cs, igpkp, specMaps, pageTargets(), markdownEngine, packge, lrc, versionToAnnotate, relatedIGs);
    if (wantGen(fr, "summary")) {
      long start = System.currentTimeMillis();
      fragment("CodeSystem-"+prefixForContainer+cs.getId()+"-summary", csr.summaryTable(fr, wantGen(fr, "xml"), wantGen(fr, "json"), wantGen(fr, "ttl"), igpkp.summaryRows()), f.getOutputNames(), fr, vars, null, start, "summary", "CodeSystem", lang);
    }
    if (wantGen(fr, "summary-table")) {
      long start = System.currentTimeMillis();
      fragment("CodeSystem-"+prefixForContainer+cs.getId()+"-summary-table", csr.summaryTable(fr, wantGen(fr, "xml"), wantGen(fr, "json"), wantGen(fr, "ttl"), igpkp.summaryRows()), f.getOutputNames(), fr, vars, null, start, "summary-table", "CodeSystem", lang);
    }
    if (wantGen(fr, "content")) {
      long start = System.currentTimeMillis();
      fragment("CodeSystem-"+prefixForContainer+cs.getId()+"-content", csr.content(otherFilesRun), f.getOutputNames(), fr, vars, null, start, "content", "CodeSystem", lang);
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
      CodeSystemSpreadsheetGenerator vsg = new CodeSystemSpreadsheetGenerator(context);
      if (vsg.canGenerate(cs)) {
        String path = Utilities.path(tempDir, "CodeSystem-"+prefixForContainer + cs.getId()+".xlsx");
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
    ValueSetRenderer vsr = new ValueSetRenderer(context, specPath, vs, igpkp, specMaps, pageTargets(), markdownEngine, packge, lrc, versionToAnnotate, relatedIGs);
    if (wantGen(r, "summary")) {
      long start = System.currentTimeMillis();
      fragment("ValueSet-"+prefixForContainer+vs.getId()+"-summary", vsr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary", "ValueSet", lang);
    }
    if (wantGen(r, "summary-table")) {
      long start = System.currentTimeMillis();
      fragment("ValueSet-"+prefixForContainer+vs.getId()+"-summary-table", vsr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary-table", "ValueSet", lang);
    }
    if (wantGen(r, "cld")) {
      long start = System.currentTimeMillis();
      try {
        fragment("ValueSet-"+prefixForContainer+vs.getId()+"-cld", vsr.cld(otherFilesRun), f.getOutputNames(), r, vars, null, start, "cld", "ValueSet", lang);
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
      if (vs.getStatus() == PublicationStatus.RETIRED) {
        String html = "<p style=\"color: maroon\">Expansions are not generated for retired value sets</p>";
        
        fragment("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", html, f.getOutputNames(), r, vars, null, start, "expansion", "ValueSet", lang);
      } else {           
        ValueSetExpansionOutcome exp = context.expandVS(vs, true, true, true);        
        
        db.recordExpansion(vs, exp);
        if (exp.getValueset() != null) {
          expansions.add(exp.getValueset());
          RenderingContext elrc = lrc.withUniqueLocalPrefix("x").withMode(ResourceRendererMode.END_USER);
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
            f.getErrors().add(new ValidationMessage(Source.TerminologyEngine, IssueType.INFORMATIONAL, "ValueSet.where(id = '"+vs.getId()+"')", "The value set expansion is too large, and only a subset has been displayed", IssueSeverity.INFORMATION).setTxLink(exp.getTxLink()));
          }
        } else {
          if (exp.getError() != null) { 
            if (exp.getError().contains("Unable to provide support")) {
              if (exp.getError().contains("known versions")) {
                fragmentErrorHtml("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", "No Expansion for this valueset (Unsupported Code System Version<!-- "+Utilities.escapeXml(exp.getAllErrors().toString())+" -->)", "Publication Tooling Error: "+Utilities.escapeXml(exp.getAllErrors().toString()), f.getOutputNames(), start, "expansion", "ValueSet", lang);                
              } else {
                fragmentErrorHtml("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", "No Expansion for this valueset (Unknown Code System<!-- "+Utilities.escapeXml(exp.getAllErrors().toString())+" -->)", "Publication Tooling Error: "+Utilities.escapeXml(exp.getAllErrors().toString()), f.getOutputNames(), start, "expansion", "ValueSet", lang);
              }
              f.getErrors().add(new ValidationMessage(Source.TerminologyEngine, IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", exp.getError(), IssueSeverity.WARNING).setTxLink(exp.getTxLink()));
              r.getErrors().add(new ValidationMessage(Source.TerminologyEngine, IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", exp.getError(), IssueSeverity.WARNING).setTxLink(exp.getTxLink()));
            } else if (exp.getError().contains("grammar") || exp.getError().contains("enumerated") ) {
              fragmentErrorHtml("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", "This value set cannot be expanded because of the way it is defined - it has an infinite number of members<!-- "+Utilities.escapeXml(exp.getAllErrors().toString())+" -->", "Publication Tooling Error: "+Utilities.escapeXml(exp.getAllErrors().toString()), f.getOutputNames(), start, "expansion", "ValueSet", lang);
              f.getErrors().add(new ValidationMessage(Source.TerminologyEngine, IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", exp.getError(), IssueSeverity.ERROR).setTxLink(exp.getTxLink()));
            } else if (exp.getError().contains("too many") ) {
              fragmentErrorHtml("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", "This value set cannot be expanded because the terminology server(s) deemed it too costly to do so<!-- "+Utilities.escapeXml(exp.getAllErrors().toString())+" -->", "Publication Tooling Error: "+Utilities.escapeXml(exp.getAllErrors().toString()), f.getOutputNames(), start, "expansion", "ValueSet", lang);
              f.getErrors().add(new ValidationMessage(Source.TerminologyEngine, IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", exp.getError(), IssueSeverity.ERROR).setTxLink(exp.getTxLink()));
            } else {
              fragmentErrorHtml("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", "No Expansion for this valueset (not supported by Publication Tooling<!-- "+Utilities.escapeXml(exp.getAllErrors().toString())+" -->)", "Publication Tooling Error: "+Utilities.escapeXml(exp.getAllErrors().toString()), f.getOutputNames(), start, "expansion", "ValueSet", lang);
              f.getErrors().add(new ValidationMessage(Source.TerminologyEngine, IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", exp.getError(), IssueSeverity.ERROR).setTxLink(exp.getTxLink()));
              r.getErrors().add(new ValidationMessage(Source.TerminologyEngine, IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", exp.getError(), IssueSeverity.ERROR).setTxLink(exp.getTxLink()));
            }
          } else {
            fragmentError("ValueSet-"+prefixForContainer+vs.getId()+"-expansion", "No Expansion for this valueset (not supported by Publication Tooling)", "Unknown Error", f.getOutputNames(), start, "expansion", "ValueSet", lang);
            f.getErrors().add(new ValidationMessage(Source.TerminologyEngine, IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", "Unknown Error expanding ValueSet", IssueSeverity.ERROR).setTxLink(exp.getTxLink()));
            r.getErrors().add(new ValidationMessage(Source.TerminologyEngine, IssueType.EXCEPTION, "ValueSet.where(id = '"+vs.getId()+"')", "Unknown Error expanding ValueSet", IssueSeverity.ERROR).setTxLink(exp.getTxLink()));
          }
        }
      }
    }
    if (wantGen(r, "xlsx")) {
      ValueSetSpreadsheetGenerator vsg = new ValueSetSpreadsheetGenerator(context);
      if (vsg.canGenerate(vs)) {
        String path = Utilities.path(tempDir, "ValueSet-"+prefixForContainer + r.getId()+".xlsx");
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
    ConceptMapSpreadsheetGenerator cmg = new ConceptMapSpreadsheetGenerator(context);
    if (wantGen(r, "xlsx") && cmg.canGenerate(cm)) {
      String path = Utilities.path(tempDir, prefixForContainer + r.getId()+".xlsx");
      f.getOutputNames().add(path);
      cmg.renderConceptMap(cm);
      cmg.finish(new FileOutputStream(path));
    }
  }

  private void generateOutputsCapabilityStatement(FetchedFile f, FetchedResource r, CapabilityStatement cpbs, Map<String, String> vars, String prefixForContainer, RenderingContext lrc, String lang) throws Exception {
    if (wantGen(r, "swagger") || wantGen(r, "openapi")) {
      String lp = isNewML() && lang != null && !lang.equals(defaultTranslationLang) ? "-"+lang : "";
      Writer oa = null;
      if (openApiTemplate != null) 
        oa = new Writer(new FileOutputStream(Utilities.path(tempDir, cpbs.getId()+ lp+".openapi.json")), new FileInputStream(Utilities.path(FileUtilities.getDirectoryForFile(configFile), openApiTemplate)));
      else
        oa = new Writer(new FileOutputStream(Utilities.path(tempDir, cpbs.getId()+ lp+".openapi.json")));
      String lic = license();
      String displ = context.validateCode(new ValidationOptions(FhirPublication.R5, "en-US"), new Coding("http://hl7.org/fhir/spdx-license",  lic, null), null).getDisplay();
      new OpenApiGenerator(context, cpbs, oa).generate(displ, "http://spdx.org/licenses/"+lic+".html");
      oa.commit();
      otherFilesRun.add(Utilities.path(tempDir, cpbs.getId()+lp+ ".openapi.json"));
      addFileToNpm(Category.OPENAPI, cpbs.getId()+ lp+".openapi.json", FileUtilities.fileToBytes(Utilities.path(tempDir, cpbs.getId()+lp+".openapi.json")));
    }
  }

  private void generateSpreadsheetsStructureDefinition(FetchedFile f, FetchedResource r, StructureDefinition sd, Map<String, String> vars, boolean regen, String prefixForContainer) throws Exception {
    String sdPrefix = newIg ? "StructureDefinition-" : "";
    if (wantGen(r, "csv")) {
      String path = Utilities.path(tempDir, sdPrefix + r.getId()+".csv");
      f.getOutputNames().add(path);
      ProfileUtilities pu = new ProfileUtilities(context, errors, igpkp);
      pu.generateCsv(new FileOutputStream(path), sd, true);
      if (allProfilesCsv == null) {
        allProfilesCsv = new CSVWriter(new FileOutputStream(Utilities.path(tempDir, "all-profiles.csv")), true);
        otherFilesRun.add(Utilities.path(tempDir, "all-profiles.csv"));

      }
      pu.addToCSV(allProfilesCsv, sd);
    }
    if (wantGen(r, "xlsx")) {
      lapsed(null);
      String path = Utilities.path(tempDir, sdPrefix + r.getId()+".xlsx");
      f.getOutputNames().add(path);
      StructureDefinitionSpreadsheetGenerator sdg = new StructureDefinitionSpreadsheetGenerator(context, true, anyMustSupport(sd));
      sdg.renderStructureDefinition(sd, false);
      sdg.finish(new FileOutputStream(path));
      lapsed("xslx");
      if (allProfilesXlsx == null) {
        allProfilesXlsx  = new StructureDefinitionSpreadsheetGenerator(context, true, false);
      }
      allProfilesXlsx.renderStructureDefinition(sd, true);
      lapsed("all-xslx");
    }

    if (!regen && sd.getKind() != StructureDefinitionKind.LOGICAL &&  wantGen(r, "sch")) {
      String path = Utilities.path(tempDir, sdPrefix + r.getId()+".sch");
      f.getOutputNames().add(path);
      new ProfileUtilities(context, errors, igpkp).generateSchematrons(new FileOutputStream(path), sd);
      addFileToNpm(Category.SCHEMATRON, sdPrefix + r.getId()+".sch", FileUtilities.fileToBytes(Utilities.path(tempDir, sdPrefix + r.getId()+".sch")));
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

    StructureDefinitionRenderer sdr = new StructureDefinitionRenderer(context, sourceIg.getPackageId(), checkAppendSlash(specPath), sd, Utilities.path(tempDir), igpkp, specMaps, pageTargets(), markdownEngine, packge, fileList, lrc, allInvariants, sdMapCache, specPath, versionToAnnotate, relatedIGs);

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
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-summary-table", sdr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary-table", "StructureDefinition", lang);
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
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-eview", sdr.eview(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.SUMMARY, false), f.getOutputNames(), r, vars, null, start, "eview", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-eview-all", sdr.eview(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.SUMMARY, true), f.getOutputNames(), r, vars, null, start, "eview", "StructureDefinition", lang);
    }
    if (wantGen(r, "adl")) {
      long start = System.currentTimeMillis();
      String adl = sd.hasUserData(UserDataNames.archetypeSource) ? sdr.adl() : "";
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-adl", adl, f.getOutputNames(), r, vars, null, start, "adl", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-adl-all", adl, f.getOutputNames(), r, vars, null, start, "adl", "StructureDefinition", lang);
    }
    if (wantGen(r, "diff")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-diff", sdr.diff(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.SUMMARY, false), f.getOutputNames(), r, vars, null, start, "diff", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-diff-all", sdr.diff(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.SUMMARY, true), f.getOutputNames(), r, vars, null, start, "diff", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot", sdr.snapshot(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.SUMMARY, false), f.getOutputNames(), r, vars, null, start, "snapshot", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-all", sdr.snapshot(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.SUMMARY, true), f.getOutputNames(), r, vars, null, start, "snapshot", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot-by-key")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-key", sdr.byKey(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.SUMMARY, false), f.getOutputNames(), r, vars, null, start, "snapshot-by-key", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-key-all", sdr.byKey(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.SUMMARY, true), f.getOutputNames(), r, vars, null, start, "snapshot-by-key", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot-by-mustsupport")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-mustsupport", sdr.byMustSupport(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.SUMMARY, false), f.getOutputNames(), r, vars, null, start, "snapshot-by-mustsupport", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-mustsupport-all", sdr.byMustSupport(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.SUMMARY, true), f.getOutputNames(), r, vars, null, start, "snapshot-by-mustsupport", "StructureDefinition", lang);
    }
    if (wantGen(r, "diff-bindings")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-diff-bindings", sdr.diff(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.BINDINGS, false), f.getOutputNames(), r, vars, null, start, "diff-bindings", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-diff-bindings-all", sdr.diff(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.BINDINGS, true), f.getOutputNames(), r, vars, null, start, "diff-bindings", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot-bindings")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-bindings", sdr.snapshot(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.BINDINGS, false), f.getOutputNames(), r, vars, null, start, "snapshot-bindings", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-bindings-all", sdr.snapshot(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.BINDINGS, true), f.getOutputNames(), r, vars, null, start, "snapshot-bindings", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot-by-key-bindings")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-key-bindings", sdr.byKey(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.BINDINGS, false), f.getOutputNames(), r, vars, null, start, "snapshot-by-key-bindings", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-key-bindings-all", sdr.byKey(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.BINDINGS, true), f.getOutputNames(), r, vars, null, start, "snapshot-by-key-bindings", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot-by-mustsupport-bindings")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-mustsupport-bindings", sdr.byMustSupport(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.BINDINGS, false), f.getOutputNames(), r, vars, null, start, "snapshot-by-mustsupport-bindings", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-mustsupport-bindings-all", sdr.byMustSupport(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.BINDINGS, true), f.getOutputNames(), r, vars, null, start, "snapshot-by-mustsupport-bindings", "StructureDefinition", lang);
    }
    if (wantGen(r, "diff-obligations")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-diff-obligations", sdr.diff(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.OBLIGATIONS, false), f.getOutputNames(), r, vars, null, start, "diff-obligations", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-diff-obligations-all", sdr.diff(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.OBLIGATIONS, true), f.getOutputNames(), r, vars, null, start, "diff-obligations", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot-obligations")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-obligations", sdr.snapshot(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.OBLIGATIONS, false), f.getOutputNames(), r, vars, null, start, "snapshot-obligations", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-obligations-all", sdr.snapshot(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.OBLIGATIONS, true), f.getOutputNames(), r, vars, null, start, "snapshot-obligations", "StructureDefinition", lang);
    }
    if (wantGen(r, "obligations")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-obligations", sdr.obligations(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.OBLIGATIONS, false), f.getOutputNames(), r, vars, null, start, "diff-obligations", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-obligations-all", sdr.obligations(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.OBLIGATIONS, true), f.getOutputNames(), r, vars, null, start, "diff-obligations", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot-by-key-obligations")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-key-obligations", sdr.byKey(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.OBLIGATIONS, false), f.getOutputNames(), r, vars, null, start, "snapshot-by-key-obligations", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-key-obligations-all", sdr.byKey(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.OBLIGATIONS, true), f.getOutputNames(), r, vars, null, start, "snapshot-by-key-obligations", "StructureDefinition", lang);
    }
    if (wantGen(r, "snapshot-by-mustsupport-obligations")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-mustsupport-obligations", sdr.byMustSupport(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.OBLIGATIONS, false), f.getOutputNames(), r, vars, null, start, "snapshot-by-mustsupport-obligations", "StructureDefinition", lang);
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-snapshot-by-mustsupport-obligations-all", sdr.byMustSupport(igpkp.getDefinitionsName(r), otherFilesRun, tabbedSnapshots, StructureDefinitionRendererMode.OBLIGATIONS, true), f.getOutputNames(), r, vars, null, start, "snapshot-by-mustsupport-obligations", "StructureDefinition", lang);
    }
    if (wantGen(r, "expansion")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-expansion", sdr.expansion(igpkp.getDefinitionsName(r), otherFilesRun, "x"), f.getOutputNames(), r, vars, null, start, "expansion", "StructureDefinition", lang);
    }
    if (wantGen(r, "grid")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-grid", sdr.grid(igpkp.getDefinitionsName(r), otherFilesRun), f.getOutputNames(), r, vars, null, start, "grid", "StructureDefinition", lang);
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
    if (generateUml != UMLGenerationMode.NONE) {
      long start = System.currentTimeMillis();
      try {
        ClassDiagramRenderer cdr = new ClassDiagramRenderer(Utilities.path(rootDir, "input", "diagrams"), Utilities.path(rootDir, "temp", "diagrams"), sd.getId(), null, rc, lang);
        String src = sd.getDerivation() == TypeDerivationRule.SPECIALIZATION ? cdr.buildClassDiagram(sd) : cdr.buildConstraintDiagram(sd);
        if (generateUml == UMLGenerationMode.ALL || cdr.hasSource()) {
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
        ClassDiagramRenderer cdr = new ClassDiagramRenderer(Utilities.path(rootDir, "input", "diagrams"), Utilities.path(rootDir, "temp", "diagrams"), sd.getId(), "all-", rc, lang);
        String src = sd.getDerivation() == TypeDerivationRule.SPECIALIZATION ? cdr.buildClassDiagram(sd) : cdr.buildConstraintDiagram(sd);
        if (generateUml == UMLGenerationMode.ALL || cdr.hasSource()) {
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
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-tx", sdr.tx(includeHeadings, false, false), f.getOutputNames(), r, vars, null, start, "tx", "StructureDefinition", lang);
    }
    if (wantGen(r, "tx-must-support")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-tx-must-support", sdr.tx(includeHeadings, true, false), f.getOutputNames(), r, vars, null, start, "tx-must-support", "StructureDefinition", lang);
    }
    if (wantGen(r, "tx-key")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-tx-key", sdr.tx(includeHeadings, false, true), f.getOutputNames(), r, vars, null, start, "tx-key", "StructureDefinition", lang);
    }
    if (wantGen(r, "tx-diff")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-tx-diff", sdr.txDiff(includeHeadings, false), f.getOutputNames(), r, vars, null, start, "tx-diff", "StructureDefinition", lang);
    }
    if (wantGen(r, "tx-diff-must-support")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-tx-diff-must-support", sdr.txDiff(includeHeadings, true), f.getOutputNames(), r, vars, null, start, "tx-diff-must-support", "StructureDefinition", lang);
    }
    if (wantGen(r, "inv-diff")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-inv-diff", sdr.invOldMode(includeHeadings, StructureDefinitionRenderer.GEN_MODE_DIFF), f.getOutputNames(), r, vars, null, start, "inv-diff", "StructureDefinition", lang);
    }
    if (wantGen(r, "inv-key")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-inv-key", sdr.invOldMode(includeHeadings, StructureDefinitionRenderer.GEN_MODE_KEY), f.getOutputNames(), r, vars, null, start, "inv-key", "StructureDefinition", lang);
    }
    if (wantGen(r, "inv")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-inv", sdr.invOldMode(includeHeadings, StructureDefinitionRenderer.GEN_MODE_SNAP), f.getOutputNames(), r, vars, null, start, "inv", "StructureDefinition", lang);
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
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-maps", sdr.mappings(igpkp.getDefinitionsName(r), otherFilesRun), f.getOutputNames(), r, vars, null, start, "maps", "StructureDefinition", lang);
    }
    if (wantGen(r, "xref")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-sd-xref", sdr.references(lang, lrc), f.getOutputNames(), r, vars, null, start, "xref", "StructureDefinition", lang);
    }
    if (wantGen(r, "use-context")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-sd-use-context", sdr.useContext(), f.getOutputNames(), r, vars, null, start, "use-context", "StructureDefinition", lang);
    }
    if (wantGen(r, "changes")) {
      long start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-sd-changes", sdr.changeSummary(), f.getOutputNames(), r, vars, null, start, "changes", "StructureDefinition", lang);
    }
    long start = System.currentTimeMillis();
    fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-typename", sdr.typeName(lang, lrc), f.getOutputNames(), r, vars, null, start, "-typename", "StructureDefinition", lang);
    if (sd.getDerivation() == TypeDerivationRule.CONSTRAINT && wantGen(r, "span")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-span", sdr.span(true, igpkp.getCanonical(), otherFilesRun, "sp"), f.getOutputNames(), r, vars, null, start, "span", "StructureDefinition", lang);
    }
    if (sd.getDerivation() == TypeDerivationRule.CONSTRAINT && wantGen(r, "spanall")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-spanall", sdr.span(true, igpkp.getCanonical(), otherFilesRun, "spall"), f.getOutputNames(), r, vars, null, start, "spanall", "StructureDefinition", lang);
    }

    if (wantGen(r, "example-list")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-example-list-"+prefixForContainer+sd.getId(), sdr.exampleList(fileList, true), f.getOutputNames(), r, vars, null, start, "example-list", "StructureDefinition", lang);
    }
    if (wantGen(r, "example-table")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-example-table-"+prefixForContainer+sd.getId(), sdr.exampleTable(fileList, true), f.getOutputNames(), r, vars, null, start, "example-table", "StructureDefinition", lang);
    }
    if (wantGen(r, "example-list-all")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-example-list-all-"+prefixForContainer+sd.getId(), sdr.exampleList(fileList, false), f.getOutputNames(), r, vars, null, start, "example-list-all", "StructureDefinition", lang);
    }
    if (wantGen(r, "example-table-all")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-example-table-all-"+prefixForContainer+sd.getId(), sdr.exampleTable(fileList, false), f.getOutputNames(), r, vars, null, start, "example-table-all", "StructureDefinition", lang);
    }

    if (wantGen(r, "testplan-list")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-testplan-list-"+prefixForContainer+sd.getId(), sdr.testplanList(fileList), f.getOutputNames(), r, vars, null, start, "testplan-list", "StructureDefinition", lang);
    }
    if (wantGen(r, "testplan-table")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-testplan-table-"+prefixForContainer+sd.getId(), sdr.testplanTable(fileList), f.getOutputNames(), r, vars, null, start, "testplan-table", "StructureDefinition", lang);
    }

    if (wantGen(r, "testscript-list")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-testscript-list-"+prefixForContainer+sd.getId(), sdr.testscriptList(fileList), f.getOutputNames(), r, vars, null, start, "testscript-list", "StructureDefinition", lang);
    }
    if (wantGen(r, "testscript-table")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-testscript-table-"+prefixForContainer+sd.getId(), sdr.testscriptTable(fileList), f.getOutputNames(), r, vars, null, start, "testscript-table", "StructureDefinition", lang);
    }

    if (wantGen(r, "other-versions")) {
      start = System.currentTimeMillis();
      fragment("StructureDefinition-"+prefixForContainer+sd.getId()+"-other-versions", sdr.otherVersions(f.getOutputNames(), r), f.getOutputNames(), r, vars, null, start, "other-versions", "StructureDefinition", lang);
    }
        
    for (Extension ext : sd.getExtensionsByUrl(ToolingExtensions.EXT_SD_IMPOSE_PROFILE)) {
      StructureDefinition sdi = context.fetchResource(StructureDefinition.class, ext.getValue().primitiveValue());
      if (sdi != null) {
        start = System.currentTimeMillis();
        String cid = sdi.getUserString(UserDataNames.pub_imposes_compare_id);
        fragment("StructureDefinition-imposes-"+prefixForContainer+sd.getId()+"-"+cid, sdr.compareImposes(sdi), f.getOutputNames(), r, vars, null, start, "imposes", "StructureDefinition", lang);
      }
    }

    if (wantGen(r, "java")) {
      ConstraintJavaGenerator jg = new ConstraintJavaGenerator(context, version, tempDir, sourceIg.getUrl());
      try {
        f.getOutputNames().add(jg.generate(sd));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private boolean wantGen(FetchedResource r, String code) {
    if (wantGenParams.containsKey(code)) {
      Boolean genParam = wantGenParams.get(code);
      if (!genParam.booleanValue())
        return false;
    }
    return igpkp.wantGen(r, code);    
  }
  
  private void lapsed(String msg) {
    long now = System.currentTimeMillis();
    long d = now - last;
    last = now;
  }

  private boolean anyMustSupport(StructureDefinition sd) {
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      if (ed.getMustSupport()) {
        return true;
      }
    }
    return false;
  }

  private String checkAppendSlash(String s) {
    return s.endsWith("/") ? s : s+"/";
  }

  private void generateOutputsStructureMap(FetchedFile f, FetchedResource r, StructureMap map, Map<String,String> vars, String prefixForContainer, RenderingContext lrc, String lang) throws Exception {
    StructureMapRenderer smr = new StructureMapRenderer(context, checkAppendSlash(specPath), map, Utilities.path(tempDir), igpkp, specMaps, pageTargets(), markdownEngine, packge, lrc, versionToAnnotate, relatedIGs);
    if (wantGen(r, "summary")) {
      long start = System.currentTimeMillis();
      fragment("StructureMap-"+prefixForContainer+map.getId()+"-summary", smr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary", "StructureMap", lang);
    }
    if (wantGen(r, "summary-table")) {
      long start = System.currentTimeMillis();
      fragment("StructureMap-"+prefixForContainer+map.getId()+"-summary-table", smr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary-table", "StructureMap", lang);
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
    CanonicalRenderer smr = new CanonicalRenderer(context, checkAppendSlash(specPath), cr, Utilities.path(tempDir), igpkp, specMaps, pageTargets(), markdownEngine, packge, lrc, versionToAnnotate, relatedIGs);
    if (wantGen(r, "summary")) {
      long start = System.currentTimeMillis();
      fragment(cr.fhirType()+"-"+prefixForContainer+cr.getId()+"-summary", smr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary", "Canonical", lang);
    }
    if (wantGen(r, "summary-table")) {
      long start = System.currentTimeMillis();
      fragment(cr.fhirType()+"-"+prefixForContainer+cr.getId()+"-summary-table", smr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary-table", "Canonical", lang);
    }
  }

  private void generateOutputsLibrary(FetchedFile f, FetchedResource r, Library lib, Map<String,String> vars, String prefixForContainer, RenderingContext lrc, String lang) throws Exception {
    int counter = 0;
    for (Attachment att : lib.getContent()) {
      String extension = att.hasContentType() ? MimeType.getExtension(att.getContentType()) : null;
      if (extension != null && att.hasData()) {
        String filename = "Library-"+r.getId()+(counter == 0 ? "" : "-"+Integer.toString(counter))+"."+extension;
        FileUtilities.bytesToFile(att.getData(), Utilities.path(tempDir, filename));
        otherFilesRun.add(Utilities.path(tempDir, filename));
      }
      counter++;
    }
  }

  private void generateOutputsExampleScenario(FetchedFile f, FetchedResource r, ExampleScenario scen, Map<String,String> vars, String prefixForContainer, RenderingContext lrc, String lang) throws Exception {
    ExampleScenarioRenderer er = new ExampleScenarioRenderer(context, checkAppendSlash(specPath), scen, Utilities.path(tempDir), igpkp, specMaps, pageTargets(), markdownEngine, packge, lrc.copy(false).setDefinitionsTarget(igpkp.getDefinitionsName(r)), versionToAnnotate, relatedIGs);
    if (wantGen(r, "actor-table")) {
      long start = System.currentTimeMillis();
      fragment("ExampleScenario-"+prefixForContainer+scen.getId()+"-actor-table", er.render(ExampleScenarioRendererMode.ACTORS), f.getOutputNames(), r, vars, null, start, "actor-table", "ExampleScenario", lang);
    }
    if (wantGen(r, "instance-table")) {
      long start = System.currentTimeMillis();
      fragment("ExampleScenario-"+prefixForContainer+scen.getId()+"-instance-table", er.render(ExampleScenarioRendererMode.INSTANCES), f.getOutputNames(), r, vars, null, start, "instance-table", "ExampleScenario", lang);
    }
    if (wantGen(r, "processes")) {
      long start = System.currentTimeMillis();
      fragment("ExampleScenario-"+prefixForContainer+scen.getId()+"-processes", er.render(ExampleScenarioRendererMode.PROCESSES), f.getOutputNames(), r, vars, null, start, "processes", "ExampleScenario", lang);
    }
    if (wantGen(r, "process-diagram")) {
      long start = System.currentTimeMillis();
      fragment("ExampleScenario-"+prefixForContainer+scen.getId()+"-process-diagram", er.renderDiagram(), f.getOutputNames(), r, vars, null, start, "process-diagram", "ExampleScenario", lang);
    }
  }

  private void generateOutputsQuestionnaire(FetchedFile f, FetchedResource r, Questionnaire q, Map<String,String> vars, String prefixForContainer, RenderingContext lrc, String lang) throws Exception {
    QuestionnaireRenderer qr = new QuestionnaireRenderer(context, checkAppendSlash(specPath), q, Utilities.path(tempDir), igpkp, specMaps, pageTargets(), markdownEngine, packge, lrc.copy(false).setDefinitionsTarget(igpkp.getDefinitionsName(r)), versionToAnnotate, relatedIGs);
    if (wantGen(r, "summary")) {
      long start = System.currentTimeMillis();
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-summary", qr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary", "Questionnaire", lang);
    }
    if (wantGen(r, "summary-table")) {
      long start = System.currentTimeMillis();
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-summary-table", qr.summaryTable(r, wantGen(r, "xml"), wantGen(r, "json"), wantGen(r, "ttl"), igpkp.summaryRows()), f.getOutputNames(), r, vars, null, start, "summary-table", "Questionnaire", lang);
    }
    if (wantGen(r, "tree")) {
      long start = System.currentTimeMillis();
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-tree", qr.render(QuestionnaireRendererMode.TREE), f.getOutputNames(), r, vars, null, start, "tree", "Questionnaire", lang);
    }
    if (wantGen(r, "form")) {
      long start = System.currentTimeMillis();
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-form", qr.render(QuestionnaireRendererMode.FORM), f.getOutputNames(), r, vars, null, start, "form", "Questionnaire", lang);
    }
    if (wantGen(r, "links")) {
      long start = System.currentTimeMillis();
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-links", qr.render(QuestionnaireRendererMode.LINKS), f.getOutputNames(), r, vars, null, start, "links", "Questionnaire", lang);
    }
    if (wantGen(r, "logic")) {
      long start = System.currentTimeMillis();
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-logic", qr.render(QuestionnaireRendererMode.LOGIC), f.getOutputNames(), r, vars, null, start, "logic", "Questionnaire", lang);
    }
    if (wantGen(r, "dict")) {
      long start = System.currentTimeMillis();
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-dict", qr.render(QuestionnaireRendererMode.DEFNS), f.getOutputNames(), r, vars, null, start, "dict", "Questionnaire", lang);
    }
    if (wantGen(r, "responses")) {
      long start = System.currentTimeMillis();
      fragment("Questionnaire-"+prefixForContainer+q.getId()+"-responses", responsesForQuestionnaire(q), f.getOutputNames(), r, vars, null, start, "responses", "Questionnaire", lang);
    }
  }

  private String responsesForQuestionnaire(Questionnaire q) {
    StringBuilder b = new StringBuilder();
    for (FetchedFile f : fileList) {
      f.start("responsesForQuestionnaire");
      try {

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
    RenderingContext lrc = rc.copy(false).setParser(getTypeLoader(f, r));
    String qu = getQuestionnaireURL(r);
    if (qu != null) {
      Questionnaire q = context.fetchResource(Questionnaire.class, qu);
      if (q != null && q.hasWebPath()) {
        lrc.setDefinitionsTarget(q.getWebPath());
      }
    }

    QuestionnaireResponseRenderer qr = new QuestionnaireResponseRenderer(context, checkAppendSlash(specPath), r.getElement(), Utilities.path(tempDir), igpkp, specMaps, pageTargets(), markdownEngine, packge, lrc);
    if (wantGen(r, "tree")) {
      long start = System.currentTimeMillis();
      fragment("QuestionnaireResponse-"+prefixForContainer+r.getId()+"-tree", qr.render(QuestionnaireRendererMode.TREE), f.getOutputNames(), r, vars, null, start, "tree", "QuestionnaireResponse", lang);
    }
    if (wantGen(r, "form")) {
      long start = System.currentTimeMillis();
      fragment("QuestionnaireResponse-"+prefixForContainer+r.getId()+"-form", qr.render(QuestionnaireRendererMode.FORM), f.getOutputNames(), r, vars, null, start, "form", "QuestionnaireResponse", lang);
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
    for (ImplementationGuideDefinitionResourceComponent res : publishedIg.getDefinition().getResource()) {
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
        RenderingContext lrc = rc.copy(false).setParser(getTypeLoader(f, r));
        Bundle b = (Bundle) res;
        BundleRenderer br = new BundleRenderer(lrc);
        if (br.canRender(b)) {
          return br.buildNarrative(ResourceWrapper.forResource(rc, b));
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
      return new ParametersRenderer(rc).buildNarrative(ResourceWrapper.forResource(rc, p));
    }
    if (r.fhirType().equals("Parameters")) {
      RenderingContext lrc = rc.copy(false).setParser(getTypeLoader(f, r));
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
      return new BundleRenderer(rc).buildNarrative(ResourceWrapper.forResource(rc, b));
    }
    if (resource instanceof Parameters) {
      Parameters p = (Parameters) resource;
      return new ParametersRenderer(rc).buildNarrative(ResourceWrapper.forResource(rc, p));
    }
    RenderingContext lrc = rc.copy(false).setParser(getTypeLoader(f, r));
    return RendererFactory.factory(resource, lrc).buildNarrative(ResourceWrapper.forResource(rc, resource));
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
    if (!trackedFragments.containsKey(id)) {
      trackedFragments.put(id, new ArrayList<>());      
    }
    trackedFragments.get(id).add(name+".xhtml");
    fragment(name, content+HTMLInspector.TRACK_PREFIX+id+HTMLInspector.TRACK_SUFFIX, outputTracker, null, null, null, start, code, context, lang);
  }

  private void fragment(String name, String content, Set<String> outputTracker, long start, String code, String context, String lang) throws IOException, FHIRException {
    fragment(name, content, outputTracker, null, null, null, start, code, context, lang);
  }

  private void fragment(String name, String content, Set<String> outputTracker, FetchedResource r, Map<String, String> vars, String format, long start, String code, String context, String lang) throws IOException, FHIRException {
    String fixedContent = (r==null? content : igpkp.doReplacements(content, r, vars, format))+(trackFragments ? "<!-- fragment:"+context+"."+code+" -->" : "");

    FragmentUseRecord frag = fragmentUses.get(context+"."+code);
    if (frag == null) {
      frag = new FragmentUseRecord();
      fragmentUses.put(context+"."+code, frag);
    }
    frag.record(System.currentTimeMillis() - start, fixedContent.length());

    if (checkMakeFile(FileUtilities.stringToBytes(wrapLiquid(fixedContent)), Utilities.path(tempDir, "_includes", name+(lang == null ? "" : "-" + lang)+".xhtml"), outputTracker)) {
      if (mode != IGBuildMode.AUTOBUILD && makeQA) {
        FileUtilities.stringToFile(pageWrap(fixedContent, name), Utilities.path(qaDir, name+".html"));
      }
    }
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

  public void setLogger(ILoggingService logger) {
    this.logger = logger;
    fetcher.setLogger(logger);
  }

  public String getQAFile() throws IOException {
    return Utilities.path(outputDir, "qa.html");
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
    if (usedMemory > maxMemory) {
      maxMemory = usedMemory;
    }
    
    String s = msg;
    if (tt == null) {
      System.out.println(Utilities.padRight(s, ' ', 100));      
    } else { // if (tt.longerThan(4)) {
      System.out.println(Utilities.padRight(s, ' ', 100)+" ("+tt.milestone()+" / "+tt.clock()+", "+Utilities.describeSize(usedMemory)+")");
    }
    if (killFile != null && killFile.exists()) {
      killFile.delete();
      System.out.println("Terminating Process now");    
      System.exit(1);
    }
  }

  @Override
  public void logDebugMessage(LogCategory category, String msg) {
    if (logOptions.contains(category.toString().toLowerCase())) {
      logMessage(msg);
    }
  }


  @Override
  public boolean isDebugLogging() {
    return debug;
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
    } else if (CliParams.hasNamedParam(args, "-v")){
      System.out.println(IGVersionUtil.getVersion());
    } else if (CliParams.hasNamedParam(args, "-package")) {
      System.out.println("FHIR IG Publisher "+IGVersionUtil.getVersionString());
      System.out.println("Detected Java version: " + System.getProperty("java.version")+" from "+System.getProperty("java.home")+" on "+System.getProperty("os.arch")+" ("+System.getProperty("sun.arch.data.model")+"bit). "+toMB(Runtime.getRuntime().maxMemory())+"MB available");
      System.out.println("dir = "+System.getProperty("user.dir")+", path = "+System.getenv("PATH"));
      String s = "Parameters:";
      for (int i = 0; i < args.length; i++) {
        s = s + " "+removePassword(args, i);
      }      
      System.out.println(s);
      System.out.println("character encoding = "+java.nio.charset.Charset.defaultCharset()+" / "+System.getProperty("file.encoding"));
            FilesystemPackageCacheManager pcm = CliParams.hasNamedParam(args, "system")
              ? new FilesystemPackageCacheManager.Builder().withSystemCacheFolder().build()
              : new FilesystemPackageCacheManager.Builder().build();
      System.out.println("Cache = "+pcm.getFolder());
      for (String p : CliParams.getNamedParam(args, "-package").split("\\;")) {
        NpmPackage npm = pcm.loadPackage(p);
        System.out.println("OK: "+npm.name()+"#"+npm.version()+" for FHIR version(s) "+npm.fhirVersionList()+" with canonical "+npm.canonical());
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
        throw new Error("-delete-current must have the format -delete-current {root}/{realm}/{code} -history {history} ({history} not found ("+history+"))");
      }
      if (!fh.isDirectory()) {
        throw new Error("-delete-current must have the format -delete-current {root}/{realm}/{code} -history {history} ({history} not a directory ("+history+"))");
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
          System.out.println("Publish IG "+ig);
          Publisher self = new Publisher();
          self.setConfigFile(determineActualIG(ig, null));
          setTxServerValue(args, self);
          if (CliParams.hasNamedParam(args, "-resetTx")) {
            self.setCacheOption(CacheOption.CLEAR_ALL);
          } else if (CliParams.hasNamedParam(args, "-resetTxErrors")) {
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
          FileUtilities.stringToFile(buildReport(ig, null, self.filelog.toString(), Utilities.path(self.qaDir, "validation.txt"), self.txServer), Utilities.path(System.getProperty("java.io.tmpdir"), "fhir-ig-publisher-"+Integer.toString(i)+".log"));
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
        consoleLog =  Utilities.path("[tmp]", "fhir-ig-publisher-tmp.log");
      }
      consoleLogger = new PublisherConsoleLogger();
      if (!CliParams.hasNamedParam(args, "-auto-ig-build") && !CliParams.hasNamedParam(args, "-publish-process")) {
        consoleLogger.start(consoleLog);
      }
      self.logMessage("FHIR IG Publisher "+IGVersionUtil.getVersionString());
      self.logMessage("Detected Java version: " + System.getProperty("java.version")+" from "+System.getProperty("java.home")+" on "+System.getProperty("os.name")+"/"+System.getProperty("os.arch")+" ("+System.getProperty("sun.arch.data.model")+"bit). "+toMB(Runtime.getRuntime().maxMemory())+"MB available");
      if (!"64".equals(System.getProperty("sun.arch.data.model"))) {
        self.logMessage("Attention: you should upgrade your Java to a 64bit version in order to be able to run this program without running out of memory");        
      }
      self.logMessage("dir = "+System.getProperty("user.dir")+", path = "+System.getenv("PATH"));
      String s = "Parameters:";
      for (int i = 0; i < args.length; i++) {
        s = s + " "+removePassword(args, i);
      }      
      self.logMessage(s);
      self.logMessage("Character encoding = "+java.nio.charset.Charset.defaultCharset()+" / "+System.getProperty("file.encoding"));

      //      self.logMessage("=== Environment variables =====");
      //      for (String e : System.getenv().keySet()) {
      //        self.logMessage("  "+e+": "+System.getenv().get(e));
      //      }
      self.logMessage("Start Clock @ "+nowAsString(self.execTime)+" ("+nowAsDate(self.execTime)+")");
      self.logMessage("");
      if (CliParams.hasNamedParam(args, "-auto-ig-build")) {
        self.setMode(IGBuildMode.AUTOBUILD);
        self.targetOutput = CliParams.getNamedParam(args, "-target");
        self.setRepoSource( CliParams.getNamedParam(args, "-repo"));
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
        FhirSettings.setProhibitNetworkAccess(true);
      }
      if (CliParams.hasNamedParam(args, "-trackFragments")) {
        self.trackFragments = true;
      }
      if (CliParams.hasNamedParam(args, "-milestone")) {
        self.setMilestoneBuild(true);
      }
      
      if (FhirSettings.isProhibitNetworkAccess()) {
        System.out.println("Running without network access - output may not be correct unless cache contents are correct");        
      }

      if (CliParams.hasNamedParam(args, "-validation-off")) {
        self.validationOff = true;
        System.out.println("Running without validation to shorten the run time (editor process only)");
      }
      if (CliParams.hasNamedParam(args, "-generation-off")) {
        self.generationOff = true;
        System.out.println("Running without generation to shorten the run time (editor process only)");
      }
      
      // deprecated
      if (CliParams.hasNamedParam(args, "-new-template-format")) {
        self.newMultiLangTemplateFormat = true;
        System.out.println("Using new style template format for multi-lang IGs");
      }

      setTxServerValue(args, self);
      if (CliParams.hasNamedParam(args, "-source")) {
        // run with standard template. this is publishing lite
        self.setSourceDir(CliParams.getNamedParam(args, "-source"));
        self.setDestDir(CliParams.getNamedParam(args, "-destination"));
        self.specifiedVersion = CliParams.getNamedParam(args, "-version");
      } else if (!CliParams.hasNamedParam(args, "-ig") && args.length == 1 && new File(args[0]).exists()) {
        self.setConfigFile(determineActualIG(args[0], IGBuildMode.MANUAL));
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
      } else {
        self.setConfigFile(determineActualIG(CliParams.getNamedParam(args, "-ig"), self.mode));
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
        System.out.println("Web Proxy = "+p[0]+":"+p[1]);
      }
      self.setTxServer(CliParams.getNamedParam(args, "-tx"));
      self.setPackagesFolder(CliParams.getNamedParam(args, "-packages"));

      if (CliParams.hasNamedParam(args, "-force-language")) {
        self.setForcedLanguage(CliParams.getNamedParam(args,"-force-language"));
      }

      if (CliParams.hasNamedParam(args, "-watch")) {
        throw new Error("Watch mode (-watch) is no longer supported");
      }
      if (CliParams.hasNamedParam(args, "-simplifier")) {
        self.simplifierMode = true;
        self.generationOff = true;
      }
      self.debug = CliParams.hasNamedParam(args, "-debug");
      self.cacheVersion = CliParams.hasNamedParam(args, "-cacheVersion");
      if (CliParams.hasNamedParam(args, "-publish")) {
        self.setMode(IGBuildMode.PUBLICATION);
        self.targetOutput = CliParams.getNamedParam(args, "-publish");
        self.publishing  = true;
        self.targetOutputNested = CliParams.getNamedParam(args, "-nested");
      }
      if (CliParams.hasNamedParam(args, "-resetTx")) {
        self.setCacheOption(CacheOption.CLEAR_ALL);
      } else if (CliParams.hasNamedParam(args, "-resetTxErrors")) {
        self.setCacheOption(CacheOption.CLEAR_ERRORS);
      } else {
        self.setCacheOption(CacheOption.LEAVE);
      }
      if (CliParams.hasNamedParam(args, "-no-sushi")) {
        self.noSushi = true;
      }
      if (CliParams.hasNamedParam(args, PACKAGE_CACHE_FOLDER_PARAM)) {
        self.setPackageCacheFolder(CliParams.getNamedParam(args, PACKAGE_CACHE_FOLDER_PARAM));
      }
      if (CliParams.hasNamedParam(args, "-authorise-non-conformant-tx-servers")) {
        TerminologyClientContext.setAllowNonConformantServers(true);
      }      
      TerminologyClientContext.setCanAllowNonConformantServers(true);
      try {
        self.execute();
        if (CliParams.hasNamedParam(args, "-no-errors")) {
          exitCode = self.countErrs(self.errors) > 0 ? 1 : 0;
        }
      } catch (ENoDump e) {
        self.log("Publishing Content Failed: "+e.getMessage());
        self.log("");        
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
          FileUtilities.stringToFile(buildReport(CliParams.getNamedParam(args, "-ig"), CliParams.getNamedParam(args, "-source"), self.filelog.toString(), Utilities.path(self.qaDir, "validation.txt"), self.txServer), Utilities.path(System.getProperty("java.io.tmpdir"), "fhir-ig-publisher.log"));
        }
    }
      consoleLogger.stop();
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
    this.forcedLanguage = Locale.forLanguageTag(language);
  }

  public static String getAbsoluteConfigFilePath(String configFilePath) throws IOException {
    if (new File(configFilePath).isAbsolute()) {
      return configFilePath;
    }
    return Utilities.path(System.getProperty("user.dir"), configFilePath);
  }

  public static void parseAndAddNoNarrativeParam(Publisher self, String param) {
    for (String p : param.split("\\,")) {
      self.noNarratives.add(p);
    }
  }

  public static void parseAndAddNoValidateParam(Publisher publisher, String param) {
    for (String p : param.split("\\,")) {
      publisher.noValidate.add(p);
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
      self.setTxServer(CliParams.getNamedParam(args, "-tx"));
    } else if (CliParams.hasNamedParam(args, "-devtx")) {
      self.setTxServer(FhirSettings.getTxFhirDevelopment());
    } else {
      self.setTxServer(FhirSettings.getTxFhirProduction());
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


  public void setFetcher(SimpleFetcher theFetcher) {
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

  private void updateInspector(HTMLInspector parentInspector, String path) {
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
        JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObjectFromUrl("https://api.github.com/repos/HL7/fhir-ig-publisher/releases/latest");
        currVer = json.asString("name").toString();
      } catch (IOException e) {
        currVer = "?pub-ver-1?";
      } catch (FHIRException e) {
        currVer = "$unknown-version$";
      }
    }
    return currVer;
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
    if (profile != null && profile.getUrl().startsWith(igpkp.getCanonical())) { // ignore anything we didn't define
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
    self.setTxServer(FhirSettings.getTxFhirProduction());
    if (self.countErrs(self.errors) > 0) {
      throw new Exception("Building IG '"+path+"' caused an error");
    }
  }

  public long getMaxMemory() {
    return maxMemory;
  }

  @Override
  public String resolveUri(RenderingContext context, String uri) {
    for (Extension ext : sourceIg.getExtensionsByUrl(ToolingExtensions.EXT_IG_URL)) {
      String value = ext.getExtensionString("uri");
      if (value != null && value.equals(uri)) {
        return ext.getExtensionString("target");
      }
    }
    
    return null;
  }

  @Override
  public <T extends Resource> T findLinkableResource(Class<T> class_, String uri) throws IOException {
    for (LinkedSpecification spec : linkSpecMaps) {
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
          IContextResourceLoader pl = new PublisherLoader(spec.getNpm(), spec.getSpm(), PackageHacker.fixPackageUrl(spec.getNpm().getWebLocation()), igpkp).makeLoader();
          Resource res = pl.loadResource(s, true);
          return (T) res;
        }
      }
    }
    return null;
  }

  public boolean isMilestoneBuild() {
    return milestoneBuild;
  }

  public void setMilestoneBuild(boolean milestoneBuild) {
    this.milestoneBuild = milestoneBuild;
  }

  public static void runDirectly(String folderPath, String txServer, boolean noGeneration, boolean noValidation, boolean noNetwork, boolean trackFragmentUsage, boolean clearTermCache, boolean noSushi2, boolean wantDebug, boolean nonConformantTxServers) throws Exception {
    Publisher self = new Publisher();
    self.logMessage("FHIR IG Publisher "+IGVersionUtil.getVersionString());
    self.logMessage("Detected Java version: " + System.getProperty("java.version")+" from "+System.getProperty("java.home")+" on "+System.getProperty("os.name")+"/"+System.getProperty("os.arch")+" ("+System.getProperty("sun.arch.data.model")+"bit). "+toMB(Runtime.getRuntime().maxMemory())+"MB available");
    self.logMessage("Start Clock @ "+nowAsString(self.execTime)+" ("+nowAsDate(self.execTime)+")");
    self.logMessage("");
    if (noValidation) {
      self.validationOff = noValidation;
      System.out.println("Running without generation to shorten the run time (editor process only)");
    }
    if (noGeneration) {
      self.generationOff = noGeneration;
      System.out.println("Running without generation to shorten the run time (editor process only)");
    }
    self.setTxServer(txServer);
    self.setConfigFile(determineActualIG(folderPath, IGBuildMode.MANUAL));

    self.debug = wantDebug;

    if (clearTermCache) {
      self.setCacheOption(CacheOption.CLEAR_ALL);
    } else {
      self.setCacheOption(CacheOption.LEAVE);
    }
    if (noSushi2) {
      self.noSushi = true;
    }
    if (nonConformantTxServers) {
      TerminologyClientContext.setAllowNonConformantServers(true);
      TerminologyClientContext.setCanAllowNonConformantServers(true);
    }      
    self.execute();
  }


}
