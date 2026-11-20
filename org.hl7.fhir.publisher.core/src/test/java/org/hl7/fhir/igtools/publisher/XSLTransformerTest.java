package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The stylesheets XSLTransformer runs are named by templates: they get the principal output, and nothing else
 */
class XSLTransformerTest {

  @TempDir
  Path dir;

  private static byte[] stylesheet(String body) {
    return ("<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
        + "<xsl:output method=\"xml\" omit-xml-declaration=\"yes\"/>"
        + "<xsl:template match=\"/\">" + body + "</xsl:template></xsl:stylesheet>").getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] source() {
    return "<r/>".getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void transformsTheSource() throws Exception {
    byte[] out = new XSLTransformer(false).transform(source(), stylesheet("<out><xsl:copy-of select=\"/\"/></out>"));

    assertEquals("<out><r/></out>", new String(out, StandardCharsets.UTF_8).trim());
  }

  @Test
  void cannotWriteAFile() {
    Path target = dir.resolve("written.txt");
    byte[] xslt = stylesheet("<xsl:result-document href=\"" + target.toUri() + "\" method=\"text\">written</xsl:result-document><out/>");

    assertThrows(Exception.class, () -> new XSLTransformer(false).transform(source(), xslt));

    assertFalse(Files.exists(target));
  }

  @Test
  void cannotReadAFile() throws Exception {
    Path secret = Files.writeString(dir.resolve("secret.txt"), "secret");
    byte[] xslt = stylesheet("<out><xsl:value-of select=\"unparsed-text('" + secret.toUri() + "')\"/></out>");

    assertThrows(Exception.class, () -> new XSLTransformer(false).transform(source(), xslt));
  }
}
