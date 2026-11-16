package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.templates.Template;
import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.CodeSystem;
import org.hl7.fhir.model.core.DomainResource;
import org.hl7.fhir.model.core.ElementDefinition;
import org.hl7.fhir.model.core.Enumerations;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.model.core.ValueSet;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.services.renderers.RendererFactory;
import org.hl7.fhir.services.renderers.utils.RenderingContext;
import org.hl7.fhir.services.renderers.utils.ResourceWrapper;
import org.hl7.fhir.standalone.context.SimpleWorkerContext;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.NamedItemList;
import org.hl7.fhir.utilities.UserDataNames;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.hl7.fhir.utilities.validation.ValidationOptions;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.validation.BaseValidator;
import org.hl7.fhir.validation.instance.InstanceValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A rapido build in watch mode reuses what the last build made for what didn't change, as without watch mode: each
 * rebuild loads the files again, and compares them with the state the last build saved
 */
class PublisherRapidoWatchTest {

  @TempDir
  Path dir;

  private final String uuid = HierarchicalTableGenerator.uuid;

  @AfterEach
  void restoreUuid() {
    HierarchicalTableGenerator.uuid = uuid;
  }

  @Test
  void aWatchBuildRendersTheChangedFileAndWhatDependsOnIt() throws Exception {
    HierarchicalTableGenerator.uuid = "the-last-build";
    PublisherFields first = build("a");
    assertTrue(first.rapido.isCompleteBuild());
    finish(first);

    HierarchicalTableGenerator.uuid = "a-new-session"; // as resetForRebuild leaves it

    // the patient changes: the observation that refers to it is rendered again, the other patient isn't. The IG and
    // the page are always processed
    PublisherFields pf = build("a2");

    assertFalse(pf.rapido.isCompleteBuild(), pf.rapido.getReason());
    assertEquals(List.of("Observation-o.json (something it depends on changed)", "Patient-a.json (changed)",
        "ig.json (always processed)", "page.md (always processed)"), pf.rapido.listRenderDirty());
    assertEquals("the-last-build", HierarchicalTableGenerator.uuid);
  }

  @Test
  void withoutARecordOfWhatTheLastBuildWroteTheBuildIsComplete() throws Exception {
    finish(build("a"));
    // an older publisher built it, or the record was removed
    Files.delete(dir.resolve(".build-outputs.json"));

    PublisherFields pf = build("a2");

    assertTrue(pf.rapido.isCompleteBuild());
    assertEquals("what the last build wrote in the temp folder isn't all there", pf.rapido.getReason());
  }

  @Test
  void whenWhatTheLastBuildWroteInTheTempFolderIsGoneTheBuildIsComplete() throws Exception {
    finish(build("a"));
    Path page = dir.resolve("temp").resolve("Patient-b.html");
    assertTrue(Files.exists(page));
    Files.delete(page);

    PublisherFields pf = build("a");

    assertTrue(pf.rapido.isCompleteBuild());
    assertEquals("what the last build wrote in the temp folder isn't all there", pf.rapido.getReason());
    assertTrue(Files.isDirectory(dir.resolve("temp")), "the temp folder is cleared for the complete build");
    assertFalse(Files.exists(dir.resolve("temp").resolve("Patient-a.html")));
  }

  /**
   * Rendering that failed with an exception (a file that couldn't be written, say) may not fail the same way again:
   * what it made isn't reused
   */
  @Test
  void whatRenderingThatFailedMadeIsNotReused() throws Exception {
    finish(build("a"), "Patient-b.json");

    PublisherFields pf = build("a");

    assertFalse(pf.rapido.isCompleteBuild(), pf.rapido.getReason());
    assertEquals(List.of("Patient-b.json (outputs from the last build are missing; render only)", "ig.json (always processed)",
        "page.md (always processed)"), pf.rapido.listRenderDirty());
  }

  /** A file whose outputs are reused keeps them: they're not removed as written by no file, and the next build reuses them too */
  @Test
  void whatAReusedFileWroteIsKept() throws Exception {
    finish(build("a"));

    PublisherFields pf = build("a2");
    RapidoOutputs.Change change = finish(pf);

    assertEquals(List.of("Patient-b.json"), replayed);
    Path page = dir.resolve("temp").resolve("Patient-b.html");
    assertFalse(change.getGone().contains(page.toString()), change.getGone().toString());
    assertTrue(Files.exists(page));

    pf = build("a2");
    assertFalse(pf.rapido.isCompleteBuild(), pf.rapido.getReason());
    assertEquals(List.of("ig.json (always processed)", "page.md (always processed)"), pf.rapido.listRenderDirty());
  }

  /** Something else wrote over what the file wrote: what the file made isn't reused */
  @Test
  void anOutputWrittenOverIsntReused() throws Exception {
    finish(build("a"));
    Files.writeString(dir.resolve("temp").resolve("Patient-b.html"), "something else");

    PublisherFields pf = build("a");

    assertFalse(pf.rapido.canReplay(file(pf, "Patient-b.json"), RapidoTracker.PHASE_HTML));
    assertTrue(pf.rapido.listRenderDirty().contains("Patient-b.json (an output was overwritten: Patient-b.html; render only)"),
        pf.rapido.listRenderDirty().toString());
  }

  /** A reused profile says it has a UML diagram, as it did when it was rendered (_data/structuredefinitions.json) */
  @Test
  void aReusedFileStillHasItsUmlDiagram() throws Exception {
    PublisherFields pf = build("a");
    FetchedResource r = file(pf, "Patient-b.json").getResources().get(0);
    when(r.isUmlGenerated()).thenReturn(false, true); // rendering it makes the diagram
    finish(pf);

    pf = build("a2");
    FetchedFile b = file(pf, "Patient-b.json");
    assertTrue(pf.rapido.canReplay(b, RapidoTracker.PHASE_HTML));
    assertEquals(java.util.Set.of(0), pf.rapido.replay(b, RapidoTracker.PHASE_HTML).umlGenerated);
  }

  /** what validating a resource attached to its elements (the qa page's signposts) comes back when it's reused */
  @Test
  void aReusedValidationPutsBackItsSignposts() throws Exception {
    PublisherFields pf = build("a");
    ValidationMessage sp = validateB(pf);
    finish(pf);

    pf = build("a2");
    validator(pf);
    FetchedResource b = withRealElement(file(pf, "Patient-b.json"));
    List<ValidationMessage> reused = pf.rapido.reuseValidation(file(pf, "Patient-b.json"), b);
    assertEquals(1, reused == null ? -1 : reused.size());
    assertEquals("reused 1 of 1 validations", pf.rapido.validationSummary());
    Element name = b.getElement().getChildList().get(0);
    assertEquals(1, name.getMessages().size());
    assertEquals(sp.getMessage(), name.getMessages().get(0).getMessage());
    // the reported message comes back as the same object, as qa matches and counts it once
    assertSame(reused.get(0), name.getMessages().get(0));
    assertFalse(b.getElement().hasMessages());
  }

  /** a validation saved where a signpost can't be put back (another element tree) is done again */
  @Test
  void aValidationWhoseSignpostsCantComeBackIsDoneAgain() throws Exception {
    PublisherFields pf = build("a");
    FetchedFile profile = profile(pf);
    validateB(pf, PROFILE);
    pf.fileList.remove(profile);
    finish(pf);

    pf = build("a2");
    profile = profile(pf);
    validator(pf);
    FetchedResource b = withRealElement(file(pf, "Patient-b.json"));
    b.getElement().getChildList().clear();
    assertNull(pf.rapido.reuseValidation(file(pf, "Patient-b.json"), b));
    // and nothing it would have done is done, as validating it again does that
    assertTrue(b.getFoundProfiles().isEmpty());
    verify(profile.getResources().get(0), never()).addFoundExample(any());
  }

  private static final String PROFILE = "http://example.org/ig/StructureDefinition/p";

  private static final String BASE = "http://example.org/dep/StructureDefinition/base";
  private static final String RELATIVE = "Also see the [Location](location.html#) resource.";

  /**
   * Rendering a profile looks up the profile it's based on in the element map cache the build shares, and the first
   * look up makes the relative links in that profile's snapshot absolute, in the context: the combined package has
   * them so. A file whose html is replayed does the look ups its rendering did, so that the profile comes out as it
   * does from a complete build
   */
  @Test
  void aReplayedFileDoesTheLookUpsItsRenderingDid() throws Exception {
    context = r5();
    PublisherFields pf = build("a");
    StructureDefinition base = base(context, pf);
    rendering = f -> {
      if (f.getLoadPath().equals("Patient-b.json")) {
        lookUp(pf, BASE);
      }
    };
    finish(pf);
    String complete = comment(base);
    assertNotEquals(RELATIVE, complete);

    PublisherFields pf2 = build("a2");
    base = base(context, pf2); // loaded again, as each build does
    FetchedFile b = file(pf2, "Patient-b.json");
    assertTrue(pf2.rapido.canReplay(b, RapidoTracker.PHASE_HTML));
    PublisherGenerator generator = new PublisherGenerator(new PublisherSettings());
    generator.setPf(pf2);
    generator.replayGeneration(b, RapidoTracker.PHASE_HTML);

    assertEquals(complete, comment(base));
  }

  /**
   * A file that finds a profile already in the cache records the look up too: the file that put it there may be
   * rendered again and not use it, and then only the replayed file's look up updates it
   */
  @Test
  void aLookUpThatFindsTheProfileInTheCacheIsRecorded() throws Exception {
    context = r5();
    PublisherFields pf = build("a");
    StructureDefinition base = base(context, pf);
    rendering = f -> {
      if (f.getLoadPath().startsWith("Patient-")) {
        lookUp(pf, BASE); // Patient/a puts it in the cache, Patient/b finds it there
      }
    };
    finish(pf);
    String complete = comment(base);

    PublisherFields pf2 = build("a2"); // Patient/a changed, and its rendering doesn't use the profile now
    base = base(context, pf2);
    FetchedFile b = file(pf2, "Patient-b.json");
    assertTrue(pf2.rapido.canReplay(b, RapidoTracker.PHASE_HTML));
    assertEquals(List.of(BASE), pf2.rapido.replay(b, RapidoTracker.PHASE_HTML).elementMaps);
    generator(pf2).replayGeneration(b, RapidoTracker.PHASE_HTML);

    assertEquals(complete, comment(base));
  }

  /** a look up that doesn't find the profile changes nothing, and the renderer may expect it to fail: it's not recorded */
  @Test
  void aLookUpThatDoesNotFindTheProfileIsNotRecorded() throws Exception {
    context = r5();
    PublisherFields pf = build("a");
    base(context, pf);
    rendering = f -> {
      if (f.getLoadPath().equals("Patient-b.json")) {
        lookUp(pf, BASE);
        // as the renderer looks up the root element of a logical model's elements
        assertThrows(FHIRException.class, () -> lookUp(pf, "http://example.org/dep/StructureDefinition/missing"));
      }
    };
    finish(pf);

    PublisherFields pf2 = build("a2");
    assertEquals(List.of(BASE), pf2.rapido.replay(file(pf2, "Patient-b.json"), RapidoTracker.PHASE_HTML).elementMaps);
  }

  /** the look ups a replayed file redoes found their profiles when it was rendered: if they fail now, it's reported */
  @Test
  void aLookUpThatFailsWhenItsRedoneIsReported() throws Exception {
    context = r5();
    PublisherFields pf = build("a");
    base(context, pf);
    rendering = f -> {
      if (f.getLoadPath().equals("Patient-b.json")) {
        lookUp(pf, BASE);
      }
    };
    finish(pf);

    PublisherFields pf2 = build("a2");
    context.dropResource(base(context, pf2)); // not there this time
    FetchedFile b = file(pf2, "Patient-b.json");
    generator(pf2).replayGeneration(b, RapidoTracker.PHASE_HTML);

    assertEquals(1, b.getErrors().size());
    ValidationMessage vm = b.getErrors().get(0);
    assertEquals(IssueType.EXCEPTION, vm.getType());
    assertEquals(IssueSeverity.ERROR, vm.getLevel());
    assertTrue(vm.getMessage().contains(BASE), vm.getMessage());
  }

  /** any exception, not just a profile that isn't found: it's reported against the file, as rendering it reports it */
  @Test
  void anExceptionInALookUpThatsRedoneIsReported() throws Exception {
    context = r5();
    PublisherFields pf = build("a");
    base(context, pf);
    rendering = f -> {
      if (f.getLoadPath().equals("Patient-b.json")) {
        lookUp(pf, BASE);
      }
    };
    finish(pf);

    PublisherFields pf2 = build("a2");
    StructureDefinition base = base(context, pf2);
    base.getSnapshot().getElementFirstRep().addType().setCode("Patient").addProfileElement(); // a profile with no value
    FetchedFile b = file(pf2, "Patient-b.json");
    generator(pf2).replayGeneration(b, RapidoTracker.PHASE_HTML);

    assertEquals(1, b.getErrors().size());
    assertEquals(IssueType.EXCEPTION, b.getErrors().get(0).getType());
    assertTrue(b.getErrors().get(0).getMessage().contains(BASE), b.getErrors().get(0).getMessage());
  }

  /**
   * A file's html is rendered with the build's anchor set, and a renderer doesn't put an anchor that's in it in a page:
   * a value set rendered after a code system with the same id has no anchors for the id. When the code system's file
   * is replayed, the value set's page comes out as it does from a complete build, without them
   */
  @Test
  void aReplayedFilePutsItsAnchorsInTheBuildsAnchorSet() throws Exception {
    context = r5();
    String complete = renderingBuild("a", Map.of("Patient-b.json", codeSystem("x"), "Observation-o.json", valueSet("x")))
        .get("Observation-o.json");
    assertFalse(complete.contains("name=\"x\""), complete);

    String differential = renderingBuild("a2", Map.of("Patient-b.json", codeSystem("x"), "Observation-o.json", valueSet("x")))
        .get("Observation-o.json");

    assertEquals(List.of("Patient-b.json"), replayed);
    assertEquals(complete, differential);
  }

  /**
   * Anchors are plain ids, so they clash across types and unrelated files: a value set rendered after a code system
   * with its id has no anchors for it. When the code system gets another id, a complete build gives the value set's
   * page the anchors, so its html isn't replayed
   */
  @Test
  void aFileIsntReplayedWhenAnAnchorItFoundTakenIsFree() throws Exception {
    context = r5();
    String first = renderingBuild("a", Map.of("Patient-a.json", codeSystem("x"), "Patient-b.json", valueSet("x"))).get("Patient-b.json");
    assertFalse(first.contains("name=\"x\""), first);

    String second = renderingBuild("a2", Map.of("Patient-a.json", codeSystem("y"), "Patient-b.json", valueSet("x"))).get("Patient-b.json");

    assertEquals(List.of(), replayed);
    assertTrue(second.contains("name=\"x\""), second);
    assertTrue(second.contains("name=\"hcx\""), second);
  }

  /**
   * The reverse: the value set put the anchors in its page, and the code system before it now has its id. A complete
   * build gives the anchors to the code system only, so the value set's html isn't replayed
   */
  @Test
  void aFileIsntReplayedWhenAnAnchorItAddedIsTaken() throws Exception {
    context = r5();
    String first = renderingBuild("a", Map.of("Patient-a.json", codeSystem("y"), "Patient-b.json", valueSet("x"))).get("Patient-b.json");
    assertTrue(first.contains("name=\"x\""), first);

    String second = renderingBuild("a2", Map.of("Patient-a.json", codeSystem("x"), "Patient-b.json", valueSet("x"))).get("Patient-b.json");

    assertEquals(List.of(), replayed);
    assertFalse(second.contains("name=\"x\""), second);
  }

  /**
   * A Bundle's entries keep their own narrative, and the renderer strips the anchors in it that are in the build's
   * anchor set (stripAnchorsByName), but leaves a free one in the page without adding it. When a code system before it
   * now has that id, a complete build strips the anchor, so the Bundle's html isn't replayed
   */
  @Test
  void aFileIsntReplayedWhenAnAnchorItFoundFreeIsTaken() throws Exception {
    context = r5();
    String first = strippingBuild("a", codeSystem("y")).get("Patient-b.json");
    assertTrue(first.contains("name=\"x\""), first);

    String second = strippingBuild("a2", codeSystem("x")).get("Patient-b.json");

    assertEquals(List.of(), replayed);
    assertFalse(second.contains("name=\"x\""), second);
  }

  /** a Bundle-like file that found an anchor free, and still does, is replayed */
  @Test
  void aFileThatFindsAnAnchorStillFreeIsReplayed() throws Exception {
    context = r5();
    strippingBuild("a", codeSystem("y"));

    strippingBuild("a2", codeSystem("z"));

    assertEquals(List.of("Patient-b.json"), replayed);
  }

  /**
   * a build where Patient-a.json renders the code system, and Patient-b.json strips the anchors in the build's set from
   * a narrative with an anchor x, as BundleRenderer does with an entry's narrative
   */
  private Map<String, String> strippingBuild(String patientContent, CodeSystem cs) throws Exception {
    Map<String, String> pages = new HashMap<>();
    PublisherFields pf = build(patientContent);
    pf.rc = renderingContext(context);
    rendering = f -> {
      if (f.getLoadPath().equals("Patient-a.json")) {
        render(pf, f, pages, Map.of("Patient-a.json", cs));
      } else if (f.getLoadPath().equals("Patient-b.json")) {
        RenderingContext lrc = pf.rc.copy(true);
        XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
        div.addTag("a").setAttribute("name", "x");
        div.addText("entry");
        div.stripAnchorsByName(lrc.getAnchors());
        try {
          pages.put(f.getLoadPath(), new XhtmlComposer(XhtmlComposer.HTML).compose(div));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };
    finish(pf);
    return pages;
  }

  /** a file that found an anchor taken, and still does, is replayed */
  @Test
  void aFileThatFindsTheAnchorsAsTheyWereIsReplayed() throws Exception {
    context = r5();
    renderingBuild("a", Map.of("Patient-a.json", codeSystem("x"), "Patient-b.json", valueSet("x")));

    renderingBuild("a2", Map.of("Patient-a.json", codeSystem("x"), "Patient-b.json", valueSet("x")));

    assertEquals(List.of("Patient-b.json"), replayed);
  }

  /** a build whose files render the resources given for them, and the pages those that weren't replayed rendered */
  private Map<String, String> renderingBuild(String patientContent, Map<String, DomainResource> resources) throws Exception {
    Map<String, String> pages = new HashMap<>();
    PublisherFields pf = build(patientContent);
    pf.rc = renderingContext(context);
    rendering = f -> render(pf, f, pages, resources);
    finish(pf);
    return pages;
  }

  /** renders the resource given for the file, if any, as generateHtmlOutputsInner renders a file */
  private static void render(PublisherFields pf, FetchedFile f, Map<String, String> pages, Map<String, DomainResource> resources) {
    DomainResource res = resources.get(f.getLoadPath());
    if (res == null) {
      return;
    }
    RenderingContext lrc = pf.rc.copy(true);
    try {
      lrc.getRendererFactory().factory(res, lrc).renderOrError(ResourceWrapper.forResource(lrc, res));
      pages.put(f.getLoadPath(), new XhtmlComposer(XhtmlComposer.HTML).compose(res.getText().getDiv()));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static CodeSystem codeSystem(String id) {
    CodeSystem cs = new CodeSystem();
    cs.setId(id);
    cs.setUrl("http://example.org/ig/CodeSystem/"+id);
    cs.setContent(Enumerations.CodeSystemContentMode.COMPLETE);
    cs.addConcept().setCode("a");
    return cs;
  }

  private static ValueSet valueSet(String id) {
    ValueSet vs = new ValueSet();
    vs.setId(id);
    vs.setUrl("http://example.org/ig/ValueSet/"+id);
    vs.getCompose().addInclude().setSystem("http://example.org/ig/CodeSystem/"+id);
    return vs;
  }

  private static RenderingContext renderingContext(SimpleWorkerContext context) {
    return new RenderingContext(context, new RendererFactory(), new MarkDownProcessor(MarkDownProcessor.Dialect.COMMON_MARK),
        ValidationOptions.defaults(), "http://hl7.org/fhir/R5/", "", null, RenderingContext.ResourceRendererMode.TECHNICAL,
        RenderingContext.GenerationRules.IG_PUBLISHER);
  }

  private static PublisherGenerator generator(PublisherFields pf) {
    PublisherGenerator generator = new PublisherGenerator(new PublisherSettings());
    generator.setPf(pf);
    return generator;
  }

  private static SimpleWorkerContext r5;
  /** the context the builds use, if any */
  private SimpleWorkerContext context;

  private static SimpleWorkerContext r5() throws Exception {
    if (r5 == null) {
      NpmPackage npm;
      try {
        npm = new FilesystemPackageCacheManager.Builder().build().loadPackage("hl7.fhir.r5.core", "5.0.0");
      } catch (Exception e) {
        npm = null;
      }
      assumeTrue(npm != null, "hl7.fhir.r5.core#5.0.0 can't be fetched");
      r5 = new SimpleWorkerContext.SimpleWorkerContextBuilder(ModelContext.fullCoreContext()).withAllowLoadingDuplicates(true).fromPackage(npm);
    }
    return r5;
  }

  /** a profile from a dependency, as the loader leaves it: a relative link in its snapshot, and where its pages are */
  private static StructureDefinition base(SimpleWorkerContext context, PublisherFields pf) {
    StructureDefinition old = context.fetchResource(StructureDefinition.class, BASE);
    if (old != null) {
      context.dropResource(old);
    }
    StructureDefinition sd = new StructureDefinition();
    sd.setId("base");
    sd.setUrl(BASE);
    sd.setName("Base");
    sd.setType("Patient");
    sd.setDerivation(TypeDerivationRule.CONSTRAINT);
    sd.setBaseDefinition("http://hl7.org/fhir/StructureDefinition/Patient");
    ElementDefinition ed = sd.getSnapshot().addElement();
    ed.setId("Patient");
    ed.setPath("Patient");
    ed.setComment(RELATIVE);
    sd.setUserData(UserDataNames.render_webroot, "http://example.org/dep/");
    context.cacheResource(sd);
    pf.rc = renderingContext(context);
    return sd;
  }

  /** what the core's StructureDefinitionRenderer does for an element of a profile based on the url */
  private static void lookUp(PublisherFields pf, String url) {
    org.hl7.fhir.services.renderers.StructureDefinitionRenderer sdr = new org.hl7.fhir.services.renderers.StructureDefinitionRenderer(pf.rc);
    sdr.setSdMapCache(pf.sdMapCache);
    sdr.getElementById(url, "Patient", null);
  }

  private static String comment(StructureDefinition sd) {
    return sd.getSnapshot().getElementFirstRep().getComment();
  }

  /** a profile in the IG, which Patient/b is found to conform to when it's validated */
  private FetchedFile profile(PublisherFields pf) {
    FetchedResource r = mock(FetchedResource.class);
    when(r.getResource()).thenReturn(new StructureDefinition().setUrl(PROFILE));
    FetchedFile f = file("StructureDefinition-p.json", "p", r);
    pf.fileList.add(f);
    return f;
  }

  /** validates Patient/b as the processor does: its name element gets a signpost, which is also reported */
  private ValidationMessage validateB(PublisherFields pf) throws Exception {
    return validateB(pf, null);
  }

  /** ... and is found to conform to the profile, if one is given */
  private ValidationMessage validateB(PublisherFields pf, String profile) throws Exception {
    validator(pf);
    FetchedFile f = file(pf, "Patient-b.json");
    FetchedResource b = withRealElement(f);
    assertNull(pf.rapido.reuseValidation(f, b));
    ValidationMessage sp = new ValidationMessage(Source.InstanceValidator, IssueType.INFORMATIONAL, "Patient.name[0]",
        "Validate resource against profile http://x/p", IssueSeverity.INFORMATION);
    sp.setSignpost(true);
    b.getElement().getChildList().get(0).addMessage(sp);
    if (profile != null) {
      b.getFoundProfiles().add(profile);
    }
    pf.rapido.recordValidation(f, b, new ArrayList<>(List.of(sp)));
    return sp;
  }

  /** a validator whose record of what it has mentioned (statusWarnings) Rapido can read, as in a build */
  private static void validator(PublisherFields pf) throws Exception {
    pf.validator = mock(InstanceValidator.class);
    Field sw = BaseValidator.class.getDeclaredField("statusWarnings");
    sw.setAccessible(true);
    sw.set(pf.validator, new HashSet<String>());
  }

  /** Patient/b in the file, with a real element tree (Patient with a name), as loading it again gives */
  private static FetchedResource withRealElement(FetchedFile f) {
    Element root = new Element("Patient");
    root.setPath("Patient");
    Element name = new Element("name");
    name.setPath("Patient.name[0]");
    root.getChildList().add(name);
    FetchedResource r = f.getResources().get(0);
    when(r.getElement()).thenReturn(root);
    when(r.getFoundProfiles()).thenReturn(new ArrayList<>());
    return r;
  }

  /**
   * A template script writes diagrams into _includes (onGenerate.plantUml), after the template has listed the svgs in
   * there as pages (onGenerate.artifactList): from the second build on, the diagrams are pages too, and recorded as
   * what those pages wrote. A build after the day changed clears the temp folder, so the template lists none and the
   * diagrams are the script's again: they're not gone, or Jekyll can't include them
   */
  @Test
  void aCompleteBuildDoesntRemoveWhatTheTemplateScriptsWrote() throws Exception {
    PublisherFields pf = build("a");
    writeDiagram();
    finish(pf);

    pf = build("a2");
    assertFalse(pf.rapido.isCompleteBuild(), pf.rapido.getReason());
    FetchedFile svg = file("diagram.svg", "<svg/>");
    svg.setProcessMode(FetchedFile.PROCESS_NONE);
    pf.fileList.add(svg); // as templateBeforeGenerate loads it
    pf.changeList.add(svg);
    writeDiagram();
    svg.getOutputNames().add(includes("diagram.svg").toString());
    svg.getOutputNames().add(includes("en", "diagram.svg").toString());
    Files.createDirectories(includes("en"));
    Files.writeString(includes("en", "diagram.svg"), "<svg/>");
    finish(pf);

    pf = build("a2", 1);
    assertTrue(pf.rapido.isCompleteBuild());
    assertEquals("changed: date", pf.rapido.getReason());
    writeDiagram();
    RapidoOutputs.Change change = finish(pf);

    assertEquals(Set.of(), change.getGone());
    // and what the complete build wrote is recorded: the next build is differential again
    assertFalse(build("a2", 1).rapido.isCompleteBuild());
  }

  /** what onGenerate.plantUml does */
  private void writeDiagram() throws Exception {
    Files.createDirectories(includes());
    Files.writeString(includes("diagram.svg"), "<svg/>");
  }

  private Path includes(String... names) {
    Path p = dir.resolve("temp").resolve("_includes");
    for (String n : names) {
      p = p.resolve(n);
    }
    return p;
  }

  private static FetchedFile file(PublisherFields pf, String name) {
    return pf.fileList.stream().filter(f -> f.getLoadPath().equals(name)).findFirst().orElseThrow();
  }

  /** the files whose html the last finish reused */
  private final List<String> replayed = new ArrayList<>();
  /** what rendering a file does, besides writing its page */
  private Consumer<FetchedFile> rendering;

  /**
   * What the rest of a build does for -rapido: each file's html and spreadsheet phases are reused if they can be, and
   * recorded if not (each patient writes a page in the temp folder), what the files wrote in the temp folder is
   * recorded, and the state is saved
   */
  private RapidoOutputs.Change finish(PublisherFields pf) throws Exception {
    return finish(pf, null);
  }

  /** as finish(pf), with the html of the file named failing as generateResourceHtmlInner reports an exception */
  private RapidoOutputs.Change finish(PublisherFields pf, String failing) throws Exception {
    pf.rapido.finishValidation();
    replayed.clear();
    for (FetchedFile f : pf.fileList) {
      if (pf.rapido.canReplay(f, RapidoTracker.PHASE_HTML) && pf.rapido.canReplay(f, RapidoTracker.PHASE_SHEETS)) {
        replayed.add(f.getLoadPath());
        if (context != null) {
          generator(pf).replayGeneration(f, RapidoTracker.PHASE_HTML);
        } else { // as replayGeneration does
          f.getOutputNames().addAll(pf.rapido.replay(f, RapidoTracker.PHASE_HTML).outputs);
        }
        f.getOutputNames().addAll(pf.rapido.replay(f, RapidoTracker.PHASE_SHEETS).outputs);
        continue;
      }
      pf.rapido.startRecording(f, RapidoTracker.PHASE_HTML);
      if (rendering != null) {
        rendering.accept(f);
      }
      if (f.getLoadPath().startsWith("Patient-")) {
        Path page = dir.resolve("temp").resolve(f.getLoadPath().replace(".json", ".html"));
        Files.createDirectories(page.getParent());
        Files.writeString(page, "<p>" + f.getLoadPath() + "</p>");
        f.getOutputNames().add(page.toString());
      }
      if (f.getLoadPath().equals(failing)) {
        f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.EXCEPTION, "Patient", "Error Rendering Resource: disk full", IssueSeverity.ERROR));
      }
      pf.rapido.stopRecording();
      pf.rapido.startRecording(f, RapidoTracker.PHASE_SHEETS);
      pf.rapido.stopRecording();
    }
    RapidoOutputs.Change change = new RapidoOutputs(pf.vsCache, pf.tempDir).update(pf.fileList, pf.changeList);
    pf.rapido.save();
    IniFile buildTracker = new IniFile(dir.resolve(".build-tracker.ini").toString());
    buildTracker.setBooleanProperty("status", "complete", true, null);
    buildTracker.save();
    return change;
  }

  /** checkDependencies on new files, as a watch rebuild loads them, with the given content for Patient/a */
  private PublisherFields build(String patientContent) throws Exception {
    return build(patientContent, 0);
  }

  /** as build(patientContent), on a day that many days later */
  private PublisherFields build(String patientContent, int daysLater) throws Exception {
    FetchedFile ig = file("ig.json", "{}");
    FetchedFile patient = file("Patient-a.json", patientContent, resource("Patient", "a", primitive(patientContent)));
    FetchedFile other = file("Patient-b.json", "b", resource("Patient", "b"));
    FetchedFile observation = file("Observation-o.json", "o", resource("Observation", "o", primitive("Patient/a")));
    FetchedFile page = file("page.md", "# page");
    page.setProcessMode(FetchedFile.PROCESS_NONE);

    Publisher publisher = new Publisher();
    publisher.getSettings().setRapidoMode(true);
    publisher.getSettings().setWatchMode(true);
    PublisherFields pf = new PublisherFields();
    pf.logger = mock(ILoggingService.class);
    pf.rootDir = dir.toString();
    pf.vsCache = dir.toString();
    pf.tempDir = dir.resolve("temp").toString();
    pf.igpkp = mock(IGKnowledgeProvider.class);
    when(pf.igpkp.getCanonical()).thenReturn("http://example.org/ig");
    pf.template = mock(Template.class);
    if (context != null) {
      pf.context = context;
      pf.pcm = new FilesystemPackageCacheManager.Builder().build(); // where the packages it loaded are
    }
    pf.getExecTime().add(Calendar.DATE, daysLater);
    pf.fileList.addAll(List.of(ig, patient, other, observation, page));
    pf.changeList.addAll(pf.fileList); // as the loader leaves it
    publisher.setPf(pf);
    // as build() does, for a complete build to clear the temp folder
    Field loader = Publisher.class.getDeclaredField("loader");
    loader.setAccessible(true);
    ((PublisherIGLoader) loader.get(publisher)).setPf(pf);

    publisher.checkDependencies(ig, new IniFile(dir.resolve(".build-tracker.ini").toString()));
    return pf;
  }

  private FetchedFile file(String name, String content, FetchedResource... resources) {
    FetchedFile f = new FetchedFile(name);
    f.setPath(dir.resolve("input").resolve(name).toString());
    f.setSource(content.getBytes(StandardCharsets.UTF_8));
    f.getResources().addAll(List.of(resources));
    return f;
  }

  private static FetchedResource resource(String type, String id, Element... children) {
    Element element = mock(Element.class);
    NamedItemList<Element> list = new NamedItemList<>();
    list.addAll(List.of(children));
    when(element.getChildList()).thenReturn(list);
    when(element.getName()).thenReturn(type);
    when(element.fhirType()).thenReturn(type);
    when(element.getComments()).thenReturn(new ArrayList<>());
    FetchedResource resource = mock(FetchedResource.class);
    when(resource.fhirType()).thenReturn(type);
    when(resource.getId()).thenReturn(id);
    when(resource.getElement()).thenReturn(element);
    return resource;
  }

  private static Element primitive(String value) {
    Element element = mock(Element.class);
    when(element.isPrimitive()).thenReturn(true);
    when(element.getName()).thenReturn("value");
    when(element.fhirType()).thenReturn("string");
    when(element.hasValue()).thenReturn(true);
    when(element.getValue()).thenReturn(value);
    when(element.primitiveValue()).thenReturn(value);
    when(element.getComments()).thenReturn(new ArrayList<>());
    return element;
  }
}
