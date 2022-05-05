package org.hl7.fhir.igtools.renderers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.context.IWorkerContext.ValidationResult;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionBindingComponent;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.r5.renderers.TerminologyRenderer;
import org.hl7.fhir.r5.utils.ToolingExtensions;
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
  private IWorkerContext context;
  private List<ObservationProfile> obsList = new ArrayList<>();
  private List<ExtensionDefinition> extList = new ArrayList<>();
  public List<String> baseEffectiveTypes = new ArrayList<>();
  public List<String> baseTypes = new ArrayList<>();
  public List<String> baseExtTypes = new ArrayList<>();
  public String corePath;

  public CrossViewRenderer(String canonical, IWorkerContext context, String corePath) {
    super();
    this.canonical = canonical;
    this.context = context;
    this.corePath = corePath;
    getBaseTypes();
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
    if (sd.getUrl().length() < canonical.length()+21) {
      System.out.println("extension url doesn't follow canonical pattern: "+sd.getUrl()+", so omitted from extension summary");
      return;
    }
    ExtensionDefinition exd = new ExtensionDefinition();
    extList.add(exd);
    exd.source = sd;
    exd.code = sd.getUrl().substring(canonical.length()+21);
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
        b.append("</td>");
      }
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
              b.append("<a href=\""+cs.getUserString("path")+"#"+t.getCode()+"\" title=\""+t.getSystem()+(sys == null ? "" : " ("+sys+")")+": "+ vr.getDisplay()+"\">"+t.getCode()+"</a>");                  
            } else {
              b.append("<span title=\""+t.getSystem()+(sys == null ? "" : " ("+sys+")")+": "+ vr.getDisplay()+"\">"+t.getCode()+"</span>");
            }
            //          }
          } else {
            b.append("<span title=\""+t.getSystem()+(sys == null ? "" : " ("+sys+"): ")+"\">"+t.getCode()+"</span>");           
          }
        }
      }
    }
    b.append("</td>");
  }

  public List<ObservationProfile> getObservations() {
    return obsList;
  }

}
