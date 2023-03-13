package org.hl7.fhir.igtools.publisher;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GitUtilitiesTests {
	@Test
	public void testGetGitStatus() {
		String output = GitUtilities.getGitStatus(Path.of(new File(System.getProperty("user.dir")).getAbsolutePath(), "src/test/resources/git/regular-branch/").toFile());
		assertEquals("main", output.trim());
	}

	@Test
	public void testGetGitWorktreeStatus() {
		String output = GitUtilities.getGitStatus(Path.of(new File(System.getProperty("user.dir")).getAbsolutePath(), "src/test/resources/git/worktree-branch/").toFile());
		assertEquals("branch-a", output.trim());
	}
	@Test
	public void testGitStatusWhenNotGitDirectory() throws IOException {
		String output = GitUtilities.getGitStatus(Files.createTempDirectory("emptyNonGitDirectory").toFile());
		assertTrue(output.length() == 0);
	}
}
