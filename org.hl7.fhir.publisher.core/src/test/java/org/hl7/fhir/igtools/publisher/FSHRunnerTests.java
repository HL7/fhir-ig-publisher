package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.exec.CommandLine;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;


public class FSHRunnerTests {



	public static Stream<Arguments> defaultExecStringParams () {
		List<Arguments> output = List.of(
				Arguments.of(PublisherUtils.IGBuildMode.PUBLICATION, null, "sushi --require-latest . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.AUTOBUILD, null, "sushi --require-latest . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.MANUAL, null, "sushi . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.PUBLICATION, "1.2.3", "npx fsh-sushi@1.2.3 --require-latest . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.AUTOBUILD, "1.2.3", "npx fsh-sushi@1.2.3 --require-latest . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.MANUAL, "1.2.3", "npx fsh-sushi@1.2.3 . -o .")
		);
		return output.stream();
	}

	@ParameterizedTest
	@MethodSource("defaultExecStringParams")
	public void testDefaultExecString(PublisherUtils.IGBuildMode mode, String fshVersion, String expectedExecString) {
		FSHRunner fshRunner = new FSHRunner(Mockito.mock(ILoggingService.class));
		final CommandLine actualCommandLine = fshRunner.getDefaultCommandLine(fshVersion, mode);
		assertIsEqual(CommandLine.parse(expectedExecString), actualCommandLine);
	}

	public static Stream<Arguments> sushiCommandParams () {
		List<Arguments> output = List.of(
				Arguments.of(PublisherUtils.IGBuildMode.PUBLICATION, null, "sushi --require-latest"),
				Arguments.of(PublisherUtils.IGBuildMode.AUTOBUILD, null, "sushi --require-latest"),
				Arguments.of(PublisherUtils.IGBuildMode.MANUAL, null, "sushi"),
				Arguments.of(PublisherUtils.IGBuildMode.PUBLICATION, "1.2.3", "npx fsh-sushi@1.2.3 --require-latest"),
				Arguments.of(PublisherUtils.IGBuildMode.AUTOBUILD, "1.2.3", "npx fsh-sushi@1.2.3 --require-latest"),
				Arguments.of(PublisherUtils.IGBuildMode.MANUAL, "1.2.3", "npx fsh-sushi@1.2.3")
		);
		return output.stream();
	}

	@ParameterizedTest
	@MethodSource("sushiCommandParams")
	public void testGetSushiCommand(PublisherUtils.IGBuildMode mode, String fshVersion, String expectedSushiCommand) {
		FSHRunner fshRunner = new FSHRunner(Mockito.mock(ILoggingService.class));
		assertEquals(expectedSushiCommand, fshRunner.getSushiCommandString(fshVersion,mode));
	}

	private void assertIsEqual(CommandLine a, CommandLine b) {
		assertEquals(a.getExecutable(), b.getExecutable());
		assertArrayEquals(a.getArguments(), b.getArguments());
	}

	public static Stream<Arguments> windowsExecStringParams () {
		List<Arguments> output = List.of(
				Arguments.of(PublisherUtils.IGBuildMode.PUBLICATION, null, "cmd /C sushi --require-latest . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.AUTOBUILD, null, "cmd /C sushi --require-latest . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.MANUAL, null, "cmd /C sushi . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.PUBLICATION, "1.2.3", "cmd /C npx fsh-sushi@1.2.3 --require-latest . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.AUTOBUILD, "1.2.3", "cmd /C npx fsh-sushi@1.2.3 --require-latest . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.MANUAL, "1.2.3", "cmd /C npx fsh-sushi@1.2.3 . -o .")
		);
		return output.stream();
	}
	@ParameterizedTest
	@MethodSource("windowsExecStringParams")
	public void testWindowsExecString(PublisherUtils.IGBuildMode mode, String fshVersion, String expectedExecString) {
		FSHRunner fshRunner = new FSHRunner(Mockito.mock(ILoggingService.class));
		final CommandLine actualCommandLine = fshRunner.getWindowsCommandLine(fshVersion, mode);
		assertIsEqual(CommandLine.parse(expectedExecString), actualCommandLine);
	}

	public static Stream<Arguments> npmPathExecStringParams () {
		List<Arguments> output = List.of(
				Arguments.of(PublisherUtils.IGBuildMode.PUBLICATION, null, "sushi --require-latest . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.AUTOBUILD, null, "sushi --require-latest . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.MANUAL, null, "sushi . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.PUBLICATION, "1.2.3", "npx fsh-sushi@1.2.3 --require-latest . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.AUTOBUILD, "1.2.3", "npx fsh-sushi@1.2.3 --require-latest . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.MANUAL, "1.2.3", "npx fsh-sushi@1.2.3 . -o .")
		);
		return output.stream();
	}
	@ParameterizedTest
	@MethodSource("npmPathExecStringParams")
	public void testNpmPathExecString(PublisherUtils.IGBuildMode mode, String fshVersion, String expectedCommand) {
		FSHRunner fshRunner = new FSHRunner(Mockito.mock(ILoggingService.class));
		final CommandLine actualCommandLine = fshRunner.getNpmPathCommandLine(fshVersion, mode);
		// bash runs a fixed script; the sushi command follows as separate arguments ($0 and $@), which the shell never reads
		assertEquals("bash", actualCommandLine.getExecutable());
		List<String> expected = new ArrayList<>(List.of("-c", "exec \"$0\" \"$@\""));
		expected.addAll(List.of(expectedCommand.split(" ")));
		assertArrayEquals(expected.toArray(new String[0]), actualCommandLine.getArguments());
	}

	@ParameterizedTest
	@ValueSource(strings = {"3.16.0", "1.2.3", "3.0.0-beta.1", "2.10.12-rc1"})
	void aSushiVersionIsAPlainVersionNumber(String version) {
		assertTrue(FSHRunner.isValidSushiVersion(version));
	}

	/** the version comes from the IG's fsh.ini, and npm (and cmd on Windows) read far more than a version from it */
	@ParameterizedTest
	@ValueSource(strings = {"latest", "npm:evil-package@1.0.0", "github:someone/sushi", "https://example.org/sushi.tgz",
			"1.2.3 & calc", "1.2.3; touch x", "$(touch x)", "1.2", "file:../sushi", "1.2.3 --registry=https://example.org"})
	void anythingElseIsRefused(String version) {
		assertFalse(FSHRunner.isValidSushiVersion(version));
		FSHRunner fshRunner = new FSHRunner(Mockito.mock(ILoggingService.class));
		assertThrows(FHIRException.class, () -> fshRunner.getDefaultCommandLine(version, PublisherUtils.IGBuildMode.AUTOBUILD));
		assertThrows(FHIRException.class, () -> fshRunner.getWindowsCommandLine(version, PublisherUtils.IGBuildMode.AUTOBUILD));
		assertThrows(FHIRException.class, () -> fshRunner.getNpmPathCommandLine(version, PublisherUtils.IGBuildMode.AUTOBUILD));
	}
}
