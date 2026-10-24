package org.hl7.fhir.igtools.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.hl7.fhir.utilities.FileUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IGReleaseRedirectionBuilderTest {

  private static final String CANONICAL = "http://example.org/fhir/test";
  private static final String VPATH = "http://example.org/fhir/test";

  @TempDir
  Path temp;

  private String webRoot;
  private String publishedFolder;
  private String stagingFolder;

  @BeforeEach
  void setUp() throws IOException {
    // mirrors the publication process: the milestone build output lives outside the website root, and is
    // copied into the IG's folder in the website once the redirections have been generated
    webRoot = Files.createDirectories(temp.resolve("web-root").resolve("run-20261007")).toString();
    publishedFolder = Files.createDirectories(Path.of(webRoot, "ig", "test")).toString();
    stagingFolder = Files.createDirectories(temp.resolve("ig-builds").resolve("test#1.0.0-milestone").resolve("output")).toString();
    FileUtilities.stringToFile("{\"paths\": {\"" + CANONICAL + "/StructureDefinition/foo\": \"StructureDefinition-foo.html\"}}",
        new File(stagingFolder, "spec.internals"));
    FileUtilities.stringToFile("<StructureDefinition/>", new File(stagingFolder, "StructureDefinition-foo.xml"));
    FileUtilities.stringToFile("{}", new File(stagingFolder, "StructureDefinition-foo.json"));
  }

  @Test
  void buildsRedirectionsInStagingFolderOutsideWebsiteRoot() throws IOException {
    IGReleaseRedirectionBuilder rb = new IGReleaseRedirectionBuilder(stagingFolder, publishedFolder, CANONICAL, VPATH, webRoot);
    rb.buildApacheRedirections();
    assertTrue(new File(stagingFolder, "StructureDefinition/foo/index.php").exists());
  }

  @Test
  void aspRulesAreNamedAfterPublishedFolder() throws IOException {
    IGReleaseRedirectionBuilder rb = new IGReleaseRedirectionBuilder(stagingFolder, publishedFolder, CANONICAL, VPATH, webRoot);
    rb.buildNewAspRedirections(false, false);
    String webConfig = FileUtilities.fileToString(new File(stagingFolder, "web.config"));
    assertTrue(webConfig.contains("<rule name=\"ig.test.StructureDefinition\">"), webConfig);
    String asp = FileUtilities.fileToString(new File(stagingFolder, "crstructuredefinition.asp"));
    String publishedAsp = String.join(File.separator, "", "ig", "test", "crstructuredefinition.asp");
    assertTrue(asp.contains("(from " + publishedAsp + ")"), asp);
  }

  private static final String NEW_PAGE = "https://example.org/fhir/terminology/ValueSet-moved.html";
  private static final String NEW_SOURCE = "https://example.org/fhir/terminology/ValueSet-moved";

  /**
   * ValueSet/moved is now published in another IG (new-location), and no other ValueSet is left in this one
   */
  private void addNewLocations() throws IOException {
    FileUtilities.stringToFile("{\"paths\": {\"" + CANONICAL + "/StructureDefinition/foo\": \"StructureDefinition-foo.html\"}, " +
        "\"new-locations\": {\"ValueSet/moved\": {\"page\": \"" + NEW_PAGE + "\", \"source\": \"" + NEW_SOURCE + "\"}, " +
        "\"old-page\": {\"page\": \"https://example.org/fhir/terminology/old-page.html\"}}}",
        new File(stagingFolder, "spec.internals"));
  }

  @Test
  void aspRedirectorsSendMovedArtifactsToTheirNewLocationByFormat() throws IOException {
    addNewLocations();
    IGReleaseRedirectionBuilder rb = new IGReleaseRedirectionBuilder(stagingFolder, publishedFolder, CANONICAL, VPATH, webRoot);
    rb.buildNewAspRedirections(false, false);
    String webConfig = FileUtilities.fileToString(new File(stagingFolder, "web.config"));
    assertTrue(webConfig.contains("<rule name=\"ig.test.ValueSet\">"), webConfig);
    String asp = FileUtilities.fileToString(new File(stagingFolder, "crvalueset.asp"));
    int moved = asp.indexOf("if (id == \"moved\") {");
    assertTrue(moved > -1, asp);
    assertTrue(asp.contains("    Response.Redirect(\"" + NEW_SOURCE + ".json1\");"), asp);
    assertTrue(asp.contains("    Response.Redirect(\"" + NEW_PAGE + "\");"), asp);
    // before this IG's own redirects by format, which would send it to files that are no longer here
    assertTrue(moved < asp.indexOf("Response.Redirect(\"" + CANONICAL + "/ValueSet-\"+id+\".json2\")"), asp);
  }

  @Test
  void canonicalOfMovedArtifactRedirectsByFormat() throws IOException {
    addNewLocations();
    IGReleaseRedirectionBuilder rb = new IGReleaseRedirectionBuilder(stagingFolder, publishedFolder, CANONICAL, VPATH, webRoot);
    rb.buildApacheRedirections();
    String php = FileUtilities.fileToString(new File(stagingFolder, "ValueSet/moved/index.php"));
    assertTrue(php.contains("Redirect('" + NEW_SOURCE + ".json');"), php);
    assertTrue(php.contains("Redirect('" + NEW_SOURCE + ".xml');"), php);
    assertTrue(php.contains("Redirect('" + NEW_PAGE + "');"), php);
    rb.buildCloudRedirections();
    assertTrue(FileUtilities.fileToString(new File(stagingFolder, "ValueSet/moved/index.html")).contains("url=" + NEW_PAGE + "\""));
    // a moved page has no canonical URL
    assertFalse(new File(stagingFolder, "old-page").exists());
  }

  @Test
  void folderInsideWebsiteRootIsAccepted() {
    assertDoesNotThrow(() -> new IGReleaseRedirectionBuilder(publishedFolder, CANONICAL, VPATH, webRoot));
  }

  @Test
  void publishedFolderOutsideWebsiteRootIsRejected() {
    assertThrows(Error.class, () -> new IGReleaseRedirectionBuilder(stagingFolder, CANONICAL, VPATH, webRoot));
  }
}
