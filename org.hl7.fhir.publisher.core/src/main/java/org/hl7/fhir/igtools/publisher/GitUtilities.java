package org.hl7.fhir.igtools.publisher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

public class GitUtilities {

	protected static String execAndReturnString(String[] cmd, String[] env, File directory) throws IOException, InterruptedException {

		Process p = Runtime.getRuntime().exec(cmd, null, directory);
		int result = p.waitFor();

		if (result == 0) {
			return toString(p.getInputStream());
		}

		String error = toString(p.getErrorStream());
		throw new RuntimeException(error);
	}

	private static String toString(InputStream inputStream) throws InterruptedException, IOException {
		String output = "";
		InputStreamReader isr = new InputStreamReader(inputStream);
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
      String[] cmd = { "git", "branch", "--show-current" };
	    return execAndReturnString(cmd, new String[]{}, gitDir);
	  } catch (Exception e) {
	    System.out.println("Warning @ Unable to read the git branch: " + e.getMessage().replace("fatal: ", "") );
	    return "";
	  }
	}



  public static String getGitSource(File gitDir) {
    if (!gitDir.exists()) {
      return "";
    }
    try {
      String[] cmd = { "git", "remote", "get-url", "origin" };
		String url = execAndReturnString(cmd, new String[]{}, gitDir);
		try {
			URL newUrl = new URL(url);
			if (newUrl.getUserInfo() != null) {
				System.out.println("Warning @ Git URL contains user information. Please check your git remote origin URL.");
				return "";
			}

		} catch (MalformedURLException e) {
			System.out.println("Warning @ Git URL is not a valid URl. Please check your git remote origin URL.");
			return "";
		}
		return url;
    } catch (Exception e) {
      System.out.println("Warning @ Unable to read the git source: " + e.getMessage().replace("fatal: ", "") );
      return "";
    }
  }
  
}
