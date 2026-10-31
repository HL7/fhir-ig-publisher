package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.FileNode;
import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.FileResult;
import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.utilities.formats.FhirFormat;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.services.elementmodel.Manager;
import org.hl7.fhir.standalone.context.SimpleWorkerContext;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
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
