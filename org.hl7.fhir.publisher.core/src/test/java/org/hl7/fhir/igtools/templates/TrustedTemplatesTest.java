package org.hl7.fhir.igtools.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TrustedTemplatesTest {

  @ParameterizedTest
  @CsvSource({
    "https://github.com/HL7/ig-template-fhir, HL7/ig-template-fhir",
    "https://github.com/HL7/ig-template-fhir.git, HL7/ig-template-fhir",
    "http://github.com/HL7/ig-template-fhir/, HL7/ig-template-fhir",
    "https://github.com/HL7/ig-template-fhir/tree/master, HL7/ig-template-fhir",
    "https://www.github.com/HL7/ig-template-fhir, HL7/ig-template-fhir",
    "git@github.com:HL7/ig-template-fhir.git, HL7/ig-template-fhir",
    "ssh://git@github.com/HL7/ig-template-fhir.git, HL7/ig-template-fhir",
    "https://build.fhir.org/ig/HL7/ig-template-fhir, HL7/ig-template-fhir",
    "https://build.fhir.org/ig/HL7/ig-template-fhir/branches/my-branch, HL7/ig-template-fhir",
  })
  void findsTheRepositoryAUrlNames(String url, String repo) {
    assertEquals(repo, TrustedTemplates.githubRepository(url));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "https://gitlab.com/HL7/ig-template-fhir", "https://github.com/HL7", "https://github.com/",
      "https://example.org/github.com/HL7/ig-template-fhir", "https://github.com.evil.org/HL7/ig-template-fhir"})
  void findsNoRepositoryWhereTheUrlNamesNone(String url) {
    assertNull(TrustedTemplates.githubRepository(url));
  }

  @Test
  void aTrustedTemplateIsBuiltOnlyFromItsOwnRepository() {
    assertTrue(TrustedTemplates.isBuiltFrom("hl7.fhir.template", "https://github.com/HL7/ig-template-fhir"));
    assertTrue(TrustedTemplates.isBuiltFrom("hl7.fhir.template", "https://github.com/hl7/IG-Template-FHIR")); // GitHub names aren't case sensitive
    assertFalse(TrustedTemplates.isBuiltFrom("hl7.fhir.template", "https://github.com/someone/ig-template-fhir"));
    assertFalse(TrustedTemplates.isBuiltFrom("hl7.fhir.template", "https://github.com/HL7/ig-template-fhir-fork"));
    assertFalse(TrustedTemplates.isBuiltFrom("hl7.fhir.template", null));
  }

  @Test
  void aTemplateWithNoRecordedRepositoryIsBuiltFromNowhere() {
    assertTrue(TrustedTemplates.isTrusted("ch.fhir.ig.template"));
    assertFalse(TrustedTemplates.isBuiltFrom("ch.fhir.ig.template", "https://github.com/hl7ch/ig-template"));
  }

  @Test
  void anUntrustedTemplateIsBuiltFromNowhere() {
    assertFalse(TrustedTemplates.isTrusted("my.template"));
    assertFalse(TrustedTemplates.isBuiltFrom("my.template", "https://github.com/HL7/ig-template-fhir"));
  }
}
