package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Template scripts run ant's xslt over files the IG supplies. Loading the Publisher class switches off external
 * entities for the whole JVM, so such a file can't read local files into the output.
 *
 * Each case runs in a JVM of its own: whether Saxon honours the setting depends on what has parsed XML in the JVM
 * before it was set, so it can't be tested reliably in the shared test JVM
 */
class ExternalEntityProtectionTest {

  @TempDir
  Path dir;

  @Test
  void anIgFileCannotReadALocalFileThroughATemplateXslt() throws Exception {
    // the parser refuses the entity, which fails the ant target (even with failonerror="false")
    Path out = runInNewJvm(true);

    assertFalse(Files.exists(out) && Files.readString(out).contains("SECRET"), Files.exists(out) ? Files.readString(out) : "(no output)");
  }

  /** the same transform, without the Publisher loaded, does read the file: the test would notice the protection going */
  @Test
  void withoutThePublisherTheSameTransformReadsTheFile() throws Exception {
    Path out = runInNewJvm(false);

    assertTrue(Files.readString(out).contains("SECRET"), Files.readString(out));
  }

  private Path runInNewJvm(boolean loadPublisher) throws IOException, InterruptedException {
    Files.writeString(dir.resolve("secret.txt"), "SECRET");
    Files.writeString(dir.resolve("menu.xml"), "<!DOCTYPE x [<!ENTITY s SYSTEM \"" + dir.resolve("secret.txt").toUri() + "\">]><x>&s;</x>");
    Files.writeString(dir.resolve("copy.xslt"), "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
        + "<xsl:output method=\"text\"/><xsl:template match=\"/\">[<xsl:value-of select=\".\"/>]</xsl:template></xsl:stylesheet>");
    Files.writeString(dir.resolve("build.xml"), "<project name=\"test\" default=\"go\"><target name=\"go\">"
        + "<xslt in=\"menu.xml\" out=\"out.txt\" style=\"copy.xslt\" failonerror=\"false\"/></target></project>");

    List<String> cmd = new ArrayList<>();
    cmd.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
    cmd.add("-cp");
    cmd.add(System.getProperty("java.class.path"));
    cmd.add(RunAntXslt.class.getName());
    cmd.add(dir.toString());
    cmd.add(Boolean.toString(loadPublisher));
    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(p.waitFor(2, TimeUnit.MINUTES), "child JVM timed out: " + log);
    return dir.resolve("out.txt");
  }

  /** runs in the child JVM: as a publisher run does it, the Publisher class first, then a template's ant xslt */
  public static class RunAntXslt {
    public static void main(String[] args) throws Exception {
      if (Boolean.parseBoolean(args[1])) {
        Class.forName(Publisher.class.getName());
      }
      Project project = new Project();
      project.init();
      File buildFile = new File(args[0], "build.xml");
      ProjectHelper.configureProject(project, buildFile);
      project.setBasedir(args[0]);
      project.executeTarget("go");
    }
  }
}
