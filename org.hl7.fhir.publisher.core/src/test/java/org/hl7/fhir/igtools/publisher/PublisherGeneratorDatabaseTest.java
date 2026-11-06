package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.hl7.fhir.model.core.ImplementationGuide;
import org.hl7.fhir.services.renderers.utils.RenderingContext;
import org.hl7.fhir.standalone.context.SimpleWorkerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublisherGeneratorDatabaseTest {

  private static final Path FDS = Paths.get("/proc/self/fd");

  @TempDir
  Path dir;

  @Test
  void packageDbIsClosedWhenGenerationFails() throws Exception {
    assumeTrue(Files.isDirectory(FDS) || File.separatorChar == '\\', "needs /proc/self/fd or Windows file locking");

    PublisherFields pf = new PublisherFields();
    pf.context = mock(SimpleWorkerContext.class);
    pf.rc = mock(RenderingContext.class);
    pf.rcLangs = mock(RenderingContext.RenderingContextLangs.class);
    pf.sourceIg = new ImplementationGuide();
    pf.specPath = "http://hl7.org/fhir/R4/";
    pf.tempDir = dir.toString();
    pf.outputDir = dir.resolve("output").toString();
    pf.regenList = null; // makes generation fail just after the database is opened
    PublisherGenerator generator = new PublisherGenerator(new PublisherSettings());
    generator.setPf(pf);

    assertThrows(NullPointerException.class, generator::generate);

    Path db = dir.resolve("package.db");
    assertTrue(Files.exists(db));
    assertFalse(isOpen(db), "temp/pages/package.db is still open");
  }

  private static boolean isOpen(Path file) throws IOException {
    if (Files.isDirectory(FDS)) {
      Path real = file.toRealPath();
      try (DirectoryStream<Path> fds = Files.newDirectoryStream(FDS)) {
        for (Path fd : fds) {
          try {
            if (Files.readSymbolicLink(fd).equals(real)) {
              return true;
            }
          } catch (IOException e) {
            // closed while listing
          }
        }
      }
      return false;
    }
    // Windows won't delete a file that is open
    try {
      Files.delete(file);
      return false;
    } catch (FileSystemException e) {
      return true;
    }
  }
}
