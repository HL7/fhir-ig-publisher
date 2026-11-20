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
 * Links out of the IG's folder: refused on the ci-build; locally, the build stops unless the user has allowed them
 * (-allow-external-links, recorded in fhir-settings.json for all IGs), and then it goes on with a warning
 */
class ExternalLinkPermissionTest {

  @TempDir
  Path dir;

  private static final List<String> LINKS = List.of("input/pagecontent/secret.md");

  private String settings() {
    return dir.resolve("fhir-settings.json").toString();
  }

  @Test
  void noLinksIsFine() throws IOException {
    List<String> warnings = new ArrayList<>();
    ExternalLinkPermission.check(List.of(), "/ig", true, false, settings(), warnings::add);
    ExternalLinkPermission.check(List.of(), "/ig", false, false, settings(), warnings::add);
    assertTrue(warnings.isEmpty());
  }

  @Test
  void theCiBuildRefusesThemWhateverTheSettingsSay() throws IOException {
    ExternalLinkPermission.allow(settings());
    FHIRException e = assertThrows(FHIRException.class, () -> ExternalLinkPermission.check(LINKS, "/ig", true, true, settings(), s -> {}));
    assertTrue(e.getMessage().contains("input/pagecontent/secret.md"), e.getMessage());
  }

  @Test
  void locallyTheBuildStopsAndExplains() {
    FHIRException e = assertThrows(FHIRException.class, () -> ExternalLinkPermission.check(LINKS, "/ig", false, false, settings(), s -> {}));
    assertTrue(e.getMessage().contains(ExternalLinkPermission.PARAMETER), e.getMessage());
    assertFalse(Files.exists(dir.resolve("fhir-settings.json")));
  }

  @Test
  void theParameterAllowsThemAndIsRecorded() throws IOException {
    // what else is in the settings file stays
    Files.write(dir.resolve("fhir-settings.json"), "{ \"npm\" : { \"path\" : \"/usr/bin\" } }".getBytes(StandardCharsets.UTF_8));
    List<String> warnings = new ArrayList<>();
    ExternalLinkPermission.check(LINKS, "/ig", false, true, settings(), warnings::add);
    assertEquals(1, warnings.size());
    assertTrue(ExternalLinkPermission.isAllowed(settings()));
    assertTrue(new String(Files.readAllBytes(dir.resolve("fhir-settings.json")), StandardCharsets.UTF_8).contains("/usr/bin"));
    // and next time, without the parameter, it goes on with a warning
    warnings.clear();
    ExternalLinkPermission.check(LINKS, "/ig", false, false, settings(), warnings::add);
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).contains("input/pagecontent/secret.md"));
  }

  @Test
  void aSettingsFileThatCantBeReadIsNotWrittenOver() throws IOException {
    Files.write(dir.resolve("fhir-settings.json"), "{ not json".getBytes(StandardCharsets.UTF_8));
    assertThrows(FHIRException.class, () -> ExternalLinkPermission.check(LINKS, "/ig", false, true, settings(), s -> {}));
    assertEquals("{ not json", new String(Files.readAllBytes(dir.resolve("fhir-settings.json")), StandardCharsets.UTF_8));
  }
}
