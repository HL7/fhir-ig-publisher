package org.hl7.fhir.igtools.publisher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.jaxp.TransformerImpl;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SaxonApiUncheckedException;

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


    /**
     * The stylesheets run here are named by templates (pre-process transforms), and the factory already stops them
     * reading files or fetching anything. But xsl:result-document can still write any file the process can write, and
     * nothing the publisher runs needs anything but the principal output, so it's refused. That needs Saxon (it's
     * how the factory is provided): if the transformer is something else, the stylesheet doesn't run at all
     */
    public byte[] transform(byte[] source, byte[] xslt) throws TransformerException {
        TransformerFactory f = org.hl7.fhir.utilities.xml.XMLUtil.newXXEProtectedTransformerFactory();
        f.setErrorListener(new MyErrorListener());
        StreamSource xsrc = new StreamSource(new ByteArrayInputStream(xslt));
        Transformer t = f.newTransformer(xsrc);
        t.setErrorListener(new MyErrorListener());
        if (t instanceof TransformerImpl) {
            ((TransformerImpl) t).getUnderlyingXsltTransformer().setResultDocumentHandler(uri -> {
                throw new SaxonApiUncheckedException(new SaxonApiException("xsl:result-document is not allowed in this transform (it tried to write to "+uri+")"));
            });
        } else {
            throw new TransformerException("Unable to run the transform: the XSLT processor is "+t.getClass().getName()+", not Saxon, so it can't be stopped from writing files");
        }

        StreamSource src = new StreamSource(new ByteArrayInputStream(source));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StreamResult res = new StreamResult(out);
        t.transform(src, res);
        return out.toByteArray();
    }
}
