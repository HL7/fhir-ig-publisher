package org.hl7.fhir.igtools.publisher.xig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_10_50;
import org.hl7.fhir.convertors.analytics.PackageVisitor.IPackageVisitorProcessor;
import org.hl7.fhir.convertors.analytics.PackageVisitor.PackageContext;
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
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.JsonException;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageHacker;

public class XIGLoader implements IPackageVisitorProcessor {

  private XIGInformation info;
  private Map<String, SpecMapManager> smmList = new HashMap<>();

  public XIGLoader(XIGInformation info) {
    super();
    this.info = info;
  }

  @Override
  public void processResource(PackageContext context, Object clientContext, String type, String id, byte[] content) throws FHIRException, IOException, EOperationOutcome {
    String pid = context.getPid();
    String version = context.getVersion();
    NpmPackage npm = context.getNpm();

    SpecMapManager smm = smmList.get(pid);
    if (smm == null) {
      smm = npm.hasFile("other", "spec.internals") ?  new SpecMapManager( FileUtilities.streamToBytes(npm.load("other", "spec.internals")), npm.name(), npm.fhirVersion()) : SpecMapManager.createSpecialPackage(npm, null);
      smm.setName(npm.name());
      smm.setBase(npm.canonical());
      smm.setBase2(PackageHacker.fixPackageUrl(npm.url()));
      smmList.put(pid, smm);
      info.getJson().getJsonObject("packages").add(pid, npm.getNpm());
    }

    info.getPid().put(pid, npm.getWebLocation());
    Resource r = loadResource(pid, version, type, id, content);
    if (r != null && r instanceof CanonicalResource) {
      r.setSourcePackage(new PackageInformation(npm));
      CanonicalResource cr = (CanonicalResource) r;
      if (cr.getUrl() != null) {
        boolean core = isCoreDefinition(cr, pid);
        if (!core) {
          cr.setText(null);
          cr.setWebPath(Utilities.pathURL(smm.getBase(), smm.getPath(cr.getUrl(), null, cr.fhirType(), cr.getIdBase())));
          JsonObject j = new JsonObject();
          info.getJson().getJsonArray("canonicals").add(j);
          j.add("pid", pid);
          cr.setUserData("pid", pid);
          cr.setUserData("purl", npm.getWebLocation());
          cr.setUserData("pname", npm.title());
          cr.setUserData("fver", npm.fhirVersion());
          cr.setUserData("json", j);
          String realm = getRealm(pid);
          if (realm != null) {
            cr.setUserData("realm", realm);
            info.getJurisdictions().add(realm);
          }
          String auth = getAuth(pid);
          if (auth != null) {
            cr.setUserData("auth", auth);
          }
          cr.setUserData("filebase", (cr.fhirType()+"-"+pid.substring(0, pid.indexOf("#"))+"-"+cr.getId()).toLowerCase());
          j.add("fver", npm.fhirVersion());
          j.add("published", pid.contains("#current"));
          j.add("filebase", cr.getUserString("filebase"));
          j.add("path", cr.getWebPath());

          info.fillOutJson(pid, cr, j);
          if (info.getResources().containsKey(cr.getUrl())) {
            CanonicalResource crt = info.getResources().get(cr.getUrl());
            if (VersionUtilities.isThisOrLater(crt.getVersion(), cr.getVersion(), VersionUtilities.VersionPrecision.MINOR)) {
              info.getResources().put(cr.getUrl(), cr);
            }
          } else {
            info.getResources().put(cr.getUrl(), cr);
          }
          info.getCtxt().cacheResource(cr);
          String t = type;
          if (cr instanceof StructureDefinition) {
            StructureDefinition sd = (StructureDefinition) cr;
            if (sd.getKind() == StructureDefinitionKind.LOGICAL) {
              t = t + "/logical";
            } else if (sd.getType().equals("Extension")) {
              t = t + "/extension";
            } else if (sd.getKind() == StructureDefinitionKind.RESOURCE) {
              t = t + "/resource";
            } else {
              t = t + "/other";
            }
          } else if (cr instanceof ValueSet) {
            ValueSet vs = (ValueSet) cr;
            String sys = null;
            for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
              String s = null;
              String system = inc.getSystem();
              if (!Utilities.noString(system)) {
                if ("http://snomed.info/sct".equals(system)) {
                  s = "sct";
                } else if ("http://loinc.org".equals(system)) {
                  s = "loinc";
                } else if ("http://unitsofmeasure.org".equals(system)) {
                  s = "ucum";
                } else if ("http://hl7.org/fhir/sid/ndc".equals(system)) {
                  s = "ndc";
                } else if ("http://hl7.org/fhir/sid/cvx".equals(system)) {
                  s = "cvx";
                } else if (system.contains(":iso:")) {
                  s = "iso";
                } else if (system.contains(":ietf:")) {
                  s = "ietf";
                } else if (system.contains("ihe.net")) {
                  s = "ihe";
                } else if (system.contains("icpc")) {
                  s = "icpc";
                } else if (system.contains("ncpdp")) {
                  s = "ncpdp";
                } else if (system.contains("nucc")) {
                  s = "nucc";
                } else if (Utilities.existsInList(system, "http://hl7.org/fhir/sid/icd-9-cm", "http://hl7.org/fhir/sid/icd-10", "http://fhir.de/CodeSystem/dimdi/icd-10-gm", "http://hl7.org/fhir/sid/icd-10-nl 2.16.840.1.113883.6.3.2", "http://hl7.org/fhir/sid/icd-10-cm")) {
                  s = "icd";
                } else if (system.contains("urn:oid:")) {
                  s = "oid";
                } else if ("http://unitsofmeasure.org".equals(system)) {
                  s = "ucum";
                } else if ("http://dicom.nema.org/resources/ontology/DCM".equals(system)) {
                  s = "dcm";
                } else if ("http://unitsofmeasure.org".equals(system)) {
                  s = "ucum";
                } else if ("http://www.ama-assn.org/go/cpt".equals(system)) {
                  s = "cpt";
                } else if ("http://www.nlm.nih.gov/research/umls/rxnorm".equals(system)) {
                  s = "rx";
                } else if (system.startsWith("http://terminology.hl7.org")) {
                  s = "tho";
                } else if (system.startsWith("http://hl7.org/fhir")) {
                  s = "fhir";
                } else if (npm.canonical() != null && system.startsWith(npm.canonical())) {
                  s = "internal";
                } else if (system.contains("example.org")) {
                  s = "example";
                } else {
                  s = "?";
                }
              } else if (inc.hasValueSet()) {
                s = "vs";
              }
              if (sys == null) {
                sys = s;
              } else if (!sys.equals(s)) {
                sys = "mixed";
              }
            }
            t = t + "/"+(sys == null ? "n/a" : sys);
          }
          if (!info.getCounts().containsKey(t)) {
            info.getCounts().put(t, new HashMap<>());
          }
          Map<String, CanonicalResource> list = info.getCounts().get(t);
          String url = cr.getUrl();
          if (url == null) {
            url = cr.getId();
          }
          list.put(url, cr);
        }
      }
    }
  }

  private boolean isExtension(CanonicalResource cr, String pid) {
    return pid.equals("hl7.fhir.uv.extensions");
    //    return  cr instanceof StructureDefinition && ProfileUtilities.isExtensionDefinition((StructureDefinition) cr);
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

  public void loadFromCache(String folder) throws JsonException, IOException, ParseException {
    Map<String, JsonObject> packages = new HashMap<String, JsonObject>();

    JsonObject registry = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(Utilities.pathFile(folder, "cache.json"));
    for (JsonObject j : registry.getJsonArray("packages").asJsonObjects()) {
      packages.put(j.asString("pid"), j);
      info.getJson().getJsonObject("packages").add(j.asString("pid"), j.getJsonObject("src"));
      info.getPid().put(j.asString("pid"), j.asString("web"));
    }
    System.out.print("Loading");
    int total = registry.getJsonArray("resources").size();
    int step = total / 100;
    int count = 0;

    for (JsonObject file : registry.getJsonArray("resources").asJsonObjects()) {
      if (count == step) {
        System.out.print(".");
        count = 0;
      } else {
        count++;
      }
      String pid = file.asString("pid");
      JsonObject npm = packages.get(pid); 

      File f = Utilities.pathFile(folder, file.asString("file")+".json");
      try {
        FileInputStream fo = new FileInputStream(f);
        CanonicalResource cr = (CanonicalResource) new JsonParser().parse(fo);
        fo.close();

        cr.setSourcePackage(new PackageInformation(npm.asString("id"), npm.asString("version"), npm.asString("fhirVersion"), toDate(npm.asString("date")), npm.asString("title"), npm.asString("canonical"), npm.asString("web")));
        cr.setWebPath(file.asString("web"));
        JsonObject j = new JsonObject();
        info.getJson().getJsonArray("canonicals").add(j);
        j.add("pid", pid);
        cr.setUserData("pid", pid);
        cr.setUserData("purl", npm.asString("web"));
        cr.setUserData("pname", npm.asString("title"));
        cr.setUserData("fver", npm.asString("fver"));
        cr.setUserData("json", j);
        String realm = getRealm(pid);
        if (realm != null) {
          cr.setUserData("realm", realm);
          info.getJurisdictions().add(realm);
        }
        String auth = getAuth(pid);
        if (auth != null) {
          cr.setUserData("auth", auth);
        }
        cr.setUserData("filebase", (cr.fhirType()+"-"+pid.substring(0, pid.indexOf("#"))+"-"+cr.getId()).toLowerCase());
        j.add("fver",  npm.asString("fver"));
        j.add("published", pid.contains("#current"));
        j.add("filebase", cr.getUserString("filebase"));
        j.add("path", cr.getWebPath());

        info.fillOutJson(pid, cr, j);
        if (info.getResources().containsKey(cr.getUrl())) {
          CanonicalResource crt = info.getResources().get(cr.getUrl());
          if (VersionUtilities.isThisOrLater(crt.getVersion(), cr.getVersion(), VersionUtilities.VersionPrecision.MINOR)) {
            info.getResources().put(cr.getUrl(), cr);
          }
        } else {
          info.getResources().put(cr.getUrl(), cr);
        }
        info.getCtxt().cacheResource(cr);
        String t = cr.fhirType();
        if (cr instanceof StructureDefinition) {
          StructureDefinition sd = (StructureDefinition) cr;
          if (sd.getKind() == StructureDefinitionKind.LOGICAL) {
            t = t + "/logical";
          } else if (sd.getType().equals("Extension")) {
            t = t + "/extension";
          } else if (sd.getKind() == StructureDefinitionKind.RESOURCE) {
            t = t + "/resource";
          } else {
            t = t + "/other";
          }
        } else if (cr instanceof ValueSet) {
          ValueSet vs = (ValueSet) cr;
          String sys = null;
          for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
            String s = null;
            String system = inc.getSystem();
            if (!Utilities.noString(system)) {
              if ("http://snomed.info/sct".equals(system)) {
                s = "sct";
              } else if ("http://loinc.org".equals(system)) {
                s = "loinc";
              } else if ("http://unitsofmeasure.org".equals(system)) {
                s = "ucum";
              } else if ("http://hl7.org/fhir/sid/ndc".equals(system)) {
                s = "ndc";
              } else if ("http://hl7.org/fhir/sid/cvx".equals(system)) {
                s = "cvx";
              } else if (system.contains(":iso:")) {
                s = "iso";
              } else if (system.contains(":ietf:")) {
                s = "ietf";
              } else if (system.contains("ihe.net")) {
                s = "ihe";
              } else if (system.contains("icpc")) {
                s = "icpc";
              } else if (system.contains("ncpdp")) {
                s = "ncpdp";
              } else if (system.contains("nucc")) {
                s = "nucc";
              } else if (Utilities.existsInList(system, "http://hl7.org/fhir/sid/icd-9-cm", "http://hl7.org/fhir/sid/icd-10", "http://fhir.de/CodeSystem/dimdi/icd-10-gm", "http://hl7.org/fhir/sid/icd-10-nl 2.16.840.1.113883.6.3.2", "http://hl7.org/fhir/sid/icd-10-cm")) {
                s = "icd";
              } else if (system.contains("urn:oid:")) {
                s = "oid";
              } else if ("http://unitsofmeasure.org".equals(system)) {
                s = "ucum";
              } else if ("http://dicom.nema.org/resources/ontology/DCM".equals(system)) {
                s = "dcm";
              } else if ("http://unitsofmeasure.org".equals(system)) {
                s = "ucum";
              } else if ("http://www.ama-assn.org/go/cpt".equals(system)) {
                s = "cpt";
              } else if ("http://www.nlm.nih.gov/research/umls/rxnorm".equals(system)) {
                s = "rx";
              } else if (system.startsWith("http://terminology.hl7.org")) {
                s = "tho";
              } else if (system.startsWith("http://hl7.org/fhir")) {
                s = "fhir";
              } else if (npm.asString("canonical") != null && system.startsWith(npm.asString("canonical"))) {
                s = "internal";
              } else if (system.contains("example.org")) {
                s = "example";
              } else {
                s = "?";
              }
            } else if (inc.hasValueSet()) {
              s = "vs";
            }
            if (sys == null) {
              sys = s;
            } else if (!sys.equals(s)) {
              sys = "mixed";
            }
          }
          t = t + "/"+(sys == null ? "n/a" : sys);
        }
        if (!info.getCounts().containsKey(t)) {
          info.getCounts().put(t, new HashMap<>());
        }
        Map<String, CanonicalResource> list = info.getCounts().get(t);
        String url = cr.getUrl();
        if (url == null) {
          url = cr.getId();
        }
        list.put(url, cr);
      } catch (Exception e) {
        System.out.println("Error loading "+f.getName()+": "+e.getMessage());
        e.printStackTrace();
      }
    }
    System.out.println("Done");
  }

  private Date toDate(String d) throws ParseException {
    if (d == null) {
      return null;
    } else {
      return new SimpleDateFormat("yyyyMMddHHmmss").parse(d);
    }
  }

  @Override
  public void alreadyVisited(String pid) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public Object startPackage(PackageContext context) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void finishPackage(PackageContext context) {
    // TODO Auto-generated method stub
    
  }

}
