package org.hl7.fhir.igtools.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
// core-tem-unwind import org.hl7.fhir.utilities.npm.NpmPackageSigner;
// core-tem-unwind import org.hl7.fhir.utilities.npm.NpmPackageSigner.SigningKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageSigningUpdaterTest {
//
//  @TempDir
//  Path web;
//
//  private static byte[] tgz(String name, String version) throws IOException {
//    ByteArrayOutputStream out = new ByteArrayOutputStream();
//    try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GzipCompressorOutputStream(out))) {
//      byte[] b = ("{\"name\":\"" + name + "\",\"version\":\"" + version + "\"}").getBytes(StandardCharsets.UTF_8);
//      TarArchiveEntry e = new TarArchiveEntry("package/package.json");
//      e.setSize(b.length);
//      tar.putArchiveEntry(e);
//      tar.write(b);
//      tar.closeArchiveEntry();
//    }
//    return out.toByteArray();
//  }
//
//  private static NpmPackageSigner newSigner() throws Exception {
//    KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
//    g.initialize(new ECGenParameterSpec("secp256r1"));
//    String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(g.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
//    return NpmPackageSigner.fromPem(pem);
//  }
//
//  private void write(String path, byte[] content) throws IOException {
//    File f = web.resolve(path).toFile();
//    f.getParentFile().mkdirs();
//    Files.write(f.toPath(), content);
//  }
//
//  private String feed(String nl) {
//    String[] lines = {
//      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
//      "<rss xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:fhir=\"http://hl7.org/fhir/feed\" version=\"2.0\">",
//      "  <channel>",
//      "    <title>Test</title>",
//      "    <link>http://example.org/fhir/package-feed.xml</link>",
//      "    <item>",
//      "      <title>example.a#1.0.0</title>",
//      "      <link>http://example.org/fhir/a/1.0.0/package.tgz</link>",
//      "      <fhir:kind>IG</fhir:kind>",
//      "      <pubDate>Wed, 04 Sep 2019 08:58:14 GMT</pubDate>",
//      "    </item>",
//      "    <item>",
//      "      <title>example.b#2.0.0</title>",
//      "      <link>https://example.org/fhir/b/2.0.0/example.b.r4.tgz</link>",
//      "      <pubDate>Wed, 04 Sep 2019 08:58:14 GMT</pubDate>",
//      "    </item>",
//      "    <item>",
//      "      <title>elsewhere#1.0.0</title>",
//      "      <link>http://example.org/other/1.0.0/package.tgz</link>",
//      "      <pubDate>Wed, 04 Sep 2019 08:58:14 GMT</pubDate>",
//      "    </item>",
//      "  </channel>",
//      "</rss>"
//    };
//    return String.join(nl, lines) + nl;
//  }
//
//  private String run(NpmPackageSigner signer, boolean dryRun) throws IOException {
//    ByteArrayOutputStream log = new ByteArrayOutputStream();
//    new PackageSigningUpdater(signer, dryRun, new PrintStream(log, true, StandardCharsets.UTF_8), "test").execute(List.of(web.toFile()));
//    return log.toString(StandardCharsets.UTF_8);
//  }
//
//  private void setUp(String nl) throws IOException {
//    write("a/1.0.0/package.tgz", tgz("example.a", "1.0.0"));
//    write("b/2.0.0/example.b.r4.tgz", tgz("example.b.r4", "2.0.0"));
//    write("old/igpack.tgz", "not a package".getBytes(StandardCharsets.UTF_8));
//    write("package-feed.xml", feed(nl).getBytes(StandardCharsets.UTF_8));
//  }
//
//  private String feedNow() throws IOException {
//    return Files.readString(web.resolve("package-feed.xml"));
//  }
//
//  @Test
//  void signsPackagesAndRecordsThemInTheFeed() throws Exception {
//    setUp("\n");
//    NpmPackageSigner signer = newSigner();
//    String log = run(signer, false);
//    assertTrue(log.contains("Signed 2, already signed 0, not packages 1, failed 0"), log);
//    assertTrue(log.contains("other/1.0.0/package.tgz is not under"), log);
//
//    List<SigningKey> keys = List.of(new SigningKey(signer.getKeyId(), signer.getPublicKey()));
//    assertEquals("example.a", NpmPackageSigner.verify(web.resolve("a/1.0.0/package.tgz").toFile(), web.resolve("a/1.0.0/package.tgz.sigstore.json").toFile(), keys).getName());
//    assertEquals("example.b.r4", NpmPackageSigner.verify(web.resolve("b/2.0.0/example.b.r4.tgz").toFile(), web.resolve("b/2.0.0/example.b.r4.tgz.sigstore.json").toFile(), keys).getName());
//    assertFalse(web.resolve("old/igpack.tgz.sigstore.json").toFile().exists());
//
//    String expected = feed("\n")
//      .replace("      <fhir:kind>IG</fhir:kind>\n", "      <fhir:kind>IG</fhir:kind>\n      <fhir:signature>http://example.org/fhir/a/1.0.0/package.tgz.sigstore.json</fhir:signature>\n")
//      .replace("example.b.r4.tgz</link>\n", "example.b.r4.tgz</link>\n      <fhir:signature>https://example.org/fhir/b/2.0.0/example.b.r4.tgz.sigstore.json</fhir:signature>\n");
//    assertEquals(expected, feedNow());
//  }
//
//  @Test
//  void runningAgainChangesNothing() throws Exception {
//    setUp("\n");
//    NpmPackageSigner signer = newSigner();
//    run(signer, false);
//    String feed = feedNow();
//    long sigTime = web.resolve("a/1.0.0/package.tgz.sigstore.json").toFile().lastModified();
//    String log = run(signer, false);
//    assertTrue(log.contains("Signed 0, already signed 2, not packages 1, failed 0"), log);
//    assertTrue(log.contains("already had one: 2"), log);
//    assertEquals(feed, feedNow());
//    assertEquals(sigTime, web.resolve("a/1.0.0/package.tgz.sigstore.json").toFile().lastModified());
//  }
//
//  @Test
//  void aNewKeySignsAgain() throws Exception {
//    setUp("\n");
//    run(newSigner(), false);
//    NpmPackageSigner other = newSigner();
//    String log = run(other, false);
//    assertTrue(log.contains("Signed 2, already signed 0"), log);
//    List<SigningKey> keys = List.of(new SigningKey(other.getKeyId(), other.getPublicKey()));
//    NpmPackageSigner.verify(web.resolve("a/1.0.0/package.tgz").toFile(), web.resolve("a/1.0.0/package.tgz.sigstore.json").toFile(), keys);
//  }
//
//  @Test
//  void aDryRunWritesNothing() throws Exception {
//    setUp("\n");
//    String log = run(newSigner(), true);
//    assertTrue(log.contains("Would sign 2, already signed 0, not packages 1, failed 0"), log);
//    assertTrue(log.contains("that would get a signature: 2"), log);
//    assertFalse(web.resolve("a/1.0.0/package.tgz.sigstore.json").toFile().exists());
//    assertEquals(feed("\n"), feedNow());
//  }
//
//  @Test
//  void aMapFindsPackagesPublishedElsewhere() throws Exception {
//    setUp("\n");
//    write("../other/1.0.0/package.tgz", tgz("elsewhere", "1.0.0"));
//    NpmPackageSigner signer = newSigner();
//    ByteArrayOutputStream log = new ByteArrayOutputStream();
//    PackageSigningUpdater u = new PackageSigningUpdater(signer, false, new PrintStream(log, true, StandardCharsets.UTF_8), "test");
//    u.addMap("http://example.org/other", web.resolve("../other").toFile());
//    u.execute(List.of(web.toFile(), web.resolve("../other").toFile()));
//    String out = log.toString(StandardCharsets.UTF_8);
//    assertTrue(out.contains("Signed 3,"), out);
//    assertTrue(feedNow().contains("<fhir:signature>http://example.org/other/1.0.0/package.tgz.sigstore.json</fhir:signature>"), feedNow());
//    assertFalse(out.contains("is not under"), out);
//  }
//
//  @Test
//  void keepsCrlfLineEndings() throws Exception {
//    setUp("\r\n");
//    run(newSigner(), false);
//    String feed = feedNow();
//    assertTrue(feed.contains("<fhir:signature>http://example.org/fhir/a/1.0.0/package.tgz.sigstore.json</fhir:signature>\r\n"), feed);
//    assertFalse(feed.replace("\r\n", "").contains("\n"), "every line ends with CRLF");
//  }
}
