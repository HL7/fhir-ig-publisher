package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * -ig names the IG's folder or its ini file, whatever that's called: the IG's folder (which file access is limited to,
 * and which is checked for links out) is the folder either way
 */
class FocusDirTest {

  @TempDir
  Path dir;

  @ParameterizedTest
  @ValueSource(strings = {"", "ig.ini", "custom.ini", "myig.ini", "not-there.ini", "."})
  void theIGsFolderIsTheFolder(String name) throws IOException {
    Path ig = Files.createDirectories(dir.resolve("myig"));
    Files.writeString(ig.resolve("ig.ini"), "[IG]");
    Files.writeString(ig.resolve("custom.ini"), "[IG]");
    Files.writeString(ig.resolve("myig.ini"), "[IG]");
    String configFile = name.isEmpty() ? ig.toString() : ig.toString() + java.io.File.separator + name;
    assertEquals(ig.toFile().getAbsoluteFile(), new java.io.File(PublisherBase.focusDir(configFile)).getAbsoluteFile());
  }
}
