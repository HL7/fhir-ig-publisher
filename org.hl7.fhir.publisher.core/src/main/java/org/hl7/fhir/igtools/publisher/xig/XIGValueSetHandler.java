package org.hl7.fhir.igtools.publisher.xig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.xig.XIGInformation.CanonicalResourceUsage;
import org.hl7.fhir.igtools.publisher.xig.XIGInformation.UsageType;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionContainsComponent;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonObject;


public class XIGValueSetHandler extends XIGHandler {

  private XIGInformation info;

  public XIGValueSetHandler(XIGInformation info) {
    super();
    this.info = info;

  }

  public void fillOutJson(ValueSet vs, JsonObject j) {
    if (vs.hasImmutable()) {        
      j.add("immutable", vs.getImmutable());
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

  public static void buildUsages(XIGInformation info, ValueSet vs) {
    for (ConceptSetComponent t : vs.getCompose().getInclude()) {
      info.recordUsage(vs, t.getSystem(), UsageType.VS_SYSTEM);
      for (CanonicalType c : t.getValueSet()) {
        info.recordUsage(vs, c.getValue(), UsageType.VS_VALUESET);
      }
    }
    if (vs.hasExpansion()) {
      for (ValueSetExpansionContainsComponent t : vs.getExpansion().getContains()) {
        info.recordUsage(vs, t.getSystem(), UsageType.VS_EXPANSION);
      }
    }
  }
  
  public PageContent makeCoreUsagePage(XIGRenderer renderer, String title, String realm) {
    Map<String, List<CanonicalResourceUsage>> usages = new HashMap<>();
    for (String url : info.getUsages().keySet()) {
      if (isCoreVS(url)) {
        List<CanonicalResourceUsage> ulist = null;
        for (CanonicalResourceUsage cr : info.getUsages().get(url)) {
          if (meetsRealm(cr.getResource(), realm)) {
            if (ulist == null) {
              ulist = new ArrayList<>();
              usages.put(url, ulist);
            }
            ulist.add(cr);
          }          
        }
      }
    }
    return renderUsages(title, usages);
  }

  private PageContent renderUsages(String title, Map<String, List<CanonicalResourceUsage>> usages) {
    StringBuilder b = new StringBuilder();
    int t = 0;
    b.append("<table class=\"grid\">\r\n");
    for (String s : Utilities.sorted(usages.keySet())) {
      t++;
      b.append("<tr><td>");
      String p = getVSPath(s, true);
      if (p != null) {
        b.append("<a href=\""+p+"\">"+s+"</a>");
      } else {
        b.append(s);
      }
      b.append("</td><td><ul>");
      for (CanonicalResourceUsage cu : usages.get(s)) {
        b.append("  <li>");
        b.append(crlink(cu.getResource()));
        b.append(cu.getUsage().getDisplay());
        b.append("</li>\r\n");
      }
      b.append("</ul></td></tr>\r\n");
    }
    b.append("</ul>\r\n");

    return new PageContent(title+" ("+t+")", b.toString());
  }

  public PageContent makeTHOUsagePage(XIGRenderer renderer, String title, String realm) {
    Map<String, List<CanonicalResourceUsage>> usages = new HashMap<>();
    for (String url : info.getUsages().keySet()) {
      if (isTHOVS(url)) {
        List<CanonicalResourceUsage> ulist = null;
        for (CanonicalResourceUsage cr : info.getUsages().get(url)) {
          if (meetsRealm(cr.getResource(), realm)) {
            if (ulist == null) {
              ulist = new ArrayList<>();
              usages.put(url, ulist);
            }
            ulist.add(cr);
          }          
        }
      }
    }
    return renderUsages(title, usages);
  }

  private boolean isTHOVS(String url) {
    return url.startsWith("http://terminology.hl7.org/ValueSet/");
 }

  private String getVSPath(String url, boolean core) {
    if (core) {
      ValueSet vs = info.getCtxt().fetchResource(ValueSet.class, url);
      if (vs != null) {
        return vs.getWebPath();
      }
    } else {
      CanonicalResource cr = info.resources.get(url);
      if (cr != null ) {
        return cr.getUserString("filebase")+".html";
      }
    }
    return null;
  }

  private boolean isCoreVS(String url) {
    return url.startsWith("http://hl7.org/fhir/ValueSet/");
  }

}
