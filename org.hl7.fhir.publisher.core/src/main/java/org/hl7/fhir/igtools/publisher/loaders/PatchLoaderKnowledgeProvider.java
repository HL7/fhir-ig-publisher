package org.hl7.fhir.igtools.publisher.loaders;

import java.io.IOException;

import org.hl7.fhir.convertors.loaders.loaderR5.ILoaderKnowledgeProviderR5;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.utilities.npm.NpmPackage;

import com.google.gson.JsonSyntaxException;

public class PatchLoaderKnowledgeProvider extends LoaderUtils implements ILoaderKnowledgeProviderR5 {

  public PatchLoaderKnowledgeProvider(NpmPackage npm, SpecMapManager spm) {
    super(npm, spm, npm.getWebLocation());
  }
//
//  private static String getPathForVersion(String version) {
//    if (VersionUtilities.isR2Ver(version)) {
//      return "http://hl7.org/fhir/DSTU2";
//    } else if (VersionUtilities.isR2BVer(version)) {
//      return "http://hl7.org/fhir/2016May";
//    } else if (VersionUtilities.isR3Ver(version)) {
//      return "http://hl7.org/fhir/STU3";
//    } else if (VersionUtilities.isR4Ver(version)) {
//      return "http://hl7.org/fhir/R4";
//    } else if (VersionUtilities.isR4BVer(version)) {
//      return "http://hl7.org/fhir/R4B";
//    } else {
//      return "http://hl7.org/fhir/R5";
//    }
//  }
//

  @Override
  public String getResourcePath(Resource resource) {

    return getCorePath(resource);
  }

  @Override
  public ILoaderKnowledgeProviderR5 forNewPackage(NpmPackage npm) throws JsonSyntaxException, IOException {
    return null;
  }

  @Override
  public String getWebRoot() {
    return npm.getWebLocation();
  }

}
