package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What watch mode decides from the source files: whether anything changed since a build started, and whether SUSHI
 * has to run again
 */
class WatchSessionTest {

  @TempDir
  Path dir;

  private void write(String path, String content) throws IOException {
    Path p = dir.resolve(path);
    Files.createDirectories(p.getParent());
    Files.write(p, content.getBytes(StandardCharsets.UTF_8));
  }

  private List<Path> roots() {
    return Arrays.asList(dir.resolve("ig.ini"), dir.resolve("sushi-config.yaml"), dir.resolve("input"), dir.resolve("fsh-generated"));
  }

  private WatchSession session() throws IOException {
    write("ig.ini", "[IG]");
    write("sushi-config.yaml", "id: x");
    write("input/fsh/a.fsh", "Profile: A");
    write("input/pagecontent/index.md", "# Home");
    write("input/images/a.png", "png");
    write("fsh-generated/resources/A.json", "{}");
    return new WatchSession(dir, roots());
  }

  @Test
  void snapshotsFindAddedModifiedAndRemovedFiles() throws IOException {
    session();
    SourceSnapshot before = SourceSnapshot.take(roots(), null);
    write("input/pagecontent/index.md", "# Home, edited");
    write("input/pagecontent/new.md", "new");
    Files.delete(dir.resolve("input/images/a.png"));
    SourceSnapshot.Changes c = SourceSnapshot.take(roots(), before).changesSince(before);
    assertEquals(Collections.singleton(dir.resolve("input/pagecontent/index.md").toAbsolutePath()), c.getModified());
    assertEquals(Collections.singleton(dir.resolve("input/pagecontent/new.md").toAbsolutePath()), c.getAdded());
    assertEquals(Collections.singleton(dir.resolve("input/images/a.png").toAbsolutePath()), c.getRemoved());
  }

  @Test
  void savingTheSameContentIsNotAChange() throws IOException {
    session();
    SourceSnapshot before = SourceSnapshot.take(roots(), null);
    write("input/fsh/a.fsh", "Profile: A");
    Files.setLastModifiedTime(dir.resolve("input/fsh/a.fsh"), FileTime.fromMillis(System.currentTimeMillis() + 5000));
    assertTrue(SourceSnapshot.take(roots(), before).changesSince(before).isEmpty());
  }

  @Test
  void aChangeThatKeepsSizeAndTimeIsOnlySeenWithoutReuse() throws IOException {
    // documents the trade-off: a file that wasn't modified recently is only read again when its size or modified
    // time changed
    session();
    Path f = dir.resolve("input/fsh/a.fsh");
    FileTime t = FileTime.fromMillis(System.currentTimeMillis() - 60000);
    Files.setLastModifiedTime(f, t);
    SourceSnapshot before = SourceSnapshot.take(roots(), null);
    write("input/fsh/a.fsh", "Profile: B");
    Files.setLastModifiedTime(f, t);
    assertTrue(SourceSnapshot.take(roots(), before).changesSince(before).isEmpty());
    assertEquals(1, SourceSnapshot.take(roots(), null).changesSince(before).getModified().size());
  }

  @Test
  void aRecentlyModifiedFileIsReadAgain() throws IOException {
    // the file system may not see the time between two saves: a file modified just before a snapshot is read again
    session();
    Path f = dir.resolve("input/fsh/a.fsh");
    FileTime t = FileTime.fromMillis(System.currentTimeMillis() - 500);
    Files.setLastModifiedTime(f, t);
    SourceSnapshot before = SourceSnapshot.take(roots(), null);
    write("input/fsh/a.fsh", "Profile: B");
    Files.setLastModifiedTime(f, t);
    assertEquals(Collections.singleton(f.toAbsolutePath()), SourceSnapshot.take(roots(), before).changesSince(before).getModified());
  }

  @Test
  void withTree() throws IOException {
    session();
    SourceSnapshot before = SourceSnapshot.take(roots(), null);
    write("fsh-generated/resources/A.json", "{\"a\":1}");
    write("input/fsh/a.fsh", "Profile: C");
    SourceSnapshot now = SourceSnapshot.take(roots(), before);
    SourceSnapshot merged = before.withTree(dir.resolve("fsh-generated"), now);
    SourceSnapshot.Changes c = now.changesSince(merged);
    assertEquals(Collections.singleton(dir.resolve("input/fsh/a.fsh").toAbsolutePath()), c.getModified());
    assertTrue(SourceSnapshot.take(roots(), null).withTree(dir.resolve("fsh-generated"), before).withTree(dir.resolve("input"), before).changesSince(before).isEmpty());
  }

  @Test
  void changesSinceBuildStartIgnoreWhatSushiWrote() throws IOException {
    WatchSession s = session();
    s.startBuild();
    write("fsh-generated/resources/A.json", "{\"generated\":true}"); // SUSHI runs
    s.sushiRan();
    assertTrue(s.changesSinceBuildStart().isEmpty());
    write("input/pagecontent/index.md", "# edited");
    assertEquals(1, s.changesSinceBuildStart().getModified().size());
  }

  @Test
  void sushiRunsUntilItHasRunOnce() throws IOException {
    WatchSession s = session();
    s.startBuild();
    assertFalse(s.canSkipSushi());
    s.sushiRan();
    s.startBuild();
    assertTrue(s.canSkipSushi(), "nothing changed");
  }

  @Test
  void sushiIsSkippedForPageEditsOnly() throws IOException {
    WatchSession s = session();
    s.startBuild();
    s.sushiRan();

    write("input/pagecontent/index.md", "# Home, edited");
    write("input/images/a.png", "png2");
    s.startBuild();
    assertTrue(s.canSkipSushi());

    write("input/fsh/a.fsh", "Profile: A2");
    s.startBuild();
    assertFalse(s.canSkipSushi(), "an FSH file changed");
    s.sushiRan();
    s.startBuild();
    assertTrue(s.canSkipSushi());

    write("input/pagecontent/added.md", "added");
    s.startBuild();
    assertFalse(s.canSkipSushi(), "SUSHI lists the pages");
    s.sushiRan();

    write("sushi-config.yaml", "id: y");
    s.startBuild();
    assertFalse(s.canSkipSushi());
    s.sushiRan();

    write("fsh-generated/resources/A.json", "{\"edited\":true}");
    s.startBuild();
    assertFalse(s.canSkipSushi(), "SUSHI's output was changed");
  }

  @Test
  void aFailedSushiRunsAgain() throws IOException {
    WatchSession s = session();
    s.startBuild();
    s.sushiRan();
    s.startBuild();
    s.sushiFailed();
    s.startBuild();
    assertFalse(s.canSkipSushi());
  }

  @Test
  void whatAFailedSushiWroteIsNotAChange() throws IOException {
    WatchSession s = session();
    s.startBuild();
    write("fsh-generated/resources/A.json", "{\"written by a SUSHI that reported errors\":true}");
    s.sushiFailed();
    assertTrue(s.changesSinceBuildStart().isEmpty());
  }

  @Test
  void anEditWhileSushiRunsIsNotMissed() throws IOException {
    WatchSession s = session();
    s.startBuild();
    write("input/fsh/a.fsh", "Profile: edited while SUSHI was running");
    s.sushiRan();
    s.startBuild();
    assertFalse(s.canSkipSushi());
  }

  @Test
  void aLocalTemplateIsWatchedOnceTheBuildHasLoadedIt() throws IOException {
    WatchSession s = session();
    write("my-template/package/package.json", "{}");
    write("my-template/includes/fragment-header.html", "<header/>");
    s.startBuild();
    s.sushiRan();
    s.watchTemplateFolders(Collections.singletonList(dir.resolve("my-template").toString()));
    assertEquals(Collections.singletonList(dir.resolve("my-template").toAbsolutePath().normalize()), s.takeNotMonitored());
    assertTrue(s.takeNotMonitored().isEmpty(), "the monitor is made again once");
    assertTrue(s.changesSinceBuildStart().isEmpty(), "the template as the build loaded it is not a change");

    write("my-template/includes/fragment-header.html", "<header>edited</header>");
    assertEquals(Collections.singleton(dir.resolve("my-template/includes/fragment-header.html").toAbsolutePath()),
        s.changesSinceBuildStart().getModified());
    s.startBuild();
    assertTrue(s.canSkipSushi(), "SUSHI doesn't read the template");
    // the next build loads it again
    s.watchTemplateFolders(Collections.singletonList(dir.resolve("my-template").toString()));
    assertTrue(s.takeNotMonitored().isEmpty(), "already watched");
    assertTrue(s.changesSinceBuildStart().isEmpty());

    write("my-template/includes/fragment-footer.html", "<footer/>");
    assertEquals(1, s.changesSinceBuildStart().getAdded().size());
  }

  @Test
  void aTemplateFolderThatIsAlreadyWatchedIsLeftAsItIs() throws IOException {
    WatchSession s = session();
    write("input/template/package/package.json", "{}");
    s.startBuild();
    s.watchTemplateFolders(Collections.singletonList(dir.resolve("input/template").toString()));
    assertTrue(s.takeNotMonitored().isEmpty());
    write("input/template/package/package.json", "{\"name\":\"t\"}");
    assertEquals(1, s.changesSinceBuildStart().getModified().size());
  }

  @Test
  void whatATemplateScriptWritesToAnInPlaceTemplateIsNotAChange() throws IOException {
    WatchSession s = session();
    write("template/config.json", "{}");
    write("template/includes/fragment-header.html", "<header/>");
    write("template/onGenerate-validation.json", "{\"old\":true}");
    s.startBuild();
    s.watchTemplateFolders(Collections.singletonList(dir.resolve("template").toString()));
    assertEquals(Collections.singletonList(dir.resolve("template").toAbsolutePath().normalize()), s.takeNotMonitored());

    s.templateScriptStarting();
    write("template/onGenerate-validation.json", "{\"new\":true}");
    write("template/onGenerate-ig-updated.json", "{}");
    write("template/versions.txt", "1");
    s.templateScriptFinished();
    assertTrue(s.changesSinceBuildStart().isEmpty(), "the script's output is the build's own");

    write("template/includes/fragment-header.html", "<header>edited</header>");
    assertEquals(Collections.singleton(dir.resolve("template/includes/fragment-header.html").toAbsolutePath()),
        s.changesSinceBuildStart().getModified());

    // the next build runs the script again: the same output is still not a change, but the edit is
    s.startBuild();
    s.templateScriptStarting();
    write("template/onGenerate-validation.json", "{\"newer\":true}");
    s.templateScriptFinished();
    assertTrue(s.changesSinceBuildStart().isEmpty());
  }

  @Test
  void anEditOutsideTheTemplateWhileAScriptRunsIsNotMissed() throws IOException {
    WatchSession s = session();
    write("template/config.json", "{}");
    s.startBuild();
    s.watchTemplateFolders(Collections.singletonList(dir.resolve("template").toString()));
    s.templateScriptStarting();
    write("input/pagecontent/index.md", "# Edited");
    s.templateScriptFinished();
    assertEquals(1, s.changesSinceBuildStart().getModified().size(), "only template folders are the script's output");
  }
}
