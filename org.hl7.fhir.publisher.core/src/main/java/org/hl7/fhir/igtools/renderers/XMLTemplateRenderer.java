package org.hl7.fhir.igtools.renderers;

import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.formats.IParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.Utilities;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class XMLTemplateRenderer {


  public String generate(StructureDefinition sd) throws IOException, Exception {
    StringBuilder b = new StringBuilder();
    b.append("<pre class=\"spec\">\r\n");

    generateInner(b, sd);

    b.append("</pre>\r\n");
    return b.toString();
  }

  private void generateInner(StringBuilder b, StructureDefinition sd) throws IOException, Exception {
    ElementDefinition root = sd.getSnapshot().getElement().get(0);
    b.append("&lt;!-- "+Utilities.escapeXml(sd.getName())+" -->");
//    b.append("<span style=\"float: right\"><a title=\"Documentation for this format\" href=\""+prefix+"xml.html\"><img src=\""+prefix+"help.png\" alt=\"doco\"/></a></span>\r\n");
    String rn = sd.getSnapshot().getElement().get(0).getPath();
//
//    b.append("\r\n&lt;");
//    if (defPage == null)
//      b.append("<span title=\"" + Utilities.escapeXml(root.getDefinition())
//              + "\"><b>");
//    else
//      b.append("<a href=\"" + (defPage + "#" + root.getSliceName()) + "\" title=\""
//              + Utilities.escapeXml(root.getDefinition())
//              + "\" class=\"dict\"><b>");
//    b.append(rn);
//    if ((defPage == null))
//      b.append("</b></span>");
//    else
//      b.append("</b></a>");
//
//    b.append(" xmlns=\"http://hl7.org/fhir\"\r\n&gt;\r\n");
//
//    List<ElementDefinition> children = getChildren(sd.getSnapshot().getElement(), sd.getSnapshot().getElement().get(0));
//    boolean complex = isComplex(children);
//    if (!complex)
//      b.append("  &lt;!-- from Element: <a href=\""+prefix+"extensibility.html\">extension</a> -->\r\n");
//    for (ElementDefinition child : children)
//      generateCoreElem(sd.getSnapshot().getElement(), child, 1, rn, false, complex);

    b.append("&lt;/");
    b.append(rn);
    b.append("&gt;\r\n");
  }


  private List<ElementDefinition> getChildren(List<ElementDefinition> elements, ElementDefinition elem) {
    int i = elements.indexOf(elem)+1;
    List<ElementDefinition> res = new ArrayList<ElementDefinition>();
    while (i < elements.size()) {
      String tgt = elements.get(i).getPath();
      String src = elem.getPath();
      if (tgt.startsWith(src+".")) {
        if (!tgt.substring(src.length()+1).contains("."))
          res.add(elements.get(i));
      } else
        return res;
      i++;
    }
    return res;
  }
}
