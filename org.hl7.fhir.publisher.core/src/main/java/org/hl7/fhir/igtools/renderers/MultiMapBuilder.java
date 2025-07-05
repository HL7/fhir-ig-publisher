package org.hl7.fhir.igtools.renderers;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.exceptions.DefinitionException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.ConceptMap.TargetElementComponent;
import org.hl7.fhir.r5.model.DataType;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionContainsComponent;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities;
import org.hl7.fhir.r5.terminologies.ConceptMapUtilities;
import org.hl7.fhir.r5.terminologies.ConceptMapUtilities.MappingTriple;
import org.hl7.fhir.r5.terminologies.expansion.ValueSetExpansionOutcome;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;


public class MultiMapBuilder extends DataRenderer {


  private CodeSystem csRel;

  public MultiMapBuilder(RenderingContext context) {
    super(context);
  }

  public class SourceDataProvider {
    private CodeSystem cs;
    private ValueSet vs;
    private List<Coding> codings = new ArrayList<>();
    
    public void heading(XhtmlNode b) {
      if (cs != null) {
        b.ah(cs.getWebPath()).tx(cs.present());
      } else {
        b.ah(vs.getWebPath()).tx(vs.present());
      }      
    }
    
    public List<Coding> getCodings() {
      return codings;
    }
    
    public void populate() {
      if (cs != null) {
        makeCodings(cs.getConcept());
      } else {
        ValueSetExpansionOutcome vse = context.getContext().expandVS(vs, true, false);
        if (!vse.isOk()) {
          throw new FHIRException(vse.getError());
        }
        for (ValueSetExpansionContainsComponent ce : vse.getValueset().getExpansion().getContains()) {
          codings.add(new Coding().setSystem(ce.getSystem()).setVersion(ce.getVersion()).setCode(ce.getCode()));
        }
      }
    }
    private void makeCodings(List<ConceptDefinitionComponent> list) {
      for (ConceptDefinitionComponent cd : list) {
        codings.add(new Coding().setSystem(cs.getUrl()).setVersion(cs.getVersion()).setCode(cd.getCode()));
        if (cd.hasConcept()) {
          makeCodings(cd.getConcept());
        }
      }
    }
    
  }

  public abstract class MappingDataProvider {
    protected JsonObject config;
    public MappingDataProvider(JsonObject config) {
      super();
      this.config = config;
    }
    protected abstract void heading(XhtmlNode b);
    protected abstract void cell(XhtmlNode td, Coding c, RenderingStatus status) throws Exception ;
  }

  public class ConceptMapMappingProvider extends MappingDataProvider {

    private ConceptMap map;

    public ConceptMapMappingProvider(JsonObject config, ConceptMap map) {
      super(config);
      this.map = map;
    }

    @Override
    protected void heading(XhtmlNode b) {
      b.ah(map.getWebPath()).tx(map.present());
    }

    @Override
    protected void cell(XhtmlNode td, Coding c, RenderingStatus status) throws Exception {
      List<MappingTriple> tgts = ConceptMapUtilities.getBySource(map, c);
      if (tgts.size() > 0) {
        if (tgts.size() == 1) {
          cellTgt(td, tgts.get(0), status);
        } else {
          XhtmlNode ul = td.ul();
          for (MappingTriple tgt : tgts) {
            cellTgt(ul.li(), tgt, status);
          }
        }
      } else {
        tgts = ConceptMapUtilities.getByTarget(map, c);
        if (tgts.size() > 0) {
          if (tgts.size() == 1) {
            cellSrc(td, tgts.get(0), status);
          } else {
            XhtmlNode ul = td.ul();
            for (MappingTriple tgt : tgts) {
              cellSrc(ul.li(), tgt, status);
            }
          }
        }
      }
    }

    private void cellTgt(XhtmlNode x, MappingTriple trip, RenderingStatus status) throws FHIRFormatError, DefinitionException, IOException {
      if (trip.getTgt() == null) {
        x.tx("--");
      } else {
        TargetElementComponent ccm = trip.getTgt();
        if (ccm.hasExtension(ToolingExtensions.EXT_OLD_CONCEPTMAP_EQUIVALENCE)) {
          String code = ToolingExtensions.readStringExtension(ccm, ToolingExtensions.EXT_OLD_CONCEPTMAP_EQUIVALENCE);
          x.ah(context.prefixLocalHref(csRel.getWebPath()+"#"+code), code).tx(presentEquivalenceCode(code));                
        } else {
          x.ah(context.prefixLocalHref(csRel.getWebPath()+"#"+ccm.getRelationship().toCode()), ccm.getRelationship().toCode()).tx(presentRelationshipCode(ccm.getRelationship().toCode()));
        }
        CanonicalType ct = trip.getGrp().getTargetElement();
        Coding code = new Coding().setSystem(ct.baseUrl()).setVersion(ct.version()).setCode(trip.getTgt().getCode());
        renderDataType(status, x, ResourceWrapper.forType(context.getContextUtilities(), code));
        if (ccm.hasComment()) {
          x.title(ccm.getComment());
        }
      }
      
      // equivalence
      // code 
      // comment
      // we don't do products or depends on? 
      
    }

    private void cellSrc(XhtmlNode x, MappingTriple trip, RenderingStatus status) throws FHIRFormatError, DefinitionException, IOException {

      TargetElementComponent ccm = trip.getTgt();
      if (ccm.hasExtension(ToolingExtensions.EXT_OLD_CONCEPTMAP_EQUIVALENCE)) {
        String code = ToolingExtensions.readStringExtension(ccm, ToolingExtensions.EXT_OLD_CONCEPTMAP_EQUIVALENCE);
        x.ah(context.prefixLocalHref(csRel.getWebPath()+"#"+code), code).tx(presentEquivalenceCodeInReverse(code));                
      } else {
        x.ah(context.prefixLocalHref(csRel.getWebPath()+"#"+ccm.getRelationship().toCode()), ccm.getRelationship().toCode()).tx(presentRelationshipCodeInReverse(ccm.getRelationship().toCode()));
      }
      CanonicalType ct = trip.getGrp().getSourceElement();
      Coding code = new Coding().setSystem(ct.baseUrl()).setVersion(ct.version()).setCode(trip.getSrc().getCode());
      renderDataType(status, x, ResourceWrapper.forType(context.getContextUtilities(), code));
      if (ccm.hasComment()) {
        x.title(ccm.getComment());
      }
    }

  }

    private String presentRelationshipCode(String code) {
      if ("related-to".equals(code)) {
        return "~";
      } else if ("equivalent".equals(code)) {
        return "=";
      } else if ("source-is-narrower-than-target".equals(code)) {
        return "<";
      } else if ("source-is-broader-than-target".equals(code)) {
        return ">";
      } else if ("not-related-to".equals(code)) {
        return "!";
      } else {
        return code;
      }
    }

    private String presentRelationshipCodeInReverse(String code) {
      if ("related-to".equals(code)) {
        return "~";
      } else if ("equivalent".equals(code)) {
        return "=";
      } else if ("source-is-narrower-than-target".equals(code)) {
        return ">";
      } else if ("source-is-broader-than-target".equals(code)) {
        return "<";
      } else if ("not-related-to".equals(code)) {
        return "!";
      } else {
        return code;
      }
    }

  private String presentEquivalenceCodeInReverse(String code) {
    if ("relatedto".equals(code)) {
      return "~";
    } else if ("equivalent".equals(code)) {
      return "=";
    } else if ("equal".equals(code)) {
      return "==";
    } else if ("wider".equals(code)) {
      return ">";
    } else if ("subsumes".equals(code)) {
      return ">>";
    } else if ("source-is-broader-than-target".equals(code)) {
      return "<";
    } else if ("specializes".equals(code)) {
      return "<<";
    } else if ("inexact".equals(code)) {
      return "~~";
    } else if ("unmatched".equals(code)) {
      return "!";
    } else if ("disjoint".equals(code)) {
      return "!=";
    } else {
      return code;
    }
  }

  private String presentEquivalenceCode(String code) {
    if ("relatedto".equals(code)) {
      return "~";
    } else if ("equivalent".equals(code)) {
      return "=";
    } else if ("equal".equals(code)) {
      return "==";
    } else if ("wider".equals(code)) {
      return "<";
    } else if ("subsumes".equals(code)) {
      return "<<";
    } else if ("source-is-broader-than-target".equals(code)) {
      return ">";
    } else if ("specializes".equals(code)) {
      return ">>";
    } else if ("inexact".equals(code)) {
      return "~~";
    } else if ("unmatched".equals(code)) {
      return "!";
    } else if ("disjoint".equals(code)) {
      return "!=";
    } else {
      return code;
    }
  }
  
  public class CodeSystemMappingProvider extends MappingDataProvider {

    private CodeSystem cs;
    private String prop;

    public CodeSystemMappingProvider(JsonObject config, CodeSystem cs, String prop) {
      super(config);
      this.cs = cs;
      this.prop = prop;
    }

    @Override
    protected void heading(XhtmlNode b) {
      b.ah(cs.getWebPath()).tx(cs.present());
    }

    @Override
    protected void cell(XhtmlNode td, Coding c, RenderingStatus status) throws FHIRFormatError, DefinitionException, IOException {
      List<ConceptDefinitionComponent> list = new ArrayList<CodeSystem.ConceptDefinitionComponent>();
      findMatchingConcepts(list, c, cs.getConcept());
      if (list.size() > 0) {
        if (list.size() == 1) {
          cellTgt(td, list.get(0), status);
        } else {
          XhtmlNode ul = td.ul();
          for (ConceptDefinitionComponent tgt : list) {
            cellTgt(ul.li(), tgt, status);
          }
        }
      }
    }

    private void cellTgt(XhtmlNode x, ConceptDefinitionComponent cd, RenderingStatus status) throws FHIRFormatError, DefinitionException, IOException {
      Coding code = new Coding().setSystem(cs.getUrl()).setVersion(cs.getVersion()).setCode(cd.getCode()).setDisplay(cd.getDisplay());
      renderDataType(status, x, ResourceWrapper.forType(context.getContextUtilities(), code));
    }

    private void findMatchingConcepts(List<ConceptDefinitionComponent> list, Coding c, List<ConceptDefinitionComponent> concepts) {
      for (ConceptDefinitionComponent cd : concepts) {
        if (matchingConcept(c, cd)) {
          list.add(cd);
        }
        if (cd.hasConcept()) {
          findMatchingConcepts(list, c, cd.getConcept());
        }
      }
    }

    private boolean matchingConcept(Coding c, ConceptDefinitionComponent cd) {
      DataType p = CodeSystemUtilities.getProperty(cs, cd, prop);
      if (p instanceof Coding) {
        Coding cp = (Coding) p;
        if (cp.matches(c)) {
          return true;
        }
      }
      return false;
    }

  }
  

  public String buildMap(JsonObject config) {
    try {
      // first, resolve the left, source column
      SourceDataProvider source = loadSource(config);

      // then make a list of all the mapping sources in mapping providers
      List<MappingDataProvider> maps = new ArrayList<>();     
      for (JsonObject o : config.forceArray("columns").asJsonObjects()) {
        String t = o.asString("type");
        if ("ConceptMap".equals(t)) {
          ConceptMap map = context.getContext().fetchResource(ConceptMap.class, o.asString("url"));
          if (map == null) {
            throw new FHIRException("Unable to find conceptmap "+o.asString("url"));
          } else {
            maps.add(new ConceptMapMappingProvider(o, map));
          }
        } else if ("CodeSystem".equals(t)) {
          CodeSystem cs = context.getContext().fetchResource(CodeSystem.class, o.asString("url"));
          if (cs == null) {
            throw new FHIRException("Unable to find CodeSystem "+o.asString("url"));
          } else {
            maps.add(new CodeSystemMappingProvider(o, cs, o.asString("property")));
          }
        }
      }
      if (config.asBoolean("scan")) {
        scanAllMaps(maps, source);
      }

      csRel = getContext().getWorker().fetchCodeSystem("http://hl7.org/fhir/concept-map-relationship");
      if (csRel == null)
        csRel = getContext().getWorker().fetchCodeSystem("http://hl7.org/fhir/concept-map-equivalence");
      RenderingStatus status = new RenderingStatus();
        
      XhtmlNode node = new XhtmlNode(NodeType.Element, "div");
      if (config.has("title")) {
        node.para().b().tx(config.asString("title"));
      }
      XhtmlNode tbl = node.table("grid");
      XhtmlNode tr = tbl.tr();
      source.heading(tr.th().b());
      for (MappingDataProvider map : maps) {
        map.heading(tr.th().b());
      }
      for (Coding c : source.getCodings()) {
        tr = tbl.tr();
        renderDataType(status, tr.td(), ResourceWrapper.forType(context.getContextUtilities(), c));

        for (MappingDataProvider map : maps) {
          map.cell(tr.td(), c, status);
        } 
      }
      return new XhtmlComposer(false, true).compose(node.getChildNodes());
      
    } catch (Exception e) {
      return "<p style=\"font-weight: bold; color: maroon\">"+Utilities.escapeXml(e.getMessage())+"</p>";
    }
  }
  
  private SourceDataProvider loadSource(JsonObject config) {
    SourceDataProvider source = new SourceDataProvider();
    Resource res = context.getContext().fetchResource(Resource.class, config.asString("source"));
    if (res == null) {
      throw new FHIRException("Source not found: "+config.asString("source"));
    }
    if (res instanceof CodeSystem) {
      source.cs = (CodeSystem) res;
    } else if (res instanceof ValueSet) {
      source.vs = (ValueSet) res;
    } else if (res instanceof ConceptMap) {
      ConceptMap cm = (ConceptMap) res;
      if (cm.hasSourceScope()) {
        res = context.getContext().fetchResource(ValueSet.class, cm.getSourceScope().primitiveValue());
        if (res == null) {
          throw new FHIRException("Source ConceptMap source valueset not found: "+cm.getSourceScope().primitiveValue());
        } else {
          source.vs = (ValueSet) res; 
        }
      } else if (cm.getGroup().size() == 0) {
        throw new FHIRException("Source ConceptMap has no groups: "+cm.getSourceScope().primitiveValue());          
      } else if (cm.getGroup().size() > 1) {
        throw new FHIRException("Source ConceptMap has multiple groups");          
      } else if (!cm.getGroupFirstRep().hasSource()) {
        throw new FHIRException("Source ConceptMap group has no source");          
      } else {
        res = context.getContext().fetchResource(CodeSystem.class, cm.getGroupFirstRep().getSource());
        if (res == null) {
          throw new FHIRException("Source ConceptMap group source cannot be found: "); 
        } else {
          source.cs = (CodeSystem) res;
        }
      }
    }
    source.populate();
    return source;
  }


  private void scanAllMaps(List<MappingDataProvider> maps, SourceDataProvider source) {
    for (ConceptMap map : context.getContext().fetchResourcesByType(ConceptMap.class)) {
      if (isReleventMap(map, source) && !hasMap(maps, map)) {
        maps.add(new ConceptMapMappingProvider(null, map));
      }
    }
  }

  private boolean hasMap(List<MappingDataProvider> maps, ConceptMap map) {
    for (MappingDataProvider m : maps) {
      if (m instanceof ConceptMapMappingProvider && ((ConceptMapMappingProvider) m).map == map) {
        return true;
      }
    }
    return false;
  }

  private boolean isReleventMap(ConceptMap map, SourceDataProvider source) {
    for (Coding c : source.getCodings()) {
      if (ConceptMapUtilities.hasMappingForSource(map, c)) {
        return true;
      }
      if (ConceptMapUtilities.hasMappingForTarget(map, c)) {
        return true;
      }
    }
    return false;
  }


}
