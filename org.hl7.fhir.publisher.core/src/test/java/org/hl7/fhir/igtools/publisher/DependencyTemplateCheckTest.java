package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.hl7.fhir.exceptions.FHIRException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A dependency found through its canonical goes into the shared package cache under the id its package-list.json
 * gives. It can't be a template, or claim a trusted template's id, or a later build would run it as that template
 */
class DependencyTemplateCheckTest {

  private static byte[] tgz(String name, String type) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GzipCompressorOutputStream(bytes))) {
      byte[] json = ("{\"name\" : \""+name+"\", \"version\" : \"1.0.0\", \"type\" : \""+type+"\", \"fhirVersions\" : [\"4.0.1\"]}").getBytes(StandardCharsets.UTF_8);
      TarArchiveEntry e = new TarArchiveEntry("package/package.json");
      e.setSize(json.length);
      tar.putArchiveEntry(e);
      tar.write(json);
      tar.closeArchiveEntry();
    }
    return bytes.toByteArray();
  }

  @ParameterizedTest
  @CsvSource({
    // package-list.json claims a trusted template's id
    "hl7.fhir.template, example.ig, fhir.ig",
    // the package says it is a trusted template
    "example.ig, hl7.fhir.template, fhir.ig",
    // any template
    "example.ig, example.template, fhir.template"
  })
  void aDependencyThatIsATemplateIsRefused(String pid, String name, String type) throws IOException {
    byte[] tgz = tgz(name, type);
    assertThrows(FHIRException.class, () -> PublisherIGLoader.checkDependencyIsNotATemplate(pid, "1.0.0", tgz, "https://example.org/package.tgz"));
  }

  @Test
  void anIGDependencyIsFine() throws IOException {
    PublisherIGLoader.checkDependencyIsNotATemplate("example.ig", "1.0.0", tgz("example.ig", "fhir.ig"), "https://example.org/package.tgz");
  }
}
