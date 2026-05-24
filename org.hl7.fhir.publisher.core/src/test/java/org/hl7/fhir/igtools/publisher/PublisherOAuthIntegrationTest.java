package org.hl7.fhir.igtools.publisher;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.hl7.fhir.utilities.http.HTTPRequest;
import org.hl7.fhir.utilities.http.HTTPResult;
import org.hl7.fhir.utilities.http.HTTPTokenManager;
import org.hl7.fhir.utilities.http.ManagedWebAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests that prove the IG Publisher's OAuth client_credentials
 * CLI parameters work end-to-end: from CLI arg parsing through token
 * acquisition to authenticated FHIR server requests.
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
    ManagedWebAccess.loadFromFHIRSettings();
    ManagedWebAccess.setAccessPolicy(ManagedWebAccess.WebAccessPolicy.DIRECT);
    ManagedWebAccess.setUserAgent("hapi-fhir-tooling-client");
  }

  @AfterEach
  void tearDown() throws IOException {
    HTTPTokenManager.clearCache();
    ManagedWebAccess.loadFromFHIRSettings();
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
  // Calls the actual configureOAuthFromCliParams() method, then makes a
  // FHIR request and verifies the token was fetched and sent as Bearer header.
  // -----------------------------------------------------------------------
  @Test
  public void testFullCliToFhirFlow() throws Exception {
    String txUrl = fhirServer.url("").toString();

    String[] args = {
      "-ig", "ig.ini",
      "-tx", txUrl,
      "-tx-client-id", "publisher-client",
      "-tx-client-secret", "publisher-secret",
      "-tx-token-endpoint", tokenServer.url("/token").toString()
    };

    tokenServer.enqueue(new MockResponse()
      .setBody("{\"access_token\":\"publisher-oauth-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}")
      .addHeader("Content-Type", "application/json")
      .setResponseCode(200));

    fhirServer.enqueue(new MockResponse()
      .setBody("{\"resourceType\":\"ValueSet\",\"expansion\":{\"total\":42}}")
      .addHeader("Content-Type", "application/fhir+json")
      .setResponseCode(200));

    Publisher.configureOAuthFromCliParams(args);

    String expandUrl = fhirServer.url("/ValueSet/$expand").toString();
    HTTPResult result = ManagedWebAccess.httpCall(
      new HTTPRequest().withUrl(expandUrl).withMethod(HTTPRequest.HttpMethod.POST)
        .withBody("{\"resourceType\":\"Parameters\"}".getBytes())
        .withContentType("application/fhir+json"));

    assertThat(result.getCode()).isEqualTo(200);
    assertThat(result.getContentAsString()).contains("ValueSet");

    RecordedRequest tokenRequest = tokenServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    String tokenBody = tokenRequest.getBody().readUtf8();
    assertThat(tokenBody).contains("grant_type=client_credentials");
    assertThat(tokenBody).contains("client_id=publisher-client");
    assertThat(tokenBody).contains("client_secret=publisher-secret");

    RecordedRequest fhirRequest = fhirServer.takeRequest();
    assertThat(fhirRequest.getHeader("Authorization")).isEqualTo("Bearer publisher-oauth-token");
  }

  // -----------------------------------------------------------------------
  // Test 4: Password masking via actual removePassword method
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

    StringBuilder s = new StringBuilder("Parameters:");
    for (int i = 0; i < args.length; i++) {
      s.append(" ").append(Publisher.removePassword(args, i));
    }
    String logOutput = s.toString();

    assertThat(logOutput).doesNotContain("super-secret-value");
    assertThat(logOutput).contains("XXXXXX");

    // Other params should not be masked
    assertThat(logOutput).contains("my-client");
    assertThat(logOutput).contains("https://auth.example.org/token");
    assertThat(logOutput).contains("ig.ini");
  }

  // -----------------------------------------------------------------------
  // Test 5: Missing -tx-client-id throws IllegalArgumentException
  // -----------------------------------------------------------------------
  @Test
  public void testMissingClientIdThrowsException() {
    String[] args = {
      "-ig", "ig.ini",
      "-tx", "https://tx.example.org/fhir",
      "-tx-client-secret", "my-secret",
      "-tx-token-endpoint", "https://auth.example.org/token"
    };

    assertThatThrownBy(() -> Publisher.configureOAuthFromCliParams(args))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("-tx-client-id");
  }

  // -----------------------------------------------------------------------
  // Test 6: Missing -tx-client-secret throws IllegalArgumentException
  // -----------------------------------------------------------------------
  @Test
  public void testMissingClientSecretThrowsException() {
    String[] args = {
      "-ig", "ig.ini",
      "-tx", "https://tx.example.org/fhir",
      "-tx-client-id", "my-client",
      "-tx-token-endpoint", "https://auth.example.org/token"
    };

    assertThatThrownBy(() -> Publisher.configureOAuthFromCliParams(args))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("-tx-client-secret");
  }

  // -----------------------------------------------------------------------
  // Test 7: Flag value that looks like another flag throws exception
  // -----------------------------------------------------------------------
  @Test
  public void testFlagAsValueThrowsException() {
    String[] args = {
      "-ig", "ig.ini",
      "-tx", "https://tx.example.org/fhir",
      "-tx-client-id", "-tx-client-secret",
      "-tx-client-secret", "my-secret",
      "-tx-token-endpoint", "https://auth.example.org/token"
    };

    assertThatThrownBy(() -> Publisher.configureOAuthFromCliParams(args))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("looks like a flag name");
  }

  // -----------------------------------------------------------------------
  // Test 8: No OAuth args means configureOAuthFromCliParams is a no-op
  // -----------------------------------------------------------------------
  @Test
  public void testNoOAuthArgsIsNoOp() {
    String[] args = {"-ig", "ig.ini", "-tx", "https://tx.example.org/fhir"};

    Publisher.configureOAuthFromCliParams(args);
  }

  // -----------------------------------------------------------------------
  // Test 9: -tx-token-endpoint as last arg with no value throws exception
  // -----------------------------------------------------------------------
  @Test
  public void testTokenEndpointWithNoValueThrowsException() {
    String[] args = {
      "-ig", "ig.ini",
      "-tx", "https://tx.example.org/fhir",
      "-tx-client-id", "my-client",
      "-tx-client-secret", "my-secret",
      "-tx-token-endpoint"
    };

    assertThatThrownBy(() -> Publisher.configureOAuthFromCliParams(args))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("-tx-token-endpoint");
  }

  // -----------------------------------------------------------------------
  // Test 10: OAuth without -tx falls back to default TX server
  // -----------------------------------------------------------------------
  @Test
  public void testOAuthWithoutExplicitTxUsesDefault() {
    String[] args = {
      "-ig", "ig.ini",
      "-tx-client-id", "my-client",
      "-tx-client-secret", "my-secret",
      "-tx-token-endpoint", "https://auth.example.org/token"
    };

    // Should not throw -- falls back to FhirSettings.getTxFhirProduction()
    Publisher.configureOAuthFromCliParams(args);
  }

  // -----------------------------------------------------------------------
  // Test 11: Whitespace-only client-id is rejected
  // -----------------------------------------------------------------------
  @Test
  public void testWhitespaceOnlyClientIdThrowsException() {
    String[] args = {
      "-tx-client-id", "  ",
      "-tx-client-secret", "my-secret",
      "-tx-token-endpoint", "https://auth.example.org/token"
    };

    assertThatThrownBy(() -> Publisher.configureOAuthFromCliParams(args))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("-tx-client-id");
  }

  // -----------------------------------------------------------------------
  // Test 13: Malformed token endpoint URL is rejected
  // -----------------------------------------------------------------------
  @Test
  public void testMalformedTokenEndpointThrowsException() {
    String[] args = {
      "-tx", "https://tx.example.org/fhir",
      "-tx-client-id", "my-client",
      "-tx-client-secret", "my-secret",
      "-tx-token-endpoint", "not-a-url"
    };

    assertThatThrownBy(() -> Publisher.configureOAuthFromCliParams(args))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("-tx-token-endpoint");
  }

  // -----------------------------------------------------------------------
  // Test 14: FTP token endpoint URL is rejected
  // -----------------------------------------------------------------------
  @Test
  public void testNonHttpTokenEndpointThrowsException() {
    String[] args = {
      "-tx", "https://tx.example.org/fhir",
      "-tx-client-id", "my-client",
      "-tx-client-secret", "my-secret",
      "-tx-token-endpoint", "ftp://auth.example.org/token"
    };

    assertThatThrownBy(() -> Publisher.configureOAuthFromCliParams(args))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("HTTP(S) URL");
  }
}
