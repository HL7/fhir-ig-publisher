package org.hl7.fhir.igtools.renderers;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.igtools.publisher.ProvenanceDetails;
import org.hl7.fhir.igtools.publisher.ProvenanceDetails.ProvenanceDetailsTarget;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.Reference;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.Resolver.ResourceWithReference;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class HistoryGenerator {

  public class HistoryListSorter implements Comparator<ProvenanceDetails> {

    @Override
    public int compare(ProvenanceDetails arg0, ProvenanceDetails arg1) {
      return -arg0.getDate().getValue().compareTo(arg1.getDate().getValue());
    }

  }

  private RenderingContext context;

  public HistoryGenerator(RenderingContext context) {
    this.context = context;    
  }

  public XhtmlNode generate(FetchedResource r) throws IOException {
    if (r.getAudits().isEmpty()) {
      return null;
    } else {
      return internalGenerate(r.getAudits(), false);
    }
  }
  
  public XhtmlNode internalGenerate(List<ProvenanceDetails> entries, boolean showTargets) throws IOException {
      // prep
      Collections.sort(entries, new HistoryListSorter());
      List<Coding> actorTypes = scanActors(entries);
      
      // framework
      XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
      div.attribute("data-fhir", "generated");
      div.hr();
      div.para().b().tx("History");
      XhtmlNode tbl = div.table("grid", false).markGenerated(!context.forValidResource());
      
      // headers
      XhtmlNode tr = tbl.tr();
      if (showTargets) {
        tr.td().b().tx("Resource");        
      }
      tr.td().b().tx("Date");
      tr.td().b().tx("Action");
      for (Coding c : actorTypes) {
        CodeSystem cs = context.getWorker().fetchCodeSystem(c.getSystem());
        XhtmlNode td = tr.td().b(); 
        if (cs != null && cs.hasWebPath()) {
          ConceptDefinitionComponent cd = CodeSystemUtilities.getCode(cs, c.getCode());
          td.ah(cs.getWebPath()+"#"+cs.getId()+"-"+c.getCode()).tx(cd != null && cd.hasDisplay() ? cd.getDisplay() : c.hasDisplay() ? c.getDisplay() : c.getCode());
        } else {
          td.tx(c.getCode());
        }
      }
      tr.td().b().tx("Comment");
      // rows
      for (ProvenanceDetails pd : entries) {
        tr = tbl.tr();
        if (showTargets) {
          XhtmlNode td = tr.td();
          boolean first = true;
          for (ProvenanceDetailsTarget t : pd.getTargets()) {
            if (first) first = false; else td.tx(", "); 
            if (t.getPath() == null) {
              td.tx(t.getDisplay());             
            } else {
              td.ah(t.getPath()).tx(t.getDisplay());
            }
          }
        }
        
        tr.td().ah(pd.getPath()).tx(new DataRenderer(context).displayDataType(pd.getDate()));          
        

        XhtmlNode td = tr.td(); 
        CodeSystem cs = context.getWorker().fetchCodeSystem(pd.getAction().getSystem());
        if (cs != null && cs.hasWebPath()) {
          ConceptDefinitionComponent cd = CodeSystemUtilities.getCode(cs, pd.getAction().getCode());
          td.ah(cs.getWebPath()+"#"+cs.getId()+"-"+pd.getAction().getCode()).tx(cd != null ? cd.getDisplay() : pd.getAction().getCode());
        } else {
          td.tx(pd.getAction().getCode());
        }

        for (Coding c : actorTypes) {
          Reference aa = getActor(pd, c);
          td = tr.td();
          if (aa != null) {
            if (aa.getReference() != null) {
              ResourceWithReference rr = context.getResolver().resolve(context, aa.getReference(), null);
              if (rr == null) {
                td.tx(aa.getDisplay());
              } else {
                td.ah(rr.getWebPath()).tx(aa.getDisplay());                
              }
            } else {
              td.tx(aa.getDisplay());
            }
          }
        }
        td = tr.td();
        if (pd.getComment() != null) { 
          td.tx(pd.getComment());
        }
      }
      return div;
    }

  private Reference getActor(ProvenanceDetails pd, Coding c) {
    for (Coding t : pd.getActors().keySet()) {
      if (t.matches(c)) {
        return pd.getActors().get(t);
      }
    }
    return null;
  }

  private List<Coding> scanActors(List<ProvenanceDetails> audits) {
    List<Coding> res = new ArrayList<>();
    for (ProvenanceDetails a : audits) {
      for (Coding c : a.getActors().keySet()) {
        boolean found = false;
        for (Coding t : res) {
          found = found || t.matches(c);
        }
        if (!found) {
          res.add(c);
        }
      }
    }
    return res;
  }

  public static boolean allEntriesAreHistoryProvenance(Element resource) throws UnsupportedEncodingException, FHIRException, IOException {
    if (!resource.fhirType().equals("Bundle")) {
      return false;
    }
    List<Element> entries = resource.getChildrenByName("entry");
    for (Element be : entries) {
      if (!be.hasChild("resource") || !"Provenance".equals(be.getNamedChild("resource").fhirType())) {
        return false;
      }
    }
    return !entries.isEmpty();
  }

  public static boolean allEntriesAreHistoryProvenance(Resource resource) throws UnsupportedEncodingException, FHIRException, IOException {
    if (!resource.fhirType().equals("Bundle")) {
      return false;
    }
    for (BundleEntryComponent be : ((Bundle) resource).getEntry()) {
      if (!"Provenance".equals(be.getResource().fhirType())) {
        return false;
      }
    }
    return ((Bundle) resource).hasEntry();
  }

  public XhtmlNode generateForBundle(List<ProvenanceDetails> entries) throws IOException {
    return internalGenerate(entries, true);
  }

  
}
