package org.hl7.fhir.igtools.publisher;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;

/**
 * Parses a list of files on background threads, a few files ahead of the thread that walks the
 * list, so that its XhtmlParser calls become lookups.
 * <p>
 * Parsing is a pure function of the bytes parsed and the parser settings, so a tree parsed early is
 * the tree a parse on the walking thread would give - provided the file still has the same bytes.
 * {@link #parse} therefore always reads the file itself and only hands out a prefetched tree whose
 * source bytes are equal to what it read; otherwise (file changed, prefetch failed or threw, or not
 * started yet) it parses on the calling thread, exactly as before. Each prefetched tree is handed
 * out at most once, so callers own the trees they get, as they do with a fresh parse.
 * <p>
 * The walking thread must call {@link #take} with increasing indexes. Workers stay at most
 * {@code window} files ahead of it, and wait while the files they are ahead by add up to
 * {@link #MAX_AHEAD_BYTES}, which bounds the memory the parsed trees hold.
 */
public class XhtmlParsePrefetcher implements AutoCloseable {

  /** One parse of one byte sequence. */
  public static class Parsed {
    private final byte[] source;
    private final XhtmlNode node;
    private final List<String> recoveryNotes;

    Parsed(byte[] source, XhtmlNode node, List<String> recoveryNotes) {
      this.source = source;
      this.node = node;
      this.recoveryNotes = recoveryNotes;
    }
  }

  /** What a worker does for one file: the parses the walking thread is expected to ask for, in order. */
  public interface Job {
    List<Parsed> run(String path) throws Exception;
  }

  /** Parses of one file that are there for the taking, each handed out at most once. */
  public static class Parsings {
    private final List<Parsed> parsed = new ArrayList<>();

    public Parsings() {
    }

    Parsings(List<Parsed> parsed) {
      this.parsed.addAll(parsed);
    }

    /**
     * Makes a parse available to a later {@link XhtmlParsePrefetcher#parse}. Only for a parse whose
     * tree nothing has changed since, and that nothing else will use.
     */
    public void add(Parsed p) {
      parsed.add(p);
    }

    /** @return a parse of exactly these bytes, removed so it isn't handed out again, or null */
    Parsed claim(byte[] bytes) {
      for (Iterator<Parsed> it = parsed.iterator(); it.hasNext(); ) {
        Parsed p = it.next();
        if (Arrays.equals(p.source, bytes)) {
          it.remove();
          return p;
        }
      }
      return null;
    }
  }

  /** How many files each worker may be ahead of the walking thread. */
  static final int WINDOW_PER_THREAD = 8;

  /** Bigger files are left to the walking thread. */
  static final long MAX_PREFETCH_BYTES = 8L * 1024 * 1024;

  /** Workers don't start another file while the files they have started and not handed out are this big. */
  static final long MAX_AHEAD_BYTES = 32L * 1024 * 1024;

  private static final int NEW = 0;
  private static final int RUNNING = 1;
  private static final int DONE = 2;
  private static final int TAKEN = 3;

  private final List<String> paths;
  private final Job job;
  private final int window;
  private final long maxAheadBytes;
  private final Object lock = new Object();
  private final int[] state;
  private final List<?>[] results;
  private final long[] sizes;
  private long aheadBytes;
  private int next;
  private int consumed;
  private boolean closed;

  /**
   * @return a prefetcher, or null when there is no spare processor to run it on (callers then just
   *         parse, see {@link #parse})
   */
  public static XhtmlParsePrefetcher start(List<String> paths, Job job) {
    int threads = Math.min(3, Runtime.getRuntime().availableProcessors() - 1);
    if (threads < 1 || paths.size() < 2) {
      return null;
    }
    return new XhtmlParsePrefetcher(paths, job, threads, WINDOW_PER_THREAD * threads);
  }

  XhtmlParsePrefetcher(List<String> paths, Job job, int threads, int window) {
    this(paths, job, threads, window, MAX_AHEAD_BYTES);
  }

  XhtmlParsePrefetcher(List<String> paths, Job job, int threads, int window, long maxAheadBytes) {
    this.maxAheadBytes = maxAheadBytes;
    this.paths = new ArrayList<>(paths);
    this.job = job;
    this.window = Math.max(1, window);
    this.state = new int[paths.size()];
    this.results = new List<?>[paths.size()];
    this.sizes = new long[paths.size()];
    for (int i = 0; i < threads; i++) {
      Thread t = new Thread(this::work, "xhtml-prefetch-" + i);
      t.setDaemon(true);
      t.start();
    }
  }

  private void work() {
    while (true) {
      int i;
      synchronized (lock) {
        while (!closed && next < paths.size() && (next >= consumed + window || aheadBytes >= maxAheadBytes)) {
          try {
            lock.wait();
          } catch (InterruptedException e) {
            return;
          }
        }
        if (closed || next >= paths.size()) {
          return;
        }
        i = next++;
        if (state[i] != NEW) {
          continue;
        }
        sizes[i] = new File(paths.get(i)).length();
        if (sizes[i] > MAX_PREFETCH_BYTES) {
          continue;
        }
        state[i] = RUNNING;
        aheadBytes += sizes[i];
      }
      List<Parsed> r;
      try {
        r = job.run(paths.get(i));
      } catch (Throwable e) {
        // the walking thread parses this file itself and gets whatever the failure was
        r = null;
      }
      synchronized (lock) {
        if (state[i] == RUNNING) {
          results[i] = r;
          state[i] = DONE;
        }
        lock.notifyAll();
      }
    }
  }

  /**
   * Called by the walking thread when it gets to file {@code index}: waits for the file's prefetch
   * if it is under way, and stops it being started if it isn't.
   *
   * @return the prefetched parses (possibly none), or null if the file wasn't prefetched
   */
  @SuppressWarnings("unchecked")
  public Parsings take(int index) {
    synchronized (lock) {
      for (int i = consumed; i < index && i < state.length; i++) {
        release(i);
      }
      if (index < 0 || index >= state.length) {
        return null;
      }
      boolean interrupted = false;
      while (state[index] == RUNNING && !closed) {
        try {
          lock.wait();
        } catch (InterruptedException e) {
          interrupted = true;
          break;
        }
      }
      List<Parsed> r = state[index] == DONE ? (List<Parsed>) results[index] : null;
      release(index);
      consumed = Math.max(consumed, index + 1);
      lock.notifyAll();
      if (interrupted) {
        Thread.currentThread().interrupt();
        return null;
      }
      return r == null ? null : new Parsings(r);
    }
  }

  /** Hands file i over to the walking thread: no worker will start it, or use its result if one is under way. */
  private void release(int i) {
    if (state[i] != NEW) {
      aheadBytes -= sizes[i];
    }
    sizes[i] = 0;
    state[i] = TAKEN;
    results[i] = null;
  }

  @Override
  public void close() {
    synchronized (lock) {
      closed = true;
      Arrays.fill(results, null);
      lock.notifyAll();
    }
  }

  // ---- parsing -------------------------------------------------------------------------------

  /** Reads a file the way XhtmlParser.parse(File, ...) does. */
  public static byte[] read(File f) throws IOException {
    try (InputStream input = new FileInputStream(f)) {
      return input.readAllBytes();
    }
  }

  /**
   * The same as {@code new XhtmlParser().setMustBeWellFormed(mustBeWellFormed).parse(bytes, null)},
   * for use in a {@link Job}.
   */
  public static Parsed parseBytes(byte[] bytes, boolean mustBeWellFormed) throws IOException, FHIRFormatError {
    XhtmlParser parser = new XhtmlParser().setMustBeWellFormed(mustBeWellFormed);
    XhtmlNode x = parser.parse(new ByteArrayInputStream(bytes), null);
    return new Parsed(bytes, x, new ArrayList<>(parser.getRecoveryNotes()));
  }

  /**
   * The same as {@code new XhtmlParser().setMustBeWellFormed(mustBeWellFormed).parse(f, null)},
   * taking the tree from {@code available} if it holds a parse of the file's current bytes.
   *
   * @param available may be null
   */
  public static Parsed parseFile(File f, boolean mustBeWellFormed, Parsings available) throws IOException, FHIRFormatError {
    byte[] bytes = read(f);
    Parsed p = available == null ? null : available.claim(bytes);
    return p != null ? p : parseBytes(bytes, mustBeWellFormed);
  }

  /**
   * {@link #parseFile}, for callers that only want the tree.
   *
   * @param recoveryNotes if not null, gets what XhtmlParser.getRecoveryNotes() would have returned
   */
  public static XhtmlNode parse(File f, boolean mustBeWellFormed, Parsings available, List<String> recoveryNotes) throws IOException, FHIRFormatError {
    Parsed p = parseFile(f, mustBeWellFormed, available);
    if (recoveryNotes != null) {
      recoveryNotes.addAll(p.recoveryNotes);
    }
    return p.node;
  }

  /** @return the parses in a job's result list */
  public static List<Parsed> list(Parsed... parsed) {
    List<Parsed> res = new ArrayList<>();
    for (Parsed p : parsed) {
      if (p != null) {
        res.add(p);
      }
    }
    return Collections.unmodifiableList(res);
  }

  /** @return the parse's tree, for a job that needs to look at it to decide what comes next */
  public static XhtmlNode node(Parsed p) {
    return p.node;
  }
}
