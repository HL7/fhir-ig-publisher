package org.hl7.fhir.igtools.renderers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.RelatedIG;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.model.core.CanonicalResource;
import org.hl7.fhir.model.core.NamingSystem;
import org.hl7.fhir.model.core.NamingSystem.NamingSystemType;
import org.hl7.fhir.model.core.NamingSystem.NamingSystemUniqueIdComponent;
import org.hl7.fhir.model.core.ValueSet;
import org.hl7.fhir.model.core.ValueSet.ConceptSetComponent;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.services.renderers.utils.RenderingContext;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.NpmPackage;

/**
 * IG publisher specific rendering for NamingSystem pages - the narrative itself comes from the core NamingSystemRenderer
 */
public class NamingSystemRenderer extends CanonicalRenderer {

  private NamingSystem ns;

  public NamingSystemRenderer(IWorkerContext context, String corePath, NamingSystem ns, IGKnowledgeProvider igp, List<SpecMapManager> maps, Set<String> allTargets, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen, String versionToAnnotate, List<RelatedIG> relatedIgs, ReferenceResolver resolver) {
    super(context, corePath, ns, null, igp, maps, allTargets, markdownEngine, packge, gen, versionToAnnotate, relatedIgs, resolver);
    this.ns = ns;
  }

  /**
   * The value sets in this IG (and related IGs) whose content logical definition uses the code system this
   * NamingSystem identifies - the same list as the CodeSystem xref fragment (#1187).
   *
   * The fragment is always generated so templates can include it unconditionally: it's empty unless
   * kind = codesystem
   */
  public String xref() {
    if (ns.getKind() != NamingSystemType.CODESYSTEM) {
      return "";
    }
    Set<String> systems = systemsForCodeSystem();
    if (systems.isEmpty()) {
      return "";
    }
    StringBuilder b = new StringBuilder();
    b.append("\r\n");
    Set<String> processed = new HashSet<>();
    boolean first = true;
    for (CanonicalResource cr : scanAllLocalResources(ValueSet.class, "ValueSet")) {
      ValueSet vs = (ValueSet) cr;
      if (usesSystem(vs.getCompose().getIncludeList(), systems) || usesSystem(vs.getCompose().getExcludeList(), systems)) {
        String path = vs.getWebPath();
        if (path != null && !processed.contains(path)) {
          processed.add(path);
          if (first) {
            first = false;
            b.append("<ul>\r\n");
          }
          b.append(" <li><a href=\"" + path + "\">" + Utilities.escapeXml(gen.getTranslated(vs.getTitleElement(), vs.getNameElement())) + "</a></li>\r\n");
        }
      }
    }
    if (first) {
      b.append("<ul><li>" + (gen.formatPhrase(RenderingContext.CODE_SYS_CODE_NOT_HERE)) + "</li></ul>\r\n");
    } else {
      b.append("</ul>\r\n");
    }
    return b.toString();
  }

  /**
   * the forms in which the unique ids can appear in ValueSet.compose.include.system. Only uri, oid and uuid
   * ids can; v2csmnemonic, iri-stem and other ids can't
   */
  private Set<String> systemsForCodeSystem() {
    Set<String> systems = new HashSet<>();
    for (NamingSystemUniqueIdComponent id : ns.getUniqueIdList()) {
      if (!id.hasValue() || !id.hasType()) {
        continue;
      }
      String v = id.getValue();
      switch (id.getType()) {
      case URI:
        systems.add(v);
        break;
      case OID:
        systems.add(v.startsWith("urn:oid:") ? v : "urn:oid:" + v);
        break;
      case UUID:
        systems.add(v.startsWith("urn:uuid:") ? v : "urn:uuid:" + v);
        break;
      default:
        break;
      }
    }
    return systems;
  }

  private boolean usesSystem(List<ConceptSetComponent> list, Set<String> systems) {
    for (ConceptSetComponent inc : list) {
      if (inc.hasSystem() && systems.contains(inc.getSystem())) {
        return true;
      }
    }
    return false;
  }

}
