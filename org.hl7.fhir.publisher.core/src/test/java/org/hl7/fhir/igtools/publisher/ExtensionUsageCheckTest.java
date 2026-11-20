package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.stream.Collectors;

import org.hl7.fhir.model.core.ElementDefinition;
import org.hl7.fhir.model.core.Enumerations;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.UriType;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.services.elementmodel.Property;
import org.hl7.fhir.utilities.NamedItemList;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.junit.jupiter.api.Test;

/** validateSDs warns about extensions the IG never uses */
class ExtensionUsageCheckTest {

  private static final String NO_EXAMPLES = "The Implementation Guide contains no examples for this extension";

  private final Property property = mock(Property.class);

  @Test
  void onlyUnusedExtensionsGetTheWarning() {
    PublisherFields pf = new PublisherFields();
    // used deep inside a resource, by its fixed url (not the SD's url)
    FetchedResource used = extension(pf, "used", "http://example.org/fixed/used");
    FetchedResource unused = extension(pf, "unused", null);
    FetchedResource onTheRoot = extension(pf, "root", null);
    FetchedResource modifier = extension(pf, "modifier", null);
    FetchedResource inner = extension(pf, "inner", null);
    // a use by the SD's url doesn't count when Extension.url is fixed to another one
    FetchedResource bySdUrl = extension(pf, "by-sd-url", "http://example.org/fixed/by-sd-url");
    Element name = element("name", "HumanName", null, uses("http://example.org/fixed/used"));
    Element outer = uses(url("root"));
    outer.getChildList().add(uses(url("inner")));
    addResource(pf, "Patient-p1.json", element("Patient", "Patient", null, name, outer,
        element("modifierExtension", "Extension", null, element("url", "uri", url("modifier"))), uses(url("by-sd-url"))));

    processor(pf).validateSDs();

    assertEquals(List.of(), messages(used));
    assertEquals(List.of(NO_EXAMPLES), messages(unused));
    assertEquals(List.of(), messages(onTheRoot));
    assertEquals(List.of(), messages(modifier));
    assertEquals(List.of(), messages(inner));
    assertEquals(List.of(NO_EXAMPLES), messages(bySdUrl));
  }

  @Test
  void nothingIsWalkedWhenNothingNeedsCounting() {
    PublisherFields pf = new PublisherFields();
    pf.noUsageCheck = true;
    FetchedResource sd = extension(pf, "a", null);
    int[] walks = new int[1];
    addResource(pf, "Patient-p1.json", walkCounted(walks));

    processor(pf).validateSDs();

    assertEquals(0, walks[0]);
    assertEquals(List.of(), messages(sd));
  }

  @Test
  void theResourcesAreWalkedOnceForAllExtensions() {
    PublisherFields pf = new PublisherFields();
    for (String id : new String[] {"a", "b", "c", "d"}) {
      extension(pf, id, null);
    }
    int[] walks = new int[1];
    Element patient = walkCounted(walks);
    patient.getChildList().add(uses(url("a")));
    walks[0] = 0;
    addResource(pf, "Patient-p1.json", patient);

    processor(pf).validateSDs();

    assertEquals(1, walks[0]);
  }

  @Test
  void eachRunCountsTheUsesAgain() {
    PublisherFields pf = new PublisherFields();
    FetchedResource sd = extension(pf, "later", null);
    PublisherProcessor processor = processor(pf);
    processor.validateSDs();
    assertEquals(List.of(NO_EXAMPLES), messages(sd));

    sd.getErrors().clear();
    addResource(pf, "Patient-p1.json", element("Patient", "Patient", null, uses(url("later"))));
    processor.validateSDs();

    assertTrue(messages(sd).isEmpty(), messages(sd).toString());
  }

  private PublisherProcessor processor(PublisherFields pf) {
    PublisherProcessor processor = new PublisherProcessor(new PublisherSettings());
    processor.setPf(pf);
    return processor;
  }

  private static String url(String id) {
    return "http://example.org/StructureDefinition/" + id;
  }

  /** an extension definition, with Extension.url fixed to fixedUrl when it isn't null */
  private FetchedResource extension(PublisherFields pf, String id, String fixedUrl) {
    StructureDefinition sd = new StructureDefinition();
    sd.setId(id);
    sd.setUrl(url(id));
    sd.setKind(StructureDefinition.StructureDefinitionKind.COMPLEXTYPE);
    sd.setType("Extension");
    sd.setAbstract(false);
    sd.setStatus(Enumerations.PublicationStatus.ACTIVE);
    sd.getSnapshot().addElement().setPath("Extension");
    ElementDefinition urlElement = sd.getSnapshot().addElement().setPath("Extension.url");
    if (fixedUrl != null) {
      urlElement.setFixed(new UriType(fixedUrl));
    }
    FetchedResource r = addResource(pf, "StructureDefinition-" + id + ".json", element("StructureDefinition", "StructureDefinition", null));
    r.setResource(sd);
    return r;
  }

  private FetchedResource addResource(PublisherFields pf, String name, Element element) {
    FetchedFile f = new FetchedFile(name);
    FetchedResource r = new FetchedResource(name);
    r.setElement(element);
    f.getResources().add(r);
    pf.fileList.add(f);
    return r;
  }

  /** a Patient that counts the calls to its getChildList(), one per walk over it */
  private Element walkCounted(int[] walks) {
    return new Element("Patient", property, "Patient", null) {
      @Override
      public NamedItemList<Element> getChildList() {
        walks[0]++;
        return super.getChildList();
      }
    };
  }

  private Element uses(String url) {
    return element("extension", "Extension", null, element("url", "uri", url));
  }

  private Element element(String name, String type, String value, Element... children) {
    Element e = new Element(name, property, type, value);
    for (Element child : children) {
      e.getChildList().add(child);
    }
    return e;
  }

  private static List<String> messages(FetchedResource r) {
    return r.getErrors().stream().map(ValidationMessage::getMessage).collect(Collectors.toList());
  }
}
