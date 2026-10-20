package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The content of a set of files (single files, and directory trees) at one moment: for each file, its size, modified
 * time and a hash of its content.
 *
 * Watch mode compares the files as they were when a build read them with the files as they are now, so that it
 * rebuilds only when some content really changed (not for a save that changed nothing, or for files the build itself
 * rewrote with the same content), and so that it knows what changed.
 */
public class SourceSnapshot {

  private static class Entry {
    private final long size;
    private final long modified;
    private final byte[] hash;

    private Entry(long size, long modified, byte[] hash) {
      this.size = size;
      this.modified = modified;
      this.hash = hash;
    }
  }

  /** What changed between two snapshots: absolute paths of the files added, removed and changed */
  public static class Changes {
    private final Set<Path> added = new TreeSet<>();
    private final Set<Path> removed = new TreeSet<>();
    private final Set<Path> modified = new TreeSet<>();

    public Set<Path> getAdded() {
      return Collections.unmodifiableSet(added);
    }

    public Set<Path> getRemoved() {
      return Collections.unmodifiableSet(removed);
    }

    public Set<Path> getModified() {
      return Collections.unmodifiableSet(modified);
    }

    public boolean isEmpty() {
      return added.isEmpty() && removed.isEmpty() && modified.isEmpty();
    }

    /** every path that was added, removed or modified */
    public Set<Path> all() {
      Set<Path> res = new TreeSet<>(added);
      res.addAll(removed);
      res.addAll(modified);
      return res;
    }

    @Override
    public String toString() {
      StringBuilder b = new StringBuilder();
      for (Path p : added) {
        b.append("  + ").append(p).append("\n");
      }
      for (Path p : modified) {
        b.append("  ~ ").append(p).append("\n");
      }
      for (Path p : removed) {
        b.append("  - ").append(p).append("\n");
      }
      return b.toString();
    }
  }

  private final Map<Path, Entry> entries;

  private SourceSnapshot(Map<Path, Entry> entries) {
    this.entries = entries;
  }

  /**
   * Reads the current state of roots: each root is a file, which may not exist, or a directory, which is read with
   * everything below it. Files whose size and modified time are the same as in previous (which can be null) are not
   * read again.
   */
  public static SourceSnapshot take(Collection<Path> roots, SourceSnapshot previous) throws IOException {
    Map<Path, Entry> entries = new HashMap<>();
    for (Path root : roots) {
      Path r = root.toAbsolutePath();
      if (Files.isDirectory(r)) {
        Files.walkFileTree(r, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (attrs.isRegularFile()) {
              addEntry(entries, file, attrs, previous);
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            if (exc instanceof NoSuchFileException) {
              return FileVisitResult.CONTINUE; // deleted while we were reading the directory
            }
            throw exc;
          }
        });
      } else if (Files.isRegularFile(r)) {
        addEntry(entries, r, Files.readAttributes(r, BasicFileAttributes.class), previous);
      }
    }
    return new SourceSnapshot(entries);
  }

  private static void addEntry(Map<Path, Entry> entries, Path file, BasicFileAttributes attrs, SourceSnapshot previous) throws IOException {
    long size = attrs.size();
    long modified = attrs.lastModifiedTime().toMillis();
    Entry old = previous == null ? null : previous.entries.get(file);
    if (old != null && old.size == size && old.modified == modified) {
      entries.put(file, old);
    } else {
      try {
        entries.put(file, new Entry(size, modified, hash(file)));
      } catch (NoSuchFileException e) {
        // deleted since the directory was read: it's not there
      }
    }
  }

  private static byte[] hash(Path file) throws IOException {
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IOException(e);
    }
    byte[] buffer = new byte[65536];
    try (InputStream in = Files.newInputStream(file)) {
      int n;
      while ((n = in.read(buffer)) > 0) {
        md.update(buffer, 0, n);
      }
    }
    return md.digest();
  }

  /**
   * Returns a copy of this snapshot in which the files under root (a file or a directory) are as they are in other
   */
  public SourceSnapshot withTree(Path root, SourceSnapshot other) {
    Path r = root.toAbsolutePath();
    Map<Path, Entry> res = new HashMap<>();
    for (Map.Entry<Path, Entry> e : entries.entrySet()) {
      if (!e.getKey().startsWith(r)) {
        res.put(e.getKey(), e.getValue());
      }
    }
    for (Map.Entry<Path, Entry> e : other.entries.entrySet()) {
      if (e.getKey().startsWith(r)) {
        res.put(e.getKey(), e.getValue());
      }
    }
    return new SourceSnapshot(res);
  }

  /** The files that differ in content between before and this snapshot */
  public Changes changesSince(SourceSnapshot before) {
    Changes res = new Changes();
    for (Map.Entry<Path, Entry> e : entries.entrySet()) {
      Entry old = before.entries.get(e.getKey());
      if (old == null) {
        res.added.add(e.getKey());
      } else if (old.size != e.getValue().size || !Arrays.equals(old.hash, e.getValue().hash)) {
        res.modified.add(e.getKey());
      }
    }
    for (Path p : before.entries.keySet()) {
      if (!entries.containsKey(p)) {
        res.removed.add(p);
      }
    }
    return res;
  }

  /** A hash of the names and content of all the files in this snapshot */
  public byte[] digest() throws IOException {
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IOException(e);
    }
    for (Path p : new TreeSet<>(entries.keySet())) {
      md.update(p.toString().getBytes(StandardCharsets.UTF_8));
      md.update((byte) 0);
      md.update(entries.get(p).hash);
    }
    return md.digest();
  }

  /** The files (absolute paths) in this snapshot */
  public Set<Path> getFiles() {
    return Collections.unmodifiableSet(entries.keySet());
  }
}
