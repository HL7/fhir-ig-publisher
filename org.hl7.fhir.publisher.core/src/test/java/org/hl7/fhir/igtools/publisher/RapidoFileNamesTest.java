package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * File names the JVM can't decode: bytes that aren't valid in the file system encoding, and under a POSIX locale (as in
 * many containers and CI runners) any non-ASCII name. Run with LC_ALL=POSIX to see the second kind
 */
class RapidoFileNamesTest {

  /** writes each file, named by its bytes (as printf octal escapes), with sh; false if that can't be done here */
  private static boolean shWrite(Path dir, String... nameAndContent) throws InterruptedException {
    StringBuilder b = new StringBuilder("cd \"$1\"");
    for (int i = 0; i < nameAndContent.length; i += 2) {
      b.append(" && printf '").append(nameAndContent[i + 1]).append("' > \"$(printf '").append(nameAndContent[i]).append("')\"");
    }
    try {
      return new ProcessBuilder("sh", "-c", b.toString(), "sh", dir.toString()).inheritIO().start().waitFor() == 0;
    } catch (IOException e) {
      return false;
    }
  }

  @Test
  void otherFilesScanReadsNamesTheJvmCantDecode(@TempDir Path dir) throws Exception {
    Files.createDirectories(dir.resolve("input/excel"));
    // two names that aren't valid UTF-8 (and so decode the same), one that is but isn't ASCII
    assumeTrue(shWrite(dir.resolve("input/excel"),
      "a\\377.csv", "one", "a\\376.csv", "two", "CodeSystem\\321\\204.csv", "three", "plain.csv", "four"),
      "can't make files with such names here");
    assumeTrue(dir.resolve("input/excel").toFile().list().length == 4, "the file system changed the names");
    Map<String, String> a = RapidoTracker.scanTree(dir.toFile(), Set.of());
    assertEquals(4, a.size(), a.keySet().toString());
    assertTrue(a.containsKey("input/excel/plain.csv"));
    assertFalse(a.values().stream().anyMatch(v -> v.startsWith("unreadable")), a.toString());
    assertEquals(a, RapidoTracker.scanTree(dir.toFile(), Set.of()));
    for (String name : new String[] {"a\\376.csv", "CodeSystem\\321\\204.csv"}) {
      assertTrue(shWrite(dir.resolve("input/excel"), name, "changed"));
      Map<String, String> b = RapidoTracker.scanTree(dir.toFile(), Set.of());
      assertEquals(a.keySet(), b.keySet());
      assertEquals(1, a.keySet().stream().filter(k -> !a.get(k).equals(b.get(k))).count(), name);
      a.putAll(b);
    }
  }

  /** runs the command with sh in dir; false if that can't be done here */
  private static boolean sh(Path dir, String command) throws InterruptedException {
    try {
      return new ProcessBuilder("sh", "-c", "cd \"$1\" && " + command, "sh", dir.toString()).inheritIO().start().waitFor() == 0;
    } catch (IOException e) {
      return false;
    }
  }

  /** two folders whose names decode the same are both scanned, not taken for a link to the other */
  @Test
  void otherFilesScanListsFoldersWhoseNamesDecodeTheSame(@TempDir Path dir) throws Exception {
    Files.createDirectories(dir.resolve("input"));
    assumeTrue(sh(dir.resolve("input"), "mkdir \"$(printf 'd\\377')\" \"$(printf 'd\\376')\""
        + " && printf a > \"$(printf 'd\\377')/x.txt\" && printf b > \"$(printf 'd\\376')/x.txt\""), "can't make folders with such names here");
    assumeTrue(dir.resolve("input").toFile().list().length == 2, "the file system changed the names");
    Map<String, String> a = RapidoTracker.scanTree(dir.toFile(), Set.of());
    assertEquals(2, a.size(), a.toString());
    assertFalse(a.values().stream().anyMatch(v -> v.startsWith("(same as")), a.toString());
    assertTrue(sh(dir.resolve("input"), "printf changed > \"$(printf 'd\\377')/x.txt\""));
    Map<String, String> b = RapidoTracker.scanTree(dir.toFile(), Set.of());
    assertEquals(a.keySet(), b.keySet());
    assertNotEquals(a, b);
  }

  /** a FIFO in the template is read by nothing, so it doesn't make every build a complete one */
  @Test
  void aFifoInTheTemplateDoesntChangeItsHash(@TempDir Path dir) throws Exception {
    Path t = dir.resolve("template");
    Files.createDirectories(t);
    Files.writeString(t.resolve("a.html"), "x");
    assumeTrue(sh(t, "mkfifo pipe"), "can't make a FIFO here");
    // Git for Windows' mkfifo makes a plain pipe.lnk instead
    Path pipe = t.resolve("pipe");
    assumeTrue(Files.exists(pipe, LinkOption.NOFOLLOW_LINKS)
        && Files.readAttributes(pipe, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther(), "mkfifo didn't make a FIFO here");
    try {
      // opening a FIFO to read waits for a writer, so a scan that does would hang
      assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
        assertEquals(templateHash(t, new boolean[1]), templateHash(t, new boolean[1]));
        // and in the other-files scan
        assertEquals(Set.of("template/a.html"), RapidoTracker.scanTree(dir.toFile(), Set.of()).keySet());
      });
    } finally {
      new RandomAccessFile(pipe.toFile(), "rw").close(); // lets a stuck open go on
    }
  }

  /** nor does one whose name decodes to the name of a file next to it */
  @Test
  void aFifoNamedLikeAFileIsntRead(@TempDir Path dir) throws Exception {
    Path t = dir.resolve("template");
    Files.createDirectories(t);
    // a\377 decodes to "a" and U+FFFD under UTF-8, and to "a?" under POSIX
    assumeTrue(sh(t, "mkfifo p && mv p \"$(printf 'a\\377')\" && printf x > \"$(printf 'a\\357\\277\\275')\" && printf y > 'a?'"), "can't make such names here");
    assumeTrue(t.toFile().list().length == 3, "the file system changed the names");
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
        assertEquals(templateHash(t, new boolean[1]), templateHash(t, new boolean[1]));
        assertEquals(2, RapidoTracker.scanTree(dir.toFile(), Set.of()).size());
      });
    } finally {
      sh(t, "exec 3<>\"$(printf 'a\\377')\""); // lets a stuck open go on
    }
  }

  private static String templateHash(Path t, boolean[] fshIndex) throws IOException {
    RapidoDependencyAnalyser.Hasher h = new RapidoDependencyAnalyser.Hasher();
    RapidoTracker.hashTree(t.toFile(), h, Set.of(), fshIndex);
    return h.finish();
  }

  /** the template's hash reads files the JVM can't decode the names of too, so a change to one is seen */
  @Test
  void templateHashReadsNamesTheJvmCantDecode(@TempDir Path dir) throws Exception {
    Path t = dir.resolve("template");
    Files.createDirectories(t.resolve("layouts"));
    Files.writeString(t.resolve("layouts/a.html"), "x");
    assumeTrue(shWrite(t.resolve("layouts"), "b\\377.html", "uses fsh-index"), "can't make files with such names here");
    boolean[] fshIndex = new boolean[1];
    String h = templateHash(t, fshIndex);
    assertTrue(fshIndex[0]);
    assertEquals(h, templateHash(t, new boolean[1]));
    assertTrue(shWrite(t.resolve("layouts"), "b\\377.html", "changed"));
    boolean[] after = new boolean[1];
    assertNotEquals(h, templateHash(t, after));
    assertFalse(after[0]);
  }

  /** an output the build wrote through java.io is read by the same name, or it looks overwritten in every build */
  @Test
  void aFileJavaIoWroteIsReadByItsName(@TempDir Path dir) throws Exception {
    File f = new File(dir.toFile(), "стр.html");
    try (OutputStream o = new FileOutputStream(f)) {
      o.write(new byte[] {'a', 'b'});
    }
    assertTrue(f.isFile());
    assertArrayEquals(new byte[] {'a', 'b'}, RapidoTracker.read(f));
    String h = RapidoTracker.fileHash(f.getAbsolutePath());
    assertFalse(h.startsWith("unreadable"), h);
    assertEquals(h, RapidoTracker.fileHash(f.getAbsolutePath()));
  }

  /** an edit in a folder the scan can't list (or whose files it can't look at) could go unseen, so it counts as changed */
  @Test
  void otherFilesScanCountsAFolderItCantReadAsChanged(@TempDir Path dir) throws IOException {
    Path noList = dir.resolve("input/nolist");
    Path noSearch = dir.resolve("input/nosearch");
    Files.createDirectories(noList);
    Files.createDirectories(noSearch);
    Files.writeString(noList.resolve("a.txt"), "a");
    Files.writeString(noSearch.resolve("b.txt"), "b");
    Files.writeString(dir.resolve("input/c.txt"), "c");
    try {
      Files.setPosixFilePermissions(noList, PosixFilePermissions.fromString("--x------"));
      Files.setPosixFilePermissions(noSearch, PosixFilePermissions.fromString("r--------"));
    } catch (UnsupportedOperationException e) {
      assumeTrue(false, "no posix permissions here");
    }
    try {
      assumeTrue(!Files.isReadable(noList), "this user can read anything");
      Map<String, String> a = RapidoTracker.scanTree(dir.toFile(), Set.of());
      assertEquals(Set.of("input/c.txt", "input/nolist/", "input/nosearch/b.txt"), a.keySet());
      Map<String, String> b = RapidoTracker.scanTree(dir.toFile(), Set.of());
      assertEquals(a.get("input/c.txt"), b.get("input/c.txt"));
      assertNotEquals(a.get("input/nolist/"), b.get("input/nolist/"));
      assertNotEquals(a.get("input/nosearch/b.txt"), b.get("input/nosearch/b.txt"));
      // what's skipped isn't listed, so it never counts
      Map<String, String> s = RapidoTracker.scanTree(dir.toFile(), Set.of(noList.toFile().getCanonicalPath(), noSearch.toFile().getCanonicalPath()));
      assertEquals(Set.of("input/c.txt"), s.keySet());
    } finally {
      Files.setPosixFilePermissions(noList, PosixFilePermissions.fromString("rwx------"));
      Files.setPosixFilePermissions(noSearch, PosixFilePermissions.fromString("rwx------"));
    }
  }
}
