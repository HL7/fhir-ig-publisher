package org.hl7.fhir.igtools.openehr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hl7.fhir.model.core.DataType;
import org.hl7.fhir.model.core.DateTimeType;
import org.hl7.fhir.model.core.DateType;
import org.hl7.fhir.model.core.Duration;
import org.hl7.fhir.model.core.ElementDefinition.ConstraintSeverity;
import org.hl7.fhir.model.core.ElementDefinition.ElementDefinitionConstraintComponent;

public class ArchetypeImportHelper {

  public static ElementDefinitionConstraintComponent createDateConstraint(String name, String id, String sn, String patternConstraint) {
    ElementDefinitionConstraintComponent constraint = new ElementDefinitionConstraintComponent();
    constraint.setKey(name+"-"+id+"-"+sn+"-date-pattern");
    constraint.setSeverity(ConstraintSeverity.ERROR);
    constraint.setHuman("Date must be in "+patternConstraint+" format");
    String regex = patternConstraint.replace("YYYY", "\\d{4}")
                                      .replace("MM", "(0[1-9]|1[0-2])|[1-9])")
                                      .replace("DD", "(0[1-9]|[12][0-9]|3[01])|[1-9])");
    constraint.setExpression("matches('^"+regex+"$')");
    return null;
  }

  public static ElementDefinitionConstraintComponent createDateTimeConstraint(String name, String id, String sn, String patternConstraint) {
    ElementDefinitionConstraintComponent constraint = new ElementDefinitionConstraintComponent();
    constraint.setKey(name+"-"+id+"-"+sn+"-datetime-pattern");
    constraint.setSeverity(ConstraintSeverity.ERROR);
    constraint.setHuman("DateTime must be in "+patternConstraint+" format");
    String regex = patternConstraint.replace("YYYY", "\\d{4}")
                                     .replace("MM", "(0[1-9]|1[0-2])")
                                     .replace("DD", "(0[1-9]|[12][0-9]|3[01])")
                                     .replace("hh", "([01][0-9]|2[0-3])")
                                     .replace("mm", "([0-5][0-9])")
                                     .replace("ss", "([0-5][0-9])");
    constraint.setExpression("matches('^"+regex+"$')");
    return constraint;
  }
  
  public static DataType dateFromString(String iso8601Duration) {
    System.out.println("dateFromString: "+iso8601Duration);
    //TODO: implement this properly, for now just return a DateType with no value
    return new DateType();
  }

  public static DataType dateTimeFromString(String iso8601Duration) {
    System.out.println("dateTimeFromString: "+iso8601Duration);
    if (iso8601Duration == null || iso8601Duration.isEmpty()) {
      throw new IllegalArgumentException("Duration string cannot be null or empty");
    }
    
 // Pattern to parse ISO 8601 duration
    Pattern pattern = Pattern.compile(
            "P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?"
    );
    
    Matcher matcher = pattern.matcher(iso8601Duration);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid ISO 8601 duration format: " + iso8601Duration);
    }
    
    // Extract components
    String years = matcher.group(1);
    String months = matcher.group(2);
    String days = matcher.group(3);
    String hours = matcher.group(4);
    String minutes = matcher.group(5);
    String seconds = matcher.group(6);
    
 // Find the most significant (first non-zero) component and return as Quantity
    if (years != null) {
      return new DateTimeType().setYear(Integer.parseInt(years));
    }
    if (months != null) {
      return new DateTimeType().setMonth(Integer.parseInt(months));
    }
    if (days != null) {
      return new DateTimeType().setDay(Integer.parseInt(days));
    }
    if (hours != null) {
      return new DateTimeType().setHour(Integer.parseInt(hours));
    }
    if (minutes != null) {
      return new DateTimeType().setMinute(Integer.parseInt(minutes));
    }
    if (seconds != null) {
      return new DateTimeType().setSecond(Integer.parseInt(seconds));
    }

    // Default to 0 seconds
    return new DateTimeType().setSecond(0);
  }

  public static DataType durationFromPeriod(String iso8601Duration) {
    System.out.println("durationFromPeriod: "+iso8601Duration);
    if (iso8601Duration == null || iso8601Duration.isEmpty()) {
      throw new IllegalArgumentException("Duration string cannot be null or empty");
    }

    // Pattern to parse ISO 8601 duration
    Pattern pattern = Pattern.compile(
            "P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?"
    );

    Matcher matcher = pattern.matcher(iso8601Duration);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid ISO 8601 duration format: " + iso8601Duration);
    }

    // Extract components
    String years = matcher.group(1);
    String months = matcher.group(2);
    String weeks = matcher.group(3);
    String days = matcher.group(4);
    String hours = matcher.group(5);
    String minutes = matcher.group(6);
    String seconds = matcher.group(7);

    // Find the most significant (first non-zero) component and return as Quantity
    if (years != null) {
      return new Duration().setValue(Long.parseLong(years)).setUnit("a").setSystem("http://unitsofmeasure.org").setCode("a");
    }
    if (months != null) {
      return new Duration().setValue(Long.parseLong(months)).setUnit("mo").setSystem("http://unitsofmeasure.org").setCode("mo");
    }
    if (weeks != null) {
      return new Duration().setValue(Long.parseLong(weeks)).setUnit("wk").setSystem("http://unitsofmeasure.org").setCode("wk");
    }
    if (days != null) {
      return new Duration().setValue(Long.parseLong(days)).setUnit("d").setSystem("http://unitsofmeasure.org").setCode("d");
    }
    if (hours != null) {
      return new Duration().setValue(Long.parseLong(hours)).setUnit("h").setSystem("http://unitsofmeasure.org").setCode("h");
    }
    if (minutes != null) {
      return new Duration().setValue(Long.parseLong(minutes)).setUnit("min").setSystem("http://unitsofmeasure.org").setCode("min");
    }
    if (seconds != null) {
      return new Duration().setValue(Double.parseDouble(seconds)).setUnit("s").setSystem("http://unitsofmeasure.org").setCode("s");
    }

    // Default to 0 seconds
    return new Duration().setValue(0).setUnit("s").setSystem("http://unitsofmeasure.org").setCode("s");
  }
}
