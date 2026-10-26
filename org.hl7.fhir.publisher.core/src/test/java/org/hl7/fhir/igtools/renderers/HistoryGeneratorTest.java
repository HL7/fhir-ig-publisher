package org.hl7.fhir.igtools.renderers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.hl7.fhir.igtools.publisher.ProvenanceDetails;
import org.hl7.fhir.model.core.Coding;
import org.hl7.fhir.model.core.Reference;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.services.renderers.utils.RenderingContext;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.junit.jupiter.api.Test;

class HistoryGeneratorTest {

  private static final String TYPE_SYSTEM = "http://terminology.hl7.org/CodeSystem/provenance-participant-type";
  private static final List<String> TYPES = List.of("enterer", "performer", "author", "verifier", "legal",
      "attester", "informant", "custodian", "assembler", "composer");

  @Test
  void actorColumnsFollowAgentOrder() throws Exception {
    ProvenanceDetails pd = new ProvenanceDetails(new Coding(null, "UPDATE", null), null, null);
    for (String type : TYPES) {
      pd.getActors().put(new Coding(TYPE_SYSTEM, type, null), new Reference().setDisplay("first " + type));
    }
    for (String type : TYPES) {
      pd.getActors().put(new Coding(TYPE_SYSTEM, type, null), new Reference().setDisplay("second " + type));
    }
    List<ProvenanceDetails> entries = new ArrayList<>();
    entries.add(pd);

    XhtmlNode tbl = new HistoryGenerator(context()).internalGenerate(entries, false).getElement("table");

    List<String> headers = cells(tbl.getChildren("tr").get(0));
    assertEquals(TYPES, headers.subList(2, headers.size() - 1));
    List<String> row = cells(tbl.getChildren("tr").get(1));
    assertEquals(TYPES.stream().map(type -> "first " + type).collect(Collectors.toList()),
        row.subList(2, row.size() - 1));
  }

  private List<String> cells(XhtmlNode tr) {
    return tr.getChildren("td").stream().map(XhtmlNode::allText).collect(Collectors.toList());
  }

  private RenderingContext context() {
    RenderingContext context = mock(RenderingContext.class);
    IWorkerContext worker = mock(IWorkerContext.class);
    when(context.getWorker()).thenReturn(worker);
    return context;
  }
}
