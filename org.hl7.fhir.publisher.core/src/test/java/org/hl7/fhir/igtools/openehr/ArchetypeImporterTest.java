package org.hl7.fhir.igtools.openehr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.hl7.fhir.igtools.openehr.ArchetypeImporter.ProcessedArchetype;
import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.standalone.context.SimpleWorkerContext;
import org.hl7.fhir.standalone.context.SimpleWorkerContext.ILoaderFactory;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.validation.IgLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import com.nedap.archie.adlparser.ADLParseException;
import com.nedap.archie.aom.AuthoredArchetype;

class ArchetypeImporterTest {

  private org.hl7.fhir.services.context.IWorkerContext context;

  @BeforeEach
  void setUp() throws Exception {
    SimpleWorkerContext swc = new SimpleWorkerContext.SimpleWorkerContextBuilder(ModelContext.fullCoreContext())
        .fromNothing();
    ILoaderFactory loaderFactory = new IgLoader(new FilesystemPackageCacheManager.Builder().build(), swc, "5.0.0");
    swc.setLoaderFactory(loaderFactory);
    NpmPackage npmPackage = NpmPackage.fromUrl("https://build.fhir.org/ig/FHIR/openehr-base-ig/package.tgz");
//    NpmPackage npmPackage = NpmPackage.fromFolder("/home/roeland/.fhir/packages/openehr.base#current/");
    swc.loadPackage(npmPackage);
    context = swc;
  }

  @Test
  void testLoadADL14() throws Exception {
    ArchetypeImporter importer = new ArchetypeImporter(context, "Test");
    ProcessedArchetype archetype = importer.importArchetype(getClass().getResourceAsStream("/adl14/TEST_ADL_1.4.adl"),
        "TEST_ADL_1.4.adl", "adl");
    assertNotNull(archetype);
    assertNotNull(archetype.getArchetype());
    assertTrue(archetype.getArchetype() instanceof AuthoredArchetype);
    AuthoredArchetype inner = (AuthoredArchetype)archetype.getArchetype();
    assertEquals("1.4", inner.getAdlVersion());
    assertEquals("1", archetype.getArchetype().getArchetypeId().getVersionId());
    assertEquals("openEHR-EHR-EVALUATION.problem_diagnosis.v1", archetype.getArchetype().getArchetypeId().getFullId());
  }

  @Test
  void testLoadADL20() throws Exception {
    ArchetypeImporter importer = new ArchetypeImporter(context, "Test");
    ProcessedArchetype archetype = importer.importArchetype(getClass().getResourceAsStream("/adl20/TEST_ADL_2.0.adls"),
        "TEST_ADL_2.0.adls", "adls");
    assertNotNull(archetype);
    assertNotNull(archetype.getArchetype());
    assertTrue(archetype.getArchetype() instanceof AuthoredArchetype);
    AuthoredArchetype inner = (AuthoredArchetype)archetype.getArchetype();
    assertEquals("2.0.6", inner.getAdlVersion());
    assertEquals("1.0.0", archetype.getArchetype().getArchetypeId().getVersionId());
    assertEquals("openEHR-EHR-EVALUATION.problem_diagnosis.v1.0.0", archetype.getArchetype().getArchetypeId().getFullId());
  }

  @Test
  void testImportArchetypeInputStreamString14()
      throws ParserConfigurationException, SAXException, IOException, ADLParseException {
    ArchetypeImporter importer = new ArchetypeImporter(context, "Test");
    ProcessedArchetype archetype = importer.importArchetype(getClass().getResourceAsStream("/adl14/TEST_ADL_1.4.adl"),
        "TEST_ADL_1.4.adl", "adl");
    assertNotNull(archetype);
    assertNotNull(archetype.getArchetype());
    assertTrue(archetype.getArchetype() instanceof AuthoredArchetype);
    AuthoredArchetype inner = (AuthoredArchetype)archetype.getArchetype();
    assertEquals("1.4", inner.getAdlVersion());
    assertEquals("1", archetype.getArchetype().getArchetypeId().getVersionId());
    assertEquals("openEHR-EHR-EVALUATION.problem_diagnosis.v1", archetype.getArchetype().getArchetypeId().getFullId());
  }
  
  @Test
  void testImportArchetypeInputStreamString14_2()
      throws ParserConfigurationException, SAXException, IOException, ADLParseException {
    ArchetypeImporter importer = new ArchetypeImporter(context, "Test");
    ProcessedArchetype archetype = importer.importArchetype(getClass().getResourceAsStream("/adl14/openEHR-EHR-ACTION.medication.v1.adl"),
        "TEST_ADL_1.4.adl", "adl");
    assertNotNull(archetype);
    assertNotNull(archetype.getArchetype());
    assertTrue(archetype.getArchetype() instanceof AuthoredArchetype);
    AuthoredArchetype inner = (AuthoredArchetype)archetype.getArchetype();
    assertEquals("1.4", inner.getAdlVersion());
    assertEquals("1", archetype.getArchetype().getArchetypeId().getVersionId());
    assertEquals("openEHR-EHR-ACTION.medication.v1", archetype.getArchetype().getArchetypeId().getFullId());
  }
  
  @Test
  void testImportArchetypeInputStreamString20()
      throws ParserConfigurationException, SAXException, IOException, ADLParseException {
    ArchetypeImporter importer = new ArchetypeImporter(context, "Test");
    ProcessedArchetype archetype = importer.importArchetype(getClass().getResourceAsStream("/adl20/TEST_ADL_2.0.adls"),
        "TEST_ADL_2.0.adls", "adls");
    assertNotNull(archetype);
    assertNotNull(archetype.getArchetype());
    assertTrue(archetype.getArchetype() instanceof AuthoredArchetype);
    AuthoredArchetype inner = (AuthoredArchetype)archetype.getArchetype();
    assertEquals("2.0.6", inner.getAdlVersion());
    assertEquals("1.0.0", archetype.getArchetype().getArchetypeId().getVersionId());
    assertEquals("openEHR-EHR-EVALUATION.problem_diagnosis.v1.0.0", archetype.getArchetype().getArchetypeId().getFullId());
  }
  
  @Test
  void testImportArchetypeInputStreamString20_2()
      throws ParserConfigurationException, SAXException, IOException, ADLParseException {
    ArchetypeImporter importer = new ArchetypeImporter(context, "Test");
    ProcessedArchetype archetype = importer.importArchetype(getClass().getResourceAsStream("/adl20/openEHR-EHR-ACTION.medication.v1.0.0.adls"),
        "openEHR-EHR-ACTION.medication.v1.0.0.adls", "adls");
    assertNotNull(archetype);
    assertNotNull(archetype.getArchetype());
    assertTrue(archetype.getArchetype() instanceof AuthoredArchetype);
    AuthoredArchetype inner = (AuthoredArchetype)archetype.getArchetype();
    assertEquals("2.1.0", inner.getAdlVersion());
    assertEquals("1.0.0", archetype.getArchetype().getArchetypeId().getVersionId());
    assertEquals("openEHR-EHR-ACTION.medication.v1.0.0", archetype.getArchetype().getArchetypeId().getFullId());
  }

}