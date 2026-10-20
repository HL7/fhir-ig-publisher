package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.standalone.context.SimpleWorkerContext;
import org.hl7.fhir.utilities.VersionUtilities;

/**
 * What watch mode (-watch) keeps from one build to the next, so that a rebuild doesn't start from nothing:
 * <ul>
 *   <li>the content of the watched source files when the current build started, to decide whether anything changed
 *   since, and what</li>
 *   <li>the source files as the last SUSHI run saw them, so SUSHI is not run again when only page content changed</li>
 *   <li>the worker context as it was once the core package and the dependencies were loaded, so that a rebuild
 *   copies it instead of loading all the packages again</li>
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

  /** Thrown when a build that started with a copy of the previous worker context finds it can't use it */
  public static class WarmContextUnusableException extends Exception {
    public WarmContextUnusableException(String message) {
      super(message);
    }
  }

  private final Path fshGenerated;
  private final List<Path> pageFolders = new ArrayList<>();
  private final List<Path> watched;

  private SourceSnapshot buildStart;
  private SourceSnapshot sushiInputs;

  private String contextKey;
  private SimpleWorkerContext context;
  private List<String> contextCorePackages;
  private List<String> contextPackages;
  private boolean contextAllowed = true;
  private int contextUses;

  /**
   * @param rootDir the folder that contains ig.ini
   * @param watched the files and folders watched for changes (all under rootDir)
   */
  public WatchSession(Path rootDir, List<Path> watched) {
    Path root = rootDir.toAbsolutePath();
    this.fshGenerated = root.resolve("fsh-generated");
    for (String s : PAGE_FOLDERS) {
      pageFolders.add(root.resolve("input").resolve(s));
    }
    this.watched = new ArrayList<>();
    for (Path p : watched) {
      this.watched.add(p.toAbsolutePath());
    }
  }

  /** Records the content of the watched files as the build that is starting will see them */
  public void startBuild() throws IOException {
    buildStart = SourceSnapshot.take(watched, buildStart);
    contextAllowed = true;
  }

  /** What changed in the watched files since the current (or last) build started */
  public SourceSnapshot.Changes changesSinceBuildStart() throws IOException {
    SourceSnapshot now = SourceSnapshot.take(watched, buildStart);
    return now.changesSince(buildStart);
  }

  // --- SUSHI -------------------------------------------------------------------------------------------------------

  /**
   * SUSHI doesn't need to run if it ran before in this session and nothing changed since then except the content of
   * pages (SUSHI's output, in fsh-generated, must not have changed either)
   */
  public boolean canSkipSushi() {
    if (sushiInputs == null || buildStart == null) {
      return false;
    }
    SourceSnapshot.Changes changes = buildStart.changesSince(sushiInputs);
    if (!changes.getAdded().isEmpty() || !changes.getRemoved().isEmpty()) {
      return false;
    }
    for (Path p : changes.getModified()) {
      if (!isPageContent(p)) {
        return false;
      }
    }
    return true;
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
    SourceSnapshot generated = SourceSnapshot.take(Collections.singletonList(fshGenerated), buildStart);
    buildStart = buildStart.withTree(fshGenerated, generated);
    // the inputs are taken from before SUSHI ran: if one changed while it was running, it's seen as changed next time
    sushiInputs = buildStart;
  }

  /** SUSHI was run, but didn't finish properly: it must run next time */
  public void sushiFailed() {
    sushiInputs = null;
  }

  // --- worker context ----------------------------------------------------------------------------------------------

  /**
   * A copy of the worker context kept by an earlier build, if it was loaded from the same inputs (key), or null.
   * The copy has everything the earlier build loaded into it up to the point it was kept, so loading a package that
   * was loaded then does nothing
   */
  public SimpleWorkerContext copyContext(String key) throws IOException {
    if (!contextAllowed || context == null || key == null || !key.equals(contextKey)) {
      return null;
    }
    contextUses++;
    return new SimpleWorkerContext(context);
  }

  /**
   * The packages (id#version) the context had loaded once the core package was loaded: the start of the list of
   * loaded packages that a build which had loaded everything itself would have
   */
  public List<String> getContextCorePackages() {
    return new ArrayList<>(contextCorePackages);
  }

  /**
   * After a build that started with a copy of the context loaded its dependencies: they must be the ones the context
   * was loaded with (they can change without any change to the IG, e.g. a new #current build in the package cache)
   */
  public void checkPackages(List<String> packages) throws WarmContextUnusableException {
    if (!packages.equals(contextPackages)) {
      contextAllowed = false;
      context = null;
      throw new WarmContextUnusableException("The packages have changed since the worker context was loaded");
    }
  }

  /** Abandon the copy of the context for the rest of this build (it is restarted, and loads everything) */
  public void abandonContext() {
    contextAllowed = false;
  }

  /**
   * Keep (a copy of) a context that a build loaded itself, once it loaded its dependencies and before it loaded
   * any of the IG's own resources
   */
  public void keepContext(String key, SimpleWorkerContext ctxt, List<String> corePackages, List<String> packages) throws IOException {
    if (key == null) {
      context = null;
      contextKey = null;
      return;
    }
    contextKey = key;
    context = new SimpleWorkerContext(ctxt);
    contextCorePackages = new ArrayList<>(corePackages);
    contextPackages = new ArrayList<>(packages);
  }

  /** how many builds started with a copy of a kept context */
  public int getContextUses() {
    return contextUses;
  }

  /**
   * Whether a context that has loaded the packages in loaded has loaded dep: the same answer as
   * SimpleWorkerContext.hasPackage(String) would give
   */
  public static boolean hasPackage(List<String> loaded, String dep) {
    if (loaded.contains(dep)) {
      return true;
    }
    if (dep.startsWith("hl7.fhir.uv.extensions")) {
      String v = dep.substring(dep.lastIndexOf("#") + 1);
      for (String s : loaded) {
        String v2 = s.substring(s.lastIndexOf("#") + 1);
        if (s.startsWith("hl7.fhir.uv.extensions.") && VersionUtilities.isSemVerWithWildcards(v) && VersionUtilities.isSemVer(v2, true) && VersionUtilities.versionMatches(v, v2)) {
          return true;
        }
      }
    }
    return false;
  }
}
