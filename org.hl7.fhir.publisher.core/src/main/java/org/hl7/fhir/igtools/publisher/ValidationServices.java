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


import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.modules.IPublisherModule;
import org.hl7.fhir.r5.context.IContextResourceLoader;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.elementmodel.ObjectConverter;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.model.ActorDefinition;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.Enumerations.CodeSystemContentMode;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionResourceComponent;
import org.hl7.fhir.r5.model.NamingSystem;
import org.hl7.fhir.r5.model.NamingSystem.NamingSystemIdentifierType;
import org.hl7.fhir.r5.model.NamingSystem.NamingSystemUniqueIdComponent;
import org.hl7.fhir.r5.model.OperationDefinition;
import org.hl7.fhir.r5.model.Questionnaire;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.renderers.utils.Resolver;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.terminologies.ImplicitValueSets;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.r5.utils.validation.IMessagingServices;
import org.hl7.fhir.r5.utils.validation.IResourceValidator;
import org.hl7.fhir.r5.utils.validation.IValidationPolicyAdvisor;
import org.hl7.fhir.r5.utils.validation.IValidatorResourceFetcher;
import org.hl7.fhir.r5.utils.validation.constants.BindingKind;
import org.hl7.fhir.r5.utils.validation.constants.ContainedReferenceValidationPolicy;
import org.hl7.fhir.r5.utils.validation.constants.ReferenceValidationPolicy;
import org.hl7.fhir.utilities.SIDUtilities;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.validation.instance.advisor.BasePolicyAdvisorForFullValidation;

public class ValidationServices implements IValidatorResourceFetcher, IValidationPolicyAdvisor {

  private IWorkerContext context;
  private IGKnowledgeProvider ipg;
  private ImplementationGuide ig;
  private List<FetchedFile> files;
  private List<NpmPackage> packages;
  private Set<String> otherUrls = new HashSet<>();
  private List<String> mappingUrls = new ArrayList<>();
  private boolean bundleReferencesResolve;
  private List<SpecMapManager> specMaps;
  private List<PublisherUtils.LinkedSpecification> linkSpecMaps;
  private IPublisherModule module;
  private static boolean nsFailHasFailed = false; // work around for an THO 6.0.0 problem
  
  
  public ValidationServices(IWorkerContext context, IGKnowledgeProvider ipg, ImplementationGuide ig, List<FetchedFile> files, List<NpmPackage> packages,
                            boolean bundleReferencesResolve, List<SpecMapManager> specMaps, List<PublisherUtils.LinkedSpecification> linkSpecMaps, IPublisherModule module) {
    super();
    this.context = context;
    this.ipg = ipg;
    this.ig = ig;
    this.files = files;
    this.packages = packages;
    this.bundleReferencesResolve = bundleReferencesResolve;
    this.specMaps = specMaps;
    this.linkSpecMaps = linkSpecMaps;
    this.module = module;
    initOtherUrls();
  }

  @Override
  public Element fetch(IResourceValidator validator, Object appContext, String url) throws FHIRException, IOException {
    if (url == null)
      return null;
    if (url.contains("/_history/")) {
      url = url.substring(0, url.indexOf("/_history"));
    }
    String turl = (!Utilities.isAbsoluteUrl(url)) ? Utilities.pathURL(ipg.getCanonical(), url) : url;
    Resource res = context.fetchResource(getResourceType(turl), turl);
    if (res != null) {
      Element e = (Element)res.getUserData(UserDataNames.pub_element);
      if (e!=null)
        return e;
      else
        return new ObjectConverter(context).convert(res);
    }

    ValueSet vs = new ImplicitValueSets(context.getExpansionParameters()).generateImplicitValueSet(url);
    if (vs != null)
      return new ObjectConverter(context).convert(vs);
    
    for (NpmPackage npm : packages) {
      if (Utilities.isAbsoluteUrl(url) && npm.canonical() != null && url.startsWith(npm.canonical())) {
        String u = url.substring(npm.canonical().length());
        if (u.startsWith("/"))
          u = u.substring(1);
        String[] ul = u.split("\\/");
        if (ul.length >= 2) {
          InputStream s = npm.loadResource(ul[0], ul[1]);
          if (s == null) {
            s = npm.loadExampleResource(ul[0], ul[1]);
          }
          if (s != null)
            return Manager.makeParser(context, FhirFormat.JSON).parseSingle(s, null);
        }
      }
    }
    String[] parts = url.split("\\/");
    
    if (appContext != null) {
      Element bnd = (Element) appContext;
      int count = 0;
      for (Element be : bnd.getChildren("entry")) {
        count++;
        Element ber = be.getNamedChild("resource");
        if (ber != null) {
          if (be.hasChild("fullUrl") && be.getChildByName("fullUrl").equals(url)) {
            return ber;
          }
          if (parts.length == 2 && ber.fhirType().equals(parts[0]) && ber.hasChild("id") && ber.getChildValue("id").equals(parts[1])) 
            return ber;
        }        
      }
    }
    
    if (!Utilities.isAbsoluteUrl(url) || url.startsWith(ipg.getCanonical())) {
      if (parts.length == 2) {
        for (FetchedFile f : files) {
          for (FetchedResource r : f.getResources()) {
            if (r.getElement().fhirType().equals(parts[parts.length-2]) && r.getId().equals(parts[parts.length-1]))
              return r.getElement();
          }
        }
      }
    }
    
    if (Utilities.isAbsoluteUrl(url)) {
      for (FetchedFile f : files) {
        for (FetchedResource r : f.getResources()) {
          if (r.getElement().fhirType().equals("Bundle")) {
            for (Element be : r.getElement().getChildren("entry")) {
              Element ber = be.getNamedChild("resource");
              if (ber != null) {
                if (be.hasChild("fullUrl") && be.getChildValue("fullUrl").equals(url))
                  return ber;
              }
            }
          }
        }
      }
    }

    if (parts.length >= 2 && Utilities.existsInList(parts[parts.length - 2], context.getResourceNames())) {
      for (int i = packages.size() - 1; i >= 0; i--) {
        NpmPackage npm = packages.get(i);
        InputStream s = npm.loadExampleResource(parts[parts.length - 2], parts[parts.length - 1]);
        if (s != null) {
            return Manager.makeParser(context, FhirFormat.JSON).parseSingle(s, null);
        }
      }
    }
    

    for (FetchedFile f : files) {
      for (FetchedResource r : f.getResources()) {
        if ("ActorDefinition".equals(r.fhirType())) {
          ActorDefinition act = ((ActorDefinition) r.getResource());
          String aurl = ExtensionUtilities.readStringExtension(act, "http://hl7.org/fhir/tools/StructureDefinition/ig-actor-example-url");
          if (aurl != null && turl.startsWith(aurl)) {
            String tail = turl.substring(aurl.length()+1);
            for (ImplementationGuideDefinitionResourceComponent igr : ig.getDefinition().getResource()) {
              if (tail.equals(igr.getReference().getReference())) {
                String actor = ExtensionUtilities.readStringExtension(igr, "http://hl7.org/fhir/tools/StructureDefinition/ig-example-actor");
                if (actor.equals(act.getUrl())) {
                  for (FetchedFile f2 : files) {
                    for (FetchedResource r2 : f2.getResources()) {
                      String id = r2.fhirType()+"/"+r2.getId();
                      if (tail.equals(id)) {
                        return r2.getElement();
                      }
                    }
                  }
                }
              } 
            }
          }
        }
      }
    }

    if ("Parameters/expansion-parameters".equals(url)) {
      Element expr = new ObjectConverter(context).convert(context.getExpansionParameters());
      return expr;
    }

    //
    for (PublisherUtils.LinkedSpecification sp : linkSpecMaps) {
      Element resr = getResourceFromMap(url, sp.getSpm());
      if (resr != null) return resr;
    }
    return null;
  }

  private Element getResourceFromMap(String url, SpecMapManager sp) throws IOException {
    String[] pl = url.split("\\/");
    String rt = pl.length >= 2 ? pl[pl.length-2] : null;
    String id = pl.length >= 2 ? pl[pl.length-1] : null;
    String fp = Utilities.isAbsoluteUrl(url) ? url : sp.getBase()+"/"+ url;

    if (rt != null && !context.getResourceNamesAsSet().contains(rt)) {
      rt = null;
      id = null;
    }
    String path;
    try {
      path = sp.getPath(fp, null, rt, id);
    } catch (Exception e) {
      path = null;
    }

    // hack around an error in the R5 specmap file
    if (path != null && sp.isCore() && path.startsWith("http://terminology.hl7.org")) {
      path = null;
    }

    if (path != null) {
      InputStream s = null;
      if (rt != null && sp.getNpm() != null && fp.contains("/")) {
        s = sp.getNpm().loadExampleResource(rt, id);
        if (s == null) {
          s = sp.getNpm().loadResource(rt, id);
        }
      }
      if (s == null) {
        return null;
      } else {
        Element res = Manager.parseSingle(context, s, FhirFormat.JSON);
        res.setWebPath(path);
        return res;

      }
    }
    return null;
  }

  private Class getResourceType(String url) {
    if (url.contains("/ValueSet/"))
      return ValueSet.class;
    if (url.contains("/StructureDefinition/"))
      return StructureDefinition.class;
    if (url.contains("/CodeSystem/"))
      return CodeSystem.class;
    if (url.contains("/OperationDefinition/"))
      return OperationDefinition.class;
    if (url.contains("/Questionnaire/"))
      return Questionnaire.class;
    return null;
  }

  @Override
  public ContainedReferenceValidationPolicy policyForContained(IResourceValidator validator,
      Object appContext,
      StructureDefinition structure,
      ElementDefinition element,
      String containerType,
      String containerId,
      Element.SpecialElement containingResourceType,
      String path,
      String url) {
    return ContainedReferenceValidationPolicy.CHECK_VALID;
  }

  @Override
  public ReferenceValidationPolicy policyForReference(IResourceValidator validator, Object appContext, String path, String url, ReferenceDestinationType destinationType) {
    if (path.startsWith("Bundle.") && !bundleReferencesResolve) {
      return ReferenceValidationPolicy.CHECK_TYPE_IF_EXISTS;
    } else {
      return ReferenceValidationPolicy.CHECK_EXISTS_AND_TYPE;
    }
  }

  @Override
  public boolean resolveURL(IResourceValidator validator, Object appContext, String path, String url, String type, boolean canonical, List<CanonicalType> targets) throws IOException {
    String u = url;
    String v = null;
    if (url.contains("|")) {
      u = url.substring(0, url.indexOf("|"));
      v = url.substring(url.indexOf("|")+1);
    }
    if (otherUrls.contains(u) || otherUrls.contains(url)) {
      // ignore the version
      return true;
    }

    if (SIDUtilities.isKnownSID(u)) {
      return (v == null) || !SIDUtilities.isInvalidVersion(u, v);
    }

    if (u.startsWith("http://hl7.org/fhirpath/System.")) {
      return (v == null || Utilities.existsInList(v, "2.0.0", "1.3.0", "1.2.0", "1.1.0", "1.0.0", "0.3.0", "0.2.0"));       
    }
    
    if (path.contains("StructureDefinition.mapping") && (mappingUrls.contains(u) || mappingUrls.contains(url))) {
      // ignore the version
      return true;
    }
    
    if (url.contains("*")) {
      // for now, this is only done for StructureMap
      for (StructureMap map : context.fetchResourcesByType(StructureMap.class)) {
        if (urlMatches(url, map.getUrl())) {
          return true;
        }
      }
    }

    if (url.startsWith(ipg.getCanonical())) {
      for (FetchedFile f : files) {
        for (FetchedResource r: f.getResources()) {
          if (Utilities.pathURL(ipg.getCanonical(), r.fhirType(), r.getId()).equals(url) && (!canonical || VersionUtilities.getCanonicalResourceNames(context.getVersion()).contains(r.fhirType()))) {
            return true;
          }
        }
      }
    }
    try {
      for (NamingSystem ns : context.fetchResourcesByType(NamingSystem.class)) {
        if (hasURL(ns, u)) {
          // ignore the version?
          return true;
        }
      }
    } catch (Exception e) {
      if (!nsFailHasFailed) {
        e.printStackTrace();
        nsFailHasFailed  = true;
      }
    }

    String base = url.contains("#") ? url.substring(0, url.indexOf("#")) : url;
    for (SpecMapManager sp : specMaps) {
      if (sp.hasTarget(base)) {
        return true;
      }
    }

    if (module.resolve(u)) {
      return true;
    }
    
    for (Extension ext : ig.getExtensionsByUrl(ExtensionDefinitions.EXT_IG_URL)) {
      String value = ext.getExtensionString("uri");
      if (value != null && value.equals(url)) {
        return true;
      }
    }    

    if (org.hl7.fhir.r5.utils.BuildExtensions.allConsts().contains(u)) {
      return true;
    }
    try {
      Resource res = context.fetchResourceWithException(Resource.class, url);
      if (res != null) {
        return true;
      }
    } catch (FHIRException e) {
    }
    if (canonical) {
      if (targetsHas(targets, "CodeSystem")) {
        CodeSystem cs = context.findTxResource(CodeSystem.class, url);
        if (cs != null) {
          return true;
        }
      }
      //if (targetsHas(targets, "ValueSet")) { - can't do this test because of implicit value sets etc
      ValueSet vs = context.findTxResource(ValueSet.class, url);
      if (vs != null) {
        return true;
      }
      
    }
    if (Utilities.existsInList(path, "ValueSet.compose.include.system", "ValueSet.compose.exclude.system")) {
      CodeSystem cs = context.findTxResource(CodeSystem.class, url);
      if (cs != null) {
        return true;
      }      
    }
    return false;
  }
  

  private boolean targetsHas(List<CanonicalType> targets, String name) {
    if (targets == null) {
      return false;
    }
    for (CanonicalType ct : targets) {
      if (ct.getValue() != null && ct.getValue().contains(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean urlMatches(String mask, String url) {
    return url.length() > mask.length() && url.startsWith(mask.substring(0, mask.indexOf("*"))) && url.endsWith(mask.substring(mask.indexOf("*") + 1));
  }

  
  private boolean hasURL(NamingSystem ns, String url) {
    for (NamingSystemUniqueIdComponent uid : ns.getUniqueId()) {
      if (uid.getType() == NamingSystemIdentifierType.URI && uid.hasValue() && uid.getValue().equals(url)) {
        return true;
      }
      if (uid.getType() == NamingSystemIdentifierType.OID && uid.hasValue() && ("urn:oid:"+uid.getValue()).equals(url)) {
        return true;
      }
    }
    return false;
  }


  public Set<String> getOtherUrls() {
    return otherUrls;
  }

  public List<String> getMappingUrls() {
    return mappingUrls;
  }

  public void initOtherUrls() {
    otherUrls.clear();
    otherUrls.addAll(SIDUtilities.allSystemsList());
    otherUrls.add("http://hl7.org/fhir/w5");
    otherUrls.add("http://hl7.org/fhir/fivews");
    otherUrls.add("http://hl7.org/fhir/workflow");
    otherUrls.add("http://hl7.org/fhir/tools/StructureDefinition/resource-information");
    otherUrls.add("http://hl7.org/fhir/ConsentPolicy/opt-out"); 
    otherUrls.add("http://hl7.org/fhir/ConsentPolicy/opt-in");
  }

  @Override
  public IValidatorResourceFetcher setLocale(Locale locale) {
    return this;
  }

  @Override
  public byte[] fetchRaw(IResourceValidator validator, String source) throws MalformedURLException, IOException {
    URL url = new URL(source);
    URLConnection c = url.openConnection();
    return FileUtilities.streamToBytes(c.getInputStream());
  }

  @Override
  public CanonicalResource fetchCanonicalResource(IResourceValidator validator, Object appContext, String url) {
    if (url.equals(ig.getUrl())) {
      return ig;
    }
    return module.fetchCanonicalResource(url);
  }

  @Override
  public boolean fetchesCanonicalResource(IResourceValidator validator, String url) {
    return false;
  }

  @Override
  public EnumSet<CodedContentValidationAction> policyForCodedContent(IResourceValidator validator,
      Object appContext,
      String stackPath,
      ElementDefinition definition,
      StructureDefinition structure,
      BindingKind kind,
      AdditionalBindingPurpose purpose,
      ValueSet valueSet,
      List<String> systems) {
    if (VersionUtilities.isR4BVer(context.getVersion()) && 
        "ImplementationGuide.definition.parameter.code".equals(definition.getBase().getPath())) {
      return EnumSet.noneOf(CodedContentValidationAction.class);
    }
    return EnumSet.allOf(CodedContentValidationAction.class);
  }

  @Override
  public EnumSet<ResourceValidationAction> policyForResource(IResourceValidator validator, Object appContext,
      StructureDefinition type, String path) {
    return EnumSet.allOf(ResourceValidationAction.class);
  }

  @Override
  public EnumSet<ElementValidationAction> policyForElement(IResourceValidator validator, Object appContext,
      StructureDefinition structure, ElementDefinition element, String path) {
    return EnumSet.allOf(ElementValidationAction.class);
  }

  @Override
  public Set<ResourceVersionInformation> fetchCanonicalResourceVersions(IResourceValidator validator, Object appContext, String url) {
    Set<ResourceVersionInformation> res = new HashSet<>();
    Set<String> versions = new HashSet<>();
    for (Resource r : context.fetchResourceVersions(Resource.class, url)) {
      if (r instanceof CanonicalResource) {
        
        CanonicalResource cr = (CanonicalResource) r;
        if (cr.getUrl().contains("terminology.hl7.org") && cr.getSourcePackage() != null && cr.getSourcePackage().isCore()) {
          continue;
        }
        if (cr instanceof CodeSystem) {
          CodeSystem cs = (CodeSystem) cr;
          if (cs.getContent() == CodeSystemContentMode.NOTPRESENT) {
            if (!context.getTxSupportInfo(cs.getUrl(), cs.getVersion()).isServerSide()) {
              // ?? res.add(cr.hasVersion() ? cr.getVersion() : "{{unversioned}}");                          
            }
          } else {
            checkAddResourceVersion(res, versions, cr);
          }
        } else {
          checkAddResourceVersion(res, versions, cr);
        }
      }
    }
    return res;
  }

  private static void checkAddResourceVersion(Set<ResourceVersionInformation> res, Set<String> versions, CanonicalResource cr) {
    String version = cr.hasVersion() ? cr.getVersion() : "{{unversioned}}";
    if (!versions.contains(version)) {
      versions.add(version);
      res.add(new ResourceVersionInformation(version, cr.getSourcePackage()));
    }
  }

  public Map<String, String> fetchCanonicalResourceVersionMap(IResourceValidator validator, Object appContext, String url) {
    Map<String, String> res = new HashMap<>();
    for (Resource r : context.fetchResourceVersions(Resource.class, url)) {
      if (r instanceof CanonicalResource) {        
        CanonicalResource cr = (CanonicalResource) r;
        if (cr.getUrl().contains("terminology.hl7.org") && cr.hasSourcePackage() && cr.getSourcePackage().isCore()) {
          continue;
        }
        if (cr instanceof CodeSystem) {
          CodeSystem cs = (CodeSystem) cr;
          if (cs.getContent() == CodeSystemContentMode.NOTPRESENT) {
            if (!context.getTxSupportInfo(cs.getUrl(), cs.getVersion()).isServerSide()) {
              // ?? res.add(cr.hasVersion() ? cr.getVersion() : "{{unversioned}}");                          
            }
          } else {  
            res.put(cr.hasVersion() ? cr.getVersion() : "{{unversioned}}", cr.hasSourcePackage() ? cr.getSourcePackage().getVID() : null);            
          }
        } else {
          res.put(cr.hasVersion() ? cr.getVersion() : "{{unversioned}}", cr.hasSourcePackage() ? cr.getSourcePackage().getVID() : null);
        }
      }
    }
    return res;
  }

  @Override
  public List<StructureDefinition> getImpliedProfilesForResource(IResourceValidator validator, Object appContext,
      String stackPath, ElementDefinition definition, StructureDefinition structure, Element resource, boolean valid,
      IMessagingServices msgServices, List<ValidationMessage> messages) {
    return new BasePolicyAdvisorForFullValidation(ReferenceValidationPolicy.CHECK_VALID, null).getImpliedProfilesForResource(validator, appContext, stackPath,
        definition, structure, resource, valid, msgServices, messages);
  }

  @Override
  public boolean isSuppressMessageId(String path, String messageId) {
    return false;
  }

  @Override
  public ReferenceValidationPolicy getReferencePolicy() {
    return ReferenceValidationPolicy.CHECK_VALID;
  }

  @Override
  public Set<String> getCheckReferencesTo() {
    return Set.of();
  }

  @Override
  public IValidationPolicyAdvisor getPolicyAdvisor() {
    return null;
  }

  @Override
  public IValidationPolicyAdvisor setPolicyAdvisor(IValidationPolicyAdvisor policyAdvisor) {
    throw new Error("Not supported"); // !
  }

  @Override
  public SpecialValidationAction policyForSpecialValidation(IResourceValidator validator, Object appContext, SpecialValidationRule rule, String stackPath, Element resource, Element element) {
    return SpecialValidationAction.CHECK_RULE;

  }
}
