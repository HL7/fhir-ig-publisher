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


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.ResourceFactory;
import org.hl7.fhir.r5.utils.structuremap.ITransformerServices;

public class MappingServices implements ITransformerServices {

  private SimpleWorkerContext context;
  private String base;
  private Map<String, Integer> ids = new HashMap<String, Integer>();

  public MappingServices(SimpleWorkerContext context, String base) {
    this.context = context;
    this.base = base;
  }

  @Override
  public Base createResource(Object appInfo, Base res, boolean atRoot) {
    if (!(res instanceof Resource))
      return res;
    
    if (appInfo instanceof Bundle && res instanceof Resource) {
      Bundle bnd = (Bundle) appInfo;
      Resource r = (Resource) res;
      r.setId(getNextId(r.fhirType()));
      bnd.addEntry().setResource(r).setFullUrl(base+"/"+r.fhirType()+"/"+r.getId());
      return res;
    } else
      throw new Error("Context must be a mapping when mapping in the IG publisher");
  }

  private String getNextId(String type) {
    if (!ids.containsKey(type)) {
      ids.put(type, 1);
      return "1";
    } else {
      int id = ids.get(type);
      id++;
      ids.put(type, id);
      return Integer.toString(id);
    }
  }

  @Override
  public Coding translate(Object appInfo, Coding source, String conceptMapUrl) throws FHIRException {
    throw new Error("Not supported yet");
  }

  public void reset() {
    ids.clear();
  }

  @Override
  public void log(String message) {
//    System.out.println(message);
  }

  @Override
  public Base createType(Object appInfo, String name, ProfileUtilities utils) throws FHIRException {
    return ResourceFactory.createResourceOrType(name);
  }

  @Override
  public Base resolveReference(Object appContext, String url) {
    throw new Error("Not done yet");
  }

  @Override
  public List<Base> performSearch(Object appContext, String url) {
    throw new Error("Not supported yet");
  }

}
