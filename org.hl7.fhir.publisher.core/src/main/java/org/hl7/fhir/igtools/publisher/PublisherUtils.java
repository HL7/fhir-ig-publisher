package org.hl7.fhir.igtools.publisher;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.convertors.factory.*;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.model.utilities.formats.IParser;
import org.hl7.fhir.model.Base;
import org.hl7.fhir.model.core.CanonicalResource;
import org.hl7.fhir.services.renderers.utils.RenderingContext;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.NpmPackage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.utilities.filesystem.ManagedFileAccess;

public class PublisherUtils {

  /**
   * On Windows, cmd looks for a command (jekyll, sushi, npx) in the current folder before the PATH, so a jekyll.bat in
   * the folder a command runs in would be run instead. With NoDefaultCurrentDirectoryInExePath set (to anything), cmd
   * doesn't look in the current folder. Returns the environment to run cmd with
   */
  public static Map<String, String> noCurrentFolderLookup(Map<String, String> env) {
    Map<String, String> res = new HashMap<>(env);
    res.put("NoDefaultCurrentDirectoryInExePath", "1");
    return res;
  }

  /**
   * Whether path is inside root, where they really are: symbolic links are followed (as far as the path exists - the
   * rest of it is created later, as folders), since a repository can contain a link to anywhere. A .. after a link
   * goes up from where the link leads, as it does in the file system (see ManagedFileAccess.realPath)
   */
  public static boolean isInside(String root, String path) {
    return ManagedFileAccess.isInside(root, path);
  }

  /**
   * The symbolic links in the IG's folder that lead out of it (or nowhere), relative to it. .git is not looked at.
   * Links aren't followed: the walk only looks at what is really in the folder
   */
  public static List<String> linksOut(String root) throws IOException {
    List<String> res = new ArrayList<>();
    java.nio.file.Path r = ManagedFileAccess.file(root).toPath().toAbsolutePath().normalize();
    if (!java.nio.file.Files.isDirectory(r)) {
      return res;
    }
    java.nio.file.Files.walkFileTree(r, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
      @Override
      public java.nio.file.FileVisitResult preVisitDirectory(java.nio.file.Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
        return dir.getFileName() != null && ".git".equals(dir.getFileName().toString()) && dir.getParent() != null && dir.getParent().equals(r)
            ? java.nio.file.FileVisitResult.SKIP_SUBTREE : java.nio.file.FileVisitResult.CONTINUE;
      }

      @Override
      public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
        if (attrs.isSymbolicLink() && !isInside(r.toString(), file.toString())) {
          res.add(r.relativize(file).toString().replace('\\', '/'));
        }
        return java.nio.file.FileVisitResult.CONTINUE;
      }

      @Override
      public java.nio.file.FileVisitResult visitFileFailed(java.nio.file.Path file, IOException exc) {
        return java.nio.file.FileVisitResult.CONTINUE;
      }
    });
    Collections.sort(res);
    return res;
  }

  /**
   * Empties a folder, without following symbolic links: a link in it is deleted (not what it links to), and a folder
   * that is itself a link isn't cleared at all. Core's FileUtilities.clearDirectory does this now (it followed links up
   * to core 7.0.6), and also refuses folders that are never scratch folders (home, system folders, a git repository...)
   *
   * @param exemptions names of files directly in the folder to keep
   */
  public static void clearFolder(String folder, String... exemptions) throws IOException {
    org.hl7.fhir.utilities.FileUtilities.clearDirectory(folder, exemptions);
  }

  /**
   * Reads a download, but no more than max bytes: a server (named by the IG) could otherwise send without end
   */
  public static byte[] readLimited(java.io.InputStream in, long max, String what) throws IOException {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    byte[] buf = new byte[65536];
    long total = 0;
    int n;
    while ((n = in.read(buf)) != -1) {
      total = total + n;
      if (total > max) {
        throw new IOException(what+" is too big (more than "+(max / 1024 / 1024)+"MB)");
      }
      b.write(buf, 0, n);
    }
    return b.toByteArray();
  }

  /** how long to wait to connect to, and then for data from, a server the IG names (ms) */
  public static final int DOWNLOAD_CONNECT_TIMEOUT = 30000;
  public static final int DOWNLOAD_READ_TIMEOUT = 5 * 60000;
  public enum UMLGenerationMode {
    NONE, SOURCED, ALL;

    public static UMLGenerationMode fromCode(String value) {
      if (Utilities.noString(value)) {
        return NONE;
      } else switch (value.toLowerCase()) {
      case "none" :
        return NONE;
      case "source":
      case "sourced":
        return SOURCED;
      case "all" :
      case "always" :
        return ALL;
      default:
        throw new FHIRException("Unknown UML generation mode `"+value+"`");
      }
    }
  }

  /**
   * IG parameter 'instance-hyperlinks': whether the HTML renderings of instances in XML, JSON and Turtle
   * link element names, types and references to their definitions. The links make the pages much
   * bigger (#1382).
   */
  public enum InstanceHyperlinksMode {
    YES, NO, EXAMPLES_ONLY;

    public static InstanceHyperlinksMode fromCode(String value) {
      if (Utilities.noString(value)) {
        return YES;
      } else switch (value.toLowerCase()) {
      case "yes" :
      case "true" :
        return YES;
      case "no" :
      case "false" :
        return NO;
      case "examples-only" :
        return EXAMPLES_ONLY;
      default:
        throw new FHIRException("Unknown value for 'instance-hyperlinks' of '"+value+"': it must be yes, no or examples-only");
      }
    }
  }

  public enum PinningPolicy {NO_ACTION, FIX, WHEN_MULTIPLE_CHOICES}

  public enum IGBuildMode { MANUAL, AUTOBUILD, WEBSERVER, PUBLICATION }

  public enum LinkTargetType {

  }

  public enum CacheOption {
    LEAVE, CLEAR_ERRORS, CLEAR_ALL;
  }

  public static class LinkedSpecification {
    private SpecMapManager spm;
    private NpmPackage npm;
    @Getter @Setter private JsonObject index;

    public LinkedSpecification(SpecMapManager spm, NpmPackage npm) {
      super();
      this.spm = spm;
      this.npm = npm;
    }
    public SpecMapManager getSpm() {
      return spm;
    }
    public NpmPackage getNpm() {
      return npm;
    }
  }


  public static class ContainedResourceDetails {

    private String type;
    private String id;
    private String title;
    private String description;
    private CanonicalResource canonical;

    public ContainedResourceDetails(String type, String id, String title, String description, CanonicalResource canonical) {
      this.type = type;
      this.id = id;
      this.title = title;
      this.description = description;
      this.canonical = canonical;
    }

    public String getType() {
      return type;
    }

    public String getId() {
      return id;
    }

    public String getTitle() {
      return title;
    }

    public String getDescription() {
      return description;
    }

    public CanonicalResource getCanonical() {
      return canonical;
    }

  }

  public static class JsonDependency {
    private String name;
    private String canonical;
    private String npmId;
    private String version;
    public JsonDependency(String name, String canonical, String npmId, String version) {
      super();
      this.name = name;
      this.canonical = canonical;
      this.npmId = npmId;
      this.version = version;
    }
    public String getName() {
      return name;
    }
    public String getCanonical() {
      return canonical;
    }
    public String getNpmId() {
      return npmId;
    }
    public String getVersion() {
      return version;
    }

  }

  public static class ElideExceptDetails {
    private String base = null;
    private String except = null;

    public ElideExceptDetails(String except) {
      this.except = except;
    }

    public String getBase() {
      return base;
    }

    public boolean hasBase() { return base != null; }

    public void setBase(String base) { this.base = base; }

    public String getExcept() { return except; }
  }



}
