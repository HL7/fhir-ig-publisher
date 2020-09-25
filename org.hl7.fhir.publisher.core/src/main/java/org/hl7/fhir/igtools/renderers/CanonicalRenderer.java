package org.hl7.fhir.igtools.renderers;

import java.util.List;

import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.DateTimeType;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.StandardsStatus;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.cache.NpmPackage;

public class CanonicalRenderer extends BaseRenderer {


  private CanonicalResource cr;
  private String destDir;

  public CanonicalRenderer(IWorkerContext context, String prefix, CanonicalResource cr, String destDir, IGKnowledgeProvider igp, List<SpecMapManager> maps, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen) {
    super(context, prefix, igp, maps, markdownEngine, packge, gen);
    this.cr = cr;
    this.destDir = destDir;
  }

  public String summaryTable(FetchedResource r, boolean xml, boolean json, boolean ttl) throws Exception {
    StringBuilder b = new StringBuilder();
    b.append("<table class=\"grid\">\r\n<tbody>\r\n");
    genSummaryCore1(b);
    genSummaryRowsSpecific(b);
    genSummaryCore2(b, r, xml, json, ttl);
    b.append("</tbody></table>\r\n");

    return b.toString();
  }

  private void genSummaryCore1(StringBuilder b) {
    b.append("<tr><td>"+translate("cr.summary", "Defining URL")+":</td><td>"+Utilities.escapeXml(cr.getUrl())+"</td></tr>\r\n");
    if (cr.hasVersion()) {
      b.append(" <tr><td>"+translate("cs.summary", "Version")+":</td><td>"+Utilities.escapeXml(cr.getVersion())+"</td></tr>\r\n");
    }
    String name = cr.hasName() ? gt(cr.getNameElement()) : null;
    String title = cr.hasTitle() ? gt(cr.getTitleElement()) : null;
    b.append(" <tr><td>"+translate("cr.summary", "Name")+":</td><td>"+Utilities.escapeXml(name)+"</td></tr>\r\n");
    if (title != null && !title.equalsIgnoreCase(name)) {
      b.append(" <tr><td>"+translate("cr.summary", "Title")+":</td><td>"+Utilities.escapeXml(title)+"</td></tr>\r\n");
    }
    b.append(" <tr><td>"+translate("cs.summary", "Status")+":</td><td>"+describeStatus(cr)+"</td></tr>\r\n");
    if (cr.hasDescription()) {
      b.append(" <tr><td>"+translate("cr.summary", "Definition")+":</td><td>"+processMarkdown("description", cr.getDescriptionElement())+"</td></tr>\r\n");
    }
    if (cr.hasPublisher())
      b.append(" <tr><td>"+translate("cr.summary", "Publisher")+":</td><td>"+Utilities.escapeXml(gt(cr.getPublisherElement()))+"</td></tr>\r\n");
    if (cr.hasExtension(ToolingExtensions.EXT_WORKGROUP)) {
      b.append(" <tr><td>"+translate("cr.summary", "Committee")+":</td><td>"+renderCommitteeLink(cr)+"</td></tr>\r\n");
    }
    if (cr.hasCopyright())
      b.append(" <tr><td>"+translate("cr.summary", "Copyright")+":</td><td>"+processMarkdown("copyright", cr.getCopyrightElement())+"</td></tr>\r\n");
    if (ToolingExtensions.hasExtension(cr, ToolingExtensions.EXT_FMM_LEVEL)) {
      // Use hard-coded spec link to point to current spec because DSTU2 had maturity listed on a different page
      b.append(" <tr><td><a class=\"fmm\" href=\"http://hl7.org/fhir/versions.html#maturity\" title=\"Maturity Level\">"+translate("cs.summary", "Maturity")+"</a>:</td><td>"+ToolingExtensions.readStringExtension(cr, ToolingExtensions.EXT_FMM_LEVEL)+"</td></tr>\r\n");
    }    
  }

  protected void genSummaryRowsSpecific(StringBuilder b) {
    // Nothing    
  }

  private void genSummaryCore2(StringBuilder b, FetchedResource r, boolean xml, boolean json, boolean ttl) {
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
    String sfx = " as of "+new DataRenderer(context).display(dt);
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


}
