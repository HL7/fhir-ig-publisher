package org.hl7.fhir.igtools.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A publication builds in a copy of the IG. The copy leaves out .git: clearDirectory never empties the root of a git
 * repository, so the next publication of the same IG couldn't clear the copy
 */
class PublicationProcessCloneTest {

  @TempDir
  Path dir;

  @Test
  void theCopyLeavesOutGitAndCanBeMadeAgain() throws IOException {
    Path source = Files.createDirectories(dir.resolve("ig"));
    Files.writeString(source.resolve("ig.ini"), "[IG]");
    Files.createDirectories(source.resolve("input"));
    Files.writeString(source.resolve("input/page.md"), "page");
    Files.createDirectories(source.resolve(".git/objects"));
    Files.writeString(source.resolve(".git/HEAD"), "ref: refs/heads/main");
    Path temp = Files.createDirectories(dir.resolve("temp"));

    File copy = PublicationProcess.cloneToTemp(temp.toString(), source.toFile(), "example.ig#1.0.0");
    assertEquals("page", Files.readString(copy.toPath().resolve("input/page.md")));
    assertFalse(Files.exists(copy.toPath().resolve(".git")));

    // the next publication of the same IG
    Files.writeString(source.resolve("input/page.md"), "page 2");
    copy = PublicationProcess.cloneToTemp(temp.toString(), source.toFile(), "example.ig#1.0.0");
    assertEquals("page 2", Files.readString(copy.toPath().resolve("input/page.md")));
    assertFalse(Files.exists(copy.toPath().resolve(".git")));
  }
}
