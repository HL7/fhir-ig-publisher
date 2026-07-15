package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Pins the per-version resource-membership resolver
 * {@link PublisherBase#isIncludedInVersion(Set, String, String, Set, Set, Set)} used by the
 * {@code r4-inclusion}/{@code r4b-inclusion}/{@code r5-inclusion} tag-membership params. A resource
 * in no inclusion set is present in every version; a resource listed in any inclusion set is present
 * only in the listed version(s). Inclusion is inert for a non-R5 base.
 */
class InclusionMembershipTest {

  private static final String R5 = "5.0.0";
  private static final Set<String> EMPTY = new HashSet<>();

  private Set<String> keys(String... k) {
    Set<String> s = new HashSet<>();
    for (String x : k) {
      s.add(x);
    }
    return s;
  }

  @Test
  void resourceInNoInclusion_presentEverywhere() {
    Set<String> k = keys("StructureDefinition/foo");
    assertTrue(PublisherBase.isIncludedInVersion(k, "5.0.0", R5, EMPTY, EMPTY, EMPTY));
    assertTrue(PublisherBase.isIncludedInVersion(k, "4.0.1", R5, EMPTY, EMPTY, EMPTY));
    assertTrue(PublisherBase.isIncludedInVersion(k, "4.3.0", R5, EMPTY, EMPTY, EMPTY));
  }

  @Test
  void r4InclusionOnly_presentOnlyForR4() {
    Set<String> k = keys("StructureDefinition/foo");
    Set<String> r4 = keys("StructureDefinition/foo");
    assertTrue(PublisherBase.isIncludedInVersion(k, "4.0.1", R5, EMPTY, r4, EMPTY));
    assertFalse(PublisherBase.isIncludedInVersion(k, "5.0.0", R5, EMPTY, r4, EMPTY));
    assertFalse(PublisherBase.isIncludedInVersion(k, "4.3.0", R5, EMPTY, r4, EMPTY));
  }

  @Test
  void r5InclusionOnly_presentOnlyForR5() {
    Set<String> k = keys("StructureDefinition/foo");
    Set<String> r5 = keys("StructureDefinition/foo");
    assertTrue(PublisherBase.isIncludedInVersion(k, "5.0.0", R5, r5, EMPTY, EMPTY));
    assertFalse(PublisherBase.isIncludedInVersion(k, "4.0.1", R5, r5, EMPTY, EMPTY));
    assertFalse(PublisherBase.isIncludedInVersion(k, "4.3.0", R5, r5, EMPTY, EMPTY));
  }

  @Test
  void matchesByCanonicalUrlOrTypeId() {
    Set<String> k = keys("StructureDefinition/foo", "http://example.org/fhir/StructureDefinition/foo");
    Set<String> r4ByUrl = keys("http://example.org/fhir/StructureDefinition/foo");
    assertTrue(PublisherBase.isIncludedInVersion(k, "4.0.1", R5, EMPTY, r4ByUrl, EMPTY));
    assertFalse(PublisherBase.isIncludedInVersion(k, "5.0.0", R5, EMPTY, r4ByUrl, EMPTY));
  }

  @Test
  void tokenVariantsFoldToFamily() {
    Set<String> k = keys("StructureDefinition/foo");
    Set<String> r4 = keys("StructureDefinition/foo");
    assertTrue(PublisherBase.isIncludedInVersion(k, "4.0", R5, EMPTY, r4, EMPTY));
    assertTrue(PublisherBase.isIncludedInVersion(k, "r4", R5, EMPTY, r4, EMPTY));
  }

  @Test
  void nonR5Base_alwaysIncluded() {
    Set<String> k = keys("StructureDefinition/foo");
    Set<String> r4 = keys("StructureDefinition/foo");
    // inclusion params are only consulted for an R5 base; a non-R5 base is unaffected
    assertTrue(PublisherBase.isIncludedInVersion(k, "4.3.0", "4.0.1", EMPTY, r4, EMPTY));
  }
}
