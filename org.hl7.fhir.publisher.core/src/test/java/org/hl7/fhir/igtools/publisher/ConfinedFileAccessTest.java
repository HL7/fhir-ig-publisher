package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.filesystem.ManagedFileAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unless links out of the IG are allowed, a build's own file access is limited to the IG's folder, the temp folder,
 * ~/.fhir and the folders the person running the publisher chose
 */
class ConfinedFileAccessTest {

  @TempDir
  Path dir;

  @AfterEach
  void reset() {
    ManagedFileAccess.setAllowedPaths(null);
  }

  @Test
  void theThreePlacesAndWhatWasChosen() throws IOException {
    List<String> paths = PublisherIGLoader.allowedFileAccessPaths("/work/ig", "/cache/packages", null, "", "/etc/fhir-settings.json");
    assertTrue(paths.contains("/work/ig"));
    assertTrue(paths.contains(Utilities.path("[tmp]")));
    assertTrue(paths.contains(System.getProperty("java.io.tmpdir")));
    assertTrue(paths.contains(Utilities.path(System.getProperty("user.home"), ".fhir")));
    assertTrue(paths.contains("/cache/packages"));
    assertTrue(paths.contains("/etc/fhir-settings.json"));
    assertEquals(6, paths.size());
  }

  /** where the user has allowed links out of the IG, what they lead to is allowed, and nothing else */
  @Test
  void whatAllowedLinksLeadTo() throws IOException {
    Path ig = Files.createDirectories(dir.resolve("ig"));
    Path shared = Files.createDirectories(dir.resolve("shared"));
    Files.write(shared.resolve("a.md"), "a".getBytes(StandardCharsets.UTF_8));
    Path other = Files.createDirectories(dir.resolve("other"));
    Files.write(other.resolve("b.md"), "b".getBytes(StandardCharsets.UTF_8));
    Files.createDirectories(ig.resolve("input"));
    Files.createSymbolicLink(ig.resolve("input/shared"), shared);
    Files.createSymbolicLink(ig.resolve("input/broken"), dir.resolve("nowhere"));
    List<String> links = PublisherUtils.linksOut(ig.toString());
    List<String> targets = PublisherIGLoader.linkTargets(ig.toString(), links);
    assertEquals(List.of(shared.toRealPath().toString()), targets);
    List<String> paths = new java.util.ArrayList<>(List.of(ig.toString()));
    paths.addAll(targets);
    ManagedFileAccess.setAllowedPaths(paths);
    assertEquals("a", FileUtilities.fileToString(ig.resolve("input/shared/a.md").toString()));
    assertThrows(IOException.class, () -> FileUtilities.fileToString(other.resolve("b.md").toString()));
  }

  /** the settings file is allowed, not the folder it's in (on the ci-build, /etc, which has the key file too) */
  @Test
  void onlyTheSettingsFileNotItsFolder() throws IOException {
    Path etc = Files.createDirectories(dir.resolve("etc"));
    Path settings = etc.resolve("fhir-settings.json");
    Files.write(settings, "{}".getBytes(StandardCharsets.UTF_8));
    Files.write(etc.resolve("keyfile.ini"), "apikey=secret".getBytes(StandardCharsets.UTF_8));
    Path ig = Files.createDirectories(dir.resolve("ig"));
    // as allowedFileAccessPaths gives them, less the temp folder (which this test's folder is in)
    ManagedFileAccess.setAllowedPaths(List.of(ig.toString(), settings.toString()));
    assertEquals("{}", FileUtilities.fileToString(settings.toString()));
    assertThrows(IOException.class, () -> FileUtilities.fileToString(etc.resolve("keyfile.ini").toString()));
  }
}
