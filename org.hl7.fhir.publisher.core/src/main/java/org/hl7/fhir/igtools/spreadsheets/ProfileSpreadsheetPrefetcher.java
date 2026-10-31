package org.hl7.fhir.igtools.spreadsheets;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.hl7.fhir.model.core.StructureDefinition;

/**
 * Builds the per-profile xlsx workbooks of the Spreadsheets phase on worker threads, a few profiles ahead of
 * the publisher's own (single-threaded, in-order) loop, which then just writes the bytes.
 * <p>
 * Each task gets everything it reads up front, on the publisher's thread: a deep copy of the profile and a
 * generator whose context-backed displays are already rendered (see
 * {@link ProfileSpreadsheetGenerator#precomputeDisplays}). So a task touches no object that the publisher's
 * thread, or another task, uses at the same time. The only state tasks share is the {@link ColumnWidthCache},
 * which is thread-safe, and POI/java2d internals, which are safe for separate workbooks.
 * <p>
 * {@link #take} returns null when there is no result, for whatever reason (not a candidate, the task failed,
 * interrupted). The caller then generates the workbook itself the usual way, so failures behave and are
 * reported exactly as without the prefetcher.
 */
public class ProfileSpreadsheetPrefetcher<K> implements AutoCloseable {

  /** A workbook that is ready to be rendered off-thread. */
  public interface Job {
    /** renders the workbook and returns the xlsx file content */
    byte[] render() throws Exception;
  }

  /** Called on the publisher's thread: everything that has to happen there before the job goes to a worker. */
  public interface JobFactory<K> {
    Job prepare(K key) throws Exception;
  }

  private final List<K> candidates;
  private final Map<K, Integer> positions = new HashMap<>();
  private final Map<K, Future<byte[]>> tasks = new HashMap<>();
  private final JobFactory<K> factory;
  private final ExecutorService executor;
  private final int window;
  private int submitted;
  private int fallbacks;

  /**
   * @param candidates the profiles in the order the publisher's loop will ask for them
   * @param threads the number of worker threads, at least 1
   */
  public ProfileSpreadsheetPrefetcher(List<K> candidates, int threads, JobFactory<K> factory) {
    this.candidates = candidates;
    for (int i = 0; i < candidates.size(); i++) {
      positions.putIfAbsent(candidates.get(i), i);
    }
    this.factory = factory;
    this.window = 2 * threads;
    AtomicInteger n = new AtomicInteger();
    this.executor = Executors.newFixedThreadPool(threads, r -> {
      Thread t = new Thread(r, "ig-spreadsheets-" + n.incrementAndGet());
      t.setDaemon(true);
      return t;
    });
    submitUpTo(window);
  }

  private void submitUpTo(int end) {
    while (submitted < Math.min(end, candidates.size())) {
      K key = candidates.get(submitted++);
      Job job;
      try {
        job = factory.prepare(key);
      } catch (Exception e) {
        fallbacks++; // the caller's own path will meet the same problem and report it
        continue;
      }
      if (job != null) {
        tasks.put(key, executor.submit((Callable<byte[]>) job::render));
      }
    }
  }

  /**
   * @return the xlsx content for key, or null if the caller has to generate it itself
   */
  public byte[] take(K key) {
    Integer pos = positions.get(key);
    if (pos == null) {
      return null;
    }
    submitUpTo(pos + 1 + window);
    Future<byte[]> task = tasks.remove(key);
    if (task == null) {
      return null;
    }
    try {
      return task.get();
    } catch (ExecutionException e) {
      fallbacks++;
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      task.cancel(true);
      return null;
    }
  }

  /** the number of profiles that failed to prepare or render, and so were left to the caller */
  public int getFallbacks() {
    return fallbacks;
  }

  @Override
  public void close() {
    for (Future<byte[]> task : tasks.values()) {
      task.cancel(true);
    }
    tasks.clear();
    executor.shutdownNow();
  }

  /** the number of worker threads to use, or 0 for none: one per core beyond the publisher's own, at most 3 */
  public static int defaultThreads() {
    String s = System.getProperty("ig.spreadsheet.threads");
    if (s != null) {
      try {
        return Math.max(0, Integer.parseInt(s.trim()));
      } catch (NumberFormatException e) {
        // fall through to the default
      }
    }
    return Math.max(0, Math.min(3, Runtime.getRuntime().availableProcessors() - 1));
  }

  /** a {@link Job} for one profile workbook */
  public static Job job(ProfileSpreadsheetGenerator generator, StructureDefinition sd) {
    return () -> {
      generator.renderStructureDefinition(sd, false);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      generator.finish(bytes);
      return bytes.toByteArray();
    };
  }
}
