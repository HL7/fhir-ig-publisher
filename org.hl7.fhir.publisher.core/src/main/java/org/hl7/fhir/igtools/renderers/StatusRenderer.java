package org.hl7.fhir.igtools.renderers;

import java.util.List;

import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.ContactDetail;
import org.hl7.fhir.r5.model.ContactPoint;
import org.hl7.fhir.r5.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r5.model.DomainResource;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.IntegerType;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.utilities.Utilities;

public class StatusRenderer {

  public static class ResourceStatusInformation {
    String fmm;
    String fmmSupport;
    String owner;
    String ownerLink;
    String status;
    String sstatus;
    String sstatusSupport;
    String normVersion;
    String colorClass;
    public String getFmm() {
      return fmm;
    }
    public void setFmm(String fmm) {
      this.fmm = fmm;
    }
    public String getFmmSupport() {
      return fmmSupport;
    }
    public void setFmmSupport(String fmmSupport) {
      this.fmmSupport = fmmSupport;
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
    public String getSstatusSupport() {
      return sstatusSupport;
    }
    public void setSstatusSupport(String sstatusSupport) {
      this.sstatusSupport = sstatusSupport;
    }
    public String getNormVersion() {
      return normVersion;
    }
    public void setNormVersion(String normVersion) {
      this.normVersion = normVersion;
    }
    public String getColorClass() {
      return colorClass;
    }
    public void setColorClass(String colorClass) {
      this.colorClass = colorClass;
    }
    public void processFmm(DomainResource resource) {
      if (ExtensionUtilities.hasExtension(resource, ExtensionDefinitions.EXT_FMM_LEVEL)) {
        setFmm(ExtensionUtilities.readStringExtension(resource, ExtensionDefinitions.EXT_FMM_LEVEL));
        IntegerType fmm = resource.getExtensionByUrl(ExtensionDefinitions.EXT_FMM_LEVEL).getValueIntegerType();
        if (resource.hasExtension(ExtensionDefinitions.EXT_FMM_SUPPORT))
          setFmmSupport(resource.getExtensionByUrl(ExtensionDefinitions.EXT_FMM_SUPPORT).getValueStringType().getValue());
        else if (fmm.hasExtension(ExtensionDefinitions.EXT_FMM_DERIVED)) {
          List<Extension> derivations = fmm.getExtensionsByUrl(ExtensionDefinitions.EXT_FMM_DERIVED);
          String s = "Inherited from ";
          for (Extension ex: derivations) {
            s += ", " + ex.getValueCanonicalType();
          }
          setFmmSupport(s);
        }
      }
    }
    public void processFmm(Element resource) {
      if (resource.hasExtension(ExtensionDefinitions.EXT_FMM_LEVEL)) {
        setFmm(resource.getExtensionString(ExtensionDefinitions.EXT_FMM_LEVEL));
        Element fmm = resource.getExtension(ExtensionDefinitions.EXT_FMM_LEVEL).getNamedChild("value");
        if (fmm.hasExtension(ExtensionDefinitions.EXT_FMM_SUPPORT))
          setFmmSupport(fmm.getExtensionString(ExtensionDefinitions.EXT_FMM_SUPPORT));
        else if (fmm.hasExtension(ExtensionDefinitions.EXT_FMM_DERIVED)) {
          List<Element> derivations = fmm.getExtensions(ExtensionDefinitions.EXT_FMM_DERIVED);
          String s = "Inherited from ";
          for (Element ex: derivations) {
            s += ", " + ex.primitiveValue();
          }
          setFmmSupport(s);
        }
      }
    }
    public void processSStatus(DomainResource resource) {
      if (ExtensionUtilities.hasExtension(resource, ExtensionDefinitions.EXT_STANDARDS_STATUS)) {
        setSstatus(ExtensionUtilities.readStringExtension(resource, ExtensionDefinitions.EXT_STANDARDS_STATUS));
        StringType sstatus = resource.getExtensionByUrl(ExtensionDefinitions.EXT_STANDARDS_STATUS).getValueStringType();
        if (resource.hasExtension(ExtensionDefinitions.EXT_STANDARDS_STATUS_REASON))
          setSstatusSupport(resource.getExtensionByUrl(ExtensionDefinitions.EXT_STANDARDS_STATUS_REASON).getValueStringType().getValue());
        else if (sstatus.hasExtension(ExtensionDefinitions.EXT_FMM_DERIVED)) {
          List<Extension> derivations = sstatus.getExtensionsByUrl(ExtensionDefinitions.EXT_FMM_DERIVED);
          String s = "Inherited from ";
          for (Extension ex: derivations) {
            s += ", " + ex.getValueCanonicalType();
          }
          setFmmSupport(s);
        }
      }
    }
    
    public void processSStatus(Element resource) {
      if (resource.hasExtension(ExtensionDefinitions.EXT_STANDARDS_STATUS)) {
        setSstatus(resource.getExtensionString(ExtensionDefinitions.EXT_STANDARDS_STATUS));
        Element sstatus = resource.getExtension(ExtensionDefinitions.EXT_STANDARDS_STATUS).getNamedChild("value");
        if (sstatus.hasExtension(ExtensionDefinitions.EXT_FMM_SUPPORT))
          setFmmSupport(sstatus.getExtensionString(ExtensionDefinitions.EXT_FMM_SUPPORT));
        else if (sstatus.hasExtension(ExtensionDefinitions.EXT_FMM_DERIVED)) {
          List<Element> derivations = sstatus.getExtensions(ExtensionDefinitions.EXT_FMM_DERIVED);
          String s = "Inherited from ";
          for (Element ex: derivations) {
            s += ", " + ex.primitiveValue();
          }
          setFmmSupport(s);
        }
      }
    }
  }

  public static ResourceStatusInformation analyse(DomainResource resource) {
    ResourceStatusInformation info = new ResourceStatusInformation();
    info.processFmm(resource);
    info.setOwner(readOwner(resource));
    info.setOwnerLink(readOwnerLink(resource));
    info.setStatus(readStatus(resource));
    info.processSStatus(resource);
    info.setNormVersion(readNormativeVersion(resource));
    info.setColorClass(getColor(info));
    return info;
  }

  public static ResourceStatusInformation analyse(Element resource) {
    ResourceStatusInformation info = new ResourceStatusInformation();
    info.processFmm(resource);
    info.setOwner(readOwner(resource));
    info.setOwnerLink(readOwnerLink(resource));
    info.setStatus(readStatus(resource));
    info.processSStatus(resource);
    info.setNormVersion(readNormativeVersion(resource));
    info.setColorClass(getColor(info));
    return info;
  }

  private static String getColor(ResourceStatusInformation info) {
	  return getColor(info.getStatus(), info.getSstatus(), info.getFmm());
  }
  
  public static String getColor(String status, String sStatus, String fmm) {
    if (sStatus != null) {
      switch (sStatus.toLowerCase()) {
        case "draft": return "colsd";
        case "trial-use": return "0".equals(fmm) ? "colsd" : "colstu"; 
        case "normative": return "colsn";
        case "informative": return "colsi";
        case "deprecated": return "colsdp";
        case "external": return "colse";
      }
    }
    if (fmm != null) {
      return "0".equals(fmm) ? "colsd" : "colstu";
    }
    if (status != null) {
      switch (status.toLowerCase()) {
        case "draft": return "colsd";
        case "retired": return "colsdp"; 
      }
    }
    return "colsi";
  }


  private static String readStandardsStatus(DomainResource resource) {
    return ExtensionUtilities.readStringExtension(resource, ExtensionDefinitions.EXT_STANDARDS_STATUS);
  }

  
  private static String readNormativeVersion(DomainResource resource) {
    return ExtensionUtilities.readStringExtension(resource, ExtensionDefinitions.EXT_NORMATIVE_VERSION);
  }

  private static String readNormativeVersion(Element resource) {
    return resource.getExtensionString(ExtensionDefinitions.EXT_NORMATIVE_VERSION);
  }


  private static String readStatus(DomainResource resource) {
    if (resource instanceof CanonicalResource) {
      return ((CanonicalResource) resource).getStatus().getDisplay();
    }
    return null;
  }
  
  private static String readStatus(Element resource) {
    return resource.getNamedChildValue("status");
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

  private static String readOwnerLink(Element resource) {
    for (Element cd : resource.getChildren("contact")) {
      for (Element cp : cd.getChildren("telecom")) {
        if ("url".equals(cp.getNamedChildValue("system"))) {
          return cp.getNamedChildValue("value");
        }
      }
    }
    return null;
  }

  private static String readOwner(DomainResource resource) {
    if (resource instanceof CanonicalResource) {
      return ((CanonicalResource) resource).hasPublisher() ? ((CanonicalResource) resource).getPublisher() : null;
    }
    return null;
  }

  private static String readOwner(Element resource) {
    return resource.hasChild("publisher") ? resource.getNamedChildValue("publisher") : null;
  }


  public static String render(String src, ResourceStatusInformation info, RenderingContext rc) {
    StringBuilder b = new StringBuilder();
    b.append("<table role=\"presentation\" class=\" data-fhir=\"generated-heirarchy\"");
    b.append(info.getColorClass());
    b.append("\"><tr>");
    String pub = rc.formatPhrase(RenderingContext.MATURITY_PUBLISHER, Utilities.escapeXml(info.getOwner()));
    if (info.getOwnerLink() != null) {
      b.append("<td><a href=\"");
      b.append(checkLink(info.getOwnerLink()));
      b.append("\">");
      b.append(pub);
      b.append("</a></td><td>");
    } else {
      b.append("<td>");
      b.append(pub);
      b.append("</td><td>");
    }
    b.append("<a href=\""+src+"/versions.html#maturity\">"+rc.formatPhrase(RenderingContext.MATURITY_STATUS, info.getStatus())+"</a>: ");
    b.append("</td><td>");
    b.append("<a href=\""+src+"/versions.html#maturity\">"+rc.formatPhrase(RenderingContext.MATURITY_MATURITY, 
        info.getFmm() != null ? info.getFmm() : rc.formatPhrase(RenderingContext._NA))+"</a>");
    b.append("</td><td>");
    b.append("<a href=\""+src+"/versions.html#std-process\">"+rc.formatPhrase(RenderingContext.MATURITY_STDS_STATUS, 
        info.getFmm() != null ? info.getSstatus() : rc.formatPhrase(RenderingContext._NA))+"</a>");
    b.append("</td></tr></table>\r\n");
    return b.toString();
  }

  private static Object checkLink(String link) {
    if (Utilities.isAbsoluteUrlLinkable(link)) {
      return link;
    }
    if (link.startsWith("www.")) {
      return "http://"+link;
    }
    if (link.contains("@") && !link.startsWith("mailto:")) {
      return "http://"+link;
    }
    return link;
  }

}
