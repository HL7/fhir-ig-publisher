package org.hl7.fhir.igtools.renderers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.ContactDetail;
import org.hl7.fhir.r5.model.ContactPoint;
import org.hl7.fhir.r5.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r5.model.DateTimeType;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.NpmPackage;

public class CanonicalRenderer extends BaseRenderer {


  private CanonicalResource cr;
  private String destDir;
  protected String versionToAnnotate;

  public CanonicalRenderer(IWorkerContext context, String corePath, CanonicalResource cr, String destDir, IGKnowledgeProvider igp, List<SpecMapManager> maps, Set<String> allTargets, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen, String versionToAnnotate) {
    super(context, corePath, igp, maps, allTargets, markdownEngine, packge, gen);
    this.cr = cr;
    this.destDir = destDir;
    this.versionToAnnotate = versionToAnnotate;
  }

  public String summaryTable(FetchedResource r, boolean xml, boolean json, boolean ttl, Set<String> rows) throws Exception {
    StringBuilder b = new StringBuilder();
    b.append("<table class=\"grid\">\r\n<tbody>\r\n");
    genSummaryCore1(b, rows);
    genSummaryRowsSpecific(b, rows);
    genSummaryCore2(b, r, xml, json, ttl, rows);
    b.append("</tbody></table>\r\n");

    return b.toString();
  }

  private void genSummaryCore1(StringBuilder b, Set<String> rows) {
    if (hasSummaryRow(rows, "url")) {
      if (cr.hasUrl()) {
        b.append("<tr><td>"+translate("cr.summary", "Defining URL")+":</td><td>"+Utilities.escapeXml(cr.getUrl())+"</td></tr>\r\n");
      } else if (cr.hasExtension("http://hl7.org/fhir/5.0/StructureDefinition/extension-NamingSystem.url")) {
        b.append("<tr><td>"+translate("cr.summary", "Defining URL")+":</td><td>"+Utilities.escapeXml(ToolingExtensions.readStringExtension(cr, "http://hl7.org/fhir/5.0/StructureDefinition/extension-NamingSystem.url"))+"</td></tr>\r\n");      
      } else {
        b.append("<tr><td>"+translate("cr.summary", "Defining URL")+":</td><td></td></tr>\r\n");      
      }
    }
    if (hasSummaryRow(rows, "version")) {
      if (cr.hasVersion()) {
        b.append(" <tr><td>"+translate("cs.summary", "Version")+":</td><td>"+Utilities.escapeXml(cr.getVersion())+"</td></tr>\r\n");
      } else if (cr.hasExtension("http://terminology.hl7.org/StructureDefinition/ext-namingsystem-version")) {
        b.append(" <tr><td>"+translate("cs.summary", "Version")+":</td><td>"+Utilities.escapeXml(ToolingExtensions.readStringExtension(cr, "http://terminology.hl7.org/StructureDefinition/ext-namingsystem-version"))+"</td></tr>\r\n");
      }
    }

    String name = cr.hasName() ? gt(cr.getNameElement()) : null;
    String title = cr.hasTitle() ? gt(cr.getTitleElement()) : null;
    if (hasSummaryRow(rows, "name")) {

      b.append(" <tr><td>"+translate("cr.summary", "Name")+":</td><td>"+Utilities.escapeXml(name)+"</td></tr>\r\n");
    }
    if (hasSummaryRow(rows, "title")) {
      if (title != null && !title.equalsIgnoreCase(name)) {
        b.append(" <tr><td>"+translate("cr.summary", "Title")+":</td><td>"+Utilities.escapeXml(title)+"</td></tr>\r\n");
      }
    }
    if (hasSummaryRow(rows, "status")) {
      b.append(" <tr><td>"+translate("cs.summary", "Status")+":</td><td>"+describeStatus(cr)+"</td></tr>\r\n");
    }
    if (hasSummaryRow(rows, "definition")) {
      if (cr.hasDescription()) {
        b.append(" <tr><td>"+translate("cr.summary", "Definition")+":</td><td>"+processMarkdown("description", cr.getDescriptionElement())+"</td></tr>\r\n");
      }
    }
    if (hasSummaryRow(rows, "publisher")) {
      if (cr.hasPublisher())
        b.append(" <tr><td>"+translate("cr.summary", "Publisher")+":</td><td>"+buildPublisherLinks(cr)+"</td></tr>\r\n");
    }
    if (hasSummaryRow(rows, "committee")) {
      if (cr.hasExtension(ToolingExtensions.EXT_WORKGROUP)) {
        b.append(" <tr><td>"+translate("cr.summary", "Committee")+":</td><td>"+renderCommitteeLink(cr)+"</td></tr>\r\n");
      }
    }
    if (hasSummaryRow(rows, "copyright")) {
      if (cr.hasCopyright())
        b.append(" <tr><td>"+translate("cr.summary", "Copyright")+":</td><td>"+processMarkdown("copyright", cr.getCopyrightElement())+"</td></tr>\r\n");
    }
    if (hasSummaryRow(rows, "maturity")) {
      if (ToolingExtensions.hasExtension(cr, ToolingExtensions.EXT_FMM_LEVEL)) {
        // Use hard-coded spec link to point to current spec because DSTU2 had maturity listed on a different page
        b.append(" <tr><td><a class=\"fmm\" href=\"http://hl7.org/fhir/versions.html#maturity\" title=\"Maturity Level\">"+translate("cs.summary", "Maturity")+"</a>:</td><td>"+ToolingExtensions.readStringExtension(cr, ToolingExtensions.EXT_FMM_LEVEL)+"</td></tr>\r\n");
      }    
    }
  }

  private String buildPublisherLinks(CanonicalResource cr) {
    CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder(". ");
    boolean useName = false;
    for (ContactDetail cd : cr.getContact()) {
      if (!cd.hasName()) {
        useName = true;
      }
    }
    if (!useName) {
      b.append(Utilities.escapeXml(cr.getPublisher()));            
    }    
    for (ContactDetail cd : cr.getContact()) {
      String name = cd.hasName() ? cd.getName() : cr.getPublisher();
      b.append(renderContact(name, cd.getTelecom()));
    }
    return b.toString();
  }

  private String renderContact(String name, List<ContactPoint> telecom) {
    List<String> urls = new ArrayList<>();
    for (ContactPoint t : telecom) {
      if (t.getSystem() == ContactPointSystem.URL && t.hasValue()) {
        urls.add(t.getValue());
      }
    }
    StringBuilder b = new StringBuilder();
    if (urls.size() == 1) {
      b.append("<a href=\""+Utilities.escapeXml(urls.get(0))+"\">"+Utilities.escapeXml(name)+"</a>");
    } else if (urls.size() == 1) {
      b.append(Utilities.escapeXml(name));
    } 
    for (ContactPoint t : telecom) {
      b.append(", ");
      if (t.getSystem() == ContactPointSystem.URL && t.hasValue() && urls.size() > 1) {
        b.append("<a href=\""+Utilities.escapeXml(t.getValue())+"\">Link</a>");
      }
      if (t.getSystem() == ContactPointSystem.EMAIL && t.hasValue()) {
        b.append("<a href=\"mailto:"+Utilities.escapeXml(t.getValue())+"\">Email</a>");
      }
      if (t.getSystem() == ContactPointSystem.PHONE && t.hasValue()) {
        b.append(Utilities.escapeXml(t.getValue()));
      }
      if (t.getSystem() == ContactPointSystem.FAX && t.hasValue()) {
        b.append("Fax:"+Utilities.escapeXml(t.getValue()));
      }
    } 
    return b.toString();
  }

  protected boolean hasSummaryRow(Set<String> rows, String name) {
    return rows.isEmpty() || rows.contains(name);
  }

  protected void genSummaryRowsSpecific(StringBuilder b, Set<String> rows) {
    // Nothing - override in descendents   
  }

  private void genSummaryCore2(StringBuilder b, FetchedResource r, boolean xml, boolean json, boolean ttl, Set<String> rows) {
    if (hasSummaryRow(rows, "formats")) {
      if (xml || json || ttl) {
        b.append(" <tr><td>"+translate("cr.summary", "Source Resource")+":</td><td>");
        boolean first = true;
        String filename = igp.getProperty(r, "format");
        if (filename == null)
          filename = r.fhirType()+"-"+r.getId()+".{{[fmt]}}.html";
        if (xml) {
          first = false;
          b.append("<a href=\""+igp.doReplacements(filename,  r,  null, "xml")+"\">"+translate("cr.summary", "XML")+"</a>");
        }
        if (json) {
          if (first) first = false; else b.append(" / ");
          b.append("<a href=\""+igp.doReplacements(filename,  r,  null, "json")+"\">"+translate("cr.summary", "JSON")+"</a>");
        }
        if (ttl) {
          if (first) first = false; else b.append(" / ");
          b.append("<a href=\""+igp.doReplacements(filename,  r,  null, "ttl")+"\">"+translate("cr.summary", "Turtle")+"</a>");
        }
        b.append("</td></tr>\r\n");
      }    
    }
  }

  protected String describeStatus(CanonicalResource cr) {
    String s = describeStatus(cr.getStatus(), cr.hasExperimental() ? cr.getExperimental() : false, cr.hasDate() ? cr.getDateElement() : null, ToolingExtensions.readBooleanExtension(cr, "http://hl7.org/fhir/StructureDefinition/valueset-deprecated"));
    if (cr.hasExtension(ToolingExtensions.EXT_STANDARDS_STATUS)) {
      s = s + presentStandardsStatus(ToolingExtensions.readStringExtension(cr, ToolingExtensions.EXT_STANDARDS_STATUS));
    }
    return s;
  }

  private String presentStandardsStatus(String code) {
    String pfx = " (<a href=\"http://hl7.org/fhir/codesystem-standards-status.html#"+code+"\">Standards Status</a>: ";
    switch (code) {
    case "draft" : return pfx+"Draft)"; 
    case "normative" : return pfx+"Normative)"; 
    case "trial-use" : return pfx+"Trial Use)"; 
    case "informative" : return pfx+"Informative)"; 
    case "deprecated" : return pfx+"<span style=\"color: maroon; font-weight: bold\">Deprecated</span>)"; 
    case "external" : return pfx+"External)"; 
    }
    return "";
  }

  protected String describeStatus(PublicationStatus status, boolean experimental, DateTimeType dt, Boolean deprecated) {
    String sfx = " as of "+new DataRenderer(gen).display(dt);
    if (deprecated != null && deprecated) {
      if (status == PublicationStatus.RETIRED) {
        return "Deprecated + Retired"+sfx;
      } else {
        return "Deprecated"+sfx; 
      }
    } else {
      switch (status) {
      case ACTIVE: return (experimental ? "Experimental" : "Active")+sfx; 
      case DRAFT: return "Draft"+sfx;
      case RETIRED: return "Retired"+sfx;
      default: return "Unknown"+sfx;
      }
    }
  }

  protected void listResources(StringBuilder b, List<? extends CanonicalResource> list) {
    boolean first = true;
    for (CanonicalResource r : list) {
      if (first) first = false; else b.append(", ");
      String path = r.getWebPath();
      if (path != null) {
        b.append("<a href=\""+Utilities.escapeXml(path)+"\">"+Utilities.escapeXml(r.present())+"</a>");
      } else {
        b.append(Utilities.escapeXml(r.present()));        
      }
    }    
  }

  public String changeSummary() {
    if (versionToAnnotate == null) {
      return "";
    } 

    StringBuilder b = new StringBuilder();
    b.append("<div style=\"background-color: #fff2ff; border-left: solid 3px #ffa0ff; margin: 4px; padding: 4px\">\r\n");
    b.append("<p><b>Changes since version "+versionToAnnotate+":</b></p>\r\n");
    changeSummaryDetails(b);
    b.append("</div>\r\n");
    return b.toString();
  }

  protected void changeSummaryDetails(StringBuilder b) {
    b.append("<ul><li>Not done yet</li></ul>\r\n");    
  }
}
