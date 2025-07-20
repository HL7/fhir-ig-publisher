package org.hl7.fhir.igtools.publisher.modules;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.modules.xver.CodeChainsSorter;
import org.hl7.fhir.igtools.publisher.modules.xver.ColumnEntry;
import org.hl7.fhir.igtools.publisher.modules.xver.ColumnSorter;
import org.hl7.fhir.igtools.publisher.modules.xver.ElementDefinitionLink;
import org.hl7.fhir.igtools.publisher.modules.xver.ElementDefinitionPair;
import org.hl7.fhir.igtools.publisher.modules.xver.SourcedElementDefinition;
import org.hl7.fhir.igtools.publisher.modules.xver.StructureDefinitionColumn;
import org.hl7.fhir.igtools.publisher.modules.xver.XVerAnalysisEngine;
import org.hl7.fhir.igtools.publisher.modules.xver.XVerAnalysisEngine.MakeLinkMode;
import org.hl7.fhir.igtools.publisher.modules.xver.XVerAnalysisEngine.MultiConceptMapType;
import org.hl7.fhir.igtools.publisher.modules.xver.XVerAnalysisEngine.MultiRowRenderingContext;
import org.hl7.fhir.r5.conformance.profile.BindingResolution;
import org.hl7.fhir.r5.conformance.profile.ProfileKnowledgeProvider;
import org.hl7.fhir.r5.context.ContextUtilities;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionBindingComponent;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.GuidePageGeneration;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionPageComponent;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionContextComponent;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.r5.model.StructureMap.StructureMapModelMode;
import org.hl7.fhir.r5.model.StructureMap.StructureMapStructureComponent;
import org.hl7.fhir.r5.model.UrlType;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.renderers.ConceptMapRenderer;
import org.hl7.fhir.r5.renderers.ConceptMapRenderer.RenderMultiRowSortPolicy;
import org.hl7.fhir.r5.renderers.Renderer.RenderingStatus;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.GenerationRules;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.ResourceRendererMode;
import org.hl7.fhir.r5.utils.ResourceSorters;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.MarkDownProcessor.Dialect;
import org.hl7.fhir.utilities.StandardsStatus;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.ZipGenerator;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

import kotlin.NotImplementedError;

public class CrossVersionModule implements IPublisherModule, ProfileKnowledgeProvider {

  private XVerAnalysisEngine engine;
  private ContextUtilities cu;

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
      cleanup(path);
      if (engine.process(path)) {
        ImplementationGuide ig = (ImplementationGuide) new XmlParser().parse(new FileInputStream(Utilities.path(path, "input", "xver-ig.xml")));
        ImplementationGuideDefinitionPageComponent resPage = ig.getPageByName("cross-version-resources.html");
        ImplementationGuideDefinitionPageComponent dtPage = ig.getPageByName("cross-version-types.html");
        resPage.getPage().clear();
        dtPage.getPage().clear();
        
        cu = new ContextUtilities(engine.getVdr5());
        engine.logProgress("Generating fragments");
        FileUtilities.createDirectory(Utilities.path(path, "temp", "xver-qa"));
        genChainsHtml(path, "cross-version-chains-all", false, false);
        genChainsHtml(path, "cross-version-chains-valid", true, false);
        genChainsHtml(path, "cross-version-chains-min", true, true);

        for (StructureDefinition sd : engine.sortedSDs(engine.getVdr5().fetchResourcesByType(StructureDefinition.class))) {
          if ((sd.getKind() == StructureDefinitionKind.COMPLEXTYPE || sd.getKind() == StructureDefinitionKind.RESOURCE) && !sd.getAbstract() && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
            genVersionType(path, sd, sd.getKind() == StructureDefinitionKind.COMPLEXTYPE ? dtPage : resPage);
          }
        }

        engine.logProgress("Generating extensions");
        FileUtilities.createDirectory(Utilities.path(path, "temp", "xver", "x-extensions"));
        for (StructureDefinition sd : engine.getExtensions()) {
          new JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream(Utilities.path(path, "temp", "xver", "x-extensions", "StructureDefinition-"+sd.getId()+".json")), sd);
          genExtensionPage(path, sd);
        }  
        for (ValueSet vs : engine.getNewValueSets().values()) {
          new JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream(Utilities.path(path, "temp", "xver", "x-extensions", "ValueSet-"+vs.getId()+".json")), vs);
        }
        genSummaryPages(path);
        genZips(path); 
        new XmlParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream(Utilities.path(path, "input", "xver-ig.xml")), ig);

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

  private void genZips(String path) throws FileNotFoundException, IOException {
    ZipGenerator zip = new ZipGenerator(Utilities.path(path, "temp", "xver-qa", "sd.zip"));
    zip.addFiles(Utilities.path(path, "input", "extensions"), "", null, null);   
    zip.close();

    zip = new ZipGenerator(Utilities.path(path, "temp", "xver-qa", "cm.zip"));
    zip.addFiles(Utilities.path(path, "input", "codes"), "", null, null);   
    zip.addFiles(Utilities.path(path, "input", "elements"), "", null, null);   
    zip.addFiles(Utilities.path(path, "input", "resources"), "", null, null);   
    zip.addFiles(Utilities.path(path, "input", "types"), "", null, null);   
    zip.close();

    zip = new ZipGenerator(Utilities.path(path, "temp", "xver-qa", "sm.zip"));
    zip.addFiles(Utilities.path(path, "input", "R2toR3"), "R2toR3/", null, null);   
    zip.addFiles(Utilities.path(path, "input", "R3toR4"), "R3toR4/", null, null);   
    zip.addFiles(Utilities.path(path, "input", "R4toR5"), "R4toR5/", null, null);   
    zip.addFiles(Utilities.path(path, "input", "R4BtoR5"), "R4BtoR5/", null, null);   
    zip.addFiles(Utilities.path(path, "input", "R3toR2"), "R3toR2/", null, null);   
    zip.addFiles(Utilities.path(path, "input", "R4toR3"), "R4toR3/", null, null);   
    zip.addFiles(Utilities.path(path, "input", "R5toR4"), "R5toR4/", null, null);   
    zip.addFiles(Utilities.path(path, "input", "R5toR4B"), "R5toR4B/", null, null);   
    zip.close();      
  }

  private void cleanup(String path) throws IOException {
    FileUtilities.clearDirectory(Utilities.path(path, "input", "extensions"));
    File dir = new File(Utilities.path(path, "temp", "xver-qa"));
    if (dir.exists()) {
      for (File f : dir.listFiles()) {
        if (f.getName().endsWith(".html") && f.getName().contains("-")) {
          f.delete();
        }
      }
    }
  }

  private void genExtensionPage(String path, StructureDefinition sd) throws IOException {
    String fn = Utilities.path(path, "temp", "xver-qa", "StructureDefinition-"+sd.getId()+".html");
    if (new File(fn).exists()) {
      throw new FHIRException("Duplicate id: "+sd.getId());
    }
    XhtmlNode body = new XhtmlNode(NodeType.Element, "div");  
    body.h1().tx(sd.getTitle());
    var tbl = body.table("grid", false);
    var tr = tbl.tr();
    tr.td().b().tx("URL");
    tr.td().tx(sd.getUrl());
    tr = tbl.tr();
    tr.td().b().tx("Version");
    tr.td().tx(sd.getVersion());
    tr = tbl.tr();
    tr.td().b().tx("Status");
    tr.td().tx(sd.getStatus().toCode());
    tr = tbl.tr();
    tr.td().b().tx("Description");
    tr.td().markdown(sd.getDescription(),sd.getName()+".description");
    
    body.para().b().tx("Context of Use");
    body.para().tx("This extension may be used in the following contexts:");
    XhtmlNode ul = body.ul();
    for (StructureDefinitionContextComponent ctxt : sd.getContext()) {
      var li = ul.li();
      li.tx(ctxt.getType().toCode());
      li.tx(" ");
      li.tx(ctxt.getExpression());
      if (ctxt.hasExtension(ExtensionDefinitions.EXT_APPLICABLE_VERSION)) {
        li.tx(" (");
        renderVersionRange(li, ctxt.getExtensionByUrl(ExtensionDefinitions.EXT_APPLICABLE_VERSION));
        li.tx(")");
      }
    }
    body.hr();
    
    RenderingContext rc = new RenderingContext(engine.getVdr5(), new MarkDownProcessor(Dialect.COMMON_MARK), null, "http://hl7.org/fhir", "", null, ResourceRendererMode.TECHNICAL, GenerationRules.IG_PUBLISHER);
    rc.setPkp(this);
    var sdr = new org.hl7.fhir.r5.renderers.StructureDefinitionRenderer(rc);
    body.add(sdr.generateTable(new RenderingStatus(), "todo", sd, true,  Utilities.path(path, "temp", "xver-qa"), false, "Extension", false, "http://hl7.org/fhir", "", false, false, null, false, rc, "", ResourceWrapper.forResource(rc.getContextUtilities(), sd), "CV"));
    body.hr();
    
    body.pre().tx(new JsonParser().setOutputStyle(OutputStyle.PRETTY).composeString(sd));
    FileUtilities.stringToFile(new XhtmlComposer(false, true).compose(wrapPage(body, sd.getName())), fn);
  }

  private void renderVersionRange(XhtmlNode x, Extension ext) {
    String sv = ext.hasExtension("startFhirVersion") ? ext.getExtensionString("startFhirVersion") : null;
    String ev = ext.hasExtension("endFhirVersion") ? ext.getExtensionString("endFhirVersion") : null;
    if (ev != null && ev.equals(sv)) {
      x.tx("For version "+VersionUtilities.getNameForVersion(ev));
    } else if (ev != null && sv != null) {
      x.tx("For versions "+VersionUtilities.getNameForVersion(sv)+" to "+VersionUtilities.getNameForVersion(ev));
    } else if (ev == null && sv != null) {
      x.tx("For versions "+VersionUtilities.getNameForVersion(sv)+" onwards");
    } else if (ev == null && sv != null) {
      x.tx("For versions until "+VersionUtilities.getNameForVersion(ev));
    } else {
      x.tx("For unknown versions");
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
    FileUtilities.stringToFile(new XhtmlComposer(false, true).compose(body), Utilities.path(path, "input", "includes", filename+".xhtml"));
    FileUtilities.stringToFile(new XhtmlComposer(false, true).compose(wrapPage(body, "FHIR Cross Version Extensions")), Utilities.path(path, "temp", "xver-qa", filename+".html"));
  }


  private void renderElementDefinition(XhtmlNode x, SourcedElementDefinition ed) {
    String ver = VersionUtilities.getMajMin(ed.getVer());
    String prefix = VersionUtilities.getNameForVersion(ver).toLowerCase();
    IWorkerContext ctxt = engine.getVersions().get(prefix);

    XhtmlNode x1 = ed.isValid() ? x : x.strikethrough();

    x1.code("http://hl7.org/fhir/"+ver+"/StructureDefinition/extension-"+ed.getEd().getPath());

    boolean first = true;
    for (TypeRefComponent t : ed.getEd().getType()) {
      StructureDefinition sd = ctxt.fetchTypeDefinition(t.getWorkingCode());
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
      if (ed.getStatusReason() != null && !"No Change".equals(ed.getStatusReason())) {
        x.tx(" (because "+ed.getStatusReason()+")");
      }
    } else {
      if (ed.getStatusReason() != null && !"??".equals(ed.getStatusReason())) {
        x.tx(" Not valid because "+ed.getStatusReason());
      } else {
        x.tx(" Not valid because No Change");        
      }
    }
  }

  
  private void genVersionType(String path, StructureDefinition sd, ImplementationGuideDefinitionPageComponent page) throws IOException {
    XhtmlNode body = new XhtmlNode(NodeType.Element, "div");  
    body.h1().tx(sd.getName());
    body.para().tx("FHIR Cross-version Mappings for "+sd.getType()+" based on the R5 structure");
    XhtmlNode tbl = body.table("grid", false);
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

    tr = tbl.tr();
    for (StructureDefinitionColumn col : columns) {
      XhtmlNode td = tr.td().colspan(col.isRoot() ? 1 : 2);
      List<StructureMap> inList = findStructureMaps(col.getSd().getType(), col.getSd().getFhirVersion().toCode(), true);
      List<StructureMap> outList = findStructureMaps(col.getSd().getType(), col.getSd().getFhirVersion().toCode(), false);
      if (inList.size() > 0 || outList.size() > 0) {
        td.tx("Links:");
        for (StructureMap sm : inList) {
          td.tx(" ");
          td.ah(sm.getWebPath()).tx(versionSummary(sm));
        }
      }
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
      ed.setUserData(UserDataNames.xver_rows, rows);
      renderElementRow(sd, columns, ed, rowCount, rows);
    }

    // ok, now we look for any other terminating elements that didn't make it into the R5 structure, and inject them into the table
    List<SourcedElementDefinition> missingChains = findMissingTerminatingElements(sd, columns);
    for (SourcedElementDefinition m : missingChains) {
      StructureDefinitionColumn rootCol = engine.getColumn(columns, m.getSd());
      ElementDefinition prevED = findPreviousElement(m, rootCol.getElements()); // find the anchor that knows which rows we'll be inserting under
      List<XhtmlNode> rows = (List<XhtmlNode>) prevED.getUserData(UserDataNames.xver_rows);
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
      m.getEd().setUserData(UserDataNames.xver_rows, rows);
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
          link.getNextCM().setUserData(UserDataNames.render_presentation,
              VersionUtilities.getNameForVersion(tgtscope)+" "+tgtscope.substring(tgtscope.lastIndexOf("#")+1));
          maps.add(link.getNextCM());
        }
      }
      body.hr();
      body.add(ConceptMapRenderer.renderMultipleMaps(name, maps, engine, new MultiRowRenderingContext(MultiConceptMapType.CODED, RenderMultiRowSortPolicy.UNSORTED, links)));
    }

    FileUtilities.stringToFile(new XhtmlComposer(false, true).compose(body), Utilities.path(path, "input", "includes", "cross-version-"+sd.getName()+".xhtml"));
    FileUtilities.stringToFile("{% include cross-version-"+sd.getName()+".xhtml %}\r\n", Utilities.path(path, "input", "pagecontent", "cross-version-"+sd.getName()+".md"));
    ImplementationGuideDefinitionPageComponent p = page.addPage();
    p.setSource(new UrlType("cross-version-"+sd.getName()+".md"));
    p.setName("cross-version-"+sd.getName()+".html");
    p.setTitle("Cross-Version summary for "+sd.getName());
    p.setGeneration(GuidePageGeneration.MARKDOWN);
    ExtensionUtilities.setStandardsStatus(p, StandardsStatus.INFORMATIVE, null);
    FileUtilities.stringToFile(new XhtmlComposer(false, true).compose(wrapPage(body, sd.getName())), Utilities.path(path, "temp", "xver-qa", "cross-version-"+sd.getName()+".html"));
  }


  private String versionSummary(StructureMap sm) {
    String src = null;
    String tgt = null;
    for (StructureMapStructureComponent u : sm.getStructure()) {
      String v = VersionUtilities.getNameForVersion(VersionUtilities.getMajMin(u.getUrl()));
      if (u.getMode() == StructureMapModelMode.SOURCE) {
        src = v;
      } else if (u.getMode() == StructureMapModelMode.TARGET) {
        tgt = v;
      }
    }
    return "T:"+src+"->"+tgt;
  }

  private List<StructureMap> findStructureMaps(String type, String fhirVersion, boolean inwards) {
    List<StructureMap> res = new ArrayList<>();
    String v = VersionUtilities.getMajMin(fhirVersion);
    String url = "http://hl7.org/fhir/"+v+"/"+type;
    for (StructureMap sm : engine.getStructureMaps().values()) {
      for (StructureMapStructureComponent u : sm.getStructure()) {
        if (u.getUrl().equals(url)) {
          if ((inwards && u.getMode() == StructureMapModelMode.SOURCE) ||
              (!inwards && u.getMode() == StructureMapModelMode.TARGET)) {
            res.add(sm);
          }
        }
      }
    }
    Collections.sort(res, new ResourceSorters.CanonicalResourceSortByUrl());
    return res;
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
    if (link.getPrev().getEd().hasUserData(UserDataNames.xver_links+"."+MakeLinkMode.ORIGIN_CHAIN)) {
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
        XhtmlNode td1 = rendererElementForType(ctr.td(), col.isRoot() ? sd : col.getSd(), entry.getEd(), col.isRoot() ? engine.getVdr5() : engine.getVersions().get(VersionUtilities.getNameForVersion(col.getSd().getFhirVersion().toCode()).toLowerCase()), ed.getPath());
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
    XhtmlNode page = ConceptMapRenderer.renderMultipleMaps("R2 Resources", maps, engine, new MultiRowRenderingContext(MultiConceptMapType.SUMMARY, RenderMultiRowSortPolicy.FIRST_COL, "resources"));

    FileUtilities.stringToFile(new XhtmlComposer(false, true).compose(page), Utilities.path(path, "input", "includes", "cross-version-resources.xhtml"));
    FileUtilities.stringToFile(new XhtmlComposer(false, true).compose(wrapPage(page, "Resource Map")), Utilities.path(path, "temp", "xver-qa", "cross-version-resources.html"));

    maps = new ArrayList<>();
    maps.add(engine.cm("types-2to3"));
    maps.add(engine.cm("types-3to4"));
    maps.add(engine.cm("types-4to4b"));
    maps.add(engine.cm("types-4bto5"));
    page = ConceptMapRenderer.renderMultipleMaps("R2 DataTypes", maps, engine, new MultiRowRenderingContext(MultiConceptMapType.SUMMARY, RenderMultiRowSortPolicy.FIRST_COL, "types"));

    FileUtilities.stringToFile(new XhtmlComposer(false, true).compose(page), Utilities.path(path, "input", "includes", "cross-version-types.xhtml"));
    FileUtilities.stringToFile(new XhtmlComposer(false, true).compose(wrapPage(page, "Type Map")), Utilities.path(path, "temp", "xver-qa", "cross-version-types.html"));

  }

  private XhtmlNode rendererElementForType(XhtmlNode td, StructureDefinition sdt, ElementDefinition ed, IWorkerContext context, String origName) {
    if (ed.getPath().contains(".")) {
      SourcedElementDefinition sed = (SourcedElementDefinition) ed.getUserData(UserDataNames.xver_sed);
      if (sed != null) {
        if (sed.getExtension() == null) {
          td.imgT("icon-extension-no.png", "No cross-version extension allowed for this element because "+sed.getStatusReason());
        } else {
          td.ah("StructureDefinition-"+sed.getExtension().getId()+".html").imgT("icon-extension.png", "Extension definition for this version of the element. Defined because: "+sed.getStatusReason());
        }
        td.tx(" ");
      }
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
        StructureDefinition sd = context.fetchTypeDefinition(t.getWorkingCode());
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
      return "cross-version-types.html";
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

  @Override
  public boolean isDatatype(String typeSimple) {
    return !cu.isDatatype(typeSimple);
  }

  @Override
  public boolean isPrimitiveType(String typeSimple) {
    return cu.isPrimitiveType(typeSimple);
  }

  @Override
  public boolean isResource(String typeSimple) {
    return cu.isResource(typeSimple);
  }

  @Override
  public boolean hasLinkFor(String typeSimple) {
    return !cu.isPrimitiveType(typeSimple);
  }

  @Override
  public String getLinkFor(String corePath, String typeSimple) {
    return Utilities.pathURL(corePath, typeSimple.toLowerCase()+".html");
  }

  @Override
  public BindingResolution resolveBinding(StructureDefinition def, ElementDefinitionBindingComponent binding, String path) throws FHIRException {
    return new BindingResolution("todo", "todo");
  }

  @Override
  public BindingResolution resolveBinding(StructureDefinition def, String url, String path) throws FHIRException {
    return new BindingResolution("todo", "todo");
  }

  @Override
  public String getLinkForProfile(StructureDefinition profile, String url) {
    return profile.getWebPath();
  }
  
  @Override
  public boolean prependLinks() {
    return false;
  }

  @Override
  public String getLinkForUrl(String corePath, String s) {
    throw new NotImplementedError();
  }

  @Override
  public void defineTypeMap(Map<String, String> typeMap) {
    typeMap.putAll(engine.getTypeMap());
    
  }

  @Override
  public boolean resolve(String ref) {
    if (ref.contains("#")) {
      ref = ref.substring(0, ref.indexOf("#"));
    }
    if (ref.startsWith("StructureDefinition-xv-")) {
      return true;
    }
    if (Utilities.existsInList(ref,
        "http://hl7.org/fhir/1.0/elements", "http://hl7.org/fhir/3.0/elements", "http://hl7.org/fhir/4.0/elements", "http://hl7.org/fhir/4.3/elements", "http://hl7.org/fhir/5.0/elements",
        "http://hl7.org/fhir/1.0/element-names", "http://hl7.org/fhir/3.0/element-names", "http://hl7.org/fhir/4.0/element-names", "http://hl7.org/fhir/4.3/element-names", "http://hl7.org/fhir/5.0/element-names",
        "http://hl7.org/fhir/1.0/SearchParameter", "http://hl7.org/fhir/3.0/SearchParameter", "http://hl7.org/fhir/4.0/SearchParameter", "http://hl7.org/fhir/4.3/SearchParameter", "http://hl7.org/fhir/5.0/SearchParameter",
        "http://hl7.org/fhir/1.0/SearchParameter-codes", "http://hl7.org/fhir/3.0/SearchParameter-codes", "http://hl7.org/fhir/4.0/SearchParameter-codes", "http://hl7.org/fhir/4.3/SearchParameter-codes", "http://hl7.org/fhir/5.0/SearchParameter-codes",
        "http://hl7.org/fhir/5.0/resource-types")) {
      return true;
    }
    if (ref.startsWith("http://hl7.org/fhir/1.0/")) {
      String tail = ref.replace("http://hl7.org/fhir/1.0/", "");
      if (engine.getVdr2().getResourceNamesAsSet().contains(tail) || engine.getVdr2().fetchTypeDefinition(tail) != null) {
        return true;
      }
      return engine.getVdr2().fetchResource(Resource.class, ref.replace("/1.0/", "/")) != null;
    }
    if (ref.startsWith("http://hl7.org/fhir/3.0/")) {
      String tail = ref.replace("http://hl7.org/fhir/3.0/", "");
      if (engine.getVdr3().getResourceNamesAsSet().contains(tail) || engine.getVdr3().fetchTypeDefinition(tail) != null) {
        return true;
      }
      return engine.getVdr3().fetchResource(Resource.class, ref.replace("/3.0/", "/")) != null;
    }
    if (ref.startsWith("http://hl7.org/fhir/4.0/")) {
      String tail = ref.replace("http://hl7.org/fhir/4.0/", "");
      if (engine.getVdr4().getResourceNamesAsSet().contains(tail) || engine.getVdr4().fetchTypeDefinition(tail) != null) {
        return true;
      }
      return engine.getVdr4().fetchResource(Resource.class, ref.replace("/4.0/", "/")) != null;
    }
    if (ref.startsWith("http://hl7.org/fhir/4.3/")) {
      String tail = ref.replace("http://hl7.org/fhir/4.3/", "");
      if (engine.getVdr4b().getResourceNamesAsSet().contains(tail) || engine.getVdr4b().fetchTypeDefinition(tail) != null) {
        return true;
      }
      return engine.getVdr4b().fetchResource(Resource.class, ref.replace("/4.3/", "/")) != null;
    }
    if (ref.startsWith("http://hl7.org/fhir/5.0/")) {
      String tail = ref.replace("http://hl7.org/fhir/5.0/", "");
      if (engine.getVdr5().getResourceNamesAsSet().contains(tail) || engine.getVdr5().fetchTypeDefinition(tail) != null) {
        return true;
      }
      return engine.getVdr5().fetchResource(Resource.class, ref.replace("/5.0/", "/")) != null;
    }
    return false;
  }

  @Override
  public CanonicalResource fetchCanonicalResource(String ref) {

    if (ref.startsWith("http://hl7.org/fhir/1.0/")) {
      String tail = ref.replace("http://hl7.org/fhir/1.0/", "");
      StructureDefinition sd = engine.getVdr2().fetchTypeDefinition(tail);
      if (sd != null) {
        return sd;
      }
      return (CanonicalResource) engine.getVdr2().fetchResource(Resource.class, ref.replace("/1.0/", "/"));
    }
    if (ref.startsWith("http://hl7.org/fhir/3.0/")) {
      String tail = ref.replace("http://hl7.org/fhir/3.0/", "");
      StructureDefinition sd = engine.getVdr3().fetchTypeDefinition(tail);
      if (sd != null) {
        return sd;
      }
      return (CanonicalResource) engine.getVdr3().fetchResource(Resource.class, ref.replace("/3.0/", "/"));
    }
    if (ref.startsWith("http://hl7.org/fhir/4.0/")) {
      String tail = ref.replace("http://hl7.org/fhir/4.0/", "");
      StructureDefinition sd = engine.getVdr4().fetchTypeDefinition(tail);
      if (sd != null) {
        return sd;
      }
      return (CanonicalResource) engine.getVdr4().fetchResource(Resource.class, ref.replace("/4.0/", "/"));
    }
    if (ref.startsWith("http://hl7.org/fhir/4.3/")) {
      String tail = ref.replace("http://hl7.org/fhir/4.3/", "");
      StructureDefinition sd = engine.getVdr4b().fetchTypeDefinition(tail);
      if (sd != null) {
        return sd;
      }
      return (CanonicalResource) engine.getVdr4b().fetchResource(Resource.class, ref.replace("/4.3/", "/"));
    }
    if (ref.startsWith("http://hl7.org/fhir/5.0/")) {
      String tail = ref.replace("http://hl7.org/fhir/5.0/", "");
      StructureDefinition sd = engine.getVdr5().fetchTypeDefinition(tail);
      if (sd != null) {
        return sd;
      }
      return (CanonicalResource) engine.getVdr5().fetchResource(Resource.class, ref.replace("/5.0/", "/"));
    }
    return null;
  }
  

  private Set<String> APPROVED_FRAGMENTS = Set.of("cld", "content", "ctxts", "dict", "diff", "example-list", "example-list-all", "example-table", "example-table-all", "expansion", "form",
      "grid", "header", "history", "html", "idempotence", "instance-table", "inv", "ip-statements", "java", "jekyll-data", "json", "json-html",
      "json-schema", "links", "list-list", "list-list-simple", "list-list-table", "logic", "maturity", "openapi", "process-diagram", "processes",
      "profiles", "pseudo-json", "pseudo-ttl", "pseudo-xml", "responses", "sch", "script", "script-plain", "sheet", "shex", "snapshot", "span",
      "spanall", "status", "summary", "summary-table", "swagger", "tree", "tx", "tx-diff", "tx-diff-must-support", "tx-key", "tx-must-support", "uml",
      "uses", "validate", "xml", "xml-html", "xref" );

  @Override
  public boolean approveFragment(boolean value, String code) {
    if (value) {
      return APPROVED_FRAGMENTS.contains(code);
    } else {
      return value;
    }
  }

  @Override
  public String getCanonicalForDefaultContext() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean isNoNarrative() {
    return true;
  }

  @Override
  public String getDefinitionsName(Resource r) {
    // TODO Auto-generated method stub
    return null;
  }
  
  
}
