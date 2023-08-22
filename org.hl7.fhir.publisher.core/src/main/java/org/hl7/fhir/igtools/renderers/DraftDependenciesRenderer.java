package org.hl7.fhir.igtools.renderers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.utils.ElementVisitor;
import org.hl7.fhir.r5.utils.ElementVisitor.ElementVisitorInstruction;
import org.hl7.fhir.r5.utils.ElementVisitor.IElementVisitor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;


public class DraftDependenciesRenderer implements IElementVisitor {

  public class DraftReference {

    private List<FetchedResource> resources = new ArrayList<>();
    private String url;
    private CanonicalResource tgt;

    public DraftReference(FetchedResource resource, String url, CanonicalResource tgt) {
      this.resources.add(resource);
      this.url = url;
      this.tgt = tgt;
    }
  }

  private IWorkerContext context;
  private List<DraftReference> draftRefs = new ArrayList<>();
  private String thisPackage;

  public DraftDependenciesRenderer(IWorkerContext context, String thisPackage) {
    super();
    this.context = context;
    this.thisPackage = thisPackage;
  }
  
  public void checkResource(FetchedResource resource) {
    if (resource.getResource() != null) {
      scanResource(resource, resource.getResource());      
    } else {
      scanReferences(resource, resource.getElement());
    }
  }

  private void scanResource(FetchedResource source, Resource resource) {
    new ElementVisitor(this).visit(source, resource);
  }

  private void scanReferences(FetchedResource resource, Element element) {
    for (Element child : element.getChildren()) {
      scanReferences(resource, child);
    }
    if (element.fhirType().equals("Coding")) {
      checkReference(resource, element.getNamedChildValue("system"));
    } else if (element.fhirType().equals("Extension")) {
      checkReference(resource, element.getNamedChildValue("url"));
    } else if (element.fhirType().equals("Reference")) {
      checkReference(resource, element.getNamedChildValue("reference"));
    } else if (element.fhirType().equals("canonical")) {
      checkReference(resource, element.primitiveValue());
    } 
  }

  private void checkReference(FetchedResource resource, String url) {
    if (url == null) {
      return;
    }
    if (url.contains("#")) {
      url = url.substring(0, url.indexOf("#"));
    }
    if (Utilities.isAbsoluteUrl(url)) {
      CanonicalResource tgt = (CanonicalResource) context.fetchResource(Resource.class, url);
      if (tgt != null && tgt.hasSourcePackage() && !thisPackage.equals(tgt.getSourcePackage().getVID())) {
        if (tgt.getStatus() == PublicationStatus.DRAFT || tgt.getExperimental()) {
          DraftReference dr = new DraftReference(resource, url, tgt);
          if (!alreadyExists(dr)) {
            draftRefs.add(dr);
          }
        }
      }
    }
  }

  private boolean alreadyExists(DraftReference dr) {
    for (DraftReference t : draftRefs) {
      if (t.tgt == dr.tgt) {
        t.resources.addAll(dr.resources);
        return true;
      }
    }
    return false;
  }

  @Override
  public ElementVisitorInstruction visit(Object context, Resource resource) {
   // nothing    
    return ElementVisitorInstruction.VISIT_CHILDREN;
  }

  @Override
  public ElementVisitorInstruction visit(Object context, org.hl7.fhir.r5.model.Element element) {
    if (element.isPrimitive()) {
      checkReference((FetchedResource) context, element.primitiveValue());
    }
    return ElementVisitorInstruction.VISIT_CHILDREN;
  }

  public String render() throws IOException {
    if (draftRefs.isEmpty()) {
      return "(none)";
    }
    Set<String> packages = new HashSet<>();
    for (DraftReference t : draftRefs) {
      packages.add(t.tgt.getSourcePackage().getVID());
    }
    XhtmlNode x = new XhtmlNode(NodeType.Element, "ul");
    for (String pid : Utilities.sorted(packages)) {
      XhtmlNode li = x.li();
      li.tx(pid+": ");
      for (DraftReference t : draftRefs) {
        if (pid.equals(t.tgt.getSourcePackage().getVID())) {
          li.sep(", ");
          if (t.tgt.getWebPath() == null) {
            li.code().tx(t.tgt.getName());            
          } else {
            li.ah(t.tgt.getWebPath()).tx(t.tgt.getName());
          }
          li.tx(" ("+t.resources.size()+" uses)");
        }
      }
    }
    return new XhtmlComposer(false, true).compose(x);
  }
}
