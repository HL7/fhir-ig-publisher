package org.hl7.fhir.igtools.openehr;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.hl7.fhir.igtools.openehr.ArchetypeImporter.ProcessedArchetype;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.utilities.FileUtilities;
import org.xml.sax.SAXException;

import com.nedap.archie.adlparser.ADLParseException;

public class OpenEHRTest {

  public static void main(String[] args) throws Exception {
    new OpenEHRTest().test();
  }
  
  public void test() throws FileNotFoundException, ADLParseException, IOException, ParserConfigurationException, SAXException {
    ArchetypeImporter ai = new ArchetypeImporter(null, "http://openehr.org/fhir/uv/test");
    ProcessedArchetype pa = ai.importArchetype(new FileInputStream("/Users/grahamegrieve/Downloads/openEHR-EHR-OBSERVATION.blood_pressure.v2.adl"), "openEHR-EHR-OBSERVATION.blood_pressure.v2.adl");
    System.out.println();
    String json = new JsonParser().setOutputStyle(OutputStyle.PRETTY).composeString(pa.getBnd());
    FileUtilities.stringToFile(json, "/Users/grahamegrieve/temp/igs/FHIR-sample-ig#master/input/resources/Bundle-openEHR-EHR-OBSERVATION.blood-pressure.v2.json");
    System.out.println("Done");
  }

}
