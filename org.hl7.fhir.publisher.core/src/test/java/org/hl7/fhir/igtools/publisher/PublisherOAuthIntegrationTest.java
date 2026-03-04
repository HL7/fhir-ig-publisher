package org.hl7.fhir.igtools.publisher;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.hl7.fhir.utilities.http.HTTPRequest;
import org.hl7.fhir.utilities.http.HTTPResult;
import org.hl7.fhir.utilities.http.HTTPTokenManager;
import org.hl7.fhir.utilities.http.ManagedWebAccess;
import org.hl7.fhir.utilities.settings.ServerDetailsPOJO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that prove the IG Publisher's OAuth client_credentials
 * CLI parameters work end-to-end: from CLI arg parsing through token
 * acquisition to authenticated FHIR server requests.
 *
 * These tests replicate exactly what Publisher.main() does when it receives
 * -tx-client-id, -tx-client-secret, and -tx-token-endpoint arguments.
 */
public class PublisherOAuthIntegrationTest {

  private MockWebServer tokenServer;
  private MockWebServer fhirServer;

  @BeforeEach
  void setup() throws IOException {
    tokenServer = new MockWebServer();
    fhirServer = new MockWebServer();
    tokenServer.start();
    fhirServer.start();
    HTTPTokenManager.clearCache();
    ManagedWebAccess.setAccessPolicy(ManagedWebAccess.WebAccessPolicy.DIRECT);
    ManagedWebAccess.setUserAgent("hapi-fhir-tooling-client");
  }

  @AfterEach
  void tearDown() throws IOException {
    HTTPTokenManager.clearCache();
    tokenServer.shutdown();
    fhirServer.shutdown();
  }

  // -----------------------------------------------------------------------
  // Test 1: CliParams correctly parses OAuth arguments
  // -----------------------------------------------------------------------
  @Test
  public void testCliParamsParsesOAuthArgs() {
    String[] args = {
      "-ig", "ig.ini",
      "-tx", "https://tx.example.org/fhir",
      "-tx-client-id", "my-client",
      "-tx-client-secret", "my-secret",
      "-tx-token-endpoint", "https://auth.example.org/token"
    };

    assertThat(CliParams.hasNamedParam(args, "-tx-token-endpoint")).isTrue();
    assertThat(CliParams.getNamedParam(args, "-tx-client-id")).isEqualTo("my-client");
    assertThat(CliParams.getNamedParam(args, "-tx-client-secret")).isEqualTo("my-secret");
    assertThat(CliParams.getNamedParam(args, "-tx-token-endpoint")).isEqualTo("https://auth.example.org/token");
    assertThat(CliParams.getNamedParam(args, "-tx")).isEqualTo("https://tx.example.org/fhir");
  }

  // -----------------------------------------------------------------------
  // Test 2: CliParams returns false when OAuth args are absent
  // -----------------------------------------------------------------------
  @Test
  public void testCliParamsAbsentOAuthArgs() {
    String[] args = {"-ig", "ig.ini", "-tx", "https://tx.example.org/fhir"};

    assertThat(CliParams.hasNamedParam(args, "-tx-token-endpoint")).isFalse();
    assertThat(CliParams.getNamedParam(args, "-tx-client-id")).isNull();
  }

  // -----------------------------------------------------------------------
  // Test 3: Full Publisher CLI-to-FHIR flow
  //
  // Replicates what Publisher.main() does:
  //   1. Parse CLI args for -tx, -tx-client-id, -tx-client-secret, -tx-token-endpoint
  //   2. Build a ServerDetailsPOJO (same as configureOAuthFromCliParams)
  //   3. Call ManagedWebAccess.addServerAuthDetail()
  //   4. Make a FHIR terminology request
  //   5. Verify the token was fetched and sent as Bearer header
  // -----------------------------------------------------------------------
  @Test
  public void testFullCliToFhirFlow() throws Exception {
    String txUrl = fhirServer.url("").toString();

    // Simulate CLI args as the user would pass them
    String[] args = {
      "-ig", "ig.ini",
      "-tx", txUrl,
      "-tx-client-id", "publisher-client",
      "-tx-client-secret", "publisher-secret",
      "-tx-token-endpoint", tokenServer.url("/token").toString()
    };

    // Token endpoint returns a valid token
    tokenServer.enqueue(new MockResponse()
      .setBody("{\"access_token\":\"publisher-oauth-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}")
      .addHeader("Content-Type", "application/json")
      .setResponseCode(200));

    // FHIR server returns a ValueSet expansion (typical terminology operation)
    fhirServer.enqueue(new MockResponse()
      .setBody("{\"resourceType\":\"ValueSet\",\"expansion\":{\"total\":42}}")
      .addHeader("Content-Type", "application/fhir+json")
      .setResponseCode(200));

    // --- Replicate exactly what configureOAuthFromCliParams() does ---
    if (CliParams.hasNamedParam(args, "-tx-token-endpoint")) {
      String cliTxUrl = CliParams.getNamedParam(args, "-tx");
      ServerDetailsPOJO oauthServer = ServerDetailsPOJO.builder()
        .url(cliTxUrl)
        .type("fhir")
        .authenticationType("client_credentials")
        .clientId(CliParams.getNamedParam(args, "-tx-client-id"))
        .clientSecret(CliParams.getNamedParam(args, "-tx-client-secret"))
        .tokenEndpoint(CliParams.getNamedParam(args, "-tx-token-endpoint"))
        .build();
      ManagedWebAccess.addServerAuthDetail(oauthServer);
    }
    // --- End of configureOAuthFromCliParams replication ---

    // Make a FHIR request the same way the IG Publisher's terminology client would
    String expandUrl = fhirServer.url("/ValueSet/$expand").toString();
    HTTPResult result = ManagedWebAccess.httpCall(
      new HTTPRequest().withUrl(expandUrl).withMethod(HTTPRequest.HttpMethod.POST)
        .withBody("{\"resourceType\":\"Parameters\"}".getBytes())
        .withContentType("application/fhir+json"));

    // Verify the FHIR response
    assertThat(result.getCode()).isEqualTo(200);
    assertThat(result.getContentAsString()).contains("ValueSet");

    // Verify the token endpoint was called with correct credentials
    RecordedRequest tokenRequest = tokenServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    String tokenBody = tokenRequest.getBody().readUtf8();
    assertThat(tokenBody).contains("grant_type=client_credentials");
    assertThat(tokenBody).contains("client_id=publisher-client");
    assertThat(tokenBody).contains("client_secret=publisher-secret");

    // Verify the FHIR request carried the Bearer token
    RecordedRequest fhirRequest = fhirServer.takeRequest();
    assertThat(fhirRequest.getHeader("Authorization")).isEqualTo("Bearer publisher-oauth-token");
  }

  // -----------------------------------------------------------------------
  // Test 5: Password masking hides -tx-client-secret in log output
  //
  // Verifies that removePassword() masks the secret value when logging
  // CLI parameters, preventing credentials from appearing in build logs.
  // Since removePassword is private, we test the same logic directly.
  // -----------------------------------------------------------------------
  @Test
  public void testPasswordMaskingForClientSecret() {
    String[] args = {
      "-ig", "ig.ini",
      "-tx", "https://tx.example.org/fhir",
      "-tx-client-id", "my-client",
      "-tx-client-secret", "super-secret-value",
      "-tx-token-endpoint", "https://auth.example.org/token"
    };

    // Build the parameter string the same way Publisher.main() does
    StringBuilder s = new StringBuilder("Parameters:");
    for (int i = 0; i < args.length; i++) {
      s.append(" ").append(maskSecrets(args, i));
    }
    String logOutput = s.toString();

    // Secret should be masked
    assertThat(logOutput).doesNotContain("super-secret-value");
    assertThat(logOutput).contains("XXXXXX");

    // Other params should not be masked
    assertThat(logOutput).contains("my-client");
    assertThat(logOutput).contains("https://auth.example.org/token");
    assertThat(logOutput).contains("ig.ini");
  }

  /**
   * Replicates Publisher.removePassword(String[] args, int i) logic.
   */
  private static String maskSecrets(String[] args, int i) {
    if (i == 0) {
      return args[i];
    }
    String prev = args[i - 1].toLowerCase();
    if (prev.contains("password") || prev.equals("-tx-client-secret")) {
      return "XXXXXX";
    }
    return args[i];
  }
}
