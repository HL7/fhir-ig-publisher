package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.hl7.fhir.igtools.publisher.HTMLInspector.ExternalReference;
import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.ImplementationGuide;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An external link is recorded once for each type, url and link text, in the order first seen.
 */
class HTMLInspectorExternalReferenceTest {

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

  @Test
  void eachExternalReferenceIsRecordedOnce() throws IOException {
    File root = new File(dir.toFile(), "output");
    File req = new File(dir.toFile(), "req");
    root.mkdirs();
    req.mkdirs();
    FileUtilities.stringToFile(page("<h1>A</h1>"
        + "<p><a href=\"https://example.com/one\">One</a></p>"
        + "<p><a href=\"https://example.com/one\">One</a></p>"
        + "<p><a href=\"https://example.com/one\">One again</a></p>"
        + "<p><a href=\"https://example.com/two\">Two</a></p>"
        + "<p><a href=\"https://hl7.org/fhir/R5/patient.html\">skipped</a></p>"
        + "<p><img src=\"https://example.com/one\" alt=\"one\"/></p>"), new File(root, "a.html"));
    FileUtilities.stringToFile(page("<h1>B</h1>"
        + "<p><a href=\"https://example.com/two\">Two</a></p>"
        + "<p><a href=\"https://example.com/two\">Two on b</a></p>"
        + "<p><a href=\"https://example.com/three\">Three</a></p>"), new File(root, "b.html"));

    IWorkerContext context = mock(IWorkerContext.class);
    when(context.getLocale()).thenReturn(Locale.ENGLISH);
    when(context.getModelContext()).thenReturn(ModelContext.fullCoreContext());
    HTMLInspector inspector = new HTMLInspector(context, root.getAbsolutePath(), new ArrayList<>(), new ArrayList<>(), LOG,
      "http://example.org/ig", "example.ig", "0.1.0", new HashMap<>(), new ArrayList<>(), null, false, null, new ArrayList<>(), false,
      new ArrayList<>(), null);
    inspector.setExemptHtmlPatterns(new ArrayList<>());
    inspector.setReqFolder(req.getAbsolutePath());
    inspector.setIg(new ImplementationGuide().setUrl("http://example.org/ig/ImplementationGuide/example.ig"));
    inspector.check("status", new HashMap<>());

    List<String> refs = inspector.getExternalReferences().stream()
      .map(HTMLInspectorExternalReferenceTest::describe)
      .collect(Collectors.toList());
    assertEquals(List.of(
        "WEB https://example.com/one 'One' a.html",
        "WEB https://example.com/one 'One again' a.html",
        "WEB https://example.com/two 'Two' a.html",
        "IMG https://example.com/one '' a.html",
        "WEB https://example.com/two 'Two on b' b.html",
        "WEB https://example.com/three 'Three' b.html"),
      refs);
  }

  private static String describe(ExternalReference exr) {
    return exr.getType() + " " + exr.getUrl() + " '" + exr.getXhtml().allText() + "' " + exr.getSource();
  }
}
