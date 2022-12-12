package org.hl7.fhir.igtools.publisher.xig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.xig.XIGInformation.UsageType;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.ConceptMap.ConceptMapGroupComponent;
import org.hl7.fhir.r5.model.ConceptMap.OtherElementComponent;
import org.hl7.fhir.r5.model.ConceptMap.SourceElementComponent;
import org.hl7.fhir.r5.model.ConceptMap.TargetElementComponent;
import org.hl7.fhir.utilities.json.model.JsonObject;


public class XIGConceptMapHandler extends XIGHandler {

  private XIGInformation info;

  public XIGConceptMapHandler(XIGInformation info) {
    super();
    this.info = info;

  }

  public void fillOutJson(ConceptMap cm, JsonObject j) {
    if (cm.hasSourceScope()) {        j.add("sourceScope", cm.getSourceScope().primitiveValue()); }
    if (cm.hasTargetScope()) {        j.add("targetScope", cm.getTargetScope().primitiveValue()); }
    for (ConceptMapGroupComponent g : cm.getGroup()) {
      if (g.hasSource()) {    
        j.forceArray("sources").add(g.getSource()); 
      }
      if (g.hasTarget()) {        
        j.forceArray("targets").add(g.getTarget()); 
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

  public static void buildUsages(XIGInformation info, ConceptMap cm) {
    if (cm.hasSourceScopeCanonicalType()) {
      info.recordUsage(cm, cm.getSourceScopeCanonicalType().getValue(), UsageType.CM_SCOPE);
    }
    if (cm.hasTargetScopeCanonicalType()) {
      info.recordUsage(cm, cm.getTargetScopeCanonicalType().getValue(), UsageType.CM_SCOPE);
    }
    for (ConceptMapGroupComponent g : cm.getGroup()) {
      info.recordUsage(cm, g.getSource(), UsageType.CM_SCOPE);
      info.recordUsage(cm, g.getSource(), UsageType.CM_SCOPE);
      for (SourceElementComponent e : g.getElement()) {
        info.recordUsage(cm, e.getValueSet(), UsageType.CM_MAP);
        for (TargetElementComponent t : e.getTarget()) {
          info.recordUsage(cm, t.getValueSet(), UsageType.CM_MAP);
          for (OtherElementComponent p : t.getProduct()) {
            info.recordUsage(cm, p.getValueSet(), UsageType.CM_MAP);
          }
          for (OtherElementComponent d : t.getDependsOn()) {
            info.recordUsage(cm, d.getValueSet(), UsageType.CM_MAP);
          }
        }
      }
      if (g.hasUnmapped()) {
        info.recordUsage(cm, g.getUnmapped().getValueSet(), UsageType.CM_UNMAP);
        info.recordUsage(cm, g.getUnmapped().getOtherMap(), UsageType.CM_UNMAP);
      }
    }
  }
}
