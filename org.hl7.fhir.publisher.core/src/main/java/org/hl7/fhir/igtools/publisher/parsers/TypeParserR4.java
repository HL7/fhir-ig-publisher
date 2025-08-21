package org.hl7.fhir.igtools.publisher.parsers;

import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.PublisherBase;
import org.hl7.fhir.igtools.publisher.PublisherFields;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.formats.IParser;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TypeParserR4 implements RenderingContext.ITypeParser {

    private final PublisherFields publisherFields;

    public TypeParserR4(PublisherFields publisherFields) {
        this.publisherFields = publisherFields;
    }

    @Override
    public Base parseType(String xml, String type) throws IOException, FHIRException {
        org.hl7.fhir.r4.model.Type t = new org.hl7.fhir.r4.formats.XmlParser().parseType(xml, type);
        return VersionConvertorFactory_40_50.convertType(t);
    }

    @Override
    public Base parseType(Element base) throws FHIRFormatError, IOException, FHIRException {
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        new org.hl7.fhir.r5.elementmodel.XmlParser(publisherFields.getContext()).compose(base, bs, IParser.OutputStyle.NORMAL, null);
        String xml = new String(bs.toByteArray(), StandardCharsets.UTF_8);
        return parseType(xml, base.fhirType());
    }
}
