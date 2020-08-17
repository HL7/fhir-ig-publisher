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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.PreviousVersionComparator;
import org.hl7.fhir.igtools.publisher.realm.RealmBusinessRules;
import org.hl7.fhir.igtools.renderers.ValidationPresenter.FiledValidationMessage;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r5.model.Constants;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.utils.FHIRPathEngine;
import org.hl7.fhir.r5.utils.OperationOutcomeUtilities;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.r5.utils.TranslatingUtilities;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.i18n.I18nConstants;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.stringtemplate.v4.ST;

public class ValidationPresenter extends TranslatingUtilities implements Comparator<FetchedFile> {


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
  private String ballotCheck;
  private String toolsVersion;
  private String currentToolsVersion;
  private RealmBusinessRules realm;
  private PreviousVersionComparator previousVersionComparator;
  private String csAnalysis;

  public ValidationPresenter(String statedVersion, String igVersion, IGKnowledgeProvider provider, IGKnowledgeProvider altProvider, String root, String packageId, String altPackageId, String ballotCheck, 
      String toolsVersion, String currentToolsVersion, RealmBusinessRules realm, PreviousVersionComparator previousVersionComparator, String dependencies, String csAnalysis) {
    super();
    this.statedVersion = statedVersion;
    this.igVersion = igVersion;
    this.provider = provider;
    this.altProvider = altProvider;
    this.root = root;
    this.packageId = packageId;
    this.altPackageId = altPackageId;
    this.ballotCheck = ballotCheck;
    this.realm = realm;
    this.toolsVersion = toolsVersion;
    this.currentToolsVersion = currentToolsVersion;
    this.previousVersionComparator = previousVersionComparator;
    this.dependencies = dependencies;
    this.csAnalysis = csAnalysis;
  }

  private List<FetchedFile> sorted(List<FetchedFile> files) {
    List<FetchedFile> list = new ArrayList<FetchedFile>();
    list.addAll(files);
    Collections.sort(list, this);
    return list;
  }
  
  public String generate(String title, List<ValidationMessage> allErrors, List<FetchedFile> files, String path, Map<String, String> filteredMessages) throws IOException {
    
    for (FetchedFile f : files) {
      for (ValidationMessage vm : filterMessages(f.getErrors(), false, filteredMessages.keySet())) {
        if (vm.getLevel().equals(ValidationMessage.IssueSeverity.FATAL)||vm.getLevel().equals(ValidationMessage.IssueSeverity.ERROR))
          err++;
        else if (vm.getLevel().equals(ValidationMessage.IssueSeverity.WARNING))
          warn++;
        else if (!vm.isSignpost()) {
          info++;
        }
      }
    }
    
    List<ValidationMessage> linkErrors = filterMessages(allErrors, true, filteredMessages.keySet()); 
    for (ValidationMessage vm : linkErrors) {
      if (vm.getSource() == Source.LinkChecker) {
        link++;
      } else if (vm.getLevel().equals(ValidationMessage.IssueSeverity.FATAL)||vm.getLevel().equals(ValidationMessage.IssueSeverity.ERROR))
        err++;
      else if (vm.getLevel().equals(ValidationMessage.IssueSeverity.WARNING))
        warn++;
      else if (!vm.isSignpost()) {
        info++;
      }
    }
    
    files = genQAHtml(title, files, path, filteredMessages, linkErrors, false);
    files = genQAHtml(title, files, path, filteredMessages, linkErrors, true);

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
        ToolingExtensions.addStringExtension(oo, ToolingExtensions.EXT_OO_FILE, f.getName());
        for (ValidationMessage vm : filterMessages(f.getErrors(), false, filteredMessages.keySet())) {
          oo.getIssue().add(OperationOutcomeUtilities.convertToIssue(vm, oo));
        }
      }
    }
    FileOutputStream s = new FileOutputStream(Utilities.changeFileExt(path, ".xml"));
    new XmlParser().compose(s, validationBundle, true);
    s.close();

    genQAText(title, files, path, filteredMessages, linkErrors);
    
    String summary = "Errors: " + err + ", Warnings: " + warn + ", Info: " + info+", Broken Links = "+link;
    return path + "\r\n" + summary;
  }

  public void genQAText(String title, List<FetchedFile> files, String path, Map<String, String> filteredMessages, List<ValidationMessage> linkErrors)
      throws IOException {
    StringBuilder b = new StringBuilder();
    b.append(genHeaderTxt(title, err, warn, info));
    b.append(genSummaryRowTxtInternal(linkErrors));
    files = sorted(files);
    for (FetchedFile f : files) 
      b.append(genSummaryRowTxt(f));
    b.append(genEnd());
    b.append(genStartTxtInternal());
    for (ValidationMessage vm : linkErrors)
      b.append(vm.getDisplay() + "\r\n");
    b.append(genEndTxt());
    for (FetchedFile f : files) {
      b.append(genStartTxt(f));
      for (ValidationMessage vm : filterMessages(f.getErrors(), false, filteredMessages.keySet()))
        b.append(vm.getDisplay() + "\r\n");
      b.append(genEndTxt());
    }    
    b.append(genFooterTxt(title));
    TextFile.stringToFile(b.toString(), Utilities.changeFileExt(path, ".txt"));
  }

  public List<FetchedFile> genQAHtml(String title, List<FetchedFile> files, String path, Map<String, String> filteredMessages, List<ValidationMessage> linkErrors, boolean allIssues) throws IOException {
    StringBuilder b = new StringBuilder();
    b.append(genHeader(title, err, warn, info, link, filteredMessages.size(), allIssues, path));
    b.append(genSummaryRowInteral(linkErrors));
    files = sorted(files);
    for (FetchedFile f : files) {
      if (allIssues || hasIssues(f, filteredMessages)) {
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
    for (FetchedFile f : files) {
      if (allIssues || hasIssues(f, filteredMessages)) {
        b.append(genStart(f));
        if (f.getErrors().size() > 0)
          b.append(startTemplateErrors);
        else
          b.append(startTemplateNoErrors);
        for (ValidationMessage vm : filterMessages(f.getErrors(), false, filteredMessages.keySet())) {
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
    TextFile.stringToFile(b.toString(), allIssues ? path : Utilities.changeFileExt(path, ".min.html"));
    return files;
  }



  private boolean hasIssues(FetchedFile f, Map<String, String> filteredMessages) {
    List<ValidationMessage> uniqueErrors = filterMessages(f.getErrors(), false, filteredMessages.keySet());
    for (ValidationMessage vm : uniqueErrors) {
      if (vm.getLevel() != IssueSeverity.INFORMATION) {
        return true;
      }
    }
    return false;
  }

  private void getMatchingMessages(FetchedFile f, String n, List<FiledValidationMessage> fvml, Map<String, String> filteredMessages) {
    for (ValidationMessage vm : filterMessages(f.getErrors(), false, filteredMessages.keySet())) {
      if (n.equals(vm.getMessageId()) && !vm.isSignpost()) {
        fvml.add(new FiledValidationMessage(f, vm));
      }
    }
  }

  private List<String> messageIdNames() {
    I18nConstants obj = new I18nConstants();
    org.hl7.fhir.igtools.publisher.I18nConstants obj2 = new org.hl7.fhir.igtools.publisher.I18nConstants(); // not that it really matters?
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
    interfaceFields=org.hl7.fhir.igtools.publisher.I18nConstants.class.getFields();
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

  private String genSuppressedMessages(Map<String, String> msgs) {
    StringBuilder b = new StringBuilder();
    b.append("<a name=\"suppressed\"> </a>\r\n<p><b>Suppressed Messages (Warnings, hints, broken links)</b></p>\r\n");
    boolean found = false;
    Map<String, List<String>> inverted = new HashMap<>();
    for (Entry<String, String> e : msgs.entrySet()) {
      if (!inverted.containsKey(e.getValue())) {
        inverted.put(e.getValue(), new ArrayList<>());
      }
      inverted.get(e.getValue()).add(e.getKey());
    }
    for (String s : sorted(inverted.keySet())) {
      b.append("<p><b>"+Utilities.escapeXml(s)+"</b></p><ul>\r\n");
      for (String m : inverted.get(s)) {
        b.append(" <li>"+Utilities.escapeXml(m)+"</li>\r\n");
      }
      b.append("</ul>\r\n");
    }
    if (!found) {
      b.append("<p>No suppressed messsages</p>");
    }
    return b.toString();
  }

  private List<String> sorted(Set<String> keys) {
    List<String> list = new ArrayList<>();
    list.addAll(keys);
    Collections.sort(list);
    return list;
  }

  public static List<ValidationMessage> filterMessages(List<ValidationMessage> messages, boolean canSuppressErrors, Collection<String> suppressedMessages) {
    List<ValidationMessage> passList = new ArrayList<ValidationMessage>();
    Set<String> msgs = new HashSet<>();
    for (ValidationMessage message : messages) {
      boolean passesFilter = true;
      if (canSuppressErrors || !message.getLevel().isError()) {
        if (suppressedMessages.contains(message.getDisplay()) || suppressedMessages.contains(message.getMessage()) || suppressedMessages.contains(message.getMessageId()) ) {
          passesFilter = false;
        } else if (msgs.contains(message.getLocation()+"|"+message.getMessage())) {
          passesFilter = false;
        }
      }
      if (NO_FILTER) {
        passesFilter = true;
      }
      if (passesFilter) {
        passList.add(message);
        msgs.add(message.getLocation()+"|"+message.getMessage());        
      } else {
      }
    }
    return passList;
  }


  
  // HTML templating
  private final String headerTemplate = 
      "<!DOCTYPE HTML>\r\n"+
      "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\r\n"+
      "<!-- broken links = $links$, errors = $err$, warn = $warn$, info = $info$ -->\r\n"+
      "<head>\r\n"+
      "  <title>$title$ : Validation Results</title>\r\n"+
      "  <link href=\"fhir.css\" rel=\"stylesheet\"/>\r\n"+
      "  <style>\r\n"+
      "    span.flip  { background-color: #4CAF50; color: white; border: solid 1px #a6d8a8; padding: 2px }\r\n"+
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
      "  </script>\r\n"+
      "</head>\r\n"+
      "<body style=\"margin: 20px; background-color: #ffffff\">\r\n"+
      " <h1>Validation Results for $title$</h1>\r\n"+
      " <p>Generated $time$, FHIR version $version$ for $packageId$#$igversion$ (canonical = <a href=\"$canonical$\">$canonical$</a> (<a href=\"$canonical$/history.html\">history</a>)). See <a href=\"$otherFilePath$\">$otherFileName$</a></p>\r\n"+
      "<table class=\"grid\">"+
      " <tr><td colspan=2><b>Quality Checks</b></td></tr>\r\n"+
      " <tr><td>Publisher Version:</td><td>$versionCheck$</td></tr>\r\n"+
      " <tr><td>Supressed Messages:</td><td>$suppressedmsgssummary$</td></tr>\r\n"+
      " <tr><td>Dependency Checks:</td><td>$dependencyCheck$</td></tr>\r\n"+
      " <tr><td>HL7 Publication Rules:</td><td>$ballotCheck$</td></tr>\r\n"+
      " <tr><td>HTA Analysis:</td><td>$csAnalysis$</td></tr>\r\n"+
      " <tr><td>Realm rules:</td><td>$realmCheck$</td></tr>\r\n"+
      " <tr><td>Previous Version Comparison:</td><td> $previousVersion$</td></tr>\r\n"+
      " <tr><td>Summary:</td><td> broken links = $links$, errors = $err$, warn = $warn$, info = $info$</td></tr>\r\n"+
      "</table>\r\n"+
      " <table class=\"grid\">\r\n"+
      "   <tr>\r\n"+
      "     <td><b>Filename</b></td><td><b>Errors</b></td><td><b>Warnings</b></td><td><b>Hints</b></td>\r\n"+
      "   </tr>\r\n";
  
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
      "<h2><a href=\"$xlink$\">$path$</a></h2>\r\n"+
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
      "     <td><b>$path$</b></td><td><b>$level$</b></td><td title=\"$mid$\"><b>$msg$</b></td>\r\n"+
      "   </tr>\r\n";
  
  private final String groupDetailsTemplate = 
      "   <tr style=\"background-color: $halfcolor$\">\r\n"+
      "     <td><a href=\"$xlink$\">$fpath$</a></td><td><b>$msg$</b></td>\r\n"+
      "   </tr>\r\n";
  
  
  private final String detailsTemplateTx = 
      "   <tr style=\"background-color: $color$\">\r\n"+
      "     <td><b>$path$</b></td><td><b>$level$</b></td><td><b>$msg$</b> (<a href=\"$tx$\">see Tx log</a>)</td>\r\n"+
      "   </tr>\r\n";
  
  private final String detailsTemplateWithExtraDetails = 
      "   <tr style=\"background-color: $color$\">\r\n"+
      "     <td><b><a href=\"$pathlink$\">$path$</a></b></td><td><b>$level$</b></td><td><b>$msg$</b> <span id=\"s$id$\" class=\"flip\" onclick=\"flip('$id$')\">Show Reasoning</span><div id=\"$id$\" style=\"display: none\"><p>&nbsp;</p>$msgdetails$</div></td>\r\n"+
      "   </tr>\r\n";
      
  private final String detailsTemplateWithLink = 
      "   <tr style=\"background-color: $color$\">\r\n"+
      "     <td><b><a href=\"$pathlink$\">$path$</a></b></td><td><b>$level$</b></td><td><b>$msg$</b></td>\r\n"+
      "   </tr>\r\n";
  
  private final String footerTemplate = 
      "</body>\r\n"+
      "</html>\r\n";

  // Text templates
  private final String headerTemplateText = 
      "$title$ : Validation Results\r\n"+
      "=========================================\r\n\r\n"+
      "err = $err$, warn = $warn$, info = $info$\r\n"+
      "$versionCheck$\r\n"+
      "Generated $time$. FHIR version $version$ for $packageId$#$igversion$ (canonical = $canonical$)\r\n\r\n";
  
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
  private String dependencies;
  
  private ST template(String t) {
    return new ST(t, '$', '$');
  }

  private String genHeader(String title, int err, int warn, int info, int links, int msgCount, boolean allIssues, String path) {
    ST t = template(headerTemplate);
    t.add("version", statedVersion);
    t.add("igversion", igVersion);
    t.add("toolsVersion", toolsVersion);
    t.add("versionCheck", versionCheckHtml());
    t.add("title", title);
    t.add("time", new Date().toString());
    t.add("err", Integer.toString(err));
    t.add("warn", Integer.toString(warn));
    t.add("info", Integer.toString(info));
    t.add("links", Integer.toString(links));
    t.add("packageId", packageId);
    t.add("canonical", provider.getCanonical());
    t.add("ballotCheck", ballotCheck);
    t.add("realmCheck", realm.checkHtml());
    t.add("dependencyCheck", dependencies);
    t.add("csAnalysis", csAnalysis);
    t.add("otherFileName", allIssues ? "Errors Only" : "Full QA Report");
    t.add("otherFilePath", allIssues ? Utilities.getFileNameForName(Utilities.changeFileExt(path, ".min.html")) : Utilities.getFileNameForName(path));
    t.add("previousVersion", previousVersionComparator.checkHtml());
    if (msgCount == 0)
      t.add("suppressedmsgssummary", "No Suppressed Issues\r\n");
    else
      t.add("suppressedmsgssummary", "<a href=\"#suppressed\">"+msgCount+" Suppressed "+Utilities.pluralize("Issue", msgCount)+"</a>\r\n");
    return t.render();
  }

  private String genHeaderTxt(String title, int err, int warn, int info) {
    ST t = template(headerTemplateText);
    t.add("version", statedVersion);
    t.add("toolsVersion", toolsVersion);
    t.add("versionCheck", versionCheckText());
    t.add("igversion", igVersion);
    t.add("title", title);
    t.add("time", new Date().toString());
    t.add("err",  Integer.toString(err));
    t.add("warn",  Integer.toString(warn));
    t.add("info",  Integer.toString(info));
    t.add("packageId", packageId);
    t.add("canonical", provider.getCanonical());
    t.add("ballotCheck", ballotCheck);
    t.add("realmCheck", realm.checkText());
    t.add("dependencyCheck", dependencies);
    t.add("csAnalysis", csAnalysis);
    t.add("previousVersion", previousVersionComparator.checkHtml());
    return t.render();
  }

  private String genEnd() {
    ST t = template(endTemplate);
    t.add("version", Constants.VERSION);
    t.add("igversion", statedVersion);
    t.add("time", new Date().toString());
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
    t.add("time", new Date().toString());
    return t.render();
  }

  private String genFooter(String title) {
    ST t = template(footerTemplate);
    t.add("version", Constants.VERSION);
    t.add("igversion", statedVersion);
    t.add("title", title);
    t.add("time", new Date().toString());
    return t.render();
  }

  private String genFooterTxt(String title) {
    ST t = template(footerTemplateText);
    t.add("version", Constants.VERSION);
    t.add("igversion", statedVersion);
    t.add("title", title);
    t.add("time", new Date().toString());
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
    if ("0".equals(ec))
      t.add("color", "#EFFFEF");
    else
      t.add("color", colorForLevel(IssueSeverity.ERROR, false));
      
    return t.render();
  }

  private String genSummaryRow(FetchedFile f, Map<String, String> filteredMessages) {
    ST t = template(summaryTemplate);
    t.add("link", makelink(f));
    List<ValidationMessage> uniqueErrors = filterMessages(f.getErrors(), false, filteredMessages.keySet());
    
    t.add("filename", f.getName());
    String ec = errCount(uniqueErrors);
    t.add("errcount", ec);
    t.add("warningcount", warningCount(uniqueErrors));
    t.add("infocount", infoCount(uniqueErrors));
    if ("0".equals(ec))
      t.add("color", "#EFFFEF");
    else
      t.add("color", colorForLevel(IssueSeverity.ERROR, false));
      
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

  private String genStart(FetchedFile f) {
    ST t = template(startTemplate);
    t.add("link", makelink(f));
    t.add("filename", f.getName());
    t.add("path", makeLocal(f.getPath()));
    String link = provider.getLinkFor(f.getResources().get(0), true);
    if (link==null) {
      link = altProvider.getLinkFor(f.getResources().get(0), true);
    }
    if (link != null) { 
      link = link.replace("{{[id]}}", f.getResources().get(0).getId());
      link = link.replace("{{[type]}}", f.getResources().get(0).getElement().fhirType());
    }
    
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
    t.add("link", INTERNAL_LINK);
    t.add("filename", "Build Errors");
    t.add("path", "n/a");
    t.add("xlink", "");
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
    ST t = template(vm.isSlicingHint() ? detailsTemplateWithExtraDetails : vm.getLocationLink() != null ? detailsTemplateWithLink : vm.getTxLink() != null ? detailsTemplateTx : detailsTemplate);
    if (vm.getLocation()!=null) {
      t.add("path", makeLocal(vm.getLocation())+lineCol(vm));
      t.add("pathlink", vm.getLocationLink());
    }
    t.add("level", vm.isSlicingHint() ? "Slicing Information" : vm.isSignpost() ? "Process Info" : vm.getLevel().toCode());
    t.add("color", colorForLevel(vm.getLevel(), vm.isSignpost()));
    t.add("halfcolor", halfColorForLevel(vm.getLevel(), vm.isSignpost()));
    t.add("id", "l"+id);
    t.add("mid", vm.getMessageId());
    t.add("msg", vm.getHtml());
    t.add("msgdetails", vm.isSlicingHint() ? vm.getSliceHtml() : vm.getHtml());
    t.add("tx", "qa-tx.html#l"+vm.getTxLink());
    return t.render();
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
      link = link.replace("{{[type]}}", f.getResources().get(0).getElement().fhirType());
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
    if (!toolsVersion.equals(currentToolsVersion)) {
      b.append("<span style=\"background-color: #ffcccc\">IG Publisher Version: ");
    } else {
      b.append("<span>IG Publisher Version: ");
    }
    b.append("v"+toolsVersion);
    if (!toolsVersion.equals(currentToolsVersion)) {
      b.append(", which is out of date. The current version is ");
      b.append("v"+currentToolsVersion);      
      b.append(" <a href=\"https://github.com/HL7/fhir-ig-publisher/releases/latest/download/publisher.jar\">Download Latest</a>");
    }
    b.append("</span>");
    return b.toString();
  }
  
}
