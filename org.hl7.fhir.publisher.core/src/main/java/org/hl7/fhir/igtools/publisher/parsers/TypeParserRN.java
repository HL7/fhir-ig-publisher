package org.hl7.fhir.igtools.publisher.parsers;

import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.model.Base;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.services.elementmodel.Element;
import org.hl7.fhir.services.renderers.utils.RenderingContext;

import java.io.IOException;

public class TypeParserRN implements RenderingContext.ITypeParser {

    private IWorkerContext context;

    public TypeParserRN(IWorkerContext context) {
        this.context = context;
    }

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
        return new org.hl7.fhir.model.core.formats.XmlParser(context.getModelContext()).parseType(xml, type);
    }

    @Override
    public Base parseType(Element base) throws FHIRFormatError, IOException, FHIRException {
        throw new NotImplementedException();
    }
}
