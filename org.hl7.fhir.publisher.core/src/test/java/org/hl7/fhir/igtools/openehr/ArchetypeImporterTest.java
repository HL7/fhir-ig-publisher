package org.hl7.fhir.igtools.openehr;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.hl7.fhir.igtools.openehr.ArchetypeImporter.ProcessedArchetype;
import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.Property;
import org.hl7.fhir.model.core.DataType;
import org.hl7.fhir.model.core.ElementDefinition;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.StructureDefinition.StructureDefinitionDifferentialComponent;
import org.hl7.fhir.standalone.context.SimpleWorkerContext;
import org.hl7.fhir.standalone.context.SimpleWorkerContext.ILoaderFactory;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.validation.IgLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import com.nedap.archie.adlparser.ADLParseException;
import com.nedap.archie.aom.Archetype;
import com.nedap.archie.aom.AuthoredArchetype;

class ArchetypeImporterTest {

  private static org.hl7.fhir.services.context.IWorkerContext context;
  private String testIsu8601DateTime1;
  private String testIsu8601DateTime2;
  private String testIsu8601DateTime3;
  
  @BeforeAll
  static void beforeAll()  throws Exception {
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
    Archetype archetype = importer.load14(getClass().getResourceAsStream("/adl14/TEST_ADL_1.4.adl"));
    assertNotNull(archetype);
    assertTrue(archetype instanceof AuthoredArchetype);
    AuthoredArchetype inner = (AuthoredArchetype)archetype;
    assertEquals("1.4", inner.getAdlVersion());
    assertEquals("1", archetype.getArchetypeId().getVersionId());
    assertEquals("openEHR-EHR-EVALUATION.problem_diagnosis.v1", archetype.getArchetypeId().getFullId());    
  }

  @Test
  void testLoadADL20() throws Exception {
    ArchetypeImporter importer = new ArchetypeImporter(context, "Test");
    Archetype archetype = importer.load20(getClass().getResourceAsStream("/adl20/TEST_ADL_2.0.adls"));
    assertNotNull(archetype);
    assertTrue(archetype instanceof AuthoredArchetype);
    AuthoredArchetype inner = (AuthoredArchetype)archetype;
    assertEquals("2.0.6", inner.getAdlVersion());
    assertEquals("1.0.0", archetype.getArchetypeId().getVersionId());
    assertEquals("openEHR-EHR-EVALUATION.problem_diagnosis.v1.0.0", archetype.getArchetypeId().getFullId());
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
    
    StructureDefinition strdef = archetype.getSd();
    assertNotNull(strdef);
    assertEquals("openEHR-EHR-EVALUATION.problem-diagnosis.v1", strdef.getId());
    
    assertNotNull(strdef.getChildren());
    assertEquals(37, strdef.getChildren().size());
    Property firstChild = strdef.getChildren().get(0);
    assertNotNull(firstChild);
    assertEquals("id", firstChild.getName());
    assertEquals("id", firstChild.getTypeCode());
    assertEquals("openEHR-EHR-EVALUATION.problem-diagnosis.v1", firstChild.getValues().get(0).toString());
    
    assertNotNull(strdef.getDifferential());
    StructureDefinitionDifferentialComponent diff = strdef.getDifferential();
    assertNotNull(diff.getElementFirstRep());
    assertEquals("EVALUATION",  diff.getElementFirstRep().getId());
    
    ElementDefinition element4 = diff.getElementList().get(4);
    assertNotNull(element4);
    assertEquals("EVALUATION.data.items:at0002.value", element4.getId());
    
    ElementDefinition element5 = diff.getElementList().get(5);
    assertNotNull(element5);
    assertEquals("EVALUATION.data.items:at0079", element5.getId());
    assertEquals("*", element5.getMax());
    assertEquals(0, element5.getMin());
  }
  
  @Test
  void testImportArchetypeInputStreamString14_2()
      throws ParserConfigurationException, SAXException, IOException, ADLParseException {
    ArchetypeImporter importer = new ArchetypeImporter(context, "Test");
    ProcessedArchetype archetype = importer.importArchetype(getClass().getResourceAsStream("/adl14/openEHR-EHR-ACTION.medication.v1.adl"),
        "openEHR-EHR-ACTION.medication.v1.adl", "adl");
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
 
    StructureDefinition strdef = archetype.getSd();
    assertNotNull(strdef);
    assertEquals("openEHR-EHR-EVALUATION.problem-diagnosis.v1.0.0", strdef.getId());
    
    assertNotNull(strdef.getChildren());
    assertEquals(37, strdef.getChildren().size());
    Property firstChild = strdef.getChildren().get(0);
    assertNotNull(firstChild);
    assertEquals("id", firstChild.getName());
    assertEquals("id", firstChild.getTypeCode());
    assertEquals("openEHR-EHR-EVALUATION.problem-diagnosis.v1.0.0", firstChild.getValues().get(0).toString());
    
    assertNotNull(strdef.getDifferential());
    StructureDefinitionDifferentialComponent diff = strdef.getDifferential();
    assertNotNull(diff.getElementFirstRep());
    assertEquals("EVALUATION",  diff.getElementFirstRep().getId());
    
    ElementDefinition element4 = diff.getElementList().get(4);
    assertNotNull(element4);
    assertEquals("EVALUATION.data.items:id3.value", element4.getId());
    
    ElementDefinition element5 = diff.getElementList().get(5);
    assertNotNull(element5);
    assertEquals("EVALUATION.data.items:id10", element5.getId());
    assertEquals("1", element5.getMax());
    assertEquals(0, element5.getMin());
    
    ElementDefinition element10 = diff.getElementList().get(10);
    assertNotNull(element10);
    assertEquals("EVALUATION.data.items:id4.value", element10.getId());
    assertEquals("id51", element10.getLabel());
    assertEquals("yyyy-mm-ddTHH:MM:SS", element5.getPatternDateTimeType().getValueAsString());
    
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