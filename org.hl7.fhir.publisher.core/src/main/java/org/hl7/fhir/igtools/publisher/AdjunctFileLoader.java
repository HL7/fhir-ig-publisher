package org.hl7.fhir.igtools.publisher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.ObjectConverter;
import org.hl7.fhir.r5.model.Attachment;
import org.hl7.fhir.r5.model.Base64BinaryType;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;

public class AdjunctFileLoader {

  private CqlSubSystem cql;
  private List<String> binaryPaths;

  public AdjunctFileLoader(List<String> binaryPaths, CqlSubSystem cql) {
    this.binaryPaths = binaryPaths;
    this.cql = cql;
  }

  private class ElementWithPath {
    private String path;
    private Element element;
    public ElementWithPath(String path, Element element) {
      super();
      this.path = path;
      this.element = element;
    }
    public String getPath() {
      return path;
    }
    public Element getElement() {
      return element;
    }
    
  }
  public boolean replaceAttachments(FetchedFile f, Element e) {
    List<ElementWithPath> attachments = makeListOfAttachments(e);
    for (ElementWithPath att : attachments) {
      String fn = checkReplaceable(att.element);
      if (fn != null) {
        Attachment a;
        try {
          a = loadFile(fn);
          if (a == null) {
            f.getErrors().add(new ValidationMessage(Source.InstanceValidator, IssueType.NOTFOUND, att.element.line(), att.element.col(), att.getPath(), "Unable to find Adjunct Binary "+fn, IssueSeverity.ERROR));
          } else {
            att.element.getChildren().clear();
            if (a.hasContentType()) {
              att.element.setChildValue("contentType", a.getContentType());
            } else {
              f.getErrors().add(new ValidationMessage(Source.InstanceValidator, IssueType.NOTFOUND, att.element.line(), att.element.col(), att.getPath(), "Unknown file type "+fn, IssueSeverity.ERROR));
            }
            att.element.setProperty("data".hashCode(), "data", new Base64BinaryType(a.getData()));
          }
          if (e.fhirType().equals("Library") && "text/cql".equals(a.getContentType())) {
            // we just injected CQL into a library 
            performLibraryCQLProcessing(e, a.getUrl());
          }
        } catch (Exception ex) {
          f.getErrors().add(new ValidationMessage(Source.InstanceValidator, IssueType.NOTFOUND, att.element.line(), att.element.col(), att.getPath(), "Error Loading "+fn+": " +ex.getMessage(), IssueSeverity.ERROR));
        }
      }
    }
    // special cases:
    // binary - not an attachment 
    if (e.fhirType().equals("Binary")) {
      String n = e.getChildValue("data");
      if (n != null && n.startsWith("ig-loader-")) {
        String fn = n.substring(10);
        try {
          Attachment a = loadFile(fn);
          if (a == null) {
            f.getErrors().add(new ValidationMessage(Source.InstanceValidator, IssueType.NOTFOUND, e.line(), e.col(), "Binary", "Unable to find Adjunct Binary "+fn, IssueSeverity.ERROR));
          } else {
            if (a.hasContentType()) {
              e.setChildValue("contentType", a.getContentType());
            } else {
              f.getErrors().add(new ValidationMessage(Source.InstanceValidator, IssueType.NOTFOUND, e.line(), e.col(), "Binary", "Unknown file type "+fn, IssueSeverity.ERROR));
            }
            e.setProperty("data".hashCode(), "data", new Base64BinaryType(a.getData()));
          }
        } catch (Exception ex) {
          f.getErrors().add(new ValidationMessage(Source.InstanceValidator, IssueType.NOTFOUND, e.line(), e.col(), "Binary", "Error Loading "+fn+": " +ex.getMessage(), IssueSeverity.ERROR));
        }
        
      }
    }
    // Library - post processing needed
    return false;
  }

  private Attachment loadFile(String fn) throws FileNotFoundException, IOException {
    for (String dir : binaryPaths) {
      File f = new File(Utilities.path(dir, fn));
      if (f.exists()) {
        Attachment att = new Attachment();
        att.setContentType(determineContentType(Utilities.getFileExtension(fn)));
        att.setData(TextFile.fileToBytes(f));
        att.setUrl(f.getAbsolutePath());
        return att;
      }
    }
    return null;
  }

  private String determineContentType(String ext) {
    ext = ext.toLowerCase();
    if ("pdf".equals(ext)) {
      return "application/pdf";
    } else if ("dicom".equals(ext)) {
      return "image/dicom";
    } else if ("png".equals(ext)) {
      return "image/png";
    } else if ("gif".equals(ext)) {
      return "image/gif";
    } else if ("jog".equals(ext)) {
      return "image/jpeg";
    } else if ("cql".equals(ext)) {
      return "text/cql";
    } else {
      return null;
    }
  }

  private String checkReplaceable(Element att) {
    if (att.getChildren().size() != 1) {
      return null;
    }
    Element id = att.getChildren().get(0);
    if (!id.getName().equals("id")) {
      return null;
    }
    String value = id.primitiveValue();
    if (value == null || !value.startsWith("ig-loader-")) {
      return null;
    }
    return value.substring(10);
  }

  private List<ElementWithPath> makeListOfAttachments(Element element) {
    List<ElementWithPath> res = new ArrayList<>();
    listAttachments(res, element.getChildren(), element.fhirType());
    return res;
  }

  private void listAttachments(List<ElementWithPath> res, List<Element> children, String path) {
    String lastName = "";
    int c = -1;
    for (int i = 0; i < children.size(); i++) {
      Element child = children.get(i);
      if (lastName.equals(child.getName())) {
        c++;
      } else if (i == children.size()-1 || !children.get(i+1).getName().equals(child.getName())) {
        c = -1;
      } else {
        c = 0;
      }
      lastName = child.getName();
      String p = "."+lastName+(i == -1 ? "" : "["+i+"]");
      if (child.fhirType().equals("Attachment")) {
        res.add(new ElementWithPath(path+p, child));
      } else {
        listAttachments(res, child.getChildren(), path+p);
      }
    }    
  }
  
  private void performLibraryCQLProcessing(Element e, String name) {
    
  }

}
