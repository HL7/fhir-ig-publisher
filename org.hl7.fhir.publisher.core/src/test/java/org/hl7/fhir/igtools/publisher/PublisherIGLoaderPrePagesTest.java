package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublisherIGLoaderPrePagesTest {

  @TempDir
  Path dir;

  @Test
  void prePagesGetTheirRelativePathWhenTheFolderIsNotGivenByItsCanonicalPath() throws Exception {
    Path real = dir.resolve("a-much-longer-real-folder-name");
    Files.createDirectories(real.resolve("input/intro-notes/sub"));
    Files.writeString(real.resolve("input/intro-notes/index.md"), "index");
    Files.writeString(real.resolve("input/intro-notes/sub/notes.md"), "notes");
    // the same mismatch as an 8.3 short name on Windows, or /var and /private/var on macOS
    Path link = dir.resolve("ig");
    try {
      Files.createSymbolicLink(link, real);
    } catch (IOException | UnsupportedOperationException e) {
      assumeTrue(false, "can't make a symbolic link here: " + e.getMessage());
    }

    String prePages = link.resolve("input/intro-notes").toString();
    PublisherFields pf = new PublisherFields();
    pf.prePagesDirs.add(prePages);
    pf.preProcessInfo.put(prePages, new PreProcessInfo(null, "_includes"));
    PublisherIGLoader loader = new PublisherIGLoader(new PublisherSettings());
    loader.setPf(pf);

    loader.loadPrePages();

    Set<String> relativePaths = new TreeSet<>();
    for (FetchedFile f : pf.fileList) {
      relativePaths.add(f.getRelativePath());
    }
    assertEquals(Set.of("_includes" + File.separator + "index.md", "_includes" + File.separator + "sub" + File.separator + "notes.md"), relativePaths);
  }
}
