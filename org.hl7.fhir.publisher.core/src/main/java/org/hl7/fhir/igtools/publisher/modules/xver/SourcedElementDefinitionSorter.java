package org.hl7.fhir.igtools.publisher.modules.xver;

import java.util.Comparator;

public class SourcedElementDefinitionSorter implements Comparator<SourcedElementDefinition> {

  @Override
  public int compare(SourcedElementDefinition o1, SourcedElementDefinition o2) {
    String s1 = o1.toString();
    String s2 = o2.toString();
    return s1.compareTo(s2);
  }

}