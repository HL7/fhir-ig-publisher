package org.hl7.fhir.igtools.publisher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Brings a folder up to date with a freshly built copy of it, touching only what differs. In watch mode the build
 * writes to a staging folder and this moves what is new or changed into the output folder and deletes what the
 * build no longer produced, so the output stays complete while a rebuild runs.
 *
 * <p>Each folder is listed once (on Windows the listing carries the sizes, so there is no call per file to find
 * them). A file whose size and content are the same is left alone, so its modification time doesn't change. A new
 * or changed file is moved from the staging folder, replacing the old one in one rename, so no one sees it half
 * written (across file systems, a copy next to it is renamed over it), and qa.json is moved last. Stale files are
 * deleted before anything is moved in, so a name that only changed case (one file on Windows and macOS) ends up
 * with the new case. A read-only file is made writable and tried again.
 *
 * <p>Files that are moved are no longer in the staging folder afterwards, unless the sync is asked to keep them (then
 * they are copied).
 */
public class OutputFolderSync {

  public static class Result {
    private int written;
    private int deleted;
    private int unchanged;
    private final List<String> failures = new ArrayList<>();

    /** new or changed files */
    public int getWritten() {
      return written;
    }

    /** stale files and folders */
    public int getDeleted() {
      return deleted;
    }

    public int getUnchanged() {
      return unchanged;
    }

    /** a message for each file or folder that couldn't be updated or deleted */
    public List<String> getFailures() {
      return Collections.unmodifiableList(failures);
    }
  }

  private static final int BUFFER = 64 * 1024;
  private static final String QA_JSON = "qa.json";

  private OutputFolderSync() {
  }

  public static Result sync(Path staged, Path target) throws IOException {
    return sync(staged, target, false);
  }

  /**
   * As sync(staged, target), but if keep is true the new and changed files are copied rather than moved, so that the
   * staging folder still has the whole output afterwards (a Jekyll kept running between builds only writes what
   * changed into it: see JekyllDriver)
   */
  public static Result sync(Path staged, Path target, boolean keep) throws IOException {
    Result result = new Result();
    // a folder that is a link (output linked into a web server's folder, say) is synced where it points
    staged = staged.toRealPath();
    boolean exists = Files.isDirectory(target);
    if (!exists) {
      Files.createDirectories(target);
    }
    target = target.toRealPath();
    if (staged.startsWith(target) || target.startsWith(staged)) {
      // everything in the target that isn't staged, the staging folder itself included, would be deleted
      throw new IOException("The folder " + target + " holds, or is inside, the staging folder " + staged);
    }
    Map<String, BasicFileAttributes> stagedFiles = new HashMap<>();
    Set<String> stagedDirs = new HashSet<>();
    // what can't be listed here would look stale, and be deleted from the target
    list(staged, stagedFiles, stagedDirs, null);

    Map<String, BasicFileAttributes> targetFiles = new HashMap<>();
    Set<String> targetDirs = new HashSet<>();
    if (exists) {
      // what can't be listed here is left alone
      list(target, targetFiles, targetDirs, result);
    }

    for (String rel : targetFiles.keySet()) {
      if (!stagedFiles.containsKey(rel)) {
        delete(target.resolve(rel), rel, result);
      }
    }
    // children sort after their parents, so in reverse order a folder is emptied before it is deleted
    TreeSet<String> staleDirs = new TreeSet<>(Collections.reverseOrder());
    for (String rel : targetDirs) {
      if (!stagedDirs.contains(rel)) {
        staleDirs.add(rel);
      }
    }
    for (String rel : staleDirs) {
      if (delete(target.resolve(rel), rel, result)) {
        targetDirs.remove(rel);
      }
    }

    for (String rel : stagedDirs) {
      if (!targetDirs.contains(rel)) {
        try {
          // an empty folder, or the parent of the files below
          Files.createDirectories(target.resolve(rel));
          targetDirs.add(rel);
        } catch (IOException ex) {
          result.failures.add("Unable to create " + rel + ": " + describe(ex));
        }
      }
    }
    byte[] ba = new byte[BUFFER];
    byte[] bb = new byte[BUFFER];
    List<Map.Entry<String, BasicFileAttributes>> files = new ArrayList<>(stagedFiles.size());
    Map.Entry<String, BasicFileAttributes> qa = null;
    for (Map.Entry<String, BasicFileAttributes> e : stagedFiles.entrySet()) {
      if (e.getKey().equals(QA_JSON)) {
        qa = e;
      } else {
        files.add(e);
      }
    }
    if (qa != null) {
      // last, so that what waits for it to change finds the pages in place
      files.add(qa);
    }
    for (Map.Entry<String, BasicFileAttributes> e : files) {
      String rel = e.getKey();
      Path from = staged.resolve(rel);
      Path to = target.resolve(rel);
      BasicFileAttributes old = targetFiles.get(rel);
      try {
        if (old != null && old.isRegularFile() && old.size() == e.getValue().size() && sameContent(from, to, ba, bb)) {
          result.unchanged++;
        } else {
          if (keep) {
            copy(from, to);
          } else {
            move(from, to);
          }
          result.written++;
        }
      } catch (IOException ex) {
        result.failures.add("Unable to update " + rel + ": " + describe(ex));
      }
    }
    return result;
  }

  /**
   * the files and folders under root, by their path relative to it. If result is given, an entry that can't be read is
   * reported there and left out, else it fails the listing
   */
  private static void list(Path root, Map<String, BasicFileAttributes> files, Set<String> dirs, Result result) throws IOException {
    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
        if (result == null || file.equals(root)) {
          throw e;
        }
        result.failures.add("Unable to read " + root.relativize(file) + ": " + describe(e));
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        if (!dir.equals(root)) {
          dirs.add(root.relativize(dir).toString());
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        files.put(root.relativize(file).toString(), attrs);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private static boolean sameContent(Path a, Path b, byte[] ba, byte[] bb) throws IOException {
    try (InputStream ia = Files.newInputStream(a); InputStream ib = Files.newInputStream(b)) {
      while (true) {
        int na = ia.readNBytes(ba, 0, BUFFER);
        int nb = ib.readNBytes(bb, 0, BUFFER);
        if (na != nb || !Arrays.equals(ba, 0, na, bb, 0, nb)) {
          return false;
        }
        if (na < BUFFER) {
          return true;
        }
      }
    }
  }

  /** replaces a file with another, in one rename where the file system allows it */
  static void move(Path from, Path to) throws IOException {
    try {
      rename(from, to);
    } catch (AccessDeniedException e) {
      // a read-only file can't be replaced on Windows
      if (!makeWritable(to)) {
        throw e;
      }
      rename(from, to);
    }
  }

  /** replaces a file with a copy of another, in one rename: the copy is made next to it first */
  static void copy(Path from, Path to) throws IOException {
    try {
      copyOver(from, to);
    } catch (AccessDeniedException e) {
      // a read-only file can't be replaced on Windows
      if (!makeWritable(to)) {
        throw e;
      }
      copyOver(from, to);
    }
  }

  private static void copyOver(Path from, Path to) throws IOException {
    // (a copy left by a sync that stopped is stale, and deleted by the next)
    Path copy = to.resolveSibling(".sync-" + to.getFileName());
    Files.copy(from, copy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    try {
      Files.move(copy, to, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException ex) {
      Files.deleteIfExists(copy);
      throw ex;
    }
  }

  private static void rename(Path from, Path to) throws IOException {
    try {
      // replaces the file in one call: a move that isn't atomic deletes it first
      Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      // the staging folder is on another file system, where a move deletes the file and then copies: copy next to
      // it and rename that over it instead (a copy left by a sync that stopped is stale, and deleted by the next)
      Path copy = to.resolveSibling(".sync-" + to.getFileName());
      Files.copy(from, copy, StandardCopyOption.REPLACE_EXISTING);
      try {
        Files.move(copy, to, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException ex) {
        Files.deleteIfExists(copy);
        throw ex;
      }
      try {
        Files.delete(from);
      } catch (IOException ex) {
        // the file is in place; the staging folder is emptied at the start of the next build
      }
    }
  }

  private static boolean delete(Path p, String rel, Result result) {
    try {
      try {
        Files.delete(p);
      } catch (AccessDeniedException e) {
        // a read-only file can't be deleted on Windows
        if (!makeWritable(p)) {
          throw e;
        }
        Files.delete(p);
      }
      result.deleted++;
      return true;
    } catch (IOException e) {
      result.failures.add("Unable to delete " + rel + ": " + describe(e));
      return false;
    }
  }

  private static boolean makeWritable(Path p) {
    File f = p.toFile();
    return f.exists() && !f.canWrite() && f.setWritable(true);
  }

  private static String describe(IOException e) {
    return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " " + e.getMessage());
  }
}
