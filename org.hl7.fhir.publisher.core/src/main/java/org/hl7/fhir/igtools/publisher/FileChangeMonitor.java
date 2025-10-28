package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Monitors files and directories for changes.
 * Automatically uses the best implementation for the current OS:
 * - macOS: Polling-based (fast, reliable)
 * - Other OS: WatchService-based (efficient)
 */
public class FileChangeMonitor {
  private final FileChangeMonitorImpl impl;

  public static class FileChangeEvent {
    private final Path path;
    private final ChangeType changeType;
    private final long timestamp;

    public enum ChangeType {
      CREATED, MODIFIED, DELETED
    }

    public FileChangeEvent(Path path, ChangeType changeType) {
      this.path = path;
      this.changeType = changeType;
      this.timestamp = System.currentTimeMillis();
    }

    public Path getPath() { return path; }
    public ChangeType getChangeType() { return changeType; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
      return String.format("[%s] %s: %s", changeType, path, new Date(timestamp));
    }
  }

  public static class MonitoredPath {
    private final Path path;
    private final boolean recursive;

    private MonitoredPath(Path path, boolean recursive) {
      this.path = path;
      this.recursive = recursive;
    }

    public static MonitoredPath recursive(Path path) {
      return new MonitoredPath(path, true);
    }

    public static MonitoredPath nonRecursive(Path path) {
      return new MonitoredPath(path, false);
    }

    public static MonitoredPath file(Path path) {
      return new MonitoredPath(path, false);
    }
  }

  public FileChangeMonitor(List<MonitoredPath> pathsToMonitor) throws IOException {
    if (isMacOS()) {
      System.out.println("Detected macOS - using polling-based file monitor");
      impl = new PollingFileChangeMonitorImpl(pathsToMonitor, 200);
    } else {
      System.out.println("Using WatchService-based file monitor");
      impl = new WatchServiceFileChangeMonitorImpl(pathsToMonitor);
    }
  }

  public FileChangeMonitor(Path... paths) throws IOException {
    this(Arrays.stream(paths)
            .map(MonitoredPath::recursive)
            .collect(Collectors.toList()));
  }

  private static boolean isMacOS() {
    String os = System.getProperty("os.name").toLowerCase();
    return os.contains("mac") || os.contains("darwin");
  }

  public void startMonitoring() {
    impl.startMonitoring();
  }

  public boolean hasChanges() {
    return impl.hasChanges();
  }

  public List<FileChangeEvent> getChanges() {
    return impl.getChanges();
  }

  public List<Path> getChangedFiles() {
    return impl.getChangedFiles();
  }

  public void printChanges() {
    impl.printChanges();
  }

  public void printChangesSummary() {
    impl.printChangesSummary();
  }

  public FileChangeEvent waitForChange(long timeout, TimeUnit unit) throws InterruptedException {
    return impl.waitForChange(timeout, unit);
  }

  public FileChangeEvent waitForChange() throws InterruptedException {
    return impl.waitForChange();
  }

  public void clearChanges() {
    impl.clearChanges();
  }

  public void stopMonitoring() {
    impl.stopMonitoring();
  }

  // Private interface for implementations
  private interface FileChangeMonitorImpl {
    void startMonitoring();
    boolean hasChanges();
    List<FileChangeEvent> getChanges();
    List<Path> getChangedFiles();
    void printChanges();
    void printChangesSummary();
    FileChangeEvent waitForChange(long timeout, TimeUnit unit) throws InterruptedException;
    FileChangeEvent waitForChange() throws InterruptedException;
    void clearChanges();
    void stopMonitoring();
  }

  // WatchService implementation (Linux, Windows)
  private static class WatchServiceFileChangeMonitorImpl implements FileChangeMonitorImpl {
    private final WatchService watchService;
    private final Map<WatchKey, Path> watchKeys;
    private final Set<Path> monitoredFiles;
    private ExecutorService executor;
    private final BlockingQueue<FileChangeEvent> changeQueue;
    private volatile boolean monitoring;

    public WatchServiceFileChangeMonitorImpl(List<MonitoredPath> pathsToMonitor) throws IOException {
      this.watchService = FileSystems.getDefault().newWatchService();
      this.watchKeys = new ConcurrentHashMap<>();
      this.monitoredFiles = ConcurrentHashMap.newKeySet();
      this.executor = Executors.newSingleThreadExecutor();
      this.changeQueue = new LinkedBlockingQueue<>();
      this.monitoring = false;

      for (MonitoredPath monitoredPath : pathsToMonitor) {
        Path path = monitoredPath.path;

        if (Files.isDirectory(path)) {
          if (monitoredPath.recursive) {
            registerRecursive(path);
          } else {
            registerDirectory(path);
          }
        } else if (Files.exists(path)) {
          monitoredFiles.add(path.toAbsolutePath());
          registerDirectory(path.getParent());
        }
      }
    }

    private void registerRecursive(Path directory) throws IOException {
      Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
          registerDirectory(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    }

    private void registerDirectory(Path directory) throws IOException {
      WatchKey key = directory.register(
              watchService,
              StandardWatchEventKinds.ENTRY_CREATE,
              StandardWatchEventKinds.ENTRY_MODIFY,
              StandardWatchEventKinds.ENTRY_DELETE
      );
      watchKeys.put(key, directory);
    }

    @Override
    public void startMonitoring() {
      if (monitoring) return;

      // If executor was shutdown, create a new one
      if (executor.isShutdown() || executor.isTerminated()) {
        executor = Executors.newSingleThreadExecutor();
      }
      monitoring = true;

      executor.submit(() -> {
        try {
          while (monitoring) {
            WatchKey key = watchService.poll(100, TimeUnit.MILLISECONDS);
            if (key == null) continue;

            Path directory = watchKeys.get(key);
            if (directory == null) continue;

            for (WatchEvent<?> event : key.pollEvents()) {
              WatchEvent.Kind<?> kind = event.kind();
              if (kind == StandardWatchEventKinds.OVERFLOW) continue;

              @SuppressWarnings("unchecked")
              WatchEvent<Path> ev = (WatchEvent<Path>) event;
              Path filename = ev.context();
              Path changedPath = directory.resolve(filename).toAbsolutePath();

              if (!monitoredFiles.isEmpty() && !monitoredFiles.contains(changedPath)) {
                continue;
              }

              FileChangeEvent.ChangeType changeType;
              if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                changeType = FileChangeEvent.ChangeType.CREATED;
              } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                changeType = FileChangeEvent.ChangeType.MODIFIED;
              } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                changeType = FileChangeEvent.ChangeType.DELETED;
              } else {
                continue;
              }

              changeQueue.offer(new FileChangeEvent(changedPath, changeType));
            }

            boolean valid = key.reset();
            if (!valid) {
              watchKeys.remove(key);
            }
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
    }

    @Override
    public boolean hasChanges() {
      return !changeQueue.isEmpty();
    }

    @Override
    public List<FileChangeEvent> getChanges() {
      List<FileChangeEvent> changes = new ArrayList<>();
      changeQueue.drainTo(changes);
      return changes;
    }

    @Override
    public List<Path> getChangedFiles() {
      return getChanges().stream()
              .map(FileChangeEvent::getPath)
              .distinct()
              .collect(Collectors.toList());
    }

    @Override
    public void printChanges() {
      List<FileChangeEvent> changes = getChanges();
      if (changes.isEmpty()) {
        System.out.println("No changes detected.");
        return;
      }

      System.out.println("=== File Changes Detected ===");
      for (FileChangeEvent change : changes) {
        System.out.println(change);
      }
      System.out.println("Total changes: " + changes.size());
    }

    @Override
    public void printChangesSummary() {
      List<FileChangeEvent> changes = getChanges();
      if (changes.isEmpty()) {
        System.out.println("No changes detected.");
        return;
      }

      Map<FileChangeEvent.ChangeType, List<FileChangeEvent>> grouped = changes.stream()
              .collect(Collectors.groupingBy(FileChangeEvent::getChangeType));

      System.out.println("=== File Changes Summary ===");

      if (grouped.containsKey(FileChangeEvent.ChangeType.CREATED)) {
        System.out.println("\nCreated (" + grouped.get(FileChangeEvent.ChangeType.CREATED).size() + "):");
        grouped.get(FileChangeEvent.ChangeType.CREATED).forEach(e ->
                System.out.println("  + " + e.getPath()));
      }

      if (grouped.containsKey(FileChangeEvent.ChangeType.MODIFIED)) {
        System.out.println("\nModified (" + grouped.get(FileChangeEvent.ChangeType.MODIFIED).size() + "):");
        grouped.get(FileChangeEvent.ChangeType.MODIFIED).forEach(e ->
                System.out.println("  ~ " + e.getPath()));
      }

      if (grouped.containsKey(FileChangeEvent.ChangeType.DELETED)) {
        System.out.println("\nDeleted (" + grouped.get(FileChangeEvent.ChangeType.DELETED).size() + "):");
        grouped.get(FileChangeEvent.ChangeType.DELETED).forEach(e ->
                System.out.println("  - " + e.getPath()));
      }

      System.out.println("\nTotal changes: " + changes.size());
    }

    @Override
    public FileChangeEvent waitForChange(long timeout, TimeUnit unit) throws InterruptedException {
      return changeQueue.poll(timeout, unit);
    }

    @Override
    public FileChangeEvent waitForChange() throws InterruptedException {
      return changeQueue.take();
    }

    @Override
    public void clearChanges() {
      changeQueue.clear();
    }

    @Override
    public void stopMonitoring() {
      monitoring = false;
      if (executor != null && !executor.isShutdown()) {
        executor.shutdown();
        try {
          if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
          }
          watchService.close();
        } catch (InterruptedException e) {
          executor.shutdownNow();
          Thread.currentThread().interrupt();
        } catch (IOException e) {
          System.err.println("Error closing watch service: " + e.getMessage());
        }
      }
    }
  }

  // Polling implementation (macOS)
  private static class PollingFileChangeMonitorImpl implements FileChangeMonitorImpl {
    private final Map<Path, FileSnapshot> fileSnapshots;
    private final List<MonitoredPath> monitoredPaths;
    private ScheduledExecutorService scheduler;
    private final BlockingQueue<FileChangeEvent> changeQueue;
    private volatile boolean monitoring;
    private final long pollIntervalMs;

    private static class FileSnapshot {
      FileTime lastModified;
      long size;
      boolean exists;

      FileSnapshot(Path path) {
        if (Files.exists(path)) {
          try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            this.lastModified = attrs.lastModifiedTime();
            this.size = attrs.size();
            this.exists = true;
          } catch (IOException e) {
            this.lastModified = null;
            this.size = 0;
            this.exists = false;
          }
        } else {
          this.lastModified = null;
          this.size = 0;
          this.exists = false;
        }
      }

      boolean hasChanged(FileSnapshot other) {
        if (this.exists != other.exists) return true;
        if (!this.exists) return false;
        return !this.lastModified.equals(other.lastModified) || this.size != other.size;
      }
    }

    public PollingFileChangeMonitorImpl(List<MonitoredPath> pathsToMonitor, long pollIntervalMs) {
      this.monitoredPaths = pathsToMonitor;
      this.pollIntervalMs = pollIntervalMs;
      this.fileSnapshots = new ConcurrentHashMap<>();
      this.changeQueue = new LinkedBlockingQueue<>();
      this.scheduler = Executors.newScheduledThreadPool(1);
      this.monitoring = false;

      scanAndSnapshot();
    }

    private void scanAndSnapshot() {
      Map<Path, FileSnapshot> newSnapshots = new ConcurrentHashMap<>();

      for (MonitoredPath monitoredPath : monitoredPaths) {
        try {
          if (Files.isDirectory(monitoredPath.path)) {
            if (monitoredPath.recursive) {
              scanDirectoryRecursive(monitoredPath.path, newSnapshots);
            } else {
              scanDirectoryNonRecursive(monitoredPath.path, newSnapshots);
            }
          } else {
            newSnapshots.put(monitoredPath.path, new FileSnapshot(monitoredPath.path));
          }
        } catch (IOException e) {
          System.err.println("Error scanning " + monitoredPath.path + ": " + e.getMessage());
        }
      }

      fileSnapshots.clear();
      fileSnapshots.putAll(newSnapshots);
    }

    private void scanDirectoryRecursive(Path directory, Map<Path, FileSnapshot> snapshots) throws IOException {
      Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
          snapshots.put(file, new FileSnapshot(file));
          return FileVisitResult.CONTINUE;
        }
      });
    }

    private void scanDirectoryNonRecursive(Path directory, Map<Path, FileSnapshot> snapshots) throws IOException {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
        for (Path file : stream) {
          if (Files.isRegularFile(file)) {
            snapshots.put(file, new FileSnapshot(file));
          }
        }
      }
    }

    @Override
    public void startMonitoring() {
      if (monitoring) return;

      // If scheduler was shutdown, create a new one
      if (scheduler.isShutdown() || scheduler.isTerminated()) {
        scheduler = Executors.newScheduledThreadPool(1);
      }

      monitoring = true;

      scheduler.scheduleAtFixedRate(() -> {
        try {
          checkForChanges();
        } catch (Exception e) {
          System.err.println("Error checking for changes: " + e.getMessage());
        }
      }, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void checkForChanges() {
      Map<Path, FileSnapshot> currentSnapshots = new ConcurrentHashMap<>();

      for (MonitoredPath monitoredPath : monitoredPaths) {
        try {
          if (Files.isDirectory(monitoredPath.path)) {
            if (monitoredPath.recursive) {
              scanDirectoryRecursive(monitoredPath.path, currentSnapshots);
            } else {
              scanDirectoryNonRecursive(monitoredPath.path, currentSnapshots);
            }
          } else {
            currentSnapshots.put(monitoredPath.path, new FileSnapshot(monitoredPath.path));
          }
        } catch (IOException e) {
          // Directory might have been deleted, continue
        }
      }

      for (Map.Entry<Path, FileSnapshot> entry : fileSnapshots.entrySet()) {
        Path path = entry.getKey();
        FileSnapshot oldSnapshot = entry.getValue();
        FileSnapshot newSnapshot = currentSnapshots.get(path);

        if (newSnapshot == null) {
          changeQueue.offer(new FileChangeEvent(path, FileChangeEvent.ChangeType.DELETED));
        } else if (oldSnapshot.hasChanged(newSnapshot)) {
          changeQueue.offer(new FileChangeEvent(path, FileChangeEvent.ChangeType.MODIFIED));
        }
      }

      for (Map.Entry<Path, FileSnapshot> entry : currentSnapshots.entrySet()) {
        Path path = entry.getKey();
        if (!fileSnapshots.containsKey(path) && entry.getValue().exists) {
          changeQueue.offer(new FileChangeEvent(path, FileChangeEvent.ChangeType.CREATED));
        }
      }

      fileSnapshots.clear();
      fileSnapshots.putAll(currentSnapshots);
    }

    @Override
    public boolean hasChanges() {
      return !changeQueue.isEmpty();
    }

    @Override
    public List<FileChangeEvent> getChanges() {
      List<FileChangeEvent> changes = new ArrayList<>();
      changeQueue.drainTo(changes);
      return changes;
    }

    @Override
    public List<Path> getChangedFiles() {
      return getChanges().stream()
              .map(FileChangeEvent::getPath)
              .distinct()
              .collect(Collectors.toList());
    }

    @Override
    public void printChanges() {
      List<FileChangeEvent> changes = getChanges();
      if (changes.isEmpty()) {
        System.out.println("No changes detected.");
        return;
      }

      System.out.println("=== File Changes Detected ===");
      for (FileChangeEvent change : changes) {
        System.out.println(change);
      }
      System.out.println("Total changes: " + changes.size());
    }

    @Override
    public void printChangesSummary() {
      List<FileChangeEvent> changes = getChanges();
      if (changes.isEmpty()) {
        System.out.println("No changes detected.");
        return;
      }

      Map<FileChangeEvent.ChangeType, List<FileChangeEvent>> grouped = changes.stream()
              .collect(Collectors.groupingBy(FileChangeEvent::getChangeType));

      System.out.println("=== File Changes Summary ===");

      if (grouped.containsKey(FileChangeEvent.ChangeType.CREATED)) {
        System.out.println("\nCreated (" + grouped.get(FileChangeEvent.ChangeType.CREATED).size() + "):");
        grouped.get(FileChangeEvent.ChangeType.CREATED).forEach(e ->
                System.out.println("  + " + e.getPath()));
      }

      if (grouped.containsKey(FileChangeEvent.ChangeType.MODIFIED)) {
        System.out.println("\nModified (" + grouped.get(FileChangeEvent.ChangeType.MODIFIED).size() + "):");
        grouped.get(FileChangeEvent.ChangeType.MODIFIED).forEach(e ->
                System.out.println("  ~ " + e.getPath()));
      }

      if (grouped.containsKey(FileChangeEvent.ChangeType.DELETED)) {
        System.out.println("\nDeleted (" + grouped.get(FileChangeEvent.ChangeType.DELETED).size() + "):");
        grouped.get(FileChangeEvent.ChangeType.DELETED).forEach(e ->
                System.out.println("  - " + e.getPath()));
      }

      System.out.println("\nTotal changes: " + changes.size());
    }

    @Override
    public FileChangeEvent waitForChange(long timeout, TimeUnit unit) throws InterruptedException {
      return changeQueue.poll(timeout, unit);
    }

    @Override
    public FileChangeEvent waitForChange() throws InterruptedException {
      return changeQueue.take();
    }

    @Override
    public void clearChanges() {
      changeQueue.clear();
    }

    @Override
    public void stopMonitoring() {
      monitoring = false;
      if (scheduler != null && !scheduler.isShutdown()) {
        scheduler.shutdown();
        try {
          if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
            scheduler.shutdownNow();
          }
        } catch (InterruptedException e) {
          scheduler.shutdownNow();
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  // Example usage
  public static void main(String[] args) throws IOException, InterruptedException {
    List<MonitoredPath> paths = Arrays.asList(
            MonitoredPath.recursive(Paths.get("src")),
            MonitoredPath.file(Paths.get("pom.xml"))
    );

    FileChangeMonitor monitor = new FileChangeMonitor(paths);
    monitor.startMonitoring();

    System.out.println("Monitoring for changes...");

    while (true) {
      if (monitor.hasChanges()) {
        monitor.printChanges();
      }
      Thread.sleep(100);
    }
  }
}
