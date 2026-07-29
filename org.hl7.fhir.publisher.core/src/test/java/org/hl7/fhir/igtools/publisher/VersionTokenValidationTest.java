package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.junit.jupiter.api.Test;

/**
 * Pins the author version-token guards added for {@code 0709-08} H1: {@code canonicalTargetOrNull}
 * never throws and folds unknown families to {@code null}; {@code versionTokenIssue} differentiates
 * ERROR (generate-version) vs WARNING (dependency fhirVersion); and both {@code dependsOn} occurrence
 * consumers ({@code isDepApplicableForVersion} and {@code applyPerVersionDeps}) tolerate an
 * unresolvable {@code fhirVersion} without throwing and without silently dropping the dependency.
 */
class VersionTokenValidationTest {

  private ImplementationGuideDependsOnComponent dep(String packageId, String version) {
    ImplementationGuideDependsOnComponent d = new ImplementationGuideDependsOnComponent();
    d.setUri("http://example.org/" + packageId);
    d.setPackageId(packageId);
    d.setVersion(version);
    return d;
  }

  private void addOccurrence(ImplementationGuideDependsOnComponent dep, String fhirVersion, String packageId, String version, String use) {
    Extension occ = new Extension(PublisherIGLoader.EXT_IG_DEP_VERSION);
    ExtensionUtilities.setStringExtension(occ, "fhirVersion", fhirVersion);
    if (packageId != null) {
      ExtensionUtilities.setStringExtension(occ, "packageId", packageId);
    }
    if (version != null) {
      ExtensionUtilities.setStringExtension(occ, "version", version);
    }
    if (use != null) {
      ExtensionUtilities.setStringExtension(occ, "use", use);
    }
    dep.addExtension(occ);
  }

  // --- canonicalTargetOrNull -------------------------------------------------

  @Test
  void canonicalTargetOrNull_resolvesRecognisedVersions() {
    assertEquals("r4", PublisherIGLoader.canonicalTargetOrNull("r4"));
    assertEquals("r4", PublisherIGLoader.canonicalTargetOrNull("4.0.1"));
    assertEquals("r5", PublisherIGLoader.canonicalTargetOrNull("5.0.0"));
    assertEquals("r4b", PublisherIGLoader.canonicalTargetOrNull("r4b"));
  }

  @Test
  void canonicalTargetOrNull_nullForUnknownOrMalformed_neverThrows() {
    // valid semver but unknown family
    assertNull(assertDoesNotThrow(() -> PublisherIGLoader.canonicalTargetOrNull("4.2.0")));
    assertNull(assertDoesNotThrow(() -> PublisherIGLoader.canonicalTargetOrNull("2.0")));
    // non-semver tokens (getNameForVersion throws) must be swallowed to null
    assertNull(assertDoesNotThrow(() -> PublisherIGLoader.canonicalTargetOrNull("44")));
    assertNull(assertDoesNotThrow(() -> PublisherIGLoader.canonicalTargetOrNull("foo")));
  }

  // --- versionTokenIssue -----------------------------------------------------

  @Test
  void versionTokenIssue_nullForValidTokens() {
    assertNull(PublisherIGLoader.versionTokenIssue("r4", "path", true));
    assertNull(PublisherIGLoader.versionTokenIssue("4.0.1", "path", false));
  }

  @Test
  void versionTokenIssue_errorForUnknownGenerateVersion() {
    ValidationMessage vm = PublisherIGLoader.versionTokenIssue("4.2.0", "ImplementationGuide.definition.parameter[generate-version]", true);
    assertEquals(ValidationMessage.IssueSeverity.ERROR, vm.getLevel());
    assertEquals(PublisherMessageIds.GENERATE_VERSION_UNKNOWN, vm.getMessageId());
  }

  @Test
  void versionTokenIssue_warningForUnknownDependencyVersion() {
    ValidationMessage vm = PublisherIGLoader.versionTokenIssue("44", "ImplementationGuide.dependsOn[x]", false);
    assertEquals(ValidationMessage.IssueSeverity.WARNING, vm.getLevel());
    assertEquals(PublisherMessageIds.DEPENDENCY_VERSION_UNKNOWN, vm.getMessageId());
  }

  // --- isDepApplicableForVersion (throw-safe, unresolvable == absent) --------

  @Test
  void isDepApplicable_noExtensionAppliesToAll() {
    ImplementationGuideDependsOnComponent d = dep("test.plain", "1.0.0");
    assertTrue(PublisherIGLoader.isDepApplicableForVersion(d, "r5"));
    assertTrue(PublisherIGLoader.isDepApplicableForVersion(d, "r4"));
  }

  @Test
  void isDepApplicable_unresolvableOccurrenceTreatedAsAbsent_depKept() {
    // only occurrence is an unknown/typo'd version: dep must be kept (applies to all), not dropped
    ImplementationGuideDependsOnComponent d = dep("test.typo", "1.0.0");
    addOccurrence(d, "44", null, null, null);
    assertTrue(assertDoesNotThrow(() -> PublisherIGLoader.isDepApplicableForVersion(d, "r5")));
    assertTrue(assertDoesNotThrow(() -> PublisherIGLoader.isDepApplicableForVersion(d, "r4")));

    ImplementationGuideDependsOnComponent d2 = dep("test.unknownfamily", "1.0.0");
    addOccurrence(d2, "4.2.0", null, null, null);
    assertTrue(PublisherIGLoader.isDepApplicableForVersion(d2, "r5"));
  }

  @Test
  void isDepApplicable_resolvableOccurrenceStillScopes() {
    // a resolvable r4 occurrence scopes the dep to r4 only (unchanged legacy behaviour)
    ImplementationGuideDependsOnComponent d = dep("test.scoped", "1.0.0");
    addOccurrence(d, "4.0.1", null, null, null);
    assertTrue(PublisherIGLoader.isDepApplicableForVersion(d, "r4"));
    assertFalse(PublisherIGLoader.isDepApplicableForVersion(d, "r5"));
    // an unresolvable sibling occurrence does not change the r4 scoping
    addOccurrence(d, "44", null, null, null);
    assertTrue(PublisherIGLoader.isDepApplicableForVersion(d, "r4"));
    assertFalse(PublisherIGLoader.isDepApplicableForVersion(d, "r5"));
  }

  // --- applyPerVersionDeps ordered-mixed occurrences (no throw at override loop)

  @Test
  void applyPerVersionDeps_orderedMixedOccurrences_doesNotThrow() {
    // A non-semver token before the matching r4 token: with the old canonicalTarget() call the
    // override loop would throw FHIRException on "44" before reaching "4.0.1".
    ImplementationGuide ig = new ImplementationGuide();
    ImplementationGuideDependsOnComponent d = dep("test.mixed.r5", "1.0.0");
    addOccurrence(d, "44", "test.mixed.bogus", "0.0.0", null);
    addOccurrence(d, "4.0.1", "test.mixed.r4", "9.9.9", null);
    ig.getDependsOn().add(d);

    assertDoesNotThrow(() -> PublisherIGLoader.applyPerVersionDeps(ig, "r4", "5.0.0"));
    assertEquals(1, ig.getDependsOn().size());
    assertEquals("test.mixed.r4", ig.getDependsOn().get(0).getPackageId());
    assertEquals("9.9.9", ig.getDependsOn().get(0).getVersion());
  }

  @Test
  void applyPerVersionDeps_unknownFamilyBeforeMatch_doesNotThrow() {
    // The plan's literal example (4.2.0 -> R?), which resolves without throwing but must still be
    // skipped so the matching r4 override wins.
    ImplementationGuide ig = new ImplementationGuide();
    ImplementationGuideDependsOnComponent d = dep("test.unk.r5", "1.0.0");
    addOccurrence(d, "4.2.0", "test.unk.bogus", "0.0.0", null);
    addOccurrence(d, "4.0.1", "test.unk.r4", "9.9.9", null);
    ig.getDependsOn().add(d);

    assertDoesNotThrow(() -> PublisherIGLoader.applyPerVersionDeps(ig, "r4", "5.0.0"));
    assertEquals("test.unk.r4", ig.getDependsOn().get(0).getPackageId());
  }

  // --- validateDependencyVersionTokens (ingestion warning, once per build) ---

  @Test
  void validateDependencyVersionTokens_warnsOncePerUnresolvedOccurrence() {
    ImplementationGuide ig = new ImplementationGuide();
    ImplementationGuideDependsOnComponent bad = dep("test.bad", "1.0.0");
    addOccurrence(bad, "44", null, null, null);
    ImplementationGuideDependsOnComponent good = dep("test.good", "1.0.0");
    addOccurrence(good, "4.0.1", null, null, null);
    ig.getDependsOn().add(bad);
    ig.getDependsOn().add(good);

    List<ValidationMessage> errors = new ArrayList<>();
    PublisherIGLoader.validateDependencyVersionTokens(ig, errors);
    assertEquals(1, errors.size());
    assertEquals(PublisherMessageIds.DEPENDENCY_VERSION_UNKNOWN, errors.get(0).getMessageId());
    assertTrue(errors.get(0).getLocation().contains("test.bad"), errors.get(0).getLocation());
  }
}
