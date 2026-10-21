package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.FileNode;
import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.FileResult;
import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.ResourceNode;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Each test builds a small IG, analyses it, makes one edit, analyses it again, and checks which files Rapido would
 * validate or render again. These are the edges that the dependency tracking in 3.0.1 missed.
 */
class RapidoDependencyAnalyserTest {

  private static final String B = "http://example.org/ig";

  /** a mutable description of an IG: file key -> the resources in it, as (type, id, url, content strings) */
  private static class Ig {
    final Map<String, List<Res>> files = new LinkedHashMap<>();
    /** the resource types the build knows, or null for any */
    List<String> types;

    Res add(String file, String type, String id) {
      Res r = new Res(type, id);
      files.computeIfAbsent(file, k -> new ArrayList<>()).add(r);
      return r;
    }

    Res get(String file) {
      return files.get(file).get(0);
    }

    Map<String, FileResult> analyse() {
      List<FileNode> nodes = new ArrayList<>();
      for (Map.Entry<String, List<Res>> e : files.entrySet()) {
        FileNode fn = new FileNode(e.getKey(), "src:"+e.getKey());
        for (Res r : e.getValue()) {
          ResourceNode n = new ResourceNode(r.type, r.id).setUrl(r.url).setVersion(r.version).setName(r.name).setTitle(r.title).setBaseDefinition(r.base);
          StringBuilder content = new StringBuilder();
          for (String s : r.strings) {
            n.addString(s);
            content.append(s).append("\u0000");
          }
          for (String s : r.refs) {
            n.addReference(s);
            content.append("ref:").append(s).append("\u0000");
          }
          n.setContentHash(r.url+"|"+r.version+"|"+r.name+"|"+r.title+"|"+r.base+"|"+content);
          fn.addResource(n);
        }
        nodes.add(fn);
      }
      return new RapidoDependencyAnalyser(B, types).analyse(nodes);
    }
  }

  private static class Res {
    final String type;
    final String id;
    String url;
    String version;
    String name;
    String title;
    String base;
    final List<String> strings = new ArrayList<>();
    final List<String> refs = new ArrayList<>();

    Res(String type, String id) {
      this.type = type;
      this.id = id;
    }

    Res url(String url) {
      this.url = url;
      strings.add(url);
      return this;
    }

    Res title(String t) {
      this.title = t;
      return this;
    }

    Res base(String b) {
      this.base = b;
      strings.add(b);
      return this;
    }

    Res str(String s) {
      strings.add(s);
      return this;
    }
  }

  /** the outcome of one edit: which files would be validated and rendered again */
  private static class Outcome {
    final List<String> validate = new ArrayList<>();
    final List<String> render = new ArrayList<>();
  }

  private static Outcome edit(Ig ig, Consumer<Ig> change) {
    Map<String, FileResult> before = ig.analyse();
    change.accept(ig);
    Map<String, FileResult> after = ig.analyse();
    Outcome o = new Outcome();
    for (String f : ig.files.keySet()) {
      FileResult b = before.get(f);
      if (b == null || !b.getValidationHash().equals(after.get(f).getValidationHash())) {
        o.validate.add(f);
      }
      if (b == null || !b.getRenderHash().equals(after.get(f).getRenderHash())) {
        o.render.add(f);
      }
    }
    return o;
  }

  private static Ig terminologyIg() {
    Ig ig = new Ig();
    ig.add("cs-a", "CodeSystem", "cs-a").url(B+"/CodeSystem/cs-a").title("CS A").str("a1");
    ig.add("vs-a", "ValueSet", "vs-a").url(B+"/ValueSet/vs-a").title("VS A").str(B+"/CodeSystem/cs-a");
    ig.add("vs-b", "ValueSet", "vs-b").url(B+"/ValueSet/vs-b").title("VS B").str(B+"/ValueSet/vs-a");
    ig.add("base", "StructureDefinition", "base").url(B+"/StructureDefinition/base").base("http://hl7.org/fhir/StructureDefinition/Patient").str(B+"/ValueSet/vs-b|0.1.0");
    ig.add("derived", "StructureDefinition", "derived").url(B+"/StructureDefinition/derived").base(B+"/StructureDefinition/base");
    ig.add("ex1", "Patient", "ex1").str(B+"/StructureDefinition/derived").title("Example 1");
    ig.add("other", "Observation", "other").str("http://loinc.org").str("1234-5");
    return ig;
  }

  @Test
  void codeSystemChangeReachesValueSetsProfilesAndExamples() {
    Outcome o = edit(terminologyIg(), ig -> ig.get("cs-a").strings.set(1, "a1-changed"));
    // vs-a includes the code system, vs-b includes vs-a, base binds vs-b (versioned canonical), derived inherits
    // from base, and the example claims derived
    assertEquals(List.of("cs-a", "vs-a", "vs-b", "base", "derived", "ex1"), o.validate);
    assertFalse(o.render.contains("other"));
  }

  @Test
  void versionedCanonicalIsAnEdge() {
    // 3.0.1 ignored any canonical with a |version
    Outcome o = edit(terminologyIg(), ig -> ig.get("vs-b").title("VS B renamed"));
    assertTrue(o.validate.contains("base"));
  }

  @Test
  void usagesAreRenderedAgainButNotValidated() {
    // the profile's page lists its examples, so it's rendered again when an example changes
    Outcome o = edit(terminologyIg(), ig -> ig.get("ex1").title("Example 1 renamed"));
    assertEquals(List.of("ex1"), o.validate);
    assertTrue(o.render.contains("derived"));
    assertFalse(o.render.contains("other"));
  }

  @Test
  void valueSetPageListsProfilesThatInheritTheBinding() {
    // derived doesn't mention vs-b itself, but it has the binding through base
    Outcome o = edit(terminologyIg(), ig -> ig.get("derived").title("Derived renamed"));
    assertTrue(o.render.contains("vs-b"));
    assertTrue(o.render.contains("base"));
    assertFalse(o.validate.contains("vs-b"));
  }

  private static Ig logicIg() {
    Ig ig = new Ig();
    ig.add("vs", "ValueSet", "vs").url(B+"/ValueSet/vs").title("VS");
    ig.add("lib-common", "Library", "Common").url(B+"/Library/Common").str("define Common: x * 2");
    ig.add("lib-main", "Library", "Main").url(B+"/Library/Main").str("depends-on").str(B+"/Library/Common|1.0.0")
      .str(B+"/ValueSet/vs");
    ig.add("measure", "Measure", "m1").url(B+"/Measure/m1").str(B+"/Library/Main");
    ig.add("ad", "ActivityDefinition", "ad1").url(B+"/ActivityDefinition/ad1").str(B+"/Library/Main");
    ig.add("pd", "PlanDefinition", "pd1").url(B+"/PlanDefinition/pd1").str(B+"/ActivityDefinition/ad1");
    ig.add("plain", "Library", "plain").url(B+"/Library/plain").str("nothing here");
    return ig;
  }

  @Test
  void libraryChangeReachesMeasuresAndPlanDefinitions() {
    // relatedArtifact depends-on, Measure.library, ActivityDefinition.library, nested PlanDefinition action
    // definitionCanonical, dataRequirement.codeFilter.valueSet: all of them are canonical strings in the content
    Outcome o = edit(logicIg(), ig -> ig.get("lib-common").strings.set(1, "define Common: x * 3"));
    assertEquals(List.of("lib-common", "lib-main", "measure", "ad", "pd"), o.validate);
    assertFalse(o.render.contains("plain"));

    o = edit(logicIg(), ig -> ig.get("vs").title("VS renamed"));
    assertEquals(List.of("vs", "lib-main", "measure", "ad", "pd"), o.validate);
  }

  @Test
  void referencesInTextAndByTail() {
    Ig ig = new Ig();
    ig.add("target", "Practitioner", "pr1").title("Dr Who");
    ig.add("narrative", "Patient", "p1").str("<div xmlns=\"http://www.w3.org/1999/xhtml\">See <a href=\"https://example.org/fhir/Practitioner/pr1\">the GP</a>.</div>");
    ig.add("md", "Patient", "p2").str("Seen by [[[Practitioner/pr1]]] last week");
    ig.add("relative", "Encounter", "e1").str("Practitioner/pr1/_history/2");
    ig.add("unrelated", "Patient", "p3").str("nothing");
    Outcome o = edit(ig, i -> i.get("target").title("Dr Who 2"));
    assertEquals(List.of("target", "narrative", "md", "relative"), o.validate);
  }

  @Test
  void unresolvedLocalReferenceDependsOnTheInventory() {
    Ig ig = new Ig();
    ig.add("p1", "Patient", "p1").str("Practitioner/missing");
    ig.add("p2", "Patient", "p2").str("http://loinc.org");
    // a resource appears that the dangling reference might have meant
    Outcome o = edit(ig, i -> i.add("pr", "Practitioner", "missing"));
    assertTrue(o.validate.contains("p1"));
    assertFalse(o.validate.contains("p2"));
    // now it resolves, so a change elsewhere doesn't matter any more
    o = edit(ig, i -> i.get("p2").title("renamed"));
    assertFalse(o.validate.contains("p1"));

    // while it doesn't resolve, a change to the title (or url, name, id) of anything else does
    Ig ig2 = new Ig();
    ig2.add("p1", "Patient", "p1").str("Practitioner/missing");
    ig2.add("p2", "Patient", "p2");
    o = edit(ig2, i -> i.get("p2").title("renamed"));
    assertTrue(o.validate.contains("p1"));
  }

  @Test
  void externalReferencesDontDependOnTheInventory() {
    Ig ig = new Ig();
    ig.add("p1", "Patient", "p1").str("http://hl7.org/fhir/StructureDefinition/patient-birthPlace").str("http://snomed.info/sct");
    ig.add("p2", "Patient", "p2");
    Map<String, FileResult> res = ig.analyse();
    assertFalse(res.get("p1").isUsesInventory());
    Outcome o = edit(ig, i -> i.get("p2").title("renamed"));
    assertEquals(List.of("p2"), o.validate);
  }

  /** a reference resolves even when an earlier one has already found the same resource (by url, then as Type/id) */
  @Test
  void repeatedReferencesStillResolve() {
    Ig ig = new Ig();
    ig.add("ext", "StructureDefinition", "ext").url(B+"/StructureDefinition/ext").str("StructureDefinition/ext");
    ig.add("p1", "Patient", "p1").str(B+"/StructureDefinition/ext").str("StructureDefinition/ext").str(B+"/StructureDefinition/ext|1.0");
    ig.add("p2", "Patient", "p2");
    Map<String, FileResult> res = ig.analyse();
    assertFalse(res.get("ext").isUsesInventory());
    assertFalse(res.get("p1").isUsesInventory());
    Outcome o = edit(ig, i -> i.get("p2").title("renamed"));
    assertEquals(List.of("p2"), o.validate);
  }

  @Test
  void mappingsAreNotRelativeReferences() {
    // base snapshots carry v2 mappings such as XPN/4, which look like Type/id
    Ig ig = new Ig();
    ig.add("prof", "StructureDefinition", "prof").url(B+"/StructureDefinition/prof").str("XPN/4");
    ig.add("ex", "Patient", "ex").str(B+"/StructureDefinition/prof");
    ig.add("other", "Patient", "other");
    ig.add("model", "StructureDefinition", "MyModel").url(B+"/StructureDefinition/MyModel");
    ig.add("inst", "Basic", "inst").str("MyModel/missing").str("Practitioner/missing");
    // without the list of types, any capitalised name could be a type: the profile and its examples depend on everything
    assertTrue(ig.analyse().get("prof").isUsesInventory());
    assertEquals(List.of("prof", "ex", "other", "inst"), edit(ig, i -> i.get("other").title("renamed")).validate);

    ig.types = List.of("Patient", "Practitioner", "Basic", "StructureDefinition");
    Map<String, FileResult> res = ig.analyse();
    assertFalse(res.get("prof").isUsesInventory());
    assertFalse(res.get("ex").isUsesInventory());
    // a real resource type, or a logical model of the IG, is still meant to be local
    assertTrue(res.get("inst").isUsesInventory());
    ig.get("inst").strings.remove("Practitioner/missing");
    assertTrue(ig.analyse().get("inst").isUsesInventory());
    Outcome o = edit(ig, i -> i.get("other").title("renamed again"));
    assertEquals(List.of("other", "inst"), o.validate);
  }

  @Test
  void cyclesAreHarmless() {
    Ig ig = new Ig();
    ig.add("a", "ValueSet", "a").url(B+"/ValueSet/a").str(B+"/ValueSet/b");
    ig.add("b", "ValueSet", "b").url(B+"/ValueSet/b").str(B+"/ValueSet/c");
    ig.add("c", "ValueSet", "c").url(B+"/ValueSet/c").str(B+"/ValueSet/a");
    ig.add("d", "StructureDefinition", "d").url(B+"/StructureDefinition/d").base(B+"/StructureDefinition/e");
    ig.add("e", "StructureDefinition", "e").url(B+"/StructureDefinition/e").base(B+"/StructureDefinition/d");
    Outcome o = edit(ig, i -> i.get("c").title("C"));
    assertEquals(List.of("a", "b", "c"), o.validate);
    Map<String, FileResult> res = ig.analyse();
    assertEquals(2, res.get("a").getDependsOn().size());
    o = edit(ig, i -> i.get("e").title("E"));
    assertEquals(List.of("d", "e"), o.validate);
  }

  @Test
  void hashesAreStable() {
    Map<String, FileResult> a = terminologyIg().analyse();
    Map<String, FileResult> b = terminologyIg().analyse();
    for (String k : a.keySet()) {
      assertEquals(a.get(k).getValidationHash(), b.get(k).getValidationHash());
      assertEquals(a.get(k).getRenderHash(), b.get(k).getRenderHash());
    }
    assertNotEquals(a.get("vs-a").getValidationHash(), a.get("vs-b").getValidationHash());
  }

  @Test
  void validationMessagesRoundTrip() {
    ValidationMessage vm = new ValidationMessage(Source.InstanceValidator, IssueType.CODEINVALID, 3, 7, "Patient.gender", "bad code", IssueSeverity.ERROR);
    vm.setMessageId("CODE_INVALID");
    vm.setTxLink("tx.html#1");
    vm.setSlicingHint(true);
    vm.setRuleDate(new Date(1000000L));
    vm.setServer("http://tx.fhir.org");
    vm.incCount();
    vm.setSliceHtml("<b>slice</b>", List.of(new ValidationMessage(Source.InstanceValidator, IssueType.STRUCTURE, "Patient.x", "inner", IssueSeverity.INFORMATION)));
    JsonObject json = RapidoTracker.writeMessage(vm);
    ValidationMessage back = RapidoTracker.readMessage(json);
    assertEquals(json.toString(), RapidoTracker.writeMessage(back).toString());
    assertEquals(vm.getLocation(), back.getLocation());
    assertEquals(vm.getLevel(), back.getLevel());
    assertEquals(vm.getLine(), back.getLine());
    assertEquals(vm.getRuleDate(), back.getRuleDate());
    assertEquals(1, back.getSliceInfo().size());
    assertEquals(vm.getDisplay(), back.getDisplay());
    assertTrue(vm.matches(back));
  }

  /** the scan of the other files in the IG folder must stop on a link back up the tree (it used to recurse until the
   * path got too long) and still see edits and skip what it's told to */
  @Test
  void otherFilesScanSurvivesLinkLoops(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve("ig.ini"), "[IG]");
    Files.createDirectories(dir.resolve("input"));
    Files.writeString(dir.resolve("input/x.txt"), "one");
    Files.createDirectories(dir.resolve("output"));
    Files.writeString(dir.resolve("output/o.txt"), "built");
    Files.createSymbolicLink(dir.resolve("self"), dir);
    Files.createSymbolicLink(dir.resolve("input/up"), dir.resolve("input"));
    Set<String> skip = Set.of(dir.resolve("output").toFile().getCanonicalPath());
    Map<String, String> a = RapidoTracker.scanTree(dir.toFile(), skip);
    assertEquals(Set.of("ig.ini", "input/x.txt", "input/up/", "self/"), a.keySet());
    assertEquals(a, RapidoTracker.scanTree(dir.toFile(), skip));
    Files.writeString(dir.resolve("input/x.txt"), "two");
    assertNotEquals(a.get("input/x.txt"), RapidoTracker.scanTree(dir.toFile(), skip).get("input/x.txt"));
  }
}
