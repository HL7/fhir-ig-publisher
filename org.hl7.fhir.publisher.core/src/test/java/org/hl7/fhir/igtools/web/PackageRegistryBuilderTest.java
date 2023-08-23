package org.hl7.fhir.igtools.web;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

public class PackageRegistryBuilderTest {
	@Test
	public void testThrowsIllegalArgumentWhenPathMissing() throws IOException {
		IllegalArgumentException thrown =  Assertions.assertThrows(IllegalArgumentException.class, () -> { PackageRegistryBuilder packageRegistryBuilder = new PackageRegistryBuilder("src/test/resources/package-registry/missing-path/");

		packageRegistryBuilder.update("dummyPath",null ); });

		System.out.println(thrown);
	}
}
