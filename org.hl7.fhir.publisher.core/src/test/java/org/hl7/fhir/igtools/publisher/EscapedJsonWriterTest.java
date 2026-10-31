package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.hl7.fhir.utilities.xml.XmlEscaper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** EscapedJsonWriter must write exactly what XmlEscaper writes, for any input. */
class EscapedJsonWriterTest {

  private static byte[] reference(byte[] source) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    XmlEscaper.convert(new ByteArrayInputStream(source), out);
    return out.toByteArray();
  }

  private static void check(byte[] source) throws IOException {
    assertArrayEquals(reference(source), EscapedJsonWriter.escape(source));
  }

  @Test
  void sameAsXmlEscaperForJson() throws IOException {
    check(new byte[0]);
    check("{\"a\":\"<div xmlns=\\\"http://www.w3.org/1999/xhtml\\\">x &amp; y > z</div>\"}".getBytes(StandardCharsets.UTF_8));
    check("<<>>&&\"\"".getBytes(StandardCharsets.UTF_8));
    check("plain ascii only\r\n\t".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void sameAsXmlEscaperForNonAscii() throws IOException {
    // XmlEscaper keeps only the low byte of each char; the result must match that, not proper UTF-8
    check("Õnne & \"Ülo\" – 中文 <😀> ß  ".getBytes(StandardCharsets.UTF_8));
    check("Ā ā ĀļĦ".getBytes(StandardCharsets.UTF_16LE));
  }

  @Test
  void sameAsXmlEscaperForArbitraryBytes() throws IOException {
    // includes malformed UTF-8, truncated sequences and lone surrogates' encodings
    Random rnd = new Random(42);
    for (int i = 0; i < 2000; i++) {
      byte[] b = new byte[rnd.nextInt(300)];
      rnd.nextBytes(b);
      if (i % 3 == 0) {
        for (int j = 0; j < b.length; j++) {
          if (rnd.nextInt(4) == 0) {
            b[j] = (byte) "<>&\"".charAt(rnd.nextInt(4));
          }
        }
      }
      check(b);
    }
    check(new byte[] { (byte) 0xe2, (byte) 0x82 });
    check(new byte[] { 'a', (byte) 0xed, (byte) 0xa0, (byte) 0x80, '<' });
    check(new byte[] { (byte) 0xf0, (byte) 0x9f, (byte) 0x98 });
  }

  /** XmlEscaper.process(), with the charset of its InputStreamReader made explicit */
  private static byte[] referenceWithCharset(byte[] source, Charset cs) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(source), cs));
    int i;
    while ((i = reader.read()) != -1) {
      char c = (char) i;
      if (c == '<') out.write("&lt;".getBytes(StandardCharsets.UTF_8));
      else if (c == '>') out.write("&gt;".getBytes(StandardCharsets.UTF_8));
      else if (c == '&') out.write("&amp;".getBytes(StandardCharsets.UTF_8));
      else if (c == '"') out.write("&quot;".getBytes(StandardCharsets.UTF_8));
      else out.write((byte) i);
    }
    return out.toByteArray();
  }

  @Test
  void sameAsXmlEscaperUnderOtherDefaultCharsets() throws IOException {
    // the platform default charset is not always UTF-8 (Java before 18 on Windows); inputs span the reader's buffers
    Random rnd = new Random(7);
    for (String cn : new String[] { "UTF-8", "windows-1252", "ISO-8859-1", "US-ASCII", "Shift_JIS", "GB18030" }) {
      Charset cs = Charset.forName(cn);
      for (int i = 0; i < 40; i++) {
        byte[] b = new byte[rnd.nextInt(20000)];
        rnd.nextBytes(b);
        if (i % 2 == 0) {
          byte[] t = "x\u00e9<&>\"".repeat(b.length / 5 + 1).getBytes(cs);
          System.arraycopy(t, 0, b, 0, Math.min(t.length, b.length));
        }
        assertArrayEquals(referenceWithCharset(b, cs), EscapedJsonWriter.escape(b, cs), cn);
      }
    }
  }

  @Test
  void largeInputBeyondReaderBuffers() throws IOException {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < 40000; i++) {
      b.append("{\"x\":\"a<b>&cé中\"},");
    }
    check(b.toString().getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void writesTheSameFileAsXmlEscaperConvert(@TempDir Path dir) throws IOException {
    byte[] src = "{\"t\":\"<p>x & ü</p>\"}".getBytes(StandardCharsets.UTF_8);
    File in = dir.resolve("r.json").toFile();
    Files.write(in.toPath(), src);
    String ref = dir.resolve("ref.escaped.json").toString();
    String mine = dir.resolve("mine.escaped.json").toString();
    XmlEscaper.convert(in.getAbsolutePath(), ref);
    EscapedJsonWriter.write(src, mine);
    assertArrayEquals(Files.readAllBytes(Path.of(ref)), Files.readAllBytes(Path.of(mine)));
  }
}
