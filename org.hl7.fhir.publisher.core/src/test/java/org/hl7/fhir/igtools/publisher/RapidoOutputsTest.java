package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.utilities.Utilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RapidoOutputsTest {

  @TempDir
  Path dir;

  @Test
  void aDifferentialBuildKeepsWhatTheUnchangedFilesWroteAndRemovesWhatNoFileWritesNow() throws Exception {
    String temp = dir.resolve("temp").toString();
    String cache = dir.resolve("cache").toString();
    Files.createDirectories(dir.resolve("cache"));
    FetchedFile config = file("template/config/_config.yml", temp, "_config.yml");
    FetchedFile page = file("input/pagecontent/a.md", temp, "a.md", "en/a.md");
    FetchedFile patient = file("Patient-p.json", temp, "Patient-p.html", "en/Patient-p.html");
    FetchedFile removed = file("Patient-gone.json", temp, "Patient-gone.html", "en/Patient-gone.html");
    assertEquals(Set.of(), new RapidoOutputs(cache, temp).update(List.of(config, page, patient, removed), List.of(config, page, patient, removed)).getKept());

    // the next build: the page changed, the patient changed its id, a file was removed, and the config is the same
    FetchedFile config2 = file("template/config/_config.yml", temp);
    FetchedFile page2 = file("input/pagecontent/a.md", temp, "a.md", "en/a.md");
    FetchedFile patient2 = file("Patient-p.json", temp, "Patient-q.html", "en/Patient-q.html");
    RapidoOutputs.Change change = new RapidoOutputs(cache, temp).update(List.of(config2, page2, patient2), List.of(page2, patient2));

    assertEquals(Set.of(p(temp, "_config.yml")), change.getKept());
    assertEquals(Set.of(p(temp, "Patient-p.html"), p(temp, "en/Patient-p.html"), p(temp, "Patient-gone.html"), p(temp, "en/Patient-gone.html")), change.getGone());

    // and what the config wrote is still known in the build after that
    FetchedFile config3 = file("template/config/_config.yml", temp);
    FetchedFile page3 = file("input/pagecontent/a.md", temp, "a.md");
    FetchedFile patient3 = file("Patient-p.json", temp);
    change = new RapidoOutputs(cache, temp).update(List.of(config3, page3, patient3), List.of(page3));
    assertEquals(Set.of(p(temp, "_config.yml"), p(temp, "Patient-q.html"), p(temp, "en/Patient-q.html")), change.getKept());
    assertEquals(Set.of(p(temp, "en/a.md")), change.getGone());
  }

  /**
   * A Rapido build takes every file through generation (reusing what it can), so the files generated are all of
   * them: what an earlier build wrote that no file writes now is still gone, in the folders of the temp folder too
   */
  @Test
  void aBuildOfEveryFileRemovesWhatNoFileWritesNow() throws Exception {
    String temp = dir.resolve("temp").toString();
    String cache = dir.resolve("cache").toString();
    Files.createDirectories(dir.resolve("cache"));
    FetchedFile patient = file("Patient-p.json", temp, "Patient-p.html", "en/Patient-p.html");
    FetchedFile other = file("Patient-o.json", temp, "Patient-o.html");
    new RapidoOutputs(cache, temp).update(List.of(patient, other), List.of(patient, other));

    // the patient's id changed
    FetchedFile patient2 = file("Patient-p.json", temp, "Patient-q.html", "en/Patient-q.html");
    FetchedFile other2 = file("Patient-o.json", temp, "Patient-o.html");
    RapidoOutputs.Change change = new RapidoOutputs(cache, temp).update(List.of(patient2, other2), List.of(patient2, other2));

    assertEquals(Set.of(), change.getKept());
    assertEquals(Set.of(p(temp, "Patient-p.html"), p(temp, "en/Patient-p.html")), change.getGone());
  }

  /**
   * Once the temp folder is cleared, what is in it is this build's, whoever wrote it (a template script writes into
   * _includes what were pages in the last build, say): nothing is gone, and what this build wrote is recorded
   */
  @Test
  void afterTheTempFolderIsClearedNothingIsGone() throws Exception {
    String temp = dir.resolve("temp").toString();
    String cache = dir.resolve("cache").toString();
    Files.createDirectories(dir.resolve("cache"));
    FetchedFile page = file("a.md", temp, "a.md");
    FetchedFile diagram = file("d.svg", temp, "_includes/d.svg", "_includes/en/d.svg");
    new RapidoOutputs(cache, temp).update(List.of(page, diagram), List.of(page, diagram));

    new RapidoOutputs(cache, temp).forget();
    FetchedFile page2 = file("a.md", temp, "a.md");
    RapidoOutputs.Change change = new RapidoOutputs(cache, temp).update(List.of(page2), List.of(page2));

    assertEquals(Set.of(), change.getGone());
    assertEquals(Set.of(), change.getKept());
    assertTrue(Files.exists(dir.resolve("cache").resolve(".build-outputs.json")), "recorded for the next build");
  }

  @Test
  void aFileRenamedInCaseOnlyIsStillWritten() throws Exception {
    // on macOS and Windows, removing the old name would remove the file just written
    String temp = dir.resolve("temp").toString();
    String cache = dir.resolve("cache").toString();
    Files.createDirectories(dir.resolve("cache"));
    FetchedFile page = file("Background.md", temp, "Background.md");
    FetchedFile other = file("other.md", temp, "other.md");
    new RapidoOutputs(cache, temp).update(List.of(page, other), List.of(page, other));

    FetchedFile renamed = file("background.md", temp, "background.md");
    FetchedFile other2 = file("other.md", temp);
    assertEquals(Set.of(), new RapidoOutputs(cache, temp).update(List.of(renamed, other2), List.of(renamed)).getGone());
  }

  @Test
  void theTempFolderItselfIsntAnOutput() throws Exception {
    // a template folder copied to the root of the temp folder
    String temp = dir.resolve("temp").toString();
    String cache = dir.resolve("cache").toString();
    Files.createDirectories(dir.resolve("cache"));
    FetchedFile folder = new FetchedFile("config");
    folder.getOutputNames().add(temp + File.separator);
    FetchedFile page = file("a.md", temp, "a.md");
    new RapidoOutputs(cache, temp).update(List.of(folder, page), List.of(folder, page));

    FetchedFile folder2 = new FetchedFile("config");
    FetchedFile page2 = file("a.md", temp, "a.md");
    RapidoOutputs.Change change = new RapidoOutputs(cache, temp).update(List.of(folder2, page2), List.of(page2));
    assertEquals(Set.of(), change.getKept());
    assertEquals(Set.of(), change.getGone());
  }

  @Test
  void theOutputsAreCompleteOnlyWhileAllOfThemAreInTheTempFolder() throws Exception {
    String temp = dir.resolve("temp").toString();
    String cache = dir.resolve("cache").toString();
    Files.createDirectories(dir.resolve("cache"));
    assertFalse(new RapidoOutputs(cache, temp).isComplete(), "no record");

    Files.createDirectories(dir.resolve("temp").resolve("en"));
    Files.writeString(dir.resolve("temp").resolve("a.md"), "a");
    Files.writeString(dir.resolve("temp").resolve("en").resolve("a.md"), "a");
    FetchedFile page = file("a.md", temp, "a.md", "en/a.md");
    new RapidoOutputs(cache, temp).update(List.of(page), List.of(page));
    assertTrue(new RapidoOutputs(cache, temp).isComplete());

    // the temp folder was cleared (or partly) since
    Files.delete(dir.resolve("temp").resolve("en").resolve("a.md"));
    assertFalse(new RapidoOutputs(cache, temp).isComplete());
  }

  @Test
  void whatTheUnchangedFilesWroteIsntKnownWithoutARecord() throws Exception {
    String temp = dir.resolve("temp").toString();
    Files.createDirectories(dir.resolve("cache"));
    FetchedFile page = file("a.md", temp, "a.md");
    FetchedFile other = file("b.md", temp);

    assertNull(new RapidoOutputs(dir.resolve("cache").toString(), temp).update(List.of(page, other), List.of(page)));
    assertFalse(Files.exists(dir.resolve("cache").resolve(".build-outputs.json")));
  }

  @Test
  void aFileThatWasntThereLastTimeAndWasntGeneratedIsntKnown() throws Exception {
    String temp = dir.resolve("temp").toString();
    String cache = dir.resolve("cache").toString();
    Files.createDirectories(dir.resolve("cache"));
    FetchedFile page = file("a.md", temp, "a.md");
    new RapidoOutputs(cache, temp).update(List.of(page), List.of(page));

    FetchedFile page2 = file("a.md", temp, "a.md");
    FetchedFile added = file("b.md", temp);
    assertNull(new RapidoOutputs(cache, temp).update(List.of(page2, added), List.of(page2)));
    assertFalse(Files.exists(dir.resolve("cache").resolve(".build-outputs.json")));
  }

  private static String p(String temp, String name) throws Exception {
    return Utilities.path(temp, name);
  }

  private static FetchedFile file(String loadPath, String temp, String... outputs) throws Exception {
    FetchedFile f = new FetchedFile(loadPath);
    for (String o : outputs) {
      f.getOutputNames().add(Utilities.path(temp, o));
    }
    return f;
  }
}
