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
import java.util.HashMap;
import java.util.Map;

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
