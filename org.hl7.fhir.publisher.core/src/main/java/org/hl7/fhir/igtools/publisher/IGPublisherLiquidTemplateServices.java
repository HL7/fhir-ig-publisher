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


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.hl7.fhir.r5.model.DomainResource;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.ILiquidTemplateProvider;
import org.hl7.fhir.utilities.FileUtilities;

public class IGPublisherLiquidTemplateServices implements ILiquidTemplateProvider {

  Map<String, String> templates = new HashMap<>();
  
  public void clear() {
    templates.clear();
  }
  
  public void load(String path) throws FileNotFoundException, IOException {
    File p = new File(path);
    if (!p.exists())
      return;
    for (File f : p.listFiles()) {
      if (f.getName().endsWith(".liquid")) {
        String fn = f.getName();
        fn = fn.substring(0, fn.indexOf("."));
        templates.put(fn.toLowerCase(), FileUtilities.fileToString(f));
      } else if (f.getName().endsWith(".html")) {
        String fn = f.getName();
        templates.put(fn, FileUtilities.fileToString(f));
      } 
    }
  }

  @Override
  public String findTemplate(RenderingContext rcontext, DomainResource r) {
    String s = r.fhirType();
    return templates.get(s.toLowerCase());
  }

  @Override
  public String findTemplate(RenderingContext rcontext, String name) {
    return templates.get(name.toLowerCase());
  }

}
