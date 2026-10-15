package org.hl7.fhir.igtools.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

  @Test
  void folderInsideWebsiteRootIsAccepted() {
    assertDoesNotThrow(() -> new IGReleaseRedirectionBuilder(publishedFolder, CANONICAL, VPATH, webRoot));
  }

  @Test
  void publishedFolderOutsideWebsiteRootIsRejected() {
    assertThrows(Error.class, () -> new IGReleaseRedirectionBuilder(stagingFolder, CANONICAL, VPATH, webRoot));
  }
}
