package tests;

import java.io.IOException;

import org.hl7.fhir.igtools.publisher.Publisher;
import org.hl7.fhir.igtools.publisher.Publisher.CacheOption;
import org.hl7.fhir.r5.test.utils.TestingUtilities;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.Utilities;
import org.junit.Test;

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
  public void testNewIgInlineTemplate1() throws Exception {
    String path = Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig");
    setup(path, "#template");
    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig"));
  } 

  @Test
  public void testNewIgInlineTemplate2() throws Exception {
    String path = Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig2");
    setup(path, "#template-local");
    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig2"));
  } 

  @Test
  public void testNewIgExternalTemplateLocal() throws Exception {
    String path = Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig");
    setup(path, "C:\\work\\org.hl7.fhir\\org.hl7.fhir.template.test"); // not sure what to do about the path here...
    test(path);
  }

  @Test
  public void testNewIgExternalTemplateGitHub() throws Exception {
    String path = Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig");
    setup(path, "http://github.com/FHIR/test-template");
    test(path);
  }

  @Test
  public void testNewIgExternalTemplatePckage() throws Exception {
    String path = Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig");
    setup(path, "fhir.test.template");
    test(path);
  }

  private void setup(String path, String template) throws IOException {
    IniFile ini = new IniFile(Utilities.path(path, "ig.ini"));
    ini.setStringProperty("IG", "ig", "resources/ig-new.json", null);
    ini.setStringProperty("IG", "template", template, null); 
    ini.save();
  } 

}
