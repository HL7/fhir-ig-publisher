package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.model.core.ValueSet;
import org.junit.jupiter.api.Test;

/**
 * {@link PublisherProcessor#repinToTarget}: a core value set copied into another version's package
 * must not stay pinned to a source-version code system or value set that the package won't have.
 * The case that found it: R5's value-filter-comparator, copied into the R4 package of the
 * extensions IG, pinned search-comparator|5.0.0, which R4 has as 4.0.1 (so it isn't copied)
 */
class RepinToTargetTest {

  private static class Lookup implements PublisherProcessor.ITargetLookup {
    final Set<String> copied = new HashSet<>();
    final Map<String, String> target = new HashMap<>();

    @Override
    public boolean isCopied(String type, String url, String version) {
      return copied.contains(type + "|" + url + "|" + version);
    }

    @Override
    public String targetVersion(String type, String url) {
      return target.get(type + "|" + url);
    }
  }

  private static final String SEARCH_COMPARATOR = "http://hl7.org/fhir/search-comparator";

  @Test
  void pinnedCodeSystemTheTargetHasIsRepinnedToTheTargetVersion() {
    ValueSet vs = new ValueSet();
    vs.getCompose().addInclude().setSystem(SEARCH_COMPARATOR).setVersion("5.0.0").addConcept().setCode("eq");
    Lookup lookup = new Lookup();
    lookup.target.put("CodeSystem|" + SEARCH_COMPARATOR, "4.0.1");

    PublisherProcessor.repinToTarget(vs, lookup);

    assertEquals("4.0.1", vs.getCompose().getIncludeFirstRep().getVersion());
  }

  @Test
  void pinnedCodeSystemThatIsCopiedKeepsItsPin() {
    ValueSet vs = new ValueSet();
    vs.getCompose().addInclude().setSystem("http://hl7.org/fhir/version-algorithm").setVersion("5.0.0");
    Lookup lookup = new Lookup();
    lookup.copied.add("CodeSystem|http://hl7.org/fhir/version-algorithm|5.0.0");
    lookup.target.put("CodeSystem|http://hl7.org/fhir/version-algorithm", "4.0.1"); // not consulted

    PublisherProcessor.repinToTarget(vs, lookup);

    assertEquals("5.0.0", vs.getCompose().getIncludeFirstRep().getVersion());
  }

  @Test
  void pinThatTheTargetDoesNotHaveIsLeftAlone() {
    ValueSet vs = new ValueSet();
    vs.getCompose().addInclude().setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode").setVersion("9.0.0");

    PublisherProcessor.repinToTarget(vs, new Lookup());

    assertEquals("9.0.0", vs.getCompose().getIncludeFirstRep().getVersion());
  }

  @Test
  void unpinnedIncludeStaysUnpinned() {
    ValueSet vs = new ValueSet();
    vs.getCompose().addInclude().setSystem(SEARCH_COMPARATOR);
    Lookup lookup = new Lookup();
    lookup.target.put("CodeSystem|" + SEARCH_COMPARATOR, "4.0.1");

    PublisherProcessor.repinToTarget(vs, lookup);

    assertEquals(null, vs.getCompose().getIncludeFirstRep().getVersion());
  }

  @Test
  void excludesAndImportedValueSetsAreRepinnedToo() {
    ValueSet vs = new ValueSet();
    vs.getCompose().addInclude().addValueSet("http://hl7.org/fhir/ValueSet/a|5.0.0").addValueSet("http://hl7.org/fhir/ValueSet/b|5.0.0");
    vs.getCompose().addExclude().setSystem(SEARCH_COMPARATOR).setVersion("5.0.0");
    Lookup lookup = new Lookup();
    lookup.target.put("ValueSet|http://hl7.org/fhir/ValueSet/a", "4.0.1");
    lookup.copied.add("ValueSet|http://hl7.org/fhir/ValueSet/b|5.0.0");
    lookup.target.put("CodeSystem|" + SEARCH_COMPARATOR, "4.0.1");

    PublisherProcessor.repinToTarget(vs, lookup);

    assertEquals("http://hl7.org/fhir/ValueSet/a|4.0.1", vs.getCompose().getIncludeFirstRep().getValueSetList().get(0).getValue());
    assertEquals("http://hl7.org/fhir/ValueSet/b|5.0.0", vs.getCompose().getIncludeFirstRep().getValueSetList().get(1).getValue());
    assertEquals("4.0.1", vs.getCompose().getExcludeFirstRep().getVersion());
  }
}
