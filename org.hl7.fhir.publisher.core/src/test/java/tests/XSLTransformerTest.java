package tests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.xml.transform.TransformerException;

import org.apache.commons.io.IOUtils;
import org.hl7.fhir.igtools.publisher.XSLTransformer;
import org.junit.jupiter.api.Test;

public class XSLTransformerTest {
    @Test
    public void testNormalTransformSucceeds() throws IOException, TransformerException {
        byte[] source = getLineSeparatorNormalizedBytes("/xslt/unicom-index.xml");
        byte[] transform = getLineSeparatorNormalizedBytes("/xslt/unicom-transform.xslt");

        XSLTransformer xslTransformer = new XSLTransformer(false);
        byte[] actual = xslTransformer.transform(source, transform);
        System.out.println(new String(actual, StandardCharsets.UTF_8));
        byte[] expected = getLineSeparatorNormalizedBytes("/xslt/unicom-expected.xml");

        assertArrayEquals(actual, expected);
    }

    @Test
    void testEvilXMLThrowsException() throws IOException {
        byte[] source = getLineSeparatorNormalizedBytes("/xslt/unicom-index-evil.xml");
        byte[] transform = getLineSeparatorNormalizedBytes("/xslt/unicom-transform.xslt");

        TransformerException exception = assertThrows(TransformerException.class, () -> {
            XSLTransformer xslTransformer = new XSLTransformer(false);
            xslTransformer.transform(source, transform);
        } );

        assertThat(exception.getMessage()).contains("External Entity");
    }

    private byte[] getLineSeparatorNormalizedBytes(String fileName) throws IOException {
        return new String(IOUtils.toByteArray(this.getClass().getResource(fileName))).replace(System.lineSeparator(), "\n").getBytes();
    }
}
