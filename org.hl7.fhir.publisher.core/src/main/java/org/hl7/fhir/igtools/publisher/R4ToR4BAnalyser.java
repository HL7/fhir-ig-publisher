package org.hl7.fhir.igtools.publisher;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_43_50;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.loaders.PublisherLoader;
import org.hl7.fhir.r4b.model.*;
import org.hl7.fhir.r4b.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4b.model.Enumerations.FHIRVersion;
import org.hl7.fhir.r4b.model.OperationDefinition.OperationDefinitionParameterComponent;
import org.hl7.fhir.r4b.utils.DataTypeVisitor;
import org.hl7.fhir.r4b.utils.DataTypeVisitor.IDatatypeVisitor;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.context.ContextUtilities;
import org.hl7.fhir.r5.context.IContextResourceLoader;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.utils.NPMPackageGenerator;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.i18n.RenderingI18nContext;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.NpmPackage.NpmPackageFolder;
import org.hl7.fhir.utilities.npm.NpmPackage.PackageResourceInformation;
import org.hl7.fhir.utilities.npm.PackageHacker;
import org.hl7.fhir.utilities.validation.ValidationMessage;

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
      return resource != null ? resource.getWebPath() : element.getWebPath();
    }
    public String present() {
      return resource != null ? resource.present(rc.getLocale().toLanguageTag()) : element.fhirType()+"/"+element.getIdBase();
    }
  }

  private static final List<String> R4BOnlyTypes = Collections.unmodifiableList(
      Arrays.asList(new String[] {"CodeableReference", "RatioRange", "NutritionProduct", 
          "AdministrableProductDefinition", "ClinicalUseDefinition", "PackagedProductDefinition", "ManufacturedItemDefinition", "RegulatedAuthorization",
          "MedicinalProductDefinition", "Ingredient", "SubstanceDefinition", "Citation", "EvidenceReport", "SubscriptionStatus"/*, "SubscriptionTopic"*/}));
  
  private static final List<String> R4OnlyTypes = Collections.unmodifiableList(
      Arrays.asList(new String[] {"MedicinalProduct", "MedicinalProductIngredient", "SubstanceSpecification", "MedicinalProductAuthorization", 
          "MedicinalProductContraindication", "MedicinalProductIndication", "MedicinalProductInteraction", "MedicinalproductManufactured",
          "MedicinalproductPackaged", "MedicinalproductPharmaceutical", "MedicinalproductUndesirableEffect", "SubstanceAmount", "SubstanceNucleicAcid", 
          "SubstancePolymer", "SubstanceProtein", "SubstanceReferenceInformation", "SubstanceSourceMaterial", "EffectEvidenceSynthesis", "RiskEvidenceSynthesis"}));
  
  private static final List<String> R4BChangedTypes = Collections.unmodifiableList(
      Arrays.asList(new String[] {"MarketingStatus", "ProductShelfLife", "Evidence", "EvidenceVariable"}));
  

//    Add canonical as an allowed type for  to ActivityDefinition and PlanDefinition
    
  private IWorkerContext context;
  private RenderingContext rc;
  private boolean checking;
  private boolean r4OK;
  private boolean r4BOK;
  private List<String> r4Problems = new ArrayList<>();
  private List<String> r4BProblems = new ArrayList<>();
  private Map<String, ResPointer> r4Exemptions = new HashMap<>();
  private Map<String, ResPointer> r4BExemptions = new HashMap<>();

  private boolean newFormat;
  
  public R4ToR4BAnalyser(RenderingContext rc, boolean newFormat) {
    super();
    this.rc = rc;
    this.newFormat = newFormat;
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
    checkTypeDerivation(sd, rc.formatPhrase(RenderingI18nContext.R44B_DERIVES_FROM), sd.getBaseDefinition());
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
    checkTypeReference(e, rc.formatPhrase(RenderingI18nContext.R44B_HAS_TYPE), e.fhirType());
    for (Element c : e.getChildren()) {
      checkExample(e, c);
    }
  }
  
  public void checkExample(Element src, Element e) {
    checkTypeReference(src, rc.formatPhrase(RenderingI18nContext.R44B_HAS_TYPE), e.fhirType());
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
          String msg = rc.formatPhrase(RenderingI18nContext.R44B_REFERS_TO, src.getWebPath(), Utilities.escapeXml(src.present(rc.getLocale().toLanguageTag())), ed.getPath());
          r4OK = false;
          addToList(r4Problems, msg);
        }
      }
    } 
  }

  private void checkTypeUsage(StructureDefinition src, TypeRefComponent tr) {
    checkTypeReference(src, rc.formatPhrase(RenderingI18nContext.R44B_DERIVES_FROM), tr.getCode());
    for (CanonicalType t : tr.getTargetProfile()) {
      checkTypeDerivation(src, rc.formatPhrase(RenderingI18nContext.R44B_HAS_TARGET), t.getValue());
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
    String msg = "<a href=\""+src.getWebPath()+"\">"+Utilities.escapeXml(src.present(rc.getLocale().toLanguageTag()))+"</a> "+use+" "+type;
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
    String msg = "<a href=\""+src.getWebPath()+"\">"+Utilities.escapeXml(src.fhirType()+"/"+src.getIdBase())+"</a> "+use+" "+type;
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

  public String generate(String pid, boolean inline) {
    if (VersionUtilities.isR4Ver(context.getVersion())) {
      return gen(pid, "R4", "R4B", r4OK, r4BOK, r4Problems, r4BProblems, r4Exemptions, r4BExemptions, inline);
    } else if (VersionUtilities.isR4BVer(context.getVersion())) {
      return gen(pid, "R4B", "R4", r4BOK, r4OK, r4BProblems, r4Problems, r4BExemptions, r4Exemptions, inline);
    } else {
      return "";
    }
  }

  private String gen(String pid, String src, String dst, boolean srcOk, boolean dstOk, List<String> srcProblems, List<String> dstProblems, Map<String, ResPointer> srcExempt, Map<String, ResPointer> dstExempt, boolean inline) {
    StringBuilder b = new StringBuilder();
    if (!srcOk) {
      if (!inline) {
        b.append("<p>");
      }
      b.append(rc.formatPhrase(RenderingI18nContext.R44B_WRONG_MSG)+": \r\n");
      if (!inline) {
        b.append("</p>\r\n");
        b.append("<ul>\r\n");
      }
      boolean first = true;
      for (String s : srcProblems) {
        if (!inline) {
          b.append("<li>");
        }
        if (first) first = false; else if (inline) b.append(", ");
        b.append(s);
        if (!inline) {
          b.append("</li>\r\n");
        }
      }
      if (!inline) {
        b.append("</ul>\r\n");
      }
    } else if (dstOk) {
      if (!inline) {
        b.append("<p>");
      }
      b.append(rc.formatPhrase(RenderingI18nContext.R44B_USE_OK, src, dst)+" ");
      String ref = newFormat ? "../package" : "package";
      b.append(rc.formatPhrase(RenderingI18nContext.R44B_PACKAGE_REF, ref+".r4.tgz", pid, ref+".r4b.tgz", pid));
      if (!inline) {
        b.append("</p>\r\n");
      }
      
    } else {
      if (!inline) {
        b.append("<p>");
      }
      b.append(rc.formatPhrase(RenderingI18nContext.R44B_NOT_COMP, src, dst)+": \r\n");
      if (!inline) {
        b.append("</p>\r\n");
        b.append("<ul>\r\n");
      }
      boolean first = true;
      for (String s : dstProblems) {
        if (!inline) {
          b.append("<li>");
        }
        if (first) first = false; else if (inline) b.append(", ");
        b.append(s);
        if (!inline) {
          b.append("</li>\r\n");
        }
      }
      if (!inline) {
        b.append("</ul>\r\n");
      }
    }
    if (!inline) {
      if (dstExempt.size() > 0) {
        b.append("<p>"+rc.formatPhrase(RenderingI18nContext.R44B_NOT_IN, dst)+": </p>\r\n");
        renderExempList(dstExempt, b);            
      }
      if (srcExempt.size() > 0) {
        b.append("<p>"+rc.formatPhrase(RenderingI18nContext.R44B_ONLY_IN, dst)+": </p>\r\n");
        renderExempList(srcExempt, b);            
      }
      if (srcProblems.size() > 0) {
        b.append("<p>"+rc.formatPhrase(RenderingI18nContext.R44B_PROBLEMS, dst, src)+": </p>\r\n");
        b.append("<ul>\r\n");
        for (String s : srcProblems) {
          b.append("<li>");
          b.append(s);
          b.append("</li>\r\n");
        }
        b.append("</ul>\r\n");            
      }
    }
    return b.toString();
  }
  
  public void log(String pid) {
    if (VersionUtilities.isR4Ver(context.getVersion())) {
      log(pid, "R4", "R4B", r4BOK, r4Problems, r4BProblems, r4Exemptions, r4BExemptions);
    } else if (VersionUtilities.isR4BVer(context.getVersion())) {
      log(pid, "R4B", "R4", r4OK, r4BProblems, r4Problems, r4BExemptions, r4Exemptions);
    } else {
      System.out.println("??");
    }
  }

  private void log(String pid, String src, String dst, boolean dstOk, List<String> srcProblems, List<String> dstProblems, Map<String, ResPointer> srcExempt, Map<String, ResPointer> dstExempt) {
    if (dstOk) {
      System.out.println("This is an "+src+" IG. None of the features it uses are changed "+(src.equals("r4") ? "in" : "from")+" "+dst+", so it can be used as is with "+dst+" systems.");
      System.out.println("Packages for both R4 ("+pid+".r4) and R4B ("+pid+".r4b) are available.");
    } else {
      System.out.println("This is an "+src+" IG that is not compatible with "+dst+" because:");
      for (String s : dstProblems) {
        System.out.println("* "+s);
      }
    }
    if (dstExempt.size() > 0) {
      System.out.println("The following resources are not in the "+dst+" version:");
      for (String s : Utilities.sorted(dstExempt.keySet())) {
        System.out.println("* "+s);
      }
    }
    if (srcExempt.size() > 0) {
      System.out.println("The following resources are only in the "+dst+" version:");
      for (String s : Utilities.sorted(srcExempt.keySet())) {
        System.out.println("* "+s);
      }
    }
    if (srcProblems.size() > 0) {
      System.out.println("<p>While checking for "+dst+" compatibility, the following "+src+" problems were found:");
      for (String s : srcProblems) {
        System.out.println("* "+s);
      }
    }

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
      genSameVersionPackage(pid, filename, FileUtilities.changeFileExt(filename, ".r4.tgz"), true, "4.0.1", "r4");
      genOtherVersionPackage(pid, filename, FileUtilities.changeFileExt(filename, ".r4b.tgz"), "hl7.fhir.r4b.core", "4.3.0", "r4b", "4.0.1", VersionUtilities.getSpecUrl("4.0"), VersionUtilities.getSpecUrl("4.3"));
    } else if (VersionUtilities.isR4BVer(context.getVersion())) {
      genSameVersionPackage(pid, filename, FileUtilities.changeFileExt(filename, ".r4b.tgz"), false, "4.3.0", "r4b");
      genOtherVersionPackage(pid, filename, FileUtilities.changeFileExt(filename, ".r4.tgz"), "hl7.fhir.r4.core", "4.0.1", "r4", "4.3.0", VersionUtilities.getSpecUrl("4.3"), VersionUtilities.getSpecUrl("4.0"));
    } else {
      throw new Error("Should not happen");
    }
  }
  

  private void genSameVersionPackage(String pid, String source, String dest, boolean r4, String ver, String pver) throws FHIRException, IOException {
    NpmPackage src = NpmPackage.fromPackage(new FileInputStream(source));
    JsonObject npm = src.getNpm();
    npm.remove("name");
    npm.add("name", pid+"."+pver);

    NPMPackageGenerator gen = new NPMPackageGenerator(dest, npm, src.dateAsDate(), src.isNotForPublication());
    
    for (Entry<String, NpmPackageFolder> f : src.getFolders().entrySet()) {
      for (String s : f.getValue().listFiles()) {
        processFileSame(gen, f.getKey(), s, f.getValue().fetchFile(s), r4 ? r4Exemptions : r4BExemptions, ver, pver);          
      }
    }
    gen.finish();
  }

  // we use R4B here, whether it's r4 or r4b - if the content is in the differences, we won't get to the this point
  private void processFileSame(NPMPackageGenerator gen, String folder, String filename, byte[] content, Map<String, ResPointer> exemptions, String ver, String pver) {
    try {
      if (Utilities.existsInList(folder, "package", "example")) {
        if (!Utilities.existsInList(filename, "package.json", ".index.json", ".index.db")) {
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
      } else if (filename.equals("spec.internals")) {
        gen.addFile(folder, filename, updateSpecInternals(content, ver, pver));
      } else {
        gen.addFile(folder, filename, content);
      }
    } catch (Exception e) {
      throw new FHIRException("Error processing "+folder+"/"+filename+": "+e.getMessage(), e);
    }
  }


  private byte[] updateIGR4(byte[] content, String ver, String pver) throws IOException {
    JsonObject json = JsonParser.parseObject(content);
    json.set("packageId", json.asString("packageId")+"."+pver);
    json.remove("fhirVersion");
    JsonArray fvl = new JsonArray(); 
    json.add("fhirVersion", fvl);
    fvl.add(ver);
    return JsonParser.composeBytes(json, false);
  }

  private byte[] updateSpecInternals(byte[] content, String ver, String pver) throws IOException {
    JsonObject json = JsonParser.parseObject(content);
    json.set("npm-name", json.asString("npm-name")+"."+pver);
    if (!ver.equals(json.asString("ig-version"))) {
      json.set("ig-version", ver);      
    }
    return JsonParser.composeBytes(json, true);
  }

  private void genOtherVersionPackage(String pid, String source, String dest, String core, String ver, String pver, String nver, String pathS, String pathT) throws FHIRException, IOException {
    NpmPackage src = NpmPackage.fromPackage(new FileInputStream(source));
    JsonObject npm = src.getNpm();
    npm.remove("name");
    npm.add("name", pid+"."+pver);
    npm.remove("fhirVersions");
    JsonArray fvl = new JsonArray(); 
    npm.add("fhirVersions", fvl);
    fvl.add(ver);
    JsonObject dep = npm.getJsonObject("dependencies");
    dep.remove("hl7.fhir.r4.core");
    dep.remove("hl7.fhir.r4b.core");
    dep.add(core, ver);

    NPMPackageGenerator gen = new NPMPackageGenerator(dest, npm, src.dateAsDate(), src.isNotForPublication());
    
    for (Entry<String, NpmPackageFolder> f : src.getFolders().entrySet()) {
      for (String s : f.getValue().listFiles()) {
       processFileOther(gen, f.getKey(), s, f.getValue().fetchFile(s), ver, pver, nver, VersionUtilities.isR4Ver(ver) ? r4BExemptions : r4Exemptions, pathS, pathT);          
      }
    }
    gen.finish();
  }

  // we use R4B here, whether it's r4 or r4b - if the content is in the differences, we won't get to them at this point
  private void processFileOther(NPMPackageGenerator gen, String folder, String filename, byte[] content, String ver, String pver, String nver, Map<String, ResPointer> exemptions, String pathS, String pathT) throws IOException {
    if (Utilities.existsInList(folder, "package", "example")) {
      if (!Utilities.existsInList(filename, "package.json", ".index.json", ".index.db")) {
        org.hl7.fhir.r4b.model.Resource res = new org.hl7.fhir.r4b.formats.JsonParser().parse(content);
        if (VersionUtilities.isR4Ver(context.getVersion()) && "Basic".equals(res.fhirType())) {
          org.hl7.fhir.r4.model.Resource r4 =  new org.hl7.fhir.r4.formats.JsonParser().parse(content);
          org.hl7.fhir.r5.model.Resource r5 = VersionConvertorFactory_40_50.convertResource(r4);
          res = VersionConvertorFactory_43_50.convertResource(r5);
        }
        boolean exempt = (exemptions.containsKey(res.fhirType()+"/"+res.getId()) ||
            ((res instanceof org.hl7.fhir.r4b.model.CanonicalResource) && exemptions.containsKey(((org.hl7.fhir.r4b.model.CanonicalResource) res).getUrl())));
        if (!exempt) {
//          System.out.println("** Add "+res.fhirType()+"/"+res.getId()+" to other version");
          if (reVersion(res, ver, pver, nver, pathS, pathT)) {
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

  private boolean reVersion(org.hl7.fhir.r4b.model.Resource res, String ver, String pver, String nver, String pathS, String pathT) {
    if (res instanceof org.hl7.fhir.r4b.model.StructureDefinition) {
      return reVersionSD((org.hl7.fhir.r4b.model.StructureDefinition) res, ver, nver, pathS, pathT);
    } else if (res instanceof org.hl7.fhir.r4b.model.CapabilityStatement) {
      return reVersionCS((org.hl7.fhir.r4b.model.CapabilityStatement) res, ver, pathS, pathT);
    } else if (res instanceof org.hl7.fhir.r4b.model.OperationDefinition) {
      return reVersionOD((org.hl7.fhir.r4b.model.OperationDefinition) res, ver, nver, pathS, pathT);
    } else if (res instanceof org.hl7.fhir.r4b.model.ImplementationGuide) {
      return reVersionIG((org.hl7.fhir.r4b.model.ImplementationGuide) res, ver, pver, pathS, pathT);
    } else if (res instanceof org.hl7.fhir.r4b.model.Bundle) {
      return reVersionBundle((org.hl7.fhir.r4b.model.Bundle) res, ver, pver, nver, pathS, pathT);
    } else {
      return processMarkdown(res, pathS, pathT);
    }
  }

  private class MarkdownProcessor implements IDatatypeVisitor<MarkdownType> {
    
    private String path1;
    private String path2;

    
    protected MarkdownProcessor(String path1, String path2) {
      super();
      this.path1 = path1;
      this.path2 = path2;
    }

    @Override
    public Class<MarkdownType> classT() {
      return MarkdownType.class;
    }

    @Override
    public boolean visit(String path, MarkdownType node) {
      String src = node.asStringValue();
      if (src != null && src.contains(path1)) {
        node.setValueAsString(src.replace(path1, path2));
        return true;
      } else {
        return false;
      }
    }
    
  }
  private boolean processMarkdown(Resource res, String pathS, String pathT) {
    DataTypeVisitor visitor = new DataTypeVisitor();
    visitor.visit(res, new MarkdownProcessor(pathS, pathT));
    return visitor.isAnyTrue();
  }

  private boolean reVersionOD(OperationDefinition od, String ver, String nver, String pathS, String pathT) {
    boolean res = false;    
    for (OperationDefinitionParameterComponent p : od.getParameter()) {
      if (p.hasBinding() && p.getBinding().hasValueSet() && p.getBinding().getValueSet().endsWith("|"+nver) && p.getBinding().getValueSet().startsWith("http://hl7.org/fhir/ValueSet")) {
        p.getBinding().setValueSet(p.getBinding().getValueSet().replace("|"+nver, "|"+ver));
        res = true;
      }
    }
    return processMarkdown(od, pathS, pathT) || res;
  }

  private boolean reVersionBundle(Bundle bnd, String ver, String pver, String nver, String pathS, String pathT) {
    boolean res = false;
    for (BundleEntryComponent be : bnd.getEntry()) {
      if (be.hasResource()) {
        res = reVersion(be.getResource(), ver, pver, nver, pathS, pathT) || res;
      }
    }
    return processMarkdown(bnd, pathS, pathT) || res;
  }

  private boolean reVersionSD(org.hl7.fhir.r4b.model.StructureDefinition sd, String ver, String nver, String pathS, String pathT) {
    sd.setFhirVersion(org.hl7.fhir.r4b.model.Enumerations.FHIRVersion.fromCode(ver));
    reVersionExtList(sd.getDifferential().getExtension(), nver, ver);
    reVersionExtList(sd.getSnapshot().getExtension(), nver, ver);
    reVersionExtList(sd.getExtension(), nver, ver);
    for (org.hl7.fhir.r4b.model.ElementDefinition ed : sd.getDifferential().getElement()) {
      reVersionED(ed, ver, nver);
    }
    for (org.hl7.fhir.r4b.model.ElementDefinition ed : sd.getSnapshot().getElement()) {
      reVersionED(ed, ver, nver);
    }
    return processMarkdown(sd, pathS, pathT) || true;
  }

  private boolean reVersionED(org.hl7.fhir.r4b.model.ElementDefinition ed, String ver, String nver) {
    boolean changed = false;
    changed = reVersionExtList(ed.getExtension(), nver, ver) || changed;
    for (org.hl7.fhir.r4b.model.ElementDefinition.TypeRefComponent t : ed.getType()) {
      changed = reVersionExtList(t.getExtension(), nver, ver) || changed;
      for (org.hl7.fhir.r4b.model.CanonicalType ct : t.getProfile()) {
        if (ct.hasValue() && ct.getValue().endsWith("|"+nver) && ct.getValue().startsWith("http://hl7.org/fhir/StructureDefinition/")) {
          ct.setValue(ct.getValue().replace("|"+nver, "|"+ver));
          changed = true;
        }
      }
      for (org.hl7.fhir.r4b.model.CanonicalType ct : t.getTargetProfile()) {
        if (ct.hasValue() && ct.getValue().endsWith("|"+nver) && ct.getValue().startsWith("http://hl7.org/fhir/StructureDefinition/")) {
          ct.setValue(ct.getValue().replace("|"+nver, "|"+ver));
          changed = true;
        }
      }
    }
    changed = reVersionExtList(ed.getBinding().getExtension(), nver, ver) || changed;
    if (ed.hasBinding() && ed.getBinding().hasValueSet() && ed.getBinding().getValueSet().endsWith("|"+nver) && ed.getBinding().getValueSet().startsWith("http://hl7.org/fhir/ValueSet")) {
      ed.getBinding().setValueSet(ed.getBinding().getValueSet().replace("|"+nver, "|"+ver));
      changed = true;
    }
    return changed;
  }

  private boolean reVersionExtList(List<Extension> extensions, String nver, String ver) {
    boolean changed = false;
    for (Extension e : extensions) {
      changed = reVersionExt(e, nver, ver) || changed;
    }
    return changed;
  }

  private boolean reVersionExt(Extension extension, String nver, String ver) {
    boolean changed = false;
    if (extension.getValue() instanceof org.hl7.fhir.r4b.model.CanonicalType) {
      org.hl7.fhir.r4b.model.CanonicalType ct = extension.getValueCanonicalType();
      if (ct.hasValue() && ct.getValue().endsWith("|"+nver) && ct.getValue().startsWith("http://hl7.org/fhir/StructureDefinition/")) {
        ct.setValue(ct.getValue().replace("|"+nver, "|"+ver));
        changed = true;
      }
    }
    if ("http://hl7.org/fhir/tools/StructureDefinition/snapshot-base-version".equals(extension.getUrl()) && extension.hasValueStringType() && extension.getValue().primitiveValue().equals(nver)) {
      extension.setValue(new StringType(ver));
      changed = true;
    }
    return reVersionExtList(extension.getExtension(), nver, ver) || changed;
  }

  private boolean reVersionCS(org.hl7.fhir.r4b.model.CapabilityStatement cs, String ver, String pathS, String pathT) {
    cs.setFhirVersion(org.hl7.fhir.r4b.model.Enumerations.FHIRVersion.fromCode(ver));
    return processMarkdown(cs, pathS, pathT) || true;
  }

  private boolean reVersionIG(org.hl7.fhir.r4b.model.ImplementationGuide ig, String ver, String pver, String pathS, String pathT) {
    ig.setId(ig.getId()+"."+pver);
    ig.setPackageId(ig.getPackageId()+"."+pver);
    ig.getFhirVersion().clear();
    ig.addFhirVersion(FHIRVersion.fromCode(ver));
    return processMarkdown(ig, pathS, pathT) ||  true;
  }
  
  public SpecMapManager loadSpecDetails(byte[] bs, String name, String version, String specPath) throws IOException {
    SpecMapManager map = new SpecMapManager(bs, name, version);
    map.setBase(PackageHacker.fixPackageUrl(specPath));
    return map;
  }

  private void processPackage(String filename) throws FileNotFoundException, IOException {
    System.out.println("Analysing "+filename);
    NpmPackage npm = NpmPackage.fromPackage(new FileInputStream(filename));
    String version = npm.fhirVersion();
    String pid = VersionUtilities.packageForVersion(version);
    String specPath = VersionUtilities.getSpecUrl(version);
    System.out.println("Loaded. Version = "+version);
    FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager.Builder().build();
    System.out.println("Preparing using "+pid);
    NpmPackage pi = pcm.loadPackage(pid);
    
    SpecMapManager spm = loadSpecDetails(FileUtilities.streamToBytes(pi.load("other", "spec.internals")), pi.name(), version, specPath);
    SimpleWorkerContext sp;
    IContextResourceLoader loader = new PublisherLoader(pi, spm, specPath, null, false).makeLoader();
    sp = new SimpleWorkerContext.SimpleWorkerContextBuilder().fromPackage(pi, loader, true);
    ProfileUtilities utils = new ProfileUtilities(context, new ArrayList<ValidationMessage>(), null);
    for (StructureDefinition sd : new ContextUtilities(sp).allStructures()) {
      utils.setIds(sd, true);
    }
    setContext(sp);
    System.out.println("Processing");
    for (PackageResourceInformation pri : npm.listIndexedResources("StructureDefinition")) {
      StructureDefinition sd = (StructureDefinition) loader.loadResource(npm.load(pri), true);
      checkProfile(sd);
    }
    log(npm.name());
    if (canBeR4() && canBeR4B()) {
      clonePackage(npm.name(), filename);
    }
    System.out.println("== done ==");
  }
}
