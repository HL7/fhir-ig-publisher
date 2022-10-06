package org.hl7.fhir.igtools.publisher.utils.xig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.igtools.publisher.utils.xig.XIGHandler.PageContent;
import org.hl7.fhir.igtools.publisher.utils.xig.XIGInformation.UsageType;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeType;
import org.hl7.fhir.r5.model.SearchParameter;
import org.hl7.fhir.r5.model.SearchParameter.SearchParameterComponentComponent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class XIGSearchParameterHandler extends XIGHandler {

  private XIGInformation info;

  public XIGSearchParameterHandler(XIGInformation info) {
    super();
    this.info = info;

  }

  public void fillOutJson(SearchParameter sp, JsonObject j) {
    if (sp.hasCode()) {            j.addProperty("code", sp.getCode()); }
    if (sp.hasType()) {            j.addProperty("type", sp.getType().toCode()); }
    for (CodeType t : sp.getBase()) {
      if (!j.has("resources")) {
        j.add("resources", new JsonArray());
      }
      j.getAsJsonArray("resources").add(t.toString()); 
      info.getSpr().add(t.toString());
    }
  }
  
  public PageContent makeSearchParamsPage(String r, String title, String realm) {
    List<SearchParameter> list = new ArrayList<>();
    for (CanonicalResource cr : info.getResources().values()) {
      if (meetsRealm(cr, realm)) {
        if (cr instanceof SearchParameter) {
          SearchParameter sp = (SearchParameter) cr;
          boolean ok = false;
          for (CodeType c : sp.getBase()) {
            if (r.equals(c.asStringValue())) {
              ok = true;
            }
          }
          if (ok) {
            list.add(sp);
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
    for (SearchParameter sp : list) {
      crTr(b, dt, sp, 0);       
    }
    b.append("</table>\r\n");

    return new PageContent(title+" ("+list.size()+")", b.toString());
  }

  public static void buildUsages(XIGInformation info, SearchParameter sp) {
    info.recordUsage(sp, sp.getDerivedFrom(), UsageType.DERIVATION);
    for (SearchParameterComponentComponent t : sp.getComponent()) {
      info.recordUsage(sp, t.getDefinition(), UsageType.SP_PROFILE);
    }
    
  }

}
