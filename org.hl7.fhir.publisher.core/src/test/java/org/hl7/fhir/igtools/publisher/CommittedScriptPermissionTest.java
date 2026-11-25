package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Scripts and programs committed to the IG's repository: for now, never stop the build, but in the root folder, a
 * warning that says the build will stop (locally, unless the user accepts them with -allow-committed-scripts, recorded
 * in fhir-settings.json for that IG) or be refused (on the ci-build) in a future version; in sub-folders, a warning.
 * Only files git tracks count
 */
class CommittedScriptPermissionTest {

  @TempDir
  Path dir;

  private static final List<String> ROOT = List.of("_genonce.sh", "_updatePublisher.bat");
  private static final List<String> SUB = List.of("scripts/build.sh");

  private String settings() {
    return dir.resolve("fhir-settings.json").toString();
  }

  private String ig(String name) throws IOException {
    return Files.createDirectories(dir.resolve(name)).toString();
  }

  private String settingsContent() throws IOException {
    return new String(Files.readAllBytes(dir.resolve("fhir-settings.json")), StandardCharsets.UTF_8);
  }

  private static void write(Path root, String file, String content) throws IOException {
    write(root, file, content.getBytes(StandardCharsets.UTF_8));
  }

  private static void write(Path root, String file, byte[] content) throws IOException {
    Path p = root.resolve(file);
    Files.createDirectories(p.getParent());
    Files.write(p, content);
  }

  private static byte[] bytes(int... b) {
    byte[] res = new byte[b.length];
    for (int i = 0; i < b.length; i++) {
      res[i] = (byte) b[i];
    }
    return res;
  }

  /** a file with the 'MZ' DOS stub, and (if pe) the PE header where the stub says */
  private static byte[] windows(boolean pe) {
    byte[] res = new byte[0x90];
    res[0] = 'M';
    res[1] = 'Z';
    res[0x3C] = (byte) 0x80;
    if (pe) {
      res[0x80] = 'P';
      res[0x81] = 'E';
    }
    return res;
  }

  private static void zip(Path root, String file, String... entries) throws IOException {
    Path p = root.resolve(file);
    Files.createDirectories(p.getParent());
    try (OutputStream out = Files.newOutputStream(p); ZipOutputStream zip = new ZipOutputStream(out)) {
      for (String e : entries) {
        zip.putNextEntry(new ZipEntry(e));
        zip.write("x".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
  }

  private static boolean git(Path dir, String... args) throws IOException, InterruptedException {
    List<String> cmd = new ArrayList<>();
    cmd.add("git");
    cmd.addAll(List.of(args));
    Process p;
    try {
      p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
    } catch (IOException e) {
      return false;
    }
    return p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0;
  }

  @Test
  void noScriptsIsFine() throws IOException {
    List<String> warnings = new ArrayList<>();
    CommittedScriptPermission.check(List.of(), ig("ig"), true, false, settings(), warnings::add);
    CommittedScriptPermission.check(List.of(), ig("ig"), false, false, settings(), warnings::add);
    assertTrue(warnings.isEmpty());
  }

  @Test
  void theCiBuildWarnsThatItWillRefuseRootScriptsWhateverTheSettingsSay() throws IOException {
    IGPermissions.allow(settings(), CommittedScriptPermission.PROPERTY, ig("ig"));
    List<String> warnings = new ArrayList<>();
    CommittedScriptPermission.check(ROOT, ig("ig"), true, true, settings(), warnings::add);
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).contains("_genonce.sh"), warnings.get(0));
    assertTrue(warnings.get(0).contains("ci-build will refuse"), warnings.get(0));
    assertFalse(warnings.get(0).contains("accepted"), warnings.get(0));
  }

  @Test
  void scriptsInSubFoldersAreAWarningEverywhere() throws IOException {
    for (boolean ciBuild : new boolean[] { true, false }) {
      List<String> warnings = new ArrayList<>();
      CommittedScriptPermission.check(SUB, ig("ig"), ciBuild, false, settings(), warnings::add);
      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).contains("scripts/build.sh"), warnings.get(0));
      assertFalse(warnings.get(0).contains("future"), warnings.get(0));
    }
    assertFalse(Files.exists(dir.resolve("fhir-settings.json")));
  }

  @Test
  void theCiBuildWarnsAboutSubFoldersAndTheRoot() throws IOException {
    List<String> warnings = new ArrayList<>();
    List<String> both = new ArrayList<>(ROOT);
    both.addAll(SUB);
    CommittedScriptPermission.check(both, ig("ig"), true, false, settings(), warnings::add);
    assertEquals(2, warnings.size());
  }

  @Test
  void locallyTheBuildGoesOnAndExplainsWhatWillHappen() throws IOException {
    List<String> warnings = new ArrayList<>();
    CommittedScriptPermission.check(ROOT, ig("ig"), false, false, settings(), warnings::add);
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).contains(CommittedScriptPermission.PARAMETER), warnings.get(0));
    assertTrue(warnings.get(0).contains("_updatePublisher.bat"), warnings.get(0));
    assertTrue(warnings.get(0).contains("the build will stop"), warnings.get(0));
    assertFalse(Files.exists(dir.resolve("fhir-settings.json")));
  }

  @Test
  void theParameterAcceptsThemForThatIGAndIsRecorded() throws IOException {
    // what else is in the settings file stays, including links allowed for the IG
    Files.write(dir.resolve("fhir-settings.json"), "{ \"npm\" : { \"path\" : \"/usr/bin\" } }".getBytes(StandardCharsets.UTF_8));
    ExternalLinkPermission.allow(settings(), ig("ig"));
    List<String> warnings = new ArrayList<>();
    CommittedScriptPermission.check(ROOT, ig("ig"), false, true, settings(), warnings::add);
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).contains("accepted"), warnings.get(0));
    assertTrue(IGPermissions.isAllowed(settings(), CommittedScriptPermission.PROPERTY, ig("ig")));
    JsonObject json = JsonParser.parseObject(settingsContent());
    assertEquals("/usr/bin", json.getJsonObject("npm").asString("path"));
    assertTrue(ExternalLinkPermission.isAllowed(settings(), ig("ig")));
    // and next time, without the parameter, it's accepted (and the warning still says the ci-build won't be)
    warnings.clear();
    CommittedScriptPermission.check(ROOT, ig("ig"), false, false, settings(), warnings::add);
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).contains("_genonce.sh"));
    assertTrue(warnings.get(0).contains("accepted"), warnings.get(0));
    assertTrue(warnings.get(0).contains("ci-build will refuse"), warnings.get(0));
    // but not for another IG
    warnings.clear();
    CommittedScriptPermission.check(ROOT, ig("other"), false, false, settings(), warnings::add);
    assertTrue(warnings.get(0).contains("the build will stop"), warnings.get(0));
  }

  @Test
  void allowingLinksDoesntAcceptScripts() throws IOException {
    ExternalLinkPermission.allow(settings(), ig("ig"));
    List<String> warnings = new ArrayList<>();
    CommittedScriptPermission.check(ROOT, ig("ig"), false, false, settings(), warnings::add);
    assertTrue(warnings.get(0).contains("the build will stop"), warnings.get(0));
  }

  /**
   * Root-folder scripts and programs that haven't been accepted are errors (on the ci-build, whatever was accepted
   * locally); those in sub-folders, and those the user has accepted, are warnings
   */
  @Test
  void unacceptedRootScriptsAreErrorsAndTheRestWarnings() throws IOException {
    List<String> both = new ArrayList<>(ROOT);
    both.addAll(SUB);
    for (boolean ciBuild : new boolean[] { true, false }) {
      List<String> warnings = new ArrayList<>();
      List<String> errors = new ArrayList<>();
      CommittedScriptPermission.check(both, ig("ig"), ciBuild, false, settings(), warnings::add, errors::add);
      assertEquals(1, errors.size());
      assertTrue(errors.get(0).contains("_genonce.sh"), errors.get(0));
      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).contains("scripts/build.sh"), warnings.get(0));
    }
    // accepted locally: a warning
    List<String> warnings = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    CommittedScriptPermission.check(ROOT, ig("ig"), false, true, settings(), warnings::add, errors::add);
    assertTrue(errors.isEmpty(), errors.toString());
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).contains("accepted"), warnings.get(0));
    // but the ci-build doesn't go by what was accepted locally
    warnings.clear();
    CommittedScriptPermission.check(ROOT, ig("ig"), true, false, settings(), warnings::add, errors::add);
    assertTrue(warnings.isEmpty(), warnings.toString());
    assertEquals(1, errors.size());
  }

  @Test
  void aSettingsFileThatCantBeReadIsNotWrittenOver() throws IOException {
    Files.write(dir.resolve("fhir-settings.json"), "{ not json".getBytes(StandardCharsets.UTF_8));
    assertThrows(FHIRException.class, () -> CommittedScriptPermission.check(ROOT, ig("ig"), false, true, settings(), s -> {}));
    assertEquals("{ not json", settingsContent());
  }

  @Test
  void whatCountsAsAScript() throws IOException {
    Path ig = Path.of(ig("ig"));
    write(ig, "_genonce.sh", "java -jar publisher.jar");
    write(ig, "_genonce.BAT", "java -jar publisher.jar");
    write(ig, "setup.ps1", "x");
    write(ig, "run.cmd", "x");
    write(ig, "go.command", "x");
    write(ig, "build", "#!/bin/bash\necho hi");
    write(ig, "input/pagecontent/index.md", "# Index\n");
    write(ig, "input/pagecontent/notes.md", "#!not really, but it starts that way\n");
    write(ig, "input/fsh/profiles.fsh", "Profile: X");
    write(ig, ".git/hooks/pre-commit.sh", "x"); // not part of the IG
    // the executable bit doesn't count
    Path exec = ig.resolve("input/images/diagram.png");
    write(ig, "input/images/diagram.png", "png");
    exec.toFile().setExecutable(true);
    assertEquals(List.of("_genonce.BAT", "_genonce.sh", "build", "go.command", "input/pagecontent/notes.md", "run.cmd", "setup.ps1"),
        CommittedScriptPermission.findScripts(ig.toString()));
  }

  @Test
  void whatCountsAsAProgram() throws IOException {
    Path ig = Path.of(ig("ig"));
    // by extension, whatever is in them
    write(ig, "input-cache/publisher.jar", "not really a jar");
    write(ig, "tool.EXE", "x");
    write(ig, "lib/native.dll", "x");
    write(ig, "lib/libthing.so", "x");
    write(ig, "lib/libthing.dylib", "x");
    write(ig, "Thing.class", "x");
    // by content, whatever they are called
    write(ig, "elf", bytes(0x7F, 'E', 'L', 'F', 2, 1, 1));
    write(ig, "macho", bytes(0xCF, 0xFA, 0xED, 0xFE, 7, 0, 0, 1));
    write(ig, "universal", bytes(0xCA, 0xFE, 0xBA, 0xBE, 0, 0, 0, 2));
    write(ig, "win.dat", windows(true));
    zip(ig, "publisher.zip", "META-INF/MANIFEST.MF", "org/hl7/Main.class");
    zip(ig, "classes.bin", "org/hl7/Main.class");
    // not programs
    write(ig, "dos.txt", windows(false)); // 'MZ' but no PE header
    write(ig, "input/pagecontent/mz.md", "MZ is where it starts\n");
    zip(ig, "input/images/examples.zip", "patient.json", "observation.xml");
    zip(ig, "input/images/spec.docx", "[Content_Types].xml", "word/document.xml");
    write(ig, "input/images/broken.zip", bytes('P', 'K', 3, 4, 0, 0));
    write(ig, "input/images/diagram.png", bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A));
    write(ig, "empty", new byte[0]);
    write(ig, "one", bytes('#'));
    assertEquals(List.of("Thing.class", "classes.bin", "elf", "input-cache/publisher.jar", "lib/libthing.dylib", "lib/libthing.so", "lib/native.dll",
        "macho", "publisher.zip", "tool.EXE", "universal", "win.dat"),
        CommittedScriptPermission.findScripts(ig.toString()));
  }

  @Test
  void onlyFilesGitTracksCount() throws IOException, InterruptedException {
    Path ig = Path.of(ig("ig"));
    assumeTrue(git(ig, "init", "-q"), "git isn't available");
    write(ig, ".gitignore", "/_genonce.sh\n/input-cache\n");
    write(ig, "_genonce.sh", "x"); // the user's own copy, ignored
    write(ig, "input-cache/_updatePublisher.sh", "x");
    write(ig, "input-cache/publisher.jar", "x");
    write(ig, "_gencontinuous.sh", "x"); // committed
    write(ig, "scripts/build.bat", "x"); // committed
    write(ig, "new.sh", "x"); // not added yet
    assertTrue(git(ig, "add", ".gitignore", "_gencontinuous.sh", "scripts/build.bat"));
    assertEquals(List.of("_gencontinuous.sh", "scripts/build.bat"), CommittedScriptPermission.findScripts(ig.toString()));
  }

  @Test
  void anIGInASubFolderOfTheRepository() throws IOException, InterruptedException {
    Path repo = Path.of(ig("repo"));
    assumeTrue(git(repo, "init", "-q"), "git isn't available");
    write(repo, "_build.sh", "x"); // the repository's, not the IG's
    write(repo, "ig/_genonce.sh", "x");
    assertTrue(git(repo, "add", "."));
    assertEquals(List.of("_genonce.sh"), CommittedScriptPermission.findScripts(repo.resolve("ig").toString()));
  }
}
