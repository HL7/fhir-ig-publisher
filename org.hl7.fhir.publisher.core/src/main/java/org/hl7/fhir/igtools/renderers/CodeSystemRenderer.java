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
import org.hl7.fhir.r5.comparison.VersionComparisonAnnotation;
import org.hl7.fhir.r5.comparison.CanonicalResourceComparer.CanonicalResourceComparison;
import org.hl7.fhir.r5.comparison.CanonicalResourceComparer.ChangeAnalysisState;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.Enumerations.CodeSystemContentMode;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.renderers.RendererFactory;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;

public class CodeSystemRenderer extends CanonicalRenderer {

  private CodeSystem cs;

  public CodeSystemRenderer(IWorkerContext context, String corePath, CodeSystem cs, IGKnowledgeProvider igp, List<SpecMapManager> maps, Set<String> allTargets, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen, String versionToAnnotate) {
    super(context, corePath, cs, null, igp, maps, allTargets, markdownEngine, packge, gen, versionToAnnotate);
    this.cs = cs;
  }

  @Override
  protected void genSummaryRowsSpecific(StringBuilder b, Set<String> rows) {
    if (hasSummaryRow(rows, "content")) {
      if (cs.hasContent()) {
        b.append(" <tr><td>"+translate("cs.summary", "Content")+":</td><td>"+translate("cs.summary", cs.getContent().getDisplay())+": "+describeContent(cs.getContent())+"</td></tr>\r\n");
      }
    }
    if (hasSummaryRow(rows, "oid")) {
      if (CodeSystemUtilities.hasOID(cs)) {
        b.append(" <tr><td>"+translate("cs.summary", "OID")+":</td><td>"+CodeSystemUtilities.getOID(cs)+" ("+translate("cs.summary", "for OID based terminology systems")+")</td></tr>\r\n");
      }
    }
    if (hasSummaryRow(rows, "cs.vs")) {
      if (cs.hasValueSet()) {
        ValueSet vs = context.fetchResource(ValueSet.class, cs.getValueSet());
        if (vs == null) {
          b.append(" <tr><td>"+translate("cs.summary", "Value Set")+":</td><td>"+ cs.getValueSet()+" ("+translate("cs.summary", " is the value set for all codes in this code system")+")</td></tr>\r\n");
        } else {
          b.append(" <tr><td>"+translate("cs.summary", "Value Set")+":</td><td><a href=\""+vs.getWebPath()+"\">"+ cs.getValueSet()+"</a> ("+translate("cs.summary", " is the value set for all codes in this code system")+")</td></tr>\r\n");        
        }
      }
    }
  }

  private String describeContent(CodeSystemContentMode content) {
    switch (content) {
    case COMPLETE: return translate("cs.summary", "All the concepts defined by the code system are included in the code system resource");
    case NOTPRESENT: return translate("cs.summary", "None of the concepts defined by the code system are included in the code system resource");
    case EXAMPLE: return translate("cs.summary", "A few representative concepts are included in the code system resource");
    case FRAGMENT: return translate("cs.summary", "A subset of the code system concepts are included in the code system resource");
    case SUPPLEMENT: return translate("cs.summary", "This code system resource is a supplement to ")+refCS(cs.getSupplements());
    default:
      return "?? illegal content status value "+(content == null ? "(null)" : content.toCode());
    }
  }

  private String refCS(String supplements) {
    CodeSystem tgt = context.fetchCodeSystem(supplements);
    if (tgt != null) {
      return "<a href=\""+tgt.getWebPath()+"\"><code>"+supplements+"</code></a>";
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
    for (ValueSet vs : context.fetchResourcesByType(ValueSet.class)) {
        vsurls.add(vs.getUrl());
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
      b.append("<ul><li>"+translate("cs.xref", "This CodeSystem is not used here; it may be used elsewhere (e.g. specifications and/or implementations that use this content)")+"</li></ul>\r\n");
    else
      b.append("</ul>\r\n");    
    return b.toString()+changeSummary();
  }


  private boolean addLink(StringBuilder b, boolean first, ValueSet vc, ConceptSetComponent ed, Set<String> processed) {
    if (ed.hasSystem() && ed.getSystem().equals(cs.getUrl())) {
      String path = vc.getWebPath();
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


  protected void changeSummaryDetails(StringBuilder b) {
    CanonicalResourceComparison<? extends CanonicalResource> comp = VersionComparisonAnnotation.artifactComparison(cs);
    if (comp != null && comp.anyUpdates()) {
      if (comp.getChangedMetadata() == ChangeAnalysisState.CannotEvaluate) {
        b.append("<li>Unable to evaluate changes to metadata</li>\r\n");
      } else if (comp.getChangedMetadata() == ChangeAnalysisState.Changed) {
        b.append("<li>The resource metadata has changed ("+comp.getMetadataFieldsAsText()+")</li>\r\n");          
      }
      
      if (comp.getChangedContent() == ChangeAnalysisState.CannotEvaluate) {
        b.append("<li>Unable to evaluate changes to content</li>\r\n");
      } else if (comp.getChangedContent() == ChangeAnalysisState.Changed) {
        b.append("<li>The list of codes defined by the code system has changed</li>\r\n");          
      }

      if (comp.getChangedDefinitions() == ChangeAnalysisState.CannotEvaluate) {
        b.append("<li>Unable to evaluate changes to definitions</li>\r\n");
      } else if (comp.getChangedDefinitions() == ChangeAnalysisState.Changed) {
        b.append("<li>One or more text definitions/displays have changed</li>\r\n");          
      }

    } else {
      b.append("<li>No changes</li>\r\n");
    }
  }
  
}
