package org.hl7.fhir.igtools.publisher;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hl7.fhir.igtools.publisher.GitUtilities.execAndReturnString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.annotation.Nonnull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GitUtilitiesTests {

	public static final String VALID_URL = "https://github.com/FHIR/fhir-core-examples.git";
	public static final String USERNAME_AND_TOKEN_URL = "https://username:token@github.com/FHIR/fhir-core-examples.git";
	public static final String NORMAL_BRANCH = "normal-branch";
	public static final String WORKTREE_BRANCH = "branch-a";

	public File initiateGitDirectory() throws IOException, InterruptedException {
		File gitRoot = Files.createTempDirectory("testGitDirectory").toFile();

		File normalBranchDirectory = getGitBranchDirectory(gitRoot, NORMAL_BRANCH);
		normalBranchDirectory.mkdir();

		System.out.println(execAndReturnString(new String[]{"git", "init"}, null, normalBranchDirectory));

		//git config --global user.email "you@example.com"
		System.out.println(execAndReturnString(new String[]{"git", "config", "user.email", "\"dummy@dummy.org\""}, null, normalBranchDirectory));

		System.out.println(execAndReturnString(new String[]{"git", "config", "user.name", "\"guyincognito\""}, null, normalBranchDirectory));

		File dummyFile = getGitBranchDirectory(normalBranchDirectory, "dummy.txt");
		dummyFile.createNewFile();

		BufferedWriter writer = new BufferedWriter(new FileWriter(dummyFile));
		writer.write("dummy content");

		writer.close();

		System.out.println(execAndReturnString(new String[]{"git", "add", "--all"},null, normalBranchDirectory));
		System.out.println(execAndReturnString(new String[]{"git", "commit", "-m", "test"},null, normalBranchDirectory));
		System.out.println(execAndReturnString(new String[]{"git", "branch", WORKTREE_BRANCH}, null, normalBranchDirectory));

		System.out.println(execAndReturnString(new String[]{"git", "checkout", "-b", "branch-b"}, null, normalBranchDirectory));
		return gitRoot;
	}

	private static @NotNull File getGitBranchDirectory(File gitRoot, String branchName) {
		return Path.of(gitRoot.getAbsolutePath(), branchName).toFile();
	}

	private static @Nonnull File createWorktree(File gitRoot) throws IOException, InterruptedException {
		Path worktreeBranchPath = Path.of(gitRoot.getAbsolutePath(), WORKTREE_BRANCH);
		System.out.println(execAndReturnString(new String[]{"git", "worktree", "add", worktreeBranchPath.toString(), WORKTREE_BRANCH}, null, getGitBranchDirectory(gitRoot, NORMAL_BRANCH)));
		return worktreeBranchPath.toFile();
	}



	private static void createOriginURL(File gitRoot, String urlString) throws IOException, InterruptedException {
		System.out.println(execAndReturnString(new String[]{"git", "remote", "add", "origin", urlString }, null, getGitBranchDirectory(gitRoot, NORMAL_BRANCH)));
	}

	@Test
	public void testGetGitStatus() throws IOException, InterruptedException {
		File gitRoot = initiateGitDirectory();
		File normalBranchDirectory = getGitBranchDirectory(gitRoot, NORMAL_BRANCH);
		String output = GitUtilities.getGitStatus(normalBranchDirectory);
		assertEquals("branch-b", output.trim());
	}

	@Test
	public void testGetGitWorktreeStatus() throws IOException, InterruptedException {
		File gitRoot = initiateGitDirectory();
		File worktreeBranchDirectory = createWorktree(gitRoot);
		String output = GitUtilities.getGitStatus(worktreeBranchDirectory);
		assertEquals(WORKTREE_BRANCH, output.trim());
	}
	@Test
	public void testGitStatusWhenNotGitDirectory() throws IOException {
		String output = GitUtilities.getGitStatus(Files.createTempDirectory("emptyNonGitDirectory").toFile());
		assertTrue(output.length() == 0);
	}

	@ParameterizedTest
	@CsvSource(value= {
			VALID_URL+","+VALID_URL,
			"whwehwhasdlksdjsdf,''",
			USERNAME_AND_TOKEN_URL+","+ VALID_URL})
	void testGetGitSource(String url, String expected) throws IOException, InterruptedException {
		File gitRoot = initiateGitDirectory();
		createOriginURL(gitRoot, url);
		String actual = GitUtilities.getGitSource(getGitBranchDirectory(gitRoot, NORMAL_BRANCH));
		assertThat(actual).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource(value= {
			VALID_URL+","+VALID_URL,
			"whwehwhasdlksdjsdf,NULL",
			USERNAME_AND_TOKEN_URL+","+VALID_URL}, nullValues={"NULL"})
	public void testGetURLWithNoUserInfo(String url, String expected) {
		String result = GitUtilities.getURLWithNoUserInfo(url, "test");
		assertThat(result).isEqualTo(expected);
	}
}
