package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.HTMLInspector.LoadedFile;
import org.hl7.fhir.model.core.ActorDefinition;
import org.hl7.fhir.model.core.Coding;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.validation.BaseValidator.BooleanHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConformanceStatementHandlerTest {

  @ParameterizedTest
  @MethodSource("metadata")
  void extractsMetadataFromPlainTextClause(String metadata) throws Exception {
    XhtmlNode paragraph = paragraph("\u00a7" + metadata + ":Implementations SHALL respect units.\u00a7");

    assertTrue(process(paragraph));

    List<XhtmlNode> clauses = clauses(paragraph);
    assertEquals(1, clauses.size());
    assertEquals(metadata, clauses.get(0).getAttribute("id"));
    assertEquals("Implementations SHALL respect units.", clauses.get(0).allText());
    assertEquals("Implementations SHALL respect units.", paragraph.allText());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "Implementations SHALL respect units.",
      "Implementations SHALL support: equality and comparison.",
      "not a token: Implementations SHALL respect units."
  })
  void preservesAnonymousClauseText(String text) throws Exception {
    XhtmlNode paragraph = paragraph("\u00a7" + text + "\u00a7");

    assertTrue(process(paragraph));

    List<XhtmlNode> clauses = clauses(paragraph);
    assertEquals(1, clauses.size());
    assertNull(clauses.get(0).getAttribute("id"));
    assertEquals(text, paragraph.allText());
  }

  @Test
  void preservesTextBeforeAndAfterClause() throws Exception {
    XhtmlNode paragraph = paragraph("Before. \u00a7req1:Implementations SHALL respect units.\u00a7 After.");

    assertTrue(process(paragraph));

    List<XhtmlNode> clauses = clauses(paragraph);
    assertEquals(1, clauses.size());
    assertEquals("req1", clauses.get(0).getAttribute("id"));
    assertEquals("Before. Implementations SHALL respect units. After.", paragraph.allText());
    assertEquals(NodeType.Text, paragraph.getChildNodes().get(2).getNodeType());
    assertEquals(" After.", paragraph.getChildNodes().get(2).getContent());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\n"})
  void preservesConsecutiveClauses(String separator) throws Exception {
    XhtmlNode paragraph = paragraph(
        "\u00a7rptAll1:Implementations SHOULD include safeguards.\u00a7" + separator
        + "\u00a7rptAll2:Evaluation MAY signal an error.\u00a7");

    assertTrue(process(paragraph));

    List<XhtmlNode> clauses = clauses(paragraph);
    assertEquals(2, clauses.size());
    assertNotSame(clauses.get(0), clauses.get(1));
    assertEquals("rptAll1", clauses.get(0).getAttribute("id"));
    assertEquals("rptAll2", clauses.get(1).getAttribute("id"));
    assertEquals("Implementations SHOULD include safeguards.", clauses.get(0).allText());
    assertEquals("Evaluation MAY signal an error.", clauses.get(1).allText());
    assertEquals("Implementations SHOULD include safeguards." + separator
        + "Evaluation MAY signal an error.", paragraph.allText());
  }

  @ParameterizedTest
  @MethodSource("metadata")
  void preservesFormattedClause(String metadata) throws Exception {
    XhtmlNode paragraph = paragraph("Before. \u00a7" + metadata + ":Implementations ");
    XhtmlNode emphasis = paragraph.addTag("strong");
    emphasis.tx("SHALL");
    paragraph.tx(" respect units.\u00a7 After.");

    assertTrue(process(paragraph));

    List<XhtmlNode> clauses = clauses(paragraph);
    assertEquals(1, clauses.size());
    assertEquals(metadata, clauses.get(0).getAttribute("id"));
    assertSame(emphasis, clauses.get(0).getChildNodes().get(1));
    assertEquals("Before. Implementations SHALL respect units. After.", paragraph.allText());
  }

  @Test
  void preservesFormattedClauseFollowingPlainTextClause() throws Exception {
    XhtmlNode paragraph = paragraph(
        "\u00a7req1:Implementations SHALL respect units.\u00a7 Then \u00a7req2:Engines SHOULD support ");
    XhtmlNode code = paragraph.addTag("code");
    code.tx("trace()");
    paragraph.tx(".\u00a7 After.");

    assertTrue(process(paragraph));

    List<XhtmlNode> clauses = clauses(paragraph);
    assertEquals(2, clauses.size());
    assertEquals("req1", clauses.get(0).getAttribute("id"));
    assertEquals("req2", clauses.get(1).getAttribute("id"));
    assertSame(code, clauses.get(1).getChildNodes().get(1));
    assertEquals("Implementations SHALL respect units. Then Engines SHOULD support trace(). After.",
        paragraph.allText());
  }

  @ParameterizedTest
  @ValueSource(strings = {"Implementations SHALL respect units.", "\u00a7req1:An unclosed marker."})
  void leavesUnmarkedOrUnclosedTextUnchanged(String text) throws Exception {
    XhtmlNode paragraph = paragraph(text);

    assertFalse(process(paragraph));

    assertTrue(clauses(paragraph).isEmpty());
    assertEquals(text, paragraph.allText());
  }

  @Test
  void replacesDocumentationPlaceholder() throws Exception {
    XhtmlNode paragraph = paragraph("!\u00a7\u00a7");

    assertTrue(process(paragraph));

    assertEquals("\u00a7\u00a7", paragraph.allText());
  }

  @Test
  void replacesDocumentationPlaceholderInTextNode() throws Exception {
    XhtmlNode text = new XhtmlNode(NodeType.Text);
    text.setContent("!\u00a7\u00a7");

    assertTrue(process(text));

    assertEquals("\u00a7\u00a7", text.getContent());
  }

  @Test
  void leavesPlaceholderInCommentUnchanged() throws Exception {
    XhtmlNode paragraph = paragraph("Text.");
    XhtmlNode comment = paragraph.addComment("!\u00a7\u00a7");

    assertFalse(process(paragraph));

    assertEquals("!\u00a7\u00a7", comment.getContent());
    assertEquals("Text.", paragraph.allText());
  }

  @ParameterizedTest
  @CsvSource(value = {
      "req | false | 0 | 0",
      "req? | true | 0 | 0",
      "req^actor1 | false | 1 | 0",
      "req^actor1,actor2 | false | 2 | 0",
      "req^^category1 | false | 0 | 1",
      "req^^category1,category2 | false | 0 | 2",
      "req^actor1^category1 | false | 1 | 1",
      "req?^actor1,actor2^category1,category2 | true | 2 | 2"
  }, delimiter = '|')
  void resolvesActorsAndCategories(String metadata, boolean conditional, int actorCount, int categoryCount) {
    ConformanceStatementHandler handler = mock(ConformanceStatementHandler.class);
    List<ActorDefinition> actors = List.of(new ActorDefinition(), new ActorDefinition());
    List<Coding> categories = List.of(
        new Coding("http://example.org/categories", "category1", "Category 1"),
        new Coding("http://example.org/categories", "category2", "Category 2"));
    when(handler.findActor("actor1")).thenReturn(actors.get(0));
    when(handler.findActor("actor2")).thenReturn(actors.get(1));
    when(handler.findCategory("category1")).thenReturn(categories.get(0));
    when(handler.findCategory("category2")).thenReturn(categories.get(1));

    ConformanceClause clause = clause(metadata, handler);

    assertEquals("req", clause.getId());
    assertEquals(conditional, clause.isConditional());
    assertEquals(actors.subList(0, actorCount), clause.getActors());
    assertEquals(categories.subList(0, categoryCount), clause.getCategories());
  }

  @ParameterizedTest
  @CsvSource(value = {
      "req^unknown | Unable to find actor with id unknown",
      "req^^unknown | Unable to find matching code in category value set for unknown",
      "req^actor1,actor1 | Conformance statement has two references to the same actor id actor1",
      "req^^category1,category1 | Conformance statement has two references to the same category category1",
      "req^category1 | Unable to find actor with id category1"
  }, delimiter = '|')
  void rejectsInvalidActorAndCategoryReferences(String metadata, String message) {
    ConformanceStatementHandler handler = mock(ConformanceStatementHandler.class);
    when(handler.findActor("actor1")).thenReturn(new ActorDefinition());
    when(handler.findCategory("category1"))
        .thenReturn(new Coding("http://example.org/categories", "category1", "Category 1"));

    FHIRException exception = assertThrows(FHIRException.class, () -> clause(metadata, handler));

    assertEquals(message + " - " + metadata, exception.getMessage());
  }

  private static Stream<String> metadata() {
    return Stream.of(
        "qtyEq1",
        "req.part-1-a",
        "req?",
        "req^actor1",
        "req^actor1,actor2",
        "req^^category1",
        "req^^category1,category2",
        "req^actor1^category1",
        "req?^actor1,actor2^category1,category2");
  }

  private ConformanceClause clause(String metadata, ConformanceStatementHandler handler) {
    doAnswer(invocation -> {
      invocation.<List<String>>getArgument(0).add("SHALL");
      return null;
    }).when(handler).extractExpectations(anyList(), anyString());
    LoadedFile source = mock(LoadedFile.class);
    source.path = "page.html";
    XhtmlNode paragraph = paragraph("\u00a7" + metadata + ":Implementations SHALL respect units.\u00a7");
    assertTrue(process(paragraph));
    XhtmlNode span = clauses(paragraph).get(0);
    return new ConformanceClause(handler, null, span, source, span.getAttribute("id"), "en");
  }

  private XhtmlNode paragraph(String text) {
    XhtmlNode paragraph = new XhtmlNode(NodeType.Element, "p");
    paragraph.tx(text);
    return paragraph;
  }

  private List<XhtmlNode> clauses(XhtmlNode paragraph) {
    return paragraph.getChildNodes().stream()
        .filter(node -> node.getNodeType() == NodeType.Element && node.hasClass("fhir-conformance"))
        .collect(Collectors.toList());
  }

  private boolean process(XhtmlNode node) {
    ConformanceStatementHandler handler =
        new ConformanceStatementHandler(null, null, null, List.of("en"), false, null);
    Set<XhtmlNode> noClauseMarkers = Collections.newSetFromMap(new IdentityHashMap<>());
    handler.findNodesWithoutClauseMarkers(node, noClauseMarkers);
    Map<XhtmlNode, Integer> minTextLengths = new IdentityHashMap<>();
    handler.findMinTextLengths(node, minTextLengths);
    BooleanHolder hasClauses = new BooleanHolder(false);
    handler.processMarkdownConformanceClauses(node, hasClauses, new ArrayList<>(),
        noClauseMarkers, minTextLengths);
    return hasClauses.ok();
  }
}
