package org.hl7.fhir.igtools.web;

import java.io.IOException;
import java.util.List;

import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;

import com.google.common.base.Strings;

public class WebSiteLayoutRulesProviders {

  public interface WebSiteLayoutRulesProvider {
    public boolean checkNpmId(List<ValidationMessage> res);
    public boolean checkCanonicalAndUrl(List<ValidationMessage> res, String canonical, String url);
    public String getDestination(String rootFolder) throws IOException;
  }
  
  public static class DefaultNamingRulesProvider implements WebSiteLayoutRulesProvider {
    protected String id;
    protected String[] parts;
    protected String tail;

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
      throw new Error("This website needs configuration or support in the IG publisher. Discuss on https://chat.fhir.org/#narrow/stream/179252-IG-creation");
    }

    public String desc() {
      return "No defined web Layout";
    }
  }
  
  public static class ScriptedNamingRulesProvider extends DefaultNamingRulesProvider {
    protected String idRule;
    protected String[] ruleParts;
    protected String canonicalRule;

    public ScriptedNamingRulesProvider(String idRule, String canonicalRule) {
      super();
      this.idRule = idRule;
      this.canonicalRule = canonicalRule;
      this.ruleParts = idRule.split("\\.");
    }

    protected boolean check(List<ValidationMessage> res, boolean b, String message) {
      if (!b)  {
        ValidationMessage msg = new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, "parameters", message, IssueSeverity.ERROR);
        res.add(msg);
      }
      return b;      
    }

    private String getPart(String name) {
      for (int i = 0; i < ruleParts.length; i++) {
        String p = ruleParts[i];
        if (p.equals("["+name+"]")) {
          return parts[i];
        }
      }
      return null;
    }

    @Override
    public boolean checkNpmId(List<ValidationMessage> res) {
      boolean ok = parts.length == ruleParts.length;
      for (int i = 0; i < ruleParts.length; i++) {
        String p = ruleParts[i];
        ok = ok && (p.startsWith("[") || p.equals(parts[i]));
      }
      return check(res, ok, "Package Id '"+id+"' is not valid:  must have the structure '"+idRule+"'");
    }
    
    @Override
    public boolean checkCanonicalAndUrl(List<ValidationMessage> res, String canonical, String url) {
        String category = getPart("category");
        String code = getPart("code");

        String u = canonicalRule.replace("[code]", code);

        if(u.contains("[category]")) {
          assert !Strings.isNullOrEmpty(category);
          u = u.replace("[category]", category);
        }

        boolean ok = check(res, canonical.equals(u) , "canonical URL of '"+canonical+"' does not match the required canonical of '"+u+"' [1]");
        return check(res, canonical.startsWith(url), "Proposed canonical '"+canonical+"' does not match the web site URL '"+url+"'") && ok;
    }
        
    @Override
    public String getDestination(String rootFolder) throws IOException {
      String category = getPart("category");
      String code = getPart("code");
      if (category == null) {
        return Utilities.path(rootFolder, code);
      } else {
        return Utilities.path(rootFolder, category, code);        
      }
    }

    public String desc() {
      return "Web Layout using the script "+idRule+" & "+canonicalRule;
    }

  }
  

  public static class HL7NamingRulesProvider extends DefaultNamingRulesProvider {
    @Override
    public boolean checkNpmId(List<ValidationMessage> res) {
      boolean ok = check(res, parts.length == 4, "Package Id '"+id+"' is not valid: must have 4 parts (hl7.fhir.[realm].[code]");
      ok = check(res, "hl7".equals(parts[0]), "Package Id '"+id+"' is not valid: must start with hl7.") && ok;
      if ("eu".equals(parts[1])) {
        ok = check(res, Utilities.existsInList(parts[2], "fhir"), "Package Id '"+id+"' is not valid: must start with hl7.eu.fhir.[code]") && ok;         
      } else {
        ok = check(res, Utilities.existsInList(parts[1], "fhir", "xprod", "cda", "ehrs"), "Package Id '"+id+"' is not valid: must start with hl7.fhir.[realm].[code], hl7.cda.[realm].[code], hl7.ehrs.[realm].[code] or hl7.xprod.[realm].[code]") && ok; 
      }
      return ok;
    }

    @Override
    public boolean checkCanonicalAndUrl(List<ValidationMessage> res, String canonical, String url) {
      boolean ok = true;
      if ("dk".equals(realm())) {
        ok = check(res, canonical.equals("http://hl7.dk/fhir/"+code()), 
            "canonical URL of "+canonical+" does not match the required canonical of http://hl7.dk/fhir/"+code()+" [2]");          
      } else if ("eu".equals(realm())) {
        ok = check(res, canonical.equals("http://hl7.eu/fhir/ig/"+code()), 
            "canonical URL of "+canonical+" does not match the required canonical of http://fhir.eu/fhir/ig/"+code()+" [3]");          
      } else if ("ch".equals(realm())) {
        ok = check(res, canonical.equals("http://fhir.ch/ig/"+code()), 
            "canonical URL of "+canonical+" does not match the required canonical of http://fhir.ch/ig/"+code()+" [3]");          
      } else if ("be".equals(realm())) {
        ok = check(res, canonical.equals("http://hl7belgium.org/profiles/fhir/"+code()) || canonical.equals("https://www.ehealth.fgov.be/standards/fhir/"+code()), 
            "canonical URL of "+canonical+" does not match the required canonical of http://hl7belgium.org/profiles/fhir/"+code()+" or https://www.ehealth.fgov.be/standards/fhir/"+code()+" [4]");          
      } else if ("cl".equals(realm())) {
        ok = check(res, canonical.equals("https://hl7chile.cl/fhir/ig/"+code()), 
            "canonical URL of "+canonical+" does not match the required canonical of https://hl7chile.cl/fhir/ig/"+code()+" [4a]");          
      } else {
        // special case weirdity
        if ("uv".equals(realm()) && Utilities.existsInList(code(), "smart-app-launch", "extensions")) {
          ok = check(res, canonical.equals("http://hl7.org/fhir/"+code()), "canonical URL of "+canonical+" does not match the required canonical of http://hl7.org/fhir/smart-app-launch"+" [5]");
        } else if ("xprod".equals(family())) {
          ok = check(res, canonical.equals("http://hl7.org/xprod/ig/"+realm()+"/"+code()), "canonical URL of "+canonical+" does not match the required canonical of http://hl7.org/fhir/"+realm()+"/"+code()+" [5a]");
        } else if ("cda".equals(family())) {
          ok = check(res, canonical.equals("http://hl7.org/cda/stds/"+code()), "canonical URL of "+canonical+" does not match the required canonical of http://hl7.org/cda/"+code()+" [5b]");
        } else if ("eu".equals(family())) {
          ok = check(res, canonical.equals("http://hl7.eu/fhir/ig/"+code()), "canonical URL of "+canonical+" does not match the required canonical of http://hl7.eu/fhir/ig/"+code()+" [5c]");
        } else {
          ok = check(res, canonical.equals("http://hl7.org/fhir/"+realm()+"/"+code()) || canonical.equals("http://hl7.org/fhir/smart-app-launch"), "canonical URL of "+canonical+" does not match the required canonical of http://hl7.org/fhir/"+realm()+"/"+code()+" (5d)");
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
    public String family() {
      return parts[1];
    }  
    
    @Override
    public String getDestination(String rootFolder) throws IOException {
      if (Utilities.existsInList(realm(), "dk", "ch", "be", "cl")) {
        return Utilities.path(rootFolder, code());
      } else if ("eu".equals(parts[1])) {
        return Utilities.path(rootFolder, code());
      } else {
        // special case weirdity
        if ("uv".equals(realm()) && Utilities.existsInList(code(), "smart-app-launch", "extensions")) {
          return Utilities.path(rootFolder, code());
        } else  if ("cda".equals(family())) {
          return Utilities.path(rootFolder, code());
        } else {
          return Utilities.path(rootFolder, realm(), code());
        }
      }
    }
    
    public String desc() {
      return "Web Layout using the HL7 defined rules";
    }

  }

  public static class HL7ChNamingRulesProvider extends DefaultNamingRulesProvider {
    @Override
    public boolean checkNpmId(List<ValidationMessage> res) {
      return check(res, parts.length == 4 && "ch".equals(parts[0]) && "fhir".equals(parts[1]) && "ig".equals(parts[2]), 
          "Package Id '"+id+"' is not valid:  must have 4 parts (ch.fhir.ig.[code]");
    }

    @Override
    public boolean checkCanonicalAndUrl(List<ValidationMessage> res, String canonical, String url) {
      boolean ok = true;
      if ("ch".equals(realm())) {
        ok = check(res, canonical.equals("http://fhir.ch/ig/"+code()), 
            "canonical URL of "+canonical+" does not match the required canonical of http://fhir.ch/ig/"+code()+" [6]");          
      } 
      return check(res, canonical.startsWith(url), "Proposed canonical '"+canonical+"' does not match the web site URL '"+url+"'") && ok;
    }
        
    public String realm() {
      return parts[0];
    }
    
    public String code() {
      return parts[3];
    }  
    
    @Override
    public String getDestination(String rootFolder) throws IOException {
      if ("ch".equals(realm())) {
        return Utilities.path(rootFolder, code());
      } 
      return super.getDestination(rootFolder);
    }
    public String desc() {
      return "Web Layout using the HL7-CH defined rules";
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
          "canonical URL of "+canonical+" does not match the required canonical of https://profiles.ihe.net/"+domain()+"/"+profile()+" [7]");          
      return check(res, canonical.startsWith(url), "Proposed canonical '"+canonical+"' does not match the web site URL '"+url+"'") && ok;
    }


    public String getDestination(String rootFolder) throws IOException {
      // there's a case problem here: if IHI lowercases package names, but not canonicals or folder URLs, then the case of this will be wrong
      // and can't upper case algorithmically. May have to pick up case from canonical?
      return Utilities.path(rootFolder, domain().toUpperCase(), profile().toUpperCase());      
    }
    public String desc() {
      return "Web Layout using the IHE defined rules";
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
      if (org().equals("acme")) {
        boolean ok = check(res, canonical != null && canonical.startsWith("http://fhir.org/guides/"+org()+"/") && (canonical.equalsIgnoreCase("http://fhir.org/guides/"+org()+"/"+code())), 
            "canonical URL of "+canonical+" does not match the required canonical of http://fhir.org/guides/"+org()+"/"+code()+" [8]") &&
            check(res, canonical.startsWith(url), "Proposed canonical '"+canonical+"' does not match the web site URL '"+url+"'");
        if (ok) {
          parts[parts.length - 1] = tail(canonical);
        }
        return ok;
      } else {
        return check(res, canonical != null && (canonical.equals("http://fhir.org/guides/"+org()+"/"+code())), 
          "canonical URL of "+canonical+" does not match the required canonical of http://fhir.org/guides/"+org()+"/"+code()+" [9]") &&
          check(res, canonical.startsWith(url), "Proposed canonical '"+canonical+"' does not match the web site URL '"+url+"'");
      }
    }
    
    private String tail(String canonical) {
      return canonical.substring(canonical.lastIndexOf("/")+1);
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
    
    public String desc() {
      return "Web Layout using the fhir.org defined rules";
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
    public String desc() {
      return "Web Layout using the CQL defined rules";
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

    public String desc() {
      return "Web Layout using the THO defined rules";
    }

  }

  public static WebSiteLayoutRulesProvider recogniseNpmId(String id, String[] p, JsonObject setup) {
    DefaultNamingRulesProvider res = new DefaultNamingRulesProvider();
    if (id.equals("hl7.terminology")) {
      res = new HL7TerminologyNamingRulesProvider();
    } else if (id.equals("hl7.cql")) {
      res = new CQLNamingRulesProvider();
    } else if (id.startsWith("fhir.")) {
      res = new FHIROrgNamingRulesProvider();
    } else if (id.startsWith("hl7.")) {
      res = new HL7NamingRulesProvider();
    } else if ((id.startsWith("ch.fhir"))) {
      res = new HL7ChNamingRulesProvider();
    } else if (id.startsWith("ihe.")) {
      res = new IHENamingRulesProvider();
    } else if (setup != null) {
      if (setup.has("id") && setup.has("canonical")) {
        res = new ScriptedNamingRulesProvider(setup.asString("id"), setup.asString("canonical"));
      } else {
        System.out.println("Found layout in publish-setup.json, but it doesn't contain both id & canonical rules");
      }
    }
    System.out.println(res.desc());
    res.id = id;
    res.parts = p;
    return res;
  }

  
}
