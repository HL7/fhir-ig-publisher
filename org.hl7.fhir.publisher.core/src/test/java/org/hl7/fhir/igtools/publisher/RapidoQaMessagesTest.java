package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Builds a small IG (src/test/resources/rapido-qa-ig) and then edits it: after each edit, a -rapido build of the same
 * folder (a differential build) must report exactly the messages a complete build of a fresh copy reports - the same
 * messages, on the same files and locations, with the same severity, the same number of times, in every QA output
 * (qa.compare.txt, qa.json, qa.xml, the suppressed messages and their use counts in qa.html, the package's
 * validation-summary.json and validation-oo.json, and the totals in the build log).
 *
 * <p>Each build runs in its own JVM, as -rapido builds do from the command line. It needs a terminology server, the
 * template and the core packages (or a package cache that has them) and Jekyll, and takes a few minutes, so it only runs
 * when asked: mvn test -Dtest=RapidoQaMessagesTest -Drapido.qa.ig=true
 */
@EnabledIfSystemProperty(named = "rapido.qa.ig", matches = "true")
class RapidoQaMessagesTest {

  @TempDir
  Path dir;

  private Path w;

  private static final Pattern LOG_TOTALS = Pattern.compile("Errors: \\d+, Warnings: \\d+, Info: \\d+, Broken Links: \\d+");
  private static final Pattern REUSED = Pattern.compile("Rapido Mode: reused (\\d+) of");
  private static final Pattern SIGNPOST = Pattern.compile("<li>([^<]*: Validated against .*?)</li>");
  private static final Pattern SUPPRESSED = Pattern.compile("<li>(.*?) <span style=\"color: (?:navy|maroon)\">\\((\\d+) uses\\)</span></li>");

  @Test
  void aDifferentialBuildReportsWhatACompleteBuildDoes() throws Exception {
    Path fixture = Paths.get(getClass().getResource("/rapido-qa-ig/ig.ini").toURI()).getParent();
    w = dir.resolve("w");
    copy(fixture, w);
    // the first -rapido build is a complete one
    build(w, true, "s0");

    // an example's error fixed and one introduced, an example for the data type profile that had none, a broken link
    // fixed and another one made
    sub("input/examples/Patient-qa-good.json", "\"name\"", "\"gender\": \"qa-worse\",\n  \"address\": [{\"city\": \"Tallinn\"}],\n  \"name\"");
    sub("input/examples/Patient-qa-bad.json", "\"gender\": \"qa-bad\",", "");
    sub("input/pagecontent/other.md", "(no-such-page.html)", "(index.html)");
    append("input/pagecontent/index.md", "\nAnd [another missing page](missing.html).\n");
    check("s1", true);
    pageEdit("s2");

    // a suppressed message entry replaced by another: -rapido builds everything again for that
    sub("input/ignoreWarnings.txt", "%should have an OID assigned%", "%Unable to find ImplementationGuide.definition.resource.description%");
    check("s3", false);
    pageEdit("s4");

    // the examples and the pages back as they were: the data type profile has no examples again
    for (String p : new String[] {"input/examples", "input/pagecontent/index.md"}) {
      restore(fixture, p);
    }
    check("s5", true);
    pageEdit("s6");

    restore(fixture, "input/ignoreWarnings.txt");
    check("s7", false);
    pageEdit("s8");

    // the value set loses the code an example uses
    sub("input/resources/ValueSet-qa-valueset.json", "\"system\": \"http://example.org/fhir/rapido-qa/CodeSystem/qa-codes\"",
        "\"system\": \"http://example.org/fhir/rapido-qa/CodeSystem/qa-codes\", \"concept\": [{\"code\": \"a\"}]");
    check("s9", true);
    pageEdit("s10");
  }

  /**
   * What validating a resource leaves behind for the rest of the build: the profiles it validated each element against
   * (the qa page's signposts), and what the build checks after validation (a realm IG that uses SNOMED CT without fixing
   * the edition gets a warning). Three examples of a profile, the first with a SNOMED CT code, and all with a code system
   * that isn't known; only the last one changes, so a differential build reuses the first two
   */
  @Test
  void aReusedValidationLeavesTheBuildAsValidatingWould() throws Exception {
    Path fixture = Paths.get(getClass().getResource("/rapido-reuse-ig/ig.ini").toURI()).getParent();
    w = dir.resolve("w");
    copy(fixture, w);
    build(w, true, "u0");
    sub("input/examples/Observation-qa-c.json", "note c", "note c, edited");
    check("u1", true);
    sub("input/examples/Observation-qa-c.json", "note c, edited", "note c, edited again");
    check("u2", true);
  }

  private void pageEdit(String state) throws Exception {
    append("input/pagecontent/other.md", "\nA page edit before " + state + ".\n");
    check(state, true);
  }

  private void restore(Path fixture, String p) throws IOException {
    Path t = w.resolve(p);
    if (Files.isDirectory(t)) {
      deleteAll(t);
    }
    copy(fixture.resolve(p), t);
  }

  /** a -rapido build of w, and a complete build of a copy of it, must report the same */
  private void check(String state, boolean differential) throws Exception {
    String rapidoLog = build(w, true, state);
    // else a state meant to test a differential build compares two complete builds
    assertEquals(differential, rapidoLog.contains("Rapido Mode: Differential Build"),
        "The -rapido build after " + state + (differential ? " wasn't" : " was") + " a differential one");
    if (differential) {
      // else a differential build that validates everything again passes too
      Matcher m = REUSED.matcher(rapidoLog);
      assertTrue(m.find() && Integer.parseInt(m.group(1)) > 0, "The -rapido build after " + state + " didn't reuse any validations");
    }
    Path r = dir.resolve("r-" + state);
    copy(w, r);
    String completeLog = build(r, false, state + "-complete");
    Map<String, Integer> expected = messages(r, completeLog);
    Map<String, Integer> actual = messages(w, rapidoLog);
    assertTrue(expected.keySet().stream().anyMatch(k -> k.startsWith("qa.compare.txt|")), "no messages in " + r);
    assertEquals(describe(expected, actual), "", "the messages of the -rapido build after " + state);
    deleteAll(r);
  }

  // --- builds ---------------------------------------------------------------------------------------------------------

  private String build(Path ig, boolean rapido, String name) throws IOException, InterruptedException {
    List<String> cmd = new ArrayList<>();
    // e.g. -Drapido.qa.prefix="flock /some/lock" to take turns with other builds on the machine
    String prefix = System.getProperty("rapido.qa.prefix");
    if (prefix != null && !prefix.isBlank()) {
      cmd.addAll(Arrays.asList(prefix.trim().split("\\s+")));
    }
    cmd.add(ProcessHandle.current().info().command().orElse("java"));
    cmd.add("-Xmx3g");
    cmd.add("-cp");
    cmd.add(System.getProperty("java.class.path"));
    cmd.add(Publisher.class.getName());
    cmd.add("-ig");
    cmd.add(ig.toString());
    cmd.add("-no-sushi");
    // e.g. -Drapido.qa.args="-tx https://tx.fhir.org -package-cache-folder /some/cache"
    String args = System.getProperty("rapido.qa.args");
    if (args != null && !args.isBlank()) {
      cmd.addAll(Arrays.asList(args.trim().split("\\s+")));
    }
    if (rapido) {
      cmd.add("-rapido");
    }
    File log = dir.resolve(name + ".log").toFile();
    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log).start();
    if (!p.waitFor(20, TimeUnit.MINUTES)) {
      p.destroyForcibly();
      throw new IOException("The build " + name + " didn't finish");
    }
    String res = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
    assertEquals(0, p.exitValue(), "The build " + name + " failed: " + tail(res));
    assertTrue(LOG_TOTALS.matcher(res).find(), "The build " + name + " didn't finish: " + tail(res));
    return res;
  }

  private static String tail(String s) {
    return s.length() > 4000 ? s.substring(s.length() - 4000) : s;
  }

  // --- the messages ---------------------------------------------------------------------------------------------------

  /** The messages of the build of the IG in folder ig, as a multiset of keys (source|what) */
  static Map<String, Integer> messages(Path ig, String log) throws Exception {
    Map<String, Integer> res = new TreeMap<>();
    String root = ig.toFile().getCanonicalPath();
    Path out = ig.resolve("output");

    String totals = null;
    Matcher m = LOG_TOTALS.matcher(log);
    while (m.find()) {
      totals = m.group();
    }
    add(res, "log|" + totals);

    JsonObject qa = JsonParser.parseObject(out.resolve("qa.json").toFile());
    for (String n : new String[] {"errs", "warnings", "hints", "broken-links", "suppressed-hints", "suppressed-warnings"}) {
      add(res, "qa.json|" + n + "=" + qa.asString(n));
    }

    String section = "";
    for (String line : Files.readAllLines(out.resolve("qa.compare.txt"), StandardCharsets.UTF_8)) {
      line = norm(line, root, ig);
      if (line.startsWith("== ")) {
        section = line;
      } else if (!line.trim().isEmpty() && !line.startsWith("Generated ") && !line.startsWith("$--")) {
        add(res, "qa.compare.txt|" + section + "|" + line.trim());
      }
    }

    String html = new String(Files.readAllBytes(out.resolve("qa.html")), StandardCharsets.UTF_8);
    m = SUPPRESSED.matcher(html);
    while (m.find()) {
      add(res, "qa.html suppressed|" + m.group(1) + "|" + m.group(2));
    }

    m = SIGNPOST.matcher(html);
    while (m.find()) {
      add(res, "qa.html signpost|" + norm(m.group(1), root, ig));
    }

    Document xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(out.resolve("qa.xml").toFile());
    NodeList oos = xml.getElementsByTagName("OperationOutcome");
    for (int i = 0; i < oos.getLength(); i++) {
      Element oo = (Element) oos.item(i);
      String file = "";
      for (Element e : children(oo, "extension")) {
        file = value(e, "valueString");
      }
      for (Element issue : children(oo, "issue")) {
        StringBuilder b = new StringBuilder("qa.xml|" + norm(file, root, ig));
        b.append("|").append(value(issue, "severity")).append("|").append(value(issue, "code"));
        for (Element e : children(issue, "location")) {
          b.append("|@").append(e.getAttribute("value"));
        }
        for (Element e : children(issue, "expression")) {
          b.append("|").append(e.getAttribute("value"));
        }
        for (Element d : children(issue, "details")) {
          b.append("|").append(value(d, "text"));
        }
        add(res, norm(b.toString(), root, ig));
      }
    }

    NpmPackage npm;
    try (InputStream s = new FileInputStream(out.resolve("package.tgz").toFile())) {
      npm = NpmPackage.fromPackage(s);
    }
    JsonObject summary = JsonParser.parseObject(npm.load("other", "validation-summary.json"));
    for (JsonProperty p : summary.getProperties()) {
      JsonObject o = (JsonObject) p.getValue();
      add(res, "validation-summary.json|" + p.getName() + "|" + o.asString("errors") + "|" + o.asString("warnings"));
    }
    JsonObject bundle = JsonParser.parseObject(npm.load("other", "validation-oo.json"));
    for (JsonObject entry : bundle.forceArray("entry").asJsonObjects()) {
      JsonObject oo = entry.getJsonObject("resource");
      add(res, "validation-oo.json|" + oo.asString("id"));
      for (JsonObject issue : oo.forceArray("issue").asJsonObjects()) {
        JsonObject details = issue.getJsonObject("details");
        add(res, norm("validation-oo.json|" + oo.asString("id") + "|" + issue.asString("severity") + "|" + issue.asString("code")
            + "|" + issue.forceArray("expression").asStrings() + "|" + (details == null ? "" : details.asString("text")), root, ig));
      }
    }
    return res;
  }

  private static String norm(String s, String root, Path ig) {
    return s == null ? "" : s.replace(root, "<ig>").replace(ig.toString(), "<ig>");
  }

  private static void add(Map<String, Integer> set, String key) {
    set.merge(key, 1, Integer::sum);
  }

  private static List<Element> children(Element e, String name) {
    List<Element> res = new ArrayList<>();
    for (int i = 0; i < e.getChildNodes().getLength(); i++) {
      if (e.getChildNodes().item(i) instanceof Element && name.equals(((Element) e.getChildNodes().item(i)).getTagName())) {
        res.add((Element) e.getChildNodes().item(i));
      }
    }
    return res;
  }

  private static String value(Element e, String name) {
    List<Element> c = children(e, name);
    return c.isEmpty() ? "" : c.get(0).getAttribute("value");
  }

  /** "" if the multisets are the same, else what is in only one of them */
  static String describe(Map<String, Integer> expected, Map<String, Integer> actual) {
    StringBuilder b = new StringBuilder();
    for (Map.Entry<String, Integer> e : expected.entrySet()) {
      int n = actual.getOrDefault(e.getKey(), 0);
      if (n != e.getValue()) {
        b.append("complete ").append(e.getValue()).append(" x, -rapido ").append(n).append(" x: ").append(e.getKey()).append("\n");
      }
    }
    for (Map.Entry<String, Integer> e : actual.entrySet()) {
      if (!expected.containsKey(e.getKey())) {
        b.append("complete 0 x, -rapido ").append(e.getValue()).append(" x: ").append(e.getKey()).append("\n");
      }
    }
    return b.toString();
  }

  // --- files ----------------------------------------------------------------------------------------------------------

  private void sub(String path, String from, String to) throws IOException {
    Path p = w.resolve(path);
    String s = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    assertTrue(s.contains(from), from + " in " + path);
    Files.write(p, s.replace(from, to).getBytes(StandardCharsets.UTF_8));
  }

  private void append(String path, String text) throws IOException {
    Path p = w.resolve(path);
    Files.write(p, (new String(Files.readAllBytes(p), StandardCharsets.UTF_8) + text).getBytes(StandardCharsets.UTF_8));
  }

  /** copies the IG's source files (not what a build writes) */
  private static void copy(Path from, Path to) throws IOException {
    if (Files.isRegularFile(from)) {
      Files.createDirectories(to.getParent());
      Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
      return;
    }
    try (Stream<Path> s = Files.walk(from)) {
      for (Path p : (Iterable<Path>) s::iterator) {
        Path rel = from.relativize(p);
        String first = rel.getNameCount() == 0 ? "" : rel.getName(0).toString();
        if (first.equals("output") || first.equals("temp") || first.equals("template") || first.startsWith("qa")) {
          continue;
        }
        Path t = to.resolve(rel.toString());
        if (Files.isDirectory(p)) {
          Files.createDirectories(t);
        } else {
          Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  private static void deleteAll(Path p) throws IOException {
    try (Stream<Path> s = Files.walk(p)) {
      s.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(x -> x.toFile().delete());
    }
  }
}
