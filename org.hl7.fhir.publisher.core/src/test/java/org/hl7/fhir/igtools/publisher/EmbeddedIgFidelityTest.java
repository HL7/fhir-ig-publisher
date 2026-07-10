package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.model.Enumerations;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.junit.jupiter.api.Test;

/**
 * Pins the H3 embedded-IG resource-membership filter
 * ({@link PublisherBase#filterResourceMembership(ImplementationGuide, String, String, Function, Set, Set, Set)}):
 * a per-version package must list, in its embedded {@code ImplementationGuide.definition.resource},
 * only the resources actually included for that target version. The membership decision reuses the
 * same identity-key set ({@code Type/id} and canonical URL) as {@code isIncludedInVersion}, so a
 * resource scoped via a canonical-keyed inclusion set is honoured even though {@code definition.resource}
 * references it by {@code Type/id}.
 * <p>
 * It also pins {@link PublisherBase#applyEffectiveDependsOn(ImplementationGuide, ImplementationGuide)}:
 * the embedded {@code dependsOn} is rebuilt to mirror the effective (Phase 4) per-target list exactly,
 * so a filtered/reordered effective list is reproduced without any positional (by-index) assumption.
 * <p>
 * The per-target version stamp / packageId suffix half of H3 is pinned by {@code ConvVersionTest}.
 */
class EmbeddedIgFidelityTest {

  private ImplementationGuide igWithResources(String... references) {
    ImplementationGuide ig = new ImplementationGuide();
    ig.setUrl("http://example.org/fhir/ImplementationGuide/example");
    ig.setName("Example");
    ig.setPackageId("example.test");
    ig.setStatus(Enumerations.PublicationStatus.ACTIVE);
    ig.getFhirVersion().add(new Enumeration<>(new Enumerations.FHIRVersionEnumFactory(), "5.0.0"));
    for (String ref : references) {
      ig.getDefinition().addResource().getReference().setReference(ref);
    }
    return ig;
  }

  /** Mirror {@code resourceKeys}: a definition.resource reference (Type/id) resolves to both the
   *  Type/id key and a synthetic canonical URL for that resource. */
  private Set<String> keysOf(String reference) {
    Set<String> keys = new HashSet<>();
    if (reference != null) {
      keys.add(reference);
      keys.add("http://example.org/fhir/" + reference);
    }
    return keys;
  }

  private Set<String> referencesOf(ImplementationGuide ig) {
    return ig.getDefinition().getResource().stream()
        .map(res -> res.getReference().getReference())
        .collect(Collectors.toSet());
  }

  @Test
  void membershipFilter_dropsR5OnlyResourceFromR4AndR4B_keepsInR5() {
    Set<String> r5 = new HashSet<>(Set.of("Patient/r5only"));
    Set<String> r4 = new HashSet<>();
    Set<String> r4b = new HashSet<>();
    Function<String, Set<String>> resolver = this::keysOf;

    ImplementationGuide r4ig = igWithResources("Patient/r5only", "Patient/shared");
    PublisherBase.filterResourceMembership(r4ig, "r4", "5.0.0", resolver, r5, r4, r4b);
    assertEquals(Set.of("Patient/shared"), referencesOf(r4ig), "r5-only resource dropped from R4 package IG");

    ImplementationGuide r4big = igWithResources("Patient/r5only", "Patient/shared");
    PublisherBase.filterResourceMembership(r4big, "r4b", "5.0.0", resolver, r5, r4, r4b);
    assertEquals(Set.of("Patient/shared"), referencesOf(r4big), "r5-only resource dropped from R4B package IG");

    ImplementationGuide r5ig = igWithResources("Patient/r5only", "Patient/shared");
    PublisherBase.filterResourceMembership(r5ig, "r5", "5.0.0", resolver, r5, r4, r4b);
    assertEquals(Set.of("Patient/r5only", "Patient/shared"), referencesOf(r5ig),
        "r5-only resource kept in the R5 base package IG");
  }

  @Test
  void membershipFilter_matchesViaCanonicalUrlKey_notJustTypeId() {
    // The inclusion set lists the canonical URL (not the Type/id). The resolver returns both keys, so
    // the URL-keyed r4-only membership must still be honoured for a definition.resource keyed by Type/id.
    Set<String> r5 = new HashSet<>();
    Set<String> r4 = new HashSet<>(Set.of("http://example.org/fhir/Patient/byUrl"));
    Set<String> r4b = new HashSet<>();
    Function<String, Set<String>> resolver = this::keysOf;

    ImplementationGuide r5ig = igWithResources("Patient/byUrl", "Patient/shared");
    PublisherBase.filterResourceMembership(r5ig, "r5", "5.0.0", resolver, r5, r4, r4b);
    assertEquals(Set.of("Patient/shared"), referencesOf(r5ig),
        "URL-keyed r4-only resource dropped from R5 package IG");

    ImplementationGuide r4ig = igWithResources("Patient/byUrl", "Patient/shared");
    PublisherBase.filterResourceMembership(r4ig, "r4", "5.0.0", resolver, r5, r4, r4b);
    assertEquals(Set.of("Patient/byUrl", "Patient/shared"), referencesOf(r4ig),
        "URL-keyed r4-only resource kept in R4 package IG");
  }

  @Test
  void membershipFilter_noOpWhenBaseIsNotR5() {
    Set<String> r5 = new HashSet<>(Set.of("Patient/r5only"));
    ImplementationGuide ig = igWithResources("Patient/r5only", "Patient/shared");
    PublisherBase.filterResourceMembership(ig, "r4", "4.0.1", this::keysOf, r5, new HashSet<>(), new HashSet<>());
    assertEquals(Set.of("Patient/r5only", "Patient/shared"), referencesOf(ig),
        "no membership filtering when the base version is not R5+");
  }

  private ImplementationGuide igWithDeps(String... packageIds) {
    ImplementationGuide ig = new ImplementationGuide();
    for (String id : packageIds) {
      ig.addDependsOn().setPackageId(id).setVersion("1.0.0").setUri("http://example.org/" + id);
    }
    return ig;
  }

  private List<String> depPackageIds(ImplementationGuide ig) {
    return ig.getDependsOn().stream()
        .map(ImplementationGuideDependsOnComponent::getPackageId)
        .collect(Collectors.toList());
  }

  @Test
  void applyEffectiveDependsOn_mirrorsFilteredAndReorderedEffectiveList() {
    // Regression guard: the effective dependsOn is filtered (r-version-inapplicable entries removed)
    // and reordered (auto-added-family dedup) relative to the full generated IG, so the overlay must
    // reproduce the effective list exactly - never align by index (which would keep dropped entries
    // and mislabel survivors).
    ImplementationGuide target = igWithDeps("dep.a", "dep.b", "dep.c", "dep.d");
    ImplementationGuide effective = igWithDeps("dep.d", "dep.b");
    effective.getDependsOn().get(0).setVersion("9.9.9"); // prove values come from effective, not target

    PublisherBase.applyEffectiveDependsOn(target, effective);

    assertEquals(List.of("dep.d", "dep.b"), depPackageIds(target),
        "overlay reproduces the effective (filtered + reordered) dependsOn, not the full list");
    assertEquals("9.9.9", target.getDependsOn().get(0).getVersion(),
        "overlay copies the effective entry's coordinates");
  }

  @Test
  void applyEffectiveDependsOn_nullEffectiveLeavesTargetUntouched() {
    ImplementationGuide target = igWithDeps("dep.a", "dep.b");
    PublisherBase.applyEffectiveDependsOn(target, null);
    assertEquals(List.of("dep.a", "dep.b"), depPackageIds(target),
        "a null effective IG (no Phase 4 snapshot) leaves the generated dependsOn as-is");
  }
}
