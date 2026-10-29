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
  void aVersionedCanonicalDependsOnTheFileWithThatVersion() {
    Element canonical = mock(Element.class);
    when(canonical.isPrimitive()).thenReturn(true);
    when(canonical.fhirType()).thenReturn("canonical");
    when(canonical.primitiveValue()).thenReturn("http://example.org/Library/lib|1.0.0");
    FetchedFile focus = new FetchedFile("Group-example.json");
    FetchedFile library = fileWith("Library-lib.json", "http://example.org/Library/lib", "1.0.0");
    FetchedFile otherVersion = fileWith("Library-lib-2.json", "http://example.org/Library/lib", "2.0.0");
    List<FetchedFile> dependencies = new ArrayList<>();

    new DependencyElementVisitor(List.of(focus, library, otherVersion), dependencies, focus).visit(null, canonical);

    assertEquals(List.of(library), dependencies);
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
