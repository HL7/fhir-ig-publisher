package org.hl7.fhir.igtools.publisher;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.hl7.fhir.igtools.publisher.GitUtilities.execAndReturnString;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GitUtilitiesTests {

	File normalBranchDirectory;
	File worktreeBranchDirectory;
	@BeforeAll
	public void beforeAll() throws IOException, InterruptedException {
		File gitRoot = Files.createTempDirectory("testGitDirectory").toFile();

		normalBranchDirectory = Path.of(gitRoot.getAbsolutePath(),"normal-branch").toFile();
		normalBranchDirectory.mkdir();

		Path worktreeBranchPath = Path.of(gitRoot.getAbsolutePath(), "branch-a");

		System.out.println(execAndReturnString(new String[]{"git", "init"}, null, normalBranchDirectory));
		System.out.println(execAndReturnString(new String[]{"touch", "dummy.txt"}, null, normalBranchDirectory));

		System.out.println(execAndReturnString(new String[]{"git", "add", "./dummy.txt"},null, normalBranchDirectory));
		System.out.println(execAndReturnString(new String[]{"git", "commit", "-m", "test"},null, normalBranchDirectory));
		System.out.println(execAndReturnString(new String[]{"git", "branch", "branch-a"}, null, normalBranchDirectory));
		System.out.println(execAndReturnString(new String[]{"git", "worktree", "add", worktreeBranchPath.toString(), "branch-a"}, null, normalBranchDirectory));

		worktreeBranchDirectory = worktreeBranchPath.toFile();
	}
	@Test
	public void testGetGitStatus() {
		String output = GitUtilities.getGitStatus(normalBranchDirectory);
		assertEquals("main", output.trim());
	}

	@Test
	public void testGetGitWorktreeStatus() {
		String output = GitUtilities.getGitStatus(worktreeBranchDirectory);
		assertEquals("branch-a", output.trim());
	}
	@Test
	public void testGitStatusWhenNotGitDirectory() throws IOException {
		String output = GitUtilities.getGitStatus(Files.createTempDirectory("emptyNonGitDirectory").toFile());
		assertTrue(output.length() == 0);
	}
}
