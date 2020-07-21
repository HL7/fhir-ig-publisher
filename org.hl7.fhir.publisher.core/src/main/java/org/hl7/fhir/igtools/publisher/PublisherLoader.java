package org.hl7.fhir.igtools.publisher;

import org.hl7.fhir.convertors.loaders.BaseLoaderR5.ILoaderKnowledgeProvider;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.SimpleWorkerContext.ILoadFilter;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.cache.NpmPackage;

public class PublisherLoader implements ILoaderKnowledgeProvider {

  private NpmPackage npm;
  private SpecMapManager spm;
  private String pathToSpec;
  private IGKnowledgeProvider igpkp;

  public PublisherLoader(NpmPackage npm, SpecMapManager spm, String pathToSpec, IGKnowledgeProvider igpkp) {
    super();
    this.npm = npm;
    this.spm = spm;
    this.pathToSpec = pathToSpec;
    this.igpkp = igpkp;
  }

  @Override
  public String getResourcePath(Resource resource) {
    if (isCore()) {
      return getCorePath(resource);
    } else {
      return getIgPath(resource);
    }
  }

  private String getIgPath(Resource r) {
    if (r instanceof CanonicalResource) {
      String u = ((CanonicalResource) r).getUrl();
      if (u != null) {
        String p = spm.getPath(u);
        if (p == null) {
          throw new FHIRException("Internal error in IG "+npm.name()+"#"+npm.version()+" map: No identity found for "+u);
        }
        if (!r.hasId()) {
          r.setId(tail(u));
        }
        String path;
        if (Utilities.isAbsoluteUrl(p)) {
          path = igpkp.doReplacements(p, r, null, null);            
        } else {
          path = pathToSpec+"/"+ igpkp.doReplacements(p, r, null, null);
        }
        String v = ((CanonicalResource) r).getVersion();
        if (v != null) {
          u = u + "|" + v;
          p = spm.getPath(u);
          if (p == null) {
            System.out.println("In IG "+npm.name()+"#"+npm.version()+" map: No identity found for "+u);
          }
          r.setUserData("versionpath", igpkp.getCanonical()+"/"+ igpkp.doReplacements(p, r, null, null));
        }
        return path;
      } 
      
    }
    return null;
  }

  private boolean isCore() {
    return npm.isCore();
  }

  private String getCorePath(Resource resource) {
    if (resource instanceof CanonicalResource) {
      CanonicalResource bc = (CanonicalResource) resource;
      String s = getOverride(bc.getUrl());
      if (s == null) {
        s = spm.getPath(bc.getUrl());
      }
      if (s == null && bc instanceof CodeSystem) { // work around for an R2 issue) 
        CodeSystem cs = (CodeSystem) bc;
        s = spm.getPath(cs.getValueSet());
      }
      if (s != null) {
        return specPath(s);
        // special cases
      } else if (bc.hasUrl() && bc.getUrl().equals("http://hl7.org/fhir/ValueSet/security-role-type")) {
        return specPath("valueset-security-role-type.html");
      } else if (bc.hasUrl() && bc.getUrl().equals("http://hl7.org/fhir/ValueSet/object-lifecycle-events")) {
        return specPath("valueset-object-lifecycle-events.html");
      } else if (bc.hasUrl() && bc.getUrl().equals("http://hl7.org/fhir/ValueSet/performer-function")) {
        return specPath("valueset-performer-function.html");
      } else if (bc.hasUrl() && bc.getUrl().equals("http://hl7.org/fhir/ValueSet/written-language")) {
        return specPath("valueset-written-language.html");
      } else {
        return null;
      }
    } else { 
      return null;
    }
  }
  
  public String specPath(String path) {
    if (Utilities.isAbsoluteUrl(path)) {
      return path;
    } else if (npm.isCore()) {
      return Utilities.pathURL(npm.getWebLocation(), path);
    } else {
      assert pathToSpec != null;
      return Utilities.pathURL(pathToSpec, path);
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

  private String tail(String ref) {
    if  (ref.contains("/"))
      return ref.substring(ref.lastIndexOf("/")+1);
    else
      return ref;
  }

}
