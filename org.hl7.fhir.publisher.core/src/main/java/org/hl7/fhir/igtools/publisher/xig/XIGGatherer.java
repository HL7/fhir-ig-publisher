package org.hl7.fhir.igtools.publisher.xig;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_10_50;
import org.hl7.fhir.convertors.analytics.PackageVisitor.IPackageVisitorProcessor;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_10_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_14_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_43_50;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.IGR2ConvertorAdvisor5;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.PackageInformation;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageHacker;

public class XIGGatherer implements IPackageVisitorProcessor {

  private JsonObject registry;
  private JsonArray packages;
  private JsonArray resources;

  private String dest;
  private Map<String, SpecMapManager> smmList = new HashMap<>();

  public XIGGatherer(String dest) {
    super();
    this.registry = new JsonObject();
    packages = new JsonArray();
    resources = new JsonArray();
    registry.add("packages", packages);
    registry.add("resources", resources);
    this.dest = dest;
 }

  public void finish() throws IOException {
    org.hl7.fhir.utilities.json.parser.JsonParser.compose(registry, Utilities.pathFile(dest, "cache.json"), true);
  }
  
  @Override
  public void processResource(String pid, NpmPackage npm, String version, String type, String id, byte[] content)
      throws FHIRException, IOException, EOperationOutcome {

    SpecMapManager smm = smmList.get(pid);
    if (smm == null) {
      smm = npm.hasFile("other", "spec.internals") ?  new SpecMapManager( TextFile.streamToBytes(npm.load("other", "spec.internals")), npm.fhirVersion()) : SpecMapManager.createSpecialPackage(npm);
      smm.setName(npm.name());
      smm.setBase(npm.canonical());
      smm.setBase2(PackageHacker.fixPackageUrl(npm.url()));
      smmList.put(pid, smm);
      JsonObject p = new JsonObject();
      packages.add(p);
      p.add("pid", pid);
      p.add("id", npm.name());
      p.add("date", npm.date());
      p.add("title", npm.title());
      p.add("canonical", npm.canonical());
      p.add("web", npm.getWebLocation());
      p.add("version", npm.version());
      p.add("fhirVersion", npm.fhirVersionList());
      p.add("fver", npm.fhirVersion());
      p.add("realm", getRealm(pid));
      p.add("auth", getAuth(pid));
      p.add("src", npm.getNpm());
    }
        
    Resource r = loadResource(pid, version, type, id, content);
    if (r != null && r instanceof CanonicalResource) {
      r.setSourcePackage(new PackageInformation(npm));
      CanonicalResource cr = (CanonicalResource) r;
      if (cr.getUrl() != null) {
        boolean core = isCoreDefinition(cr, pid);
        if (!core) {
          cr.setText(null);
          String base = (cr.fhirType()+"-"+pid.substring(0, pid.indexOf("#"))+"-"+cr.getId()).toLowerCase();
          String tgt = Utilities.path(dest, base+".json");
          
          JsonObject p = new JsonObject();
          resources.add(p);
          p.add("file", base);
          p.add("url", cr.getUrl());
          p.add("version", cr.getVersion());          
          p.add("pid", pid);
          p.add("web", Utilities.pathURL(smm.getBase(), smm.getPath(cr.getUrl(), null, cr.fhirType(), cr.getIdBase())));
          FileOutputStream fo = new FileOutputStream(tgt);
          new JsonParser().compose(fo, r);
          fo.close();
        }
      }
    }
  }

  private boolean isCoreDefinition(CanonicalResource cr, String pid) {
    return Utilities.startsWithInList(pid, "hl7.fhir.r2", "hl7.fhir.r2b", "hl7.fhir.r3", "hl7.fhir.r4", "hl7.fhir.r4b", "hl7.fhir.r5", "hl7.fhir.r6", "hl7.fhir.xver");
  }

  private Resource loadResource(String pid, String parseVersion, String type, String id, byte[] source) {
    try {
      if (parseVersion.equals("current")) {
        return null;
      }
      if (VersionUtilities.isR3Ver(parseVersion)) {
        org.hl7.fhir.dstu3.model.Resource res;
        res = new org.hl7.fhir.dstu3.formats.JsonParser(true).parse(source);
        return VersionConvertorFactory_30_50.convertResource(res);
      } else if (VersionUtilities.isR4Ver(parseVersion)) {
        org.hl7.fhir.r4.model.Resource res;
        res = new org.hl7.fhir.r4.formats.JsonParser(true, true).parse(source);
        return VersionConvertorFactory_40_50.convertResource(res);
      } else if (VersionUtilities.isR2BVer(parseVersion)) {
        org.hl7.fhir.dstu2016may.model.Resource res;
        res = new org.hl7.fhir.dstu2016may.formats.JsonParser(true).parse(source);
        return VersionConvertorFactory_14_50.convertResource(res);
      } else if (VersionUtilities.isR2Ver(parseVersion)) {
        org.hl7.fhir.dstu2.model.Resource res;
        res = new org.hl7.fhir.dstu2.formats.JsonParser(true).parse(source);

        BaseAdvisor_10_50 advisor = new IGR2ConvertorAdvisor5();
        return VersionConvertorFactory_10_50.convertResource(res, advisor);
      } else if (VersionUtilities.isR4BVer(parseVersion)) {
        org.hl7.fhir.r4b.model.Resource res;
        res = new org.hl7.fhir.r4b.formats.JsonParser(true).parse(source);
        return VersionConvertorFactory_43_50.convertResource(res);
      } else if (VersionUtilities.isR5Plus(parseVersion)) {
        return new JsonParser(true, true).parse(source);
      } else if (Utilities.existsInList(parseVersion, "4.6.0", "3.5.0", "1.8.0")) {
        return null;
      } else {
        throw new Exception("Unsupported version "+parseVersion);
      }    

    } catch (Exception e) {
      System.out.println("Error loading "+type+"/"+id+" from "+pid+"("+parseVersion+"):" +e.getMessage());
      // e.printStackTrace();
      return null;
    }
  }

  private String getAuth(String pid) {
    if (pid.startsWith("hl7.") || pid.startsWith("fhir.") || pid.startsWith("ch.fhir.")) {
      return "hl7";
    }
    if (pid.startsWith("ihe.")) {
      return "ihe";
    }
    return null;
  }

  private String getRealm(String pid) {
    if (pid.startsWith("hl7.fhir.")) {
      return pid.split("\\.")[2];
    }
    if (pid.startsWith("fhir.") || pid.startsWith("us.")) {
      return "us";
    }
    if (pid.startsWith("ch.fhir.")) {
      return "ch";
    }

    return null;
  }
}
