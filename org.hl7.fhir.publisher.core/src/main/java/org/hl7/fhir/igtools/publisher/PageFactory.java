package org.hl7.fhir.igtools.publisher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.ContextUtilities;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.GuidePageGeneration;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionPageComponent;
import org.hl7.fhir.r5.model.UrlType;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.JsonException;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;

public class PageFactory {

  private String source;
  private JsonObject json;
  private IWorkerContext context;
  private ContextUtilities utils;
  private String dir;
  
  public PageFactory(String filename, String dir) throws JsonException, IOException {
    source = filename;
    json = JsonParser.parseObjectFromFile(filename);
    this.dir = dir;
  }
  
  public IWorkerContext getContext() {
    return context;
  }

  public void setContext(IWorkerContext context) {
    this.context = context;
    utils = new ContextUtilities(context);
  }

  private String itemFactory() {
    return json.asString("item-factory");
  }
  
  private String sourceFile() {
    return json.asString("source-file");
  }
  
  private List<String> variables() {
    List<String> vars = new ArrayList<>();
    for (JsonObject obj : json.forceArray("variables").asJsonObjects()) {
      vars.add(obj.asString("name"));
    }
    return vars;
  }

  private String variableValue(String name, String item) {
    for (JsonObject obj : json.forceArray("variables").asJsonObjects()) {
      if (name.equals(obj.asString("name"))) {
        switch (obj.asString("transform")) {
        case "n/a": return item;
        case "lowercase" : return item.toLowerCase();
        case "uppercase" : return item.toUpperCase();
        }
      }
    }
    return null;
  }

  private String generatedFileName() {
    return json.asString("generated-file-name");
  }
  
  private String statedFileName() {
    return json.asString("stated-file-name");
  }
  
  private String parentPage() {
    return json.asString("parent-page");
  }
  
  private String generation() {
    return json.asString("generation");
  }
  
  private String pageTitle() {
    return json.asString("page-title");
  }
  
  boolean debug() {
    return json.asBoolean("debug");
  }
  
  private List<String> items() {
    switch (itemFactory()) {
    case "types" : return removeExceptions(itemsForTypes());
    case "resources" : return removeExceptions(itemsForResources());
    case "datatypes" : return removeExceptions(itemsForDataTypes());
    case "canonicals" : return removeExceptions(itemsForCanonicalResources());
    case "manual" : return removeExceptions(manualItemList());
    }
    throw new Error("Unknown page factory 'item-factory' value "+itemFactory()+"'");
  }

  private List<String> removeExceptions(List<String> list) {
    List<String> ret = new ArrayList<>();
    ret.addAll(list);
    if (json.has("except-items")) {
      ret.removeAll(json.getStrings("except-items"));
    }
    return ret;
  }

  private List<String> manualItemList() {
    return json.forceArray("items").asStrings();
  }

  private List<String> itemsForDataTypes() {
    List<String> res = itemsForTypes();
    res.removeAll(itemsForResources());
    return res;
  }

  private List<String> itemsForResources() {
    return context.getResourceNames();
  }

  private List<String> itemsForCanonicalResources() {
    return utils.getCanonicalResourceNames();
  }
  
  private List<String> itemsForTypes() {
    return utils.getTypeNames();
  }
  
  public void execute(String repoSource, ImplementationGuide ig) throws FileNotFoundException, IOException {
    doDebug("pf: "+source);
    checks(repoSource, ig);
    for (String item : sorted(items())) {
      doDebug("  "+item);
      execute(item, repoSource, ig);
    }
  }
  
  private void doDebug(String msg) {
    if (debug()) {
      System.out.println(msg);
    }
  }

  public static List<String> sorted(Collection<String> set) {
    List<String> list = new ArrayList<>();
    list.addAll(set);
    Collections.sort(list);
    return list;
  }
  
  private void execute(String item, String repoSource, ImplementationGuide ig) throws FileNotFoundException, IOException {
    String source = FileUtilities.fileToString(Utilities.path(repoSource, sourceFile()));
    for (String n : variables()) {
      String v = variableValue(n, item);
      doDebug("   "+n+" --> "+v);
      source = source.replace(n, v);
    }
    String fn = generatedFileName(item);
    doDebug("  save to "+fn);
    FileUtilities.stringToFile(source, Utilities.path(dir, "_includes", fn));
    ImplementationGuideDefinitionPageComponent page = getParentPage(ig.getDefinition().getPage());
    ImplementationGuideDefinitionPageComponent subPage = page.addPage();
    subPage.setSource(new UrlType(statedFileName(item)));
    subPage.setName(statedFileName(item));
    subPage.setGeneration(GuidePageGeneration.fromCode(generation()));
    subPage.setTitle(title(item)); 
    doDebug("  page "+subPage.getName()+":"+subPage.getGenerationElement().toString()+" = "+subPage.getTitle());
  }

  private ImplementationGuideDefinitionPageComponent getParentPage(ImplementationGuideDefinitionPageComponent page) {
    if (parentPage().equals(page.getName())) {
      return page;
    }
    for (ImplementationGuideDefinitionPageComponent p : page.getPage()) {
      ImplementationGuideDefinitionPageComponent t = getParentPage(p);
      if (t != null) {
        return t;
      }
    }
    return null;
  }

  private String title(String item) {
    return pageTitle().replace("%item%", item);
  }

  private String generatedFileName(String item) {
    return generatedFileName().replace("%item%", item);
  }

  private String statedFileName(String item) {
    return statedFileName().replace("%item%", item);
  }

  private void checks(String repoSource, ImplementationGuide ig) throws IOException {
    checkFileExists("source-file", Utilities.path(repoSource, sourceFile()));
    checkFileExists("page folder", dir);
    FileUtilities.clearDirectory(dir);
    FileUtilities.createDirectory(Utilities.path(dir, "_includes"));
  }

  private void checkFileExists(String purpose, String path) {
    File f = new File(path);
    if (!f.exists()) {
      throw new FHIRException("Unable to find "+purpose+" file "+path);
    }
  }
  
}
