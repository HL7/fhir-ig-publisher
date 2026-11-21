package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Links out of the IG's folder: refused on the ci-build; locally, the build stops unless the user has allowed them for
 * the IG (-allow-external-links, recorded in fhir-settings.json for that IG), and then it goes on with a warning
 */
class ExternalLinkPermissionTest {

  @TempDir
  Path dir;

  private static final List<String> LINKS = List.of("input/pagecontent/secret.md");

  private String settings() {
    return dir.resolve("fhir-settings.json").toString();
  }

  private String ig(String name) throws IOException {
    return Files.createDirectories(dir.resolve(name)).toString();
  }

  private String settingsContent() throws IOException {
    return new String(Files.readAllBytes(dir.resolve("fhir-settings.json")), StandardCharsets.UTF_8);
  }

  @Test
  void noLinksIsFine() throws IOException {
    List<String> warnings = new ArrayList<>();
    ExternalLinkPermission.check(List.of(), ig("ig"), true, false, settings(), warnings::add);
    ExternalLinkPermission.check(List.of(), ig("ig"), false, false, settings(), warnings::add);
    assertTrue(warnings.isEmpty());
  }

  @Test
  void theCiBuildRefusesThemWhateverTheSettingsSay() throws IOException {
    ExternalLinkPermission.allow(settings(), ig("ig"));
    FHIRException e = assertThrows(FHIRException.class, () -> ExternalLinkPermission.check(LINKS, ig("ig"), true, true, settings(), s -> {}));
    assertTrue(e.getMessage().contains("input/pagecontent/secret.md"), e.getMessage());
  }

  @Test
  void locallyTheBuildStopsAndExplains() {
    FHIRException e = assertThrows(FHIRException.class, () -> ExternalLinkPermission.check(LINKS, ig("ig"), false, false, settings(), s -> {}));
    assertTrue(e.getMessage().contains(ExternalLinkPermission.PARAMETER), e.getMessage());
    assertFalse(Files.exists(dir.resolve("fhir-settings.json")));
  }

  @Test
  void theParameterAllowsThemForThatIGAndIsRecorded() throws IOException {
    // what else is in the settings file stays
    Files.write(dir.resolve("fhir-settings.json"), "{ \"npm\" : { \"path\" : \"/usr/bin\" } }".getBytes(StandardCharsets.UTF_8));
    List<String> warnings = new ArrayList<>();
    ExternalLinkPermission.check(LINKS, ig("ig"), false, true, settings(), warnings::add);
    assertEquals(1, warnings.size());
    assertTrue(ExternalLinkPermission.isAllowed(settings(), ig("ig")));
    assertTrue(settingsContent().contains("/usr/bin"));
    // and next time, without the parameter, it goes on with a warning
    warnings.clear();
    ExternalLinkPermission.check(LINKS, ig("ig"), false, false, settings(), warnings::add);
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).contains("input/pagecontent/secret.md"));
    // but not for another IG
    assertFalse(ExternalLinkPermission.isAllowed(settings(), ig("other")));
    assertThrows(FHIRException.class, () -> ExternalLinkPermission.check(LINKS, ig("other"), false, false, settings(), s -> {}));
    // which can be allowed as well
    ExternalLinkPermission.check(LINKS, ig("other"), false, true, settings(), s -> {});
    assertTrue(ExternalLinkPermission.isAllowed(settings(), ig("ig")));
    assertTrue(ExternalLinkPermission.isAllowed(settings(), ig("other")));
  }

  @Test
  void theIGIsWhereItReallyIs() throws IOException {
    Path link = Files.createSymbolicLink(dir.resolve("ig-link"), Path.of(ig("ig")));
    ExternalLinkPermission.allow(settings(), link.toString());
    assertTrue(ExternalLinkPermission.isAllowed(settings(), ig("ig")));
    assertTrue(ExternalLinkPermission.isAllowed(settings(), ig("ig")+"/../ig"));
  }

  @Test
  void theOldAllowAllIsNotHonoured() throws IOException {
    Files.write(dir.resolve("fhir-settings.json"), ("{ \""+ExternalLinkPermission.PROPERTY+"\" : true }").getBytes(StandardCharsets.UTF_8));
    assertFalse(ExternalLinkPermission.isAllowed(settings(), ig("ig")));
    FHIRException e = assertThrows(FHIRException.class, () -> ExternalLinkPermission.check(LINKS, ig("ig"), false, false, settings(), s -> {}));
    assertTrue(e.getMessage().contains("isn't honoured"), e.getMessage());
    // allowing an IG replaces it
    ExternalLinkPermission.check(LINKS, ig("ig"), false, true, settings(), s -> {});
    assertTrue(ExternalLinkPermission.isAllowed(settings(), ig("ig")));
    assertFalse(settingsContent().contains("true"), settingsContent());
  }

  @Test
  void aSettingsFileThatCantBeReadIsNotWrittenOver() throws IOException {
    Files.write(dir.resolve("fhir-settings.json"), "{ not json".getBytes(StandardCharsets.UTF_8));
    assertThrows(FHIRException.class, () -> ExternalLinkPermission.check(LINKS, ig("ig"), false, true, settings(), s -> {}));
    assertEquals("{ not json", settingsContent());
  }
}
