package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fhir.ucum.Decimal;
import org.fhir.ucum.UcumService;
import org.junit.jupiter.api.Test;

class PublisherIGLoaderUcumTest {

  @Test
  void loadsUcumServiceFromEmbeddedEssence() throws Exception {
    UcumService ucum = PublisherIGLoader.loadUcumService();
    assertNotNull(ucum);
    assertTrue(ucum.isComparable("mg", "g"));
    assertFalse(ucum.isComparable("mg", "mL"));
    assertEquals(0, ucum.convert(new Decimal("2"), "g", "mg").comparesTo(new Decimal("2000")));
  }
}
