package org.hl7.fhir.igtools.renderers;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.cache.BasePackageCacheManager;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.cache.PackageHacker;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.Row;
import org.hl7.fhir.utilities.xhtml.HierarchicalTableGenerator.TableModel;
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
    
    String realm = determineRealmForIg(ig.getPackageId());

    Row row = addBaseRow(gen, model, ig);
    for (ImplementationGuideDependsOnComponent d : ig.getDependsOn()) {
      try {
        NpmPackage p = resolve(d);
        addPackageRow(gen, row.getSubRows(), p, d.getVersion(), realm);
      } catch (Exception e) {
        addErrorRow(gen, row.getSubRows(), d.getPackageId(), d.getVersion(), d.getUri(), null, e.getMessage());
      }
    }
    // create the table
    // add the rows 
    // render it       
    XhtmlNode x = gen.generate(model, dstFolder, 0, null);
    return new XhtmlComposer(false).compose(x);
  }

  private String determineRealmForIg(String packageId) {
    if (packageId.startsWith("hl7.")) {
      String[] p = packageId.split("\\.");
      if (p.length > 2 && "fhir".equals(p[1])) {
        if (Utilities.existsInList(p[2], "us", "au", "ch", "be", "nl", "fr", "de") ) {
          return p[2];
        }
        return "uv";
      }
    }
    return null;
  }

  private void addPackageRow(HierarchicalTableGenerator gen, List<Row> rows, NpmPackage npm, String originalVersion, String realm) throws FHIRException, IOException {
    if (!npm.isCore()) {
      String idv = npm.name()+"#"+npm.version();
      boolean isNew = !ids.contains(idv);
      ids.add(idv);
      String comment = "";
      if (!isNew) {
        comment = "see above";
      } else if (!VersionUtilities.versionsCompatible(fver, npm.fhirVersion())) {
        comment = "FHIR Version Mismatch";
      } else if ("current".equals(npm.version())) {
        comment = "Cannot be published with a dependency on a current build version";
      } else if (!npm.version().equals(originalVersion)) {
        comment = "Matched to latest patch release";
      } else if (realm != null) {
        String drealm = determineRealmForIg(npm.name());
        if (drealm != null) {
          if ("uv".equals(realm)) {
            if (!"uv".equals(drealm)) {
              comment = "An international realm (uv) publication cannot depend on a realm specific guide ("+drealm+")";
            }
          } else if (!"uv".equals(drealm) && !realm.equals(drealm)) {
            comment = "An realm publication for "+realm+" should not depend on a realm specific guide from ("+drealm+")";
          }
        }
      }
      Row row = addRow(gen, rows, npm.name(), npm.version(), "current".equals(npm.version()), npm.fhirVersion(), !VersionUtilities.versionsCompatible(fver, npm.fhirVersion()), npm.canonical(), PackageHacker.fixPackageUrl(npm.getWebLocation()), comment);
      if (isNew) {
        for (String d : npm.dependencies()) {
          String id = d.substring(0, d.indexOf("#"));
          String version = d.substring(d.indexOf("#")+1);
          try {
            NpmPackage p = resolve(id, version);
            addPackageRow(gen, row.getSubRows(), p, d.substring(d.indexOf("#")+1), realm);
          } catch (Exception e) {
            addErrorRow(gen, row.getSubRows(), id, version, null, null, e.getMessage());
          }
        }
      }
    }
  }

  private NpmPackage resolve(ImplementationGuideDependsOnComponent d) throws FHIRException, IOException {
    if (d.hasPackageId()) {
      return resolve(d.getPackageId(), d.getVersion());
    }
    String pid = pcm.getPackageId(d.getUri());
    if (pid == null) {
      throw new FHIRException("Unable to resolve canonical URL to package Id");
    }
    return resolve(pid, d.getVersion());
  }

  private NpmPackage resolve(String id, String version) throws FHIRException, IOException {
    if (VersionUtilities.isCorePackage(id)) {
      version = VersionUtilities.getCurrentVersion(version);
      return pcm.loadPackage(id, version);      
    } else {
      return pcm.loadPackage(id, version);
    }
  }

  private Row addBaseRow(HierarchicalTableGenerator gen, TableModel model, ImplementationGuide ig) {
    String id = ig.getPackageId();
    String ver = ig.getVersion();
    String fver = ig.getFhirVersion().get(0).asStringValue();
    this.fver = fver;
    String canonical = ig.getUrl();
    String web = ig.getManifest().getRendering();
    if (canonical.contains("/ImplementationGuide/")) {
      canonical = canonical.substring(0, canonical.indexOf("/ImplementationGuide/"));
    }
    return addRow(gen, model.getRows(), id, ver, false, fver, false, canonical, web, "");
  }

  private Row addRow(HierarchicalTableGenerator gen, List<Row> rows, String id, String ver, boolean verError, String fver, boolean fverError, String canonical, String web, String problems) {
    Row row = gen.new Row();
    rows.add(row);
    row.setIcon("icon-fhir-16.png", "NPM Package");
    row.getCells().add(gen.new Cell(null, null, id, null, null));
    row.getCells().add(gen.new Cell(null, null, ver, null, null));
    row.getCells().add(gen.new Cell(null, null, fver, null, null));
    row.getCells().add(gen.new Cell(null, null, canonical, null, null));
    row.getCells().add(gen.new Cell(null, null, web, null, null));
    row.getCells().add(gen.new Cell(null, null, problems, null, null));
    if (verError) {
      row.getCells().get(1).addStyle("background-color: #ffcccc");
    }
    if (fverError) {
      row.getCells().get(2).addStyle("background-color: #ffcccc");
    }

    return row;
  }

  private void addErrorRow(HierarchicalTableGenerator gen, List<Row> rows, String id, String ver, String uri, String web, String message) {
    Row row = gen.new Row();
    rows.add(row);
    row.setIcon("icon-fhir-16.png", "NPM Package");
    row.getCells().add(gen.new Cell(null, null, id, null, null));
    row.getCells().add(gen.new Cell(null, null, ver, null, null));
    row.getCells().add(gen.new Cell(null, null, null, null, null));
    row.getCells().add(gen.new Cell(null, null, uri, null, null));
    row.getCells().add(gen.new Cell(null, null, web, null, null));
    row.getCells().add(gen.new Cell(null, null, message, null, null));
    row.setColor("#ffcccc");
  }

  private TableModel createTable(HierarchicalTableGenerator gen) {
    TableModel model = gen.new TableModel("dep", false);
    
    model.setAlternating(true);
    model.getTitles().add(gen.new Title(null, null, "Package", "The NPM Package Id", null, 0));
    model.getTitles().add(gen.new Title(null, null, "Version", "The version of the package", null, 0));
    model.getTitles().add(gen.new Title(null, null, "FHIR Release", "The version of FHIR that the package is based on", null, 0));
    model.getTitles().add(gen.new Title(null, null, "Canonical", "Canonical URL", null, 0));
    model.getTitles().add(gen.new Title(null, null, "Web Base", "Web Reference Base", null, 0));
    model.getTitles().add(gen.new Title(null, null, "Comment", "Comments about this entry", null, 0));
    return model;
  }
}
