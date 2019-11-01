package tests;

import java.io.IOException;

import org.hl7.fhir.igtools.publisher.Publisher;
import org.hl7.fhir.igtools.publisher.Publisher.CacheOption;
import org.hl7.fhir.r5.test.utils.TestingUtilities;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.JsonTrackingParser;
import org.junit.Test;

import com.google.gson.JsonObject;

import junit.framework.Assert;

public class AllGuidesTests {

  private void test(String path) throws Exception {
    System.out.println("=======================================================================================");
    System.out.println("Publish IG "+path);
    Publisher pub = new Publisher();
    pub.setConfigFile(path);
    pub.setTxServer("http://tx.fhir.org");
    pub.setCacheOption(CacheOption.LEAVE);
    pub.execute();
    System.out.println("=======================================================================================");
    System.out.println("");
  }

  private String testingPath() {
    return System.getProperty("user.dir");
  }

  @Test
  public void testOldIg() throws Exception {
    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "old", "ig", "ig.json"));
  }

  @Test
  public void testOldIg30() throws Exception {
    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "old", "ig30", "ig.json"));
  }

  @Test
  public void testOldIg14() throws Exception {
    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "old", "ig14", "ig.json"));
  }

  @Test
  public void testOldIg10() throws Exception {
    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "old", "ig10", "ig.json"));
  }

  @Test
  public void testOldIgDependsOnArgonaut() throws Exception {
    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "old", "igDependsOnArgonaut", "ig.json"));
  }

  @Test
  public void testNewIgInlineTemplate1() throws Exception {
    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig-inline"));
  }

  @Test
  public void testNewIgInlineTemplate2() throws Exception {
    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig-inline-rename"));
  }

  @Test
  public void testNewIgExternalTemplateLocal() throws Exception {
    String path = Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig-dir");
    test(path);
    checkIGMods(Utilities.path(path, "output", "ImplementationGuide-hl7.fhir.test.ig40.json"));// check that the onload() event fired as expected
  }

  private void checkIGMods(String path) throws IOException {
    JsonObject json = JsonTrackingParser.parseJsonFile(path);
    Assert.assertEquals("xxxxx", json.get("publisher").getAsString());  // jjjjj is set in the javascript load script
  }

  @Test
  public void testNewIgExternalTemplateGitHub() throws Exception {
    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig-github"));
  }

  @Test
  public void testNewIgExternalTemplatePckage() throws Exception {
    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig-package"));
  }


}
