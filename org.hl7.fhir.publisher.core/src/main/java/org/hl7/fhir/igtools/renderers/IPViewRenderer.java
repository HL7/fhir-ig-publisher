package org.hl7.fhir.igtools.renderers;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.HTMLInspector.ExternalReference;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlDocument;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;

public class IPViewRenderer {

  private Map<String, String> csList;
  private List<ExternalReference> references;
  private Map<String, String> copyrights;
  private Map<String, XhtmlNode> images;
  private IWorkerContext context;
  private String ipFragment;
  private String igFragPage;
  
  
  public IPViewRenderer(Map<String, String> csList, List<ExternalReference> references, Map<String, XhtmlNode> images, Map<String, String> copyrights, String ipFragment, String igFragPage, IWorkerContext context) {
    super();
    this.csList = csList;
    this.references = references;
    this.images = images;    
    this.copyrights = copyrights;
    this.context = context;
    this.ipFragment = ipFragment;
    this.igFragPage = igFragPage;
  }


  public String execute() throws IOException {
    XhtmlNode doc = new XhtmlNode(NodeType.Document);
    XhtmlNode html = doc.addTag("html");
    XhtmlNode head = html.addTag("head");
    head.addTag("title").tx("IP Review");
    head.link("stylesheet", "fhir.css");
    XhtmlNode body = html.addTag("body");
    body.h2().tx("Unattributed Code Systems");
    XhtmlNode ul = body.ul();
    for (String s : Utilities.sorted(csList.keySet())) {
      ul.li().ah(csList.get(s)).code().tx(s);
    }
    body.h2().tx("Copyright Fragment");
    if (igFragPage == null) {
      body.para().style("color: maroon").b().tx("This fragment is not visible to the reader");
    } else {
      var p = body.para();
      p.tx("This fragment is available on ");
      p.ah(igFragPage).tx(igFragPage);
    }
    try {
      XhtmlDocument cf = new XhtmlParser().parse("<div>"+ipFragment+"</div>", "div");
      body.addChildNodes(cf.getChildNodes());
    } catch (Exception e) {
      body.para().tx("Error parsing IP fragment: "+e.getMessage());
    }

    body.h2().tx("Copyright and Registered Trademark Uses");
    ul = body.ul();
    for (String s : Utilities.sorted(copyrights.keySet())) {
      if (!isRendering(s)) {
        XhtmlNode li = ul.li();
        li.tx(s);
        li.tx(" (");
        li.ah(copyrights.get(s)).tx("src");
        li.tx(")");      
      }
    }
    body.h2().tx("External References");
    XhtmlNode tbl = body.table("grid");
    XhtmlNode tr = tbl.tr();
    tr.th().b().tx("Type");
    tr.th().b().tx("Reference");
    tr.th().b().tx("Content");
    for (ExternalReference exr : references) {
      tr = tbl.tr();
      tr.td().ah(exr.getSource()).tx(exr.getType().toString().toLowerCase());
      tr.td().ah(exr.getUrl()).tx(host(exr.getUrl()));
      tr.td().addChildNodes(exr.getXhtml().getChildNodes());
    }
    
    body.h2().tx("Internal Images");
    tbl = body.table("grid");
    for (String src : Utilities.sorted(images.keySet())) {
      XhtmlNode td = tbl.tr().td();
      td.tx(src);
      td.br();
      XhtmlNode img = td.img(src, src);
      img.attribute("width", "600px");
      img.attribute("height", "auto");
//      String w = images.get(src).getAttribute("width");
//      String h = images.get(src).getAttribute("height");
//      if (w != null && w.endsWith("px")) {
//        img.attribute("width", w);
//      } 
//      if (h != null && h.endsWith("px")) {
//        img.attribute("height", h);
//      }
    }
    return new XhtmlComposer(false, true).compose(doc);
  }


  private boolean isRendering(String s) {
    if (s.contains("xmlns=\"http://hl7.org/fhir\"")) {
      return true;
    }
    if (s.contains("<http://www.w3.org/2001/XMLSchema#>")) {
      return true;
    }
    if (s.contains("\"resourceType\" : \"")) {
      return true;
    }
    return false;
  }


  private String host(String url) {
    try {
      URL u = new URL(url);
      return u.getHost();
    } catch (Exception e) {
      return url;
    }
  }

}
