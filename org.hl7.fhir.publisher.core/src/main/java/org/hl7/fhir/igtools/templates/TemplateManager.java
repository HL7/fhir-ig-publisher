package org.hl7.fhir.igtools.templates;

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


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.hl7.fhir.igtools.publisher.PublisherUtils;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageGenerator.PackageType;
import org.hl7.fhir.utilities.settings.FhirSettings;
import org.hl7.fhir.utilities.filesystem.ManagedFileAccess;

public class TemplateManager {

  private FilesystemPackageCacheManager pcm;
  private ILoggingService logger;
  private List<JsonObject> configs = new ArrayList<JsonObject>();
  boolean canExecute;
  String templateThatCantExecute;
  String templateReason;
  List<String> templateList = new ArrayList<>();
  Set<String> antScripts = new HashSet<>();
  private boolean autoMode;
  private Map<String, String> templateOverrides = new LinkedHashMap<>(); // template id (name#version, or just name) -> local folder
  private boolean usedOverride;
  private final List<String> localFolders = new ArrayList<>(); // the local folders the last template was loaded from
  private String trustTemplate; // -trust-template: a template id, or "all"
  private String settingsPath; // fhir-settings.json: null = FhirSettings.getFilePath()

  private static final String ACCESSIBLE_MARKER = "[%accessibility-routines%]";
  private static final String JS_ROUTINE_NAME_REGEX = "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*";

  public TemplateManager(FilesystemPackageCacheManager pcm, ILoggingService logger) {
    this.pcm = pcm;
    this.logger = logger;
  }

  /**
   * For debugging templates: in the [IG] section of ig.ini, any property whose name is a package id,
   * with or without a version, nominates a local folder to load that template from instead of the
   * package cache. This applies to the template itself and to any of its base templates, e.g.
   *
   *   template = hl7.fhir.template#current
   *   hl7.fhir.template#current = /Users/me/work/templates/hl7.fhir.template
   *   hl7.base.template = ../templates/hl7.base.template
   *
   * Relative folders are relative to the IG root folder. Ignored in auto-build mode
   */
  public void loadTemplateOverrides(IniFile ini, String rootFolder) throws IOException {
    templateOverrides.clear();
    if (ini == null || !ini.hasSection("IG")) {
      return;
    }
    for (String name : ini.getPropertyNames("IG")) {
      if (name.matches(FilesystemPackageCacheManager.PACKAGE_VERSION_REGEX) || name.matches(FilesystemPackageCacheManager.PACKAGE_REGEX)) {
        String folder = ini.getStringProperty("IG", name);
        if (!Utilities.noString(folder)) {
          File f = new File(folder);
          if (!f.isAbsolute()) {
            f = new File(Utilities.path(rootFolder, folder));
          }
          templateOverrides.put(name, f.getAbsolutePath());
        }
      }
    }
  }

  private String findTemplateOverride(String template) {
    if (templateOverrides.isEmpty()) {
      return null;
    }
    String res = templateOverrides.get(template);
    if (res == null && template.contains("#")) {
      res = templateOverrides.get(template.substring(0, template.indexOf("#")));
    }
    return res;
  }

  public Template loadTemplate(String template, String rootFolder, String packageId, boolean autoMode, boolean wantLog, boolean rapidoMode) throws FHIRException, IOException {
    this.autoMode = autoMode;
    this.usedOverride = false;
    localFolders.clear();
    if (autoMode && !templateOverrides.isEmpty()) {
      // overrides would let an IG substitute arbitrary content for a trusted template id
      logger.logMessage("Template overrides in ig.ini are ignored in auto-build mode: "+String.join(", ", templateOverrides.keySet()));
      templateOverrides.clear();
    }
    String templateDir = Utilities.path(rootFolder, "template");
    boolean inPlace = template.equals("#template");
    if (!inPlace) {
      FileUtilities.createDirectory(templateDir);
      PublisherUtils.clearFolder(templateDir);
    };
    List<String> scriptTemplates = new ArrayList<String>();
    
    canExecute = true;
    NpmPackage npm;
    if (!inPlace) {
      installTemplate(template, rootFolder, templateDir, scriptTemplates, new ArrayList<String>(), 0);
    } else {
      // the IG's own template folder is the template, as it is: a change to it needs a rebuild
      localFolders.add(new File(templateDir).getAbsolutePath());
      checkInPlaceTemplate(template, templateDir);
    }
    
    if (!autoMode && !canExecute) {
      // locally, we'll build whatever people give us - once they've said they trust it
      checkLocalTrust();
      canExecute = true;
    }
    if (!canExecute) {
      logger.logMessage("IG template '"+templateThatCantExecute+"' is not trusted.  No scripts will be executed");
    }
    return new Template(rootFolder, canExecute, templateThatCantExecute, templateReason, wantLog, rapidoMode, usedOverride || isDevMode(template));
  }

  /**
   * The -trust-template parameter: the id of a template that isn't trusted (see trustId()), or "all", that the user
   * says can run locally. It's recorded in fhir-settings.json, so it only needs to be said once
   */
  public void setTrustTemplate(String trustTemplate) {
    this.trustTemplate = Utilities.noString(trustTemplate) ? null : trustTemplate.trim();
  }

  /**
   * The fhir-settings.json file that records which templates the user trusts. By default, the one FhirSettings uses
   */
  public void setSettingsPath(String settingsPath) {
    this.settingsPath = settingsPath;
  }

  private String getSettingsPath() {
    return settingsPath != null ? settingsPath : FhirSettings.getFilePath();
  }

  /**
   * The id that a template is trusted by locally: what the IG (or the template that uses it as its base) names it by,
   * without a version - a package id, a github URL, a folder, or #folder for a template in the IG's own folders
   */
  static String trustId(String template) {
    if (template.startsWith("#") || !template.contains("#")) {
      return template;
    }
    return template.substring(0, template.indexOf("#"));
  }

  /**
   * Run locally, a template that has active content and isn't trusted can run, but only once the user has said they
   * trust it: by listing its id (or "all") in trustedTemplates in fhir-settings.json, or by running with
   * -trust-template, which adds it there. Otherwise the run stops, saying why and what to do
   */
  private void checkLocalTrust() throws IOException {
    String id = trustId(templateThatCantExecute);
    String settings = getSettingsPath();
    if (settings != null && LocalTemplateTrust.isTrusted(settings, id)) {
      return;
    }
    if (trustTemplate != null && (LocalTemplateTrust.ALL.equals(trustTemplate) || trustTemplate.equals(id))) {
      if (settings != null) {
        LocalTemplateTrust.addTrust(settings, trustTemplate);
        logger.logMessage("Template '"+id+"' is trusted, as you said ("+(LocalTemplateTrust.ALL.equals(trustTemplate) ? "all templates" : "this template")+"): recorded in "+settings+", so you won't be asked again");
      } else {
        logger.logMessage("Template '"+id+"' is trusted for this run, as you said. It couldn't be recorded, because the FHIR settings file isn't known");
      }
      return;
    }
    throw new FHIRException("The template '"+templateThatCantExecute+"' runs scripts, and it is not a trusted template ("+templateReason+"). "
        + "A template's scripts can do anything you can do on this computer, so be sure that this template is trustworthy before you run it. "
        + "If you are sure, run the IG Publisher again with '-trust-template "+id+"' to trust this template, or '-trust-template all' to trust all templates. "
        + (settings != null ? "That will be recorded in "+settings+", and you won't be asked again." : "")
        + (trustTemplate != null ? " (-trust-template '"+trustTemplate+"' doesn't name this template.)" : ""));
  }

  /**
   * The local folders that the template loaded last, and its base templates, came from (not those from the package
   * cache): a template that is being worked on. Watch mode watches them
   */
  public List<String> getLocalFolders() {
    return Collections.unmodifiableList(localFolders);
  }

  private boolean isDevMode(String template) {
    return !NpmPackage.isValidNameWithVersion(template, true);
  }

  private void installTemplate(String template, String rootFolder, String templateDir, List<String> scriptIds, ArrayList<String> loadedIds, int level) throws FHIRException, IOException {
    String override = findTemplateOverride(template);
    NpmPackage npm;
    if (override != null) {
      logger.logMessage(Utilities.padLeft("", ' ', level) + "Load Template "+template+" from local folder "+override+" (override in ig.ini)");
      File f = new File(override);
      if (!f.exists() || !f.isDirectory()) {
        throw new FHIRException("The ig.ini override for template "+template+" names the folder "+override+" which does not exist");
      }
      // recorded first, so that a template that can't be read now is still watched: fixing it needs a rebuild
      localFolders.add(f.getAbsolutePath());
      npm = NpmPackage.fromFolder(f.getAbsolutePath(), PackageType.IG_TEMPLATE, "output", ".git");
      usedOverride = true;
      String id = template.contains("#") ? template.substring(0, template.indexOf("#")) : template;
      if (!id.equals(npm.name())) {
        logger.logMessage(Utilities.padLeft("", ' ', level) + "  Warning: the template in "+override+" is "+npm.name()+", not "+id);
      }
    } else {
      logger.logMessage(Utilities.padLeft("", ' ', level) + "Load Template from "+template);
      npm = loadPackage(template, rootFolder);
    }
    if (!npm.isType(PackageType.IG_TEMPLATE))
      throw new FHIRException("The referenced package '"+template+"' does not have the correct type - is "+npm.type()+" but should be a template");
    templateList.add(npm.name()+"#"+npm.version());
    loadedIds.add(npm.name());
    if (npm.getNpm().has("base")) {
      String baseTemplate = npm.getNpm().asString("base");
      if (loadedIds.contains(baseTemplate)) {
        loadedIds.add(baseTemplate);
        throw new FHIRException("Template parents recurse: " + String.join("->", loadedIds));
      }
      if (!npm.getNpm().has("dependencies") || !npm.getNpm().getJsonObject("dependencies").has(baseTemplate)) {
        throw new FHIRException("Unable to resolve "+baseTemplate+" because it is not listed in the dependencies");
      }
      String ver = npm.getNpm().getJsonObject("dependencies").asString(baseTemplate);
      installTemplate(baseTemplate+"#"+ver, rootFolder, templateDir, scriptIds, loadedIds, level + 1);
    }
    // npm.debugDump("template");
    
    List<String> files = new ArrayList<>();
    npm.unPackWithAppend(templateDir, files);
    JsonObject config = null;
    if (npm.hasFile(Utilities.path("package", "$root"), "config.json")) {
      // we found a config file
      try {
        config = JsonParser.parseObject(npm.load(Utilities.path("package", "$root"), "config.json"));
      } catch (Exception e) {
        FileUtilities.streamToFile(npm.load(Utilities.path("package", "$root"), "config.json"), Utilities.path("[tmp]", npm.name()+"#"+npm.version()+"$config.json"));
        throw new FHIRException("Error parsing "+npm.name()+"#"+npm.version()+"#"+Utilities.path("package", "$root", "config.json")+": "+e.getMessage(), e);
      }
      configs.add(config);
    }
    // if scriptReason != null, then the template has active content, and scriptReason contains the reason why we decided that there's active content
    String scriptReason = activeContentReason(templateDir, files, config, npm.name()+"#"+npm.version());

    // if it has active content, then we check to see if it's trusted. Locally: always trusted. auto-build: only some trusted
    if (scriptReason != null) {
      checkTemplateId(template, npm.name(), scriptReason);
    }
    if (level==0 && configs.size() > 1) {
      config = configs.get(0);
      for (int i=1;i<configs.size(); i++) {
        applyConfigChanges(config, configs.get(i));
      }
      String configString = JsonParser.compose(config, true);
      String configPath = Utilities.path(templateDir, "config.json");
      FileUtilities.stringToFile(configString, configPath);
    }
    if (level==0 && !configs.isEmpty()) {
      generateMakeAccessible(templateDir, configs.get(0));
    }
  }

  /**
   * Whether a template has active content, and if it does, why: it nominates an ant script or targets, it overwrites a
   * registered ant script, or it has a file that isn't on the list of harmless ones. These rules apply to every template,
   * however it was loaded (see installTemplate() and checkInPlaceTemplate())
   *
   * @param templateDir the folder the template content is in
   * @param files the files of this template (absolute, or with the same prefix as templateDir)
   * @param config the template's config.json, if it has one
   * @param id the template, for messages
   * @return null if the template has no active content
   */
  String activeContentReason(String templateDir, List<String> files, JsonObject config, String id) {
    Set<String> ext = new HashSet<>();
    String scriptReason = null;
    if (config != null) {
      // check to see if it defines an active script
      if (config.has("script") || config.has("targets")) {
        scriptReason = "Template nominates an ant script or targets";
      }
      // a pre-process transform is an xslt that the publisher runs over the IG's pages: that's code too
      if (scriptReason == null && config.has("pre-process") && config.get("pre-process").isJsonArray()) {
        for (JsonElement e : config.getJsonArray("pre-process")) {
          if (e.isJsonObject() && ((JsonObject) e).has("transform")) {
            scriptReason = "Template nominates a pre-process transform";
          }
        }
      }
      if (config.has("script")) {
        antScripts.add(scriptPath(config.asString("script")));
        
        // the ant script might call other ant scripts. No way we can introspect the ant script to see what it calls, 
        // so the template has to declare what other any scripts it can call out to, so we can track to see if any template
        // later tries to overwrite them
        // if it doesn't declare the list - even if it's empty - we refuse to run at all- templates have to do this
        if (!config.has("otherScripts")) {
          if (autoMode) {
            throw new Error("Template "+id+" names a script, but is not explicit about all ant scripts - this is no longer allowed");  
          } else {
            System.out.println("Template "+id+" names a script, but is not explicit about all ant scripts. Note that this means that this template no longer works with the ci-build infrastructure");
          }
        } else {
          for (String s : config.getStrings("otherScripts")) {
            antScripts.add(scriptPath(s));
          }
        }
      }
    }

    // if we have't already found that it's considered a script, we'll look through the content
    if (scriptReason == null) {
      for (String fn : files) {
        // where the file actually is: the name it was unpacked with could have . or .. in it
        String n = templatePath(templateDir, fn);
        if (n == null) {
          scriptReason = "Template has a file outside the template folder ("+fn+")";
          break;
        }
        String lower = n.toLowerCase();

        if (!Utilities.existsInList(lower, "license", "readme")) {
          // Normalize path separators and get basename
          String unix = n.replace('\\', '/');
          String base = unix.contains("/") ? unix.substring(unix.lastIndexOf('/') + 1) : unix;

          // Allow a few harmless dotfiles anywhere
          if (Utilities.existsInList(base, ".gitignore", ".gitattributes", ".gitkeep")) {
            continue;
          }

          // Explicit, narrow allowlist for .github/ housekeeping files
          if (unix.startsWith(".github/")) {
            boolean safeGithub =
                unix.equals(".github/CODEOWNERS") ||
                unix.equals(".github/FUNDING.yml") ||
                unix.equals(".github/SECURITY.md") ||
                unix.equals(".github/dependabot.yml") ||
                unix.equalsIgnoreCase(".github/pull_request_template.md") ||
                (unix.startsWith(".github/workflows/") && (unix.endsWith(".yml") || unix.endsWith(".yaml"))) ||
                (unix.startsWith(".github/ISSUE_TEMPLATE/") &&
                    (unix.endsWith(".md") || unix.endsWith(".yml") || unix.endsWith(".yaml"))) ||
                // Optionally allow docs in .github/ (Markdown only)
                unix.endsWith(".md");

            if (safeGithub) {
              continue;
            }
          }

          // scripts/ (ig.scripts) is where the scripts keep what they run and read: stylesheets, jars, and data files
          // with harmless extensions (e.g. groupings.txt). Anything there is active content, whatever its extension
          if (unix.toLowerCase().startsWith("scripts/")) {
            scriptReason = "Template has content in scripts/ ("+unix+")";
            break;
          }
          String s = extension(n);
          if (antScripts.contains(lower)) {
            // nah, if you overwrite an xml ant script, you're doing active content
            scriptReason = "Template contains a registered ant script";
          } else if (!Utilities.existsInList(s,
              ".html", ".css", ".png", ".gif", ".oet", ".json", ".xml", ".ico", ".jpg", ".md", ".ini", ".po",
              ".eot", ".otf", ".svg", ".ttf", ".woff", ".woff2", ".txt", ".yml", ".yaml", ".liquid", ".gitignore")) {
            // we don't track what other scripts are potentially active, so we only allow a whitelist of files.
            // this is safe if the base templates don't call out to scripts with weird file extensions (the template authors know about this rule)
            ext.add(s);
            break;
          }
        }
      }
      if (!ext.isEmpty()) {
        scriptReason = "Template has file extensions: " + ext;
      }
    }

    return scriptReason;
  }

  /**
   * A template in the IG's own folder (template = #template) is used as it is: it isn't installed, so it doesn't go
   * through installTemplate(). It's local to the repository, so it can't be a trusted template (only templates loaded
   * by package id can be): like any other template that isn't trusted, it can be used on the ci-build only if it has no
   * active content, as decided by the same rules
   */
  private void checkInPlaceTemplate(String template, String templateDir) throws IOException {
    JsonObject config = null;
    File cfg = ManagedFileAccess.file(Utilities.path(templateDir, "config.json"));
    if (cfg.exists()) {
      try {
        config = JsonParser.parseObject(cfg);
      } catch (Exception e) {
        throw new FHIRException("Error parsing "+cfg.getAbsolutePath()+": "+e.getMessage(), e);
      }
    }
    List<String> files = new ArrayList<>();
    if (ManagedFileAccess.file(templateDir).isDirectory()) {
      try (Stream<Path> s = Files.walk(ManagedFileAccess.file(templateDir).toPath())) {
        s.filter(Files::isRegularFile).forEach(f -> files.add(f.toString()));
      }
    }
    String reason = activeContentReason(Paths.get(templateDir).toString(), files, config, template);
    if (reason != null) {
      canExecute = false;
      templateThatCantExecute = template;
      templateReason = reason;
    }
  }

  /**
   * Each template in the hierarchy can register javascript routines in its config.json:
   *
   *   "accessibility-routines": ["myTemplateMakeAccessible"]
   *
   * Arrays are concatenated when the configs are merged (base template first), so the
   * merged config lists the routines in template order. They're written into the body of
   * makeAccessible(accessible) in content/assets/js/accessible.js, replacing the marker line,
   * and each is called as routine(accessible). Each template is responsible for making sure
   * that any routine it registers is loaded in the page before accessible.js runs
   */
  private void generateMakeAccessible(String templateDir, JsonObject config) throws IOException {
    String path = Utilities.path(templateDir, "content", "assets", "js", "accessible.js");
    File f = ManagedFileAccess.file(path);
    if (!f.exists()) {
      return;
    }
    List<String> routines = config.has("accessibility-routines") ? config.getStrings("accessibility-routines") : new ArrayList<>();
    StringBuilder b = new StringBuilder();
    for (String r : routines) {
      if (!r.matches(JS_ROUTINE_NAME_REGEX)) {
        throw new FHIRException("The template accessibility-routine '"+r+"' is not a valid javascript function name");
      }
      b.append("  "+r+"(accessible);\n");
    }
    String src = FileUtilities.fileToString(f);
    String nl = src.contains("\r\n") ? "\r\n" : "\n";
    int i = src.indexOf(ACCESSIBLE_MARKER);
    if (i == -1) {
      logger.logMessage("Template accessible.js has no '"+ACCESSIBLE_MARKER+"' marker, so the accessibility-routines were not added to makeAccessible()");
      return;
    }
    int start = src.lastIndexOf('\n', i) + 1;
    int end = src.indexOf('\n', i);
    end = end == -1 ? src.length() : end + 1;
    src = src.substring(0, start) + b.toString().replace("\n", nl) + src.substring(end);
    FileUtilities.stringToFile(src, path);
  }
  
  private void applyConfigChanges(JsonObject baseConfig, JsonObject deltaConfig) throws FHIRException {
    for (JsonProperty p : deltaConfig.getProperties()) {
      if (baseConfig.has(p.getName())) {
        JsonElement baseElement = baseConfig.get(p.getName());
        JsonElement newElement = deltaConfig.get(p.getName());
        if (baseElement.isJsonArray()!=newElement.isJsonArray() || baseElement.isJsonObject()!=newElement.isJsonObject() || baseElement.isJsonPrimitive()!=newElement.isJsonPrimitive())
          throw new FHIRException("When overriding template config file, element " + p.getName() + " has a different JSON type in the base config file (" + baseElement + ") than it does in the overriding config file (" + newElement + ").");
        if (newElement.isJsonObject()) {
          applyConfigChanges((JsonObject) baseElement, (JsonObject) p.getValue());
        } else if (newElement.isJsonArray()) {
          ((JsonArray)baseElement).getItems().addAll(((JsonArray)newElement).getItems());
        } else {
          baseConfig.remove(p.getName());
          baseConfig.add(p.getName(), deltaConfig.get(p.getName()));
        }
      } else {
        baseConfig.add(p.getName(), deltaConfig.get(p.getName()));
      }
    }
  }

  /**
   * A file's path relative to the template folder, normalised (no . or .. segments, / as the separator), or null if
   * it isn't inside the template folder. The active content checks are made on this, since it's where the file is
   */
  static String templatePath(String templateDir, String file) {
    Path root = Paths.get(templateDir).toAbsolutePath().normalize();
    Path f = Paths.get(file).toAbsolutePath().normalize();
    if (!f.startsWith(root) || f.equals(root)) {
      return null;
    }
    return root.relativize(f).toString().replace('\\', '/');
  }

  /**
   * An ant script named in config.json (script, otherScripts), in the form templatePath() gives, in lower case: what
   * matters is which file it is, and some file systems ignore case
   */
  static String scriptPath(String script) {
    return Paths.get(script.replace('\\', '/')).normalize().toString().replace('\\', '/').toLowerCase();
  }

  /**
   * A template can be loaded by URL only from a repository on github.com (not any host whose name starts with github.)
   */
  static boolean isGitHubUrl(String template) {
    return template.startsWith("https://github.com/") || template.startsWith("http://github.com/");
  }

  private String extension(String n) {
    if (!n.contains(".")) {
      return "";
    }
    return n.substring(n.lastIndexOf("."));
  }

  /**
   * If we get to here, we've found a template with active content. This code checks that it's authorised.
   * 
   * If the IG is being run locally, we don't care - we'll always run it (see code in loadTemplate)
   * But on the ci-build, we only allow scripts that are approved by the FHIR product director
   * (as enforced by this code here).
   * 
   * We only allow scripts from packages loaded by package id, for known packages 
   * (the only way to fiddle with packages loaded by id is through the code)
   * 
   * The trusted templates are listed in TrustedTemplates, with the repository each is built from: the ci-build only
   * builds a template claiming a trusted package id from that repository (PublisherIGLoader.checkTrustedTemplateSource)
   *
   * @param template
   * @param packageId
   * @return
   */
  private void checkTemplateId(String template, String packageId, String reason) {
    String t = template.contains("#") ? template.substring(0, template.indexOf("#")) : template;
    if (!t.equals(packageId)) {
      canExecute = false;
      templateThatCantExecute = template;
      templateReason = reason;
    } else if (!TrustedTemplates.isTrusted(packageId)) {
      canExecute = false;
      templateThatCantExecute = template;
      templateReason = reason;
    } else if (!isTrustableVersion(template)) {
      canExecute = false;
      templateThatCantExecute = template;
      templateReason = reason+", and only a released version or #current of a trusted template is trusted";
    }
  }

  /**
   * A trusted template is trusted only as published: a released version (n.n.n), #current, or no version (which
   * loads the latest release). Not a branch build (current$branch), a dev build, or a version with a label (e.g.
   * -cibuild), none of which have been released
   */
  static boolean isTrustableVersion(String template) {
    if (!template.contains("#")) {
      return true;
    }
    String version = template.substring(template.indexOf("#") + 1);
    return "current".equals(version) || version.matches("[0-9]+\\.[0-9]+\\.[0-9]+");
  }

  private boolean isTemplate(String url, String id, String template) {
    if (url.equals(template))
      return true;
    if (template.equals(id))
      return true;
    if (template.matches(FilesystemPackageCacheManager.PACKAGE_VERSION_REGEX) && template.startsWith(id+"#"))
      return true;
    return false;
  }

  private NpmPackage loadPackage(String template, String rootFolder) throws FHIRException, IOException {
    try {
      if (template.startsWith("#")) {
        File f = ManagedFileAccess.file(Utilities.path(rootFolder, template.substring(1)));
        if (f.exists() && f.isDirectory()) {
          localFolders.add(f.getAbsolutePath()); // first, as for an override
          return NpmPackage.fromFolder(f.getAbsolutePath(), PackageType.IG_TEMPLATE, "output", ".git");
        }
      }

      if (template.matches(FilesystemPackageCacheManager.PACKAGE_REGEX)) {
        return pcm.loadPackage(template, null);
      }
      if (template.matches(FilesystemPackageCacheManager.PACKAGE_VERSION_REGEX)) {
        String[] p = template.split("\\#");
        return pcm.loadPackage(p[0], p[1]);
      }
      // a URL isn't a file: asking file access about it can fail (it isn't in a folder the build may use, and on Windows
      // it isn't a path at all)
      File f = Utilities.isURL(template) ? null : ManagedFileAccess.file(template);
      if (f != null && !f.exists()) {
        f = ManagedFileAccess.file(Utilities.path(rootFolder, template));
      }
      if (f != null && f.exists()) {
        if (f.isDirectory()) {
          localFolders.add(f.getAbsolutePath());
          return NpmPackage.fromFolder(f.getAbsolutePath());
        } else {
          try (FileInputStream in = ManagedFileAccess.inStream(template)) {
            return NpmPackage.fromPackage(in);
          }
        }
      }
      if (isGitHubUrl(template)) {
        if (template.startsWith("http://")) {
          template = template.replace("http://", "https://");
        }

        URL url = URI.create(zipUrl(template)).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(PublisherUtils.DOWNLOAD_CONNECT_TIMEOUT);
        connection.setReadTimeout(PublisherUtils.DOWNLOAD_READ_TIMEOUT);
        byte[] zip;
        try (InputStream in = connection.getInputStream()) {
          zip = PublisherUtils.readLimited(in, MAX_TEMPLATE_DOWNLOAD, "The template "+template);
        }
        checkZipEntryNames(zip, url.toString());
        return NpmPackage.fromZip(new ByteArrayInputStream(zip), true, url.toString()); 
      }
      throw new FHIRException("Unable to load template source from "+template+". Use a github URL, a local directory, or #[folder] for a contained template");
    } catch (Exception e) {
      e.printStackTrace();
      throw new FHIRException("Error loading template "+template+": "+e.getMessage(), e);
    }
  }

  /**
   * No entry in a template zip can have ".." in its name. NpmPackage.fromZip (core 7.0.6) doesn't check: unpacked,
   * content/../scripts/ant.xml is written over scripts/ant.xml (a trusted base template's script), but the list of the
   * files unpacked - which is what the active content check looks at - still says content/../scripts/ant.xml
   */
  /** the largest template zip that will be downloaded from github (bytes) */
  public static final long MAX_TEMPLATE_DOWNLOAD = 200L * 1024 * 1024;

  static void checkZipEntryNames(byte[] zip, String source) throws IOException {
    try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry ze;
      while ((ze = zin.getNextEntry()) != null) {
        if (ze.getName().contains("..")) {
          throw new FHIRException("The template "+source+" has an entry with an illegal name: "+ze.getName());
        }
      }
    }
  }

  private String zipUrl(String template) {
    if (!template.startsWith("https://github.com/")) {
      throw new FHIRException("Cannot refer to a template by URL unless referring to a github repository: "+template);
    } else if (Utilities.charCount(template, '/') == 4) {
      return Utilities.pathURL(template, "archive", "master.zip");      
    } else if (Utilities.charCount(template, '/') == 6) {
      String[] p = template.split("\\/");
      return Utilities.pathURL("https://"+p[2], p[3], p[4], "archive", p[6]+".zip");      
    } else {
      throw new FHIRException("Template syntax in URL referring to a github repository was not understood: "+template);
    }
  }

  public List<String> listTemplates() {
    return templateList;
  }
  
}
