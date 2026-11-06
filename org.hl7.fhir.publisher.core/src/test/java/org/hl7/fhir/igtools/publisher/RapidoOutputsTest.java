package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

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

    assertEquals(Set.of(temp + "/_config.yml"), change.getKept());
    assertEquals(Set.of(temp + "/Patient-p.html", temp + "/en/Patient-p.html", temp + "/Patient-gone.html", temp + "/en/Patient-gone.html"), change.getGone());

    // and what the config wrote is still known in the build after that
    FetchedFile config3 = file("template/config/_config.yml", temp);
    FetchedFile page3 = file("input/pagecontent/a.md", temp, "a.md");
    FetchedFile patient3 = file("Patient-p.json", temp);
    change = new RapidoOutputs(cache, temp).update(List.of(config3, page3, patient3), List.of(page3));
    assertEquals(Set.of(temp + "/_config.yml", temp + "/Patient-q.html", temp + "/en/Patient-q.html"), change.getKept());
    assertEquals(Set.of(temp + "/en/a.md"), change.getGone());
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

  private static FetchedFile file(String loadPath, String temp, String... outputs) {
    FetchedFile f = new FetchedFile(loadPath);
    for (String o : outputs) {
      f.getOutputNames().add(temp + "/" + o);
    }
    return f;
  }
}
