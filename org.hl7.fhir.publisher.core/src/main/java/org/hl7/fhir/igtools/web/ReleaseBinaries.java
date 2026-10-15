package org.hl7.fhir.igtools.web;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;

/**
 * Support for releases whose binaries are not published with the website.
 *
 * When the publication request has a binaries-location, the release binaries (full-ig.zip and the package files)
 * are moved out of the version folder into a staging folder, from where they are uploaded to the binaries location
 * (typically the assets of a GitHub release). binaries-location is recorded on the version's entry in
 * package-list.json, and whatever links to the binaries of a version uses it instead of the version's path.
 */
public class ReleaseBinaries {

  public static final String PROPERTY = "binaries-location";

  private static final Pattern GITHUB_RELEASE = Pattern.compile("^https://github\\.com/([^/]+)/([^/]+)/releases/download/([^/]+)/?$");

  /**
   * @return the binaries-location of a package-list.json version entry, or null if its binaries are published with it
   */
  public static String location(JsonObject version) {
    return version.has(PROPERTY) ? version.asString(PROPERTY) : null;
  }

  public static String location(PackageListEntry version) {
    return location(version.json());
  }

  /**
   * @return the URL that the binaries of a version are found at
   */
  public static String base(PackageListEntry version) {
    String location = location(version);
    return location != null ? location : version.path();
  }

  /**
   * @return null if binaries-location is acceptable for a publication at path, or else the problem with it
   */
  public static String checkLocation(String location, String path) {
    if (!location.startsWith("https://")) {
      return "The binaries-location '"+location+"' must be an absolute https URL";
    }
    if (location.endsWith("/")) {
      return "The binaries-location '"+location+"' must not end with a '/'";
    }
    if (path != null && (location.equals(path) || location.startsWith(path+"/"))) {
      return "The binaries-location '"+location+"' is inside the publication path '"+path+"', so the binaries would still be published with the website";
    }
    return null;
  }

  public static boolean isGitHubRelease(String location) {
    return GITHUB_RELEASE.matcher(location).matches();
  }

  /**
   * Move the binaries of a release out of its folder in the website and into a staging folder
   *
   * @return the names of the files moved
   */
  public static List<String> stage(String versionFolder, String stagingFolder, boolean isCore) throws IOException {
    if (new File(stagingFolder).exists()) {
      FileUtilities.clearDirectory(stagingFolder);
    } else {
      FileUtilities.createDirectory(stagingFolder);
    }
    List<String> names = new ArrayList<>();
    for (File f : new File(versionFolder).listFiles()) {
      if (f.isFile() && (f.getName().equals(isCore ? "fhir-spec.zip" : "full-ig.zip") || f.getName().endsWith(".tgz"))) {
        names.add(f.getName());
      }
    }
    for (String name : names) {
      FileUtils.moveFileToDirectory(new File(Utilities.path(versionFolder, name)), new File(stagingFolder), false);
    }
    return names;
  }

  /**
   * The pages of the release link to its binaries relative to the page, or using the version path. Point those
   * links at the binaries location instead
   *
   * @return the number of pages changed
   */
  public static int relink(String versionFolder, String path, String location, List<String> names) throws IOException {
    int count = 0;
    for (File f : new File(versionFolder).listFiles()) {
      if (f.isFile() && (f.getName().endsWith(".html") || f.getName().endsWith(".htm"))) {
        String src = FileUtilities.fileToString(f);
        String o = src;
        for (String name : names) {
          String target = "href=\""+Utilities.pathURL(location, name)+"\"";
          src = src.replace("href=\""+name+"\"", target);
          src = src.replace("href=\""+Utilities.pathURL(path, name)+"\"", target);
        }
        if (!src.equals(o)) {
          FileUtilities.stringToFile(src, f);
          count++;
        }
      }
    }
    return count;
  }

  /**
   * @return what the person publishing needs to do with the staged binaries
   */
  public static String instructions(String stagingFolder, String location, List<String> names) {
    StringBuilder b = new StringBuilder();
    b.append("The release binaries are staged in "+stagingFolder+": "+String.join(", ", names)+"\r\n");
    b.append("Upload them to "+location+" before the website is updated, so that the links to them work when it is published");
    Matcher m = GITHUB_RELEASE.matcher(location);
    if (m.matches()) {
      b.append(" (the release must exist and be published, not a draft - gh release create "+m.group(3)+" --repo "+m.group(1)+"/"+m.group(2)+"):\r\n");
      b.append("  cd \""+stagingFolder+"\"\r\n");
      b.append("  gh release upload "+m.group(3)+" "+String.join(" ", names)+" --repo "+m.group(1)+"/"+m.group(2));
    }
    return b.toString();
  }
}
