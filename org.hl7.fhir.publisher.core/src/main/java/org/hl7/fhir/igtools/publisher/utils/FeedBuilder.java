package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.utils.FeedBuilder.Publication;
import org.hl7.fhir.igtools.publisher.utils.FeedBuilder.PublicationSorter;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.json.JSONUtil;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class FeedBuilder {

  private static final String RSS_DATE = "EEE, dd MMM yyyy hh:mm:ss";

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
    private String kind;
    private String folder;
    private List<String> subPackages;
    
    public Publication(String packageId, String title, String canonical, String version, String desc, String date, String path, String status, String sequence, String fhirversion, String kind, String folder, List<String> subPackages) {
      super();
      this.packageId = packageId;
      this.title = title;
      this.canonical = canonical;
      this.version = version;
      this.desc = desc;
      this.folder = folder;
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", new Locale("en", "US"));
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
      this.kind = kind;
      this.subPackages = subPackages;
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
      SimpleDateFormat sdf = new SimpleDateFormat(RSS_DATE, new Locale("en", "US"));
      // Wed, 04 Sep 2019 08:58:14 GMT      
      return sdf.format(date)+" GMT";
    }
    public String title(boolean forPackage) {
      if (forPackage)
        return packageId+"#"+version;
      else
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
    public String fhirVersion() {
      return fhirversion;
    }
    public String kind() {
      return kind;
    }    
    public boolean isSemVer() {
      return version.matches("[0-9]+[.][0-9]+[a-z]?[.][0-9]+[a-z]?");
    }
    
    @Override
    public String toString() {
      return packageId+"#"+version + (!subPackages.isEmpty() ? subPackages.toString() : "");
    }
  }

  public void execute(String rootFolder, String packageFile, String publicationFile, String orgName, String thisUrl, String rootUrl) throws JsonSyntaxException, FileNotFoundException, IOException, ParseException {
    System.out.println("Build Feed. ");
    System.out.println("  rootFolder="+rootFolder);
    System.out.println("  packageFile="+packageFile);
    System.out.println("  publicationFile="+publicationFile);
    System.out.println("  orgName="+orgName);
    System.out.println("  thisUrl="+thisUrl);
    System.out.println("  rootUrl="+rootUrl);
    List<Publication> pubs = new ArrayList<>();
    
    scanFolder(new File(rootFolder), pubs, rootUrl, rootFolder);
    Collections.sort(pubs, new PublicationSorter());
    if (packageFile != null) {
      System.out.println("Save Package feed to "+packageFile);
      TextFile.stringToFile(buildFeed(pubs, orgName, thisUrl, true), packageFile);
    }
    if (publicationFile != null) {
      System.out.println("Save publication feed to "+publicationFile);
      TextFile.stringToFile(buildFeed(pubs, orgName, thisUrl, false), publicationFile);
    }
    Set<String> statusCodes = new HashSet<>();
    for (Publication pub : pubs) {
      statusCodes.add(pub.status);
    }
    System.out.println("Known status codes: "+statusCodes);
  }

  private String buildFeed(List<Publication> pubs, String orgName, String thisUrl, boolean forPackage) throws IOException {
    StringBuilder b = new StringBuilder();
    b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n");
    b.append("<rss xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:content=\"http://purl.org/rss/1.0/modules/content/\" "+
          "xmlns:fhir=\"http://hl7.org/fhir/feed\" xmlns:atom=\"http://www.w3.org/2005/Atom\" version=\"2.0\">\r\n");
    b.append("  <channel>\r\n");
    if (forPackage) {
      b.append("    <title>"+orgName+" FHIR Packages</title>\r\n");
      b.append("    <description>New Packages published by "+orgName+"></description>\r\n");
    } else {
      b.append("    <title>"+orgName+" FHIR Publications</title>\r\n");
      b.append("    <description>New publications by "+orgName+"></description>\r\n");      
    }
    b.append("    <link>"+thisUrl+"</link>\r\n");
    b.append("    <generator>HL7, Inc FHIR Publication tooling</generator>\r\n");
    // "Fri, 20 Sep 2019 12:44:30 GMT"
    SimpleDateFormat df = new SimpleDateFormat(RSS_DATE, new Locale("en", "US"));
    b.append("    <lastBuildDate>"+df.format(new Date())+" GMT"+"</lastBuildDate>\r\n");
    b.append("    <atom:link href=\""+thisUrl+"\" rel=\"self\" type=\"application/rss+xml\"/>\r\n");
    b.append("    <pubDate>"+df.format(new Date())+"</pubDate>\r\n");
    b.append("    <language>en></language>\r\n");
    b.append("    <ttl>600</ttl>\r\n");
    
    for (Publication pub : pubs) {
      if (forPackage && !pub.isSemVer()) {
        System.out.println("Ignoring package "+pub.title(forPackage)+" as the version ("+pub.getVersion()+") does not conform to semver");
      } else if (forPackage && !packageExists(pub.folder, pub.subPackages)) {
        System.out.println("Ignoring package "+pub.title(forPackage)+" as the actual package could not be found at "+pub.folder);
      } else {
        String desc = pub.desc();
        if (forPackage && new File(Utilities.path(pub.folder, "package.tgz")).exists()) {
          // open the package, check the details and get the description
          NpmPackage npm = NpmPackage.fromPackage(new FileInputStream(Utilities.path(pub.folder, "package.tgz")));
          if (!(npm.name()+"#"+npm.version()).equals(pub.title(forPackage)))
            System.out.println("id mismatch in "+Utilities.path(pub.folder, "package.tgz")+" - expected "+pub.title(forPackage)+" but found "+npm.name()+"#"+npm.version());
          desc = npm.description();
          String pver = npm.version();
          if (!pver.equals(pub.version)) {
            System.out.println("Version mismatch - package-list.json says "+pub.version+", actual package says "+pver);
          }
          
        }
        if (forPackage) {
          for (String s : pub.subPackages) {
            // open the package, check the details and get the description
            NpmPackage npm = NpmPackage.fromPackage(new FileInputStream(Utilities.path(pub.folder, s+".tgz")), Utilities.path(pub.folder, s+".tgz"));
            if (!npm.name().equals(s))
              System.out.println("id mismatch in "+Utilities.path(pub.folder, s+".tgz")+" - expected "+s+" but found "+npm.name());
            if (!npm.version().equals(pub.version))
              System.out.println("version mismatch in "+Utilities.path(pub.folder, s+".tgz")+" - expected "+pub.version+" but found "+npm.version());

            String url = Utilities.pathURL(root(pub.link(forPackage)), s+".tgz");
            b.append("    <item>\r\n");
            b.append("      <title>"+Utilities.escapeXml(s)+"</title>\r\n");
            b.append("      <description>"+Utilities.escapeXml(npm.description())+"</description>\r\n");
            b.append("      <link>"+Utilities.escapeXml(url)+"</link>\r\n");
            b.append("      <guid isPermaLink=\"true\">"+Utilities.escapeXml(url)+"</guid>\r\n");
            b.append("      <dc:creator>"+orgName+"</dc:creator>\r\n");
            b.append("      <fhir:version>"+pub.fhirVersion()+"</fhir:version>\r\n");
            b.append("      <fhir:kind>"+pub.kind()+"</fhir:kind>\r\n");
            b.append("      <pubDate>"+pub.presentDate()+"</pubDate>\r\n");
            b.append("    </item>\r\n"); 
          }
        }
        b.append("    <item>\r\n");
        b.append("      <title>"+Utilities.escapeXml(pub.title(forPackage))+"</title>\r\n");
        b.append("      <description>"+Utilities.escapeXml(desc)+"</description>\r\n");
        b.append("      <link>"+Utilities.escapeXml(pub.link(forPackage))+"</link>\r\n");
        b.append("      <guid isPermaLink=\"true\">"+Utilities.escapeXml(pub.link(forPackage))+"</guid>\r\n");
        b.append("      <dc:creator>"+orgName+"</dc:creator>\r\n");
        b.append("      <fhir:version>"+pub.fhirVersion()+"</fhir:version>\r\n");
        b.append("      <fhir:kind>"+pub.kind()+"</fhir:kind>\r\n");
        b.append("      <pubDate>"+pub.presentDate()+"</pubDate>\r\n");
        b.append("    </item>\r\n");
      }
    }
    b.append("  </channel>\r\n");
    b.append("</rss>\r\n");
    return b.toString();
  }

  private String root(String link) {
    return link.substring(0, link.lastIndexOf("/"));
  }

  private boolean packageExists(String folder, List<String> subPackages) throws IOException {
    if (subPackages.isEmpty()) {
      return new File(Utilities.path(folder, "package.tgz")).exists();
    } else {
      boolean ok = true;
      for (String s : subPackages) {
        ok = ok && new File(Utilities.path(folder, s+".tgz")).exists();
      }
      return ok;
    }
    
  }

  private void scanFolder(File folder, List<Publication> pubs, String rootUrl, String rootFolder) throws JsonSyntaxException, FileNotFoundException, IOException, ParseException {
    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        scanFolder(f, pubs, rootUrl, rootFolder);
      } else if (f.getName().equals("package-list.json")) {
        loadPackageList(f, pubs, rootUrl, rootFolder);
      }
    }    
  }

  private void loadPackageList(File f, List<Publication> pubs, String rootUrl, String rootFolder) throws JsonSyntaxException, FileNotFoundException, IOException, ParseException {
    System.out.println("Load from "+f.getAbsolutePath());
    String folder = Utilities.getDirectoryForFile(f.getAbsolutePath());    
    JsonObject json = (JsonObject) new JsonParser().parse(TextFile.fileToString(f)); // use gson parser to preseve property order
    String packageId = JSONUtil.str(json, "package-id");
    String title = JSONUtil.str(json, "title");
    String canonical = JSONUtil.str(json, "canonical");
    String kind = JSONUtil.str(json, "kind");
    if (Utilities.noString(kind))
      kind = "fhir.ig";
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
        if (Utilities.noString(fhirversion)) {
          if ("fhir.core".equals(kind))
            fhirversion = version;
          else
            System.out.println("No fhirVersion for "+version+" in "+f.getAbsolutePath());
        }
        List<String> subPackages = new ArrayList<>();
        if (v.has("sub-packages")) {
          for (JsonElement a : v.getAsJsonArray("sub-packages")) {
            subPackages.add(a.getAsString());
          }
        }
        pubs.add(new Publication(packageId, title, canonical, version, desc, date, path, status, sequence, fhirversion, kind, getCorrectPath(path, rootUrl, rootFolder), subPackages));
      }
    }
  }
  

  private String getCorrectPath(String path, String rootUrl, String rootFolder) throws IOException {
    path = path.substring(rootUrl.length());
    return Utilities.path(rootFolder, path);
  }

  public static void main(String[] args) throws FileNotFoundException, IOException, JsonSyntaxException, ParseException {
    new FeedBuilder().execute("C:\\web\\hl7.org\\fhir", "C:\\web\\hl7.org\\fhir\\package-feed.xml", "C:\\web\\hl7.org\\fhir\\publication-feed.xml", "HL7, Inc", "http://hl7.org/fhir/package-feed.xml", "http://hl7.org/fhir");
  }
}
