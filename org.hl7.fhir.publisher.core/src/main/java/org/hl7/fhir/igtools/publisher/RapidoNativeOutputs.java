package org.hl7.fhir.igtools.publisher;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.hl7.fhir.igtools.publisher.RapidoDependencyAnalyser.Hasher;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.services.utilities.NPMPackageGenerator;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

/**
 * -rapido: what the native outputs step did for each source file when it last made them, kept in the temp folder
 * (_rapido/native) so that a later build can do the same for a file that hasn't changed without composing its
 * resources again.
 *
 * <p>The step writes each resource in the formats wanted (json, xml, turtle) in the temp folder and for each language
 * (in the language's folder, in the new template format), with the json for the pages in _includes, and adds it to the
 * packages. A {@link FileRecord} holds, for a file, what the next build needs to leave the files where they
 * are: a key for what each resource was made from (see {@link #contentKey}), the entries that went in the packages, the
 * json for the database row, the messages the step added to the resource, the outputs it claimed for the file, and
 * the size and time of every file it wrote. The record is used only when the keys are the same and every file is still
 * as it was written; otherwise the outputs are made again.
 */
public class RapidoNativeOutputs {

  private static final int VERSION = 1;

  /** a file in one of the packages: the main one, or a language's */
  public static class Entry {
    /** null for the main package */
    final String lang;
    final NPMPackageGenerator.Category category;
    final String name;
    final byte[] content;

    Entry(String lang, NPMPackageGenerator.Category category, String name, byte[] content) {
      this.lang = lang;
      this.category = category;
      this.name = name;
      this.content = content;
    }
  }

  /** what the step did for one resource */
  public static class ResourceRecord {
    private final FileRecord file;
    final String key;
    final List<Entry> entries = new ArrayList<>();
    /** what the step returns, for the database row */
    byte[] json;
    /** the messages the step added to the resource */
    final List<ValidationMessage> messages = new ArrayList<>();

    private ResourceRecord(FileRecord file, String key) {
      this.file = file;
      this.key = key;
    }

    FileRecord file() {
      return file;
    }
  }

  /** what the step did for the resources of one source file */
  public static class FileRecord {
    final List<ResourceRecord> resources = new ArrayList<>();
    /** the paths (in the temp folder) added to the file's output names */
    final List<String> outputNames = new ArrayList<>();
    /** every file written, as a path in the temp folder, with its size and time once written */
    final Map<String, long[]> written = new TreeMap<>();
    /** set when the step did something for the file that a record can't hold: then no record is kept */
    private boolean unsupported;

    ResourceRecord add(String key) {
      ResourceRecord r = new ResourceRecord(this, key);
      resources.add(r);
      return r;
    }

    void unsupported() {
      unsupported = true;
    }

    boolean isUnsupported() {
      return unsupported;
    }
  }

  private final String tempDir;
  private final File folder;

  public RapidoNativeOutputs(String tempDir) throws IOException {
    this.tempDir = tempDir;
    this.folder = new File(Utilities.path(tempDir, "_rapido", "native"));
  }

  /**
   * What the file's record goes by: where it was loaded from. Files loaded from the same place share a record, which
   * is then of the one written last, and the keys tell whose it is.
   */
  private static String name(FetchedFile f) {
    return f.getLoadPath() != null ? f.getLoadPath() : f.getPath();
  }

  private File recordFile(FetchedFile f) {
    return new File(folder, new Hasher().add(name(f)).finish() + ".bin");
  }

  /** before a file's outputs are made again: what is recorded for it no longer describes them */
  public void forget(FetchedFile f) throws IOException {
    Files.deleteIfExists(recordFile(f).toPath());
  }

  /** notes a file the step wrote; claimed if it was added to the file's output names */
  public void wrote(FileRecord rec, String path, boolean claimed) {
    String rel = relative(path);
    if (rel == null) {
      rec.unsupported();
      return;
    }
    rec.written.put(rel, null);
    if (claimed) {
      rec.outputNames.add(rel);
    }
  }

  private String relative(String path) {
    String prefix = tempDir + File.separator;
    return path.startsWith(prefix) && path.length() > prefix.length() ? path.substring(prefix.length()) : null;
  }

  public String absolute(String relativePath) {
    return tempDir + File.separator + relativePath;
  }

  /** size and time of the file, or null if it isn't a file that can be read */
  private static long[] stat(File f) {
    try {
      BasicFileAttributes a = Files.readAttributes(f.toPath(), BasicFileAttributes.class);
      return a.isRegularFile() ? new long[] {a.size(), a.lastModifiedTime().to(TimeUnit.NANOSECONDS)} : null;
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  /** whether every file the record says the step wrote is still there, with the size and time it had then */
  public boolean filesUnchanged(FileRecord rec) {
    for (Map.Entry<String, long[]> e : rec.written.entrySet()) {
      long[] now = stat(new File(absolute(e.getKey())));
      if (now == null || !Arrays.equals(now, e.getValue())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Keeps the record for the next build, once the step has written the file's outputs. Nothing is kept if the step did
   * something a record can't hold, or one of its files can't be read back: then the next build makes the outputs again
   */
  public void save(FetchedFile f, FileRecord rec) throws IOException {
    if (rec.unsupported) {
      return;
    }
    for (ResourceRecord r : rec.resources) {
      for (ValidationMessage vm : r.messages) {
        if (RapidoTracker.writeMessage(vm) == null) {
          return;
        }
      }
    }
    for (Map.Entry<String, long[]> e : rec.written.entrySet()) {
      long[] s = stat(new File(absolute(e.getKey())));
      if (s == null) {
        return;
      }
      e.setValue(s);
    }
    Files.createDirectories(folder.toPath());
    File dst = recordFile(f);
    Path tmp = Files.createTempFile(folder.toPath(), dst.getName(), ".tmp");
    try {
      try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 16))) {
        write(out, f, rec);
      }
      try {
        // in one step, so that two files loaded from the same place, saved at once, leave one whole record
        Files.move(tmp, dst.toPath(), StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      try {
        Files.deleteIfExists(tmp);
      } catch (IOException e) {
        // left behind: nothing reads it
      }
    }
  }

  private static void write(DataOutputStream out, FetchedFile f, FileRecord rec) throws IOException {
    out.writeInt(VERSION);
    string(out, name(f));
    out.writeInt(rec.resources.size());
    for (ResourceRecord r : rec.resources) {
      string(out, r.key);
      out.writeInt(r.entries.size());
      int same = -1;
      for (int i = 0; i < r.entries.size(); i++) {
        Entry e = r.entries.get(i);
        string(out, e.lang);
        string(out, e.category.name());
        string(out, e.name);
        bytes(out, e.content);
        if (same < 0 && Arrays.equals(e.content, r.json)) {
          same = i;
        }
      }
      // the json for the database is usually what went in the main package: then it is kept once
      out.writeInt(same);
      if (same < 0) {
        bytes(out, r.json);
      }
      out.writeInt(r.messages.size());
      for (ValidationMessage vm : r.messages) {
        string(out, org.hl7.fhir.utilities.json.parser.JsonParser.compose(RapidoTracker.writeMessage(vm), false));
      }
    }
    out.writeInt(rec.outputNames.size());
    for (String s : rec.outputNames) {
      string(out, s);
    }
    out.writeInt(rec.written.size());
    for (Map.Entry<String, long[]> e : rec.written.entrySet()) {
      string(out, e.getKey());
      out.writeLong(e.getValue()[0]);
      out.writeLong(e.getValue()[1]);
    }
    out.writeInt(VERSION); // the end: a record cut short doesn't have it
  }

  /** what was recorded for the file, or null if there is no record that can be read */
  public FileRecord read(FetchedFile f) {
    File src = recordFile(f);
    if (!src.isFile()) {
      return null;
    }
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(src.toPath()), 1 << 16))) {
      if (in.readInt() != VERSION || !name(f).equals(string(in))) {
        return null;
      }
      FileRecord rec = new FileRecord();
      int resources = count(in);
      for (int i = 0; i < resources; i++) {
        ResourceRecord r = rec.add(string(in));
        int entries = count(in);
        for (int j = 0; j < entries; j++) {
          String lang = string(in);
          NPMPackageGenerator.Category category = NPMPackageGenerator.Category.valueOf(string(in));
          r.entries.add(new Entry(lang, category, string(in), bytes(in)));
        }
        int same = in.readInt();
        r.json = same < 0 ? bytes(in) : r.entries.get(same).content;
        if (r.json == null) {
          return null;
        }
        int messages = count(in);
        for (int j = 0; j < messages; j++) {
          ValidationMessage vm = RapidoTracker.readMessage(org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(string(in)));
          if (vm == null) {
            return null;
          }
          r.messages.add(vm);
        }
      }
      int names = count(in);
      for (int i = 0; i < names; i++) {
        rec.outputNames.add(string(in));
      }
      int written = count(in);
      for (int i = 0; i < written; i++) {
        String path = string(in);
        rec.written.put(path, new long[] {in.readLong(), in.readLong()});
      }
      return in.readInt() == VERSION && in.read() == -1 ? rec : null;
    } catch (Exception e) {
      // cut short, or not a record: the outputs are made again
      return null;
    }
  }

  private static int count(DataInputStream in) throws IOException {
    int n = in.readInt();
    if (n < 0) {
      throw new IOException("not a count: " + n);
    }
    return n;
  }

  private static void string(DataOutputStream out, String s) throws IOException {
    bytes(out, s == null ? null : s.getBytes(StandardCharsets.UTF_8));
  }

  private static String string(DataInputStream in) throws IOException {
    byte[] b = bytes(in);
    return b == null ? null : new String(b, StandardCharsets.UTF_8);
  }

  private static void bytes(DataOutputStream out, byte[] b) throws IOException {
    if (b == null) {
      out.writeInt(-1);
    } else {
      out.writeInt(b.length);
      out.write(b);
    }
  }

  private static byte[] bytes(DataInputStream in) throws IOException {
    int n = in.readInt();
    if (n < 0) {
      return null;
    }
    if (n > in.available()) {
      throw new IOException("cut short");
    }
    byte[] b = new byte[n];
    in.readFully(b);
    return b;
  }

  /**
   * Everything of the element that the json, xml and turtle composers and the language copies write from: names,
   * types, values, narrative (the tree, and the source text the json composer writes when the tree is as parsed),
   * comments, and the order of it all. Two elements with the same key are written the same by the same publisher as
   * long as the definitions they were parsed against are the same: those are not in here, they are in Rapido's hash
   * of what the file depends on.
   */
  public static void contentKey(Hasher h, Element e) {
    h.add("(").add(e.getName()).add(e.fhirType()).add(e.getType()).add(e.getExplicitType());
    h.add(e.getSpecial() == null ? null : e.getSpecial().name());
    h.add(e.isNull() ? "null" : "").add(e.isElided() ? "elided" : "");
    if (e.isXhtml()) {
      h.add(e.getXhtmlSource());
      xhtmlKey(h, e.getXhtml());
    } else {
      h.add(e.getValue());
    }
    if (e.hasComments()) {
      for (String c : e.getComments()) {
        h.add("comment").add(c);
      }
    }
    if (e.hasChildren()) {
      for (Element c : e.getChildList()) {
        contentKey(h, c);
      }
    }
    h.add(")");
  }

  private static void xhtmlKey(Hasher h, XhtmlNode x) {
    if (x == null) {
      h.add((String) null);
      return;
    }
    h.add("<").add(x.getNodeType() == null ? null : x.getNodeType().name()).add(x.getName()).add(x.getContent());
    if (x.hasAttributes()) {
      for (Map.Entry<String, String> a : x.getAttributes().entrySet()) { // in the order they are written
        h.add("@").add(a.getKey()).add(a.getValue());
      }
    }
    if (x.hasChildren()) {
      for (XhtmlNode c : x.getChildNodes()) {
        xhtmlKey(h, c);
      }
    }
    h.add(">");
  }
}
