package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.model.Enumerations;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.utils.NPMPackageGenerator;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageGenerator.PackageType;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the publisher's broad FHIR-core-dependency safety net (feature slot {@code 0709-05}).
 * <p>
 * FHIR core's {@code NPMPackageGenerator.packageForVersion} auto-adds the core dependency for
 * R2..R4B but returns {@code null} for R5/R6, so an R5 IG's base/{@code .r5}/per-language packages
 * ship with no {@code hl7.fhir.r5.core}. {@link PublisherBase#patchMissingCoreDependency} patches
 * the expected core dep into a finished manifest <b>only when none is present</b>, so it fixes
 * R5/R6 today and auto-no-ops (never duplicating a key) once upstream core is fixed to add it
 * itself. These tests pin the two static resolvers plus the patch's add/no-op/idempotency/round-trip
 * behaviour, hermetically (JUnit {@code @TempDir}, no network).
 */
class CoreDependencyEnsureTest {

  // ---------------------------------------------------------------------------------------------
  // static resolver: hasFhirCoreDependency
  // ---------------------------------------------------------------------------------------------

  @Test
  void hasFhirCoreDependency_matchesCoreFamiliesOnly() {
    assertFalse(PublisherBase.hasFhirCoreDependency(null), "null dependencies -> false");

    assertTrue(PublisherBase.hasFhirCoreDependency(deps("hl7.fhir.r5.core", "5.0.0")), "r5 core matches");
    assertTrue(PublisherBase.hasFhirCoreDependency(deps("hl7.fhir.r4b.core", "4.3.0")), "r4b core matches");
    assertTrue(PublisherBase.hasFhirCoreDependency(deps("hl7.fhir.r4.core", "4.0.1")), "r4 core matches");

    JsonObject nonCore = new JsonObject();
    nonCore.add("hl7.fhir.uv.extensions.r5", "5.3.0");
    nonCore.add("hl7.terminology.r5", "7.2.0");
    assertFalse(PublisherBase.hasFhirCoreDependency(nonCore), "extensions/terminology are not core deps");

    assertFalse(PublisherBase.hasFhirCoreDependency(new JsonObject()), "empty dependencies -> false");
  }

  // ---------------------------------------------------------------------------------------------
  // static resolver: expectedCoreDependency
  // ---------------------------------------------------------------------------------------------

  @Test
  void expectedCoreDependency_perFamily() {
    assertArrayEquals(new String[] { "hl7.fhir.r5.core", "5.0.0" }, PublisherBase.expectedCoreDependency("5.0.0"));
    assertArrayEquals(new String[] { "hl7.fhir.r4.core", "4.0.1" }, PublisherBase.expectedCoreDependency("4.0.1"));
    assertArrayEquals(new String[] { "hl7.fhir.r4b.core", "4.3.0" }, PublisherBase.expectedCoreDependency("4.3.0"));
    assertArrayEquals(new String[] { "hl7.fhir.r2.core", "1.0.2" }, PublisherBase.expectedCoreDependency("1.0.2"));
    assertArrayEquals(new String[] { "hl7.fhir.r6.core", "6.0.0" }, PublisherBase.expectedCoreDependency("6.0.0"));
    assertNull(PublisherBase.expectedCoreDependency(null), "null version -> null");
    assertNull(PublisherBase.expectedCoreDependency("4.2.0"), "unknown/ballot family -> null (never hl7.fhir.r?.core)");
    assertNull(PublisherBase.expectedCoreDependency("44"), "non-semver -> null (never throws)");
  }

  // ---------------------------------------------------------------------------------------------
  // patch behaviour on a real finished .tgz
  // ---------------------------------------------------------------------------------------------

  @Test
  void patch_r5Package_addsR5Core(@TempDir File tempDir) throws Exception {
    NPMPackageGenerator gen = generatorFor("5.0.0", tempDir, "package.tgz");
    gen.finish();
    JsonObject manifest = gen.getPackageJ();
    assertFalse(PublisherBase.hasFhirCoreDependency(manifest.getJsonObject("dependencies")),
        "precondition: an R5 package has no core dep (the defect this fixes)");

    List<ValidationMessage> messages = new ArrayList<>();
    assertTrue(PublisherBase.patchMissingCoreDependency(manifest, gen.filename(), messages), "R5 package is patched");
    assertTrue(messages.isEmpty(), "a clean add emits no family-corrected warning");

    JsonObject deps = reload(gen.filename()).getNpm().getJsonObject("dependencies");
    assertNotNull(deps, "patched package declares dependencies");
    assertEquals("5.0.0", deps.asString("hl7.fhir.r5.core"), "patched .tgz declares hl7.fhir.r5.core#5.0.0");
    assertEquals("5.0.0", manifest.getJsonObject("dependencies").asString("hl7.fhir.r5.core"),
        "in-memory manifest is mirrored to agree with disk");
  }

  @Test
  void patch_r4Package_noOp_singleCore(@TempDir File tempDir) throws Exception {
    NPMPackageGenerator gen = generatorFor("4.0.1", tempDir, "package.tgz");
    gen.finish();
    JsonObject manifest = gen.getPackageJ();
    // R4 already carries hl7.fhir.r4.core from core's packageForVersion.
    assertTrue(PublisherBase.hasFhirCoreDependency(manifest.getJsonObject("dependencies")),
        "precondition: R4 package already has its core dep");

    assertFalse(PublisherBase.patchMissingCoreDependency(manifest, gen.filename(), null), "R4 package is a no-op");

    JsonObject deps = reload(gen.filename()).getNpm().getJsonObject("dependencies");
    assertEquals("4.0.1", deps.asString("hl7.fhir.r4.core"));
    assertEquals(1, countCoreDeps(deps), "exactly one core dep survives (no duplicate-key crash)");
  }

  @Test
  void patch_idempotent_whenCoreAlreadyPresent() throws Exception {
    // Simulates the post-upstream-fix world: a core dep already present -> no-op, and the file is
    // never even read (so a non-existent path does not throw). Pins the "no conflict once root fixed".
    JsonObject manifest = new JsonObject();
    manifest.add("fhirVersions", java.util.List.of("5.0.0"));
    manifest.forceObject("dependencies").add("hl7.fhir.r5.core", "5.0.0");

    assertFalse(PublisherBase.patchMissingCoreDependency(manifest, "no-such-file.tgz", null),
        "present core dep -> no-op, file untouched");
    assertEquals(1, countCoreDeps(manifest.getJsonObject("dependencies")), "still exactly one core dep");
  }

  @Test
  void patch_preservesOtherFiles(@TempDir File tempDir) throws Exception {
    NPMPackageGenerator gen = generatorFor("5.0.0", tempDir, "package.tgz");
    gen.addFile(NPMPackageGenerator.Category.RESOURCE, "Patient-example.json",
        "{\"resourceType\":\"Patient\",\"id\":\"example\"}".getBytes(StandardCharsets.UTF_8));
    gen.finish();

    assertTrue(PublisherBase.patchMissingCoreDependency(gen.getPackageJ(), gen.filename(), null));

    NpmPackage reloaded = reload(gen.filename());
    assertEquals("5.0.0", reloaded.getNpm().getJsonObject("dependencies").asString("hl7.fhir.r5.core"),
        "patch added the core dep");
    assertTrue(reloaded.hasFile("package", "Patient-example.json"),
        "the seeded resource survives the patch/save round-trip");
  }

  // ---------------------------------------------------------------------------------------------
  // family-aware correction (0709-09 M3 / 0709-08 M1)
  // ---------------------------------------------------------------------------------------------

  @Test
  void patch_wrongFamilyCore_correctedWithWarning(@TempDir File tempDir) throws Exception {
    NPMPackageGenerator gen = generatorFor("5.0.0", tempDir, "package.tgz");
    gen.finish();
    injectDep(gen, "hl7.fhir.r4.core", "4.0.1"); // stale/foreign-family core on an R5 package

    List<ValidationMessage> messages = new ArrayList<>();
    assertTrue(PublisherBase.patchMissingCoreDependency(gen.getPackageJ(), gen.filename(), messages),
        "a wrong-family core triggers a corrective rewrite");

    JsonObject deps = reload(gen.filename()).getNpm().getJsonObject("dependencies");
    assertEquals("5.0.0", deps.asString("hl7.fhir.r5.core"), "expected r5 core is present");
    assertFalse(deps.has("hl7.fhir.r4.core"), "wrong-family r4 core is removed");
    assertEquals(1, countCoreDeps(deps), "exactly one core dep left");
    assertEquals(1, messages.size(), "one family-corrected warning");
    assertEquals(PublisherMessageIds.CORE_DEPENDENCY_FAMILY_CORRECTED, messages.get(0).getMessageId());
  }

  @Test
  void patch_bothExpectedAndWrongFamily_removesWrongLeavesOne(@TempDir File tempDir) throws Exception {
    NPMPackageGenerator gen = generatorFor("5.0.0", tempDir, "package.tgz");
    gen.finish();
    injectDep(gen, "hl7.fhir.r5.core", "5.0.0"); // the expected core...
    injectDep(gen, "hl7.fhir.r4.core", "4.0.1"); // ...plus a stale wrong-family one

    List<ValidationMessage> messages = new ArrayList<>();
    assertTrue(PublisherBase.patchMissingCoreDependency(gen.getPackageJ(), gen.filename(), messages),
        "a stale wrong-family core alongside the expected one triggers a rewrite (no short-circuit no-op)");

    JsonObject deps = reload(gen.filename()).getNpm().getJsonObject("dependencies");
    assertEquals("5.0.0", deps.asString("hl7.fhir.r5.core"), "expected core kept");
    assertFalse(deps.has("hl7.fhir.r4.core"), "wrong-family core removed");
    assertEquals(1, countCoreDeps(deps), "exactly one core dep left (not two)");
    assertEquals(1, messages.size(), "one family-corrected warning");
  }

  @Test
  void patch_unknownVersion_noOp_neverWritesRQCore(@TempDir File tempDir) throws Exception {
    NPMPackageGenerator gen = generatorFor("5.0.0", tempDir, "package.tgz");
    gen.finish();
    JsonObject manifest = gen.getPackageJ();
    manifest.remove("fhirVersions");
    manifest.add("fhirVersions", List.of("4.2.0")); // unknown/ballot family

    List<ValidationMessage> messages = new ArrayList<>();
    assertFalse(PublisherBase.patchMissingCoreDependency(manifest, gen.filename(), messages),
        "an unresolvable version is a clean no-op");
    assertTrue(messages.isEmpty(), "no warning for an unresolvable version");
    JsonObject deps = manifest.getJsonObject("dependencies");
    if (deps != null) {
      assertFalse(deps.has("hl7.fhir.r?.core"), "never writes a malformed hl7.fhir.r?.core");
    }
  }

  @Test
  void patch_r6Version_addsR6Core_roundTrip(@TempDir File tempDir) throws Exception {
    NPMPackageGenerator gen = generatorFor("5.0.0", tempDir, "package.tgz");
    gen.finish();
    JsonObject manifest = gen.getPackageJ();
    manifest.remove("fhirVersions");
    manifest.add("fhirVersions", List.of("6.0.0"));

    assertTrue(PublisherBase.patchMissingCoreDependency(manifest, gen.filename(), null), "R6-expected package is patched");
    JsonObject deps = reload(gen.filename()).getNpm().getJsonObject("dependencies");
    assertEquals("6.0.0", deps.asString("hl7.fhir.r6.core"), "r6 core added and survives the tgz round-trip");
    assertEquals(1, countCoreDeps(deps));
  }

  // ---------------------------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------------------------

  private JsonObject deps(String id, String version) {
    JsonObject o = new JsonObject();
    o.add(id, version);
    return o;
  }

  private NPMPackageGenerator generatorFor(String fhirVersion, File dir, String name) throws Exception {
    ImplementationGuide ig = new ImplementationGuide();
    ig.setId("example");
    ig.setUrl("http://example.org/fhir/ImplementationGuide/example");
    ig.setName("Example");
    ig.setPackageId("example.test");
    ig.setVersion("0.1.0");
    ig.getFhirVersion().add(new Enumeration<>(new Enumerations.FHIRVersionEnumFactory(), fhirVersion));
    File out = new File(dir, name);
    return new NPMPackageGenerator("example.test", out.getAbsolutePath(), "http://example.org/fhir",
        "http://example.org/fhir", PackageType.IG, ig, new Date(), null, true);
  }

  private NpmPackage reload(String filename) throws Exception {
    try (FileInputStream in = new FileInputStream(filename)) {
      return NpmPackage.fromPackage(in);
    }
  }

  /**
   * Inject a dependency directly into a finished {@code .tgz}'s {@code package.json} and mirror it into
   * the generator's in-memory manifest, simulating a package that shipped with a (wrong-family) core
   * dependency - version-agnostic so it does not depend on {@code NPMPackageGenerator}'s dependsOn handling.
   */
  private void injectDep(NPMPackageGenerator gen, String id, String version) throws Exception {
    NpmPackage tgz;
    try (FileInputStream in = new FileInputStream(gen.filename())) {
      tgz = NpmPackage.fromPackage(in);
    }
    tgz.getNpm().forceObject("dependencies").add(id, version);
    try (FileOutputStream out = new FileOutputStream(gen.filename())) {
      tgz.save(out);
    }
    gen.getPackageJ().forceObject("dependencies").add(id, version);
  }

  private int countCoreDeps(JsonObject deps) {
    int n = 0;
    for (JsonProperty p : deps.getProperties()) {
      if (p.getName() != null && p.getName().matches("^hl7\\.fhir\\.r\\d+b?\\.core$")) {
        n++;
      }
    }
    return n;
  }
}
