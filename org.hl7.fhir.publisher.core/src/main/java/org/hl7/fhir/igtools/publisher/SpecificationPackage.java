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


import java.io.FileNotFoundException;
import java.io.IOException;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.context.SimpleWorkerContext.IContextResourceLoader;
import org.hl7.fhir.r5.context.SimpleWorkerContext.ILoadFilter;
import org.hl7.fhir.utilities.cache.NpmPackage;

public class SpecificationPackage {

  private SimpleWorkerContext context;

//  public static SpecificationPackage fromPath(String path) throws FileNotFoundException, IOException, FHIRException {
//    SpecificationPackage self = new SpecificationPackage();
//    self.context = SimpleWorkerContext.fromPath(path);
//    return self;
//  }

//  public static SpecificationPackage fromClassPath(String path) throws IOException, FHIRException {
//    SpecificationPackage self = new SpecificationPackage();
//    self.context = SimpleWorkerContext.fromClassPath(path);
//    return self;
//  }
//
//  public static SpecificationPackage fromPath(String path, IContextResourceLoader loader) throws FileNotFoundException, IOException, FHIRException {
//    SpecificationPackage self = new SpecificationPackage();
//    self.context = SimpleWorkerContext.fromPath(path, loader);
//    return self;
//  }

  public SimpleWorkerContext makeContext() throws FileNotFoundException, IOException, FHIRException {
    return new SimpleWorkerContext(context);
  }

  public void loadOtherContent(NpmPackage pi) throws FileNotFoundException, Exception {
    context.loadBinariesFromFolder(pi);

  }

  public static SpecificationPackage fromPackage(NpmPackage pi, IContextResourceLoader loader) throws FileNotFoundException, IOException, FHIRException {
    return fromPackage(pi, loader, null);
  }
  
  public static SpecificationPackage fromPackage(NpmPackage pi, IContextResourceLoader loader, ILoadFilter filter) throws FileNotFoundException, IOException, FHIRException {
    SpecificationPackage self = new SpecificationPackage();
    self.context = SimpleWorkerContext.fromPackage(pi, loader, filter);
    return self;
  }

  public static SpecificationPackage fromPackage(NpmPackage pi) throws FileNotFoundException, IOException, FHIRException {
    return fromPackage(pi, null);
  }
  public static SpecificationPackage fromPackage(NpmPackage pi, ILoadFilter filter) throws FileNotFoundException, IOException, FHIRException {
    SpecificationPackage self = new SpecificationPackage();
    self.context = SimpleWorkerContext.fromPackage(pi, null);
    return self;
  }

}
