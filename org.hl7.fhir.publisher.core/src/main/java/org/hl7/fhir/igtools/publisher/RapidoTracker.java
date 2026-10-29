package org.hl7.fhir.igtools.publisher;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.FileNode;
import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.FileResult;
import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.Hasher;
import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.ResourceNode;
import org.hl7.fhir.model.core.CanonicalResource;
import org.hl7.fhir.model.core.DateTimeType;
import org.hl7.fhir.model.core.DomainResource;
import org.hl7.fhir.model.core.Resource;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.ValueSet;
import org.hl7.fhir.model.core.formats.JsonParser;
import org.hl7.fhir.model.utilities.formats.OutputStyle;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.filesystem.ManagedFileAccess;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonElement;
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
 * </ul>
 * Anything outside the files of the IG that could affect the build (the publisher itself, the command line, the IG
 * resource, the template, other files in the IG folder, the day) goes into a global hash, and each package the build loaded is
 * recorded with a hash of its package.json. If any of that changes, or the set of files changes, or the last build
 * didn't finish, the build is a complete one.
 */
public class RapidoTracker {

  public static final String PHASE_HTML = "html";
  public static final String PHASE_SHEETS = "sheets";

  private static final int STATE_VERSION = 4;

  private final PublisherFields pf;
  private final String stateFile;
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
  private boolean complete;
  private String reason;
  private Recording recording;
  /** the validator's statusWarnings before the resource being validated now, or null if they can't be read */
  private Set<String> statusBefore;
  /** how many of the IG's profiles the resource being validated was known to conform to before it was validated */
  private int profilesBefore;
  /** the date the loader puts on canonical resources that have none; it differs every build */
  private final String buildDate;

  public RapidoTracker(PublisherFields pf) throws IOException {
    this.pf = pf;
    this.stateFile = Utilities.path(pf.tempDir, "_rapido", "state.json");
    this.buildDate = new DateTimeType(pf.getExecTime()).asStringValue();
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

    for (FetchedFile f : pf.fileList) {
      nodes.put(f, fileNode(f));
    }
    results = new RapidoDependencyAnalyser(pf.igpkp.getCanonical(), pf.context == null ? null : pf.context.getResourceNames()).analyse(nodes.values());

    JsonObject old = loadState();
    reason = failure;
    if (reason == null) {
      reason = compareState(old);
    }
    if (reason == null && pf.genExamples) {
      reason = "generating examples";
    }
    if (reason == null && pf.doTransforms) {
      reason = "running transforms";
    }
    complete = reason != null;
    if (complete) {
      return;
    }
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
    return true;
  }

  public boolean isCompleteBuild() {
    return complete;
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

  private ResourceNode resourceNode(FetchedResource r) {
    ResourceNode n = new ResourceNode(r.fhirType(), r.getId());
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
      n.setUrl(e.getNamedChildValue("url"));
      n.setVersion(e.getNamedChildValue("version"));
      n.setName(e.getNamedChildValue("name"));
      n.setTitle(e.getNamedChildValue("title"));
      if ("StructureDefinition".equals(r.fhirType())) {
        n.setBaseDefinition(e.getNamedChildValue("baseDefinition"));
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
          scan(c, h, n);
        }
      }
      h.add(")");
    }
    n.setContentHash(h.finish());
    return n;
  }

  private void scan(Element e, Hasher h, ResourceNode n) {
    h.add("(").add(e.getName()).add(e.fhirType());
    if (e.isXhtml()) {
      String x = e.getXhtml() == null ? e.getValue() : new XhtmlComposer(XhtmlComposer.XML).compose(e.getXhtml());
      h.add(x);
      n.addString(x);
    } else if (e.hasValue()) {
      h.add(e.getValue());
      n.addString(e.getValue());
    }
    for (String c : e.getComments()) {
      h.add("comment").add(c);
    }
    if (e.hasChildren()) {
      for (Element c : e.getChildList()) {
        scan(c, h, n);
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
      File jf = new File(RapidoTracker.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      jar.add(jf.getAbsolutePath()).add(Long.toString(jf.length())).add(Long.toString(jf.lastModified()));
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

  private static String hashOtherFiles(Map<String, String> files) {
    Hasher other = new Hasher();
    for (Map.Entry<String, String> e : files.entrySet()) {
      other.add(e.getKey()).add(e.getValue());
    }
    return other.finish();
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

  /** every file under root (skipping the canonical paths in skip), relative path to content hash; safe on link loops */
  static Map<String, String> scanTree(File root, Set<String> skip) {
    Map<String, String> res = new TreeMap<>();
    listTree(root, root, skip, res, new HashSet<>());
    return res;
  }

  private static void listTree(File root, File dir, Set<String> skip, Map<String, String> files, Set<String> visited) {
    String c = canonical(dir.getAbsolutePath());
    if (skip.contains(c)) {
      return;
    }
    // with '/' between the names on any OS, as the state is the same file on any OS
    String rel = root.toPath().relativize(dir.toPath()).toString().replace(File.separatorChar, '/');
    if (dir.isFile()) {
      try {
        files.put(rel, new Hasher().add(Files.readAllBytes(dir.toPath())).finish());
      } catch (IOException e) {
        files.put(rel, "unreadable: "+UUID.randomUUID()); // can't tell what it holds, so assume it changed
      }
      return;
    }
    if (!visited.add(c)) {
      // a link to a folder that's already been listed (possibly one that contains it)
      files.put(rel + "/", "(same as "+c+")");
      return;
    }
    String[] list = dir.list();
    if (list == null) {
      return;
    }
    if (list.length == 0) {
      files.put(rel + "/", "(empty folder)");
    }
    java.util.Arrays.sort(list); // so that which path to a linked folder gets listed doesn't vary
    for (String s : list) {
      listTree(root, new File(dir, s), skip, files, visited);
    }
  }

  private void hashTree(File dir, Hasher h, Set<String> skip, boolean[] fshIndex) throws IOException {
    if (!dir.exists()) {
      h.add("missing").add(dir.getAbsolutePath());
      return;
    }
    if (skip.contains(canonical(dir.getAbsolutePath()))) {
      return;
    }
    if (dir.isFile()) {
      byte[] b = Files.readAllBytes(dir.toPath());
      h.add(dir.getAbsolutePath()).add(b);
      if (fshIndex != null && !fshIndex[0] && new String(b, StandardCharsets.ISO_8859_1).contains("fsh-index")) {
        fshIndex[0] = true;
      }
      return;
    }
    String[] list = dir.list();
    if (list == null) {
      return;
    }
    java.util.Arrays.sort(list);
    h.add("dir").add(dir.getAbsolutePath());
    for (String s : list) {
      hashTree(new File(dir, s), h, skip, fshIndex);
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
          return h.add(Files.readAllBytes(pj.toPath())).finish();
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
    try {
      return readState(f);
    } catch (Exception e) {
      return new JsonObject().add("unreadable", e.getMessage() == null ? e.getClass().getName() : e.getMessage());
    }
  }

  static JsonObject readState(File f) throws IOException {
    return org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(f);
  }

  /**
   * The JSON writer escapes the values it writes, but not the names, and the state's names are paths and file names
   * (on Windows, with backslashes in them): written as they are, the next build can't read the state. So the names are
   * escaped here, as the reader unescapes them. A name with nothing to escape is written as before
   */
  static void writeState(JsonObject state, File f) throws IOException {
    // as the JSON writer writes it, but written as it goes: the state of a large IG is hundreds of MB, and made as one
    // string (and then as bytes) first, it took seconds, at the end of each build
    try (Writer w = new BufferedWriter(new OutputStreamWriter(ManagedFileAccess.outStream(f), StandardCharsets.UTF_8), 1 << 16)) {
      write(w, state);
    } catch (IOException | RuntimeException e) {
      // not left half written: with no state, the next build is a complete one
      if (f.exists() && !f.delete()) {
        e.addSuppressed(new IOException("Unable to delete the incomplete " + f));
      }
      throw e;
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
    state.add("version", STATE_VERSION);
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
    for (FetchedFile f : pf.fileList) {
      String key = keys.get(f);
      FileResult res = results.get(key);
      JsonObject nf = newFiles.getJsonObject(key);
      if (res == null || nf == null) {
        continue;
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
    File sf = new File(stateFile);
    FileUtilities.createDirectory(sf.getParent());
    writeState(state, sf);
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
    }
    return res;
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
    newFile(f).forceObject("validation").set(Integer.toString(f.getResources().indexOf(r)), rv);
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
      for (String path : written.getNames()) {
        if (!fileHash(path).equals(written.asString(path))) {
          problem = "an output was overwritten: "+new File(path).getName();
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

  private static String fileHash(String path) {
    File f = new File(path);
    if (!f.isFile()) {
      return "missing";
    }
    try {
      return new Hasher().add(Files.readAllBytes(f.toPath())).finish();
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
      for (int i = 0; i < resErrorsBefore.length; i++) {
        resErrorsBefore[i] = file.getResources().get(i).getErrors().size();
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
    for (String s : new TreeSet<>(f.getOutputNames())) {
      String h = fileHash(s);
      if (!h.equals(rec.outputHashesBefore.get(s))) {
        written.set(s, h);
      }
    }
    for (String s : other.asStrings()) {
      written.set(s, fileHash(s));
    }
    if (pf.errors.size() < rec.errorsBefore || f.getErrors().size() < rec.fileErrorsBefore || pf.expansions.size() < rec.expansionsBefore) {
      return;
    }
    if (!addMessages(j.forceArray("errors"), pf.errors.subList(rec.errorsBefore, pf.errors.size()))
        || !addMessages(j.forceArray("fileErrors"), f.getErrors().subList(rec.fileErrorsBefore, f.getErrors().size()))) {
      return;
    }
    if (f.getResources().size() != rec.resErrorsBefore.length) {
      return;
    }
    JsonObject re = j.forceObject("resErrors");
    for (int i = 0; i < rec.resErrorsBefore.length; i++) {
      List<ValidationMessage> errs = f.getResources().get(i).getErrors();
      if (errs.size() < rec.resErrorsBefore[i]) {
        return;
      }
      if (errs.size() > rec.resErrorsBefore[i] && !addMessages(re.forceArray(Integer.toString(i)), errs.subList(rec.resErrorsBefore[i], errs.size()))) {
        return;
      }
    }
    JsonArray exp = j.forceArray("expansions");
    for (ValueSet vs : pf.expansions.subList(rec.expansionsBefore, pf.expansions.size())) {
      try {
        exp.add(saveExpansion(new JsonParser(pf.context.getModelContext()), vs));
      } catch (Exception e) {
        return;
      }
    }
    newFile(f).set(rec.phase, j);
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
    for (String s : j.forceArray("expansions").asStrings()) {
      try {
        res.expansions.add(readExpansion(new JsonParser(pf.context.getModelContext()), s));
      } catch (Exception e) {
        throw new Error("Unable to read an expansion saved by the last Rapido build: "+e.getMessage(), e);
      }
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
      restoreEmptyText(dr.getText().getDiv());
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
