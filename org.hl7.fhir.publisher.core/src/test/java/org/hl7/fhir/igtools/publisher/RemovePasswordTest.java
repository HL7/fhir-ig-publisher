package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RemovePasswordTest {

  @ParameterizedTest
  @CsvSource({
    "-repo, https://user:ghp_secret@github.com/org/repo.git, https://github.com/org/repo.git",
    "-repo, https://ghp_secret@github.com/org/repo, https://github.com/org/repo",
    "-repo, HTTP://u:p@example.org/x, HTTP://example.org/x",
    "-repo, https://github.com/org/repo, https://github.com/org/repo",
    "-repo, git@github.com:org/repo.git, git@github.com:org/repo.git",
    "-ig, /work/my@ig/ig.ini, /work/my@ig/ig.ini",
    "-target, https://build.fhir.org/ig/org/repo/branches/a@b, https://build.fhir.org/ig/org/repo/branches/a@b",
    "-proxy-password, secret, XXXXXX",
    "-proxy, user:secret@proxy.example.org:8080, proxy.example.org:8080",
    "-proxy, user:@proxy.example.org:8080, proxy.example.org:8080",
    "-proxy, proxy.example.org:8080, proxy.example.org:8080",
    "-proxy, user@proxy.example.org:8080, user@proxy.example.org:8080",
    "-ig, C:\\work\\my@ig\\ig.ini, C:\\work\\my@ig\\ig.ini",
    // an @ in the password
    "-repo, https://user:p@ss@github.com/org/repo, https://github.com/org/repo",
    "-repo, https://user:p@ss@github.com/org/repo@x, https://github.com/org/repo@x",
    "-proxy, user:p@ss@proxy.example.org:8080, proxy.example.org:8080",
    "-x, git@github.com:org/repo@x, git@github.com:org/repo@x",
    "-x, user:p@ss@host:8080/path@x, host:8080/path@x",
    "-x, user:p@ss@host:8080/path, host:8080/path",
    "-x, a:b c@d, a:b c@d",
  })
  void masksCredentials(String name, String value, String expected) {
    assertEquals(expected, Publisher.removePassword(new String[] {name, value}, 1));
  }

  @ParameterizedTest
  @CsvSource({"-ig", "https://u:p@example.org"})
  void firstArgument(String value) {
    assertEquals(value.replace("u:p@", ""), Publisher.removePassword(new String[] {value}, 0));
  }
}
