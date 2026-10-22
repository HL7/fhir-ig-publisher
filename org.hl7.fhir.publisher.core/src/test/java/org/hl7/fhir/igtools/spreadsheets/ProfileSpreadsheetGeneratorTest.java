package org.hl7.fhir.igtools.spreadsheets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hl7.fhir.model.Base;
import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.CodeableConcept;
import org.hl7.fhir.model.core.Coding;
import org.hl7.fhir.model.core.ContactDetail;
import org.hl7.fhir.model.core.ContactPoint;
import org.hl7.fhir.model.core.ElementDefinition;
import org.hl7.fhir.model.core.ElementDefinition.ConstraintSeverity;
import org.hl7.fhir.model.core.ElementDefinition.DiscriminatorType;
import org.hl7.fhir.model.core.ElementDefinition.SlicingRules;
import org.hl7.fhir.model.core.Enumerations.BindingStrength;
import org.hl7.fhir.model.core.Enumerations.PublicationStatus;
import org.hl7.fhir.model.core.IntegerType;
import org.hl7.fhir.model.core.Quantity;
import org.hl7.fhir.model.core.StringType;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.model.core.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.services.renderers.RendererFactory;
import org.hl7.fhir.services.renderers.spreadsheets.StructureDefinitionSpreadsheetGenerator;
import org.hl7.fhir.services.terminology.ValidationResult;
import org.hl7.fhir.utilities.validation.ValidationOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * ProfileSpreadsheetGenerator must produce the same workbook as core's StructureDefinitionSpreadsheetGenerator:
 * every zip entry byte-identical, except the creation timestamp in docProps/core.xml.
 */
class ProfileSpreadsheetGeneratorTest {

  private IWorkerContext worker;
  private RendererFactory rf;

  @BeforeEach
  void setUp() {
    worker = mock(IWorkerContext.class);
    when(worker.getModelContext()).thenReturn(ModelContext.fullCoreContext());
    when(worker.getLocale()).thenReturn(Locale.US);
    when(worker.formatMessage(anyString(), any(Object[].class))).thenAnswer(i -> i.getArgument(0));
    when(worker.validateCode(any(ValidationOptions.class), anyString(), isNull(), anyString(), isNull()))
        .thenAnswer(i -> new ValidationResult(i.getArgument(1), null, null, "Display of " + i.getArgument(3)));
    rf = new RendererFactory();
  }

  private static final String[] TEXTS = {
      "", "Y", "0", "1", "*", "A short text", "Unique id for inter-element referencing",
      "May be used to represent additional information that is not part of the basic definition of the element.\nTo make the use of extensions safe and managable, there is a strict set of governance applied.",
      "Identifies the patient", "Bold & <xml> \"quotes\" 'apos'", "Ünïcödé ťëxť ✓", "line one\nline two\nline three",
      "a much longer text that goes on and on so that the column width grows beyond the default and needs measuring, with some more words",
      "    leading spaces", "trailing spaces    ", "tab\tseparated"
  };

  /** a profile that touches every column and the metadata rows; same seed, same profile */
  static StructureDefinition profile(String id, long seed, int elements, int mappings) {
    Random rnd = new Random(seed);
    StructureDefinition sd = new StructureDefinition(ModelContext.fullCoreContext());
    sd.setId(id);
    sd.setUrl("http://example.org/StructureDefinition/" + id);
    sd.addIdentifier().setSystem("urn:ietf:rfc:3986").setValue("urn:oid:1.2.3." + seed);
    sd.setVersion("1.0." + seed);
    sd.setName("P" + id);
    sd.setTitle("Profile " + id);
    sd.setStatus(PublicationStatus.ACTIVE);
    sd.setExperimental(rnd.nextBoolean());
    sd.setPublisher("Example Publisher");
    ContactDetail cd = sd.addContact();
    cd.setName("Someone");
    cd.addTelecom().setSystem(ContactPoint.ContactPointSystem.EMAIL).setValue("someone@example.org");
    sd.addJurisdiction().addCoding().setSystem("urn:iso:std:iso:3166").setCode("EE");
    sd.addJurisdiction().addCoding().setSystem("http://example.org/cs").setCode("region-" + seed);
    sd.addKeyword(new Coding().setSystem("http://example.org/kw").setCode("kw" + seed).setDisplay("Keyword " + seed));
    sd.addKeyword(new Coding().setSystem("http://example.org/kw").setCode("nodisplay" + seed));
    sd.setDescription(TEXTS[rnd.nextInt(TEXTS.length)]);
    sd.setPurpose(TEXTS[rnd.nextInt(TEXTS.length)]);
    sd.setCopyright("CC0");
    sd.setFhirVersion(org.hl7.fhir.model.core.Enumerations.FHIRVersion._5_0_0);
    sd.setKind(StructureDefinitionKind.RESOURCE);
    sd.setAbstract(false);
    sd.setType("Patient");
    sd.setBaseDefinition("http://hl7.org/fhir/StructureDefinition/Patient");
    sd.setDerivation(TypeDerivationRule.CONSTRAINT);
    sd.addContext().setType(StructureDefinition.ExtensionContextType.ELEMENT).setExpression("Patient");
    sd.addContextInvariant("true");
    for (int m = 0; m < mappings; m++) {
      sd.addMapping().setIdentity("m" + m).setName("Mapping " + m).setUri("http://example.org/map/" + m);
    }
    for (int i = 0; i < elements; i++) {
      ElementDefinition ed = sd.getSnapshot().addElement();
      String path = i == 0 ? "Patient" : "Patient.e" + (i % 7) + (i % 3 == 0 ? "" : ".c" + i);
      ed.setPath(path);
      ed.setId(path + (i % 5 == 0 ? ":slice" + i : ""));
      if (i % 5 == 0) {
        ed.setSliceName("slice" + i);
      }
      if (rnd.nextInt(4) == 0) {
        ed.addAlias(TEXTS[rnd.nextInt(TEXTS.length)]);
        ed.addAlias("alias" + i);
      }
      if (rnd.nextInt(4) == 0) {
        ed.setLabel(TEXTS[rnd.nextInt(TEXTS.length)]);
      }
      ed.setMin(rnd.nextInt(2));
      ed.setMax(rnd.nextBoolean() ? "1" : "*");
      if (rnd.nextInt(3) > 0) {
        ed.setMustSupport(rnd.nextBoolean());
      }
      ed.setIsModifier(rnd.nextInt(5) == 0);
      ed.setIsSummary(rnd.nextBoolean());
      if (i > 0) {
        ElementDefinition.TypeRefComponent t = ed.addType().setCode(rnd.nextBoolean() ? "string" : "Reference");
        if ("Reference".equals(t.getCode())) {
          t.addTargetProfile("http://hl7.org/fhir/StructureDefinition/Patient");
          t.addTargetProfile("http://example.org/StructureDefinition/other");
          t.addAggregation(ElementDefinition.AggregationMode.REFERENCED);
        }
        if (rnd.nextInt(4) == 0) {
          t.addProfile("http://example.org/StructureDefinition/p" + i);
        }
      }
      ed.setShort(TEXTS[rnd.nextInt(TEXTS.length)]);
      ed.setDefinition(TEXTS[rnd.nextInt(TEXTS.length)]);
      if (rnd.nextBoolean()) {
        ed.setComment(TEXTS[rnd.nextInt(TEXTS.length)]);
      }
      if (rnd.nextBoolean()) {
        ed.setRequirements(TEXTS[rnd.nextInt(TEXTS.length)]);
      }
      switch (rnd.nextInt(6)) {
        case 0: ed.setDefaultValue(new StringType("default " + i)); break;
        case 1: ed.setFixed(new CodeableConcept().addCoding(new Coding("http://loinc.org", "1234-" + i, "Fixed " + i))); break;
        case 2: ed.setPattern(new Coding("http://snomed.info/sct", "" + (100000 + i), null)); break;
        case 3: ed.addExample().setLabel("ex").setValue(new Quantity().setValue(i).setUnit("mg")); break;
        case 4: ed.setMinValue(new IntegerType(i)); ed.setMaxValue(new IntegerType(i * 10)); ed.setMaxLength(i + 5); break;
        default: ed.setMeaningWhenMissing(TEXTS[rnd.nextInt(TEXTS.length)]);
      }
      if (rnd.nextInt(3) == 0) {
        ed.getBinding().setStrength(BindingStrength.EXTENSIBLE).setDescription(TEXTS[rnd.nextInt(TEXTS.length)])
            .setValueSet(rnd.nextBoolean() ? "http://example.org/ValueSet/vs" + i : null);
      }
      if (rnd.nextInt(4) == 0) {
        ed.addCode(new Coding("http://loinc.org", "c" + i, rnd.nextBoolean() ? "Code " + i : null));
      }
      if (i % 5 == 0) {
        ed.getSlicing().addDiscriminator().setType(DiscriminatorType.VALUE).setPath("system");
        ed.getSlicing().setDescription(TEXTS[rnd.nextInt(TEXTS.length)]).setOrdered(rnd.nextBoolean()).setRules(SlicingRules.OPEN);
      }
      if (rnd.nextBoolean()) {
        ed.getBase().setPath(path).setMin(0).setMax("*");
      }
      if (rnd.nextInt(4) == 0) {
        ed.addCondition("ele-" + i);
      }
      if (rnd.nextInt(3) == 0) {
        ed.addConstraint().setKey("inv-" + i).setSeverity(ConstraintSeverity.ERROR).setHuman(TEXTS[rnd.nextInt(TEXTS.length)]).setExpression("hasValue()");
      }
      for (int m = 0; m < mappings; m++) {
        if (rnd.nextBoolean()) {
          ed.addMapping().setIdentity("m" + m).setMap("MAP." + i + "." + m);
        }
      }
    }
    return sd;
  }

  /** zip entries in order; docProps/core.xml with its created/modified values masked */
  static Map<String, byte[]> entries(byte[] xlsx) throws Exception {
    Map<String, byte[]> res = new LinkedHashMap<>();
    try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
      ZipEntry e;
      while ((e = z.getNextEntry()) != null) {
        byte[] b = z.readAllBytes();
        if ("docProps/core.xml".equals(e.getName())) {
          b = new String(b, java.nio.charset.StandardCharsets.UTF_8)
              .replaceAll("(<dcterms:(?:created|modified)[^>]*>)[^<]*(</dcterms)", "$1#$2").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        res.put(e.getName(), b);
      }
    }
    return res;
  }

  static void assertSameWorkbook(byte[] expected, byte[] actual) throws Exception {
    Map<String, byte[]> a = entries(expected);
    Map<String, byte[]> b = entries(actual);
    assertEquals(new ArrayList<>(a.keySet()), new ArrayList<>(b.keySet()), "zip entries");
    for (String n : a.keySet()) {
      assertArrayEquals(a.get(n), b.get(n), "entry " + n);
    }
  }

  private byte[] core(StructureDefinition sd, boolean hide) throws Exception {
    StructureDefinitionSpreadsheetGenerator g = new StructureDefinitionSpreadsheetGenerator(worker, true, hide, rf);
    g.renderStructureDefinition(sd, false);
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    g.finish(b);
    return b.toByteArray();
  }

  private byte[] ours(StructureDefinition sd, boolean hide, ColumnWidthCache widths) throws Exception {
    ProfileSpreadsheetGenerator g = new ProfileSpreadsheetGenerator(worker, true, hide, rf, widths);
    g.renderStructureDefinition(sd, false);
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    g.finish(b);
    return b.toByteArray();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void sameProfileWorkbookAsCore(boolean hideMustSupportFalse) throws Exception {
    ColumnWidthCache widths = new ColumnWidthCache();
    for (int p = 0; p < 4; p++) {
      StructureDefinition sd = profile("p" + p, p, 30 + 20 * p, p % 3);
      assertSameWorkbook(core(sd, hideMustSupportFalse), ours(sd, hideMustSupportFalse, widths));
    }
    assertTrue(widths.size() > 50, "the cache was used");
  }

  @Test
  void sameAllProfilesWorkbookAsCore() throws Exception {
    StructureDefinitionSpreadsheetGenerator c = new StructureDefinitionSpreadsheetGenerator(worker, true, false, rf);
    ColumnWidthCache widths = new ColumnWidthCache();
    // warm the cache with other profiles first, as the per-profile workbooks do in a build
    ours(profile("w", 99, 40, 1), true, widths);
    ProfileSpreadsheetGenerator o = new ProfileSpreadsheetGenerator(worker, true, false, rf, widths);
    for (int p = 0; p < 5; p++) {
      StructureDefinition sd = profile("p" + p, 10 + p, 25 + 10 * p, p % 2);
      c.renderStructureDefinition(sd, true);
      o.renderStructureDefinition(sd, true);
    }
    c.configure();
    o.configure();
    ByteArrayOutputStream bc = new ByteArrayOutputStream();
    c.finish(bc);
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    o.finish(bo);
    assertSameWorkbook(bc.toByteArray(), bo.toByteArray());
  }

  @Test
  void titleCountMatchesCore() throws Exception {
    // the header row of a profile without mappings has exactly one cell per title
    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(core(profile("t", 1, 3, 0), false)))) {
      Row header = wb.getSheet("Elements").getRow(0);
      assertEquals(ProfileSpreadsheetGenerator.TITLE_COUNT, header.getLastCellNum());
    }
  }

  @Test
  void precomputedDisplaysNeedNoContext() throws Exception {
    StructureDefinition sd = profile("d", 5, 40, 2);
    byte[] expected = core(sd, true);

    ProfileSpreadsheetGenerator g = new ProfileSpreadsheetGenerator(worker, true, true, rf, new ColumnWidthCache());
    StructureDefinition copy = sd.copy(Base.COPY_ALL);
    g.precomputeDisplays(copy);
    clearInvocations(worker);
    byte[] actual = ProfileSpreadsheetPrefetcher.job(g, copy).render();
    verifyNoInteractions(worker);
    assertSameWorkbook(expected, actual);
    // the metadata really went through the (mocked) terminology lookup, so the test means something
    assertTrue(new String(entries(actual).get("xl/sharedStrings.xml"), java.nio.charset.StandardCharsets.UTF_8).contains("Display of nodisplay5"));
  }

  @Test
  void precomputedDisplaysRefuseOtherObjects() throws Exception {
    StructureDefinition sd = profile("d", 6, 5, 0);
    ProfileSpreadsheetGenerator g = new ProfileSpreadsheetGenerator(worker, true, true, rf, new ColumnWidthCache());
    g.precomputeDisplays(sd.copy(Base.COPY_ALL));
    // the original's identifiers are other objects than the copy's, so rendering the original must not silently work
    assertThrows(IllegalStateException.class, () -> g.renderStructureDefinition(sd, false));
  }

  @Test
  void differentProfilesGiveDifferentWorkbooks() throws Exception {
    // guards the comparison itself
    assertNotEquals(new String(entries(core(profile("a", 1, 10, 0), false)).get("xl/worksheets/sheet2.xml")),
        new String(entries(core(profile("a", 2, 10, 0), false)).get("xl/worksheets/sheet2.xml")));
  }
}
