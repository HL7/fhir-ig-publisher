package org.hl7.fhir.igtools.renderers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.model.core.Enumerations.PublicationStatus;
import org.hl7.fhir.model.core.Narrative.NarrativeStatus;
import org.hl7.fhir.model.core.PackageInformation;
import org.hl7.fhir.model.core.Resource;
import org.hl7.fhir.model.core.ValueSet;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;
import org.junit.jupiter.api.Test;

class DraftDependenciesRendererTest {

  private static final String DRAFT = "http://example.org/other/CodeSystem/draft";

  private IWorkerContext contextWithDraftCodeSystem() {
    ValueSet target = new ValueSet();
    target.setUrl(DRAFT);
    target.setName("DraftThing");
    target.setStatus(PublicationStatus.DRAFT);
    target.setSourcePackage(new PackageInformation("other.pkg", "1.0.0", "5.0.0", null));
    IWorkerContext context = mock(IWorkerContext.class);
    when(context.fetchResource(eq(Resource.class), eq(DRAFT), any())).thenReturn(target);
    return context;
  }

  private FetchedResource valueSetUsingDraft(XhtmlNode div) {
    ValueSet vs = new ValueSet();
    vs.setUrl("http://example.org/this/ValueSet/vs");
    vs.getText().setStatus(NarrativeStatus.GENERATED).setDiv(div);
    vs.getCompose().addInclude().setSystem(DRAFT);
    vs.getCompose().addInclude().setSystem(DRAFT);
    FetchedResource r = new FetchedResource("ValueSet/vs");
    r.setResource(vs);
    return r;
  }

  @Test
  void findsDraftDependenciesAndLeavesTheNarrativeAlone() throws Exception {
    // composing a narrative as html puts line breaks between its blocks, in the narrative itself
    XhtmlNode div = new XhtmlParser().parseFragment("<div xmlns=\"http://www.w3.org/1999/xhtml\"><div>see http://example.org/x</div><div>two</div></div>");
    DraftDependenciesRenderer dr = new DraftDependenciesRenderer(contextWithDraftCodeSystem(), "this.pkg#0.1.0");

    dr.checkResource(valueSetUsingDraft(div));

    assertEquals("<ul><li>other.pkg#1.0.0:<code>DraftThing</code>(2uses)</li></ul>", dr.render().replaceAll("\\s", ""));
    assertEquals(2, div.getChildNodes().size());
  }
}
