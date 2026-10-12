package org.hl7.fhir.igtools.publisher.loaders;

import java.io.IOException;
import java.io.InputStream;

import org.cqframework.cql.cql2elm.model.Model;
import org.hl7.elm.r1.Mode;
import org.hl7.fhir.convertors.factory.*;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.CqlSubSystem.ICqlResourceReader;
import org.hl7.fhir.model.ModelContext;
import org.hl7.fhir.model.core.ActivityDefinition;
import org.hl7.fhir.model.core.Library;
import org.hl7.fhir.model.core.Measure;
import org.hl7.fhir.model.core.PlanDefinition;
import org.hl7.fhir.model.core.formats.JsonParser;
import org.hl7.fhir.utilities.VersionUtilities;

public class CqlResourceLoader implements ICqlResourceReader {

  private String version;

  public CqlResourceLoader(String version) {
    this.version = version;
  }

  @Override
  public Library readLibrary(InputStream stream) throws FHIRFormatError, IOException {
    if (VersionUtilities.isR2Ver(version)) {
      throw new FHIRException("Library is not supported in R2");
    } else if (VersionUtilities.isR2BVer(version)) {
      org.hl7.fhir.dstu2016may.model.Resource res = new org.hl7.fhir.dstu2016may.formats.JsonParser().parse(stream);
      return (Library) VersionConvertorFactory_14_N.convertResource(res);
    } else if (VersionUtilities.isR3Ver(version)) {
      org.hl7.fhir.dstu3.model.Resource res = new org.hl7.fhir.dstu3.formats.JsonParser().parse(stream);
      return (Library) VersionConvertorFactory_30_N.convertResource(res);
    } else if (VersionUtilities.isR4Ver(version)) {
      org.hl7.fhir.r4.model.Resource res = new org.hl7.fhir.r4.formats.JsonParser().parse(stream);
      return (Library) VersionConvertorFactory_40_N.convertResource(res);
    } else if (VersionUtilities.isR5Ver(version)) {
      org.hl7.fhir.r5.model.Resource res = new org.hl7.fhir.r5.formats.JsonParser().parse(stream);
      return (Library) VersionConvertorFactory_50_N.convertResource(res);
    } else if (VersionUtilities.isR6Plus(version)) {
      return (Library) new JsonParser(ModelContext.fullCoreContext()).parse(stream);
    } else {
      throw new FHIRException("Unknown Version '"+version+"'");      
    }
  }
  

  @Override
  public Measure readMeasure(InputStream stream) throws FHIRFormatError, IOException {
    if (VersionUtilities.isR2Ver(version)) {
      throw new FHIRException("Measure is not supported in R2");
    } else if (VersionUtilities.isR2BVer(version)) {
      org.hl7.fhir.dstu2016may.model.Resource res = new org.hl7.fhir.dstu2016may.formats.JsonParser().parse(stream);
      return (Measure) VersionConvertorFactory_14_N.convertResource(res);
    } else if (VersionUtilities.isR3Ver(version)) {
      org.hl7.fhir.dstu3.model.Resource res = new org.hl7.fhir.dstu3.formats.JsonParser().parse(stream);
      return (Measure) VersionConvertorFactory_30_N.convertResource(res);
    } else if (VersionUtilities.isR4Ver(version)) {
      org.hl7.fhir.r4.model.Resource res = new org.hl7.fhir.r4.formats.JsonParser().parse(stream);
      return (Measure) VersionConvertorFactory_40_N.convertResource(res);
    } else if (VersionUtilities.isR5Ver(version)) {
      org.hl7.fhir.r5.model.Resource res = new org.hl7.fhir.r5.formats.JsonParser().parse(stream);
      return (Measure) VersionConvertorFactory_50_N.convertResource(res);
    } else if (VersionUtilities.isR6Plus(version)) {
      return (Measure) new JsonParser(ModelContext.fullCoreContext()).parse(stream);
    } else {
      throw new FHIRException("Unknown Version '"+version+"'");      
    }
  }

  @Override
  public PlanDefinition readPlanDefinition(InputStream stream) throws FHIRFormatError, IOException {
    if (VersionUtilities.isR2Ver(version)) {
      throw new FHIRException("PlanDefinition is not supported in R2");
    } else if (VersionUtilities.isR2BVer(version)) {
      org.hl7.fhir.dstu2016may.model.Resource res = new org.hl7.fhir.dstu2016may.formats.JsonParser().parse(stream);
      return (PlanDefinition) VersionConvertorFactory_14_N.convertResource(res);
    } else if (VersionUtilities.isR3Ver(version)) {
      org.hl7.fhir.dstu3.model.Resource res = new org.hl7.fhir.dstu3.formats.JsonParser().parse(stream);
      return (PlanDefinition) VersionConvertorFactory_30_N.convertResource(res);
    } else if (VersionUtilities.isR4Ver(version)) {
      org.hl7.fhir.r4.model.Resource res = new org.hl7.fhir.r4.formats.JsonParser().parse(stream);
      return (PlanDefinition) VersionConvertorFactory_40_N.convertResource(res);
    } else if (VersionUtilities.isR5Ver(version)) {
      org.hl7.fhir.r5.model.Resource res = new org.hl7.fhir.r5.formats.JsonParser().parse(stream);
      return (PlanDefinition) VersionConvertorFactory_50_N.convertResource(res);
    } else if (VersionUtilities.isR6Plus(version)) {
      return (PlanDefinition) new JsonParser(ModelContext.fullCoreContext()).parse(stream);
    } else {
      throw new FHIRException("Unknown Version '"+version+"'");      
    }
  }

  @Override
  public ActivityDefinition readActivityDefinition(InputStream stream) throws FHIRFormatError, IOException {
    if (VersionUtilities.isR2Ver(version)) {
      throw new FHIRException("ActivityDefinition is not supported in R2");
    } else if (VersionUtilities.isR2BVer(version)) {
      org.hl7.fhir.dstu2016may.model.Resource res = new org.hl7.fhir.dstu2016may.formats.JsonParser().parse(stream);
      return (ActivityDefinition) VersionConvertorFactory_14_N.convertResource(res);
    } else if (VersionUtilities.isR3Ver(version)) {
      org.hl7.fhir.dstu3.model.Resource res = new org.hl7.fhir.dstu3.formats.JsonParser().parse(stream);
      return (ActivityDefinition) VersionConvertorFactory_30_N.convertResource(res);
    } else if (VersionUtilities.isR4Ver(version)) {
      org.hl7.fhir.r4.model.Resource res = new org.hl7.fhir.r4.formats.JsonParser().parse(stream);
      return (ActivityDefinition) VersionConvertorFactory_40_N.convertResource(res);
    } else if (VersionUtilities.isR5Ver(version)) {
      org.hl7.fhir.r5.model.Resource res = new org.hl7.fhir.r5.formats.JsonParser().parse(stream);
      return (ActivityDefinition) VersionConvertorFactory_50_N.convertResource(res);
    } else if (VersionUtilities.isR6Plus(version)) {
      return (ActivityDefinition) new JsonParser(ModelContext.fullCoreContext()).parse(stream);
    } else {
      throw new FHIRException("Unknown Version '"+version+"'");      
    }
  }
  

}
