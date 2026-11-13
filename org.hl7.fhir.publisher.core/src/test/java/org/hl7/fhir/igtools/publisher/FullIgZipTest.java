package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.zip.ZipException;

import org.hl7.fhir.utilities.ZipGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Watch mode's full-ig.zip writer: the zip is what ZipGenerator writes for the same folder (apart from the entry
 * times), whether the entries are compressed or copied from the last zip
 */
class FullIgZipTest {

  private static final String REDIRECT = "<html lang=\"en\">\r\n<head>\r\n<meta http-equiv=\"Refresh\" content=\"0; url=site/index.html\"/>\r\n</head>\r\n</html>\r\n";

  @TempDir
  Path dir;

  // a clock well after the files were written, so that their stamps can be trusted
  private final FullIgZip zip = new FullIgZip(() -> System.currentTimeMillis() + 60_000, 3);

  @Test
  void sameAsZipGeneratorAcrossChanges() throws IOException {
    Path site = dir.resolve("site");
    Files.createDirectories(site.resolve("sub/deeper"));
    Random r = new Random(42);
    for (int i = 0; i < 40; i++) {
      Files.write(site.resolve("page" + i + ".html"), page(r, i));
    }
    byte[] big = new byte[3_000_000];
    r.nextBytes(big);
    Files.write(site.resolve("random.bin"), big);
    Files.write(site.resolve("empty.txt"), new byte[0]);
    Files.write(site.resolve("sub/a.json"), "{\"a\":1}".getBytes(StandardCharsets.UTF_8));
    Files.write(site.resolve("sub/deeper/b-x.css"), "body { }".repeat(5000).getBytes(StandardCharsets.UTF_8));
    Files.write(site.resolve(".DS_Store"), new byte[] {1, 2, 3});

    check(site, 0);

    // the same again: every entry is copied
    check(site, 44);

    // a file changed, one added, one removed, one rewritten with the same content
    Files.write(site.resolve("page3.html"), page(r, 1003));
    Files.write(site.resolve("sub/new.html"), page(r, 77));
    Files.delete(site.resolve("page7.html"));
    Files.write(site.resolve("page8.html"), Files.readAllBytes(site.resolve("page8.html")));
    check(site, 42);
  }

  @Test
  void stampsThatMayStillChangeAreNotTrusted() throws IOException {
    FullIgZip now = new FullIgZip(System::currentTimeMillis, 2);
    Path site = dir.resolve("site");
    Files.createDirectories(site);
    Files.write(site.resolve("a.html"), "one".getBytes(StandardCharsets.UTF_8));
    Path z = dir.resolve("z.zip");
    now.write(z.toString(), site.toString(), "site/", "index.html", REDIRECT.getBytes(StandardCharsets.UTF_8));
    // same size, written straight away: the time may be the same, the content isn't
    Files.write(site.resolve("a.html"), "two".getBytes(StandardCharsets.UTF_8));
    now.write(z.toString(), site.toString(), "site/", "index.html", REDIRECT.getBytes(StandardCharsets.UTF_8));
    assertEquals(0, now.getReused());
    Path ref = dir.resolve("ref.zip");
    stock(site, ref);
    assertArrayEquals(withoutTimes(Files.readAllBytes(ref)), withoutTimes(Files.readAllBytes(z)));
  }

  @Test
  void theFilesInFlightStayWithinTheBudget() throws IOException {
    FullIgZip small = new FullIgZip(() -> System.currentTimeMillis() + 60_000, 3, 100_000);
    Path site = dir.resolve("site");
    Files.createDirectories(site);
    Random r = new Random(7);
    for (int i = 0; i < 10; i++) {
      byte[] b = new byte[60_000];
      r.nextBytes(b);
      Files.write(site.resolve("part" + i + ".bin"), b);
    }
    byte[] big = new byte[300_000];
    r.nextBytes(big);
    Files.write(site.resolve("big.bin"), big);
    Path z = dir.resolve("z.zip");
    assertTrue(small.write(z.toString(), site.toString(), "site/", "index.html", REDIRECT.getBytes(StandardCharsets.UTF_8)));
    // two parts would be over the budget, so they go one at a time; the big file goes on its own
    assertEquals(300_000, small.peakInFlight());
    Path ref = dir.resolve("ref.zip");
    stock(site, ref);
    assertArrayEquals(withoutTimes(Files.readAllBytes(ref)), withoutTimes(Files.readAllBytes(z)));
  }

  @Test
  void duplicateNameFails() throws IOException {
    Path site = dir.resolve("site");
    Files.createDirectories(site);
    Files.write(site.resolve("a.html"), "a".getBytes(StandardCharsets.UTF_8));
    Path z = dir.resolve("z.zip");
    assertThrows(ZipException.class, () -> zip.write(z.toString(), site.toString(), "", "a.html", new byte[0]));
  }

  private void check(Path site, int reused) throws IOException {
    Path ref = dir.resolve("ref.zip");
    Path mine = dir.resolve("mine.zip");
    stock(site, ref);
    assertTrue(zip.write(mine.toString(), site.toString(), "site/", "index.html", REDIRECT.getBytes(StandardCharsets.UTF_8)));
    assertEquals(reused, zip.getReused());
    assertArrayEquals(withoutTimes(Files.readAllBytes(ref)), withoutTimes(Files.readAllBytes(mine)));
  }

  private static void stock(Path site, Path ref) throws IOException {
    ZipGenerator zg = new ZipGenerator(ref.toString());
    zg.addFolder(site.toString(), "site/", false);
    zg.addFileSource("index.html", REDIRECT, false);
    zg.close();
  }

  private static byte[] page(Random r, int i) {
    StringBuilder b = new StringBuilder("<html><body>\n");
    for (int j = 0; j < 200 + r.nextInt(400); j++) {
      b.append("<p id=\"p").append(j).append("\">Paragraph ").append(i).append(" / ").append(r.nextInt(1000)).append("</p>\n");
    }
    return b.append("</body></html>\n").toString().getBytes(StandardCharsets.UTF_8);
  }

  /** The zip with the times in its local and central headers set to 0 */
  static byte[] withoutTimes(byte[] zip) {
    ByteBuffer b = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
    int end = zip.length - 22;
    while (b.getInt(end) != 0x06054b50) {
      end--;
    }
    int count = b.getShort(end + 10) & 0xffff;
    int cen = b.getInt(end + 16);
    for (int i = 0; i < count; i++) {
      assertEquals(0x02014b50, b.getInt(cen));
      b.putInt(cen + 12, 0);
      int loc = b.getInt(cen + 42);
      assertEquals(0x04034b50, b.getInt(loc));
      b.putInt(loc + 10, 0);
      cen += 46 + (b.getShort(cen + 28) & 0xffff) + (b.getShort(cen + 30) & 0xffff) + (b.getShort(cen + 32) & 0xffff);
    }
    return zip;
  }
}
