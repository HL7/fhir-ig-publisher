package org.hl7.fhir.igtools.publisher;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.hl7.fhir.igtools.publisher.HTMLInspector.ExternalReferenceType;
import org.hl7.fhir.igtools.publisher.HTMLInspector.LoadedFile;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

/**
 * Watch mode: what the html checks (HTMLInspector.check()) found in each output file, and what they looked at to find
 * it, so that the check after a page rebuild only needs to check the files that are new, and the files whose checks
 * looked at something that has changed since. For the others it puts back what the last check found, in the order a
 * check of everything would find it, so the result is the same as checking everything again.
 *
 * <p>What a file's check looks at, apart from the file itself and the set up of the check (see
 * HTMLInspector.recordSignature()): whether files exist, and what loading other files found (their anchors). What it
 * adds to: the messages, the counts and lists the inspector keeps, and the file itself (the publish box, the anchors the
 * link check adds). A file whose check changed it is written again as Jekyll wrote it before it's loaded again.
 */
class HtmlCheckRecords {

  /** A script the check found in a file: the message it makes the first time it's found, if any */
  static final class ScriptUse {
    final String js;
    final ValidationMessage message;

    ScriptUse(String js, ValidationMessage message) {
      this.js = js;
      this.message = message;
    }
  }

  /** A call to HTMLInspector.addExternalReference() that got past the filter */
  static final class ExternalRef {
    final ExternalReferenceType type;
    final String ref;
    final XhtmlNode node;
    final String source;
    final String text;

    ExternalRef(ExternalReferenceType type, String ref, XhtmlNode node, String source, String text) {
      this.type = type;
      this.ref = ref;
      this.node = node;
      this.source = source;
      this.text = text;
    }
  }

  /** What checking one file found */
  static final class FileRecord {
    final LoadedFile loaded;
    final List<ValidationMessage> loadMessages;
    final List<String> parseProblems;
    // whether the check changed the file (so that it's no longer what Jekyll wrote)
    boolean modified;
    // why what was found can't be put back without checking the file again, if it can't
    String notReplayable;
    // messages (copies, as they were added) and ScriptUses, in order
    final List<Object> events = new ArrayList<>();
    int links;
    List<String> foundFragments;
    List<String> fragmentsUsed;
    List<String[]> visibleFragments;
    List<String[]> copyrights;
    List<Object[]> imageRefs;
    List<ExternalRef> externalRefs;
    // the files whose existence the check asked about, and the answer
    Map<String, Boolean> exists;
    // the files the check looked up in what was loaded, and what it used of what was found
    Map<String, LookupUse> lookups;
    // the file's size and time when the check had finished
    long size = -1;
    long modifiedTime;

    FileRecord(LoadedFile loaded, List<ValidationMessage> loadMessages, List<String> parseProblems) {
      this.loaded = loaded;
      this.loadMessages = loadMessages;
      this.parseProblems = parseProblems;
    }

    void addFoundFragment(String s) {
      if (foundFragments == null) {
        foundFragments = new ArrayList<>();
      }
      foundFragments.add(s);
    }

    void addFragmentUsed(String s) {
      if (fragmentsUsed == null) {
        fragmentsUsed = new ArrayList<>();
      }
      fragmentsUsed.add(s);
    }

    void addVisibleFragment(String key, String value) {
      if (visibleFragments == null) {
        visibleFragments = new ArrayList<>();
      }
      visibleFragments.add(new String[] {key, value});
    }

    void addCopyright(String key, String value) {
      if (copyrights == null) {
        copyrights = new ArrayList<>();
      }
      copyrights.add(new String[] {key, value});
    }

    void addImageRef(String ref, XhtmlNode node) {
      if (imageRefs == null) {
        imageRefs = new ArrayList<>();
      }
      imageRefs.add(new Object[] {ref, node});
    }

    void addExternalRef(ExternalRef r) {
      if (externalRefs == null) {
        externalRefs = new ArrayList<>();
      }
      externalRefs.add(r);
    }

    void addExists(String path, boolean result) {
      if (exists == null) {
        exists = new HashMap<>();
      }
      exists.put(path, result);
    }

    /** Records that the check looked up path and found result (null: nothing); what it then used of it goes in the result */
    LookupUse addLookup(String path, LoadedFile result) {
      if (lookups == null) {
        lookups = new HashMap<>();
      }
      LookupUse res = lookups.get(path);
      if (res == null) {
        res = new LookupUse(result);
        lookups.put(path, res);
      }
      return res;
    }

    void stamp() {
      File f = new File(loaded.getFilename());
      size = f.isFile() ? f.length() : -1;
      modifiedTime = f.lastModified();
    }

    boolean sameStamp() {
      File f = new File(loaded.getFilename());
      return size >= 0 && f.isFile() && f.length() == size && f.lastModified() == modifiedTime;
    }
  }

  /**
   * What the link checks of a file used of a file they looked up: whether it's there, and, for a link to an anchor in it,
   * whether it's a language redirect, and whether it has the anchor; if it doesn't, the message lists its anchors, so all
   * of them. The rest (e.g. the anchors that a page that links to the file without an anchor doesn't use) can change
   * without changing what the check finds
   */
  static final class LookupUse {
    final LoadedFile found;
    // whether the check asked if it's a language redirect, and the answer (null: it didn't)
    Boolean langRedirect;
    // the anchors the check found in it (null: none)
    Set<String> anchorsFound;
    // whether the check used all its anchors, in order (it looked for one that isn't there)
    boolean allAnchors;

    LookupUse(LoadedFile found) {
      this.found = found;
    }

    /** Records that the check asked whether the file found is a language redirect */
    void usedLangRedirect() {
      if (langRedirect == null) {
        langRedirect = found.isLangRedirect();
      }
    }

    /** Records that the check looked for the anchor in the file found, and whether it's there */
    void usedAnchor(String name, boolean there) {
      if (!there) {
        allAnchors = true;
      } else {
        if (anchorsFound == null) {
          anchorsFound = new HashSet<>();
        }
        anchorsFound.add(name);
      }
    }

    /**
     * How what a lookup finds now (now) is different from what the check used, for the log; null if it would find the
     * same
     */
    String difference(LoadedFile now) {
      if (found == null || now == null) {
        return found == now ? null : lookupDifference(found, now);
      }
      if (langRedirect != null && langRedirect != now.isLangRedirect()) {
        return lookupDifference(found, now);
      }
      if (allAnchors && !sameLookup(found, now)) {
        return lookupDifference(found, now);
      }
      if (anchorsFound != null) {
        for (String a : new TreeSet<>(anchorsFound)) {
          if (!now.getTargets().contains(a)) {
            return "no longer has the anchor " + a;
          }
        }
      }
      return null;
    }
  }

  /**
   * The messages list that the check of a file adds to, while it's recorded: records a copy of each message as it's
   * added. The check only adds to the end; anything else means the records can't be used
   */
  static final class RecordingList extends AbstractList<ValidationMessage> {
    private final List<ValidationMessage> list;
    private final FileRecord record;
    private final HtmlCheckRecords records;

    RecordingList(List<ValidationMessage> list, FileRecord record, HtmlCheckRecords records) {
      this.list = list;
      this.record = record;
      this.records = records;
    }

    @Override
    public ValidationMessage get(int index) {
      return list.get(index);
    }

    @Override
    public int size() {
      return list.size();
    }

    @Override
    public void add(int index, ValidationMessage element) {
      if (index != list.size()) {
        records.notUsable("a check inserted a message");
      }
      list.add(index, element);
      record.events.add(copy(element));
    }

    @Override
    public ValidationMessage set(int index, ValidationMessage element) {
      records.notUsable("a check replaced a message");
      return list.set(index, element);
    }

    @Override
    public ValidationMessage remove(int index) {
      records.notUsable("a check removed a message");
      return list.remove(index);
    }

    /** Adds a message without recording it (the caller records it some other way) */
    void addUnrecorded(ValidationMessage element) {
      list.add(element);
    }
  }

  private final String signature;
  // what the signature is made of, by name (for the log)
  private final Map<String, String> signatureParts;
  private String notUsable;
  final Map<String, FileRecord> files = new HashMap<>();
  // the files and folders in the output when the files were checked, and whether there were links among them
  private Set<String> tree;
  private boolean treeHasLinks;

  HtmlCheckRecords(String signature) {
    this.signature = signature;
    this.signatureParts = null;
  }

  HtmlCheckRecords(Map<String, String> signatureParts) {
    this.signature = signatureParts.toString();
    this.signatureParts = signatureParts;
  }

  String getSignature() {
    return signature;
  }

  Map<String, String> getSignatureParts() {
    return signatureParts;
  }

  /** The names of the parts of two signatures that are different (for the log) */
  static String differences(Map<String, String> a, Map<String, String> b) {
    if (a == null || b == null) {
      return "not recorded";
    }
    List<String> res = new ArrayList<>();
    Set<String> names = new LinkedHashSet<>(a.keySet());
    names.addAll(b.keySet());
    for (String n : names) {
      if (!Objects.equals(a.get(n), b.get(n))) {
        res.add(n);
      }
    }
    return res.isEmpty() ? "nothing differs" : String.join(", ", res);
  }

  void notUsable(String reason) {
    if (notUsable == null) {
      notUsable = reason;
    }
  }

  String getNotUsable() {
    return notUsable;
  }

  void setTree(Set<String> tree, boolean hasLinks) {
    this.tree = tree;
    this.treeHasLinks = hasLinks;
  }

  /**
   * Whether the files and folders in the output are the same as when these records were made (so that whether a file
   * in it exists hasn't changed)
   */
  boolean sameTree(Set<String> now, boolean nowHasLinks) {
    return tree != null && !treeHasLinks && !nowHasLinks && tree.equals(now);
  }

  /** The paths (relative to root) of the files and folders under root; links[0] is set if there are symbolic links */
  static Set<String> listTree(String root, boolean[] links) throws IOException {
    Set<String> res = new HashSet<>();
    Path r = Paths.get(root);
    if (!Files.isDirectory(r)) {
      return res;
    }
    Files.walkFileTree(r, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        res.add(r.relativize(dir).toString());
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (attrs.isSymbolicLink()) {
          links[0] = true;
        }
        res.add(r.relativize(file).toString());
        return FileVisitResult.CONTINUE;
      }
    });
    return res;
  }

  /** How what the lookup finds is different from what the check found (see sameLookup()), for the log */
  static String lookupDifference(LoadedFile a, LoadedFile b) {
    if (a == null) {
      return "is there now";
    }
    if (b == null) {
      return "is no longer there";
    }
    if (a.isLangRedirect() != b.isLangRedirect()) {
      return b.isLangRedirect() ? "is a language redirect now" : "is no longer a language redirect";
    }
    Set<String> added = new TreeSet<>(b.getTargets());
    added.removeAll(a.getTargets());
    Set<String> gone = new TreeSet<>(a.getTargets());
    gone.removeAll(b.getTargets());
    if (added.isEmpty() && gone.isEmpty()) {
      return "has the same " + a.getTargets().size() + " anchors in a different order";
    }
    return "has " + (added.isEmpty() ? "" : anchors(added.size(), "more") + " (e.g. " + added.iterator().next() + ")")
        + (added.isEmpty() || gone.isEmpty() ? "" : " and ")
        + (gone.isEmpty() ? "" : anchors(gone.size(), "fewer") + " (e.g. " + gone.iterator().next() + ")");
  }

  private static String anchors(int n, String how) {
    return n + " " + how + (n == 1 ? " anchor" : " anchors");
  }

  /**
   * Whether a lookup that found a would find the same as one that finds b, as far as the link checks can tell: whether
   * there's a file, and its anchors in the same order
   */
  static boolean sameLookup(LoadedFile a, LoadedFile b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    return a.isLangRedirect() == b.isLangRedirect() && a.getTargets().size() == b.getTargets().size()
        && new ArrayList<>(a.getTargets()).equals(new ArrayList<>(b.getTargets()));
  }

  // --- copying messages ----------------------------------------------------------------------------------------------

  private static List<Field> messageFields;

  /**
   * A copy of the message: the check's messages are changed after the check (e.g. when suppressed messages are
   * matched), so the records keep a copy of each as it was made, and the messages put back are copies of those
   */
  static ValidationMessage copy(ValidationMessage m) {
    if (m == null) {
      return null;
    }
    try {
      ValidationMessage res = new ValidationMessage();
      for (Field f : fields()) {
        f.set(res, f.get(m));
      }
      return res;
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Unable to copy a message", e);
    }
  }

  static List<ValidationMessage> copies(List<ValidationMessage> list) {
    List<ValidationMessage> res = new ArrayList<>(list.size());
    for (ValidationMessage m : list) {
      res.add(copy(m));
    }
    return res;
  }

  private static synchronized List<Field> fields() {
    if (messageFields == null) {
      List<Field> res = new ArrayList<>();
      for (Class<?> c = ValidationMessage.class; c != null && c != Object.class; c = c.getSuperclass()) {
        for (Field f : c.getDeclaredFields()) {
          if (!Modifier.isStatic(f.getModifiers())) {
            f.setAccessible(true);
            res.add(f);
          }
        }
      }
      messageFields = res;
    }
    return messageFields;
  }

  /** for tests: whether two messages have the same content */
  static boolean sameContent(ValidationMessage a, ValidationMessage b) {
    try {
      for (Field f : fields()) {
        if (!Objects.deepEquals(f.get(a), f.get(b))) {
          return false;
        }
      }
      return true;
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }
}
