package org.hl7.fhir.igtools.renderers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.convertors.misc.ProfileVersionAdaptor.ConversionMessage;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.igtools.publisher.FetchedResource.AlternativeVersionResource;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.RelatedIG;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.comparison.CanonicalResourceComparer.CanonicalResourceComparison;
import org.hl7.fhir.r5.comparison.CanonicalResourceComparer.ChangeAnalysisState;
import org.hl7.fhir.r5.comparison.VersionComparisonAnnotation;
import org.hl7.fhir.r5.conformance.profile.BindingResolution;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.conformance.profile.SnapshotGenerationPreProcessor;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.CapabilityStatement;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ContactPoint;
import org.hl7.fhir.r5.model.DataType;
import org.hl7.fhir.r5.model.DateTimeType;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionBindingComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionConstraintComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionMappingComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionSlicingComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent;
import org.hl7.fhir.r5.model.ElementDefinition.SlicingRules;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.Enumerations.BindingStrength;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.PackageInformation;
import org.hl7.fhir.r5.model.PrimitiveType;
import org.hl7.fhir.r5.model.Quantity;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.SearchParameter;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionContextComponent;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionMappingComponent;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.profilemodel.PEBuilder;
import org.hl7.fhir.r5.profilemodel.PEBuilder.PEElementPropertiesPolicy;
import org.hl7.fhir.r5.profilemodel.PEDefinition;
import org.hl7.fhir.r5.profilemodel.PEType;
import org.hl7.fhir.r5.renderers.AdditionalBindingsRenderer;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.r5.renderers.Renderer.RenderingStatus;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.StructureDefinitionRendererMode;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities.SystemReference;
import org.hl7.fhir.r5.terminologies.ValueSetUtilities;
import org.hl7.fhir.r5.utils.ElementDefinitionUtilities;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.StandardsStatus;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.i18n.RenderingI18nContext;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.Cell;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.Piece;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.Row;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.TableGenerationMode;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.TableModel;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

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

  public static final int GEN_MODE_SNAP = 1;
  public static final int GEN_MODE_DIFF = 2;
  public static final int GEN_MODE_MS = 3;
  public static final int GEN_MODE_KEY = 4;
  public static final String ANCHOR_PREFIX_SNAP = "";
  public static final String ANCHOR_PREFIX_DIFF = "diff_";
  public static final String ANCHOR_PREFIX_MS = "ms_";
  public static final String ANCHOR_PREFIX_KEY = "key_";

  private static List<String> SIGNIFICANT_EXTENSIONS = new ArrayList<String>(Arrays.asList(new String[]
          {"http://hl7.org/fhir/StructureDefinition/elementdefinition-allowedUnits",
                  "http://hl7.org/fhir/StructureDefinition/elementdefinition-bestPractice",
                  "http://hl7.org/fhir/StructureDefinition/elementdefinition-graphConstraint",
                  "http://hl7.org/fhir/StructureDefinition/elementdefinition-maxDecimalPlaces",
                  "http://hl7.org/fhir/StructureDefinition/elementdefinition-maxSize",
                  "http://hl7.org/fhir/StructureDefinition/elementdefinition-mimeType",
                  "http://hl7.org/fhir/StructureDefinition/elementdefinition-minLength",
                  "http://hl7.org/fhir/StructureDefinition/elementdefinition-obligation"}
  ));
  ProfileUtilities utils;
  private StructureDefinition sd;
  private String destDir;
  private List<FetchedFile> files;
  private boolean allInvariants;
  private HashMap<String, ElementDefinition> differentialHash = null;
  private HashMap<String, ElementDefinition> mustSupportHash = null;
  private Map<String, Map<String, ElementDefinition>> sdMapCache;
  private List<ElementDefinition> diffElements = null;
  private List<ElementDefinition> mustSupportElements = null;
  private List<ElementDefinition> keyElements = null;
  private static JsonObject usages;
  private String specPath;

  private org.hl7.fhir.r5.renderers.StructureDefinitionRenderer sdr;
  private ResourceWrapper resE;

  public StructureDefinitionRenderer(IWorkerContext context, String corePath, StructureDefinition sd, String destDir, IGKnowledgeProvider igp, List<SpecMapManager> maps, Set<String> allTargets, MarkDownProcessor markdownEngine, NpmPackage packge, List<FetchedFile> files, RenderingContext gen, boolean allInvariants,Map<String, Map<String, ElementDefinition>> mapCache, String specPath, String versionToAnnotate, List<RelatedIG> relatedIgs) {
    super(context, corePath, sd, destDir, igp, maps, allTargets, markdownEngine, packge, gen, versionToAnnotate, relatedIgs);
    this.sd = sd;
    this.destDir = destDir;
    utils = new ProfileUtilities(context, null, igp);
    this.files = files;
    this.allInvariants = allInvariants;
    this.sdMapCache = mapCache;
    sdr = new org.hl7.fhir.r5.renderers.StructureDefinitionRenderer(gen);
    sdr.setSdMapCache(sdMapCache);
    sdr.setHostMd(this);
    this.specPath = specPath;
    this.resE = ResourceWrapper.forResource(gen.getContextUtilities(), sd);
  }

  public String summary(boolean all) {
    try {
      if (sd.hasExtension(ToolingExtensions.EXT_SUMMARY)) {
        return processMarkdown("Profile Summary", (PrimitiveType) sd.getExtensionByUrl(ToolingExtensions.EXT_SUMMARY).getValue());
      }

      if (sd.getDifferential() == null)
        return "<p>" + gen.formatPhrase(RenderingContext.STRUC_DEF_NO_SUMMARY) + "</p>";

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
                for (CanonicalType ct : t.getProfile()) {
                  tryAdd(refs, describeProfile(ct.getValue()));
                }
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
      StringBuilder res = new StringBuilder("<a name=\""+(all ? "a" : "s")+"-summary\"> </a>\r\n<p><b>\r\n" + (gen.formatPhrase(RenderingContext.GENERAL_SUMM)) + "\r\n</b></p>\r\n");      
      if (ToolingExtensions.hasExtension(sd, ToolingExtensions.EXT_SUMMARY)) {
        Extension v = ToolingExtensions.getExtension(sd, ToolingExtensions.EXT_SUMMARY);
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
            res.append(gen.formatPhrase(RenderingContext.SD_SUMMARY_MANDATORY, gen.toStr(requiredOutrights), (requiredOutrights > 1 ? (Utilities.pluralizeMe(gen.formatPhrase(RenderingContext.STRUC_DEF_ELEMENT))) : (gen.formatPhrase(RenderingContext.STRUC_DEF_ELEMENT)))));
            if (requiredNesteds > 0)
              res.append(gen.formatPhrase(RenderingContext.SD_SUMMARY_NESTED_MANDATORY, gen.toStr(requiredNesteds), requiredNesteds > 1 ? (Utilities.pluralizeMe(gen.formatPhrase(RenderingContext.STRUC_DEF_ELEMENT))) : (gen.formatPhrase(RenderingContext.STRUC_DEF_ELEMENT))));
          }
          if (supports > 0) {
            if (started)
              res.append("<br/> ");
            started = true;
            res.append(gen.formatPhrase(RenderingContext.SD_SUMMARY_MUST_SUPPORT, gen.toStr(supports), supports > 1 ? (Utilities.pluralizeMe(gen.formatPhrase(RenderingContext.STRUC_DEF_ELEMENT))) : (gen.formatPhrase(RenderingContext.STRUC_DEF_ELEMENT))));
          }
          if (fixeds > 0) {
            if (started)
              res.append("<br/> ");
            started = true;
            res.append(gen.formatPhrase(RenderingContext.SD_SUMMARY_FIXED, gen.toStr(fixeds), fixeds > 1 ? (Utilities.pluralizeMe(gen.formatPhrase(RenderingContext.STRUC_DEF_ELEMENT))) : (gen.formatPhrase(RenderingContext.STRUC_DEF_ELEMENT))));
          }
          if (prohibits > 0) {
            if (started)
              res.append("<br/> ");
            started = true;
            res.append(gen.formatPhrase(RenderingContext.SD_SUMMARY_PROHIBITED, gen.toStr(prohibits), prohibits > 1 ? (Utilities.pluralizeMe(gen.formatPhrase(RenderingContext.STRUC_DEF_ELEMENT))) : (gen.formatPhrase(RenderingContext.STRUC_DEF_ELEMENT))));
          }
          res.append("</p>");
        }

        if (!refs.isEmpty()) {
          res.append("<p><b>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_STRUCTURES)) + "</b></p>\r\n<p>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_THIS_REFERS)) + ":</p>\r\n<ul>\r\n");
          for (String s : refs)
            res.append(s);
          res.append("\r\n</ul>\r\n\r\n");
        }
        if (!ext.isEmpty()) {
          res.append("<p><b>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_EXTENSIONS)) + "</b></p>\r\n<p>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_REFERS_EXT)) + ":</p>\r\n<ul>\r\n");
          for (String s : ext)
            res.append(s);
          res.append("\r\n</ul>\r\n\r\n");
        }
        if (!slices.isEmpty()) {
          res.append("<p><b>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_SLIC)) + "</b></p>\r\n<p>" + gen.formatPhrase(RenderingContext.SD_SUMMARY_SLICES, "<a href=\"" + corePath + "profiling.html#slices\">", "</a>") + ":</p>\r\n<ul>\r\n");
          for (String s : slices)
            res.append(s);
          res.append("\r\n</ul>\r\n\r\n");
        }
      }
      if (ToolingExtensions.hasExtension(sd, ToolingExtensions.EXT_FMM_LEVEL)) {
        // Use hard-coded spec link to point to current spec because DSTU2 had maturity listed on a different page
        res.append("<p><b><a class=\"fmm\" href=\"http://hl7.org/fhir/versions.html#maturity\" title=\"Maturity Level\">" + (gen.formatPhrase(RenderingContext.CANON_REND_MATURITY)) + "</a></b>: " + ToolingExtensions.readStringExtension(sd, ToolingExtensions.EXT_FMM_LEVEL) + "</p>\r\n");
      }

      return res.toString();
    } catch (Exception e) {
      return "<p><i>" + Utilities.escapeXml(e.getMessage()) + "</i></p>";
    }
  }

  private String extensionSummary() {
    if (ProfileUtilities.isSimpleExtension(sd)) {
      ElementDefinition value = sd.getSnapshot().getElementByPath("Extension.value");      
      return "<p>"+
          gen.formatPhrase(RenderingI18nContext.SDR_EXTENSION_SUMMARY, value.typeSummary(), Utilities.stripPara(processMarkdown("ext-desc", sd.getDescriptionElement())))+
          "</p>";
    } else {
      List<ElementDefinition> subs = new ArrayList<>();
      ElementDefinition slice = null;
      for (ElementDefinition ed : sd.getSnapshot().getElement()) {
        if (ed.getPath().endsWith(".extension") && ed.hasSliceName()) {
          slice = ed;
        } else if (ed.getPath().endsWith(".extension.value[x]")) {
          ed.setUserData(UserDataNames.render_extension_slice, slice);
          subs.add(ed);
          slice = null;
        }
      }
      StringBuilder b = new StringBuilder();
      String html = Utilities.stripAllPara(processMarkdown("description", sd.getDescriptionElement()));
      b.append("<p>Complex Extension: "+html+"</p><ul>");
      for (ElementDefinition ed : subs) {
        ElementDefinition defn = (ElementDefinition) ed.getUserData(UserDataNames.render_extension_slice);
        if (defn != null) {
          b.append("<li>"+(defn.getSliceName())+": "+ed.typeSummary()+": "+Utilities.stripPara(processMarkdown("ext-desc", defn.getDefinition()))+"</li>\r\n");
        }
      }
      b.append("</ul>");
      return b.toString();
    }
  }

  private boolean parentChainHasOptional(ElementDefinition ed, StructureDefinition profile) {
    if (!ed.getPath().contains("."))
      return false;

    ElementDefinition match = (ElementDefinition) ed.getUserData(UserDataNames.SNAPSHOT_DERIVATION_POINTER);
    if (match == null)
      return true; // really, we shouldn't get here, but this appears to be common in the existing profiles?
    // throw new Error("no matches for "+ed.getPath()+"/"+ed.getName()+" in "+profile.getUrl());

    while (match.getPath().contains(".")) {
      if (match.getMin() == 0) {
        return true;
      }
      match = getElementParent(profile.getSnapshot().getElement(), match);
      if (match == null) {
        return true;
      }
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
      return "<li>" +gen.formatPhrase(RenderingContext.SD_SUMMARY_SLICE_NONE, path) + "</li>\r\n";
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
    return "<li>" + gen.formatMessagePlural(slicing.getDiscriminator().size(), RenderingContext.SD_SUMMARY_SLICE, path, b.toString()) + s + "</li>\r\n";
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
      return "<li>" + gen.formatPhrase(RenderingContext.SD_SUMMARY_MISSING_EXTENSION, url) + "</li>";
    if (ed.getWebPath() == null)
      return "<li><a href=\"" + "extension-" + ed.getId().toLowerCase() + ".html\">" + url + "</a>" + (modifier ? " (<b>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_MODIF)) + "</b>) " : "") + "</li>\r\n";
    else
      return "<li><a href=\"" + Utilities.escapeXml(ed.getWebPath()) + "\">" + url + "</a>" + (modifier ? " (<b>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_MODIF)) + "</b>) " : "") + "</li>\r\n";
  }

  private String describeProfile(String url) throws Exception {
    if (url.startsWith("http://hl7.org/fhir/StructureDefinition/") && (igp.isDatatype(url.substring(40)) || igp.isResource(url.substring(40)) || "Resource".equals(url.substring(40))))
      return null;

    StructureDefinition ed = context.fetchResource(StructureDefinition.class, url);
    if (ed == null)
      return "<li>" + gen.formatPhrase(RenderingContext.SD_SUMMARY_MISSING_PROFILE, url) + "</li>";
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
      cu = " (" + (gen.formatPhrase(RenderingContext.GENERAL_UCUM)) + ": " + quantity.getCode() + ")";
    if ("http://snomed.info/sct".equals(quantity.getSystem()))
      cu = " (" + (gen.formatPhrase(RenderingContext.STRUC_DEF_SNOMED_CODE)) + ": " + quantity.getCode() + ")";
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
      return "" + (gen.formatPhrase(RenderingContext.STRUC_DEF_SNOMED_CODE)) + " " + coding.getCode() + (!coding.hasDisplay() ? "" : "(\"" + gen.getTranslated(coding.getDisplayElement()) + "\")");
    if ("http://loinc.org".equals(coding.getSystem()))
      return "" + (gen.formatPhrase(RenderingContext.STRUC_DEF_LOINC)) + " " + coding.getCode() + (!coding.hasDisplay() ? "" : "(\"" + gen.getTranslated(coding.getDisplayElement()) + "\")");
    if ("http://unitsofmeasure.org/".equals(coding.getSystem()))
      return " (" + (gen.formatPhrase(RenderingContext.GENERAL_UCUM)) + ": " + coding.getCode() + ")";
    CodeSystem cs = context.fetchCodeSystem(coding.getSystem());
    if (cs == null)
      return "<span title=\"" + coding.getSystem() + "\">" + coding.getCode() + "</a>" + (!coding.hasDisplay() ? "" : "(\"" + gen.getTranslated(coding.getDisplayElement()) + "\")");
    else
      return "<a title=\"" + cs.present() + "\" href=\"" + Utilities.escapeXml(cs.getWebPath()) + "#" + cs.getId() + "-" + Utilities.nmtokenize(coding.getCode()) + "\">" + coding.getCode() + "</a>" + (!coding.hasDisplay() ? "" : "(\"" + gen.getTranslated(coding.getDisplayElement()) + "\")");
  }
  
  public String contexts() throws IOException {
    XhtmlNode ul = new XhtmlNode(NodeType.Element, "ul");
    for (StructureDefinitionContextComponent ctxt : sd.getContext()) {
      var li = ul.li();
      li.tx(ctxt.getType().toCode());
      li.tx(" ");
      li.tx(ctxt.getExpression());
      if (ctxt.hasExtension(ToolingExtensions.EXT_APPLICABLE_VERSION)) {
        li.tx(" (");
        renderVersionRange(li, ctxt.getExtensionByUrl(ToolingExtensions.EXT_APPLICABLE_VERSION));
        li.tx(")");
          
      }
    }
    return new XhtmlComposer(XhtmlComposer.HTML).compose(ul);
  }

  private void renderVersionRange(XhtmlNode x, Extension ext) {
    String sv = ext.hasExtension("startFhirVersion") ? ext.getExtensionString("startFhirVersion") : null;
    String ev = ext.hasExtension("endFhirVersion") ? ext.getExtensionString("endFhirVersion") : null;
    if (ev != null && ev.equals(sv)) {
      x.tx("For version "+VersionUtilities.getNameForVersion(ev));
    } else if (ev != null && sv != null) {
      x.tx("For versions "+VersionUtilities.getNameForVersion(sv)+" to "+VersionUtilities.getNameForVersion(ev));
    } else if (ev == null && sv != null) {
      x.tx("For versions "+VersionUtilities.getNameForVersion(sv)+" onwards");
    } else if (ev == null && sv != null) {
      x.tx("For versions until "+VersionUtilities.getNameForVersion(ev));
    } else {
      x.tx("For unknown versions");
    }
  }

  public String diff(String defnFile, Set<String> outputTracker, boolean toTabs, StructureDefinitionRendererMode mode, boolean all) throws IOException, FHIRException, org.hl7.fhir.exceptions.FHIRException {
    if (sd.getDifferential().getElement().isEmpty())
      return "";
    else {
      sdr.getContext().setStructureMode(mode);
      return new XhtmlComposer(XhtmlComposer.HTML).compose(sdr.generateTable(new RenderingStatus(), defnFile, sd, true, destDir, false, sd.getId(), false, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, false, outputTracker, false, gen.withUniqueLocalPrefix(all ? "da" : "d"), toTabs ? ANCHOR_PREFIX_DIFF : ANCHOR_PREFIX_SNAP, resE));
    }
  }

  public String eview(String defnFile, Set<String> outputTracker, boolean toTabs, StructureDefinitionRendererMode mode, boolean all) throws IOException, FHIRException, org.hl7.fhir.exceptions.FHIRException {
   return new XhtmlComposer(XhtmlComposer.HTML).compose(sdr.buildElementTable(new RenderingStatus(), defnFile, sd, destDir, false, sd.getId(), false, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, false, outputTracker, false, gen.withUniqueLocalPrefix(all ? "da" : "d"), toTabs ? ANCHOR_PREFIX_DIFF : ANCHOR_PREFIX_SNAP, resE));
  }

  public String snapshot(String defnFile, Set<String> outputTracker, boolean toTabs, StructureDefinitionRendererMode mode, boolean all) throws IOException, FHIRException, org.hl7.fhir.exceptions.FHIRException {
    if (sd.getSnapshot().getElement().isEmpty())
      return "";
    else {
      sdr.getContext().setStructureMode(mode);
      return new XhtmlComposer(XhtmlComposer.HTML).compose(sdr.generateTable(new RenderingStatus(), defnFile, sd, false, destDir, false, sd.getId(), true, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, false, outputTracker, false, gen.withUniqueLocalPrefix(all ? "sa" : null), toTabs ? ANCHOR_PREFIX_SNAP : ANCHOR_PREFIX_SNAP, resE));
    }
  }

  public String obligations(String defnFile, Set<String> outputTracker, boolean toTabs, StructureDefinitionRendererMode mode, boolean all) throws IOException, FHIRException, org.hl7.fhir.exceptions.FHIRException {
    if (sd.getSnapshot().getElement().isEmpty())
      return "";
    else {
      sdr.getContext().setStructureMode(mode);
      return new XhtmlComposer(XhtmlComposer.HTML).compose(sdr.generateTable(new RenderingStatus(), defnFile, sd, false, destDir, false, sd.getId(), true, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, false, outputTracker, false, gen.withUniqueLocalPrefix(all ? "sa" : null), toTabs ? ANCHOR_PREFIX_SNAP : ANCHOR_PREFIX_SNAP, resE));
    }
  }

  public String byKey(String defnFile, Set<String> outputTracker, boolean toTabs, StructureDefinitionRendererMode mode, boolean all) throws IOException, FHIRException, org.hl7.fhir.exceptions.FHIRException {
    if (sd.getSnapshot().getElement().isEmpty())
      return "";
    else {
      XhtmlComposer composer = new XhtmlComposer(XhtmlComposer.HTML);
      StructureDefinition sdCopy = sd.copy();
      
      sdCopy.getSnapshot().setElement(getKeyElements());
      sdr.getContext().setStructureMode(mode);
      org.hl7.fhir.utilities.xhtml.XhtmlNode table = sdr.generateTable(new RenderingStatus(), defnFile, sdCopy, false, destDir, false, sdCopy.getId(), true, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, true, outputTracker, true, gen.withUniqueLocalPrefix(all ? "ka" :"k"), toTabs ? ANCHOR_PREFIX_KEY : ANCHOR_PREFIX_SNAP, resE);

      return composer.compose(table);
    }
  }

  public String byMustSupport(String defnFile, Set<String> outputTracker, boolean toTabs, StructureDefinitionRendererMode mode, boolean all) throws IOException, FHIRException, org.hl7.fhir.exceptions.FHIRException {
    if (sd.getSnapshot().getElement().isEmpty())
      return "";
    else {
      XhtmlComposer composer = new XhtmlComposer(XhtmlComposer.HTML);
      StructureDefinition sdCopy = sd.copy();
      sdr.getContext().setStructureMode(mode);

      sdCopy.getSnapshot().setElement(getMustSupportElements());
      org.hl7.fhir.utilities.xhtml.XhtmlNode table = sdr.generateTable(new RenderingStatus(), defnFile, sdCopy, false, destDir, false, sdCopy.getId(), true, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, false, outputTracker, true, gen.withUniqueLocalPrefix(all ? "ma" :"m"), toTabs ? ANCHOR_PREFIX_MS : ANCHOR_PREFIX_SNAP, resE);

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
              edCopy.setUserData(UserDataNames.render_opaque, true);
            }
            edCopy.setBinding(null);
            edCopy.getConstraint().clear();
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
      org.hl7.fhir.utilities.xhtml.XhtmlNode table = sdr.generateTable(new RenderingStatus(), defnFile, sdCopy, false, destDir, false, sdCopy.getId(), true, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, true, outputTracker, true, gen, ANCHOR_PREFIX_KEY, resE);

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
      boolean keyEligible = sd.getDerivation().equals(TypeDerivationRule.CONSTRAINT) && !sd.getKind().equals(StructureDefinitionKind.LOGICAL);
      keyElements = new ArrayList<ElementDefinition>();
      Map<String, ElementDefinition> mustSupport = getMustSupport();
      Set<ElementDefinition> keyElementsSet = new HashSet<ElementDefinition>();
      if (keyEligible)
        scanForKeyElements(keyElementsSet, mustSupport, sd.getSnapshot().getElement(), sd.getSnapshot().getElementFirstRep(), null);
      for (ElementDefinition ed : sd.getSnapshot().getElement()) {
        if (!keyEligible || keyElementsSet.contains(ed)) {
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
      //  - the min cardinality has been constrained from the base min cardinality
      //  - it has a vocabulary binding or additional binding that is required, extensible, minimum, maximum, or current binding that differs from the base resource.
      //  - it has a fixed value, pattern value, minValue, maxValue, maxLength, mustHaveValue, valueAlternatives
      //  - it has any of the following extensions: allowedUnits, bestPractice, graphConstraint, maxDecimalPlaces, maxSize, mimeType, minLength, obligation,

      List<String> urls = extensionUrls(child.getExtension());
      urls.retainAll(SIGNIFICANT_EXTENSIONS);
      boolean bindingChanged = false;
      String basePath = child.getBase().getPath();
      StructureDefinition baseType = context.fetchResource(StructureDefinition.class, "http://hl7.org/fhir/StructureDefinition/" + basePath.substring(0, basePath.indexOf(".")));
      ElementDefinition baseElement = null;
      for (ElementDefinition e: baseType.getSnapshot().getElement()) {
        if (e.getPath().equals(basePath)) {
          baseElement = e;
          break;
        }
      }
      if (baseElement==null) {
        baseElement = baseElement;
        //This shouldn't have happened
      } else {
        if (!baseElement.hasBinding())
          bindingChanged = true;
        else {
          ElementDefinitionBindingComponent baseBinding = baseElement.getBinding();
          ElementDefinitionBindingComponent binding = child.getBinding();
          if (binding.hasValueSet() && (binding.getStrength().equals(BindingStrength.REQUIRED) || binding.getStrength().equals(BindingStrength.EXTENSIBLE))) {
            if (!baseBinding.hasStrength() || !binding.getStrength().toCode().equals(baseBinding.getStrength().toCode()))
              bindingChanged = true;
            else if (!baseBinding.hasValueSet() || !baseBinding.getValueSet().equals(binding.getValueSet()))
              bindingChanged = true;
          }
          String additionalBindings = getAdditional(binding.getAdditional());
          String baseAdditionalBindings = getAdditional(binding.getAdditional());
          if (!additionalBindings.equals(baseAdditionalBindings))
            bindingChanged = true;
        }
      }
      if (child.hasBinding()) {
        if (baseType.hasSnapshot()) {
          for (ElementDefinition e : baseType.getSnapshot().getElement()) {
            if (e.getPath().equals(basePath)) {
              baseElement = e;
              break;
            }
          }
        }
      }
      boolean oldMS = mustSupport.containsKey(child.getId()) ||
              child.getMin()!=0 ||
              (child.hasCondition() && child.getCondition().size()>1) ||
              child.getIsModifier() ||
              (child.hasSlicing() && !child.getPath().endsWith(".extension") && !child.getPath().endsWith(".modifierExtension")) ||
              child.hasSliceName() ||
              getDifferential().containsKey(child.getId()) ||
              !child.getMax().equals(child.getBase().getMax());
      boolean newMS = child.getMin()!=child.getBase().getMin() ||
              child.hasFixed() ||
              child.hasPattern() ||
              child.hasMinValue() && (baseElement==null || !baseElement.hasMinValue() || !child.getMinValue().toString().equals(baseElement.getMinValue().toString())) ||
              child.hasMaxValue() && (baseElement==null || !baseElement.hasMaxValue() || !child.getMaxValue().toString().equals(baseElement.getMaxValue().toString())) ||
              child.hasMaxLength() && (baseElement==null || !baseElement.hasMaxLength() || child.getMaxLength()!=baseElement.getMaxLength()) ||
              child.hasMustHaveValue() ||
              child.hasValueAlternatives() ||
              !urls.isEmpty();
      if ( oldMS || newMS) {
        scanForKeyElements(keyElements ,mustSupport, elements, child, baseModelUrl);
      }
    }
  }

  private String getAdditional(List<ElementDefinition.ElementDefinitionBindingAdditionalComponent> additionalBindings) {
    String bindings = "";
    for (ElementDefinition.ElementDefinitionBindingAdditionalComponent additional: additionalBindings) {
      if (additional.getPurpose().equals(ElementDefinition.AdditionalBindingPurposeVS.REQUIRED) ||
              additional.getPurpose().equals(ElementDefinition.AdditionalBindingPurposeVS.EXTENSIBLE) ||
              additional.getPurpose().equals(ElementDefinition.AdditionalBindingPurposeVS.REQUIRED))
        bindings += additional.getPurpose().getDisplay()+":" + additional.getValueSet()+";";
    }
    return bindings;
  }

  private List<String> extensionUrls(List<Extension> extensions) {
    List<String> urls = new ArrayList<String>();
    for (Extension e: extensions) {
      if (!urls.contains(e.getUrl()))
        urls.add(e.getUrl());
    }
    return urls;
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
          ed.getBinding().setUserData(UserDataNames.render_tx_value, ed.getFixed());
        } else if (ed.hasPattern()) {
          hasFixed = true;
          ed.getBinding().setUserData(UserDataNames.render_tx_pattern, ed.getPattern());
        } else {
          // tricky : scan the children for a fixed coding value
          DataType t = findFixedValue(ed, true);
          if (t != null)
            ed.getBinding().setUserData(UserDataNames.render_tx_value, t);
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
        b.append("<h4>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_TERM_BIND)) + "</h4>\r\n");
      b.append("<table class=\"list\">\r\n");
      b.append("<tr><td><b>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_PATH)) + "</b></td><td><b>" + (gen.formatPhrase(RenderingContext.GENERAL_CONFORMANCE)) + "</b></td><td><b>" + (hasFixed ? (gen.formatPhrase(RenderingContext.STRUC_DEF_VALUESET_CODE)) : (gen.formatPhrase(RenderingContext.STRUC_DEF_VALUESET))) + "</b></td><td><b>" + (gen.formatPhrase(RenderingContext.GENERAL_URI)) + "</b></td></tr>\r\n");
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
          ed.getBinding().setUserData(UserDataNames.render_tx_value, ed.getFixed());
        } else if (ed.hasPattern()) {
          hasFixed = true;
          ed.getBinding().setUserData(UserDataNames.render_tx_pattern, ed.getPattern());
        } else {
          // tricky : scan the children for a fixed coding value
          DataType t = findFixedValue(ed, false);
          if (t != null)
            ed.getBinding().setUserData(UserDataNames.render_tx_value, t);
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
        b.append("<h4>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_TERM_BINDS)) + "</h4>\r\n");
      b.append("<table class=\"list\">\r\n");
      b.append("<tr><td><b>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_PATH)) + "</b></td><td><b>" + (gen.formatPhrase(RenderingContext.GENERAL_CONFORMANCE)) + "</b></td><td><b>" + (hasFixed ?  (gen.formatPhrase(RenderingContext.STRUC_DEF_VALUESET_CODE)) : (gen.formatPhrase(RenderingContext.STRUC_DEF_VALUESET))) + "</b></td>"+
      "<td><b>" + (gen.formatPhrase(RenderingContext.GENERAL_URI)) + "</b></td></tr>\r\n");
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
    String link = null;
    if (tx.hasValueSet()) {
      link = txDetails(tx, brd, false);
    } else if (ed.hasUserData(UserDataNames.SNAPSHOT_DERIVATION_POINTER)) {
      ElementDefinitionBindingComponent txi = ((ElementDefinition) ed.getUserData(UserDataNames.SNAPSHOT_DERIVATION_POINTER)).getBinding();
      link = txDetails(txi, brd, true);
    }
    boolean strengthInh = false;
    BindingStrength strength = null;
    if (tx.hasStrength()) {
      strength = tx.getStrength();
    } else if (ed.hasUserData(UserDataNames.SNAPSHOT_DERIVATION_POINTER)) {
      ElementDefinitionBindingComponent txi = ((ElementDefinition) ed.getUserData(UserDataNames.SNAPSHOT_DERIVATION_POINTER)).getBinding();
      strength = txi.getStrength();
      strengthInh = true;
    }

    if ("?ext".equals(brd.vsn)) {
      if (tx.getValueSet() != null)
         System.out.println("Value set '"+tx.getValueSet()+"' at " + url + "#" + path + " not found");
      else if (!tx.hasDescription())
        System.out.println("No value set specified at " + url + "#" + path + " (no url)");
    }
    if (tx.hasUserData(UserDataNames.render_tx_value)) {
      brd.vss = "Fixed Value: " + summariseValue((DataType) tx.getUserData(UserDataNames.render_tx_value));
      brd.suffix = null;
    } else if (tx.hasUserData(UserDataNames.render_tx_pattern)) {
      brd.vss = "Pattern: " + summariseValue((DataType) tx.getUserData(UserDataNames.render_tx_pattern));
      brd.suffix = null;
    }

    b.append("<tr><td>").append(path).append("</td><td><a style=\"opacity: " + opacityStr(strengthInh) + "\" href=\"").append(corePath).append("terminologies.html#").append(strength == null ? "" : gen.getTranslated(tx.getStrengthElement()));
    if (tx.hasDescription())
      b.append("\">").append(strength == null ? "" : gen.getTranslated(tx.getStrengthElement())).append("</a></td><td title=\"").append(Utilities.escapeXml(tx.getDescription())).append("\">").append(brd.vss);
    else
      b.append("\">").append(strength == null ? "" : gen.getTranslated(tx.getStrengthElement())).append("</a></td><td>").append(brd.vss);
    if (brd.suffix != null) {
      b.append(brd.suffix);
    }
    if (tx.hasValueSet()) {
      b.append("<div><code>"+Utilities.escapeXml(tx.getValueSet())+"</code><button title=\"Click to copy URL\" class=\"btn-copy\" data-clipboard-text=\""+Utilities.escapeXml(tx.getValueSet())+"\"></button></div>");
      if (link != null) {
        if (Utilities.isAbsoluteUrlLinkable(link)) {
          b.append("<div>from <a href=\""+Utilities.escapeXml(link)+"\">"+Utilities.escapeXml(link)+"</a></div>");
        } else {
          b.append("<div>from "+Utilities.escapeXml(link)+"</div>");
        }
      }
    } else {
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
    b.append("</td>");
    b.append("</tr>\r\n");
  }

  public String txDetails(ElementDefinitionBindingComponent tx, BindingResolutionDetails brd, boolean inherited) {
    String uri = null;
    String link = null;
    if (tx.getValueSet() != null) {
      uri = tx.getValueSet().trim();
    }
    String name = getSpecialValueSetName(uri);
    if (name != null) {
      brd.vss = "<a style=\"opacity: " + opacityStr(inherited) + "\" href=\"" + Utilities.escapeXml(getSpecialValueSetUrl(uri)) + "\">" + Utilities.escapeXml(name) + "</a>";
      brd.vsn = name;
    } else {
      ValueSet vs = context.findTxResource(ValueSet.class, canonicalise(uri));
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
        if (vs.hasUserData(UserDataNames.render_external_link)) {
          link = vs.getUserString(UserDataNames.render_external_link);
        } else if (vs.hasSourcePackage()) {
          if (VersionUtilities.isCorePackage(vs.getSourcePackage().getId())) {
            link = "the FHIR Standard";
          } else if (!Utilities.isAbsoluteUrlLinkable(vs.getWebPath())) {
            link = "this IG";
          } else if (!Utilities.isAbsoluteUrlLinkable(vs.getWebPath())) {
            link = "Package: "+vs.getSourcePackage();
          }
        }
        StringBuilder b = new StringBuilder();
        if (p == null)
          b.append("<a style=\"opacity: " + opacityStr(inherited) + "\" href=\"??\">" + Utilities.escapeXml(gen.getTranslated(vs.getNameElement())) + " (" + (gen.formatPhrase(RenderingContext.STRUC_DEF_MISSING_LINK))+")");
        else if (p.startsWith("http:"))
          b.append("<a style=\"opacity: " + opacityStr(inherited) + "\" href=\"" + Utilities.escapeXml(p) + "\">" + Utilities.escapeXml(gen.getTranslated(vs.getNameElement())));
        else
          b.append("<a style=\"opacity: " + opacityStr(inherited) + "\" href=\"" + Utilities.escapeXml(p) + "\">" + Utilities.escapeXml(gen.getTranslated(vs.getNameElement())));
        if (vs.hasUserData(UserDataNames.render_external_link)) {
          b.append(" <img src=\"external.png\" alt=\".\"/>");
        }
        b.append("</a>");
        brd.vss = b.toString();
        StringType title = vs.hasTitleElement() ? vs.getTitleElement() : vs.getNameElement();
        if (title != null) {
          brd.vsn = gen.getTranslated(title);
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
    return link;
  }

  private String opacityStr(boolean inherited) {
    return inherited ? "0.5" : "1.0";
  }

  private String getSpecialValueSetName(String uri) {
    if (uri != null && uri.startsWith("http://loinc.org/vs/"))
      return "LOINC " + uri.substring(20);
    return null;
  }

  private String getSpecialValueSetUrl(String uri) {
    if (uri != null && uri.startsWith("http://loinc.org/vs/"))
      return "https://loinc.org/" + uri.substring(20);
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
      return new SnapshotGenerationPreProcessor(gen.getProfileUtilities()).supplementMissingDiffElements(sd);
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
        b.append("<h4>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_CONSTRAINTS)) + "</h4>\r\n");
      b.append("<table class=\"list\">\r\n");
      b.append("<tr><td width=\"60\"><b>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_ID)) + "</b></td><td><b>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_GRADE)) + "</b></td><td><b>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_PATHS)) + "</b></td><td><b>" + (gen.formatPhrase(RenderingContext.GENERAL_DETAILS)) + "</b></td><td><b>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_REQUIREMENTS)) + "</b></td></tr>\r\n");
      List<String> keys = new ArrayList<>(constraintMap.keySet());

      Collections.sort(keys, new ConstraintKeyComparator());
      for (String key : keys) {
        ConstraintInfo ci = constraintMap.get(key);
        for (ConstraintVariation cv : ci.getVariations()) {
          ElementDefinitionConstraintComponent inv = cv.getConstraint();
          if (!inv.hasSource() || inv.getSource().equals(sd.getUrl()) || allInvariants) {
            b.append("<tr><td>").append(inv.getKey()).append("</td><td>").append(grade(inv)).append("</td><td>").append(cv.getIds()).append("</td><td>").append(Utilities.escapeXml(gen.getTranslated(inv.getHumanElement())))
            .append("<br/>: ").append(Utilities.escapeXml(inv.getExpression())).append("</td><td>").append(Utilities.escapeXml(gen.getTranslated(inv.getRequirementsElement()))).append("</td></tr>\r\n");
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
      return gen.getTranslated(inv.getSeverityElement());
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


  public String dict(boolean incProfiledOut, int mode, String anchorPrefix) throws Exception {
    XhtmlNode x = new XhtmlNode(NodeType.Element, "div");
    var p = x.para();
    p.tx("Guidance on how to interpret the contents of this table can be found ");
    p.ah("https://build.fhir.org/ig/FHIR/ig-guidance//readingIgs.html#data-dictionaries").tx("here");
    XhtmlNode t = x.table("dict", false);

    List<ElementDefinition> elements = elementsForMode(mode);

    sdr.renderDict(new RenderingStatus(), sd, elements, t, incProfiledOut, mode, anchorPrefix, resE);
    
    return new XhtmlComposer(false, false).compose(x.getChildNodes());
  }


  public String mappings(boolean complete, boolean diff) {
    if (sd.getMapping().isEmpty())
      return "<p>" + (gen.formatPhrase(RenderingContext.STRUC_DEF_NO_MAPPINGS)) + "</p>";
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
            s.append("<a name=\"" + map.getIdentity() + "\"> </a><h3>" +gen.formatPhrase(RenderingContext.SD_SUMMARY_MAPPINGS, Utilities.escapeXml(gen.getTranslated(map.getNameElement())), Utilities.escapeXml(map.getUri()), "", "") + "</h3>");
          else
            s.append("<a name=\"" + map.getIdentity() + "\"> </a><h3>" +gen.formatPhrase(RenderingContext.SD_SUMMARY_MAPPINGS, Utilities.escapeXml(gen.getTranslated(map.getNameElement())), Utilities.escapeXml(map.getUri()), "<a href=\"" + Utilities.escapeXml(url) + "\">", "</a>") + "</h3>");
          if (map.hasComment())
            s.append("<p>" + Utilities.escapeXml(gen.getTranslated(map.getCommentElement())) + "</p>");
          //        else if (specmaps != null && preambles.has(map.getUri()))   
          //          s.append(preambles.get(map.getUri()).getAsString()); 

          s.append("<table class=\"grid\">\r\n");
          s.append(" <tr><td colspan=\"3\"><b>" + Utilities.escapeXml(gen.getTranslated(sd.getNameElement())) + "</b></td></tr>\r\n");
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
        s.append("<p>" + gen.formatPhrase(RenderingContext.STRUC_DEF_ALL_MAP_KEY) + "</p>");
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
    b.append("<p class=\"profile-url-label\">\r\n");
    b.append("The official URL for this profile is:" + "\r\n");
    b.append("</p>\r\n");
    b.append("<pre class=\"profile-url\">" + sd.getUrl() + "</pre>\r\n");
    b.append("<div class=\"profile-description\">\r\n");
    b.append(processMarkdown("description", sd.getDescriptionElement()));
    b.append("</div>\r\n");
    if (sd.getDerivation() == TypeDerivationRule.CONSTRAINT) {
      b.append("<p class=\"profile-derivation\">\r\n");
      StructureDefinition sdb = context.fetchResource(StructureDefinition.class, sd.getBaseDefinition());
      if (sdb != null)
        b.append(gen.formatPhrase(RenderingContext.STRUC_DEF_PROFILE_BUILDS) + " <a href=\"" + Utilities.escapeXml(sdb.getWebPath()) + "\">" + gen.getTranslated(sdb.getNameElement()) + "</a>.");
      else
        b.append(gen.formatPhrase(RenderingContext.STRUC_DEF_PROFILE_BUILDS) + " " + sd.getBaseDefinition() + ".");
      b.append("</p>\r\n");
    }
    b.append("<p class=\"profile-publication\">\r\n");
    b.append(gen.formatPhrase(RenderingContext.SD_SUMMARY_PUBLICATION, renderDate(sd.getDateElement()), gen.getTranslated(sd.getStatusElement()), gen.getTranslated(sd.getPublisherElement()))+"\r\n");
    b.append("</p>\r\n");
    return b.toString();
  }

  private String renderDate(DateTimeType date) {
    return new DataRenderer(gen).displayDataType(date);
  }

  public String uses() throws Exception {
    StringBuilder b = new StringBuilder();
    List<CanonicalResource> crl = scanAllResources(StructureDefinition.class, "StructureDefinition");
    List<StructureDefinition> derived = findDerived(crl);
    if (!derived.isEmpty()) {
      b.append("<p>\r\n");
      b.append(gen.formatPhrase(RenderingContext.STRUC_DEF_DERIVED_PROFILE) + " ");
      listResources(b, derived);
      b.append("</p>\r\n");
    }
    List<StructureDefinition> users = findUses(crl);
    if (!users.isEmpty()) {
      b.append("<p>\r\n");
      b.append(gen.formatPhrase(RenderingContext.STRUC_DEF_REFER_PROFILE)+" ");
      listResources(b, users);
      b.append("</p>\r\n");
    }
    return b.toString();
  }


  private List<StructureDefinition> findDerived(List<CanonicalResource> crl) {
    List<StructureDefinition> res = new ArrayList<>();
    for (CanonicalResource cr : crl) {
      StructureDefinition t = (StructureDefinition) cr;
      if (refersToThisSD(t.getBaseDefinition())) {
        res.add(t);
      }
    }
    return res;
  }

  private List<StructureDefinition> findUses(List<CanonicalResource> crl) {
    List<StructureDefinition> res = new ArrayList<>();
    for (CanonicalResource cr : crl) {
      StructureDefinition t = (StructureDefinition) cr;
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
            if (refersToThisSD(p)) {
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
            if (refersToThisSD(p)) {
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
            if (refersToThisSD(p)) {
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
            if (refersToThisSD(p)) {
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
            if (refersToThisSD(p)) {
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
            if (refersToThisSD(p)) {
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

  public String span(boolean onlyConstraints, String canonical, Set<String> outputTracker, String anchorPrefix) throws IOException, FHIRException {
    return new XhtmlComposer(XhtmlComposer.HTML).compose(sdr.generateSpanningTable(sd, destDir, onlyConstraints, canonical, outputTracker, anchorPrefix));
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
        generateCoreElemSliced(b, sd.getSnapshot().getElement(), child, children, 2, rn, false, child.getType().isEmpty() ? null : child.getType().get(0), ++c == l, complex);
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
        ValueSet vs = context.findTxResource(ValueSet.class, elem.getBinding().getValueSet());
        if (vs != null)
          b.append(" <span style=\"color: navy; opacity: 0.8\"><a href=\"" + corePath + vs.getUserData(UserDataNames.render_filename) + ".html\" style=\"color: navy\">" + Utilities.escapeXml(elem.getShort()) + "</a></span>");
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
    if (en.contains("[x]")) {
      if (type == null) {
        throw new Error("Type cannot be unknown for element with [x] in the name @ "+pathName);
      }
      en = en.replace("[x]", upFirst(type.getWorkingCode()));
    }
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
    Map<String, String> imposed = new HashMap<>();
    Map<String, String> compliedWith = new HashMap<>();
    Map<String, String> refs = new HashMap<>();
    Map<String, String> trefs = new HashMap<>();
    Map<String, String> examples = new HashMap<>();
    Map<String, String> searches = new HashMap<>();
    Map<String, String> capStmts = new HashMap<>();
    for (CanonicalResource cr : scanAllResources(StructureDefinition.class, "StructureDefinition")) {
      StructureDefinition sdt = (StructureDefinition) cr;
      if (refersToThisSD(sdt.getBaseDefinition())) {
        base.put(sdt.getWebPath(), sdt.present());
      }
      scanExtensions(invoked, sdt, ToolingExtensions.EXT_OBLIGATION_INHERITS);
      scanExtensions(imposed, sdt, ToolingExtensions.EXT_SD_IMPOSE_PROFILE);
      scanExtensions(compliedWith, sdt, ToolingExtensions.EXT_SD_COMPLIES_WITH_PROFILE);

      for (ElementDefinition ed : sdt.getDifferential().getElement()) {
        for (TypeRefComponent tr : ed.getType()) {
          if (refersToThisSD(tr.getCode())) {
            if (sdt.hasWebPath()) {
              refs.put(sdt.getWebPath(), sdt.present());
            }
          }
          for (CanonicalType u : tr.getProfile()) {
            if (refersToThisSD(u.getValue())) {
              if (sdt.hasWebPath()) {
                refs.put(sdt.getWebPath(), sdt.present());
              } else {
                System.out.println("SD "+sdt.getVersionedUrl()+" has no path");
              }
            }
          }
          for (CanonicalType u : tr.getTargetProfile()) {
            if (refersToThisSD(u.getValue())) {
              if (sdt.hasWebPath()) {
                trefs.put(sdt.getWebPath(), sdt.present());
              } else {
                System.out.println("SD "+sdt.getVersionedUrl()+" has no path");
              }
            }
          }
        }
      }
    }

    for (FetchedFile f : files) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof CapabilityStatement) {
          CapabilityStatement cst = (CapabilityStatement) r.getResource();
          scanCapStmt(capStmts, cst);
        }
      }
    }
    for (CanonicalResource cr : scanAllResources(null, "StructureDefinition")) {
      CapabilityStatement cst = (CapabilityStatement) cr;
      scanCapStmt(capStmts, cst);
    }
    
    if (VersionUtilities.isR5Plus(context.getVersion())) {
      if (usages == null) {
        FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager.Builder().build();
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
          if (r.fhirType().equals("SearchParameter")) {
            SearchParameter sp = (SearchParameter) r.getResource();
            String exp = sp.getExpression();
            if (exp != null && exp.contains("extension('"+sd.getUrl()+"')")) {
              searches.put(igp.getLinkFor(r, true), r.getTitle());
            }
          }
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
    for (RelatedIG ig : relatedIgs) {
      for (Element r : ig.loadE(context, sd.getType())) {
        if (usesSD(r)) {
          examples.put(r.getWebPath(), r.getUserString(UserDataNames.renderer_title));
        }        
      }
    }

    StringBuilder b = new StringBuilder();
    if (sd.hasExtension(ToolingExtensions.EXT_WEB_SOURCE)) {
      String url = ToolingExtensions.readStringExtension(sd, ToolingExtensions.EXT_WEB_SOURCE);
      if (Utilities.isAbsoluteUrlLinkable(url)) {
        b.append("<p><b>Original Source:</b> <a href=\""+Utilities.escapeXml(url)+"\">"+Utilities.escapeXml(Utilities.extractDomain(url))+"</a></p>\r\n");      
      } else {
        b.append("<p><b>Original Source:</b> <code>"+Utilities.escapeXml(url)+"</a></p>\r\n");        
      }
    } else if (sd.getMeta().hasSource() && Utilities.isAbsoluteUrlLinkable(sd.getMeta().getSource())) {
      b.append("<p><b>Original Source:</b> <a href=\""+sd.getMeta().getSource()+"\">"+Utilities.extractDomain(sd.getMeta().getSource())+"</a></p>\r\n");
    }
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
    if (!imposed.isEmpty()) {
      b.append(" <li>Impose this profile " + type + ": " + refList(imposed, "imposed") + "</li>\r\n");
    }
    if (!compliedWith.isEmpty()) {
      b.append(" <li>Comply with this profile " + type + ": " + refList(compliedWith, "compliedWith") + "</li>\r\n");
    }
    if (!refs.isEmpty()) {
      b.append(" <li>Use this " + type + ": " + refList(refs, "ref") + "</li>\r\n");
    }
    if (!trefs.isEmpty()) {
      b.append(" <li>Refer to this " + type + ": " + refList(trefs, "tref") + "</li>\r\n");
    }
    if (!examples.isEmpty()) {
      b.append(" <li>Examples for this " + type + ": " + refList(examples, "ex") + "</li>\r\n");
    }
    if (!searches.isEmpty()) {
      b.append(" <li>Search Parameters using this " + type + ": " + refList(searches, "sp") + "</li>\r\n");
    }
    if (!capStmts.isEmpty()) {
      b.append(" <li>CapabilityStatements using this " + type + ": " + refList(capStmts, "cst") + "</li>\r\n");
    }
    if (base.isEmpty() && refs.isEmpty() && trefs.isEmpty() && examples.isEmpty() & invoked.isEmpty() && imposed.isEmpty() && compliedWith.isEmpty()) {
      b.append(" <li>This " + type + " is not used by any profiles in this Implementation Guide</li>\r\n");
    }
    b.append("</ul>\r\n");
    return b.toString()+changeSummary();
  }

  public void scanCapStmt(Map<String, String> capStmts, CapabilityStatement cst) {
    for (CapabilityStatementRestComponent rest : cst.getRest()) {
      for (CapabilityStatementRestResourceComponent res : rest.getResource()) {
        boolean inc = false;
        if (refersToThisSD(res.getProfile())) {
          inc = true;
        } else for (CanonicalType c : res.getSupportedProfile()) {
          if (refersToThisSD(c.getValue())) {
            inc = true;
          } 
        }
        if (inc) {
          capStmts.put(cst.getWebPath(), cst.present());
        }
      }
    }
  }

  private boolean refersToThisSD(String url) {
    if (url == null) {
      return false;
    }
    if (url.contains("|")) {
      url = url.substring(0, url.indexOf("|"));
    }
    if (this.sd.getUrl().equals(url)) {
      return true;
    } else {
      return false;
    }
  }

  private void scanExtensions(Map<String, String> invoked, StructureDefinition sd, String u) {
    for (Extension ext : sd.getExtensionsByUrl(u)) {
      String v = ext.getValue().primitiveValue();
      if (refersToThisSD(v)) {
        invoked.put(sd.getWebPath(), sd.present());
      }
    }
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
        if (refersToThisSD(p.getValue()))
          return true;
      }
    }
    return usesExtension(resource);
  }

  private boolean usesExtension(Element focus) {
    for (Element child : focus.getChildren()) {
      if (child.getName().equals("extension") && refersToThisSD(child.getChildValue("url")))
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
    boolean showLink = false;
    for (String s : sorted(base.keySet())) {
      c++;
      if (c == MAX_DEF_SHOW && base.size() > MAX_DEF_SHOW) {
        showLink = true;
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
      if (c % 80 == 0) {
        b.append("\r\n");
      }
    }

    if (showLink) {
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

  public String expansion(String definitionsName, Set<String> otherFilesRun, String anchorPrefix) throws IOException {
    PEBuilder pe = context.getProfiledElementBuilder(PEElementPropertiesPolicy.NONE, true);
    HierarchicalTableGenerator gen = new HierarchicalTableGenerator(this.gen, destDir, true, true, anchorPrefix);

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
    row.setColor(element.hasFixedValue() ? "#eeeeee" : "#ffffff");
    row.setLineColor(0);
    if (element.hasFixedValue()) {
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
      gc.addStyledText((this.gen.formatPhrase(RenderingContext.STRUC_DEF_MOD)), "?!", null, null, null, false);
    }
    if (element.definition().getMustSupport() || element.definition().hasExtension(ToolingExtensions.EXT_OBLIGATION_CORE, ToolingExtensions.EXT_OBLIGATION_TOOLS)) {
      gc.addStyledText((this.gen.formatPhrase(RenderingContext.STRUC_DEF_ELE_MUST_SUPP)), "S", "white", "red", null, false);
    }
    if (element.definition().getIsSummary()) {
      gc.addStyledText((this.gen.formatPhrase(RenderingContext.STRUC_DEF_ELE_INCLUDED)), "\u03A3", null, null, null, false);
    }
    if (sdr.hasNonBaseConstraints(element.definition().getConstraint()) || sdr.hasNonBaseConditions(element.definition().getCondition())) {
      Piece p = gc.addText(org.hl7.fhir.r5.renderers.StructureDefinitionRenderer.CONSTRAINT_CHAR);
      p.setHint((this.gen.formatPhrase(RenderingContext.STRUC_DEF_AFFECT_CONSTRAINTS)+sdr.listConstraintsAndConditions(element.definition())+")"));
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
    sdr.generateDescription(new RenderingStatus(), gen, row, element.definition(), null, true, context.getSpecUrl(), null, sd, context.getSpecUrl(), destDir, false, false, allInvariants, true, false, false, sdr.getContext(), resE);

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
        trow.setColor(element.hasFixedValue() ? "#eeeeee" : "#ffffff");
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
  

  protected void changeSummaryDetails(StringBuilder b) {
    CanonicalResourceComparison<? extends CanonicalResource> comp = VersionComparisonAnnotation.artifactComparison(sd);
    if (comp != null && comp.anyUpdates()) {
      if (comp.getChangedMetadata() == ChangeAnalysisState.CannotEvaluate) {
        b.append("<li>Unable to evaluate changes to metadata</li>\r\n");
      } else if (comp.getChangedMetadata() == ChangeAnalysisState.Changed) {
        b.append("<li>The resource metadata has changed ("+comp.getMetadataFieldsAsText()+")</li>\r\n");          
      }
      
      if (comp.getChangedContent() == ChangeAnalysisState.CannotEvaluate) {
        b.append("<li>Unable to evaluate changes to content</li>\r\n");
      } else if (comp.getChangedContent() == ChangeAnalysisState.Changed) {
        b.append("<li>The data elements list has changed</li>\r\n");          
      }

      if (comp.getChangedDefinitions() == ChangeAnalysisState.CannotEvaluate) {
        b.append("<li>Unable to evaluate changes to definitions</li>\r\n");
      } else if (comp.getChangedDefinitions() == ChangeAnalysisState.Changed) {
        b.append("<li>One or more text definitions, invariants or bindings have changed</li>\r\n");          
      }
    } else if (comp == null) {
      b.append("<li>New Content</li>\r\n");
    } else {
      b.append("<li>No changes</li>\r\n");
    }
  }
  

  private String tail(String path) {
    if (path.contains("."))
      return path.substring(path.lastIndexOf(".") + 1);
    else
      return path;
  }

  public String compareImposes(StructureDefinition sdi) {
    return "todo";
  }

  public String experimentalWarning() {
    if (sd.getExperimental()) {
      return "<div><div style=\"border: 1px solid maroon; padding: 10px; background-color: #fffbf7; min-height: 160px;\">\r\n"+
             "<img src=\"assets/images/dragon.png\" width=\"150\" style=\"float:left; mix-blend-mode: multiply; margin-right: 10px;\" title=\"Here Be Dragons!\" height=\"150\"/>\r\n"+
             "<p><b>This is an experimental extension definition; the committee is seeking implementation feedback, and the " + 
             "definition or contents of the extension may change in future versions.</b></p></div></div>";
    } else {
      return "";
    }
  }

  public String adl() {
    return "<pre><code>"+Utilities.escapeXml(sd.getUserString(UserDataNames.archetypeSource))+"</code></pre>";
  }

  public String useContext() throws IOException {
    XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
    if ("deprecated".equals(ToolingExtensions.readStringExtension(sd, ToolingExtensions.EXT_STANDARDS_STATUS))) {
      XhtmlNode ddiv = div.div("background-color: #ffe6e6; border: 1px solid black; border-radius: 10px; padding: 10px");
      ddiv.para().b().tx("This extension is deprecated and should no longer be used");
      Extension ext = sd.getExtensionByUrl(ToolingExtensions.EXT_STANDARDS_STATUS);
      ext = ext == null || !ext.hasValue() ? null : ext.getValue().getExtensionByUrl(ToolingExtensions.EXT_STANDARDS_STATUS_REASON);
      if (ext != null && ext.hasValue()) {
        String md = ext.getValue().primitiveValue();
        try {
          md = preProcessMarkdown("Standards-Status-Reason", md);
          ddiv.markdown(md, "Standards-Status-Reason");
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    if (sd.getContext().isEmpty()) {
      div.para().tx("This extension does not specify which elements it should be used on");
    } else {
      div.para().tx("This extension may be used on the following element(s):");
      var ul = div.ul();
      for (StructureDefinitionContextComponent c : sd.getContext()) {
        var li = ul.li();
        switch (c.getType()) {
        case ELEMENT:
          li.tx("Element ID: ");
          String tn = c.getExpression();
          if (tn.contains(".")) {
            tn = tn.substring(0, tn.indexOf("."));
          }
          StructureDefinition t = context.fetchTypeDefinition(tn);
          var code = li.code();
          if (t != null && t.hasWebPath()) {
            code.ah(t.getWebPath()).tx(c.getExpression());
          } else {
            code.tx(c.getExpression());
          }
          break;
        case EXTENSION:
          li.tx("Extension: ");
          t = context.fetchResource(StructureDefinition.class, c.getExpression());
          if (t != null && t.hasWebPath()) {
            li.ah(t.getWebPath()).tx(t.present());
          } else {
            li.code().tx(c.getExpression());
          }
          break;
        case FHIRPATH:
          li.ah(Utilities.pathURL(context.getSpecUrl(), "fhirpath.html")).tx("Path");
          li.tx(": ");
          li.tx(c.getExpression());
          break;
        default:
          li.tx("?type?: ");
          li.tx(c.getExpression());
          break;
        }
        if (c.hasExtension(ToolingExtensions.EXT_FHIRVERSION_SPECIFIC_USE)) {
          li.tx(" (");
          renderVersionRange(c.getExtensionByUrl(ToolingExtensions.EXT_FHIRVERSION_SPECIFIC_USE), li);
          li.tx(")");        
        }
      }
    }
    if (sd.hasContextInvariant()) {
      if (sd.getContextInvariant().size() == 1) {
        div.para().tx("In addition, the extension can only be used when this FHIRPath expression is true:");
        div.para().code().tx(sd.getContextInvariant().get(0).asStringValue());
      } else {
        div.para().tx("In addition, the extension can only be used when these FHIRPath expressions are true:");
        var ul = div.ul();
        for (StringType sv : sd.getContextInvariant()) {
          ul.li().code().tx(sv.asStringValue());
        }
      }
    }
    if (sd.hasExtension(ToolingExtensions.EXT_FHIRVERSION_SPECIFIC_USE)) {
      var p = div.para();
      p.tx("This extension is allowed for use with "); 
      renderVersionRange(sd.getExtensionByUrl(ToolingExtensions.EXT_FHIRVERSION_SPECIFIC_USE), p);
      p.tx(".");        
    }
    return new XhtmlComposer(false, true).compose(div.getChildNodes());
  }

  public void renderVersionRange(Extension ext, XhtmlNode li) {
    if (!ext.hasExtension(ToolingExtensions.EXT_FHIRVERSION_SPECIFIC_USE_START)) {
      li.tx("FHIR versions up to ");
      linkToVersion(li, ToolingExtensions.readStringExtension(ext, ToolingExtensions.EXT_FHIRVERSION_SPECIFIC_USE_END));                
    } else if (!ext.hasExtension(ToolingExtensions.EXT_FHIRVERSION_SPECIFIC_USE_END)) {
      li.tx("FHIR versions ");
      linkToVersion(li, ToolingExtensions.readStringExtension(ext, ToolingExtensions.EXT_FHIRVERSION_SPECIFIC_USE_START));
      li.tx(" and after");        
    } else {
      li.tx("FHIR versions ");
      linkToVersion(li, ToolingExtensions.readStringExtension(ext, ToolingExtensions.EXT_FHIRVERSION_SPECIFIC_USE_START));
      li.tx(" to ");
      linkToVersion(li, ToolingExtensions.readStringExtension(ext, ToolingExtensions.EXT_FHIRVERSION_SPECIFIC_USE_END));        
    }
  }

  private void linkToVersion(XhtmlNode p, String version) {
    String url = VersionUtilities.getSpecUrl(version);
    p.ahOrNot(url).b().tx(VersionUtilities.getNameForVersion(version));    
  }

  public String otherVersions(Set<String> outputTracker, FetchedResource resource) throws IOException {
    if (!resource.hasOtherVersions()) {
      return "";
    } else {
      XhtmlNode x = new XhtmlNode(NodeType.Element, "div");
      for (String v : Utilities.sortedReverse(resource.getOtherVersions().keySet())) {
        if (v.contains("-StructureDefinition")) {
          AlternativeVersionResource sdv = resource.getOtherVersions().get(v);
          x.h3().tx(VersionUtilities.getNameForVersion(v));
          if (sdv.getResource() == null) {
            x.para().tx("The extension is not used in "+VersionUtilities.getNameForVersion(v).toUpperCase());
            XhtmlNode ul = x.ul(); 
            for (ConversionMessage msg : sdv.getLog()) {
              switch (msg.getStatus()) {
              case ERROR:
                ul.li().span().style("padding: 4px; color: maroon").tx(msg.getMessage());
                break;
              case NOTE:
                ul.li().span().style("padding: 4px; background-color: #fadbcf").tx(msg.getMessage());
                break;
              case WARNING:
              default:
                ul.li().tx(msg.getMessage());
              }
            }
          } else if (sdv.getLog().isEmpty()) {
            x.para().tx("The extension is unchanged in "+VersionUtilities.getNameForVersion(v).toUpperCase());
          } else {
            x.para().tx("The extension is represented a little differently in "+VersionUtilities.getNameForVersion(v).toUpperCase()+": ");
            XhtmlNode ul = x.ul();
            for (ConversionMessage msg : sdv.getLog()) {
              switch (msg.getStatus()) {
              case ERROR:
                ul.li().span().style("padding: 4px; color: maroon").tx(msg.getMessage());
                break;
              case NOTE:
                ul.li().span().style("padding: 4px; background-color: #fadbcf").tx(msg.getMessage());
                break;
              case WARNING:
              default:
                ul.li().tx(msg.getMessage());
              }
            }
            sdr.getContext().setStructureMode(StructureDefinitionRendererMode.SUMMARY);
            x.add(sdr.generateTable(new RenderingStatus(), null, (StructureDefinition) sdv.getResource(), true, destDir, false, sd.getId(), false, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, false, 
                outputTracker, false, gen.withUniqueLocalPrefix(VersionUtilities.getNameForVersion(v)), ANCHOR_PREFIX_SNAP, resE));
          }
        }
      }
      return new XhtmlComposer(false, true).compose(x.getChildNodes());
    }
  }
  
}
