package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.igtools.templates.Template;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.NamedItemList;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
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

  /**
   * SUSHI copies an instance's Title and Description into its IG entry. The loader copies the entry's name and
   * description to the resource, whose own hash has them, so an edit to them alone renders that resource and what
   * depends on it again (and, as always, the IG, the pages and the summaries that list the entries), not everything
   */
  @Test
  void anIgEntrysNameAndDescriptionAloneAreNotACompleteBuild() throws Exception {
    finish(build("a", new Ig()));

    Ig ig = new Ig();
    ig.aName = "Patient A, renamed";
    ig.aDescription = "The first patient, edited";
    PublisherFields pf = build("a", ig);

    // the profile that Patient/a states lists it among its examples
    List<String> dirty = List.of("Observation-o.json (something it depends on changed)",
        "Patient-a.json (the content loaded from it changed: Patient/a)", "StructureDefinition-p.json (something that refers to it changed; render only)",
        "ig.json (always processed)", "page.md (always processed)");
    assertFalse(pf.rapido.isCompleteBuild(), pf.rapido.getReason());
    assertEquals(dirty, pf.rapido.listRenderDirty());
    finish(pf);
    // the pages that show the entry's text have the new text
    assertTrue(page("artifacts.html").contains("<li>Patient A, renamed: The first patient, edited</li>"), page("artifacts.html"));
    assertEquals("<li>Patient A, renamed</li>", page("StructureDefinition-p.html"));

    // the description alone
    ig.aDescription = "The first patient, edited again";
    pf = build("a", ig);
    assertFalse(pf.rapido.isCompleteBuild(), pf.rapido.getReason());
    assertEquals(dirty, pf.rapido.listRenderDirty());
    finish(pf);
    assertTrue(page("artifacts.html").contains("<li>Patient A, renamed: The first patient, edited again</li>"), page("artifacts.html"));
  }

  /**
   * A profile that validation found an example to conform to lists it too, though nothing in the example refers to it:
   * what the last build found counts as a reference
   */
  @Test
  void aProfileAnExampleWasFoundToConformToIsRenderedAgain() throws Exception {
    finish(build("a", new Ig()));
    foundLastTime("0", "Patient/b");

    Ig ig = new Ig();
    ig.bName = "Patient B, renamed";
    PublisherFields pf = build("a", ig);
    assertFalse(pf.rapido.isCompleteBuild(), pf.rapido.getReason());
    assertEquals(List.of("Patient-b.json (the content loaded from it changed: Patient/b)",
        "StructureDefinition-p.json (something that refers to it changed; render only)", "ig.json (always processed)",
        "page.md (always processed)"), pf.rapido.listRenderDirty());
  }

  /** what the last build found for the second resource in a file is its own, and a record of another resource isn't */
  @Test
  void whatWasFoundIsTheResourcesOwn() throws Exception {
    Ig ig = new Ig();
    ig.twoInB = true;
    finish(build("a", ig));
    foundLastTime("1", "Patient/b2");
    ig.b2Name = "Patient B2, renamed";
    PublisherFields pf = build("a", ig);
    assertTrue(pf.rapido.listRenderDirty().contains("StructureDefinition-p.json (something that refers to it changed; render only)"),
        pf.rapido.listRenderDirty().toString());
  }

  /** after the file's resources are reordered, the record of the resource is still its own */
  @Test
  void whatWasFoundIsTheResourcesOwnAfterAReorder() throws Exception {
    Ig ig = new Ig();
    ig.twoInB = true;
    finish(build("a", ig));
    foundLastTime("0", "Patient/b2");
    ig.b2Name = "Patient B2, renamed";
    PublisherFields pf = build("a", ig);
    assertTrue(pf.rapido.listRenderDirty().contains("StructureDefinition-p.json (something that refers to it changed; render only)"),
        pf.rapido.listRenderDirty().toString());
  }

  /** a record at the index that's of another resource isn't the resource's */
  @Test
  void whatWasFoundForAnotherResourceIsntTheResourcesOwn() throws Exception {
    Ig ig = new Ig();
    ig.twoInB = true;
    finish(build("a", ig));
    foundLastTime("1", "Patient/other");
    ig.b2Name = "Patient B2, renamed";
    PublisherFields pf = build("a", ig);
    assertEquals(List.of("Patient-b.json (the content loaded from it changed: Patient/b2)", "ig.json (always processed)",
        "page.md (always processed)"), pf.rapido.listRenderDirty());
  }

  /** an example the IG entry says is of a profile (exampleCanonical) is listed by it */
  @Test
  void aProfileTheIgSaysAnExampleIsOfIsRenderedAgain() throws Exception {
    Ig ig = new Ig();
    ig.bExampleOfP = true;
    finish(build("a", ig));
    ig.bName = "Patient B, renamed";
    PublisherFields pf = build("a", ig);
    assertEquals(List.of("Patient-b.json (the content loaded from it changed: Patient/b)",
        "StructureDefinition-p.json (something that refers to it changed; render only)", "ig.json (always processed)",
        "page.md (always processed)"), pf.rapido.listRenderDirty());
  }

  /**
   * A profile an example is newly found to conform to has no edge to it from the last build, but lists it now: it's
   * rendered again once validation finds it. One it was found to conform to last time isn't, unless it no longer is
   */
  @Test
  void aProfileAnExampleIsNewlyFoundToConformToIsRenderedAgain() throws Exception {
    List<String> pDirty = List.of("StructureDefinition-p.json (the examples found to conform to it changed; render only)",
        "ig.json (always processed)", "page.md (always processed)");
    List<String> none = List.of("ig.json (always processed)", "page.md (always processed)");
    finish(build("a", new Ig()));
    PublisherFields pf = build("a", new Ig());
    assertEquals(none, pf.rapido.listRenderDirty());
    validateB(pf, true);
    assertEquals(pDirty, pf.rapido.listRenderDirty());
    finish(pf);
    // what it was found to conform to is kept, though without a validator the rest can't be
    JsonObject rv = RapidoTracker.readState(state()).getJsonObject("files").getJsonObject("Patient-b.json").getJsonObject("validation").getJsonObject("0");
    assertEquals("Patient/b", rv.asString("key"));
    assertEquals(List.of(P_URL), rv.getJsonArray("profileUsages").asStrings());
    assertFalse(rv.has("statusBefore"));

    // the build after the one that has the edge
    finish(build("a", new Ig()));
    foundLastTime("0", "Patient/b");
    pf = build("a", new Ig());
    assertEquals(none, pf.rapido.listRenderDirty());
    validateB(pf, true);
    assertEquals(none, pf.rapido.listRenderDirty());
    finish(pf);

    // no longer found to conform to it
    pf = build("a", new Ig());
    validateB(pf, false);
    assertEquals(pDirty, pf.rapido.listRenderDirty());
  }

  /**
   * With a validator: a record whose messages can be saved is reused by the next build, one with a message that can't
   * be isn't, though what it was found to conform to is kept
   */
  @Test
  void aRecordWithAMessageThatCantBeSavedIsntReused() throws Exception {
    finish(build("a", new Ig()));
    ValidationMessage saved = new ValidationMessage(Source.InstanceValidator, IssueType.INVALID, "Patient", "saved", IssueSeverity.ERROR);
    PublisherFields pf = withValidator(build("a", new Ig()));
    assertEquals(null, validateB(pf, List.of(saved)));
    finish(pf);
    assertTrue(recordOfB().has("statusBefore"));

    pf = withValidator(build("a", new Ig()));
    List<ValidationMessage> reused = validateB(pf, List.of());
    assertEquals(1, reused.size());
    assertEquals("saved", reused.get(0).getMessage());
    finish(pf);

    ValidationMessage unsaved = new ValidationMessage(Source.InstanceValidator, IssueType.INVALID, "Patient", "unsaved", IssueSeverity.ERROR);
    List<ValidationMessage> slices = new ArrayList<>();
    slices.add(null);
    unsaved.setSliceInfo(slices);
    Ig renamed = new Ig();
    renamed.bName = "Patient B, renamed";
    pf = withValidator(build("a", renamed));
    assertEquals(null, validateB(pf, List.of(unsaved)));
    finish(pf);
    assertFalse(recordOfB().has("statusBefore"));
    assertEquals("Patient/b", recordOfB().asString("key"));

    pf = withValidator(build("a", renamed));
    assertEquals(null, validateB(pf, List.of()));
  }

  /** a validator whose record of the definitions it has mentioned can be read */
  private static PublisherFields withValidator(PublisherFields pf) throws Exception {
    pf.validator = mock(InstanceValidator.class);
    Field fld = BaseValidator.class.getDeclaredField("statusWarnings");
    fld.setAccessible(true);
    fld.set(pf.validator, new HashSet<String>());
    return pf;
  }

  /**
   * as the publisher validates Patient/b: the messages from the last build if they're reused, else null, when
   * validating it gives errs
   */
  private static List<ValidationMessage> validateB(PublisherFields pf, List<ValidationMessage> errs) {
    FetchedFile f = pf.fileList.stream().filter(x -> x.getLoadPath().equals("Patient-b.json")).findFirst().get();
    FetchedResource b = f.getResources().get(0);
    when(b.getFoundProfiles()).thenReturn(new ArrayList<>());
    List<ValidationMessage> reused = pf.rapido.reuseValidation(f, b);
    if (reused == null) {
      pf.rapido.recordValidation(f, b, new ArrayList<>(errs));
    }
    return reused;
  }

  private JsonObject recordOfB() throws Exception {
    return RapidoTracker.readState(state()).getJsonObject("files").getJsonObject("Patient-b.json").getJsonObject("validation").getJsonObject("0");
  }

  /** as validating Patient/b does, finding it conforms to profile p or not */
  private static void validateB(PublisherFields pf, boolean conforms) {
    FetchedFile f = pf.fileList.stream().filter(x -> x.getLoadPath().equals("Patient-b.json")).findFirst().get();
    FetchedResource b = f.getResources().get(0);
    List<String> found = new ArrayList<>();
    when(b.getFoundProfiles()).thenReturn(found);
    pf.rapido.reuseValidation(f, b);
    if (conforms) {
      found.add(P_URL);
    }
    pf.rapido.recordValidation(f, b, new ArrayList<>());
  }

  private File state() {
    return dir.resolve("temp").resolve("_rapido").resolve("state.json").toFile();
  }

  /** the last build's state, as recordValidation records that the resource (key) at index of Patient-b.json conforms to p */
  private void foundLastTime(String index, String key) throws Exception {
    File state = state();
    JsonObject json = RapidoTracker.readState(state);
    JsonObject v = json.getJsonObject("files").getJsonObject("Patient-b.json").forceObject("validation");
    v.remove(index);
    JsonObject rv = v.forceObject(index);
    rv.add("key", key);
    rv.forceArray("profileUsages").add(P_URL);
    RapidoTracker.writeState(json, state);
  }

  /** Without an ig.ini, the loader doesn't copy the entries' names and descriptions to the resources: they count */
  @Test
  void withoutAnIgIniAnIgEntrysNameIsACompleteBuild() throws Exception {
    Ig ig = new Ig();
    ig.igMode = false;
    finish(build("a", ig));
    ig.aName = "Patient A, renamed";
    PublisherFields pf = build("a", ig);
    assertTrue(pf.rapido.isCompleteBuild());
    assertEquals("changed: ig", pf.rapido.getReason());
  }

  /** Anything else in the IG still makes the build a complete one */
  @Test
  void anyOtherIgChangeIsACompleteBuild() throws Exception {
    finish(build("a", new Ig()));
    Ig ig = new Ig();
    ig.status = "active";
    PublisherFields pf = build("a", ig);
    assertTrue(pf.rapido.isCompleteBuild());
    assertEquals("changed: ig", pf.rapido.getReason());

    // the entry of a resource that isn't loaded: nothing else has its name
    finish(build("a", new Ig()));
    ig = new Ig();
    ig.missingName = "Not loaded, renamed";
    pf = build("a", ig);
    assertTrue(pf.rapido.isCompleteBuild());
    assertEquals("changed: ig", pf.rapido.getReason());

    // a name with extensions (a translation, say) counts as it is
    finish(build("a", new Ig()));
    ig = new Ig();
    ig.aNameExtension = true;
    pf = build("a", ig);
    assertTrue(pf.rapido.isCompleteBuild());
    assertEquals("changed: ig", pf.rapido.getReason());
  }

  /**
   * A template script gets the IG as [target]-ig-working.json, written by the build, and gives it back as
   * [target]-ig-updated.json: the entries' names and descriptions are left out of that one too, the rest counts
   */
  @Test
  void theIgATemplateScriptGivesBackCountsAsTheIgDoes() throws Exception {
    Ig ig = new Ig();
    ig.inTemplate = true;
    finish(build("a", ig));

    ig.aName = "Patient A, renamed";
    PublisherFields pf = build("a", ig);
    assertFalse(pf.rapido.isCompleteBuild(), pf.rapido.getReason());
    finish(pf);

    Ig changed = new Ig();
    changed.inTemplate = true;
    changed.aName = ig.aName;
    changed.status = "active";
    pf = build("a", ig, changed);
    assertTrue(pf.rapido.isCompleteBuild());
    assertEquals("changed: template", pf.rapido.getReason());
    finish(pf);

    // a file of the template's own whose name only looks like one counts as it is
    Files.writeString(dir.resolve("template").resolve("notes-ig-working.txt"), "one");
    finish(build("a", ig, changed));
    Files.writeString(dir.resolve("template").resolve("notes-ig-working.txt"), "two");
    pf = build("a", ig, changed);
    assertTrue(pf.rapido.isCompleteBuild());
    assertEquals("changed: template", pf.rapido.getReason());
  }

  /** what a template script gives back counts through a link too */
  @Test
  void theIgATemplateScriptGivesBackThroughALinkCounts() throws Exception {
    Ig ig = new Ig();
    Path template = Files.createDirectories(dir.resolve("template"));
    Files.writeString(template.resolve("given.json"), ig.json());
    Files.createSymbolicLink(template.resolve("onLoad-ig-updated.json"), template.resolve("given.json"));
    finish(build("a", ig));

    Ig changed = new Ig();
    changed.status = "active";
    Files.writeString(template.resolve("given.json"), changed.json());
    PublisherFields pf = build("a", ig);
    assertTrue(pf.rapido.isCompleteBuild());
    assertEquals("changed: template", pf.rapido.getReason());
  }

  private static final String P_URL = "http://example.org/ig/StructureDefinition/p";

  /**
   * The IG of a build: Patient/a's entry has the name and description given, Patient/a states profile p, and an entry
   * for a resource not loaded
   */
  private static class Ig {
    boolean igMode = true;
    String bName = "Patient B";
    /** Patient/b's IG entry says it's an example of p */
    boolean bExampleOfP = false;
    /** Patient-b.json holds Patient/b2 too */
    boolean twoInB = false;
    String b2Name = "Patient B2";
    String aName = "Patient A";
    String aDescription = "The first patient";
    boolean aNameExtension = false;
    String missingName = "Not loaded";
    String status = "draft";
    /** also as a template script's onLoad-ig-working.json and onLoad-ig-updated.json */
    boolean inTemplate = false;

    String json() {
      return "{\"resourceType\":\"ImplementationGuide\",\"id\":\"ig\",\"status\":\""+status+"\",\"definition\":{\"resource\":["
          + "{\"reference\":{\"reference\":\"Patient/a\"},\"name\":\""+aName+"\","
          + (aNameExtension ? "\"_name\":{\"extension\":[{\"url\":\"http://example.org/x\",\"valueString\":\"x\"}]}," : "")
          + "\"description\":\""+aDescription+"\"},"
          + "{\"reference\":{\"reference\":\"Patient/b\"},\"name\":\""+bName+"\"},"
          + "{\"reference\":{\"reference\":\"StructureDefinition/p\"},\"name\":\"Profile P\"},"
          + "{\"reference\":{\"reference\":\"Observation/o\"},\"name\":\"Observation\"},"
          + "{\"reference\":{\"reference\":\"Patient/missing\"},\"name\":\""+missingName+"\"}]}}";
    }

    /** the IG as the loader reads it */
    FetchedResource igResource() {
      Element name = primitive("name", aName);
      if (aNameExtension) {
        Element ext = node("extension", primitive("url", "http://example.org/x"), primitive("valueString", "x"));
        when(name.hasChildren()).thenReturn(true);
        NamedItemList<Element> l = new NamedItemList<>();
        l.add(ext);
        when(name.getChildList()).thenReturn(l);
      }
      return PublisherRapidoWatchTest.resource("ImplementationGuide", "ig", primitive("status", status), node("definition",
          node("resource", node("reference", primitive("reference", "Patient/a")), name, primitive("description", aDescription)),
          node("resource", node("reference", primitive("reference", "Patient/b")), primitive("name", bName)),
          node("resource", node("reference", primitive("reference", "StructureDefinition/p")), primitive("name", "Profile P")),
          node("resource", node("reference", primitive("reference", "Observation/o")), primitive("name", "Observation")),
          node("resource", node("reference", primitive("reference", "Patient/missing")), primitive("name", missingName))));
    }
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

  /**
   * What the rest of a build does for -rapido: each file's html and spreadsheet phases are reused if they can be, and
   * recorded if not (each patient, the IG and profile p write pages in the temp folder), what the files wrote in the
   * temp folder is recorded, and the state is saved
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
        // as replayGeneration does
        replayed.add(f.getLoadPath());
        f.getOutputNames().addAll(pf.rapido.replay(f, RapidoTracker.PHASE_HTML).outputs);
        f.getOutputNames().addAll(pf.rapido.replay(f, RapidoTracker.PHASE_SHEETS).outputs);
        continue;
      }
      pf.rapido.startRecording(f, RapidoTracker.PHASE_HTML);
      if (f.getLoadPath().startsWith("Patient-")) {
        page(f, f.getLoadPath().replace(".json", ".html"), "<p>" + f.getLoadPath() + "</p>");
      }
      if (f.getLoadPath().equals("ig.json")) {
        // the artifacts page: the template lists the IG's entries by name and description, from the IG
        StringBuilder b = new StringBuilder();
        JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(f.getSource());
        if (json.hasObject("definition")) {
          for (JsonObject rg : json.getJsonObject("definition").forceArray("resource").asJsonObjects()) {
            b.append("<li>").append(rg.asString("name")).append(": ").append(rg.asString("description")).append("</li>");
          }
        }
        page(f, "artifacts.html", b.toString());
      }
      if (f.getLoadPath().equals("StructureDefinition-p.json")) {
        // the profile's list of examples: the files that state it, by the name the IG gives them
        StringBuilder b = new StringBuilder();
        for (FetchedFile e : pf.fileList) {
          for (FetchedResource r : e.getResources()) {
            if (r.getProfiles(false).contains(P_URL)) {
              b.append("<li>").append(e.getTitle()).append("</li>");
            }
          }
        }
        page(f, "StructureDefinition-p.html", b.toString());
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

  /** f writes the page in the temp folder */
  private void page(FetchedFile f, String name, String content) throws Exception {
    Path page = dir.resolve("temp").resolve(name);
    Files.createDirectories(page.getParent());
    Files.writeString(page, content);
    f.getOutputNames().add(page.toString());
  }

  private String page(String name) throws Exception {
    return Files.readString(dir.resolve("temp").resolve(name));
  }

  /** checkDependencies on new files, as a watch rebuild loads them, with the given content for Patient/a */
  private PublisherFields build(String patientContent) throws Exception {
    return build(patientContent, null, null, 0);
  }

  /** as build(patientContent), on a day that many days later */
  private PublisherFields build(String patientContent, int daysLater) throws Exception {
    return build(patientContent, null, null, daysLater);
  }

  private PublisherFields build(String patientContent, Ig igContent) throws Exception {
    return build(patientContent, igContent, igContent);
  }

  private PublisherFields build(String patientContent, Ig igContent, Ig updated) throws Exception {
    return build(patientContent, igContent, updated, 0);
  }

  /**
   * as build(patientContent), with an IG (as ig.ini gives it) that lists the files; updated is what a template script
   * gives back. daysLater moves the build that many days on
   */
  private PublisherFields build(String patientContent, Ig igContent, Ig updated, int daysLater) throws Exception {
    FetchedFile ig = igContent == null ? file("ig.json", "{}") : file("ig.json", igContent.json(), igContent.igResource());
    FetchedResource a = resource("Patient", "a", primitive(patientContent));
    if (igContent != null) {
      // as the loader copies them from the IG
      when(a.getResourceName()).thenReturn(igContent.aName);
      when(a.getResourceDescription()).thenReturn(igContent.aDescription);
      when(a.getProfiles(false)).thenReturn(List.of(P_URL));
      if (igContent.inTemplate) {
        Path template = Files.createDirectories(dir.resolve("template"));
        Files.writeString(template.resolve("onLoad-ig-working.json"), igContent.json());
        Files.writeString(template.resolve("onLoad-ig-updated.json"), updated.json());
      }
    }
    FetchedFile patient = file("Patient-a.json", patientContent, a);
    if (igContent != null) {
      // the loader gives the file its entry's name
      patient.setTitle(igContent.aName);
    }
    FetchedResource b = resource("Patient", "b");
    FetchedResource b2 = resource("Patient", "b2");
    if (igContent != null) {
      when(b.getResourceName()).thenReturn(igContent.bName);
      when(b2.getResourceName()).thenReturn(igContent.b2Name);
      if (igContent.bExampleOfP) {
        when(b.getExampleUri()).thenReturn(P_URL);
      }
    }
    FetchedFile other = igContent != null && igContent.twoInB ? file("Patient-b.json", "b", b, b2) : file("Patient-b.json", "b", b);
    if (igContent != null && !igContent.twoInB) {
      other.setTitle(igContent.bName); // its entry's name: a file the IG lists holds one resource
    }
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
    pf.igMode = igContent != null && igContent.igMode;
    pf.getExecTime().add(Calendar.DATE, daysLater);
    pf.fileList.addAll(List.of(ig, patient, other, observation, page));
    if (igContent != null) {
      FetchedResource p = resource("StructureDefinition", "p");
      Element url = primitive("url", P_URL);
      when(p.getElement().hasChildren()).thenReturn(true);
      when(p.getElement().getChildrenByName("url")).thenReturn(List.of(url));
      StructureDefinition sd = new StructureDefinition();
      sd.setUrl(P_URL);
      when(p.getResource()).thenReturn(sd);
      pf.fileList.add(pf.fileList.size() - 1, file("StructureDefinition-p.json", "p", p));
    }
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
    return primitive("value", value);
  }

  private static Element primitive(String name, String value) {
    Element element = mock(Element.class);
    when(element.isPrimitive()).thenReturn(true);
    when(element.getName()).thenReturn(name);
    when(element.fhirType()).thenReturn("string");
    when(element.hasValue()).thenReturn(true);
    when(element.getValue()).thenReturn(value);
    when(element.primitiveValue()).thenReturn(value);
    when(element.getComments()).thenReturn(new ArrayList<>());
    return element;
  }

  private static Element node(String name, Element... children) {
    Element element = mock(Element.class);
    NamedItemList<Element> list = new NamedItemList<>();
    list.addAll(List.of(children));
    when(element.getName()).thenReturn(name);
    when(element.fhirType()).thenReturn("BackboneElement");
    when(element.hasChildren()).thenReturn(true);
    when(element.getChildList()).thenReturn(list);
    when(element.getComments()).thenReturn(new ArrayList<>());
    return element;
  }
}
