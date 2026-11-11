package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.igtools.templates.Template;
import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.ValueSet;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.NamedItemList;
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

  // ---- what keeping the state costs: the state kept in memory, the expansions in files, the stamps of the outputs ----

  private Path state() {
    return dir.resolve("temp").resolve("_rapido").resolve("state.json");
  }

  private Path expansionDir() {
    return dir.resolve("temp").resolve("_rapido").resolve("expansions");
  }

  private static ValueSet expansion(String url) {
    ValueSet vs = new ValueSet();
    vs.setUrl(url);
    vs.getExpansion().addContains().setCode("c");
    return vs;
  }

  /**
   * In watch mode the next build starts from the state this process saved, without reading it: what it parsed for the
   * last build (an expansion) is what the next build replays
   */
  @Test
  void theNextBuildOfTheProcessStartsFromTheStateItSaved() throws Exception {
    expansions.put("Patient-b.json", expansion("http://example.org/vs"));
    finish(build("a"));

    finish(build("a2"));
    assertEquals(List.of("Patient-b.json"), replayed);
    ValueSet parsed = replayedExpansions.get(0);
    assertEquals("http://example.org/vs", parsed.getUrl());

    finish(build("a3"));
    assertEquals(List.of("Patient-b.json"), replayed);
    assertSame(parsed, replayedExpansions.get(0));

    // a new process reads the state, and the expansion: it's all in the files
    RapidoTracker.forgetKept();
    PublisherFields pf = build("a4");
    assertFalse(pf.rapido.isCompleteBuild(), pf.rapido.getReason());
    finish(pf);
    assertEquals(List.of("Patient-b.json"), replayed);
    assertNotSame(parsed, replayedExpansions.get(0));
    assertEquals("c", replayedExpansions.get(0).getExpansion().getContainsFirstRep().getCode());
  }

  /** a state file that isn't the one this process wrote (another publisher ran since) is read, not taken as known */
  @Test
  void aStateSavedBySomethingElseIsRead() throws Exception {
    finish(build("a"));
    PublisherFields known = build("a");
    assertFalse(known.rapido.isCompleteBuild(), known.rapido.getReason());
    finish(known);

    // as a publisher with another format would have left it
    String saved = Files.readString(state());
    assertTrue(saved.startsWith("{\"version\":5,"), saved.substring(0, 20));
    Files.writeString(state(), "{\"version\":4," + saved.substring(13));

    PublisherFields pf = build("a");
    assertTrue(pf.rapido.isCompleteBuild());
    assertEquals("state from a different version", pf.rapido.getReason());
    finish(pf);

    // half a state (a build killed where the file system can't move the file in one step)
    saved = Files.readString(state());
    Files.writeString(state(), saved.substring(0, saved.length() / 2));
    pf = build("a");
    assertTrue(pf.rapido.isCompleteBuild());
    assertTrue(pf.rapido.getReason().startsWith("the state from the last build can't be read"), pf.rapido.getReason());
    finish(pf);

    // and none
    Files.delete(state());
    pf = build("a");
    assertTrue(pf.rapido.isCompleteBuild());
    assertEquals("no state from a previous build", pf.rapido.getReason());
  }

  /** the file is the one that was written only with the id that was written into it, and the stamp it had */
  @Test
  void theStateFileIsKnownByItsIdAndItsStamp() throws Exception {
    File sf = dir.resolve("state.json").toFile();
    RapidoTracker.writeState(new JsonObject().add("version", 5).add("id", "one").add("uuid", "u"), sf);
    String stamp = RapidoTracker.FileStamp.of(sf.getPath()).text;

    assertTrue(RapidoTracker.isStillTheFile(sf, "one", stamp));
    assertFalse(RapidoTracker.isStillTheFile(sf, "two", stamp));
    assertFalse(RapidoTracker.isStillTheFile(sf, "one", stamp + "0"));
    assertFalse(RapidoTracker.isStillTheFile(new File(sf.getPath() + ".none"), "one", stamp));
  }

  /** an expansion is one file, named by its hash, however many files and builds have it; what no file has is removed */
  @Test
  void anExpansionIsStoredOnceAndRemovedWhenUnused() throws Exception {
    expansions.put("Patient-a.json", expansion("http://example.org/vs"));
    expansions.put("Patient-b.json", expansion("http://example.org/vs"));
    Files.createDirectories(expansionDir());
    Files.writeString(expansionDir().resolve("0123.4567.tmp"), "what a killed build left");
    finish(build("a"));

    File[] files = expansionDir().toFile().listFiles();
    assertEquals(1, files.length, Arrays.toString(files));
    assertTrue(Files.readString(files[0].toPath()).contains("http://example.org/vs"));
    assertFalse(Files.readString(state()).contains("http://example.org/vs"));

    // Patient/a changes, and has another expansion now: the one of Patient/b stays, as it is
    FileTime written = Files.getLastModifiedTime(files[0].toPath());
    expansions.put("Patient-a.json", expansion("http://example.org/other"));
    finish(build("a2"));
    assertEquals(2, expansionDir().toFile().listFiles().length);
    assertEquals(written, Files.getLastModifiedTime(files[0].toPath()));

    expansions.remove("Patient-a.json");
    finish(build("a3"));
    assertArrayEquals(files, expansionDir().toFile().listFiles());
  }

  /**
   * An expansion file that doesn't hold what was written (the disk, or something else that wrote to it): the file that
   * made it is rendered again, and the expansion written again
   */
  @Test
  void anExpansionThatCantBeReadIsMadeAgain() throws Exception {
    expansions.put("Patient-b.json", expansion("http://example.org/vs"));
    finish(build("a"));
    Path file = expansionDir().toFile().listFiles()[0].toPath();
    String content = Files.readString(file);
    RapidoTracker.forgetKept();
    for (String other : new String[] {content.substring(0, content.length() / 2), content.replace("example.org/vs", "example.org/xx")}) {
      // half of it, or an expansion, but not the one that was written
      Files.writeString(file, other);

      PublisherFields pf = build("a");
      assertFalse(pf.rapido.isCompleteBuild(), pf.rapido.getReason());
      finish(pf);
      assertFalse(replayed.contains("Patient-b.json"));
      assertTrue(pf.rapido.listRenderDirty().contains("Patient-b.json (an expansion it saved can't be read; render only)"),
          pf.rapido.listRenderDirty().toString());
      assertEquals(content, Files.readString(file));
      RapidoTracker.forgetKept();
    }

    finish(build("a"));
    assertEquals(List.of("Patient-a.json", "Patient-b.json", "Observation-o.json"), replayed);
  }

  /** an expansion file removed while the process has it parsed: the saved state has to stay complete, so it's made again */
  @Test
  void anExpansionWhoseFileIsGoneIsMadeAgain() throws Exception {
    expansions.put("Patient-b.json", expansion("http://example.org/vs"));
    finish(build("a"));
    finish(build("a"));
    assertTrue(replayed.contains("Patient-b.json"));
    Path file = expansionDir().toFile().listFiles()[0].toPath();
    Files.delete(file);

    PublisherFields pf = build("a");
    assertTrue(pf.rapido.listRenderDirty().contains("Patient-b.json (outputs from the last build are missing; render only)"),
        pf.rapido.listRenderDirty().toString());
    finish(pf);
    assertTrue(Files.exists(file));
  }

  /**
   * A build killed while it saves leaves the state of the build before it, whole (and the build tracker says that a build
   * didn't finish): the new state is written next to it, and moved over it when it is complete
   */
  @Test
  void theStateOfTheBuildBeforeIsThereUntilTheNewOneIsWritten() throws Exception {
    File sf = dir.resolve("state.json").toFile();
    RapidoTracker.writeState(new JsonObject().add("version", 5).add("uuid", "before"), sf);
    String before = Files.readString(sf.toPath());
    List<String> seen = new ArrayList<>();
    JsonObject state = new JsonObject().add("version", 5).add("uuid", "after");
    // a value that looks at the file while the state is being written
    state.add("probe", new org.hl7.fhir.utilities.json.model.JsonString("x") {
      @Override
      public String toJson() {
        try {
          seen.add(Files.readString(sf.toPath()));
        } catch (Exception e) {
          seen.add(e.toString());
        }
        return super.toJson();
      }
    });

    RapidoTracker.writeState(state, sf);

    assertEquals(List.of(before), seen);
    assertEquals("after", RapidoTracker.readState(sf).asString("uuid"));
    assertArrayEquals(new String[] {"state.json"}, dir.toFile().list((d, n) -> n.startsWith("state")));

    // a state that couldn't be written leaves no state at all (not the one of the build before, nor half of one): the
    // next build is a complete one
    JsonObject failing = new JsonObject().add("version", 5);
    failing.add("probe", new org.hl7.fhir.utilities.json.model.JsonString("x") {
      @Override
      public String toJson() {
        throw new IllegalStateException("disk full");
      }
    });
    assertThrows(IllegalStateException.class, () -> RapidoTracker.writeState(failing, sf));
    assertArrayEquals(new String[0], dir.toFile().list((d, n) -> n.startsWith("state")));
  }

  /** a build that went no further than looking at what it can reuse: as if it hadn't started */
  private void stopped() throws Exception {
    IniFile buildTracker = new IniFile(dir.resolve(".build-tracker.ini").toString());
    buildTracker.setBooleanProperty("status", "complete", true, null);
    buildTracker.save();
  }

  /** the file system's clock, read once it has moved past the file's times */
  private RapidoTracker.FileStamp clockAfter(Path file) throws Exception {
    RapidoTracker.FileStamp stamp = RapidoTracker.FileStamp.of(file.toString());
    for (int i = 0; i < 1000; i++) {
      RapidoTracker.FileStamp clock = RapidoTracker.clock(dir.resolve("clock").toFile());
      if (stamp.settledAt(clock)) {
        return clock;
      }
      Thread.sleep(5);
    }
    throw new AssertionError("the file system's clock doesn't move");
  }

  /** a file with the stamp it had when it was hashed isn't read again, if the stamp was settled then */
  @Test
  void aFileWithASettledStampIsntReadAgain() throws Exception {
    Path file = dir.resolve("out.html");
    Files.writeString(file, "<p>one</p>");
    JsonObject written = new JsonObject();
    JsonObject stamps = new JsonObject();
    RapidoTracker.FileStamp clock = clockAfter(file);

    written.set(file.toString(), RapidoTracker.hashAndStamp(file.toString(), stamps, clock));
    assertEquals(RapidoTracker.FileStamp.of(file.toString()).text, stamps.asString(file.toString()));

    // not read: it is what the stamp says, whatever hash is recorded
    written.set(file.toString(), "not its hash");
    assertTrue(RapidoTracker.holds(file.toString(), written, stamps, clock));
    // without the stamp it is read
    stamps.remove(file.toString());
    assertFalse(RapidoTracker.holds(file.toString(), written, stamps, clock));
    assertFalse(stamps.has(file.toString()));
  }

  /**
   * A file written in the same tick of the file system's clock as the clock was read (or after) could be written again
   * in that tick, with the same size, and have the same stamp: its stamp isn't kept, and it is hashed next time
   */
  @Test
  void aStampThatIsntSettledIsntKept() throws Exception {
    Path file = dir.resolve("out.html");
    JsonObject written = new JsonObject();
    JsonObject stamps = new JsonObject();
    RapidoTracker.FileStamp clock = RapidoTracker.clock(dir.resolve("clock").toFile());
    Files.writeString(file, "<p>one</p>");
    // later than the clock was read, however coarse the clock
    Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 10000));

    written.set(file.toString(), RapidoTracker.hashAndStamp(file.toString(), stamps, clock));
    assertFalse(stamps.has(file.toString()));
    assertTrue(RapidoTracker.holds(file.toString(), written, stamps, clock));
    assertFalse(stamps.has(file.toString()));

    // without a reading of the clock, nothing is settled
    assertFalse(RapidoTracker.FileStamp.of(file.toString()).settledAt(null));
    Files.writeString(file, "<p>two</p>");
    assertFalse(RapidoTracker.holds(file.toString(), written, stamps, clock));

    // once the clock is past the file's times, the stamp is kept: by the build that finds the file as it was
    Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - 10000));
    written.set(file.toString(), RapidoTracker.fileHash(file.toString()));
    assertTrue(RapidoTracker.holds(file.toString(), written, stamps, clockAfter(file)));
    assertTrue(stamps.has(file.toString()));
  }

  /**
   * An output written over with content of the same size, and given the time it had: still found, by the time the file
   * system says it was changed (where it says: see FileStamp), or by which file it is when it was replaced
   */
  @Test
  void anOutputWrittenOverWithTheSameSizeAndTimeIsntReused() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("unix"));
    Path page = dir.resolve("temp").resolve("Patient-b.html");
    finish(build("a"));
    clockAfter(page);
    // the build that finds the outputs as they were keeps their stamps
    finish(build("a"));
    assertTrue(Files.readString(state()).contains("\"stamps\":{\"" + page.toString().replace("\\", "\\\\") + "\""), Files.readString(state()));
    PublisherFields pf = build("a");
    assertTrue(pf.rapido.canReplay(file(pf, "Patient-b.json"), RapidoTracker.PHASE_HTML));
    stopped();

    FileTime time = Files.getLastModifiedTime(page);
    String content = Files.readString(page);
    Files.writeString(page, content.replace("Patient-b", "Patient-X"));
    Files.setLastModifiedTime(page, time);

    pf = build("a");
    assertFalse(pf.rapido.isCompleteBuild(), pf.rapido.getReason());
    assertFalse(pf.rapido.canReplay(file(pf, "Patient-b.json"), RapidoTracker.PHASE_HTML));
    assertTrue(pf.rapido.listRenderDirty().contains("Patient-b.json (an output was overwritten: Patient-b.html; render only)"),
        pf.rapido.listRenderDirty().toString());
    stopped();

    // put back, it is what was written: reused, after reading it
    Files.writeString(page, content);
    Files.setLastModifiedTime(page, time);
    pf = build("a");
    assertTrue(pf.rapido.canReplay(file(pf, "Patient-b.json"), RapidoTracker.PHASE_HTML));
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
  /** the expansions that the html of the files the last finish reused had made */
  private final List<ValueSet> replayedExpansions = new ArrayList<>();
  /** the expansion that rendering a file (by name) makes */
  private final java.util.Map<String, ValueSet> expansions = new java.util.HashMap<>();

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
    replayedExpansions.clear();
    for (FetchedFile f : pf.fileList) {
      if (pf.rapido.canReplay(f, RapidoTracker.PHASE_HTML) && pf.rapido.canReplay(f, RapidoTracker.PHASE_SHEETS)) {
        // as replayGeneration does
        replayed.add(f.getLoadPath());
        RapidoTracker.Journal html = pf.rapido.replay(f, RapidoTracker.PHASE_HTML);
        replayedExpansions.addAll(html.expansions);
        f.getOutputNames().addAll(html.outputs);
        f.getOutputNames().addAll(pf.rapido.replay(f, RapidoTracker.PHASE_SHEETS).outputs);
        continue;
      }
      pf.rapido.startRecording(f, RapidoTracker.PHASE_HTML);
      if (f.getLoadPath().startsWith("Patient-")) {
        Path page = dir.resolve("temp").resolve(f.getLoadPath().replace(".json", ".html"));
        Files.createDirectories(page.getParent());
        Files.writeString(page, "<p>" + f.getLoadPath() + "</p>");
        f.getOutputNames().add(page.toString());
      }
      if (expansions.containsKey(f.getLoadPath())) {
        pf.expansions.add(expansions.get(f.getLoadPath()));
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
    pf.context = mock(org.hl7.fhir.standalone.context.SimpleWorkerContext.class);
    when(pf.context.getModelContext()).thenReturn(ModelContext.fullCoreContext());
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
