package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import org.hl7.fhir.utilities.filesystem.ManagedFileAccess;
import org.hl7.fhir.utilities.xml.XmlEscaper;

/**
 * Writes the {@code _includes/*.escaped.json} files: the same bytes as {@link XmlEscaper#convert(String, String)}
 * produces from the plain {@code .json} file, but built in memory from the bytes the publisher has just written,
 * and written with a single call.
 * <p>
 * {@link XmlEscaper} writes to an unbuffered {@code FileOutputStream} one character at a time, so each
 * character of a resource is a separate system call. On an IG with large profiles that was about a third of the
 * "Generate Native Outputs" phase.
 * <p>
 * The output has to stay byte-identical to XmlEscaper's, quirks included: XmlEscaper decodes the input with the
 * platform default charset (an {@code InputStreamReader} without a charset), replaces {@code < > & "} with their
 * entities, and writes every other char as its low byte only ({@code (byte) c}), so a non-ASCII char comes out
 * as one byte that is not its UTF-8 encoding.
 */
public final class EscapedJsonWriter {

  private EscapedJsonWriter() {
  }

  /** Returns the bytes XmlEscaper would write for this input. */
  public static byte[] escape(byte[] source) {
    return escape(source, Charset.defaultCharset());
  }

  /**
   * Returns the bytes XmlEscaper would write if its reader decoded the input with this charset. Decoding the whole
   * array at once gives the same chars as XmlEscaper's {@code InputStreamReader}, malformed input included (both
   * replace it with U+FFFD).
   */
  static byte[] escape(byte[] source, Charset charset) {
    String s = new String(source, charset);
    int n = s.length();
    int size = n;
    for (int i = 0; i < n; i++) {
      switch (s.charAt(i)) {
      case '<':
      case '>':
        size += 3;
        break;
      case '&':
        size += 4;
        break;
      case '"':
        size += 5;
        break;
      default:
        break;
      }
    }
    byte[] out = new byte[size];
    int p = 0;
    for (int i = 0; i < n; i++) {
      char c = s.charAt(i);
      switch (c) {
      case '<':
        p = put(out, p, "&lt;");
        break;
      case '>':
        p = put(out, p, "&gt;");
        break;
      case '&':
        p = put(out, p, "&amp;");
        break;
      case '"':
        p = put(out, p, "&quot;");
        break;
      default:
        out[p++] = (byte) c;
      }
    }
    return out;
  }

  private static int put(byte[] out, int p, String entity) {
    for (int i = 0; i < entity.length(); i++) {
      out[p++] = (byte) entity.charAt(i);
    }
    return p;
  }

  /** Writes {@link #escape(byte[])} of the source to the file, opened the same way XmlEscaper opens it. */
  public static void write(byte[] source, String target) throws IOException {
    byte[] escaped = escape(source);
    try (OutputStream os = ManagedFileAccess.outStream(target)) {
      os.write(escaped);
    }
  }
}
