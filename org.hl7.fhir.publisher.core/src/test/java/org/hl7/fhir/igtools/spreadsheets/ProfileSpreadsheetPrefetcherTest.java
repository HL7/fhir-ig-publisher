package org.hl7.fhir.igtools.spreadsheets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class ProfileSpreadsheetPrefetcherTest {

  private static List<String> keys(int n) {
    List<String> res = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      res.add("k" + i);
    }
    return res;
  }

  @Test
  void returnsEachResultOnceInAnyOrder() {
    List<String> keys = keys(20);
    List<String> prepared = Collections.synchronizedList(new ArrayList<>());
    Thread caller = Thread.currentThread();
    try (ProfileSpreadsheetPrefetcher<String> p = new ProfileSpreadsheetPrefetcher<>(keys, 3, k -> {
      assertTrue(Thread.currentThread() == caller, "prepare runs on the caller's thread");
      prepared.add(k);
      return () -> k.getBytes();
    })) {
      assertEquals(6, prepared.size(), "only a window of jobs is prepared up front");
      for (String k : keys) {
        assertArrayEquals(k.getBytes(), p.take(k));
        assertTrue(prepared.size() <= keys.indexOf(k) + 1 + 6);
        assertNull(p.take(k), "a result is handed out once");
      }
      assertEquals(keys, prepared, "prepared in candidate order");
      // out of order: a key further on is prepared on demand
      assertNull(p.take("not-a-candidate"));
      assertEquals(0, p.getFallbacks());
    }
    try (ProfileSpreadsheetPrefetcher<String> p = new ProfileSpreadsheetPrefetcher<>(keys, 2, k -> () -> k.getBytes())) {
      assertArrayEquals("k15".getBytes(), p.take("k15"));
      assertArrayEquals("k3".getBytes(), p.take("k3"));
    }
  }

  @Test
  void failuresAreLeftToTheCaller() {
    List<String> keys = keys(6);
    try (ProfileSpreadsheetPrefetcher<String> p = new ProfileSpreadsheetPrefetcher<>(keys, 2, k -> {
      if (k.equals("k1")) {
        throw new IllegalArgumentException("prepare failed");
      }
      if (k.equals("k2")) {
        return null;
      }
      return () -> {
        if (k.equals("k3")) {
          throw new IllegalStateException("render failed");
        }
        if (k.equals("k4")) {
          throw new OutOfMemoryError("simulated");
        }
        return k.getBytes();
      };
    })) {
      assertArrayEquals("k0".getBytes(), p.take("k0"));
      assertNull(p.take("k1"));
      assertNull(p.take("k2"));
      assertNull(p.take("k3"));
      assertNull(p.take("k4"));
      assertArrayEquals("k5".getBytes(), p.take("k5"));
      assertEquals(2, p.getFallbacks());
    }
  }

  @Test
  void runsJobsConcurrentlyOnDaemonThreads() throws Exception {
    List<String> keys = keys(3);
    CountDownLatch allStarted = new CountDownLatch(3);
    Set<String> threads = ConcurrentHashMap.newKeySet();
    AtomicBoolean daemon = new AtomicBoolean(true);
    try (ProfileSpreadsheetPrefetcher<String> p = new ProfileSpreadsheetPrefetcher<>(keys, 3, k -> () -> {
      threads.add(Thread.currentThread().getName());
      daemon.compareAndSet(true, Thread.currentThread().isDaemon());
      allStarted.countDown();
      // only finishes when all three run at the same time
      assertTrue(allStarted.await(10, TimeUnit.SECONDS));
      return k.getBytes();
    })) {
      for (String k : keys) {
        assertArrayEquals(k.getBytes(), p.take(k));
      }
    }
    assertEquals(3, threads.size());
    assertTrue(daemon.get());
  }

  @Test
  void closeCancelsWhatIsLeft() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch interrupted = new CountDownLatch(1);
    ProfileSpreadsheetPrefetcher<String> p = new ProfileSpreadsheetPrefetcher<>(keys(1), 1, k -> () -> {
      started.countDown();
      try {
        Thread.sleep(60000);
      } catch (InterruptedException e) {
        interrupted.countDown();
      }
      return null;
    });
    assertTrue(started.await(10, TimeUnit.SECONDS));
    p.close();
    assertTrue(interrupted.await(10, TimeUnit.SECONDS), "the running job is interrupted");
  }
}
