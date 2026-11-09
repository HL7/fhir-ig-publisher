package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.FileNode;
import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.FileResult;
import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.ElementDefinition;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.utilities.formats.FhirFormat;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.services.elementmodel.Manager;
import org.hl7.fhir.services.renderers.RendererFactory;
import org.hl7.fhir.services.renderers.utils.RenderingContext;
import org.hl7.fhir.services.renderers.utils.Resolver.IReferenceResolver;
import org.hl7.fhir.services.renderers.utils.Resolver.ResourceReferenceKind;
import org.hl7.fhir.services.renderers.utils.Resolver.ResourceWithReference;
import org.hl7.fhir.services.renderers.utils.ResourceWrapper;
import org.hl7.fhir.standalone.context.SimpleWorkerContext;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The dependency analysis reads resources as the publisher loads them: any resource, not only canonical ones. A Device
 * can have more than one name and version, and reading them as a canonical's url, version, name and title stopped
 * every -rapido build of an IG with one
 */
class RapidoTrackerResourcesTest {

  private static SimpleWorkerContext context;
  private static String noContext;
  private static String noPackage;

  /** The package from the cache, downloading it if need be: null (and why in noPackage) when it can't be fetched */
  private static NpmPackage fetch(String id, String version) throws IOException {
    try {
      return new FilesystemPackageCacheManager.Builder().build().loadPackage(id, version);
    } catch (IOException | FHIRException e) {
      noPackage = id + "#" + version + " can't be fetched: " + e;
      return null;
    }
  }

  @BeforeAll
  static void loadContext() throws Exception {
    NpmPackage npm = fetch("hl7.fhir.r5.core", "5.0.0");
    noContext = noPackage;
    if (npm != null) {
      context = new SimpleWorkerContext.SimpleWorkerContextBuilder(ModelContext.fullCoreContext()).withAllowLoadingDuplicates(true).fromPackage(npm);
    }
  }

  @TempDir
  Path temp;

  private RapidoTracker tracker() throws Exception {
    return tracker(new PublisherFields());
  }

  private RapidoTracker tracker(PublisherFields pf) throws Exception {
    assumeTrue(context != null, noContext);
    pf.tempDir = temp.toString();
    pf.context = context;
    pf.version = "5.0.0";
    return new RapidoTracker(pf);
  }

  private static Element parse(String name) throws Exception {
    try (InputStream in = RapidoTrackerResourcesTest.class.getResourceAsStream("/rapido/" + name + ".json")) {
      assertNotNull(in, name);
      return Manager.parseSingle(context, in, FhirFormat.JSON);
    }
  }

  private static FetchedFile file(String name) throws Exception {
    Element e = parse(name);
    FetchedFile f = new FetchedFile(name + ".json");
    f.addResource(name).setElement(e).setId(e.getIdBase());
    return f;
  }

  private static final String[] FIXTURES = {"Device-ct-scanner", "Patient-two-names", "Observation-contained", "Bundle-collection", "CodeSystem-codes"};

  @Test
  void anyResourceCanBeAnalysed() throws Exception {
    RapidoTracker t = tracker();
    List<FileNode> nodes = new ArrayList<>();
    for (String name : FIXTURES) {
      FetchedFile f = file(name);
      FileNode fn = new FileNode(name, "source");
      fn.addResource(t.resourceNode(f.getResources().get(0)));
      nodes.add(fn);
    }
    Map<String, FileResult> results = new RapidoDependencyAnalyser("http://example.org/fhir").analyse(nodes);

    assertEquals(FIXTURES.length, results.size());
    // the code system's name is still read: [[[Codes]]] finds it, so the observation doesn't depend on everything
    FileResult obs = results.get("Observation-contained");
    assertTrue(obs.getDependsOn().contains("CodeSystem/codes"), obs.getDependsOn().toString());
    assertFalse(obs.isUsesInventory());
  }

  @Test
  void aDeviceNameIsNotACanonicalName() throws Exception {
    RapidoTracker t = tracker();
    FileNode device = new FileNode("device", "source").addResource(t.resourceNode(file("Device-ct-scanner").getResources().get(0)));
    Element e = parse("Observation-contained");
    e.getNamedChild("valueString").setValue("see [[[KT skaner]]]");
    FetchedFile of = new FetchedFile("obs.json");
    of.addResource("obs").setElement(e).setId("contained");
    FileNode obs = new FileNode("obs", "source").addResource(t.resourceNode(of.getResources().get(0)));

    FileResult res = new RapidoDependencyAnalyser("http://example.org/fhir").analyse(List.of(device, obs)).get("obs");
    assertFalse(res.getDependsOn().contains("Device/ct-scanner"));
    assertTrue(res.isUsesInventory());
  }

  private static Element parseJson(String json) throws Exception {
    return Manager.parseSingle(context, new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), FhirFormat.JSON);
  }

  /** the analysis of files holding the given resources, keyed by the resources' Type/id */
  private Map<String, FileResult> analyse(Element... resources) throws Exception {
    RapidoTracker t = tracker();
    List<FileNode> nodes = new ArrayList<>();
    for (Element e : resources) {
      FetchedFile f = new FetchedFile(e.fhirType()+"-"+e.getIdBase()+".json");
      f.addResource(e.getIdBase()).setElement(e).setId(e.getIdBase());
      nodes.add(new FileNode(e.fhirType()+"/"+e.getIdBase(), "source").addResource(t.resourceNode(f.getResources().get(0))));
    }
    return new RapidoDependencyAnalyser("http://example.org/fhir").analyse(nodes);
  }

  private static final String CONDITION = "{\"resourceType\":\"Condition\",\"id\":\"c\",\"code\":{\"text\":\"Asthma\"},"
      + "\"subject\":{\"reference\":\"Patient/p\"}}";
  /** core's Observation has an invariant with a resolve() (obs-9) */
  private static final String OBSERVATION = "{\"resourceType\":\"Observation\",\"id\":\"o\",\"status\":\"final\",\"code\":{\"text\":\"Weight\"},"
      + "\"subject\":{\"reference\":\"Patient/p\"}}";
  /** a contained resource is validated against the target profiles of the references to it (CHECK_VALID) */
  private static final String CONTAINED = "{\"resourceType\":\"Condition\",\"id\":\"k\",\"contained\":[{\"resourceType\":\"Practitioner\",\"id\":\"pr\"}],"
      + "\"code\":{\"text\":\"Asthma\"},\"subject\":{\"reference\":\"Patient/p\"},\"asserter\":{\"reference\":\"#pr\"}}";

  private static String patient(String family) {
    return "{\"resourceType\":\"Patient\",\"id\":\"p\",\"name\":[{\"family\":\""+family+"\"}]}";
  }

  /** the narrative the publisher generates for the resource, with Patient/p resolving to the patient given */
  private static String narrative(Element resource, Element patient) throws Exception {
    RenderingContext rc = new RenderingContext(context, new RendererFactory(), new MarkDownProcessor(MarkDownProcessor.Dialect.COMMON_MARK),
        null, "http://hl7.org/fhir", "", Locale.ENGLISH, RenderingContext.ResourceRendererMode.END_USER, RenderingContext.GenerationRules.VALID_RESOURCE);
    rc.setResolver(new IReferenceResolver() {
      @Override
      public ResourceWithReference resolve(RenderingContext ctx, String url, String version) {
        return "Patient/p".equals(url) ? new ResourceWithReference(ResourceReferenceKind.EXTERNAL, url, "Patient-p.html", ResourceWrapper.forResource(ctx, patient)) : null;
      }

      @Override
      public String resolveUri(RenderingContext ctx, String uri) {
        return null;
      }
    });
    ResourceWrapper rw = ResourceWrapper.forResource(rc, resource);
    return new XhtmlComposer(false).compose(rc.getRendererFactory().factory(rw, rc).buildNarrative(rw));
  }

  /**
   * A Reference is weak for validation (the validator only checks that the target exists with the right type), but
   * the page of the resource that refers to it shows the target's content: the condition's narrative names the
   * patient. So when the patient changes, the condition keeps its validation and is rendered again. Only a
   * Reference's reference is weak: DetectedIssue.reference (and Expression.reference, ...) is a uri like any other
   */
  @Test
  void aReferenceIsWeakForValidationOnly() throws Exception {
    RapidoTracker t = tracker();
    Element obs = parseJson(CONDITION);
    Element alice = parseJson(patient("Alice"));
    Element bob = parseJson(patient("Bob"));
    String before = narrative(obs, alice);
    String after = narrative(obs, bob);
    assertTrue(before.contains("Alice"), before);
    assertTrue(after.contains("Bob"), after);

    FileResult b = analyse(parseJson(CONDITION), alice).get("Condition/c");
    FileResult a = analyse(parseJson(CONDITION), bob).get("Condition/c");
    assertTrue(b.getDependsOn().contains("Patient/p"), b.getDependsOn().toString());
    assertEquals(b.getValidationHash(), a.getValidationHash());
    assertFalse(b.getRenderHash().equals(a.getRenderHash()), "the condition's page shows the patient's name");

    // validation reads the target where a definition from outside the IG has a resolve(), and where the resource
    // holds others
    for (String[] r : new String[][] {{OBSERVATION, "Observation/o"}, {CONTAINED, "Condition/k"}}) {
      FileResult rb = analyse(parseJson(r[0]), parseJson(patient("Alice"))).get(r[1]);
      FileResult ra = analyse(parseJson(r[0]), parseJson(patient("Bob"))).get(r[1]);
      assertFalse(rb.getValidationHash().equals(ra.getValidationHash()), r[1]);
    }

    String issue = "{\"resourceType\":\"DetectedIssue\",\"id\":\"d\",\"status\":\"final\",\"reference\":\"http://example.org/fhir/Library/lib\"}";
    String lib = "{\"resourceType\":\"Library\",\"id\":\"lib\",\"url\":\"http://example.org/fhir/Library/lib\",\"status\":\"draft\","
        + "\"type\":{\"text\":\"logic\"},\"description\":\"%s\"}";
    FileResult one = analyse(parseJson(issue), parseJson(String.format(lib, "one"))).get("DetectedIssue/d");
    FileResult two = analyse(parseJson(issue), parseJson(String.format(lib, "two"))).get("DetectedIssue/d");
    assertTrue(one.getDependsOn().contains("Library/lib"), one.getDependsOn().toString());
    assertFalse(one.getValidationHash().equals(two.getValidationHash()));
  }

  private static String profile(String id, String title, String type, String path, String shortText, String targetProfile) {
    return "{\"resourceType\":\"StructureDefinition\",\"id\":\""+id+"\",\"url\":\"http://example.org/fhir/StructureDefinition/"+id+"\","
        + "\"name\":\""+id+"\",\"title\":\""+title+"\",\"status\":\"draft\",\"kind\":\"resource\",\"abstract\":false,\"type\":\""+type+"\","
        + "\"baseDefinition\":\"http://hl7.org/fhir/StructureDefinition/"+type+"\",\"derivation\":\"constraint\",\"differential\":{\"element\":["
        + "{\"id\":\""+path+"\",\"path\":\""+path+"\",\"short\":\""+shortText+"\""
        + (targetProfile == null ? "" : ",\"type\":[{\"code\":\"Reference\",\"targetProfile\":[\"http://example.org/fhir/StructureDefinition/"+targetProfile+"\"]}]")
        + "}]}}";
  }

  /**
   * As the publisher reads them: a targetProfile only needs the target profile's identity, for validation and
   * rendering (the observation profile's page shows the specimen profile's title as a link), and so does a narrative
   * link. Other changes to the targets don't reach them
   */
  @Test
  void targetProfilesAndNarrativeLinksDependOnTheTargetsIdentity() throws Exception {
    String obsProfile = profile("obs", "Obs", "Observation", "Observation.specimen", "the specimen", "spec");
    Element spec = parseJson(profile("spec", "Spec", "Specimen", "Specimen.status", "lifecycle status", null));
    Element specShort = parseJson(profile("spec", "Spec", "Specimen", "Specimen.status", "Bench1 lifecycle status", null));
    Element specTitle = parseJson(profile("spec", "Spec renamed", "Specimen", "Specimen.status", "lifecycle status", null));
    FileResult base = analyse(parseJson(obsProfile), spec).get("StructureDefinition/obs");
    FileResult shortChanged = analyse(parseJson(obsProfile), specShort).get("StructureDefinition/obs");
    FileResult titleChanged = analyse(parseJson(obsProfile), specTitle).get("StructureDefinition/obs");
    assertTrue(base.getDependsOn().isEmpty(), base.getDependsOn().toString());
    assertEquals(base.getValidationHash(), shortChanged.getValidationHash());
    assertEquals(base.getRenderHash(), shortChanged.getRenderHash());
    assertFalse(base.getValidationHash().equals(titleChanged.getValidationHash()));
    assertFalse(base.getRenderHash().equals(titleChanged.getRenderHash()));
    // the validator checks the target profile's type
    Element specType = parseJson(profile("spec", "Spec", "Specimen", "Specimen.status", "lifecycle status", null).replace("\"type\":\"Specimen\"", "\"type\":\"Substance\""));
    assertFalse(base.getValidationHash().equals(analyse(parseJson(obsProfile), specType).get("StructureDefinition/obs").getValidationHash()));
    // and its kind, derivation and abstract
    for (String[] c : new String[][] {{"\"kind\":\"resource\"", "\"kind\":\"logical\""}, {"\"derivation\":\"constraint\"", "\"derivation\":\"specialization\""},
        {"\"abstract\":false", "\"abstract\":true"}}) {
      Element changed = parseJson(profile("spec", "Spec", "Specimen", "Specimen.status", "lifecycle status", null).replace(c[0], c[1]));
      assertFalse(base.getValidationHash().equals(analyse(parseJson(obsProfile), changed).get("StructureDefinition/obs").getValidationHash()), c[1]);
    }
    // and, for a logical model, whether it can be a target
    Element target = parseJson(profile("spec", "Spec", "Specimen", "Specimen.status", "lifecycle status", null).replace("\"status\":\"draft\",",
        "\"status\":\"draft\",\"extension\":[{\"url\":\"http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics\",\"valueCode\":\"can-be-target\"}],"));
    assertFalse(base.getValidationHash().equals(analyse(parseJson(obsProfile), target).get("StructureDefinition/obs").getValidationHash()));

    String linking = "{\"resourceType\":\"Patient\",\"id\":\"q\",\"text\":{\"status\":\"generated\",\"div\":"
        + "\"<div xmlns=\\\"http://www.w3.org/1999/xhtml\\\">Seen by <a href=\\\"http://example.org/fhir/Practitioner/pr\\\">the GP</a></div>\"}}";
    String pr = "{\"resourceType\":\"Practitioner\",\"id\":\"pr\",\"gender\":\"%s\"}";
    FileResult p1 = analyse(parseJson(linking), parseJson(String.format(pr, "female"))).get("Patient/q");
    FileResult p2 = analyse(parseJson(linking), parseJson(String.format(pr, "male"))).get("Patient/q");
    assertTrue(p1.getDependsOn().isEmpty(), p1.getDependsOn().toString());
    assertEquals(p1.getValidationHash(), p2.getValidationHash());
    assertEquals(p1.getRenderHash(), p2.getRenderHash());
    // but it does link to the practitioner
    assertTrue(analyse(parseJson(linking), parseJson(String.format(pr, "female"))).get("Practitioner/pr").getUsedBy().contains("Patient/q"));
  }


  /** a StructureDefinition from a package, with the given element types (profiles), and a resolve() if asked */
  private static void definition(String url, String type, boolean resolve, String... typeProfiles) {
    StructureDefinition sd = new StructureDefinition();
    sd.setUrl(url).setName("Def").setType(type).setDerivation(StructureDefinition.TypeDerivationRule.CONSTRAINT)
      .setBaseDefinition("http://hl7.org/fhir/StructureDefinition/"+type);
    ElementDefinition root = sd.getSnapshot().addElement().setPath(type);
    if (resolve) {
      root.addConstraint().setKey("x-1").setExpression("subject.resolve().exists()");
    }
    for (String p : typeProfiles) {
      sd.getSnapshot().addElement().setPath(type+".code").addType().setCode("CodeableConcept").addProfile(p);
    }
    context.cacheResource(sd);
  }

  private static void sliced(String url, String discriminatorPath) {
    StructureDefinition sd = context.fetchResource(StructureDefinition.class, url);
    sd.getSnapshot().addElement().setPath(sd.getType()+".evidence").getSlicing().addDiscriminator()
      .setType(ElementDefinition.DiscriminatorType.VALUE).setPath(discriminatorPath);
  }

  /**
   * Package definitions that reach each other: a reaches c, which has a resolve(), and b, which reaches a. b is looked at
   * before c in the walk from a: that mustn't leave b known as not reading the targets
   */
  @Test
  void definitionsThatReachEachOtherReadTheTargetsOfEither() throws Exception {
    tracker();
    StructureDefinition a = new StructureDefinition();
    a.setUrl("http://example.org/pkg/StructureDefinition/cycle-a").setName("A").setType("Condition").setDerivation(StructureDefinition.TypeDerivationRule.CONSTRAINT);
    a.getSnapshot().addElement().setPath("Condition.note").addType().setCode("Annotation").addProfile("http://example.org/pkg/StructureDefinition/cycle-c");
    a.getSnapshot().addElement().setPath("Condition.code").addType().setCode("CodeableConcept").addProfile("http://example.org/pkg/StructureDefinition/cycle-b");
    StructureDefinition c = new StructureDefinition();
    c.setUrl("http://example.org/pkg/StructureDefinition/cycle-c").setName("C").setType("Annotation").setDerivation(StructureDefinition.TypeDerivationRule.CONSTRAINT);
    c.getSnapshot().addElement().setPath("Annotation").addConstraint().setKey("c-1").setExpression("author.resolve().exists()");
    context.cacheResource(c);
    StructureDefinition b = new StructureDefinition();
    b.setUrl("http://example.org/pkg/StructureDefinition/cycle-b").setName("B").setType("Condition").setDerivation(StructureDefinition.TypeDerivationRule.CONSTRAINT);
    b.getSnapshot().addElement().setPath("Condition.code").addType().setCode("CodeableConcept").addProfile("http://example.org/pkg/StructureDefinition/cycle-a");
    context.cacheResource(a);
    context.cacheResource(b);
    String claims = "{\"resourceType\":\"Condition\",\"id\":\"%s\",\"meta\":{\"profile\":[\"%s\"]},\"subject\":{\"reference\":\"Patient/p\"}}";
    // one analysis (one tracker), with the resource that claims a looked at first
    String plain = CONDITION.replace("\"id\":\"c\"", "\"id\":\"plain\"");
    Element[] before = {parseJson(String.format(claims, "ca", a.getUrl())), parseJson(String.format(claims, "cb", b.getUrl())), parseJson(plain), parseJson(patient("Alice"))};
    Element[] after = {parseJson(String.format(claims, "ca", a.getUrl())), parseJson(String.format(claims, "cb", b.getUrl())), parseJson(plain), parseJson(patient("Bob"))};
    Map<String, FileResult> rb = analyse(before);
    Map<String, FileResult> ra = analyse(after);
    // a resource that claims neither only needs the patient to exist
    assertEquals(rb.get("Condition/plain").getValidationHash(), ra.get("Condition/plain").getValidationHash());
    assertFalse(rb.get("Condition/ca").getValidationHash().equals(ra.get("Condition/ca").getValidationHash()));
    assertFalse(rb.get("Condition/cb").getValidationHash().equals(ra.get("Condition/cb").getValidationHash()));
  }

  /**
   * In one analysis: what the resource before claimed isn't taken for the next one's. Without a context, what a
   * resource is validated against isn't known, so it may read the targets. Some validators read what a resource refers
   * to whatever the definitions say (a subject-list MeasureReport counts the entries of its Lists), and so does a
   * discriminator path that goes through a reference, in a profile the IG holds
   */
  @Test
  void otherWaysOfReadingTheTargets() throws Exception {
    tracker();
    definition("http://example.org/pkg/StructureDefinition/condition-reads", "Condition", true);
    String claims = "{\"resourceType\":\"Condition\",\"id\":\"c\",\"meta\":{\"profile\":[\"http://example.org/pkg/StructureDefinition/condition-reads\"]},\"subject\":{\"reference\":\"Patient/p\"}}";
    String plain = CONDITION.replace("\"id\":\"c\"", "\"id\":\"plain\"");
    Map<String, FileResult> rb = analyse(parseJson(claims), parseJson(plain), parseJson(patient("Alice")));
    Map<String, FileResult> ra = analyse(parseJson(claims), parseJson(plain), parseJson(patient("Bob")));
    assertFalse(rb.get("Condition/c").getValidationHash().equals(ra.get("Condition/c").getValidationHash()));
    rb = analyse(parseJson(claims), parseJson(plain), parseJson(patient("Alice")));
    ra = analyse(parseJson(claims), parseJson(plain), parseJson(patient("Bob")));
    assertEquals(rb.get("Condition/plain").getValidationHash(), ra.get("Condition/plain").getValidationHash());

    PublisherFields pf = new PublisherFields();
    RapidoTracker noContext = tracker(pf);
    pf.context = null;
    List<String> hashes = new ArrayList<>();
    for (String name : new String[] {"Alice", "Bob"}) {
      List<FileNode> nodes = new ArrayList<>();
      for (Element e : new Element[] {parseJson(plain), parseJson(patient(name))}) {
        FetchedFile f = new FetchedFile(e.getIdBase()+".json");
        f.addResource(e.getIdBase()).setElement(e).setId(e.getIdBase());
        nodes.add(new FileNode(e.fhirType()+"/"+e.getIdBase(), "source").addResource(noContext.resourceNode(f.getResources().get(0))));
      }
      hashes.add(new RapidoDependencyAnalyser("http://example.org/fhir").analyse(nodes).get("Condition/plain").getValidationHash());
    }
    assertFalse(hashes.get(0).equals(hashes.get(1)), "without a context");

    // nor when the context fails
    SimpleWorkerContext failing = mock(SimpleWorkerContext.class);
    when(failing.fetchResource(org.mockito.ArgumentMatchers.eq(StructureDefinition.class), org.mockito.ArgumentMatchers.anyString()))
      .thenThrow(new IllegalStateException("broken"));
    pf.context = failing;
    hashes.clear();
    for (String name : new String[] {"Alice", "Bob"}) {
      List<FileNode> nodes = new ArrayList<>();
      for (Element e : new Element[] {parseJson(plain), parseJson(patient(name))}) {
        FetchedFile f = new FetchedFile(e.getIdBase()+".json");
        f.addResource(e.getIdBase()).setElement(e).setId(e.getIdBase());
        nodes.add(new FileNode(e.fhirType()+"/"+e.getIdBase(), "source").addResource(new RapidoTracker(pf).resourceNode(f.getResources().get(0))));
      }
      hashes.add(new RapidoDependencyAnalyser("http://example.org/fhir").analyse(nodes).get("Condition/plain").getValidationHash());
    }
    assertFalse(hashes.get(0).equals(hashes.get(1)), "the context failed");

    // before R4, definitions are linked by Reference: every Reference is a full edge
    pf.context = context;
    pf.version = "3.0.2";
    hashes.clear();
    for (String name : new String[] {"Alice", "Bob"}) {
      List<FileNode> nodes = new ArrayList<>();
      for (Element e : new Element[] {parseJson(plain), parseJson(patient(name))}) {
        FetchedFile f = new FetchedFile(e.getIdBase()+".json");
        f.addResource(e.getIdBase()).setElement(e).setId(e.getIdBase());
        nodes.add(new FileNode(e.fhirType()+"/"+e.getIdBase(), "source").addResource(new RapidoTracker(pf).resourceNode(f.getResources().get(0))));
      }
      hashes.add(new RapidoDependencyAnalyser("http://example.org/fhir").analyse(nodes).get("Condition/plain").getValidationHash());
    }
    assertFalse(hashes.get(0).equals(hashes.get(1)), "STU3");

    String report = "{\"resourceType\":\"MeasureReport\",\"id\":\"mr\",\"status\":\"complete\",\"type\":\"subject-list\",\"measure\":\"http://example.org/fhir/Measure/m\","
        + "\"group\":[{\"population\":[{\"count\":1,\"subjectResults\":{\"reference\":\"List/l\"}}]}]}";
    String list = "{\"resourceType\":\"List\",\"id\":\"l\",\"status\":\"current\",\"mode\":\"snapshot\",\"entry\":[%s]}";
    String entry = "{\"item\":{\"display\":\"x\"}}";
    assertFalse(analyse(parseJson(report), parseJson(String.format(list, entry))).get("MeasureReport/mr").getValidationHash()
        .equals(analyse(parseJson(report), parseJson(String.format(list, entry+","+entry))).get("MeasureReport/mr").getValidationHash()));

    String profile = "{\"resourceType\":\"StructureDefinition\",\"id\":\"sliced\",\"url\":\"http://example.org/fhir/StructureDefinition/sliced\","
        + "\"name\":\"Sliced\",\"status\":\"draft\",\"kind\":\"resource\",\"abstract\":false,\"type\":\"Condition\","
        + "\"baseDefinition\":\"http://hl7.org/fhir/StructureDefinition/Condition\",\"derivation\":\"constraint\",\"differential\":{\"element\":["
        + "{\"id\":\"Condition.evidence\",\"path\":\"Condition.evidence\",\"slicing\":{\"discriminator\":[{\"type\":\"value\",\"path\":\"%s\"}],\"rules\":\"open\"}}]}}";
    String claimsLocal = "{\"resourceType\":\"Condition\",\"id\":\"local\",\"meta\":{\"profile\":[\"http://example.org/fhir/StructureDefinition/sliced\"]},"
        + "\"subject\":{\"reference\":\"Patient/p\"}}";
    for (String[] c : new String[][] {{"reference", "true"}, {"code", "false"}}) {
      FileResult b = analyse(parseJson(String.format(profile, c[0])), parseJson(claimsLocal), parseJson(patient("Alice"))).get("Condition/local");
      FileResult a = analyse(parseJson(String.format(profile, c[0])), parseJson(claimsLocal), parseJson(patient("Bob"))).get("Condition/local");
      assertEquals(Boolean.parseBoolean(c[1]), !b.getValidationHash().equals(a.getValidationHash()), c[0]);
    }
  }

  /**
   * The analysis only sees the IG's own resources: a profile or extension from a package that has a resolve() makes
   * what claims or uses it read the targets of its references too (directly, or through the types it uses)
   */
  @Test
  void definitionsFromPackagesCanReadTheTargets() throws Exception {
    tracker();
    definition("http://example.org/pkg/StructureDefinition/condition-resolve", "Condition", true);
    definition("http://example.org/pkg/StructureDefinition/ext-resolve", "Extension", true);
    definition("http://example.org/pkg/StructureDefinition/condition-plain", "Condition", false);
    definition("http://example.org/pkg/StructureDefinition/cc-resolve", "CodeableConcept", true);
    definition("http://example.org/pkg/StructureDefinition/condition-typed", "Condition", false, "http://example.org/pkg/StructureDefinition/cc-resolve");
    String claims = "{\"resourceType\":\"Condition\",\"id\":\"c\",\"meta\":{\"profile\":[\"%s\"]},\"subject\":{\"reference\":\"Patient/p\"}}";
    String uses = "{\"resourceType\":\"Condition\",\"id\":\"c\",\"extension\":[{\"url\":\"%s\",\"valueBoolean\":true}],\"subject\":{\"reference\":\"Patient/p\"}}";
    String[][] cases = {
        {String.format(claims, "http://example.org/pkg/StructureDefinition/condition-resolve"), "true"},
        {String.format(uses, "http://example.org/pkg/StructureDefinition/ext-resolve"), "true"},
        {String.format(claims, "http://example.org/pkg/StructureDefinition/condition-typed"), "true"},
        {String.format(claims, "http://example.org/pkg/StructureDefinition/condition-plain"), "false"},
        {String.format(uses, "http://example.org/pkg/StructureDefinition/condition-plain"), "false"},
        {String.format(claims, "http://example.org/pkg/StructureDefinition/condition-sliced"), "true"},
        {String.format(claims, "http://example.org/pkg/StructureDefinition/condition-sliced-by-reference"), "true"},
        {String.format(claims, "http://example.org/pkg/StructureDefinition/condition-logical"), "true"},
        {String.format(claims, "http://example.org/pkg/StructureDefinition/condition-imposes"), "true"}};
    definition("http://example.org/pkg/StructureDefinition/condition-sliced", "Condition", false);
    sliced("http://example.org/pkg/StructureDefinition/condition-sliced", "evidence.resolve()");
    definition("http://example.org/pkg/StructureDefinition/condition-sliced-by-reference", "Condition", false);
    sliced("http://example.org/pkg/StructureDefinition/condition-sliced-by-reference", "subject.reference");
    definition("http://example.org/pkg/StructureDefinition/logical-resolve", "http://example.org/pkg/StructureDefinition/logical-resolve", true);
    definition("http://example.org/pkg/StructureDefinition/condition-logical", "Condition", false);
    ((StructureDefinition) context.fetchResource(StructureDefinition.class, "http://example.org/pkg/StructureDefinition/condition-logical"))
      .getSnapshot().addElement().setPath("Condition.note").addType().setCode("http://example.org/pkg/StructureDefinition/logical-resolve");
    definition("http://example.org/pkg/StructureDefinition/condition-imposes", "Condition", false);
    context.fetchResource(StructureDefinition.class, "http://example.org/pkg/StructureDefinition/condition-imposes")
      .addExtension("http://hl7.org/fhir/StructureDefinition/structuredefinition-imposeProfile",
          new org.hl7.fhir.model.core.CanonicalType("http://example.org/pkg/StructureDefinition/condition-resolve"));
    for (String[] c : cases) {
      FileResult before = analyse(parseJson(c[0]), parseJson(patient("Alice"))).get("Condition/c");
      FileResult after = analyse(parseJson(c[0]), parseJson(patient("Bob"))).get("Condition/c");
      assertEquals(Boolean.parseBoolean(c[1]), !before.getValidationHash().equals(after.getValidationHash()), c[0]);
    }
  }

  /**
   * Whatever else in a resource the analysis can't handle: the build is a complete one, as a build without -rapido
   * would be, not one that fails
   */
  @Test
  void aResourceTheAnalysisCantHandleMakesItACompleteBuild() throws Exception {
    PublisherFields pf = new PublisherFields();
    RapidoTracker t = tracker(pf);
    FetchedFile f = new FetchedFile("odd.json");
    FetchedResource r = mock(FetchedResource.class);
    when(r.fhirType()).thenThrow(new Error("Attempt to read a single element when there is more than one present (name)"));
    f.getResources().add(r);
    pf.fileList.add(f);

    String why = t.analyseDependencies();
    assertNotNull(why);
    assertTrue(why.contains("more than one present"), why);
  }

  /**
   * Every example in the FHIR specification (an 18 MB package, downloaded once into the package cache: skip it with
   * -Drapido.corpus.skip=true)
   */
  @Test
  void everySpecExampleCanBeAnalysed() throws Exception {
    assumeTrue(!Boolean.getBoolean("rapido.corpus.skip"), "rapido.corpus.skip is set");
    RapidoTracker t = tracker();
    NpmPackage examples = fetch("hl7.fhir.r5.examples", "5.0.0");
    assumeTrue(examples != null, noPackage);

    List<FileNode> nodes = new ArrayList<>();
    int unparsed = 0;
    for (String name : examples.listResources()) {
      Element e;
      try (InputStream in = examples.loadResource(name)) {
        e = Manager.parseSingle(context, in, FhirFormat.JSON);
      } catch (Exception ex) {
        unparsed++; // not what this is testing
        continue;
      }
      FetchedFile f = new FetchedFile(name);
      f.addResource(name).setElement(e).setId(e.getIdBase());
      nodes.add(new FileNode(name, "source").addResource(t.resourceNode(f.getResources().get(0))));
    }
    assertTrue(nodes.size() > 1000, nodes.size() + " examples analysed, " + unparsed + " not parsed");
    assertEquals(nodes.size(), new RapidoDependencyAnalyser("http://hl7.org/fhir").analyse(nodes).size());
  }
}
