package org.hl7.fhir.igtools.publisher;

import org.jetbrains.annotations.NotNull;

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
     final String[] cmd = { "git", "remote", "get-url", "origin" };
		final String url = execAndReturnString(cmd, new String[]{}, gitDir);
		final String noUserInfoUrl = getURLIfNoUserInfo(url, "git remote origin url");
		return noUserInfoUrl == null ? "" : noUserInfoUrl;
	} catch (Exception e) {
      System.out.println("Warning @ Unable to read the git source: " + e.getMessage().replace("fatal: ", "") );
      return "";
    }
  }

	/**
	 * Return the URL if it is a valid URL that does not contain user information. Otherwise return null.
	 * <p/>
	 * This will also send log info to console reporting the reason for null returns.
	 *
	 * @param url The URL
	 * @param urlSource A string representing the source of the URL to be output
	 * @return A valid URL that does not contain user information, or null.
	 */
	protected static String getURLIfNoUserInfo(final String url, final String urlSource) {
		try {
			URL newUrl = new URL(url);
			boolean result = newUrl.getUserInfo() != null;
			if (result) {
				System.out.println("Warning @ Git URL contains user information. Source: " + urlSource);
				return null;
			}

		} catch (MalformedURLException e) {
			System.out.println("Warning @ Git URL is not a valid URl. Source: " + urlSource);
			return null;
		}
		return url;
	}

}
