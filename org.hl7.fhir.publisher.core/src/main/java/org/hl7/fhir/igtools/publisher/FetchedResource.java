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
import java.util.HashSet;
import java.util.List;

import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionResourceComponent;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.utilities.validation.ValidationMessage;

import com.google.gson.JsonObject;

public class FetchedResource {
  private String id;
  private String title;
  private String type;
  private String path; // where published
  private Resource resource;
  private Element element;
  private JsonObject config;
  private boolean validated;
  private boolean validateAsResource;
  private List<String> statedProfiles = new ArrayList<String>();
  private List<String> foundProfiles = new ArrayList<String>();
  private boolean snapshotted;
  private String exampleUri;
  private HashSet<FetchedResource> statedExamples = new HashSet<FetchedResource>();
  private HashSet<FetchedResource> foundExamples = new HashSet<FetchedResource>();
  private ImplementationGuideDefinitionResourceComponent resEntry;
  private List<ProvenanceDetails> audits = new ArrayList<>();
  private List<ValidationMessage> errors = new ArrayList<ValidationMessage>();
  private boolean isProvenance = false;

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
    type = element.fhirType();
    return this;
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
    return (this.exampleUri != null) || (resEntry != null && resEntry.hasIsExample() && (!resEntry.getIsExample()));
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
  
  
}
