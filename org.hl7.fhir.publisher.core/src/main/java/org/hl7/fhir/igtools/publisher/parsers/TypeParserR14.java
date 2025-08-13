package org.hl7.fhir.igtools.publisher.parsers;

import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_14_50;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;

import java.io.IOException;

public class TypeParserR14 implements RenderingContext.ITypeParser {

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
        org.hl7.fhir.dstu2016may.model.Type t = new org.hl7.fhir.dstu2016may.formats.XmlParser().parseType(xml, type);
        return VersionConvertorFactory_14_50.convertType(t);
    }

    @Override
    public Base parseType(Element base) throws FHIRFormatError, IOException, FHIRException {
        throw new NotImplementedException();
    }
}
