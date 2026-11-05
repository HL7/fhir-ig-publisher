package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.igtools.templates.Template;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.NamedItemList;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A rapido build in watch mode builds again only what changed and what depends on it, as without watch mode */
class PublisherRapidoWatchTest {

  @TempDir
  Path dir;

  @Test
  void aWatchBuildRebuildsTheChangedFileAndWhatDependsOnIt() throws Exception {
    IniFile buildTracker = new IniFile(dir.resolve(".build-tracker.ini").toString());
    buildTracker.setBooleanProperty("status", "started", true, null);
    buildTracker.setBooleanProperty("status", "complete", true, null);
    buildTracker.setStringProperty("status", "uuid", "the-last-build", null);
    // what an earlier build tracked for the IG and a page that depends only on it, so that this build is differential
    buildTracker.setStringProperty("tracked", "ig.json", "1", null);
    buildTracker.setStringProperty("tracked", "page.md", Long.toString(37 * 9 + 1), null);
    buildTracker.save();
    build(buildTracker, 2);
    buildTracker.setBooleanProperty("status", "complete", true, null);
    buildTracker.save();

    HierarchicalTableGenerator.uuid = "a-new-session"; // as resetForRebuild leaves it

    // the patient changes: the observation that refers to it is built again, the other patient isn't
    List<FetchedFile> changes = build(new IniFile(dir.resolve(".build-tracker.ini").toString()), 3);

    assertEquals(List.of("Patient-a.json", "Observation-o.json"), names(changes));
    assertEquals("the-last-build", HierarchicalTableGenerator.uuid);
  }

  /** checkDependencies on new files, as a watch rebuild loads them, with the given hash for Patient/a */
  private List<FetchedFile> build(IniFile buildTracker, long patientHash) throws Exception {
    FetchedFile ig = file("ig.json", 1);
    FetchedFile patient = file("Patient-a.json", patientHash, resource("Patient", "a"));
    FetchedFile other = file("Patient-b.json", 5, resource("Patient", "b"));
    FetchedFile observation = file("Observation-o.json", 7, resource("Observation", "o", primitive("Patient/a")));
    FetchedFile page = file("page.md", 9);

    Publisher publisher = new Publisher();
    publisher.getSettings().setRapidoMode(true);
    publisher.getSettings().setWatchMode(true);
    PublisherFields pf = new PublisherFields();
    pf.logger = mock(ILoggingService.class);
    pf.template = mock(Template.class);
    pf.fileList.addAll(List.of(ig, patient, other, observation, page));
    publisher.setPf(pf);

    publisher.checkDependencies(ig, buildTracker);
    return pf.changeList;
  }

  private static List<String> names(List<FetchedFile> files) {
    List<String> names = new ArrayList<>();
    for (FetchedFile f : files) {
      names.add(f.getLoadPath());
    }
    return names;
  }

  private static FetchedFile file(String name, long hash, FetchedResource... resources) {
    FetchedFile f = new FetchedFile(name);
    f.setHash(hash);
    f.getResources().addAll(List.of(resources));
    return f;
  }

  private static FetchedResource resource(String type, String id, Element... children) {
    Element element = mock(Element.class);
    NamedItemList<Element> list = new NamedItemList<>();
    list.addAll(List.of(children));
    when(element.getChildList()).thenReturn(list);
    FetchedResource resource = mock(FetchedResource.class);
    when(resource.fhirType()).thenReturn(type);
    when(resource.getId()).thenReturn(id);
    when(resource.getElement()).thenReturn(element);
    return resource;
  }

  private static Element primitive(String value) {
    Element element = mock(Element.class);
    when(element.isPrimitive()).thenReturn(true);
    when(element.fhirType()).thenReturn("string");
    when(element.primitiveValue()).thenReturn(value);
    return element;
  }
}
