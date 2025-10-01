package org.hl7.fhir.igtools.renderers;

import org.hl7.fhir.r5.formats.IParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.utils.TypesUtilities;
import org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities;
import org.hl7.fhir.utilities.StringPair;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.validation.instance.utils.StructureDefinitionSorterByUrl;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TemplateRenderer {

  public static final String INV_FLAG = "<a style=\"padding-left: 3px; padding-right: 3px; border: 1px maroon solid; font-weight: bold; color: #301212; background-color: #fdeeee;\" href=\"conformance-rules.html#constraints\" title=\"This element has or is affected by some invariants\">I</a>";
  private final StructureDefinition sd;

  private RenderingContext context;
  private String defPage;

  public TemplateRenderer(RenderingContext context, StructureDefinition sd) throws UnsupportedEncodingException {
    this.context = context;
    this.sd = sd;
    this.defPage = getLinkFor(sd.getType());
  }

  protected String getBindingLink(ElementDefinition e) throws Exception {
    ElementDefinition.ElementDefinitionBindingComponent bs = e.getBinding();
    if (bs == null)
      return "terminologies.html#unbound";
    if (bs.getValueSet() == null)
      return "terminologies.html#unbound";
    ValueSet vs = context.getContext().findTxResource(ValueSet.class, bs.getValueSet());
    if (vs == null) {
      return bs.getValueSet();
    } else {
      return vs.getWebPath();
    }
  }

  public String generateXml() throws Exception {
    StringBuilder b = new StringBuilder();
    b.append("<pre class=\"spec\">\r\n");

    generateInnerXml(b, sd.getSnapshot().getElementFirstRep());

    b.append("</pre>\r\n");
    return b.toString();
  }

  private void generateInnerXml(StringBuilder b, ElementDefinition root) throws IOException, Exception {
    List<ElementDefinition> children = context.getProfileUtilities().getChildList(sd, root);

    String rn;
    if (root.getName().equals("Extension")) {
      rn = "extension|modifierExtension";
    } else if (root.getName().equals("Meta")) {
      rn = "meta";
    } else if (sd.getAbstract()) {
      rn = "[name]";
    } else {
      rn = root.getName();
    }

    b.append("&lt;");
    if (defPage == null)
      b.append("<span title=\"" + Utilities.escapeXml(root.getDefinition())
              + "\"><b>");
    else
      b.append("<a href=\"" + (defPage + "#" + root.getName()) + "\" title=\""
              + Utilities.escapeXml(root.getDefinition())
              + "\" class=\"dict\"><b>");
    b.append(rn);
    if ((defPage == null))
      b.append("</b></span>");
    else
      b.append("</b></a>");

    b.append(" xmlns=\"http://hl7.org/fhir\"");
    for (ElementDefinition elem : children) {
      if (elem.hasRepresentation(ElementDefinition.PropertyRepresentation.XMLATTR)) {
        generateAttribute(b, elem);
      }
    }
    boolean resource = sd.getKind() == StructureDefinition.StructureDefinitionKind.RESOURCE;
    if (resource) {
      b.append("&gt; <span style=\"float: right\"><a title=\"Documentation for this format\" href=\"" + "xml.html\"><img src=\"" + "help.png\" alt=\"doco\"/></a></span>\r\n");
    } else {
      b.append("&gt;\r\n");
    }
    if (rn.equals(root.getName()) && resource) {
      if (!Utilities.noString(root.typeSummary())) {
        b.append(" &lt;!-- from <a href=\"" + "resource.html\">Resource</a>: <a href=\"" + "resource.html#id\">id</a>, <a href=\"" + "resource.html#meta\">meta</a>, <a href=\"" + "resource.html#implicitRules\">implicitRules</a>, and <a href=\"" + "resource.html#language\">language</a> -->\r\n");
        if (isDomainResource(sd)) {
          b.append(" &lt;!-- from <a href=\"" + "domainresource.html\">DomainResource</a>: <a href=\"" + "narrative.html#Narrative\">text</a>, <a href=\"" + "references.html#contained\">contained</a>, <a href=\"" + "extensibility.html\">extension</a>, and <a href=\"" + "extensibility.html#modifierExtension\">modifierExtension</a> -->\r\n");
        }
      }
    } else if (root.typeSummary().equals("BackboneElement")) {
      b.append(" &lt;!-- from BackboneElement: <a href=\"" + "extensibility.html\">extension</a>, <a href=\"" + "extensibility.html\">modifierExtension</a> -->\r\n");
    } else {
      b.append(" &lt;!-- from Element: <a href=\"" + "extensibility.html\">extension</a> -->\r\n");
    }
    for (ElementDefinition elem : children) {
      if (!elem.hasRepresentation(ElementDefinition.PropertyRepresentation.XMLATTR)) {
        generateCoreElemXml(b, elem, 1, rn, root.getName(), rn.equals(root.getName()) && resource);
      }
    }

    b.append("&lt;/");
    b.append(rn);
    b.append("&gt;\r\n");
  }

  private boolean isDomainResource(StructureDefinition t) {
    while (t != null) {
      if ("DomainResource".equals(t.getType())) {
        return true;
      }
      t = context.getContext().fetchResource(StructureDefinition.class, t.getBaseDefinition());
    }
    return false;
  }

  private void generateAttribute(StringBuilder b, ElementDefinition elem) throws Exception {
    b.append(" " + elem.getName() + "=\"");

    if (Utilities.isURL(elem.getShort()))
      b.append("<span style=\"color: navy\"><a href=\"" + Utilities.escapeXml(elem.getShort()) + "\">" + Utilities.escapeXml(elem.getShort()) + "</a></span>");
    else
      b.append("<span style=\"color: navy\">" + Utilities.escapeXml(elem.getShort()) + "</span>");
    String t = elem.typeSummary();
    b.append(" (<span style=\"color: darkgreen\"><a href=\"" + (getLinkFor(t) + ".html#" + t) + "\">" + t + "</a></span>)\"");
  }

  private void generateCoreElemXml(StringBuilder b, ElementDefinition elem, int indent, String rootName, String pathName, boolean backbone) throws Exception {
    List<ElementDefinition> children = context.getProfileUtilities().getChildList(sd, elem);

    boolean listed = false;
    boolean doneType = false;
    int width = 0;

    for (int i = 0; i < indent; i++) {
      b.append(" ");
    }
    boolean inherited = !elem.getPath().equals(elem.getBase().getPath());
    if (inherited) {
      b.append("<i class=\"inherited\">");
    }

    String en = elem.getName();

    if (en.contains("[x]") && elem.getType().size() == 1)
      en = en.replace("[x]", elem.typeSummary());

    if (defPage == null) {
      if (elem.getIsModifier() || elem.getMustSupport())
        b.append("&lt;<span style=\"text-decoration: underline\" title=\"" + Utilities.escapeXml(elem.getDefinition()) + "\">");
      else
        b.append("&lt;<span title=\"" + Utilities.escapeXml(elem.getDefinition()) + "\">");
    } else if (elem.getIsModifier() || elem.getMustSupport())
      b.append("&lt;<a href=\"" + (defPage + "#" + pathName + "." + en) + "\" title=\"" + Utilities.escapeXml(elem.getDefinition())
              + "\" class=\"dict\"><span style=\"text-decoration: underline\">");
    else
      b.append("&lt;<a href=\"" + (defPage + "#" + pathName + "." + en) + "\" title=\"" + Utilities.escapeXml(elem.getDefinition()) + "\" class=\"dict\">");

    // element contains xhtml
    if (!elem.getType().isEmpty() && elem.getType().get(0).getWorkingCode().equals("xhtml")) {
      b.append("<b title=\""
              + Utilities.escapeXml(elem.getDefinition())
              + "\">div</b>" + ((elem.getIsModifier() || elem.getMustSupport()) ? "</span>" : "")
              + (defPage == null ? "</span>" : "</a>")
              + " xmlns=\"http://www.w3.org/1999/xhtml\"&gt; <span style=\"color: Gray\">&lt;!--</span> <span style=\"color: navy\">"
              + Utilities.escapeXml(elem.getShort())
              + "</span><span style=\"color: Gray\">&lt; --&gt;</span> &lt;/div&gt;\r\n");
    }
    // element has a constraint which fixes its value
    else if (elem.hasFixed()) {
      if (defPage == null) {
        b.append(en + "</span>&gt;");
      } else if (elem.getIsModifier() || elem.getMustSupport()) {
        b.append(en + "</span></a>&gt;");
      } else {
        b.append(en + "</a>&gt;");
      }
      b.append(renderTypeXml(indent, elem.getFixed()) + "&lt;" + en + "/&gt;\r\n");
    } else {
      b.append("<b>" + en);
      if (defPage == null) {
        b.append("</b></span>");
      } else if (elem.getIsModifier() || elem.getMustSupport()) {
        b.append("</b></span></a>");
      } else {
        b.append("</b></a>");
      }
      if (elem.getType().size() == 1 && context.getContextUtilities().isPrimitiveType(elem.typeSummary())) {
        doneType = true;
        b.append(" value=\"[<span style=\"color: darkgreen\"><a href=\"" + getLinkFor(elem.typeSummary()) + "\">" + elem.typeSummary() + "</a></span>]\"/");
      }
      b.append("&gt;");

      // For simple elements without nested content, render the
      // optionality etc. within a comment
      if (children.isEmpty()) {
        b.append("<span style=\"color: Gray\">&lt;!--</span>");
      }

      if (elem.hasContentReference()) {
        // Contents of element are defined elsewhere in the same
        // resource
        writeCardinality(b, elem);
        b.append(" <span style=\"color: darkgreen\">");
        b.append("Content as for " + elem.getContentReference().substring(elem.getContentReference().indexOf("#")+1) + "</span>");
        listed = true;
      } else if (!elem.getType().isEmpty()
              && !(elem.getType().size() == 1)) {
        writeCardinality(b, elem);
        listed = true;
        if (!doneType) {
          width = writeTypeLinksXml(b, elem, indent);
        }
      } else if (elem.getName().equals("extension")) {
        b.append(" <a href=\"" + "extensibility.html\"><span style=\"color: navy\">See Extensions</span></a> ");
      } else if (elem.getType().size() == 1) {
        writeCardinality(b, elem);
        b.append(" <span style=\"color: darkgreen\">");
        b.append("<a href=\"" + "datatypes.html#open\">*</a>");
        b.append("</span>");
        listed = true;
      }

//			if (!Utilities.noString(elem.getProfile())) {
//	      b.append(" <a href=\""+elem.getProfile()+"\"><span style=\"color: DarkViolet\">StructureDefinition: \""+elem.getProfile().substring(1)+"\"</span></a>");
//			}
      b.append(" ");
      if (children.isEmpty()) {
        if ("See Extensions".equals(elem.getShort())) {
          b.append(" <a href=\"" + "extensibility.html\"><span style=\"color: navy\">"
                  + Utilities.escapeXml(elem.getShort())
                  + "</span></a> ");
        } else {
          if (elem.prohibited())
            b.append("<span style=\"text-decoration: line-through\">");
          String ref = getBindingLink(elem);
          b.append("<span style=\"color: navy\"><a href=\"" + (Utilities.isAbsoluteUrl(ref) ? "" : "") + ref + "\" style=\"color: navy\">" + Utilities.escapeXml(elem.getShort()) + "</a></span>");
          if (elem.prohibited())
            b.append("</span>");
        }
      } else {
        if (elem.unbounded() && !listed) { // isNolist()) {
          if (false) { // elem.usesCompositeType() ??
            b.append(" <span style=\"color: Gray\">&lt;!--");
            writeCardinality(b, elem);
            if (elem.prohibited())
              b.append("<span style=\"text-decoration: line-through\">");
            b.append("" + Utilities.escapeXml(elem.getShort()));
            if (elem.prohibited())
              b.append("</span>");
            b.append(" --&gt;</span>");
          } else if (elem.hasShort()) {
            b.append(" <span style=\"color: Gray\">&lt;!--");
            writeCardinality(b, elem);
            if (elem.prohibited())
              b.append("<span style=\"text-decoration: line-through\">");
            b.append(" " + Utilities.escapeXml(elem.getShort()));
            if (elem.prohibited())
              b.append("</span>");
            b.append(" --&gt;</span>");
          } else {
            b.append(" <span style=\"color: Gray\">&lt;!--");
            writeCardinality(b, elem);
            b.append(" --&gt;</span>");
          }
        } else if (elem.hasShort()) {
          b.append(" <span style=\"color: Gray\">&lt;!--");
          writeCardinality(b, elem);
          if (elem.prohibited())
            b.append("<span style=\"text-decoration: line-through\">");
          b.append(" " + Utilities.escapeXml(elem.getShort()));
          if (elem.prohibited())
            b.append("</span>");
          b.append(" --&gt;</span>");
        }
        b.append("\r\n");

        if (!elem.prohibited()) {
          for (ElementDefinition child : children) {
            generateCoreElemXml(b, child, indent + 1, rootName, pathName + "." + en, backbone);
          }
        }
      }

      for (int i = 0; i < indent; i++) {
        b.append(" ");
      }
    }

    if (children.isEmpty()) {
      b.append("<span style=\"color: Gray\"> --&gt;</span>");
    }
    if (!doneType) {
      b.append("&lt;/");
      b.append(en);
      b.append("&gt;");
    }
    if (inherited) {
      b.append("</i>");
    }
    b.append("\r\n");
  }

  private String renderTypeXml(int indent, DataType value) throws Exception {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < indent - 2; i++)
      b.append(" ");
    String ind = b.toString();

    XmlParser xml = new XmlParser();
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    xml.setOutputStyle(IParser.OutputStyle.PRETTY);
    xml.compose(bs, null, value);
    bs.close();
    String[] result = bs.toString().split("\\r?\\n");
    b = new StringBuilder();
    for (String s : result) {
      if (s.startsWith(" ")) // eliminate the wrapper content
        b.append("\r\n  " + ind + Utilities.escapeXml(s));
    }
    return b.toString() + "\r\n" + ind;
  }

  private void writeCardinality(StringBuilder b, ElementDefinition elem) throws IOException {
    if (elem.getConstraint().size() > 0)
      b.append(" <span style=\"color: brown\" title=\""
              + Utilities.escapeXml(getInvariants(elem)) + "\"><b>" + INV_FLAG + " "
              + describeCardinality(elem) + "</b></span>");
    else
      b.append(" <span style=\"color: brown\"><b>"
              + describeCardinality(elem) + "</b></span>");
  }

  private String describeCardinality(ElementDefinition elem) {
    return (elem.getMinElement() == null ? "" : Integer.toString(elem.getMin())) + ".." + (elem.getMax() == null ? "" : elem.getMax());
  }

  private String getInvariants(ElementDefinition elem) {
    StringBuilder b = new StringBuilder();
    boolean first = true;
    for (ElementDefinition.ElementDefinitionConstraintComponent i : elem.getConstraint()) {
      if (!first)
        b.append("; ");
      first = false;
      b.append(i.getKey() + ": " + i.getHuman());
    }

    return b.toString();
  }

  private int writeTypeLinksXml(StringBuilder b, ElementDefinition elem, int indent) throws Exception {
    b.append(" <span style=\"color: darkgreen\">");
    int i = 0;
    int w = indent + 12 + elem.getName().length(); // this is wrong if the type is an attribute, but the wrapping concern shouldn't apply in this case, so this is ok
    for (ElementDefinition.TypeRefComponent t : elem.getType()) {
      if (i > 0) {
        b.append("|");
        w++;
      }
      if (w + t.getName().length() > 80) {
        b.append("\r\n  ");
        for (int j = 0; j < indent; j++)
          b.append(" ");
        w = indent + 2;
      }
      w = w + t.getName().length(); // again, could be wrong if this is an extension, but then it won't wrap
      if (t.getWorkingCode().equals("xhtml")) {
        b.append(t.getName());
      } else if (t.getName().equals("Extension") && t.hasProfile()) {
        b.append("<a href=\"" + t.getProfile().get(0).primitiveValue() + "\"><span style=\"color: DarkViolet\">@" + t.getProfile().get(0).primitiveValue() + "</span></a>");
      } else
        b.append("<a href=\"" + (getLinkFor(t.getWorkingCode())
                + ".html#" + t.getName() + "\">" + t.getName())
                + "</a>");
      if (t.hasTargetProfile()) {
        b.append("(");
        boolean firstp = true;
        List<StructureDefinition> ap = new ArrayList<>();
        for (CanonicalType p : t.getTargetProfile()) {
          StructureDefinition sdt = context.getContext().fetchResource(StructureDefinition.class, p.primitiveValue());
          if (sdt != null) {
            ap.add(sdt);
          }
        }
        ap.sort(new StructureDefinitionSorterByUrl());
        for (StructureDefinition sdt : ap) {
          String p = sdt.getType();
          if (!firstp) {
            b.append("|");
            w++;
          }
          if (w + p.length() > 80) {
            b.append("\r\n  ");
            for (int j = 0; j < indent; j++)
              b.append(" ");
            w = indent + 2;
          }
          w = w + p.length();

          b.append("<a href=\"" + sdt.getWebPath() + "\">" + p + "</a>");

          firstp = false;
        }
        b.append(")");
        w++;
      }

      i++;
    }
    b.append("</span>");
    return w;
  }

  public String generateJson() throws Exception {
    StringBuilder b = new StringBuilder();
    b.append("<pre class=\"spec\">\r\n");

    generateInnerJson(b, sd.getSnapshot().getElementFirstRep());

    b.append("</pre>\r\n");
    return b.toString();
  }

  private void generateInnerJson(StringBuilder b, ElementDefinition root) throws IOException, Exception {
    List<ElementDefinition> children = context.getProfileUtilities().getChildList(sd, root);

    String rn;
    if (sd.getAbstract())
      rn = "[name]";
    else
      rn = root.getName();

    boolean resource = sd.getKind() == StructureDefinition.StructureDefinitionKind.RESOURCE;

    b.append("{<span style=\"float: right\"><a title=\"Documentation for this format\" href=\""+""+"json.html\"><img src=\""+""+"help.png\" alt=\"doco\"/></a></span>\r\n");
    if (resource) {
      b.append("  \"resourceType\" : \"");
      if (defPage == null)
        b.append("<span title=\"" + Utilities.escapeXml(root.getDefinition())
                + "\"><b>");
      else
        b.append("<a href=\"" + (defPage + "#" + root.getName()) + "\" title=\""
                + Utilities.escapeXml(root.getDefinition())
                + "\" class=\"dict\"><b>");
      b.append(rn);
      if ((defPage == null))
        b.append("</b></span>\",\r\n");
      else
        b.append("</b></a>\",\r\n");
    }

    if ((root.getName().equals(rn) || "[name]".equals(rn)) && resource) {
      if (!Utilities.noString(root.typeSummary())) {
        b.append("  // from <a href=\""+"resource.html\">Resource</a>: <a href=\""+"resource.html#id\">id</a>, <a href=\""+"resource.html#meta\">meta</a>, <a href=\""+"resource.html#implicitRules\">implicitRules</a>, and <a href=\""+"resource.html#language\">language</a>\r\n");
        if (isDomainResource(sd)) {
          b.append("  // from <a href=\""+"domainresource.html\">DomainResource</a>: <a href=\""+"narrative.html#Narrative\">text</a>, <a href=\""+"references.html#contained\">contained</a>, <a href=\""+"extensibility.html\">extension</a>, and <a href=\""+"extensibility.html#modifierExtension\">modifierExtension</a>\r\n");
        }
      }
    } else if (!resource) {
      if (root.typeSummary().equals("BackboneElement"))
        b.append("  // from BackboneElement: <a href=\""+"extensibility.html\">extension</a>, <a href=\""+"extensibility.html\">modifierExtension</a>\r\n");
      else
        b.append("  // from Element: <a href=\""+"extensibility.html\">extension</a>\r\n");
    }
    int c = 0;
    for (ElementDefinition elem : children) {
      generateCoreElemJson(b, elem, 1, rn, root.getName(), root.getName().equals(rn) && resource, ++c == children.size());
    }

    b.append("}\r\n");
  }


  private void generateCoreElemJson(StringBuilder b, ElementDefinition elem, int indent,	String rootName, String pathName, boolean backbone, boolean last) throws Exception {
    // base pattern: "name" : "value" // optionality documentation

    int width = 0;
    
    if (elem.prohibited()) {
      b.append("<span style=\"text-decoration: line-through\">");
    }

    String en = elem.getName();

    if (en.contains("[x]") && elem.getType().size() == 1)
      en = en.replace("[x]", elem.typeSummary());

    if (en.contains("[x]")) {
      // 1. name
      for (int i = 0; i < indent; i++) {
        b.append("  ");
      }
      if (elem.getType().size() > 1) {
        b.append("<span style=\"color: Gray\">// "+en+": <span style=\"color: navy; opacity: 0.8\">" + docPrefix(width, indent, elem)+Utilities.escapeXml(elem.getShort()) + "</span>. One of these "+Integer.toString(elem.getType().size())+":</span>\r\n");
        int c = 0;
        for (ElementDefinition.TypeRefComponent t : elem.getType()) {
          c++;
          generateCoreElemDetailsJson(b, elem, indent, rootName, pathName, backbone, last && c == elem.getType().size(), width, en.replace("[x]", t.getName()), t, false);
        }
      } else {
        List<TypesUtilities.WildcardInformation> tr = TypesUtilities.wildcards(context.getContext().getVersion());
        b.append("<span style=\"color: Gray\">// "+en+": <span style=\"color: navy; opacity: 0.8\">" + docPrefix(width, indent, elem)+Utilities.escapeXml(elem.getShort()) + "</span>. One of these "+Integer.toString(tr.size())+":</span>\r\n");
        int c = 0;
        for (TypesUtilities.WildcardInformation t : tr) {
          c++;
          generateCoreElemDetailsJson(b, elem, indent, rootName, pathName, backbone, last && c == elem.getType().size(), width, en.replace("[x]", upFirst(t.getTypeName())), toTypeRef(t), false);
        }
      }
    } else {
      generateCoreElemDetailsJson(b, elem, indent, rootName, pathName, backbone, last, width, en, elem.getType().isEmpty() ? null : elem.getType().get(0), true);
    }
  }

  private String upFirst(String s) {
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }

  private String docPrefix(int widthSoFar, int indent, ElementDefinition elem) {
    if (widthSoFar + elem.getShort().length()+8+elem.getName().length() > 105) {
      String ret = "\r\n  ";
      for (int i = 0; i < indent+2; i++)
        ret = ret + " ";

      return ret;
    }
    else
      return "";
  }


  private ElementDefinition.TypeRefComponent toTypeRef(TypesUtilities.WildcardInformation t) {
    ElementDefinition.TypeRefComponent r = new ElementDefinition.TypeRefComponent();
    r.setCode(t.getTypeName());
    return r;
  }

  private void generateCoreElemDetailsJson(StringBuilder b, ElementDefinition elem, int indent, String rootName, String pathName, boolean backbone, boolean last, int width, String en, ElementDefinition.TypeRefComponent type, boolean doco) throws Exception {
    List<ElementDefinition> children = context.getProfileUtilities().getChildList(sd, elem);
    
    if (elem.getName().equals("extension")) {
      b.append("  (<a href=\""+"extensibility.html\">Extensions</a> - see <a href=\""+"json.html#extensions\">JSON page</a>)\r\n");
      return;
    }
    if (elem.getName().equals("modifierExtension")) {
      b.append("  (<a href=\""+"extensibility.html#modifier\">Modifier Extensions</a> - see <a href=\""+"json.html#modifier\">JSON page</a>)\r\n");
      return;
    }

    // 1. name
    for (int i = 0; i < indent; i++) {
      b.append("  ");
    }

    if (defPage == null) {
      if (elem.getIsModifier() || elem.getMustSupport())
        b.append("\"<span style=\"text-decoration: underline\" title=\"" + Utilities.escapeXml(elem.getDefinition())	+ "\">");
      else
        b.append("\"<span title=\"" + Utilities.escapeXml(elem.getDefinition()) + "\">");
    } else if (elem.getIsModifier() || elem.getMustSupport())
      b.append("\"<a href=\"" + (defPage + "#" + pathName + "." + en)+ "\" title=\"" + Utilities .escapeXml(elem.getDefinition())
              + "\" class=\"dict\"><span style=\"text-decoration: underline\">");
    else
      b.append("\"<a href=\"" + (defPage + "#" + pathName + "." + en) + "\" title=\"" + Utilities.escapeXml(elem.getDefinition()) + "\" class=\"dict\">");

    if (defPage == null) {
      b.append(en+"</span>");
    } else if (elem.getIsModifier() || elem.getMustSupport())
      b.append(en + "</span></a>");
    else
      b.append(en + "</a>");

    b.append("\" : ");

    // 2. value
    boolean delayedCloseArray = false;
    if (elem.repeats())
      b.append("[");

    if (type == null) {
      // inline definition
      assert(children.size() > 0);
      b.append("{");
      delayedCloseArray = true;
    } else if (type.getWorkingCode().equals("xhtml")) {
      // element contains xhtml
      b.append("\"(Escaped XHTML)\"");
    } else if (context.getContextUtilities().isPrimitiveType(type.getName())) {
      if (!(type.getName().equals("integer") || type.getName().equals("boolean") || type.getName().equals("decimal")))
        b.append("\"");
      b.append("&lt;<span style=\"color: darkgreen\"><a href=\"" + getLinkFor(type.getName()) + "\">" + type.getName()+ "</a></span>");
      if (type.hasTargetProfile()) {
        b.append("(");
        boolean first = true;
        for (CanonicalType p : type.getTargetProfile()) {
          if (first) first = false; else b.append("|");
          StructureDefinition sdt = context.getContext().fetchResource(StructureDefinition.class, p.primitiveValue());
          if (sdt != null) {
            b.append("<a href=\"" + sdt.getWebPath() + "\">" + sdt.getType() + "</a>");
          }
        }
        b.append(")");
      }
      b.append("&gt;");
      if (!(type.getName().equals("integer") || type.getName().equals("boolean") || type.getName().equals("decimal")))
        b.append("\"");
    } else {
      b.append("{");
      width = writeTypeLinksJson(b, elem, indent, type);
      b.append(" }");
    }

    if (!delayedCloseArray && elem.repeats())
      b.append("]");
    if (!last && !delayedCloseArray)
      b.append(",");

    if (!elem.hasFixed() && doco) {
      b.append(" <span style=\"color: Gray\">//</span>");

      // 3. optionality
      writeCardinality(b, elem);

      // 4. doco
      b.append(" ");

      if (elem.getName().equals("extension")) {
        b.append(" <a href=\""+"extensibility.html\"><span style=\"color: navy; opacity: 0.8\">See Extensions</span></a>");
      } else if ("See Extensions".equals(elem.getShort())) {
        b.append(" <a href=\""+"extensibility.html\"><span style=\"color: navy; opacity: 0.8\">"
                + Utilities.escapeXml(elem.getShort())
                + "</span></a>");
      } else {
        String ref = getBindingLink(elem);
        b.append("<span style=\"color: navy; opacity: 0.8\"><a href=\""+(Utilities.isAbsoluteUrl(ref) ? "": "")+ref+"\" style=\"color: navy; opacity: 0.8\">" + Utilities.escapeXml(elem.getShort()) + "</a></span>");
      }
    }
    if (elem.prohibited())
      b.append("</span>");

    if (children.size() > 0) {
      b.append("\r\n");
      int c = 0;
      for (ElementDefinition child : children) {
        generateCoreElemJson(b, child, indent + 1, rootName, pathName + "." + en, backbone, ++c == children.size());
      }

      for (int i = 0; i < indent; i++) {
        b.append("  ");
      }
    }
    if (children.size() > 0) {
      b.append("}");
      if (delayedCloseArray && elem.repeats())
        b.append("]");
      if (!last && delayedCloseArray)
        b.append(",");
    }

    b.append("\r\n");
  }

  private String getLinkFor(String p) {
    return context.getPkp().getLinkFor(context.getLink(RenderingContext.KnownLinkType.SPEC, true), p);
  }

  private int writeTypeLinksJson(StringBuilder b, ElementDefinition elem, int indent, ElementDefinition.TypeRefComponent t) throws Exception {
    b.append(" <span style=\"color: darkgreen\">");
    int i = 0;
    int w = indent + 12 + elem.getName().length(); // this is wrong if the type is an attribute, but the wrapping concern shouldn't apply in this case, so this is ok
//     for (TypeRef t : elem.getTypes()) {
    if (i > 0) {
      b.append("|");
      w++;
    }
    if (w + t.getName().length() > 80) {
      throw new Error("this sholdn't happen");
//        write("\r\n  ");
//        for (int j = 0; j < indent; j++)
//          write(" ");
//        w = indent+2;
    }
    w = w + t.getName().length(); // again, could be wrong if this is an extension, but then it won't wrap
    if (t.getWorkingCode().equals("xhtml"))
      b.append(t.getName());
    else if (t.getName().equals("Extension") && t.hasProfile()) {
      b.append("<a href=\""+t.getProfile()+"\"><span style=\"color: DarkViolet\">@"+t.getProfile()+"</span></a>");
    } else {
      b.append("<a href=\"" + (getLinkFor(t.getName())
              + ".html#" + t.getName() + "\">" + t.getName())
              + "</a>");
    }
    if (t.hasTargetProfile()) {
      b.append("(");
      boolean firstp = true;
      List<StructureDefinition> ap = new ArrayList<>();
      for (CanonicalType p : t.getTargetProfile()) {
        StructureDefinition sdt = context.getContext().fetchResource(StructureDefinition.class, p.primitiveValue());
        if (sdt != null) {
          ap.add(sdt);
        }
      }
      ap.sort(new StructureDefinitionSorterByUrl());
      for (StructureDefinition sdt : ap) {
        String p = sdt.getType();
        if (!firstp) {
          b.append("|");
          w++;
        }

        // again, p.length() could be wrong if this is an extension, but then it won't wrap
        if (w + p.length() > 80) {
          b.append("\r\n  ");
          for (int j = 0; j < indent; j++)
            b.append(" ");
          w = indent+2;
        }
        w = w + p.length();

        // TODO: Display action and/or profile information
        b.append("<a href=\"" + sdt.getWebPath() + "\">" + p + "</a>");
        firstp = false;
      }
      b.append(")");
      w++;
    }

    i++;
    //}
    b.append("</span>");
    return w;
  }

}


