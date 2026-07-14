package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.convertors.misc.ProfileVersionAdaptor.ConversionMessage;
import org.hl7.fhir.convertors.misc.ProfileVersionAdaptor.ConversionMessageStatus;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link CrossVersionAnalyser}: per-target problem reporting (from ProfileVersionAdaptor
 * conversion logs and caught convVersion failures), intentional membership omissions, the all-clean
 * "OK" summary, and empty output when there are no targets (the R5-base-without-generate-version case
 * that leaves {@code pf.cvAnalyser} null / with nothing to say).
 */
class CrossVersionAnalyserTest {

  private CrossVersionAnalyser analyser() {
    CrossVersionAnalyser a = new CrossVersionAnalyser();
    a.setTargets(List.of("r4", "r4b"));
    return a;
  }

  @Test
  void reportsPerTargetProblemsAndOmissions() {
    CrossVersionAnalyser a = analyser();
    List<ConversionMessage> log = new ArrayList<>();
    log.add(new ConversionMessage("element X not representable", ConversionMessageStatus.ERROR));
    a.record("4.0.1", "StructureDefinition/foo", log);
    a.recordOmission("4.3.0", "StructureDefinition/bar");

    String html = a.generate("my.pkg", false);
    assertTrue(html.contains("R4"), html);
    assertTrue(html.contains("StructureDefinition/foo"), html);
    assertTrue(html.contains("element X not representable"), html);
    assertTrue(html.contains("R4B"), html);
    assertTrue(html.contains("StructureDefinition/bar"), html);
    assertTrue(html.contains("not included"), html);
  }

  @Test
  void inlineOutputIsNonEmptyWhenProblems() {
    CrossVersionAnalyser a = analyser();
    a.recordProblem("4.0.1", "ValueSet/vs", "cannot downgrade");
    String inline = a.generate("my.pkg", true);
    assertFalse(inline.isEmpty());
    assertTrue(inline.contains("cannot downgrade"), inline);
  }

  @Test
  void inlineOutputEscapesSpecialChars() {
    CrossVersionAnalyser a = analyser();
    // author-controlled resource key + conversion message + omission key with XML metacharacters
    a.recordProblem("4.0.1", "StructureDefinition/a<b>", "value <x> & \"y\" not representable");
    a.recordOmission("4.3.0", "ValueSet/d<e>&f");
    String inline = a.generate("my.pkg", true);
    // escaped exactly as the block branch does (Utilities.escapeXml: < > & ")
    assertTrue(inline.contains("&lt;"), inline);
    assertTrue(inline.contains("&gt;"), inline);
    assertTrue(inline.contains("&amp;"), inline);
    assertTrue(inline.contains("&quot;"), inline);
    // no raw, unescaped author substrings leak into the fragment
    assertFalse(inline.contains("a<b>"), inline);
    assertFalse(inline.contains("<x>"), inline);
    assertFalse(inline.contains("d<e>"), inline);
  }

  @Test
  void allCleanRendersOkMessage() {
    CrossVersionAnalyser a = analyser();
    String html = a.generate("my.pkg", false);
    assertTrue(html.contains("convert cleanly"), html);
    assertEquals("", a.generate("my.pkg", true)); // inline clean is empty
  }

  @Test
  void noTargetsYieldsEmpty() {
    CrossVersionAnalyser a = new CrossVersionAnalyser(); // no targets, no data (R5 base w/o generate-version)
    assertEquals("", a.generate("my.pkg", false));
    assertEquals("", a.generate("my.pkg", true));
  }

  @Test
  void noteMessagesAreNotReportedAsProblems() {
    CrossVersionAnalyser a = analyser();
    List<ConversionMessage> log = new ArrayList<>();
    log.add(new ConversionMessage("just informational", ConversionMessageStatus.NOTE));
    a.record("4.0.1", "StructureDefinition/foo", log);
    String html = a.generate("my.pkg", false);
    assertTrue(html.contains("convert cleanly"), html);
    assertFalse(html.contains("StructureDefinition/foo"), html);
  }
}
