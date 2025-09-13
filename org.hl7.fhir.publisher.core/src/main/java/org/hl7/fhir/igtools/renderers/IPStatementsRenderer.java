package org.hl7.fhir.igtools.renderers;

import java.io.IOException;
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
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.RenderingContextLangs;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.i18n.I18nConstants;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;

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
  private RenderingContext rc;

  private String packageId;
  
  
  public IPStatementsRenderer(IWorkerContext ctxt, MarkDownProcessor markdownEngine, String packageId, RenderingContext rc) {
    super();
    this.ctxt = ctxt;
    this.markdownEngine = markdownEngine;
    this.packageId = packageId;
    this.rc = rc;
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
  

  public String genIpStatements(FetchedResource r, boolean example, String lang) throws FHIRException, IOException {
    listAllCodeSystems(r, r.getElement());
    if (r.getResource() != null) {
      listAllCodeSystems(r, r.getResource());
    }
    return render((example ? "example" : describeResource(r.getElement(), rc)));
  }

  private String describeResource(Element element, RenderingContext lrc) {
    String t = element.fhirType();
    if ("StructureDefinition".equals(t)) {
      String s = element.getChildValue("type");
      if ("Extension".equals(s)) {
        return lrc.formatPhrase(RenderingContext.KIND_EXTENSION);
      } else {
        s = element.getChildValue("kind");
        if ("logical".equals(s)) {
          return lrc.formatPhrase(RenderingContext.KIND_LOGICAL);
        } else {
          return lrc.formatPhrase(RenderingContext.KIND_PROFILE);
        }       
      }
    } else {
      return t;
    }
  }

  public String genIpStatements(List<FetchedFile> files, String lang) throws FHIRException, IOException {
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
  
  private String render(String title) throws IOException {
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
      return rc.formatPhrase(isHL7Ig() ? RenderingContext.IP_NONE : RenderingContext.IP_NONE_EXT);
    } else {
      StringBuilder b = new StringBuilder();
      b.append("<p>"+rc.formatPhrase(RenderingContext.IP_INTRO, title)+"</p>\r\n<ul>\r\n");
      int key1 = 0;
      int key2 = 0;
      for (String stmt : Utilities.sorted(usages.keySet())) {
        key1++;
        b.append("<li>");
        b.append(stmt);
        b.append("<div data-fhir=\"generated\" id=\"ipp_"+key1+"\" onClick=\"if (document.getElementById('ipp2_"+key1+"').innerHTML != '') {document.getElementById('ipp_"+key1+"').innerHTML = document.getElementById('ipp2_"+key1+"').innerHTML; document.getElementById('ipp2_"+key1+"').innerHTML = ''}\">"+
            " <span style=\"cursor: pointer; border: 1px grey solid; background-color: #fcdcb3; padding-left: 3px; padding-right: 3px; color: black\">"+
            "Show Usage</span></div><div id=\"ipp2_"+key1+"\" style=\"display: none\">");
        b.append("\r\n<ul>\r\n");
        List<SystemUsage> v = usages.get(stmt);
        Collections.sort(v, new SystemUsageSorter());        
        for (SystemUsage t : v) {
          b.append("<li>");
          if (t.cs == null) {
            b.append(Utilities.escapeXml(t.desc));
          } else if (t.cs.hasWebPath()) {
            b.append("<a href=\"");
            b.append(Utilities.escapeXml(t.cs.getWebPath()));
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
//              link = r.getResource().getWebPath();
//            }
            links.put(r.getTitle(), link);
          }
          key2++;
          int c = 0;
          boolean closeSpan = false;
          for (String s : Utilities.sorted(links.keySet())) {
            c++;
            if (c == MAX_LIST_DISPLAY && links.size() > MAX_LIST_DISPLAY + 2) {
              closeSpan = true;
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
              b.append("<a href=\""+Utilities.escapeXml(links.get(s))+"\">"+Utilities.escapeXml(s)+"</a>");
            }
          }
          if (closeSpan) {
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
  
  private String getCopyRightStatement(SystemUsage system) throws IOException {
    if ("http://snomed.info/sct".equals(system.system)) {
      system.desc = ctxt.formatMessage(I18nConstants.HTA_SCT_DESC);
      return ctxt.formatMessage(I18nConstants.HTA_SCT_MESSAGE);
    }
    if ("http://loinc.org".equals(system.system)) {
      system.desc = ctxt.formatMessage(I18nConstants.HTA_LOINC_DESC);
      return ctxt.formatMessage(I18nConstants.HTA_LOINC_MESSAGE);
    }
    if (system.cs != null) {
      system.desc = system.cs.present();
      if (system.cs.hasCopyright()) {
        List<XhtmlNode> xl = new XhtmlParser().parseMDFragmentStripParas(markdownEngine.process(fixCopyright(system.cs.getCopyright()), "Copyright"));
        if (xl.size() == 0) {
          return "?";
        } else {
          return new XhtmlComposer(false, true).setAutoLinks(true).compose(xl);
        }
      }
    }
    return null;
  }
  
  private String fixCopyright(String copyright) {
    if (copyright.contains("HL7 Terminology")) {
      return "This material derives from the HL7 Terminology (THO). THO is copyright ©1989+ Health Level Seven International and is made available under the CC0 designation. For more licensing information see: https://terminology.hl7.org/license.html";
    } else {
      return copyright;
    }
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
