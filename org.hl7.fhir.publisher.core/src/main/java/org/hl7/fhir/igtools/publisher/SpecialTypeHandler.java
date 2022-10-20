package org.hl7.fhir.igtools.publisher;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.utilities.Utilities;

public class SpecialTypeHandler {

  public static final List<String> SPECIAL_TYPES = Collections.unmodifiableList(Arrays.asList("ActorDefinition"));
  public static final String VERSION = "5.0.0";

  public static boolean handlesType(String type) {
    return Utilities.existsInList(type, SPECIAL_TYPES);
  }

}
