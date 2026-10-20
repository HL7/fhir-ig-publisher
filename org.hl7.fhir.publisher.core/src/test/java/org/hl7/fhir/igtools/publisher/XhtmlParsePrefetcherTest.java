package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XhtmlParsePrefetcherTest {

  @TempDir
  Path dir;

  private static final String PAGE = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>t</title></head>"
      + "<body><p>one <b>two</b> &amp; <a href=\"x.html\">three</a></p><br>unclosed</body></html>";

  private File write(String name, String content) throws IOException {
    File f = dir.resolve(name).toFile();
    Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
    return f;
  }

  private static String compose(XhtmlNode x) throws IOException {
    return new XhtmlComposer(false, true).compose(x);
  }

  private static XhtmlParsePrefetcher.Job parseJob(boolean strict) {
    return path -> XhtmlParsePrefetcher.list(XhtmlParsePrefetcher.parseBytes(XhtmlParsePrefetcher.read(new File(path)), strict));
  }

  /** A job that counts down {@code started} once it is under way, after which take() waits for it rather than skipping it. */
  private static XhtmlParsePrefetcher.Job counting(XhtmlParsePrefetcher.Job job, CountDownLatch started) {
    return path -> {
      started.countDown();
      return job.run(path);
    };
  }

  @Test
  void prefetchedTreeIsTheTreeAFreshParseGives() throws Exception {
    List<String> paths = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      paths.add(write("p" + i + ".html", PAGE.replace("three", "three " + i)).getAbsolutePath());
    }
    CountDownLatch started = new CountDownLatch(paths.size());
    try (XhtmlParsePrefetcher p = new XhtmlParsePrefetcher(paths, counting(parseJob(false), started), 3, paths.size())) {
      started.await();
      for (int i = 0; i < paths.size(); i++) {
        File f = new File(paths.get(i));
        XhtmlParser parser = new XhtmlParser().setMustBeWellFormed(false);
        XhtmlNode expected = parser.parse(f, null);
        List<String> notes = new ArrayList<>();
        XhtmlParsePrefetcher.Parsings got = p.take(i);
        assertNotNull(got, "file " + i + " should have been prefetched");
        XhtmlNode actual = XhtmlParsePrefetcher.parse(f, false, got, notes);
        assertEquals(compose(expected), compose(actual));
        assertEquals(expected.getClass(), actual.getClass());
        assertEquals(parser.getRecoveryNotes(), notes);
        assertTrue(!notes.isEmpty(), "the page has an unclosed element, so the parser should have noted a recovery");
      }
    }
  }

  @Test
  void changedFileIsParsedAgain() throws Exception {
    File a = write("a.html", PAGE);
    File b = write("b.html", PAGE);
    List<String> paths = List.of(a.getAbsolutePath(), b.getAbsolutePath());
    CountDownLatch started = new CountDownLatch(1);
    try (XhtmlParsePrefetcher p = new XhtmlParsePrefetcher(paths, counting(parseJob(false), started), 1, 2)) {
      started.await();
      XhtmlParsePrefetcher.Parsings got = p.take(0);
      assertNotNull(got);
      write("a.html", PAGE.replace("one", "changed"));
      XhtmlNode x = XhtmlParsePrefetcher.parse(a, false, got, null);
      assertTrue(compose(x).contains("changed"));
    }
  }

  @Test
  void eachParseIsHandedOutOnce() throws Exception {
    File a = write("a.html", PAGE);
    byte[] bytes = XhtmlParsePrefetcher.read(a);
    XhtmlParsePrefetcher.Parsed first = XhtmlParsePrefetcher.parseBytes(bytes, false);
    XhtmlParsePrefetcher.Parsed second = XhtmlParsePrefetcher.parseBytes(bytes, false);
    XhtmlParsePrefetcher.Parsings got = new XhtmlParsePrefetcher.Parsings(XhtmlParsePrefetcher.list(first, second));
    XhtmlNode x1 = XhtmlParsePrefetcher.parse(a, false, got, null);
    XhtmlNode x2 = XhtmlParsePrefetcher.parse(a, false, got, null);
    XhtmlNode x3 = XhtmlParsePrefetcher.parse(a, false, got, null);
    assertSame(XhtmlParsePrefetcher.node(first), x1);
    assertSame(XhtmlParsePrefetcher.node(second), x2);
    assertNotSame(x1, x3);
    assertNotSame(x2, x3);
    assertEquals(compose(x1), compose(x3));
  }

  @Test
  void aParseGivenBackIsReusedOnlyForTheSameBytes() throws Exception {
    File a = write("a.html", PAGE);
    XhtmlParsePrefetcher.Parsings parsings = new XhtmlParsePrefetcher.Parsings();
    XhtmlParsePrefetcher.Parsed first = XhtmlParsePrefetcher.parseFile(a, false, parsings);
    parsings.add(first);
    assertSame(XhtmlParsePrefetcher.node(first), XhtmlParsePrefetcher.parse(a, false, parsings, null));

    parsings.add(first);
    write("a.html", PAGE.replace("one", "rewritten"));
    XhtmlNode x = XhtmlParsePrefetcher.parse(a, false, parsings, null);
    assertNotSame(XhtmlParsePrefetcher.node(first), x);
    assertTrue(compose(x).contains("rewritten"));
  }

  @Test
  void failedPrefetchGivesTheSameErrorAsAFreshParse() throws Exception {
    File bad = write("bad.html", PAGE); // the unclosed <br> is an error when the page must be well formed
    File missing = dir.resolve("missing.html").toFile();
    List<String> paths = List.of(bad.getAbsolutePath(), missing.getAbsolutePath());
    try (XhtmlParsePrefetcher p = new XhtmlParsePrefetcher(paths, parseJob(true), 2, 2)) {
      Exception expected = assertThrows(Exception.class, () -> new XhtmlParser().setMustBeWellFormed(true).parse(bad, null));
      Exception actual = assertThrows(Exception.class, () -> XhtmlParsePrefetcher.parse(bad, true, p.take(0), null));
      assertEquals(expected.getClass(), actual.getClass());
      assertEquals(expected.getMessage(), actual.getMessage());

      Exception expectedMissing = assertThrows(Exception.class, () -> new XhtmlParser().parse(missing, null));
      Exception actualMissing = assertThrows(Exception.class, () -> XhtmlParsePrefetcher.parse(missing, true, p.take(1), null));
      assertEquals(expectedMissing.getClass(), actualMissing.getClass());
    }
  }

  @Test
  void workersStayWithinTheWindow() throws Exception {
    int n = 30;
    int window = 3;
    List<String> paths = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      paths.add(String.valueOf(i));
    }
    AtomicInteger maxAhead = new AtomicInteger();
    AtomicInteger taken = new AtomicInteger();
    XhtmlParsePrefetcher.Job job = path -> {
      int ahead = Integer.parseInt(path) - taken.get();
      maxAhead.accumulateAndGet(ahead, Math::max);
      return Collections.emptyList();
    };
    try (XhtmlParsePrefetcher p = new XhtmlParsePrefetcher(paths, job, 3, window)) {
      for (int i = 0; i < n; i++) {
        Thread.sleep(2);
        p.take(i);
        taken.set(i + 1);
      }
    }
    // taken lags the prefetcher's own count by at most the one take in progress
    assertTrue(maxAhead.get() <= window, "workers ran " + maxAhead.get() + " files ahead with a window of " + window);
  }

  @Test
  void workersStopWhenTheFilesAheadAreBigEnough() throws Exception {
    List<String> paths = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      paths.add(write("f" + i + ".html", "0123456789").getAbsolutePath()); // 10 bytes each
    }
    AtomicInteger maxAhead = new AtomicInteger();
    AtomicInteger taken = new AtomicInteger();
    XhtmlParsePrefetcher.Job job = path -> {
      int index = Integer.parseInt(new File(path).getName().replaceAll("\\D", ""));
      maxAhead.accumulateAndGet(index - taken.get(), Math::max);
      return Collections.emptyList();
    };
    // a window of 10 files, but only 30 bytes: the workers start at most 3 files ahead
    try (XhtmlParsePrefetcher p = new XhtmlParsePrefetcher(paths, job, 3, 10, 30)) {
      for (int i = 0; i < paths.size(); i++) {
        Thread.sleep(2);
        p.take(i);
        taken.set(i + 1);
      }
    }
    assertTrue(maxAhead.get() <= 3, "workers ran " + maxAhead.get() + " files ahead with room for 3");
  }

  @Test
  void skippedFilesAreNotPrefetchedLater() throws Exception {
    List<String> paths = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      paths.add(String.valueOf(i));
    }
    List<String> ran = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch release = new CountDownLatch(1);
    XhtmlParsePrefetcher.Job job = path -> {
      if (path.equals("0")) {
        release.await();
      }
      ran.add(path);
      return Collections.emptyList();
    };
    try (XhtmlParsePrefetcher p = new XhtmlParsePrefetcher(paths, job, 1, 10)) {
      // the only worker is held on file 0 (or hasn't started); jumping to 5 skips 0-4, and 5 isn't under way
      assertNull(p.take(5));
      release.countDown();
      for (int i = 6; i < 10; i++) {
        p.take(i);
      }
    }
    for (int i = 1; i <= 5; i++) {
      assertTrue(!ran.contains(String.valueOf(i)), "file " + i + " was prefetched after being skipped");
    }
  }

  @Test
  void takeAfterCloseFallsBack() throws Exception {
    File a = write("a.html", PAGE);
    File b = write("b.html", PAGE);
    List<String> paths = List.of(a.getAbsolutePath(), b.getAbsolutePath());
    XhtmlParsePrefetcher p = new XhtmlParsePrefetcher(paths, parseJob(false), 1, 2);
    p.close();
    assertNull(p.take(0));
    assertNull(p.take(1));
    assertNull(p.take(2));
    assertEquals(compose(new XhtmlParser().setMustBeWellFormed(false).parse(a, null)), compose(XhtmlParsePrefetcher.parse(a, false, null, null)));
  }

  @Test
  void bigFilesAreLeftToTheCaller() throws Exception {
    StringBuilder b = new StringBuilder("<html><body>");
    while (b.length() <= XhtmlParsePrefetcher.MAX_PREFETCH_BYTES) {
      b.append("<p>0123456789012345678901234567890123456789012345678901234567890123456789</p>");
    }
    b.append("</body></html>");
    File big = write("big.html", b.toString());
    File small = write("small.html", PAGE);
    List<String> paths = List.of(big.getAbsolutePath(), small.getAbsolutePath());
    CountDownLatch started = new CountDownLatch(1);
    try (XhtmlParsePrefetcher p = new XhtmlParsePrefetcher(paths, counting(parseJob(false), started), 1, 2)) {
      started.await(); // the job only runs for the small file
      assertNull(p.take(0));
      assertNotNull(p.take(1));
    }
  }
}
