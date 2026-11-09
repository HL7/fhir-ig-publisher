package org.hl7.fhir.igtools.publisher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out, for Rapido, which files of an IG have to be validated and rendered again after an edit.
 *
 * The analysis works on plain data (see {@link FileNode} and {@link ResourceNode}) so that it can be tested without a
 * FHIR context: {@link RapidoTracker} turns the loaded {@link FetchedFile}s into these nodes.
 *
 * Every string found in a resource is treated as a possible reference to another resource in the IG: by url (with or
 * without |version or #fragment), by Type/id (relative, or as the tail of an absolute url), as a url embedded in text
 * or xhtml, or as a [[[name]]] reference. That's deliberately broader than any one renderer or validator, because a
 * missed edge leaves stale output, while an extra one only costs a little time.
 *
 * For each file the analysis produces two hashes:
 * <ul>
 *   <li>the validation hash covers the file itself and everything it depends on, transitively (profiles, base
 *   definitions, extensions, value sets, code systems, libraries, ...)</li>
 *   <li>the render hash adds the resources that refer to the file's resources (directly, or through a profile they are
 *   derived from), because pages list their usages, examples, derived profiles and so on.</li>
 * </ul>
 * Some edges are weak: what's at the other end only needs to exist, with the same identity (key, file, url, version,
 * name, title, web path, and for a StructureDefinition its type, kind, derivation and base). The hash then holds that
 * identity rather than the target's content, and doesn't go on through the target's own dependencies:
 * <ul>
 *   <li>a Reference.reference ({@link ResourceNode#addInstanceReference}) is weak for validation, which only checks that
 *   the target exists with the right type. Rendering may show the target's content, so it's a full edge there</li>
 *   <li>ElementDefinition.type.targetProfile and narrative ({@link ResourceNode#addLink}) are weak for both: the
 *   validator checks that a target profile exists with the right type and base, and pages only link to it</li>
 * </ul>
 * A resource whose content, or anything it depends on through full edges, has a resolve() or conformsTo( reads the
 * targets of its references, so its validation hash follows every edge.
 * A reference that looks like it is meant to point into the IG, but doesn't resolve, makes the file depend on the
 * inventory of the whole IG (every id, url, name and title), so that the file is rebuilt whenever anything it might
 * have meant appears or changes.
 */
public class RapidoDependencyAnalyser {

  public static class ResourceNode {
    private final String type;
    private final String id;
    private String url;
    private String version;
    private String name;
    private String title;
    private String baseDefinition;
    private String contentHash = "";
    private final Set<String> strings = new LinkedHashSet<>();
    private final Set<String> references = new LinkedHashSet<>();
    private final Set<String> instanceReferences = new LinkedHashSet<>();
    private final Set<String> links = new LinkedHashSet<>();
    private final List<String> identity = new ArrayList<>();
    private boolean readsTargets;

    public ResourceNode(String type, String id) {
      this.type = type;
      this.id = id;
    }

    public String getKey() {
      return type+"/"+id;
    }

    public String getType() {
      return type;
    }

    public String getId() {
      return id;
    }

    public ResourceNode setUrl(String url) {
      this.url = url;
      return this;
    }

    public ResourceNode setVersion(String version) {
      this.version = version;
      return this;
    }

    public ResourceNode setName(String name) {
      this.name = name;
      return this;
    }

    public ResourceNode setTitle(String title) {
      this.title = title;
      return this;
    }

    public ResourceNode setBaseDefinition(String baseDefinition) {
      this.baseDefinition = baseDefinition;
      return this;
    }

    public ResourceNode setContentHash(String contentHash) {
      this.contentHash = contentHash;
      return this;
    }

    public String getContentHash() {
      return contentHash;
    }

    /** a string value found anywhere in the resource (primitive, markdown, narrative, ...). */
    public ResourceNode addString(String s) {
      if (s != null && !s.isEmpty()) {
        strings.add(s);
      }
      return this;
    }

    /** a reference that the publisher knows about outside the resource content (e.g. a profile an example is
     * declared against in the IG resource). Treated exactly like a string found in the content */
    public ResourceNode addReference(String s) {
      if (s != null && !s.isEmpty()) {
        references.add(s);
      }
      return this;
    }

    /** a Reference.reference: validating the resource only needs the target to exist with the right type, rendering
     * it may show the target's content */
    public ResourceNode addInstanceReference(String s) {
      if (s != null && !s.isEmpty()) {
        instanceReferences.add(s);
      }
      return this;
    }

    /** a value that only links to its target (ElementDefinition.type.targetProfile, narrative): validating and
     * rendering the resource only need the target's identity */
    public ResourceNode addLink(String s) {
      if (s != null && !s.isEmpty()) {
        links.add(s);
      }
      return this;
    }

    /** validating the resource reads the content of what it refers to, through a definition from outside the IG (its
     * type's, a profile or extension from a package) that has a resolve() or conformsTo( */
    public ResourceNode setReadsTargets(boolean readsTargets) {
      this.readsTargets = readsTargets;
      return this;
    }

    /** something about the resource that a weak edge to it depends on, besides its key, url, version, name and title
     * (its web path, a StructureDefinition's type, ...) */
    public ResourceNode addIdentity(String s) {
      identity.add(s);
      return this;
    }
  }

  public static class FileNode {
    private final String key;
    private final String sourceHash;
    private final List<ResourceNode> resources = new ArrayList<>();

    public FileNode(String key, String sourceHash) {
      this.key = key;
      this.sourceHash = sourceHash;
    }

    public String getKey() {
      return key;
    }

    public String getSourceHash() {
      return sourceHash;
    }

    public List<ResourceNode> getResources() {
      return resources;
    }

    public FileNode addResource(ResourceNode r) {
      resources.add(r);
      return this;
    }
  }

  public static class FileResult {
    private final String ownHash;
    private final String validationHash;
    private final String renderHash;
    private final Set<String> dependsOn;
    private final Set<String> usedBy;
    private final boolean usesInventory;

    private FileResult(String ownHash, String validationHash, String renderHash, Set<String> dependsOn, Set<String> usedBy, boolean usesInventory) {
      this.ownHash = ownHash;
      this.validationHash = validationHash;
      this.renderHash = renderHash;
      this.dependsOn = dependsOn;
      this.usedBy = usedBy;
      this.usesInventory = usesInventory;
    }

    /** the file's own content: its source, and the resources loaded from it */
    public String getOwnHash() {
      return ownHash;
    }

    public String getValidationHash() {
      return validationHash;
    }

    public String getRenderHash() {
      return renderHash;
    }

    /** the keys of the resources in other files that this file depends on, transitively */
    public Set<String> getDependsOn() {
      return dependsOn;
    }

    /** the keys of the resources in other files that refer to this file's resources */
    public Set<String> getUsedBy() {
      return usedBy;
    }

    public boolean isUsesInventory() {
      return usesInventory;
    }
  }

  private static final Pattern URL_IN_TEXT = Pattern.compile("(https?://|urn:(uuid|oid):)[^\\s\"'<>()\\[\\]{}|,;`\\\\]+(\\|[^\\s\"'<>()\\[\\]{},;`\\\\]+)?");
  private static final Pattern REF_TAG = Pattern.compile("\\[\\[\\[([^\\]]+)\\]\\]\\]");
  private static final Pattern RELATIVE_REF = Pattern.compile("^[A-Z][A-Za-z]{1,63}/[A-Za-z0-9\\-\\.]{1,64}(/_history/[A-Za-z0-9\\-\\.]{1,64})?$");
  private static final Pattern ID_LIKE = Pattern.compile("^[A-Za-z0-9\\-\\.]{1,64}$");
  /** longest string that is looked at as a whole as a possible reference */
  private static final int MAX_REF_LENGTH = 1024;

  private final String canonical;
  /** the type names a relative reference (Type/id) can start with, or null for any capitalised name */
  private final Set<String> resourceTypes;
  private final Index index = new Index();
  private final Map<String, List<ResourceNode>> byId = new HashMap<>();
  private final Map<String, List<ResourceNode>> byLabel = new HashMap<>();
  private final Map<ResourceNode, String> fileOf = new HashMap<>();
  private final Map<ResourceNode, Map<ResourceNode, Edge>> direct = new HashMap<>();
  private final Set<ResourceNode> unresolved = new HashSet<>();
  /** the resources whose content has a resolve() or conformsTo(, which read the content of the resources they refer to */
  private final Set<ResourceNode> readsTargets = new HashSet<>();

  /** what one resource needs of another. Declared from the strongest down: when a resource refers to another in several
   * ways, the strongest counts */
  enum Edge {
    /** the content, and what it depends on */
    FULL,
    /** a Reference.reference: the identity for validation, the content for rendering */
    REFERENCE,
    /** the identity, for validation and rendering */
    LINK;

    static Edge strongest(Edge a, Edge b) {
      return a == null || b.ordinal() < a.ordinal() ? b : a;
    }
  }

  /** which edges a hash follows through to the target's content and dependencies; the others give the target's identity */
  private enum Mode {
    ALL(EnumSet.allOf(Edge.class)),
    VALIDATION(EnumSet.of(Edge.FULL)),
    RENDER(EnumSet.of(Edge.FULL, Edge.REFERENCE));

    final Set<Edge> follows;

    Mode(Set<Edge> follows) {
      this.follows = follows;
    }
  }

  /**
   * The resources of the IG by what a value can refer to them by: Type/id, url and url|version. Built once for all the
   * files, so that looking up a value doesn't go through every resource of the IG. A key finds every resource that has
   * it, in the order they were added (two files can hold the same Type/id, or several versions of a url)
   */
  static class Index {
    private final Map<String, List<ResourceNode>> resources = new HashMap<>();

    void add(String key, ResourceNode r) {
      if (key != null) {
        List<ResourceNode> list = resources.computeIfAbsent(key, k -> new ArrayList<>());
        // a resource is added under each of its keys in turn, so one that is already there is the last one
        if (list.isEmpty() || list.get(list.size()-1) != r) {
          list.add(r);
        }
      }
    }

    /** the resources that the value refers to, in the order they were added */
    List<ResourceNode> find(String value) {
      return resources.getOrDefault(value, List.of());
    }

    boolean has(String value) {
      return resources.containsKey(value);
    }
  }

  public RapidoDependencyAnalyser(String canonical) {
    this(canonical, null);
  }

  /**
   * @param resourceTypes the resource types the build knows (null or empty: take any capitalised name as one). Strings such as
   * the v2 mapping XPN/4 look like a relative reference, but can't be one, and would otherwise make everything that
   * depends on a base snapshot depend on the inventory. The types of the IG's own resources and the ids and names of
   * its StructureDefinitions (logical models, custom resources) are added.
   */
  public RapidoDependencyAnalyser(String canonical, Collection<String> resourceTypes) {
    this.canonical = canonical;
    // an empty list would make every dangling Type/id look external, and so rebuild less: take it as unknown
    this.resourceTypes = resourceTypes == null || resourceTypes.isEmpty() ? null : new HashSet<>(resourceTypes);
  }

  public Map<String, FileResult> analyse(Collection<FileNode> files) {
    for (FileNode f : files) {
      for (ResourceNode r : f.resources) {
        index(f, r);
        if (resourceTypes != null) {
          resourceTypes.add(r.type);
          if ("StructureDefinition".equals(r.type)) {
            resourceTypes.add(r.id);
            if (r.name != null) {
              resourceTypes.add(r.name);
            }
          }
        }
      }
    }
    for (FileNode f : files) {
      for (ResourceNode r : f.resources) {
        Map<ResourceNode, Edge> targets = new LinkedHashMap<>();
        boolean ok = true;
        if (r.readsTargets) {
          readsTargets.add(r);
        }
        for (String s : r.strings) {
          ok = resolveString(s, targets, Edge.FULL) && ok;
          if (s.contains("resolve()") || s.contains("conformsTo(")) {
            readsTargets.add(r);
          }
        }
        for (String s : r.references) {
          ok = resolveString(s, targets, Edge.FULL) && ok;
        }
        for (String s : r.instanceReferences) {
          ok = resolveString(s, targets, Edge.REFERENCE) && ok;
        }
        for (String s : r.links) {
          ok = resolveString(s, targets, Edge.LINK) && ok;
        }
        targets.remove(r);
        direct.put(r, targets);
        if (!ok) {
          unresolved.add(r);
        }
      }
    }
    String inventory = inventoryHash(files);

    // a profile is used by everything its derived profiles use (bindings, extensions and so on are inherited)
    Map<ResourceNode, Map<ResourceNode, Edge>> effective = new HashMap<>();
    for (ResourceNode r : direct.keySet()) {
      effective(r, effective, new HashSet<>());
    }
    Map<ResourceNode, Map<ResourceNode, Edge>> referrers = new HashMap<>();
    for (Map.Entry<ResourceNode, Map<ResourceNode, Edge>> e : effective.entrySet()) {
      for (Map.Entry<ResourceNode, Edge> t : e.getValue().entrySet()) {
        referrers.computeIfAbsent(t.getKey(), k -> new HashMap<>()).merge(e.getKey(), t.getValue(), Edge::strongest);
      }
    }

    Map<Mode, Map<ResourceNode, Set<ResourceNode>>> closures = new EnumMap<>(Mode.class);
    Map<String, FileResult> results = new HashMap<>();
    for (FileNode f : files) {
      Set<ResourceNode> own = new HashSet<>(f.resources);
      Mode validation = Mode.VALIDATION;
      for (ResourceNode r : f.resources) {
        if (readsTargets(r, closures)) {
          validation = Mode.ALL;
        }
      }
      Set<ResourceNode> vClosure = closure(f, validation, closures);
      Set<ResourceNode> rClosure = closure(f, Mode.RENDER, closures);
      Map<ResourceNode, Edge> users = new HashMap<>();
      for (ResourceNode r : f.resources) {
        Map<ResourceNode, Edge> rs = referrers.get(r);
        if (rs != null) {
          for (Map.Entry<ResourceNode, Edge> e : rs.entrySet()) {
            users.merge(e.getKey(), e.getValue(), Edge::strongest);
          }
        }
      }
      users.keySet().removeAll(own);
      boolean vInventory = usesInventory(f, vClosure);
      boolean rInventory = usesInventory(f, rClosure);

      Hasher oh = new Hasher();
      oh.add("own").add(f.key).add(f.sourceHash);
      addNodes(oh, f.resources);
      String ownHash = oh.finish();

      Hasher vh = new Hasher();
      vh.add("validation").add(ownHash);
      vh.add("depends-on");
      addNodes(vh, vClosure);
      vh.add("identity-of");
      addIdentities(vh, identityTargets(f, vClosure, validation));
      if (vInventory) {
        vh.add("inventory").add(inventory);
      }
      String validationHash = vh.finish();

      Hasher rh = new Hasher();
      rh.add("render").add(validationHash).add("depends-on");
      addNodes(rh, rClosure);
      rh.add("identity-of");
      addIdentities(rh, identityTargets(f, rClosure, Mode.RENDER));
      if (rInventory) {
        rh.add("inventory").add(inventory);
      }
      // a page lists what links to it by identity
      Set<ResourceNode> fullUsers = new HashSet<>();
      Set<ResourceNode> linkUsers = new HashSet<>();
      for (Map.Entry<ResourceNode, Edge> e : users.entrySet()) {
        (e.getValue() == Edge.LINK ? linkUsers : fullUsers).add(e.getKey());
      }
      rh.add("used-by");
      addNodes(rh, fullUsers);
      rh.add("linked-from");
      addIdentities(rh, linkUsers);
      String renderHash = rh.finish();

      Set<ResourceNode> dependsOn = new HashSet<>(vClosure);
      dependsOn.addAll(rClosure);
      results.put(f.key, new FileResult(ownHash, validationHash, renderHash, keys(dependsOn), keys(users.keySet()), vInventory || rInventory));
    }
    return results;
  }

  private boolean usesInventory(FileNode f, Set<ResourceNode> closure) {
    for (ResourceNode r : f.resources) {
      if (unresolved.contains(r)) {
        return true;
      }
    }
    for (ResourceNode r : closure) {
      if (unresolved.contains(r)) {
        return true;
      }
    }
    return false;
  }

  /** whether validating r reads the content of what it refers to: a resolve() or conformsTo( in r, or in anything r
   * depends on through full edges (a profile's snapshot, an extension, ...) */
  private boolean readsTargets(ResourceNode r, Map<Mode, Map<ResourceNode, Set<ResourceNode>>> closures) {
    if (readsTargets.contains(r)) {
      return true;
    }
    for (ResourceNode n : closure(r, Mode.VALIDATION, closures)) {
      if (readsTargets.contains(n)) {
        return true;
      }
    }
    return false;
  }

  private Set<ResourceNode> closure(FileNode f, Mode mode, Map<Mode, Map<ResourceNode, Set<ResourceNode>>> closures) {
    Set<ResourceNode> res = new HashSet<>();
    for (ResourceNode r : f.resources) {
      res.addAll(closure(r, mode, closures));
    }
    res.removeAll(f.resources);
    return res;
  }

  /**
   * the resources that the file, or what it depends on through the edges the mode follows, refers to through the
   * other edges, and whose identity the hash holds instead. A StructureDefinition's identity includes its base, so the
   * profiles it's derived from are added too (the validator looks for a target profile's base type)
   */
  private Set<ResourceNode> identityTargets(FileNode f, Set<ResourceNode> closure, Mode mode) {
    Set<ResourceNode> res = new HashSet<>();
    List<ResourceNode> from = new ArrayList<>(f.resources);
    from.addAll(closure);
    for (ResourceNode n : from) {
      for (Map.Entry<ResourceNode, Edge> e : direct.getOrDefault(n, Map.of()).entrySet()) {
        if (!mode.follows.contains(e.getValue())) {
          addWithBases(e.getKey(), res);
        }
      }
    }
    res.removeAll(closure);
    res.removeAll(f.resources);
    return res;
  }

  private void addWithBases(ResourceNode r, Set<ResourceNode> res) {
    if (res.add(r) && r.baseDefinition != null) {
      Map<ResourceNode, Edge> bases = new LinkedHashMap<>();
      resolveReference(r.baseDefinition, bases, Edge.FULL);
      for (ResourceNode b : bases.keySet()) {
        addWithBases(b, res);
      }
    }
  }

  private void index(FileNode f, ResourceNode r) {
    fileOf.put(r, f.key);
    index.add(r.getKey(), r);
    addTo(byId, r.id, r);
    if (r.url != null) {
      index.add(r.url, r);
      if (r.version != null) {
        index.add(r.url+"|"+r.version, r);
      }
      addTo(byLabel, r.url, r);
    }
    addTo(byLabel, r.getKey(), r);
    addTo(byLabel, r.id, r);
    if (r.name != null) {
      addTo(byLabel, r.name, r);
    }
    if (r.title != null) {
      addTo(byLabel, r.title, r);
    }
  }

  private static void addTo(Map<String, List<ResourceNode>> map, String key, ResourceNode r) {
    if (key != null) {
      map.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
    }
  }

  /**
   * @return false if the string contains something that looks like it is meant to refer to a resource in this IG,
   * but which can't be resolved
   */
  private boolean resolveString(String s, Map<ResourceNode, Edge> targets, Edge edge) {
    boolean ok = true;
    if (s.length() <= MAX_REF_LENGTH && !containsWhitespace(s)) {
      ok = resolveReference(s, targets, edge);
    }
    if (s.contains("://") || s.contains("urn:")) {
      Matcher m = URL_IN_TEXT.matcher(s);
      while (m.find()) {
        String u = trimPunctuation(m.group());
        if (!u.equals(s)) {
          ok = resolveReference(u, targets, edge) && ok;
        }
      }
    }
    if (s.contains("[[[")) {
      Matcher m = REF_TAG.matcher(s);
      while (m.find()) {
        String label = m.group(1).trim();
        if (label.endsWith("'")) {
          label = label.substring(0, label.length()-1);
        }
        List<ResourceNode> found = byLabel.get(label);
        if (found == null) {
          ok = resolveReference(label, targets, edge) && ok;
          if (!index.has(label)) {
            ok = false;
          }
        } else {
          addAll(targets, found, edge);
        }
      }
    }
    return ok;
  }

  private static boolean containsWhitespace(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (Character.isWhitespace(s.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  private static String trimPunctuation(String u) {
    while (!u.isEmpty() && ".:!?".indexOf(u.charAt(u.length()-1)) >= 0) {
      u = u.substring(0, u.length()-1);
    }
    return u;
  }

  private boolean resolveReference(String s, Map<ResourceNode, Edge> targets, Edge edge) {
    // what this string finds, apart from what the strings before it found (it can find the same resource again)
    Set<ResourceNode> found = new HashSet<>();
    resolveInto(s, found);
    addAll(targets, found, edge);
    if (!found.isEmpty()) {
      return true;
    }
    // nothing found. Is it meant to point into the IG?
    String path = refPath(s);
    boolean local = (canonical != null && path.startsWith(canonical+"/")) || isRelativeReference(path);
    if (!local) {
      return true;
    }
    String[] parts = path.split("/");
    String last = parts.length == 0 ? path : parts[parts.length-1];
    if (ID_LIKE.matcher(last).matches()) {
      // be generous: anything with the same id might be what's meant
      addAll(targets, byId.get(last), edge);
    }
    return false;
  }

  private boolean isRelativeReference(String path) {
    return RELATIVE_REF.matcher(path).matches() && (resourceTypes == null || resourceTypes.contains(path.substring(0, path.indexOf('/'))));
  }

  /** the reference without its #fragment, |version and /_history/ */
  private static String refPath(String s) {
    String path = s;
    if (path.contains("#")) {
      path = path.substring(0, path.indexOf("#"));
    }
    if (path.contains("|")) {
      path = path.substring(0, path.indexOf("|"));
    }
    if (path.contains("/_history/")) {
      path = path.substring(0, path.indexOf("/_history/"));
    }
    return path;
  }

  private void resolveInto(String s, Set<ResourceNode> targets) {
    String base = s;
    if (base.contains("#")) {
      base = base.substring(0, base.indexOf("#"));
    }
    targets.addAll(index.find(s));
    targets.addAll(index.find(base));
    if (base.contains("|")) {
      base = base.substring(0, base.indexOf("|"));
      targets.addAll(index.find(base));
    }
    String path = base;
    if (path.contains("/_history/")) {
      path = path.substring(0, path.indexOf("/_history/"));
    }
    targets.addAll(index.find(path));
    boolean absolute = path.startsWith("http://") || path.startsWith("https://");
    String[] parts = path.split("/");
    if (absolute && parts.length >= 2) {
      targets.addAll(index.find(parts[parts.length-2]+"/"+parts[parts.length-1]));
    }
  }

  private static void addAll(Map<ResourceNode, Edge> targets, Collection<ResourceNode> list, Edge edge) {
    if (list != null) {
      for (ResourceNode r : list) {
        targets.merge(r, edge, Edge::strongest);
      }
    }
  }

  private Map<ResourceNode, Edge> effective(ResourceNode r, Map<ResourceNode, Map<ResourceNode, Edge>> memo, Set<ResourceNode> trail) {
    Map<ResourceNode, Edge> res = memo.get(r);
    if (res != null) {
      return res;
    }
    res = new HashMap<>(direct.getOrDefault(r, Map.of()));
    if (r.baseDefinition != null && trail.add(r)) {
      Map<ResourceNode, Edge> bases = new LinkedHashMap<>();
      resolveReference(r.baseDefinition, bases, Edge.FULL);
      for (ResourceNode b : bases.keySet()) {
        if (b != r) {
          for (Map.Entry<ResourceNode, Edge> e : effective(b, memo, trail).entrySet()) {
            res.merge(e.getKey(), e.getValue(), Edge::strongest);
          }
        }
      }
      trail.remove(r);
    }
    res.remove(r);
    if (trail.isEmpty()) {
      memo.put(r, res);
    }
    return res;
  }

  /** what r depends on through the edges the mode follows, transitively */
  private Set<ResourceNode> closure(ResourceNode r, Mode mode, Map<Mode, Map<ResourceNode, Set<ResourceNode>>> closures) {
    Map<ResourceNode, Set<ResourceNode>> memo = closures.computeIfAbsent(mode, m -> new HashMap<>());
    Set<ResourceNode> res = memo.get(r);
    if (res != null) {
      return res;
    }
    // iterative walk, so that cycles are harmless. A node whose closure is already known isn't walked again
    res = new HashSet<>();
    Deque<ResourceNode> todo = new ArrayDeque<>();
    todo.push(r);
    while (!todo.isEmpty()) {
      ResourceNode n = todo.pop();
      for (Map.Entry<ResourceNode, Edge> e : direct.getOrDefault(n, Map.of()).entrySet()) {
        if (!mode.follows.contains(e.getValue())) {
          continue;
        }
        ResourceNode t = e.getKey();
        if (res.add(t)) {
          Set<ResourceNode> known = memo.get(t);
          if (known != null) {
            res.addAll(known);
          } else {
            todo.push(t);
          }
        }
      }
    }
    res.remove(r);
    memo.put(r, res);
    return res;
  }

  private void addNodes(Hasher h, Collection<ResourceNode> nodes) {
    // sorted, so that the hash doesn't depend on the order the nodes were found in. Two files (or one) can hold the
    // same Type/id, and each of them counts
    List<String[]> sorted = new ArrayList<>();
    for (ResourceNode n : nodes) {
      sorted.add(new String[] {n.getKey(), String.valueOf(fileOf.get(n)), n.contentHash});
    }
    sorted.sort(Comparator.comparing((String[] e) -> e[0]).thenComparing(e -> e[1]).thenComparing(e -> e[2]));
    for (String[] e : sorted) {
      h.add(e[0]).add(e[1]).add(e[2]);
    }
  }

  /** what a weak edge to each of the nodes depends on: not their content, but what they are, and where */
  private void addIdentities(Hasher h, Collection<ResourceNode> nodes) {
    List<List<String>> sorted = new ArrayList<>();
    for (ResourceNode n : nodes) {
      List<String> id = new ArrayList<>(List.of(n.getKey(), String.valueOf(fileOf.get(n)), String.valueOf(n.url),
          String.valueOf(n.version), String.valueOf(n.name), String.valueOf(n.title), String.valueOf(n.baseDefinition)));
      id.addAll(n.identity);
      sorted.add(id);
    }
    sorted.sort(Comparator.comparing((List<String> e) -> e.get(0)).thenComparing(e -> e.get(1)).thenComparing(e -> String.join("\u0001", e)));
    for (List<String> id : sorted) {
      h.add(Integer.toString(id.size()));
      for (String s : id) {
        h.add(s);
      }
    }
  }

  private static Set<String> keys(Collection<ResourceNode> nodes) {
    Set<String> res = new TreeSet<>();
    for (ResourceNode n : nodes) {
      res.add(n.getKey());
    }
    return res;
  }

  private String inventoryHash(Collection<FileNode> files) {
    TreeSet<String> all = new TreeSet<>();
    for (FileNode f : files) {
      for (ResourceNode r : f.resources) {
        all.add(f.key+"\u0001"+r.getKey()+"\u0001"+r.url+"\u0001"+r.version+"\u0001"+r.name+"\u0001"+r.title);
      }
    }
    Hasher h = new Hasher();
    for (String s : all) {
      h.add(s);
    }
    return h.finish();
  }

  /** SHA-256 over a sequence of strings, each one length-prefixed so that the sequence is unambiguous */
  public static class Hasher {
    private final MessageDigest md;

    public Hasher() {
      try {
        md = MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException e) {
        throw new Error(e);
      }
    }

    public Hasher add(String s) {
      if (s == null) {
        md.update((byte) 0);
      } else {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        md.update((byte) 1);
        md.update(Integer.toString(b.length).getBytes(StandardCharsets.US_ASCII));
        md.update((byte) ':');
        md.update(b);
      }
      return this;
    }

    public Hasher add(byte[] b) {
      if (b == null) {
        md.update((byte) 0);
      } else {
        md.update((byte) 2);
        md.update(Integer.toString(b.length).getBytes(StandardCharsets.US_ASCII));
        md.update((byte) ':');
        md.update(b);
      }
      return this;
    }

    public String finish() {
      byte[] d = md.digest();
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < 16; i++) {
        b.append(String.format("%02x", d[i]));
      }
      return b.toString();
    }
  }
}
