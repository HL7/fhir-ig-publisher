package org.hl7.fhir.igtools.publisher.utils.xig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.utilities.Utilities;

import com.google.gson.JsonObject;

public class XIGValueSetHandler extends XIGHandler {

  private XIGInformation info;

  public XIGValueSetHandler(XIGInformation info) {
    super();
    this.info = info;

  }

  public void fillOutJson(ValueSet vs, JsonObject j) {
    if (vs.hasImmutable()) {        
      j.addProperty("immutable", vs.getImmutable());
    }
  }


  private boolean isOKVsPid(String pid) {
    if (pid.contains("#")) {
      pid = pid.substring(0, pid.indexOf("#"));
    }
    return !Utilities.existsInList(pid, "us.nlm.vsac", "fhir.dicom", "us.cdc.phinvads");
  }

  private boolean isOkVSMode(String mode, ValueSet vs) {
    if (mode == null) {
      return true;
    } else if ("exp".equals("mode")) {
      return !vs.hasCompose() && vs.hasExpansion();
    } else {
      Set<String> systems = new HashSet<>();
      for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
        if (inc.getSystem() != null) {
          systems.add(inc.getSystem());
        }
      }
      switch (mode) {
      case "vs" : return systems.isEmpty();
      case "mixed" : return systems.size() > 1;
      default: return inMode(mode, systems);  
      }
    }
  }
  
  protected PageContent makeValueSetsPage(String mode, String title, String realm) {
    List<ValueSet> vslist = new ArrayList<>();
    for (CanonicalResource cr : info.getResources().values()) {
      if (meetsRealm(cr, realm)) {
        if (cr instanceof ValueSet) {
          ValueSet vs = (ValueSet) cr;
          String pid = vs.getUserString("pid");
          if (isOKVsPid(pid) && isOkVSMode(mode, vs)) {
            vslist.add(vs);
          }
        }
      }
    }
    if (vslist.isEmpty() && mode != null) {
      return null;
    }
    Collections.sort(vslist, new CanonicalResourceSorter());
    StringBuilder b = new StringBuilder();
    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, false);
    DuplicateTracker dt = new DuplicateTracker();
    for (ValueSet vs : vslist) {
      crTr(b, dt, vs, 0);            
    }
    b.append("</table>\r\n");
    return new PageContent(title+" ("+vslist.size()+")", b.toString());
  }
}
