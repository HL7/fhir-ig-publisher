package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.PackageList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** In watch mode, each package-list.json that was found is fetched once per session; one that wasn't is tried again */
class PackageListFetcherTest {

  private static final String URL = "https://example.org/fhir/package-list.json";
  private final AtomicInteger requests = new AtomicInteger();
  private volatile boolean fails;
  private PackageListFetcher.Fetch original;

  @BeforeEach
  void serve() {
    original = PackageListFetcher.fetch;
    PackageListFetcher.fetch = url -> {
      requests.incrementAndGet();
      if (fails) {
        throw new IOException("unable to fetch " + url);
      }
      return JsonParser.parseObject("{\"package-id\":\"example.fhir\",\"canonical\":\"http://example.org/fhir\",\"list\":[]}");
    };
  }

  @AfterEach
  void stop() {
    PackageListFetcher.forgetFetches();
    PackageListFetcher.fetch = original;
  }

  @Test
  void eachListIsFetchedOncePerSession() throws IOException {
    PackageListFetcher.rememberFetches();
    PackageList first = PackageListFetcher.fromUrl(URL);
    first.setCanonical("changed by a caller");
    PackageList second = PackageListFetcher.fromUrl(URL);
    assertEquals(1, requests.get());
    assertEquals("http://example.org/fhir", second.canonical(), "each caller has its own copy");
    assertEquals(URL, second.source());

    PackageListFetcher.forgetFetches();
    PackageListFetcher.rememberFetches();
    PackageListFetcher.fromUrl(URL);
    assertEquals(2, requests.get(), "a new session fetches it again");
  }

  @Test
  void aListThatCouldNotBeFetchedIsTriedAgain() throws IOException {
    PackageListFetcher.rememberFetches();
    fails = true;
    assertThrows(IOException.class, () -> PackageListFetcher.fromUrl(URL));
    fails = false;
    assertEquals("example.fhir", PackageListFetcher.fromUrl(URL).pid());
    assertEquals(2, requests.get());
  }
}
