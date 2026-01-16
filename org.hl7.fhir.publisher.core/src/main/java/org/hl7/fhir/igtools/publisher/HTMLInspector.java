package org.hl7.fhir.igtools.publisher;

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


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.annotation.Nonnull;

import lombok.Getter;
import lombok.Setter;

import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.PublisherBase.FragmentUseRecord;
import org.hl7.fhir.igtools.publisher.PublisherUtils.LinkedSpecification;
import org.hl7.fhir.igtools.publisher.SpecMapManager.SpecialPackageType;
import org.hl7.fhir.igtools.publisher.modules.IPublisherModule;
import org.hl7.fhir.r5.context.ILoggingService;
import org.hl7.fhir.r5.context.ILoggingService.LogCategory;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.LanguageUtils;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.formats.JsonCreator;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeType;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.utilities.*;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.hl7.fhir.utilities.xhtml.*;
import org.hl7.fhir.utilities.xhtml.XhtmlNode.Location;
import org.hl7.fhir.validation.BaseValidator.BooleanHolder;

public class HTMLInspector {
  public class XhtmlNodeHolder {
    XhtmlNode start;
    XhtmlNode end;
  }

  public enum ExternalReferenceType { FILE, WEB, IMG }

  public class ExternalReference {
    private ExternalReferenceType type;
    private String url;
    private XhtmlNode xhtml;
    private String source;
    public ExternalReference(ExternalReferenceType type, String url, XhtmlNode xhtml, String source) {
      super();
      this.type = type; 
      this.url = url;
      this.xhtml = xhtml;
      this.source = source;
    }
    public String getUrl() {
      return url;
    }
    public XhtmlNode getXhtml() {
      return xhtml;
    }
    public ExternalReferenceType getType() {
      return type;
    }
    public String getSource() {
      return source;
    }
  }

  public class DuplicateAnchorTracker {
    private Set<String> found = new HashSet<>();
    private Set<String> duplicates = new HashSet<>();

    public void seeAnchor(String tgt) {
      if (!found.contains(tgt)) {
        found.add(tgt);
      } else {
        duplicates.add(tgt);
      }
    }

    public boolean hasDuplicates() {
      return !duplicates.isEmpty();
    }

    public String listDuplicates() {
      return CommaSeparatedStringBuilder.join(",", Utilities.sorted(duplicates));
    }
  }


  public enum NodeChangeType {
    NONE, SELF, CHILD
  }

  public class HtmlChangeListenerContext {

    private List<ValidationMessage> messages;
    private String source;

    public HtmlChangeListenerContext(List<ValidationMessage> messages, String source) {
      this.messages = messages;
      this.source = source;
    }
  }

  //  public class HtmlSanitizerObserver implements HtmlChangeListener<HtmlChangeListenerContext> {
  //
  //    @Override
  //    public void discardedAttributes(HtmlChangeListenerContext ctxt, String elementName, String... attributeNames) {
  //      ctxt.messages.add(new ValidationMessage(Source.Publisher, IssueType.STRUCTURE, ctxt.source, "the element "+elementName+" attributes failed security testing", IssueSeverity.ERROR));
  //    }
  //
  //    @Override
  //    public void discardedTag(HtmlChangeListenerContext ctxt, String elementName) {
  //      ctxt.messages.add(new ValidationMessage(Source.Publisher, IssueType.STRUCTURE, ctxt.source, "the element "+elementName+" failed security testing", IssueSeverity.ERROR));
  //    }
  //  }

  public class StringPair {
    private String source;
    private String link;
    private String text;
    public StringPair(String source, String link, String text) {
      super();
      this.source = source;
      this.link = link;
      this.text = text;
    }
  }

  public class LoadedFile {
    String filename;
    private long lastModified;
    private int iteration;
    private Set<String> targets = new HashSet<String>();
    private Boolean hl7State;
    private boolean exempt;
    String path;
    private boolean hasXhtml;
    private int id = 0;
    private boolean isLangRedirect;
    public XhtmlNode xhtml;

    public LoadedFile(String filename, String path, long lastModified, int iteration, Boolean hl7State, boolean exempt, boolean hasXhtml) {
      this.filename = filename;
      this.lastModified = lastModified;
      this.iteration = iteration;
      this.hl7State = hl7State;
      this.path = path;
      this.exempt = exempt;
      this.hasXhtml = hasXhtml;
    }

    public long getLastModified() {
      return lastModified;
    }

    public int getIteration() {
      return iteration;
    }

    public void setIteration(int iteration) {
      this.iteration = iteration;
    }

    public boolean isExempt() {
      return exempt;
    }

    public Set<String> getTargets() {
      return targets;
    }

    public String getFilename() {
      return filename;
    }

    public Boolean getHl7State() {
      return hl7State;
    }

    public boolean isHasXhtml() {
      return hasXhtml;
    }

    public String getNextId() {
      id++;
      return Integer.toString(id );
    }

  }

  private static final String RELEASE_HTML_MARKER = "<!--ReleaseHeader--><p id=\"publish-box\">Publish Box goes here</p><!--EndReleaseHeader-->";
  private static final String START_HTML_MARKER = "<!--ReleaseHeader--><p id=\"publish-box\">";
  private static final String END_HTML_MARKER = "</p><!--EndReleaseHeader-->";
  public static final String TRACK_PREFIX = "<!--$$";
  public static final String TRACK_SUFFIX = "$$-->";
  private static final String PLAIN_LANG_INSERTION = "\n<!--PlainLangHeader--><div id=\"plain-lang-box\">Plain Language Summary goes here</div><script src=\"https://hl7.org/fhir/plain-lang.js\"></script><script type=\"application/javascript\" src=\"https://hl7.org/fhir/history-cm.js\"> </script><script>showPlainLanguage('{0}', '{1}', '{2}');</script><!--EndPlainLangHeader-->";
  
  private boolean strict;
  @Getter private String rootFolder;
  private String altRootFolder;
  private List<SpecMapManager> specs;
  private List<LinkedSpecification> linkSpecs;
  private Map<String, LoadedFile> cache = new HashMap<String, LoadedFile>();
  private int iteration = 0;
  private List<StringPair> otherlinks = new ArrayList<StringPair>();
  private int links;
  private List<String> manual = new ArrayList<String>(); // pages that will be provided manually when published, so allowed to be broken links
  private ILoggingService log;
  private boolean forHL7;
  private boolean requirePublishBox;
  private List<String> igs;
  private FilesystemPackageCacheManager pcm;
  private String canonical;
  private String statusMessageDef;
  private Map<String, String> statusMessagesLang;
  @Getter @Setter private List<String> exemptHtmlPatterns;
  private List<String> parseProblems = new ArrayList<>();
  private List<String> publishBoxProblems = new ArrayList<>();
  private Set<String> exceptions = new HashSet<>();
  private boolean referencesValidatorPack;
  private Map<String, List<String>> trackedFragments;
  private Set<String> foundFragments = new HashSet<>();
  private Map<String, String> visibleFragments = new HashMap<>();
  private List<FetchedFile> sources;
  private IPublisherModule module;
  private boolean isCIBuild;
  private Map<String, ValidationMessage> jsmsgs = new HashMap<>();
  private Map<String, FragmentUseRecord> fragmentUses = new HashMap<>();
  private List<RelatedIG> relatedIGs;
  private List<ExternalReference> externalReferences = new ArrayList<>(); 
  private Map<String, XhtmlNode> imageRefs = new HashMap<>();
  private Map<String, String> copyrights = new HashMap<>();
  private boolean noCIBuildIssues;
  private String packageId;
  private String version;
  private IWorkerContext context;
  private ConformanceStatementHandler csHandler;

  public HTMLInspector(IWorkerContext context, String rootFolder, List<SpecMapManager> specs, List<LinkedSpecification> linkSpecs, ILoggingService log, String canonical, String packageId, String version, Map<String, List<String>> trackedFragments, List<FetchedFile> sources, IPublisherModule module, boolean isCIBuild, Map<String, PublisherBase.FragmentUseRecord> fragmentUses, List<RelatedIG> relatedIGs, boolean noCIBuildIssues, List<String> langList) throws IOException {
    this.rootFolder = rootFolder.replace("/", File.separator);
    this.specs = specs;
    this.linkSpecs = linkSpecs;
    this.log = log;
    this.canonical = canonical;
    this.forHL7 = canonical.contains("hl7.org/fhir");
    this.trackedFragments = trackedFragments;
    this.sources = sources;
    this.module = module;
    this.isCIBuild = isCIBuild;
    this.fragmentUses = fragmentUses;
    this.relatedIGs = relatedIGs;
    this.noCIBuildIssues = noCIBuildIssues;
    this.packageId = packageId;
    this.version = version;
    requirePublishBox = Utilities.startsWithInList(packageId, "hl7.");
    this.context = context;
    this.csHandler = new ConformanceStatementHandler(this, context, log, langList, forHL7);
  }

  public void setAltRootFolder(String altRootFolder) throws IOException {
    this.altRootFolder = Utilities.path(rootFolder, altRootFolder.replace("/", File.separator));
  }
 
  public List<ValidationMessage> check(String statusMessageDef, Map<String, String> statusMessagesLang) throws IOException {
    csHandler.setup();
    this.statusMessageDef = statusMessageDef;
    this.statusMessagesLang = statusMessagesLang;
    iteration ++;

    List<ValidationMessage> messages = new ArrayList<ValidationMessage>();

    log.logDebugMessage(LogCategory.HTML, "CheckHTML: List files");
    // list new or updated files
    List<String> loadList = new ArrayList<>();
    listFiles(rootFolder, loadList);
    log.logMessage("found "+Integer.toString(loadList.size())+" files");

    checkGoneFiles();

    log.logDebugMessage(LogCategory.HTML, "Loading Files");
    // load files
    int i = 0;
    int c = loadList.size() / 40;
    for (String s : loadList) {
      loadFile(s, rootFolder, messages);
      if (i == c) {
        System.out.print(".");
        i = 0;
      }
      i++;
    }
    System.out.println();


    log.logDebugMessage(LogCategory.HTML, "Checking Resources");
    for (FetchedFile f : sources) {
      for (FetchedResource r : f.getResources()) {
        checkNarrativeLinks(f, r, Utilities.path(rootFolder, r.getPath()));
      }
    }
    log.logDebugMessage(LogCategory.HTML, "Checking Files");
    links = 0;
    // check links
    boolean first = true;
    i = 0;
    c = cache.size() / 40;
    for (String s : sorted(cache.keySet())) {
      log.logDebugMessage(LogCategory.HTML, "Check "+s);
      LoadedFile lf = cache.get(s);
      if (lf.getHl7State() != null && !lf.getHl7State()) {
        boolean check = true;
        for (String pattern : exemptHtmlPatterns ) {
          if (lf.path.matches(pattern)) {
            check = false;
            break;
          }
        }
        if (check && !lf.isExempt()) {
          if (requirePublishBox) {
            messages.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, s, "The html source does not contain the publish box" 
                + (first ? " "+RELEASE_HTML_MARKER+" (see note at https://confluence.hl7.org/spaces/FHIR/pages/66930646/FHIR+Implementation+Guide+Publishing+Requirements#FHIRImplementationGuidePublishingRequirements-PublishBox)" : ""), IssueSeverity.ERROR));
          } else if (first) {
            messages.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, s, "The html source does not contain the publish box; this is recommended for publishing support",
                "The html source does not contain the publish box; this is recommended for publishing support  (see note at https://confluence.hl7.org/spaces/FHIR/pages/66930646/FHIR+Implementation+Guide+Publishing+Requirements#FHIRImplementationGuidePublishingRequirements-PublishBox). Note that this is mandatory for HL7 specifications, and on the ci-build, but in other cases it's still recommended (this is only reported once, but applies for all pages)", IssueSeverity.INFORMATION));            

          }
          publishBoxProblems.add(s.substring(rootFolder.length()+1));
          first = false;
        }
      }
      checkFragmentIds(FileUtilities.fileToString(lf.filename));

      if (lf.isHasXhtml()) {
        if (fragmentUses != null) {
          checkFragmentMarkers(FileUtilities.fileToString(lf.getFilename()));
        }
        XhtmlNode x = new XhtmlParser().setMustBeWellFormed(strict).parse(new FileInputStream(lf.filename), null);
        BooleanHolder bh = new BooleanHolder(false);
        csHandler.readConformanceClauses(lf, x, bh, new BooleanHolder(false), messages);
        try {
          processAsMarkdown(lf, x);
        } catch (Exception e) {
          messages.add(new ValidationMessage(Source.Publisher, IssueType.INVALID, s, "Illegal HTML: "+e.getMessage(), IssueSeverity.WARNING));
        }
        referencesValidatorPack = false;
        DuplicateAnchorTracker dat = new DuplicateAnchorTracker();
        Stack<XhtmlNode> stack = new Stack<XhtmlNode>();
        checkVisibleFragments(lf.path, stack, x);
        if (checkLinks(lf, s, "", x, null, messages, false, dat, null) != NodeChangeType.NONE || bh.ok()) { // returns true if changed
          saveFile(lf, x);
        }
        if (dat.hasDuplicates()) {
          messages.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, s, "The html source has duplicate anchor Ids: "+dat.listDuplicates(), IssueSeverity.WARNING));                      
        }
        if (referencesValidatorPack) {
          if (lf.getHl7State() != null && lf.getHl7State()) {
            messages.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, s, "The html source references validator.pack which is deprecated. Change the IG to describe the use of the package system instead", IssueSeverity.ERROR));                      
          } else {
            messages.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, s, "The html source references validator.pack which is deprecated. Change the IG to describe the use of the package system instead", IssueSeverity.WARNING));                                  
          }
        }
      }
      if (i == c) {
        System.out.print(".");
        i = 0;
      }
      i++;
    }
    System.out.println();

    log.logDebugMessage(LogCategory.HTML, "Checking Other Links");
    // check other links:
    for (StringPair sp : otherlinks) {
      checkResolveLink(sp.source, null, null, sp.link, sp.text, messages, null, new XhtmlNode(NodeType.Element, "p").tx(sp.text), null);
    }

    log.logDebugMessage(LogCategory.HTML, "Sorting Conformance Clauses");
    csHandler.renderConfHomes();
    csHandler.generateRequirementsInstance(messages);
    log.logDebugMessage(LogCategory.HTML, "Done checking");

    for (String s : trackedFragments.keySet()) {
      if (!foundFragments.contains(s)) {
        if (trackedFragments.get(s).size() > 1) {
          messages.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, s, "An HTML fragment from the set "+trackedFragments.get(s)+" is not included anywhere in the produced implementation guide",
              "An HTML fragment from the set "+trackedFragments.get(s)+" is not included anywhere in the produced implementation guide", IssueSeverity.WARNING));
        } else {
          messages.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, s, "The HTML fragment '"+trackedFragments.get(s).get(0)+"' is not included anywhere in the produced implementation guide",
              "The HTML fragment '"+trackedFragments.get(s).get(0)+"' is not included anywhere in the produced implementation guide", IssueSeverity.WARNING));
        }
      }
    }
    return messages;
  }

  private void processAsMarkdown(LoadedFile lf, XhtmlNode x) throws IOException {
    XhtmlToMarkdownConverter conv = new XhtmlToMarkdownConverter(false);
    conv.getImageFilters().add("^tbl_.*");
    conv.getImageFilters().add("^icon_.*");
    conv.getImageFilters().add("^assets\\/*");
    conv.getImageFilters().add("^https://hl7\\.org/.*");
    conv.getImageFilters().add("^tree-filter");
    conv.getIdFilters().add("^segment-header");
    conv.getIdFilters().add("^segment-navbar");
    conv.getIdFilters().add("^ppprofile");
    conv.setIgnoreGeneratedTables(true);
    conv.setAiMode(true);
    conv.convert(x);
  }

  private void checkVisibleFragments(String pageName, Stack<XhtmlNode> stack, XhtmlNode x) {
    if (x.getNodeType() == NodeType.Comment) {
     String s = x.getContent();
     if (s != null && s.startsWith("$$")) {
       if (isVisible(stack)) {
         visibleFragments.put(s.replace("$", ""), pageName);
       }
     }
    } else {
      stack.push(x);
      for (XhtmlNode child : x.getChildNodes()) {
        checkVisibleFragments(pageName, stack, child);
      }
      stack.pop();
    }    
  }

  private boolean isVisible(Stack<XhtmlNode> stack) {
    for (XhtmlNode x : stack) {
      String style = x.getAttribute("style");
      if (style != null) {
        style = style.replace(" ", "");
        if (style.contains("display:none")) {
          return false;
        }
      }
    }
    return true;
  }

  private void checkFragmentMarkers(String s) {
    for (int i = 0; i < s.length() - 14; i++) {
      char ch = s.charAt(i);
      if (ch == '<') {
        if ("<!-- fragment:".equals(s.substring(i, i+14))) {
          String v = s.substring(i+14);
          int j = v.indexOf("-->");
          v = v.substring(0, j).trim();
          fragmentUses.get(v).setUsed();          
        }
      }
    }
  }

  private void checkNarrativeLinks(FetchedFile f, FetchedResource r, String filename) throws IOException {
    Element t = r.getElement().getNamedChild("text");
    if (t != null) {
      t = t.getNamedChild("div");
    }
    if (t != null) {
      XhtmlNode x = t.getXhtml();
      if (x != null) {
        checkReferences(f.getErrors(), r.fhirType()+".text.div", "div", x, filename, null);
        checkImgSources(f.getErrors(), r.fhirType()+".text.div", "div", x, filename);
      }
    }
  }

  private boolean checkReferences(List<ValidationMessage> errors, String path, String xpath, XhtmlNode node, String filename, XhtmlNode parent) throws IOException {
    boolean ok = true;
    if (node.getNodeType() == NodeType.Element & "a".equals(node.getName()) && node.getAttribute("href") != null) {
      String href = node.getAttribute("href");
      BooleanHolder bh = new BooleanHolder();
      if (!href.startsWith("#") && !isKnownTarget(href, bh, node, filename, parent)) {
        String msg = "Hyperlink '"+href+"' at '"+xpath+"' for '"+node.allText()+"' does not resolve";
        errors.add(new ValidationMessage(Source.Publisher, IssueType.INVALID, path, msg, msg, IssueSeverity.ERROR).setRuleDate("2024-02-08"));
      } else if (!bh.ok()) {
        String msg = "Hyperlink '"+href+"' at '"+xpath+"' for '"+node.allText()+"' is a canonical reference, and is unsafe because of version handling (points to the current version, not this version)";
        errors.add(new ValidationMessage(Source.Publisher, IssueType.INVALID, path, msg, msg, IssueSeverity.WARNING).setRuleDate("2024-02-09"));
      }
    }
    if (node.hasChildren()) {
      for (XhtmlNode child : node.getChildNodes()) {
        checkReferences(errors, path, xpath+"/"+child.getName(), child, filename, node);
      }        
    }
    return ok;
  }

  private boolean checkImgSources(List<ValidationMessage> errors, String path, String xpath, XhtmlNode node, String filename) throws IOException {
    boolean ok = true;
    if (node.getNodeType() == NodeType.Element & "img".equals(node.getName()) && node.getAttribute("src") != null) {
      String src = node.getAttribute("src");
      if (!src.startsWith("#") && !checkImgSourceExists(filename, src, node)) {
        String msg = "Img Source '"+src+"' at '"+xpath+(node.hasAttribute("alt") ? "' for '"+node.getAttribute("alt")+"'" : "")+" does not resolve";
        errors.add(new ValidationMessage(Source.Publisher, IssueType.INVALID, path, msg, msg, IssueSeverity.ERROR).setRuleDate("2024-02-08"));
      }
    }
    if (node.hasChildren()) {
      for (XhtmlNode child : node.getChildNodes()) {
        checkImgSources(errors, path, xpath+"/"+child.getName(), child, filename);
      }        
    }
    return ok;
  }


  private boolean isKnownTarget(String target, BooleanHolder bh, XhtmlNode x, String filename, XhtmlNode parent) throws IOException {
    return checkTarget(filename, target, target, new StringBuilder(), bh, x, parent);
  }

  private boolean isKnownImgTarget(String target) {
    if (target.contains("#")) {
      return false;
    }
    return false;
  }

  private void checkFragmentIds(String src) {
    int s = src.indexOf(TRACK_PREFIX);
    while (s > -1) {
      src = src.substring(s+TRACK_PREFIX.length());
      int e = src.indexOf(TRACK_SUFFIX);
      foundFragments.add(src.substring(0, e));
      s = src.indexOf(TRACK_PREFIX);
    }    
  }

  private List<String> sorted(Set<String> keys) {
    List<String> res = new ArrayList<>();
    res.addAll(keys);
    Collections.sort(res);
    return res;
  }

  public void saveFile(LoadedFile lf, XhtmlNode x) throws IOException {
    new File(lf.getFilename()).delete();
    FileOutputStream f = new FileOutputStream(lf.getFilename());
    new XhtmlComposer(XhtmlComposer.HTML).composeDocument(f, x);
    f.close();
  }

  private void checkGoneFiles() {
    List<String> td = new ArrayList<String>();
    for (String s : cache.keySet()) {
      LoadedFile lf = cache.get(s);
      if (lf.getIteration() != iteration)
        td.add(s);
    }
    for (String s : td)
      cache.remove(s);
  }

  private void listFiles(String folder, List<String> loadList) {
    for (File f : new File(folder).listFiles()) {
      if (!Utilities.startsWithInList(f.getAbsolutePath(), exceptions)) {
        if (f.isDirectory()) {
          listFiles(f.getAbsolutePath(), loadList);
        } else {
          LoadedFile lf = cache.get(f.getAbsolutePath());
          if (lf == null || lf.getLastModified() != f.lastModified())
            loadList.add(f.getAbsolutePath());
          else
            lf.setIteration(iteration);
        }
      }
    }
  }

  private void loadFile(String s, String base, List<ValidationMessage> messages) {
    log.logDebugMessage(LogCategory.HTML, "Load "+s);

    File f = new File(s);
    Boolean hl7State = null;
    XhtmlNode x = null;
    boolean htmlName = f.getName().endsWith(".html") || f.getName().endsWith(".xhtml") || f.getName().endsWith(".svg");
    try {
      x = new XhtmlParser().setMustBeWellFormed(strict).parse(new FileInputStream(f), null);
      if (x.getElement("html")==null && x.getElement("svg")==null && !htmlName) {
        // We don't want resources being treated as HTML.  We'll check the HTML of the narrative in the page representation
        x = null;
      }
    } catch (FHIRFormatError | IOException e) {
      x = null;
      if (htmlName || !(e.getMessage().startsWith("Unable to Parse HTML - does not start with tag.") || e.getMessage().startsWith("Malformed XHTML"))) {
        messages.add(new ValidationMessage(Source.LinkChecker, IssueType.STRUCTURE, s, e.getMessage(), IssueSeverity.ERROR).setLocationLink(makeLocal(f.getAbsolutePath())).setMessageId("HTML_PARSING_FAILED"));
        parseProblems.add(f.getName());
      }
    }
    boolean lr = false;
    if (x != null) {

      String src;
      try {
        src = FileUtilities.fileToString(f);
        lr = src.contains("lang-redirects.js");
        hl7State = src.contains(RELEASE_HTML_MARKER);
        if (hl7State) {
          String msg = statusMessageDef;
          String lang = getLang(x);
          if (statusMessagesLang.containsKey(lang)) {
            msg = statusMessagesLang.get(lang);
          }
          src = src.replace(RELEASE_HTML_MARKER, START_HTML_MARKER + msg + END_HTML_MARKER);
          if (packageId.startsWith("hl7.") && f.getName().equals("index.html") && isCIBuild) {
            int index = src.indexOf(END_HTML_MARKER) + END_HTML_MARKER.length();
            String pl = PLAIN_LANG_INSERTION;
            pl = pl.replace("{0}", packageId);
            pl = pl.replace("{1}", version);
            pl = pl.replace("{2}", HierarchicalTableGenerator.uuid);
            src = src.substring(0, index) + pl + src.substring(index);
          }
          FileUtilities.stringToFile(src, f);
        }
        x = new XhtmlParser().setMustBeWellFormed(strict).parse(new FileInputStream(f), null);
      } catch (Exception e1) {
        hl7State = false;
      }
    }
    LoadedFile lf = new LoadedFile(s, getPath(s, base), f.lastModified(), iteration, hl7State, findExemptionComment(x) || Utilities.existsInList(f.getName(), "searchform.html"), x != null);
    lf.isLangRedirect = lr;
    cache.put(s, lf);
    if (x != null && x.getElement("svg") != null) {
      lf.exempt = true;
    }
    if (x != null) {
      checkHtmlStructure(s, x, messages);
      listTargets(x, lf.getTargets());
      if (forHL7 & !isRedirect(x)) {
        checkTemplatePoints(x, messages, s);
      }
    }

    // ok, now check for XSS safety:
    // this is presently disabled; it's not clear whether oWasp is worth trying out for the purpose we are seeking (XSS safety)

    //    
    //    HtmlPolicyBuilder pp = new HtmlPolicyBuilder();
    //    pp
    //      .allowStandardUrlProtocols().allowAttributes("title").globally() 
    //      .allowElements("html", "head", "meta", "title", "body", "span", "link", "nav", "button")
    //      .allowAttributes("xmlns", "xml:lang", "lang", "charset", "name", "content", "id", "class", "href", "rel", "sizes", "data-no-external", "target", "data-target", "data-toggle", "type", "colspan").globally();
    //    
    //    PolicyFactory policy = Sanitizers.FORMATTING.and(Sanitizers.LINKS).and(Sanitizers.BLOCKS).and(Sanitizers.IMAGES).and(Sanitizers.STYLES).and(Sanitizers.TABLES).and(pp.toFactory());
    //    
    //    String source;
    //    try {
    //      source = TextFile.fileToString(s);
    //      HtmlChangeListenerContext ctxt = new HtmlChangeListenerContext(messages, s);
    //      String sanitized = policy.sanitize(source, new HtmlSanitizerObserver(), ctxt);
    //    } catch (IOException e) {
    //      messages.add(new ValidationMessage(Source.Publisher, IssueType.STRUCTURE, s, "failed security testing: "+e.getMessage(), IssueSeverity.ERROR));
    //    } 
  }

  private String getLang(XhtmlNode x) {
    if (!"html".equals(x.getName())) {
      for (XhtmlNode t : x.getChildNodes()) {
        if ("html".equals(t.getName())) {
          x = t;
          break;
        }
      }
    }
    if (x.getAttribute("lang") != null) {
      return x.getAttribute("lang");
    }
    if (x.getAttribute("xml:lang") != null) {
      return x.getAttribute("xml:lang");
    }
    return null;
  }

  private String getPath(String s, String base) {
    String t = s.substring(base.length()+1);
    return t.replace("\\", "/");
  }

  private boolean isRedirect(XhtmlNode x) {
    return !hasHTTPRedirect(x);
  }

  private boolean hasHTTPRedirect(XhtmlNode x) {
    if ("meta".equals(x.getName()) && x.hasAttribute("http-equiv"))
      return true;
    for (XhtmlNode c : x.getChildNodes())
      if (hasHTTPRedirect(c))
        return true;
    return false;
  }

  private void checkTemplatePoints(XhtmlNode x, List<ValidationMessage> messages, String s) {
    // look for a footer: a div tag with igtool=footer on it 
    XhtmlNode footer = findFooterDiv(x);
    if (footer == null && !findExemptionComment(x)) 
      messages.add(new ValidationMessage(Source.Publisher, IssueType.STRUCTURE, s, "The html must include a div with an attribute igtool=\"footer\" that marks the footer in the template", IssueSeverity.ERROR));
    else {
      // look in the footer for: .. nothing yet... 
    }
  }

  private boolean findExemptionComment(XhtmlNode x) {
    if (x == null) {
      return false;
    }
    for (XhtmlNode c : x.getChildNodes()) {
      if (c.getNodeType() == NodeType.Comment && x.getContent() != null && x.getContent().trim().equals("frameset content")) {
        return true;
      }
      if (c.getNodeType() == NodeType.Comment && x.getContent() != null && x.getContent().trim().equals("no-publish-box-ok")) {
        return true;
      }
    }
    return false;
  }

  private XhtmlNode findFooterDiv(XhtmlNode x) {
    if (x.getNodeType() == NodeType.Element && "footer".equals(x.getAttribute("igtool")))
      return x;
    for (XhtmlNode c : x.getChildNodes()) {
      XhtmlNode n = findFooterDiv(c);
      if (n != null)
        return n;
    }
    return null;
  }

  private boolean findStatusBarComment(XhtmlNode x) {
    if (x.getNodeType() == NodeType.Comment && "status-bar".equals(x.getContent().trim()))
      return true;
    for (XhtmlNode c : x.getChildNodes()) {
      if (findStatusBarComment(c))
        return true;
    }
    return false;
  }

  private void checkHtmlStructure(String s, XhtmlNode x, List<ValidationMessage> messages) {
    if (x.getNodeType() == NodeType.Document)
      x = x.getFirstElement();
    if (!"html".equals(x.getName()) && !"div".equals(x.getName()) && !"svg".equals(x.getName())) {
      messages.add(new ValidationMessage(Source.Publisher, IssueType.STRUCTURE, s, "Root node must be 'html' or 'div', but is "+x.getName(), IssueSeverity.ERROR));
    }
    // We support div as well because with HTML 5, referenced files might just start with <div>
    // todo: check secure?

  }

  private void listTargets(XhtmlNode x, Set<String> targets) {
    if ("svg".equals(x.getName())) {
      return;
    }
    if ("a".equals(x.getName()) && x.hasAttribute("name"))
      targets.add(x.getAttribute("name"));
    if (x.hasAttribute("id"))
      targets.add(x.getAttribute("id"));
    if (Utilities.existsInList(x.getName(), "h1", "h2", "h3", "h4", "h5", "h6")) {
      if (x.allText() != null) {
        targets.add(urlify(x.allText()));
      }
    }
    for (XhtmlNode c : x.getChildNodes()) {
      listTargets(c, targets);
    }
  }

  private NodeChangeType checkLinks(LoadedFile lf, String s, String path, XhtmlNode x, String uuid, List<ValidationMessage> messages, boolean inPre, DuplicateAnchorTracker dat, XhtmlNode parent) throws IOException {
    boolean changed = false;
    if (x.getName() != null) {
      path = path + "/"+ x.getName();
    } else {
      if (x.getContent() != null && x.getContent().contains("validator.pack")) {
        referencesValidatorPack = true;
      }
    }
    if (x.getNodeType() == NodeType.Text) {
      String tx = x.allText().toUpperCase();
      if (tx.contains("©") || tx.contains("®") || tx.contains("(R)") || tx.contains("(TM)") || tx.contains("(C)") ) {
        copyrights.put(parent == null ? tx : parent.allText(), FileUtilities.getRelativePath(rootFolder, lf.filename));
      }
    }
    if ("title".equals(x.getName()) && Utilities.noString(x.allText())) {
      x.addText("?html-link?");
    }
    if ("a".equals(x.getName()) && x.hasAttribute("href")) {
      changed = checkResolveLink(s, x.getLocation(), path, x.getAttribute("href"), x.allText(), messages, uuid, x, parent);
    }
    if ("a".equals(x.getName()) && x.hasAttribute("name")) {
      dat.seeAnchor(x.getAttribute("name"));
    }
    if ("img".equals(x.getName()) && x.hasAttribute("src")) {
      changed = checkResolveImageLink(s, x.getLocation(), path, x.getAttribute("src"), messages, uuid, x) || changed;
    }
    if ("area".equals(x.getName()) && x.hasAttribute("href")) {
      changed = checkResolveLink(s, x.getLocation(), path, x.getAttribute("href"), x.getAttribute("coords"), messages, uuid, x, parent) || changed;
    }
    if ("link".equals(x.getName())) {
      changed = checkLinkElement(s, x.getLocation(), path, x.getAttribute("href"), messages, uuid) || changed;
    }
    if ("script".equals(x.getName())) {
      checkScriptElement(s, x.getLocation(), path, x, messages);
    } 
    String nuid = genID(lf);
    boolean nchanged = false;
    boolean nSelfChanged = false;
    for (XhtmlNode c : x.getChildNodes()) { 
      NodeChangeType ct = checkLinks(lf, s, path, c, nuid, messages, inPre || "pre".equals(x.getName()), dat, x);
      if (ct == NodeChangeType.SELF) {
        nSelfChanged = true;
        nchanged = true;
      } else if (ct == NodeChangeType.CHILD) {
        nchanged = true;
      }      
    }
    if (nSelfChanged) {
      XhtmlNode a = new XhtmlNode(NodeType.Element);
      a.setName("a").setAttribute("name", nuid).addText("\u200B");
      x.addChildNode(0, a);
    } 
    if (changed)
      return NodeChangeType.SELF;
    else if (nchanged)
      return NodeChangeType.CHILD;
    else
      return NodeChangeType.NONE;
  }

  public String genID(LoadedFile lf) {
    return "l"+lf.getNextId(); // UUID.randomUUID().toString().toLowerCase();
  }

  private void checkScriptElement(String filename, Location loc, String path, XhtmlNode x, List<ValidationMessage> messages) {
    String src = x.getAttribute("src");
    if (!Utilities.noString(src) && Utilities.isAbsoluteUrl(src) && !Utilities.existsInList(src, 
        "https://hl7.org/fhir/history-cm.js", "https://hl7.org/fhir/assets-hist/js/jquery.js", "https://hl7.org/fhir/plain-lang.js") && !src.contains("googletagmanager.com")) {
      messages.add(new ValidationMessage(Source.Publisher, IssueType.INVALID, filename+(loc == null ? "" : " at "+loc.toString()), "The <script> src '"+src+"' is illegal", IssueSeverity.FATAL));
    } else if (src == null && x.allText() != null && !x.allText().contains(HierarchicalTableGenerator.uuid)) {
      String js = x.allText();
      if (jsmsgs.containsKey(js)) {
        ValidationMessage vm = jsmsgs.get(js);
        vm.incCount();
      } else {
        ValidationMessage vm;
        if (isCIBuild) {
          vm = new ValidationMessage(Source.Publisher, IssueType.INVALID, filename+(path == null ? "" : "#"+path+(loc == null ? "" : " at "+loc.toString())), "The <script> tag in the file '"+filename+"' containing the javascript '"+subset(x.allText())+"'... is illegal - put the script in a  .js file in a trusted template (if it is justified and needed)", IssueSeverity.FATAL);
        } else if (forHL7) {
          vm =  new ValidationMessage(Source.Publisher, IssueType.INVALID, filename+(path == null ? "" : "#"+path+(loc == null ? "" : " at "+loc.toString())), "The <script> containing the javascript '"+subset(x.allText())+"'... is illegal and not allowed on the HL7 ci-build - put the script in a  .js file in a trusted template (if it is justified and needed)", IssueSeverity.ERROR);
        } else if (noCIBuildIssues) {
          vm = null;
        } else {
          vm =  new ValidationMessage(Source.Publisher, IssueType.INVALID, filename+(path == null ? "" : "#"+path+(loc == null ? "" : " at "+loc.toString())), "The <script> containing the javascript '"+subset(x.allText())+"'... is illegal and not allowed on the HL7 ci-build - need to put the script in a  .js file in a trusted template if this IG is to build on the HL7 ci-build (if it is justified and needed)", IssueSeverity.WARNING);
        }
        if (vm != null) {
          messages.add(vm);
          jsmsgs.put(js, vm);
        }
      }
    }
  }

  private String subset(String src) {
    src = Utilities.stripEoln(src.strip()).replace("\"", "'");
    return src.length() < 20 ? src : src.substring(0, 20);
  }

  private boolean checkLinkElement(String filename, Location loc, String path, String href, List<ValidationMessage> messages, String uuid) {
    if (Utilities.isAbsoluteUrl(href) && !href.startsWith("http://hl7.org/") && !href.startsWith("http://cql.hl7.org/")) {
      messages.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, filename+(loc == null ? "" : " at "+loc.toString()), "The <link> href '"+href+"' is llegal", IssueSeverity.FATAL).setLocationLink(uuid == null ? null : filename+"#"+uuid));
      return true;        
    } else
      return false;
  }

  private boolean checkResolveLink(String filename, Location loc, String path, String ref, String text, List<ValidationMessage> messages, String uuid, XhtmlNode x, XhtmlNode parent) throws IOException {
    links++;
    String rref = Utilities.URLDecode(ref);
    if ((rref.startsWith("http:") || rref.startsWith("https:") ) && (rref.endsWith(".sch") || rref.endsWith(".xsd") || rref.endsWith(".shex"))) { // work around for the fact that spec.internals does not track all these minor things 
      rref = FileUtilities.changeFileExt(ref, ".html");
    }
    if (rref.contains("validator.pack")) {
      referencesValidatorPack = true;
    }

    StringBuilder tgtList = new StringBuilder();
    BooleanHolder bh = new BooleanHolder();
    boolean resolved = checkTarget(filename, ref, rref, tgtList, bh, x, parent);
    if (!resolved) {
      if (text == null)
        text = "";
      String fn = makeLocal(filename);
      messages.add(new ValidationMessage(Source.LinkChecker, IssueType.NOTFOUND, fn+(path == null ? "" : "#"+path+(loc == null ? "" : " at "+loc.toString())),
          "The link '"+ref+"' for \""+text.replaceAll("[\\s\\n]+", " ").trim()+"\" cannot be resolved"+tgtList, IssueSeverity.ERROR).setLocationLink(uuid == null ? null : fn+"#"+uuid).setMessageId("HTML_LINK_CHECK_FAILED"));
      return true;
    } else if (!bh.ok()) {
      if (text == null)
        text = "";
      String fn = makeLocal(filename);
      messages.add(new ValidationMessage(Source.LinkChecker, IssueType.NOTFOUND, fn+(path == null ? "" : "#"+path+(loc == null ? "" : " at "+loc.toString())),
          "The link '"+ref+"' for \""+text.replaceAll("[\\s\\n]+", " ").trim()+"\" is a canonical link and is therefore unsafe with regard to versions", IssueSeverity.WARNING).setLocationLink(uuid == null ? null : fn+"#"+uuid).setMessageId("HTML_LINK_VERSIONLESS_CANONICAL"));
      return false;
    } else {
      return false;
    }
  }

  private boolean checkTarget(String filename, String ref, String rref, StringBuilder tgtList, BooleanHolder bh, XhtmlNode x, XhtmlNode parent) throws IOException {
    if (rref.startsWith("./")) {
      rref = rref.substring(2);
    }
    if (rref.endsWith("/")) {
      rref = rref.substring(0, rref.length()-1);
    }

    if (ref.startsWith("data:")) {
      return true;
    }
    boolean isSubFolder = filename.substring(rootFolder.length()+1).contains(File.separator);
    String subFolder = null;
    if (isSubFolder) {
      subFolder = filename.substring(rootFolder.length()+1);
      subFolder = subFolder.substring(0, subFolder.lastIndexOf(File.separator));
    }
    // full-ig.zip doesn't exist yet
    if (subFolder == null) {
      if ("full-ig.zip".equals(ref)) {
        return true;
      }
    } else {
      if ("../full-ig.zip".equals(ref)) {
        return true;
      }
    }
    if (Utilities.existsInList(ref, isSubFolder ? "../qa.html" : "qa.html",
        "http://hl7.org/fhir", "http://hl7.org", "http://www.hl7.org", "http://hl7.org/fhir/search.cfm") || 
        ref.startsWith("http://gforge.hl7.org/gf/project/fhir/tracker/") || ref.startsWith("mailto:") || ref.startsWith("javascript:")) {
      return true;
    }
    if (forHL7 && Utilities.pathURL(canonical, "history.html").equals(ref) || ref.equals("searchform.html")) {
      return true;
    }
    if (filename != null && filename.contains("searchform.html") && ref.equals("history.html")) {
      return true;
    }
    if (manual.contains(rref)) {
      return true;
    }
    if (rref.startsWith("http://build.fhir.org/ig/FHIR/fhir-tools-ig") || rref.startsWith("http://build.fhir.org/ig/FHIR/ig-guidance")) { // always allowed to refer to tooling or IG Guidance IG build location
      return true;
    }
    if (specs != null){
      for (SpecMapManager spec : specs) {
        if (spec.getBase() != null && (spec.getBase().equals(rref) || (spec.getBase()).equals(rref+"/") || (spec.getBase()+"/").equals(rref)|| spec.hasTarget(rref) ||
              Utilities.existsInList(rref, Utilities.pathURL(spec.getBase(), "definitions.json.zip"), 
                  Utilities.pathURL(spec.getBase(), "full-ig.zip"), Utilities.pathURL(spec.getBase(), "definitions.xml.zip"), 
                  Utilities.pathURL(spec.getBase(), "package.tgz"), Utilities.pathURL(spec.getBase(), "history.html")))) {
          return true;
        }
        if (spec.getBase2() != null && (spec.getBase2().equals(rref) || (spec.getBase2()).equals(rref+"/") ||
              Utilities.existsInList(rref, Utilities.pathURL(spec.getBase2(), "definitions.json.zip"), Utilities.pathURL(spec.getBase2(), "definitions.xml.zip"), Utilities.pathURL(spec.getBase2(), "package.tgz"), Utilities.pathURL(spec.getBase2(), "full-ig.zip")))) {
          return true;
        }
      }
    }
    if (linkSpecs != null){
      for (LinkedSpecification spec : linkSpecs) {
        if (spec.getSpm().getBase() != null && (spec.getSpm().getBase().equals(rref) || (spec.getSpm().getBase()).equals(rref+"/") || (spec.getSpm().getBase()+"/").equals(rref)|| spec.getSpm().hasTarget(rref) || Utilities.existsInList(rref, Utilities.pathURL(spec.getSpm().getBase(), "history.html")))) {
          return true;
        }
        if (spec.getSpm().getBase2() != null && (spec.getSpm().getBase2().equals(rref) || (spec.getSpm().getBase2()).equals(rref+"/"))) {
          return true;
        }
      }
    }

    for (RelatedIG ig : relatedIGs) {
      if (ig.getWebLocation() != null && rref.startsWith(ig.getWebLocation())) {
        String tref = rref.substring(ig.getWebLocation().length());
        if (tref.startsWith("/")) {
          tref = tref.substring(1);
          if (ig.getSpm().getBase().equals(rref) || (ig.getSpm().getBase()).equals(rref+"/") || (ig.getSpm().getBase()+"/").equals(rref)|| ig.getSpm().hasTarget(rref) || Utilities.existsInList(rref, Utilities.pathURL(ig.getSpm().getBase(), "history.html"))) {
            return true;
          }
        } else if ("".equals(tref)) {
          return true;
        }
      }
    }

    if (Utilities.isAbsoluteFileName(ref)) {
      if (new File(ref).exists()) {
        addExternalReference(ExternalReferenceType.FILE, ref, parent == null ? x : parent, filename);
        return true;
      }
    } else if (!Utilities.isAbsoluteUrl(ref) && !rref.startsWith("#") && filename != null) {
      String fref =  buildRef(FileUtilities.getDirectoryForFile(filename), ref);
      if (fref.equals(Utilities.path(rootFolder, "qa.html"))) {
        return true;
      }
    }
    // special case end-points that are always valid:
    if (Utilities.existsInList(ref, "http://hl7.org/fhir/fhir-spec.zip", "http://hl7.org/fhir/R4/fhir-spec.zip", "http://hl7.org/fhir/STU3/fhir-spec.zip", "http://hl7.org/fhir/DSTU2/fhir-spec.zip",
          "http://hl7.org/fhir-issues", "http://hl7.org/registry") || 
      matchesTarget(ref, "http://hl7.org", "http://hl7.org/fhir/DSTU2", "http://hl7.org/fhir/STU3", "http://hl7.org/fhir/R4", "http://hl7.org/fhir/smart-app-launch", "http://hl7.org/fhir/validator")) {
      return true;
    }

    if (ref.startsWith("https://build.fhir.org/ig/FHIR/ig-guidance/readingIgs.html")) { // updated table documentation
      return true;
    }

    // a local file may have been created by some poorly tracked process, so we'll consider that as a possible
    if (!Utilities.isAbsoluteUrl(rref) && !rref.contains("..") && filename != null) { // .. is security check. Maybe there's some ways it could be valid, but we're not interested for now
      String fname = buildRef(new File(filename).getParent(), rref);
      if (new File(fname).exists()) {
        return true;
      }
    }

    // external terminology resources 
    if (Utilities.startsWithInList(ref, "http://cts.nlm.nih.gov/fhir")) {
      return true;
    }

      if (rref.startsWith("http://") || rref.startsWith("https://") || rref.startsWith("ftp://") || rref.startsWith("tel:") || rref.startsWith("urn:")) {
        boolean resolved = true;
        addExternalReference(ExternalReferenceType.WEB, ref, parent == null ? x : parent, filename);
        if (rref.startsWith(canonical)) {
          if (rref.equals(canonical)) {
            resolved = true;
            bh.fail();
          } else {
            resolved = false;
            for (FetchedFile f : sources)  {
              for (FetchedResource r : f.getResources()) {
                if (r.getResource() != null && r.getResource() instanceof CanonicalResource && rref.equals(((CanonicalResource) r.getResource()).getUrl())) {
                  resolved = true;
                  bh.fail();
                  break;
                }
              }
            }
            if ("http://hl7.org.au/fhir".equals(ref)) {
              // special case because au - wrongly - posts AU Base at http://hl7.org.au/fhir
              resolved = true;
            }
          }
        } else if (specs != null) {
          for (SpecMapManager spec : specs) {
            if (spec.getSpecial() != SpecialPackageType.Examples && spec.getBase() != null && rref.startsWith(spec.getBase())) {
              resolved = false;
            }
          }
        }
        return resolved;
      } else if (!Utilities.isAbsoluteFileName(rref)) { 
        String page = rref;
        String name = null;
        if (page.startsWith("#")) {
          name = page.substring(1);
          page = filename;
        } else if (page.contains("#")) {
          name = page.substring(page.indexOf("#")+1);
          try {
            if (altRootFolder != null && filename.startsWith(altRootFolder))
              page = Utilities.path(altRootFolder, page.substring(0, page.indexOf("#")).replace("/", File.separator));
            else if (subFolder == null) {
              page = Utilities.path(rootFolder, page.substring(0, page.indexOf("#")).replace("/", File.separator));              
            } else {
              page = Utilities.path(rootFolder, subFolder, page.substring(0, page.indexOf("#")).replace("/", File.separator));
            }
          } catch (java.nio.file.InvalidPathException e) {
            page = null;
          }
        } else if (filename == null) {
          try {
            if (subFolder == null) {
              page = PathBuilder.getPathBuilder().withRequiredTarget(rootFolder).buildPath(rootFolder, page.replace("/", File.separator));              
            } else {
              page = PathBuilder.getPathBuilder().withRequiredTarget(rootFolder).buildPath(rootFolder, subFolder, page.replace("/", File.separator));
            }
          } catch (java.nio.file.InvalidPathException e) {
            page = null;
          }
        } else {
          try {
            String folder = FileUtilities.getDirectoryForFile(filename);
            String f = folder == null ? (altRootFolder != null && filename.startsWith(altRootFolder) ? altRootFolder : rootFolder) : folder;
            page = PathBuilder.getPathBuilder().withRequiredTarget(rootFolder).buildPath(f, page.replace("/", File.separator));
          } catch (java.nio.file.InvalidPathException e) {
            page = null;
          }
        }
        if (page != null) {
          LoadedFile f = cache.get(page);
          if (f != null) {
            if (Utilities.noString(name))
              return true;
            else if (f.isLangRedirect) {
              String fn = filename.replace(rootFolder, rootFolder+"/"+csHandler.getPrimaryLang());
              return checkTarget(fn, ref, rref, tgtList, bh, x, parent);
            } else {
              tgtList.append(" (valid targets: "+(f.targets.size() > 40 ? Integer.toString(f.targets.size())+" targets"  :  f.targets.toString())+")");
              for (String s : f.targets) {
                if (s.equalsIgnoreCase(name)) {
                  tgtList.append(" - case is wrong ('"+s+"')");
                }
              }
              return f.targets.contains(name);
            }
          }
        } else {
          return false;
        }
      }

    if (module.resolve(ref)) {
      return true;
    }
    return false;
  }

  private void addExternalReference(ExternalReferenceType type, String ref, XhtmlNode x, String source) {
    if (ref.contains("hl7.org") || ref.contains("simplifier.net") || ref.contains("fhir.org") || ref.contains("loinc.org") || ref.contains("vsac.nlm.nih.gov") ||
        ref.contains("snomed.org") || ref.contains("iana.org") || ref.contains("ietf.org") || ref.contains("w3.org") || ref.endsWith(".gov") || ref.contains(".gov/")) {
      return;
    }
    for (ExternalReference exr : externalReferences) {
      if (exr.getType() == type && exr.getUrl().equals(ref) && x.allText().equals(exr.getXhtml().allText())) {
        return;
      }
    }
    externalReferences.add(new ExternalReference(type, ref, x, stripLeadSlash(source.replace(rootFolder, ""))));
  }

  private String stripLeadSlash(String s) {
    return s.startsWith("/") || s.startsWith("\\") ? s.substring(1) : s;
  }

  @Nonnull
  private String buildRef(String refParentPath, String ref) throws IOException {
    // #TODO This logic should be in Utilities.path
    // Utilities path will try to assemble a filesystem path,
    // and this will fail in Windows if it contains ':' characters.
    return Utilities.path(refParentPath) + File.separator + ref;
  }

  private boolean matchesTarget(String ref, String... url) {
    for (String s : url) {
      if (ref.equals(s))
        return true;
      if (ref.equals(s+"/"))
        return true;
      if (ref.equals(s+"/index.html"))
        return true;
    }
    return false;
  }

  //  private SpecMapManager loadSpecMap(String id, String ver, String url) throws IOException {
  //    NpmPackage pi = pcm.loadPackageFromCacheOnly(id, ver);
  //    if (pi == null) {
  //      System.out.println("Fetch "+id+" package from "+url);
  //      URL url1 = new URL(Utilities.pathURL(url, "package.tgz")+"?nocache=" + System.currentTimeMillis());
  //      URLConnection c = url1.openConnection();
  //      InputStream src = c.getInputStream();
  //      pi = pcm.addPackageToCache(id, ver, src, url);
  //    }    
  //    SpecMapManager sm = new SpecMapManager(TextFile.streamToBytes(pi.load("other", "spec.internals")), id, pi.getNpm().getJsonObject("dependencies").asString("hl7.fhir.core"));
  //    sm.setBase(PackageHacker.fixPackageUrl(url));
  //    return sm;
  //  }

  private String makeLocal(String filename) {
    if (filename.startsWith(rootFolder))
      return filename.substring(rootFolder.length()+1);
    return filename;
  }

  private boolean checkResolveImageLink(String filename, Location loc, String path, String ref, List<ValidationMessage> messages, String uuid, XhtmlNode src) throws IOException {
    links++;
    boolean resolved = checkImgSourceExists(filename, ref, src);
    if (resolved)
      return false;
    else {
      String fn = makeLocal(filename);
      messages.add(new ValidationMessage(Source.LinkChecker, IssueType.NOTFOUND, fn+(path == null ? "" : "#"+path+(loc == null ? "" : " at "+loc.toString())), 
          "The image source '"+ref+"' cannot be resolved", IssueSeverity.ERROR).setLocationLink(uuid == null ? null : fn+"#"+uuid).setMessageId("HTML_IMG_SRC_CHECK_FAILED"));
      return true;
    } 
  }

  private boolean checkImgSourceExists(String filename, String ref, XhtmlNode src) throws IOException {
    boolean resolved = false;
    if (ref.startsWith("data:"))
      resolved = true;
    if (ref.startsWith("./")) {
      ref = ref.substring(2);
    }
    if (!resolved)
      resolved = manual.contains(ref);
    if (!resolved && specs != null){
      for (SpecMapManager spec : specs) {
        resolved = resolved || (spec.getBase() != null && spec.hasImage(ref)); 
      }
    }
    if (!resolved) {
      resolved = Utilities.existsInList(ref, "http://hl7.org/fhir/assets-hist/images/fhir-logo-www.png", "http://hl7.org/fhir/assets-hist/images/hl7-logo-n.png"); 
    }
    if (!resolved) {
      if (ref.startsWith("http://") || ref.startsWith("https://")) {
        resolved = true;
        if (specs != null) {
          for (SpecMapManager spec : specs) {
            if (spec.getBase() != null) {
              if (ref.startsWith(spec.getBase())) {
                resolved = false;
              }
            }
          }
        }
      } else if (!ref.contains("#") ) {
        try {
          String path = filename == null ? rootFolder : FileUtilities.getDirectoryForFile(filename);
          String page = PathBuilder.getPathBuilder().withRequiredTarget(rootFolder).buildPath(path, ref.replace("/", File.separator));
          LoadedFile f = cache.get(page);
          resolved = f != null;
        } catch (Exception e) {
          resolved = false;
        }
      }
    }
    if (resolved) {
      if (!Utilities.startsWithInList(ref, "icon_", "icon-", "tbl_", "data:", "cc0.png", "external.png", "assets/images/") && !ref.contains("hl7.org"))
        if (ref.startsWith("http://") || ref.startsWith("https://")) {
          addExternalReference(ExternalReferenceType.IMG, ref, src, filename);
        } else {
         imageRefs.put(ref, src);
        }
    }
    return resolved;
  }

  public void addLinkToCheck(String source, String link, String text) {
    otherlinks.add(new StringPair(source, link, text));

  }

  public int total() {
    return cache.size();
  }

  public int links() {
    return links;
  }

  private static String checkPlural(String word, int c) {
    return c == 1 ? word : Utilities.pluralizeMe(word);
  }

  public List<String> getManual() {
    return manual;
  }

  public void setManual(List<String> manual) {
    this.manual = manual;
  }

  public boolean isStrict() {
    return strict;
  }

  public void setStrict(boolean strict) {
    this.strict = strict;
  }

  public  List<SpecMapManager> getSpecMaps() {
    return specs;
  }

  public List<String> getIgs() {
    return igs;
  }

  public void setPcm(FilesystemPackageCacheManager pcm) {
    this.pcm = pcm; 
  }

  public boolean getPublishBoxOK() {
    return parseProblems.isEmpty();
  }

  public Set<String> getExceptions() {
    return exceptions;
  }

  public String getPublishboxParsingSummary() {
    if (parseProblems.isEmpty() && publishBoxProblems.isEmpty()) {
      return "No Problems";
    } else if (parseProblems.isEmpty()) {
      return "Missing Publish Box: "+summarise(publishBoxProblems);
    } else if (publishBoxProblems.isEmpty()) {
      return "Unable to Parse: "+summarise(parseProblems);      
    } else {
      return "No Publish Box: "+summarise(publishBoxProblems)+" and unable to Parse: "+summarise(parseProblems);      
    }
  }

  private String summarise(List<String> list) {
    CommaSeparatedStringBuilder b1 = new CommaSeparatedStringBuilder();
    for (int i = 0; i < list.size() && i < 10; i++) {
      b1.append(list.get(i));
    }    
    if (list.size() > 10) {
      return b1.toString()+" + "+Integer.toString(list.size()-10)+" other files";
    } else {
      return b1.toString();
    }
  }

  // adapted from anchor.min, which is used to generate these things on the flt 
  private String urlify(String a) {
    String repl = "-& +$,:;=?@\"#{}()[]|^~[`%!'<>].*";
    String elim = "/\\";
    StringBuilder b = new StringBuilder();
    boolean nextDash = false;
    for (char ch : a.toCharArray()) {
      if (elim.indexOf(ch) == -1) {
        if (repl.indexOf(ch) == -1) {
          if (nextDash) {
            b.append("-");
          }
          nextDash = false;
          b.append(Character.toLowerCase(ch));
        } else {
          nextDash = true;
        }
      }
    }
    String s = b.toString().trim();
    return s;
  }

  public List<ExternalReference> getExternalReferences() {
    return externalReferences;
  }

  public Map<String, String> getCopyrights() {
    return copyrights;
  }

  public Map<String, XhtmlNode> getImageRefs() {
    return imageRefs;
  }

  public Map<String, String> getVisibleFragments() {
    return visibleFragments;
  }

  public void setDefaultLang(String defaultTranslationLang) {
    csHandler.setDefaultLang(defaultTranslationLang);
  }

  public void setTranslationLangs(List<String> translationLangs) {
    csHandler.setTranslationLangs(translationLangs);
  }

  public void setReqFolder(String folder) {
    csHandler.setReqFolder(folder);
  }
  
}
