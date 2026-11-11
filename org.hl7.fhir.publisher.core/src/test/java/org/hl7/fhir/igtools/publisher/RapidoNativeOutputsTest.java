package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.Hasher;
import org.hl7.fhir.igtools.publisher.RapidoNativeOutputs.FileRecord;
import org.hl7.fhir.igtools.publisher.RapidoNativeOutputs.ResourceRecord;
import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.ElementDefinition;
import org.hl7.fhir.model.core.Enumerations.PublicationStatus;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.model.core.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.services.elementmodel.JsonParser;
import org.hl7.fhir.services.utilities.NPMPackageGenerator.Category;
import org.hl7.fhir.standalone.context.SimpleWorkerContext;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** the record of what the native outputs step did for a file, and the key for what a resource's outputs are made from */
class RapidoNativeOutputsTest {

  private static SimpleWorkerContext context;

  @TempDir
  Path temp;

  @BeforeAll
  static void makeContext() throws Exception {
    context = new SimpleWorkerContext.SimpleWorkerContextBuilder(ModelContext.fullCoreContext()).fromNothing();
    for (String name : new String[] {"string", "id", "xhtml"}) {
      StructureDefinition sd = type(name, StructureDefinitionKind.PRIMITIVETYPE);
      element(sd, name, null, "*");
      element(sd, name + ".value", "http://hl7.org/fhirpath/System.String", "1");
      context.cacheResource(sd);
    }
    StructureDefinition basic = type("Basic", StructureDefinitionKind.RESOURCE);
    element(basic, "Basic", null, "*");
    element(basic, "Basic.id", "id", "1");
    element(basic, "Basic.code", "string", "1");
    element(basic, "Basic.subject", "string", "*");
    element(basic, "Basic.div", "xhtml", "1");
    context.cacheResource(basic);
  }

  private static StructureDefinition type(String name, StructureDefinitionKind kind) {
    StructureDefinition sd = new StructureDefinition();
    sd.setUrl("http://hl7.org/fhir/StructureDefinition/" + name);
    sd.setId(name);
    sd.setName(name);
    sd.setType(name);
    sd.setKind(kind);
    sd.setStatus(PublicationStatus.ACTIVE);
    sd.setDerivation(TypeDerivationRule.SPECIALIZATION);
    sd.setAbstract(false);
    return sd;
  }

  private static void element(StructureDefinition sd, String path, String type, String max) {
    ElementDefinition ed = sd.getSnapshot().addElement();
    ed.setId(path);
    ed.setPath(path);
    ed.setMin(0);
    ed.setMax(max);
    ed.getBase().setPath(path).setMin(0).setMax(max);
    if (type != null) {
      ed.addType().setCode(type);
    }
  }

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  /** a record of two resources, with the files it says were written */
  private FileRecord record(RapidoNativeOutputs outputs) throws Exception {
    Files.createDirectories(temp.resolve("en"));
    Files.write(temp.resolve("Basic-a.json"), bytes("{a}"));
    Files.write(temp.resolve("en").resolve("Basic-a.json"), bytes("{a en}"));
    Files.write(temp.resolve("Basic-b.xml"), bytes("<b/>"));
    FileRecord rec = new FileRecord();
    ResourceRecord a = rec.add("key a");
    a.entries.add(new RapidoNativeOutputs.Entry(null, Category.RESOURCE, "Basic-a.json", bytes("main a")));
    a.entries.add(new RapidoNativeOutputs.Entry("en", Category.RESOURCE, "Basic-a.json", bytes("english a")));
    a.json = bytes("english a");
    ValidationMessage vm = new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, 3, 4, "Basic.code", "not translated", IssueSeverity.WARNING);
    vm.setMessageId("AN_ID");
    a.messages.add(vm);
    ResourceRecord b = rec.add("key b");
    b.entries.add(new RapidoNativeOutputs.Entry(null, Category.EXAMPLE, "Basic-b.json", new byte[0]));
    b.json = bytes("other json for b");
    outputs.wrote(rec, temp.resolve("Basic-a.json").toString(), true);
    outputs.wrote(rec, temp.resolve("en").resolve("Basic-a.json").toString(), true);
    outputs.wrote(rec, temp.resolve("Basic-b.xml").toString(), false);
    return rec;
  }

  private List<Path> recordFiles() throws Exception {
    try (Stream<Path> s = Files.list(temp.resolve("_rapido").resolve("native"))) {
      return s.collect(Collectors.toList());
    }
  }

  @Test
  void aRecordComesBackAsItWasSaved() throws Exception {
    RapidoNativeOutputs outputs = new RapidoNativeOutputs(temp.toString());
    FetchedFile f = new FetchedFile("input/a.json");
    assertNull(outputs.read(f));
    outputs.save(f, record(outputs));

    FileRecord rec = outputs.read(f);
    assertNotNull(rec);
    assertEquals(2, rec.resources.size());
    ResourceRecord a = rec.resources.get(0);
    assertEquals("key a", a.key);
    assertEquals(2, a.entries.size());
    assertNull(a.entries.get(0).lang);
    assertEquals(Category.RESOURCE, a.entries.get(0).category);
    assertEquals("Basic-a.json", a.entries.get(0).name);
    assertArrayEquals(bytes("main a"), a.entries.get(0).content);
    assertEquals("en", a.entries.get(1).lang);
    assertArrayEquals(bytes("english a"), a.entries.get(1).content);
    assertSame(a.entries.get(1).content, a.json, "kept once");
    assertEquals(1, a.messages.size());
    ValidationMessage vm = a.messages.get(0);
    assertEquals("not translated", vm.getMessage());
    assertEquals("AN_ID", vm.getMessageId());
    assertEquals(IssueSeverity.WARNING, vm.getLevel());
    assertEquals(3, vm.getLine());
    ResourceRecord b = rec.resources.get(1);
    assertEquals("key b", b.key);
    assertEquals(Category.EXAMPLE, b.entries.get(0).category);
    assertArrayEquals(new byte[0], b.entries.get(0).content);
    assertArrayEquals(bytes("other json for b"), b.json);
    assertTrue(b.messages.isEmpty());
    assertEquals(List.of("Basic-a.json", Path.of("en", "Basic-a.json").toString()), rec.outputNames);
    assertEquals(3, rec.written.size());
    assertEquals(temp.resolve("Basic-b.xml").toString(), outputs.absolute("Basic-b.xml"));
    assertTrue(outputs.filesUnchanged(rec));

    // another file has another record
    assertNull(outputs.read(new FetchedFile("input/b.json")));
    outputs.forget(f);
    assertNull(outputs.read(f));
  }

  @Test
  void aFileThatChangedSinceIsSeen() throws Exception {
    RapidoNativeOutputs outputs = new RapidoNativeOutputs(temp.toString());
    FetchedFile f = new FetchedFile("input/a.json");
    outputs.save(f, record(outputs));
    Path p = temp.resolve("en").resolve("Basic-a.json");
    FileTime time = Files.getLastModifiedTime(p);
    assertTrue(outputs.filesUnchanged(outputs.read(f)));

    // the same size at another time
    Files.setLastModifiedTime(p, FileTime.fromMillis(time.toMillis() + 2000));
    assertFalse(outputs.filesUnchanged(outputs.read(f)));
    Files.setLastModifiedTime(p, time);
    assertTrue(outputs.filesUnchanged(outputs.read(f)));

    // another size at the same time
    Files.write(p, bytes("{a english}"));
    Files.setLastModifiedTime(p, time);
    assertFalse(outputs.filesUnchanged(outputs.read(f)));
    Files.write(p, bytes("{a en}"));
    Files.setLastModifiedTime(p, time);
    assertTrue(outputs.filesUnchanged(outputs.read(f)));

    Files.delete(p);
    assertFalse(outputs.filesUnchanged(outputs.read(f)));
    Files.createDirectory(p);
    assertFalse(outputs.filesUnchanged(outputs.read(f)));
  }

  @Test
  void aRecordThatIsCutShortOrNotOneIsNotRead() throws Exception {
    RapidoNativeOutputs outputs = new RapidoNativeOutputs(temp.toString());
    FetchedFile f = new FetchedFile("input/a.json");
    outputs.save(f, record(outputs));
    Path file = recordFiles().get(0);
    byte[] all = Files.readAllBytes(file);
    for (int length = 0; length < all.length; length++) {
      Files.write(file, Arrays.copyOf(all, length));
      assertNull(outputs.read(f), "cut to " + length + " of " + all.length);
    }
    Files.write(file, Arrays.copyOf(all, all.length + 1));
    assertNull(outputs.read(f), "more than a record");
    byte[] other = all.clone();
    other[3]++;
    Files.write(file, other);
    assertNull(outputs.read(f), "another version");
    // a length that isn't one
    other = all.clone();
    int at = 4 + 4 + bytes("input/a.json").length + 4; // version, path, resource count: then the first key's length
    other[at] = 0x7f;
    Files.write(file, other);
    assertNull(outputs.read(f), "a length past the end");
    Files.write(file, all);
    assertNotNull(outputs.read(f));
  }

  @Test
  void filesLoadedFromTheSamePlaceCanBeSavedAtOnce() throws Exception {
    RapidoNativeOutputs outputs = new RapidoNativeOutputs(temp.toString());
    FileRecord one = record(outputs);
    FileRecord other = record(outputs);
    other.resources.get(0).entries.clear();
    List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
    List<Thread> threads = new ArrayList<>();
    for (FileRecord rec : List.of(one, other)) {
      Thread t = new Thread(() -> {
        try {
          for (int i = 0; i < 300; i++) {
            outputs.save(new FetchedFile("input/a.json"), rec);
          }
        } catch (Throwable e) {
          failures.add(e);
        }
      });
      threads.add(t);
      t.start();
    }
    for (Thread t : threads) {
      t.join();
    }
    assertEquals(List.of(), failures);
    // the record is one of the two, whole
    FileRecord back = outputs.read(new FetchedFile("input/a.json"));
    assertNotNull(back);
    assertTrue(outputs.filesUnchanged(back));
    assertEquals(1, recordFiles().size());
  }

  @Test
  void noRecordIsKeptOfWhatItCantHold() throws Exception {
    RapidoNativeOutputs outputs = new RapidoNativeOutputs(temp.toString());
    FetchedFile f = new FetchedFile("input/a.json");

    FileRecord rec = record(outputs);
    rec.unsupported();
    outputs.save(f, rec);
    assertNull(outputs.read(f));

    // a resource the step didn't get to the end of
    rec = record(outputs);
    rec.resources.get(1).json = null;
    outputs.save(f, rec);
    assertNull(outputs.read(f));

    // a file outside the temp folder
    rec = record(outputs);
    outputs.wrote(rec, temp.getParent().resolve("elsewhere.json").toString(), true);
    assertTrue(rec.isUnsupported());

    // a file that isn't there to compare with later
    rec = record(outputs);
    outputs.wrote(rec, temp.resolve("Basic-c.json").toString(), true);
    outputs.save(f, rec);
    assertNull(outputs.read(f));

    outputs.save(f, record(outputs));
    assertNotNull(outputs.read(f));
  }

  private static Element parse(String json) throws Exception {
    return new JsonParser(context).parseSingle(new ByteArrayInputStream(bytes(json)), null);
  }

  private static String key(Element e) {
    Hasher h = new Hasher();
    RapidoNativeOutputs.contentKey(h, e);
    return h.finish();
  }

  private static final String BASIC = "{\"resourceType\":\"Basic\",\"id\":\"a\",\"code\":\"c\",\"subject\":[\"one\",\"two\"],\"div\":\"<div xmlns=\\\"http://www.w3.org/1999/xhtml\\\"><p class=\\\"x\\\">text</p></div>\"}";

  @Test
  void theKeyIsTheSameForTheSameContent() throws Exception {
    assertEquals(key(parse(BASIC)), key(parse(BASIC)));
    assertEquals(key(parse(BASIC)), key((Element) parse(BASIC).copy(org.hl7.fhir.model.Base.COPY_DATA)));
  }

  @Test
  void theKeyChangesWithAnythingThatIsWritten() throws Exception {
    String key = key(parse(BASIC));
    assertNotEquals(key, key(parse(BASIC.replace("\"c\"", "\"d\""))), "a value");
    assertNotEquals(key, key(parse(BASIC.replace("\"one\",\"two\"", "\"two\",\"one\""))), "the order");
    assertNotEquals(key, key(parse(BASIC.replace("\"one\",\"two\"", "\"one\""))), "an item less");
    assertNotEquals(key, key(parse(BASIC.replace("\"one\",\"two\"", "\"onetwo\""))), "two values in one");
    assertNotEquals(key, key(parse(BASIC.replace(">text<", ">other text<"))), "the narrative's text");
    assertNotEquals(key, key(parse(BASIC.replace("class=\\\"x\\\"", "class=\\\"y\\\""))), "an attribute in the narrative");

    Element e = parse(BASIC);
    e.getNamedChild("code").getComments().add("a comment");
    assertNotEquals(key, key(e), "a comment");

    e = parse(BASIC);
    e.getNamedChild("code").setValue(null);
    assertNotEquals(key, key(e), "no value");

    // the narrative as a tree that was changed after parsing (as narrative generation and the language copies do)
    e = parse(BASIC);
    XhtmlNode div = e.getNamedChild("div").getXhtml();
    assertNotNull(div);
    div.getChildNodes().get(0).setAttribute("class", "z");
    assertNotEquals(key, key(e), "the narrative tree");

    e = parse(BASIC);
    e.getNamedChild("div").setXhtml(new XhtmlParser().parseFragment("<div xmlns=\"http://www.w3.org/1999/xhtml\"><p class=\"x\">text</p><p>more</p></div>"));
    assertNotEquals(key, key(e), "another narrative tree");
  }
}
