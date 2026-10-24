package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hl7.fhir.utilities.Utilities;
import org.junit.jupiter.api.Test;

class PublisherBaseMemoryTest {

  @Test
  void usedMemoryIsTheDifference() {
    assertEquals(300, PublisherBase.usedMemory(1000, 700));
  }

  @Test
  void heapGrowingBetweenTheTwoReadsDoesNotGiveANegativeSize() {
    // the heap grew from 1000 to 1500 after totalMemory was read, and 200 is in use
    long used = PublisherBase.usedMemory(1000, 1300);
    assertEquals(0, used);
    assertEquals(Utilities.describeSize(0), Utilities.describeSize(used));
  }
}
