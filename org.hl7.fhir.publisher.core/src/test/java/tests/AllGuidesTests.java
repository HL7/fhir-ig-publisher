package tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.apache.commons.io.IOUtils;
import org.hl7.fhir.igtools.publisher.Publisher;
import org.hl7.fhir.igtools.publisher.Publisher.CacheOption;
import org.hl7.fhir.utilities.ToolGlobalSettings;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.junit.jupiter.api.Assertions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

@EnabledIf("igsPathExists")
public class AllGuidesTests {

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
    if (!igsPathExists()) {
      Assertions.assertTrue(true);
      return;
    }
    System.out.println("=======================================================================================");
    String p = (path == null ? Utilities.path(ToolGlobalSettings.getTestIgsPath(), id) : Utilities.path(ToolGlobalSettings.getTestIgsPath(), id, path));
    System.out.println("Publish IG "+ p);
    Publisher pub = new Publisher();
    pub.setConfigFile(p);
    pub.setTxServer("http://tx.fhir.org");
    pub.setCacheOption(CacheOption.LEAVE);
    pub.execute();
    
    System.out.println("===== Analysis ======================================================================");
    // to make diff programs easy to run
    IOUtils.copy(new FileInputStream(Utilities.path(ToolGlobalSettings.getTestIgsPath(), id, "output", "qa.json")), new FileOutputStream(Utilities.path(ToolGlobalSettings.getTestIgsPath(), "records", id+"-qa-gen.json")));
    IOUtils.copy(new FileInputStream(Utilities.path(ToolGlobalSettings.getTestIgsPath(), id, "output", "qa.html")), new FileOutputStream(Utilities.path(ToolGlobalSettings.getTestIgsPath(), "records", id+"-qa-gen.html")));
    if (new File(Utilities.path(ToolGlobalSettings.getTestIgsPath(), id, "output", "qa.txt")).exists()) {
      IOUtils.copy(new FileInputStream(Utilities.path(ToolGlobalSettings.getTestIgsPath(), id, "output", "qa.txt")), new FileOutputStream(Utilities.path(ToolGlobalSettings.getTestIgsPath(), "records", id+"-qa-gen.txt")));
    }
    
    JsonObject current = JsonParser.parseObject(new FileInputStream(Utilities.path(ToolGlobalSettings.getTestIgsPath(), id, "output", "qa.json")));
    JsonObject previous = null;
    if (new File(Utilities.path(ToolGlobalSettings.getTestIgsPath(), "records", id+"-qa.json")).exists()) {
      previous = JsonParser.parseObject(new FileInputStream(Utilities.path(ToolGlobalSettings.getTestIgsPath(), "records", id+"-qa.json")));
    } else {
      previous = new JsonObject();      
    }
    int cErr = current.hasNumber("errs") ? current.asInteger("errs") : 0;
    int pErr = previous.hasNumber("errs") ? previous.asInteger("errs") : 0;
    int cWarn = current.hasNumber("warnings") ? current.asInteger("warnings") : 0;
    int pWarn = previous.hasNumber("warnings") ? previous.asInteger("warnings") : 0;
    int cHint = current.hasNumber("hints") ? current.asInteger("hints") : 0;
    int pHint = previous.hasNumber("hints") ? previous.asInteger("hints") : 0;
    Assertions.assertTrue(cErr <= pErr, "Error count has increased from "+pErr+" to "+cErr);
    Assertions.assertTrue(cWarn <= pWarn, "Warning count has increased from "+pWarn+" to "+cWarn);
    Assertions.assertTrue(cHint <= pHint, "Hint count has increased from "+pHint+" to "+cHint);
    System.out.println("=======================================================================================");
    System.out.println("");
  }

  private static boolean igsPathExists() {
    return ToolGlobalSettings.getTestIgsPath() != null && new File(ToolGlobalSettings.getTestIgsPath()).exists();
  }

//  private String testingPath() {
//    return System.getProperty("user.dir");
//  }

  @Test
  public void testTemplateBase() throws Exception {
    testIg("fhir.base.template.ig", null);
  }

  @Test
  public void test_TemplateHL7() throws Exception {
    testIg("hl7.base.template.ig", null);
  }

  @Test
  public void testTemplateHL7FHIR() throws Exception {
    testIg("hl7.fhir.template.ig", null);
  }

  @Test
  public void testUSCore() throws Exception {
    testIg("hl7.fhir.us.core", null);
  }

  @Test
  public void testSDC() throws Exception {
    testIg("hl7.fhir.uv.sdc", null);
  }


  @Test
  public void testECR() throws Exception {
    testIg("hl7.fhir.us.ecr", "ig.ini");
  }

  @Test
  public void testMHD() throws Exception {
    testIg("ihe.mhd.fhir", null);
  }

  @Test
  public void testAUBase() throws Exception {
    testIg("hl7.fhir.au.base", null);
  }


  @Test
  public void testSample() throws Exception {
    testIg("example.fhir.uv.myig", null);
  }

  @Test
  public void testGuidance() throws Exception {
    testIg("hl7.fhir.uv.howto", null);
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
//    JsonObject json = JsonParser.parseJsonFile(path);
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

  @Test
  public void testIPS() throws Exception {
    testIg("hl7.fhir.uv.ips", null);
  }

  @Test
  public void testIPA() throws Exception {
    testIg("hl7.fhir.uv.ipa", null);
  }

  @Test
  public void testTools() throws Exception {
    testIg("hl7.fhir.uv.tools", null);
  }

  
  
}
