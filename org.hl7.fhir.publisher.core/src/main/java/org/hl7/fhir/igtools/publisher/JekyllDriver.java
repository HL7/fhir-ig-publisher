package org.hl7.fhir.igtools.publisher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.commons.lang3.SystemUtils;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.settings.FhirSettings;

/**
 * Watch mode: a Jekyll that keeps running between the builds of a watch session (see jekyll-driver.rb), so that when
 * only some pages changed, it renders those pages (and the pages that include what changed) instead of the whole site,
 * and the steps after Jekyll know which output files are new.
 *
 * <p>It runs the Jekyll that `jekyll build` would run: the Ruby that the jekyll command on the path (after the
 * configured Ruby path) starts. It isn't used on Windows, when the IG asks for another Jekyll command, or when the
 * system property {@value #DISABLE_PROPERTY} is "false"; and it gives up (so the build runs `jekyll build` as before)
 * when it can't load Jekyll or the site has a Gemfile.
 */
public class JekyllDriver {

  public static final String DISABLE_PROPERTY = "ig.jekyll.driver";
  private static final String MARK = "@@ig-jekyll-driver@@ ";
  private static final String EOF = "\u0000eof";

  /** What a command did */
  public static class Result {
    private final boolean ok;
    private final boolean full;
    private final String reason;
    private final String error;
    private final List<String> written;
    private final List<String> unknown;

    private Result(Result r, List<String> written) {
      ok = r.ok;
      full = r.full;
      reason = r.reason;
      error = r.error;
      this.written = written;
      unknown = r.unknown;
    }

    /** This result, with these files written */
    Result withWritten(List<String> written) {
      return new Result(this, written);
    }

    Result(JsonObject json) {
      ok = json.asBoolean("ok");
      full = json.has("full") && json.asBoolean("full");
      reason = json.has("reason") && json.get("reason").isJsonPrimitive() ? json.asString("reason") : null;
      error = json.has("error") ? json.asString("error") : null;
      written = list(json, "written");
      unknown = list(json, "unknown");
    }

    private static List<String> list(JsonObject json, String name) {
      if (!json.has(name) || !json.get(name).isJsonArray()) {
        return null;
      }
      List<String> res = new ArrayList<>();
      for (JsonElement e : json.getJsonArray(name)) {
        res.add(e.asJsonPrimitive().asString());
      }
      return res;
    }

    public boolean isOk() {
      return ok;
    }

    /** Whether the whole site was built (see isAllWritten() for whether every output file is new) */
    public boolean isFull() {
      return full;
    }

    /**
     * Whether every output file is to be taken as new: the whole site was built, and Jekyll can't tell which files it
     * wrote (otherwise getWritten() lists them, and the others are as they were)
     */
    public boolean isAllWritten() {
      return full && written == null;
    }

    /** Why an update built the whole site, if it did */
    public String getReason() {
      return reason;
    }

    public String getError() {
      return error;
    }

    /** The output files written (relative to the output folder), unless isAllWritten() */
    public List<String> getWritten() {
      return written == null ? Collections.emptyList() : written;
    }

    /** For a rewrite: the files asked for that Jekyll doesn't write */
    public List<String> getUnknown() {
      return unknown == null ? Collections.emptyList() : unknown;
    }
  }

  private final List<String> command;
  private final Map<String, String> env;
  private volatile Process process;
  private boolean hooked;
  private OutputStream stdin;
  private BlockingQueue<String> lines;
  private String source;
  private String destination;
  private String unavailable;
  private Path script;
  // the output files as Jekyll last wrote them: path (relative to the destination) => stamp (see stamp())
  private Map<String, String> stamps;

  JekyllDriver(List<String> command, Map<String, String> env) {
    this.command = command;
    this.env = env;
  }

  /**
   * A driver for this Jekyll command, or null if it can't be used for it (see class doc). Nothing is started until the
   * first command
   */
  public static JekyllDriver forCommand(String jekyllCommand) {
    if (SystemUtils.IS_OS_WINDOWS || !"jekyll".equals(jekyllCommand) || "false".equals(System.getProperty(DISABLE_PROPERTY))) {
      return null;
    }
    Map<String, String> env = new java.util.HashMap<>(System.getenv());
    if (FhirSettings.hasRubyPath()) {
      env.put("PATH", FhirSettings.getRubyPath() + File.pathSeparator + env.getOrDefault("PATH", ""));
      if (FhirSettings.getGemPath() != null) {
        env.put("GEM_PATH", FhirSettings.getGemPath());
      }
    }
    return new JekyllDriver(rubyFor(env.get("PATH")), env);
  }

  /**
   * How to start the Ruby that runs the jekyll command found on this path: the interpreter its #! line names (for a
   * RubyGems binstub), otherwise the ruby on the path
   */
  static List<String> rubyFor(String path) {
    List<String> res = new ArrayList<>();
    for (String dir : path == null ? new String[0] : path.split(File.pathSeparator)) {
      File f = new File(dir.isEmpty() ? "." : dir, "jekyll");
      if (f.isFile() && f.canExecute()) {
        String line = firstLine(f);
        if (line != null && line.startsWith("#!") && line.contains("ruby")) {
          String[] parts = line.substring(2).trim().split("\\s+");
          int i = 0;
          if (parts[0].endsWith("/env")) {
            i = 1;
            while (i < parts.length && parts[i].startsWith("-")) {
              i++;
            }
          }
          for (; i < parts.length; i++) {
            res.add(parts[i]);
          }
          if (!res.isEmpty()) {
            return res;
          }
        }
        break;
      }
    }
    res.add("ruby");
    return res;
  }

  private static String firstLine(File f) {
    try (BufferedReader r = Files.newBufferedReader(f.toPath(), StandardCharsets.ISO_8859_1)) {
      return r.readLine();
    } catch (IOException e) {
      return null;
    }
  }

  /** Why the driver can't be used, once it has found that it can't (then the build runs `jekyll build`) */
  public String getUnavailable() {
    return unavailable;
  }

  /** Stops the driver and makes it unavailable from now on, for this reason */
  public synchronized void disable(String reason) {
    stop();
    unavailable = reason;
  }

  /** Builds the whole site from source into destination */
  public Result build(String source, String destination, long timeout, Consumer<String> log) throws IOException {
    return stamped(destination, send(source, destination, "{\"cmd\":\"build\"}", timeout, log));
  }

  /**
   * Brings the output up to date after the files changed (relative to source) changed: either by rendering what
   * depends on them, or (as the result says) by building the whole site. When what Jekyll last wrote is known (see
   * isAsWritten()), an output file that would get the same content again is left as it is, so it isn't among the
   * files written, even when the whole site is built
   */
  public Result update(String source, String destination, Collection<String> changed, long timeout, Consumer<String> log) throws IOException {
    JsonObject cmd = new JsonObject();
    cmd.add("cmd", "update");
    JsonArray a = new JsonArray();
    for (String s : changed) {
      a.add(s);
    }
    cmd.add("changed", a);
    // the stamps tell what has changed an output file since Jekyll wrote it, so it can be left as it is
    boolean keep = stamps != null && isRunningFor(source, destination);
    cmd.add("keep", keep);
    Result r = send(source, destination, JsonParser.compose(cmd, false), timeout, log);
    if (!keep && r.isOk() && r.isFull() && r.written != null) {
      // Jekyll can only have left files as they were when asked to: take them all as new
      r = r.withWritten(null);
    }
    return stamped(destination, r);
  }

  /** Writes these output files (relative to destination) again as the last build or update wrote them */
  public Result rewrite(String source, String destination, Collection<String> files, long timeout, Consumer<String> log) throws IOException {
    JsonObject cmd = new JsonObject();
    cmd.add("cmd", "rewrite");
    JsonArray a = new JsonArray();
    for (String s : files) {
      a.add(s);
    }
    cmd.add("files", a);
    return stamped(destination, send(source, destination, JsonParser.compose(cmd, false), timeout, log));
  }

  /**
   * The output files (relative to destination) that have changed since Jekyll wrote them, or that Jekyll didn't write,
   * sorted: writing them again (see rewrite()) leaves the site's files as `jekyll build` would
   */
  public List<String> changedSinceWritten(String source, String destination) throws IOException {
    if (stamps == null || !isRunningFor(source, destination)) {
      throw new IOException("There is no Jekyll build to go back to");
    }
    List<String> changed = new ArrayList<>();
    for (Map.Entry<String, String> e : stampTree(destination).entrySet()) {
      String was = stamps.get(e.getKey());
      if (was == null || e.getValue() == null || !was.equals(e.getValue())) {
        changed.add(e.getKey());
      }
    }
    Collections.sort(changed);
    return changed;
  }

  /** Whether this output file (relative to destination) is as Jekyll last wrote it */
  public boolean isAsWritten(String destination, String file) {
    String was = stamps == null || !destination.equals(this.destination) ? null : stamps.get(file);
    return was != null && was.equals(stamp(Paths.get(destination, file)));
  }

  /** Keeps the stamps of the files a command wrote */
  private Result stamped(String destination, Result r) throws IOException {
    if (!r.isOk()) {
      stamps = null;
    } else if (r.isAllWritten()) {
      stamps = stampTree(destination);
    } else if (stamps != null) {
      for (String f : r.getWritten()) {
        stamps.put(f, stamp(Paths.get(destination, f)));
      }
      // what Jekyll removed
      stamps.keySet().removeIf(f -> !Files.exists(Paths.get(destination, f)));
    }
    return r;
  }

  /** The stamps of the files under this folder, by path relative to it */
  static Map<String, String> stampTree(String folder) throws IOException {
    Map<String, String> res = new HashMap<>();
    Path root = Paths.get(folder);
    if (!Files.isDirectory(root)) {
      return res;
    }
    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        res.put(root.relativize(file).toString(), stamp(file));
        return FileVisitResult.CONTINUE;
      }
    });
    return res;
  }

  /**
   * What changes when anything writes to a file, or replaces it: its size, inode and inode change time (null if they
   * can't be read: then the file counts as changed)
   */
  static String stamp(Path p) {
    try {
      Map<String, Object> a = Files.readAttributes(p, "unix:size,ino,ctime", java.nio.file.LinkOption.NOFOLLOW_LINKS);
      Object ctime = a.get("ctime");
      if (ctime == null || a.get("ino") == null) {
        return null;
      }
      return a.get("size") + ":" + a.get("ino") + ":" + ((java.nio.file.attribute.FileTime) ctime).to(TimeUnit.NANOSECONDS);
    } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
      return null;
    }
  }

  /** Whether the running Jekyll has built this site (so that an update can go on from it) */
  public boolean isRunningFor(String source, String destination) {
    return process != null && process.isAlive() && source.equals(this.source) && destination.equals(this.destination);
  }

  private synchronized Result send(String source, String destination, String cmd, long timeout, Consumer<String> log) throws IOException {
    if (unavailable != null) {
      throw new IOException(unavailable);
    }
    if (!isRunningFor(source, destination)) {
      stop();
      start(source, destination);
    }
    try {
      stdin.write((cmd + "\n").getBytes(StandardCharsets.UTF_8));
      stdin.flush();
    } catch (IOException e) {
      stop();
      throw new IOException("Jekyll stopped running: " + e.getMessage(), e);
    }
    long end = System.currentTimeMillis() + timeout;
    while (true) {
      String line;
      try {
        long wait = end - System.currentTimeMillis();
        line = wait <= 0 ? null : lines.poll(wait, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        stop();
        throw new IOException("Interrupted while Jekyll was running");
      }
      if (line == null) {
        stop();
        throw new IOException("Jekyll timeout exceeded: " + (timeout / 1000) + " seconds");
      }
      if (EOF.equals(line)) {
        stop();
        throw new IOException("Jekyll stopped running");
      }
      if (line.startsWith(MARK)) {
        JsonObject json = JsonParser.parseObject(line.substring(MARK.length()));
        if (json.has("unavailable")) {
          unavailable = json.asString("unavailable");
          stop();
          throw new IOException(unavailable);
        }
        return new Result(json);
      }
      log.accept(line);
    }
  }

  private void start(String source, String destination) throws IOException {
    if (script == null) {
      script = Files.createTempFile("ig-jekyll-driver", ".rb");
      script.toFile().deleteOnExit();
      try (InputStream is = JekyllDriver.class.getResourceAsStream("/jekyll/jekyll-driver.rb")) {
        if (is == null) {
          throw new IOException("jekyll-driver.rb is missing");
        }
        Files.copy(is, script, StandardCopyOption.REPLACE_EXISTING);
      }
    }
    List<String> cmd = new ArrayList<>(command);
    cmd.add(script.toString());
    cmd.add(destination);
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.directory(new File(source));
    pb.redirectErrorStream(true);
    pb.environment().clear();
    pb.environment().putAll(env);
    try {
      process = pb.start();
    } catch (IOException e) {
      unavailable = "Ruby could not be started (" + String.join(" ", command) + "): " + e.getMessage();
      throw new IOException(unavailable, e);
    }
    Process p = process;
    if (!hooked) {
      // whichever process is running when the JVM exits
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        Process running = process;
        if (running != null) {
          running.destroyForcibly();
        }
      }, "jekyll-driver-stop"));
      hooked = true;
    }
    stdin = process.getOutputStream();
    BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    lines = queue;
    Thread reader = new Thread(() -> {
      try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = r.readLine()) != null) {
          queue.add(line);
        }
      } catch (IOException e) {
        // the process has gone: the EOF below says so
      }
      queue.add(EOF);
    }, "jekyll-driver-output");
    reader.setDaemon(true);
    reader.start();
    this.source = source;
    this.destination = destination;
  }

  /** Stops the Jekyll process, if it's running */
  public synchronized void stop() {
    if (process != null) {
      try {
        stdin.close();
      } catch (IOException e) {
        // it's being stopped anyway
      }
      process.destroy();
      try {
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
          process.destroyForcibly();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        process.destroyForcibly();
      }
      process = null;
      source = null;
      destination = null;
    }
    stamps = null;
  }
}
