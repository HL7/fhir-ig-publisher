package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Jekyll that's kept running in watch mode: which Ruby it starts, and (when Jekyll is installed) that what it writes
 * after an update is what `jekyll build` writes for the same site
 */
class JekyllDriverTest {

  private static final long TIMEOUT = 120_000;

  @TempDir
  Path dir;

  private JekyllDriver driver;
  private final List<String> log = new ArrayList<>();

  @AfterEach
  void stop() {
    if (driver != null) {
      driver.stop();
    }
  }

  @Test
  void rubyFromTheBinstub() throws IOException {
    assertEquals(Arrays.asList("ruby"), JekyllDriver.rubyFor(binstub("a", "#!/usr/bin/env ruby")));
    assertEquals(Arrays.asList("/opt/ruby/bin/ruby3.3", "-w"), JekyllDriver.rubyFor(binstub("b", "#!/opt/ruby/bin/ruby3.3 -w")));
    assertEquals(Arrays.asList("ruby", "--disable-gems"), JekyllDriver.rubyFor(binstub("c", "#!/usr/bin/env -S ruby --disable-gems")));
    // not a ruby script, or no jekyll on the path: the ruby on the path
    assertEquals(Arrays.asList("ruby"), JekyllDriver.rubyFor(binstub("d", "#!/bin/sh")));
    assertEquals(Arrays.asList("ruby"), JekyllDriver.rubyFor(dir.resolve("none").toString()));
    assertEquals(Arrays.asList("ruby"), JekyllDriver.rubyFor(null));
    // the first jekyll on the path is the one
    assertEquals(Arrays.asList("/x/ruby"), JekyllDriver.rubyFor(binstub("e", "#!/x/ruby") + File.pathSeparator + binstub("f", "#!/y/ruby")));
  }

  private String binstub(String name, String firstLine) throws IOException {
    Path d = Files.createDirectories(dir.resolve(name));
    Path f = d.resolve("jekyll");
    Files.write(f, (firstLine + "\nputs 1\n").getBytes(StandardCharsets.UTF_8));
    assertTrue(f.toFile().setExecutable(true));
    return d.toString();
  }

  @Test
  void disabled() {
    assertEquals(null, JekyllDriver.forCommand("bundle exec jekyll"));
    String old = System.getProperty(JekyllDriver.DISABLE_PROPERTY);
    System.setProperty(JekyllDriver.DISABLE_PROPERTY, "false");
    try {
      assertEquals(null, JekyllDriver.forCommand("jekyll"));
    } finally {
      if (old == null) {
        System.clearProperty(JekyllDriver.DISABLE_PROPERTY);
      } else {
        System.setProperty(JekyllDriver.DISABLE_PROPERTY, old);
      }
    }
  }

  @Test
  void disabledAfterItFailed() {
    JekyllDriver d = new JekyllDriver(Arrays.asList("ruby"), System.getenv());
    assertEquals(null, d.getUnavailable());
    d.disable("it failed");
    assertEquals("it failed", d.getUnavailable());
    IOException e = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
        () -> d.build(dir.toString(), dir.resolve("out").toString(), TIMEOUT, log::add));
    assertEquals("it failed", e.getMessage());
    assertFalse(d.isRunningFor(dir.toString(), dir.resolve("out").toString()));
  }

  @Test
  void updatesWriteWhatABuildWrites() throws Exception {
    assumeTrue(jekyllInstalled(), "jekyll is not installed");
    Path src = Files.createDirectories(dir.resolve("src"));
    Path out = dir.resolve("out");
    write(src, "_config.yml", "exclude: [\"*.ignored\"]\n");
    write(src, "_includes/inc.html", "<p>included {{ include.x }}</p>\n");
    write(src, "_includes/other.html", "<p>other</p>\n");
    write(src, "a.html", "---\ntitle: A\n---\n<h1>{{ page.title }}</h1>\n{% include inc.html x=\"1\" %}\n");
    write(src, "b.html", "---\ntitle: B\n---\n<h1>{{ page.title }}</h1>{% include other.html %}\n");
    write(src, "c.md", "---\ntitle: C\n---\n# {{ page.title }}\n\nSome *markdown*.\n");
    write(src, "style.css", "body { }\n");
    write(src, "data.ignored", "x\n");

    driver = JekyllDriver.forCommand("jekyll");
    JekyllDriver.Result r = driver.build(src.toString(), out.toString(), TIMEOUT, log::add);
    assertTrue(r.isOk(), r.getError());
    assertTrue(r.isFull());
    sameAsBuild(src, out);

    // a page: just that page
    write(src, "b.html", "---\ntitle: B2\n---\n<h1>{{ page.title }}</h1>{% include other.html %}\n");
    r = update(src, out, "b.html");
    assertFalse(r.isFull(), r.getReason());
    assertEquals(Arrays.asList("b.html"), r.getWritten());
    sameAsBuild(src, out);

    // an include: the pages that include it
    write(src, "_includes/inc.html", "<p>included again {{ include.x }}</p>\n");
    r = update(src, out, "_includes/inc.html");
    assertFalse(r.isFull(), r.getReason());
    assertEquals(Arrays.asList("a.html"), r.getWritten());
    sameAsBuild(src, out);

    // a page that now includes something else
    write(src, "c.md", "---\ntitle: C\n---\n# {{ page.title }}\n\n{% include other.html %}\n");
    r = update(src, out, "c.md");
    assertEquals(Arrays.asList("c.html"), r.getWritten());
    write(src, "_includes/other.html", "<p>other again</p>\n");
    r = update(src, out, "_includes/other.html");
    assertEquals(Arrays.asList("b.html", "c.html"), sorted(r.getWritten()));
    sameAsBuild(src, out);

    // a static file
    write(src, "style.css", "body { color: red; }\n");
    r = update(src, out, "style.css");
    assertEquals(Arrays.asList("style.css"), r.getWritten());
    sameAsBuild(src, out);

    // something written to the output that isn't the site's goes, as a build removes it
    write(out, "extra.txt", "x");
    r = update(src, out, "a.html");
    assertFalse(Files.exists(out.resolve("extra.txt")));
    sameAsBuild(src, out);

    // a rewrite puts back what the last build wrote
    write(out, "a.html", "changed after Jekyll");
    r = driver.rewrite(src.toString(), out.toString(), Arrays.asList("a.html", "style.css", "nothing.html"), TIMEOUT, log::add);
    assertTrue(r.isOk(), r.getError());
    assertEquals(Arrays.asList("nothing.html"), r.getUnknown());
    sameAsBuild(src, out);

    // what was changed after Jekyll wrote it is known, however it was changed
    assertTrue(driver.isAsWritten(out.toString(), "c.html"));
    write(out, "c.html", "changed after Jekyll");
    assertFalse(driver.isAsWritten(out.toString(), "c.html"));
    Path b = out.resolve("b.html");
    Path moved = dir.resolve("b.tmp");
    Files.copy(b, moved);
    Files.move(moved, b, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    assertFalse(driver.isAsWritten(out.toString(), "b.html"));
    List<String> changed = driver.changedSinceWritten(src.toString(), out.toString());
    assertEquals(Arrays.asList("b.html", "c.html"), changed);
    r = driver.rewrite(src.toString(), out.toString(), changed, TIMEOUT, log::add);
    assertTrue(r.isOk(), r.getError());
    assertEquals(Arrays.asList("b.html", "c.html"), sorted(r.getWritten()));
    assertTrue(driver.isAsWritten(out.toString(), "b.html"));
    sameAsBuild(src, out);
    assertEquals(Collections.emptyList(), driver.changedSinceWritten(src.toString(), out.toString()));

    // a page that reads other pages: only a full build can be sure
    write(src, "b.html", "---\ntitle: B\n---\n{% for p in site.pages %}{{ p.title }} {% endfor %}\n");
    r = update(src, out, "b.html");
    assertTrue(r.isFull());
    sameAsBuild(src, out);
    write(src, "a.html", "---\ntitle: A3\n---\n<h1>{{ page.title }}</h1>\n");
    r = update(src, out, "a.html");
    assertTrue(r.isFull());
    sameAsBuild(src, out);
    write(src, "b.html", "---\ntitle: B\n---\n<h1>{{ page.title }}</h1>\n");
    r = update(src, out, "b.html");
    assertTrue(r.isFull());
    r = update(src, out, "a.html");
    assertFalse(r.isFull(), r.getReason());

    // _data, a file that's gone: a full build
    write(src, "_data/x.json", "{\"a\": 1}\n");
    r = update(src, out, "_data/x.json");
    assertTrue(r.isFull());
    Files.delete(src.resolve("c.md"));
    r = update(src, out, "c.md");
    assertTrue(r.isFull());
    sameAsBuild(src, out);

    // a page that Jekyll can't render fails, as a build does; the next update builds everything
    write(src, "a.html", "---\ntitle: A\n---\n{% include no-such-include.html %}\n");
    r = driver.update(src.toString(), out.toString(), Arrays.asList("a.html"), TIMEOUT, log::add);
    assertFalse(r.isOk());
    assertFalse(cliBuild(src, dir.resolve("failed")) == 0);
    write(src, "a.html", "---\ntitle: A\n---\nfine\n");
    r = update(src, out, "a.html");
    assertTrue(r.isOk(), r.getError());
    assertTrue(r.isFull());
    sameAsBuild(src, out);
  }

  @Test
  void siteInANameOrAStringIsNotTheSite() throws Exception {
    assumeTrue(jekyllInstalled(), "jekyll is not installed");
    Path src = Files.createDirectories(dir.resolve("src"));
    Path out = dir.resolve("out");
    write(src, "_includes/frag.html", "<p>{{ include.type }} {{ include.id }} {{ include.site }}</p>\n");
    write(src, "a.html", "---\ntitle: A\n---\n{% include frag.html type='CodeSystem' id='body-site-cs' site=\"site\" %}\n");
    write(src, "b.html", "---\ntitle: B\nsite: x\n---\n{{-page.site-}} {% assign my-site = page.title %}{{ my-site }} {{ site.data.d.v }}\n");
    write(src, "_data/d.json", "{\"v\": 1}\n");

    driver = JekyllDriver.forCommand("jekyll");
    assertTrue(driver.build(src.toString(), out.toString(), TIMEOUT, log::add).isOk());
    write(src, "_includes/frag.html", "<p>{{ include.id }}</p>\n");
    JekyllDriver.Result r = update(src, out, "_includes/frag.html");
    assertFalse(r.isFull(), r.getReason());
    assertEquals(Arrays.asList("a.html"), r.getWritten());
    sameAsBuild(src, out);

    // the site variable itself, even with the hyphens that control whitespace
    write(src, "b.html", "---\ntitle: B\n---\n{{-site.time-}}\n");
    r = update(src, out, "b.html");
    assertTrue(r.isFull());
    write(src, "b.html", "---\ntitle: B\n---\n{{ page.title }}\n");
    r = update(src, out, "b.html");
    assertTrue(r.isFull());
    r = update(src, out, "b.html");
    assertFalse(r.isFull(), r.getReason());
  }

  @Test
  void aFullRenderOnlyWritesWhatChanged() throws Exception {
    assumeTrue(jekyllInstalled(), "jekyll is not installed");
    Path src = Files.createDirectories(dir.resolve("src"));
    Path out = dir.resolve("out");
    // b reads the other pages, so every update renders the whole site
    write(src, "a.html", "---\ntitle: A\n---\n<h1>{{ page.title }}</h1>\n");
    write(src, "b.html", "---\ntitle: B\n---\n{% for p in site.pages %}{{ p.title }} {% endfor %}\n");
    write(src, "c.html", "---\ntitle: C\n---\n<h1>{{ page.title }}</h1>\n");
    write(src, "style.css", "body { }\n");
    write(src, "logo.txt", "logo\n");

    driver = JekyllDriver.forCommand("jekyll");
    JekyllDriver.Result r = driver.build(src.toString(), out.toString(), TIMEOUT, log::add);
    assertTrue(r.isOk(), r.getError());
    assertTrue(r.isAllWritten());

    // a page whose title the other pages show: it and the page that lists the titles
    write(src, "a.html", "---\ntitle: A2\n---\n<h1>{{ page.title }}</h1>\n");
    r = update(src, out, "a.html");
    assertTrue(r.isFull());
    assertFalse(r.isAllWritten());
    assertEquals(Arrays.asList("a.html", "b.html"), r.getWritten());
    sameAsBuild(src, out);

    // what changed an output file after Jekyll wrote it (the html checks) is left, unless Jekyll writes something new
    // there; a static file is copied when its source has been written to, even with the same content
    write(out, "c.html", "checked");
    write(out, "a.html", "checked");
    write(src, "a.html", "---\ntitle: A3\n---\n<h1>{{ page.title }}</h1>\n");
    write(src, "style.css", "body { color: red; }\n");
    write(src, "logo.txt", "logo\n");
    r = update(src, out, "a.html", "style.css", "logo.txt");
    assertTrue(r.isFull());
    assertEquals(Arrays.asList("a.html", "b.html", "logo.txt", "style.css"), r.getWritten());
    assertEquals("checked", new String(Files.readAllBytes(out.resolve("c.html")), StandardCharsets.UTF_8));
    assertFalse(driver.isAsWritten(out.toString(), "c.html"));
    assertTrue(driver.isAsWritten(out.toString(), "a.html"));
    assertEquals(Arrays.asList("c.html"), driver.changedSinceWritten(src.toString(), out.toString()));
    r = driver.rewrite(src.toString(), out.toString(), Arrays.asList("c.html"), TIMEOUT, log::add);
    assertTrue(r.isOk(), r.getError());
    sameAsBuild(src, out);

    // a file that's gone from the output is written again
    Files.delete(out.resolve("c.html"));
    r = update(src, out, "a.html");
    assertEquals(Arrays.asList("c.html"), r.getWritten());
    sameAsBuild(src, out);

    // nothing is left as it was after a command failed
    write(src, "a.html", "---\ntitle: A\n---\n{% include no-such-include.html %}\n");
    assertFalse(driver.update(src.toString(), out.toString(), Arrays.asList("a.html"), TIMEOUT, log::add).isOk());
    write(src, "a.html", "---\ntitle: A3\n---\n<h1>{{ page.title }}</h1>\n");
    r = update(src, out, "a.html");
    assertTrue(r.isAllWritten());
    sameAsBuild(src, out);
  }

  private JekyllDriver.Result update(Path src, Path out, String... changed) throws IOException {
    JekyllDriver.Result r = driver.update(src.toString(), out.toString(), Arrays.asList(changed), TIMEOUT, log::add);
    assertTrue(r.isOk(), r.getError() + " " + log);
    return r;
  }

  private void sameAsBuild(Path src, Path out) throws Exception {
    Path ref = dir.resolve("ref");
    deleteTree(ref);
    assertEquals(0, cliBuild(src, ref));
    Map<String, byte[]> a = files(ref);
    Map<String, byte[]> b = files(out);
    assertEquals(a.keySet(), b.keySet());
    for (String k : a.keySet()) {
      assertArrayEquals(a.get(k), b.get(k), k);
    }
  }

  private static int cliBuild(Path src, Path dest) throws Exception {
    Process p = new ProcessBuilder("jekyll", "build", "--destination", dest.toString()).directory(src.toFile())
        .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
    assertTrue(p.waitFor(2, TimeUnit.MINUTES));
    return p.exitValue();
  }

  private static boolean jekyllInstalled() {
    try {
      Process p = new ProcessBuilder("jekyll", "--version").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
      return p.waitFor(1, TimeUnit.MINUTES) && p.exitValue() == 0 && JekyllDriver.forCommand("jekyll") != null;
    } catch (Exception e) {
      return false;
    }
  }

  private static Map<String, byte[]> files(Path root) throws IOException {
    Map<String, byte[]> res = new TreeMap<>();
    try (Stream<Path> s = Files.walk(root)) {
      for (Path p : (Iterable<Path>) s::iterator) {
        if (Files.isRegularFile(p)) {
          res.put(root.relativize(p).toString(), Files.readAllBytes(p));
        }
      }
    }
    return res;
  }

  private static void deleteTree(Path p) throws IOException {
    if (Files.exists(p)) {
      try (Stream<Path> s = Files.walk(p)) {
        List<Path> all = new ArrayList<>();
        s.forEach(all::add);
        Collections.reverse(all);
        for (Path x : all) {
          Files.delete(x);
        }
      }
    }
  }

  private static List<String> sorted(List<String> l) {
    List<String> res = new ArrayList<>(l);
    Collections.sort(res);
    return res;
  }

  private static void write(Path root, String rel, String content) throws IOException {
    Path f = root.resolve(rel);
    Files.createDirectories(f.getParent());
    Files.write(f, content.getBytes(StandardCharsets.UTF_8));
  }
}
