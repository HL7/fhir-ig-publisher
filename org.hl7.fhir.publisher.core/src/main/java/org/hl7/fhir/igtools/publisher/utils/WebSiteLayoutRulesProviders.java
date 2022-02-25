package org.hl7.fhir.igtools.publisher.utils;

import java.io.IOException;
import java.util.List;

import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;

public class WebSiteLayoutRulesProviders {

  public interface WebSiteLayoutRulesProvider {
    public boolean checkNpmId(List<ValidationMessage> res);
    public boolean checkCanonicalAndUrl(List<ValidationMessage> res, String canonical, String url);
    public String getDestination(String rootFolder) throws IOException;
  }
  
  public static class DefaultNamingRulesProvider implements WebSiteLayoutRulesProvider {
    protected String id;
    protected String[] parts;

    protected boolean check(List<ValidationMessage> res, boolean b, String message) {
      if (!b)  {
        ValidationMessage msg = new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, "parameters", message, IssueSeverity.ERROR);
        res.add(msg);
      }
      return b;      
    }

    @Override
    public boolean checkNpmId(List<ValidationMessage> res) {
      return true;      
    }
    
    @Override
    public boolean checkCanonicalAndUrl(List<ValidationMessage> res, String canonical, String url) {
      return true;
    }
        
    @Override
    public String getDestination(String rootFolder) throws IOException {
      throw new Error("Must be overridden");
    }
  }

  public static class HL7NamingRulesProvider extends DefaultNamingRulesProvider {
    @Override
    public boolean checkNpmId(List<ValidationMessage> res) {
      return check(res, parts.length == 4 && "hl7".equals(parts[0]) && "fhir".equals(parts[1]), 
          "Package Id '"+id+"' is not valid:  must have 4 parts (hl7.fhir.[realm].[code]");
    }

    @Override
    public boolean checkCanonicalAndUrl(List<ValidationMessage> res, String canonical, String url) {
      boolean ok = true;
      if ("dk".equals(realm())) {
        ok = check(res, canonical.equals("http://hl7.dk/fhir/"+code()), 
            "canonical URL of "+canonical+" does not match the required canonical of http://hl7.dk/fhir/"+code());          
      } else if ("ch".equals(realm())) {
        ok = check(res, canonical.equals("http://fhir.ch/ig/"+code()), 
            "canonical URL of "+canonical+" does not match the required canonical of http://fhir.ch/ig/"+code());          
      } else if ("be".equals(realm())) {
        ok = check(res, canonical.equals("http://hl7belgium.org/profiles/fhir/"+code()) || canonical.equals("https://www.ehealth.fgov.be/standards/fhir/"+code()), 
            "canonical URL of "+canonical+" does not match the required canonical of http://hl7belgium.org/profiles/fhir/"+code()+" or https://www.ehealth.fgov.be/standards/fhir/"+code());          
      } else {
        // special case weirdity
        if ("uv".equals(realm()) && "smart-app-launch".equals(code())) {
          ok = check(res, canonical.equals("http://hl7.org/fhir/smart-app-launch"), "canonical URL of "+canonical+" does not match the required canonical of http://hl7.org/fhir/smart-app-launch");
        } else {
          ok = check(res, canonical.equals("http://hl7.org/fhir/"+realm()+"/"+code()) || canonical.equals("http://hl7.org/fhir/smart-app-launch"), "canonical URL of "+canonical+" does not match the required canonical of http://hl7.org/fhir/"+realm()+"/"+code());
        }
      }
      return check(res, canonical.startsWith(url), "Proposed canonical '"+canonical+"' does not match the web site URL '"+url+"'") && ok;
    }
        
    public String realm() {
      return parts[2];
    }
    
    public String code() {
      return parts[3];
    }  
    
    @Override
    public String getDestination(String rootFolder) throws IOException {
      if ("dk".equals(realm())) {
        return Utilities.path(rootFolder, code());
      } else if ("ch".equals(realm())) {
        return Utilities.path(rootFolder, code());
      } else if ("be".equals(realm())) {
        return Utilities.path(rootFolder, code());
      } else {
        // special case weirdity
        if ("uv".equals(realm()) && "smart-app-launch".equals(code())) {
          return Utilities.path(rootFolder, code());
        } else {
          return Utilities.path(rootFolder, realm(), code());
        }
      }
    }
  }

  public static class IHENamingRulesProvider extends DefaultNamingRulesProvider {
    public boolean checkNpmId(List<ValidationMessage> res) {
      return check(res, parts.length == 3 && "ihe".equals(parts[0]), 
          "Package Id '"+id+"' is not valid:  must have 3 parts (hl7.fhir.[domain].[profile]");
    }
    
    private String domain() {
      return parts[1];
    }
    
    private String profile() {
      return parts[2];
    }

    public boolean checkCanonicalAndUrl(List<ValidationMessage> res, String canonical, String url) {
      // IHE case differs, but not predictably, so we can't check case. 
      // canonical is https://profiles.ihe.net/${domain}/${profile} - see https://chat.fhir.org/#narrow/stream/179252-IG-creation/topic/IG.20Release.20Publication.20procedure/near/268010908      
      boolean ok = check(res, canonical.equalsIgnoreCase("https://profiles.ihe.net/"+domain()+"/"+profile()),
          "canonical URL of "+canonical+" does not match the required canonical of https://profiles.ihe.net/"+domain()+"/"+profile());          
      return check(res, canonical.startsWith(url), "Proposed canonical '"+canonical+"' does not match the web site URL '"+url+"'") && ok;
    }


    public String getDestination(String rootFolder) throws IOException {
      // there's a case problem here: if IHI lowercases package names, but not canonicals or folder URLs, then the case of this will be wrong
      // and can't upper case algorithmically. May have to pick up case from canonical?
      return Utilities.path(rootFolder, domain(), profile());      
    }

  }

  public static class FHIROrgNamingRulesProvider extends DefaultNamingRulesProvider {
    @Override
    public boolean checkNpmId(List<ValidationMessage> res) {
      return check(res, parts.length == 3 && "fhir".equals(parts[0]), 
          "Package Id '"+id+"' is not valid:  must have 3 parts (fhir.[org].[code]");
    }

    @Override
    public boolean checkCanonicalAndUrl(List<ValidationMessage> res, String canonical, String url) {
      return check(res, canonical != null && (canonical.equals("http://fhir.org/guides/"+org()+"/"+code())), 
          "canonical URL of "+canonical+" does not match the required canonical of http://fhir.org/guides/"+org()+"/"+code()) &&
          check(res, canonical.startsWith(url), "Proposed canonical '"+canonical+"' does not match the web site URL '"+url+"'");
    }
    
    public String org() {
      return parts[1];
    }
    
    public String code() {
      return parts[2];
    }
    
    public String getDestination(String rootFolder) throws IOException {
      return Utilities.path(rootFolder, org(), code());      
    }

  }

  public static class CQLNamingRulesProvider extends DefaultNamingRulesProvider {
    public boolean checkNpmId(List<ValidationMessage> res) {
      return true;
    }

    @Override
    public boolean checkCanonicalAndUrl(List<ValidationMessage> res, String canonical, String url) {
      return check(res, canonical.equals("http://cql.hl7.org") && url.equals("http://cql.hl7.org"), 
          "Proposed canonical '"+canonical+"' does not match the web site URL '"+url+"' with a value of http://cql.hl7.org");
    }
    

    @Override
    public String getDestination(String rootFolder) throws IOException {
      return rootFolder;
    }
    
  }

  public static class HL7TerminologyNamingRulesProvider extends DefaultNamingRulesProvider {
    public boolean checkNpmId(List<ValidationMessage> res) {
      return true;
    }

    @Override
    public boolean checkCanonicalAndUrl(List<ValidationMessage> res, String canonical, String url) {
      return check(res, canonical.equals("http://terminology.hl7.org") && url.equals("http://terminology.hl7.org"), 
          "Proposed canonical '"+canonical+"' does not match the web site URL '"+url+"' with a value of http://terminology.hl7.org");
    }
    
    @Override
    public String getDestination(String rootFolder) throws IOException {
      return rootFolder;
    }


  }

  public static WebSiteLayoutRulesProvider recogniseNpmId(String id, String[] p) {
    DefaultNamingRulesProvider res = new DefaultNamingRulesProvider();
    if (id.equals("hl7.terminology")) {
      res = new HL7TerminologyNamingRulesProvider();
    } else if (id.equals("hl7.cql")) {
      res = new CQLNamingRulesProvider();
    } else if (id.startsWith("fhir.")) {
      res = new FHIROrgNamingRulesProvider();
    } else if (id.startsWith("hl7.")) {
      res = new HL7NamingRulesProvider();
    } else if (id.startsWith("ihe.")) {
      res = new IHENamingRulesProvider();
    } 
    res.id = id;
    res.parts = p;
    return res;
  }

  
}
