package org.hl7.fhir.igtools.publisher.utils.xig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.utils.xig.XIGHandler.PageContent;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.ConceptMap.ConceptMapGroupComponent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class XIGConceptMapHandler extends XIGHandler {

  private XIGInformation info;

  public XIGConceptMapHandler(XIGInformation info) {
    super();
    this.info = info;

  }

  public void fillOutJson(ConceptMap cm, JsonObject j) {
    if (cm.hasSourceScope()) {        j.addProperty("sourceScope", cm.getSourceScope().primitiveValue()); }
    if (cm.hasTargetScope()) {        j.addProperty("targetScope", cm.getTargetScope().primitiveValue()); }
    for (ConceptMapGroupComponent g : cm.getGroup()) {
      if (g.hasSource()) {    
        if (!j.has("sources")) {
          j.add("sources", new JsonArray());
        }
        j.getAsJsonArray("sources").add(g.getSource()); 
      }
      if (g.hasTarget()) {        
        if (!j.has("targets")) {
          j.add("targets", new JsonArray());
        }
        j.getAsJsonArray("targets").add(g.getTarget()); 
      }
    }    
  }
  
  public PageContent makeConceptMapsPage(String mode, String title, String realm) {
    List<ConceptMap> list = new ArrayList<>();
    for (CanonicalResource cr : info.getResources().values()) {
      if (meetsRealm(cr, realm)) {
        if (cr instanceof ConceptMap) {
          ConceptMap cm = (ConceptMap) cr;
          Set<String> systems = new HashSet<>();
          for (ConceptMapGroupComponent g : cm.getGroup()) {
            if (g.hasSource()) {
              systems.add(g.getSource());
            }
            if (g.hasTarget()) {
              systems.add(g.getTarget());
            }
          }
          if (inMode(mode, systems)) {
            list.add(cm);
          }
        }
      }
    }
    if (list.size() == 0) {
      return null;
    }
    Collections.sort(list, new CanonicalResourceSorter());
    StringBuilder b = new StringBuilder();

    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, false);
    DuplicateTracker dt = new DuplicateTracker();
    for (ConceptMap cm : list) {
      crTr(b, dt, cm, 0); 
    }
    b.append("</table>\r\n");

    return new PageContent(title+" ("+list.size()+")", b.toString());
  }
}
