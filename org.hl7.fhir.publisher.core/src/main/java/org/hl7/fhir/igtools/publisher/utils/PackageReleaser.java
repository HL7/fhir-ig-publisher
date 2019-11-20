package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.json.JSONUtil;
import org.hl7.fhir.utilities.json.JsonTrackingParser;
import org.hl7.fhir.utilities.xml.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.google.gson.JsonObject;

public class PackageReleaser {
  private static final String RSS_DATE = "EEE, dd MMM yyyy hh:mm:ss";

  private String pid;
  private String version;
  private Document rss;
  private Element channel;
  private String linkRoot;

  private File xml;

  private String fhirKind;

  // 3 parameters: source of package, package dest folder, and release note
  public static void main(String[] args) throws Exception {
    new PackageReleaser().release(args[0], args[1], args[2]);
  }

  private void release(String source, String dest, String note) throws Exception {
    System.out.println("Publish Package at "+source+" to "+dest+". release note = "+note);
    checkPackage(source);
    checkDest(dest);
    checkNote(note);
    
    NpmPackage npm = NpmPackage.fromFolder(source);
    String fhirVersion = npm.fhirVersion();
    System.out.println("Package "+pid+"#"+version+" for FHIR Version "+fhirVersion);
    check(fhirVersion != null, "Could not determine FHIR version");
    
    Utilities.createDirectory(Utilities.path(dest, pid, version));
    npm.save(new FileOutputStream(Utilities.path(dest, pid, version, "package.tgz")));
    Element item = rss.createElement("item");
    List<Element> list = XMLUtil.getNamedChildren(channel, "item");
    Node txt = rss.createTextNode("\n    ");
    if (list.isEmpty()) {
      channel.appendChild(txt);
    } else {
      channel.insertBefore(txt, list.get(0));
    }
    channel.insertBefore(item, txt);
    addTextChild(item, "title", pid+"#"+version);
    addTextChild(item, "description", note);
    addTextChild(item, "link", Utilities.pathURL(linkRoot, pid, version, "package.tgz"));
    addTextChild(item, "guid", Utilities.pathURL(linkRoot, pid, version, "package.tgz")).setAttribute("isPermaLink", "true");
    addTextChild(item, "dc:creator", "FHIR Project");
    addTextChild(item, "fhir:version", fhirVersion);
    addTextChild(item, "fhir:kind", fhirKind);
    SimpleDateFormat df = new SimpleDateFormat(RSS_DATE);
    addTextChild(item, "pubDate", df.format(new Date()));
    txt = rss.createTextNode("\n    ");
    item.appendChild(txt);

    Element lbd = XMLUtil.getNamedChild(channel, "lastBuildDate");
    lbd.setTextContent(df.format(new Date()));
    File bak = new File(Utilities.changeFileExt(xml.getAbsolutePath(),  ".bak"));
    if (bak.exists())
      bak.delete();
    xml.renameTo(bak);
    saveXml(new FileOutputStream(xml));  
    System.out.println("Published");
  }

  private void saveXml(FileOutputStream stream) throws TransformerException, IOException {
    TransformerFactory factory = TransformerFactory.newInstance();
    Transformer transformer = factory.newTransformer();
    Result result = new StreamResult(stream);
    Source source = new DOMSource(rss);
    transformer.transform(source, result);    
    stream.flush();
  }
  private Element addTextChild(Element focus, String name, String text) {
    Node txt = rss.createTextNode("\n      ");
    focus.appendChild(txt);
    Element child = rss.createElement(name);
    focus.appendChild(child);
    child.setTextContent(text);
    return child;
  }

  private void checkPackage(String source) throws IOException {
    File f = new File(source);
    check(f.exists(), "Source "+source+" not found");
    check(f.isDirectory(), "Source "+source+" is not a directoyy");
    File p = new File(Utilities.path(source, "package", "package.json"));
    check(p.exists(), "Source Package "+p.getAbsolutePath()+" not found");
    check(!p.isDirectory(), "Source Package "+p.getAbsolutePath()+" is a directoyy");
    JsonObject pck = JsonTrackingParser.parseJson(p);
    pid = JSONUtil.str(pck, "name");
    version = JSONUtil.str(pck, "version");
    String tv = JSONUtil.str(pck, "tools-version");
    check("3".equals(tv), "Source Package "+p.getAbsolutePath()+" Package tools-version is not 3");
    check(pid != null, "Source Package "+p.getAbsolutePath()+" Package id not found");
    check(NpmPackage.isValidName(pid), "Source Package "+p.getAbsolutePath()+" Package id "+pid+" is not valid");
    check(version != null, "Source Package "+p.getAbsolutePath()+" Package version not found");
    check(NpmPackage.isValidVersion(version), "Source Package "+p.getAbsolutePath()+" Package version "+version+" is not valid");
    fhirKind = JSONUtil.str(pck, "type"); 
    check(fhirKind != null, "Source Package "+p.getAbsolutePath()+" Package type not found");
  }
    
  private void check(boolean condition, String message) {
    if (!condition) {
      throw new Error(message);
    }
  }

  private void checkDest(String dest) throws Exception {
    File f = new File(dest);
    check(f.exists(), "Destination "+dest+" not found");
    check(f.isDirectory(), "Source "+dest+" is not a directoyy");
    xml = new File(Utilities.path(dest, "package-feed.xml"));
    check(xml.exists(), "Destination rss "+xml.getAbsolutePath()+" not found");
    rss = loadXml(xml);
    channel = XMLUtil.getNamedChild(rss.getDocumentElement(), "channel");
    check(channel != null, "Destination rss "+xml.getAbsolutePath()+" channel not found");
    check("HL7 FHIR Publication tooling".equals(XMLUtil.getNamedChildText(channel, "generator")), "Destination rss "+xml.getAbsolutePath()+" channel.generator not correct");
    for (Element item : XMLUtil.getNamedChildren(channel, "item")) {
      check(!(pid+"#"+version).equals(XMLUtil.getNamedChildText(item, "title")), "Destination rss "+xml.getAbsolutePath()+" already contains a release for "+pid+"#"+version);
    }
    String link = XMLUtil.getNamedChildText(channel, "link");
    check(link != null, "Destination rss "+xml.getAbsolutePath()+" channel.link not found");
    linkRoot = link.substring(0, link.lastIndexOf("/"));
    
    File dst = new File(Utilities.path(dest, pid, version));
    check(!dst.exists(), "Implied destination "+dst.getAbsolutePath()+" already exists - check that a new version is being released.");  
  }
  
  private Document loadXml(File file) throws Exception {
    InputStream src = new FileInputStream(file);
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    DocumentBuilder db = dbf.newDocumentBuilder();
    return db.parse(src);
  }

  private void checkNote(String note) {
    check(note != null, "A release note must be provided");
  }

}
