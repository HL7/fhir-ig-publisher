package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.ElementDefinition;
import org.hl7.fhir.model.core.Narrative.NarrativeStatus;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.ValueSet;
import org.hl7.fhir.model.core.formats.JsonParser;
import org.hl7.fhir.model.core.formats.XmlParser;
import org.hl7.fhir.model.utilities.formats.OutputStyle;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.services.elementmodel.Property;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonBoolean;
import org.hl7.fhir.utilities.json.model.JsonNull;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

  private static String plain(JsonObject o) {
    return org.hl7.fhir.utilities.json.parser.JsonParser.compose(o, false);
  }

  /**
   * The state's names are paths and file names: on Windows, with backslashes in them (the paths the files were loaded
   * from), and a file name can hold a quote, a control character, or non-ASCII. The JSON writer doesn't escape names,
   * so the state written with them couldn't be read back, and every -rapido build on Windows was a complete one
   */
  @Test
  void theStateIsReadBackWhateverItsNamesHold(@TempDir Path dir) throws Exception {
    JsonObject state = new JsonObject();
    state.add("version", 1);
    JsonObject other = state.forceObject("otherFiles");
    other.set("input\\images\\a.png", "h1");
    other.set("input\\up/", "(same as D:\\a\\ig\\input)");
    other.set("input/q\"uote.txt", "h2");
    other.set("input/tab\tand\u0001.txt", "h3");
    other.set("input/images/gr\u00f6\u00dfe-\u00f1.svg", "h4");
    JsonObject files = state.forceObject("files");
    JsonObject f = files.forceObject("D:\\a\\_temp\\ig\\input\\pages\\index.md");
    f.forceObject("resources").set("Patient/p", "h5");
    f.forceArray("list").add(new JsonObject().add("C:\\x\\y", "z"));
    File sf = dir.resolve("state.json").toFile();

    RapidoTracker.writeState(state, sf);
    JsonObject read = RapidoTracker.readState(sf);

    assertEquals(plain(state), plain(read));
    assertEquals("h1", read.getJsonObject("otherFiles").asString("input\\images\\a.png"));
    assertEquals("h5", read.getJsonObject("files").getJsonObject("D:\\a\\_temp\\ig\\input\\pages\\index.md").getJsonObject("resources").asString("Patient/p"));
  }

  /** the state of a build whose names have nothing to escape (any build on Linux or macOS, mostly) is written as before */
  @Test
  void aStateWithNothingToEscapeIsWrittenAsBefore(@TempDir Path dir) throws Exception {
    JsonObject state = new JsonObject();
    state.add("version", 1);
    state.forceObject("otherFiles").set("input/images/gr\u00f6\u00dfe-\u00f1.svg", "h\\1\"").set("input/up/", "(same as /x/\"y\")");
    JsonObject f = state.forceObject("files").forceObject("/home/ig/input/pages/index.md");
    f.forceObject("resources").set("Patient/p", "h5");
    f.forceArray("list").add(new JsonObject().add("a", "b")).add("c");
    File sf = dir.resolve("state.json").toFile();
    File before = dir.resolve("before.json").toFile();

    RapidoTracker.writeState(state, sf);
    org.hl7.fhir.utilities.json.parser.JsonParser.compose(state, before, false);

    assertArrayEquals(Files.readAllBytes(before.toPath()), Files.readAllBytes(sf.toPath()));
  }

  /** the state is written as it goes, but as the JSON writer writes it: whatever it holds, the same bytes */
  @Test
  void theStateIsWrittenAsTheJsonWriterWritesIt(@TempDir Path dir) throws Exception {
    JsonObject state = new JsonObject();
    state.add("version", 4);
    state.add("uuid", "u\"1\\\n\u0001\u00e9\u4e2d");
    state.add("complete", true);
    state.add("none", new JsonNull());
    state.add("n", 1234567890123L);
    state.forceObject("empty");
    state.forceArray("emptyList");
    JsonObject files = state.forceObject("files");
    JsonObject f = files.forceObject("D:\\a\\ig\\input\\q\"uote\t.md");
    f.forceObject("resources").set("Patient/p", "h5").set("Observation/o", "h6");
    f.forceArray("list").add(new JsonObject().add("C:\\x", "z")).add("c").add(new JsonArray().add(1).add(new JsonBoolean(false)));
    files.forceObject("/home/ig/input/gr\u00f6\u00dfe.md").add("oh", "h7");
    File sf = dir.resolve("state.json").toFile();
    File before = dir.resolve("before.json").toFile();

    RapidoTracker.writeState(state, sf);
    org.hl7.fhir.utilities.json.parser.JsonParser.compose(RapidoTracker.escapeNames(state), before, false);

    assertArrayEquals(Files.readAllBytes(before.toPath()), Files.readAllBytes(sf.toPath()));
    assertEquals(plain(state), plain(RapidoTracker.readState(sf)));
  }

  private static Element child(String name, String type, String value) {
    Property p = mock(Property.class);
    when(p.getName()).thenReturn(name);
    when(p.getDefinition()).thenReturn(new ElementDefinition());
    when(p.isPrimitive("string")).thenReturn(true);
    return new Element(name, p, type, value);
  }

  private static Element resource(String type, Element... children) {
    Property p = mock(Property.class);
    when(p.isResource()).thenReturn(true);
    Element e = new Element(type, p);
    for (Element c : children) {
      e.getChildList().add(c);
    }
    return e;
  }

  /** a Device can have more than one name: it has no canonical name, and reading it as one stopped the build */
  @Test
  void aResourceWithManyNamesHasNoName() {
    Element device = resource("Device", child("name", "BackboneElement", null), child("name", "BackboneElement", null));
    assertThrows(Error.class, () -> device.getNamedChildValue("name"));
    assertNull(RapidoTracker.singleValue(device, "name"));

    assertNull(RapidoTracker.singleValue(resource("Patient", child("name", "HumanName", null)), "name"));
    assertNull(RapidoTracker.singleValue(device, "url"));
    Element vs = resource("ValueSet", child("url", "uri", "http://example.org/vs"), child("name", "string", "VS"));
    assertEquals("VS", RapidoTracker.singleValue(vs, "name"));
  }
}
