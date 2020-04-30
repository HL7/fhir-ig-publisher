package org.hl7.fhir.igtools.publisher;

import org.hl7.fhir.r5.model.AuditEvent.AuditEventAction;

import java.util.HashMap;
import java.util.Map;

import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.DateTimeType;

public class AuditRecord {

  public class AuditEventActor {
    private String display;
    private String reference;
    
    public AuditEventActor(String display, String reference) {
      super();
      this.display = display;
      this.reference = reference;
    }
    public String getDisplay() {
      return display;
    }
    public String getReference() {
      return reference;
    }
  }
  private String path;
  private AuditEventAction action;
  private DateTimeType date;
  private String comment;
  private Map<Coding, AuditEventActor> actors = new HashMap<>();
  
  
  public AuditRecord() {
    super();
  }
  
  public AuditRecord(AuditEventAction action, DateTimeType date, String comment) {
    super();
    this.action = action;
    this.date = date;
    this.comment = comment;
  }
  public AuditEventAction getAction() {
    return action;
  }
  public DateTimeType getDate() {
    return date;
  }
  public String getComment() {
    return comment;
  }
  public Map<Coding, AuditEventActor> getActors() {
    return actors;
  }

  public void setAction(AuditEventAction action) {
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
