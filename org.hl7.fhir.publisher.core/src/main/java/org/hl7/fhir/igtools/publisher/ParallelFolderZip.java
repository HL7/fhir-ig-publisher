package org.hl7.fhir.igtools.publisher;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.filesystem.ManagedFileAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes the zip that ZipGenerator writes for addFolder(folder, prefix, false) followed by one more entry (name,
 * content), but compresses the files on several threads: the same entries, in the same order, with the same names
 * and the same compressed data (deflate, level 9). Only the zip's own headers are laid out differently (the sizes
 * go in the local header instead of a data descriptor), which readers don't see.
 * <p>
 * Worker threads compress the files and the calling thread writes them to the zip in order. Most files are compressed
 * into memory, a bounded number of bytes ahead of the writing. Big files are compressed into temporary files next to
 * the zip instead, and are all started first, biggest first, so that the longest jobs don't hold up the end. If
 * anything fails, the zip and the temporary files are deleted and the exception is thrown.
 */
public class ParallelFolderZip {

  private static final Logger log = LoggerFactory.getLogger(ParallelFolderZip.class);

  /** the most bytes of files being compressed into memory, or compressed and waiting to be written, at a time */
  static final long MAX_IN_FLIGHT = 64L << 20;
  /**
   * files bigger than this are compressed into temporary files, longest first. Measured on a 2.3GB IG output: with
   * 8MB, runs of several-MB pages ahead of a slow one filled the bound and left workers idle (9.1s on 7 threads);
   * with 256KB it is 4.6s, close to the CPU time divided by the threads, for ~200MB of temporary files at most
   */
  static final long BIG_FILE = 256L << 10;
  private static final int BUFFER = 65536;

  /** a file to add: its name in the zip, and where it is */
  private static final class Source {
    final String name;
    final File file;
    final long length;

    Source(String name, File file) {
      this.name = name;
      this.file = file;
      this.length = file.length();
    }
  }

  /** a file's compressed data: in memory (the first csize bytes of data), or in a temporary file */
  private static final class Compressed {
    final long size;
    final long crc;
    final long csize;
    final byte[] data;
    final File temp;

    Compressed(long size, long crc, long csize, byte[] data, File temp) {
      this.size = size;
      this.crc = crc;
      this.csize = csize;
      this.data = data;
      this.temp = temp;
    }
  }

  /** a ByteArrayOutputStream that hands over its array instead of a copy */
  private static final class Buffer extends ByteArrayOutputStream {
    Buffer(int size) {
      super(size);
    }

    byte[] array() {
      return buf;
    }
  }

  private final int threads;
  private final long maxInFlight;
  private final long big;
  private long peakInFlight;

  /** @param threads the number of worker threads, at least 1 */
  public ParallelFolderZip(int threads) {
    this(threads, MAX_IN_FLIGHT);
  }

  ParallelFolderZip(int threads, long maxInFlight) {
    if (threads < 1) {
      throw new IllegalArgumentException("threads must be at least 1, not " + threads);
    }
    this.threads = threads;
    this.maxInFlight = maxInFlight;
    this.big = Math.min(BIG_FILE, maxInFlight / 8);
  }

  /**
   * Writes the zip: the files in folder, named as ZipGenerator.addFolder(folder, prefix, false) names them, then an
   * entry with this name and content
   */
  public void write(String zip, String folder, String prefix, String name, byte[] content) throws IOException {
    List<Source> files = new ArrayList<>();
    list(folder, prefix, files);
    for (Source s : files) {
      if (s.name.equals(name)) {
        throw new ZipException("duplicate entry: " + name); // as ZipOutputStream says
      }
    }
    File zipFile = ManagedFileAccess.file(zip);
    File tempDir = zipFile.getAbsoluteFile().getParentFile();
    AtomicInteger n = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
      Thread t = new Thread(r, "ig-zip-" + n.incrementAndGet());
      t.setDaemon(true);
      return t;
    });
    Deque<Future<Compressed>> pending = new ArrayDeque<>();
    Map<Integer, Future<Compressed>> bigOnes = new HashMap<>();
    Queue<File> temps = new ConcurrentLinkedQueue<>();
    boolean ok = false;
    try {
      // the big files first, biggest first: they take longest
      List<Integer> order = new ArrayList<>();
      for (int i = 0; i < files.size(); i++) {
        if (files.get(i).length > big) {
          order.add(i);
        }
      }
      order.sort((a, b) -> Long.compare(files.get(b).length, files.get(a).length));
      for (int i : order) {
        Source s = files.get(i);
        bigOnes.put(i, pool.submit(() -> compressToFile(s, tempDir, temps)));
      }
      try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(zipFile)) {
        out.setLevel(Deflater.BEST_COMPRESSION);
        writeEntries(out, files, pool, pending, bigOnes);
        addRaw(out, name, compress(new ByteArrayInputStream(content), content.length));
        out.finish();
      }
      ok = true;
    } finally {
      for (Future<Compressed> f : pending) {
        f.cancel(true);
      }
      for (Future<Compressed> f : bigOnes.values()) {
        f.cancel(true);
      }
      pool.shutdownNow();
      awaitWorkers(pool);
      for (File f : temps) {
        delete(f, "temporary file");
      }
      if (!ok) {
        delete(zipFile, "incomplete zip");
      }
    }
  }

  /**
   * adds the files, in order. The workers compress the small files a bounded number of files and bytes ahead, and the
   * big ones (already started) on their own
   */
  private void writeEntries(ZipArchiveOutputStream out, List<Source> files, ExecutorService pool,
      Deque<Future<Compressed>> pending, Map<Integer, Future<Compressed>> bigOnes) throws IOException {
    long inFlight = 0;
    peakInFlight = 0;
    int next = 0;
    int window = threads * 8;
    for (int i = 0; i < files.size(); i++) {
      while (next < files.size() && pending.size() < window) {
        Source s = files.get(next);
        if (s.length <= big) {
          if (!pending.isEmpty() && inFlight + s.length > maxInFlight) {
            break;
          }
          pending.add(pool.submit(() -> compress(s)));
          inFlight += s.length;
          peakInFlight = Math.max(peakInFlight, inFlight);
        }
        next++;
      }
      Source s = files.get(i);
      if (s.length > big) {
        Compressed c = get(bigOnes.remove(i), s);
        addRaw(out, s.name, c);
        delete(c.temp, "temporary file");
      } else {
        Compressed c = get(pending.removeFirst(), s);
        inFlight -= s.length;
        addRaw(out, s.name, c);
      }
    }
  }

  /**
   * The files under dir, in the order ZipGenerator.addFolder() adds them: what File.list() returns, without
   * .DS_Store, folders replaced by their content; links to folders and broken links are skipped, as there
   */
  static void list(String dir, String prefix, List<Source> res) throws IOException {
    File fd = ManagedFileAccess.csfile(dir);
    String[] names = fd.list();
    if (names == null) {
      log.warn("Unable to list the contents of " + fd.getAbsolutePath() + " - not added to zip");
      return;
    }
    for (String f : names) {
      if (".DS_Store".equals(f)) {
        continue;
      }
      String fn = Utilities.path(dir, f);
      File cf = ManagedFileAccess.file(fn);
      if (Files.isSymbolicLink(cf.toPath())) {
        if (!cf.exists()) {
          log.warn("Skipping broken symbolic link " + cf.getAbsolutePath() + " - not added to zip");
          continue;
        } else if (cf.isDirectory()) {
          log.warn("Skipping symbolic link to directory " + cf.getAbsolutePath() + " - not added to zip");
          continue;
        }
      }
      if (cf.isDirectory()) {
        list(fn, Utilities.pathURL(prefix, f), res);
      } else {
        res.add(new Source(Utilities.pathURL(prefix, f), cf));
      }
    }
  }

  /**
   * waits for the workers to stop, so that none of them still has a file open when write() returns. This waits even
   * when the calling thread was interrupted (they have been interrupted too and stop soon), and keeps the interrupt
   */
  private static void awaitWorkers(ExecutorService pool) {
    boolean interrupted = Thread.interrupted();
    long end = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
    try {
      while (true) {
        try {
          if (!pool.awaitTermination(end - System.nanoTime(), TimeUnit.NANOSECONDS)) {
            log.warn("The zip worker threads did not stop in time");
          }
          return;
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static void delete(File f, String what) {
    if (f.exists() && !f.delete()) {
      log.warn("Unable to delete the " + what + " " + f.getAbsolutePath());
    }
  }

  /** opens a file to add (tests replace it) */
  InputStream open(File f) throws IOException {
    return ManagedFileAccess.inStream(f);
  }

  private static void addRaw(ZipArchiveOutputStream out, String name, Compressed c) throws IOException {
    ZipArchiveEntry e = new ZipArchiveEntry(name);
    e.setMethod(ZipEntry.DEFLATED);
    e.setTime(System.currentTimeMillis()); // as ZipOutputStream.putNextEntry() does
    e.setSize(c.size);
    e.setCompressedSize(c.csize);
    e.setCrc(c.crc);
    try (InputStream raw = c.temp != null ? new FileInputStream(c.temp) : new ByteArrayInputStream(c.data, 0, (int) c.csize)) {
      out.addRawArchiveEntry(e, raw);
    }
  }

  private Compressed compress(Source s) throws IOException {
    try (InputStream in = open(s.file)) {
      return compress(in, s.length);
    }
  }

  /** into memory, in a buffer that already has room for the most that deflate can make of size bytes */
  private static Compressed compress(InputStream in, long size) throws IOException {
    Buffer bo = new Buffer((int) Math.min(Integer.MAX_VALUE - 64, size + (size >> 12) + (size >> 14) + 64));
    long[] res = deflate(in, bo);
    return new Compressed(res[0], res[1], bo.size(), bo.array(), null);
  }

  private Compressed compressToFile(Source s, File dir, Queue<File> temps) throws IOException {
    File temp = Files.createTempFile(dir.toPath(), "zip-", ".deflate").toFile();
    temps.add(temp);
    long[] res;
    try (InputStream in = open(s.file); OutputStream out = new BufferedOutputStream(new FileOutputStream(temp), BUFFER)) {
      res = deflate(in, out);
    } catch (IOException | RuntimeException | Error e) {
      // write() deletes it too, but may have stopped waiting for this worker before new FileOutputStream() made it again
      temp.delete();
      throw e;
    }
    return new Compressed(res[0], res[1], temp.length(), null, temp);
  }

  /**
   * compresses as ZipOutputStream does at level 9 (raw deflate; the same input gives the same output)
   * @return the number of bytes read and their CRC
   */
  private static long[] deflate(InputStream in, OutputStream out) throws IOException {
    Deflater def = new Deflater(Deflater.BEST_COMPRESSION, true);
    try {
      CRC32 crc = new CRC32();
      byte[] buf = new byte[BUFFER];
      byte[] cbuf = new byte[BUFFER];
      long read = 0;
      int n;
      while ((n = in.read(buf)) != -1) {
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedIOException("zip cancelled");
        }
        crc.update(buf, 0, n);
        read += n;
        def.setInput(buf, 0, n);
        while (!def.needsInput()) {
          out.write(cbuf, 0, def.deflate(cbuf));
        }
      }
      def.finish();
      while (!def.finished()) {
        out.write(cbuf, 0, def.deflate(cbuf));
      }
      return new long[] {read, crc.getValue()};
    } finally {
      def.end();
    }
  }

  private static Compressed get(Future<Compressed> f, Source s) throws IOException {
    try {
      return f.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InterruptedIOException("Interrupted while writing the zip");
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        // the worker's stack doesn't say where it was
        throw new IOException("Unable to add " + s.file.getAbsolutePath() + " to the zip: " + cause.getMessage(), cause);
      } else if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      } else if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw new IOException(cause);
    }
  }

  /** for tests: the most bytes of files compressed into memory at a time in the last write() */
  long peakInFlight() {
    return peakInFlight;
  }

  /** for tests: files bigger than this go through temporary files */
  long bigFile() {
    return big;
  }
}
