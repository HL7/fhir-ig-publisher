package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.junit.jupiter.api.Test;

/** IG spreadsheets are no longer supported: an IG that uses one stops, saying what to do instead */
class SpreadsheetsUnsupportedTest {

  @Test
  void anIGThatUsesASpreadsheetStops() {
    FHIRException e = assertThrows(FHIRException.class, () -> PublisherIGLoader.checkNoSpreadsheets(List.of("profiles.xml", "!old.xml")));
    assertTrue(e.getMessage().contains("profiles.xml"), e.getMessage());
    assertFalse(e.getMessage().contains("old.xml"), e.getMessage());
  }

  @Test
  void spreadsheetsThatAreSwitchedOffAreStillIgnored() {
    PublisherIGLoader.checkNoSpreadsheets(List.of("!old.xml"));
    PublisherIGLoader.checkNoSpreadsheets(List.of());
  }
}
