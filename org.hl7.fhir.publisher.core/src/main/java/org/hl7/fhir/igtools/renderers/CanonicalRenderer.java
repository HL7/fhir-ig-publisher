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
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.terminologies.ValueSetUtilities;
import org.hl7.fhir.r5.utils.StructureMapUtilities;
import org.hl7.fhir.r5.utils.StructureMapUtilities.StructureMapAnalysis;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;

public class CanonicalRenderer extends BaseRenderer {


  private CanonicalResource cr;
  private String destDir;

  public CanonicalRenderer(IWorkerContext context, String prefix, CanonicalResource cr, String destDir, IGKnowledgeProvider igp, List<SpecMapManager> maps, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen) {
    super(context, prefix, igp, maps, markdownEngine, packge, gen);
    this.cr = cr;
    this.destDir = destDir;
  }

  public String summaryTable(FetchedResource r, boolean xml, boolean json, boolean ttl) throws Exception {
    StringBuilder b = new StringBuilder();
    b.append("<table class=\"grid\">\r\n<tbody>\r\n");
    genSummaryCore1(b);
    genSummaryRowsSpecific(b);
    genSummaryCore2(b, r, xml, json, ttl);
    b.append("</tbody></table>\r\n");

    return b.toString();
  }

  private void genSummaryCore1(StringBuilder b) {
    b.append("<tr><td>"+translate("cr.summary", "Defining URL")+":</td><td>"+Utilities.escapeXml(cr.getUrl())+"</td></tr>\r\n");
    if (cr.hasVersion()) {
      b.append(" <tr><td>"+translate("cs.summary", "Version")+":</td><td>"+Utilities.escapeXml(cr.getVersion())+"</td></tr>\r\n");
    }
    if (cr.hasName()) {
      b.append(" <tr><td>"+translate("cr.summary", "Name")+":</td><td>"+Utilities.escapeXml(gt(cr.getNameElement()))+(cr.hasTitle() ? "\""+Utilities.escapeXml(gt(cr.getTitleElement()))+"\"" : "")+"</td></tr>\r\n");
    }
    b.append(" <tr><td>"+translate("cs.summary", "Status")+":</td><td>"+describeStatus(cr)+"</td></tr>\r\n");
    if (cr.hasDescription()) {
      b.append(" <tr><td>"+translate("cr.summary", "Definition")+":</td><td>"+processMarkdown("description", cr.getDescriptionElement())+"</td></tr>\r\n");
    }
    if (cr.hasPublisher())
      b.append(" <tr><td>"+translate("cr.summary", "Publisher")+":</td><td>"+Utilities.escapeXml(gt(cr.getPublisherElement()))+"</td></tr>\r\n");
    if (cr.hasExtension(ToolingExtensions.EXT_WORKGROUP)) {
      b.append(" <tr><td>"+translate("cr.summary", "Committee")+":</td><td>"+renderCommitteeLink(cr)+"</td></tr>\r\n");
    }
    if (cr.hasCopyright())
      b.append(" <tr><td>"+translate("cr.summary", "Copyright")+":</td><td>"+processMarkdown("copyright", cr.getCopyrightElement())+"</td></tr>\r\n");
    if (ToolingExtensions.hasExtension(cr, ToolingExtensions.EXT_FMM_LEVEL)) {
      // Use hard-coded spec link to point to current spec because DSTU2 had maturity listed on a different page
      b.append(" <tr><td><a class=\"fmm\" href=\"http://hl7.org/fhir/versions.html#maturity\" title=\"Maturity Level\">"+translate("cs.summary", "Maturity")+"</a>:</td><td>"+ToolingExtensions.readStringExtension(cr, ToolingExtensions.EXT_FMM_LEVEL)+"</td></tr>\r\n");
    }    
  }

  protected void genSummaryRowsSpecific(StringBuilder b) {
    // Nothing    
  }

  private void genSummaryCore2(StringBuilder b, FetchedResource r, boolean xml, boolean json, boolean ttl) {
    if (xml || json || ttl) {
      b.append(" <tr><td>"+translate("cr.summary", "Source Resource")+":</td><td>");
      boolean first = true;
      String filename = igp.getProperty(r, "format");
      if (filename == null)
        filename = "ValueSet-"+r.getId()+".{{[fmt]}}.html";
      if (xml) {
        first = false;
        b.append("<a href=\""+igp.doReplacements(filename,  r,  null, "xml")+"\">"+translate("cr.summary", "XML")+"</a>");
      }
      if (json) {
        if (first) first = false; else b.append(" / ");
        b.append("<a href=\""+igp.doReplacements(filename,  r,  null, "json")+"\">"+translate("cr.summary", "JSON")+"</a>");
      }
      if (ttl) {
        if (first) first = false; else b.append(" / ");
        b.append("<a href=\""+igp.doReplacements(filename,  r,  null, "ttl")+"\">"+translate("cr.summary", "Turtle")+"</a>");
      }
      b.append("</td></tr>\r\n");
    }    
  }

  protected String describeStatus(CanonicalResource cr) {
    return describeStatus(cr.getStatus(), cr.getExperimental());
  }

  protected String describeStatus(PublicationStatus status, boolean experimental) {
    switch (status) {
    case ACTIVE: return experimental ? "Experimental" : "Active"; 
    case DRAFT: return "draft";
    case RETIRED: return "retired";
    default: return "Unknown";
    }
  }


}
