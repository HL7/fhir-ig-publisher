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

  private static IWorkerContext context;
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
    return analyse(Map.of(), resources);
  }

  /** as analyse(resources), with the web paths given (Type/id to path), and Type-id.html for the others */
  private Map<String, FileResult> analyse(Map<String, String> paths, Element... resources) throws Exception {
    RapidoTracker t = tracker();
    List<FileNode> nodes = new ArrayList<>();
    for (Element e : resources) {
      FetchedFile f = new FetchedFile(e.fhirType()+"-"+e.getIdBase()+".json");
      String key = e.fhirType()+"/"+e.getIdBase();
      f.addResource(e.getIdBase()).setElement(e).setId(e.getIdBase());
      f.getResources().get(0).setPath(paths.getOrDefault(key, e.fhirType()+"-"+e.getIdBase()+".html"));
      nodes.add(new FileNode(e.fhirType()+"/"+e.getIdBase(), "source").addResource(t.resourceNode(f.getResources().get(0))));
    }
    return new RapidoDependencyAnalyser("http://example.org/fhir").analyse(nodes);
  }

  private static final String OBSERVATION = "{\"resourceType\":\"Observation\",\"id\":\"o\",\"status\":\"final\",\"code\":{\"text\":\"Weight\"},"
      + "\"subject\":{\"reference\":\"Patient/p\"}}";

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
   * the page of the resource that refers to it shows the target's content: the observation's narrative names the
   * patient. So when the patient changes, the observation keeps its validation and is rendered again. Only a
   * Reference's reference is weak: DetectedIssue.reference (and Expression.reference, ...) is a uri like any other
   */
  @Test
  void aReferenceIsWeakForValidationOnly() throws Exception {
    RapidoTracker t = tracker();
    Element obs = parseJson(OBSERVATION);
    Element alice = parseJson(patient("Alice"));
    Element bob = parseJson(patient("Bob"));
    String before = narrative(obs, alice);
    String after = narrative(obs, bob);
    assertTrue(before.contains("Alice"), before);
    assertTrue(after.contains("Bob"), after);

    FileResult b = analyse(parseJson(OBSERVATION), alice).get("Observation/o");
    FileResult a = analyse(parseJson(OBSERVATION), bob).get("Observation/o");
    assertTrue(b.getDependsOn().contains("Patient/p"), b.getDependsOn().toString());
    assertEquals(b.getValidationHash(), a.getValidationHash());
    assertFalse(b.getRenderHash().equals(a.getRenderHash()), "the observation's page shows the patient's name");

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
    // the link goes to the target's page
    FileResult moved = analyse(Map.of("StructureDefinition/spec", "moved.html"), parseJson(obsProfile), spec).get("StructureDefinition/obs");
    assertFalse(base.getRenderHash().equals(moved.getRenderHash()));

    String linking = "{\"resourceType\":\"Patient\",\"id\":\"q\",\"text\":{\"status\":\"generated\",\"div\":"
        + "\"<div xmlns=\\\"http://www.w3.org/1999/xhtml\\\">Seen by <a href=\\\"http://example.org/fhir/Practitioner/pr\\\">the GP</a></div>\"}}";
    String pr = "{\"resourceType\":\"Practitioner\",\"id\":\"pr\",\"gender\":\"%s\"}";
    FileResult p1 = analyse(parseJson(linking), parseJson(String.format(pr, "female"))).get("Patient/q");
    FileResult p2 = analyse(parseJson(linking), parseJson(String.format(pr, "male"))).get("Patient/q");
    assertTrue(p1.getDependsOn().isEmpty(), p1.getDependsOn().toString());
    assertEquals(p1.getValidationHash(), p2.getValidationHash());
    assertEquals(p1.getRenderHash(), p2.getRenderHash());
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
