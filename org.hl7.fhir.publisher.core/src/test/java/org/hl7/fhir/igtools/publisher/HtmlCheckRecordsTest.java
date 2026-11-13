package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What watch mode keeps of an html check, so that it can put back what the check found */
class HtmlCheckRecordsTest {

  @TempDir
  Path dir;

  private static ValidationMessage message(String text) {
    return new ValidationMessage(Source.HtmlChecker, IssueType.NOTFOUND, "page.html", text, IssueSeverity.ERROR);
  }

  @Test
  void recordsWhatIsAddedAsItWasAdded() {
    HtmlCheckRecords records = new HtmlCheckRecords("sig");
    HtmlCheckRecords.FileRecord record = new HtmlCheckRecords.FileRecord(null, new ArrayList<>(), new ArrayList<>());
    List<ValidationMessage> messages = new ArrayList<>();
    messages.add(message("before"));
    HtmlCheckRecords.RecordingList list = new HtmlCheckRecords.RecordingList(messages, record, records);

    ValidationMessage m = message("one");
    list.add(m);
    list.addUnrecorded(message("not recorded"));
    // changed after it was added (as matching suppressed messages does): the record keeps it as it was
    m.setMessage("changed");

    assertEquals(3, messages.size());
    assertEquals(1, record.events.size());
    ValidationMessage kept = (ValidationMessage) record.events.get(0);
    assertNotSame(m, kept);
    assertEquals("one", kept.getMessage());
    assertTrue(HtmlCheckRecords.sameContent(message("one"), kept));
    assertNull(records.getNotUsable());
  }

  @Test
  void anythingButAddingAtTheEndMeansTheRecordsCantBeUsed() {
    for (int i = 0; i < 3; i++) {
      HtmlCheckRecords records = new HtmlCheckRecords("sig");
      HtmlCheckRecords.FileRecord record = new HtmlCheckRecords.FileRecord(null, new ArrayList<>(), new ArrayList<>());
      List<ValidationMessage> messages = new ArrayList<>(Arrays.asList(message("a"), message("b")));
      HtmlCheckRecords.RecordingList list = new HtmlCheckRecords.RecordingList(messages, record, records);
      if (i == 0) {
        list.add(0, message("c"));
      } else if (i == 1) {
        list.set(1, message("c"));
      } else {
        list.remove(0);
      }
      assertTrue(records.getNotUsable() != null, "case " + i);
    }
  }

  @Test
  void copiesAreIndependent() {
    ValidationMessage m = message("text");
    m.setLocation("here");
    List<ValidationMessage> copies = HtmlCheckRecords.copies(Arrays.asList(m));
    assertTrue(HtmlCheckRecords.sameContent(m, copies.get(0)));
    copies.get(0).setLocation("there");
    assertEquals("here", m.getLocation());
    assertFalse(HtmlCheckRecords.sameContent(m, copies.get(0)));
    assertNull(HtmlCheckRecords.copy(null));
  }

  @Test
  void theTreeOfTheOutput() throws IOException {
    Files.createDirectories(dir.resolve("out/sub"));
    Files.write(dir.resolve("out/a.html"), new byte[0]);
    Files.write(dir.resolve("out/sub/b.html"), new byte[0]);
    boolean[] links = new boolean[1];
    Set<String> tree = HtmlCheckRecords.listTree(dir.resolve("out").toString(), links);
    // with the file system's separator: the tree is only compared with another listing
    assertEquals(new HashSet<>(Arrays.asList("", "a.html", "sub", Path.of("sub", "b.html").toString())), tree);
    assertFalse(links[0]);

    HtmlCheckRecords records = new HtmlCheckRecords("sig");
    records.setTree(tree, false);
    assertTrue(records.sameTree(HtmlCheckRecords.listTree(dir.resolve("out").toString(), new boolean[1]), false));

    // a file added or removed, or links (which may point anywhere): not the same
    Files.write(dir.resolve("out/sub/c.html"), new byte[0]);
    assertFalse(records.sameTree(HtmlCheckRecords.listTree(dir.resolve("out").toString(), new boolean[1]), false));
    Files.delete(dir.resolve("out/sub/c.html"));
    assertFalse(records.sameTree(tree, true));
    Files.createSymbolicLink(dir.resolve("out/link.html"), dir.resolve("out/a.html"));
    boolean[] l2 = new boolean[1];
    HtmlCheckRecords.listTree(dir.resolve("out").toString(), l2);
    assertTrue(l2[0]);

    assertTrue(HtmlCheckRecords.listTree(dir.resolve("none").toString(), new boolean[1]).isEmpty());
    assertFalse(new HtmlCheckRecords("sig").sameTree(tree, false));
  }
}
