package org.hl7.fhir.igtools.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.model.core.ImplementationGuide;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.filesystem.ManagedFileAccess;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

/**
 * The local folders a template is loaded from (getLocalFolders()), which watch mode watches: they are known also when
 * the template can't be loaded, because fixing it is a change that needs a rebuild
 */
class TemplateManagerTest {

  @TempDir
  Path dir;

  private void write(String path, String content) throws IOException {
    Path p = dir.resolve(path);
    Files.createDirectories(p.getParent());
    Files.write(p, content.getBytes(StandardCharsets.UTF_8));
  }

  /** with a fhir-settings.json of its own (which needn't exist), so the user's real settings play no part */
  private TemplateManager manager() {
    TemplateManager tm = new TemplateManager(null, Mockito.mock(ILoggingService.class));
    tm.setSettingsPath(settingsFile());
    return tm;
  }

  private String settingsFile() {
    return dir.resolve("fhir-settings.json").toString();
  }

  private String folder(String path) {
    return dir.resolve(path).toFile().getAbsolutePath();
  }

  /** the load failed because package.json is broken, not for some other reason */
  private void assertBrokenJson(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t.getMessage() != null && t.getMessage().contains("Error parsing JSON")) {
        return;
      }
    }
    fail("not a JSON error: " + e);
  }

  /**
   * A template in the IG's own folder (template = #template) is local to the repository, so it can't be trusted: on
   * the ci-build its script isn't run, not even the tasks outside a target. Locally, it is (which shows the test would
   * notice)
   */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void anInPlaceTemplateRunsItsScriptOnlyOutsideTheCiBuild(boolean autoMode) throws IOException {
    Path marker = dir.resolve("marker.txt");
    write("template/config.json", "{\"script\": \"scripts/ant.xml\", \"otherScripts\": [], \"targets\": {\"onLoad\": \"onLoad\"}}");
    write("template/scripts/ant.xml", "<project name=\"test\">\n<touch file=\"" + marker + "\"/>\n<target name=\"onLoad\"/>\n</project>\n");
    if (!autoMode) {
      write("fhir-settings.json", "{\"trustedTemplates\": [\"#template\"]}"); // locally, the user has said they trust it
    }

    Template template = manager().loadTemplate("#template", dir.toString(), "x.y", autoMode, false, false);

    assertEquals(!autoMode, Files.exists(marker));
    if (autoMode) {
      FHIRException e = assertThrows(FHIRException.class, () -> template.onLoadEvent(ig(), new HashMap<>()));
      assertTrue(e.getMessage().contains("'#template' is not trusted (reason: Template nominates an ant script or targets)"), e.getMessage());
    }
  }

  /**
   * The same rules as for a packaged template: a file that isn't on the list of harmless ones is active content, even
   * without a script
   */
  @Test
  void anInPlaceTemplateWithAnUnlistedFileIsNotTrustedOnTheCiBuild() throws IOException {
    write("template/config.json", "{}");
    write("template/scripts/run.sh", "echo hello");
    ILoggingService logger = Mockito.mock(ILoggingService.class);

    new TemplateManager(null, logger).loadTemplate("#template", dir.toString(), "x.y", true, false, false);

    Mockito.verify(logger).logMessage("IG template '#template' is not trusted.  No scripts will be executed");
  }

  /**
   * A pre-process transform is an xslt the publisher runs over the IG's pages: active content, like a script
   */
  @Test
  void anInPlaceTemplateWithAPreProcessTransformIsNotTrustedOnTheCiBuild() throws IOException {
    write("template/config.json", "{\"pre-process\": [{\"folder\": \"input/pagecontent\", \"relativePath\": \"_includes\", \"transform\": \"template/process.xml\"}]}");
    write("template/process.xml", "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"/>");

    Template template = manager().loadTemplate("#template", dir.toString(), "x.y", true, false, false);

    assertFalse(template.canExecute());
    assertTrue(template.getUntrustedReason().contains("Template nominates a pre-process transform"), template.getUntrustedReason());
  }

  @Test
  void anInPlaceTemplateWithPlainPreProcessCopiesIsFineOnTheCiBuild() throws IOException {
    write("template/config.json", "{\"pre-process\": [{\"folder\": \"input/includes\", \"relativePath\": \"_includes\"}]}");

    Template template = manager().loadTemplate("#template", dir.toString(), "x.y", true, false, false);

    assertTrue(template.canExecute());
  }

  /**
   * scripts/ is where trusted scripts keep what they read and run, so a file there is active content even if its
   * extension is a harmless one
   */
  @Test
  void anInPlaceTemplateWithAFileInScriptsIsNotTrustedOnTheCiBuild() throws IOException {
    write("template/config.json", "{}");
    write("template/scripts/groupings.txt", "group");

    Template template = manager().loadTemplate("#template", dir.toString(), "x.y", true, false, false);

    assertFalse(template.canExecute());
    assertTrue(template.getUntrustedReason().contains("Template has content in scripts/ (scripts/groupings.txt)"), template.getUntrustedReason());
  }

  @Test
  void anInPlaceTemplateWithOnlyHarmlessFilesIsFineOnTheCiBuild() throws IOException {
    write("template/config.json", "{}");
    write("template/content/index.html", "<p>hello</p>");
    ILoggingService logger = Mockito.mock(ILoggingService.class);

    new TemplateManager(null, logger).loadTemplate("#template", dir.toString(), "x.y", true, false, false);

    Mockito.verify(logger, Mockito.never()).logMessage(Mockito.contains("is not trusted"));
  }

  private static ImplementationGuide ig() {
    ImplementationGuide ig = new ImplementationGuide();
    ig.setId("test");
    ig.setUrl("http://example.org/ImplementationGuide/test");
    ig.setName("Test");
    return ig;
  }

  /**
   * A trusted template is trusted only as published: a released version, #current, or the latest release
   */
  @ParameterizedTest
  @ValueSource(strings = {"hl7.fhir.template", "hl7.fhir.template#current", "hl7.fhir.template#0.12.0", "hl7.fhir.template#10.0.12"})
  void releasedVersionsAndCurrentAreTrustable(String template) {
    assertTrue(TemplateManager.isTrustableVersion(template));
  }

  @ParameterizedTest
  @ValueSource(strings = {"hl7.fhir.template#current$my-branch", "hl7.fhir.template#dev", "hl7.fhir.template#dev$branch",
      "hl7.fhir.template#0.12.0-cibuild", "hl7.fhir.template#0.12", "hl7.fhir.template#latest", "hl7.fhir.template#"})
  void otherVersionsAreNotTrustable(String template) {
    assertFalse(TemplateManager.isTrustableVersion(template));
  }

  private Path scriptedInPlaceTemplate() throws IOException {
    Path marker = dir.resolve("marker.txt");
    write("template/config.json", "{\"script\": \"scripts/ant.xml\", \"otherScripts\": [], \"targets\": {\"onLoad\": \"onLoad\"}}");
    write("template/scripts/ant.xml", "<project name=\"test\">\n<touch file=\"" + marker + "\"/>\n<target name=\"onLoad\"/>\n</project>\n");
    return marker;
  }

  /**
   * Locally, a template with scripts that isn't trusted doesn't run until the user says they trust it
   */
  @Test
  void locallyAnUntrustedScriptStopsTheRun() throws IOException {
    Path marker = scriptedInPlaceTemplate();

    FHIRException e = assertThrows(FHIRException.class, () -> manager().loadTemplate("#template", dir.toString(), "x.y", false, false, false));

    assertTrue(e.getMessage().contains("'-trust-template #template'"), e.getMessage());
    assertTrue(e.getMessage().contains("'-trust-template all'"), e.getMessage());
    assertTrue(e.getMessage().contains(settingsFile()), e.getMessage());
    assertFalse(Files.exists(marker));
    assertFalse(Files.exists(dir.resolve("fhir-settings.json")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"#template", "all"})
  void locallyTrustTemplateRunsTheScriptAndIsRecorded(String trust) throws IOException {
    Path marker = scriptedInPlaceTemplate();
    TemplateManager tm = manager();
    tm.setTrustTemplate(trust);

    tm.loadTemplate("#template", dir.toString(), "x.y", false, false, false);

    assertTrue(Files.exists(marker));
    assertTrue(LocalTemplateTrust.isTrusted(settingsFile(), "#template"));
    // and the next run doesn't need it
    manager().loadTemplate("#template", dir.toString(), "x.y", false, false, false);
  }

  @Test
  void locallyTrustingAnotherTemplateDoesNotRunThisOne() throws IOException {
    Path marker = scriptedInPlaceTemplate();
    TemplateManager tm = manager();
    tm.setTrustTemplate("some.other.template");

    FHIRException e = assertThrows(FHIRException.class, () -> tm.loadTemplate("#template", dir.toString(), "x.y", false, false, false));

    assertTrue(e.getMessage().contains("doesn't name this template"), e.getMessage());
    assertFalse(Files.exists(marker));
    assertFalse(Files.exists(dir.resolve("fhir-settings.json")));
  }

  @ParameterizedTest
  @CsvSource({"'#template', '#template'", "my.template#1.0.0, my.template", "my.template, my.template",
      "https://github.com/org/repo, https://github.com/org/repo", "'#my-folder', '#my-folder'"})
  void aTemplateIsTrustedLocallyByItsIdWithoutAVersion(String template, String id) {
    assertEquals(id, TemplateManager.trustId(template));
  }

  /**
   * Where the settings can't be written (a container, a read-only home folder), -trust-template still trusts the
   * template for this run: it just can't be recorded
   */
  @Test
  void locallyTrustTemplateRunsTheScriptWhenTheSettingsCantBeWritten() throws IOException {
    Path marker = scriptedInPlaceTemplate();
    // a folder that can't be made, even by root: there's a file in the way
    write("not-a-folder", "x");
    ILoggingService logger = Mockito.mock(ILoggingService.class);
    TemplateManager tm = new TemplateManager(null, logger);
    tm.setSettingsPath(dir.resolve("not-a-folder").resolve("fhir-settings.json").toString());
    tm.setTrustTemplate("#template");

    tm.loadTemplate("#template", dir.toString(), "x.y", false, false, false);

    assertTrue(Files.exists(marker));
    Mockito.verify(logger).logMessage(Mockito.contains("is trusted for this run"));
  }

  /** a settings file that can't be read doesn't stop -trust-template either, and it isn't written over */
  @Test
  void locallyTrustTemplateRunsTheScriptWhenTheSettingsCantBeRead() throws IOException {
    Path marker = scriptedInPlaceTemplate();
    write("fhir-settings.json", "{ not json");
    TemplateManager tm = manager();
    tm.setTrustTemplate("#template");

    tm.loadTemplate("#template", dir.toString(), "x.y", false, false, false);

    assertTrue(Files.exists(marker));
    assertEquals("{ not json", Files.readString(dir.resolve("fhir-settings.json")));
  }

  @Test
  void trustIsAddedToTheSettingsWithoutLosingAnything() throws IOException {
    write("fhir-settings.json", "{\"npmPath\": \"/usr/bin/npm\", \"trustedTemplates\": [\"a.template\"]}");

    LocalTemplateTrust.addTrust(settingsFile(), "b.template");
    LocalTemplateTrust.addTrust(settingsFile(), "b.template");

    String json = Files.readString(dir.resolve("fhir-settings.json"));
    assertTrue(json.contains("/usr/bin/npm"), json);
    assertEquals(1, json.split("b.template", -1).length - 1, json);
    assertTrue(LocalTemplateTrust.isTrusted(settingsFile(), "a.template"));
    assertTrue(LocalTemplateTrust.isTrusted(settingsFile(), "b.template"));
    assertFalse(LocalTemplateTrust.isTrusted(settingsFile(), "c.template"));
  }

  @Test
  void anUnreadableSettingsFileIsNeverWrittenOver() throws IOException {
    write("fhir-settings.json", "{ not json");

    assertThrows(FHIRException.class, () -> LocalTemplateTrust.addTrust(settingsFile(), "b.template"));

    assertEquals("{ not json", Files.readString(dir.resolve("fhir-settings.json")));
  }

  @Test
  void aTemplateUsedInPlaceIsALocalFolder() throws IOException {
    write("template/config.json", "{}");
    TemplateManager tm = manager();
    tm.loadTemplate("#template", dir.toString(), "x.y", false, false, false);
    assertEquals(Collections.singletonList(folder("template")), tm.getLocalFolders());
  }

  @Test
  void aLocalTemplateWithABrokenPackageIsStillALocalFolder() throws IOException {
    write("my-template/package/package.json", "{ not json");
    TemplateManager tm = manager();
    assertBrokenJson(assertThrows(Exception.class, () -> tm.loadTemplate("#my-template", dir.toString(), "x.y", false, false, false)));
    assertEquals(Collections.singletonList(folder("my-template")), tm.getLocalFolders());
  }

  @Test
  void anOverrideWithABrokenPackageIsStillALocalFolder() throws IOException {
    write("override/package/package.json", "{ not json");
    write("ig.ini", "[IG]\nig = input/ig.xml\ntemplate = my.template#1.0.0\nmy.template = override\n");
    TemplateManager tm = manager();
    tm.loadTemplateOverrides(new IniFile(dir.resolve("ig.ini").toString()), dir.toString());
    assertBrokenJson(assertThrows(Exception.class, () -> tm.loadTemplate("my.template#1.0.0", dir.toString(), "x.y", false, false, false)));
    assertEquals(Collections.singletonList(folder("override")), tm.getLocalFolders());
  }

  /** the template folders ig.ini names, where they are (relative ones are relative to the IG's folder); none in auto-build mode, which ignores them */
  @Test
  void theOverrideFoldersAreTheFoldersIgIniNames(@TempDir Path other) throws IOException {
    write("ig.ini", "[IG]\nig = input/ig.xml\ntemplate = my.template#1.0.0\nmy.template = override\nother.template#1.0.0 = " + other + "\n");
    TemplateManager tm = manager();
    tm.loadTemplateOverrides(new IniFile(dir.resolve("ig.ini").toString()), dir.toString());
    assertEquals(List.of(folder("override"), other.toFile().getAbsolutePath()), tm.getTemplateOverrideFolders(false));
    assertTrue(tm.getTemplateOverrideFolders(true).isEmpty());
  }

  /**
   * a template folder ig.ini names outside the folders the build is limited to can't be read until it's allowed - as
   * PublisherIGLoader.allowTemplateOverrideFolders does with getTemplateOverrideFolders. Once it is, the load gets as far
   * as the (broken) package.json, so the folder was read
   */
  @Test
  void anOverrideOutsideTheAllowedFoldersCanBeReadOnceItIsAllowed(@TempDir Path other) throws IOException {
    Files.createDirectories(other.resolve("package"));
    Files.write(other.resolve("package/package.json"), "{ not json".getBytes(StandardCharsets.UTF_8));
    write("ig.ini", "[IG]\nig = input/ig.xml\ntemplate = my.template#1.0.0\nmy.template = " + other + "\n");
    List<String> allowed = List.of(dir.toString(), System.getProperty("java.io.tmpdir"));
    try {
      ManagedFileAccess.setAllowedPaths(allowed);
      TemplateManager tm = manager();
      tm.loadTemplateOverrides(new IniFile(dir.resolve("ig.ini").toString()), dir.toString());
      Exception e = assertThrows(Exception.class, () -> tm.loadTemplate("my.template#1.0.0", dir.toString(), "x.y", false, false, false));
      assertTrue(e.getMessage().contains("cannot be accessed by policy"), e.getMessage());

      List<String> withOverrides = new java.util.ArrayList<>(allowed);
      withOverrides.addAll(tm.getTemplateOverrideFolders(false));
      ManagedFileAccess.setAllowedPaths(withOverrides);
      assertBrokenJson(assertThrows(Exception.class, () -> tm.loadTemplate("my.template#1.0.0", dir.toString(), "x.y", false, false, false)));
    } finally {
      ManagedFileAccess.setAllowedPaths(Collections.emptyList());
    }
  }

  private static byte[] zip(String... names) throws IOException {
    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) {
      for (String n : names) {
        zip.putNextEntry(new java.util.zip.ZipEntry(n));
        zip.write("x".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return bytes.toByteArray();
  }

  /** a template zip (from github) with ".." in an entry name is refused before anything is unpacked */
  @ParameterizedTest
  @ValueSource(strings = {"repo-main/package/content/../scripts/ant.xml", "repo-main/../evil.txt", "repo-main/package/a..b"})
  void aTemplateZipWithDotDotInANameIsRefused(String name) throws IOException {
    byte[] zip = zip("repo-main/package/package.json", name);
    FHIRException e = assertThrows(FHIRException.class, () -> TemplateManager.checkZipEntryNames(zip, "test"));
    assertTrue(e.getMessage().contains(name));
  }

  @Test
  void aTemplateZipWithOrdinaryNamesIsFine() throws IOException {
    TemplateManager.checkZipEntryNames(zip("repo-main/package/package.json", "repo-main/package/content/assets/x.css", "repo-main/package/.github/CODEOWNERS"), "test");
  }

  @ParameterizedTest
  @CsvSource({
    "scripts/ant.xml, scripts/ant.xml",
    "content/../scripts/ant.xml, scripts/ant.xml",
    "./content/./x.html, content/x.html",
    "content/../../outside.txt, ",
    "../outside.txt, "
  })
  void filesAreCheckedWhereTheyAre(String name, String expected) {
    assertEquals(expected, TemplateManager.templatePath(dir.toString(), dir.toString() + "/" + name));
  }

  @ParameterizedTest
  @CsvSource({
    "scripts/ant.xml, scripts/ant.xml",
    "./scripts/ant.xml, scripts/ant.xml",
    "scripts\\Ant-HL7.xml, scripts/ant-hl7.xml",
    "scripts/x/../ant.xml, scripts/ant.xml"
  })
  void scriptNamesAreNormalised(String name, String expected) {
    assertEquals(expected, TemplateManager.scriptPath(name));
  }

  /** a file unpacked under a name with .. in it is checked as the file it actually is */
  @Test
  void aFileInScriptsUnderAnotherNameIsActiveContent() {
    String reason = manager().activeContentReason(dir.toString(), Collections.singletonList(dir + "/content/../scripts/ant.xml"), null, "test");
    assertTrue(reason != null && reason.contains("scripts/ant.xml"), reason);
  }

  /** a registered ant script outside scripts/ is found however either name is written */
  @Test
  void overwritingARegisteredScriptIsActiveContentHoweverItIsNamed() {
    TemplateManager tm = manager();
    org.hl7.fhir.utilities.json.model.JsonObject base = new org.hl7.fhir.utilities.json.model.JsonObject();
    base.add("script", "./build.xml");
    base.add("otherScripts", new org.hl7.fhir.utilities.json.model.JsonArray());
    tm.activeContentReason(dir.toString(), Collections.emptyList(), base, "base");
    assertEquals("Template contains a registered ant script",
        tm.activeContentReason(dir.toString(), Collections.singletonList(dir + "/content/../BUILD.xml"), null, "child"));
  }

  @Test
  void aFileOutsideTheTemplateFolderIsActiveContent() {
    String reason = manager().activeContentReason(dir.toString(), Collections.singletonList(dir + "/content/../../outside.txt"), null, "test");
    assertTrue(reason != null && reason.contains("outside the template folder"), reason);
  }

  @ParameterizedTest
  @CsvSource({
    "https://github.com/HL7/ig-template-base, true",
    "http://github.com/HL7/ig-template-base, true",
    "https://github.attacker.example/HL7/ig-template-base, false",
    "https://github.com.attacker.example/HL7/ig-template-base, false",
    "https://raw.githubusercontent.com/HL7/x, false"
  })
  void templatesCanOnlyBeLoadedFromGitHubDotCom(String url, boolean ok) {
    assertEquals(ok, TemplateManager.isGitHubUrl(url));
  }
}
