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

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.model.core.ImplementationGuide;
import org.hl7.fhir.utilities.IniFile;
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

  // core-tem-unwind @ParameterizedTest
// core-tem-unwind   @CsvSource({"#template, #template", "my.template#1.0.0, my.template", "my.template, my.template",
// core-tem-unwind       "https://github.com/org/repo, https://github.com/org/repo", "#my-folder, #my-folder"})
// core-tem-unwind   void aTemplateIsTrustedLocallyByItsIdWithoutAVersion(String template, String id) {
    // core-tem-unwind assertEquals(id, TemplateManager.trustId(template));
// core-tem-unwind   }

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
}
