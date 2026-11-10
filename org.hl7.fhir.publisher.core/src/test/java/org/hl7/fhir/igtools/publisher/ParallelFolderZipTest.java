package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipException;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.hl7.fhir.utilities.ZipGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParallelFolderZipTest {

  private static final byte[] REDIRECT = "<html>redirect ü</html>".getBytes(StandardCharsets.UTF_8);

  @TempDir
  Path tmp;

  /** an entry as a reader sees it, and its compressed data */
  private static final class Entry {
    final String name;
    final int method;
    final long crc;
    final long size;
    final long csize;
    final byte[] raw;
    final byte[] content;

    Entry(ZipFile zip, ZipArchiveEntry e) throws IOException {
      name = e.getName();
      method = e.getMethod();
      crc = e.getCrc();
      size = e.getSize();
      csize = e.getCompressedSize();
      try (InputStream in = zip.getRawInputStream(e)) {
        raw = in.readAllBytes();
      }
      try (InputStream in = zip.getInputStream(e)) {
        content = in.readAllBytes();
      }
    }

    @Override
    public String toString() {
      return name + " method " + method + " crc " + crc + " size " + size + " csize " + csize;
    }
  }

  private static List<Entry> entries(Path zip) throws IOException {
    List<Entry> res = new ArrayList<>();
    try (ZipFile z = ZipFile.builder().setPath(zip).get()) {
      for (ZipArchiveEntry e : Collections.list(z.getEntriesInPhysicalOrder())) {
        res.add(new Entry(z, e));
      }
    }
    // the JDK reader checks every CRC as it reads
    try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(zip.toFile())) {
      for (java.util.zip.ZipEntry e : Collections.list(z.entries())) {
        try (InputStream in = z.getInputStream(e)) {
          in.readAllBytes();
        }
      }
    }
    return res;
  }

  /** what PublisherGenerator wrote before: ZipGenerator.addFolder(), then the redirect page */
  private Path master(Path folder) throws IOException {
    Path zip = tmp.resolve("master.zip");
    ZipGenerator gen = new ZipGenerator(zip.toString());
    gen.addFolder(folder.toString(), "site/", false);
    gen.addFileSource("index.html", new String(REDIRECT, StandardCharsets.UTF_8), false);
    gen.close();
    return zip;
  }

  private Path parallel(Path folder, int threads, long maxInFlight) throws IOException {
    Path zip = tmp.resolve("parallel-" + threads + "-" + maxInFlight + ".zip");
    new ParallelFolderZip(threads, maxInFlight).write(zip.toString(), folder.toString(), "site/", "index.html", REDIRECT);
    return zip;
  }

  private static void assertSameEntries(Path expected, Path actual) throws IOException {
    List<Entry> e = entries(expected);
    List<Entry> a = entries(actual);
    assertEquals(e.size(), a.size());
    for (int i = 0; i < e.size(); i++) {
      assertEquals(e.get(i).toString(), a.get(i).toString(), "entry " + i);
      assertArrayEquals(e.get(i).raw, a.get(i).raw, "compressed data of " + e.get(i).name);
      assertArrayEquals(e.get(i).content, a.get(i).content, "content of " + e.get(i).name);
    }
  }

  private static void write(Path p, byte[] b) throws IOException {
    Files.createDirectories(p.getParent());
    Files.write(p, b);
  }

  private static byte[] text(int size, int seed) {
    Random r = new Random(seed);
    String[] words = {"<div>", "Patient", "Observation", "текст", "</td>", "valueQuantity", " "};
    StringBuilder b = new StringBuilder();
    while (b.length() < size) {
      b.append(words[r.nextInt(words.length)]);
    }
    return b.substring(0, size).getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] random(int size, int seed) {
    byte[] b = new byte[size];
    new Random(seed).nextBytes(b);
    return b;
  }

  /** an output folder like an IG's: pages, nested folders, empty folders and files, .DS_Store, binary files */
  private Path site() throws IOException {
    Path site = tmp.resolve("output");
    for (int i = 0; i < 300; i++) {
      // sizes vary a lot so that the workers finish out of order
      write(site.resolve("page-" + i + ".html"), text((i * 7919) % 200_000, i));
    }
    write(site.resolve("en/StructureDefinition-a.html"), text(50_000, 1));
    write(site.resolve("ru/страница.html"), text(3_000, 2));
    write(site.resolve("deep/a/b/c/d.json"), text(10, 3));
    write(site.resolve("images/logo.png"), random(300_000, 4));
    write(site.resolve("empty.txt"), new byte[0]);
    write(site.resolve(".DS_Store"), text(100, 5));
    write(site.resolve("sub/.DS_Store"), text(100, 6));
    Files.createDirectories(site.resolve("empty-folder"));
    Files.createDirectories(site.resolve("nested/empty/folders"));
    return site;
  }

  @Test
  void sameEntriesInTheSameOrderAsZipGenerator() throws IOException {
    Path site = site();
    Path master = master(site);
    for (int threads : new int[] {1, 3, 7}) {
      Set<Thread> before = liveThreads();
      Path zip = parallel(site, threads, ParallelFolderZip.MAX_IN_FLIGHT);
      assertNoNewThreads(before);
      assertSameEntries(master, zip);
    }
  }

  @Test
  void entriesAreOnlyTheFilesAndTheRedirect() throws IOException {
    Path site = site();
    List<Entry> a = entries(parallel(site, 3, ParallelFolderZip.MAX_IN_FLIGHT));
    // no folder entries (none for empty folders either), no .DS_Store; the redirect is last
    assertEquals(300 + 5 + 1, a.size());
    for (Entry e : a) {
      assertFalse(e.name.endsWith("/"), e.name);
      assertFalse(e.name.endsWith(".DS_Store"), e.name);
    }
    assertEquals("index.html", a.get(a.size() - 1).name);
    assertArrayEquals(REDIRECT, a.get(a.size() - 1).content);
  }

  @Test
  void emptyFolder() throws IOException {
    Path site = tmp.resolve("empty");
    Files.createDirectories(site.resolve("a/b"));
    Path zip = parallel(site, 2, ParallelFolderZip.MAX_IN_FLIGHT);
    assertSameEntries(master(site), zip);
    assertEquals(1, entries(zip).size());
  }

  /** the temporary files for big files go next to the zip, and are gone once write() returns */
  private void assertNoTempFiles() throws IOException {
    try (java.util.stream.Stream<Path> l = Files.list(tmp)) {
      List<Path> left = l.filter(p -> p.getFileName().toString().endsWith(".deflate")).toList();
      assertTrue(left.isEmpty(), "temporary files left: " + left);
    }
  }

  @Test
  void bigFilesGoThroughTempFilesAndMemoryStaysBounded() throws IOException {
    Path site = tmp.resolve("large");
    long budget = 1 << 20;
    // files over the budget (compressible and not), between ones under it, and one exactly at it
    write(site.resolve("a-small.html"), text(10_000, 1));
    write(site.resolve("b-large.html"), text(5 << 20, 2));
    write(site.resolve("c-small.html"), text(700_000, 3));
    write(site.resolve("d-large.bin"), random(3 << 20, 4));
    write(site.resolve("e-at-budget.html"), text((int) budget, 5));
    for (int i = 0; i < 40; i++) {
      write(site.resolve("f/page-" + i + ".html"), text(300_000, 10 + i));
    }
    write(site.resolve("g-large.html"), text(2 << 20, 6));
    Path master = master(site);
    ParallelFolderZip zip = new ParallelFolderZip(4, budget);
    Path out = tmp.resolve("large.zip");
    zip.write(out.toString(), site.toString(), "site/", "index.html", REDIRECT);
    assertSameEntries(master, out);
    assertTrue(zip.peakInFlight() <= budget, "peak " + zip.peakInFlight());
    assertTrue(zip.peakInFlight() > 0);
    assertNoTempFiles();
  }

  @Test
  void symbolicLinksAsZipGenerator() throws IOException {
    Path site = tmp.resolve("links");
    write(site.resolve("real/file.html"), text(1000, 1));
    try {
      Files.createSymbolicLink(site.resolve("link-to-file.html"), site.resolve("real/file.html"));
      Files.createSymbolicLink(site.resolve("link-to-folder"), site.resolve("real"));
      Files.createSymbolicLink(site.resolve("broken.html"), site.resolve("nowhere.html"));
    } catch (UnsupportedOperationException | IOException e) {
      assumeTrue(false, "no symbolic links here: " + e);
    }
    Path zip = parallel(site, 2, ParallelFolderZip.MAX_IN_FLIGHT);
    assertSameEntries(master(site), zip);
    List<String> names = new ArrayList<>();
    for (Entry e : entries(zip)) {
      names.add(e.name);
    }
    Collections.sort(names);
    assertEquals(List.of("index.html", "site/link-to-file.html", "site/real/file.html"), names);
  }

  private Path unreadable(Path site, String name, byte[] content) throws IOException {
    Path p = site.resolve(name);
    write(p, content);
    try {
      Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("-w-------"));
    } catch (UnsupportedOperationException e) {
      assumeTrue(false, "no posix permissions here");
    }
    assumeTrue(!Files.isReadable(p), "the file is still readable (running as root?)");
    return p;
  }

  private void assertFailsWithoutZip(Path site, long budget) throws IOException {
    assertFailsWithoutZip(new ParallelFolderZip(3, budget), site);
  }

  private IOException assertFailsWithoutZip(ParallelFolderZip pz, Path site) throws IOException {
    Path zip = tmp.resolve("failed.zip");
    Files.write(zip, new byte[] {1, 2, 3}); // a zip left from before goes too: it is not this build's
    Set<Thread> before = liveThreads();
    IOException e = assertThrows(IOException.class, () -> pz.write(zip.toString(), site.toString(), "site/", "index.html", REDIRECT));
    assertFalse(Files.exists(zip), "an incomplete zip was left behind");
    assertNoNewThreads(before);
    assertNoTempFiles();
    return e;
  }

  private static Set<Thread> liveThreads() {
    Set<Thread> res = new HashSet<>();
    for (Thread t : Thread.getAllStackTraces().keySet()) {
      if (t.isAlive()) {
        res.add(t);
      }
    }
    return res;
  }

  /** write() waits for its workers, so none is left once it returns */
  private static void assertNoNewThreads(Set<Thread> before) {
    Set<Thread> now = liveThreads();
    now.removeAll(before);
    // awaitTermination() can return while the last workers are still returning from run()
    for (Thread t : now) {
      try {
        t.join(5000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    now.removeIf(t -> !t.isAlive());
    assertTrue(now.isEmpty(), "threads left running: " + now);
  }

  /** a ParallelFolderZip whose files are read through a stream the test controls */
  private static class Seam extends ParallelFolderZip {
    interface Opener {
      InputStream open(File f, InputStream real) throws IOException;
    }

    final Opener opener;

    Seam(int threads, long maxInFlight, Opener opener) {
      super(threads, maxInFlight);
      this.opener = opener;
    }

    @Override
    InputStream open(File f) throws IOException {
      return opener.open(f, super.open(f));
    }
  }

  /** a stream that fails once it has given out limit bytes */
  private static InputStream failingAfter(InputStream in, long limit) {
    return new FilterInputStream(in) {
      long n;

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        if (n >= limit) {
          throw new IOException("disk error");
        }
        int r = super.read(b, off, (int) Math.min(len, Math.max(1, limit - n)));
        n += Math.max(0, r);
        return r;
      }
    };
  }

  @Test
  void workerFailsHalfwayThroughAFile() throws IOException {
    Path site = tmp.resolve("fail-mid-worker");
    for (int i = 0; i < 60; i++) {
      write(site.resolve(String.format("page-%02d.html", i)), text(50_000, i));
    }
    ParallelFolderZip pz = new Seam(3, ParallelFolderZip.MAX_IN_FLIGHT,
        (f, in) -> f.getName().equals("page-40.html") ? failingAfter(in, 20_000) : in);
    IOException e = assertFailsWithoutZip(pz, site);
    assertTrue(e.getMessage().contains("page-40.html"), e.getMessage());
  }

  @Test
  void bigFileFailsHalfway() throws IOException {
    Path site = tmp.resolve("fail-mid-big");
    for (int i = 0; i < 20; i++) {
      write(site.resolve(String.format("page-%02d.html", i)), text(20_000, i));
    }
    write(site.resolve("large.html"), text(500_000, 99));
    ParallelFolderZip pz = new Seam(3, 100_000, (f, in) -> f.getName().equals("large.html") ? failingAfter(in, 300_000) : in);
    assertFailsWithoutZip(pz, site);
  }

  @Test
  void orderHoldsWhenTheFirstFileFinishesLast() throws Exception {
    Path site = tmp.resolve("slow-first");
    for (int i = 0; i < 40; i++) {
      write(site.resolve("page-" + i + ".html"), text(5_000 + i, i));
    }
    String first = new File(site.toFile().list()[0]).getName();
    CountDownLatch others = new CountDownLatch(10);
    AtomicLong waited = new AtomicLong();
    ParallelFolderZip pz = new Seam(3, ParallelFolderZip.MAX_IN_FLIGHT, (f, in) -> {
      if (!f.getName().equals(first)) {
        return new FilterInputStream(in) {
          @Override
          public void close() throws IOException {
            super.close();
            others.countDown();
          }
        };
      }
      // the first file is held back until ten later ones have been compressed
      try {
        if (others.await(30, TimeUnit.SECONDS)) {
          waited.incrementAndGet();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return in;
    });
    Path zip = tmp.resolve("slow-first.zip");
    pz.write(zip.toString(), site.toString(), "site/", "index.html", REDIRECT);
    assertEquals(1, waited.get(), "the first file wasn't held back");
    assertSameEntries(master(site), zip);
  }

  @Test
  void bigFilesStartFirstAndSmallOnesStayWithinTheBound() throws IOException {
    Path site = tmp.resolve("bound");
    long budget = 1 << 20;
    for (int i = 0; i < 60; i++) {
      write(site.resolve(String.format("page-%02d.html", i)), text(10_000 + (i * 3_700) % 100_000, i));
    }
    write(site.resolve("page-10b.html"), text(3 << 20, 1));
    write(site.resolve("page-30b.bin"), random(2 << 20, 2));
    write(site.resolve("page-50b.html"), text(500_000, 3));
    List<String> opened = Collections.synchronizedList(new ArrayList<>());
    Map<String, String> openedBy = new ConcurrentHashMap<>();
    AtomicLong open = new AtomicLong();
    AtomicLong peakOpen = new AtomicLong();
    long big = Math.min(ParallelFolderZip.BIG_FILE, budget / 8);
    ParallelFolderZip pz = new Seam(1, budget, (f, in) -> {
      opened.add(f.getName());
      openedBy.put(f.getName(), Thread.currentThread().getName());
      long len = f.length();
      boolean small = len <= big;
      if (small) {
        peakOpen.accumulateAndGet(open.addAndGet(len), Math::max);
      }
      return new FilterInputStream(in) {
        @Override
        public void close() throws IOException {
          super.close();
          if (small) {
            open.addAndGet(-len);
          }
        }
      };
    });
    Path zip = tmp.resolve("bound.zip");
    Set<Thread> before = liveThreads();
    assertEquals(big, pz.bigFile());
    pz.write(zip.toString(), site.toString(), "site/", "index.html", REDIRECT);
    assertNoNewThreads(before);
    // one worker: it does the big ones first, biggest first, then the rest in order
    assertEquals(List.of("page-10b.html", "page-30b.bin", "page-50b.html"), opened.subList(0, 3));
    for (Map.Entry<String, String> e : openedBy.entrySet()) {
      assertTrue(e.getValue().startsWith("ig-zip-"), e.getKey() + " was read on " + e.getValue());
    }
    assertTrue(peakOpen.get() <= budget, "open " + peakOpen.get());
    assertSameEntries(master(site), zip);
    assertNoTempFiles();
  }

  @Test
  void workerFailureLeavesNoZip() throws IOException {
    Path site = tmp.resolve("fail-worker");
    for (int i = 0; i < 50; i++) {
      write(site.resolve("page-" + i + ".html"), text(20_000, i));
    }
    unreadable(site, "page-25b.html", text(1000, 99));
    assertFailsWithoutZip(site, ParallelFolderZip.MAX_IN_FLIGHT);
  }

  @Test
  void unreadableBigFileLeavesNoZip() throws IOException {
    Path site = tmp.resolve("fail-large");
    for (int i = 0; i < 20; i++) {
      write(site.resolve("page-" + i + ".html"), text(20_000, i));
    }
    unreadable(site, "large.html", text(200_000, 99));
    assertFailsWithoutZip(site, 100_000);
  }

  @Test
  void duplicateNameFailsBeforeWriting() throws IOException {
    Path site = tmp.resolve("dup");
    write(site.resolve("index.html"), text(100, 1));
    Path zip = tmp.resolve("dup.zip");
    assertThrows(ZipException.class, () -> new ParallelFolderZip(2).write(zip.toString(), site.toString(), "", "index.html", REDIRECT));
    assertFalse(Files.exists(zip));
  }

  @Test
  void needsAThread() {
    assertThrows(IllegalArgumentException.class, () -> new ParallelFolderZip(0));
    assertEquals(ParallelFolderZip.BIG_FILE, new ParallelFolderZip(1).bigFile());
  }

  @Test
  void theByteBoundStopsTheReadAhead() throws Exception {
    Path site = tmp.resolve("byte-bound");
    long budget = 1 << 20;
    for (int i = 0; i < 60; i++) {
      write(site.resolve(String.format("page-%02d.bin", i)), random(100_000, i));
    }
    String first = site.toFile().list()[0];
    CountDownLatch nine = new CountDownLatch(9);
    AtomicLong others = new AtomicLong();
    AtomicLong othersWhileHeld = new AtomicLong(-1);
    // 4 workers read up to 32 files ahead, but only 10 of these fit in the budget: the first (held back) and 9 more
    ParallelFolderZip pz = new Seam(4, budget, (f, in) -> {
      if (!f.getName().equals(first)) {
        others.addAndGet(f.length());
        nine.countDown();
        return in;
      }
      try {
        nine.await(30, TimeUnit.SECONDS);
        Thread.sleep(300); // time for a 10th file to be opened, if one could be
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      othersWhileHeld.set(others.get());
      return in;
    });
    Path zip = tmp.resolve("byte-bound.zip");
    pz.write(zip.toString(), site.toString(), "site/", "index.html", REDIRECT);
    assertEquals(9 * 100_000, othersWhileHeld.get(), "read ahead while the first file was held");
    assertTrue(pz.peakInFlight() <= budget && pz.peakInFlight() >= 10 * 100_000, "peak " + pz.peakInFlight());
    assertSameEntries(master(site), zip);
  }

  @Test
  void smallFileFailsWhileBigOnesAreUnderWay() throws IOException {
    Path site = tmp.resolve("fail-with-big");
    write(site.resolve("y-large.html"), text(2 << 20, 98));
    write(site.resolve("z-large.html"), text(4 << 20, 99));
    for (int i = 0; i < 30; i++) {
      write(site.resolve(String.format("page-%02d.html", i)), text(20_000, i));
    }
    // in the order the zip has them: a small file fails while the last big file is still being compressed (it waits
    // to be cancelled) and the other one is done but not written yet (or already written)
    List<String> order = List.of(site.toFile().list());
    String held = order.indexOf("y-large.html") > order.indexOf("z-large.html") ? "y-large.html" : "z-large.html";
    String other = held.equals("y-large.html") ? "z-large.html" : "y-large.html";
    String failing = order.stream().filter(n -> n.startsWith("page-")).findFirst().get();
    assumeTrue(order.indexOf(failing) < order.indexOf(held), "the file system lists the big files first: " + order);
    boolean waitForOther = order.indexOf(other) > order.indexOf(failing);
    CountDownLatch otherDone = new CountDownLatch(1);
    CountDownLatch heldWaits = new CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
    ParallelFolderZip pz = new Seam(3, 1 << 20, (f, in) -> {
      if (f.getName().equals(failing)) {
        return new FilterInputStream(in) {
          @Override
          public int read(byte[] b, int off, int len) throws IOException {
            try {
              if (waitForOther) {
                otherDone.await(30, TimeUnit.SECONDS);
              }
              heldWaits.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            throw new IOException("disk error");
          }
        };
      } else if (f.getName().equals(other)) {
        return new FilterInputStream(in) {
          @Override
          public int read(byte[] b, int off, int len) throws IOException {
            int r = super.read(b, off, len);
            if (r == -1) {
              otherDone.countDown();
            }
            return r;
          }
        };
      } else if (f.getName().equals(held)) {
        return new FilterInputStream(in) {
          long n;
          boolean waited;

          @Override
          public int read(byte[] b, int off, int len) throws IOException {
            if (n >= 65536 && !waited) {
              waited = true;
              heldWaits.countDown();
              try {
                new CountDownLatch(1).await(30, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                cancelled.set(true);
                throw new java.io.InterruptedIOException("cancelled");
              }
            }
            int r = super.read(b, off, len);
            n += Math.max(0, r);
            return r;
          }
        };
      }
      return in;
    });
    IOException e = assertFailsWithoutZip(pz, site);
    assertTrue(e.getMessage().contains(failing), e.getMessage());
    assertTrue(cancelled.get(), held + " was not cancelled");
  }

  @Test
  void interruptedWriteStillWaitsForTheWorkers() throws Exception {
    Path site = tmp.resolve("interrupted");
    for (int i = 0; i < 5; i++) {
      write(site.resolve("page-" + i + ".html"), text(5_000, i));
    }
    write(site.resolve("large.html"), text(1 << 20, 9));
    CountDownLatch opening = new CountDownLatch(1);
    // the worker of the big file has made its temporary file and takes a while, ignoring interrupts, to open the file
    ParallelFolderZip pz = new Seam(2, ParallelFolderZip.MAX_IN_FLIGHT, (f, in) -> {
      if (f.getName().equals("large.html")) {
        opening.countDown();
        boolean interrupted = false;
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(700);
        while (System.nanoTime() < end) {
          try {
            Thread.sleep(50);
          } catch (InterruptedException e) {
            interrupted = true;
          }
        }
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }
      return in;
    });
    Thread caller = Thread.currentThread();
    Thread interrupter = new Thread(() -> {
      try {
        opening.await(30, TimeUnit.SECONDS);
        Thread.sleep(100);
      } catch (InterruptedException e) {
        return;
      }
      caller.interrupt();
    });
    interrupter.start();
    Path zip = tmp.resolve("interrupted.zip");
    try {
      assertThrows(IOException.class, () -> pz.write(zip.toString(), site.toString(), "site/", "index.html", REDIRECT));
      assertTrue(Thread.interrupted(), "the interrupt was lost");
    } finally {
      interrupter.join();
      Thread.interrupted();
    }
    assertFalse(Files.exists(zip));
    Thread.sleep(1000); // a worker still running would make its file now
    assertNoTempFiles();
  }
}
