package org.hl7.fhir.igtools.publisher;

import java.util.HashMap;
import java.util.Map;

import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.DateTimeType;
import org.hl7.fhir.r5.model.Reference;

public class ProvenanceDetails {

 
  private String path;
  private Coding action;
  private DateTimeType date;
  private String comment;
  private Map<Coding, Reference> actors = new HashMap<>();
  
  
  public ProvenanceDetails() {
    super();
  }
  
  public ProvenanceDetails(Coding action, DateTimeType date, String comment) {
    super();
    this.action = action;
    this.date = date;
    this.comment = comment;
  }
  public Coding getAction() {
    return action;
  }
  public DateTimeType getDate() {
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

  public void setDate(DateTimeType date) {
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
  
}
