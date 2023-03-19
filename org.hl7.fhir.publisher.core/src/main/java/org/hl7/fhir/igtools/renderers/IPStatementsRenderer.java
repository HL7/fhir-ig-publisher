package org.hl7.fhir.igtools.renderers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.OperationDefinition;
import org.hl7.fhir.r5.model.OperationDefinition.OperationDefinitionParameterComponent;
import org.hl7.fhir.r5.model.Questionnaire;
import org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionContainsComponent;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;

public class IPStatementsRenderer {

  public class SystemUsage {
    private String system;
    private String desc;
    private CodeSystem cs;
    private List<FetchedResource> uses = new ArrayList<>();
  }
  public class SystemUsageSorter implements Comparator<SystemUsage> {
    @Override
    public int compare(SystemUsage o1, SystemUsage o2) {
      return o1.system.compareTo(o2.system);
    }
  }

  private static final int MAX_LIST_DISPLAY = 3;

  private IWorkerContext ctxt;
  private MarkDownProcessor markdownEngine;
  private Map<String, SystemUsage> systems = new HashMap<>();
  private Map<String, List<SystemUsage>> usages = new HashMap<>();

  private String packageId;
  
  
  public IPStatementsRenderer(IWorkerContext ctxt, MarkDownProcessor markdownEngine, String packageId) {
    super();
    this.ctxt = ctxt;
    this.markdownEngine = markdownEngine;
    this.packageId = packageId;
  }
  
  private void seeSystem(String url, FetchedResource source) {
    if (url != null) {
      SystemUsage su = systems.get(url);
      if (su == null) {
        su = new SystemUsage();
        su.system = url;
        su.cs = ctxt.fetchCodeSystem(url);
        systems.put(url, su);
      }
      if (!su.uses.contains(source)) {
        su.uses.add(source);
      }
    }
  }
  

  public String genIpStatements(FetchedResource r, boolean example) throws FHIRException {
    listAllCodeSystems(r, r.getElement());
    if (r.getResource() != null) {
      listAllCodeSystems(r, r.getResource());
    }
    return render((example ? "example" : describeResource(r.getElement())));
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

  public String genIpStatements(List<FetchedFile> files) throws FHIRException {
    for (FetchedFile f : files) {
      for (FetchedResource r : f.getResources()) {
        listAllCodeSystems(r, r.getElement());
        if (r.getResource() != null) {
          listAllCodeSystems(r, r.getResource());
        }
      }
    }
    
    return render("publication");
  }
  
  private String render(String title) {
    for (SystemUsage su : systems.values()) {
      String stmt = getCopyRightStatement(su);
      if (stmt != null) {
        if (!usages.containsKey(stmt)) {
          usages.put(stmt, new ArrayList<>());
        }
        usages.get(stmt).add(su);
      }
    }
    
    if (usages.size() == 0) {
      return isHL7Ig() ? "No use of external IP" : "No use of external IP (other than from the FHIR specification)";
    } else {
      StringBuilder b = new StringBuilder();
      b.append("<p>This "+title+" includes IP covered under the following statements.</p>\r\n<ul>\r\n");
      int key1 = 0;
      int key2 = 0;
      for (String stmt : Utilities.sorted(usages.keySet())) {
        key1++;
        b.append("<li>");
        b.append(stmt);
        b.append("<div id=\"ipp_"+key1+"\" onClick=\"if (document.getElementById('ipp2_"+key1+"').innerHTML != '') {document.getElementById('ipp_"+key1+"').innerHTML = document.getElementById('ipp2_"+key1+"').innerHTML; document.getElementById('ipp2_"+key1+"').innerHTML = ''}\">"+
            " <span style=\"cursor: pointer; border: 1px grey solid; background-color: #fcdcb3; padding-left: 3px; padding-right: 3px; color: black\">"+
            "Show Usage</span></div><div id=\"ipp2_"+key1+"\" style=\"display: none\">");
        b.append("\r\n<ul>\r\n");
        List<SystemUsage> v = usages.get(stmt);
        Collections.sort(v, new SystemUsageSorter());        
        for (SystemUsage t : v) {
          b.append("<li>");
          if (t.cs == null) {
            b.append(Utilities.escapeXml(t.desc));
          } else if (t.cs.hasUserData("path")) {
            b.append("<a href=\"");
            b.append(t.cs.getUserString("path"));
            b.append("\">");
            b.append(Utilities.escapeXml(t.desc));
            b.append("</a>");
          } else {
            b.append(Utilities.escapeXml(t.desc));
          }
          b.append(": ");
          
          Map<String, String> links = new HashMap<>();
          for (FetchedResource r : t.uses) {
            String link = r.getPath();
//            if (link == null && r.getResource() != null) {
//              link = r.getResource().getUserString("path");
//            }
            links.put(r.getTitle(), link);
          }
          key2++;
          int c = 0;
          
          for (String s : Utilities.sorted(links.keySet())) {
            c++;
            if (c == MAX_LIST_DISPLAY && links.size() > MAX_LIST_DISPLAY + 2) {
              b.append("<span id=\"ips_"+key2+"\" onClick=\"document.getElementById('ips_"+key2+"').innerHTML = document.getElementById('ips2_"+key2+"').innerHTML\">..."+
                  " <span style=\"cursor: pointer; border: 1px grey solid; background-color: #fcdcb3; padding-left: 3px; padding-right: 3px; color: black\">"+
                  "Show "+(links.size()-MAX_LIST_DISPLAY+1)+" more</span></span><span id=\"ips2_"+key2+"\" style=\"display: none\">");
            }
            if (c == links.size() && c != 1) {
              b.append(" and ");
            } else if (c > 1) {
              b.append(", ");
            }
            if (links.get(s) == null) {
              b.append(Utilities.escapeXml(s));
            } else {
              b.append("<a href=\""+links.get(s)+"\">"+Utilities.escapeXml(s)+"</a>");
            }
          }
          if (links.size() > MAX_LIST_DISPLAY + 2) {
            b.append("</span>");
          }
          b.append("</li>\r\n");
        }
        b.append("</ul>\r\n");
        b.append("</div>");
        b.append("</li>\r\n");
      }
      b.append("</ul>\r\n");
      return b.toString();
    }
  } 

  private boolean isHL7Ig() {
    return packageId.startsWith("hl7.");
  }
  
  private String getCopyRightStatement(SystemUsage system) {
    if ("http://snomed.info/sct".equals(system.system)) {
      system.desc = "SNOMED Clinical Terms® (SNOMED CT®)";
      return "This material contains content that is copyright of SNOMED International. Implementers of these specifications must have the appropriate SNOMED CT Affiliate license - "+
       "for more information contact <a href=\"http://www.snomed.org/snomed-ct/get-snomed-ct\">http://www.snomed.org/snomed-ct/get-snomed-ct</a> or <a href=\"mailto:info@snomed.org\">info@snomed.org</a>.";
    }
    if ("http://loinc.org".equals(system.system)) {
      system.desc = "LOINC";
      return "This material contains content from <a href=\"http://loinc.org\">LOINC</a>. LOINC is copyright © 1995-2020, Regenstrief Institute, Inc. and the Logical Observation Identifiers Names and Codes (LOINC) "+
         "Committee and is available at no cost under the <a href=\"http://loinc.org/license\">license</a>. LOINC® is a registered United States trademark of Regenstrief Institute, Inc.";
    }
    if (system.cs != null) {
      system.desc = system.cs.present();
      if (system.cs.hasCopyright()) {
        return Utilities.stripPara(markdownEngine.process(system.cs.getCopyright(), "Copyright"));        
      }
    }
    return null;
  }
  
  private void listAllCodeSystems(FetchedResource source, Resource resource) {
    if (resource instanceof StructureDefinition) {
      listAllCodeSystemsSD(source, (StructureDefinition) resource);
    }
    if (resource instanceof OperationDefinition) {
      listAllCodeSystemsOD(source, (OperationDefinition) resource);
    }
    if (resource instanceof ValueSet) {
      listAllCodeSystemsVS(source, (ValueSet) resource);
    }    
    if (resource instanceof Questionnaire) {
      listAllCodeSystemsQ(source, (Questionnaire) resource);
    } 
    if (resource instanceof CodeSystem) {
      listAllCodeSystemsCS(source, (CodeSystem) resource);
    } 
  }
  
  private void listAllCodeSystemsQ(FetchedResource source, Questionnaire q) {
    for (QuestionnaireItemComponent i : q.getItem()) {
      listAllCodeSystemsQ(source, i);
    }
  }
  
  private void listAllCodeSystemsQ(FetchedResource source, QuestionnaireItemComponent i) {
    if (i.hasAnswerValueSet()) {
      ValueSet vs = ctxt.fetchResource(ValueSet.class, i.getAnswerValueSet());
      if (vs != null) {
        listAllCodeSystemsVS(source, vs);
      }
    }
    for (QuestionnaireItemComponent ii : i.getItem()) {
      listAllCodeSystemsQ(source, ii);
    }    
  }
  private void listAllCodeSystemsCS(FetchedResource source, CodeSystem cs) {
    seeSystem(cs.getSupplements(), source);
  }
  
  private void listAllCodeSystemsVS(FetchedResource source, ValueSet vs) {
    for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
      seeSystem(inc.getSystem(), source);
    }
    for (ConceptSetComponent exc : vs.getCompose().getExclude()) {
      seeSystem(exc.getSystem(), source);
    }
    for (ValueSetExpansionContainsComponent c : vs.getExpansion().getContains()) {
      listAllCodeSystemsVS(source, c);
    }
  }
  
  private void listAllCodeSystemsVS(FetchedResource source, ValueSetExpansionContainsComponent c) {
    seeSystem(c.getSystem(), source);    
    for (ValueSetExpansionContainsComponent cc : c.getContains()) {
      listAllCodeSystemsVS(source, cc);
    }
    
  }
  private void listAllCodeSystemsOD(FetchedResource source, OperationDefinition opd) {
    for (OperationDefinitionParameterComponent p : opd.getParameter()) {
      if (p.getBinding().hasValueSet()) {
        ValueSet vs = ctxt.fetchResource(ValueSet.class, p.getBinding().getValueSet());
        if (vs != null) {
          listAllCodeSystemsVS(source, vs);
        }
      }
    }    
  }
  
  private void listAllCodeSystemsSD(FetchedResource source, StructureDefinition sd) {
    for (ElementDefinition ed : sd.getDifferential().getElement()) {
      if (ed.getBinding().hasValueSet()) {
        ValueSet vs = ctxt.fetchResource(ValueSet.class, ed.getBinding().getValueSet());
        if (vs != null) {
          listAllCodeSystemsVS(source, vs);
        }
      }
    }
    
  }
  private void listAllCodeSystems(FetchedResource source, Element element) {
    if ("Coding".equals(element.fhirType())) {
      seeSystem(element.getChildValue("system"), source);
    }
    if ("Quantity".equals(element.fhirType())) {
      seeSystem(element.getChildValue("system"), source);
    }
    for (Element child : element.getChildren()) {
      listAllCodeSystems(source, child);
    }    
  }
}
