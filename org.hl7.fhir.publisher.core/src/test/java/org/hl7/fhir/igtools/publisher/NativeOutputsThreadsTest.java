package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.hl7.fhir.model.core.NutritionProduct;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.model.core.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.services.elementmodel.JsonParser;
import org.hl7.fhir.services.elementmodel.LanguageUtils;
import org.hl7.fhir.services.utilities.NPMPackageGenerator;
import org.hl7.fhir.utilities.UserDataNames;
import org.hl7.fhir.standalone.context.SimpleWorkerContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The native outputs phase with -threads: the files it writes, and everything it adds to the packages, the
 * cross-version notes and the database, in what order and on which thread, must be the same as without threads.
 */
class NativeOutputsThreadsTest {

  private static final int FILES = 40;
  private static SimpleWorkerContext context;

  @TempDir
  Path dir;

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
    StructureDefinition custom = type("Custom", StructureDefinitionKind.RESOURCE);
    element(custom, "Custom", null, "*");
    element(custom, "Custom.id", "id", "1");
    element(custom, "Custom.language", "code", "1");
    element(custom, "Custom.code", "string", "1");
    element(custom, "Custom.subject", "string", "*");
    custom.setUserData(UserDataNames.loader_custom_resource, "true");
    context.cacheResource(custom);
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

  /** what the phase did, in order: package entries, cross-version notes and database rows */
  private final List<String> calls = Collections.synchronizedList(new ArrayList<>());
  /** the threads that the phase called the packages, the notes and the database from */
  private final Set<String> callThreads = ConcurrentHashMap.newKeySet();
  /** the threads that asked which outputs to make */
  private final Set<String> workThreads = ConcurrentHashMap.newKeySet();
  /** set by the first database row */
  private final AtomicBoolean rowsStarted = new AtomicBoolean();
  /** whether a worker was still making outputs after the first database row */
  private final AtomicBoolean workAfterRows = new AtomicBoolean();

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

  private static String sha(byte[] b) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(b)).substring(0, 12);
  }

  /** a publisher set up for the native outputs of FILES files, each with two resources, on threads */
  private PublisherGenerator generator(Path temp, int threads, String failOn, String... ids) throws Exception {
    for (String d : new String[] {"_includes", "en", "de", "fr"}) {
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
    pf.translationLangs.add("fr");
    pf.langUtils = new LanguageUtils(context);
    pf.module = new NullModule();
    pf.template = mock(Template.class);
    when(pf.template.wantGenerateFragment(anyString(), anyString())).thenReturn(true);
    pf.igpkp = mock(IGKnowledgeProvider.class);
    when(pf.igpkp.getCanonical()).thenReturn("http://example.org/fhir");
    when(pf.igpkp.wantGen(any(FetchedResource.class), anyString())).thenAnswer(inv -> {
      FetchedResource r = inv.getArgument(0);
      workThreads.add(Thread.currentThread().getName());
      if (rowsStarted.get()) {
        workAfterRows.set(true);
      }
      if (r.getId().equals(failOn)) {
        throw new IllegalStateException("no outputs for " + failOn);
      }
      if (r.getId().equals("!" + failOn)) {
        throw new AssertionError("no outputs for " + failOn);
      }
      return true;
    });
    pf.publishedIg = new ImplementationGuide();
    pf.npm = npm("npm");
    pf.lnpms.put("en", npm("npm-en"));
    pf.lnpms.put("de", npm("npm-de"));
    pf.lnpms.put("fr", npm("npm-fr"));
    pf.generateVersions.add("r4");
    pf.vnpms.put("r4", npm("npm-r4"));
    pf.cvAnalyser = mock(CrossVersionAnalyser.class);
    doAnswer(inv -> {
      record("omit " + inv.getArgument(0) + " " + inv.getArgument(1));
      return null;
    }).when(pf.cvAnalyser).recordOmission(anyString(), anyString());
    doAnswer(inv -> {
      record("problem " + inv.getArgument(0) + " " + inv.getArgument(1));
      return null;
    }).when(pf.cvAnalyser).recordProblem(anyString(), anyString(), any());

    int n = 0;
    for (int i = 0; i < ids.length; i += 2) {
      FetchedFile f = new FetchedFile("input/file" + i + ".json");
      for (int j = i; j < i + 2 && j < ids.length; j++) {
        String id = ids[j];
        FetchedResource r = f.addResource(id);
        int kind = j % 7;
        String type = kind == 2 ? "Custom" : "Basic";
        String json = "{\"resourceType\":\"" + type + "\",\"id\":\"" + id + "\",\"code\":\"code " + id + "\",\"subject\":["
            + "\"" + "x".repeat(n++ * 37 % 2000) + "\"]}"; // different sizes, so they take different times
        Element e = new JsonParser(context).parseSingle(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), null);
        if (kind == 1) {
          r.setType("Binary"); // an instance of a logical model, loaded as a Binary
        } else if (kind == 4) {
          r.setType("StructureDefinition"); // a custom resource's definition
          StructureDefinition sd = type("Def" + id, StructureDefinitionKind.LOGICAL);
          sd.setUserData(UserDataNames.loader_custom_resource, "true");
          r.setResource(sd);
        }
        r.setElement(e).setId(id);
        if (j % 4 == 1) {
          pf.publishedIg.getDefinition().addResource().setIsExample(true).getReference().setReference(r.fhirType() + "/" + id);
        }
        if (kind == 6) {
          // a resource that can't be converted to R4: a problem note
          r.setResource(new NutritionProduct().setId(id));
        } else if (j % 3 == 0 && kind != 2 && kind != 4) {
          // a resource that also goes in the R4 package
          r.setResource(new Basic().setId(id));
          if (j % 2 == 0) {
            r.getResource().setUserData(UserDataNames.archetypeSource, "archetype " + id);
            r.getResource().setUserData(UserDataNames.archetypeName, id + ".adl");
          }
        }
        if (j % 5 == 0) {
          pf.r5Inclusions.add("Basic/" + id); // only in the R5 package: an omission note for R4
        }
      }
      pf.changeList.add(f);
    }
    PublisherGenerator generator = new PublisherGenerator(settings);
    generator.setPf(pf);
    return generator;
  }

  private static String[] ids(int count) {
    String[] ids = new String[count];
    for (int i = 0; i < count; i++) {
      ids[i] = "b" + i;
    }
    return ids;
  }

  /** the id whose database row fails, if any */
  private String failRowOn;

  private DBBuilder db() {
    DBBuilder db = mock(DBBuilder.class);
    doAnswer(inv -> {
      FetchedFile f = inv.getArgument(0);
      FetchedResource r = inv.getArgument(1);
      rowsStarted.set(true);
      if (r.getId().equals(failRowOn)) {
        throw new IllegalArgumentException("no row for " + failRowOn);
      }
      record("db " + f.getStatedPath() + " " + r.getId() + " " + sha(inv.getArgument(2)));
      return null;
    }).when(db).saveResource(any(FetchedFile.class), any(FetchedResource.class), any(byte[].class));
    return db;
  }

  /** every file under dir, with its content's hash */
  private static Map<String, String> files(Path dir) throws Exception {
    Map<String, String> map = new TreeMap<>();
    try (Stream<Path> s = Files.walk(dir)) {
      for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
        map.put(dir.relativize(p).toString(), sha(Files.readAllBytes(p)));
      }
    }
    return map;
  }

  private static List<String> outputNames(PublisherGenerator generator, Path temp) {
    List<String> names = new ArrayList<>();
    for (FetchedFile f : generator.pf.changeList) {
      for (String s : f.getOutputNames()) {
        names.add(f.getStatedPath() + " " + temp.relativize(Path.of(s)));
      }
    }
    Collections.sort(names); // a HashSet of absolute paths, and each run has its own temp folder
    return names;
  }

  private static class Run {
    List<String> calls;
    Set<String> callThreads;
    Set<String> workThreads;
    Map<String, String> files;
    List<String> outputNames;
    Throwable failure;
    boolean workAfterRows;

    /** the package entries and notes, in order */
    List<String> packageCalls() {
      return calls.stream().filter(c -> !c.startsWith("db ")).collect(Collectors.toList());
    }

    /** the database rows, in order */
    List<String> rows() {
      return calls.stream().filter(c -> c.startsWith("db ")).collect(Collectors.toList());
    }
  }

  private Run run(String name, int threads, String failOn, String... ids) throws Exception {
    return run(name, threads, true, failOn, ids);
  }

  private Run run(String name, int threads, boolean withDb, String failOn, String... ids) throws Exception {
    calls.clear();
    callThreads.clear();
    workThreads.clear();
    rowsStarted.set(false);
    workAfterRows.set(false);
    Path temp = dir.resolve(name);
    PublisherGenerator generator = generator(temp, threads, failOn, ids);
    Run run = new Run();
    try {
      generator.generateNativeOutputs(withDb ? db() : null);
    } catch (Exception | Error e) {
      run.failure = e;
    }
    run.calls = new ArrayList<>(calls);
    run.callThreads = Set.copyOf(callThreads);
    run.workThreads = Set.copyOf(workThreads);
    run.files = files(temp);
    run.outputNames = outputNames(generator, temp);
    run.workAfterRows = workAfterRows.get();
    return run;
  }

  @Test
  void theSameOutputsInTheSameOrderAsWithoutThreads() throws Exception {
    Run plain = run("plain", 0, null, ids(FILES));
    String main = Thread.currentThread().getName();
    assertEquals(Set.of(main), plain.workThreads);
    for (String kind : new String[] {"npm ", "npm-en ", "npm-fr ", "npm-r4 ", "omit ", "problem ", "db "}) {
      assertTrue(plain.calls.stream().anyMatch(c -> c.startsWith(kind)), kind + " in " + plain.calls);
    }
    for (String entry : new String[] {"RESOURCE Binary-", "EXAMPLE Binary-", "ADL ", "CUSTOM Custom-", "CUSTOM StructureDefinition-"}) {
      assertTrue(plain.calls.stream().anyMatch(c -> c.contains(" " + entry)), entry + " in " + plain.calls);
    }
    // xml, json and ttl in the root and the 3 language folders, and the _includes json and escaped json
    assertEquals(FILES * (3 * 4 + 2), plain.files.size(), plain.files.keySet().toString());

    for (int threads : new int[] {1, 3, 8}) {
      for (int rep = 0; rep < 3; rep++) {
        Run run = run("threads" + threads + "-" + rep, threads, null, ids(FILES));
        assertEquals(plain.packageCalls(), run.packageCalls(), threads + " threads");
        assertEquals(plain.rows(), run.rows(), threads + " threads");
        assertEquals(Set.of(main), run.callThreads, "packages, notes and database only from the calling thread");
        assertFalse(run.workAfterRows, "a database row was saved while the workers ran");
        assertFalse(run.workThreads.contains(main), "the outputs were made on " + run.workThreads);
        assertEquals(plain.files, run.files, threads + " threads");
        assertEquals(plain.outputNames, run.outputNames, threads + " threads");
      }
    }
  }

  @Test
  void aFailureStopsAtTheSameResourceAsWithoutThreads() throws Exception {
    Run plain = run("plain", 0, "b13", ids(FILES));
    Run threads = run("threads", 4, "b13", ids(FILES));
    assertTrue(plain.failure instanceof IllegalStateException, String.valueOf(plain.failure));
    assertEquals(plain.failure.getClass(), threads.failure.getClass());
    assertEquals(plain.failure.getMessage(), threads.failure.getMessage());
    // b12 (the resource before b13, in the same file) is in the packages and the database, b13 and later aren't
    assertTrue(plain.calls.stream().anyMatch(c -> c.startsWith("db input/file12.json b12 ")), plain.calls.toString());
    assertEquals(plain.packageCalls(), threads.packageCalls());
    assertEquals(plain.rows(), threads.rows());
  }

  @Test
  void anErrorStopsAtTheSameResourceAsWithoutThreads() throws Exception {
    String[] ids = ids(FILES);
    ids[13] = "!b13";
    Run plain = run("plain", 0, "b13", ids);
    Run threads = run("threads", 4, "b13", ids);
    assertTrue(plain.failure instanceof AssertionError, String.valueOf(plain.failure));
    assertEquals(plain.failure.getClass(), threads.failure.getClass());
    assertEquals(plain.failure.getMessage(), threads.failure.getMessage());
    assertTrue(plain.calls.stream().anyMatch(c -> c.startsWith("db input/file12.json b12 ")), plain.calls.toString());
    assertEquals(plain.packageCalls(), threads.packageCalls());
    assertEquals(plain.rows(), threads.rows());
  }

  @Test
  void aFailedRowIsThrownBeforeALaterFailureAsWithoutThreads() throws Exception {
    failRowOn = "b4";
    Run plain = run("plain", 0, "b13", ids(FILES));
    Run threads = run("threads", 4, "b13", ids(FILES));
    assertTrue(plain.failure instanceof IllegalArgumentException, String.valueOf(plain.failure));
    assertEquals(plain.failure.getClass(), threads.failure.getClass());
    assertEquals(plain.failure.getMessage(), threads.failure.getMessage());
    assertEquals(plain.rows(), threads.rows());
  }

  @Test
  void theSameOutputsWithoutADatabase() throws Exception {
    Run plain = run("plain", 0, false, null, ids(FILES));
    Run threads = run("threads", 4, false, null, ids(FILES));
    assertEquals(null, plain.failure);
    assertEquals(null, threads.failure);
    assertTrue(threads.rows().isEmpty());
    assertEquals(plain.packageCalls(), threads.packageCalls());
    assertEquals(plain.files, threads.files);
  }

  @Test
  void idsThatDifferOnlyInCaseAreDoneWithoutThreads() throws Exception {
    String[] ids = ids(FILES);
    ids[35] = "B0"; // a Basic, as b0 is
    Run run = run("clash", 4, null, ids);
    assertEquals(Set.of(Thread.currentThread().getName()), run.workThreads);
    assertTrue(PublisherGenerator.nativeOutputNamesClash(files("a", "A")));
    assertFalse(PublisherGenerator.nativeOutputNamesClash(files("a", "b")));
  }

  /** a file for each id, each with one Basic resource */
  private static List<FetchedFile> files(String... ids) {
    List<FetchedFile> files = new ArrayList<>();
    for (String id : ids) {
      FetchedFile f = new FetchedFile(id + ".json");
      f.addResource(id).setId(id).setType("Basic");
      files.add(f);
    }
    return files;
  }
}
