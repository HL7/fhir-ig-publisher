package org.hl7.fhir.igtools.templates;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.ComponentHelper;
import org.apache.tools.ant.Evaluable;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.PropertyHelper;
import org.apache.tools.ant.RuntimeConfigurable;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.UnknownElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Whether the template's check (onCheck) may look at the output: watch mode writes the output files the html check
 * changed again, as Jekyll wrote them, for a check that may
 */
class TemplateOnCheckOutputTest {

  @TempDir
  Path dir;

  /** the base templates' onCheck: it gathers the outcomes in the template folder, and nothing else */
  private static final String BASE_ON_CHECK = "<extension-point name=\"onCheck.extend\"/>"
      + "<target name=\"onCheck\" depends=\"onCheck.extend\">"
      + "<echo file=\"${ig.template}/onCheck-validation.json\" message=\"{&quot;resourceType&quot;:&quot;OperationOutcome&quot;,&quot;issue&quot;:[&#x0a;\"/>"
      + "<concat destfile=\"${ig.template}/onCheck-validation.json\" encoding=\"UTF-8\" outputencoding=\"UTF-8\" append=\"yes\">"
      + "<fileset dir=\"${ig.template}\" includes=\"oncheck-validation*.json\"/>"
      + "<fileset dir=\"${ig.template}\" includes=\"**/*.oncheck-validation.json\"/>"
      + "<filterchain><tailfilter lines=\"-1\" skip=\"0\"/></filterchain></concat>"
      + "<echo file=\"${ig.template}/onCheck-validation.json\" message=\"]}&#x0a;\" append=\"yes\"/>"
      + "<replaceregexp file=\"${ig.template}/onCheck-validation.json\" match=\",(\\s*\\]\\}\\s*)$\" replace=\"\\1\" flags=\"s\"/>"
      + "</target>";

  private void write(String path, String content) throws IOException {
    Path p = dir.resolve(path);
    Files.createDirectories(p.getParent());
    Files.write(p, content.getBytes(StandardCharsets.UTF_8));
  }

  private String why(String project) throws IOException {
    return why(project, dir.resolve("output").toString());
  }

  private String why(String project, String output) throws IOException {
    write("template/config.json", "{\"script\": \"scripts/ant.xml\", \"otherScripts\": [], \"targets\": {\"onGenerate\": \"onGenerate\", \"onCheck\": \"onCheck\"}}");
    write("template/scripts/ant.xml", "<project name=\"t\">" + project + "</project>");
    return new Template(dir.toString(), true, null, null, false, false, false).whyOnCheckMaySeeOutput(output);
  }

  @Test
  void theBaseTemplatesCheckDoesNot() throws IOException {
    // what the other targets do doesn't matter
    assertNull(why("<target name=\"onGenerate\"><copy todir=\"${ig.root}/output\"><fileset dir=\"${ig.root}/temp\"/></copy></target>" + BASE_ON_CHECK));
    // nor a target that extends another extension point
    assertNull(why("<extension-point name=\"onGenerate.extend\"/><target name=\"x\" extensionOf=\"onGenerate.extend\"><xslt in=\"a\" out=\"b\"/></target>" + BASE_ON_CHECK));
  }

  @Test
  void aCheckThatRanBefore() throws IOException {
    // watch mode asks after the check has run (in the build before)
    assertNull(why(BASE_ON_CHECK));
    Template t = new Template(dir.toString(), true, null, null, false, false, false);
    t.onCheckEvent(null, null);
    t.onCheckEvent(null, null);
    assertNull(t.whyOnCheckMaySeeOutput(dir.resolve("output").toString()));
  }

  @Test
  void noCheckDoesNot() throws IOException {
    write("template/config.json", "{}");
    assertNull(new Template(dir.toString(), true, null, null, false, false, false).whyOnCheckMaySeeOutput(dir.resolve("output").toString()));
  }

  @Test
  void aTargetThatExtendsTheCheckMay() throws IOException {
    String why = why("<target name=\"qa\" extensionOf=\"onCheck.extend\"><copy todir=\"x\"><fileset dir=\"${ig.root}/output\"/></copy></target>" + BASE_ON_CHECK);
    assertTrue(why != null && why.contains("'qa'") && why.contains("copy"), why);
    // in a file the script imports
    write("template/scripts/qa.xml", "<project name=\"qa\"><target name=\"qa\" extensionOf=\"onCheck.extend\"><echo message=\"x\"/>"
        + "<concat destfile=\"${ig.template}/x.txt\"><fileset dir=\"${ig.root}/output\" includes=\"*.html\"/></concat></target></project>");
    why = why("<import file=\"qa.xml\"/>" + BASE_ON_CHECK);
    assertTrue(why != null && why.contains("output"), why);
    // a target the check depends on
    why = why("<target name=\"pre\"><xslt in=\"${ig.root}/output/index.html\" out=\"${ig.template}/x\"/></target>"
        + BASE_ON_CHECK.replace("depends=\"onCheck.extend\"", "depends=\"pre,onCheck.extend\""));
    assertTrue(why != null && why.contains("'pre'"), why);
  }

  @Test
  void aTaskOfTheTemplatesOwnUnderAnAntName() throws IOException {
    // defined when the script loads, Ant's own are put back after (Project.init()): the check runs Ant's
    write("output/index.html", "x");
    String macro = "<macrodef name=\"echo\"><attribute name=\"file\"/><attribute name=\"message\"/><sequential>"
        + "<copy file=\"${ig.root}/output/index.html\" tofile=\"${ig.root}/copied.html\"/></sequential></macrodef>";
    assertNull(why(macro + BASE_ON_CHECK));
    new Template(dir.toString(), true, null, null, false, false, false).onCheckEvent(null, null);
    assertFalse(Files.exists(dir.resolve("copied.html")));
    assertTrue(Files.exists(dir.resolve("template/onCheck-validation.json")));
    // defined by a target that ran before, with an Ant task as what adapts it (made, though it can't be adapted)
    why("<target name=\"defs\"><typedef name=\"echo\" classname=\"" + Sneaky.class.getName()
        + "\" adapter=\"org.apache.tools.ant.taskdefs.Echo\" onerror=\"ignore\"/></target>" + BASE_ON_CHECK);
    Template t = new Template(dir.toString(), true, null, null, false, false, false);
    assertNull(t.whyOnCheckMaySeeOutput(dir.resolve("output").toString()));
    try {
      java.lang.reflect.Field f = Template.class.getDeclaredField("antProject");
      f.setAccessible(true);
      ((Project) f.get(t)).executeTarget("defs");
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
    String adapted = t.whyOnCheckMaySeeOutput(dir.resolve("output").toString());
    assertTrue(adapted != null && adapted.contains("runs echo"), adapted);
    // defined by the check
    String why = why("<target name=\"defs\">" + macro + "</target>" + BASE_ON_CHECK.replace("depends=\"onCheck.extend\"", "depends=\"defs,onCheck.extend\""));
    assertTrue(why != null && why.contains("macrodef"), why);
  }

  @Test
  void filesOutsideTheTemplateFolder() throws IOException {
    for (String fs : new String[] {"<fileset dir=\"${ig.root}/output\"/>", "<fileset dir=\"output\"/>", "<fileset dir=\"${ig.template}/..\"/>",
        "<fileset dir=\"${ig.template}\" includes=\"../output/**\"/>", "<fileset dir=\"${ig.template}\" includes=\"a.json, /output/**\"/>",
        "<fileset dir=\"${no.such.property}\"/>", "<fileset dir=\"${ig.template}\"><include name=\"**\"/></fileset>",
        "<fileset dir=\"${ig.template}\" followsymlinks=\"true\"/>", "<path><pathelement location=\"${ig.root}/output\"/></path>"}) {
      String why = why("<target name=\"onCheck\"><concat destfile=\"${ig.template}/x.json\">" + fs + "</concat></target>");
      assertNotNull(why, fs);
    }
    assertNotNull(why("<target name=\"onCheck\"><replaceregexp file=\"${ig.root}/output/index.html\" match=\"a\" replace=\"b\"/></target>"));
    assertNotNull(why("<target name=\"onCheck\"><echo file=\"../x.txt\" message=\"a\"/></target>"));
    assertNull(why("<target name=\"onCheck\"><echo file=\"${ig.template}/sub/../x.txt\" message=\"a\"/></target>"));
  }

  @Test
  void theOutputInTheTemplateFolder() throws IOException {
    assertNotNull(why(BASE_ON_CHECK, dir.resolve("template/output").toString()));
  }

  @Test
  void aCheckThatCantBeOrdered() throws IOException {
    assertNotNull(why("<target name=\"onCheck\" depends=\"missing\"/>"));
  }

  @Test
  void aNestedElementOfAnotherType() throws IOException {
    String why = why("<target name=\"onCheck\"><concat destfile=\"${ig.template}/x.json\"><fileset ant-type=\"fileset\" dir=\"${ig.template}\"/></concat></target>");
    assertTrue(why != null && why.contains("ant-type"), why);
  }

  @Test
  void aPropertyThatIsEvaluated() throws IOException {
    // ${toString:id} lists what a fileset finds, when it's expanded
    String out = "<fileset id=\"out\" dir=\"${ig.root}/output\"/>";
    assertNotNull(why(out + "<target name=\"onCheck\"><echo file=\"${ig.template}/x.txt\" message=\"${toString:out}\"/></target>"));
    assertNotNull(why(out + "<target name=\"onCheck\"><echo file=\"${ig.template}/x.txt\">${toString:out}</echo></target>"));
    assertNotNull(why(out + "<target name=\"onCheck\"><replaceregexp file=\"${ig.template}/x.txt\" match=\"a\" replace=\"${ant.refid:out}\"/></target>"));
    assertNull(why(out + "<target name=\"onCheck\"><echo file=\"${ig.template}/x.txt\">${ig.root}: done</echo></target>"));
    // a property that holds what it refers to (set when the script is read: what it refers to must be there then)
    String held = why("<fileset id=\"here\" dir=\".\"/><property name=\"a\" value=\"${ant.refid:here}\"/><target name=\"onCheck\"><echo file=\"${ig.template}/x.txt\" message=\"${a}\"/></target>");
    assertTrue(held != null && held.contains("property 'a'"), held);
  }

  @Test
  void aLinkInTheTemplateFolder() throws IOException {
    Files.createDirectories(dir.resolve("output"));
    write("output/index.html", "x");
    assertNull(why(BASE_ON_CHECK));
    Path link = dir.resolve("template/o");
    try {
      Files.createSymbolicLink(link, Paths.get("../output"));
    } catch (UnsupportedOperationException | IOException e) {
      return; // no links here
    }
    String why = why(BASE_ON_CHECK);
    assertTrue(why != null && why.contains("link"), why);
    Files.delete(link);
    assertNull(why(BASE_ON_CHECK));
    if (dir.getFileSystem().supportedFileAttributeViews().contains("unix")) {
      Files.createLink(dir.resolve("template/a.oncheck-validation.json"), dir.resolve("output/index.html"));
      why = why(BASE_ON_CHECK);
      assertTrue(why != null && why.contains("another name"), why);
    }
  }

  @Test
  void whatTheScriptLeftInItsProject() throws IOException {
    String def = "<taskdef name=\"hook\" classname=\"" + Hook.class.getName() + "\"/><typedef name=\"evaluator\" classname=\"" + Evaluator.class.getName() + "\"/>";
    // a listener that runs with the check
    String why = why(def + "<hook what=\"listener\"/>" + BASE_ON_CHECK);
    assertTrue(why != null && why.contains("listeners"), why);
    // a way to expand properties
    why = why(def + "<propertyhelper><evaluator/></propertyhelper>" + BASE_ON_CHECK);
    assertTrue(why != null && why.contains("properties"), why);
    // a target that does something else than its tasks
    why = why(def + "<hook what=\"target\"/>" + BASE_ON_CHECK);
    assertTrue(why != null && why.contains("Sneaky"), why);
    // a condition of its own on a target
    why = why(def + "<hook what=\"condition\"/>" + BASE_ON_CHECK);
    assertTrue(why != null && why.contains("condition"), why);
    // something to run in a target that isn't a task
    why = why(def + "<hook what=\"child\"/>" + BASE_ON_CHECK);
    assertTrue(why != null && why.contains("runs"), why);
    // a task already made
    why = why(def + "<hook what=\"made\"/>" + BASE_ON_CHECK);
    assertTrue(why != null && why.contains("of its own kind"), why);
    // a value that isn't text
    why = why(def + "<hook what=\"value\"/>" + BASE_ON_CHECK);
    assertTrue(why != null && why.contains("message> of its own kind"), why);
    // a nested element set up from something else than what is looked at
    why = why(def + "<hook what=\"wrapper\"/>" + BASE_ON_CHECK);
    assertTrue(why != null && why.contains("<fileset> of its own kind"), why);
    // a target or an element of another project (with its own listeners, properties or tasks)
    why = why(def + "<hook what=\"project\"/>" + BASE_ON_CHECK);
    assertTrue(why != null && why.contains("another project"), why);
    why = why(def + "<hook what=\"elementProject\"/>" + BASE_ON_CHECK);
    assertTrue(why != null && why.contains("<fileset> of its own kind"), why);
    // a condition that reads another target's
    why = why(def + "<hook what=\"copy\"/>" + BASE_ON_CHECK);
    assertTrue(why != null && why.contains("condition of its own"), why);
    // tasks made another way
    why = why(def + "<hook what=\"components\"/>" + BASE_ON_CHECK);
    assertTrue(why != null && why.contains("its own way"), why);
    why = why(def + "<hook what=\"helperProject\"/>" + BASE_ON_CHECK);
    assertTrue(why != null && why.contains("its own way"), why);
    // the definitions alone
    assertNull(why(def + BASE_ON_CHECK));
  }

  @Test
  void aConditionThatIsEvaluated() throws IOException {
    String out = "<fileset id=\"out\" dir=\"${ig.root}/output\"/>";
    for (String c : new String[] {"if", "unless"}) {
      String why = why(out + BASE_ON_CHECK.replace("<target name=\"onCheck\"", "<target name=\"onCheck\" " + c + "=\"${toString:out}\""));
      assertTrue(why != null && why.contains(c + "="), why);
      // the name is evaluated too, and what a property expands to
      why = why(out + BASE_ON_CHECK.replace("<target name=\"onCheck\"", "<target name=\"onCheck\" " + c + "=\"toString:out\""));
      assertTrue(why != null && why.contains(c + "="), why);
      why = why(out + "<property name=\"a\" value=\"toString:out\"/>" + BASE_ON_CHECK.replace("<target name=\"onCheck\"", "<target name=\"onCheck\" " + c + "=\"${a}\""));
      assertTrue(why != null && why.contains(c + "="), why);
      assertNull(why(out + BASE_ON_CHECK.replace("<target name=\"onCheck\"", "<target name=\"onCheck\" " + c + "=\"ig.root\"")));
    }
  }

  /** What a template's own code can do to the script's project before the check runs */
  public static class Hook extends Task {
    private String what;

    public void setWhat(String what) {
      this.what = what;
    }

    @Override
    public void execute() {
      Target check = getProject().getTargets().get("onCheck");
      if ("condition".equals(what)) {
        check.setIf(() -> true);
      } else if ("child".equals(what)) {
        check.addDataType(new RuntimeConfigurable(new Object(), "thing"));
      } else if ("made".equals(what)) {
        ((UnknownElement) check.getTasks()[0]).setRealThing(new Task() { });
      } else if ("value".equals(what)) {
        ((UnknownElement) check.getTasks()[0]).getWrapper().setAttribute("message", (Evaluable<String>) () -> "x");
      } else if ("wrapper".equals(what)) {
        UnknownElement concat = (UnknownElement) check.getTasks()[1];
        concat.getChildren().get(0).setRuntimeConfigurableWrapper(new RuntimeConfigurable(concat.getChildren().get(0), "fileset"));
      } else if ("project".equals(what)) {
        Project other = new Project();
        other.init();
        check.setProject(other);
      } else if ("elementProject".equals(what)) {
        Project other = new Project();
        other.init();
        ((UnknownElement) check.getTasks()[1]).getChildren().get(0).setProject(other);
      } else if ("copy".equals(what)) {
        check.setIf("ig.root");
        Target copy = new Target(check);
        check.setIf("toString:out");
        getProject().addOrReplaceTarget("onCheck", copy);
      } else if ("helperProject".equals(what)) {
        Project other = new Project();
        other.init();
        ComponentHelper.getComponentHelper(getProject()).setProject(other);
      } else if ("components".equals(what)) {
        ComponentHelper ch = new ComponentHelper() { };
        ch.setProject(getProject());
        ch.initDefaultDefinitions();
        getProject().addReference("ant.ComponentHelper", ch);
      } else if ("listener".equals(what)) {
        getProject().addBuildListener(new BuildListener() {
          public void buildStarted(BuildEvent event) { }
          public void buildFinished(BuildEvent event) { }
          public void targetStarted(BuildEvent event) { }
          public void targetFinished(BuildEvent event) { }
          public void taskStarted(BuildEvent event) { }
          public void taskFinished(BuildEvent event) { }
          public void messageLogged(BuildEvent event) { }
        });
      } else {
        Target t = new Sneaky();
        t.setName("onCheck.extend");
        t.setProject(getProject());
        getProject().addOrReplaceTarget(t);
      }
    }
  }

  public static class Sneaky extends Target {
  }

  public static class Evaluator implements PropertyHelper.PropertyEvaluator {
    @Override
    public Object evaluate(String property, PropertyHelper propertyHelper) {
      return null;
    }
  }
}
