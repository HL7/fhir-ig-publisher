package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.io.InputStream;

import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.utilities.xhtml.XhtmlDocument;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;

class XhtmlParsing {

  private XhtmlParsing() {
  }

  /**
   * XhtmlParser.parse(InputStream, String) reads the stream to the end but leaves closing it to the caller.
   * Close it as soon as the page is parsed, so file handles don't pile up until the garbage collector gets to them.
   */
  static XhtmlDocument parseAndClose(XhtmlParser parser, InputStream input, String entryName) throws FHIRFormatError, IOException {
    try (InputStream in = input) {
      return parser.parse(in, entryName);
    }
  }
}
