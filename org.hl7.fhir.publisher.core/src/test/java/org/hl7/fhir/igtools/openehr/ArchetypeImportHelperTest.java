package org.hl7.fhir.igtools.openehr;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.hl7.fhir.model.core.DataType;
import org.hl7.fhir.model.core.ElementDefinition.ElementDefinitionConstraintComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

class ArchetypeImportHelperTest {

  private String testIsu8601DateTime1;
  private String testIsu8601DateTime2;
  private String testIsu8601DateTime3;

  @BeforeEach
  void setUp() {
    testIsu8601DateTime1 = "PT0S";
    testIsu8601DateTime2 = "PT24H";
    testIsu8601DateTime3 = "P3M";
  }

  @Test
  void testCreateDateConstraint() {
    ElementDefinitionConstraintComponent ref = ArchetypeImportHelper.createDateConstraint("test", "id1", null, "YYYY-MM-DD");
    assertNotNull(ref);
  }

  @Test
  void testCreateDateTimeConstraint() {
    fail("Not yet implemented");
  }

  @Test
  void testDateFromString() {
    fail("Not yet implemented");
  }

  @Test
  void testDateTimeFromString() {
    fail("Not yet implemented");
  }

  @Test
  void testDurationFromPeriod1() throws ParserConfigurationException, SAXException, IOException {
    DataType ref = ArchetypeImportHelper.durationFromPeriod(testIsu8601DateTime1);
    assertNotNull(ref);
    assertTrue(ref instanceof org.hl7.fhir.model.core.Duration);
  }

  @Test
  void testDurationFromPeriod2() throws ParserConfigurationException, SAXException, IOException {

    DataType ref = ArchetypeImportHelper.durationFromPeriod(testIsu8601DateTime2);
    assertNotNull(ref);
    assertTrue(ref instanceof org.hl7.fhir.model.core.Duration);
  }

  @Test
  void testDurationFromPeriod3() throws ParserConfigurationException, SAXException, IOException {
    DataType ref = ArchetypeImportHelper.durationFromPeriod(testIsu8601DateTime3);
    assertNotNull(ref);
    assertTrue(ref instanceof org.hl7.fhir.model.core.Duration);
  }

}
