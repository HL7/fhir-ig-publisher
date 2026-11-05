package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// the polling monitor (used on macOS), on any OS
class PollingFileChangeMonitorTest {

  @TempDir
  Path dir;

  private Path page;
  private Path sd;
  private Path fshGenerated;
  private FileChangeMonitor monitor;

  @BeforeEach
  void setUp() throws Exception {
    Files.writeString(dir.resolve("ig.ini"), "[IG]");
    Files.createDirectories(dir.resolve("input/pagecontent"));
    page = dir.resolve("input/pagecontent/index.md");
    Files.writeString(page, "one");
    fshGenerated = dir.resolve("fsh-generated");
    sd = fshGenerated.resolve("resources/StructureDefinition-example.json");
    Files.createDirectories(sd.getParent());
    Files.writeString(sd, "{}");
    monitor = new FileChangeMonitor(List.of(
        FileChangeMonitor.MonitoredPath.file(dir.resolve("ig.ini")),
        FileChangeMonitor.MonitoredPath.recursive(dir.resolve("input")),
        FileChangeMonitor.MonitoredPath.recursive(fshGenerated)), true);
  }

  @AfterEach
  void tearDown() throws Exception {
    monitor.close();
  }

  @Test
  void whatTheBuildWroteIsNotReported() throws Exception {
    // SUSHI rewrites its output while the build runs
    Files.writeString(sd, "{ \"resourceType\" : \"StructureDefinition\" }");
    Files.writeString(fshGenerated.resolve("resources/ValueSet-example.json"), "{}");

    monitor.startMonitoring(List.of(fshGenerated));

    assertNull(monitor.waitForChange(1, TimeUnit.SECONDS));
  }

  @Test
  void anEditMadeWhileABuildRanIsReported() throws Exception {
    Files.writeString(sd, "{ \"resourceType\" : \"StructureDefinition\" }");
    Files.writeString(page, "edited during the build");

    monitor.startMonitoring(List.of(fshGenerated));

    FileChangeMonitor.FileChangeEvent event = monitor.waitForChange(5, TimeUnit.SECONDS);
    assertNotNull(event, "the edit made during the build wasn't reported");
    assertEquals(page, event.getPath());
    assertNull(monitor.waitForChange(1, TimeUnit.SECONDS));
  }

  @Test
  void onlyTheEditsAreReportedWhenAModuleWritesInInput() throws Exception {
    Path input = dir.resolve("input");
    Map<Path, String> before = FileChangeMonitor.fileStates(input);
    Path generated = input.resolve("includes/cross-version.xhtml");
    Files.createDirectories(generated.getParent());
    Files.writeString(generated, "<div/>");
    Map<Path, String> after = FileChangeMonitor.fileStates(input);
    Files.writeString(page, "edited during the build");

    monitor.startMonitoring(List.of(), new FileChangeMonitor.FolderWrites(input, before, FileChangeMonitor.changedFiles(before, after), List.of(input.resolve("fsh"))));

    List<Path> reported = new ArrayList<>();
    FileChangeMonitor.FileChangeEvent event;
    while ((event = monitor.waitForChange(1, TimeUnit.SECONDS)) != null) {
      reported.add(event.getPath());
    }
    assertEquals(List.of(page), reported);
  }
}
