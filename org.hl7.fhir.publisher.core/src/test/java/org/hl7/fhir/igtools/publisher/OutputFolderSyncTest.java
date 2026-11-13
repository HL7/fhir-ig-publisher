package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutputFolderSyncTest {

  private static final FileTime OLD = FileTime.from(Instant.parse("2020-01-01T00:00:00Z"));

  @TempDir
  Path dir;

  private Path staged() throws IOException {
    return Files.createDirectories(dir.resolve("staged"));
  }

  private Path target() {
    return dir.resolve("output");
  }

  private static void write(Path root, String rel, String content) throws IOException {
    Path p = root.resolve(rel);
    Files.createDirectories(p.getParent());
    Files.writeString(p, content);
  }

  private static void writeOld(Path root, String rel, String content) throws IOException {
    write(root, rel, content);
    Files.setLastModifiedTime(root.resolve(rel), OLD);
  }

  private static String read(Path root, String rel) throws IOException {
    return Files.readString(root.resolve(rel));
  }

  /** what is in the folder, as relative paths, folders ending with / */
  private static Set<String> contents(Path root) throws IOException {
    try (Stream<Path> s = Files.walk(root)) {
      return s.filter(p -> !p.equals(root))
          .map(p -> root.relativize(p).toString().replace(File.separatorChar, '/') + (Files.isDirectory(p) ? "/" : ""))
          .collect(Collectors.toCollection(TreeSet::new));
    }
  }

  @Test
  void keepsTheStagedFilesWhenAsked() throws IOException {
    Path staged = staged();
    writeOld(staged, "index.html", "home");
    write(staged, "en/page.html", "new page");
    write(staged, "en/same.html", "same");
    write(target(), "en/page.html", "old page");
    write(target(), "en/same.html", "same");
    write(target(), "stale.html", "stale");
    Object inode = Files.readAttributes(staged.resolve("en/page.html"), java.nio.file.attribute.BasicFileAttributes.class).fileKey();

    OutputFolderSync.Result r = OutputFolderSync.sync(staged, target(), true);

    // the output is synced as it is with a move
    assertEquals(Set.of("index.html", "en/", "en/page.html", "en/same.html"), contents(target()));
    assertEquals("new page", read(target(), "en/page.html"));
    assertEquals(OLD, Files.getLastModifiedTime(target().resolve("index.html")));
    assertEquals(2, r.getWritten());
    assertEquals(1, r.getUnchanged());
    assertEquals(1, r.getDeleted());
    // and the staged files are all still there, as they were (the same file, with the same modified time)
    assertEquals(Set.of("index.html", "en/", "en/page.html", "en/same.html"), contents(staged));
    assertEquals("new page", read(staged, "en/page.html"));
    assertEquals(OLD, Files.getLastModifiedTime(staged.resolve("index.html")));
    if (inode != null) {
      assertEquals(inode, Files.readAttributes(staged.resolve("en/page.html"), java.nio.file.attribute.BasicFileAttributes.class).fileKey());
      assertFalse(inode.equals(Files.readAttributes(target().resolve("en/page.html"), java.nio.file.attribute.BasicFileAttributes.class).fileKey()));
    }
    // no copy is left next to the files
    try (Stream<Path> st = Files.walk(target())) {
      assertFalse(st.anyMatch(p -> p.getFileName().toString().startsWith(".sync-")));
    }
  }

  @Test
  void keepingTheStagedFilesReplacesAReadOnlyFile() throws IOException {
    Path staged = staged();
    write(staged, "page.html", "new");
    write(target(), "page.html", "old");
    assumeTrue(target().resolve("page.html").toFile().setWritable(false));

    OutputFolderSync.Result r = OutputFolderSync.sync(staged, target(), true);

    assertTrue(r.getFailures().isEmpty(), r.getFailures().toString());
    assertEquals("new", read(target(), "page.html"));
    assertEquals("new", read(staged, "page.html"));
  }

  @Test
  void createsTheTargetAndCopiesEverything() throws IOException {
    Path staged = staged();
    write(staged, "index.html", "home");
    write(staged, "en/page.html", "page");
    Files.createDirectories(staged.resolve("empty/inner"));

    OutputFolderSync.Result r = OutputFolderSync.sync(staged, target());

    assertEquals(Set.of("index.html", "en/", "en/page.html", "empty/", "empty/inner/"), contents(target()));
    assertEquals("page", read(target(), "en/page.html"));
    assertEquals(2, r.getWritten());
    assertEquals(0, r.getDeleted());
    assertEquals(0, r.getUnchanged());
    assertTrue(r.getFailures().isEmpty());
  }

  @Test
  void leavesUnchangedFilesAlone() throws IOException {
    Path staged = staged();
    Path target = target();
    write(staged, "same.html", "same content");
    write(staged, "sub/same.css", "body {}");
    writeOld(target, "same.html", "same content");
    writeOld(target, "sub/same.css", "body {}");
    Object key = Files.readAttributes(target.resolve("same.html"), java.nio.file.attribute.BasicFileAttributes.class).fileKey();

    OutputFolderSync.Result r = OutputFolderSync.sync(staged, target);

    assertEquals(OLD, Files.getLastModifiedTime(target.resolve("same.html")));
    assertEquals(OLD, Files.getLastModifiedTime(target.resolve("sub/same.css")));
    if (key != null) {
      // not replaced by another file with the same content
      assertEquals(key, Files.readAttributes(target.resolve("same.html"), java.nio.file.attribute.BasicFileAttributes.class).fileKey());
    }
    assertEquals(2, r.getUnchanged());
    assertEquals(0, r.getWritten());
    assertEquals(0, r.getDeleted());
  }

  @Test
  void replacesChangedFiles() throws IOException {
    Path staged = staged();
    Path target = target();
    write(staged, "same-size.html", "content B");
    write(staged, "longer.html", "a longer content");
    // differs only after the first 64K block that is compared
    String big = "x".repeat(70_000);
    write(staged, "big.html", big + "B");
    writeOld(target, "same-size.html", "content A");
    writeOld(target, "longer.html", "short");
    writeOld(target, "big.html", big + "A");

    OutputFolderSync.Result r = OutputFolderSync.sync(staged, target);

    assertEquals("content B", read(target, "same-size.html"));
    assertEquals("a longer content", read(target, "longer.html"));
    assertEquals(big + "B", read(target, "big.html"));
    assertFalse(OLD.equals(Files.getLastModifiedTime(target.resolve("same-size.html"))));
    assertEquals(3, r.getWritten());
    assertEquals(0, r.getUnchanged());
  }

  @Test
  void deletesWhatTheBuildNoLongerProduces() throws IOException {
    Path staged = staged();
    Path target = target();
    write(staged, "index.html", "home");
    write(staged, "kept/a.html", "a");
    writeOld(target, "index.html", "home");
    writeOld(target, "kept/a.html", "a");
    write(target, "kept/stale.html", "old");
    write(target, "stale.html", "old");
    write(target, "gone/deeper/x.html", "old");
    Files.createDirectories(target.resolve("gone/empty"));

    OutputFolderSync.Result r = OutputFolderSync.sync(staged, target);

    assertEquals(Set.of("index.html", "kept/", "kept/a.html"), contents(target));
    assertEquals(OLD, Files.getLastModifiedTime(target.resolve("kept/a.html")));
    // 3 files and 3 folders
    assertEquals(6, r.getDeleted());
    assertEquals(2, r.getUnchanged());
    assertTrue(r.getFailures().isEmpty());
  }

  @Test
  void aFileCanBecomeAFolderAndAFolderAFile() throws IOException {
    Path staged = staged();
    Path target = target();
    write(staged, "was-file/inner.html", "now a folder");
    write(staged, "was-folder", "now a file");
    write(target, "was-file", "a file");
    write(target, "was-folder/inner.html", "in a folder");

    OutputFolderSync.sync(staged, target);

    assertEquals(Set.of("was-file/", "was-file/inner.html", "was-folder"), contents(target));
    assertEquals("now a file", read(target, "was-folder"));
  }

  @Test
  void comparesWholeBlocksAndEmptyFiles() throws IOException {
    Path staged = staged();
    Path target = target();
    String block = "y".repeat(64 * 1024);
    write(staged, "one-block.html", block.substring(1) + "B");
    write(staged, "two-blocks.html", block + block.substring(1) + "B");
    write(staged, "two-blocks-same.html", block + block);
    write(staged, "empty.html", "");
    writeOld(target, "one-block.html", block.substring(1) + "A");
    writeOld(target, "two-blocks.html", block + block.substring(1) + "A");
    writeOld(target, "two-blocks-same.html", block + block);
    writeOld(target, "empty.html", "");

    OutputFolderSync.Result r = OutputFolderSync.sync(staged, target);

    assertEquals(2, r.getWritten());
    assertEquals(2, r.getUnchanged());
    assertTrue(read(target, "one-block.html").endsWith("B"));
    assertTrue(read(target, "two-blocks.html").endsWith("B"));
    assertEquals(OLD, Files.getLastModifiedTime(target.resolve("empty.html")));
  }

  @Test
  void syncsWhereALinkedOutputFolderPoints() throws IOException {
    Path staged = staged();
    Path real = Files.createDirectories(dir.resolve("web-root"));
    write(staged, "new.html", "new");
    write(staged, "same.html", "same");
    writeOld(real, "same.html", "same");
    write(real, "stale.html", "old");
    try {
      Files.createSymbolicLink(target(), real);
    } catch (UnsupportedOperationException | IOException e) {
      assumeTrue(false, "can't make a link here");
    }

    OutputFolderSync.Result r = OutputFolderSync.sync(staged, target());

    assertTrue(Files.isSymbolicLink(target()));
    assertEquals(Set.of("new.html", "same.html"), contents(real));
    assertEquals(OLD, Files.getLastModifiedTime(real.resolve("same.html")));
    assertTrue(r.getFailures().isEmpty(), r.getFailures().toString());
  }

  @Test
  void leavesAFolderItCantReadAloneAndCarriesOn() throws IOException {
    Path staged = staged();
    Path target = target();
    write(staged, "index.html", "new");
    write(target, "index.html", "old");
    write(target, "private/secret.html", "kept");
    File priv = target.resolve("private").toFile();
    assumeTrue(priv.setReadable(false, false) && !Files.isReadable(target.resolve("private")));
    try {
      OutputFolderSync.Result r = OutputFolderSync.sync(staged, target);

      assertEquals("new", read(target, "index.html"));
      assertEquals(1, r.getFailures().size(), r.getFailures().toString());
      assertTrue(r.getFailures().get(0).contains("private"), r.getFailures().toString());
    } finally {
      priv.setReadable(true, false);
    }
    assertEquals("kept", read(target, "private/secret.html"));
  }

  /*
   * On a file system that ignores case (Windows, macOS) the stale name has to be deleted before the new one is moved
   * in, or the old case stays. On Linux the names are different files, so this only checks the result; the order is
   * also what aFileCanBecomeAFolderAndAFolderAFile depends on
   */
  @Test
  void aNameThatOnlyChangedCaseGetsTheNewCase() throws IOException {
    Path staged = staged();
    Path target = target();
    write(staged, "Page.html", "page");
    write(staged, "images/logo.png", "logo");
    write(target, "page.html", "page");
    write(target, "Images/logo.png", "logo");

    OutputFolderSync.Result r = OutputFolderSync.sync(staged, target);

    // on a file system that ignores case these are the same file: the listing must still show the new names
    try (Stream<Path> s = Files.list(target)) {
      assertEquals(Set.of("Page.html", "images"), s.map(p -> p.getFileName().toString()).collect(Collectors.toSet()));
    }
    try (Stream<Path> s = Files.list(target.resolve("images"))) {
      assertEquals(List.of("logo.png"), s.map(p -> p.getFileName().toString()).collect(Collectors.toList()));
    }
    assertTrue(r.getFailures().isEmpty());
  }

  @Test
  void replacesAndDeletesReadOnlyFiles() throws IOException {
    Path staged = staged();
    Path target = target();
    write(staged, "changed.html", "new");
    write(target, "changed.html", "old!");
    write(target, "stale.html", "old");
    assumeTrue(target.resolve("changed.html").toFile().setWritable(false));
    assumeTrue(target.resolve("stale.html").toFile().setWritable(false));

    OutputFolderSync.Result r = OutputFolderSync.sync(staged, target);

    assertEquals("new", read(target, "changed.html"));
    assertFalse(Files.exists(target.resolve("stale.html")));
    assertTrue(r.getFailures().isEmpty(), r.getFailures().toString());
  }

  @Test
  void reportsWhatItCouldNotDeleteAndCarriesOn() throws IOException {
    Path staged = staged();
    Path target = target();
    write(staged, "locked/new.html", "new");
    write(staged, "other.html", "other");
    write(target, "locked/stale.html", "old");
    File locked = target.resolve("locked").toFile();
    // a folder that can't be written to: its files can't be deleted or added (not so for root, or on Windows)
    assumeTrue(locked.setWritable(false) && !Files.isWritable(target.resolve("locked")));
    try {
      OutputFolderSync.Result r = OutputFolderSync.sync(staged, target);

      assertEquals("other", read(target, "other.html"));
      assertEquals(2, r.getFailures().size(), r.getFailures().toString());
      assertTrue(r.getFailures().get(0).contains("stale.html"), r.getFailures().toString());
      assertTrue(r.getFailures().get(1).contains("new.html"), r.getFailures().toString());
      assertEquals(1, r.getWritten());
    } finally {
      locked.setWritable(true);
    }
  }

  @Test
  void movesNewAndChangedFilesOutOfTheStagingFolder() throws IOException {
    Path staged = staged();
    Path target = target();
    write(staged, "new.html", "new");
    write(staged, "same.html", "same");
    write(target, "same.html", "same");

    OutputFolderSync.sync(staged, target);

    assertFalse(Files.exists(staged.resolve("new.html")));
    assertTrue(Files.exists(staged.resolve("same.html")));
  }

  @Test
  void aStagingFolderItCantReadFailsTheSyncBeforeAnythingIsDeleted() throws IOException {
    Path staged = staged();
    Path target = target();
    write(staged, "index.html", "new");
    write(staged, "sub/page.html", "new");
    write(target, "index.html", "old");
    write(target, "sub/page.html", "old");
    File sub = staged.resolve("sub").toFile();
    assumeTrue(sub.setReadable(false, false) && !Files.isReadable(staged.resolve("sub")));
    try {
      assertThrows(IOException.class, () -> OutputFolderSync.sync(staged, target));
    } finally {
      sub.setReadable(true, false);
    }
    assertEquals("old", read(target, "index.html"));
    assertEquals("old", read(target, "sub/page.html"));
  }

  @Test
  void aLinkInTheOutputFolderIsDeletedButNotWhatItPointsTo() throws IOException {
    Path staged = staged();
    Path target = target();
    write(staged, "index.html", "new");
    write(dir, "elsewhere/keep.html", "keep");
    Files.createDirectories(target);
    try {
      Files.createSymbolicLink(target.resolve("linked"), dir.resolve("elsewhere"));
    } catch (UnsupportedOperationException | IOException e) {
      assumeTrue(false, "can't make a link here");
    }

    OutputFolderSync.Result r = OutputFolderSync.sync(staged, target);

    assertEquals(Set.of("index.html"), contents(target));
    assertEquals("keep", read(dir, "elsewhere/keep.html"));
    assertTrue(r.getFailures().isEmpty(), r.getFailures().toString());
  }

  @Test
  void acrossFileSystemsAChangedFileIsReplacedByARename() throws IOException {
    Path shm = Paths.get("/dev/shm");
    assumeTrue(Files.isDirectory(shm) && Files.isWritable(shm));
    Path staged = Files.createTempDirectory(shm, "sync-test");
    try {
      assumeTrue(!Files.getFileStore(staged).equals(Files.getFileStore(dir)), "the same file system");
      Path target = target();
      write(staged, "page.html", "new");
      write(staged, "new.html", "new");
      write(target, "page.html", "old");

      OutputFolderSync.Result r = OutputFolderSync.sync(staged, target);

      assertEquals(Set.of("new.html", "page.html"), contents(target));
      assertEquals("new", read(target, "page.html"));
      assertEquals(2, r.getWritten());
      assertFalse(Files.exists(staged.resolve("page.html")));
    } finally {
      try (Stream<Path> s = Files.walk(staged)) {
        for (Path p : s.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
          Files.deleteIfExists(p);
        }
      }
    }
  }

  @Test
  void aTargetThatHoldsTheStagingFolderIsLeftAlone() throws IOException {
    Path staged = staged();
    write(staged, "index.html", "new");
    write(dir, "input/page.md", "source");

    assertThrows(IOException.class, () -> OutputFolderSync.sync(staged, dir));
    assertThrows(IOException.class, () -> OutputFolderSync.sync(dir, staged.resolve("out")));

    assertEquals("source", read(dir, "input/page.md"));
    assertEquals("new", read(staged, "index.html"));
  }
}
