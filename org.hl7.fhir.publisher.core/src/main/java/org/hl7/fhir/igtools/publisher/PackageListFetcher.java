package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.PackageList;

/**
 * Fetches the package-list.json of published IGs. In watch mode, each one is only fetched once per session (whether it
 * was found or not), instead of once per build: a rebuild doesn't wait for the web, and sees the same list as the
 * build before it.
 */
public class PackageListFetcher {

  private static volatile Map<String, Object> remembered; // url -> json text, or the exception fetching it threw

  private PackageListFetcher() {
  }

  /** From now on, each package list is fetched once, and remembered for the rest of the session */
  public static void rememberFetches() {
    if (remembered == null) {
      remembered = new ConcurrentHashMap<>();
    }
  }

  public static PackageList fromUrl(String url) throws IOException {
    Map<String, Object> memo = remembered;
    if (memo == null) {
      return PackageList.fromUrl(url);
    }
    Object o = memo.get(url);
    if (o == null) {
      try {
        o = JsonParser.compose(JsonParser.parseObjectFromUrl(url), false);
      } catch (IOException | RuntimeException e) {
        o = e;
      }
      memo.put(url, o);
    }
    if (o instanceof IOException) {
      throw (IOException) o;
    } else if (o instanceof RuntimeException) {
      throw (RuntimeException) o;
    }
    // a new PackageList each time, so a caller can't change what the next one sees
    return new PackageList(JsonParser.parseObject((String) o)).setSource(url);
  }
}
