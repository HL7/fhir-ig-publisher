package org.hl7.fhir.igtools.publisher.xig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.igtools.publisher.xig.XIGInformation.UsageType;
import org.hl7.fhir.model.core.*;
import org.hl7.fhir.model.core.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.model.core.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.model.core.CapabilityStatement.CapabilityStatementRestResourceOperationComponent;
import org.hl7.fhir.model.core.CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent;
import org.hl7.fhir.model.core.Enumerations.CapabilityStatementKind;
import org.hl7.fhir.utilities.json.model.JsonObject;

public class XIGCapabilityStatementHandler extends XIGHandler {

  private XIGInformation info;

  public XIGCapabilityStatementHandler(XIGInformation info) {
    super();
    this.info = info;
  }

  public void fillOutJson(CapabilityStatement cs, JsonObject j) {
    if (cs.hasKind()) {    
      j.add("kind", cs.getKind().toCode()); 
    }
    if (cs.hasFhirVersion()) {         
      j.add("fhirVersion", cs.getFhirVersion().toCode()); 
    }
    
    for (CanonicalType g : cs.getInstantiatesList()) {
      if (g.hasValue()) {    
        j.forceArray("instantiates").add(g.primitiveValue()); 
      }
    }
    for (CanonicalType g : cs.getImportsList()) {
      if (g.hasValue()) {    
        j.forceArray("imports").add(g.primitiveValue()); 
      }
    }
    for (CodeType g : cs.getFormatList()) {
      if (g.hasValue()) {    
        j.forceArray("formats").add(g.primitiveValue()); 
      }
    }
    for (Enumeration<CapabilityStatement.PatchMimeTypes> g : cs.getPatchFormatList()) {
      if (g.hasValue()) {    
        j.forceArray("formats").add(g.primitiveValue()); 
      }
    }
    for (CodeType g : cs.getAcceptLanguageList()) {
      if (g.hasValue()) {    
        j.forceArray("languages").add(g.primitiveValue()); 
      }
    }
    
    for (CanonicalType g : cs.getImplementationGuideList()) {
      if (g.hasValue()) {    
        j.forceArray("implementationGuides").add(g.primitiveValue()); 
      }
    }
    
  }
  
  
  public PageContent makeCapabilityStatementPage(CapabilityStatementKind kind, String title, String realm) {
    List<CapabilityStatement> list = new ArrayList<>();
    for (CanonicalResource cr : info.getResources().values()) {
      if (meetsRealm(cr, realm)) {
        if (cr instanceof CapabilityStatement) {
          CapabilityStatement cs = (CapabilityStatement) cr;
          boolean ok = cs.getKind() == kind;
          if (ok) {
            list.add(cs);
          }
        }
      }
    }
    if (list.isEmpty() && kind != null) {
      return null;
    }

    Collections.sort(list, new CanonicalResourceSorter());
    StringBuilder b = new StringBuilder();

    b.append("<table class=\"\">\r\n");
    crTrHeaders(b, false);
    DuplicateTracker dt = new DuplicateTracker();
    for (CapabilityStatement cs : list) {
      crTr(b, dt, cs, 0);      
    }
    b.append("</table>\r\n");

    return new PageContent(title+" ("+list.size()+")", b.toString());
  }

  public static void buildUsages(XIGInformation info, CapabilityStatement cs) {
    for (CanonicalType ct : cs.getImportsList()) {
      info.recordUsage(cs, ct.getValue(), UsageType.CS_IMPORTS);
    }
    for (CanonicalType ct : cs.getInstantiatesList()) {
      info.recordUsage(cs, ct.getValue(), UsageType.CS_IMPORTS);
    }
    for (CanonicalType ct : cs.getImplementationGuideList()) {
      info.recordUsage(cs, ct.getValue(), UsageType.CS_IMPORTS);
    }
    for (CapabilityStatementRestComponent tr1 : cs.getRestList()) {
      for (CapabilityStatementRestResourceSearchParamComponent t : tr1.getSearchParamList()) {
        info.recordUsage(cs, t.getDefinition(), UsageType.CS_IMPORTS);
      }
      for (CapabilityStatementRestResourceOperationComponent t : tr1.getOperationList()) {
        info.recordUsage(cs, t.getDefinition(), UsageType.CS_IMPORTS);
      }
      for (CapabilityStatementRestResourceComponent tr : tr1.getResourceList()) {
        info.recordUsage(cs, tr.getProfile(), UsageType.CS_PROFILE);
        for (CanonicalType t : tr.getSupportedProfileList()) {
          info.recordUsage(cs, t.getValue(), UsageType.CS_PROFILE);
        }
        for (CapabilityStatementRestResourceSearchParamComponent t : tr.getSearchParamList()) {
          info.recordUsage(cs, t.getDefinition(), UsageType.CS_IMPORTS);
        }
        for (CapabilityStatementRestResourceOperationComponent t : tr.getOperationList()) {
          info.recordUsage(cs, t.getDefinition(), UsageType.CS_IMPORTS);
        }
      }
    }
  }
}
