package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.model.Enumerations;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.junit.jupiter.api.Test;

/**
 * Pins the "one effective view for all dependency consumers" fix (feature slot {@code 0709-09} H2).
 * <p>
 * The dependency <i>loader</i> (which must load the overridden package) and the manifest / dependency
 * table / publication check <i>writers</i> now share a single per-entry transform
 * ({@link PublisherIGLoader#applyEffectiveOverride}); {@link PublisherIGLoader#applyPerVersionDeps}
 * (which backs the base/variant manifests) delegates to it, so the two paths cannot diverge. These
 * tests pin (a) the single-entry override, (b) that the load-loop path and the manifest path resolve
 * the <i>identical</i> effective dependency from the same raw source without mutating it, and (c) the
 * {@link PublisherFields#getEffectiveBaseIg()} accessor every base-target consumer reads.
 * <p>
 * <b>Deviation from plan:</b> the plan asked for an assertion on the fragment rendered by the real
 * {@code DependencyRenderer}. Driving {@code DependencyRenderer.render} needs a live
 * {@code IWorkerContext}/{@code RenderingContext} + package cache (the existing
 * {@code DependencyRendererPerVersionTest} deliberately avoids the full render path for the same
 * reason), which is not hermetic. The renderer-side wiring intent is instead covered by (i) the
 * shared-helper equivalence here - the exact dep {@code render} resolves is the manifest dep - and
 * (ii) a base-view filter-agreement test added to {@code DependencyRendererPerVersionTest} using the
 * real renderer's {@code baseVersionKey} the {@code render} loop itself calls.
 */
class PerVersionDependencyConsistencyTest {

  private ImplementationGuide igWithBaseOverride() {
    ImplementationGuide ig = new ImplementationGuide();
    ig.setPackageId("example.test");
    ig.getFhirVersion().add(new Enumeration<>(new Enumerations.FHIRVersionEnumFactory(), "5.0.0"));
    ImplementationGuideDependsOnComponent dep = ig.addDependsOn();
    dep.setId("base");
    dep.setUri("http://example.org/fhir/ImplementationGuide/test.base");
    dep.setPackageId("test.base.r5");
    dep.setVersion("1.0.0");
    addOccurrence(dep, "5.0.0", "test.base.override", "2.0.0", null); // author overrides the base (R5) dep
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

  // ---------------------------------------------------------------------------------------------
  // single-entry effective transform
  // ---------------------------------------------------------------------------------------------

  @Test
  void applyEffectiveOverride_baseVersionOverride_appliesIdAndVersion() {
    ImplementationGuideDependsOnComponent dep = igWithBaseOverride().getDependsOn().get(0);
    PublisherIGLoader.applyEffectiveOverride(dep, "5.0.0", "5.0.0");
    assertEquals("test.base.override", dep.getPackageId(), "base-version packageId override applied");
    assertEquals("2.0.0", dep.getVersion(), "base-version version override applied");
  }

  @Test
  void applyEffectiveOverride_baseView_noRenameWhenNoMatchingOccurrence() {
    // A plain, un-scoped dep at the base view (targetIsSource): no EXT_IG_DEP_VERSION occurrence, so
    // the legacy suffix rename must NOT fire (that only applies to non-base targets).
    ImplementationGuideDependsOnComponent dep = new ImplementationGuideDependsOnComponent();
    dep.setPackageId("test.plain.r5");
    dep.setVersion("1.0.0");
    PublisherIGLoader.applyEffectiveOverride(dep, "5.0.0", "5.0.0");
    assertEquals("test.plain.r5", dep.getPackageId(), "base view leaves a plain packageId untouched");
    assertEquals("1.0.0", dep.getVersion());
  }

  // ---------------------------------------------------------------------------------------------
  // load loop and manifest resolve the same effective dep (0709-09 H2 core guarantee)
  // ---------------------------------------------------------------------------------------------

  @Test
  void loadLoopAndManifest_resolveSameEffectiveDep_withoutMutatingSource() {
    ImplementationGuide raw = igWithBaseOverride();
    ImplementationGuideDependsOnComponent rawDep = raw.getDependsOn().get(0);

    // load-loop path: copy the raw dep and apply the single-entry transform (as PublisherIGLoader's
    // dependency load loop does before loadIg).
    ImplementationGuideDependsOnComponent loadLoop = rawDep.copy();
    PublisherIGLoader.applyEffectiveOverride(loadLoop, "5.0.0", "5.0.0");

    // manifest/table path: copy the whole IG and apply the per-version transform (as the baseVig that
    // backs pf.effectiveBaseIg does).
    ImplementationGuide manifestIg = raw.copy();
    PublisherIGLoader.applyPerVersionDeps(manifestIg, "5.0.0", "5.0.0");
    ImplementationGuideDependsOnComponent manifestDep = manifestIg.getDependsOn().get(0);

    assertEquals("test.base.override", loadLoop.getPackageId());
    assertEquals("2.0.0", loadLoop.getVersion());
    // the load-loop dep and the manifest dep are identical - both consumers see the same package
    assertEquals(loadLoop.getPackageId(), manifestDep.getPackageId(), "loader and manifest resolve the same packageId");
    assertEquals(loadLoop.getVersion(), manifestDep.getVersion(), "loader and manifest resolve the same version");
    // and the shared raw source dep is never mutated in place by either path
    assertEquals("test.base.r5", rawDep.getPackageId(), "the source IG dependency is left untouched");
    assertEquals("1.0.0", rawDep.getVersion(), "the source IG dependency version is left untouched");
  }

  // ---------------------------------------------------------------------------------------------
  // the accessor every base-target consumer reads
  // ---------------------------------------------------------------------------------------------

  @Test
  void getEffectiveBaseIg_prefersEffective_fallsBackToPublished() {
    PublisherFields pf = new PublisherFields();
    assertNull(pf.getEffectiveBaseIg(), "with neither set, null (nothing to fall back to yet)");

    ImplementationGuide published = new ImplementationGuide();
    published.setPackageId("pub");
    pf.publishedIg = published;
    assertSame(published, pf.getEffectiveBaseIg(), "before the effective view is built, falls back to publishedIg");

    ImplementationGuide effective = new ImplementationGuide();
    effective.setPackageId("eff");
    pf.effectiveBaseIg = effective;
    assertSame(effective, pf.getEffectiveBaseIg(), "once built, returns the effective base IG");
  }
}
