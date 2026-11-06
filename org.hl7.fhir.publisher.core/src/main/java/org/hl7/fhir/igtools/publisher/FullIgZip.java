package org.hl7.fhir.igtools.publisher;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipException;

import org.hl7.fhir.utilities.Utilities;

/**
 * Watch mode: writes full-ig.zip as PublisherGenerator does with ZipGenerator (addFolder(output, "site/"), then the
 * redirect page), in the format java.util.zip.ZipOutputStream writes at level 9, byte for byte the same apart from
 * the entry times, but faster: the files are compressed in parallel, and the compressed data of a file that hasn't
 * changed since the last zip is copied from the last zip instead of compressed again (compressing the same bytes the
 * same way gives the same data).
 *
 * <p>It only writes zips that don't need the zip64 extensions (fewer than 65535 entries, everything under 4GB); for
 * the others it says so (write() returns false) and the caller writes the zip the usual way.
 */
public class FullIgZip {

  private static final int LOCSIG = 0x04034b50;
  private static final int EXTSIG = 0x08074b50;
  private static final int CENSIG = 0x02014b50;
  private static final int ENDSIG = 0x06054b50;
  private static final int FLAGS = 0x0808; // sizes in a data descriptor, UTF-8 names
  private static final int VERSION = 20;
  private static final int DEFLATED = 8;
  private static final long LIMIT = 0xFFFFFFFFL;
  private static final int LOCHDR = 30;
  // a file whose inode change time is this close to now may change again without a change of that time
  private static final long RACY_MILLIS = 2000;
  // the most bytes of files being read and compressed, or compressed and waiting to be written, at a time; a bigger
  // file goes on its own. A file takes about three times its size while it is compressed
  private static final long MAX_IN_FLIGHT = 64L << 20;

  /** What the zip holds for one file, and where its compressed data is in the zip */
  private static final class Entry {
    final String name;
    final long size;
    final long crc;
    final long csize;
    final byte[] md5;
    // the size, inode and inode change time of the file when it was zipped (null: not known)
    String stamp;
    long dataOffset;
    // compressed data not yet written (only while the zip is being written)
    byte[] data;

    Entry(String name, long size, long crc, long csize, byte[] md5) {
      this.name = name;
      this.size = size;
      this.crc = crc;
      this.csize = csize;
      this.md5 = md5;
    }
  }

  private final LongSupplier clock;
  private final int threads;
  private final long maxInFlight;
  private long peakInFlight;
  private Map<String, Entry> last = new HashMap<>();
  private Path lastZip;
  private int reused;

  public FullIgZip() {
    this(System::currentTimeMillis, Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())));
  }

  FullIgZip(LongSupplier clock, int threads) {
    this(clock, threads, MAX_IN_FLIGHT);
  }

  FullIgZip(LongSupplier clock, int threads, long maxInFlight) {
    this.clock = clock;
    this.threads = threads;
    this.maxInFlight = maxInFlight;
  }

  /** How many files the last write() took from the zip before */
  public int getReused() {
    return reused;
  }

  /**
   * Writes the zip: the files in folder (under prefix, as ZipGenerator.addFolder() names them) and then an entry with
   * this name and content. Returns false, having written nothing useful, if the zip would need zip64 extensions
   */
  public boolean write(String zip, String folder, String prefix, String name, byte[] content) throws IOException {
    List<String[]> files = new ArrayList<>();
    list(folder, prefix, files);
    if (files.size() + 1 >= 0xFFFF) {
      forget();
      return false;
    }
    Map<String, Entry> now = new HashMap<>();
    reused = 0;
    Path out = new File(zip).toPath();
    ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
      Thread t = new Thread(r, "full-ig-zip");
      t.setDaemon(true);
      return t;
    });
    boolean ok;
    try (FileChannel previous = lastZip != null && Files.exists(lastZip) ? FileChannel.open(lastZip, StandardOpenOption.READ) : null;
         FileChannel channel = FileChannel.open(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
      Writer w = new Writer(channel, previous);
      Deque<Future<Entry>> pending = new ArrayDeque<>();
      Deque<Long> pendingSizes = new ArrayDeque<>();
      long inFlight = 0;
      peakInFlight = 0;
      int next = 0;
      int window = threads * 8;
      while (next < files.size() || !pending.isEmpty()) {
        while (next < files.size() && pending.size() < window) {
          String[] f = files.get(next);
          File file = new File(f[1]);
          long size = file.length();
          if (!pending.isEmpty() && inFlight + size > maxInFlight) {
            break;
          }
          next++;
          Entry old = previous == null ? null : last.get(f[0]);
          pending.add(pool.submit(() -> prepare(f[0], file, old)));
          pendingSizes.add(size);
          inFlight += size;
          peakInFlight = Math.max(peakInFlight, inFlight);
        }
        Entry e = get(pending.removeFirst());
        inFlight -= pendingSizes.removeFirst();
        if (now.containsKey(e.name)) {
          throw new ZipException("duplicate entry: " + e.name);
        }
        w.add(e);
        now.put(e.name, e);
      }
      if (now.containsKey(name)) {
        throw new ZipException("duplicate entry: " + name);
      }
      w.add(compress(name, content));
      ok = w.finish();
    } catch (IOException | RuntimeException e) {
      forget();
      throw e;
    } finally {
      pool.shutdownNow();
    }
    if (!ok) {
      forget();
      return false;
    }
    keep(out, now);
    return true;
  }

  /** Forgets the last zip (so the next one is compressed from scratch) */
  public void forget() {
    last = new HashMap<>();
    if (lastZip != null) {
      try {
        Files.deleteIfExists(lastZip);
      } catch (IOException e) {
        lastZip.toFile().deleteOnExit();
      }
      lastZip = null;
    }
  }

  private void keep(Path zip, Map<String, Entry> entries) throws IOException {
    if (lastZip == null) {
      lastZip = Files.createTempFile("ig-full-zip-", ".zip");
      lastZip.toFile().deleteOnExit();
    }
    Files.copy(zip, lastZip, StandardCopyOption.REPLACE_EXISTING);
    last = entries;
  }

  private static Entry get(Future<Entry> f) throws IOException {
    try {
      return f.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while writing the zip", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new IOException(e.getCause());
    }
  }

  /** The files under dir, in the order ZipGenerator.addFolder() adds them, as {entry name, path} */
  static void list(String dir, String prefix, List<String[]> res) throws IOException {
    String[] names = new File(dir).list();
    if (names == null) {
      return;
    }
    for (String n : names) {
      if (".DS_Store".equals(n)) {
        continue;
      }
      String path = Utilities.path(dir, n);
      File f = new File(path);
      if (Files.isSymbolicLink(f.toPath()) && (!f.exists() || f.isDirectory())) {
        continue;
      }
      if (f.isDirectory()) {
        list(path, Utilities.pathURL(prefix, n), res);
      } else {
        res.add(new String[] {Utilities.pathURL(prefix, n), path});
      }
    }
  }

  /**
   * The entry for a file: the one in the last zip if the file hasn't changed since, or one compressed from it. A file
   * hasn't changed if its content is the same; or, without reading it, if its inode change time (which anything that
   * writes the file, or sets its times, changes) is the same, and was well before the last zip was made
   */
  private Entry prepare(String name, File f, Entry old) throws IOException {
    String stamp = stamp(f.toPath());
    if (old != null && stamp != null && stamp.equals(old.stamp)) {
      return reuse(old, stamp);
    }
    byte[] bytes = Files.readAllBytes(f.toPath());
    byte[] md5 = md5(bytes);
    if (old != null && old.size == bytes.length && Arrays.equals(old.md5, md5)) {
      return reuse(old, stamp);
    }
    Entry e = compress(name, bytes, md5);
    e.stamp = stamp;
    return e;
  }

  /** The file's size, inode and inode change time, if they're known, and the change time is safely in the past */
  private String stamp(Path p) {
    try {
      Map<String, Object> a = Files.readAttributes(p, "unix:size,ino,ctime");
      FileTime ctime = (FileTime) a.get("ctime");
      if (ctime == null || a.get("ino") == null || ctime.toMillis() >= clock.getAsLong() - RACY_MILLIS) {
        return null;
      }
      return a.get("size") + ":" + a.get("ino") + ":" + ctime.to(TimeUnit.NANOSECONDS);
    } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
      return null;
    }
  }

  private Entry reuse(Entry old, String stamp) {
    Entry e = new Entry(old.name, old.size, old.crc, old.csize, old.md5);
    e.dataOffset = old.dataOffset;
    e.stamp = stamp;
    return e;
  }

  private static Entry compress(String name, byte[] bytes) {
    return compress(name, bytes, md5(bytes));
  }

  private static Entry compress(String name, byte[] bytes, byte[] md5) {
    CRC32 crc = new CRC32();
    crc.update(bytes);
    Deflater def = new Deflater(9, true);
    try {
      def.setInput(bytes);
      def.finish();
      ByteArrayOutputStream bo = new ByteArrayOutputStream(Math.max(64, bytes.length / 4));
      byte[] buf = new byte[65536];
      while (!def.finished()) {
        int n = def.deflate(buf);
        bo.write(buf, 0, n);
      }
      byte[] data = bo.toByteArray();
      Entry e = new Entry(name, bytes.length, crc.getValue(), data.length, md5);
      e.data = data;
      e.dataOffset = -1;
      return e;
    } finally {
      def.end();
    }
  }

  private static byte[] md5(byte[] bytes) {
    try {
      return MessageDigest.getInstance("MD5").digest(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Writes the entries as ZipOutputStream does */
  private final class Writer {
    private final FileChannel channel;
    private final OutputStream os;
    private final FileChannel previous;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Integer> times = new ArrayList<>();
    private final List<Long> offsets = new ArrayList<>();
    private long written;
    private boolean tooBig;

    Writer(FileChannel channel, FileChannel previous) {
      this.channel = channel;
      this.os = new BufferedOutputStream(Channels.newOutputStream(channel), 65536);
      this.previous = previous;
    }

    void add(Entry e) throws IOException {
      if (e.size >= LIMIT || e.csize >= LIMIT || written >= LIMIT) {
        tooBig = true;
        return;
      }
      int time = dosTime(clock.getAsLong());
      byte[] name = e.name.getBytes(StandardCharsets.UTF_8);
      long offset = written;
      writeInt(LOCSIG);
      writeShort(VERSION);
      writeShort(FLAGS);
      writeShort(DEFLATED);
      writeInt(time);
      writeInt(0);
      writeInt(0);
      writeInt(0);
      writeShort(name.length);
      writeShort(0);
      writeBytes(name, name.length);
      long dataOffset = written;
      if (e.data != null) {
        writeBytes(e.data, e.data.length);
        e.data = null;
      } else {
        os.flush();
        long pos = e.dataOffset;
        long end = pos + e.csize;
        while (pos < end) {
          pos += previous.transferTo(pos, end - pos, channel);
        }
        written += e.csize;
        reused++;
      }
      e.dataOffset = dataOffset;
      writeInt(EXTSIG);
      writeInt((int) e.crc);
      writeInt((int) e.csize);
      writeInt((int) e.size);
      entries.add(e);
      times.add(time);
      offsets.add(offset);
    }

    boolean finish() throws IOException {
      if (tooBig) {
        os.flush();
        return false;
      }
      long cen = written;
      for (int i = 0; i < entries.size(); i++) {
        Entry e = entries.get(i);
        byte[] name = e.name.getBytes(StandardCharsets.UTF_8);
        writeInt(CENSIG);
        writeShort(VERSION);
        writeShort(VERSION);
        writeShort(FLAGS);
        writeShort(DEFLATED);
        writeInt(times.get(i));
        writeInt((int) e.crc);
        writeInt((int) e.csize);
        writeInt((int) e.size);
        writeShort(name.length);
        writeShort(0);
        writeShort(0);
        writeShort(0);
        writeShort(0);
        writeInt(0);
        writeInt((int) (long) offsets.get(i));
        writeBytes(name, name.length);
      }
      long cenSize = written - cen;
      if (written >= LIMIT) {
        os.flush();
        return false;
      }
      writeInt(ENDSIG);
      writeShort(0);
      writeShort(0);
      writeShort(entries.size());
      writeShort(entries.size());
      writeInt((int) cenSize);
      writeInt((int) cen);
      writeShort(0);
      os.flush();
      return true;
    }

    private void writeShort(int v) throws IOException {
      os.write(v & 0xff);
      os.write((v >>> 8) & 0xff);
      written += 2;
    }

    private void writeInt(long v) throws IOException {
      os.write((int) (v & 0xff));
      os.write((int) ((v >>> 8) & 0xff));
      os.write((int) ((v >>> 16) & 0xff));
      os.write((int) ((v >>> 24) & 0xff));
      written += 4;
    }

    private void writeBytes(byte[] b, int len) throws IOException {
      os.write(b, 0, len);
      written += len;
    }
  }

  /** The MS-DOS date and time for this time, as ZipEntry.setTime() works it out (for times after 1980) */
  static int dosTime(long millis) {
    LocalDateTime d = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    int year = d.getYear() - 1980;
    if (year < 0) {
      return (1 << 21) | (1 << 16);
    }
    return (year << 25 | d.getMonthValue() << 21 | d.getDayOfMonth() << 16 | d.getHour() << 11 | d.getMinute() << 5
        | d.getSecond() >> 1);
  }

  /** for tests: the most bytes of files in flight at a time in the last write() */
  long peakInFlight() {
    return peakInFlight;
  }

  /** for tests: the names of the entries of the last zip written */
  Set<String> lastNames() {
    return new HashSet<>(last.keySet());
  }
}
