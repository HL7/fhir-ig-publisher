package org.hl7.fhir.igtools.renderers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.criteria.Root;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.conformance.profile.BindingResolution;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities.ElementChoiceGroup;
import org.hl7.fhir.r5.context.CanonicalResourceManager;
import org.hl7.fhir.r5.context.ContextUtilities;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.ActorDefinition;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r5.model.BooleanType;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.CapabilityStatement;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.ContactPoint;
import org.hl7.fhir.r5.model.DataType;
import org.hl7.fhir.r5.model.DateTimeType;
import org.hl7.fhir.r5.model.DomainResource;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.AggregationMode;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionBindingComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionConstraintComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionExampleComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionMappingComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionSlicingComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent;
import org.hl7.fhir.r5.model.ElementDefinition.PropertyRepresentation;
import org.hl7.fhir.r5.model.ElementDefinition.SlicingRules;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.model.Enumerations.BindingStrength;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.Library;
import org.hl7.fhir.r5.model.Measure;
import org.hl7.fhir.r5.model.NamingSystem;
import org.hl7.fhir.r5.model.OperationDefinition;
import org.hl7.fhir.r5.model.PackageInformation;
import org.hl7.fhir.r5.model.PlanDefinition;
import org.hl7.fhir.r5.model.PrimitiveType;
import org.hl7.fhir.r5.model.Property;
import org.hl7.fhir.r5.model.Quantity;
import org.hl7.fhir.r5.model.Questionnaire;
import org.hl7.fhir.r5.model.Requirements;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.SearchParameter;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionMappingComponent;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.profilemodel.PEBuilder;
import org.hl7.fhir.r5.profilemodel.PEBuilder.PEElementPropertiesPolicy;
import org.hl7.fhir.r5.profilemodel.PEDefinition;
import org.hl7.fhir.r5.profilemodel.PEType;
import org.hl7.fhir.r5.renderers.AdditionalBindingsRenderer;
import org.hl7.fhir.r5.renderers.CodeResolver;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.r5.renderers.IMarkdownProcessor;
import org.hl7.fhir.r5.renderers.ObligationsRenderer;
import org.hl7.fhir.r5.renderers.RendererFactory;
import org.hl7.fhir.r5.renderers.StructureDefinitionRenderer.UnusedTracker;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.GenerationRules;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.KnownLinkType;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.StructureDefinitionRendererMode;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities.SystemReference;
import org.hl7.fhir.r5.terminologies.ValueSetUtilities;
import org.hl7.fhir.r5.utils.ElementDefinitionUtilities;
import org.hl7.fhir.r5.utils.PublicationHacker;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.StandardsStatus;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.i18n.I18nConstants;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.Cell;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.Piece;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.Row;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.TableGenerationMode;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.TableModel;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.Title;

public class StructureDefinitionRenderer extends CanonicalRenderer {
  public class BindingResolutionDetails {
    private String vss;
    private String vsn;
    private String suffix;

    public BindingResolutionDetails(String vss, String vsn) {
      super();
      this.vss = vss;
      this.vsn = vsn;
    }
  }

  public static final String RIM_MAPPING = "http://hl7.org/v3";
  public static final String v2_MAPPING = "http://hl7.org/v2";
  public static final String LOINC_MAPPING = "http://loinc.org";
  public static final String SNOMED_MAPPING = "http://snomed.info";
  public static final int GEN_MODE_SNAP = 1;
  public static final int GEN_MODE_DIFF = 2;
  public static final int GEN_MODE_MS = 3;
  public static final int GEN_MODE_KEY = 4;
  public static final String ANCHOR_PREFIX_SNAP = "";
  public static final String ANCHOR_PREFIX_DIFF = "diff_";
  public static final String ANCHOR_PREFIX_MS = "ms_";
  public static final String ANCHOR_PREFIX_KEY = "key_";

  ProfileUtilities utils;
  private StructureDefinition sd;
  private String destDir;
  private List<FetchedFile> files;
  private boolean allInvariants;
  HashMap<String, ElementDefinition> differentialHash = null;
  HashMap<String, ElementDefinition> mustSupportHash = null;
  Map<String, Map<String, ElementDefinition>> sdMapCache;
  List<ElementDefinition> diffElements = null;
  List<ElementDefinition> mustSupportElements = null;
  List<ElementDefinition> keyElements = null;
  private static JsonObject usages;
  private String specPath;
  
  private org.hl7.fhir.r5.renderers.StructureDefinitionRenderer sdr;

  public StructureDefinitionRenderer(IWorkerContext context, String corePath, StructureDefinition sd, String destDir, IGKnowledgeProvider igp, List<SpecMapManager> maps, Set<String> allTargets, MarkDownProcessor markdownEngine, NpmPackage packge, List<FetchedFile> files, RenderingContext gen, boolean allInvariants,Map<String, Map<String, ElementDefinition>> mapCache, String specPath) {
    super(context, corePath, sd, destDir, igp, maps, allTargets, markdownEngine, packge, gen);
    this.sd = sd;
    this.destDir = destDir;
    utils = new ProfileUtilities(context, null, igp);
    this.files = files;
    this.allInvariants = allInvariants;
    this.sdMapCache = mapCache;
    sdr = new org.hl7.fhir.r5.renderers.StructureDefinitionRenderer(gen);
    this.specPath = specPath;
  }

  @Override
  public void setTranslator(org.hl7.fhir.utilities.TranslationServices translator) {
    super.setTranslator(translator);
    utils.setTranslator(translator);
  }

  public String summary() {
    try {
      if (sd.hasExtension(ToolingExtensions.EXT_SUMMARY)) {
        return processMarkdown("Profile Summary", (PrimitiveType) sd.getExtensionByUrl(ToolingExtensions.EXT_SUMMARY).getValue());
      }
      
      if (sd.getDifferential() == null)
        return "<p>" + translate("sd.summary", "No Summary, as this profile has no differential") + "</p>";

      // references
      List<String> refs = new ArrayList<String>(); // profile references
      // extensions (modifier extensions)
      List<String> ext = new ArrayList<String>(); // extensions
      // slices
      List<String> slices = new ArrayList<String>(); // Fixed Values
      // numbers - must support, required, prohibited, fixed
      int supports = 0;
      int requiredOutrights = 0;
      int requiredNesteds = 0;
      int fixeds = 0;
      int prohibits = 0;

      for (ElementDefinition ed : sd.getDifferential().getElement()) {
        if (ed.getPath().contains(".")) {
          if (ed.getMin() == 1) {
            if (parentChainHasOptional(ed, sd)) {
              requiredNesteds++;
            } else {
              requiredOutrights++;
            }
          }
          if ("0".equals(ed.getMax())) {
            prohibits++;
          }
          if (ed.getMustSupport()) {
            supports++;
          }
          if (ed.hasFixed()) {
            fixeds++;
          }

          for (TypeRefComponent t : ed.getType()) {
            if (t.hasProfile() && t.getProfile().get(0).getValue().length() > 40 && !igp.isDatatype(t.getProfile().get(0).getValue().substring(40))) {
              if (ed.getPath().endsWith(".extension")) {
                tryAdd(ext, summariseExtension(t.getProfile(), false));
              } else if (ed.getPath().endsWith(".modifierExtension")) {
                tryAdd(ext, summariseExtension(t.getProfile(), true));
              } else {
                tryAdd(refs, describeProfile(t.getProfile().get(0).getValue()));
              }
            }
            for (CanonicalType ct : t.getTargetProfile()) {
              tryAdd(refs, describeProfile(ct.getValue()));
            }
          }

          if (ed.hasSlicing() && !ed.getPath().endsWith(".extension") && !ed.getPath().endsWith(".modifierExtension")) {
            tryAdd(slices, describeSlice(ed.getPath(), ed.getSlicing()));
          }
        }
      }
      StringBuilder res = new StringBuilder("<a name=\"summary\"> </a>\r\n<p><b>\r\n" + translate("sd.summary", "Summary") + "\r\n</b></p>\r\n");
      if (ToolingExtensions.hasExtension(sd, "http://hl7.org/fhir/StructureDefinition/structuredefinition-summary")) {
        Extension v = ToolingExtensions.getExtension(sd, "http://hl7.org/fhir/StructureDefinition/structuredefinition-summary");
        res.append(processMarkdown("Profile.summary", (PrimitiveType) v.getValue()));
      }
      if (sd.getType().equals("Extension")) {
        res.append(extensionSummary());
      }else {
        if (supports + requiredOutrights + requiredNesteds + fixeds + prohibits > 0) {
          boolean started = false;
          res.append("<p>");
          if (requiredOutrights > 0 || requiredNesteds > 0) {
            started = true;
            res.append(translate("sd.summary", "Mandatory: %s %s", toStr(requiredOutrights), (requiredOutrights > 1 ? translate("sd.summary", Utilities.pluralizeMe("element")) : translate("sd.summary", "element"))));
            if (requiredNesteds > 0)
              res.append(translate("sd.summary", " (%s nested mandatory %s)", toStr(requiredNesteds), requiredNesteds > 1 ? translate("sd.summary", Utilities.pluralizeMe("element")) : translate("sd.summary", "element")));
          }
          if (supports > 0) {
            if (started)
              res.append("<br/> ");
            started = true;
            res.append(translate("sd.summary", "Must-Support: %s %s", toStr(supports), supports > 1 ? translate("sd.summary", Utilities.pluralizeMe("element")) : translate("sd.summary", "element")));
          }
          if (fixeds > 0) {
            if (started)
              res.append("<br/> ");
            started = true;
            res.append(translate("sd.summary", "Fixed Value: %s %s", toStr(fixeds), fixeds > 1 ? translate("sd.summary", Utilities.pluralizeMe("element")) : translate("sd.summary", "element")));
          }
          if (prohibits > 0) {
            if (started)
              res.append("<br/> ");
            started = true;
            res.append(translate("sd.summary", "Prohibited: %s %s", toStr(prohibits), prohibits > 1 ? translate("sd.summary", Utilities.pluralizeMe("element")) : translate("sd.summary", "element")));
          }
          res.append("</p>");
        }

        if (!refs.isEmpty()) {
          res.append("<p><b>" + translate("sd.summary", "Structures") + "</b></p>\r\n<p>" + translate("sd.summary", "This structure refers to these other structures") + ":</p>\r\n<ul>\r\n");
          for (String s : refs)
            res.append(s);
          res.append("\r\n</ul>\r\n\r\n");
        }
        if (!ext.isEmpty()) {
          res.append("<p><b>" + translate("sd.summary", "Extensions") + "</b></p>\r\n<p>" + translate("sd.summary", "This structure refers to these extensions") + ":</p>\r\n<ul>\r\n");
          for (String s : ext)
            res.append(s);
          res.append("\r\n</ul>\r\n\r\n");
        }
        if (!slices.isEmpty()) {
          res.append("<p><b>" + translate("sd.summary", "Slices") + "</b></p>\r\n<p>" + translate("sd.summary", "This structure defines the following %sSlices%s", "<a href=\"" + corePath + "profiling.html#slices\">", "</a>") + ":</p>\r\n<ul>\r\n");
          for (String s : slices)
            res.append(s);
          res.append("\r\n</ul>\r\n\r\n");
        }
      }
      if (ToolingExtensions.hasExtension(sd, ToolingExtensions.EXT_FMM_LEVEL)) {
        // Use hard-coded spec link to point to current spec because DSTU2 had maturity listed on a different page
        res.append("<p><b><a class=\"fmm\" href=\"http://hl7.org/fhir/versions.html#maturity\" title=\"Maturity Level\">" + translate("cs.summary", "Maturity") + "</a></b>: " + ToolingExtensions.readStringExtension(sd, ToolingExtensions.EXT_FMM_LEVEL) + "</p>\r\n");
      }

      return res.toString();
    } catch (Exception e) {
      return "<p><i>" + Utilities.escapeXml(e.getMessage()) + "</i></p>";
    }
  }

  private String extensionSummary() {
    if (ProfileUtilities.isSimpleExtension(sd)) {
      ElementDefinition value = sd.getSnapshot().getElementByPath("Extension.value");      
      return "<p>Simple Extension of type "+value.typeSummary()+": "+Utilities.stripPara(processMarkdown("ext-desc", sd.getDescriptionElement()))+"</p>";
    } else {
      List<ElementDefinition> subs = new ArrayList<>();
      ElementDefinition slice = null;
      for (ElementDefinition ed : sd.getSnapshot().getElement()) {
        if (ed.getPath().endsWith(".extension") && ed.hasSliceName()) {
          slice = ed;
        } else if (ed.getPath().endsWith(".extension.value[x]")) {
          ed.setUserData("slice", slice);
          subs.add(ed);
          slice = null;
        }
      }
      StringBuilder b = new StringBuilder();
      String html = Utilities.stripAllPara(processMarkdown("description", sd.getDescriptionElement()));
      b.append("<p>Complex Extension: "+html+"</p><ul>");
      for (ElementDefinition ed : subs) {
        ElementDefinition defn = (ElementDefinition) ed.getUserData("slice");
        b.append("<li>"+(defn.getSliceName())+": "+ed.typeSummary()+": "+Utilities.stripPara(processMarkdown("ext-desc", defn.getDefinition()))+"</li>\r\n");
      }
      b.append("</ul>");
      return b.toString();
    }
  }

  private boolean parentChainHasOptional(ElementDefinition ed, StructureDefinition profile) {
    if (!ed.getPath().contains("."))
      return false;

    ElementDefinition match = (ElementDefinition) ed.getUserData(ProfileUtilities.UD_DERIVATION_POINTER);
    if (match == null)
      return true; // really, we shouldn't get here, but this appears to be common in the existing profiles?
    // throw new Error("no matches for "+ed.getPath()+"/"+ed.getName()+" in "+profile.getUrl());

    while (match.getPath().contains(".")) {
      if (match.getMin() == 0) {
        return true;
      }
      match = getElementParent(profile.getSnapshot().getElement(), match);
    }

    return false;
  }

  private ElementDefinition getElementParent(List<ElementDefinition> list, ElementDefinition element) {
    String targetPath = element.getPath().substring(0, element.getPath().lastIndexOf("."));
    int index = list.indexOf(element) - 1;
    while (index >= 0) {
      if (list.get(index).getPath().equals(targetPath))
        return list.get(index);
      index--;
    }
    return null;
  }

  private String describeSlice(String path, ElementDefinitionSlicingComponent slicing) {
    if (!slicing.hasDiscriminator())
      return "<li>" + translate("sd.summary", "There is a slice with no discriminator at %s", path) + "</li>\r\n";
    String s = "";
    if (slicing.getOrdered())
      s = "ordered";
    if (slicing.getRules() != SlicingRules.OPEN)
      s = Utilities.noString(s) ? slicing.getRules().getDisplay() : s + ", " + slicing.getRules().getDisplay();
    if (!Utilities.noString(s))
      s = " (" + s + ")";
    CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
    for (ElementDefinitionSlicingDiscriminatorComponent d : slicing.getDiscriminator())
      b.append(d.getType().toCode() + ":" + d.getPath());
    if (slicing.getDiscriminator().size() == 1)
      return "<li>" + translate("sd.summary", "The element %s is sliced based on the value of %s", path, b.toString()) + s + "</li>\r\n";
    else
      return "<li>" + translate("sd.summary", "The element %s is sliced based on the values of %s", path, b.toString()) + s + "</li>\r\n";
  }

  private void tryAdd(List<String> ext, String s) {
    if (!Utilities.noString(s) && !ext.contains(s))
      ext.add(s);
  }

  private String summariseExtension(List<CanonicalType> profiles, boolean modifier) throws Exception {
    if (profiles.size() != 1)
      throw new Exception("Multiple profiles are not supported at this time (#1)");
    String url = profiles.get(0).getValue();
    StructureDefinition ed = context.fetchResource(StructureDefinition.class, url);
    if (ed == null)
      return "<li>" + translate("sd.summary", "Unable to summarise extension %s (no extension found)", url) + "</li>";
    if (ed.getWebPath() == null)
      return "<li><a href=\"" + "extension-" + ed.getId().toLowerCase() + ".html\">" + url + "</a>" + (modifier ? " (<b>" + translate("sd.summary", "Modifier") + "</b>) " : "") + "</li>\r\n";
    else
      return "<li><a href=\"" + Utilities.escapeXml(ed.getWebPath()) + "\">" + url + "</a>" + (modifier ? " (<b>" + translate("sd.summary", "Modifier") + "</b>) " : "") + "</li>\r\n";
  }

  private String describeProfile(String url) throws Exception {
    if (url.startsWith("http://hl7.org/fhir/StructureDefinition/") && (igp.isDatatype(url.substring(40)) || igp.isResource(url.substring(40)) || "Resource".equals(url.substring(40))))
      return null;

    StructureDefinition ed = context.fetchResource(StructureDefinition.class, url);
    if (ed == null)
      return "<li>" + translate("sd.summary", "unable to summarise profile %s (no profile found)", url) + "</li>";
    return "<li><a href=\"" + Utilities.escapeXml(ed.getWebPath()) + "\">" + ed.present() + " <span style=\"font-size: 8px\">(" + url + ")</span></a></li>\r\n";
  }

  private String summariseValue(DataType fixed) throws FHIRException {
    if (fixed instanceof org.hl7.fhir.r5.model.PrimitiveType)
      return Utilities.escapeXml(((org.hl7.fhir.r5.model.PrimitiveType) fixed).asStringValue());
    if (fixed instanceof CodeableConcept)
      return summarise((CodeableConcept) fixed);
    if (fixed instanceof Coding)
      return summarise((Coding) fixed);
    if (fixed instanceof Quantity)
      return summarise((Quantity) fixed);
    if (fixed instanceof ContactPoint)
      return summarise((ContactPoint) fixed);
    throw new FHIRException("Generating text summary of fixed value not yet done for type " + fixed.getClass().getName());
  }

  private String summarise(ContactPoint cp) {
    return cp.getValue();
  }


  private String summarise(Quantity quantity) {
    String cu = "";
    if ("http://unitsofmeasure.org/".equals(quantity.getSystem()))
      cu = " (" + translate("sd.summary", "UCUM") + ": " + quantity.getCode() + ")";
    if ("http://snomed.info/sct".equals(quantity.getSystem()))
      cu = " (" + translate("sd.summary", "SNOMED CT") + ": " + quantity.getCode() + ")";
    return quantity.getValue().toString() + quantity.getUnit() + cu;
  }

  private String summarise(CodeableConcept cc) throws FHIRException {
    if (cc.getCoding().size() == 1 && cc.getText() == null) {
      return summarise(cc.getCoding().get(0));
    } else if (cc.hasText()) {
      return "\"" + cc.getText() + "\"";
    } else if (cc.getCoding().size() > 0) {
      CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
      for (Coding c : cc.getCoding()) {
        b.append(summarise(c));
      }
      return b.toString();
    } else {
      throw new FHIRException("Error describing concept - not done yet (no codings, no text)");
    }
  }

  private String summarise(Coding coding) throws FHIRException {
    if ("http://snomed.info/sct".equals(coding.getSystem()))
      return "" + translate("sd.summary", "SNOMED CT code") + " " + coding.getCode() + (!coding.hasDisplay() ? "" : "(\"" + gt(coding.getDisplayElement()) + "\")");
    if ("http://loinc.org".equals(coding.getSystem()))
      return "" + translate("sd.summary", "LOINC code") + " " + coding.getCode() + (!coding.hasDisplay() ? "" : "(\"" + gt(coding.getDisplayElement()) + "\")");
    CodeSystem cs = context.fetchCodeSystem(coding.getSystem());
    if (cs == null)
      return "<span title=\"" + coding.getSystem() + "\">" + coding.getCode() + "</a>" + (!coding.hasDisplay() ? "" : "(\"" + gt(coding.getDisplayElement()) + "\")");
    else
      return "<a title=\"" + cs.present() + "\" href=\"" + Utilities.escapeXml(cs.getWebPath()) + "#" + cs.getId() + "-" + coding.getCode() + "\">" + coding.getCode() + "</a>" + (!coding.hasDisplay() ? "" : "(\"" + gt(coding.getDisplayElement()) + "\")");
  }

  public String diff(String defnFile, Set<String> outputTracker, boolean toTabs, StructureDefinitionRendererMode mode) throws IOException, FHIRException, org.hl7.fhir.exceptions.FHIRException {
    if (sd.getDifferential().getElement().isEmpty())
      return "";
    else {
      sdr.getContext().setStructureMode(mode);
      return new XhtmlComposer(XhtmlComposer.HTML).compose(sdr.generateTable(defnFile, sd, true, destDir, false, sd.getId(), false, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, false, outputTracker, false, gen, toTabs ? ANCHOR_PREFIX_DIFF : ANCHOR_PREFIX_SNAP));
    }
  }

  public String snapshot(String defnFile, Set<String> outputTracker, boolean toTabs, StructureDefinitionRendererMode mode) throws IOException, FHIRException, org.hl7.fhir.exceptions.FHIRException {
    if (sd.getSnapshot().getElement().isEmpty())
      return "";
    else {
      sdr.getContext().setStructureMode(mode);
      return new XhtmlComposer(XhtmlComposer.HTML).compose(sdr.generateTable(defnFile, sd, false, destDir, false, sd.getId(), true, corePath, "", false, false, outputTracker, false, gen, toTabs ? ANCHOR_PREFIX_SNAP : ANCHOR_PREFIX_SNAP));
    }
  }

  public String byKey(String defnFile, Set<String> outputTracker, boolean toTabs, StructureDefinitionRendererMode mode) throws IOException, FHIRException, org.hl7.fhir.exceptions.FHIRException {
    if (sd.getSnapshot().getElement().isEmpty())
      return "";
    else {
      XhtmlComposer composer = new XhtmlComposer(XhtmlComposer.HTML);
      StructureDefinition sdCopy = sd.copy();
      sdCopy.getSnapshot().setElement(getKeyElements());
      sdr.getContext().setStructureMode(mode);
      org.hl7.fhir.utilities.xhtml.XhtmlNode table = sdr.generateTable(defnFile, sdCopy, false, destDir, false, sdCopy.getId(), true, corePath, "", false, false, outputTracker, true, gen, toTabs ? ANCHOR_PREFIX_KEY : ANCHOR_PREFIX_SNAP);

      return composer.compose(table);
    }
  }

  public String byMustSupport(String defnFile, Set<String> outputTracker, boolean toTabs, StructureDefinitionRendererMode mode) throws IOException, FHIRException, org.hl7.fhir.exceptions.FHIRException {
    if (sd.getSnapshot().getElement().isEmpty())
      return "";
    else {
      XhtmlComposer composer = new XhtmlComposer(XhtmlComposer.HTML);
      StructureDefinition sdCopy = sd.copy();
      sdr.getContext().setStructureMode(mode);

      sdCopy.getSnapshot().setElement(getMustSupportElements());
      org.hl7.fhir.utilities.xhtml.XhtmlNode table = sdr.generateTable(defnFile, sdCopy, false, destDir, false, sdCopy.getId(), true, corePath, "", false, false, outputTracker, true, gen, toTabs ? ANCHOR_PREFIX_MS : ANCHOR_PREFIX_SNAP);

      return composer.compose(table);
    }
  }

  protected List<ElementDefinition> getMustSupportElements() {
    if (mustSupportElements==null) {
      mustSupportElements = new ArrayList<ElementDefinition>();
      Map<String, ElementDefinition> mustSupport = getMustSupport();
      // Scan through all the properties checking for must support elements
      // and clear properties in the cloned StructureDefinition that we don't want to
      // show in the custom view
      for (ElementDefinition ed : sd.getSnapshot().getElement()) {
        if (mustSupport.containsKey(ed.getId())) {
          ElementDefinition edCopy = ed.copy();
          edCopy.copyUserData(ed);
          if (edCopy.hasExample())
            edCopy.getExample().clear();
          if (!edCopy.getMustSupport()) {
            if (edCopy.getPath().contains(".")) {
              edCopy.setUserData("render.opaque", true);
            }
            edCopy.setBinding(null);
            ed.getConstraint().clear();
          }
          edCopy.setMustSupport(false);
          mustSupportElements.add(edCopy);
        }
      }
    }
    return mustSupportElements;
  }

  public String byKeyElements(String defnFile, Set<String> outputTracker) throws IOException, FHIRException, org.hl7.fhir.exceptions.FHIRException {
    if (sd.getSnapshot().getElement().isEmpty())
      return "";
    else {
      XhtmlComposer composer = new XhtmlComposer(XhtmlComposer.HTML);
      StructureDefinition sdCopy = sd.copy();
      List<ElementDefinition> keyElements = getKeyElements();

      sdCopy.getSnapshot().setElement(keyElements);
      org.hl7.fhir.utilities.xhtml.XhtmlNode table = sdr.generateTable(defnFile, sdCopy, false, destDir, false, sdCopy.getId(), true, corePath, "", false, false, outputTracker, true, gen, ANCHOR_PREFIX_KEY);

      return composer.compose(table);
    }
  }

  protected Map<String, ElementDefinition> getMustSupport() {
    if (mustSupportHash==null) {
      mustSupportHash = new HashMap<String, ElementDefinition>();
      scanForMustSupport(mustSupportHash, sd.getSnapshot().getElement(), sd.getSnapshot().getElementFirstRep(), new ArrayList<>());
    }
    return mustSupportHash;
  }

  // Returns a hash of all elements in the differential (including omitted ancestors) by element id
  // Allows checking if an element is in the differential.
  protected Map<String, ElementDefinition> getDifferential() {
    if (differentialHash==null) {
      differentialHash = new HashMap<String, ElementDefinition>();
      for (ElementDefinition e : sd.getDifferential().getElement()) {
        differentialHash.put(e.getId(), e);
        if (e.getId().contains(".")) {
          String id = e.getId();
          do {
            id = id.substring(0, id.lastIndexOf(".")-1);
            if (differentialHash.containsKey(id))
              break;
            else
              differentialHash.put(id, null);
          } while(id.contains("."));
        }
      }
    }
    return differentialHash;
  }

  protected List<ElementDefinition> getDifferentialElements() {
    if (diffElements == null) {
      diffElements = new ArrayList<ElementDefinition>();
      for (ElementDefinition e : sd.getSnapshot().getElement()) {
        if (getDifferential().containsKey(e.getId())) {
          ElementDefinition ediff = getDifferential().get(e.getId());
          if (ediff == null)
            ediff = e.copy();
          diffElements.add(ediff);
        }
      }
    }
    return diffElements;
  }

  private void scanForMustSupport(Map<String, ElementDefinition> mustSupport, List<ElementDefinition> elements, ElementDefinition element, List<ElementDefinition> parents) {
    if (parents.isEmpty() || element.hasMustSupport() && element.getMustSupport()) {
      mustSupport.put(element.getId(), element);
      for (ElementDefinition parent : parents) {
        mustSupport.put(parent.getId(), parent);
      }
    }
    List<ElementDefinition> children = getChildren(elements, element);
    for (ElementDefinition child : children) {
      List<ElementDefinition> np = new ArrayList<>();
      np.addAll(parents);
      np.add(element);
      scanForMustSupport(mustSupport, elements, child, np);
    }
  }

  protected List<ElementDefinition> getKeyElements() {
    if (keyElements==null) {
      keyElements = new ArrayList<ElementDefinition>();
      Map<String, ElementDefinition> mustSupport = getMustSupport();
      Set<ElementDefinition> keyElementsSet = new HashSet<ElementDefinition>();
      scanForKeyElements(keyElementsSet, mustSupport, sd.getSnapshot().getElement(), sd.getSnapshot().getElementFirstRep(), null);
      for (ElementDefinition ed : sd.getSnapshot().getElement()) {
        if (keyElementsSet.contains(ed)) {
          ElementDefinition edCopy = ed.copy();
          edCopy.copyUserData(ed);
          keyElements.add(edCopy);
        }
      }
    }

    return keyElements;
  }

  private void scanForKeyElements(Set<ElementDefinition> keyElements, Map<String, ElementDefinition> mustSupport, List<ElementDefinition> elements, ElementDefinition element, String baseModelUrl) {
    keyElements.add(element);
    // Lloyd todo: check changes with the underlying 'base' model
    List<ElementDefinition> children = getChildren(elements, element);

    for (ElementDefinition child : children) {
      // An element is 'key' if it's within a 'mustSupport'-relevant element (mustSupport or ancestor of mustSupport) and:
      //  - it's mandatory
      //  - it's referenced by an invariant (other than the generic ele-1 that everything is referenced by)
      //  - it's a modifier element
      //  - it's a slice (which means it's a constraint on the core) or declares slicing not implicit in the core spec (i.e. extension/modifierExtension)
      //  - it appears in the differential
      //  - the max cardinality has been constrained from the base max cardinality
      if (mustSupport.containsKey(child.getId()) || child.getMin()!=0 || (child.hasCondition() && child.getCondition().size()>1) || child.getIsModifier() || (child.hasSlicing() && !child.getPath().endsWith(".extension") && !child.getPath().endsWith(".modifierExtension")) || child.hasSliceName() || getDifferential().containsKey(child.getId()) || !child.getMax().equals(child.getBase().getMax())) {
        scanForKeyElements(keyElements ,mustSupport, elements, child, baseModelUrl);
      }
    }
  }

  public String grid(String defnFile, Set<String> outputTracker) throws IOException, FHIRException, org.hl7.fhir.exceptions.FHIRException {
    if (sd.getSnapshot().getElement().isEmpty())
      return "";
    else
      return new XhtmlComposer(XhtmlComposer.HTML).compose(sdr.generateGrid(defnFile, sd, destDir, false, sd.getId(), corePath, "", outputTracker));
  }

  public String txDiff(boolean withHeadings, boolean mustSupportOnly) throws FHIRException, IOException {
    List<String> txlist = new ArrayList<String>();
    boolean hasFixed = false;
    Map<String, ElementDefinition> txmap = new HashMap<String, ElementDefinition>();
    for (ElementDefinition ed : sd.getDifferential().getElement()) {
      if (ed.hasBinding() && !"0".equals(ed.getMax()) && (!mustSupportOnly || ed.getMustSupport())) {
        String id = ed.getId();
        if (ed.hasFixed()) {
          hasFixed = true;
          ed.getBinding().setUserData("tx.value", ed.getFixed());
        } else if (ed.hasPattern()) {
          hasFixed = true;
          ed.getBinding().setUserData("tx.pattern", ed.getPattern());
        } else {
          // tricky : scan the children for a fixed coding value
          DataType t = findFixedValue(ed, true);
          if (t != null)
            ed.getBinding().setUserData("tx.value", t);
        }
        if (ed.getType().size() == 1 && ed.getType().get(0).getWorkingCode().equals("Extension"))
          id = id + "<br/>" + ed.getType().get(0).getProfile();
        txlist.add(id);
        txmap.put(id, ed);
      }
    }
    if (txlist.isEmpty())
      return "";
    else {
      StringBuilder b = new StringBuilder();
      if (withHeadings)
        b.append("<h4>" + translate("sd.tx", "Terminology Bindings (Differential)") + "</h4>\r\n");
      b.append("<table class=\"list\">\r\n");
      b.append("<tr><td><b>" + translate("sd.tx", "Path") + "</b></td><td><b>" + translate("sd.tx", "Conformance") + "</b></td><td><b>" + translate("sd.tx", hasFixed ? "ValueSet / Code" : "ValueSet") + "</b></td></tr>\r\n");
      for (String path : txlist) {
        txItem(txmap, b, path, sd.getUrl());
      }
      b.append("</table>\r\n");
      return b.toString();
    }
  }

  public String tx(boolean withHeadings, boolean mustSupportOnly, boolean keyOnly) throws FHIRException, IOException {
    List<String> txlist = new ArrayList<String>();
    boolean hasFixed = false;
    Map<String, ElementDefinition> txmap = new HashMap<String, ElementDefinition>();
    for (ElementDefinition ed : keyOnly? getKeyElements() : sd.getSnapshot().getElement()) {
      if (ed.hasBinding() && !"0".equals(ed.getMax()) && (!mustSupportOnly || ed.getMustSupport())) {
        String id = ed.getId();
        if (ed.hasFixed()) {
          hasFixed = true;
          ed.getBinding().setUserData("tx.value", ed.getFixed());
        } else if (ed.hasPattern()) {
          hasFixed = true;
          ed.getBinding().setUserData("tx.pattern", ed.getPattern());
        } else {
          // tricky : scan the children for a fixed coding value
          DataType t = findFixedValue(ed, false);
          if (t != null)
            ed.getBinding().setUserData("tx.value", t);
        }
        if (ed.getType().size() == 1 && ed.getType().get(0).getWorkingCode().equals("Extension"))
          id = id + "<br/>" + ed.getType().get(0).getProfile();
        txlist.add(id);
        txmap.put(id, ed);
      }
    }
    if (txlist.isEmpty())
      return "";
    else {
      StringBuilder b = new StringBuilder();
      if (withHeadings)
        b.append("<h4>" + translate("sd.tx", "Terminology Bindings") + "</h4>\r\n");
      b.append("<table class=\"list\">\r\n");
      b.append("<tr><td><b>" + translate("sd.tx", "Path") + "</b></td><td><b>" + translate("sd.tx", "Conformance") + "</b></td><td><b>" + translate("sd.tx", hasFixed ? "ValueSet / Code" : "ValueSet") + "</b></td></tr>\r\n");
      for (String path : txlist) {
        txItem(txmap, b, path, sd.getUrl());
      }
      b.append("</table>\r\n");
      return b.toString();

    }
  }

  private DataType findFixedValue(ElementDefinition ed, boolean diff) {
    if (ElementDefinitionUtilities.hasType(ed, "Coding")) {
      List<ElementDefinition> children = utils.getChildList(sd, ed, diff);
      String sys = null;
      String code = null;
      for (ElementDefinition cd : children) {
        if (cd.getPath().endsWith(".system") && cd.hasFixed())
          sys = cd.getFixed().primitiveValue();
        if (cd.getPath().endsWith(".code") && cd.hasFixed())
          code = cd.getFixed().primitiveValue();
      }
      if (sys != null && code != null)
        return new Coding().setSystem(sys).setCode(code);
    }
    return null;
  }

  public void txItem(Map<String, ElementDefinition> txmap, StringBuilder b, String path, String url) throws FHIRException, IOException {
    ElementDefinition ed = txmap.get(path);
    ElementDefinitionBindingComponent tx = ed.getBinding();
    BindingResolutionDetails brd = new BindingResolutionDetails("", "?ext");
    if (tx.hasValueSet()) {
      txDetails(tx, brd, false);
    } else if (ed.hasUserData(ProfileUtilities.UD_DERIVATION_POINTER)) {
      ElementDefinitionBindingComponent txi = ((ElementDefinition) ed.getUserData(ProfileUtilities.UD_DERIVATION_POINTER)).getBinding();
      txDetails(txi, brd, true);
    }
    boolean strengthInh = false;
    BindingStrength strength = null;
    if (tx.hasStrength()) {
      strength = tx.getStrength();
    } else if (ed.hasUserData(ProfileUtilities.UD_DERIVATION_POINTER)) {
      ElementDefinitionBindingComponent txi = ((ElementDefinition) ed.getUserData(ProfileUtilities.UD_DERIVATION_POINTER)).getBinding();
      strength = txi.getStrength();
      strengthInh = true;
    }

    if (brd.vsn.equals("?ext")) {
      if (tx.getValueSet() != null)
        System.out.println("Value set '"+tx.getValueSet()+"' at " + url + "#" + path + " not found");
      else if (!tx.hasDescription())
        System.out.println("No value set specified at " + url + "#" + path + " (no url)");
    }
    if (tx.hasUserData("tx.value")) {
      brd.vss = "Fixed Value: " + summariseValue((DataType) tx.getUserData("tx.value"));
      brd.suffix = null;
    } else if (tx.hasUserData("tx.pattern")) {
      brd.vss = "Pattern: " + summariseValue((DataType) tx.getUserData("tx.pattern"));
      brd.suffix = null;
    }

    b.append("<tr><td>").append(path).append("</td><td><a style=\"opacity: " + opacityStr(strengthInh) + "\" href=\"").append(corePath).append("terminologies.html#").append(strength == null ? "" : egt(tx.getStrengthElement()));
    if (tx.hasDescription())
      b.append("\">").append(strength == null ? "" : egt(tx.getStrengthElement())).append("</a></td><td title=\"").append(Utilities.escapeXml(tx.getDescription())).append("\">").append(brd.vss);
    else
      b.append("\">").append(strength == null ? "" : egt(tx.getStrengthElement())).append("</a></td><td>").append(brd.vss);
    if (brd.suffix != null) {
      b.append(brd.suffix);
    }
    AdditionalBindingsRenderer abr = new AdditionalBindingsRenderer(igp, corePath, sd, path, gen, this, sdr);
    if (tx.hasExtension(ToolingExtensions.EXT_MAX_VALUESET)) {
      abr.seeMaxBinding(ToolingExtensions.getExtension(tx, ToolingExtensions.EXT_MAX_VALUESET));
    }
    if (tx.hasExtension(ToolingExtensions.EXT_MIN_VALUESET)) {
      abr.seeMinBinding(ToolingExtensions.getExtension(tx, ToolingExtensions.EXT_MIN_VALUESET));
    }
    if (tx.hasExtension(ToolingExtensions.EXT_BINDING_ADDITIONAL)) {
      abr.seeAdditionalBindings(tx.getExtensionsByUrl(ToolingExtensions.EXT_BINDING_ADDITIONAL));
    }
    if (abr.hasBindings()) {
      XhtmlNode x = new XhtmlNode(NodeType.Element, "table");
      x.setAttribute("class", "grid");
      abr.render(x.getChildNodes(), true);
      b.append(new XhtmlComposer(true, true).compose(x));
    }
    //
    //        if (tx.hasExtension(ToolingExtensions.EXT_MAX_VALUESET)) {
    //            BindingResolution br = igp.resolveBinding(sd, ToolingExtensions.readStringExtension(tx, ToolingExtensions.EXT_MAX_VALUESET), path);
    //            b.append("<br/>");
    //            b.append("<a style=\"font-weight:bold\" title=\"Max Value Set Extension\" href=\"" + corePath + "extension-elementdefinition-maxvalueset.html\">Max Binding</a>: ");
    //            b.append((br.url == null ? processMarkdown("binding", br.display) : "<a href=\"" + Utilities.escapeXml((Utilities.isAbsoluteUrlLinkable(br.url) || !igp.prependLinks() ? br.url : corePath + br.url)) + "\">" + Utilities.escapeXml(br.display) + "</a>"));
    //        }
    //        if (tx.hasExtension(ToolingExtensions.EXT_MIN_VALUESET)) {
    //            BindingResolution br = igp.resolveBinding(sd, ToolingExtensions.readStringExtension(tx, ToolingExtensions.EXT_MIN_VALUESET), path);
    //            b.append("<br/>");
    //            b.append("<a style=\"font-weight:bold\" title=\"Min Value Set Extension\" href=\"" + corePath + "extension-elementdefinition-minvalueset.html\">Min Binding</a>: ");
    //            b.append((br.url == null ? processMarkdown("binding", br.display) : "<a href=\"" + Utilities.escapeXml((Utilities.isAbsoluteUrlLinkable(br.url) || !igp.prependLinks() ? br.url : corePath + br.url)) + "\">" + Utilities.escapeXml(br.display) + "</a>"));
    //        }
    b.append("</td></tr>\r\n");
  }

  public void txDetails(ElementDefinitionBindingComponent tx, BindingResolutionDetails brd, boolean inherited) {
    String uri = null;
    if (tx.getValueSet() != null) {
      uri = tx.getValueSet().trim();
    }
    String name = getSpecialValueSetName(uri);
    if (name != null) {
      brd.vss = "<a style=\"opacity: " + opacityStr(inherited) + "\" href=\"" + Utilities.escapeXml(uri) + "\">" + Utilities.escapeXml(name) + "</a>";
      brd.vsn = name;
    } else {
      ValueSet vs = context.fetchResource(ValueSet.class, canonicalise(uri));
      if (vs == null) {
        BindingResolution br = igp.resolveActualUrl(uri);
        if (br.url == null)
          brd.vss = "<code>" + processMarkdown("binding", br.display) + "</code>";
        else if (Utilities.isAbsoluteUrlLinkable(br.url))
          brd.vss = "<a style=\"opacity: " + opacityStr(inherited) + "\" href=\"" + Utilities.escapeXml(br.url) + "\">" + Utilities.escapeXml(br.display) + "</a>";
        else {
          brd.vss = "<a style=\"opacity: " + opacityStr(inherited) + "\" href=\"" + Utilities.escapeXml(prefix + br.url) + "\">" + Utilities.escapeXml(br.display) + "</a>";
        }
      } else {
        String p = vs.getWebPath();
        if (p == null)
          brd.vss = "<a style=\"opacity: " + opacityStr(inherited) + "\" href=\"??\">" + Utilities.escapeXml(gt(vs.getNameElement())) + " (" + translate("sd.tx", "missing link") + ")</a>";
        else if (p.startsWith("http:"))
          brd.vss = "<a style=\"opacity: " + opacityStr(inherited) + "\" href=\"" + Utilities.escapeXml(p) + "\">" + Utilities.escapeXml(gt(vs.getNameElement())) + "</a>";
        else
          brd.vss = "<a style=\"opacity: " + opacityStr(inherited) + "\" href=\"" + Utilities.escapeXml(p) + "\">" + Utilities.escapeXml(gt(vs.getNameElement())) + "</a>";
        StringType title = vs.hasTitleElement() ? vs.getTitleElement() : vs.getNameElement();
        if (title != null) {
          brd.vsn = gt(title);
        }
        String system = ValueSetUtilities.getAllCodesSystem(vs);
        if (system != null) {
          SystemReference sr = CodeSystemUtilities.getSystemReference(system, context);
          if (sr == null) {
            brd.suffix = " (a valid code from <code>"+system+"</code>)";
          } else if (sr.isLocal() || (sr.getText() != null && sr.getText().equals(vs.getName()))) {
            brd.suffix = "";
          } else if (sr.getLink() == null) {
            brd.suffix = " (a valid code from "+sr.getText()+" (<code>"+system+"</code>)";
          } else {
            brd.suffix = " (a valid code from <a href=\""+sr.getLink()+"\">"+sr.getText()+"</a>)";
          }
        }
      }
    }
  }

  private String opacityStr(boolean inherited) {
    return inherited ? "0.5" : "1.0";
  }

  private String getSpecialValueSetName(String uri) {
    if (uri != null && uri.startsWith("http://loinc.org/vs/"))
      return "LOINC " + uri.substring(20);
    return null;
  }

  // Information about a constraint within the StructureDefinition that allows for the possibility of
  // variation in a constraint key between elements (because some elements constraint it and others don't)
  private class ConstraintInfo {
    private String key;                                   // The key for the constraint
    private ConstraintVariation primary;                  // The original definition of the constraint
    private Map<String, ConstraintVariation> variations = new HashMap<String, ConstraintVariation>();  // Constrained definitions of the constraint

    public ConstraintInfo(ElementDefinitionConstraintComponent c, String id) {
      key = c.getKey();
      addVariation(c, id);
    }

    private String constraintHash(ElementDefinitionConstraintComponent c) {
      return c.getExpression()+c.getHuman();
    }
    public void addVariation(ElementDefinitionConstraintComponent c, String id) {
      // 'primary' indicates if this is the initial definition of the constraint or if it's a subsequently profiled
      // version of the constraint.  The logic here could probably use some work, but all it does is make sure the
      // 'official' one comes first, so it's not critical that there are issues.
      if (!c.hasSource() || c.getSource().equals(sd.getUrl()) || (c.getSource().startsWith("http://hl7.org/fhir/StructureDefinition/") && !c.getSource().substring(41).contains("/"))) {
        if (primary == null) {
          primary = variations.get(constraintHash(c));
          if (primary==null)
            primary = new ConstraintVariation(c);
          else
            variations.remove(constraintHash(c));

          primary.setPrimary(true);
        }
        primary.addElement(id);
      } else {
        ConstraintVariation v = variations.get(constraintHash(c));
        if (v==null) {
          v = new ConstraintVariation(c);
          variations.put(constraintHash(c), v);
        }
        v.addElement(id);
      }
    }

    public List<ConstraintVariation> getVariations() {
      List<ConstraintVariation> l = new ArrayList<ConstraintVariation>();
      if (primary!=null)
        l.add(primary);
      l.addAll(variations.values());
      return l;
    }
  }

  private class ConstraintVariation {
    private ElementDefinitionConstraintComponent constraint;
    private List<String> elements = new ArrayList<String>();
    private boolean primary = false;

    public ConstraintVariation(ElementDefinitionConstraintComponent c) {
      constraint = c;
    }
    public void addElement(String id) {
      elements.add(id);
    }
    public ElementDefinitionConstraintComponent getConstraint() {
      return constraint;
    }
    public void setPrimary(boolean isPrimary) {
      primary = isPrimary;
    }
    public String getIds() {
      if (constraint.hasSource() && constraint.getSource().equals("http://hl7.org/fhir/StructureDefinition/Element"))
        return "**ALL** elements";
      else if (constraint.hasSource() && constraint.getSource().equals("http://hl7.org/fhir/StructureDefinition/Extension"))
        return "**ALL** extensions";
      else
        return String.join(", ", elements);
    }
  }

  public List<ElementDefinition> elementsForMode(int genMode) {
    switch (genMode) {
    case GEN_MODE_DIFF:
      return sdr.supplementMissingDiffElements(sd);
    case GEN_MODE_KEY:
      return getKeyElements();
    case GEN_MODE_MS:
      return getMustSupportElements();
    default:
      return sd.getSnapshot().getElement();
    }
  }

  public String invOldMode(boolean withHeadings, int genMode) {
    Map<String, ConstraintInfo> constraintMap = new HashMap<String,ConstraintInfo>();
    List<ElementDefinition> list = elementsForMode(genMode);
    for (ElementDefinition ed : list) {
      if (!"0".equals(ed.getMax()) && ed.hasConstraint()) {
        for (ElementDefinitionConstraintComponent c : ed.getConstraint()) {
          ConstraintInfo ci = constraintMap.get(c.getKey());
          if (ci == null) {
            ci = new ConstraintInfo(c, ed.getId());
            constraintMap.put(c.getKey(), ci);
          } else
            ci.addVariation(c, ed.getId());
        }
      }
    }

    if (constraintMap.isEmpty())
      return "";
    else {
      StringBuilder b = new StringBuilder();
      if (withHeadings)
        b.append("<h4>" + translate("sd.inv", "Constraints") + "</h4>\r\n");
      b.append("<table class=\"list\">\r\n");
      b.append("<tr><td width=\"60\"><b>" + translate("sd.inv", "Id") + "</b></td><td><b>" + translate("sd.inv", "Grade") + "</b></td><td><b>" + translate("sd.inv", "Path(s)") + "</b></td><td><b>" + translate("sd.inv", "Details") + "</b></td><td><b>" + translate("sd.inv", "Requirements") + "</b></td></tr>\r\n");
      List<String> keys = new ArrayList<>(constraintMap.keySet());

      Collections.sort(keys, new ConstraintKeyComparator());
      for (String key : keys) {
        ConstraintInfo ci = constraintMap.get(key);
        for (ConstraintVariation cv : ci.getVariations()) {
          ElementDefinitionConstraintComponent inv = cv.getConstraint();
          if (!inv.hasSource() || inv.getSource().equals(sd.getUrl()) || allInvariants) {
            b.append("<tr><td>").append(inv.getKey()).append("</td><td>").append(grade(inv)).append("</td><td>").append(cv.getIds()).append("</td><td>").append(Utilities.escapeXml(gt(inv.getHumanElement())))
            .append("<br/>: ").append(Utilities.escapeXml(inv.getExpression())).append("</td><td>").append(Utilities.escapeXml(gt(inv.getRequirementsElement()))).append("</td></tr>\r\n");
          }
        }
      }
      b.append("</table>\r\n");
      return b.toString();
    }
  }

  class ConstraintKeyComparator implements Comparator<String> {

    public int compare(String a, String b) {
      if (a.matches(".+\\-\\d+") && b.matches(".+\\-\\d+")) {
        String aStart = a.substring(0, a.lastIndexOf("-")-1);
        String bStart = b.substring(0, b.lastIndexOf("-")-1);
        if (aStart.equals(bStart)) {
          Integer aEnd = Integer.parseInt(a.substring(a.lastIndexOf("-") + 1));
          Integer bEnd = Integer.parseInt(b.substring(b.lastIndexOf("-") + 1));
          return aEnd.compareTo(bEnd);
        } else
          return aStart.compareTo(bStart);
      } else
        return a.compareTo(b);
    }
  }

  private String grade(ElementDefinitionConstraintComponent inv) {
    if (inv.hasExtension(ToolingExtensions.EXT_BEST_PRACTICE)) {
      return "best practice";
    } else {
      return gt(inv.getSeverityElement());
    }
  }

  public class StringPair {

    private String match;
    private String replace;

    public StringPair(String match, String replace) {
      this.match = match;
      this.replace = replace;
    }

    @Override
    public String toString() {
      return match + " -> " + replace;
    }

  }

  // Returns the ElementDefinition for the 'parent' of the current element
  private ElementDefinition getBaseElement(ElementDefinition e, String url) {
    if (e.hasUserData(ProfileUtilities.UD_DERIVATION_POINTER)) {
      return getElementById(url, e.getUserString(ProfileUtilities.UD_DERIVATION_POINTER));
    }
    return null;
  }

  // Returns the ElementDefinition for the 'root' ancestor of the current element
  private ElementDefinition getRootElement(ElementDefinition e) {
    if (!e.hasBase())
      return null;
    String basePath = e.getBase().getPath();
    String url = "http://hl7.org/fhir/StructureDefinition/" + (basePath.contains(".") ? basePath.substring(0, basePath.indexOf(".")) : basePath);
    try {
      return getElementById(url, basePath);
    } catch (FHIRException except) {
      // Likely a logical model, so this is ok
      return null;
    }
  }

  public String dict(boolean incProfiledOut, int mode, String anchorPrefix) throws Exception {
    int i = 1;
    StringBuilder b = new StringBuilder();
    b.append("<p>Guidance on how to interpret the contents of this table can be found <a href=\"https://build.fhir.org/ig/FHIR/ig-guidance//readingIgs.html#data-dictionaries\">here</a>.</p>\r\n");
    b.append("<table class=\"dict\">\r\n");

    Map<String, ElementDefinition> allAnchors = new HashMap<>();
    List<ElementDefinition> excluded = new ArrayList<>();

    List<ElementDefinition> stack = new ArrayList<>(); // keeps track of parents, for anchor generation
    List<ElementDefinition> elements = elementsForMode(mode);
    for (ElementDefinition ec : elements) {
      addToStack(stack, ec);
      generateAnchors(stack, allAnchors);
      checkInScope(stack, excluded);
    }

    for (ElementDefinition ec : elements) {
      if ((incProfiledOut || !"0".equals(ec.getMax())) && !excluded.contains(ec)) {
        ElementDefinition compareElement = null;
        if (mode==GEN_MODE_DIFF)
          compareElement = getBaseElement(ec, sd.getBaseDefinition());
        else if (mode==GEN_MODE_KEY)
          compareElement = getRootElement(ec);

        String anchors = makeAnchors(ec, anchorPrefix);
        if (isProfiledExtension(ec)) {
          StructureDefinition extDefn = context.fetchResource(StructureDefinition.class, ec.getType().get(0).getProfile().get(0).getValue());
          if (extDefn == null) {
            String title = ec.getId();
            b.append("  <tr><td colspan=\"2\" class=\"structure\"><span class=\"self-link-parent\">"+anchors+"<span style=\"color: grey\">" + Integer.toString(i++) + ".</span> <b>" + title + "</b>" + link(ec.getId(), anchorPrefix) + "</span></td></tr>\r\n");
            generateElementInner(b, sd, ec, 1, null, compareElement, null);
          } else {
            String title = ec.getId();
            b.append("  <tr><td colspan=\"2\" class=\"structure\"><span class=\"self-link-parent\">"+anchors);
            b.append("<span style=\"color: grey\">" + Integer.toString(i++) + ".</span> <b>" + title + "</b>" + link(ec.getId(), anchorPrefix) + "</span></td></tr>\r\n");
            ElementDefinition valueDefn = getExtensionValueDefinition(extDefn);
            ElementDefinition compareValueDefn = null;
            try {
              StructureDefinition compareExtDefn = context.fetchResource(StructureDefinition.class, compareElement.getType().get(0).getProfile().get(0).getValue());
              compareValueDefn = getExtensionValueDefinition(extDefn);
            } catch (Exception except) {}
            generateElementInner(b, sd, ec, valueDefn == null || valueDefn.prohibited() ? 2 : 3, valueDefn, compareElement, compareValueDefn);
            // generateElementInner(b, extDefn, extDefn.getSnapshot().getElement().get(0), valueDefn == null ? 2 : 3, valueDefn);
          }
        } else {
          String title = ec.getId();
          b.append("  <tr><td colspan=\"2\" class=\"structure\"><span class=\"self-link-parent\">"+anchors);
          b.append("<span style=\"color: grey\">" + Integer.toString(i++) + ".</span> <b>" + title + "</b>" + link(ec.getId(), anchorPrefix) + "</span></td></tr>\r\n");
          generateElementInner(b, sd, ec, mode, null, compareElement, null);
          if (ec.hasSlicing())
            generateSlicing(b, sd, ec, ec.getSlicing(), compareElement, mode);
        }
      }
    }
    b.append("</table>\r\n");
    i++;
    return b.toString();
  }

  private void checkInScope(List<ElementDefinition> stack, List<ElementDefinition> excluded) {
    if (stack.size() > 2) {
      ElementDefinition parent = stack.get(stack.size()-2);
      ElementDefinition focus = stack.get(stack.size()-1);

      if (excluded.contains(parent) || "0".equals(parent.getMax())) {
        excluded.add(focus);
      }
    }
  }

  private void generateAnchors(List<ElementDefinition> stack, Map<String, ElementDefinition> allAnchors) {
    List<String> list = new ArrayList<>();
    list.add(stack.get(0).getId()); // initialise
    for (int i = 1; i < stack.size(); i++) {
      ElementDefinition ed = stack.get(i);
      List<String> aliases = new ArrayList<>();
      String name = tail(ed.getPath());
      if (name.endsWith("[x]")) {
        aliases.add(name);
        Set<String> tl = new HashSet<String>(); // guard against duplicate type names - can happn in some versions
        for (TypeRefComponent tr : ed.getType()) {
          String tc = tr.getWorkingCode();
          if (!tl.contains(tc)) {
            aliases.add(name.replace("[x]", Utilities.capitalize(tc)));
            aliases.add(name+":"+name.replace("[x]", Utilities.capitalize(tc)));
            tl.add(tc);
          }
        }
      } else if (ed.hasSliceName()) {
        aliases.add(name+":"+ed.getSliceName());
        // names.add(name); no good generating this?
      } else {
        aliases.add(name);
      }
      List<String> generated = new ArrayList<>();
      for (String l : list) {
        for (String a : aliases) {
          generated.add(l+"."+a);
        }
      }
      list.clear();
      list.addAll(generated);
    }
    ElementDefinition ed = stack.get(stack.size()-1);

    // now we have all the possible names, but some of them might be inappropriate if we've
    // already generated a type slicer. On the other hand, if we've already done that, we're
    // going to steal any type specific ones off it.
    List<String> removed = new ArrayList<>();
    for (String s : list) {
      if (!allAnchors.containsKey(s)) {
        allAnchors.put(s, ed);
      } else if (s.endsWith("[x]")) {
        // that belongs on the earlier element
        removed.add(s);
      } else {
        // we delete it from the other
        @SuppressWarnings("unchecked")
        List<String> other = (List<String>) allAnchors.get(s).getUserData("dict.generator.anchors");
        other.remove(s);
        allAnchors.put(s, ed);
      }
    }
    list.removeAll(removed);
    ed.setUserData("dict.generator.anchors", list);
  }

  private void addToStack(List<ElementDefinition> stack, ElementDefinition ec) {
    while (!stack.isEmpty() && !isParent(stack.get(stack.size()-1), ec)) {
      stack.remove(stack.size()-1);
    }
    stack.add(ec);
  }

  private boolean isParent(ElementDefinition ed, ElementDefinition ec) {      
    return ec.getPath().startsWith(ed.getPath()+".");
  }

  private String makeAnchors(ElementDefinition ed, String anchorPrefix) {
    List<String> list = (List<String>) ed.getUserData("dict.generator.anchors");
    StringBuilder b = new StringBuilder();
    b.append("<a name=\"" + anchorPrefix + ed.getId() + "\"> </a>");
    for (String s : list) {
      if (!s.equals(ed.getId())) {
        b.append("<a name=\"" + anchorPrefix + s + "\"> </a>");
      }
    }
    return b.toString();
  }

  private String link(String id, String anchorPrefix) {
    return "<a href=\"#" + anchorPrefix + id + "\" title=\"link to here\" class=\"self-link\"><svg viewBox=\"0 0 1792 1792\" width=\"16\" class=\"self-link\" height=\"16\"><path d=\"M1520 1216q0-40-28-68l-208-208q-28-28-68-28-42 0-72 32 3 3 19 18.5t21.5 21.5 15 19 13 25.5 3.5 27.5q0 40-28 68t-68 28q-15 0-27.5-3.5t-25.5-13-19-15-21.5-21.5-18.5-19q-33 31-33 73 0 40 28 68l206 207q27 27 68 27 40 0 68-26l147-146q28-28 28-67zm-703-705q0-40-28-68l-206-207q-28-28-68-28-39 0-68 27l-147 146q-28 28-28 67 0 40 28 68l208 208q27 27 68 27 42 0 72-31-3-3-19-18.5t-21.5-21.5-15-19-13-25.5-3.5-27.5q0-40 28-68t68-28q15 0 27.5 3.5t25.5 13 19 15 21.5 21.5 18.5 19q33-31 33-73zm895 705q0 120-85 203l-147 146q-83 83-203 83-121 0-204-85l-206-207q-83-83-83-203 0-123 88-209l-88-88q-86 88-208 88-120 0-204-84l-208-208q-84-84-84-204t85-203l147-146q83-83 203-83 121 0 204 85l206 207q83 83 83 203 0 123-88 209l88 88q86-88 208-88 120 0 204 84l208 208q84 84 84 204z\" fill=\"navy\"></path></svg></a>";
  }

  private boolean isProfiledExtension(ElementDefinition ec) {
    return ec.getType().size() == 1 && "Extension".equals(ec.getType().get(0).getWorkingCode()) && ec.getType().get(0).hasProfile();
  }

  private ElementDefinition getExtensionValueDefinition(StructureDefinition extDefn) {
    for (ElementDefinition ed : extDefn.getSnapshot().getElement()) {
      if (ed.getPath().startsWith("Extension.value"))
        return ed;
    }
    return null;
  }

  public String compareMarkdown(String location, PrimitiveType md, PrimitiveType compare, int mode) throws FHIRException {
    if (compare == null)
      return processMarkdown(location, md);
    String newMd = processMarkdown(location, md);
    String oldMd = processMarkdown(location, compare);
    return compareString(newMd, oldMd, mode);
  }

  public String compareString(String newStr, String oldStr, int mode) {
    if (mode==GEN_MODE_SNAP || mode==GEN_MODE_MS)
      return newStr;
    if (oldStr==null || oldStr.isEmpty())
      if (newStr==null || newStr.isEmpty())
        return null;
      else
        return newStr;
    if (oldStr!=null && !oldStr.isEmpty() && (newStr==null || newStr.isEmpty())) {
      if (mode == GEN_MODE_DIFF)
        return "";
      else
        return removed(oldStr);
    }
    if (oldStr.equals(newStr))
      if (mode==GEN_MODE_DIFF)
        return "";
      else
        return unchanged(newStr);
    if (newStr.startsWith(oldStr))
      return unchanged(oldStr) + newStr.substring(oldStr.length());
    // TODO: improve comparision in this fall-through case, by looking for matches in sub-paragraphs?
    return newStr + removed(oldStr);
  }

  public String unchanged(String s) {
    return "<span style='color:DarkGray'>" + s + "</span>";
  }

  public String removed(String s) {
    return "<span style='color:DarkGray;text-decoration:line-through'>" + s + "</span>";
  }

  private void generateElementInner(StringBuilder b, StructureDefinition profile, ElementDefinition d, int mode, ElementDefinition value, ElementDefinition compare, ElementDefinition compareValue) throws Exception {
    boolean root = !d.getPath().contains(".");
    tableRow(b, translate("sd.dict", "SliceName"), "profiling.html#slicing", d.getSliceName());
    tableRowNE(b, translate("sd.dict", "Definition"), null, compareMarkdown(profile.getName(), d.getDefinitionElement(), compare==null ? null : compare.getDefinitionElement(), mode));
    tableRowNE(b, translate("sd.dict", "Note"), null, businessIdWarning(profile.getName(), tail(d.getPath())));
    tableRowNE(b, translate("sd.dict", "Control"), "conformance-rules.html#conformance", describeCardinality(d, compare, mode) + summariseConditions(d.getCondition(), compare==null?null:compare.getCondition(), mode));
    tableRowNE(b, translate("sd.dict", "Binding"), "terminologies.html", describeBinding(profile, d, d.getPath(), compare, mode));
    if (d.hasContentReference()) {
      tableRow(b, translate("sd.dict", "Type"), null, "See " + d.getContentReference().substring(1));
    } else {
      tableRowNE(b, translate("sd.dict", "Type"), "datatypes.html", describeTypes(d.getType(), false, compare, mode) + (value==null ? "" : processSecondary(mode, value, compareValue, mode)));
    }
    if (d.hasExtension(ToolingExtensions.EXT_DEF_TYPE)) {
      tableRowNE(b, translate("sd.dict", "Default Type"), "datatypes.html", ToolingExtensions.readStringExtension(d, ToolingExtensions.EXT_DEF_TYPE));          
    }
    if (d.hasExtension(ToolingExtensions.EXT_TYPE_SPEC)) {
      tableRowNE(b, translate("sd.dict", Utilities.pluralize("Type Specifier", d.getExtensionsByUrl(ToolingExtensions.EXT_TYPE_SPEC).size())),
          "datatypes.html", sdr.formatTypeSpecifiers(context, d));          
    }
    if (d.getPath().endsWith("[x]"))
      tableRowNE(b, translate("sd.dict", "[x] Note"), null, translate("sd.dict", "See %sChoice of Data Types%s for further information about how to use [x]", "<a href=\"" + corePath + "formats.html#choice\">", "</a>"));
    tableRowNE(b, translate("sd.dict", "Is Modifier"), "conformance-rules.html#ismodifier", displayBoolean(d.getIsModifier(), null, mode));
    tableRowNE(b, translate("sd.dict", "Must Support"), "conformance-rules.html#mustSupport", displayBoolean(d.getMustSupport(), compare==null ? null : compare.getMustSupportElement(), mode));
    if (d.getMustSupport()) {
      if (hasMustSupportTypes(d.getType())) {
        tableRowNE(b, translate("sd.dict", "Must Support Types"), "datatypes.html", describeTypes(d.getType(), true, compare, mode));
      } else if (hasChoices(d.getType())) {
        tableRowNE(b, translate("sd.dict", "Must Support Types"), "datatypes.html", "No must-support rules about the choice of types/profiles");
      }
    }
    if (root && sd.getKind() == StructureDefinitionKind.LOGICAL) {
      tableRowNE(b, translate("sd.dict", "Logical Model"), null, ToolingExtensions.readBoolExtension(sd, ToolingExtensions.EXT_LOGICAL_TARGET) ?
          "This logical model can be the target of a reference" : "This logical model cannot be the target of a reference");
    }
    ObligationsRenderer obr = new ObligationsRenderer(corePath, sd, d.getPath(), gen, this, sdr);
    obr.seeObligations(d.getExtension());
    if (obr.hasObligations() || (root && (sd.hasExtension(ToolingExtensions.EXT_OBLIGATION_PROFILE_FLAG) || sd.hasExtension(ToolingExtensions.EXT_OBLIGATION_INHERITS)))) {
      StringBuilder s = new StringBuilder();
      XhtmlNode ul = new XhtmlNode(NodeType.Element, "ul");
      if (root) {
        if (sd.hasExtension(ToolingExtensions.EXT_OBLIGATION_PROFILE_FLAG)) {
          ul.li().tx("This is an obligation profile that only contains obligations and additional bindings");           
        } 
        for (Extension ext : sd.getExtensionsByUrl(ToolingExtensions.EXT_OBLIGATION_INHERITS)) {
          String iu = ext.getValue().primitiveValue();
          XhtmlNode bb = ul.li();
          bb.tx("This profile picks up obligations and additional bindings from ");           
          StructureDefinition sd = context.fetchResource(StructureDefinition.class, iu); 
          if (sd == null) { 
            bb.code().tx(iu);                     
          } else if (sd.hasWebPath()) { 
            bb.ah(sd.getWebPath()).tx(sd.present());
          } else { 
            bb.ah(iu).tx(sd.present());
          } 
        }  
        if (ul.hasChildren()) {
          s.append(new XhtmlComposer(true).compose(ul));
        }
      }
      if (obr.hasObligations()) {
        XhtmlNode tbl = new XhtmlNode(NodeType.Element, "table").attribute("class", "grid");
        obr.renderTable(tbl.getChildNodes(), true);
        if (tbl.hasChildren()) {
          s.append(new XhtmlComposer(true).compose(tbl));
        }
      }
      tableRowNE(b, translate("sd.dict", "Obligations"), null, s.toString());   
    }
    
    if (d.hasExtension(ToolingExtensions.EXT_EXTENSION_STYLE)) {
      String es = d.getExtensionString(ToolingExtensions.EXT_EXTENSION_STYLE);
      if ("named-elements".equals(es)) {
        if (gen.hasLink(KnownLinkType.JSON_NAMES)) {
//          c.getPieces().add(gen.new Piece(rc.getLink(KnownLinkType.JSON_NAMES), "This element can be extended by named JSON elements", null));                        
          tableRowNE(b, translate("sd.dict", "Extension Style"), gen.getLink(KnownLinkType.JSON_NAMES), "This element can be extended by named JSON elements");
        } else {
//          c.getPieces().add(gen.new Piece(null, "This element can be extended by named JSON elements", null));                        
          tableRowNE(b, translate("sd.dict", "Extension Style"), ToolingExtensions.WEB_EXTENSION_STYLE, "This element can be extended by named JSON elements");
        }
      }
    }

    if (!d.getPath().contains(".") && ToolingExtensions.hasExtension(profile, ToolingExtensions.EXT_BINDING_STYLE)) {
      tableRowNE(b, translate("sd.dict", "Binding Style"), ToolingExtensions.WEB_BINDING_STYLE, 
          "This type can be bound to a value set using the " + ToolingExtensions.readStringExtension(profile, ToolingExtensions.EXT_BINDING_STYLE)+" binding style");            
    }
    
    if (d.hasExtension(ToolingExtensions.EXT_DATE_FORMAT)) {
      String df = ToolingExtensions.readStringExtension(d, ToolingExtensions.EXT_DATE_FORMAT);
      if (df != null) {
        tableRowNE(b, translate("sd.dict", "Date Format"), null, df);
      }
    }
    String ide = ToolingExtensions.readStringExtension(d, ToolingExtensions.EXT_ID_EXPECTATION);
    if (ide != null) {
      if (ide.equals("optional")) {
        tableRowNE(b, translate("sd.dict", "ID Expectation"), null, "Id may or not be present (this is the default for elements but not resources)");
      } else if (ide.equals("required")) {
        tableRowNE(b, translate("sd.dict", "ID Expectation"), null, "Id is required to be present (this is the default for resources but not elements)");
      } else if (ide.equals("required")) {
        tableRowNE(b, translate("sd.dict", "ID Expectation"), null, "An ID is not allowed in this context");
      }
    }
    // tooling extensions for formats
    if (ToolingExtensions.hasExtensions(d, ToolingExtensions.EXT_JSON_EMPTY, ToolingExtensions.EXT_JSON_PROP_KEY, ToolingExtensions.EXT_JSON_NULLABLE, 
        ToolingExtensions.EXT_JSON_NAME, ToolingExtensions.EXT_JSON_PRIMITIVE_CHOICE)) {
      boolean list = ToolingExtensions.countExtensions(d, ToolingExtensions.EXT_JSON_EMPTY, ToolingExtensions.EXT_JSON_PROP_KEY, ToolingExtensions.EXT_JSON_NULLABLE, ToolingExtensions.EXT_JSON_NAME) > 1;
      StringBuilder s = new StringBuilder();
      String pfx = "";
      String sfx = "";
      if (list) {
        s.append("<ul>\r\n");
        pfx = "<li>";
        sfx = "</li>\r\n";
      }
      String code = ToolingExtensions.readStringExtension(d, ToolingExtensions.EXT_JSON_EMPTY);
      if (code != null) {
        switch (code) {
        case "present":
          s.append(pfx+"The JSON Array for this property is present even when there are no items in the instance (e.g. as an empty array)"+sfx);
          break;
        case "absent":
          s.append(pfx+"The JSON Array for this property is not present when there are no items in the instance (e.g. never as an empty array)"+sfx);
          break;
        case "either":
          s.append(pfx+"The JSON Array for this property may be present even when there are no items in the instance (e.g. may be present as an empty array)</li>\r\n ");
          break;
        }
      }
      String jn = ToolingExtensions.readStringExtension(d, ToolingExtensions.EXT_JSON_NAME);
      if (jn != null) {
        if (d.getPath().contains(".")) {
          s.append(pfx+"This property appears in JSON with the property name <code>"+Utilities.escapeXml(jn)+"</code>"+sfx);
        } else {
          s.append(pfx+"This type can appear in JSON with the property name <code>"+Utilities.escapeXml(jn)+"</code> (in elements using named extensions)"+sfx);          
        }
      }
      code = ToolingExtensions.readStringExtension(d, ToolingExtensions.EXT_JSON_PROP_KEY);
      if (code != null) {
        s.append(pfx+"This repeating object is represented as a single JSON object with named properties. The name of the property (key) is the value of the <code>"+Utilities.escapeXml(code)+"</code> child"+sfx);
      }
      if (ToolingExtensions.readBoolExtension(d, ToolingExtensions.EXT_JSON_NULLABLE)) {
        s.append(pfx+"This object can be represented as null in the JSON structure (which counts as 'present' for cardinality purposes)"+sfx);
      }
      if (ToolingExtensions.readBoolExtension(d, ToolingExtensions.EXT_JSON_PRIMITIVE_CHOICE)) {
        s.append(pfx+"The type of this element is inferred from the JSON type in the instance"+sfx);
      }
      if (list) s.append("<ul>");
      tableRowNE(b, translate("sd.dict", "JSON Representation"), null,  s.toString());          
    }
    if (d.hasExtension(ToolingExtensions.EXT_XML_NAMESPACE) || profile.hasExtension(ToolingExtensions.EXT_XML_NAMESPACE) || d.hasExtension(ToolingExtensions.EXT_XML_NAME) || (root && profile.hasExtension(ToolingExtensions.EXT_XML_NO_ORDER)) ||
        d.hasRepresentation()) {
      StringBuilder s = new StringBuilder();
      for (PropertyRepresentation pr : PropertyRepresentation.values()) {
        if (d.hasRepresentation(pr)) {
          switch (pr) {
          case CDATEXT:
            s.append("This property is represented as CDA Text in the XML.");
            break;
          case TYPEATTR:
            s.append("The type of this property is determined using the xsi:type attribute.");
            break;
          case XHTML:
            s.append("This property is represented as XHTML Text in the XML.");
            break;
          case XMLATTR:
            s.append("In the XML format, this property is represented as an attribute.");
            break;
          case XMLTEXT:
            s.append("In the XML format, this property is represented as unadorned text.");
            break;
          default:
          }
        }
      }
      String name = ToolingExtensions.readStringExtension(d, ToolingExtensions.EXT_XML_NAMESPACE);
      if (name == null && root) {
        name = ToolingExtensions.readStringExtension(profile, ToolingExtensions.EXT_XML_NAMESPACE);
      }
      if (name != null) {
        s.append("In the XML format, this property has the namespace <code>"+name+"</code>.");
      }
      name = ToolingExtensions.readStringExtension(d, ToolingExtensions.EXT_XML_NAME);
      if (name != null) {
        s.append("In the XML format, this property has the actual name <code>"+name+"</code>.");
      }
      boolean no = root && ToolingExtensions.readBoolExtension(profile, ToolingExtensions.EXT_XML_NO_ORDER);
      if (no) {
        s.append("The children of this property can appear in any order in the XML.");
      }
      tableRowNE(b, translate("sd.dict", "XML Representation"), null, s.toString());          
    }

    if (d.hasExtension(ToolingExtensions.EXT_IMPLIED_PREFIX)) {
      tableRowNE(b, translate("sd.dict", "Validation Rules"), null, "When this element is read <code>"
          +ToolingExtensions.readStringExtension(d, ToolingExtensions.EXT_IMPLIED_PREFIX)+"</code> is prefixed to the value before validation");                
    }
    
    if (d.hasExtension(ToolingExtensions.EXT_STANDARDS_STATUS)) {
      StandardsStatus ss = StandardsStatus.fromCode(d.getExtensionString(ToolingExtensions.EXT_STANDARDS_STATUS));
//      gc.addStyledText("Standards Status = "+ss.toDisplay(), ss.getAbbrev(), "black", ss.getColor(), baseSpecUrl()+, true);
      StructureDefinition sdb = context.fetchResource(StructureDefinition.class, profile.getBaseDefinition());
      if (sdb != null) {
        StandardsStatus base = determineStandardsStatus(sdb, (ElementDefinition) d.getUserData("derived.pointer"));
        if (base != null) {
          tableRowNE(b, translate("sd.dict", "Standards Status"), "versions.html#std-process", ss.toDisplay()+" (from "+base.toDisplay()+")");
        } else {
          tableRowNE(b, translate("sd.dict", "Standards Status"), "versions.html#std-process", ss.toDisplay());          
        }
      } else {
        tableRowNE(b, translate("sd.dict", "Standards Status"), "versions.html#std-process", ss.toDisplay());
      }
    }
    if (mode != GEN_MODE_DIFF && d.hasIsSummary()) {
      tableRow(b, "Summary", "search.html#summary", Boolean.toString(d.getIsSummary()));
    }
    tableRowNE(b, translate("sd.dict", "Requirements"), null, compareMarkdown(profile.getName(), d.getRequirementsElement(), compare==null ? null : compare.getRequirementsElement(), mode));
    tableRowHint(b, translate("sd.dict", "Alternate Names"), translate("sd.dict", "Other names by which this resource/element may be known"), null, compareSimpleTypeLists(d.getAlias(), (compare==null ? new ArrayList<StringType>() : compare.getAlias()), mode));
    tableRowNE(b, translate("sd.dict", "Comments"), null, compareMarkdown(profile.getName(), d.getCommentElement(), compare==null ? null : compare.getCommentElement(), mode));
    tableRowNE(b, translate("sd.dict", "Max Length"), null, !d.hasMaxLengthElement() ? null : compare!= null && compare.hasMaxLengthElement() ? compareString(toStr(d.getMaxLength()), toStr(compare.getMaxLength()), mode) : toStr(d.getMaxLength()));
    tableRowNE(b, translate("sd.dict", "Default Value"), null, encodeValue(d.getDefaultValue(), compare==null ? null : compare.getDefaultValue(), mode));
    tableRowNE(b, translate("sd.dict", "Meaning if Missing"), null, d.getMeaningWhenMissing());
    tableRowNE(b, translate("sd.dict", "Fixed Value"), null, encodeValue(d.getFixed(), compare==null ? null : compare.getFixed(), mode));
    tableRowNE(b, translate("sd.dict", "Pattern Value"), null, encodeValue(d.getPattern(), compare==null ? null : compare.getPattern(), mode));
    tableRowNE(b, translate("sd.dict", "Example"), null, encodeValues(d.getExample()));
    tableRowNE(b, translate("sd.dict", "Invariants"), null, invariants(d.getConstraint(), compare==null ? null : compare.getConstraint(), mode));
    tableRowNE(b, translate("sd.dict", "LOINC Code"), null, getMapping(profile, d, LOINC_MAPPING, compare, mode));
    tableRowNE(b, translate("sd.dict", "SNOMED-CT Code"), null, getMapping(profile, d, SNOMED_MAPPING, compare, mode));
  }

  private StandardsStatus determineStandardsStatus(StructureDefinition sd, ElementDefinition ed) {
    if (ed != null && ed.hasExtension(ToolingExtensions.EXT_STANDARDS_STATUS)) {
      return StandardsStatus.fromCode(ed.getExtensionString(ToolingExtensions.EXT_STANDARDS_STATUS));
    }
    while (sd != null) {
      if (sd.hasExtension(ToolingExtensions.EXT_STANDARDS_STATUS)) {
        return ToolingExtensions.getStandardsStatus(sd);
      }
      sd = context.fetchResource(StructureDefinition.class, sd.getBaseDefinition());
    }
    return null;
  }

  private boolean hasChoices(List<TypeRefComponent> types) {
    for (TypeRefComponent type : types) {
      if (type.getProfile().size() > 1 || type.getTargetProfile().size() > 1) {
        return true;
      }
    }
    return types.size() > 1;
  }

  private String sliceOrderString(ElementDefinitionSlicingComponent slicing) {
    if (slicing.getOrdered())
      return translate("sd.dict", "ordered");
    else
      return translate("sd.dict", "unordered");
  }
  private void generateSlicing(StringBuilder b, StructureDefinition profile, ElementDefinition ed, ElementDefinitionSlicingComponent slicing, ElementDefinition compare, int mode) throws IOException {
    String newOrdered = sliceOrderString(slicing);
    String oldOrdered = (compare==null || !compare.hasSlicing()) ? null : sliceOrderString(compare.getSlicing());
    String slicingRules = compareString(slicing.hasRules() ? slicing.getRules().getDisplay() : null, compare!=null && compare.hasSlicing() && compare.getSlicing().hasRules() ? compare.getSlicing().getRules().getDisplay() : null, mode);
    String rs = compareString(newOrdered, oldOrdered, mode) + " and " + slicingRules;

    StringBuilder bl = new StringBuilder();
    List<ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent> newDiscs = slicing.getDiscriminator();
    List<ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent> oldDiscs;
    if (compare!=null && compare.hasSlicing())
      oldDiscs = compare.getSlicing().getDiscriminator();
    else
      oldDiscs = new ArrayList<>();
    if (!newDiscs.isEmpty() || oldDiscs.isEmpty()) {
      boolean first = true;
      for (int i=0; i<newDiscs.size()||i<oldDiscs.size(); i++) {
        ElementDefinitionSlicingDiscriminatorComponent newDisc = i<newDiscs.size() ? slicing.getDiscriminator().get(0) : null;
        ElementDefinitionSlicingDiscriminatorComponent oldDisc = i<oldDiscs.size() ? slicing.getDiscriminator().get(0) : null;
        String code = compareString(newDisc==null ? null : newDisc.getType().toCode(), oldDisc==null ? null : oldDisc.getType().toCode(), mode);
        String path = compareString(newDisc==null ? null : newDisc.getPath(), oldDisc==null ? null : oldDisc.getPath(), mode);
        bl.append("<li>" + code + " @ " + path + (i!=0 ? ", ": "") + "</li>");
      }
    }
    if (slicing.hasDiscriminator())
      tableRowNE(b, "" + translate("sd.dict", "Slicing"), "profiling.html#slicing", "This element introduces a set of slices on " + ed.getPath() + ". The slices are " + rs + ", and can be differentiated using the following discriminators: <ul> " + bl.toString() + "</ul>");
    else
      tableRowNE(b, "" + translate("sd.dict", "Slicing"), "profiling.html#slicing", "This element introduces a set of slices on " + ed.getPath() + ". The slices are " + rs + ", and defines no discriminators to differentiate the slices");
  }

  private void tableRow(StringBuilder b, String name, String defRef, String value) throws IOException {
    if (value != null && !"".equals(value)) {
      if (defRef != null)
        b.append("  <tr><td><a href=\"" + corePath + defRef + "\">" + name + "</a></td><td>" + Utilities.escapeXml(value) + "</td></tr>\r\n");
      else
        b.append("  <tr><td>" + name + "</td><td>" + Utilities.escapeXml(value) + "</td></tr>\r\n");
    }
  }


  private void tableRowHint(StringBuilder b, String name, String hint, String defRef, String value) throws IOException {
    if (value != null && !"".equals(value)) {
      if (defRef != null)
        b.append("  <tr><td><a href=\"" + corePath + defRef + "\" title=\"" + Utilities.escapeXml(hint) + "\">" + name + "</a></td><td>" + value + "</td></tr>\r\n");
      else
        b.append("  <tr><td title=\"" + Utilities.escapeXml(hint) + "\">" + name + "</td><td>" + value + "</td></tr>\r\n");
    }
  }


  private void tableRowNE(StringBuilder b, String name, String defRef, String value) throws IOException {
    if (value != null && !"".equals(value))
      if (defRef == null) {
        b.append("  <tr><td>" + name + "</td><td>" + value + "</td></tr>\r\n");
      } else if (Utilities.isAbsoluteUrl(defRef)) {
        b.append("  <tr><td><a href=\"" + defRef + "\">" + name + "</a></td><td>" + value + "</td></tr>\r\n");
      } else {
        b.append("  <tr><td><a href=\"" + corePath + defRef + "\">" + name + "</a></td><td>" + value + "</td></tr>\r\n");
      }
  }

  private String head(String path) {
    if (path.contains("."))
      return path.substring(0, path.indexOf("."));
    else
      return path;
  }

  private String tail(String path) {
    if (path.contains("."))
      return path.substring(path.lastIndexOf(".") + 1);
    else
      return path;
  }

  private String nottail(String path) {
    if (path.contains("."))
      return path.substring(0, path.lastIndexOf("."));
    else
      return path;
  }

  private String businessIdWarning(String resource, String name) {
    if (name.equals("identifier"))
      return "" + translate("sd.dict", "This is a business identifier, not a resource identifier (see %sdiscussion%s)", "<a href=\"" + corePath + "resource.html#identifiers\">", "</a>");
    if (name.equals("version")) // && !resource.equals("Device"))
      return "" + translate("sd.dict", "This is a business versionId, not a resource version id (see %sdiscussion%s)", "<a href=\"" + corePath + "resource.html#versions\">", "</a>");
    return null;
  }

  private String describeCardinality(ElementDefinition d, ElementDefinition compare, int mode) {
    if (compare==null) {
      if (!d.hasMax() && d.getMinElement() == null)
        return "";
      else if (d.getMax() == null)
        return toStr(d.getMin()) + "..?";
      else
        return toStr(d.getMin()) + ".." + d.getMax();
    } else {
      String min = compareString(toStr(d.getMin()), toStr(compare.getMin()), mode);
      if (mode==GEN_MODE_DIFF && (d.getMin()==compare.getMin() || d.getMin()==0))
        min=null;
      String max = compareString(d.getMax(), compare.getMax(), mode);
      if ((min==null || min.isEmpty()) && (max==null || max.isEmpty()))
        return "";
      if (mode==GEN_MODE_DIFF)
        return( (min==null || min.isEmpty() ? unchanged(toStr(compare.getMin())) : min) + ".." + (max==null || max.isEmpty() ? unchanged(compare.getMax()) : max));
      else
        return( (min==null || min.isEmpty() ? "?" : min) + ".." + (max==null || max.isEmpty() ? "?" : max));
    }
  }

  private String summariseConditions(List<IdType> conditions, List<IdType> compare, int mode) {
    if (conditions.isEmpty() && (compare==null || compare.isEmpty()))
      return "";
    else {
      String comparedList = compareSimpleTypeLists(conditions, compare, mode);
      if (comparedList.equals(""))
        return "";
      return " " + translate("sd.dict", "This element is affected by the following invariants") + ": " + comparedList;
    }
  }

  private boolean hasMustSupportTypes(List<TypeRefComponent> types) {
    for (TypeRefComponent tr : types) {
      if (sdr.isMustSupport(tr)) {
        return true;
      }
    }
    return false;
  }

  private String describeTypes(List<TypeRefComponent> types, boolean mustSupportOnly, ElementDefinition compare, int mode) throws Exception {
    if (types.isEmpty())
      return "";

    List<TypeRefComponent> compareTypes = compare==null ? new ArrayList<>() : compare.getType();
    StringBuilder b = new StringBuilder();
    if ((!mustSupportOnly && types.size() == 1 && compareTypes.size() <=1) || (mustSupportOnly && mustSupportCount(types) == 1)) {
      if (!mustSupportOnly || sdr.isMustSupport(types.get(0))) {
        describeType(b, types.get(0), mustSupportOnly, compareTypes.size()==0 ? null : compareTypes.get(0), mode);
      }
    } else {
      boolean first = true;
      b.append(translate("sd.dict", "Choice of") + ": ");
      Map<String,TypeRefComponent> map = new HashMap<String, TypeRefComponent>();
      for (TypeRefComponent t : compareTypes) {
        map.put(t.getCode(), t);
      }
      for (TypeRefComponent t : types) {
        TypeRefComponent compareType = map.get(t.getCode());
        if (compareType!=null)
          map.remove(t.getCode());
        if (!mustSupportOnly || sdr.isMustSupport(t)) {
          if (first) {
            first = false;
          } else {
            b.append(", ");
          }
          describeType(b, t, mustSupportOnly, compareType, mode);
        }
      }
      for (TypeRefComponent t : map.values()) {
        b.append(", ");
        StringBuilder b2 = new StringBuilder();
        describeType(b2, t, mustSupportOnly, null, mode);
        b.append(removed(b2.toString()));
      }
    }
    return b.toString();
  }

  private int mustSupportCount(List<TypeRefComponent> types) {
    int c = 0;
    for (TypeRefComponent tr : types) {
      if (sdr.isMustSupport(tr)) {
        c++;
      }
    }
    return c;
  }

  private void describeType(StringBuilder b, TypeRefComponent t, boolean mustSupportOnly, TypeRefComponent compare, int mode) throws Exception {
    if (t.getWorkingCode() == null) {
      return;
    }
    if (t.getWorkingCode().startsWith("=")) {
      return;
    }

    if (t.getWorkingCode().startsWith("xs:")) {
      b.append(compareString(t.getWorkingCode(), compare==null ? null : compare.getWorkingCode(), mode));
    } else {
      b.append(compareString(getTypeLink(t), compare==null ? null : getTypeLink(compare), mode));
    }
    if ((!mustSupportOnly && (t.hasProfile() || (compare!=null && compare.hasProfile()))) || sdr.isMustSupport(t.getProfile())) {
      List<String> newProfiles = new ArrayList<String>();
      List<String> oldProfiles = new ArrayList<String>();
      for (CanonicalType pt : t.getProfile()) {
        newProfiles.add(getTypeProfile(pt, mustSupportOnly));
      }
      if (compare!=null) {
        for (CanonicalType pt : compare.getProfile()) {
          oldProfiles.add(getTypeProfile(pt, mustSupportOnly));
        }
      }
      String profiles = compareSimpleTypeLists(newProfiles, oldProfiles, mode);
      if (!profiles.isEmpty()) {
        if (b.toString().isEmpty())
          b.append(unchanged(getTypeLink(t)));
        b.append("(");
        b.append(profiles);
        b.append(")");
      }
    }
    if ((!mustSupportOnly && (t.hasTargetProfile() || (compare!=null && compare.hasTargetProfile()))) || sdr.isMustSupport(t.getTargetProfile())) {
      List<StringType> newProfiles = new ArrayList<StringType>();
      List<StringType> oldProfiles = new ArrayList<StringType>();
      for (CanonicalType pt : t.getTargetProfile()) {
        String tgtProfile = getTypeProfile(pt, mustSupportOnly);
        if (!tgtProfile.isEmpty())
          newProfiles.add(new StringType(tgtProfile));
      }
      if (compare!=null) {
        for (CanonicalType pt : compare.getTargetProfile()) {
          String tgtProfile = getTypeProfile(pt, mustSupportOnly);
          if (!tgtProfile.isEmpty())
            oldProfiles.add(new StringType(tgtProfile));
        }
      }
      String profiles = compareSimpleTypeLists(newProfiles, oldProfiles, mode, "|");
      if (!profiles.isEmpty()) {
        if (b.toString().isEmpty())
          b.append(unchanged(getTypeLink(t)));
        b.append("(");
        b.append(profiles);
        b.append(")");
      }

      if (!t.getAggregation().isEmpty() || (compare!=null && !compare.getAggregation().isEmpty())) {
        b.append(" : ");
        List<String> newAgg = new ArrayList<String>();
        List<String> oldAgg = new ArrayList<String>();
        for (Enumeration<AggregationMode> a :t.getAggregation()) {
          newAgg.add(" <a href=\"" + corePath + "codesystem-resource-aggregation-mode.html#content\" title=\""+sdr.hintForAggregation(a.getValue())+"\">{" + sdr.codeForAggregation(a.getValue()) + "}</a>");
        }
        if (compare!=null) {
          for (Enumeration<AggregationMode> a : compare.getAggregation()) {
            newAgg.add(" <a href=\"" + corePath + "codesystem-resource-aggregation-mode.html#content\" title=\"" + sdr.hintForAggregation(a.getValue()) + "\">{" + sdr.codeForAggregation(a.getValue()) + "}</a>");
          }
        }
        b.append(compareSimpleTypeLists(newAgg, oldAgg, mode));
      }
    }
  }

  private String getTypeProfile(CanonicalType pt, boolean mustSupportOnly) {
    StringBuilder b = new StringBuilder();
    if (!mustSupportOnly || sdr.isMustSupport(pt)) {
      StructureDefinition p = context.fetchResource(StructureDefinition.class, pt.getValue());
      if (p == null)
        b.append(pt.getValue());
      else {
        String pth = p.getWebPath();
        b.append("<a href=\"" + Utilities.escapeXml(pth) + "\" title=\"" + pt.getValue() + "\">");
        b.append(p.getName());
        b.append("</a>");
      }
    }
    return b.toString();
  }

  private String getTypeLink(TypeRefComponent t) {
    StringBuilder b = new StringBuilder();
    String s = igp.getLinkFor(sd.getWebPath(), t.getWorkingCode());
    if (s != null) {
      b.append("<a href=\"");
      //    GG 13/12/2016 - I think that this is always wrong now.
      //      if (!s.startsWith("http:") && !s.startsWith("https:") && !s.startsWith(".."))
      //        b.append(prefix);
      b.append(s);
      if (!s.contains(".html")) {
        //     b.append(".html#");
        //     String type = t.getCode();
        //     if (type.equals("*"))
        //       b.append("open");
        //     else
        //       b.append(t.getCode());
      }
      b.append("\">");
      b.append(t.getWorkingCode());
      b.append("</a>");
    } else {
      b.append(t.getWorkingCode());
    }
    return b.toString();
  }

  private String processSecondary(int mode, ElementDefinition value, ElementDefinition compareValue, int compMode) throws Exception {
    switch (mode) {
    case 1:
      return "";
    case 2:
      return "  (" + translate("sd.dict", "Complex Extension") + ")";
    case 3:
      return "  (" + translate("sd.dict", "Extension Type") + ": " + describeTypes(value.getType(), false, compareValue, compMode) + ")";
    default:
      return "";
    }
  }

  private String displayBoolean(boolean value, BooleanType compare, int mode) {
    String newValue = value ? "true" : null;
    String oldValue = compare==null || compare.getValue()==null ? null : (compare.getValue()!=true ? null : "true");
    return compareString(newValue, oldValue, mode);
  }

  private String invariants(List<ElementDefinitionConstraintComponent> constraints, List<ElementDefinitionConstraintComponent> compare, int mode) {
    // Lloyd todo compare
    if (constraints.isEmpty() && (compare==null || compare.isEmpty()))
      return null;
    if (compare == null)
      compare = new ArrayList<ElementDefinitionConstraintComponent>();
    StringBuilder s = new StringBuilder();
    if (constraints.size() > 0) {
      s.append("<b>" + translate("sd.dict", "Defined on this element") + "</b><br/>\r\n");
      List<String> ids = new ArrayList<String>();
      for (ElementDefinitionConstraintComponent id : constraints)
        ids.add(id.hasKey() ? id.getKey() : id.toString());
      Collections.sort(ids, new ConstraintKeyComparator());
      boolean b = false;
      for (String id : ids) {
        ElementDefinitionConstraintComponent inv = getConstraint(constraints, id);
        ElementDefinitionConstraintComponent invCompare = getConstraint(compare, id);
        if (b)
          s.append("<br/>");
        else
          b = true;
        String human = compareString(Utilities.escapeXml(gt(inv.getHumanElement())), invCompare==null ? null : Utilities.escapeXml(gt(invCompare.getHumanElement())), mode);
        String expression= compareString(Utilities.escapeXml(inv.getExpression()), invCompare==null ? null : Utilities.escapeXml(invCompare.getExpression()), mode);
        s.append("<b title=\"" + translate("sd.dict", "Formal Invariant Identifier") + "\">" + id + "</b>: " + human + " (: " + expression + ")");
      }
    }

    return s.toString();
  }

  private ElementDefinitionConstraintComponent getConstraint(List<ElementDefinitionConstraintComponent> constraints, String id) {
    for (ElementDefinitionConstraintComponent c : constraints) {
      if (c.hasKey() && c.getKey().equals(id))
        return c;
      if (!c.hasKey() && c.toString().equals(id))
        return c;
    }
    return null;
  }

  private String describeBinding(StructureDefinition sd, ElementDefinition d, String path, ElementDefinition compare, int mode) throws Exception {
    if (!d.hasBinding())
      return null;
    else {
      ElementDefinitionBindingComponent binding = d.getBinding();
      ElementDefinitionBindingComponent compBinding = compare == null ? null : compare.getBinding();
      String bindingDesc = null;
      if (binding.hasDescription()) {
        StringType newBinding = PublicationHacker.fixBindingDescriptions(context, binding.getDescriptionElement());
        if (mode == GEN_MODE_SNAP || mode == GEN_MODE_MS)
          bindingDesc = processMarkdown("Binding.description", newBinding);
        else {
          StringType oldBinding = compBinding != null && compBinding.hasDescription() ? PublicationHacker.fixBindingDescriptions(context, compBinding.getDescriptionElement()) : null;
          bindingDesc = compareMarkdown("Binding.description", newBinding, oldBinding, mode);
        }
      }
      if (!binding.hasValueSet())
        return bindingDesc;
      BindingResolution br = igp.resolveBinding(sd, binding, path);
      String s = conf(binding) + "<a href=\"" + Utilities.escapeXml(br.url) + "\">" + Utilities.escapeXml(br.display) + "</a>" + confTail(binding);
      if (compBinding!=null ) {
        BindingResolution compBr = igp.resolveBinding(sd, compBinding, path);
        String compS = conf(compBinding) + "<a href=\"" + Utilities.escapeXml(compBr.url) + "\">" + Utilities.escapeXml(compBr.display) + "</a>" + confTail(compBinding);
        s = compareString(s, compS, mode);
      }
      if (binding.hasDescription()) {
        String desc = bindingDesc;
        if (desc != null) {
          if (desc.length() > 4 && desc.substring(4).contains("<p>")) {
            s = s + "<br/>" + desc;
          } else {
            s = s + "\r\n" + stripPara(desc);
          }
        }
      }

      AdditionalBindingsRenderer abr = new AdditionalBindingsRenderer(igp, corePath, sd, d.getPath(), gen, this, sdr);
      if (binding.hasExtension(ToolingExtensions.EXT_MAX_VALUESET)) {
        abr.seeMaxBinding(ToolingExtensions.getExtension(binding, ToolingExtensions.EXT_MAX_VALUESET), compBinding==null ? null : ToolingExtensions.getExtension(compBinding, ToolingExtensions.EXT_MAX_VALUESET), mode!=GEN_MODE_SNAP && mode!=GEN_MODE_MS);
      }
      if (binding.hasExtension(ToolingExtensions.EXT_MIN_VALUESET)) {
        abr.seeMinBinding(ToolingExtensions.getExtension(binding, ToolingExtensions.EXT_MIN_VALUESET), compBinding==null ? null : ToolingExtensions.getExtension(compBinding, ToolingExtensions.EXT_MIN_VALUESET), mode!=GEN_MODE_SNAP && mode!=GEN_MODE_MS);
      }
      if (binding.hasExtension(ToolingExtensions.EXT_BINDING_ADDITIONAL)) {
        abr.seeAdditionalBindings(binding.getExtensionsByUrl(ToolingExtensions.EXT_BINDING_ADDITIONAL), compBinding==null ? null : compBinding.getExtensionsByUrl(ToolingExtensions.EXT_BINDING_ADDITIONAL), mode!=GEN_MODE_SNAP && mode!=GEN_MODE_MS);
      }

      s = s + abr.render();

      return s;
    }
  }


  private String stripPara(String s) {
    if (s.startsWith("<p>")) {
      s = s.substring(3);
    }
    if (s.trim().endsWith("</p>")) {
      s = s.substring(0, s.lastIndexOf("</p>")-1) + s.substring(s.lastIndexOf("</p>") +4);
    }
    return s;
  }

  private String confTail(ElementDefinitionBindingComponent def) {
    if (def.getStrength() == BindingStrength.EXTENSIBLE)
      return "; " + translate("sd.dict", "other codes may be used where these codes are not suitable");
    else
      return "";
  }

  private String conf(ElementDefinitionBindingComponent def) {
    if (def.getStrength() == null) {
      return "" + translate("sd.dict", "For codes, see ");
    }
    switch (def.getStrength()) {
    case EXAMPLE:
      return "" + translate("sd.dict", "For example codes, see ");
    case PREFERRED:
      return "" + translate("sd.dict", "The codes SHOULD be taken from ");
    case EXTENSIBLE:
      return "" + translate("sd.dict", "The codes SHALL be taken from ");
    case REQUIRED:
      return "" + translate("sd.dict", "The codes SHALL be taken from ");
    default:
      return "" + "?sd-conf?";
    }
  }

  private String encodeValues(List<ElementDefinitionExampleComponent> examples) throws Exception {
    StringBuilder b = new StringBuilder();
    boolean first = false;
    for (ElementDefinitionExampleComponent ex : examples) {
      if (first)
        first = false;
      else
        b.append("<br/>");
      b.append("<b>" + Utilities.escapeXml(ex.getLabel()) + "</b>:" + encodeValue(ex.getValue()) + "\r\n");
    }
    return b.toString();

  }

  private String encodeValue(DataType value, DataType compare, int mode) throws Exception {
    String oldValue = encodeValue(compare);
    String newValue = encodeValue(value);
    return compareString(newValue, oldValue, mode);
  }

  private String encodeValue(DataType value) throws Exception {
    if (value == null || value.isEmpty())
      return null;
    if (value instanceof PrimitiveType)
      return Utilities.escapeXml(((PrimitiveType) value).asStringValue());

    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    XmlParser parser = new XmlParser();
    parser.setOutputStyle(OutputStyle.PRETTY);
    parser.compose(bs, null, value);
    String[] lines = bs.toString().split("\\r?\\n");
    StringBuilder b = new StringBuilder();
    for (String s : lines) {
      if (!Utilities.noString(s) && !s.startsWith("<?")) { // eliminate the xml header
        b.append(Utilities.escapeXml(s).replace(" ", "&nbsp;") + "<br/>");
      }
    }
    return b.toString();

  }

  private String getMapping(StructureDefinition profile, ElementDefinition d, String uri, ElementDefinition compare, int mode) {
    String id = null;
    for (StructureDefinitionMappingComponent m : profile.getMapping()) {
      if (m.hasUri() && m.getUri().equals(uri))
        id = m.getIdentity();
    }
    if (id == null)
      return null;
    String newMap = null;
    for (ElementDefinitionMappingComponent m : d.getMapping()) {
      if (m.getIdentity().equals(id)) {
        newMap = m.getMap();
        break;
      }
    }
    if (compare==null)
      return newMap;
    String oldMap = null;
    for (ElementDefinitionMappingComponent m : compare.getMapping()) {
      if (m.getIdentity().equals(id)) {
        oldMap = m.getMap();
        break;
      }
    }

    return compareString(Utilities.escapeXml(newMap), Utilities.escapeXml(oldMap), mode);
  }

  private String compareSimpleTypeLists(List original, List compare, int mode) {
    return compareSimpleTypeLists(original, compare, mode, ", ");
  }

  private List<String> convertPrimitiveTypeList(List<PrimitiveType> original) {
    List<String> originalList = new ArrayList<String>();
    for (PrimitiveType pt : original) {
      String s = gt(pt);
      if (!s.startsWith("<"))
        s = Utilities.escapeXml(s);
      originalList.add(s);
    }
    return originalList;
  }

  private String compareSimpleTypeLists(List originalList, List compareList, int mode, String separator) {
    List<String> original;
    List<String> compare;
    if (compareList == null)
      compareList = new ArrayList<>();
    if (!originalList.isEmpty() && originalList.get(0) instanceof PrimitiveType)
      original = convertPrimitiveTypeList(originalList);
    else
      original = originalList;
    if (!compareList.isEmpty() && compareList.get(0) instanceof PrimitiveType)
      compare = convertPrimitiveTypeList(compareList);
    else
      compare = compareList;
    StringBuilder b = new StringBuilder();
    if (mode==GEN_MODE_SNAP || mode==GEN_MODE_MS) {
      boolean first = true;
      for (String s : original) {
        if (first) first = false; else b.append(separator);
        b.append(s);
      }
    } else {
      boolean first = true;
      for (String s : original) {
        if (first) first = false; else b.append(separator);
        if (compare.contains(s))
          b.append(unchanged(s));
        else
          b.append(s);
      }
      if (original.size()!=0 || mode!=GEN_MODE_DIFF) {
        for (String s : compare) {
          if (!original.contains(s)) {
            if (first)
              first = false;
            else
              b.append(separator);
            b.append(removed(s));
          }
        }
      }
    }
    return b.toString();
  }

  public String mappings(boolean complete, boolean diff) {
    if (sd.getMapping().isEmpty())
      return "<p>" + translate("sd.maps", "No Mappings") + "</p>";
    else {
      boolean allEmpty = true;  // assume all the mappings are empty; 
      StringBuilder s = new StringBuilder();
      for (StructureDefinitionMappingComponent map : sd.getMapping()) {

        // Go check all the mappings have at least one Map or Comment defined (we want to suppress completely empty mappings)
        boolean hasComments = false; boolean hasMaps = false;
        String path = null;
        for (ElementDefinition e : diff ? sd.getDifferential().getElement() : sd.getSnapshot().getElement()) {
          if (path == null || !e.getPath().startsWith(path)) {
            path = null;
            if (e.hasMax() && e.getMax().equals("0") || !(complete || hasMappings(e, map))) {
              path = e.getPath() + ".";
            } else
              hasComments = checkGenElementComments(e, map.getIdentity()) || hasComments;
              hasMaps = checkGenElementMaps(e, map.getIdentity()) || hasMaps;
          }  
        }
        
        // Don't include empty mappings...
        if(hasMaps || hasComments) {
          allEmpty = false; // that assumption is wrong
          String url = getUrlForUri(map.getUri());
          if (url == null)
            s.append("<a name=\"" + map.getIdentity() + "\"> </a><h3>" + translate("sd.maps", "Mappings for %s (%s)", Utilities.escapeXml(gt(map.getNameElement())), Utilities.escapeXml(map.getUri())) + "</h3>");
          else
            s.append("<a name=\"" + map.getIdentity() + "\"> </a><h3>" + translate("sd.maps", "Mappings for %s (<a href=\"" + Utilities.escapeXml(url) + "\">%s</a>)", Utilities.escapeXml(gt(map.getNameElement())), Utilities.escapeXml(map.getUri())) + "</h3>");
          if (map.hasComment())
            s.append("<p>" + Utilities.escapeXml(gt(map.getCommentElement())) + "</p>");
          //        else if (specmaps != null && preambles.has(map.getUri()))   
          //          s.append(preambles.get(map.getUri()).getAsString()); 

          s.append("<table class=\"grid\">\r\n");
          s.append(" <tr><td colspan=\"3\"><b>" + Utilities.escapeXml(gt(sd.getNameElement())) + "</b></td></tr>\r\n");
          path = null;
          for (ElementDefinition e : diff ? sd.getDifferential().getElement() : sd.getSnapshot().getElement()) {
            if (path == null || !e.getPath().startsWith(path)) {
              path = null;
              if (e.hasMax() && e.getMax().equals("0") || !(complete || hasMappings(e, map))) {
                path = e.getPath() + ".";
              } else
                genElement(s, e, map.getIdentity(), hasComments);
            }
          }
          s.append("</table>\r\n");
        }
      }

      // Well all the mappings are empty
      if(allEmpty) {
        s.append("<p>" + translate("sd.maps", "All Mappings are Empty" + "</p>"));
      }

      return s.toString();
    }
  }

  private String getUrlForUri(String uri) {
    if (Utilities.existsInList(uri, "http://clinicaltrials.gov",
        "http://github.com/MDMI/ReferentIndexContent",
        "http://loinc.org",
        "http://metadata-standards.org/11179/",
        "http://openehr.org",
        "http://snomed.info/sct",
        "http://wiki.ihe.net/index.php?title=Data_Element_Exchange",
        "http://www.cda-adc.ca/en/services/cdanet/",
        "http://www.cdisc.org/define-xml",
        "http://www.fda.gov/MedicalDevices/DeviceRegulationandGuidance/UniqueDeviceIdentification/default.htm",
        "http://www.hl7.org/implement/standards/product_brief.cfm?product_id=378",
        "http://www.ietf.org/rfc/rfc2445.txt",
        "http://www.omg.org/spec/ServD/1.0/",
        "http://www.pharmacists.ca/",
        "http://www.w3.org/ns/prov"))
      return uri;
    if ("http://hl7.org/fhir/auditevent".equals(uri)) return "http://hl7.org/fhir/auditevent.html";
    if ("http://hl7.org/fhir/provenance".equals(uri)) return "http://hl7.org/fhir/provenance.html";
    if ("http://hl7.org/fhir/w5".equals(uri)) return "http://hl7.org/fhir/w5.html";
    if ("http://hl7.org/fhir/workflow".equals(uri)) return "http://hl7.org/fhir/workflow.html";
    if ("http://hl7.org/fhir/interface".equals(uri)) return "http://hl7.org/fhir/patterns.html";
    if ("http://hl7.org/v2".equals(uri)) return "http://hl7.org/comparison-v2.html";
    if ("http://hl7.org/v3".equals(uri)) return "http://hl7.org/comparison-v3.html";
    if ("http://hl7.org/v3/cda".equals(uri)) return "http://hl7.org/comparison-cda.html";
    return null;
  }

  private boolean hasMappings(ElementDefinition e, StructureDefinitionMappingComponent map) {
    List<ElementDefinitionMappingComponent> ml = getMap(e, map.getIdentity());
    if (!ml.isEmpty())
      return true;
    int i = sd.getSnapshot().getElement().indexOf(e) + 1;
    while (i < sd.getSnapshot().getElement().size()) {
      ElementDefinition t = sd.getSnapshot().getElement().get(i);
      if (t.getPath().startsWith(e.getPath() + ".")) {
        ml = getMap(t, map.getIdentity());
        if (!ml.isEmpty())
          return true;
      }
      i++;
    }
    return false;
  }

  private boolean checkGenElementMaps(ElementDefinition e, String id) {
    List<ElementDefinitionMappingComponent> ml = getMap(e, id);
    for (ElementDefinitionMappingComponent m : ml) {
      if (m.hasMap()) {
        return true;
      }
    }
    return false;
  }
  private boolean checkGenElementComments(ElementDefinition e, String id) {
    List<ElementDefinitionMappingComponent> ml = getMap(e, id);
    for (ElementDefinitionMappingComponent m : ml) {
      if (m.hasComment()) {
        return true;
      }
    }
    return false;
  }
  private void genElement(StringBuilder s, ElementDefinition e, String id, boolean comments) {
    s.append(" <tr><td>");
    boolean root = true;
    for (char c : e.getPath().toCharArray())
      if (c == '.') {
        s.append("&nbsp;");
        s.append("&nbsp;");
        s.append("&nbsp;");
        root = false;
      }
    if (root)
      s.append(e.getPath());
    else
      s.append(tail(e.getPath()));
    if (e.hasSliceName()) {
      s.append(" (");
      s.append(Utilities.escapeXml(e.getSliceName()));
      s.append(")");
    }
    s.append("</td>");
    List<ElementDefinitionMappingComponent> ml = getMap(e, id);
    if (ml.isEmpty())
      s.append("<td></td>");
    else {
      s.append("<td>");
      boolean first = true;
      for (ElementDefinitionMappingComponent m : ml) {
        if (first) first = false;
        else s.append(", ");
        s.append(Utilities.escapeXml(m.getMap()));
      }
      s.append("</td>");
    }
    if (comments) {
      if (ml.isEmpty())
        s.append("<td></td>");
      else {
        s.append("<td>");
        boolean first = true;
        for (ElementDefinitionMappingComponent m : ml) {
          if (first) first = false;
          else s.append(", ");
//          s.append(Utilities.escapeXml(m.getComment()));
            s.append(processMarkdown("map.comment", m.getComment()));
        }
        s.append("</td>");
      }
    }

    s.append(" </tr>\r\n");
  }


  private List<ElementDefinitionMappingComponent> getMap(ElementDefinition e, String id) {
    List<ElementDefinitionMappingComponent> res = new ArrayList<>();
    for (ElementDefinitionMappingComponent m : e.getMapping()) {
      if (m.getIdentity().equals(id))
        res.add(m);
    }
    return res;
  }

  public String header() throws Exception {
    StringBuilder b = new StringBuilder();
    b.append("<p>\r\n");
    b.append(translate("sd.header", "The official URL for this profile is:") + "\r\n");
    b.append("</p>\r\n");
    b.append("<pre>" + sd.getUrl() + "</pre>\r\n");
    b.append("<p>\r\n");
    b.append(processMarkdown("description", sd.getDescriptionElement()));
    b.append("</p>\r\n");
    if (sd.getDerivation() == TypeDerivationRule.CONSTRAINT) {
      b.append("<p>\r\n");
      StructureDefinition sdb = context.fetchResource(StructureDefinition.class, sd.getBaseDefinition());
      if (sdb != null)
        b.append(translate("sd.header", "This profile builds on") + " <a href=\"" + Utilities.escapeXml(sdb.getWebPath()) + "\">" + gt(sdb.getNameElement()) + "</a>.");
      else
        b.append(translate("sd.header", "This profile builds on") + " " + sd.getBaseDefinition() + ".");
      b.append("</p>\r\n");
    }
    b.append("<p>\r\n");
    b.append(translate("sd.header", "This profile was published on %s as a %s by %s.\r\n", renderDate(sd.getDateElement()), egt(sd.getStatusElement()), gt(sd.getPublisherElement())));
    b.append("</p>\r\n");
    return b.toString();
  }

  private String renderDate(DateTimeType date) {
    return new DataRenderer(gen).display(date);
  }

  public String uses() throws Exception {
    StringBuilder b = new StringBuilder();
    List<StructureDefinition> derived = findDerived();
    if (!derived.isEmpty()) {
      b.append("<p>\r\n");
      b.append(translate("sd.header", "In this IG, the following structures are derived from this profile: "));
      listResources(b, derived);
      b.append("</p>\r\n");
    }
    List<StructureDefinition> users = findUses();
    if (!users.isEmpty()) {
      b.append("<p>\r\n");
      b.append(translate("sd.header", "In this IG, the following structures refer to this profile: "));
      listResources(b, users);
      b.append("</p>\r\n");
    }
    return b.toString();
  }


  private List<StructureDefinition> findDerived() {
    List<StructureDefinition> res = new ArrayList<>();
    for (StructureDefinition t : context.fetchResourcesByType(StructureDefinition.class)) {
      if (sd.getUrl().equals(t.getBaseDefinition())) {
        res.add(t);
      }
    }
    return res;
  }

  private List<StructureDefinition> findUses() {
    List<StructureDefinition> res = new ArrayList<>();
    for (StructureDefinition t : context.fetchResourcesByType(StructureDefinition.class)) {
      boolean uses = false;
      for (ElementDefinition ed : t.getDifferential().getElement()) {
        for (TypeRefComponent u : ed.getType()) {
          if (u.hasProfile(sd.getUrl())) {
            uses = true;
          }
        }
      }
      if (uses) {
        res.add(t);
      }
    }
    return res;
  }


  public String exampleList(List<FetchedFile> fileList, boolean statedOnly) {
    StringBuilder b = new StringBuilder();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (!r.fhirType().equals("ImplementationGuide")) {
          for (String p : r.getProfiles(statedOnly)) {
            if (sd.getUrl().equals(p)) {
              String name = r.getTitle();
              if (Utilities.noString(name))
                name = "example";
              String ref = igp.getLinkFor(r, true);
              b.append(" <li><a href=\"" + Utilities.escapeXml(ref) + "\">" + Utilities.escapeXml(name) + "</a></li>\r\n");
            }
          }
        }
      }
    }
    return b.toString();
  }

  public String exampleTable(List<FetchedFile> fileList, boolean statedOnly) {
    StringBuilder b = new StringBuilder();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (!r.fhirType().equals("ImplementationGuide")) {
          for (String p : r.getProfiles(statedOnly)) {
            if (sd.getUrl().equals(p)) {
              String name = r.fhirType() + "/" + r.getId();
              String title = r.getTitle();
              if (Utilities.noString(title))
                name = "example";
              if (f.getTitle() != null && f.getTitle() != f.getName())
                title = f.getTitle();
              String ref = igp.getLinkFor(r, true);
              b.append(" <tr>\r\n");
              b.append("   <td><a href=\"" + Utilities.escapeXml(ref) + "\">" + Utilities.escapeXml(name) + "</a></td>\r\n");
              b.append("   <td>" + Utilities.escapeXml(title) + "</td>\r\n");
              b.append(" </tr>\r\n");
            }
          }
        }
      }
    }
    return b.toString();
  }

  public String testplanList(List<FetchedFile> fileList) {
    StringBuilder b = new StringBuilder();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("TestPlan")) {
  	      for (String p : r.getTestArtifacts()) {
            if (sd.getUrl().equals(p)) {
              String name = r.getTitle();
              if (Utilities.noString(name))
                name = "TestPlan";
              String ref = igp.getLinkFor(r, true);
              b.append(" <li><a href=\"" + Utilities.escapeXml(ref) + "\">" + Utilities.escapeXml(name) + "</a></li>\r\n");
            }
          }
        }
      }
    }
    return b.toString();
  }

  public String testplanTable(List<FetchedFile> fileList) {
    StringBuilder b = new StringBuilder();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("TestPlan")) {
          for (String p : r.getTestArtifacts()) {
            if (sd.getUrl().equals(p)) {
              String name = r.fhirType() + "/" + r.getId();
              String title = r.getTitle();
              if (Utilities.noString(title))
                name = "TestPlan";
              if (f.getTitle() != null && f.getTitle() != f.getName())
                title = f.getTitle();
              String ref = igp.getLinkFor(r, true);
              b.append(" <tr>\r\n");
              b.append("   <td><a href=\"" + Utilities.escapeXml(ref) + "\">" + Utilities.escapeXml(name) + "</a></td>\r\n");
              b.append("   <td>" + Utilities.escapeXml(title) + "</td>\r\n");
              b.append(" </tr>\r\n");
            }
          }
        }
      }
    }
    return b.toString();
  }

  public String testscriptList(List<FetchedFile> fileList) {
    StringBuilder b = new StringBuilder();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("TestScript")) {
  	      for (String p : r.getTestArtifacts()) {
            if (sd.getUrl().equals(p)) {
              String name = r.getTitle();
              if (Utilities.noString(name))
                name = "TestScript";
              String ref = igp.getLinkFor(r, true);
              b.append(" <li><a href=\"" + Utilities.escapeXml(ref) + "\">" + Utilities.escapeXml(name) + "</a></li>\r\n");
            }
          }
        }
      }
    }
    return b.toString();
  }

  public String testscriptTable(List<FetchedFile> fileList) {
    StringBuilder b = new StringBuilder();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("TestScript")) {
          for (String p : r.getTestArtifacts()) {
            if (sd.getUrl().equals(p)) {
              String name = r.fhirType() + "/" + r.getId();
              String title = r.getTitle();
              if (Utilities.noString(title))
                name = "TestScript";
              if (f.getTitle() != null && f.getTitle() != f.getName())
                title = f.getTitle();
              String ref = igp.getLinkFor(r, true);
              b.append(" <tr>\r\n");
              b.append("   <td><a href=\"" + Utilities.escapeXml(ref) + "\">" + Utilities.escapeXml(name) + "</a></td>\r\n");
              b.append("   <td>" + Utilities.escapeXml(title) + "</td>\r\n");
              b.append(" </tr>\r\n");
            }
          }
        }
      }
    }
    return b.toString();
  }

  public String span(boolean onlyConstraints, String canonical, Set<String> outputTracker) throws IOException, FHIRException {
    return new XhtmlComposer(XhtmlComposer.HTML).compose(sdr.generateSpanningTable(sd, destDir, onlyConstraints, canonical, outputTracker));
  }

  public String pseudoJson() throws Exception {
    if (sd.getSnapshot() == null || sd.getSnapshot().getElement() == null || sd.getSnapshot().getElement().size() == 0) {
      return "";
    }
    StringBuilder b = new StringBuilder();
    String rn = sd.getSnapshot().getElement().get(0).getPath();
    b.append(" // <span style=\"color: navy; opacity: 0.8\">" + Utilities.escapeXml(sd.getTitle()) + "</span>\r\n {\r\n");
    if (sd.getKind() == StructureDefinitionKind.RESOURCE)
      b.append("   \"resourceType\" : \"" + sd.getType() + "\",\r\n");

    List<ElementDefinition> children = getChildren(sd.getSnapshot().getElement(), sd.getSnapshot().getElement().get(0));
    boolean complex = isComplex(children);
    if (!complex && !hasExtensionChild(children))
      b.append("  // from Element: <a href=\"" + corePath + "extensibility.html\">extension</a>\r\n");

    int c = 0;
    int l = lastChild(children);
    boolean extDone = false; // todo: investigate why getChildren is returning slices...
    for (ElementDefinition child : children)
      if (isExtension(child)) {
        if (!extDone)
          generateCoreElemExtension(b, sd.getSnapshot().getElement(), child, children, 2, rn, false, child.getType().get(0), ++c == l, complex);
        extDone = true;
      } else if (child.hasSlicing())
        generateCoreElemSliced(b, sd.getSnapshot().getElement(), child, children, 2, rn, false, child.getType().get(0), ++c == l, complex);
      else if (wasSliced(child, children))
        ; // nothing
        else if (child.getType().size() == 1 || allTypesAreReference(child))
          generateCoreElem(b, sd.getSnapshot().getElement(), child, 2, rn, false, child.getType().get(0), ++c == l, complex);
        else {
          if (!"0".equals(child.getMax())) {
            b.append("<span style=\"color: Gray\">// " + tail(child.getPath()) + ": <span style=\"color: navy; opacity: 0.8\">" + Utilities.escapeXml(child.getShort()) + "</span>. One of these " + Integer.toString(child.getType().size()) + ":</span>\r\n");
            for (TypeRefComponent t : child.getType())
              generateCoreElem(b, sd.getSnapshot().getElement(), child, 2, rn, false, t, ++c == l, false);
          }
        }
    b.append("  }\r\n");
    return b.toString();
  }

  private boolean allTypesAreReference(ElementDefinition child) {
    for (TypeRefComponent tr : child.getType()) {
      if (!"Reference".equals(tr.getWorkingCode()))
        return false;
    }
    return !child.getType().isEmpty();
  }

  private boolean isExtension(ElementDefinition child) {
    return child.getPath().endsWith(".extension") || child.getPath().endsWith(".modifierExtension");
  }

  private boolean hasExtensionChild(List<ElementDefinition> children) {
    for (ElementDefinition ed : children)
      if (ed.getPath().endsWith(".extension"))
        return true;
    return false;
  }

  private List<ElementDefinition> getChildren(List<ElementDefinition> elements, ElementDefinition elem) {
    int i = elements.indexOf(elem) + 1;
    List<ElementDefinition> res = new ArrayList<ElementDefinition>();
    while (i < elements.size()) {
      if (elements.get(i).getPath().startsWith(elem.getPath() + ".")) {
        String tgt = elements.get(i).getPath();
        String src = elem.getPath();
        if (tgt.startsWith(src + ".")) {
          if (!tgt.substring(src.length() + 1).contains("."))
            res.add(elements.get(i));
        }
      } else
        return res;
      i++;
    }
    return res;
  }

  private boolean isComplex(List<ElementDefinition> children) {
    int c = 0;
    for (ElementDefinition child : children) {
      if (child.getPath().equals("Extension.extension"))
        c++;
    }
    return c > 1;
  }

  private int lastChild(List<ElementDefinition> extchildren) {
    int l = extchildren.size();
    while (l > 0 && "0".equals(extchildren.get(l - 1).getMax()))
      l--;
    return l;
  }

  @SuppressWarnings("rawtypes")
  private void generateCoreElem(StringBuilder b, List<ElementDefinition> elements, ElementDefinition elem, int indent, String pathName, boolean asValue, TypeRefComponent type, boolean last, boolean complex) throws Exception {
    if (elem.getPath().endsWith(".id") && elem.getPath().lastIndexOf('.') > elem.getPath().indexOf('.'))
      return;
    if (!complex && elem.getPath().endsWith(".extension"))
      return;

    if ("0".equals(elem.getMax()))
      return;

    String indentS = "";
    for (int i = 0; i < indent; i++) {
      indentS += "  ";
    }
    b.append(indentS);


    List<ElementDefinition> children = getChildren(elements, elem);
    String name = tail(elem.getPath());
    String en = asValue ? "value[x]" : name;
    if (en.contains("[x]"))
      en = en.replace("[x]", upFirst(type.getWorkingCode()));
    boolean unbounded = elem.hasBase() && elem.getBase().hasMax() ? elem.getBase().getMax().equals("*") : "*".equals(elem.getMax());
    String defPage = igp.getLinkForProfile(sd, sd.getUrl());
    // 1. name
    if (defPage.contains("|"))
      defPage = defPage.substring(0, defPage.indexOf("|"));
    b.append("\"<a href=\"" + (defPage + "#" + pathName + "." + en) + "\" title=\"" + Utilities.escapeXml(getEnhancedDefinition(elem))
    + "\" class=\"dict\"><span style=\"text-decoration: underline\">" + en + "</span></a>\" : ");

    // 2. value
    boolean delayedClose = false;
    if (unbounded)
      b.append("[");

    if (type == null || children.size() > 0) {
      // inline definition
      assert (children.size() > 0);
      b.append("{");
      delayedClose = true;
    } else if (type.getWorkingCode() == null) {
      // For the 'value' element of simple types
      b.append("&lt;<span style=\"color: darkgreen\">n/a</span>&gt;");
    } else if (isPrimitive(type.getWorkingCode())) {
      if (!(type.getWorkingCode().equals("integer") || type.getWorkingCode().equals("boolean") || type.getWorkingCode().equals("decimal")))
        b.append("\"");
      if (elem.hasFixed())
        b.append(Utilities.escapeJson(((PrimitiveType) elem.getFixed()).asStringValue()));
      else {
        String l = getSrcFile(type.getWorkingCode());
        if (l == null)
          b.append("&lt;<span style=\"color: darkgreen\">" + type.getWorkingCode() + "</span>&gt;");
        else
          b.append("&lt;<span style=\"color: darkgreen\"><a href=\"" + suffix(l, type.getWorkingCode()) + "\">" + type.getWorkingCode() + "</a></span>&gt;");
      }
      if (!(type.getWorkingCode().equals("integer") || type.getWorkingCode().equals("boolean") || type.getWorkingCode().equals("decimal")))
        b.append("\"");
    } else {
      b.append("{");
      b.append("<span style=\"color: darkgreen\"><a href=\"" + Utilities.escapeXml(suffix(getSrcFile(type.getWorkingCode()), type.getWorkingCode())) + "\">" + type.getWorkingCode() + "</a></span>");
      if (type.hasProfile()) {
        StructureDefinition tsd = context.fetchResource(StructureDefinition.class, type.getProfile().get(0).getValue());
        if (tsd != null)
          b.append(" (as <span style=\"color: darkgreen\"><a href=\"" + Utilities.escapeXml(tsd.getWebPath()) + "#" + tsd.getType() + "\">" + tsd.getName() + "</a></span>)");
        else
          b.append(" (as <span style=\"color: darkgreen\">" + type.getProfile() + "</span>)");
      }
      if (type.hasTargetProfile()) {
        if (type.getTargetProfile().get(0).getValue().startsWith("http://hl7.org/fhir/StructureDefinition/")) {
          String t = type.getTargetProfile().get(0).getValue().substring(40);
          if (hasType(t))
            b.append("(<span style=\"color: darkgreen\"><a href=\"" + Utilities.escapeXml(suffix(getSrcFile(t), t)) + "\">" + t + "</a></span>)");
          else if (hasResource(t))
            b.append("(<span style=\"color: darkgreen\"><a href=\"" + Utilities.escapeXml(corePath + t.toLowerCase()) + ".html\">" + t + "</a></span>)");
          else
            b.append("(" + t + ")");
        } else
          b.append("(" + type.getTargetProfile() + ")");
      }
      b.append("}");
    }

    if (!delayedClose) {
      if (unbounded) {
        b.append("]");
      }
      if (!last)
        b.append(",");
    }

    b.append(" <span style=\"color: Gray\">//</span>");

    // 3. optionality
    writeCardinality(unbounded, b, elem);

    // 4. doco
    if (!elem.hasFixed()) {
      if (elem.hasBinding() && elem.getBinding().hasValueSet()) {
        ValueSet vs = context.fetchResource(ValueSet.class, elem.getBinding().getValueSet());
        if (vs != null)
          b.append(" <span style=\"color: navy; opacity: 0.8\"><a href=\"" + corePath + vs.getUserData("filename") + ".html\" style=\"color: navy\">" + Utilities.escapeXml(elem.getShort()) + "</a></span>");
        else
          b.append(" <span style=\"color: navy; opacity: 0.8\"><a href=\"" + elem.getBinding().getValueSet() + ".html\" style=\"color: navy\">" + Utilities.escapeXml(elem.getShort()) + "</a></span>");
      } else
        b.append(" <span style=\"color: navy; opacity: 0.8\">" + Utilities.escapeXml(elem.getShort()) + "</span>");
    }

    b.append("\r\n");

    if (delayedClose) {
      int c = 0;
      int l = lastChild(children);
      boolean extDone = false;
      for (ElementDefinition child : children) {
        if (isExtension(child)) {
          if (!extDone)
            generateCoreElemExtension(b, sd.getSnapshot().getElement(), child, children, indent + 1, pathName + "." + name, false, child.getType().get(0), ++c == l, complex);
          extDone = true;
        } else if (child.hasSlicing())
          generateCoreElemSliced(b, sd.getSnapshot().getElement(), child, children, indent + 1, pathName + "." + name, false, child.hasType() ? child.getType().get(0) : null, ++c == l, complex);
        else if (wasSliced(child, children))
          ; // nothing
        else if (child.getType().size() == 1 || allTypesAreReference(child))
          generateCoreElem(b, elements, child, indent + 1, pathName + "." + name, false, child.getType().get(0), ++c == l, false);
        else {
          if (!"0".equals(child.getMax())) {
            b.append("<span style=\"color: Gray\">// value[x]: <span style=\"color: navy; opacity: 0.8\">" + Utilities.escapeXml(child.getShort()) + "</span>. One of these " + Integer.toString(child.getType().size()) + ":</span>\r\n");
            for (TypeRefComponent t : child.getType())
              generateCoreElem(b, elements, child, indent + 1, pathName + "." + name, false, t, ++c == l, false);
          }
        }
      }
      b.append(indentS);
      b.append("}");
      if (unbounded)
        b.append("]");
      if (!last)
        b.append(",");
      b.append("\r\n");
    }
  }

  private String suffix(String link, String suffix) {
    if (link.contains("|"))
      link = link.substring(0, link.indexOf("|"));
    if (link.contains("#"))
      return link;
    else
      return link + "#" + suffix;
  }

  private void generateCoreElemSliced(StringBuilder b, List<ElementDefinition> elements, ElementDefinition elem, List<ElementDefinition> children, int indent, String pathName, boolean asValue, TypeRefComponent type, boolean last, boolean complex) throws Exception {
    if (elem.getMax().equals("0"))
      return;

    String name = tail(elem.getPath());
    String en = asValue ? "value[x]" : name;
    if (en.contains("[x]"))
      en = en.replace("[x]", upFirst(type.getWorkingCode()));
    boolean unbounded = elem.hasMax() && elem.getMax().equals("*");

    String indentS = "";
    for (int i = 0; i < indent; i++) {
      indentS += "  ";
    }
    String defPage = igp.getLinkForProfile(sd, sd.getUrl());
    if (defPage.contains("|"))
      defPage = defPage.substring(0, defPage.indexOf("|"));
    b.append(indentS);
    b.append("\"<a href=\"" + (defPage + "#" + pathName + "." + en) + "\" title=\"" + Utilities.escapeXml(getEnhancedDefinition(elem))
    + "\" class=\"dict\"><span style=\"text-decoration: underline\">" + en + "</span></a>\" : ");
    b.append("[ <span style=\"color: navy\">" + describeSlicing(elem.getSlicing()) + "</span>");
    //    b.append(" <span style=\"color: Gray\">//</span>");
    //    writeCardinality(elem);
    b.append("\r\n");

    List<ElementDefinition> slices = getSlices(elem, children);
    int c = 0;
    for (ElementDefinition slice : slices) {
      b.append(indentS + "  ");
      b.append("{ // <span style=\"color: navy; opacity: 0.8\">" + Utilities.escapeXml(slice.getShort()) + "</span>");
      writeCardinality(unbounded, b, slice);
      b.append("\r\n");

      List<ElementDefinition> extchildren = getChildren(elements, slice);
      boolean extcomplex = isComplex(extchildren) && complex;
      if (!extcomplex && !hasExtensionChild(extchildren)) {
        b.append(indentS + "  ");
        b.append("  // from Element: <a href=\"" + corePath + "extensibility.html\">extension</a>\r\n");
      }

      int cc = 0;
      int l = lastChild(extchildren);
      for (ElementDefinition child : extchildren)
        if (child.hasSlicing())
          generateCoreElemSliced(b, elements, child, children, indent + 2, pathName + "." + en, false, child.getType().isEmpty() ? null : child.getType().get(0), ++cc == l, extcomplex);
        else if (wasSliced(child, children))
          ; // nothing
        else if (child.getType().size() == 1)
          generateCoreElem(b, elements, child, indent + 2, pathName + "." + en, false, child.getType().get(0), ++cc == l, extcomplex);
        else {
          b.append("<span style=\"color: Gray\">// value[x]: <span style=\"color: navy; opacity: 0.8\">" + Utilities.escapeXml(child.getShort()) + "</span>. One of these " + Integer.toString(child.getType().size()) + ":</span>\r\n");
          for (TypeRefComponent t : child.getType())
            generateCoreElem(b, elements, child, indent + 2, pathName + "." + en, false, t, ++cc == l, false);
        }
      c++;
      b.append(indentS);
      if (c == slices.size())
        b.append("  }\r\n");
      else
        b.append("  },\r\n");

    }
    b.append(indentS);
    if (last)
      b.append("]\r\n");
    else
      b.append("],\r\n");
  }

  private void generateCoreElemExtension(StringBuilder b, List<ElementDefinition> elements, ElementDefinition elem, List<ElementDefinition> children, int indent, String pathName, boolean asValue, TypeRefComponent type, boolean last, boolean complex) throws Exception {
    if (elem.getMax().equals("0"))
      return;

    String name = tail(elem.getPath());
    String en = asValue ? "value[x]" : name;
    if (en.contains("[x]"))
      en = en.replace("[x]", upFirst(type.getWorkingCode()));
    boolean unbounded = elem.hasMax() && elem.getMax().equals("*");

    String indentS = "";
    for (int i = 0; i < indent; i++) {
      indentS += "  ";
    }
    //    String defPage = igp.getLinkForProfile(sd, sd.getUrl());
    //    b.append(indentS);
    //    b.append("\"<a href=\"" + (defPage + "#" + pathName + "." + en)+ "\" title=\"" + Utilities .escapeXml(getEnhancedDefinition(elem)) 
    //    + "\" class=\"dict\"><span style=\"text-decoration: underline\">"+en+"</span></a>\" : ");
    //    b.append("[ <span style=\"color: navy\">"+describeSlicing(elem.getSlicing())+"</span>");
    ////    b.append(" <span style=\"color: Gray\">//</span>");
    ////    writeCardinality(elem);
    //    b.append("\r\n");
    //    
    b.append(indentS + "\"extension\": [\r\n");
    List<ElementDefinition> slices = getSlices(elem, children);
    int c = 0;
    for (ElementDefinition slice : slices) {
      List<CanonicalType> profiles = slice.getTypeFirstRep().getProfile();
      // Won't have a profile if this slice is part of a complex extension
      String url = profiles.isEmpty() ? null : profiles.get(0).getValue();
      StructureDefinition sdExt = url == null ? null : context.fetchResource(StructureDefinition.class, url);
      b.append(indentS + "  ");
      b.append("{ // <span style=\"color: navy; opacity: 0.8\">");
      writeCardinality(unbounded, b, slice);
      b.append(Utilities.escapeXml(slice.getShort()) + "</span>");
      b.append("\r\n");
      b.append(indentS + "    ");
      if (sdExt == null)
        b.append("\"url\": \"" + url + "\",\r\n");
      else
        b.append("\"url\": \"<a href=\"" + sdExt.getWebPath() + "\">" + url + "</a>\",\r\n");

      List<ElementDefinition> extchildren = getChildren(elements, slice);
      if (extchildren.isEmpty()) {
        if (sdExt == null)
          b.append("Not handled yet: unknown extension " + url + "\r\n");
        else
          extchildren = getChildren(sdExt.getSnapshot().getElement(), sdExt.getSnapshot().getElementFirstRep());
      }

      ElementDefinition value = getValue(extchildren);
      if (value != null) {
        if (value.getType().size() == 1)
          generateCoreElem(b, elements, value, indent + 2, pathName + "." + en, false, value.getType().get(0), true, false);
        else {
          b.append("<span style=\"color: Gray\">// value[x]: <span style=\"color: navy; opacity: 0.8\">" + Utilities.escapeXml(value.getShort()) + "</span>. One of these " + Integer.toString(value.getType().size()) + ":</span>\r\n");
          for (TypeRefComponent t : value.getType())
            generateCoreElem(b, elements, value, indent + 2, pathName + "." + en, false, t, t == value.getType().get(value.getType().size() - 1), false);
        }
      } else {
        b.append("Not handled yet: complex extension " + url + "\r\n");
      }

      c++;
      b.append(indentS);
      if (c == slices.size())
        b.append("  }\r\n");
      else
        b.append("  },\r\n");

    }
    b.append(indentS);
    if (last)
      b.append("]\r\n");
    else
      b.append("],\r\n");
  }


  private ElementDefinition getValue(List<ElementDefinition> extchildren) {
    for (ElementDefinition ed : extchildren)
      if (ed.getPath().contains(".value") && !"0".equals(ed.getMax()))
        return ed;
    return null;
  }

  private boolean hasResource(String code) {
    StructureDefinition sd = context.fetchTypeDefinition(code);
    return sd != null && sd.getKind() == StructureDefinitionKind.RESOURCE;
  }

  private boolean hasType(String code) {
    StructureDefinition sd = context.fetchResource(StructureDefinition.class, ProfileUtilities.sdNs(code, null));
    return sd != null && (sd.getKind() == StructureDefinitionKind.PRIMITIVETYPE || sd.getKind() == StructureDefinitionKind.COMPLEXTYPE);
  }

  private String getSrcFile(String code) {
    StructureDefinition sd = context.fetchResource(StructureDefinition.class, ProfileUtilities.sdNs(code, null));
    if (sd == null)
      return "?sd-src?";
    else {
      String l = igp.getLinkForProfile(this.sd, sd.getUrl());
      if (l == null)
        return null;
      if (l.contains("|"))
        l = l.substring(0, l.indexOf("|"));
      return l;
    }
  }

  private boolean isPrimitive(String code) {
    StructureDefinition sd = context.fetchTypeDefinition(code);
    return sd != null && sd.getKind() == StructureDefinitionKind.PRIMITIVETYPE;
  }


  private String upFirst(String s) {
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }

  private void writeCardinality(boolean unbounded, StringBuilder b, ElementDefinition elem) throws IOException {
    if (elem.getConstraint().size() > 0)
      b.append(" <span style=\"color: brown\" title=\""
          + Utilities.escapeXml(getInvariants(elem)) + "\"><b>C?</b></span>");
    if (elem.getMin() > 0)
      b.append(" <span style=\"color: brown\" title=\"This element is required\"><b>R!</b></span>");
    if (unbounded && "1".equals(elem.getMax()))
      b.append(" <span style=\"color: brown\" title=\"This element is an array in the base standard, but the profile only allows on element\"><b>Only One!</b></span> ");
  }

  private String getInvariants(ElementDefinition elem) {
    StringBuilder b = new StringBuilder();
    boolean first = true;
    for (ElementDefinitionConstraintComponent i : elem.getConstraint()) {
      if (!i.hasSource() || i.getSource().equals(sd.getUrl()) || allInvariants) {
        if (!first)
          b.append("; ");
        first = false;
        b.append(i.getKey() + ": " + i.getHuman());
      }
    }

    return b.toString();
  }


  private String getEnhancedDefinition(ElementDefinition elem) {
    if (elem.getIsModifier() && elem.getMustSupport())
      return Utilities.removePeriod(elem.getDefinition()) + " (this element modifies the meaning of other elements, and must be supported)";
    else if (elem.getIsModifier())
      return Utilities.removePeriod(elem.getDefinition()) + " (this element modifies the meaning of other elements)";
    else if (elem.getMustSupport())
      return Utilities.removePeriod(elem.getDefinition()) + " (this element must be supported)";
    else
      return Utilities.removePeriod(elem.getDefinition());
  }

  private boolean wasSliced(ElementDefinition child, List<ElementDefinition> children) {
    String path = child.getPath();
    for (ElementDefinition c : children) {
      if (c == child)
        break;
      if (c.getPath().equals(path) && c.hasSlicing())
        return true;
    }
    return false;
  }


  private List<ElementDefinition> getSlices(ElementDefinition elem, List<ElementDefinition> children) {
    List<ElementDefinition> slices = new ArrayList<ElementDefinition>();
    for (ElementDefinition child : children) {
      if (child != elem && child.getPath().equals(elem.getPath()))
        slices.add(child);
    }
    return slices;
  }

  private String describeSlicing(ElementDefinitionSlicingComponent slicing) {
    if (slicing.getRules() == SlicingRules.CLOSED)
      return "";
    CommaSeparatedStringBuilder csv = new CommaSeparatedStringBuilder();
    for (ElementDefinitionSlicingDiscriminatorComponent d : slicing.getDiscriminator()) {
      csv.append(d.getType().toCode() + ":" + d.getPath());
    }
    String s = slicing.getOrdered() ? " in any order" : " in the specified order " + (slicing.hasRules() ? slicing.getRules().getDisplay() : "");
    return "// sliced by " + csv.toString() + " " + s;
  }

  public String references() throws FHIRFormatError, IOException {
    Map<String, String> base = new HashMap<>();
    Map<String, String> invoked = new HashMap<>();
    Map<String, String> refs = new HashMap<>();
    Map<String, String> trefs = new HashMap<>();
    Map<String, String> examples = new HashMap<>();
    for (StructureDefinition sd : context.fetchResourcesByType(StructureDefinition.class)) {
      if (this.sd.getUrl().equals(sd.getBaseDefinition())) {
        base.put(sd.getWebPath(), sd.present());
      }
      for (Extension ext : sd.getExtensionsByUrl(ToolingExtensions.EXT_OBLIGATION_INHERITS)) {
        String v = ext.getValue().primitiveValue();
        if (this.sd.getUrl().equals(v)) {
          invoked.put(sd.getWebPath(), sd.present());
        }
      }
       for (ElementDefinition ed : sd.getDifferential().getElement()) {
        for (TypeRefComponent tr : ed.getType()) {
          for (CanonicalType u : tr.getProfile()) {
            if (this.sd.getUrl().equals(u.getValue())) {
              if (sd.hasWebPath()) {
                refs.put(sd.getWebPath(), sd.present());
              } else {
                System.out.println("SD "+sd.getVersionedUrl()+" has no path");
              }
            }
          }
          for (CanonicalType u : tr.getTargetProfile()) {
            if (this.sd.getUrl().equals(u.getValue())) {
              if (sd.hasWebPath()) {
                trefs.put(sd.getWebPath(), sd.present());
              } else {
                System.out.println("SD "+sd.getVersionedUrl()+" has no path");
              }
            }
          }
        }
      }
    }
    if (VersionUtilities.isR5Plus(context.getVersion())) {
      if (usages == null) {
        FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager(true);
        NpmPackage npm = pcm.loadPackage("hl7.fhir.r5.core");
        usages = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(npm.load("other", "sdmap.details"));
      }
      if (usages.has(sd.getUrl())) {
        for (JsonProperty p : usages.getJsonObject(sd.getUrl()).getProperties()) {
          examples.put(Utilities.pathURL(specPath, p.getName()), p.getValue().asString());
        }
      } 

    }

    for (FetchedFile f : files) {
      for (FetchedResource r : f.getResources()) {
        if (!r.fhirType().equals("ImplementationGuide")) {
          if (usesSD(r.getElement())) {
            String p = igp.getLinkFor(r, true);
            if (p != null) {
              examples.put(p, r.getTitle());
            } else {
              System.out.println("Res "+f.getName()+" has no path");
            }
          }
        }
      }
    }

    StringBuilder b = new StringBuilder();
    String type = sd.describeType();
    if (ToolingExtensions.readBoolExtension(sd, ToolingExtensions.EXT_OBLIGATION_PROFILE_FLAG)) {
      type = "Obligation Profile";
    }
    b.append("<p><b>Usage:</b></p>\r\n<ul>\r\n");
    if (!base.isEmpty())
      b.append(" <li>Derived from this " + type + ": " + refList(base, "base") + "</li>\r\n");
    if (!invoked.isEmpty()) {
      b.append(" <li>Draw in Obligations &amp; Additional Bindings from this " + type + ": " + refList(invoked, "invoked") + "</li>\r\n");
    }
    if (!refs.isEmpty())
      b.append(" <li>Use this " + type + ": " + refList(refs, "ref") + "</li>\r\n");
    if (!trefs.isEmpty())
      b.append(" <li>Refer to this " + type + ": " + refList(trefs, "tref") + "</li>\r\n");
    if (!examples.isEmpty())
      b.append(" <li>Examples for this " + type + ": " + refList(examples, "ex") + "</li>\r\n");
    if (base.isEmpty() && refs.isEmpty() && trefs.isEmpty() && examples.isEmpty() & invoked.isEmpty()) {
      b.append(" <li>This " + type + " is not used by any profiles in this Implementation Guide</li>\r\n");
    }
    b.append("</ul>\r\n");
    return b.toString();
  }
  
  public String typeName() {
    String type = sd.describeType();
    if (ToolingExtensions.readBoolExtension(sd, ToolingExtensions.EXT_OBLIGATION_PROFILE_FLAG)) {
      type = "Obligation Profile";
    }
    return type;
  }

  private boolean usesSD(Element resource) {
    if (resource.hasChild("meta")) {
      Element meta = resource.getNamedChild("meta");
      for (Element p : meta.getChildrenByName("profile")) {
        if (sd.getUrl().equals(p.getValue()))
          return true;
      }
    }
    return usesExtension(resource);
  }

  private boolean usesExtension(Element focus) {
    for (Element child : focus.getChildren()) {
      if (child.getName().equals("extension") && sd.getUrl().equals(child.getChildValue("url")))
        return true;
      if (usesExtension(child))
        return true;
    }
    return false;
  }


  private static final int MAX_DEF_SHOW = 5;
  private String refList(Map<String, String> base, String key) {
    StringBuilder b = new StringBuilder();
    int c = 0;
    for (String s : sorted(base.keySet())) {
      c++;
      if (c == MAX_DEF_SHOW && base.size() > MAX_DEF_SHOW) {
        b.append("<span id=\"rr_"+key+"\" onClick=\"document.getElementById('rr_"+key+"').innerHTML = document.getElementById('rr2_"+key+"').innerHTML\">..."+
            " <span style=\"cursor: pointer; border: 1px grey solid; background-color: #fcdcb3; padding-left: 3px; padding-right: 3px; color: black\">"+
            "Show "+(base.size()-MAX_DEF_SHOW+1)+" more</span></span><span id=\"rr2_"+key+"\" style=\"display: none\">");
      }
      if (c == base.size() && c != 1) {
        b.append(" and ");
      } else if (c > 1) {
        b.append(", ");
      }

      b.append("<a href=\"" + s + "\">" + base.get(s) + "</a>");
    }

    if (c >= MAX_DEF_SHOW && base.size() > MAX_DEF_SHOW) {
      b.append("</span>");
    }
    return b.toString();
  }

  private List<String> sorted(Set<String> keys) {
    List<String> res = new ArrayList<>();
    res.addAll(keys);
    Collections.sort(res);
    return res;
  }

  @Override
  protected void genSummaryRowsSpecific(StringBuilder b, Set<String> rows) {
  }

  public ElementDefinition getElementById(String url, String id) {
    Map<String, ElementDefinition> sdCache = sdMapCache.get(url);

    if (sdCache == null) {
      StructureDefinition sd = (StructureDefinition) context.fetchResource(StructureDefinition.class, url);
      if (sd == null) {
        if (url.equals("http://hl7.org/fhir/StructureDefinition/Base")) {
          sd = (StructureDefinition) context.fetchResource(StructureDefinition.class, "http://hl7.org/fhir/StructureDefinition/Element");                
        }
        if (sd == null) {
          throw new FHIRException("Unable to retrieve StructureDefinition with URL " + url);
        }
      }
      sdCache = new HashMap<String, ElementDefinition>();
      sdMapCache.put(url, sdCache);
      String webroot = sd.getUserString("webroot");
      for (ElementDefinition e : sd.getSnapshot().getElement()) {
        utils.updateURLs(sd.getUrl(), webroot, e);
        sdCache.put(e.getId(), e);
      }
    }
    return sdCache.get(id);
  }


  public String dense(PackageInformation srcInfo) {
    if (srcInfo.getId().startsWith("hl7.fhir")) {
      return srcInfo.getId().substring(9);
    }
    return srcInfo.getId();
  }
  
  public String crumbTrail() {
    CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder(" -&lt; ");
    b.append(sd.getName());
    StructureDefinition t = context.fetchResource(StructureDefinition.class, sd.getBaseDefinition());
    while (t != null) {
      PackageInformation srcInfo = t.getSourcePackage();
      String s = "";
      if (srcInfo != null) {
        s = s + "<a href=\""+srcInfo.getWeb()+"\">"+dense(srcInfo)+"</a>:";
      }
      s = s + "<a href=\""+t.getWebPath()+"\">"+t.getName()+"</a>";
      b.append(s);
      t = t.getDerivation() == TypeDerivationRule.SPECIALIZATION ? null : context.fetchResource(StructureDefinition.class, t.getBaseDefinition());
    }
    return b.toString();
  }

  public String expansion(String definitionsName, Set<String> otherFilesRun) throws IOException {
    PEBuilder pe = context.getProfiledElementBuilder(PEElementPropertiesPolicy.NONE, true);
    HierarchicalTableGenerator gen = new HierarchicalTableGenerator(destDir, true, true);
    gen.setTranslator(getTranslator());
    
    TableModel model = gen.initNormalTable(corePath, false, true, sd.getId()+"x", true, TableGenerationMode.XHTML);
    XhtmlNode x = null;
    try {
      genElement(gen, model.getRows(), pe.buildPEDefinition(sd));
      x = gen.generate(model, destDir, 0, otherFilesRun);
    } catch (Exception e) {
      x = new XhtmlNode(NodeType.Element, "div").tx("Error: "+e.getMessage());
    }
    return new XhtmlComposer(false, false).compose(x);
  }

  private void genElement(HierarchicalTableGenerator gen, List<Row> rows, PEDefinition element) throws FHIRException, IOException {
    Row row = gen.new Row();
    rows.add(row);
    row.setAnchor(element.path());
    row.setColor(element.fixedValue() ? "#eeeeee" : "#ffffff");
    row.setLineColor(0);
    if (element.fixedValue()) {
      row.setIcon("icon_fixed.gif", "Fixed Value" /*HierarchicalTableGenerator.TEXT_ICON_FIXED*/);
    } else {
      switch (element.mode()) {
      case Resource:
        row.setIcon("icon_resource.png", HierarchicalTableGenerator.TEXT_ICON_RESOURCE);
        break;
      case Element:
        row.setIcon("icon_element.gif", HierarchicalTableGenerator.TEXT_ICON_ELEMENT);
        break;
      case DataType:
        row.setIcon("icon_datatype.gif", HierarchicalTableGenerator.TEXT_ICON_DATATYPE);
        break;
      case Extension:
        row.setIcon("icon_extension_simple.png", HierarchicalTableGenerator.TEXT_ICON_EXTENSION_SIMPLE);
        break;
      }
    }
    // name
    Cell gc = gen.new Cell();
    row.getCells().add(gc);
    gc.addText(element.name());
    
    // flags
    gc = gen.new Cell();
    row.getCells().add(gc);
    if (element.definition().getIsModifier()) {
      gc.addStyledText(translate("sd.table", "This element is a modifier element"), "?!", null, null, null, false);
    }
    if (element.definition().getMustSupport() || element.definition().hasExtension(ToolingExtensions.EXT_OBLIGATION)) {
      gc.addStyledText(translate("sd.table", "This element must be supported"), "S", "white", "red", null, false);
    }
    if (element.definition().getIsSummary()) {
      gc.addStyledText(translate("sd.table", "This element is included in summaries"), "\u03A3", null, null, null, false);
    }
    if (sdr.hasNonBaseConstraints(element.definition().getConstraint()) || sdr.hasNonBaseConditions(element.definition().getCondition())) {
      Piece p = gc.addText(org.hl7.fhir.r5.renderers.StructureDefinitionRenderer.CONSTRAINT_CHAR);
      p.setHint(translate("sd.table", "This element has or is affected by constraints ("+sdr.listConstraintsAndConditions(element.definition())+")"));
      p.addStyle(org.hl7.fhir.r5.renderers.StructureDefinitionRenderer.CONSTRAINT_STYLE);
      p.setReference(Utilities.pathURL(VersionUtilities.getSpecUrl(context.getVersion()), "conformance-rules.html#constraints"));
    }
    if (element != null && element.definition().hasExtension(ToolingExtensions.EXT_STANDARDS_STATUS)) {
      StandardsStatus ss = StandardsStatus.fromCode(element.definition().getExtensionString(ToolingExtensions.EXT_STANDARDS_STATUS));
      gc.addStyledText("Standards Status = "+ss.toDisplay(), ss.getAbbrev(), "black", ss.getColor(), context.getSpecUrl()+"versions.html#std-process", true);
    }


    // cardinality
    gc = gen.new Cell();
    row.getCells().add(gc);
    gc.addText(""+element.min()+".."+(element.max() == Integer.MAX_VALUE ? "*" : element.max()));
    
    // type
    gc = gen.new Cell();
    row.getCells().add(gc);
    if (element.types().size() == 1) {
      PEType t = element.types().get(0);
      StructureDefinition sd = context.fetchResource(StructureDefinition.class, t.getUrl());
      if (sd != null) {
        gc.addPiece(gen.new Piece(sd.getWebPath(), t.getName(), t.getType()));        
      } else {
        gc.addPiece(gen.new Piece(null, t.getName(), t.getType()));
      }
    } else if (element.types().size() == 0) {
      // unprofiled primitive type value
      TypeRefComponent tr = element.definition().getTypeFirstRep();
      StructureDefinition sd = context.fetchTypeDefinition(tr.getWorkingCode());
      if (sd != null) {
        gc.addPiece(gen.new Piece(sd.getWebPath(), "("+tr.getWorkingCode()+" value)", "Primitive value "+tr.getWorkingCode()));        
      } else {
        gc.addPiece(gen.new Piece(null, "("+tr.getWorkingCode()+" value)", "Primitive value "+tr.getWorkingCode()));
      }
    } else {
      gc.addText("(multiple)");
      
    }
    
    // description
    sdr.generateDescription(gen, row, element.definition(), null, true, context.getSpecUrl(), null, sd, context.getSpecUrl(), destDir, false, false, allInvariants, true, false, false, sdr.getContext());
    
    if (element.types().size() == 1) {
      PEType t = element.types().get(0);
      for (PEDefinition child : element.children(t.getUrl())) {
        if (child.isProfiled()) {
          genElement(gen, row.getSubRows(), child);
        }
      }
    } else {
      for (PEType t : element.types()) {
        Row trow = gen.new Row();
        row.getSubRows().add(trow);
        trow.setAnchor(element.path()+"-t-"+t.getName());
        trow.setColor(element.fixedValue() ? "#eeeeee" : "#ffffff");
        trow.setLineColor(0);
        trow.setIcon("icon_slice.png", HierarchicalTableGenerator.TEXT_ICON_SLICE);
        gc = gen.new Cell();
        trow.getCells().add(gc);
        gc.addText(t.getName());
        gc = gen.new Cell();
        trow.getCells().add(gc);
        gc = gen.new Cell();
        trow.getCells().add(gc);
        
        // type
        gc = gen.new Cell();
        trow.getCells().add(gc);
        StructureDefinition sd = context.fetchResource(StructureDefinition.class, t.getUrl());
        if (sd != null) {
          gc.addPiece(gen.new Piece(sd.getWebPath(), t.getName(), t.getType()));        
        } else {
          gc.addPiece(gen.new Piece(null, t.getName(), t.getType()));
        }
        gc = gen.new Cell();
        trow.getCells().add(gc);
        for (PEDefinition child : element.children(t.getUrl())) {
          if (child.isProfiled()) {
            genElement(gen, row.getSubRows(), child);
          }
        }
      }
    }
  }
}
