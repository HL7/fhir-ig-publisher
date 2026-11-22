package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * path-tx-cache: the publisher deletes files in the terminology cache folder, so the IG can only name a folder of its own
 */
class TxCachePathTest {

  @TempDir
  Path dir;

  @ParameterizedTest
  @ValueSource(strings = {"txCache", "input-cache/txcache", "./txCache", "input/../txCache"})
  void aFolderInTheIGIsUsed(String value) throws IOException {
    Path ig = Files.createDirectories(dir.resolve("ig"));
    // where it really is (the temp folder can be through a link: /var is /private/var on macOS)
    assertEquals(ig.toRealPath().resolve(value).normalize().toString(), PublisherIGLoader.txCachePath(ig.toString(), ig.toString(), value));
  }

  @ParameterizedTest
  @ValueSource(strings = {"../txCache", "../other-ig/txCache", "input/../../txCache", "/tmp/fhircache", "/"})
  void aFolderOutsideTheIGIsIgnored(String value) throws IOException {
    Path ig = Files.createDirectories(dir.resolve("ig"));
    assertNull(PublisherIGLoader.txCachePath(ig.toString(), ig.toString(), value));
  }

  @Test
  void anAbsolutePathInTheIGIsUsed() throws IOException {
    Path ig = Files.createDirectories(dir.resolve("ig"));
    String abs = ig.resolve("txCache").toAbsolutePath().toString();
    assertEquals(ig.toRealPath().resolve("txCache").toString(), PublisherIGLoader.txCachePath(ig.toString(), ig.toString(), abs));
  }

  /** a repository can contain a symbolic link to somewhere else */
  @Test
  void aLinkOutOfTheIGIsIgnored() throws IOException {
    Path ig = Files.createDirectories(dir.resolve("ig"));
    Path outside = Files.createDirectories(dir.resolve("outside"));
    Files.createSymbolicLink(ig.resolve("txCache"), outside);
    assertNull(PublisherIGLoader.txCachePath(ig.toString(), ig.toString(), "txCache"));
    assertNull(PublisherIGLoader.txCachePath(ig.toString(), ig.toString(), "txCache/sub"));
  }

  /** a .. after a link goes up from where the link leads (not on Windows, where the file system takes .. off first) */
  @Test
  @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
  void aDotDotAfterALinkOutOfTheIGIsIgnored() throws IOException {
    Path ig = Files.createDirectories(dir.resolve("ig"));
    Path outside = Files.createDirectories(dir.resolve("outside/inner"));
    Files.createSymbolicLink(ig.resolve("input"), outside);
    assertNull(PublisherIGLoader.txCachePath(ig.toString(), ig.toString(), "input/../txCache"));
  }
}
