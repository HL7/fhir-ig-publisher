package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.loaders.PublisherLoader;
import org.hl7.fhir.r5.context.IContextResourceLoader;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.Identifier;
import org.hl7.fhir.r5.model.Identifier.IdentifierUse;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.ResourceType;
import org.hl7.fhir.utilities.CSVWriter;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;

public class CrossVersionOIDScanner {
  
  public static void main(String[] args) throws IOException {
    new CrossVersionOIDScanner().execute();
  }

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

  private List<LoadedCanonicalResource> resources = new ArrayList<>();
  private List<LoadedCanonicalResource> resourcesR4B = new ArrayList<>();
  private List<LoadedCanonicalResource> resourcesTHO = new ArrayList<>();
  private Map<String, List<LoadedCanonicalResource>> byOid = new HashMap<>();
  private Map<String, List<LoadedCanonicalResource>> byUrl = new HashMap<>();
  private Map<String, List<String>> oidMap = new HashMap<>();

  private void execute() throws IOException {
    loadPackage("hl7.fhir.r2.core", resources);
    loadPackage("hl7.fhir.r3.core", resources);
    loadPackage("hl7.fhir.r4.core", resources);
    loadPackage("hl7.fhir.r4b.core", resourcesR4B);
//    loadPackage("hl7.fhir.r5.core");
    loadPackage("hl7.terminology.r4", resourcesTHO);    
    
    analyse();
  }

  private void analyse() throws UnsupportedEncodingException, FileNotFoundException, IOException {
    for (LoadedCanonicalResource lcr : resources) {
      for (String oid : getOids(lcr)) {
        if (!byOid.containsKey(oid)) {
          byOid.put(oid, new ArrayList<>());
        }
        byOid.get(oid).add(lcr);
      }
      if (!byUrl.containsKey(lcr.getResource().getUrl())) {
        byUrl.put(lcr.getResource().getUrl(), new ArrayList<>());
      }
      byUrl.get(lcr.getResource().getUrl()).add(0, lcr);
    }
    
    System.out.println("OID count = "+byOid.size());
    for (String oid : byOid.keySet()) {
      checkUnique(oid, byOid.get(oid));
    }
    
    System.out.println("URL count = "+byUrl.size());
    for (String url : byUrl.keySet()) {
      List<String> oids = collectOids(byUrl.get(url));
      if (oids.size() > 1) {
        System.out.println("The URL "+url+" has multiple OIDs: "+oids.toString());        
      }
      oidMap.put(url, oids);
    }
    
    CSVWriter csv = new CSVWriter(new FileOutputStream(Utilities.path("[tmp]", "r4b.csv")));
    csv.line("URL", "State", "New OID", "Old OIDS");
    System.out.println("R4B Changes");
    Set<String> usedOids = new HashSet<>();
    for (LoadedCanonicalResource lcr : resourcesR4B) {
      if (lcr.resource.getResourceType() == ResourceType.ValueSet || lcr.resource.getResourceType() == ResourceType.CodeSystem) {
        String url = lcr.getResource().getUrl();
        List<String> oids = getOids(lcr);
        String oid = null;
        if (oidMap.containsKey(url) && oidMap.get(url).size() > 0) {
          oid = oidMap.get(url).get(0);
        }
        if (oid == null) {
          String modUrl = getTHOUrl(url);
          LoadedCanonicalResource tho = getFromTho(modUrl);
          if (tho != null) {
            List<String> moids = getOids(tho);
            if (moids.size() > 0) {
              oid = moids.get(0);
            }
          }
        }
        if (oid == null) {
          if (oids.size() > 0 && !byOid.containsKey(oids.get(0))) {
            oid = oids.get(0);
          }
        }
        if (oid != null) {
          if (!usedOids.contains(oid)) {
            usedOids.add(oid);
          } else {
            System.out.println("duplicate OID "+oid);
            oid = null;
          }
        }
        if (oids.size() == 1 && oid != null && oid.equals(oids.get(0))) {
          csv.line(url, "same", oid, oids.get(0));
//          System.out.println(url+": "+oid+" stays the same");
        } else if (oid == null) {
//          System.out.println(url+": change to "+oid+" from "+oids);
          csv.line(url, "new", oid, oids.toString());
        } else {
          csv.line(url, "change", oid, oids.toString());
//          System.out.println(url+": change to "+oid+" from "+oids);
        }
      }
    }
    csv.close();
    processR5Source(new File("/Users/grahamegrieve/work/r5/source"));
    System.out.println("done");
  }

  private void processR5Source(File folder) {
    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        processR5Source(f);
      } else if (f.getName().endsWith(".json")) {
        try {
          Resource r = new JsonParser().parse(new FileInputStream(f));
          if (updateOids(r)) {
            OutputStream s = new FileOutputStream(f);
            new JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(s, r);
            s.close();
          }         
        } catch (Exception e) {
          System.out.println("Fail "+f+": " +e.getMessage());
        }        
      } else if (f.getName().endsWith(".xml")) {
        try {
          Resource r = new XmlParser().parse(new FileInputStream(f));
          if (updateOids(r)) {
            OutputStream s = new FileOutputStream(f);
            new XmlParser().setOutputStyle(OutputStyle.PRETTY).compose(s, r);
            s.close();
          }         
        } catch (Exception e) {
          System.out.println("Fail "+f+": " +e.getMessage());
        }
      }
    }
    
  }

  private boolean updateOids(Resource r) {
    if (r.getResourceType() == ResourceType.ValueSet || r.getResourceType() == ResourceType.CodeSystem) {
      CanonicalResource cr = (CanonicalResource) r;
      String url = cr.getUrl();
      List<String> oids = oidMap.get(url);
      if (oids != null && oids.size() > 0) {
        cr.getIdentifier().removeIf(i -> i.getValue().startsWith("urn:oid:"));
        boolean first = true;
        for (String oid : oids) {
          Identifier id = cr.addIdentifier().setValue("urn:oid:"+oid).setSystem("urn:ietf:rfc:3986");
          if (first) first = false; else id.setUse(IdentifierUse.OLD);
        }
        return true;
      } else {
        if (cr.getIdentifier().removeIf(i -> i.getValue().startsWith("urn:oid:") && byOid.containsKey(i.getValue().substring(8)))) {
          return true;
        }
      }
    }
    return false;
  }

  private String getTHOUrl(String url) {
    if (url.startsWith("http://hl7.org/fhir/ValueSet")) {
      return url.replace("http://hl7.org/fhir/ValueSet", "http://terminology.hl7.org/ValueSet");
    } else {
      return url.replace("http://hl7.org/fhir", "http://terminology.hl7.org/CodeSystem");
    }
  }

  private LoadedCanonicalResource getFromTho(String modUrl) {
    for (LoadedCanonicalResource lcr : resourcesTHO) {
      if (modUrl.equals(lcr.getResource().getUrl())) {
        return lcr;
      }
    }
    return null;
  }

  private List<String> collectOids(List<LoadedCanonicalResource> list) {
    List<String> oids = new ArrayList<>();
    for (LoadedCanonicalResource lcr : list) {
      for (String oid : getOids(lcr)) {
        if (!oids.contains(oid)) {
          oids.add(oid); 
        }
      }
    }
    return oids;
  }

  private void checkUnique(String oid, List<LoadedCanonicalResource> list) {
    List<String> canonicals = new ArrayList<>();
    for (LoadedCanonicalResource lcr : list) {
      if (!canonicals.contains(lcr.getResource().getUrl())) {
        canonicals.add(lcr.getResource().getUrl()); 
      }
    }
    boolean ok = true;
    if (canonicals.size() > 1) {
      if (canonicals.size() == 2) {
        String nv1 = canonicals.get(0).replace("http://terminology.hl7.org/ValueSet", "http://hl7.org/fhir/ValueSet");
        String nv2 = canonicals.get(0).replace("http://terminology.hl7.org/ValueSet", "http://hl7.org/fhir/ValueSet");
        if (nv1.equals(nv2)) {
         // System.out.println("The OID "+oid+" has been assigned the same valueset in both core and THO");          
        } else {
          System.out.println("The OID "+oid+" has been assigned to more than one resource: "+canonicals.toString());
          ok = false;
        }
      } else {
        System.out.println("The OID "+oid+" has been assigned to more than one resource: "+canonicals.toString());
        ok = false;
      }
    }
    if (!ok) {
      for (LoadedCanonicalResource lcr : list) {
        System.out.println("  "+lcr.getResource().getUrl()+"|"+lcr.getResource().getVersion()+" from "+lcr.getPid());        
      }
    }
  }

  private List<String> getOids(LoadedCanonicalResource lcr) {
    List<String> oids = new ArrayList<>();
    for (Identifier id : lcr.getResource().getIdentifier()) {
      if (id.hasValue() && id.getValue().startsWith("urn:oid:")) {
        oids.add(id.getValue().substring(8));
      }
    }
    for (org.hl7.fhir.r5.model.Extension ext : lcr.getResource().getExtension()) {
      if ("http://hl7.org/fhir/StructureDefinition/valueset-oid".equals(ext.getUrl())) {
        String v = ext.getValue().primitiveValue();
        if (v != null && v.startsWith("urn:oid:")) {
          oids.add(v.substring(8));
        }
      }
      if ("http://hl7.org/fhir/StructureDefinition/codesystem-oid".equals(ext.getUrl())) {
        String v = ext.getValue().primitiveValue();
        if (v != null && v.startsWith("urn:oid:")) {
          oids.add(v.substring(8));
        }
      }
    }
    return oids;
  }

  private void loadPackage(String pid, List<LoadedCanonicalResource> reslist) throws IOException {
    System.out.println("Load Package "+pid);
    FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager.Builder().build();
    NpmPackage npm = pcm.loadPackage(pid);
//    SpecMapManager spm = new SpecMapManager(TextFile.streamToBytes(npm.load("other", "spec.internals")), npm.fhirVersion());
    IContextResourceLoader loader = new PublisherLoader(npm, null, npm.getWebLocation(), null, false).makeLoader();
    String[] types = new String[] { "StructureDefinition", "CodeSystem", "ValueSet", "SearchParameter", "OperationDefinition", "Questionnaire", "ConceptMap", "StructureMap", "NamingSystem" };
    for (String s : npm.listResources(types)) {
      Resource r = loader.loadResource(npm.load("package", s), true);
      if (r instanceof CanonicalResource) {
        reslist.add(new LoadedCanonicalResource((CanonicalResource) r, npm.name()+"#"+npm.version()));
      }
      for (CodeSystem cs : loader.getCodeSystems()) {
        reslist.add(new LoadedCanonicalResource(cs, npm.name()+"#"+npm.version()));        
      }
    } 
  }
  
}
