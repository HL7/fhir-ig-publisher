package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;

import org.hl7.fhir.services.comparison.ResourceComparer;
import org.junit.jupiter.api.Test;

/** A rebuild in watch mode numbers the comparisons from 1 again, as a fresh start does */
class ComparisonIdsResetTest {

  @Test
  void theComparisonCounterStartsAgain() throws Exception {
    Field f = ResourceComparer.class.getDeclaredField("counter");
    f.setAccessible(true);
    f.setInt(null, 20);
    Publisher.resetComparisonIds();
    assertEquals(0, f.getInt(null));
  }
}
