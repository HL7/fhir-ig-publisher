package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;

import org.hl7.fhir.utilities.FileUtilities;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AIProcessorResourceFileNamesTest {

  private static final List<String> RESOURCE_FILES = List.of(
    "StructureDefinition-example-patient.html", "CodeSystem-Nationality.html", "ValueSet-x.html", "Patient-example");

  static Stream<String> pages() {
    return Stream.of("StructureDefinition-example-patient.html", "structuredefinition-EXAMPLE-patient.html",
      "StructureDefinition-example-patient-definitions.html", "StructureDefinition-example-patient.profile.json.html",
      "codesystem-nationality.html", "CodeSystem-Nationality-testing.html", "ValueSet-x.html", "ValueSet-xy.html",
      "ValueSet-.html", "Patient-example.html", "Patient-examples.html", "index.html", "toc.html", "");
  }

  @ParameterizedTest
  @MethodSource("pages")
  void sameAnswersAsComparingWithEveryResourceInTurn(String page) {
    AIProcessor.ResourceFileNames names = new AIProcessor.ResourceFileNames();
    for (String rf : RESOURCE_FILES) {
      names.add(rf);
    }

    // what loadPages did for each page before
    boolean found = false;
    boolean found2 = false;
    for (String filename : RESOURCE_FILES) {
      if (filename.equalsIgnoreCase(page)) {
        found = true;
      }
      String n = FileUtilities.changeFileExt(page.toLowerCase(), "");
      if (n.startsWith(FileUtilities.changeFileExt(filename.toLowerCase(), ""))) {
        found2 = true;
      }
    }

    assertEquals(found, names.isResourceFile(page));
    assertEquals(found2, names.startsWithResourceFile(page));
  }
}
