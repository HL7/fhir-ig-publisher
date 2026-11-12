package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.templates.Template;
import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.ImplementationGuide;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.utilities.formats.FhirFormat;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.services.elementmodel.Manager;
import org.hl7.fhir.standalone.context.SimpleWorkerContext;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A -rapido build writes the translation artifacts (translations/[lang]/po|xliff|json) of a file again only if the
 * file, or something it depends on, changed, or the artifacts aren't as the last build wrote them. A build that isn't
 * differential writes them all
 */
class RapidoTranslationsTest {

  private static FilesystemPackageCacheManager pcm;
  private static SimpleWorkerContext context;
  private static String noContext;

  @BeforeAll
  static void loadContext() throws Exception {
    try {
      pcm = new FilesystemPackageCacheManager.Builder().build();
      context = new SimpleWorkerContext.SimpleWorkerContextBuilder(ModelContext.fullCoreContext()).withAllowLoadingDuplicates(true)
          .fromPackage(pcm.loadPackage("hl7.fhir.r5.core", "5.0.0"));
    } catch (IOException | FHIRException e) {
      noContext = "hl7.fhir.r5.core#5.0.0 can't be fetched: " + e;
    }
  }

  @TempDir
  Path dir;

  private final String uuid = HierarchicalTableGenerator.uuid;
  private List<String> langs = List.of("ru", "uz");
  private String defaultLang = "en";
  private boolean withProfile;

  @AfterEach
  void restoreUuid() {
    HierarchicalTableGenerator.uuid = uuid;
  }

  /** the artifacts of each language, and when each was written */
  private Map<String, FileTime> artifacts() throws IOException {
    Map<String, FileTime> res = new TreeMap<>();
    Path root = dir.resolve("translations");
    try (Stream<Path> s = Files.walk(root)) {
      for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
        res.put(root.relativize(p).toString().replace('\\', '/'), Files.getLastModifiedTime(p));
      }
    }
    return res;
  }

  private static List<String> written(Map<String, FileTime> before, Map<String, FileTime> after) {
    List<String> res = new ArrayList<>();
    for (Map.Entry<String, FileTime> e : after.entrySet()) {
      if (!e.getValue().equals(before.get(e.getKey()))) {
        res.add(e.getKey());
      }
    }
    return res;
  }

  private static final List<String> A = List.of("ru/json/Patient-a.json", "ru/po/Patient-a.po", "ru/xliff/Patient-a.xliff",
      "uz/json/Patient-a.json", "uz/po/Patient-a.po", "uz/xliff/Patient-a.xliff");
  private static final List<String> B = List.of("ru/json/Patient-b.json", "ru/po/Patient-b.po", "ru/xliff/Patient-b.xliff",
      "uz/json/Patient-b.json", "uz/po/Patient-b.po", "uz/xliff/Patient-b.xliff");
  private static final List<String> O = List.of("ru/json/Observation-o.json", "ru/po/Observation-o.po", "ru/xliff/Observation-o.xliff",
      "uz/json/Observation-o.json", "uz/po/Observation-o.po", "uz/xliff/Observation-o.xliff");

  @SafeVarargs
  private static List<String> all(List<String>... lists) {
    List<String> res = new ArrayList<>();
    for (List<String> l : lists) {
      res.addAll(l);
    }
    res.sort(null);
    return res;
  }

  @Test
  void onlyTheArtifactsOfWhatChangedAreWrittenAgain() throws Exception {
    Map<String, FileTime> none = new TreeMap<>();
    Map<String, FileTime> first = translate(build("Alice", true));
    assertEquals(all(A, B, O), written(none, first), "a complete build writes them all");
    assertTrue(Files.readString(dir.resolve("translations/ru/po/Patient-a.po")).contains("msgid \"Alice\""));

    // nothing changed
    Map<String, FileTime> second = translate(build("Alice", true));
    assertEquals(List.of(), written(first, second));

    // the patient changed: the observation that refers to it is done again as well, the other patient isn't
    Map<String, FileTime> third = translate(build("Bob", true));
    assertEquals(all(A, O), written(second, third));
    assertTrue(Files.readString(dir.resolve("translations/ru/po/Patient-a.po")).contains("msgid \"Bob\""));

    // and what was reused is still on record: nothing to do in the next build
    Map<String, FileTime> fourth = translate(build("Bob", true));
    assertEquals(List.of(), written(third, fourth));
  }

  @Test
  void artifactsThatAreNotAsTheyWereWrittenAreWrittenAgain() throws Exception {
    Map<String, FileTime> first = translate(build("Alice", true));
    Path gone = dir.resolve("translations/ru/po/Patient-b.po");
    Path edited = dir.resolve("translations/uz/json/Patient-a.json");
    String content = Files.readString(edited);
    Files.delete(gone);
    Files.delete(dir.resolve("translations/uz/xliff/Observation-o.xliff"));
    Files.writeString(edited, content.replace("Alice", "Alisa"));

    Map<String, FileTime> second = translate(build("Alice", true));

    assertEquals(all(A, B, O), written(first, second), "each file's artifacts are written together");
    assertEquals(content, Files.readString(edited));
  }

  @Test
  void aBuildWithoutRapidoWritesThemAll() throws Exception {
    Map<String, FileTime> first = translate(build("Alice", true));
    Map<String, FileTime> second = translate(build("Alice", false));
    assertEquals(all(A, B, O), written(first, second));

    // and leaves no record: the rapido build that follows it writes over what it wrote
    Map<String, FileTime> third = translate(build("Alice", true));
    assertEquals(all(A, B, O), written(second, third));
  }

  @Test
  void aCompleteBuildWritesThemAll() throws Exception {
    Map<String, FileTime> first = translate(build("Alice", true));
    // as after a build that failed
    setBuildComplete(false);

    PublisherFields pf = build("Alice", true);
    assertTrue(pf.rapido.isCompleteBuild());
    assertEquals(all(A, B, O), written(first, translate(pf)));
  }

  @Test
  void withOneLanguageTheArtifactsOfWhatDidntChangeAreKept() throws Exception {
    // (the default translation language is one of them)
    langs = List.of();
    defaultLang = "ru";
    Map<String, FileTime> first = translate(build("Alice", true));
    assertEquals(List.of("json/Patient-a.json", "po/Patient-a.po", "xliff/Patient-a.xliff"),
        first.keySet().stream().filter(k -> k.contains("Patient-a")).toList(), "no language folder");

    assertEquals(List.of(), written(first, translate(build("Alice", true))));
  }

  @Test
  void theArtifactsOfAProfileAreKeptOnlyIfTheyAreAsTheyWereWritten() throws Exception {
    // a profile's are made from the resource, not the element
    withProfile = true;
    List<String> p = List.of("ru/json/StructureDefinition-p.json", "ru/po/StructureDefinition-p.po", "ru/xliff/StructureDefinition-p.xliff",
        "uz/json/StructureDefinition-p.json", "uz/po/StructureDefinition-p.po", "uz/xliff/StructureDefinition-p.xliff");
    Map<String, FileTime> first = translate(build("Alice", true));
    assertTrue(first.keySet().containsAll(p), first.keySet().toString());
    Map<String, FileTime> second = translate(build("Alice", true));
    assertEquals(List.of(), written(first, second));

    Files.delete(dir.resolve("translations/uz/xliff/StructureDefinition-p.xliff"));
    assertEquals(p, written(second, translate(build("Alice", true))));
  }

  /** The producers are core's: an artifact that isn't where it was expected to be written has no stamp, and isn't reused */
  @Test
  void artifactsThatAreNotWhereTheyAreExpectedAreNotReused() throws Exception {
    PublisherFields pf = build("Alice", true);
    pf.rapido.recordTranslations(pf.fileList.get(2), List.of(dir.resolve("translations/ru/po/Patient-b.po").toString()));
    finish(pf);

    pf = build("Alice", true);
    assertFalse(pf.rapido.isCompleteBuild(), pf.rapido.getReason());
    assertFalse(pf.rapido.reuseTranslations(pf.fileList.get(2)));
  }

  /** what the build does between checkDependencies and saving the state, as far as -rapido and the translations go */
  private Map<String, FileTime> translate(PublisherFields pf) throws Exception {
    PublisherProcessor processor = new PublisherProcessor(new PublisherSettings());
    processor.setPf(pf);
    processor.processTranslationOutputs();
    if (pf.rapido != null) {
      finish(pf);
    }
    return artifacts();
  }

  private void finish(PublisherFields pf) throws Exception {
    pf.rapido.finishValidation();
    for (FetchedFile f : pf.fileList) {
      for (String phase : new String[] {RapidoTracker.PHASE_HTML, RapidoTracker.PHASE_SHEETS}) {
        if (pf.rapido.canReplay(f, phase)) {
          f.getOutputNames().addAll(pf.rapido.replay(f, phase).outputs);
        } else {
          pf.rapido.startRecording(f, phase);
          if (RapidoTracker.PHASE_HTML.equals(phase)) {
            // each file writes a page in the temp folder
            Path page = dir.resolve("temp").resolve(f.getLoadPath().replace(".json", ".html"));
            Files.createDirectories(page.getParent());
            Files.writeString(page, "<p>" + f.getLoadPath() + "</p>");
            f.getOutputNames().add(page.toString());
          }
          pf.rapido.stopRecording();
        }
      }
    }
    new RapidoOutputs(pf.vsCache, pf.tempDir).update(pf.fileList, pf.changeList);
    pf.rapido.save();
    setBuildComplete(true);
  }

  private void setBuildComplete(boolean complete) throws Exception {
    IniFile buildTracker = new IniFile(dir.resolve(".build-tracker.ini").toString());
    buildTracker.setBooleanProperty("status", "complete", complete, null);
    buildTracker.save();
  }

  /** the files as a build loads them, with the given name for Patient/a, and checkDependencies if it's a -rapido build */
  private PublisherFields build(String name, boolean rapido) throws Exception {
    assumeTrue(context != null, noContext);
    FetchedFile ig = file("ig.json", "{}");
    FetchedFile a = file("Patient-a.json", "{\"resourceType\":\"Patient\",\"id\":\"a\",\"name\":[{\"text\":\"" + name + "\"}]}");
    FetchedFile b = file("Patient-b.json", "{\"resourceType\":\"Patient\",\"id\":\"b\",\"name\":[{\"text\":\"Carol\"}]}");
    FetchedFile o = file("Observation-o.json", "{\"resourceType\":\"Observation\",\"id\":\"o\",\"status\":\"final\","
        + "\"code\":{\"text\":\"Weight\"},\"subject\":{\"reference\":\"Patient/a\"}}");

    Publisher publisher = new Publisher();
    publisher.getSettings().setRapidoMode(rapido);
    PublisherFields pf = new PublisherFields();
    pf.logger = mock(ILoggingService.class);
    pf.rootDir = dir.toString();
    pf.vsCache = dir.toString();
    pf.tempDir = dir.resolve("temp").toString();
    pf.tempLangDir = dir.resolve("translations").toString();
    pf.context = context;
    pf.pcm = pcm; // where the build looks for the packages it loaded, to see whether they changed
    pf.version = "5.0.0";
    pf.sourceIg = new ImplementationGuide();
    pf.defaultTranslationLang = defaultLang;
    pf.translationLangs.addAll(langs);
    pf.igpkp = mock(IGKnowledgeProvider.class);
    when(pf.igpkp.getCanonical()).thenReturn("http://example.org/ig");
    pf.template = mock(Template.class);
    pf.fileList.addAll(List.of(ig, a, b, o));
    if (withProfile) {
      FetchedFile p = file("StructureDefinition-p.json", "{\"resourceType\":\"StructureDefinition\",\"id\":\"p\","
          + "\"url\":\"http://example.org/StructureDefinition/p\",\"name\":\"P\",\"title\":\"A profile\",\"status\":\"draft\","
          + "\"kind\":\"resource\",\"abstract\":false,\"type\":\"Patient\","
          + "\"baseDefinition\":\"http://hl7.org/fhir/StructureDefinition/Patient\",\"derivation\":\"constraint\"}");
      StructureDefinition sd = new StructureDefinition();
      sd.setId("p");
      sd.setUrl("http://example.org/StructureDefinition/p").setName("P").setTitle("A profile");
      p.getResources().get(0).setResource(sd);
      pf.fileList.add(p);
    }
    pf.changeList.addAll(pf.fileList); // as the loader leaves it
    publisher.setPf(pf);
    Field loader = Publisher.class.getDeclaredField("loader");
    loader.setAccessible(true);
    ((PublisherIGLoader) loader.get(publisher)).setPf(pf);

    publisher.checkDependencies(ig, new IniFile(dir.resolve(".build-tracker.ini").toString()));
    return pf;
  }

  private FetchedFile file(String name, String content) throws Exception {
    FetchedFile f = new FetchedFile(name);
    f.setPath(dir.resolve("input").resolve(name).toString());
    f.setSource(content.getBytes(StandardCharsets.UTF_8));
    if (!"{}".equals(content)) {
      Element e = Manager.parseSingle(context, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), FhirFormat.JSON);
      f.addResource(name).setElement(e).setId(e.getIdBase());
    }
    return f;
  }
}
