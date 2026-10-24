package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Set;

import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.formats.JsonParser;
import org.hl7.fhir.standalone.context.SimpleWorkerContext;
import org.junit.jupiter.api.Test;

class PublisherIGLoaderAdditionalResourcesTest {

  @Test
  void recognizesAdditionalResourceFromDependencyInCoreNamespace() throws Exception {
    StructureDefinition additional = resource("AdditionalRecord");

    assertEquals(Set.of("AdditionalRecord"), registeredNames(additional));
  }

  @Test
  void recognizesExplicitAdditionalResourceInVersionedNamespace() throws Exception {
    StructureDefinition additional = resource("VersionedAdditionalRecord");

    assertEquals(Set.of("AdditionalRecord"), registeredNames(additional));
  }

  @Test
  void doesNotRegisterOrdinaryCoreOrCrossVersionResources() throws Exception {
    assertEquals(Set.of(), registeredNames(
        resource("Patient"),
        resource("VersionedObservation")));
  }

  @Test
  void falseAdditionalResourceFlagDoesNotRegisterCoreResource() throws Exception {
    StructureDefinition ordinary = resource("PatientFalseFlag");

    assertEquals(Set.of(), registeredNames(ordinary));
  }

  @Test
  void stillRecognizesCustomResourceWithoutAdditionalResourceFlag() throws Exception {
    assertEquals(Set.of("CustomRecord"), registeredNames(
        resource("CustomRecord")));
  }

  @Test
  void additionalFlagDoesNotTurnProfilesOrLogicalModelsIntoResourceTypes() throws Exception {
    StructureDefinition profile = resource("PatientProfile");
    StructureDefinition logical = resource("LogicalRecord");

    assertEquals(Set.of(), registeredNames(profile, logical));
  }

  private StructureDefinition resource(String name) throws Exception {
    try (InputStream input = getClass().getResourceAsStream("/additional-resource-dependencies/" + name + ".json")) {
      assertNotNull(input, name);
      return (StructureDefinition) new JsonParser(ModelContext.fullCoreContext()).parse(input);
    }
  }

  private Set<String> registeredNames(StructureDefinition... dependencies) throws Exception {
    PublisherFields fields = new PublisherFields();
    fields.context = new SimpleWorkerContext.SimpleWorkerContextBuilder(ModelContext.fullCoreContext()).fromNothing();
    for (StructureDefinition sd : dependencies) {
      fields.context.cacheResource(sd);
    }
    // The definitions are already loaded from dependencies; this IG declares no resource files.
    PublisherIGLoader loader = new PublisherIGLoader(new PublisherSettings());
    loader.setPf(fields);
    Method load = PublisherIGLoader.class.getDeclaredMethod("loadCustomResources");
    load.setAccessible(true);
    load.invoke(loader);
    return fields.customResourceNames;
  }
}
