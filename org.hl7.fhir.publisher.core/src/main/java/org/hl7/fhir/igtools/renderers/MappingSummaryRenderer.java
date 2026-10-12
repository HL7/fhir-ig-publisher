package org.hl7.fhir.igtools.renderers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.standalone.context.CanonicalResourceManager;
import org.hl7.fhir.services.context.ContextUtilities;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.model.extensions.ExtensionUtilities;
import org.hl7.fhir.model.core.Enumerations.PublicationStatus;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.fml.StructureMap;
import org.hl7.fhir.model.fml.StructureMap.StructureMapGroupComponent;
import org.hl7.fhir.model.fml.StructureMap.StructureMapGroupInputComponent;
import org.hl7.fhir.model.fml.StructureMap.StructureMapInputMode;
import org.hl7.fhir.model.fml.StructureMap.StructureMapStructureComponent;
import org.hl7.fhir.services.renderers.Renderer.RenderingStatus;
import org.hl7.fhir.services.renderers.utils.RenderingContext;
import org.hl7.fhir.services.renderers.utils.ResourceWrapper;
import org.hl7.fhir.model.utilities.EOperationOutcome;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class MappingSummaryRenderer {

  public class StructureMapInformation {
    StructureMap map;
    Set<String> sources = new HashSet<>();
    Set<String> targets = new HashSet<>();
  }

  private IWorkerContext context;
  private ContextUtilities cu;
  private List<String> canonicals = new ArrayList<>();
  private RenderingContext rc;
  
  List<StructureMapInformation> maps = new ArrayList<>();

  public MappingSummaryRenderer(IWorkerContext context, RenderingContext rc) {
    super();
    this.context = context;
    cu = new ContextUtilities(context);
    this.rc = rc;
  }

  public void addCanonical(String canonical) {
    canonicals.add(canonical);
  }

  public void analyse() {
    for (StructureMap map : context.fetchResourcesByType(StructureMap.class)) {
      if (Utilities.startsWithInList(map.getUrl(), canonicals)) {
        StructureMapInformation info = new StructureMapInformation();
        info.map = map;
        maps.add(info);
        analyse(map, info.sources, info.targets);
      }
    }
  }

  private void analyse(StructureMap map, Set<String> sources, Set<String> targets) {
    for (StructureMapGroupComponent group : map.getGroupList()) {
      for (StructureMapGroupInputComponent input : group.getInputList()) {
        String url = resolveType(input.getType(), map, input.getMode());
        if (Utilities.isAbsoluteUrl(url)) {
          if (input.getMode() == StructureMapInputMode.SOURCE) {
            sources.add(url);
          } else {
            targets.add(url);
          }
        }
      }
    }
  }

  private String resolveType(String type, StructureMap map, StructureMapInputMode mode) {
    if (Utilities.isAbsoluteUrl(type)) {
      return type;
    }
    for (StructureMapStructureComponent struc : map.getStructureList()) {
      if (struc.hasAlias() && struc.getAlias().equals(type)) {
        return struc.getUrl();
      }
    }
    return null;
  }

  public String render(StructureDefinition sd) throws IOException, FHIRException, EOperationOutcome {
    List<StructureMap> list = new ArrayList<>();
    for (StructureMapInformation info : maps) {
      if (info.sources.contains(sd.getUrl())) {
        list.add(info.map);
      }
    }
    String src = render(sd.getType(), "Source", list);
    list.clear();
    for (StructureMapInformation info : maps) {
      if (info.targets.contains(sd.getUrl())) {
        list.add(info.map);
      }
    }
    String tgt = render(sd.getType(), "Target", list);
    return src+tgt;
  }

  private String render(String typeName, String mode, List<StructureMap> list) throws IOException, FHIRException, EOperationOutcome {
    XhtmlNode x = new XhtmlNode(NodeType.Element, "div");
    x.an("conv-"+typeName+"-"+mode, " ");
    x.h3().tx("Maps"+(mode.equals("Source") ? " to " : " from ")+typeName);
    if (list.isEmpty()) {
      x.para().tx("No maps found");
    } else {
      Collections.sort(list, new CanonicalResourceManager.CanonicalListSorter());
      for (StructureMap map : list) {
        x.an(mode+"-"+map.getId(), " ");
        x.h4().tx(map.getTitle()+" ("+(map.getStatus() == PublicationStatus.ACTIVE ? "Ready for Use" : map.getStatus().getDisplay())+
            (ExtensionUtilities.getStandardsStatus(map) != null ? "/"+ ExtensionUtilities.getStandardsStatus(map).toDisplay() : "")+")");
        new org.hl7.fhir.services.renderers.StructureMapRenderer(rc).buildNarrative(new RenderingStatus(), x, ResourceWrapper.forResource(rc.getContextUtilities(), map));
      }
    }
    return new XhtmlComposer(false, false).compose(x); 
  }

}
