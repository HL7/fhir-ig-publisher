package org.hl7.fhir.igtools.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Which template scripts stop a watch mode rebuild from treating a page edit as a page-only change: all of them, or
 * those in the folders the scripts read
 */
class TemplateScriptPageUseTest {

  @TempDir
  Path dir;

  private static Set<String> folders(String src) {
    return Template.scriptFolderUse("onGenerate.xml", src).keySet();
  }

  @Test
  void aScriptThatOnlyReadsGeneratedContentIsFine() {
    String src = "<project><target name=\"onGenerate\"><copy todir=\"${ig.temp}/_data\"><fileset dir=\"${ig.temp}/_includes\"/></copy></target></project>";
    assertNull(Template.scriptPageUse("onGenerate.xml", src));
    assertTrue(folders(src).isEmpty());
  }

  @Test
  void definingAPropertyFromTheInputFolderIsFine() {
    String src = "<project><property name=\"in\" value=\"${folder.input}\"/><target name=\"onGenerate\"/></project>";
    assertNull(Template.scriptPageUse("onGenerate.xml", src));
    assertTrue(folders(src).isEmpty());
  }

  @Test
  void aScriptThatNamesAPageFolderReadsIt() {
    assertNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"input/pagecontent\"/>"));
    assertEquals(Set.of("pagecontent"), folders("<fileset dir=\"input/pagecontent\"/>"));
    assertEquals(Set.of("intro-notes"), folders("<fileset dir=\"${x}/intro-notes\"/>"));
    assertEquals(Set.of("translations"), folders("<fileset dir=\"input/translations\"/>"));
  }

  @Test
  void aScriptThatReadsTheInputFolderBlocksAll() {
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${folder.input}\"/>"));
    assertNull(Template.scriptPageUse("onGenerate.xml", "<copy file=\"${dir.input}/images/logo.png\" todir=\"x\"/>"));
    assertEquals(Set.of("images"), folders("<copy file=\"${dir.input}/images/logo.png\" todir=\"x\"/>"));
    assertEquals(Set.of("data"), folders("<copy file=\"${dir.input}/data/x.json\" todir=\"x\"/>"));
  }

  @Test
  void aScriptThatReadsTheInputFolderFromTheRoot() {
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${ig.root}/input\"/>"));
    assertNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${basedir}/input/includes\"/>"));
    assertEquals(Set.of("includes"), folders("<fileset dir=\"${basedir}/input/includes\"/>"));
    String cache = "<property name=\"x\" value=\"${ig.root}/input-cache/schemas\"/><copy todir=\"${ig.root}/input-cache\"/>";
    assertNull(Template.scriptPageUse("onGenerate.xml", cache));
    assertTrue(folders(cache).isEmpty());
  }

  @Test
  void aScriptThatReadsTheInputFolderThroughAProperty() {
    assertNotNull(Template.scriptPageUse("onGenerate.xml",
        "<project><property name=\"in\" value=\"${folder.input}\"/><fileset dir=\"${in}\"/></project>"));
    String menu = "<project><property name=\"in\" location=\"${ig.root}/input\"/><copy file=\"${in}/includes/menu.xml\" todir=\"x\"/></project>";
    assertNull(Template.scriptPageUse("onGenerate.xml", menu));
    assertEquals(Set.of("includes"), folders(menu));
    String data = "<project><property name=\"in\" value=\"${folder.input}\"/><copy file=\"${in}/data/x.json\" todir=\"x\"/></project>";
    assertNull(Template.scriptPageUse("onGenerate.xml", data));
    assertEquals(Set.of("data"), folders(data));
  }

  @Test
  void dotsAreTheInputFolderOrTheIgsFolder() {
    for (String src : new String[] {"<fileset dir=\"${dir.input}/.\"/>", "<fileset dir=\"${dir.input}/../pages\"/>",
        "<fileset dir=\"${ig.root}/input/..\"/>", "<fileset dir=\"input/../pages\"/>", "<property name=\"r\" value=\"input/..\"/>",
        "<copy file=\"${dir.input}/includes/../images/logo.png\" todir=\"x\"/>", "<fileset dir=\"input/includes/./..\"/>"}) {
      assertNotNull(Template.scriptPageUse("onGenerate.xml", src), src);
    }
    assertTrue(folders("<fileset dir=\"${dir.input}/.\"/>").isEmpty());
    // through a property defined as a folder in the input folder
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<property name=\"inc\" value=\"${dir.input}/includes\"/><fileset dir=\"${inc}/..\"/>"));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<property name=\"inc\" location=\"input/includes\"/><copy file=\"${inc}/../pagecontent/a.md\" todir=\"x\"/>"));
    assertNull(Template.scriptPageUse("onGenerate.xml", "<property name=\"inc\" value=\"${dir.input}/includes\"/><fileset dir=\"${inc}/menus\"/>"));
  }

  @Test
  void theFolderEndsAtAQuoteOrABracket() {
    // the base template's onLoad transform names the folders it adds to the IG
    assertEquals(Set.of("images"), folders("<xsl:with-param name=\"value\" select=\"'input/images'\"/>"));
    assertEquals(Set.of("data"), folders("<available file=\"${basedir}/input/data\" type=\"dir\"/>(input/data)"));
  }

  @Test
  void aFolderThatCantBeToldBlocksAll() {
    // a property or a wildcard could be any folder
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${dir.input}/${folder.pages}\"/>"));
    assertTrue(folders("<fileset dir=\"${dir.input}/${folder.pages}\"/>").isEmpty());
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${dir.input}/inc*\"/>"));
    assertTrue(folders("<fileset dir=\"${dir.input}/inc*\"/>").isEmpty());
    assertNull(Template.scriptPageUse("onGenerate.xml", "<copy file=\"${dir.input}/data/x.json;${dir.input}/data/y.json\" todir=\"x\"/>"));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${dir.input}/incl?des\"/>"));
    assertTrue(folders("<fileset dir=\"${dir.input}/incl?des\"/>").isEmpty());
    assertEquals("the template's script onGenerate.xml reads input/inc*", Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"input/inc*\"/>"));
    assertTrue(folders("<fileset dir=\"input/inc*\"/>").isEmpty());
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"input/incl?des\"/>"));
  }

  @Test
  void theInputFolderByItsPath() {
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"input\"/>"));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"input/\"/>"));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\".\" includes=\"input/**\"/>"));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"input/${x}\"/>"));
    assertNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"input-cache\"/>"));
    // the base template defines it
    String base = "<property name=\"folder.input\" value=\"input\"/><copy file=\"${folder.input}/data/x.json\" todir=\"x\"/>";
    assertNull(Template.scriptPageUse("onGenerate.xml", base));
    assertEquals(Set.of("data"), folders(base));
    String in = "<property name=\"in\" value=\"input\"/><copy file=\"${in}/includes/menu.xml\" todir=\"x\"/>";
    assertNull(Template.scriptPageUse("onGenerate.xml", in));
    assertEquals(Set.of("includes"), folders(in));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<property name=\"in\" value=\"input\"/><fileset dir=\"${in}\"/>"));
  }

  private Template template(String targets) throws IOException {
    write("template/config.json", "{\"script\": \"scripts/ant.xml\", \"targets\": {" + targets + "}}");
    write("template/scripts/ant.xml", "<project name=\"t\"><target name=\"onGenerate\"><copy file=\"${dir.input}/includes/menu.xml\" todir=\"x\"/></target>"
        + "<target name=\"onJekyll\"/></project>");
    return new Template(dir.toString(), true, null, null, false, false, false);
  }

  private void write(String path, String content) throws IOException {
    Path p = dir.resolve(path);
    Files.createDirectories(p.getParent());
    Files.write(p, content.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void theScriptsInFoldersAndTheAntFileWhereverItIs() throws IOException {
    write("template/config.json", "{\"script\": \"build/ant.xml\", \"targets\": {\"onGenerate\": \"onGenerate\"}}");
    write("template/build/ant.xml", "<project name=\"t\"><target name=\"onGenerate\"><import file=\"../scripts/more/x.xml\"/>"
        + "<copy file=\"${dir.input}/includes/menu.xml\" todir=\"x\"/></target></project>");
    write("template/scripts/more/x.xml", "<project name=\"x\"><copy file=\"${dir.input}/images/logo.png\" todir=\"x\"/></project>");
    Template t = new Template(dir.toString(), true, null, null, false, false, false);
    assertEquals(Set.of("includes", "images"), t.pageFolderBlockers().keySet());
    assertNull(t.pageOnlyRebuildBlocker());
    // an ant file that isn't read
    write("template/build/ant.xml", "<project name=\"t\"><import file=\"common.xml\"/><target name=\"onGenerate\"/></project>");
    write("template/build/common.xml", "<project name=\"c\"/>");
    t = new Template(dir.toString(), true, null, null, false, false, false);
    assertEquals("the template's script build/ant.xml runs common.xml, which isn't read", t.pageOnlyRebuildBlocker());
    // other builds, which aren't read either
    for (String run : new String[] {"<ant antfile=\"x.xml\"/>", "<ant dir=\"x\"/>", "<subant><fileset dir=\"x\"/></subant>",
        "<import resource=\"x.xml\"/>", "<exec executable=\"python\"><arg value=\"menu.py\"/></exec>", "<apply executable=\"x\"/>",
        "<taskdef name=\"x\" classname=\"y\"/>"}) {
      write("template/build/ant.xml", "<project name=\"t\"><target name=\"onGenerate\">" + run + "</target></project>");
      t = new Template(dir.toString(), true, null, null, false, false, false);
      assertTrue(t.pageOnlyRebuildBlocker().startsWith("the template's script build/ant.xml runs another build or a program"), run);
    }
  }

  @Test
  void theStyleSheetsAndScriptsThatTheAntFileUses() throws IOException {
    write("template/config.json", "{\"script\": \"scripts/ant.xml\", \"targets\": {\"onGenerate\": \"onGenerate\"}}");
    write("template/scripts/s.xslt", "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"/>");
    write("template/xslt/x.xslt", "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"/>");
    String[][] uses = {{"${ig.scripts}/s.xslt", null}, {"${ig.template}/xslt/x.xslt", "uses ${ig.template}/xslt/x.xslt"},
        {"${ig.scripts}/gone.xslt", "uses ${ig.scripts}/gone.xslt"},
        // a path in the IG's folder: nothing there reads nothing, but a file there isn't read
        {"{$ig.scripts}/s.xslt", null}, {"template/xslt/x.xslt", "uses template/xslt/x.xslt"}};
    for (String[] u : uses) {
      write("template/scripts/ant.xml", "<project name=\"t\"><target name=\"onGenerate\"><xslt in=\"a.xml\" out=\"b.xml\" style=\"" + u[0]
          + "\"/></target></project>");
      Template t = new Template(dir.toString(), true, null, null, false, false, false);
      assertEquals(u[1] == null ? null : "the template's script scripts/ant.xml " + u[1] + ", which isn't read", t.pageOnlyRebuildBlocker(), u[0]);
    }
  }

  @Test
  void thePropertyFilesTheScriptsLoad() throws IOException {
    write("template/config.json", "{\"script\": \"scripts/ant.xml\", \"targets\": {\"onGenerate\": \"onGenerate\"}}");
    write("template/build.properties", "# where the pages are\npages = ${dir.input}/pagecontent\n");
    write("build.properties", "x=y");
    String copy = "<copy todir=\"${ig.temp}/x\"><fileset dir=\"${pages}\"/></copy>";
    String[][] loads = {
        // one in the template is read, with its properties
        {"<property file=\"${ig.template}/build.properties\"/>", null},
        {"<property name=\"props\" value=\"${ig.template}/build.properties\"/><loadproperties srcFile=\"${props}\"/>", null},
        // one the scripts write while the build runs (as the base templates do)
        {"<loadproperties srcfile=\"${ig.template}/versions.txt\"/>", null},
        {"<property file=\"build.properties\"/>", "loads properties from build.properties"},
        {"<property file=\"${somewhere}/build.properties\"/>", "loads properties from ${somewhere}/build.properties"},
        {"<property url=\"http://example.org/build.properties\"/>", "loads properties from http://example.org/build.properties"},
        {"<loadproperties resource=\"build.properties\"/>", "loads properties from build.properties"},
        {"<xmlproperty file=\"input/ig.xml\"/>", "loads properties from input/ig.xml"},
        {"<scriptdef name=\"x\" language=\"javascript\" src=\"${ig.template}/x.js\"/>", "uses ${ig.template}/x.js"}};
    for (String[] l : loads) {
      write("template/scripts/ant.xml", "<project name=\"t\"><target name=\"onGenerate\">" + l[0] + (l[1] == null ? copy : "") + "</target></project>");
      Template t = new Template(dir.toString(), true, null, null, false, false, false);
      assertEquals(l[1] == null ? null : "the template's script scripts/ant.xml " + l[1] + ", which isn't read", t.pageOnlyRebuildBlocker(), l[0]);
      if (l[1] == null && !l[0].contains("versions")) {
        assertEquals(Set.of("pagecontent"), t.pageFolderBlockers().keySet(), l[0]);
      }
    }
  }

  @Test
  void codeInTheScripts() {
    // what the base templates run
    String langs = "<![CDATA[\n  langstring = project.getProperty( \"langs\" );\n  langs = langstring.split(\";\");\n"
        + "  for (i=0;i<langs.length;i++) {\n    if (langs[i]!=\"\") {\n      self.project.setProperty(\"lang\", langs[i]);\n"
        + "      self.project.executeTarget(\"onGenerate.processIncludes.lang\");\n    }\n  }\n]]>";
    assertNull(Template.scriptCode("ant.xml", "javascript", langs, ""));
    assertNull(Template.scriptCode("ant.xml", "javascript", "<attribute name=\"files\"/>\n self.project.setProperty(\"onGenerate.files\", "
        + "self.project.getProperty(\"onGenerate.files\").concat(\";\").concat(attributes.get(\"files\")));", ""));
    // code that reads files, or names the input folder or the IG's
    String[][] codes = {{"var f = new java.io.File(project.getProperty(\"x\"));", "may read files (new)"},
        {"<![CDATA[ var s = Packages.java.nio.file.Files.readString(p); ]]>", "may read files (Packages)"},
        {"var t = project.createTask(\"copy\");", "may read files (createTask)"},
        {"var b = project.getBaseDir();", "may read files (getBaseDir)"},
        {"var d = project.getProperty('dir.input');", "uses the property dir.input"},
        {"var d = project.getProperty(\"ig.root\") + \"/x\";", "uses the property ig.root"},
        {"self.project.setProperty(\"in\", self.project.getProperty(\"folder.input\"));", "uses the property folder.input"},
        {"<classpath path=\"x.jar\"/>run();", "may read files (classpath)"},
        {"var x = load(&quot;y.js&quot;);", "may read files (load)"}};
    for (String[] c : codes) {
      assertEquals("the template's script ant.xml runs code that " + c[1] + ", which isn't checked", Template.scriptCode("ant.xml", "javascript", c[0], ""), c[0]);
    }
    assertEquals("the template's script ant.xml runs code that uses the property here, which isn't checked",
        Template.scriptCode("ant.xml", "javascript", "x = project.getProperty(\"here\");", "<property name=\"here\" value=\"${ig.root}\"/>"));
    assertEquals("the template's script ant.xml runs code (groovy), which isn't checked", Template.scriptCode("ant.xml", "groovy", "println 1", ""));
    assertEquals("the template's script ant.xml runs code (in a language it doesn't name), which isn't checked", Template.scriptCode("ant.xml", "", "x", ""));
  }

  @Test
  void aPropertyFileInTheScriptsFolder() throws IOException {
    write("template/config.json", "{\"script\": \"scripts/ant.xml\", \"targets\": {\"onGenerate\": \"onGenerate\"}}");
    write("template/scripts/x.properties", "root=${ig.root}\n");
    write("template/scripts/ant.xml", "<project name=\"t\"><target name=\"onGenerate\"><property file=\"${ig.scripts}/x.properties\"/>"
        + "<copy todir=\"${ig.temp}/md\"><fileset dir=\"${root}\" includes=\"**/*.md\"/></copy></target></project>");
    Template t = new Template(dir.toString(), true, null, null, false, false, false);
    assertEquals("the template's script scripts/ant.xml reads everything under the IG's folder", t.pageOnlyRebuildBlocker());
  }

  @Test
  void anAntFileByAnotherName() throws IOException {
    write("template/config.json", "{\"script\": \"build.ant\", \"targets\": {\"onGenerate\": \"onGenerate\"}}");
    write("template/build.ant", "<project name=\"t\"><target name=\"onGenerate\"><groovy>println 1</groovy></target></project>");
    Template t = new Template(dir.toString(), true, null, null, false, false, false);
    assertEquals("the template's script build.ant runs code (groovy), which isn't checked", t.pageOnlyRebuildBlocker());
  }

  @Test
  void codeInATemplate() throws IOException {
    write("template/config.json", "{\"script\": \"scripts/ant.xml\", \"targets\": {\"onGenerate\": \"onGenerate\"}}");
    String[][] runs = {{"<script language=\"javascript\">project.setProperty(\"a\", \"b\");</script>", null},
        {"<script language=\"javascript\"><![CDATA[ var f = new java.io.File(\"input\"); ]]></script>", "runs code that may read files (new)"},
        {"<scriptdef name=\"x\" language=\"beanshell\"><attribute name=\"a\"/>print(1);</scriptdef>", "runs code (beanshell)"},
        {"<groovy>new File('input').eachFile { }</groovy>", "runs code (groovy)"},
        {"<script language=\"javascript\" src=\"${ig.scripts}/x.js\"/>", "runs code that may read files (new)"}};
    for (String[] r : runs) {
      write("template/scripts/ant.xml", "<project name=\"t\"><target name=\"onGenerate\">" + r[0] + "</target></project>");
      Files.deleteIfExists(dir.resolve("template/scripts/x.js"));
      if (r[0].contains("x.js")) {
        write("template/scripts/x.js", "var f = new java.io.File(\"input\");");
      }
      Template t = new Template(dir.toString(), true, null, null, false, false, false);
      String blocker = t.pageOnlyRebuildBlocker();
      assertEquals(r[1] == null ? null : "the template's script " + (r[0].contains("x.js") ? "scripts/x.js " : "scripts/ant.xml ") + r[1] + ", which isn't checked", blocker, r[0]);
    }
  }

  @Test
  void aPathWithBackslashes() throws IOException {
    write("template/config.json", "{\"script\": \"ant.xml\", \"targets\": {\"onLoad\": \"onLoad\"}}");
    write("template/ant.xml", "<project name=\"t\"><target name=\"onLoad\"><copy file=\"input\\includes\\menu.xml\" todir=\"x\"/></target></project>");
    Template t = new Template(dir.toString(), true, null, null, false, false, false);
    assertNull(t.pageOnlyRebuildBlocker());
    assertEquals(Set.of("includes"), t.pageFolderBlockers().keySet());
  }

  @Test
  void theIgFolderByAnotherName() {
    // a property defined as the IG's folder
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<property name=\"root\" value=\"${ig.root}\"/><fileset dir=\"${root}\" includes=\"**/*.md\"/>"));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<property name=\"root\" location=\".\"/><property name=\"r\" value=\"${root}/\"/>"
        + "<fileset dir=\"${r}\"/>"));
    String menu = "<property name=\"root\" value=\"${ig.root}\"/><copy file=\"${root}/input/includes/menu.xml\" todir=\"x\"/>";
    assertNull(Template.scriptPageUse("onGenerate.xml", menu));
    assertEquals(Set.of("includes"), folders(menu));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<property name=\"root\" value=\"${ig.root}\"/><fileset dir=\"${root}/input\"/>"));
    // fsh-generated, which pages come from too
    String fsh = "<xslt basedir=\"${ig.root}\" destdir=\"x\" style=\"s.xslt\"><include name=\"fsh-generated/includes/menu.xml\"/></xslt>";
    assertNull(Template.scriptPageUse("onGenerate.xml", fsh));
    assertEquals(Set.of("includes"), folders(fsh));
    assertEquals(Set.of("includes"), folders("<copy file=\"${ig.root}/fsh-generated/includes/menu.xml\" todir=\"x\"/>"));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${ig.root}/fsh-generated\"/>"));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<copy todir=\"x\"><fileset dir=\"fsh-generated\"/></copy>"));
    // a path that goes up out of a folder in the IG's folder
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${ig.temp}/..\" includes=\"**/*.md\"/>"));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${ig.root}/temp/..\" includes=\"**/*.md\"/>"));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"temp/..\" includes=\"**/*.md\"/>"));
    // but not one out of the IG's folder
    assertNull(Template.scriptPageUse("onGenerate.xml", "<xmlcatalog><dtd location=\"${ig.root}/../schemas/x.dtd\"/></xmlcatalog>"));
    // the template's folder is in the IG's folder
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${ig.template}/..\" includes=\"**/*.md\"/>"));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<copy file=\"${ig.scripts}/../../input/includes/menu.xml\" todir=\"x\"/>"));
    assertNull(Template.scriptPageUse("onGenerate.xml", "<copy file=\"${ig.scripts}/..x/menu.xml\" todir=\"x\"/>"));
  }

  @Test
  void twoScriptsWithTheSameFileName() throws IOException {
    write("template/config.json", "{\"script\": \"ant.xml\", \"targets\": {\"onGenerate\": \"onGenerate\"}}");
    write("template/ant.xml", "<project name=\"t\"><target name=\"onGenerate\"><copy file=\"${dir.input}/includes/menu.xml\" todir=\"x\"/></target></project>");
    write("template/scripts/ant.xml", "<project name=\"x\"/>");
    Template t = new Template(dir.toString(), true, null, null, false, false, false);
    assertEquals(Map.of("includes", "the template's script ant.xml reads (${dir.input}/includes)"), t.pageFolderBlockers());
  }

  @Test
  void aPropertyDefinedInAnotherScript() throws IOException {
    write("template/config.json", "{\"script\": \"scripts/ant.xml\", \"targets\": {\"onGenerate\": \"onGenerate\"}}");
    write("template/scripts/ant.xml", "<project name=\"t\"><property name=\"in\" value=\"${folder.input}\"/><import file=\"x.xml\"/>"
        + "<target name=\"onGenerate\"/></project>");
    write("template/scripts/x.xml", "<project name=\"x\"><target name=\"x\"><copy file=\"${in}/includes/menu.xml\" todir=\"x\"/></target></project>");
    Template t = new Template(dir.toString(), true, null, null, false, false, false);
    assertNull(t.pageOnlyRebuildBlocker());
    assertEquals(Map.of("includes", "the template's script scripts/x.xml reads (${in}/includes)"), t.pageFolderBlockers());
    write("template/scripts/x.xml", "<project name=\"x\"><target name=\"x\"><fileset dir=\"${in}\"/></target></project>");
    t = new Template(dir.toString(), true, null, null, false, false, false);
    assertEquals("the template's script scripts/x.xml reads ${in}", t.pageOnlyRebuildBlocker());
  }

  @Test
  void theAntFileWithoutAFolderOfScripts() throws IOException {
    write("template/config.json", "{\"script\": \"ant.xml\", \"targets\": {\"onLoad\": \"onLoad\"}}");
    write("template/ant.xml", "<project name=\"t\"><target name=\"onLoad\"><copy file=\"${dir.input}/includes/menu.xml\" todir=\"x\"/></target></project>");
    Template t = new Template(dir.toString(), true, null, null, false, false, false);
    assertNull(t.pageOnlyRebuildBlocker());
    assertEquals(Map.of("includes", "the template's script ant.xml reads (${dir.input}/includes)"), t.pageFolderBlockers());
  }

  @Test
  void theTasksOutsideTheTargetsWhenNoneRunBeforeJekyll() throws IOException {
    // ant runs them when it loads the template's ant file
    write("template/config.json", "{\"script\": \"scripts/ant.xml\", \"targets\": {\"onCheck\": \"onCheck\"}}");
    write("template/scripts/ant.xml", "<project name=\"t\"><loadfile property=\"m\" srcfile=\"${dir.input}/includes/menu.xml\" failonerror=\"false\"/>"
        + "<target name=\"onCheck\"><copy file=\"${dir.input}/images/logo.png\" todir=\"x\"/></target></project>");
    Template t = new Template(dir.toString(), true, null, null, false, false, false);
    assertNull(t.pageOnlyRebuildBlocker());
    assertEquals(Set.of("includes"), t.pageFolderBlockers().keySet());
    write("template/scripts/ant.xml", "<project name=\"t\"><pathconvert property=\"p\"><fileset dir=\"${dir.input}\" erroronmissingdir=\"false\"/></pathconvert>"
        + "<target name=\"onCheck\"/></project>");
    t = new Template(dir.toString(), true, null, null, false, false, false);
    assertEquals("the template's script scripts/ant.xml reads ${dir.input}", t.pageOnlyRebuildBlocker());
    // with the properties from a property file it loads there
    write("template/scripts/ant.xml", "<project name=\"t\"><property file=\"${ig.scripts}/t.properties\"/>"
        + "<loadfile property=\"m\" srcfile=\"${pages}/x.md\" failonerror=\"false\"/><target name=\"onCheck\"/></project>");
    write("template/scripts/t.properties", "pages=${dir.input}/pagecontent");
    t = new Template(dir.toString(), true, null, null, false, false, false);
    assertNull(t.pageOnlyRebuildBlocker());
    assertEquals(Set.of("pagecontent"), t.pageFolderBlockers().keySet());
    Files.delete(dir.resolve("template/scripts/t.properties"));
    // a target in a comment is not one
    write("template/scripts/ant.xml", "<project name=\"t\"><!-- <target name=\"old\"> --><pathconvert property=\"p\">"
        + "<fileset dir=\"${dir.input}\" erroronmissingdir=\"false\"/></pathconvert><target name=\"onCheck\"/></project>");
    t = new Template(dir.toString(), true, null, null, false, false, false);
    assertEquals("the template's script scripts/ant.xml reads ${dir.input}", t.pageOnlyRebuildBlocker());
    // and those it doesn't run: nothing
    write("template/scripts/ant.xml", "<project name=\"t\"><target name=\"onCheck\"><fileset dir=\"${dir.input}\"/></target><target name=\"x\"/></project>");
    t = new Template(dir.toString(), true, null, null, false, false, false);
    assertNull(t.pageOnlyRebuildBlocker());
    assertTrue(t.pageFolderBlockers().isEmpty());
  }

  @Test
  void aLongStringInCode() {
    String code = "var s = \"" + "a\\\"".repeat(50000) + "\";";
    assertNull(Template.scriptCode("x.js", "javascript", code, ""));
  }

  @Test
  void theFoldersTheTemplatesScriptsRead() throws IOException {
    Template t = template("\"onGenerate\": \"onGenerate\"");
    assertNull(t.pageOnlyRebuildBlocker());
    assertEquals(Map.of("includes", "the template's script scripts/ant.xml reads (${dir.input}/includes)"), t.pageFolderBlockers());
    // onLoad runs before Jekyll too
    t = template("\"onLoad\": \"onGenerate\"");
    assertNull(t.pageOnlyRebuildBlocker());
    assertEquals(Map.of("includes", "the template's script scripts/ant.xml reads (${dir.input}/includes)"), t.pageFolderBlockers());
    // nothing runs before Jekyll
    t = template("\"onCheck\": \"onGenerate\"");
    assertNull(t.pageOnlyRebuildBlocker());
    assertTrue(t.pageFolderBlockers().isEmpty());
    // something runs just before Jekyll: that blocks all
    t = template("\"onGenerate\": \"onGenerate\", \"onJekyll\": \"onJekyll\"");
    assertNotNull(t.pageOnlyRebuildBlocker());
    assertTrue(t.pageFolderBlockers().isEmpty());
  }

  @Test
  void howTheScriptReadsTheFolder() {
    // the translated base template reads the menu for its QA check, and its own translations
    Map<String, String> use = Template.scriptFolderUse("ant.xml",
        "<property name=\"dir.input\" value=\"${ig.root}/${folder.input}\"/>"
            + "<xslt in=\"${dir.input}/includes/menu.xml\" out=\"${ig.template}/x.json\"/>"
            + "<property name=\"dir.template.translations\" value=\"${ig.template}/translations\"/>");
    assertEquals(Set.of("includes"), use.keySet());
    assertEquals("the template's script ant.xml reads (${dir.input}/includes)", use.get("includes"));
  }

  @Test
  void aScriptThatReadsEverythingUnderTheRootBlocksAll() {
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${ig.root}\" includes=\"**/*.md\"/>"));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${basedir}\"><include name=\"**/*.md\"/></fileset>"));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<copy todir=\"x\"><fileset dir=\"${ig.root}\"/></copy>"));
    assertNull(Template.scriptPageUse("onGenerate.xml", "<copy file=\"x\" todir=\"${ig.root}\"/>"));
    // a wildcard first folder may be the input folder
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${ig.root}\"><include name=\"*/includes/menu.xml\"/></fileset>"));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${ig.root}\" includes=\"a.xml, in?ut/includes/menu.xml\"/>"));
    assertNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${ig.root}\"><include name=\"${dir.temp.includes}/*.svg\"/></fileset>"));
    // an xslt task's basedir is a folder it reads
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<xslt basedir=\"${ig.root}\" destdir=\"x\" style=\"s.xslt\"/>"));
    String menu = "<xslt basedir=\"${ig.root}\" destdir=\"x\" style=\"s.xslt\"><include name=\"input/includes/menu.xml\"/></xslt>";
    assertNull(Template.scriptPageUse("onGenerate.xml", menu));
    assertEquals(Set.of("includes"), folders(menu));
    // but not the project's
    assertNull(Template.scriptPageUse("onGenerate.xml", "<project name=\"t\" basedir=\".\"><target name=\"x\"/></project>"));
    // the scripts run in the IG's folder
    for (String d : new String[] {".", "./", "", "${ig.root}/.", "${basedir}/./"}) {
      assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"" + d + "\" includes=\"**/*.md\"/>"), d);
    }
    // outside a target, paths start from the ant file's folder, so a folder above it may be the IG's folder
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<property name=\"ig\" location=\"../..\"/><fileset dir=\"${ig}\" includes=\"**/*.md\"/>"));
    // the folder that holds the template may be the IG's folder
    assertNotNull(Template.scriptPageUse("onGenerate.xml",
        "<dirname property=\"up\" file=\"${ig.template}\"/><copy todir=\"${ig.temp}/md\"><fileset dir=\"${up}\" includes=\"**/*.md\"/></copy>"));
    assertEquals(Set.of("includes"), folders("<dirname property=\"up\" file=\"${ig.template}\"/><copy file=\"${up}/input/includes/menu.xml\" todir=\"x\"/>"));
    // the base template's spreadsheet search
    assertNull(Template.scriptPageUse("onLoad.xml",
        "<fileset id=\"s\" dir=\"${ig.root}\"><include name=\"${folder.input}/examples/*spreadsheet.xml\"/></fileset>"));
  }

  @Test
  void aListOfFilesInAnotherFileUnderTheRootBlocksAll() {
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${ig.root}\" includesfile=\"${ig.template}/pages.txt\"/>"));
    assertNotNull(Template.scriptPageUse("onGenerate.xml",
        "<fileset dir=\"${ig.root}\"><include name=\"a.xml\"/><includesfile name=\"${ig.template}/pages.txt\"/></fileset>"));
    assertNotNull(Template.scriptPageUse("onGenerate.xml", "<fileset dir=\"${ig.root}\" excludes=\"temp/**\"/>"));
  }

  @Test
  void aProgramGivenTheIgsFolder() {
    String plantUml = "<java jar=\"${ig.scripts}/plantuml.jar\" fork=\"true\"><jvmarg line=\"-Dfile.encoding=UTF-8\"/>"
        + "<arg line=\"-tsvg -o &quot;${basedir}/${dir.temp.includes}&quot; &quot;${basedir}/${folder.input}/images-source/**.plantuml&quot;\"/></java>";
    assertNull(Template.scriptPageUse("onGenerate.xml", plantUml));
    assertEquals(Set.of("images-source"), folders(plantUml));
    for (String arg : new String[] {"<arg value=\"${ig.root}\"/>", "<arg value=\"${basedir}/\"/>", "<arg path=\".\"/>",
        "<arg line=\"-i &quot;${ig.root}&quot; -o x\"/>", "<arg line=\"-i ${basedir} -o x\"/>", "<sysproperty key=\"d\" value=\"${ig.root}\"/>"}) {
      assertEquals("the template's script onGenerate.xml gives the IG's folder to a program (<java>), which isn't read",
          Template.scriptPageUse("onGenerate.xml", "<java jar=\"x.jar\">" + arg + "</java>"), arg);
    }
    assertNotNull(Template.scriptPageUse("onGenerate.xml",
        "<property name=\"here\" location=\"${ig.root}\"/><java jar=\"x.jar\"><arg value=\"${here}\"/></java>"));
    assertNull(Template.scriptPageUse("onGenerate.xml", "<java jar=\"x.jar\"><arg value=\"${ig.root}/temp/pages\"/></java>"));
    assertNull(Template.scriptPageUse("onGenerate.xml", "<javac srcdir=\"x\"/><arg value=\"${ig.root}\"/>"));
  }
}
