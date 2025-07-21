package org.hl7.fhir.igtools.renderers;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.igtools.publisher.comparators.PreviousVersionComparator;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

import java.util.*;

public class DeprecationRenderer extends BaseRenderer {

  public DeprecationRenderer(IWorkerContext context, String corePath, IGKnowledgeProvider igp, List<SpecMapManager> specmaps, Set<String> allTargets, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen) {
    super(context, corePath, igp, specmaps, allTargets, markdownEngine, packge, gen);
  }

  public String deprecationSummary(List<FetchedFile> fileList, PreviousVersionComparator previous) throws Exception {
    Set<String> oldDeprecatedIds = previous == null ? null : previous.deprecatedResourceIds();
    Set<String> deprecatedIds = previous == null ? null : previous.deprecatedResourceIds();

    List<DeprecationInfo> list = new ArrayList<>();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        org.hl7.fhir.r5.elementmodel.Element sse = r.getElement().getExtension(ExtensionDefinitions.EXT_STANDARDS_STATUS);
        boolean dep = false;
        if (sse != null) {
          org.hl7.fhir.r5.elementmodel.Element ssv = sse.getNamedChild("value");
          String ss = ssv.primitiveValue();
          if ("deprecated".equals(ss)) {
            dep = true;
            String d = r.getElement().getNamedChildValue("description");
            String sr =  ssv.getExtensionString(ExtensionDefinitions.EXT_STANDARDS_STATUS_REASON);
            String t = r.getElement().getNamedChildValue("title");
            if (t == null) {
              t = r.getElement().getNamedChildValue("name");
            }
            if (t == null) {
              t = r.getId();
            }
            String path = r.getPath();
            if (deprecatedIds != null) {
              deprecatedIds.add(r.fhirType() + "/" + r.getId());
            }
            list.add(new DeprecationInfo(path, r.fhirType(), t, sr, d, r.getElement().getNamedChildValue("status"), r.getId(),
                    determineDStatus(r.fhirType()+"/"+r.getId(), oldDeprecatedIds)));
          }
        }
        if (!dep && oldDeprecatedIds != null) {
          if (oldDeprecatedIds.contains(r.fhirType()+"/"+r.getId())) {
            String d = r.getElement().getNamedChildValue("description");
            String t = r.getElement().getNamedChildValue("title");
            if (t == null) {
              t = r.getElement().getNamedChildValue("name");
            }
            if (t == null) {
              t = r.getId();
            }

            list.add(new DeprecationInfo(r.getPath(), r.fhirType(), t, null, d, r.getElement().getNamedChildValue("status"), r.getId(),
                    DeprecationStatus.UNDEPRECATED));
          }
        }
      }
    }
    Collections.sort(list, new DeprecationInfoSorter());
    if (list.isEmpty()) {
      return "<p>No deprecated content</p>";
    } else {
      XhtmlNode x = new XhtmlNode(NodeType.Element, "div");
      XhtmlNode tbl = x.table("grid");
      XhtmlNode tr = tbl.tr();
      tr.td().b().tx("Resource");
      tr.td().b().tx("Change");
      tr.td().b().tx("Status");
      tr.td().b().tx("Reason");
      tr.td().b().tx("Description");
      for (DeprecationInfo di : list) {
        tr = tbl.tr();
        if (di.reason == null) {
          if (di.desc.toLowerCase().contains("deprecated")) {
            tr.style("background-color: #ffeccf");
          } else {
            tr.style("background-color: #ffcfcf");
          }
        }
        tr.td().ah(di.path).tx(di.name+" ("+di.type+")");
        if (di.dstatus.isChange()) {
          tr.td().b().tx(di.dstatus.desc());
        } else {
          tr.td().tx(di.dstatus.desc());
        }
        if (!"retired".equals(di.status)) {
          tr.td().style("color: maroon").b().tx(di.status);
        } else {
          tr.td().tx(di.status);
        }
        tr.td().markdown(preProcessMarkdown("?", di.reason), "?");
        tr.td().markdown(preProcessMarkdown("?", di.desc), "?");
      }
      return new XhtmlComposer(true, true).compose(x.getChildNodes());
    }
  }

  private DeprecationStatus determineDStatus(String s, Set<String> deprecatedIds) {
    if (deprecatedIds == null) {
      return DeprecationStatus.UNKNOWN;
    } else if (deprecatedIds.contains(s)) {
      return DeprecationStatus.NO_CHANGE;
    } else {
      return DeprecationStatus.NEW;
    }
  }

  private enum  DeprecationStatus {NEW, UNDEPRECATED, NO_CHANGE, UNKNOWN;

    public String desc() {
      switch (this) {
        case UNKNOWN: return "";
        case NO_CHANGE: return "no change";
        case NEW: return "newly deprecated";
        case UNDEPRECATED: return "un-deprecated";
        default: return "??";
      }
    }

    public boolean isChange() {
      return this == NEW || this == UNDEPRECATED;
    }
  }

  private class DeprecationInfo {
    private final String path;
    private final String id;
    private final String type;
    private final String name;
    private final String reason;
    private final String desc;
    private final String status;
    private DeprecationStatus dstatus;

    public DeprecationInfo(String path, String type, String t, String sr, String d, String st, String id, DeprecationStatus dstatus) {
      this.path = path;
      this.type = type;
      this.name = t;
      this.reason = sr;
      this.desc = d;
      this.status = st;
      this.id = id;
      this.dstatus = dstatus;
    }
  }

  private class DeprecationInfoSorter implements Comparator<DeprecationInfo> {
    @Override
    public int compare(DeprecationInfo o1, DeprecationInfo o2) {
      if (o1.dstatus != o2.dstatus) {
        return o1.dstatus.compareTo(o2.dstatus);
      } else {
        return StringUtils.compare(o1.name, o2.name);
      }
    }
  }

  public String listNewResources(List<FetchedFile> fileList, PreviousVersionComparator previous, String type) {
    if (previous == null) {
      return "";
    }
    Set<String> ids = previous.listAllResourceIds();
    if (ids == null) {
      return "";
    }

    CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder(", ", " and ");
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (isOfType(type, r) && !ids.contains(r.fhirType()+"/"+r.getId())) {
          String t = r.getElement().getNamedChildValue("title");
          if (t == null) {
            t = r.getElement().getNamedChildValue("name");
          }
          if (t == null) {
            t = r.getId();
          }
          b.append("<a href=\""+r.getPath()+"\">"+Utilities.escapeXml(t)+"</a>");
        }
      }
    }
    return b.toString();
  }

  private boolean isOfType(String type, FetchedResource r) {
    if (type == null) {
      return true;
    }
    if (!type.contains(".")) {
      return type.equals(r.fhirType());
    } else {
      String rt = type.substring(0, type.indexOf('.'));
      String tt = type.substring(type.indexOf('.') + 1);
      if (rt.equals(r.fhirType())) {
        switch (rt) {
          case "StructureDefinition":
            if ("extension".equals(tt)) {
              return "Extension".equals(r.getElement().getNamedChildValue("type"));
            } else if ("logical".equals(tt)) {
              return "logical".equals(r.getElement().getNamedChildValue("kind"));
            } else if ("additional".equals(tt)) {
              return false;
            } else if ("profile".equals(tt)) {
              return "constraint".equals(r.getElement().getNamedChildValue("derivation"));
            } else {
              return false;
            }
          default: return false;
        }
      } else {
        return false;
      }
    }
  }

  private boolean isOfType(String type, CanonicalResource r) {
    if (type == null) {
      return true;
    }
    if (!type.contains(".")) {
      return type.equals(r.fhirType());
    } else {
      String rt = type.substring(0, type.indexOf('.'));
      String tt = type.substring(type.indexOf('.') + 1);
      if (rt.equals(r.fhirType())) {
        switch (rt) {
          case "StructureDefinition":
            StructureDefinition sd = (StructureDefinition) r;
            if ("extension".equals(tt)) {
              return "Extension".equals(sd.getType());
            } else if ("logical".equals(tt)) {
              return "logical".equals(sd.getKind().toCode());
            } else if ("additional".equals(tt)) {
              return false;
            } else if ("profile".equals(tt)) {
              return "constraint".equals(sd.getDerivation().toCode());
            } else {
              return false;
            }
          default: return false;
        }
      } else {
        return false;
      }
    }
  }

  public String listDeletedResources(List<FetchedFile> fileList, PreviousVersionComparator previous, String type) {
    if (previous == null) {
      return "";
    }
    List<CanonicalResource> oldResources = previous.listAllResources();
    if (oldResources == null) {
      return "<i>(n/a)</i>";
    }
    Set<String> ids = new HashSet<>();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        ids.add(r.fhirType() + "/" + r.getId());
      }
    }
    if (ids == null) {
      return "";
    }

    CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder(", ", " and ");
    for (CanonicalResource r : oldResources) {
      if (isOfType(type, r) && !ids.contains(r.fhirType() + "/" + r.getId())) {
        String t = r.present();
        b.append(Utilities.escapeXml(t));
      }
    }
    if (b.length() == 0) {
      return "<i>(none)</i>";
    } else {
      return b.toString();
    }
  }

}
