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
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.comparison.CanonicalResourceComparer.CanonicalResourceComparison;
import org.hl7.fhir.r5.comparison.CanonicalResourceComparer.ChangeAnalysisState;
import org.hl7.fhir.r5.comparison.VersionComparisonAnnotation;
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
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class ValueSetRenderer extends CanonicalRenderer {

  private ValueSet vs;

  public ValueSetRenderer(IWorkerContext context, String corePath, ValueSet vs, IGKnowledgeProvider igp, List<SpecMapManager> maps, Set<String> allTargets, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen, String versionToAnnotate) {
    super(context, corePath, vs, null, igp, maps, allTargets, markdownEngine, packge, gen, versionToAnnotate);
    this.vs = vs; 
  }

  @Override
  protected void genSummaryRowsSpecific(StringBuilder b, Set<String> rows) {
    if (hasSummaryRow(rows, "oid")) {
      if (ValueSetUtilities.hasOID(vs)) {
        b.append(" <tr><td>"+translate("cr.summary", "OID")+":</td><td>"+ValueSetUtilities.getOID(vs)+" ("+translate("cr.summary", "for OID based terminology systems")+")</td></tr>\r\n");
      }
    }
  }

  public String cld(Set<String> outputTracker) throws EOperationOutcome, FHIRException, IOException, org.hl7.fhir.exceptions.FHIRException  {
    if (vs.hasText() && !vs.getText().hasUserData("renderer.generated") && vs.getText().hasDiv()) {
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
      RendererFactory.factory(vsc, gen).renderOrError(vsc);
      return "<h3>Logical Definition (CLD)</h3>\r\n" + new XhtmlComposer(XhtmlComposer.HTML).compose(vsc.getText().getDiv());
    } else {
      return "<h3>Logical Definition (CLD)</h3>\r\n<p>No formal definition provided for this ValueSet</p>\r\n";
      
    }
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
      for (CanonicalResource sd : context.fetchResourcesByType(CanonicalResource.class)) {
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
        ValueSet vc = context.findTxResource(ValueSet.class, url);
        for (ConceptSetComponent t : vc.getCompose().getInclude()) {
          for (UriType ed : t.getValueSet()) {
            if (ed.getValueAsString().equals(vs.getUrl())) {
              if (first) {
                first = false;
                b.append("<ul>\r\n");
              }
              b.append(" <li>"+translate("vs.usage", "Included into ")+"<a href=\""+vc.getWebPath()+"\">"+Utilities.escapeXml(gt(vc.getNameElement()))+"</a></li>\r\n");
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
              b.append(" <li>"+translate("vs.usage", "Excluded from ")+"<a href=\""+vc.getWebPath()+"\">"+Utilities.escapeXml(gt(vc.getNameElement()))+"</a></li>\r\n");
              break;
            }
          }
        }
      }
      for (String url : sorted(sdurls)) {
        StructureDefinition sd = context.fetchResourceRaw(StructureDefinition.class, url);
        if (sd != null) {
          for (ElementDefinition ed : sd.getDifferential().getElement()) {
            if (ed.hasBinding() && ed.getBinding().hasValueSet()) {
              if ((ed.getBinding().hasValueSet() && ed.getBinding().getValueSet().equals(vs.getUrl()))) {
                if (first) {
                  first = false;
                  b.append("<ul>\r\n");
                }
                String path = sd.getWebPath();
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
            String path = q.getWebPath();
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
          b.append(" <li>Used as a trigger criteria in <a href=\""+pd.getWebPath()+"\">"+Utilities.escapeXml(pd.present())+"</a></li>\r\n");
        }
      }
    }
    if (first)
      b.append("<p>"+translate("vs.usage", "This value set is not used here; it may be used elsewhere (e.g. specifications and/or implementations that use this content)")+"</p>\r\n");
    else
      b.append("</ul>\r\n");
    return b.toString()+changeSummary();
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


  protected void changeSummaryDetails(StringBuilder b) {
    CanonicalResourceComparison<? extends CanonicalResource> comp = VersionComparisonAnnotation.artifactComparison(vs);
    if (comp != null) {
      if (comp.anyUpdates()) {
        if (comp.getChangedMetadata() == ChangeAnalysisState.CannotEvaluate) {
          b.append("<li>Unable to evaluate changes to metadata</li>\r\n");
        } else if (comp.getChangedMetadata() == ChangeAnalysisState.Changed) {
          b.append("<li>The resource metadata has changed ("+comp.getMetadataFieldsAsText()+")</li>\r\n");          
        }
        
        if (comp.getChangedContent() == ChangeAnalysisState.CannotEvaluate) {
          b.append("<li>Unable to evaluate changes to content</li>\r\n");
        } else if (comp.getChangedContent() == ChangeAnalysisState.Changed) {
          b.append("<li>The logical definition of the value set has changed</li>\r\n");          
        }

        if (comp.getChangedDefinitions() == ChangeAnalysisState.CannotEvaluate) {
          b.append("<li>Unable to evaluate changes to definitions</li>\r\n");
        } else if (comp.getChangedDefinitions() == ChangeAnalysisState.Changed) {
          b.append("<li>One or more text definitions/displays have changed</li>\r\n");          
        }

        if (comp.getChangedContentInterpretation() == ChangeAnalysisState.CannotEvaluate) {
          b.append("<li>Unable to evaluate changes to the expansion</li>\r\n");
        } else if (comp.getChangedContentInterpretation() == ChangeAnalysisState.Changed) {
          b.append("<li>The expansion has changed</li>\r\n");          
        }

      } else {
        b.append("<li>No changes</li>\r\n");
      }
    }
  }
}
