package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.utilities.logging.ILoggingService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Watch mode: if the changes can't be checked for a page rebuild, or the page rebuild fails, a complete build follows */
class RebuildPagesFallbackTest {

  @TempDir
  Path dir;

  @Test
  void whenTheChangesCantBeChecked() throws Exception {
    boolean[] discarded = {false};
    PageRebuild state = new PageRebuild() {
      @Override
      public List<FetchedFile> pagesFor(SourceSnapshot.Changes changes, List<String> reason) throws IOException {
        throw new IOException("a path that can't be read");
      }

      @Override
      public void discard() {
        discarded[0] = true;
      }
    };
    Publisher p = new Publisher();
    List<String> log = new ArrayList<>();
    p.setPf(new PublisherFields());
    p.getPf().logger = new ILoggingService() {
      public void logMessage(String message) {
        log.add(message);
      }

      public void logDebugMessage(LogCategory category, String message) {
      }
    };
    Field f = Publisher.class.getDeclaredField("pageRebuild");
    f.setAccessible(true);
    f.set(p, state);

    assertFalse(p.rebuildPages(new WatchSession(dir, Collections.emptyList())));
    assertTrue(discarded[0]);
    assertNull(f.get(p));
    assertEquals(List.of("Rapido Mode: Complete Build (the changes couldn't be checked: java.io.IOException: a path that can't be read)"), log);
  }

  @Test
  void whenThePageRebuildFails() throws Exception {
    boolean[] discarded = {false};
    List<String> log = new ArrayList<>();
    PublisherFields pf = new PublisherFields();
    pf.vsCache = dir.toString();
    pf.logger = new ILoggingService() {
      public void logMessage(String message) {
        log.add(message);
      }

      public void logDebugMessage(LogCategory category, String message) {
      }
    };
    PageRebuild state = new PageRebuild() {
      @Override
      public List<FetchedFile> pagesFor(SourceSnapshot.Changes changes, List<String> reason) {
        return new ArrayList<>();
      }

      @Override
      public PublisherFields getPf() {
        return pf;
      }

      @Override
      public void restore() {
        // as checkMakeFile() does for a file written twice
        throw new Error("Output file x is being generated more than once");
      }

      @Override
      public void discard() {
        discarded[0] = true;
      }
    };
    Publisher p = new Publisher();
    p.setPf(pf);
    Field f = Publisher.class.getDeclaredField("pageRebuild");
    f.setAccessible(true);
    f.set(p, state);

    assertFalse(p.rebuildPages(new WatchSession(dir, Collections.emptyList())));
    assertTrue(discarded[0]);
    assertNull(f.get(p));
    assertEquals("Rapido Mode: the page rebuild failed (java.lang.Error: Output file x is being generated more than once), so doing a complete build",
        log.get(log.size() - 1));
  }
}
