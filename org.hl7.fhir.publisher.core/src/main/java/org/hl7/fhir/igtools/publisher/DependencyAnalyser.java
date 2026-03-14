package org.hl7.fhir.igtools.publisher;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.model.ActivityDefinition;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.CapabilityStatement;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceOperationComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CompartmentDefinition;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.ConceptMap.ConceptMapGroupComponent;
import org.hl7.fhir.r5.model.DeviceDefinition;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.EventDefinition;
import org.hl7.fhir.r5.model.ExampleScenario;
import org.hl7.fhir.r5.model.GraphDefinition;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideGlobalComponent;
import org.hl7.fhir.r5.model.MessageDefinition;
import org.hl7.fhir.r5.model.NamingSystem;
import org.hl7.fhir.r5.model.ObservationDefinition;
import org.hl7.fhir.r5.model.OperationDefinition;
import org.hl7.fhir.r5.model.OperationDefinition.OperationDefinitionParameterComponent;
import org.hl7.fhir.r5.model.PlanDefinition;
import org.hl7.fhir.r5.model.Questionnaire;
import org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.SearchParameter;
import org.hl7.fhir.r5.model.SearchParameter.SearchParameterComponentComponent;
import org.hl7.fhir.r5.model.SpecimenDefinition;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.r5.model.StructureMap.StructureMapStructureComponent;
import org.hl7.fhir.r5.model.TerminologyCapabilities;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionContainsComponent;

public class DependencyAnalyser {

  public static class ArtifactDependency {
    private Resource source;
    private String kind;
    private Resource target;
    public ArtifactDependency(Resource source, String kind, Resource target) {
      super();
      this.source = source;
      this.kind = kind;
      this.target = target;
    }
    public Resource getSource() {
      return source;
    }
    public String getKind() {
      return kind;
    }
    public Resource getTarget() {
      return target;
    }
  }

  private IWorkerContext context;
  private List<DependencyAnalyser.ArtifactDependency> list = new ArrayList<>();

  public DependencyAnalyser(IWorkerContext context) {
    super();
    this.context = context;
  }

  public List<ArtifactDependency> getList() {
    return list;
  }

  public void analyse(Resource resource) {
    if (resource instanceof CodeSystem) {
      analyseCS((CodeSystem) resource);
    } else if (resource instanceof ValueSet) {
      analyseVS((ValueSet) resource);
    } else if (resource instanceof ConceptMap) {
      analyseCM((ConceptMap) resource);
    } else if (resource instanceof CapabilityStatement) {
      analyseCPS((CapabilityStatement) resource);
    } else if (resource instanceof StructureDefinition) {
      analyseSD((StructureDefinition) resource);
    } else if (resource instanceof ImplementationGuide) {
      analyseIG((ImplementationGuide) resource);
    } else if (resource instanceof SearchParameter) {
      analyseSP((SearchParameter) resource);
    } else if (resource instanceof MessageDefinition) {
      analyseMD((MessageDefinition) resource);
    } else if (resource instanceof OperationDefinition) {
      analyseOPD((OperationDefinition) resource);
    } else if (resource instanceof CompartmentDefinition) {
      analyseCD((CompartmentDefinition) resource);
    } else if (resource instanceof StructureMap) {
      analyseSM((StructureMap) resource);
    } else if (resource instanceof GraphDefinition) {
      analyseGD((GraphDefinition) resource);
    } else if (resource instanceof ExampleScenario) {
      analyseES((ExampleScenario) resource);
    } else if (resource instanceof NamingSystem) {
      analyseNS((NamingSystem) resource);
    } else if (resource instanceof TerminologyCapabilities) {
      analyseTC((TerminologyCapabilities) resource);
    } else if (resource instanceof ActivityDefinition) {
      analyseAD((ActivityDefinition) resource);
    } else if (resource instanceof DeviceDefinition) {
      analyseDD((DeviceDefinition) resource);
    } else if (resource instanceof EventDefinition) {
      analyseED((EventDefinition) resource);
    } else if (resource instanceof ObservationDefinition) {
      analyseOD((ObservationDefinition) resource);
    } else if (resource instanceof PlanDefinition) {
      analysePD((PlanDefinition) resource);
    } else if (resource instanceof Questionnaire) {
      analyseQ((Questionnaire) resource);
    } else if (resource instanceof SpecimenDefinition) {
      analyseSpD((SpecimenDefinition) resource);
    }
  }


  private void analyseCS(CodeSystem cs) {
    if (cs.hasSupplements()) {
      dep(cs, "supplements", context.fetchResource(CodeSystem.class, cs.getSupplements(), ExtensionUtilities.getVersionResolutionRules(cs.getSupplementsElement())));
    }
  }

  private void analyseVS(ValueSet vs) {
    for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
      if (inc.hasSystem()) {
        dep(vs, "includes from", context.fetchResource(CodeSystem.class, inc.getSystem(), ExtensionUtilities.getVersionResolutionRules(inc)));
      }
      for (CanonicalType imp : inc.getValueSet()) {
        dep(vs, "imports", context.fetchResource(ValueSet.class, imp.asStringValue(), ExtensionUtilities.getVersionResolutionRules(imp)));
      }
    }
    for (ConceptSetComponent exc : vs.getCompose().getExclude()) {
      if (exc.hasSystem()) {
        dep(vs, "excludes from", context.fetchResource(CodeSystem.class, exc.getSystem(), ExtensionUtilities.getVersionResolutionRules(exc)));
      }
      for (CanonicalType imp : exc.getValueSet()) {
        dep(vs, "unimports", context.fetchResource(ValueSet.class, imp.asStringValue(), ExtensionUtilities.getVersionResolutionRules(imp)));
      }
    }
    for (ValueSetExpansionContainsComponent cc : vs.getExpansion().getContains()) {
      if (cc.hasSystem()) {
        dep(vs, "contains", context.fetchResource(CodeSystem.class, cc.getSystem(), ExtensionUtilities.getVersionResolutionRules(cc.getSystemElement())));
      }
    }
  }

  private void analyseCM(ConceptMap cm) {
    if (cm.hasSourceScope()) {
      dep(cm, "has scope", context.fetchResource(ValueSet.class, cm.getSourceScope().primitiveValue(), ExtensionUtilities.getVersionResolutionRules(cm.getSourceScope())));
    }
    if (cm.hasTargetScope()) {
      dep(cm, "has target", context.fetchResource(ValueSet.class, cm.getTargetScope().primitiveValue(), ExtensionUtilities.getVersionResolutionRules(cm.getTargetScope())));
    }
    for (ConceptMapGroupComponent g : cm.getGroup()) {
      if (g.hasSource()) {
        dep(cm, "has scope", context.fetchResource(CodeSystem.class, g.getSource(), ExtensionUtilities.getVersionResolutionRules(g.getSourceElement())));
      }
      if (g.hasTarget()) {
        dep(cm, "has target", context.fetchResource(CodeSystem.class, g.getTarget(), ExtensionUtilities.getVersionResolutionRules(g.getTargetElement())));
      }
    }
  }

  private void analyseCPS(CapabilityStatement cs) {
    for (CanonicalType imp : cs.getInstantiates()) {
      dep(cs, "instantiates", context.fetchResource(CapabilityStatement.class, imp.getValue(), ExtensionUtilities.getVersionResolutionRules(imp)));
    }
    for (CanonicalType imp : cs.getImports()) {
      dep(cs, "imports", context.fetchResource(CapabilityStatement.class, imp.getValue(), ExtensionUtilities.getVersionResolutionRules(imp)));
    }
    for (CapabilityStatementRestComponent rest : cs.getRest()) {
      for (CapabilityStatementRestResourceComponent r : rest.getResource()) {
        dep(cs, "uses profile", context.fetchResource(StructureDefinition.class, r.getProfile(), ExtensionUtilities.getVersionResolutionRules(r.getProfileElement())));
        for (CanonicalType sp : r.getSupportedProfile()) {          
          dep(cs, "uses profile", context.fetchResource(StructureDefinition.class, sp.asStringValue(), ExtensionUtilities.getVersionResolutionRules(sp)));
        }
        for (CapabilityStatementRestResourceSearchParamComponent cp : r.getSearchParam()) {
          dep(cs, "uses", context.fetchResource(SearchParameter.class, cp.getDefinition(), ExtensionUtilities.getVersionResolutionRules(cp.getDefinitionElement())));
        }
        for (CapabilityStatementRestResourceOperationComponent cp : r.getOperation()) {
          dep(cs, "uses", context.fetchResource(OperationDefinition.class, cp.getDefinition(), ExtensionUtilities.getVersionResolutionRules(cp.getDefinitionElement())));
        }
      }
      for (CapabilityStatementRestResourceSearchParamComponent cp : rest.getSearchParam()) {
        dep(cs, "uses", context.fetchResource(SearchParameter.class, cp.getDefinition(), ExtensionUtilities.getVersionResolutionRules(cp.getDefinitionElement())));
      }
      for (CapabilityStatementRestResourceOperationComponent cp : rest.getOperation()) {
        dep(cs, "uses", context.fetchResource(OperationDefinition.class, cp.getDefinition(), ExtensionUtilities.getVersionResolutionRules(cp.getDefinitionElement())));
      }
    }
  }

  private void analyseSD(StructureDefinition sd) {
    if (sd.hasBaseDefinition()) {
      dep(sd, "derives from", context.fetchResource(StructureDefinition.class, sd.getBaseDefinition(), ExtensionUtilities.getVersionResolutionRules(sd.getBaseDefinitionElement())));
    } 
    for (ElementDefinition ed : sd.getDifferential().getElement()) {
      if (ed.getBinding().hasValueSet()) {
        dep(sd, "binds to", context.fetchResource(ValueSet.class, ed.getBinding().getValueSet(), ExtensionUtilities.getVersionResolutionRules(ed.getBinding().getValueSetElement())));
      }
      for (TypeRefComponent tr : ed.getType()) {
        for (CanonicalType p : tr.getProfile()) {
          dep(sd, "uses", context.fetchResource(Resource.class, p.asStringValue(), ExtensionUtilities.getVersionResolutionRules(p)));
        }
        for (CanonicalType p : tr.getTargetProfile()) {
          dep(sd, "refers to", context.fetchResource(Resource.class, p.asStringValue(), ExtensionUtilities.getVersionResolutionRules(p)));
        }
      }
    }
  }

  private void analyseIG(ImplementationGuide ig) {
    for (ImplementationGuideGlobalComponent g : ig.getGlobal()) {
      dep(ig, "makes global", context.fetchResource(StructureDefinition.class, g.getProfile(), ExtensionUtilities.getVersionResolutionRules(g.getProfileElement())));
    } 
  }

  private void analyseSP(SearchParameter sp) {
    if (sp.hasDerivedFrom()) {
      dep(sp, "derives from", context.fetchResource(SearchParameter.class, sp.getDerivedFrom(), ExtensionUtilities.getVersionResolutionRules(sp.getDerivedFromElement())));
    } 
    for (SearchParameterComponentComponent c : sp.getComponent()) {
      dep(sp, "derives from", context.fetchResource(SearchParameter.class, c.getDefinition(), ExtensionUtilities.getVersionResolutionRules(c.getDefinitionElement())));
    }
  }

  private void analyseMD(MessageDefinition md) {
  }

  private void analyseOPD(OperationDefinition opd) {
    if (opd.hasBase()) {
      dep(opd, "derives from", context.fetchResource(OperationDefinition.class, opd.getBase(), ExtensionUtilities.getVersionResolutionRules(opd.getBaseElement())));
    } 
    if (opd.hasInputProfile()) {
      dep(opd, "uses", context.fetchResource(StructureDefinition.class, opd.getInputProfile(), ExtensionUtilities.getVersionResolutionRules(opd.getInputProfileElement())));
    } 
    if (opd.hasOutputProfile()) {
      dep(opd, "uses", context.fetchResource(StructureDefinition.class, opd.getOutputProfile(), ExtensionUtilities.getVersionResolutionRules(opd.getOutputProfileElement())));
    } 
    for (OperationDefinitionParameterComponent c : opd.getParameter()) {
      if (c.getBinding().hasValueSet()) {
        dep(opd, "binds to", context.fetchResource(ValueSet.class, c.getBinding().getValueSet(), ExtensionUtilities.getVersionResolutionRules(c.getBinding().getValueSetElement())));
      }
      for (CanonicalType ct : c.getTargetProfile()) {
        dep(opd, "refers to", context.fetchResource(StructureDefinition.class, ct.getValue(), ExtensionUtilities.getVersionResolutionRules(ct)));
      }
    }
  }

  private void analyseCD(CompartmentDefinition cd) {
 
  }

  private void analyseSM(StructureMap sm) {
    for (StructureMapStructureComponent ref : sm.getStructure()) {
      dep(sm, "refers to", context.fetchResource(StructureDefinition.class, ref.getUrl(), ExtensionUtilities.getVersionResolutionRules(ref.getUrlElement())));
    }
    for (CanonicalType ct : sm.getImport()) {
      dep(sm, "imports", context.fetchResource(StructureMap.class, ct.getValue(), ExtensionUtilities.getVersionResolutionRules(ct)));
    }
  }

  private void analyseGD(GraphDefinition gd) {
    
  }

  private void analyseES(ExampleScenario es) {
    
  }

  private void analyseNS(NamingSystem ns) {
    
  }

  private void analyseTC(TerminologyCapabilities tc) {
    
  }

  private void analyseAD(ActivityDefinition ad) {
    
  }

  private void analyseDD(DeviceDefinition dd) {
    
  }

  private void analyseOD(ObservationDefinition od) {
    
  }

  private void analysePD(PlanDefinition pd) {
    
  }

  private void analyseQ(Questionnaire q) {
    for (CanonicalType ct : q.getDerivedFrom()) {
      dep(q, "derives from", context.fetchResource(Questionnaire.class, ct.getValue(), ExtensionUtilities.getVersionResolutionRules(ct)));
    } 
    for (QuestionnaireItemComponent item : q.getItem()) {
      analyseQItem(q, item);
    }
  }

  private void analyseQItem(Questionnaire q, QuestionnaireItemComponent item) {
    if (item.hasAnswerValueSet()) {
      dep(q, "binds to", context.fetchResource(ValueSet.class, item.getAnswerValueSet(), ExtensionUtilities.getVersionResolutionRules(item.getAnswerValueSetElement())));
    }    
    for (QuestionnaireItemComponent i : item.getItem()) {
      analyseQItem(q, i);
    }
  }

  private void analyseSpD(SpecimenDefinition sd) {    
  }
  
  private void analyseED(EventDefinition ed) {
  }

  private void dep(Resource src, String uses, Resource tgt) {
    if (tgt == null) {
      return;
    }
    boolean exists = false; 
    for (ArtifactDependency dep : list) {
      if (src == dep.source && uses.equals(dep.kind) && tgt== dep.target) {
        exists = true;
        break;
      }
    }
    if (!exists) {
      list.add(new ArtifactDependency(src, uses, tgt));
    }
  }

  
}
