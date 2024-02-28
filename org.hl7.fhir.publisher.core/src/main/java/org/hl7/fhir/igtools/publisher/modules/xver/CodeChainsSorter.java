package org.hl7.fhir.igtools.publisher.modules.xver;

import java.util.Comparator;
import java.util.List;

import org.hl7.fhir.r5.terminologies.ConceptMapUtilities;

public class CodeChainsSorter implements Comparator<List<ElementDefinitionLink>> {

  @Override
  public int compare(List<ElementDefinitionLink> o1, List<ElementDefinitionLink> o2) {

    return maxSize(o1) - maxSize(o2);
  }

  private int maxSize(List<ElementDefinitionLink> list) {

    int i = 0;
    for (ElementDefinitionLink link : list) {
      if (link.getNextCM() != null) {
        i = Integer.max(i, ConceptMapUtilities.mapCount(link.getNextCM()));
      }
    }
    return i;
  }

}