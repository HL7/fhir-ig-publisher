package org.hl7.fhir.igtools.publisher.parsers;

import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_50_N;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.model.Base;
import org.hl7.fhir.services.renderers.utils.RenderingContext;

import java.io.IOException;

public class TypeParserR5 implements RenderingContext.ITypeParser {

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
        return VersionConvertorFactory_50_N.convertType(new org.hl7.fhir.r5.formats.XmlParser().parseType(xml, type));
    }

    @Override
    public Base parseType(Element base) throws FHIRFormatError, IOException, FHIRException {
        throw new NotImplementedException();
    }
}
