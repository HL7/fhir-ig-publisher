package org.hl7.fhir.igtools.publisher;

/*-
 * #%L
 * org.hl7.fhir.publisher.core
 * %%
 * Copyright (C) 2014 - 2019 Health Level 7
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.convertors.misc.ProfileVersionAdaptor.ConversionMessage;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionResourceComponent;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.validation.ValidationMessage;


public class FetchedResource {

  public static class AlternativeVersionResource {
    List<ConversionMessage> log;
    CanonicalResource cr;
    protected AlternativeVersionResource(List<ConversionMessage> log, CanonicalResource cr) {
      super();
      this.log = log;
      this.cr = cr;
    }
    public List<ConversionMessage> getLog() {
      return log;
    }
    public CanonicalResource getResource() {
      return cr;
    }
  }

  private String id;
  private String title;
  private String type;
  private String path; // where published
  private Resource resource;
  private Element element;
  private Element logicalElement;
  private JsonObject config;
  private boolean validated;
  private boolean validateAsResource;
  private List<String> statedProfiles = new ArrayList<String>();
  private List<String> foundProfiles = new ArrayList<String>();
  private List<String> testArtifacts = new ArrayList<String>();
  private boolean snapshotted;
  private String exampleUri;
  private HashSet<FetchedResource> statedExamples = new HashSet<FetchedResource>();
  private HashSet<FetchedResource> foundExamples = new HashSet<FetchedResource>();
  private HashSet<FetchedResource> foundTestPlans = new HashSet<FetchedResource>();
  private HashSet<FetchedResource> foundTestScripts = new HashSet<FetchedResource>();
  private ImplementationGuideDefinitionResourceComponent resEntry;
  private List<ProvenanceDetails> audits = new ArrayList<>();
  private List<ValidationMessage> errors = new ArrayList<ValidationMessage>();
  private boolean isProvenance = false;
  private String nameForErrors;
  private boolean hasTranslations;
  private String resourceName;
  private String resourceDescription;
  private boolean regenAfterValidation;
  private Map<String, AlternativeVersionResource> otherVersions;
  private boolean umlGenerated;
  @Getter @Setter private boolean generatedNarrative; // if we generate the narrative, we'll regenerate it later

  public FetchedResource(String nameForErrors) {
    super();
    this.nameForErrors = nameForErrors;
  }

  public Resource getResource() {
    return resource;
  }

  public void setResource(Resource resource) {
    this.resource = resource;
  }
  
  public Element getElement() {
    return element;
  }
  
  public FetchedResource setElement(Element element) {
    this.element = element;
    if (type == null) {
      type = element.fhirType();
    }
    return this;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getId() {
    return id;
  }
  
  public FetchedResource setId(String id) {
    this.id = id;
    return this;
  }
  
  public Boolean hasTitle() {
    return title != null;
  }
  
  public String getTitle() {
    return title == null ? type+"/" + id : title;
  }
  
  public FetchedResource setTitle(String title) {
    this.title = title;
    return this;
  }
  
  public JsonObject getConfig() {
    return config;
  }
  
  public void setConfig(JsonObject config) {
    this.config = config;
  }
  
  public boolean isValidated() {
    return validated;
  }
  
  public void setValidated(boolean validated) {
    this.validated = validated;
  }
  
  public List<String> getProfiles(boolean statedOnly) {
    List<String> res = new ArrayList<>();
    res.addAll(statedProfiles);
    if (!statedOnly) {
      res.addAll(foundProfiles);
    }
    return res;
  }
  
  public List<String> getStatedProfiles() {
    return statedProfiles;
  }
  
  public List<String> getFoundProfiles() {
    return foundProfiles;
  }
  
  public String getUrlTail() {
    return "/"+type+"/"+id;
  }
  
  public boolean isSnapshotted() {
    return snapshotted;
  }
  
  public void setSnapshotted(boolean snapshotted) {
    this.snapshotted = snapshotted;  
  }
  
  public String getLocalRef() {
    return type+"/"+id;
  }

  public String getExampleUri() {
    return exampleUri;
  }  

  public void setExampleUri(String exampleUri) {
    this.exampleUri = exampleUri;
  }  

  public boolean isExample() {
    return (this.exampleUri != null) || (resEntry != null && resEntry.hasIsExample() && (resEntry.getIsExample()));
  }  

  public HashSet<FetchedResource> getFoundExamples() {
    return foundExamples;
  }  

  public void addFoundExample(FetchedResource r) {
    this.foundExamples.add(r);
  }
  
  public HashSet<FetchedResource> getStatedExamples() {
    return statedExamples;
  }  

  public void addStatedExample(FetchedResource r) {
    this.statedExamples.add(r);
  }
  
  public String fhirType() {
    return type != null ? type : resource != null ? resource.fhirType() : element != null ? element.fhirType() : "?fr?";
  }
  
  public void setResEntry(ImplementationGuideDefinitionResourceComponent value) {
    this.resEntry = value;
  }

  /**
   * Note: this may be broken after the Template.beforeGenerate event, so do not use after that 
   * @return
   */
  public ImplementationGuideDefinitionResourceComponent getResEntry() {
    return resEntry;
  }
  public boolean isValidateAsResource() {
    return validateAsResource;
  }
  public void setValidateAsResource(boolean validateAsResource) {
    this.validateAsResource = validateAsResource;
  }
  public List<ProvenanceDetails> getAudits() {
    return audits;
  }
  public boolean hasHistory() {
    return !audits.isEmpty();
  }


  public List<ValidationMessage> getErrors() {
    return errors;
  }
  
  public boolean getProvenance() {
    return this.isProvenance;
  }

  public void setProvenance(boolean isProvenance) {
    this.isProvenance = isProvenance;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public void addTestArtifact(String profile) {
    // Check for duplicate
    if (!testArtifacts.contains(profile)) {
      testArtifacts.add(profile);
    }
  }
	  
  public List<String> getTestArtifacts() {
    return testArtifacts;
  }

  public boolean hasTestArtifacts() {
    return !testArtifacts.isEmpty();
  }

  public HashSet<FetchedResource> getFoundTestPlans() {
    return foundTestPlans;
  }

  public void addFoundTestPlan(FetchedResource r) {
    // Check for duplicate
    if (!foundTestPlans.contains(r)) {
      this.foundTestPlans.add(r);
    }
  }

  public boolean hasFoundTestPlans() {
    return !foundTestPlans.isEmpty();
  }

  public HashSet<FetchedResource> getFoundTestScripts() {
    return foundTestScripts;
  }

  public void addFoundTestScript(FetchedResource r) {
    // Check for duplicate
    if (!foundTestScripts.contains(r)) {
      this.foundTestScripts.add(r);
    }
  }

  public boolean hasFoundTestScripts() {
    return !foundTestScripts.isEmpty();
  }

  @Override
  public String toString() {
    return fhirType()+"/"+getId(); 
  }

  public String getNameForErrors() {
    return nameForErrors;
  }

  public void setNameForErrors(String nameForErrors) {
    this.nameForErrors = nameForErrors;
  }

  public Element getLogicalElement() {
    return logicalElement;
  }

  public void setLogicalElement(Element logicalElement) {
    this.logicalElement = logicalElement;
  }

  public boolean isHasTranslations() {
    return hasTranslations;
  }

  public void setHasTranslations(boolean hasTranslations) {
    this.hasTranslations = hasTranslations;
  }

  public String getContentType() {
    switch (element.getFormat()) {
    case FML: return "application/fhir+fml";
    case JSON: return "application/fhir+json";
    case SHC: return "application/smart-health-card";
    case SHL: return "application/smart-api-access";
    case TEXT: return "text/plain";
    case TURTLE: return "application/fhir+ttl";
    case VBAR: return "application/hl7-v2";
    case XML: return "application/fhir+xml";
    default:
      break;
    
    }
    return "application/bin";
  }

  public String getResourceName() {
    return resourceName;
  }

  public void setResourceName(String resourceName) {
    this.resourceName = resourceName;
  }

  public String getResourceDescription() {
    return resourceDescription;
  }

  public void setResourceDescription(String resourceDescription) {
    this.resourceDescription = resourceDescription;
  }

  public String getBestName() {
    if (resourceName != null && resourceName.contains(" ")) {
      return resourceName;
    } else {
      return resourceDescription;
    }
  }

  public boolean isRegenAfterValidation() {
    return regenAfterValidation;
  }

  public void setRegenAfterValidation(boolean regenAfterValidation) {
    this.regenAfterValidation = regenAfterValidation;
  }

  public boolean isCustomResource() {
    if (getResource() != null) {
      return getResource().hasUserData(UserDataNames.loader_custom_resource);
    } else {
      return getElement().getProperty().getStructure().hasUserData(UserDataNames.loader_custom_resource);
    }
  }

  public boolean isCanonical(IWorkerContext context) {
    StructureDefinition sd = getElement().getProperty().getStructure();
    // Enumerate the R4 canonicals because they won't have CanonicalResource in their hierarchy
    switch (sd.getType()) {
      case "ActivityDefinition":
      case "CapabilityStatement":
      case "ChargeItemDefinition":
      case "CodeSystem":
      case "CompartmentDefinition":
      case "ConceptMap":
      case "EventDefinition":
      case "ExampleScenario":
      case "GraphDefinition":
      case "ImplementationGuide":
      case "Library":
      case "Measure":
      case "MessageDefinition":
      case "NamingSystem":
      case "ObservationDefinition":
      case "OperationDefinition":
      case "PlanDefinition":
      case "Questionnaire":
      case "SearchParameter":
      case "SpecimenDefinition":
      case "StructureDefinition":
      case "StructureMap":
      case "TerminologyCapabilities":
      case "TestScript":
      case "ValueSet":
        return true;
    }
    while (sd != null) {
      if ("CanonicalResource".equals(sd.getType())) {
        return true;
      }
      sd = context.fetchResource(StructureDefinition.class, sd.getBaseDefinition());
    }
    return false;
  }

  public Map<String, AlternativeVersionResource> getOtherVersions() {
    if (otherVersions == null) {
      otherVersions = new HashMap<String, FetchedResource.AlternativeVersionResource>();
    }
    return otherVersions;
  }

  public boolean hasOtherVersions() {
    return otherVersions != null && !otherVersions.isEmpty();
  }

  public boolean isUmlGenerated() {
    return umlGenerated;
  }

  public void setUmlGenerated(boolean umlGenerated) {
    this.umlGenerated = umlGenerated;
  }

  
}
