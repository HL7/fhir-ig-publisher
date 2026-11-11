package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.hl7.fhir.igtools.templates.Template;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A template in development mode (a local folder) is copied into the IG's template folder on every run. Rapido hashes
 * that folder, so a build after a change to the template is a complete one, and one after no change is differential
 */
class PublisherRapidoDevTemplateTest {

  @TempDir
  Path dir;

  private final String uuid = HierarchicalTableGenerator.uuid;

  @AfterEach
  void restoreUuid() {
    // a differential build takes the uuid of the last build
    HierarchicalTableGenerator.uuid = uuid;
  }

  @Test
  void aTemplateInDevelopmentIsComparedWithTheLastBuild() throws Exception {
    Path page = dir.resolve("template").resolve("layout.html");
    Files.createDirectories(page.getParent());
    Files.writeString(page, "<html>1</html>");
    Path ig = dir.resolve("input").resolve("ig.json");
    Files.createDirectories(ig.getParent());
    Files.writeString(ig, "{}");

    PublisherFields pf = new PublisherFields();
    pf.logger = mock(ILoggingService.class);
    pf.rootDir = dir.toString();
    pf.tempDir = dir.resolve("temp").toString();
    pf.vsCache = dir.resolve("txcache").toString();
    pf.igpkp = mock(IGKnowledgeProvider.class);
    when(pf.igpkp.getCanonical()).thenReturn("http://example.org/ig");
    pf.template = mock(Template.class);
    when(pf.template.isDevMode()).thenReturn(true);
    FetchedFile igf = new FetchedFile("input/ig.json");
    igf.setPath(ig.toString());
    igf.setSource("{}".getBytes(StandardCharsets.UTF_8));
    pf.fileList.add(igf);

    // the first build: nothing to compare with
    RapidoTracker first = new RapidoTracker(pf);
    first.analyse(igf, null);
    assertTrue(first.isCompleteBuild());
    first.setUuid("the-last-build");
    first.save();
    // and the record of what it wrote in the temp folder (nothing)
    Files.createDirectories(Path.of(pf.vsCache));
    Files.writeString(Path.of(pf.vsCache, ".build-outputs.json"), "{}");

    // the template hasn't changed: a differential build
    Publisher publisher = new Publisher();
    publisher.getSettings().setRapidoMode(true);
    publisher.setPf(pf);
    IniFile buildTracker = new IniFile(Path.of(pf.vsCache, ".build-tracker.ini").toString());
    publisher.checkDependencies(igf, buildTracker);
    assertFalse(pf.rapido.isCompleteBuild(), pf.rapido.getReason());
    assertEquals("the-last-build", pf.rapido.getUuid());

    // the template has changed: a complete build
    Files.writeString(page, "<html>2</html>");
    RapidoTracker changed = new RapidoTracker(pf);
    changed.analyse(igf, null);
    assertTrue(changed.isCompleteBuild());
    assertEquals("changed: template", changed.getReason());
  }
}
