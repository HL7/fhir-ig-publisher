package org.hl7.fhir.igtools.renderers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.hl7.fhir.igtools.publisher.ProvenanceDetails;
import org.hl7.fhir.model.core.CodeSystem;
import org.hl7.fhir.model.core.Coding;
import org.hl7.fhir.model.core.DateTimeType;
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
  void actorColumnsFollowCodeSystemOrder() throws Exception {
    ProvenanceDetails older = provenance("2024-01-01");
    actor(older, TYPE_SYSTEM, "author", "a");
    actor(older, "http://example.org/b", "x", "b");
    actor(older, TYPE_SYSTEM, "enterer", "c");
    ProvenanceDetails newer = provenance("2025-01-01");
    actor(newer, TYPE_SYSTEM, "unknown", "d");
    actor(newer, TYPE_SYSTEM, "verifier", "e");
    actor(newer, "http://example.org/a", "y", "f");
    actor(newer, TYPE_SYSTEM, "performer", "g");
    List<ProvenanceDetails> entries = new ArrayList<>(List.of(older, newer));

    XhtmlNode tbl = new HistoryGenerator(context()).internalGenerate(entries, false).getElement("table");

    List<String> headers = cells(tbl.getChildren("tr").get(0));
    assertEquals(List.of("enterer", "performer", "author", "verifier", "y", "x", "unknown"),
        headers.subList(2, headers.size() - 1));
  }

  @Test
  void firstAgentOfTypeIsShown() throws Exception {
    ProvenanceDetails pd = provenance("2025-01-01");
    for (String type : TYPES) {
      actor(pd, TYPE_SYSTEM, type, "first " + type);
    }
    for (String type : TYPES) {
      actor(pd, TYPE_SYSTEM, type, "second " + type);
    }
    List<ProvenanceDetails> entries = new ArrayList<>(List.of(pd));

    XhtmlNode tbl = new HistoryGenerator(context()).internalGenerate(entries, false).getElement("table");

    List<String> headers = cells(tbl.getChildren("tr").get(0));
    assertEquals(TYPES, headers.subList(2, headers.size() - 1));
    List<String> row = cells(tbl.getChildren("tr").get(1));
    assertEquals(TYPES.stream().map(type -> "first " + type).collect(Collectors.toList()),
        row.subList(2, row.size() - 1));
  }

  private ProvenanceDetails provenance(String date) {
    return new ProvenanceDetails(new Coding(null, "UPDATE", null), new DateTimeType(date), null);
  }

  private void actor(ProvenanceDetails pd, String system, String code, String display) {
    pd.getActors().put(new Coding(system, code, null), new Reference().setDisplay(display));
  }

  private List<String> cells(XhtmlNode tr) {
    return tr.getChildren("td").stream().map(XhtmlNode::allText).collect(Collectors.toList());
  }

  private RenderingContext context() {
    CodeSystem cs = new CodeSystem();
    cs.setUrl(TYPE_SYSTEM);
    cs.addConcept().setCode("enterer");
    CodeSystem.ConceptDefinitionComponent performer = cs.addConcept().setCode("performer");
    performer.addConcept().setCode("author");
    for (String type : TYPES.subList(3, TYPES.size())) {
      cs.addConcept().setCode(type);
    }
    RenderingContext context = mock(RenderingContext.class);
    IWorkerContext worker = mock(IWorkerContext.class);
    when(context.getWorker()).thenReturn(worker);
    when(context.getLocale()).thenReturn(Locale.ENGLISH);
    when(worker.fetchCodeSystem(eq(TYPE_SYSTEM), any())).thenReturn(cs);
    return context;
  }
}
