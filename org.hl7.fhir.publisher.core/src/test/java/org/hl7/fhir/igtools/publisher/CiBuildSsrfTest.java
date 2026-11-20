package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hl7.fhir.utilities.http.ManagedWebAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** the ci-build builds IGs that anyone can write: SSRF protection is on there, whatever turned it off */
class CiBuildSsrfTest {

  @AfterEach
  void restore() {
    ManagedWebAccess.setSsrfProtectionEnabled(true);
  }

  @Test
  void theCiBuildTurnsItBackOn() {
    ManagedWebAccess.setSsrfProtectionEnabled(false);
    Publisher.forceSsrfProtectionOnCiBuild(new String[] {"-ig", "x", "-auto-ig-build", "-ssrf-protection-enabled", "false"});
    assertTrue(ManagedWebAccess.isSsrfProtectionEnabled());
  }

  @Test
  void otherwiseTheSettingStands() {
    ManagedWebAccess.setSsrfProtectionEnabled(false);
    Publisher.forceSsrfProtectionOnCiBuild(new String[] {"-ig", "x", "-ssrf-protection-enabled", "false"});
    assertFalse(ManagedWebAccess.isSsrfProtectionEnabled());
  }
}
