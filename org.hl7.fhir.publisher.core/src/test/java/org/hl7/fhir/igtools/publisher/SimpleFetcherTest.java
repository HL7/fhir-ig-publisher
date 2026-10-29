package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.utilities.logging.ILoggingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SimpleFetcherTest {

  @TempDir
  Path dir;

  @Test
  void undecodableFileNameUnderAsciiSaysToUseAUtf8Locale() {
    String message = SimpleFetcher.undecodableFileNameMessage("input/pagecontent/\uFFFD\uFFFDlevaade.md", "ANSI_X3.4-1968");

    assertTrue(message.startsWith("Unable to read input/pagecontent/\uFFFD\uFFFDlevaade.md: its name can't be decoded with the file name encoding 'ANSI_X3.4-1968'"), message);
    assertTrue(message.contains("LC_ALL=C.UTF-8"), message);
  }

  @Test
  void undecodableFileNameUnderUtf8SaysToRenameIt() {
    String message = SimpleFetcher.undecodableFileNameMessage("input/pagecontent/\uFFFDlevaade.md", "UTF-8");

    assertTrue(message.startsWith("Unable to read input/pagecontent/\uFFFDlevaade.md: its name isn't valid UTF-8"), message);
    assertFalse(message.contains("locale"), message);
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void fetchReportsUndecodableFileName() throws Exception {
    rawName("touch", "\\374levaade.md");
    SimpleFetcher fetcher = new SimpleFetcher(null);

    Exception e = assertThrows(Exception.class, () -> {
      for (String f : fetcher.fetch(dir.toString()).getFiles()) {
        fetcher.fetch(f);
      }
    });

    assertEquals(expectedMessage("\uFFFDlevaade.md"), e.getMessage());
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void scanReportsUndecodableFileName() throws Exception {
    rawName("touch", "Patient-\\374.json");

    Exception e = assertThrows(Exception.class, () -> fetcher().scan(dir.toString(), null, false, new ArrayList<>()));

    assertEquals(expectedMessage("Patient-\uFFFD.json"), e.getMessage());
  }

  @ParameterizedTest
  @CsvSource({"touch, Notizen-\\374.md", "touch, Ablauf-\\374.svg", "touch, ._\\374bersicht.json", "touch, \\374bersicht.html", "mkdir, \\374bersicht"})
  @EnabledOnOs(OS.LINUX)
  void scanSkipsUndecodableNamesItDoesNotRead(String command, String name) throws Exception {
    rawName(command, name);

    assertTrue(fetcher().scan(dir.toString(), null, false, new ArrayList<>()).isEmpty());
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void scanFoldersReportsUndecodableFolderName() throws Exception {
    rawName("mkdir", "\\374bersicht");

    Exception e = assertThrows(Exception.class, () -> fetcher().scanFolders(dir.toString(), new ArrayList<>()));

    assertEquals(expectedMessage("\uFFFDbersicht"), e.getMessage());
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void scanFoldersLeavesUndecodableFileName() throws Exception {
    rawName("touch", "\\374bersicht.md");
    List<String> dirs = new ArrayList<>();

    fetcher().scanFolders(dir.toString(), dirs);

    assertEquals(List.of(dir.toFile().getAbsolutePath()), dirs);
  }

  private SimpleFetcher fetcher() {
    return new SimpleFetcher(mock(ILoggingService.class));
  }

  /**
   * Makes a file or folder whose name has the byte 0xFC (u-umlaut in ISO-8859-1), which isn't valid UTF-8 or ASCII, so
   * the JVM lists it with U+FFFD in it under a UTF-8 or an ASCII locale, as it lists a UTF-8 non-ASCII name under the
   * POSIX locale
   */
  private void rawName(String command, String name) throws Exception {
    Process p = new ProcessBuilder("sh", "-c", command + " \"$(printf '" + name + "')\"").directory(dir.toFile()).start();
    assertEquals(0, p.waitFor());
  }

  private String expectedMessage(String name) {
    return SimpleFetcher.undecodableFileNameMessage(new File(dir.toFile(), name).getPath(), System.getProperty("sun.jnu.encoding"));
  }
}
