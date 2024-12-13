package org.hl7.fhir.igtools.publisher;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r5.utils.sql.Provider;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.elementmodel.ValidatedFragment;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.NpmPackage.PackageResourceInformation;

public class PublisherProvider implements Provider {

  private IWorkerContext context;
  private List<NpmPackage> npmList;
  private List<FetchedFile> fileList;
  private List<String> packageMasks = new ArrayList<String>();
  private String canonical;
  
  protected PublisherProvider(IWorkerContext context, List<NpmPackage> npmList, List<FetchedFile> fileList, String canonical) {
    super();
    this.context = context;
    this.npmList = npmList;
    this.fileList = fileList;
    this.canonical = canonical;
  }

  @Override
  public List<Base> fetch(String resourceType) {
    List<Base> results = new ArrayList<>();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (resourceType.equals(r.fhirType())) {
          results.add(r.getElement());
        }
      }
    }
    if (!packageMasks.isEmpty()) {
      for (NpmPackage npm : npmList) {
        if (meetsPackageMasks(npm)) {
          try {
            for (PackageResourceInformation f : npm.listIndexedResources(resourceType)) {
              InputStream cnt = npm.load(f);
              List<ValidatedFragment> fragments = Manager.parse(context, cnt, FhirFormat.JSON);
              if (fragments.size() == 1) {
                results.add(fragments.get(0).getElement());
              }
            } 
          } catch (Exception e) {
            // nothing
          }
        }
      }
    }
    return results;
  }

  private boolean meetsPackageMasks(NpmPackage npm) {
    for (String mask : packageMasks) {
      if (meetsPackageMask(mask, npm)) {
        return true;
      }
    }
    return false;
  }

  private boolean meetsPackageMask(String mask, NpmPackage npm) {
    if ("*".equals(mask)) {
      return true;
    }
    if (mask.endsWith("*") && npm.name().startsWith(mask.substring(0, mask.length()-1))) {
      return true;
    }
    return false;
  }

  @Override
  public Base resolveReference(Base rootResource, String ref, String specifiedResourceType) {
    if (ref.startsWith("#")) {
      return null;
    }
    if (Utilities.isAbsoluteUrl(ref)) {
      return null;
    } else {
      String[] p = ref.split("/");
      if (p.length == 2 && context.getResourceNamesAsSet().contains(p[0])) {
        for (FetchedFile f : fileList) {
          for (FetchedResource r : f.getResources()) {
            if (p[0].equals(r.fhirType()) && p[1].equals(r.getId())) {
              return r.getElement();
            }
          }
        }
      }
    }
    return null;
  }

  public void inspect(JsonObject vd) {
    for (JsonObject extension : vd.getJsonObjects("extension")) {
      packageMasks.add(extension.asString("valueCode"));
    }
    
    
  }

}
