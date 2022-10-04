package org.hl7.fhir.igtools.publisher.utils.xig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.igtools.publisher.utils.xig.XIGHandler.PageContent;
import org.hl7.fhir.igtools.publisher.utils.xig.XIGInformation.UsageType;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.CodeType;
import org.hl7.fhir.r5.model.OperationDefinition;
import org.hl7.fhir.r5.model.OperationDefinition.OperationDefinitionParameterComponent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class XIGOperationDefinitionHandler extends XIGHandler {

  private XIGInformation info;

  public XIGOperationDefinitionHandler(XIGInformation info) {
    super();
    this.info = info;

  }

  public void fillOutJson(OperationDefinition od, JsonObject j) {
    if (od.hasAffectsState()) {    j.addProperty("affectsState", od.getAffectsState()); }
    if (od.hasCode()) {            j.addProperty("code", od.getCode()); }
    if (od.hasBase()) {            j.addProperty("base", od.getBase()); }
    if (od.hasSystem()) {          j.addProperty("system", od.getSystem()); }
    if (od.hasType())     {        j.addProperty("type", od.getType()); }
    if (od.hasInstance()) {        j.addProperty("instance", od.getInstance()); }
    for (CodeType t : od.getResource()) {
      if (!j.has("resources")) {
        j.add("resources", new JsonArray());
      }
      j.getAsJsonArray("resources").add(t.toString()); 
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
          for (CodeType c : od.getResource()) {
            if (r.equals(c.asStringValue())) {
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
