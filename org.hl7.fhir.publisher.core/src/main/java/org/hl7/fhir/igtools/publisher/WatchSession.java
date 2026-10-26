package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
  /** files in the template folder that changed while the build wrote there, but aren't named as its output */
  private Set<Path> writtenThisBuild = new HashSet<>();
  /** ... in the last build, when they alone started this build */
  private Set<Path> writtenLastBuild = new HashSet<>();
  /** files in the template folder that the build writes again on every build, with different content */
  private final Set<Path> alwaysWritten = new HashSet<>();
  private Consumer<String> log = s -> { };

  private static class OutputWindow {
    final Path folder;
    final Predicate<String> managed;
    final SourceSnapshot before;

    OutputWindow(Path folder, Predicate<String> managed, SourceSnapshot before) {
      this.folder = folder;
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
    Set<Path> written = writtenThisBuild;
    writtenThisBuild = new HashSet<>();
    writtenLastBuild = new HashSet<>();
    if (last != null && !written.isEmpty()) {
      Set<Path> changed = buildStart.changesSince(last).all();
      if (!changed.isEmpty() && written.containsAll(changed)) {
        writtenLastBuild.addAll(changed);
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
      public void writing(String folder, Predicate<String> managed) throws IOException {
        outputWriting(Paths.get(folder), managed);
      }

      @Override
      public void written() throws IOException {
        outputWritten();
      }
    };
  }

  void outputWriting(Path folder, Predicate<String> managed) throws IOException {
    Path p = folder.toAbsolutePath().normalize();
    output = null;
    if (buildStart != null && isWatched(p)) {
      output = new OutputWindow(p, managed, SourceSnapshot.take(Collections.singletonList(p), buildStart));
    }
  }

  void outputWritten() throws IOException {
    OutputWindow w = output;
    output = null;
    if (w == null || buildStart == null) {
      return;
    }
    SourceSnapshot after = SourceSnapshot.take(Collections.singletonList(w.folder), w.before);
    Set<Path> absorbed = new TreeSet<>();
    Set<Path> learnt = new TreeSet<>();
    for (Path f : after.changesSince(w.before).all()) {
      if (w.managed.test(w.folder.relativize(f).toString().replace('\\', '/')) || alwaysWritten.contains(f)) {
        absorbed.add(f);
      } else if (writtenLastBuild.contains(f)) {
        // the last build was started by this file changing while the build wrote to the template folder, and it
        // changes again: something the template writes on every build, with different content each time
        learnt.add(f);
      } else {
        writtenThisBuild.add(f);
      }
    }
    if (!learnt.isEmpty()) {
      alwaysWritten.addAll(learnt);
      absorbed.addAll(learnt);
      log.accept("Watch mode: " + describe(w.folder, learnt) + " changed again while the template wrote to its folder: "
          + "taken as the template's output from now on, not as changes");
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
