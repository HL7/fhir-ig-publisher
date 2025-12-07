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
import org.hl7.fhir.igtools.publisher.*;
import org.hl7.fhir.r5.comparison.CanonicalResourceComparer.CanonicalResourceComparison;
import org.hl7.fhir.r5.comparison.VersionComparisonAnnotation;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.model.Enumerations.CodeSystemContentMode;
import org.hl7.fhir.r5.model.NamingSystem.NamingSystemUniqueIdComponent;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.renderers.NamingSystemRenderer;
import org.hl7.fhir.r5.renderers.RendererFactory;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.graphql.Value;
import org.hl7.fhir.utilities.i18n.RenderingI18nContext;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class CodeSystemRenderer extends CanonicalRenderer {

  private CodeSystem cs;

  public CodeSystemRenderer(IWorkerContext context, String corePath, CodeSystem cs, IGKnowledgeProvider igp, List<SpecMapManager> maps, Set<String> allTargets, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen, String versionToAnnotate, List<RelatedIG> relatedIgs) {
    super(context, corePath, cs, null, igp, maps, allTargets, markdownEngine, packge, gen, versionToAnnotate, relatedIgs);
    this.cs = cs;
  }

  @Override
  protected void genSummaryRowsSpecific(StringBuilder b, Set<String> rows) {
    if (hasSummaryRow(rows, "content")) {
      if (cs.hasContent()) {
        b.append(" <tr><td>" + (gen.formatPhrase(RenderingContext.GENERAL_CONTENT)) + ":</td><td>" + (cs.getContent().getDisplay()) + ": " + describeContent(cs.getContent()) + "</td></tr>\r\n");
      }
    }
    if (hasSummaryRow(rows, "oid")) {
      if (CodeSystemUtilities.hasOID(cs)) {
        b.append(" <tr><td>" + (gen.formatPhrase(RenderingContext.GENERAL_OID)) + ":</td><td>" + CodeSystemUtilities.getOID(cs) + " (" + (gen.formatPhrase(RenderingContext.CODE_SYS_FOR_OID)) + ")</td></tr>\r\n");
      }
    }
    if (hasSummaryRow(rows, "cs.vs")) {
      if (cs.hasValueSet()) {
        ValueSet vs = context.findTxResource(ValueSet.class, cs.getValueSet());
        if (vs == null) {
          b.append(" <tr><td>" + (gen.formatPhrase(RenderingContext.GENERAL_VALUESET)) + ":</td><td>" + cs.getValueSet() + " (" + (" " + gen.formatPhrase(RenderingContext.CODE_SYS_THE_VALUE_SET)) + ")</td></tr>\r\n");
        } else {
          b.append(" <tr><td>" + (gen.formatPhrase(RenderingContext.GENERAL_VALUESET)) + ":</td><td><a href=\"" + vs.getWebPath() + "\">" + cs.getValueSet() + "</a> (" + (" " + gen.formatPhrase(RenderingContext.CODE_SYS_THE_VALUE_SET)) + ")</td></tr>\r\n");
        }
      }
    }
  }

  private String describeContent(CodeSystemContentMode content) {
    switch (content) {
      case COMPLETE:
        return (gen.formatPhrase(RenderingContext.CODE_SYS_COMPLETE));
      case NOTPRESENT:
        return (gen.formatPhrase(RenderingContext.CODE_SYS_NOTPRESENT));
      case EXAMPLE:
        return (gen.formatPhrase(RenderingContext.CODE_SYS_EXAMPLE));
      case FRAGMENT:
        return (gen.formatPhrase(RenderingContext.CODE_SYS_FRAGMENT));
      case SUPPLEMENT:
        return (gen.formatPhrase(RenderingContext.CODE_SYS_SUPPLEMENT) + " ") + refCS(cs.getSupplements());
      default:
        return "?? illegal content status value " + (content == null ? "(null)" : content.toCode());
    }
  }

  private String refCS(String supplements) {
    CodeSystem tgt = context.fetchCodeSystem(supplements);
    if (tgt != null) {
      return "<a href=\"" + tgt.getWebPath() + "\"><code>" + supplements + "</code></a>";
    } else {
      return "<code>" + supplements + "</code>";
    }
  }

  public String content(Set<String> outputTracker) throws EOperationOutcome, FHIRException, IOException, org.hl7.fhir.exceptions.FHIRException {
    CodeSystem csc = cs.copy();

    csc.setId(cs.getId()); // because that's not copied
    csc.setText(null);
    RendererFactory.factory(csc, gen).renderResource(ResourceWrapper.forResource(gen.getContextUtilities(), csc));

    return new XhtmlComposer(XhtmlComposer.HTML).compose(csc.getText().getDiv());
    //    }
  }

  public String xref() throws FHIRException, IOException {
    StringBuilder b = new StringBuilder();
    boolean first = true;
    b.append("\r\n");

    Set<String> processed = new HashSet<String>();
    for (CanonicalResource cr : scanAllLocalResources(ValueSet.class, "ValueSet")) {
      ValueSet vs = (ValueSet) cr;
      if (cs.getContent() != CodeSystemContentMode.SUPPLEMENT) {
        for (ConceptSetComponent ed : vs.getCompose().getInclude()) {
          first = addLink(b, first, vs, ed, processed);
        }
        for (ConceptSetComponent ed : vs.getCompose().getExclude()) {
          first = addLink(b, first, vs, ed, processed);
        }
      } else {
        for (Extension ex : vs.getExtensionsByUrl(ExtensionDefinitions.EXT_VS_CS_SUPPL_NEEDED)) {
          first = addLink(b, first, vs, ex, processed);
        }
      }
    }
    if (first) {
      if (cs.getContent() == CodeSystemContentMode.SUPPLEMENT) {
        b.append("<ul><li>" + (gen.formatPhrase(RenderingContext.CODE_SYS_SUPP_CODE_NOT_HERE)) + "</li></ul>\r\n");
      } else {
        b.append("<ul><li>" + (gen.formatPhrase(RenderingContext.CODE_SYS_CODE_NOT_HERE)) + "</li></ul>\r\n");
      }
    } else {
      b.append("</ul>\r\n");
    }
    String pr = getProvenanceReferences(cs);
    return b.toString() +pr+ changeSummary();
  }

  public String nsInfo() throws FHIRException {
    List<NamingSystem> nsl = new ArrayList<>();
    for (NamingSystem t : context.fetchResourcesByType(NamingSystem.class)) {
      if (isNSforCS(t)) {
        nsl.add(t);
      }
    }
    if (nsl.isEmpty()) {
      return "";
    } else {
      NamingSystemRenderer nsr = new NamingSystemRenderer(gen);
      XhtmlNode x = new XhtmlNode(NodeType.Element, "div");
      nsr.renderList(x, nsl);
    }

    StringBuilder b = new StringBuilder();
    boolean first = true;
    b.append("\r\n");
    List<String> vsurls = new ArrayList<String>();
    for (CanonicalResource vs : scanAllLocalResources(ValueSet.class, "ValueSet")) {
      vsurls.add(vs.getUrl());
    }
    Collections.sort(vsurls);

    Set<String> processed = new HashSet<String>();
    for (String url : vsurls) {
      ValueSet vc = context.findTxResource(ValueSet.class, url);
      if (cs.getContent() != CodeSystemContentMode.SUPPLEMENT) {
        for (ConceptSetComponent ed : vc.getCompose().getInclude()) {
          first = addLink(b, first, vc, ed, processed);
        }
        for (ConceptSetComponent ed : vc.getCompose().getExclude()) {
          first = addLink(b, first, vc, ed, processed);
        }
      } else {
        for (Extension ex : vc.getExtensionsByUrl(ExtensionDefinitions.EXT_VS_CS_SUPPL_NEEDED)) {
          first = addLink(b, first, vc, ex, processed);
        }
      }
    }
    if (first) {
      if (cs.getContent() == CodeSystemContentMode.SUPPLEMENT) {
        b.append("<ul><li>" + (gen.formatPhrase(RenderingContext.CODE_SYS_SUPP_CODE_NOT_HERE)) + "</li></ul>\r\n");
      } else {
        b.append("<ul><li>" + (gen.formatPhrase(RenderingContext.CODE_SYS_CODE_NOT_HERE)) + "</li></ul>\r\n");
      }
    } else {
      b.append("</ul>\r\n");
    }
    return b.toString() + changeSummary();
  }

  private boolean isNSforCS(NamingSystem t) {
    for (NamingSystemUniqueIdComponent ui : t.getUniqueId()) {
      if (ui.hasValue() && ui.getValue().equals(cs.getUrl())) {
        return true;
      }
    }
    return false;
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
          System.out.println("No path for " + vc.getUrl());
        } else {
          b.append(" <li><a href=\"" + path + "\">" + Utilities.escapeXml(gen.getTranslated(vc.getNameElement())) + "</a></li>\r\n");
        }
        processed.add(path);
      }
    }
    return first;
  }


  private boolean addLink(StringBuilder b, boolean first, ValueSet vc, Extension ex, Set<String> processed) {
    if (ex.hasValue() && Utilities.existsInList(ex.getValue().primitiveValue(), cs.getUrl(), cs.getVersionedUrl())) {
      String path = vc.getWebPath();
      if (!processed.contains(path)) {
        if (first) {
          first = false;
          b.append("<ul>\r\n");
        }
        if (path == null) {
          System.out.println("No path for " + vc.getUrl());
        } else {
          b.append(" <li><a href=\"" + path + "\">" + Utilities.escapeXml(gen.getTranslated(vc.getNameElement())) + "</a></li>\r\n");
        }
        processed.add(path);
      }
    }
    return first;
  }


  protected void changeSummaryDetails(StringBuilder b) {
    CanonicalResourceComparison<? extends CanonicalResource> comp = VersionComparisonAnnotation.artifactComparison(cs);
    changeSummaryDetails(b, comp, RenderingI18nContext.SDR_CONT_CH_DET_CS, RenderingI18nContext.SDR_DEFN_CH_DET_CS, null, null);
  }

}
