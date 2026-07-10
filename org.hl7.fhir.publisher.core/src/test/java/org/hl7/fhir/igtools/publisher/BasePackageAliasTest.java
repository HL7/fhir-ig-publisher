package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Date;

import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.model.Enumerations;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.hl7.fhir.r5.utils.NPMPackageGenerator;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.PackageGenerator.PackageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the npm-alias marker fix (feature slot {@code 0709-09} H1).
 * <p>
 * A {@code dependsOn} authored as {@code alias@npm:real.package} is normalised at load time to
 * {@code id=alias}/{@code packageId=real.package} with the transient {@link UserDataNames#IG_DEP_ALIASED}
 * marker on its {@code packageId} element; {@code NPMPackageGenerator} reads that marker to re-emit
 * the {@code <id>@npm:<packageId>} npm-alias form in {@code package.json}. Every per-version/base copy
 * of the IG is taken by {@link ImplementationGuide#copy()} <em>before</em> rendering flips
 * {@code Base.copyUserData} on, so the copy silently drops the marker and the emitted manifest
 * downgrades to the bare {@code packageId}. {@link PublisherIGLoader#preserveAliasUserData} restores
 * it. These tests pin both the marker round-trip and the end-to-end serialisation, hermetically
 * (no network, {@code @TempDir}).
 */
class BasePackageAliasTest {

  /**
   * The marker only survives {@link ImplementationGuide#copy()} when the {@code Base.copyUserData}
   * ThreadLocal is on (it is, only during rendering). Pin it off so the "plain copy drops the
   * marker" precondition is deterministic regardless of what else ran in this JVM fork.
   */
  @BeforeEach
  void pinCopyUserDataOff() {
    Base.setCopyUserData(false);
  }

  // ---------------------------------------------------------------------------------------------
  // preserveAliasUserData round-trip
  // ---------------------------------------------------------------------------------------------

  @Test
  void plainCopy_dropsAliasMarker_preserveRestoresIt() {
    ImplementationGuide ig = igWithAliasedDep();
    assertTrue(marker(ig, 0), "precondition: source dep carries the alias marker");

    ImplementationGuide copy = ig.copy();
    assertFalse(marker(copy, 0), "a plain copy() loses the transient IG_DEP_ALIASED marker (the regression)");

    PublisherIGLoader.preserveAliasUserData(ig, copy);
    assertTrue(marker(copy, 0), "preserveAliasUserData restores the marker onto the copy at the same index");
  }

  @Test
  void preserve_onlyMarksAliasedEntries_positionally() {
    ImplementationGuide ig = new ImplementationGuide();
    addDep(ig, null, "plain.package", "1.0.0", false);  // index 0: not aliased
    addDep(ig, "alias", "real.package", "2.0.0", true); // index 1: aliased
    ImplementationGuide copy = ig.copy();
    assertFalse(marker(copy, 0));
    assertFalse(marker(copy, 1));

    PublisherIGLoader.preserveAliasUserData(ig, copy);
    assertFalse(marker(copy, 0), "a non-aliased entry stays unmarked");
    assertTrue(marker(copy, 1), "the aliased entry is (re)marked at the same index");
  }

  @Test
  void preserve_isNullSafeAndToleratesShorterLists() {
    ImplementationGuide from = igWithAliasedDep();
    ImplementationGuide to = new ImplementationGuide(); // no deps
    PublisherIGLoader.preserveAliasUserData(from, to);  // must not throw on a shorter target
    assertTrue(to.getDependsOn().isEmpty());
    PublisherIGLoader.preserveAliasUserData(null, to);  // null from
    PublisherIGLoader.preserveAliasUserData(from, null); // null to
  }

  // ---------------------------------------------------------------------------------------------
  // end-to-end: the marker drives the @npm: alias form in package.json
  // ---------------------------------------------------------------------------------------------

  @Test
  void markedDependency_serializesAsNpmAlias(@TempDir File tempDir) throws Exception {
    JsonObject deps = dependenciesOf(igWithAliasedDep(), tempDir);
    assertNotNull(deps, "manifest declares dependencies");
    assertEquals("1.0.0", deps.asString("alias@npm:real.package"),
        "a marked dep serialises as <id>@npm:<packageId> (npm-alias form)");
    assertFalse(deps.has("real.package"), "the bare packageId form is not emitted for an aliased dep");
  }

  @Test
  void unmarkedCopy_degradesToBarePackageId(@TempDir File tempDir) throws Exception {
    // The regression this pins: without the marker (as a plain copy leaves it) the base manifest
    // silently downgrades the aliased dep to its bare packageId, losing the npm alias.
    ImplementationGuide copy = igWithAliasedDep().copy(); // drops the marker
    JsonObject deps = dependenciesOf(copy, tempDir);
    assertNotNull(deps);
    assertEquals("1.0.0", deps.asString("real.package"),
        "without the marker the dep degrades to the bare packageId");
    assertFalse(deps.has("alias@npm:real.package"), "no alias form without the marker");
  }

  @Test
  void preservedCopy_serializesAsNpmAlias(@TempDir File tempDir) throws Exception {
    // The fix in the round-trip the publisher actually performs: copy (drops marker) then restore.
    ImplementationGuide from = igWithAliasedDep();
    ImplementationGuide copy = from.copy();
    PublisherIGLoader.preserveAliasUserData(from, copy);
    JsonObject deps = dependenciesOf(copy, tempDir);
    assertEquals("1.0.0", deps.asString("alias@npm:real.package"),
        "a copy through preserveAliasUserData re-emits the npm-alias form");
    assertFalse(deps.has("real.package"), "the bare packageId form is not emitted after preservation");
  }

  // ---------------------------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------------------------

  private ImplementationGuide igWithAliasedDep() {
    ImplementationGuide ig = new ImplementationGuide();
    ig.setId("example");
    ig.setUrl("http://example.org/fhir/ImplementationGuide/example");
    ig.setName("Example");
    ig.setPackageId("example.test");
    ig.setVersion("0.1.0");
    ig.getFhirVersion().add(new Enumeration<>(new Enumerations.FHIRVersionEnumFactory(), "5.0.0"));
    addDep(ig, "alias", "real.package", "1.0.0", true);
    return ig;
  }

  private void addDep(ImplementationGuide ig, String id, String packageId, String version, boolean aliased) {
    ImplementationGuideDependsOnComponent dep = ig.addDependsOn();
    if (id != null) {
      dep.setId(id);
    }
    dep.setPackageId(packageId);
    dep.setVersion(version);
    dep.setUri("http://example.org/fhir/ImplementationGuide/" + packageId);
    if (aliased) {
      dep.getPackageIdElement().setUserData(UserDataNames.IG_DEP_ALIASED, true);
    }
  }

  private boolean marker(ImplementationGuide ig, int i) {
    return ig.getDependsOn().get(i).getPackageIdElement().hasUserData(UserDataNames.IG_DEP_ALIASED);
  }

  private JsonObject dependenciesOf(ImplementationGuide ig, File dir) throws Exception {
    File out = new File(dir, ig.getPackageId() + "-" + System.nanoTime() + ".tgz");
    NPMPackageGenerator gen = new NPMPackageGenerator(ig.getPackageId(), out.getAbsolutePath(),
        "http://example.org/fhir", "http://example.org/fhir", PackageType.IG, ig, new Date(), null, true);
    gen.finish();
    return gen.getPackageJ().getJsonObject("dependencies");
  }
}
