package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.hl7.fhir.model.core.OperationOutcome;
import org.hl7.fhir.model.core.ValueSet;
import org.hl7.fhir.services.client.EFhirClientException;
import org.hl7.fhir.services.client.ITerminologyClientFactoryN;
import org.hl7.fhir.services.client.ITerminologyClientN;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A build in which the terminology server didn't answer (it was down, or a call timed out) made its expansions and
 * validation messages from those failures: the next build mustn't reuse them
 */
class RapidoTxFailuresTest {

  @TempDir
  Path dir;

  private PublisherFields pf;
  private FetchedFile igf;
  private ITerminologyClientN server;
  private ITerminologyClientN client;

  @BeforeEach
  void setUp() throws Exception {
    Path ig = dir.resolve("input").resolve("ig.json");
    Files.createDirectories(ig.getParent());
    Files.writeString(ig, "{}");
    pf = new PublisherFields();
    pf.rootDir = dir.toString();
    pf.tempDir = dir.resolve("temp").toString();
    pf.vsCache = dir.resolve("txcache").toString();
    pf.igpkp = mock(IGKnowledgeProvider.class);
    when(pf.igpkp.getCanonical()).thenReturn("http://example.org/ig");
    igf = new FetchedFile("input/ig.json");
    igf.setPath(ig.toString());
    igf.setSource("{}".getBytes(StandardCharsets.UTF_8));
    pf.fileList.add(igf);

    server = mock(ITerminologyClientN.class);
    ITerminologyClientFactoryN factory = mock(ITerminologyClientFactoryN.class);
    when(factory.makeClientN(any(), any(), any(), any(), any())).thenReturn(server);
    pf.txFailures = new RapidoTracker.TxFailures(factory);
    client = pf.txFailures.makeClientN(null, "tx-server", "http://tx.example.org", "fhir/publisher", null);
  }

  /** a build, then the one after it: what the second one can reuse from the first */
  private RapidoTracker buildAndNext(Runnable duringTheBuild) throws Exception {
    RapidoTracker first = new RapidoTracker(pf);
    first.analyse(igf, null);
    first.setUuid("the-last-build");
    duringTheBuild.run();
    first.save();
    RapidoTracker next = new RapidoTracker(pf);
    next.analyse(igf, null);
    return next;
  }

  private void expand() {
    client.expandValueset(new ValueSet(), null);
  }

  @Test
  void aCallThatGotNoAnswerIsNotReused() throws Exception {
    when(server.expandValueset(any(), any())).thenThrow(new EFhirClientException(new IOException("Read timed out")));
    RapidoTracker next = buildAndNext(() -> assertThrows(EFhirClientException.class, this::expand));
    assertEquals(1, pf.txFailures.count());
    assertTrue(next.isCompleteBuild());
    assertEquals("terminology server calls failed in the last build (1)", next.getReason());
  }

  @Test
  void aServerErrorIsNotReused() throws Exception {
    when(server.expandValueset(any(), any())).thenThrow(new EFhirClientException(503, "Service Unavailable", new OperationOutcome()));
    RapidoTracker next = buildAndNext(() -> assertThrows(EFhirClientException.class, this::expand));
    assertTrue(next.isCompleteBuild());
  }

  @Test
  void anErrorTheServerAnsweredWithIsReused() throws Exception {
    // e.g. a value set the server doesn't know: the same answer next time
    when(server.expandValueset(any(), any())).thenThrow(new EFhirClientException(404, "Unknown value set", new OperationOutcome()));
    RapidoTracker next = buildAndNext(() -> assertThrows(EFhirClientException.class, this::expand));
    assertEquals(0, pf.txFailures.count());
    assertFalse(next.isCompleteBuild(), next.getReason());
  }

  @Test
  void theClientIsUsedAsBefore() throws Exception {
    ValueSet vs = new ValueSet();
    when(server.expandValueset(any(), any())).thenReturn(vs);
    when(server.setRetryCount(anyInt())).thenReturn(server);
    assertSame(vs, client.expandValueset(new ValueSet(), null));
    // a setter that returns the client returns the counting one
    assertSame(client, client.setRetryCount(3));
    RapidoTracker next = buildAndNext(() -> { });
    assertFalse(next.isCompleteBuild(), next.getReason());
  }
}
