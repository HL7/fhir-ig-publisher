package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;

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
}
