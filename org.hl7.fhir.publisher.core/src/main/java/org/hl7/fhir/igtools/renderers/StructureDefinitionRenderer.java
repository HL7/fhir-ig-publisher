package org.hl7.fhir.igtools.renderers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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
import org.hl7.fhir.r5.comparison.VersionComparisonAnnotation;
import org.hl7.fhir.r5.conformance.profile.BindingResolution;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.conformance.profile.SnapshotGenerationPreProcessor;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionBindingComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionConstraintComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionMappingComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionSlicingComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent;
import org.hl7.fhir.r5.model.ElementDefinition.SlicingRules;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.Enumerations.BindingStrength;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionContextComponent;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionMappingComponent;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.profilemodel.PEBuilder;
import org.hl7.fhir.r5.profilemodel.PEBuilder.PEElementPropertiesPolicy;
import org.hl7.fhir.r5.profilemodel.PEDefinition;
import org.hl7.fhir.r5.profilemodel.PEType;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.r5.renderers.Renderer.RenderingStatus;
import org.hl7.fhir.r5.renderers.ResourceRenderer;
import org.hl7.fhir.r5.renderers.StructureDefinitionRenderer.MapStructureMode;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.StructureDefinitionRendererMode;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.utils.ElementDefinitionUtilities;
import org.hl7.fhir.r5.utils.ElementVisitor;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.utilities.*;
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

import static org.hl7.fhir.r5.utils.ElementVisitor.ElementVisitorInstruction.VISIT_CHILDREN;

public class StructureDefinitionRenderer extends CanonicalRenderer {

  private static final int EXAMPLE_UPPER_LIMIT = 50;
  private boolean noXigLink;

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
  public static final String ANCHOR_PREFIX_MAP = "";
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
  private final String packageId;

  private org.hl7.fhir.r5.renderers.StructureDefinitionRenderer sdr;
  private ResourceWrapper resE;

  public StructureDefinitionRenderer(IWorkerContext context, String packageId, String corePath, StructureDefinition sd, String destDir, IGKnowledgeProvider igp, List<SpecMapManager> maps, Set<String> allTargets, MarkDownProcessor markdownEngine, NpmPackage packge, List<FetchedFile> files, RenderingContext gen, boolean allInvariants,Map<String, Map<String, ElementDefinition>> mapCache, String specPath, String versionToAnnotate, List<RelatedIG> relatedIgs) {
    super(context, corePath, sd, destDir, igp, maps, allTargets, markdownEngine, packge, gen, versionToAnnotate, relatedIgs);
    this.packageId = packageId;
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
      if (sd.hasExtension(ExtensionDefinitions.EXT_SUMMARY)) {
        return processMarkdown("Profile Summary", (PrimitiveType) sd.getExtensionByUrl(ExtensionDefinitions.EXT_SUMMARY).getValue());
      }

      if (sd.getDifferential() == null)
        return "<p>" + gen.formatPhrase(RenderingI18nContext.STRUC_DEF_NO_SUMMARY) + "</p>";

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
      StringBuilder res = new StringBuilder("<a name=\""+(all ? "a" : "s")+"-summary\"> </a>\r\n<p><b>\r\n" + (gen.formatPhrase(RenderingI18nContext.GENERAL_SUMM)) + "\r\n</b></p>\r\n");      
      if (ExtensionUtilities.hasExtension(sd, ExtensionDefinitions.EXT_SUMMARY)) {
        Extension v = ExtensionUtilities.getExtension(sd, ExtensionDefinitions.EXT_SUMMARY);
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
            res.append(gen.formatPhrase(RenderingI18nContext.SD_SUMMARY_MANDATORY, gen.toStr(requiredOutrights), (requiredOutrights > 1 ? (Utilities.pluralizeMe(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_ELEMENT))) : (gen.formatPhrase(RenderingI18nContext.STRUC_DEF_ELEMENT)))));
            if (requiredNesteds > 0)
              res.append(gen.formatPhrase(RenderingI18nContext.SD_SUMMARY_NESTED_MANDATORY, gen.toStr(requiredNesteds), requiredNesteds > 1 ? (Utilities.pluralizeMe(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_ELEMENT))) : (gen.formatPhrase(RenderingI18nContext.STRUC_DEF_ELEMENT))));
          }
          if (supports > 0) {
            if (started)
              res.append("<br/> ");
            started = true;
            res.append(gen.formatPhrase(RenderingI18nContext.SD_SUMMARY_MUST_SUPPORT, gen.toStr(supports), supports > 1 ? (Utilities.pluralizeMe(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_ELEMENT))) : (gen.formatPhrase(RenderingI18nContext.STRUC_DEF_ELEMENT))));
          }
          if (fixeds > 0) {
            if (started)
              res.append("<br/> ");
            started = true;
            res.append(gen.formatPhrase(RenderingI18nContext.SD_SUMMARY_FIXED, gen.toStr(fixeds), fixeds > 1 ? (Utilities.pluralizeMe(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_ELEMENT))) : (gen.formatPhrase(RenderingI18nContext.STRUC_DEF_ELEMENT))));
          }
          if (prohibits > 0) {
            if (started)
              res.append("<br/> ");
            started = true;
            res.append(gen.formatPhrase(RenderingI18nContext.SD_SUMMARY_PROHIBITED, gen.toStr(prohibits), prohibits > 1 ? (Utilities.pluralizeMe(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_ELEMENT))) : (gen.formatPhrase(RenderingI18nContext.STRUC_DEF_ELEMENT))));
          }
          res.append("</p>");
        }

        if (!refs.isEmpty()) {
          res.append("<p><b>" + (gen.formatPhrase(RenderingI18nContext.STRUC_DEF_STRUCTURES)) + "</b></p>\r\n<p>" + (gen.formatPhrase(RenderingI18nContext.STRUC_DEF_THIS_REFERS)) + ":</p>\r\n<ul>\r\n");
          for (String s : refs)
            res.append(s);
          res.append("\r\n</ul>\r\n\r\n");
        }
        if (!ext.isEmpty()) {
          res.append("<p><b>" + (gen.formatPhrase(RenderingI18nContext.STRUC_DEF_EXTENSIONS)) + "</b></p>\r\n<p>" + (gen.formatPhrase(RenderingI18nContext.STRUC_DEF_REFERS_EXT)) + ":</p>\r\n<ul>\r\n");
          for (String s : ext)
            res.append(s);
          res.append("\r\n</ul>\r\n\r\n");
        }
        if (!slices.isEmpty()) {
          res.append("<p><b>" + (gen.formatPhrase(RenderingI18nContext.STRUC_DEF_SLIC)) + "</b></p>\r\n<p>" + gen.formatPhrase(RenderingI18nContext.SD_SUMMARY_SLICES, "<a href=\"" + corePath + "profiling.html#slices\">", "</a>") + ":</p>\r\n<ul>\r\n");
          for (String s : slices)
            res.append(s);
          res.append("\r\n</ul>\r\n\r\n");
        }
      }
      if (ExtensionUtilities.hasExtension(sd, ExtensionDefinitions.EXT_FMM_LEVEL)) {
        // Use hard-coded spec link to point to current spec because DSTU2 had maturity listed on a different page
        res.append("<p><b><a class=\"fmm\" href=\"http://hl7.org/fhir/versions.html#maturity\" title=\"Maturity Level\">" + (gen.formatPhrase(RenderingI18nContext.CANON_REND_MATURITY)) + "</a></b>: " + ExtensionUtilities.readStringExtension(sd, ExtensionDefinitions.EXT_FMM_LEVEL) + "</p>\r\n");
      }

      return res.toString();
    } catch (Exception e) {
      return "<p><i>" + Utilities.escapeXml(e.getMessage()) + "</i></p>";
    }
  }

  private String extensionSummary() {
    boolean isMod = ProfileUtilities.isModifierExtension(sd);
    if (ProfileUtilities.isSimpleExtension(sd)) {
      ElementDefinition value = sd.getSnapshot().getElementByPath("Extension.value");
      return "<p>"+
          gen.formatPhrase(isMod ? RenderingI18nContext.SDR_EXTENSION_SUMMARY_MODIFIER : RenderingI18nContext.SDR_EXTENSION_SUMMARY , value.typeSummary(), Utilities.stripPara(processMarkdown("ext-desc", sd.getDescriptionElement())))+
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
      b.append("<p>"+gen.formatPhrase(RenderingI18nContext.TEXT_ICON_EXTENSION_COMPLEX)+": "+html+"</p><ul data-fhir=\"generated-heirarchy\">");
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
      return "<li>" +gen.formatPhrase(RenderingI18nContext.SD_SUMMARY_SLICE_NONE, path) + "</li>\r\n";
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
      throw new Exception(gen.formatPhrase(RenderingI18nContext.SDR_MULTIPLE_PROFILES)+" (#1)"); 
    String url = profiles.get(0).getValue();
    StructureDefinition ed = context.fetchResource(StructureDefinition.class, url);
    if (ed == null)
      return "<li>" + gen.formatPhrase(RenderingI18nContext.SD_SUMMARY_MISSING_EXTENSION, url) + "</li>";
    if (ed.getWebPath() == null)
      return "<li><a href=\"" + "extension-" + ed.getId().toLowerCase() + ".html\">" + url + "</a>" + (modifier ? " (<b>" + (gen.formatPhrase(RenderingI18nContext.STRUC_DEF_MODIF)) + "</b>) " : "") + "</li>\r\n";
    else
      return "<li><a href=\"" + Utilities.escapeXml(ed.getWebPath()) + "\">" + url + "</a>" + (modifier ? " (<b>" + (gen.formatPhrase(RenderingI18nContext.STRUC_DEF_MODIF)) + "</b>) " : "") + "</li>\r\n";
  }

  private String describeProfile(String url) throws Exception {
    if (url.startsWith("http://hl7.org/fhir/StructureDefinition/") && (igp.isDatatype(url.substring(40)) || igp.isResource(url.substring(40)) || "Resource".equals(url.substring(40))))
      return null;

    StructureDefinition ed = context.fetchResource(StructureDefinition.class, url);
    if (ed == null)
      return "<li>" + gen.formatPhrase(RenderingI18nContext.SD_SUMMARY_MISSING_PROFILE, url) + "</li>";
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
    throw new FHIRException(gen.formatPhrase(RenderingI18nContext.SDR_NOT_GEN, fixed.getClass().getName())); 
  }

  private String summarise(ContactPoint cp) {
    return cp.getValue();
  }


  private String summarise(Quantity quantity) {
    String cu = "";
    if ("http://unitsofmeasure.org/".equals(quantity.getSystem()))
      cu = " (" + (gen.formatPhrase(RenderingI18nContext.GENERAL_UCUM)) + ": " + quantity.getCode() + ")";
    if ("http://snomed.info/sct".equals(quantity.getSystem()))
      cu = " (" + (gen.formatPhrase(RenderingI18nContext.STRUC_DEF_SNOMED_CODE)) + ": " + quantity.getCode() + ")";
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
      throw new FHIRException(gen.formatPhrase(RenderingI18nContext.SDR_NOT_DONE_CC)); 
    }
  }

  private String summarise(Coding coding) throws FHIRException {
    if ("http://snomed.info/sct".equals(coding.getSystem()))
      return "" + (gen.formatPhrase(RenderingI18nContext.STRUC_DEF_SNOMED_CODE)) + " " + coding.getCode() + (!coding.hasDisplay() ? "" : "(\"" + gen.getTranslated(coding.getDisplayElement()) + "\")");
    if ("http://loinc.org".equals(coding.getSystem()))
      return "" + (gen.formatPhrase(RenderingI18nContext.STRUC_DEF_LOINC)) + " " + coding.getCode() + (!coding.hasDisplay() ? "" : "(\"" + gen.getTranslated(coding.getDisplayElement()) + "\")");
    if ("http://unitsofmeasure.org/".equals(coding.getSystem()))
      return " (" + (gen.formatPhrase(RenderingI18nContext.GENERAL_UCUM)) + ": " + coding.getCode() + ")";
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
      if (ctxt.hasExtension(ExtensionDefinitions.EXT_APPLICABLE_VERSION)) {
        li.tx(" (");
        renderVersionRange(li, ctxt.getExtensionByUrl(ExtensionDefinitions.EXT_APPLICABLE_VERSION));
        li.tx(")");
          
      }
    }
    return new XhtmlComposer(XhtmlComposer.HTML).compose(ul);
  }

  private void renderVersionRange(XhtmlNode x, Extension ext) {
    String sv = ext.hasExtension("startFhirVersion") ? ext.getExtensionString("startFhirVersion") : null;
    String ev = ext.hasExtension("endFhirVersion") ? ext.getExtensionString("endFhirVersion") : null;
    if (ev != null && ev.equals(sv)) {
      x.tx(gen.formatPhrase(RenderingI18nContext.SDR_VER_FOR, VersionUtilities.getNameForVersion(ev))); 
    } else if (ev != null && sv != null) {
      x.tx(gen.formatPhrase(RenderingI18nContext.SDR_VER_RANGE, VersionUtilities.getNameForVersion(sv), VersionUtilities.getNameForVersion(ev))); 
    } else if (ev == null && sv != null) {
      x.tx(gen.formatPhrase(RenderingI18nContext.SDR_VER_ON, VersionUtilities.getNameForVersion(sv))); 
    } else if (ev == null && sv != null) {
      x.tx(gen.formatPhrase(RenderingI18nContext.SDR_VER_BEF, VersionUtilities.getNameForVersion(ev))); 
    } else {
      x.tx(gen.formatPhrase(RenderingI18nContext.SDR_VER_UNK)); 
    }
  }

  public String diff(String defnFile, Set<String> outputTracker, boolean toTabs, StructureDefinitionRendererMode mode, boolean all) throws IOException, FHIRException, org.hl7.fhir.exceptions.FHIRException {
    if (sd.getDifferential().getElement().isEmpty())
      return "";
    else {
      sdr.getContext().setStructureMode(mode);
      return new XhtmlComposer(XhtmlComposer.HTML).compose(sdr.generateTable(new RenderingStatus(), defnFile, sd, true, destDir, false, sd.getId(), false, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, false, outputTracker, false, gen.withUniqueLocalPrefix(all ? mc(mode)+"a" : mc(mode)), toTabs ? ANCHOR_PREFIX_DIFF : ANCHOR_PREFIX_SNAP, resE, all ? "DA" : "D"));
    }
  }

  private String mc(StructureDefinitionRendererMode mode) {
    switch (mode) {
    case BINDINGS: return "b";
    case DATA_DICT: return "d";
    case OBLIGATIONS: return "o";
    case SUMMARY: return "";
    default:return "";
    }
  }

  public String eview(String defnFile, Set<String> outputTracker, boolean toTabs, StructureDefinitionRendererMode mode, boolean all) throws IOException, FHIRException, org.hl7.fhir.exceptions.FHIRException {
   return new XhtmlComposer(XhtmlComposer.HTML).compose(sdr.buildElementTable(new RenderingStatus(), defnFile, sd, destDir, false, sd.getId(), false, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, false, outputTracker, false, gen.withUniqueLocalPrefix(all ? mc(mode)+"ea" : mc(mode)+"e"), toTabs ? ANCHOR_PREFIX_DIFF : ANCHOR_PREFIX_SNAP, resE));
  }

  public String snapshot(String defnFile, Set<String> outputTracker, boolean toTabs, StructureDefinitionRendererMode mode, boolean all) throws IOException, FHIRException, org.hl7.fhir.exceptions.FHIRException {
    if (sd.getSnapshot().getElement().isEmpty())
      return "";
    else {
      sdr.getContext().setStructureMode(mode);
      return new XhtmlComposer(XhtmlComposer.HTML).compose(sdr.generateTable(new RenderingStatus(), defnFile, sd, false, destDir, false, sd.getId(), true, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, true, outputTracker, false, gen.withUniqueLocalPrefix(all ? mc(mode)+"sa" : mc(mode)+"s"), toTabs ? ANCHOR_PREFIX_SNAP : ANCHOR_PREFIX_SNAP, resE, all ? "SA" : "S"));
    }
  }

  public String obligations(String defnFile, Set<String> outputTracker, boolean toTabs, StructureDefinitionRendererMode mode, boolean all) throws IOException, FHIRException, org.hl7.fhir.exceptions.FHIRException {
    if (sd.getSnapshot().getElement().isEmpty())
      return "";
    else {
      sdr.getContext().setStructureMode(mode);
      return new XhtmlComposer(XhtmlComposer.HTML).compose(sdr.generateTable(new RenderingStatus(), defnFile, sd, false, destDir, false, sd.getId(), true, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, false, outputTracker, false, gen.withUniqueLocalPrefix(all ? mc(mode)+"oa" : mc(mode)+"o"), toTabs ? ANCHOR_PREFIX_SNAP : ANCHOR_PREFIX_SNAP, resE, all ? "OA" : "O"));
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
      org.hl7.fhir.utilities.xhtml.XhtmlNode table = sdr.generateTable(new RenderingStatus(), defnFile, sdCopy, false, destDir, false, sdCopy.getId(), true, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, true, outputTracker, false, gen.withUniqueLocalPrefix(all ? mc(mode)+"ka" :mc(mode)+"k"), toTabs ? ANCHOR_PREFIX_KEY : ANCHOR_PREFIX_SNAP, resE, all ? "KA" : "K");

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
      org.hl7.fhir.utilities.xhtml.XhtmlNode table = sdr.generateTable(new RenderingStatus(), defnFile, sdCopy, false, destDir, false, sdCopy.getId(), true, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, false, outputTracker, true, gen.withUniqueLocalPrefix(all ? mc(mode)+"ma" :mc(mode)+"m"), toTabs ? ANCHOR_PREFIX_MS : ANCHOR_PREFIX_SNAP, resE, all ? "MA" : "M");

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
      org.hl7.fhir.utilities.xhtml.XhtmlNode table = sdr.generateTable(new RenderingStatus(), defnFile, sdCopy, false, destDir, false, sdCopy.getId(), true, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, true, outputTracker, true, gen, ANCHOR_PREFIX_KEY, resE, "KK");

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
      return new XhtmlComposer(XhtmlComposer.HTML).compose(sdr.generateGrid(defnFile, sd, destDir, false, sd.getId(), corePath, "", outputTracker, false));
  }

  public String txDiff(boolean withHeadings, boolean mustSupportOnly) throws FHIRException, IOException {
    List<String> txlist = new ArrayList<String>();
    boolean hasFixed = false;
    boolean hasDesc = false; // this is currently unused - have to figure out whether we want to try and show descriptions or not
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
        hasDesc = hasDesc || ed.getBinding().hasDescription();
        txlist.add(id);
        txmap.put(id, ed);
      }
    }
    if (txlist.isEmpty())
      return "";
    else {
      XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
      if (withHeadings) {
        div.h4().tx(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_TERM_BIND));
      }
      XhtmlNode tbl = div.table("list").setAttribute("data-fhir", "generated-heirarchy");
      txItemHeadings(tbl, false);
      for (String path : txlist) {
        txItem(txmap, tbl, path, sd.getUrl(), false);
      }
      return new XhtmlComposer(false, true).compose(div.getChildNodes());
    }
  }

  private void txItemHeadings(XhtmlNode tbl, boolean hasFixed) {
    XhtmlNode tr = tbl.tr();
    tr.td().b().tx(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_PATH));
    tr.td().b().tx(gen.formatPhrase(RenderingI18nContext.GENERAL_STATUS));
    tr.td().b().tx(gen.formatPhrase(RenderingI18nContext.GENERAL_USAGE));
    tr.td().b().tx(hasFixed ? gen.formatPhrase(RenderingI18nContext.STRUC_DEF_VALUESET_CODE) : gen.formatPhrase(RenderingI18nContext.STRUC_DEF_VALUESET));
    tr.td().b().tx(gen.formatPhrase(RenderingI18nContext.GENERAL_VER));
    tr.td().b().tx(gen.formatPhrase(RenderingI18nContext.GENERAL_SOURCE));
  }

  public String tx(boolean withHeadings, boolean mustSupportOnly, boolean keyOnly) throws FHIRException, IOException {
    List<String> txlist = new ArrayList<String>();
    boolean hasFixed = false;
    boolean hasDesc = false; // this is currently unused - have to figure out whether we want to try and show descriptions or not
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
        hasDesc = hasDesc || ed.getBinding().hasDescription();
      }
    }
    if (txlist.isEmpty())
      return "";
    else {
      XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
      if (withHeadings) {
        div.h4().tx(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_TERM_BINDS));
      }
      XhtmlNode tbl = div.table("list").setAttribute("data-fhir", "generated-heirarchy");
      txItemHeadings(tbl, false);
      for (String path : txlist) {
        txItem(txmap, tbl, path, sd.getUrl(), false);
      }
      return new XhtmlComposer(false, true).compose(div.getChildNodes());
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

  public void txItem(Map<String, ElementDefinition> txmap, XhtmlNode tbl, String path, String url, boolean hasDesc) throws FHIRException, IOException {
    ElementDefinition ed = txmap.get(path);
    ElementDefinitionBindingComponent tx = ed.getBinding();
    boolean strengthInh = false;
    BindingStrength strength = null;
    if (tx.hasStrength()) {
      strength = tx.getStrength();
    } else if (ed.hasUserData(UserDataNames.SNAPSHOT_DERIVATION_POINTER)) {
      ElementDefinitionBindingComponent txi = ((ElementDefinition) ed.getUserData(UserDataNames.SNAPSHOT_DERIVATION_POINTER)).getBinding();
      strength = txi.getStrength();
      strengthInh = true;
    }
    XhtmlNode tr = tbl.tr();
    tr.td().tx(Utilities.insertBreakingSpaces(path, new HashSet<>(Arrays.asList('.'))));
    tr.td().tx("Base");
    if (strength == null) {
      tr.td();
    } else {
      tr.td().ah(Utilities.pathURL(corePath, "terminologies.html#" + tx.getStrengthElement())).style("opacity: "+opacityStr(strengthInh)).tx(gen.getTranslated(tx.getStrengthElement()));
    }
    XhtmlNode td = tr.td();
    if (tx.hasDescription()) {
      td.setAttribute("title", tx.getDescription());
    }

    boolean inherited = ed.hasUserData(UserDataNames.SNAPSHOT_DERIVATION_POINTER);
    String uri = null;
    if (tx.getValueSet() != null) {
      uri = tx.getValueSet().trim();
    }
    ValueSet vs = context.findTxResource(ValueSet.class, canonicalise(uri));
    if (vs == null) {
      String name = getSpecialValueSetName(uri);
      if (name != null) {
        td.ah(getSpecialValueSetUrl(uri)).style("opacity: "+opacityStr(inherited)).tx(name);
      } else {
        BindingResolution br = igp.resolveActualUrl(uri);
        if (br.url == null) {
          td.markdown(br.display, "binding");
        } else if (Utilities.isAbsoluteUrlLinkable(br.url)) {
          td.ah(br.url).style("opacity: "+opacityStr(inherited)).tx(br.display);
          td.button("btn-copy", gen.formatPhrase(RenderingI18nContext.SDR_CLICK_COPY)).setAttribute("data-clipboard-text", tx.getValueSet());
        } else {
          td.ah(prefix + br.url).style("opacity: "+opacityStr(inherited)).tx(br.display);
          td.button("btn-copy", gen.formatPhrase(RenderingI18nContext.SDR_CLICK_COPY)).setAttribute("data-clipboard-text", tx.getValueSet());
        }
        showVersion(tr.td(), uri, null);
        tr.td().tx("Unknown");
      }
    } else {
      String p = vs.getWebPath();
      if (p == null) {
        td.ah("??").style("opacity: "+opacityStr(inherited)).tx(gen.getTranslated(vs.getTitleElement(), vs.getNameElement()) + " (" + (gen.formatPhrase(RenderingI18nContext.STRUC_DEF_MISSING_LINK)) + ")");
      } else {
        td.ah(p).style("opacity: "+opacityStr(inherited)).tx(gen.getTranslated(vs.getTitleElement(), vs.getNameElement()));
      }
      td.button("btn-copy", gen.formatPhrase(RenderingI18nContext.SDR_CLICK_COPY)).setAttribute("data-clipboard-text", tx.getValueSet());
      if (vs.hasUserData(UserDataNames.render_external_link)) {
        td.img("external.png", ".");
      }
      showVersion(tr.td(), uri, vs);

      String src = null;
      String link = null;
      if (vs.hasSourcePackage()) {
        if (VersionUtilities.isCorePackage(vs.getSourcePackage().getId())) {
          src = gen.formatPhrase(RenderingI18nContext.SDR_SRC_FHIR);
          link = gen.getLink(RenderingContext.KnownLinkType.SPEC, true);
        } else if (!Utilities.isAbsoluteUrlLinkable(vs.getWebPath())) {
          src = gen.formatPhrase(RenderingI18nContext.SDR_SRC_IG);
          link = null;
        } else {
          String pname = getSourcePackageName(vs.getSourcePackage());
          src = pname + " v" + presentVersion(vs.getSourcePackage());
          link = vs.getSourcePackage().getWeb();
        }
      } else if (vs.hasUserData(UserDataNames.render_external_link)) {
        src = vs.getUserString(UserDataNames.render_external_link);
        link = vs.getUserString(UserDataNames.render_external_link);
        try {
          src = new URL(src).getHost();
        } catch (Exception e) {
          // nothing
        }
      } else {
        src = "unknown?";
      }
      tr.td().ahOrNot(link).tx(src);

//        String system = ValueSetUtilities.getAllCodesSystem(vs);
//        if (system != null) {
//          SystemReference sr = CodeSystemUtilities.getSystemReference(system, context);
//          if (sr == null) {
//            brd.suffix = gen.formatPhrase(RenderingI18nContext.SDR_CODE_FROM, "<code>"+system+"</code>");
//          } else if (sr.isLocal() || (sr.getText() != null && sr.getText().equals(vs.getName()))) {
//            brd.suffix = "";
//          } else if (sr.getLink() == null) {
//            brd.suffix = gen.formatPhrase(RenderingI18nContext.SDR_CODE_FROM, sr.getText()+" (<code>"+system+"</code>)");
//          } else {
//            brd.suffix = gen.formatPhrase(RenderingI18nContext.SDR_CODE_FROM, "<a href=\""+sr.getLink()+"\">"+sr.getText()+"</a>");
//          }
//        }
      }

//    if (link != null) {
//      if (Utilities.isAbsoluteUrlLinkable(link)) {
//        b.append("<div>"+gen.formatPhrase(RenderingI18nContext.SDR_FROM, "<a href=\""+Utilities.escapeXml(link)+"\">"+Utilities.escapeXml(link)+"</a></div>"));
//      } else {
//        b.append("<div>"+gen.formatPhrase(RenderingI18nContext.SDR_FROM, Utilities.escapeXml(link)+"</div>"));
//      }
//    }
//    AdditionalBindingsRenderer abr = new AdditionalBindingsRenderer(igp, corePath, sd, path, gen, this, sdr);
//    if (tx.hasExtension(ExtensionDefinitions.EXT_MAX_VALUESET)) {
//      abr.seeMaxBinding(ExtensionUtilities.getExtension(tx, ExtensionDefinitions.EXT_MAX_VALUESET));
//    }
//    if (tx.hasExtension(ExtensionDefinitions.EXT_MIN_VALUESET)) {
//      abr.seeMinBinding(ExtensionUtilities.getExtension(tx, ExtensionDefinitions.EXT_MIN_VALUESET));
//    }
//    if (tx.hasExtension(ExtensionDefinitions.EXT_BINDING_ADDITIONAL)) {
//      abr.seeAdditionalBindings(tx.getExtensionsByUrl(ExtensionDefinitions.EXT_BINDING_ADDITIONAL));
//    }
//    if (abr.hasBindings()) {
//      XhtmlNode x = new XhtmlNode(NodeType.Element, "table");
//      x.setAttribute("class", "grid");
//      abr.render(x.getChildNodes(), true);
//      b.append(new XhtmlComposer(true, true).compose(x));
//    }
//    b.append("</td>");
//    b.append("</tr>\r\n");
    if (hasDesc) {
      tr.td().markdown(tx.getDescription(), "binding description");
    }
  }

  private String presentVersion(PackageInformation sourcePackage) {
    if (sourcePackage.getCanonical() != null) {
      switch (sourcePackage.getCanonical()) {
        case "http://fhir.org/packages/fhir.dicom":
          String[] vp = sourcePackage.getVersion().split("\\.");
          return vp[0] + String.valueOf((char) ('a' + (vp[1].charAt(0) - '1')));
        default:
      }
    }
    return VersionUtilities.getMajMin(sourcePackage.getVersion());
  }

  private String getSourcePackageName(PackageInformation sourcePackage) {
    if (sourcePackage.getCanonical() != null) {
      switch (sourcePackage.getCanonical()) {
        case "http://terminology.hl7.org":
          return "THO";
        case "http://hl7.org/fhir/us/core":
          return "US Core";
        case "http://fhir.org/packages/us.nlm.vsac":
          return "VSAC";
        case "http://fhir.org/packages/fhir.dicom":
          return "DICOM";
        default:
      }
    }
    if (sourcePackage.getName() == null) {
      return sourcePackage.getId();
    } else if (sourcePackage.getName().contains("(")) {
      return sourcePackage.getName().substring(0, sourcePackage.getName().indexOf("(")).replace(")", "");
    } else {
      return sourcePackage.getName();
    }
  }

  private void showVersion(XhtmlNode td, String uri, ValueSet vs) {
    String statedVersion = uri != null && uri.contains("|") ? uri.substring(uri.indexOf("|")+1) : null;
    String actualVersion = vs == null ? null : vs.getVersion();
    boolean fromPackages = vs == null ? false : vs.hasSourcePackage();
    boolean fromThisPackage = vs == null ? false : !Utilities.isAbsoluteUrlLinkable(vs.getWebPath());
    ResourceRenderer.renderVersionReference(gen, vs, statedVersion, actualVersion, fromPackages, td, fromThisPackage, gen.formatPhrase(RenderingContext.GENERAL_VALUESET), RenderingI18nContext.VS_VERSION_NOTHING_TEXT);
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
        return gen.formatPhrase(RenderingI18nContext.SDR_ALL_ELEM);
      else if (constraint.hasSource() && constraint.getSource().equals("http://hl7.org/fhir/StructureDefinition/Extension"))
        return gen.formatPhrase(RenderingI18nContext.SDR_ALL_EXT);
      else
        return String.join(", ", elements);
    }
    public boolean isBold() {
      if (constraint.hasSource() && constraint.getSource().equals("http://hl7.org/fhir/StructureDefinition/Element"))
        return true;
      else if (constraint.hasSource() && constraint.getSource().equals("http://hl7.org/fhir/StructureDefinition/Extension"))
        return true;
      else
        return false;
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

  public String invOldMode(boolean withHeadings, int genMode) throws IOException {
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
      XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
      if (withHeadings) {
        div.h4().tx(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_CONSTRAINTS));
      }
      XhtmlNode tbl = div.table("list", true).setAttribute("data-fhir", "generated-heirarchy");
      XhtmlNode tr = tbl.tr();
      tr.tdW(60).b().tx(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_ID));
      tr.td().b().tx(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_GRADE));
      tr.td().b().tx(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_PATHS));
      tr.td().b().tx(gen.formatPhrase(RenderingI18nContext.GENERAL_DESC));
      tr.td().b().tx(gen.formatPhrase(RenderingI18nContext.SEARCH_PAR_EXP));

      List<String> keys = new ArrayList<>(constraintMap.keySet());

      Collections.sort(keys, new ConstraintKeyComparator());
      for (String key : keys) {
        ConstraintInfo ci = constraintMap.get(key);
        for (ConstraintVariation cv : ci.getVariations()) {
          ElementDefinitionConstraintComponent inv = cv.getConstraint();
          if (!inv.hasSource() || inv.getSource().equals(sd.getUrl()) || allInvariants || genMode!=GEN_MODE_DIFF ) {
            tr = tbl.tr();
            tr.td().tx(inv.getKey());
            tr.td().tx(grade(inv));
            if (cv.isBold()) {
              tr.td().b().tx(cv.getIds());
            } else {
              tr.td().tx(cv.getIds());
            }
            XhtmlNode td = tr.td();
            td.tx(gen.getTranslated(inv.getHumanElement()));
            if (inv.hasRequirements()) {
              td.br();
              td.tx(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_REQUIREMENTS));
              td.tx(": ");
              td.markdown(gen.getTranslated(inv.getRequirementsElement()), "requirements");
            }
            tr.td().code(inv.getExpression());
          }
        }
      }
      return new XhtmlComposer(false, true).compose(div.getChildNodes());
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
    if (inv.hasExtension(ExtensionDefinitions.EXT_BEST_PRACTICE)) {
      return gen.formatPhrase(RenderingI18nContext.SDR_BP);
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
    p.tx(gen.formatPhrase(RenderingI18nContext.SDR_GUIDANCE_PFX));   
    p.ah("https://build.fhir.org/ig/FHIR/ig-guidance/readingIgs.html#data-dictionaries").tx(gen.formatPhrase(RenderingI18nContext.SDR_GUIDANCE_HERE));
    p.tx(gen.formatPhrase(RenderingI18nContext.SDR_GUIDANCE_SFX));   
    XhtmlNode t = x.table("dict", false).markGenerated(true);

    List<ElementDefinition> elements = elementsForMode(mode);

    sdr.renderDict(new RenderingStatus(), sd, elements, t, incProfiledOut, mode, anchorPrefix, resE);
    
    return new XhtmlComposer(false, false).compose(x.getChildNodes());
  }

  public String mappings(String defnFile, Set<String> outputTracker) throws FHIRException, IOException {
    sdr.getContext().setStructureMode(StructureDefinitionRendererMode.MAPPINGS);
    sdr.setMappingsMode(MapStructureMode.IN_LIST);
    sdr.getMappingTargets().clear();
    for (FetchedFile f : files) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() instanceof StructureDefinition) {
          sdr.getMappingTargets().add((StructureDefinition) r.getResource());
        }
      }
    }
    XhtmlNode intTable = sdr.generateTable(new RenderingStatus(), defnFile, sd, false, destDir, false, sd.getId(), true, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, false, 
        outputTracker, false, gen, ANCHOR_PREFIX_MAP, resE, "M");

    sdr.setMappingsMode(MapStructureMode.NOT_IN_LIST);

    XhtmlNode extTable = sdr.generateTable(new RenderingStatus(), defnFile, sd, false, destDir, false, sd.getId(), true, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, false, 
        outputTracker, false, gen, ANCHOR_PREFIX_MAP, resE, "M");

    sdr.setMappingsMode(MapStructureMode.OTHER);

    XhtmlNode otherTable = sdr.generateTable(new RenderingStatus(), defnFile, sd, false, destDir, false, sd.getId(), true, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, false, 
        outputTracker, false, gen, ANCHOR_PREFIX_MAP, resE, "M");

    if (intTable == null && extTable == null && otherTable == null) {
      return "<p>"+sdr.getContext().formatPhrase(RenderingI18nContext.STRUC_DEF_NO_MAPPINGS)+"</p>";
    } else {
      StringBuilder b = new StringBuilder();
      
      b.append("<h4>Mappings to Structures in this Implementation Guide</h4>\r\n");
      if (intTable == null) {
        b.append("<p>No Mappings Found</p>\r\n");                
      } else {
        b.append(new XhtmlComposer(false, false).compose(intTable));        
      }
      
      b.append("<h4>Mappings to other Structures</h4>\r\n");
      if (extTable == null) {
        b.append("<p>No Mappings Found</p>\r\n");                
      } else {
        b.append(new XhtmlComposer(false, false).compose(extTable));        
      }

      b.append("<h4>Other Mappings</h4>\r\n");
      if (otherTable == null) {
        b.append("<p>No Mappings Found</p>\r\n");                
      } else {
        b.append(new XhtmlComposer(false, false).compose(otherTable));        
      }
      return b.toString();
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
    b.append(gen.formatPhrase(RenderingI18nContext.SDR_OFF_URL)+":" + "\r\n"); 
    b.append("</p>\r\n");
    b.append("<pre class=\"profile-url\">" + sd.getUrl() + "</pre>\r\n");
    b.append("<div class=\"profile-description\">\r\n");
    b.append(processMarkdown("description", sd.getDescriptionElement()));
    b.append("</div>\r\n");
    if (sd.getDerivation() == TypeDerivationRule.CONSTRAINT) {
      b.append("<p class=\"profile-derivation\">\r\n");
      StructureDefinition sdb = context.fetchResource(StructureDefinition.class, sd.getBaseDefinition());
      if (sdb != null)
        b.append(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_PROFILE_BUILDS) + " <a href=\"" + Utilities.escapeXml(sdb.getWebPath()) + "\">" + gen.getTranslated(sdb.getNameElement()) + "</a>.");
      else
        b.append(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_PROFILE_BUILDS) + " " + sd.getBaseDefinition() + ".");
      b.append("</p>\r\n");
    }
    b.append("<p class=\"profile-publication\">\r\n");
    b.append(gen.formatPhrase(RenderingI18nContext.SD_SUMMARY_PUBLICATION, renderDate(sd.getDateElement()), gen.getTranslated(sd.getStatusElement()), gen.getTranslated(sd.getPublisherElement()))+"\r\n");
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
      b.append(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_DERIVED_PROFILE) + " ");
      listResources(b, derived);
      b.append("</p>\r\n");
    }
    List<StructureDefinition> users = findUses(crl);
    if (!users.isEmpty()) {
      b.append("<p>\r\n");
      b.append(gen.formatPhrase(RenderingI18nContext.STRUC_DEF_REFER_PROFILE)+" ");
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
      b.append(gen.formatPhrase(RenderingI18nContext.SDR_FROM_ELEM, corePath + "extensibility.html")+"\r\n"); 

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
          b.append("<span style=\"color: Gray\">// " + tail(child.getPath()) + ": <span style=\"color: navy; opacity: 0.8\">" + Utilities.escapeXml(child.getShort()) + "</span>. "+gen.formatPhrase(RenderingI18nContext.SDR_ONE_OF, child.getType().size()) + ":</span>\r\n"); 
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
    if (defPage != null && defPage.contains("|"))
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
      if (!last) {
        b.append(",");
      }
    }

    b.append(" <span style=\"color: Gray\">//</span>");

    // 3. optionality
    writeCardinality(unbounded, b, elem);

    // 4. doco
    if (!elem.hasFixed()) {
      if (elem.hasBinding() && elem.getBinding().hasValueSet()) {
        ValueSet vs = context.findTxResource(ValueSet.class, elem.getBinding().getValueSet());
        if (vs != null) {
          b.append(" <span style=\"color: navy; opacity: 0.8\"><a href=\"" + corePath + vs.getUserData(UserDataNames.render_filename) + ".html\" style=\"color: navy\">" + Utilities.escapeXml(elem.getShort()) + "</a></span>");
        } else {
          b.append(" <span style=\"color: navy; opacity: 0.8\"><a href=\"" + elem.getBinding().getValueSet() + ".html\" style=\"color: navy\">" + Utilities.escapeXml(elem.getShort()) + "</a></span>");
        }
      } else {
        b.append(" <span style=\"color: navy; opacity: 0.8\">" + Utilities.escapeXml(elem.getShort()) + "</span>");
      }
    }

    b.append("\r\n");

    if (delayedClose) {
      int c = 0;
      int l = lastChild(children);
      boolean extDone = false;
      for (ElementDefinition child : children) {
        if (isExtension(child)) {
          if (!extDone) {
            generateCoreElemExtension(b, sd.getSnapshot().getElement(), child, children, indent + 1, pathName + "." + name, false, child.getType().get(0), ++c == l, complex);
          }
          extDone = true;
        } else if (child.hasSlicing()) {
          generateCoreElemSliced(b, sd.getSnapshot().getElement(), child, children, indent + 1, pathName + "." + name, false, child.hasType() ? child.getType().get(0) : null, ++c == l, complex);
        } else if (wasSliced(child, children)) {
          ; // nothing
        } else if (child.getType().size() == 1 || allTypesAreReference(child)) {
          generateCoreElem(b, elements, child, indent + 1, pathName + "." + name, false, child.getType().get(0), ++c == l, false);
        } else {
          if (!"0".equals(child.getMax())) {
            b.append("<span style=\"color: Gray\">// value[x]: <span style=\"color: navy; opacity: 0.8\">" + Utilities.escapeXml(child.getShort()) + "</span>. One of these " + Integer.toString(child.getType().size()) + ":</span>\r\n");
            for (TypeRefComponent t : child.getType()) {
              generateCoreElem(b, elements, child, indent + 1, pathName + "." + name, false, t, ++c == l, false);
            }
          }
        }
      }
      b.append(indentS);
      b.append("}");
      if (unbounded) {
        b.append("]");
      }
      if (!last) {
        b.append(",");
      }
      b.append("\r\n");
    }
  }

  private String suffix(String link, String suffix) {
    if (link.contains("|")) {
      link = link.substring(0, link.indexOf("|"));
    }
    if (link.contains("#")) {
      return link;
    } else {
      return link + "#" + suffix;
    }
  }

  private void generateCoreElemSliced(StringBuilder b, List<ElementDefinition> elements, ElementDefinition elem, List<ElementDefinition> children, int indent, String pathName, boolean asValue, TypeRefComponent type, boolean last, boolean complex) throws Exception {
    if (elem.getMax().equals("0")) {
      return;
    }

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
    if (defPage != null && defPage.contains("|")) {
      defPage = defPage.substring(0, defPage.indexOf("|"));
    }
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
        b.append(gen.formatPhrase(RenderingI18nContext.SDR_FROM_ELEM, corePath + "extensibility.html")+"\r\n"); 
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
          b.append("<span style=\"color: Gray\">// value[x]: <span style=\"color: navy; opacity: 0.8\">" + Utilities.escapeXml(child.getShort()) + "</span>. "+gen.formatPhrase(RenderingI18nContext.SDR_ONE_OF, child.getType().size()) + ":</span>\r\n"); 
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
          b.append(gen.formatPhrase(RenderingI18nContext.SDR_UNK_EXT,  url) + "\r\n"); 
        else
          extchildren = getChildren(sdExt.getSnapshot().getElement(), sdExt.getSnapshot().getElementFirstRep());
      }

      ElementDefinition value = getValue(extchildren);
      if (value != null) {
        if (value.getType().size() == 1)
          generateCoreElem(b, elements, value, indent + 2, pathName + "." + en, false, value.getType().get(0), true, false);
        else {
          b.append("<span style=\"color: Gray\">// value[x]: <span style=\"color: navy; opacity: 0.8\">" + Utilities.escapeXml(value.getShort()) + "</span>. "+gen.formatPhrase(RenderingI18nContext.SDR_ONE_OF, value.getType().size()) + ":</span>\r\n"); 
          for (TypeRefComponent t : value.getType())
            generateCoreElem(b, elements, value, indent + 2, pathName + "." + en, false, t, t == value.getType().get(value.getType().size() - 1), false);
        }
      } else {
        b.append(gen.formatPhrase(RenderingI18nContext.SDR_NOT_HANDLED_EXT, "Not handled yet: complex extension " + url) + "\r\n"); 
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
      b.append(" <span style=\"color: brown\" title=\""+gen.formatPhrase(RenderingI18nContext.SDR_REQUIRED)+"\"><b>R!</b></span>");  
    if (unbounded && "1".equals(elem.getMax()))
      b.append(" <span style=\"color: brown\" title=\""+gen.formatPhrase(RenderingI18nContext.SDR_LIMITED_ONE)+"\"><b>"+gen.formatPhrase(RenderingI18nContext.SDR_ONLY_ONE)+"</b></span> "); 
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
      return Utilities.removePeriod(elem.getDefinition()) + " "+ gen.formatPhrase(RenderingI18nContext.SDR_MOD_SUPP); 
    else if (elem.getIsModifier())
      return Utilities.removePeriod(elem.getDefinition()) + " "+gen.formatPhrase(RenderingI18nContext.SDR_MOD); 
    else if (elem.getMustSupport())
      return Utilities.removePeriod(elem.getDefinition()) + " "+gen.formatPhrase(RenderingI18nContext.SDR_SUPP); 
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
    String s = " " + gen.formatPhrase(slicing.getOrdered() ? RenderingI18nContext.SDR_ANY_ORDER : RenderingI18nContext.SDR_SORTED, csv.toString(), (slicing.hasRules() ? slicing.getRules().getDisplay() : "")); 
    return s;
  }

  public String typeName(String lang, RenderingContext rc) {
    if (ExtensionUtilities.readBoolExtension(sd, ExtensionDefinitions.EXT_OBLIGATION_PROFILE_FLAG_NEW, ExtensionDefinitions.EXT_OBLIGATION_PROFILE_FLAG_OLD)) {
      return rc.formatPhrase(RenderingI18nContext.SDT_OBLIGATION_PROFILE); // "Obligation Profile";
    } else if ("Extension".equals(sd.getType())) {
      return rc.formatPhrase(RenderingI18nContext.SDT_EXTENSION); // "Extension"
    } else switch (sd.getKind()) {
    case COMPLEXTYPE:   return rc.formatPhrase(sd.getDerivation() == TypeDerivationRule.CONSTRAINT ? RenderingI18nContext.SDT_DT_PROF  : RenderingI18nContext.SDT_DT); // "DataType Constraint" : "DataType" ;
    case LOGICAL:       return rc.formatPhrase(sd.getDerivation() == TypeDerivationRule.CONSTRAINT ? RenderingI18nContext.SDT_LM_PROF  : RenderingI18nContext.SDT_LM); //"Logical Model" : "Logical Model Profile";
    case PRIMITIVETYPE: return rc.formatPhrase(sd.getDerivation() == TypeDerivationRule.CONSTRAINT ? RenderingI18nContext.SDT_PT_PROF  : RenderingI18nContext.SDT_PT); //"PrimitiveType Constraint" : "PrimitiveType";
    case RESOURCE:      return rc.formatPhrase(sd.getDerivation() == TypeDerivationRule.CONSTRAINT ? RenderingI18nContext.SDT_RES_PROF : RenderingI18nContext.SDT_RES); //"Resource Profile" : "Resource";
      default:          return rc.formatPhrase(RenderingI18nContext.SDT_DEF);
    }
  }
  
  public String references(String lang, RenderingContext lrc) throws FHIRFormatError, IOException {
    
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
      scanExtensions(invoked, sdt, ExtensionDefinitions.EXT_OBLIGATION_INHERITS_NEW);
      scanExtensions(invoked, sdt, ExtensionDefinitions.EXT_OBLIGATION_INHERITS_OLD);
      scanExtensions(imposed, sdt, ExtensionDefinitions.EXT_SD_IMPOSE_PROFILE);
      scanExtensions(compliedWith, sdt, ExtensionDefinitions.EXT_SD_COMPLIES_WITH_PROFILE);

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
    for (CanonicalResource cr : scanAllResources(CapabilityStatement.class, "CapabilityStatement")) {
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
          if (examples.size() < EXAMPLE_UPPER_LIMIT) {
            examples.put(Utilities.pathURL(specPath, p.getName()), p.getValue().asString());
          }
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
              if (examples.size() < EXAMPLE_UPPER_LIMIT) {
                examples.put(p, r.getTitle());
              }
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
          if (examples.size() < EXAMPLE_UPPER_LIMIT) {
            examples.put(r.getWebPath(), r.getUserString(UserDataNames.renderer_title));
          }
        }        
      }
    }

    if ("hl7.fhir.uv.extensions".equals(packageId)) {
      if (igp.getCoreExtensionMap().isEmpty()) {
        loadCoreExtensionMap();
      }
      List<IGKnowledgeProvider.ExtensionUsage> usages = igp.getCoreExtensionMap().get(sd.getUrl());
      if (usages != null) {
        for (IGKnowledgeProvider.ExtensionUsage u : usages) {
          if (examples.size() < EXAMPLE_UPPER_LIMIT) {
            examples.put(Utilities.pathURL(specPath, u.getUrl()), u.getName());
          }
        }
      }
    }

    StringBuilder b = new StringBuilder();
    String os = lrc.formatPhrase(RenderingI18nContext.SDT_ORIGINAL_SOURCE);
    if (sd.hasExtension(ExtensionDefinitions.EXT_WEB_SOURCE_OLD, ExtensionDefinitions.EXT_WEB_SOURCE_NEW)) {
      String url = ExtensionUtilities.readStringExtension(sd, ExtensionDefinitions.EXT_WEB_SOURCE_OLD, ExtensionDefinitions.EXT_WEB_SOURCE_NEW);
      if (Utilities.isAbsoluteUrlLinkable(url)) {
        b.append("<p><b>"+os+":</b> <a href=\""+Utilities.escapeXml(url)+"\">"+Utilities.escapeXml(Utilities.extractDomain(url))+"</a></p>\r\n");      
      } else {
        b.append("<p><b>"+os+"</b> <code>"+Utilities.escapeXml(url)+"</a></p>\r\n");        
      }
    } else if (sd.getMeta().hasSource() && Utilities.isAbsoluteUrlLinkable(sd.getMeta().getSource())) {
      b.append("<p><b>"+os+":</b> <a href=\""+sd.getMeta().getSource()+"\">"+Utilities.extractDomain(sd.getMeta().getSource())+"</a></p>\r\n");
    }
    String type = typeName(lang, lrc);
    b.append("<p><b>"+lrc.formatPhrase(RenderingI18nContext.SDR_USAGE)+":</b></p>\r\n<ul>\r\n");
    if (!base.isEmpty())
      b.append(" <li>"+Utilities.escapeXml(lrc.formatPhrase(RenderingI18nContext.SDR_DERIVED, type))+": " + refList(base, "base") + "</li>\r\n");
    if (!invoked.isEmpty()) {
      b.append(" <li>"+Utilities.escapeXml(lrc.formatPhrase(RenderingI18nContext.SDR_DRAW_IN, type))+": " + refList(invoked, "invoked") + "</li>\r\n");
    }
    if (!imposed.isEmpty()) {
      b.append(" <li>"+Utilities.escapeXml(lrc.formatPhrase(RenderingI18nContext.SDR_IMPOSE, type))+": " + refList(imposed, "imposed") + "</li>\r\n");
    }
    if (!compliedWith.isEmpty()) {
      b.append(" <li>"+Utilities.escapeXml(lrc.formatPhrase(RenderingI18nContext.SDR_COMPLY, type))+": " + refList(compliedWith, "compliedWith") + "</li>\r\n");
    }
    if (!refs.isEmpty()) {
      b.append(" <li>"+Utilities.escapeXml(lrc.formatPhrase(RenderingI18nContext.SDR_USE, type))+": " + refList(refs, "ref") + "</li>\r\n");
    }
    if (!trefs.isEmpty()) {
      b.append(" <li>"+Utilities.escapeXml(lrc.formatPhrase(RenderingI18nContext.SDR_REFER, type))+": " + refList(trefs, "tref") + "</li>\r\n");
    }
    if (!examples.isEmpty()) {
      b.append(" <li>"+Utilities.escapeXml(lrc.formatPhrase(RenderingI18nContext.SDR_EXAMPLE, type))+": " + refList(examples, "ex") + "</li>\r\n");
    }
    if (!searches.isEmpty()) {
      b.append(" <li>"+Utilities.escapeXml(lrc.formatPhrase(RenderingI18nContext.SDR_SEARCH, type))+": " + refList(searches, "sp") + "</li>\r\n");
    }
    if (!capStmts.isEmpty()) {
      b.append(" <li>"+Utilities.escapeXml(lrc.formatPhrase(RenderingI18nContext.SDR_CAPSTMT, type))+": " + refList(capStmts, "cst") + "</li>\r\n");
    }
    if (base.isEmpty() && refs.isEmpty() && trefs.isEmpty() && examples.isEmpty() & invoked.isEmpty() && imposed.isEmpty() && compliedWith.isEmpty()) {
      b.append(" <li>"+Utilities.escapeXml(lrc.formatPhrase(RenderingI18nContext.SDR_NOT_USED, type))+"</li>\r\n");
    }
    b.append("</ul>\r\n");
    b.append(xigReference());
    if (sd.hasUserData(UserDataNames.loader_custom_resource)) {
      b.append("<p><b>Additional Resource</b></p>\r\n<p>This is an <a href=\"https://build.fhir.org/resource.html#additional\">Additional Resource</a>.");
      b.append(" As an additional resource, the instances carry a <code>resourceDefinition</code> <a href=\"https://build.fhir.org/json.html#additional\">property</a> or <a href=\"https://build.fhir.org/xml.html#additional\">attribute</a>. For this resource, the value of the resourceDefinition is:</p>\r\n");
      b.append("<pre>"+sd.getVersionedUrl()+"</pre>\r\n");
      b.append("<p>");
      boolean hasCompartments = false;
      for (Extension ext : sd.getExtensionsByUrl(ExtensionDefinitions.EXT_ADDITIONAL_COMPARTMENT)) {
        if (ext.hasExtension("param")) {
          hasCompartments = true;
        }
      }
      boolean hasReferences = false;
      for (Extension ext : sd.getExtensionsByUrl(ExtensionDefinitions.EXT_ADDITIONAL_REFERENCE)) {
        hasReferences = true;
      }
      if (!hasCompartments) {
        b.append(" This resource is not in any compartments.");
      }
      if (!hasReferences) {
        b.append(" This resource does not hook into any existing resources.");
      }
      b.append("</p>\n");

      if (hasCompartments) {
        b.append("<p>This resource is in the following compartments:</p><table class=\"grid\">\r\n");
        b.append("<tr><td><b>Compartment</b></td> <td><b>Param(s)</b></td> <td><b>Documentation</b></td> <td title=\"For the $everything operation\"><b>StartParam</b></td> <td title=\"For the $everything operation\"><b>EndParam</b></td></tr>\r\n");
        for (Extension ext : sd.getExtensionsByUrl(ExtensionDefinitions.EXT_ADDITIONAL_COMPARTMENT)) {
          List<String> params = new ArrayList<>();
          for (Extension param : ext.getExtensionsByUrl("param")) {
            params.add(param.getValue().primitiveValue());
          }
          String name = ext.getExtensionString("code");
          String md = processMarkdown("doco", ext.getExtensionString("documentation"));
          b.append("<tr><td><a href=\"https://build.fhir.org/compartmentdefinition-"+name.toLowerCase()+".html\">"+name+"</a></td> <td>"+CommaSeparatedStringBuilder.join(",", params)+"</td>"+
                  "<td>"+md+"</td> <td>"+nn(ext.getExtensionString("startParam"))+"</td> <td>"+nn(ext.getExtensionString("endParam"))+"</td></tr>\r\n");
        }
        b.append("</table>\r\n");
      }

      if (hasReferences) {
        b.append("<p>This resource can be the target of the follow base resource references:</p><table class=\"grid\">\r\n");
        b.append("<tr><td><b>Location</b></td> <td><b>Documentation</b></td></tr>\r\n");
        for (Extension ext : sd.getExtensionsByUrl(ExtensionDefinitions.EXT_ADDITIONAL_REFERENCE)) {
          String path = ext.getExtensionString("path");
          String res = path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : path;
          String md = processMarkdown("doco", ext.getExtensionString("documentation"));
          b.append("<tr><td><a href=\"https://build.fhir.org/"+res.toLowerCase()+".html\">"+path+"</a></td><td>"+md+"</td> </tr>\r\n");
        }
        b.append("</table>\r\n");
      }
    }
    return b.toString()+changeSummary();
  }

  private String nn(String s) {
    return s == null ? "" : s;
  }

  private void loadCoreExtensionMap() throws IOException {
    ClassLoader classLoader = HierarchicalTableGenerator.class.getClassLoader();
    InputStream map = classLoader.getResourceAsStream("r5-examples.json");
    JsonObject examples = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(map);

    FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager.Builder().build();
    NpmPackage npm = pcm.loadPackage("hl7.fhir.r5.examples");
    for (String fn : npm.getFolders().get("package").listFiles()) {
      try {
        Resource r = new JsonParser().parse(npm.getFolders().get("package").fetchFile(fn));
        JsonObject details = examples.getJsonObject(r.fhirType()+"/"+r.getId());
        if (details != null) {
          new ElementVisitor(new ExtensionVisitor(details.asString("path"), details.asString("name"))).visit(null, r);
        }
      } catch (Exception e) {
        // npthing
      }
    }
  }

   private class ExtensionVisitor implements ElementVisitor.IElementVisitor {

    private String path;
    private String name;
    public ExtensionVisitor(String path, String name) {
      this.path = path;
      this.name = name;
    }

    @Override
    public ElementVisitor.ElementVisitorInstruction visit(Object context, Resource resource) {
      if (resource instanceof DomainResource) {
        DomainResource dr = (DomainResource) resource;
        for (Extension ex : dr.getExtension()) {
          seeExtension(path, name, ex.getUrl());
        }
        for (Extension ex : dr.getModifierExtension()) {
          seeExtension(path, name, ex.getUrl());
        }
      }
      return VISIT_CHILDREN;
    }

    @Override
    public ElementVisitor.ElementVisitorInstruction visit(Object context, org.hl7.fhir.r5.model.Element element) {
      for (Extension ex : element.getExtension()) {
        seeExtension(path, name, ex.getUrl());
      }
      if (element instanceof BackboneElement) {
        BackboneElement be = (BackboneElement) element;
        for (Extension ex : be.getModifierExtension()) {
          seeExtension(path, name, ex.getUrl());
        }
      }
      if (element instanceof BackboneType) {
        BackboneType be = (BackboneType) element;
        for (Extension ex : be.getModifierExtension()) {
          seeExtension(path, name, ex.getUrl());
        }
      }
      return VISIT_CHILDREN;
    }
  }

  private void seeExtension(String path, String name, String url) {
    List<IGKnowledgeProvider.ExtensionUsage> list = igp.getCoreExtensionMap().get(url);
    if (list == null) {
      list = new ArrayList<>();
      igp.getCoreExtensionMap().put(url, list);
    }
    list.add(new IGKnowledgeProvider.ExtensionUsage(path, name));
  }

  private String xigReference() {
    if (noXigLink) {
      return "";
    } else {
      return gen.formatPhrase(RenderingI18nContext.SD_XIG_LINK, packageId, sd.getId());
    }
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
        if (inc && cst.hasWebPath()) {
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

      if (s == null) {
        b.append(base.get(s));
      } else {
        b.append("<a href=\"" + s + "\">" + base.get(s) + "</a>");
      }
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
      gc.addStyledText((this.gen.formatPhrase(RenderingI18nContext.STRUC_DEF_MOD)), "?!", null, null, null, false);
    }
    if (element.definition().getMustSupport() || element.definition().hasExtension(ExtensionDefinitions.EXT_OBLIGATION_CORE, ExtensionDefinitions.EXT_OBLIGATION_TOOLS)) {
      gc.addStyledText((this.gen.formatPhrase(RenderingI18nContext.STRUC_DEF_ELE_MUST_SUPP)), "S", "white", "red", null, false);
    }
    if (element.definition().getIsSummary()) {
      gc.addStyledText((this.gen.formatPhrase(RenderingI18nContext.STRUC_DEF_ELE_INCLUDED)), "\u03A3", null, null, null, false);
    }
    if (sdr.hasNonBaseConstraints(element.definition().getConstraint()) || sdr.hasNonBaseConditions(element.definition().getCondition())) {
      Piece p = gc.addText(org.hl7.fhir.r5.renderers.StructureDefinitionRenderer.CONSTRAINT_CHAR);
      p.setHint((this.gen.formatPhrase(RenderingI18nContext.STRUC_DEF_AFFECT_CONSTRAINTS)+sdr.listConstraintsAndConditions(element.definition())+")"));
      p.addStyle(org.hl7.fhir.r5.renderers.StructureDefinitionRenderer.CONSTRAINT_STYLE);
      p.setReference(Utilities.pathURL(VersionUtilities.getSpecUrl(context.getVersion()), "conformance-rules.html#constraints"));
    }
    if (element != null && element.definition().hasExtension(ExtensionDefinitions.EXT_STANDARDS_STATUS)) {
      StandardsStatus ss = StandardsStatus.fromCode(element.definition().getExtensionString(ExtensionDefinitions.EXT_STANDARDS_STATUS));
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
        gc.addPiece(gen.new Piece(sd.getWebPath(), "("+tr.getWorkingCode()+" value)", this.gen.formatPhrase(RenderingI18nContext.SDR_PRIM_VALUE, tr.getWorkingCode())));         
      } else {
        gc.addPiece(gen.new Piece(null, "("+tr.getWorkingCode()+" value)", this.gen.formatPhrase(RenderingI18nContext.SDR_PRIM_VALUE, tr.getWorkingCode()))); 
      }
    } else {
      gc.addText("(multiple)");

    }

    // description
    sdr.generateDescription(new RenderingStatus(), gen, row, element.definition(), null, context.getSpecUrl(), null, sd, context.getSpecUrl(), destDir, false, false, allInvariants, true, false, false, sdr.getContext(), resE);

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
    changeSummaryDetails(b, comp, RenderingI18nContext.SDR_CONT_CH_DET_SD, RenderingI18nContext.SDR_DEFN_CH_DET_SD, null, null);
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
              "<p><b>"+gen.formatPhrase(RenderingI18nContext.SDR_EXPERIMENTAL)+"</b></p></div></div>";
    } else if (sd.getStatus() == Enumerations.PublicationStatus.DRAFT) {
      return "<div><div style=\"border: 1px solid maroon; padding: 10px; background-color: #efefef; min-height: 60px;\">\r\n"+
              "<p><b>"+gen.formatPhrase(RenderingI18nContext.SDR_DRAFT)+"</b></p></div></div>";
    } else {
      return "";
    }
  }

  public String adl() {
    return "<pre><code>"+Utilities.escapeXml(sd.getUserString(UserDataNames.archetypeSource))+"</code></pre>";
  }

  public String useContext() throws IOException {
    XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
    if ("deprecated".equals(ExtensionUtilities.readStringExtension(sd, ExtensionDefinitions.EXT_STANDARDS_STATUS))) {
      XhtmlNode ddiv = div.div("background-color: #ffe6e6; border: 1px solid black; border-radius: 10px; padding: 10px");
      ddiv.para().b().tx(gen.formatPhrase(RenderingI18nContext.SDR_EXT_DEPR)); 
      Extension ext = sd.getExtensionByUrl(ExtensionDefinitions.EXT_STANDARDS_STATUS);
      ext = ext == null || !ext.hasValue() ? null : ext.getValue().getExtensionByUrl(ExtensionDefinitions.EXT_STANDARDS_STATUS_REASON);
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
    if (ProfileUtilities.isModifierExtension(sd)) {
      XhtmlNode ddiv = div.div("border: 1px solid black; border-radius: 10px; padding: 10px");
      ddiv.para().b().tx(gen.formatPhrase(RenderingI18nContext.SDR_EXT_MOD));
    }


    if (sd.getContext().isEmpty()) {
      div.para().tx(gen.formatPhrase(RenderingI18nContext.SDR_EXT_ANY));
    } else {
      div.para().tx(gen.formatPhrase(RenderingI18nContext.SDR_EXT_ELEM)); 
      var ul = div.ul();
      for (StructureDefinitionContextComponent c : sd.getContext()) {
        var li = ul.li();
        switch (c.getType()) {
        case ELEMENT:
          li.tx(gen.formatPhrase(RenderingI18nContext.SDR_EXT_ELEM_ID)); 
          li.tx(": ");
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
          li.tx(gen.formatPhrase(RenderingI18nContext.SDR_EXT_EXT)); 
          li.tx(": ");
          t = context.fetchResource(StructureDefinition.class, c.getExpression());
          if (t != null && t.hasWebPath()) {
            li.ah(t.getWebPath()).tx(t.present());
          } else {
            li.code().tx(c.getExpression());
          }
          break;
        case FHIRPATH:
          li.ah(Utilities.pathURL(context.getSpecUrl(), "fhirpath.html")).tx(gen.formatPhrase(RenderingI18nContext.SDR_EXT_PATH));
          li.tx(c.getExpression());
          break;
        default:
          li.tx("?type?: ");
          li.tx(c.getExpression());
          break;
        }
        if (c.hasExtension(ExtensionDefinitions.EXT_FHIRVERSION_SPECIFIC_USE)) {
          li.tx(" (");
          renderVersionRange(c.getExtensionByUrl(ExtensionDefinitions.EXT_FHIRVERSION_SPECIFIC_USE), li);
          li.tx(")");        
        }
      }
    }
    if (sd.hasContextInvariant()) {
      if (sd.getContextInvariant().size() == 1) {
        XhtmlNode x = div.para();
        x.tx(gen.formatPhrase(RenderingI18nContext.SDR_EXT_CTXT_PATH)); 
        x.tx(": ");
        div.para().code().tx(sd.getContextInvariant().get(0).asStringValue());
      } else {
        XhtmlNode x = div.para();
        x.tx(gen.formatPhrase(RenderingI18nContext.SDR_EXT_CTXT_PATHS)); 
        x.tx(": ");
        var ul = div.ul();
        for (StringType sv : sd.getContextInvariant()) {
          ul.li().code().tx(sv.asStringValue());
        }
      }
    }
    if (sd.hasExtension(ExtensionDefinitions.EXT_FHIRVERSION_SPECIFIC_USE)) {
      var p = div.para();
      p.tx(gen.formatPhrase(RenderingI18nContext.SDR_EXT_VER_PFX)+" ");  
      renderVersionRange(sd.getExtensionByUrl(ExtensionDefinitions.EXT_FHIRVERSION_SPECIFIC_USE), p);
      p.tx(gen.formatPhrase(RenderingI18nContext.SDR_EXT_VER_SFX));        
    }
    return new XhtmlComposer(false, true).compose(div.getChildNodes());
  }

  public void renderVersionRange(Extension ext, XhtmlNode li) {
    if (!ext.hasExtension(ExtensionDefinitions.EXT_FHIRVERSION_SPECIFIC_USE_START)) {
      li.tx(gen.formatPhrase(RenderingI18nContext.SDR_VER_TO_PFX)); 
      li.tx(" ");
      linkToVersion(li, ExtensionUtilities.readStringExtension(ext, ExtensionDefinitions.EXT_FHIRVERSION_SPECIFIC_USE_END));                
      li.stx(gen.formatPhrase(RenderingI18nContext.SDR_VER_TO_SFX)); 
    } else if (!ext.hasExtension(ExtensionDefinitions.EXT_FHIRVERSION_SPECIFIC_USE_END)) {
      li.tx(gen.formatPhrase(RenderingI18nContext.SDR_VER_FROM_PFX)); 
      li.tx(" ");
      linkToVersion(li, ExtensionUtilities.readStringExtension(ext, ExtensionDefinitions.EXT_FHIRVERSION_SPECIFIC_USE_START));
      li.stx(gen.formatPhrase(RenderingI18nContext.SDR_VER_FROM_SFX));      
    } else {
      li.tx(gen.formatPhrase(RenderingI18nContext.SDR_VER_RANGE_PFX)); 
      li.tx(" ");
      linkToVersion(li, ExtensionUtilities.readStringExtension(ext, ExtensionDefinitions.EXT_FHIRVERSION_SPECIFIC_USE_START));
      li.tx(" ");
      li.tx(gen.formatPhrase(RenderingI18nContext.SDR_VER_RANGE_MID)); 
      li.tx(" ");
      linkToVersion(li, ExtensionUtilities.readStringExtension(ext, ExtensionDefinitions.EXT_FHIRVERSION_SPECIFIC_USE_END));        
      li.tx(" ");
      li.stx(gen.formatPhrase(RenderingI18nContext.SDR_VER_RANGE_SFX)); 
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
            x.para().tx(gen.formatPhrase(RenderingI18nContext.SDR_EXT_UNCHANGED, VersionUtilities.getNameForVersion(v).toUpperCase())); 
          } else {
            x.para().tx(gen.formatPhrase(RenderingI18nContext.SDR_EXT_CHANGED, VersionUtilities.getNameForVersion(v).toUpperCase())+": "); 
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
                outputTracker, false, gen.withUniqueLocalPrefix(VersionUtilities.getNameForVersion(v)), ANCHOR_PREFIX_SNAP, resE, "V"));
          }
        }
      }
      return new XhtmlComposer(false, true).compose(x.getChildNodes());
    }
  }

  public boolean isNoXigLink() {
    return noXigLink;
  }

  public void setNoXigLink(boolean noXigLink) {
    this.noXigLink = noXigLink;
  }


  public String classTable(String defnFile, Set<String> outputTracker, boolean toTabs, StructureDefinitionRendererMode mode, boolean all) throws IOException {
    try {
      XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
      XhtmlNode tbl = div.table("box").markGenerated(true);
      // row 1: Class Definition
      XhtmlNode tr = tbl.tr();
      XhtmlNode td = tr.td();
      td.colspan(5);
      td.style("text-align: center");
      var x = td;
      if (sd.getAbstract()) {
        x = x.i();
      }
      x.b().tx(sd.getTypeName());
      StructureDefinition sdb = context.fetchTypeDefinition(sd.getBaseDefinition());
      if (sdb != null) {
        x.tx(" : ");
        x.ah(sdb.getWebPath()).tx(sdb.getTypeName());
      }
      if (sd.hasDescription()) {
        tr = tbl.tr();
        tr.td().colspan(5).markdownSimple(sd.getDescription(), "desc");
      }
      tr.styleChildren("border-bottom: 1px solid #666");
      // generate a heirarchical table, and put it in td
      gen.setStructureMode(StructureDefinitionRendererMode.SUMMARY);
      XhtmlNode tblt = sdr.generateAttributeTable(new RenderingStatus(), defnFile, sd, true, destDir, false, sd.getId(), false, corePath, "", sd.getKind() == StructureDefinitionKind.LOGICAL, false, outputTracker, false, gen.withUniqueLocalPrefix(all ? mc(mode) + "a" : mc(mode)), toTabs ? ANCHOR_PREFIX_DIFF : ANCHOR_PREFIX_SNAP, resE, all ? "DA" : "D");
      if (!tblt.getChildNodes().isEmpty()) {
        XhtmlNode tt = null;
        for (XhtmlNode t : tblt.getChildNodes()) {
          if ("tr".equals(t.getName())) {
            tt = t;
          }
        }
        if (tt != null) {
          tt.styleChildren("border-bottom: 1px solid #666");
        }
      }
      tbl.getChildNodes().addAll(tblt.getChildNodes());

      if (sd.hasExtension(ExtensionDefinitions.EXT_TYPE_OPERATION)) {
        for (Extension ext : sd.getExtensionsByUrl(ExtensionDefinitions.EXT_TYPE_OPERATION)) {
          OperationDefinition od = context.fetchResource(OperationDefinition.class, ext.getValue().primitiveValue(), null, sd);
          if (od != null) {
            tr = tbl.tr();
            tr.style("padding: 0px; background-color: white");
            td = tr.td().colspan(4);
            td.style("padding: 0px 4px 0px 4px");
            List<OperationDefinition.OperationDefinitionParameterComponent> inp = new ArrayList<>();
            List<OperationDefinition.OperationDefinitionParameterComponent> outp = new ArrayList<>();
            for (OperationDefinition.OperationDefinitionParameterComponent p : od.getParameter()) {
              if (p.getUse() == Enumerations.OperationParameterUse.IN) {
                inp.add(p);
              } else {
                outp.add(p);
              }
            }
            td.img("icon-type-operation.png", "icon-type-operation").style("vertical-align: text-bottom");
            td.tx(" ");
            td.tx(od.getCode());
            td.tx("(");
            boolean first = true;
            for (OperationDefinition.OperationDefinitionParameterComponent p : inp) {
              if (first) first = false; else td.tx(", ");
              td.tx(p.getName());
              if (p.hasType()) {
                td.tx(" : ");
                td.tx(p.getType().toCode());
              }
            }
            td.tx(")");
            if (!outp.isEmpty()) {
              td.tx(" : ");
              if (outp.size() == 1) {
                td.tx(outp.get(0).getType().toCode());
              } else {
                td.tx("[");
                first = true;
                for (OperationDefinition.OperationDefinitionParameterComponent p : outp) {
                  if (first) first = false; else td.tx(", ");
                  td.tx(p.getName());
                  if (p.hasType()) {
                    td.tx(" : ");
                    td.tx(p.getType().toCode());
                  }
                }
                td.tx("]");
              }
            }
            tr.td().style("padding: 0px 4px 0px 4px").markdownSimple(od.getDescription(), "desc");
          }
        }
      }
      div.styles("table.box { margin-bottom: 10px; border: 1px black solid;  margin-right: inherit; }\n" +
              "table.box th { border: none; }\n" +
              "table.box td { border: none; font-size : 11px; }\n");
      return new XhtmlComposer(true, true).compose(div.getChildNodes());
    } catch (Exception e) {
      e.printStackTrace();
      return "<p><i>" + Utilities.escapeXml(e.getMessage()) + "</i></p>";
    }
  }

  public String searchParameters() throws IOException {
    Map<String, SearchParameter> splist = new HashMap<>();
    for (SearchParameter sp : context.fetchResourcesByType(SearchParameter.class)) {
      if (hasBase(sp, sd.getType())) {
        splist.put(sp.getCode(), sp);
      }
    }
    XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
    XhtmlNode tbl = div.table("list");
    XhtmlNode tr = tbl.tr();
    tr.td().b().tx(gen.formatPhrase(RenderingI18nContext.GENERAL_NAME));
    tr.td().b().tx(gen.formatPhrase(RenderingI18nContext.GENERAL_TYPE));
    tr.td().b().tx(gen.formatPhrase(RenderingI18nContext.GENERAL_DESC));
    tr.td().b().tx(gen.formatPhrase(RenderingI18nContext.SEARCH_PAR_EXP));

    for (String code : Utilities.sorted(splist.keySet())) {
      SearchParameter sp = splist.get(code);
      tr = tbl.tr();
      tr.td().ah(sp.getWebPath()).tx(sp.getCode());
      tr.td().ah(Utilities.pathURL(gen.getLink(RenderingContext.KnownLinkType.SPEC, true), "search.html#"+sp.getType().toCode())).tx(sp.getType().toCode());
      tr.td().markdown(sp.getDescription(), "description");
      tr.td().code(sp.getExpression());
    }
    return new XhtmlComposer(false, true).compose(div.getChildNodes());
  }

  private boolean hasBase(SearchParameter sp, String type) {
    for (Enumeration<Enumerations.VersionIndependentResourceTypesAll> c : sp.getBase()) {
      if (type.equals(c.getCode())) {
        return true;
      }
    }
    return false;
  }

}
