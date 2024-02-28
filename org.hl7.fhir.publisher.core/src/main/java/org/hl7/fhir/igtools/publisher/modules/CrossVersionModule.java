package org.hl7.fhir.igtools.publisher.modules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.modules.xver.CodeChainsSorter;
import org.hl7.fhir.igtools.publisher.modules.xver.ColumnEntry;
import org.hl7.fhir.igtools.publisher.modules.xver.ColumnSorter;
import org.hl7.fhir.igtools.publisher.modules.xver.ElementDefinitionLink;
import org.hl7.fhir.igtools.publisher.modules.xver.ElementDefinitionPair;
import org.hl7.fhir.igtools.publisher.modules.xver.SourcedElementDefinition;
import org.hl7.fhir.igtools.publisher.modules.xver.SourcedElementDefinitionSorter;
import org.hl7.fhir.igtools.publisher.modules.xver.StructureDefinitionColumn;
import org.hl7.fhir.igtools.publisher.modules.xver.XVerAnalysisEngine;
import org.hl7.fhir.igtools.publisher.modules.xver.XVerAnalysisEngine.MakeLinkMode;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.renderers.ConceptMapRenderer;
import org.hl7.fhir.r5.renderers.ConceptMapRenderer.RenderMultiRowSortPolicy;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class CrossVersionModule implements IPublisherModule {

  private XVerAnalysisEngine engine;

  public CrossVersionModule() {
    engine = new XVerAnalysisEngine();
  }

  @Override
  public boolean useRoutine(String name) {
    return Utilities.existsInList(name, "preProcess");
  }

  @Override
  public String code() {
    return "x-version";
  }

  @Override
  public String name() {
    return "Cross-Version PreProcessor";
  }

  public boolean preProcess(String path) {
    try {
      if (engine.process(path)) {
        engine.logProgress("Generating fragments");
        genChainsHtml(path, "cross-version-chains-all", false, false);
        genChainsHtml(path, "cross-version-chains-valid", true, false);
        genChainsHtml(path, "cross-version-chains-min", true, true);

        for (String name : Utilities.sorted(engine.getVdr5().getStructures().keySet())) {
          StructureDefinition sd = engine.getVdr5().getStructures().get(name);
          if ((sd.getKind() == StructureDefinitionKind.COMPLEXTYPE || sd.getKind() == StructureDefinitionKind.RESOURCE) && !sd.getAbstract() && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
            genVersionType(path, sd);
          }
        }

        genSummaryPages(path);
        return true;
      } else {
        return false;
      }
    } catch (Exception e) {
      System.out.println("Exception running "+name()+": "+e.getMessage());
      e.printStackTrace();
      return false;
    }

  }

  private void genChainsHtml(String path, String filename, boolean validOnly, boolean itemsOnly) throws IOException {
   engine.logProgress("Create "+filename);

    XhtmlNode body = new XhtmlNode(NodeType.Element, "div");    
    body.h1().tx("FHIR Cross Version Extensions");
    body.para().tx("something");


    body.h2().tx("Element Chains");
    XhtmlNode ul = null;
    for (SourcedElementDefinition origin :engine.getOrigins()) {
      List<ElementDefinitionLink> links = engine.makeEDLinks(origin, MakeLinkMode.ORIGIN_CHAIN);
      String n = origin.getEd().getPath();
      if (!n.contains(".")) {
        body.para().tx(n);
        ul = null;
      } else if (!validOnly || engine.hasValid(links)) { 
        if (ul == null) {
          ul = body.ul();
        }
        XhtmlNode li = ul.li();
        li.tx(origin.toString());
        XhtmlNode uli = li.ul();
        renderElementDefinition(uli.li(), origin);
        for (ElementDefinitionLink link : links) {
          if (!itemsOnly || link.getNext().isValid()) {
            renderElementDefinition(uli.li(), link.getNext());
          }
        }
      }
    }
    TextFile.stringToFile(new XhtmlComposer(false, false).compose(body), Utilities.path(path, "input", "includes", filename+".xhtml"));
    TextFile.stringToFile(new XhtmlComposer(false, false).compose(wrapPage(body, "FHIR Cross Version Extensions")), Utilities.path(path, "temp", "xver-qa", filename+".html"));
  }


  private void renderElementDefinition(XhtmlNode x, SourcedElementDefinition ed) {
    String ver = VersionUtilities.getMajMin(ed.getVer());
    String prefix = VersionUtilities.getNameForVersion(ver).toLowerCase();
    Map<String, StructureDefinition> structures = engine.getVersions().get(prefix).getStructures();

    XhtmlNode x1 = ed.isValid() ? x : x.strikethrough();

    x1.code("http://hl7.org/fhir/"+ver+"/StructureDefinition/extension-"+ed.getEd().getPath());

    boolean first = true;
    for (TypeRefComponent t : ed.getEd().getType()) {
      StructureDefinition sd = structures.get(t.getWorkingCode());
      if (sd != null && !sd.getAbstract()) {
        if (first) {x1.tx(" : "); first = false; } else { x1.tx("|");  x1.wbr(); }
        x1.ah(prefix+"#"+t.getWorkingCode()).tx(t.getWorkingCode());
        if (t.hasTargetProfile()) {
          x1.tx("(");
          boolean tfirst = true;
          for (CanonicalType u : t.getTargetProfile()) {
            if (tfirst) {tfirst = false; } else { x1.tx("|"); x1.wbr(); }
            String rt = tail(u.getValue());
            x1.ah(prefix+"#"+rt).tx(rt);
          }

          x1.tx(")");
        }
      }
    }
    x1.tx(" : ");
    x1.tx("[");
    x1.tx(ed.getEd().getMin());
    x1.tx("..");
    x1.tx(ed.getEd().getMax());
    x1.tx("].");
    if (ed.isValid()) {
      x.tx(" Valid versions: "+ed.getVerList());
    } else {
      x.tx(" Not valid because "+ed.getStatusReason());
    }
  }

  
  private void genVersionType(String path, StructureDefinition sd) throws IOException {
    XhtmlNode body = new XhtmlNode(NodeType.Element, "div");  
    body.h1().tx(sd.getName());
    body.para().tx("something");
    XhtmlNode tbl = body.table("grid");
    XhtmlNode tr = tbl.tr();

    // we're going to generate a table with a column for each target structure definition in the chains that terminate in the provided sd
    List<StructureDefinitionColumn> columns = new ArrayList<StructureDefinitionColumn>();
    for (StructureDefinition sdt : findLinkedStructures(sd)) {
      columns.add(new StructureDefinitionColumn(sdt, false));
    }
    Collections.sort(columns, new ColumnSorter());
    columns.add(new StructureDefinitionColumn(sd, true));

    for (StructureDefinitionColumn col : columns) {
      tr.th().colspan(col.isRoot() ? 1 : 2).tx(col.getSd().getName()+" ("+col.getSd().getFhirVersion().toCode()+")");
    }
    List<List<ElementDefinitionLink>> codeChains = new ArrayList<>();

    for (ElementDefinition ed : sd.getDifferential().getElement()) {
      for (StructureDefinitionColumn col : columns) {
        col.clear();
      }
      StructureDefinitionColumn rootCol = engine.getColumn(columns, sd);
      rootCol.getEntries().add(new ColumnEntry(null, ed));
      List<ElementDefinitionLink> links = engine.makeEDLinks(ed, MakeLinkMode.CHAIN);
      // multiple fields might collapse into one. So there might be more than one row per target structure. 
      // if there are, we add additional rows, and set the existing cells to span the multiple rows
      // we already know what rowspan is needed.  
      for (ElementDefinitionLink link : links) {
        StructureDefinitionColumn col = engine.getColumn(columns, link.getPrev().getSd());
        col.getEntries().add(new ColumnEntry(link, link.getPrev().getEd()));
        // while we're at it, scan for origin chains with translations
        checkForCodeTranslations(codeChains, link);
      }
      int rowCount = 0;
      for (StructureDefinitionColumn col : columns) {
        rowCount = Integer.max(rowCount, col.rowCount());
      }
      List<XhtmlNode> rows = new ArrayList<XhtmlNode>();
      for (int i = 0; i < rowCount; i++) {
        rows.add(tbl.tr());
      }
      ed.setUserData("rows", rows);
      renderElementRow(sd, columns, ed, rowCount, rows);
    }

    // ok, now we look for any other terminating elements that didn't make it into the R5 structure, and inject them into the table
    List<SourcedElementDefinition> missingChains = findMissingTerminatingElements(sd, columns);
    for (SourcedElementDefinition m : missingChains) {
      StructureDefinitionColumn rootCol = engine.getColumn(columns, m.getSd());
      ElementDefinition prevED = findPreviousElement(m, rootCol.getElements()); // find the anchor that knows which rows we'll be inserting under
      List<XhtmlNode> rows = (List<XhtmlNode>) prevED.getUserData("rows");
      XhtmlNode lastTR = rows.get(rows.size()-1);
      for (StructureDefinitionColumn col : columns) {
        col.clear();
      }
      rootCol.getEntries().add(new ColumnEntry(null, m.getEd()));
      List<ElementDefinitionLink> links = engine.makeEDLinks(m.getEd(), MakeLinkMode.CHAIN);
      // multiple fields might collapse into one. So there might be more than one row per target structure. 
      // if there are, we add additional rows, and set the existing cells to span the multiple rows
      // we already know what rowspan is needed.  
      for (ElementDefinitionLink link : links) {
        StructureDefinitionColumn col = engine.getColumn(columns, link.getPrev().getSd());
        col.getEntries().add(new ColumnEntry(link, link.getPrev().getEd()));
        // while we're at it, scan for origin chains with translations
        checkForCodeTranslations(codeChains, link);
      }
      int rowCount = 0;
      for (StructureDefinitionColumn col : columns) {
        rowCount = Integer.max(rowCount, col.rowCount());
      }
      rows = new ArrayList<XhtmlNode>();
      for (int i = 0; i < rowCount; i++) {
        rows.add(tbl.tr(lastTR));
      }
      m.getEd().setUserData("rows", rows);
      renderElementRow(m.getSd(), columns, m.getEd(), rowCount, rows);
    }

    Collections.sort(codeChains, new CodeChainsSorter());
    for (List<ElementDefinitionLink> links : codeChains) {
      String name = null; // links.get(links.size() -1).next.ed.getPath();
      List<ConceptMap> maps = new ArrayList<>();
      for (ElementDefinitionLink link : links) {
        if (link.getNextCM() != null) {
          if (name == null) {
            String srcscope = link.getNextCM().getSourceScope().primitiveValue();
            name = VersionUtilities.getNameForVersion(srcscope)+" "+srcscope.substring(srcscope.lastIndexOf("#")+1);
          }
          String tgtscope = link.getNextCM().getTargetScope().primitiveValue();
          link.getNextCM().setUserData("presentation",
              VersionUtilities.getNameForVersion(tgtscope)+" "+tgtscope.substring(tgtscope.lastIndexOf("#")+1));
          maps.add(link.getNextCM());
        }
      }
      body.hr();
      body.add(ConceptMapRenderer.renderMultipleMaps(name, maps, engine, RenderMultiRowSortPolicy.UNSORTED));
    }

    TextFile.stringToFile(new XhtmlComposer(false, false).compose(body), Utilities.path(path, "input", "includes", "cross-version-"+sd.getName()+".xhtml"));
    TextFile.stringToFile(new XhtmlComposer(false, false).compose(wrapPage(body, sd.getName())), Utilities.path(path, "temp", "xver-qa", "cross-version-"+sd.getName()+".html"));
  }


  public Set<StructureDefinition> findLinkedStructures(StructureDefinition sd) {
    Set<StructureDefinition> res = new HashSet<>();
    for (ElementDefinition ed : sd.getDifferential().getElement()) {
      SourcedElementDefinition sed = engine.makeSED(sd, ed);
      findLinkedStructures(res, sed);
    }
    return res;
  }


  private void findLinkedStructures(Set<StructureDefinition> res, SourcedElementDefinition sed) {

    List<ElementDefinitionLink> links = engine.makeEDLinks(sed, MakeLinkMode.CHAIN);
    for (ElementDefinitionLink link : links) {
      res.add(link.getPrev().getSd());
    }

  }


  private void checkForCodeTranslations(List<List<ElementDefinitionLink>> codeChains, ElementDefinitionLink link) {
    if (link.getPrev().getEd().hasUserData("links."+MakeLinkMode.ORIGIN_CHAIN)) {
      List<ElementDefinitionLink> originChain = engine.makeEDLinks(link.getPrev().getEd(), MakeLinkMode.ORIGIN_CHAIN);
      boolean mappings = false;
      for (ElementDefinitionLink olink : originChain) {
        mappings = mappings || olink.getNextCM() != null;
      }
      if (mappings) {
        codeChains.add(originChain);
      }
    }
  }


  private void renderElementRow(StructureDefinition sd, List<StructureDefinitionColumn> columns, ElementDefinition ed, int rowCount, List<XhtmlNode> rows) {
    for (StructureDefinitionColumn col : columns) {
      int i = 0;
      for (ColumnEntry entry : col.getEntries()) {
        XhtmlNode ctr = rows.get(i);
        i = i + (entry.getLink() == null ? 1 : entry.getLink().getLeftWidth());
        col.getElements().add(new ElementDefinitionPair(entry.getEd(), ed));
        XhtmlNode td1 = rendererElementForType(ctr.td(), col.isRoot() ? sd : col.getSd(), entry.getEd(), col.isRoot() ? engine.getVdr5().getStructures() : engine.getVersions().get(VersionUtilities.getNameForVersion(col.getSd().getFhirVersion().toCode()).toLowerCase()).getStructures(), ed.getPath());
        if (entry.getLink() != null && entry.getLink().getLeftWidth() > 1) {
          td1.rowspan(entry.getLink().getLeftWidth());
        }
        if (!col.isRoot()) {
          if (entry.getLink() != null) {
            XhtmlNode td = ctr.td().style("background-color: LightGrey; text-align: center; vertical-align: middle; color: white"); // .tx(entry.link.rel.getSymbol());
            if (entry.getLink().getLeftWidth() > 1) {
              td.rowspan(entry.getLink().getLeftWidth());
            }
            if (entry.getLink().getNextCM() != null) {
              td.tx("☰");
            } else if (entry.getLink().getRel() == null) {
              td.tx("?");
            } else switch (entry.getLink().getRel() ) {
            case EQUIVALENT:
              td.tx("=");
              break;
            case NOTRELATEDTO:
              td.tx("!=");
              break;
            case RELATEDTO:
              td.tx("~");
              break;
            case SOURCEISBROADERTHANTARGET:
              td.tx("<");
              break;
            case SOURCEISNARROWERTHANTARGET:
              td.tx(">");
              break;
            }
          } else {
            ctr.td().style("background-color: #eeeeee");
          }
        }
      }
      for (int j = i; j < rowCount; j++ ) {
        rows.get(j).td().style("background-color: #eeeeee");
        if (!col.isRoot()) {
          rows.get(j).td().style("background-color: #eeeeee");
        }
      }          
    }
  }

  private ElementDefinition findPreviousElement(SourcedElementDefinition m, List<ElementDefinitionPair> elements) {
    // latest possible parent
    CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
    int parent = 0;  
    for (int i = elements.size()-1; i >= 0; i--) {
      b.append(elements.get(i).getFocus().getPath());
      if (m.getEd().getPath().startsWith(elements.get(i).getFocus().getPath()+".")) {
        parent = i; 
      }      
    }
    // last descendant of parent
    String path = elements.get(parent).getFocus().getPath()+".";
    for (int i = elements.size()-1; i >= parent; i--) {
      if (elements.get(i).getFocus().getPath().startsWith(path)) {
        return elements.get(i).getAnchor();
      }      
    }
    return elements.get(parent).getAnchor();
  }


  private List<SourcedElementDefinition> findMissingTerminatingElements(StructureDefinition base, List<StructureDefinitionColumn> columns) {
    List<SourcedElementDefinition> res = new ArrayList<SourcedElementDefinition>();
    for (SourcedElementDefinition t : engine.getTerminatingElements()) {
      if (t.getSd() != base) { // already rendered them 
        for (StructureDefinitionColumn col : columns) {
          if (t.getSd() == col.getSd()) {
            res.add(t);
          }
        }
      }
    }
    return res;
  }

  private void genSummaryPages(String path) throws IOException {
    List<ConceptMap> maps = new ArrayList<>();
    maps.add(engine.cm("resources-2to3"));
    maps.add(engine.cm("resources-3to4"));
    maps.add(engine.cm("resources-4to4b"));
    maps.add(engine.cm("resources-4bto5"));
    XhtmlNode page = ConceptMapRenderer.renderMultipleMaps("Resource", maps, engine, RenderMultiRowSortPolicy.FIRST_COL);

    TextFile.stringToFile(new XhtmlComposer(false, false).compose(page), Utilities.path(path, "input", "includes", "cross-version-resources.xhtml"));
    TextFile.stringToFile(new XhtmlComposer(false, false).compose(wrapPage(page, "Resource Map")), Utilities.path(path, "temp", "xver-qa", "cross-version-resources.html"));

    maps = new ArrayList<>();
    maps.add(engine.cm("types-2to3"));
    maps.add(engine.cm("types-3to4"));
    maps.add(engine.cm("types-4to4b"));
    maps.add(engine.cm("types-4bto5"));
    page = ConceptMapRenderer.renderMultipleMaps("Type", maps, engine, RenderMultiRowSortPolicy.FIRST_COL);

    TextFile.stringToFile(new XhtmlComposer(false, false).compose(page), Utilities.path(path, "input", "includes", "cross-version-types.xhtml"));
    TextFile.stringToFile(new XhtmlComposer(false, false).compose(wrapPage(page, "Type Map")), Utilities.path(path, "temp", "xver-qa", "cross-version-types.html"));

  }

  private XhtmlNode rendererElementForType(XhtmlNode td, StructureDefinition sdt, ElementDefinition ed, Map<String, StructureDefinition> structures, String origName) {
    if (ed.getPath().contains(".")) {
      XhtmlNode span = td.span().attribute("title", ed.getPath());
      if (origName != null && !origName.equals(ed.getPath())) {
        span.style("color: maroon; font-weight: bold");
      }
      String[] p = ed.getPath().split("\\.");
      for (int i = 0; i < p.length; i++) {
        if (i == p.length - 1) {
          span.tx(p[i]);
        } else {
          span.tx(p[i].substring(0, 1));
          span.tx(".");
        }
      }
      boolean first = true;
      for (TypeRefComponent t : ed.getType()) {
        StructureDefinition sd = structures.get(t.getWorkingCode());
        if (sd != null && !sd.getAbstract()) {
          if (first) {td.tx(" : "); first = false; } else { td.tx("|");  td.wbr(); }
          td.ah(linkforType(t.getWorkingCode())).tx(t.getWorkingCode());
          if (t.hasTargetProfile()) {
            td.tx("(");
            boolean tfirst = true;
            for (CanonicalType u : t.getTargetProfile()) {
              if (tfirst) {tfirst = false; } else { td.tx("|"); td.wbr(); }
              String rt = tail(u.getValue());
              td.ah(linkforType(rt)).tx(rt);
            }
            td.tx(")");
          }
        }
      }
      td.tx(" : ");
      td.tx("[");
      td.tx(ed.getMin());
      td.tx("..");
      td.tx(ed.getMax());
      td.tx("]");    
    } else {
      XhtmlNode ah = td.ah(pathForType(sdt, ed.getPath()));
      if (origName != null && !origName.equals(ed.getPath())) {
        ah.style("color: maroon; font-weight: bold");
      }
      ah.tx(ed.getPath());        
    }
    return td;
  }

  private String linkforType(String type) {
    if (Utilities.existsInList(type, "instant", "time", "date", "dateTime", "decimal", "boolean", "integer", "string", "uri", "base64Binary", "code", "id", "oid", 
        "unsignedInt", "positiveInt", "markdown", "url", "canonical", "uuid", "integer64")) {
      return "cross-version-primitives.html";
    } else {
      return "cross-version-"+type+".html";
    }
  }


  private String pathForType(StructureDefinition sdt, String type) {
    String sp = VersionUtilities.getSpecUrl(sdt.getFhirVersion().toCode());
    if (Utilities.existsInList(type, "instant", "time", "date", "dateTime", "decimal", "boolean", "integer", "string", "uri", "base64Binary", "code", "id", "oid", 
        "unsignedInt", "positiveInt", "markdown", "url", "canonical", "uuid", "integer64", "Identifier", "HumanName", "Address", "ContactPoint", 
        "Timing", "Quantity", "SimpleQuantity", "Attachment", "Range", "Period", "Ratio", "RatioRange", "CodeableConcept", "Coding", "SampledData", 
        "Age", "Distance", "Duration", "Count", "Money", "MoneyQuantity", "Annotation", "Signature")) {
      return Utilities.pathURL(sp, "datatypes.html#"+type);
    } else if (Utilities.existsInList(type, "ContactDetail", "Contributor", "DataRequirement", "ParameterDefinition", "RelatedArtifact", "TriggerDefinition", 
        "UsageContext", "Expression", "ExtendedContactDetail", "VirtualServiceDetail", "Availability", "MonetaryComponent")) {
      return Utilities.pathURL(sp, "metadatatypes.html#"+type);
    } else if (Utilities.existsInList(type, "DataType", "BackboneType", "BackboneElement", "Element")) {
      return Utilities.pathURL(sp, "types.html#"+type);      
    } else if (Utilities.existsInList(type, "Extension")) { 
      return Utilities.pathURL(sp, "extensions.html#"+type);      
    } else if (Utilities.existsInList(type, "Narrative", "xhtml")) {
      return Utilities.pathURL(sp, "narrative.html#"+type);      
    } else if (Utilities.existsInList(type, "Meta")) { 
      return Utilities.pathURL(sp, "resource.html#"+type);      
    } else if (Utilities.existsInList(type, "Reference", "CodeableReference")) { 
      return Utilities.pathURL(sp, "references.html#"+type);      
    } else {
      return Utilities.pathURL(sp, type.toLowerCase()+".html#"+type);
    }
  }


  private XhtmlNode wrapPage(XhtmlNode content, String title) {
    XhtmlNode page = new XhtmlNode(NodeType.Element, "html");
    XhtmlNode head = page.head();
    head.link("stylesheet", "fhir.css");
    head.title(title);
    XhtmlNode body = page.body();
    body.style("background-color: white");
    body.add(content);
    return page;
  }


  private String tail(String value) {
    return value.contains("/") ? value.substring(value.lastIndexOf("/")+1) : value;
  }
}
