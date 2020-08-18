package org.hl7.fhir.igtools.publisher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r5.model.BaseDateTimeType;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.Reference;

public class ProvenanceDetails {

 
  public class ProvenanceDetailsTarget {
    private String path;
    private String display;
    
    public ProvenanceDetailsTarget(String path, String display) {
      super();
      this.path = path;
      this.display = display;
    }
    public String getPath() {
      return path;
    }
    public String getDisplay() {
      return display;
    }

  }

  private String path;
  private Coding action;
  private BaseDateTimeType date;
  private String comment;
  private Map<Coding, Reference> actors = new HashMap<>();
  private List<ProvenanceDetailsTarget> targets;
  
  
  public ProvenanceDetails() {
    super();
  }
  
  public ProvenanceDetails(Coding action, BaseDateTimeType date, String comment) {
    super();
    this.action = action;
    this.date = date;
    this.comment = comment;
  }
  public Coding getAction() {
    return action;
  }
  public BaseDateTimeType getDate() {
    return date;
  }
  public String getComment() {
    return comment;
  }
  public Map<Coding, Reference> getActors() {
    return actors;
  }

  public void setAction(Coding action) {
    this.action = action;
  }

  public void setDate(BaseDateTimeType date) {
    this.date = date;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public List<ProvenanceDetailsTarget> getTargets() {
    if (targets == null) {
      targets = new ArrayList<>();
    }
    return targets;
  }
  
}
