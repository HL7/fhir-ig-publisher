package org.hl7.fhir.igtools.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.hl7.fhir.utilities.FileUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseBinariesTest {

  private static final String PATH = "https://example.org/fhir/test/1.0.0";
  private static final String LOCATION = "https://github.com/example/test-ig/releases/download/v1.0.0";

  @TempDir
  Path temp;

  private String versionFolder;
  private String stagingFolder;

  @BeforeEach
  void setUp() throws IOException {
    versionFolder = Files.createDirectories(temp.resolve("web").resolve("test").resolve("1.0.0")).toString();
    stagingFolder = temp.resolve("binaries").resolve("example.test#1.0.0").toString();
    FileUtilities.stringToFile("zip", new File(versionFolder, "full-ig.zip"));
    FileUtilities.stringToFile("tgz", new File(versionFolder, "package.tgz"));
    FileUtilities.stringToFile("tgz", new File(versionFolder, "example.test.r4.tgz"));
    FileUtilities.stringToFile("zip", new File(versionFolder, "definitions.json.zip"));
    FileUtilities.stringToFile("<a href=\"full-ig.zip\">IG</a> <a href=\""+PATH+"/package.tgz\">package</a> <a href=\"definitions.json.zip\">definitions</a>",
        new File(versionFolder, "downloads.html"));
    FileUtilities.stringToFile("<p>nothing to download</p>", new File(versionFolder, "index.html"));
  }

  @Test
  void checksLocation() {
    assertNull(ReleaseBinaries.checkLocation(LOCATION, PATH));
    assertNotNull(ReleaseBinaries.checkLocation("http://github.com/example/test-ig/releases/download/v1.0.0", PATH));
    assertNotNull(ReleaseBinaries.checkLocation(LOCATION+"/", PATH));
    assertNotNull(ReleaseBinaries.checkLocation(PATH, PATH));
    assertNotNull(ReleaseBinaries.checkLocation(PATH+"/binaries", PATH));
    assertTrue(ReleaseBinaries.isGitHubRelease(LOCATION));
    assertFalse(ReleaseBinaries.isGitHubRelease("https://binaries.example.org/test/1.0.0"));
  }

  @Test
  void fillsTheLayoutRulePattern() {
    // the parts of the package id ({1}, {2}, ...) are filled in by the layout rule processing first
    assertEquals("https://github.com/hl7-be/fhir-binaries/releases/download/hl7.fhir.be.core-1.2.0",
        ReleaseBinaries.fromPattern("https://github.com/hl7-be/fhir-binaries/releases/download/{package-id}-{version}", "hl7.fhir.be.core", "1.2.0"));
    assertEquals("https://github.com/hl7-be/core/releases/download/v1.2.0",
        ReleaseBinaries.fromPattern("https://github.com/hl7-be/core/releases/download/v{version}", "hl7.fhir.be.core", "1.2.0"));
    assertNotNull(ReleaseBinaries.checkLocation("https://github.com/hl7-be/{5}/releases/download/v1.2.0", PATH));
  }

  @Test
  void stagesOnlyTheReleaseBinaries() throws IOException {
    List<String> names = ReleaseBinaries.stage(versionFolder, stagingFolder, false);
    assertEquals(3, names.size(), names.toString());
    for (String name : List.of("full-ig.zip", "package.tgz", "example.test.r4.tgz")) {
      assertTrue(new File(stagingFolder, name).exists(), name);
      assertFalse(new File(versionFolder, name).exists(), name);
    }
    assertTrue(new File(versionFolder, "definitions.json.zip").exists());
  }

  @Test
  void relinksPagesToTheBinariesLocation() throws IOException {
    List<String> names = ReleaseBinaries.stage(versionFolder, stagingFolder, false);
    assertEquals(1, ReleaseBinaries.relink(versionFolder, PATH, LOCATION, names));
    String html = FileUtilities.fileToString(new File(versionFolder, "downloads.html"));
    assertTrue(html.contains("href=\""+LOCATION+"/full-ig.zip\""), html);
    assertTrue(html.contains("href=\""+LOCATION+"/package.tgz\""), html);
    assertTrue(html.contains("href=\"definitions.json.zip\""), html);
  }

  @Test
  void givesGitHubUploadCommand() {
    String s = ReleaseBinaries.instructions(stagingFolder, LOCATION, List.of("full-ig.zip", "package.tgz"));
    assertTrue(s.contains("gh release upload v1.0.0 full-ig.zip package.tgz --repo example/test-ig"), s);
  }
}
