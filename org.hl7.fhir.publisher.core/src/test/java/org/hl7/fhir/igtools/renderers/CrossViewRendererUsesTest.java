package org.hl7.fhir.igtools.renderers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.CodeSystem;
import org.hl7.fhir.model.core.Resource;
import org.hl7.fhir.model.core.ValueSet;
import org.hl7.fhir.services.renderers.RendererFactory;
import org.hl7.fhir.services.renderers.utils.RenderingContext;
import org.hl7.fhir.standalone.context.SimpleWorkerContext;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.UserDataNames;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.validation.ValidationOptions;
import org.junit.jupiter.api.Test;

/**
 * The lists of code systems and value sets used by the IG name, for each, what uses it. package.db keeps one of those
 * per type and id, the first listed, so they're listed in the order they're found: by object identity they'd come in
 * an order that differs from one build to the next, and so would which of two value sets with the same id is kept
 */
class CrossViewRendererUsesTest {

  @Test
  void whatUsesACodeSystemIsListedInTheOrderItsFound() throws Exception {
    NpmPackage npm;
    try {
      npm = new FilesystemPackageCacheManager.Builder().build().loadPackage("hl7.fhir.r5.core", "5.0.0");
    } catch (Exception e) {
      npm = null;
    }
    assumeTrue(npm != null, "hl7.fhir.r5.core#5.0.0 can't be fetched");
    SimpleWorkerContext context = new SimpleWorkerContext.SimpleWorkerContextBuilder(ModelContext.fullCoreContext()).withAllowLoadingDuplicates(true).fromPackage(npm);
    CodeSystem cs = new CodeSystem();
    cs.setId("cs");
    cs.setUrl("http://example.org/CodeSystem/cs");
    context.cacheResource(cs);

    List<FetchedFile> files = new ArrayList<>();
    List<Resource> found = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      ValueSet vs = new ValueSet();
      vs.setId("vs" + i);
      vs.setUrl("http://example.org/ValueSet/vs" + i);
      vs.getCompose().addInclude().setSystem(cs.getUrl());
      FetchedResource r = mock(FetchedResource.class);
      when(r.getResource()).thenReturn(vs);
      FetchedFile f = new FetchedFile("ValueSet-vs" + i + ".json");
      f.getResources().add(r);
      files.add(f);
      found.add(vs);
    }
    RenderingContext rc = new RenderingContext(context, new RendererFactory(), new MarkDownProcessor(MarkDownProcessor.Dialect.COMMON_MARK),
        ValidationOptions.defaults(), "http://hl7.org/fhir/R5/", "", null, RenderingContext.ResourceRendererMode.TECHNICAL,
        RenderingContext.GenerationRules.IG_PUBLISHER);
    CrossViewRenderer cvr = new CrossViewRenderer("http://example.org", null, context, "http://hl7.org/fhir/R5/", rc);

    List<CodeSystem> used = cvr.buildUsedCodeSystemList(false, files);

    assertEquals(List.of(cs), used);
    assertEquals(found, new ArrayList<>((Set<Resource>) cs.getUserData(UserDataNames.pub_xref_used)));
  }

  @Test
  void whatUsesAValueSetIsListedInTheOrderItsFound() throws Exception {
    SimpleWorkerContext context = r5();
    ValueSet common = new ValueSet();
    common.setId("common");
    common.setUrl("http://example.org/ValueSet/common");
    context.cacheResource(common);

    List<FetchedFile> files = new ArrayList<>();
    List<Resource> found = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      ValueSet vs = new ValueSet();
      vs.setId("vs" + i);
      vs.setUrl("http://example.org/ValueSet/vs" + i);
      vs.getCompose().addInclude().addValueSet(common.getUrl());
      FetchedResource r = mock(FetchedResource.class);
      when(r.getResource()).thenReturn(vs);
      FetchedFile f = new FetchedFile("ValueSet-vs" + i + ".json");
      f.getResources().add(r);
      files.add(f);
      found.add(vs);
    }
    CrossViewRenderer cvr = new CrossViewRenderer("http://example.org", null, context, "http://hl7.org/fhir/R5/", rc(context));

    List<ValueSet> used = cvr.buildUsedValueSetList(false, files);

    assertTrue(used.contains(common));
    assertEquals(found, new ArrayList<>((Set<Resource>) common.getUserData(UserDataNames.pub_xref_used)));
  }

  private static SimpleWorkerContext r5() throws Exception {
    NpmPackage npm;
    try {
      npm = new FilesystemPackageCacheManager.Builder().build().loadPackage("hl7.fhir.r5.core", "5.0.0");
    } catch (Exception e) {
      npm = null;
    }
    assumeTrue(npm != null, "hl7.fhir.r5.core#5.0.0 can't be fetched");
    return new SimpleWorkerContext.SimpleWorkerContextBuilder(ModelContext.fullCoreContext()).withAllowLoadingDuplicates(true).fromPackage(npm);
  }

  private static RenderingContext rc(SimpleWorkerContext context) {
    return new RenderingContext(context, new RendererFactory(), new MarkDownProcessor(MarkDownProcessor.Dialect.COMMON_MARK),
        ValidationOptions.defaults(), "http://hl7.org/fhir/R5/", "", null, RenderingContext.ResourceRendererMode.TECHNICAL,
        RenderingContext.GenerationRules.IG_PUBLISHER);
  }
}
