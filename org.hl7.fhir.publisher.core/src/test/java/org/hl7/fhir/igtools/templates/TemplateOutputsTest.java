package org.hl7.fhir.igtools.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.hl7.fhir.igtools.publisher.WatchSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the template says it writes to its own folder (Template.OutputListener): watch mode takes those files as the
 * build's output for a template that is used in place, and only those
 */
class TemplateOutputsTest {

  @TempDir
  Path dir;

  private void write(String path, String content) throws IOException {
    Path p = dir.resolve(path);
    Files.createDirectories(p.getParent());
    Files.write(p, content.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void aScriptsOutputIsTheFilesItIsGivenAndGathersItsOutcomesIn() {
    Predicate<String> p = Template.scriptOutputs("onGenerate");
    for (String s : Arrays.asList("onGenerate-validation.json", "onGenerate-validation.xml", "onGenerate-ig-working.json",
        "onGenerate-ig-working.xml", "onGenerate-ig-working.1.xml", "onGenerate-ig-working.2.xml", "onGenerate-ig-updated.xml",
        "onGenerate-ig-updated.json", "ongenerate-validation-igqa.json", "versions.txt")) {
      assertTrue(p.test(s), s);
    }
    for (String s : Arrays.asList("onLoad-validation.json", "onLoad-ig-working.json", "config.json", "includes/fragment-header.html",
        "content/onGenerate-validation.json", "scripts/ant.xml", "onGenerate-validation.html", "layouts/versions.txt")) {
      assertFalse(p.test(s), s);
    }
  }

  @Test
  void theTranslationsOutputIsTheTranslationFiles() {
    Predicate<String> p = Template.translationOutputs();
    for (String s : Arrays.asList("translations/base.json", "translations/base.xml", "translations/base-nl.po")) {
      assertTrue(p.test(s), s);
    }
    for (String s : Arrays.asList("translations/old/base-nl.po", "includes/base.json", "base-nl.po", "translations/README.md")) {
      assertFalse(p.test(s), s);
    }
  }

  @Test
  void theTranslationsAnInPlaceTemplateWritesAreNotAChange() throws IOException {
    write("ig.ini", "[IG]\ntemplate = #template\n");
    write("template/config.json", "{}");
    write("template/translations/base.json", "{\"en\":{\"a\":\"A\",\"b\":\"B\"},\"nl\":{\"a\":\"Anl\"}}");
    WatchSession s = new WatchSession(dir, Collections.singletonList(dir.resolve("ig.ini")));
    List<String> log = new ArrayList<>();
    s.setLog(log::add);
    s.startBuild();
    s.watchTemplateFolders(Collections.singletonList(dir.resolve("template").toString()));
    Template t = new Template(dir.toString(), true, null, null, false, false, false);
    t.setOutputListener(s.templateOutputs());

    t.processTemplateTranslations("en", Collections.singletonList("nl"));
    assertTrue(Files.exists(dir.resolve("template/translations/base-nl.po")));
    assertTrue(Files.exists(dir.resolve("template/translations/base.xml")));
    assertTrue(s.changesSinceBuildStart().isEmpty(), "what it wrote is the build's");
    assertEquals(1, log.size());
    assertTrue(log.get(0).startsWith("Watch mode: translations/base-nl.po, translations/base.json, translations/base.xml written"), log.get(0));

    // the next build does it again
    s.startBuild();
    t.processTemplateTranslations("en", Collections.singletonList("nl"));
    assertTrue(s.changesSinceBuildStart().isEmpty());

    // an edit of a template file is still a change
    write("template/includes/fragment-header.html", "<header/>");
    assertEquals(1, s.changesSinceBuildStart().getAdded().size());
  }
}
