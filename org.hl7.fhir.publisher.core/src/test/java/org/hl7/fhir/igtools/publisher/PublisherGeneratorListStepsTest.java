package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.formats.JsonParser;
import org.hl7.fhir.model.extensions.ExtensionDefinitions;
import org.hl7.fhir.model.utilities.formats.FhirFormat;
import org.hl7.fhir.services.context.ContextUtilities;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.services.elementmodel.Manager;
import org.hl7.fhir.standalone.context.SimpleWorkerContext;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The lists of the IG's resources of each type are the same in every build until what they show of the resources
 * changes: a Rapido differential build only makes them again then, or when the files aren't as the last build's lists
 * left them
 */
class PublisherGeneratorListStepsTest {

  private static SimpleWorkerContext context;
  private static String noContext;

  @BeforeAll
  static void loadContext() throws Exception {
    try {
      NpmPackage npm = new FilesystemPackageCacheManager.Builder().build().loadPackage("hl7.fhir.r5.core", "5.0.0");
      context = new SimpleWorkerContext.SimpleWorkerContextBuilder(ModelContext.fullCoreContext()).withAllowLoadingDuplicates(true).fromPackage(npm);
    } catch (IOException | FHIRException e) {
      noContext = "hl7.fhir.r5.core#5.0.0 can't be fetched: " + e;
    }
  }

  @TempDir
  Path dir;

  private static final String CODES = "{\"resourceType\":\"CodeSystem\",\"id\":\"codes\",\"extension\":[{\"url\":\"" + ExtensionDefinitions.EXT_FMM_LEVEL
      + "\",\"valueInteger\":2}],\"url\":\"http://example.org/CodeSystem/codes\",\"version\":\"1.0.0\",\"name\":\"Codes\",\"title\":\"Some codes\","
      + "\"status\":\"draft\",\"date\":\"2025-01-02\",\"publisher\":\"Example Org\",\"contact\":[{\"telecom\":[{\"system\":\"url\",\"value\":\"http://example.org/contact\"}]}],"
      + "\"description\":\"The **codes**\",\"copyright\":\"Uses LOINC\",\"content\":\"complete\",\"concept\":[{\"code\":\"a\"}]}";
  private static final String OTHER = "{\"resourceType\":\"CodeSystem\",\"id\":\"other\",\"url\":\"http://example.org/CodeSystem/other\",\"name\":\"Other\","
      + "\"status\":\"active\",\"content\":\"complete\"}";
  private static final String PROFILE = "{\"resourceType\":\"StructureDefinition\",\"id\":\"profile\",\"extension\":[{\"url\":\"" + ExtensionDefinitions.EXT_FMM_LEVEL
      + "\",\"valueInteger\":1}],\"url\":\"http://example.org/StructureDefinition/profile\",\"name\":\"Profile\",\"title\":\"A profile\",\"status\":\"draft\","
      + "\"description\":\"Of a patient\",\"kind\":\"resource\",\"abstract\":false,\"type\":\"Patient\","
      + "\"baseDefinition\":\"http://hl7.org/fhir/StructureDefinition/Patient\",\"derivation\":\"constraint\"}";
  /** not a type: the lists of the profiles */
  private static final String PROFILES = "(profiles)";
  private static final String EXTENSIONS = "(extensions)";
  private static final String LOGICALS = "(logicals)";
  private static final String EXTENSION = "{\"resourceType\":\"StructureDefinition\",\"id\":\"ext\",\"url\":\"http://example.org/StructureDefinition/ext\","
      + "\"name\":\"Ext\",\"title\":\"An extension\",\"status\":\"draft\",\"kind\":\"complex-type\",\"abstract\":false,\"type\":\"Extension\","
      + "\"baseDefinition\":\"http://hl7.org/fhir/StructureDefinition/Extension\",\"derivation\":\"constraint\"}";
  private static final String LOGICAL = "{\"resourceType\":\"StructureDefinition\",\"id\":\"model\",\"url\":\"http://example.org/StructureDefinition/model\","
      + "\"name\":\"Model\",\"title\":\"A model\",\"status\":\"draft\",\"kind\":\"logical\",\"abstract\":false,\"type\":\"http://example.org/StructureDefinition/model\","
      + "\"baseDefinition\":\"http://hl7.org/fhir/StructureDefinition/Base\",\"derivation\":\"specialization\"}";
  private static final String PLACE = "{\"resourceType\":\"Location\",\"id\":\"place\",\"name\":\"A place\",\"description\":\"Where it is\"}";

  /** A resource as the loader leaves it */
  private static class Res {
    private String json;
    private String title; // the FetchedResource's
    private String fileTitle;
    private String linkBase;
    private boolean elementOnly; // as an example is: not loaded as a resource

    private Res(String json, String title, String fileTitle) {
      this.json = json;
      this.title = title;
      this.fileTitle = fileTitle;
    }

    private void replace(String from, String to) {
      assertTrue(json.contains(from), from);
      json = json.replace(from, to);
    }
  }

  private static List<Res> resources() {
    List<Res> res = new ArrayList<>();
    res.add(new Res(CODES, "B codes", "codes file"));
    res.add(new Res(OTHER, "A other", "other file"));
    res.add(new Res(PLACE, "Place", "place file"));
    res.add(new Res(PROFILE, "Profile", "profile file"));
    return res;
  }

  /** The part of a build that makes the lists of the resources of a type, in the folder given */
  private class Build {
    private final PublisherFields pf = new PublisherFields();
    private final PublisherGenerator generator = new PublisherGenerator(new PublisherSettings());
    private final Path includes;

    /** lastSteps: the steps of the build whose state this one reuses (a differential build); null for a complete build */
    private Build(String folder, List<Res> resources, JsonObject lastSteps) throws Exception {
      assumeTrue(context != null, noContext);
      pf.tempDir = dir.resolve(folder).resolve("temp").toString();
      pf.qaDir = dir.resolve(folder).resolve("qa").toString();
      Files.createDirectories(dir.resolve(folder).resolve("qa"));
      includes = dir.resolve(folder).resolve("temp").resolve("_includes");
      pf.context = context;
      pf.version = "5.0.0";
      pf.cu = new ContextUtilities(context);
      pf.markdownEngine = new MarkDownProcessor(MarkDownProcessor.Dialect.COMMON_MARK);
      pf.igpkp = mock(IGKnowledgeProvider.class, CALLS_REAL_METHODS);
      pf.defaultTranslationLang = "en";
      pf.rapido = new RapidoTracker(pf);
      pf.rapido.getSteps().load(lastSteps);
      generator.setPf(pf);
      for (Res res : resources) {
        byte[] bytes = res.json.getBytes(StandardCharsets.UTF_8);
        Element e = Manager.parseSingle(context, new ByteArrayInputStream(bytes), FhirFormat.JSON);
        FetchedFile f = new FetchedFile(e.fhirType() + "-" + e.getIdBase() + ".json");
        f.setTitle(res.fileTitle);
        FetchedResource r = f.addResource(f.getName()).setElement(e).setId(e.getIdBase()).setTitle(res.title);
        if (!res.elementOnly) {
          r.setResource(new JsonParser(context.getModelContext()).parse(bytes));
        }
        if (res.linkBase != null) {
          JsonObject config = new JsonObject();
          config.add("base", res.linkBase);
          r.setConfig(config);
        }
        pf.fileList.add(f);
      }
    }

    private Build lists(String type) throws Exception {
      return lists(type, "en");
    }

    private Build lists(String type, String lang) throws Exception {
      if (PROFILES.equals(type)) {
        generator.generateProfiles(lang);
      } else if (EXTENSIONS.equals(type)) {
        generator.generateExtensions(lang);
      } else if (LOGICALS.equals(type)) {
        generator.generateLogicals(lang);
      } else {
        generator.generateResourceReferences(type, lang);
      }
      return this;
    }

    private RapidoSteps steps() {
      return pf.rapido.getSteps();
    }

    private String key(String type) {
      return steps().save().getJsonObject("lists/en/" + type).asString("key");
    }

    /** the fragments, by name */
    private Map<String, String> fragments() throws IOException {
      Map<String, String> res = new TreeMap<>();
      try (Stream<Path> s = Files.list(includes)) {
        for (Path p : (Iterable<Path>) s::iterator) {
          res.put(p.getFileName().toString(), Files.readString(p));
        }
      }
      return res;
    }

    /** when each fragment was written */
    private Map<String, FileTime> written() throws IOException {
      Map<String, FileTime> res = new TreeMap<>();
      try (Stream<Path> s = Files.list(includes)) {
        for (Path p : (Iterable<Path>) s::iterator) {
          res.put(p.getFileName().toString(), Files.getLastModifiedTime(p));
        }
      }
      return res;
    }

    /** what the fragments made count for in the fragment usage report */
    private Map<String, String> uses() {
      Map<String, String> res = new TreeMap<>();
      for (Map.Entry<String, PublisherBase.FragmentUseRecord> e : pf.fragmentUses.entrySet()) {
        res.put(e.getKey(), e.getValue().count + "/" + e.getValue().size);
      }
      return res;
    }
  }

  @Test
  void listsWhoseResourcesShowTheSameAreNotMadeAgain() throws Exception {
    Build first = new Build("ig", resources(), null).lists("CodeSystem").lists("Location");
    assertEquals(2, first.steps().getRun());
    assertTrue(first.fragments().get("table-grid-codesystems-en.xhtml").contains("Some codes"));
    Map<String, FileTime> written = first.written();

    // a concept isn't in the lists
    List<Res> changed = resources();
    changed.get(0).replace("{\"code\":\"a\"}", "{\"code\":\"a\"},{\"code\":\"b\"}");
    Build second = new Build("ig", changed, first.steps().save()).lists("CodeSystem").lists("Location");

    assertEquals(0, second.steps().getRun());
    assertEquals(2, second.steps().getReused());
    assertEquals(written, second.written());
    // the rest of the build sees what it would if they had been made
    assertEquals(first.pf.otherFilesRun, second.pf.otherFilesRun);
    assertEquals(first.pf.allOutputs, second.pf.allOutputs);
    assertEquals(first.uses(), second.uses());
    assertFalse(second.pf.otherFilesRun.isEmpty());

    // and the next build can reuse them in turn
    Build third = new Build("ig", changed, second.steps().save()).lists("CodeSystem").lists("Location");
    assertEquals(2, third.steps().getReused());
    assertEquals(written, third.written());
  }

  static Stream<Arguments> shown() {
    return Stream.of(
        shown("id", "CodeSystem", l -> l.get(0).replace("\"id\":\"codes\"", "\"id\":\"codez\"")),
        shown("version", "CodeSystem", l -> l.get(0).replace("1.0.0", "1.0.1")),
        shown("title", "CodeSystem", l -> l.get(0).replace("Some codes", "Any codes")),
        shown("name, without a title", "CodeSystem", l -> l.get(1).replace("\"name\":\"Other\"", "\"name\":\"Another\"")),
        shown("status", "CodeSystem", l -> l.get(0).replace("\"draft\"", "\"active\"")),
        shown("standards status", "CodeSystem", l -> l.get(0).replace("\"extension\":[", "\"extension\":[{\"url\":\""
            + ExtensionDefinitions.EXT_STANDARDS_STATUS + "\",\"valueCode\":\"informative\"},")),
        shown("work group", "CodeSystem", l -> l.get(0).replace("\"extension\":[", "\"extension\":[{\"url\":\""
            + ExtensionDefinitions.EXT_WORKGROUP + "\",\"valueCode\":\"fhir\"},")),
        shown("date", "CodeSystem", l -> l.get(0).replace("2025-01-02", "2025-01-03")),
        shown("publisher", "CodeSystem", l -> l.get(0).replace("Example Org", "HL7")),
        shown("contact", "CodeSystem", l -> l.get(0).replace("http://example.org/contact", "http://example.org/us")),
        shown("description", "CodeSystem", l -> l.get(0).replace("The **codes**", "The *codes*")),
        shown("copyright", "CodeSystem", l -> l.get(0).replace("Uses LOINC", "Uses DICOM")),
        shown("copyright label", "CodeSystem", l -> l.get(0).replace("\"content\"", "\"copyrightLabel\":\"LNC\",\"content\"")),
        shown("content", "CodeSystem", l -> l.get(0).replace("\"content\":\"complete\"", "\"content\":\"fragment\"")),
        shown("the title it is sorted by", "CodeSystem", l -> l.get(0).title = "0 codes"),
        shown("the title given for want of a description", "CodeSystem", l -> l.get(1).title = "The other"),
        shown("no title of its own", "CodeSystem", l -> l.get(1).title = null),
        // the title a resource has without one of its own is its type and id
        shown("no title of its own, with the same title", "CodeSystem", l -> l.get(1).title = null, l -> l.get(1).title = "CodeSystem/other"),
        shown("its file's title", "CodeSystem", l -> {
          l.get(1).title = null;
          l.get(1).fileTitle = "another file";
        }, l -> l.get(1).title = null),
        shown("where its page is", "CodeSystem", l -> l.get(0).linkBase = "cs/{{[id]}}.html"),
        shown("one more", "CodeSystem", l -> l.add(new Res(OTHER.replace("other", "third"), "Third", "third file"))),
        shown("their order", "CodeSystem", l -> l.add(l.remove(0))),
        shown("description of a resource that isn't canonical", "Location", l -> l.get(2).replace("Where it is", "Where it was")),
        shown("title of a resource that isn't canonical", "Location", l -> l.get(2).title = "Somewhere"),
        shown("title of a profile", PROFILES, l -> l.get(3).replace("A profile", "The profile")),
        shown("description of a profile", PROFILES, l -> l.get(3).replace("Of a patient", "Of patients")),
        shown("maturity of a profile", PROFILES, l -> l.get(3).replace("\"valueInteger\":1", "\"valueInteger\":3")),
        shown("description of a resource that isn't loaded as one", "Location", l -> l.get(2).replace("Where it is", "Where it was"), l -> l.get(2).elementOnly = true),
        shown("a description, for a resource that isn't loaded as one", "Location", l -> l.get(2).replace("\"name\"", "\"description\":\"Where\",\"name\""), l -> {
          l.get(2).elementOnly = true;
          l.get(2).replace(",\"description\":\"Where it is\"", "");
        }),
        shown("title of an extension", EXTENSIONS, l -> l.get(4).replace("An extension", "The extension"), l -> l.add(new Res(EXTENSION, "Ext", "ext file"))),
        shown("the first extension", EXTENSIONS, l -> l.add(new Res(EXTENSION, "Ext", "ext file"))),
        shown("title of a logical model", LOGICALS, l -> l.get(4).replace("A model", "The model"), l -> l.add(new Res(LOGICAL, "Model", "model file"))),
        shown("the first logical model", LOGICALS, l -> l.add(new Res(LOGICAL, "Model", "model file"))),
        shown("one more profile", PROFILES, l -> l.add(new Res(PROFILE.replace("\"id\":\"profile\"", "\"id\":\"second\""), "Second", "second file"))));
  }

  private static Arguments shown(String what, String type, Consumer<List<Res>> change) {
    return shown(what, type, change, l -> { });
  }

  private static Arguments shown(String what, String type, Consumer<List<Res>> change, Consumer<List<Res>> before) {
    return Arguments.of(what, type, change, before);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("shown")
  void whatTheListsShowIsInTheirKey(String what, String type, Consumer<List<Res>> change, Consumer<List<Res>> before) throws Exception {
    List<Res> was = resources();
    before.accept(was);
    List<Res> now = resources();
    before.accept(now);
    change.accept(now);
    Build a = new Build("a", was, null).lists(type);
    Build b = new Build("b", now, null).lists(type);
    // (the change is one the lists show)
    assertNotEquals(a.fragments(), b.fragments(), what);

    assertNotEquals(a.key(type), b.key(type), what);

    // and a build after the change makes them as a complete build does
    Build next = new Build("a", now, a.steps().save()).lists(type);
    assertEquals(1, next.steps().getRun());
    assertEquals(b.fragments(), next.fragments());
  }

  @Test
  void listsWrittenOverAreMadeAgain() throws Exception {
    Build first = new Build("ig", resources(), null).lists("CodeSystem");
    Map<String, String> fragments = first.fragments();
    Files.writeString(first.includes.resolve("list-codesystems-en.xhtml"), "something else");

    Build second = new Build("ig", resources(), first.steps().save()).lists("CodeSystem");

    assertEquals(1, second.steps().getRun());
    assertEquals(fragments, second.fragments());
  }

  @Test
  void aListWhoseCopyWithoutTheLanguageIsGoneIsMadeAgain() throws Exception {
    Build first = new Build("ig", resources(), null).lists("CodeSystem");
    Map<String, String> fragments = first.fragments();
    assertEquals(fragments.get("list-codesystems-en.xhtml"), fragments.get("list-codesystems.xhtml"));
    Files.delete(first.includes.resolve("list-codesystems.xhtml"));

    Build second = new Build("ig", resources(), first.steps().save()).lists("CodeSystem");

    assertEquals(1, second.steps().getRun());
    assertEquals(fragments, second.fragments());
  }

  @Test
  void aCompleteBuildMakesTheLists() throws Exception {
    Build first = new Build("ig", resources(), null).lists("CodeSystem");
    Map<String, String> fragments = first.fragments();
    Files.writeString(first.includes.resolve("list-codesystems-en.xhtml"), "something else");
    // (and the file is as old as the first build left it)
    Files.setLastModifiedTime(first.includes.resolve("list-codesystems-en.xhtml"), first.written().get("list-codesystems-xml-en.xhtml"));

    Build second = new Build("ig", resources(), null).lists("CodeSystem");

    assertEquals(1, second.steps().getRun());
    assertEquals(fragments, second.fragments());
  }

  @Test
  void aListThatIsAnOutputAlreadyIsReportedAsItWouldBe() throws Exception {
    Build first = new Build("ig", resources(), null).lists("CodeSystem");
    Build second = new Build("ig", resources(), first.steps().save());
    second.pf.allOutputs.add(second.includes.resolve("list-codesystems-en.xhtml").toString().toLowerCase());

    Error e = assertThrows(Error.class, () -> second.lists("CodeSystem"));

    assertTrue(e.getMessage().contains("generated more than once"), e.getMessage());
    assertNull(second.steps().save().getJsonObject("lists/en/CodeSystem"));
  }

  @Test
  void listsThatCouldNotBeMadeAreNotRecorded() throws Exception {
    Build first = new Build("ig", resources(), null);
    first.pf.igpkp = mock(IGKnowledgeProvider.class); // no resource has a page: the lists can't link to them

    assertThrows(Exception.class, () -> first.lists("CodeSystem"));

    assertNull(first.steps().save().getJsonObject("lists/en/CodeSystem"));
    assertFalse(first.steps().isRecording());
  }

  @Test
  void anUnchangedCopyWithoutTheLanguageIsNotWrittenAgain() throws Exception {
    Build first = new Build("ig", resources(), null).lists("CodeSystem");
    Path copy = first.includes.resolve("list-codesystems.xhtml");
    FileTime old = FileTime.fromMillis(1_000_000_000_000L);
    Files.setLastModifiedTime(copy, old);
    Path changed = first.includes.resolve("table-grid-codesystems.xhtml");
    Files.writeString(changed, "something else");
    Files.setLastModifiedTime(changed, old);

    // a complete build: every list is made
    Build second = new Build("ig", resources(), null).lists("CodeSystem");

    assertEquals(old, Files.getLastModifiedTime(copy));
    assertNotEquals(old, Files.getLastModifiedTime(changed));
    assertEquals(first.fragments().get("table-grid-codesystems-en.xhtml"), second.fragments().get("table-grid-codesystems.xhtml"));
    assertNotNull(second.steps().save().getJsonObject("lists/en/CodeSystem"));
  }

  @Test
  void aCopyOfTheSameSizeWithOtherContentIsWrittenAgain() throws Exception {
    Build first = new Build("ig", resources(), null).lists("CodeSystem");
    Path copy = first.includes.resolve("list-codesystems.xhtml");
    String content = Files.readString(copy);
    Files.writeString(copy, content.replace('a', 'b'));

    new Build("ig", resources(), null).lists("CodeSystem");

    assertEquals(content, Files.readString(copy));
  }

  @Test
  void aBuildWithoutRapidoMakesTheLists() throws Exception {
    Build rapido = new Build("a", resources(), null).lists("CodeSystem").lists(PROFILES).lists(EXTENSIONS).lists(LOGICALS);
    Build plain = new Build("b", resources(), null);
    plain.pf.rapido = null;

    plain.lists("CodeSystem").lists(PROFILES).lists(EXTENSIONS).lists(LOGICALS);

    assertEquals(rapido.fragments(), plain.fragments());
    assertEquals(rapido.uses(), plain.uses());
  }

  @Test
  void aStepThatReportsMessagesIsNotRecorded() throws Exception {
    Build build = new Build("ig", resources(), null);

    build.generator.fragmentStep("step", "key", () -> build.pf.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher,
        ValidationMessage.IssueType.NOTFOUND, "here", "not found", ValidationMessage.IssueSeverity.WARNING)));

    assertNull(build.steps().save().getJsonObject("step"));
    assertEquals(1, build.pf.errors.size());
  }

  @Test
  void aStepInsideAStepIsPartOfIt() throws Exception {
    Build first = new Build("ig", resources(), null);
    first.generator.fragmentStep("outer", "key", () -> first.lists("CodeSystem"));
    assertNotNull(first.steps().save().getJsonObject("outer"));
    assertNull(first.steps().save().getJsonObject("lists/en/CodeSystem"));
    assertFalse(first.steps().save().getJsonObject("outer").getJsonArray("fragments").getItems().isEmpty());

    Build second = new Build("ig", resources(), first.steps().save());
    second.generator.fragmentStep("outer", "key", () -> second.lists("CodeSystem"));

    assertEquals(1, second.steps().getReused());
    assertEquals(first.pf.otherFilesRun, second.pf.otherFilesRun);
  }

  @Test
  void listsInNoLanguageOrAnotherThanTheDefaultHaveNoCopy() throws Exception {
    Build first = new Build("ig", resources(), null).lists("CodeSystem", null).lists("Location", "fr");
    assertTrue(first.fragments().containsKey("list-codesystems.xhtml"));
    assertTrue(first.fragments().containsKey("list-locations-fr.xhtml"));
    assertFalse(first.fragments().containsKey("list-locations.xhtml"));
    Map<String, FileTime> written = first.written();

    Build second = new Build("ig", resources(), first.steps().save()).lists("CodeSystem", null).lists("Location", "fr");

    assertEquals(2, second.steps().getReused());
    assertEquals(written, second.written());
    assertEquals(first.pf.otherFilesRun, second.pf.otherFilesRun);

    // now French is the default language: its lists get a copy
    Build third = new Build("ig", resources(), second.steps().save());
    third.pf.defaultTranslationLang = "fr";
    third.lists("Location", "fr");
    assertEquals(1, third.steps().getRun());
    assertEquals(third.fragments().get("list-locations-fr.xhtml"), third.fragments().get("list-locations.xhtml"));

    // and when it no longer is, they are made again as well (the copy isn't theirs to count on)
    Build fourth = new Build("ig", resources(), third.steps().save()).lists("Location", "fr");
    assertEquals(1, fourth.steps().getRun());
  }

  @Test
  void listsOfNothingAreReusedToo() throws Exception {
    Build first = new Build("ig", resources(), null).lists("Patient").lists(EXTENSIONS).lists(LOGICALS);
    assertEquals(3, first.steps().getRun());

    Build second = new Build("ig", resources(), first.steps().save()).lists("Patient").lists(EXTENSIONS).lists(LOGICALS);

    assertEquals(3, second.steps().getReused());
    assertEquals(first.pf.otherFilesRun, second.pf.otherFilesRun);

    // the first patient: its lists are made
    List<Res> more = resources();
    more.add(new Res("{\"resourceType\":\"Patient\",\"id\":\"p\"}", "Pat", "patient file"));
    Build third = new Build("ig", more, second.steps().save()).lists("Patient");
    assertEquals(1, third.steps().getRun());
    assertTrue(third.fragments().get("list-patients-en.xhtml").contains("Patient-p.html"));
  }
}
