package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a watch mode build keeps for a page-only rebuild: which changes it can handle, and putting the temp and output
 * folders back as they were
 */
class PageRebuildTest {

  @TempDir
  Path dir;

  private PublisherFields pf;
  private PageRebuild state;
  private FetchedFile page;

  private void write(String path, String content) throws IOException {
    Path p = dir.resolve(path);
    Files.createDirectories(p.getParent());
    Files.write(p, content.getBytes(StandardCharsets.UTF_8));
  }

  private String read(String path) throws IOException {
    return new String(Files.readAllBytes(dir.resolve(path)), StandardCharsets.UTF_8);
  }

  private List<Path> roots() {
    return Arrays.asList(dir.resolve("ig.ini"), dir.resolve("input"));
  }

  @BeforeEach
  void setUp() throws IOException {
    write("ig.ini", "[IG]");
    write("input/pagecontent/index.md", "# Home");
    write("input/fsh/a.fsh", "Profile: A");
    write("temp/index.md", "# Home");
    write("temp/_includes/x.xml", "<x/>");
    write("output/index.html", "<html/>");
    pf = new PublisherFields();
    pf.rootDir = dir.toString();
    pf.tempDir = dir.resolve("temp").toString();
    pf.outputDir = dir.resolve("output").toString();
    page = new FetchedFile("input/pagecontent/index.md", "pagecontent/index.md");
    page.setPath(dir.resolve("input/pagecontent/index.md").toFile().getCanonicalPath());
    page.setRelativePath("index.md");
    page.setProcessMode(FetchedFile.PROCESS_NONE);
    pf.altMap.put("page/" + page.getPath(), page);
    pf.fileList.add(page);
    state = new PageRebuild();
    state.setPf(pf);
  }

  @AfterEach
  void tearDown() {
    state.discard();
  }

  /** what a build that got to the end does with the state */
  private void built() throws IOException {
    state.afterJekyll();
    state.afterPackage();
    state.finished();
  }

  /** what changed since the snapshot was taken, once edit has run */
  private SourceSnapshot.Changes changes(IORunnable edit) throws IOException {
    SourceSnapshot before = SourceSnapshot.take(roots(), null);
    edit.run();
    return SourceSnapshot.take(roots(), before).changesSince(before);
  }

  interface IORunnable {
    void run() throws IOException;
  }

  @Test
  void plainContent() {
    assertTrue(PageRebuild.isPlainContent("# Home\n\nSome *text* and {% include x.md %}"));
    assertFalse(PageRebuild.isPlainContent("{% sql SELECT * FROM Resources %}"));
    assertFalse(PageRebuild.isPlainContent("{% fragment StructureDefinition/x JSON %}"));
    assertFalse(PageRebuild.isPlainContent("see [[[StructureDefinition/x]]]"));
    assertFalse(PageRebuild.isPlainContent("<div class=\"validationRule\"/>"));
  }

  @Test
  void aPageEditIsAPageOnlyChange() throws IOException {
    built();
    List<String> reason = new ArrayList<>();
    List<FetchedFile> pages = state.pagesFor(changes(() -> write("input/pagecontent/index.md", "# Home, edited")), reason);
    assertEquals(Collections.singletonList(page), pages);
  }

  @Test
  void notOnAnotherDay() throws IOException {
    built();
    // the build ran yesterday: its pages say so, so they are all written again
    pf.getExecTime().add(Calendar.DAY_OF_MONTH, -1);
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> write("input/pagecontent/index.md", "# Home, edited")), reason));
    assertEquals("the day changed since the last build", reason.get(0));
  }

  @Test
  void sameDay() {
    Calendar a = Calendar.getInstance();
    a.set(2026, Calendar.OCTOBER, 25, 23, 59, 0);
    Calendar b = (Calendar) a.clone();
    b.set(Calendar.HOUR_OF_DAY, 0);
    assertTrue(PageRebuild.sameDay(a, b));
    b.add(Calendar.MINUTE, 24 * 60 + 1);
    assertFalse(PageRebuild.sameDay(a, b));
    b.add(Calendar.YEAR, -1);
    b.add(Calendar.DAY_OF_MONTH, -1);
    assertFalse(PageRebuild.sameDay(a, b), "the same day of another year");
  }

  @Test
  void notAfterABuildThatDidntFinish() throws IOException {
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> write("input/pagecontent/index.md", "# Home, edited")), reason));
    assertEquals("the last build didn't finish", reason.get(0));
  }

  @Test
  void notAfterABuildThatDidntGetToThePackageStep() throws IOException {
    state.finished();
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> write("input/pagecontent/index.md", "# Home, edited")), reason));
    assertEquals("the last build didn't get to the package step", reason.get(0));
  }

  @Test
  void notWhenTheBuildSaidSo() throws IOException {
    built();
    state.notReusable("the IG is translated");
    state.notReusable("something else");
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> write("input/pagecontent/index.md", "# Home, edited")), reason));
    assertEquals("the IG is translated", reason.get(0));
  }

  @Test
  void notForOtherFiles() throws IOException {
    built();
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> {
      write("input/pagecontent/index.md", "# Home, edited");
      write("input/fsh/a.fsh", "Profile: B");
    }), reason));
    assertTrue(reason.get(0).endsWith("a.fsh is not a page"), reason.get(0));
  }

  @Test
  void notWhenFilesAreAddedOrRemoved() throws IOException {
    built();
    assertNull(state.pagesFor(changes(() -> write("input/pagecontent/new.md", "new")), new ArrayList<>()));
    assertNull(state.pagesFor(changes(() -> Files.delete(dir.resolve("input/pagecontent/index.md"))), new ArrayList<>()));
  }

  @Test
  void notForPagesThatUseTheResources() throws IOException {
    built();
    // the new content does
    assertNull(state.pagesFor(changes(() -> write("input/pagecontent/index.md", "{% sql SELECT 1 %}")), new ArrayList<>()));
    // the old content did
    pf.pagesWithCustomContent.add(page);
    assertNull(state.pagesFor(changes(() -> write("input/pagecontent/index.md", "# Plain again")), new ArrayList<>()));
  }

  @Test
  void notForPagesThatAreTransformedOrAlsoReadAsResources() throws IOException {
    built();
    page.setProcessMode(FetchedFile.PROCESS_XSLT);
    assertNull(state.pagesFor(changes(() -> write("input/pagecontent/index.md", "# 1")), new ArrayList<>()));
    page.setProcessMode(FetchedFile.PROCESS_NONE);
    page.addOutput("_includes/index.md", FetchedFile.PROCESS_XSLT, null);
    assertNull(state.pagesFor(changes(() -> write("input/pagecontent/index.md", "# 2")), new ArrayList<>()));

    state.discard();
    setUp();
    built();
    pf.resourceDirs.add(dir.resolve("input/pagecontent").toString());
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> write("input/pagecontent/index.md", "# 3")), reason));
    assertTrue(reason.get(0).endsWith("is also read as something other than a page"), reason.get(0));
  }

  @Test
  void prepareTempTakesOutWhatCameAfterJekyll() throws IOException {
    state.beforeJekyll();
    // what Jekyll and the rest of the build leave in the temp folder
    write("temp/full-ig.zip", "zip");
    write("temp/.jekyll-cache/a/b", "cache");
    // the changed page, written again
    write("temp/index.md", "# Home, edited");
    Files.setLastModifiedTime(dir.resolve("temp/index.md"), FileTime.fromMillis(System.currentTimeMillis() + 5000));
    state.prepareTemp(Collections.singletonList(dir.resolve("temp/index.md").toString()));
    assertFalse(Files.exists(dir.resolve("temp/full-ig.zip")));
    assertFalse(Files.exists(dir.resolve("temp/.jekyll-cache")));
    assertEquals("# Home, edited", read("temp/index.md"));
    assertEquals("<x/>", read("temp/_includes/x.xml"));
  }

  @Test
  void prepareTempFailsIfSomethingElseChanged() throws IOException {
    state.beforeJekyll();
    write("temp/_includes/x.xml", "<x>changed</x>");
    Files.setLastModifiedTime(dir.resolve("temp/_includes/x.xml"), FileTime.fromMillis(System.currentTimeMillis() + 5000));
    assertThrows(IOException.class, () -> state.prepareTemp(Collections.emptyList()));

    state.beforeJekyll();
    Files.delete(dir.resolve("temp/_includes/x.xml"));
    assertThrows(IOException.class, () -> state.prepareTemp(Collections.emptyList()));
  }

  @Test
  void prepareTempFailsForAPageThatWasntThereBefore() throws IOException {
    state.beforeJekyll();
    write("temp/other.md", "other");
    assertThrows(IOException.class, () -> state.prepareTemp(Collections.singletonList(dir.resolve("temp/other.md").toString())));
  }

  @Test
  void thePackageOutputsArePutBack() throws IOException {
    state.finished();
    state.afterJekyll();
    write("output/package.tgz", "tgz");
    write("output/sub/full.zip", "zip");
    state.afterPackage();
    assertEquals(Arrays.asList(Paths.get("package.tgz"), Paths.get("sub", "full.zip")), sorted(state.getStashed()));
    assertNull(state.getNotReusable());

    // what Jekyll does when it runs again
    Files.delete(dir.resolve("output/package.tgz"));
    Files.delete(dir.resolve("output/sub/full.zip"));
    state.restorePackageOutputs();
    assertEquals("tgz", read("output/package.tgz"));
    assertEquals("zip", read("output/sub/full.zip"));
    assertEquals("<html/>", read("output/index.html"));
    Path stash = state.getStashFolder();
    assertTrue(Files.isDirectory(stash));
    state.discard();
    assertFalse(Files.exists(stash));
  }

  @Test
  void aPackageStepThatDeletesOutputIsNotReplayed() throws IOException {
    state.finished();
    state.afterJekyll();
    Files.delete(dir.resolve("output/index.html"));
    state.afterPackage();
    assertTrue(state.getNotReusable().startsWith("the package step deleted"));
    state.discard();
  }

  private static List<Path> sorted(List<Path> list) {
    List<Path> res = new ArrayList<>(list);
    Collections.sort(res);
    return res;
  }

  @Test
  void theTemplatesCheckMustSayTheSame() throws IOException {
    Map<String, List<ValidationMessage>> before = new HashMap<>();
    before.put("a", new ArrayList<>(Collections.singletonList(
        new ValidationMessage(Source.Template, IssueType.BUSINESSRULE, "x", "something", IssueSeverity.WARNING))));
    state.setOnCheckMessages(before);
    Map<String, List<ValidationMessage>> same = new HashMap<>();
    same.put("a", new ArrayList<>(Collections.singletonList(
        new ValidationMessage(Source.Template, IssueType.BUSINESSRULE, "x", "something", IssueSeverity.WARNING))));
    state.checkOnCheckMessages(same);
    same.get("a").get(0).setMessage("something else");
    assertThrows(IOException.class, () -> state.checkOnCheckMessages(same));
    assertThrows(IOException.class, () -> state.checkOnCheckMessages(null));

    state.setOnCheckMessages(null);
    state.checkOnCheckMessages(null);
  }

  private static ValidationMessage warning(String text) {
    return new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "x", text, IssueSeverity.WARNING);
  }

  private void filter(ValidationMessage m) {
    pf.suppressedMessages.contains(m.getMessage(), m);
  }

  @Test
  void theSuppressedMessagesAreCountedAsInTheBuild() throws IOException {
    pf.suppressedMessages.add("Suppressed%", "test");
    ValidationMessage before = warning("Suppressed before the html checks");
    ValidationMessage inQa = warning("Suppressed by qa");
    pf.errors.add(before);
    pf.errors.add(inQa);
    filter(before);
    built();
    // what qa and the html checks do in the build
    filter(inQa);
    filter(warning("Suppressed html check message"));
    assertEquals(3, pf.suppressedMessages.list("test").get(0).getUseCount());

    // and again in the page rebuild
    state.restore();
    filter(inQa);
    filter(warning("Suppressed html check message"));
    assertEquals(3, pf.suppressedMessages.list("test").get(0).getUseCount());
    assertEquals("test", inQa.getComment());
  }

  @Test
  void theMessagesArePutBackAsTheyWereBeforeTheHtmlChecks() throws IOException {
    pf.suppressedMessages.add("Suppressed%", "test");
    FetchedFile file = new FetchedFile("input/resources/a.json");
    FetchedResource res = file.addResource("a");
    pf.fileList.add(file);
    ValidationMessage error = new ValidationMessage(Source.InstanceValidator, IssueType.CODEINVALID, "Patient/a: Patient.gender",
        "bad code", IssueSeverity.ERROR);
    ValidationMessage suppressed = warning("Suppressed in validation");
    ValidationMessage ig = warning("An IG level warning");
    res.getErrors().add(error);
    res.getErrors().add(suppressed);
    file.getErrors().add(error);
    file.getErrors().add(suppressed);
    pf.errors.add(ig);
    filter(suppressed);
    file.setFilteredMessages(new ArrayList<>(Arrays.asList(error)));
    built();

    // what the html checks and qa then do in the build
    ValidationMessage link = new ValidationMessage(Source.HtmlChecker, IssueType.NOTFOUND, "index.html",
        "The link 'x.html' for \"x\" cannot be resolved", IssueSeverity.ERROR);
    pf.errors.add(link);
    page.getErrors().add(link);
    file.getErrors().add(warning("Suppressed html check message"));
    filter(file.getErrors().get(2));
    filter(ig);
    file.setFilteredMessages(new ArrayList<>(file.getErrors()));
    res.getErrors().clear();
    suppressed.setComment("changed");
    suppressed.setMatched(false);
    error.setMatched(true);
    error.setComment("changed");
    ig.setMatched(true);

    state.restore();
    assertEquals(Collections.singletonList(ig), pf.errors);
    assertEquals(Arrays.asList(error, suppressed), file.getErrors());
    assertEquals(Arrays.asList(error, suppressed), res.getErrors());
    assertTrue(page.getErrors().isEmpty());
    assertEquals(Collections.singletonList(error), file.getFilteredMessages());
    assertTrue(suppressed.isMatched());
    assertEquals("test", suppressed.getComment());
    assertFalse(error.isMatched());
    assertNull(error.getComment());
    assertFalse(ig.isMatched());
    assertEquals(1, pf.suppressedMessages.list("test").get(0).getUseCount());
    // the same objects: qa matches them, and counts them, as the build did
    assertTrue(file.getErrors().get(1) == res.getErrors().get(1));

    // and a second page rebuild starts from the same state again
    pf.errors.add(link);
    filter(warning("Suppressed html check message"));
    state.restore();
    assertEquals(Collections.singletonList(ig), pf.errors);
    assertEquals(1, pf.suppressedMessages.list("test").get(0).getUseCount());
  }

  /** a translated IG: its pages are written in ru from input/translations/ru, if they are there */
  private void translated() throws IOException {
    pf.defaultTranslationLang = "en";
    pf.translationLangs.add("ru");
    pf.translationSources.add("input/translations/ru");
    state.setPageLanguages(Collections.singletonList("ru"));
    write("input/translations/ru/pagecontent/index.md", "# Главная");
    write("input/translations/ru/StructureDefinition-a.po", "msgid \"\"");
  }

  /** a page that comes from a folder that the template pre-processes into _includes, as -intro and -notes do */
  private FetchedFile prePage(String loadPath, String content) throws IOException {
    write("input/" + loadPath, content);
    FetchedFile f = new FetchedFile("input/" + loadPath, loadPath);
    f.setPath(dir.resolve("input/" + loadPath).toFile().getCanonicalPath());
    f.setRelativePath("_includes/" + new java.io.File(loadPath).getName());
    f.setProcessMode(FetchedFile.PROCESS_NONE);
    pf.altMap.put("pre-page/" + f.getPath(), f);
    pf.fileList.add(f);
    return f;
  }

  @Test
  void anEditToATranslationIsAChangeToItsPage() throws IOException {
    translated();
    built();
    List<String> reason = new ArrayList<>();
    assertEquals(Collections.singletonList(page),
        state.pagesFor(changes(() -> write("input/translations/ru/pagecontent/index.md", "# Главная страница")), reason), reason.toString());
    // and with the page
    assertEquals(Collections.singletonList(page), state.pagesFor(changes(() -> {
      write("input/pagecontent/index.md", "# Home, edited");
      write("input/translations/ru/pagecontent/index.md", "# Главная, edited");
    }), reason), reason.toString());
  }

  @Test
  void anEditToAnIntroOrItsTranslationIsAChangeToIt() throws IOException {
    translated();
    FetchedFile intro = prePage("pagecontent/StructureDefinition-a-intro.md", "Intro");
    write("input/translations/ru/pagecontent/StructureDefinition-a-intro.md", "Введение");
    built();
    List<String> reason = new ArrayList<>();
    assertEquals(Collections.singletonList(intro),
        state.pagesFor(changes(() -> write("input/pagecontent/StructureDefinition-a-intro.md", "Intro, edited")), reason), reason.toString());
    assertEquals(Collections.singletonList(intro),
        state.pagesFor(changes(() -> write("input/translations/ru/pagecontent/StructureDefinition-a-intro.md", "Введение, edited")), reason),
        reason.toString());
  }

  @Test
  void anEditToAPageWithoutATranslationIsAChangeToIt() throws IOException {
    translated();
    FetchedFile other = prePage("pagecontent/other.md", "Other");
    built();
    List<String> reason = new ArrayList<>();
    assertEquals(Collections.singletonList(other),
        state.pagesFor(changes(() -> write("input/pagecontent/other.md", "Other, edited")), reason), reason.toString());
  }

  @Test
  void notForTheTranslationsOfTheResources() throws IOException {
    translated();
    built();
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> write("input/translations/ru/StructureDefinition-a.po", "msgid \"a\"")), reason));
    assertTrue(reason.get(0).endsWith("StructureDefinition-a.po is read as a translation of the resources"), reason.get(0));
  }

  @Test
  void notForATranslationThatIsNotOfAPage() throws IOException {
    translated();
    write("input/translations/ru/pagecontent/gone.md", "# Удалено");
    built();
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> write("input/translations/ru/pagecontent/gone.md", "# Удалено, edited")), reason));
    assertTrue(reason.get(0).endsWith("gone.md is not the translation of a page"), reason.get(0));
  }

  /** another page that reads its translation from where the index page does */
  private FetchedFile anotherIndex(String statedPath) throws IOException {
    write(statedPath, "# Other");
    FetchedFile f = new FetchedFile(statedPath, "pagecontent/index.md");
    f.setPath(dir.resolve(statedPath).toFile().getCanonicalPath());
    f.setRelativePath("other-index.md");
    f.setProcessMode(FetchedFile.PROCESS_NONE);
    pf.altMap.put("page/" + f.getPath(), f);
    pf.fileList.add(f);
    return f;
  }

  @Test
  void notForATranslationOfMoreThanOnePage() throws IOException {
    translated();
    anotherIndex("pages/pagecontent/index.md");
    built();
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> write("input/translations/ru/pagecontent/index.md", "# Главная страница")), reason));
    assertTrue(reason.get(0).endsWith("index.md is the translation of 2 pages"), reason.get(0));
  }

  @Test
  void notForATranslationOfATemplatePage() throws IOException {
    // the build doesn't translate the template's pages
    translated();
    pf.fileList.remove(page);
    pf.altMap.remove("page/" + page.getPath());
    anotherIndex(dir.resolve("template/pagecontent/index.md").toString());
    built();
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> write("input/translations/ru/pagecontent/index.md", "# Главная страница")), reason));
    assertTrue(reason.get(0).endsWith("index.md is not the translation of a page"), reason.get(0));
  }

  @Test
  void notForATranslationThatTheBuildDoesntRead() throws IOException {
    // translations, but the template doesn't write the pages in each language
    translated();
    state.setPageLanguages(Collections.emptyList());
    built();
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> write("input/translations/ru/pagecontent/index.md", "# Главная страница")), reason));
    assertTrue(reason.get(0).endsWith("index.md is not a page"), reason.get(0));
  }

  @Test
  void notWhenATranslationIsAddedOrRemoved() throws IOException {
    // which pages are translated is in the page data (pages.json)
    translated();
    prePage("pagecontent/other.md", "Other");
    built();
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> write("input/translations/ru/pagecontent/other.md", "Другое")), reason));
    assertEquals("files were added or removed", reason.get(0));
    assertNull(state.pagesFor(changes(() -> Files.delete(dir.resolve("input/translations/ru/pagecontent/index.md"))), reason));
  }

  @Test
  void notForATranslationThatUsesTheResources() throws IOException {
    translated();
    built();
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> write("input/translations/ru/pagecontent/index.md", "{% sql SELECT 1 %}")), reason));
    assertTrue(reason.get(0).endsWith("has content that depends on the resources"), reason.get(0));
  }

  @Test
  void notForAPageInAFolderTheTemplateReads() throws IOException {
    prePage("includes/menu.xml", "<ul/>");
    Map<String, String> blockers = new HashMap<>();
    blockers.put("includes", "the template's script ant.xml reads (${dir.input}/includes)");
    state.setFolderBlockers(blockers);
    built();
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> write("input/includes/menu.xml", "<ul><li/></ul>")), reason));
    assertTrue(reason.get(0).endsWith("menu.xml is in a folder that the template's script ant.xml reads (${dir.input}/includes)"), reason.get(0));
    // the template doesn't read the other page folders
    assertEquals(Collections.singletonList(page), state.pagesFor(changes(() -> write("input/pagecontent/index.md", "# Home, edited")), reason));
  }

  @Test
  void notForATranslationInAFolderTheTemplateReads() throws IOException {
    translated();
    prePage("includes/menu.xml", "<ul/>");
    write("input/translations/ru/includes/menu.xml", "<ul><li>Меню</li></ul>");
    Map<String, String> blockers = new HashMap<>();
    blockers.put("includes", "the template's script ant.xml reads (${dir.input}/includes)");
    blockers.put("intro-notes", "the template's script ant.xml names");
    state.setFolderBlockers(blockers);
    built();
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> write("input/translations/ru/includes/menu.xml", "<ul><li>Меню 2</li></ul>")), reason));
    assertTrue(reason.get(0).endsWith("menu.xml is in a folder that the template's script ant.xml reads (${dir.input}/includes)"), reason.get(0));
    // a page folder that the template names
    prePage("intro-notes/StructureDefinition-a-intro.md", "Intro");
    write("input/translations/ru/intro-notes/StructureDefinition-a-intro.md", "Введение");
    built();
    reason.clear();
    assertNull(state.pagesFor(changes(() -> write("input/translations/ru/intro-notes/StructureDefinition-a-intro.md", "Введение 2")), reason));
    assertTrue(reason.get(0).endsWith("is in a folder that the template's script ant.xml names"), reason.get(0));
    // and not the others
    assertEquals(Collections.singletonList(page), state.pagesFor(changes(() -> write("input/translations/ru/pagecontent/index.md", "# Главная 2")), reason));
  }

  @Test
  void notForAFileInTheInputFolderThatTheTemplateReads() throws IOException {
    prePage("toc.md", "# Contents");
    state.setFolderBlockers(Collections.singletonMap("toc.md", "the template's script ant.xml reads (${dir.input}/toc.md)"));
    built();
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> write("input/toc.md", "# Contents 2")), reason));
    assertTrue(reason.get(0).endsWith("the template's script ant.xml reads (${dir.input}/toc.md)"), reason.get(0));
  }

  @Test
  void notForAPageThatIsAlsoATranslation() throws IOException {
    // a page in a translation source: the build writes the page from it, and the translation of the page with that name
    translated();
    pf.translationSources.set(0, "input/ru");
    prePage("ru/pagecontent/index.md", "# Главная");
    built();
    List<String> reason = new ArrayList<>();
    assertNull(state.pagesFor(changes(() -> write("input/ru/pagecontent/index.md", "# Главная страница")), reason));
    assertTrue(reason.get(0).endsWith("is both a page and in the translations of the pages"), reason.get(0));
  }

  @Test
  void aLangFragmentIsPlainContent() {
    // it's an include of the fragment in the page's language, which is in the temp folder already
    assertTrue(PageRebuild.isPlainContent("{% lang-fragment ip-statements.xhtml %}"));
  }

  @Test
  void plainContentFromBytes() {
    assertTrue(PageRebuild.isPlainContent("Embrüo".getBytes(StandardCharsets.UTF_8)));
    assertFalse(PageRebuild.isPlainContent("x [[[y]]]".getBytes(StandardCharsets.UTF_8)));
  }
}
