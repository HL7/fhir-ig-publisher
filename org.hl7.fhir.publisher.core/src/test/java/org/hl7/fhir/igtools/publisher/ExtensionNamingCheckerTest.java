package org.hl7.fhir.igtools.publisher;

import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.StructureDefinition.ExtensionContextType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * FHIR-43753 extension naming rules. These tests use no worker context, so they cover the single
 * resource/type rule and the general rule, but not the pattern rule (which needs the resource definitions)
 */
public class ExtensionNamingCheckerTest {

  private StructureDefinition ext(String id, String... contexts) {
    StructureDefinition sd = new StructureDefinition();
    sd.setId(id);
    sd.setType("Extension");
    for (String c : contexts) {
      sd.addContext().setType(ExtensionContextType.ELEMENT).setExpression(c);
    }
    return sd;
  }

  private String check(StructureDefinition sd) {
    return new ExtensionNamingChecker(null).check(sd);
  }

  @Test
  public void testExistingIsExempt() {
    Assertions.assertTrue(ExtensionNamingChecker.isExisting("patient-birthPlace"));
    Assertions.assertNull(check(ext("patient-birthPlace", "Observation")));
  }

  @Test
  public void testSingleResource() {
    Assertions.assertNull(check(ext("patient-favouriteColour", "Patient")));
    Assertions.assertNull(check(ext("questionnaire-itemThing", "Questionnaire.item", "Questionnaire")));
    Assertions.assertNotNull(check(ext("favouriteColour", "Patient")));
    Assertions.assertNotNull(check(ext("patient-favourite-colour", "Patient")));
    Assertions.assertNotNull(check(ext("Patient-favouriteColour", "Patient")));
    Assertions.assertNotNull(check(ext("cqf-favouriteColour", "Patient")));
  }

  @Test
  public void testSingleType() {
    Assertions.assertNull(check(ext("attachment-previewImage", "Attachment")));
    Assertions.assertNull(check(ext("elementdefinition-newThing", "ElementDefinition.type")));
    Assertions.assertNotNull(check(ext("previewImage", "Attachment")));
  }

  @Test
  public void testGeneral() {
    Assertions.assertNull(check(ext("newThing", "string", "uri")));
    Assertions.assertNull(check(ext("artifact-newThing", "string", "uri")));
    Assertions.assertNull(check(ext("openEHR-newThing", "string", "uri")));
    Assertions.assertNotNull(check(ext("new-thing", "string", "uri")));
    Assertions.assertNotNull(check(ext("other-newThing", "string", "uri")));
    Assertions.assertNotNull(check(ext("NewThing", "string", "uri")));
  }

  @Test
  public void testNonElementContext() {
    StructureDefinition sd = ext("newThing");
    sd.addContext().setType(ExtensionContextType.EXTENSION).setExpression("http://example.org/StructureDefinition/other");
    Assertions.assertNull(check(sd));
    sd.setId("other-newThing");
    Assertions.assertNotNull(check(sd));
  }
}
