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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.DataRequirement;
import org.hl7.fhir.r5.model.DataRequirement.DataRequirementCodeFilterComponent;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.PlanDefinition;
import org.hl7.fhir.r5.model.PlanDefinition.PlanDefinitionActionComponent;
import org.hl7.fhir.r5.model.Questionnaire;
import org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.TriggerDefinition;
import org.hl7.fhir.r5.model.UriType;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.renderers.RendererFactory;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.terminologies.ValueSetUtilities;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class ValueSetRenderer extends BaseRenderer {

  private ValueSet vs;

  public ValueSetRenderer(IWorkerContext context, String prefix, ValueSet vs, IGKnowledgeProvider igp, List<SpecMapManager> maps, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen) {
    super(context, prefix, igp, maps, markdownEngine, packge, gen);
    this.vs = vs; 
  }

  public String summary(FetchedResource r, boolean xml, boolean json, boolean ttl) throws Exception {
    StringBuilder b = new StringBuilder();
    b.append("<table class=\"grid\">\r\n");
    b.append(" <tbody><tr><td>"+translate("vs.summary", "Defining URL")+":</td><td>"+Utilities.escapeXml(vs.getUrl())+"</td></tr>\r\n");
    if (vs.hasVersion()) {
      b.append(" <tr><td>"+translate("cs.summary", "Version")+":</td><td>"+Utilities.escapeXml(vs.getVersion())+"</td></tr>\r\n");
    }
    if (vs.hasName()) {
      b.append(" <tr><td>"+translate("vs.summary", "Name")+":</td><td>"+Utilities.escapeXml(gt(vs.getNameElement()))+"</td></tr>\r\n");
    }
    b.append(" <tr><td>"+translate("cs.summary", "Status")+":</td><td>"+describeStatus(vs.getStatus(), vs.getExperimental())+"</td></tr>\r\n");
    if (vs.hasTitle()) {
      b.append(" <tr><td>"+translate("vs.summary", "Title")+":</td><td>"+Utilities.escapeXml(gt(vs.getTitleElement()))+"</td></tr>\r\n");
    }
    if (vs.hasDescription()) {
      b.append(" <tr><td>"+translate("vs.summary", "Definition")+":</td><td>"+processMarkdown("description", vs.getDescriptionElement())+"</td></tr>\r\n");
    }
    if (vs.hasPublisher())
      b.append(" <tr><td>"+translate("vs.summary", "Publisher")+":</td><td>"+Utilities.escapeXml(gt(vs.getPublisherElement()))+"</td></tr>\r\n");
    if (vs.hasExtension(ToolingExtensions.EXT_WORKGROUP)) {
      b.append(" <tr><td>"+translate("vs.summary", "Committee")+":</td><td>"+renderCommitteeLink(vs)+"</td></tr>\r\n");
    }
    if (ValueSetUtilities.hasOID(vs))
      b.append(" <tr><td>"+translate("vs.summary", "OID")+":</td><td>"+ValueSetUtilities.getOID(vs)+" ("+translate("vs.summary", "for OID based terminology systems")+")</td></tr>\r\n");
    if (vs.hasCopyright())
      b.append(" <tr><td>"+translate("vs.summary", "Copyright")+":</td><td>"+processMarkdown("copyright", vs.getCopyrightElement())+"</td></tr>\r\n");
    if (ToolingExtensions.hasExtension(vs, ToolingExtensions.EXT_FMM_LEVEL)) {
      // Use hard-coded spec link to point to current spec because DSTU2 had maturity listed on a different page
      b.append(" <tr><td><a class=\"fmm\" href=\"http://hl7.org/fhir/versions.html#maturity\" title=\"Maturity Level\">"+translate("cs.summary", "Maturity")+"</a>:</td><td>"+ToolingExtensions.readStringExtension(vs, ToolingExtensions.EXT_FMM_LEVEL)+"</td></tr>\r\n");
    }
    if (xml || json || ttl) {
      b.append(" <tr><td>"+translate("vs.summary", "Source Resource")+":</td><td>");
      boolean first = true;
      String filename = igp.getProperty(r, "format");
      if (filename == null)
        filename = "ValueSet-"+r.getId()+".{{[fmt]}}.html";
      if (xml) {
        first = false;
        b.append("<a href=\""+igp.doReplacements(filename,  r,  null, "xml")+"\">"+translate("vs.summary", "XML")+"</a>");
      }
      if (json) {
        if (first) first = false; else b.append(" / ");
        b.append("<a href=\""+igp.doReplacements(filename,  r,  null, "json")+"\">"+translate("vs.summary", "JSON")+"</a>");
      }
      if (ttl) {
        if (first) first = false; else b.append(" / ");
        b.append("<a href=\""+igp.doReplacements(filename,  r,  null, "ttl")+"\">"+translate("vs.summary", "Turtle")+"</a>");
      }
      b.append("</td></tr>\r\n");
    }
    b.append("</tbody></table>\r\n");

    return b.toString();
  }

  public String cld(Set<String> outputTracker) throws EOperationOutcome, FHIRException, IOException, org.hl7.fhir.exceptions.FHIRException  {
    if (vs.hasText() && vs.getText().hasDiv()) {
      for (XhtmlNode n : vs.getText().getDiv().getChildNodes()) {
        if ("div".equals(n.getName()) && "cld".equals(n.getAttribute("id"))) {
          return "<h3>Definition</h3>\r\n" + new XhtmlComposer(XhtmlComposer.HTML).compose(n);
        }
      }
    }
    ValueSet vsc = vs.copy();
    vsc.setText(null);
    if (vsc.hasCompose()) {
      vsc.setExpansion(null); // we don't want to render an expansion by mistake
    }
    RendererFactory.factory(vsc, gen).render(vsc);
    return "<h3>Logical Definition (CLD)</h3>\r\n" + new XhtmlComposer(XhtmlComposer.HTML).compose(vsc.getText().getDiv());
  }

  public String xref() throws FHIRException {
    StringBuilder b = new StringBuilder();
    boolean first = true;
    b.append("\r\n");
    if (vs.hasUrl()) {
      Set<String> sdurls = new HashSet<String>();
      Set<String> vsurls = new HashSet<String>();
      Set<String> pdurls = new HashSet<String>();
      Set<String> qurls = new HashSet<String>();
      for (CanonicalResource sd : context.allConformanceResources()) {
        if (sd instanceof StructureDefinition)
          sdurls.add(sd.getUrl());
        if (sd instanceof ValueSet)
          vsurls.add(sd.getUrl());
        if (sd instanceof PlanDefinition)
          pdurls.add(sd.getUrl());
        if (sd instanceof Questionnaire && sd.hasUrl())
          qurls.add(sd.getUrl());
      }

      for (String url : sorted(vsurls)) {
        ValueSet vc = context.fetchResource(ValueSet.class, url);
        for (ConceptSetComponent t : vc.getCompose().getInclude()) {
          for (UriType ed : t.getValueSet()) {
            if (ed.getValueAsString().equals(vs.getUrl())) {
              if (first) {
                first = false;
                b.append("<ul>\r\n");
              }
              b.append(" <li>"+translate("vs.usage", "Included into ")+"<a href=\""+vc.getUserString("path")+"\">"+Utilities.escapeXml(gt(vc.getNameElement()))+"</a></li>\r\n");
              break;
            }
          }
        }
        for (ConceptSetComponent t : vc.getCompose().getExclude()) {
          for (UriType ed : t.getValueSet()) {
            if (ed.getValueAsString().equals(vs.getUrl())) {
              if (first) {
                first = false;
                b.append("<ul>\r\n");
              }
              b.append(" <li>"+translate("vs.usage", "Excluded from ")+"<a href=\""+vc.getUserString("path")+"\">"+Utilities.escapeXml(gt(vc.getNameElement()))+"</a></li>\r\n");
              break;
            }
          }
        }
      }
      for (String url : sorted(sdurls)) {
        StructureDefinition sd = context.fetchResource(StructureDefinition.class, url);
        if (sd != null) {
          for (ElementDefinition ed : sd.getSnapshot().getElement()) {
            if (ed.hasBinding() && ed.getBinding().hasValueSet()) {
              if ((ed.getBinding().hasValueSet() && ed.getBinding().getValueSet().equals(vs.getUrl()))) {
                if (first) {
                  first = false;
                  b.append("<ul>\r\n");
                }
                String path = sd.getUserString("path");
                if (path == null) {
                  System.out.println("No path for "+sd.getUrl());
                } else {
                  b.append(" <li><a href=\""+path+"\">"+Utilities.escapeXml(sd.present())+"</a></li>\r\n");
                }
                break;
              }
            }
          }
        }
      }

      for (String url : sorted(qurls)) {
        Questionnaire q = context.fetchResource(Questionnaire.class, url);
        if (q != null) {
          if (questionnaireUsesValueSet(q.getItem(), vs.getUrl())) {
            if (first) {
              first = false;
              b.append("<ul>\r\n");
            }
            String path = q.getUserString("path");
            if (path == null) {
              System.out.println("No path for "+q.getUrl());
            } else {
              b.append(" <li><a href=\""+path+"\">"+Utilities.escapeXml(q.present())+"</a></li>\r\n");
            }
            break;
          }
        }
      }

      for (String u : sorted(pdurls)) {
        PlanDefinition pd = context.fetchResource(PlanDefinition.class, u);
        if (referencesValueSet(pd)) {
          if (first) {
            first = false;
            b.append("<ul>\r\n");
          }
          b.append(" <li>Used as a trigger criteria in <a href=\""+pd.getUserString("path")+"\">"+Utilities.escapeXml(pd.present())+"</a></li>\r\n");
        }
      }
    }
    if (first)
      b.append("<p>"+translate("vs.usage", "This value set is not used here; it may be used elsewhere (e.g. specifications and/or implementations that use this content)")+"</p>\r\n");
    else
      b.append("</ul>\r\n");
    return b.toString();
  }

  private boolean questionnaireUsesValueSet(List<QuestionnaireItemComponent> items, String url) {
    for (QuestionnaireItemComponent i : items) {
      if (i.hasAnswerValueSet() && url.equals(i.getAnswerValueSet()))
        return true;
      if (questionnaireUsesValueSet(i.getItem(), url))
        return true;
    }
    return false;
  }

  private List<String> sorted(Collection<String> collection) {
    List<String> res = new ArrayList<>();
    res.addAll(collection);
    Collections.sort(res);
    return res;
  }

  private boolean referencesValueSet(PlanDefinition pd) {
    for (PlanDefinitionActionComponent pda : pd.getAction()) {
      for (TriggerDefinition td : pda.getTrigger()) {
        for (DataRequirement dr : td.getData())
          for (DataRequirementCodeFilterComponent ed : dr.getCodeFilter())
            if (ed.getValueSet().equals(vs.getUrl()))
              return true;
      }
    }
    return false;
  }

  
}
