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

 
import java.util.*;

import org.hl7.fhir.convertors.VersionConvertorConstants;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.modules.IPublisherModule;
import org.hl7.fhir.igtools.templates.Template;
import org.hl7.fhir.r5.conformance.profile.BindingResolution;
import org.hl7.fhir.r5.conformance.profile.ProfileKnowledgeProvider;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.context.ContextUtilities;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.ParserBase;
import org.hl7.fhir.r5.elementmodel.Property;
import org.hl7.fhir.r5.formats.FormatUtilities;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionBindingComponent;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities;
import org.hl7.fhir.r5.utils.xver.XVerExtensionManager;
import org.hl7.fhir.r5.utils.xver.XVerExtensionManagerFactory;
import org.hl7.fhir.utilities.LoincLinker;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonPrimitive;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.npm.PackageHacker;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;

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
  private Set<String> summaryRows = new HashSet<>();
  private String altCanonical;
  private XVerExtensionManager xver;
  private List<FetchedFile> files;
  private IPublisherModule module;
  private Map<String, List<ExtensionUsage>> coreExtensionMap;
  private ContextUtilities contextUtilities;
  
  public IGKnowledgeProvider(IWorkerContext context, String pathToSpec, String canonical, JsonObject igs, List<ValidationMessage> errors, boolean noXhtml, Template template, List<String> listedURLExemptions, String altCanonical, List<FetchedFile> files, IPublisherModule module) throws Exception {
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
    this.altCanonical = altCanonical;
    this.files = files;
    if (igs != null) {
      loadPaths(igs);
    }
    this.xver = XVerExtensionManagerFactory.createExtensionManager(context);
    this.module = module;
    contextUtilities = new ContextUtilities(context);
  }
  
  private void loadPaths(JsonObject igs) throws Exception {
    JsonElement e = igs.get("path-pattern");
    if (e != null)
      pathPattern = e.asString(); 
    defaultConfig = igs.getJsonObject("defaults");
    resourceConfig = igs.getJsonObject("resources");
    if (resourceConfig != null) {
      for (JsonProperty pp : resourceConfig.getProperties()) {
        if (pp.getName().equals("*")) {
          autoPath = true;
        } else if (!pp.getName().startsWith("_")) {
          String s = pp.getName();
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
          if (p != null && !(p instanceof JsonPrimitive))
            throw new Exception("Unexpected type in paths - base must be a string");
          p = o.get("defns");
          if (p != null && !(p instanceof JsonPrimitive))
            throw new Exception("Unexpected type in paths - defns must be a string");
          p = o.get("source");
          if (p != null && !(p instanceof JsonPrimitive))
            throw new Exception("Unexpected type in paths - source must be a string");
        }
      }
    } else if (template == null)
      throw new Exception("No \"resources\" entry found in json file (see http://wiki.hl7.org/index.php?title=IG_Publisher_Documentation#Control_file)");
  }

  public String doReplacements(String s, FetchedResource r, Map<String, String> vars, String format) throws FHIRException {
    if (Utilities.noString(s))
      return s;
    if (r.getId()== null) {
      throw new FHIRException("Error doing replacements - no id defined in resource: " + (r.getTitle()== null ? "NO TITLE EITHER" : r.getTitle())+" from "+r.getNameForErrors());
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
    if (r.getConfig() != null && r.getConfig().hasBoolean(code))
      return module.approveFragment(r.getConfig().asBoolean(code), code);
    JsonObject cfg = null;
    if (defaultConfig != null) {
      cfg = defaultConfig.getJsonObject(r.fhirType());
      if (cfg != null && cfg.hasBoolean(code)) {
        return module.approveFragment(cfg.asBoolean(code), code);
      }
      cfg = defaultConfig.getJsonObject("Any");
      if (cfg != null && cfg.hasBoolean(code)) {
        return module.approveFragment(cfg.asBoolean(code), code);
      }
    }
    return module.approveFragment(true, code);
  }

  public String getPropertyContained(FetchedResource r, String propertyName, Resource contained) {
    if (contained == null && r.getConfig() != null && r.getConfig().hasString(propertyName)) {
      return r.getConfig().asString(propertyName);
    }
    if (defaultConfig != null && contained != null) {
      JsonObject cfg = null;
      if (cfg==null && "StructureDefinition".equals(contained.fhirType())) {
        cfg = defaultConfig.getJsonObject(contained.fhirType()+":"+getSDType(contained));
        if (cfg != null && cfg.hasString(propertyName)) {
          return cfg.asString(propertyName);        
        }
      }
      cfg = defaultConfig.getJsonObject(contained.fhirType());
      if (cfg != null && cfg.hasString(propertyName)) {
        return cfg.asString(propertyName);
      }
      cfg = defaultConfig.getJsonObject("Any");
      if (cfg != null && cfg.hasString(propertyName)) {
        return cfg.asString(propertyName);
      }
    }
    return null;
  }

  public String getProperty(FetchedResource r, String propertyName) {
    if (r.getConfig() != null && r.getConfig().hasString(propertyName)) {
      return r.getConfig().asString(propertyName);
    }
    if (defaultConfig != null) {
      JsonObject cfg = null;
      if (r.isExample()) {
        cfg = defaultConfig.getJsonObject("example");
      }
      if (cfg==null && "StructureDefinition".equals(r.fhirType())) {
        cfg = defaultConfig.getJsonObject(r.fhirType()+":"+getSDType(r));
        if (cfg != null && cfg.hasString(propertyName)) {
          return cfg.asString(propertyName);        
        }
      }
      cfg = defaultConfig.getJsonObject(r.fhirType());
  	  if (cfg != null && cfg.hasString(propertyName)) {
  	    return cfg.asString(propertyName);
  	  }
      cfg = defaultConfig.getJsonObject("Any");
      if (cfg != null && cfg.hasString(propertyName)) {
        return cfg.asString(propertyName);
      }
    }
    return null;
  }

  public static String getSDType(Resource r) {
    StructureDefinition sd = (StructureDefinition) r;
    if ("Extension".equals(sd.getType())) {
      return "extension";
    }
    if (sd.getKind() == StructureDefinitionKind.LOGICAL) {
      return "logical";
    }
    return sd.getKind().toCode() + (sd.getAbstract() ? ":abstract" : "");    
  }
  
  public static String getSDType(FetchedResource r) {
    if ("Extension".equals(r.getElement().getChildValue("type"))) {
      return "extension";
    }
//    if (sd.getKind() == StructureDefinitionKind.LOGICAL)
    if (r.getElement().getChildValue("kind").equals("resource") && r.getElement().getChildValue("derivation").equals("specialization"))
      return "resourcedefn";
    
    return r.getElement().getChildValue("kind") + ("true".equals(r.getElement().getChildValue("abstract")) ? ":abstract" : "");
  }

  public boolean hasProperty(FetchedResource r, String propertyName, Resource contained) {
    if (r.getConfig() != null && r.getConfig().hasString(propertyName))
      return true;
    if (defaultConfig != null && contained != null) {
      JsonObject cfg = defaultConfig.getJsonObject(contained.fhirType());
      if (cfg != null && cfg.hasString(propertyName))
        return true;
    }
    if (defaultConfig != null) {
      JsonObject cfg = defaultConfig.getJsonObject(r.fhirType());
      if (cfg != null && cfg.hasString(propertyName))
        return true;
      cfg = defaultConfig.getJsonObject("Any");
      if (cfg != null && cfg.hasString(propertyName))
        return true;
    }
    return false;    
  }
  
  public boolean hasProperty(FetchedResource r, String propertyName) {
    if (r.getConfig() != null && r.getConfig().hasString(propertyName))
      return true;
    if (defaultConfig != null) {
      JsonObject cfg = defaultConfig.getJsonObject(r.fhirType());
      if (cfg != null && cfg.hasString(propertyName))
        return true;
      cfg = defaultConfig.getJsonObject("Any");
      if (cfg != null && cfg.hasString(propertyName))
        return true;
    }
    return false;
  }

  public String getDefinitionsName(FetchedResource r) {	
    return doReplacements(getProperty(r, "defns"), r, null, null);
  }

  public String getDefinitionsName(Resource r) { 
    for (FetchedFile f : files) {
      for (FetchedResource t : f.getResources()) {
        if (t.getResource() == r) {
          return getDefinitionsName(t);
        }
      }
    }
    String guess = r.getWebPath();
    return guess.replace(".html", "-definitions.html");
  }
  
  // base specification only, only the old json style
  public void loadSpecPaths(SpecMapManager paths) throws Exception {
    this.specPaths = paths;
    for (CanonicalResource bc : context.fetchResourcesByType(CanonicalResource.class)) {
      String s = getOverride(bc.getUrl());
      if (s == null) {
        s = paths.getPath(bc.getUrl(), bc.getMeta().getSource(), bc.fhirType(), bc.getId());
      }
      if (s == null && bc instanceof CodeSystem) { // work around for an R2 issue) 
        CodeSystem cs = (CodeSystem) bc;
        s = paths.getPath(cs.getValueSet(), null, cs.fhirType(), cs.getId());
      }
      if (s != null) {
        bc.setWebPath(specPath(s));
      // special cases
      } else if (bc.hasUrl() && bc.getUrl().equals("http://hl7.org/fhir/ValueSet/security-role-type")) {
        bc.setWebPath(specPath("valueset-security-role-type.html"));
      } else if (bc.hasUrl() && bc.getUrl().equals("http://hl7.org/fhir/ValueSet/object-lifecycle-events")) {
        bc.setWebPath(specPath("valueset-object-lifecycle-events.html"));
      } else if (bc.hasUrl() && bc.getUrl().equals("http://hl7.org/fhir/ValueSet/performer-function")) {
        bc.setWebPath(specPath("valueset-performer-function.html"));
      } else if (bc.hasUrl() && bc.getUrl().equals("http://hl7.org/fhir/ValueSet/written-language")) {
        bc.setWebPath(specPath("valueset-written-language.html"));
        
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
    JsonObject o = resourceConfig.getJsonObject(ref);
    if (o == null)
      return null;
    JsonElement e = o.get("source");
    if (e == null)
      return null;
    return e.asString();
  }

  public void findConfiguration(FetchedFile f, FetchedResource r) {
    if (template != null) {
      JsonObject cfg = null;
      if (cfg == null && r.fhirType().equals("StructureDefinition")) {
        cfg = defaultConfig.getJsonObject(r.fhirType()+":"+getSDType(r));
      }
      if (cfg == null)
        cfg = template.getConfig(r.fhirType(), r.getId());        
      if (cfg == null && r.isExample()) {
        if (r.isCanonical(context))
          cfg = defaultConfig.getJsonObject("example:canonical");
        if (cfg == null)
          cfg = defaultConfig.getJsonObject("example");
      }        
      if (cfg == null && r.isCanonical(context)) {
        if (cfg == null) {
          cfg = defaultConfig.getJsonObject(r.fhirType()+":canonical");
        }
        if (cfg == null) {
          cfg = defaultConfig.getJsonObject("Any:canonical");
        }
      }
      r.setConfig(cfg);
    }
    if (r.getConfig() == null && resourceConfig != null) {
      JsonObject e = resourceConfig.getJsonObject(r.fhirType()+"/"+r.getId());
      if (e != null)
        r.setConfig(e);
    }
  }
  
  public void checkForPath(FetchedFile f, FetchedResource r, CanonicalResource bc, boolean inner) throws FHIRException {
    if (!bc.hasUrl())
      error(f, bc.fhirType()+".url", "Resource has no url: "+bc.getId(), PublisherMessageIds.RESOURCE_ID_NO_URL);
    else if ((bc.getUrl().startsWith(canonical) || (altCanonical != null && bc.getUrl().startsWith(altCanonical)))
        && !bc.getUrl().endsWith("/"+bc.getId()) && !listedURLExemptions.contains(bc.getUrl()))
      error(f, bc.fhirType()+".url","Resource id/url mismatch: "+bc.getId()+"/"+bc.getUrl(), PublisherMessageIds.RESOURCE_ID_MISMATCH);
    if (!inner && !r.getId().equals(bc.getId()))
      error(f, bc.fhirType()+".id", "Resource id/loaded id mismatch: "+r.getId()+"/"+bc.getUrl(), PublisherMessageIds.RESOURCE_ID_LOADED_MISMATCH);
    if (r.getConfig() == null)
      findConfiguration(f, r);
    JsonObject e = r.getConfig();
    bc.setUserData(UserDataNames.pub_resource_config, e);
    String base = getProperty(r,  "base");
    if (base != null) 
      bc.setWebPath(doReplacements(base, r, null, null));
    else if (pathPattern != null)
      bc.setWebPath(pathPattern.replace("[type]", r.fhirType()).replace("[id]", r.getId()));
    else
      bc.setWebPath(r.fhirType()+"/"+r.getId()+".html");
    r.getElement().setWebPath(bc.getWebPath());
    for (Resource cont : bc.getContained()) {
      if (base != null)  {
        cont.setWebPath(doReplacements(base, r, cont, null, null, bc.getId()+"_"));
      } else if (pathPattern != null) {
        cont.setWebPath(pathPattern.replace("[type]", r.fhirType()).replace("[id]", bc.getId()+"_"+cont.getId()));
      } else {
        cont.setWebPath(r.fhirType()+"/"+bc.getId()+"_"+r.getId()+".html");
      }
    }
  }

  public void checkForPath(FetchedFile f, FetchedResource r, Element res) throws FHIRException {
    if (r.getConfig() == null)
      findConfiguration(f, r);
    JsonObject e = r.getConfig();
    res.setUserData(UserDataNames.pub_resource_config, e);
    String base = getProperty(r,  "base");
    if (base != null) 
      res.setWebPath(doReplacements(base, r, null, null));
    else if (pathPattern != null)
      res.setWebPath(pathPattern.replace("[type]", r.fhirType()).replace("[id]", r.getId()));
    else
      res.setWebPath(r.fhirType()+"/"+r.getId()+".html");
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

  private void brokenLinkMessage(String location, String ref, boolean warning) {
    String s = "The reference "+ref+" could not be resolved";
    if (!msgs.contains(s)) {
      msgs.add(s);
      if (errors != null) {
        errors.add(new ValidationMessage(Source.Publisher, IssueType.INVARIANT, pathToFhirPath(location), s, warning ? IssueSeverity.WARNING : IssueSeverity.ERROR));
      }
    }
  }

  private String pathToFhirPath(String path) {
    return path.replace("[x]", "");
  }
  
  public String specPath(String path) {
    if (Utilities.isAbsoluteUrl(path)) {
      return PackageHacker.fixPackageUrl(path);
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
  public boolean isPrimitiveType(String name) {
    StructureDefinition sd = context.fetchTypeDefinition(name);
    return sd != null && (sd.getKind() == StructureDefinitionKind.PRIMITIVETYPE) && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION;
  }  

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
    if (sd != null && sd.hasWebPath())
        return sd.getWebPath();
    sd = contextUtilities.findType(name);
    if (sd != null && sd.hasWebPath())
      return sd.getWebPath();
    brokenLinkMessage(corepath, name, false);
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
      br.uri = ref;
      br.external = false;
    } else if (ref.startsWith("ValueSet/")) {
      ValueSet vs = context.findTxResource(ValueSet.class, makeCanonical(ref));
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
          brokenLinkMessage(path, ref, false);
        }
      } else {
        br.url = vs.getWebPath();
        br.display = vs.getName(); 
        br.uri = vs.getVersionedUrl();
        br.external = vs.hasUserData(UserDataNames.render_external_link);
      }
    } else { 
      if (ref.startsWith("http://hl7.org/fhir/ValueSet/")) {
        ValueSet vs = context.findTxResource(ValueSet.class, ref);
        if (vs != null) { 
          br.url = vs.getWebPath();
          br.display = vs.getName(); 
          br.uri = vs.getUrl();
          br.external = vs.hasUserData(UserDataNames.render_external_link);
        } else {
          String nvref = ref;
          if (nvref.contains("|")) {
            nvref = nvref.substring(0, nvref.indexOf("|"));
          }
          String vsr = VersionConvertorConstants.vsToRef(nvref);
          if (vsr != null) {
            br.display = ref.substring(29);
            br.url = vsr;
          } else {
            br.display = ref.substring(29)+" (??)";
            br.url = null;
            brokenLinkMessage(path, ref, false);
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
          br.url = LoincLinker.getLinkForCode(code);
          br.display = "LOINC Answer List "+code;
        } else {
          br.url = LoincLinker.getLinkForCode(code);
          br.display = "LOINC "+code;
        }
      } else {
        ValueSet vs = context.findTxResource(ValueSet.class, ref);
        if (vs != null) {
          if (ref.contains("|")) {
            // for now, we don't do anything different. This is a todo - what can we do? 
            br.url = vs.getWebPath();
            br.display = vs.getName() + " (" + vs.getVersion() + ")"; 
          } else if (vs != null && vs.hasWebPath()) {
            br.url = vs.getWebPath();  
            br.display = vs.present();
          } else {
            br.url = vs.getWebPath();
            br.display = vs.getName(); 
          }
          br.uri = vs.getUrl();
          br.external = vs.hasUserData(UserDataNames.render_external_link);
        } else if (ref.startsWith("http://cts.nlm.nih.gov/fhir/ValueSet/")) {          
          String oid = ref.substring("http://cts.nlm.nih.gov/fhir/ValueSet/".length());
          br.url = "https://vsac.nlm.nih.gov/valueset/"+oid+"/expansion";  
          br.display = "VSAC "+oid;
          br.uri = ref;
          br.external = true;
        } else if (Utilities.isAbsoluteUrlLinkable(ref) && (!ref.startsWith("http://hl7.org") || !ref.startsWith("http://terminology.hl7.org"))) {
          br.url = ref;  
          br.display = ref;
        } else if (vs == null) {
          br.url = null;
          br.display = ref;
          brokenLinkMessage(path, ref, true);
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
    if (xver.matchingUrl(url)) {
      return xver.getReference(url); 
    }
    if (sd != null && sd.hasWebPath()) {
      if (url.contains("|") || hasMultipleVersions(context.fetchResourceVersions(StructureDefinition.class, url))) {
        return sd.getWebPath()+"|"+sd.getName()+"("+sd.getVersion()+")";        
      } else {
        return sd.getWebPath()+"|"+sd.getName();
      }
    }
    brokenLinkMessage("?pkp-1?", url, false);
    return "unknown.html|?pkp-2?";
  }

  private boolean hasMultipleVersions(List<? extends CanonicalResource> list) {
    Set<String> vl = new HashSet<>();
    for (CanonicalResource cr : list) {
      vl.add(cr.getVersion());
    }
    return vl.size() > 1;
  }

  @Override
  public String resolveType(String type) {
    return getLinkFor("", type);
  }

  @Override
  public String resolveProperty(Property property) {
    ElementDefinition ed = property.getDefinition();
    StructureDefinition sd = property.getStructure();
    String path = ed.getPath();
    if (ed.getBase().hasPath() && !path.equals(ed.getBase().getPath())) {
      StructureDefinition sdt = context.fetchTypeDefinition(head(ed.getBase().getPath()));
      if (sdt != null) {
        sd = sdt;
      }
    }
    while (sd.getDerivation() == TypeDerivationRule.CONSTRAINT) {
      StructureDefinition sdt = context.fetchResource(StructureDefinition.class, sd.getBaseDefinition());
      if (sdt != null) {
        sd = sdt;
      } else {
        break;
      }
    }
    if (sd.getSourcePackage() != null && sd.getSourcePackage().isCore() && VersionUtilities.isR5Plus(sd.getFhirVersion().toCode())) {
      return sd.getWebPath() + "#X" + path.replace("[x]", "_x_");
    } else {
      return sd.getWebPath() + "#" + path.replace("[x]", "_x_");
    }
  }

  private String head(String path) {
    return path.contains(".") ? path.substring(0, path.indexOf(".")) : path;
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
          br.url = LoincLinker.getLinkForCode(code);
          br.display = "LOINC Answer List "+code;
        } else {
          br.url = LoincLinker.getLinkForCode(code);
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
    return new ContextUtilities(context).getLinkForUrl(corePath, s);
  }

  public Set<String> summaryRows() {
    return summaryRows ;
  }

  @Override
  public String resolveReference(String ref) {
    if (ref == null) {
      return null;
    }
    Resource res = context.fetchResource(Resource.class, ref);
    if (res != null && res.hasWebPath()) {
      return res.getWebPath();
    }
    if (ref.startsWith(canonical)) {
      ref = Utilities.getRelativeUrlPath(canonical, ref);
    }
    String[] p = ref.split("/");
    if (p.length == 2 && files != null) {
      for (FetchedFile f : files) {
        for (FetchedResource r : f.getResources()) {
          if (p[0].equals(r.fhirType()) && p[1].equals(r.getId())) {
            return getLinkFor(r, true);
          }
        }
      }
    }
    return null;
  }

  @Override
  public String getCanonicalForDefaultContext() {
    return canonical;
  }

  public Map<String, List<ExtensionUsage>> getCoreExtensionMap() {
    if (coreExtensionMap == null) {
      coreExtensionMap = new HashMap<>();
    }
    return coreExtensionMap;
  }

  public static class ExtensionUsage {
    private String name;
    private String url;

    public ExtensionUsage(String name, String url) {
      this.name = name;
      this.url = url;
    }

    public String getName() {
      return name;
    }

    public String getUrl() {
      return url;
    }
  }
}
