package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.utilities.xhtml.XhtmlDocument;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;
import org.junit.jupiter.api.Test;

class XhtmlParsingTest {

  private static class TrackingStream extends ByteArrayInputStream {
    boolean closed;

    TrackingStream(String content) {
      super(content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  @Test
  void closesTheStreamAfterParsing() throws Exception {
    TrackingStream in = new TrackingStream("<html><body><p>text</p></body></html>");
    XhtmlDocument doc = XhtmlParsing.parseAndClose(new XhtmlParser(), in, null);
    assertEquals("text", doc.getElement("html").getElement("body").getElement("p").allText());
    assertTrue(in.closed);
  }

  @Test
  void closesTheStreamWhenParsingFails() {
    TrackingStream in = new TrackingStream("<html><body><p>text</div></body></html>");
    assertThrows(FHIRFormatError.class, () -> XhtmlParsing.parseAndClose(new XhtmlParser().setMustBeWellFormed(true), in, null));
    assertTrue(in.closed);
  }
}
