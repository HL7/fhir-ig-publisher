package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class ExtensionTracker {

  public class UsageSorter implements Comparator<ExtensionUsage> {

    @Override
    public int compare(ExtensionUsage l, ExtensionUsage r) {
      if (l.getExtension().equals(r.getExtension()))
        return l.getLocation().compareTo(r.getLocation());
      else 
        return l.getExtension().compareTo(r.getExtension());
    }
  }

  public class SDSorter implements Comparator<StructureDefinition> {

    @Override
    public int compare(StructureDefinition l, StructureDefinition r) {
      return l.getUrl().compareTo(r.getUrl());
    }
  }

  public class SD2Sorter implements Comparator<StructureDefinition> {

    @Override
    public int compare(StructureDefinition l, StructureDefinition r) {
      if (l.getType().equals(l.getType()))
        return l.getUrl().compareTo(r.getUrl());
      else
        return l.getType().compareTo(r.getType());
    }
  }

  public class ExtensionUsage {
    private boolean profile;
    private String extension;
    private String location;
    public ExtensionUsage(boolean profile, String extension, String location) {
      super();
      this.profile = profile;
      this.extension = extension;
      this.location = location;
    }
    public boolean isProfile() {
      return profile;
    }
    public String getExtension() {
      return extension;
    }
    public String getLocation() {
      return location;
    }

  }

  private static final boolean THREADED = true;

  private boolean optIn = true;
  private List<StructureDefinition> exts = new ArrayList<>();
  private List<StructureDefinition> profs = new ArrayList<>(); 
  private List<ExtensionUsage> usages = new ArrayList<>();
  private Map<String, Integer> useCount = new HashMap<>();
  private String version;
  private String fhirVersion;
  private String jurisdiction;
  private String packageId;


  public void scan(ImplementationGuide publishedIg) {
    version = publishedIg.getVersion();
    fhirVersion = publishedIg.getFhirVersion().get(0).asStringValue();
    jurisdiction = publishedIg.getJurisdictionFirstRep().getCodingFirstRep().getCode();
    packageId = publishedIg.getPackageId();
  }

  public void scan(StructureDefinition sd) {
    if (sd.getType().equals("Extension")) {
      exts.add(sd);      
    } else if (sd.getKind() == StructureDefinitionKind.RESOURCE) {
      profs.add(sd);            
    }

    //now, scan the definitions looking for references to extensions, and noting their context
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      if (ed.getType().size() == 1 && "Extension".equals(ed.getTypeFirstRep().getWorkingCode()) && ed.getTypeFirstRep().hasProfile()) {
        usages.add(new ExtensionUsage(true, ed.getTypeFirstRep().getProfile().get(0).asStringValue(), tail(ed.getPath())));
      }
    }

  }

  private String tail(String path) {
    return path.substring(0,  path.lastIndexOf("."));
  }

  public void scan(Element element, String origin) {
    String t = element.fhirType();
    if (!useCount.containsKey(t))
      useCount.put(t, 0);
    useCount.put(t, useCount.get(t)+1);
    scan(t, element, origin);
  }

  private void scan(String path, Element element, String origin) {
    for (Element e : element.getChildren()) {
      if (Utilities.existsInList(e.getName(), "extension", "modifierExtension")) {
        String url = e.getChildValue("url");
        if (url != null) {
          usages.add(new ExtensionUsage(false, url, path));
        }
      } else {
        scan(path+"."+e.getName(), e, origin);
      }
    }    
  }

  public  byte[] generate() throws IOException {
    Collections.sort(exts, new SDSorter());
    Collections.sort(profs, new SD2Sorter());
    Collections.sort(usages, new UsageSorter());

    JsonObject data = new JsonObject();
    data.addProperty("package", packageId);
    data.addProperty("version", version);
    data.addProperty("fhirVersion", fhirVersion);
    data.addProperty("jurisdiction", jurisdiction);

    JsonArray exts = new JsonArray();
    data.add("extensions", exts);
    for (StructureDefinition sd : this.exts) {
      JsonObject ext = new JsonObject();
      exts.add(ext);
      ext.addProperty("url", sd.getUrl());
      ext.addProperty("title", sd.present());
      JsonArray types = new JsonArray();
      for (ElementDefinition e : sd.getSnapshot().getElement()) {
        if (e.getPath().contains(".value"))
          for (TypeRefComponent t : e.getType())
            types.add(t.getWorkingCode());
      }
      if (types.size() > 0)
        ext.add("types", types);
    }


    JsonObject rl = new JsonObject();
    data.add("profiles", rl);
    String last = null;
    for (StructureDefinition sd : profs) {
      if (!sd.getType().equals(last)) {
        exts = new JsonArray();
        last = sd.getType();
        rl.add(last, exts);
      }
      JsonObject ext = new JsonObject();
      exts.add(ext);
      ext.addProperty("url", sd.getUrl());
      ext.addProperty("title", sd.present());
    }

    JsonObject uses = new JsonObject();
    data.add("usage", uses);

    Set<String> set = new HashSet<>();
    last = null;
    JsonArray use = null;    
    for (ExtensionUsage eu : usages) {
      if (!eu.getExtension().equals(last)) {
        use = new JsonArray();
        last = eu.getExtension();
        uses.add(last, use);
        set.clear();
      }
      if (!set.contains(eu.getLocation())) {
        set.add(eu.getLocation());
        use.add(eu.getLocation());
      }
    }
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    String json = gson.toJson(data);
    return FileUtilities.stringToBytes(json);
  }

  public boolean isoptIn() {
    return optIn;
  }

  public void setoptIn(boolean optIn) {
    this.optIn = optIn;
  }


  public void runAsThead(String address, byte[] ba) throws IOException {
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          send(address, ba);
        } catch (IOException e) {
          System.out.println("Sending usage stats failed: "+e.getMessage());
        }
      }
    }).start();
  }

  private void send(String address, byte[] ba) throws ClientProtocolException, IOException {
    HttpPost req = new HttpPost(address);
    req.addHeader("User-Agent", "IGPublisher");
    req.addHeader("Accept", "application/json");
    req.addHeader("Content-Type", "application/json");
    req.setHeader("Authorization", "Bearer 9D1C23C0-3915-4542-882E-2BCC55645DF8");
    @SuppressWarnings("deprecation")
    HttpClient httpclient = new DefaultHttpClient();
    //  if(proxy != null) {
    //  httpclient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
    //}
//    HttpResponse response = null;
    req.setEntity(new ByteArrayEntity(ba));
    httpclient.execute(req);
    httpclient.getConnectionManager().shutdown();
  }

  public void sendToServer(String address) throws IOException {
    if (optIn) {
      if (THREADED) {
        runAsThead(address, generate());
      } else {
        send(address, generate());        
      }
    }
  }

}
