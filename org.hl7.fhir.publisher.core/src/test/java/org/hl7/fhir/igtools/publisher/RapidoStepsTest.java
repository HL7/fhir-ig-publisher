package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What a step that makes fragments for the IG as a whole did is kept for the next build, by the key it had */
class RapidoStepsTest {

  @TempDir
  Path dir;

  private static RapidoSteps.Fragment fragment(String name, String lang, String copyStamp) {
    return new RapidoSteps.Fragment(name, lang, "code-" + name, "Cross", 12, 345, "stamp-" + name, copyStamp);
  }

  /** the steps of a build that ran one step, as the next build reads them from the state file */
  private static JsonObject lastBuild(RapidoSteps.Fragment... fragments) throws Exception {
    RapidoSteps steps = new RapidoSteps();
    steps.start("step", "key");
    for (RapidoSteps.Fragment f : fragments) {
      steps.record(f);
    }
    steps.finish();
    return JsonParser.parseObject(JsonParser.compose(steps.save(), false));
  }

  @Test
  void aStepIsReusableByTheKeyItHad() throws Exception {
    RapidoSteps steps = new RapidoSteps();
    steps.load(lastBuild(fragment("a", "en", "copy"), fragment("b", null, null)));

    List<RapidoSteps.Fragment> last = steps.reusable("step", "key");

    assertEquals(2, last.size());
    RapidoSteps.Fragment a = last.get(0);
    assertEquals(List.of("a", "en", "code-a", "Cross", "stamp-a", "copy"), List.of(a.name, a.lang, a.code, a.context, a.stamp, a.copyStamp));
    assertEquals(12, a.time);
    assertEquals(345, a.size);
    assertNull(last.get(1).lang);
    assertNull(last.get(1).copyStamp);
    assertNull(steps.reusable("step", "another key"));
    assertNull(steps.reusable("step", null));
    assertNull(steps.reusable("another step", "key"));
  }

  @Test
  void withNoStateToReuseNoStepIsReusable() throws Exception {
    RapidoSteps steps = new RapidoSteps();
    assertNull(steps.reusable("step", "key"));
    steps.load(null);
    assertNull(steps.reusable("step", "key"));
  }

  @Test
  void aRecordThatCantBeReadIsNotReused() throws Exception {
    JsonObject last = lastBuild(fragment("a", "en", null));
    last.getJsonObject("step").getJsonArray("fragments").get(0).asJsonArray().remove(last.getJsonObject("step").getJsonArray("fragments").get(0).asJsonArray().get(7));
    RapidoSteps steps = new RapidoSteps();
    steps.load(last);
    assertNull(steps.reusable("step", "key"));

    last = JsonParser.parseObject("{\"step\":{\"key\":\"key\",\"fragments\":[[\"a\",\"en\",\"c\",\"Cross\",\"soon\",\"345\",\"stamp\",\"\"]]}}");
    steps.load(last);
    assertNull(steps.reusable("step", "key"));

    steps.load(JsonParser.parseObject("{\"step\":{\"key\":\"key\"}}"));
    assertNull(steps.reusable("step", "key"));

    steps.load(JsonParser.parseObject("{\"step\":{\"key\":\"key\",\"fragments\":{}}}"));
    assertNull(steps.reusable("step", "key"));

    steps.load(JsonParser.parseObject("{\"step\":{\"key\":\"key\",\"fragments\":[[\"a\",\"en\",\"c\",\"Cross\",\"1\",\"2\",{},\"\"]]}}"));
    assertNull(steps.reusable("step", "key"));
  }

  @Test
  void onlyStepsThatRanOrWereReusedAreKeptForTheNextBuild() throws Exception {
    RapidoSteps steps = new RapidoSteps();
    steps.load(lastBuild(fragment("a", "en", null)));
    steps.keep("step");
    steps.start("failed", "key");
    steps.record(fragment("b", "en", null));
    assertTrue(steps.isRecording());
    steps.abandon();
    assertFalse(steps.isRecording());
    steps.record(fragment("c", "en", null)); // not in a step
    steps.finish(); // nothing to finish
    steps.start("new", "key");
    steps.finish();

    JsonObject saved = steps.save();

    assertEquals(Set.of("step", "new"), Set.copyOf(saved.getNames()));
    assertEquals(1, saved.getJsonObject("step").getJsonArray("fragments").size());
    assertEquals(0, saved.getJsonObject("new").getJsonArray("fragments").size());
    assertEquals(1, steps.getReused());
    assertEquals(2, steps.getRun());
  }

  @Test
  void aStampTellsAFileThatWasWrittenAgain() throws Exception {
    Path file = dir.resolve("f.xhtml");
    Files.writeString(file, "content");
    Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000_000_000L));
    String stamp = RapidoSteps.stamp(file.toString());

    assertEquals(stamp, RapidoSteps.stamp(file.toString()));
    Files.writeString(file, "CONTENT"); // the same size
    assertNotEquals(stamp, RapidoSteps.stamp(file.toString()));
    Files.writeString(file, "content");
    Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000_000_000L));
    assertEquals(stamp, RapidoSteps.stamp(file.toString()));
    Files.writeString(file, "content and more");
    Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000_000_000L));
    assertNotEquals(stamp, RapidoSteps.stamp(file.toString()));
  }

  @Test
  void aFileThatIsNotThereHasNoStampToMatch() throws Exception {
    String missing = dir.resolve("missing.xhtml").toString();
    assertNotEquals(RapidoSteps.stamp(missing), RapidoSteps.stamp(missing));
    assertNotEquals(RapidoSteps.stamp(dir.toString()), RapidoSteps.stamp(dir.toString()));
  }
}
