package org.hl7.fhir.igtools.renderers;

import java.util.List;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.RelatedIG;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.OperationDefinition;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.npm.NpmPackage;

public class OperationDefinitionRenderer extends CanonicalRenderer {
  public static final String RIM_MAPPING = "http://hl7.org/v3";
  public static final String v2_MAPPING = "http://hl7.org/v2";
  public static final String LOINC_MAPPING = "http://loinc.org";
  public static final String SNOMED_MAPPING = "http://snomed.info";

  ProfileUtilities utils;
  private OperationDefinition od;
  private String destDir;
  private List<FetchedFile> files;

  public OperationDefinitionRenderer(IWorkerContext context, String corePath, OperationDefinition od, String destDir, IGKnowledgeProvider igp, List<SpecMapManager> maps, Set<String> allTargets, MarkDownProcessor markdownEngine, NpmPackage packge, List<FetchedFile> files, RenderingContext gen, String versionToAnnotate, List<RelatedIG> relatedIgs) {
    super(context, corePath, od, destDir, igp, maps, allTargets, markdownEngine, packge, gen, versionToAnnotate, relatedIgs);
    this.od = od;
    this.destDir = destDir;
    utils = new ProfileUtilities(context, null, igp);
    this.files = files;
  }

  @Override
  protected void genSummaryRowsSpecific(StringBuilder b, Set<String> rows) {
  }

  public String summary() {
    return "<p><i>Not Done Yet</i></p>";
  }

  public String idempotence() {
    if (!od.hasAffectsStateElement())
      return "";
    if (!od.getAffectsState())
      return "<p>This operation may or does change the state of resources on the server (not idempotent)</p>";  
    else  
      return "<p>This operation does not change the state of resources on the server (idempotent)</p>";  
  }

}
