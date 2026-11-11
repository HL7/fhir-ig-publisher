package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.igtools.publisher.modules.NullModule;
import org.hl7.fhir.igtools.renderers.DBBuilder;
import org.hl7.fhir.igtools.templates.Template;
import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.Basic;
import org.hl7.fhir.model.core.ElementDefinition;
import org.hl7.fhir.model.core.Enumerations.PublicationStatus;
import org.hl7.fhir.model.core.ImplementationGuide;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.model.core.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.services.elementmodel.JsonParser;
import org.hl7.fhir.services.elementmodel.LanguageUtils;
import org.hl7.fhir.services.utilities.NPMPackageGenerator;
import org.hl7.fhir.standalone.context.SimpleWorkerContext;
import org.hl7.fhir.utilities.UserDataNames;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * -rapido: the native outputs of a file that Rapido found unchanged are left as the last build wrote them, and the
 * step still adds the same entries to the packages, the same rows to the database, the same messages to the resources
 * and the same output names to the files, in the same order, with and without -threads. Anything else is written.
 */
class NativeOutputsReuseTest {

  private static final int FILES = 12;
  private static SimpleWorkerContext context;

  @TempDir
  Path temp;

  @BeforeAll
  static void makeContext() throws Exception {
    context = new SimpleWorkerContext.SimpleWorkerContextBuilder(ModelContext.fullCoreContext()).fromNothing();
    context.cacheResource(primitive("string"));
    context.cacheResource(primitive("code"));
    context.cacheResource(primitive("id"));
    StructureDefinition basic = type("Basic", StructureDefinitionKind.RESOURCE);
    element(basic, "Basic", null, "*");
    element(basic, "Basic.id", "id", "1");
    element(basic, "Basic.language", "code", "1");
    element(basic, "Basic.code", "string", "1");
    element(basic, "Basic.subject", "string", "*");
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

  private static StructureDefinition primitive(String name) {
    StructureDefinition sd = type(name, StructureDefinitionKind.PRIMITIVETYPE);
    element(sd, name, null, "*");
    element(sd, name + ".value", "http://hl7.org/fhirpath/System.String", "1");
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

  private static String sha(byte[] b) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(b)).substring(0, 12);
  }

  /** one build's native outputs step over the same temp folder */
  private class Build {
    final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    final Set<String> callThreads = ConcurrentHashMap.newKeySet();
    /** the files Rapido says are unchanged */
    final Set<String> clean = new HashSet<>();
    /** resource id to the code it holds, where that isn't "code [id]" */
    final Map<String, String> codes = new TreeMap<>();
    boolean rapido = true;
    boolean complete;
    int threads;
    boolean otherVersion;
    /** resources with no object to convert for the other version: nothing of them goes in its package */
    boolean elementsOnly;
    String archetypeOn;
    /** an instance of a logical model, loaded as a Binary */
    String binaryOn;
    /** one more example */
    String exampleOn;
    /** how many resources the last file has */
    int lastFileResources = 2;
    /** Rapido's hash of a file, what it depends on and what refers to it, where that isn't "state 1" */
    final Map<String, String> states = new TreeMap<>();
    /** Rapido has no such hashes */
    boolean noStates;
    /** the folder for the records can't be made */
    boolean noRecordFolder;
    PublisherGenerator generator;
    Map<String, FileTime> timesBefore;

    Build cleanAll() {
      for (int i = 0; i < FILES; i++) {
        clean.add("input/file" + i + ".json");
      }
      return this;
    }

    private NPMPackageGenerator npm(String label) throws Exception {
      NPMPackageGenerator npm = mock(NPMPackageGenerator.class);
      doAnswer(inv -> {
        record(label + " " + inv.getArgument(0) + " " + inv.getArgument(1) + " " + sha(inv.getArgument(2)));
        return null;
      }).when(npm).addFile(any(NPMPackageGenerator.Category.class), anyString(), any(byte[].class));
      return npm;
    }

    private void record(String call) {
      callThreads.add(Thread.currentThread().getName());
      calls.add(call);
    }

    Build run() throws Exception {
      for (String d : new String[] {"_includes", "en", "de"}) {
        Files.createDirectories(temp.resolve(d));
      }
      PublisherSettings settings = new PublisherSettings();
      settings.setThreads(threads);
      settings.setNewMultiLangTemplateFormat(true);
      PublisherFields pf = new PublisherFields();
      pf.context = context;
      pf.tempDir = temp.toString();
      pf.version = "5.0.0";
      pf.defaultTranslationLang = "en";
      pf.translationLangs.add("de");
      // the language copies of every third resource come with a message
      pf.langUtils = spy(new LanguageUtils(context));
      doAnswer(inv -> {
        Element e = inv.getArgument(0);
        Object res = inv.callRealMethod();
        if (e.isResource() && Integer.parseInt(e.getNamedChildValue("id").substring(1)) % 3 == 0) {
          List<ValidationMessage> errors = inv.getArgument(5);
          errors.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "IG", "no " + inv.getArgument(1) + " narrative for " + e.getNamedChildValue("id"), IssueSeverity.WARNING));
        }
        return res;
      }).when(pf.langUtils).switchLanguage(any(Element.class), anyString(), anyBoolean(), any(), any(), any());
      pf.module = new NullModule();
      pf.template = mock(Template.class);
      when(pf.template.wantGenerateFragment(anyString(), anyString())).thenReturn(true);
      pf.igpkp = mock(IGKnowledgeProvider.class);
      when(pf.igpkp.getCanonical()).thenReturn("http://example.org/fhir");
      when(pf.igpkp.wantGen(any(FetchedResource.class), anyString())).thenReturn(true);
      pf.publishedIg = new ImplementationGuide();
      pf.npm = npm("npm");
      pf.lnpms.put("en", npm("npm-en"));
      pf.lnpms.put("de", npm("npm-de"));
      if (otherVersion) {
        pf.generateVersions.add("r4");
        pf.vnpms.put("r4", npm("npm-r4"));
      }
      if (rapido) {
        pf.rapido = mock(RapidoTracker.class);
        when(pf.rapido.isCompleteBuild()).thenReturn(complete);
        when(pf.rapido.isRenderClean(any(FetchedFile.class))).thenAnswer(inv -> !complete && clean.contains(((FetchedFile) inv.getArgument(0)).getStatedPath()));
        when(pf.rapido.renderHash(any(FetchedFile.class))).thenAnswer(inv -> noStates ? null : states.getOrDefault(((FetchedFile) inv.getArgument(0)).getStatedPath(), "state 1"));
      }
      for (int i = 0; i < FILES; i++) {
        FetchedFile f = new FetchedFile("input/file" + i + ".json");
        // the last file has two resources
        for (String id : i == FILES - 1 && lastFileResources == 2 ? new String[] {"b" + i, "b" + (i + 1)} : new String[] {"b" + i}) {
          FetchedResource r = f.addResource(id);
          String json = "{\"resourceType\":\"Basic\",\"id\":\"" + id + "\",\"code\":\"" + codes.getOrDefault(id, "code " + id) + "\",\"subject\":[\"" + "x".repeat(i * 53) + "\"]}";
          r.setElement(new JsonParser(context).parseSingle(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), null)).setId(id);
          if (!elementsOnly) {
            r.setResource(new Basic().setId(id));
          }
          if (id.equals(binaryOn)) {
            r.setType("Binary");
          }
          if (i % 5 == 2) {
            // from before the step
            r.getErrors().add(new ValidationMessage(Source.InstanceValidator, IssueType.INVALID, "Basic.code", "an earlier message for " + id, IssueSeverity.ERROR));
          }
          if (id.equals(archetypeOn)) {
            r.getResource().setUserData(UserDataNames.archetypeSource, "archetype " + id);
            r.getResource().setUserData(UserDataNames.archetypeName, id + ".adl");
          }
          if (i % 4 == 1 || id.equals(exampleOn)) {
            pf.publishedIg.getDefinition().addResource().setIsExample(true).getReference().setReference("Basic/" + id);
          }
        }
        pf.changeList.add(f);
      }
      DBBuilder db = mock(DBBuilder.class);
      doAnswer(inv -> {
        record("db " + ((FetchedFile) inv.getArgument(0)).getStatedPath() + " " + ((FetchedResource) inv.getArgument(1)).getId() + " " + sha(inv.getArgument(2)));
        return null;
      }).when(db).saveResource(any(FetchedFile.class), any(FetchedResource.class), any(byte[].class));
      generator = new PublisherGenerator(settings);
      generator.setPf(pf);
      if (noRecordFolder) {
        Files.createDirectories(temp.resolve("_rapido"));
        Files.write(temp.resolve("_rapido").resolve("native"), new byte[0]);
      }
      timesBefore = times();
      Thread.sleep(20); // so that a file written again has another time
      generator.generateNativeOutputs(db);
      return this;
    }

    /** the output names of each file, as paths in the temp folder */
    List<String> outputNames() {
      List<String> names = new ArrayList<>();
      for (FetchedFile f : generator.pf.changeList) {
        for (String s : f.getOutputNames()) {
          names.add(f.getStatedPath() + " " + temp.relativize(Path.of(s)));
        }
      }
      Collections.sort(names);
      return names;
    }

    /** the messages of each resource, in order */
    List<String> messages() {
      List<String> res = new ArrayList<>();
      for (FetchedFile f : generator.pf.changeList) {
        for (FetchedResource r : f.getResources()) {
          for (ValidationMessage vm : r.getErrors()) {
            res.add(r.getId() + " " + vm.getLevel() + " " + vm.getType() + " " + vm.getSource() + " " + vm.getLocation() + " " + vm.getMessage());
          }
        }
      }
      return res;
    }

    /** the native outputs (not the records) that this build wrote, by the resource id in their names */
    Set<String> written() throws Exception {
      Set<String> ids = new TreeSet<>();
      for (Map.Entry<String, FileTime> e : times().entrySet()) {
        if (!e.getValue().equals(timesBefore.get(e.getKey()))) {
          String name = Path.of(e.getKey()).getFileName().toString();
          ids.add(name.substring(name.indexOf('-') + 1, name.indexOf('.')));
        }
      }
      return ids;
    }
  }

  /** every native output under the temp folder, with its time */
  private Map<String, FileTime> times() throws Exception {
    Map<String, FileTime> map = new TreeMap<>();
    for (Path p : outputs()) {
      map.put(temp.relativize(p).toString(), Files.getLastModifiedTime(p));
    }
    return map;
  }

  /** every native output under the temp folder, with its content's hash */
  private Map<String, String> contents() throws Exception {
    Map<String, String> map = new TreeMap<>();
    for (Path p : outputs()) {
      map.put(temp.relativize(p).toString(), sha(Files.readAllBytes(p)));
    }
    return map;
  }

  private List<Path> outputs() throws Exception {
    if (!Files.isDirectory(temp)) {
      return List.of();
    }
    try (Stream<Path> s = Files.walk(temp)) {
      return s.filter(Files::isRegularFile).filter(p -> !temp.relativize(p).startsWith("_rapido")).collect(Collectors.toList());
    }
  }

  private List<Path> records() throws Exception {
    Path folder = temp.resolve("_rapido").resolve("native");
    if (!Files.isDirectory(folder)) {
      return List.of();
    }
    try (Stream<Path> s = Files.list(folder)) {
      return s.sorted().collect(Collectors.toList());
    }
  }

  private static Set<String> ids(int... numbers) {
    Set<String> ids = new TreeSet<>();
    for (int n : numbers) {
      ids.add("b" + n);
    }
    return ids;
  }

  private static void assertSameEffects(Build first, Build next) throws Exception {
    if (first.threads == next.threads) {
      assertEquals(first.calls, next.calls);
    } else {
      // with threads, the database rows come after the package entries
      assertEquals(packageCalls(first), packageCalls(next));
      assertEquals(rows(first), rows(next));
    }
    assertEquals(first.outputNames(), next.outputNames());
    assertEquals(first.messages(), next.messages());
  }

  private static List<String> packageCalls(Build build) {
    return build.calls.stream().filter(c -> !c.startsWith("db ")).collect(Collectors.toList());
  }

  private static List<String> rows(Build build) {
    return build.calls.stream().filter(c -> c.startsWith("db ")).collect(Collectors.toList());
  }

  @Test
  void unchangedFilesAreNotWrittenAgainAndTheStepDoesTheSame() throws Exception {
    Build first = new Build();
    first.complete = true;
    first.run();
    assertEquals(FILES + 1, first.written().size());
    assertEquals(FILES, records().size());
    // json, xml and ttl in the root and the 2 language folders, and the _includes json and escaped json
    assertEquals((FILES + 1) * (3 * 3 + 2), outputs().size());
    for (String kind : new String[] {"npm RESOURCE ", "npm EXAMPLE ", "npm-en ", "npm-de ", "db "}) {
      assertTrue(first.calls.stream().anyMatch(c -> c.startsWith(kind)), kind + " in " + first.calls);
    }
    assertFalse(first.messages().isEmpty());
    Map<String, String> contents = contents();

    Build next = new Build().cleanAll().run();
    assertEquals(Set.of(), next.written());
    assertSameEffects(first, next);
    assertEquals(contents, contents());

    // and again, from the same records
    Build third = new Build().cleanAll().run();
    assertEquals(Set.of(), third.written());
    assertSameEffects(first, third);
  }

  @Test
  void onThreadsTheStepDoesTheSameFromTheCallingThread() throws Exception {
    Build first = new Build();
    first.complete = true;
    first.run();

    Build next = new Build().cleanAll();
    next.threads = 3;
    next.run();
    assertEquals(Set.of(), next.written());
    assertSameEffects(first, next);
    assertEquals(Set.of(Thread.currentThread().getName()), next.callThreads);

    // records written on the threads are as good as the others
    Build threaded = new Build();
    threaded.complete = true;
    threaded.threads = 3;
    threaded.codes.put("b2", "another code");
    threaded.run();
    assertEquals(FILES + 1, threaded.written().size());
    Build after = new Build().cleanAll();
    after.codes.put("b2", "another code");
    after.run();
    assertEquals(Set.of(), after.written());
    assertSameEffects(threaded, after);
  }

  @Test
  void aFileRapidoFoundChangedIsWritten() throws Exception {
    Build first = new Build();
    first.complete = true;
    first.run();

    Build next = new Build().cleanAll();
    next.clean.remove("input/file4.json");
    next.clean.remove("input/file" + (FILES - 1) + ".json");
    next.run();
    assertEquals(ids(4, FILES - 1, FILES), next.written());
    assertSameEffects(first, next);

    // what was written then is reused after that
    Build third = new Build().cleanAll().run();
    assertEquals(Set.of(), third.written());
    assertSameEffects(first, third);
  }

  @Test
  void aResourceWhoseContentIsNotWhatWasWrittenIsWritten() throws Exception {
    Build first = new Build();
    first.complete = true;
    first.run();

    // Rapido takes the file as unchanged, but what the step would write from isn't what it wrote from
    Build next = new Build().cleanAll();
    next.codes.put("b5", "a new code");
    next.run();
    assertEquals(ids(5), next.written());
    assertNotEquals(first.calls, next.calls);
    assertTrue(new String(Files.readAllBytes(temp.resolve("Basic-b5.json")), StandardCharsets.UTF_8).contains("a new code"));

    Build fresh = new Build();
    fresh.rapido = false;
    fresh.codes.put("b5", "a new code");
    fresh.run();
    assertEquals(fresh.calls, next.calls);
  }

  @Test
  void aFileThatIsNotAsItWasWrittenIsWrittenAgain() throws Exception {
    Build first = new Build();
    first.complete = true;
    first.run();
    Map<String, String> contents = contents();

    // gone, written over with the same size, written over with the same time
    Files.delete(temp.resolve("de").resolve("Basic-b1.ttl"));
    Path same = temp.resolve("_includes").resolve("Basic-b2.json");
    Thread.sleep(20);
    Files.write(same, new String(Files.readAllBytes(same), StandardCharsets.UTF_8).replace("code b2", "CODE B2").getBytes(StandardCharsets.UTF_8));
    Path longer = temp.resolve("en").resolve("Basic-b3.xml");
    FileTime time = Files.getLastModifiedTime(longer);
    Files.write(longer, "<Basic/>".getBytes(StandardCharsets.UTF_8));
    Files.setLastModifiedTime(longer, time);

    Build next = new Build().cleanAll().run();
    assertEquals(ids(1, 2, 3), next.written());
    assertSameEffects(first, next);
    assertEquals(contents, contents());
  }

  @Test
  void aRecordThatCantBeReadIsNotUsed() throws Exception {
    Build first = new Build();
    first.complete = true;
    first.run();
    List<Path> records = records();
    byte[] b = Files.readAllBytes(records.get(0));
    Files.write(records.get(0), java.util.Arrays.copyOf(b, b.length - 5));
    Files.write(records.get(1), "not a record".getBytes(StandardCharsets.UTF_8));
    Files.delete(records.get(2));

    Build next = new Build().cleanAll().run();
    assertEquals(3, next.written().size(), next.written().toString());
    assertSameEffects(first, next);
    assertEquals(FILES, records().size());
  }

  @Test
  void aCompleteRapidoBuildWritesEverything() throws Exception {
    Build first = new Build();
    first.complete = true;
    first.run();

    Build next = new Build().cleanAll();
    next.complete = true;
    next.run();
    assertEquals(FILES + 1, next.written().size());
    assertSameEffects(first, next);
  }

  @Test
  void withoutRapidoNothingIsRecordedOrReused() throws Exception {
    Build first = new Build();
    first.rapido = false;
    first.run();
    assertEquals(List.of(), records());

    Build next = new Build();
    next.rapido = false;
    next.run();
    assertEquals(FILES + 1, next.written().size());
    assertEquals(List.of(), records());
    assertSameEffects(first, next);

    // and what a Rapido build does is what a build without it does
    Build rapido = new Build();
    rapido.complete = true;
    rapido.run();
    assertSameEffects(first, rapido);
  }

  @Test
  void whatARecordCantHoldIsWrittenEveryTime() throws Exception {
    // packages for another version are converted from the resource objects
    Build first = new Build();
    first.complete = true;
    first.otherVersion = true;
    first.run();
    assertEquals(List.of(), records());
    assertTrue(first.calls.stream().anyMatch(c -> c.startsWith("npm-r4 ")), first.calls.toString());
    Build next = new Build().cleanAll();
    next.otherVersion = true;
    next.run();
    assertEquals(FILES + 1, next.written().size());
    assertSameEffects(first, next);

    // also when the step adds nothing to the other version's package
    Build third = new Build();
    third.complete = true;
    third.otherVersion = true;
    third.elementsOnly = true;
    third.run();
    assertEquals(List.of(), records());
    assertFalse(third.calls.stream().anyMatch(c -> c.startsWith("npm-r4 ")), third.calls.toString());
    Build fourth = new Build().cleanAll();
    fourth.otherVersion = true;
    fourth.elementsOnly = true;
    fourth.run();
    assertEquals(FILES + 1, fourth.written().size());
    assertSameEffects(third, fourth);
  }

  @Test
  void aResourceWithAnArchetypeIsWrittenEveryTime() throws Exception {
    Build first = new Build();
    first.complete = true;
    first.archetypeOn = "b6";
    first.run();
    assertEquals(FILES - 1, records().size());
    assertTrue(first.calls.stream().anyMatch(c -> c.startsWith("npm ADL b6.adl ")), first.calls.toString());
    Build next = new Build().cleanAll();
    next.archetypeOn = "b6";
    next.run();
    assertEquals(ids(6), next.written());
    assertSameEffects(first, next);
  }

  @Test
  void outputsWrittenFromAnotherStateAreWritten() throws Exception {
    Build first = new Build();
    first.complete = true;
    first.run();

    // a build that Rapido keeps no state of (it failed later) wrote file 3 when what it depends on was different
    Build second = new Build().cleanAll();
    second.clean.remove("input/file3.json");
    second.states.put("input/file3.json", "state 2");
    second.run();
    assertEquals(ids(3), second.written());

    // what it depends on is as it was for the first build again: to Rapido nothing changed, but the files aren't from then
    Build third = new Build().cleanAll().run();
    assertEquals(ids(3), third.written());
    assertSameEffects(first, third);
    assertEquals(Set.of(), new Build().cleanAll().run().written());

    // and with no hash from Rapido there is nothing to go by
    Build none = new Build().cleanAll();
    none.noStates = true;
    none.run();
    assertEquals(FILES + 1, none.written().size());
    assertEquals(List.of(), records());
    assertSameEffects(first, none);
  }

  @Test
  void aProblemWithTheRecordsDoesNotStopTheBuild() throws Exception {
    Build fresh = new Build();
    fresh.rapido = false;
    fresh.run();

    Build first = new Build();
    first.complete = true;
    first.noRecordFolder = true;
    first.run();
    assertEquals(FILES + 1, first.written().size());
    assertSameEffects(fresh, first);

    Build next = new Build().cleanAll();
    next.noRecordFolder = true;
    next.run();
    assertEquals(FILES + 1, next.written().size());
    assertSameEffects(fresh, next);
  }

  @Test
  void anInstanceOfALogicalModelGoesInThePackagesAsABinaryFromTheRecordToo() throws Exception {
    Build first = new Build();
    first.complete = true;
    first.binaryOn = "b9";
    first.run();
    assertTrue(first.calls.stream().anyMatch(c -> c.startsWith("npm RESOURCE Binary-b9.json ")), first.calls.toString());
    assertTrue(first.calls.stream().anyMatch(c -> c.startsWith("npm-de RESOURCE Binary-b9.json ")), first.calls.toString());

    Build next = new Build().cleanAll();
    next.binaryOn = "b9";
    next.run();
    assertEquals(Set.of(), next.written());
    assertSameEffects(first, next);
  }

  @Test
  void aSettingThatChangesWhatIsDoneForAResourceHasItWritten() throws Exception {
    Build first = new Build();
    first.complete = true;
    first.run();
    assertTrue(first.calls.stream().anyMatch(c -> c.startsWith("npm RESOURCE Basic-b6.json ")), first.calls.toString());

    // b6 is an example now: the same files, but another place in the packages
    Build next = new Build().cleanAll();
    next.exampleOn = "b6";
    next.run();
    assertEquals(ids(6), next.written());
    assertTrue(next.calls.stream().anyMatch(c -> c.startsWith("npm EXAMPLE Basic-b6.json ")), next.calls.toString());
    Build fresh = new Build();
    fresh.rapido = false;
    fresh.exampleOn = "b6";
    fresh.run();
    assertSameEffects(fresh, next);
  }

  @Test
  void aFileWithAnotherNumberOfResourcesIsWritten() throws Exception {
    Build fresh = new Build();
    fresh.rapido = false;
    fresh.lastFileResources = 1;
    fresh.run();
    Build first = new Build();
    first.complete = true;
    first.run();

    Build next = new Build().cleanAll();
    next.lastFileResources = 1;
    next.run();
    assertEquals(ids(FILES - 1), next.written());
    assertSameEffects(fresh, next);

    Build third = new Build().cleanAll().run();
    assertEquals(ids(FILES - 1, FILES), third.written());
    assertSameEffects(first, third);
  }

  @Test
  void aRecordFromBeforeAFileWasWrittenAgainIsNotUsed() throws Exception {
    Build first = new Build();
    first.complete = true;
    first.run();

    // b7 is written again with other content, by a build that keeps no record of it (it has an archetype now)
    Build second = new Build().cleanAll();
    second.clean.remove("input/file7.json");
    second.codes.put("b7", "changed");
    second.archetypeOn = "b7";
    second.run();
    assertEquals(ids(7), second.written());
    assertEquals(FILES - 1, records().size());

    // back to what the first build had: its record is gone, so the outputs are written
    Build third = new Build().cleanAll().run();
    assertEquals(ids(7), third.written());
    assertSameEffects(first, third);
  }
}
