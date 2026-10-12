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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
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

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.PublisherBase.FragmentUseRecord;
import org.hl7.fhir.igtools.publisher.PublisherUtils.LinkedSpecification;
import org.hl7.fhir.igtools.publisher.SpecMapManager.SpecialPackageType;
import org.hl7.fhir.igtools.publisher.modules.IPublisherModule;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.model.core.CanonicalResource;
import org.hl7.fhir.model.core.ImplementationGuide;
import org.hl7.fhir.services.renderers.RendererFactory;
import org.hl7.fhir.utilities.*;
import org.hl7.fhir.utilities.logging.ILoggingService;
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

  /**
   * Tracks repeated anchor targets on a page: {@code <a name="..">} and element {@code id}s.
   * <p>
   * The two are counted SEPARATELY even though a fragment reference resolves against either,
   * because the templates deliberately emit both for the same target - {@code <a name="root"> </a>}
   * immediately above {@code <h2 id="root">} - and that idiom is not a duplicate. Sharing one
   * namespace would report almost every page in an IG for something that is entirely intentional.
   */
  public class DuplicateAnchorTracker {
    private Set<String> found = new HashSet<>();
    private Set<String> duplicates = new HashSet<>();
    private Set<String> foundIds = new HashSet<>();
    private Set<String> duplicateIds = new HashSet<>();

    public void seeAnchor(String tgt) {
      if (!found.contains(tgt)) {
        found.add(tgt);
      } else {
        duplicates.add(tgt);
      }
    }

    public void seeId(String tgt) {
      if (!foundIds.contains(tgt)) {
        foundIds.add(tgt);
      } else {
        duplicateIds.add(tgt);
      }
    }

    public boolean hasDuplicates() {
      return !duplicates.isEmpty();
    }

    public String listDuplicates() {
      return CommaSeparatedStringBuilder.join(",", Utilities.sorted(duplicates));
    }

    public boolean hasDuplicateIds() {
      return !duplicateIds.isEmpty();
    }

    public String listDuplicateIds() {
      return CommaSeparatedStringBuilder.join(",", Utilities.sorted(duplicateIds));
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
  /**
   * The heading level the IG wants each page's top heading to sit at, from the 'page-heading-level'
   * IG parameter. 0 means the IG did not set it, and heading levels are left exactly as generated.
   */
  @Getter @Setter private int pageHeadingLevel = 0;
  /**
   * Whether to run the static accessibility checks in checkAccessibility (the HTML_A11Y_* warnings), from
   * the 'accessibility-checks' IG parameter. The heading structure and page language checks are not
   * affected by this.
   */
  @Getter @Setter private boolean accessibilityChecks = false;
  /**
   * Whether to add the in-page accessibility check panel (a button that runs axe-core on the page in the
   * browser) to the bottom of every page. Local builds only, and not if the IG turns 'accessibility-checks' off.
   */
  @Getter @Setter private boolean accessibilityPanel = false;
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
  private Set<String> comparisonFiles = new HashSet<>();
  @Getter @Setter private ImplementationGuide ig;

  public HTMLInspector(IWorkerContext context, String rootFolder, List<SpecMapManager> specs, List<LinkedSpecification> linkSpecs, ILoggingService log,
                       String canonical, String packageId, String version, Map<String, List<String>> trackedFragments, List<FetchedFile> sources, IPublisherModule module, boolean isCIBuild,
                       Map<String, PublisherBase.FragmentUseRecord> fragmentUses, List<RelatedIG> relatedIGs, boolean noCIBuildIssues, List<String> langList, RendererFactory rendererFactory) throws IOException {
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
    this.csHandler = new ConformanceStatementHandler(this, context, log, langList, forHL7, rendererFactory);
  }

  public void setAltRootFolder(String altRootFolder) throws IOException {
    this.altRootFolder = Utilities.path(rootFolder, altRootFolder.replace("/", File.separator));
  }

  public List<ValidationMessage> check(String statusMessageDef, Map<String, String> statusMessagesLang) throws IOException {
    csHandler.setup(this.ig);
    this.statusMessageDef = statusMessageDef;
    this.statusMessagesLang = statusMessagesLang;
    iteration ++;

    List<ValidationMessage> messages = new ArrayList<ValidationMessage>();

    log.logDebugMessage(ILoggingService.LogCategory.HTML, "CheckHTML: List files");
    // list new or updated files
    List<String> loadList = new ArrayList<>();
    listFiles(rootFolder, loadList);
    log.logMessage("found "+Integer.toString(loadList.size())+" files");

    checkGoneFiles();

    log.logDebugMessage(ILoggingService.LogCategory.HTML, "Loading Files");
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


    log.logDebugMessage(ILoggingService.LogCategory.HTML, "Checking Resources");
    for (FetchedFile f : sources) {
      for (FetchedResource r : f.getResources()) {
        checkNarrativeLinks(f, r, Utilities.path(rootFolder, r.getPath()));
      }
    }
    if (accessibilityPanel) {
      writeAccessibilityScripts();
    }
    log.logDebugMessage(ILoggingService.LogCategory.HTML, "Checking Files");
    links = 0;
    // check links
    boolean first = true;
    i = 0;
    c = cache.size() / 40;
    for (String s : sorted(cache.keySet())) {
      log.logDebugMessage(ILoggingService.LogCategory.HTML, "Check "+s);
      LoadedFile lf = cache.get(s);
      // 'history.html' is generated by the publication process (it becomes the
      // directory of published versions). If this IG build also produces a
      // root-level history.html, the published version overwrites it and the
      // build's content is silently lost - so warn the author to rename the page.
      if (s.length() > rootFolder.length() && "history.html".equals(s.substring(rootFolder.length()+1))) {
        messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.BUSINESSRULE, s,
                "This IG generates a page named 'history.html'. That file name is reserved by the publication process, which replaces it with the directory of published versions when the IG is published, so the content generated here will be overwritten and lost. Rename the page to something else (e.g. test-history.html).",
                IssueSeverity.WARNING));
      }
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
            messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.NOTFOUND, s, "The html source does not contain the publish box"
                    + (first ? " "+RELEASE_HTML_MARKER+" (see note at https://confluence.hl7.org/spaces/FHIR/pages/66930646/FHIR+Implementation+Guide+Publishing+Requirements#FHIRImplementationGuidePublishingRequirements-PublishBox)" : ""), IssueSeverity.ERROR));
          } else if (first) {
            messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.NOTFOUND, s, "The html source does not contain the publish box; this is recommended for publishing support",
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
        XhtmlParser parser = new XhtmlParser().setMustBeWellFormed(strict);
        XhtmlNode x = parser.parse(new File(lf.filename), null);
        // The parse is lenient so that a page with broken markup still gets link checked, heading
        // checked and so on. But the markup IS broken - IG output is XHTML - so report whatever the
        // parser had to fix up to get here, with the line and column it happened at.
        for (String note : parser.getRecoveryNotes()) {
          messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.STRUCTURE, s,
              "The html source is not well formed: "+note, IssueSeverity.WARNING).setMessageId("HTML_NOT_WELL_FORMED"));
        }
        BooleanHolder bh = new BooleanHolder(false);
        csHandler.readConformanceClauses(lf, x, bh, new BooleanHolder(false), messages);
        try {
          processAsMarkdown(lf, x);
        } catch (Exception e) {
          messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.INVALID, s, "Illegal HTML: "+e.getMessage(), IssueSeverity.WARNING));
        }
        referencesValidatorPack = false;
        DuplicateAnchorTracker dat = new DuplicateAnchorTracker();
        Stack<XhtmlNode> stack = new Stack<XhtmlNode>();
        checkVisibleFragments(lf.path, stack, x);
        checkLanguage(s, x, messages);
        boolean headingsShifted = false;
        if (!isBuildReportPage(lf.path)) {
          // both of these run before checkLinks, which mutates the tree as it walks
          if (accessibilityChecks) {
            checkAccessibility(s, x, messages);
          }
          boolean singleRoot = checkHeadingStructure(s, x, messages);
          if (singleRoot) {
            headingsShifted = applyPageHeadingLevel(s, x, messages);
          }
        }
        boolean changed = checkLinks(lf, s, "", x, null, messages, false, dat, null) != NodeChangeType.NONE || bh.ok() || headingsShifted; // returns true if changed
        // after all the checks, so the panel's own markup is never checked
        if (accessibilityPanel && addAccessibilityPanel(lf, x)) {
          changed = true;
        }
        if (changed) {
          saveFile(lf, x);
        }
        if (dat.hasDuplicates()) {
          messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.BUSINESSRULE, s, "The html source has duplicate anchor Ids: "+dat.listDuplicates(), IssueSeverity.WARNING));
        }
        if (dat.hasDuplicateIds()) {
          messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.BUSINESSRULE, s,
                  "The html source has duplicate element ids: "+dat.listDuplicateIds()+". An id must be unique in a page - a link to one of these is ambiguous, and it is invalid HTML",
                  IssueSeverity.WARNING).setMessageId("HTML_DUPLICATE_ID"));
        }
        if (referencesValidatorPack) {
          if (lf.getHl7State() != null && lf.getHl7State()) {
            messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.BUSINESSRULE, s, "The html source references validator.pack which is deprecated. Change the IG to describe the use of the package system instead", IssueSeverity.ERROR));
          } else {
            messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.BUSINESSRULE, s, "The html source references validator.pack which is deprecated. Change the IG to describe the use of the package system instead", IssueSeverity.WARNING));
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

    log.logDebugMessage(ILoggingService.LogCategory.HTML, "Checking Other Links");
    // check other links:
    for (StringPair sp : otherlinks) {
      checkResolveLink(sp.source, null, null, sp.link, sp.text, messages, null, new XhtmlNode(NodeType.Element, "p").tx(sp.text), null);
    }

    log.logDebugMessage(ILoggingService.LogCategory.HTML, "Sorting Conformance Clauses");
    csHandler.renderConfHomes();
    csHandler.generateRequirementsInstance(messages);
    log.logDebugMessage(ILoggingService.LogCategory.HTML, "Done checking");

    for (String s : trackedFragments.keySet()) {
      if (!foundFragments.contains(s)) {
        if (trackedFragments.get(s).size() > 1) {
          messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.NOTFOUND, s, "An HTML fragment from the set "+trackedFragments.get(s)+" is not included anywhere in the produced implementation guide",
                  "An HTML fragment from the set "+trackedFragments.get(s)+" is not included anywhere in the produced implementation guide", IssueSeverity.WARNING));
        } else {
          messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.NOTFOUND, s, "The HTML fragment '"+trackedFragments.get(s).get(0)+"' is not included anywhere in the produced implementation guide",
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
        errors.add(new ValidationMessage(Source.HtmlChecker, IssueType.INVALID, path, msg, msg, IssueSeverity.ERROR).setRuleDate("2024-02-08"));
      } else if (!bh.ok()) {
        String msg = "Hyperlink '"+href+"' at '"+xpath+"' for '"+node.allText()+"' is a canonical reference, and is unsafe because of version handling (points to the current version, not this version)";
        errors.add(new ValidationMessage(Source.HtmlChecker, IssueType.INVALID, path, msg, msg, IssueSeverity.WARNING).setRuleDate("2024-02-09"));
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
        errors.add(new ValidationMessage(Source.HtmlChecker, IssueType.INVALID, path, msg, msg, IssueSeverity.ERROR).setRuleDate("2024-02-08"));
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
    log.logDebugMessage(ILoggingService.LogCategory.HTML, "Load "+s);

    File f = new File(s);
    Boolean hl7State = null;
    XhtmlNode x = null;
    boolean htmlName = f.getName().endsWith(".html") || f.getName().endsWith(".xhtml") || f.getName().endsWith(".svg");
    try {
      x = new XhtmlParser().setMustBeWellFormed(strict).parse(f, null);
      if (x.getElement("html")==null && x.getElement("svg")==null && !htmlName) {
        // We don't want resources being treated as HTML.  We'll check the HTML of the narrative in the page representation
        x = null;
      }
    } catch (FHIRFormatError | IOException e) {
      x = null;
      if (htmlName || !(e.getMessage().startsWith("Unable to Parse HTML - does not start with tag.") || e.getMessage().startsWith("Malformed XHTML"))) {
        messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.STRUCTURE, s, e.getMessage(), IssueSeverity.ERROR).setLocationLink(makeLocal(f.getAbsolutePath())).setMessageId("HTML_PARSING_FAILED"));
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
        x = new XhtmlParser().setMustBeWellFormed(strict).parse(f, null);
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
    //      messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.STRUCTURE, s, "failed security testing: "+e.getMessage(), IssueSeverity.ERROR));
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
      messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.STRUCTURE, s, "The html must include a div with an attribute igtool=\"footer\" that marks the footer in the template", IssueSeverity.ERROR));
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
      messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.STRUCTURE, s, "Root node must be 'html' or 'div', but is "+x.getName(), IssueSeverity.ERROR));
    }
    // We support div as well because with HTML 5, referenced files might just start with <div>
    // todo: check secure?

  }

  /**
   * A page must declare its language on the root html element (WCAG 3.1.1, Language of Page). Screen
   * readers use it to choose the voice and pronunciation rules, and without it they fall back to the
   * user's default language, which mangles the page for anyone reading it in a different one.
   * <p>
   * It has to be the html lang attribute: xml:lang on its own doesn't count, since browsers only
   * use it when the page is served as XML, and IG pages are served as text/html. Files with a div
   * root are fragments that get composed into some other page, and take that page's language.
   */
  private void checkLanguage(String s, XhtmlNode x, List<ValidationMessage> messages) {
    if (x.getNodeType() == NodeType.Document) {
      x = x.getFirstElement();
    }
    if (x == null || !"html".equals(x.getName())) {
      return;
    }
    String lang = x.getAttribute("lang");
    if (lang == null || lang.trim().isEmpty()) {
      String xmlLang = x.getAttribute("xml:lang");
      messages.add(located(new ValidationMessage(Source.HtmlChecker, IssueType.STRUCTURE, s,
              "The html element has no lang attribute, so the language of the page is not known"
              + (xmlLang != null && !xmlLang.trim().isEmpty() ? " (it has xml:lang=\""+xmlLang+"\", but browsers ignore that on pages served as html - add lang=\""+xmlLang+"\" as well)" : "")
              + ". Every page must declare its language on the root element (WCAG compliance test)",
              IssueSeverity.ERROR).setMessageId("HTML_NO_LANGUAGE"), s, x, null));
    }
  }

  // ---- Static accessibility checks (HHS Section 508 checklist items that can be tested from the markup) ----------

  private static final java.util.regex.Pattern LANG_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z]{2,8}(-[a-zA-Z0-9]{1,8})*$");
  private static final Set<String> ARIA_IDREF_ATTRIBUTES = new HashSet<>(Arrays.asList(
          "aria-labelledby", "aria-describedby", "aria-controls", "aria-owns", "aria-activedescendant", "aria-details", "aria-errormessage", "aria-flowto"));
  private static final Set<String> WIDGET_ROLES = new HashSet<>(Arrays.asList(
          "button", "link", "checkbox", "radio", "switch", "tab", "menuitem", "menuitemcheckbox", "menuitemradio", "option",
          "textbox", "searchbox", "combobox", "slider", "spinbutton", "treeitem", "gridcell"));
  private static final Set<String> PLACEHOLDER_ALT_TEXT = new HashSet<>(Arrays.asList(
          ".", "-", "icon", "image", "img", "picture", "graphic", "photo", "spacer", "logo"));
  // only phrases that are never names: words like 'link' or 'this' are also element names (Bundle.link), where the
  // link text is exactly the name of the thing linked to
  private static final Set<String> GENERIC_LINK_TEXT = new HashSet<>(Arrays.asList(
          "here", "click here", "more", "read more", "this link", "click this link"));

  /** an idref attribute (aria-labelledby etc, label for) found on the page, checked once all the ids are known */
  private static class A11yRef {
    private String attribute;
    private String ref;
    private XhtmlNode node;
    private String anchor;
    private String heading;
    private A11yRef(String attribute, String ref, XhtmlNode node, String anchor, String heading) {
      this.attribute = attribute;
      this.ref = ref;
      this.node = node;
      this.anchor = anchor;
      this.heading = heading;
    }
  }

  /** what the walk over a page collects for the checks that need the whole page (ids, references, labels) */
  private static class A11yScan {
    private Map<String, Integer> ids = new HashMap<>();
    private List<A11yRef> idRefs = new ArrayList<>();
    private Set<String> labelFors = new HashSet<>();
    private Map<XhtmlNode, String[]> unlabelledControls = new java.util.LinkedHashMap<>(); // controls not labelled by an attribute or an enclosing label -> anchor, heading
    private String title;
    private String heading; // the text of the most recent heading in the walk, so messages can say where on the page they are
  }

  /**
   * Checks for the parts of the HHS Section 508 checklist that can be decided from the markup alone:
   * images without alternative text, form controls without labels, links without names, click handlers
   * on things a keyboard cannot reach, broken aria references, missing page title, and
   * so on. Things that need a browser (colour contrast, focus order, anything the scripts build at
   * run time) or a person (whether alt text, titles and link text are meaningful) are out of scope.
   * <p>
   * These are all warnings for now: many of them come from the publisher, core renderers or the
   * templates rather than anything an IG author wrote.
   * <p>
   * Only whole pages are checked; div rooted files are fragments that are checked as part of the
   * pages they end up in.
   */
  private void checkAccessibility(String s, XhtmlNode x, List<ValidationMessage> messages) {
    if (x.getNodeType() == NodeType.Document) {
      x = x.getFirstElement();
    }
    if (x == null || !"html".equals(x.getName())) {
      return;
    }
    A11yScan scan = new A11yScan();
    scanA11y(s, x, messages, scan, null, false, false, null);

    if (scan.title == null || scan.title.trim().isEmpty()) {
      a11yWarning(messages, s, null, null, null, "HTML_A11Y_NO_TITLE", "The page has no title (HHS 9D, WCAG 2.4.2)");
    }
    // duplicate ids are already reported by the DuplicateAnchorTracker in checkLinks
    for (A11yRef ref : scan.idRefs) {
      if (!scan.ids.containsKey(ref.ref)) {
        if ("for".equals(ref.attribute)) {
          String text = ref.node.allText() == null ? "" : ref.node.allText().replaceAll("[\\s\\n]+", " ").trim();
          a11yWarning(messages, s, ref.node, ref.anchor, ref.heading, "HTML_A11Y_BROKEN_IDREF", "The label "+(text.isEmpty() ? "" : "'"+text+"' ")+"says it is for the element with id '"+ref.ref+"' (for=\""+ref.ref+"\"), but nothing on the page has that id, so the label is not connected to any form control. Usually the for and the control's id have got out of step (HHS 13B, WCAG 4.1.2)");
        } else {
          a11yWarning(messages, s, ref.node, ref.anchor, ref.heading, "HTML_A11Y_BROKEN_IDREF", "The "+a11yDesc(ref.node)+" has "+ref.attribute+"=\""+ref.ref+"\", but nothing on the page has the id '"+ref.ref+"', so assistive technology cannot follow the reference (HHS 13B, WCAG 4.1.2)");
        }
      }
    }
    for (Map.Entry<XhtmlNode, String[]> e : scan.unlabelledControls.entrySet()) {
      XhtmlNode c = e.getKey();
      String id = c.getAttribute("id");
      if (id == null || !scan.labelFors.contains(id)) {
        a11yWarning(messages, s, c, e.getValue()[0], e.getValue()[1], "HTML_A11Y_CONTROL_NO_LABEL", "The form control "+a11yDesc(c)+" has no label: use a <label>, aria-label or aria-labelledby (HHS 4G, WCAG 1.3.1/4.1.2)");
      }
    }
  }

  private void scanA11y(String s, XhtmlNode x, List<ValidationMessage> messages, A11yScan scan, XhtmlNode interactiveAncestor, boolean inHidden, boolean inLabel, String nearestId) {
    if (x.getNodeType() != NodeType.Element) {
      return;
    }
    String name = x.getName();
    if ("svg".equals(name)) {
      return; // svg has its own rules for all of this, and the checks below don't apply
    }
    String id = a11yAttr(x, "id");
    if (id != null && !id.isEmpty()) {
      scan.ids.put(id, scan.ids.getOrDefault(id, 0) + 1);
    }
    // messages link to the closest element that can be linked to - this one, or its nearest ancestor with an id
    String here = id != null && !id.isEmpty() ? id : nearestId;
    for (String an : ARIA_IDREF_ATTRIBUTES) {
      String v = a11yAttr(x, an);
      if (v != null) {
        for (String ref : v.trim().split("\\s+")) {
          if (!ref.isEmpty()) {
            scan.idRefs.add(new A11yRef(an, ref, x, here, scan.heading));
          }
        }
      }
    }
    if ("title".equals(name) && scan.title == null) {
      scan.title = x.allText();
    }
    if (name.length() == 2 && name.charAt(0) == 'h' && name.charAt(1) >= '1' && name.charAt(1) <= '6') {
      String ht = a11yText(x);
      if (!ht.isEmpty()) {
        scan.heading = ht;
      }
    }
    if ("label".equals(name) && a11yAttr(x, "for") != null) {
      scan.labelFors.add(a11yAttr(x, "for"));
      scan.idRefs.add(new A11yRef("for", a11yAttr(x, "for"), x, here, scan.heading));
    }
    String lang = a11yAttr(x, "lang");
    if (lang != null && !lang.isEmpty() && !LANG_PATTERN.matcher(lang).matches()) {
      a11yWarning(messages, s, x, here, scan.heading, "HTML_A11Y_BAD_LANG", "The "+a11yDesc(x)+" has lang='"+lang+"', which is not a valid language code (HHS 10B, WCAG 3.1.2)");
    }
    if (a11yAttr(x, "accesskey") != null) {
      a11yWarning(messages, s, x, here, scan.heading, "HTML_A11Y_ACCESSKEY", "The "+a11yDesc(x)+" has an accesskey, which can conflict with browser and screen reader shortcuts (HHS 6B, WCAG 2.1.1)");
    }
    if ("meta".equals(name) && "refresh".equalsIgnoreCase(a11yAttr(x, "http-equiv"))) {
      String content = a11yAttr(x, "content");
      String delay = content == null ? "" : content.split(";")[0].trim();
      if (!delay.isEmpty() && !delay.matches("0+(\\.0*)?")) {
        a11yWarning(messages, s, x, here, scan.heading, "HTML_A11Y_META_REFRESH", "The page refreshes or redirects itself after a delay ("+content+"), which users cannot pause or stop (HHS 7C, WCAG 2.2.1)");
      }
    }
    if ("video".equals(name) && !hasCaptionTrack(x)) {
      a11yWarning(messages, s, x, here, scan.heading, "HTML_A11Y_VIDEO_NO_CAPTIONS", "The "+a11yDesc(x)+" has no captions track (HHS 3D, WCAG 1.2.2)");
    }
    if (("iframe".equals(name) || "frame".equals(name)) && a11yBlank(a11yAttr(x, "title"))) {
      a11yWarning(messages, s, x, here, scan.heading, "HTML_A11Y_FRAME_NO_TITLE", "The "+a11yDesc(x)+" has no title describing its content (HHS 2E, WCAG 4.1.2)");
    }
    if ("img".equals(name) || "area".equals(name) || ("input".equals(name) && "image".equalsIgnoreCase(a11yAttr(x, "type")))) {
      String alt = a11yAttr(x, "alt");
      if (alt == null) {
        if (a11yBlank(a11yAttr(x, "aria-label")) && a11yBlank(a11yAttr(x, "aria-labelledby")) && !"presentation".equals(a11yAttr(x, "role")) && !"none".equals(a11yAttr(x, "role"))) {
          a11yWarning(messages, s, x, here, scan.heading, "HTML_A11Y_IMG_NO_ALT", "The "+a11yDesc(x)+" has no alt attribute: give it alternative text, or alt=\"\" if it is decorative (HHS 2A/2B, WCAG 1.1.1)");
        }
      } else if (PLACEHOLDER_ALT_TEXT.contains(alt.trim().toLowerCase()) || isFileNameOf(alt, a11yAttr(x, "src"))) {
        a11yWarning(messages, s, x, here, scan.heading, "HTML_A11Y_IMG_PLACEHOLDER_ALT", "The "+a11yDesc(x)+" has alt='"+alt+"', which tells a screen reader user nothing: describe the image, or use alt=\"\" if it is decorative (HHS 2A/2B, WCAG 1.1.1)");
      }
    }
    if ("table".equals(name) && Utilities.existsInList(a11yAttr(x, "role"), "presentation", "none") && hasTableStructure(x)) {
      a11yWarning(messages, s, x, here, scan.heading, "HTML_A11Y_LAYOUT_TABLE_STRUCTURE", "The "+a11yDesc(x)+" is marked as a layout table but contains header cells or a caption (HHS 4F, WCAG 1.3.1)");
    }

    boolean interactive = isA11yInteractive(x);
    boolean focusable = isA11yFocusable(x);
    if (interactive && interactiveAncestor != null) {
      a11yWarning(messages, s, x, here, scan.heading, "HTML_A11Y_NESTED_INTERACTIVE", "The "+a11yDesc(x)+" is inside another interactive element ("+a11yDesc(interactiveAncestor)+"); screen readers may not announce it and focus can misbehave (HHS 13B, WCAG 4.1.2)");
    }
    if (focusable && inHidden) {
      a11yWarning(messages, s, x, here, scan.heading, "HTML_A11Y_FOCUSABLE_HIDDEN", "The "+a11yDesc(x)+" can take keyboard focus but is inside aria-hidden content, so screen reader users land on something they cannot hear (HHS 2F, WCAG 4.1.2)");
    }
    String onclick = a11yAttr(x, "onclick");
    if (onclick != null && !interactive && !onclick.trim().startsWith("tableRowAction")) {
      // tableRowAction (the expand/collapse control in the hierarchical tables) is deliberately not reported for now
      a11yWarning(messages, s, x, here, scan.heading, "HTML_A11Y_CLICK_NOT_KEYBOARD", "The "+a11yDesc(x)+" has a click handler ("+(onclick.length() > 40 ? onclick.substring(0, 40)+"..." : onclick).replace("\"", "'")+") but cannot be reached or used from the keyboard (HHS 6A/6D, WCAG 2.1.1)");
    }
    if ("a".equals(name) && a11yAttr(x, "href") != null) {
      String text = x.allText() == null ? "" : x.allText().trim();
      boolean named = !text.isEmpty() || !a11yBlank(a11yAttr(x, "aria-label")) || !a11yBlank(a11yAttr(x, "aria-labelledby")) || !a11yBlank(a11yAttr(x, "title")) || hasImageWithAlt(x);
      if (!named) {
        a11yWarning(messages, s, x, here, scan.heading, "HTML_A11Y_EMPTY_LINK", "The "+a11yDesc(x)+" has no text, so a screen reader cannot say where it goes (HHS 9G, WCAG 2.4.4)");
      } else if (GENERIC_LINK_TEXT.contains(text.toLowerCase()) && a11yBlank(a11yAttr(x, "aria-label")) && !isLinkToNamedTarget(text, a11yAttr(x, "href"))) {
        a11yWarning(messages, s, x, here, scan.heading, "HTML_A11Y_GENERIC_LINK_TEXT", "The link "+a11yDesc(x)+" does not say where it goes when read out of context (HHS 9G, WCAG 2.4.4)");
      }
    }
    if (isA11yFormControl(x) && !inLabel && a11yBlank(a11yAttr(x, "aria-label")) && a11yBlank(a11yAttr(x, "aria-labelledby")) && a11yBlank(a11yAttr(x, "title"))) {
      scan.unlabelledControls.put(x, new String[] { here, scan.heading }); // decided at the end: a <label for> may come later in the page
    }

    boolean hidden = inHidden || "true".equals(a11yAttr(x, "aria-hidden"));
    boolean label = inLabel || "label".equals(name);
    XhtmlNode ia = interactive ? x : interactiveAncestor;
    if (x.hasChildren()) {
      for (XhtmlNode c : x.getChildNodes()) {
        scanA11y(s, c, messages, scan, ia, hidden, label, here);
      }
    }
  }

  private void a11yWarning(List<ValidationMessage> messages, String s, XhtmlNode node, String anchor, String heading, String id, String msg) {
    // each message ends with the checklist reference "(HHS nn, WCAG n.n.n)"; mark it as a compliance test like the heading checks.
    // Say which section of the page it's in: the line/column and anchor are not much help to someone looking at the rendered page
    String where = node == null ? "" : heading == null ? " (it is before the first heading on the page)" : " (it is in the section headed '"+heading+"')";
    messages.add(located(new ValidationMessage(Source.HtmlChecker, IssueType.STRUCTURE, s, msg.replace(" (HHS ", where+" (WCAG compliance test - HHS "), IssueSeverity.WARNING).setMessageId(id), s, node, anchor));
  }

  /** the visible text of an element, whitespace collapsed, shortened to something that fits in a message */
  private String a11yText(XhtmlNode x) {
    String t = x.allText();
    t = t == null ? "" : t.replaceAll("[\\s\\n]+", " ").trim();
    return t.length() > 50 ? t.substring(0, 47)+"..." : t;
  }

  /**
   * Make a message about a page clickable in the QA report: link it to the page (qa.html sits in the
   * output folder, so the page's path relative to that works), to the given anchor on it if there is one,
   * and record the line and column the parser saw the node at
   */
  private ValidationMessage located(ValidationMessage vm, String s, XhtmlNode node, String anchor) {
    vm.setLocationLink(makeLocal(s) + (Utilities.noString(anchor) ? "" : "#"+anchor));
    if (node != null && node.getLocation() != null) {
      vm.setLine(node.getLocation().getLine());
      vm.setCol(node.getLocation().getColumn());
    }
    return vm;
  }

  /** attribute lookup ignoring case - the parser keeps attribute names as written (onClick, onclick) */
  private String a11yAttr(XhtmlNode x, String name) {
    if (!x.hasAttributes()) {
      return null;
    }
    for (Map.Entry<String, String> e : x.getAttributes().entrySet()) {
      if (name.equalsIgnoreCase(e.getKey())) {
        return e.getValue() == null ? "" : e.getValue();
      }
    }
    return null;
  }

  private boolean a11yBlank(String v) {
    return v == null || v.trim().isEmpty();
  }

  private boolean isA11yFocusable(XhtmlNode x) {
    String tabindex = a11yAttr(x, "tabindex");
    if (tabindex != null) {
      return !tabindex.trim().startsWith("-");
    }
    switch (x.getName()) {
      case "a": return a11yAttr(x, "href") != null;
      case "button":
      case "select":
      case "textarea":
      case "summary": return a11yAttr(x, "disabled") == null;
      case "input": return a11yAttr(x, "disabled") == null && !"hidden".equalsIgnoreCase(a11yAttr(x, "type"));
      default: return false;
    }
  }

  private boolean isA11yInteractive(XhtmlNode x) {
    String role = a11yAttr(x, "role");
    return isA11yFocusable(x) || (role != null && WIDGET_ROLES.contains(role.trim()));
  }

  private boolean isA11yFormControl(XhtmlNode x) {
    switch (x.getName()) {
      case "select":
      case "textarea": return true;
      case "input": return !Utilities.existsInList(a11yAttr(x, "type") == null ? "text" : a11yAttr(x, "type").toLowerCase(), "hidden", "submit", "reset", "button", "image");
      default: return false;
    }
  }

  private boolean hasCaptionTrack(XhtmlNode x) {
    if (x.hasChildren()) {
      for (XhtmlNode c : x.getChildNodes()) {
        if (c.getNodeType() == NodeType.Element && "track".equals(c.getName()) && Utilities.existsInList(a11yAttr(c, "kind"), "captions", "subtitles")) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean hasImageWithAlt(XhtmlNode x) {
    if (x.hasChildren()) {
      for (XhtmlNode c : x.getChildNodes()) {
        if (c.getNodeType() == NodeType.Element) {
          if ("img".equals(c.getName()) && !a11yBlank(a11yAttr(c, "alt"))) {
            return true;
          }
          if ("svg".equals(c.getName()) || hasImageWithAlt(c)) {
            return true; // an inline svg is assumed to carry its own title
          }
        }
      }
    }
    return false;
  }

  private boolean hasTableStructure(XhtmlNode x) {
    if (x.hasChildren()) {
      for (XhtmlNode c : x.getChildNodes()) {
        if (c.getNodeType() == NodeType.Element) {
          if (Utilities.existsInList(c.getName(), "th", "caption", "thead")) {
            return true;
          }
          if (!"table".equals(c.getName()) && hasTableStructure(c)) { // a nested table answers for itself
            return true;
          }
        }
      }
    }
    return false;
  }

  /** true if the link text is the name of what the link points to, e.g. <a href="bundle.html#Bundle.link">link</a> */
  private boolean isLinkToNamedTarget(String text, String href) {
    if (href == null || !href.contains("#")) {
      return false;
    }
    String frag = href.substring(href.indexOf('#') + 1).toLowerCase();
    String t = text.toLowerCase();
    return frag.equals(t) || frag.endsWith("."+t);
  }

  private boolean isFileNameOf(String alt, String src) {
    if (a11yBlank(alt) || a11yBlank(src) || src.startsWith("data:")) {
      return false;
    }
    String fn = src.contains("/") ? src.substring(src.lastIndexOf('/') + 1) : src;
    return alt.trim().equalsIgnoreCase(fn);
  }

  private String a11yDesc(XhtmlNode x) {
    StringBuilder b = new StringBuilder("<"+x.getName());
    for (String an : new String[] { "id", "name", "type", "href", "src", "class" }) {
      String v = a11yAttr(x, an);
      if (v != null && !v.startsWith("data:")) {
        b.append(" "+an+"=\""+(v.length() > 60 ? v.substring(0, 60)+"..." : v)+"\"");
        break;
      }
    }
    b.append(">");
    // and its text, if it's the sort of element whose text identifies it (a button, a link, a label...)
    if (!Utilities.existsInList(x.getName(), "html", "body", "div", "table", "tbody", "thead", "tr", "ul", "ol", "dl", "section", "nav", "main", "header", "footer", "form", "select", "iframe", "frame", "img", "input", "textarea")) {
      String t = a11yText(x);
      if (!t.isEmpty()) {
        b.append(" '"+t+"'");
      }
    }
    return b.toString();
  }

  // ---- In-page accessibility check (local builds) -------------------------------------------------------

  private static final String A11Y_PANEL_ID = "fhir-a11y-panel";
  private static final String A11Y_CHECK_SCRIPT = "a11y-check.js";
  private static final String A11Y_AXE_SCRIPT = "a11y-axe.min.js";

  /**
   * Copy the two scripts the panel uses into the root of the output: our own a11y-check.js, and axe-core
   * (https://github.com/dequelabs/axe-core, MPL-2.0), which the page only loads when the button is pressed.
   */
  private void writeAccessibilityScripts() {
    try {
      copyResource("/a11y/a11y-check.js", A11Y_CHECK_SCRIPT);
      copyResource("/a11y/axe.min.js", A11Y_AXE_SCRIPT);
    } catch (Exception e) {
      log.logMessage("Unable to write the accessibility check scripts - the check panel will not be added: "+e.getMessage());
      accessibilityPanel = false;
    }
  }

  private void copyResource(String resource, String name) throws IOException {
    try (InputStream is = HTMLInspector.class.getResourceAsStream(resource)) {
      if (is == null) {
        throw new IOException("resource "+resource+" not found");
      }
      FileUtilities.streamToFile(is, Utilities.path(rootFolder, name));
    }
  }

  /**
   * Add the accessibility check panel to the end of the page body: a button that loads axe-core and checks
   * the page as the browser has it (after the scripts have run), reporting against the HHS checklist - see
   * a11y-check.js. Only whole content pages get it: not fragments, redirects, or the build reports.
   *
   * @return true if the panel was added (so the page needs saving)
   */
  private boolean addAccessibilityPanel(LoadedFile lf, XhtmlNode x) {
    XhtmlNode root = x.getNodeType() == NodeType.Document ? x.getFirstElement() : x;
    if (root == null || !"html".equals(root.getName()) || lf.isLangRedirect || isBuildReportPage(lf.path) || hasMetaRefresh(root)) {
      return false;
    }
    XhtmlNode body = root.getElement("body");
    if (body == null || findById(body, A11Y_PANEL_ID) != null) {
      return false; // no body, or already added (an earlier iteration of a watched build)
    }
    String prefix = "";
    for (char c : lf.path.toCharArray()) {
      if (c == '/') {
        prefix = prefix + "../";
      }
    }
    XhtmlNode panel = body.div();
    panel.setAttribute("id", A11Y_PANEL_ID);
    panel.setAttribute("data-axe", prefix+A11Y_AXE_SCRIPT);
    panel.setAttribute("lang", "en");
    panel.style("clear: both; margin: 20px 10px; padding: 8px; border: 1px solid #888888; background-color: #f4f4f4; color: #000000; font-size: 13px; font-family: sans-serif");
    XhtmlNode p = panel.para();
    p.style("margin: 0");
    p.b().tx("Accessibility check");
    p.tx(" (local builds only - checks this page as it is now against WCAG 2.0 A/AA, reported by HHS 508 checklist item) ");
    XhtmlNode btn = p.addTag("button");
    btn.setAttribute("type", "button");
    btn.setAttribute("class", "fhir-a11y-run");
    btn.tx("Check this page");
    p.tx(" ");
    p.span().setAttribute("class", "fhir-a11y-status").setAttribute("role", "status");
    panel.div().setAttribute("class", "fhir-a11y-results");
    XhtmlNode script = body.addTag("script");
    script.setAttribute("type", "text/javascript");
    script.setAttribute("src", prefix+A11Y_CHECK_SCRIPT);
    return true;
  }

  /** a redirect page (meta http-equiv=refresh) - note that hasHTTPRedirect is true for any meta http-equiv, including Content-Type */
  private boolean hasMetaRefresh(XhtmlNode x) {
    if ("meta".equals(x.getName()) && "refresh".equalsIgnoreCase(x.getAttribute("http-equiv"))) {
      return true;
    }
    if (x.hasChildren()) {
      for (XhtmlNode c : x.getChildNodes()) {
        if (c.getNodeType() == NodeType.Element && hasMetaRefresh(c)) {
          return true;
        }
      }
    }
    return false;
  }

  private XhtmlNode findById(XhtmlNode x, String id) {
    if (id.equals(x.getAttribute("id"))) {
      return x;
    }
    if (x.hasChildren()) {
      for (XhtmlNode c : x.getChildNodes()) {
        if (c.getNodeType() == NodeType.Element) {
          XhtmlNode r = findById(c, id);
          if (r != null) {
            return r;
          }
        }
      }
    }
    return null;
  }

  /**
   * The publisher's own build reports (qa.html and friends). These are not IG content - they are
   * generated here, they are not produced through the template, and an IG author cannot change
   * their structure, so it is not useful to raise heading structure errors against them.
   */
  private static final List<String> BUILD_REPORT_PAGES = Arrays.asList(
          "qa.html", "qa.min.html", "qa-tx.html", "qa-txservers.html", "qa-dep.html", "qa-ipreview.html");

  private boolean isBuildReportPage(String path) {
    return path != null && BUILD_REPORT_PAGES.contains(path);
  }

  /**
   * Check the heading structure of a composed page. A page must have exactly one top level heading,
   * it must be the first heading on the page, and heading levels must not skip a level on the way
   * down (h2 -> h4). Headings are how assistive technology navigates a document, so a heading tree
   * that does not match the visible structure of the page is a WCAG problem, and skipped levels are
   * a defect even when the page looks right.
   * <p>
   * Only whole documents are checked. A file with a div root is a fragment that gets composed into
   * some other page, so its heading levels are relative to wherever it lands and say nothing on
   * their own.
   * <p>
   * Every offending heading is reported, not just the first: the problems on a page are usually
   * independent of each other, and reporting one at a time would cost a full rebuild per fix.
   * <p>
   * To make that work, the walk carries the level each heading SHOULD have had rather than the one
   * it does have. Otherwise one heading in the wrong place drags every heading after it out of
   * position too, and the author gets a page of errors describing a single mistake. Note that this
   * only applies to a skipped level, where the right level is unambiguous; headings at or above the
   * root level are left where they are, because the fix there is to add a heading above them, and
   * pretending each successive one should be a level deeper is nonsense.
   *
   * @return true if the page has exactly one top level heading and it is the first heading on the
   *   page - that is, if it is safe to re-level the page as a whole. A skipped level further down
   *   does not affect this: shifting the tree preserves the gap, so it is still reported either way
   */
  private boolean checkHeadingStructure(String s, XhtmlNode x, List<ValidationMessage> messages) {
    if (x.getNodeType() == NodeType.Document) {
      x = x.getFirstElement();
    }
    if (x == null || !"html".equals(x.getName())) {
      return false;
    }
    List<XhtmlNode> headings = new ArrayList<XhtmlNode>();
    listHeadings(x, headings);
    if (headings.isEmpty()) {
      return false;
    }
    XhtmlNode first = headings.get(0);
    int root = headingLevel(first);
    int prev = root;         // the level the previous heading should have had
    XhtmlNode prevNode = first;
    boolean singleRoot = true;
    for (int i = 1; i < headings.size(); i++) {
      XhtmlNode h = headings.get(i);
      int level = headingLevel(h);
      int should = level;    // what this heading should have been, for the benefit of the ones after it
      if (level < root) {
        // no suggested level: the page needs one heading above all of these, and which heading
        // that is, is the author's call - not something to guess at per offending heading
        should = root;
        singleRoot = false;
        messages.add(located(new ValidationMessage(Source.HtmlChecker, IssueType.STRUCTURE, s,
                "The heading <"+h.getName()+"> "+headingDesc(h)+" is at a higher level than the first heading on the page (<h"+root+"> "+headingDesc(first)+"). The first heading on a page must be its top level heading (WCAG compliance test)",
                IssueSeverity.ERROR).setMessageId("HTML_HEADING_ABOVE_ROOT"), s, h, h.getAttribute("id")));
      } else if (level == root) {
        should = root;
        singleRoot = false;
        messages.add(located(new ValidationMessage(Source.HtmlChecker, IssueType.STRUCTURE, s,
                "The page has more than one top level heading: <"+h.getName()+"> "+headingDesc(h)+" is at the same level as the first heading on the page (<h"+root+"> "+headingDesc(first)+"). A page must have exactly one top level heading, with every other heading beneath it (WCAG compliance test)",
                IssueSeverity.ERROR).setMessageId("HTML_HEADING_MULTIPLE_ROOTS"), s, h, h.getAttribute("id")));
      } else if (level > prev + 1) {
        // here the correction IS computable, and carrying it forward is what stops one misplaced
        // heading reporting again for every heading under it
        should = prev + 1;
        messages.add(located(new ValidationMessage(Source.HtmlChecker, IssueType.STRUCTURE, s,
                "The page skips a heading level: <"+h.getName()+"> "+headingDesc(h)+" follows <h"+prev+"> "+headingDesc(prevNode)+", so it should be <h"+should+">. Heading levels must not skip a level going down (WCAG compliance test)",
                IssueSeverity.ERROR).setMessageId("HTML_HEADING_LEVEL_SKIPPED"), s, h, h.getAttribute("id")));
      }
      prevNode = h;
      prev = should;
    }
    return singleRoot;
  }

  /**
   * Re-level a page so that its top heading sits at the level the IG asked for in
   * 'page-heading-level', shifting everything below it by the same amount so the structure is kept.
   * <p>
   * This is off unless the IG sets the parameter, and that is deliberate. The stock templates
   * number their headings in CSS keyed to the literal tag names - h2 is the page section counter,
   * h3 the sub-section, and --heading-prefix is set on h2 and on h3..h6 with different values - so
   * re-levelling a page silently breaks its section numbering. A template opts in only once it has
   * rebased that CSS to match.
   * <p>
   * Only pages with one top level heading are touched. On a page with several, there is no single
   * "top" to move to the requested level, and the author has already been told about it.
   * <p>
   * Nothing here repairs a page: a skipped level survives the shift, and is reported either way.
   *
   * @return true if any heading was changed, so the caller knows the file has to be written back
   */
  private boolean applyPageHeadingLevel(String s, XhtmlNode x, List<ValidationMessage> messages) {
    if (pageHeadingLevel < 1) {
      return false;
    }
    try {
      List<XhtmlNode> before = new ArrayList<XhtmlNode>();
      listHeadings(x, before);
      String was = before.isEmpty() ? null : before.get(0).getName();
      x.ensureTopHeadingIs(pageHeadingLevel);
      return was != null && !was.equals(before.get(0).getName());
    } catch (FHIRException e) {
      messages.add(located(new ValidationMessage(Source.HtmlChecker, IssueType.STRUCTURE, s,
              "The headings on this page cannot be moved to start at <h"+pageHeadingLevel+"> as the IG's 'page-heading-level' asks: "+e.getMessage()+
                      ". The page has been left as it is",
              IssueSeverity.WARNING).setMessageId("HTML_HEADING_LEVEL_UNSHIFTABLE"), s, null, null));
      return false;
    }
  }

  private void listHeadings(XhtmlNode x, List<XhtmlNode> headings) {
    if ("svg".equals(x.getName())) {
      return;
    }
    if (headingLevel(x) > 0) {
      headings.add(x);
    }
    for (XhtmlNode c : x.getChildNodes()) {
      listHeadings(c, headings);
    }
  }

  /**
   * @return 1-6 for h1..h6, or 0 for anything else
   */
  private int headingLevel(XhtmlNode x) {
    String n = x.getName();
    if (n != null && n.length() == 2 && (n.charAt(0) == 'h' || n.charAt(0) == 'H') && n.charAt(1) >= '1' && n.charAt(1) <= '6') {
      return n.charAt(1) - '0';
    }
    return 0;
  }

  private String headingDesc(XhtmlNode h) {
    String t = h.allText();
    if (t == null) {
      return "(no text)";
    }
    t = t.replaceAll("[\\s\\n]+", " ").trim();
    if (t.isEmpty()) {
      return "(no text)";
    }
    if (t.length() > 60) {
      t = t.substring(0, 57)+"...";
    }
    return "'"+t+"'"+(h.hasAttribute("id") ? " (id="+h.getAttribute("id")+")" : "");
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
    if (x.hasAttribute("id")) {
      dat.seeId(x.getAttribute("id"));
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
      messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.INVALID, filename+(loc == null ? "" : " at "+loc.toString()), "The <script> src '"+src+"' is illegal", IssueSeverity.FATAL));
    } else if (src == null && x.allText() != null && !x.allText().contains(HierarchicalTableGenerator.uuid)) {
      String js = x.allText();
      if (jsmsgs.containsKey(js)) {
        ValidationMessage vm = jsmsgs.get(js);
        vm.incCount();
      } else {
        ValidationMessage vm;
        if (isCIBuild) {
          vm = new ValidationMessage(Source.HtmlChecker, IssueType.INVALID, filename+(path == null ? "" : "#"+path+(loc == null ? "" : " at "+loc.toString())), "The <script> tag in the file '"+filename+"' containing the javascript '"+subset(x.allText())+"'... is illegal - put the script in a  .js file in a trusted template (if it is justified and needed)", IssueSeverity.FATAL);
        } else if (forHL7) {
          vm =  new ValidationMessage(Source.HtmlChecker, IssueType.INVALID, filename+(path == null ? "" : "#"+path+(loc == null ? "" : " at "+loc.toString())), "The <script> containing the javascript '"+subset(x.allText())+"'... is illegal and not allowed on the HL7 ci-build - put the script in a  .js file in a trusted template (if it is justified and needed)", IssueSeverity.ERROR);
        } else if (noCIBuildIssues) {
          vm = null;
        } else {
          vm =  new ValidationMessage(Source.HtmlChecker, IssueType.INVALID, filename+(path == null ? "" : "#"+path+(loc == null ? "" : " at "+loc.toString())), "The <script> containing the javascript '"+subset(x.allText())+"'... is illegal and not allowed on the HL7 ci-build - need to put the script in a  .js file in a trusted template if this IG is to build on the HL7 ci-build (if it is justified and needed)", IssueSeverity.WARNING);
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
      messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.NOTFOUND, filename+(loc == null ? "" : " at "+loc.toString()), "The <link> href '"+href+"' is llegal", IssueSeverity.FATAL).setLocationLink(uuid == null ? null : filename+"#"+uuid));
      return true;
    } else
      return false;
  }

  private boolean checkResolveLink(String filename, Location loc, String path, String ref, String text, List<ValidationMessage> messages, String uuid, XhtmlNode x, XhtmlNode parent) throws IOException {
    links++;
    String fn = makeLocal(filename);
    String rref;
    try {
      rref = Utilities.URLDecode(ref);
    } catch (Exception e) {
      messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.NOTFOUND, fn+(path == null ? "" : "#"+path+(loc == null ? "" : " at "+loc.toString())),
              "The link '"+ref+"' for \""+text.replaceAll("[\\s\\n]+", " ").trim()+"\" contains invalid characters (around %)", IssueSeverity.ERROR).setLocationLink(uuid == null ? null : fn+"#"+uuid).setMessageId("HTML_LINK_CHECK_FAILED"));
      return false;
    }
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
      messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.NOTFOUND, fn+(path == null ? "" : "#"+path+(loc == null ? "" : " at "+loc.toString())),
              "The link '"+ref+"' for \""+text.replaceAll("[\\s\\n]+", " ").trim()+"\" cannot be resolved"+tgtList, IssueSeverity.ERROR).setLocationLink(uuid == null ? null : fn+"#"+uuid).setMessageId("HTML_LINK_CHECK_FAILED"));
      return true;
    } else if (!bh.ok()) {
      if (text == null)
        text = "";
      messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.NOTFOUND, fn+(path == null ? "" : "#"+path+(loc == null ? "" : " at "+loc.toString())),
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
    String fixedRef = null;
    if (subFolder == null) {
      if ("full-ig.zip".equals(ref)) {
        return true;
      }
      fixedRef = rref;
    } else {
      if ("../full-ig.zip".equals(ref)) {
        return true;
      }
      fixedRef = rref.length() > 3 ? rref.substring(3) : rref;
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

    if (comparisonFiles.contains(fixedRef)) {
      return true;
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
      messages.add(new ValidationMessage(Source.HtmlChecker, IssueType.NOTFOUND, fn+(path == null ? "" : "#"+path+(loc == null ? "" : " at "+loc.toString())),
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

  public Set<String> getComparisonFiles() {
    return comparisonFiles;
  }
}