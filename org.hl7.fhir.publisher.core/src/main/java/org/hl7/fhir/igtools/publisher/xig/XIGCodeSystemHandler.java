package org.hl7.fhir.igtools.publisher.xig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.igtools.publisher.xig.XIGInformation.CanonicalResourceUsage;
import org.hl7.fhir.igtools.publisher.xig.XIGInformation.UsageType;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.Enumerations.CodeSystemContentMode;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonObject;


public class XIGCodeSystemHandler extends XIGHandler {

  private XIGInformation info;

  public XIGCodeSystemHandler(XIGInformation info) {
    super();
    this.info = info;

  }

  public void fillOutJson(CodeSystem cs, JsonObject j) {
    if (cs.hasCaseSensitive()) {    j.add("caseSensitive", cs.getCaseSensitive()); }
    if (cs.hasValueSet()) {         j.add("valueSet", cs.getValueSet()); }
    if (cs.hasHierarchyMeaning()) { j.add("hierarchyMeaning", cs.getHierarchyMeaning().toCode());}
    if (cs.hasCompositional()) {    j.add("compositional", cs.getCompositional()); }
    if (cs.hasVersionNeeded()) {    j.add("versionNeeded", cs.getVersionNeeded()); }
    if (cs.hasContent()) {          j.add("content", cs.getContent().toCode()); }
    if (cs.hasSupplements()) {      j.add("supplements", cs.getSupplements()); }
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

  public static void buildUsages(XIGInformation info, CodeSystem cs) {
    info.recordUsage(cs, cs.getValueSet(), UsageType.CS_VALUESET);
    info.recordUsage(cs, cs.getSupplements(), UsageType.CS_SUPPLEMENTS);
  }

  public PageContent makeCoreUsagePage(XIGRenderer renderer, String title, String realm) {
    Map<String, List<CanonicalResourceUsage>> usages = new HashMap<>();
    for (String url : info.getUsages().keySet()) {
      if (isCoreCS(url)) {
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
    StringBuilder b = new StringBuilder();
    int t = 0;
    b.append("<table class=\"grid\">\r\n");
    for (String s : Utilities.sorted(usages.keySet())) {
      t++;
      b.append("<tr><td>");
      String p = getCSPath(s, true);
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
      if (isTHOCS(url)) {
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
    StringBuilder b = new StringBuilder();
    int t = 0;
    b.append("<table class=\"grid\">\r\n");
    for (String s : Utilities.sorted(usages.keySet())) {
      t++;
      b.append("<tr><td>");
      String p = getCSPath(s, false);
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

  private boolean isTHOCS(String url) {
    return url.startsWith("http://terminology.hl7.org/CodeSystem/");
  }

  private String getCSPath(String url, boolean core) {
    if (core) {
      CodeSystem cs = info.getCtxt().fetchCodeSystem(url);
      if (cs != null) {
        return cs.getWebPath();
      }
    } else {
      CanonicalResource cr = info.resources.get(url);
      if (cr != null ) {
        return cr.getUserString("filebase")+".html";
      }
    }
    return null;
  }

  private boolean isCoreCS(String url) {
    return info.getCtxt().fetchCodeSystem(url) != null;
  }

}
