package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CliParamsTest {

  @Test
  void getNamedParam_returnsFirstValue() {
    String[] args = {"-foo", "a", "-foo", "b"};
    assertEquals("a", CliParams.getNamedParam(args, "-foo"));
  }

  @Test
  void getNamedParam_missingFlagReturnsNull() {
    assertNull(CliParams.getNamedParam(new String[]{"-bar", "x"}, "-foo"));
  }

  @Test
  void hasNamedParam_detectsFlagWithoutValue() {
    assertTrue(CliParams.hasNamedParam(new String[]{"-gui"}, "-gui"));
  }

  @Test
  void getNamedParams_collectsAllOccurrencesInOrder() {
    String[] args = {"-po", "a.po", "-po", "b.po", "-other", "x", "-po", "c.po"};
    List<String> values = CliParams.getNamedParams(args, "-po");
    assertEquals(List.of("a.po", "b.po", "c.po"), values);
  }

  @Test
  void getNamedParams_returnsEmptyWhenFlagAbsent() {
    assertTrue(CliParams.getNamedParams(new String[]{"-x", "1"}, "-po").isEmpty());
  }

  @Test
  void getNamedParams_ignoresTrailingFlagWithNoValue() {
    String[] args = {"-po", "a.po", "-po"};
    assertEquals(List.of("a.po"), CliParams.getNamedParams(args, "-po"));
  }
}
