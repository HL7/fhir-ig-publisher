package org.hl7.fhir.igtools.publisher.parsers;

import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_43_N;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.model.Base;
import org.hl7.fhir.model.utilities.formats.OutputStyle;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.services.renderers.utils.RenderingContext;

import java.io.IOException;

public class TypeParserR4B implements RenderingContext.ITypeParser {

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
        org.hl7.fhir.r4b.model.DataType t = new org.hl7.fhir.r4b.formats.XmlParser().parseType(xml, type);
        return VersionConvertorFactory_43_N.convertType(t);
    }

    @Override
    public Base parseType(Element base) throws FHIRFormatError, IOException, FHIRException {
        throw new NotImplementedException();
    }
}
