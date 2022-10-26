package org.hl7.fhir.igtools.publisher;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.R4ToR4BAnalyser.ResPointer;
import org.hl7.fhir.r4b.model.Bundle;
import org.hl7.fhir.r4b.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4b.model.Enumerations.FHIRVersion;
import org.hl7.fhir.r4b.model.OperationDefinition;
import org.hl7.fhir.r4b.model.OperationDefinition.OperationDefinitionParameterComponent;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.utils.NPMPackageGenerator;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.JsonTrackingParser;
import org.hl7.fhir.utilities.json.JsonUtilities;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.NpmPackage.NpmPackageFolder;
import org.hl7.fhir.utilities.npm.PackageHacker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class R4ToR4BAnalyser {
  
  public class ResPointer {
    private CanonicalResource resource;
    private Element element;
        
    public ResPointer(CanonicalResource resource) {
      super();
      this.resource = resource;
    }
    public ResPointer(Element element) {
      super();
      this.element = element;
    }
    public CanonicalResource getResource() {
      return resource;
    }
    public Element getElement() {
      return element;
    }
    public String getPath() {
      return resource != null ? resource.getUserString("path") : element.getUserString("path");
    }
    public String present() {
      return resource != null ? resource.present() : element.fhirType()+"/"+element.getIdBase();
    }
  }

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
  private boolean checking;
  private boolean r4OK;
  private boolean r4BOK;
  private List<String> r4Problems = new ArrayList<>();
  private List<String> r4BProblems = new ArrayList<>();
  private Map<String, ResPointer> r4Exemptions = new HashMap<>();
  private Map<String, ResPointer> r4BExemptions = new HashMap<>();
  
  public R4ToR4BAnalyser() {
    super();
  }

  public void setContext(IWorkerContext context) {
    this.context = context;    
    if (context != null && (VersionUtilities.isR4Ver(context.getVersion()) || VersionUtilities.isR4BVer(context.getVersion()))) {
      r4OK = true;
      r4BOK = true;
      checking = true;
    } else {
      r4OK = false;
      r4BOK = false;
      checking = false;
    }
  }
  
  public void checkProfile(StructureDefinition sd) {
    if (isExempt(sd)) {
      return;
    }
    if (sd.getKind() == StructureDefinitionKind.LOGICAL || sd.getDerivation() == TypeDerivationRule.SPECIALIZATION) {
      return;
    }
    if (!checking) {
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
  
  public void checkExample(Element e) {
    if (isExempt(e)) {
      return;
    }
    checkTypeReference(e, "has type", e.fhirType());
    for (Element c : e.getChildren()) {
      checkExample(e, c);
    }
  }
  
  public void checkExample(Element src, Element e) {
    checkTypeReference(src, "has type", e.fhirType());
    for (Element c : e.getChildren()) {
      checkExample(src, c);
    }
  }
  
  public void markExempt(String s, boolean R4) {
    if (R4) {
      r4Exemptions.put(s, null);
    } else {
      r4BExemptions.put(s, null);      
    }
  }
  
  private boolean isExempt(StructureDefinition sd) {
    boolean res = false;
    for (String s : r4Exemptions.keySet()) {
      if (s.equals(sd.fhirType()+"/"+sd.getId()) || s.equals(sd.getUrl())) {
        r4Exemptions.put(s, new ResPointer(sd));
        res = true;
      }
    }
    for (String s : r4BExemptions.keySet()) {
      if (s.equals(sd.fhirType()+"/"+sd.getId()) || s.equals(sd.getUrl())) {
        r4BExemptions.put(s, new ResPointer(sd));
        res = true;
      }
    }
    return res;
  }

  private boolean isExempt(Element e) {
    boolean res = false;
    for (String s : r4Exemptions.keySet()) {
      if (s.equals(e.fhirType()+"/"+e.getIdBase())) {
        r4Exemptions.put(s, new ResPointer(e));
        res = true;
      }
    }
    for (String s : r4BExemptions.keySet()) {
      if (s.equals(e.fhirType()+"/"+e.getIdBase())) {
        r4BExemptions.put(s, new ResPointer(e));
        res = true;
      }
    }
    return res;
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
    if (Utilities.existsInList(type, R4OnlyTypes) || (VersionUtilities.isR4Ver(context.getVersion()) && Utilities.existsInList(type, R4BChangedTypes))) {
      r4BOK = false;
      addToList(r4BProblems, msg);
    }    
  }

  private void checkTypeReference(Element src, String use, String type) {
    String msg = "<a href=\""+src.getUserString("path")+"\">"+Utilities.escapeXml(src.fhirType()+"/"+src.getIdBase())+"</a> "+use+" "+type;
    if (Utilities.existsInList(type, R4BOnlyTypes) || (VersionUtilities.isR4BVer(context.getVersion()) && Utilities.existsInList(type, R4BChangedTypes))) {
      r4OK = false;
      addToList(r4Problems, msg);
    }
    if (Utilities.existsInList(type, R4OnlyTypes) || (VersionUtilities.isR4Ver(context.getVersion()) && Utilities.existsInList(type, R4BChangedTypes))) {
      r4BOK = false;
      addToList(r4BProblems, msg);
    }    
  }

  private void addToList(List<String> list, String msg) {
    if (!list.contains(msg)) {
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
      return gen(pid, "R4", "R4B", r4BOK, r4Problems, r4BProblems, r4Exemptions, r4BExemptions);
    } else if (VersionUtilities.isR4BVer(context.getVersion())) {
      return gen(pid, "R4B", "R4", r4OK, r4BProblems, r4Problems, r4BExemptions, r4Exemptions);
    } else {
      return "";
    }
  }

  private String gen(String pid, String src, String dst, boolean dstOk, List<String> srcProblems, List<String> dstProblems, Map<String, ResPointer> srcExempt, Map<String, ResPointer> dstExempt) {
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
    if (dstExempt.size() > 0) {
      b.append("<p>The following resources are not in the "+dst+" version: </p>\r\n");
      renderExempList(dstExempt, b);            
    }
    if (srcExempt.size() > 0) {
      b.append("<p>The following resources are only in the "+dst+" version: </p>\r\n");
      renderExempList(srcExempt, b);            
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

  private void renderExempList(Map<String, ResPointer> list, StringBuilder b) {
    b.append("<ul>\r\n");
    for (String s : Utilities.sorted(list.keySet())) {
      b.append("<li>");
      ResPointer res = list.get(s);
      if (res == null) {
        b.append("<code>");
        b.append(s);
        b.append("</code>\r\n");          
      } else {
        b.append("<a href=\"");
        b.append(res.getPath());
        b.append("\">");          
        b.append(Utilities.escapeXml(res.present()));
        b.append("</a>\r\n");          
      }
      b.append("</li>\r\n");
    }
    b.append("</ul>\r\n");
  }

  public void clonePackage(String pid, String filename) throws IOException {
    if (VersionUtilities.isR4Ver(context.getVersion())) {
      genSameVersionPackage(pid, filename, Utilities.changeFileExt(filename, ".r4.tgz"), true, "4.0.0", "r4");
      genOtherVersionPackage(pid, filename, Utilities.changeFileExt(filename, ".r4b.tgz"), "hl7.fhir.r4b.core", "4.3.0", "r4b", "4.0.0");
    } else if (VersionUtilities.isR4BVer(context.getVersion())) {
      genSameVersionPackage(pid, filename, Utilities.changeFileExt(filename, ".r4b.tgz"), false, "4.3.0", "r4b");
      genOtherVersionPackage(pid, filename, Utilities.changeFileExt(filename, ".r4.tgz"), "hl7.fhir.r4.core", "4.0.0", "r4", "4.3.0");
    } else {
      throw new Error("Should not happen");
    }
  }
  

  private void genSameVersionPackage(String pid, String source, String dest, boolean r4, String ver, String pver) throws FHIRException, IOException {
    NpmPackage src = NpmPackage.fromPackage(new FileInputStream(source));
    JsonObject npm = src.getNpm();
    npm.remove("name");
    npm.addProperty("name", pid+"."+pver);

    NPMPackageGenerator gen = new NPMPackageGenerator(dest, npm, src.dateAsDate(), src.isNotForPublication());
    
    for (Entry<String, NpmPackageFolder> f : src.getFolders().entrySet()) {
      for (String s : f.getValue().listFiles()) {
        processFileSame(gen, f.getKey(), s, f.getValue().fetchFile(s), r4 ? r4Exemptions : r4BExemptions, ver, pver);          
      }
    }
    gen.finish();
  }
  
  // we use R4B here, whether it's r4 or r4b - if the content is in the differences, we won't get to the this point
  private void processFileSame(NPMPackageGenerator gen, String folder, String filename, byte[] content, Map<String, ResPointer> exemptions, String ver, String pver) throws IOException {
    if (Utilities.existsInList(folder, "package", "example")) {
      if (!Utilities.existsInList(filename, "package.json", ".index.json")) {
        org.hl7.fhir.r4b.model.Resource res = new org.hl7.fhir.r4b.formats.JsonParser().parse(content);
        boolean exempt = (exemptions.containsKey(res.fhirType()+"/"+res.getIdBase()) ||
            ((res instanceof org.hl7.fhir.r4b.model.CanonicalResource) && exemptions.containsKey(((org.hl7.fhir.r4b.model.CanonicalResource) res).getUrl())));
        if (!exempt) {
//          System.out.println("** Add "+res.fhirType()+"/"+res.getId()+" to same version");
          gen.addFile(folder, filename, content);          
        } else {
//          System.out.println("** Exclude "+res.fhirType()+"/"+res.getId()+" from same version");
        }
      }
    } else if (filename.equals("ig-r4.json")) {
      gen.addFile(folder, filename, updateIGR4(content, ver, pver));
    } else if (filename.equals("spec.internals")) {
      gen.addFile(folder, filename, updateSpecInternals(content, ver, pver));
    } else {
      gen.addFile(folder, filename, content);
    }
  }


  private byte[] updateIGR4(byte[] content, String ver, String pver) throws IOException {
    JsonObject json = JsonTrackingParser.parseJson(content);
    JsonUtilities.setProperty(json, "packageId", JsonUtilities.str(json, "packageId")+"."+pver);
    json.remove("fhirVersion");
    JsonArray fvl = new JsonArray(); 
    json.add("fhirVersion", fvl);
    fvl.add(ver);
    return JsonTrackingParser.writeBytes(json, false);
  }

  private byte[] updateSpecInternals(byte[] content, String ver, String pver) throws IOException {
    JsonObject json = JsonTrackingParser.parseJson(content);
    JsonUtilities.setProperty(json, "npm-name", JsonUtilities.str(json, "npm-name")+"."+pver);
    if (!ver.equals(JsonUtilities.str(json, "ig-version"))) {
      JsonUtilities.setProperty(json, "ig-version", ver);      
    }
    return JsonTrackingParser.writeBytes(json, true);
  }

  private void genOtherVersionPackage(String pid, String source, String dest, String core, String ver, String pver, String nver) throws FHIRException, IOException {
    NpmPackage src = NpmPackage.fromPackage(new FileInputStream(source));
    JsonObject npm = src.getNpm();
    npm.remove("name");
    npm.addProperty("name", pid+"."+pver);
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
       processFileOther(gen, f.getKey(), s, f.getValue().fetchFile(s), ver, pver, nver, VersionUtilities.isR4Ver(ver) ? r4BExemptions : r4Exemptions);          
      }
    }
    gen.finish();
  }

  // we use R4B here, whether it's r4 or r4b - if the content is in the differences, we won't get to the this point
  private void processFileOther(NPMPackageGenerator gen, String folder, String filename, byte[] content, String ver, String pver, String nver, Map<String, ResPointer> exemptions) throws IOException {
    if (Utilities.existsInList(folder, "package", "example")) {
      if (!Utilities.existsInList(filename, "package.json", ".index.json")) {
        org.hl7.fhir.r4b.model.Resource res = new org.hl7.fhir.r4b.formats.JsonParser().parse(content);
        boolean exempt = (exemptions.containsKey(res.fhirType()+"/"+res.getId()) ||
            ((res instanceof org.hl7.fhir.r4b.model.CanonicalResource) && exemptions.containsKey(((org.hl7.fhir.r4b.model.CanonicalResource) res).getUrl())));
        if (!exempt) {
//          System.out.println("** Add "+res.fhirType()+"/"+res.getId()+" to other version");
          if (reVersion(res, ver, pver, nver)) {
            gen.addFile(folder, filename, new org.hl7.fhir.r4b.formats.JsonParser().composeBytes(res));            
          } else {
            gen.addFile(folder, filename, content);
          }
        } else {
//          System.out.println("** Exclude "+res.fhirType()+"/"+res.getId()+" from other version");
        }
      }
    } else if (filename.equals("spec.internals")) {
      gen.addFile(folder, filename, updateSpecInternals(content, ver, pver));
    } else if (filename.equals("ig-r4.json")) {
      gen.addFile(folder, filename, updateIGR4(content, ver, pver));
    } else {
      gen.addFile(folder, filename, content);
    }
  }

  private boolean reVersion(org.hl7.fhir.r4b.model.Resource res, String ver, String pver, String nver) {
    if (res instanceof org.hl7.fhir.r4b.model.StructureDefinition) {
      return reVersionSD((org.hl7.fhir.r4b.model.StructureDefinition) res, ver, nver);
    } else if (res instanceof org.hl7.fhir.r4b.model.CapabilityStatement) {
      return reVersionCS((org.hl7.fhir.r4b.model.CapabilityStatement) res, ver);
    } else if (res instanceof org.hl7.fhir.r4b.model.OperationDefinition) {
      return reVersionOD((org.hl7.fhir.r4b.model.OperationDefinition) res, ver, nver);
    } else if (res instanceof org.hl7.fhir.r4b.model.ImplementationGuide) {
      return reVersionIG((org.hl7.fhir.r4b.model.ImplementationGuide) res, ver, pver);
    } else if (res instanceof org.hl7.fhir.r4b.model.Bundle) {
      return reVersionBundle((org.hl7.fhir.r4b.model.Bundle) res, ver, pver, nver);
    } else {
      return false;
    }
  }

  private boolean reVersionOD(OperationDefinition od, String ver, String nver) {
    boolean res = false;    
    for (OperationDefinitionParameterComponent p : od.getParameter()) {
      if (p.hasBinding() && p.getBinding().hasValueSet() && p.getBinding().getValueSet().endsWith("|"+nver) && p.getBinding().getValueSet().startsWith("http://hl7.org/fhir/ValueSet")) {
        p.getBinding().setValueSet(p.getBinding().getValueSet().replace("|"+nver, "|"+ver));
        res = true;
      }
    }
    return res;
  }

  private boolean reVersionBundle(Bundle bnd, String ver, String pver, String nver) {
    boolean res = false;
    for (BundleEntryComponent be : bnd.getEntry()) {
      if (be.hasResource()) {
        res = reVersion(be.getResource(), ver, pver, nver) || res;
      }
    }
    return res;
  }

  private boolean reVersionSD(org.hl7.fhir.r4b.model.StructureDefinition sd, String ver, String nver) {
    sd.setFhirVersion(org.hl7.fhir.r4b.model.Enumerations.FHIRVersion.fromCode(ver));
    for (org.hl7.fhir.r4b.model.ElementDefinition ed : sd.getDifferential().getElement()) {
      reVersionED(ed, ver, nver);
    }
    for (org.hl7.fhir.r4b.model.ElementDefinition ed : sd.getSnapshot().getElement()) {
      reVersionED(ed, ver, nver);
    }
    return true;
  }

  private boolean reVersionED(org.hl7.fhir.r4b.model.ElementDefinition ed, String ver, String nver) {
    if (ed.hasBinding() && ed.getBinding().hasValueSet() && ed.getBinding().getValueSet().endsWith("|"+nver) && ed.getBinding().getValueSet().startsWith("http://hl7.org/fhir/ValueSet")) {
      ed.getBinding().setValueSet(ed.getBinding().getValueSet().replace("|"+nver, "|"+ver));
      return true;
    } else {
      return false;
    }
  }

  private boolean reVersionCS(org.hl7.fhir.r4b.model.CapabilityStatement cs, String ver) {
    cs.setFhirVersion(org.hl7.fhir.r4b.model.Enumerations.FHIRVersion.fromCode(ver));
    return true;
  }

  private boolean reVersionIG(org.hl7.fhir.r4b.model.ImplementationGuide ig, String ver, String pver) {
    ig.setId(ig.getId()+"."+pver);
    ig.setPackageId(ig.getPackageId()+"."+pver);
    ig.getFhirVersion().clear();
    ig.addFhirVersion(FHIRVersion.fromCode(ver));
    return true;
  }
  
}
