package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The AI step in watch mode (with a cache) writes what it does without one, but only writes the files whose content
 * is different, and reports only those as written
 */
class AIProcessorTest {

  @TempDir
  Path dir;

  @Test
  void watchModeWritesWhatAFullRunWritesButOnlyWhatChanged() throws IOException {
    Path out = dir.resolve("out");
    site(out);
    AIProcessor.Cache cache = new AIProcessor.Cache();
    Set<String> fresh = null;
    run(out, cache, fresh);
    assertSameAsFullRun(out);
    // everything is new the first time
    assertTrue(cache.getWritten().contains(abs(out, "en/page1.md")));
    assertTrue(cache.getWritten().contains(abs(out, "en/CodeSystem-cs1.md")));
    assertTrue(cache.getWritten().contains(abs(out, "en/ai.zip")));
    assertTrue(cache.getWritten().contains(abs(out, "llms.txt")));
    Map<String, String> before = stamps(out);

    // nothing changed: nothing is written
    run(out, cache, new HashSet<>());
    assertEquals(Collections.emptySet(), cache.getWritten());
    assertEquals(before, stamps(out));
    assertSameAsFullRun(out);

    // a page's text changed: its markdown and the zip
    write(out, "en/page1.html", page("Page One", "changed text"));
    run(out, cache, set(out, "en/page1.html"));
    assertEquals(set(out, "en/page1.md", "en/ai.zip"), cache.getWritten());
    assertSameAsFullRun(out);

    // a page's title changed: the lists of pages too
    write(out, "en/page1.html", page("Page Uno", "changed text"));
    run(out, cache, set(out, "en/page1.html"));
    assertEquals(set(out, "en/page1.md", "en/ai.zip", "en/llms.txt", "llms.txt"), cache.getWritten());
    assertSameAsFullRun(out);

    // a resource's page changed
    write(out, "en/CodeSystem-cs1.html", page("CS1", "other codes"));
    run(out, cache, set(out, "en/CodeSystem-cs1.html"));
    assertEquals(set(out, "en/CodeSystem-cs1.md", "en/ai.zip"), cache.getWritten());
    assertSameAsFullRun(out);

    // files the step writes were deleted, or changed by something else: they're written again
    Files.delete(out.resolve("en/ai.zip"));
    Files.delete(out.resolve("en/ValueSet-vs1.md"));
    write(out, "en/page2.md", "edited");
    run(out, cache, new HashSet<>());
    assertEquals(set(out, "en/ai.zip", "en/ValueSet-vs1.md", "en/page2.md"), cache.getWritten());
    assertSameAsFullRun(out);
    write(out, "en/ai.zip", "not a zip");
    run(out, cache, new HashSet<>());
    assertEquals(set(out, "en/ai.zip"), cache.getWritten());
    assertSameAsFullRun(out);
  }

  @Test
  void anOldTemplateToo() throws IOException {
    // the pages are in the output folder itself, and the zip is written as ai-prep.zip and then renamed
    Path out = dir.resolve("old");
    site(out);
    try (Stream<Path> s = Files.list(out.resolve("en"))) {
      for (Path p : (Iterable<Path>) s::iterator) {
        Files.move(p, out.resolve(p.getFileName()));
      }
    }
    Files.delete(out.resolve("en"));
    AIProcessor.Cache cache = new AIProcessor.Cache();
    run(out, cache, null, true);
    assertSameAsFullRun(out, true);
    assertTrue(cache.getWritten().containsAll(set(out, "page1.md", "ai.zip", "llms.txt")), cache.getWritten().toString());

    run(out, cache, new HashSet<>(), true);
    assertEquals(Collections.emptySet(), cache.getWritten());
    assertSameAsFullRun(out, true);

    write(out, "page1.html", page("Page One", "changed text"));
    run(out, cache, set(out, "page1.html"), true);
    assertEquals(set(out, "page1.md", "ai.zip"), cache.getWritten());
    assertSameAsFullRun(out, true);

    write(out, "page1.html", page("Page Uno", "changed text"));
    run(out, cache, set(out, "page1.html"), true);
    assertEquals(set(out, "page1.md", "ai.zip", "llms.txt"), cache.getWritten());
    assertSameAsFullRun(out, true);

    write(out, "ai.zip", "not a zip");
    run(out, cache, new HashSet<>(), true);
    assertEquals(set(out, "ai.zip"), cache.getWritten());
    assertSameAsFullRun(out, true);
  }

  // --- helpers -------------------------------------------------------------------------------------------------

  private void run(Path out, AIProcessor.Cache cache, Set<String> fresh) throws IOException {
    run(out, cache, fresh, false);
  }

  private void run(Path out, AIProcessor.Cache cache, Set<String> fresh, boolean old) throws IOException {
    cache.prepare(fresh, files -> {
      throw new IOException("nothing should need to be written again by Jekyll: " + files);
    });
    process(new AIProcessor(out.toString(), cache), old);
  }

  private static void process(AIProcessor ai, boolean old) throws IOException {
    if (old) {
      ai.processOldTemplates();
    } else {
      ai.processNewTemplates(List.of("en"));
    }
  }

  /** The output is what a run without a cache makes of the same pages */
  private void assertSameAsFullRun(Path out) throws IOException {
    assertSameAsFullRun(out, false);
  }

  private void assertSameAsFullRun(Path out, boolean old) throws IOException {
    Path ref = dir.resolve("ref");
    if (Files.exists(ref)) {
      delete(ref);
    }
    copyPages(out, ref);
    process(new AIProcessor(ref.toString()), old);
    assertEquals(contents(ref), contents(out));
    for (String f : contents(out).keySet()) {
      assertFalse(f.contains(".ai.") || f.contains("ai-prep"), f);
    }
  }

  private void site(Path out) throws IOException {
    Files.createDirectories(out.resolve("en"));
    write(out, "en/index.html", page("Home", "the home page"));
    write(out, "en/page1.html", page("Page One", "some text"));
    write(out, "en/page2.html", page("Page Two", "more text"));
    write(out, "en/CodeSystem-cs1.html", page("CS1", "codes"));
    write(out, "en/CodeSystem-cs1-definitions.html", page("CS1 definitions", "an alternate view"));
    write(out, "en/ValueSet-vs1.html", page("VS1", "a value set"));
    write(out, "en/Patient-p1.html", page("P1", "an example"));
    write(out, "en/toc.html", page("Toc", "the table of contents"));
    Files.write(out.resolve("package.tgz"), tgz(
        "package/package.json", "{\"name\":\"test.ig\",\"version\":\"0.1.0\",\"title\":\"Test IG\",\"fhirVersions\":[\"5.0.0\"],\"dependencies\":{}}",
        "package/other/spec.internals", "{}",
        "package/CodeSystem-cs1.json", "{\"resourceType\":\"CodeSystem\",\"id\":\"cs1\",\"url\":\"http://x/cs1\",\"title\":\"CS One\"}",
        "package/ValueSet-vs1.json", "{\"resourceType\":\"ValueSet\",\"id\":\"vs1\",\"url\":\"http://x/vs1\",\"name\":\"VSOne\"}",
        "package/example/Patient-p1.json", "{\"resourceType\":\"Patient\",\"id\":\"p1\"}"));
  }

  private static String page(String title, String text) {
    return "<html><head><title>" + title + "</title></head><body><div><p>" + text + "</p></div></body></html>";
  }

  private static byte[] tgz(String... nameAndContent) throws IOException {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    try (TarArchiveOutputStream t = new TarArchiveOutputStream(new GZIPOutputStream(b))) {
      for (int i = 0; i < nameAndContent.length; i += 2) {
        byte[] c = nameAndContent[i + 1].getBytes(StandardCharsets.UTF_8);
        TarArchiveEntry e = new TarArchiveEntry(nameAndContent[i]);
        e.setSize(c.length);
        t.putArchiveEntry(e);
        t.write(c);
        t.closeArchiveEntry();
      }
    }
    return b.toByteArray();
  }

  /** The files under the folder, with their content (a zip's entries and their content, not its bytes) */
  private static Map<String, String> contents(Path root) throws IOException {
    Map<String, String> res = new TreeMap<>();
    try (Stream<Path> s = Files.walk(root)) {
      for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
        String rel = root.relativize(p).toString().replace('\\', '/');
        res.put(rel, rel.endsWith(".zip") ? zipContent(p) : new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
      }
    }
    return res;
  }

  private static String zipContent(Path p) throws IOException {
    StringBuilder b = new StringBuilder();
    try (InputStream in = Files.newInputStream(p); ZipInputStream z = new ZipInputStream(in)) {
      ZipEntry e;
      while ((e = z.getNextEntry()) != null) {
        b.append(e.getName()).append('\n').append(new String(z.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
      }
    } catch (IOException e) {
      return "unreadable: " + new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
    return b.toString();
  }

  /** What identifies each file's write: its size, time and inode */
  private static Map<String, String> stamps(Path root) throws IOException {
    Map<String, String> res = new TreeMap<>();
    // a file written again gets a new inode change time (where there's none, the modification time is what tells)
    boolean unix = root.getFileSystem().supportedFileAttributeViews().contains("unix");
    try (Stream<Path> s = Files.walk(root)) {
      for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
        res.put(root.relativize(p).toString(), Files.size(p) + ":" + (unix ? Files.getAttribute(p, "unix:ino") + ":" + Files.getAttribute(p, "unix:ctime")
            : Files.getLastModifiedTime(p).toString()));
      }
    }
    return res;
  }

  /** The pages and the package (what the AI step reads) */
  private static void copyPages(Path from, Path to) throws IOException {
    try (Stream<Path> s = Files.walk(from)) {
      for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
        String n = p.getFileName().toString();
        if (n.endsWith(".html") || n.equals("package.tgz")) {
          Path d = to.resolve(from.relativize(p));
          Files.createDirectories(d.getParent());
          Files.copy(p, d);
        }
      }
    }
  }

  private static void delete(Path p) throws IOException {
    try (Stream<Path> s = Files.walk(p)) {
      List<Path> all = new ArrayList<>();
      s.forEach(all::add);
      Collections.reverse(all);
      for (Path x : all) {
        Files.delete(x);
      }
    }
  }

  private static void write(Path out, String rel, String content) throws IOException {
    try (OutputStream o = Files.newOutputStream(out.resolve(rel))) {
      o.write(content.getBytes(StandardCharsets.UTF_8));
    }
  }

  private static String abs(Path out, String rel) {
    return out.resolve(rel).toFile().getAbsolutePath();
  }

  private static Set<String> set(Path out, String... rels) {
    Set<String> res = new HashSet<>();
    for (String r : rels) {
      res.add(abs(out, r));
    }
    return res;
  }
}
