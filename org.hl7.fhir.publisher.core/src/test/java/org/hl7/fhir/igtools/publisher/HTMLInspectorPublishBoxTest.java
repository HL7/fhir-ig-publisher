package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
 * The html check replaces the publish box placeholder with the status message, and then checks the
 * page as it now is; pages without the placeholder, and files that aren't html, are left alone.
 */
class HTMLInspectorPublishBoxTest {

  private static final String PLACEHOLDER = "<!--ReleaseHeader--><p id=\"publish-box\">Publish Box goes here</p><!--EndReleaseHeader-->";
  private static final String STATUS = "Status <a name=\"status-anchor\"> </a>message";

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
  void publishBoxIsFilledInAndChecked() throws IOException {
    File root = new File(dir.toFile(), "output");
    File req = new File(dir.toFile(), "req");
    root.mkdirs();
    req.mkdirs();
    File a = new File(root, "a.html");
    File b = new File(root, "b.html");
    FileUtilities.stringToFile(page(PLACEHOLDER + "<h1>A</h1><p>text</p>"), a);
    String bSource = page("<h1>B</h1><p><a href=\"a.html#status-anchor\">in the status message</a> "
      + "<a href=\"a.html#no-such-anchor\">missing</a></p>");
    FileUtilities.stringToFile(bSource, b);
    FileUtilities.stringToFile("{\"resourceType\" : \"Patient\"}", new File(root, "Patient-x.json"));
    Files.write(new File(root, "image.png").toPath(), new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 1, 2, 3});

    IWorkerContext context = mock(IWorkerContext.class);
    when(context.getLocale()).thenReturn(Locale.ENGLISH);
    when(context.getModelContext()).thenReturn(ModelContext.fullCoreContext());
    HTMLInspector inspector = new HTMLInspector(context, root.getAbsolutePath(), new ArrayList<>(), new ArrayList<>(), LOG,
      "http://example.org/ig", "example.ig", "0.1.0", new HashMap<>(), new ArrayList<>(), null, false, null, new ArrayList<>(), false,
      new ArrayList<>(), null);
    inspector.setExemptHtmlPatterns(new ArrayList<>());
    inspector.setReqFolder(req.getAbsolutePath());
    inspector.setIg(new ImplementationGuide().setUrl("http://example.org/ig/ImplementationGuide/example.ig"));
    List<ValidationMessage> messages = inspector.check(STATUS, new HashMap<>());

    String aNow = FileUtilities.fileToString(a);
    assertTrue(aNow.contains("<p id=\"publish-box\">" + STATUS), aNow);
    // b has no placeholder, so it gets no publish box (the link check marks the broken link in it)
    assertFalse(FileUtilities.fileToString(b).contains("publish-box"));

    List<String> problems = messages.stream()
      .filter(m -> m.getMessageId() != null && m.getMessageId().startsWith("HTML_"))
      .map(m -> m.getMessageId() + ": " + m.getMessage())
      .sorted()
      .collect(Collectors.toList());
    // the anchor in the status message is found; the json and png files aren't reported
    assertEquals(List.of("HTML_LINK_CHECK_FAILED: The link 'a.html#no-such-anchor' for \"missing\" cannot be resolved (valid targets: [a, status-anchor, publish-box])"),
      problems);
  }
}
