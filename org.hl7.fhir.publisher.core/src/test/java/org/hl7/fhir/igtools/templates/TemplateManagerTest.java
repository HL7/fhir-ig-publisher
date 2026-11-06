package org.hl7.fhir.igtools.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/**
 * The local folders a template is loaded from (getLocalFolders()), which watch mode watches: they are known also when
 * the template can't be loaded, because fixing it is a change that needs a rebuild
 */
class TemplateManagerTest {

  @TempDir
  Path dir;

  private void write(String path, String content) throws IOException {
    Path p = dir.resolve(path);
    Files.createDirectories(p.getParent());
    Files.write(p, content.getBytes(StandardCharsets.UTF_8));
  }

  private TemplateManager manager() {
    return new TemplateManager(null, Mockito.mock(ILoggingService.class));
  }

  private String folder(String path) {
    return dir.resolve(path).toFile().getAbsolutePath();
  }

  /** the load failed because package.json is broken, not for some other reason */
  private void assertBrokenJson(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t.getMessage() != null && t.getMessage().contains("Error parsing JSON")) {
        return;
      }
    }
    fail("not a JSON error: " + e);
  }

  @Test
  void aTemplateUsedInPlaceIsALocalFolder() throws IOException {
    write("template/config.json", "{}");
    TemplateManager tm = manager();
    tm.loadTemplate("#template", dir.toString(), "x.y", false, false, false);
    assertEquals(Collections.singletonList(folder("template")), tm.getLocalFolders());
  }

  @Test
  void aLocalTemplateWithABrokenPackageIsStillALocalFolder() throws IOException {
    write("my-template/package/package.json", "{ not json");
    TemplateManager tm = manager();
    assertBrokenJson(assertThrows(Exception.class, () -> tm.loadTemplate("#my-template", dir.toString(), "x.y", false, false, false)));
    assertEquals(Collections.singletonList(folder("my-template")), tm.getLocalFolders());
  }

  @Test
  void anOverrideWithABrokenPackageIsStillALocalFolder() throws IOException {
    write("override/package/package.json", "{ not json");
    write("ig.ini", "[IG]\nig = input/ig.xml\ntemplate = my.template#1.0.0\nmy.template = override\n");
    TemplateManager tm = manager();
    tm.loadTemplateOverrides(new IniFile(dir.resolve("ig.ini").toString()), dir.toString());
    assertBrokenJson(assertThrows(Exception.class, () -> tm.loadTemplate("my.template#1.0.0", dir.toString(), "x.y", false, false, false)));
    assertEquals(Collections.singletonList(folder("override")), tm.getLocalFolders());
  }
}
