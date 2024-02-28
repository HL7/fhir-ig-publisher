package org.hl7.fhir.igtools.publisher.modules.xver;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r5.model.StructureDefinition;

public class StructureDefinitionColumn {
  private StructureDefinition sd;
  private boolean root;
  
  public StructureDefinitionColumn(StructureDefinition sd, boolean root) {
    this.sd = sd;
    this.root = root;
  }
  
  private List<ElementDefinitionPair> elements = new ArrayList<>();
  private List<ColumnEntry> entries = new ArrayList<ColumnEntry>();

  public void clear() {
    entries.clear();
  }

  public int rowCount() {
    int c = 0;
    for (ColumnEntry entry : entries) {
      if (entry.getLink() == null) {
        c = c + 1;
      } else {
        c = c + entry.getLink().getLeftWidth();
      }
    }
    return c;
  }

  public StructureDefinition getSd() {
    return sd;
  }

  public boolean isRoot() {
    return root;
  }

  public List<ElementDefinitionPair> getElements() {
    return elements;
  }

  public List<ColumnEntry> getEntries() {
    return entries;
  }

}