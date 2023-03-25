package org.hl7.fhir.igtools.publisher.loaders;

import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.NpmPackage;

public class LoaderUtils {

  protected NpmPackage npm;
  protected SpecMapManager spm;
  protected String pathToSpec;
  
  public LoaderUtils(NpmPackage npm, SpecMapManager spm, String pathToSpec) {
    this.npm = npm;
    this.spm = spm;
    this.pathToSpec = pathToSpec;
  }
  
  protected String getCorePath(Resource resource) {
    if (resource instanceof CanonicalResource) {
      CanonicalResource bc = (CanonicalResource) resource;
      String s = getOverride(bc.getUrl());
      if (s == null) {
        if (spm == null) {
          return null;
        }
        s = spm.getPath(bc.getUrl(), resource.getMeta().getSource(), resource.fhirType(), resource.getId());
      }
      if (s == null && bc instanceof CodeSystem) { // work around for an R2 issue) 
        CodeSystem cs = (CodeSystem) bc;
        s = spm.getPath(cs.getValueSet(), resource.getMeta().getSource(), resource.fhirType(), resource.getId());
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

}
