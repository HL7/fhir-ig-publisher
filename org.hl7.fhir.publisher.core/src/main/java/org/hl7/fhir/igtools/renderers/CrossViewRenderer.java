package org.hl7.fhir.igtools.renderers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.igtools.renderers.utils.ObligationsAnalysis;
import org.hl7.fhir.igtools.renderers.utils.ObligationsAnalysis.ActorInfo;
import org.hl7.fhir.igtools.renderers.utils.ObligationsAnalysis.ProfileActorObligationsAnalysis;
import org.hl7.fhir.igtools.renderers.utils.ObligationsAnalysis.ProfileObligationsAnalysis;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.fhirpath.ExpressionNode;
import org.hl7.fhir.r5.fhirpath.ExpressionNode.Kind;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r5.model.ActorDefinition;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.ConceptMap.ConceptMapGroupComponent;
import org.hl7.fhir.r5.model.DataType;
import org.hl7.fhir.r5.model.DomainResource;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionBindingAdditionalComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionBindingComponent;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.OperationDefinition;
import org.hl7.fhir.r5.model.OperationDefinition.OperationDefinitionParameterComponent;
import org.hl7.fhir.r5.model.Questionnaire;
import org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.SearchParameter;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.ExtensionContextType;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionContextComponent;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.r5.renderers.Renderer;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities;
import org.hl7.fhir.r5.terminologies.utilities.ValidationResult;
import org.hl7.fhir.r5.utils.ResourceSorters.CanonicalResourceSortByUrl;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.utilities.*;
import org.hl7.fhir.utilities.HL7WorkGroups.HL7WorkGroup;
import org.hl7.fhir.utilities.validation.ValidationOptions;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class CrossViewRenderer extends Renderer {

  private static final String HARD_BORDER = "border-left: 2px solid silver";

  public class UsedType {
    public UsedType(String name, boolean ms) {
      super();
      this.name = name;
      this.ms = ms;
    }

    public String name;
    public boolean ms;
  }

  public class ObsListSorter implements Comparator<ObservationProfile> {
    @Override
    public int compare(ObservationProfile l, ObservationProfile r) {
      return l.source.getUrl().toLowerCase().compareTo(r.source.getUrl().toLowerCase());
    }
  }

  public class ExtListSorter implements Comparator<ExtensionDefinition> {
    @Override
    public int compare(ExtensionDefinition l, ExtensionDefinition r) {
      return l.source.getUrl().toLowerCase().compareTo(r.source.getUrl().toLowerCase());
    }
  }

  public class StructureDefinitionNote {
    public StructureDefinition source;
  }

  public class ObservationProfile extends StructureDefinitionNote {
    public List<Coding> code = new ArrayList<>();
    public ElementDefinitionBindingComponent codeVS;
    public List<Coding> category = new ArrayList<>();
    public ElementDefinitionBindingComponent catVS;
    public List<UsedType> effectiveTypes = new ArrayList<>();
    public List<UsedType> types = new ArrayList<>();
    public Boolean dataAbsentReason;
    public List<Coding> bodySite = new ArrayList<>();
    public List<Coding> method = new ArrayList<>();
    public List<ObservationProfile> components = new ArrayList<>();
    public List<String> members = new ArrayList<String>();
    public String name;

    public boolean hasValue() {
      return code.size() > 0 || category.size() > 0;
    }
  }

  public class ExtensionDefinition extends StructureDefinitionNote {
    public String code;
    public String definition;
    public List<UsedType> types = new ArrayList<>();
    public List<ExtensionDefinition> components = new ArrayList<>();
  }

  private String canonical;
  private String canonical2;
  private IWorkerContext worker;
  private List<ObservationProfile> obsList = new ArrayList<>();
  private List<ExtensionDefinition> extList = new ArrayList<>();
  private Map<String, List<ExtensionDefinition>> extMap = new HashMap<>();
  public List<StructureDefinition> allExtensions = new ArrayList<>();
  public List<StructureDefinition> allProfiles = new ArrayList<>();

  public List<String> baseEffectiveTypes = new ArrayList<>();
  public List<String> baseTypes = new ArrayList<>();
  public List<String> baseExtTypes = new ArrayList<>();
  public String corePath;
  private FHIRPathEngine fpe;
  private List<SearchParameter> searchParams = new ArrayList<>();
  private OIDUtilities oids = new OIDUtilities();

  public CrossViewRenderer(String canonical, String canonical2, IWorkerContext context, String corePath, RenderingContext rc) {
    super(rc);
    this.canonical = canonical;
    this.canonical2 = canonical2;
    this.worker = context;
    this.corePath = corePath;
    getBaseTypes();
    fpe = new FHIRPathEngine(context);
  }

  private void getBaseTypes() {
    StructureDefinition sd = worker.fetchTypeDefinition("Observation");
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      if (ed.getPath().equals("Observation.effective[x]")) {
        for (TypeRefComponent tr : ed.getType())
          if (!baseEffectiveTypes.contains(tr.getWorkingCode()))
            baseEffectiveTypes.add(tr.getWorkingCode());
      }
      if (ed.getPath().startsWith("Observation.value") && Utilities.charCount(ed.getPath(), '.') == 1 && !ed.getMax().equals("0")) {
        for (TypeRefComponent tr : ed.getType())
          if (!baseTypes.contains(tr.getWorkingCode()))
            baseTypes.add(tr.getWorkingCode());
      }
    }
    sd = worker.fetchTypeDefinition("Extension");
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      if (ed.getPath().startsWith("Extension.value") && !ed.getMax().equals("0")) {
        for (TypeRefComponent tr : ed.getType())
          if (!baseExtTypes.contains(tr.getCode()))
            baseExtTypes.add(tr.getCode());
      }
    }
  }

  public void seeResource(CanonicalResource res) {
    if (res instanceof StructureDefinition) {
      seeStructureDefinition((StructureDefinition) res);
    }
    if (res instanceof SearchParameter) {
      seeSearchParameter((SearchParameter) res);
    }
  }

  private void seeSearchParameter(SearchParameter sp) {
    try {
      ExpressionNode n = fpe.parse(sp.getExpression());
      sp.getExpressionElement().setUserData(UserDataNames.xver_expression, n);
    } catch (Exception e) {
      // do nothing in this case
    }
    searchParams.add(sp);
  }

  public void seeStructureDefinition(StructureDefinition sd) {
    allProfiles.add(sd);
    if ("Extension".equals(sd.getType())) {
      seeExtensionDefinition(sd);
    }
    if ("Observation".equals(sd.getType())) {
      seeObservation(sd);
    }
    checkForExtensions(sd);
  }

  private void checkForExtensions(StructureDefinition sd) {
    // TODO Auto-generated method stub

  }

  private void seeObservation(StructureDefinition sd) {
    ObservationProfile obs = new ObservationProfile();
    obs.source = sd;
    int i = 0;
    String system = null;
    String compSlice = null;
    while (i < sd.getSnapshot().getElement().size()) {
      ElementDefinition ed = sd.getSnapshot().getElement().get(i);

      if (ed.getPath().equals("Observation.category") && ed.hasFixedOrPattern() && ed.getFixedOrPattern() instanceof CodeableConcept) {
        obs.category.addAll(((CodeableConcept) ed.getFixedOrPattern()).getCoding());
      }
      if (ed.getPath().equals("Observation.category.coding")) {
        system = null;
        if (ed.hasFixedOrPattern() && ed.getFixedOrPattern() instanceof Coding) {
          obs.category.add(((Coding) ed.getFixedOrPattern()));
        } else if (ed.getBinding().hasValueSet()) {
          obs.catVS = ed.getBinding();
        } else if (ed.getBinding().hasValueSet()) {
          obs.catVS = ed.getBinding();
        }
      }
      if (ed.getPath().equals("Observation.category.coding.system") && ed.hasFixedOrPattern()) {
        system = ed.getFixedOrPattern().primitiveValue();
      }
      if (ed.getPath().equals("Observation.category.coding.code") && (system != null && ed.hasFixedOrPattern())) {
        obs.category.add(new Coding(system, ed.getFixedOrPattern().primitiveValue(), null));
        system = null;
      }

      if (ed.getPath().equals("Observation.code")) {
        if (ed.hasFixedOrPattern() && ed.getFixedOrPattern() instanceof CodeableConcept) {
          obs.code.addAll(((CodeableConcept) ed.getFixedOrPattern()).getCoding());
        } else if (ed.getBinding().hasValueSet()) {
          obs.codeVS = ed.getBinding();
        }
      }
      if (ed.getPath().equals("Observation.code.coding")) {
        system = null;
        if (ed.hasFixedOrPattern() && ed.getFixedOrPattern() instanceof Coding) {
          obs.code.add(((Coding) ed.getFixedOrPattern()));
        } else if (ed.getBinding().hasValueSet()) {
          obs.codeVS = ed.getBinding();
        }
      }
      if (ed.getPath().equals("Observation.code.coding.system") && ed.hasFixedOrPattern()) {
        system = ed.getFixedOrPattern().primitiveValue();
      }
      if (ed.getPath().equals("Observation.code.coding.code") && (system != null && ed.hasFixedOrPattern())) {
        obs.code.add(new Coding(system, ed.getFixedOrPattern().primitiveValue(), null));
        system = null;
      }

      if (ed.getPath().equals("Observation.bodySite") && ed.hasFixedOrPattern() && ed.getFixedOrPattern() instanceof CodeableConcept) {
        obs.bodySite.addAll(((CodeableConcept) ed.getFixedOrPattern()).getCoding());
      }
      if (ed.getPath().equals("Observation.bodySite.coding")) {
        system = null;
        if (ed.hasFixedOrPattern() && ed.getFixedOrPattern() instanceof Coding) {
          obs.bodySite.add(((Coding) ed.getFixedOrPattern()));
        }
      }
      if (ed.getPath().equals("Observation.bodySite.coding.system") && ed.hasFixedOrPattern()) {
        system = ed.getFixedOrPattern().primitiveValue();
      }
      if (ed.getPath().equals("Observation.bodySite.coding.code") && (system != null && ed.hasFixedOrPattern())) {
        obs.bodySite.add(new Coding(system, ed.getFixedOrPattern().primitiveValue(), null));
        system = null;
      }

      if (ed.getPath().equals("Observation.method") && ed.hasFixedOrPattern() && ed.getFixedOrPattern() instanceof CodeableConcept) {
        obs.method.addAll(((CodeableConcept) ed.getFixedOrPattern()).getCoding());
      }
      if (ed.getPath().equals("Observation.method.coding")) {
        system = null;
        if (ed.hasFixedOrPattern() && ed.getFixedOrPattern() instanceof Coding) {
          obs.method.add(((Coding) ed.getFixedOrPattern()));
        }
      }
      if (ed.getPath().equals("Observation.method.coding.system") && ed.hasFixedOrPattern()) {
        system = ed.getFixedOrPattern().primitiveValue();
      }
      if (ed.getPath().equals("Observation.method.coding.code") && (system != null && ed.hasFixedOrPattern())) {
        obs.method.add(new Coding(system, ed.getFixedOrPattern().primitiveValue(), null));
        system = null;
      }

      if (ed.getPath().equals("Observation.effective[x]")) {
        for (TypeRefComponent tr : ed.getType())
          if (!typesContain(obs.effectiveTypes, tr.getWorkingCode()))
            obs.effectiveTypes.add(new UsedType(tr.getWorkingCode(), isMustSupport(ed, tr)));
      }
      if (ed.getPath().startsWith("Observation.value") && Utilities.charCount(ed.getPath(), '.') == 1 && !ed.getMax().equals("0")) {
        for (TypeRefComponent tr : ed.getType())
          if (!typesContain(obs.types, tr.getWorkingCode()))
            obs.types.add(new UsedType(tr.getWorkingCode(), isMustSupport(ed, tr)));
      }
      if (ed.getPath().equals("Observation.dataAbsentReason")) {
        if (ed.getMax().equals("0")) {
          obs.dataAbsentReason = false;
        } else if (ed.getMin() == 1) {
          obs.dataAbsentReason = true;
        }
      }
      if (ed.getPath().equals("Observation.component")) {
        compSlice = ed.getSliceName();
      }
      if (ed.getPath().startsWith("Observation.component.") && !ed.isProhibited() && compSlice != null) {
        i = processObservationComponent(obs, sd.getSnapshot().getElement(), compSlice, i);
      } else {
        i++;
      }
    }

    if (obs.hasValue()) {
      obsList.add(obs);
    }
  }

  private boolean typesContain(List<UsedType> types, String name) {
    for (UsedType t : types) {
      if (t.name.equals(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean isMustSupport(ElementDefinition ed, TypeRefComponent tr) {
    return ed.getMustSupport() || "true".equals(ExtensionUtilities.readStringExtension(tr, ExtensionDefinitions.EXT_MUST_SUPPORT));
  }

  private int processObservationComponent(ObservationProfile parent, List<ElementDefinition> list, String compSlice, int i) {
    ObservationProfile obs = new ObservationProfile();
    String system = null;
    obs.name = compSlice;
    while (i < list.size() && list.get(i).getPath().startsWith("Observation.component.")) {
      ElementDefinition ed = list.get(i);
      if (ed.getPath().equals("Observation.component.category") && ed.hasFixedOrPattern() && ed.getFixedOrPattern() instanceof CodeableConcept) {
        obs.category.addAll(((CodeableConcept) ed.getFixedOrPattern()).getCoding());
      }
      if (ed.getPath().equals("Observation.component.category.coding")) {
        system = null;
        if (ed.hasFixedOrPattern() && ed.getFixedOrPattern() instanceof Coding) {
          obs.category.add(((Coding) ed.getFixedOrPattern()));
        }
      }
      if (ed.getPath().equals("Observation.component.category.coding.system") && ed.hasFixedOrPattern()) {
        system = ed.getFixedOrPattern().primitiveValue();
      }
      if (ed.getPath().equals("Observation.component.category.coding.code") && (system != null && ed.hasFixedOrPattern())) {
        obs.method.add(new Coding(system, ed.getFixedOrPattern().primitiveValue(), null));
        system = null;
      }

      if (ed.getPath().equals("Observation.component.code") && ed.hasFixedOrPattern() && ed.getFixedOrPattern() instanceof CodeableConcept) {
        obs.code.addAll(((CodeableConcept) ed.getFixedOrPattern()).getCoding());
      }
      if (ed.getPath().equals("Observation.component.code.coding")) {
        system = null;
        if (ed.hasFixedOrPattern() && ed.getFixedOrPattern() instanceof Coding) {
          obs.code.add(((Coding) ed.getFixedOrPattern()));
        }
      }
      if (ed.getPath().equals("Observation.component.code.coding.system") && ed.hasFixedOrPattern()) {
        system = ed.getFixedOrPattern().primitiveValue();
      }
      if (ed.getPath().equals("Observation.component.code.coding.code") && (system != null && ed.hasFixedOrPattern())) {
        obs.method.add(new Coding(system, ed.getFixedOrPattern().primitiveValue(), null));
        system = null;
      }

      if (ed.getPath().startsWith("Observation.component.value") && Utilities.charCount(ed.getPath(), '.') == 2 && !ed.getMax().equals("0")) {
        for (TypeRefComponent tr : ed.getType())
          if (!typesContain(obs.types, tr.getWorkingCode()))
            obs.types.add(new UsedType(tr.getWorkingCode(), isMustSupport(ed, tr)));
      }
      i++;
    }
    parent.components.add(obs);
    return i;
  }

  private void seeExtensionDefinition(StructureDefinition sd) {
    allExtensions.add(sd);
    String code = null;
    if (sd.getUrl().startsWith(canonical + "/StructureDefinition/")) {
      code = sd.getUrl().substring(canonical.length() + 21);
    } else if (canonical2 != null && sd.getUrl().startsWith(canonical2 + "/StructureDefinition/")) {
      code = sd.getUrl().substring(canonical2.length() + 21);
    } else {
      //  System.out.println("extension url doesn't follow canonical pattern: "+sd.getUrl()+"/StructureDefinition, so omitted from extension summary");
      return;
    }
    ExtensionDefinition exd = new ExtensionDefinition();
    extList.add(exd);
    exd.source = sd;
    exd.code = code;
    exd.definition = sd.getDescription();
    int i = 0;
    while (i < sd.getSnapshot().getElement().size()) {
      ElementDefinition ed = sd.getSnapshot().getElement().get(i);
      if (ed.getPath().startsWith("Extension.value") && !ed.getMax().equals("0")) {
        for (TypeRefComponent tr : ed.getType())
          if (!typesContain(exd.types, tr.getCode()))
            exd.types.add(new UsedType(tr.getCode(), isMustSupport(ed, tr)));
      }
      if (ed.getPath().startsWith("Extension.extension.")) {
        i = processExtensionComponent(exd, sd.getSnapshot().getElement(), sd.getSnapshot().getElement().get(i - 1).getDefinition(), i);
      } else {
        i++;
      }
    }
    for (String s : getExtensionContext(sd)) {
      List<ExtensionDefinition> list = extMap.get(s);
      if (list == null) {
        list = new ArrayList<>();
        extMap.put(s, list);
      }
      list.add(exd);
    }
  }

  private int processExtensionComponent(ExtensionDefinition parent, List<ElementDefinition> list, String defn, int i) {
    ExtensionDefinition exd = new ExtensionDefinition();
    exd.definition = defn;
    while (i < list.size() && list.get(i).getPath().startsWith("Extension.extension.")) {
      ElementDefinition ed = list.get(i);
      if (ed.getPath().equals("Extension.extension.url") && ed.hasFixedOrPattern()) {
        exd.code = ed.getFixedOrPattern().primitiveValue();
        if (exd.code.startsWith(canonical)) {
          if (exd.code.length() > canonical.length() + 21) {
            System.out.println("extension code doesn't follow canonical pattern: " + exd.code);
          } else {
            exd.code = exd.code.substring(canonical.length() + 21);
          }
        }
        if (canonical2 != null && exd.code.startsWith(canonical2)) {
          if (exd.code.length() > canonical2.length() + 21) {
            System.out.println("extension code doesn't follow canonical2 pattern: " + exd.code);
          } else {
            exd.code = exd.code.substring(canonical2.length() + 21);
          }
        }
      }

      if (ed.getPath().startsWith("Extension.extension.value")) {
        for (TypeRefComponent tr : ed.getType())
          if (!typesContain(exd.types, tr.getCode()))
            exd.types.add(new UsedType(tr.getCode(), isMustSupport(ed, tr)));
      }
      i++;
    }
    if (exd.code != null)
      parent.components.add(exd);
    return i;
  }

  public String getExtensionSummary() {
    StringBuilder b = new StringBuilder();
    if (extList.size() == 0) {
      b.append("<p>No Extensions Defined by this Implementation Guide</p>\r\n");
    } else {
      Collections.sort(extList, new ExtListSorter());
      b.append("<table class=\"grid\">\r\n");
      b.append(" <tr>");
      b.append("<td><b>Code</b></td>");
      b.append("<td><b>Value Types</b></td>");
      b.append("<td><b>Definition</b></td>");
      b.append("</tr>\r\n");

      for (ExtensionDefinition op : extList) {
        b.append(" <tr>");
        b.append("<td><a href=\"" + op.source.getWebPath() + "\">" + op.code + "</a></td>");
        renderTypeCell(b, true, op.types, baseExtTypes);
        b.append("<td>" + Utilities.escapeXml(op.definition) + "</td>");
        b.append("</tr>\r\n");
        for (ExtensionDefinition inner : op.components) {
          b.append(" <tr>");
          b.append("<td>&nbsp;&nbsp;" + inner.code + "</td>");
          renderTypeCell(b, true, inner.types, baseExtTypes);
          b.append("<td>" + Utilities.escapeXml(inner.definition) + "</td>");
          b.append("</tr>\r\n");

        }
      }
      b.append("</table>\r\n");
    }
    return b.toString();
  }


  public String getObservationSummary() {
    StringBuilder b = new StringBuilder();
    if (obsList.size() == 0) {
      b.append("<p>No Observations Found</p>\r\n");
    } else {
      Collections.sort(obsList, new ObsListSorter());
      b.append("<table class=\"grid\">\r\n");
      boolean hasCat = false;
      boolean hasCode = false;
      boolean hasEffective = false;
      boolean hasTypes = false;
      boolean hasDAR = false;
      boolean hasBodySite = false;
      boolean hasMethod = false;
      for (ObservationProfile op : obsList) {
        hasCat = hasCat || !op.category.isEmpty() || op.catVS != null;
        hasCode = hasCode || !op.code.isEmpty() | op.codeVS != null;
        hasEffective = hasEffective || !op.effectiveTypes.isEmpty();
        hasTypes = hasTypes || !op.types.isEmpty();
        hasBodySite = hasBodySite || !op.bodySite.isEmpty();
        hasMethod = hasMethod || !op.method.isEmpty();
        hasDAR = hasDAR || op.dataAbsentReason != null;
        for (ObservationProfile op2 : op.components) {
          hasCode = hasCode || !op2.code.isEmpty() | op2.codeVS != null;
          hasEffective = hasEffective || !op2.effectiveTypes.isEmpty();
          hasTypes = hasTypes || !op2.types.isEmpty();
          hasBodySite = hasBodySite || !op2.bodySite.isEmpty();
          hasMethod = hasMethod || !op2.method.isEmpty();
          hasDAR = hasDAR || op2.dataAbsentReason != null;
        }
      }
      if (hasCat) {
        List<Coding> cat = obsList.get(0).category;
        boolean diff = false;
        for (ObservationProfile op : obsList) {
          if (!isSameCodes(cat, op.category)) {
            diff = true;
          }
        }
        if (!diff) {
          if (cat.isEmpty()) {
            b.append("<p>All the observations have the no assigned category</p>\r\n");
          } else {
            b.append("<p>All the observations have the category " + new DataRenderer(context).displayCoding(cat) + "</p>\r\n");
          }
          hasCat = false;
        }
      }
      b.append(" <tr>");
      b.append("<td><b>Profile Name</b></td>");
      if (hasCat) b.append("<td><b>Category</b></td>");
      if (hasCode) b.append("<td><b>Code</b></td>");
      if (hasEffective) b.append("<td><b>Time Types</b></td>");
      if (hasTypes) b.append("<td><b>Value Types</b></td>");
      if (hasDAR) b.append("<td><b>Data Absent Reason</b></td>");
      if (hasBodySite) b.append("<td><b>Body Site</b></td>");
      if (hasMethod) b.append("<td><b>Method</b></td>");
      b.append("</tr>\r\n");

      for (ObservationProfile op : obsList) {
        b.append(" <tr>");

        b.append("<td><a href=\"" + op.source.getWebPath() + "\" title=\"" + op.source.present() + "\">" + op.source.getId() + "</a></td>");
        renderCodingCell(b, hasCat, op.category, op.catVS);
        renderCodingCell(b, hasCode, op.code, op.codeVS);
        renderTypeCell(b, hasEffective, op.effectiveTypes, baseEffectiveTypes);
        renderTypeCell(b, hasTypes, op.types, baseTypes);
        renderBoolean(b, hasDAR, op.dataAbsentReason);
        renderCodingCell(b, hasBodySite, op.bodySite, null);
        renderCodingCell(b, hasMethod, op.method, null);
        b.append("</tr>\r\n");
        for (ObservationProfile op2 : op.components) {
          b.append(" <tr style=\"background-color: #eeeeee\">");
          b.append("<td>&nbsp;&nbsp;" + op2.name + "</td>");
          b.append("<td></td>");
          renderCodingCell(b, hasCode, op2.code, op2.codeVS);
          renderTypeCell(b, hasEffective, op2.effectiveTypes, baseEffectiveTypes);
          renderTypeCell(b, hasTypes, op2.types, baseTypes);
          renderBoolean(b, hasDAR, op2.dataAbsentReason);
          renderCodingCell(b, hasBodySite, op2.bodySite, null);
          renderCodingCell(b, hasMethod, op2.method, null);
          b.append("</tr>\r\n");
        }
      }
      b.append("</table>\r\n");
    }
    return b.toString();
  }


  private boolean isSameCodes(List<Coding> l1, List<Coding> l2) {
    if (l1.size() != l2.size())
      return false;
    for (Coding c1 : l1) {
      boolean found = false;
      for (Coding c2 : l2) {
        found = found || (c2.hasSystem() && c2.getSystem().equals(c1.getSystem()) && c2.hasCode() && c2.getCode().equals(c1.getCode()));
      }
      if (!found)
        return false;
    }
    return true;
  }

  private void renderBoolean(StringBuilder b, boolean render, Boolean bool) {
    if (render) {
      b.append("<td>");
      if (bool == null) {
        b.append("<img src=\"conf-optional.png\"/>");
      } else if (bool) {
        b.append("<img src=\"conf-required.png\"/>");
      } else {
        b.append("<img src=\"conf-prohibited.png\"/>");
      }
      b.append("</td>");
    }
  }

  private void renderTypeCell(StringBuilder b, boolean render, List<UsedType> types, List<String> base) {
    if (render) {
      b.append("<td>");
      if (types.size() == base.size() && allMSAreSame(types)) {
        if (types.size() > 0 && types.get(0).ms) {
          b.append(" <span style=\"color:white; background-color: red; font-weight:bold\">S</span> ");
        }
        b.append("(all)");
      } else {
        boolean doMS = !allMSAreSame(types);
        boolean first = true;
        for (UsedType t : types) {
          if (!doMS && first && t.ms) {
            b.append(" <span style=\"color:white; background-color: red; font-weight:bold\">S</span> ");
          }
          if (first) first = false;
          else b.append(" | ");
          StructureDefinition sd = worker.fetchTypeDefinition(t.name);
          if (sd != null) {
            b.append("<a href=\"" + sd.getWebPath() + "\" title=\"" + t.name + "\">" + t.name + "</a>");
          } else {
            b.append(t.name);
          }
          if (doMS && t.ms) {
            b.append(" <span style=\"color:white; background-color: red; font-weight:bold\">S</span>");
          }
        }
      }
      b.append("</td>");
    }
  }

  private boolean allMSAreSame(List<UsedType> types) {
    if (types.size() == 0) {
      return false;
    }
    boolean ms = types.get(0).ms;
    for (UsedType t : types) {
      if (ms != t.ms) {
        return false;
      }
    }
    return true;
  }

  private boolean noneAreMs(List<UsedType> types) {
    for (UsedType t : types) {
      if (t.ms) {
        return false;
      }
    }
    return true;
  }

  private void renderCodingCell(StringBuilder b, boolean render, List<Coding> list, ElementDefinitionBindingComponent binding) {
    if (render) {
      b.append("<td>");
      boolean first = true;
      if (binding != null) {
        b.append("<a href=\"" + Utilities.pathURL(corePath, "terminologies.html#" + binding.getStrength().toCode()) + "\">" + binding.getStrength().toCode() + "</a> VS ");
        ValueSet vs = worker.findTxResource(ValueSet.class, binding.getValueSet());
        if (vs == null) {
          b.append(Utilities.escapeXml(binding.getValueSet()));
        } else if (vs.hasWebPath()) {
          b.append("<a href=\"" + vs.getWebPath() + "\">" + Utilities.escapeXml(vs.present()) + "</a>");
        } else {
          b.append(Utilities.escapeXml(vs.present()));
        }
      } else {
        for (Coding t : list) {
          if (first) first = false;
          else b.append(", ");
          String sys = new DataRenderer(context).displaySystem(t.getSystem());
          if (sys.equals(t.getSystem()))
            sys = null;
          if (sys == null) {
            CodeSystem cs = worker.fetchCodeSystem(t.getSystem());
            if (cs != null)
              sys = cs.getTitle();
          }
          t.setUserData(UserDataNames.xver_desc, sys);
          ValidationResult vr = worker.validateCode(ValidationOptions.defaults(), t.getSystem(), t.getVersion(), t.getCode(), null);
          if (vr != null & vr.getDisplay() != null) {
            //          if (Utilities.existsInList(t.getSystem(), "http://loinc.org"))
            //            b.append("<span title=\""+t.getSystem()+(sys == null ? "" : " ("+sys+")")+": "+ vr.getDisplay()+"\">"+t.getCode()+" "+vr.getDisplay()+"</span>");           
            //          else {
            CodeSystem cs = worker.fetchCodeSystem(t.getSystem());
            if (cs != null && cs.hasWebPath()) {
              b.append("<a href=\"" + cs.getWebPath() + "#" + cs.getId() + "-" + t.getCode() + "\" title=\"" + t.getSystem() + (sys == null ? "" : " (" + sys + ")") + ": " + vr.getDisplay() + "\">" + t.getCode() + "</a>");
            } else {
              b.append("<span title=\"" + t.getSystem() + (sys == null ? "" : " (" + sys + ")") + ": " + vr.getDisplay() + "\">" + t.getCode() + "</span>");
            }
            //          }
          } else {
            b.append("<span title=\"" + t.getSystem() + (sys == null ? "" : " (" + sys + "): ") + "\">" + t.getCode() + "</span>");
          }
        }
      }
      b.append("</td>");
    }
  }

  public List<ObservationProfile> getObservations() {
    return obsList;
  }

  public List<String> getExtensionContext(StructureDefinition sd) {
    Set<String> set = new HashSet<>();
    for (StructureDefinitionContextComponent ec : sd.getContext()) {
      set.addAll(getExtensionContext(ec));
    }

    if (set.size() == 0) {
      set.add("none");
    } else if (set.size() > 1) {
      set.add("multiple");
    }
    return Utilities.sorted(set);
  }

  private Set<String> getExtensionContext(StructureDefinitionContextComponent ctxt) {
    Set<String> set = new HashSet<>();
    if (ctxt.getType() == null) {
      set.add("Unknown");
      return set;
    }
    switch (ctxt.getType()) {
      case ELEMENT:
        String s = ctxt.getExpression();
        if (s.contains(":")) {
          if (s.contains("#")) {
            s = s.substring(s.indexOf("#") + 1);
          } else {
            s = null;
          }
        }
        if (s != null) {
          if (s.contains(".")) {
            s = s.substring(0, s.indexOf("."));
          }
          if (worker.isPrimitiveType(s)) {
            set.add("primitives");
            s = null;
          }
          if (worker.isDataType(s)) {
            set.add("datatypes");
          }
          if (s != null) {
            set.add(s);
          }
        }
        break;
      case EXTENSION:
        set.add("Extension");
        break;
      case FHIRPATH:
        set.add("Path");
        break;
      case NULL:
      default:
        set.add("none");
    }
    return set;
  }

  public List<String> getExtensionContexts() {
    return Utilities.sorted(extMap.keySet());
  }

  public String buildExtensionTable() throws Exception {
    return buildExtensionTable(null, extList);
  }

  public String buildExtensionTable(String s) throws Exception {
    if (extMap.containsKey(s)) {
      return buildExtensionTable(s, extMap.get(s));
    } else {
      return buildExtensionTable(s, extMap.get(s));
    }
  }

  private String buildExtensionTable(String type, List<ExtensionDefinition> definitions) throws Exception {
    XhtmlNode x = new XhtmlNode(NodeType.Element, "div");

    String kind;
    if (Utilities.existsInList(type, worker.getResourceNames())) {
      kind = "resource";
    } else {
      kind = "data type";
    }
    var tbl = x.table("list", false).markGenerated(!context.forValidResource());
    var tr = tbl.tr();
    tr.td().tx("Identity");
    tr.td().tx("Card.");
    tr.td().tx("Type");
    tr.td().tx("Context");
    tr.td().tx("WG");
    tr.td().tx("Status");
    int width = 6;
    if (context.getChangeVersion() != null) {
      tr.td().b().tx("Δ v" + context.getChangeVersion() + " ");
      width++;
    }

    tr = tbl.tr();
    tr.td().input(null, "text", null, 15).attribute("class", "filter-input").attribute("id", "filter-identity");
    tr.td().input(null, "text", null, 3).attribute("class", "filter-input").attribute("id", "filter-card");
    tr.td().input(null, "text", null, 8).attribute("class", "filter-input").attribute("id", "filter-type");
    tr.td().input(null, "text", null, 20).attribute("class", "filter-input").attribute("id", "filter-context");
    tr.td().input(null, "text", null, 3).attribute("class", "filter-input").attribute("id", "filter-wg");
    tr.td().input(null, "text", null, 6).attribute("class", "filter-input").attribute("id", "filter-status");
    if (context.getChangeVersion() != null) {
      tr.td().input(null, "checkbox", null, 0).attribute("id", "hideUnchanged");
    }

    if (type != null) {
      if ("Path".equals(type)) {
        tbl.tr().td().colspan(5).b().tx("Extensions defined by a FHIRPath expression");
      } else if ("primitives".equals(type)) {
        tbl.tr().td().colspan(5).b().tx("Extensions defined on primitive types");
      } else {
        tbl.tr().td().colspan(5).b().tx("Extensions defined for the " + type + " " + kind);
      }
    }
    Map<String, StructureDefinition> map = new HashMap<>();
    if (definitions != null) {
      for (ExtensionDefinition sd : definitions)
        map.put(sd.source.getUrl(), sd.source);
    }
    if (map.size() == 0) {
      tr.td().colspan(width).tx("None found");
    } else {
      for (String s : Utilities.sorted(map.keySet())) {
        genExtensionRow(tbl, map.get(s), context.getChangeVersion());
      }
    }

    if (type != null && !Utilities.existsInList(type, "Path", "primitives", "datatypes")) {
      List<String> ancestors = new ArrayList<>();
      StructureDefinition t = worker.fetchTypeDefinition(type);
      if (t != null) {
        t = worker.fetchResource(StructureDefinition.class, t.getBaseDefinition());
        while (t != null) {
          ancestors.add(t.getType());
          t = worker.fetchResource(StructureDefinition.class, t.getBaseDefinition());
        }
      }

      if (Utilities.existsInList(type, worker.getResourceNames())) {
        if (!context.getContextUtilities().isDomainResource(type)) {
          tbl.tr().td().colspan(width).b().tx("Resources of type " + type + " do not have extensions at the root element, but extensions MAY be present on the elements in the resource.");
        } else {
          tbl.tr().td().colspan(width).b().tx("Extensions defined for many resources including the " + type + " resource");
          map = new HashMap<>();
          for (ExtensionDefinition sd : this.extList) {
            if (forAncestor(ancestors, sd)) {
              map.put(sd.source.getUrl(), sd.source);
            }
          }
          if (map.size() == 0) {
            tbl.tr().td().colspan(width).tx("None found");
          } else {
            for (String s : Utilities.sorted(map.keySet())) {
              genExtensionRow(tbl, map.get(s), context.getChangeVersion());
            }
          }

          tbl.tr().td().colspan(width).b().tx("Extensions that refer to the " + type + " resource");
          map = new HashMap<>();
          for (ExtensionDefinition sd : this.extList) {
            if (refersToThisType(type, sd)) {
              map.put(sd.source.getUrl(), sd.source);
            }
          }
          if (map.size() == 0) {
            tbl.tr().td().colspan(width).tx("None found");
          } else {
            for (String s : Utilities.sorted(map.keySet())) {
              genExtensionRow(tbl, map.get(s), context.getChangeVersion());
            }
          }
          tbl.tr().td().colspan(width).b().tx("Extensions that refer to many resources including the " + type + " resource");
          map = new HashMap<>();
          for (ExtensionDefinition sd : this.extList) {
            if (refersToThisTypesAncestors(ancestors, sd)) {
              map.put(sd.source.getUrl(), sd.source);
            }
          }
          if (map.size() == 0) {
            tbl.tr().td().colspan(width).tx("None found");
          } else {
            for (String s : Utilities.sorted(map.keySet())) {
              genExtensionRow(tbl, map.get(s), context.getChangeVersion());
            }
          }
        }
      } else {
        StructureDefinition sd = worker.fetchTypeDefinition(type);
        if (sd != null && sd.hasBaseDefinition()) {
          String bt = Utilities.tail(sd.getBaseDefinitionNoVersion());
          String p = "extensions-types.html#ext-" + bt;
          if ("Base".equals(bt)) {
            // do nothing
          } else {
            if (context.getContextUtilities().isPrimitiveType(bt)) {
              p = "extensions-datatypes.html#" + bt;
            } else {
              switch (bt) {
                case "Element":
                case "BackboneElement":
                case "DataType":
                case "BackboneType":
                case "PrimitiveType":
                  p = "extensions-types.html#" + bt;
                  break;
                case "Attachment":
                case "Coding":
                case "CodeableConcept":
                case "Quantity":
                case "Money":
                case "Range":
                case "Ratio":
                case "RatioRange":
                case "Period":
                case "SampledData":
                case "Identifier":
                case "HumanName":
                case "Address":
                case "ContactPoint":
                case "Timing":
                case "Signature":
                case "Annotation":
                  p = "extensions-datatypes.html#" + bt;
                  break;

                case "ContactDetail":
                case "DataRequirement":
                case "ParameterDefinition":
                case "RelatedArtifact":
                case "TriggerDefinition":
                case "Expression":
                case "UsageContext":
                case "ExtendedContactDetail":
                case "VirtualServiceDetail":
                case "Availability":
                case "MonetaryComponent":
                  p = "extensions-metadatatypes.html#" + bt;
                  break;
                default:
                  break;
              }
            }
          }
          XhtmlNode td = tbl.tr().td().colspan(width);
          td.br();
          td.tx("(See also Extensions defined on ");
          td.ah(p).tx(bt);
          td.tx(")");
        }
      }
    }
    x.para().tx("" + (tbl.getChildNodes().size() - 1) + " Extensions");
    x.jsSrc("assets/js/table.js");

    x.button(null, null).attribute("onclick", "clearAllFilters()").style("padding: 8px 16px; background-color: #f0f0f0; border: 1px solid #ccc; border-radius: 4px; cursor: pointer;").tx("Clear All Filters");
    return new XhtmlComposer(false, true).compose(x.getChildNodes());
  }

  private boolean refersToThisType(String type, ExtensionDefinition sd) {
    String url = "http://hl7.org/fhir/StructureDefinition/" + type;
    for (ElementDefinition ed : sd.source.getSnapshot().getElement()) {
      for (TypeRefComponent t : ed.getType()) {
        for (CanonicalType u : t.getTargetProfile()) {
          if (url.equals(u.getValue())) {
            return true;
          }
        }
      }
    }
    return false;
  }


  private boolean refersToThisTypesAncestors(List<String> ancestors, ExtensionDefinition sd) {
    List<String> urls = new ArrayList<>();
    for (String t : ancestors) {
      urls.add("http://hl7.org/fhir/StructureDefinition/" + t);
    }

    for (ElementDefinition ed : sd.source.getSnapshot().getElement()) {
      for (TypeRefComponent t : ed.getType()) {
        for (CanonicalType u : t.getTargetProfile()) {
          if (urls.contains(u.getValue())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean forAncestor(List<String> ancestors, ExtensionDefinition sd) {
    List<String> types = getExtensionContext(sd.source);
    for (String type : types) {
      if (ancestors.contains(type)) {
        return true;
      }
    }
    return false;
  }

  private void genExtensionRow(XhtmlNode tbl, StructureDefinition ed, String versionToAnnotate) throws Exception {
    StandardsStatus status = ExtensionUtilities.getStandardsStatus(ed);
    XhtmlNode tr;
    if (status == StandardsStatus.DEPRECATED) {
      tr = tbl.tr().style("background-color: #ffeeee");
    } else if (status == StandardsStatus.NORMATIVE) {
      tr = tbl.tr().style("background-color: #f2fff2");
    } else if (status == StandardsStatus.INFORMATIVE) {
      tr = tbl.tr().style("background-color: #fffff6");
    } else {
      tr = tbl.tr();
    }
    tr.clss("data-row");
    tr.td().ah(ed.getWebPath(), ed.getDescription()).tx(ed.getId());
    displayExtensionCardinality(ed, tr.td());
    determineExtensionType(ed, tr.td());
    var td = tr.td();

    boolean first = true;
    int l = 0;
    for (StructureDefinitionContextComponent ec : ed.getContext()) {
      if (first)
        first = false;
      else if (l > 60) {
        td.tx(",");
        td.br();
        l = 0;
      } else {
        td.tx(", ");
        l++;
      }
      l = l + (ec.hasExpression() ? ec.getExpression().length() : 0);
      if (ec.getType() == ExtensionContextType.ELEMENT) {
        String ref = oids.oidRoot(ec.getExpression());
        if (ref.startsWith("@"))
          ref = ref.substring(1);
        if (ref.contains(".")) {
          ref = ref.substring(0, ref.indexOf("."));
        }
        StructureDefinition sd = worker.fetchTypeDefinition(ref);
        if (sd != null && sd.hasWebPath()) {
          td.ah(sd.getWebPath()).tx(ec.getExpression());
        } else {
          td.tx(ec.getExpression());
        }
      } else if (ec.getType() == ExtensionContextType.FHIRPATH) {
        td.tx(Utilities.escapeXml(ec.getExpression()));
      } else if (ec.getType() == ExtensionContextType.EXTENSION) {
        StructureDefinition extension = worker.fetchResource(StructureDefinition.class, ec.getExpression());
        if (extension == null)
          td.tx(Utilities.escapeXml(ec.getExpression()));
        else {
          td.ah(extension.getWebPath()).tx(ec.getExpression());
        }
      } else if (ec.getType() == null) {
        td.tx("??error??: " + Utilities.escapeXml(ec.getExpression()));
      } else {
        throw new Error("Not done yet");
      }
    }

    String wg = ExtensionUtilities.readStringExtension(ed, ExtensionDefinitions.EXT_WORKGROUP);
    if (wg == null) {
      tr.td();
    } else {
      HL7WorkGroup wgd = HL7WorkGroups.find(wg);
      if (wgd == null) {
        tr.td().tx(wg);
      } else {
        tr.td().ah(wgd.getLink()).tx(wg);
      }
    }
    String fmm = ExtensionUtilities.readStringExtension(ed, ExtensionDefinitions.EXT_FMM_LEVEL);
    td = tr.td();
    if (status == StandardsStatus.NORMATIVE) {
      td.ahWithText("", Utilities.pathURL(corePath, "versions.html") + "#std-process", "Normative", "Normative", null).attribute("class", "normative-flag");
    } else if (status == StandardsStatus.DEPRECATED) {
      td.ahWithText("", Utilities.pathURL(corePath, "versions.html") + "#std-process", "Deprecated", "Deprecated", null).attribute("class", "deprecated-flag");
    } else if (status == StandardsStatus.INFORMATIVE) {
      td.ahWithText("", Utilities.pathURL(corePath, "versions.html") + "#std-process", "Informative", "Informative", null).attribute("class", "informative-flag");
    } else if (status == StandardsStatus.DRAFT) {
      if (ed.getExperimental()) {
        td.ahWithText("", Utilities.pathURL(corePath, "canonicalresource-definitions.html") + "#CanonicalResource.experimental", "Experimental", "Experimental", null).attribute("class", "experimental-flag");
        td.br();
      }
      td.ahWithText("", Utilities.pathURL(corePath, "versions.html") + "#std-process", "Draft", "Draft", null).attribute("class", "draft-flag");
    } else {
      td.ahWithText("", Utilities.pathURL(corePath, "versions.html") + "#std-process", "Trial-Use", "Trial-Use", null).attribute("class", "trial-use-flag");
    }
    td.tx(Utilities.noString(fmm) ? "" : ": FMM" + fmm + "");
    if (context.getChangeVersion() != null) {
      if (renderStatusSummary(context, ed, tr.td(), versionToAnnotate)) {
        tr.attribute("data-change", "true");
      } else {
        tr.attribute("data-change", "false");
      }
    } else {
      tr.attribute("data-change", "false");
    }
  }

  private void displayExtensionCardinality(StructureDefinition ed, XhtmlNode x) {
    ElementDefinition e = ed.getSnapshot().getElementFirstRep();
    x.tx(Integer.toString(e.getMin()) + ".." + e.getMax());
    if (ed.getSnapshot().getElementFirstRep().getIsModifier()) {
      x.b().attribute("title", "This is a modifier extension").tx("M");
    }
  }

  private void determineExtensionType(StructureDefinition ed, XhtmlNode x) throws Exception {
    for (ElementDefinition e : ed.getSnapshot().getElement()) {
      if (e.getPath().startsWith("Extension.value") && !"0".equals(e.getMax())) {
        if (e.getType().size() == 1) {
          StructureDefinition sd = worker.fetchTypeDefinition(e.getType().get(0).getWorkingCode());
          if (sd != null) {
            x.ah(sd.getWebPath()).tx(e.getType().get(0).getWorkingCode());
            return;
          } else {
            x.tx(e.getType().get(0).getWorkingCode());
            return;
          }
        } else if (e.getType().size() == 0) {
          // nothing
        } else {
          x.tx("(Choice)");
          return;
        }
      }
    }
    x.tx("(complex)");
  }


  public class SearchParameterListSorter implements Comparator<SearchParameter> {

    @Override
    public int compare(SearchParameter arg0, SearchParameter arg1) {
      String u0 = arg0.getUrl();
      String u1 = arg1.getUrl();
      return u0.compareTo(u1);
    }
  }

  public String buildExtensionSearchTable(String s) {
    List<SearchParameter> list = new ArrayList<>();
    for (SearchParameter sp : searchParams) {
      if (sp.hasBase(s)) {
        list.add(sp);
      }
    }
    return genSearchList(list);
  }

  public String genSearchList(List<SearchParameter> list) {
    if (list.size() == 0) {
      return "<p>(none found)</p>";
    } else {
      Collections.sort(list, new SearchParameterListSorter());
      StringBuilder b = new StringBuilder();
      b.append("<ul>\r\n");
      for (SearchParameter sp : list) {
        if (sp.hasDescription()) {
          b.append(" <li><a href=\"" + sp.getWebPath() + "\">" + Utilities.escapeXml(sp.present()) + "</a>: " + Utilities.escapeXml(sp.getDescription()) + "</li>\r\n");
        } else {
          b.append(" <li><a href=\"" + sp.getWebPath() + "\">" + Utilities.escapeXml(sp.present()) + "</a></li>\r\n");
        }
      }
      b.append("</ul>\r\n");
      return b.toString();
    }
  }

  public List<String> getExtensionIds() {
    List<String> ret = new ArrayList<>();
    for (StructureDefinition ext : allExtensions) {
      ret.add(ext.getId());
    }
    return ret;
  }

  public String buildSearchTableForExtension(String id) {
    StructureDefinition ext = null;
    for (StructureDefinition t : allExtensions) {
      if (t.getId().equals(id)) {
        ext = t;
        break;
      }
    }
    if (ext == null) {
      return "<p>Unknown Extension " + id + "</p>";
    } else {
      List<SearchParameter> list = new ArrayList<>();
      for (SearchParameter sp : searchParams) {
        ExpressionNode n = (ExpressionNode) sp.getExpressionElement().getUserData(UserDataNames.xver_expression);
        if (n != null && refersToExtension(n, ext.getUrl())) {
          list.add(sp);
        }
      }
      return genSearchList(list);
    }
  }

  private boolean refersToExtension(ExpressionNode n, String url) {
    if (n != null && n.getKind() == Kind.Function && "extension".equals(n.getName()) && n.getParameters().size() == 1) {
      ExpressionNode p = n.getParameters().get(0);
      if (p.getConstant() != null && p.getConstant().hasPrimitiveValue()) {
        return p.getKind() == Kind.Constant && p.getConstant().primitiveValue().equals(url);
      }
    }
    if (n.getInner() != null) {
      if (refersToExtension(n.getInner(), url)) {
        return true;
      }
    }
    if (n.getGroup() != null) {
      if (refersToExtension(n.getGroup(), url)) {
        return true;
      }
    }
    return false;
  }


  public List<ValueSet> buildUsedValueSetList(boolean all, List<FetchedFile> fileList) throws IOException {
    List<ValueSet> vslist = new ArrayList<>();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null) {
          findValueSetReferences(vslist, r.getResource(), all);
        }
      }
    }
    return vslist;
    //    return renderVSList(versionToAnnotate, x, vslist, true, true);
  }

  private void findValueSetReferences(List<ValueSet> vslist, Resource resource, boolean all) {
    if (resource instanceof StructureDefinition) {
      StructureDefinition sd = (StructureDefinition) resource;
      findValueSets(vslist, sd, all);
    }
    if (resource instanceof Questionnaire) {
      Questionnaire q = (Questionnaire) resource;
      findValueSets(vslist, q);
    }
    if (resource instanceof ValueSet) {
      ValueSet vs = (ValueSet) resource;
      findValueSets(vslist, vs, all);
    }
    if (resource instanceof ConceptMap) {
      ConceptMap sd = (ConceptMap) resource;
      findValueSets(vslist, sd);
    }
    if (resource instanceof OperationDefinition) {
      OperationDefinition sd = (OperationDefinition) resource;
      findValueSets(vslist, sd);
    }
    if (resource instanceof DomainResource) {
      DomainResource dr = (DomainResource) resource;
      for (Resource r : dr.getContained()) {
        findValueSetReferences(vslist, r, all);
      }
    }
  }

  private void findValueSets(List<ValueSet> list, OperationDefinition opd) {
    for (OperationDefinitionParameterComponent p : opd.getParameter()) {
      if (p.hasBinding()) {
        resolveVS(list, p.getBinding().getValueSet(), opd);
      }
      ;
    }
  }

  private void findValueSets(List<ValueSet> list, ValueSet vs, boolean all) {
    if (!list.contains(vs)) {
      list.add(vs);
    }
    for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
      for (CanonicalType u : inc.getValueSet()) {
        resolveVS(list, u, vs);
      }
    }
    if (all) {
      for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
        for (CanonicalType u : inc.getValueSet()) {
          resolveVS(list, u, vs);
        }
      }
    }
  }

  private void findValueSets(List<ValueSet> list, ConceptMap cm) {
    resolveVS(list, cm.getSourceScope(), cm);
    resolveVS(list, cm.getTargetScope(), cm);
  }

  private void findValueSets(List<ValueSet> list, Questionnaire q) {
    for (QuestionnaireItemComponent item : q.getItem()) {
      findValueSets(list, item, q);
    }
  }

  private void findValueSets(List<ValueSet> list, QuestionnaireItemComponent item, Resource source) {
    resolveVS(list, item.getAnswerValueSet(), source);
    for (QuestionnaireItemComponent c : item.getItem()) {
      findValueSets(list, c, source);
    }
  }

  private void findValueSets(List<ValueSet> list, StructureDefinition sd, boolean all) {
    if (all) {
      for (ElementDefinition ed : sd.getSnapshot().getElement()) {
        findValueSets(list, ed, sd);
      }
    } else {
      for (ElementDefinition ed : sd.getDifferential().getElement()) {
        findValueSets(list, ed, sd);
      }
    }
  }

  private void findValueSets(List<ValueSet> list, ElementDefinition ed, Resource source) {
    if (ed.hasBinding()) {
      resolveVS(list, ed.getBinding().getValueSet(), source);
      for (ElementDefinitionBindingAdditionalComponent ab : ed.getBinding().getAdditional()) {
        resolveVS(list, ab.getValueSet(), source);
      }
    }
  }

  private void resolveVS(List<ValueSet> list, DataType ref, Resource source) {
    if (ref != null && ref.isPrimitive()) {
      resolveVS(list, ref.primitiveValue(), source);
    }
  }


  private void resolveVS(List<ValueSet> list, String url, Resource source) {
    if (url != null) {
      ValueSet vs = context.getContext().findTxResource(ValueSet.class, url);
      if (vs != null) {
        if (!vs.hasUserData(UserDataNames.pub_xref_used)) {
          vs.setUserData(UserDataNames.pub_xref_used, new HashSet<>());
        }
        Set<Resource> rl = (Set<Resource>) vs.getUserData(UserDataNames.pub_xref_used);
        rl.add(source);
        if (!list.contains(vs)) {
          list.add(vs);
        }
      }
    }
  }

  public List<ValueSet> buildDefinedValueSetList(List<FetchedFile> fileList) throws IOException {
    List<ValueSet> vslist = new ArrayList<>();

    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("ValueSet")) {
          ValueSet vs = (ValueSet) r.getResource();
          vslist.add(vs);
        }
      }
    }
    return vslist;
    //    return renderVSList(versionToAnnotate, x, vslist, versions, false);
  }

  public boolean needVersionReferences(List<? extends CanonicalResource> list, String thisVersion) throws IOException {
    boolean versions = false;
    for (CanonicalResource vs : list) {
      versions = !(thisVersion.equals(vs.getVersion())) || versions;
    }
    return versions;
  }

  public String renderVSList(String versionToAnnotate, List<ValueSet> vslist, boolean versions, boolean used)
          throws IOException {

    XhtmlNode x = new XhtmlNode(NodeType.Element, "div");
    Collections.sort(vslist, new CanonicalResourceSortByUrl());
    var tbl = x.table("grid", false);
    var tr = tbl.tr();
    tr.th().tx("URL");
    if (versions) {
      tr.th().tx("Version");
    }
    tr.th().tx("Name / Title");
    tr.th().tx("Status");
    tr.th().tx("Flags");
    tr.th().tx("Source");
    if (used) {
      tr.th().tx("References");
    }
    if (versionToAnnotate != null) {
      var td = tr.th();
      td.tx("Δ v");
      td.tx(versionToAnnotate);
    }
    for (ValueSet vs : vslist) {
      tr = tbl.tr();
      renderStatus(vs.getUrlElement(), tr.td().ah(vs.getWebPath())).tx(vs.getUrl());
      if (versions) {
        renderStatus(vs.getVersionElement(), tr.td()).tx(vs.getVersion());
      }
      var td = tr.td();
      renderStatus(vs.getNameElement(), td).tx(vs.getName());
      td.br();
      renderStatus(vs.getTitleElement(), td).tx(vs.getTitle());
      td = tr.td();
      renderStatus(vs.getStatusElement(), td).tx(vs.getStatus() == null ? "null" : vs.getStatus().toCode());
      if (vs.hasExtension(ExtensionDefinitions.EXT_STANDARDS_STATUS)) {
        td.tx(" / ");
        Extension ext = vs.getExtensionByUrl(ExtensionDefinitions.EXT_STANDARDS_STATUS);
        String v = ExtensionUtilities.getStandardsStatus(vs).toCode();
        renderStatus(ext, td).attribute("class", v + "-flag").tx(v);
      }
      if (vs.hasExtension(ExtensionDefinitions.EXT_FMM_LEVEL)) {
        td.tx(" / ");
        Extension ext = vs.getExtensionByUrl(ExtensionDefinitions.EXT_FMM_LEVEL);
        renderStatus(ext, td).tx("FMM" + ext.getValue().primitiveValue());
      }
      if (vs.getExperimental()) {
        td.tx(":");
        renderStatus(vs.getExperimentalElement(), td).tx("experimental");
      }
      td = tr.td();

      if (vs.getCompose().hasLockedDate()) {
        renderStatus(vs.getCompose().getLockedDateElement(), td).tx("Locked-Date");
        td.tx(" ");
      }
      if (vs.getCompose().getInactive()) {
        renderStatus(vs.getCompose().getInactiveElement(), td).tx("Inactive");
        td.tx(" ");
      }
      boolean i = false;
      boolean e = false;
      boolean v = false;
      boolean a = false;
      Set<String> sources = new HashSet<>();
      for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
        if (inc.hasValueSet()) {
          v = true;
        }
        if (inc.hasSystem()) {
          sources.add(describeSource(inc.getSystem()));
          if (inc.hasConcept()) {
            e = true;
          } else if (inc.hasFilter()) {
            i = true;
          } else {
            a = true;
          }
        }
      }
      if (a) {
        td.span(null, "All Code System").tx("A ");
      }
      if (i) {
        td.span(null, "Intensional").tx("I ");
      }
      if (e) {
        td.span(null, "Extensional").tx("E ");
      }
      if (v) {
        td.span(null, "Imports Valueset(s)").tx("V ");
      }

      vs.setUserData(UserDataNames.pub_xref_sources, sources);
      td = tr.td();
      for (String s : Utilities.sorted(sources)) {
        td.sep(", ");
        td.tx(s);
      }
      if (used) {
        td = tr.td();
        Set<Resource> rl = (Set<Resource>) vs.getUserData(UserDataNames.pub_xref_used);
        if (rl != null) {
          if (rl.size() < 10) {
            for (Resource r : rl) {
              String title = (r instanceof CanonicalResource) ? ((CanonicalResource) r).present() : r.fhirType() + "/" + r.getIdBase();
              String link = r.getWebPath();
              td.sep(", ");
              td.ah(link).tx(title);
            }
          } else {
            td.tx("" + rl.size() + " references");
          }
        }
      }

      if (versionToAnnotate != null) {
        renderStatusSummary(context, vs, tr.td(), versionToAnnotate, "url", "name", "title", "version", "status", "experimental");
      }
    }

    return new XhtmlComposer(false, false).compose(x.getChildNodes());
  }

  private String describeSource(String uri) {
    CodeSystem cs = worker.fetchCodeSystem(uri);
    if (cs != null) {
      if (!Utilities.isAbsoluteUrl(cs.getWebPath())) {
        return "Internal";
      }
    }
    if ("http://snomed.info/sct".equals(uri)) return "SCT";
    if ("http://loinc.org".equals(uri)) return "LOINC";
    if ("http://dicom.nema.org/resources/ontology/DCM".equals(uri)) return "DICOM";
    if ("http://unitsofmeasure.org".equals(uri)) return "UCUM";
    if ("http://www.nlm.nih.gov/research/umls/rxnorm".equals(uri)) return "RxNorm";
    if (uri.startsWith("http://terminology.hl7.org/CodeSystem/v3-")) return "THO (V3)";
    if (uri.startsWith("http://terminology.hl7.org/CodeSystem/v2-")) return "THO (V2)";
    if (uri.startsWith("http://terminology.hl7.org")) return "THO";
    if (cs != null && cs.hasSourcePackage()) {
      return cs.getSourcePackage().getId();
    }
    if (uri.startsWith("http://hl7.org/fhir")) return "FHIR";
    return "Other";
  }

  public List<CodeSystem> buildDefinedCodeSystemList(List<FetchedFile> fileList) throws IOException {
    List<CodeSystem> cslist = new ArrayList<>();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("CodeSystem")) {
          CodeSystem cs = (CodeSystem) r.getResource();
          cslist.add(cs);
        }
      }
    }
    return cslist;
    //    return renderCSList(versionToAnnotate, x, cslist, versions, false);
  }


  public List<CodeSystem> buildUsedCodeSystemList(boolean all, List<FetchedFile> fileList) throws IOException {
    XhtmlNode x = new XhtmlNode(NodeType.Element, "div");
    List<CodeSystem> cslist = new ArrayList<>();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null) {
          findCodeSystemReferences(cslist, r.getResource(), all);
        }
      }
    }
    return cslist;
    //    return renderCSList(versionToAnnotate, x, cslist, true, true);
  }

  private void findCodeSystemReferences(List<CodeSystem> cslist, Resource resource, boolean all) {
    if (resource instanceof StructureDefinition) {
      StructureDefinition sd = (StructureDefinition) resource;
      findCodeSystems(cslist, sd, all);
    }
    if (resource instanceof Questionnaire) {
      Questionnaire q = (Questionnaire) resource;
      findCodeSystems(cslist, q, all);
    }
    if (resource instanceof ValueSet) {
      ValueSet vs = (ValueSet) resource;
      findCodeSystems(cslist, vs, all, vs);
    }
    if (resource instanceof ConceptMap) {
      ConceptMap sd = (ConceptMap) resource;
      findCodeSystems(cslist, sd, all);
    }
    if (resource instanceof OperationDefinition) {
      OperationDefinition sd = (OperationDefinition) resource;
      findCodeSystems(cslist, sd, all);
    }
    if (resource instanceof DomainResource) {
      DomainResource dr = (DomainResource) resource;
      for (Resource r : dr.getContained()) {
        findCodeSystemReferences(cslist, r, all);
      }
    }
  }

  private void findCodeSystems(List<CodeSystem> list, OperationDefinition opd, boolean all) {
    for (OperationDefinitionParameterComponent p : opd.getParameter()) {
      if (p.hasBinding()) {
        resolveCSFromVS(list, p.getBinding().getValueSet(), all, opd);
      }
    }
  }

  private void findCodeSystems(List<CodeSystem> list, ConceptMap cm, boolean all) {
    resolveCSFromVS(list, cm.getSourceScope(), all, cm);
    resolveCSFromVS(list, cm.getTargetScope(), all, cm);
    for (ConceptMapGroupComponent grp : cm.getGroup()) {
      resolveCS(list, grp.getSource(), cm);
      resolveCS(list, grp.getTarget(), cm);
    }
  }

  private void findCodeSystems(List<CodeSystem> list, Questionnaire q, boolean all) {
    for (QuestionnaireItemComponent item : q.getItem()) {
      findCodeSystems(list, item, all, q);
    }
  }

  private void findCodeSystems(List<CodeSystem> list, QuestionnaireItemComponent item, boolean all, Resource source) {
    resolveCSFromVS(list, item.getAnswerValueSet(), all, source);
    for (QuestionnaireItemComponent c : item.getItem()) {
      findCodeSystems(list, c, all, source);
    }
  }

  private void findCodeSystems(List<CodeSystem> list, StructureDefinition sd, boolean all) {
    if (all) {
      for (ElementDefinition ed : sd.getSnapshot().getElement()) {
        findCodeSystems(list, ed, all, sd);
      }
    } else {
      for (ElementDefinition ed : sd.getDifferential().getElement()) {
        findCodeSystems(list, ed, all, sd);
      }
    }
  }

  private void findCodeSystems(List<CodeSystem> list, ElementDefinition ed, boolean all, Resource source) {
    if (ed.hasBinding()) {
      resolveCSFromVS(list, ed.getBinding().getValueSet(), all, source);
      for (ElementDefinitionBindingAdditionalComponent ab : ed.getBinding().getAdditional()) {
        resolveCSFromVS(list, ab.getValueSet(), all, source);
      }
    }
  }

  private void findCodeSystems(List<CodeSystem> list, ValueSet vs, boolean all, Resource source) {
    for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
      resolveCS(list, inc.getSystem(), vs);
    }
    if (all) {
      for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
        resolveCS(list, inc.getSystem(), vs);
      }
    }
  }

  private void resolveCSFromVS(List<CodeSystem> list, String valueSet, boolean all, Resource resource) {
    if (valueSet != null) {
      ValueSet vs = context.getContext().findTxResource(ValueSet.class, valueSet);
      if (vs != null) {
        findCodeSystems(list, vs, all, resource);
      }
    }
  }

  private void resolveCSFromVS(List<CodeSystem> list, DataType ref, boolean all, Resource resource) {
    if (ref != null && ref.isPrimitive()) {
      resolveCSFromVS(list, ref.primitiveValue(), all, resource);
    }
  }

  private void resolveCS(List<CodeSystem> list, String url, Resource source) {
    if (url != null) {
      CodeSystem cs = context.getContext().fetchResource(CodeSystem.class, url);
      if (cs != null) {
        if (!cs.hasUserData(UserDataNames.pub_xref_used)) {
          cs.setUserData(UserDataNames.pub_xref_used, new HashSet<>());
        }
        Set<Resource> rl = (Set<Resource>) cs.getUserData(UserDataNames.pub_xref_used);
        rl.add(source);
        if (!list.contains(cs)) {
          list.add(cs);
        }
      }
    }
  }

  public String renderCSList(String versionToAnnotate, List<CodeSystem> cslist, boolean versions, boolean used) throws IOException {
    XhtmlNode x = new XhtmlNode(NodeType.Element, "div");
    Collections.sort(cslist, new CanonicalResourceSortByUrl());
    var tbl = x.table("grid", false);
    var tr = tbl.tr();
    tr.th().tx("URL");
    if (versions) {
      tr.th().tx("Version");
    }
    tr.th().tx("Name / Title");
    tr.th().tx("Status");
    tr.th().tx("Flags");
    tr.th().tx("Count");
    if (used) {
      tr.th().tx("References");
    }
    if (versionToAnnotate != null) {
      var td = tr.th();
      td.tx("Δ v");
      td.tx(versionToAnnotate);
    }
    for (CodeSystem cs : cslist) {
      tr = tbl.tr();
      renderStatus(cs.getUrlElement(), tr.td().ah(cs.getWebPath())).tx(cs.getUrl());
      if (versions) {
        tr.td().tx(cs.getVersion());
      }
      var td = tr.td();
      renderStatus(cs.getNameElement(), td).tx(cs.getName());
      td.br();
      renderStatus(cs.getTitleElement(), td).tx(cs.getTitle());
      td = tr.td();
      renderStatus(cs.getStatusElement(), td).tx(cs.getStatus().toCode());
      if (cs.hasExtension(ExtensionDefinitions.EXT_STANDARDS_STATUS)) {
        td.tx(" / ");
        Extension ext = cs.getExtensionByUrl(ExtensionDefinitions.EXT_STANDARDS_STATUS);
        String v = ExtensionUtilities.getStandardsStatus(cs).toCode();
        renderStatus(ext, td).attribute("class", v + "-flag").tx(v);
      }
      if (cs.hasExtension(ExtensionDefinitions.EXT_FMM_LEVEL)) {
        td.tx(" / ");
        Extension ext = cs.getExtensionByUrl(ExtensionDefinitions.EXT_FMM_LEVEL);
        renderStatus(ext, td).tx("FMM" + ext.getValue().primitiveValue());
      }
      if (cs.getExperimental()) {
        td.tx(": ");
        renderStatus(cs.getExperimentalElement(), td).tx("experimental");
      }
      td = tr.td();
      if (cs.hasHierarchyMeaning()) {
        renderStatus(cs.getHierarchyMeaningElement(), td).tx(cs.getHierarchyMeaning().toCode());
        td.tx(" ");
      }
      if (!CodeSystemUtilities.hasHierarchy(cs)) {
        td.tx("flat ");
      }
      if (cs.hasCompositional() && cs.getCompositional()) {
        renderStatus(cs.getCompositionalElement(), td).tx("compositional");
        td.tx(" ");
      }
      if (cs.hasVersionNeeded() && cs.getVersionNeeded()) {
        renderStatus(cs.getVersionNeededElement(), td).tx("version-needed");
        td.tx(" ");
      }

      td = tr.td();
      td.tx(CodeSystemUtilities.countCodes(cs));
      if (cs.hasContent()) {
        td.tx(" (");
        td.tx(cs.getContent().toCode());
        td.tx(")");
      }
      if (used) {
        td = tr.td();
        Set<Resource> rl = (Set<Resource>) cs.getUserData(UserDataNames.pub_xref_used);
        if (rl != null) {
          if (rl.size() < 10) {
            for (Resource r : rl) {
              String title = (r instanceof CanonicalResource) ? ((CanonicalResource) r).present() : r.fhirType() + "/" + r.getIdBase();
              String link = r.getWebPath();
              td.sep(", ");
              td.ah(link).tx(title);
            }
          } else {
            td.tx("" + rl.size() + " references");
          }
        }
      }

      if (versionToAnnotate != null) {
        renderStatusSummary(context, cs, tr.td(), versionToAnnotate, "url", "name", "title", "version", "status", "experimental", "hierarchyMeaning", "versionNeeded", "compositional");
      }
    }

    return new XhtmlComposer(false, false).compose(x.getChildNodes());
  }


  public String renderObligationSummary() throws IOException {
    CodeSystem cs = context.getContext().fetchCodeSystem("http://hl7.org/fhir/CodeSystem/obligation");
    ObligationsAnalysis oa = ObligationsAnalysis.build(allProfiles);
    XhtmlNode x = new XhtmlNode(NodeType.Element, "div");
    XhtmlNode tbl = x.table("grid");
    XhtmlNode tr = tbl.tr();
    tr.th().tx("Obligations");
    XhtmlNode tr2 = tbl.tr();
    tr2.td();
    if (oa.hasNullActor()) {
      actorHeader(tr, tr2, oa, null, cs);
    }
    List<String> actors = Utilities.sorted(oa.getActors().keySet());
    for (String a : actors) {
      actorHeader(tr, tr2, oa, a, cs);
    }

    for (StructureDefinition sd : allProfiles) {
      ProfileObligationsAnalysis p = oa.getProfile(sd);
      if (p != null) {
        tr = tbl.tr();
        tr.td().ah(sd.getWebPath()).tx(sd.present());
        if (oa.hasNullActor()) {
          presentActor(tr, oa.getActors().get(null), p.actor(null), cs);
        }
        for (String a : actors) {
          presentActor(tr, oa.getActors().get(a), p.actor(a), cs);
        }
      }
    }
    return new XhtmlComposer(false, false).compose(x.getChildNodes());
  }

  private void presentActor(XhtmlNode tr, ActorInfo ai, ProfileActorObligationsAnalysis actor, CodeSystem cs) {
    if (actor == null) {
      for (String code : Utilities.sorted(ai.getCommonObligations())) {
        tr.td();
      }
      if (ai.hasOthers()) {
        tr.td();
      }
      return;
    }
    boolean first = true;
    for (String code : Utilities.sorted(ai.getCommonObligations())) {
      XhtmlNode td = tr.td();
      if (first) {
        td.style(HARD_BORDER);
        first = false;
      }
      if (actor.getObligations().contains(code)) {
        td.img("mustsupport.png", "Yes");
      }
    }
    if (ai.hasOthers()) {
      XhtmlNode td = tr.td();
      if (first) {
        td.style(HARD_BORDER);
      }
      for (String c : actor.getObligations()) {
        if (!ai.getCommonObligations().contains(c)) {
          td.sep(", ");
          td.ah("https://hl7.org/fhir/extensions/CodeSystem-obligation.html#obligation-" + Utilities.nmtokenize(c), title(cs, c)).tx(c);
        }
      }
    }

  }

  private void actorHeader(XhtmlNode tr, XhtmlNode tr2, ObligationsAnalysis oa, String a, CodeSystem cs) {
    ActorInfo ai = oa.getActors().get(a);
    if (a == null) {
      tr.th().colspan(ai.colspan()).style(HARD_BORDER).tx("All Actors");
    } else {
      ActorDefinition ad = context.getContext().fetchResource(ActorDefinition.class, a);
      if (ad == null) {
        tr.th().colspan(ai.colspan()).style(HARD_BORDER).span(null, a).code().tx(tail(a));
      } else {
        tr.th().colspan(ai.colspan()).style(HARD_BORDER).ah(ad.getWebPath()).tx(ad.present());
      }
    }
    boolean first = true;
    for (String s : Utilities.sorted(ai.getCommonObligations())) {
      XhtmlNode td = tr2.td();
      if (first) {
        td.style(HARD_BORDER);
        first = false;
      }
      td.ah("https://hl7.org/fhir/extensions/CodeSystem-obligation.html#obligation-" + s.replace(":", ".58"), title(cs, s)).tx(s.replace(":", ":\u200B"));
    }
    if (ai.hasOthers()) {
      XhtmlNode td = tr2.td();
      if (first) {
        td.style(HARD_BORDER);
      }
      td.tx("Others");
    }
  }

  private String tail(String url) {
    return url.contains("/") ? url.substring(url.lastIndexOf("/") + 1) : url;
  }

  private String title(CodeSystem cs, String s) {
    if (cs == null) {
      return null;
    }
    ConceptDefinitionComponent d = CodeSystemUtilities.getCode(cs, s);
    if (d == null) {
      return null;
    }
    return d.getDefinition();
  }

}
