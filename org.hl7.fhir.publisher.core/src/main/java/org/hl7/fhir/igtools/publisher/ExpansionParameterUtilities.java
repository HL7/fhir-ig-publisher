package org.hl7.fhir.igtools.publisher;

import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.Enumerations.CodeSystemContentMode;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.Parameters;
import org.hl7.fhir.r5.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionContainsComponent;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionParameterComponent;
import org.hl7.fhir.r5.terminologies.expansion.ValueSetExpansionOutcome;
import org.hl7.fhir.r5.terminologies.utilities.ValidationResult;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.utilities.validation.ValidationOptions;

public class ExpansionParameterUtilities {

  private IWorkerContext context;
  
  public ExpansionParameterUtilities(IWorkerContext context) {
    super();
    this.context = context;
  }

  public Parameters reviewVersions(Parameters params) {
    for (ParametersParameterComponent pp : params.getParameter()) {
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
      ValueSet vs = context.findTxResource(ValueSet.class, url);
      return vs == null ? null : vs.getVersionedUrl();
    } else if ("system-version".equals(name)) {      
      CodeSystem cs = context.findTxResource(CodeSystem.class, url);
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
        for (ValueSetExpansionParameterComponent pp : exp.getValueset().getExpansion().getParameter()) {
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
