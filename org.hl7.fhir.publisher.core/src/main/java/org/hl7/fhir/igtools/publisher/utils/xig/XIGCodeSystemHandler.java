package org.hl7.fhir.igtools.publisher.utils.xig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.igtools.publisher.utils.xig.XIGHandler.PageContent;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.CodeSystemContentMode;

import com.google.gson.JsonObject;

public class XIGCodeSystemHandler extends XIGHandler {

  private XIGInformation info;

  public XIGCodeSystemHandler(XIGInformation info) {
    super();
    this.info = info;

  }

  public void fillOutJson(CodeSystem cs, JsonObject j) {
    if (cs.hasCaseSensitive()) {    j.addProperty("caseSensitive", cs.getCaseSensitive()); }
    if (cs.hasValueSet()) {         j.addProperty("valueSet", cs.getValueSet()); }
    if (cs.hasHierarchyMeaning()) { j.addProperty("hierarchyMeaning", cs.getHierarchyMeaning().toCode());}
    if (cs.hasCompositional()) {    j.addProperty("compositional", cs.getCompositional()); }
    if (cs.hasVersionNeeded()) {    j.addProperty("versionNeeded", cs.getVersionNeeded()); }
    if (cs.hasContent()) {          j.addProperty("content", cs.getContent().toCode()); }
    if (cs.hasSupplements()) {      j.addProperty("supplements", cs.getSupplements()); }
  }
  
  
  public PageContent makeCodeSystemPage(CodeSystemContentMode mode, String title, String realm) {
    List<CodeSystem> list = new ArrayList<>();
    for (CanonicalResource cr : info.getResources().values()) {
      if (meetsRealm(cr, realm)) {
        if (cr instanceof CodeSystem) {
          CodeSystem cs = (CodeSystem) cr;
          boolean ok = cs.getContent() == mode;
          if (ok) {
            list.add(cs);
          }
        }
      }
    }
    if (list.isEmpty() && mode != null) {
      return null;
    }
    Collections.sort(list, new CanonicalResourceSorter());
    StringBuilder b = new StringBuilder();

    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, false);
    DuplicateTracker dt = new DuplicateTracker();
    for (CodeSystem cs : list) {
      crTr(b, dt, cs, 0);      
    }
    b.append("</table>\r\n");

    return new PageContent(title+" ("+list.size()+")", b.toString());
  }

}
