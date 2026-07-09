package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.Enumerations;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.junit.jupiter.api.Test;

/**
 * Pins the per-version serialization dispatch extracted into
 * {@link PublisherBase#serializeForVersion(org.hl7.fhir.r5.model.Resource, String, String, String)}.
 * <p>
 * In particular it guards the R4 ({@code VersionConvertorFactory_40_50}) vs R4B
 * ({@code VersionConvertorFactory_43_50}) split: before this fix, an R4B target was
 * silently serialized as R4 bytes mislabeled 4.3.0. The CodeSystem case additionally
 * exercises the conformance/core-dependency path used by
 * {@code PublisherProcessor.checkForCoreDependenciesCS/VS}, which serializes via the
 * same {@code convVersion} dispatch.
 */
class ConvVersionTest {

  private static final String SOURCE_VERSION = "5.0.0";
  private static final String BASE_PACKAGE_ID = "example.test";

  private StructureDefinition sampleProfile() {
    StructureDefinition sd = new StructureDefinition("http://example.org/fhir/StructureDefinition/test-profile",
        "TestProfile", Enumerations.PublicationStatus.ACTIVE, StructureDefinition.StructureDefinitionKind.RESOURCE,
        false, "Patient");
    sd.setBaseDefinition("http://hl7.org/fhir/StructureDefinition/Patient");
    sd.setDerivation(StructureDefinition.TypeDerivationRule.CONSTRAINT);
    return sd;
  }

  private CodeSystem sampleCodeSystem() {
    CodeSystem cs = new CodeSystem();
    cs.setUrl("http://example.org/fhir/CodeSystem/test-cs");
    cs.setVersion("1.0.0");
    cs.setName("TestCodeSystem");
    cs.setStatus(Enumerations.PublicationStatus.ACTIVE);
    cs.setContent(Enumerations.CodeSystemContentMode.COMPLETE);
    cs.setCaseSensitive(true);
    cs.addConcept().setCode("a").setDisplay("A");
    return cs;
  }

  @Test
  void structureDefinition_serializesAsGenuineR4AndR4B() throws Exception {
    byte[] r4Bytes = PublisherBase.serializeForVersion(sampleProfile(), "4.0.1", SOURCE_VERSION, BASE_PACKAGE_ID);
    byte[] r4bBytes = PublisherBase.serializeForVersion(sampleProfile(), "4.3.0", SOURCE_VERSION, BASE_PACKAGE_ID);

    org.hl7.fhir.r4.model.Resource r4res = new org.hl7.fhir.r4.formats.JsonParser().parse(new ByteArrayInputStream(r4Bytes));
    org.hl7.fhir.r4b.model.Resource r4bres = new org.hl7.fhir.r4b.formats.JsonParser().parse(new ByteArrayInputStream(r4bBytes));

    assertEquals("StructureDefinition", r4res.fhirType());
    assertEquals("StructureDefinition", r4bres.fhirType());

    // fhirVersion is stamped per target (SD carries it and serializeForVersion sets it).
    org.hl7.fhir.r4.model.StructureDefinition r4sd = (org.hl7.fhir.r4.model.StructureDefinition) r4res;
    org.hl7.fhir.r4b.model.StructureDefinition r4bsd = (org.hl7.fhir.r4b.model.StructureDefinition) r4bres;
    assertEquals("4.0.1", r4sd.getFhirVersion().toCode());
    assertEquals("4.3.0", r4bsd.getFhirVersion().toCode());

    // The R4B bytes must not be the R4 bytes relabeled: they are produced by different
    // convertor factories/parsers and at minimum the fhirVersion element differs.
    assertFalse(Arrays.equals(r4Bytes, r4bBytes), "R4B output must differ from R4 output");
  }

  @Test
  void structureDefinition_numericShortTokens_stampCanonicalFhirVersion() throws Exception {
    // Short numeric tokens (4.0/4.3) must stamp the canonical full version (4.0.1/4.3.0) on the
    // serialized StructureDefinition, matching the symbolic/full spellings - not the raw short
    // form (formerly stamped as non-canonical 4.0/4.3) (M2).
    byte[] r4Bytes = PublisherBase.serializeForVersion(sampleProfile(), "4.0", SOURCE_VERSION, BASE_PACKAGE_ID);
    byte[] r4bBytes = PublisherBase.serializeForVersion(sampleProfile(), "4.3", SOURCE_VERSION, BASE_PACKAGE_ID);

    org.hl7.fhir.r4.model.StructureDefinition r4sd = (org.hl7.fhir.r4.model.StructureDefinition)
        new org.hl7.fhir.r4.formats.JsonParser().parse(new ByteArrayInputStream(r4Bytes));
    org.hl7.fhir.r4b.model.StructureDefinition r4bsd = (org.hl7.fhir.r4b.model.StructureDefinition)
        new org.hl7.fhir.r4b.formats.JsonParser().parse(new ByteArrayInputStream(r4bBytes));

    assertEquals("4.0.1", r4sd.getFhirVersion().toCode());
    assertEquals("4.3.0", r4bsd.getFhirVersion().toCode());
  }

  @Test
  void codeSystem_serializesParseablyForR4AndR4B() throws Exception {
    byte[] r4Bytes = PublisherBase.serializeForVersion(sampleCodeSystem(), "4.0.1", SOURCE_VERSION, BASE_PACKAGE_ID);
    byte[] r4bBytes = PublisherBase.serializeForVersion(sampleCodeSystem(), "4.3.0", SOURCE_VERSION, BASE_PACKAGE_ID);

    org.hl7.fhir.r4.model.Resource r4res = new org.hl7.fhir.r4.formats.JsonParser().parse(new ByteArrayInputStream(r4Bytes));
    org.hl7.fhir.r4b.model.Resource r4bres = new org.hl7.fhir.r4b.formats.JsonParser().parse(new ByteArrayInputStream(r4bBytes));

    assertEquals("CodeSystem", r4res.fhirType());
    assertEquals("CodeSystem", r4bres.fhirType());
    assertEquals("http://example.org/fhir/CodeSystem/test-cs", ((org.hl7.fhir.r4.model.CodeSystem) r4res).getUrl());
    assertEquals("http://example.org/fhir/CodeSystem/test-cs", ((org.hl7.fhir.r4b.model.CodeSystem) r4bres).getUrl());
  }
}
