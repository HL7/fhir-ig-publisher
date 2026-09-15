package org.hl7.fhir.igtools.publisher.loaders;

import java.io.IOException;
import java.util.Set;

import org.hl7.fhir.convertors.loaders.loaderRN.*;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.igtools.publisher.SpecMapManager.SpecialPackageType;
import org.hl7.fhir.igtools.publisher.SpecialTypeHandler;
import org.hl7.fhir.model.IModelContext;
import org.hl7.fhir.services.context.IContextResourceLoaderN;
import org.hl7.fhir.model.extensions.ExtensionDefinitions;
import org.hl7.fhir.model.extensions.ExtensionUtilities;
import org.hl7.fhir.model.core.CanonicalResource;
import org.hl7.fhir.model.core.DomainResource;
import org.hl7.fhir.model.core.Resource;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.utilities.UserDataNames;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.npm.NpmPackage;

import com.google.gson.JsonSyntaxException;

public class PublisherLoader extends LoaderUtils implements ILoaderKnowledgeProviderRN {

  private final boolean internalUseOnly;
  private IGKnowledgeProvider igpkp;
  private final IModelContext context;

  public PublisherLoader(NpmPackage npm, SpecMapManager spm, String pathToSpec, IGKnowledgeProvider igpkp, boolean internalUseOnly, IModelContext context) {
    super(npm, spm, pathToSpec);
    this.igpkp = igpkp;
    this.internalUseOnly = internalUseOnly;
    this.context = context;
  }

  public IContextResourceLoaderN makeLoader() {
    // there's no penalty for listing resources that don't exist, so we just all the relevant possibilities for all versions 
    Set<String> types = Utilities.stringSet("CodeSystem", "ValueSet", "ConceptMap", "NamingSystem",
                                   "StructureDefinition", "StructureMap", 
                                   "SearchParameter", "OperationDefinition", "CapabilityStatement", "Conformance",
                                   "Questionnaire", "ImplementationGuide",
                                   "Measure");
    if (VersionUtilities.isR4BVer(npm.fhirVersion())) {
      types.addAll(SpecialTypeHandler.SPECIAL_TYPES_4B);
    } else {
      types.addAll(SpecialTypeHandler.SPECIAL_TYPES_OTHER);
    }
    if (VersionUtilities.isR2Ver(npm.fhirVersion())) {
      return new R2ToRNLoader(context, types, this).addTag(internalUseOnly ? UserDataNames.RESOURCE_INTERNAL_USE_ONLY : null);
    } else if (VersionUtilities.isR2BVer(npm.fhirVersion())) {
      return new R2016MayToRNLoader(context, types, this).addTag(internalUseOnly ? UserDataNames.RESOURCE_INTERNAL_USE_ONLY : null);
    } else if (VersionUtilities.isR3Ver(npm.fhirVersion())) {
      return new R3ToRNLoader(context, types, this).addTag(internalUseOnly ? UserDataNames.RESOURCE_INTERNAL_USE_ONLY : null);
    } else if (VersionUtilities.isR4Ver(npm.fhirVersion())) {
      return new R4ToRNLoader(context, types, this, npm.fhirVersion()).addTag(internalUseOnly ? UserDataNames.RESOURCE_INTERNAL_USE_ONLY : null);
    } else if (VersionUtilities.isR4BVer(npm.fhirVersion())) {
      return new R4BToRNLoader(context, types, this, npm.fhirVersion()).addTag(internalUseOnly ? UserDataNames.RESOURCE_INTERNAL_USE_ONLY : null);
    } else if (VersionUtilities.isR5Ver(npm.fhirVersion())) {
      return new R5ToRNLoader(context, types, this).addTag(internalUseOnly ? UserDataNames.RESOURCE_INTERNAL_USE_ONLY : null);
    } else {
      return new RNToRNLoader(context, types, this).addTag(internalUseOnly ? UserDataNames.RESOURCE_INTERNAL_USE_ONLY : null);
    }
  }
  
  @Override
  public String getResourcePath(Resource resource) {
   
    if (isCore()) {
      return getCorePath(resource);
    } else {
      if (pathToSpec == null || igpkp == null) {
        if (spm != null && spm.getSpecial() == SpecialPackageType.Simplifier) {
          if (resource instanceof CanonicalResource) {
            return spm.getPath(((CanonicalResource) resource).getUrl(), resource.getMeta().getSource(), resource.fhirType(), resource.getId());
          } else {
            return null;
          }
        } else {
          return null;
        }
      }
      return getIgPath(resource);
    }
  }

  private String getIgPath(Resource r) {
    if (r instanceof CanonicalResource) {
      CanonicalResource cr = (CanonicalResource) r;
      String u = cr.getUrl();
      if (u != null) {
        if (u.contains("|")) {
          u = u.substring(0, u.indexOf("|"));
        }
        String p = spm.getPath(u, r.getMeta().getSource(), r.fhirType(), r.getId());
        if (p == null) {
          if ("NamingSystem".equals(r.fhirType())) {
            // work around for an issue caused by loading the NamingSystem url extension correctly while converting to R5 internally,
            // but not generating the correct metadata for the NamingSystem when the package was generated before the URL was being
            // processed correctly
            return null;
          }
          if (spm.getSpecial() != null) {
            // these weird packages don't always have paths
            return null;
          }
          if (cr.hasExtension(ExtensionDefinitions.EXT_WEB_SOURCE_OLD, ExtensionDefinitions.EXT_WEB_SOURCE_NEW)) {
            return ExtensionUtilities.readStringExtension(cr, ExtensionDefinitions.EXT_WEB_SOURCE_OLD, ExtensionDefinitions.EXT_WEB_SOURCE_NEW);
          }
          System.out.println("Internal error in IG "+npm.name()+"#"+npm.version()+" map: No identity found for "+u);
          return u;
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
        if (r instanceof DomainResource) {
          DomainResource dr = (DomainResource) r;
          if (dr.hasExtension(ExtensionDefinitions.EXT_WEB_SOURCE_OLD, ExtensionDefinitions.EXT_WEB_SOURCE_NEW)) {
            path = ExtensionUtilities.readStringExtension(dr, ExtensionDefinitions.EXT_WEB_SOURCE_OLD, ExtensionDefinitions.EXT_WEB_SOURCE_NEW);
          }
        }
        r.setWebPath(path);
        if (path.contains("vsac")) {
          r.setUserData(UserDataNames.render_external_link, "https://vsac.nlm.nih.gov");
        }
        r.setUserData(UserDataNames.render_webroot, pathToSpec);
//        String v = ((CanonicalResource) r).getVersion();
        return path;
      } 
      
    }
    return null;
  }

  private boolean isCore() {
    return npm.isCore();
  }

  
  private String tail(String ref) {
    if  (ref.contains("/"))
      return ref.substring(ref.lastIndexOf("/")+1);
    else
      return ref;
  }

  @Override
  public ILoaderKnowledgeProviderRN forNewPackage(NpmPackage npm) throws JsonSyntaxException, IOException {
    return new PublisherLoader(npm, SpecMapManager.fromPackage(npm), npm.getWebLocation(), igpkp, internalUseOnly, context);
  }

  @Override
  public String getWebRoot() {
    return pathToSpec;
  }


}
