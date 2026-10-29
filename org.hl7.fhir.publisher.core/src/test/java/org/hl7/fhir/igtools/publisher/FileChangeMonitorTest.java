package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

// macOS uses the polling monitor
@DisabledOnOs(OS.MAC)
class FileChangeMonitorTest {

  @TempDir
  Path dir;

  private Path page;
  private Path fshGenerated;
  private FileChangeMonitor monitor;

  @BeforeEach
  void setUp() throws Exception {
    Files.writeString(dir.resolve("ig.ini"), "[IG]");
    Files.createDirectories(dir.resolve("input/pagecontent"));
    page = dir.resolve("input/pagecontent/index.md");
    Files.writeString(page, "one");
    fshGenerated = dir.resolve("fsh-generated");
    Files.createDirectories(fshGenerated.resolve("resources"));
    monitor = newMonitor();
  }

  // as the publisher's watch mode does: some files in the IG folder, the input folder, and SUSHI's output
  private FileChangeMonitor newMonitor() throws Exception {
    return new FileChangeMonitor(List.of(
        FileChangeMonitor.MonitoredPath.file(dir.resolve("ig.ini")),
        FileChangeMonitor.MonitoredPath.recursive(dir.resolve("input")),
        FileChangeMonitor.MonitoredPath.recursive(fshGenerated)));
  }

  @AfterEach
  void tearDown() throws Exception {
    monitor.close();
  }

  @Test
  void anEditInAWatchedFolderIsReportedWhenFilesAreWatchedToo() throws Exception {
    monitor.startMonitoring();
    Files.writeString(page, "two");

    FileChangeMonitor.FileChangeEvent event = monitor.waitForChange(10, TimeUnit.SECONDS);

    assertNotNull(event, "the edit to index.md wasn't reported");
    assertEquals(page.toAbsolutePath(), event.getPath());
  }

  @Test
  void itSaysWhetherItIsWatching() throws Exception {
    assertTrue(!monitor.isWatching());
    monitor.startMonitoring();
    assertTrue(monitor.isWatching());
    monitor.stopMonitoring();
    assertTrue(!monitor.isWatching());
  }

  @Test
  void otherFilesNextToAWatchedFileAreNotReported() throws Exception {
    monitor.startMonitoring();
    Files.writeString(dir.resolve("notes.txt"), "not watched");

    assertNull(monitor.waitForChange(2, TimeUnit.SECONDS));
  }

  @Test
  void changesAreReportedAfterMonitoringIsStartedAgain() throws Exception {
    monitor.close();
    // only the folder, so that this doesn't depend on the files being watched too
    monitor = new FileChangeMonitor(List.of(FileChangeMonitor.MonitoredPath.recursive(dir.resolve("input"))));
    monitor.startMonitoring();
    Files.writeString(page, "two");
    assertNotNull(monitor.waitForChange(10, TimeUnit.SECONDS));
    monitor.clearChanges();
    monitor.stopMonitoring();

    monitor.startMonitoring();
    Files.writeString(page, "three");

    assertNotNull(monitor.waitForChange(10, TimeUnit.SECONDS), "nothing was reported after the restart");
  }

  @Test
  void whatTheBuildWroteIsNotReported() throws Exception {
    // what SUSHI writes while the build runs, between the monitor being made (or stopped) and started
    Files.writeString(fshGenerated.resolve("resources/StructureDefinition-example.json"), "{}");
    Thread.sleep(500);

    monitor.startMonitoring(List.of(fshGenerated));

    assertNull(monitor.waitForChange(2, TimeUnit.SECONDS));
  }

  @Test
  void anEditMadeWhileABuildRanIsReportedWhenMonitoringStartsAgain() throws Exception {
    monitor.startMonitoring();
    monitor.stopMonitoring();
    // the build runs: SUSHI writes its output, and the user saves a page
    Files.writeString(fshGenerated.resolve("resources/StructureDefinition-example.json"), "{}");
    Files.writeString(page, "edited during the build");
    Thread.sleep(500);

    monitor.startMonitoring(List.of(fshGenerated));

    FileChangeMonitor.FileChangeEvent event = monitor.waitForChange(10, TimeUnit.SECONDS);
    assertNotNull(event, "the edit made during the build wasn't reported");
    assertEquals(page.toAbsolutePath(), event.getPath());
    for (FileChangeMonitor.FileChangeEvent other : monitor.getChanges()) {
      assertEquals(page.toAbsolutePath(), other.getPath());
    }
  }

  @Test
  void onlyTheFilesTheBuildWroteAreIgnored() throws Exception {
    // a module that pre-processes the IG writes some files in input; the user edits another one meanwhile
    Path generated = dir.resolve("input/xver-ig.xml");
    Files.writeString(generated, "<ImplementationGuide/>");
    Files.writeString(page, "edited during the build");
    Thread.sleep(500);
    PrintStream out = System.out;
    ByteArrayOutputStream log = new ByteArrayOutputStream();
    System.setOut(new PrintStream(log, true));
    try {
      monitor.startMonitoring(List.of(generated));
    } finally {
      System.setOut(out);
    }

    List<Path> reported = reportedPaths(page);
    assertTrue(reported.contains(page.toAbsolutePath()), "the edit made during the build wasn't reported");
    assertTrue(!reported.contains(generated.toAbsolutePath()), "what the build wrote was reported: " + reported);
    assertTrue(log.toString().contains("changes made by the build, in " + dir.resolve("input").toAbsolutePath()), log.toString());
  }

  @Test
  void aFolderMadeWhileMonitoringIsWatched() throws Exception {
    monitor.startMonitoring();
    Path images = dir.resolve("input/images/new");
    Files.createDirectories(images);
    Files.writeString(images.resolve("one.png"), "one");

    assertTrue(reportedPaths(images.resolve("one.png")).contains(images.resolve("one.png").toAbsolutePath()), "the file in the new folder wasn't reported");
    Thread.sleep(200);
    monitor.clearChanges();
    Files.writeString(images.resolve("two.png"), "two");
    assertTrue(reportedPaths(images.resolve("two.png")).contains(images.resolve("two.png").toAbsolutePath()), "the new folder isn't watched");
  }

  @Test
  void aFolderThatIsDeletedAndMadeAgainIsWatched() throws Exception {
    monitor.startMonitoring();
    // e.g. git switching branches
    Path pages = dir.resolve("input/pagecontent");
    Files.delete(page);
    deleteFolder(pages);
    Thread.sleep(500);
    makeAgain(pages);
    Thread.sleep(500);
    monitor.clearChanges();
    Files.writeString(page, "back again");

    assertTrue(reportedPaths(page).contains(page.toAbsolutePath()), "the folder made again isn't watched");
  }

  @Test
  void aWatchedFolderThatIsMadeLaterIsWatched() throws Exception {
    // fsh-generated, before SUSHI first runs
    monitor.close();
    deleteFolder(fshGenerated.resolve("resources"));
    deleteFolder(fshGenerated);
    monitor = new FileChangeMonitor(List.of(
        FileChangeMonitor.MonitoredPath.file(dir.resolve("ig.ini")),
        FileChangeMonitor.MonitoredPath.recursive(fshGenerated)));
    monitor.startMonitoring();
    Path sd = fshGenerated.resolve("resources/StructureDefinition-example.json");
    makeAgain(sd.getParent());
    Files.writeString(sd, "{}");

    assertTrue(reportedPaths(sd).contains(sd.toAbsolutePath()), "fsh-generated isn't watched once it's made");
    Thread.sleep(200);
    monitor.clearChanges();
    Files.writeString(sd, "{ }");
    assertTrue(reportedPaths(sd).contains(sd.toAbsolutePath()), "fsh-generated isn't watched once it's made");
  }

  @Test
  void theFilesAStepOfTheBuildWroteAreFound() throws Exception {
    Path unchanged = dir.resolve("input/unchanged.md");
    Files.writeString(unchanged, "same");
    Path deleted = dir.resolve("input/deleted.md");
    Files.writeString(deleted, "going");
    Map<Path, String> before = FileChangeMonitor.fileStates(dir.resolve("input"));

    Files.writeString(page, "a longer page than it was");
    Files.delete(deleted);
    Path added = dir.resolve("input/added.md");
    Files.writeString(added, "new");

    Set<Path> changed = FileChangeMonitor.changedFiles(before, FileChangeMonitor.fileStates(dir.resolve("input")));
    assertEquals(Set.of(page.toAbsolutePath(), deleted.toAbsolutePath(), added.toAbsolutePath()), changed);
  }

  @Test
  void whatAModuleWritesInInputDoesntStartAnotherBuild() throws Exception {
    Path input = dir.resolve("input");
    Path includes = input.resolve("includes");
    Files.createDirectories(includes);
    Path extensions = input.resolve("extensions");
    Files.createDirectories(extensions);
    Files.writeString(extensions.resolve("old.xml"), "old");
    monitor.close();
    monitor = newMonitor();

    // the module pre-processes the IG: more files in a folder than the watch service keeps the events of, a folder
    // made again, and a new one
    Map<Path, String> before = FileChangeMonitor.fileStates(input);
    for (int i = 0; i < 600; i++) {
      Files.writeString(includes.resolve("cross-version-" + i + ".xhtml"), "<div/>");
    }
    Files.writeString(input.resolve("xver-ig.xml"), "<ImplementationGuide/>");
    Files.delete(extensions.resolve("old.xml"));
    deleteFolder(extensions);
    makeAgain(extensions);
    Files.writeString(extensions.resolve("new.xml"), "new");
    Files.createDirectories(input.resolve("codes"));
    Files.writeString(input.resolve("codes/ConceptMap-example.xml"), "<ConceptMap/>");
    Map<Path, String> after = FileChangeMonitor.fileStates(input);
    Thread.sleep(500);

    monitor.startMonitoring(List.of(), moduleWrites(before, FileChangeMonitor.changedFiles(before, after)));

    assertNull(monitor.waitForChange(2, TimeUnit.SECONDS));
  }

  // as the publisher does, for a module that pre-processes the IG
  private FileChangeMonitor.FolderWrites moduleWrites(Map<Path, String> before, Set<Path> written) {
    return new FileChangeMonitor.FolderWrites(dir.resolve("input"), before, written, List.of(dir.resolve("input/fsh")));
  }

  @Test
  void whatAModuleWroteInAnEarlierBuildIsntReported() throws Exception {
    // on Windows, what it writes may not look changed until it closes the file: after it's done
    Path generated = dir.resolve("input/xver-ig.xml");
    Map<Path, String> before = FileChangeMonitor.fileStates(dir.resolve("input"));
    Map<Path, String> after = FileChangeMonitor.fileStates(dir.resolve("input"));
    Files.writeString(generated, "<ImplementationGuide/>");
    Thread.sleep(500);

    // it wrote it in an earlier build too
    monitor.startMonitoring(List.of(), moduleWrites(before, Set.of(generated)));

    assertNull(monitor.waitForChange(2, TimeUnit.SECONDS));
  }

  @Test
  void editsAreReportedWhenAModuleWritesInInput() throws Exception {
    Path input = dir.resolve("input");
    Path generated = input.resolve("xver-ig.xml");
    Files.writeString(generated, "<ImplementationGuide/>");
    Path fsh = input.resolve("fsh/profile.fsh");
    Files.createDirectories(fsh.getParent());
    Files.writeString(fsh, "Profile: A");
    Path other = input.resolve("pagecontent/other.md");
    monitor.close();
    monitor = newMonitor();

    Map<Path, String> before = FileChangeMonitor.fileStates(input);
    Thread.sleep(50);
    // the module writes, and the user saves some FSH meanwhile
    Files.writeString(generated, "<ImplementationGuide id=\"x\"/>");
    Files.createDirectories(input.resolve("includes"));
    Files.writeString(input.resolve("includes/cross-version.xhtml"), "<div/>");
    Files.writeString(fsh, "Profile: B edited");
    Map<Path, String> after = FileChangeMonitor.fileStates(input);
    // then a page
    Files.writeString(other, "edited after the module ran");
    Thread.sleep(500);

    monitor.startMonitoring(List.of(), moduleWrites(before, FileChangeMonitor.changedFiles(before, after)));

    List<Path> reported = reportedPaths(other);
    assertTrue(reported.containsAll(List.of(other.toAbsolutePath(), fsh.toAbsolutePath())), reported.toString());
    assertTrue(!reported.contains(input.resolve("includes/cross-version.xhtml").toAbsolutePath()), "what the module wrote was reported: " + reported);
    assertTrue(!reported.contains(generated.toAbsolutePath()), "what the module wrote was reported: " + reported);
  }

  @Test
  void aFolderThatIsDeletedAndMadeAgainStraightAwayIsWatched() throws Exception {
    monitor.startMonitoring();
    Path pages = dir.resolve("input/pagecontent");
    Files.delete(page);
    deleteFolder(pages);
    makeAgain(pages);
    Thread.sleep(500);
    monitor.clearChanges();

    Files.writeString(page, "back again");
    assertTrue(reportedPaths(page).contains(page.toAbsolutePath()), "the folder made again isn't watched");
    // nor what's made in it
    Path sub = pages.resolve("sub/page.md");
    Files.createDirectories(sub.getParent());
    Thread.sleep(200);
    monitor.clearChanges();
    Files.writeString(sub, "new");
    assertTrue(reportedPaths(sub).contains(sub.toAbsolutePath()), "a folder made in the folder made again isn't watched");
  }

  @Test
  void aWatchedFolderThatIsDeletedAndMadeAgainStraightAwayIsWatched() throws Exception {
    monitor.startMonitoring();
    // e.g. SUSHI clearing its output
    deleteFolder(fshGenerated.resolve("resources"));
    deleteFolder(fshGenerated);
    makeAgain(fshGenerated.resolve("resources"));
    Thread.sleep(500);
    monitor.clearChanges();

    Path sd = fshGenerated.resolve("resources/StructureDefinition-example.json");
    Files.writeString(sd, "{}");
    assertTrue(reportedPaths(sd).contains(sd.toAbsolutePath()), "fsh-generated made again isn't watched");
  }

  @Test
  void aFolderMadeWhenEventsWereLostIsWatched() throws Exception {
    Path includes = dir.resolve("input/includes");
    Files.createDirectories(includes);
    monitor.close();
    monitor = newMonitor();
    for (int i = 0; i < 600; i++) {
      Files.writeString(includes.resolve("include-" + i + ".xhtml"), "<div/>");
    }
    Path sub = includes.resolve("sub");
    Files.createDirectories(sub);
    Thread.sleep(500);
    monitor.startMonitoring();
    assertNotNull(monitor.waitForChange(10, TimeUnit.SECONDS));
    Thread.sleep(300);
    monitor.clearChanges();

    Path file = sub.resolve("include.xhtml");
    Files.writeString(file, "<div/>");
    assertTrue(reportedPaths(file).contains(file.toAbsolutePath()), "the folder made when events were lost isn't watched");
  }

  @Test
  void aWatchedFolderMadeWhenEventsWereLostIsWatched() throws Exception {
    // fsh-generated, waited for in the IG folder, made when that folder's events were lost
    monitor.close();
    deleteFolder(fshGenerated.resolve("resources"));
    deleteFolder(fshGenerated);
    monitor = newMonitor();
    for (int i = 0; i < 600; i++) {
      Files.writeString(dir.resolve("notes-" + i + ".txt"), "not watched");
    }
    makeAgain(fshGenerated.resolve("resources"));
    Thread.sleep(500);
    monitor.startMonitoring();
    Thread.sleep(300);
    monitor.clearChanges();

    Path sd = fshGenerated.resolve("resources/StructureDefinition-example.json");
    Files.writeString(sd, "{}");
    assertTrue(reportedPaths(sd).contains(sd.toAbsolutePath()), "fsh-generated isn't watched");
  }

  // on Windows, what's in a folder that is watched can be pending delete for a moment
  private static void deleteFolder(Path folder) throws Exception {
    for (int i = 0; ; i++) {
      try {
        Files.delete(folder);
        return;
      } catch (DirectoryNotEmptyException | AccessDeniedException e) {
        if (i == 50) {
          throw e;
        }
        Thread.sleep(100);
      }
    }
  }

  // on Windows, a deleted folder that was watched can be pending delete for a moment
  private static void makeAgain(Path folder) throws Exception {
    for (int i = 0; ; i++) {
      try {
        Files.createDirectories(folder);
        return;
      } catch (AccessDeniedException e) {
        if (i == 50) {
          throw e;
        }
        Thread.sleep(100);
      }
    }
  }

  // the paths reported, until the one expected (or 10 seconds)
  private List<Path> reportedPaths(Path expected) throws InterruptedException {
    List<Path> paths = new ArrayList<>();
    long end = System.currentTimeMillis() + 10000;
    while (System.currentTimeMillis() < end && !paths.contains(expected.toAbsolutePath())) {
      FileChangeMonitor.FileChangeEvent event = monitor.waitForChange(100, TimeUnit.MILLISECONDS);
      if (event != null) {
        paths.add(event.getPath());
      }
    }
    // and anything else that came with it
    FileChangeMonitor.FileChangeEvent event;
    while ((event = monitor.waitForChange(300, TimeUnit.MILLISECONDS)) != null) {
      paths.add(event.getPath());
    }
    return paths;
  }

  @Test
  void lostEventsAreReportedAsAChange() throws Exception {
    // more events than the watch service keeps for a folder: it then reports an overflow, and the change to
    // ig.ini is lost in it
    for (int i = 0; i < 600; i++) {
      Files.writeString(dir.resolve("other-" + i + ".txt"), "not watched");
    }
    Files.writeString(dir.resolve("ig.ini"), "[IG]\nchanged");
    Thread.sleep(500);

    monitor.startMonitoring();

    assertNotNull(monitor.waitForChange(10, TimeUnit.SECONDS), "the lost change wasn't reported");
  }
}
