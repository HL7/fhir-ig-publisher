package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hl7.fhir.exceptions.FHIRException;
import org.junit.jupiter.api.Test;

class MarkdownHeadingSeaterTest {

  @Test
  void keepAChangelog() {
    String md = "# Changelog\n\nAll notable changes...\n\n## [0.1.0] - 2026-10-30\n\nFirst\n";
    assertEquals("### Changelog\n\nAll notable changes...\n\n#### [0.1.0] - 2026-10-30\n\nFirst\n",
        MarkdownHeadingSeater.seat(md, 3));
  }

  @Test
  void alreadySeated() {
    String md = "### A\n\n#### B\n";
    assertSame(md, MarkdownHeadingSeater.seat(md, 3));
  }

  @Test
  void noHeadings() {
    String md = "just text\n\n* a list\n";
    assertSame(md, MarkdownHeadingSeater.seat(md, 3));
  }

  @Test
  void pullsUp() {
    assertEquals("### A\n#### B\n", MarkdownHeadingSeater.seat("#### A\n##### B\n", 3));
  }

  @Test
  void gapsArePreserved() {
    assertEquals("### A\n##### B\n", MarkdownHeadingSeater.seat("# A\n### B\n", 3));
  }

  @Test
  void notAHeading() {
    // GFM needs the space; indented isn't a heading in kramdown
    String md = "#hashtag\n #5 on the list\n## Real\n";
    assertEquals("#hashtag\n #5 on the list\n### Real\n", MarkdownHeadingSeater.seat(md, 3));
  }

  @Test
  void emptyAtx() {
    assertEquals("###\n#### B\n", MarkdownHeadingSeater.seat("#\n## B\n", 3));
  }

  @Test
  void fencedCodeIsLeftAlone() {
    String md = "## A\n\n```\n# not a heading\n<h1>nor this</h1>\n```\n\n~~~~\n# nor this\n```\n# still code\n~~~~\n";
    assertEquals("### A\n\n```\n# not a heading\n<h1>nor this</h1>\n```\n\n~~~~\n# nor this\n```\n# still code\n~~~~\n",
        MarkdownHeadingSeater.seat(md, 3));
  }

  @Test
  void codeFencesDontSetTheLevel() {
    // the only real heading is the h3, so nothing moves
    String md = "```\n# code\n```\n### A\n";
    assertSame(md, MarkdownHeadingSeater.seat(md, 3));
  }

  @Test
  void setext() {
    String md = "Title\n=====\n\nSection\n-------\n\ntext\n";
    assertEquals("### Title\n\n\n#### Section\n\n\ntext\n", MarkdownHeadingSeater.seat(md, 3));
  }

  @Test
  void ruleAfterParagraphIsNotSetext() {
    String md = "line one\nline two\n---\n\n## A\n";
    assertEquals("line one\nline two\n---\n\n### A\n", MarkdownHeadingSeater.seat(md, 3));
  }

  @Test
  void listItemIsNotSetext() {
    String md = "- item\n---\n\n## A\n";
    assertEquals("- item\n---\n\n### A\n", MarkdownHeadingSeater.seat(md, 3));
  }

  @Test
  void htmlHeadings() {
    String md = "<div>\n    <h2 id=\"x\">A</h2>\n    <H3>B</H3>\n</div>\n## C\n";
    assertEquals("<div>\n    <h3 id=\"x\">A</h3>\n    <H4>B</H4>\n</div>\n### C\n", MarkdownHeadingSeater.seat(md, 3));
  }

  @Test
  void htmlLookalikesAreLeftAlone() {
    String md = "## A\n<header>x</header> <hr/> `<h1>` and ``<h2>`` in code\n";
    assertEquals("### A\n<header>x</header> <hr/> `<h1>` and ``<h2>`` in code\n", MarkdownHeadingSeater.seat(md, 3));
  }

  @Test
  void lineEndingsArePreserved() {
    assertEquals("### A\r\ntext\r\n#### B\rmore", MarkdownHeadingSeater.seat("# A\r\ntext\r\n## B\rmore", 3));
  }

  @Test
  void kramdownIdsAndClosingHashesSurvive() {
    assertEquals("### A {#a}\n#### B ##\n", MarkdownHeadingSeater.seat("# A {#a}\n## B ##\n", 3));
  }

  @Test
  void tooDeep() {
    String md = "# A\n## B\n### C\n#### D\n##### E\n";
    assertThrows(FHIRException.class, () -> MarkdownHeadingSeater.seat(md, 3));
  }

  @Test
  void badLevel() {
    assertThrows(FHIRException.class, () -> MarkdownHeadingSeater.seat("# A\n", 0));
    assertThrows(FHIRException.class, () -> MarkdownHeadingSeater.seat("# A\n", 7));
  }
}
