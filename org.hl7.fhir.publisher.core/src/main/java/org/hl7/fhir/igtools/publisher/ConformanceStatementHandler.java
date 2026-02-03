package org.hl7.fhir.igtools.publisher;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.HTMLInspector.LoadedFile;
import org.hl7.fhir.igtools.publisher.HTMLInspector.XhtmlNodeHolder;
import org.hl7.fhir.r5.context.ILoggingService;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.model.ActorDefinition;
import org.hl7.fhir.r5.model.BooleanType;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.Requirements;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDefinitionParameterComponent;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionContainsComponent;
import org.hl7.fhir.r5.renderers.ActorDefinitionRenderer;
import org.hl7.fhir.r5.renderers.Renderer.RenderingStatus;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.terminologies.expansion.ValueSetExpansionOutcome;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.NaturalOrderComparator;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.i18n.I18nConstants;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xhtml.XhtmlToMarkdownConverter;
import org.hl7.fhir.validation.BaseValidator.BooleanHolder;

class ConformanceStatementHandler {
  class HomeInfo {
    LoadedFile source;
    XhtmlNode node;
    
    private HomeInfo(LoadedFile source, XhtmlNode node) {
      this.source = source;
      this.node = node;
    }
  }
  
  public static final String EXT_REQACTORKEY = "http://hl7.org/fhir/tools/StructureDefinition/requirements-actorkey";
  public static final String EXT_CSACTOR = "http://hl7.org/fhir/tools/StructureDefinition/requirements-statementactor";
  public static final String EXT_CSCAT = "http://hl7.org/fhir/tools/StructureDefinition/requirements-statementcategory";
  public static final String EXT_CSSHALLNOT = "http://hl7.org/fhir/tools/StructureDefinition/requirements-statementshallnot";
  
  private HTMLInspector inspector;
  private Map<String, ActorDefinition> usedActors = new HashMap<>();
  private Map<String, Coding> usedCategories = new HashMap<>();
  private List<String> foundExpectations = new ArrayList<>();
  private String termShallNot;
  private String termShall;
  private String termShouldNot;
  private String termShould;
  private String termMay;
  private String termShallNotPlural;
  private String termShallPlural;
  private String termShouldNotPlural;
  private String termShouldPlural;
  private String termMayPlural;
  private Map<String, Integer> langCounts = new HashMap<>();
  private Map<String, ActorDefinition> actorDefs;
  private Map<String, Coding> categories;
  private String rootUrl;
  private ActorDefinitionRenderer rr;
  private ImplementationGuide thisIg;
  private XhtmlToMarkdownConverter xhtmlToMd;
  private String defaultLang;
  private String reqFolder;
  private List<String> translationLangs;
  private Map<String, HomeInfo> confHomes = new HashMap<>();
  private Map<String, Map<String, ConformanceClause>> languageClauses = new HashMap<>();
  private IWorkerContext context;
  private RenderingContext rc;
  private ILoggingService log;
  private List<String> langList;
  private boolean forHL7;

  public ConformanceStatementHandler(HTMLInspector htmlInspector, IWorkerContext context, ILoggingService log, List<String> langList, boolean forHL7) {
    this.inspector = htmlInspector;
    this.xhtmlToMd = new XhtmlToMarkdownConverter(true);
    this.langList = langList;
    this.context = context;
    this.log = log;
    this.forHL7 = forHL7;
  }
  
  /*
   * This will find the ActorDefinition with a matching id.
   * If there are multiple actors in-scope with the same id, we will return the one defined in this IG
   * If there are multiple actors in-scope with the same id and none are defined in this IG, will log a message and won't return an actor
   */
  public ActorDefinition findActor(String id) {
    if (actorDefs == null) {
      actorDefs = new HashMap<String, ActorDefinition>();
      for (ActorDefinition anActor: context.fetchResourcesByType(ActorDefinition.class)) {
        if (actorDefs.containsKey(anActor.getId())) {
          ActorDefinition matchingActor = actorDefs.get(anActor.getId());
          if (matchingActor != null && matchingActor.getUrl().equals(anActor.getUrl())) {
            // We already have an actor with this url and we don't care about the version
            // We can ignore the additional actor repetition
          } else {
            if (matchingActor != null && matchingActor.getUrl().startsWith(rootUrl)) {
              // Ignore this new actor, as the actor from this IG trumps and is already found
            } else if (anActor.getUrl().startsWith(rootUrl)) {
              // The new one is for this IG and the old one wasn't, so dump the old one and store this one
              actorDefs.remove(anActor.getId());
              actorDefs.put(anActor.getId(), anActor);
            } else {
              actorDefs.remove(anActor.getId());
              actorDefs.put(anActor.getId(), null);
              log.logMessage("There are multiple ActorDefinitions with the same id and neither is from this IG. Ignoring reference to actorId " + anActor.getId() + " when processing conformance statements.");
            }
          }
        } else {
          actorDefs.put(anActor.getId(), anActor);
        }
      }
    }
      
    return actorDefs.get(id);
  }
  
  public Coding findCategory(String id) {
    return categories.get(id);
  }
  
  
  /*
   * Sets up information in the HTMLInspector that can't be done at the time the inspector is initialized
   * (because all IGs, including the 'current' IG aren't loaded at the time this class first comes into being).
   */
  public void setup() {
    List<ImplementationGuide> l = context.fetchResourcesByType(ImplementationGuide.class);
    thisIg = l.get(l.size() - 1);
    rootUrl = StringUtils.substringBefore(thisIg.getUrl(), "ImplementationGuide");
    categories = new HashMap<String, Coding>();
    for (ImplementationGuideDefinitionParameterComponent param: thisIg.getDefinition().getParameter()) {
      if (param.getCode().getSystem().equals("http://hl7.org/fhir/tools/CodeSystem/ig-parameters") && param.getCode().getCode().equals("requirements-category-vs")) {
        String valuesetUrl = param.getValue();
        ValueSet categoriesVs = context.fetchResource(ValueSet.class, valuesetUrl);
        ValueSetExpansionOutcome expansion = context.expandVS(categoriesVs, false, true, 100);
        for (ValueSetExpansionContainsComponent catCode: expansion.getValueset().getExpansion().getContains()) {
          categories.put(catCode.getCode(), new Coding(catCode.getSystem(), catCode.getCode(), catCode.getDisplay()));
        }
        break;
      }
    }
  }
  
  private String translateExpectationEnglish(String expectation) {
    if (expectation.equals(Requirements.ConformanceExpectation.SHALL.toCode()))
      return rc.formatPhrase(RenderingContext.CONF_SHALL); 
    if (expectation.equals("SHALLNOT"))
      return rc.formatPhrase(RenderingContext.CONF_SHALLNOT); 
    if (expectation.equals(Requirements.ConformanceExpectation.SHOULD.toCode()))
      return rc.formatPhrase(RenderingContext.CONF_SHOULD); 
    if (expectation.equals(Requirements.ConformanceExpectation.SHOULDNOT.toCode()))
      return rc.formatPhrase(RenderingContext.CONF_SHOULDNOT); 
    if (expectation.equals(Requirements.ConformanceExpectation.MAY.toCode()))
      return rc.formatPhrase(RenderingContext.CONF_MAY); 

    return "Unknown Conformance";
  }

  private String translateExpectation(String expectation) {
    if (expectation.equals(Requirements.ConformanceExpectation.SHALL.toCode()))
      return termShall;
    if (expectation.equals("SHALLNOT"))
      return termShallNot;
    if (expectation.equals(Requirements.ConformanceExpectation.SHOULD.toCode()))
      return termShould;
    if (expectation.equals(Requirements.ConformanceExpectation.SHOULDNOT.toCode()))
      return termShouldNot;
    if (expectation.equals(Requirements.ConformanceExpectation.MAY.toCode()))
      return termMay;

    return "Unknown Conformance";
  }

  public void generateRequirementsInstance(List<ValidationMessage> messages) throws IOException {
    String reqUrl = this.rootUrl + "Requirements/fromNarrative";
    Requirements req = context.fetchResource(Requirements.class, reqUrl);
    Map<String, Requirements.RequirementsStatementComponent> oldReq = new HashMap<>();
    boolean mismatch = false;
    if (req != null) {
      req.setText(null);
      for (Requirements.RequirementsStatementComponent comp: req.getStatement()) {
        oldReq.put(comp.getKey(), comp);
      }
      req.setStatement(new ArrayList<>());
      if (req.getActor().size()!=this.usedActors.size())
        mismatch = true;
      else {
        for (ActorDefinition actor: usedActors.values()) {
          if(!req.hasActor(actor.getUrl()))
            mismatch = true;
        }
      }
      req.setActor(new ArrayList<>());
      for (ActorDefinition actor: usedActors.values()) {
        CanonicalType actorRef = req.addActorElement();
        actorRef.setValue(actor.getUrl());
        actorRef.addExtension(EXT_REQACTORKEY, new StringType(actor.getId()));
      }

      
    } else {
      req = new Requirements();
      req.setId("fromNarrative");
      req.setUrl(reqUrl);
      req.setName("FromNarrative");
      req.setTitle(rc.formatPhrase(RenderingContext.CSREQ_TITLE));
      req.setStatus(PublicationStatus.ACTIVE);
      req.setExperimental(false);
      req.setDescription(rc.formatPhrase(RenderingContext.CSREQ_DESC));
    }
    
    
    Map<String, ConformanceClause> primaryClauses = languageClauses.get(defaultLang);
    List<String> keyList = new ArrayList<>();
    keyList.addAll(primaryClauses != null ? primaryClauses.keySet() : Collections.emptyList());
    Collections.sort(keyList, new NaturalOrderComparator<String>());
    for (String key: keyList) {
      Requirements.RequirementsStatementComponent newComp;
      if (oldReq.containsKey(key))
        newComp = oldReq.get(key);
      else {
        mismatch = true;
        newComp = new Requirements.RequirementsStatementComponent();
        newComp.setKey(key);
      }
      req.addStatement(newComp);
      ConformanceClause clause = primaryClauses.get(key);
      
      boolean hasShallNot = newComp.hasExtension(EXT_CSSHALLNOT);
      int effectiveSize = newComp.getConformance().size() + (hasShallNot ? 1 : 0);
      if (effectiveSize!=clause.getExpectations().size() || !matchConfExpect(newComp.getConformance(),clause.getExpectations(), hasShallNot)) {
        mismatch = true;
        newComp.setConformance(new ArrayList<>());
        for (String expectation: clause.getExpectations()) {
          if (expectation.equals("SHALLNOT")) {
            newComp.addExtension(EXT_CSSHALLNOT, new BooleanType(true));
          } else {
            newComp.addConformance(Requirements.ConformanceExpectation.fromCode(expectation));
          }
        }
      }
      
      if (!newComp.getConditionality()==clause.isConditional()) {
        mismatch = true;
        newComp.setConditionality(clause.isConditional());
      }

      List<Extension> cat = newComp.getExtensionsByUrl(EXT_CSCAT); 
      if (cat.size()!=clause.getCategories().size() || !matchCategories(cat, clause.getCategories())) {
        mismatch = true;
        newComp.removeExtension(EXT_CSCAT);
        for (Coding category: clause.getCategories()) {
          newComp.addExtension(EXT_CSCAT, category);
        }
      }
      
      List<Extension> actors = newComp.getExtensionsByUrl(EXT_CSACTOR);
      boolean actorsMatch = true;
      if (actors.size() != clause.getActors().size())
        actorsMatch = false;
      else {
        for (int i=0;i<actors.size();i++) {
          String actorUrl = actorDefs.get(actors.get(i).getValue().toString()).getUrl();
          if (!actorUrl.equals(clause.getActors().get(i).getUrl())) {
            actorsMatch = false;
            break;
          }
        }
      }

      if (!actorsMatch) {
        mismatch = true;
        newComp.removeExtension(EXT_CSACTOR);
        for (ActorDefinition actor: clause.getActors()) {
          newComp.addExtension(EXT_CSACTOR, new StringType(actor.getId()));
        }
      }
      
      if (newComp.hasLabel() != clause.hasSummary() || (newComp.hasLabel() && !newComp.getLabel().equals(clause.getSummary()))) {
        mismatch = true;
        newComp.setLabel(clause.getSummary());
      }
      
      String md = xhtmlToMd.convert(clause.getOrigNode()).trim();
      if (!newComp.hasRequirement() || !newComp.getRequirement().equals(md)) {
        mismatch = true;
        newComp.setRequirement(md);
      }
      
      // Add language translations for string and markdown elements
      if (!translationLangs.isEmpty()) {
        for (String tLang: translationLangs) {
          Map<String, ConformanceClause> tClauseMap = languageClauses.get(tLang);
          ConformanceClause tClause = tClauseMap == null ? null : (tClauseMap.containsKey(key) ? tClauseMap.get(key) : null);
          
          String tranLabel = ExtensionUtilities.getLanguageTranslation(newComp.getLabelElement(), tLang);
          if (newComp.hasLabel()) {
            if (tranLabel == null) {
              if (tClause==null || !tClause.hasSummary()) {
                // All ok
              } else {
                mismatch = true;
                ExtensionUtilities.addLanguageTranslation(newComp.getLabelElement(), tLang, tClause.getSummary());
              }
            } else if (!tranLabel.equals(newComp.getLabel())) {
              mismatch = true;
              ExtensionUtilities.setLanguageTranslation(newComp.getLabelElement(), tLang, tClause.getSummary());
            }
          } else if (tranLabel!= null) {
            ExtensionUtilities.removeLanguageTranslation(newComp.getLabelElement(), tLang);
          }
          
          String tranRequirement = ExtensionUtilities.getLanguageTranslation(newComp.getRequirementElement(), tLang);
          if (tClause==null) { 
            ExtensionUtilities.removeLanguageTranslation(newComp.getRequirementElement(), tLang);
          } else {
            String tranMd = xhtmlToMd.convert(tClause.getOrigNode()).trim();
            if (tranRequirement == null) {
              mismatch = true;
              ExtensionUtilities.addLanguageTranslation(newComp.getRequirementElement(), tLang, tranMd);
            } else if (!tranRequirement.equals(tranMd)) {
              mismatch = true;
              ExtensionUtilities.setLanguageTranslation(newComp.getRequirementElement(), tLang, tranMd);
            }
          }
        }
      }
    }
    
    if (mismatch) {
      String fname = Utilities.path(reqFolder, "Requirements-fromNarrative.json");
      FileOutputStream fs = new FileOutputStream(fname);
      if (!oldReq.isEmpty())
        messages.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, "Requirements/fromNarrative", "There are differences between the requirements found in the narrative and what's found the provided Requirements resource.  A new version has been generated in the root.  It should be used to replace the one in the input folder.",
            IssueSeverity.WARNING));
        
      new org.hl7.fhir.r5.formats.JsonParser().setOutputStyle(org.hl7.fhir.r5.formats.IParser.OutputStyle.PRETTY).compose(fs, req);
    }
  }
  
  private boolean matchConfExpect(List<Enumeration<Requirements.ConformanceExpectation>> l1, List<String> l2, boolean hasShallNot) {
    List<String> l1Cond = new ArrayList<>();
    for (int i = 0; i < l1.size(); i++){
      l1Cond.add(l1.get(i).asStringValue());
    }
    if (hasShallNot)
      l1Cond.add("SHALLNOT");
    for (int i = 0; i< l2.size(); i++) {
      if (!l1Cond.contains(l2.get(i)))
          return false;
    }
    
    return true;
  }
  
  private boolean matchCategories(List<Extension> l1, List<Coding> l2) {
    for (int i = 0; i< l1.size(); i++) {
      Coding l1Cat = l1.get(i).getValueCoding();
      if (!l1Cat.equalsDeep(l2.get(i)))
        return false;
    }
    
    return true;
  }
  
  private void renderConformanceHome(String lang) throws IOException {
    HomeInfo info = confHomes.get(lang);
    XhtmlNode confHome = info.node;
    if (confHome != null) {
      XhtmlNode thead = confHome.addTag("thead");
      XhtmlNode tbody = confHome.addTag("tbody");
      XhtmlNode header = thead.tr().style("background-color: WhiteSmoke;");
      rc = new RenderingContext(context, new MarkDownProcessor(MarkDownProcessor.Dialect.COMMON_MARK),
          null, "http://hl7.org/fhir", "", new Locale(lang), RenderingContext.ResourceRendererMode.END_USER, RenderingContext.GenerationRules.VALID_RESOURCE);

      header.th().style("text-align: center;").tx(rc.formatPhrase(RenderingContext.CSTABLE_HEAD_ID));
      header.th().style("text-align: center;").tx(rc.formatPhrase(RenderingContext.CSTABLE_HEAD_EXPECT));
      header.th().style("text-align: center;").tx(rc.formatPhrase(RenderingContext.CSTABLE_HEAD_COND));
      if (!usedActors.isEmpty())
        header.th().style("text-align: center;").tx(rc.formatPhrase(RenderingContext.CSTABLE_HEAD_ACTOR));
      if (!usedCategories.isEmpty())
        header.th().style("text-align: center;").tx(rc.formatPhrase(RenderingContext.CSTABLE_HEAD_CAT));
      header.th().style("text-align: center;").tx(rc.formatPhrase(RenderingContext.CSTABLE_HEAD_RULE));
      
      XhtmlNode filters = thead.tr().style("background-color: WhiteSmoke;");
      filters.td().input("filterid", "text", " ", 4).attribute("title", rc.formatPhrase(RenderingContext.CSTABLE_TITLE_ID));
      XhtmlNode expectFilter = filters.td();
      expectFilter.attribute("title", rc.formatPhrase(RenderingContext.CSTABLE_TITLE_EXPECT));
      int count=1;
      for (String expect: foundExpectations) {
        if (count!=1)
          expectFilter.br();
        expectFilter.input("expect" + count++,  "checkbox",  null, 0).nbsp().tx(translateExpectationEnglish(expect));
      }
      XhtmlNode conditionFilter = filters.td();
      conditionFilter.attribute("title", rc.formatPhrase(RenderingContext.CSTABLE_TITLE_COND));
      conditionFilter.input("conditionFilter", "radio",  null, 0).attribute("id", "conditionYes").attribute("value", "true").nbsp().tx(rc.formatPhrase(RenderingContext.CSTABLE_COND_YES));
      conditionFilter.br();
      conditionFilter.input("conditionFilter", "radio",  null, 0).attribute("id", "conditionNo").attribute("value", "false").nbsp().tx(rc.formatPhrase(RenderingContext.CSTABLE_COND_NO));
      conditionFilter.br();
      conditionFilter.input("conditionFilter", "radio",  null, 0).nbsp().attribute("id", "conditionAny").attribute("value", "").attribute("checked", "true").tx(rc.formatPhrase(RenderingContext.CSTABLE_COND_ANY));
      if (!usedActors.isEmpty()) {
        XhtmlNode actorFilter = filters.td();
        actorFilter.attribute("title", rc.formatPhrase(RenderingContext.CSTABLE_TITLE_ACTOR));
        count = 1;
        List<String> actorTitles = new ArrayList<>();
        Map<String,ActorDefinition> actorLookup = new HashMap<>();
        for (ActorDefinition actor: usedActors.values()) {
          String transTitle = actor.getTitleElement().getTranslation(lang);
          actorTitles.add(transTitle);
          actorLookup.put(transTitle, actor);
        }
        Collections.sort(actorTitles);
        for (String title: actorTitles) {
          ActorDefinition actor = actorLookup.get(title);
          if (count!=1)
            actorFilter.br();
          XhtmlNode actorInput = actorFilter.input("actor" + count++,  "checkbox",  null, 1);
          actorInput.nbsp();
          rr.renderCanonical(new RenderingStatus(), ResourceWrapper.forResource(rc, actor), actorInput,  ActorDefinition.class, new CanonicalType(actor.getUrl()), lang);
        }
      }
      if (!usedCategories.isEmpty()) {
        XhtmlNode categoryFilter = filters.td();
        categoryFilter.attribute("title", rc.formatPhrase(RenderingContext.CSTABLE_TITLE_CAT));
        count = 1;
        List<String> codingDisplays = new ArrayList<>();
        Map<String,Coding> codingLookup = new HashMap<>();
        for (Coding category: usedCategories.values()) {
          String transDisplay = category.getDisplayElement().getTranslation(lang);
          codingDisplays.add(transDisplay);
          codingLookup.put(transDisplay, category);
        }
        Collections.sort(codingDisplays);
        for (String display: codingDisplays) {
          Coding category = codingLookup.get(display);
          if (count!=1)
            categoryFilter.br();
          XhtmlNode categoryInput = categoryFilter.input("category" + count++,  "checkbox",  null, 1);
          categoryInput.nbsp();
          categoryInput.tx(rr.displayCoding(ResourceWrapper.forType(rc.getContextUtilities(), category)));
        }
      }
      XhtmlNode filterRule = filters.td();
      filterRule.input("filterrule", "text", " ", 20).attribute("title", rc.formatPhrase(RenderingContext.CSTABLE_TITLE_RULE));
      filterRule.input("clearFilters", "button", null, 10).style("float:right").attribute("value", rc.formatPhrase(RenderingContext.CSTABLE_CLEAR_FILTERS)).attribute("title", rc.formatPhrase(RenderingContext.CSTABLE_TITLE_CLEAR));
      
      Set<LoadedFile> sources = new HashSet<>();
      sources.add(info.source);
      int ci = 0;
      for (ConformanceClause clause : clausesForLang(lang)) {
        sources.add(clause.getSource());
        String id = clause.getId();
        if (Utilities.noString(id)) {
          ci++;
          id = "cnf-"+ci;
        }
        XhtmlNode tr = tbody.tr();
        XhtmlNode idCell = tr.th();
        idCell.an("ci-c-"+id);
        idCell.ah(Paths.get(clause.getSource().filename).getFileName().toString() +"#ci-c-"+id).tx("§"+id);
        XhtmlNode expectNode = tr.td();
        boolean first = true;
        for (String expectation: clause.getExpectations()) {
          if (first)
            first = false;
          else
            expectNode.br();
          expectNode.tx(translateExpectationEnglish(expectation));
        }
        tr.td().style("text-align: center;").tx(clause.isConditional() ? "X" : " ");
        if (!usedActors.isEmpty()) {
          XhtmlNode actors = tr.td();
          boolean firstActor = true;
          for (ActorDefinition actor: clause.getActors()) {
            if (firstActor)
              firstActor = false;
            else
              actors.br();
            rr.renderCanonical(new RenderingStatus(), ResourceWrapper.forResource(rc, actor), actors,  ActorDefinition.class, new CanonicalType(actor.getUrl()), lang);
          }
        }
        
        if (!usedCategories.isEmpty()) {
          XhtmlNode categories = tr.td();
          boolean firstCategory = true;
          for (Coding category: clause.getCategories()) {
            if (firstCategory)
              firstCategory = false;
            else
              categories.br();
            categories.tx(rr.displayCoding(ResourceWrapper.forType(rc.getContextUtilities(), category)));
          }
        }
        
        if (clause.hasSummary()) {
          tr.td().tx(clause.getSummary());
        } else {
          tr.td().getChildNodes().addAll(clause.getNode().getChildNodes());
        }
        XhtmlNode focus = clause.getNode();
        if (clause.div) {
          focus = clause.getNode().firstNamedDescendent("p");
          if (focus == null) {
            focus = clause.getNode();
          }
        }
        focus.an("ci-c-" + id).tx(" ");
        XhtmlNode f = focus.ah(Paths.get(info.source.filename).getFileName().toString() + "#ci-c-" + id);
        if (clause.isDiv()) {
          f.img("conformance.png", "conf").attribute("width", "20").attribute("class", "self-link").attribute("height", "20");
          f.tx("§" + id);
        } else {
          f.supr("§" + id);
        }
      }
      for (LoadedFile lf : sources) {
        inspector.saveFile(lf, lf.xhtml);
      }
    }
  }
  
  private Collection<ConformanceClause> clausesForLang(String lang) {
    if (!languageClauses.containsKey(lang))
      return new ArrayList<ConformanceClause>();
    Map<String, ConformanceClause> clauseMap = languageClauses.get(lang);
    List<String> idList = new ArrayList<>(clauseMap.keySet());
    Collections.sort(idList, new NaturalOrderComparator<String>());
    List<ConformanceClause> clauses = new ArrayList<>();
    for (String id: idList) {
      clauses.add(clauseMap.get(id));
    }
    return clauses;
  }
  
  public void readConformanceClauses(LoadedFile source, XhtmlNode x, BooleanHolder hasClauses, BooleanHolder hasWarning, List<ValidationMessage> messages) {
    // there's two kinds of conformance clauses:
    // from XML
    //  <span class="fhir-conformance" id="id">clause</span> - in paragraph clause id is optional but recommended
    //  <div class=="fhir-conformance" id="id" summary="summary">multi-paragraphs</div> - summary is mandatory
    // and from markdown
    //  §id:clause$. in-paragraph clause. Id must be a token if present. it's optional but recommended
    //  §§id:summary. both id and summary are mandatory
    //
    // the first pass is finding and fixing the markdown clauses, and then we process them at the end
    // spans

    String langName = langForSource(source);
    rc = new RenderingContext(context, new MarkDownProcessor(MarkDownProcessor.Dialect.COMMON_MARK),
        null, "http://hl7.org/fhir", "", new Locale(langName), RenderingContext.ResourceRendererMode.END_USER, RenderingContext.GenerationRules.VALID_RESOURCE);
    rr = new ActorDefinitionRenderer(rc);

    termShallNot = rc.formatPhrase(RenderingContext.CONF_SHALLNOT); 
    termShall = rc.formatPhrase(RenderingContext.CONF_SHALL); 
    termShouldNot = rc.formatPhrase(RenderingContext.CONF_SHOULDNOT); 
    termShould = rc.formatPhrase(RenderingContext.CONF_SHOULD); 
    termMay = rc.formatPhrase(RenderingContext.CONF_MAY); 
    termShallNotPlural = rc.formatPhrase(RenderingContext.CONF_SHALLNOT_PLURAL); 
    termShallPlural = rc.formatPhrase(RenderingContext.CONF_SHALL_PLURAL); 
    termShouldNotPlural = rc.formatPhrase(RenderingContext.CONF_SHOULDNOT_PLURAL); 
    termShouldPlural = rc.formatPhrase(RenderingContext.CONF_SHOULD_PLURAL); 
    termMayPlural = rc.formatPhrase(RenderingContext.CONF_MAY_PLURAL); 
    
    List<XhtmlNodeHolder> divs = new ArrayList<>();
    processMarkdownConformanceClauses(x, hasClauses, divs);
    List<XhtmlNode> parents = new ArrayList<>();
    List<ValidationMessage> unwarnedMessages = new ArrayList<ValidationMessage>();
    processConformanceClauses(source, parents, x, hasClauses, hasWarning, messages, unwarnedMessages);
    if (hasClauses.ok()) {
      source.xhtml = x;
      // We have called out clauses on this page, so show *all* non-flagged conformance statements, not just the first one
      messages.addAll(unwarnedMessages);
    }
  }

  private String langForSource(LoadedFile source) {
    Path langFolder = Paths.get(source.filename).normalize().getParent();
    String langName = langFolder.getName(langFolder.getNameCount()-1).toString();
    if (langName.length()!=2)
      langName = context.getLocale().getLanguage();
    return langName;
  }
  
  private void processConformanceClauses(LoadedFile source, List<XhtmlNode> parents, XhtmlNode x, BooleanHolder hasClauses, BooleanHolder hasWarning, List<ValidationMessage> messages, List<ValidationMessage> unwarnedMessages) {
    processConformanceClauses(source, parents, x, hasClauses, hasWarning, messages, unwarnedMessages, "");
  }
  
  private void processConformanceClauses(LoadedFile source, List<XhtmlNode> parents, XhtmlNode x, BooleanHolder hasClauses, BooleanHolder hasWarning, List<ValidationMessage> messages, List<ValidationMessage> unwarnedMessages, String nodeText) {
    String lang = langForSource(source);
    List<XhtmlNode> nparents = new ArrayList<>();
    nparents.addAll(parents);
    nparents.add(x);
    if (nodeText.isEmpty() && x.getNodeType() == NodeType.Element && new HashSet<>(Arrays.asList("p", "li", "span", "td")).contains(x.getName()))
      nodeText = x.toLiteralText().replaceAll("\\s", " ");
    for (XhtmlNode c : x.getChildNodes()) {
      if (c.getNodeType() == NodeType.Element && "span".equals(c.getName()) && c.hasClass("fhir-conformance")) {
        String spanId = c.getAttribute("id");
        try {
          ConformanceClause clause = new ConformanceClause(this, getHeading(parents, x), c, source, c.getAttribute("id"), lang);
          if (spanId!=null && spanId.contains("^")) {
            // Strip latter parts of the id
            c.setAttribute("id", StringUtils.substringBefore(spanId,  "^"));
          }
          addTitle(c, clause);
          addClause(lang, clause, source.path, messages);
        } catch (FHIRException e) {
//          messages.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, source.path, context.formatMessage(I18nConstants.CONFORMANCE_STATEMENT_NOCONFWORD, lang, c.toString()), IssueSeverity.INFORMATION).setMessageId(I18nConstants.CONFORMANCE_STATEMENT_NOCONFWORD));          
          messages.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, source.path, e.getMessage(), IssueSeverity.INFORMATION).setMessageId(I18nConstants.CONFORMANCE_STATEMENT_NOCONFWORD));          
        }
      } else if (c.getNodeType() == NodeType.Element && "div".equals(c.getName()) && c.hasClass("fhir-conformance")) {
        String spanId = c.getAttribute("id");
        try {
          ConformanceClause clause = new ConformanceClause(this, getHeading(parents, x), c, source, c.getAttribute("id"), lang, c.getAttribute("summary"));
          if (spanId!=null && spanId.contains("^")) {
            // Strip latter parts of the id
            c.setAttribute("id", StringUtils.substringBefore(spanId,  "^"));
          }
          if (clause.hasSummary()) {
            addTitle(c, clause);
            addClause(lang, clause, source.path, messages);
          } else {
            messages.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, source.path, context.formatMessage(I18nConstants.CONFORMANCE_STATEMENT_NOSUMMARY, clause.getId(), lang), IssueSeverity.INFORMATION).setMessageId(I18nConstants.CONFORMANCE_STATEMENT_NOCONFWORD));          
          }
        } catch (FHIRException e) {
          messages.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, source.path, context.formatMessage(I18nConstants.CONFORMANCE_STATEMENT_NOCONFWORD, lang, getStringWithoutNewlines(c.toString())), IssueSeverity.INFORMATION).setMessageId(I18nConstants.CONFORMANCE_STATEMENT_NOCONFWORD));
        }
      } else if (c.getNodeType() == NodeType.Element && "table".equals(c.getName()) && c.hasClass("fhir-conformance-list")) {
        if (confHomes.containsKey(lang)) {
          HomeInfo info = confHomes.get(lang);
          messages.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, source.path, context.formatMessage(I18nConstants.CONFORMANCE_STATEMENT_DUPHOME, lang, info.source.path, source.path), IssueSeverity.INFORMATION).setMessageId(I18nConstants.CONFORMANCE_STATEMENT_DUPHOME));
        } else {
          HomeInfo info = new HomeInfo(source, c);
          confHomes.put(lang, info);
        }
        c.getChildNodes().clear();
        hasClauses.set(true);
      } else if (c.getNodeType() == NodeType.Element) {
        boolean process = true;
        if (c.hasAttribute("data-fhir")) {
          process = !c.getAttribute("data-fhir").startsWith("generated");
        }
        if (process) {
          processConformanceClauses(source, nparents, c, hasClauses, hasWarning, messages, unwarnedMessages, nodeText);
        }
      } else if (c.getNodeType() == NodeType.Text && forHL7) {
        String s = c.getContent();
        String conformanceWord = null;
        if (s.contains(termShallNot)) {
          conformanceWord = termShallNot;
        } else if (s.contains(termShall)) {
          conformanceWord = termShall;
        } else if (s.contains(termShouldNot)) {
          conformanceWord = termShouldNot;
        } else if (s.contains(termShould)) {
          conformanceWord = termShould;
        } else if (s.contains(termMay)) {
          conformanceWord = termMay;
        }
        if (conformanceWord != null) {
          if (!hasWarning.ok()) {
            messages.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, source.path,
                    context.formatMessage(I18nConstants.CONFORMANCE_STATEMENT_WORD, conformanceWord, nodeText), IssueSeverity.INFORMATION).setMessageId(I18nConstants.CONFORMANCE_STATEMENT_WORD));
            hasWarning.set(true);
          } else {
            unwarnedMessages.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, source.path,
                context.formatMessage(I18nConstants.CONFORMANCE_STATEMENT_WORD, conformanceWord, nodeText), IssueSeverity.INFORMATION).setMessageId(I18nConstants.CONFORMANCE_STATEMENT_WORD));
          }
        } 
      }
    }
  }

  private static String getStringWithoutNewlines(String string) {
    if (string == null) {
      return null;
    }
    String noCrNl = string.replaceAll("\r\n", " ");
    String noNl = noCrNl.replaceAll("\n", " ");
    return noNl;
  }

  private void addClause(String lang, ConformanceClause clause, String path, List<ValidationMessage> messages) {
    Map<String, ConformanceClause> clauseMap;
    if (languageClauses.containsKey(lang)) {
      clauseMap = languageClauses.get(lang);
    } else {
      clauseMap = new HashMap<>();
      languageClauses.put(lang, clauseMap);
    }
    if (clauseMap.containsKey(clause.getId()))
      messages.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, path, context.formatMessage(I18nConstants.CONFORMANCE_STATEMENT_DUP, lang, clause.getId()), IssueSeverity.INFORMATION).setMessageId(I18nConstants.CONFORMANCE_STATEMENT_DUP));
    else
      clauseMap.put(clause.getId(), clause);
  }

  private void addTitle(XhtmlNode c, ConformanceClause clause) {
    if (clause.hasActors() || clause.hasCategories()) {
      String title = "";
      if (clause.hasActors()) {
        title += "Actor(s): ";
        boolean first = true;
        for (ActorDefinition actor: clause.getActors()) {
          if (!first)
            title += ", ";
          else
            first = false;
          title += actor.getTitle();
        }
      }
      if (clause.hasCategories()) {
        if (!title.isEmpty())
          title += "\n";
        title += "Category(ies): ";
        boolean first = true;
        for (Coding category: clause.getCategories()) {
          if (!first)
            title += ", ";
          else
            first = false;
          title += category.getDisplay();
        }
      }
      c.setAttribute("title", title);
    }
  }
  
  private void processMarkdownConformanceClauses(XhtmlNode x, BooleanHolder hasClauses, List<XhtmlNodeHolder> divs) {
    boolean tryAgain = false; // for if there's more than one clause in a run of text
    if ("p".equals(x.getName()) && "§§§".equals(x.allText())) {
      x.setName("table");
      x.getChildNodes().clear();
      x.setAttribute("class", "fhir-conformance-list grid");
      return;
    }

    if (x.allText() != null && "p".equals(x.getName()) && (x.allText().startsWith("§§") || x.allText().startsWith("&sect;&sect;"))) {
      if (divs.isEmpty() || divs.get(0).end != null) {
        // this is the starting node
        divs.add(0, inspector.new XhtmlNodeHolder());
        divs.get(0).start = x;
      } else {
        XhtmlNodeHolder div = divs.get(0);
        div.end = x;
        String cnt = div.start.allText().substring(2);
        String original = div.start.allText().substring(2);
        if (!cnt.contains(":")) {
          throw new FHIRException("Invalid conformance clause id: no ':' making the token in " +original);
        }
        String id = cnt.substring(0, cnt.indexOf(":"));
        if (!id.matches("^[a-zA-Z0-9._\\-\\^\\,\\?]+$")) {
          throw new FHIRException("Invalid conformance clause id: '"+id+"' in " +original);
        }
        cnt = cnt.substring(cnt.indexOf(":")+1);
        if (!cnt.contains("^")) {
          throw new FHIRException("Invalid conformance clause id: no '^' making the summary in " +original);
        }
        String summary = cnt.substring(0, cnt.indexOf("^"));
        cnt = cnt.substring(cnt.indexOf("^")+1);
        x.setName("div");
        div.start.setName("div");
        div.start.setAttribute("class", "fhir-conformance");
        div.start.setAttribute("id", id);
        div.start.setAttribute("summary", summary);
        div.start.getChildNodes().clear();
        if (!cnt.trim().isBlank())
          div.start.para(cnt);
        x.getChildNodes().clear();
        x.para("!");
        hasClauses.set(true);
      }
    } else if (x.allText() != null && "!§§".equals(x.allText())) {
      // special placeholder for documentation
      x.getChildNodes().clear();
      x.tx("§§");
      hasClauses.set(true);
    } else {
      do {
        int start = -1;
        int end = -1;
        tryAgain = false;
        for (int i = 0; i < x.getChildNodes().size(); i++) {
          XhtmlNode c = x.getChildNodes().get(i);
          if (c.getNodeType() == NodeType.Text && c.getContent().contains("§")) {
            int offset = c.getContent().indexOf("§");
            if (start == -1) {
              start = i;
              String s = c.getContent().substring(offset+1);
              if (s.contains("§")) {
                end = i;
                break;
              }
            } else if (end == -1) {
              end = i;
              break;
            } else {
              // we ignore it - we'll get back to it
            }
          }
        }
        if (end > -1) {
          hasClauses.set(true);
          tryAgain = true;
          if (end == start) {
            XhtmlNode span = new XhtmlNode(NodeType.Element, "span");
            span.setAttribute("class", "fhir-conformance");
            XhtmlNode c = x.getChildNodes().get(start);
            String cnt = c.getContent();
            int si = cnt.indexOf("§")+1;
            int ei = cnt.substring(si).indexOf("§") + si;
            span.addText((si > 0 && ei >= si) ? cnt.substring(si, ei) : cnt);
            x.getChildNodes().add(start + 1, span);
            String ss = cnt.substring(0, si-1);
            String es = cnt.substring(ei + 1);
            c.setContent(ss);
            if (!Utilities.noString(es)) {
              XhtmlNode t = new XhtmlNode(NodeType.Text);
              t.setContent(es);
              x.getChildNodes().add(start + 2, span);
            }
          } else {
            XhtmlNode sc = x.getChildNodes().get(start);
            XhtmlNode ec = x.getChildNodes().get(end);
            XhtmlNode span = new XhtmlNode(NodeType.Element, "span");
            span.setAttribute("class", "fhir-conformance");
            int si = sc.getContent().indexOf("§");
            int ei = ec.getContent().indexOf("§");
            String cnt = sc.getContent().substring(si + 1);
            if (cnt.contains(":")) {
              String token = cnt.substring(0, cnt.indexOf(":"));
              if (token.matches("^[a-zA-Z0-9._\\-\\^\\,\\?]+$")) {
                span.setAttribute("id", token);
                cnt = cnt.substring(cnt.indexOf(":")+1);
              }
            }
            span.addText(cnt);
            for (int i = start + 1; i < end; i++) {
              span.add(x.getChildNodes().get(start + 1));
              x.getChildNodes().remove(start + 1);
            }
            span.addText(ec.getContent().substring(0, ei));
            x.getChildNodes().add(start + 1, span);
            sc.setContent(sc.getContent().substring(0, si));
            ec.setContent(ec.getContent().substring(ei + 1));
          }
        }
      } while (tryAgain);
    }

    List<XhtmlNodeHolder> childDivs = new ArrayList<>();
    for (XhtmlNode c : x.getChildNodes()) {
      processMarkdownConformanceClauses(c, hasClauses, childDivs);
    }
    
    for (XhtmlNodeHolder childDiv : childDivs) {
      if (childDiv.end != null) {
        int start = x.getChildNodes().indexOf(childDiv.start);

        int end = x.getChildNodes().indexOf(childDiv.end);
        for (int i = start + 1; i < end - 1; i++) {
          childDiv.start.getChildNodes().add(x.getChildNodes().get(i));
        }
        x.getChildNodes().subList(start + 1, end).clear();
      }
    }
  }

  public void renderConfHomes() throws IOException {
    for (String lang: confHomes.keySet())
      renderConformanceHome(lang);
  }
  
  public void setDefaultLang(String defaultTranslationLang) {
    this.defaultLang = defaultTranslationLang;
  }

  public void setTranslationLangs(List<String> translationLangs) {
    this.translationLangs = translationLangs;
  }

  public void setReqFolder(String reqFolder) {
    this.reqFolder = reqFolder;
  }

  public String getPrimaryLang() {
    return langList.get(0);
  }
  
  public void expectationFound(String expectation) {
    if (!foundExpectations.contains(expectation))
      foundExpectations.add(expectation);
  }
  
  public String nextStatementId(String lang) {
    Integer count = 1;
    if (langCounts.containsKey(lang)) {
      count = langCounts.get(lang) + 1;
      langCounts.remove(lang);
    }
    langCounts.put(lang,  count);

    return count.toString();
  }
  
  public void actorUsed(ActorDefinition anActor) {
    if (!usedActors.containsKey(anActor.getUrl()))
      usedActors.put(anActor.getUrl(), anActor);    
  }
  
  public void categoryUsed(Coding category) {
    if (!usedCategories.containsKey(category.getCode()))
      usedCategories.put(category.getCode(), category);
  }

  public void extractExpectations(List<String> expectations, String text) {
    if (text.contains(termShallNot) || text.contains(termShallNotPlural)) {
      expectations.add("SHALLNOT");
      text = text.replaceAll(termShallNot, "");
      text = text.replaceAll(termShallNotPlural, "");
    }
    if (text.contains(termShall) || text.contains(termShallPlural)) {
      expectations.add(Requirements.ConformanceExpectation.SHALL.toCode());
      text = text.replaceAll(termShall, "");
      text = text.replaceAll(termShallPlural, "");
    }
    if (text.contains(termShouldNot) || text.contains(termShouldNotPlural)) {
      expectations.add(Requirements.ConformanceExpectation.SHOULDNOT.toCode());
      text = text.replaceAll(termShouldNot, "");
      text = text.replaceAll(termShouldNotPlural, "");
    }
    if (text.contains(termShould) || text.contains(termShouldPlural)) {
      expectations.add(Requirements.ConformanceExpectation.SHOULD.toCode());
      text = text.replaceAll(termShould, "");
      text = text.replaceAll(termShouldPlural, "");
    }
    if (text.contains(termMay) || text.contains(termMayPlural)) {
      expectations.add(Requirements.ConformanceExpectation.MAY.toCode());
      text = text.replaceAll(termMay, "");
      text = text.replaceAll(termMayPlural, "");
    }
  }  

  private XhtmlNode getHeading(List<XhtmlNode> parents, XhtmlNode x) {
    // walk backwards up the parents list
    for (int i = parents.size() - 1; i >= 0; i--) {
      // looking for any heading element
      XhtmlNode p = parents.get(i);
      int index = p.getChildNodes().indexOf(x);
      for (int j = index - 1; j >- 0; j--) {
        if (Utilities.existsInList(p.getChildNodes().get(j).getName(), "h1", "h2", "h3", "h4", "h5", "h6")) {
          return p.getChildNodes().get(j);
        }
      }
      x = p;
    }
    return null;
  }

}