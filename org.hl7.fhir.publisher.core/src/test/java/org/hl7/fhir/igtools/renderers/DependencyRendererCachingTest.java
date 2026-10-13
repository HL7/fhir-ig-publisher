package org.hl7.fhir.igtools.renderers;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.model.core.ImplementationGuide;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.npm.BasePackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * DependencyRenderer meets the same packages many times in a dependency tree. It keeps the packages it
 * has loaded and remembers which packages' guides it has checked, but only where loading again could
 * not give a different answer.
 */
class DependencyRendererCachingTest {

  private static final String IG_JSON = "{\"resourceType\":\"ImplementationGuide\",\"url\":\"http://example.org/ImplementationGuide/x\",\"version\":\"1.0.0\","
      + "\"name\":\"X\",\"status\":\"active\",\"packageId\":\"example.x\",\"fhirVersion\":[\"4.0.1\"]}";

  private static DependencyRenderer renderer(BasePackageCacheManager pcm) {
    return new DependencyRenderer(pcm, null, "example.this", null, null, null, null, null, null, new ImplementationGuide());
  }

  private static BasePackageCacheManager freshPackageEachTime() throws IOException {
    BasePackageCacheManager pcm = mock(BasePackageCacheManager.class);
    when(pcm.loadPackage(anyString(), any())).thenAnswer(inv -> mock(NpmPackage.class));
    return pcm;
  }

  @Test
  void fixedVersionIsLoadedOnce() throws IOException {
    BasePackageCacheManager pcm = freshPackageEachTime();
    DependencyRenderer r = renderer(pcm);
    NpmPackage first = r.resolve("example.dep", "1.0.0");
    assertSame(first, r.resolve("example.dep", "1.0.0"));
    verify(pcm, times(1)).loadPackage("example.dep", "1.0.0");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"current", "dev", "current$branch", "1.0.x", "1.0"})
  void otherVersionsAreLoadedEachTime(String version) throws IOException {
    BasePackageCacheManager pcm = freshPackageEachTime();
    DependencyRenderer r = renderer(pcm);
    NpmPackage first = r.resolve("example.dep", version);
    assertNotSame(first, r.resolve("example.dep", version));
    verify(pcm, times(2)).loadPackage("example.dep", version);
  }

  @Test
  void corePackagesAreKeptByTheirMappedVersion() throws IOException {
    BasePackageCacheManager pcm = freshPackageEachTime();
    DependencyRenderer r = renderer(pcm);
    String mapped = VersionUtilities.getCurrentVersion("4.0");
    r.resolve("hl7.fhir.r4.core", "4.0");
    r.resolve("hl7.fhir.r4.core", mapped);
    verify(pcm, times(1)).loadPackage("hl7.fhir.r4.core", mapped);
  }

  @Test
  void failuresAreNotRemembered() throws IOException {
    BasePackageCacheManager pcm = mock(BasePackageCacheManager.class);
    when(pcm.loadPackage(anyString(), any())).thenThrow(new FHIRException("not found"));
    DependencyRenderer r = renderer(pcm);
    assertThrows(FHIRException.class, () -> r.resolve("example.missing", "1.0.0"));
    assertThrows(FHIRException.class, () -> r.resolve("example.missing", "1.0.0"));
    verify(pcm, times(2)).loadPackage("example.missing", "1.0.0");
  }

  private static NpmPackage packageWithGuide() throws IOException {
    NpmPackage npm = mock(NpmPackage.class);
    when(npm.listResources("ImplementationGuide")).thenReturn(List.of("ImplementationGuide-x.json"));
    when(npm.loadResource("ImplementationGuide-x.json")).thenAnswer(inv -> new ByteArrayInputStream(IG_JSON.getBytes(StandardCharsets.UTF_8)));
    when(npm.fhirVersion()).thenReturn("4.0.1");
    when(npm.vid()).thenReturn("example.dep#1.0.0");
    when(npm.version()).thenReturn("1.0.0");
    return npm;
  }

  @Test
  void aPackagesGuidesAreCheckedOnce() throws IOException {
    DependencyRenderer r = renderer(mock(BasePackageCacheManager.class));
    NpmPackage npm = packageWithGuide();
    r.checkGlobals(npm);
    r.checkGlobals(npm);
    verify(npm, times(1)).loadResource("ImplementationGuide-x.json");
  }

  @Test
  void aReloadedPackageIsCheckedAgain() throws IOException {
    // what resolve() returns for current or dev each time: a new package, maybe a newer build
    DependencyRenderer r = renderer(mock(BasePackageCacheManager.class));
    NpmPackage first = packageWithGuide();
    NpmPackage reloaded = packageWithGuide();
    r.checkGlobals(first);
    r.checkGlobals(reloaded);
    verify(first, times(1)).loadResource("ImplementationGuide-x.json");
    verify(reloaded, times(1)).loadResource("ImplementationGuide-x.json");
  }

  @Test
  void aGuideThatFailsToLoadIsTriedAgain() throws IOException {
    DependencyRenderer r = renderer(mock(BasePackageCacheManager.class));
    NpmPackage npm = packageWithGuide();
    when(npm.loadResource("ImplementationGuide-x.json")).thenThrow(new IOException("unreadable"));
    assertThrows(IOException.class, () -> r.checkGlobals(npm));
    assertThrows(IOException.class, () -> r.checkGlobals(npm));
    verify(npm, times(2)).loadResource("ImplementationGuide-x.json");
  }
}
