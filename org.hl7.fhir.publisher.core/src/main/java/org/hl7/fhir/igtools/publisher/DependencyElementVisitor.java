package org.hl7.fhir.igtools.publisher;

import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.services.elementmodel.ElementVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DependencyElementVisitor implements ElementVisitor.IElementVisitor {

  /**
   * The files of the IG by what a value can refer to them by: the type/id and the url of each of their resources.
   * Built once for all the files, so that looking up a value doesn't go through every resource of the IG
   */
  public static class Index {
    private final Map<String, List<FetchedFile>> files = new HashMap<>();

    public Index(List<FetchedFile> fileList) {
      for (FetchedFile f : fileList) {
        for (FetchedResource r : f.getResources()) {
          add(r.fhirType() + "/" + r.getId(), f);
          if (r.getElement() != null) {
            add(r.getElement().getNamedChildValue("url"), f);
          }
        }
      }
    }

    private void add(String key, FetchedFile f) {
      if (key != null) {
        List<FetchedFile> list = files.computeIfAbsent(key, k -> new ArrayList<>());
        // files are added in the order of the file list, so a file that is already there is the last one
        if (list.isEmpty() || list.get(list.size() - 1) != f) {
          list.add(f);
        }
      }
    }

    /** the files with a resource that the value refers to, in the order of the file list */
    List<FetchedFile> filesFor(String value) {
      return files.getOrDefault(value, List.of());
    }
  }

  private final Index index;
  private final List<FetchedFile> dependencies;
  private final FetchedFile focus;

  public DependencyElementVisitor(List<FetchedFile> fileList, List<FetchedFile> dependencies, FetchedFile focus) {
    this(new Index(fileList), dependencies, focus);
  }

  public DependencyElementVisitor(Index index, List<FetchedFile> dependencies, FetchedFile focus) {
    super();
    this.index = index;
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
      for (FetchedFile f : index.filesFor(url)) {
        if (f != focus && !dependencies.contains(f)) {
          dependencies.add(f);
        }
      }
    }
  }
}
