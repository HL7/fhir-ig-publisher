package org.hl7.fhir.igtools.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.model.core.ImplementationGuide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TemplateScriptTest {

  @TempDir
  Path dir;

  /**
   * On Windows a file that is still open can't be deleted, so streams left open by the onLoad script run
   * stop the next template load from clearing the template folder.
   */
  @Test
  void onLoadScriptLeavesItsFilesDeletable() throws Exception {
    ImplementationGuide updated = runOnLoad("xml");

    assertEquals("Test", updated.getName());
    List<Path> files = scriptFiles();
    assertEquals(List.of("onLoad-ig-updated.xml", "onLoad-ig-working.json", "onLoad-ig-working.xml"),
        files.stream().map(f -> f.getFileName().toString()).collect(Collectors.toList()));
    for (Path f : files) {
      assertTrue(f.toFile().delete(), "couldn't delete " + f.getFileName());
    }
  }

  /**
   * Linux doesn't block deleting an open file, so look for the open handles in /proc/self/fd instead.
   */
  @ParameterizedTest
  @ValueSource(strings = {"xml", "json"})
  @EnabledOnOs(OS.LINUX)
  void onLoadScriptLeavesNoFilesOpen(String format) throws Exception {
    Path probe = Files.writeString(dir.resolve("probe.txt"), "probe");
    try (FileInputStream in = new FileInputStream(probe.toFile())) {
      assertEquals(List.of("probe.txt"), filesOpenUnder(dir));
    }
    assertEquals(List.of(), filesOpenUnder(dir));

    runOnLoad(format);

    assertEquals(List.of(), filesOpenUnder(dir));
  }

  @Test
  void onLoadScriptCanWriteOnlyJson() throws Exception {
    ImplementationGuide updated = runOnLoad("json");

    assertEquals("Test", updated.getName());
    assertTrue(Files.notExists(dir.resolve("template/onLoad-ig-updated.xml")));
  }

  @Test
  void onLoadScriptWritingOnlyJsonIgnoresAnUpdatedXmlFromAnEarlierRun() throws Exception {
    Files.createDirectories(dir.resolve("template"));
    Files.writeString(dir.resolve("template/onLoad-ig-updated.xml"),
        "<ImplementationGuide xmlns=\"http://hl7.org/fhir\"><name value=\"Stale\"/></ImplementationGuide>");

    ImplementationGuide updated = runOnLoad("json");

    assertEquals("Test", updated.getName());
  }

  @Test
  void onGenerateScriptCanWriteOnlyJson() throws Exception {
    Template template = template("onGenerate",
        "<property name=\"onGenerate.files\" value=\"\"/>\n"
        + "<copy file=\"${onGenerate.ig.source}json\" tofile=\"${onGenerate.ig.dest}json\">\n"
        + "  <filterchain><tokenfilter><replacestring from=\"Original\" to=\"Changed\"/></tokenfilter></filterchain>\n"
        + "</copy>");
    ImplementationGuide ig = ig();
    ig.getDefinition().addGrouping().setName("Original");

    template.beforeGenerateEvent(null, ig, Files.createDirectory(dir.resolve("temp")).toString(), new HashSet<>(), new ArrayList<>(), List.of());

    assertEquals("Changed", ig.getDefinition().getGroupingFirstRep().getName());
    assertTrue(Files.notExists(dir.resolve("template/onGenerate-ig-updated.xml")));
  }

  /**
   * Ant runs the tasks that are outside any target while it parses the build file. The script of a template that
   * isn't trusted is never parsed, so they don't run (the trusted case shows that the test would notice if they did)
   */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void topLevelTasksRunOnlyForATrustedTemplate(boolean trusted) throws Exception {
    Path marker = dir.resolve("marker.txt");

    template("onLoad", "<echo message=\"onLoad\"/>", "<touch file=\"" + marker + "\"/>", trusted);

    assertEquals(trusted, Files.exists(marker));
  }

  @Test
  void anUntrustedTemplateSaysWhyItsOnLoadScriptCantRun() throws Exception {
    Template template = template("onLoad", "<echo message=\"onLoad\"/>", "", false);

    FHIRException e = assertThrows(FHIRException.class, () -> template.onLoadEvent(ig(), new HashMap<>()));

    assertTrue(e.getMessage().contains("is not trusted (reason: test reason)"), e.getMessage());
  }

  @Test
  void anUntrustedTemplateSaysWhyItsOnGenerateScriptCantRun() throws Exception {
    Template template = template("onGenerate", "<echo message=\"onGenerate\"/>", "", false);
    String temp = Files.createDirectory(dir.resolve("temp")).toString();

    FHIRException e = assertThrows(FHIRException.class,
        () -> template.beforeGenerateEvent(null, ig(), temp, new HashSet<>(), new ArrayList<>(), List.of()));

    assertTrue(e.getMessage().contains("is not trusted (reason: test reason)"), e.getMessage());
  }

  private ImplementationGuide runOnLoad(String format) throws Exception {
    Template template = template("onLoad", "<copy file=\"${onLoad.ig.source}" + format + "\" tofile=\"${onLoad.ig.dest}" + format + "\"/>");
    ImplementationGuide updated = template.onLoadEvent(ig(), new HashMap<>());
    assertNotNull(updated);
    return updated;
  }

  /**
   * A template whose script has a single target, named after the event it runs on.
   */
  private Template template(String event, String tasks) throws IOException {
    return template(event, tasks, "", true);
  }

  /**
   * As above, with topLevel tasks outside the target, for a template that is or isn't trusted
   */
  private Template template(String event, String tasks, String topLevel, boolean trusted) throws IOException {
    Path templateDir = Files.createDirectories(dir.resolve("template"));
    Files.writeString(templateDir.resolve("config.json"), "{\"script\": \"scripts/ant.xml\", \"targets\": {\"" + event + "\": \"" + event + "\"}}");
    Files.createDirectory(templateDir.resolve("scripts"));
    Files.writeString(templateDir.resolve("scripts/ant.xml"),
        "<project name=\"test\">\n"
        + topLevel + "\n"
        + "  <target name=\"" + event + "\">\n"
        + tasks + "\n"
        + "  </target>\n"
        + "</project>\n");
    return trusted
        ? new Template(dir.toString(), true, null, null, false, false, false)
        : new Template(dir.toString(), false, "test.template#1.0.0", "test reason", false, false, false);
  }

  private static ImplementationGuide ig() {
    ImplementationGuide ig = new ImplementationGuide();
    ig.setId("test");
    ig.setUrl("http://example.org/ImplementationGuide/test");
    ig.setName("Test");
    return ig;
  }

  private List<Path> scriptFiles() throws IOException {
    try (Stream<Path> files = Files.list(dir.resolve("template"))) {
      return files.filter(f -> f.getFileName().toString().startsWith("onLoad-ig-")).sorted().collect(Collectors.toList());
    }
  }

  private static List<String> filesOpenUnder(Path folder) throws IOException {
    String prefix = folder.toRealPath().toString() + File.separator;
    List<String> open = new ArrayList<>();
    try (Stream<Path> fds = Files.list(Path.of("/proc/self/fd"))) {
      for (Path fd : (Iterable<Path>) fds::iterator) {
        try {
          String target = Files.readSymbolicLink(fd).toString();
          if (target.startsWith(prefix)) {
            open.add(target.substring(prefix.length()));
          }
        } catch (NoSuchFileException e) {
          // the descriptor was closed while we were looking
        }
      }
    }
    return open;
  }
}
