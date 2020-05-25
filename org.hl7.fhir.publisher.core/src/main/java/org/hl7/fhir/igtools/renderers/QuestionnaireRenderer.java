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

import org.hl7.fhir.exceptions.DefinitionException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.Questionnaire;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.QuestionnaireRendererMode;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.r5.utils.StructureMapUtilities;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.r5.utils.StructureMapUtilities.StructureMapAnalysis;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;

public class QuestionnaireRenderer extends BaseRenderer {


  private Questionnaire q;
  private String destDir;

  public QuestionnaireRenderer(IWorkerContext context, String prefix, Questionnaire q, String destDir, IGKnowledgeProvider igp, List<SpecMapManager> maps, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen) {
    super(context, prefix, igp, maps, markdownEngine, packge, gen);
    this.q = q;
    this.destDir = destDir;
  }

  public String summary(FetchedResource r, boolean xml, boolean json, boolean ttl) throws Exception {
//    return "[--Summary goes here--]";
    StringBuilder b = new StringBuilder();
    b.append("<table class=\"grid\">\r\n");
    b.append(" <tbody><tr><td>"+translate("sm.summary", "Defining URL")+":</td><td>"+Utilities.escapeXml(q.getUrl())+"</td></tr>\r\n");
    if (q.hasVersion())
      b.append(" <tr><td>"+translate("cs.summary", "Version")+":</td><td>"+Utilities.escapeXml(q.getVersion())+"</td></tr>\r\n");
    b.append(" <tr><td>"+translate("sm.summary", "Name")+":</td><td>"+Utilities.escapeXml(gt(q.getNameElement()))+"</td></tr>\r\n");
    if (q.hasDescription())
      b.append(" <tr><td>"+translate("sm.summary", "Definition")+":</td><td>"+processMarkdown("description", q.getDescriptionElement())+"</td></tr>\r\n");
    if (q.hasPublisher())
      b.append(" <tr><td>"+translate("sm.summary", "Publisher")+":</td><td>"+Utilities.escapeXml(gt(q.getPublisherElement()))+"</td></tr>\r\n");
    if (q.hasCopyright())
      b.append(" <tr><td>"+translate("sm.summary", "Copyright")+":</td><td>"+processMarkdown("copyright", q.getCopyrightElement())+"</td></tr>\r\n");

    if (xml || json || ttl) {
      b.append(" <tr><td>"+translate("sm.summary", "Source Resource")+":</td><td>");
      boolean first = true;
      String filename = igp.getProperty(r, "format");
      if (filename == null)
        filename = "ValueSet-"+r.getId()+".{{[fmt]}}.html";
      if (xml) {
        first = false;
        b.append("<a href=\""+igp.doReplacements(filename,  r,  null, "xml")+"\">"+translate("sm.summary", "XML")+"</a>");
      }
      if (json) {
        if (first) first = false; else b.append(" / ");
        b.append("<a href=\""+igp.doReplacements(filename,  r,  null, "json")+"\">"+translate("sm.summary", "JSON")+"</a>");
      }
      if (ttl) {
        if (first) first = false; else b.append(" / ");
        b.append("<a href=\""+igp.doReplacements(filename,  r,  null, "ttl")+"\">"+translate("sm.summary", "Turtle")+"</a>");
      }
      b.append("</td></tr>\r\n");
    }
    b.append("</tbody></table>\r\n");

    return b.toString();    
  }

  public String render(QuestionnaireRendererMode mode) throws IOException, FHIRFormatError, DefinitionException, FHIRException, EOperationOutcome {
    org.hl7.fhir.r5.renderers.QuestionnaireRenderer qr = new org.hl7.fhir.r5.renderers.QuestionnaireRenderer(gen);
    gen.setQuestionnaireMode(mode);
    return new XhtmlComposer(XhtmlComposer.HTML).compose(qr.build(q));
  }

}
