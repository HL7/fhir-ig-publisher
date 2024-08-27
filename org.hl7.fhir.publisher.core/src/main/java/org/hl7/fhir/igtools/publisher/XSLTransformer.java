package org.hl7.fhir.igtools.publisher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public class XSLTransformer {
    private final boolean debug;

    public XSLTransformer(boolean debug) {
        this.debug = debug;
    }

    public class MyErrorListener implements ErrorListener {

        @Override
        public void error(TransformerException arg0) throws TransformerException {
            System.out.println("XSLT Error: "+arg0.getMessage());
            if (debug) {
                arg0.printStackTrace();
            }
        }

        @Override
        public void fatalError(TransformerException arg0) throws TransformerException {
            System.out.println("XSLT Error: "+arg0.getMessage());
            if (debug) {
                arg0.printStackTrace();
            }
        }

        @Override
        public void warning(TransformerException arg0) throws TransformerException {
            System.out.println("XSLT Warning: "+arg0.getMessage());
            if (debug) {
                arg0.printStackTrace();
            }
        }
    }


    public byte[] transform(byte[] source, byte[] xslt) throws TransformerException {
        TransformerFactory f = org.hl7.fhir.utilities.xml.XMLUtil.newXXEProtectedTransformerFactory();
        f.setErrorListener(new MyErrorListener());
        StreamSource xsrc = new StreamSource(new ByteArrayInputStream(xslt));
        Transformer t = f.newTransformer(xsrc);
        t.setErrorListener(new MyErrorListener());

        StreamSource src = new StreamSource(new ByteArrayInputStream(source));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StreamResult res = new StreamResult(out);
        t.transform(src, res);
        return out.toByteArray();
    }
}
