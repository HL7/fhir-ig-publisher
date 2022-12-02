package org.hl7.fhir.igtools.publisher.utils;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;


public class IndexMaintainer {

  public class IGIndexInformation {
    private String id;
    private String name;
    private String descMD;
    private Date dateMilestone;
    private String refMilestone;
    private String fvMilestone;
    private String verMilestone;
    private Date dateLatest;
    private String refLatest;
    private String fvLatest;
    private String verLatest;
    
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
  
  public IndexMaintainer(String realm, String name, String dest, String template) {
    this.realm = realm;
    this.name = name;
    this.dest = dest;
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
    String src = TextFile.fileToString(template);
    src = src.replace("{{name}}", name);
    src = src.replace("{{tbl.summary}}", tbl);
    src = src.replace("{{tbl.date}}", tblDate);
    TextFile.stringToFile(src, dest);
  }

  private String generateSummary() throws IOException {
    XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
    XhtmlNode tbl = div.table("grid");
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
      } else if (ig.dateMilestone.before(ig.dateLatest)) {
        tr.backgroundColor("#ffebeb");
      }
      tr.td().ah(ig.code()+"/history.html").tx(ig.code());
      XhtmlNode td = tr.td();
      td.b().tx(ig.name);
      td.br();
      td.markdown(ig.descMD, "Description for "+ig.name);
      td = tr.td();
      if (ig.refMilestone != null) {
        release(td, ig.dateMilestone, ig.verMilestone, ig.refMilestone, ig.fvMilestone);
      }
      td = tr.td();
      if (ig.dateMilestone == null || ig.dateMilestone.before(ig.dateLatest)) {
        release(td, ig.dateLatest, ig.verLatest, ig.refLatest, ig.fvLatest);
      }
    }
    return new XhtmlComposer(false).compose(div);
  }

  private void release(XhtmlNode td, Date date, String ver, String ref, String fv) {
    td.ah(ref).tx(ver);
    td.br();
    td.tx(fmtDateHuman(date));
    td.br();
    td.tx("v"+fv);
  }

  private String generateByDate() throws IOException {
    XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
    XhtmlNode tbl = div.table("grid");
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
      if (ig.dateMilestone == null || ig.dateMilestone.before(ig.dateLatest)) {
        tr.td().tx(ig.verLatest);
        tr.td().tx("Candidate");
        tr.td().tx(fmtDateFUll(ig.dateLatest));      
        tr.td().tx(Utilities.describeDuration(Duration.ofMillis(new Date().getTime() - ig.dateLatest.getTime())));        
      } else {
        tr.td().tx(ig.verMilestone);
        tr.td().tx("Publication");
        tr.td().tx(fmtDateFUll(ig.dateMilestone));      
        tr.td().tx(Utilities.describeDuration(Duration.ofMillis(new Date().getTime() - ig.dateMilestone.getTime())));
      }
    }
    return new XhtmlComposer(false).compose(div);
  }

  private String fmtDateHuman(Date date) {
    SimpleDateFormat df = new SimpleDateFormat("MMM-yyyy");
    return df.format(date);
  }

  private String fmtDateFUll(Date date) {
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    return df.format(date);
  }

  public void seeEntry(String id, JsonObject ig, JsonObject ver) throws ParseException {
    String name = ig.asString("title"); 
    String descMD = ig.asString("introduction");
    String version = ver.asString("version");
    String fhirVersion = ver.asString("fhirversion");
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    Date d = df.parse(ver.asString("date"));
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
    if (entry.dateLatest == null || entry.dateLatest.before(d)) {
      entry.dateLatest = d;
      entry.refLatest = ver.asString("path");
      entry.fvLatest = fhirVersion;
      entry.verLatest = version;
    }
  }
}
