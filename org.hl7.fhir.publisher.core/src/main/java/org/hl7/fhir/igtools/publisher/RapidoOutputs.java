package org.hl7.fhir.igtools.publisher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.parser.JsonParser;

/**
 * -rapido: the files each source file put in the temp folder when it was last generated, kept from one build to the
 * next (in a file next to the build tracker).
 *
 * <p>Once the output has been generated, the temp folder is cleared of what the build didn't write, so that what a
 * removed or renamed source wrote isn't published again. This is how what a removed file, or an earlier version of a
 * changed one, wrote in the folders of the temp folder is known (cleanOutput only clears the top of it). A Rapido
 * build takes every file through generation: for a file whose outputs it reuses, they are what the file wrote in the
 * last build (see RapidoTracker), so a differential build is only made when all that this records is still there. A
 * build that generates only some of the files keeps what the others wrote before.
 */
public class RapidoOutputs {

  /** What a differential build keeps in the temp folder, and what it removes, as paths in it */
  public static class Change {
    private final Set<String> kept = new HashSet<>();
    private final Set<String> gone = new HashSet<>();

    /** what the files that weren't generated wrote before */
    public Set<String> getKept() {
      return kept;
    }

    /** what an earlier build wrote that no file writes now */
    public Set<String> getGone() {
      return gone;
    }
  }

  private final File record;
  private final String tempDir;

  public RapidoOutputs(String vsCache, String tempDir) throws IOException {
    this.record = new File(Utilities.path(vsCache, ".build-outputs.json"));
    this.tempDir = tempDir;
  }

  /**
   * Records what each file wrote in the temp folder: in this build, for the files it generated, and as recorded in an
   * earlier build for the others. Returns what is kept and what is gone since, or null if what the files that weren't
   * generated wrote isn't known (then nothing can be removed)
   */
  public Change update(List<FetchedFile> fileList, Collection<FetchedFile> changeList) throws IOException {
    Set<FetchedFile> generated = new HashSet<>(changeList);
    // what was written before is needed for the files that weren't generated, and to know what is gone since
    JsonObject before = read();
    if (before == null && (generated.size() < fileList.size() || !generated.containsAll(fileList))) {
      // a differential build after a build that didn't record its outputs: the next complete build does
      record.delete();
      return null;
    }
    Change change = new Change();
    Set<String> written = new HashSet<>();
    JsonObject now = new JsonObject();
    for (FetchedFile f : fileList) {
      JsonArray names = new JsonArray();
      if (generated.contains(f)) {
        for (String name : f.getOutputNames()) {
          if (name.startsWith(tempDir + File.separator) && name.length() > tempDir.length() + 1) { // not the temp folder itself
            names.add(name.substring(tempDir.length() + 1));
            written.add(name);
          }
        }
      } else {
        JsonArray was = before.getJsonArray(f.getLoadPath());
        if (was == null) {
          // a file that wasn't there when the outputs were last recorded, but wasn't generated: not known
          record.delete();
          return null;
        }
        for (String name : was.asStrings()) {
          names.add(name);
          change.kept.add(Utilities.path(tempDir, name));
        }
      }
      now.set(f.getLoadPath(), names);
    }
    if (before != null) {
      written.addAll(change.kept);
      // a file renamed in case only is the same file on macOS and Windows: it is still written
      Set<String> writtenAnyCase = new HashSet<>();
      for (String w : written) {
        writtenAnyCase.add(w.toLowerCase(Locale.ROOT));
      }
      for (JsonProperty p : before.getProperties()) {
        if (p.getValue() instanceof JsonArray) {
          for (String name : ((JsonArray) p.getValue()).asStrings()) {
            String path = Utilities.path(tempDir, name);
            if (!writtenAnyCase.contains(path.toLowerCase(Locale.ROOT))) {
              change.gone.add(path);
            }
          }
        }
      }
    }
    FileUtilities.stringToFile(JsonParser.compose(now, false), record);
    return change;
  }

  /**
   * The temp folder was cleared, so the record no longer says what is in it: everything there now is this build's.
   * Kept, the next build of every file would report as gone what the files of an earlier build wrote that this build
   * writes another way (the diagrams a template script writes into _includes, say), and they would be removed before
   * Jekyll runs.
   */
  public void forget() throws IOException {
    Files.deleteIfExists(record.toPath());
  }

  /**
   * whether there is a record of what the files wrote, and all of it is still in the temp folder (a differential build
   * needs the record to clear the temp folder, and the files of the unchanged sources, which it doesn't write again)
   */
  public boolean isComplete() throws IOException {
    JsonObject recorded = read();
    if (recorded == null) {
      return false;
    }
    for (JsonProperty p : recorded.getProperties()) {
      if (!(p.getValue() instanceof JsonArray)) {
        return false;
      }
      for (String name : ((JsonArray) p.getValue()).asStrings()) {
        if (!new File(Utilities.path(tempDir, name)).exists()) {
          return false;
        }
      }
    }
    return true;
  }

  private JsonObject read() {
    if (!record.exists()) {
      return null;
    }
    try {
      JsonElement e = JsonParser.parse(record);
      return e instanceof JsonObject ? (JsonObject) e : null;
    } catch (Exception e) {
      return null;
    }
  }
}
