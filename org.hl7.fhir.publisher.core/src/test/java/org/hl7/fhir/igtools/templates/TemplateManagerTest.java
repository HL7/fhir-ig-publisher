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

  /** the user's settings allow ig.ini to override templates with local folders (template development) */
  private void allowOverrides() throws IOException {
    write("fhir-settings.json", "{\"" + LocalTemplateTrust.ALLOW_OVERRIDES + "\": true}");
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
      // locally, the user has said they trust it (by its folder)
      write("fhir-settings.json", "{\"trustedTemplates\": [\"" + folder("template").replace("\\", "\\\\") + "\"]}");
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

    // the template is named by its folder, not '#template', which is a different template in every IG
    assertTrue(e.getMessage().contains("The template '" + folder("template") + "' runs scripts"), e.getMessage());
    assertTrue(e.getMessage().contains("'-trust-template \"" + folder("template") + "\"'"), e.getMessage()); // quoted: it may have spaces
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
    // recorded by its folder, even if the user named it as the IG does
    assertTrue(LocalTemplateTrust.isTrusted(settingsFile(), folder("template")));
    assertEquals(trust.equals("all"), LocalTemplateTrust.isTrusted(settingsFile(), "#template"));
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
  @CsvSource({"my.template#1.0.0, my.template", "my.template, my.template",
      "https://github.com/org/repo, https://github.com/org/repo"})
  void aTemplateIsTrustedLocallyByItsIdWithoutAVersion(String template, String id) {
    assertEquals(id, TemplateManager.trustId(template, dir.toString()));
  }

  /** a template in the IG's folders is trusted by its absolute folder: '#template' is a different template in every IG */
  @ParameterizedTest
  @CsvSource({"'#template', template", "'#my-folder', my-folder", "'#sub/../my-folder', my-folder"})
  void aTemplateInTheIGsFoldersIsTrustedLocallyByItsAbsoluteFolder(String template, String folder) {
    assertEquals(folder(folder), TemplateManager.trustId(template, dir.toString()));
  }

  /** trusting another IG's #template doesn't trust this IG's #template */
  @Test
  void locallyTrustingTheSameNameInAnotherIGDoesNotRunThisOne() throws IOException {
    Path marker = scriptedInPlaceTemplate();
    LocalTemplateTrust.addTrust(settingsFile(), folder("other-ig/template"));
    LocalTemplateTrust.addTrust(settingsFile(), "#template"); // as recorded before trust was by folder

    assertThrows(FHIRException.class, () -> manager().loadTemplate("#template", dir.toString(), "x.y", false, false, false));

    assertFalse(Files.exists(marker));
  }

  /** -trust-template with the folder that the refusal names, as it tells the user to */
  @Test
  void locallyTrustTemplateByTheFolderTheRefusalNames() throws IOException {
    Path marker = scriptedInPlaceTemplate();
    TemplateManager tm = manager();
    tm.setTrustTemplate(folder("template"));

    tm.loadTemplate("#template", dir.toString(), "x.y", false, false, false);

    assertTrue(Files.exists(marker));
    assertTrue(LocalTemplateTrust.isTrusted(settingsFile(), folder("template")));
  }

  /** an absolute path is kept as it is (normalised): a # in a folder name isn't a version */
  @Test
  void anAbsolutePathIsTrustedAsItIsEvenWithAHashInIt() {
    assertEquals(folder("c#-igs/ig/template"), TemplateManager.trustId(folder("c#-igs/ig/sub/../template"), dir.toString()));
  }

  /** a template in a local folder that the IG names by a relative path (template = my-template, not #my-template) */
  private void localFolderTemplateWithActiveContent() throws IOException {
    write("my-template/package/package.json", "{\"name\": \"my.template\", \"version\": \"1.0.0\", \"type\": \"fhir.template\"}");
    write("my-template/package/scripts/groupings.txt", "x");
    write("my-template/package/$root/config.json", "{}");
  }

  /**
   * a template in a local folder named by a relative path is trusted by its absolute folder, as #folder is: the same
   * relative name is a different template in every IG, so trusting it by that name (as was recorded before) doesn't
   * trust this one
   */
  @Test
  void aLocalFolderTemplateIsTrustedLocallyByItsAbsoluteFolder() throws IOException {
    localFolderTemplateWithActiveContent();
    LocalTemplateTrust.addTrust(settingsFile(), "my-template");

    FHIRException e = assertThrows(FHIRException.class, () -> manager().loadTemplate("my-template", dir.toString(), "x.y", false, false, false));

    assertTrue(e.getMessage().contains("The template '" + folder("my-template") + "' runs scripts"), e.getMessage());
    assertTrue(e.getMessage().contains("'-trust-template \"" + folder("my-template") + "\"'"), e.getMessage());
  }

  /** -trust-template can name a local folder template as the IG does, or by its folder: either way, it's recorded by its folder */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void aLocalFolderTemplateCanBeTrustedAsTheIGNamesItOrByItsFolder(boolean byFolder) throws IOException {
    localFolderTemplateWithActiveContent();
    TemplateManager tm = manager();
    tm.setTrustTemplate(byFolder ? folder("my-template") : "my-template");

    tm.loadTemplate("my-template", dir.toString(), "x.y", false, false, false);

    assertTrue(LocalTemplateTrust.isTrusted(settingsFile(), folder("my-template")));
    assertFalse(LocalTemplateTrust.isTrusted(settingsFile(), "my-template"));
    // and the next run doesn't need it
    manager().loadTemplate("my-template", dir.toString(), "x.y", false, false, false);
  }

  /**
   * Everything that makes the template active content is reported together: content in scripts/ as well as the file
   * extensions. A pre-compressed copy is judged as the file it's a copy of, and .DS_Store is ignored
   */
  @Test
  void scriptsContentAndExtensionsAreAllReported() throws IOException {
    write("template/config.json", "{}");
    write("template/.DS_Store", "x");
    write("template/content/.DS_Store", "x");
    write("template/content/assets/css/x.css.gz", "x");
    write("template/content/assets/css/x.css.br", "x");
    write("template/content/assets/js/x.js", "var x;");
    write("template/content/assets/js/x.js.gz", "x");
    write("template/scripts/onGenerate.final.xslt", "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"/>");

    Template template = manager().loadTemplate("#template", dir.toString(), "x.y", true, false, false);

    assertFalse(template.canExecute());
    assertTrue(template.getUntrustedReason().contains("(reason: Template has content in scripts/ (scripts/onGenerate.final.xslt); Template has file extensions: [.js, .js.gz])"),
        template.getUntrustedReason());
  }

  /** a template whose only extras are .DS_Store files and compressed copies of harmless files has no active content */
  @Test
  void dsStoreAndCompressedHarmlessFilesAreNotActiveContent() throws IOException {
    write("template/config.json", "{}");
    write("template/.DS_Store", "x");
    write("template/content/assets/css/x.css.gz", "x");
    write("template/content/assets/css/x.css.br", "x");
    write("template/content/my.folder/notes.txt", "x");

    Template template = manager().loadTemplate("#template", dir.toString(), "x.y", true, false, false);

    assertTrue(template.canExecute(), template.getUntrustedReason());
  }

  /** every file extension that makes the template active content is reported, not just the first one found */
  @Test
  void allTheActiveFileExtensionsAreReported() throws IOException {
    write("template/config.json", "{}");
    write("template/content/assets/js/x.js", "var x;");
    write("template/content/assets/transform.xslt", "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"/>");

    Template template = manager().loadTemplate("#template", dir.toString(), "x.y", true, false, false);

    assertFalse(template.canExecute());
    assertTrue(template.getUntrustedReason().contains("Template has file extensions: [.js, .xslt]"), template.getUntrustedReason());
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
    allowOverrides();
    TemplateManager tm = manager();
    tm.loadTemplateOverrides(new IniFile(dir.resolve("ig.ini").toString()), dir.toString());
    assertBrokenJson(assertThrows(Exception.class, () -> tm.loadTemplate("my.template#1.0.0", dir.toString(), "x.y", false, false, false)));
    assertEquals(Collections.singletonList(folder("override")), tm.getLocalFolders());
  }

  /** the template folders ig.ini names, where they are (relative ones are relative to the IG's folder); none in auto-build mode, which ignores them */
  @Test
  void theOverrideFoldersAreTheFoldersIgIniNames(@TempDir Path other) throws IOException {
    write("ig.ini", "[IG]\nig = input/ig.xml\ntemplate = my.template#1.0.0\nmy.template = override\nother.template#1.0.0 = " + other + "\n");
    allowOverrides();
    TemplateManager tm = manager();
    tm.loadTemplateOverrides(new IniFile(dir.resolve("ig.ini").toString()), dir.toString());
    assertEquals(List.of(folder("override"), other.toFile().getAbsolutePath()), tm.getTemplateOverrideFolders(false));
    assertTrue(tm.getTemplateOverrideFolders(true).isEmpty());
  }

  /**
   * a template folder ig.ini names outside the folders the build is limited to can't be read until it's allowed - as
   * PublisherIGLoader.allowTemplateOverrideFolders does with getTemplateOverrideFolders. Once it is, the load gets as far
   * as the (broken) package.json, so the folder was read. The IG and the template are side by side in the test folder,
   * and only the IG's folder is allowed (not the temp folder, which the test folder is in)
   */
  @Test
  void anOverrideOutsideTheAllowedFoldersCanBeReadOnceItIsAllowed() throws IOException {
    write("template-src/package/package.json", "{ not json");
    write("ig/ig.ini", "[IG]\nig = input/ig.xml\ntemplate = my.template#1.0.0\nmy.template = " + folder("template-src") + "\n");
    String ig = folder("ig");
    allowOverrides();
    // the overrides are read (with the settings, in the user's .fhir folder) before the build is limited to the IG's folders
    TemplateManager tm = manager();
    tm.loadTemplateOverrides(new IniFile(dir.resolve("ig/ig.ini").toString()), ig);
    try {
      ManagedFileAccess.setAllowedPaths(List.of(ig));
      Exception e = assertThrows(Exception.class, () -> tm.loadTemplate("my.template#1.0.0", ig, "x.y", false, false, false));
      assertTrue(e.getMessage().contains("cannot be accessed by policy"), e.getMessage());

      List<String> withOverrides = new java.util.ArrayList<>(List.of(ig));
      withOverrides.addAll(tm.getTemplateOverrideFolders(false));
      ManagedFileAccess.setAllowedPaths(withOverrides);
      assertBrokenJson(assertThrows(Exception.class, () -> tm.loadTemplate("my.template#1.0.0", ig, "x.y", false, false, false)));
    } finally {
      ManagedFileAccess.setAllowedPaths(Collections.emptyList());
    }
  }

  /** ig.ini comes with the IG: its template overrides are only used if the user's own settings say so */
  @ParameterizedTest
  @ValueSource(strings = {"", "{}", "{\"allow-template-overrides\": false}", "{ not json"})
  void templateOverridesAreIgnoredUnlessTheSettingsAllowThem(String settings) throws IOException {
    write("ig.ini", "[IG]\nig = input/ig.xml\ntemplate = my.template#1.0.0\nmy.template = override\n");
    if (!settings.isEmpty()) {
      write("fhir-settings.json", settings);
    }
    TemplateManager tm = manager();

    tm.loadTemplateOverrides(new IniFile(dir.resolve("ig.ini").toString()), dir.toString());

    assertTrue(tm.getTemplateOverrideFolders(false).isEmpty());
  }

  /** a template loaded from an override is the developer's own: the active content rules don't apply to it */
  @Test
  void anOverriddenTemplateWithActiveContentRunsWithoutBeingTrusted() throws IOException {
    write("override/package/package.json", "{\"name\": \"my.template\", \"version\": \"1.0.0\", \"type\": \"fhir.template\"}");
    write("override/package/$root/config.json", "{}");
    write("override/package/scripts/groupings.txt", "x");
    write("ig.ini", "[IG]\nig = input/ig.xml\ntemplate = my.template#1.0.0\nmy.template = override\n");
    allowOverrides();
    TemplateManager tm = manager();
    tm.loadTemplateOverrides(new IniFile(dir.resolve("ig.ini").toString()), dir.toString());

    Template template = tm.loadTemplate("my.template#1.0.0", dir.toString(), "x.y", false, false, false);

    assertTrue(template.canExecute());
    assertFalse(LocalTemplateTrust.isTrusted(settingsFile(), "my.template"));
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
