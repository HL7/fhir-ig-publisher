package tests;

import org.hl7.fhir.igtools.publisher.Publisher;
import org.hl7.fhir.igtools.publisher.Publisher.CacheOption;
import org.hl7.fhir.r5.test.utils.TestingUtilities;
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
  public void testNewIg() throws Exception {
    test(Utilities.path(testingPath(), "src", "test", "resources", "test-igs", "new", "ig"));
  } 

}
