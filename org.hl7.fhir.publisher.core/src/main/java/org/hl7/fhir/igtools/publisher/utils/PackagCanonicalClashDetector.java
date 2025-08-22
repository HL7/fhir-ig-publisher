package org.hl7.fhir.igtools.publisher.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.igtools.publisher.loaders.PublisherLoader;
import org.hl7.fhir.r4.test.utils.TestingUtilities;
import org.hl7.fhir.r5.context.IContextResourceLoader;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;

import com.google.gson.JsonSyntaxException;

@Slf4j
public class PackagCanonicalClashDetector {

  public class LoadedCanonicalResource {

    private CanonicalResource resource;
    private String pid;

    public LoadedCanonicalResource(CanonicalResource r, String pid) {
     this.resource = r;
     this.pid = pid;
    }

    public CanonicalResource getResource() {
      return resource;
    }

    public String getPid() {
      return pid;
    }
  
  }

  public static void main(String[] args) throws IOException {
    PackagCanonicalClashDetector self = new PackagCanonicalClashDetector();
    for (String arg : args) {
      self.check(arg);
    }
    self.analyse();
  }
  
  private List<LoadedCanonicalResource> resources = new ArrayList<>();
  private Map<String, List<LoadedCanonicalResource>> map = new HashMap<>();

  private void analyse() throws IOException {
    System.out.println("Loaded "+resources.size()+" Resources");
    for (LoadedCanonicalResource lcr : resources) {
      if ("http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1".equals(lcr.getResource().getUrl())) {
        System.out.print(".");      
      }
      String id = lcr.getResource().getVersionedUrl();
      if (!map.containsKey(id)) {
        map.put(id, new ArrayList<>());
      }
      List<LoadedCanonicalResource> v = map.get(id);
      v.add(lcr);
    }
    for (String s : map.keySet()) {
      List<LoadedCanonicalResource> list = map.get(s);
      if (list.size() > 1) {
        checkResources(s, list);
      }
    }
  }

  
  private void checkResources(String s, List<LoadedCanonicalResource> list) throws JsonSyntaxException, FileNotFoundException, IOException {
    List<String> sl = new ArrayList<>();
    String fn0 = Utilities.path("[tmp]", "c0.json");
    String fn1 = Utilities.path("[tmp]", "c1.json");
    list.get(0).resource.setMeta(null);
    list.get(0).resource.setDate(null);
    list.get(1).resource.setMeta(null);
    list.get(1).resource.setDate(null);
    FileUtilities.bytesToFile(new JsonParser().composeBytes(list.get(0).resource), fn0);
    FileUtilities.bytesToFile(new JsonParser().composeBytes(list.get(1).resource), fn1);
    String diff = TestingUtilities.checkJsonIsSame(fn0, fn1);
    if (diff != null) {
      System.out.println(s+": "+diff);
    }
    
  }


  private void check(String pid) throws IOException {
    log.info("Load Package "+pid);
    FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager.Builder().build();
    NpmPackage npm = pcm.loadPackage(pid);
//    SpecMapManager spm = new SpecMapManager(TextFile.streamToBytes(npm.load("other", "spec.internals")), npm.fhirVersion());
    IContextResourceLoader loader = new PublisherLoader(npm, null, npm.getWebLocation(), null, false).makeLoader();
    String[] types = new String[] { "StructureDefinition", "ValueSet", "SearchParameter", "OperationDefinition", "Questionnaire", "ConceptMap", "StructureMap", "NamingSystem" };
    for (String s : npm.listResources(types)) {
      Resource r = loader.loadResource(npm.load("package", s), true);
      if (r instanceof CanonicalResource) {
        resources.add(new LoadedCanonicalResource((CanonicalResource) r, npm.name()+"#"+npm.version()));
      }
    } 
  }


}
