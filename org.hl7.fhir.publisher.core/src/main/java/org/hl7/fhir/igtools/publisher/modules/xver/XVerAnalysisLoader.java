package org.hl7.fhir.igtools.publisher.modules.xver;

import java.io.IOException;

import org.hl7.fhir.convertors.loaders.loaderR5.ILoaderKnowledgeProviderR5;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.NpmPackage;

import com.google.gson.JsonSyntaxException;

public class XVerAnalysisLoader implements ILoaderKnowledgeProviderR5 {

  private String webPath;
  
  protected XVerAnalysisLoader(String webPath) {
    super();
    this.webPath = webPath;
  }

  @Override
  public String getResourcePath(Resource resource) {
    return Utilities.pathURL(webPath, resource.fhirType()+"-"+resource.getId()+".html");
  }

  @Override
  public ILoaderKnowledgeProviderR5 forNewPackage(NpmPackage npm) throws JsonSyntaxException, IOException {
    return null;
  }

  @Override
  public String getWebRoot() {
    return null;
  }

}
