package tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.hl7.fhir.igtools.publisher.Publisher;
import org.hl7.fhir.igtools.publisher.PublisherUtils;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.settings.FhirSettings;
import org.hl7.fhir.utilities.xml.XMLUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

@EnabledIf("igsPathExists")
public class AllGuidesTests {


  private static String toMB(long maxMemory) {
    return Long.toString(maxMemory / (1024*1024));
  }
  
  private void testIg(String id, String path) throws Exception {
    writeMem("Starting memory");
    if (!igsPathExists()) {
      Assertions.assertTrue(true);
      return;
    }
    String version = readVersion();
    File statsFile = determineStatsFile();
    long time = System.currentTimeMillis();
    long startingMem = getCurrentmem();
    
    System.out.println("=======================================================================================");
    String p = (path == null ? Utilities.path(FhirSettings.getTestIgsPath(), id) : Utilities.path(FhirSettings.getTestIgsPath(), id, path));
    System.out.println("Publish IG "+ p);
    
    System.out.println("Detected Java version: " + System.getProperty("java.version")+" from "+System.getProperty("java.home")+" on "+System.getProperty("os.name")+"/"+System.getProperty("os.arch")+" ("+System.getProperty("sun.arch.data.model")+"bit). "+toMB(Runtime.getRuntime().maxMemory())+"MB available");
    System.out.println("dir = "+System.getProperty("user.dir")+", path = "+System.getenv("PATH"));
    
    Publisher pub = new Publisher();
    pub.setConfigFile(p);
    pub.getSettings().setTxServer(FhirSettings.getTxFhirDevelopment());
    pub.settings.setCacheOption(PublisherUtils.CacheOption.CLEAR_ALL);
    pub.execute();
    
    System.out.println("===== Analysis ======================================================================");
    System.out.println("-- output in "+Utilities.path(FhirSettings.getTestIgsPath(), "actual", id+".txt")+" --");
    
    // to make diff programs eas
    // .y to run
    File actual = new File(Utilities.path(FhirSettings.getTestIgsPath(), "actual", id + ".json"));
    IOUtils.copy(new FileInputStream(Utilities.path(FhirSettings.getTestIgsPath(), id, "output", "qa.json")), new FileOutputStream(actual));
    JsonObject o = JsonParser.parseObject(actual);
    o.remove("date");
    o.remove("dateISO8601");
    o.remove("maxMemory");
    JsonParser.compose(o, actual, true);
    IOUtils.copy(new FileInputStream(Utilities.path(FhirSettings.getTestIgsPath(), id, "output", "qa.compare.txt")), new FileOutputStream(Utilities.path(FhirSettings.getTestIgsPath(), "actual", id+".txt")));
    
    JsonObject current = JsonParser.parseObject(new FileInputStream(Utilities.path(FhirSettings.getTestIgsPath(), id, "output", "qa.json")));
    JsonObject previous = null;
    if (new File(Utilities.path(FhirSettings.getTestIgsPath(), "expected", id+".json")).exists()) {
      previous = JsonParser.parseObject(new FileInputStream(Utilities.path(FhirSettings.getTestIgsPath(), "expected", id+".json")));
    } else {
      previous = new JsonObject();      
    }
    int cErr = current.hasNumber("errs") ? current.asInteger("errs") : 0;
    int pErr = previous.hasNumber("errs") ? previous.asInteger("errs") : 0;
    int cWarn = current.hasNumber("warnings") ? current.asInteger("warnings") : 0;
    int pWarn = previous.hasNumber("warnings") ? previous.asInteger("warnings") : 0;
    int cHint = current.hasNumber("hints") ? current.asInteger("hints") : 0;
    int pHint = previous.hasNumber("hints") ? previous.asInteger("hints") : 0;

    JsonObject stats = JsonParser.parseObject(statsFile);
    JsonObject ver = stats.forceObject(version);
    ver.set("sync-date", FileUtilities.fileToString(syncDateFile()).trim());
    ver.set("date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
    JsonObject si = ver.forceObject(id);
    si.set("errors", cErr);
    si.set("warnings", cWarn);
    si.set("hints", cHint);
    si.set("time", System.currentTimeMillis() - time);
    si.set("memory", pub.getMaxMemory() - startingMem);
    JsonParser.compose(stats, statsFile, true);
    
    Map<String, Map<String, String>> statsMap = new HashMap<>();
    Set<String> cols = new HashSet<>();
    for (JsonProperty v : stats.getProperties()) {
      if (v.getValue().isJsonObject()) {
        Map<String, String> map = new HashMap<>();
        statsMap.put(v.getName(), map);
        for (JsonProperty ig : ((JsonObject) v.getValue()).getProperties()) {
          if (ig.getValue().isJsonObject()) {
            cols.add(ig.getName());
            map.put(ig.getName(), ((JsonObject) ig.getValue()).asString("time"));
          }
        }
      }
    }
    
    List<String> colNames = Utilities.sorted(cols);
    StringBuilder b = new StringBuilder();
    b.append("Version");
    for (String s : colNames) {
      b.append(",");
      b.append(s);
    }
    b.append("\r\n");
    for (String v : sortedbySemVer(statsMap.keySet())) {
      b.append(v);
      for (String s : colNames) {
        b.append(",");
        String t = statsMap.get(v).get(s);
        b.append(t == null ? "" : t);
      }
      b.append("\r\n");
    }
    FileUtilities.stringToFile(b.toString(), FileUtilities.changeFileExt(statsFile.getAbsolutePath(), ".csv"));
    
    
    Assertions.assertTrue(cErr <= pErr, "Error count has increased from "+pErr+" to "+cErr);
    Assertions.assertTrue(cWarn <= pWarn, "Warning count has increased from "+pWarn+" to "+cWarn);
    Assertions.assertTrue(cHint <= pHint, "Hint count has increased from "+pHint+" to "+cHint);
    System.out.println("=======================================================================================");
    System.out.println("");
    pub = null;

    writeMem("Residual memory");

    dumpMem(id);
  }

  private List<String> sortedbySemVer(Collection<String> set) {
    List<String> list = new ArrayList<>();
    list.addAll(set);
    Collections.sort(list, new VersionUtilities.SemVerSorter());
    return list;
  }

  private long getCurrentmem() {

    Runtime runtime = Runtime.getRuntime();
    long totalMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();
    long usedMemory = totalMemory - freeMemory;
    return usedMemory;
  }

  private void dumpMem(String id) throws IOException {
    File f = new File(Utilities.path("[tmp]", "memory-dump-"+id+".hprof"));
    f.delete();
    System.gc();
    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    com.sun.management.HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(server, "com.sun.management:type=HotSpotDiagnostic", com.sun.management.HotSpotDiagnosticMXBean.class);
    mxBean.dumpHeap(f.getAbsolutePath(), true);
  }

  private void writeMem(String name) {
    System.gc();
    Runtime runtime = Runtime.getRuntime();
    long totalMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();
    long usedMemory = totalMemory - freeMemory;
    System.out.println(name+": "+usedMemory+" of "+runtime.maxMemory()+" ("+((usedMemory * 100) / runtime.maxMemory())+"%)");
  }

  //---- todo: this class is only run by Grahame, so these paths are hard-coded
  
  private File syncDateFile() {
    return new File("/Users/grahamegrieve/work/test-igs/date.txt");
  }

  private File determineStatsFile() {
    return new File("/Users/grahamegrieve/work/ig-pub/test-statistics.json");
  }

  private String readVersion() throws ParserConfigurationException, SAXException, IOException {
    Document doc = XMLUtil.parseFileToDom("/Users/grahamegrieve/work/ig-pub/pom.xml");
    Element root = doc.getDocumentElement();
    Element ver = XMLUtil.getNamedChild(root, "version");
    String version = ver.getTextContent();
    return version.contains("-") ? version.substring(0, version.indexOf("-")) : version;
  }

  private static boolean igsPathExists() {
    return FhirSettings.getTestIgsPath() != null && new File(FhirSettings.getTestIgsPath()).exists();
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

  @Test
  public void testExtensions() throws Exception {
    testIg("hl7.fhir.uv.extensions", null);
  }
  
  @Test
  public void testCDA() throws Exception {
    testIg("hl7.cda.uv.core", null);
  }

  @Test
  public void testDaVinciCRD() throws Exception {
    testIg("hl7.fhir.us.davinci-crd", null);
  }

  @Test
  public void testWHOICVP() throws Exception {
    testIg("smart.who.int.icvp", null);
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
