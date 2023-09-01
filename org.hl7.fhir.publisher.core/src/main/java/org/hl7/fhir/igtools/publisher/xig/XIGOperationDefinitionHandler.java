package org.hl7.fhir.igtools.publisher.xig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.igtools.publisher.xig.XIGInformation.UsageType;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.model.Enumerations.VersionIndependentResourceTypesAll;
import org.hl7.fhir.r5.model.OperationDefinition;
import org.hl7.fhir.r5.model.OperationDefinition.OperationDefinitionParameterComponent;
import org.hl7.fhir.utilities.json.model.JsonObject;

public class XIGOperationDefinitionHandler extends XIGHandler {

  private XIGInformation info;

  public XIGOperationDefinitionHandler(XIGInformation info) {
    super();
    this.info = info;

  }

  public void fillOutJson(OperationDefinition od, JsonObject j) {
    if (od.hasAffectsState()) {    j.add("affectsState", od.getAffectsState()); }
    if (od.hasCode()) {            j.add("code", od.getCode()); }
    if (od.hasBase()) {            j.add("base", od.getBase()); }
    if (od.hasSystem()) {          j.add("system", od.getSystem()); }
    if (od.hasType())     {        j.add("type", od.getType()); }
    if (od.hasInstance()) {        j.add("instance", od.getInstance()); }
    for (Enumeration<VersionIndependentResourceTypesAll> t : od.getResource()) {
      j.forceArray("resources").add(t.getCode()); 
      info.getOpr().add(t.toString());
    }
  }
  
  public PageContent makeOperationsPage(String r, String title, String realm) {
    List<OperationDefinition> list = new ArrayList<>();
    for (CanonicalResource cr : info.getResources().values()) {
      if (meetsRealm(cr, realm)) {
        if (cr instanceof OperationDefinition) {
          OperationDefinition od = (OperationDefinition) cr;
          boolean ok = false;
          for (Enumeration<VersionIndependentResourceTypesAll> c : od.getResource()) {
            if (r.equals(c.getCode())) {
              ok = true;
            }
          }
          if (ok) {
            list.add(od);
          }
        }
      }
    }
    if (list.isEmpty() && r != null) {
      return null;
    }
    Collections.sort(list, new CanonicalResourceSorter());
    StringBuilder b = new StringBuilder();

    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, false);
    DuplicateTracker dt = new DuplicateTracker();
    for (OperationDefinition od : list) {
      crTr(b, dt, od, 0);       
    }
    b.append("</table>\r\n");

    return new PageContent(title+" ("+list.size()+")", b.toString());
  }

  public static void buildUsages(XIGInformation info, OperationDefinition od) {
    info.recordUsage(od, od.getBase(), UsageType.DERIVATION);
    info.recordUsage(od, od.getInputProfile(), UsageType.OP_PROFILE);
    info.recordUsage(od, od.getOutputProfile(), UsageType.OP_PROFILE);
    for (OperationDefinitionParameterComponent t : od.getParameter()) {
      for (CanonicalType c : t.getTargetProfile()) {
        info.recordUsage(od, c.getValue(), UsageType.TARGET);
      }
      if (t.hasBinding()) {
        info.recordUsage(od, t.getBinding().getValueSet(), UsageType.BINDING);
      }
    }
  }


}
