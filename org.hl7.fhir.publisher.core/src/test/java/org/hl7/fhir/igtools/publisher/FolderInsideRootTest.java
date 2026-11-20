package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The IG's folders (temp, output, qa, and the source folders) must really be inside the IG's folder: a repository
 * can contain a symbolic link to anywhere, and the publisher clears temp and output, and publishes what's in the
 * source folders
 */
class FolderInsideRootTest {

  @TempDir
  Path dir;

  private Path ig() throws IOException {
    return Files.createDirectories(dir.resolve("ig"));
  }

  @Test
  void foldersInTheIGAreInside() throws IOException {
    Path ig = ig();
    Files.createDirectories(ig.resolve("input/pagecontent"));
    assertTrue(PublisherIGLoader.isInside(ig.toString(), ig.resolve("input/pagecontent").toString()));
    // not there yet: created later
    assertTrue(PublisherIGLoader.isInside(ig.toString(), ig.resolve("output").toString()));
    assertTrue(PublisherIGLoader.isInside(ig.toString(), ig.resolve("temp/pages/en").toString()));
  }

  @Test
  void foldersOutsideTheIGAreNot() throws IOException {
    Path ig = ig();
    assertFalse(PublisherIGLoader.isInside(ig.toString(), dir.resolve("other").toString()));
    assertFalse(PublisherIGLoader.isInside(ig.toString(), ig.resolve("../other").toString()));
    // a folder whose name starts with the IG folder's name isn't in it
    assertFalse(PublisherIGLoader.isInside(ig.toString(), dir.resolve("ig-other/output").toString()));
  }

  @Test
  void aLinkedFolderIsWhereItLinksTo() throws IOException {
    Path ig = ig();
    Path outside = Files.createDirectories(dir.resolve("users-documents"));
    Files.createSymbolicLink(ig.resolve("output"), outside);
    assertFalse(PublisherIGLoader.isInside(ig.toString(), ig.resolve("output").toString()));
    // and so is anything under it, existing or not
    assertFalse(PublisherIGLoader.isInside(ig.toString(), ig.resolve("output/qa").toString()));
  }

  @Test
  void aLinkFurtherUpThePathCounts() throws IOException {
    Path ig = ig();
    Path outside = Files.createDirectories(dir.resolve("elsewhere"));
    Files.createSymbolicLink(ig.resolve("input"), outside);
    assertFalse(PublisherIGLoader.isInside(ig.toString(), ig.resolve("input/pagecontent").toString()));
  }

  @Test
  void aLinkWithinTheIGIsFine() throws IOException {
    Path ig = ig();
    Path real = Files.createDirectories(ig.resolve("source/pages"));
    Files.createSymbolicLink(ig.resolve("pages"), real);
    assertTrue(PublisherIGLoader.isInside(ig.toString(), ig.resolve("pages").toString()));
  }

  @Test
  void aBrokenLinkIsNotInside() throws IOException {
    Path ig = ig();
    Files.createSymbolicLink(ig.resolve("output"), dir.resolve("does-not-exist"));
    assertFalse(PublisherIGLoader.isInside(ig.toString(), ig.resolve("output").toString()));
  }

  /** the IG itself can be reached through a link (e.g. /tmp on macOS is /private/tmp) */
  @Test
  void theIGFolderCanBeALink() throws IOException {
    Path ig = ig();
    Files.createDirectories(ig.resolve("input"));
    Path link = Files.createSymbolicLink(dir.resolve("ig-link"), ig);
    assertTrue(PublisherIGLoader.isInside(link.toString(), link.resolve("input").toString()));
    assertTrue(PublisherIGLoader.isInside(link.toString(), link.resolve("output").toString()));
  }
}
