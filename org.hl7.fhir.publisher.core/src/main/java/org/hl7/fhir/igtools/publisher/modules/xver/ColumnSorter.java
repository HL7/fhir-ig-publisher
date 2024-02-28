package org.hl7.fhir.igtools.publisher.modules.xver;

import java.util.Comparator;

public class ColumnSorter implements Comparator<StructureDefinitionColumn> {

  @Override
  public int compare(StructureDefinitionColumn o1, StructureDefinitionColumn o2) {
    String s1 = o1.getSd().getFhirVersion().toCode()+":"+o1.getSd().getName();
    String s2 = o2.getSd().getFhirVersion().toCode()+":"+o2.getSd().getName();
    return s1.compareTo(s2);
  }

}