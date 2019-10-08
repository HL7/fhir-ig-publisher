package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.igtools.publisher.utils.FeedBuilder.Publication;
import org.hl7.fhir.igtools.publisher.utils.FeedBuilder.PublicationSorter;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.JSONUtil;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class FeedBuilder {

  public class PublicationSorter implements Comparator<Publication> {

    @Override
    public int compare(Publication o1, Publication o2) {
      if (o1.date.equals(o2.date)) {
        return o1.packageId.compareTo(o2.packageId);
      } else {
        return -o1.date.compareTo(o2.date);
      }
    }
  }

  public class Publication {

    private String packageId;
    private String title;
    private String canonical;
    private String version;
    private String desc;
    private Date date;
    private String path;
    private String status;
    private String sequence;
    private String fhirversion;
    
    public Publication(String packageId, String title, String canonical, String version, String desc, String date, String path, String status, String sequence, String fhirversion) {
      super();
      this.packageId = packageId;
      this.title = title;
      this.canonical = canonical;
      this.version = version;
      this.desc = desc;
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      if (date.length() == 7)
        date = date + "-01";
      try {
        this.date = sdf.parse(date);
      } catch (ParseException e) {
        throw new Error("The date "+date+" is not valid");
      }
      this.path = path;
      this.status = status;
      this.sequence = sequence;
      this.fhirversion = fhirversion;
    }
    public String getPackageId() {
      return packageId;
    }
    public String getTitle() {
      return title;
    }
    public String getCanonical() {
      return canonical;
    }
    public String getVersion() {
      return version;
    }
    public String getDesc() {
      return desc;
    }
    public Date getDate() {
      return date;
    }
    public String getPath() {
      return path;
    }
    public String getStatus() {
      return status;
    }
    public String getSequence() {
      return sequence;
    }
    public String getFhirversion() {
      return fhirversion;
    }
    public String presentDate() {
      SimpleDateFormat sdf = new SimpleDateFormat("ddd, dd, MMMM yyyy hh:mm:ss z");
      // Wed, 04 Sep 2019 08:58:14 GMT      
      return sdf.format(date);
    }
    public String title() {
      return title;
    }
    public String desc() {
      return desc;
    }
    public String link(boolean forPackage) {
      if (forPackage)
        return Utilities.pathURL(path, "package.tgz");
      else
        return Utilities.pathURL(path, "index.html");
    }    
  }

  public void execute(String rootFolder, String feedFile, String orgName, String thisUrl, boolean forPackage) throws JsonSyntaxException, FileNotFoundException, IOException, ParseException {
    List<Publication> pubs = new ArrayList<>();
    
    scanFolder(new File(rootFolder), pubs);
    Collections.sort(pubs, new PublicationSorter());
    String s = buildFeed(pubs, orgName, thisUrl, forPackage);
    TextFile.stringToFile(s, feedFile);
  }

  private String buildFeed(List<Publication> pubs, String orgName, String thisUrl, boolean forPackage) {
    StringBuilder b = new StringBuilder();
    b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n");
    b.append("<rss xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:content=\"http://purl.org/rss/1.0/modules/content/\" xmlns:atom=\"http://www.w3.org/2005/Atom\" version=\"2.0\">\r\n");
    b.append("  <channel>\r\n");
    b.append("    <title>"+orgName+" FHIR Publications</title>\r\n");
    b.append("    <description>New publications by "+orgName+"></description>\r\n");
    b.append("    <link>"+thisUrl+"</link>\r\n");
    b.append("    <generator>HL7 FHIR Publication tooling</generator>\r\n");
    // "Fri, 20 Sep 2019 12:44:30 GMT"
    SimpleDateFormat df = new SimpleDateFormat("ddd, dd MMM YYYY hh:mm:ss z");
    b.append("    <lastBuildDate>"+df.format(new Date())+"</lastBuildDate>\r\n");
    b.append("    <atom:link href=\""+thisUrl+"\" rel=\"self\" type=\"application/rss+xml\"/>\r\n");
    b.append("    <pubDate>"+df.format(new Date())+"</pubDate>\r\n");
    b.append("    <language>en></language>\r\n");
    b.append("    <ttl>600</ttl>\r\n");
    
    for (Publication pub : pubs) {
      b.append("      <item>\r\n");
      b.append("        <title>"+Utilities.escapeXml(pub.title())+"</title>\r\n");
      b.append("        <description>"+Utilities.escapeXml(pub.desc())+"</description>\r\n");
      b.append("        <link>"+Utilities.escapeXml(pub.link(forPackage))+"</link>\r\n");
      b.append("        <guid isPermaLink=\"true\">"+Utilities.escapeXml(pub.link(forPackage))+"</guid>\r\n");
      b.append("        <dc:creator>"+orgName+"</dc:creator>\r\n");
      b.append("        <pubDate>"+pub.presentDate()+"</pubDate>\r\n");
      b.append("      </item>\r\n");
    }
    b.append("  </channel>\r\n");
    b.append("</rss>\r\n");
    return b.toString();
  }

  private void scanFolder(File folder, List<Publication> pubs) throws JsonSyntaxException, FileNotFoundException, IOException, ParseException {
    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        scanFolder(f, pubs);
      } else if (f.getName().equals("package-list.json")) {
        loadPackageList(f, pubs);
      }
    }    
  }

  private void loadPackageList(File f, List<Publication> pubs) throws JsonSyntaxException, FileNotFoundException, IOException, ParseException {
    System.out.println("Load from "+f.getAbsolutePath());
    JsonObject json = (JsonObject) new JsonParser().parse(TextFile.fileToString(f)); // use gson parser to preseve property order
    String packageId = JSONUtil.str(json, "package-id");
    String title = JSONUtil.str(json, "title");
    String canonical = JSONUtil.str(json, "canonical");
    for (JsonElement e : JSONUtil.forceArray(json, "list")) {
      JsonObject v = (JsonObject) e;
      String version = JSONUtil.str(v, "version");
      if (!"current".equals(version)) {
        String desc = JSONUtil.str(v, "desc");
        if (Utilities.noString(desc))
          desc = JSONUtil.str(v, "descmd");
        String date = JSONUtil.str(v, "date");
        String path = JSONUtil.str(v, "path");
        String status = JSONUtil.str(v, "status");
        String sequence = JSONUtil.str(v, "sequence");
        String fhirversion = JSONUtil.str(v, "fhirversion");
        pubs.add(new Publication(packageId, title, canonical, version, desc, date, path, status, sequence, fhirversion));
      }
    }


  }
  
  public static void main(String[] args) throws FileNotFoundException, IOException, JsonSyntaxException, ParseException {
    new FeedBuilder().execute("C:\\web\\hl7.org\\fhir", "C:\\web\\hl7.org\\fhir\\package-feed.xml", "HL7", "http://hl7.org/fhir/package-feed.xml", true);
    new FeedBuilder().execute("C:\\web\\hl7.org\\fhir", "C:\\web\\hl7.org\\fhir\\publication-feed.xml", "HL7", "http://hl7.org/fhir/publication-feed.xml", false);
  }
}
