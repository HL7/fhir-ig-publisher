package org.hl7.fhir.igtools.publisher;

import org.hl7.fhir.model.core.*;
import org.hl7.fhir.model.core.Enumerations.CodeSystemContentMode;
import org.hl7.fhir.model.core.Enumerations.PublicationStatus;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.model.core.Parameters.ParametersParameterComponent;
import org.hl7.fhir.model.core.ValueSet.ValueSetExpansionParameterComponent;
import org.hl7.fhir.services.terminology.ValueSetExpansionOutcome;

public class ExpansionParameterUtilities {

  private IWorkerContext context;
  
  public ExpansionParameterUtilities(IWorkerContext context) {
    super();
    this.context = context;
  }

  public Parameters reviewVersions(Parameters params) {
    for (ParametersParameterComponent pp : params.getParameterList()) {
      String revised = checkParameter(pp.getName(), pp.getValue().primitiveValue());
      if (revised != null) {
        pp.setValue(new CanonicalType(revised));
      }
    }
    return params;
  }

  private String checkParameter(String name, String value) {
    if (value == null ||  !value.endsWith("|?")) {
      return null;
    }
    String url = value.substring(0, value.indexOf("|"));
    if ("default-valueset-version".equals(name)) {
      ValueSet vs = context.findTxResource(ValueSet.class, url, VersionResolutionRules.defaultRule());
      return vs == null ? null : vs.getVersionedUrl();
    } else if ("system-version".equals(name)) {      
      CodeSystem cs = context.findTxResource(CodeSystem.class, url, VersionResolutionRules.defaultRule());
      if (cs != null && !(cs.getContent() == CodeSystemContentMode.NOTPRESENT && cs.hasSourcePackage() && cs.getSourcePackage().isTHO())) {
        return cs.getVersionedUrl();
      }
      // there's one other way to find out what the version is
      ValueSet vs = new ValueSet();
      vs.setStatus(PublicationStatus.DRAFT);
      vs.setId("vs-"+url);
      vs.setUrl("http://fhir.org/ValueSet/"+vs.getId());
      vs.setVersion("0.0.1");
      vs.setDescription("just finding out what the default version is for "+url);
      vs.getCompose().addInclude().setSystem(url).addConcept().setCode("--this-is-intended-to-be-an-invalid-code--");
      ValueSetExpansionOutcome exp = context.expandVS(vs, false, false);
      if (exp != null && exp.getValueset() != null) {
        for (ValueSetExpansionParameterComponent pp : exp.getValueset().getExpansion().getParameterList()) {
          if ("used-codesystem".equals(pp.getName())) {
            return pp.getValue().primitiveValue();
          }
        }
      }

      return null;      
    } else {
      return null; // we don't process this kind of parameter, whatever it is
    }
  }

  public void reviewVersions(Element p) {

    for (Element t : p.getChildren("parameter")) {
      String name = t.getNamedChildValue("name");
      String value = t.getNamedChildValue("value");        
      String revised = checkParameter(name, value);
      if (revised != null) {
        Element v = t.getNamedChild("value");
        v.setValue(revised);
      }

    }
  }

}
