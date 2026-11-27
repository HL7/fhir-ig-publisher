package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class OrderedWorkersTest {

  private static List<Integer> items(int n) {
    return IntStream.range(0, n).boxed().collect(Collectors.toList());
  }

  private static boolean workersAlive(String name) {
    return Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.getName().startsWith(name + "-") && t.isAlive());
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void spin(long ms) {
    long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms);
    while (System.nanoTime() < end) {
      Thread.onSpinWait();
    }
  }

  @Test
  void resultsAreConsumedInListOrderOnTheCallingThread() throws Exception {
    List<Integer> in = items(300);
    Thread caller = Thread.currentThread();
    Set<String> workers = ConcurrentHashMap.newKeySet();
    List<String> consumed = new ArrayList<>();
    OrderedWorkers.run("ow-order", 7, 21, in, i -> {
      workers.add(Thread.currentThread().getName());
      sleep((300 - i) % 4); // earlier items tend to finish later
      return "r" + i;
    }, (i, r) -> {
      assertSame(caller, Thread.currentThread());
      consumed.add(i + "=" + r);
    });
    assertEquals(in.stream().map(i -> i + "=r" + i).collect(Collectors.toList()), consumed);
    assertTrue(workers.size() > 1, "jobs ran on " + workers);
    assertFalse(workers.contains(caller.getName()));
    assertFalse(workersAlive("ow-order"));
  }

  @Test
  void theSameResultsAsALoopWhateverTheThreads() throws Exception {
    List<Integer> in = items(500);
    List<Long> expected = new ArrayList<>();
    for (int i : in) {
      expected.add((long) i * i);
    }
    for (int threads : new int[] {1, 2, 5, 10}) {
      List<Long> got = new ArrayList<>();
      OrderedWorkers.run("ow-same", threads, threads * 3, in, i -> {
        spin(i % 3 == 0 ? 1 : 0);
        return (long) i * i;
      }, (i, r) -> got.add(r));
      assertEquals(expected, got, threads + " threads");
    }
  }

  @Test
  void jobsRunAtMostWindowItemsAheadOfTheConsumer() throws Exception {
    AtomicInteger started = new AtomicInteger();
    AtomicInteger consumed = new AtomicInteger();
    AtomicInteger maxAhead = new AtomicInteger();
    OrderedWorkers.run("ow-window", 4, 6, items(60), i -> {
      started.incrementAndGet();
      return i;
    }, (i, r) -> {
      sleep(2); // a slow consumer: the workers would race ahead without the window
      maxAhead.accumulateAndGet(started.get() - consumed.get(), Math::max);
      consumed.incrementAndGet();
    });
    assertTrue(maxAhead.get() <= 6, "ran " + maxAhead.get() + " ahead");
    assertEquals(60, started.get());
  }

  @Test
  void aFailedJobIsThrownAsItIsAfterTheItemsBeforeIt() {
    IOException failure = new IOException("item 5");
    List<Integer> consumed = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger started = new AtomicInteger();
    IOException thrown = assertThrows(IOException.class, () -> OrderedWorkers.run("ow-fail", 3, 4, items(100), i -> {
      started.incrementAndGet();
      if (i == 5) {
        throw failure;
      }
      return i;
    }, (i, r) -> consumed.add(i)));
    assertSame(failure, thrown);
    assertEquals(items(5), consumed);
    assertTrue(started.get() <= 10, "started " + started.get() + " jobs");
    assertFalse(workersAlive("ow-fail"));
  }

  @Test
  void anErrorIsThrownAsItIs() {
    AssertionError failure = new AssertionError("item 2");
    AssertionError thrown = assertThrows(AssertionError.class, () -> OrderedWorkers.run("ow-error", 2, 2, items(10), i -> {
      if (i == 2) {
        throw failure;
      }
      return i;
    }, (i, r) -> { }));
    assertSame(failure, thrown);
    assertFalse(workersAlive("ow-error"));
  }

  @Test
  void aFailedConsumerStopsTheRun() {
    IllegalStateException failure = new IllegalStateException("consumer at 3");
    List<Integer> consumed = new ArrayList<>();
    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> OrderedWorkers.run("ow-consumer", 3, 3, items(50), i -> i, (i, r) -> {
      consumed.add(i);
      if (i == 3) {
        throw failure;
      }
    }));
    assertSame(failure, thrown);
    assertEquals(items(4), consumed);
    assertFalse(workersAlive("ow-consumer"));
  }

  @Test
  void runningJobsFinishBeforeTheFailureIsThrown() {
    AtomicBoolean slowJobDone = new AtomicBoolean();
    assertThrows(IOException.class, () -> OrderedWorkers.run("ow-wait", 2, 2, items(2), i -> {
      if (i == 0) {
        sleep(50); // let item 1 start
        throw new IOException("item 0");
      }
      spin(300); // not interruptible, like a file write
      slowJobDone.set(true);
      return i;
    }, (i, r) -> { }));
    assertTrue(slowJobDone.get(), "run() threw while a job was still running");
  }

  @Test
  void anInterruptedCallerStillWaitsForTheWorkers() throws Exception {
    AtomicBoolean jobDone = new AtomicBoolean();
    List<Throwable> thrown = new ArrayList<>();
    Thread caller = new Thread(() -> {
      try {
        OrderedWorkers.run("ow-interrupt", 1, 1, items(1), i -> {
          spin(300);
          jobDone.set(true);
          return i;
        }, (i, r) -> { });
      } catch (Throwable e) {
        thrown.add(e);
      }
    });
    caller.start();
    sleep(50);
    caller.interrupt();
    caller.join();
    assertEquals(1, thrown.size());
    assertTrue(thrown.get(0) instanceof InterruptedException, thrown.get(0).toString());
    assertTrue(jobDone.get(), "run() returned while the job was still running");
    assertFalse(workersAlive("ow-interrupt"));
  }

  @Test
  void anEmptyListRunsNothing() throws Exception {
    OrderedWorkers.run("ow-empty", 2, 2, new ArrayList<Integer>(), i -> {
      throw new AssertionError();
    }, (i, r) -> {
      throw new AssertionError();
    });
    assertFalse(workersAlive("ow-empty"));
  }

  @Test
  void rejectsNoThreadsOrASmallWindow() {
    assertThrows(IllegalArgumentException.class, () -> OrderedWorkers.run("ow-args", 0, 4, items(1), i -> i, (i, r) -> { }));
    assertThrows(IllegalArgumentException.class, () -> OrderedWorkers.run("ow-args", 4, 3, items(1), i -> i, (i, r) -> { }));
  }
}
