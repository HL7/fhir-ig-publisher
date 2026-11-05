package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PublisherSettingsThreadsTest {

  @Test
  void offByDefault() {
    assertEquals(0, new PublisherSettings().getThreads());
  }

  @Test
  void usesTheNumberAskedFor() {
    assertEquals(12, PublisherSettings.parseThreads("12", 16));
    assertEquals(0, PublisherSettings.parseThreads("0", 16));
    assertEquals(4, PublisherSettings.parseThreads(" 4 ", 16));
  }

  @Test
  void capsAtOneLessThanTheCores() {
    assertEquals(15, PublisherSettings.parseThreads("15", 16));
    assertEquals(15, PublisherSettings.parseThreads("64", 16));
    assertEquals(0, PublisherSettings.parseThreads("4", 1));
  }

  @Test
  void rejectsValuesThatAreNotACount() {
    assertThrows(IllegalArgumentException.class, () -> PublisherSettings.parseThreads("lots", 16));
    assertThrows(IllegalArgumentException.class, () -> PublisherSettings.parseThreads(null, 16));
    assertThrows(IllegalArgumentException.class, () -> PublisherSettings.parseThreads("-1", 16));
  }
}
