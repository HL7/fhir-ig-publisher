package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.services.comparison.ResourceComparer;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
import org.junit.jupiter.api.Test;

/**
 * A complete rebuild in watch mode starts from what a fresh start has: the no-validate and no-narrative lists that the
 * IG's parameters added to in the last build (PublisherIGLoader) go back to what the command line said, so a parameter
 * taken out of the IG validates (and reports) those resources again; and the table and comparison page ids start again
 */
class WatchRebuildResetTest {

  @Test
  void aRebuildStartsFromTheCommandLineSettings() throws Exception {
    Publisher p = new Publisher();
    p.getSettings().getNoValidate().add("Patient/from-cli");
    p.getSettings().getNoNarratives().add("Observation/from-cli");
    List<String> cliNoNarratives = new ArrayList<>(p.getSettings().getNoNarratives());
    List<String> cliNoValidate = new ArrayList<>(p.getSettings().getNoValidate());

    // what the last build's IG parameters (no-validate, no-narrative) added, and what the build left elsewhere
    p.getSettings().getNoValidate().add("Patient/from-ig");
    p.getSettings().getNoNarratives().add("Observation/from-ig");
    HierarchicalTableGenerator.ACTIVE_TABLES = true;
    String uuid = HierarchicalTableGenerator.uuid;

    p.resetForRebuild(cliNoNarratives, cliNoValidate);

    assertEquals(List.of("Patient/from-cli"), p.getSettings().getNoValidate());
    assertEquals(List.of("Observation/from-cli"), p.getSettings().getNoNarratives());
    assertFalse(HierarchicalTableGenerator.ACTIVE_TABLES);
    assertNotEquals(uuid, HierarchicalTableGenerator.uuid);

    // and again for the next rebuild
    p.getSettings().getNoValidate().add("Patient/from-ig");
    p.resetForRebuild(cliNoNarratives, cliNoValidate);
    assertEquals(List.of("Patient/from-cli"), p.getSettings().getNoValidate());
  }

  @Test
  void everyRebuildNumbersTheComparisonPagesAsTheFirstBuildDid() throws Exception {
    Publisher p = new Publisher();
    List<String> none = new ArrayList<>();
    p.resetForRebuild(none, none); // as a fresh start
    List<String> first = comparisonIds();

    p.resetForRebuild(none, none);
    assertEquals(first, comparisonIds());
    p.resetForRebuild(none, none);
    assertEquals(first, comparisonIds());
  }

  /** the ids of the comparison pages for the comparisons a build makes */
  private static List<String> comparisonIds() {
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      ids.add(new Comparison().getId());
    }
    return ids;
  }

  private static class Comparison extends ResourceComparer.ResourceComparison {
    Comparison() {
      super("left", "right");
    }

    @Override
    protected String abbreviation() {
      return "t";
    }

    @Override
    protected String summary() {
      return "";
    }

    @Override
    protected String fhirType() {
      return "ValueSet";
    }

    @Override
    protected String toTable() {
      return "";
    }

    @Override
    protected void countMessages(ResourceComparer.MessageCounts cnts) {
    }
  }
}
