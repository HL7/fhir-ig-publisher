package org.hl7.fhir.igtools.publisher;

/*-
 * #%L
 * org.hl7.fhir.publisher.core
 * %%
 * Copyright (C) 2014 - 2019 Health Level 7
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

 
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.hl7.fhir.convertors.VersionConvertorConstants;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.templates.Template;
import org.hl7.fhir.r5.conformance.ProfileUtilities;
import org.hl7.fhir.r5.conformance.ProfileUtilities.ProfileKnowledgeProvider;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.ParserBase;
import org.hl7.fhir.r5.elementmodel.Property;
import org.hl7.fhir.r5.formats.FormatUtilities;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionBindingComponent;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class IGKnowledgeProvider implements ProfileKnowledgeProvider, ParserBase.ILinkResolver {

  private IWorkerContext context;
  private SpecMapManager specPaths;
  private Set<String> msgs = new HashSet<String>();
  private String pathToSpec;
  private String canonical;
  private List<ValidationMessage> errors;
  private JsonObject defaultConfig;
  private JsonObject resourceConfig;
  private String pathPattern;
  private boolean autoPath = false;
  private boolean noXhtml;
  private Template template;
  private List<String> listedURLExemptions;
  
  public IGKnowledgeProvider(IWorkerContext context, String pathToSpec, String canonical, JsonObject igs, List<ValidationMessage> errors, boolean noXhtml, Template template, List<String> listedURLExemptions) throws Exception {
    super();
    this.context = context;
    this.pathToSpec = pathToSpec;
    if (this.pathToSpec.endsWith("/"))
      this.pathToSpec = this.pathToSpec.substring(0, this.pathToSpec.length()-1);
    this.errors = errors;
    this.noXhtml = noXhtml;
    this.canonical = canonical;
    this.template = template;
    this.listedURLExemptions = listedURLExemptions;
    loadPaths(igs);
  }
  
  private void loadPaths(JsonObject igs) throws Exception {
    JsonElement e = igs.get("path-pattern");
    if (e != null)
      pathPattern = e.getAsString(); 
    defaultConfig = igs.getAsJsonObject("defaults");
    resourceConfig = igs.getAsJsonObject("resources");
    if (resourceConfig != null) {
      for (Entry<String, JsonElement> pp : resourceConfig.entrySet()) {
        if (pp.getKey().equals("*")) {
          autoPath = true;
        } else if (!pp.getKey().startsWith("_")) {
          String s = pp.getKey();
          if (!s.contains("/"))
            throw new Exception("Bad Resource Identity - should have the format [Type]/[id]:" + s);
          String type = s.substring(0,  s.indexOf("/"));
          String id = s.substring(s.indexOf("/")+1); 
          if (!context.hasResource(StructureDefinition.class, "http://hl7.org/fhir/StructureDefinition/"+type) && !(context.hasResource(StructureDefinition.class , "http://hl7.org/fhir/StructureDefinition/Conformance") && type.equals("CapabilityStatement")))
            throw new Exception("Bad Resource Identity - should have the format [Type]/[id] where Type is a valid resource type:" + s);
          if (!id.matches(FormatUtilities.ID_REGEX))
            throw new Exception("Bad Resource Identity - should have the format [Type]/[id] where id is a valid FHIR id type:" + s);

          if (!(pp.getValue() instanceof JsonObject))
            throw new Exception("Unexpected type in resource list - must be an object");
          JsonObject o = (JsonObject) pp.getValue();
          JsonElement p = o.get("base");
          //        if (p == null)
          //          throw new Exception("You must provide a base on each path in the json file");
          if (p != null && !(p instanceof JsonPrimitive) && !((JsonPrimitive) p).isString())
            throw new Exception("Unexpected type in paths - base must be a string");
          p = o.get("defns");
          if (p != null && !(p instanceof JsonPrimitive) && !((JsonPrimitive) p).isString())
            throw new Exception("Unexpected type in paths - defns must be a string");
          p = o.get("source");
          if (p != null && !(p instanceof JsonPrimitive) && !((JsonPrimitive) p).isString())
            throw new Exception("Unexpected type in paths - source must be a string");
        }
      }
    } else if (template == null)
      throw new Exception("No \"resources\" entry found in json file (see http://wiki.hl7.org/index.php?title=IG_Publisher_Documentation#Control_file)");
  }
  
  private boolean hasBoolean(JsonObject obj, String code) {
    JsonElement e = obj.get(code);
    return e != null && e instanceof JsonPrimitive && ((JsonPrimitive) e).isBoolean();
  }

  private boolean getBoolean(JsonObject obj, String code) {
    JsonElement e = obj.get(code);
    return e != null && e instanceof JsonPrimitive && ((JsonPrimitive) e).getAsBoolean();
  }

  private boolean hasString(JsonObject obj, String code) {
    JsonElement e = obj.get(code);
    return e != null && (e instanceof JsonPrimitive && ((JsonPrimitive) e).isString()) || e instanceof JsonNull;
  }

  private String getString(JsonObject obj, String code) {
    JsonElement e = obj.get(code);
    if (e instanceof JsonNull)
      return null;
    else 
      return ((JsonPrimitive) e).getAsString();
  }

  public String doReplacements(String s, FetchedResource r, Map<String, String> vars, String format) throws FHIRException {
    if (Utilities.noString(s))
      return s;
    if (r.getId()== null) {
      throw new FHIRException("Error doing replacements - no id defined in resource: " + (r.getTitle()== null ? "NO TITLE EITHER" : r.getTitle()));
    }
    s = s.replace("{{[title]}}", r.getTitle() == null ? "?title?" : r.getTitle());
    s = s.replace("{{[name]}}", r.getId()+(format==null? "": "-"+format)+"-html");
    s = s.replace("{{[id]}}", r.getId());
    if (format!=null)
      s = s.replace("{{[fmt]}}", format);
    s = s.replace("{{[type]}}", r.fhirType());
    s = s.replace("{{[uid]}}", r.fhirType()+"="+r.getId());
    if (vars != null) {
      for (String n : vars.keySet()) {
        String v = vars.get(n);
        if (v == null) {
          v = "";
        }
        s = s == null ? "" : s.replace("{{["+n+"]}}", v);
      }
    }
    return s;
  }

  public String doReplacements(String s, FetchedResource r, Resource res, Map<String, String> vars, String format, String prefixForContained) throws FHIRException {
    if (Utilities.noString(s))
      return s;
    if (r.getId()== null) {
      throw new FHIRException("Error doing replacements - no id defined in resource: " + (r.getTitle()== null ? "NO TITLE EITHER" : r.getTitle()));
    }
    s = s.replace("{{[title]}}", r.getTitle() == null ? "?title?" : r.getTitle());
    s = s.replace("{{[name]}}", res.getId()+(format==null? "": "-"+format)+"-html");
    s = s.replace("{{[id]}}", prefixForContained+res.getId());
    if (format!=null)
      s = s.replace("{{[fmt]}}", format);
    s = s.replace("{{[type]}}", res.fhirType());
    s = s.replace("{{[uid]}}", res.fhirType()+"="+prefixForContained+res.getId());
    if (vars != null) {
      for (String n : vars.keySet()) {
        String v = vars.get(n);
        if (v == null) {
          v = "";
        }
        s = s == null ? "" : s.replace("{{["+n+"]}}", v);
      }
    }
    return s;
  }

  public String doReplacements(String s, Resource r, Map<String, String> vars, String format) {
    if (Utilities.noString(s))
      return s;
    s = s.replace("{{[title]}}", "?title?");
    s = s.replace("{{[name]}}", r.getId()+(format==null? "": "-"+format)+"-html");
    s = s.replace("{{[id]}}", r.getId());
    if (format!=null)
      s = s.replace("{{[fmt]}}", format);
//    s = s.replace("{{[type]}}", r.fhirType());
//    s = s.replace("{{[uid]}}", r.fhirType()+"="+r.getId());
    if (vars != null) {
      for (String n : vars.keySet())
        s = s.replace("{{["+n+"]}}", vars.get(n));
    }
    return s;
  }

  public boolean wantGen(FetchedResource r, String code) {
    if (r.getConfig() != null && hasBoolean(r.getConfig(), code))
      return getBoolean(r.getConfig(), code);
    JsonObject cfg = null;
    if (defaultConfig != null) {
      cfg = defaultConfig.getAsJsonObject(r.fhirType());
      if (cfg != null && hasBoolean(cfg, code)) {
        return getBoolean(cfg, code);
      }
      cfg = defaultConfig.getAsJsonObject("Any");
      if (cfg != null && hasBoolean(cfg, code)) {
        return getBoolean(cfg, code);
      }
    }
    return true;
  }

  public String getPropertyContained(FetchedResource r, String propertyName, Resource contained) {
    if (contained == null && r.getConfig() != null && hasString(r.getConfig(), propertyName)) {
      return getString(r.getConfig(), propertyName);
    }
    if (defaultConfig != null && contained != null) {
      JsonObject cfg = null;
      if (cfg==null && "StructureDefinition".equals(contained.fhirType())) {
        cfg = defaultConfig.getAsJsonObject(contained.fhirType()+":"+getSDType(contained));
        if (cfg != null && hasString(cfg, propertyName)) {
          return getString(cfg, propertyName);        
        }
      }
      cfg = defaultConfig.getAsJsonObject(contained.fhirType());
      if (cfg != null && hasString(cfg, propertyName)) {
        return getString(cfg, propertyName);
      }
      cfg = defaultConfig.getAsJsonObject("Any");
      if (cfg != null && hasString(cfg, propertyName)) {
        return getString(cfg, propertyName);
      }
    }
    return null;
  }

  public String getProperty(FetchedResource r, String propertyName) {
    if (r.getConfig() != null && hasString(r.getConfig(), propertyName)) {
      return getString(r.getConfig(), propertyName);
    }
    if (defaultConfig != null) {
      JsonObject cfg = null;
      if (r.isExample()) {
        cfg = defaultConfig.getAsJsonObject("example");
      }
      if (cfg==null && "StructureDefinition".equals(r.fhirType())) {
        cfg = defaultConfig.getAsJsonObject(r.fhirType()+":"+getSDType(r));
        if (cfg != null && hasString(cfg, propertyName)) {
          return getString(cfg, propertyName);        
        }
      }
      cfg = defaultConfig.getAsJsonObject(r.fhirType());
  	  if (cfg != null && hasString(cfg, propertyName)) {
  	    return getString(cfg, propertyName);
  	  }
      cfg = defaultConfig.getAsJsonObject("Any");
      if (cfg != null && hasString(cfg, propertyName)) {
        return getString(cfg, propertyName);
      }
    }
    return null;
  }

  public static String getSDType(Resource r) {
    StructureDefinition sd = (StructureDefinition) r;
    if ("Extension".equals(sd.getType())) {
      return "extension";
    }
//    if (sd.getKind() == StructureDefinitionKind.LOGICAL)
    return sd.getKind().toCode() + (sd.getAbstract() ? ":abstract" : "");    
  }
  
  public static String getSDType(FetchedResource r) {
    if ("Extension".equals(r.getElement().getChildValue("type"))) {
      return "extension";
    }
//    if (sd.getKind() == StructureDefinitionKind.LOGICAL)
    return r.getElement().getChildValue("kind") + ("true".equals(r.getElement().getChildValue("abstract")) ? ":abstract" : "");
  }

  public boolean hasProperty(FetchedResource r, String propertyName, Resource contained) {
    if (r.getConfig() != null && hasString(r.getConfig(), propertyName))
      return true;
    if (defaultConfig != null && contained != null) {
      JsonObject cfg = defaultConfig.getAsJsonObject(contained.fhirType());
      if (cfg != null && hasString(cfg, propertyName))
        return true;
    }
    if (defaultConfig != null) {
      JsonObject cfg = defaultConfig.getAsJsonObject(r.fhirType());
      if (cfg != null && hasString(cfg, propertyName))
        return true;
      cfg = defaultConfig.getAsJsonObject("Any");
      if (cfg != null && hasString(cfg, propertyName))
        return true;
    }
    return false;    
  }
  
  public boolean hasProperty(FetchedResource r, String propertyName) {
    if (r.getConfig() != null && hasString(r.getConfig(), propertyName))
      return true;
    if (defaultConfig != null) {
      JsonObject cfg = defaultConfig.getAsJsonObject(r.fhirType());
      if (cfg != null && hasString(cfg, propertyName))
        return true;
      cfg = defaultConfig.getAsJsonObject("Any");
      if (cfg != null && hasString(cfg, propertyName))
        return true;
    }
    return false;
  }

  public String getDefinitionsName(FetchedResource r) {	
    return doReplacements(getProperty(r, "defns"), r, null, null);
  }

  // base specification only, only the old json style
  public void loadSpecPaths(SpecMapManager paths) throws Exception {
    this.specPaths = paths;
    for (CanonicalResource bc : context.allConformanceResources()) {
      String s = getOverride(bc.getUrl());
      if (s == null) {
        s = paths.getPath(bc.getUrl(), bc.getMeta().getSource());
      }
      if (s == null && bc instanceof CodeSystem) { // work around for an R2 issue) 
        CodeSystem cs = (CodeSystem) bc;
        s = paths.getPath(cs.getValueSet(), null);
      }
      if (s != null) {
        bc.setUserData("path", specPath(s));
      // special cases
      } else if (bc.hasUrl() && bc.getUrl().equals("http://hl7.org/fhir/ValueSet/security-role-type")) {
        bc.setUserData("path", specPath("valueset-security-role-type.html"));
      } else if (bc.hasUrl() && bc.getUrl().equals("http://hl7.org/fhir/ValueSet/object-lifecycle-events")) {
        bc.setUserData("path", specPath("valueset-object-lifecycle-events.html"));
      } else if (bc.hasUrl() && bc.getUrl().equals("http://hl7.org/fhir/ValueSet/performer-function")) {
        bc.setUserData("path", specPath("valueset-performer-function.html"));
      } else if (bc.hasUrl() && bc.getUrl().equals("http://hl7.org/fhir/ValueSet/written-language")) {
        bc.setUserData("path", specPath("valueset-written-language.html"));
        
//      else
//        System.out.println("No path for "+bc.getUrl());
      }
    }    
  }

  private String getOverride(String url) {
    if ("http://hl7.org/fhir/StructureDefinition/Reference".equals(url))
      return "references.html#Reference";
    if ("http://hl7.org/fhir/StructureDefinition/DataRequirement".equals(url))
     return "metadatatypes.html#DataRequirement";
    if ("http://hl7.org/fhir/StructureDefinition/ContactDetail".equals(url))
      return "metadatatypes.html#ContactDetail";
    if ("http://hl7.org/fhir/StructureDefinition/Contributor".equals(url))
      return "metadatatypes.html#Contributor";
    if ("http://hl7.org/fhir/StructureDefinition/ParameterDefinition".equals(url))
      return "metadatatypes.html#ParameterDefinition";
    if ("http://hl7.org/fhir/StructureDefinition/RelatedArtifact".equals(url))
      return "metadatatypes.html#RelatedArtifact";
    if ("http://hl7.org/fhir/StructureDefinition/TriggerDefinition".equals(url))
      return "metadatatypes.html#TriggerDefinition";
    if ("http://hl7.org/fhir/StructureDefinition/UsageContext".equals(url))
      return "metadatatypes.html#UsageContext";
    if ("http://hl7.org/fhir/StructureDefinition/Extension".equals(url))
      return "extensibility.html#Extension";
    return null;
  }
  
  public String getSourceFor(String ref) {
    if (resourceConfig == null)
      return null;
    JsonObject o = resourceConfig.getAsJsonObject(ref);
    if (o == null)
      return null;
    JsonElement e = o.get("source");
    if (e == null)
      return null;
    return e.getAsString();
  }

  public void findConfiguration(FetchedFile f, FetchedResource r) {
    if (template != null) {
      JsonObject cfg = null;
      if (r.isExample()) {
        cfg = defaultConfig.getAsJsonObject("example");
      }        
      if (cfg == null && r.fhirType().equals("StructureDefinition")) {
        cfg = defaultConfig.getAsJsonObject(r.fhirType()+":"+getSDType(r));
      }
      if (cfg == null)
        cfg = template.getConfig(r.fhirType(), r.getId());        
      r.setConfig(cfg);
    }
    if (r.getConfig() == null && resourceConfig != null) {
      JsonObject e = resourceConfig.getAsJsonObject(r.fhirType()+"/"+r.getId());
      if (e != null)
        r.setConfig(e);
    }
  }
  
  public void checkForPath(FetchedFile f, FetchedResource r, CanonicalResource bc, boolean inner) throws FHIRException {
    if (!bc.hasUrl())
      error(f, bc.fhirType()+".url", "Resource has no url: "+bc.getId(), I18nConstants.RESOURCE_ID_NO_URL);
    else if (bc.getUrl().startsWith(canonical) && !bc.getUrl().endsWith("/"+bc.getId()) && !listedURLExemptions.contains(bc.getUrl()))
      error(f, bc.fhirType()+".url","Resource id/url mismatch: "+bc.getId()+"/"+bc.getUrl(), I18nConstants.RESOURCE_ID_MISMATCH);
    if (!inner && !r.getId().equals(bc.getId()))
      error(f, bc.fhirType()+".id", "Resource id/loaded id mismatch: "+r.getId()+"/"+bc.getUrl(), I18nConstants.RESOURCE_ID_LOADED_MISMATCH);
    if (r.getConfig() == null)
      findConfiguration(f, r);
    JsonObject e = r.getConfig();
    bc.setUserData("config", e);
    String base = getProperty(r,  "base");
    if (base != null) 
      bc.setUserData("path", doReplacements(base, r, null, null));
    else if (pathPattern != null)
      bc.setUserData("path", pathPattern.replace("[type]", r.fhirType()).replace("[id]", r.getId()));
    else
      bc.setUserData("path", r.fhirType()+"/"+r.getId()+".html");
    for (Resource cont : bc.getContained()) {
      if (base != null) 
        cont.setUserData("path", doReplacements(base, r, cont, null, null, bc.getId()+"_"));
      else if (pathPattern != null)
        cont.setUserData("path", pathPattern.replace("[type]", r.fhirType()).replace("[id]", bc.getId()+"_"+cont.getId()));
      else
        cont.setUserData("path", r.fhirType()+"/"+bc.getId()+"_"+r.getId()+".html");
    }
  }

  private void error(FetchedFile f, String path, String msg, String msgId) {
    if (!msgs.contains(msg)) {
      msgs.add(msg);
      f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.INVARIANT, path, msg, IssueSeverity.ERROR).setMessageId(msgId));
    }
  }

  private void hint(String location, String msg) {
    if (!msgs.contains(msg)) {
      msgs.add(msg);
      errors.add(new ValidationMessage(Source.Publisher, IssueType.INVARIANT, location, msg, IssueSeverity.INFORMATION));
    }
  }

  private String makeCanonical(String ref) {
    return Utilities.pathURL(canonical, ref);
  }

  private void brokenLinkWarning(String location, String ref) {
    String s = "The reference "+ref+" could not be resolved";
    if (!msgs.contains(s)) {
      msgs.add(s);
      errors.add(new ValidationMessage(Source.Publisher, IssueType.INVARIANT, pathToFhirPath(location), s, IssueSeverity.ERROR));
    }
  }

  private String pathToFhirPath(String path) {
    return path.replace("[x]", "");
  }
  
  public String specPath(String path) {
    if (Utilities.isAbsoluteUrl(path)) {
      return path;
    } else {
      assert pathToSpec != null;
      return Utilities.pathURL(pathToSpec, path);
    }
  }

  public String specPath() {
    return pathToSpec;
  }

  // ---- overrides ---------------------------------------------------------------------------
  
  @Override
  public boolean isDatatype(String name) {
    StructureDefinition sd = context.fetchTypeDefinition(name);
    return sd != null && (sd.getKind() == StructureDefinitionKind.PRIMITIVETYPE || sd.getKind() == StructureDefinitionKind.COMPLEXTYPE) && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION;
  }  

  @Override
  public boolean isResource(String name) {
    StructureDefinition sd = context.fetchTypeDefinition(name);
    return sd != null && (sd.getKind() == StructureDefinitionKind.RESOURCE) && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION;
  }

  public boolean isLogical(String name) {
    StructureDefinition sd = context.fetchTypeDefinition(name);
    return sd != null && (sd.getKind() == StructureDefinitionKind.LOGICAL) && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION;
  }

  @Override
  public boolean hasLinkFor(String name) {
    return isDatatype(name) || isResource(name) || isLogical(name);
  }

  @Override
  public String getLinkFor(String corepath, String name) {
    if (noXhtml && name.equals("xhtml"))
      return null;
    StructureDefinition sd = context.fetchResource(StructureDefinition.class, ProfileUtilities.sdNs(name, null));
    if (sd != null && sd.hasUserData("path"))
        return sd.getUserString("path");
    brokenLinkWarning(corepath, name);
    return name+".html";
  }

  @Override
  public BindingResolution resolveBinding(StructureDefinition profile, ElementDefinitionBindingComponent binding, String path) {
    if (!binding.hasValueSet()) {
      BindingResolution br = new BindingResolution();
      br.url = specPath("terminologies.html#unbound");
      br.display = "(unbound)";
      return br;
    } else {
      return resolveBinding(profile, binding.getValueSet(), path);
    }
  }
  
  public BindingResolution resolveBinding(StructureDefinition profile, String ref, String path) {
    BindingResolution br = new BindingResolution();
    if (ref.startsWith("http://hl7.org/fhir/ValueSet/v3-")) {
      br.url = specPath("v3/"+ref.substring(32)+"/vs.html");
      br.display = ref.substring(32);
    } else if (ref.startsWith("ValueSet/")) {
      ValueSet vs = context.fetchResource(ValueSet.class, makeCanonical(ref));
      if (vs == null) {
        br.url = ref;  
        if (ref.equals("http://tools.ietf.org/html/bcp47"))
          br.display = "IETF BCP-47";
        else if (ref.equals("http://www.rfc-editor.org/bcp/bcp13.txt"))
          br.display = "IETF BCP-13";
        else if (ref.equals("http://www.ncbi.nlm.nih.gov/nuccore?db=nuccore"))
          br.display = "NucCore";
        else if (ref.equals("https://rtmms.nist.gov/rtmms/index.htm#!rosetta"))
          br.display = "Rosetta";
        else if (ref.equals("http://www.iso.org/iso/country_codes.htm"))
          br.display = "ISO Country Codes";
        else {
          br.url = ref.substring(9)+".html"; // broken link, 
          br.display = ref.substring(9);
          brokenLinkWarning(path, ref);
        }
      } else {
        br.url = vs.getUserString("path");
        br.display = vs.getName(); 
      }
    } else { 
      if (ref.startsWith("http://hl7.org/fhir/ValueSet/")) {
        ValueSet vs = context.fetchResource(ValueSet.class, ref);
        if (vs != null) { 
          br.url = vs.getUserString("path");
          br.display = vs.getName(); 
        } else {
          String vsr = VersionConvertorConstants.vsToRef(ref);
          if (vsr != null) {
            br.display = ref.substring(29);
            br.url = vsr;
          } else {
            br.display = ref.substring(29);
            br.url = ref.substring(29)+".html";
            brokenLinkWarning(path, ref);
          }
        }
      } else if (ref.startsWith("http://hl7.org/fhir/ValueSet/v3-")) {
        br.url = specPath("v3/"+ref.substring(26)+"/index.html"); 
        br.display = ref.substring(26);
      } else if (ref.startsWith("http://hl7.org/fhir/ValueSet/v2-")) {
        br.url = specPath("v2/"+ref.substring(26)+"/index.html"); 
        br.display = ref.substring(26);
      } else if (ref.startsWith("http://loinc.org/vs/")) {
        String code = tail(ref);
        if (code.startsWith("LL")) {
          br.url = "https://r.details.loinc.org/AnswerList/"+code+".html";
          br.display = "LOINC Answer List "+code;
        } else {
          br.url = "https://r.details.loinc.org/LOINC/"+code+".html";
          br.display = "LOINC "+code;
        }
      } else {
        ValueSet vs = context.fetchResource(ValueSet.class, ref);
        if (vs != null) {
          if (ref.contains("|")) {
            br.url = vs.getUserString("versionpath");
            if (br.url == null) {
              br.url = vs.getUserString("path");
            }
            br.display = vs.getName() + " (" + vs.getVersion() + ")"; 
          } else if (vs != null && vs.hasUserData("path")) {
            br.url = vs.getUserString("path");  
            br.display = vs.present();
          } else {
            br.url = vs.getUserString("path");
            br.display = vs.getName(); 
          }
        } else if (ref.startsWith("http://cts.nlm.nih.gov/fhir/ValueSet/")) {          
          String oid = ref.substring("http://cts.nlm.nih.gov/fhir/ValueSet/".length());
          br.url = "https://vsac.nlm.nih.gov/valueset/"+oid+"/expansion";  
          br.display = "VSAC "+oid;
        } else if (Utilities.isAbsoluteUrl(ref) && (!ref.startsWith("http://hl7.org") || !ref.startsWith("http://terminology.hl7.org"))) {
          br.url = Utilities.encodeUri(ref);  
          br.display = ref;
        } else if (vs == null) {
          br.url = ref+".html"; // broken link, 
          br.display = ref;
          brokenLinkWarning(path, ref);
        }
      }
    }
    return br;
  }

  private String tail(String ref) {
    if  (ref.contains("/"))
      return ref.substring(ref.lastIndexOf("/")+1);
    else
      return ref;
  }

  @Override
  public String getLinkForProfile(StructureDefinition profile, String url) {
    StructureDefinition sd = context.fetchResource(StructureDefinition.class, url);
    if (noXhtml && sd != null && sd.getType().equals("xhtml"))
      return null;
    if (sd != null && sd.hasUserData("path"))
      return sd.getUserString("path")+"|"+sd.getName();
    brokenLinkWarning("?pkp-1?", url);
    return "unknown.html|?pkp-2?";
  }


  @Override
  public String resolveType(String type) {
    return getLinkFor("", type);
  }

  @Override
  public String resolveProperty(Property property) {
    String path = property.getDefinition().getPath();
    return property.getStructure().getUserString("path")+"#"+path;
  }

  @Override
  public String resolvePage(String name) {
    return specPath(name);
  }

  @Override
  public boolean prependLinks() {
    return false;
  }

  public String getCanonical() {
    return canonical;
  }

  public String getLinkFor(FetchedResource r, boolean replace) {
    String base = getProperty(r, "base");
    if (base!=null) {
      if (replace) {
        base = base.replace("{{[id]}}", r.getId());
        base = base.replace("{{[type]}}", r.fhirType());
      }
      return base;
    }
    return r.fhirType()+"-"+r.getId()+".html";
  }

  public String getLinkFor(FetchedResource r, boolean replace, Resource contained) {
    String base = getProperty(r, "base");
    if (base!=null) {
      if (replace) {
        base = base.replace("{{[id]}}", r.getId());
        base = base.replace("{{[type]}}", r.fhirType());
      }
      base = base.replace(".html", "_"+contained.getId()+".html");
      return base;
    }
    return r.fhirType()+"-"+r.getId()+"_"+contained.getId()+".html";
  }

  public IWorkerContext getContext() {
    return context;
  }

  public boolean isAutoPath() {
    return autoPath;
  }

  public BindingResolution resolveActualUrl(String uri) {
    BindingResolution br = new BindingResolution();
    if (uri != null) {
      if (uri.startsWith("http://loinc.org/vs/")) {
        String code = tail(uri);
        if (code.startsWith("LL")) {
          br.url = "https://r.details.loinc.org/AnswerList/"+code+".html";
          br.display = "LOINC Answer List "+code;
        } else {
          br.url = "https://r.details.loinc.org/LOINC/"+code+".html";
          br.display = "LOINC "+code;
        }
      } else if (uri.startsWith("urn:")) {
        br.url = null;
        br.display = uri;
      } else {
        br.url = uri;
        br.display = uri;
      }
    }
    return br;
  }
  public void setAutoPath(boolean autoPath) {
    this.autoPath = autoPath;
  }
  @Override
  public String getLinkForUrl(String corePath, String s) {
    return context.getLinkForUrl(corePath, s);
  }

  
}
