package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.hl7.fhir.igtools.templates.Template;
import org.hl7.fhir.model.core.ImplementationGuide;
import org.hl7.fhir.utilities.TimeTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublisherGeneratorTemplatePagesTest {

  @TempDir
  Path dir;

  @Test
  void templatePagesGetTheirRelativePathWhenTheFolderIsNotGivenByItsCanonicalPath() throws Exception {
    Path real = dir.resolve("a-much-longer-real-folder-name");
    Files.createDirectories(real.resolve("temp/pages/_includes"));
    // the same mismatch as an 8.3 short name on Windows, or /var and /private/var on macOS
    Path link = dir.resolve("ig");
    try {
      Files.createSymbolicLink(link, real);
    } catch (IOException | UnsupportedOperationException e) {
      assumeTrue(false, "can't make a symbolic link here: " + e.getMessage());
    }

    PublisherFields pf = new PublisherFields();
    pf.repoRoot = link.toString();
    pf.tempDir = link.resolve("temp/pages").toString();
    pf.publishedIg = new ImplementationGuide();
    pf.tt = new TimeTracker();
    // as a template's onGenerate script does
    pf.template = mock(Template.class);
    doAnswer(invocation -> {
      Files.writeString(real.resolve("temp/pages/_includes/artifacts.xml"), "<div/>");
      List<String> newFileList = invocation.getArgument(4);
      newFileList.add("temp" + File.separator + "pages" + File.separator + "_includes" + File.separator + "artifacts.xml");
      return null;
    }).when(pf.template).beforeGenerateEvent(any(), any(), any(), any(), any(), any());
    PublisherGenerator generator = new PublisherGenerator(new PublisherSettings());
    generator.setPf(pf);

    generator.templateBeforeGenerate();

    assertEquals(1, pf.fileList.size());
    assertEquals("_includes" + File.separator + "artifacts.xml", pf.fileList.get(0).getRelativePath());
  }
}
