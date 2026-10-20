package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.PackageList;

/**
 * Fetches the package-list.json of published IGs. In watch mode, each one that was found is only fetched once per
 * session, instead of once per build: a rebuild doesn't wait for the web, and sees the same list as the build before
 * it. One that couldn't be fetched is tried again by the next build, as a fresh build would.
 */
public class PackageListFetcher {

  private static volatile Map<String, String> remembered; // url -> json text

  private PackageListFetcher() {
  }

  /** From now on, each package list is fetched once, and remembered until forgetFetches() */
  public static void rememberFetches() {
    if (remembered == null) {
      remembered = new ConcurrentHashMap<>();
    }
  }

  /** Back to fetching each package list every time it's needed */
  public static void forgetFetches() {
    remembered = null;
  }

  public static PackageList fromUrl(String url) throws IOException {
    Map<String, String> memo = remembered;
    if (memo == null) {
      return PackageList.fromUrl(url);
    }
    String json = memo.get(url);
    if (json == null) {
      json = JsonParser.compose(JsonParser.parseObjectFromUrl(url), false);
      memo.put(url, json);
    }
    // a new PackageList each time, so a caller can't change what the next one sees
    return new PackageList(JsonParser.parseObject(json)).setSource(url);
  }
}
