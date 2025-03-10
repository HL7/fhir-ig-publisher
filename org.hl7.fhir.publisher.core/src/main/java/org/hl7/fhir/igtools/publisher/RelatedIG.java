package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.convertors.context.ContextResourceLoaderFactory;
import org.hl7.fhir.convertors.loaders.loaderR5.NullLoaderKnowledgeProviderR5;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.IContextResourceLoader;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionResourceComponent;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageHacker;

public class RelatedIG {

  public enum RelatedIGLoadingMode {
    NOT_FOUND,
    LOCAL,
    CIBUILD,
    WEB;

    public String toCode() {
      switch (this) {
      case CIBUILD: return "ci-build";
      case LOCAL: return "local";
      case NOT_FOUND: return "not found";
      case WEB: return "Web location";
      default: return "??";
      }
    }
  }

  public enum RelatedIGRole {
    MODULE,
    DATA,
    TEST_CASES,
    REF_IMPL;

    public static RelatedIGRole fromCode(String code) {
      switch (code) {
      case "module": return MODULE;
      case "data": return DATA;
      case "test-cases": return TEST_CASES;
      case "ref-impl": return REF_IMPL;
      default: return null;
      }
    }
    public String toCode() {
      switch (this) {
      case MODULE: return "module";
      case DATA: return "data";
      case TEST_CASES: return "test-cases";
      case REF_IMPL: return "ref-impl";
      default: return "??";
      }
    }
    
    public String toDisplay(boolean plural) {
      switch (this) {
      case MODULE: return "Additional Module"+(plural ? "s" : "");
      case DATA: return "Data Package"+(plural ? "s" : "");
      case TEST_CASES: return "Test Cases";
      case REF_IMPL: return "Reference Implementation"+(plural ? "s" : "");
      default: return "??";
      }
    }
  }
  
  private final String code;
  private final String id;
  private final RelatedIGLoadingMode mode;
  private final NpmPackage npm;
  private boolean rebuild;
  private boolean checkUsages;
  private int linkCount;
  private final RelatedIGRole role;
  private SpecMapManager spm;
  private Map<String, List<CanonicalResource>> resources = new HashMap<>();
  private Map<String, List<Element>> elements = new HashMap<>();
  private String message;
  private ImplementationGuide ig;
  private String webLocation;
  
  protected RelatedIG(String code, String id, RelatedIGLoadingMode mode, RelatedIGRole role, NpmPackage npm) {
    super();
    this.id = id;
    this.mode = mode;
    this.npm = npm;
    this.code = code;
    this.role = role;
    this.message = null;
    this.webLocation = npm.getWebLocation();
    load();
  }
  
  protected RelatedIG(String code, String id, RelatedIGLoadingMode mode, RelatedIGRole role, NpmPackage npm, String location) {
    super();
    this.id = id;
    this.mode = mode;
    this.npm = npm;
    this.code = code;
    this.role = role;
    this.message = null;
    this.webLocation = location;
    load();
  }

  
  private void load() {
    
    try {
      IContextResourceLoader loader = ContextResourceLoaderFactory.makeLoader(npm.fhirVersion(), new NullLoaderKnowledgeProviderR5());
      for (String s : npm.listResources("ImplementationGuide")) {
        try {
          Resource res = loader.loadResource(npm.load("package", s), true);
          if (res instanceof ImplementationGuide) {
            ig = (ImplementationGuide) res;
            break;
          }
        } catch (Exception e) {
          // nothing?
        }
      }
    } catch (Exception e) {
      // nothing?
    }    

  }
  public RelatedIG(String code, String id, RelatedIGRole role, String message) {
    super();
    this.id = id;
    this.mode = RelatedIGLoadingMode.NOT_FOUND;
    this.npm = null;
    this.code = code;
    this.role = role;
    this.message = message;    
  }


  public String getCode() {
    return code;
  }


  public String getId() {
    return id;
  }

  public String getCanonical() {
    return npm == null ? null : npm.canonical();
  }

  public RelatedIGLoadingMode getMode() {
    return mode;
  }

  public String getWebLocation() {
    return webLocation;
  }

  public NpmPackage getNpm() {
    return npm;
  }

  public boolean isRebuild() {
    return rebuild;
  }

  public void setRebuild(boolean rebuild) {
    this.rebuild = rebuild;
  }

  public boolean isCheckUsages() {
    return checkUsages;
  }

  public void setCheckUsages(boolean checkUsages) {
    this.checkUsages = checkUsages;
  }

  public int getLinkCount() {
    return linkCount;
  }

  public void setLinkCount(int linkCount) {
    this.linkCount = linkCount;
  }

  public String getTitle() {
    return npm == null ? "Unknown IG" : npm.title();
  }

  public RelatedIGRole getRole() {
    return role;
  }

  public String getRoleCode() {
    return role.toCode();
  }

  public String getVersion() {
    return npm == null ? "" : npm.version();
  }

  public void dump() {
    spm = null;
    resources.clear();
  }

  public SpecMapManager getSpm() throws IOException {
    if (spm == null && npm != null && npm.hasFile("other", "spec.internals")) {
      spm = new SpecMapManager(FileUtilities.streamToBytes(npm.load("other", "spec.internals")), npm.vid(), npm.fhirVersion());
      spm.setName(npm.title());
      spm.setBase(npm.canonical());
      spm.setBase2(PackageHacker.fixPackageUrl(npm.url()));
    }
    return spm;
  }


  public List<CanonicalResource> load(String rt) {
    if (resources.containsKey(rt)) {
      return resources.get(rt);
    } else {
      List<CanonicalResource> list = new ArrayList<>();
      resources.put(rt,  list);
      try {
        IContextResourceLoader loader = ContextResourceLoaderFactory.makeLoader(npm.fhirVersion(), new NullLoaderKnowledgeProviderR5());
        for (String s : npm.listResources(rt)) {
          try {
            Resource res = loader.loadResource(npm.load("package", s), true);
            if (res instanceof CanonicalResource) {
              list.add((CanonicalResource) res);
            }
          } catch (Exception e) {
            // nothing?
          }
        }
      } catch (Exception e) {
        // nothing?
      }
      return list;
    }
  }
  

  public List<Element> loadE(IWorkerContext ctxt, String rt) {
    if (elements.containsKey(rt)) {
      return elements.get(rt);
    } else {
      List<Element> list = new ArrayList<>();
      elements.put(rt,  list);
      if (npm != null) {
        try {
          for (String s : npm.listResources(rt)) {
            try {
              Element e = Manager.parseSingle(ctxt, npm.load("package", s), FhirFormat.JSON);
              e.setUserData(UserDataNames.renderer_title, getTitle(e.fhirType(), e.getIdBase()));
              e.setWebPath(getWebPath(e.fhirType(), e.getIdBase()));
              list.add(e);
            } catch (Exception e) {
              // nothing?
            }
          }
        } catch (Exception e) {
          // nothing?
        }
      }
      return list;
    }
  }


  private String getWebPath(String fhirType, String id) throws FHIRException, IOException {
    String url = Utilities.pathURL(npm.canonical(), fhirType, id);
    return getSpm().getPath(url, null, fhirType, id);
  }


  private String getTitle(String fhirType, String id) {
    String res = fhirType+"/"+id;
    for (ImplementationGuideDefinitionResourceComponent r : ig.getDefinition().getResource()) {
      if (res.equals(r.getReference().getReference())) {
        if (r.hasDescription()) {
          return r.getDescription();
        }
      }
    }
    return res;
  }


  public String getMessage() {
    return message;
  }

}
