package org.hl7.fhir.igtools.renderers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.model.core.CanonicalResource;
import org.hl7.fhir.model.core.Resource;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

/**
 * Finds the content in the IG that is marked as experimental, for the QA page qa-experimental.html
 * (linked from qa.html).
 *
 * Only the IG's own resources are scanned - dependencies are not.
 */
public class ExperimentalContentRenderer {

  public static final String PAGE_NAME = "qa-experimental.html";

  private final List<FetchedResource> experimental = new ArrayList<>();
  private boolean igIsExperimental;
  private int count;

  public void scan(List<FetchedFile> files) {
    for (FetchedFile f : files) {
      for (FetchedResource r : f.getResources()) {
        count++;
        if (isExperimental(r)) {
          if ("ImplementationGuide".equals(r.fhirType())) {
            igIsExperimental = true;
          }
          experimental.add(r);
        }
      }
    }
    experimental.sort(Comparator.comparing((FetchedResource r) -> r.fhirType()).thenComparing(r -> r.getId() == null ? "" : r.getId()));
  }

  private boolean isExperimental(FetchedResource r) {
    Resource res = r.getResource();
    if (res instanceof CanonicalResource) {
      return ((CanonicalResource) res).getExperimental();
    }
    Element e = r.getElement();
    if (e == null || !e.hasChild("experimental")) {
      return false;
    }
    return "true".equals(e.getNamedChildValue("experimental"));
  }

  /**
   * Whether the IG marks the resource as an example - either isExample = true, or as an example of a profile
   */
  private void renderExample(XhtmlNode td, FetchedResource r) {
    if (!r.isExample()) {
      td.tx("No");
    } else if (r.getExampleUri() != null) {
      td.tx("Yes, of ");
      td.code().tx(r.getExampleUri());
    } else {
      td.tx("Yes");
    }
  }

  public boolean hasExperimentalContent() {
    return !experimental.isEmpty();
  }

  /**
   * @return the text of the link in the qa.html summary table
   */
  public String summary() {
    if (experimental.isEmpty()) {
      return "(none)";
    } else {
      return (igIsExperimental ? "IG is experimental; " : "")+experimental.size()+" experimental "+Utilities.pluralize("resource", experimental.size());
    }
  }

  public XhtmlNode render() {
    XhtmlNode x = new XhtmlNode(NodeType.Element, "div");
    if (experimental.isEmpty()) {
      x.para().tx("None of the content in this IG is marked as experimental ("+count+" "+Utilities.pluralize("resource", count)+" checked).");
    } else {
      if (igIsExperimental) {
        x.para().b().tx("The ImplementationGuide itself is marked as experimental.");
      }
      x.para().tx(experimental.size()+" of the "+count+" "+Utilities.pluralize("resource", count)+" in this IG "+(experimental.size() == 1 ? "is" : "are")+" marked as experimental:");
      XhtmlNode tbl = x.table("grid", false);
      XhtmlNode tr = tbl.tr();
      tr.th().tx("Resource");
      tr.th().tx("Name");
      tr.th().tx("URL");
      tr.th().tx("Status");
      tr.th().tx("Example");
      for (FetchedResource r : experimental) {
        tr = tbl.tr();
        String label = r.fhirType()+"/"+r.getId();
        if (r.getPath() != null) {
          tr.td().ah(r.getPath()).tx(label);
        } else {
          tr.td().tx(label);
        }
        if (r.getResource() instanceof CanonicalResource) {
          CanonicalResource cr = (CanonicalResource) r.getResource();
          tr.td().tx(cr.present());
          tr.td().code().tx(cr.getUrl());
          tr.td().tx(cr.hasStatus() ? cr.getStatus().toCode() : "");
        } else {
          Element e = r.getElement();
          tr.td().tx(Boolean.TRUE.equals(r.hasTitle()) ? r.getTitle() : e.getNamedChildValue("name"));
          String url = e.getNamedChildValue("url");
          if (url == null) {
            tr.td();
          } else {
            tr.td().code().tx(url);
          }
          String status = e.getNamedChildValue("status");
          tr.td().tx(status == null ? "" : status);
        }
        renderExample(tr.td(), r);
      }
    }
    x.para().i().tx("Note: only the content of this IG is checked; content in dependencies is not.");
    return x;
  }
}
