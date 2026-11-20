package org.hl7.fhir.igtools.web;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.NpmPackageSigner;

/**
 * Signs the packages already published in a set of web folders, and records the signatures in
 * their package feeds (see NpmPackageSigner):
 * <ul>
 *   <li>every .tgz under each folder that is a FHIR package (has package/package.json) gets its
 *   partner file, {package}.tgz.sigstore.json, unless it already has one, by this key, that is
 *   newer than the package. A signature by another key is replaced. Anything that isn't a package
 *   is left alone.</li>
 *   <li>in the folder's package-feed.xml, each item whose package is now signed gets a
 *   fhir:signature element pointing at the partner file. An item's package is found from its link:
 *   the feed's own link (in the channel) says where the folder is on the web, so a link under that
 *   is a file under the folder. Items linking elsewhere are reported, not changed. The feed is
 *   edited as text, so its layout and line endings are kept.</li>
 * </ul>
 * Running it again does nothing more, so it can be stopped and restarted.
 * <p/>
 * Usage: -sign-packages [-key {pem}] [-dry-run] [-map {url}={folder}]... {folder} [{folder}...].
 * Without -key, the key is the one in fhir-settings.json (packageSigningKey). With -dry-run,
 * nothing is written. -map says where a web location that isn't under a feed's own folder is
 * (e.g. a feed that lists packages published elsewhere on the same site); list that folder too,
 * so its packages are signed.
 */
public class PackageSigningUpdater {

  private static final String FEED = "package-feed.xml";
  private static final Pattern ITEM = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL);
  private static final Pattern LINK = Pattern.compile("<link>(.*?)</link>", Pattern.DOTALL);
  private static final Pattern HINT = Pattern.compile("\"hint\"\\s*:\\s*\"([^\"]*)\"");

  private final NpmPackageSigner signer;
  private final boolean dryRun;
  private final PrintStream log;
  private final String tool;

  private int signed;
  private int alreadySigned;
  private int notPackages;
  private int failed;
  private int feedItemsAdded;
  private int feedItemsAlready;
  private final List<String> problems = new ArrayList<>();
  // web location (no scheme, ending in /) -> folder
  private final List<String[]> maps = new ArrayList<>();

  public PackageSigningUpdater(NpmPackageSigner signer, boolean dryRun, PrintStream log, String tool) {
    this.signer = signer;
    this.dryRun = dryRun;
    this.log = log;
    this.tool = tool;
  }

  /** Says that the web location url (a folder) is the local folder */
  public void addMap(String url, File folder) {
    String base = noScheme(url);
    maps.add(new String[] {base.endsWith("/") ? base : base + "/", folder.getAbsolutePath()});
  }

  public static void main(String[] args) throws IOException {
    execute(args, "FHIR IG Publisher");
  }

  /** Runs the -sign-packages command line (see the class comment) */
  public static void execute(String[] args, String tool) throws IOException {
    String key = null;
    boolean dryRun = false;
    List<File> folders = new ArrayList<>();
    List<String> mapArgs = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      if ("-sign-packages".equals(args[i])) {
        // the command itself
      } else if ("-key".equals(args[i]) && i + 1 < args.length) {
        key = args[++i];
      } else if ("-map".equals(args[i]) && i + 1 < args.length) {
        mapArgs.add(args[++i]);
      } else if ("-dry-run".equals(args[i])) {
        dryRun = true;
      } else if (args[i].startsWith("-")) {
        throw new IOException("Unknown parameter " + args[i] + " (usage: -sign-packages [-key {pem}] [-dry-run] {folder}...)");
      } else {
        File f = new File(args[i]);
        if (!f.isDirectory()) {
          throw new IOException("Not a folder: " + args[i]);
        }
        folders.add(f);
      }
    }
    if (folders.isEmpty()) {
      throw new IOException("No folders (usage: -sign-packages [-key {pem}] [-dry-run] {folder}...)");
    }
    NpmPackageSigner signer = key != null ? NpmPackageSigner.load(new File(key)) : NpmPackageSigner.fromSettings();
    if (signer == null) {
      throw new IOException("No signing key: use -key, or set packageSigningKey in fhir-settings.json");
    }
    PackageSigningUpdater updater = new PackageSigningUpdater(signer, dryRun, System.out, tool);
    for (String m : mapArgs) {
      int eq = m.indexOf('=');
      if (eq < 0 || !new File(m.substring(eq + 1)).isDirectory()) {
    throw new IOException("-map must be {url}={folder}, with an existing folder: " + m);
    }
    updater.addMap(m.substring(0, eq), new File(m.substring(eq + 1)));
    }
    updater.execute(folders);
  }

  public void execute(List<File> folders) throws IOException {
    log.println((dryRun ? "Dry run: " : "") + "signing packages with key " + signer.getKeyId());
    // every package first, since a feed can list packages in another of the folders (see -map)
    for (File folder : folders) {
      log.println("== " + folder.getAbsolutePath());
      signPackages(folder);
    }
    for (File folder : folders) {
      File feed = new File(folder, FEED);
      if (feed.exists()) {
        log.println("== " + feed.getAbsolutePath());
        updateFeed(feed, folder);
      }
    }
    log.println();
    log.println((dryRun ? "Would sign " : "Signed ") + signed + ", already signed " + alreadySigned + ", not packages " + notPackages + ", failed " + failed);
    log.println("Feed items " + (dryRun ? "that would get" : "given") + " a signature: " + feedItemsAdded + ", already had one: " + feedItemsAlready);
    if (!problems.isEmpty()) {
      log.println("Problems:");
      for (String p : problems) {
        log.println("  " + p);
      }
    }
  }

  private void signPackages(File folder) throws IOException {
    List<Path> files;
    try (Stream<Path> walk = Files.walk(folder.toPath())) {
      files = walk.filter(p -> p.getFileName().toString().endsWith(".tgz") && Files.isRegularFile(p)).sorted().toList();
    }
    log.println("  " + files.size() + " .tgz files");
    int n = 0;
    for (Path p : files) {
      File f = p.toFile();
      try {
        if (isSignedByThisKey(f)) {
          alreadySigned++;
        } else if (dryRun) {
          if (isPackage(f)) {
            signed++;
          } else {
            notPackages++;
            problems.add("Would not sign, not a package: " + f);
          }
        } else {
          try {
            signer.sign(f, tool);
          signed++;
          } catch (NpmPackageSigner.NotAPackageException e) {
          notPackages++;
          problems.add("Not signed, not a package: " + f + " (" + e.getMessage() + ")");
          }
        }
      } catch (Exception e) {
        failed++;
        problems.add("Failed to sign " + f + ": " + e.getMessage());
      }
      if (++n % 100 == 0) {
        log.println("  ... " + n + " of " + files.size());
      }
    }
  }

  // Signed by this key since the package last changed: nothing to do
  private boolean isSignedByThisKey(File tgz) throws IOException {
    File partner = new File(tgz.getAbsolutePath() + NpmPackageSigner.SIGNATURE_SUFFIX);
    if (!partner.exists() || partner.lastModified() < tgz.lastModified()) {
      return false;
    }
    Matcher m = HINT.matcher(new String(Files.readAllBytes(partner.toPath()), StandardCharsets.UTF_8));
    return m.find() && signer.getKeyId().equals(m.group(1));
  }

  private boolean isPackage(File tgz) {
    try {
      JsonObject pj = NpmPackageSigner.readPackageJson(Files.readAllBytes(tgz.toPath()));
      return pj.asString("name") != null && pj.asString("version") != null;
    } catch (Exception e) {
      return false;
    }
  }

  // --- the feed ----------------------------------------------------------------------------------

  private void updateFeed(File feed, File folder) throws IOException {
    String content = new String(Files.readAllBytes(feed.toPath()), StandardCharsets.UTF_8);
    String nl = content.contains("\r\n") ? "\r\n" : "\n";
    int firstItem = content.indexOf("<item>");
    Matcher cl = LINK.matcher(firstItem < 0 ? content : content.substring(0, firstItem));
    if (!cl.find()) {
      problems.add(feed + ": the channel has no link, so its items can't be matched to files");
      return;
    }
    String feedUrl = unescape(cl.group(1).trim());
    String base = noScheme(feedUrl.substring(0, feedUrl.lastIndexOf('/') + 1));

    StringBuilder out = new StringBuilder();
    Matcher items = ITEM.matcher(content);
    int added = 0;
    while (items.find()) {
      String body = items.group(1);
      String newBody = body;
      Matcher lm = LINK.matcher(body);
      if (body.contains("fhir:signature")) {
        feedItemsAlready++;
      } else if (lm.find()) {
        String link = unescape(lm.group(1).trim());
        if (link.endsWith(".tgz")) {
          File tgz = locate(noScheme(link), base, folder);
          if (tgz == null) {
            problems.add(feed.getName() + " in " + folder + ": " + link + " is not under " + feedUrl + " (or a -map); not signed here");
          } else {
            File partner = new File(tgz.getAbsolutePath() + NpmPackageSigner.SIGNATURE_SUFFIX);
            if (!tgz.exists()) {
              problems.add(feed.getName() + " in " + folder + ": " + link + " - no such file " + tgz);
            } else if (dryRun ? isSignedByThisKey(tgz) || isPackage(tgz) : isSignedByThisKey(tgz)) {
              newBody = insertSignature(body, lineIndent(body, lm.start()), link + NpmPackageSigner.SIGNATURE_SUFFIX, nl);
              added++;
            } else {
              problems.add(feed.getName() + " in " + folder + ": " + link + " is not signed");
            }
          }
        }
      }
      items.appendReplacement(out, Matcher.quoteReplacement("<item>" + newBody + "</item>"));
    }
    items.appendTail(out);
    feedItemsAdded += added;
    String result = out.toString();
    if (added > 0 && !result.contains("xmlns:fhir=")) {
      result = result.replaceFirst("<rss ", Matcher.quoteReplacement("<rss xmlns:fhir=\"http://hl7.org/fhir/feed\" "));
    }
    log.println("  " + FEED + ": " + added + " item" + (added == 1 ? "" : "s") + (dryRun ? " would be" : "") + " given a signature");
    if (added > 0 && !dryRun) {
      Files.write(feed.toPath(), result.getBytes(StandardCharsets.UTF_8));
    }
  }

  // The local file for a link: under the feed's own folder, or a mapped one
  private File locate(String link, String base, File folder) {
    if (link.startsWith(base)) {
      return new File(folder, link.substring(base.length()));
    }
    for (String[] m : maps) {
      if (link.startsWith(m[0])) {
        return new File(m[1], link.substring(m[0].length()));
      }
    }
    return null;
  }

  // The new element goes on its own line before pubDate (or at the end of the item), indented as the link is
  static String insertSignature(String body, String indent, String url, String nl) {
    String line = indent + "<fhir:signature>" + escape(url) + "</fhir:signature>" + nl;
    int pd = body.indexOf("<pubDate>");
    int at = pd >= 0 ? body.lastIndexOf('\n', pd) + 1 : body.lastIndexOf('\n') + 1;
    if (at <= 0) {
      // all on one line
      return body + line;
    }
    return body.substring(0, at) + line + body.substring(at);
  }

  private static String lineIndent(String s, int pos) {
    int start = s.lastIndexOf('\n', pos) + 1;
    int i = start;
    while (i < pos && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) {
      i++;
    }
    return s.substring(start, i);
  }

  private static String noScheme(String url) {
    return url.replaceFirst("^[a-zA-Z]+://", "");
  }

  private static String unescape(String s) {
    return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
