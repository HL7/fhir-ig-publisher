package org.hl7.fhir.igtools.publisher.xig;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;

import org.hl7.fhir.convertors.analytics.PackageVisitor;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.xml.sax.SAXException;

public class XIGGenerator {

  private static final String SNOMED_EDITION = "900000000000207008"; // international

  private String target;
  private String cache;
  private FilesystemPackageCacheManager pcm;

  private String date;
    
  public static void main(String[] args) throws Exception {
    new XIGGenerator(args[0], args[1]).execute(Integer.parseInt(args[3]));
  }

  public XIGGenerator(String target, String cache) throws FHIRException, IOException, URISyntaxException {
    super();
    this.target = target;
    this.cache = cache;
    Utilities.createDirectory(cache);
    pcm = new FilesystemPackageCacheManager.Builder().build();
    String ds = new SimpleDateFormat("dd MMM yyyy", new Locale("en", "US")).format(Calendar.getInstance().getTime());
    String dl = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ", new Locale("en", "US")).format(Calendar.getInstance().getTime());
    date = "<span title=\""+dl+"\">"+ds+"</span>";    
  }
  
  public void execute(int step) throws IOException, ParserConfigurationException, SAXException, FHIRException, EOperationOutcome, ParseException {
    ProfileUtilities.setSuppressIgnorableExceptions(true);

    long ms = System.currentTimeMillis();

    System.out.println("Start Step "+step);

    File tgt = new File(target);
    if (step == 1 || step == 0) {
      tgt.delete();
    }

    PackageVisitor pv = new PackageVisitor();
    
    pv.getResourceTypes().add("ActivityDefinition");
    pv.getResourceTypes().add("ActorDefinition");
    pv.getResourceTypes().add("CapabilityStatement");
    pv.getResourceTypes().add("CodeSystem");
    pv.getResourceTypes().add("ConceptMap");
    pv.getResourceTypes().add("ConditionDefinition");
    pv.getResourceTypes().add("DeviceDefinition");
    pv.getResourceTypes().add("EventDefinition");
    pv.getResourceTypes().add("ExampleScenario");
    pv.getResourceTypes().add("GraphDefinition");
    pv.getResourceTypes().add("Group");
    pv.getResourceTypes().add("ImplementationGuide");
    pv.getResourceTypes().add("Measure");
    pv.getResourceTypes().add("MeasureReport");
    pv.getResourceTypes().add("Medication");
    pv.getResourceTypes().add("MessageDefinition");
    pv.getResourceTypes().add("NamingSystem");
    pv.getResourceTypes().add("ObservationDefinition");
    pv.getResourceTypes().add("OperationDefinition");
    pv.getResourceTypes().add("PlanDefinition");
    pv.getResourceTypes().add("Questionnaire");
    pv.getResourceTypes().add("Requirements");
    pv.getResourceTypes().add("SearchParameter");
    pv.getResourceTypes().add("SpecimenDefinition");
    pv.getResourceTypes().add("StructureDefinition");
    pv.getResourceTypes().add("StructureMap");
    pv.getResourceTypes().add("TerminologyCapabilities");
    pv.getResourceTypes().add("TestPlan");
    pv.getResourceTypes().add("TestReport");
    pv.getResourceTypes().add("TestScript");
    pv.getResourceTypes().add("ValueSet");

    pv.setCache(cache);
    pv.setOldVersions(false);
    pv.setCorePackages(true);
    XIGDatabaseBuilder gather = new XIGDatabaseBuilder(target, step == 1 || step == 0, new SimpleDateFormat("dd MMM yyyy", new Locale("en", "US")).format(Calendar.getInstance().getTime()));
    pv.setProcessor(gather);
    pv.setCurrent(true);
    pv.setStep(step);
    pv.visitPackages();
    gather.finish(step == 0 || step == 3);

    System.out.println("Finished Step "+step+": "+Utilities.describeDuration(System.currentTimeMillis() - ms));
    System.out.println("File "+target+", size is "+Utilities.describeSize(tgt.length()));
  }
//
//  private void test(File tgt) {
//    try {
//      Connection con = DriverManager.getConnection("jdbc:sqlite:"+tgt.getAbsolutePath());
//      Statement q = con.createStatement();
//      q.execute("select JsonR5 from Contents where ResourceKey = 228");
//      byte[] cnt = q.getResultSet().getBytes(1);
//      cnt = XIGDatabaseBuilder.unGzip(cnt);
//      String s = new String(cnt);
//      System.out.println(s);
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
//    throw new Error("Done");
//    
//  }

}
