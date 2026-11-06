package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.igtools.templates.Template;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What a page-only rebuild writes to the temp folder for a page in a translated IG: the page in each language, from
 * its translation if it has one, as the build does
 */
class RegeneratePagesTest {

  @TempDir
  Path dir;

  private PublisherFields pf;
  private PublisherGenerator gen;
  private FetchedFile page;
  private final List<String> log = new ArrayList<>();

  private void write(String path, String content) throws IOException {
    Path p = dir.resolve(path);
    Files.createDirectories(p.getParent());
    Files.write(p, content.getBytes(StandardCharsets.UTF_8));
  }

  private String read(String path) throws IOException {
    return new String(Files.readAllBytes(dir.resolve(path)), StandardCharsets.UTF_8);
  }

  private String temp(String path) {
    return dir.resolve("temp").resolve(path).toString();
  }

  /** the files the build wrote for the page */
  private List<String> outputs() {
    return Arrays.asList(temp("_includes/en/index.md"), temp("_includes/ru/index.md"), temp("_includes/uz/index.md"),
        temp("_includes/index.md"));
  }

  @BeforeEach
  void setUp() throws IOException {
    write("input/pagecontent/index.md", "# Home");
    write("input/translations/ru/pagecontent/index.md", "# Главная");
    PublisherSettings settings = new PublisherSettings();
    settings.setNewMultiLangTemplateFormat(true);
    gen = new PublisherGenerator(settings);
    pf = new PublisherFields();
    pf.rootDir = dir.toString();
    pf.tempDir = dir.resolve("temp").toString();
    pf.outputDir = dir.resolve("output").toString();
    Files.createDirectories(dir.resolve("output"));
    pf.logger = new ILoggingService() {
      public void logMessage(String message) {
        log.add(message);
      }

      public void logDebugMessage(LogCategory category, String message) {
      }
    };
    pf.defaultTranslationLang = "en";
    pf.translationLangs.addAll(Arrays.asList("ru", "uz"));
    pf.translationSources.addAll(Arrays.asList("input/translations/ru", "input/translations/uz"));
    gen.setPf(pf);
    page = new FetchedFile("input/pagecontent/index.md", "pagecontent/index.md");
    page.setPath(dir.resolve("input/pagecontent/index.md").toString());
    page.setRelativePath("_includes/index.md");
    page.setProcessMode(FetchedFile.PROCESS_NONE);
    // what the build recorded when it wrote the page (uz has no translation, so it's the English page)
    for (String s : outputs()) {
      pf.allOutputs.add(s.toLowerCase());
      page.getOutputNames().add(s);
    }
    write("temp/_includes/en/index.md", "# Home");
    write("temp/_includes/ru/index.md", "# Главная");
    write("temp/_includes/uz/index.md", "# Home");
    write("temp/_includes/index.md", "# Home");
  }

  private List<String> regenerate() throws Exception {
    List<String> res = gen.regeneratePages(Collections.singletonList(page));
    assertEquals(4, pf.allOutputs.size());
    return res;
  }

  /** the temp folder: each file by its path, with its content */
  private java.util.Map<String, String> tempTree() throws IOException {
    java.util.Map<String, String> res = new java.util.TreeMap<>();
    try (java.util.stream.Stream<Path> files = Files.walk(dir.resolve("temp"))) {
      for (Path p : (Iterable<Path>) files::iterator) {
        if (Files.isRegularFile(p)) {
          res.put(dir.resolve("temp").relativize(p).toString(), new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
        }
      }
    }
    return res;
  }

  /** writes the page to an empty temp folder as the build does, and records what it wrote as the build does */
  private void build() throws Exception {
    try (java.util.stream.Stream<Path> files = Files.walk(dir.resolve("temp"))) {
      for (Path p : (Iterable<Path>) files.sorted(java.util.Comparator.reverseOrder())::iterator) {
        Files.delete(p);
      }
    }
    page.getOutputNames().clear();
    pf.allOutputs.clear();
    page.setSource(Files.readAllBytes(dir.resolve("input/pagecontent/index.md")));
    // with the database the build makes, if it makes one
    org.hl7.fhir.igtools.renderers.DBBuilder db = pf.generatingDatabase
        ? new org.hl7.fhir.igtools.renderers.DBBuilder(dir.resolve("package.db").toString(), null, null, null, pf.fileList) : null;
    gen.generateHtmlOutputs(page, false, db, null);
    if (db != null) {
      db.closeUp();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void theSameAsTheBuildForAPageWithTwoOutputs(boolean database) throws Exception {
    // a page in a folder that is both a pre-process folder and a pages folder: one output under _includes, one in the
    // root, each in every language; with or without the database, which changes how a lang-fragment is written
    pf.generatingDatabase = database;
    page.addOutput("index.md", FetchedFile.PROCESS_NONE, null);
    build();
    assertEquals(8, tempTree().size(), tempTree().toString());
    String[][] edits = {{"input/pagecontent/index.md", "# Home, edited"}, {"input/translations/ru/pagecontent/index.md", "# Главная страница"},
        {"input/pagecontent/index.md", "# Home\n\n{% lang-fragment ip-statements.xhtml %}"}};
    int[] changes = {6, 2, 6};
    for (int e = 0; e < edits.length; e++) {
      java.util.Map<String, String> before = tempTree();
      write(edits[e][0], edits[e][1]);
      List<String> said = new ArrayList<>();
      for (String f : gen.regeneratePages(Collections.singletonList(page))) {
        said.add(dir.resolve("temp").relativize(Path.of(f)).toString());
      }
      java.util.Map<String, String> rebuilt = tempTree();
      build();
      java.util.Map<String, String> built = tempTree();
      assertEquals(built, rebuilt, edits[e][0]);
      // and it says which files changed
      List<String> changed = new ArrayList<>();
      for (String f : built.keySet()) {
        if (!built.get(f).equals(before.get(f))) {
          changed.add(f);
        }
      }
      Collections.sort(said);
      assertEquals(changed, said, edits[e][0]);
      assertEquals(changes[e], changed.size(), edits[e][0]);
    }
    assertEquals(database ? "# Home\n\n{% include ip-statements-en.xhtml %}" : edits[2][1], read("temp/_includes/index.md"));
  }

  @Test
  void anEditToTheEnglishPage() throws Exception {
    write("input/pagecontent/index.md", "# Home, edited");
    // and the page without a translation in uz
    assertEquals(Arrays.asList(temp("_includes/en/index.md"), temp("_includes/uz/index.md"), temp("_includes/index.md")), regenerate());
    assertEquals("# Home, edited", read("temp/_includes/en/index.md"));
    assertEquals("# Главная", read("temp/_includes/ru/index.md"));
    assertEquals("# Home, edited", read("temp/_includes/uz/index.md"));
    assertEquals("# Home, edited", read("temp/_includes/index.md"));
  }

  @Test
  void anEditToATranslation() throws Exception {
    write("input/translations/ru/pagecontent/index.md", "# Главная страница");
    assertEquals(Collections.singletonList(temp("_includes/ru/index.md")), regenerate());
    assertEquals("# Главная страница", read("temp/_includes/ru/index.md"));
    assertEquals("# Home", read("temp/_includes/en/index.md"));
  }

  @Test
  void theFrontMatterIsTakenOut() throws Exception {
    write("input/translations/ru/pagecontent/index.md", "---\ntitle: x\n---\n# Главная страница");
    regenerate();
    assertEquals("\n# Главная страница", read("temp/_includes/ru/index.md"));
  }

  @Test
  void aLangFragmentIsProcessedAsInTheBuild() throws Exception {
    // the build processes it while it builds the database, and then with the language of the page, which is none, so
    // each language gets the English fragment: that is what the build does now, not what it should do
    write("input/pagecontent/index.md", "{% lang-fragment ip-statements.xhtml %}");
    regenerate();
    assertEquals("{% include ip-statements-en.xhtml %}", read("temp/_includes/uz/index.md"));
    assertEquals("{% include ip-statements-en.xhtml %}", read("temp/_includes/index.md"));
    assertEquals(Collections.emptySet(), pf.pagesWithCustomContent);

    pf.generatingDatabase = false;
    write("input/pagecontent/index.md", "{% lang-fragment ip-statements.xhtml %} ");
    regenerate();
    assertEquals("{% lang-fragment ip-statements.xhtml %} ", read("temp/_includes/index.md"));
  }

  @Test
  void notIfTheBuildWroteThePageElsewhere() {
    page.getOutputNames().remove(temp("_includes/uz/index.md"));
    assertThrows(IOException.class, this::regenerate);
  }

  @Test
  void notIfTheBuildDidntRecordItsOutput() {
    pf.allOutputs.remove(temp("_includes/ru/index.md").toLowerCase());
    assertThrows(IOException.class, this::regenerate);
  }

  @Test
  void notIfThePageCantBeWritten() throws Exception {
    // the last file the page is written to
    Files.delete(dir.resolve("temp/_includes/index.md"));
    Files.createDirectories(dir.resolve("temp/_includes/index.md"));
    write("input/pagecontent/index.md", "# Home, edited");
    IOException e = assertThrows(IOException.class, () -> gen.regeneratePages(Collections.singletonList(page)));
    assertTrue(e.getMessage().contains("could not be made into " + temp("_includes/index.md")), e.getMessage());
    assertTrue(log.get(0).startsWith("Exception generating page " + temp("_includes/index.md")), log.toString());
  }

  @Test
  void thePagesThePageIsWrappedIn() throws Exception {
    // the build wraps the page in a page in each language (see applyPageTemplate()), and notes them as the page's
    for (String s : Arrays.asList("index.html", "en/index.html", "ru/index.html", "uz/index.html")) {
      write("temp/" + s, "{% include template-page-md.html %}");
      page.getOutputNames().add(temp(s));
    }
    write("input/pagecontent/index.md", "# Home, edited");
    assertEquals(Arrays.asList(temp("_includes/en/index.md"), temp("_includes/uz/index.md"), temp("_includes/index.md")),
        gen.regeneratePages(Collections.singletonList(page)));
    assertEquals("{% include template-page-md.html %}", read("temp/ru/index.html"));
    // anything else it was written to is not known
    page.getOutputNames().add(temp("other.html"));
    IOException e = assertThrows(IOException.class, () -> gen.regeneratePages(Collections.singletonList(page)));
    assertTrue(e.getMessage().endsWith("was also written to [" + temp("other.html") + "]"), e.getMessage());
  }

  @Test
  void aPageThatIsntAnInclude() throws Exception {
    // written to the language's folder (as the pages of a path-pages folder are)
    page.setRelativePath("index.md");
    pf.allOutputs.clear();
    page.getOutputNames().clear();
    for (String s : Arrays.asList(temp("en/index.md"), temp("ru/index.md"), temp("uz/index.md"), temp("index.md"))) {
      pf.allOutputs.add(s.toLowerCase());
      page.getOutputNames().add(s);
    }
    write("temp/en/index.md", "# Home");
    write("temp/ru/index.md", "# Главная");
    write("temp/uz/index.md", "# Home");
    write("temp/index.md", "# Home");
    write("input/translations/ru/pagecontent/index.md", "# Главная страница");
    assertEquals(Collections.singletonList(temp("ru/index.md")), regenerate());
    assertEquals("# Главная страница", read("temp/ru/index.md"));
  }

  @Test
  void anEditToATranslationThroughTheRebuild() throws Exception {
    // what the watch session does with it: find the page, write it again, and put the temp folder back for Jekyll
    page.setPath(dir.resolve("input/pagecontent/index.md").toFile().getCanonicalPath());
    pf.altMap.put("page/" + page.getPath(), page);
    pf.fileList.add(page);
    PageRebuild state = new PageRebuild();
    state.setPf(pf);
    state.setPageLanguages(Arrays.asList("ru", "uz"));
    try {
      state.beforeJekyll();
      state.afterJekyll();
      state.afterPackage();
      state.finished();
      List<Path> roots = Collections.singletonList(dir.resolve("input"));
      SourceSnapshot before = SourceSnapshot.take(roots, null);
      write("input/translations/ru/pagecontent/index.md", "# Главная страница");
      List<String> reason = new ArrayList<>();
      List<FetchedFile> pages = state.pagesFor(SourceSnapshot.take(roots, before).changesSince(before), reason);
      assertEquals(Collections.singletonList(page), pages, reason.toString());
      List<String> written = gen.regeneratePages(pages);
      assertEquals(Collections.singletonList(temp("_includes/ru/index.md")), written);
      // the other files the page is written to are as they were
      state.prepareTemp(written);
      assertEquals("# Главная страница", read("temp/_includes/ru/index.md"));
    } finally {
      state.discard();
    }
  }

  /** the rebuild state that the build sets up, from what it did */
  private PageRebuild checked() throws IOException {
    PageRebuild state = new PageRebuild();
    state.setPf(pf);
    gen.setPageRebuild(state);
    pf.changeList.add(page);
    gen.checkPageRebuild();
    return state;
  }

  @Test
  void theBuildTellsTheRebuildTheLanguagesOfThePages() throws Exception {
    page.setPath(dir.resolve("input/pagecontent/index.md").toFile().getCanonicalPath());
    pf.altMap.put("page/" + page.getPath(), page);
    pf.fileList.add(page);
    PageRebuild state = checked();
    try {
      state.beforeJekyll();
      state.afterJekyll();
      state.afterPackage();
      state.finished();
      assertEquals(null, state.getNotReusable());
      List<Path> roots = Collections.singletonList(dir.resolve("input"));
      SourceSnapshot before = SourceSnapshot.take(roots, null);
      write("input/translations/ru/pagecontent/index.md", "# Главная страница");
      List<String> reason = new ArrayList<>();
      assertEquals(Collections.singletonList(page), state.pagesFor(SourceSnapshot.take(roots, before).changesSince(before), reason), reason.toString());
    } finally {
      state.discard();
    }
  }

  /** a template whose onGenerate script reads the menu in the includes folder */
  private Template template() throws IOException {
    write("template/config.json", "{\"script\": \"scripts/ant.xml\", \"targets\": {\"onGenerate\": \"onGenerate\"}}");
    write("template/scripts/ant.xml", "<project name=\"t\"><target name=\"onGenerate\">"
        + "<copy file=\"${dir.input}/includes/menu.xml\" todir=\"x\"/></target></project>");
    return new Template(dir.toString(), true, null, null, false, false, false);
  }

  @Test
  void theBuildTellsTheRebuildWhatTheTemplateReads() throws Exception {
    pf.template = template();
    FetchedFile menu = new FetchedFile("input/includes/menu.xml", "includes/menu.xml");
    write("input/includes/menu.xml", "<ul/>");
    write("input/translations/ru/includes/menu.xml", "<ul><li>Меню</li></ul>");
    menu.setPath(dir.resolve("input/includes/menu.xml").toFile().getCanonicalPath());
    menu.setRelativePath("_includes/menu.xml");
    menu.setProcessMode(FetchedFile.PROCESS_NONE);
    pf.altMap.put("pre-page/" + menu.getPath(), menu);
    page.setPath(dir.resolve("input/pagecontent/index.md").toFile().getCanonicalPath());
    pf.altMap.put("page/" + page.getPath(), page);
    pf.fileList.addAll(Arrays.asList(page, menu));
    PageRebuild state = checked();
    try {
      state.beforeJekyll();
      state.afterJekyll();
      state.afterPackage();
      state.finished();
      assertEquals(null, state.getNotReusable());
      List<Path> roots = Collections.singletonList(dir.resolve("input"));
      for (String edit : new String[] {"input/includes/menu.xml", "input/translations/ru/includes/menu.xml"}) {
        SourceSnapshot before = SourceSnapshot.take(roots, null);
        write(edit, "<ul><li>2</li></ul>");
        List<String> reason = new ArrayList<>();
        assertEquals(null, state.pagesFor(SourceSnapshot.take(roots, before).changesSince(before), reason), edit);
        assertTrue(reason.get(0).endsWith("the template's script scripts/ant.xml reads (${dir.input}/includes)"), reason.get(0));
      }
      SourceSnapshot before = SourceSnapshot.take(roots, null);
      write("input/translations/ru/pagecontent/index.md", "# Главная страница");
      List<String> reason = new ArrayList<>();
      assertEquals(Collections.singletonList(page), state.pagesFor(SourceSnapshot.take(roots, before).changesSince(before), reason), reason.toString());
    } finally {
      state.discard();
    }
  }

  @Test
  void notForTranslationsOrPagesThatArentWatched() throws Exception {
    // the folders that the base template adds pages from: the template's (which the build makes), and the images
    pf.pagesDirs.addAll(Arrays.asList(dir.resolve("template/config").toString(), dir.resolve("input/images").toString()));
    PageRebuild state = checked();
    try {
      state.beforeJekyll();
      state.afterJekyll();
      state.afterPackage();
      state.finished();
      assertEquals(null, state.getNotReusable());
    } finally {
      state.discard();
    }
    pf.changeList.clear();
    pf.translationSources.set(0, "translations/ru");
    assertEquals("the translations in translations/ru are outside the folders that are watched", checked().getNotReusable());
    pf.translationSources.set(0, "input/translations/ru");
    pf.pagesDirs.add(dir.resolve("pages").toString());
    pf.changeList.clear();
    assertEquals("the pages in " + dir.resolve("pages") + " are outside the folders that are watched", checked().getNotReusable());
    pf.pagesDirs.remove(dir.resolve("pages").toString());
    pf.prePagesDirs.add(dir.resolve("temp/pre").toString());
    pf.changeList.clear();
    assertEquals("the pages in " + dir.resolve("temp/pre") + " are outside the folders that are watched", checked().getNotReusable());
    pf.prePagesDirs.clear();
    write("input/factory.json", "{}");
    pf.pageFactories.add(new PageFactory(dir.resolve("input/factory.json").toString(), dir.resolve("temp/factory-pages/factory0").toString()));
    pf.changeList.clear();
    assertEquals("the IG has page factories", checked().getNotReusable());
  }

  @Test
  void notForAnIgTranslatedTheOldWay() throws Exception {
    gen = new PublisherGenerator(new PublisherSettings());
    gen.setPf(pf);
    pf.hasTranslations = true;
    assertEquals("the IG is translated, but not in the multi-language template format", checked().getNotReusable());
  }

  @Test
  void anIgThatIsntTranslated() throws Exception {
    PublisherSettings settings = new PublisherSettings();
    gen = new PublisherGenerator(settings);
    gen.setPf(pf);
    pf.allOutputs.clear();
    page.getOutputNames().clear();
    pf.allOutputs.add(temp("_includes/index.md").toLowerCase());
    page.getOutputNames().add(temp("_includes/index.md"));
    write("input/pagecontent/index.md", "# Home, edited");
    assertEquals(Collections.singletonList(temp("_includes/index.md")), gen.regeneratePages(Collections.singletonList(page)));
    assertEquals("# Home, edited", read("temp/_includes/index.md"));
    assertEquals(new ArrayList<>(Collections.singletonList(temp("_includes/index.md").toLowerCase())), new ArrayList<>(pf.allOutputs));
  }
}
