package org.hl7.fhir.igtools.publisher;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.FileNode;
import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.FileResult;
import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.Hasher;
import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.ResourceNode;
import org.hl7.fhir.model.IModelContext;
import org.hl7.fhir.model.core.CanonicalResource;
import org.hl7.fhir.model.core.CanonicalType;
import org.hl7.fhir.model.core.DateTimeType;
import org.hl7.fhir.model.core.DomainResource;
import org.hl7.fhir.model.core.ElementDefinition;
import org.hl7.fhir.model.core.Resource;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.ValueSet;
import org.hl7.fhir.model.core.formats.JsonParser;
import org.hl7.fhir.model.utilities.formats.OutputStyle;
import org.hl7.fhir.services.client.EFhirClientException;
import org.hl7.fhir.services.client.ITerminologyClientFactoryN;
import org.hl7.fhir.services.client.ITerminologyClientN;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.standalone.terminology.client.TerminologyClientManager;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.ToolingClientLogger;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.filesystem.ManagedFileAccess;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonNumber;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonPrimitive;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

/**
 * The state that Rapido keeps between builds, and the decisions made from it.
 *
 * Every build still loads every file, generates narratives and native outputs for every resource, and runs every
 * summary page and Jekyll: those are cheap enough, and they're where most of the cross-resource output comes from.
 * What a Rapido build skips, for a file whose inputs haven't changed, is
 * <ul>
 *   <li>validation: the messages from the last build are restored instead, if nothing the file depends on (see
 *   {@link RapidoDependencyAnalyser}) has changed</li>
 *   <li>html and spreadsheet generation: the outputs from the last build are still in the temp folder, and the effects
 *   that generation has on the rest of the build (outputs it claims, npm entries, expansions, errors, tracked
 *   fragments) are replayed from a journal, if nothing that the file depends on, or that refers to it, has
 *   changed</li>
 *   <li>translation artifacts: the files from the last build are kept in the translations folder, if nothing the
 *   file depends on has changed and they are as that build wrote them</li>
 * </ul>
 * Anything outside the files of the IG that could affect the build (the publisher itself, the command line, the IG
 * resource, the template, other files in the IG folder, the day) goes into a global hash, and each package the build loaded is
 * recorded with a hash of its package.json. If any of that changes, or the set of files changes, or the last build
 * didn't finish, the build is a complete one.
 *
 * <p>What keeping the state costs a build goes with what changed, not with the size of the IG:
 * <ul>
 *   <li>the state a process saved is the state its next build starts from (watch mode), so it isn't read again while the
 *   file is still the one that was written (see {@link Kept})</li>
 *   <li>a journaled expansion is a file of its own, named by the hash of its content, so it is written once, and parsed
 *   once by a process, however many builds replay it</li>
 *   <li>a file a phase wrote is taken as unchanged from its size and times when those can't have stayed the same over a
 *   change (see {@link FileStamp}), and only read and hashed otherwise</li>
 * </ul>
 */
public class RapidoTracker {

  public static final String PHASE_HTML = "html";
  public static final String PHASE_SHEETS = "sheets";

  private static final int STATE_VERSION = 5;

  /**
   * The state this process saved last, and the expansions it has parsed. The next build of the same IG in this process
   * (watch mode) starts from it instead of reading the file, as long as the file is still the one that was written: it
   * has the size and times it had then, and starts with the id that was written into it. So a state saved by another
   * process since, or a file that was replaced or removed, is read (or missed) as in a new process.
   */
  private record Kept(String file, String id, String stamp, JsonObject state, Map<String, ValueSet> expansions) {
  }

  private static Kept kept;

  /** for a test: what a new process has */
  static synchronized void forgetKept() {
    kept = null;
  }

  /** for a test: the state this process has kept for that state file, or null */
  static JsonObject keptState(File file) {
    Kept k = kept(file.getPath());
    return k == null ? null : k.state();
  }

  private static synchronized Kept kept(String file) {
    return kept != null && kept.file().equals(file) ? kept : null;
  }

  private static synchronized void keep(Kept k) {
    kept = k;
  }

  private final PublisherFields pf;
  private final String stateFile;
  /** the journaled expansions, each in a file named by the hash of its content */
  private final String expansionDir;
  /** the parsed expansions of the last build, by hash, if this process has them */
  private Map<String, ValueSet> oldExpansions = new HashMap<>();
  /** the expansions this build replays, by hash */
  private final Map<String, ValueSet> expansions = new HashMap<>();
  /** the file system's time when this build first looked at the files of the last one (see {@link FileStamp}) */
  private FileStamp buildClock;
  private boolean buildClockRead;
  private final Map<FetchedFile, String> keys = new IdentityHashMap<>();
  private final Set<FetchedFile> validationClean = Collections.newSetFromMap(new IdentityHashMap<>());
  private final Set<FetchedFile> renderClean = Collections.newSetFromMap(new IdentityHashMap<>());
  private final Set<FetchedFile> noSource = Collections.newSetFromMap(new IdentityHashMap<>());
  /** why a file is validated or rendered again, for the log */
  private final Map<FetchedFile, String> why = new IdentityHashMap<>();
  private Map<String, FileResult> results = new HashMap<>();
  private final Map<FetchedFile, FileNode> nodes = new IdentityHashMap<>();
  private JsonObject oldFiles;
  private final JsonObject newFiles = new JsonObject();
  private final Map<String, String> global = new TreeMap<>();
  /** path (relative to the IG folder) to content hash, for the files behind global "other-files" */
  private final Map<String, String> otherFiles = new TreeMap<>();
  /** what isn't part of other-files (outputs, caches, the IG's own files, ...) */
  private final Set<String> otherSkip = new HashSet<>();
  private String fileSetHash;
  private String uuid;
  private final RapidoSteps steps = new RapidoSteps();
  private boolean complete;
  private String reason;
  private Recording recording;
  /** the validator's statusWarnings before the resource being validated now, or null if they can't be read */
  private Set<String> statusBefore;
  /** how many of the IG's profiles the resource being validated was known to conform to before it was validated */
  private int profilesBefore;
  /** how many validations were reused, and how many were done, for the log */
  private int validationsReused;
  private int validationsDone;
  /** the date the loader puts on canonical resources that have none; it differs every build */
  private final String buildDate;
  /**
   * whether a Reference.reference only needs its target to exist with the right type. Before R4, definitions are
   * linked by Reference too (a binding's valueSetReference, QuestionnaireResponse.questionnaire, ...), and the validator
   * reads all of the target, so there it's a full edge
   */
  private final boolean weakReferences;

  public RapidoTracker(PublisherFields pf) throws IOException {
    this.pf = pf;
    this.stateFile = Utilities.path(pf.tempDir, "_rapido", "state.json");
    this.expansionDir = Utilities.path(pf.tempDir, "_rapido", "expansions");
    this.buildDate = new DateTimeType(pf.getExecTime()).asStringValue();
    this.weakReferences = pf.version != null && VersionUtilities.isR4Plus(pf.version);
  }

  // ---- analysis ------------------------------------------------------------------------------------------------------

  /**
   * Works out what can be reused from the last build.
   *
   * @param failure if not null, why the build has to be a complete one regardless of the saved state
   */
  public void analyse(FetchedFile igf, String failure) throws IOException {
    Set<String> used = new HashSet<>();
    for (FetchedFile f : pf.fileList) {
      String k = f.getLoadPath() != null ? f.getLoadPath() : f.getPath();
      String key = k;
      int i = 1;
      while (!used.add(key)) {
        key = k+"#"+(i++);
      }
      keys.put(f, key);
    }
    computeGlobal(igf);
    Hasher fs = new Hasher();
    for (String k : new TreeSet<>(keys.values())) {
      fs.add(k);
    }
    fileSetHash = fs.finish();

    String noAnalysis = analyseDependencies();

    JsonObject old = loadState();
    reason = failure;
    if (reason == null) {
      reason = noAnalysis;
    }
    if (reason == null) {
      reason = compareState(old);
    }
    if (reason == null && pf.genExamples) {
      reason = "generating examples";
    }
    if (reason == null && pf.doTransforms) {
      reason = "running transforms";
    }
    if (reason == null && !new RapidoOutputs(pf.vsCache, pf.tempDir).isComplete()) {
      // a reused file's outputs are what it wrote in the temp folder in the last build (see RapidoOutputs)
      reason = "what the last build wrote in the temp folder isn't all there";
    }
    complete = reason != null;
    if (complete) {
      return;
    }
    steps.load(old.getJsonObject("steps"));
    oldFiles = old.getJsonObject("files");
    uuid = old.asString("uuid");
    for (FetchedFile f : pf.fileList) {
      JsonObject of = oldFiles.getJsonObject(keys.get(f));
      FileResult res = results.get(keys.get(f));
      if (alwaysDirty(f, igf)) {
        why.put(f, "always processed");
      } else if (of == null || res == null) {
        why.put(f, "not in the last build");
      } else if (!res.getOwnHash().equals(of.asString("oh"))) {
        why.put(f, ownChange(f, of));
      } else if (!res.getValidationHash().equals(of.asString("vh"))) {
        why.put(f, "something it depends on changed");
      } else if (!of.has("validation")) {
        why.put(f, "no saved validation");
      } else {
        validationClean.add(f);
        if (!res.getRenderHash().equals(of.asString("rh"))) {
          why.put(f, "something that refers to it changed");
        } else if (!journalUsable(of.getJsonObject(PHASE_HTML)) || !journalUsable(of.getJsonObject(PHASE_SHEETS))) {
          why.put(f, "outputs from the last build are missing");
        } else {
          renderClean.add(f);
        }
      }
    }
  }

  private String ownChange(FetchedFile f, JsonObject of) {
    FileNode fn = nodes.get(f);
    if (!fn.getSourceHash().equals(of.asString("src"))) {
      return "changed";
    }
    JsonObject or = of.forceObject("resources");
    for (ResourceNode r : fn.getResources()) {
      if (!r.getContentHash().equals(or.asString(r.getKey()))) {
        return "the content loaded from it changed: "+r.getKey();
      }
    }
    return "changed";
  }

  private String firstDifference(JsonObject oldOther) {
    Set<String> paths = new TreeSet<>(otherFiles.keySet());
    paths.addAll(oldOther.getNames());
    for (String p : paths) {
      if (!otherFiles.containsKey(p)) {
        return p+" (removed)";
      }
      if (!otherFiles.get(p).equals(oldOther.asString(p))) {
        return p+(oldOther.has(p) ? "" : " (added)");
      }
    }
    return "other-files";
  }

  private String compareState(JsonObject old) {
    if (old == null) {
      return "no state from a previous build";
    }
    if (old.has("unreadable")) {
      return "the state from the last build can't be read ("+old.asString("unreadable")+")";
    }
    if (!Integer.valueOf(STATE_VERSION).equals(old.asInteger("version"))) {
      return "state from a different version";
    }
    if (old.has("txFailures")) {
      return "terminology server calls failed in the last build ("+old.asInteger("txFailures")+")";
    }
    JsonObject og = old.getJsonObject("global");
    for (Map.Entry<String, String> e : global.entrySet()) {
      if (og == null || !e.getValue().equals(og.asString(e.getKey()))) {
        if ("other-files".equals(e.getKey()) && old.has("otherFiles")) {
          return "changed: "+firstDifference(old.getJsonObject("otherFiles"));
        }
        return "changed: "+e.getKey();
      }
    }
    String pc = packageChange(old);
    if (pc != null) {
      return pc;
    }
    if (!fileSetHash.equals(old.asString("fileSet"))) {
      return "files added or removed";
    }
    if (!old.has("files") || !old.has("uuid")) {
      return "incomplete state";
    }
    return null;
  }

  /**
   * Files that are always validated and generated again: the IG itself, pages (which can include anything), and
   * resources whose rendering pulls in content from all over the IG, or isn't fully captured by a journal.
   */
  private boolean alwaysDirty(FetchedFile f, FetchedFile igf) {
    if (f == igf || noSource.contains(f) || f.getResources().isEmpty() || f.getProcessMode() != FetchedFile.PROCESS_RESOURCE || f.getLogical() != null || f.isFolder()) {
      return true;
    }
    for (FetchedResource r : f.getResources()) {
      if (Utilities.existsInList(r.fhirType(), "ImplementationGuide", "FeatureDefinition", "WebTemplate", "ViewDefinition", "Binary") || r.isCustomResource()) {
        return true;
      }
    }
    return false;
  }

  private boolean journalUsable(JsonObject j) {
    if (j == null) {
      return false;
    }
    for (String s : j.forceArray("outputs").asStrings()) {
      if (!new File(s).exists()) {
        return false;
      }
    }
    // also when this process has them parsed: the saved state has to stay one that another process can use
    for (String h : j.forceArray("expansions").asStrings()) {
      if (!expansionFile(h).exists()) {
        return false;
      }
    }
    return true;
  }

  public boolean isCompleteBuild() {
    return complete;
  }

  /** the steps that make fragments for the IG as a whole, and what the last build's did (see RapidoSteps) */
  public RapidoSteps getSteps() {
    return steps;
  }

  public String getReason() {
    return reason;
  }

  /** the table generator uuid to reuse, so that replayed outputs and new outputs agree */
  public String getUuid() {
    return uuid;
  }

  public void setUuid(String uuid) {
    this.uuid = uuid;
  }

  /**
   * Whether nothing that the file's outputs are made from has changed since the build whose state this build loaded:
   * the file, what it depends on, and what refers to it (always false in a complete build)
   */
  public boolean isRenderClean(FetchedFile f) {
    return renderClean.contains(f);
  }

  public int countValidationDirty() {
    return pf.fileList.size() - validationClean.size();
  }

  public int countRenderDirty() {
    return pf.fileList.size() - renderClean.size();
  }

  public List<String> listRenderDirty() {
    List<String> res = new ArrayList<>();
    for (FetchedFile f : pf.fileList) {
      if (!renderClean.contains(f)) {
        res.add(keys.get(f)+" ("+why.get(f)+(validationClean.contains(f) ? "; render only" : "")+")");
      }
    }
    Collections.sort(res);
    return res;
  }

  private FileNode fileNode(FetchedFile f) {
    Hasher h = new Hasher();
    h.add(f.getPath()).add(Integer.toString(f.getProcessMode())).add(f.getLogical()).add(f.getContentType());
    if (f.hasSource()) {
      h.add(f.getSource());
    } else {
      // no bytes to compare: the file itself is always processed again (see alwaysDirty), and what depends on
      // it still sees its resources' content, which resourceNode hashes
      h.add("(no source)");
      noSource.add(f);
      for (FetchedResource r : f.getResources()) {
        if (r.getElement() == null) {
          // nothing at all to compare, so assume it changed
          h.add(UUID.randomUUID().toString());
        }
      }
    }
    for (FetchedFile.Output o : f.getOutputs()) {
      h.add(o.getRelativePath()).add(Integer.toString(o.getProcessMode())).add(o.getXslt());
    }
    FileNode fn = new FileNode(keys.get(f), h.finish());
    for (FetchedResource r : f.getResources()) {
      fn.addResource(resourceNode(r));
    }
    return fn;
  }

  /**
   * What each file depends on. null, or why it can't be worked out: then the build is a complete one, as a build
   * without -rapido would be, rather than one that fails
   */
  String analyseDependencies() {
    try {
      for (FetchedFile f : pf.fileList) {
        nodes.put(f, fileNode(f));
      }
      results = new RapidoDependencyAnalyser(pf.igpkp.getCanonical(), pf.context == null ? null : pf.context.getResourceNames()).analyse(nodes.values());
      return null;
    } catch (RuntimeException | Error e) {
      if (BuildFailures.isFatal(e)) {
        throw e;
      }
      e.printStackTrace();
      nodes.clear();
      results = new HashMap<>();
      return "the dependency analysis failed: " + e;
    }
  }

  ResourceNode resourceNode(FetchedResource r) {
    ResourceNode n = new ResourceNode(r.fhirType(), r.getId());
    definitions.clear();
    Element e = r.getElement();
    Hasher h = new Hasher();
    h.add(r.fhirType()).add(r.getId()).add(r.getTitle()).add(r.getResourceName()).add(r.getResourceDescription())
      .add(Boolean.toString(r.isExample())).add(r.getExampleUri()).add(r.getPath()).add(r.getLocalRef());
    for (String s : r.getProfiles(false)) {
      h.add("profile").add(s);
      n.addReference(s);
    }
    for (String s : r.getTestArtifacts()) {
      h.add("test").add(s);
      n.addReference(s);
    }
    n.addReference(r.getExampleUri());
    if (e != null) {
      n.setUrl(singleValue(e, "url"));
      n.setVersion(singleValue(e, "version"));
      n.setName(singleValue(e, "name"));
      n.setTitle(singleValue(e, "title"));
      if ("StructureDefinition".equals(r.fhirType())) {
        n.setBaseDefinition(e.getNamedChildValue("baseDefinition"));
        // what the validator checks of a target profile, besides that it exists
        n.addIdentity(singleValue(e, "type")).addIdentity(singleValue(e, "kind")).addIdentity(singleValue(e, "derivation"))
          .addIdentity(singleValue(e, "abstract"));
        // and, for a logical model, whether it can be the target of a reference
        for (Element x : e.getChildrenByName("extension")) {
          String u = x.getNamedChildValue("url");
          if (u != null && (u.endsWith("/structuredefinition-type-characteristics") || u.endsWith("/logical-target"))) {
            for (Element v : x.getChildList()) {
              if (v.getName().startsWith("value")) {
                n.addIdentity(u+"="+v.primitiveValue());
              }
            }
          }
        }
      }
      h.add("(").add(e.getName()).add(e.fhirType());
      for (String c : e.getComments()) {
        h.add("comment").add(c);
      }
      for (Element c : e.getChildList()) {
        if ("date".equals(c.getName()) && buildDate.equals(c.getValue())) {
          // stamped by the loader because the source has no date: the same input every build, so hash it as such
          h.add("(build date)");
        } else {
          scan(c, e, null, h, n);
        }
      }
      h.add(")");
    }
    n.setContentHash(h.finish());
    if (definitionsReadTargets(r, n)) {
      n.setReadsTargets(true);
    }
    return n;
  }

  /** the profiles and extensions that the resource being scanned claims or uses (meta.profile, Extension.url) */
  private final Set<String> definitions = new LinkedHashSet<>();

  /** for each url: whether the StructureDefinition it names in the context reads the targets of references */
  private final Map<String, Boolean> definitionReadsTargets = new HashMap<>();

  /**
   * Whether validating the resource can read the content of what it refers to through a definition the IG doesn't hold
   * (the analysis looks at the IG's own): its type's (Observation's obs-9 has a resolve()), a profile or extension from a
   * package, or a type they use. Without a context to look in, it may
   */
  private boolean definitionsReadTargets(FetchedResource r, ResourceNode n) {
    if (pf.context == null) {
      return true;
    }
    if ("MeasureReport".equals(r.fhirType())) {
      // its validator reads what it refers to: a subject-list MeasureReport counts the entries of its Lists
      return true;
    }
    Set<String> urls = new LinkedHashSet<>();
    urls.add("http://hl7.org/fhir/StructureDefinition/"+r.fhirType());
    urls.addAll(r.getProfiles(false));
    urls.addAll(definitions);
    for (String u : urls) {
      if (definitionReadsTargets(u)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether the StructureDefinition the url names, or any definition it reaches through its elements' types, reads the
   * targets of references. A walk over what it reaches: a definition is only known not to read them when nothing it
   * reaches does, so a false is kept for everything the walk saw only once the walk is over (a cycle can't leave a
   * false behind for a definition that reaches one that does)
   */
  private boolean definitionReadsTargets(String root) {
    Boolean known = definitionReadsTargets.get(root);
    if (known != null) {
      return known;
    }
    Set<String> seen = new HashSet<>();
    Deque<String> todo = new ArrayDeque<>();
    todo.push(root);
    boolean res = false;
    try {
      while (!todo.isEmpty() && !res) {
        String url = todo.pop();
        Boolean k = definitionReadsTargets.get(url);
        if (Boolean.TRUE.equals(k)) {
          res = true;
        } else if (k == null && seen.add(url)) {
          StructureDefinition sd = pf.context.fetchResource(StructureDefinition.class, url);
          if (sd != null) {
            // the validator checks against the profiles it imposes too
            for (org.hl7.fhir.model.core.Extension x : sd.getExtension()) {
              if (x.getUrl() != null && x.getUrl().endsWith("/structuredefinition-imposeProfile") && x.hasValue()) {
                todo.push(x.getValue().primitiveValue());
              }
            }
            if (!sd.hasSnapshot()) {
              // the constraints it inherits aren't known
              res = sd.getDerivation() == StructureDefinition.TypeDerivationRule.CONSTRAINT || readsTargets(sd.getDifferential().getElementList(), todo);
            } else {
              res = readsTargets(sd.getSnapshot().getElementList(), todo);
            }
          }
        }
      }
    } catch (RuntimeException e) {
      res = true;
    }
    if (res) {
      definitionReadsTargets.put(root, true);
    } else {
      for (String u : seen) {
        definitionReadsTargets.put(u, false);
      }
    }
    return res;
  }

  /** whether the elements read the targets of references themselves; the definitions their types name are added to todo */
  private boolean readsTargets(List<ElementDefinition> elements, Deque<String> todo) {
    for (ElementDefinition ed : elements) {
      for (ElementDefinition.ElementDefinitionConstraintComponent c : ed.getConstraintList()) {
        if (readsTargets(c.getExpression())) {
          return true;
        }
      }
      if (ed.hasSlicing()) {
        for (ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent d : ed.getSlicing().getDiscriminatorList()) {
          if (readsTargets(d.getPath()) || discriminatorFollowsReference(d.getPath())) {
            return true;
          }
        }
      }
      for (ElementDefinition.TypeRefComponent t : ed.getTypeList()) {
        for (CanonicalType p : t.getProfileList()) {
          if (p.hasValue()) {
            todo.push(p.getValue());
          }
        }
        String code = t.getWorkingCode();
        if (code != null) {
          todo.push(Utilities.isAbsoluteUrl(code) ? code : "http://hl7.org/fhir/StructureDefinition/"+code);
        }
      }
    }
    return false;
  }

  private static boolean readsTargets(String expression) {
    return expression != null && (expression.contains("resolve()") || expression.contains("conformsTo("));
  }

  /** a discriminator path that goes through a Reference's reference: the validator fetches the target and goes on in it */
  static boolean discriminatorFollowsReference(String path) {
    return path != null && Arrays.asList(path.split("\\.")).contains("reference");
  }

  // a canonical resource's url, version, name and title are single primitives; other resources can repeat them (Device.name)
  static String singleValue(Element e, String name) {
    if (!e.hasChildren()) {
      return null;
    }
    List<Element> l = e.getChildrenByName(name);
    return l.size() == 1 && l.get(0).isPrimitive() ? l.get(0).getValue() : null;
  }

  private void scan(Element e, Element parent, Element grandparent, Hasher h, ResourceNode n) {
    h.add("(").add(e.getName()).add(e.fhirType());
    if (e.isResource()) {
      // a contained resource, or one in a Bundle or Parameters: the validator checks the references to it against
      // the whole of their target profiles (CHECK_VALID)
      n.setReadsTargets(true);
    }
    if (e.isXhtml()) {
      String x = e.getXhtml() == null ? e.getValue() : new XhtmlComposer(XhtmlComposer.XML).compose(e.getXhtml());
      h.add(x);
      // narrative only links to what it mentions
      n.addLink(x);
    } else if (e.hasValue()) {
      h.add(e.getValue());
      if (("profile".equals(e.getName()) && "meta".equals(parent.getName()))
          || ("url".equals(e.getName()) && Utilities.existsInList(parent.getName(), "extension", "modifierExtension"))) {
        definitions.add(e.getValue());
      }
      if ("path".equals(e.getName()) && "discriminator".equals(parent.getName()) && discriminatorFollowsReference(e.getValue())) {
        n.setReadsTargets(true);
      }
      if (weakReferences && "reference".equals(e.getName()) && "Reference".equals(parent.fhirType())) {
        n.addInstanceReference(e.getValue());
      } else if ("targetProfile".equals(e.getName()) && "type".equals(parent.getName()) && grandparent != null
          && "ElementDefinition".equals(grandparent.fhirType())) {
        n.addLink(e.getValue());
      } else {
        n.addString(e.getValue());
      }
    }
    for (String c : e.getComments()) {
      h.add("comment").add(c);
    }
    if (e.hasChildren()) {
      for (Element c : e.getChildList()) {
        scan(c, e, parent, h, n);
      }
    }
    h.add(")");
  }

  // ---- global hash ---------------------------------------------------------------------------------------------------

  private void computeGlobal(FetchedFile igf) throws IOException {
    global.put("command", String.valueOf(System.getProperty("sun.java.command")));
    Hasher jar = new Hasher();
    jar.add(IGVersionUtil.getVersion());
    try {
      jar.add(codeHash(new File(RapidoTracker.class.getProtectionDomain().getCodeSource().getLocation().toURI())));
      // run from an IDE or the build, the core libraries are on the class path rather than in the publisher's jar
      for (String s : String.valueOf(System.getProperty("java.class.path")).split(File.pathSeparator)) {
        jar.add(codeHash(new File(s)));
      }
    } catch (Exception e) {
      jar.add(Long.toString(System.nanoTime())); // can't tell what the publisher is, so assume it changed
    }
    global.put("publisher", jar.finish());
    global.put("ig", new Hasher().add(igf.getPath()).add(igf.getSource()).finish());
    // the loader stamps the build date on canonicals that have none, and pages show it ("Draft as of ..."); FHIRPath
    // today() and now() see it too. A page replayed on a later day would show the day it was first rendered
    global.put("date", buildDay(pf.getExecTime()));

    Set<String> skip = otherSkip;
    for (String s : new String[] {Utilities.path(pf.rootDir, "output"), Utilities.path(pf.rootDir, "temp"), pf.tempDir, pf.tempLangDir,
      Utilities.path(pf.rootDir, "template"), Utilities.path(pf.rootDir, "input-cache"), pf.vsCache, pf.qaDir, Utilities.path(pf.rootDir, ".git"),
      Utilities.path(pf.rootDir, "input", "fsh"), Utilities.path(pf.rootDir, "fsh")}) {
      if (s != null) {
        skip.add(canonical(s));
      }
    }
    if (pf.repoRoot != null) {
      // written by the build itself (HTMLInspector's conformance statement check), not read by it
      skip.add(canonical(Utilities.path(pf.repoRoot, "Requirements-fromNarrative.json")));
    }
    if (pf.killFile != null) {
      skip.add(canonical(pf.killFile.getAbsolutePath()));
    }
    for (String s : pf.binaryPaths) {
      // the cql (and other adjunct) files: what they contribute ends up in the content of the resources that use them
      skip.add(canonical(s));
    }
    for (FetchedFile f : pf.fileList) {
      if (!f.isFolder() && f.getPath() != null) {
        skip.add(canonical(f.getPath()));
      }
    }
    Hasher tmpl = new Hasher();
    boolean[] templateUsesFshIndex = new boolean[1];
    hashTree(new File(Utilities.path(pf.rootDir, "template")), tmpl, Collections.emptySet(), templateUsesFshIndex);
    global.put("template", tmpl.finish());
    if (!templateUsesFshIndex[0]) {
      // written by sushi on every run, and not read by the publisher or the template
      skip.add(canonical(Utilities.path(pf.rootDir, "fsh-generated", "fsh-index.txt")));
      skip.add(canonical(Utilities.path(pf.rootDir, "fsh-generated", "data", "fsh-index.json")));
    }
    otherFiles.putAll(scanOtherFiles());
    global.put("other-files", hashOtherFiles(otherFiles));
  }

  /**
   * The publisher's code: a jar, or (run from an IDE or the build) a folder of classes, whose own length and date don't
   * change when the classes in it are compiled again
   */
  static String codeHash(File f) throws IOException {
    Hasher h = new Hasher().add(f.getAbsolutePath());
    if (f.isDirectory()) {
      try (Stream<Path> s = Files.walk(f.toPath())) {
        for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile).sorted()::iterator) {
          File c = p.toFile();
          h.add(p.toString()).add(Long.toString(c.length())).add(Long.toString(c.lastModified()));
        }
      }
    } else {
      h.add(Long.toString(f.length())).add(Long.toString(f.lastModified()));
    }
    return h.finish();
  }

  private static String hashOtherFiles(Map<String, String> files) {
    Hasher other = new Hasher();
    for (Map.Entry<String, String> e : files.entrySet()) {
      other.add(e.getKey()).add(e.getValue());
    }
    return other.finish();
  }
  // getCanonicalPath doesn't resolve links on Windows (JDK-8003887), so it can't spot a loop there
  private static Path realPath(Path p) {
    try {
      return p.toRealPath();
    } catch (IOException e) {
      return p.toAbsolutePath().normalize();
    }
  }

  /**
   * whether p is a file to read: on Windows, a file that is a reparse point but not a link (a OneDrive file that is only
   * online, say) isn't a regular file for java.nio, but is one for java.io and the build. A FIFO or socket is neither.
   * java.io is asked only when it names the same file: for a name the JVM can't decode, it names another one
   */
  private static boolean isFile(Path p) {
    if (Files.isRegularFile(p)) {
      return true;
    }
    File f = p.toFile();
    try {
      return f.toPath().equals(p) && !Files.isDirectory(p) && f.isFile();
    } catch (InvalidPathException e) {
      return false;
    }
  }

  private static String canonical(String s) {
    try {
      return new File(s).getCanonicalPath();
    } catch (IOException e) {
      return new File(s).getAbsolutePath();
    }
  }

  /** the other files in the IG folder (see computeGlobal), relative path to content hash */
  private Map<String, String> scanOtherFiles() {
    return scanTree(new File(pf.rootDir), otherSkip);
  }

  /**
   * every file under root (skipping the canonical paths in skip), relative path to content hash; safe on link loops,
   * and on names the JVM can't decode (see listTree)
   */
  static Map<String, String> scanTree(File root, Set<String> skip) {
    Map<String, String> res = new TreeMap<>();
    Path r = path(root);
    if (r == null) {
      // nothing in it can be read either: can't tell what changed, so assume something did
      res.put("", "unreadable: "+UUID.randomUUID());
      return res;
    }
    listTree(r, "", skip, res, new HashSet<>());
    return res;
  }

  /**
   * Lists dir, which is at rel (with '/' between the names on any OS, as the state is the same file on any OS) below the
   * root. It goes by the Paths a folder listing gives, not by Files made from names: such a Path keeps the bytes of the
   * name, so a file whose name the JVM can't decode (bytes not valid in the file system encoding, or any non-ASCII name
   * under a POSIX locale) is still read, and a change to it seen. Such names can decode to the same string, so a name is
   * made unique among those in its folder
   */
  private static void listTree(Path dir, String rel, Set<String> skip, Map<String, String> files, Set<Path> visited) {
    if (skip.contains(canonical(dir.toString()))) {
      return;
    }
    if (isFile(dir)) {
      try {
        files.put(rel, new Hasher().add(Files.readAllBytes(dir)).finish());
      } catch (IOException e) {
        files.put(rel, "unreadable: "+UUID.randomUUID()); // can't tell what it holds, so assume it changed
      }
      return;
    }
    if (!Files.isDirectory(dir)) {
      if (!rel.isEmpty() && !Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
        // listed, but can't be looked at (in a folder that can be listed but not searched): can't tell what it is
        files.put(rel, "unreadable: "+UUID.randomUUID());
      }
      return; // else a link to nothing, a socket, ...: not read by anything
    }
    // a Path, not its name: names that decode the same are different folders
    Path real = realPath(dir);
    if (!visited.add(real)) {
      // a link to a folder that's already been listed (possibly one that contains it)
      files.put(rel + "/", "(same as "+real+")");
      return;
    }
    List<Path> list = list(dir);
    if (list == null) {
      // can't tell what's in it, so assume something changed. What the build owns, and .git, are skipped above, so
      // this is a folder of the IG's own
      files.put(rel + "/", "unreadable: "+UUID.randomUUID());
      return;
    }
    if (list.isEmpty()) {
      files.put(rel + "/", "(empty folder)");
    }
    Set<String> names = new HashSet<>();
    for (Path p : list) {
      String n = p.getFileName().toString();
      String u = n;
      for (int i = 2; !names.add(u); i++) {
        u = n + "#" + i;
      }
      listTree(p, rel.isEmpty() ? u : rel + "/" + u, skip, files, visited);
    }
  }

  /** see listTree() for why this goes by Paths */
  static void hashTree(File root, Hasher h, Set<String> skip, boolean[] fshIndex) throws IOException {
    Path r = path(root);
    if (r == null) {
      h.add("unreadable").add(root.getAbsolutePath()).add(UUID.randomUUID().toString()); // assume it changed
    } else {
      hashTree(r, h, skip, fshIndex);
    }
  }

  private static void hashTree(Path dir, Hasher h, Set<String> skip, boolean[] fshIndex) throws IOException {
    if (!Files.exists(dir)) {
      h.add("missing").add(dir.toAbsolutePath().toString());
      return;
    }
    if (skip.contains(canonical(dir.toString()))) {
      return;
    }
    if (isFile(dir)) {
      byte[] b = Files.readAllBytes(dir);
      h.add(dir.toAbsolutePath().toString()).add(b);
      if (fshIndex != null && !fshIndex[0] && new String(b, StandardCharsets.ISO_8859_1).contains("fsh-index")) {
        fshIndex[0] = true;
      }
      return;
    }
    if (!Files.isDirectory(dir)) {
      return; // a FIFO, a socket, ...: not read by anything
    }
    List<Path> list = list(dir);
    if (list == null) {
      h.add("unreadable").add(dir.toAbsolutePath().toString()).add(UUID.randomUUID().toString()); // assume it changed
      return;
    }
    h.add("dir").add(dir.toAbsolutePath().toString());
    for (Path p : list) {
      hashTree(p, h, skip, fshIndex);
    }
  }

  /** the folder's entries, sorted (so that which path to a linked folder is listed doesn't vary); null if it can't be listed */
  private static List<Path> list(Path dir) {
    List<Path> res = new ArrayList<>();
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
      for (Path p : ds) {
        res.add(p);
      }
    } catch (IOException | DirectoryIteratorException e) {
      return null;
    }
    Collections.sort(res);
    return res;
  }

  /** the Path of a File, or null if its name isn't a valid Path (any non-ASCII name under a POSIX locale) */
  private static Path path(File f) {
    try {
      return f.toPath();
    } catch (InvalidPathException e) {
      return null;
    }
  }

  /**
   * The file's content, read through java.io, as the build reads and writes its files: under a POSIX locale, the build
   * writes a non-ASCII name with '?' for what can't be encoded, and that name isn't a valid Path
   */
  static byte[] read(File f) throws IOException {
    try (FileInputStream in = new FileInputStream(f)) {
      return in.readAllBytes();
    }
  }

  /** the day of the build, in the time zone the build date is stamped in */
  static String buildDay(Calendar execTime) {
    return new DateTimeType(execTime).asStringValue().substring(0, 10);
  }

  /**
   * The packages the build has loaded (into the context) so far, each with a hash of its package.json in the package
   * cache, which changes whenever the package is downloaded again. Packages are compared one by one, so that packages
   * this IG doesn't use can come and go in the cache without making the next build a complete one.
   */
  private Map<String, String> loadedPackages() {
    Map<String, String> res = new TreeMap<>();
    if (pf.context == null) {
      return res;
    }
    for (String p : pf.context.getLoadedPackages()) {
      res.put(p, packageHash(p));
    }
    return res;
  }

  private String packageHash(String p) {
    Hasher h = new Hasher();
    String folder = pf.pcm == null ? null : pf.pcm.getFolder();
    if (folder != null) {
      try {
        File pj = new File(Utilities.path(folder, p.replace("|", "#"), "package", "package.json"));
        if (pj.exists()) {
          return h.add(read(pj)).finish();
        }
      } catch (IOException e) {
        return h.add("unreadable: "+e.getMessage()).finish();
      }
    }
    // not in the cache (e.g. loaded from a file): there's no telling whether it changed, so assume it did
    return h.add("not in the cache").add(UUID.randomUUID().toString()).finish();
  }

  private String packageChange(JsonObject old) {
    JsonObject op = old.getJsonObject("packages");
    if (op == null) {
      return "changed: packages";
    }
    for (String p : op.getNames()) {
      if (!packageHash(p).equals(op.asString(p))) {
        return "changed: package "+p;
      }
    }
    for (String p : loadedPackages().keySet()) {
      if (!op.has(p)) {
        return "changed: package "+p+" (not loaded by the last build)";
      }
    }
    return null;
  }

  // ---- state ---------------------------------------------------------------------------------------------------------

  private JsonObject loadState() {
    File f = new File(stateFile);
    if (!f.exists()) {
      return null;
    }
    Kept k = kept(stateFile);
    if (k != null && isStillTheFile(f, k.id(), k.stamp())) {
      oldExpansions = k.expansions();
      return k.state();
    }
    keep(null);
    try {
      return readState(f);
    } catch (Exception e) {
      return new JsonObject().add("unreadable", e.getMessage() == null ? e.getClass().getName() : e.getMessage());
    }
  }

  static JsonObject readState(File f) throws IOException {
    return org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(f);
  }

  /** how a state file with that id starts (see writeState()) */
  private static String stateStart(String id) {
    return "{\"version\":"+STATE_VERSION+",\"id\":\""+id+"\"";
  }

  /**
   * whether the state file is the one that was written with that id, when it had that stamp. The stamp alone could be
   * that of another file written in the same instant with the same size; the id is different for every save
   */
  static boolean isStillTheFile(File f, String id, String stamp) {
    FileStamp now = FileStamp.of(f.getPath());
    if (id == null || stamp == null || now == null || !stamp.equals(now.text)) {
      return false;
    }
    byte[] start = stateStart(id).getBytes(StandardCharsets.UTF_8);
    try (FileInputStream in = new FileInputStream(f)) {
      return Arrays.equals(start, in.readNBytes(start.length));
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * The JSON writer escapes the values it writes, but not the names, and the state's names are paths and file names
   * (on Windows, with backslashes in them): written as they are, the next build can't read the state. So the names are
   * escaped here, as the reader unescapes them. A name with nothing to escape is written as before
   */
  static void writeState(JsonObject state, File f) throws IOException {
    // written next to the file and then moved over it, so that a build that is killed while it saves leaves the state
    // of the build before it (which the build tracker says isn't to be used) and not half of this one
    File tmp = new File(f.getPath()+".tmp");
    // as the JSON writer writes it, but written as it goes: the state of a large IG is tens of MB, and made as one
    // string (and then as bytes) first, it took seconds, at the end of each build
    try {
      try (Writer w = new BufferedWriter(new OutputStreamWriter(ManagedFileAccess.outStream(tmp), StandardCharsets.UTF_8), 1 << 16)) {
        write(w, state);
      }
      move(tmp, f);
    } catch (IOException | RuntimeException e) {
      // neither half written, nor the state of the build before: with no state, the next build is a complete one
      for (File x : new File[] {tmp, f}) {
        if (x.exists() && !x.delete()) {
          e.addSuppressed(new IOException("Unable to delete " + x));
        }
      }
      throw e;
    }
  }

  private static void move(File from, File to) throws IOException {
    try {
      Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void write(Writer w, JsonElement e) throws IOException {
    if (e instanceof JsonObject) {
      w.write('{');
      boolean first = true;
      for (JsonProperty p : ((JsonObject) e).getProperties()) {
        if (!first) {
          w.write(',');
        }
        first = false;
        w.write('"');
        w.write(escapeName(p.getName()));
        w.write("\":");
        write(w, p.getValue());
      }
      w.write('}');
    } else if (e instanceof JsonArray) {
      w.write('[');
      boolean first = true;
      for (JsonElement i : (JsonArray) e) {
        if (!first) {
          w.write(',');
        }
        first = false;
        write(w, i);
      }
      w.write(']');
    } else {
      w.write(((JsonPrimitive) e).toJson());
    }
  }

  /** The state with its names escaped (see writeState()) */
  static JsonElement escapeNames(JsonElement e) {
    if (e instanceof JsonObject) {
      JsonObject res = new JsonObject();
      for (JsonProperty p : ((JsonObject) e).getProperties()) {
        res.add(escapeName(p.getName()), escapeNames(p.getValue()));
      }
      return res;
    }
    if (e instanceof JsonArray) {
      JsonArray res = new JsonArray();
      for (JsonElement i : (JsonArray) e) {
        res.add(escapeNames(i));
      }
      return res;
    }
    return e;
  }

  static String escapeName(String s) {
    int i = 0;
    while (i < s.length() && s.charAt(i) != '"' && s.charAt(i) != '\\' && s.charAt(i) >= 0x20) {
      i++;
    }
    if (i == s.length()) {
      return s;
    }
    StringBuilder b = new StringBuilder();
    for (char c : s.toCharArray()) {
      if (c == '"' || c == '\\') {
        b.append('\\').append(c);
      } else if (c < 0x20) {
        b.append(String.format("\\u%04x", (int) c));
      } else {
        b.append(c);
      }
    }
    return b.toString();
  }

  /** saves the state for the next build. Only called once the build has finished successfully */
  public void save() throws IOException {
    JsonObject state = new JsonObject();
    String id = UUID.randomUUID().toString();
    // first, and in this order: see stateStart()
    state.add("version", STATE_VERSION);
    state.add("id", id);
    File sf = new File(stateFile);
    FileUtilities.createDirectory(sf.getParent());
    int txFailures = txFailures(pf);
    if (txFailures > 0) {
      // what the build made from those calls isn't what the server would have said, so none of it can be reused
      state.add("txFailures", txFailures);
      keep(null);
      writeState(state, sf);
      return;
    }
    state.add("uuid", uuid);
    state.add("fileSet", fileSetHash);
    // the template can write into the IG folder as well (the HL7 template copies the IG's Jira spec file there), so the
    // next build has to be compared with what the other files hold now, not with what they held when this one started.
    // (A file edited by hand while the build is running is taken as unchanged by the next build, as it would be by any
    // build tool that compares with the end of the last build)
    Map<String, String> otherNow = scanOtherFiles();
    global.put("other-files", hashOtherFiles(otherNow));
    JsonObject g = new JsonObject();
    for (Map.Entry<String, String> e : global.entrySet()) {
      g.add(e.getKey(), e.getValue());
    }
    state.add("global", g);
    JsonObject of = new JsonObject();
    for (Map.Entry<String, String> e : otherNow.entrySet()) {
      of.set(e.getKey(), e.getValue());
    }
    state.add("otherFiles", of);
    // at the end of the build, so that packages loaded on demand along the way are included
    JsonObject pk = new JsonObject();
    for (Map.Entry<String, String> e : loadedPackages().entrySet()) {
      pk.set(e.getKey(), e.getValue());
    }
    state.add("packages", pk);
    JsonObject files = new JsonObject();
    Set<String> expansionsUsed = new HashSet<>();
    for (FetchedFile f : pf.fileList) {
      String key = keys.get(f);
      FileResult res = results.get(key);
      JsonObject nf = newFiles.getJsonObject(key);
      if (res == null || nf == null) {
        continue;
      }
      for (String phase : new String[] {PHASE_HTML, PHASE_SHEETS}) {
        if (nf.has(phase)) {
          expansionsUsed.addAll(nf.getJsonObject(phase).forceArray("expansions").asStrings());
        }
      }
      nf.set("oh", res.getOwnHash());
      nf.set("src", nodes.get(f).getSourceHash());
      JsonObject rs = new JsonObject();
      for (ResourceNode r : nodes.get(f).getResources()) {
        rs.set(r.getKey(), r.getContentHash());
      }
      nf.set("resources", rs);
      nf.set("vh", res.getValidationHash());
      nf.set("rh", res.getRenderHash());
      files.set(key, nf);
    }
    state.add("files", files);
    state.add("steps", steps.save());
    // until the file is written and its stamp read, there is nothing this process can start its next build from
    keep(null);
    writeState(state, sf);
    FileStamp stamp = FileStamp.of(stateFile);
    if (stamp != null) {
      expansions.keySet().retainAll(expansionsUsed);
      keep(new Kept(stateFile, id, stamp.text, state, expansions));
    }
    removeExpansionsExcept(expansionsUsed);
  }

  // ---- journaled expansions --------------------------------------------------------------------------------------------

  private File expansionFile(String hash) {
    return new File(expansionDir, hash+".json");
  }

  /**
   * Writes a journaled expansion to its file, unless it's there already, and returns its hash. Written under another name
   * and then moved, so that a file with the name of a hash is never part of an expansion
   */
  private String storeExpansion(String json) throws IOException {
    String hash = new Hasher().add(json).finish();
    File f = expansionFile(hash);
    if (!f.exists()) {
      FileUtilities.createDirectory(expansionDir);
      File tmp = new File(Utilities.path(expansionDir, hash+"."+UUID.randomUUID()+".tmp"));
      try {
        Files.write(tmp.toPath(), json.getBytes(StandardCharsets.UTF_8));
        move(tmp, f);
      } finally {
        Files.deleteIfExists(tmp.toPath());
      }
    }
    return hash;
  }

  /**
   * The journaled expansion with that hash, parsed: the one this process parsed before, or read from its file. null if
   * it can't be read, or isn't what was written (then the file is removed, so that it's written again)
   */
  private ValueSet loadExpansion(String hash) {
    ValueSet vs = expansions.get(hash);
    if (vs == null) {
      vs = oldExpansions.get(hash);
    }
    if (vs == null) {
      vs = readExpansionFile(hash);
    }
    if (vs != null) {
      expansions.put(hash, vs);
    }
    return vs;
  }

  private ValueSet readExpansionFile(String hash) {
    File f = expansionFile(hash);
    ValueSet vs = null;
    try {
      String json = new String(read(f), StandardCharsets.UTF_8);
      if (hash.equals(new Hasher().add(json).finish())) {
        vs = readExpansion(new JsonParser(pf.context.getModelContext()), json);
      }
    } catch (Exception e) {
      // as for a file that doesn't hold what was written
    }
    if (vs == null) {
      f.delete();
    }
    return vs;
  }

  /** removes the expansions (and what a killed build left of one) that the state just saved doesn't refer to */
  private void removeExpansionsExcept(Set<String> used) {
    File[] list = new File(expansionDir).listFiles();
    if (list != null) {
      for (File f : list) {
        String n = f.getName();
        if (!n.endsWith(".json") || !used.contains(n.substring(0, n.length() - 5))) {
          f.delete();
        }
      }
    }
  }

  // ---- file stamps -----------------------------------------------------------------------------------------------------

  /**
   * What the file system says of a file without reading it: its size, when it was last modified and, where the file
   * system says, when it was last changed in any way (ctime, which a program can't set), which file it is (so that
   * another file moved to its name is another file) and which device it is on.
   *
   * <p>A file is taken to hold what it held when it was hashed if its stamp is the same as then, but only if the stamp
   * was "settled" when the file was hashed: the file system's own clock, read (by writing a file in the same folder
   * tree, see clock()) before the file was read, was already past the file's times. Any write after that gets a time
   * from that clock that is no earlier than the reading of it, so later than the times in the stamp, and so changes the
   * stamp. This doesn't depend on how coarse the file system's times are: a file written in the same tick as the clock
   * was read (within the second, on a file system that keeps seconds) has a stamp that isn't settled, and is hashed in
   * the next build, as every file was before. It does assume that the file system's clock doesn't go back.
   *
   * <p>Where the file system doesn't say that much (no unix attributes, as on Windows), the stamp is the size, the
   * modified time and the creation time, and the device is the volume of the path. There, a file written again with the
   * same size by a program that then sets its modified time back to what it was is taken as unchanged.
   */
  static final class FileStamp {
    private static final boolean UNIX = FileSystems.getDefault().supportedFileAttributeViews().contains("unix");

    final String text;
    private final long earliest;
    private final long latest;
    private final Object device;

    private FileStamp(String text, long earliest, long latest, Object device) {
      this.text = text;
      this.earliest = earliest;
      this.latest = latest;
      this.device = device;
    }

    /** the stamp of the file, or null if it isn't a file, or the file system doesn't say */
    static FileStamp of(String path) {
      try {
        Path p = Paths.get(path);
        if (UNIX) {
          Map<String, Object> a = Files.readAttributes(p, "unix:isRegularFile,size,lastModifiedTime,ctime,ino,dev");
          if (!Boolean.TRUE.equals(a.get("isRegularFile"))) {
            return null;
          }
          long m = nanos((FileTime) a.get("lastModifiedTime"));
          long c = nanos((FileTime) a.get("ctime"));
          return new FileStamp(a.get("size")+" "+m+" "+c+" "+a.get("ino")+" "+a.get("dev"), Math.min(m, c), Math.max(m, c), a.get("dev"));
        }
        return basic(p);
      } catch (IOException | RuntimeException e) {
        return null;
      }
    }

    /** the stamp where the file system doesn't say more: the device is the volume the path is on */
    static FileStamp basic(Path p) throws IOException {
      BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
      if (!a.isRegularFile()) {
        return null;
      }
      long m = nanos(a.lastModifiedTime());
      Path root = p.toAbsolutePath().getRoot();
      return new FileStamp(a.size()+" "+m+" "+nanos(a.creationTime()), m, m, String.valueOf(root));
    }

    private static long nanos(FileTime t) {
      return t.to(TimeUnit.NANOSECONDS);
    }

    /**
     * whether that reading of the clock, which has to be from before this stamp was taken, was already past the times
     * in it, and is of the same device. False without a reading
     */
    boolean settledAt(FileStamp clock) {
      return clock != null && Objects.equals(device, clock.device) && latest < clock.earliest;
    }
  }

  /**
   * The file system's time now, where the files of the build are: the stamp of a file written just now, next to the
   * state. null if it can't be told
   */
  private FileStamp clock() {
    return clock(new File(new File(stateFile).getParentFile(), "clock"));
  }

  static FileStamp clock(File f) {
    try {
      FileUtilities.createDirectory(f.getParent());
      Files.write(f.toPath(), new byte[] {0});
      return FileStamp.of(f.getPath());
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  /**
   * The hash of the file's content, for a phase's record of what it wrote; and its stamp, if it is settled at that
   * reading of the clock (which has to be from before this is called), so that the file doesn't have to be read to
   * know that it still holds this
   */
  static String hashAndStamp(String path, JsonObject stamps, FileStamp clock) {
    FileStamp stamp = FileStamp.of(path);
    String hash = fileHash(path);
    if (isContentHash(hash) && stamp != null && stamp.settledAt(clock)) {
      stamps.set(path, stamp.text);
    } else {
      stamps.remove(path);
    }
    return hash;
  }

  /** whether the file holds what the phase recorded that it wrote, reading it only if its stamp doesn't say */
  static boolean holds(String path, JsonObject written, JsonObject stamps, FileStamp clock) {
    FileStamp stamp = FileStamp.of(path);
    if (stamp != null && stamp.text.equals(stamps.asString(path)) && isContentHash(written.asString(path))) {
      return true;
    }
    boolean same = fileHash(path).equals(written.asString(path));
    if (same && stamp != null && stamp.settledAt(clock)) {
      stamps.set(path, stamp.text);
    } else {
      stamps.remove(path);
    }
    return same;
  }

  /** the terminology calls of this build that got no answer, including the ones that ask the registry which server to use */
  static int txFailures(PublisherFields pf) {
    int res = pf.txFailures == null ? 0 : pf.txFailures.count();
    if (pf.context != null && pf.context.getTxClientManager() != null) {
      for (TerminologyClientManager.InternalLogEvent e : pf.context.getTxClientManager().getInternalLog()) {
        if (e.isError()) {
          res++; // the registry couldn't be reached, so the build asked the main server instead
        }
      }
    }
    return res;
  }

  /**
   * For a Rapido build, the terminology client factory: counts the calls that got no answer from the terminology
   * server (it was down, or the call timed out). An error the server answered with is an answer like any other
   */
  public static class TxFailures implements ITerminologyClientFactoryN {
    private final ITerminologyClientFactoryN factory;
    private final AtomicInteger count = new AtomicInteger();

    public TxFailures(ITerminologyClientFactoryN factory) {
      this.factory = factory;
    }

    public int count() {
      return count.get();
    }

    @Override
    public ITerminologyClientN makeClientN(IModelContext context, String id, String url, String userAgent, ToolingClientLogger logger) throws URISyntaxException {
      ITerminologyClientN client = factory.makeClientN(context, id, url, userAgent, logger);
      return (ITerminologyClientN) Proxy.newProxyInstance(ITerminologyClientN.class.getClassLoader(), new Class<?>[] {ITerminologyClientN.class}, (p, m, args) -> {
        try {
          Object res = m.invoke(client, args);
          return res == client ? p : res;
        } catch (InvocationTargetException e) {
          Throwable t = e.getCause();
          if (!isAnswer(t)) {
            count.incrementAndGet();
          }
          throw t;
        }
      });
    }

    @Override
    public String getVersion() {
      return factory.getVersion();
    }

    /** an error the server answered with: not one that says it couldn't answer (5xx, a timeout, too many requests) */
    private static boolean isAnswer(Throwable t) {
      if (!(t instanceof EFhirClientException) || !((EFhirClientException) t).hasServerErrors()) {
        return false;
      }
      int code = ((EFhirClientException) t).getCode();
      return code < 500 && code != 408 && code != 429;
    }
  }

  private JsonObject newFile(FetchedFile f) {
    String key = keys.get(f);
    JsonObject res = newFiles.getJsonObject(key);
    if (res == null) {
      res = new JsonObject();
      newFiles.add(key, res);
    }
    return res;
  }

  // ---- translation artifacts -----------------------------------------------------------------------------------------

  private static final String TRANSLATIONS = "translations";

  /**
   * Whether the translation artifacts the last build wrote for the file are what this build would write. They are made
   * from the file's resources as loaded, with the translations imported from the translation sources (which are part
   * of the content that is hashed), and the languages of the IG (in the global hash), so it's enough that the file and
   * what it depends on are unchanged, and that the artifacts are on disk as they were written. The record is carried
   * over to the next build
   */
  public boolean reuseTranslations(FetchedFile f) {
    if (complete || !validationClean.contains(f)) {
      return false;
    }
    JsonObject written = oldFiles.getJsonObject(keys.get(f)).getJsonObject(TRANSLATIONS);
    if (written == null) {
      return false;
    }
    for (String path : written.getNames()) {
      String stamp = fileStamp(path);
      // (a file that wasn't written where it was expected has no stamp, and is never reused)
      if (stamp == null || !stamp.equals(written.asString(path))) {
        return false;
      }
    }
    newFile(f).set(TRANSLATIONS, written);
    return true;
  }

  /** the translation artifacts written for the file, to check before they're reused in the next build */
  public void recordTranslations(FetchedFile f, Collection<String> paths) {
    if (!keys.containsKey(f)) {
      return;
    }
    JsonObject written = new JsonObject();
    for (String path : paths) {
      written.set(path, fileStamp(path));
    }
    newFile(f).set(TRANSLATIONS, written);
  }

  /** size and time of the last change of a file, or null if it isn't one */
  static String fileStamp(String path) {
    try {
      BasicFileAttributes a = Files.readAttributes(new File(path).toPath(), BasicFileAttributes.class);
      return a.isRegularFile() ? a.size()+"@"+a.lastModifiedTime().to(TimeUnit.NANOSECONDS) : null;
    } catch (IOException | InvalidPathException e) {
      return null;
    }
  }

  // ---- validation ----------------------------------------------------------------------------------------------------

  /**
   * Called just before a resource is validated.
   *
   * @return the messages from the last build if the resource doesn't need to be validated again, else null
   */
  public List<ValidationMessage> reuseValidation(FetchedFile f, FetchedResource r) {
    List<ValidationMessage> res = savedValidation(f, r);
    if (res == null) {
      Set<String> sw = statusWarnings();
      statusBefore = sw == null ? null : new HashSet<>(sw);
      profilesBefore = r.getFoundProfiles().size();
    } else {
      validationsReused++;
    }
    return res;
  }

  /** e.g. "reused 12 of 15 validations", for the log */
  public String validationSummary() {
    return "reused "+validationsReused+" of "+(validationsReused+validationsDone)+" validations";
  }

  private List<ValidationMessage> savedValidation(FetchedFile f, FetchedResource r) {
    if (!validationClean.contains(f)) {
      return null;
    }
    JsonObject v = oldFiles.getJsonObject(keys.get(f)).getJsonObject("validation");
    String ri = Integer.toString(f.getResources().indexOf(r));
    JsonObject rv = v == null ? null : v.getJsonObject(ri);
    if (rv == null || !(r.fhirType()+"/"+r.getId()).equals(rv.asString("key"))) {
      return null;
    }
    List<ValidationMessage> res = new ArrayList<>();
    for (JsonObject m : rv.forceArray("messages").asJsonObjects()) {
      ValidationMessage vm = readMessage(m);
      if (vm == null) {
        return null;
      }
      res.add(vm);
    }
    // the validator only mentions a deprecated (etc) definition the first time it sees it in a build, so what it says
    // about this resource depends on what it has seen before it, and what it sees here changes what it says later
    Set<String> sw = statusWarnings();
    if (sw == null || !statusHash(sw).equals(rv.asString("statusBefore"))) {
      return null;
    }
    // validating an example tells the publisher which of the IG's profiles it conforms to (recordProfileUsage), which
    // the profiles' "no examples" checks and example lists depend on
    List<FetchedResource> sources = profileSources(pf.fileList, rv.getJsonArray("profileUsages"));
    if (sources == null) {
      return null;
    }
    // the validator attaches the profiles it validated an element against to the element, for the qa page's signposts
    if (!restoreSignposts(r.getElement(), rv.getJsonArray("signposts"), res)) {
      return null;
    }
    for (FetchedResource source : sources) {
      source.addFoundExample(r);
      r.getFoundProfiles().add(((CanonicalResource) source.getResource()).getUrl());
    }
    sw.addAll(rv.forceArray("statusAdded").asStrings());
    newFile(f).forceObject("validation").set(ri, rv);
    return res;
  }

  /** records the messages from validating a resource, before the publisher adds them to the file */
  public void recordValidation(FetchedFile f, FetchedResource r, List<ValidationMessage> errs) {
    validationsDone++;
    Set<String> sw = statusWarnings();
    if (!keys.containsKey(f) || sw == null || statusBefore == null) {
      return; // without knowing what the validator had seen, the result can't be reused
    }
    JsonObject rv = new JsonObject();
    rv.add("key", r.fhirType()+"/"+r.getId());
    rv.add("statusBefore", statusHash(statusBefore));
    JsonArray added = rv.forceArray("statusAdded");
    for (String s : new TreeSet<>(sw)) {
      if (!statusBefore.contains(s)) {
        added.add(s);
      }
    }
    JsonArray usages = rv.forceArray("profileUsages");
    for (String url : r.getFoundProfiles().subList(Math.min(profilesBefore, r.getFoundProfiles().size()), r.getFoundProfiles().size())) {
      usages.add(url);
    }
    if (profileSources(pf.fileList, usages) == null) {
      return;
    }
    JsonArray msgs = rv.forceArray("messages");
    for (ValidationMessage vm : errs) {
      JsonObject m = writeMessage(vm);
      if (m == null) {
        return; // can't be saved, so the resource will be validated again next time
      }
      msgs.add(m);
    }
    JsonArray signposts = recordSignposts(r.getElement(), errs);
    if (signposts == null) {
      return;
    }
    rv.add("signposts", signposts);
    newFile(f).forceObject("validation").set(Integer.toString(f.getResources().indexOf(r)), rv);
  }

  /**
   * The messages the validator attached to the elements of the resource (Element.addMessage(): the profiles it
   * validated each element against), with where each element is (the index of each child from the root) and its path.
   * A message that is also one of the resource's messages is recorded as its index there, so that it comes back as the
   * same object, as in a stock build. null if a message can't be saved.
   */
  static JsonArray recordSignposts(Element root, List<ValidationMessage> errs) {
    JsonArray res = new JsonArray();
    Map<ValidationMessage, Integer> index = new IdentityHashMap<>();
    for (int i = 0; i < errs.size(); i++) {
      index.putIfAbsent(errs.get(i), i);
    }
    return root == null || recordSignposts(root, "", index, res) ? res : null;
  }

  private static boolean recordSignposts(Element e, String at, Map<ValidationMessage, Integer> index, JsonArray res) {
    if (e.hasMessages()) {
      JsonObject o = new JsonObject();
      o.add("at", at);
      o.add("path", String.valueOf(e.getPath()));
      JsonArray msgs = o.forceArray("messages");
      for (ValidationMessage vm : e.getMessages()) {
        Integer i = index.get(vm);
        if (i != null) {
          msgs.add(i);
        } else {
          JsonObject m = writeMessage(vm);
          if (m == null) {
            return false;
          }
          msgs.add(m);
        }
      }
      res.add(o);
    }
    if (e.hasChildren()) {
      int i = 0;
      for (Element c : e.getChildList()) {
        if (!recordSignposts(c, at.isEmpty() ? Integer.toString(i) : at+"."+i, index, res)) {
          return false;
        }
        i++;
      }
    }
    return true;
  }

  /**
   * Attaches the messages recordSignposts() recorded to the elements of the resource, as validating it would have.
   * false (and nothing attached) if an element isn't where it was, or a message can't be read.
   *
   * @param msgs the resource's messages, as restored
   */
  static boolean restoreSignposts(Element root, JsonArray signposts, List<ValidationMessage> msgs) {
    if (signposts == null || root == null) {
      return signposts != null && signposts.size() == 0;
    }
    Map<Element, List<ValidationMessage>> found = new LinkedHashMap<>();
    for (JsonElement je : signposts.getItems()) {
      if (!(je instanceof JsonObject)) {
        return false;
      }
      JsonObject o = (JsonObject) je;
      Element e = root;
      String at = o.asString("at");
      if (at == null) {
        return false;
      }
      if (!at.isEmpty()) {
        for (String s : at.split("\\.")) {
          int i;
          try {
            i = Integer.parseInt(s);
          } catch (NumberFormatException ex) {
            return false;
          }
          if (!e.hasChildren() || i < 0 || i >= e.getChildList().size()) {
            return false;
          }
          e = e.getChildList().get(i);
        }
      }
      if (!String.valueOf(e.getPath()).equals(o.asString("path")) || o.getJsonArray("messages") == null) {
        return false;
      }
      List<ValidationMessage> list = new ArrayList<>();
      for (JsonElement m : o.getJsonArray("messages").getItems()) {
        ValidationMessage vm = null;
        if (m instanceof JsonObject) {
          vm = readMessage((JsonObject) m);
        } else if (m instanceof JsonNumber) {
          Integer i = ((JsonNumber) m).getInteger();
          vm = i != null && i >= 0 && i < msgs.size() ? msgs.get(i) : null;
        }
        if (vm == null) {
          return false;
        }
        list.add(vm);
      }
      found.put(e, list);
    }
    for (Map.Entry<Element, List<ValidationMessage>> e : found.entrySet()) {
      for (ValidationMessage vm : e.getValue()) {
        e.getKey().addMessage(vm);
      }
    }
    return true;
  }

  /**
   * The IG's profiles that a resource was found to conform to when it was validated, as the publisher's
   * recordProfileUsage finds them. null if one of them isn't in the IG (or the record is from a build that didn't
   * keep them), so the resource has to be validated again
   */
  static List<FetchedResource> profileSources(List<FetchedFile> files, JsonArray usages) {
    if (usages == null) {
      return null;
    }
    List<FetchedResource> res = new ArrayList<>();
    for (String url : usages.asStrings()) {
      FetchedResource source = null;
      for (FetchedFile f : files) {
        for (FetchedResource r : f.getResources()) {
          if (r.getResource() instanceof StructureDefinition && url.equals(((StructureDefinition) r.getResource()).getUrl())) {
            source = r;
          }
        }
      }
      if (source == null) {
        return null;
      }
      res.add(source);
    }
    return res;
  }

  /** a file with no resources to validate still needs a record, so the next build knows it can be reused */
  public void finishValidation() {
    for (FetchedFile f : pf.fileList) {
      if (keys.containsKey(f)) {
        newFile(f).forceObject("validation");
      }
    }
  }

  /**
   * The validator's record of the deprecated, withdrawn, retired and experimental definitions it has already
   * mentioned in this build (BaseValidator.statusWarnings). null if it can't be read
   */
  @SuppressWarnings("unchecked")
  private Set<String> statusWarnings() {
    if (pf.validator == null) {
      return null;
    }
    for (Class<?> c = pf.validator.getClass(); c != null; c = c.getSuperclass()) {
      try {
        Field fld = c.getDeclaredField("statusWarnings");
        fld.setAccessible(true);
        Object v = fld.get(pf.validator);
        return v instanceof Set ? (Set<String>) v : null;
      } catch (NoSuchFieldException e) {
        // look in the superclass
      } catch (Exception e) {
        return null;
      }
    }
    return null;
  }

  private static String statusHash(Set<String> statusWarnings) {
    Hasher h = new Hasher();
    for (String s : new TreeSet<>(statusWarnings)) {
      h.add(s);
    }
    return h.finish();
  }

  private static final List<Field> MESSAGE_FIELDS = new ArrayList<>();
  private static boolean messageFieldsOk = true;

  static {
    for (Field fld : ValidationMessage.class.getDeclaredFields()) {
      if (!Modifier.isStatic(fld.getModifiers())) {
        try {
          fld.setAccessible(true);
          MESSAGE_FIELDS.add(fld);
        } catch (Exception e) {
          messageFieldsOk = false;
        }
      }
    }
  }

  /** all the fields of the message, so that it comes back exactly as it was. null if that's not possible */
  static JsonObject writeMessage(ValidationMessage vm) {
    if (!messageFieldsOk) {
      return null;
    }
    JsonObject res = new JsonObject();
    try {
      for (Field fld : MESSAGE_FIELDS) {
        Object v = fld.get(vm);
        if (v == null) {
          continue;
        }
        Class<?> t = fld.getType();
        if (t == String.class) {
          res.add(fld.getName(), (String) v);
        } else if (t == int.class) {
          res.add(fld.getName(), (Integer) v);
        } else if (t == boolean.class) {
          res.add(fld.getName(), (Boolean) v);
        } else if (t.isEnum()) {
          res.add(fld.getName(), ((Enum<?>) v).name());
        } else if (t == Date.class) {
          res.add(fld.getName(), ((Date) v).getTime());
        } else if (List.class.isAssignableFrom(t)) {
          JsonArray a = new JsonArray();
          for (Object o : (List<?>) v) {
            if (!(o instanceof ValidationMessage)) {
              return null;
            }
            JsonObject m = writeMessage((ValidationMessage) o);
            if (m == null) {
              return null;
            }
            a.add(m);
          }
          res.add(fld.getName(), a);
        } else {
          return null;
        }
      }
    } catch (IllegalAccessException e) {
      return null;
    }
    return res;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  static ValidationMessage readMessage(JsonObject json) {
    ValidationMessage vm = new ValidationMessage();
    try {
      for (Field fld : MESSAGE_FIELDS) {
        Class<?> t = fld.getType();
        if (!json.has(fld.getName())) {
          if (!t.isPrimitive()) {
            fld.set(vm, null);
          }
          continue;
        }
        if (t == String.class) {
          fld.set(vm, json.asString(fld.getName()));
        } else if (t == int.class) {
          fld.setInt(vm, json.asInteger(fld.getName()));
        } else if (t == boolean.class) {
          fld.setBoolean(vm, json.asBoolean(fld.getName()));
        } else if (t.isEnum()) {
          fld.set(vm, Enum.valueOf((Class<Enum>) t, json.asString(fld.getName())));
        } else if (t == Date.class) {
          fld.set(vm, new Date(json.asLong(fld.getName())));
        } else if (List.class.isAssignableFrom(t)) {
          List<ValidationMessage> list = new ArrayList<>();
          for (JsonObject m : json.getJsonArray(fld.getName()).asJsonObjects()) {
            ValidationMessage c = readMessage(m);
            if (c == null) {
              return null;
            }
            list.add(c);
          }
          fld.set(vm, list);
        } else {
          return null;
        }
      }
    } catch (Exception e) {
      return null;
    }
    return vm;
  }

  // ---- generation ----------------------------------------------------------------------------------------------------

  /**
   * Whether the outputs of the phase can be reused for the file. The html includes the file's messages (its
   * -validation fragment), and they're not all from validation, so they're compared at the point the html would be
   * generated. And the files the phase wrote have to hold what it wrote: other steps can write to the same paths (the
   * native xml of a Library and its first xml attachment are both Library-[id].xml), and in a complete build the
   * phase would have written them again
   */
  public boolean canReplay(FetchedFile f, String phase) {
    if (!renderClean.contains(f)) {
      return false;
    }
    JsonObject j = oldFiles.getJsonObject(keys.get(f)).getJsonObject(phase);
    String problem = null;
    if (PHASE_HTML.equals(phase) && !messagesHash(f).equals(j.asString("messages"))) {
      problem = "its messages changed";
    }
    JsonObject written = j.getJsonObject("written");
    if (problem == null && written == null) {
      problem = "no record of its outputs";
    }
    if (problem == null) {
      if (!buildClockRead) {
        buildClock = clock();
        buildClockRead = true;
      }
      JsonObject stamps = j.forceObject("stamps");
      for (String path : written.getNames()) {
        if (!holds(path, written, stamps, buildClock)) {
          problem = "an output was overwritten: "+new File(path).getName();
          break;
        }
      }
    }
    if (problem == null) {
      for (String h : j.forceArray("expansions").asStrings()) {
        if (loadExpansion(h) == null) {
          problem = "an expansion it saved can't be read";
          break;
        }
      }
    }
    if (problem != null) {
      renderClean.remove(f);
      why.put(f, problem);
      return false;
    }
    return true;
  }

  /** whether it is the hash of what a file held: not what fileHash() gives for a file that is missing, or can't be read */
  static boolean isContentHash(String hash) {
    return hash != null && !hash.equals("missing") && !hash.startsWith("unreadable: ");
  }

  static String fileHash(String path) {
    File f = new File(path);
    if (!f.isFile()) {
      return "missing";
    }
    try {
      return new Hasher().add(read(f)).finish();
    } catch (IOException e) {
      return "unreadable: "+UUID.randomUUID();
    }
  }

  private static String messagesHash(FetchedFile f) {
    Hasher h = new Hasher();
    for (ValidationMessage vm : f.getErrors()) {
      JsonObject m = writeMessage(vm);
      h.add(m == null ? UUID.randomUUID().toString() : org.hl7.fhir.utilities.json.parser.JsonParser.compose(m, false));
    }
    return h.finish();
  }

  /** what a file's generation did to the rest of the build, for one phase */
  public static class Journal {
    public final Set<String> outputs = new HashSet<>();
    public final List<String> allOutputs = new ArrayList<>();
    public final Set<String> otherFiles = new HashSet<>();
    public final List<ValidationMessage> errors = new ArrayList<>();
    public final List<ValidationMessage> fileErrors = new ArrayList<>();
    public final Map<Integer, List<ValidationMessage>> resErrors = new LinkedHashMap<>();
    /** the resources (by index) the phase found a UML diagram for, as _data/structuredefinitions.json says */
    public final Set<Integer> umlGenerated = new TreeSet<>();
    public final List<NpmEntry> npm = new ArrayList<>();
    public final List<ValueSet> expansions = new ArrayList<>();
    public final List<String[]> tracked = new ArrayList<>();
    public final List<long[]> fragUseNumbers = new ArrayList<>();
    public final List<String> fragUseKeys = new ArrayList<>();
  }

  public static class NpmEntry {
    public final String category; // an NPMPackageGenerator.Category name, or null
    public final String folder; // used if category is null
    public final String name;
    public final byte[] content;

    public NpmEntry(String category, String folder, String name, byte[] content) {
      this.category = category;
      this.folder = folder;
      this.name = name;
      this.content = content;
    }
  }

  private class Recording {
    private final FetchedFile file;
    private final String phase;
    private final JsonObject journal = new JsonObject();
    private final Set<String> outputsBefore;
    private final Map<String, String> outputHashesBefore = new HashMap<>();
    private final Set<String> otherBefore;
    private final int errorsBefore;
    private final int fileErrorsBefore;
    private final int[] resErrorsBefore;
    private final boolean[] umlBefore;
    private final int expansionsBefore;

    private Recording(FetchedFile file, String phase) {
      this.file = file;
      this.phase = phase;
      outputsBefore = new HashSet<>(file.getOutputNames());
      for (String s : outputsBefore) {
        outputHashesBefore.put(s, fileHash(s));
      }
      otherBefore = new HashSet<>(pf.otherFilesRun);
      errorsBefore = pf.errors.size();
      fileErrorsBefore = file.getErrors().size();
      resErrorsBefore = new int[file.getResources().size()];
      umlBefore = new boolean[resErrorsBefore.length];
      for (int i = 0; i < resErrorsBefore.length; i++) {
        resErrorsBefore[i] = file.getResources().get(i).getErrors().size();
        umlBefore[i] = file.getResources().get(i).isUmlGenerated();
      }
      expansionsBefore = pf.expansions.size();
      if (PHASE_HTML.equals(phase)) {
        journal.add("messages", messagesHash(file));
      }
    }
  }

  /** starts recording what generating a file does, so that it can be replayed in the next build */
  public void startRecording(FetchedFile f, String phase) {
    recording = keys.containsKey(f) ? new Recording(f, phase) : null;
  }

  public void stopRecording() {
    Recording rec = recording;
    recording = null;
    if (rec == null) {
      return;
    }
    FetchedFile f = rec.file;
    JsonObject j = rec.journal;
    JsonArray outputs = j.forceArray("outputs");
    for (String s : new TreeSet<>(f.getOutputNames())) {
      if (!rec.outputsBefore.contains(s)) {
        outputs.add(s);
      }
    }
    JsonArray other = j.forceArray("otherFiles");
    for (String s : new TreeSet<>(pf.otherFilesRun)) {
      if (!rec.otherBefore.contains(s)) {
        other.add(s);
      }
    }
    // what the phase wrote, including what it wrote over from an earlier phase, to check before it's replayed
    JsonObject written = j.forceObject("written");
    JsonObject stamps = j.forceObject("stamps");
    FileStamp clock = clock();
    for (String s : new TreeSet<>(f.getOutputNames())) {
      String h = hashAndStamp(s, stamps, clock);
      if (!h.equals(rec.outputHashesBefore.get(s))) {
        written.set(s, h);
      } else {
        stamps.remove(s);
      }
    }
    for (String s : other.asStrings()) {
      written.set(s, hashAndStamp(s, stamps, clock));
    }
    if (pf.errors.size() < rec.errorsBefore || f.getErrors().size() < rec.fileErrorsBefore || pf.expansions.size() < rec.expansionsBefore) {
      return;
    }
    // a phase that failed with an exception (a file that couldn't be written, say) is done again in the next build
    if (threw(pf.errors.subList(rec.errorsBefore, pf.errors.size())) || threw(f.getErrors().subList(rec.fileErrorsBefore, f.getErrors().size()))) {
      return;
    }
    if (!addMessages(j.forceArray("errors"), pf.errors.subList(rec.errorsBefore, pf.errors.size()))
        || !addMessages(j.forceArray("fileErrors"), f.getErrors().subList(rec.fileErrorsBefore, f.getErrors().size()))) {
      return;
    }
    if (f.getResources().size() != rec.resErrorsBefore.length) {
      return;
    }
    JsonArray uml = j.forceArray("uml");
    for (int i = 0; i < rec.umlBefore.length; i++) {
      if (!rec.umlBefore[i] && f.getResources().get(i).isUmlGenerated()) {
        uml.add(Integer.toString(i));
      }
    }
    JsonObject re = j.forceObject("resErrors");
    for (int i = 0; i < rec.resErrorsBefore.length; i++) {
      List<ValidationMessage> errs = f.getResources().get(i).getErrors();
      if (errs.size() < rec.resErrorsBefore[i] || threw(errs.subList(rec.resErrorsBefore[i], errs.size()))) {
        return;
      }
      if (errs.size() > rec.resErrorsBefore[i] && !addMessages(re.forceArray(Integer.toString(i)), errs.subList(rec.resErrorsBefore[i], errs.size()))) {
        return;
      }
    }
    JsonArray exp = j.forceArray("expansions");
    for (ValueSet vs : pf.expansions.subList(rec.expansionsBefore, pf.expansions.size())) {
      try {
        exp.add(storeExpansion(saveExpansion(new JsonParser(pf.context.getModelContext()), vs)));
      } catch (Exception e) {
        return;
      }
    }
    newFile(f).set(rec.phase, j);
  }

  private static boolean threw(List<ValidationMessage> list) {
    for (ValidationMessage vm : list) {
      if (vm.getSource() == ValidationMessage.Source.Publisher && vm.getType() == ValidationMessage.IssueType.EXCEPTION) {
        return true;
      }
    }
    return false;
  }

  private boolean addMessages(JsonArray a, List<ValidationMessage> list) {
    for (ValidationMessage vm : list) {
      JsonObject m = writeMessage(vm);
      if (m == null) {
        return false;
      }
      a.add(m);
    }
    return true;
  }

  public void recordOutput(String lowerCasePath) {
    if (recording != null) {
      recording.journal.forceArray("allOutputs").add(lowerCasePath);
    }
  }

  public void recordNpm(String category, String folder, String name, byte[] content) {
    if (recording != null) {
      JsonObject e = recording.journal.forceArray("npm").addObject();
      if (category != null) {
        e.add("category", category);
      } else {
        e.add("folder", folder);
      }
      e.add("name", name);
      e.add("content", Base64.getEncoder().encodeToString(content));
    }
  }

  public void recordTrackedFragment(String id, String name) {
    if (recording != null) {
      JsonArray a = new JsonArray();
      recording.journal.forceArray("tracked").add(a);
      a.add(id);
      a.add(name);
    }
  }

  public void recordFragmentUse(String key, long time, long size) {
    if (recording != null) {
      JsonArray a = new JsonArray();
      recording.journal.forceArray("fragUses").add(a);
      a.add(key);
      a.add(Long.toString(time));
      a.add(Long.toString(size));
    }
  }

  /** the journal of the last build for the file and phase, which is carried over to the next build */
  public Journal replay(FetchedFile f, String phase) {
    JsonObject j = oldFiles.getJsonObject(keys.get(f)).getJsonObject(phase);
    newFile(f).set(phase, j);
    Journal res = new Journal();
    res.outputs.addAll(j.forceArray("outputs").asStrings());
    res.allOutputs.addAll(j.forceArray("allOutputs").asStrings());
    res.otherFiles.addAll(j.forceArray("otherFiles").asStrings());
    for (String s : j.forceArray("uml").asStrings()) {
      res.umlGenerated.add(Integer.parseInt(s));
    }
    readMessages(j.forceArray("errors"), res.errors);
    readMessages(j.forceArray("fileErrors"), res.fileErrors);
    JsonObject re = j.forceObject("resErrors");
    for (String k : re.getNames()) {
      List<ValidationMessage> list = new ArrayList<>();
      readMessages(re.getJsonArray(k), list);
      res.resErrors.put(Integer.parseInt(k), list);
    }
    for (JsonObject e : j.forceArray("npm").asJsonObjects()) {
      res.npm.add(new NpmEntry(e.asString("category"), e.asString("folder"), e.asString("name"), Base64.getDecoder().decode(e.asString("content"))));
    }
    for (String h : j.forceArray("expansions").asStrings()) {
      // canReplay() has read it
      ValueSet vs = loadExpansion(h);
      if (vs == null) {
        throw new Error("Unable to read an expansion saved by the last Rapido build: "+h);
      }
      res.expansions.add(vs);
    }
    for (JsonElement e : j.forceArray("tracked").getItems()) {
      List<String> a = ((JsonArray) e).asStrings();
      res.tracked.add(new String[] {a.get(0), a.get(1)});
    }
    for (JsonElement e : j.forceArray("fragUses").getItems()) {
      List<String> a = ((JsonArray) e).asStrings();
      res.fragUseKeys.add(a.get(0));
      res.fragUseNumbers.add(new long[] {Long.parseLong(a.get(1)), Long.parseLong(a.get(2))});
    }
    return res;
  }

  private void readMessages(JsonArray a, List<ValidationMessage> list) {
    for (JsonObject m : a.asJsonObjects()) {
      ValidationMessage vm = readMessage(m);
      if (vm == null) {
        throw new Error("Unable to read a message saved by the last Rapido build");
      }
      list.add(vm);
    }
  }

  /**
   * Stands in for an empty text node while an expansion is saved. The XHTML parser drops empty text, so without it an
   * element whose only child was empty text (a renderer's empty table cell) would come back with no children, and be
   * written as {@code <td/>} where the build that made it wrote {@code <td></td>}.
   */
  static final String EMPTY_TEXT = "\uE000rapido:empty\uE000";

  /** the expansion as JSON, keeping its narrative's empty text nodes */
  static String saveExpansion(JsonParser json, ValueSet vs) throws IOException {
    List<XhtmlNode> marked = new ArrayList<>();
    try {
      markEmptyText(vs, marked);
      ByteArrayOutputStream bs = new ByteArrayOutputStream();
      json.setOutputStyle(OutputStyle.NORMAL).compose(bs, vs);
      return new String(bs.toByteArray(), StandardCharsets.UTF_8);
    } finally {
      for (XhtmlNode x : marked) {
        x.setContent("");
      }
    }
  }

  /** reads an expansion written by {@link #saveExpansion} */
  static ValueSet readExpansion(JsonParser json, String s) throws IOException {
    ValueSet vs = (ValueSet) json.parse(s);
    restoreEmptyText(vs);
    return vs;
  }

  private static void markEmptyText(DomainResource dr, List<XhtmlNode> marked) {
    if (dr.hasText() && dr.getText().hasDiv()) {
      markEmptyText(dr.getText().getDiv(), marked);
    }
    for (Resource c : dr.getContained()) {
      if (c instanceof DomainResource) {
        markEmptyText((DomainResource) c, marked);
      }
    }
  }

  private static void markEmptyText(XhtmlNode x, List<XhtmlNode> marked) {
    if (!x.hasChildren()) {
      return;
    }
    for (XhtmlNode c : x.getChildNodes()) {
      if (c.getNodeType() == NodeType.Text) {
        if (c.getContent() != null && c.getContent().isEmpty()) {
          c.setContent(EMPTY_TEXT);
          marked.add(c);
        }
      } else {
        markEmptyText(c, marked);
      }
    }
  }

  private static void restoreEmptyText(DomainResource dr) {
    if (dr.hasText() && dr.getText().hasDiv()) {
      XhtmlNode div = dr.getText().getDiv();
      // the composers write the attributes in order, and the parser reads xml:lang before the namespace: as the
      // renderers made it, the namespace is the first
      String ns = div.getNsDecl();
      if (ns != null && !"xmlns".equals(div.getAttributes().keySet().iterator().next())) {
        Map<String, String> attributes = new LinkedHashMap<>(div.getAttributes());
        attributes.remove("xmlns");
        div.getAttributes().clear();
        div.getAttributes().put("xmlns", ns);
        div.getAttributes().putAll(attributes);
      }
      restoreEmptyText(div);
    }
    for (Resource c : dr.getContained()) {
      if (c instanceof DomainResource) {
        restoreEmptyText((DomainResource) c);
      }
    }
  }

  private static void restoreEmptyText(XhtmlNode x) {
    if (!x.hasChildren()) {
      return;
    }
    for (XhtmlNode c : x.getChildNodes()) {
      if (c.getNodeType() == NodeType.Text) {
        // an empty text node next to other text is read back merged into it
        if (c.getContent() != null && c.getContent().contains(EMPTY_TEXT)) {
          c.setContent(c.getContent().replace(EMPTY_TEXT, ""));
        }
      } else {
        restoreEmptyText(c);
      }
    }
  }
}
