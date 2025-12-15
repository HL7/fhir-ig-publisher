package org.hl7.fhir.igtools.publisher.loaders;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.igtools.publisher.CqlSubSystem;
import org.hl7.fhir.igtools.publisher.CqlSubSystem.CqlSourceFileInformation;
import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.model.Attachment;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r5.model.Base64BinaryType;
import org.hl7.fhir.r5.model.Library;
import org.hl7.fhir.r5.model.Property;
import org.hl7.fhir.r5.model.RelatedArtifact.RelatedArtifactType;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.utilities.NamedItemList;
import org.hl7.fhir.utilities.FileUtilities;
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
  
  private class AttachmentWithPath {
    private String path;
    private Attachment attachment;
    public AttachmentWithPath(String path, Attachment attachment) {
      super();
      this.path = path;
      this.attachment = attachment;
    }
    public String getPath() {
      return path;
    }
    public Attachment getAttachment() {
      return attachment;
    }
  }
  
  
  /**
   * second invocation, for metadata resources 
   * 
   * @param f
   * @param r
   * @return
   */
  public boolean replaceAttachments2(FetchedFile f, FetchedResource r) {
    boolean res = false;
    List<AttachmentWithPath> attachments = makeListOfAttachments(r.getResource());
    for (AttachmentWithPath att : attachments) {
      String fn = checkReplaceable(att.getAttachment());
      if (fn != null) {
        Attachment a;
        try {
          f.addAdditionalPath(fn);
          a = loadFile(fn);
          res = true;
          att.getAttachment().setId(null);
          att.getAttachment().setData(a.getData());
          att.getAttachment().setContentType(a.getContentType());
          // Library - post processing needed
          if (r.getResource() instanceof Library && ("text/cql".equals(a.getContentType()) || "application/xml".equals(a.getContentType()))) {
            // we just injected CQL or a CQL ModelInfo into a library
            try {
              performLibraryCQLProcessing(f, (Library) r.getResource(), a);
            } catch (Exception ex) {
              f.getErrors().add(new ValidationMessage(Source.InstanceValidator, IssueType.EXCEPTION, att.getPath(), "Error processing CQL or CQL Model Info: " +ex.getMessage(), IssueSeverity.ERROR));
            }
          }
        } catch (Exception ex) {
          f.getErrors().add(new ValidationMessage(Source.InstanceValidator, IssueType.NOTFOUND, att.getPath(), "Error Loading "+fn+": " +ex.getMessage(), IssueSeverity.ERROR));
        }
      }
    }
    return res;    
  }
  
  /**
   * This services is invoked twice. The first time, on the elemnt model, as soon as a resource is loaded. 
   * But it will be invoked a second time for Metadata resources, once the metdata resource has been loaded,
   * so we ignore them the first time around
   * 
   * @param f
   * @param r
   * @param metadataResourceNames
   * @return
   */
  public boolean replaceAttachments1(FetchedFile f, FetchedResource r, List<String> metadataResourceNames) {    
    if (r.getElement().fhirType().equals("Binary")) {
      return processBinary(f, r.getElement());
    } else if (!Utilities.existsInList(r.getElement().fhirType(), metadataResourceNames)) {
      return processByElement(f, r.getElement());
    } else {
      return false;
    }
  }

  private boolean processByElement(FetchedFile f, Element e) {
    boolean res = false;
    List<ElementWithPath> attachments = makeListOfAttachments(e);
    for (ElementWithPath att : attachments) {
      String fn = checkReplaceable(att.getElement());
      if (fn != null) {
        Attachment a;
        try {
          a = loadFile(fn);
          res = true;
          addAttachment(f, att, fn, a);
        } catch (Exception ex) {
          f.getErrors().add(new ValidationMessage(Source.InstanceValidator, IssueType.NOTFOUND, att.getElement().line(), att.getElement().col(), att.getPath(), "Error Loading "+fn+": " +ex.getMessage(), IssueSeverity.ERROR));
        }
      }
    }
    return res;
  }

  public boolean processBinary(FetchedFile f, Element e) {
    boolean res = false;
    String n = e.getChildValue("data");
    if (n != null && n.startsWith("ig-loader-")) {
      String fn = n.substring(10);
      try {
        Attachment a = loadFile(fn);
        if (a == null) {
          f.getErrors().add(new ValidationMessage(Source.InstanceValidator, IssueType.NOTFOUND, e.line(), e.col(), "Binary", "Unable to find Adjunct Binary "+fn, IssueSeverity.ERROR));
        } else {
          res = true;
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
    return res;
  }

  public void addAttachment(FetchedFile f, ElementWithPath att, String fn, Attachment a) {
    if (a == null) {
      f.getErrors().add(new ValidationMessage(Source.InstanceValidator, IssueType.NOTFOUND, att.getElement().line(), att.getElement().col(), att.getPath(), "Unable to find Adjunct Binary "+fn, IssueSeverity.ERROR));
    } else {
      att.getElement().getChildren().clear();
      if (a.hasContentType()) {
        att.getElement().setChildValue("contentType", a.getContentType());
      } else {
        f.getErrors().add(new ValidationMessage(Source.InstanceValidator, IssueType.NOTFOUND, att.getElement().line(), att.getElement().col(), att.getPath(), "Unknown file type "+fn, IssueSeverity.ERROR));
      }
      att.getElement().setProperty("data".hashCode(), "data", new Base64BinaryType(a.getData()));
    }
  }

  private Attachment loadFile(String fn) throws FileNotFoundException, IOException {
    for (String dir : binaryPaths) {
      File f = new File(Utilities.path(dir, fn));
      if (f.exists()) {
        Attachment att = new Attachment();
        att.setContentType(determineContentType(Utilities.getFileExtension(fn)));
        att.setData(FileUtilities.fileToBytes(f));
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
    } else if ("feature".equals(ext)) {
      return "text/x-gherkin";
    } else if ("json".equals(ext)) {
      return "application/json";
    } else if ("xml".equals(ext)) {
      return "application/xml";
    } else if ("text".equals(ext)) {
      return "text/plain";
    } else if ("txt".equals(ext)) {
      return "text/plain";
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

  private String checkReplaceable(Attachment att) {
    if (att.hasData() || att.hasUrl() || att.hasContentType() || att.hasSize() || att.hasTitle()) {
      return null;
    }
    if (!att.hasId()) {
      return null;
    }
    String value = att.getId();
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

  private void listAttachments(List<ElementWithPath> res, NamedItemList<Element> children, String path) {
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
  

  private List<AttachmentWithPath> makeListOfAttachments(Resource r) {
    List<AttachmentWithPath> res = new ArrayList<>();
    listAttachments(res, r, r.fhirType());
    return res;
  }

  private void listAttachments(List<AttachmentWithPath> res, Base focus, String path) {
    if (focus instanceof Attachment) {
      res.add(new AttachmentWithPath(path, (Attachment) focus));
    } else {
      for (Property p : focus.children()) {
        if (p.getMaxCardinality() > 1) {
          int i = 0;
          for (Base b : p.getValues()) {
            listAttachments(res, b, path+"."+p.getName()+"{"+i+"]");
            i++;
          }
        } else if (p.hasValues()) {
          listAttachments(res, p.getValues().get(0), path+"."+p.getName());
        }
      }
    }       
  }

  private void performLibraryCQLProcessing(FetchedFile f, Library lib, Attachment attachment) {
    CqlSourceFileInformation info = cql.getFileInformation(attachment.getUrl());
    if (info != null) {
      f.getErrors().addAll(info.getErrors());
      if (info.getElm() != null) {
        lib.addContent().setContentType("application/elm+xml").setData(info.getElm());
      }
      if (info.getJsonElm() != null) {
        lib.addContent().setContentType("application/elm+json").setData(info.getJsonElm());
      }
      lib.getDataRequirement().clear();
      lib.getDataRequirement().addAll(info.getDataRequirements());
      lib.getRelatedArtifact().removeIf(n -> n.getType() == RelatedArtifactType.DEPENDSON);
      lib.getRelatedArtifact().addAll(info.getRelatedArtifacts());
      lib.getParameter().clear();
      lib.getParameter().addAll(info.getParameters());
    } else {
      f.getErrors().add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, "Library", "No cql info found for "+f.getName(), IssueSeverity.ERROR));      
    }
    
  }

}
