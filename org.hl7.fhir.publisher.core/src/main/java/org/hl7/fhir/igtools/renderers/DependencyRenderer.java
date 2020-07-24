package org.hl7.fhir.igtools.renderers;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.cache.BasePackageCacheManager;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.Cell;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.Row;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.TableModel;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.Title;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class DependencyRenderer {

  private BasePackageCacheManager pcm;
  private String dstFolder;
  private Set<String> ids = new HashSet<>();
  private String fver;
  
  public DependencyRenderer(BasePackageCacheManager pcm, String dstFolder) {
    super();
    this.pcm = pcm;
    this.dstFolder = dstFolder;
  }

  public String render(ImplementationGuide ig) throws FHIRException, IOException {
    HierarchicalTableGenerator gen = new HierarchicalTableGenerator(dstFolder, true, true);
    TableModel model = createTable(gen);

    Row row = addBaseRow(gen, model, ig);
    for (ImplementationGuideDependsOnComponent d : ig.getDependsOn()) {
      addPackageRow(gen, row.getSubRows(), resolve(d));
    }
    // create the table
    // add the rows 
    // render it       
    XhtmlNode x = gen.generate(model, dstFolder, 0, null);
    return new XhtmlComposer(false).compose(x);
  }

  private void addPackageRow(HierarchicalTableGenerator gen, List<Row> rows, NpmPackage npm) throws FHIRException, IOException {
    if (!npm.isCore()) {
      String idv = npm.name()+"#"+npm.version();
      boolean isNew = !ids.contains(idv);
      ids.add(idv);
      String comment = "";
      if (!isNew) {
        comment = "see above";
      } else if (!fver.equals(npm.fhirVersion())) {
        comment = "FHIR Version Mismatch";
      } else if ("current".equals(npm.version())) {
        comment = "Cannot be published with a dependency on a current build version";
      }
      Row row = addRow(gen, rows, npm.name(), npm.version(), "current".equals(npm.version()), npm.fhirVersion(), !VersionUtilities.versionsCompatible(fver, npm.fhirVersion()), npm.canonical(), comment);
      if (isNew) {
        for (String d : npm.dependencies()) {
          addPackageRow(gen, row.getSubRows(), resolve(d));
        }
      }
    }
  }

  private NpmPackage resolve(ImplementationGuideDependsOnComponent d) throws FHIRException, IOException {
    if (d.hasPackageId()) {
      return pcm.loadPackage(d.getPackageId(), d.getVersion());
    }
    throw new Error("Not supported yet");
  }

  private NpmPackage resolve(String dep) throws FHIRException, IOException {
    return pcm.loadPackage(dep);
  }

  private Row addBaseRow(HierarchicalTableGenerator gen, TableModel model, ImplementationGuide ig) {
    String id = ig.getPackageId();
    String ver = ig.getVersion();
    String fver = ig.getFhirVersion().get(0).asStringValue();
    this.fver = fver;
    String canonical = ig.getUrl();
    if (canonical.contains("/ImplementationGuide/")) {
      canonical = canonical.substring(0, canonical.indexOf("/ImplementationGuide/"));
    }
    return addRow(gen, model.getRows(), id, ver, false, fver, false, canonical, "");
  }

  private Row addRow(HierarchicalTableGenerator gen, List<Row> rows, String id, String ver, boolean verError, String fver, boolean fverError, String canonical, String problems) {
    Row row = gen.new Row();
    rows.add(row);
    row.setIcon("icon-fhir-16.png", "NPM Package");
    row.getCells().add(gen.new Cell(null, null, id, null, null));
    row.getCells().add(gen.new Cell(null, null, ver, null, null));
    row.getCells().add(gen.new Cell(null, null, fver, null, null));
    row.getCells().add(gen.new Cell(null, null, canonical, null, null));
    row.getCells().add(gen.new Cell(null, null, problems, null, null));
    if (verError) {
      row.getCells().get(1).addStyle("background-color: #ffcccc");
    }
    if (fverError) {
      row.getCells().get(2).addStyle("background-color: #ffcccc");
    }

    return row;
  }

  private TableModel createTable(HierarchicalTableGenerator gen) {
    TableModel model = gen.new TableModel("dep", false);
    
    model.setAlternating(true);
    model.getTitles().add(gen.new Title(null, null, "Package", "The NPM Package Id", null, 0));
    model.getTitles().add(gen.new Title(null, null, "Version", "The version of the package", null, 0));
    model.getTitles().add(gen.new Title(null, null, "FHIR Release", "The version of FHIR that the package is based on", null, 0));
    model.getTitles().add(gen.new Title(null, null, "Canonical", "Canonical URL", null, 0));
    model.getTitles().add(gen.new Title(null, null, "Comment", "Comments about this entry", null, 0));
    return model;
  }
}
