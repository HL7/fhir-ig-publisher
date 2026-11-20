package org.hl7.fhir.igtools.publisher;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs a job for each item of a list on worker threads, and hands each result to a consumer on the calling thread,
 * in list order. So whatever the consumer does happens in the same order as in a plain loop over the list, and the
 * jobs only have to be safe to run at the same time as each other.
 * <p>
 * Jobs run at most {@code window} items ahead of the consumer, which bounds the results held in memory.
 * <p>
 * If a job or the consumer fails, the results of the items before it have been consumed and no later one is. The
 * jobs not yet started are cancelled, the workers are waited for, and the failure is thrown as it is (an unchecked
 * exception or error, or the job's checked exception).
 */
public class OrderedWorkers {

  /** Runs on a worker thread. */
  public interface Job<T, R> {
    R run(T item) throws Exception;
  }

  /** Runs on the calling thread, in list order. */
  public interface Consumer<T, R> {
    void accept(T item, R result) throws Exception;
  }

  private OrderedWorkers() {
  }

  /**
   * @param name prefix of the worker thread names
   * @param threads the number of worker threads, at least 1
   * @param window how far, in items, the jobs may run ahead of the consumer, at least threads
   */
  public static <T, R> void run(String name, int threads, int window, List<T> items, Job<T, R> job, Consumer<T, R> consumer) throws Exception {
    if (threads < 1 || window < threads) {
      throw new IllegalArgumentException("threads = " + threads + ", window = " + window);
    }
    AtomicInteger n = new AtomicInteger();
    ExecutorService executor = Executors.newFixedThreadPool(threads, r -> {
      Thread t = new Thread(r, name + "-" + n.incrementAndGet());
      t.setDaemon(true);
      return t;
    });
    ArrayDeque<Future<R>> pending = new ArrayDeque<>();
    try {
      int submitted = 0;
      for (T item : items) {
        while (submitted < items.size() && pending.size() < window) {
          T next = items.get(submitted++);
          pending.add(executor.submit(() -> job.run(next)));
        }
        R result;
        try {
          result = pending.remove().get();
        } catch (ExecutionException e) {
          throw rethrow(e.getCause());
        }
        consumer.accept(item, result);
      }
    } finally {
      for (Future<R> f : pending) {
        f.cancel(true);
      }
      executor.shutdownNow();
      awaitTermination(name, executor);
    }
  }

  private static Exception rethrow(Throwable cause) {
    if (cause instanceof Error) {
      throw (Error) cause;
    }
    if (cause instanceof Exception) {
      return (Exception) cause;
    }
    return new Exception(cause);
  }

  /** no worker is left writing once run() returns or throws, even if the calling thread is interrupted */
  private static void awaitTermination(String name, ExecutorService executor) {
    boolean interrupted = false;
    boolean said = false;
    while (true) {
      try {
        if (executor.awaitTermination(1, TimeUnit.MINUTES)) {
          break;
        }
        if (!said) {
          System.out.println("Still waiting for the " + name + " workers to stop");
          said = true;
        }
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
