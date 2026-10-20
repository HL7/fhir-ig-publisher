package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.hl7.fhir.utilities.FileUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Watch mode stops and starts the monitor around builds, and must see every change after that (it used to see only
 * the first one on Linux, because stopping closed the WatchService).
 */
class FileChangeMonitorTest {

  @TempDir
  Path dir;

  /** waits until path is reported (other paths may be reported too) */
  private static boolean sees(FileChangeMonitor monitor, Path path) throws InterruptedException {
    long end = System.currentTimeMillis() + 15000;
    while (System.currentTimeMillis() < end) {
      FileChangeMonitor.FileChangeEvent e = monitor.waitForChange(200, TimeUnit.MILLISECONDS);
      if (e != null && e.getPath().equals(path.toAbsolutePath())) {
        return true;
      }
    }
    return false;
  }

  /** nothing is reported for a while */
  private static boolean quiet(FileChangeMonitor monitor) throws InterruptedException {
    return monitor.waitForChange(2500, TimeUnit.MILLISECONDS) == null;
  }

  private static void write(Path p, String s) throws IOException {
    Files.createDirectories(p.getParent());
    Files.write(p, s.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void seesChangesAfterEachRestart() throws Exception {
    Path input = dir.resolve("input");
    Path page = input.resolve("pagecontent").resolve("index.md");
    write(page, "start");
    try (FileChangeMonitor monitor = new FileChangeMonitor(Arrays.asList(FileChangeMonitor.MonitoredPath.recursive(input)))) {
      for (int i = 0; i < 3; i++) {
        monitor.startMonitoring();
        Thread.sleep(1100); // the polling monitor (macOS) compares modification times
        write(page, "edit " + i);
        assertTrue(sees(monitor, page), "change " + i + " was not seen");
        monitor.clearChanges();
        monitor.stopMonitoring();
      }
    }
  }

  @Test
  @DisabledOnOs(OS.MAC) // the polling monitor used on macOS finds new folders its own way
  void seesFilesInNewFolders() throws Exception {
    Path input = dir.resolve("input");
    Files.createDirectories(input);
    try (FileChangeMonitor monitor = new FileChangeMonitor(Arrays.asList(FileChangeMonitor.MonitoredPath.recursive(input)))) {
      monitor.startMonitoring();
      Path folder = input.resolve("images");
      Files.createDirectories(folder);
      Path first = folder.resolve("a.png");
      write(first, "a");
      // a.png may be written before the folder is watched: it's reported either way
      assertTrue(sees(monitor, first));
      Path second = folder.resolve("b.png");
      write(second, "b");
      assertTrue(sees(monitor, second));
    }
  }

  @Test
  @DisabledOnOs(OS.MAC)
  void watchesSingleFilesOnly() throws Exception {
    Path ini = dir.resolve("ig.ini");
    Path other = dir.resolve("other.txt");
    Path later = dir.resolve("publication-request.json"); // doesn't exist yet
    write(ini, "[IG]");
    try (FileChangeMonitor monitor = new FileChangeMonitor(Arrays.asList(
        FileChangeMonitor.MonitoredPath.file(ini), FileChangeMonitor.MonitoredPath.file(later)))) {
      monitor.startMonitoring();
      write(other, "x");
      assertTrue(quiet(monitor), "a file that isn't watched was reported");
      write(later, "{}");
      assertTrue(sees(monitor, later));
      write(ini, "[IG]\nig = x");
      assertTrue(sees(monitor, ini));
    }
  }

  @Test
  @DisabledOnOs(OS.MAC)
  void watchesAFolderThatIsCreatedAgain() throws Exception {
    // SUSHI deletes fsh-generated and writes it again
    Path ini = dir.resolve("ig.ini");
    write(ini, "[IG]");
    Path generated = dir.resolve("fsh-generated");
    Path resource = generated.resolve("resources").resolve("a.json");
    write(resource, "{}");
    try (FileChangeMonitor monitor = new FileChangeMonitor(Arrays.asList(
        FileChangeMonitor.MonitoredPath.file(ini), FileChangeMonitor.MonitoredPath.recursive(generated)))) {
      monitor.startMonitoring();
      FileUtilities.clearDirectory(generated.toString());
      Files.delete(generated);
      Thread.sleep(500);
      write(resource, "{\"a\":1}");
      assertTrue(sees(monitor, resource));
      monitor.clearChanges();
      write(resource, "{\"a\":2}");
      assertTrue(sees(monitor, resource));
    }
  }

  @Test
  void closeStopsMonitoring() throws Exception {
    Path input = dir.resolve("input");
    Path page = input.resolve("a.md");
    write(page, "a");
    FileChangeMonitor monitor = new FileChangeMonitor(Arrays.asList(FileChangeMonitor.MonitoredPath.recursive(input)));
    monitor.startMonitoring();
    monitor.close();
    write(page, "b");
    assertTrue(quiet(monitor));
  }
}
