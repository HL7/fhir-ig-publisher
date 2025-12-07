package org.hl7.fhir.igtools.publisher;

import org.hl7.fhir.convertors.context.ContextResourceLoaderFactory;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_43_50;
import org.hl7.fhir.convertors.loaders.loaderR5.NullLoaderKnowledgeProviderR5;
import org.hl7.fhir.convertors.misc.ProfileVersionAdaptor;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.renderers.ValidationPresenter;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.context.IContextResourceLoader;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.fhirpath.ExpressionNode;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.renderers.BundleRenderer;
import org.hl7.fhir.r5.renderers.ParametersRenderer;
import org.hl7.fhir.r5.renderers.RendererFactory;
import org.hl7.fhir.r5.renderers.ResourceRenderer;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.utils.NPMPackageGenerator;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.r5.utils.structuremap.StructureMapAnalysis;
import org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities;
import org.hl7.fhir.utilities.*;
import org.hl7.fhir.utilities.i18n.I18nConstants;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

import static org.hl7.fhir.igtools.publisher.Publisher.FMM_DERIVATION_MAX;

/**
 * this class is part of the Publisher Core cluster, and handles all the checking and changing of resources once they're loaded. See @Publisher for discussion
 */

public class PublisherProcessor extends PublisherBase  {
  public PublisherProcessor(PublisherSettings settings) {
    super(settings);
  }


  public void checkLanguage() {
    if ((pf.langPolicy == ValidationPresenter.LanguagePopulationPolicy.ALL || pf.langPolicy == ValidationPresenter.LanguagePopulationPolicy.OTHERS)) {
      for (FetchedFile f : pf.changeList) {
        for (FetchedResource r : f.getResources()) {
          logDebugMessage(LogCategory.PROGRESS, "process language in res: "+r.fhirType()+"/"+r.getId());
          if (!this.pf.sourceIg.hasLanguage()) {
            if (r.getElement().hasChild("language")) {
              r.getElement().removeChild("language");
            }
          } else {
            r.getElement().setChildValue("language", this.pf.sourceIg.getLanguage());
          }
        }
      }
    }
  }

  public void loadConformance2() throws Exception {
    for (String s : metadataResourceNames())
      scanUrls(s);
    log("Load Dependency Info");
    loadDepInfo();
    log("Load Info");
    loadInfo();
    log("Load Paths");
    loadPaths();

    log("Check R4 / R4B");
    checkR4R4B();
    generateOtherVersions();

    log("Assign Comparison Ids");
    assignComparisonIds();
    if (pf.isPropagateStatus) {
      log("Propagating status");
      propagateStatus();
    }
    log("Generating Narratives");
    doActorScan();
    generateNarratives(false);
    if (!settings.isValidationOff()) {
      log("Validating Conformance Resources");
      for (String s : metadataResourceNames()) {
        validate(s);
      }
    }

    loadLists();
    checkConformanceResources();
    generateLogicalMaps();
    //    load("StructureMap"); // todo: this is a problem...
    generateAdditionalExamples();
    executeTransforms();
    validateExpressions();
    pf.errors.addAll(pf.cql.getGeneralErrors());
    scanForUsageStats();
  }

  private void doActorScan() {
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r: f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof StructureDefinition) {
          for (ElementDefinition ed : ((StructureDefinition) r.getResource()).getDifferential().getElement()) {
            for (Extension obd : ExtensionUtilities.getExtensions(ed, ExtensionDefinitions.EXT_OBLIGATION_CORE)) {
              for (Extension act : ExtensionUtilities.getExtensions(obd, "actor")) {
                ActorDefinition ad = this.pf.context.fetchResource(ActorDefinition.class, act.getValue().primitiveValue());
                if (ad != null) {
                  this.pf.rc.getActorWhiteList().add(ad);
                }
              }
            }
          }
        }

        if (r.getResource() != null && r.getResource() instanceof ActorDefinition) {
          this.pf.rc.getActorWhiteList().add((ActorDefinition) r.getResource());
        }
      }
    }
  }

  private void scanUrls(String type) throws Exception {
    logDebugMessage(LogCategory.PROGRESS, "process type: "+type);
    for (FetchedFile f : pf.fileList) {
      f.start("scan");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.fhirType().equals(type) ) {
            String url = r.getElement().getChildValue("url");
            if (url != null) {
              String title = r.getElement().getChildValue("title");
              if (title == null) {
                title = r.getElement().getChildValue("name");
              }
              String link = this.pf.igpkp.getLinkFor(r, true);
              r.getElement().setWebPath(link);
              this.pf.validationFetcher.getOtherUrls().add(url);
            }
          }
        }
      } finally {
        f.finish("scan");
      }
    }
  }


  private void loadDepInfo() {
    for (FetchedFile f : pf.fileList) {
      f.start("loadDepInfo");
      try {
        for (FetchedResource r : f.getResources()) {
          String url = r.getElement().getChildValue("url");
          if (url != null) {
            String title = r.getElement().getChildValue("title");
            if (title == null) {
              title = r.getElement().getChildValue("name");
            }
            String link = this.pf.igpkp.getLinkFor(r, true);
            switch (r.fhirType() ) {
              case "CodeSystem":
                this.pf.dependentIgFinder.addCodeSystem(url, title, link);
                break;
              case "ValueSet":
                this.pf.dependentIgFinder.addValueSet(url, title, link);
                break;
              case "StructureDefinition":
                String kind = r.getElement().getChildValue("url");
                if ("logical".equals(kind)) {
                  this.pf.dependentIgFinder.addLogical(url, title, link);
                } else if ("Extension".equals(r.getElement().getChildValue("type"))) {
                  this.pf.dependentIgFinder.addExtension(url, title, link);
                } else {
                  this.pf.dependentIgFinder.addProfile(url, title, link);
                }
                break;
              case "SearchParameter":
                this.pf.dependentIgFinder.addSearchParam(url, title, link);
                break;
              case "CapabilityStatement":
                this.pf.dependentIgFinder.addCapabilityStatement(url, title, link);
                break;
              default:
                // do nothing
            }
          }
        }
      } finally {
        f.finish("loadDepInfo");
      }
    }
    pf.dependentIgFinder.go();
  }


  private void loadInfo() {
    for (FetchedFile f : pf.fileList) {
      f.start("loadInfo");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.getResEntry() != null) {
            if (r.getResource() instanceof StructureDefinition) {
              ExtensionUtilities.setStringExtension(r.getResEntry(), ExtensionDefinitions.EXT_IGP_RESOURCE_INFO, r.fhirType()+":"+IGKnowledgeProvider.getSDType(r));
            } else {
              ExtensionUtilities.setStringExtension(r.getResEntry(), ExtensionDefinitions.EXT_IGP_RESOURCE_INFO, r.fhirType());
            }
          }
        }
      } finally {
        f.finish("loadInfo");
      }
    }
  }


  private void loadPaths() {
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (!r.getElement().hasWebPath()) {
          this.pf.igpkp.checkForPath(f, r, r.getElement());
        }
      }
    }
  }

  private void checkR4R4B() throws Exception {
    logDebugMessage(LogCategory.PROGRESS, "R4/R4B Check");
    for (FetchedFile f : pf.fileList) {
      f.start("checkR4R4B");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.getResource() instanceof StructureDefinition) {
            this.pf.r4tor4b.checkProfile((StructureDefinition) r.getResource());
          } else {
            this.pf.r4tor4b.checkExample(r.getElement());
          }
        }
      } finally {
        f.finish("checkR4R4B");
      }
    }
  }

  private void generateOtherVersions() throws Exception {
    for (String v : pf.generateVersions) {
      String version = VersionUtilities.versionFromCode(v);
      if (!VersionUtilities.versionMatches(version, pf.context.getVersion())) {
        logDebugMessage(LogCategory.PROGRESS, "Generate Other Version: "+version);

        NpmPackage targetNpm = pf.pcm.loadPackage(VersionUtilities.packageForVersion(version));
        IContextResourceLoader loader = ContextResourceLoaderFactory.makeLoader(targetNpm.fhirVersion(), new NullLoaderKnowledgeProviderR5());
        SimpleWorkerContext tctxt = new SimpleWorkerContext.SimpleWorkerContextBuilder().withAllowLoadingDuplicates(true).fromPackage(targetNpm, loader, true);
        ProfileVersionAdaptor pva = new ProfileVersionAdaptor(pf.context, tctxt);

        for (FetchedFile f : pf.fileList) {
          f.start("generateOtherVersions");
          try {
            for (FetchedResource r : f.getResources()) {
              if (r.getResource() instanceof StructureDefinition) {
                generateOtherVersion(r, pva, version, (StructureDefinition) r.getResource());
              }
              if (r.getResource() instanceof SearchParameter) {
                generateOtherVersion(r, pva, version, (SearchParameter) r.getResource());
              }
            }
          } finally {
            f.finish("generateOtherVersions");
          }
        }
        List<ProfileVersionAdaptor.ConversionMessage> log = new ArrayList<>();
        pva.generateSnapshots(log);
        for (ProfileVersionAdaptor.ConversionMessage m : log) {
          System.out.println(m.getMessage());
        }

        for (FetchedFile f: pf.fileList) {
          f.start("generateOtherVersions");
          try {
            for (FetchedResource r : f.getResources()) {
              if (r.getResource() != null) {
                checkForCoreDependencies(this.pf.vnpms.get(v), tctxt, r.getResource(), targetNpm);
              }
            }
          } finally {
            f.finish("generateOtherVersions");
          }
        }
      }
    }
  }

  private void checkForCoreDependencies(NPMPackageGenerator npm, SimpleWorkerContext tctxt, Resource res, NpmPackage tnpm) throws IOException {
    if (res instanceof StructureDefinition) {
      checkForCoreDependenciesSD(npm, tctxt, (StructureDefinition) res, tnpm);
    }
    if (res instanceof ValueSet) {
      checkForCoreDependenciesVS(npm, tctxt, (ValueSet) res, tnpm);
    }
  }


  private void checkForCoreDependenciesSD(NPMPackageGenerator npm, SimpleWorkerContext tctxt, StructureDefinition sd, NpmPackage tnpm) throws IOException {
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      if (ed.hasBinding() && ed.getBinding().hasValueSet()) {
        ValueSet vs = pf.context.fetchResource(ValueSet.class, ed.getBinding().getValueSet());
        if (vs != null) {
          checkForCoreDependenciesVS(npm, tctxt, vs, tnpm);
        }
      }
    }
  }

  private void checkForCoreDependenciesVS(NPMPackageGenerator npm, SimpleWorkerContext tctxt, ValueSet valueSet, NpmPackage tnpm) throws IOException {

    if (isCoreResource(valueSet)) {
      if (!inTargetCore(tnpm, valueSet)) {
        if (!npm.hasFile(NPMPackageGenerator.Category.RESOURCE, valueSet.fhirType()+"-"+valueSet.getIdBase()+".json")) {
          noteOtherVersionAddedFile(tctxt.getVersion(), "ValueSet", valueSet.getIdBase());
          npm.addFile(NPMPackageGenerator.Category.RESOURCE, valueSet.fhirType()+"-"+valueSet.getIdBase()+".json", convVersion(valueSet, tctxt.getVersion()));
        }
      }
    }
    for (ValueSet.ConceptSetComponent inc : valueSet.getCompose().getInclude()) {
      for (CanonicalType c : inc.getValueSet()) {
        ValueSet vs = pf.context.fetchResource(ValueSet.class, c.getValue());
        if (vs != null) {
          checkForCoreDependenciesVS(npm, tctxt, vs, tnpm);
        }
      }
      if (inc.hasSystem()) {
        CodeSystem cs = pf.context.fetchResource(CodeSystem.class, inc.getSystem(), inc.getVersion());
        if (cs != null) {
          checkForCoreDependenciesCS(npm, tctxt, cs, tnpm);
        }
      }
    }
  }

  private void checkForCoreDependenciesCS(NPMPackageGenerator npm, SimpleWorkerContext tctxt, CodeSystem cs, NpmPackage tnpm) throws IOException {
    if (isCoreResource(cs)) {
      if (!inTargetCore(tnpm, cs)) {
        if (!npm.hasFile(NPMPackageGenerator.Category.RESOURCE, cs.fhirType()+"-"+cs.getIdBase()+".json")) {
          noteOtherVersionAddedFile(tctxt.getVersion(), "CodeSystem", cs.getIdBase());
          npm.addFile(NPMPackageGenerator.Category.RESOURCE, cs.fhirType()+"-"+cs.getIdBase()+".json", convVersion(cs, tctxt.getVersion()));
        }
      }
    }
  }

  private void noteOtherVersionAddedFile(String ver, String type, String id) {
    Set<String> ids = pf.otherVersionAddedResources.get(ver+"-"+type);
    if (ids == null) {
      ids = new HashSet<String>();
      pf.otherVersionAddedResources .put(ver+"-"+type, ids);
    }
    ids.add(id);
  }

  private boolean inTargetCore(NpmPackage tnpm, CanonicalResource cr) throws IOException {
    boolean res = tnpm.hasCanonical(cr.getUrl());
    return res;
  }

  private boolean isCoreResource(CanonicalResource cr) {
    return cr.hasSourcePackage() && Utilities.existsInList(cr.getSourcePackage().getId(), "hl7.fhir.r5.core", "hl7.fhir.r4.core");
  }


  private void generateOtherVersion(FetchedResource r, ProfileVersionAdaptor pva, String v, StructureDefinition resource) throws FileNotFoundException, IOException {
    List<ProfileVersionAdaptor.ConversionMessage> log = new ArrayList<>();
    try {
      StructureDefinition sd = pva.convert(resource, log);
      r.getOtherVersions().put(v+"-StructureDefinition", new FetchedResource.AlternativeVersionResource(log, sd));
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Error converting "+r.getId()+" to "+v+": "+e.getMessage());
      log.add(new ProfileVersionAdaptor.ConversionMessage(e.getMessage(), ProfileVersionAdaptor.ConversionMessageStatus.ERROR));
      r.getOtherVersions().put(v+"-StructureDefinition", new FetchedResource.AlternativeVersionResource(log, null));
    }
  }

  private void generateOtherVersion(FetchedResource r, ProfileVersionAdaptor pva, String v, SearchParameter resource) throws FileNotFoundException, IOException {
    List<ProfileVersionAdaptor.ConversionMessage> log = new ArrayList<>();
    try {
      SearchParameter sp = pva.convert(resource, log);
      r.getOtherVersions().put(v+"-SearchParameter", new FetchedResource.AlternativeVersionResource(log, sp));
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Error converting "+r.getId()+" to "+v+": "+e.getMessage());
      log.add(new ProfileVersionAdaptor.ConversionMessage(e.getMessage(), ProfileVersionAdaptor.ConversionMessageStatus.ERROR));
      r.getOtherVersions().put(v+"-SearchParameter", new FetchedResource.AlternativeVersionResource(log, null));
    }
  }

  private void validateExpressions() {
    logDebugMessage(LogCategory.PROGRESS, "Validate Expressions");
    for (FetchedFile f : pf.changeList) {
      f.start("validateExpressions");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.getResource() instanceof StructureDefinition && !r.isSnapshotted()) {
            StructureDefinition sd = (StructureDefinition) r.getResource();
            validateExpressions(f, sd, r);
          }
        }
      } finally {
        f.finish("validateExpressions");
      }
    }
  }

  private void validateExpressions(FetchedFile f, StructureDefinition sd, FetchedResource r) {
    FHIRPathEngine fpe = new FHIRPathEngine(this.pf.context);
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      for (ElementDefinition.ElementDefinitionConstraintComponent inv : ed.getConstraint()) {
        validateExpression(f, sd, fpe, ed, inv, r);
      }
    }
  }

  private void validateExpression(FetchedFile f, StructureDefinition sd, FHIRPathEngine fpe, ElementDefinition ed, ElementDefinition.ElementDefinitionConstraintComponent inv, FetchedResource r) {
    if (inv.hasExpression()) {
      try {
        ExpressionNode n = (ExpressionNode) inv.getUserData(UserDataNames.validator_expression_cache);
        if (n == null) {
          n = fpe.parse(inv.getExpression(), sd.getUrl()+"#"+ed.getId()+" / "+inv.getKey());
          inv.setUserData(UserDataNames.validator_expression_cache, n);
        }
        fpe.check(null, "Resource", sd, ed.getPath(), n);
      } catch (Exception e) {
        f.getErrors().add(new ValidationMessage(ValidationMessage.Source.ProfileValidator, ValidationMessage.IssueType.INVALID, "StructureDefinition.where(url = '"+sd.getUrl()+"').snapshot.element.where('path = '"+ed.getPath()+"').constraint.where(key = '"+inv.getKey()+"')", e.getMessage(), ValidationMessage.IssueSeverity.ERROR));
        r.getErrors().add(new ValidationMessage(ValidationMessage.Source.ProfileValidator, ValidationMessage.IssueType.INVALID, "StructureDefinition.where(url = '"+sd.getUrl()+"').snapshot.element.where('path = '"+ed.getPath()+"').constraint.where(key = '"+inv.getKey()+"')", e.getMessage(), ValidationMessage.IssueSeverity.ERROR));
      }
    }
  }

  private void generateLogicalMaps() throws Exception {
    StructureMapUtilities mu = new StructureMapUtilities(pf.context, null, null);
    for (FetchedFile f : pf.fileList) {
      f.start("generateLogicalMaps");
      try {
        List<StructureMap> maps = new ArrayList<StructureMap>();
        for (FetchedResource r : f.getResources()) {
          if (r.getResource() instanceof StructureDefinition) {
            StructureMap map = mu.generateMapFromMappings((StructureDefinition) r.getResource());
            if (map != null) {
              maps.add(map);
            }
          }
        }
        for (StructureMap map : maps) {
          FetchedResource nr = f.addResource(f.getName()+" (LM)");
          nr.setResource(map);
          nr.setElement(convertToElement(nr, map));
          nr.setId(map.getId());
          nr.setTitle(map.getName());
          this.pf.igpkp.findConfiguration(f, nr);
        }
      } finally {
        f.finish("generateLogicalMaps");
      }
    }
  }


  public void validate() throws Exception {
    if (settings.isValidationOff()) {
      return;
    }

    checkURLsUnique();
    checkOIDsUnique();

    for (FetchedFile f : pf.changeList) {
      f.start("validate");
      try {
        logDebugMessage(LogCategory.PROGRESS, " .. validate "+f.getName());
        logDebugMessage(LogCategory.PROGRESS, " .. "+f.getName());
        if (!f.getResources().isEmpty()) {
          FetchedResource r0 = f.getResources().get(0);
          if (f.getLogical() != null && f.getResources().size() == 1 && !r0.fhirType().equals("Binary")) {
            throw new Error("Not done yet");
          } else {
            for (FetchedResource r : f.getResources()) {
              if (!r.isValidated()) {
                logDebugMessage(LogCategory.PROGRESS, "     validating " + r.getTitle());
//              log("     validating "+r.getTitle());
                validate(f, r);
              }
            }
            if (f.getLogical() != null && f.getResources().size() == 1 && r0.fhirType().equals("Binary")) {
              Binary bin = (Binary) r0.getResource();
              StructureDefinition profile = this.pf.context.fetchResource(StructureDefinition.class, f.getLogical());
              List<ValidationMessage> errs = new ArrayList<ValidationMessage>();
              if (profile == null) {
                errs.add(new ValidationMessage(ValidationMessage.Source.InstanceValidator, ValidationMessage.IssueType.NOTFOUND, "file", this.pf.context.formatMessage(I18nConstants.Bundle_BUNDLE_Entry_NO_LOGICAL_EXPL, r0.getId(), f.getLogical()), ValidationMessage.IssueSeverity.ERROR));
              } else {
                Manager.FhirFormat fmt = Manager.FhirFormat.readFromMimeType(bin.getContentType() == null ? f.getContentType() : bin.getContentType());
                TimeTracker.Session tts = this.pf.tt.start("validation");
                List<StructureDefinition> profiles = new ArrayList<>();
                profiles.add(profile);
                validate(f, r0, bin, errs, fmt, profiles);
                tts.end();
              }
              processValidationOutcomes(f, r0, errs);
            }
          }
        }
      } finally {
        f.finish("validate");
      }
    }
    logDebugMessage(LogCategory.PROGRESS, " .. check Profile Examples");
    logDebugMessage(LogCategory.PROGRESS, "gen narratives");
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.fhirType().equals("StructureDefinition")) {
          validateSD(f, r);
        }
      }
    }
  }

  private void validate(FetchedFile f, FetchedResource r, Binary bin, List<ValidationMessage> errs, Manager.FhirFormat fmt, List<StructureDefinition> profiles) {
    long ts = System.currentTimeMillis();
    r.setLogicalElement(this.pf.validator.validate(r.getElement(), errs, new ByteArrayInputStream(bin.getContent()), fmt, profiles));
    long tf = System.currentTimeMillis();
    if (tf-ts > this.pf.validationLogTime && this.pf.validationLogTime > 0) {
      reportLongValidation(f, r, tf-ts);
    }
  }

  private void validate(FetchedFile f, FetchedResource r, List<ValidationMessage> errs, List<StructureDefinition> profiles) {
    long ts = System.currentTimeMillis();
    this.pf.validator.validate(r.getElement(), errs, null, r.getElement(), profiles);
    long tf = System.currentTimeMillis();
    if (tf-ts > this.pf.validationLogTime && this.pf.validationLogTime > 0) {
      reportLongValidation(f, r, tf-ts);
    }
  }

  private void validate(FetchedFile f, FetchedResource r, List<ValidationMessage> errs, Binary bin) {
    long ts = System.currentTimeMillis();
    this.pf.validator.validate(r.getElement(), errs, new ByteArrayInputStream(bin.getContent()), Manager.FhirFormat.readFromMimeType(bin.getContentType() == null ? f.getContentType() : bin.getContentType()));
    long tf = System.currentTimeMillis();
    if (tf-ts > this.pf.validationLogTime && this.pf.validationLogTime > 0) {
      reportLongValidation(f, r, tf-ts);
    }
  }

  private void validate(FetchedFile f, FetchedResource r, List<ValidationMessage> errs, Binary bin, StructureDefinition sd) {
    long ts = System.currentTimeMillis();
    List<StructureDefinition> profiles = new ArrayList<StructureDefinition>();
    profiles.add(sd);
    this.pf.validator.validate(r.getElement(), errs, new ByteArrayInputStream(bin.getContent()), Manager.FhirFormat.readFromMimeType(bin.getContentType() == null ? f.getContentType(): bin.getContentType()), profiles);
    long tf = System.currentTimeMillis();
    if (tf-ts > this.pf.validationLogTime && this.pf.validationLogTime > 0) {
      reportLongValidation(f, r, tf-ts);
    }
  }

  private void validate(FetchedFile f, FetchedResource r, List<ValidationMessage> errs, Resource ber) {
    long ts = System.currentTimeMillis();
    this.pf.validator.validate(r.getElement(), errs, ber, ber.getUserString(UserDataNames.map_profile));
    long tf = System.currentTimeMillis();
    if (tf-ts > this.pf.validationLogTime && this.pf.validationLogTime > 0) {
      reportLongValidation(f, r, tf-ts);
    }
  }

  private void validate(FetchedFile f, FetchedResource r, List<ValidationMessage> errs) {
    long ts = System.currentTimeMillis();
    this.pf.validator.validate(r.getElement(), errs, null, r.getElement());
    long tf = System.currentTimeMillis();
    if (tf-ts > this.pf.validationLogTime && this.pf.validationLogTime > 0) {
      reportLongValidation(f, r, tf-ts);
    }
  }

  private void reportLongValidation(FetchedFile f, FetchedResource r, long l) {
    String bps = Long.toString(f.getSize()/l);
    System.out.println("Long Validation for "+f.getTitle()+" resource "+r.fhirType()+"/"+r.getId()+": "+Long.toString(l)+"ms ("+bps+" kb/sec)");
    System.out.println("  * "+ this.pf.validator.reportTimes());
  }

  private void checkURLsUnique() {
    Map<String, FetchedResource> urls = new HashMap<>();
    for (FetchedFile f : pf.fileList) {
      f.start("checkURLsUnique");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
            CanonicalResource cr = (CanonicalResource) r.getResource();
            String url = cr.getUrl();
            if (url != null) {
              if (urls.containsKey(url)) {
                FetchedResource rs = urls.get(url);
                CanonicalResource crs = (CanonicalResource) rs.getResource();
                if (!(crs.getStatus() == Enumerations.PublicationStatus.RETIRED || cr.getStatus() == Enumerations.PublicationStatus.RETIRED)) {
                  FetchedFile fs = findFileForResource(rs);
                  boolean local = url.startsWith(this.pf.igpkp.getCanonical());
                  f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "Resource", "The URL '"+url+"' has already been used by "+rs.getId()+" in "+fs.getName(), local ? ValidationMessage.IssueSeverity.ERROR : ValidationMessage.IssueSeverity.WARNING));
                  fs.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "Resource", "The URL '"+url+"' is also used by "+r.getId()+" in "+f.getName(), local ? ValidationMessage.IssueSeverity.ERROR : ValidationMessage.IssueSeverity.WARNING));
                }
              } else {
                urls.put(url, r);
              }
            }
          }
        }
      } finally {
        f.finish("checkURLsUnique");
      }
    }
  }

  private void checkOIDsUnique() {
    if (pf.oidRoot != null) {
      try {
        JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObjectFromUrl("https://fhir.github.io/ig-registry/oid-assignments.json");
        JsonObject assignments = json.getJsonObject("assignments");
        String ig = null;
        String oid = null;
        if (assignments.has(pf.oidRoot)) {
          ig = assignments.getJsonObject(pf.oidRoot).asString("id");
        }
        for (JsonProperty p : assignments.getProperties()) {
          if (p.getValue().isJsonObject() && pf.sourceIg.getPackageId().equals(p.getValue().asJsonObject().asString("id"))) {
            oid = p.getName();
          }
        }
        if (oid == null && ig == null) {
          pf.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "ImplementationGuide", "The assigned auto-oid-root value '"+ pf.oidRoot +"' is not registered in https://github.com/FHIR/ig-registry/blob/master/oid-assignments.json so isn't known to be valid", ValidationMessage.IssueSeverity.WARNING));
        } else if (oid != null && !oid.equals(pf.oidRoot)) {
          throw new FHIRException("The assigned auto-oid-root value '"+ pf.oidRoot +"' does not match the value of '"+ pf.oidRoot +"' registered in https://github.com/FHIR/ig-registry/blob/master/oid-assignments.json so cannot proceed");
        } else if (ig != null && !ig.equals(pf.sourceIg.getPackageId())) {
          throw new FHIRException("The assigned auto-oid-root value '"+ pf.oidRoot +"' is already registered to the IG '"+ig+"' in https://github.com/FHIR/ig-registry/blob/master/oid-assignments.json so cannot proceed");
        }
      } catch (Exception e) {
        pf.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "ImplementationGuide", "Unable to check auto-oid-root because "+e.getMessage(), ValidationMessage.IssueSeverity.INFORMATION));
      }
    }
    String oidHint = " (OIDs are easy to assign - see https://build.fhir.org/ig/FHIR/fhir-tools-ig/CodeSystem-ig-parameters.html#ig-parameters-auto-oid-root)";
    Map<String, FetchedResource> oidMap = new HashMap<>();
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
          CanonicalResource cr = (CanonicalResource) r.getResource();
          if (r.isExample()) {
            List<String> oids = loadOids(cr);
            if (oids.isEmpty()) {
              if (Utilities.existsInList(r.getResource().fhirType(), "CodeSystem", "ValueSet")) {
                if (forHL7orFHIR()) {
                  f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "Resource", "The resource "+r.fhirType()+"/"+r.getId()+" must have an OID assigned to cater for possible use with OID based terminology systems e.g. CDA usage"+oidHint, ValidationMessage.IssueSeverity.ERROR));
                } else {
                  f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "Resource", "The resource "+r.fhirType()+"/"+r.getId()+" should have an OID assigned to cater for possible use with OID based terminology systems e.g. CDA usage"+oidHint, ValidationMessage.IssueSeverity.WARNING));
                }
              } else {
                f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "Resource", "The resource "+r.fhirType()+"/"+r.getId()+" could usefully have an OID assigned"+oidHint, ValidationMessage.IssueSeverity.INFORMATION));
              }
            } else {
              for (String oid : oids) {
                if (oidMap.containsKey(oid)) {
                  FetchedResource rs = oidMap.get(oid);
                  FetchedFile fs = findFileForResource(rs);
                  f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "Resource", "The OID '"+oid+"' has already been used by "+rs.getId()+" in "+fs.getName(), ValidationMessage.IssueSeverity.ERROR));
                  fs.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "Resource", "The OID '"+oid+"' is also used by "+r.getId()+" in "+f.getName(), ValidationMessage.IssueSeverity.ERROR));
                } else {
                  oidMap.put(oid, r);
                }
              }
            }
          }
        }
      }
    }
  }

  private List<String> loadOids(CanonicalResource cr) {
    List<String> res = new ArrayList<>();
    for (Identifier id : cr.getIdentifier()) {
      if (id.hasValue() && id.getValue().startsWith("urn:oid:") && id.getUse() != Identifier.IdentifierUse.OLD) {
        res.add(id.getValue().substring(8));
      }
    }
    return res;
  }

  public void validateSD(FetchedFile f, FetchedResource r) {
    StructureDefinition sd = (StructureDefinition) r.getResource();
    if (!sd.getAbstract() && !isClosing(sd)) {
      if (sd.getKind() == StructureDefinition.StructureDefinitionKind.RESOURCE) {
        int cE = countStatedExamples(sd.getUrl(), sd.getVersionedUrl());
        int cI = countFoundExamples(sd.getUrl(), sd.getVersionedUrl());
        if (cE + cI == 0) {
          f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this profile", ValidationMessage.IssueSeverity.WARNING));
          r.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this profile", ValidationMessage.IssueSeverity.WARNING));
        } else if (cE == 0) {
          f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no explicitly linked examples for this profile", ValidationMessage.IssueSeverity.INFORMATION));
          r.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no explicitly linked examples for this profile", ValidationMessage.IssueSeverity.INFORMATION));
        }
      } else if (sd.getKind() == StructureDefinition.StructureDefinitionKind.COMPLEXTYPE) {
        if (!this.pf.noUsageCheck) {
          if (sd.getType().equals("Extension")) {
            int c = countUsages(getFixedUrl(sd));
            if (c == 0) {
              f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this extension", ValidationMessage.IssueSeverity.WARNING));
              r.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this extension", ValidationMessage.IssueSeverity.WARNING));
            }
          } else {
            int cI = countFoundExamples(sd.getUrl(), sd.getVersionedUrl());
            if (cI == 0) {
              f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this data type profile", ValidationMessage.IssueSeverity.WARNING));
              r.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, "StructureDefinition.where(url = '"+sd.getUrl()+"')", "The Implementation Guide contains no examples for this data type profile", ValidationMessage.IssueSeverity.WARNING));
            }
          }
        }
      }
    }
  }


  private void assignComparisonIds() {
    int i = 0;
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() instanceof StructureDefinition) {
          StructureDefinition sd = (StructureDefinition) r.getResource();
          for (Extension ext : sd.getExtensionsByUrl(ExtensionDefinitions.EXT_SD_IMPOSE_PROFILE)) {
            StructureDefinition sdi = this.pf.context.fetchResource(StructureDefinition.class, ext.getValue().primitiveValue());
            if (sdi != null && !sdi.hasUserData(UserDataNames.pub_imposes_compare_id)) {
              String cid = "c"+Integer.toString(i);
              sdi.setUserData(UserDataNames.pub_imposes_compare_id, cid);
              i++;
            }
          }
        }
      }
    }
  }


  /*
   * Propagate status goes through all resources in the IG and propagates the FMM and Standards Status declarations from top-level artifacts
   * to any dependencies that don't declare their own values.  If different statuses or maturity level would propagate to a dependency from
   * different artifacts, the 'highest' FMM or most mature standards status will apply (Normative or Informative, then STU, then Draft).
   * Information does not propagate to artifacts marked as examples or as experimental.
   * Propagation is based on references.  E.g. the IG references everything (so its status will propagate everywhere by default).  If there
   * are higher statuses on certain CapabilityStatements, Operations or Profiles, those status will propagate to the artifacts they reference,
   * such as other profiles, ValueSets or CodeSystems.
   * Propagation only happens within the context of an IG.  There is no propagation across artifacts present in other IG packages listed as
   * dependencies.
   */
  private void propagateStatus() throws Exception {
    TimeTracker.Session tts = pf.tt.start("propagating status");
    logDebugMessage(LogCategory.PROGRESS, "propagating status");
    IntegerType igFMM = pf.sourceIg.hasExtension(ExtensionDefinitions.EXT_FMM_LEVEL) ? pf.sourceIg.getExtensionByUrl(ExtensionDefinitions.EXT_FMM_LEVEL).getValueIntegerType() : null;
    CodeType igStandardsStatus = pf.sourceIg.hasExtension(ExtensionDefinitions.EXT_STANDARDS_STATUS) ? pf.sourceIg.getExtensionByUrl(ExtensionDefinitions.EXT_STANDARDS_STATUS).getValueCodeType() : null;
    String igNormVersion = pf.sourceIg.hasExtension(ExtensionDefinitions.EXT_NORMATIVE_VERSION) ? pf.sourceIg.getExtensionByUrl(ExtensionDefinitions.EXT_NORMATIVE_VERSION).getValueStringType().asStringValue() : null;

    // If IG doesn't declare FMM or standards status, nothing to do
    if (igFMM == null && igStandardsStatus == null)
      return;

    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        if (!r.isExample())
          updateResourceStatus(r, igFMM, igStandardsStatus, igNormVersion, this.pf.sourceIg.getUrl());
      }
    }

    updatePageStatus(pf.publishedIg.getDefinition().getPage(), null, new CodeType("informative"), null);
    tts.end();
  }

  private void updatePageStatus(ImplementationGuide.ImplementationGuideDefinitionPageComponent page, IntegerType parentFmm, CodeType parentStatus, String parentNormVersion) {
    IntegerType fmm = null;
    CodeType standardsStatus = page.hasExtension(ExtensionDefinitions.EXT_STANDARDS_STATUS) ? page.getExtensionByUrl(ExtensionDefinitions.EXT_STANDARDS_STATUS).getValueCodeType() : null;
    String normVersion = pf.sourceIg.hasExtension(ExtensionDefinitions.EXT_NORMATIVE_VERSION) ? pf.sourceIg.getExtensionByUrl(ExtensionDefinitions.EXT_NORMATIVE_VERSION).getValueStringType().asStringValue() : null;

    Extension fmmExt = page.getExtensionByUrl(ExtensionDefinitions.EXT_FMM_LEVEL);

    if (parentStatus != null && standardsStatus == null) {
      standardsStatus = parentStatus.copy();
      page.addExtension(new Extension(ExtensionDefinitions.EXT_STANDARDS_STATUS, standardsStatus));
      if (parentNormVersion != null && normVersion == null) {
        normVersion = parentNormVersion;
        page.addExtension(new Extension(ExtensionDefinitions.EXT_NORMATIVE_VERSION, new StringType(normVersion)));
      }
    } else {
      parentNormVersion = null;
    }

    if (standardsStatus.getValue().equals("informative")) {
      // We strip FMMs for informative artifacts
      if (fmmExt != null)
        page.getExtension().remove(fmmExt);
    } else {
      if (parentFmm != null && fmmExt == null) {
        fmm = parentFmm.copy();
        page.addExtension(new Extension(ExtensionDefinitions.EXT_FMM_LEVEL, fmm));
      } else if (fmmExt != null)
        fmm = fmmExt.getValueIntegerType();
    }
    for (ImplementationGuide.ImplementationGuideDefinitionPageComponent childPage: page.getPage()) {
      FetchedResource res = pf.resources.get(page.getName());
      if (res == null)
        updatePageStatus(childPage, fmm, standardsStatus, normVersion);
    }
  }

  private void updateResourceStatus(Reference ref, IntegerType parentFmm, CodeType parentStatus, String parentNormVersion, String parentCanonical) {
    String canonical = ref.getReference();
    if (canonical == null)
      return;
    if (!canonical.contains("://"))
      canonical = pf.igpkp.getCanonical() + "/" + canonical;
    FetchedResource r = pf.canonicalResources.get(canonical);
    if (r != null) {
      updateResourceStatus(r, parentFmm, parentStatus, parentNormVersion, parentCanonical);
    }
  }

  private void updateResourceStatus(String ref, IntegerType parentFmm, CodeType parentStatus, String parentNormVersion, String parentCanonical) {
    String canonical = ref;
    if (canonical == null)
      return;
    if (!canonical.contains("://"))
      canonical = pf.igpkp.getCanonical() + "/" + canonical;
    FetchedResource r = pf.canonicalResources.get(canonical);
    if (r != null) {
      updateResourceStatus(r, parentFmm, parentStatus, parentNormVersion, parentCanonical);
    }
  }

  private void updateResourceStatus(CanonicalType canonical, IntegerType parentFmm, CodeType parentStatus, String parentNormVersion, String parentCanonical) {
    FetchedResource r = pf.canonicalResources.get(canonical.getValue());
    if (r != null) {
      updateResourceStatus(r, parentFmm, parentStatus, parentNormVersion, parentCanonical);
    }
  }

  private void updateResourceStatus(FetchedResource r, IntegerType parentFmm, CodeType parentStatus, String parentNormVersion, String parentCanonical) {
    // We only propagate status for resources that:
    // - are canonical resources
    // - aren't examples
    // - aren't experimental
    // - aren't one of these types:
    //     ChargeItemDefinition, Citation, ConditionDefinition, Evidence, EvidenceReport, EvidenceVariable, ExampleScenario, ObservationDefinition, TestScript
    boolean isInformative = false;
    if (!(r.getResource() instanceof CanonicalResource))
      return;

    CanonicalResource res = (CanonicalResource)r.getResource();

    if (r.isExample())
      isInformative = true;
    else {
      if (res.hasExperimental() && res.getExperimental())
        isInformative = true;

      switch (res.getResourceType()) {
        case ChargeItemDefinition :
        case Citation:
        case ConditionDefinition:
        case EvidenceReport:
        case EvidenceVariable:
        case ExampleScenario:
        case ObservationDefinition:
          isInformative = true;
        default:
          // We're in a resource we need to process, so continue on
      }
    }

    Extension statusExt = res.getExtensionByUrl(ExtensionDefinitions.EXT_STANDARDS_STATUS);
    CodeType status = statusExt!=null ? statusExt.getValueCodeType() : null;
    String statusNormVersion = res.hasExtension(ExtensionDefinitions.EXT_NORMATIVE_VERSION) ? res.getExtensionByUrl(ExtensionDefinitions.EXT_NORMATIVE_VERSION).getValueStringType().asStringValue() : null;
    if (isInformative) {
      if (status == null) {
        CodeType code = new CodeType("informative");
        code.addExtension(ExtensionDefinitions.EXT_FMM_DERIVED, new CanonicalType(parentCanonical));
        res.addExtension(ExtensionDefinitions.EXT_STANDARDS_STATUS, code);
      } else if (!Utilities.existsInList(status.getValue(), "informative", "draft", "deprecated")) {
        pf.errors.add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.INVALID, res.getResourceType() + " " + r.getId(), "If a resource is not implementable, is marked as experimental or example, the standards status can only be 'informative', 'draft' or 'deprecated', not '"+status.getValue()+"'.", ValidationMessage.IssueSeverity.ERROR));
      }

    } else {
      Extension fmmExt = res.getExtensionByUrl(ExtensionDefinitions.EXT_FMM_LEVEL);
      IntegerType fmm = fmmExt!=null ? fmmExt.getValueIntegerType() : null;

      boolean fmmChanged = false;
      if (parentFmm !=null) {
        boolean addExtension = false;
        if (fmm == null) {
          addExtension = true;

        } else if (fmm.hasExtension(ExtensionDefinitions.EXT_FMM_DERIVED)) {
          if (fmm.getValue() < parentFmm.getValue()) {
            res.getExtension().remove(fmmExt);
            addExtension = true;

          } else if (fmm.getValue() == parentFmm.getValue()) {
            if (fmm.getExtensionsByUrl(ExtensionDefinitions.EXT_FMM_DERIVED).size() < FMM_DERIVATION_MAX)
              fmm.addExtension(ExtensionDefinitions.EXT_FMM_DERIVED, new CanonicalType(parentCanonical));
          }
        }
        if (addExtension) {
          fmmChanged = true;
          IntegerType newFmm = parentFmm.copy();
          Extension e = new Extension(ExtensionDefinitions.EXT_FMM_LEVEL, newFmm);
          newFmm.addExtension(ExtensionDefinitions.EXT_FMM_DERIVED, new CanonicalType(parentCanonical));
          res.addExtension(e);
        }
      }

      boolean statusChanged = false;
      if (parentStatus != null) {
        boolean addExtension = false;
        if (status == null) {
          addExtension = true;

        } else if (status.hasExtension(ExtensionDefinitions.EXT_FMM_DERIVED)) {
          if (StandardsStatus.fromCode(parentStatus.getValue()).canDependOn(StandardsStatus.fromCode(status.getValue()))) {
            res.getExtension().remove(statusExt);
            addExtension = true;

          } else if (status.getValue() == parentStatus.getValue()) {
            if (fmm.getExtensionsByUrl(ExtensionDefinitions.EXT_FMM_DERIVED).size() < FMM_DERIVATION_MAX)
              fmm.addExtension(ExtensionDefinitions.EXT_FMM_DERIVED, new CanonicalType(parentCanonical));

          }
        }
        if (addExtension) {
          statusChanged = true;
          CodeType code = parentStatus.copy();
          Extension e = new Extension(ExtensionDefinitions.EXT_STANDARDS_STATUS, code);
          code.addExtension(ExtensionDefinitions.EXT_FMM_DERIVED, new CanonicalType(parentCanonical));
          res.addExtension(e);
          if (code.getCode().equals("normative") && !Utilities.noString(parentNormVersion)) {
            res.addExtension(new Extension(ExtensionDefinitions.EXT_NORMATIVE_VERSION, new CodeType(parentNormVersion)));
            statusNormVersion = parentNormVersion;
          }
        } else {
          parentNormVersion = null;
        }
      }

      // If we've changed things, need to propagate to children
      if (fmmChanged || statusChanged) {
        for (Extension e : getDescendantExtensions(res, "http://hl7.org/fhir/StructureDefinition/cqf-library")) {
          updateResourceStatus((CanonicalType)e.getValue(), fmm, status, statusNormVersion, res.getUrl());
        }
        switch (res.getResourceType()) {
          case ActivityDefinition:
            ActivityDefinition ad = (ActivityDefinition)res;
            for (CanonicalType canonical : ad.getLibrary()) {
              updateResourceStatus(canonical, fmm, status, statusNormVersion, res.getUrl());
            }
            if (ad.hasProfile())
              updateResourceStatus(ad.getProfileElement(), fmm, status, statusNormVersion, res.getUrl());
            for (CanonicalType ref : ad.getObservationRequirement()) {
              updateResourceStatus(ref, fmm, status, statusNormVersion, res.getUrl());
            }
            for (CanonicalType ref : ad.getObservationResultRequirement()) {
              updateResourceStatus(ref, fmm, status, statusNormVersion, res.getUrl());
            }
            if (ad.hasTransform())
              updateResourceStatus(ad.getTransformElement(), fmm, status, statusNormVersion, res.getUrl());
            break;

          case CapabilityStatement:
            CapabilityStatement cs = (CapabilityStatement)res;
            for (CapabilityStatement.CapabilityStatementRestComponent rest: cs.getRest()) {
              for (CapabilityStatement.CapabilityStatementRestResourceComponent resource: rest.getResource()) {
                if (resource.hasProfile())
                  updateResourceStatus(resource.getProfileElement(), fmm, status, statusNormVersion, res.getUrl());
                for (CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent sp: resource.getSearchParam()) {
                  if (sp.hasDefinition())
                    updateResourceStatus(sp.getDefinitionElement(), fmm, status, statusNormVersion, res.getUrl());
                }
                for (CapabilityStatement.CapabilityStatementRestResourceOperationComponent op: resource.getOperation()) {
                  if (op.hasDefinition())
                    updateResourceStatus(op.getDefinitionElement(), fmm, status, statusNormVersion, res.getUrl());
                }
              }
              for (CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent sp: rest.getSearchParam()) {
                if (sp.hasDefinition())
                  updateResourceStatus(sp.getDefinitionElement(), fmm, status, statusNormVersion, res.getUrl());
              }
              for (CapabilityStatement.CapabilityStatementRestResourceOperationComponent op: rest.getOperation()) {
                if (op.hasDefinition())
                  updateResourceStatus(op.getDefinitionElement(), fmm, status, statusNormVersion, res.getUrl());
              }
              for (CapabilityStatement.CapabilityStatementMessagingComponent messaging: cs.getMessaging()) {
                for (CapabilityStatement.CapabilityStatementMessagingSupportedMessageComponent msg: messaging.getSupportedMessage()) {
                  if (msg.hasDefinition())
                    updateResourceStatus(msg.getDefinitionElement(), fmm, status, statusNormVersion, res.getUrl());
                }
              }
              for (CapabilityStatement.CapabilityStatementDocumentComponent doc: cs.getDocument()) {
                updateResourceStatus(doc.getProfileElement(), fmm, status, statusNormVersion, res.getUrl());
              }
            }
            break;

          case ConceptMap:
            ConceptMap cm = (ConceptMap)res;
            for (ConceptMap.ConceptMapGroupComponent group: cm.getGroup()) {
              if (group.hasUnmapped() && group.getUnmapped().hasValueSet()) {
                updateResourceStatus(group.getUnmapped().getValueSetElement(), fmm, status, statusNormVersion, res.getUrl());
              }
            }
            break;

          case GraphDefinition:
            GraphDefinition gd = (GraphDefinition)res;
            //            if (gd.hasProfile())
            //              updateResourceStatus(gd.getProfileElement(), fmm, status, statusNormVersion, res.getUrl());
            //            for (GraphDefinitionLinkComponent link: gd.getLink()) {
            //              for (GraphDefinitionLinkTargetComponent target: link.getTarget()) {
            //                if (gd.hasProfile())
            //                  updateResourceStatus(target.getProfileElement(), fmm, status, statusNormVersion, res.getUrl());
            //              }
            //            }
            break;

          case ImplementationGuide:
            ImplementationGuide ig = (ImplementationGuide)res;
            for (ImplementationGuide.ImplementationGuideGlobalComponent global: ig.getGlobal()) {
              updateResourceStatus((CanonicalType)global.getProfileElement(), fmm, status, statusNormVersion, res.getUrl());
            }
            if (ig.hasDefinition()) {
              for (ImplementationGuide.ImplementationGuideDefinitionResourceComponent resource: ig.getDefinition().getResource()) {
                updateResourceStatus(resource.getReference(), fmm, status, statusNormVersion, res.getUrl());
              }
            }
            break;

          case Measure:
            Measure m = (Measure)res;
            for (CanonicalType library: m.getLibrary()) {
              updateResourceStatus(library, fmm, status, statusNormVersion, res.getUrl());
            }
            break;

          case MessageDefinition:
            MessageDefinition md = (MessageDefinition)res;
            if (md.hasBase())
              updateResourceStatus(md.getBaseElement(), fmm, status, statusNormVersion, res.getUrl());
            for (CanonicalType parent: md.getParent()) {
              updateResourceStatus(parent, fmm, status, statusNormVersion, res.getUrl());
            }
            for (MessageDefinition.MessageDefinitionFocusComponent focus : md.getFocus()) {
              if (focus.hasProfile())
                updateResourceStatus(focus.getProfileElement(), fmm, status, statusNormVersion, res.getUrl());
            }
            for (MessageDefinition.MessageDefinitionAllowedResponseComponent response : md.getAllowedResponse()) {
              if (response.hasMessage())
                updateResourceStatus(response.getMessageElement(), fmm, status, statusNormVersion, res.getUrl());
            }
            updateResourceStatus(md.getGraph(), fmm, status, statusNormVersion, res.getUrl());
            break;

          case OperationDefinition:
            OperationDefinition od = (OperationDefinition)res;
            if (od.hasBase())
              updateResourceStatus(od.getBaseElement(), fmm, status, statusNormVersion, res.getUrl());
            if (od.hasInputProfile())
              updateResourceStatus(od.getInputProfileElement(), fmm, status, statusNormVersion, res.getUrl());
            if (od.hasOutputProfile())
              updateResourceStatus(od.getOutputProfileElement(), fmm, status, statusNormVersion, res.getUrl());
            for (OperationDefinition.OperationDefinitionParameterComponent param : od.getParameter()) {
              for (CanonicalType profile: param.getTargetProfile()) {
                updateResourceStatus(profile, fmm, status, statusNormVersion, res.getUrl());
              }
              if (param.hasBinding() && param.getBinding().hasValueSet()) {
                updateResourceStatus(param.getBinding().getValueSetElement(), fmm, status, statusNormVersion, res.getUrl());
              }
            }
            break;

          case PlanDefinition:
            PlanDefinition pd = (PlanDefinition)res;
            for (CanonicalType library: pd.getLibrary()) {
              updateResourceStatus(library, fmm, status, statusNormVersion, res.getUrl());
            }
            for (PlanDefinition.PlanDefinitionActionComponent action: pd.getAction()) {
              if (action.hasDefinitionCanonicalType())
                updateResourceStatus(action.getDefinitionCanonicalType(), fmm, status, statusNormVersion, res.getUrl());
            }
            break;

          case Questionnaire:
            Questionnaire q = (Questionnaire)res;
            for (CanonicalType derived: q.getDerivedFrom()) {
              updateResourceStatus(derived, fmm, status, statusNormVersion, res.getUrl());
            }
            for (Questionnaire.QuestionnaireItemComponent item: q.getItem()) {
              if (item.hasAnswerValueSet())
                updateResourceStatus(item.getAnswerValueSetElement(), fmm, status, statusNormVersion, res.getUrl());
              for (Extension ext: item.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/questionnaire-referenceProfile")) {
                updateResourceStatus(ext.getValueCanonicalType(), fmm, status, statusNormVersion, res.getUrl());
              }
              for (Extension ext: item.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/questionnaire-unitValueSet")) {
                updateResourceStatus(ext.getValueCanonicalType(), fmm, status, statusNormVersion, res.getUrl());
              }
            }
            break;

          case SearchParameter:
            SearchParameter sp = (SearchParameter)res;
            if (sp.hasDerivedFrom())
              updateResourceStatus(sp.getDerivedFromElement(), fmm, status, statusNormVersion, res.getUrl());
            for (SearchParameter.SearchParameterComponentComponent comp: sp.getComponent()) {
              if (comp.hasDefinition())
                updateResourceStatus(comp.getDefinitionElement(), fmm, status, statusNormVersion, res.getUrl());
            }
            break;

          case StructureDefinition:
            StructureDefinition sd = (StructureDefinition)res;
            if (sd.hasBaseDefinition())
              updateResourceStatus(sd.getBaseDefinitionElement(), fmm, status, statusNormVersion, res.getUrl());
            for (ElementDefinition e: sd.getDifferential().getElement()) {
              if (e.hasBinding() && e.getBinding().hasValueSet())
                updateResourceStatus(e.getBinding().getValueSetElement(), fmm, status, statusNormVersion, res.getUrl());
            }
            break;

          case StructureMap:
            StructureMap sm = (StructureMap)res;
            for (CanonicalType imp: sm.getImport()) {
              updateResourceStatus(imp, fmm, status, statusNormVersion, res.getUrl());
            }
            break;

          /* TODO: Add this once SubscriptionTopic is a canonical
          case SubscriptionTopic:
            SubscriptionTopic st = (SubscriptionTopic)res;
            for (CanonicalType canonical: st.getDerivedFrom()) {
              updateResourceStatus(canonical, fmm, status, statusNormVersion, res.getUrl());
            }
            for (CanonicalType canonical: st.getDerivedFrom()) {
              for (SubscriptionTopicResourceTriggerComponent trigger: st.getResourceTrigger()) {
                for (SubscriptionTopicResourceTriggerCanFilterByComponent filter: trigger.getCanFilterBy()) {
                  updateResourceStatus(filter.getSearchParamName(), fmm, status, statusNormVersion, res.getUrl());
                }
              }
            }
            break;*/

          case ValueSet:
            ValueSet vs = (ValueSet)res;
            for (Extension ext: vs.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/valueset-map")) {
              updateResourceStatus(ext.getValueCanonicalType(), fmm, status, statusNormVersion, res.getUrl());
            }
            for (Extension ext: vs.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/valueset-supplement")) {
              updateResourceStatus(ext.getValueCanonicalType(), fmm, status, statusNormVersion, res.getUrl());
            }
            if (vs.hasCompose()) {
              for (ValueSet.ConceptSetComponent compose: vs.getCompose().getInclude()) {
                if (compose.hasSystem())
                  updateResourceStatus(new CanonicalType(compose.getSystem()), fmm, status, statusNormVersion, res.getUrl());
                for (CanonicalType valueSet: compose.getValueSet()) {
                  updateResourceStatus(valueSet, fmm, status, statusNormVersion, res.getUrl());
                }
              }
              for (ValueSet.ConceptSetComponent compose: vs.getCompose().getExclude()) {
                if (compose.hasSystem())
                  updateResourceStatus(new CanonicalType(compose.getSystem()), fmm, status, statusNormVersion, res.getUrl());
                for (CanonicalType valueSet: compose.getValueSet()) {
                  updateResourceStatus(valueSet, fmm, status, statusNormVersion, res.getUrl());
                }
              }
            }
            break;

          // The following types don't actually have anything to cascade to - at least not yet
          case CodeSystem:
          case EventDefinition:
          case Library:
          case NamingSystem:
          case TerminologyCapabilities:
          default:

        }
      }
    }
  }


  public void generateNarratives(boolean isRegen) throws Exception {
    TimeTracker.Session tts = pf.tt.start("narrative generation");
    logDebugMessage(LogCategory.PROGRESS, isRegen ? "regen narratives" : "gen narratives");
    for (FetchedFile f : pf.changeList) {
      f.start("generateNarratives");
      try {
        for (FetchedResource r : f.getResources()) {
          if (!isRegen || r.isRegenAfterValidation()) {
            if (r.getExampleUri()==null || this.pf.genExampleNarratives) {
              if (!passesNarrativeFilter(r)) {
                this.pf.noNarrativeResources.add(r);
                logDebugMessage(LogCategory.PROGRESS, "narrative for "+f.getName()+" : "+r.getId()+" suppressed");
                if (r.getResource() != null && r.getResource() instanceof DomainResource) {
                  ((DomainResource) r.getResource()).setText(null);
                }
                r.getElement().removeChild("text");
              } else {
                List<Locale> langs = translationLocales();
                logDebugMessage(LogCategory.PROGRESS, "narrative for "+f.getName()+" : "+r.getId());
                if (r.getResource() != null && r.getResource() instanceof DomainResource && isConvertableResource(r.getResource().fhirType())) {
                  boolean regen = false;
                  boolean first = true;
                  for (Locale lang : langs) {
                    RenderingContext lrc = this.pf.rc.copy(false).setDefinitionsTarget(this.pf.igpkp.getDefinitionsName(r));
                    lrc.setLocale(lang);
                    lrc.setRules(RenderingContext.GenerationRules.VALID_RESOURCE);
                    lrc.setDefinitionsTarget(this.pf.igpkp.getDefinitionsName(r));
                    lrc.setSecondaryLang(!first);
                    if (!first) {
                      lrc.setUniqueLocalPrefix(lang.toLanguageTag());
                    }
                    first = false;
                    if (r.getResource() instanceof DomainResource && (langs.size() > 1 || !(((DomainResource) r.getResource()).hasText() && ((DomainResource) r.getResource()).getText().hasDiv()))) {
                      regen = true;
                      ResourceRenderer rr = RendererFactory.factory(r.getResource(), lrc);
                      if (rr.renderingUsesValidation()) {
                        r.setRegenAfterValidation(true);
                        this.pf.needsRegen = true;
                      }
                      rr.setMultiLangMode(langs.size() > 1).renderResource(ResourceWrapper.forResource(lrc, r.getResource()));
                    } else if (r.getResource() instanceof Bundle) {
                      regen = true;
                      new BundleRenderer(lrc).setMultiLangMode(langs.size() > 1).renderResource(ResourceWrapper.forResource(lrc, r.getResource()));
                    } else if (r.getResource() instanceof Parameters) {
                      regen = true;
                      Parameters p = (Parameters) r.getResource();
                      new ParametersRenderer(lrc).setMultiLangMode(langs.size() > 1).renderResource(ResourceWrapper.forResource(lrc, p));
                    } else if (r.getResource() instanceof DomainResource) {
                      checkExistingNarrative(f, r, ((DomainResource) r.getResource()).getText().getDiv());
                    }
                  }
                  if (regen) {
                    r.setGeneratedNarrative(true);
                    org.hl7.fhir.r5.elementmodel.Element e = convertToElement(r, r.getResource());
                    e.copyUserData(r.getElement());
                    r.setElement(e);
                  }
                } else {
                  boolean first = true;
                  for (Locale lang : langs) {
                    RenderingContext lrc = this.pf.rc.copy(false).setParser(getTypeLoader(f,r));
                    lrc.clearAnchors();
                    lrc.setLocale(lang);
                    lrc.setRules(RenderingContext.GenerationRules.VALID_RESOURCE);
                    lrc.setSecondaryLang(!first);
                    if (!first) {
                      lrc.setUniqueLocalPrefix(lang.toLanguageTag());
                    }
                    first = false;
                    if (isDomainResource(r) && (isRegen || langs.size() > 1 || !hasNarrative(r.getElement()))) {
                      ResourceWrapper rw = ResourceWrapper.forResource(lrc, r.getElement());
                      ResourceRenderer rr = RendererFactory.factory(rw, lrc);
                      if (rr.renderingUsesValidation()) {
                        r.setRegenAfterValidation(true);
                        this.pf.needsRegen = true;
                      }
                      rr.setMultiLangMode(langs.size() > 1).renderResource(rw);
                      this.pf.otherFilesRun.addAll(lrc.getFiles());
                    } else if (r.fhirType().equals("Bundle")) {
                      lrc.setAddName(true);
                      for (org.hl7.fhir.r5.elementmodel.Element e : r.getElement().getChildrenByName("entry")) {
                        Element res = e.getNamedChild("resource");
                        if (res!=null && isDomainResource(res.getProperty().getStructure())) {
                          ResourceWrapper rw = ResourceWrapper.forResource(lrc, res);
                          ResourceRenderer rr = RendererFactory.factory(rw, lrc);
                          if (rr.renderingUsesValidation()) {
                            r.setRegenAfterValidation(true);
                            this.pf.needsRegen = true;
                          }
                          if (hasNarrative(res)) {
                            rr.checkNarrative(rw);
                          } else {
                            rr.setMultiLangMode(langs.size() > 1).renderResource(rw);
                          }
                        }
                      }
                    } else if (isDomainResource(r) && hasNarrative(r.getElement())) {
                      checkExistingNarrative(f, r, r.getElement().getNamedChild("text").getNamedChild("div").getXhtml());
                    }
                  }
                }
              }
            } else {
              logDebugMessage(LogCategory.PROGRESS, "skipped narrative for "+f.getName()+" : "+r.getId());
            }
          }
        }
      } finally {
        f.finish("generateNarratives");
      }
    }
    tts.end();
  }

  private void loadLists() throws Exception {
    for (FetchedFile f : pf.fileList) {
      f.start("loadLists");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.fhirType().equals("List")) {
            ListResource l = (ListResource) convertFromElement(r.getElement());
            r.setResource(l);
          }
        }
      } finally {
        f.finish("loadLists");
      }
    }
  }


  private void checkExistingNarrative(FetchedFile f, FetchedResource r, XhtmlNode xhtml) {
    if (xhtml != null) {
      boolean hasGenNarrative = scanForGeneratedNarrative(xhtml);
      if (hasGenNarrative) {
        f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.NOTFOUND, r.fhirType()+".text.div", "Resource has provided narrative, but the narrative indicates that it is generated - remove the narrative or fix it up", ValidationMessage.IssueSeverity.ERROR));
        r.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.NOTFOUND, r.fhirType()+".text.div", "Resource has provided narrative, but the narrative indicates that it is generated - remove the narrative or fix it up", ValidationMessage.IssueSeverity.ERROR));
      }
    }
  }

  private boolean scanForGeneratedNarrative(XhtmlNode x) {
    if (x.getContent() != null && x.getContent().contains("Generated Narrative")) {
      return true;
    }
    for (XhtmlNode c : x.getChildNodes()) {
      if (scanForGeneratedNarrative(c)) {
        return true;
      }
    }
    return false;
  }


  private boolean hasNarrative(Element element) {
    return element.hasChild("text") && element.getNamedChild("text").hasChild("div");
  }

  private boolean isDomainResource(StructureDefinition structure) {
    StructureDefinition sd = structure;
    while (sd != null) {
      if ("DomainResource".equals(sd.getType())) {
        return true;
      }
      sd = pf.context.fetchResource(StructureDefinition.class, sd.getBaseDefinition());
    }
    return false;
  }


  private boolean isDomainResource(FetchedResource r) {
    StructureDefinition sd = r.getElement().getProperty().getStructure();
    while (sd != null) {
      if ("DomainResource".equals(sd.getType())) {
        return true;
      }
      sd = pf.context.fetchResource(StructureDefinition.class, sd.getBaseDefinition());
    }
    return false;
  }

  private boolean passesNarrativeFilter(FetchedResource r) {
    for (String s : settings.getNoNarratives()) {
      String[] p = s.split("\\/");
      if (p.length == 2) {
        if (("*".equals(p[0]) || r.fhirType().equals(p[0])) &&
                ("*".equals(p[1]) || r.getId().equals(p[1]))) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean passesValidationFilter(FetchedResource r) {
    for (String s : settings.getNoValidate()) {
      String[] p = s.split("\\/");
      if (p.length == 2) {
        if (("*".equals(p[0]) || r.fhirType().equals(p[0])) &&
                ("*".equals(p[1]) || r.getId().equals(p[1]))) {
          return false;
        }
      }
    }
    return true;
  }


  private void checkConformanceResources() throws IOException {
    log("Check profiles & code systems");
    for (FetchedFile f : pf.changeList) {
      f.start("checkConformanceResources");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.fhirType().equals("StructureDefinition")) {
            logDebugMessage(LogCategory.PROGRESS, "process profile: "+r.getId());
            StructureDefinition sd = (StructureDefinition) r.getResource();
            if (sd == null) {
              f.getErrors().add(new ValidationMessage(ValidationMessage.Source.ProfileValidator, ValidationMessage.IssueType.INVALID, "StructureDefinition", "Unable to validate - Profile not loaded", ValidationMessage.IssueSeverity.ERROR));
            } else {
              f.getErrors().addAll(this.pf.pvalidator.validate(sd, false));
              checkJurisdiction(f, (CanonicalResource) r.getResource(), ValidationMessage.IssueSeverity.ERROR, "must");
            }
          } else if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
            checkJurisdiction(f, (CanonicalResource) r.getResource(), ValidationMessage.IssueSeverity.WARNING, "should");
          }
          if (r.fhirType().equals("CodeSystem")) {
            logDebugMessage(LogCategory.PROGRESS, "process CodeSystem: "+r.getId());
            CodeSystem cs = (CodeSystem) r.getResource();
            if (cs != null) {
              f.getErrors().addAll(this.pf.csvalidator.validate(cs, false));
            }
          }
        }
      } finally {
        f.finish("checkConformanceResources");
      }
    }
    TimeTracker.Session tts = pf.tt.start("realm-rules");
    if (!pf.realmRules.isExempt(pf.publishedIg.getPackageId())) {
      log("Check realm rules");
      pf.realmRules.startChecks(pf.publishedIg);
      for (FetchedFile f : pf.changeList) {
        f.start("checkConformanceResources2");
        try {
          for (FetchedResource r : f.getResources()) {
            if (r.fhirType().equals("StructureDefinition")) {
              StructureDefinition sd = (StructureDefinition) r.getResource();
              this.pf.realmRules.checkSD(f, sd);
            } else if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
              this.pf.realmRules.checkCR(f, (CanonicalResource) r.getResource());
            }
          }
        } finally {
          f.finish("checkConformanceResources2");
        }
      }
      pf.realmRules.finishChecks();
    }
    tts.end();
    log("Previous Version Comparison");
    tts = pf.tt.start("previous-version");
    pf.previousVersionComparator.startChecks(pf.publishedIg);
    if (pf.ipaComparator != null) {
      pf.ipaComparator.startChecks(pf.publishedIg);
    }
    if (pf.ipsComparator != null) {
      pf.ipsComparator.startChecks(pf.publishedIg);
    }
    for (FetchedFile f : pf.changeList) {
      f.start("checkConformanceResources3");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.getResource() != null && r.getResource() instanceof CanonicalResource) {
            this.pf.previousVersionComparator.check((CanonicalResource) r.getResource());
            if (this.pf.ipaComparator != null) {
              this.pf.ipaComparator.check((CanonicalResource) r.getResource());
            }
            if (this.pf.ipsComparator != null) {
              this.pf.ipsComparator.check((CanonicalResource) r.getResource());
            }
          }

        }
      } finally {
        f.finish("checkConformanceResources3");
      }
    }
    pf.previousVersionComparator.finishChecks();
    if (pf.ipaComparator != null) {
      pf.ipaComparator.finishChecks();
    }
    if (pf.ipsComparator != null) {
      pf.ipsComparator.finishChecks();
    }
    tts.end();
  }

  private void checkJurisdiction(FetchedFile f, CanonicalResource resource, ValidationMessage.IssueSeverity error, String verb) {
    if (this.pf.expectedJurisdiction != null) {
      boolean ok = false;
      CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
      for (CodeableConcept cc : resource.getJurisdiction()) {
        ok = ok || cc.hasCoding(this.pf.expectedJurisdiction);
        b.append(cc.toString());
      }
      if (!ok) {
        f.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.BUSINESSRULE, resource.fhirType()+".jurisdiction",
                "The resource "+verb+" declare its jurisdiction to match the package id ("+ this.pf.npmName +", jurisdiction = "+ this.pf.expectedJurisdiction.toString()+
                        (Utilities.noString(b.toString()) ? "" : " instead of or as well as "+b.toString())+
                        ") (for Sushi users: in sushi-config.yaml, 'jurisdiction: "+toFSH(this.pf.expectedJurisdiction)+"')",
                error).setMessageId(PublisherMessageIds.RESOURCE_JURISDICTION_MISMATCH));
      }
    }
  }

  private String toFSH(Coding c) {
    StringBuilder b = new StringBuilder();
    b.append(c.getSystem());
    b.append("#");
    b.append(c.getCode());
    b.append(" \"");
    b.append(c.getDisplay());
    b.append("\"");
    return b.toString();
  }


  private void generateAdditionalExamples() throws Exception {
    if (pf.genExamples) {
      ProfileUtilities utils = new ProfileUtilities(pf.context, null, null);
      for (FetchedFile f : pf.changeList) {
        f.start("generateAdditionalExamples");
        try {
          List<StructureDefinition> list = new ArrayList<StructureDefinition>();
          for (FetchedResource r : f.getResources()) {
            if (r.getResource() instanceof StructureDefinition) {
              list.add((StructureDefinition) r.getResource());
            }
          }
          for (StructureDefinition sd : list) {
            for (Element e : utils.generateExamples(sd, false)) {
              FetchedResource nr = new FetchedResource(f.getName()+" (additional example)");
              nr.setElement(e);
              nr.setId(e.getChildValue("id"));
              nr.setTitle("Generated Example");
              nr.getStatedProfiles().add(sd.getUrl());
              f.getResources().add(nr);
              this.pf.igpkp.findConfiguration(f, nr);
            }
          }
        } finally {
          f.finish("generateAdditionalExamples");
        }
      }
    }
  }


  private void executeTransforms() throws FHIRException, Exception {
    if (pf.doTransforms) {
      MappingServices services = new MappingServices(pf.context, pf.igpkp.getCanonical());
      StructureMapUtilities utils = new StructureMapUtilities(pf.context, services, pf.igpkp);

      // ok, our first task is to generate the profiles
      for (FetchedFile f : pf.changeList) {
        f.start("executeTransforms");
        try {
          List<StructureMap> worklist = new ArrayList<StructureMap>();
          for (FetchedResource r : f.getResources()) {
            if (r.getResource() != null && r.getResource() instanceof StructureDefinition) {
              List<StructureMap> transforms = this.pf.context.findTransformsforSource(((StructureDefinition) r.getResource()).getUrl());
              worklist.addAll(transforms);
            }
          }


          ProfileUtilities putils = new ProfileUtilities(this.pf.context, null, this.pf.igpkp);
          putils.setXver(this.pf.context.getXVer());
          putils.setForPublication(true);
          putils.setMasterSourceFileNames(this.pf.specMaps.get(0).getTargets());
          putils.setLocalFileNames(pageTargets());
          if (VersionUtilities.isR4Plus(this.pf.version)) {
            putils.setNewSlicingProcessing(true);
          }


          for (StructureMap map : worklist) {
            StructureMapAnalysis analysis = utils.analyse(null, map);
            map.setUserData(UserDataNames.pub_analysis, analysis);
            for (StructureDefinition sd : analysis.getProfiles()) {
              FetchedResource nr = new FetchedResource(f.getName()+" (ex transform)");
              nr.setElement(convertToElement(nr, sd));
              nr.setId(sd.getId());
              nr.setResource(sd);
              nr.setTitle("Generated Profile (by Transform)");
              f.getResources().add(nr);
              this.pf.igpkp.findConfiguration(f, nr);
              sd.setWebPath(this.pf.igpkp.getLinkFor(nr, true));
              generateSnapshot(f, nr, sd, true, putils);
            }
          }
        } finally {
          f.finish("executeTransforms");
        }
      }

      for (FetchedFile f : pf.changeList) {
        f.start("executeTransforms2");
        try {
          Map<FetchedResource, List<StructureMap>> worklist = new HashMap<FetchedResource, List<StructureMap>>();
          for (FetchedResource r : f.getResources()) {
            List<StructureMap> transforms = this.pf.context.findTransformsforSource(r.getElement().getProperty().getStructure().getUrl());
            if (transforms.size() > 0) {
              worklist.put(r, transforms);
            }
          }
          for (Map.Entry<FetchedResource, List<StructureMap>> t : worklist.entrySet()) {
            int i = 0;
            for (StructureMap map : t.getValue()) {
              boolean ok = true;
              String tgturl = null;
              for (StructureMap.StructureMapStructureComponent st : map.getStructure()) {
                if (st.getMode() == StructureMap.StructureMapModelMode.TARGET) {
                  if (tgturl == null)
                    tgturl = st.getUrl();
                  else
                    ok = false;
                }
              }
              if (ok) {
                Resource target = new Bundle().setType(Bundle.BundleType.COLLECTION);
                if (tgturl != null) {
                  StructureDefinition tsd = this.pf.context.fetchResource(StructureDefinition.class, tgturl);
                  if (tsd == null)
                    throw new Exception("Unable to find definition "+tgturl);
                  target = ResourceFactory.createResource(tsd.getType());
                }
                if (t.getValue().size() > 1)
                  target.setId(t.getKey().getId()+"-map-"+Integer.toString(i));
                else
                  target.setId(t.getKey().getId()+"-map");
                i++;
                services.reset();
                utils.transform(target, t.getKey().getElement(), map, target);
                FetchedResource nr = new FetchedResource(f.getName()+" (ex transform 2)");
                nr.setElement(convertToElement(nr, target));
                nr.setId(target.getId());
                nr.setResource(target);
                nr.setTitle("Generated Example (by Transform)");
                nr.setValidateAsResource(true);
                f.getResources().add(nr);
                this.pf.igpkp.findConfiguration(f, nr);
              }
            }
          }
        } finally {
          f.finish("executeTransforms2");
        }
      }
    }
  }


  private void scanForUsageStats() {
    logDebugMessage(LogCategory.PROGRESS, "scanForUsageStats");
    for (FetchedFile f : pf.fileList) {
      f.start("scanForUsageStats");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.fhirType().equals("StructureDefinition")) {
            this.pf.extensionTracker.scan((StructureDefinition) r.getResource());
          }
          this.pf.extensionTracker.scan(r.getElement(), f.getName());
        }
      } finally {
        f.finish("scanForUsageStats");
      }
    }
  }

  private void validate(FetchedFile file, FetchedResource r) throws Exception {
    if (!passesValidationFilter(r)) {
      pf.noValidateResources.add(r);
      return;
    }
    if ("ImplementationGuide".equals(r.fhirType()) && !pf.unknownParams.isEmpty()) {
      file.getErrors().add(new ValidationMessage(ValidationMessage.Source.Publisher, ValidationMessage.IssueType.INVALID, file.getName(), "Unknown Parameters: "+ pf.unknownParams.toString(), ValidationMessage.IssueSeverity.WARNING));
    }

    TimeTracker.Session tts = pf.tt.start("validation");
    List<ValidationMessage> errs = new ArrayList<ValidationMessage>();
    r.getElement().setUserData(UserDataNames.pub_context_file, file);
    r.getElement().setUserData(UserDataNames.pub_context_resource, r);
    pf.validator.setExample(r.isExample());
    if (r.isValidateAsResource()) {
      Resource res = r.getResource();
      if (res instanceof Bundle) {
        validate(file, r, errs);

        for (Bundle.BundleEntryComponent be : ((Bundle) res).getEntry()) {
          Resource ber = be.getResource();
          if (ber.hasUserData(UserDataNames.map_profile)) {
            validate(file, r, errs, ber);
          }
        }
      } else if (res.hasUserData(UserDataNames.map_profile)) {
        validate(file, r, errs, res);
      }
    } else if (r.getResource() != null && r.getResource() instanceof Binary && file.getLogical() != null && pf.context.hasResource(StructureDefinition.class, file.getLogical())) {
      StructureDefinition sd = pf.context.fetchResource(StructureDefinition.class, file.getLogical());
      Binary bin = (Binary) r.getResource();
      validate(file, r, errs, bin, sd);
    } else if (r.getResource() != null && r.getResource() instanceof Binary && r.getExampleUri() != null) {
      Binary bin = (Binary) r.getResource();
      validate(file, r, errs, bin);
    } else {
      pf.validator.setNoCheckAggregation(r.isExample() && ExtensionUtilities.readBoolExtension(r.getResEntry(), "http://hl7.org/fhir/tools/StructureDefinition/igpublisher-no-check-aggregation"));
      List<StructureDefinition> profiles = new ArrayList<>();

      if (r.getElement().hasUserData(UserDataNames.map_profile)) {
        addProfile(profiles, r.getElement().getUserString(UserDataNames.map_profile), null);
      }
      for (String s : r.getProfiles(false)) {
        addProfile(profiles, s, r.fhirType());
      }
      validate(file, r, errs, profiles);
    }
    processValidationOutcomes(file, r, errs);
    r.setValidated(true);
    if (r.getConfig() == null) {
      pf.igpkp.findConfiguration(file, r);
    }
    tts.end();
  }

  private void processValidationOutcomes(FetchedFile file, FetchedResource r, List<ValidationMessage> errs) {
    for (ValidationMessage vm : errs) {
      String loc = r.fhirType()+"/"+r.getId();
      if (!vm.getLocation().startsWith(loc)) {
        vm.setLocation(loc+": "+vm.getLocation());
      }
      if (!alreadyExists(file.getErrors(), vm)) {
        file.getErrors().add(vm);
      }
      r.getErrors().add(vm);
    }
  }


  private boolean alreadyExists(List<ValidationMessage> list, ValidationMessage vm) {
    for (ValidationMessage t : list) {
      if (t.matches(vm)) {
        return true;
      }
    }
    return false;
  }

  private void addProfile(List<StructureDefinition> profiles, String ref, String rt) {
    if (!Utilities.isAbsoluteUrl(ref)) {
      ref = Utilities.pathURL(pf.igpkp.getCanonical(), ref);
    }
    for (StructureDefinition sd : profiles) {
      if (ref.equals(sd.getUrl())) {
        return;
      }
    }
    StructureDefinition sd = pf.context.fetchResource(StructureDefinition.class, ref);
    if (sd != null && (rt == null || sd.getType().equals(rt))) {
      profiles.add(sd);
    }
  }


  private boolean isClosing(StructureDefinition sd) {
    StandardsStatus ss = ExtensionUtilities.getStandardsStatus(sd);
    if (ss == StandardsStatus.DEPRECATED || ss == StandardsStatus.WITHDRAWN) {
      return true;
    }
    if (sd.getStatus() == Enumerations.PublicationStatus.RETIRED) {
      return true;
    }
    return false;
  }


  private int countUsages(String fixedUrl) {
    int res = 0;
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        res = res + countExtensionUsage(r.getElement(), fixedUrl);
      }
    }
    return res;
  }



  private int countExtensionUsage(Element element, String url) {
    int res = 0;
    if (element.fhirType().equals("Extension") && url.equals(element.getChildValue("url"))) {
      res = res + 1;
    }
    for (Element child : element.getChildren()) {
      res = res + countExtensionUsage(child, url);
    }
    return res;
  }



  private String getFixedUrl(StructureDefinition sd) {
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      if (ed.getPath().equals("Extension.url") && ed.hasFixed()) {
        return ed.getFixed().primitiveValue();
      }
    }
    return sd.getUrl();
  }



  private int countStatedExamples(String url, String vurl) {
    int res = 0;
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        for (String p : r.getStatedProfiles()) {
          if (url.equals(p) || vurl.equals(p)) {
            res++;
          }
        }
      }
    }
    return res;
  }

  private int countFoundExamples(String url, String vurl) {
    int res = 0;
    for (FetchedFile f : pf.fileList) {
      for (FetchedResource r : f.getResources()) {
        for (String p : r.getFoundProfiles()) {
          if (url.equals(p) || vurl.equals(p)) {
            res++;
          }
        }
      }
    }
    return res;
  }

  public List<Extension> getDescendantExtensions(Base e, String url) {
    List<Extension> extensions = new ArrayList<Extension>();
    for (Property childName: e.children()) {
      String name = childName.getName().endsWith("[x]") ? childName.getName().substring(0, childName.getName().length()-3) : childName.getName();
      for (Base b: e.listChildrenByName(name)) {
        if (b instanceof org.hl7.fhir.r5.model.Element) {
          org.hl7.fhir.r5.model.Element ce = (org.hl7.fhir.r5.model.Element)b;
          extensions.addAll(ce.getExtensionsByUrl(url));
          getDescendantExtensions(ce, url);
        }
      }
    }
    return extensions;
  }


  public void checkSignBundles() throws Exception {
    log("Checking for Bundles to sign");
    for (FetchedFile f : pf.changeList) {
      for (FetchedResource r : f.getResources()) {
        if ("Bundle".equals(r.fhirType())) {
          Element sig = r.getElement().getNamedChild("signature");
          if (sig != null && !sig.hasChild("data") && "application/jose".equals(sig.getNamedChildValue("sigFormat"))) {
            this.pf.signer.signBundle(r.getElement(), sig, PublisherSigner.SignatureType.JOSE);
          }
          if (sig != null && !sig.hasChild("data") && "application/pkcs7-signature".equals(sig.getNamedChildValue("sigFormat"))) {
            this.pf.signer.signBundle(r.getElement(), sig, PublisherSigner.SignatureType.DIGSIG);
          }
        }
      }
    }
  }


  public void processProvenanceDetails() throws Exception {
    for (FetchedFile f : pf.changeList) {
      f.start("processProvenanceDetails");
      try {

        for (FetchedResource r : f.getResources()) {
          if (!r.isExample()) {
            if (r.fhirType().equals("Provenance")) {
              logDebugMessage(LogCategory.PROGRESS, "Process Provenance "+f.getName()+" : "+r.getId());
              if (processProvenance(this.pf.igpkp.getLinkFor(r, true), r.getElement(), r.getResource()))
                r.setProvenance(true);
            } else if (r.fhirType().equals("Bundle")) {
              if (processProvenanceEntries(f, r))
                r.setProvenance(true);
            }
          }
        }
      } finally {
        f.finish("processProvenanceDetails");
      }
    }
  }

  public boolean processProvenanceEntries(FetchedFile f, FetchedResource r) throws Exception {
    boolean isHistory = false;
    Bundle b = (Bundle) r.getResource();
    List<Element> entries = r.getElement().getChildrenByName("entry");
    for (int i = 0; i < entries.size(); i++) {
      Element entry = entries.get(i);
      Element res = entry.getNamedChild("resource");
      if (res != null && "Provenance".equals(res.fhirType())) {
        logDebugMessage(LogCategory.PROGRESS, "Process Provenance "+f.getName()+" : "+r.getId()+".entry["+i+"]");
        if (processProvenance(this.pf.igpkp.getLinkFor(r, true), res, b == null ? null : b.getEntry().get(i).getResource()))
          isHistory = true;
      }
    }
    return isHistory;
  }


  public void processTranslationOutputs() throws IOException {

    PublisherTranslator pt = new PublisherTranslator(pf.context, pf.sourceIg.hasLanguage() ? pf.sourceIg.getLanguage() : "en", pf.defaultTranslationLang, pf.translationLangs);
    pt.start(pf.tempLangDir);
    for (FetchedFile f : pf.changeList) {
      f.start("translate");
      try {
        for (FetchedResource r : f.getResources()) {
          pt.translate(f, r);
        }
      } finally {
        f.finish("translate");
      }
    }
    pt.finish();
  }


  private void validate(String type) throws Exception {
    for (FetchedFile f : pf.changeList) {
      f.start("validate");
      try {
        for (FetchedResource r : f.getResources()) {
          if (r.fhirType().equals(type)) {
            logDebugMessage(LogCategory.PROGRESS, "validate res: "+r.fhirType()+"/"+r.getId());
            if (!r.isValidated()) {
              validate(f, r);
            }
            if (SpecialTypeHandler.handlesType(r.fhirType(), this.pf.context.getVersion()) && !VersionUtilities.isR5Plus(this.pf.version)) {
              // we validated the resource as it was supplied, but now we need to
              // switch it for the correct representation in the underlying version
              byte[] cnt = null;
              if (VersionUtilities.isR3Ver(this.pf.version)) {
                org.hl7.fhir.dstu3.model.Resource res = VersionConvertorFactory_30_50.convertResource(r.getResource());
                cnt = new org.hl7.fhir.dstu3.formats.JsonParser().setOutputStyle(org.hl7.fhir.dstu3.formats.IParser.OutputStyle.PRETTY).composeBytes(res);
              } else if (VersionUtilities.isR4Ver(this.pf.version)) {
                org.hl7.fhir.r4.model.Resource res = VersionConvertorFactory_40_50.convertResource(r.getResource());
                cnt = new org.hl7.fhir.r4.formats.JsonParser().setOutputStyle(org.hl7.fhir.r4.formats.IParser.OutputStyle.PRETTY).composeBytes(res);
              } else if (VersionUtilities.isR4BVer(this.pf.version)) {
                org.hl7.fhir.r4b.model.Resource res = VersionConvertorFactory_43_50.convertResource(r.getResource());
                cnt = new org.hl7.fhir.r4b.formats.JsonParser().setOutputStyle(org.hl7.fhir.r4b.formats.IParser.OutputStyle.PRETTY).composeBytes(res);
              } else {
                throw new Error("Cannot use resources of type "+r.fhirType()+" in a IG with version "+ this.pf.version);
              }
              Element e = new org.hl7.fhir.r5.elementmodel.JsonParser(this.pf.context).parseSingle(new ByteArrayInputStream(cnt), null);
              e.copyUserData(r.getElement());
              r.setElement(e);
            }
          }
        }
      } finally {
        f.finish("validate");
      }
    }
  }


  /**
   * Return a list of locales containing the translation languages for the IG, as well as the inferred default language
   * of the IG.
   *
   * @return translation locales
   */
  private List<Locale> translationLocales() {
    List<Locale> res = new ArrayList<>();
    res.add(inferDefaultNarrativeLang());

    for (String translationLang : pf.translationLangs) {
      Locale locale = Locale.forLanguageTag(translationLang);
      if (!res.contains(locale)) {
        res.add(locale);
      }
    }
    return res;
  }

  private boolean isConvertableResource(String t) {
    return Utilities.existsInList(t, "StructureDefinition", "ValueSet", "CodeSystem", "Conformance", "CapabilityStatement", "Questionnaire", "NamingSystem", "SearchParameter",
            "ConceptMap", "OperationOutcome", "CompartmentDefinition", "OperationDefinition", "ImplementationGuide", "ActorDefinition", "Requirements", "StructureMap", "SubscriptionTopic");
  }



}
