package org.hl7.fhir.igtools.publisher;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;

public class SpecialTypeHandler {

  public static final List<String> SPECIAL_TYPES_4B = Collections.unmodifiableList(Arrays.asList("ActorDefinition", "Requirements", "TestPlan"));
  public static final List<String> SPECIAL_TYPES_OTHER = Collections.unmodifiableList(Arrays.asList("ActorDefinition", "Requirements", "SubscriptionTopic", "TestPlan"));
  
  public static final String VERSION = "5.0.0";

  public static List<String> specialTypes(String version) {
    if (VersionUtilities.isR4BVer(version)) {
      return SPECIAL_TYPES_4B;
    } else {
      return SPECIAL_TYPES_OTHER;
    }
  }
  
  public static boolean handlesType(String type, String version) {
    return Utilities.existsInList(type, specialTypes(version));
  }

}
