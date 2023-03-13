package org.hl7.fhir.igtools.renderers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.r5.context.CanonicalResourceManager.CanonicalListSorter;
import org.hl7.fhir.r5.context.ContextUtilities;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.context.IWorkerContext.ValidationResult;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionBindingComponent;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.ExpressionNode;
import org.hl7.fhir.r5.model.ExpressionNode.Kind;
import org.hl7.fhir.r5.model.SearchParameter;
import org.hl7.fhir.r5.model.StructureDefinition.ExtensionContextType;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionContextComponent;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.r5.renderers.TerminologyRenderer;
import org.hl7.fhir.r5.utils.FHIRPathEngine;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.utilities.StandardsStatus;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.validation.ValidationOptions;

public class CrossViewRenderer {

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
  private IWorkerContext context;
  private List<ObservationProfile> obsList = new ArrayList<>();
  private List<ExtensionDefinition> extList = new ArrayList<>();
  private Map<String, List<ExtensionDefinition>> extMap = new HashMap<>();
  public List<StructureDefinition> allExtensions = new ArrayList<>();

  public List<String> baseEffectiveTypes = new ArrayList<>();
  public List<String> baseTypes = new ArrayList<>();
  public List<String> baseExtTypes = new ArrayList<>();
  public String corePath;
  private ContextUtilities cu;
  private FHIRPathEngine fpe;
  private List<SearchParameter> searchParams = new ArrayList<>();

  public CrossViewRenderer(String canonical, String canonical2, IWorkerContext context, String corePath) {
    super();
    this.canonical = canonical;
    this.canonical2 = canonical2;
    this.context = context;
    this.corePath = corePath;
    getBaseTypes();
    cu = new ContextUtilities(context);
    fpe = new FHIRPathEngine(context);
  }

  private void getBaseTypes() {
    StructureDefinition sd = context.fetchTypeDefinition("Observation");
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
    sd = context.fetchTypeDefinition("Extension");
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
      sp.getExpressionElement().setUserData("expression", n);
    } catch (Exception e) {
      // do nothing in this case
    }
    searchParams.add(sp);    
  }

  public void seeStructureDefinition(StructureDefinition sd) {
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
    return ed.getMustSupport() || "true".equals(ToolingExtensions.readStringExtension(tr, ToolingExtensions.EXT_MUST_SUPPORT));
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
    if (sd.getUrl().startsWith(canonical)) {
      code = sd.getUrl().substring(canonical.length()+21);
    } else if (canonical2 != null && sd.getUrl().startsWith(canonical2)) {
      code = sd.getUrl().substring(canonical2.length()+21);
    } else {
     //  System.out.println("extension url doesn't follow canonical pattern: "+sd.getUrl()+", so omitted from extension summary");
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
        i = processExtensionComponent(exd, sd.getSnapshot().getElement(), sd.getSnapshot().getElement().get(i-1).getDefinition(), i);
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
            System.out.println("extension code doesn't follow canonical pattern: "+exd.code);
          } else { 
            exd.code = exd.code.substring(canonical.length() + 21);
          }
        }
        if (canonical2 != null && exd.code.startsWith(canonical2)) {
          if (exd.code.length() > canonical2.length() + 21) {
            System.out.println("extension code doesn't follow canonical2 pattern: "+exd.code);
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
        b.append("<td><a href=\""+op.source.getUserString("path")+"\">"+op.code+"</a></td>");
        renderTypeCell(b, true, op.types, baseExtTypes);        
        b.append("<td>"+Utilities.escapeXml(op.definition)+"</td>");
        b.append("</tr>\r\n");
        for (ExtensionDefinition inner : op.components) {
          b.append(" <tr>");
          b.append("<td>&nbsp;&nbsp;"+inner.code+"</td>");
          renderTypeCell(b, true, inner.types, baseExtTypes);        
          b.append("<td>"+Utilities.escapeXml(inner.definition)+"</td>");
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
            b.append("<p>All the observations have the category "+new DataRenderer(context).displayCoding(cat)+"</p>\r\n");
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

        b.append("<td><a href=\""+op.source.getUserString("path")+"\" title=\""+op.source.present()+"\">"+op.source.getId()+"</a></td>");
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
          b.append("<td>&nbsp;&nbsp;"+op2.name+"</td>");
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
          if (first) first = false; else b.append(" | ");
          StructureDefinition sd = context.fetchTypeDefinition(t.name);
          if (sd != null) {
            b.append("<a href=\""+sd.getUserString("path")+"\" title=\""+t.name+"\">"+t.name+"</a>");
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
        b.append("<a href=\""+Utilities.pathURL(corePath, "terminologies.html#"+binding.getStrength().toCode())+"\">"+binding.getStrength().toCode()+"</a> VS ");           
        ValueSet vs = context.fetchResource(ValueSet.class, binding.getValueSet());
        if (vs == null) {
          b.append(Utilities.escapeXml(binding.getValueSet()));                     
        } else if (vs.hasUserData("path")) {
          b.append("<a href=\""+vs.getUserString("path")+"\">"+Utilities.escapeXml(vs.present())+"</a>");
        } else { 
          b.append(Utilities.escapeXml(vs.present()));
        }
      } else {
        for (Coding t : list) {
          if (first) first = false; else b.append(", ");
          String sys = TerminologyRenderer.describeSystem(t.getSystem());
          if (sys.equals(t.getSystem()))
            sys = null;
          if (sys == null) {
            CodeSystem cs = context.fetchCodeSystem(t.getSystem());
            if (cs != null)
              sys = cs.getTitle();
          }
          t.setUserData("desc", sys);
          ValidationResult vr = context.validateCode(ValidationOptions.defaults(), t.getSystem(), t.getVersion(), t.getCode(), null);
          if (vr != null & vr.getDisplay() != null) {
            //          if (Utilities.existsInList(t.getSystem(), "http://loinc.org"))
            //            b.append("<span title=\""+t.getSystem()+(sys == null ? "" : " ("+sys+")")+": "+ vr.getDisplay()+"\">"+t.getCode()+" "+vr.getDisplay()+"</span>");           
            //          else {
            CodeSystem cs = context.fetchCodeSystem(t.getSystem());
            if (cs != null && cs.hasUserData("path")) {
              b.append("<a href=\""+cs.getUserString("path")+"#"+cs.getId()+"-"+t.getCode()+"\" title=\""+t.getSystem()+(sys == null ? "" : " ("+sys+")")+": "+ vr.getDisplay()+"\">"+t.getCode()+"</a>");                  
            } else {
              b.append("<span title=\""+t.getSystem()+(sys == null ? "" : " ("+sys+")")+": "+ vr.getDisplay()+"\">"+t.getCode()+"</span>");
            }
            //          }
          } else {
            b.append("<span title=\""+t.getSystem()+(sys == null ? "" : " ("+sys+"): ")+"\">"+t.getCode()+"</span>");           
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
      if (s.contains(".")) {
        s = s.substring(0, s.indexOf("."));
      }
      if (cu.isPrimitiveDatatype(s)) {
        set.add("primitives");
      }
      if (cu.isDatatype(s)) {
        set.add("datatypes");
      } 
      set.add(s);
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
    StringBuilder b = new StringBuilder();

    String kind;
    if (Utilities.existsInList(type, context.getResourceNames())) {
      kind = "resource";
    } else {
      kind = "data type";
    }
    b.append("<table class=\"list\">\r\n");
    b.append("<tr>");
    b.append("<td><b>Identity</b><a name=\"ext-"+type+"\"> </a></td>");
    b.append("<td><b><a href=\""+Utilities.pathURL(context.getSpecUrl(), "defining-extensions.html")+"#cardinality\">Conf.</a></b></td>");
    b.append("<td><b>Type</b></td>");
    b.append("<td><b><a href=\""+Utilities.pathURL(context.getSpecUrl(), "defining-extensions.html")+"#context\">Context</a></b></td>");
    b.append("<td><b><a href=\""+Utilities.pathURL(context.getSpecUrl(), "versions.html")+"#maturity\">FMM</a></b></td>");
    b.append("</tr>");
    if (type != null) {
      if ("Path".equals(type)) {
        b.append("<tr><td colspan=\"5\"><b>Extensions defined by a FHIRPath expression</b></td></tr>\r\n");
      } else if ("primitives".equals(type)) {
        b.append("<tr><td colspan=\"5\"><b>Extensions defined on primitive types</b></td></tr>\r\n");
      } else {
        b.append("<tr><td colspan=\"5\"><b>Extensions defined for the "+type+" "+kind+"</b></td></tr>\r\n");
      }
    }
    Map<String, StructureDefinition> map = new HashMap<>();
    if (definitions != null) {
      for (ExtensionDefinition sd : definitions)
        map.put(sd.source.getUrl(), sd.source);
    }
    if (map.size() == 0) {
      b.append("<tr><td colspan=\"5\">(None found)</td></tr>\r\n");      
    } else {
      for (String s : Utilities.sorted(map.keySet())) {
        genExtensionRow(b, map.get(s));
      }
    }

    if (type != null && !Utilities.existsInList(type, "Path", "primitives", "datatypes")) {
      List<String> ancestors = new ArrayList<>();
      StructureDefinition t = context.fetchTypeDefinition(type);
      if (t != null) {
        t = context.fetchResource(StructureDefinition.class, t.getBaseDefinition());
        while (t != null) {
          ancestors.add(t.getType());
          t = context.fetchResource(StructureDefinition.class, t.getBaseDefinition());
        }
      }
      
      if (Utilities.existsInList(type, context.getResourceNames())) {
        b.append("<tr><td colspan=\"5\"><b>Extensions defined for many resources including the "+type+" resource</b></td></tr>\r\n");
        map = new HashMap<>();
        for (ExtensionDefinition sd : this.extList) {
          if (forAncestor(ancestors, sd)) {
            map.put(sd.source.getUrl(), sd.source);
          }
        }
        if (map.size() == 0) {
          b.append("<tr><td colspan=\"5\">(None found)</td></tr>\r\n");      
        } else {
          for (String s : Utilities.sorted(map.keySet())) {
            genExtensionRow(b, map.get(s));
          }
        }

        b.append("<tr><td colspan=\"5\"><b>Extensions that refer to the "+type+" resource</b></td></tr>\r\n");
        map = new HashMap<>();
        for (ExtensionDefinition sd : this.extList) {
          if (refersToThisType(type, sd)) {
            map.put(sd.source.getUrl(), sd.source);
          }
        }
        if (map.size() == 0) {
          b.append("<tr><td colspan=\"5\">(None found)</td></tr>\r\n");      
        } else {
          for (String s : Utilities.sorted(map.keySet())) {
            genExtensionRow(b, map.get(s));
          }
        }
        b.append("<tr><td colspan=\"5\"><b>Extensions that refer to many resources including the "+type+" resource</b></td></tr>\r\n");
        map = new HashMap<>();
        for (ExtensionDefinition sd : this.extList) {
          if (refersToThisTypesAncestors(ancestors, sd)) {
            map.put(sd.source.getUrl(), sd.source);
          }
        }
        if (map.size() == 0) {
          b.append("<tr><td colspan=\"5\">(None found)</td></tr>\r\n");      
        } else {
          for (String s : Utilities.sorted(map.keySet())) {
            genExtensionRow(b, map.get(s));
          }
        }
      } else {
        StructureDefinition sd = context.fetchTypeDefinition(type);
        if (sd != null && sd.hasBaseDefinition()) {
          String bt = Utilities.tail(sd.getBaseDefinition());
          b.append("<tr><td colspan=\"5\"><br/>(See also Extensions defined on <a href=\"extensions-types.html#ext-"+bt+"\">"+bt+"</a>)</td></tr>\r\n");      
        }
      }
    }

    b.append("</table>\r\n");
    return b.toString();
  }
  
  private boolean refersToThisType(String type, ExtensionDefinition sd) {
    String url = "http://hl7.org/fhir/StructureDefinition/"+type;
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
      urls.add("http://hl7.org/fhir/StructureDefinition/"+t);
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

  private void genExtensionRow(StringBuilder s, StructureDefinition ed) throws Exception {
    StandardsStatus status = ToolingExtensions.getStandardsStatus(ed);
    if (status  == StandardsStatus.DEPRECATED) {
      s.append("<tr style=\"background-color: #ffeeee\">");
    } else if (status  == StandardsStatus.NORMATIVE) {
      s.append("<tr style=\"background-color: #f2fff2\">");
    } else if (status  == StandardsStatus.INFORMATIVE) {
      s.append("<tr style=\"background-color: #fffff6\">");
    } else {
      s.append("<tr>");
    }
    s.append("<td><a href=\""+ed.getUserString("path")+"\" title=\""+Utilities.escapeXml(ed.getDescription())+"\">"+ed.getId()+"</a></td>");
    s.append("<td>"+displayExtensionCardinality(ed)+"</td>");
    s.append("<td>"+determineExtensionType(ed)+"</td>");
    s.append("<td>");
    boolean first = true;
    int l = 0;
    for (StructureDefinitionContextComponent ec : ed.getContext()) {
      if (first)
        first = false;
      else if (l > 60) {
        s.append(",<br/> ");
        l = 0;
      } else {
        s.append(", ");
        l++;        
      }
      l = l + (ec.hasExpression() ? ec.getExpression().length() : 0);
      if (ec.getType() == ExtensionContextType.ELEMENT) {
        String ref = Utilities.oidRoot(ec.getExpression());
        if (ref.startsWith("@"))
          ref = ref.substring(1);
        if (ref.contains(".")) {
          ref = ref.substring(0, ref.indexOf("."));
        }
        StructureDefinition sd = context.fetchTypeDefinition(ref);
        if (sd != null && sd.hasUserData("path")) {
          s.append("<a href=\""+sd.getUserString("path")+"\">"+ec.getExpression()+"</a>");          
        } else {
          s.append(ec.getExpression());
        }
      } else if (ec.getType() == ExtensionContextType.FHIRPATH) {
        s.append(Utilities.escapeXml(ec.getExpression()));
      } else if (ec.getType() == ExtensionContextType.EXTENSION) {
        StructureDefinition extension = context.fetchResource(StructureDefinition.class, ec.getExpression());
        if (extension==null)
          s.append(Utilities.escapeXml(ec.getExpression()));
        else {
          s.append("<a href=\""+extension.getUserData("path")+"\">"+ec.getExpression()+"</a>");
        }
      } else if (ec.getType() == null) {
        s.append("??error??: "+Utilities.escapeXml(ec.getExpression()));
      } else {
        throw new Error("Not done yet");
      }
    }
    s.append("</td>");
    if (status == StandardsStatus.NORMATIVE) {
      s.append("<td><a href=\""+Utilities.pathURL(corePath, "versions.html")+"#std-process\" title=\"Normative Content\" class=\"normative-flag\">N</a></td>");
    } else if (status == StandardsStatus.DEPRECATED) {
      s.append("<td><a href=\""+Utilities.pathURL(corePath, "versions.html")+"#std-process\" title=\"Deprecated Content\" class=\"deprecated-flag\">D</a></td>");      
    } else if (status == StandardsStatus.INFORMATIVE) {
      s.append("<td><a href=\""+Utilities.pathURL(corePath, "versions.html")+"#std-process\" title=\"Informative Content\" class=\"deprecated-flag\">I</a></td>");      
    } else { 
      String fmm = ToolingExtensions.readStringExtension(ed, ToolingExtensions.EXT_FMM_LEVEL);
      s.append("<td>"+(Utilities.noString(fmm) ? "0" : fmm)+"</td>");
    }
//    s.append("<td><a href=\"extension-"+ed.getId().toLowerCase()+ ".xml.html\">XML</a></td>");
//    s.append("<td><a href=\"extension-"+ed.getId().toLowerCase()+ ".json.html\">JSON</a></td>");
    s.append("</tr>");
  }

  private String displayExtensionCardinality(StructureDefinition ed) {
    ElementDefinition e = ed.getSnapshot().getElementFirstRep();
    String m = "";
    if (ed.getSnapshot().getElementFirstRep().getIsModifier())
      m = " <b title=\"This is a modifier extension\">M</b>";

    return Integer.toString(e.getMin())+".."+e.getMax()+m;
  }

  private String determineExtensionType(StructureDefinition ed) throws Exception {
    for (ElementDefinition e : ed.getSnapshot().getElement()) {
      if (e.getPath().startsWith("Extension.value") && !"0".equals(e.getMax())) {
        if (e.getType().size() == 1) {
          StructureDefinition sd = context.fetchTypeDefinition(e.getType().get(0).getWorkingCode());
          if (sd != null) {
            return "<a href=\""+sd.getUserString("path")+"\">"+e.getType().get(0).getWorkingCode()+"</a>";            
          } else {
            return e.getType().get(0).getWorkingCode();
          }
        } else if (e.getType().size() == 0) {
          return "";
        } else {
          return "(Choice)";
        }
      }
    }
    return "(complex)";
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
          b.append(" <li><a href=\""+sp.getUserString("path")+"\">"+Utilities.escapeXml(sp.present())+"</a>: "+Utilities.escapeXml(sp.getDescription())+"</li>\r\n");
        } else {
          b.append(" <li><a href=\""+sp.getUserString("path")+"\">"+Utilities.escapeXml(sp.present())+"</a></li>\r\n");
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
      return "<p>Unknown Extension "+id+"</p>";
    } else {
      List<SearchParameter> list = new ArrayList<>();
      for (SearchParameter sp : searchParams) {
        ExpressionNode n = (ExpressionNode) sp.getExpressionElement() .getUserData("expression");
        if (n != null && refersToExtension(n, ext.getUrl())) {
          list.add(sp);
        }
      }
      return genSearchList(list);
    }
  }

  private boolean refersToExtension(ExpressionNode n, String url) {
    if (n != null && n.getKind() == Kind.Function && n.getName().equals("extension") && n.getParameters().size() == 1) {
      ExpressionNode p = n.getParameters().get(0);
      return p.getKind() == Kind.Constant && p.getConstant().primitiveValue().equals(url);
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
}
