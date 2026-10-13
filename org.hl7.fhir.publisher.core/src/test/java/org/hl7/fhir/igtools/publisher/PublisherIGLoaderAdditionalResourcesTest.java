package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Set;

import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.BooleanType;
import org.hl7.fhir.model.core.Enumerations.PublicationStatus;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.model.core.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.model.extensions.ExtensionDefinitions;
import org.hl7.fhir.standalone.context.SimpleWorkerContext;
import org.junit.jupiter.api.Test;

class PublisherIGLoaderAdditionalResourcesTest {

  @Test
  void recognizesAdditionalResourceFromDependencyInCoreNamespace() throws Exception {
    StructureDefinition additional = resource("http://hl7.org/fhir/StructureDefinition/AdditionalRecord");
    additional.addExtension(ExtensionDefinitions.EXT_ADDITIONAL_RESOURCE, new BooleanType(true));

    assertEquals(Set.of("AdditionalRecord"), registeredNames(additional));
  }

  @Test
  void recognizesExplicitAdditionalResourceInVersionedNamespace() throws Exception {
    StructureDefinition additional = resource("http://hl7.org/fhir/5.0/StructureDefinition/AdditionalRecord");
    additional.addExtension(ExtensionDefinitions.EXT_ADDITIONAL_RESOURCE, new BooleanType(true));

    assertEquals(Set.of("AdditionalRecord"), registeredNames(additional));
  }

  @Test
  void doesNotRegisterOrdinaryCoreOrCrossVersionResources() throws Exception {
    assertEquals(Set.of(), registeredNames(
        resource("http://hl7.org/fhir/StructureDefinition/Patient"),
        resource("http://hl7.org/fhir/5.0/StructureDefinition/Observation")));
  }

  @Test
  void falseAdditionalResourceFlagDoesNotRegisterCoreResource() throws Exception {
    StructureDefinition ordinary = resource("http://hl7.org/fhir/StructureDefinition/Patient");
    ordinary.addExtension(ExtensionDefinitions.EXT_ADDITIONAL_RESOURCE, new BooleanType(false));

    assertEquals(Set.of(), registeredNames(ordinary));
  }

  @Test
  void stillRecognizesCustomResourceWithoutAdditionalResourceFlag() throws Exception {
    assertEquals(Set.of("CustomRecord"), registeredNames(
        resource("http://example.org/fhir/StructureDefinition/CustomRecord")));
  }

  @Test
  void additionalFlagDoesNotTurnProfilesOrLogicalModelsIntoResourceTypes() throws Exception {
    StructureDefinition profile = resource("http://example.org/fhir/StructureDefinition/PatientProfile");
    profile.setDerivation(TypeDerivationRule.CONSTRAINT);
    profile.addExtension(ExtensionDefinitions.EXT_ADDITIONAL_RESOURCE, new BooleanType(true));
    StructureDefinition logical = resource("http://example.org/fhir/StructureDefinition/LogicalRecord");
    logical.setKind(StructureDefinitionKind.LOGICAL);
    logical.addExtension(ExtensionDefinitions.EXT_ADDITIONAL_RESOURCE, new BooleanType(true));

    assertEquals(Set.of(), registeredNames(profile, logical));
  }

  private StructureDefinition resource(String url) {
    String name = url.substring(url.lastIndexOf('/') + 1);
    StructureDefinition sd = new StructureDefinition();
    sd.setId(name);
    sd.setUrl(url);
    sd.setName(name);
    sd.setType(name);
    sd.setStatus(PublicationStatus.DRAFT);
    sd.setKind(StructureDefinitionKind.RESOURCE);
    sd.setDerivation(TypeDerivationRule.SPECIALIZATION);
    return sd;
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
