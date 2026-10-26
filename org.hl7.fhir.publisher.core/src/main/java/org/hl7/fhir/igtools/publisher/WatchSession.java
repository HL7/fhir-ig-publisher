package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.hl7.fhir.igtools.templates.Template;

/**
 * What watch mode (-watch) keeps from one build to the next:
 * <ul>
 *   <li>the content of the watched source files when the current build started, to decide whether anything changed
 *   since, and what</li>
 *   <li>the source files as the last SUSHI run saw them, so SUSHI is not run again when only page content changed</li>
 * </ul>
 *
 * Every rebuild is still a complete build: only work whose inputs are known not to have changed is skipped.
 */
public class WatchSession {

  /**
   * The folders under input/ whose content SUSHI doesn't read: it only lists the pages, and checks whether
   * input/includes/menu.xml exists. A change to the content of a file in one of them doesn't change what SUSHI
   * generates (adding, renaming or deleting one does)
   */
  private static final String[] PAGE_FOLDERS = {"pagecontent", "pages", "intro-notes", "includes", "images"};

  private final Path fshGenerated;
  private final List<Path> pageFolders = new ArrayList<>();
  private final List<Path> watched;
  /** the local template folders watched, which SUSHI doesn't read */
  private final List<Path> templateFolders = new ArrayList<>();
  /** folders watched since the file monitor was made */
  private final List<Path> notMonitored = new ArrayList<>();

  private SourceSnapshot buildStart;
  private SourceSnapshot sushiInputs;
  /** while the template writes to its folder, the folder as it was before */
  private OutputWindow output;
  /*
   * The loop guard, by window (a script target, or "translations"; see outputWritten()):
   */
  /** files in the template folder that changed in a window of this build, but aren't named as its output */
  private Map<String, Set<Path>> writtenThisBuild = new HashMap<>();
  /**
   * files that changed in a window, and alone started the next build (with others like them), for that many builds
   * in a row up to this one
   */
  private Map<String, Map<Path, Integer>> startedBuilds = new HashMap<>();
  /** files that a window writes on every build, with different content: taken as its output */
  private final Map<String, Set<Path>> alwaysWritten = new HashMap<>();
  private Consumer<String> log = s -> { };

  private static class OutputWindow {
    final Path folder;
    final String name;
    final Predicate<String> managed;
    final SourceSnapshot before;

    OutputWindow(Path folder, String name, Predicate<String> managed, SourceSnapshot before) {
      this.folder = folder;
      this.name = name;
      this.managed = managed;
      this.before = before;
    }
  }


  /**
   * @param rootDir the folder that contains ig.ini
   * @param watched the files and folders watched for changes (all under rootDir; local template folders are added
   *                as builds load them, see watchTemplateFolders())
   */
  public WatchSession(Path rootDir, List<Path> watched) {
    Path root = rootDir.toAbsolutePath().normalize();
    this.fshGenerated = root.resolve("fsh-generated");
    for (String s : PAGE_FOLDERS) {
      pageFolders.add(root.resolve("input").resolve(s));
    }
    this.watched = new ArrayList<>();
    for (Path p : watched) {
      this.watched.add(p.toAbsolutePath().normalize());
    }
  }

  /** Records the content of the watched files as the build that is starting will see them */
  public void startBuild() throws IOException {
    SourceSnapshot last = buildStart;
    buildStart = SourceSnapshot.take(watched, buildStart);
    countWhatStartedThisBuild(last);
  }

  /**
   * If this build was started only by files that the last build's windows changed without naming them as output, it
   * counts for each of them (and its window) one more build in a row that it started; any other start breaks the row
   */
  private void countWhatStartedThisBuild(SourceSnapshot last) {
    Map<String, Set<Path>> written = writtenThisBuild;
    Map<String, Map<Path, Integer>> before = startedBuilds;
    writtenThisBuild = new HashMap<>();
    startedBuilds = new HashMap<>();
    if (last == null || written.isEmpty()) {
      return;
    }
    Set<Path> changed = buildStart.changesSince(last).all();
    Set<Path> all = new HashSet<>();
    for (Set<Path> files : written.values()) {
      all.addAll(files);
    }
    if (changed.isEmpty() || !all.containsAll(changed)) {
      return;
    }
    for (Map.Entry<String, Set<Path>> e : written.entrySet()) {
      Map<Path, Integer> was = before.getOrDefault(e.getKey(), Collections.emptyMap());
      for (Path f : e.getValue()) {
        if (changed.contains(f)) {
          startedBuilds.computeIfAbsent(e.getKey(), k -> new HashMap<>()).put(f, was.getOrDefault(f, 0) + 1);
        }
      }
    }
  }

  /**
   * The build has loaded its template from these local folders (see TemplateManager.getLocalFolders()): they are
   * watched from now on, as they are now. Folders already watched are left as they are
   */
  public void watchTemplateFolders(List<String> folders) throws IOException {
    for (String f : folders) {
      Path p = Paths.get(f).toAbsolutePath().normalize();
      if (isWatched(p)) {
        continue;
      }
      watched.add(p);
      templateFolders.add(p);
      notMonitored.add(p);
      if (buildStart != null) {
        buildStart = buildStart.withTree(p, SourceSnapshot.take(Collections.singletonList(p), null));
      }
    }
  }

  private boolean isWatched(Path p) {
    for (Path w : watched) {
      if (p.startsWith(w)) {
        return true;
      }
    }
    return false;
  }

  /**
   * What the template tells about writing to its own folder (see Template.OutputListener): a template that is used in
   * place (template = #template) is a watched folder, and what the build writes there is its own output, not a change
   * that needs another build. Only the files the template names as its output (and those found to change on every
   * build, see outputWritten()) are taken as such; any other change made meanwhile stays a change. The folders a
   * template is loaded from (#folder, ig.ini overrides) are copied to the template folder first, so nothing is ever
   * taken from them
   */
  public Template.OutputListener templateOutputs() {
    return new Template.OutputListener() {
      @Override
      public void writing(String folder, String window, Predicate<String> managed) throws IOException {
        outputWriting(Paths.get(folder), window, managed);
      }

      @Override
      public void written() throws IOException {
        outputWritten();
      }
    };
  }

  void outputWriting(Path folder, String window, Predicate<String> managed) throws IOException {
    Path p = folder.toAbsolutePath().normalize();
    output = null;
    if (buildStart != null && isWatched(p)) {
      output = new OutputWindow(p, window, managed, SourceSnapshot.take(Collections.singletonList(p), buildStart));
    }
  }

  /**
   * What changed in the template folder while the window ran: the files it names as its output are the build's;
   * anything else stays a change (it may be the user's), except for the loop guard. A file that a window writes on
   * every build with different content (say, with the time in it) would start a build after every build. So a file
   * that alone started two builds in a row, each time after the same window changed it, and that the window changes
   * a third time, is taken as that window's output from then on, until a run of the window leaves it as it was. That
   * costs at most two extra builds; a user who saves a file while a window runs in each of three builds in a row, to
   * other content each time, would see that third save go unbuilt
   */
  void outputWritten() throws IOException {
    OutputWindow w = output;
    output = null;
    if (w == null || buildStart == null) {
      return;
    }
    SourceSnapshot after = SourceSnapshot.take(Collections.singletonList(w.folder), w.before);
    Set<Path> changed = after.changesSince(w.before).all();
    Set<Path> always = alwaysWritten.computeIfAbsent(w.name, k -> new HashSet<>());
    Map<Path, Integer> started = startedBuilds.getOrDefault(w.name, Collections.emptyMap());
    Set<Path> absorbed = new TreeSet<>();
    Set<Path> learnt = new TreeSet<>();
    Set<Path> unlearnt = new TreeSet<>();
    for (Path f : always) {
      if (!changed.contains(f)) {
        unlearnt.add(f);
      }
    }
    always.removeAll(unlearnt);
    for (Path f : changed) {
      if (w.managed.test(w.folder.relativize(f).toString().replace('\\', '/')) || always.contains(f)) {
        absorbed.add(f);
      } else if (started.getOrDefault(f, 0) >= 2) {
        learnt.add(f);
      } else {
        writtenThisBuild.computeIfAbsent(w.name, k -> new HashSet<>()).add(f);
      }
    }
    if (!unlearnt.isEmpty()) {
      log.accept("Watch mode: " + describe(w.folder, unlearnt) + " not changed by " + w.name
          + " this time: changes to it count again");
    }
    if (!learnt.isEmpty()) {
      always.addAll(learnt);
      absorbed.addAll(learnt);
      log.accept("Watch mode: " + describe(w.folder, learnt) + " changed by " + w.name + " in every build: "
          + "taken as its output from now on, not as changes");
    }
    if (!absorbed.isEmpty()) {
      buildStart = buildStart.withFiles(absorbed, after);
      log.accept("Watch mode: " + describe(w.folder, absorbed) + " written by the build to the template folder: not a change");
    }
  }

  private static String describe(Path folder, Collection<Path> files) {
    if (files.size() > 3) {
      return files.size() + " files";
    }
    List<String> names = new ArrayList<>();
    for (Path f : files) {
      names.add(folder.relativize(f).toString().replace('\\', '/'));
    }
    return String.join(", ", names);
  }

  /** Where watch mode reports what it decides (the publisher's log) */
  public void setLog(Consumer<String> log) {
    this.log = log;
  }

  /** The folders watched since the file monitor was last made (and forgets them): it has to be made again */
  public List<Path> takeNotMonitored() {
    List<Path> res = new ArrayList<>(notMonitored);
    notMonitored.clear();
    return res;
  }

  /** What changed in the watched files since the current (or last) build started */
  public SourceSnapshot.Changes changesSinceBuildStart() throws IOException {
    SourceSnapshot now = SourceSnapshot.take(watched, buildStart);
    return now.changesSince(buildStart);
  }

  // --- SUSHI -------------------------------------------------------------------------------------------------------

  /**
   * SUSHI doesn't need to run if it ran before in this session and nothing changed since then except the content of
   * pages and the local template folders (SUSHI's output, in fsh-generated, must not have changed either)
   */
  public boolean canSkipSushi() {
    if (sushiInputs == null || buildStart == null) {
      return false;
    }
    SourceSnapshot.Changes changes = buildStart.changesSince(sushiInputs);
    for (Path p : changes.getAdded()) {
      if (!isTemplate(p)) {
        return false;
      }
    }
    for (Path p : changes.getRemoved()) {
      if (!isTemplate(p)) {
        return false;
      }
    }
    for (Path p : changes.getModified()) {
      if (!isPageContent(p) && !isTemplate(p)) {
        return false;
      }
    }
    return true;
  }

  private boolean isTemplate(Path p) {
    for (Path f : templateFolders) {
      if (p.startsWith(f)) {
        return true;
      }
    }
    return false;
  }

  private boolean isPageContent(Path p) {
    for (Path f : pageFolders) {
      if (p.startsWith(f)) {
        return true;
      }
    }
    return false;
  }

  /**
   * SUSHI ran successfully at the start of this build. What it generated is part of the build's sources from now on,
   * not a change that needs another build
   */
  public void sushiRan() throws IOException {
    if (buildStart == null) {
      return;
    }
    takeSushiOutput();
    // the inputs are taken from before SUSHI ran: if one changed while it was running, it's seen as changed next time
    sushiInputs = buildStart;
  }

  /**
   * SUSHI was run, but didn't finish properly: it must run next time. What it wrote (it writes its output even when
   * it reports errors) is not a change that needs another build either: the same sources would fail the same way
   */
  public void sushiFailed() throws IOException {
    sushiInputs = null;
    if (buildStart != null) {
      takeSushiOutput();
    }
  }

  private void takeSushiOutput() throws IOException {
    SourceSnapshot generated = SourceSnapshot.take(Collections.singletonList(fshGenerated), buildStart);
    buildStart = buildStart.withTree(fshGenerated, generated);
  }
}
