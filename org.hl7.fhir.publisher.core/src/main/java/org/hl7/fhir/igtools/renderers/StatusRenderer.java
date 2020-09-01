package org.hl7.fhir.igtools.renderers;

import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.ContactDetail;
import org.hl7.fhir.r5.model.ContactPoint;
import org.hl7.fhir.r5.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r5.model.DomainResource;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.utilities.Utilities;

public class StatusRenderer {

  public static class ResourceStatusInformation {
    String fmm;
    String owner;
    String ownerLink;
    String status;
    String sstatus;
    String colorClass;
    public String getFmm() {
      return fmm;
    }
    public void setFmm(String fmm) {
      this.fmm = fmm;
    }
    public String getOwner() {
      return owner;
    }
    public void setOwner(String owner) {
      this.owner = owner;
    }
    public String getOwnerLink() {
      return ownerLink;
    }
    public void setOwnerLink(String ownerLink) {
      this.ownerLink = ownerLink;
    }
    public String getStatus() {
      return status;
    }
    public void setStatus(String status) {
      this.status = status;
    }
    public String getSstatus() {
      return sstatus;
    }
    public void setSstatus(String sstatus) {
      this.sstatus = sstatus;
    }
    public String getColorClass() {
      return colorClass;
    }
    public void setColorClass(String colorClass) {
      this.colorClass = colorClass;
    }
  }


  public static ResourceStatusInformation analyse(DomainResource resource) {
    ResourceStatusInformation info = new ResourceStatusInformation();
    info.setFmm(readFmmStatus(resource));
    info.setOwner(readOwner(resource));
    info.setOwnerLink(readOwnerLink(resource));
    info.setStatus(readStatus(resource));
    info.setSstatus(readStandardsStatus(resource));
    info.setColorClass(getColor(info));
    return info;
  }


  private static String getColor(ResourceStatusInformation info) {
    if ("Draft".equals(info.getStatus())) {
      return "colsd";
    }
    if (!"Active".equals(info.getStatus())) {
      return "colsd";
    }
    if (info.getSstatus() == null)
      return "0".equals(info.getFmm()) ? "colsd" : "colstu";
    switch (info.getSstatus()) {
    case "Draft": return "colsd";
    case "Trial-Use": return "0".equals(info.getFmm()) ? "colsd" : "colstu"; 
    case "Normative": return "colsn";
    case "Informative": return "colsi";
    case "Exteranl": return "colse";
    default:
      return "colsi";
    }
  }


  private static String readStandardsStatus(DomainResource resource) {
    return ToolingExtensions.readStringExtension(resource, ToolingExtensions.EXT_STANDARDS_STATUS);
  }


  private static String readStatus(DomainResource resource) {
    if (resource instanceof CanonicalResource) {
      return ((CanonicalResource) resource).getStatus().getDisplay();
    }
    return null;
  }


  private static String readOwnerLink(DomainResource resource) {
    if (resource instanceof CanonicalResource) {
      for (ContactDetail cd : ((CanonicalResource) resource).getContact()) {
        for (ContactPoint cp : cd.getTelecom()) {
          if (cp.getSystem() == ContactPointSystem.URL) {
            return cp.getValue();
          }
        }
      }
    }
    return null;
  }


  private static String readOwner(DomainResource resource) {
    if (resource instanceof CanonicalResource) {
      return ((CanonicalResource) resource).getPublisher();
    }
    return null;
  }


  private static String readFmmStatus(DomainResource resource) {
    return ToolingExtensions.readStringExtension(resource, ToolingExtensions.EXT_FMM_LEVEL);
  }


  public static String render(String src, ResourceStatusInformation info) {
    StringBuilder b = new StringBuilder();
    b.append("<table class=\"");
    b.append(info.getColorClass());
    b.append("\"><tr>");
    if (info.getOwnerLink() != null) {
    b.append("<td>Publisher: <a href=\"");
    b.append(info.getOwnerLink());
    b.append("\">");
    b.append(Utilities.escapeXml(info.getOwner()));
    b.append("</a></td><td>");
    } else {
      b.append("<td>Publisher: ");
      b.append(Utilities.escapeXml(info.getOwner()));
      b.append("</td><td>");
    }
    b.append("<a href=\""+src+"/versions.html#maturity\">Status</a>: ");
    b.append(info.getStatus());
    b.append("</td><td>");
    b.append("<a href=\""+src+"/versions.html#maturity\">Maturity Level</a>: ");
    if (info.getFmm() != null) {
      b.append(info.getFmm());
    } else {
      b.append("N/A");      
    }
    b.append("</td><td>");
    b.append("<a href=\""+src+"/versions.html#std-process\">Standards Status</a>: ");
    if (info.getSstatus() != null) {
      b.append(info.getSstatus());
    } else {
      b.append("N/A");      
    }
    b.append("</td></tr></table>\r\n");
    return b.toString();
  }

}
