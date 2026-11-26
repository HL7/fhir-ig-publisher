package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hl7.fhir.model.core.PackageInformation;
import org.junit.jupiter.api.Test;

/** Watch mode: a page rebuild puts the draft dependencies the last build found in qa.html; any other build scans */
class DraftDependenciesReuseTest {

  private Publisher publisher() {
    Publisher p = new Publisher();
    PublisherFields pf = new PublisherFields();
    pf.packageInfo = new PackageInformation("this.pkg", "0.1.0", "5.0.0", null);
    p.setPf(pf);
    return p;
  }

  @Test
  void aPageRebuildUsesWhatTheLastBuildFound() throws Exception {
    Publisher p = publisher();
    p.getPf().draftDependencies = "<ul><li>kept</li></ul>";
    assertEquals("<ul><li>kept</li></ul>", p.draftDependencies(true));
  }

  @Test
  void aPageRebuildScansIfTheLastBuildDidNot() throws Exception {
    Publisher p = publisher();
    assertEquals("(none)", p.draftDependencies(true));
  }

  @Test
  void aCompleteBuildScans() throws Exception {
    Publisher p = publisher();
    p.getPf().draftDependencies = "<ul><li>kept</li></ul>";
    assertEquals("(none)", p.draftDependencies(false));
    assertEquals("(none)", p.getPf().draftDependencies);
  }
}
