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

import org.hl7.fhir.exceptions.DefinitionException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.RelatedIG;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.Questionnaire;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.QuestionnaireRendererMode;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;

public class QuestionnaireRenderer extends CanonicalRenderer {


  private Questionnaire q;
  private String destDir;

  public QuestionnaireRenderer(IWorkerContext context, String corePath, Questionnaire q, String destDir, IGKnowledgeProvider igp, List<SpecMapManager> maps, Set<String> allTargets, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen, String versionToAnnotate, List<RelatedIG> relatedIgs) {
    super(context, corePath, q, destDir, igp, maps, allTargets, markdownEngine, packge, gen, versionToAnnotate, relatedIgs);
    this.q = q;
    this.destDir = destDir;
  }

  @Override
  protected void genSummaryRowsSpecific(StringBuilder b, Set<String> rows) {
  }

  public String render(QuestionnaireRendererMode mode) throws IOException, FHIRFormatError, DefinitionException, FHIRException, EOperationOutcome {
    org.hl7.fhir.r5.renderers.QuestionnaireRenderer qr = new org.hl7.fhir.r5.renderers.QuestionnaireRenderer(gen);
    gen.setQuestionnaireMode(mode);
    return new XhtmlComposer(XhtmlComposer.HTML).compose(qr.buildNarrative(ResourceWrapper.forResource(gen.getContextUtilities(), q)));
  }

}
