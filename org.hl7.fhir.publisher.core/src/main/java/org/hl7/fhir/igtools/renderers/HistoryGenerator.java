package org.hl7.fhir.igtools.renderers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.igtools.publisher.AuditRecord;
import org.hl7.fhir.igtools.publisher.AuditRecord.AuditEventActor;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.DateTimeType;
import org.hl7.fhir.r5.model.AuditEvent.AuditEventAction;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class HistoryGenerator {

  private SimpleWorkerContext context;

  public HistoryGenerator(SimpleWorkerContext context) {
    this.context = context;    
  }

  public XhtmlNode generate(FetchedResource r) throws IOException {
    if (r.getAudits().isEmpty()) {
      return null;
    } else {
      XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
      div.hr();
      div.para().b().tx("history");
      XhtmlNode tbl = div.table("grid");
      XhtmlNode tr = tbl.tr();
      List<Coding> actorTypes = scanActors(r.getAudits());
      tr.td().b().tx("Date");
      tr.td().b().tx("Action");
      tr.td().b().tx("Type");
      for (Coding c : actorTypes) {
        tr.td().b().tx(c.getCode());        
      }
      tr.td().b().tx("Comment");
      return div;
    }
  }

  private List<Coding> scanActors(List<AuditRecord> audits) {
    List<Coding> res = new ArrayList<>();
    for (AuditRecord a : audits) {
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
