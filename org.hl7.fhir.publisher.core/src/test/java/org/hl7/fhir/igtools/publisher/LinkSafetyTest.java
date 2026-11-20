package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A repository can contain symbolic links to anywhere: the publisher mustn't empty, read or write what they link to
 */
class LinkSafetyTest {

  @TempDir
  Path dir;

  private Path file(Path p, String content) throws IOException {
    Files.createDirectories(p.getParent());
    Files.write(p, content.getBytes(StandardCharsets.UTF_8));
    return p;
  }

  /** what a link points to - the user's own files */
  private Path precious() throws IOException {
    Path p = dir.resolve("precious");
    file(p.resolve("a.txt"), "a");
    file(p.resolve("sub/b.txt"), "b");
    return p;
  }

  private void assertPreciousIntact(Path p) {
    assertTrue(Files.exists(p.resolve("a.txt")));
    assertTrue(Files.exists(p.resolve("sub/b.txt")));
  }

  @Test
  void clearingAFolderDeletesLinksInItButNotWhatTheyLinkTo() throws IOException {
    Path precious = precious();
    Path temp = Files.createDirectories(dir.resolve("ig/temp"));
    file(temp.resolve("x.html"), "x");
    file(temp.resolve("pages/y.html"), "y");
    Files.createSymbolicLink(temp.resolve("comparison-a"), precious);
    Files.createSymbolicLink(temp.resolve("pages/link.txt"), precious.resolve("a.txt"));
    PublisherUtils.clearFolder(temp.toString());
    assertArrayEquals(new String[0], temp.toFile().list());
    assertPreciousIntact(precious);
  }

  @Test
  void aFolderThatIsALinkIsNotCleared() throws IOException {
    Path precious = precious();
    Files.createDirectories(dir.resolve("ig"));
    Path temp = Files.createSymbolicLink(dir.resolve("ig/temp"), precious);
    assertThrows(IOException.class, () -> PublisherUtils.clearFolder(temp.toString()));
    assertPreciousIntact(precious);
  }

  @Test
  void exemptionsAreKeptAndLinksInsideAreNotFollowed() throws IOException {
    Path precious = precious();
    Path cache = Files.createDirectories(dir.resolve("ig/txCache"));
    file(cache.resolve("snomed.cache"), "s");
    file(cache.resolve("other.cache"), "o");
    Files.createSymbolicLink(cache.resolve("sub"), precious);
    PublisherUtils.clearFolder(cache.toString(), "snomed.cache", "loinc.cache");
    assertArrayEquals(new String[] {"snomed.cache"}, cache.toFile().list());
    assertPreciousIntact(precious);
  }

  @Test
  void aMissingFolderIsNothingToClear() throws IOException {
    PublisherUtils.clearFolder(dir.resolve("not-there").toString());
  }

  @Test
  void linksOutOfTheIGAreFound() throws IOException {
    Path precious = precious();
    Path ig = Files.createDirectories(dir.resolve("ig"));
    file(ig.resolve("input/pagecontent/index.md"), "x");
    // inside the IG: fine
    Files.createSymbolicLink(ig.resolve("input/pagecontent/same.md"), ig.resolve("input/pagecontent/index.md"));
    Files.createSymbolicLink(ig.resolve("pages"), ig.resolve("input/pagecontent"));
    // out of it
    Files.createSymbolicLink(ig.resolve("input/pagecontent/secret.md"), precious.resolve("a.txt"));
    Files.createDirectories(ig.resolve("temp"));
    Files.createSymbolicLink(ig.resolve("temp/comparison-a"), precious);
    Files.createSymbolicLink(ig.resolve("template"), precious);
    // nowhere
    Files.createSymbolicLink(ig.resolve("input/broken.md"), dir.resolve("does-not-exist"));
    // .git isn't looked at
    Files.createDirectories(ig.resolve(".git"));
    Files.createSymbolicLink(ig.resolve(".git/whatever"), precious);
    List<String> links = PublisherUtils.linksOut(ig.toString());
    assertEquals(List.of("input/broken.md", "input/pagecontent/secret.md", "temp/comparison-a", "template"), links);
  }

  @Test
  void anIGWithoutLinksOutIsFine() throws IOException {
    Path ig = Files.createDirectories(dir.resolve("ig"));
    file(ig.resolve("input/pagecontent/index.md"), "x");
    assertTrue(PublisherUtils.linksOut(ig.toString()).isEmpty());
  }

  @Test
  void aDownloadCanOnlyBeSoBig() throws IOException {
    byte[] ten = new byte[10 * 1024 * 1024];
    assertEquals(ten.length, PublisherUtils.readLimited(new ByteArrayInputStream(ten), 10 * 1024 * 1024, "test").length);
    IOException e = assertThrows(IOException.class, () -> PublisherUtils.readLimited(new ByteArrayInputStream(ten), 1024 * 1024, "The template x"));
    assertTrue(e.getMessage().startsWith("The template x is too big"), e.getMessage());
  }

  /** npm stops looking in the folders above at the first one with a package.json */
  @Test
  void sushisFolderHasItsOwnPackageJson() throws IOException {
    File work = FSHRunner.makeWorkFolder();
    try {
      assertEquals("{}", new String(Files.readAllBytes(new File(work, "package.json").toPath()), StandardCharsets.UTF_8));
      assertFalse(work.getAbsolutePath().contains(dir.toString()));
    } finally {
      new File(work, "package.json").delete();
      work.delete();
    }
  }
}
