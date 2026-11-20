package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * On Windows, jekyll and sushi are run through cmd, which looks in the current folder for a command (e.g. jekyll.bat)
 * before the PATH unless NoDefaultCurrentDirectoryInExePath is set
 */
class NoCurrentFolderLookupTest {

  @Test
  void cmdIsToldNotToLookInTheCurrentFolder() {
    Map<String, String> env = new HashMap<>();
    env.put("PATH", "C:\\ruby\\bin");
    env.put("JEKYLL_NO_BUNDLER_REQUIRE", "true");
    Map<String, String> res = PublisherUtils.noCurrentFolderLookup(env);
    assertEquals("1", res.get("NoDefaultCurrentDirectoryInExePath"));
    // the rest of the environment is passed on, and what it was made from is left as it is
    assertEquals("C:\\ruby\\bin", res.get("PATH"));
    assertEquals("true", res.get("JEKYLL_NO_BUNDLER_REQUIRE"));
    assertFalse(env.containsKey("NoDefaultCurrentDirectoryInExePath"));
  }
}
