package org.hl7.fhir.igtools.renderers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.igtools.renderers.CrossViewRenderer.ObsListSorter;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.context.IWorkerContext.ValidationResult;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.Narrative;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionSnapshotComponent;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.r5.renderers.TerminologyRenderer;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.validation.ValidationOptions;

public class CrossViewRenderer {

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
    public List<Coding> category = new ArrayList<>();
    public List<String> effectiveTypes = new ArrayList<>();
    public List<String> types = new ArrayList<>();
    public boolean dataAbsentReason;
    public List<Coding> bodySite = new ArrayList<>(); 
    public List<Coding> method = new ArrayList<>();
    public List<ObservationProfile> components = new ArrayList<>();
    public List<String> members = new ArrayList<String>();
    
    public boolean hasValue() {
      return code.size() > 0 || category.size() > 0;
    }    
  }
  
  public class ExtensionDefinition extends StructureDefinitionNote {
    public String code;
    public String definition;
    public List<String> types = new ArrayList<>();
    public List<ExtensionDefinition> components = new ArrayList<>();
  }
  
  private String canonical;
  private IWorkerContext context;
  private List<ObservationProfile> obsList = new ArrayList<>();
  private List<ExtensionDefinition> extList = new ArrayList<>();
  
  
  public CrossViewRenderer(String canonical, IWorkerContext context) {
    super();
    this.canonical = canonical;
    this.context = context;
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
    while (i < sd.getSnapshot().getElement().size()) {
      ElementDefinition ed = sd.getSnapshot().getElement().get(i);
      
      if (ed.getPath().equals("Observation.category") && ed.hasFixed() && ed.getFixed() instanceof CodeableConcept) {
        obs.category.addAll(((CodeableConcept) ed.getFixed()).getCoding());
      }
      if (ed.getPath().equals("Observation.category.coding")) {
        system = null;
        if (ed.hasFixed() && ed.getFixed() instanceof Coding) {
           obs.category.add(((Coding) ed.getFixed()));
        }
      }
      if (ed.getPath().equals("Observation.category.coding.system") && ed.hasFixed()) {
        system = ed.getFixed().primitiveValue();
      }
      if (ed.getPath().equals("Observation.category.coding.code") && (system != null && ed.hasFixed())) {
        obs.category.add(new Coding(system, ed.getFixed().primitiveValue(), null));
        system = null;
      }
      
      if (ed.getPath().equals("Observation.code") && ed.hasFixed() && ed.getFixed() instanceof CodeableConcept) {
        obs.code.addAll(((CodeableConcept) ed.getFixed()).getCoding());
      }
      if (ed.getPath().equals("Observation.code.coding")) {
        system = null;
        if (ed.hasFixed() && ed.getFixed() instanceof Coding) {
          obs.code.add(((Coding) ed.getFixed()));
        }
      }
      if (ed.getPath().equals("Observation.code.coding.system") && ed.hasFixed()) {
        system = ed.getFixed().primitiveValue();
      }
      if (ed.getPath().equals("Observation.code.coding.code") && (system != null && ed.hasFixed())) {
        obs.code.add(new Coding(system, ed.getFixed().primitiveValue(), null));
        system = null;
      }

      if (ed.getPath().equals("Observation.bodySite") && ed.hasFixed() && ed.getFixed() instanceof CodeableConcept) {
        obs.bodySite.addAll(((CodeableConcept) ed.getFixed()).getCoding());
      }
      if (ed.getPath().equals("Observation.bodySite.coding")) {
        system = null;
        if (ed.hasFixed() && ed.getFixed() instanceof Coding) {
          obs.bodySite.add(((Coding) ed.getFixed()));
        }
      }
      if (ed.getPath().equals("Observation.bodySite.coding.system") && ed.hasFixed()) {
        system = ed.getFixed().primitiveValue();
      }
      if (ed.getPath().equals("Observation.bodySite.coding.code") && (system != null && ed.hasFixed())) {
        obs.bodySite.add(new Coding(system, ed.getFixed().primitiveValue(), null));
        system = null;
      }

      if (ed.getPath().equals("Observation.method") && ed.hasFixed() && ed.getFixed() instanceof CodeableConcept) {
        obs.method.addAll(((CodeableConcept) ed.getFixed()).getCoding());
      }
      if (ed.getPath().equals("Observation.method.coding")) {
        system = null;
        if (ed.hasFixed() && ed.getFixed() instanceof Coding) {
          obs.method.add(((Coding) ed.getFixed()));
        }
      }
      if (ed.getPath().equals("Observation.method.coding.system") && ed.hasFixed()) {
        system = ed.getFixed().primitiveValue();
      }
      if (ed.getPath().equals("Observation.method.coding.code") && (system != null && ed.hasFixed())) {
        obs.method.add(new Coding(system, ed.getFixed().primitiveValue(), null));
        system = null;
      }
      
      if (ed.getPath().equals("Observation.effective[x]")) {
        for (TypeRefComponent tr : ed.getType())
          if (!obs.effectiveTypes.contains(tr.getCode()))
            obs.effectiveTypes.add(tr.getCode());
      }
      if (ed.getPath().startsWith("Observation.value") && !ed.getMax().equals("0")) {
        for (TypeRefComponent tr : ed.getType())
          if (!obs.types.contains(tr.getCode()))
            obs.types.add(tr.getCode());
      }
      if (ed.getPath().startsWith("Observation.component.")) {
        i = processObservationComponent(obs, sd.getSnapshot().getElement(), i);
      } else {
        i++;
      }
    }

    if (obs.hasValue()) {
      obsList.add(obs);
    }
  }

  private int processObservationComponent(ObservationProfile parent, List<ElementDefinition> list, int i) {
    ObservationProfile obs = new ObservationProfile();
    String system = null;
    while (i < list.size() && list.get(i).getPath().startsWith("Observation.component.")) {
      ElementDefinition ed = list.get(i);
      if (ed.getPath().equals("Observation.component.category") && ed.hasFixed() && ed.getFixed() instanceof CodeableConcept) {
        obs.category.addAll(((CodeableConcept) ed.getFixed()).getCoding());
      }
      if (ed.getPath().equals("Observation.component.category.coding")) {
        system = null;
        if (ed.hasFixed() && ed.getFixed() instanceof Coding) {
          obs.category.add(((Coding) ed.getFixed()));
        }
      }
      if (ed.getPath().equals("Observation.component.category.coding.system") && ed.hasFixed()) {
        system = ed.getFixed().primitiveValue();
      }
      if (ed.getPath().equals("Observation.component.category.coding.code") && (system != null && ed.hasFixed())) {
        obs.method.add(new Coding(system, ed.getFixed().primitiveValue(), null));
        system = null;
      }
      
      if (ed.getPath().equals("Observation.component.code") && ed.hasFixed() && ed.getFixed() instanceof CodeableConcept) {
        obs.code.addAll(((CodeableConcept) ed.getFixed()).getCoding());
      }
      if (ed.getPath().equals("Observation.component.code.coding")) {
        system = null;
        if (ed.hasFixed() && ed.getFixed() instanceof Coding) {
          obs.code.add(((Coding) ed.getFixed()));
        }
      }
      if (ed.getPath().equals("Observation.component.code.coding.system") && ed.hasFixed()) {
        system = ed.getFixed().primitiveValue();
      }
      if (ed.getPath().equals("Observation.component.code.coding.code") && (system != null && ed.hasFixed())) {
        obs.method.add(new Coding(system, ed.getFixed().primitiveValue(), null));
        system = null;
      }
      
      if (ed.getPath().equals("Observation.component.value") && !ed.getMax().equals("0")) {
        for (TypeRefComponent tr : ed.getType())
          if (!obs.types.contains(tr.getCode()))
            obs.types.add(tr.getCode());
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
          if (!exd.types.contains(tr.getCode()))
            exd.types.add(tr.getCode());
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
      if (ed.getPath().equals("Extension.extension.url") && ed.hasFixed()) {
        exd.code = ed.getFixed().primitiveValue();
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
          if (!exd.types.contains(tr.getCode()))
            exd.types.add(tr.getCode());
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
        renderTypeCell(b, true, op.types);        
        b.append("<td>"+Utilities.escapeXml(op.definition)+"</td>");
        b.append("</tr>\r\n");
        for (ExtensionDefinition inner : op.components) {
          b.append(" <tr>");
          b.append("<td>&nbsp;&nbsp;"+inner.code+"</td>");
          renderTypeCell(b, true, inner.types);        
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
      boolean hasLoinc = false;
      boolean hasSnomed = false;
      boolean hasEffective = false;
      boolean hasTypes = false;
      boolean hasBodySite = false;
      boolean hasMethod = false;
      for (ObservationProfile op : obsList) {
        hasCat = hasCat || !op.category.isEmpty();
        hasLoinc = hasLoinc || hasCode(op.code, "http://loinc.org");
        hasSnomed = hasSnomed || hasCode(op.code, "http://snomed.info/sct");
        hasCode = hasCode || hasOtherCode(op.code, "http://loinc.org", "http://snomed.info/sct");
        hasEffective = hasEffective || !op.effectiveTypes.isEmpty();
        hasTypes = hasTypes || !op.types.isEmpty();
        hasBodySite = hasBodySite || !op.bodySite.isEmpty();
        hasMethod = hasMethod || !op.method.isEmpty();
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
      if (hasCat) b.append("<td><b>Category</b></td>");
      if (hasLoinc) b.append("<td><b>Loinc</b></td>");
      if (hasSnomed) b.append("<td><b>Snomed</b></td>");
      if (hasCode) b.append("<td><b>Code</b></td>");
      b.append("<td><b>Profile Name</b></td>");
      if (hasEffective) b.append("<td><b>Time Types</b></td>");
      if (hasTypes) b.append("<td><b>Value Types</b></td>");
      if (hasBodySite) b.append("<td><b>Body Site</b></td>");
      if (hasMethod) b.append("<td><b>Method</b></td>");
      b.append("</tr>\r\n");
      
      for (ObservationProfile op : obsList) {
        b.append(" <tr>");

        renderCodingCell(b, hasCat, op.category, null, null);
        renderCodingCell(b, hasLoinc, op.code, "http://loinc.org", "https://loinc.org/");
        renderCodingCell(b, hasSnomed, op.code, "http://snomed.info/sct", null);
        renderCodingCell(b, hasCode, op.code, null, null);
        b.append("<td><a href=\""+op.source.getUserString("path")+"\">"+op.source.present()+"</a></td>");
        renderTypeCell(b, hasEffective, op.effectiveTypes);
        renderTypeCell(b, hasTypes, op.types);
        renderCodingCell(b, hasBodySite, op.bodySite, null, null);
        renderCodingCell(b, hasMethod, op.method, null, null);
        
        b.append("</tr>\r\n");
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

  private boolean hasOtherCode(List<Coding> codes, String... exclusions) {
    boolean found = false;
    for (Coding c : codes) {
      if (!Utilities.existsInList(c.getSystem(), exclusions)) {
        found = true;
      }
    }
    return found;  
  }

  private boolean hasCode(List<Coding> codes, String system) {
    for (Coding c : codes) {
      if (system.equals(c.getSystem())) {
        return true;
      }
    }
    return false;
  }

  private void renderTypeCell(StringBuilder b, boolean render, List<String> types) {
    if (render) {
      b.append("<td>");
      boolean first = true;
      for (String t : types) {
        if (first) first = false; else b.append(" | ");
        StructureDefinition sd = context.fetchTypeDefinition(t);
        if (sd != null) {
          b.append("<a href=\""+sd.getUserString("path")+"\">"+t+"</a>");
        } else {
          b.append(t);
        }
      }
      b.append("</td>");
    }
    
  }
  private void renderCodingCell(StringBuilder b, boolean render, List<Coding> list, String system, String link) {
    if (render) {
      b.append("<td>");
      boolean first = true;
      for (Coding t : list) {
        if (system == null || system.equals(t.getSystem())) {
          if (first) first = false; else b.append(", ");
          String sys = TerminologyRenderer.describeSystem(t.getSystem());
          if (sys.equals(t.getSystem()))
            sys = null;
          if (sys == null) {
            CodeSystem cs = context.fetchCodeSystem(t.getSystem());
            if (cs != null)
              sys = cs.getTitle();
          }
          ValidationResult vr = context.validateCode(ValidationOptions.defaults(), t.getSystem(), t.getCode(), null);
          if (system != null) {
            if (vr != null & vr.getDisplay() != null) {
              b.append("<a href=\""+link+t.getCode()+"\" title=\""+vr.getDisplay()+"\">"+t.getCode()+"</a>");
            } else {
              b.append("<a href=\""+link+t.getCode()+"\">"+t.getCode()+"</a>");
            }
          } else {
            if (vr != null & vr.getDisplay() != null) {
              if (Utilities.existsInList(t.getSystem(), "http://loinc.org"))
                b.append("<span title=\""+t.getSystem()+(sys == null ? "" : " ("+sys+")")+": "+ vr.getDisplay()+"\">"+t.getCode()+" "+vr.getDisplay()+"</span>");           
              else
                b.append("<span title=\""+t.getSystem()+(sys == null ? "" : " ("+sys+")")+": "+ vr.getDisplay()+"\">"+t.getCode()+"</span>");           
            } else {
              b.append("<span title=\""+t.getSystem()+(sys == null ? "" : " ("+sys+"): ")+"\">"+t.getCode()+"</span>");           
            }
          }
        }
      }
      b.append("</td>");
    }
  }
  
  private String gen(Coding t) {
    return t.getSystem()+"#"+t.getCode();
  }
}
