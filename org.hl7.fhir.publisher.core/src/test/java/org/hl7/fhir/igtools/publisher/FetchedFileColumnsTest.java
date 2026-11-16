package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

import org.junit.jupiter.api.Test;

/** With -threads, files are started on several threads at once: each activity is still one column of the processing report */
class FetchedFileColumnsTest {

  @Test
  void eachActivityIsOneColumnWhenStartedFromSeveralThreads() throws Exception {
    int threads = 8;
    int activities = 200;
    CyclicBarrier start = new CyclicBarrier(threads);
    List<Thread> workers = new ArrayList<>();
    List<Throwable> failures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      Thread w = new Thread(() -> {
        try {
          start.await();
          FetchedFile f = new FetchedFile("columns-test.json");
          for (int a = 0; a < activities; a++) {
            f.start("columns-test-" + a);
            f.finish("columns-test-" + a);
          }
        } catch (Throwable e) {
          synchronized (failures) {
            failures.add(e);
          }
        }
      });
      workers.add(w);
      w.start();
    }
    for (Thread w : workers) {
      w.join();
    }
    assertEquals(List.of(), failures);
    long columns = FetchedFile.getColumns().stream().filter(c -> c.startsWith("columns-test-")).count();
    assertEquals(activities, columns);
  }
}
