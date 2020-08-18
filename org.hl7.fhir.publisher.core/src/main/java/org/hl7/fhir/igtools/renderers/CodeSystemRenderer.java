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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.CodeSystemContentMode;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.renderers.RendererFactory;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;

public class CodeSystemRenderer extends BaseRenderer {

  private CodeSystem cs;

  public CodeSystemRenderer(IWorkerContext context, String prefix, CodeSystem cs, IGKnowledgeProvider igp, List<SpecMapManager> maps, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen) {
    super(context, prefix, igp, maps, markdownEngine, packge, gen);
    this.cs = cs;
  }

  public String summary(boolean xml, boolean json, boolean ttl) throws Exception {
    StringBuilder b = new StringBuilder();
    b.append("<table class=\"grid\">\r\n");
    b.append(" <tbody><tr><td>"+translate("cs.summary", "Defining URL")+":</td><td>"+Utilities.escapeXml(cs.getUrl())+"</td></tr>\r\n");
    if (cs.hasVersion()) {
      b.append(" <tr><td>"+translate("cs.summary", "Version")+":</td><td>"+Utilities.escapeXml(cs.getVersion())+"</td></tr>\r\n");
    }
    if (cs.hasName()) {
      b.append(" <tr><td>"+translate("vs.summary", "Name")+":</td><td>"+Utilities.escapeXml(gt(cs.getNameElement()))+"</td></tr>\r\n");
    }
    if (cs.hasTitle()) {
      b.append(" <tr><td>"+translate("vs.summary", "Title")+":</td><td>"+Utilities.escapeXml(gt(cs.getTitleElement()))+"</td></tr>\r\n");
    }
    b.append(" <tr><td>"+translate("cs.summary", "Status")+":</td><td>"+describeStatus(cs.getStatus(), cs.getExperimental())+"</td></tr>\r\n");
    b.append(" <tr><td>"+translate("cs.summary", "Content")+":</td><td>"+describeContent(cs.getContent())+"</td></tr>\r\n");
    if (cs.hasDescription()) {
      b.append(" <tr><td>"+translate("vs.summary", "Definition")+":</td><td>"+processMarkdown("description", cs.getDescriptionElement())+"</td></tr>\r\n");
    }
    if (cs.hasPublisher()) {
      b.append(" <tr><td>"+translate("cs.summary", "Publisher")+":</td><td>"+Utilities.escapeXml(gt(cs.getPublisherElement()))+"</td></tr>\r\n");
    }
    if (cs.hasExtension(ToolingExtensions.EXT_WORKGROUP)) {
      b.append(" <tr><td>"+translate("vs.summary", "Committee")+":</td><td>"+renderCommitteeLink(cs)+"</td></tr>\r\n");
    }
    if (CodeSystemUtilities.hasOID(cs)) {
      b.append(" <tr><td>"+translate("cs.summary", "OID")+":</td><td>"+CodeSystemUtilities.getOID(cs)+" ("+translate("cs.summary", "for OID based terminology systems")+")</td></tr>\r\n");
    }
    if (cs.hasContent()) {
      b.append(" <tr><td>"+translate("cs.summary", "Content Mode")+":</td><td>"+translate("cs.summary", cs.getContent().getDisplay())+"</td></tr>\r\n");      
    }
    if (cs.hasValueSet()) {
      ValueSet vs = context.fetchResource(ValueSet.class, cs.getValueSet());
      if (vs == null) {
        b.append(" <tr><td>"+translate("cs.summary", "Value Set")+":</td><td>"+ cs.getValueSet()+" ("+translate("cs.summary", " is the value set for all codes in this code system")+")</td></tr>\r\n");
      } else {
        b.append(" <tr><td>"+translate("cs.summary", "Value Set")+":</td><td><a href=\""+vs.getUserString("path")+"\">"+ cs.getValueSet()+"</a> ("+translate("cs.summary", " is the value set for all codes in this code system")+")</td></tr>\r\n");        
      }
    }
    if (cs.hasCopyright()) {
      b.append(" <tr><td>"+translate("cs.summary", "Copyright")+":</td><td>"+processMarkdown("copyright", cs.getCopyrightElement())+"</td></tr>\r\n");
    }
    if (ToolingExtensions.hasExtension(cs, ToolingExtensions.EXT_FMM_LEVEL)) {
      // We link to the current version of FHIR because for historical versions (e.g. DSTU2), maturity was captured in different places
      b.append(" <tr><td><a class=\"fmm\" href=\"http://hl7.org/fhir/versions.html#maturity\" title=\"Maturity Level\">"+translate("cs.summary", "Maturity")+"</a>:</td><td>"+ToolingExtensions.readStringExtension(cs, ToolingExtensions.EXT_FMM_LEVEL)+"</td></tr>\r\n");
    }
    if (xml || json || ttl) {
      b.append(" <tr><td>"+translate("cs.summary", "Source Resource")+":</td><td>");
      boolean first = true;
      if (xml) {
        first = false;
        b.append("<a href=\"CodeSystem-"+cs.getId()+".xml.html\">"+translate("cs.summary", "XML")+"</a>");
      }
      if (json) {
        if (first) first = false; else b.append(" / ");
        b.append("<a href=\"CodeSystem-"+cs.getId()+".json.html\">"+translate("cs.summary", "JSON")+"</a>");
      }
      if (ttl) {
        if (first) first = false; else b.append(" / ");
        b.append("<a href=\"CodeSystem-"+cs.getId()+".ttl.html\">"+translate("cs.summary", "Turtle")+"</a>");
      }
      b.append("</td></tr>\r\n");
    }
    b.append("</tbody></table>\r\n");

    return b.toString();
  }

  private String describeContent(CodeSystemContentMode content) {
    switch (content) {
    case COMPLETE: return translate("cs.summary", "All the concepts defined by the code system are included in the code system resource");
    case NOTPRESENT: return translate("cs.summary", "None of the concepts defined by the code system are included in the code system resource");
    case EXAMPLE: return translate("cs.summary", "A few representative concepts are included in the code system resource");
    case FRAGMENT: return translate("cs.summary", "A subset of the code system concepts are included in the code system resource");
    case SUPPLEMENT: return translate("cs.summary", "This code system resource is a supplement to ")+refCS(cs.getSupplements());
    }
    return "?? illegal content status value "+(content == null ? "(null)" : content.toCode());
  }

  private String refCS(String supplements) {
    CodeSystem tgt = context.fetchCodeSystem(supplements);
    if (tgt != null) {
      return "<a href=\""+tgt.getUserString("path")+"\"><code>"+supplements+"</code></a>";
    } else {
      return "<code>"+supplements+"</code>";
    }
  }

  public String content(Set<String> outputTracker) throws EOperationOutcome, FHIRException, IOException, org.hl7.fhir.exceptions.FHIRException  {
//    if (cs.hasText() && cs.getText().hasDiv())
//      return new XhtmlComposer().compose(cs.getText().getDiv());
//    else {
      CodeSystem csc = cs.copy();
      csc.setId(cs.getId()); // because that's not copied
      csc.setText(null);
      RendererFactory.factory(csc, gen).render(csc);

      return new XhtmlComposer(XhtmlComposer.HTML).compose(csc.getText().getDiv());
//    }
  }

  public String xref() throws FHIRException {
    StringBuilder b = new StringBuilder();
    boolean first = true;
    b.append("\r\n");
    List<String> vsurls = new ArrayList<String>();
    for (CanonicalResource sd : context.allConformanceResources()) {
      if (sd instanceof ValueSet)
        vsurls.add(sd.getUrl());
    }
    Collections.sort(vsurls);

    Set<String> processed = new HashSet<String>();
    for (String url : vsurls) {
      ValueSet vc = context.fetchResource(ValueSet.class, url);
      for (ConceptSetComponent ed : vc.getCompose().getInclude())
        first = addLink(b, first, vc, ed, processed);
      for (ConceptSetComponent ed : vc.getCompose().getExclude())
        first = addLink(b, first, vc, ed, processed);
    }
    if (first)
      b.append("<p>"+translate("cs.xref", "This CodeSystem is not used here; it may be used elsewhere (e.g. specifications and/or implementations that use this content)")+"</p>\r\n");
    else
      b.append("</ul>\r\n");
    return b.toString();
  }

  private boolean addLink(StringBuilder b, boolean first, ValueSet vc, ConceptSetComponent ed, Set<String> processed) {
    if (ed.hasSystem() && ed.getSystem().equals(cs.getUrl())) {
      String path = vc.getUserString("path");
      if (!processed.contains(path)) {
        if (first) {
          first = false;
          b.append("<ul>\r\n");
        } 
        if (path == null) {
          System.out.println("No path for "+vc.getUrl());
        } else {
          b.append(" <li><a href=\""+path+"\">"+Utilities.escapeXml(gt(vc.getNameElement()))+"</a></li>\r\n");
        }
        processed.add(path);
      }
    }
    return first;
  }


}
