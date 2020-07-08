package org.hl7.fhir.igtools.publisher;

import org.junit.Test;

import static org.junit.Assert.*;

public class PublisherTest {

    @Test
    public void testIGVer() {
        Publisher.fetchCurrentIGPubVersionStatic();
    }

}