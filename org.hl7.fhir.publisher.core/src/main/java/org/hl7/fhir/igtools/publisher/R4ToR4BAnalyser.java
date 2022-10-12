package org.hl7.fhir.igtools.publisher;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4b.formats.JsonParser;
import org.hl7.fhir.r4b.model.CapabilityStatement;
import org.hl7.fhir.r4b.model.Enumerations.FHIRVersion;
import org.hl7.fhir.r4b.model.ImplementationGuide;
import org.hl7.fhir.r4b.model.Resource;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.utils.NPMPackageGenerator;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.NpmPackage.NpmPackageFolder;
import org.hl7.fhir.utilities.npm.PackageHacker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class R4ToR4BAnalyser {
  
  private static final List<String> R4BOnlyTypes = Collections.unmodifiableList(
      Arrays.asList(new String[] {"CodeableReference", "RatioRange", "NutritionProduct", 
          "AdministrableProductDefinition", "ClinicalUseDefinition", "PackagedProductDefinition", "ManufacturedItemDefinition", "RegulatedAuthorization",
          "MedicinalProductDefinition", "Ingredient", "SubstanceDefinition", "Citation", "EvidenceReport", "SubscriptionStatus", "SubscriptionTopic"}));
  
  private static final List<String> R4OnlyTypes = Collections.unmodifiableList(
      Arrays.asList(new String[] {"MedicinalProduct", "MedicinalProductIngredient", "SubstanceSpecification", "MedicinalProductAuthorization", 
          "MedicinalProductContraindication", "MedicinalProductIndication", "MedicinalProductInteraction", "MedicinalproductManufactured",
          "MedicinalproductPackaged", "MedicinalproductPharmaceutical", "MedicinalproductUndesirableEffect", "SubstanceAmount", "SubstanceNucleicAcid", 
          "SubstancePolymer", "SubstanceProtein", "SubstanceReferenceInformation", "SubstanceSourceMaterial", "EffectEvidenceSynthesis", "RiskEvidenceSynthesis"}));
  
  private static final List<String> R4BChangedTypes = Collections.unmodifiableList(
      Arrays.asList(new String[] {"MarketingStatus", "ProductShelfLife", "Evidence", "EvidenceVariable"}));
  

//    Add canonical as an allowed type for  to ActivityDefinition and PlanDefinition
    
  private IWorkerContext context;

  private boolean r4OK;
  private boolean r4BOK;
  private List<String> r4Problems = new ArrayList<>();
  private List<String> r4BProblems = new ArrayList<>();
  
  public R4ToR4BAnalyser(IWorkerContext context) {
    super();
    this.context = context;
    if (VersionUtilities.isR4Ver(context.getVersion()) || VersionUtilities.isR4BVer(context.getVersion())) {
      r4OK = true;
      r4BOK = true;
    } else {
      r4OK = false;
      r4BOK = false;
    }
  }

  public void checkProfile(StructureDefinition sd) {
    if (sd.getKind() == StructureDefinitionKind.LOGICAL || sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
      return;
    }
    checkTypeDerivation(sd, "derives from", sd.getBaseDefinition());
    for (ElementDefinition ed : sd.getDifferential().getElement()) {
      checkPathUsage(sd, ed);
      for (TypeRefComponent tr : ed.getType()) {
        checkTypeUsage(sd, tr);
      }
    }
  }
  
  private void checkPathUsage(StructureDefinition src, ElementDefinition ed) {
    if (Utilities.existsInList(ed.getPath(), "ActivityDefinition.subject[x]", "PlanDefinitionsubject[x]")) {
      for (TypeRefComponent tr : ed.getType()) {
        if ("canonical".equals(tr.getCode())) {
          String msg = "<a href=\""+src.getUserString("path")+"\">"+Utilities.escapeXml(src.present())+"</a> refers to the canonical type at "+ed.getPath();
          r4OK = false;
          addToList(r4Problems, msg);
        }
      }
    } 
  }

  private void checkTypeUsage(StructureDefinition src, TypeRefComponent tr) {
    checkTypeReference(src, "derives from", tr.getCode());
    for (CanonicalType t : tr.getTargetProfile()) {
      checkTypeDerivation(src, "has target", t.getValue());
    }
  }

  private void checkTypeDerivation(StructureDefinition src, String usage, String ref) {
    StructureDefinition sd = context.fetchResource(StructureDefinition.class, ref);
    while (sd != null && sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
      sd = context.fetchResource(StructureDefinition.class, sd.getBaseDefinition());      
    }
    if (sd != null) {
      String type = sd.getType();
      checkTypeReference(src, usage, type);
    }
  }

  private void checkTypeReference(StructureDefinition src, String use, String type) {
    String msg = "<a href=\""+src.getUserString("path")+"\">"+Utilities.escapeXml(src.present())+"</a> "+use+" "+type;
    if (Utilities.existsInList(type, R4BOnlyTypes) || (VersionUtilities.isR4BVer(context.getVersion()) && Utilities.existsInList(type, R4BChangedTypes))) {
      r4OK = false;
      addToList(r4Problems, msg);
    }
    if (Utilities.existsInList(type, R4BOnlyTypes) || (VersionUtilities.isR4Ver(context.getVersion()) && Utilities.existsInList(type, R4BChangedTypes))) {
      r4BOK = false;
      addToList(r4BProblems, msg);
    }    
  }

  private void addToList(List<String> list, String msg) {
    if (list.contains(msg)) {
      list.add(msg);
    }    
  }

  public boolean canBeR4() {
    return r4OK;
  }
  
  public boolean canBeR4B() {
    return r4BOK;
  }

  public String generate(String pid) {
    if (VersionUtilities.isR4Ver(context.getVersion())) {
      return gen(pid, "R4", "R4B", r4BOK, r4Problems, r4BProblems);
    } else if (VersionUtilities.isR4BVer(context.getVersion())) {
      return gen(pid, "R4B", "R4", r4OK, r4BProblems, r4Problems);
    } else {
      return "";
    }
  }

  private String gen(String pid, String src, String dst, boolean dstOk, List<String> srcProblems, List<String> dstProblems) {
    StringBuilder b = new StringBuilder();
    if (dstOk) {
      b.append("<p>This is an "+src+" IG. None of the features it uses are changed "+(src.equals("r4") ? "in" : "from")+" "+dst+", so it can be used as is with "+dst+" systems. ");
      b.append("Packages for both <a href=\"package.r4.tgz\">R4 ("+pid+".r4)</a> and <a href=\"package.r4b.tgz\">R4B ("+pid+".r4b)</a> are available.</p>\r\n");
    } else {
      b.append("<p>This is an "+src+" IG that is not compatible with "+dst+" because: </p>\r\n");
      b.append("<ul>\r\n");
      for (String s : dstProblems) {
        b.append("<li>");
        b.append(s);
        b.append("</li>\r\n");
      }
      b.append("</ul>\r\n");      
    }
    if (srcProblems.size() > 0) {
      b.append("<p>While checking for "+dst+" compatibility, the following "+src+" problems were found: </p>\r\n");
      b.append("<ul>\r\n");
      for (String s : srcProblems) {
        b.append("<li>");
        b.append(s);
        b.append("</li>\r\n");
      }
      b.append("</ul>\r\n");            
    }
    return b.toString();
  }

  public void clonePackage(String pid, String filename) throws IOException {
    if (VersionUtilities.isR4Ver(context.getVersion())) {
      genSameVersionPackage(pid, filename, Utilities.changeFileExt(filename, ".r4.tgz"));
      genOtherVersionPackage(pid, filename, Utilities.changeFileExt(filename, ".r4b.tgz"), "hl7.fhir.r4b.core", "4.3.0");
    } else if (VersionUtilities.isR4Ver(context.getVersion())) {
      Utilities.copyFile(filename, Utilities.changeFileExt(filename, ".r4b.tgz"));
    } else {
      throw new Error("Should not happen");
    }
  }
  

  private void genSameVersionPackage(String pid, String source, String dest) throws FHIRException, IOException {
    NpmPackage src = NpmPackage.fromPackage(new FileInputStream(source));
    JsonObject npm = src.getNpm();
    npm.remove("name");
    npm.addProperty("name", pid+".r4b");

    NPMPackageGenerator gen = new NPMPackageGenerator(dest, npm, src.dateAsDate(), src.isNotForPublication());
    
    for (Entry<String, NpmPackageFolder> f : src.getFolders().entrySet()) {
      for (String s : f.getValue().listFiles()) {
        processFile(gen, f.getKey(), s, f.getValue().fetchFile(s), null);
      }
    }
    gen.finish();

  }

  private void genOtherVersionPackage(String pid, String source, String dest, String core, String ver) throws FHIRException, IOException {
    NpmPackage src = NpmPackage.fromPackage(new FileInputStream(source));
    JsonObject npm = src.getNpm();
    npm.remove("name");
    npm.addProperty("name", pid+".r4b");
    npm.remove("fhirVersions");
    JsonArray fvl = new JsonArray(); 
    npm.add("fhirVersions", fvl);
    fvl.add(ver);
    JsonObject dep = npm.getAsJsonObject("dependencies");
    dep.remove("hl7.fhir.r4.core");
    dep.remove("hl7.fhir.r4b.core");
    dep.addProperty(core, ver);

    NPMPackageGenerator gen = new NPMPackageGenerator(dest, npm, src.dateAsDate(), src.isNotForPublication());
    
    for (Entry<String, NpmPackageFolder> f : src.getFolders().entrySet()) {
      for (String s : f.getValue().listFiles()) {
        processFile(gen, f.getKey(), s, f.getValue().fetchFile(s), ver);
      }
    }
    gen.finish();

  }

  private void processFile(NPMPackageGenerator gen, String folder, String filename, byte[] content, String ver) throws IOException {
    if ("package".equals(folder) && ver != null) {
      if (!Utilities.existsInList(filename, "package.json", ".index.json")) {
        Resource res = new JsonParser().parse(content);
        if (reVersion(res, ver)) {
          gen.addFile(folder, filename, new JsonParser().composeBytes(res));
        } else {
          gen.addFile(folder, filename, content);
        }
      }
    } else {
      gen.addFile(folder, filename, content);
    }
  }

  private boolean reVersion(Resource res, String ver) {
    if (res instanceof org.hl7.fhir.r4b.model.StructureDefinition) {
      return reVersionSD((org.hl7.fhir.r4b.model.StructureDefinition) res, ver);
    } else if (res instanceof org.hl7.fhir.r4b.model.CapabilityStatement) {
      return reVersionCS((org.hl7.fhir.r4b.model.CapabilityStatement) res, ver);
    } else if (res instanceof org.hl7.fhir.r4b.model.ImplementationGuide) {
      return reVersionIG((org.hl7.fhir.r4b.model.ImplementationGuide) res, ver);
    } else {
      return false;
    }
  }

  private boolean reVersionSD(org.hl7.fhir.r4b.model.StructureDefinition sd, String ver) {
    sd.setFhirVersion(FHIRVersion.fromCode(ver));
    return true;
  }

  private boolean reVersionCS(CapabilityStatement cs, String ver) {
    cs.setFhirVersion(FHIRVersion.fromCode(ver));
    return true;
  }

  private boolean reVersionIG(ImplementationGuide ig, String ver) {
    ig.setVersion(ver);
    return true;
  }
  
}
