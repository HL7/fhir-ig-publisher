package tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.apache.commons.io.IOUtils;
import org.hl7.fhir.igtools.publisher.Publisher;
import org.hl7.fhir.igtools.publisher.Publisher.CacheOption;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.JsonTrackingParser;
import org.junit.Test;

import com.google.gson.JsonObject;

import junit.framework.Assert;

public class AllGuidesTests {

private static final String ROOT_DIR = "C:\\work\\org.hl7.fhir\\test-igs";
private static final String VER = "1.0.53";

//  private void test(String path) throws Exception {
//    System.out.println("=======================================================================================");
//    System.out.println("Publish IG "+path);
//    Publisher pub = new Publisher();
//    pub.setConfigFile(path);
//    pub.setTxServer("http://tx.fhir.org");
//    pub.setCacheOption(CacheOption.LEAVE);
//    pub.execute();
//    System.out.println("=======================================================================================");
//    System.out.println("");
//  }

  private void testIg(String id, String path) throws Exception {
    if (!new File(ROOT_DIR).exists()) {
      Assert.assertTrue(true);
      return;
    }
    System.out.println("=======================================================================================");
    String p = (path == null ? Utilities.path(ROOT_DIR, id) : Utilities.path(ROOT_DIR, id, path));
    System.out.println("Publish IG "+ p);
    Publisher pub = new Publisher();
    pub.setConfigFile(p);
    pub.setTxServer("http://tx.fhir.org");
    pub.setCacheOption(CacheOption.LEAVE);
    pub.execute();
    
    System.out.println("===== Analysis ======================================================================");
    // to make diff programs easy to run
    IOUtils.copy(new FileInputStream(Utilities.path(ROOT_DIR, id, "output", "qa.json")), new FileOutputStream(Utilities.path(ROOT_DIR, "records", id+"-qa-gen.json")));
    IOUtils.copy(new FileInputStream(Utilities.path(ROOT_DIR, id, "output", "qa.html")), new FileOutputStream(Utilities.path(ROOT_DIR, "records", id+"-qa-gen.html")));
    if (new File(Utilities.path(ROOT_DIR, id, "output", "qa.txt")).exists()) {
      IOUtils.copy(new FileInputStream(Utilities.path(ROOT_DIR, id, "output", "qa.txt")), new FileOutputStream(Utilities.path(ROOT_DIR, "records", id+"-qa-gen.txt")));
    }
    
    JsonObject current = JsonTrackingParser.parseJson(new FileInputStream(Utilities.path(ROOT_DIR, id, "output", "qa.json")));
    JsonObject previous = JsonTrackingParser.parseJson(new FileInputStream(Utilities.path(ROOT_DIR, "records", id+"-qa.json")));
    int cErr = current.has("errs") ? current.get("errs").getAsInt() : 0;
    int pErr = previous.has("errs") ? previous.get("errs").getAsInt() : 0;
    int cWarn = current.has("warnings") ? current.get("warnings").getAsInt() : 0;
    int pWarn = previous.has("warnings") ? previous.get("warnings").getAsInt() : 0;
    int cHint = current.has("hints") ? current.get("hints").getAsInt() : 0;
    int pHint = previous.has("hints") ? previous.get("hints").getAsInt() : 0;
    Assert.assertTrue("Error count has increased from "+pErr+" to "+cErr, cErr <= pErr);
    Assert.assertTrue("Warning count has increased from "+pWarn+" to "+cWarn, cWarn <= pWarn);
    Assert.assertTrue("Hint count has increased from "+pHint+" to "+cHint, cHint <= pHint);
    System.out.println("=======================================================================================");
    System.out.println("");
  }

//  private String testingPath() {
//    return System.getProperty("user.dir");
//  }

  @Test
  public void testTemplateBase() throws Exception {
    testIg("fhir.base.template", null);
  }

  @Test
  public void test_TemplateHL7() throws Exception {
    testIg("hl7.base.template", null);
  }

  @Test
  public void testTemplateHL7FHIR() throws Exception {
    testIg("hl7.fhir.template", null);
  }

  @Test
  public void testUSCore() throws Exception {
    testIg("hl7.fhir.us.core", "ig.json");
  }

  @Test
  public void testSDC() throws Exception {
    testIg("hl7.fhir.uv.sdc", null);
  }


  @Test
  public void testECR() throws Exception {
    testIg("hl7.fhir.us.ecr", "ig.json");
  }

  @Test
  public void testMHD() throws Exception {
    testIg("ihe.mhd.fhir", null);
  }

  @Test
  public void testAUBase() throws Exception {
    testIg("hl7.fhir.au.base", "ig.json");
  }

  
//
//  @Test
//  public void testOldIg() throws Exception {
//    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "old", "ig", "ig.json"));
//  }
//
//  @Test
//  public void testOldIg30() throws Exception {
//    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "old", "ig30", "ig.json"));
//  }
//
//  @Test
//  public void testOldIg14() throws Exception {
//    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "old", "ig14", "ig.json"));
//  }
//
//  @Test
//  public void testOldIg10() throws Exception {
//    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "old", "ig10", "ig.json"));
//  }
//
//  @Test
//  public void testOldIgDependsOnArgonaut() throws Exception {
//    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "old", "igDependsOnArgonaut", "ig.json"));
//  }
//
//  @Test
//  public void testNewIgInlineTemplate1() throws Exception {
//    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig-inline"));
//  }
//
//  @Test
//  public void testNewIgInlineTemplate2() throws Exception {
//    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig-inline-rename"));
//  }
//
//  @Test
//  public void testNewIgExternalTemplateLocal() throws Exception {
//    String path = Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig-dir");
//    test(path);
//    checkIGMods(Utilities.path(path, "output", "ImplementationGuide-hl7.fhir.test.ig40.json"));// check that the onload() event fired as expected
//  }
//
//  private void checkIGMods(String path) throws IOException {
//    JsonObject json = JsonTrackingParser.parseJsonFile(path);
//    Assert.assertEquals("xxxxx", json.get("publisher").getAsString());  // jjjjj is set in the javascript load script
//  }
//
//  @Test
//  public void testNewIgExternalTemplateGitHub() throws Exception {
//    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig-github"));
//  }
//
//  @Test
//  public void testNewIgExternalTemplatePckage() throws Exception {
//    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig-package"));
//  }

  //@Test
  public void testIPS() throws Exception {
    testIg("hl7.fhir.uv.ips", "ig.json");
  }

}
