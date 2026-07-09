package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.model.Enumerations;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.hl7.fhir.r5.model.MarkdownType;
import org.junit.jupiter.api.Test;

/**
 * Pins the version-appropriate auto-dependency behaviour of {@link PublisherIGLoader} for
 * multi-version builds (feature slot {@code 0709-21}):
 * <ul>
 *   <li>the per-version dedup in {@link PublisherIGLoader#applyPerVersionDeps} - a variant whose
 *       version the author covered must end up with the author's single applicable entry, never a
 *       duplicate {@code packageId} (which would crash package.json generation); and</li>
 *   <li>the applicability-aware auto-add guard ({@link PublisherIGLoader#autoDepGuardView} feeding
 *       the {@code dependsOn*} family predicates) - a dependency scoped to another version must not
 *       suppress the base package's auto-add, while single-version builds keep the legacy behaviour.</li>
 * </ul>
 * Tooling has no variant-package coverage because it is added as an internal build-time dependency
 * ({@code EXT_IGINTERNAL_DEPENDENCY}, see {@code PublisherIGLoader} ~1042/1050/1090) and is never
 * written to a package manifest ({@code NPMPackageGenerator} iterates only {@code ig.getDependsOn()}),
 * so it can never collide there.
 */
class PublisherIGLoaderAutoDepsTest {

  private static final String R5 = "5.0.0";

  private static final String UTG_MARKER = "hl7.terminology";
  private static final String UTG_URI = "http://terminology.hl7.org/ImplementationGuide/hl7.terminology";
  private static final String EXT_MARKER = "hl7.fhir.uv.extensions";
  private static final String EXT_URI = "http://hl7.org/fhir/extensions/ImplementationGuide/hl7.fhir.uv.extensions";

  // ---------------------------------------------------------------------------------------------
  // Phase 1 - per-version family dedup in applyPerVersionDeps
  // ---------------------------------------------------------------------------------------------

  @Test
  void dedup_r4Variant_authorEntryWins_noDuplicatePackageId() {
    assertR4VariantAuthorWins(UTG_MARKER, UTG_URI, "hl7.terminology.r5", PublisherIGLoader.AUTO_DEP_COMMENT_UTG, "hl7.terminology.r4");
    assertR4VariantAuthorWins(EXT_MARKER, EXT_URI, "hl7.fhir.uv.extensions.r5", PublisherIGLoader.AUTO_DEP_COMMENT_EXTENSIONS, "hl7.fhir.uv.extensions.r4");
  }

  private void assertR4VariantAuthorWins(String familyMarker, String autoUri, String autoPkgR5, String autoComment, String authorPkgR4) {
    // base R5 view: the R4-only author entry is not applicable -> only the auto entry survives, untouched
    ImplementationGuide base = scopedIg(autoUri, autoPkgR5, autoComment, authorPkgR4);
    PublisherIGLoader.applyPerVersionDeps(base, R5, R5);
    List<ImplementationGuideDependsOnComponent> baseFam = family(base, familyMarker);
    assertEquals(1, baseFam.size(), familyMarker + " base view size");
    assertEquals(autoPkgR5, baseFam.get(0).getPackageId(), familyMarker + " base keeps the auto entry");
    assertTrue(isAuto(baseFam.get(0), autoComment), familyMarker + " base survivor is the auto entry");

    // R4 variant: the author's applicable entry wins, the carried auto entry is dropped, no duplicate id
    ImplementationGuide r4 = scopedIg(autoUri, autoPkgR5, autoComment, authorPkgR4);
    PublisherIGLoader.applyPerVersionDeps(r4, "r4", R5);
    List<ImplementationGuideDependsOnComponent> r4Fam = family(r4, familyMarker);
    assertEquals(1, r4Fam.size(), familyMarker + " r4 view size");
    assertEquals(authorPkgR4, r4Fam.get(0).getPackageId(), familyMarker + " r4 survivor is the author's entry");
    assertEquals("6.1.0", r4Fam.get(0).getVersion(), familyMarker + " r4 survivor keeps the author's pinned version");
    assertFalse(isAuto(r4Fam.get(0), autoComment), familyMarker + " r4 survivor is not the auto entry");
    assertNoDuplicatePackageId(r4);

    // R4B variant: R4-only author still not applicable -> the auto entry survives, forced to .r4 (legacy rule)
    ImplementationGuide r4b = scopedIg(autoUri, autoPkgR5, autoComment, authorPkgR4);
    PublisherIGLoader.applyPerVersionDeps(r4b, "r4b", R5);
    List<ImplementationGuideDependsOnComponent> r4bFam = family(r4b, familyMarker);
    assertEquals(1, r4bFam.size(), familyMarker + " r4b view size");
    assertTrue(isAuto(r4bFam.get(0), autoComment), familyMarker + " r4b survivor is the auto entry");
  }

  @Test
  void dedup_copiedAutoComment_stillNoDuplicatePackageId() {
    // Part-B safety net: an author who copied the auto-comment onto their own R4-only entry is still
    // classified as "auto" by part-A (so part-A removes neither), which would leave two entries with
    // an identical final packageId (hl7.terminology.r4) and crash packaging. Part-B collapses them.
    ImplementationGuide ig = baseIg();
    ImplementationGuideDependsOnComponent author = addAuthorScoped(ig, "hl7.terminology.r4", "6.1.0", "4.0.1");
    author.addExtension(ExtensionDefinitions.EXT_IGDEP_COMMENT, new MarkdownType(PublisherIGLoader.AUTO_DEP_COMMENT_UTG));
    insertAuto(ig, UTG_URI, "hl7.terminology.r5", "9.9.9", PublisherIGLoader.AUTO_DEP_COMMENT_UTG);

    PublisherIGLoader.applyPerVersionDeps(ig, "r4", R5);

    List<ImplementationGuideDependsOnComponent> utg = family(ig, UTG_MARKER);
    assertEquals(1, utg.size());
    assertEquals("hl7.terminology.r4", utg.get(0).getPackageId());
    assertEquals("6.1.0", utg.get(0).getVersion(), "part-B keeps the last (authored) occurrence");
    assertNoDuplicatePackageId(ig);
  }

  @Test
  void dedup_differentPackageIdSameFamily_authorWins() {
    // Pins that part-A is family-level, not exact-id: the author declares the umbrella extensions id
    // (in-family, but not identical to the auto entry's .r4 rename), so part-A - not part-B - drops
    // the auto entry.
    ImplementationGuide ig = baseIg();
    addAuthorScoped(ig, "hl7.fhir.uv.extensions", "5.2.0", "4.0.1");
    insertAuto(ig, EXT_URI, "hl7.fhir.uv.extensions.r5", "9.9.9", PublisherIGLoader.AUTO_DEP_COMMENT_EXTENSIONS);

    PublisherIGLoader.applyPerVersionDeps(ig, "r4", R5);

    List<ImplementationGuideDependsOnComponent> ext = family(ig, EXT_MARKER);
    assertEquals(1, ext.size());
    assertEquals("hl7.fhir.uv.extensions", ext.get(0).getPackageId(), "author's non-identical same-family id survives");
    assertEquals("5.2.0", ext.get(0).getVersion());
    assertFalse(isAuto(ext.get(0), PublisherIGLoader.AUTO_DEP_COMMENT_EXTENSIONS));
  }

  @Test
  void dedup_baseAndR4bViews_keepSingleAutoEntry() {
    // When no author entry is applicable to the view, the base and the uncovered R4B variant keep
    // exactly the auto entry (dedup is a no-op).
    ImplementationGuide base = baseIg();
    addAuthorScoped(base, "hl7.terminology.r4", "6.1.0", "4.0.1");
    insertAuto(base, UTG_URI, "hl7.terminology.r5", "9.9.9", PublisherIGLoader.AUTO_DEP_COMMENT_UTG);
    PublisherIGLoader.applyPerVersionDeps(base, R5, R5);
    List<ImplementationGuideDependsOnComponent> baseUtg = family(base, UTG_MARKER);
    assertEquals(1, baseUtg.size());
    assertEquals("hl7.terminology.r5", baseUtg.get(0).getPackageId());
    assertTrue(isAuto(baseUtg.get(0), PublisherIGLoader.AUTO_DEP_COMMENT_UTG));

    ImplementationGuide r4b = baseIg();
    addAuthorScoped(r4b, "hl7.terminology.r4", "6.1.0", "4.0.1");
    insertAuto(r4b, UTG_URI, "hl7.terminology.r5", "9.9.9", PublisherIGLoader.AUTO_DEP_COMMENT_UTG);
    PublisherIGLoader.applyPerVersionDeps(r4b, "r4b", R5);
    List<ImplementationGuideDependsOnComponent> r4bUtg = family(r4b, UTG_MARKER);
    assertEquals(1, r4bUtg.size());
    assertEquals("hl7.terminology.r4", r4bUtg.get(0).getPackageId(), "auto entry forced to .r4 for the R4B view");
    assertTrue(isAuto(r4bUtg.get(0), PublisherIGLoader.AUTO_DEP_COMMENT_UTG));
  }

  // ---------------------------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------------------------

  private ImplementationGuide baseIg() {
    ImplementationGuide ig = new ImplementationGuide();
    ig.getFhirVersion().add(new Enumeration<>(new Enumerations.FHIRVersionEnumFactory(), R5));
    return ig;
  }

  /** An R5 base IG carrying an un-scoped auto-marked entry (at index 0) plus an author R4-only entry. */
  private ImplementationGuide scopedIg(String autoUri, String autoPkgR5, String autoComment, String authorPkgR4) {
    ImplementationGuide ig = baseIg();
    addAuthorScoped(ig, authorPkgR4, "6.1.0", "4.0.1");
    insertAuto(ig, autoUri, autoPkgR5, "9.9.9", autoComment);
    return ig;
  }

  /** A version-scoped author dependency (single {@code EXT_IG_DEP_VERSION} occurrence, no override). */
  private ImplementationGuideDependsOnComponent addAuthorScoped(ImplementationGuide ig, String packageId, String version, String occurrenceFhirVersion) {
    ImplementationGuideDependsOnComponent dep = ig.addDependsOn();
    dep.setUri("http://example.org/pin/" + packageId);
    dep.setPackageId(packageId);
    dep.setVersion(version);
    Extension occ = new Extension(PublisherIGLoader.EXT_IG_DEP_VERSION);
    ExtensionUtilities.setStringExtension(occ, "fhirVersion", occurrenceFhirVersion);
    dep.addExtension(occ);
    return dep;
  }

  /** An auto-added dependency, inserted at index 0 exactly as {@code initializeFromIg} does. */
  private ImplementationGuideDependsOnComponent insertAuto(ImplementationGuide ig, String uri, String packageId, String version, String autoComment) {
    ImplementationGuideDependsOnComponent dep = new ImplementationGuideDependsOnComponent();
    dep.setPackageId(packageId);
    dep.setUri(uri);
    dep.setVersion(version);
    dep.addExtension(ExtensionDefinitions.EXT_IGDEP_COMMENT, new MarkdownType(autoComment));
    ig.getDependsOn().add(0, dep);
    return dep;
  }

  private List<ImplementationGuideDependsOnComponent> family(ImplementationGuide ig, String packageMarker) {
    List<ImplementationGuideDependsOnComponent> r = new ArrayList<>();
    for (ImplementationGuideDependsOnComponent d : ig.getDependsOn()) {
      if (d.getPackageId() != null && d.getPackageId().contains(packageMarker)) {
        r.add(d);
      }
    }
    return r;
  }

  private boolean isAuto(ImplementationGuideDependsOnComponent dep, String autoComment) {
    return autoComment.equals(ExtensionUtilities.readStringExtension(dep, ExtensionDefinitions.EXT_IGDEP_COMMENT));
  }

  private void assertNoDuplicatePackageId(ImplementationGuide ig) {
    Set<String> seen = new HashSet<>();
    for (ImplementationGuideDependsOnComponent d : ig.getDependsOn()) {
      if (d.getPackageId() != null) {
        assertTrue(seen.add(d.getPackageId()), "duplicate packageId would crash package.json: " + d.getPackageId());
      }
    }
  }
}
