package org.hl7.fhir.igtools.publisher.xig;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.model.JsonObject;


public class XIGHandler {


  public class DuplicateTracker {

    Set<String> keys = new HashSet<>();
    Set<String> names = new HashSet<>();
    public int hasDuplicate(CanonicalResource cr) {
      if (!cr.hasName()) {
        return 0;
      }
      String key = cr.getName()+"|"+cr.getDescription();
      boolean exists = keys.contains(key);
      keys.add(key);
      if (exists) {
        return 2;
      }
      key = cr.getName().toLowerCase();
      exists = names.contains(key);
      names.add(key);
      if (exists) {
        return 1;
      }
      return 0;
    }
  }

  public static class PageContent {
    String title;
    String content;

    public PageContent(String title, String content) {
      super();
      this.title = title;
      this.content = content;
    }
    public String getTitle() {
      return title;
    }
    public String getContent() {
      return content;
    }
  }

  public static class CanonicalResourceSorter implements Comparator<CanonicalResource> {

    @Override
    public int compare(CanonicalResource arg0, CanonicalResource arg1) {
      return StringUtils.compareIgnoreCase(arg0.present(), arg1.present());
    }
  }
  

  protected String prepPackage(String pck) {
    if (pck == null) {
      return null;
    }
    if (pck.contains("#")) {
      pck = pck.substring(0, pck.indexOf("#"));
    }
    return pck.replace(".", ".<wbr/>");
  }

  protected String crlink(CanonicalResource sd) {
    return "<a href=\""+sd.getUserString("filebase")+".html\">"+sd.present()+"</a>";
  }

  protected void crTrHeaders(StringBuilder b, boolean c) {
    b.append("<tr>"+(c ? "<td>#<td>" : "")+"<td>Name</td><td>Source</td><td>Ver</td><td>Description</td></li>\r\n");
  }

  private String ver(String ver) {
    if (VersionUtilities.isR2Ver(ver)) {
      return "R2";
    }
    if (VersionUtilities.isR2BVer(ver)) {
      return "R2B";
    }
    if (VersionUtilities.isR3Ver(ver)) {
      return "R3";
    }
    if (VersionUtilities.isR4Ver(ver)) {
      return "R4";
    }
    if (VersionUtilities.isR4BVer(ver)) {
      return "R4B";
    }
    if (VersionUtilities.isR5Ver(ver)) {
      return "R5";
    }
    if (VersionUtilities.isR6Ver(ver)) {
      return "R6";
    }
    return "R?";
  }

  protected void crTr(StringBuilder b, DuplicateTracker dt, CanonicalResource cr, int count) {
    b.append("<tr"+rowColor(dt.hasDuplicate(cr))+">"+(count > 0 ? "<td>"+count+"<td>" : "")+
        "<td title=\""+cr.getUrl()+"#"+cr.getVersion()+"\">"+crlink(cr)+"</td><td><a href=\""+cr.getWebPath()+"\">"+cr.getUserString("pid")+"</a></td>"+
        "<td>"+ver(cr.getUserString("fver"))+"</td>"+
        "<td>"+Utilities.escapeXml(cr.getDescription())+"</td>\r\n");
  }

  protected String rowColor(int dupl) {
    switch (dupl) {
    case 2: return " style=\"background-color: #ffabab\"";
    case 1: return " style=\"background-color: #fcd7d7\"";
    case 0: return "";
    }
    return "";
  }


  protected boolean meetsRealm(CanonicalResource cr, String realm) {
    if (realm.equals("all")) {
      return true;
    }
    if (Utilities.existsInList(realm, "hl7", "ihe")) {
      return realm.equals(cr.getUserData("auth"));
    } else {
      if (realm.equals(cr.getUserData("realm"))) {
        return true;
      }
      JsonObject j = (JsonObject) cr.getUserData("json");
      if (j.has("jurisdictions")) {
        for (String str : j.getStrings("jurisdictions")) {
          if (realm.equals(str)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  protected boolean inMode(String mode, Set<String> systems) {
    if (mode == null) {
      return true;
    }
    switch (mode) {
    case "cpt": return systems.contains("http://www.ama-assn.org/go/cpt");    
    case "dcm": return systems.contains("http://dicom.nema.org/resources/ontology/DCM");   
    case "example": 
      for (String s : systems) {
        if (s.contains("example.org")) {
          return true;
        }
      }
      return false;
    case "fhir":    
      for (String s : systems) {
        if (s.startsWith("http://hl7.org/fhir")) {
          return true;
        }
      }
      return false;
    case "cvx": return systems.contains("http://hl7.org/fhir/sid/cvx");   
    case "icd": 
      for (String s : systems) {
        if (Utilities.existsInList(s, "http://hl7.org/fhir/sid/icd-9-cm", "http://hl7.org/fhir/sid/icd-10", "http://fhir.de/CodeSystem/dimdi/icd-10-gm", "http://hl7.org/fhir/sid/icd-10-nl 2.16.840.1.113883.6.3.2", "http://hl7.org/fhir/sid/icd-10-cm")) {
          return true;
        }
      }
      return false;     
    case "ietf": 
      for (String s : systems) {
        if (s.contains(":ieft:")) {
          return true;
        }
      }
      return false;    
    case "ihe":  
      for (String s : systems) {
        if (s.contains("ihe.net")) {
          return true;
        }
      }
      return false;    
    case "icpc":  
      for (String s : systems) {
        if (s.contains("icpc")) {
          return true;
        }
      }
      return false;    
    case "internal": return systems.contains("");    
    case "iso": 
      for (String s : systems) {
        if (s.contains(":iso:")) {
          return true;
        }
      }
      return false;
    case "loinc": return systems.contains("http://loinc.org");    
    case "ncpdp": return systems.contains("");    
    case "ndc": return systems.contains("http://hl7.org/fhir/sid/ndc");    
    case "nucc":  
      for (String s : systems) {
        if (s.contains("nucc")) {
          return true;
        }
      }
      return false;  
    case "oid":  
      for (String s : systems) {
        if (s.contains("urn:oid:")) {
          return true;
        }
      }
      return false;     
    case "tho":  
      for (String s : systems) {
        if (s.contains("urn:oid:")) {
          return true;
        }
      }
      return false;    
    case "rx": return systems.contains("http://www.nlm.nih.gov/research/umls/rxnorm");    
    case "sct": return systems.contains("http://snomed.info/sct");    
    case "ucum": return systems.contains("http://unitsofmeasure.org");    
    }
    return false; 
  }


  public String realmPrefix(String realm) {
    return realm+"-";
  }

  
}
