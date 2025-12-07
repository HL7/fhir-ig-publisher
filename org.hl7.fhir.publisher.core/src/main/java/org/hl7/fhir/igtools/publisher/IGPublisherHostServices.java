package org.hl7.fhir.igtools.publisher;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.PathEngineException;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.fhirpath.BaseHostServices;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r5.fhirpath.TypeDetails;
import org.hl7.fhir.r5.liquid.GlobalObject;
import org.hl7.fhir.r5.fhirpath.ExpressionNode.CollectionStatus;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.utils.validation.IResourceValidator;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.fhirpath.FHIRPathConstantEvaluationMode;
import org.hl7.fhir.utilities.validation.ValidationMessage;

public class IGPublisherHostServices extends BaseHostServices {
  
  private IGKnowledgeProvider igpkp;
  private List<FetchedFile> fileList = new ArrayList<FetchedFile>();
  private SimpleWorkerContext context;
  private DateTimeType dt;
  private DateType dtD;
  private StringType pathToSpec;
  
  public IGPublisherHostServices(IGKnowledgeProvider igpkp, List<FetchedFile> fileList,
                                 SimpleWorkerContext context, DateTimeType dt, DateType dtD, StringType pathToSpec) {
    super(context);
    this.igpkp = igpkp;
    this.fileList = fileList;
    this.context = context;
    this.dt = dt;
    this.dtD = dtD;
    this.pathToSpec = pathToSpec;
  }
  
  @Override
  public List<Base> resolveConstant(FHIRPathEngine engine, Object appContext, String name, FHIRPathConstantEvaluationMode mode) throws PathEngineException {
    if ("Globals".equals(name)) {
      List<Base> list = new ArrayList<Base>();
      list.add(new GlobalObject(dt, dtD, pathToSpec));
      return list;
    } else {
      return new ArrayList<>();
    }
  }

  @Override
  public TypeDetails resolveConstantType(FHIRPathEngine engine, Object appContext, String name, FHIRPathConstantEvaluationMode mode) throws PathEngineException {
    if ("Globals".equals(name)) {
      return new TypeDetails(CollectionStatus.SINGLETON, "GlobalObject");
    } else {
      return null; // whatever it is, we don't know about it.
    }
  }

  @Override
  public boolean paramIsType(String name, int index) {
    return false;
  }
  
  @Override
  public boolean log(String argument, List<Base> focus) {
    return false;
  }

  @Override
  public Base resolveReference(FHIRPathEngine engine, Object appContext, String url, Base refContext) {
    if (Utilities.isAbsoluteUrl(url)) {
      if (url.startsWith(igpkp.getCanonical())) {
        url = url.substring(igpkp.getCanonical().length());
        if (url.startsWith("/")) {
          url = url.substring(1);
        }
      } else
        return null;;
    }
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getElement() != null && url.equals(r.fhirType()+"/"+r.getId())) {
          return r.getElement();
        }
      }
    }
    return null;
  }

  @Override
  public boolean conformsToProfile(FHIRPathEngine engine, Object appContext, Base item, String url) throws FHIRException {
    IResourceValidator val = context.newValidator();
    List<ValidationMessage> valerrors = new ArrayList<ValidationMessage>();
    if (item instanceof Resource) {
      val.validate(appContext, valerrors, (Resource) item, url);
      boolean ok = true;
      for (ValidationMessage v : valerrors) {
        ok = ok && v.getLevel().isError();
      }
      return ok;
    }
    throw new NotImplementedException("Not done yet (IGPublisherHostServices.conformsToProfile), when item is element");
  }

  @Override
  public ValueSet resolveValueSet(FHIRPathEngine engine, Object appContext, String url) {
    throw new NotImplementedException("Not done yet (IGPublisherHostServices.resolveValueSet)"); // cause I don't know when we 'd need to do this
  }

  @Override
  public Base findContainingResource(Object appContext, Base item) {
    if (item instanceof Element element) {
      while (element != null && !element.isResource()) {
        element = element.getParentForValidator();
      }
      if (element != null) {
        return element;
      }
    }
    if (item instanceof Resource) {
      return item;
    }
    // now it gets hard
    return null; // for now
  }

}