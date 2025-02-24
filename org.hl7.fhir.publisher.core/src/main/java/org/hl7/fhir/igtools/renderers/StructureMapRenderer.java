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
import java.util.Set;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.RelatedIG;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.r5.renderers.Renderer.RenderingStatus;
import org.hl7.fhir.r5.renderers.RendererFactory;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.r5.utils.structuremap.StructureMapAnalysis;
import org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class StructureMapRenderer extends CanonicalRenderer {


  private StructureMapUtilities utils;
  private StructureMap map;
  private StructureMapAnalysis analysis;
  private String destDir;

  public StructureMapRenderer(IWorkerContext context, String corePath, StructureMap map, String destDir, IGKnowledgeProvider igp, List<SpecMapManager> maps, Set<String> allTargets, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen, String versionToAnnotate, List<RelatedIG> relatedIgs) {
    super(context, corePath, map, destDir, igp, maps, allTargets, markdownEngine, packge, gen, versionToAnnotate, relatedIgs);
    this.map = map;
    this.destDir = destDir;
    utils = new StructureMapUtilities(context, null, igp);
    analysis = (StructureMapAnalysis) map.getUserData(UserDataNames.pub_analysis);
  }

  @Override
  protected void genSummaryRowsSpecific(StringBuilder b, Set<String> rows) {
  }

  public String profiles() {
    StringBuilder b = new StringBuilder();
    if (analysis == null) {
      b.append("<p>Analysis is not available</p>\r\n");
    } else { 
      b.append("<ul>\r\n");
      for (StructureDefinition sd : analysis.getProfiles()) {
        b.append("  <li><a href=\""+sd.getWebPath()+"\">"+Utilities.escapeXml(gen.getTranslated(sd.getNameElement()))+"</a></li>\r\n");
      }
      b.append("</ul>\r\n");
    } 
    return b.toString();
  }

  public String script(boolean plainText) throws FHIRException, IOException, EOperationOutcome {
    if (plainText) {
      return StructureMapUtilities.render(map);
    } else {
      XhtmlNode node = new XhtmlNode(NodeType.Element, "div");
      RendererFactory.factory(map, gen).buildNarrative(new RenderingStatus(), node, ResourceWrapper.forResource(gen.getContextUtilities(), map));
      return new XhtmlComposer(false, false).compose(node);
    }
  }

  public String content() throws IOException {
    if (analysis == null) {
      try {
        analysis = utils.analyse(null, map);
      } catch (FHIRException e) {
        return "Error in Map: "+e.getMessage();  
      }
      map.setUserData(UserDataNames.pub_analysis, analysis);
    }      
    XhtmlNode summary = analysis.getSummary();
    return summary == null ? "" : new XhtmlComposer(XhtmlComposer.HTML).compose(summary);        
  }


  
}
