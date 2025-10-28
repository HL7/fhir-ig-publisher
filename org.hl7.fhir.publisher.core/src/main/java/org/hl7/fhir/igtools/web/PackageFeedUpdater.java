package org.hl7.fhir.igtools.web;

import org.hl7.fhir.utilities.xml.XMLUtil;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.*;
import com.google.gson.*;

/**
 * Scans a directory tree for npm package files (.tgz) and updates package-feed.xml
 * with any missing packages.
 */
public class PackageFeedUpdater {

  private static final String ROOT_DIR = "/Users/grahamegrieve/web/www.hl7.org.fhir";
  private static final String FEED_FILE = "/Users/grahamegrieve/web/www.hl7.org.fhir/package-feed.xml";
  private static final SimpleDateFormat RFC822_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

  static class PackageInfo {
    String name;
    String version;
    String fhirVersion;
    Date date;
    String filePath;
    String description;

    String getId() {
      return name + "#" + version;
    }

    @Override
    public String toString() {
      return getId() + " (" + filePath + ")";
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Starting package feed update...");
    System.out.println("Root directory: " + ROOT_DIR);
    System.out.println("Feed file: " + FEED_FILE);
    System.out.println();

    // Step 1: Scan for all .tgz files
    System.out.println("Step 1: Scanning for .tgz files...");
    List<PackageInfo> allPackages = scanForPackages(ROOT_DIR);
    System.out.println("Found " + allPackages.size() + " package files");
    System.out.println();

    // Step 2: Load existing feed
    System.out.println("Step 2: Loading existing feed...");
    Set<String> existingPackages = loadExistingPackages(FEED_FILE);
    System.out.println("Feed contains " + existingPackages.size() + " packages");
    System.out.println();

    // Step 3: Find missing packages
    System.out.println("Step 3: Finding missing packages...");
    List<PackageInfo> missingPackages = new ArrayList<>();
    Map<String, List<String>> duplicatePaths = new HashMap<>();

    for (PackageInfo pkg : allPackages) {
      String id = pkg.getId();
      if (!existingPackages.contains(id)) {
        missingPackages.add(pkg);
      } else {
        // Track packages that exist in feed but found at different paths
        if (!duplicatePaths.containsKey(id)) {
          duplicatePaths.put(id, new ArrayList<>());
        }
        duplicatePaths.get(id).add(pkg.filePath);
      }
    }

    if (!duplicatePaths.isEmpty()) {
      System.out.println("Note: Found " + duplicatePaths.size() + " packages already in feed at different paths:");
      int count = 0;
      for (Map.Entry<String, List<String>> entry : duplicatePaths.entrySet()) {
        if (count < 5) { // Show first 5 examples
          System.out.println("  - " + entry.getKey() + " (found at " + entry.getValue().size() + " location(s))");
          count++;
        }
      }
      if (duplicatePaths.size() > 5) {
        System.out.println("  ... and " + (duplicatePaths.size() - 5) + " more");
      }
      System.out.println();
    }

    if (missingPackages.isEmpty()) {
      System.out.println("No missing packages found. Feed is up to date!");
      return;
    }

    System.out.println("Found " + missingPackages.size() + " missing packages:");
    for (PackageInfo pkg : missingPackages) {
      System.out.println("  - " + pkg);
    }
    System.out.println();

    // Step 4: Add missing packages to feed
    System.out.println("Step 4: Adding missing packages to feed...");
    addPackagesToFeed(FEED_FILE, missingPackages);
    System.out.println("Feed updated successfully!");
    System.out.println();

    System.out.println("Summary:");
    System.out.println("  Total packages found: " + allPackages.size());
    System.out.println("  Previously in feed: " + existingPackages.size());
    System.out.println("  Added to feed: " + missingPackages.size());
  }

  /**
   * Scans directory tree for all .tgz files and extracts package info
   */
  private static List<PackageInfo> scanForPackages(String rootDir) throws IOException {
    List<PackageInfo> packages = new ArrayList<>();
    Path root = Paths.get(rootDir);

    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (file.toString().endsWith(".tgz")) {
          try {
            PackageInfo info = extractPackageInfo(file);
            if (info != null) {
              packages.add(info);
            }
          } catch (Exception e) {
            System.err.println("Warning: Failed to extract info from " + file + ": " + e.getMessage());
          }
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFileFailed(Path file, IOException exc) {
        // Skip files we can't access
        return FileVisitResult.CONTINUE;
      }
    });

    return packages;
  }

  /**
   * Extracts package name, version, and date from a .tgz file
   */
  private static PackageInfo extractPackageInfo(Path tgzFile) throws IOException {
    try (FileInputStream fis = new FileInputStream(tgzFile.toFile());
         GZIPInputStream gis = new GZIPInputStream(fis);
         TarArchiveInputStream tis = new TarArchiveInputStream(gis)) {

      TarArchiveEntry entry;
      while ((entry = tis.getNextTarEntry()) != null) {
        // Look for package/package.json or package.json
        if (entry.getName().equals("package/package.json") ||
                entry.getName().equals("package.json")) {

          // Read the package.json content
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          byte[] buffer = new byte[8192];
          int len;
          while ((len = tis.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
          }

          String jsonContent = baos.toString("UTF-8");
          return parsePackageJson(jsonContent, tgzFile);
        }
      }
    }
    return null;
  }

  /**
   * Parses package.json content and creates PackageInfo
   */
  private static PackageInfo parsePackageJson(String jsonContent, Path tgzFile) {
    try {
      Gson gson = new Gson();
      JsonObject json = gson.fromJson(jsonContent, JsonObject.class);

      PackageInfo info = new PackageInfo();
      info.name = json.get("name").getAsString();
      info.version = json.get("version").getAsString();
      info.filePath = tgzFile.toString();

      // Get FHIR version if available
      if (json.has("fhirVersions") && json.get("fhirVersions").isJsonArray()) {
        JsonArray versions = json.getAsJsonArray("fhirVersions");
        if (versions.size() > 0) {
          info.fhirVersion = versions.get(0).getAsString();
        }
      }

      // Get description if available
      if (json.has("description")) {
        info.description = json.get("description").getAsString();
      }

      // Try to get date from file modification time
      try {
        BasicFileAttributes attrs = Files.readAttributes(tgzFile, BasicFileAttributes.class);
        info.date = new Date(attrs.lastModifiedTime().toMillis());
      } catch (IOException e) {
        info.date = new Date(); // Use current date as fallback
      }

      return info;
    } catch (Exception e) {
      System.err.println("Warning: Failed to parse package.json from " + tgzFile + ": " + e.getMessage());
      return null;
    }
  }

  /**
   * Loads existing package IDs (name#version) from the feed XML
   */
  private static Set<String> loadExistingPackages(String feedFile) throws Exception {
    Set<String> packages = new HashSet<>();

    DocumentBuilderFactory factory =  XMLUtil.newXXEProtectedDocumentBuilderFactory();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.parse(new File(feedFile));

    NodeList items = doc.getElementsByTagName("item");
    for (int i = 0; i < items.getLength(); i++) {
      Element item = (Element) items.item(i);
      NodeList titles = item.getElementsByTagName("title");
      if (titles.getLength() > 0) {
        String title = titles.item(0).getTextContent().trim();
        // Store the name#version (which is what's in the title)
        packages.add(title);
      }
    }

    return packages;
  }

  /**
   * Adds missing packages to the feed XML
   */
  private static void addPackagesToFeed(String feedFile, List<PackageInfo> packages) throws Exception {
    DocumentBuilderFactory factory =  XMLUtil.newXXEProtectedDocumentBuilderFactory();
    factory.setNamespaceAware(true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.parse(new File(feedFile));

    // Find the channel element
    NodeList channels = doc.getElementsByTagName("channel");
    if (channels.getLength() == 0) {
      throw new Exception("No channel element found in feed");
    }
    Element channel = (Element) channels.item(0);

    // Update lastBuildDate
    NodeList lastBuildDates = channel.getElementsByTagName("lastBuildDate");
    if (lastBuildDates.getLength() > 0) {
      lastBuildDates.item(0).setTextContent(RFC822_FORMAT.format(new Date()));
    }

    // Update pubDate (only the one that's a direct child of channel)
    NodeList pubDates = channel.getElementsByTagName("pubDate");
    if (pubDates.getLength() > 0 && pubDates.item(0).getParentNode() == channel) {
      pubDates.item(0).setTextContent(RFC822_FORMAT.format(new Date()));
    }

    // Sort packages by date (newest first)
    packages.sort((a, b) -> b.date.compareTo(a.date));

    // Add each package as a new item at the end (before the closing </channel>)
    Set<String> ids = new HashSet<>();
    for (PackageInfo pkg : packages) {
      String id = pkg.name+"#"+pkg.version;
      if (!ids.contains(id)) {
        ids.add(id);
        Element item = createItemElement(doc, pkg);
        channel.appendChild(doc.createTextNode("\n    "));
        channel.appendChild(item);
      }
    }

    // Add final newline before closing channel tag
    channel.appendChild(doc.createTextNode("\n  "));

    // Save the updated document
    saveDocument(doc, feedFile);
  }

  /**
   * Creates an XML item element for a package
   */
  private static Element createItemElement(Document doc, PackageInfo pkg) {
    Element item = doc.createElement("item");

    // Add newlines and indentation for formatting
    item.appendChild(doc.createTextNode("\n      "));

    // Title
    Element title = doc.createElement("title");
    title.setTextContent(pkg.getId());
    item.appendChild(title);
    item.appendChild(doc.createTextNode("\n      "));

    // Description
    Element description = doc.createElement("description");
    description.setTextContent(pkg.description != null ? pkg.description : "Package " + pkg.getId());
    item.appendChild(description);
    item.appendChild(doc.createTextNode("\n      "));

    // Link - convert file path to URL
    String url = convertFilePathToUrl(pkg.filePath);
    Element link = doc.createElement("link");
    link.setTextContent(url);
    item.appendChild(link);
    item.appendChild(doc.createTextNode("\n      "));

    // GUID
    Element guid = doc.createElement("guid");
    guid.setAttribute("isPermaLink", "true");
    guid.setTextContent(url);
    item.appendChild(guid);
    item.appendChild(doc.createTextNode("\n      "));

    // Creator
    Element creator = doc.createElementNS("http://purl.org/dc/elements/1.1/", "dc:creator");
    creator.setTextContent("HL7, Inc");
    item.appendChild(creator);
    item.appendChild(doc.createTextNode("\n      "));

    // FHIR Version
    if (pkg.fhirVersion != null) {
      Element fhirVersion = doc.createElementNS("http://hl7.org/fhir/feed", "fhir:version");
      fhirVersion.setTextContent(pkg.fhirVersion);
      item.appendChild(fhirVersion);
      item.appendChild(doc.createTextNode("\n      "));
    }

    // Kind
    Element kind = doc.createElementNS("http://hl7.org/fhir/feed", "fhir:kind");
    kind.setTextContent("IG");
    item.appendChild(kind);
    item.appendChild(doc.createTextNode("\n      "));

    // PubDate
    Element pubDate = doc.createElement("pubDate");
    pubDate.setTextContent(RFC822_FORMAT.format(pkg.date));
    item.appendChild(pubDate);
    item.appendChild(doc.createTextNode("\n      "));

    // Details
    Element details = doc.createElementNS("http://hl7.org/fhir/feed", "fhir:details");
    details.setTextContent("Added from file system scan on " + new SimpleDateFormat("dd/MM/yyyy").format(new Date()));
    item.appendChild(details);
    item.appendChild(doc.createTextNode("\n    "));

    return item;
  }

  /**
   * Converts a file path to a URL
   */
  private static String convertFilePathToUrl(String filePath) {
    // Remove the root directory prefix
    String relativePath = filePath.replace("/Users/grahamegrieve/web/www.hl7.org.fhir/", "");

    // Convert to URL
    String url = "http://hl7.org/fhir/" + relativePath;

    return url;
  }

  /**
   * Saves the document back to file with proper formatting
   */
  private static void saveDocument(Document doc, String filePath) throws Exception {
    TransformerFactory transformerFactory = XMLUtil.newXXEProtectedTransformerFactory();
    Transformer transformer = transformerFactory.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "no"); // We handle indentation manually
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

    DOMSource source = new DOMSource(doc);

    // Create backup
    File original = new File(filePath);
    File backup = new File(filePath + ".backup");
    if (original.exists()) {
      Files.copy(original.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
      System.out.println("Created backup: " + backup.getPath());
    }

    // Save to file
    StreamResult result = new StreamResult(new File(filePath));
    transformer.transform(source, result);
  }
}
