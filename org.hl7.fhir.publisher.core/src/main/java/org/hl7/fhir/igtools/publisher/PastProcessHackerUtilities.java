package org.hl7.fhir.igtools.publisher;

public class PastProcessHackerUtilities {


  public static String actualUrl(String canonical) {
    if ("http://fhir-registry.smarthealthit.org".equals(canonical)) {
      return "http://hl7.org/fhir/smart-app-launch";
    }
    return canonical;
  }

  
}
