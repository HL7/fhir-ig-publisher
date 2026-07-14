package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.model.Enumerations;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link PublisherIGLoader#applyPerVersionDeps(ImplementationGuide, String, String)} - the
 * per-version dependency transform that replaced the historical {@code checkIgDeps} suffix rename.
 * Covers legacy suffix rename (incl. the {@code r4b}->{@code r4} forcing), per-version override,
 * {@code use=remove}, and the version-scoped "R4-only add" (which must be absent from the base R5
 * view), plus token-variant normalisation ({@code 4.0}/{@code r4}/{@code 4.0.1}).
 */
class CheckIgDepsTest {

  private static final String R5 = "5.0.0";

  private ImplementationGuide sampleIg() {
    ImplementationGuide ig = new ImplementationGuide();
    ig.getFhirVersion().add(new Enumeration<>(new Enumerations.FHIRVersionEnumFactory(), R5));

    // (a) plain dep, no extension -> legacy suffix rename for variants, unchanged for the base
    ImplementationGuideDependsOnComponent plain = ig.addDependsOn();
    plain.setUri("http://example.org/plain");
    plain.setPackageId("test.plain.r5");
    plain.setVersion("1.0.0");

    // (b) present in R5 as authored; overridden for R4; absent from R4B (no R4B occurrence)
    ImplementationGuideDependsOnComponent override = ig.addDependsOn();
    override.setUri("http://example.org/override");
    override.setPackageId("test.over.r5");
    override.setVersion("1.0.0");
    addOccurrence(override, "5.0.0", null, null, null);
    addOccurrence(override, "4.0.1", "test.over.explicit.r4", "9.9.9", null);

    // (c) present in R5 + R4, explicitly removed from R4B via use=remove
    ImplementationGuideDependsOnComponent remove = ig.addDependsOn();
    remove.setUri("http://example.org/remove");
    remove.setPackageId("test.rem.r5");
    remove.setVersion("1.0.0");
    addOccurrence(remove, "5.0.0", null, null, null);
    addOccurrence(remove, "4.0.1", null, null, null);
    addOccurrence(remove, "4.3.0", null, null, "remove");

    // (d) R4-only add: absent from the base R5 view and R4B, present only for R4
    ImplementationGuideDependsOnComponent add = ig.addDependsOn();
    add.setUri("http://example.org/add");
    add.setPackageId("test.add.r4");
    add.setVersion("3.0.0");
    addOccurrence(add, "4.0.1", null, null, null);

    return ig;
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

  private ImplementationGuideDependsOnComponent byUri(ImplementationGuide ig, String uri) {
    for (ImplementationGuideDependsOnComponent d : ig.getDependsOn()) {
      if (uri.equals(d.getUri())) {
        return d;
      }
    }
    return null;
  }

  @Test
  void baseView_dropsR4OnlyAdd_keepsOthersAsAuthored() {
    ImplementationGuide ig = sampleIg();
    PublisherIGLoader.applyPerVersionDeps(ig, R5, R5);
    assertEquals(3, ig.getDependsOn().size());
    assertEquals("test.plain.r5", byUri(ig, "http://example.org/plain").getPackageId());
    assertEquals("test.over.r5", byUri(ig, "http://example.org/override").getPackageId());
    assertEquals("test.rem.r5", byUri(ig, "http://example.org/remove").getPackageId());
    assertNull(byUri(ig, "http://example.org/add")); // R4-only add absent from the base view (F1)
  }

  @Test
  void r4View_appliesRenameOverrideAndAdd() {
    ImplementationGuide ig = sampleIg();
    PublisherIGLoader.applyPerVersionDeps(ig, "r4", R5);
    assertEquals(4, ig.getDependsOn().size());
    assertEquals("test.plain.r4", byUri(ig, "http://example.org/plain").getPackageId()); // legacy suffix rename
    ImplementationGuideDependsOnComponent over = byUri(ig, "http://example.org/override");
    assertEquals("test.over.explicit.r4", over.getPackageId());
    assertEquals("9.9.9", over.getVersion());
    assertEquals("test.rem.r5", byUri(ig, "http://example.org/remove").getPackageId()); // bare occurrence: no rename
    ImplementationGuideDependsOnComponent add = byUri(ig, "http://example.org/add");
    assertEquals("test.add.r4", add.getPackageId());
    assertEquals("3.0.0", add.getVersion());
  }

  @Test
  void r4bView_forcesSuffixToR4_andHonoursRemove() {
    ImplementationGuide ig = sampleIg();
    PublisherIGLoader.applyPerVersionDeps(ig, "r4b", R5);
    assertEquals(1, ig.getDependsOn().size());
    assertEquals("test.plain.r4", byUri(ig, "http://example.org/plain").getPackageId()); // r4b->r4 forcing preserved
    assertNull(byUri(ig, "http://example.org/override")); // version-scoped, no R4B occurrence
    assertNull(byUri(ig, "http://example.org/remove"));   // use=remove for 4.3.0
    assertNull(byUri(ig, "http://example.org/add"));      // R4-only
  }

  @Test
  void tokenVariants_normaliseToSameFamily() {
    // "4.0", "r4" and "4.0.1" must all select the R4 override occurrence (authored as 4.0.1)
    for (String token : new String[] {"4.0", "r4", "4.0.1"}) {
      ImplementationGuide ig = sampleIg();
      PublisherIGLoader.applyPerVersionDeps(ig, token, R5);
      ImplementationGuideDependsOnComponent over = byUri(ig, "http://example.org/override");
      assertEquals("test.over.explicit.r4", over.getPackageId(), "token " + token);
      assertEquals("9.9.9", over.getVersion(), "token " + token);
    }
  }

  @Test
  void plainDep_numericTokens_useFamilySuffix() {
    // Numeric generate-version tokens must yield the same legacy suffix rename as the symbolic
    // spellings: the plain R5 dep renames to .r4 (R4B family forced to .r4), never to a raw
    // ".4.0"/".4.3.0"-style suffix that names a package which does not exist upstream (M2).
    for (String token : new String[] {"4.0", "4.0.1", "4.3", "4.3.0"}) {
      ImplementationGuide ig = sampleIg();
      PublisherIGLoader.applyPerVersionDeps(ig, token, R5);
      assertEquals("test.plain.r4", byUri(ig, "http://example.org/plain").getPackageId(), "token " + token);
    }
  }
}
