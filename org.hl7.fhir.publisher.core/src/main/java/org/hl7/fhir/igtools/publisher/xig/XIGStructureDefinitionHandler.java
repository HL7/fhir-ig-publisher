package org.hl7.fhir.igtools.publisher.xig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.xig.XIGInformation.CanonicalResourceUsage;
import org.hl7.fhir.igtools.publisher.xig.XIGInformation.UsageType;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionContextComponent;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonObject;

public class XIGStructureDefinitionHandler extends XIGHandler {

  private XIGInformation info;
  private static Set<String> pidlist = new HashSet<>();

  public XIGStructureDefinitionHandler(XIGInformation info) {
    super();
    this.info = info;
  }

  public void fillOutJson(String pid, StructureDefinition sd, JsonObject j) {
    if (ProfileUtilities.isExtensionDefinition(sd)) {
      info.getExtensionHandler().seeExtension(sd);
    } else {
      for (ElementDefinition ed : sd.getSnapshot().getElement()) {
        for (TypeRefComponent tr : ed.getType()) {
          if ("Extension".equals(tr.getCode())) {
            for (CanonicalType u : tr.getProfile()) {
              ElementDefinition focus = getParent(sd.getSnapshot().getElement(), ed);
              info.getExtensionHandler().seeUse(u.getValue(), focus.getBase().getPath(), focus.getPath());
            }
          }
        }
      }
    }
    if (sd.hasFhirVersion()) {      j.add("fhirVersion", sd.getFhirVersion().toCode()); }
    if (sd.hasKind()) {             j.add("kind", sd.getKind().toCode()); }
    if (sd.hasAbstract()) {         j.add("abstract", sd.getAbstract()); }
    if (sd.hasType()) {             j.add("sdTtype", sd.getType()); }
    if (sd.hasDerivation()) {       j.add("derivation", sd.getDerivation().toCode()); }
    if (sd.hasBaseDefinition()) {   j.add("base", sd.getBaseDefinition()); }

    for (StringType t : sd.getContextInvariant()) {
      j.forceArray("contextInvs").add(t.asStringValue()); 
    }

    for (StructureDefinitionContextComponent t : sd.getContext()) {
      j.forceArray("contexts").add(t.getType().toCode()+":"+t.getExpression()); 
    }

    for (Coding t : sd.getKeyword()) {
      j.forceArray("keywords").add(t.toString()); 
    }  

    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      if (!ed.getPath().equals("Bundle.entry.resource")) {
        for (TypeRefComponent tr : ed.getType()) {
          if (!"Extension".equals(tr.getCode()) && tr.getProfile().size() == 1&& !tr.getProfile().get(0).asStringValue().startsWith("http://hl7.org/fhir/StructureDefinition/")) {
            if (!pidlist.contains(pid)) {
//              System.out.println(pid+"\t"+sd.getUrl()+"\t"+ed.getPath()+":"+tr.getCode()+"\t"+tr.getProfile().get(0).asStringValue());
              pidlist.add(pid);
            }
          }
        }
      }
    }
  }

  private ElementDefinition getParent(List<ElementDefinition> list, ElementDefinition ed) {
    int i = list.indexOf(ed);
    while (i >= 0 && !ed.getPath().startsWith(list.get(i).getPath()+".")) {
      i--;
    }
    if (i < 0) {
      return ed;
    } else {
      ElementDefinition t = list.get(i);
      return t.getPath().endsWith(".extension") ? getParent(list, t) : t;
    }
  }

  public PageContent makeLogicalsPage(String realm) {
    List<StructureDefinition> list = new ArrayList<>();
    for (CanonicalResource cr : info.getResources().values()) {
      if (meetsRealm(cr, realm)) {
        if (cr instanceof StructureDefinition) {
          StructureDefinition sd = (StructureDefinition) cr;
          if ((sd.getKind() == StructureDefinitionKind.LOGICAL)) {
            list.add(sd);
          }
        }
      }
    }
    Collections.sort(list, new CanonicalResourceSorter());
    StringBuilder b = new StringBuilder();

    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, false);
    DuplicateTracker dt = new DuplicateTracker();
    for (StructureDefinition sd : list) {
      crTr(b, dt, sd, 0);

    }
    b.append("</table>\r\n");

    return new PageContent("Logical Models ("+list.size()+")", b.toString());
  }

  public PageContent makeExtensionsPage(XIGRenderer renderer, String string, String realm) throws IOException {
    Map<String, List<StructureDefinition>> profiles = new HashMap<>();
    for (CanonicalResource cr : info.getResources().values()) {
      if (meetsRealm(cr, realm)) {
        boolean ok = false;
        if (cr instanceof StructureDefinition) {
          StructureDefinition sd = (StructureDefinition) cr;
          if ("Extension".equals(sd.getType())) {
            for (StructureDefinitionContextComponent t : sd.getContext()) {
              String m = descContext(t);
              if (m != null) {
                if (!profiles.containsKey(m)) {
                  profiles.put(m, new ArrayList<>());
                }
                profiles.get(m).add(sd);
                ok = true;
              }
            }
            if (!ok) {
              if (!profiles.containsKey("s")) {
                profiles.put("No Context", new ArrayList<>());
              }
              profiles.get("No Context").add(sd);        
            }
          }
        }
      }
    }
    StringBuilder b = new StringBuilder();
    int t = 0;
    b.append("<ul style=\"column-count: 3\">\r\n");
    for (String s : Utilities.sorted(profiles.keySet())) {
      t = t + profiles.get(s).size();
      renderer.addPage(b, realmPrefix(realm)+"extension-"+s.toLowerCase()+".html", new PageContent(s+" ("+profiles.get(s).size()+")", makeProfilesTypePage(profiles.get(s), s, "Extension")));
    }
    b.append("</ul>\r\n");

    return new PageContent("Extensions ("+t+")", b.toString());
  }

  private String descContext(StructureDefinitionContextComponent t) {
    switch (t.getType()) {
    case ELEMENT:
      if (t.getExpression().contains(".")) {
        return t.getExpression().substring(0, t.getExpression().indexOf("."));        
      } else {
        return t.getExpression();
      }
    case EXTENSION:
      return "Extension";
    case FHIRPATH:
      return "FHIRPath";
    default:
      return "Unknown";
    }
  }

  public String makeProfilesTypePage(List<StructureDefinition> list, String type, String actualType) {
    Collections.sort(list, new CanonicalResourceSorter());
    StringBuilder b = new StringBuilder();
    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, true);
    int i = 0;
    DuplicateTracker dt = new DuplicateTracker();
    for (StructureDefinition sd : list) {
      i++;
      sd.setUserData("#", i);
      crTr(b, dt, sd, i);
    }
    b.append("</table>\r\n");
    StructureDefinition sdt = info.getCtxt().fetchTypeDefinition(actualType);
    if (sdt != null) {
      List<String> paths = new ArrayList<>();
      for (ElementDefinition ed : sdt.getSnapshot().getElement()) {
        if (!ed.getPath().endsWith(".id")) {
          paths.add(ed.getPath());
        }
      }
      for (StructureDefinition sd : list) {
        List<String> tpaths = new ArrayList<>();

        for (ElementDefinition ed : sd.getDifferential().getElement()) {
          if (ed.getPath() != null) {
            tpaths.add(ed.getPath());
          }
        }
        fillOutSparseElements(tpaths);
        for (String t : tpaths) {
          if (!paths.contains(t)) {
            int ndx = paths.indexOf(head(t));
            if (ndx == -1) {
              paths.add(t);
            } else {              
              paths.add(ndx+1, t);
            }
          }
        }
      }
      b.append("<table class=\"grid\">\r\n");
      b.append("<tr>\r\n");
      b.append("<td></td>\r\n");
      for (StructureDefinition sd : list) {
        b.append("<td>"+sd.getUserData("#")+"</td>\r\n");
      }
      b.append("</tr>\r\n");
      for (String p : paths) {
        b.append("<tr>\r\n");
        b.append("<td>"+p+"</td>\r\n");
        for (StructureDefinition sd : list) {
          b.append(analyse(p, sd)+"\r\n");
        }
        b.append("</tr>\r\n");
      }
      b.append("</table>\r\n");
      b.append(legend());
    }

    return b.toString();
  }


  private String legend() {
    return
        "<span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #cafcd3\" title=\"Slicing\">S</span>: There is slicing defined in the element(s)<br/>\r\n"+
        "<span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #b7e8f7\" title=\"Cardinality\">C</span>: There is cardinality erstrictions defined in the element(s)<br/>\r\n"+
        "<span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #fdf4f4\" title=\"invariants\">I</span>: There is invariants defined in the element(s)<br/>\r\n"+
        "<span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #fcfccc\" title=\"Fixed\">F</span>: There is a fixed or pattern value defined in the element(s)<br/>\r\n"+
        "<span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #fcd9f1\" title=\"Documentation\">D</span>: There is document provided in the element(s)<br/>\r\n"+
        "<span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #fce0d9\" title=\"Binding\">B</span>: There is terminology bindings defined in the element(s)<br/>\r\n"+
        "<span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #ccf2ff\" title=\"Must Support\">M</span>: At least one of the element(s) has must-support = true<br/>\r\n"+
        " (N): The number of elements if > 1";

  }

  private void fillOutSparseElements(List<String> tpaths) {
    int i = 1;
    while (i < tpaths.size()) {
      if (Utilities.charCount(tpaths.get(i-1), '.') < Utilities.charCount(tpaths.get(i), '.')-1) {
        tpaths.add(i, head(tpaths.get(i)));
      } else {
        i++;
      }
    }
  }


  private String head(String path) {
    if (path.contains(".")) {
      return path.substring(0, path.lastIndexOf("."));
    } else {
      return path;
    }
  }


  private String analyse(String p, StructureDefinition sd) {
    List<ElementDefinition> list = new ArrayList<>();
    for (ElementDefinition ed : sd.getDifferential().getElement()) {
      if (p.equals(ed.getPath())) {
        list.add(ed);
      }
    }
    if (list.size() == 0) {
      return "<td style=\"background-color: #dddddd\"></td>";
    } else {
      boolean binding = false;
      boolean cardinality = false;
      boolean constraints = false;
      boolean fixed = false;
      boolean doco = false;
      boolean slicing = false;
      boolean mustSupport = false;
      for (ElementDefinition ed : list) {
        doco = doco || (ed.hasDefinition() || ed.hasComment());
        binding = binding || ed.hasBinding();
        cardinality = cardinality || ed.hasMin() || ed.hasMax();
        constraints = constraints || ed.hasConstraint();
        fixed = fixed || ed.hasPattern() || ed.hasFixed();
        slicing = slicing || ed.hasSlicing() || ed.hasSliceName();
        mustSupport = mustSupport || ed.getMustSupport();
      }
      String s = "";
      if (slicing) {
        s = s +" <span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #cafcd3\" title=\"Slicing\">S</span>";
      }
      if (cardinality) {
        s = s +" <span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #b7e8f7\" title=\"Cardinality\">C</span>";
      }
      if (constraints) {
        s = s +" <span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #fdf4f4\" title=\"constraints\">I</span>";
      }
      if (fixed) {
        s = s +" <span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #fcfccc\" title=\"Fixed\">F</span>";
      }
      if (doco) {
        s = s +" <span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #fcd9f1\" title=\"Documentation\">D</span>";
      }
      if (binding) {
        s = s +" <span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #fce0d9\" title=\"Binding\">B</span>";
      }
      if (binding) {
        s = s +" <span style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: black; background-color: #ccf2ff\" title=\"Must Support\">M</span>";
      }
      if (list.size() > 1) {
        return "<td style=\"background-color: #ffffff\">"+s+" ("+list.size()+")</td>";
      } else {
        return "<td style=\"background-color: #ffffff\">"+s+"</td>";
      }
    }
  }

  public PageContent makeProfilesPage(XIGRenderer renderer, StructureDefinitionKind kind, String prefix, String title, String realm) throws IOException {
    Map<String, List<StructureDefinition>> profiles = new HashMap<>();
    for (CanonicalResource cr : info.getResources().values()) {
      if (meetsRealm(cr, realm)) {
        if (cr instanceof StructureDefinition) {
          StructureDefinition sd = (StructureDefinition) cr;
          if ((sd.getKind() == kind) || (kind == null && (sd.getKind() == StructureDefinitionKind.COMPLEXTYPE ||
              sd.getKind() == StructureDefinitionKind.PRIMITIVETYPE))) {
            String t = sd.getDifferential().getElementFirstRep().getPath();
            if (t == null) {
              t = sd.getType();
            }
            if (t == null) {
              break;
            }
            if (t.contains(".")) {
              t = t.substring(0, t.indexOf("."));
            }
            if (!"Extension".equals(t)) {
              if (!profiles.containsKey(t)) {
                profiles.put(t, new ArrayList<>());
              }
              profiles.get(t).add(sd);
            }
          }
        }
      }
    }
    StringBuilder b = new StringBuilder();
    int t = 0;
    b.append("<ul style=\"column-count: 3\">\r\n");
    for (String s : Utilities.sorted(profiles.keySet())) {
      t = t + profiles.get(s).size();
      renderer.addPage(b, realmPrefix(realm)+prefix+"-"+s.toLowerCase()+".html",
          new PageContent(s+" ("+profiles.get(s).size()+")", makeProfilesTypePage(profiles.get(s), s, s)));
    }
    b.append("</ul>\r\n");

    return new PageContent(title+" ("+t+")", b.toString());
  }

  public static void buildUsages(XIGInformation info, StructureDefinition sd) {
    info.recordUsage(sd, sd.getBaseDefinition(), UsageType.DERIVATION);
    for (ElementDefinition ed : sd.getDifferential().getElement()) {
      for (TypeRefComponent tr : ed.getType()) {
        for (CanonicalType c : tr.getProfile()) {
          info.recordUsage(sd, c.getValue(), UsageType.SD_PROFILE);
        }
        for (CanonicalType c : tr.getTargetProfile()) {
          info.recordUsage(sd, c.getValue(), UsageType.TARGET);
        }
      }
      if (ed.hasBinding()) {
        info.recordUsage(sd, ed.getBinding().getValueSet(), UsageType.BINDING);
      }
    }
  }

  public PageContent makeExtensionUsagePage(XIGRenderer renderer, String title, String realm, boolean core) throws IOException {
    Map<String, List<CanonicalResourceUsage>> usages = new HashMap<>();
    for (String url : info.getUsages().keySet()) {
      boolean isCore = isCore(url);
      if (isCore == core && isExtension(url, isCore)) {
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
      String p = getSDPath(s, core);
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

  public PageContent makeProfilesUsagePage(XIGRenderer renderer, String title, String realm, boolean core) throws IOException {
    Map<String, List<CanonicalResourceUsage>> usages = new HashMap<>();
    for (String url : info.getUsages().keySet()) {
      boolean isCore = isCore(url);
      if (isCore == core && isProfile(url, isCore)) {
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
      String p = getSDPath(s, core);
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

  private String getSDPath(String url, boolean core) {
    if (core) {
      StructureDefinition sd = info.getCtxt().fetchResource(StructureDefinition.class, url);
      if (sd != null) {
        return sd.getWebPath();
      }
    } else {
      CanonicalResource cr = info.resources.get(url);
      if (cr != null ) {
        return cr.getUserString("filebase")+".html";
      }
    }
    return null;
  }

  private boolean isExtension(String url, boolean isCore) {
    CanonicalResource cr = isCore ? info.getCtxt().fetchResource(StructureDefinition.class, url) : info.resources.get(url);
    if (cr != null && cr instanceof StructureDefinition) {
      StructureDefinition sd = (StructureDefinition) cr;
      return "Extension".equals(sd.getType()) && !url.equals("http://hl7.org/fhir/StructureDefinition/Extension");
    }
    return false;
  }

  private boolean isProfile(String url, boolean isCore) {
    CanonicalResource cr = isCore ? info.getCtxt().fetchResource(StructureDefinition.class, url) : info.resources.get(url);
    if (cr != null && cr instanceof StructureDefinition) {
      StructureDefinition sd = (StructureDefinition) cr;
      return !"Extension".equals(sd.getType()) && !url.equals("http://hl7.org/fhir/StructureDefinition/Extension") && sd.getDerivation() == TypeDerivationRule.CONSTRAINT;
    }
    return false;
  }

  private boolean isCore(String url) {
    return url.startsWith("http://hl7.org/fhir/StructureDefinition/");
  }


}
