package org.hl7.fhir.igtools.publisher.loaders;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.model.core.Attachment;
import org.hl7.fhir.model.core.Library;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdjunctFileLoaderTest {

  @Test
  void replaceAttachments2LoadsAttachmentWithExplicitContentType(@TempDir Path tempDir) throws IOException {
    byte[] content = {1, 2, 3};
    Files.write(tempDir.resolve("artifact.bin"), content);

    Attachment attachment = new Attachment();
    attachment.setId("ig-loader-artifact.bin");
    attachment.setContentType("application/custom");
    Library library = new Library();
    library.addContent(attachment);
    FetchedResource resource = new FetchedResource("Library/example");
    resource.setResource(library);

    AdjunctFileLoader loader = new AdjunctFileLoader(List.of(tempDir.toString()), null);
    assertTrue(loader.replaceAttachments2(new FetchedFile("input.json"), resource));

    assertNull(attachment.getId());
    assertEquals("application/custom", attachment.getContentType());
    assertArrayEquals(content, attachment.getData());
  }
}
