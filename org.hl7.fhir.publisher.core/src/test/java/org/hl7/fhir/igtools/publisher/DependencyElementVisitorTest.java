package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.services.elementmodel.ElementVisitor.ElementVisitorInstruction;
import org.junit.jupiter.api.Test;

class DependencyElementVisitorTest {

  @Test
  void aCanonicalWithOnlyExtensionsHasNoDependencies() {
    // e.g. "_valueCanonical": { "extension": [...] }
    Element canonical = mock(Element.class);
    when(canonical.isPrimitive()).thenReturn(true);
    when(canonical.fhirType()).thenReturn("canonical");
    when(canonical.primitiveValue()).thenReturn(null);
    List<FetchedFile> dependencies = new ArrayList<>();
    FetchedFile focus = new FetchedFile("Group-example.json");
    List<FetchedFile> fileList = new ArrayList<>(List.of(focus, new FetchedFile("Library-example.json")));

    ElementVisitorInstruction result = new DependencyElementVisitor(fileList, dependencies, focus).visit(null, canonical);

    assertEquals(ElementVisitorInstruction.NO_VISIT_CHILDREN, result);
    assertTrue(dependencies.isEmpty());
  }

  @Test
  void aVersionedCanonicalDependsOnEveryVersionOfThatUrl() {
    Element canonical = mock(Element.class);
    when(canonical.isPrimitive()).thenReturn(true);
    when(canonical.fhirType()).thenReturn("canonical");
    when(canonical.primitiveValue()).thenReturn("http://example.org/Library/lib|1.0.0");
    FetchedFile focus = new FetchedFile("Group-example.json");
    FetchedFile library = fileWith("Library-lib.json", "http://example.org/Library/lib", "1.0.0");
    FetchedFile otherVersion = fileWith("Library-lib-2.json", "http://example.org/Library/lib", "2.0.0");
    List<FetchedFile> dependencies = new ArrayList<>();

    new DependencyElementVisitor(List.of(focus, library, otherVersion), dependencies, focus).visit(null, canonical);

    assertEquals(List.of(library, otherVersion), dependencies);
  }

  @Test
  void aReferenceDependsOnTheFileWithThatTypeAndIdOnce() {
    Element reference = primitive("string", "Patient/p1");
    FetchedFile focus = new FetchedFile("Observation-o1.json");
    FetchedFile patients = new FetchedFile("Bundle-patients.json");
    patients.getResources().add(resource("Patient", "p1", null));
    patients.getResources().add(resource("Patient", "p1", null)); // the same id twice in one file
    FetchedFile other = new FetchedFile("Patient-p2.json");
    other.getResources().add(resource("Patient", "p2", null));
    List<FetchedFile> dependencies = new ArrayList<>();
    DependencyElementVisitor visitor = new DependencyElementVisitor(new DependencyElementVisitor.Index(List.of(focus, patients, other)), dependencies, focus);

    visitor.visit(null, reference);
    visitor.visit(null, reference);

    assertEquals(List.of(patients), dependencies);
  }

  @Test
  void theFilesAreDependedOnInTheOrderOfTheFileList() {
    // what the index finds must be what going through the file list found, so that the hashes don't change
    FetchedFile focus = new FetchedFile("StructureDefinition-a.json");
    focus.getResources().add(resource("StructureDefinition", "a", "http://example.org/a"));
    FetchedFile second = new FetchedFile("ValueSet-v2.json");
    second.getResources().add(resource("ValueSet", "v2", "http://example.org/v"));
    FetchedFile first = new FetchedFile("ValueSet-v1.json");
    first.getResources().add(resource("ValueSet", "v1", "http://example.org/v"));
    List<FetchedFile> dependencies = new ArrayList<>();

    new DependencyElementVisitor(List.of(first, focus, second), dependencies, focus).visit(null, primitive("uri", "http://example.org/v"));
    new DependencyElementVisitor(List.of(first, focus, second), dependencies, focus).visit(null, primitive("uri", "http://example.org/a"));

    assertEquals(List.of(first, second), dependencies);
  }

  private static Element primitive(String type, String value) {
    Element element = mock(Element.class);
    when(element.isPrimitive()).thenReturn(true);
    when(element.fhirType()).thenReturn(type);
    when(element.primitiveValue()).thenReturn(value);
    return element;
  }

  private static FetchedResource resource(String type, String id, String url) {
    Element element = mock(Element.class);
    when(element.getNamedChildValue("url")).thenReturn(url);
    FetchedResource resource = mock(FetchedResource.class);
    when(resource.fhirType()).thenReturn(type);
    when(resource.getId()).thenReturn(id);
    when(resource.getElement()).thenReturn(element);
    return resource;
  }

  private static FetchedFile fileWith(String name, String url, String version) {
    Element element = mock(Element.class);
    when(element.getNamedChildValue("url")).thenReturn(url);
    when(element.getNamedChildValue("version")).thenReturn(version);
    FetchedResource resource = mock(FetchedResource.class);
    when(resource.getElement()).thenReturn(element);
    FetchedFile file = new FetchedFile(name);
    file.getResources().add(resource);
    return file;
  }
}
