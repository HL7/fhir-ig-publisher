package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;
import org.hl7.fhir.validation.BaseValidator.BooleanHolder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The markdown conformance clause scan skips the subtrees that can't hold a clause; it has to make the same changes
 * to a page as the scan of every node does
 */
class ConformanceClauseSkipTest {

  private static Stream<String> pages() {
    return Stream.of(
      "<div><h2>Intro</h2><p>Plain text with <b>bold</b> and <a href=\"x.html\">a link</a>.</p><ul><li>one</li><li>two</li></ul></div>",
      "<div><p>Before</p><p>§§c1:The summary^ first para</p><p>inside one</p><p>inside <i>two</i></p><p>§§</p><p>after</p></div>",
      "<div><section><p>Systems §c2:SHALL do this§ and §c3:SHOULD do that§.</p></section><p>none here</p></div>",
      "<div><p>Split §c4:SHALL <b>support</b> this§ clause</p><table><tr><td>a</td><td>b §c5:MAY x§</td></tr></table></div>",
      "<div><p>!§§</p><p>§§§</p><p>text</p></div>",
      "<div>!§§</div>",
      "<div><p>An image <img src=\"i.png\" alt=\"§c6:pic§\"/> here</p><p>a &amp; b</p><p>&lt;tag&gt;</p></div>",
      "<div><div><div><p>deep §c7:SHALL nest§</p></div></div><div><p>deep but plain</p></div></div>",
      "<div><div><span>!§§</span></div><div><div>!§§</div></div><section><b>!</b><i>§§</i></section><p>more &amp; more</p></div>",
      "<div><ul><li>!§§</li></ul><table><tr><td>!§§</td></tr></table><p>§§§</p><p><img src=\"x.png\" alt=\"!§§\"/></p></div>"
    );
  }

  @ParameterizedTest
  @MethodSource("pages")
  void skippingMakesTheSameChanges(String page) throws IOException {
    HTMLInspector inspector = new HTMLInspector(null, "root", new ArrayList<>(), new ArrayList<>(), null, "http://example.org/ig",
      "example.ig", "0.1.0", null, new ArrayList<>(), null, false, null, new ArrayList<>(), false, new ArrayList<>(), null);
    ConformanceStatementHandler handler = new ConformanceStatementHandler(inspector, null, null, new ArrayList<>(), false, null);

    XhtmlNode all = new XhtmlParser().parseFragment(page);
    BooleanHolder allClauses = new BooleanHolder(false);
    handler.processMarkdownConformanceClauses(all, allClauses, new ArrayList<>(), Collections.emptySet(), Collections.emptyMap());

    XhtmlNode skipping = new XhtmlParser().parseFragment(page);
    Set<XhtmlNode> noMarkers = Collections.newSetFromMap(new IdentityHashMap<>());
    handler.findNodesWithoutClauseMarkers(skipping, noMarkers);
    Map<XhtmlNode, Integer> minTextLengths = new IdentityHashMap<>();
    handler.findMinTextLengths(skipping, minTextLengths);
    BooleanHolder skippingClauses = new BooleanHolder(false);
    handler.processMarkdownConformanceClauses(skipping, skippingClauses, new ArrayList<>(), noMarkers, minTextLengths);

    assertEquals(new XhtmlComposer(false).compose(all), new XhtmlComposer(false).compose(skipping));
    assertEquals(allClauses.ok(), skippingClauses.ok());
    if (!page.contains("§")) {
      assertTrue(noMarkers.contains(skipping));
      assertFalse(allClauses.ok());
    }
  }
}
