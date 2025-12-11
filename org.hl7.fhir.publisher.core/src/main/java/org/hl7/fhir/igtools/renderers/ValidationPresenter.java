package org.hl7.fhir.igtools.renderers;

/*-
 * #%L
 * org.hl7.fhir.publisher.core
 * %%
 * Copyright (C) 2014 - 2019 Health Level 7
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.DependentIGFinder;
import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.RelatedIG;
import org.hl7.fhir.igtools.publisher.SuppressedMessageInformation;
import org.hl7.fhir.igtools.publisher.SuppressedMessageInformation.SuppressedMessage;
import org.hl7.fhir.igtools.publisher.comparators.IpaComparator;
import org.hl7.fhir.igtools.publisher.comparators.IpsComparator;
import org.hl7.fhir.igtools.publisher.comparators.PreviousVersionComparator;
import org.hl7.fhir.igtools.publisher.realm.RealmBusinessRules;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.Constants;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.PackageInformation;
import org.hl7.fhir.r5.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.terminologies.client.TerminologyClientContext;
import org.hl7.fhir.r5.terminologies.client.TerminologyClientContext.TerminologyClientContextUseCount;
import org.hl7.fhir.r5.terminologies.client.TerminologyClientManager;
import org.hl7.fhir.r5.terminologies.client.TerminologyClientManager.InternalLogEvent;
import org.hl7.fhir.r5.utils.OperationOutcomeUtilities;
import org.hl7.fhir.utilities.*;
import org.hl7.fhir.utilities.i18n.I18nConstants;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.stringtemplate.v4.ST;

public class ValidationPresenter implements Comparator<FetchedFile> {

  private int vmid = 0;

  public enum LanguagePopulationPolicy {
    NONE, IG, OTHERS, ALL;
    
    public static LanguagePopulationPolicy fromCode(String code) {
      if (code != null) {
        code = code.toLowerCase();
        switch (code) {
        case "all-ig": return OTHERS;
        case "ig-realm": return IG;
        case "all-realm": return ALL;
        }
      }
      return null;
    }
  }

  public static class IGLanguageInformation {
    private LanguagePopulationPolicy policy;
    private String igResourceLanguage;
    private int total;
    private int withLang;
    private List<String> igLangs;
    
    public void seeResource(boolean hasLang) {
       total++;
       if (hasLang) {
         withLang++;
       }
    }

    public LanguagePopulationPolicy getPolicy() {
      return policy;
    }

    public void setPolicy(LanguagePopulationPolicy policy) {
      this.policy = policy;
    }

    public String getIgResourceLanguage() {
      return igResourceLanguage;
    }

    public void setIgResourceLanguage(String igResourceLanguage) {
      this.igResourceLanguage = igResourceLanguage;
    }

    public List<String> getIgLangs() {
      return igLangs;
    }

    public void setIgLangs(List<String> igLangs) {
      this.igLangs = igLangs;
    }

    public String generate() {
      if (policy == LanguagePopulationPolicy.NONE && igResourceLanguage == null && igLangs.isEmpty() && withLang == 0) {
        return "This IG has no language information";
      } else {
        CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder(". ");
        b.append("Stated Languages: "+(igLangs.isEmpty() ? "n/a" : CommaSeparatedStringBuilder.join(",", igLangs)));
        b.append("IG Resource Lang: "+(igResourceLanguage == null ? "n/a" : igResourceLanguage));
        b.append(String.format("%d of %d (%d%%) of resources have a language", withLang, total, (withLang * 100) / total));
        b.append("<a href=\"https://build.fhir.org/ig/FHIR/fhir-tools-ig/CodeSystem-ig-parameters.html#ig-parameters-resource-language-policy\">Population Policy</a>: "+policy.toString());
        return b.toString();
      }
    }
    
  }

  private class ProfileSignpostBuilder {

    private StringBuilder output = new StringBuilder();
    private int count;

    private Integer analyseLoc(String location) {
      if (location.startsWith("Bundle/") && location.contains(".entry[")) {
        String index = location.substring(location.indexOf(".entry[")+7);
        index = index.substring(0, index.indexOf("]"));
        return Integer.parseInt(index);
      }
      System.out.println("no loc for "+location);
      return -1;
    }

    private String getSpecName(String url) {
      if (url.startsWith("http://hl7.org/fhir/StructureDefinition/")) {
        return "<a href=\"http://hl7.org/fhir\">fhir</a>.";
      }
      PackageInformation pck = context.getPackageForUrl(url);
      if (pck != null) {
        if (pck.getId().equals(packageId)) {
          return "<i>this.</i>";
        } else if (pck.getWeb() != null) {
          return "<a href=\""+pck.getWeb()+"\">"+pck.getId()+"</a>.";
        } else {
          return pck.getId()+".";
        }
      }
      return "";
    }


    public void analyse(FetchedResource r, boolean only) {
      count = 0;
      Element e = r.getElement();
      String root = only ? "" : r.fhirType() + "/"+r.getId()+": ";
      StringBuilder b = new StringBuilder();
      if (!showMessages(b, root, e)) {
        List<String> paths = new ArrayList<>();
        for (ValidationMessage vm : r.getErrors()) {
          if (vm.isSignpost()) {
            if (!paths.contains(vm.getLocation())) {
              paths.add(vm.getLocation());
            }
          }
        }
        for (String path : paths) {
          List<ValidationMessage> signposts = new ArrayList<>();
          for (ValidationMessage vm : r.getErrors()) {
            if (vm.isSignpost() && vm.getLocation().equals(path)) {
              signposts.add(vm);
            }
          }
          b.append("<li>"+path+": Validated against "+analyse(signposts)+"</li>");
        }
      }
      output.append(b.toString());      
    }
    

    private boolean showMessages(StringBuilder b, String root, Element e) {
      boolean ok = false;
      if (e.hasMessages()) {
        String s = analyse(e.getMessages());
        ok = ok || !Utilities.noString(s);
        b.append("<li>"+root+e.getPath()+": Validated against "+s+"</li>");
      }
      for (Element c : e.getChildren()) {
        ok = showMessages(b, root, c) || ok;
      }
      return ok;
    }

    public boolean hasContent() {
      return output.length() > 0;
    }

    public String build() {
      return output.toString();
    }

    public String analyse(List<ValidationMessage> vmlist) {
      String base = context.formatMessage(I18nConstants.VALIDATION_VAL_PROFILE_SIGNPOST_BASE);
      List<String> list = new ArrayList<>();
      for (ValidationMessage vm : vmlist) {
        if (vm.getMessage().startsWith(base) && vm.isSignpost()) {
          String s = vm.getMessage().substring(base.length()).trim();
          String url = s.contains(" ") ? s.substring(0, s.indexOf(" ")) : s;
          s = s.contains(" ") ? s.substring(s.indexOf(" ")) : "";
          String specName = getSpecName(url);
          StructureDefinition sd = context.fetchResource(StructureDefinition.class, url);
          String l = null;
          if (sd != null) {
            l = specName+"<a href=\""+sd.getWebPath()+"\">"+sd.present()+"</a>"+s;
          } else {
            l = "<a href=\""+url+"\">"+url+"</a>"+s;          
          } 
          if (!list.contains(l)) {
            list.add(l);
            count++;
          }
        } 
      }
      return list(list);
    }

//
//    public int count() {
//      int res = 0;
//      for (List<String> i : entries.values()) {
//        res = res + i.size();
//      }
//      return res + others.size();
//    }
//
//    public String build() {
//      StringBuilder b = new StringBuilder();
//      b.append("<ul>\r\n");
//      if (entries.containsKey(-1)) {
//        b.append("<li>Validated against "+list(entries.get(-1))+"</li>\r\n");        
//      }
//      for (Integer i : sorted(entries.keySet())) {
//        if (i >= 0) {
//          b.append("<li>Entry "+Integer.toString(i)+" Validated against "+list(entries.get(i))+"</li>\r\n");        
//        }
//      }
//      for (String s : others) {
//        b.append("<li>"+s+"</li>\r\n");
//      }
//      b.append("</ul>\r\n");
//      return b.toString();
//    }
//
//    private List<Integer> sorted(Set<Integer> keySet) {
//      List<Integer> res = new ArrayList<>();
//      res.addAll(keySet);
//      Collections.sort(res);
//      return res;
//    }
//
    private String list(List<String> list) {
      StringBuilder b = new StringBuilder(); 
      for (int i = 0; i < list.size(); i++) {
        if (i > 0 && i == list.size()-1) {
          b.append(" and ");
        } else if (i > 0) {
          b.append(", ");
        }
        b.append(list.get(i));
      }
      return b.toString();
    }
    
    public int count() {
      return count;
    }

  }

  public class FiledValidationMessage {

    private FetchedFile f;
    private ValidationMessage vm;
    public FiledValidationMessage(FetchedFile f, ValidationMessage vm) {
      super();
      this.f = f;
      this.vm = vm;
    }
    public FetchedFile getF() {
      return f;
    }
    public ValidationMessage getVm() {
      return vm;
    }
  }

  private static final String INTERNAL_LINK = "internal";
  private static final boolean NO_FILTER = false;
  private static final int MAX_PATH_LENGTH_NO_BREAK = 40;
  private Date ruleDateCutoff = null;
  private String statedVersion;
  private IGKnowledgeProvider provider;
  private IGKnowledgeProvider altProvider;
  int err = 0;
  int warn = 0;
  int info = 0;
  int link = 0;
  private String root;
  private String packageId;
  private String altPackageId;
  private String igVersion;
  private String toolsVersion;
  private String currentToolsVersion;
  private String pubReqCheck;
  private RealmBusinessRules realm;
  private PreviousVersionComparator previousVersionComparator;
  private String csAnalysis;
  private String igcode;
  private String igCodeError;
  private String igrealm;
  private String igRealmError;
  private String copyrightYear;
  private IWorkerContext context;
  private List<FetchedResource> noNarratives;
  private List<FetchedResource> noValidation;
  private boolean noValidate;
  private boolean noGenerate;
  private Set<String> r5Extensions;
  private String dependencies;
  private DependentIGFinder dependentIgs;
  private IpaComparator ipaComparator;
  private IpsComparator ipsComparator;
  private List<StructureDefinition> modifierExtensions;
  private String globalCheck;
  private String draftDependencies;
  private TerminologyClientManager txServers;

  boolean hintAboutNonMustSupport = false;
  boolean anyExtensionsAllowed = false;
  boolean checkAggregation = false;
  boolean autoLoad = false;
  boolean showReferenceMessages = false;
  boolean noExperimentalContent = false;
  boolean displayWarnings = false;
  private List<FetchedResource> fragments;
  private IGLanguageInformation langinfo;
  private List<RelatedIG> relatedIGs;

  
  public ValidationPresenter(String statedVersion, String igVersion, IGKnowledgeProvider provider, IGKnowledgeProvider altProvider, String root, String packageId, String altPackageId, 
      String toolsVersion, String currentToolsVersion, RealmBusinessRules realm, PreviousVersionComparator previousVersionComparator, IpaComparator ipaComparator, IpsComparator ipsComparator,
      String dependencies, String csAnalysis, String pubReqCheck, String globalCheck, String copyrightYear, IWorkerContext context,
      Set<String> r5Extensions, List<StructureDefinition> modifierExtensions, String draftDependencies,
      List<FetchedResource> noNarratives, List<FetchedResource> noValidation, boolean noValidate, boolean noGenerate, DependentIGFinder dependentIgs, 
      TerminologyClientManager txServers, List<FetchedResource> fragments, IGLanguageInformation langinfo, List<RelatedIG> relatedIGs) {
    super();
    this.statedVersion = statedVersion;
    this.igVersion = igVersion;
    this.provider = provider;
    this.altProvider = altProvider;
    this.root = root;
    this.packageId = packageId;
    this.altPackageId = altPackageId;
    this.realm = realm;
    this.toolsVersion = toolsVersion;
    this.currentToolsVersion = currentToolsVersion;
    this.previousVersionComparator = previousVersionComparator;
    this.ipaComparator = ipaComparator;
    this.ipsComparator = ipsComparator;
    this.dependencies = dependencies;
    this.dependentIgs = dependentIgs;
    this.csAnalysis = csAnalysis;
    this.pubReqCheck = pubReqCheck;
    this.copyrightYear = copyrightYear;
    this.context = context;
    this.noNarratives = noNarratives;
    this.noValidation = noValidation;
    this.noValidate = noValidate;
    this.noGenerate = noGenerate;
    this.r5Extensions = r5Extensions;
    this.modifierExtensions = modifierExtensions;
    this.draftDependencies = draftDependencies;
    this.globalCheck = globalCheck;
    this.txServers = txServers;
    this.fragments = fragments;
    this.langinfo = langinfo;
    this.relatedIGs = relatedIGs;
    ruleDateCutoff = Date.from(LocalDate.now().minusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
    determineCode();
  }

  public void setValidationFlags(boolean hintAboutNonMustSupport, boolean anyExtensionsAllowed, boolean checkAggregation, boolean autoLoad, boolean showReferenceMessages, boolean noExperimentalContent, boolean displayWarnings) {

    this.hintAboutNonMustSupport = hintAboutNonMustSupport;
    this.anyExtensionsAllowed = anyExtensionsAllowed;
    this.checkAggregation = checkAggregation;
    this.autoLoad = autoLoad;
    this.showReferenceMessages = showReferenceMessages;
    this.noExperimentalContent = noExperimentalContent;
    this.displayWarnings = displayWarnings;

  }
  private void determineCode() {
    if (provider.getCanonical().startsWith("http://hl7.org/fhir")) {
      String[] u = provider.getCanonical().split("\\/");
      String ucode = u[u.length-1];
      String[] p = packageId.split("\\.");
      String pcode = p[p.length-1];
      igcode = ucode;
      if (!ucode.equals(pcode)) {
        igCodeError = "Error: codes in canonical and package id are different: "+ucode+" vs "+pcode+".";
      }
      igrealm = p[p.length-2];
      String urealm = u[u.length-2];
      if (!igrealm.equals(urealm)) {
        igRealmError = "Error: realms in canonical and package id are different: "+igrealm+" vs "+urealm;
      } else if (!igrealm.equalsIgnoreCase(realm.code())) {
        igRealmError = "Error: realms in IG definition and package id are different: "+igrealm+" vs "+realm.code();
      }
    } else {
      this.igcode = "n/a";
    }
  }

  public List<FetchedResource> getNoNarratives() {
    return noNarratives;
  }

  public List<FetchedResource> getNoValidation() {
    return noValidation;
  }


  private List<FetchedFile> sorted(List<FetchedFile> files) {
    List<FetchedFile> list = new ArrayList<FetchedFile>();
    list.addAll(files);
    Collections.sort(list, this);
    return list;
  }

  private boolean hasMessage(List<ValidationMessage> errors, ValidationMessage newMsg) {
    for (ValidationMessage m : errors) {
      if (m.preciseMatch(newMsg)) {
        return true;
      }
    }
    return false;
  }

  public String generate(String title, List<ValidationMessage> allErrors, List<FetchedFile> files, String path, SuppressedMessageInformation filteredMessages, String pinned) throws IOException {
    for (FetchedFile f : files) {
      for (FetchedResource r: f.getResources()) {
        for (ValidationMessage v : r.getErrors()) {
          if (!hasMessage(f.getErrors(), v)) {
            f.getErrors().add(v);
          }
        }
      }
      for (ValidationMessage vm : filterMessages(f, f.getErrors(), false, filteredMessages)) {
        if (vm.getLevel().equals(ValidationMessage.IssueSeverity.FATAL)||vm.getLevel().equals(ValidationMessage.IssueSeverity.ERROR))
          err++;
        else if (vm.getLevel().equals(ValidationMessage.IssueSeverity.WARNING))
          warn++;
        else if (!vm.isSignpost()) {
          info++;
        }
      }
    }
    
    List<ValidationMessage> linkErrors = filterMessages(null, allErrors, true, filteredMessages); 
    for (ValidationMessage vm : linkErrors) {
      if (vm.getSource() == Source.LinkChecker) {
        link++;
      } else if (vm.getLevel() == null) {
        err++;
      } else if (vm.getLevel().equals(ValidationMessage.IssueSeverity.FATAL)||vm.getLevel().equals(ValidationMessage.IssueSeverity.ERROR))
        err++;
      else if (vm.getLevel().equals(ValidationMessage.IssueSeverity.WARNING))
        warn++;
      else if (!vm.isSignpost()) {
        info++;
      }
    }
    
    files = genQAHtml(title, files, path, filteredMessages, linkErrors, true, pinned);
    files = genQAHtml(title, files, path, filteredMessages, linkErrors, false, pinned);

    Bundle validationBundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
    OperationOutcome oo = new OperationOutcome();
    validationBundle.addEntry(new BundleEntryComponent().setResource(oo));
    for (ValidationMessage vm : linkErrors) {
      if (vm.getSource() != Source.LinkChecker && vm.getLocation()!=null) {
        FHIRPathEngine fpe = new FHIRPathEngine(provider.getContext());
        try {
          fpe.parse(vm.getLocation());
        } catch (Exception e) {
          System.out.println("Internal error in location for message: '"+e.getMessage()+"', loc = '"+subst100(vm.getLocation())+"', err = '"+subst100(vm.getMessage())+"'");
        }
        oo.getIssue().add(OperationOutcomeUtilities.convertToIssue(vm, oo));
      }
    }
    for (FetchedFile f : files) {
      if (!f.getErrors().isEmpty()) {
        oo = new OperationOutcome();
        validationBundle.addEntry(new BundleEntryComponent().setResource(oo));
        ExtensionUtilities.addStringExtension(oo, ExtensionDefinitions.EXT_OO_FILE, f.getStatedPath());
        for (ValidationMessage vm : filterMessages(f, f.getErrors(), false, filteredMessages)) {
          oo.getIssue().add(OperationOutcomeUtilities.convertToIssue(vm, oo));
        }
      }
    }
    FileOutputStream s = new FileOutputStream(FileUtilities.changeFileExt(path, ".xml"));
    new XmlParser().compose(s, validationBundle, true);
    s.close();

    genQAText(title, files, path, filteredMessages, linkErrors);
    genQATextForCompare(title, files, path, filteredMessages, linkErrors);
    genQAESLintCompactText(title, files, path, filteredMessages, linkErrors);
    genTXServerQA(title, path);
    
    String summary = "Errors: " + err + ", Warnings: " + warn + ", Info: " + info+", Broken Links: "+link;
    return path + "\r\n" + summary;
  }

  private void genTXServerQA(String title, String path) throws IOException {
    XhtmlNode x = new XhtmlNode(NodeType.Element, "div");
    
    
    if (!txServers.hasClient()) {
      x.para("This IG was published without any terminology support");      
    } else {
      x.para("This page provides a report on which terminology servers were used while publishing this IG. Note that terminology caching reduces the server hit count - this page only reports content that wasn't cached");
      x.h2().tx("Terminology Parameters");
      XhtmlNode ul = x.ul();
      for (ParametersParameterComponent p : context.getExpansionParameters().getParameter()) {
        XhtmlNode li = ul.li();
        li.b().tx(p.getName());
        li.tx(": ");
        if (p.hasValuePrimitive()) {
          li.code().tx(p.getValue().primitiveValue());
        } else if (p.hasValue()) {
          li.code().tx(p.getValue().fhirType()+"??");
        }
      }
      x.h2().tx("Tx Manager report for '"+txServers.getMonitorServiceURL()+"'");
      if (txServers.getInternalLog().isEmpty()) {
        ul = x.ul();
        ul.li().tx("(No Errors/Reports - all good)");        
      } else {
        x.para().b().tx("Decisions");
        XhtmlNode tbl = x.table("grid", false);
        XhtmlNode tr = tbl.tr();
        tr.td().b().tx("Message");
        tr.td().b().tx("Server");
        tr.td().b().tx("ValueSet");
        tr.td().b().tx("Systems");
        tr.td().b().tx("Choices");
        boolean isErr = false;
        Set<String> contexts = new HashSet<>();
        for (InternalLogEvent log : txServers.getInternalLog()) {
          if (!log.isError()) {
            tr = tbl.tr();
            tr.td().tx(log.getMessage());
            tr.td().tx(log.getServer());
            tr.td().tx(log.getVs());
            tr.td().tx(log.getSystems());
            tr.td().tx(log.getChoices());
          } else {
            isErr = true;
            contexts.add(log.getContext());
          }
        }
        if (isErr) {
          x.para().b().tx("Errors");
          tbl = x.table("grid", false);
          tr = tbl.tr();
          tr.td().b().tx("URL");
          tr.td().b().tx("Message");
          for (String c : Utilities.sorted(contexts)) {
            tr = tbl.tr();
            tr.td().tx(c);
            List<InternalLogEvent> list = new ArrayList<TerminologyClientManager.InternalLogEvent>();
            for (InternalLogEvent log : txServers.getInternalLog()) {
              if (log.isError() && c.equals(log.getContext())) {
                list.add(log);
              }
            }
            if (list.size() == 1) {
              tr.td().style("background-color: #ffe3e3").span().attribute("title",list.get(0).getRequest()).tx(list.get(0).getMessage());              
            } else {
              ul = tr.td().style("background-color: #ffe3e3").ul();
              for (InternalLogEvent log : list) {
                ul.li().span().attribute("title", log.getRequest()).tx(log.getMessage());
              }
            }
          }
        }
      }     
      
      TerminologyClientContext master = txServers.getServerList().get(0);
      x.h2().tx("Primary Server: "+master.getAddress());
      genServerReport(x, master);
      for (String s : Utilities.sorted(txServers.getServerMap().keySet())) {
        TerminologyClientContext t = txServers.getServerMap().get(s);
        if (t != master) {
          x.h2().tx("Server: "+t.getAddress());
          genServerReport(x, t);          
        }
      }
    }
    
    String page = genTxServersHeader(title)+new XhtmlComposer(false, true).compose(x.getChildNodes())+genTxServerFooter(title);
    FileUtilities.stringToFile(page, Utilities.path(FileUtilities.getDirectoryForFile(path), "qa-txservers.html"));    
  }

  private void genServerReport(XhtmlNode x, TerminologyClientContext t) {
    x.ah(t.getAddress());
    x.para("Use Count: "+t.getUseCount()+". Code Systems used: ");
    Map<String, TerminologyClientContextUseCount> uc = t.getUseCounts();
    List<String> nl = Utilities.sorted(uc.keySet());
    boolean hvs = false;
    boolean hcs = false;
    for (String s : nl) {
      TerminologyClientContextUseCount us = uc.get(s);
      if (us.getExpands() + us.getValidates() > 0) {
        hcs = true;
      } 
      if (us.getReadVS() > 0) {
        hvs = true;
      }
      if (us.getReadCS() > 0) {
        hcs = true;
      }
      if (hcs & hvs) {
        break;
      }
    }
    
    if (!hcs) {
      XhtmlNode ul = x.ul();
      ul.li().tx("(None)");
    } else {        
      XhtmlNode tbl = x.table("grid", false);
      XhtmlNode tr = tbl.tr();
      tr.th().b().tx("System");
      tr.th().b().tx("#Exp.");
      tr.th().b().tx("#Val.");
      tr.th().b().tx("Details");
      for (String s : nl) {
        TerminologyClientContextUseCount us = uc.get(s);
        if (us.getExpands() + us.getValidates() > 0) {
          tr = tbl.tr();
          tr.td().tx(s);        
          tr.td().tx(us.getExpands());
          tr.td().tx(us.getValidates());
          XhtmlNode td = tr.td();
          CodeSystem cs = context.fetchResource(CodeSystem.class, s);
          if (cs != null) {
            if (cs.hasWebPath()) {
              td.ah(cs.getWebPath()).tx(cs.present());
            } else 
              td.tx(cs.present());
          }
        }
      }
    }
    if (hvs) {
      x.para().tx("ValueSets fetched:");
      XhtmlNode ul = x.ul();
      for (String s : nl) { 
        TerminologyClientContextUseCount us = uc.get(s);
        if (us.getReadVS() > 0) {
          ValueSet vs = context.fetchResource(ValueSet.class, s);
          if (vs != null && vs.hasWebPath()) {
            ul.li().ah(vs.getWebPath()).tx(s);
          } else {
            ul.li().tx(s);
          }
        }
      }
    }
  }

  private String genTxServerFooter(String title) {
    ST t = template(footerTemplateTxServers);
    t.add("version", Constants.VERSION);
    t.add("igversion", statedVersion);
    t.add("title", title);
    t.add("time", genDate);
    return t.render();
  }

  private String genTxServersHeader(String title) {

    ST t = template(headerTemplateTxServers);
    t.add("version", statedVersion);
    t.add("toolsVersion", toolsVersion);
    t.add("versionCheck", versionCheckText());
    t.add("igversion", igVersion);
    t.add("title", title);
    t.add("time", genDate);
    t.add("err",  Integer.toString(err));
    t.add("warn",  Integer.toString(warn));
    t.add("info",  Integer.toString(info));
    t.add("packageId", packageId);
    t.add("canonical", provider.getCanonical());
    t.add("copyrightYearCheck", checkCopyRightYear());
    t.add("realmCheck", realm.checkText());
    t.add("igcode", igcode);
    t.add("igcodeerror", igCodeError);
    t.add("igrealmerror", igRealmError);
    t.add("realm", igrealm == null ? "n/a" : igrealm.toUpperCase());
    t.add("dependencyCheck", dependencies);
    t.add("dependentIgs", dependentIgs.getCountDesc());
    t.add("pubReqCheck", pubReqCheck);
    t.add("csAnalysis", csAnalysis);
    t.add("previousVersion", previousVersionComparator.checkHtml());
    t.add("ipaComparison", ipaComparator == null ? "n/a" : ipaComparator.checkHtml());
    t.add("ipsComparison", ipsComparator == null ? "n/a" : ipsComparator.checkHtml());
    return t.render();
  }

  public void genQAESLintCompactText(String title, List<FetchedFile> files, String path, SuppressedMessageInformation filteredMessages, List<ValidationMessage> linkErrors)
      throws IOException {
    StringBuilder b = new StringBuilder();
    files = sorted(files);

    b.append(title + "\n");
    b.append("=========================================");
    b.append("\n\n");

    for (ValidationMessage vm : linkErrors) {
        String eslintSeverity = vm.getLevel().getDisplay();
        if (eslintSeverity.equals("Information"))
          eslintSeverity = "Info";

        if (eslintSeverity.equals("Fatal"))
          eslintSeverity = "Error";

      b.append("Generic: line 0, col 0, " + eslintSeverity + " - " + vm.getMessage() + " (" + vm.getType() + ")" + "\n");
    }

    for (FetchedFile f : files) {
      for (ValidationMessage vm : filterMessages(f, f.getErrors(), false, filteredMessages)) {
        String eslintSeverity = vm.getLevel().getDisplay();
        if (eslintSeverity.equals("Information"))
          eslintSeverity = "Info";

        if (eslintSeverity.equals("Fatal"))
          eslintSeverity = "Error";

        int eslintLine = vm.getLine();
        if (eslintLine < 0)
          eslintLine = 0;

        int eslintColumn = vm.getCol();
        if (eslintColumn < 0)
          eslintColumn = 0;

        b.append(f.getPath() + ": line " + eslintLine + ", col " + eslintColumn + ", " + eslintSeverity + " - " + vm.getMessage() + " (" + vm.getType() + ")" + "\n");
      }
    }

    b.append("\n");
    b.append("err = " + err + ", warn = " + warn + ", info = " + info + "\n");
    b.append("IG Publisher Version: " + toolsVersion);

    FileUtilities.stringToFile(b.toString(), FileUtilities.changeFileExt(path, "-eslintcompact.txt"));
  }

  public void genQAText(String title, List<FetchedFile> files, String path, SuppressedMessageInformation filteredMessages, List<ValidationMessage> linkErrors)
      throws IOException {
    StringBuilder b = new StringBuilder();
    b.append(genHeaderTxt(title, err, warn, info));
    b.append(genSummaryRowTxtInternal(linkErrors));
    files = sorted(files);
    for (FetchedFile f : files) {
      if (!f.getResources().isEmpty()) {
        b.append(genSummaryRowTxt(f));
      }
    }
    b.append(genEnd());
    b.append(genStartTxtInternal());
    for (ValidationMessage vm : linkErrors)
      b.append(vm.getDisplay() + "\r\n");
    b.append(genEndTxt());
    for (FetchedFile f : files) {
      if (!f.getResources().isEmpty()) {
        b.append(genStartTxt(f));
        for (ValidationMessage vm : filterMessages(f, f.getErrors(), false, filteredMessages))
          b.append(vm.getDisplay() + "\r\n");
        b.append(genEndTxt());
      }
    }    
    b.append(genFooterTxt(title));
    FileUtilities.stringToFile(b.toString(), FileUtilities.changeFileExt(path, ".txt"));
  }

  public void genQATextForCompare(String title, List<FetchedFile> files, String path, SuppressedMessageInformation filteredMessages, List<ValidationMessage> linkErrors)
      throws IOException {
    StringBuilder b = new StringBuilder();
    b.append(genHeaderTxtForCompare(title, err, warn, info));
    b.append(genSummaryRowTxtInternal(linkErrors));
    files = sorted(files);
    for (FetchedFile f : files) {
      if (!f.getResources().isEmpty()) {
        b.append(genSummaryRowTxt(f));
      }
    }
    b.append(genEnd());
    b.append(genStartTxtInternal());
    for (ValidationMessage vm : linkErrors)
      b.append(vm.getDisplay() + "\r\n");
    b.append(genEndTxt());
    for (FetchedFile f : files) {
      if (!f.getResources().isEmpty()) {
        b.append(genStartTxt(f));
        for (ValidationMessage vm : filterMessages(f, f.getErrors(), false, filteredMessages))
          b.append(vm.getDisplay() + "\r\n");
        b.append(genEndTxt());
      }
    }    
    b.append(genFooterTxt(title));
    FileUtilities.stringToFile(b.toString(), FileUtilities.changeFileExt(path, ".compare.txt"));
  }

  public List<FetchedFile> genQAHtml(String title, List<FetchedFile> files, String path, SuppressedMessageInformation filteredMessages, List<ValidationMessage> linkErrors, boolean allIssues, Object pinned) throws IOException {
    StringBuilder b = new StringBuilder();
    b.append(genHeader(title, err, warn, info, link, filteredMessages.count(), allIssues, path, pinned));
    b.append(genSummaryRowInteral(linkErrors));

    files = sorted(files);

    for (FetchedFile f : files) {
      if (hasIssues(f, filteredMessages) || allIssues) {
        b.append(genSummaryRow(f, filteredMessages));
      }
    }
    b.append(genEnd());
    b.append(genStartInternal());
    int id = 0;
    for (ValidationMessage vm : linkErrors) {
      b.append(genDetails(vm, id));
      id++;
    }
    b.append(genEnd());

    int i = 0;
    for (FetchedFile f : files) {
      if (hasIssues(f, filteredMessages) || allIssues) {
        i++;
        b.append(genStart(f, i));
        if (countNonSignpostMessages(f, filteredMessages))
          b.append(startTemplateErrors);
        else
          b.append(startTemplateNoErrors);
        for (ValidationMessage vm : filterMessages(f, f.getErrors(), false, filteredMessages)) {
          b.append(genDetails(vm, id));
          id++;
        }
        b.append(genEnd());
      }
    }    

    b.append(genSuppressedMessages(filteredMessages));
    b.append("<a name=\"sorted\"> </a>\r\n<p><b>Errors sorted by type</b></p>\r\n");
    for (String n : messageIdNames()) {
      List<FiledValidationMessage> fvml = new ArrayList<>();    
      for (FetchedFile f : files) {
        getMatchingMessages(f, n, fvml, filteredMessages);
      }
      if (fvml.size() > 0) {
        b.append(genGroupStart(n));
        for (FiledValidationMessage fvm : fvml) {
          b.append(genGroupDetails(fvm.getF(), fvm.getVm()));
        }
        b.append(genGroupEnd());
      }
    }
    b.append(genFooter(title));
    FileUtilities.stringToFile(b.toString(), allIssues ? path : FileUtilities.changeFileExt(path, ".min.html"));
    return files;
  }



  private boolean countNonSignpostMessages(FetchedFile f, SuppressedMessageInformation filteredMessages) {
    List<ValidationMessage> uniqueErrors = filterMessages(f, f.getErrors(), false, filteredMessages);
    for (ValidationMessage vm : uniqueErrors) {
      if (!vm.isSignpost()) {
        return true;
      }
    }
    return false;
  }

  private boolean hasIssues(FetchedFile f, SuppressedMessageInformation filteredMessages) {
    List<ValidationMessage> uniqueErrors = filterMessages(f, f.getErrors(), false, filteredMessages);
    for (ValidationMessage vm : uniqueErrors) {
      if (vm.getLevel() != IssueSeverity.INFORMATION) {
        return true;
      }
    }
    return false;
  }

  private void getMatchingMessages(FetchedFile f, String n, List<FiledValidationMessage> fvml, SuppressedMessageInformation filteredMessages) {
    for (ValidationMessage vm : filterMessages(f, f.getErrors(), false, filteredMessages)) {
      if (n.equals(vm.getMessageId()) && !vm.isSignpost()) {
        fvml.add(new FiledValidationMessage(f, vm));
      }
    }
  }

  private List<String> messageIdNames() {
    I18nConstants obj = new I18nConstants();
    org.hl7.fhir.igtools.publisher.PublisherMessageIds obj2 = new org.hl7.fhir.igtools.publisher.PublisherMessageIds(); // not that it really matters?
    List<String> names = new ArrayList<>();
    Field[] interfaceFields=I18nConstants.class.getFields();
    for(Field f : interfaceFields) {
      try {
        if (Modifier.isStatic(f.getModifiers())) {
          String n = (String) f.get(obj);
          names.add(n);
        }
      } catch (Exception e) {
      }
    }
    interfaceFields=org.hl7.fhir.igtools.publisher.PublisherMessageIds.class.getFields();
    for(Field f : interfaceFields) {
      try {
        if (Modifier.isStatic(f.getModifiers())) {
          String n = (String) f.get(obj2);
          names.add(n);
        }
      } catch (Exception e) {
      }
    }
    Collections.sort(names);
    return names;
  }

  private String subst100(String msg) {
    if (msg == null)
      return "";
    return msg.length() > 100 ? msg.substring(0, 100) : msg;
  }

  private String genSuppressedMessages(SuppressedMessageInformation msgs) {
    StringBuilder b = new StringBuilder();
    b.append("<a name=\"suppressed\"> </a>\r\n<p><b>Suppressed Messages (Warnings, hints, broken links)</b></p>\r\n");
    boolean found = false;
    suppressedInfo = 0;
    suppressedWarnings = 0;
    for (String s : msgs.categories()) {
      b.append("<p><b>"+Utilities.escapeXml(s)+"</b></p><ul>\r\n");
      for (SuppressedMessage m : msgs.list(s)) {
        suppressedInfo += m.getUseCountHint();
        suppressedWarnings += m.getUseCountWarning();
        found = true;
        b.append(" <li>"+Utilities.escapeXml(m.getMessageRaw())+" <span style=\"color: "+(m.getUseCount() == 0 ? "maroon" : "navy")+"\">("+m.getUseCount()+" uses)</span></li>\r\n");
      }
      b.append("</ul>\r\n");
    }
    if (!found) {
      b.append("<p>No suppressed messsages</p>");
    }
    return b.toString();
  }


  public static List<ValidationMessage> filterMessages(FetchedFile f, List<ValidationMessage> messages, boolean canSuppressErrors, SuppressedMessageInformation suppressedMessages) {
    if (f != null && f.getFilteredMessages() != null) {
      return f.getFilteredMessages();
    }
    List<ValidationMessage> passList = new ArrayList<ValidationMessage>();
    Set<String> msgs = new HashSet<>();
    for (ValidationMessage message : messages) {
      boolean passesFilter = true;
      if ((canSuppressErrors || !message.getLevel().isError()) && (!"HTML_PARSING_FAILED".equals(message.getMessageId()))) {
        if (suppressedMessages.contains(message.getDisplay(), message) || suppressedMessages.contains(message.getMessage(), message) ||
              suppressedMessages.contains(message.getHtml(), message) || suppressedMessages.contains(message.getMessageId(), message) || 
              suppressedMessages.contains(message.getInvId(), message)) {
          passesFilter = false;
        } else if (msgs.contains(message.getLocation()+"|"+message.getMessage())) {
          passesFilter = false;
        }
      }
      if (message.getLevel() != null && message.getLevel().isError()) {
        if (suppressedMessages.contains(message.getDisplay(), message) || suppressedMessages.contains(message.getMessage(), message) ||
            suppressedMessages.contains(message.getHtml(), message) || suppressedMessages.contains(message.getMessageId(), message) || 
            suppressedMessages.contains(message.getInvId(), message)) {
          // nothing - we're just letting the message get a comment here
      } else if (msgs.contains(message.getLocation()+"|"+message.getMessage())) {
        // nothing - we're just letting the message get a comment here
      }
    }
      if (NO_FILTER) {
        passesFilter = true;
      }
      if (message.isSignpost()) {
        passesFilter = false;        
      }
      if (passesFilter) {
        passList.add(message);
        msgs.add(message.getLocation()+"|"+message.getMessage());        
      } else {
      }
    }
    if (f != null) {
      f.setFilteredMessages(passList);
    }
    return passList;
  }
  
  // HTML templating
  private final String headerTemplate = 
      "<!DOCTYPE HTML>\r\n"+
      "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\r\n"+
      "<!-- broken links = $links$, errors = $err$, warn = $warn$, info = $info$-->\r\n"+
      "<head>\r\n"+
      "  <title>$title$ : Validation Results</title>\r\n"+
      "  <link href=\"fhir.css\" rel=\"stylesheet\"/>\r\n"+
      "  <meta charset=\"utf-8\"/>\r\n"+
      "  <style>\r\n"+
      "    span.flip  { background-color: #4CAF50; color: white; border: solid 1px #a6d8a8; padding: 2px }\r\n"+
      "    span.toggle  { background-color: #e6f2ff; color: black; border: solid 1px #0056b3; padding: 2px; font-size: 10px }\r\n"+
      "    button { padding: 0px 8px; font-size: 12px; cursor: pointer; border-radius: 2px; border: 1px solid #ccc; background-color: #f0f0f0 }\r\n"+
      "    button:hover { background-color: #e0e0e0; }\r\n"+
      "    .code-value { display: inline; }\r\n"+
      "    .code-hidden { display: none; }\r\n"+
      "    span.toggle  { font-size: 10px }\r\n"+
      "  </style>\r\n"+
      "  <script>\r\n"+
      "    function flip(id) {\r\n"+
      "      var span = document.getElementById('s'+id);\r\n"+
      "      var div = document.getElementById(id);\r\n"+
      "      if (document.getElementById('s'+id).innerHTML == 'Show Reasoning') {\r\n"+
      "        div.style.display = 'block';\r\n"+
      "        span.innerHTML = 'Hide Reasoning';\r\n"+
      "      } else {\r\n"+
      "        div.style.display = 'none';\r\n"+
      "        span.innerHTML = 'Show Reasoning';\r\n"+
      "      }\r\n"+
      "    }\r\n"+
      "    function toggle(id) {\r\n"+
      "      var span = document.getElementById('s'+id);\r\n"+
      "      var div = document.getElementById(id);\r\n"+
      "      if (document.getElementById('s'+id).innerHTML == 'Show Validation Information') {\r\n"+
      "        div.style.display = 'block';\r\n"+
      "        span.innerHTML = 'Hide Validation Information';\r\n"+
      "      } else {\r\n"+
      "        div.style.display = 'none';\r\n"+
      "        span.innerHTML = 'Show Validation Information';\r\n"+
      "      }\r\n"+
      "    }\r\n"+
      "    function toggleCodes() {\r\n"+
      "      const codes = document.getElementsByClassName('code-value');\r\n"+
      "      for (let code of codes) {\r\n"+
      "        code.classList.toggle('code-hidden');\r\n"+
      "      }\r\n"+
      "    }\r\n"+
      "  </script>\r\n"+
      "</head>\r\n"+
      "<body style=\"margin: 20px; background-color: #ffffff\">\r\n"+
      " <h1>Validation Results for $title$</h1>\r\n"+
      " <p>Generated $time$, FHIR version $version$ for $packageId$#$igversion$ (canonical = <a href=\"$canonical$\">$canonical$</a> (<a href=\"$canonical$/history.html\">history</a>)). See <a href=\"$otherFilePath$\">$otherFileName$</a> &amp; <a href=\"qa-ipreview.html\">IP</a></p>\r\n"+
      "$warning$"+
      "<table class=\"grid\">"+
      " <tr><td colspan=2><b>Quality Checks</b></td></tr>\r\n"+
      " <tr><td>Publisher Version:</td><td>$versionCheck$</td></tr>\r\n"+
      " <tr><td>Publication Code:</td><td>$igcode$<span style=\"color: maroon; font-weight: bold\"> $igcodeerror$</span>. PackageId = $packageId$, Canonical = $canonical$</td></tr>\r\n"+
      " <tr><td>Realm Check for $realm$:</td><td><span style=\"color: maroon; font-weight: bold\">$igrealmerror$</span>$realmCheck$</td></tr>\r\n"+
      " <tr><td>Language Info:</td><td>$langInfo$</td></tr>\r\n"+
      " <tr><td>Publication Request:</td><td>$pubReqCheck$</td></tr>\r\n"+
      " <tr><td>Supressed Messages:</td><td>$suppressedmsgssummary$</td></tr>\r\n"+
      " <tr><td>Dependency Checks:</td><td>$dependencyCheck$</td></tr>\r\n"+
      " <tr><td>Related IGs:</td><td>$relatedIgs$</td></tr>\r\n"+
      " <tr><td>Dependent IGs:</td><td><a href=\"qa-dep.html\">$dependentIgs$</a></td></tr>\r\n"+
      " <tr><td>Global Profiles:</td><td>$globalCheck$</td></tr>\r\n"+
      " <tr><td>Terminology Server(s):</td><td>$txserverlist$ (<a href=\"qa-txservers.html\">details</a>)</td></tr>\r\n"+
      " <tr><td>HTA Analysis:</td><td>$csAnalysis$</td></tr>\r\n"+
      " <tr><td>R5 Dependencies:</td><td>$r5usage$</td></tr>\r\n"+
      " <tr><td>Draft Dependencies:</td><td>$draftDependencies$</td></tr>\r\n"+
      " <tr><td>Modifier Extensions:</td><td>$modifiers$</td></tr>\r\n"+
      " <tr><td>Previous Version Comparison:</td><td> $previousVersion$</td></tr>\r\n"+
      " <tr><td>IPA Comparison:</td><td> $ipaComparison$</td></tr>\r\n"+
      " <tr><td>IPS Comparison:</td><td> $ipsComparison$</td></tr>\r\n"+
      " <tr><td>Validation Flags:</td><td> $validationFlags$</td></tr>\r\n"+
      "$noNarrative$"+
      "$noValidation$"+
      "$fragments$"+
      " <tr><td>Summary:</td><td> errors = $err$, warn = $warn$, info = $info$, broken links = $links$, pinned = $pinned$.  <button onclick=\"toggleCodes()\">Show Message Ids</button></td></tr>\r\n"+
      "</table>\r\n"+
      " <table class=\"grid\">\r\n"+
      "   <tr>\r\n"+
      "     <td><b>Filename</b></td><td><b>Errors</b></td><td><b>Warnings</b></td><td><b>Hints</b></td>\r\n"+
      "   </tr>\r\n";
  
  private final String headerTemplateTxServers = 
      "<!DOCTYPE HTML>\r\n"+
      "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\r\n"+
      "<head>\r\n"+
      "  <title>$title$ : Terminology Server Report</title>\r\n"+
      "  <link href=\"fhir.css\" rel=\"stylesheet\"/>\r\n"+
      "  <meta charset=\"utf-8\"/>\r\n"+
      "  <style>\r\n"+
      "    span.flip  { background-color: #4CAF50; color: white; border: solid 1px #a6d8a8; padding: 2px }\r\n"+
      "    span.toggle  { background-color: #e6f2ff; color: black; border: solid 1px #0056b3; padding: 2px; font-size: 10px }\r\n"+
      "    span.toggle  { font-size: 10px }\r\n"+
      "  </style>\r\n"+
      "</head>\r\n"+
      "<body style=\"margin: 20px; background-color: #ffffff\">\r\n"+
      " <h1>Terminology Server Report for $title$</h1>\r\n"+
      " <p>Generated $time$, FHIR version $version$ for $packageId$#$igversion$ (canonical = <a href=\"$canonical$\">$canonical$</a> (<a href=\"$canonical$/history.html\">history</a>))</p>\r\n";
  
  private final String summaryTemplate = 
      "   <tr style=\"background-color: $color$\">\r\n"+
      "     <td><a href=\"#$link$\"><b>$filename$</b></a></td><td><b>$errcount$</b></td><td><b>$warningcount$</b></td><td><b>$infocount$</b></td>\r\n"+
      "   </tr>\r\n";
  
  private final String endTemplate = 
      "</table>\r\n";
  
  private final String groupEndTemplate = 
      "</table>\r\n";

  private final String startTemplate = 
      "<hr/>\r\n"+
      "<a name=\"$link$\"> </a>\r\n"+
      "<h2><a href=\"$xlink$\">$path$</a> <span id=\"sv$id$\" class=\"toggle\" onclick=\"toggle('v$id$')\">Show Validation Information</span> <span class=\"vcount\" onclick=\"toggle('v$id$')\">$vsumm$</span></h2>\r\n"+
      " <div style=\"border: 1px grey solid; display: none\" id=\"v$id$\">$signposts$</div>\r\n"+
      " <table class=\"grid\">\r\n";
  
  private final String groupStartTemplate = 
      "<hr/>\r\n"+
      "<a name=\"$name$\"> </a>\r\n"+
      "<h2>$name$</h2>\r\n"+
      " <table class=\"grid\">\r\n";
  
  private final String startTemplateErrors = 
      "   <tr>\r\n"+
      "     <td><b>Path</b></td><td><b>Severity</b></td><td><b>Message</b></td>\r\n"+
      "   </tr>\r\n";

  private final String startTemplateNoErrors = 
      "   <tr>\r\n"+
      "     <td>&check;</td>\r\n"+
      "   </tr>\r\n";

  private final String detailsTemplate = 
      "   <tr style=\"background-color: $color$\">\r\n"+
      "     <td><b>$path$</b></td><td><b>$level$</b></td><td><b>$msg$</b>$comment$ <span class=\"code-value code-hidden\" style=\"font-size: 8px; color: navy\">$mid$</span></td>\r\n"+
      "   </tr>\r\n";
  
  private final String groupDetailsTemplate = 
      "   <tr style=\"background-color: $halfcolor$\">\r\n"+
      "     <td><a href=\"$xlink$\">$fpath$</a></td><td><b>$msg$</b>$comment$</td>\r\n"+
      "   </tr>\r\n";


  private final String detailsTemplateTxLink =
          "   <tr style=\"background-color: $color$\">\r\n"+
                  "     <td><b>$path$</b></td><td><b>$level$</b></td><td><b>$msg$</b>$comment$ (from <a href=\"qa-txservers.html#$txsrvr$\">$txsrvr$</a>, see <a href=\"$tx$\">log</a>) <span class=\"code-value code-hidden\" style=\"font-size: 8px; color: navy\">$mid$</span></td>\r\n"+
                  "   </tr>\r\n";

  private final String detailsTemplateTxLinkDiagnostics =
          "   <tr style=\"background-color: $color$\">\r\n"+
                  "     <td><b>$path$</b></td><td><b>$level$</b></td><td><b>$msg$</b>$comment$ (from <a href=\"qa-txservers.html#$txsrvr$\">$txsrvr$</a>, see <a href=\"$tx$\">log</a>, or see <a href=\"#$vmid$\" onClick=\"document.getElementById('$vmid$').style.display='block'\">the servers logic</a>) <span class=\"code-value code-hidden\" style=\"font-size: 8px; color: navy\">$mid$</span><div id=\"$vmid$\" style=\"display:none\"><br/><pre>$diags$</pre></div></td>\r\n"+
                  "   </tr>\r\n";

  private final String detailsTemplateTxNoLink =
          "   <tr style=\"background-color: $color$\">\r\n"+
                  "     <td><b>$path$</b></td><td><b>$level$</b></td><td><b>$msg$</b>$comment$ (from <a href=\"qa-txservers.html#$txsrvr$\">$txsrvr$</a>) <span class=\"code-value code-hidden\" style=\"font-size: 8px; color: navy\">$mid$</span></td>\r\n"+
                  "   </tr>\r\n";

  private final String detailsTemplateTxNoLinkDiagnostics =
          "   <tr style=\"background-color: $color$\">\r\n"+
                  "     <td><b>$path$</b></td><td><b>$level$</b></td><td><b>$msg$</b>$comment$ (from <a href=\"qa-txservers.html#$txsrvr$\">$txsrvr$</a> - see <a href=\"#$vmid$\" onClick=\"document.getElementById('$vmid$').style.display='block'\">the servers logic</a>) <span class=\"code-value code-hidden\" style=\"font-size: 8px; color: navy\">$mid$</span><div id=\"$vmid$\" style=\"display:none\"><br/><pre>$diags$</pre></div></td>\r\n"+
                  "   </tr>\r\n";

  private final String detailsTemplateWithExtraDetails = 
      "   <tr style=\"background-color: $color$\">\r\n"+
      "     <td><b><a href=\"$pathlink$\">$path$</a></b></td><td><b>$level$</b></td><td><b>$msg$</b>$comment$ <span id=\"s$id$\" class=\"flip\" onclick=\"flip('$id$')\">Show Reasoning</span><div id=\"$id$\" style=\"display: none\"><p>&nbsp;</p>$msgdetails$</div> <span class=\"code-value code-hidden\" style=\"font-size: 8px; color: navy\">$mid$</span></td>\r\n"+
      "   </tr>\r\n";
      
  private final String detailsTemplateWithLink = 
      "   <tr style=\"background-color: $color$\">\r\n"+
      "     <td><b><a href=\"$pathlink$\">$path$</a></b></td><td><b>$level$</b></td><td><b>$msg$</b>$comment$ <span class=\"code-value code-hidden\" style=\"font-size: 8px; color: navy\">$mid$</span></td>\r\n"+
      "   </tr>\r\n";
  
  private final String footerTemplate = 
      "</body>\r\n"+
      "</html>\r\n";
  
  private final String footerTemplateTxServers = 
      "</body>\r\n"+
      "</html>\r\n";
  
  // Text templates
  private final String headerTemplateText = 
      "$title$ : Validation Results\r\n"+
      "=========================================\r\n\r\n"+
      "err = $err$, warn = $warn$, info = $info$\r\n"+
      "$versionCheck$\r\n"+
      "Generated $time$. FHIR version $version$ for $packageId$#$igversion$ (canonical = $canonical$)\r\n$warning$\r\n";
  
  private final String summaryTemplateText = 
      " $filename$ : $errcount$ / $warningcount$ / $infocount$\r\n";
  
  private final String endTemplateText = 
      "\r\n";

  private final String startTemplateText = 
      "\r\n== $path$ ==\r\n";

  private final String detailsTemplateText = 
      " * $level$ : $path$ ==> $msg$\r\n";
  
  private final String footerTemplateText = 
      "\r\n";
  private String genDate = new Date().toString();
  private int suppressedInfo;
  private int suppressedWarnings;
  
  private ST template(String t) {
    return new ST(t, '$', '$');
  }

  private String genHeader(String title, int err, int warn, int info, int links, int msgCount, boolean allIssues, String path, Object pinned) {
    ST t = template(headerTemplate);
    t.add("version", statedVersion);
    t.add("igversion", igVersion);
    t.add("toolsVersion", toolsVersion);
    t.add("versionCheck", versionCheckHtml());
    t.add("title", title);
    t.add("time", genDate);
    t.add("err", Integer.toString(err));
    t.add("warn", Integer.toString(warn));
    t.add("info", Integer.toString(info));
    t.add("links", Integer.toString(links));
    t.add("pinned", pinned);
    t.add("packageId", packageId);
    t.add("canonical", provider.getCanonical());
    t.add("copyrightYearCheck", checkCopyRightYear());
    t.add("realmCheck", realm.checkHtml());
    t.add("igcode", igcode);
    t.add("igcodeerror", igCodeError);
    t.add("igrealmerror", igRealmError);
    t.add("pubReqCheck", pubReqCheck);
    t.add("realm", igrealm == null ? "n/a" : igrealm.toUpperCase());
    t.add("globalCheck", globalCheck);
    t.add("txserverlist", txserverlist());
    t.add("dependencyCheck", dependencies);
    t.add("draftDependencies", draftDependencies);
    t.add("relatedIgs", relatedIgs());
    t.add("dependentIgs", dependentIgs.getCountDesc());    
    t.add("csAnalysis", csAnalysis);
    t.add("r5usage", genR5());
    t.add("modifiers", genModifiers());
    t.add("otherFileName", allIssues ? "Errors Only" : "Full QA Report");
    t.add("otherFilePath", allIssues ? "qa.min.html" : "qa.html");
    t.add("previousVersion", previousVersionComparator.checkHtml());
    t.add("ipaComparison", ipaComparator == null ? "n/a" : ipaComparator.checkHtml());
    t.add("ipsComparison", ipsComparator == null ? "n/a" : ipsComparator.checkHtml());
    t.add("noNarrative", genResourceList(noNarratives, "Narratives Suppressed"));
    t.add("noValidation", genResourceList(noValidation, "Validation Suppressed"));
    t.add("fragments", genResourceList(noValidation, "CodeSystem Fragments"));
    t.add("langInfo", langinfo.generate());
    t.add("validationFlags", validationFlags());
    
    if (noGenerate || noValidate) {
      if (noGenerate && noValidate) {
        t.add("warning", "<p style=\"color: maroon; font-weight: bold\">Warning: This IG was generated with both validation and HTML generation off. Many kinds of errors will not be reported.</p>\r\n");        
      } else if (noGenerate) {
        t.add("warning", "<p style=\"color: maroon; font-weight: bold\">Warning: This IG was generated with HTML generation off. Some kinds of errors will not be reported.</p>\r\n");        
      } else {
        t.add("warning", "<p style=\"color: maroon; font-weight: bold\">Warning: This IG was generated with validation off. Many kinds of errors will not be reported.</p>\r\n");        
      }      
    } else {
      t.add("warning", "");
    }

    if (msgCount == 0)
      t.add("suppressedmsgssummary", "No Suppressed Issues\r\n");
    else
      t.add("suppressedmsgssummary", "<a href=\"#suppressed\">"+msgCount+" Suppressed "+Utilities.pluralize("Issue", msgCount)+"</a>\r\n");
    return t.render();
  }

  private String relatedIgs() {
    if (relatedIGs.isEmpty()) {
      return "n/a";
    }
    XhtmlNode x = new XhtmlNode(NodeType.Element);
    XhtmlNode tbl = x.table("grid");
    XhtmlNode tr = tbl.tr();
    tr.th().b().tx("code");
    tr.th().b().tx("ID");
    tr.th().b().tx("Role");
    tr.th().b().tx("Version");
    tr.th().b().tx("Source");
    tr.th().b().tx("Location");
    tr.th().b().tx("Link Count");
    for (RelatedIG ig : relatedIGs) {
      tr = tbl.tr();
      tr.td().tx(ig.getCode());
      tr.td().tx(ig.getId());
      tr.td().tx(ig.getRoleCode());
      if (ig.getNpm() != null) {
        tr.td().tx(ig.getNpm().version());        
      } else {
        tr.td().span().style("color: maroon").tx(ig.getMessage());        
      }
      tr.td().tx(ig.getMode().toCode());
      tr.td().tx(ig.getWebLocation());
      tr.td().tx(ig.getLinkCount());      
    }
    return new XhtmlComposer(false, true).compose(tbl);
  }

  private String validationFlags() {
    CommaSeparatedStringBuilder t = new CommaSeparatedStringBuilder();
    CommaSeparatedStringBuilder f = new CommaSeparatedStringBuilder();
    (hintAboutNonMustSupport ? t : f).append("hintAboutNonMustSupport");
    (anyExtensionsAllowed ? t : f).append("anyExtensionsAllowed");
    (checkAggregation ? t : f).append("checkAggregation");
    (autoLoad ? t : f).append("autoLoad");
    (showReferenceMessages ? t : f).append("showReferenceMessages");
    (noExperimentalContent ? t : f).append("noExperimentalContent");
    (displayWarnings ? t : f).append("displayWarnings");

    return "On: "+(t.length() == 0 ? "none" : t.toString())+"; Off: "+(f.length() == 0 ? "" : f.toString());
  }

  private String txserverlist() {
    CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder(", ");
    for (String s : Utilities.sorted(txServers.getServerMap().keySet())) {
      b.append("<a href=\""+s+"\">"+s+"</a>");
    }
    return b.toString();
  }

  private String genR5() {
    if (r5Extensions == null || r5Extensions.isEmpty()) {
      return "<span style=\"color: grey\">(none)</span>";
    } else {
      StringBuilder b = new StringBuilder();
      b.append("<ul>");
      for (String url : r5Extensions) {
        String s = url.substring(url.lastIndexOf("-")+1);
        if (s.contains(".")) {
          s = s.substring(0, s.indexOf("."));
        }
        b.append("<li><a href=\"http://build.fhir.org/"+s.toLowerCase()+".html\">"+Utilities.escapeXml(url)+"</a></li>");
      }
      b.append("</ul>");
      return b.toString();
    }
  }

  private String genModifiers() {
    if (modifierExtensions == null || modifierExtensions.isEmpty()) {
      return "<span style=\"color: grey\">(none)</span>";
    } else {
      StringBuilder b = new StringBuilder();
      b.append("<ul>");
      for (StructureDefinition sd : modifierExtensions) {
        b.append("<li><a href=\""+sd.getWebPath()+"\">"+Utilities.escapeXml(sd.present())+"</a></li>");
      }
      b.append("</ul>");
      return b.toString();
    }
  }

  private String genResourceList(List<FetchedResource> list, String name) {
    if (list == null || list.size() == 0) {
      return "";
    }
    StringBuilder b = new StringBuilder();
    b.append("<tr><td>"+name+"</td><td>");
    boolean first = true;
    for (FetchedResource r : list) {
      if (first) first = false; else b.append(", ");
      b.append("<a href=\""+r.getPath()+"\">"+r.fhirType()+"/"+r.getId()+"</a>");
    }
    return b.toString();
  }

  private String genHeaderTxt(String title, int err, int warn, int info) {
    ST t = template(headerTemplateText);
    t.add("version", statedVersion);
    t.add("toolsVersion", toolsVersion);
    t.add("versionCheck", versionCheckText());
    t.add("igversion", igVersion);
    t.add("title", title);
    t.add("time", genDate);
    t.add("err",  Integer.toString(err));
    t.add("warn",  Integer.toString(warn));
    t.add("info",  Integer.toString(info));
    t.add("packageId", packageId);
    t.add("canonical", provider.getCanonical());
    t.add("copyrightYearCheck", checkCopyRightYear());
    t.add("realmCheck", realm.checkText());
    t.add("igcode", igcode);
    t.add("igcodeerror", igCodeError);
    t.add("igrealmerror", igRealmError);
    t.add("realm", igrealm == null ? "n/a" : igrealm.toUpperCase());
    t.add("dependencyCheck", dependencies);
    t.add("dependentIgs", dependentIgs.getCountDesc());
    t.add("pubReqCheck", pubReqCheck);
    t.add("csAnalysis", csAnalysis);
    t.add("previousVersion", previousVersionComparator.checkHtml());
    t.add("ipaComparison", ipaComparator == null ? "n/a" : ipaComparator.checkHtml());
    t.add("ipsComparison", ipsComparator == null ? "n/a" : ipsComparator.checkHtml());
    if (noGenerate || noValidate) {
      if (noGenerate && noValidate) {
        t.add("warning", "Warning: This IG was generated with both validation and HTML generation off. Many kinds of errors will not be reported.\r\n");        
      } else if (noGenerate) {
        t.add("warning", "Warning: This IG was generated with HTML generation off. Some kinds of errors will not be reported.\r\n");        
      } else {
        t.add("warning", "Warning: This IG was generated with validation off. Many kinds of errors will not be reported.\r\n");        
      }      
    } else {
      t.add("warning", "");
    }
    return t.render();
  }

  private String genHeaderTxtForCompare(String title, int err, int warn, int info) {
    ST t = template(headerTemplateText);
    t.add("version", "$--");
    t.add("toolsVersion", "$--");
    t.add("versionCheck", "$--");
    t.add("igversion", "$--");
    t.add("title", title);
    t.add("time", "$--");
    t.add("err",  Integer.toString(err));
    t.add("warn",  Integer.toString(warn));
    t.add("info",  Integer.toString(info));
    t.add("packageId", packageId);
    t.add("canonical", provider.getCanonical());
    t.add("copyrightYearCheck", checkCopyRightYear());
    t.add("realmCheck", realm.checkText());
    t.add("igcode", igcode);
    t.add("igcodeerror", igCodeError);
    t.add("igrealmerror", igRealmError);
    t.add("realm", igrealm == null ? "n/a" : igrealm.toUpperCase());
    t.add("dependencyCheck", dependencies);
    t.add("dependentIgs", dependentIgs.getCountDesc());
    t.add("pubReqCheck", pubReqCheck);
    t.add("csAnalysis", csAnalysis);
    t.add("previousVersion", previousVersionComparator.checkHtml());
    t.add("ipaComparison", ipaComparator == null ? "n/a" : ipaComparator.checkHtml());
    t.add("ipsComparison", ipsComparator == null ? "n/a" : ipsComparator.checkHtml());
    if (noGenerate || noValidate) {
      if (noGenerate && noValidate) {
        t.add("warning", "Warning: This IG was generated with both validation and HTML generation off. Many kinds of errors will not be reported.\r\n");        
      } else if (noGenerate) {
        t.add("warning", "Warning: This IG was generated with HTML generation off. Some kinds of errors will not be reported.\r\n");        
      } else {
        t.add("warning", "Warning: This IG was generated with validation off. Many kinds of errors will not be reported.\r\n");        
      }      
    } else {
      t.add("warning", "");
    }
    return t.render();
  }

  private String checkCopyRightYear() {
    if (copyrightYear == null) {
      return "<br/>The IG resource does not contain a copyrightYear parameter.";
    } else if (copyrightYear.endsWith("+") && copyrightYear.length() == 5) {
      String s = copyrightYear.substring(0, 4);
      if (Utilities.isInteger(s)) {
        int y = Integer.parseInt(s);
        if (y > Calendar.getInstance().get(Calendar.YEAR)) {
          return "<br/><br/><span style=\"background-color: #ffcccc\">The copyrightYear parameter ('"+copyrightYear+"') in the IG resource looks wrong - should not be after this year</span>";                  
        } else {
          return "<br/><br/>The copyrightYear parameter ('"+copyrightYear+"') in the IG resource is good";          
        }
      } else {
        return "<br/><br/><span style=\"background-color: #ffcccc\">The copyrightYear parameter ('"+copyrightYear+"') in the IG resource can't be understood - expecting YYYY+</span>";        
      }
    } else {
      return "<br/><br/><span style=\"background-color: #ffcccc\">The copyrightYear parameter ('"+copyrightYear+"') in the IG resource can't be understood - expecting YYYY+</span>";        
    }
  }
    
  private String genEnd() {
    ST t = template(endTemplate);
    t.add("version", Constants.VERSION);
    t.add("igversion", statedVersion);
    t.add("time", genDate);
    return t.render();
  }

  private String genGroupEnd() {
    ST t = template(groupEndTemplate);
    return t.render();
  }

  private String genEndTxt() {
    ST t = template(endTemplateText);
    t.add("version", Constants.VERSION);
    t.add("igversion", statedVersion);
    t.add("time", genDate);
    return t.render();
  }

  private String genFooter(String title) {
    ST t = template(footerTemplate);
    t.add("version", Constants.VERSION);
    t.add("igversion", statedVersion);
    t.add("title", title);
    t.add("time", genDate);
    return t.render();
  }

  private String genFooterTxt(String title) {
    ST t = template(footerTemplateText);
    t.add("version", Constants.VERSION);
    t.add("igversion", statedVersion);
    t.add("title", title);
    t.add("time", genDate);
    return t.render();
  }

  private String genSummaryRowInteral(List<ValidationMessage> list) {
    ST t = template(summaryTemplate);
    t.add("link", INTERNAL_LINK);
    
    t.add("filename", "Build Errors");
    String ec = errCount(list);
    t.add("errcount", ec);
    t.add("warningcount", warningCount(list));
    t.add("infocount", infoCount(list));
    if ("0".equals(ec)) {
      t.add("color", "#EFFFEF");
    } else {
      t.add("color", colorForLevel(IssueSeverity.ERROR, false));
    }
      
    return t.render();
  }

  private String genSummaryRow(FetchedFile f, SuppressedMessageInformation filteredMessages) {
    ST t = template(summaryTemplate);
    t.add("link", makelink(f));
    List<ValidationMessage> uniqueErrors = filterMessages(f, f.getErrors(), false, filteredMessages);
    
    t.add("filename", f.getName());
    String ec = errCount(uniqueErrors);
    t.add("errcount", ec);
    t.add("warningcount", warningCount(uniqueErrors));
    t.add("infocount", infoCount(uniqueErrors));
    if ("0".equals(ec)) {
      t.add("color", "#EFFFEF");
    } else {
      t.add("color", colorForLevel(IssueSeverity.ERROR, false));
    }
      
    return t.render();
  }

  private String genSummaryRowTxt(FetchedFile f) {
    ST t = template(summaryTemplateText);
    t.add("filename", f.getName());
    String ec = errCount(f.getErrors());
    t.add("errcount", ec);
    t.add("warningcount", warningCount(f.getErrors()));
    t.add("infocount", infoCount(f.getErrors()));
      
    return t.render();
  }

  private String genSummaryRowTxtInternal(List<ValidationMessage> linkErrors) {
    ST t = template(summaryTemplateText);
    t.add("filename", "Build Errors");
    String ec = errCount(linkErrors);
    t.add("errcount", ec);
    t.add("warningcount", warningCount(linkErrors));
    t.add("infocount", infoCount(linkErrors));
      
    return t.render();
  }

  
  private String makelink(FetchedFile f) {
    String fn = f.getName().replace("/", "_").replace("\\", "_").replace(":", "_").replace("#", "_");
    return fn;
  }

  private String errCount(List<ValidationMessage> list) {
    int c = 0;
    for (ValidationMessage vm : list) {
      if (vm.getLevel() == IssueSeverity.ERROR || vm.getLevel() == IssueSeverity.FATAL)
        c++;
    }
    return Integer.toString(c);
  }

  private String warningCount(List<ValidationMessage> list) {
    int c = 0;
    for (ValidationMessage vm : list) {
      if (vm.getLevel() == IssueSeverity.WARNING)
        c++;
    }
    return Integer.toString(c);
  }

  private String infoCount(List<ValidationMessage> list) {
    int c = 0;
    for (ValidationMessage vm : list) {
      if (vm.getLevel() == IssueSeverity.INFORMATION)
        c++;
    }
    return Integer.toString(c);
  }

  private String genStart(FetchedFile f, int i) {
    ST t = template(startTemplate);
    t.add("id", "i"+Integer.toString(i));
    t.add("link", makelink(f));
    t.add("filename", f.getName());
    t.add("path", makeLocal(f.getPath()));
    String link = f.getResources().isEmpty() ? null : provider.getLinkFor(f.getResources().get(0), true);
    if (link==null) {
      link = f.getResources().isEmpty() ? null : altProvider.getLinkFor(f.getResources().get(0), true);
    }
    if (link != null) { 
      link = link.replace("{{[id]}}", f.getResources().get(0).getId());
      link = link.replace("{{[type]}}", f.getResources().get(0).fhirType());
    }
    ProfileSignpostBuilder psb = new ProfileSignpostBuilder();
    
    for (FetchedResource r : f.getResources()) {
      psb.analyse(r, f.getResources().size() == 1); 
    }
    if (psb.hasContent()) {
      t.add("signposts", "<ul style=\"font-size: 7px\">"+psb.build()+"</ul>\r\n");            
    } else {
      t.add("signposts", "");      
    }
    t.add("vsumm", "("+Integer.toString(psb.count())+")");            
    t.add("xlink", link);
    return t.render();
  }
    
  private String genGroupStart(String n) {
    ST t = template(groupStartTemplate);
    t.add("name", n);
    return t.render();
  }
  
  private String makeLocal(String path) {
    if (path.startsWith(root))
      return path.substring(root.length()+1);
    return path;
  }

  private String genStartInternal() {
    ST t = template(startTemplate);
    t.add("id", "");
    t.add("link", INTERNAL_LINK);
    t.add("filename", "Build Errors");
    t.add("path", "n/a");
    t.add("xlink", "");
    t.add("signposts", "");      
    t.add("vsumm", "");         
    return t.render();
  }

  private String genStartTxtInternal() {
    ST t = template(startTemplateText);
    t.add("link", INTERNAL_LINK);
    t.add("filename", "Build Errors");
    t.add("path", "n/a");
    return t.render();
  }

  private String genStartTxt(FetchedFile f) {
    ST t = template(startTemplateText);
    t.add("link", makelink(f));
    t.add("filename", f.getName());
    t.add("path", makeLocal(f.getPath()));
    return t.render();
  }
  
  private String genDetails(ValidationMessage vm, int id) {
    String tid = null;
    if (vm.isSlicingHint() || vm.hasSliceInfo()) {
      tid = detailsTemplateWithExtraDetails;
    } else if (vm.getLocationLink() != null) {
      tid = detailsTemplateWithLink;
    } else if (vm.getTxLink() != null) {
      if (vm.getDiagnostics() != null) {
        tid = detailsTemplateTxLinkDiagnostics;
      } else {
        tid = detailsTemplateTxLink;
      }
    } else if (vm.getServer() != null) {
      if (vm.getDiagnostics() != null) {
        tid = detailsTemplateTxNoLinkDiagnostics;
      } else {
        tid = detailsTemplateTxNoLink;
      }
    } else {
      tid = detailsTemplate;
    }
    ST t = template(tid);
    if (vm.getLocation()!=null) {
      t.add("path", makeBreakable(stripId(makeLocal(vm.getLocation())+lineCol(vm))));
      t.add("pathlink", vm.getLocationLink());
    } else {
      t.add("path", "");
      t.add("pathlink", "");      
    }

    t.add("level", vm.isSlicingHint() ? "Slicing Information" : vm.isSignpost() ? "Process Info" : vm.getLevel().toCode());
    t.add("color", colorForLevel(vm.getLevel(), vm.isSignpost()));
    t.add("halfcolor", halfColorForLevel(vm.getLevel(), vm.isSignpost()));
    t.add("id", "l"+id);
    t.add("mid", vm.getMessageId());
    t.add("diags", vm.getDiagnostics());
    t.add("vmid", "vmi"+(++vmid));
    t.add("msg", (isNewRule(vm) ? "<img style=\"vertical-align: text-bottom\" src=\"new.png\" height=\"16px\" width=\"36px\" alt=\"New Rule as of "+date(vm)+": \"> " : "")+ vm.getHtml());
    if (!vm.hasSliceInfo()) {      
      t.add("msgdetails",  vm.getHtml());
    } else if (Utilities.noString(vm.getSliceHtml())) {
      t.add("msgdetails", genSliceInfo(vm.getSliceInfo()));
    } else {
      t.add("msgdetails", vm.getSliceHtml());
      
    }
    t.add("comment", vm.getComment() == null ? "" : "<br/><br/><span style=\"display: block; border: 1px grey solid; border-radius: 5px; background-color: #eeeeee; padding: 3px; margin: 3px \"><i><b>Editor's Comment</b>: "+Utilities.escapeXml(vm.getComment())+"</i></span>");
    t.add("tx", "qa-tx.html#l"+vm.getTxLink());
    t.add("txsrvr", getServer(vm.getServer()));
    return t.render();
  }

  private Object genSliceInfo(List<ValidationMessage> sliceInfo) {
    StringBuilder b = new StringBuilder();
    b.append("<table class=\"grid\">");
    for (ValidationMessage vm : sliceInfo) {
      b.append("<tr><td>");
      b.append(Utilities.escapeXml(vm.getLocation()));
      b.append("</td><td>");
      b.append(vm.getLevel().toCode());
      b.append("</td><td>");
      b.append(Utilities.escapeXml(vm.getMessage()));
      b.append("</td></tr>");
    }
    b.append("</table>");
    return b.toString();
  }

  private String genDetails(String vp, int id) {
    String tid = null;
    tid = detailsTemplate;
    ST t = template(tid);
    t.add("path", "");
    t.add("pathlink", "");      
    t.add("level", "warning");
    t.add("color", colorForLevel(IssueSeverity.WARNING, false));
    t.add("halfcolor", halfColorForLevel(IssueSeverity.WARNING, false));
    t.add("id", "l"+id);
    t.add("mid", "");
    t.add("msg", vp);
    t.add("msgdetails", vp);
    t.add("comment", "");
    t.add("tx", "");
    t.add("txsrvr", "");

    return t.render();
  }

  private String getServer(String server) {
    if (txServers.getServerMap().containsKey(server)) {
      return txServers.getServerMap().get(server).getClient().getAddress();
    } else if (Utilities.noString(server)) {
      return "server";
    } else {
      return server;
    }
  }

  private Object makeBreakable(String path) {
    if (path == null || path.length() < MAX_PATH_LENGTH_NO_BREAK) {
      return path;
    } else {
      return path.replace("#", "&#8203;#").replace(".", "&#8203;.").replace("/", "&#8203;/");
    }
  }

  private boolean isNewRule(ValidationMessage vm) {
    return vm.getRuleDate() != null && !vm.getRuleDate().before(ruleDateCutoff);
  }

  private String date(ValidationMessage vm) {
    return vm.getRuleDate() != null  ? new SimpleDateFormat("YYYY-MM-DD").format(vm.getRuleDate()) : "????";
  }
  
  private String stripId(String loc) {
    if (loc.contains(": ")) {
      return loc.substring(loc.indexOf(": ")+2);
    }
    return loc;
  }

  private Object genGroupDetails(FetchedFile f, ValidationMessage vm) {
    ST t = template(groupDetailsTemplate);
    t.add("link", makelink(f));
    t.add("filename", f.getName());
    t.add("fpath", makeLocal(f.getPath()));
    String link = provider.getLinkFor(f.getResources().get(0), true);
    if (link==null) {
      link = altProvider.getLinkFor(f.getResources().get(0), true);
    }
    if (link != null) { 
      link = link.replace("{{[id]}}", f.getResources().get(0).getId());
      link = link.replace("{{[type]}}", f.getResources().get(0).fhirType());
    }
    
    t.add("xlink", link);
    if (vm.getLocation()!=null) {
      t.add("path", makeLocal(vm.getLocation())+lineCol(vm));
      t.add("pathlink", vm.getLocationLink());
    }
    t.add("level", vm.isSlicingHint() ? "Slicing Information" : vm.isSignpost() ? "Process Info" : vm.getLevel().toCode());
    t.add("color", colorForLevel(vm.getLevel(), vm.isSignpost()));
    t.add("halfcolor", halfColorForLevel(vm.getLevel(), vm.isSignpost()));
    t.add("msg", vm.getHtml());
    t.add("msgdetails", vm.isSlicingHint() ? vm.getSliceHtml() : vm.getHtml());
    t.add("comment", "");
    return t.render();
  }
  
  private String lineCol(ValidationMessage vm) {
    return vm.getLine() > 0 ? " (l"+vm.getLine()+"/c"+vm.getCol()+")" : "";
  }

  private String genDetailsTxt(ValidationMessage vm) {
    ST t = template(detailsTemplateText);
    t.add("path", vm.getLocation());
    t.add("level", vm.getLevel().toCode());
    t.add("color", colorForLevel(vm.getLevel(), vm.isSignpost()));
    t.add("msg", vm.getHtml());
    t.add("comment", "");
    return t.render();
  }

  private String colorForLevel(IssueSeverity level, boolean signpost) {
    if (signpost) {
      return "#d6feff";
    }
    switch (level) {
    case ERROR:
      return "#ffcccc";
    case FATAL:
      return "#ff9999";
    case WARNING:
      return "#ffebcc";
    default: // INFORMATION:
      return "#ffffe6";
    }
  }

  private String halfColorForLevel(IssueSeverity level, boolean signpost) {
    if (signpost) {
      return "#e3feff";
    }
    switch (level) {
    case ERROR:
      return "#ffeeee";
    case FATAL:
      return "#ffcccc";
    case WARNING:
      return "#fff4ee";
    default: // INFORMATION:
      return "#fffff2";
    }
  }

  @Override
  public int compare(FetchedFile f1, FetchedFile f2) {
    return f1.getName().compareTo(f2.getName());
  }

  public int getErr() {
    return err;
  }

  public int getWarn() {
    return warn;
  }

  public int getInfo() {
    return info;
  }  
  
  private String versionCheckText() {
    StringBuilder b = new StringBuilder();
    b.append("IG Publisher Version: ");
    b.append(toolsVersion);
    if (!toolsVersion.equals(currentToolsVersion)) {
      b.append(" Out of date - current version is ");
      b.append(currentToolsVersion);      
    }
    return b.toString();
  }

  private String versionCheckHtml() {
    StringBuilder b = new StringBuilder();
    if (toolsVersion.equals("DEV-VERSION")) {
      b.append("<span style=\"background-color: #ccffcc\">IG Publisher Version: ");
      b.append("Current Dev Version");
      b.append("</span>");
    } else {
      if (!toolsVersion.equals(currentToolsVersion)) {
        b.append("<span style=\"background-color: #ffcccc\">IG Publisher Version: ");
      } else {
        b.append("<span>IG Publisher Version: ");
      }
      b.append("v"+toolsVersion);
      if (!toolsVersion.equals(currentToolsVersion)) {
        if ("(unknown)".equals(toolsVersion)) {
          b.append(". Development Version in use. ");
        } else if (currentToolsVersion == null) {
          b.append(". The latest official release version is unknown");
        } else if (VersionUtilities.isThisOrLater(currentToolsVersion, toolsVersion, VersionUtilities.VersionPrecision.MINOR)) {
          b.append(" which is a development version. The latest official release version is");
          b.append("v" + currentToolsVersion);
        } else {
          b.append(", which is out of date. The current version is ");
          b.append("v" + currentToolsVersion);
        }
        b.append(" <a href=\"https://github.com/HL7/fhir-ig-publisher/releases/latest/download/publisher.jar\">Download Latest</a>");
      }
    }
    b.append("</span>");
    return b.toString();
  }

  public int getSuppressedInfo() {
    return suppressedInfo;
  }

  public int getSuppressedWarnings() {
    return suppressedWarnings;
  }  
}
