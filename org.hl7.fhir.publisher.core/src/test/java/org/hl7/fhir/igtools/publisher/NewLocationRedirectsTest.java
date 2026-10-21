package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.model.core.Extension;
import org.hl7.fhir.model.core.IdType;
import org.hl7.fhir.model.core.ImplementationGuide;
import org.hl7.fhir.model.core.StringType;
import org.hl7.fhir.model.core.UrlType;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NewLocationRedirectsTest {

  private static final String NEW_SPEC = "https://example.org/fhir/terminology";

  // the relevant part of the defaults in the base template's config.json
  private static final String DEFAULTS = "{"+
      "\"Any\": {\"template-base\": \"x.html\", \"base\": \"{{[type]}}-{{[id]}}.html\", \"format\": \"{{[type]}}-{{[id]}}.{{[fmt]}}.html\"},"+
      "\"ValueSet\": {\"base\": \"{{[type]}}-{{[id]}}.html\", \"testing\": \"{{[type]}}-{{[id]}}-testing.html\"}"+
      "}";

  @TempDir
  Path temp;

  private String output;
  private JsonObject defaults;
  private List<ValidationMessage> errors;

  @BeforeEach
  void setUp() throws IOException {
    output = Files.createDirectories(temp.resolve("output")).toString();
    defaults = JsonParser.parseObject(DEFAULTS);
    errors = new ArrayList<>();
  }

  private static Extension newLocation(String artifact, String page, String newSpec, String rename, String version) {
    Extension ext = new Extension(NewLocationRedirects.EXT_NEW_LOCATION);
    if (artifact != null) {
      ext.addExtension("artifact", new UrlType(artifact));
    }
    if (page != null) {
      ext.addExtension("page", new StringType(page));
    }
    if (newSpec != null) {
      ext.addExtension("newSpec", new UrlType(newSpec));
    }
    if (rename != null) {
      ext.addExtension("rename", new StringType(rename));
    }
    if (version != null) {
      ext.addExtension("version", new IdType(version));
    }
    return ext;
  }

  private NewLocationRedirects load(String igVersion, Extension... exts) {
    ImplementationGuide ig = new ImplementationGuide();
    for (Extension ext : exts) {
      ig.addExtension(ext);
    }
    NewLocationRedirects nl = new NewLocationRedirects(errors);
    nl.load(ig, igVersion);
    return nl;
  }

  private String read(String... path) throws IOException {
    return FileUtilities.fileToString(Path.of(output, path).toFile());
  }

  private boolean exists(String... path) {
    return Path.of(output, path).toFile().exists();
  }

  private long count(IssueSeverity level) {
    return errors.stream().filter(m -> m.getLevel() == level).count();
  }

  @Test
  void redirectsTheMainPageOfAMovedArtifact() throws IOException {
    NewLocationRedirects nl = load("2.0.0", newLocation("ValueSet/be-civilstate", null, NEW_SPEC, null, "2.0.0"));
    nl.generate(output, defaults, Set.of());
    String page = read("ValueSet-be-civilstate.html");
    assertTrue(page.contains("url="+NEW_SPEC+"/ValueSet-be-civilstate.html\""), page);
    // the HTML checker doesn't allow <link> elements that point outside the IG
    assertFalse(page.contains("<link"), page);
    // only the main page: the canonical URL is redirected when the IG is published, by format
    assertFalse(exists("ValueSet-be-civilstate.json.html"));
    assertFalse(exists("ValueSet-be-civilstate-testing.html"));
    assertFalse(exists("ValueSet"));
    NewLocationRedirects.Target t = nl.getRedirects().get("ValueSet/be-civilstate");
    assertEquals(NEW_SPEC+"/ValueSet-be-civilstate.html", t.getPage());
    assertEquals(NEW_SPEC+"/ValueSet-be-civilstate", t.getSource());
    assertEquals(0, count(IssueSeverity.ERROR), errors.toString());
  }

  @Test
  void usesTheNewIdWhenRenamed() throws IOException {
    NewLocationRedirects nl = load("2.0.0", newLocation("ValueSet/be-civilstate", null, NEW_SPEC, "be-civil-state", "2.0.0"));
    nl.generate(output, defaults, Set.of());
    assertTrue(read("ValueSet-be-civilstate.html").contains("url="+NEW_SPEC+"/ValueSet-be-civil-state.html\""));
    assertEquals(NEW_SPEC+"/ValueSet-be-civil-state", nl.getRedirects().get("ValueSet/be-civilstate").getSource());
  }

  @Test
  void redirectsAMovedPage() throws IOException {
    NewLocationRedirects nl = load("2.0.0", newLocation(null, "terminology-guidance", NEW_SPEC, "guidance", "2.0.0"));
    nl.generate(output, defaults, Set.of());
    assertTrue(read("terminology-guidance.html").contains("url="+NEW_SPEC+"/guidance.html\""));
    assertEquals(NEW_SPEC+"/guidance.html", nl.getRedirects().get("terminology-guidance").getPage());
    assertNull(nl.getRedirects().get("terminology-guidance").getSource());
  }

  @Test
  void appliesFromItsVersionOn() throws IOException {
    load("1.1.0", newLocation("ValueSet/a", null, NEW_SPEC, null, "2.0.0")).generate(output, defaults, Set.of());
    load("2.0.0-ballot", newLocation("ValueSet/b", null, NEW_SPEC, null, "2.0.0")).generate(output, defaults, Set.of());
    load("2.1.0", newLocation("ValueSet/c", null, NEW_SPEC, null, "2.0.0")).generate(output, defaults, Set.of());
    assertFalse(exists("ValueSet-a.html"));
    assertFalse(exists("ValueSet-b.html"));
    assertTrue(exists("ValueSet-c.html"));
    assertEquals(0, count(IssueSeverity.ERROR), errors.toString());
  }

  @Test
  void doesNotReplaceContentTheIGStillHas() throws IOException {
    FileUtilities.stringToFile("<html>the real page</html>", new File(output, "ValueSet-kept.html"));
    NewLocationRedirects nl = load("2.0.0",
        newLocation("ValueSet/still-here", null, NEW_SPEC, null, "2.0.0"),
        newLocation("ValueSet/kept", null, NEW_SPEC, null, "2.0.0"));
    nl.generate(output, defaults, Set.of("ValueSet/still-here"));
    assertFalse(exists("ValueSet-still-here.html"));
    assertEquals("<html>the real page</html>", read("ValueSet-kept.html"));
    assertEquals(2, count(IssueSeverity.ERROR), errors.toString());
  }

  @Test
  void reportsInvalidNewLocations() {
    load("2.0.0",
        newLocation("ValueSet/a", "a", NEW_SPEC, null, "2.0.0"),
        newLocation(null, null, NEW_SPEC, null, "2.0.0"),
        newLocation("http://example.org/ValueSet/a", null, NEW_SPEC, null, "2.0.0"),
        newLocation(null, "a.html", NEW_SPEC, null, "2.0.0"),
        newLocation("ValueSet/a", null, "terminology", null, "2.0.0"),
        newLocation("ValueSet/a", null, NEW_SPEC, null, null),
        newLocation("ValueSet/a", null, NEW_SPEC, null, "two"));
    assertEquals(7, count(IssueSeverity.ERROR), errors.toString());
  }
}
