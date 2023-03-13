package org.hl7.fhir.igtools.publisher;

import java.io.File;

public class GitUtilities {
	public static String getGitStatus(File gitDir) {
	  if (!gitDir.exists()) {
		return "";
	  }
	  try {
		String[] cmd = { "git", "rev-parse", "--abbrev-ref"," HEAD" };
		return Publisher.execAndReturnString(cmd, new String[]{}, gitDir);
	  } catch (Exception e) {
		System.out.println("Warning @ Unable to read the git branch: " + e.getMessage() );
		return "";
	  }
	}
}
