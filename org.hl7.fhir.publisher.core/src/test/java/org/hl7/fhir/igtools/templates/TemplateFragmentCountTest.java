package org.hl7.fhir.igtools.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** With -threads, the native outputs ask the template from several threads at once: the Rapido summary still counts every ask */
class TemplateFragmentCountTest {

  @TempDir
  Path dir;

  @Test
  void theRapidoSummaryCountsAsksFromSeveralThreads() throws Exception {
    Files.createDirectories(dir.resolve("template"));
    Files.write(dir.resolve("template/config.json"), "{}".getBytes(StandardCharsets.UTF_8));
    Template t = new Template(dir.toString(), true, null, null, false, true, false);

    int threads = 8;
    int asks = 20000;
    List<Thread> workers = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      Thread w = new Thread(() -> {
        for (int j = 0; j < asks; j++) {
          t.wantGenerateFragment("Basic", j % 2 == 0 ? "json" : "not-a-fragment");
        }
      });
      workers.add(w);
    }
    workers.forEach(Thread::start);
    for (Thread w : workers) {
      w.join();
    }

    PrintStream out = System.out;
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    System.setOut(new PrintStream(b, true, StandardCharsets.UTF_8));
    try {
      t.rapidoSummary();
    } finally {
      System.setOut(out);
    }
    assertEquals("Rapido Template Summary: don't produce " + (threads * asks / 2) + " of " + (threads * asks) + " fragments",
        b.toString(StandardCharsets.UTF_8).trim());
  }
}
