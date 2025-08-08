package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.exec.CommandLine;
import org.hl7.fhir.r5.context.ILoggingService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
				Arguments.of(PublisherUtils.IGBuildMode.PUBLICATION, null, "bash -c sushi --require-latest . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.AUTOBUILD, null, "bash -c sushi --require-latest . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.MANUAL, null, "bash -c sushi . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.PUBLICATION, "1.2.3", "bash -c npx fsh-sushi@1.2.3 --require-latest . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.AUTOBUILD, "1.2.3", "bash -c npx fsh-sushi@1.2.3 --require-latest . -o ."),
				Arguments.of(PublisherUtils.IGBuildMode.MANUAL, "1.2.3", "bash -c npx fsh-sushi@1.2.3 . -o .")
		);
		return output.stream();
	}
	@ParameterizedTest
	@MethodSource("npmPathExecStringParams")
	public void testNpmPathExecString(PublisherUtils.IGBuildMode mode, String fshVersion, String expectedExecString) {
		FSHRunner fshRunner = new FSHRunner(Mockito.mock(ILoggingService.class));
		final CommandLine actualCommandLine = fshRunner.getNpmPathCommandLine(fshVersion, mode);
		assertIsEqual(CommandLine.parse(expectedExecString), actualCommandLine);
	}
}
