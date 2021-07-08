package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.io.InputStream;

import org.hl7.fhir.convertors.conv14_50.VersionConvertor_14_50;
import org.hl7.fhir.convertors.conv30_50.VersionConvertor_30_50;
import org.hl7.fhir.convertors.VersionConvertor_40_50;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.CqlSubSystem.ILibraryReader;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.Library;
import org.hl7.fhir.utilities.VersionUtilities;

public class LibraryLoader implements ILibraryReader {

  private String version;

  public LibraryLoader(String version) {
    this.version = version;
  }

  @Override
  public Library readLibrary(InputStream stream) throws FHIRFormatError, IOException {
    if (VersionUtilities.isR2Ver(version)) {
      throw new FHIRException("Library is not supported in R2");
    } else if (VersionUtilities.isR2BVer(version)) {
      org.hl7.fhir.dstu2016may.model.Resource res = new org.hl7.fhir.dstu2016may.formats.JsonParser().parse(stream);
      return (Library) VersionConvertor_14_50.convertResource(res);
    } else if (VersionUtilities.isR3Ver(version)) {
      org.hl7.fhir.dstu3.model.Resource res = new org.hl7.fhir.dstu3.formats.JsonParser().parse(stream);
      return (Library) VersionConvertor_30_50.convertResource(res);
    } else if (VersionUtilities.isR4Ver(version)) {
      org.hl7.fhir.r4.model.Resource res = new org.hl7.fhir.r4.formats.JsonParser().parse(stream);
      return (Library) VersionConvertor_40_50.convertResource(res);
    } else if (VersionUtilities.isR5Ver(version)) {
      return (Library) new JsonParser().parse(stream);  
    } else {
      throw new FHIRException("Unknown Version '"+version+"'");      
    }
  }

}
