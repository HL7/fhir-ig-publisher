package org.hl7.fhir.igtools.publisher.xig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.NamingSystem;
import org.hl7.fhir.r5.model.NamingSystem.NamingSystemType;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.utilities.json.model.JsonObject;


public class XIGNamingSystemHandler extends XIGHandler {

  private XIGInformation info;

  public XIGNamingSystemHandler(XIGInformation info) {
    super();
    this.info = info;
  }

  public void fillOutJson(NamingSystem ns, JsonObject j) {
    if (ns.hasKind()) {    
      j.add("kind", ns.getKind().toCode()); 
    }
    if (ns.hasType()) {    
      j.add("type", new DataRenderer(info.getCtxt()).displayDataType(ns.getType()));
      for (Coding t : ns.getType().getCoding()) {
        info.getNspr().add(t.getCode());
      }
    }
  }

  public PageContent makeKindPage(NamingSystemType kind, String title, String realm) {
    List<NamingSystem> list = new ArrayList<>();
    for (CanonicalResource cr : info.getResources().values()) {
      if (meetsRealm(cr, realm)) {
        if (cr instanceof NamingSystem) {
          NamingSystem ns = (NamingSystem) cr;
          if ((kind == null) || (ns.getKind() == kind)) {
            list.add(ns);
          }
        }
      }
    }
    Collections.sort(list, new CanonicalResourceSorter());
    StringBuilder b = new StringBuilder();
    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, false);
    DuplicateTracker dt = new DuplicateTracker();
    for (NamingSystem vs : list) {
      crTr(b, dt, vs, 0);            
    }
    b.append("</table>\r\n");
    return new PageContent(title+" ("+list.size()+")", b.toString());
  }

  public PageContent makeTypePage(String type, String title, String realm) {
    List<NamingSystem> list = new ArrayList<>();
    for (CanonicalResource cr : info.getResources().values()) {
      if (meetsRealm(cr, realm)) {
        if (cr instanceof NamingSystem) {
          NamingSystem ns = (NamingSystem) cr;
          boolean ok = false;
          for (Coding c : ns.getType().getCoding()) {
            if (type.equals(c.getCode())) {
              ok = true;
            }
          }
          if (ok) {
            list.add(ns);
          }
        }
      }
    }
    Collections.sort(list, new CanonicalResourceSorter());
    StringBuilder b = new StringBuilder();
    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, false);
    DuplicateTracker dt = new DuplicateTracker();
    for (NamingSystem vs : list) {
      crTr(b, dt, vs, 0);            
    }
    b.append("</table>\r\n");
    return new PageContent(title+" ("+list.size()+")", b.toString());
  }
}
