package org.hl7.fhir.igtools.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

  private TemplateManager manager() {
    return new TemplateManager(null, Mockito.mock(ILoggingService.class));
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

  private static final String SCRIPT = "\"script\": \"scripts/ant.xml\", \"targets\": {\"onGenerate\": \"onGenerate\"}, \"otherScripts\": []";
  private static final String UNDECLARED = "the template doesn't declare what its scripts read (script-inputs in its config.json)";

  /** a template in a local folder (an ig.ini override), with its config.json (if not null) and files */
  private void template(String name, String base, String config, String... files) throws IOException {
    write(name + "/package/package.json", "{\"name\": \"" + name + "\", \"version\": \"1.0.0\", \"type\": \"fhir.template\""
        + (base == null ? "" : ", \"base\": \"" + base + "\", \"dependencies\": {\"" + base + "\": \"1.0.0\"}") + "}");
    if (config != null) {
      write(name + "/config.json", config);
    }
    for (String f : files) {
      write(name + "/" + f, "<project/>");
    }
  }

  private Template load(String name) throws IOException {
    StringBuilder ini = new StringBuilder("[IG]\nig = input/ig.xml\ntemplate = " + name + "#1.0.0\n");
    for (String t : new String[] {"base.t", "middle.t", "child.t"}) {
      ini.append(t).append(" = ").append(t).append("\n");
    }
    write("ig.ini", ini.toString());
    TemplateManager tm = manager();
    tm.loadTemplateOverrides(new IniFile(dir.resolve("ig.ini").toString()), dir.toString());
    return tm.loadTemplate(name + "#1.0.0", dir.toString(), "x.y", false, false, false);
  }

  /** a template whose base template declares that its scripts read input/data */
  private Template childOf(String childConfig, String... childFiles) throws IOException {
    template("base.t", null, "{" + SCRIPT + ", \"script-inputs\": [\"input/data\"]}", "scripts/ant.xml");
    template("child.t", "base.t", childConfig, childFiles);
    return load("child.t");
  }

  private String inIg(String path) {
    return dir.resolve(path).toFile().getAbsolutePath();
  }

  @Test
  void aChildWithoutScriptsKeepsTheDeclaration() throws IOException {
    Template t = childOf(null, "includes/fragment.xml");
    assertNull(t.whyNoPageRebuild());
    assertEquals(Collections.singletonList(inIg("input/data")), t.getScriptInputs());
  }

  @Test
  void aChildThatNamesAScriptAndDeclaresAddsToTheDeclaration() throws IOException {
    Template t = childOf("{\"script\": \"scripts/child.xml\", \"otherScripts\": [], \"script-inputs\": [\"package-list.json\"]}", "scripts/child.xml");
    assertNull(t.whyNoPageRebuild());
    assertEquals(Arrays.asList(inIg("input/data"), inIg("package-list.json")), t.getScriptInputs());
  }

  @Test
  void aChildThatNamesAScriptWithoutDeclaringDropsTheDeclaration() throws IOException {
    assertEquals(UNDECLARED, childOf("{\"script\": \"scripts/child.xml\", \"otherScripts\": []}", "scripts/child.xml").whyNoPageRebuild());
  }

  @Test
  void aChildThatNamesTargetsWithoutDeclaringDropsTheDeclaration() throws IOException {
    assertEquals(UNDECLARED, childOf("{\"targets\": {\"onCheck\": \"onCheck\"}}").whyNoPageRebuild());
  }

  @Test
  void aChildThatNamesOtherScriptsWithoutDeclaringDropsTheDeclaration() throws IOException {
    assertEquals(UNDECLARED, childOf("{\"otherScripts\": [\"scripts/more.xml\"]}").whyNoPageRebuild());
  }

  @Test
  void aChildThatOverwritesTheAntScriptDropsTheDeclaration() throws IOException {
    assertEquals(UNDECLARED, childOf(null, "scripts/ant.xml").whyNoPageRebuild());
  }

  @Test
  void anAntScriptIsFoundWithEitherSeparator() {
    Set<String> antScripts = Collections.singleton("scripts/ant.xml");
    assertTrue(TemplateManager.isAntScript(antScripts, "scripts\\ant.xml"));
    assertTrue(TemplateManager.isAntScript(antScripts, "scripts/ant.xml"));
    assertFalse(TemplateManager.isAntScript(antScripts, "scripts/other.xml"));
  }

  @Test
  void aChildThatShipsFilesThatCouldBeScriptsDropsTheDeclaration() throws IOException {
    assertEquals(UNDECLARED, childOf(null, "scripts/gen.py").whyNoPageRebuild());
  }

  @Test
  void aTemplateAfterADropCanDeclareAgain() throws IOException {
    template("base.t", null, "{" + SCRIPT + ", \"script-inputs\": [\"input/data\"]}", "scripts/ant.xml");
    template("middle.t", "base.t", "{\"script\": \"scripts/middle.xml\", \"otherScripts\": []}", "scripts/middle.xml");
    template("child.t", "middle.t", "{\"script-inputs\": [\"package-list.json\"]}");
    Template t = load("child.t");
    assertNull(t.whyNoPageRebuild());
    assertEquals(Collections.singletonList(inIg("package-list.json")), t.getScriptInputs());
  }

  @Test
  void theConfigIsAsItWasWhenNoTemplateDeclares() throws IOException {
    String config = "{" + SCRIPT + "}";
    template("base.t", null, config, "scripts/ant.xml");
    template("child.t", "base.t", null, "scripts/ant.xml");
    assertEquals(UNDECLARED, load("child.t").whyNoPageRebuild());
    assertEquals(config, new String(Files.readAllBytes(dir.resolve("template/config.json")), StandardCharsets.UTF_8));
  }
}
