package org.hl7.fhir.igtools.publisher;

import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.services.elementmodel.ElementVisitor;

import java.util.List;

public class DependencyElementVisitor implements ElementVisitor.IElementVisitor {
  private final List<FetchedFile> fileList;
  private final List<FetchedFile> dependencies;
  private final FetchedFile focus;

  public DependencyElementVisitor(List<FetchedFile> fileList, List<FetchedFile> dependencies, FetchedFile focus) {
    super();
    this.fileList = fileList;
    this.dependencies = dependencies;
    this.focus = focus;
  }

  @Override
  public ElementVisitor.ElementVisitorInstruction visit(Object context, Element element) {
    ElementVisitor.ElementVisitorInstruction result = ElementVisitor.ElementVisitorInstruction.NO_VISIT_CHILDREN;
    if (element.isElementForPath("ValueSet.compose.include", "ValueSet.compose.exclude", "Coding.value")) {
      String url = element.getNamedChildValue("system");
      checkDependencies(url);
    } else if (element.isElementForPath("Quantity")) {
      String url = element.getNamedChildValue("system");
      checkDependencies(url);
    } else if (element.isPrimitive() ) {
      if ("canonical".equals(element.fhirType())) {
        String canonical = element.primitiveValue();
        if (canonical != null) { // a canonical can have only extensions
          // versions are ignored: a missed dependency leaves -rapido output stale, an extra one only costs a rebuild
          checkDependencies(canonical.contains("|") ? canonical.substring(0, canonical.indexOf("|")) : canonical);
        }
      } else {
        String s = element.primitiveValue();
        checkDependencies(s);
      }
    } else {
      result = ElementVisitor.ElementVisitorInstruction.VISIT_CHILDREN;
    }
    return result;
  }

  private void checkDependencies(String url) {
    if (url != null) {
      for (FetchedFile f : fileList) {
        if (f != focus) {
          boolean dep = false;
          for (FetchedResource r : f.getResources()) {
            if (url.equals(r.fhirType() + "/" + r.getId())) {
              dep = true;
            } else if (url.equals(r.getElement().getNamedChildValue("url"))) {
              dep = true;
            }
          }
          if (dep && !dependencies.contains(f)) {
            dependencies.add(f);
          }
        }
      }
    }
  }
}
