package org.hl7.fhir.igtools.renderers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hl7.fhir.igtools.publisher.PublisherIGLoader;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.model.Enumerations;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.junit.jupiter.api.Test;

/**
 * Pins the per-version dependency reporting helpers used by {@link DependencyRenderer}. The full
 * {@code render}/{@code renderNonTech} paths need a package cache + RenderingContext, so the pure,
 * extracted helpers ({@code perVersionNote}, {@code baseVersionKey}) and the shared base-view skip
 * predicate are unit-tested directly instead.
 */
class DependencyRendererPerVersionTest {

  private ImplementationGuideDependsOnComponent dep(String uri, String packageId, String version) {
    ImplementationGuideDependsOnComponent d = new ImplementationGuideDependsOnComponent();
    d.setUri(uri);
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

  @Test
  void perVersionNote_showsOverridePackageAndVersion() {
    ImplementationGuideDependsOnComponent d = dep("http://example.org/override", "test.over.r5", "1.0.0");
    addOccurrence(d, "4.0.1", "test.over.explicit.r4", "9.9.9", null);
    String note = DependencyRenderer.perVersionNote(d);
    assertTrue(note.contains("R4"), note);
    assertTrue(note.contains("test.over.explicit.r4#9.9.9"), note);
  }

  @Test
  void perVersionNote_showsNotUsedForRemove() {
    ImplementationGuideDependsOnComponent d = dep("http://example.org/remove", "test.rem.r5", "1.0.0");
    addOccurrence(d, "4.3.0", null, null, "remove");
    assertTrue(DependencyRenderer.perVersionNote(d).contains("not used in R4B"));
  }

  @Test
  void perVersionNote_emptyWhenNoExtension() {
    assertEquals("", DependencyRenderer.perVersionNote(dep("http://example.org/plain", "test.plain.r5", "1.0.0")));
  }

  @Test
  void baseView_excludesR4OnlyAddFromR5Table() {
    ImplementationGuide ig = new ImplementationGuide();
    ig.getFhirVersion().add(new Enumeration<>(new Enumerations.FHIRVersionEnumFactory(), "5.0.0"));
    ImplementationGuideDependsOnComponent add = dep("http://example.org/add", "test.add.r4", "3.0.0");
    addOccurrence(add, "4.0.1", null, null, null);
    ig.getDependsOn().add(add);

    String baseVer = DependencyRenderer.baseVersionKey(ig);
    assertEquals("r5", baseVer);
    // the renderer skips deps not applicable to the base version, so the R4-only add is not listed
    // as an R5 dependency (agreeing with the R5 package.json produced in Phase 2).
    assertFalse(PublisherIGLoader.isDepApplicableForVersion(add, baseVer));
  }

  @Test
  void baseView_listsOverriddenDepWithEffectivePackageId() {
    // 0709-09 H2: the base dependency table is rendered from the *effective* base IG (pf.effectiveBaseIg),
    // so a base-version override must (a) still pass the renderer's base-view applicability filter and
    // (b) carry the overridden packageId that render() resolves and shows - not the raw one.
    ImplementationGuide ig = new ImplementationGuide();
    ig.getFhirVersion().add(new Enumeration<>(new Enumerations.FHIRVersionEnumFactory(), "5.0.0"));
    ImplementationGuideDependsOnComponent dep = dep("http://example.org/base", "test.base.r5", "1.0.0");
    addOccurrence(dep, "5.0.0", "test.base.override", "2.0.0", null);
    ig.getDependsOn().add(dep);

    // build the effective base view exactly as PublisherIGLoader does for pf.effectiveBaseIg
    PublisherIGLoader.applyPerVersionDeps(ig, "5.0.0", "5.0.0");

    String baseVer = DependencyRenderer.baseVersionKey(ig);
    assertEquals("r5", baseVer);
    ImplementationGuideDependsOnComponent effective = ig.getDependsOn().get(0);
    assertTrue(PublisherIGLoader.isDepApplicableForVersion(effective, baseVer), "the overridden dep is still listed by the base table");
    assertEquals("test.base.override", effective.getPackageId(), "render() resolves the overridden packageId");
    assertEquals("2.0.0", effective.getVersion(), "render() resolves the overridden version");
  }
}
