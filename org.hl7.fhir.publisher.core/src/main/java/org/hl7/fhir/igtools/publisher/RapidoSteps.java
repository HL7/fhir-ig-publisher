package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;

/**
 * Rapido: the steps of the generate phase that make fragments for the IG as a whole (the lists of its resources), and
 * are the same in every build until what they show changes. Each step has a key, a hash of everything it reads; when
 * the key is the one the last build's step had, and the files that step wrote are as it left them, the step isn't run:
 * what it did besides writing those files (see Fragment) is done from its record. Anything else, and the step runs.
 *
 * <p>The records are part of Rapido's state (see RapidoTracker.save()), so they are only kept when the build succeeds,
 * and only reused by a differential build: there, the publisher, the IG resource, the template and the files that
 * aren't resources are what they were, so a key only has to cover what the step reads of the resources.
 */
public class RapidoSteps {

  /** What one fragment a step made did to the build, besides the files it wrote */
  public static class Fragment {
    public final String name;
    public final String lang; // null: no language
    public final String code;
    public final String context;
    public final long time;
    public final long size;
    /** the fragment's file as the step left it (see stamp()) */
    public final String stamp;
    /** the copy without the language in its name, as the step left it; null if the fragment has no such copy */
    public final String copyStamp;

    public Fragment(String name, String lang, String code, String context, long time, long size, String stamp, String copyStamp) {
      this.name = name;
      this.lang = lang;
      this.code = code;
      this.context = context;
      this.time = time;
      this.size = size;
      this.stamp = stamp;
      this.copyStamp = copyStamp;
    }
  }

  private JsonObject old; // the last build's steps by name; null: none can be reused
  private final JsonObject now = new JsonObject();
  private String recordingName;
  private JsonObject recording;
  private int reused;
  private int run;

  /** steps: what save() returned in the build whose state this one reuses; null if it reuses none */
  public void load(JsonObject steps) {
    old = steps;
  }

  /** the steps of this build, for the next one */
  public JsonObject save() {
    return now;
  }

  /** What the step did in the last build, if its key was this one and its record can be read; else null */
  public List<Fragment> reusable(String name, String key) {
    JsonObject step = old == null || key == null ? null : old.getJsonObject(name);
    if (step == null || !key.equals(step.asString("key")) || !step.has("fragments")) {
      return null;
    }
    List<Fragment> res = new ArrayList<>();
    for (JsonElement e : step.getJsonArray("fragments").getItems()) {
      if (!(e instanceof JsonArray) || ((JsonArray) e).size() != 8) {
        return null;
      }
      List<String> a = ((JsonArray) e).asStrings();
      try {
        res.add(new Fragment(a.get(0), text(a.get(1)), a.get(2), a.get(3), Long.parseLong(a.get(4)), Long.parseLong(a.get(5)), a.get(6), text(a.get(7))));
      } catch (NumberFormatException ex) {
        return null;
      }
    }
    return res;
  }

  /** The step wasn't run: its record is the last build's */
  public void keep(String name) {
    now.set(name, old.getJsonObject(name));
    reused++;
  }

  /** From here to finish(), what the step does is recorded */
  public void start(String name, String key) {
    recordingName = name;
    recording = new JsonObject();
    recording.add("key", key);
    recording.forceArray("fragments");
    run++;
  }

  public boolean isRecording() {
    return recording != null;
  }

  public void record(Fragment f) {
    if (recording != null) {
      JsonArray a = new JsonArray();
      a.add(f.name);
      a.add(f.lang == null ? "" : f.lang);
      a.add(f.code);
      a.add(f.context);
      a.add(Long.toString(f.time));
      a.add(Long.toString(f.size));
      a.add(f.stamp);
      a.add(f.copyStamp == null ? "" : f.copyStamp);
      recording.getJsonArray("fragments").add(a);
    }
  }

  /** The step did something a record doesn't hold, or failed: the next build runs it */
  public void abandon() {
    recordingName = null;
    recording = null;
  }

  public void finish() {
    if (recording != null) {
      now.set(recordingName, recording);
    }
    abandon();
  }

  public int getReused() {
    return reused;
  }

  public int getRun() {
    return run;
  }

  private static String text(String s) {
    return s.isEmpty() ? null : s;
  }

  /**
   * The size of the file and when it was last written, which tell whether it's still what a step left; a value that
   * equals no other if it isn't there or can't be read
   */
  public static String stamp(String path) {
    try {
      BasicFileAttributes a = Files.readAttributes(Paths.get(path), BasicFileAttributes.class);
      if (!a.isRegularFile()) {
        return "not a file: "+System.nanoTime();
      }
      return a.size()+"@"+a.lastModifiedTime().toInstant();
    } catch (IOException | RuntimeException e) {
      return "unreadable: "+System.nanoTime();
    }
  }
}
