package org.hl7.fhir.igtools.renderers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.hl7.fhir.igtools.publisher.ProvenanceDetails;
import org.hl7.fhir.igtools.renderers.HistoryGenerator.HistoryListSorter;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.DateTimeType;
import org.hl7.fhir.r5.model.Reference;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities;
import org.hl7.fhir.r5.model.AuditEvent.AuditEventAction;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class HistoryGenerator {

  public class HistoryListSorter implements Comparator<ProvenanceDetails> {

    @Override
    public int compare(ProvenanceDetails arg0, ProvenanceDetails arg1) {
      return -arg0.getDate().getValue().compareTo(arg1.getDate().getValue());
    }

  }

  private SimpleWorkerContext context;

  public HistoryGenerator(SimpleWorkerContext context) {
    this.context = context;    
  }

  public XhtmlNode generate(FetchedResource r) throws IOException {
    if (r.getAudits().isEmpty()) {
      return null;
    } else {
      // prep
      Collections.sort(r.getAudits(), new HistoryListSorter());
      List<Coding> actorTypes = scanActors(r.getAudits());
      
      // framework
      XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
      div.hr();
      div.para().b().tx("History");
      XhtmlNode tbl = div.table("grid");
      
      // headers
      XhtmlNode tr = tbl.tr();
      tr.td().b().tx("Date");
      tr.td().b().tx("Action");
      for (Coding c : actorTypes) {
        CodeSystem cs = context.fetchCodeSystem(c.getSystem());
        XhtmlNode td = tr.td().b(); 
        if (cs != null && cs.hasUserData("path")) {
          ConceptDefinitionComponent cd = CodeSystemUtilities.getCode(cs, c.getCode());
          td.ah(cs.getUserString("path")+"#"+cs.getId()+"-"+c.getCode()).tx(cd != null && cd.hasDisplay() ? cd.getDisplay() : c.hasDisplay() ? c.getDisplay() : c.getCode());
        } else {
          td.tx(c.getCode());
        }
      }
      tr.td().b().tx("Comment");
      // rows
      for (ProvenanceDetails pd : r.getAudits()) {
        tr = tbl.tr();
        tr.td().ah(pd.getPath()).tx(pd.getDate().asStringValue().substring(0, 10));

        XhtmlNode td = tr.td(); 
        CodeSystem cs = context.fetchCodeSystem(pd.getAction().getSystem());
        if (cs != null && cs.hasUserData("path")) {
          ConceptDefinitionComponent cd = CodeSystemUtilities.getCode(cs, pd.getAction().getCode());
          td.ah(cs.getUserString("path")+"#"+cs.getId()+"-"+pd.getAction().getCode()).tx(cd != null ? cd.getDisplay() : pd.getAction().getCode());
        } else {
          td.tx(pd.getAction().getCode());
        }

        for (Coding c : actorTypes) {
          Reference aa = getActor(pd, c);
          td = tr.td();
          if (aa != null) {
            if (aa.getReference() != null) {
              td.ah(aa.getReference()).tx(aa.getDisplay());
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
  }

  private Reference getActor(ProvenanceDetails pd, Coding c) {
    for (Coding t : pd.getActors().keySet()) {
      if (t.matches(c)) {
        return pd.getActors().get(c);
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


}
