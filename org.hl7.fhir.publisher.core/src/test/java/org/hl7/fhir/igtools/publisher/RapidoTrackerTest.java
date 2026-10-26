package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.Narrative.NarrativeStatus;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.ValueSet;
import org.hl7.fhir.model.core.formats.JsonParser;
import org.hl7.fhir.model.core.formats.XmlParser;
import org.hl7.fhir.model.utilities.formats.OutputStyle;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.junit.jupiter.api.Test;

class RapidoTrackerTest {

  private static String xml(ValueSet vs) throws Exception {
    return new XmlParser(ModelContext.fullCoreContext()).setOutputStyle(OutputStyle.PRETTY).composeString(vs);
  }

  private static String json(ValueSet vs) throws Exception {
    return new JsonParser(ModelContext.fullCoreContext()).setOutputStyle(OutputStyle.PRETTY).composeString(vs);
  }

  /** an expansion whose narrative has a table cell with only an empty text node in it, as the renderers write */
  private static ValueSet expansion() {
    ValueSet vs = new ValueSet();
    vs.setUrl("http://example.org/ValueSet/vs");
    XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
    XhtmlNode tr = div.table("grid", false).tr();
    tr.td().addText("");
    tr.td().addText("code");
    XhtmlNode p = div.para();
    p.addText("");
    p.addText("text after an empty text node");
    vs.getText().setStatus(NarrativeStatus.GENERATED).setDiv(div);
    return vs;
  }

  @Test
  void anExpansionReadBackIsWrittenAsBefore() throws Exception {
    ValueSet vs = expansion();
    String xml = xml(vs);
    String json = json(vs);

    String saved = RapidoTracker.saveExpansion(new JsonParser(ModelContext.fullCoreContext()), vs);
    ValueSet read = RapidoTracker.readExpansion(new JsonParser(ModelContext.fullCoreContext()), saved);

    assertEquals(xml, xml(read));
    assertEquals(json, json(read));
    // and saving it leaves the expansion in the build as it was
    assertEquals(xml, xml(vs));
  }

  @Test
  void anExpansionThroughPlainJsonLosesTheEmptyCell() throws Exception {
    // what saveExpansion is for: a plain JSON round trip writes the cell as <td/>
    ValueSet vs = expansion();
    ValueSet read = (ValueSet) new JsonParser(ModelContext.fullCoreContext()).parse(json(vs));
    assertNotEquals(xml(vs), xml(read));
  }

  @Test
  void profileUsagesAreFoundByUrl() {
    FetchedFile f = new FetchedFile("sd.json");
    FetchedResource r = f.addResource("sd");
    StructureDefinition sd = new StructureDefinition();
    sd.setUrl("http://example.org/StructureDefinition/address");
    r.setResource(sd);
    FetchedFile g = new FetchedFile("vs.json");
    g.addResource("vs").setResource(new ValueSet().setUrl("http://example.org/StructureDefinition/address"));

    JsonArray a = new JsonArray();
    a.add("http://example.org/StructureDefinition/address");
    List<FetchedResource> found = RapidoTracker.profileSources(List.of(g, f), a);
    assertEquals(1, found.size());
    assertSame(r, found.get(0));

    assertEquals(0, RapidoTracker.profileSources(List.of(g, f), new JsonArray()).size());
    // a profile that is no longer in the IG, or a record from a build that didn't keep them: validate again
    JsonArray gone = new JsonArray();
    gone.add("http://example.org/StructureDefinition/gone");
    assertNull(RapidoTracker.profileSources(List.of(g, f), gone));
    assertNull(RapidoTracker.profileSources(List.of(g, f), null));
  }
}
