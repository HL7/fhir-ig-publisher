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
     final String[] cmd = { "git", "remote", "get-url", "origin" };
		final String url = execAndReturnString(cmd, new String[]{}, gitDir);
		final String noUserInfoUrl = getURLWithNoUserInfo(url, "git remote origin url");
		return noUserInfoUrl == null ? "" : noUserInfoUrl;
	} catch (Exception e) {
      System.out.println("Warning @ Unable to read the git source: " + e.getMessage().replace("fatal: ", "") );
      return "";
    }
  }

	/**
	 * Return the URL with no user information.
	 * <p/>
	 * This will also send log info to console that reports user information removal, as well as a malformed URL
	 * resulting in a null return.
	 *
	 * @param url The URL
	 * @param urlSource A string representing the source of the URL to be output (CLI param, git remote, etc.)
	 * @return A valid URL that does not contain user information or null if the url param was malformed.
	 */
	protected static String getURLWithNoUserInfo(final String url, final String urlSource) {
		try {
			URL newUrl = new URL(url);
            if (newUrl.getUserInfo() != null) {
				System.out.println("Info @ Removing user info from GIT URL. Source: " + urlSource);
				return new URL(newUrl.getProtocol(), newUrl.getHost(), newUrl.getPort(), newUrl.getFile()).toString();
			}
			return url;
		} catch (MalformedURLException e) {
			System.out.println("Warning @ Git URL is not a valid URl. Source: " + urlSource);
			return null;
		}
	}

}
