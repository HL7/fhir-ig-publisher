package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** ig.ini's jekyll-timeout can't keep Jekyll running on the ci-build for more than an hour */
class JekyllTimeoutTest {

  @ParameterizedTest
  @CsvSource({
    // requested (s), ci-build, used (s)
    "300, true, 300",
    "3600, true, 3600",
    "36000, true, 3600",
    "36000, false, 36000",
    "300, false, 300"
  })
  void theCiBuildCapsJekyllAtAnHour(long requested, boolean ciBuild, long used) {
    assertEquals(used * 1000, PublisherIGLoader.jekyllTimeout(requested * 1000, ciBuild));
  }
}
