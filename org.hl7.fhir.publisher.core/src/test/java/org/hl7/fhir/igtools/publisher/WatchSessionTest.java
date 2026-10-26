package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.hl7.fhir.igtools.templates.Template;
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

  /** the files a template script names as its output (as Template.scriptOutputs() does) */
  private static boolean scriptOutput(String path) {
    return path.matches("onGenerate-(validation\\.json|ig-working\\.json|ig-updated\\.xml)|versions\\.txt");
  }

  private WatchSession inPlace(List<String> log) throws IOException {
    WatchSession s = session();
    s.setLog(log::add);
    write("template/config.json", "{}");
    write("template/includes/fragment-header.html", "<header/>");
    write("template/onGenerate-validation.json", "{\"old\":true}");
    s.startBuild();
    s.watchTemplateFolders(Collections.singletonList(dir.resolve("template").toString()));
    assertEquals(Collections.singletonList(dir.resolve("template").toAbsolutePath().normalize()), s.takeNotMonitored());
    return s;
  }

  private Path abs(String path) {
    return dir.resolve(path).toAbsolutePath().normalize();
  }

  @Test
  void whatATemplateScriptWritesToAnInPlaceTemplateIsNotAChange() throws IOException {
    List<String> log = new ArrayList<>();
    WatchSession s = inPlace(log);
    Template.OutputListener l = s.templateOutputs();

    l.writing(dir.resolve("template").toString(), WatchSessionTest::scriptOutput);
    write("template/onGenerate-validation.json", "{\"new\":true}");
    write("template/onGenerate-ig-updated.xml", "<ig/>");
    write("template/versions.txt", "1");
    l.written();
    assertTrue(s.changesSinceBuildStart().isEmpty(), "the script's output is the build's own");
    assertEquals(Collections.singletonList("Watch mode: onGenerate-ig-updated.xml, onGenerate-validation.json, versions.txt "
        + "written by the build to the template folder: not a change"), log);

    write("template/includes/fragment-header.html", "<header>edited</header>");
    assertEquals(Collections.singleton(abs("template/includes/fragment-header.html")), s.changesSinceBuildStart().getModified(),
        "the user's edit is a change");

    // the next build runs the script again: the new output is still not a change
    s.startBuild();
    l.writing(dir.resolve("template").toString(), WatchSessionTest::scriptOutput);
    write("template/onGenerate-validation.json", "{\"newer\":true}");
    l.written();
    assertTrue(s.changesSinceBuildStart().isEmpty());
  }

  @Test
  void aUserEditInTheTemplateWhileAScriptRunsIsStillAChange() throws IOException {
    WatchSession s = inPlace(new ArrayList<>());
    Template.OutputListener l = s.templateOutputs();
    l.writing(dir.resolve("template").toString(), WatchSessionTest::scriptOutput);
    write("template/onGenerate-validation.json", "{\"new\":true}");
    write("template/includes/fragment-header.html", "<header>edited</header>");
    write("input/pagecontent/index.md", "# Edited");
    l.written();
    SourceSnapshot.Changes c = s.changesSinceBuildStart();
    assertEquals(new HashSet<>(Arrays.asList(abs("template/includes/fragment-header.html"), abs("input/pagecontent/index.md"))),
        c.getModified());
  }

  @Test
  void nothingIsTakenFromALocalTemplateFolderThatIsCopiedToTheTemplateFolder() throws IOException {
    List<String> log = new ArrayList<>();
    WatchSession s = session();
    s.setLog(log::add);
    write("my-template/includes/fragment-header.html", "<header/>");
    write("template/onGenerate-validation.json", "{\"old\":true}");
    s.startBuild();
    s.watchTemplateFolders(Collections.singletonList(dir.resolve("my-template").toString()));
    Template.OutputListener l = s.templateOutputs();
    // the scripts run on the copy in template/, which isn't watched
    l.writing(dir.resolve("template").toString(), path -> true);
    write("template/onGenerate-validation.json", "{\"new\":true}");
    write("my-template/includes/fragment-header.html", "<header>edited</header>");
    l.written();
    assertEquals(Collections.singleton(abs("my-template/includes/fragment-header.html")), s.changesSinceBuildStart().getModified());
    assertTrue(log.isEmpty());
  }

  @Test
  void outputThatChangesOnEveryBuildIsTakenAsOutputAfterOneMoreBuild() throws IOException {
    List<String> log = new ArrayList<>();
    WatchSession s = inPlace(log);
    Template.OutputListener l = s.templateOutputs();
    // build 1: the script writes a file that isn't named as its output, with a time in it
    l.writing(dir.resolve("template").toString(), WatchSessionTest::scriptOutput);
    write("template/stamp.txt", "built at 1");
    l.written();
    assertEquals(Collections.singleton(abs("template/stamp.txt")), s.changesSinceBuildStart().getAdded(), "it may be the user's");
    // build 2, started by that alone: it changes again
    s.startBuild();
    l.writing(dir.resolve("template").toString(), WatchSessionTest::scriptOutput);
    write("template/stamp.txt", "built at 2");
    l.written();
    assertTrue(s.changesSinceBuildStart().isEmpty(), "no loop");
    assertEquals(Arrays.asList("Watch mode: stamp.txt changed again while the template wrote to its folder: taken as the "
        + "template's output from now on, not as changes",
        "Watch mode: stamp.txt written by the build to the template folder: not a change"), log);
    // build 3, for an edit
    write("template/includes/fragment-header.html", "<header>edited</header>");
    s.startBuild();
    l.writing(dir.resolve("template").toString(), WatchSessionTest::scriptOutput);
    write("template/stamp.txt", "built at 3");
    l.written();
    assertTrue(s.changesSinceBuildStart().isEmpty());
  }

  @Test
  void aUserEditDuringAScriptIsNotTakenAsOutputByTheLoopGuard() throws IOException {
    WatchSession s = inPlace(new ArrayList<>());
    Template.OutputListener l = s.templateOutputs();
    l.writing(dir.resolve("template").toString(), WatchSessionTest::scriptOutput);
    write("template/includes/fragment-header.html", "<header>edited</header>");
    l.written();
    // the build it starts runs the script, which doesn't write that file
    s.startBuild();
    l.writing(dir.resolve("template").toString(), WatchSessionTest::scriptOutput);
    write("template/onGenerate-validation.json", "{\"new\":true}");
    l.written();
    assertTrue(s.changesSinceBuildStart().isEmpty());
    // so a later edit of it is a change
    write("template/includes/fragment-header.html", "<header>edited again</header>");
    assertEquals(Collections.singleton(abs("template/includes/fragment-header.html")), s.changesSinceBuildStart().getModified());
  }

  @Test
  void aChangeThatStartedABuildWithOthersIsNotTakenAsOutput() throws IOException {
    WatchSession s = inPlace(new ArrayList<>());
    Template.OutputListener l = s.templateOutputs();
    l.writing(dir.resolve("template").toString(), WatchSessionTest::scriptOutput);
    write("template/stamp.txt", "built at 1");
    l.written();
    write("input/pagecontent/index.md", "# Edited"); // the build isn't started by template output alone
    s.startBuild();
    l.writing(dir.resolve("template").toString(), WatchSessionTest::scriptOutput);
    write("template/stamp.txt", "built at 2");
    l.written();
    assertEquals(Collections.singleton(abs("template/stamp.txt")), s.changesSinceBuildStart().getModified());
  }
}
