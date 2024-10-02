package org.hl7.fhir.igtools.renderers;

import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.PackageList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PublicationCheckerTests {


	public static Stream<Arguments> getNotFoundExceptions() {
		return Stream.of(
				Arguments.of(new Exception("Not Found"), true),
				Arguments.of(new Exception("404"), true),
				Arguments.of(new Exception("meh", new Exception("Not Found")), true),
				Arguments.of(new Exception("meh", new Exception("404")), true),
				Arguments.of(new Exception(), false),
				Arguments.of(new Exception("meh"), false),
				Arguments.of(new Exception("meh", new Exception("also meh")), false)
		);
	}

	@ParameterizedTest
	@MethodSource("getNotFoundExceptions")
	void testExceptionCausedByNotFound(Exception e, boolean causedByNotFound) {
		assertEquals(causedByNotFound, PublicationChecker.exceptionCausedByNotFound(e));

	}
}
