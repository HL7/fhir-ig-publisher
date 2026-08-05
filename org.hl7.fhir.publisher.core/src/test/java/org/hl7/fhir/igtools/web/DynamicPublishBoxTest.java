package org.hl7.fhir.igtools.web;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.PackageList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DynamicPublishBoxTest {

  private static final String CANONICAL = "http://example.org/fhir";

  private static final String PACKAGE_LIST =
      "{"
      + "\"package-id\": \"example.fhir.test\","
      + "\"canonical\": \""+CANONICAL+"\","
      + "\"title\": \"Test IG\","
      + "\"list\": ["
      + "  {\"version\": \"current\", \"path\": \"https://build.example.org/ig/test\", \"status\": \"ci-build\", \"fhirversion\": \"4.0.1\"},"
      + "  {\"version\": \"2.0.0\", \"path\": \""+CANONICAL+"/2.0.0\", \"status\": \"trial-use\", \"sequence\": \"STU 2\", \"fhirversion\": \"4.0.1\", \"current\": true, \"milestoneName\": \"STU2\"},"
      + "  {\"version\": \"1.5.0\", \"path\": \""+CANONICAL+"/1.5.0\", \"status\": \"ballot\", \"sequence\": \"STU 2\", \"fhirversion\": \"4.0.1\"},"
      + "  {\"version\": \"1.0.0\", \"path\": \""+CANONICAL+"/1.0.0\", \"status\": \"trial-use\", \"sequence\": \"STU 1\", \"fhirversion\": \"4.0.1\", \"milestoneName\": \"STU1\"}"
      + "]"
      + "}";

  private PackageList pl() throws IOException {
    return new PackageList(JsonParser.parseObject(PACKAGE_LIST));
  }

  // ---- fragment generation -------------------------------------------------

  @Test
  public void dynamicFragmentKeepsTheStaticWording() throws IOException {
    PackageList pl = pl();
    for (String v : new String[] { "1.0.0", "1.5.0", "2.0.0" }) {
      String stat = PublishBoxStatementGenerator.genFragment(pl, pl.findByVersion(v), pl.current(), CANONICAL, false, false, false);
      String dyn = PublishBoxStatementGenerator.genFragment(pl, pl.findByVersion(v), pl.current(), CANONICAL, false, false, true);
      String stripped = dyn
          .replaceAll("<span class=\"fhir-pb\"[^>]*>", "")
          .replace("</span>", "")
          .replace(PublishBoxStatementGenerator.DYNAMIC_SCRIPTS, "");
      Assertions.assertEquals(stat, stripped, "the dynamic statement for v"+v+" must bake exactly the static wording");
    }
  }

  @Test
  public void dynamicFragmentRecordsItsAssumptions() throws IOException {
    PackageList pl = pl();
    String dyn = PublishBoxStatementGenerator.genFragment(pl, pl.findByVersion("1.0.0"), pl.current(), CANONICAL, false, false, true);
    Assertions.assertTrue(dyn.contains("class=\"fhir-pb\""));
    Assertions.assertTrue(dyn.contains("data-pb-fmt=\""+PublishBoxStatementGenerator.DYNAMIC_FORMAT+"\""));
    Assertions.assertTrue(dyn.contains("data-pb-version=\"1.0.0\""));
    Assertions.assertTrue(dyn.contains("data-pb-current=\"2.0.0\""));
    Assertions.assertTrue(dyn.contains("data-pb-canonical=\""+CANONICAL+"\""));
    Assertions.assertTrue(dyn.contains("src=\"{{path}}package-list.js\" defer"));
    Assertions.assertTrue(dyn.contains("src=\"{{path}}publish-box.js\" defer"));
    Assertions.assertFalse(dyn.contains("<script>"), "no inline script (CSP)");
    Assertions.assertFalse(dyn.contains("fetch("), "no inline fetch");
  }

  @Test
  public void withdrawnVersionStaysFullyStatic() throws IOException {
    PackageList pl = pl();
    PackageList.PackageListEntry v = pl.findByVersion("1.0.0");
    v.setStatus("withdrawn");
    String dyn = PublishBoxStatementGenerator.genFragment(pl, v, pl.current(), CANONICAL, false, false, true);
    Assertions.assertFalse(dyn.contains("fhir-pb"));
    Assertions.assertFalse(dyn.contains("<script"));
  }

  // ---- support files -------------------------------------------------------

  @Test
  public void packageListJsWrapsThePackageListVerbatim(@TempDir Path tmp) throws IOException {
    String json = PACKAGE_LIST.replace("Test IG", "Test IG </script> attack");
    FileUtilities.stringToFile(json, Utilities.path(tmp.toString(), "package-list.json"));
    DynamicPublishBoxSupport.writeSupportFiles(tmp.toString());

    String js = FileUtilities.fileToString(Utilities.path(tmp.toString(), "package-list.js"));
    Assertions.assertFalse(js.contains("</script>"), "must not be able to close a script element");
    String payload = js.substring(js.indexOf("var fhirPackageList = ") + "var fhirPackageList = ".length());
    payload = payload.substring(0, payload.lastIndexOf(';')).replace("<\\/", "</");
    JsonObject parsed = JsonParser.parseObject(payload);
    Assertions.assertEquals("example.fhir.test", parsed.asString("package-id"));
    Assertions.assertEquals(4, parsed.getJsonArray("list").size());

    String box = FileUtilities.fileToString(Utilities.path(tmp.toString(), "publish-box.js"));
    Assertions.assertTrue(box.contains("fhir-pb"));
    Assertions.assertTrue(box.contains("ci-build"), "current-detection must mirror PackageList.current()");
  }

  @Test
  public void supportFilesAreNotRewrittenWhenUnchanged(@TempDir Path tmp) throws IOException {
    FileUtilities.stringToFile(PACKAGE_LIST, Utilities.path(tmp.toString(), "package-list.json"));
    DynamicPublishBoxSupport.writeSupportFiles(tmp.toString());
    File f = new File(Utilities.path(tmp.toString(), "package-list.js"));
    Assertions.assertTrue(f.setLastModified(1000000L));
    DynamicPublishBoxSupport.writeSupportFiles(tmp.toString());
    Assertions.assertEquals(1000000L, f.lastModified(), "an unchanged file must not be rewritten (no upload churn)");
  }

  @Test
  public void pagesManifestRoundTrips(@TempDir Path tmp) throws IOException {
    String vf = Utilities.path(tmp.toString(), "1.0.0");
    FileUtilities.createDirectory(vf);
    FileUtilities.createDirectory(Utilities.path(vf, "sub"));
    FileUtilities.stringToFile("<html/>", Utilities.path(vf, "index.html"));
    FileUtilities.stringToFile("<html/>", Utilities.path(vf, "sub", "page.html"));
    FileUtilities.stringToFile("{}", Utilities.path(vf, "data.json"));
    DynamicPublishBoxSupport.writePagesManifest(vf);

    Set<String> pages = new HashSet<>();
    Assertions.assertTrue(DynamicPublishBoxSupport.loadPagesManifest(vf, pages));
    Assertions.assertEquals(Set.of("index.html", "sub/page.html"), pages);

    Set<String> none = new HashSet<>();
    Assertions.assertFalse(DynamicPublishBoxSupport.loadPagesManifest(Utilities.path(tmp.toString(), "nowhere"), none));
  }

  // ---- version folder updating ---------------------------------------------

  private static final String PAGE = "<html><body><!--ReleaseHeader--><p id=\"publish-box\">placeholder</p><!--EndReleaseHeader--><p>content</p></body></html>";

  private String setupSite(Path tmp) throws IOException {
    String root = tmp.toString();
    FileUtilities.stringToFile(PACKAGE_LIST, Utilities.path(root, "package-list.json"));
    for (String v : new String[] { "1.0.0", "2.0.0" }) {
      FileUtilities.createDirectory(Utilities.path(root, v));
      FileUtilities.stringToFile(PAGE, Utilities.path(root, v, "index.html"));
    }
    // only exists in 1.0.0:
    FileUtilities.stringToFile(PAGE, Utilities.path(root, "1.0.0", "only-in-stu1.html"));
    return root;
  }

  private int update(String root, PackageList pl, String version, boolean dynamic) throws IOException {
    String vf = Utilities.path(root, version);
    IGReleaseVersionUpdater igvu = new IGReleaseVersionUpdater(vf, CANONICAL, root, null, null,
        pl.findByVersion(version).json(), Utilities.path(root, "2.0.0"), dynamic);
    String fragment = PublishBoxStatementGenerator.genFragment(pl, pl.findByVersion(version), pl.current(), CANONICAL,
        pl.findByVersion(version) == pl.current(), false, dynamic);
    igvu.updateStatement(fragment, 1, pl.milestones());
    return igvu.getCountUpdated();
  }

  @Test
  public void updaterBakesAccurateStatementAndPageVersions(@TempDir Path tmp) throws IOException {
    String root = setupSite(tmp);
    PackageList pl = pl();
    Assertions.assertEquals(2, update(root, pl, "1.0.0", true));

    String page = FileUtilities.fileToString(Utilities.path(root, "1.0.0", "index.html"));
    Assertions.assertTrue(page.contains("The current version which supersedes this version is"), "baked text accurate at publication");
    Assertions.assertTrue(page.contains("data-pb-current=\"2.0.0\""));
    Assertions.assertTrue(page.contains("src=\"../package-list.js\""), "level is applied to the script references");
    // page versions: exists in both milestones; own milestone bold, other linked
    Assertions.assertTrue(page.contains("<b>STU1</b>"));
    Assertions.assertTrue(page.contains("href=\""+CANONICAL+"/2.0.0/index.html\">STU2</a>"));
    Assertions.assertTrue(page.contains("data-pb-known=\"2.0.0 1.0.0\""), "all checkable milestones recorded");
    Assertions.assertTrue(page.contains("data-pb-rel=\"index.html\""));

    String only = FileUtilities.fileToString(Utilities.path(root, "1.0.0", "only-in-stu1.html"));
    Assertions.assertTrue(only.contains("<b>STU1</b>"));
    Assertions.assertFalse(only.contains(">STU2</a>"), "no link to a milestone that does not contain the page");
  }

  @Test
  public void updaterLeavesBakedPagesAloneOnLaterPublications(@TempDir Path tmp) throws IOException {
    String root = setupSite(tmp);
    PackageList pl = pl();
    Assertions.assertEquals(2, update(root, pl, "1.0.0", true));
    String before = FileUtilities.fileToString(Utilities.path(root, "1.0.0", "index.html"));

    // a new version 3.0.0 is published: 1.0.0's pages must not change at all
    PackageList pl2 = new PackageList(JsonParser.parseObject(PACKAGE_LIST
        .replace("\"list\": [", "\"list\": [ {\"version\": \"3.0.0\", \"path\": \""+CANONICAL+"/3.0.0\", \"status\": \"trial-use\", \"sequence\": \"STU 3\", \"fhirversion\": \"4.0.1\", \"current\": true, \"milestoneName\": \"STU3\"},")
        .replace("\"fhirversion\": \"4.0.1\", \"current\": true, \"milestoneName\": \"STU2\"", "\"fhirversion\": \"4.0.1\", \"milestoneName\": \"STU2\"")));
    Assertions.assertEquals("3.0.0", pl2.current().version());
    Assertions.assertEquals(0, update(root, pl2, "1.0.0", true), "no past page may be rewritten by a later publication");
    Assertions.assertEquals(before, FileUtilities.fileToString(Utilities.path(root, "1.0.0", "index.html")));
  }

  @Test
  public void updaterMigratesStaticPagesExactlyOnce(@TempDir Path tmp) throws IOException {
    String root = setupSite(tmp);
    PackageList pl = pl();
    Assertions.assertEquals(2, update(root, pl, "1.0.0", false), "static mode bakes the plain statement");
    Assertions.assertEquals(2, update(root, pl, "1.0.0", true), "first dynamic run migrates every page");
    Assertions.assertEquals(0, update(root, pl, "1.0.0", true), "second dynamic run changes nothing");
  }

  @Test
  public void updaterUsesPagesManifestWhenTreeIsAbsent(@TempDir Path tmp) throws IOException {
    String root = setupSite(tmp);
    PackageList pl = pl();
    // replace 2.0.0's tree with just its manifest, as a publication without local trees sees it
    DynamicPublishBoxSupport.writePagesManifest(Utilities.path(root, "2.0.0"));
    new File(Utilities.path(root, "2.0.0", "index.html")).delete();

    Assertions.assertEquals(2, update(root, pl, "1.0.0", true));
    String page = FileUtilities.fileToString(Utilities.path(root, "1.0.0", "index.html"));
    Assertions.assertTrue(page.contains("href=\""+CANONICAL+"/2.0.0/index.html\">STU2</a>"), "existence resolved from the manifest");
    Assertions.assertTrue(page.contains("data-pb-known=\"2.0.0 1.0.0\""));
  }

  @Test
  public void updaterLeavesUncheckableMilestonesToTheClient(@TempDir Path tmp) throws IOException {
    String root = setupSite(tmp);
    PackageList pl = pl();
    FileUtilities.clearDirectory(Utilities.path(root, "2.0.0"));
    new File(Utilities.path(root, "2.0.0")).delete();

    Assertions.assertEquals(2, update(root, pl, "1.0.0", true));
    String page = FileUtilities.fileToString(Utilities.path(root, "1.0.0", "index.html"));
    Assertions.assertFalse(page.contains(">STU2</a>"), "an uncheckable milestone is not baked");
    Assertions.assertTrue(page.contains("data-pb-known=\"1.0.0\""), "and not recorded as known, so publish-box.js verifies it");
  }
}
