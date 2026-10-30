package org.hl7.fhir.igtools.publisher;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hl7.fhir.exceptions.FHIRException;

/**
 * Seats the headings of a markdown page under the page title that the template puts above it.
 * <p>
 * A markdown page is not a whole page: the template's md page layout wraps it, and emits the page
 * title itself as an h2 before including the markdown. But authors write markdown as if it were the
 * whole document - a changelog in the Keep a Changelog format opens with '# Changelog' and has a
 * '## [version]' per release - so the markdown's headings compete with the template's title, and
 * the page fails the WCAG heading structure checks in {@link HTMLInspector} (a heading above the
 * root, and several roots). It also means the page is never re-levelled for 'page-heading-level',
 * which only touches pages with a single root.
 * <p>
 * This is the markdown equivalent of {@link org.hl7.fhir.utilities.xhtml.XhtmlNode#ensureTopHeadingIs(int)},
 * which does the same job for narrative: every heading is shifted by the same amount so the highest
 * one lands at the requested level, preserving the structure (and any gaps - it seats, it does not
 * repair). It works in both directions, so a page that starts at h4 is pulled up too.
 * <p>
 * What counts as a heading, following kramdown's GFM parser (Jekyll's default):
 * <ul>
 *   <li>ATX headings: 1-6 '#' at the start of a line, followed by a space or tab (or nothing)</li>
 *   <li>setext headings: a line of text underlined with '=' (h1) or '-' (h2). These are rewritten as
 *     ATX headings, as setext can't express anything below h2. The underline is replaced with an
 *     empty line, so line numbers in the page don't move</li>
 *   <li>raw html &lt;h1&gt;..&lt;h6&gt; tags (and their end tags), which kramdown passes through</li>
 * </ul>
 * Nothing inside a fenced code block (``` or ~~~) or an inline code span is touched or counted.
 * Headings that arrive from elsewhere at build time ({% include %}d fragments, liquid output) are
 * invisible here, by design: they are not the author's markdown.
 */
public class MarkdownHeadingSeater {

  private static final Pattern ATX = Pattern.compile("^(#{1,6})(?=[ \\t]|$)");
  private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})");
  private static final Pattern SETEXT_UNDERLINE = Pattern.compile("^ {0,3}(=+|-+)[ \\t]*$");
  private static final Pattern HTML_HEADING = Pattern.compile("<(/?)[hH]([1-6])(?=[\\s>/])");

  private enum Kind { ATX, SETEXT, HTML }

  private static class Heading {
    private final Kind kind;
    private final int line;
    private final int offset; // for HTML: the index of the level digit in the line
    private final int level;

    private Heading(Kind kind, int line, int offset, int level) {
      this.kind = kind;
      this.line = line;
      this.offset = offset;
      this.level = level;
    }
  }

  /**
   * @param md the markdown source (front matter already removed)
   * @param level the level the highest heading in the markdown should end up at (1..6)
   * @return the markdown with its headings shifted; the same string if there are no headings or they
   *   are already at the right level
   * @throws FHIRException if level is outside 1..6, or the shift would push a heading past h6. Thrown
   *   before anything is changed
   */
  public static String seat(String md, int level) throws FHIRException {
    if (level < 1 || level > 6) {
      throw new FHIRException("Heading level "+level+" is not between 1 and 6");
    }
    List<String> lines = new ArrayList<>();
    List<String> eols = new ArrayList<>();
    splitLines(md, lines, eols);

    List<Heading> headings = findHeadings(lines);
    if (headings.isEmpty()) {
      return md;
    }
    int min = 7;
    int max = 0;
    for (Heading h : headings) {
      min = Math.min(min, h.level);
      max = Math.max(max, h.level);
    }
    int delta = level - min;
    if (delta == 0) {
      return md;
    }
    if (max + delta > 6) {
      throw new FHIRException("The headings run from h"+min+" to h"+max+", so starting them at h"+level+" would need a heading below h6");
    }

    // HTML edits are by offset, so apply them right to left within a line
    for (int i = headings.size() - 1; i >= 0; i--) {
      Heading h = headings.get(i);
      String hashes = "#".repeat(h.level + delta);
      String line = lines.get(h.line);
      switch (h.kind) {
        case ATX:
          lines.set(h.line, hashes + line.substring(h.level));
          break;
        case SETEXT:
          lines.set(h.line, hashes + " " + line.trim());
          lines.set(h.line + 1, "");
          break;
        case HTML:
          lines.set(h.line, line.substring(0, h.offset) + (h.level + delta) + line.substring(h.offset + 1));
          break;
      }
    }

    StringBuilder b = new StringBuilder(md.length() + headings.size());
    for (int i = 0; i < lines.size(); i++) {
      b.append(lines.get(i));
      b.append(eols.get(i));
    }
    return b.toString();
  }

  /**
   * Splits on \n, \r\n or \r, remembering each line's own ending, so the result can be put back
   * together exactly - IG sources have mixed line endings, and a rewrite that normalised them would
   * show up as a whole-file change.
   */
  private static void splitLines(String md, List<String> lines, List<String> eols) {
    int start = 0;
    int i = 0;
    while (i < md.length()) {
      char c = md.charAt(i);
      if (c == '\n' || c == '\r') {
        lines.add(md.substring(start, i));
        if (c == '\r' && i + 1 < md.length() && md.charAt(i + 1) == '\n') {
          eols.add("\r\n");
          i += 2;
        } else {
          eols.add(String.valueOf(c));
          i++;
        }
        start = i;
      } else {
        i++;
      }
    }
    lines.add(md.substring(start));
    eols.add("");
  }

  private static List<Heading> findHeadings(List<String> lines) {
    List<Heading> res = new ArrayList<>();
    String fence = null; // the opening fence of the code block we're in, if any
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      Matcher fm = FENCE.matcher(line);
      if (fence != null) {
        if (fm.find() && fm.group(1).charAt(0) == fence.charAt(0) && fm.group(1).length() >= fence.length()
            && line.substring(fm.end()).trim().isEmpty()) {
          fence = null;
        }
        continue;
      }
      if (fm.find()) {
        fence = fm.group(1);
        continue;
      }
      Matcher am = ATX.matcher(line);
      if (am.find()) {
        res.add(new Heading(Kind.ATX, i, 0, am.group(1).length()));
      } else if (isSetextText(lines, i)) {
        res.add(new Heading(Kind.SETEXT, i, 0, lines.get(i + 1).trim().charAt(0) == '=' ? 1 : 2));
        findHtmlHeadings(line, i, res);
        i++; // the underline
        continue;
      }
      findHtmlHeadings(line, i, res);
    }
    return res;
  }

  /**
   * A setext heading is a single line of paragraph text with an underline straight after it. It has
   * to start a block (first line, or after a blank line) - otherwise it's the last line of a
   * paragraph, and the '---' under it is either a heading of the whole paragraph or a rule,
   * depending on the parser, and guessing is worse than leaving it alone.
   */
  private static boolean isSetextText(List<String> lines, int i) {
    if (i + 1 >= lines.size() || !SETEXT_UNDERLINE.matcher(lines.get(i + 1)).matches()) {
      return false;
    }
    if (i > 0 && !lines.get(i - 1).trim().isEmpty()) {
      return false;
    }
    String line = lines.get(i);
    if (line.trim().isEmpty() || line.startsWith("    ") || line.startsWith("\t")) {
      return false;
    }
    String t = line.trim();
    // other block starts: lists, quotes, tables, html, liquid
    return !(t.startsWith(">") || t.startsWith("|") || t.startsWith("<") || t.startsWith("{%")
        || t.matches("^([-*+]|\\d+[.)])([ \\t].*)?$"));
  }

  private static void findHtmlHeadings(String line, int lineNo, List<Heading> res) {
    if (line.indexOf('<') < 0) {
      return;
    }
    String masked = maskCodeSpans(line);
    Matcher m = HTML_HEADING.matcher(masked);
    while (m.find()) {
      res.add(new Heading(Kind.HTML, lineNo, m.start(2), Integer.parseInt(m.group(2))));
    }
  }

  /**
   * Blanks out `inline code` (with any length of backtick run) so a heading tag quoted in code isn't
   * seen. The result has the same length as the input, so offsets still line up.
   */
  private static String maskCodeSpans(String line) {
    StringBuilder b = new StringBuilder(line);
    int i = 0;
    while (i < line.length()) {
      if (line.charAt(i) == '`') {
        int j = i;
        while (j < line.length() && line.charAt(j) == '`') {
          j++;
        }
        String run = line.substring(i, j);
        int close = findClosingRun(line, j, run.length());
        if (close < 0) {
          i = j;
        } else {
          for (int k = j; k < close; k++) {
            b.setCharAt(k, ' ');
          }
          i = close + run.length();
        }
      } else {
        i++;
      }
    }
    return b.toString();
  }

  private static int findClosingRun(String line, int from, int len) {
    int i = from;
    while (i < line.length()) {
      if (line.charAt(i) == '`') {
        int j = i;
        while (j < line.length() && line.charAt(j) == '`') {
          j++;
        }
        if (j - i == len) {
          return i;
        }
        i = j;
      } else {
        i++;
      }
    }
    return -1;
  }
}
