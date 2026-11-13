package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.PublisherUtils.LinkedSpecification;
import org.hl7.fhir.igtools.publisher.modules.IPublisherModule;
import org.hl7.fhir.igtools.publisher.modules.NullModule;
import org.hl7.fhir.model.core.ImplementationGuide;
import org.hl7.fhir.model.core.Requirements;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/**
 * Watch mode's html checks: when what the last check found is put back for the files that haven't changed, the
 * messages are those of checking everything again
 */
class HtmlCheckReplayTest {

  @TempDir
  Path dir;

  private final List<String> log = new ArrayList<>();
  private final ILoggingService logger = new ILoggingService() {
    @Override
    public void logMessage(String msg) {
      log.add(msg);
    }

    @Override
    public void logDebugMessage(LogCategory category, String msg) {
    }
  };
  // the same objects for each inspector, as in a page rebuild
  private final List<SpecMapManager> specs = new ArrayList<>();
  private final List<LinkedSpecification> linkSpecs = new ArrayList<>();
  private final List<FetchedFile> sources = new ArrayList<>();
  private final List<RelatedIG> relatedIGs = new ArrayList<>();
  private final Map<String, List<String>> trackedFragments = new HashMap<>();

  private final IWorkerContext context = Mockito.mock(IWorkerContext.class);
  private final IPublisherModule module = new NullModule();
  private final ImplementationGuide ig = new ImplementationGuide().setUrl("http://example.org/ig/ImplementationGuide/example.ig");

  private HTMLInspector inspector() throws IOException {
    return inspector("0.1.0");
  }

  private HTMLInspector inspector(String version) throws IOException {
    // the Requirements made from the narrative are those the IG has (there are no clauses)
    Mockito.when(context.fetchResource(Mockito.eq(Requirements.class), Mockito.anyString(), Mockito.any())).thenReturn(new Requirements());
    HTMLInspector res = new HTMLInspector(context, dir.resolve("out").toString(), specs, linkSpecs, logger, "http://example.org/ig",
        "example.ig", version, trackedFragments, sources, module, false, null, relatedIGs, false, new ArrayList<>(), null);
    res.setIg(ig);
    res.setExemptHtmlPatterns(new ArrayList<>());
    res.setReqFolder(dir.toString());
    return res;
  }

  // the body of each page, by its full path, as the last page() wrote it
  private final Map<String, String> bodies = new HashMap<>();

  private void page(String name, String body) throws IOException {
    // in a language folder, as the conformance clause reading needs a language (there's no worker context)
    Path f = dir.resolve("out/en").resolve(name);
    Files.createDirectories(f.getParent());
    Files.write(f, ("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\"><head><title>" + name + "</title></head><body>"
        + body + "</body></html>").getBytes(StandardCharsets.UTF_8));
    bodies.put(abs(name), body);
  }

  /** Writes the pages (full paths) again as page() last wrote them, undoing what a check changed in them, as Jekyll would */
  private Set<String> writeAgain(Iterable<String> files) throws IOException {
    for (String f : files) {
      page(Paths.get(f).getFileName().toString(), bodies.get(f));
    }
    return Collections.emptySet();
  }

  private List<String> check(HTMLInspector i) throws IOException {
    List<String> res = new ArrayList<>();
    for (ValidationMessage m : i.check(null, null)) {
      res.add(m.getLevel() + " " + m.getLocation() + " " + m.getMessage());
    }
    return res;
  }

  private String abs(String name) {
    return dir.resolve("out/en").resolve(name).toAbsolutePath().toString();
  }

  @Test
  void replayedChecksFindWhatCheckingEverythingFinds() throws IOException {
    page("a.html", "<h1>A</h1><p><a href=\"b.html#x\">to b</a> <a href=\"c.html\">to c</a></p>");
    page("b.html", "<h1>B</h1><p id=\"x\">here</p><p><a href=\"a.html#nowhere\">broken anchor</a></p>");
    page("c.html", "<h1>C</h1><p><a href=\"missing.html\">broken</a></p>");

    Set<String> rewritten = new HashSet<>();
    HTMLInspector.OutputRewriter rewriter = files -> {
      rewritten.addAll(files);
      return Collections.emptySet();
    };
    HTMLInspector first = inspector();
    first.recordChecks(null, null, rewriter);
    List<String> all = check(first);
    HtmlCheckRecords records = first.getRecords();
    assertNotNull(records, first.getRecordsNotUsable());
    assertTrue(all.stream().anyMatch(s -> s.contains("missing.html")), all.toString());

    // nothing written since (a file moved away and back, as watch mode does while the template's check runs, still
    // counts as the one checked): everything is put back
    Path a = Paths.get(abs("a.html"));
    Path aside = dir.resolve("a.aside");
    Files.move(a, aside);
    Files.move(aside, a);
    HTMLInspector second = inspector();
    second.recordChecks(records, new HashSet<>(), rewriter);
    assertEquals(all, check(second));
    assertTrue(log.contains("Rapido Mode: the html checks of 3 of 3 files are reused"), log.toString());
    records = second.getRecords();
    assertNotNull(records);

    // c fixed, and an anchor added to a that b links to: c is checked again, and b, which looked at a's anchors
    page("c.html", "<h1>C</h1><p><a href=\"a.html\">fine</a></p>");
    page("a.html", "<h1>A</h1><p id=\"nowhere\"><a href=\"b.html#x\">to b</a> <a href=\"c.html\">to c</a></p>");
    HTMLInspector third = inspector();
    Set<String> fresh = new HashSet<>();
    fresh.add(abs("c.html"));
    fresh.add(abs("a.html"));
    third.recordChecks(records, fresh, rewriter);
    List<String> replayed = check(third);
    assertTrue(log.contains("Rapido Mode: the html checks of 0 of 3 files are reused; not the others: 2 written again (e.g. en/a.html), "
        + "1 looked at something that changed (en/b.html looked up en/a.html, which has 1 more anchor (e.g. nowhere))"), log.toString());
    HTMLInspector everything = inspector();
    assertEquals(check(everything), replayed);
    assertTrue(replayed.stream().noneMatch(s -> s.contains("missing.html") || s.contains("nowhere")), replayed.toString());
  }

  @Test
  void aChangedAnchorOnlyMattersToTheLinksThatUseIt() throws IOException {
    // every page links to the home page (as the menu does), b to an anchor in it that stays, c to one that goes, and d to
    // one that's missing before and after (the message lists all the anchors)
    page("index.html", "<h1>Home</h1><h2>Old Heading</h2><p id=\"stays\">x</p><p><a href=\"b.html\">b</a></p>");
    page("b.html", "<h1>B</h1><p><a href=\"index.html\">home</a> <a href=\"index.html#stays\">stays</a></p>");
    page("c.html", "<h1>C</h1><p><a href=\"index.html\">home</a> <a href=\"index.html#old-heading\">heading</a></p>");
    page("d.html", "<h1>D</h1><p><a href=\"index.html\">home</a> <a href=\"index.html#nowhere\">nowhere</a></p>");
    page("e.html", "<h1>E</h1><p><a href=\"index.html\">home</a></p>");
    HTMLInspector.OutputRewriter rewriter = this::writeAgain;
    HTMLInspector first = inspector();
    first.recordChecks(null, null, rewriter);
    check(first);
    HtmlCheckRecords records = first.getRecords();
    assertNotNull(records, first.getRecordsNotUsable());

    page("index.html", "<h1>Home</h1><h2>New Heading</h2><p id=\"stays\">x</p><p><a href=\"b.html\">b</a></p>");
    HTMLInspector second = inspector();
    Set<String> fresh = new HashSet<>();
    fresh.add(abs("index.html"));
    second.recordChecks(records, fresh, rewriter);
    List<String> replayed = check(second);
    assertTrue(log.contains("Rapido Mode: the html checks of 2 of 5 files are reused; not the others: 1 written again (e.g. en/index.html), "
        + "2 looked at something that changed (en/c.html looked up en/index.html, which no longer has the anchor old-heading)"),
        log.toString());
    // checking everything as written by Jekyll
    writeAgain(new ArrayList<>(bodies.keySet()));
    assertEquals(check(inspector()), replayed);
    assertTrue(replayed.stream().anyMatch(s -> s.contains("index.html#old-heading") && s.contains("new-heading")), replayed.toString());
    assertTrue(replayed.stream().anyMatch(s -> s.contains("index.html#nowhere") && s.contains("new-heading")), replayed.toString());
  }

  @Test
  void theExemptPagesAreTheSameWhateverTheirPatternsOrderOrRepeats() throws IOException {
    // the comparison with the previous versions adds its patterns at each build, and watch mode keeps the list
    page("a.html", "<h1>A</h1><p><a href=\"b.html\">b</a></p>");
    page("b.html", "<h1>B</h1><p>text</p>");
    HTMLInspector.OutputRewriter rewriter = this::writeAgain;
    HTMLInspector first = inspector();
    first.setExemptHtmlPatterns(new ArrayList<>(Arrays.asList("comparison-v1\\.0.*", "comparison-v0\\.9.*")));
    first.recordChecks(null, null, rewriter);
    List<String> all = check(first);
    HtmlCheckRecords records = first.getRecords();
    assertNotNull(records, first.getRecordsNotUsable());

    HTMLInspector second = inspector();
    second.setExemptHtmlPatterns(new ArrayList<>(Arrays.asList("comparison-v0\\.9.*", "comparison-v1\\.0.*", "comparison-v0\\.9.*")));
    second.recordChecks(records, new HashSet<>(), rewriter);
    assertEquals(all, check(second));
    assertTrue(log.contains("Rapido Mode: the html checks of 2 of 2 files are reused"), log.toString());
  }

  @Test
  void withoutRecordsTheFilesTheLastCheckChangedArentKnown() throws IOException {
    page("a.html", "<h1>A</h1><p>text</p>");
    HTMLInspector i = inspector();
    i.recordChecks(null, new HashSet<>(), files -> Collections.emptySet());
    assertThrows(IOException.class, () -> i.check(null, null));
  }

  @Test
  void checkedDifferentlyTheFilesTheLastCheckChangedAreWrittenAgain() throws IOException {
    page("a.html", "<h1>A</h1><p>text</p>");
    page("b.html", "<h1>B</h1><p>text</p>");
    HTMLInspector first = inspector();
    first.recordChecks(null, null, files -> Collections.emptySet());
    check(first);
    HtmlCheckRecords records = first.getRecords();
    assertNotNull(records);
    HtmlCheckRecords.FileRecord a = records.files.get(abs("a.html"));
    assertNotNull(a, records.files.keySet().toString());
    a.modified = true;

    Set<String> rewritten = new HashSet<>();
    HTMLInspector other = inspector("0.2.0");
    other.recordChecks(records, new HashSet<>(), files -> {
      rewritten.addAll(files);
      return Collections.emptySet();
    });
    check(other);
    assertEquals(Collections.singleton(abs("a.html")), rewritten);
    assertTrue(log.contains("Rapido Mode: the html checks of 0 of 2 files are reused; not the others: the check is set up "
        + "differently (version)"), log.toString());
  }
}
