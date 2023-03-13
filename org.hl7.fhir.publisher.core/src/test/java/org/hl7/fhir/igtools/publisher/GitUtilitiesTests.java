package org.hl7.fhir.igtools.publisher;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GitUtilitiesTests {
	@Test
	public void testGetGitStatus() {
		String output = GitUtilities.getGitStatus(new File(System.getProperty("user.dir")));
		assertTrue(output.length() > 0);
	}

	@Test
	public void testGitStatusWhenNotGitDirectory() throws IOException {
		String output = GitUtilities.getGitStatus(Files.createTempDirectory("emptyNonGitDirectory").toFile());
		assertTrue(output.length() == 0);
	}
}
