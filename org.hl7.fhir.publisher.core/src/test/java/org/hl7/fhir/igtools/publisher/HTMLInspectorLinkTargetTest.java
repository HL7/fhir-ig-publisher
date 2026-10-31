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

import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.ImplementationGuide;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The links to an anchor on another page: one that resolves gives no message, and one that doesn't
 * gives a message that lists the page's anchors and points out an anchor that differs only in case.
 */
class HTMLInspectorLinkTargetTest {

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
  void linksToAnchorsOnAnotherPage() throws IOException {
    File root = new File(dir.toFile(), "output");
    File req = new File(dir.toFile(), "req");
    root.mkdirs();
    req.mkdirs();
    FileUtilities.stringToFile(page("<h1>A</h1><p>"
        + "<a href=\"b.html#here\">resolves</a> "
        + "<a href=\"b.html#Here\">wrong case</a> "
        + "<a href=\"b.html#missing\">missing</a>"
        + "</p>"), new File(root, "a.html"));
    FileUtilities.stringToFile(page("<h1>B</h1><p><a name=\"here\"> </a>anchor</p><p id=\"there\">other</p>"), new File(root, "b.html"));

    IWorkerContext context = mock(IWorkerContext.class);
    when(context.getLocale()).thenReturn(Locale.ENGLISH);
    when(context.getModelContext()).thenReturn(ModelContext.fullCoreContext());
    HTMLInspector inspector = new HTMLInspector(context, root.getAbsolutePath(), new ArrayList<>(), new ArrayList<>(), LOG,
      "http://example.org/ig", "example.ig", "0.1.0", new HashMap<>(), new ArrayList<>(), null, false, null, new ArrayList<>(), false,
      new ArrayList<>(), null);
    inspector.setExemptHtmlPatterns(new ArrayList<>());
    inspector.setReqFolder(req.getAbsolutePath());
    inspector.setIg(new ImplementationGuide().setUrl("http://example.org/ig/ImplementationGuide/example.ig"));
    List<ValidationMessage> messages = inspector.check("status", new HashMap<>());

    List<String> links = messages.stream()
      .filter(m -> "HTML_LINK_CHECK_FAILED".equals(m.getMessageId()))
      .map(ValidationMessage::getMessage)
      .sorted()
      .collect(Collectors.toList());
    assertEquals(List.of(
        "The link 'b.html#Here' for \"wrong case\" cannot be resolved (valid targets: [here, b, there]) - case is wrong ('here')",
        "The link 'b.html#missing' for \"missing\" cannot be resolved (valid targets: [here, b, there])"),
      links);
  }
}
