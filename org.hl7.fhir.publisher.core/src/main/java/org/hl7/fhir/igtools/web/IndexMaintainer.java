package org.hl7.fhir.igtools.web;

import java.io.IOException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.JsonException;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;


public class IndexMaintainer {

  public class IGIndexInformation {
    private String id;
    private String name;
    private String descMD;
    private Instant dateMilestone;
    private String refMilestone;
    private String fvMilestone;
    private String verMilestone;
    private Instant dateLatest;
    private String refLatest;
    private String fvLatest;
    private String verLatest;
    private boolean withdrawn;
    
    public IGIndexInformation(String id) {
      this.id = id;
    }

    public String code() {
      return id.substring(id.lastIndexOf(".")+1);
    }
    
  }

  public class IdOrderSorter implements Comparator<IGIndexInformation> {

    @Override
    public int compare(IGIndexInformation l, IGIndexInformation r) {
      return l.id.compareTo(r.id);
    }

  }


  public class DateOrderSorter implements Comparator<IGIndexInformation> {

    @Override
    public int compare(IGIndexInformation l, IGIndexInformation r) {
      return -(l.dateLatest.compareTo(r.dateLatest));
    }

  }
  
  private Map<String, IGIndexInformation> igs = new HashMap<>();
  private String realm;
  private String name;
  private String dest;
  private String template;
  private String path;
  
  public IndexMaintainer(String realm, String name, String path, String dest, String template) {
    this.realm = realm;
    this.name = name;
    this.dest = dest;
    this.path = path;
    this.template = template;
  }

  public String getRealm() {
    return realm;
  }
  
  public void setRealm(String realm) {
    this.realm = realm;
  }
  
  public Map<String, IGIndexInformation> getIgs() {
    return igs;
  }
  
  public void execute() throws IOException {
    String tbl = generateSummary();
    String tblDate = generateByDate();
    String src = FileUtilities.fileToString(template);
    src = src.replace("{{name}}", name);
    src = src.replace("{{tbl.summary}}", tbl);
    src = src.replace("{{tbl.date}}", tblDate);
    FileUtilities.stringToFile(src, dest);
  }

  public void buildJson() throws IOException {
    JsonObject json = new JsonObject();
    JsonArray arr = json.forceArray("igs");
    for (String id : igs.keySet()) {
      IGIndexInformation ig = igs.get(id);      
      JsonObject o = new JsonObject();
      arr.add(o);
      o.set("name", ig.name);
      o.set("id", ig.id);
      o.set("descMD", ig.descMD);
      o.set("dateMilestone", ig.dateMilestone);
      o.set("refMilestone", ig.refMilestone);
      o.set("fvMilestone", ig.fvMilestone);
      o.set("verMilestone", ig.verMilestone);
      o.set("dateLatest", ig.dateLatest);
      o.set("refLatest", ig.refLatest);
      o.set("fvLatest", ig.fvLatest);
      o.set("verLatest", ig.verLatest);
    }
    String src = JsonParser.compose(json, true);
    FileUtilities.stringToFile(src, FileUtilities.changeFileExt(dest, ".json"));
  }

  public void loadJson() throws JsonException, IOException, ParseException {
    igs.clear();
    JsonObject json = JsonParser.parseObjectFromFile(FileUtilities.changeFileExt(dest, ".json"));
    for (JsonObject o : json.getJsonObjects("igs")) {
      IGIndexInformation ig = new IGIndexInformation(o.asString("id"));

      ig.name = o.asString("name");
      ig.descMD = o.asString("descMD");
      if (o.has("dateMilestone")) {
        ig.dateMilestone = o.asInstant("dateMilestone");
      }
      ig.refMilestone = o.asString("refMilestone");
      ig.fvMilestone = o.asString("fvMilestone");
      ig.verMilestone = o.asString("verMilestone");
      if (o.has("dateLatest")) {
        ig.dateLatest = o.asInstant("dateLatest");
      }
      ig.refLatest = o.asString("refLatest");
      ig.fvLatest = o.asString("fvLatest");
      ig.verLatest = o.asString("verLatest");
      igs.put(ig.id, ig);
    }
  }
  private String generateSummary() throws IOException {
    XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
    XhtmlNode tbl = div.table("grid", false);
    XhtmlNode tr = tbl.tr();
    tr.td().b().tx("IG");
    tr.td().b().tx("Details");
    tr.td().b().tx("Published");
    tr.td().b().tx("Candidate");

    List<IGIndexInformation> list = new ArrayList<>();
    list.addAll(igs.values());
    Collections.sort(list, new IdOrderSorter());
    for (IGIndexInformation ig : list) {
      tr = tbl.tr();
      if (ig.dateMilestone == null) {
        tr.backgroundColor("#fffce0");
      } else if (ig.withdrawn) {
        tr.backgroundColor("#eeeeee");        
      } else if (ig.dateMilestone.isBefore(ig.dateLatest)) {
        tr.backgroundColor("#ffebeb");
      }
      tr.td().ah(ig.code()+"/history.html").tx(ig.code());
      XhtmlNode td = tr.td();
      td.b().tx(ig.name);
      td.br();
      td.markdown(ig.descMD, "Description for "+ig.name);
      if (ig.withdrawn) {        
        td = tr.td();
        td.colspan(2);
        td.b().tx("Withdrawn");
      } else {
        td = tr.td();
        if (ig.refMilestone != null) {
          release(td, ig.dateMilestone, ig.verMilestone, ig.refMilestone, ig.fvMilestone);
        }
        td = tr.td();
        if (ig.dateMilestone == null || ig.dateMilestone.isBefore(ig.dateLatest)) {
          release(td, ig.dateLatest, ig.verLatest, ig.refLatest, ig.fvLatest);
        }
      }
    }
    return new XhtmlComposer(false).compose(div);
  }

  private void release(XhtmlNode td, Instant date, String ver, String ref, String fv) {
    td.ah(ref).tx(ver);
    td.br();
    td.tx(fmtDateHuman(date));
    td.br();
    td.tx("v"+fv);
  }

  private String generateByDate() throws IOException {
    XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
    XhtmlNode tbl = div.table("grid", false);
    XhtmlNode tr = tbl.tr();
    tr.td().b().tx("IG");
    tr.td().b().tx("Name");
    tr.td().b().tx("Version");
    tr.td().b().tx("Type");
    tr.td().b().tx("Date");
    tr.td().b().tx("Age");

    List<IGIndexInformation> list = new ArrayList<>();
    list.addAll(igs.values());
    Collections.sort(list, new DateOrderSorter());
    for (IGIndexInformation ig : list) {
      tr = tbl.tr();
      tr.td().ah(ig.code()+"/history.html").tx(ig.code());
      tr.td().tx(ig.name);
      if (ig.withdrawn) {
        tr.backgroundColor("#eeeeee");
        tr.td().colspan(2).tx("Withdrawn");
        tr.td().tx(fmtDateFUll(ig.dateLatest));      
        tr.td().tx(Utilities.describeDuration(Duration.between(ig.dateLatest, Instant.now())));                
      } else if (ig.dateMilestone == null || ig.dateMilestone.isBefore(ig.dateLatest)) {
        tr.td().tx(ig.verLatest);
        tr.td().tx("Candidate");
        tr.td().tx(fmtDateFUll(ig.dateLatest));      
        tr.td().tx(Utilities.describeDuration(Duration.between(ig.dateLatest, Instant.now())));        
      } else {
        tr.td().tx(ig.verMilestone);
        tr.td().tx("Publication");
        tr.td().tx(fmtDateFUll(ig.dateMilestone));      
        tr.td().tx(Utilities.describeDuration(Duration.between(ig.dateMilestone, Instant.now())));
      }
    }
    return new XhtmlComposer(false).compose(div);
  }

  private String fmtDateHuman(Instant date) {
    if (date == null) {
      return "";
    }
    DateTimeFormatter df = DateTimeFormatter.ofPattern("MMM-yyyy").withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());
    return df.format(date);
  }

  private String fmtDateFUll(Instant date) {
    if (date == null) {
      return "";
    }
    DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd").withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());
    return df.format(date);
  }

  public void seeEntry(String id, JsonObject ig, JsonObject ver) throws ParseException {
    String name = ig.asString("title"); 
    String descMD = ig.asString("introduction");
    String version = ver.asString("version");
    String fhirVersion = ver.asString("fhirversion");
    Instant d = ver.asInstant("date");
    boolean current = "true".equals(ver.asString("current"));
    
    IGIndexInformation entry = igs.get(id);
    if (entry == null) {
      entry = new IGIndexInformation(id);
      igs.put(id, entry);
      entry.name = name;
      entry.descMD = descMD;
    }
    if (current) {
      entry.dateMilestone = d;
      entry.refMilestone = ig.asString("canonical");
      entry.fvMilestone = fhirVersion;
      entry.verMilestone = version;      
    }  
    if (entry.dateLatest == null || entry.dateLatest.isBefore(d)) {
      entry.dateLatest = d;
      entry.refLatest = ver.asString("path");
      entry.fvLatest = fhirVersion;
      entry.verLatest = version;
    }
  }

  public void updateForPublication(PackageList pl, PackageListEntry plVer, boolean milestone) throws ParseException {
    IGIndexInformation ig = igs.get(pl.pid());
    if (ig == null) {
      ig = new IGIndexInformation(pl.pid());
      igs.put(ig.id, ig);
      ig.name = pl.title();
      ig.descMD = pl.intro();
    }
    if (milestone) {
      ig.dateMilestone = plVer.instant();
      ig.refMilestone = pl.canonical();
      ig.fvMilestone = plVer.fhirVersion();
      ig.verMilestone = plVer.version();      
      ig.dateLatest = plVer.instant();
      ig.refLatest = plVer.path();
      ig.fvLatest = plVer.version();
      ig.verLatest = plVer.version();
      ig.withdrawn = plVer.status().equals("withdrawn");
    } else if (ig.dateLatest == null || ig.dateLatest.isBefore(plVer.instant())) {
      ig.dateLatest = plVer.instant();
      ig.refLatest = plVer.path();
      ig.fvLatest = plVer.fhirVersion();
      ig.verLatest = plVer.version();
      ig.withdrawn = plVer.status().equals("withdrawn");
    }
  }

  public String path() {
    return path;
  }
}
