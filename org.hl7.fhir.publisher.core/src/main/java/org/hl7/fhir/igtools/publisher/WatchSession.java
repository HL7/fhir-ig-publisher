package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
  /** the local template folders as they were when a template script started, while it runs */
  private SourceSnapshot beforeScript;
  private SourceSnapshot sushiInputs;


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
    buildStart = SourceSnapshot.take(watched, buildStart);
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
   * A template script is about to run. The scripts write to the template folder (the IG they are given and give back,
   * their outcomes, versions.txt...), and for a template that is used in place (template = #template) that is a
   * folder that is watched: what a script writes there is the build's own output, not a change that needs another
   * build (see templateScriptFinished())
   */
  public void templateScriptStarting() throws IOException {
    beforeScript = templateFolders.isEmpty() || buildStart == null ? null : SourceSnapshot.take(templateFolders, buildStart);
  }

  /** The template script has finished (or failed): what it changed in the template folders is part of the build */
  public void templateScriptFinished() throws IOException {
    if (beforeScript == null || buildStart == null) {
      beforeScript = null;
      return;
    }
    SourceSnapshot after = SourceSnapshot.take(templateFolders, beforeScript);
    buildStart = buildStart.withFiles(after.changesSince(beforeScript).all(), after);
    beforeScript = null;
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
