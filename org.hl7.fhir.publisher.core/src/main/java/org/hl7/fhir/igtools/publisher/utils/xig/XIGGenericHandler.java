package org.hl7.fhir.igtools.publisher.utils.xig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.igtools.publisher.utils.xig.XIGHandler.PageContent;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.CodeSystemContentMode;

import com.google.gson.JsonObject;

public class XIGGenericHandler extends XIGHandler {

  private XIGInformation info;

  public XIGGenericHandler(XIGInformation info) {
    super();
    this.info = info;

  }
  
  public PageContent makeResourcesPage(String type, String title, String realm) {
    List<CanonicalResource> list = new ArrayList<>();
    for (CanonicalResource cr : info.getResources().values()) {
      if (meetsRealm(cr, realm) && type.equals(cr.fhirType())) {
        list.add(cr);
      }
    }
    if (list.isEmpty()) {
      return null;
    }
    Collections.sort(list, new CanonicalResourceSorter());
    StringBuilder b = new StringBuilder();

    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, false);
    DuplicateTracker dt = new DuplicateTracker();
    for (CanonicalResource cr : list) {
      crTr(b, dt, cr, 0);      
    }
    b.append("</table>\r\n");

    return new PageContent(title+" ("+list.size()+")", b.toString());
  }

}
