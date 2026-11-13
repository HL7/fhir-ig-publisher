package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.junit.jupiter.api.Test;

/**
 * A reused validation attaches the profiles each element was validated against to the element again, as the qa page
 * lists them from there (else it lists them from the messages, with a different label)
 */
class RapidoSignpostsTest {

  private static Element el(String name, String path, Element... children) {
    Element e = new Element(name);
    e.setPath(path);
    for (Element c : children) {
      e.getChildList().add(c);
    }
    return e;
  }

  private static Element patient() {
    return el("Patient", "Patient", el("id", "Patient.id"), el("name", "Patient.name[0]", el("family", "Patient.name[0].family")), el("contact", "Patient.contact[0]"));
  }

  private static ValidationMessage signpost(String path, String profile) {
    ValidationMessage vm = new ValidationMessage(Source.InstanceValidator, IssueType.INFORMATIONAL, path, "Validate resource against profile " + profile, IssueSeverity.INFORMATION);
    vm.setSignpost(true);
    return vm;
  }

  /** what Rapido saves goes through its JSON file */
  private static JsonArray roundTrip(JsonArray a) throws IOException {
    JsonObject o = new JsonObject();
    o.add("a", a);
    return JsonParser.parseObject(JsonParser.compose(o)).getJsonArray("a");
  }

  @Test
  void signpostsComeBackOnTheirElements() throws IOException {
    Element a = patient();
    ValidationMessage onRoot = signpost("Patient", "http://x/p1");
    ValidationMessage onName = signpost("Patient.name[0]", "http://x/p2");
    // one the validator attached without reporting it (e.g. from a slice it tried)
    ValidationMessage unreported = signpost("Patient.name[0].family", "http://x/p3");
    List<ValidationMessage> errs = new ArrayList<>(List.of(new ValidationMessage(Source.InstanceValidator, IssueType.CODEINVALID, "Patient.id", "x", IssueSeverity.ERROR), onRoot, onName));
    a.addMessage(onRoot);
    a.getChildList().get(1).addMessage(onName);
    a.getChildList().get(1).getChildList().get(0).addMessage(unreported);
    JsonArray rec = RapidoTracker.recordSignposts(a, errs);

    // the next build: the same resource, loaded again, and the messages restored
    Element b = patient();
    List<ValidationMessage> restored = new ArrayList<>();
    for (ValidationMessage vm : errs) {
      restored.add(RapidoTracker.readMessage(RapidoTracker.writeMessage(vm)));
    }
    assertTrue(RapidoTracker.restoreSignposts(b, roundTrip(rec), restored));
    assertEquals(1, b.getMessages().size());
    // a reported message comes back as the same object, as in a complete build
    assertSame(restored.get(1), b.getMessages().get(0));
    assertSame(restored.get(2), b.getChildList().get(1).getMessages().get(0));
    ValidationMessage back = b.getChildList().get(1).getChildList().get(0).getMessages().get(0);
    assertEquals(unreported.getMessage(), back.getMessage());
    assertTrue(back.isSignpost());
    assertFalse(b.getChildList().get(0).hasMessages());
    assertFalse(b.getChildList().get(2).hasMessages());
  }

  @Test
  void signpostsDontComeBackOnADifferentTree() {
    Element a = patient();
    ValidationMessage onFamily = signpost("Patient.name[0].family", "http://x/p1");
    a.getChildList().get(1).getChildList().get(0).addMessage(onFamily);
    JsonArray rec = RapidoTracker.recordSignposts(a, List.of(onFamily));
    List<ValidationMessage> restored = List.of(signpost("Patient.name[0].family", "http://x/p1"));

    // the element isn't there
    Element b = el("Patient", "Patient", el("id", "Patient.id"), el("name", "Patient.name[0]"));
    assertFalse(RapidoTracker.restoreSignposts(b, rec, restored));
    // something else is there
    Element c = el("Patient", "Patient", el("id", "Patient.id"), el("name", "Patient.name[0]", el("given", "Patient.name[0].given[0]")));
    assertFalse(RapidoTracker.restoreSignposts(c, rec, restored));
    assertFalse(c.getChildList().get(1).getChildList().get(0).hasMessages());
    // one comes back and a later one can't: nothing is attached, as the resource is validated again
    Element e = patient();
    ValidationMessage onRoot = signpost("Patient", "http://x/p2");
    e.addMessage(onRoot);
    e.getChildList().get(1).getChildList().get(0).addMessage(onFamily);
    JsonArray both = RapidoTracker.recordSignposts(e, List.of(onRoot, onFamily));
    assertFalse(RapidoTracker.restoreSignposts(b, both, List.of(signpost("Patient", "http://x/p2"), signpost("Patient.name[0].family", "http://x/p1"))));
    assertFalse(b.hasMessages());
    // the messages aren't the same
    assertFalse(RapidoTracker.restoreSignposts(patient(), rec, List.of()));
    // a damaged record
    JsonArray damaged = new JsonArray();
    damaged.add("x");
    assertFalse(RapidoTracker.restoreSignposts(patient(), damaged, restored));
    // a record from before signposts were kept
    assertFalse(RapidoTracker.restoreSignposts(patient(), null, restored));
    // nothing to attach
    Element d = patient();
    assertTrue(RapidoTracker.restoreSignposts(d, RapidoTracker.recordSignposts(patient(), restored), restored));
    assertFalse(d.hasMessages());
  }
}
