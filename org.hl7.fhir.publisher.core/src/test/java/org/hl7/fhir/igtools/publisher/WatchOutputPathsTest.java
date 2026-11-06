package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.hl7.fhir.utilities.logging.ILoggingService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WatchOutputPathsTest {

  @TempDir
  Path dir;

  private String p(String... parts) {
    return dir.resolve(String.join(File.separator, parts)).toString();
  }

  @Test
  void nestedFolders() {
    assertTrue(PublisherIGLoader.nested(p("temp", ".watch-output"), p("temp", ".watch-output")));
    // output is the IG's root folder, or inside the staging folder
    assertTrue(PublisherIGLoader.nested(p("temp", ".watch-output"), p()));
    assertTrue(PublisherIGLoader.nested(p("temp", ".watch-output"), p("temp", ".watch-output", "out")));
    assertTrue(PublisherIGLoader.nested(p("temp", ".watch-output"), p("x", "..", "temp")));
    assertFalse(PublisherIGLoader.nested(p("temp", ".watch-output"), p("output")));
    assertFalse(PublisherIGLoader.nested(p("temp", ".watch-output"), p("temp", ".watch-output2")));
    assertFalse(PublisherIGLoader.nested(p("temp", ".watch-output"), p("temp", "output")));
  }

  @Test
  void pathsInTheOutputFolderAreWrittenInTheStagingFolder() {
    String staging = p("temp", ".watch-output");
    assertEquals(p("temp", ".watch-output", "qa-tx.html"), Publisher.stagedPath(p("output", "qa-tx.html"), p("output"), staging));
    assertEquals(p("temp", ".watch-output"), Publisher.stagedPath(p("output"), p("output"), staging));
    assertEquals(p("temp", ".watch-output", "qa-tx.html"), Publisher.stagedPath(p("x", "..", "output", "qa-tx.html"), p("output"), staging));
    // elsewhere, or not in watch mode
    assertEquals(p("output2", "a.html"), Publisher.stagedPath(p("output2", "a.html"), p("output"), staging));
    assertEquals(p("output", "a.html"), Publisher.stagedPath(p("output", "a.html"), null, staging));
  }

  @Test
  void clearingTempInWatchModeKeepsTheStagingFolder() throws Exception {
    Path temp = dir.resolve("temp");
    Files.createDirectories(temp.resolve(PublisherIGLoader.WATCH_OUTPUT_STAGING));
    Files.writeString(temp.resolve(PublisherIGLoader.WATCH_OUTPUT_STAGING).resolve("index.html"), "staged");
    Files.createDirectories(temp.resolve("_includes"));
    Files.writeString(temp.resolve("_includes").resolve("old.xhtml"), "old");
    Files.writeString(temp.resolve("old.md"), "old");

    clearTemp(temp, p("output"));

    assertEquals("staged", Files.readString(temp.resolve(PublisherIGLoader.WATCH_OUTPUT_STAGING).resolve("index.html")));
    assertFalse(Files.exists(temp.resolve("old.md")));
    assertFalse(Files.exists(temp.resolve("_includes").resolve("old.xhtml")));
    assertTrue(Files.isDirectory(temp.resolve("_includes")));
  }

  @Test
  void clearingTempOutsideWatchModeClearsEverything() throws Exception {
    Path temp = dir.resolve("temp");
    Files.createDirectories(temp.resolve(PublisherIGLoader.WATCH_OUTPUT_STAGING));
    Files.writeString(temp.resolve(PublisherIGLoader.WATCH_OUTPUT_STAGING).resolve("index.html"), "staged");

    clearTemp(temp, null);

    assertFalse(Files.exists(temp.resolve(PublisherIGLoader.WATCH_OUTPUT_STAGING)));
    assertTrue(Files.isDirectory(temp.resolve("_data")));
  }

  private void clearTemp(Path temp, String watchOutputDir) throws Exception {
    PublisherFields pf = new PublisherFields();
    pf.logger = mock(ILoggingService.class);
    pf.tempDir = temp.toString();
    pf.watchOutputDir = watchOutputDir;
    PublisherIGLoader loader = new PublisherIGLoader(new PublisherSettings());
    loader.setPf(pf);
    loader.clearTempFolder();
  }

  @Test
  void inWatchModeTheBuildWritesToTheStagingFolder() throws Exception {
    PublisherFields pf = watchSetUp(true, p("output"), p("output", "qa"));
    assertEquals(p("output"), pf.watchOutputDir);
    assertEquals(p("temp", ".watch-output"), pf.outputDir);
    assertEquals(p("temp", ".watch-output", "qa"), pf.qaDir);
    assertTrue(new File(pf.outputDir).isDirectory());
  }

  @Test
  void anOutputFolderThatHoldsTheStagingFolderIsNotSynced() throws Exception {
    // the sync would delete everything in it that the build didn't write
    for (String output : new String[] {p(), p("temp"), p("temp", ".watch-output", "out")}) {
      PublisherFields pf = watchSetUp(true, output, null);
      assertEquals(null, pf.watchOutputDir, output);
      assertEquals(output, pf.outputDir);
    }
  }

  @Test
  void withoutWatchModeAStagingFolderLeftByAWatchSessionIsDeleted() throws Exception {
    Files.createDirectories(dir.resolve("temp").resolve(".watch-output"));
    Files.writeString(dir.resolve("temp").resolve(".watch-output").resolve("index.html"), "staged");
    PublisherFields pf = watchSetUp(false, p("output"), null);
    assertEquals(null, pf.watchOutputDir);
    assertEquals(p("output"), pf.outputDir);
    assertFalse(Files.exists(dir.resolve("temp").resolve(".watch-output")));
  }

  @Test
  void aFailedBuildPutsItsQaJsonInTheOutputFolder() throws Exception {
    Path staging = Files.createDirectories(dir.resolve("staging"));
    Path output = Files.createDirectories(dir.resolve("output"));
    Files.writeString(staging.resolve("qa.json"), "failed");
    Files.writeString(output.resolve("qa.json"), "succeeded");
    Files.writeString(output.resolve("index.html"), "last good");

    Publisher.recordFailure(staging, output);

    assertEquals("failed", Files.readString(output.resolve("qa.json")));
    assertEquals("last good", Files.readString(output.resolve("index.html")));
  }

  @Test
  void aFailedBuildWithoutAQaJsonRemovesTheLastOne() throws Exception {
    Path staging = Files.createDirectories(dir.resolve("staging"));
    Path output = Files.createDirectories(dir.resolve("output"));
    Files.writeString(output.resolve("qa.json"), "succeeded");

    Publisher.recordFailure(staging, output);

    assertFalse(Files.exists(output.resolve("qa.json")));
  }

  private PublisherFields watchSetUp(boolean watch, String output, String qa) throws Exception {
    PublisherFields pf = new PublisherFields();
    pf.logger = mock(ILoggingService.class);
    pf.tempDir = p("temp");
    pf.outputDir = output;
    pf.qaDir = qa;
    PublisherSettings settings = new PublisherSettings();
    settings.setWatchMode(watch);
    PublisherIGLoader loader = new PublisherIGLoader(settings);
    loader.setPf(pf);
    loader.setUpWatchOutput();
    return pf;
  }
}
