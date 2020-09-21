package org.hl7.fhir.igtools.renderers;

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


import java.io.IOException;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.utils.StructureMapUtilities;
import org.hl7.fhir.r5.utils.StructureMapUtilities.StructureMapAnalysis;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;

public class StructureMapRenderer extends CanonicalRenderer {


  private StructureMapUtilities utils;
  private StructureMap map;
  private StructureMapAnalysis analysis;
  private String destDir;

  public StructureMapRenderer(IWorkerContext context, String prefix, StructureMap map, String destDir, IGKnowledgeProvider igp, List<SpecMapManager> maps, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen) {
    super(context, prefix, map, destDir, igp, maps, markdownEngine, packge, gen);
    this.map = map;
    this.destDir = destDir;
    utils = new StructureMapUtilities(context, null, igp);
    analysis = (StructureMapAnalysis) map.getUserData("analysis");
  }

  @Override
  protected void genSummaryRowsSpecific(StringBuilder b) {
  }

  public String profiles() {
    StringBuilder b = new StringBuilder();
    b.append("<ul>\r\n");
    for (StructureDefinition sd : analysis.getProfiles()) {
      b.append("  <li><a href=\""+sd.getUserString("path")+"\">"+Utilities.escapeXml(gt(sd.getNameElement()))+"</a></li>\r\n");
    }
    b.append("</ul>\r\n");
    return b.toString();
  }

  public String script() throws FHIRException {
    return StructureMapUtilities.render(map);
  }

  public String content() throws IOException {
    if (analysis == null) {
      try {
        analysis = utils.analyse(null, map);
      } catch (FHIRException e) {
        return "Error in Map: "+e.getMessage();  
      }
      map.setUserData("analysis", analysis);
    }      
    return new XhtmlComposer(XhtmlComposer.HTML).compose(analysis.getSummary());
  }


  
}
