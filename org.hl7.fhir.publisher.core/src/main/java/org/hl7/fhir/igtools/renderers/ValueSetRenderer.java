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
import org.hl7.fhir.igtools.publisher.RelatedIG;
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
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.terminologies.ValueSetUtilities;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.i18n.RenderingI18nContext;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class ValueSetRenderer extends CanonicalRenderer {

  private ValueSet vs;
  private static boolean nsFailHasFailed;

  public ValueSetRenderer(IWorkerContext context, String corePath, ValueSet vs, IGKnowledgeProvider igp, List<SpecMapManager> maps, Set<String> allTargets, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen, String versionToAnnotate, List<RelatedIG> relatedIgs) {
    super(context, corePath, vs, null, igp, maps, allTargets, markdownEngine, packge, gen, versionToAnnotate, relatedIgs);
    this.vs = vs; 
  }

  @Override
  protected void genSummaryRowsSpecific(StringBuilder b, Set<String> rows) {
    if (hasSummaryRow(rows, "oid")) {
      if (ValueSetUtilities.hasOID(vs)) {
        b.append(" <tr><td>"+ (gen.formatPhrase(RenderingContext.GENERAL_OID))+":</td><td>"+ValueSetUtilities.getOID(vs)+" ("+ gen.formatPhrase(RenderingContext.CODE_SYS_FOR_OID)+")</td></tr>\r\n");
      }
    }
  }

  public String cld(Set<String> outputTracker) throws EOperationOutcome, FHIRException, IOException, org.hl7.fhir.exceptions.FHIRException  {
    if (vs.hasText() && !vs.getText().hasUserData(UserDataNames.renderer_is_generated) && vs.getText().hasDiv()) {
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
      RendererFactory.factory(vsc, gen).renderOrError(ResourceWrapper.forResource(gen, vsc));
      return "<h3>"+gen.formatPhrase(RenderingContext.VSR_LOGICAL)+"</h3>\r\n" + new XhtmlComposer(XhtmlComposer.HTML).compose(vsc.getText().getDiv());
    } else {
      return "<h3>"+gen.formatPhrase(RenderingContext.VSR_LOGICAL)+"</h3>\r\n<p>"+gen.formatPhrase(RenderingI18nContext.VSR_NO_DEF)+"</p>\r\n";
      
    }
  }

  public String xref() throws FHIRException, IOException {
    try {
    StringBuilder b = new StringBuilder();
    boolean first = true;
    b.append("\r\n");
    if (vs.hasUrl()) {
      for (CanonicalResource vs : scanAllLocalResources(ValueSet.class, "ValueSet")) {
        first = checkReferencesVS(b, first, (ValueSet) vs);
      }

      for (CanonicalResource cr : scanAllResources(StructureDefinition.class, "StructureDefinition")) {
        first = checkReferencesVS(b, first, (StructureDefinition) cr);
      }

      for (CanonicalResource cr : scanAllResources(Questionnaire.class, "Questionnaire")) {
        first = checkReferencesVS(b, first, (Questionnaire) cr);
      }

      for (CanonicalResource cr : scanAllResources(PlanDefinition.class, "PlanDefinition")) {
        PlanDefinition pd = (PlanDefinition) cr;
        if (referencesValueSet(pd)) {
          if (first) {
            first = false;
            b.append("<ul>\r\n");
          }
          b.append(" <li>"+gen.formatPhrase(RenderingI18nContext.VSR_TRIGGER)+" <a href=\""+pd.getWebPath()+"\">"+Utilities.escapeXml(pd.present())+"</a></li>\r\n");
        }
      }
    }
    if (first)
      b.append("<p>"+ (gen.formatPhrase(RenderingContext.VALUE_SET_USED_ELSEWHERE))+"</p>\r\n");
    else
      b.append("</ul>\r\n");
    return b.toString()+changeSummary();
    } catch (Exception e) {
      if (!nsFailHasFailed) {
        e.printStackTrace();
        nsFailHasFailed  = true;
      }
      return " <p>"+Utilities.escapeXml(e.getMessage())+"</p>\r\n"+getProvenanceReferences(vs);
    }
  }

  public boolean checkReferencesVS(StringBuilder b, boolean first, Questionnaire q) {
    if (q != null) {
      if (questionnaireUsesValueSet(q.getItem(), vs.getUrl(), vs.getVersionedUrl())) {
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
      }
    }
    return first;
  }

  public boolean checkReferencesVS(StringBuilder b, boolean first, ValueSet vc) {
    for (ConceptSetComponent t : vc.getCompose().getInclude()) {
      for (UriType ed : t.getValueSet()) {
        if (Utilities.existsInList(ed.getValueAsString(), vs.getUrl(), vs.getVersionedUrl())) {
          if (first) {
            first = false;
            b.append("<ul>\r\n");
          }
          b.append(" <li>"+ (gen.formatPhrase(RenderingContext.VALUE_SET_INCLUDED_INTO)+" ")+"<a href=\""+vc.getWebPath()+"\">"+Utilities.escapeXml(gen.getTranslated(vc.getNameElement()))+"</a></li>\r\n");
          break;
        }
      }
    }
    for (ConceptSetComponent t : vc.getCompose().getExclude()) {
      for (UriType ed : t.getValueSet()) {
        if (Utilities.existsInList(ed.getValueAsString(), vs.getUrl(), vs.getVersionedUrl())) {
          if (first) {
            first = false;
            b.append("<ul>\r\n");
          }
          b.append(" <li>"+ (gen.formatPhrase(RenderingContext.VALUE_SET_EXCLUDED_FROM)+" ")+"<a href=\""+vc.getWebPath()+"\">"+Utilities.escapeXml(gen.getTranslated(vc.getNameElement()))+"</a></li>\r\n");
          break;
        }
      }
    }
    return first;
  }

  public boolean checkReferencesVS(StringBuilder b, boolean first, StructureDefinition sd) {
    if (sd != null) {
      for (ElementDefinition ed : sd.getDifferential().getElement()) {
        if (ed.hasBinding() && ed.getBinding().hasValueSet()) {
          if ((ed.getBinding().hasValueSet() && Utilities.existsInList(ed.getBinding().getValueSet(), vs.getUrl(), vs.getVersionedUrl()))) {
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
    return first;
  }

  private boolean questionnaireUsesValueSet(List<QuestionnaireItemComponent> items, String url, String url2) {
    for (QuestionnaireItemComponent i : items) {
      if (i.hasAnswerValueSet() && Utilities.existsInList(i.getAnswerValueSet(), url, url2))
        return true;
      if (questionnaireUsesValueSet(i.getItem(), url, url2))
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
            if (Utilities.existsInList(ed.getValueSet(), vs.getUrl(), vs.getVersionedUrl()))
              return true;
      }
    }
    return false;
  }


  protected void changeSummaryDetails(StringBuilder b) {
    CanonicalResourceComparison<? extends CanonicalResource> comp = VersionComparisonAnnotation.artifactComparison(vs);
    changeSummaryDetails(b, comp, RenderingI18nContext.SDR_CONT_CH_DET_VS, RenderingI18nContext.SDR_DEFN_CH_DET_VS, RenderingI18nContext.SDR_INTRP_CH_NO_VS, RenderingI18nContext.SDR_INTRP_CH_DET_VS);
  }
}
