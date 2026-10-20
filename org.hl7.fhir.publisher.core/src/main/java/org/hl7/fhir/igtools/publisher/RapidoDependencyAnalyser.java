package org.hl7.fhir.igtools.publisher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
  private final Map<String, ResourceNode> byKey = new HashMap<>();
  private final Map<String, List<ResourceNode>> byUrl = new HashMap<>();
  private final Map<String, List<ResourceNode>> byId = new HashMap<>();
  private final Map<String, List<ResourceNode>> byLabel = new HashMap<>();
  private final Map<ResourceNode, String> fileOf = new HashMap<>();
  private final Map<ResourceNode, Set<ResourceNode>> direct = new HashMap<>();
  private final Set<ResourceNode> unresolved = new HashSet<>();

  public RapidoDependencyAnalyser(String canonical) {
    this.canonical = canonical;
  }

  public Map<String, FileResult> analyse(Collection<FileNode> files) {
    for (FileNode f : files) {
      for (ResourceNode r : f.resources) {
        index(f, r);
      }
    }
    for (FileNode f : files) {
      for (ResourceNode r : f.resources) {
        Set<ResourceNode> targets = new LinkedHashSet<>();
        boolean ok = true;
        for (String s : r.strings) {
          ok = resolveString(s, targets) && ok;
        }
        for (String s : r.references) {
          ok = resolveString(s, targets) && ok;
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
    Map<ResourceNode, Set<ResourceNode>> effective = new HashMap<>();
    for (ResourceNode r : direct.keySet()) {
      effective(r, effective, new HashSet<>());
    }
    Map<ResourceNode, Set<ResourceNode>> referrers = new HashMap<>();
    for (Map.Entry<ResourceNode, Set<ResourceNode>> e : effective.entrySet()) {
      for (ResourceNode t : e.getValue()) {
        referrers.computeIfAbsent(t, k -> new HashSet<>()).add(e.getKey());
      }
    }

    Map<ResourceNode, Set<ResourceNode>> closures = new HashMap<>();
    Map<String, FileResult> results = new HashMap<>();
    for (FileNode f : files) {
      Set<ResourceNode> own = new HashSet<>(f.resources);
      Set<ResourceNode> closure = new HashSet<>();
      Set<ResourceNode> users = new HashSet<>();
      for (ResourceNode r : f.resources) {
        closure.addAll(closure(r, closures));
        Set<ResourceNode> rs = referrers.get(r);
        if (rs != null) {
          users.addAll(rs);
        }
      }
      closure.removeAll(own);
      users.removeAll(own);
      boolean usesInventory = false;
      for (ResourceNode r : f.resources) {
        usesInventory = usesInventory || unresolved.contains(r);
      }
      for (ResourceNode r : closure) {
        usesInventory = usesInventory || unresolved.contains(r);
      }

      Hasher oh = new Hasher();
      oh.add("own").add(f.key).add(f.sourceHash);
      addNodes(oh, f.resources);
      String ownHash = oh.finish();

      Hasher vh = new Hasher();
      vh.add("validation").add(ownHash);
      vh.add("depends-on");
      addNodes(vh, closure);
      if (usesInventory) {
        vh.add("inventory").add(inventory);
      }
      String validationHash = vh.finish();

      Hasher rh = new Hasher();
      rh.add("render").add(validationHash).add("used-by");
      addNodes(rh, users);
      String renderHash = rh.finish();

      results.put(f.key, new FileResult(ownHash, validationHash, renderHash, keys(closure), keys(users), usesInventory));
    }
    return results;
  }

  private void index(FileNode f, ResourceNode r) {
    fileOf.put(r, f.key);
    byKey.put(r.getKey(), r);
    addTo(byId, r.id, r);
    if (r.url != null) {
      addTo(byUrl, r.url, r);
      if (r.version != null) {
        addTo(byUrl, r.url+"|"+r.version, r);
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
  private boolean resolveString(String s, Set<ResourceNode> targets) {
    boolean ok = true;
    if (s.length() <= MAX_REF_LENGTH && !containsWhitespace(s)) {
      ok = resolveReference(s, targets);
    }
    if (s.contains("://") || s.contains("urn:")) {
      Matcher m = URL_IN_TEXT.matcher(s);
      while (m.find()) {
        String u = trimPunctuation(m.group());
        if (!u.equals(s)) {
          ok = resolveReference(u, targets) && ok;
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
          ok = resolveReference(label, targets) && ok;
          if (!byUrl.containsKey(label) && !byKey.containsKey(label)) {
            ok = false;
          }
        } else {
          targets.addAll(found);
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

  private boolean resolveReference(String s, Set<ResourceNode> targets) {
    // what this string finds, apart from what the strings before it found (it can find the same resource again)
    Set<ResourceNode> found = new HashSet<>();
    resolveInto(s, found);
    targets.addAll(found);
    if (!found.isEmpty()) {
      return true;
    }
    // nothing found. Is it meant to point into the IG?
    String path = refPath(s);
    boolean local = (canonical != null && path.startsWith(canonical+"/")) || RELATIVE_REF.matcher(path).matches();
    if (!local) {
      return true;
    }
    String[] parts = path.split("/");
    String last = parts.length == 0 ? path : parts[parts.length-1];
    if (ID_LIKE.matcher(last).matches()) {
      // be generous: anything with the same id might be what's meant
      addAll(targets, byId.get(last));
    }
    return false;
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
    addAll(targets, byUrl.get(s));
    addAll(targets, byUrl.get(base));
    if (base.contains("|")) {
      base = base.substring(0, base.indexOf("|"));
      addAll(targets, byUrl.get(base));
    }
    String path = base;
    if (path.contains("/_history/")) {
      path = path.substring(0, path.indexOf("/_history/"));
    }
    ResourceNode r = byKey.get(path);
    if (r != null) {
      targets.add(r);
    }
    boolean absolute = path.startsWith("http://") || path.startsWith("https://");
    String[] parts = path.split("/");
    if (absolute && parts.length >= 2) {
      r = byKey.get(parts[parts.length-2]+"/"+parts[parts.length-1]);
      if (r != null) {
        targets.add(r);
      }
    }
  }

  private static void addAll(Set<ResourceNode> targets, List<ResourceNode> list) {
    if (list != null) {
      targets.addAll(list);
    }
  }

  private Set<ResourceNode> effective(ResourceNode r, Map<ResourceNode, Set<ResourceNode>> memo, Set<ResourceNode> trail) {
    Set<ResourceNode> res = memo.get(r);
    if (res != null) {
      return res;
    }
    res = new HashSet<>(direct.getOrDefault(r, new HashSet<>()));
    if (r.baseDefinition != null && trail.add(r)) {
      Set<ResourceNode> bases = new LinkedHashSet<>();
      resolveReference(r.baseDefinition, bases);
      for (ResourceNode b : bases) {
        if (b != r) {
          res.addAll(effective(b, memo, trail));
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

  private Set<ResourceNode> closure(ResourceNode r, Map<ResourceNode, Set<ResourceNode>> memo) {
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
      for (ResourceNode t : direct.getOrDefault(n, new HashSet<>())) {
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

  private static void addNodes(Hasher h, Collection<ResourceNode> nodes) {
    TreeMap<String, String> sorted = new TreeMap<>();
    for (ResourceNode n : nodes) {
      sorted.put(n.getKey(), n.contentHash);
    }
    for (Map.Entry<String, String> e : sorted.entrySet()) {
      h.add(e.getKey()).add(e.getValue());
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
