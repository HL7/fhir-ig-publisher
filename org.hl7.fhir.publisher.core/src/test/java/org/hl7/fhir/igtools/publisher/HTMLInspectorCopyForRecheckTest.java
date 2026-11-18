package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.hl7.fhir.igtools.publisher.modules.IPublisherModule;
import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.ImplementationGuide;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The copy of the html inspector that a watch mode page rebuild checks the output with: it finds what the original
 * finds, however often it is copied and used
 */
class HTMLInspectorCopyForRecheckTest {

  private static final ILoggingService LOG = new ILoggingService() {
    @Override
    public void logMessage(String message) {
    }

    @Override
    public void logDebugMessage(LogCategory category, String message) {
    }
  };

  @TempDir
  Path dir;

  private static String page(String body) {
    return "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\"><head><title>t</title></head><body>" + body + "</body></html>";
  }

  /** the output as Jekyll writes it (the html check changes it) */
  private static void writePages(File root) throws IOException {
    FileUtilities.stringToFile(page("<h1>A</h1><p>"
        + "<a href=\"b.html#missing\">missing</a> "
        + "<a href=\"manual.html\">provided when published</a> "
        + "<a href=\"exempt.html\">exempt</a> "
        + "<img src=\"pic.png\" alt=\"a picture\"/>"
        + "</p>"), new File(root, "a.html"));
    FileUtilities.stringToFile(page("<h1>B</h1><p id=\"there\">other</p>"), new File(root, "b.html"));
  }

  private List<String> check(HTMLInspector inspector, File req) throws IOException {
    writePages(new File(dir.toFile(), "output"));
    // what the build sets after the copy is made (see PublisherGenerator.finishGenerate())
    inspector.setReqFolder(req.getAbsolutePath());
    return inspector.check("status", new HashMap<>()).stream()
        .map(m -> m.getLocation() + " " + m.getMessage())
        .sorted()
        .collect(Collectors.toList());
  }

  @Test
  void theCopyFindsWhatTheOriginalFinds() throws IOException {
    File root = new File(dir.toFile(), "output");
    File req = new File(dir.toFile(), "req");
    root.mkdirs();
    req.mkdirs();
    writePages(root);

    IWorkerContext context = mock(IWorkerContext.class);
    when(context.getLocale()).thenReturn(Locale.ENGLISH);
    when(context.getModelContext()).thenReturn(ModelContext.fullCoreContext());
    HTMLInspector inspector = new HTMLInspector(context, root.getAbsolutePath(), new ArrayList<>(), new ArrayList<>(), LOG,
        "http://example.org/ig", "example.ig", "0.1.0", new HashMap<>(), new ArrayList<>(), mock(IPublisherModule.class), false, null, new ArrayList<>(), false,
        new ArrayList<>(), null);
    // what the build gives the inspector before the package step
    inspector.setExemptHtmlPatterns(new ArrayList<>(Arrays.asList("exempt\\.html")));
    inspector.getManual().add("manual.html");
    inspector.addLinkToCheck(new File(root, "a.html").getAbsolutePath(), "c.html", "a link from elsewhere");
    inspector.setIg(new ImplementationGuide().setUrl("http://example.org/ig/ImplementationGuide/example.ig"));

    HTMLInspector kept = inspector.copyForRecheck();
    List<String> found = check(inspector, req);
    assertFalse(found.isEmpty());
    // what a page rebuild does: a copy of the copy each time
    assertEquals(found, check(kept.copyForRecheck(), req));
    assertEquals(found, check(kept.copyForRecheck(), req));
  }
}
