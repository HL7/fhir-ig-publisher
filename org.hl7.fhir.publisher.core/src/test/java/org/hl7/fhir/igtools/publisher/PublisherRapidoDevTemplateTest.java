package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;

import org.hl7.fhir.igtools.templates.Template;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublisherRapidoDevTemplateTest {

  @TempDir
  Path dir;

  @Test
  void aTemplateInDevelopmentMakesACompleteBuild() throws Exception {
    // the IG, a page that changed since the last build, and one that didn't
    FetchedFile ig = file("ig.json", 1);
    FetchedFile changed = file("changed.md", 6);
    FetchedFile unchanged = file("unchanged.md", 7);
    IniFile buildTracker = new IniFile(dir.resolve(".build-tracker.ini").toString());
    buildTracker.setBooleanProperty("status", "started", true, null);
    buildTracker.setBooleanProperty("status", "complete", true, null);
    buildTracker.setStringProperty("status", "uuid", "the-last-build", null);
    // what the last build tracked: each file's hash, combined with the hash of the IG it depends on
    buildTracker.setStringProperty("tracked", "ig.json", "1", null);
    buildTracker.setStringProperty("tracked", "changed.md", Long.toString(37 * 5 + 1), null);
    buildTracker.setStringProperty("tracked", "unchanged.md", Long.toString(37 * 7 + 1), null);

    Publisher publisher = new Publisher();
    publisher.getSettings().setRapidoMode(true);
    PublisherFields pf = new PublisherFields();
    pf.logger = mock(ILoggingService.class);
    // a local template folder: the temp folder is cleared when the IG is loaded
    pf.template = mock(Template.class);
    when(pf.template.isDevMode()).thenReturn(true);
    pf.fileList.addAll(List.of(ig, changed, unchanged));
    publisher.setPf(pf);

    publisher.checkDependencies(ig, buildTracker);

    assertEquals(List.of(ig, changed, unchanged), pf.changeList);
    // recorded like any other build, so that a build that fails is followed by a complete one
    IniFile saved = new IniFile(dir.resolve(".build-tracker.ini").toString());
    assertTrue(saved.getBooleanProperty("status", "started"));
    assertFalse(saved.getBooleanProperty("status", "complete"));
    assertEquals(Long.toString(37 * 6 + 1), saved.getStringProperty("tracked", "changed.md"));
    assertEquals(HierarchicalTableGenerator.uuid, buildTracker.getStringProperty("status", "uuid"));
  }

  private static FetchedFile file(String name, long hash) {
    FetchedFile f = new FetchedFile(name);
    f.setHash(hash);
    return f;
  }
}
