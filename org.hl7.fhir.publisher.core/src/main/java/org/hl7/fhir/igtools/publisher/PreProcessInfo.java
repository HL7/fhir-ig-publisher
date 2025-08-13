package org.hl7.fhir.igtools.publisher;

import lombok.Getter;
import org.hl7.fhir.utilities.FileUtilities;

import java.io.IOException;

public class PreProcessInfo {

    @Getter
    private final String xsltName;
    @Getter
    private final byte[] xslt;
    @Getter
    private final String relativePath;

    public PreProcessInfo(String xsltName, String relativePath) throws IOException {
        this.xsltName = xsltName;
        if (xsltName != null) {
            this.xslt = FileUtilities.fileToBytes(xsltName);
        } else {
            this.xslt = null;
        }
        this.relativePath = relativePath;
    }

    public boolean hasXslt() {
        return xslt != null;
    }

    public boolean hasRelativePath() {
        return relativePath != null && !relativePath.isEmpty();

    }

}
