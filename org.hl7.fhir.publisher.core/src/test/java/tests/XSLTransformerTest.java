package tests;

import org.apache.commons.io.IOUtils;
import org.hl7.fhir.igtools.publisher.XSLTransformer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.xml.transform.TransformerException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class XSLTransformerTest {
    @Test
    public void test() throws IOException, TransformerException {
        byte[] source = getLineSeparatorNormalizedBytes("/xslt/unicom-index.xml");
        byte[] transform = getLineSeparatorNormalizedBytes("/xslt/unicom-transform.xslt");

        XSLTransformer XSLTransformer = new XSLTransformer(false);
        byte[] actual = XSLTransformer.transform(source, transform);
        byte[] expected = getLineSeparatorNormalizedBytes("/xslt/unicom-expected.xml");

        assertArrayEquals(actual, expected);
        Assertions.fail();
    }

    private byte[] getLineSeparatorNormalizedBytes(String fileName) throws IOException {
        return new String(IOUtils.toByteArray(this.getClass().getResource(fileName))).replace(System.lineSeparator(), "\n").getBytes();
    }
}
