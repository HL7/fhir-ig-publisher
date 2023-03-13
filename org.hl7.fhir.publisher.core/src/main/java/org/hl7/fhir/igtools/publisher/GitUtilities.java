package org.hl7.fhir.igtools.publisher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class GitUtilities {

	protected static String execAndReturnString(String[] cmd, String[] env, File directory) throws IOException, InterruptedException {
		String output = "";
		Process p = Runtime.getRuntime().exec(cmd, null, directory);
		InputStreamReader isr = new InputStreamReader(p.getInputStream());
		p.waitFor();
		BufferedReader br = new BufferedReader(isr);
		String line;
		while ((line = br.readLine()) != null) {
			output += line;
		}
		return output;
	}
	public static String getGitStatus(File gitDir) {
	  if (!gitDir.exists()) {
		return "";
	  }
	  try {
		String[] cmd = { "git", "rev-parse", "--abbrev-ref"," HEAD" };
		return execAndReturnString(cmd, new String[]{}, gitDir);
	  } catch (Exception e) {
		System.out.println("Warning @ Unable to read the git branch: " + e.getMessage() );
		return "";
	  }
	}
}
