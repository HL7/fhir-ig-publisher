package org.hl7.fhir.igtools.publisher.xig;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.xig.XIGInformation.CanonicalResourceUsage;
import org.hl7.fhir.r5.conformance.profile.BindingResolution;
import org.hl7.fhir.r5.conformance.profile.ProfileKnowledgeProvider;
import org.hl7.fhir.r5.context.ContextUtilities;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionBindingComponent;
import org.hl7.fhir.r5.model.Enumerations.CapabilityStatementKind;
import org.hl7.fhir.r5.model.Enumerations.CodeSystemContentMode;
import org.hl7.fhir.r5.model.NamingSystem.NamingSystemType;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.renderers.RendererFactory;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.GenerationRules;
import org.hl7.fhir.r5.renderers.utils.RenderingContext.ResourceRendererMode;
import org.hl7.fhir.r5.terminologies.JurisdictionUtilities;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.MarkDownProcessor.Dialect;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.npm.CommonPackages;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.validation.ValidationOptions;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;

public class XIGRenderer extends XIGHandler implements ProfileKnowledgeProvider {

  private final static String HEADER=
      "<html>\r\n"+
          "<head>\r\n"+
          "  <title>$title$</title>\r\n"+
          "  <link href=\"fhir.css\" rel=\"stylesheet\"/>\r\n"+
          "</head>\r\n"+
          "<body style=\"margin: 10; background-color: white\">\r\n"+
          "<h2>$title$</h2>\r\n";

  private final static String FOOTER =
      "<hr/><p>Produced $date$</p></body>\r\n</html>\r\n";

  private XIGInformation info;
  private String target;
  private RenderingContext rc;

  private String date;

  public XIGRenderer(XIGInformation info, String target, String date) {
    super();
    this.info = info;
    this.target = target;
    this.rc = new RenderingContext(info.getCtxt(), new MarkDownProcessor(Dialect.COMMON_MARK), 
        new ValidationOptions(FhirPublication.R5, "en"), "http://hl7.org/fhir", "", new Locale("en"), ResourceRendererMode.TECHNICAL, GenerationRules.IG_PUBLISHER);
    this.rc.setDestDir(target);
    this.rc.setPkp(this);
    this.rc.setNoSlowLookup(true);
    this.date = date;
  }

  private String summaryForResource(String pid, CanonicalResource cr) {
    StringBuilder b = new StringBuilder();
    b.append("<table class=\"grid\">\r\n");
    b.append("<tr><td><a href=\""+cr.getWebPath()+"\">Source</a></td><td><a href=\""+cr.getUserString("purl")+"\">"+cr.getUserString("pid")+"</a>:"
        +cr.getUserString("pname")+" (v"+cr.getUserString("fver")+")</td></tr>\r\n");
    JsonObject j = new JsonObject();
    info.fillOutJson(pid, cr, j);
    for (JsonProperty pp : j.getProperties()) {
      if (pp.getValue().isJsonPrimitive()) {
        b.append("<tr><td>"+pp.getName()+"</td><td>"+Utilities.escapeXml(pp.getValue().asString())+"</td></tr>\r\n");
      } else {
        b.append("<tr><td>"+pp.getName()+"</td><td>");
        for (JsonElement a : ((JsonArray) pp.getValue())) {
          b.append(Utilities.escapeXml(a.asString())+" ");
        }
        b.append("</td></tr>\r\n");
      }
    }
    List<CanonicalResourceUsage> list = info.usages.get(cr.getUrl());
    if (list == null || list.isEmpty()) {
      b.append("<tr><td>Usages</td><td>(none)</td></tr>\r\n");
    } else {
      b.append("<tr><td>Usages</td><td><ul>\r\n");
      for (CanonicalResourceUsage cu : list) {
        b.append("  <li>");
        b.append(crlink(cu.getResource()));
        b.append(cu.getUsage().getDisplay());
        b.append("</li>\r\n");
      }
      b.append("</ul></td></tr>\r\n");
    }
    b.append("</table>\r\n");
    return b.toString();
  }

  public void produce(FilesystemPackageCacheManager pcm) throws IOException, FHIRException, EOperationOutcome {
    org.hl7.fhir.utilities.json.parser.JsonParser.compose(info.getJson(), new FileOutputStream(Utilities.path(target, "registry.json")));
    info.setJson(null);

    System.out.println("Generate...");
    int i = 0;
    for (CanonicalResource cr : info.getResources().values()) {
      try {
        renderResource(cr.getSourcePackage().getVID(), cr);
      } catch (Exception e) {
        Exception wrappedException = new Exception("Exception rendering canonical resource " + cr.getId() + " " + cr.getUrl(), e);
        wrappedException.printStackTrace();
      }
      i++;
      if (i > info.getResources().size() / 100) {
        System.out.print(".");
        i = 0;
      }
    }
    System.out.println("");
    FileUtilities.createDirectory(target);
    fillDirectory(pcm, target);
    StringBuilder b = new StringBuilder();
    b.append("<p><b>Views</b></p>\r\n");
    b.append("<ul>\r\n");
    addPage(b, "all-index.html", genPageSet(target, "all", "Everything"));
    addPage(b, "hl7-index.html", genPageSet(target, "hl7", "HL7"));
    addPage(b, "ihe-index.html", genPageSet(target, "ihe", "IHE"));
    addPage(b, "uv-index.html", genPageSet(target, "uv", "Intl."));
    addPage(b, "us-index.html", genPageSet(target, "us", "US"));
    for (String s : Utilities.sorted(info.getJurisdictions())) {
      if (!Utilities.existsInList(s, "uv", "us", "xver") && !s.startsWith("cda")) {
        addPage(b, s+"-index.html", genPageSet(target, s, JurisdictionUtilities.displayJurisdiction(s)));
      }
    }
    b.append("</ul>\r\n");
    b.append("<p>See also the <a href=\"extension-summary-analysis.html\">Extension Analysis</a></p>\r\n");

    b.append("<p><b>"+info.getPid().size()+" Packages Loaded</b></p>\r\n");
    b.append("<ul style=\"column-count: 4\">\r\n");
    for (String s : Utilities.sorted(info.getPid().keySet())) {
      b.append("<li><a href=\""+info.getPid().get(s)+"\">"+s+"</a></li>\r\n");
    }
    b.append("</ul>\r\n");
    genPage("XIG index", b.toString(), Utilities.path(target, "index.html"));

    System.out.println("Done");
  }

  private void fillDirectory(FilesystemPackageCacheManager pcm, String target) throws FHIRException, IOException {
    NpmPackage npm = pcm.loadPackage(CommonPackages.ID_PUBPACK);
    for (String s : npm.list("other")) {
      InputStream f = npm.load("other", s);
      FileUtilities.streamToFile(f, Utilities.path(target, s));
    }
  }

  private PageContent genPageSet(String target, String realm, String realmT) throws IOException {
    System.out.print("Indexes, scope = "+realmT);
    StringBuilder b = new StringBuilder();
    b.append("<p><b>Structures</b></p>\r\n");
    b.append("<ul style=\"column-count: 4\">\r\n");

    XIGStructureDefinitionHandler sdh = new XIGStructureDefinitionHandler(info);
    addPage(b, realmPrefix(realm)+"profiles-resources.html", sdh.makeProfilesPage(this, StructureDefinitionKind.RESOURCE, "profile-res", "Resource Profiles", realm));   
    addPage(b, realmPrefix(realm)+"profiles-datatypes.html", sdh.makeProfilesPage(this, null, "profile-dt", "DataType Profiles", realm));   
    addPage(b, realmPrefix(realm)+"extensions.html", sdh.makeExtensionsPage(this, "extensions", realm));   
    addPage(b, realmPrefix(realm)+"logicals.html", sdh.makeLogicalsPage(realm));   
    addPage(b, realmPrefix(realm)+"extension-usages-core.html", sdh.makeExtensionUsagePage(this, "Core Extension Usage", realm, true));   
    addPage(b, realmPrefix(realm)+"extension-usages-other.html", sdh.makeExtensionUsagePage(this, "Other Extension Usage", realm, false));   
    addPage(b, realmPrefix(realm)+"profiles-usages-core.html", sdh.makeProfilesUsagePage(this, "Core Profile Usage", realm, true));   
    addPage(b, realmPrefix(realm)+"profiles-usages-other.html", sdh.makeProfilesUsagePage(this, "Other Profile Usage", realm, false));   

    b.append("</ul>\r\n");
    b.append("<p><b>CodeSystems</b></p>\r\n");
    b.append("<ul style=\"column-count: 4\">\r\n");
    XIGCodeSystemHandler csh = new XIGCodeSystemHandler(info);
    addPage(b, realmPrefix(realm)+"codeSystems-complete.html", csh.makeCodeSystemPage(CodeSystemContentMode.COMPLETE, "Complete Code Systems", realm));   
    addPage(b, realmPrefix(realm)+"codeSystems-example.html", csh.makeCodeSystemPage(CodeSystemContentMode.EXAMPLE, "Example Code Systems", realm));   
    addPage(b, realmPrefix(realm)+"codeSystems-fragment.html", csh.makeCodeSystemPage(CodeSystemContentMode.FRAGMENT, "Fragement Code Systems", realm));   
    addPage(b, realmPrefix(realm)+"codeSystems-notpresent.html", csh.makeCodeSystemPage(CodeSystemContentMode.NOTPRESENT, "Not Present Code Systems", realm));   
    addPage(b, realmPrefix(realm)+"codeSystems-supplement.html", csh.makeCodeSystemPage(CodeSystemContentMode.SUPPLEMENT, "Code System Supplements", realm));   
    addPage(b, realmPrefix(realm)+"codesystem-usages-core.html", csh.makeCoreUsagePage(this, "Core CodeSystems Usage", realm));   
    addPage(b, realmPrefix(realm)+"codesystem-usages-tho.html", csh.makeTHOUsagePage(this, "THO CodeSystems Usage", realm));   

    b.append("</ul>\r\n");
    b.append("<p><b>ValueSets</b></p>\r\n");
    b.append("<ul style=\"column-count: 4\">\r\n");
    XIGValueSetHandler vsh = new XIGValueSetHandler(info);
    addPage(b, realmPrefix(realm)+"valuesets-all.html", vsh.makeValueSetsPage(null, "All ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-cpt.html", vsh.makeValueSetsPage("cpt", "CPT ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-dcm.html", vsh.makeValueSetsPage("dcm", "Dicom ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-example.html",  vsh.makeValueSetsPage("example", "Example ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-fhir.html", vsh.makeValueSetsPage("fhir", "FHIR ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-cvx.html", vsh.makeValueSetsPage("cvx", "CVX ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-icd.html", vsh.makeValueSetsPage("icd", "ICD ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-icpc.html", vsh.makeValueSetsPage("icpc", "ICPC ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-ietf.html",  vsh.makeValueSetsPage("ietf", "IETF ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-ihe.html", vsh.makeValueSetsPage("ihe", "IHE ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-internal.html", vsh.makeValueSetsPage("internal", "Internal ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-iso.html", vsh.makeValueSetsPage("iso", "ISO ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-loinc.html", vsh.makeValueSetsPage("loinc", "LOINC ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-mixed.html", vsh.makeValueSetsPage("mixed", "Mixed ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-n-a.html", vsh.makeValueSetsPage("exp", "Expansion only ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-ncpdp.html", vsh.makeValueSetsPage("ncpdp", "NCPDP ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-ndc.html", vsh.makeValueSetsPage("ndc", "NDC ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-nucc.html",  vsh.makeValueSetsPage("nucc", "NUCC ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-oid.html",  vsh.makeValueSetsPage("oid", "OID ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-tho.html",  vsh.makeValueSetsPage("tho", "THO ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-rx.html", vsh.makeValueSetsPage("rx", "RxNorm ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-sct.html", vsh.makeValueSetsPage("sct", "SNOMED CT ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-fhir.html",  vsh.makeValueSetsPage("tho", "FHIR ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-ucum.html", vsh.makeValueSetsPage("ucum", "UCUM ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valuesets-vs.html", vsh.makeValueSetsPage("vs", "Composite ValueSets", realm));   
    addPage(b, realmPrefix(realm)+"valueset-usages-core.html", vsh.makeCoreUsagePage(this, "Core ValueSets Usage", realm));   
    addPage(b, realmPrefix(realm)+"valueset-usages-tho.html", vsh.makeTHOUsagePage(this, "THO ValueSets Usage", realm));   
    b.append("</ul>\r\n");
    b.append("<p><b>ConceptMaps</b></p>\r\n");
    b.append("<ul style=\"column-count: 4\">\r\n");
    XIGConceptMapHandler cmh = new XIGConceptMapHandler(info);
    addPage(b, realmPrefix(realm)+"conceptmaps-all.html", cmh.makeConceptMapsPage(null, "All ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-cpt.html", cmh.makeConceptMapsPage("cpt", "CPT ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-dcm.html", cmh.makeConceptMapsPage("dcm", "Dicom ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-example.html",  cmh.makeConceptMapsPage("example", "Example ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-fhir.html", cmh.makeConceptMapsPage("fhir", "FHIR ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-cvx.html", cmh.makeConceptMapsPage("cvx", "CVX ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-icd.html", cmh.makeConceptMapsPage("icd", "ICD ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-ietf.html",  cmh.makeConceptMapsPage("ietf", "IETF ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-ihe.html", cmh.makeConceptMapsPage("ihe", "IHE ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-icpc.html", cmh.makeConceptMapsPage("icpc", "ICPC ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-internal.html", cmh.makeConceptMapsPage("internal", "Internal ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-iso.html", cmh.makeConceptMapsPage("iso", "ISO ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-loinc.html", cmh.makeConceptMapsPage("loinc", "LOINC ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-ncpdp.html", cmh.makeConceptMapsPage("ncpdp", "NCPDP ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-ndc.html", cmh.makeConceptMapsPage("ndc", "NDC ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-nucc.html",  cmh.makeConceptMapsPage("nucc", "NUCC ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-oid.html",  cmh.makeConceptMapsPage("oid", "OID ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-tho.html",  cmh.makeConceptMapsPage("tho", "THO ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-rx.html", cmh.makeConceptMapsPage("rx", "RxNorm ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-sct.html", cmh.makeConceptMapsPage("sct", "SNOMED CT ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-tho.html",  cmh.makeConceptMapsPage("tho", "FHIR ConceptMaps", realm));   
    addPage(b, realmPrefix(realm)+"conceptmaps-ucum.html", cmh.makeConceptMapsPage("ucum", "UCUM ConceptMaps", realm));   
    b.append("</ul>\r\n");
    b.append("<p><b>Operation Definitions</b></p>\r\n");
    b.append("<ul style=\"column-count: 4\">\r\n");
    XIGOperationDefinitionHandler oph = new XIGOperationDefinitionHandler(info);
    for (String r : Utilities.sorted(info.getOpr())) {
      addPage(b, realmPrefix(realm)+"operations-"+r.toLowerCase()+".html", oph.makeOperationsPage(r, "Operations for "+r, realm));         
    }
    b.append("</ul>\r\n");
    b.append("<p><b>Naming Systems</b></p>\r\n");
    b.append("<ul style=\"column-count: 4\">\r\n");
    XIGNamingSystemHandler nsph = new XIGNamingSystemHandler(info);
    addPage(b, realmPrefix(realm)+"namingsystems.html", nsph.makeKindPage(null, "All Naming Systems", realm));
    addPage(b, realmPrefix(realm)+"namingsystems-cs.html", nsph.makeKindPage(NamingSystemType.CODESYSTEM, "Code System Systems", realm));
    addPage(b, realmPrefix(realm)+"namingsystems-is.html", nsph.makeKindPage(NamingSystemType.IDENTIFIER, "Identifier Systems", realm));
    addPage(b, realmPrefix(realm)+"namingsystems-r.html", nsph.makeKindPage(NamingSystemType.ROOT, "Root Systems", realm));
    for (String r : Utilities.sorted(info.getNspr())) {
      addPage(b, realmPrefix(realm)+"namingsystems-t-"+r.toLowerCase()+".html", nsph.makeTypePage(r, "Systems of type "+r, realm));         
    }
    b.append("</ul>\r\n");
    b.append("<p><b>Search Definitions</b></p>\r\n");
    b.append("<ul style=\"column-count: 4\">\r\n");
    XIGSearchParameterHandler sph = new XIGSearchParameterHandler(info);
    for (String r : Utilities.sorted(info.getSpr())) {
      addPage(b, realmPrefix(realm)+"searchparams-"+r.toLowerCase()+".html", sph.makeSearchParamsPage(r, "Search Params for "+r, realm));         
    }
    b.append("</ul>\r\n");
    b.append("<p><b>CapabilityStatements</b></p>\r\n");
    b.append("<ul style=\"column-count: 4\">\r\n");
    XIGCapabilityStatementHandler csth = new XIGCapabilityStatementHandler(info);
    addPage(b, realmPrefix(realm)+"capabilitystatements.html", csth.makeCapabilityStatementPage(CapabilityStatementKind.INSTANCE, "All CapabilityStatements", realm));         
    addPage(b, realmPrefix(realm)+"capabilitystatements-instance.html", csth.makeCapabilityStatementPage(CapabilityStatementKind.INSTANCE, "Instance CapabilityStatements", realm));         
    addPage(b, realmPrefix(realm)+"capabilitystatements-capability.html", csth.makeCapabilityStatementPage(CapabilityStatementKind.CAPABILITY, "Capability CapabilityStatements", realm));         
    addPage(b, realmPrefix(realm)+"capabilitystatements-requirements.html", csth.makeCapabilityStatementPage(CapabilityStatementKind.REQUIREMENTS, "Requirements CapabilityStatements", realm));         
    b.append("</ul>\r\n");

    b.append("<p><b>Other Resources</b></p>\r\n");
    b.append("<ul style=\"column-count: 4\">\r\n");
    XIGGenericHandler oth = new XIGGenericHandler(info);
    addPage(b, realmPrefix(realm)+"resources-sm.html", oth.makeResourcesPage("StructureMap", "StructureMaps", realm));         
    addPage(b, realmPrefix(realm)+"resources-gd.html", oth.makeResourcesPage("GraphDefinition", "GraphDefinitions", realm));         
    addPage(b, realmPrefix(realm)+"resources-ad.html", oth.makeResourcesPage("ActivityDefinition", "ActivityDefinitions", realm));         
    addPage(b, realmPrefix(realm)+"resources-cd.html", oth.makeResourcesPage("ConditionDefinition", "ConditionDefinitions", realm));         
    addPage(b, realmPrefix(realm)+"resources-dd.html", oth.makeResourcesPage("DeviceDefinition", "DeviceDefinitions", realm));         
    addPage(b, realmPrefix(realm)+"resources-ed.html", oth.makeResourcesPage("EventDefinition", "EventDefinitions", realm));         
    addPage(b, realmPrefix(realm)+"resources-od.html", oth.makeResourcesPage("ObservationDefinition", "ObservationDefinitions", realm));         
    addPage(b, realmPrefix(realm)+"resources-pd.html", oth.makeResourcesPage("PlanDefinition", "PlanDefinitions", realm));         
    addPage(b, realmPrefix(realm)+"resources-q.html", oth.makeResourcesPage("Questionnaire", "Questionnaires", realm));         
    addPage(b, realmPrefix(realm)+"resources-sd.html", oth.makeResourcesPage("SpecimenDefinition", "SpecimenDefinitions", realm));         
    addPage(b, realmPrefix(realm)+"resources-es.html", oth.makeResourcesPage("ExampleScenario", "ExampleScenarios", realm));         
    addPage(b, realmPrefix(realm)+"resources-act.html", oth.makeResourcesPage("ActorDefinition", "ActorDefinitions", realm));         
    addPage(b, realmPrefix(realm)+"resources-req.html", oth.makeResourcesPage("Requirements", "Requirements", realm));         
    b.append("</ul>\r\n");

    System.out.println("");
    return new PageContent("XIG index for "+realmT, b.toString());
  }

  private void genPage(String title, String content, String fn) throws IOException {
    String cnt = HEADER.replace("$title$", title)+content+FOOTER.replace("$date$", date);
    FileUtilities.stringToFile(cnt, fn);
  }


  public void addPage(StringBuilder b, String name, PageContent page) throws IOException {
    if (page != null) {
      System.out.print(".");
      genPage(page.getTitle(), page.getContent(), Utilities.path(target, name));
      b.append("<li><a href=\""+name+"\">"+page.getTitle()+"</a></li>\r\n");
    }
  }

  private void renderResource(String pid, CanonicalResource cr) throws FHIRException, IOException, EOperationOutcome {
    RendererFactory.factory(cr, rc).renderResource(ResourceWrapper.forResource(rc.getContextUtilities(), cr));
    String s = new XhtmlComposer(false, true).compose(cr.getText().getDiv());
    new JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream(Utilities.path(target, cr.getUserString("filebase")+".json")), cr);
    //    new XmlParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream(Utilities.path(target, cr.getUserString("filebase")+".xml")), cr);
    genPage(cr.fhirType()+"-"+cr.getIdBase(), summaryForResource(pid, cr) + s, Utilities.path(target, cr.getUserString("filebase")+".html"));  
    cr.setText(null);
  }


  @Override
  public boolean isDatatype(String typeSimple) {
    return new ContextUtilities(info.getCtxt()).isDatatype(typeSimple);
  }

  @Override
  public boolean isPrimitiveType(String name) {
    return info.getCtxt().isPrimitiveType(name);
  }


  @Override
  public boolean isResource(String typeSimple) {
    return new ContextUtilities(info.getCtxt()).isResource(typeSimple);
  }

  @Override
  public boolean hasLinkFor(String typeSimple) {
    return false;
  }


  @Override
  public String getLinkFor(String corePath, String typeSimple) {
    return null;
  }


  @Override
  public BindingResolution resolveBinding(StructureDefinition def, ElementDefinitionBindingComponent binding, String path) throws FHIRException {
    return resolveBinding(def, binding.getValueSet(), path);
  }


  @Override
  public BindingResolution resolveBinding(StructureDefinition def, String url, String path) throws FHIRException {
    ValueSet vs = info.getCtxt().fetchResource(ValueSet.class, url);
    if (vs != null) {
      return new BindingResolution(vs.present(), vs.getUserData("filebase")+".html");
    } else { 
      return new BindingResolution("todo", "http://none.none/none");
    }
  }


  @Override
  public String getLinkForProfile(StructureDefinition profile, String url) {
    return null;
  }


  @Override
  public boolean prependLinks() {
    return false;
  }


  @Override
  public String getLinkForUrl(String corePath, String s) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getCanonicalForDefaultContext() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getDefinitionsName(Resource r) {
    // TODO Auto-generated method stub
    return null;
  }

}
