package org.hl7.fhir.igtools.publisher;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.PathEngineException;
import org.hl7.fhir.igtools.publisher.IGPublisherHostServices.GlobalObject;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r5.fhirpath.TypeDetails;
import org.hl7.fhir.r5.fhirpath.ExpressionNode.CollectionStatus;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine.IEvaluationContext;
import org.hl7.fhir.r5.fhirpath.FHIRPathUtilityClasses.FunctionDetails;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r5.model.DateTimeType;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.utils.BaseCSVWrapper;
import org.hl7.fhir.r5.utils.validation.IResourceValidator;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.validation.ValidationMessage;

public class IGPublisherHostServices implements IEvaluationContext {

  public class GlobalObject extends Base {

    @Override
    public String fhirType() {
      return "GlobalObject";
    }

    @Override
    public String getIdBase() {
      return null;
    }

    @Override
    public void setIdBase(String value) {
      throw new Error("Read only"); 
    }

    @Override
    public Base copy() {
      return this;
    }

    @Override
    public FhirPublication getFHIRPublicationVersion() {
      return FhirPublication.R5;
    }

    public Base[] getProperty(int hash, String name, boolean checkValid) throws FHIRException {
      if ("dateTime".equals(name)) {
        Base[] l = new Base[1];
        l[0] = dt;
        return l;
      }

      return super.getProperty(hash, name, checkValid);
    }

  }

  private IGKnowledgeProvider igpkp;
  private List<FetchedFile> fileList = new ArrayList<FetchedFile>();
  private SimpleWorkerContext context;
  private DateTimeType dt;
  
  public IGPublisherHostServices(IGKnowledgeProvider igpkp, List<FetchedFile> fileList,
      SimpleWorkerContext context, DateTimeType dt) {
    super();
    this.igpkp = igpkp;
    this.fileList = fileList;
    this.context = context;
    this.dt = dt;
  }

  @Override
  public List<Base> resolveConstant(FHIRPathEngine engine, Object appContext, String name, boolean beforeContext, boolean explicitConstant) throws PathEngineException {
    if ("Globals".equals(name)) {
      List<Base> list = new ArrayList<Base>();
      list.add(new GlobalObject());
      return list;
    } else {
      return new ArrayList<>();
    }
  }

  @Override
  public TypeDetails resolveConstantType(FHIRPathEngine engine, Object appContext, String name, boolean explicitConstant) throws PathEngineException {
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
  public FunctionDetails resolveFunction(FHIRPathEngine engine, String functionName) {
    throw new NotImplementedException("Not done yet (IGPublisherHostServices.resolveFunction)");
  }

  @Override
  public TypeDetails checkFunction(FHIRPathEngine engine, Object appContext, String functionName, TypeDetails focus, List<TypeDetails> parameters) throws PathEngineException {
    throw new NotImplementedException("Not done yet (IGPublisherHostServices.checkFunction)");
  }

  @Override
  public List<Base> executeFunction(FHIRPathEngine engine, Object appContext, List<Base> focus, String functionName, List<List<Base>> parameters) {
    throw new NotImplementedException("Not done yet (IGPublisherHostServices.executeFunction)");
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
}