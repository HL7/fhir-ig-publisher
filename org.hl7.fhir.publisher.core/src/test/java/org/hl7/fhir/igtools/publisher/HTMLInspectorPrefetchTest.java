package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.igtools.publisher.modules.IPublisherModule;
import org.hl7.fhir.model.core.ImplementationGuide;
import org.hl7.fhir.model.core.Requirements;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Running the HTML check with extra threads, which parse pages ahead, has to give the same messages and leave the
 * same files as running it with none, which is the default and doesn't start any.
 */
class HTMLInspectorPrefetchTest {

  @TempDir
  Path dir;

  private static final String BOX = "<!--ReleaseHeader--><p id=\"publish-box\">Publish Box goes here</p><!--EndReleaseHeader-->";

  private static String page(String lang, String body) {
    return "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"" + lang + "\"><head><title>t</title></head><body>"
        + body + "</body></html>";
  }

  private void writeSite(Path root) throws IOException {
    Files.createDirectories(root.resolve("de"));
    for (int i = 0; i < 12; i++) {
      write(root.resolve("p" + i + ".html"), page("en", BOX + "<h1>Page " + i + "</h1><p>See <a href=\"p" + ((i + 1) % 12)
          + ".html\">next</a> and <a href=\"missing" + i + ".html\">gone</a>, café 中文</p>"));
    }
    write(root.resolve("index.html"), page("en", BOX + "<h1>Home</h1><p><a href=\"p0.html\">start</a></p>"));
    write(root.resolve("de/index.html"), page("de", BOX + "<h1>Start</h1><p><a href=\"../p1.html\">eins</a></p>"));
    write(root.resolve("nobox.html"), page("en", "<h1>No box</h1><p>text</p>"));
    write(root.resolve("broken.html"), page("en", BOX + "<h1>Broken</h1><p>unclosed <b>bold<br>line</p>"));
    write(root.resolve("headings.html"), page("en", BOX + "<h1>A</h1><h1>B</h1><h4>skipped</h4>"));
    write(root.resolve("dup.html"), page("en", BOX + "<h1>Dup</h1><p id=\"x\">a</p><p id=\"x\">b</p>"));
    write(root.resolve("pic.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>s</text></svg>");
    write(root.resolve("data.json"), "{\"resourceType\":\"Patient\"}");
    write(root.resolve("bom.html"), "\ufeff" + page("en", BOX + "<h1>Bom</h1>"));
    write(root.resolve("notxml.html"), "<html><body><p>bad & <<</body>");
  }

  private static void write(Path p, String content) throws IOException {
    Files.writeString(p, content, StandardCharsets.UTF_8);
  }

  private static class Run {
    final List<String> messages;
    final Map<String, String> files;
    final boolean sawPrefetchThread;

    Run(List<String> messages, Map<String, String> files, boolean sawPrefetchThread) {
      this.messages = messages;
      this.files = files;
      this.sawPrefetchThread = sawPrefetchThread;
    }
  }

  private static Set<Thread> prefetchThreads() {
    return Thread.getAllStackTraces().keySet().stream().filter(t -> t.isAlive() && t.getName().startsWith("xhtml-prefetch-"))
        .collect(Collectors.toSet());
  }

  private Run run(String name, int threads) throws IOException {
    Path root = dir.resolve(name);
    Files.createDirectories(root);
    writeSite(root);
    AtomicBoolean saw = new AtomicBoolean();
    // other tests' workers may still be on their way out
    Set<Thread> before = prefetchThreads();
    ILoggingService log = mock(ILoggingService.class);
    doAnswer(inv -> {
      if (!before.containsAll(prefetchThreads())) {
        saw.set(true);
      }
      return null;
    }).when(log).logDebugMessage(any(), anyString());
    IWorkerContext context = mock(IWorkerContext.class);
    when(context.getLocale()).thenReturn(Locale.ENGLISH);
    // an existing Requirements resource with nothing in it, which matches the (clause free) pages, so none is written
    when(context.fetchResource(eq(Requirements.class), anyString(), any())).thenAnswer(inv -> new Requirements());
    HTMLInspector inspector = new HTMLInspector(context, root.toString(), new ArrayList<>(), new ArrayList<>(), log,
        "http://hl7.org/fhir/example", "hl7.fhir.example", "0.1.0", new HashMap<>(), new ArrayList<>(), mock(IPublisherModule.class), true, null,
        new ArrayList<>(), false, List.of("en", "de"), null);
    ImplementationGuide ig = new ImplementationGuide();
    ig.setUrl("http://hl7.org/fhir/example/ImplementationGuide/example");
    inspector.setIg(ig);
    inspector.setExemptHtmlPatterns(new ArrayList<>());
    inspector.setReqFolder(Files.createDirectories(dir.resolve(name + "-req")).toString());
    inspector.setDefaultLang("en");
    inspector.setThreads(threads);
    Map<String, String> langs = new HashMap<>();
    langs.put("de", "Entwurf");
    List<ValidationMessage> found = inspector.check("Draft", langs);
    String prefix = root.toString();
    List<String> messages = found.stream()
        .map(m -> m.getLevel() + " " + m.getMessageId() + " " + m.getLocation() + " " + m.getMessage())
        .map(s -> s.replace(prefix, "ROOT"))
        .sorted()
        .collect(Collectors.toList());
    Map<String, String> files = new TreeMap<>();
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path p : paths.filter(Files::isRegularFile).collect(Collectors.toList())) {
        files.put(root.relativize(p).toString(), Files.readString(p, StandardCharsets.UTF_8));
      }
    }
    return new Run(messages, files, saw.get());
  }

  @Test
  void extraThreadsGiveTheSameResultAsNone() throws IOException {
    Run none = run("none", 0);
    Run three = run("three", 3);
    assertFalse(none.sawPrefetchThread, "no extra threads, the default, must not start prefetch threads");
    if (Runtime.getRuntime().availableProcessors() >= 2) {
      assertTrue(three.sawPrefetchThread, "extra threads should have been used");
    }
    assertFalse(none.messages.isEmpty());
    assertEquals(none.messages, three.messages);
    assertEquals(none.files, three.files);
    assertTrue(none.files.get("index.html").contains("Draft"), "the publish box should have been filled in");
    assertTrue(none.files.get("de" + File.separator + "index.html").contains("Entwurf"), "the publish box should be in the page's language");
  }

  @Test
  void theDefaultIsNoExtraThreads() throws IOException {
    HTMLInspector inspector = new HTMLInspector(null, "root", new ArrayList<>(), new ArrayList<>(), null, "http://example.org/ig",
        "example.ig", "0.1.0", null, new ArrayList<>(), null, false, null, new ArrayList<>(), false, new ArrayList<>(), null);
    assertEquals(0, inspector.getThreads());
  }
}
